# Аудит 5: Реальный движок профилирования (`KtfProfiler`) и проектирование инструментации Mixin

**Модуль:** `io.github.gdacoff.killthefrizz.profiling.KtfProfiler`  
**Платформа:** Minecraft 1.21.1 / NeoForge 21.1.265 / Java 21 / MixinExtras 0.5.5  
**Режим аудита:** READ-ONLY DESIGN (Без внедрения в код).  

---

## 1. Цель и задачи встроенного профилировщика

Для доказательства эффективности мода без теоретических предположений необходим специализированный легковесный инструментальный профилировщик, измеряющий:

1. **Время проверки сна:** $T(\text{IdleChecker.shouldSleep})$ в наносекундах.
2. **Время реального AI:** $T(\text{Mob.serverAiStep})$ для бодрствующих мобов (включая GoalSelector, Navigation, Brain).
3. **Время физики столкновений:** $T(\text{LivingEntity.pushEntities})$ (точный замер подозрения на главный боттлнек).
4. **Количество событий:**
   - $N_{\text{skipped}}$ (пропущенные вызовы AI);
   - $N_{\text{executed}}$ (фактически выполненные вызовы AI);
   - $N_{\text{entities}}$ (суммарно обработанные сущности).

---

## 2. Архитектура класса `KtfProfiler`

Профилировщик реализуется по принципу **нулевых накладных расходов в отключенном состоянии (Zero-Cost When Inactive)**:

```java
public final class KtfProfiler {
    // Единственный volatile флаг. При false проверка занимает < 0.5 ns благодаря JIT-инлайнингу
    public static volatile boolean ACTIVE = false;

    // Скользящее окно на 100 тиков (накапливается в Server Thread)
    private static long idleCheckNanos;
    private static long idleCheckCalls;

    private static long serverAiStepNanos;
    private static long serverAiStepCalls;

    private static long pushEntitiesNanos;
    private static long pushEntitiesCalls;

    private static long skippedAiCalls;

    // Снапшот последних 100 тиков для безопасного чтения командами
    public static volatile Snapshot LAST_SNAPSHOT = Snapshot.EMPTY;

    public record Snapshot(
        long avgIdleCheckNs,
        long avgServerAiStepNs,
        long avgPushEntitiesNs,
        long totalSkippedAi,
        long totalExecutedAi,
        double savedMsptEstimate
    ) {
        public static final Snapshot EMPTY = new Snapshot(0, 0, 0, 0, 0, 0.0);
    }

    public static void recordIdleCheck(long nanos) {
        if (!ACTIVE) return;
        idleCheckNanos += nanos;
        idleCheckCalls++;
    }

    public static void recordServerAiStep(long nanos) {
        if (!ACTIVE) return;
        serverAiStepNanos += nanos;
        serverAiStepCalls++;
    }

    public static void recordPushEntities(long nanos) {
        if (!ACTIVE) return;
        pushEntitiesNanos += nanos;
        pushEntitiesCalls++;
    }

    public static void recordSkippedAi() {
        if (!ACTIVE) return;
        skippedAiCalls++;
    }

    public static void onTickEnd(ServerTickEvent.Post event) {
        if (!ACTIVE) return;
        // Публикация снапшота каждые 100 тиков
        // Сброс рабочих аккумуляторов
    }
}
```

---

## 3. Точки инструментации Mixin (Корректность при `ci.cancel()`)

### А. Инструментация `IdleChecker.shouldSleep()`
В [`MobMixin.java`](file:///C:/Users/gdacp/IdeaProjects/KilllTheFrizz/src/main/java/io/github/gdacoff/killthefrizz/mixin/MobMixin.java#L27-L35):
```java
@Inject(method = "serverAiStep", at = @At("HEAD"), cancellable = true)
private void ktf$onServerAiStep(CallbackInfo ci) {
    Mob mob = (Mob) (Object) this;
    
    long t0 = KtfProfiler.ACTIVE ? System.nanoTime() : 0L;
    boolean sleep = IdleChecker.shouldSleep(mob, this.ktf$wasSleeping);
    if (KtfProfiler.ACTIVE) {
        KtfProfiler.recordIdleCheck(System.nanoTime() - t0);
    }
    
    this.ktf$wasSleeping = sleep;
    if (sleep) {
        mob.setNoActionTime(mob.getNoActionTime() + 1);
        KtfProfiler.recordSkippedAi();
        ci.cancel(); // Завершает метод HEAD, последующий код serverAiStep() НЕ выполняется
    }
}
```

### Б. Инструментация реально выполненного `serverAiStep()`
> [!IMPORTANT]
> **Поведение Mixin при `ci.cancel()`:** Если в методе на `HEAD` вызывается `ci.cancel()`, метод возвращает управление вызывающему коду **немедленно**. Любые инъекции на `@At("RETURN")` в том же самом методе **НЕ ВЫЗЫВАЮТСЯ**!  
> Это даёт идеальное естественное разделение: замер времени на `HEAD` (после точки отмены) и `RETURN` зафиксирует **ТОЛЬКО РЕАЛЬНО ВЫПОЛНЕННЫЕ ВЫЗОВЫ**!

```java
@Inject(method = "serverAiStep", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Mob;customServerAiStep()V", shift = At.Shift.BEFORE))
private void ktf$beforeRealAi(CallbackInfo ci) {
    if (KtfProfiler.ACTIVE) {
        // Запоминаем время старта исполнения реального AI в ThreadLocal или поле сущности
    }
}

@Inject(method = "serverAiStep", at = @At("RETURN"))
private void ktf$afterRealAi(CallbackInfo ci) {
    if (KtfProfiler.ACTIVE) {
        // Фиксируем System.nanoTime() - start
        // Этот код выполнится ТОЛЬКО если ci.cancel() НЕ был вызван!
    }
}
```

### В. Инструментация `LivingEntity.pushEntities()`
В новом `LivingEntityMixin`:
```java
@Inject(method = "pushEntities", at = @At("HEAD"))
private void ktf$pushStart(CallbackInfo ci) {
    if (KtfProfiler.ACTIVE) {
        // t0 = System.nanoTime()
    }
}

@Inject(method = "pushEntities", at = @At("RETURN"))
private void ktf$pushEnd(CallbackInfo ci) {
    if (KtfProfiler.ACTIVE) {
        // KtfProfiler.recordPushEntities(System.nanoTime() - t0)
    }
}
```

---

## 4. Защита от искажений измерений (Measurement Integrity)

1. **Исключение времени простоя:** `System.nanoTime()` замеряется точечно вокруг целевых методов внутри одного тика, исключая время ожидания потока между тиками (`Thread.sleep()`).
2. **Защита от рекурсии:** Все три метода (`shouldSleep`, `serverAiStep`, `pushEntities`) являются линейными и никогда не вызывают сами себя рекурсивно.
3. **Оверхед самого `System.nanoTime()`:**
   - На современных CPU вызов `System.nanoTime()` (инструкция `RDTSC` / `vDSO`) занимает **~15–20 наносекунд**.
   - Чтобы оверхед замеров не превышал 1% от времени тика сервера, в `KtfProfiler` вводится сэмплирование: замер каждого 4-го или 8-го моба (`(mob.getId() & 3) == 0`).
4. **Интерактивный отчёт в игре:**
   Команда `/killthefrizz profile start [ticks]` и `/killthefrizz profile stop`:
   Выводит в чат и консоль точную инструментальную таблицу:
   ```text
   KillTheFrizz Profiler Results (last 100 ticks):
     IdleChecker.shouldSleep:  1.8 µs/entity (Total: 0.45 ms/tick)
     Mob.serverAiStep (exec):  2.4 µs/entity (Total: 0.16 ms/tick)
     LivingEntity.pushEntities: 34.2 µs/entity (Total: 8.55 ms/tick)
     AI Calls: 68 skipped / 182 executed
     Net Savings: +0.07 ms/tick
   ```
   Это даст исчерпывающий, математически доказанный ответ без спекуляций.