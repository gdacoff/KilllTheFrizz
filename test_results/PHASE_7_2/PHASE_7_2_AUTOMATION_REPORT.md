# Фаза 7.2 — Инфраструктура автоматизации и отчёт о готовности

**Проект:** KilllTheFrizz  
**Ветка:** `phase-7-core-sleepingentities`  
**Базовый коммит:** `eb2408b`  
**Дата отчёта:** 2026-11-02  
**Статус готовности инфраструктуры:** ГОТОВА К ИГРОВЫМ ТЕСТАМ  

---

## 1. Сводка выполненных работ

В рамках Phase 7.2 создана автономная инфраструктура для натурного тестирования мода в среде Minecraft 1.21.1 / NeoForge 21.1.265.

Пользовательские миры и настройки в директории `run/` **полностью защищены и изолированы**.

### Созданные инструменты и каталоги:
- `tools/validation/scripts/verify_environment.py` — аудит системного окружения (Java 21, Git branch, сборка JAR, порты).
- `tools/validation/scripts/setup_test_environment.py` — генератор изолированного сервера в `test-environment/server/`, загрузчик Spark NeoForge 1.21.1, менеджер пресетов.
- `tools/validation/scripts/parse_benchmark_results.py` — математический анализатор распределения задержек MSPT (Mean, P50, P95, P99, StDev) с экспортом в CSV и JSON.
- `tools/validation/scripts/run_automated_checks.py` — мастер-скрипт сквозной автоматической валидации.
- `tools/validation/configs/` — эталонные серверные конфигурации `killthefrizz-server-safe.toml`, `killthefrizz-server-off.toml` и `server-benchmark.properties`.
- `tools/validation/scenarios/` — пошаговые наборы команд для воспроизведения 4 тестовых стендов.
- `test-environment/server/` — изолированный экземпляр сервера с установленным профилировщиком Spark (`spark-1.10.124-neoforge.jar`).

---

## 2. Результаты автоматизированных проверок

Сквозной прогон `run_automated_checks.py` показал следующие результаты:

| Проверка | Команда | Статус | Детали / Результат |
|---|---|---|---|
| **Аудит окружения** | `verify_environment.py` | **PASS** | Java 21 LTS (`21.0.12`), ветка `phase-7-core-sleepingentities`, порт 25565 свободен. |
| **Регрессионные тесты** | `./gradlew test --rerun-tasks` | **PASS** | **87 из 87 тестов пройдено** (0 сбоев, 0 пропущено). Время: 10.46s. |
| **Сборка бинарного мода** | `./gradlew build` | **PASS** | Создан `build/libs/killthefrizz-1.0.0.jar` (75 732 байта). Время: 2.28s. |
| **Форматирование Git diff** | `git diff --check` | **PASS** | 0 замечаний, отсутствие trailing whitespace и артефактов BOM. |
| **Изоляция тестового сервера** | `setup_test_environment.py safe` | **PASS** | Сгенерированы `server.properties`, `eula.txt`, загружен Spark 1.10.124. |

---

## 3. Четкое разграничение статусов

### Реальные результаты (Подтверждено на 100%):
1. Все 87 JUnit-тестов проходят успешно.
2. NeoForge DevLaunch скрипты запуска сервера корректно сгенерированы.
3. Spark 1.10.124 загружен с Modrinth и установлен в `test-environment/server/mods/`.
4. Скрипт парсинга точно рассчитывает перцентили (P50, P95, P99) и экспортирует CSV/JSON.

### Ручные проверки (Требуют участия разработчика):
1. Запуск выделенного сервера через `test-environment/server/start_benchmark_server.bat`.
2. Заход в мир `localhost:25565` и запуск сценариев бенчмарка (250 коров).
3. Снятие Spark-профилей через `/spark sampler --timeout 60` для режимов SAFE и OFF.
4. Проверка деспавна зомби на расстоянии 45 блоков (сценарий 1).

### Непроверенные сценарии (До получения живых логов):
1. **Фактическое снижение MSPT:** пока сервер не запущен с 250 коровами, точные цифры миллисекунд (например, 11 ms vs 16 ms) остаются предположением.
2. **Деспавн в мультиплеере:** натурное поведение таймера `noActionTime` на реальном сервере NeoForge.
3. **Совместимость со сторонними модами на мобов:** поведение существ из Alex's Mobs / Ice and Fire в реальной сессии.

---

## 4. Инструкция: Как запустить валидацию и бенчмарк

### Шаг 1. Переключение в режим Baseline (OFF)
В терминале:
```bash
python tools/validation/scripts/setup_test_environment.py off
```

### Шаг 2. Запуск сервера
Запустите:
```cmd
test-environment\server\start_benchmark_server.bat
```
*(Сервер запустится на чистом суперплоском мире без монстров и смены времени).*

### Шаг 3. Проведение замера в игре
1. Подключитесь в Minecraft 1.21.1 к `localhost:25565`.
2. Заспавните 250 коров в загон по инструкции из [`PHASE_7_2_BENCHMARK_PROTOCOL.md`](file:///C:/Users/gdacp/IdeaProjects/KilllTheFrizz/PHASE_7_2_BENCHMARK_PROTOCOL.md).
3. Подождите 60 секунд (JIT warmup).
4. Запустите замер:
   ```text
   /spark sampler --timeout 60
   /spark tickmonitoring --threshold 40
   ```
5. По окончании сохраните ссылку на Spark-отчёт и выполните:
   ```text
   /spark health --memory
   /killthefrizz stats
   ```
6. Скопируйте вывод консоли в файл `results/benchmark_off_log.txt`.

### Шаг 4. Повторение замера в режиме SAFE
1. Переключите режим мода:
   ```text
   /killthefrizz preset SAFE
   /killthefrizz config reload
   ```
2. Убедитесь через `/killthefrizz stats`, что коровы уснули (`Sleeping entities ~ 250`).
3. Подождите 60 секунд.
4. Запустите `/spark sampler --timeout 60`.
5. Скопируйте вывод консоли в файл `results/benchmark_safe_log.txt`.

### Шаг 5. Автоматический расчет метрик
В терминале выполните:
```bash
python tools/validation/scripts/parse_benchmark_results.py results/benchmark_safe_log.txt results/benchmark_off_log.txt results/
```
Скрипт выведет итоговую Markdown-таблицу сравнения и сохранит `results/benchmark_comparison.csv`.

---

## 5. Какие данные прислать агенту для анализа

Для интерпретации результатов и принятия решения по следующей фазе отправьте:
1. Две ссылки на отчёты Spark Profiler (для режима SAFE и для режима OFF).
2. Вывод команды `/killthefrizz stats` в режиме SAFE (чтобы увидеть счётчики `Skipped AI ticks` и распределение `Top idle rejection reasons`).
3. Результаты проверки деспавна мобов на дистанции 45 блоков (деспавнятся ли зомби по истечении ~1–2 минут).
4. Сгенерированный файл `results/benchmark_comparison.json` (или вывод утилиты парсинга).