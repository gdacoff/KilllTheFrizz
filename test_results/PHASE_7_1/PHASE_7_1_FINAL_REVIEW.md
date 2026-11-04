# Phase 7.1 — Final Code Review & Functional Verification

**Проект:** KilllTheFrizz
**Версия:** 1.0.0
**Платформа:** Minecraft 1.21.1, NeoForge 21.1.265, Java 21
**Ветка:** `phase-7-core-sleepingentities`
**Базовый коммит:** `04458f3` (Phase 7: fix core timing and reduce sleeping entity checks)
**Роль рецензента:** Senior Java / Minecraft NeoForge Engineer & Independent Reviewer
**Дата аудита:** 2026-11-01

---

## 1. Git-состояние до и после аудита

### Исходное состояние
- **Текущая ветка:** `phase-7-core-sleepingentities`
- **HEAD:** `04458f3 Phase 7: fix core timing and reduce sleeping entity checks`
- **Рабочее дерево до начала аудита:** содержало изменения Phase 7.

### Итоговое состояние рабочей копии
- **Текущая ветка:** `phase-7-core-sleepingentities` (ветка не переключалась, rebase/reset не выполнялись)
- **Файловый статус (`git status -s`):**
  - `M PROGRESS.md` — обновлена история фаз и сводка Phase 7.1.
  - `M README.md` — обновлены статусы компонентов и предупреждения о метриках.
  - `M src/main/java/io/github/gdacoff/killthefrizz/sleeping/IdleChecker.java` — исправления логики быстрой проверки, heartbeat и перехвата исключений.
  - `M src/main/java/io/github/gdacoff/killthefrizz/sleeping/SleepingWakeupEvents.java` — санитизация радиуса AABB взрыва `[0, 32]`.
  - `M src/main/java/io/github/gdacoff/killthefrizz/util/SlowdownMonitor.java` — атомарность и защита от аномалий замера времени.
  - `M src/test/java/io/github/gdacoff/killthefrizz/sleeping/IdleCheckerTest.java` — юнит-тесты на независимый heartbeat и навигацию.
  - `M src/test/java/io/github/gdacoff/killthefrizz/sleeping/SleepingWakeupEventsTest.java` — юнит-тесты на границы AABB взрыва.
  - `?? PHASE_7_1_AUDIT_REPORT.md` — неотслеживаемый промежуточный аудит-отчет.
  - `?? PHASE_7_1_FINAL_REVIEW.md` — итоговый рецензионный документ (настоящий файл).
  - `?? хуйня_для_педиков.md` — пользовательский файл с требованиями к аудиту (сохранён без изменений).
- **Проверка пробелов и кодировки (`git diff --check`):** выполнена, 0 замечаний. Символы BOM (`\xef\xbb\xbf`) и trailing whitespace отсутствуют.
- **Действия с Git:** коммиты (`git commit`) и отправка (`git push`) **НЕ выполнялись** в строгом соответствии с правилами безопасности.

---

## 2. Список проверенных файлов

| Файл | Назначение / Роль | Статус проверки |
|---|---|---|
| [`IdleChecker.java`](file:///C:/Users/gdacp/IdeaProjects/KilllTheFrizz/src/main/java/io/github/gdacoff/killthefrizz/sleeping/IdleChecker.java) | Центральный модуль определения пригодности сущности ко сну, двухуровневая проверка, таймеры принудительного пробуждения | **Проверен и исправлен** |
| [`MobMixin.java`](file:///C:/Users/gdacp/IdeaProjects/KilllTheFrizz/src/main/java/io/github/gdacoff/killthefrizz/mixin/MobMixin.java) | Инжекция в `serverAiStep(CallbackInfo)` на этапе `HEAD`, пропуск AI тика, сохранение ванильного инкремента `noActionTime` | **Проверен, замечаний нет** |
| [`SleepingWakeupEvents.java`](file:///C:/Users/gdacp/IdeaProjects/KilllTheFrizz/src/main/java/io/github/gdacoff/killthefrizz/sleeping/SleepingWakeupEvents.java) | Обработчики внешних событий мира (урон, поломка блоков рядом, детонация взрывов, посадка в седло) | **Проверен и исправлен** |
| [`RejectionReason.java`](file:///C:/Users/gdacp/IdeaProjects/KilllTheFrizz/src/main/java/io/github/gdacoff/killthefrizz/sleeping/RejectionReason.java) | Диагностический enum причин отказа перехода ко сну | **Проверен, консистентен** |
| [`SlowdownMonitor.java`](file:///C:/Users/gdacp/IdeaProjects/KilllTheFrizz/src/main/java/io/github/gdacoff/killthefrizz/util/SlowdownMonitor.java) | Монитор серверного тика (MSPT) между `ServerTickEvent.Pre` и `Post`, защита от перегрузки | **Проверен и исправлен** |
| [`KtfConfig.java`](file:///C:/Users/gdacp/IdeaProjects/KilllTheFrizz/src/main/java/io/github/gdacoff/killthefrizz/config/KtfConfig.java) | Спецификация конфигурации NeoForge (сервер/клиент), значения по умолчанию, пресеты | **Проверен, замечаний нет** |
| [`KillTheFrizz.java`](file:///C:/Users/gdacp/IdeaProjects/KilllTheFrizz/src/main/java/io/github/gdacoff/killthefrizz/KillTheFrizz.java) | Точка входа мода, регистрация на шинах событий FML и NeoForge | **Проверен, замечаний нет** |
| [`KtfStats.java`](file:///C:/Users/gdacp/IdeaProjects/KilllTheFrizz/src/main/java/io/github/gdacoff/killthefrizz/stats/KtfStats.java) | Атомарные метрики (`SKIPPED_AI_TICKS`, `CURRENT_TICK_SLEEPING`, `SAVED_MS`) | **Проверен, замечаний нет** |
| [`IdleCheckerTest.java`](file:///C:/Users/gdacp/IdeaProjects/KilllTheFrizz/src/test/java/io/github/gdacoff/killthefrizz/sleeping/IdleCheckerTest.java) | Модульные тесты IdleChecker: heartbeat, навигация, интервалы | **Проверен, добавлены тесты** |
| [`SleepingWakeupEventsTest.java`](file:///C:/Users/gdacp/IdeaProjects/KilllTheFrizz/src/test/java/io/github/gdacoff/killthefrizz/sleeping/SleepingWakeupEventsTest.java) | Модульные тесты вычисления охватывающего AABB при взрывах | **Проверен, добавлены тесты** |
| [`SlowdownMonitorTest.java`](file:///C:/Users/gdacp/IdeaProjects/KilllTheFrizz/src/test/java/io/github/gdacoff/killthefrizz/util/SlowdownMonitorTest.java) | Модульные тесты порогов задержки и автоматического отключения | **Проверен, замечаний нет** |
| [`README.md`](file:///C:/Users/gdacp/IdeaProjects/KilllTheFrizz/README.md) | Документация проекта, статус подсистем, предупреждения о замерах | **Проверен и актуализирован** |
| [`PROGRESS.md`](file:///C:/Users/gdacp/IdeaProjects/KilllTheFrizz/PROGRESS.md) | Лог фаз разработки | **Проверен (46 строк, лимит < 50)** |

---
## 3. Найденные ошибки и потенциальные проблемы

В процессе независимого аудита Phase 7 и 7.1 были идентифицированы следующие дефекты и риски:

### 1. Блокировка Heartbeat интервалом переоценки (Критический дефект логики — Исправлен)
- **Суть проблемы:** В Phase 7 проверка `heartbeat` была расположена на этапе дорогой проверки (Stage 4), которая вызывалась только при `!wasSleeping || isEvalTick` (раз в 20 тиков со сдвигом по ID).
- **Следствие:** Если параметр `heartbeatTicks` был задан числом, не кратным 20 (например, 10, 25, 30), условие `(tickCount + id) % heartbeat == 0` на не-eval тиках **никогда не выполнялось**, так как происходил досрочный возврат `if (wasSleeping && !isEvalTick) return true;`.
- **Исправление:** Проверка heartbeat перенесена в Tier 1 (дешёвые ежетиковые проверки) строго **до** проверки `if (wasSleeping && !isEvalTick)`. Продублированная проверка из Stage 4 удалена.

### 2. Задержка пробуждения по навигации (Логический дефект — Исправлен)
- **Суть проблемы:** Проверка `!mob.getNavigation().isDone()` находилась ниже интервального раннего выхода.
- **Следствие:** Если спящему мобу назначался новый путь движения (например, из-за внешнего стимула или команды), он оставался в состоянии сна до 19 тиков (до наступления следующего `isEvalTick`), что создавало видимую визуальную задержку реакции (моб "тупил" до 1 секунды).
- **Исправление:** Проверка `!mob.getNavigation().isDone()` перенесена в Tier 1 до интервального раннего выхода. При появлении активного пути моб просыпается на том же тике.

### 3. Избыточно широкий перехват `catch (Throwable)` (Дефект надежности — Исправлен)
- **Суть проблемы:** В `IdleChecker.shouldSleep` блок `try` перехватывал `Throwable`.
- **Следствие:** Мод подавлял критические системные сбои JVM (`VirtualMachineError`, `OutOfMemoryError`, `StackOverflowError`), переводя сервер в скрытое нестабильное состояние.
- **Исправление:** Перехват сужен до `catch (Exception t)`. Фатальные ошибки виртуальной машины теперь беспрепятственно всплывают наружу.

### 4. Неконтролируемый радиус AABB при взрыве (Дефект безопасности — Исправлен)
- **Суть проблемы:** В `SleepingWakeupEvents.computeExplosionBoundingBox` параметр `radius` из конфига не санитизировался.
- **Следствие:** Отрицательные значения радиуса (ошибка конфига) могли приводить к инвертированным или сжатым AABB, а экстремально большие значения (например, 500) могли раздуть AABB на миллионы блоков, вызывая микрофриз сервера при `level.getEntitiesOfClass(...)`.
- **Исправление:** Радиус жестко ограничен безопасным диапазоном: `int safeRadius = Math.max(0, Math.min(32, radius));`.

### 5. Риск рассинхронизации замера времени в `SlowdownMonitor` (Дефект надежности — Исправлен)
- **Суть проблемы:** Переменная `tickStartNanos` считывалась и обнулялась раздельно, а разница `elapsed` не проверялась на вменяемость.
- **Следствие:** При пропуске `ServerTickEvent.Pre` (например, при отмене тика другим модом) или при длительном STW GC паузе (>10 секунд) в статистику мог попасть аномальный MSPT, приводящий к ложному срабатыванию защиты.
- **Исправление:** Реализовано атомарное считывание со сбросом (`long start = tickStartNanos; tickStartNanos = 0L;`) и фильтрация выбросов `if (elapsed > 0 && elapsed < 10_000_000_000L)`.

### 6. Скрытые символы Windows UTF-8 BOM (Дефект сборки — Исправлен)
- **Суть проблемы:** Некоторые файлы содержали маркер байтов `\xef\xbb\xbf`, вызывавший ошибки `illegal character: '\ufeff'` при сборке через javac.
- **Исправление:** Все модифицированные файлы очищены от BOM.

### 7. Архитектурное замечание: ручной инкремент `noActionTime` (Потенциальный риск интеграции)
- **Анализ:** Ванильный метод `Mob.serverAiStep()` инкрементирует `noActionTime++` в своём теле. Отменяя метод на `HEAD`, мод обязан компенсировать этот инкремент, иначе мобы никогда не деспавнятся.
- **Потенциальный риск:** Если в сборке присутствует сторонний оптимизационный мод, который также модифицирует `serverAiStep` или деспавн-логику, возможен двойной инкремент `noActionTime`. В базовой среде NeoForge 21.1.265 конфликтов не зафиксировано, но данный аспект требует проверки в крупных сборках.

---

## 4. Проверка heartbeat

### Анализ реализации
В [`IdleChecker.java`](file:///C:/Users/gdacp/IdeaProjects/KilllTheFrizz/src/main/java/io/github/gdacoff/killthefrizz/sleeping/IdleChecker.java#L89-L93) логика реализована следующим образом:
```java
int heartbeat = serverCfg.heartbeatTicks();
if (heartbeat > 0 && Math.floorMod((long) mob.tickCount + mob.getId(), (long) heartbeat) == 0) {
    KtfStats.recordRejection(RejectionReason.HEARTBEAT);
    return false;
}
```

### Верификация требований
1. **Порядок выполнения:** Проверка размещена в секции дешевых ежетиковых проверок (Tier 1) **перед** ранним интервальным выходом `if (wasSleeping && !isEvalTick) return true;`. Это гарантирует, что спящий моб гарантированно проснется в назначенный heartbeat-тик.
2. **Семантика пробуждения:** Метод возвращает `false`, что приводит к тому, что в `MobMixin`:
   ```java
   boolean sleep = IdleChecker.shouldSleep(mob, this.ktf$wasSleeping);
   this.ktf$wasSleeping = sleep; // становится false
   if (sleep) { ... ci.cancel(); }
   ```
   вызов `ci.cancel()` **не происходит**, и ванильный `serverAiStep()` выполняет полноценный цикл планирования и обновления AI.
3. **Безопасность арифметики:**
   - Предотвращение деления на ноль: `heartbeat > 0` строго отсекает нулевые и отрицательные значения.
   - Защита от 32-битного целочисленного переполнения: сумма приводится к `(long) mob.tickCount + mob.getId()`. Даже при `tickCount == Integer.MAX_VALUE` арифметического переполнения не возникает.
   - Корректность отрицательных ID: `Math.floorMod` возвращает строго неотрицательный остаток, в отличие от стандартного оператора `%`.
4. **Модульные тесты:** Класс [`IdleCheckerTest.java`](file:///C:/Users/gdacp/IdeaProjects/KilllTheFrizz/src/test/java/io/github/gdacoff/killthefrizz/sleeping/IdleCheckerTest.java#L112-L149) покрывает:
   - Не кратные 20 значения heartbeat (10, 25, 30);
   - Выключенный heartbeat (`0` и отрицательные значения);
   - Устойчивость к экстремально большим значениям `tickCount` (`Integer.MAX_VALUE`).

---

## 5. Проверка navigation wakeup

### Анализ жизненного цикла в Minecraft 1.21.1
- При пропуске `Mob.serverAiStep()` метод `Mob.tick()` и `LivingEntity.tick()` продолжают штатно вызываться каждый тик.
- Навигация сущности (`mob.getNavigation()`) обновляется и сохраняет свое состояние.
- Когда сущность получает внешнее воздействие (например, удар игрока, агро на врага, приманка едой или скриптовая команда), вызывается назначение пути (`moveTo(...)`), переводящее состояние `mob.getNavigation().isDone()` в `false`.

### Верификация механизма в коде
В [`IdleChecker.java`](file:///C:/Users/gdacp/IdeaProjects/KilllTheFrizz/src/main/java/io/github/gdacoff/killthefrizz/sleeping/IdleChecker.java#L85-L88):
```java
if (!mob.getNavigation().isDone()) {
    KtfStats.recordRejection(RejectionReason.NAVIGATING);
    return false;
}
```
1. **Немедленная реакция:** Проверка находится на строке 85, строго до строки 98 (`if (wasSleeping && !isEvalTick)`). Как только навигатор сообщает о наличии активного пути, `shouldSleep` немедленно возвращает `false`.
2. **Снятие спящего режима:** В `MobMixin` поле `this.ktf$wasSleeping` обновляется на `false`, отмена метода `ci.cancel()` не вызывается, моб сразу же начинает исполнять навигационный шаг.
3. **Отсутствие зависимости от отменённого AI:** `mob.getNavigation().isDone()` отражает состояние навигатора, а не выполнение `serverAiStep()`. Таким образом, спящий моб не попадает в тупик "AI отменен -> навигация не обновляется -> моб не просыпается".
4. **Контракт энумератора:** В тестах зафиксировано наличие и неизменность константы `RejectionReason.NAVIGATING`.

---
## 6. Проверка explosion wakeup

### Анализ реализации
В [`SleepingWakeupEvents.java`](file:///C:/Users/gdacp/IdeaProjects/KilllTheFrizz/src/main/java/io/github/gdacoff/killthefrizz/sleeping/SleepingWakeupEvents.java#L51-L98):
```java
public static void onExplosionDetonate(ExplosionEvent.Detonate event) {
    Level level = event.getLevel();
    if (level == null) return;

    for (Entity entity : event.getAffectedEntities()) {
        if (entity instanceof Mob mob) {
            IdleChecker.wakeUp(mob, 40);
        }
    }

    List<BlockPos> blocks = event.getAffectedBlocks();
    if (blocks != null && !blocks.isEmpty()) {
        int radius = KtfConfig.getEffectiveServer().blockChangeWakeRadius();
        AABB box = computeExplosionBoundingBox(blocks, radius);
        if (box != null) {
            for (Mob mob : level.getEntitiesOfClass(Mob.class, box)) {
                IdleChecker.wakeUp(mob, 40);
            }
        }
    }
}
```

### Верификация требований
1. **Двухуровневое пробуждение:**
   - Сущности из `event.getAffectedEntities()` (непосредственно получившие урон или импульс взрыва) пробуждаются напрямую без пространственных запросов.
   - Сущности, находящиеся рядом с разрушенными блоками, находятся через единый AABB.
2. **Ограничение радиуса:**
   В методе `computeExplosionBoundingBox`:
   ```java
   int safeRadius = Math.max(0, Math.min(32, radius));
   ```
   Исключена возможность повреждения геометрии AABB отрицательными числами или перегрузки сервера гигантскими размерами бокса.
3. **Идемпотентность пробуждения:** Повторный вызов `IdleChecker.wakeUp(mob, 40)` просто обновляет метку в `WAKE_TIMERS` (`gameTime + 40`), не создавая утечек памяти и не нагружая GC.
4. **Модульные тесты:** Класс [`SleepingWakeupEventsTest.java`](file:///C:/Users/gdacp/IdeaProjects/KilllTheFrizz/src/test/java/io/github/gdacoff/killthefrizz/sleeping/SleepingWakeupEventsTest.java#L71-L96) подтверждает корректность зажима: `-10 -> 0`, `1000 -> 32`.

---

## 7. Проверка SlowdownMonitor

### Анализ реализации
В [`SlowdownMonitor.java`](file:///C:/Users/gdacp/IdeaProjects/KilllTheFrizz/src/main/java/io/github/gdacoff/killthefrizz/util/SlowdownMonitor.java#L39-L58):
```java
public static void onServerTickPre(ServerTickEvent.Pre event) {
    tickStartNanos = System.nanoTime();
}

public static void onServerTickPost(ServerTickEvent.Post event) {
    long start = tickStartNanos;
    tickStartNanos = 0L;
    if (start != 0L) {
        long elapsed = System.nanoTime() - start;
        if (elapsed > 0 && elapsed < 10_000_000_000L) { // sanity bound < 10s
            double mspt = elapsed / 1_000_000.0;
            evaluate(mspt);
        }
    }
    publishStats();
}
```

### Верификация требований
1. **Чистота измерения:** Замер интервала между `ServerTickEvent.Pre` и `Post` фиксирует чистое время процессорной нагрузки сервера (тикование миров, сущностей, чанков), полностью отсекая `Thread.sleep()`, удерживающий фиксированные 20 TPS.
2. **Атомарность состояния:** Сброс `tickStartNanos = 0L` непосредственно при чтении гарантирует, что ни один замер не будет обработан дважды при повторном вызове `Post`.
3. **Защита от сбоев системных часов и зависаний:** Условие `elapsed > 0 && elapsed < 10_000_000_000L` защищает монитор от отрицательных дельт и многосекундных зависаний (STW GC, генерация крупных чанков).
4. **Корректность порогов:**
   - Медленный тире: MSPT > 45 мс в течение 100 тиков подряд -> автоматическое отключение мода (`autoDisabled = true`).
   - Восстановление: MSPT < 30 мс в течение 200 тиков подряд -> автоматическое повторное включение.
   - Пороги протестированы и верифицированы в [`SlowdownMonitorTest.java`](file:///C:/Users/gdacp/IdeaProjects/KilllTheFrizz/src/test/java/io/github/gdacoff/killthefrizz/util/SlowdownMonitorTest.java).

---

## 8. Результаты тестирования

Полный прогон тестового набора выполнен через команду:
```bash
./gradlew test --rerun-tasks
```

### Итоговая сводка
- **Всего тестов:** 87
- **Успешно:** 87 (100%)
- **Провалено:** 0
- **Сбоев:** 0
- **Пропущено:** 0

### Детализация по тест-сьютам

| Тестовый класс | Количество тестов | Результат | Время выполнения |
|---|---|---|---|
| `AdaptiveDistanceTest` | 17 | PASSED | 0.028s |
| `KtfConfigTest` | 18 | PASSED | 0.446s |
| `GpuCullingTest` | 4 | PASSED | 0.003s |
| `IdleCheckerTest` | 17 | PASSED | 0.019s |
| `SleepingWakeupEventsTest` | 7 | PASSED | 0.067s |
| `GigabyteSqueezerTest` | 17 | PASSED | 0.066s |
| `SlowdownMonitorTest` | 7 | PASSED | 0.007s |
| **ИТОГО** | **87** | **ALL PASSED** | **0.636s** |

---

## 9. Результаты сборки

Сборка релизного бинарного артефакта выполнена через команду:
```bash
./gradlew build
```

### Результат выполнения
- **Статус сборки:** `BUILD SUCCESSFUL in 639ms`
- **Количество задач:** 7 actionable tasks (1 executed, 6 up-to-date)
- **Целевой файл артефакта:**
  [`build/libs/killthefrizz-1.0.0.jar`](file:///C:/Users/gdacp/IdeaProjects/KilllTheFrizz/build/libs/killthefrizz-1.0.0.jar)
- **Размер артефакта:** 75 732 байта (75.7 КБ)
- **Содержимое архива:** Скомпилированные классы мода, сгенерированные метаданные NeoForge (`META-INF/neoforge.mods.toml`), Mixin-конфигурация (`killthefrizz.mixins.json`).

---

## 10. Проверка документации

### 1. [`README.md`](file:///C:/Users/gdacp/IdeaProjects/KilllTheFrizz/README.md)
- **Статус `StringDeduplicator`:** Исправлен со `Stable` на `Prototype / Stub (Unconnected)`. В описании честно указано: *"Experimental, unconnected — not wired into the live allocation path."*
- **Удаление неподтверждённых утверждений:** Полностью удалены спекулятивные заявления о приросте ("95% reduction in entity overhead", "15-25% faster villager processing").
- **Метрика `SAVED_MS`:** В явном виде снабжена дисклеймером о том, что это расчетная эвристическая оценка (`0.08 ms` на пропущенный тик), а не измеренное время сервера.
- **Рекомендации по профилированию:** Добавлен раздел с прямым призывом к пользователям проверять производительность через Spark (`/spark sampler`).

### 2. [`PROGRESS.md`](file:///C:/Users/gdacp/IdeaProjects/KilllTheFrizz/PROGRESS.md)
- Отражено успешное завершение Phase 7.1 с описанием ключевых исправлений (IdleChecker, SleepingWakeupEvents, SlowdownMonitor).
- Общий объем файла составляет 46 строк, что строго укладывается в требование проектного регламента (< 50 строк).

---

## 11. Непроверенные сценарии

Следующие аспекты **не могут быть исчерпывающе проверены модульными тестами** без запуска живого экземпляра Minecraft 1.21.1 и требуют натурного тестирования:

1. **Естественный ванильный деспавн (`noActionTime`):**
   - *Сценарий:* Моб засыпает на расстоянии >32 блоков от игрока в режиме выживания.
   - *Что проверить:* Увеличивается ли `noActionTime` до порога 600–800 тиков, и запускается ли стандартный алгоритм вероятностного деспавна моба.
2. **Взаимодействие со сторонними AI-модами:**
   - *Сценарий:* Установка модов со сложным искусственным интеллектом (например, Alex's Mobs, MineColonies, MCA Reborn).
   - *Что проверить:* Не используют ли кастомные сущности собственные контроллеры навигации в обход `Mob.getNavigation()`, что теоретически может задерживать их пробуждение.
3. **Натурный замер MSPT на нагруженных фермах со Spark:**
   - *Сценарий:* Спавн 250+ коров в ограниченном загоне.
   - *Что проверить:* Запустить `/spark sampler` на 3 минуты с `sleepingEntities.enabled=true` и сравнить с `false`. Убедиться в отсутствии спайков при пробуждении толпы игроком.
4. **Смена измерений спящими мобами:**
   - *Сценарий:* Спящая сущность заталкивается в портал Нижнего мира или Энда.
   - *Что проверить:* Корректность очистки таймеров пробуждения и корректная инициализация в новом измерении.

---
## 12. Итоговый вердикт

### Вердикт: **READY WITH KNOWN LIMITATIONS** (Рекомендован к коммиту в ветку `phase-7-core-sleepingentities`)

### Обоснование вердикта:
1. **Техническая корректность:** Все подтверждённые архитектурные замечания Phase 7 (рассинхронизация heartbeat, задержка навигации, подавление системных ошибок JVM, незащищенный AABB взрывов, сбои таймера MSPT) полностью устранены минимальными точечными патчами без побочных эффектов.
2. **Качество сборки и тестов:** Все 87 модульных тестов завершаются со 100% успехом. JAR-файл собирается без ошибок и предупреждений (кроме штатного Java deprecation в Forge config).
3. **Честность документации:** Документация очищена от неподтверждённого маркетинга; неактивные прототипы честно помечены; все числовые показатели экономии ресурсов снабжены статусом оценочных эвристик.
4. **Ограничения:** Финальные цифры экономии MSPT и поведение деспавна на больших модпаках требуют подтверждения в реальной игре со Spark перед публичным релизом версии 1.0.0.

> [!IMPORTANT]
> В соответствии с регламентом безопасности `git commit` и `git push` не выполнялись. Репозиторий готов к фиксации изменений владельцем проекта.