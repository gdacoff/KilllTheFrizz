# Отчёт о реализации фазы 7.6 — диагностический профилировочный движок KtfProfiler

## 1. Краткое резюме

- **Компонент:** `io.github.gdacoff.killthefrizz.profiling.KtfProfiler`
- **Платформа:** Minecraft 1.21.1 / NeoForge 21.1.265 / Java 21 / MixinExtras 0.5.5
- **Ветка:** `phase-7-core-sleepingentities`
- **Базовый коммит:** `eb2408b`
- **Статус:** Полностью реализовано, проверено 12 новыми модульными тестами (всего 99/99 в наборе), изменений в основной логике sleeping не внесено.

Фаза 7.6 реализует диагностический профилировщик, спроектированный в фазе 7.5. Он обеспечивает прямое измерение горячего пути `SleepingEntities` в игре, разделяя накладные расходы, время выполнения AI, отменённые вызовы AI и время коллизий сущностей.

---

## 2. Архитектура и принципы проектирования

### A. Нулевые накладные расходы в неактивном состоянии
- `recording` — единственное поле `volatile boolean`, инициализированное значением `false`.
- Вызывающий код защищает каждую операцию записи условием `if (KtfProfiler.isRecording())`.
- В отключённом состоянии (по умолчанию) не происходит вызовов `System.nanoTime()`, не создаются объекты, не захватываются блокировки синхронизации и не накапливается статистика.
- JIT-компилятор встраивает проверки `KtfProfiler.isRecording()` с почти нулевыми накладными расходами по инструкциям (< 0.5 нс).

### B. Модель конкурентности с единственным серверным потоком
- Методы тика сущностей на горячем пути (`Mob.serverAiStep()`, `IdleChecker.shouldSleep()`, `LivingEntity.pushEntities()`) выполняются исключительно в серверном потоке.
- Внутренние метрики-аккумуляторы (`idleCheckCalls`, `idleCheckTotalNanos`, `idleCheckMaxNanos`, `serverAiStepCalls`, `serverAiStepTotalNanos`, `serverAiStepMaxNanos`, `skippedAiCalls`, `pushEntitiesCalls`, `pushEntitiesTotalNanos`, `pushEntitiesMaxNanos`) хранятся в примитивных полях `long` и обновляются без конкуренции за блокировки.
- Управление жизненным циклом (`start`, `stop`, `reset`) использует модификаторы `synchronized` для обеспечения видимости памяти и атомарности переходов состояния при вызове из команд или внешних потоков.
- Мгновенные снимки статистики публикуются через неизменяемую запись `Snapshot` (`KtfProfiler.Snapshot`).

### C. Автоматические сессии, ограниченные тиками
- Запуск профилировщика через `/killthefrizz profile start [ticks]` устанавливает обратный отсчёт тиков.
- `KtfProfiler.onServerTickPost(ServerTickEvent.Post event)` зарегистрирован в `NeoForge.EVENT_BUS`.
- На каждом серверном тике выполняется декремент `remainingTicks`. Когда счётчик достигает нуля, профилировщик автоматически останавливается, фиксирует замороженный `Snapshot` и устанавливает `recording = false`.

---

## 3. Инструментирование Mixin и аудит безопасности

### A. `MobMixin.java` (инструментирование AI-шага и проверки простоя)
- **Целевой метод:** `net.minecraft.world.entity.Mob#serverAiStep()`
- **Точки внедрения:**
  1. `@At("HEAD")`, `cancellable = true`:
     - Сбрасывает `@Unique private long ktf$aiStartNanos = 0L;` для защиты от остаточного состояния.
     - Если `KtfProfiler.isRecording()` активен, записывает `t0 = System.nanoTime()`.
     - Выполняет `boolean sleep = IdleChecker.shouldSleep(mob, this.ktf$wasSleeping);`.
     - Записывает `KtfProfiler.recordIdleCheck(System.nanoTime() - t0)`.
     - Если `sleep == true`:
       - Инкрементирует `KtfProfiler.recordSkippedAi()`.
       - Инкрементирует `mob.setNoActionTime(...)`.
       - Записывает `KtfStats.recordSkippedAiTick()`.
       - Вызывает `ci.cancel()`.
       - Немедленно возвращает управление.
     - Если `sleep == false` (бодрствующая сущность):
       - Устанавливает `this.ktf$aiStartNanos = System.nanoTime();`.
       - Позволяет выполниться телу ванильного `serverAiStep()`.
  2. `@At("RETURN")`:
     - Проверяет `if (this.ktf$aiStartNanos != 0L)`.
     - Вычисляет `duration = System.nanoTime() - this.ktf$aiStartNanos`.
     - Сбрасывает `this.ktf$aiStartNanos = 0L`.
     - Записывает `KtfProfiler.recordServerAiStep(duration)`.

> [!IMPORTANT]
> **Гарантия отмены:**
> При `sleep == true` вызов `ci.cancel()` завершает выполнение метода на HEAD. Поскольку `this.ktf$aiStartNanos` равен `0L`, отменённые вызовы AI **НИКОГДА** не учитываются как выполненные вызовы AI, и их длительность не накапливается.

### B. `LivingEntityMixin.java` (инструментирование коллизий и толкания)
- **Целевой метод:** `net.minecraft.world.entity.LivingEntity#pushEntities()`
- **Зарегистрирован в:** `killthefrizz.mixins.json`
- **Точки внедрения:**
  1. `@At("HEAD")`:
     - Если идёт запись, сохраняет `this.ktf$pushStartNanos = System.nanoTime();`, иначе сбрасывает в `0L`.
  2. `@At("RETURN")`:
     - Если `this.ktf$pushStartNanos != 0L`:
       - Вычисляет `duration = System.nanoTime() - this.ktf$pushStartNanos`.
       - Сбрасывает `this.ktf$pushStartNanos = 0L`.
       - Записывает `KtfProfiler.recordPushEntities(duration)`.

---

## 4. Диагностические команды

Интегрированы в `/killthefrizz profile` через Brigadier в `KtfCommands.java`:

| Команда | Права | Описание |
|---|---|---|
| `/killthefrizz profile start [ticks]` | Уровень 2 (OP) | Запускает запись на указанное число тиков (по умолчанию: 100). |
| `/killthefrizz profile stop` | Уровень 2 (OP) | Вручную останавливает активную сессию и замораживает текущий снимок. |
| `/killthefrizz profile report` | Уровень 0 (все) | Выводит вызывающему отформатированный отчёт с метриками. |
| `/killthefrizz profile reset` | Уровень 2 (OP) | Останавливает профилировщик и обнуляет все накопленные счётчики и снимки. |

### Пример формата вывода
```text
KilllTheFrizz profiler: STOPPED
  Recorded ticks: 100
  Remaining ticks: 0
  IdleCheck:
    calls: 25000
    total: 31.25 ms
    avg:   1250 ns
    max:   42100 ns
  serverAiStep:
    calls: 7800
    total: 21.84 ms
    avg:   2800 ns
    max:   89400 ns
  pushEntities:
    calls: 25000
    total: 855.00 ms
    avg:   34200 ns
    max:   145000 ns
  Skipped AI calls: 17200 (68.80% of 25000)
```

---

## 5. Верификация и набор тестов

Набор модульных тестов `KtfProfilerTest.java` (12 тестов) подтверждает:
1. `inactiveByDefault`: Профилировщик запускается отключённым; начальный снимок пуст.
2. `noAccumulationWhenInactive`: Вызовы записи не изменяют статистику в неактивном состоянии.
3. `accumulatesWhenActive`: В активном состоянии записываются вызовы, суммарные наносекунды, максимальные наносекунды и средние наносекунды.
4. `separatesSkippedAndExecutedAi`: Отменённые/пропущенные вызовы AI не учитываются как выполненные вызовы AI.
5. `autoStopsAfterSpecifiedTicks`: Автоматически отключается после заданного числа тиков.
6. `resetClearsEverything`: Сбрасывает аккумуляторы и снимок в пустое состояние.
7. `formatReportContainsKeyFields`: Вывод отчёта содержит все разделы, единицы измерения и теги статуса.
8. `invalidTickCountClampedToOne`: Неположительные значения тиков безопасно ограничиваются 1 тиком.
9. `repeatedStartResetsAccumulators`: Запуск новой сессии очищает предыдущие данные.
10. `repeatedStopIsIdempotent`: Многократная остановка безопасна и детерминирована.
11. `maxTimeTracksLargestSample`: Точно отслеживает максимальные выбросы длительности выполнения.
12. `tickPostIgnoredWhenInactive`: Серверные события тика игнорируются, когда профилирование не ведётся.

**Выполнение полного набора тестов:**
- Выполнено 99 модульных тестов (`gradlew test --rerun`).
- 0 сбоев, 0 ошибок, 0 пропущено.
- Архив мода собран без ошибок (`gradlew assemble`).