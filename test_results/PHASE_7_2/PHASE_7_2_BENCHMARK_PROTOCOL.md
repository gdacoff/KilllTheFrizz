# Фаза 7.2 — Воспроизводимый протокол бенчмарка: SAFE vs OFF

**Проект:** KilllTheFrizz  
**Тестируемый модуль:** `SleepingEntities`  
**Платформа:** Minecraft 1.21.1 / NeoForge 21.1.265 / Java 21  
**Инструмент профилирования:** Spark 1.10.124-neoforge  

---

## 1. Научный регламент и защита достоверности (mc-benchmark-integrity)

Для исключения случайных флуктуаций, троттлинга ОС и неравномерности прогрева JIT C2 компилятора устанавливаются строгие правила:

1. **Изоляция тестовой среды:**
   - Тестирование проводится на выделенном локальном сервере (`test-environment/server/`).
   - Игровой мир: суперплоский (`generator-settings={"layers":[{"block":"minecraft:bedrock","height":1},{"block":"minecraft:stone","height":3},{"block":"minecraft:grass_block","height":1}],"biome":"minecraft:plains"}`).
   - Заморозка времени: `/gamerule doDaylightCycle false` и `/time set noon`.
   - Заморозка погоды: `/gamerule doWeatherCycle false` и `/weather clear`.
   - Отключение случайного спавна: `/gamerule doMobSpawning false`.
2. **Фиксация нагрузки:**
   - Пул мобов: ровно **250 взрослых коров** (`cow`), запертых в прозрачном кубе 16x16 на высоте Y=65.
   - Тег `{PersistenceRequired: 1b}` предотвращает непреднамеренный деспавн во время замера.
   - Позиция наблюдателя: игрок в режиме spectator зафиксирован на координатах `0, 70, 0` (дистанция до центра загона ровно 40 блоков, что строго превышает `sleepRadius = 16`).
3. **Обязательный прогрев JVM (JIT C2 Warmup):**
   - Перед стартом замера сервер обязан отработать не менее **1200 тиков (60 секунд)** под целевой нагрузкой.
   - Во время прогрева JIT-компилятор HotSpot компилирует и инлайнит методы `Mob.serverAiStep()`, `IdleChecker.shouldSleep()`, вызовы NeoForge EventBus.
4. **Объём выборки замера:**
   - Не менее **1200 тиков** (1 минута при 20 TPS) на каждый режим (`SAFE` и `OFF`).
5. **Метрическая строгость:**
   - Запрещено делать выводы только по среднему MSPT (`Mean`).
   - Обязателен сбор перцентилей задержки: **P50 (медиана)**, **P95 (хвост задержки)**, **P99 (микроспайки)**.
   - Фиксация пауз сборщика мусора (GC) через `/spark gc`.

---

## 2. Пошаговый сценарий проведения замера

### Фаза А. Замер Baseline (Режим OFF)

1. **Подготовка сервера:**
   ```bash
   python tools/validation/scripts/setup_test_environment.py off
   ```
2. **Запуск тестового сервера:**
   Запустите файл:
   `test-environment/server/start_benchmark_server.bat` (Windows) или `.sh` (Linux).
3. **Подключение к серверу:**
   Зайдите в игру на `localhost:25565`.
4. **Инициализация окружения (выполнить в консоли сервера или в чате):**
   ```text
   /gamerule doDaylightCycle false
   /gamerule doWeatherCycle false
   /gamerule doMobSpawning false
   /time set noon
   /weather clear
   /kill @e[type=!player]
   /fill -8 64 32 8 67 48 glass outline
   /fill -7 64 33 7 64 47 grass_block
   /gamemode spectator @p
   /teleport @p 0 70 0 0 0
   ```
5. **Спавн 250 коров:**
   Выполните спавн мобов в центр загона `(0, 65, 40)`:
   ```text
   /execute positioned 0 65 40 run summon cow ~ ~ ~ {Age:0,PersistenceRequired:1b}
   ```
   *(Повторить до достижения 250 коров. Проверка количества: `/execute if entity @e[type=cow]`)*.
6. **Прогрев JIT (60 секунд):**
   Подождите 60 секунд.
7. **Старт замера Spark (Baseline):**
   ```text
   /spark sampler --timeout 60
   /spark tickmonitoring --threshold 40
   ```
8. **Фиксация результатов:**
   - По завершении таймера Spark выдаст ссылку на онлайн-отчёт (сохранить URL).
   - Запросить сводку по тикам:
     ```text
     /spark health --memory
     ```
   - Запросить статистику KillTheFrizz:
     ```text
     /killthefrizz stats
     ```
   - Сохранить лог замера в `results/benchmark_off_log.txt`.

---

### Фаза Б. Замер Оптимизации (Режим SAFE)

1. **Переключение на режим SAFE:**
   ```text
   /killthefrizz preset SAFE
   /killthefrizz config reload
   ```
   *(Либо остановить сервер, выполнить `python tools/validation/scripts/setup_test_environment.py safe` и перезапустить)*.
2. **Проверка активации спящего режима:**
   Выполните:
   ```text
   /killthefrizz stats
   ```
   Убедитесь, что:
   - `Sleeping entities (last published)` находится в районе **240–250**.
   - `Skipped AI ticks` непрерывно увеличивается со скоростью ~5000 тиков/сек.
3. **Прогрев JIT (60 секунд):**
   Подождите 60 секунд для стабилизации оптимизированного пути исполнения.
4. **Старт замера Spark (SAFE Mode):**
   ```text
   /spark sampler --timeout 60
   /spark tickmonitoring --threshold 40
   ```
5. **Фиксация результатов:**
   - Скопировать сгенерированный Spark URL.
   - Снять показатели:
     ```text
     /spark health --memory
     /killthefrizz stats
     ```
   - Сохранить лог замера в `results/benchmark_safe_log.txt`.

---

## 3. Автоматическая обработка и сопоставление результатов

После сохранения логов замера выполните утилиту анализа:

```bash
python tools/validation/scripts/parse_benchmark_results.py results/benchmark_safe_log.txt results/benchmark_off_log.txt results/
```

Скрипт автоматически:
1. Рассчитает точные квантили задержки: Mean, P50, P95, P99, Min, Max, StDev.
2. Вычислит дельту снижения MSPT и процент экономии.
3. Сформирует отчёты:
   - `results/benchmark_comparison.json`
   - `results/benchmark_comparison.csv`
4. Сгенерирует готовую сравнительную таблицу для отчёта.

---

## 4. Контрольные вопросы валидности замера

Перед утверждением результатов ответьте на чек-лист:
- [ ] Одинаково ли количество коров (ровно 250) в обоих тестах?
- [ ] Совпадала ли дистанция игрока (ровно 40 блоков)?
- [ ] Был ли выдержан 60-секундный прогрев до включения замера?
- [ ] Не было ли во время замера сторонних фоновых задач ОС (обновления, индексация диска)?
- [ ] Отражает ли Spark flame graph снижение доли `serverAiStep` в общем времени тика?