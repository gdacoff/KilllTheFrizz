# Phase 7.9.6.6 — Финальная архитектура и план реализации (Final Implementation Architecture)

**Проект:** KilllTheFrizz  
**Платформа:** Minecraft 1.21.1 | NeoForge 21.1.265 | Java 21  
**Ветка:** `phase-7-core-sleepingentities` | **Базовый коммит:** `eb2408b`  
**Дата:** 2026-11-03  
**Статус:** ЗАВЕРШЕНО (План утвержден к реализации)

---

## 1. Сводка архитектурных решений

По результатам этапов 7.9.6.1–7.9.6.5:
1. **Точка вмешательства:** Метод `Villager.customServerAiStep()` вокруг вызова `this.getBrain().tick((ServerLevel)this.level(), this);`.
2. **Тип инъекции:** MixinExtras `@WrapOperation` в `VillagerMixin.java`.
3. **Безаллокационный вычислитель:** `VillagerBrainThrottler.java` в пакете `io.github.gdacoff.killthefrizz.villager`.
4. **Конфигурация:** Новая секция `[sleepingEntities.villagers]` в `killthefrizz-server.toml`, поддержанная в `EffectiveServer` и `Preset`.
5. **Отказоустойчивость (Fail-Open):** Любое исключение в `VillagerBrainThrottler` перехватывается, логируется и возвращает `true` (исполнение ванильного 20 Hz тика).

---

## 2. Диаграмма алгоритма классификации состояний

```
Villager.customServerAiStep()
 │
 ├── [Шлюз 1: Конфигурация]
 │    if (!KtfConfig.getEffectiveServer().villagerThrottlingEnabled() || KtfConfig.isAutoDisabled())
 │        -> return original.call(brain, level, entity); // 100% Ванила
 │
 └── [Шлюз 2: VillagerBrainThrottler.shouldTickBrain(villager)]
      │
      ├── БЫСТРЫЕ ВЫХОДЫ (Всегда 20 Hz):
      │    • villager.isTrading()                      [Игрок в торговом меню]
      │    • villager.hurtTime > 0 || villager.isOnFire() [Урон, горение]
      │    • villager.getBrain().isActive(Activity.PANIC) [Угроза, паника]
      │    • !villager.getNavigation().isDone()        [Идет к цели]
      │    • deltaMovement.horizontalDistanceSqr() > 0.0009 [Движется в пространстве]
      │    • villager.getVillagerData().getProfession() == NONE [Ищет профессию]
      │    └─► return true; (20 Hz ванильный вызов Brain.tick)
      │
      ├── СОСТОЯНИЕ СНА В КРОВАТИ (2 Hz / раз в 10 тиков):
      │    • villager.isSleeping() == true (Лежит в кровати)
      │    • if (Math.floorMod((long) villager.tickCount + villager.getId(), bedInterval) == 0)
      │    │    -> return true;
      │    └─► return false; (Пропуск Brain.tick, экономия 90% CPU)
      │
      └── СТАЦИОНАРНЫЙ ТОРГОВЫЙ БОКС (5 Hz / раз в 4 тика):
           • Скорость < 0.03, навигация завершена, врагов нет
           • if (Math.floorMod((long) villager.tickCount + villager.getId(), stallInterval) == 0)
           │    -> return true;
           └─► return false; (Пропуск Brain.tick, экономия 75% CPU)
```

---

## 3. Схема конфигурации (`killthefrizz-server.toml`)

```toml
[sleepingEntities.villagers]
# Включить адаптивное прореживание тиков мозга жителей.
# Приводит к снижению нагрузки на CPU от жителей на 60-75% при сохранении ванильного геймплея.
enabled = true

# Интервал выполнения тика мозга для стационарных жителей в торговых боксах (в тиках мира).
# Значение 4 означает 5 проверок в секунду (5 Hz) вместо 20.
# Диапазон: 2 - 20. По умолчанию: 4.
stallInterval = 4

# Интервал выполнения тика мозга для жителей, спящих в кроватях (в тиках мира).
# Значение 10 означает 2 проверки в секунду (2 Hz) вместо 20.
# Диапазон: 2 - 40. По умолчанию: 10.
bedInterval = 10
```

### Поддержка в пресетах (`Preset.java`):
- `Preset.OFF`: `enabled = false`.
- `Preset.VANILLA_PLUS`: `enabled = true`, `stallInterval = 4`, `bedInterval = 10`.
- `Preset.SAFE`: `enabled = true`, `stallInterval = 4`, `bedInterval = 10`.
- `Preset.AGGRESSIVE`: `enabled = true`, `stallInterval = 5`, `bedInterval = 12`.

---

## 4. Спецификация изменений в коде

1. **`src/main/java/io/github/gdacoff/killthefrizz/villager/VillagerBrainThrottler.java` (Новый класс):**
   - Статический метод `public static boolean shouldTickBrain(Villager villager, EffectiveServer config)`.
   - Проверки быстрых выходов без создания объектов (`new`).
   - Использование `Math.floorMod((long) tickCount + id, interval)` против переполнения при большом аптайме.
   - Обертывание в `try ... catch (Throwable t)` с выходом в `true` для fail-open.
2. **`src/main/java/io/github/gdacoff/killthefrizz/config/KtfConfig.java` & `EffectiveServer.java` & `Preset.java`:**
   - Добавление полей конфигурации в `Server`, геттеров в `EffectiveServer`, значений в пресеты.
3. **`src/main/java/io/github/gdacoff/killthefrizz/mixin/VillagerMixin.java`:**
   - Обновление `@WrapOperation` на вызов `Brain.tick`:
     При пропуске тика вызывать `KtfProfiler.recordSkippedVillagerBrain()`.
4. **`src/main/java/io/github/gdacoff/killthefrizz/profiling/KtfProfiler.java`:**
   - Добавить счетчик `skippedVillagerBrainCalls`.
   - В отчете отображать процент пропущенных тиков мозга жителя.
5. **Тестирование:**
   - Создать `VillagerBrainThrottlerTest.java` (проверка всех условий, быстрых выходов, граничных значений и fail-open).
   - Запуск полного набора unit-тестов (`./gradlew test`).

Архитектура готова к реализации на Stage 7.