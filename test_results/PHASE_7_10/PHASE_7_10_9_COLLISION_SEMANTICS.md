# Фаза 7.10.9.2 — Аудит семантики коллизий и скучивания

**Проект:** KilllTheFrizz  
**Minecraft:** 1.21.1 | **NeoForge:** 21.1.265 | **Java:** 21  
**Источник дизассемблирования:** `neoforge-21.1.265.jar` -> `net.minecraft.world.entity.LivingEntity`  

---

## 1. Дизассемблирование ванильного `LivingEntity.pushEntities()`

Декомпиляция байткода Mojang 1.21.1 (`javap -c -p`) раскрывает точную структуру `pushEntities()`:

```text
protected void pushEntities():
  1. If level.isClientSide:
     - Query pushable Players within bounding box
     - Push player
     - Return
  2. If !level.isClientSide (SERVER THREAD):
     - List<Entity> list = level.getEntities(this, getBoundingBox(), EntitySelector.pushableBy(this))
     - If list.isEmpty(): return
     - int cramming = level.getGameRules().getInt(RULE_MAX_ENTITY_CRAMMING)
     - If cramming > 0 and list.size() > cramming - 1:
       --> if (this.random.nextInt(4) == 0):       <-- CRITICAL DISCOVERY: P = 0.25 in vanilla!
             int nonPassengers = 0
             for (Entity e : list):
               if (!e.isPassenger()) nonPassengers++
             if (nonPassengers > cramming - 1):
               this.hurt(damageSources().cramming(), 6.0F)
     - for (Entity e : list):
         this.doPush(e)                             <-- Calls e.push(this)
     - Return
```

### Пропускаемые операции при троттлинге:
Когда `LivingEntityMixin.ktf$onPushEntitiesHead` отменяет `pushEntities()` на нечётном тике фазы:
1. Пространственный запрос `Level.getEntities(...)` пропускается (устраняется обход секций broadphase и аллокация `new ArrayList`).
2. Проверка скучивания пропускается на данном конкретном тике.
3. Исходящие вызовы `doPush(e)` пропускаются на данном конкретном тике.

---

## 2. Анализ поведения скучивания сущностей

### 2.1 Ванильный механизм скучивания
Критическое наблюдение, выявленное при инспекции байткода: **ванильный Minecraft НЕ выполняет проверки скучивания детерминированно каждый тик**. 
Строка 106 в байткоде:
```text
101: aload_0
102: getfield      #802    // Field random:Lnet/minecraft/util/RandomSource;
105: iconst_4
106: invokeinterface #1209 // Method RandomSource.nextInt:(I)I
111: ifne          182     // Branch to skip cramming if random != 0
```
В ванилле урон от скучивания имеет **вероятность $25\%$ на тик сущности ($P = 0.25$)**.
Ожидаемое время между оценками скучивания для данной сущности в ванилле составляет:
$$\mathbb{E}[\text{Ticks}] = \frac{1}{P} = 4\text{ ticks} \quad (200\text{ ms})$$

### 2.2 Скучивание под `CollisionThrottler` (пресет SAFE)
Под `CollisionThrottler` сущность выполняет `pushEntities()` на чередующихся тиках ($50\%$ тиков).
При выполнении она сохраняет ванильную проверку $P = 0.25$:
$$\mathbb{E}[\text{Ticks}_{\text{SAFE}}] = \frac{2}{P} = 8\text{ ticks} \quad (400\text{ ms})$$

### 2.3 Агрегатная динамика удушения стада
В плотном загоне, содержащем $N$ сущностей, где $N > 24$:
- На каждом тике $t$ приблизительно $N / 2$ сущностей выполняют оценку коллизий.
- Ожидаемое число сущностей, выполняющих оценку урона от скучивания за серверный тик:
  $$\mathbb{E}[\text{Cramming Evaluations/Tick}] = \frac{N}{2} \times 0.25 = \frac{N}{8}$$
- Для начальной популяции $N = 35$ коров:
  $$\mathbb{E}[\text{Evaluations/Tick}] = \frac{35}{8} \approx 4.375\text{ checks/tick} \quad (87.5\text{ checks/second})$$
- Каждая проверка наносит $6.0\text{ HP}$ урона корове ($10.0\text{ HP}$ максимального здоровья), убивая корову за 2 попадания.
- **Эмпирическая валидация:** В тесте 3 фазы 7.10.6 35 коров в блоке 1x1 стабилизировались ровно до **24 коров в течение 3.0 секунд**, что доказывает, что `maxEntityCramming = 24` остаётся полностью принудительно применяемым без неконтролируемого роста стада.

---

## 3. Механика взаимного разделения мобов

### 3.1 Ванильная физика `Entity.push(Entity other)`
В Minecraft импульсы разделения вычисляются симметрично:
```java
double d0 = other.getX() - this.getX();
double d1 = other.getZ() - this.getZ();
double d2 = Mth.absMax(d0, d1);
if (d2 >= 0.01F) { // Singularity guard
    d2 = Math.sqrt(d2);
    d0 /= d2; d1 /= d2;
    double d3 = 1.0 / d2;
    if (d3 > 1.0) d3 = 1.0;
    d0 *= d3 * 0.05F * (1.0F - this.pushthrough);
    d1 *= d3 * 0.05F * (1.0F - this.pushthrough);
    if (!this.isPassenger() && !this.isVehicle()) this.push(-d0, 0.0, -d1);
    if (!other.isPassenger() && !other.isVehicle()) other.push(d0, 0.0, d1);
}
```
**Ключевой инвариант:** Одиночный вызов `Entity.push` применяет импульс разделения к **обеим** сущностям одновременно ($\vec{v}_{\text{this}} \gets \vec{v}_{\text{this}} - \vec{\Delta}$ и $\vec{v}_{\text{other}} \gets \vec{v}_{\text{other}} + \vec{\Delta}$).

### 3.2 Чередующаяся парная динамика
Рассмотрим две сущности $A$ и $B$, пересекающиеся в загоне ($A$ имеет чётный ID, $B$ — нечётный):
- **Тик 0 (чётный):** Сущность $A$ выполняет `pushEntities()`, запрашивает broadphase, находит $B$ и выполняет `push(B)`. Это сообщает $-\vec{\Delta}$ сущности $A$ и $+\vec{\Delta}$ сущности $B$. Сущность $B$ пропускает `pushEntities()`. Обе сущности разделяются.
- **Тик 1 (нечётный):** Сущность $B$ выполняет `pushEntities()`, запрашивает broadphase, находит $A$ и выполняет `push(A)`. Это сообщает $-\vec{\Delta}$ сущности $B$ и $+\vec{\Delta}$ сущности $A$. Сущность $A$ пропускает `pushEntities()`. Обе сущности разделяются.
- **Результат:** Пара испытывает импульсы разделения на **$100\%$ тиков**, при этом накладные расходы на пространственные запросы сокращаются на **$50\%$**.
- **Эмпирическое доказательство:** В тесте 2 фазы 7.10.6 перекрывающиеся коровы разошлись до $1.668\text{ m}$ за 30 тиков (порог $> 0.4\text{ m}$).

---

## 4. Границы быстрого пути и защита совместимости

Троттлер гарантирует ванильное поведение 20 Гц во всех критичных для геймплея взаимодействиях:

| Предикат | Назначение | Задержка / Инвариант |
|:---|:---|:---:|
| `hasNearbyAlivePlayer(x, y, z, 6.0)` | Толкание игроком мобов | 0 тиков задержки (немедленно 20 Гц) |
| `hurtTime > 0` | Боевой отброс / попадания стрел | 0 тиков задержки |
| `isOnFire()` | Урон от огня и панический бег | 0 тиков задержки |
| `horizontalDistanceSqr() > 0.04` | Ходьба, падение, взрыв | 0 тиков задержки ($> 0.20\text{ blocks/tick}$) |
| `isPassenger() \|\| isVehicle()` | Лодки, вагонетки, сёдла | 0 тиков задержки |
| `mob.isLeashed()` | Натяжение поводка и пасфайндинг | 0 тиков задержки |
| `catch (Throwable t)` | Fail-open резерв | По умолчанию ванильные 20 Гц при ошибке |