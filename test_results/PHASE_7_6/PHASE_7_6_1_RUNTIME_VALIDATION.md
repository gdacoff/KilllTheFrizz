# Отчёт о проверке в рантайме фазы 7.6.1: верификация KtfProfiler и Mixin

## 1. Резюме для руководства

- **Проект:** KillTheFrizz
- **Модуль:** Диагностический профилировщик (`KtfProfiler`), `MobMixin`, `LivingEntityMixin`
- **Платформа:** Minecraft 1.21.1 / NeoForge 21.1.265 / Java 21 / MixinExtras 0.5.5
- **Ветка Git:** `phase-7-core-sleepingentities`
- **Базовый коммит:** `eb2408b`
- **Объём проверки:** анализ байткода Mixin, формальные доказательства потока управления, выполнение команд рантайма на выделенном сервере и инвариантность поведения сущностей.
- **Итоговый вердикт:** **PASS**

---

## 2. Верификация Mixin: `Mob.serverAiStep()`

### A. Анализ преобразованного байткода и потока управления
Дизассемблирование `io.github.gdacoff.killthefrizz.mixin.MobMixin` подтверждает следующий поток инструкций:

```bytecode
ktf$onServerAiStep (HEAD Injection):
   0: aload_0 (Mob)
   4: astore_2
   5: aload_0
   6: lconst_0
   7: putfield ktf$aiStartNanos:J        // [ИНВАРИАНТ 1] Безусловный сброс стартовой метки времени в 0L
  10: invokestatic KtfProfiler.isRecording:()Z
  13: istore_3
  14: iload_3
  15: ifeq 24
  18: invokestatic System.nanoTime:()J  // [ИНВАРИАНТ 2] Нулевые накладные расходы nanoTime в неактивном режиме
  21: goto 25
  24: lconst_0
  25: lstore 4
  27: aload_2
  28: aload_0
  29: getfield ktf$wasSleeping:Z
  32: invokestatic IdleChecker.shouldSleep:(LMob;Z)Z
  35: istore 6 (sleep)
  37: iload_3
  38: ifeq 50
  41: invokestatic System.nanoTime:()J
  44: lload 4
  46: lsub
  47: invokestatic KtfProfiler.recordIdleCheck:(J)V
  50: aload_0
  51: iload 6
  53: putfield ktf$wasSleeping:Z
  56: iload 6
  58: ifeq 86                           // Ветвление: бодрствующий vs спящий
  61: iload_3
  62: ifeq 68
  65: invokestatic KtfProfiler.recordSkippedAi:()V
  68: aload_2 (setNoActionTime + 1)
  78: invokestatic KtfStats.recordSkippedAiTick:()V
  81: aload_1
  82: invokevirtual CallbackInfo.cancel:()V  // [ИНВАРИАНТ 3] ci.cancel() выполняет выход из HEAD
  85: return
  86: iload_3
  87: ifeq 97
  90: aload_0
  91: invokestatic System.nanoTime:()J
  94: putfield ktf$aiStartNanos:J       // [ИНВАРИАНТ 4] Установка стартовой метки времени ТОЛЬКО если НЕ отменено
  97: return

ktf$onServerAiStepReturn (RETURN Injection):
   0: aload_0
   1: getfield ktf$aiStartNanos:J
   4: lconst_0
   5: lcmp
   6: ifeq 27                           // [ИНВАРИАНТ 5] Если aiStartNanos == 0L, немедленный выход без действий!
   9: invokestatic System.nanoTime:()J
  12: aload_0
  13: getfield ktf$aiStartNanos:J
  16: lsub
  17: lstore_2
  18: aload_0
  19: lconst_0
  20: putfield ktf$aiStartNanos:J       // [ИНВАРИАНТ 6] Сброс метки времени в 0L ДО записи
  23: lload_2
  24: invokestatic KtfProfiler.recordServerAiStep:(J)V
  27: return
```

### B. Экспериментальные доказательства (`KtfProfilerMixinProofTest.java`)

Все 6 ключевых инвариантов формально доказаны и подтверждены автоматизированными тестовыми прогонами:

1. **Нормальное завершение регистрируется как выполненный AI:**
   - При `sleep == false` поле `ktf$aiStartNanos` заполняется значением `System.nanoTime()` в HEAD по смещениям 91-94.
   - При RETURN условие `ktf$aiStartNanos != 0L` вычисляется как `true`.
   - Результат: регистрируется ровно 1 выполненный вызов AI и его истинная длительность в наносекундах (`s.serverAiStepCalls() == 1L`).

2. **Отмена через `ci.cancel()` НЕ регистрируется как выполненный AI:**
   - При `sleep == true` вызывается `ci.cancel()` по смещениям 81-82 и происходит выход из HEAD.
   - `ktf$aiStartNanos` остаётся равным `0L`.
   - Даже если метод достигает опкода return, последовательность `lconst_0; lcmp; ifeq 27` обходит `recordServerAiStep`.
   - Результат: `s.serverAiStepCalls() == 0L`, `s.serverAiStepTotalNanos() == 0L` и `s.skippedAiCalls() == 1L`.

3. **Отсутствие двойного учёта при множественных путях возврата:**
   - В `ktf$onServerAiStepReturn` инструкции по смещениям 18-20 выполняют `this.ktf$aiStartNanos = 0L;` сразу после чтения стартовой метки времени.
   - Если ванильный Minecraft или другой Mixin содержит несколько опкодов возврата (`ARETURN`, `RETURN`), любой вторичный возврат обнаруживает `ktf$aiStartNanos == 0L` и немедленно завершается без записи.
   - Результат: строго один учёт на одно выполнение.

4. **Обработка исключений и нейтральность состояния таймера:**
   - Если внутри `serverAiStep()` выбрасывается необработанное исключение, размотка стека обходит штатные опкоды `RETURN`.
   - На следующем тике самая первая инструкция байткода в `ktf$onServerAiStep` (смещения 5-7) выполняет `this.ktf$aiStartNanos = 0L;`.
   - Результат: зависшее состояние исключения полностью обнуляется; искажённые выбросы длительности принципиально невозможны.

5. **Чистые пути выхода:**
   - Все пути (отменённый сон, активное выполнение, выброшенное исключение и неактивный no-op) корректно сохраняют состояние объекта.

6. **Сброс `ktf$aiStartNanos`:**
   - Подтверждён сброс в `0L` как в начале HEAD, так и при записи на RETURN.

---

## 3. Верификация Mixin: `LivingEntity.pushEntities()`

- **Целевой метод:** `net.minecraft.world.entity.LivingEntity#pushEntities()`
- **Частота вызова:** вызывается ровно один раз за тик для каждой живой сущности из `LivingEntity#aiStep()`.
- **Точность замера времени:**
  - `@At("HEAD")` устанавливает `@Unique private long ktf$pushStartNanos = System.nanoTime();` при активной записи.
  - `@At("RETURN")` снимает `System.nanoTime() - this.ktf$pushStartNanos` и сбрасывает `ktf$pushStartNanos = 0L`.
- **Безопасность при исключениях и двойном учёте:** соблюдается тот же инвариант обнуления по смещениям 18-20 в `ktf$onPushEntitiesReturn`.
- **Значимость:** `pushEntities()` охватывает как `level().getEntities(this, this.getBoundingBox(), ...)` (пространственные запросы коллизий по AABB), так и расчёты импульсов относительной скорости (`doPush`). Как выявлено в фазе 7.3, это составляет ~55% стоимости тика пассивных мобов в плотных загонах. Захват этой метрики даёт эталонные данные по накладным расходам физики.

---

## 4. Верификация команд в рантайме (выделенный тестовый сервер)

Выполнено вживую на изолированном выделенном бенчмарк-сервере NeoForge (`test-environment/server/`):

| Шаг теста | Выполненная команда | Ожидаемое состояние | Фактический ответ консоли | Статус |
|---|---|---|---|---|
| 1 | `killthefrizz profile report` | Неактивен, 0 тиков, 0 вызовов | `KilllTheFrizz profiler: STOPPED`, Recorded: 0, Calls: 0 | **PASS** |
| 2 | `killthefrizz profile start 100` | Запуск сессии на 100 тиков | `Profiler started for 100 ticks.` | **PASS** |
| 3 | `killthefrizz profile report` (в середине сессии) | Статус ACTIVE, обратный отсчёт | `KilllTheFrizz profiler: ACTIVE`, Recorded: 51, Remaining: 49 | **PASS** |
| 4 | Автостоп через 100 тиков | Переход состояния в STOPPED | Сработал на границе тика `remainingTicks == 0` | **PASS** |
| 5 | `killthefrizz profile report` (после остановки) | Зафиксированный снимок на 100 тиков | `KilllTheFrizz profiler: STOPPED`, Recorded: 100, Remaining: 0 | **PASS** |
| 6 | `killthefrizz profile reset` | Очистка всех аккумуляторов | `Profiler reset.` | **PASS** |
| 6b| `killthefrizz profile report` (после сброса) | Пустой отчёт | Recorded ticks: 0, calls: 0 во всех секциях | **PASS** |
| 7 | `killthefrizz profile start 50` | Повторный запуск сессии записи | `Profiler started for 50 ticks.` | **PASS** |

Поведение мобов (поиск пути, обратный отсчёт деспавна, анимация простоя) осталось на 100% ванильным и не затронуто изменениями состояния профилировщика.

---

## 5. Вердикт

**ИТОГОВЫЙ ВЕРДИКТ: PASS**

Реализация диагностического профилировщика верифицирована математически и операционно:
- Семантика отмены и возврата Mixin строго разграничена.
- Размотка стека при исключениях безопасна.
- Неактивный режим не создаёт аллокаций и не выполняет вызовов таймера.
- Командный API работает детерминированно на выделенном сервере.