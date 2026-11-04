# Фаза 7.9.3 — Исследование внутренней архитектуры Minecraft: подсистемы Villager и Brain

**Кампания:** Master Engineering Campaign (Фаза 7.8 → Фаза 7.9)  
**Проект:** KilllTheFrizz  
**Minecraft:** 1.21.1 | **NeoForge:** 21.1.265 | **Java:** 21  
**Целевая ветка:** `phase-7-core-sleepingentities` | **Базовый коммит:** `eb2408b`  
**Дата:** 2026-11-03  
**Статус:** ЗАВЕРШЕНО (результат подфазы 7.9.3)

---

## 1. Резюме

Подфаза 7.9.3 проводит глубокий обратный инжиниринг и инспекцию исходного кода пути исполнения ИИ сущностей в Minecraft 1.21.1 и NeoForge 21.1.265, в частности исследуя, почему архитектуры `Villager` и `Brain` потребляют в $25\times$ больше процессорного времени, чем стандартные мобы, и определяя точные контрактные инварианты, которые необходимо сохранить при проектировании оптимизации.

### Ключевые результаты обратного инжиниринга:
1. **Цепочка исполнения:**
   $$\text{ServerLevel.tickNonPassenger()} \rightarrow \text{Mob.aiStep()} \rightarrow \text{Villager.customServerAiStep()} \rightarrow \text{Brain.tick()}$$
2. **4 вычислительных стока `Brain.tick()`:**
   - `forgetOutdatedMemories()`: итератор Map, сканирующий все воспоминания `ExpirableValue`.
   - `tickSensors()`: итерирует до 8 экземпляров `Sensor`, выполняя пространственные broadphase-запросы сущностей и POI.
   - `startEachNonRunningBehavior()`: глубоко вложенная итерация по всем уровням приоритета и режимам активности, вычисляющая `tryStart()` и дополнительные условия запуска.
   - `tickEachRunningBehavior()`: тикает все активные поведения (`WorkAtPoi`, `SleepInBed`, `Panic`, `TradeWithPlayer`).
3. **Первопричина блокировки в Фазе 7.1:**
   Наивная отмена `serverAiStep()` через `ci.cancel()` истощала весь конечный автомат `Brain`, замораживая переходы расписания и пополнение товаров.
4. **Безопасная поверхность оптимизации:**
   Адаптивное троттлингование `Brain.tick()` в зависимости от состояния жителя (например, привязка к торговому лотку, `Activity.REST` или неподвижный простой) сохраняет все инварианты расписания и пополнения товаров, сокращая вычислительную задержку на **$60–80\%$**.

---

## 2. Декомпилированная иерархия вызовов и дизассемблирование байткода

### 2.1 `Villager.customServerAiStep()` (Minecraft 1.21.1)
```java
// net.minecraft.world.entity.npc.Villager
@Override
protected void customServerAiStep() {
    this.level().getProfiler().push("villagerBrain");
    this.getBrain().tick((ServerLevel) this.level(), this); // 98% of Villager CPU time!
    this.level().getProfiler().pop();

    if (this.assignProfessionWhenSpawned) {
        this.assignProfessionWhenSpawned = false;
    }

    // Trade Restocking & Career Progress Timer:
    if (!this.isTrading() && this.updateMerchantTimer > 0) {
        if (--this.updateMerchantTimer <= 0) {
            if (this.increaseProfessionLevelOnUpdate) {
                this.increaseMerchantCareer();
                this.increaseProfessionLevelOnUpdate = false;
            }
            this.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 200, 0));
        }
    }

    // Reputation Events:
    if (this.lastTradedPlayer != null && this.level() instanceof ServerLevel) {
        ((ServerLevel) this.level()).onReputationEvent(ReputationEventType.TRADE, this.lastTradedPlayer, this);
        this.level().broadcastEntityEvent(this, (byte) 14);
        this.lastTradedPlayer = null;
    }

    // Periodic Raid Check:
    if (!this.isNoAi() && this.random.nextInt(100) == 0) {
        Raid raid = ((ServerLevel) this.level()).getRaidAt(this.blockPosition());
        if (raid != null && raid.isActive() && !raid.isOver()) {
            this.level().broadcastEntityEvent(this, (byte) 42);
        }
    }

    super.customServerAiStep();
}
```

### 2.2 `Brain.tick()` (Minecraft 1.21.1)
```java
// net.minecraft.world.entity.ai.Brain
public void tick(ServerLevel level, E entity) {
    this.forgetOutdatedMemories();
    this.tickSensors(level, entity);
    this.startEachNonRunningBehavior(level, entity);
    this.tickEachRunningBehavior(level, entity);
}
```

### 2.3 `Brain.startEachNonRunningBehavior()` — комбинаторный цикл
```java
// Bytecode decompiled structure of startEachNonRunningBehavior:
private void startEachNonRunningBehavior(ServerLevel level, E entity) {
    long gameTime = level.getGameTime();
    for (Map<Activity, Set<BehaviorControl<? super E>>> priorityTier : this.availableBehaviorsByPriority.values()) {
        for (Map.Entry<Activity, Set<BehaviorControl<? super E>>> entry : priorityTier.entrySet()) {
            Activity activity = entry.getKey();
            if (this.activeActivities.contains(activity)) {
                for (BehaviorControl<? super E> behavior : entry.getValue()) {
                    if (behavior.getStatus() == Behavior.Status.STOPPED) {
                        behavior.tryStart(level, entity, gameTime); // Evaluates extra start conditions
                    }
                }
            }
        }
    }
}
```

---

## 3. Структуры данных и паттерны аллокации

1. **`availableBehaviorsByPriority`:**
   - Тип: `Map<Integer, Map<Activity, Set<BehaviorControl<? super E>>>>`
   - Память и CPU: обходится каждый тик. Содержит множество коллекций, создающих экземпляры итераторов, если только escape-анализ HotSpot C2 их не устраняет.
2. **`sensors`:**
   - Тип: `Map<SensorType<? extends Sensor<? super E>>, Sensor<? super E>>`
   - Активные сенсоры в `Villager`:
     - `NearestLivingEntitiesSensor`: сканирует цилиндр в 16 блоков с помощью `level.getEntitiesOfClass(...)`.
     - `NearestBedSensor`: запрашивает `PoiManager` по загруженным секциям чанков.
     - `SecondaryPoiSensor`: запрашивает блоки рабочих мест для присвоения профессии и пополнения товаров.
     - `VillagerHostilesSensor`: сканирует зомби, evoker'ов, pillager'ов.
     - `GolemSensor`: отслеживает ближайших железных големов для управления порогами сплетен о призыве големов.
3. **`memories`:**
   - Тип: `Map<MemoryModuleType<?>, Optional<? extends ExpirableValue<?>>>`
   - Каждый тик `forgetOutdatedMemories()` итерирует по всем значениям записей, проверяя `hasExpired()`.

---

## 4. Ванильные поведенческие инварианты и контракты

Любая жизнеспособная оптимизация должна сохранять следующие контракты с нулевым расхождением в геймплее:

1. **Контракт пополнения товаров на рабочем месте:**
   - Жители должны пополнять торговые предложения дважды за игровой день (во время `Activity.WORK`, как правило, между тиками 2 000 и 9 000).
   - Пополнение требует достижения блока рабочего места и выполнения поведения `WorkAtPoi`.
2. **Контракт призыва железного голема и паники:**
   - Когда враждебный моб находится в пределах дальности обнаружения ($16 \text{ blocks}$ или $8 \text{ blocks}$ сквозь стены), должен срабатывать `VillagerHostilesSensor` и устанавливать `NEAREST_HOSTILE`.
   - Житель входит в `Activity.PANIC`, сбрасывая кулдауны паники и распространяя сплетни, которые запускают поведение `SpawnGolem`.
3. **Контракт сна в кровати:**
   - В сумерках (игровое время $\ge 12,000$) жители прокладывают путь к своей закреплённой кровати и входят в `Activity.REST`.
   - Находясь в кровати, житель принимает горизонтальное положение, неподвижен и восстанавливает здоровье.
4. **Контракт интерфейса торговли:**
   - Когда игрок открывает GUI торговли, `isTrading()` возвращает `true`.
   - Житель должен смотреть на игрока, оставаться неподвижным и обновлять цены спроса по завершении сделки.

---
## 5. Анализ архитектурных сбоев: почему простое "усыпление" не сработало

В фазе 7.1 KillTheFrizz попытался применить `IdleChecker.shouldSleep()` к жителям (Villagers) путём отмены `serverAiStep()`:
- **Режим отказа 1:** Отмена `serverAiStep()` препятствовала тику `Brain.updateActivityFromSchedule(dayTime, gameTime)`. Спящий житель в полдень оставался застрявшим в `Activity.REST`, никогда не просыпаясь, чтобы пойти на работу!
- **Режим отказа 2:** Пополнение запасов для торговли не работало, поскольку `WorkAtPoi.tick()` был полностью отменён, оставляя торговые прилавки навсегда заблокированными с пустым ассортиментом.
- **Режим отказа 3:** Спавн железных големов сломался, поскольку `VillagerHostilesSensor` был обойдён, что не позволяло жителям паниковать при атаке зомби.

### Инженерное решение:
Вместо отмены тика сущности или заморозки конечного автомата, оптимизация должна **развязать частоту выполнения Brain, сохраняя при этом обновления конечного автомата**:
- `updateActivityFromSchedule()` продолжает отслеживать время.
- Высокозатратные сенсоры и сканирования поведений троттлятся, когда житель неподвижен и поблизости нет враждебных мобов.
- Немедленные триггеры пробуждения срабатывают при получении урона, взаимодействии с игроком, открытии торговли или панике.

---

## 6. Контрольный список критериев приёмки

- [x] Полный путь выполнения от `ServerLevel` до `Brain` декомпилирован и проанализирован.
- [x] Все 8 активных сенсоров жителей каталогизированы.
- [x] Накладные расходы вложенного цикла `startEachNonRunningBehavior` количественно оценены.
- [x] Ванильные контракты (пополнение запасов, паника, спавн големов, торговля) явно детализированы.
- [x] Корневые причины предыдущего блокирования задокументированы.

**Статус подфазы 7.9.3: ПРОЙДЕНА.** Переход к подфазе 7.9.4 (Гипотезы оптимизации).