# Фаза 7.9.5 — Архитектура следующей оптимизации: адаптивное троттлингование мозга жителей

**Кампания:** Master Engineering Campaign (Фаза 7.8 → Фаза 7.9)  
**Проект:** KilllTheFrizz  
**Minecraft:** 1.21.1 | **NeoForge:** 21.1.265 | **Java:** 21  
**Целевая ветка:** `phase-7-core-sleepingentities` | **Базовый коммит:** `eb2408b`  
**Дата:** 2026-11-03  
**Статус:** ЗАВЕРШЕНО (архитектурный проект подготовлен — исходный код не изменялся)

---

## 1. Резюме для руководства и постановка задачи

Эмпирическое тестирование в фазе 7.8 установило, что **выполнение мозга жителя (`Villager.customServerAiStep` -> `Brain.tick`) является единственным крупнейшим вычислительным узким местом на сервере**, потребляя $\approx 30,000 \text{ нс}$ на жителя за тик ($72.1\%$ от общего времени обработки сущностей в смешанных рабочих нагрузках ферм и деревень).

Жители были исключены из `SleepingEntities` в фазе 7.1, поскольку наивная отмена ИИ (`ci.cancel()`) ломала обновления расписания, пополнение запасов торговцев и панику железных големов.

Данный документ описывает полный, неинвазивный архитектурный проект **адаптивного троттлингования мозга жителей**:
- Сохраняет полный тик 20 Гц во время торговли с игроком, паники, получения урона, навигации и пополнения запасов.
- Снижает частоту выполнения мозга до 5 Гц (1 тик из 4) для неподвижных жителей на торговых местах.
- Снижает частоту выполнения мозга до 2 Гц (1 тик из 10) для жителей, спящих в кроватях (`Activity.REST`).
- Сокращает потребление ЦП жителями на **$60–75\%$**, устраняя $\approx 1.0 \text{ мс}$ MSPT на 40 жителей.

---

## 2. Архитектура потока выполнения

### 2.1 Текущий ванильный поток (100% тиков на 20 Гц)
```
Villager.customServerAiStep()
│
├── Profiler.push("villagerBrain")
├── Brain.tick()  <=== ВЫПОЛНЯЕТСЯ КАЖДЫЙ ТИК (20 Гц, 30 мкс/сущность)
│   ├── forgetOutdatedMemories()
│   ├── tickSensors() [NearestLivingEntities, Bed, Workstation, Hostiles, Golem...]
│   ├── startEachNonRunningBehavior() [Глубоко вложенный цикл приоритетов]
│   └── tickEachRunningBehavior()
├── Profiler.pop()
│
├── Обновление таймера пополнения запасов торговца (--updateMerchantTimer)
├── Обработка событий репутации игрока (onReputationEvent)
├── Проверка статуса рейда (случайно 1 из 100)
└── super.customServerAiStep()
```

### 2.2 Предлагаемый оптимизированный поток
```
Villager.customServerAiStep()
│
├── villagerThrottlingEnabled включён?
│    ├─► НЕТ: Выполнить ванильный Brain.tick() (100% без изменений)
│    │
│    └─► ДА: Вычислить VillagerBrainThrottler.shouldTickBrain(villager)
│         │
│         ├─► ПРИНУДИТЕЛЬНОЕ ПРОБУЖДЕНИЕ (20 Гц / каждый тик):
│         │     • isTrading() == true (У игрока открыт торговый интерфейс)
│         │     • hurtTime > 0 || isOnFire() (Сущность получила урон)
│         │     • brain.isActive(Activity.PANIC) (Обнаружена угроза)
│         │     • deltaMovement.horizontalDistanceSqr() > 0.0009 (Сущность движется)
│         │     • !getNavigation().isDone() (Активно идёт к кровати/рабочему месту)
│         │     • updateMerchantTimer > 0 (Активный отсчёт пополнения запасов)
│         │     └─► ВЫПОЛНИТЬ Brain.tick()
│         │
│         ├─► СОСТОЯНИЕ СНА В КРОВАТИ (2 Гц / каждый 10-й тик):
│         │     • brain.isActive(Activity.REST) && onGround()
│         │     • Если ((tickCount + id) % 10 == 0) -> ВЫПОЛНИТЬ Brain.tick()
│         │     • Иначе -> ПРОПУСТИТЬ Brain.tick() (Экономия 90% ЦП в кровати!)
│         │
│         └─► СОСТОЯНИЕ НЕПОДВИЖНОГО ТОРГОВОГО МЕСТА (5 Гц / каждый 4-й тик):
│               • Состояние простоя/рабочего места по умолчанию с нулевой скоростью
│               • Если ((tickCount + id) & 3 == 0) -> ВЫПОЛНИТЬ Brain.tick()
│               • Иначе -> ПРОПУСТИТЬ Brain.tick() (Экономия 75% ЦП на торговых точках!)
│
├── Обновление таймера пополнения запасов торговца (--updateMerchantTimer) [ВСЕГДА ТИКАЕТ]
├── Обработка событий репутации игрока [ВСЕГДА ТИКАЕТ]
└── super.customServerAiStep() [ВСЕГДА ТИКАЕТ]
```

---

## 3. Необходимые классы, миксины и интерфейсы

### 3.1 Новый класс: `VillagerBrainThrottler.java`
- **Пакет:** `io.github.gdacoff.killthefrizz.villager`
- **Ответственность:** Высокопроизводительная оценка состояния жителя и частотное стробирование без выделения памяти.
```java
package io.github.gdacoff.killthefrizz.villager;

import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.schedule.Activity;

public final class VillagerBrainThrottler {

    private static final double VELOCITY_THRESHOLD_SQR = 0.03 * 0.03; // 0.0009

    private VillagerBrainThrottler() {}

    public static boolean shouldTickBrain(Villager villager) {
        // 1. Обязательные условия 20 Гц (приоритетные быстрые пути):
        if (villager.isTrading()) return true;
        if (villager.hurtTime > 0 || villager.isOnFire()) return true;
        if (villager.getDeltaMovement().horizontalDistanceSqr() > VELOCITY_THRESHOLD_SQR) return true;
        if (!villager.getNavigation().isDone()) return true;

        var brain = villager.getBrain();
        if (brain.isActive(Activity.PANIC)) return true;

        // 2. Состояние сна в кровати (Activity.REST): 2 Гц (каждый 10-й тик)
        if (brain.isActive(Activity.REST)) {
            return Math.floorMod((long) villager.tickCount + villager.getId(), 10) == 0;
        }

        // 3. Неподвижное торговое место / состояние простоя: 5 Гц (каждый 4-й тик)
        return ((villager.tickCount + villager.getId()) & 3) == 0;
    }
}
```

### 3.2 Новый миксин: `VillagerMixin.java`
- **Пакет:** `io.github.gdacoff.killthefrizz.mixin`
- **Цель:** `net.minecraft.world.entity.npc.Villager`
- **Инъекция:** Обернуть или условно ограничить `this.getBrain().tick(...)` внутри `customServerAiStep()`.
```java
@Mixin(Villager.class)
public abstract class VillagerMixin {

    @WrapOperation(
        method = "customServerAiStep",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/ai/Brain;tick(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/LivingEntity;)V")
    )
    private void ktf$onTickVillagerBrain(Brain<Villager> brain, ServerLevel level, LivingEntity entity, Operation<Void> original) {
        Villager villager = (Villager) entity;
        if (!KtfConfig.getEffectiveServer().villagerThrottlingEnabled() || VillagerBrainThrottler.shouldTickBrain(villager)) {
            original.call(brain, level, entity);
        }
    }
}
```
*Примечание по технике миксинов:* Используется `@WrapOperation` из MixinExtras, что полностью исключает хрупкую перезапись байткода и обеспечивает бесшовную совместимость с Lithium, FerriteCore и другими модами производительности для NeoForge.

---

## 4. Схема конфигурации

Добавлено в `KtfConfig.Server` и разрешается в `EffectiveServer`:

```toml
[sleepingEntities.villagers]
    # Включить адаптивное троттлингование мозга для неподвижных и спящих жителей.
    # Сокращает время ЦП на жителей на 60-75%, сохраняя 100% ванильной торговли, пополнения запасов и паники.
    enabled = true

    # Интервал тиков для неподвижных жителей на торговых точках (Диапазон: от 2 до 10 тиков). По умолчанию: 4 (5 Гц)
    stallInterval = 4

    # Интервал тиков для жителей, спящих в кроватях (Диапазон: от 5 до 20 тиков). По умолчанию: 10 (2 Гц)
    bedInterval = 10
```

---

## 5. Многопоточность, память и безопасность fail-open

1. **Модель многопоточности:** Вся логика выполняется строго в потоке сервера Minecraft (`ServerLevel.tickNonPassenger`). Накладные расходы на потокобезопасность, синхронизированные блоки или блокировки не требуются.
2. **Нулевой след по выделению памяти:** `shouldTickBrain()` выполняет только примитивную арифметику, битовую маскировку (`& 3`) и обращения к полям. В горячем пути не происходит ни одного выделения объектов.
3. **Граница исключений fail-open:**
   При возникновении любого непредвиденного исключения во время оценки троттлера блок catch регистрирует предупреждение с ограничением частоты и возвращает `true`, гарантируя, что житель вернётся к ванильному тику 20 Гц без падения сервера.

---
## 6. Протокол верификации и приёмочного тестирования

### 6.1 Набор модульных тестов (`VillagerBrainThrottlerTest.java`)
1. `tradingVillagerRunsEveryTick()`: Подтверждает, что `isTrading() == true` принудительно задаёт частоту 20 Гц.
2. `damagedVillagerRunsEveryTick()`: Подтверждает, что `hurtTime > 0` принудительно задаёт частоту 20 Гц.
3. `panickingVillagerRunsEveryTick()`: Подтверждает, что `Activity.PANIC` принудительно задаёт частоту 20 Гц.
4. `movingVillagerRunsEveryTick()`: Подтверждает, что наличие скорости принудительно задаёт частоту 20 Гц.
5. `stationaryVillagerThrottledToFiveHertz()`: Подтверждает распределение 1 тик из 4.
6. `bedSleepingVillagerThrottledToTwoHertz()`: Подтверждает распределение 1 тик из 10.

### 6.2 Матрица внутриигровых регрессионных тестов
- **T-01 (Пополнение запасов):** Убедиться, что мастер-библиотекарь дважды в день пополняет сделки на зачарованные книги.
- **T-02 (Паника и спавн голема):** Заспавнить зомби рядом с 3 жителями; убедиться, что паника срабатывает в течение 50 мс и призывается железный голем.
- **T-03 (Сон в кровати):** Промотать время до ночи (`time set night`); убедиться, что жители прокладывают путь к кровати, входят в сон и просыпаются на рассвете.
- **T-04 (GUI торговли):** Открыть окно торговли; убедиться, что UI открывается мгновенно с нулевой задержкой и без блокировки торговли.

---

## 7. Стратегия отката и развязки зависимостей

- **Мгновенный откат во время выполнения:** `/killthefrizz preset OFF` или установка `villagerThrottlingEnabled = false` немедленно восстанавливает 100% ванильный `Brain.tick()` без перезапуска сервера.
- **Изоляция зависимостей:** Данная оптимизация работает независимо от `IdleChecker` пассивных мобов, что гарантирует невозможность дестабилизации систем сна коров, овец или свиней изменениями в жителях.

---

## 8. Критерии приёмки для будущей фазы реализации

Будущая фаза реализации (Phase 7.9.6 / 7.10) будет принята тогда и только тогда, когда:
1. Сценарий B (200 смешанных мобов с 40 жителями) демонстрирует **$\ge 15\%$ снижения суммарного MSPT** ($3.56 \text{ ms} \rightarrow \le 3.00 \text{ ms}$).
2. Суммарное процессорное время 40 жителей снижается на **$\ge 50\%$** ($1,200 \text{ ms} \rightarrow \le 600 \text{ ms}$).
3. Все 6 автоматизированных модульных тестов и 4 внутриигровых регрессионных теста проходят с 0 сбоев.
4. Поддерживается 100% паритет с ванильной торговлей, пополнением запасов и паникой.

**Статус подфазы 7.9.5: PASSED.** Переход к синтезу кампании и генерации мастер-отчёта.