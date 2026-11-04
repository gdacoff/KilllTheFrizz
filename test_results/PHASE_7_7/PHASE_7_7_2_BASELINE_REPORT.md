# Отчёт о базовой конфигурации фазы 7.7.2: среда выполнения

## 1. Резюме для руководства

- **Проект:** KillTheFrizz
- **Фаза:** Фаза 7.7.2 — валидация производительности среды выполнения и оптимизация узких мест
- **Среда:**
  - Minecraft 1.21.1
  - NeoForge 21.1.265
  - Java 21 (Eclipse Temurin 21.0.6+7-LTS)
  - Выделенный сервер (`test-environment/server/`, JVM: `-Xms4G -Xmx4G -XX:+UseG1GC`)
  - Spark 1.10.124 и KtfProfiler
- **Контекст Git:**
  - Активная ветка: `phase-7-core-sleepingentities`
  - Базовый коммит: `eb2408b feat(sleeping): complete Phase 7.1 hardening and audit`
  - Текущее рабочее состояние: оптимизация горячего пути бодрствования фазы 7.7.1 реализована и проверена
- **Статус проверки:**
  - Полный набор тестов: **114/114 пройдено (100%)**, 0 сбоев, 0 ошибок.
  - Компиляция Gradle: UP-TO-DATE, кэш конфигурации активен.

---

## 2. Действующая базовая конфигурация оптимизации

По состоянию на фазу 7.7.1 действующая конфигурация сервера в рамках `Preset.SAFE` следующая:

| Параметр | Значение конфигурации | Источник / происхождение |
|---|---|---|
| `common.enabled` | `true` | Preset SAFE |
| `common.preset` | `SAFE` | `killthefrizz-common.toml` |
| `sleepingEntities.enabled` | `true` | Preset SAFE |
| `sleepingEntities.sleepRadius` | `64` | Preset SAFE |
| `sleepingEntities.heartbeatTicks` | `20` | Preset SAFE |
| `sleepingEntities.recentDamageWakeTicks` | `100` | Default / Preset SAFE |
| `sleepingEntities.blockChangeWakeRadius` | `2` | Default / Preset SAFE |
| `sleepingEntities.maxIdleVelocity` | `0.03` blocks/tick | Default (`maxIdleVelocitySqr = 0.0009`) |
| `sleepingEntities.skipBabies` | `true` | Default / Preset SAFE |
| `sleepingEntities.skipTamedAndLeashed` | `true` | Default / Preset SAFE |
| `sleepingEntities.awakeEvalInterval` | `16` ticks (`& 15`) | Жёстко заданное ограничение горячего пути в `IdleChecker` |
| `sleepingEntities.staggerInterval` | `20` ticks (`% 20`) | Жёстко заданная повторная оценка спящих в `IdleChecker` |

---

## 3. Установленные базовые метрики (до оптимизации и после 7.7.1)

Ниже приведена проверенная эмпирическая база, установленная в ходе фаз 7.6.1 и 7.7.1:

| Сценарий бенчмарка | Версия / режим | MSPT медиана | MSPT P95 | Вызовы IdleCheck / 1300t | Средняя задержка IdleCheck | Средняя задержка pushEntities | Пропущено AI (%) |
|---|---|---|---|---|---|---|---|
| **250 коров в загоне** | **OFF** (фаза 7.6.1, режим A) | **4.20 ms** | **4.87 ms** | 0 (отключено) | - | - | 0.0% |
| **250 коров в загоне** | **SAFE до 7.7.1** (режим C) | **3.70 ms** | **4.65 ms** | 322,211 | 231 ns | 2,363 ns | 14.89% |
| **250 коров в загоне** | **SAFE + фаза 7.7.1** | **3.47 ms** | **4.20 ms** | 318,176 | 71 ns | 1,947 ns | 1.49% |
| **100 разрозненных коров** | **SAFE + фаза 7.7.1** | **1.60 ms** | **1.87 ms** | 130,467 | 61 ns | 219 ns | 0.0% (блуждание) |
| **100 жителей** | **SAFE + фаза 7.7.1** | **4.67 ms** | **5.67 ms** | 129,501 | 67 ns | 325 ns | 0.0% (в чёрном списке) |

---

## 4. Гарантии целостности и сохранности

1. **Рабочее дерево пользователя:** все неотслеживаемые артефакты (`PHASE_7_*.md`, `docs/`, `results/`, `tools/`) строго сохраняются.
2. **Данные бенчмарков:** существующие исторические JSON-файлы в `results/` сохраняются без изменений.
3. **Инварианты тестов:** все последующие оптимизации должны сохранять 100% прохождение всех 114 существующих модульных тестов.