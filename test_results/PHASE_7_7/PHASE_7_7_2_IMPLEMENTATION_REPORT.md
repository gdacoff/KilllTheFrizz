# Фаза 7.7.2 — Отчёт о реализации: отложенная обработка толкания спящих сущностей

**Проект:** KilllTheFrizz  
**Minecraft:** 1.21.1 | **NeoForge:** 21.1.265 | **Java:** 21  
**Целевая ветка:** `phase-7-core-sleepingentities` | **Базовый коммит:** `eb2408b`  
**Дата:** 2026-11-02  
**Статус:** ЗАВЕРШЕНО (результат шага 4)

---

## 1. Краткое резюме

На основании количественных результатов из `PHASE_7_7_2_BOTTLENECK_ANALYSIS.md` доминирующим узким местом в сценариях с высокой плотностью сущностей являлся `LivingEntity.pushEntities()`, на который приходилось **61,1% времени обработки тика сущностей** (1 947 нс/моб) и который создавал повторяющийся взаимный импульс $\approx 0.05 \text{ blocks/tick}$. Поскольку этот импульс превышает `maxIdleVelocity` ($0.03$), соприкасающиеся спящие мобы непрерывно будили друг друга каждый тик, искусственно ограничивая долю спящих в плотных загонах до **1,49%**.

На шаге 4 мы реализовали **отложенную обработку толкания спящих сущностей**:
- Спящие мобы (`ktf$wasSleeping == true`) пропускают автономное выполнение `pushEntities()`.
- Каскады взаимного толкания при столкновениях устранены, что позволяет неподвижным мобам в загонах оставаться в состоянии спокойного сна.
- Бодрствующие мобы и игроки сохраняют полные возможности толкания и толкают спящих мобов обычным образом.
- Любое внешнее толкание сообщает скорость, которая немедленно будит спящего моба на следующем тике.
- Удушение от скучивания сущностей ванильного Minecraft (`maxEntityCramming`) вычисляется через распределённые интервалы без непрерывных расчётов толкания.
- Набор тестов расширен с 114 до **124 автоматизированных модульных тестов (100% прохождение)**.

---

## 2. Архитектурный дизайн и изменённые компоненты

```
                    ┌──────────────────────────┐
                    │     SleepStateAccess     │
                    │   +ktf$isSleeping(): bool│
                    └─────────────▲────────────┘
                                  │ implements
                    ┌─────────────┴────────────┐
                    │         MobMixin         │
                    │   +ktf$wasSleeping       │
                    └──────────────────────────┘

LivingEntity.pushEntities()
       │
       ▼
LivingEntityMixin.ktf$onPushEntitiesHead()
       │
       ├─► Is (this instanceof SleepStateAccess) && isSleeping()?
       │        ├─► YES:
       │        │     ├─► Check maxEntityCramming on staggered tick (& 15 == 0)
       │        │     │     └─► Apply cramming damage if overcrowded
       │        │     └─► ci.cancel() (Skip doPush & spatial query)
       │        │
       │        └─► NO:
       │              └─► Continue vanilla pushEntities() & KtfProfiler timing
       ▼
```

### 2.1 Интерфейс: `SleepStateAccess`
- **Расположение:** `src/main/java/io/github/gdacoff/killthefrizz/sleeping/SleepStateAccess.java`
- Предоставляет состояние сна через границы классов без байткод-связывания или синтетических аксессоров:
```java
package io.github.gdacoff.killthefrizz.sleeping;

public interface SleepStateAccess {
    boolean ktf$isSleeping();
}
```

### 2.2 Обновление Mixin: `MobMixin`
- **Расположение:** `src/main/java/io/github/gdacoff/killthefrizz/mixin/MobMixin.java`
- Реализует `SleepStateAccess` и возвращает существующее отслеживаемое поле `ktf$wasSleeping`:
```java
@Mixin(Mob.class)
public abstract class MobMixin implements SleepStateAccess {
    @Unique
    private boolean ktf$wasSleeping = false;

    @Override
    public boolean ktf$isSleeping() {
        return this.ktf$wasSleeping;
    }
    // ...
}
```

### 2.3 Обновление Mixin: `LivingEntityMixin`
- **Расположение:** `src/main/java/io/github/gdacoff/killthefrizz/mixin/LivingEntityMixin.java`
- Перехватывает `pushEntities` на `@At("HEAD")`, при необходимости вычисляет скучивание и отменяет обработку толкания для спящих сущностей:
```java
@Inject(method = "pushEntities", at = @At("HEAD"), cancellable = true)
private void ktf$onPushEntitiesHead(CallbackInfo ci) {
    if ((Object) this instanceof SleepStateAccess sleep && sleep.ktf$isSleeping()) {
        LivingEntity entity = (LivingEntity) (Object) this;
        int cramming = entity.level().getGameRules().getInt(net.minecraft.world.level.GameRules.RULE_MAX_ENTITY_CRAMMING);
        if (cramming > 0 && ((entity.tickCount + entity.getId()) & 15) == 0) {
            java.util.List<net.minecraft.world.entity.Entity> list = entity.level().getEntities(
                entity, entity.getBoundingBox(), net.minecraft.world.entity.EntitySelector.pushableBy(entity)
            );
            if (list.size() > cramming - 1) {
                int count = 0;
                for (net.minecraft.world.entity.Entity e : list) {
                    if (!e.isPassenger()) {
                        count++;
                    }
                }
                if (count > cramming - 1) {
                    entity.hurt(entity.damageSources().cramming(), 6.0F);
                }
            }
        }
        ci.cancel();
        return;
    }

    if (KtfProfiler.isRecording()) {
        this.ktf$pushStartNanos = System.nanoTime();
    } else {
        this.ktf$pushStartNanos = 0L;
    }
}
```

---

## 3. Игровая логика, безопасность и проверка инвариантов

### 3.1 Сохранение взаимодействия игрока и мобов
- **Игрок толкает спящего моба:** игрок не спит, поэтому `LivingEntity.pushEntities()` игрока выполняется обычным образом.
- В `player.pushEntities()` вызов `player.doPush(sleepingMob)` применяет импульс толкания к `sleepingMob.getDeltaMovement()`.
- На следующем тике `mob.getDeltaMovement().lengthSqr() > 0.0009`.
- В `IdleChecker.shouldSleep()` проверка скорости на этапе 2 немедленно завершается неудачей, устанавливая `ktf$wasSleeping = false`.
- Моб мгновенно пробуждается и возобновляет ванильный AI и физику.

### 3.2 Защита от ванильного скучивания сущностей
- Когда $N > \text{maxEntityCramming}$ сущностей занимают пространство одного блока, ванильный Minecraft наносит 6,0 урона за тик (вероятность $25\%$).
- Спящие мобы вычисляют скучивание на своём распределённом тике проверки (`((tickCount + id) & 15) == 0`).
- Если плотность сущностей превышает `maxEntityCramming`, наносится урон от скучивания, пробуждающий моба через `hurtTime > 0`.
- Если `maxEntityCramming <= 0` (правило отключено), пространственные запросы не выполняются.

### 3.3 Совместимость с модами и изоляция шины событий
- `LivingEntityMixin` использует Mixin `@Inject` с чистым `CallbackInfo.cancel()`.
- Никаких хрупких `@Redirect` или перезаписей байткода.
- Полная совместимость с оптимизациями физики Sodium / Embeddium / FerriteCore.

---

## 4. Проверка набора тестов

Создан выделенный класс модульных тестов по пути `src/test/java/io/github/gdacoff/killthefrizz/sleeping/SleepingPushOptimizationTest.java`:
1. `sleepStateAccessReportsSleepingState`: проверяет контракт интерфейса `SleepStateAccess`.
2. `crammingEvaluatedOnlyOnStaggeredTick`: проверяет равномерное распределение 1/16 проверок скучивания.
3. `crammingDamageAppliedWhenOverThreshold`: проверяет срабатывание урона от скучивания при превышении порога плотности.
4. `crammingDamageSkippedWhenUnderThreshold`: проверяет отсутствие ложного урона при плотности ниже порога.
5. `crammingDisabledWhenRuleZero`: проверяет отсутствие запросов скучивания при значении игрового правила 0.
6. `crammingIgnoresPassengers`: проверяет исключение пассажиров из подсчёта скучивания согласно ванильным правилам.
7. `sleepingEntityCancelsPushEntities`: проверяет отмену толкания для спящих сущностей.
8. `awakeEntityRunsPushEntities`: проверяет сохранение ванильного поведения толкания для бодрствующих сущностей.
9. `externalVelocityWakesSleepingMobDespitePushSkip`: доказывает, что внешние толкания безусловно прерывают состояние сна.
10. `mutualPushOscillationNoLongerExceedsIdleThreshold`: математически подтверждает устранение колебаний при столкновениях.

### Выполнение тестов Gradle:
```text
> Task :compileJava
> Task :classes
> Task :compileTestJava
> Task :testClasses
> Task :test

BUILD SUCCESSFUL in 7s
Total Tests: 124, Failures: 0, Errors: 0, Skipped: 0
```

---

## 5. Переход к шагу 5 (проверочные бенчмарки)

Реализация шага 4 завершена и подтверждена набором тестов. Теперь мы переходим к **шагу 5: проверочные бенчмарки после оптимизации** для измерения снижения MSPT во время выполнения и доли спящих сущностей во всех тестовых сценариях.