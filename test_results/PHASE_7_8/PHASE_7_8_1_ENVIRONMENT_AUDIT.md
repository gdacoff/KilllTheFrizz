# Фаза 7.8.1 — Аудит окружения и репозитория

**Кампания:** Master Engineering Campaign (Фаза 7.8 → Фаза 7.9)  
**Проект:** KilllTheFrizz  
**Minecraft:** 1.21.1 | **NeoForge:** 21.1.265 | **Java:** 21 (Eclipse Temurin 21.0.6+7-LTS)  
**Целевая ветка:** `phase-7-core-sleepingentities` | **Базовый коммит:** `eb2408b`  
**Дата:** 2026-11-03  
**Статус:** PASSED (Подфаза 7.8.1 завершена)

---

## 1. Резюме для руководства

В соответствии с Фазой 7.8.1 Master Engineering Campaign был выполнен комплексный предбенчмарковый аудит Git-репозитория, локального рабочего дерева, сборочного инструментария, среды исполнения JVM и изолированного выделенного серверного стенда.

### Ключевые результаты аудита:
1. **Целостность репозитория и рабочего дерева:**
   - Активная ветка: `phase-7-core-sleepingentities`, являющаяся прямым потомком базового коммита `eb2408b feat(sleeping): complete Phase 7.1 hardening and audit`.
   - Изменённые production-файлы отражают упрочнение (hardening) фаз 7.7.1 и 7.7.2: `IdleChecker.java`, `MobMixin.java`, `LivingEntityMixin.java`, `SleepStateAccess.java`.
   - Неотслеживаемые пользовательские артефакты, включая `хуйня_для_педиков.md` и отчёты предыдущих фаз, подтверждены как присутствующие и сохранённые без изменений.
2. **Согласованность среды исполнения и инструментария:**
   - Виртуальная машина Java: OpenJDK 64-Bit Server VM (build 21.0.6+7-LTS, Eclipse Temurin).
   - Сборочный инструментарий: Gradle 8.8 с NeoForm Runtime `20240808.144430` / FML `4.0.45`.
   - Набор тестов: 124/124 автоматизированных модульных тестов компилируются без ошибок и проходят с 0 сбоев и ошибок.
3. **Изоляция среды исполнения:**
   - Выделенный бенчмарк-сервер работает в `test-environment/server`, исполняясь против локальных `build/classes/java/main` и `build/resources/main` через `-Dfml.modFolders`.
   - Пользовательские данные сохранений, production-миры и внешние конфигурации не изменяются и не затрагиваются.

---

## 2. Состояние Git-репозитория и рабочего дерева

### 2.1 Состояние ветки и HEAD
- **Ветка:** `phase-7-core-sleepingentities`
- **Коммит HEAD:** `eb2408b feat(sleeping): complete Phase 7.1 hardening and audit`
- **Удалённый origin:** Не запушен (строго соблюдается ограничение No-Push).

### 2.2 Инвентаризация рабочего дерева
- **Отслеживаемые изменения:**
  - `.gitignore`: Добавлены исключения для локальных тестовых и профилировочных выходных данных.
  - `PROGRESS.md`: Непрерывное отслеживание прогресса кампании.
  - `src/main/java/io/github/gdacoff/killthefrizz/KillTheFrizz.java`: Регистрация в жизненном цикле тик-обработчика `KtfProfiler`.
  - `src/main/java/io/github/gdacoff/killthefrizz/command/KtfCommands.java`: Диспетчеризация команды `/killthefrizz profile`.
  - `src/main/java/io/github/gdacoff/killthefrizz/mixin/MobMixin.java`: Интеграция раннего выхода фазы 7.7.1 + реализация интерфейса `SleepStateAccess`.
  - `src/main/java/io/github/gdacoff/killthefrizz/sleeping/IdleChecker.java`: Оптимизация бодрствующих сущностей через `isAwakeEvalTick`.
  - `src/main/resources/killthefrizz.mixins.json`: Регистрация `LivingEntityMixin`.
- **Неотслеживаемые ключевые файлы (сохранены):**
  - `хуйня_для_педиков.md`: Пользовательский файл сохранён без изменений.
  - `src/main/java/io/github/gdacoff/killthefrizz/mixin/LivingEntityMixin.java`: Отложенное отталкивание и инъекция профилирования.
  - `src/main/java/io/github/gdacoff/killthefrizz/sleeping/SleepStateAccess.java`: Мост состояния сна между миксинами.
  - `src/main/java/io/github/gdacoff/killthefrizz/profiling/`: Пакет облегчённого диагностического профилировщика.
  - `src/test/java/...`: Наборы модульных тестов (`SleepingPushOptimizationTest.java`, `AwakeHotPathOptimizationTest.java` и др.).
  - `results/`: Исторические наборы сырых телеметрических данных.

---

## 3. Спецификации платформы и JVM

```
Runtime Platform Architecture
├── OS: Windows 11 / Windows Server x86_64
├── JDK: Eclipse Temurin JDK 21.0.6+7-LTS (64-bit Server VM)
├── Java Language Level: Java 21 (bytecode version 65.0)
├── Minecraft: 1.21.1 (Mojang mappings)
├── Mod Loader: NeoForge 21.1.265 (FML 4.0.45)
├── GC Configuration: -XX:+UseG1GC (Heap: 4096M min / 4096M max)
└── Classpath Mode: DevLaunch BootstrapLauncher (-Dfml.modFolders=killthefrizz)
```

### Конфигурация запуска сервера (`start_benchmark_server.bat`):
```cmd
java @"build\moddev\serverRunClasspath.txt" @"build\moddev\serverRunVmArgs.txt" \
  -Dfml.modFolders=killthefrizz%%"build\classes\java\main";killthefrizz%%"build\resources\main" \
  net.neoforged.devlaunch.Main @"build\moddev\serverRunProgramArgs.txt"
```

---

## 4. Аудит матрицы конфигураций: OFF vs SAFE

Бенчмарк-тесты сравнивают два строго контролируемых режима конфигурации, задаваемых через `/killthefrizz preset`:

| Параметр конфигурации | Конфигурация A — OFF (ванильный базовый уровень) | Конфигурация B — SAFE (оптимизированный кандидат) |
|---|---|---|
| **Действующее значение sleepingEnabled** | `false` | `true` |
| **`IdleChecker.shouldSleep()`** | Немедленно возвращает `false` | Выполняет 4-стадийный конвейер с ранним выходом для бодрствующих |
| **`MobMixin.serverAiStep()`** | Ванильный AI выполняется каждый тик (`ci.cancel()` не вызывается) | AI пропускается для спящих мобов (`ci.cancel()` вызывается) |
| **`LivingEntityMixin.pushEntities()`** | Ванильное отталкивание выполняется каждый тик | Спящие мобы пропускают `pushEntities()` и взаимное отталкивание |
| **Обработка cramming** | Ванильная проверка каждый тик со случайным уроном 25% | Растянутая на 16 тиков проверка для спящих; ванильная для бодрствующих |
| **Радиус сна** | 48 блоков (игнорируется) | 64 блока |
| **Интервал heartbeat** | 40 тиков (игнорируется) | 20 тиков |
| **Чёрный список сущностей** | Villagers, Golems, Bosses, Vex | Villagers, Golems, Bosses, Vex |
| **Диагностический профилировщик (`KtfProfiler`)** | Активен (записывает тайминги ванильного исполнения) | Активен (записывает тайминги оптимизированного исполнения) |

---

## 5. Инвентаризация сохранённых артефактов

Следующие исторические отчёты и файлы телеметрии были каталогизированы и подтверждены как неповреждённые в корне репозитория и в `results/`:
- `PHASE_7_2_*.md` (Фаза 7.2: автоматизация, протокол и тесты)
- `PHASE_7_4_*.md` (Фаза 7.4: архитектура, дизайн бенчмарка, риски и карта миксинов)
- `PHASE_7_6_1_*.md` (Фаза 7.6.1: валидация среды исполнения и бенчмарк накладных расходов)
- `PHASE_7_7_1_*.md` (Фаза 7.7.1: реализация, бенчмарк и аудиты целостности)
- `PHASE_7_7_2_*.md` (Фаза 7.7.2: отчёты по базовому уровню, узким местам и бенчмарку)
- `PHASE_7_7_3_*.md` (Фаза 7.7.3: результаты геймплея, отчёт о регрессиях и план тестирования)
- `results/phase_7_7_1_benchmark_results.json`
- `results/phase_7_7_2_runtime_results.json`
- `results/phase_7_7_2_step5_results.json`
- `results/phase_7_7_3_gameplay_results.json`
- `results/phase_7_8_raw_data.json`

---

## 6. Контрольный список критериев приёмки

- [x] Среда исполнения полностью задокументирована.
- [x] Различия конфигураций OFF и SAFE идентифицированы и подтверждены.
- [x] Существующие бенчмарк-артефакты и неотслеживаемые файлы сохранены.
- [x] В ходе данной подфазы не выполнено ни одной модификации production-исходного кода.

**Статус подфазы 7.8.1: PASSED.** Переход к подфазе 7.8.2.