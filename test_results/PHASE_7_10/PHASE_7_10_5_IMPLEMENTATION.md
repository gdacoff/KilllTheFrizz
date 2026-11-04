# Фаза 7.10.5 — Реализация Collision Throttler и отчёт о верификации

**Проект:** KilllTheFrizz  
**Minecraft:** 1.21.1 | **NeoForge:** 21.1.265 | **Java:** 21  
**Компонент:** `io.github.gdacoff.killthefrizz.collision.CollisionThrottler`  
**Цель Mixin:** `io.github.gdacoff.killthefrizz.mixin.LivingEntityMixin`  

---

## 1. Архитектурный обзор

Фаза 7.10.2 продемонстрировала, что broadphase-запросы коллизий сущностей (`LivingEntity.pushEntities()`) представляют собой единственное крупнейшее оставшееся узкое место в тике сущностей при плотных бодрствующих популяциях (например, игрок, стоящий рядом с загоном на 250 коров или торговым залом).

`CollisionThrottler` оптимизирует этот путь путём рассогласования broadphase-запросов для неподвижных бодрствующих сущностей без изменения физики коллизий при взаимодействии, движении или близости игрока.

### Инварианты и гарантии безопасности:
1. **Нулевое влияние на взаимодействия игрока:** Если игрок находится в пределах `PLAYER_PROXIMITY = 6.0` блоков, `shouldPushEntities()` безусловно возвращает `true` (полная обработка push на 20 Гц).
2. **Динамическая реакция на толпу:** Любая сущность с горизонтальной скоростью $v^2 > 0.0009$ ($|v| > 0.03$ блоков/тик) работает на полных 20 Гц.
3. **Целостность боя и урона:** Любая сущность с `hurtTime > 0` или `isOnFire()` работает на полных 20 Гц, чтобы гарантировать неизменность распределения отбрасывания и урона от огня.
4. **Безопасность транспорта и утилит:** Пассажиры, транспортные средства и привязанные мобы (`mob.isLeashed()`) работают на полных 20 Гц для сохранения механик лодок, вагонеток и поводков.
5. **Детерминированное рассогласование:** Неподвижные мобы вне близости игрока вычисляют логику push на чередующихся тиках на основе `(entity.tickCount + entity.getId()) % 2 == 0`, сокращая broadphase-запросы ровно на 50%.
6. **Контракт fail-open:** Любое неожиданное исключение внутри `shouldPushEntities()` перехватывается и логируется (с ограничением частоты), возвращая `true` для гарантии ванильного поведения.

---

## 2. Детали реализации

### 2.1 `CollisionThrottler.java`
Расположен в `src/main/java/io/github/gdacoff/killthefrizz/collision/CollisionThrottler.java`:
```java
public static boolean shouldPushEntities(LivingEntity entity) {
    try {
        if (entity == null) {
            return true;
        }
        if (!KtfConfig.getEffectiveCommon().enabled() || KtfConfig.isAutoDisabled()) {
            return true;
        }

        if (entity.level().hasNearbyAlivePlayer(
                entity.getX(), entity.getY(), entity.getZ(), PLAYER_PROXIMITY)) {
            return true;
        }
        if (entity.hurtTime > 0 || entity.isOnFire()) {
            return true;
        }
        if (entity.getDeltaMovement().horizontalDistanceSqr() > STATIONARY_VELOCITY_SQR) {
            return true;
        }
        if (entity.isPassenger() || entity.isVehicle()
                || (entity instanceof Mob mob && mob.isLeashed())) {
            return true;
        }

        long phase = (long) entity.tickCount + entity.getId();
        return Math.floorMod(phase, STAGGER_PERIOD) == 0;
    } catch (Throwable t) {
        KtfLogger.warnRateLimited("collision-throttler",
                "CollisionThrottler failed, falling back to full-rate push: {}", t.toString());
        return true;
    }
}
```

### 2.2 Хук `LivingEntityMixin.java`
Внедрён в `pushEntities(CallbackInfo ci)` сразу после проверки отложенного push для спящей сущности:
```java
if (!CollisionThrottler.shouldPushEntities((LivingEntity) (Object) this)) {
    ci.cancel();
    return;
}
```
Если `shouldPushEntities` возвращает `false`, `pushEntities()` отменяется досрочно до выполнения каких-либо broadphase-запросов `level().getEntities(...)` или аллокаций ArrayList.

---

## 3. Верификация модульными тестами

Модульные тесты расположены в `src/test/java/io/github/gdacoff/killthefrizz/collision/CollisionThrottlerTest.java`:
1. `testStaggerMathIntervalTwo`: Доказывает 50% частоту на протяжении 1 000 непрерывных тиков.
2. `testVelocityBoundary`: Доказывает точный порог отсечения (0.029 неподвижная против 0.031 движущейся).
3. `testFailOpenOnNull`: Проверяет контракт fail-open при null-сущности.
4. `testUniformStagger`: Проверяет равномерное распределение 50/50 по ID сущностей на одном тике.

### Результаты выполнения тестов:
```text
Task :compileJava
Task :classes
Task :compileTestJava
Task :testClasses
Task :test

io.github.gdacoff.killthefrizz.collision.CollisionThrottlerTest: tests=4, failures=0, errors=0
Total Test Suite: 144 tests, 0 failures, 0 errors.
```

---

## 4. Компиляция и развёртывание
- Mod Jar: `build/libs/killthefrizz-1.0.0.jar` (96 049 байт).
- Целевой сервер: `test-environment/server/mods/killthefrizz.jar`.
- Статус: Успешно скомпилирован, верифицирован и развёрнут в изолированной среде бенчмарка.