# Аудит 1: Горячий путь IdleChecker и последовательность выполнения

**Модуль:** `io.github.gdacoff.killthefrizz.sleeping.IdleChecker`  
**Класс вызова:** `MobMixin.java` (`HEAD` метода `Mob.serverAiStep()`)  
**Частота вызова:** 20 раз в секунду на каждого моба в мире.  
**Режим аудита:** READ-ONLY.  

---

## 1. Точная последовательность выполнения `IdleChecker.shouldSleep()`

При каждом вызове `Mob.serverAiStep()` в текущей реализации Phase 7.1 выполняется следующая цепочка:

```mermaid
flowchart TD
    Start["MobMixin.ktf$onServerAiStep()"] --> C1["1. Common & Server Config Check"]
    C1 --> C2["2. isForceAwake(mob) (ConcurrentHashMap)"]
    C2 --> C3["3. hurtTime > 0 || isOnFire()"]
    C3 --> C4["4. getTarget() != null"]
    C4 --> C5["5. !onGround() || isFallFlying()"]
    C5 --> C6["6. isInWater() || isInLava()"]
    C6 --> C7["7. getDeltaMovement() > maxIdleVelocitySqr"]
    C7 --> C8["8. isPassenger() || isVehicle() || isLeashed()"]
    C8 --> C9["9. !getActiveEffects().isEmpty()"]
    C9 --> C10["10. !getNavigation().isDone()"]
    C10 --> C11["11. Heartbeat check (tickCount + id % heartbeat == 0)"]
    
    C11 --> EvalCheck{"wasSleeping && !isEvalTick?"}
    EvalCheck -->|ДА (Спящий моб, не 20-й тик)| FastReturn["CURRENT_TICK_SLEEPING.incrementAndGet()\nreturn true;"]
    
    EvalCheck -->|НЕТ: wasSleeping == false ИЛИ isEvalTick| Stage4["4. EXPENSIVE EVALUATION"]
    Stage4 --> S1["hasNearbyAlivePlayer(sleepRadius)"]
    S1 --> S2["instanceof TamableAnimal && isTame()"]
    S2 --> S3["instanceof Animal && isInLove()"]
    S3 --> S4["isBaby()"]
    S4 --> S5["BuiltInRegistries.ENTITY_TYPE.getKey()"]
    S5 --> S6["id.toString() (String Allocation!)"]
    S6 --> S7["entityBlacklistSet().contains(idStr)"]
    S7 --> EndSuccess["CURRENT_TICK_SLEEPING.incrementAndGet()\nreturn true;"]
```

---

## 2. Анализ операций и предполагаемая стоимость

| № | Операция / Проверка | Частота выполнения | Предполагаемая стоимость | Возможность кэширования | Риски и дефекты текущей реализации |
|---|---|---|---|---|---|
| 1 | `KtfConfig.getEffectiveCommon().enabled()` | Каждый вызов (100%) | ~1–2 ns | Да (локальная ссылка) | Чтение volatile-поля конфига. Незначительный оверхед. |
| 2 | `isForceAwake(mob)` | Каждый вызов (100%) | **~25–45 ns** | **Да (в сущность)** | **Вызов `mob.getUUID()`, хеширование UUID, поиск в `ConcurrentHashMap`. Аллокация боксинга.** |
| 3 | `mob.hurtTime > 0 || mob.isOnFire()` | Каждый вызов (100%) | ~1–2 ns | Нет (динамическое) | Прямое чтение примитивных полей. Оптимально. |
| 4 | `mob.getTarget() != null` | Каждый вызов (100%) | ~1–2 ns | Нет (динамическое) | Чтение ссылки. Оптимально. |
| 5 | `!mob.onGround() || mob.isFallFlying()` | Каждый вызов (100%) | ~2–4 ns | Нет (динамическое) | Чтение флагов сущности. Оптимально. |
| 6 | `mob.isInWater() || mob.isInLava()` | Каждый вызов (100%) | ~4–8 ns | Нет (динамическое) | Проверка битовых масок тегов жидкостей. Быстро. |
| 7 | `getDeltaMovement().horizontalDistanceSqr()` | Каждый вызов (100%) | ~3–5 ns | Нет (динамическое) | `Vec3` уже создан в физике тика; вычисление $x^2 + z^2$. |
| 8 | `isPassenger() || isVehicle() || isLeashed()` | Каждый вызов (100%) | ~3–5 ns | Нет (динамическое) | Проверка ссылок на транспорт/пассажиров. Быстро. |
| 9 | `!mob.getActiveEffects().isEmpty()` | Каждый вызов (100%) | ~4–8 ns | Нет (динамическое) | `getActiveEffects()` возвращает `this.activeEffects.values()`. Создаётся Collection view. Лучше проверять `getActiveEffectsMap().isEmpty()`. |
| 10 | `!mob.getNavigation().isDone()` | Каждый вызов (100%) | ~2–4 ns | Нет (динамическое) | Проверка `path == null || path.isDone()`. Оптимально. |
| 11 | `heartbeatTicks` остаток от деления | Каждый вызов (100%) | ~2–3 ns | Нет | Арифметика `Math.floorMod(long, long)`. Оптимально. |
| **12** | **Интервальный выход `wasSleeping && !isEvalTick`** | Каждый вызов (100%) | ~2 ns | - | **КРИТИЧЕСКИЙ ДЕФЕКТ ЛОГИКИ (см. п. 3).** |
| 13 | `hasNearbyAlivePlayer(sleepRadius)` | **100% тиков для всех бодрствующих мобов** | **~80–250 ns** | **Да (интервал)** | **Поиск по списку игроков мира. Для бодрствующих мобов вызывается КАЖДЫЙ ТИК!** |
| 14 | `TamableAnimal`, `Animal`, `Baby` | На eval-тиках или бодрствующих | ~2–4 ns | Частично | `instanceof` проверки. Быстро. |
| 15 | `BuiltInRegistries.ENTITY_TYPE.getKey()` | На eval-тиках или бодрствующих | ~15–30 ns | **Да (IdentityMap)** | Поиск в реестре `ResourceLocation`. |
| 16 | `id.toString()` | На eval-тиках или бодрствующих | **~40–80 ns** | **Да (статически)** | **Аллокация новой строки `new String()` в куче каждый тик для бодрствующих мобов!** |
| 17 | `entityBlacklistSet().contains(idStr)` | На eval-тиках или бодрствующих | ~15–25 ns | **Да (IdentityMap)** | Хеширование строки и поиск в HashSet. |
| 18 | `KtfStats.recordRejection()` | При каждом возврате `false` | **~10–20 ns** | **Только в DEBUG** | **Атомарная блокировка шины CPU `AtomicLong.incrementAndGet()` при каждом отказе.** |

---

## 3. Критический дефект: оверхед на бодрствующих мобах

В коде [`IdleChecker.java:98`](file:///C:/Users/gdacp/IdeaProjects/KilllTheFrizz/src/main/java/io/github/gdacoff/killthefrizz/sleeping/IdleChecker.java#L98) содержится условие:
```java
if (wasSleeping && !isEvalTick) {
    KtfStats.CURRENT_TICK_SLEEPING.incrementAndGet();
    return true;
}
```

### Следствие для бодрствующих мобов:
- Если моб **бодрствует** (`wasSleeping == false`), условие `wasSleeping && !isEvalTick` **всегда ложно**.
- Бодрствующий моб (например, 180 коров в загоне, расталкивающих друг друга, или мобы возле игрока) **каждый тик проваливается в Stage 4**!
- Каждый тик для каждого бодрствующего моба вызываются:
  1. `hasNearbyAlivePlayer()` — пространственный перебор игроков.
  2. `id.toString()` — аллокация новой строки в Java Heap.
  3. `contains(idStr)` — поиск строки в `HashSet`.
  4. `KtfStats.recordRejection()` — атомарный CAS `AtomicLong.incrementAndGet()`.

**При 200 бодрствующих мобах на сервере:**
- 200 мобов $\times$ 20 тиков = **4,000 аллокаций строк в секунду**.
- 4,000 проверок игроков `hasNearbyAlivePlayer()` в секунду.
- 4,000 блокировок шины `AtomicLong` в секунду.
- Менеджер сна тратит значительное время CPU именно на тех мобов, которых он **не может усыпить**!

---
## 4. Исследование редкой проверки бодрствующих мобов

Предложенный паттерн:
```java
boolean evalTick = ((mob.tickCount + mob.getId()) & 15) == 0;
if (!wasSleeping && !evalTick) {
    return false;
}
```

### Анализ по 7 контрольным вопросам:

1. **Где должна находиться проверка?**  
   Строго в самом начале метода `shouldSleep()`, сразу после глобальных флагов включения мода:
   - Если моб бодрствует и данный тик не является его персональным eval-тиком (`& 15 != 0`), метод **немедленно возвращает `false` за 1 наносекунду**.
   - Пропускаются `isForceAwake`, проверки скорости, навигации, игроков и списков.
2. **Какие проверки обязаны оставаться ежетиковыми?**  
   Только проверки **пробуждения уже спящего моба** (`wasSleeping == true`):
   - Урон (`hurtTime > 0`), огонь, появление цели (`getTarget() != null`), потеря опоры (`!onGround()`), навигация (`!navigation.isDone()`), `heartbeat`.
   - Для бодрствующего моба эти проверки не нужны — он и так уже бодрствует!
3. **Создаёт ли это задержку засыпания?**  
   Да, задержка составит от 0 до 15 тиков (в среднем 8 тиков / 0.4 секунды).  
   *Оценка влияния:* **Исключительно положительная**. Моб не должен засыпать мгновенно в первую же миллисекунду остановки. Задержка в 0.4 сек действует как естественный фильтр переходных процессов.
4. **Не нарушает ли обработку урона, навигации и импульсов?**  
   **Нет!** Возврат `false` означает, что `Mob.serverAiStep()` **не отменяется**. Моб исполняет полноценный ванильный AI-тик, обрабатывает урон, цели, движение и навигацию без малейших задержек.
5. **Не пропускает ли важные события?**  
   Нет. Единственное, что откладывается — это момент перехода из бодрствования в сон. Все события мира (урон, взрывы, взаимодействие) обрабатываются штатным кодом Minecraft.
6. **Взаимодействие с `ktf$wasSleeping`:**  
   Поле `ktf$wasSleeping` остаётся `false` на протяжении 15 не-eval тиков. На 16-й тик (`evalTick == true`) запускается полная проверка пригодности. Если все условия соблюдены, моб переходит в `ktf$wasSleeping = true`.
7. **Поведение на разных типах мобов:**  
   Сдвиг по `mob.getId()` равномерно размазывает 16-тиковый цикл по тикам сервера: ровно $1/16$ часть популяции проверяется на пригодность ко сну в каждом тике.

### Проект безопасной реализации (Архитектурный шаблон Phase 8):
```java
public static boolean shouldSleep(Mob mob, boolean wasSleeping) {
    // 1. Быстрый фильтр бодрствующих мобов (93.75% вызовов отсекаются за 1 такт CPU)
    if (!wasSleeping && ((mob.tickCount + mob.getId()) & 15) != 0) {
        return false;
    }
    
    // 2. Для спящих мобов — ежетиковые быстрые проверки пробуждения
    if (wasSleeping) {
        if (mob.hurtTime > 0 || mob.isOnFire() || mob.getTarget() != null 
            || !mob.onGround() || !mob.getNavigation().isDone()) {
            return false;
        }
        // Интервальный пропуск для спящих
        if (((mob.tickCount + mob.getId()) & 15) != 0) {
            return true;
        }
    }
    
    // 3. Полная оценка (вызывается только 1 раз в 16 тиков)
    return evaluateFullSleepConditions(mob);
}
```
**Эффект:** Снижение процессорного времени `IdleChecker` на бодрствующих мобах на **~90%**.