# Phase 7.9.6.1 — Аудит пути исполнения AI деревенских жителей (Villager AI Execution Audit)

**Проект:** KilllTheFrizz  
**Платформа:** Minecraft 1.21.1 | NeoForge 21.1.265 | Java 21 (Eclipse Temurin 21.0.6+7-LTS)  
**Ветка:** `phase-7-core-sleepingentities` | **Базовый коммит:** `eb2408b`  
**Режим:** READ-ONLY (Исходный код мода не изменялся)  
**Дата:** 2026-11-03  
**Статус:** ЗАВЕРШЕНО (Deliverable Subphase 7.9.6.1)

---

## 1. Введение и цели аудита

Цель данного этапа — провести строгий анализ реального декомпилированного байткода и исходного кода Minecraft 1.21.1 и NeoForge 21.1.265 (`sourcesAndCompiledWithNeoForge_4015067ba59bb5b3c275335def8de7003a95acd6_output.jar`), чтобы:
1. Построить точный и доказанный граф вызовов (`call graph`) тика деревенского жителя от серверного цикла мира до внутренних компонентов `Brain`.
2. Зафиксировать точные дескрипторы и сигнатуры методов.
3. Чётко разграничить логику, исполняемую **внутри** `Brain.tick()`, и логику, находящуюся **снаружи** в `Villager.customServerAiStep()` и `Mob.serverAiStep()`.
4. Разрешить противоречие между отчётами Phase 7.9.3 и Phase 7.9.5 относительно обновления расписаний (`Schedule` / `Activity`).
5. Проверить точки интеграции Mixin и определить безопасную поверхность оптимизации.

---

## 2. Верифицированный граф вызовов (Verified Call Graph)

В Minecraft 1.21.1 цепочка исполнения AI жителя выглядит следующим образом:

```
ServerLevel.tickNonPassenger(Entity entity)  [20 Hz]
 │
 ├── EntityTickEvent.Pre (NeoForge Event Bus)
 ├── Villager.tick()  [LivingEntity.tick() -> LivingEntity.baseTick() -> Mob.tick()]
 │    ├── tick() счетчиков: unhappyCounter, etc.
 │    └── Mob.aiStep()
 │         ├── super.aiStep() -> LivingEntity.aiStep()
 │         │    ├── Проверка isEffectiveAi()
 │         │    └── LivingEntity.serverAiStep() -> переопределен в Mob.serverAiStep() [HEAD KtfProfiler]
 │         │         │
 │         │         ├── this.noActionTime++ (счетчик бездействия/деспавна)
 │         │         ├── this.sensing.tick() (Vanilla sensing cache)
 │         │         ├── targetSelector.tick() / tickRunningGoals() (у жителя пустой)
 │         │         ├── goalSelector.tick() / tickRunningGoals() (у жителя базовые цели)
 │         │         ├── this.navigation.tick() (GroundPathNavigation)
 │         │         │
 │         │         ├── this.customServerAiStep() -> переопределен в Villager.customServerAiStep()
 │         │         │    │
 │         │         │    ├── [ИНСАЙД BRAIN] Profiler.push("villagerBrain")
 │         │         │    ├── this.getBrain().tick((ServerLevel) this.level(), this);
 │         │         │    │    │
 │         │         │    │    ├── 1. forgetOutdatedMemories()  [итерация по Map<MemoryModuleType, ExpirableValue>]
 │         │         │    │    ├── 2. tickSensors(level, this)  [9 сенсоров: отсчет timeToTick и вызов doTick()]
 │         │         │    │    ├── 3. startEachNonRunningBehavior(level, this)
 │         │         │    │    │       └── Обработка BehaviorControl (включая UpdateActivityFromSchedule!)
 │         │         │    │    └── 4. tickEachRunningBehavior(level, this)
 │         │         │    │            └── Ticking активных поведений (WorkAtPoi, SleepInBed, etc.)
 │         │         │    └── Profiler.pop()
 │         │         │
 │         │         │    ├── [АУТСАЙД BRAIN 1] if (this.assignProfessionWhenSpawned) { ... }
 │         │         │    ├── [АУТСАЙД BRAIN 2] if (!this.isTrading() && this.updateMerchantTimer > 0) { ... }
 │         │         │    │                          └── Career Level-up Progression (Regeneration 200, +1 Level)
 │         │         │    ├── [АУТСАЙД BRAIN 3] if (this.lastTradedPlayer != null) { onReputationEvent(TRADE); }
 │         │         │    ├── [АУТСАЙД BRAIN 4] if (!this.isNoAi() && random.nextInt(100) == 0) { getRaidAt(); }
 │         │         │    ├── [АУТСАЙД BRAIN 5] if (profession == NONE && isTrading()) { stopTrading(); }
 │         │         │    └── super.customServerAiStep() (no-op в Mob)
 │         │         │
 │         │         ├── moveControl.tick()
 │         │         ├── lookControl.tick()
 │         │         ├── jumpControl.tick()
 │         │         └── sendDebugPackets()
 │         │
 │         └── pushEntities() [HEAD LivingEntityMixin: пропуск для спящих мобов]
 │
 └── EntityTickEvent.Post (NeoForge Event Bus)
```

---

## 3. Точные дескрипторы и сигнатуры методов

На основе анализа декомпилированного байткода (`javap -s -p`):

| Класс | Метод | Сигнатура / Байткод-дескриптор | Частота вызова | Назначение |
|---|---|---|---|---|
| `net.minecraft.server.level.ServerLevel` | `tickNonPassenger` | `(Lnet/minecraft/world/entity/Entity;)V` | 20 Hz | Главный цикл тика сущностей сервера |
| `net.minecraft.world.entity.LivingEntity`| `aiStep` | `()V` | 20 Hz | Общий AI-шаг живого существа |
| `net.minecraft.world.entity.LivingEntity`| `serverAiStep` | `()V` | 20 Hz | Базовый серверный AI-шаг (пустой) |
| `net.minecraft.world.entity.Mob` | `serverAiStep` | `()V` | 20 Hz (`final`) | Координация навигации, контроллеров и вызова `customServerAiStep` |
| `net.minecraft.world.entity.npc.Villager` | `customServerAiStep` | `()V` | 20 Hz (`protected`) | Корневая точка входа в AI жителя и `Brain.tick()` |
| `net.minecraft.world.entity.ai.Brain` | `tick` | `(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/LivingEntity;)V` | 20 Hz (`public`) | Полный цикл тика мозга жителя (память, сенсоры, поведения) |
| `net.minecraft.world.entity.ai.Brain` | `tickSensors` | `(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/LivingEntity;)V` | 20 Hz (`private`) | Итерация по 9 зарегистрированным сенсорам |
| `net.minecraft.world.entity.ai.Brain` | `startEachNonRunningBehavior` | `(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/LivingEntity;)V` | 20 Hz (`private`) | Комбинаторный перебор приоритетов и запуск незапущенных поведений |
| `net.minecraft.world.entity.ai.Brain` | `tickEachRunningBehavior` | `(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/LivingEntity;)V` | 20 Hz (`private`) | Тик активных поведений (`BehaviorControl.tickOrStop`) |
| `net.minecraft.world.entity.ai.Brain` | `updateActivityFromSchedule` | `(JJ)V` | Внутри Brain (`public`)| Проверка и смена активного `Activity` по игровому времени |
| `net.minecraft.world.entity.ai.sensing.Sensor` | `tick` | `(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/LivingEntity;)V` | 20 Hz (`final`)| Декремент `timeToTick` и вызов `doTick()` |

---
## 4. Разрешение противоречия: Где исполняются обновления расписаний (Schedule Updates)?

### 4.1 Суть противоречия в предыдущих документах
- В отчёте **Phase 7.9.3** утверждалось: *«Наивная отмена `serverAiStep()` через `ci.cancel()` заморозила стейт-машину мозга, заблокировав смену расписаний и пополнение запасов»*.
- В документе **Phase 7.9.5** утверждалось: *«Перехват только `Brain.tick()` оставляет в живых `super.customServerAiStep()`, поэтому обновления расписаний продолжают работать каждый тик»*.

### 4.2 Фактическая проверка по исходному коду 1.21.1
Аудит выявил точное место вызова `updateActivityFromSchedule(long dayTime, long gameTime)`:
1. `updateActivityFromSchedule` вызывается **ВСЕГО В 2 МЕСТАХ**:
   - При создании жителя в `Villager.registerBrainGoals()` (разовая инициализация).
   - Внутри `Brain.tick()` в виде поведения: `UpdateActivityFromSchedule.create()`.
2. Класс `net.minecraft.world.entity.ai.behavior.UpdateActivityFromSchedule` реализует `BehaviorControl<LivingEntity>` и зарегистрирован с приоритетом `99` во всех основных пакетах поведений (`VillagerGoalPackages`):
   - `getWorkPackage()` (`Activity.WORK`)
   - `getPlayPackage()` (`Activity.PLAY`)
   - `getRestPackage()` (`Activity.REST`)
   - `getMeetPackage()` (`Activity.MEET`)
   - `getIdlePackage()` (`Activity.IDLE`)
3. **ВНЕ `Brain.tick()` вызова обновления расписания НЕ СУЩЕСТВУЕТ ВООБЩЕ!** Ни `Villager.customServerAiStep()`, ни `Mob.serverAiStep()`, ни `super.customServerAiStep()` не имеют никакого отношения к расписанию.
4. **Вывод:**
   - Утверждение Phase 7.9.5 о том, что расписание продолжит обновляться снаружи Brain через `super.customServerAiStep()`, **было фактически ошибочным**.
   - Однако логика `Brain.updateActivityFromSchedule` содержит встроенную ванильную защиту:
     ```java
     if (p_21864_ - this.lastScheduleUpdate > 20L) {
         this.lastScheduleUpdate = p_21864_;
         Activity activity = this.getSchedule().getActivityAt((int)(p_21863_ % 24000L));
         if (!this.activeActivities.contains(activity)) {
             this.setActiveActivityIfPossible(activity);
         }
     }
     ```
     Vanilla Minecraft **сам по себе проверяет смену расписания не чаще, чем раз в 20 тиков (1 раз в секунду)**!
   - Если `Brain.tick()` троттлится, например, до 5 Hz (раз в 4 тика) или до 2 Hz (раз в 10 тиков), `UpdateActivityFromSchedule` будет срабатывать раз в 4 или 10 тиков. Задержка смены фазы дня составит максимум 0.2–0.5 секунды, что полностью незаметно для игрока.
   - **Но если `Brain.tick()` отменить полностью (0 Hz), расписание не обновится никогда!**

---

## 5. Аудит логики жителя вне Brain (`Villager.customServerAiStep`)

В теле метода `Villager.customServerAiStep()` за пределами вызова `this.getBrain().tick()` находятся следующие критические блоки:

```java
// 1. Сброс флага назначения профессии при спавне
if (this.assignProfessionWhenSpawned) {
    this.assignProfessionWhenSpawned = false;
}

// 2. Таймер повышения уровня карьеры (Level-Up Celebration)
if (!this.isTrading() && this.updateMerchantTimer > 0) {
    this.updateMerchantTimer--;
    if (this.updateMerchantTimer <= 0) {
        if (this.increaseProfessionLevelOnUpdate) {
            this.increaseMerchantCareer();
            this.increaseProfessionLevelOnUpdate = false;
        }
        this.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 200, 0));
    }
}

// 3. Отправка события репутации торговли
if (this.lastTradedPlayer != null && this.level() instanceof ServerLevel) {
    ((ServerLevel)this.level()).onReputationEvent(ReputationEventType.TRADE, this.lastTradedPlayer, this);
    this.level().broadcastEntityEvent(this, (byte)14);
    this.lastTradedPlayer = null;
}

// 4. Периодический опрос рейда
if (!this.isNoAi() && this.random.nextInt(100) == 0) {
    Raid raid = ((ServerLevel)this.level()).getRaidAt(this.blockPosition());
    if (raid != null && raid.isActive() && !raid.isOver()) {
        this.level().broadcastEntityEvent(this, (byte)42);
    }
}

// 5. Защита от торговли без профессии
if (this.getVillagerData().getProfession() == VillagerProfession.NONE && this.isTrading()) {
    this.stopTrading();
}
```

### Важное исправление: `updateMerchantTimer` НЕ отвечает за ресток товаров!
- `updateMerchantTimer` отвечает **исключительно за повышение уровня торговца** (Новичок -> Подмастерье -> Ремесленник -> Эксперт -> Мастер) и выдачу регенерации с частицами радости после завершения сделки.
- Настоящее пополнение запасов товаров (**Trade Restocking**) происходит внутри поведения `WorkAtPoi.useWorkstation()`, которое вызывается **внутри `Brain.tick()`** во время фазы `Activity.WORK`:
  ```java
  // WorkAtPoi.java
  if (p_24817_.shouldRestock()) {
      p_24817_.restock();
  }
  ```
- При этом `WorkAtPoi` имеет внутренний кулдаун `CHECK_COOLDOWN = 300L` (15 секунд). Это означает, что житель в ваниле проверяет ресток на рабочем месте не каждый тик, а раз в 15 секунд при условии нахождения ближе 1.73 блоков от блока профессии.

---

## 6. Сенсоры жителя и их скрытые таймеры (`timeToTick`)

Житель инициализирует ровно 9 типов сенсоров (`Villager.SENSOR_TYPES`):

1. `SensorType.NEAREST_LIVING_ENTITIES` (базовый скан живых существ вокруг).
2. `SensorType.NEAREST_PLAYERS` (поиск ближайших игроков).
3. `SensorType.NEAREST_ITEMS` (поиск предметов на земле для фермеров/сбора хлеба).
4. `SensorType.NEAREST_BED` (поиск кровати в радиусе, `scanRate = 20`).
5. `SensorType.HURT_BY` (реакция на атакующего).
6. `SensorType.VILLAGER_HOSTILES` (поиск зомби, разбойников, иллюзоров, вексий, `scanRate = 20`).
7. `SensorType.VILLAGER_BABIES` (поиск маленьких жителей).
8. `SensorType.SECONDARY_POIS` (поиск вторичных блоков профессии, `scanRate = 40`).
9. `SensorType.GOLEM_DETECTED` (проверка наличия железного голема, `scanRate = 80`).

### Механика декремента сенсоров:
Каждый сенсор наследует:
```java
public final void tick(ServerLevel level, E entity) {
    if (--this.timeToTick <= 0L) {
        this.timeToTick = (long)this.scanRate;
        this.doTick(level, entity);
    }
}
```
**Критическое следствие:**
Если вызов `Brain.tick()` троттлится, счетчик `--this.timeToTick` уменьшается реже.
Если `Brain.tick()` вызывается 1 раз в 4 тика (5 Hz), то сенсор с `scanRate = 20` сработает не через 20 тиков (1 сек), а через $20 \times 4 = 80$ тиков (4 секунды)!
**Для сенсора врагов (`VillagerHostilesSensor`) задержка в 4 секунды недопустима** — зомби может убить жителя до того, как тот войдет в `Activity.PANIC`.
Это доказывает, что любая система троттлинга обязана учитывать обнаружение угроз и не полагаться на то, что `PANIC` включится сам при троттле сенсоров.

---

## 7. Сон в кровати: `LivingEntity.isSleeping()` против `Activity.REST`

В отчете Phase 7.9.5 предлагалось использовать `brain.isActive(Activity.REST)` как критерий сна в кровати.
Аудит классов `SleepInBed.java` и `LivingEntity.java` доказал ошибочность этой предпосылки:
1. `Activity.REST` активируется по расписанию в вечернее время (например, в 12000 тиков).
2. В фазе `Activity.REST` житель:
   - Ищет путь к кровати (`SetClosestHomeAsWalkTarget`).
   - Идет сквозь двери и деревню (`InsideBrownianWalk`).
   - И только оказавшись ближе 1.14 блока от кровати, вызывает `entity.startSleeping(bedPos)`.
3. Если затроттлить жителя до 2 Hz сразу при наступлении `Activity.REST`, житель, идущий к кровати, замедлит поиск пути в 10 раз и застрянет на улице ночью.
4. **Безопасный инвариант:**
   Проверять не `Activity.REST`, а `entity.isSleeping()`, который в `LivingEntity` реализован как `return this.getSleepingPos().isPresent();` — это мгновенная проверка синк-данных ($O(1)$) без аллокаций.

---
## 8. Выводы и требования к Stage 2 (Инструментация)

1. **Точка замера в профилировщике:**
   Инструментировать:
   - `Villager.customServerAiStep` (общая длительность AI жителя).
   - `Brain.tick` (длительность всего мозга).
   - `Brain.tickSensors` и индивидуальные сенсоры (доля сенсоров в нагрузке).
   - `Brain.startEachNonRunningBehavior` и `tickEachRunningBehavior` (доля перебора поведений).
2. **Противоречия Phase 7.9.3 / 7.9.5 устранены:**
   Доказано, что `UpdateActivityFromSchedule` живет внутри `Brain.tick()`. Троттлинг мозга допустим только с ограниченными квантами времени (не более 4–10 тиков) или с принудительным вызовом обновления расписания на границах времени.
3. **Безопасная поверхность оптимизации:**
   Определены четкие флаги быстрого выхода на 20 Hz:
   - `isTrading()`
   - `hurtTime > 0`
   - `isSleeping()` (вместо `Activity.REST`)
   - `!getNavigation().isDone()` (активное перемещение)
   - Обнаружение врагов (через прямой опрос или гарантированный тик сенсора).

*Аудит Stage 1 завершен без модификации исходного кода проекта.*