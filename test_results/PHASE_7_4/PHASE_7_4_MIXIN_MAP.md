# Фаза 7.4 — Спецификация Mixin-инъекций и хуков

**Проект:** KilllTheFrizz  
**Платформа:** Minecraft 1.21.1 / NeoForge 21.1.265 / Java 21  
**Стандарт безопасности:** `mixin-safety-audit` (строгий запрет `@Redirect`, приоритет MixinExtras)  

В данном документе приведена детальная спецификация точек интеграции для пяти подсистем архитектуры Phase 7.4.

---

## 1. Сводная карта инъекций

| Класс Minecraft | Метод | Тип инъекции | Целевая операция | Подсистема |
|---|---|---|---|---|
| `net.minecraft.world.entity.Mob` | `serverAiStep()` | `@Inject(at=@At("HEAD"), cancellable=true)` | Отмена AI целей и навигации | AI Sleep Manager |
| `net.minecraft.world.entity.npc.Villager` | `customServerAiStep()` | `@Inject(at=@At("HEAD"), cancellable=true)` | Троттлинг Brain жителей | AI Sleep Manager |
| `net.minecraft.world.entity.ai.Brain` | `tickSensors(ServerLevel, E)` | `@WrapOperation(at=@At(value="INVOKE", target="Lnet/minecraft/world/entity/ai/sensing/Sensor;tick(...)V"))` | Заморозка поиска POI/кроватей | AI Sleep Manager |
| `net.minecraft.world.entity.LivingEntity` | `pushEntities()` | `@WrapOperation(at=@At(value="INVOKE", target="Lnet/minecraft/world/entity/LivingEntity;doPush(Lnet/minecraft/world/entity/Entity;)V"))` | Демпфирование взаимного расталкивания спящих мобов | Physics Sleep Manager |
| `net.minecraft.world.entity.LivingEntity` | `handleRelativeFrictionAndCalculateMovement(...)` | `@WrapOperation(at=@At(value="INVOKE", target="Lnet/minecraft/world/entity/LivingEntity;move(Lnet/minecraft/world/entity/MoverType;Lnet/minecraft/world/phys/Vec3;)V"))` | Пропуск `move()` при нулевой скорости на твёрдой опоре | Physics Sleep Manager |
| `net.minecraft.world.entity.Entity` | Fields | `@Unique` interface injection | Кэширование флагов состояния | Entity State Tracker |

---

## 2. Детальное описание точек инъекции

### Инъекция 1: Goal AI Cancellation
- **Класс:** `net.minecraft.world.entity.Mob`
- **Метод:** `protected final void serverAiStep()`
- **Точка:** `@At("HEAD")`, `cancellable = true`
- **Код прототипа:**
  ```java
  @Inject(method = "serverAiStep", at = @At("HEAD"), cancellable = true)
  private void ktf$onServerAiStep(CallbackInfo ci) {
      Mob mob = (Mob) (Object) this;
      if (AISleepManager.shouldSkipGoalAi(mob)) {
          mob.setNoActionTime(mob.getNoActionTime() + 1); // Защита ванильного деспавна
          KtfStats.recordSkippedAiTick();
          ci.cancel();
      }
  }
  ```
- **Инвариант безопасности:** `noActionTime` обязательно инкрементируется при отмене, гарантируя штатную работу ванильного таймера деспавна на дистанциях >32 блоков.

---

### Инъекция 2: Villager Brain Throttling
- **Класс:** `net.minecraft.world.entity.npc.Villager`
- **Метод:** `protected void customServerAiStep()`
- **Точка:** `@At("HEAD")`, `cancellable = true`
- **Код прототипа:**
  ```java
  @Inject(method = "customServerAiStep", at = @At("HEAD"), cancellable = true)
  private void ktf$onVillagerCustomServerAiStep(CallbackInfo ci) {
      Villager villager = (Villager) (Object) this;
      if (AISleepManager.shouldThrottleVillager(villager)) {
          ci.cancel(); // Пропускает getBrain().tick() на промежуточных тиках
      }
  }
  ```
- **Инвариант безопасности:** Не вызывается во время торговли с игроком (`villager.isTrading()`), рейдов или паники (`Activity.PANIC`).

---

### Инъекция 3: Sensor & POI Acquisition Optimization
- **Класс:** `net.minecraft.world.entity.ai.Brain`
- **Метод:** `private void tickSensors(ServerLevel level, E brainHolder)`
- **Точка:** `@WrapOperation(method = "tickSensors", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/ai/sensing/Sensor;tick(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/LivingEntity;)V"))`
- **Код прототипа:**
  ```java
  @WrapOperation(method = "tickSensors", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/ai/sensing/Sensor;tick(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/LivingEntity;)V"))
  private void ktf$wrapSensorTick(Sensor<?> sensor, ServerLevel level, LivingEntity entity, Operation<Void> original) {
      if (AISleepManager.canSkipSensor(sensor, entity)) {
          return; // Пропуск тяжелого POI / A* поиска
      }
      original.call(sensor, level, entity);
  }
  ```
- **Инвариант безопасности:** Безопасно для Mixin-цепочек других модов (Lithium/Canary), так как используется `@WrapOperation` вместо хрупкого `@Redirect`.

---

### Инъекция 4: Mutual Push Damping (Решение проблемы 250 коров)
- **Класс:** `net.minecraft.world.entity.LivingEntity`
- **Метод:** `protected void pushEntities()`
- **Точка:** `@WrapOperation(method = "pushEntities", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;doPush(Lnet/minecraft/world/entity/Entity;)V"))`
- **Код прототипа:**
  ```java
  @WrapOperation(method = "pushEntities", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;doPush(Lnet/minecraft/world/entity/Entity;)V"))
  private void ktf$wrapDoPush(LivingEntity self, Entity other, Operation<Void> original) {
      // Игроки ВСЕГДА расталкивают мобов
      if (other instanceof Player || self instanceof Player) {
          original.call(self, other);
          return;
      }
      // Если ОБА моба спят в загоне — блокируем импульс взаиморасталкивания
      if (PhysicsSleepManager.isMutualResting(self, other)) {
          return; // Предотвращает разгон скорости > 0.0004
      }
      original.call(self, other);
  }
  ```
- **Инвариант безопасности:** Урон от удушья (`maxEntityCramming`) рассчитывается **до** вызова `doPush()` на строке 2886 `LivingEntity.java` и остаётся полностью ванильным.

---

### Инъекция 5: Stationary Move Bypass (Блокировка холостых Voxel-коллизий)
- **Класс:** `net.minecraft.world.entity.LivingEntity`
- **Метод:** `public Vec3 handleRelativeFrictionAndCalculateMovement(Vec3 deltaMovement, float friction)`
- **Точка:** `@WrapOperation(method = "handleRelativeFrictionAndCalculateMovement", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;move(Lnet/minecraft/world/entity/MoverType;Lnet/minecraft/world/phys/Vec3;)V"))`
- **Код прототипа:**
  ```java
  @WrapOperation(method = "handleRelativeFrictionAndCalculateMovement", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;move(Lnet/minecraft/world/entity/MoverType;Lnet/minecraft/world/phys/Vec3;)V"))
  private void ktf$wrapMove(LivingEntity self, MoverType type, Vec3 movement, Operation<Void> original) {
      if (PhysicsSleepManager.isStationaryOnGround(self, movement)) {
          // Моб полностью неподвижен на твёрдом блоке:
          // Пропускаем обход VoxelShape коллизий чанка
          self.setDeltaMovement(Vec3.ZERO);
          return;
      }
      original.call(self, type, movement);
  }
  ```
- **Инвариант безопасности:** Если блок под ногами разрушен (проверка через `NeighborNotifyEvent`), кэш сбрасывается и моб падает по стандартной ванильной физике.