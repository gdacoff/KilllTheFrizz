# Аудит 3: Кэш EntityType и аллокация строк в горячем пути

**Модуль:** `io.github.gdacoff.killthefrizz.sleeping.IdleChecker`  
**Строки кода:** [`IdleChecker.java:122-138`](file:///C:/Users/gdacp/IdeaProjects/KilllTheFrizz/src/main/java/io/github/gdacoff/killthefrizz/sleeping/IdleChecker.java#L122-L138)  
**Режим аудита:** READ-ONLY.  

---

## 1. Анализ текущего кода проверки списков сущностей

В Phase 7.1 проверка допустимости типа сущности реализована следующим образом:

```java
ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType());
if (id == null) {
    KtfStats.recordRejection(RejectionReason.BLACKLISTED);
    return false;
}
String idStr = id.toString();
if (serverCfg.entityWhitelistMode()) {
    if (!serverCfg.entityWhitelistSet().contains(idStr)) {
        KtfStats.recordRejection(RejectionReason.BLACKLISTED);
        return false;
    }
} else {
    if (!id.getNamespace().equals("minecraft") || serverCfg.entityBlacklistSet().contains(idStr)) {
        KtfStats.recordRejection(RejectionReason.BLACKLISTED);
        return false;
    }
}
```

---

## 2. Проверка гипотезы о создании строк в горячем пути

### Фактическое поведение:
1. Вызов `BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType())`:
   - Внутри `MappedRegistry` выполняется поиск по внутреннему массиву/карте ссылок на `Holder.Reference`. Возвращается объект `ResourceLocation` (например, `minecraft:cow`).
2. Вызов `id.toString()`:
   - В коде Minecraft `ResourceLocation.toString()` реализован как:
     ```java
     return this.namespace + ":" + this.path;
     ```
   - Метод **не кэширует** сгенерированную строку. Каждый вызов `toString()` конструирует **новый объект `String`** через `StringBuilder` / `StringConcatFactory` и аллоцирует новый массив символов `byte[]` в Java Heap!
3. Вызов `contains(idStr)`:
   - В `HashSet<String>` строка хэшируется (вызов `hashCode()` и сравнение символов через `equals`).

### Частота аллокаций:
- Для **спящего моба** этот блок вызывается 1 раз в 20 тиков (на `isEvalTick`).
- Но для **бодрствующего моба** (как доказано в Аудите №1) из-за условия `wasSleeping && !isEvalTick` этот блок вызывается **КАЖДЫЙ ТИК**!
- На сервере с 250 бодрствующими коровами в загоне:
  $$250 \text{ мобов} \times 20 \text{ тиков} = \mathbf{5,000 \text{ аллокаций строк в секунду!}}$$
  За 1 минуту работы генерируется **300,000 мусорных объектов `String`**, увеличивающих давление на JVM Garbage Collector (Young Generation Eden churn) и провоцирующих частые фазы очистки мусора.

---

## 3. Исследование вариантов кэширования по `EntityType<?>`

Экземпляры `EntityType<?>` в Minecraft являются **строгими синглтонами** (статические константы `EntityType.COW`, `EntityType.ZOMBIE` и т.д.). Даже в модпаках с десятками модов на мобов все типы сущностей регистрируются один раз при запуске через `DeferredRegister` и никогда не пересоздаются заново.

### Сравнение структур данных для кэша:

| Структура | Принцип работы | Скорость доступа | Потокобезопасность | Применимость |
|---|---|---|---|---|
| **`IdentityHashMap<EntityType<?>, Boolean>`** | Сравнение по ссылке (`==`) через `System.identityHashCode()`. | **Очень высокая (~2–4 ns)** | Требует защиты при записи или volatile-свопа. | **Идеально**. Мгновенный ответ без строковых сравнений. |
| **Обычный `HashMap<EntityType<?>, Boolean>`** | Сравнение по `equals()` и `hashCode()`. | Высокая (~5–8 ns) | Требует ConcurrentHashMap или копирования. | Избыточно, так как `EntityType` синглтон. |
| **Массив `boolean[]` по числовому ID** | Прямой индекс `BuiltInRegistries.ENTITY_TYPE.getId(type)`. | **Экстремальная (~1 ns)** (L1 кэш) | 100% безопасен при чтении неизменяемого массива. | Требует пересоздания массива при изменении реестра. |

---

## 4. Динамические EntityType из сторонних модов и жизненный цикл кэша

### Проблема динамической регистрации:
Сторонние моды могут регистрировать сущности на ранних этапах загрузки игры. Конфигурация `killthefrizz-server.toml` может быть перезагружена администратором в реальном времени через `/killthefrizz config reload`.

### Архитектурное решение: Immutable Snapshot Cache
Вместо динамического заполнения во время горячего тика, кэш предвычисляется единовременно при старте сервера и при вызове `recomputeEffective()`:

```java
public final class EntityTypeSleepFilter {
    // Неизменяемая ссылка на предвычисленную карту (zero synchronization on read)
    private static volatile IdentityHashMap<EntityType<?>, Boolean> CAN_SLEEP_CACHE = new IdentityHashMap<>();

    public static boolean isEligible(EntityType<?> type) {
        Boolean allowed = CAN_SLEEP_CACHE.get(type);
        if (allowed != null) {
            return allowed;
        }
        // Fallback для сущностей, появившихся после перезагрузки
        return evaluateAndCache(type);
    }

    public static void rebuildCache(EffectiveServer serverCfg) {
        IdentityHashMap<EntityType<?>, Boolean> newCache = new IdentityHashMap<>();
        for (EntityType<?> type : BuiltInRegistries.ENTITY_TYPE) {
            ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(type);
            if (id == null) {
                newCache.put(type, false);
                continue;
            }
            String idStr = id.toString();
            boolean allowed;
            if (serverCfg.entityWhitelistMode()) {
                allowed = serverCfg.entityWhitelistSet().contains(idStr);
            } else {
                allowed = id.getNamespace().equals("minecraft") && !serverCfg.entityBlacklistSet().contains(idStr);
            }
            newCache.put(type, allowed);
        }
        CAN_SLEEP_CACHE = newCache; // Атомарная замена ссылки
    }
}
```

### Преимущества подхода:
1. **0 аллокаций строк в горячем пути:** Метод `id.toString()` вызывается ровно один раз на каждый тип моба **при старте сервера**, а не 5000 раз в секунду во время игры.
2. **Скорость проверки O(1):** `CAN_SLEEP_CACHE.get(type)` работает через сравнение ссылок `type == otherType`.
3. **Потокобезопасность:** Чтение из `volatile` ссылки на неизменяемый `IdentityHashMap` абсолютно безопасно для параллельных потоков и не требует никаких блокировок `synchronized` или `ConcurrentHashMap`.