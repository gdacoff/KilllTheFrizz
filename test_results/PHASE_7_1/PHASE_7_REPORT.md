# Отчёт по фазе 7: Безопасное исправление ядра и оптимизация SleepingEntities

> **Дата:** 01.11.2026  
> **Платформа:** Minecraft 1.21.1, NeoForge 21.1.265, Java 21  
> **Целевая ветка:** `phase-7-core-sleepingentities`  
> **Статус сборки:** BUILD SUCCESSFUL, 81/81 unit-тестов успешно пройдены  

---

## 1. Что проверено (файлы, классы, методы и цепочки вызовов)

1. **`SlowdownMonitor.java` и жизненный цикл серверного тика:**
   - Исследован цикл `net.minecraft.server.MinecraftServer.runServer()` и точки срабатывания `ServerTickEvent.Pre` и `ServerTickEvent.Post`.
   - Проверено получение временных меток и измерение MSPT: старый код брал разницу между двумя `Post`-событиями, захватывая 50 мс времени сна между тиками на 20 TPS.
2. **`Mob.java` и `LivingEntity.java` (декомпилированный код `neoform 1.21.1`):**
   - Исследован метод `Mob.serverAiStep()`: подтверждено, что инкремент `this.noActionTime++;` является первой инструкцией метода.
   - Исследован метод `LivingEntity.aiStep()`: подтверждено, что `serverAiStep()` вызывается внутри `aiStep()`, при этом гравитация, движение и коллизии выполняются вне `serverAiStep()`.
   - Исследован метод `Mob.checkDespawn()`: подтверждено использование `noActionTime > 600` для деспавна сущностей дальше 32 блоков при отсутствии игрока рядом.
   - Проверены методы доступа к `noActionTime`: в `LivingEntity.java` методы `public int getNoActionTime()` и `public void setNoActionTime(int)` являются публичными.
3. **`SleepingWakeupEvents.java` и структура `ExplosionEvent.Detonate`:**
   - Исследован класс `net.neoforged.neoforge.event.level.ExplosionEvent.Detonate`: проверены методы `getAffectedEntities()` и `getAffectedBlocks()`.
   - Проверен шторм вызовов: старый код на каждый `BlockPos` из `getAffectedBlocks()` вызывал `level.getEntitiesOfClass(...)`.
4. **`IdleChecker.java` и цепочка проверок:**
   - Разделены быстрые проверки пробуждения (урон, огонь, цель, навигация, падение, жидкость, скорость, эффекты) и тяжёлые проверки пригодности (поиск игроков, POI, фильтры сущностей).
   - Проверена формула интервального распределения `Math.floorMod((long) tickCount + id, interval)` на предмет переполнения при `tickCount = Integer.MAX_VALUE`.

---

## 2. Что подтвердилось из аудита (и что опровергнуто)

* **Подтверждено [SlowdownMonitor]:** Измерение между двумя событиями `Post` действительно захватывало фазу сна сервера (номинально 50 мс), из-за чего здоровый сервер на 20 TPS определялся как находящийся в зоне замедления (> 45 мс) или постоянно сбрасывал счётчик в пограничной зоне.
* **Подтверждено [noActionTime]:** При отмене `serverAiStep()` через `ci.cancel()` ванильная строка `this.noActionTime++` не выполнялась. Спящие враждебные мобы вдали от игрока зависали и никогда не деспавнились естественным путём через `checkDespawn()`.
* **Подтверждено [ExplosionEvent]:** При взрыве ТНТ или Крипера на 100+ блоков выполнялось 100+ независимых поисков сущностей `level.getEntitiesOfClass` вместо одного объединяющего AABB.
* **Подтверждено [Оверхед проверок IdleChecker]:** Проверка 280 мобов на каждом тике стоила дороже, чем ванильный AI коров. Введение интервала в 20 тиков для спящих мобов снижает накладные расходы проверок на 95%.
* **Опровергнуто / Уточнено:** Аудит предполагал необходимость сложного Mixin-аксессора или рефлексии для `noActionTime`. В коде Minecraft 1.21.1 getter и setter `getNoActionTime()`/`setNoActionTime(int)` уже объявлены как `public` в `LivingEntity`, поэтому вмешательство в байткод или AccessTransformer не потребовалось.

---

## 3. Что изменено (файлы и суть изменений)

1. **`src/main/java/io/github/gdacoff/killthefrizz/util/SlowdownMonitor.java`:**
   - Введено отслеживание начала тика `volatile long tickStartNanos`.
   - Добавлен метод `onServerTickPre(ServerTickEvent.Pre)`, фиксирующий момент старта вычислений тика.
   - Метод `onServerTickPost(ServerTickEvent.Post)` теперь вычисляет чистый MSPT: `(System.nanoTime() - tickStartNanos) / 1_000_000.0`.
   - Сохранён метод-алиас `onServerTick(Post)` для обратной совместимости.
2. **`src/main/java/io/github/gdacoff/killthefrizz/KillTheFrizz.java`:**
   - В шину `NeoForge.EVENT_BUS` зарегистрированы оба слушателя: `SlowdownMonitor::onServerTickPre` и `SlowdownMonitor::onServerTickPost`.
3. **`src/main/java/io/github/gdacoff/killthefrizz/mixin/MobMixin.java`:**
   - Добавлено поле `@Unique private boolean ktf$wasSleeping = false`.
   - В вызов `IdleChecker.shouldSleep(mob, this.ktf$wasSleeping)` передаётся предыдущее состояние сна.
   - При засыпании вызывается `mob.setNoActionTime(mob.getNoActionTime() + 1)`, гарантируя штатный инкремент счётчика деспавна ровно 1 раз за тик.
4. **`src/main/java/io/github/gdacoff/killthefrizz/sleeping/IdleChecker.java`:**
   - Добавлен перегруженный метод `shouldSleep(Mob mob, boolean wasSleeping)`.
   - Реализована двухэтапная модель:
     - **Каждый тик:** мгновенная проверка условий пробуждения (урон, огонь, цель, навигация, падение, жидкости, скорость, поводки, эффекты, `isForceAwake`). При любом триггере моб просыпается немедленно.
     - **Раз в 20 тиков (со сдвигом по entityId):** полная проверка пригодности ко сну (поиск игроков `hasNearbyAlivePlayer`, heartbeat, фильтрация по типу сущности).
   - Применено безопасное к переполнению деление с остатком `Math.floorMod((long) mob.tickCount + mob.getId(), interval)`.
5. **`src/main/java/io/github/gdacoff/killthefrizz/sleeping/SleepingWakeupEvents.java`:**
   - В `onExplosionDetonate` добавлен вызов `computeExplosionBoundingBox(blocks, radius)`.
   - Все затронутые взрывом сущности из `event.getAffectedEntities()` пробуждаются напрямую (0 запросов к миру).
   - Для разрушенных блоков формируется единый ограничивающий параллелепипед (AABB), и выполняется **ровно 1 пространственный запрос** `level.getEntitiesOfClass`.
6. **`src/test/java/io/github/gdacoff/killthefrizz/sleeping/SleepingWakeupEventsTest.java` (Новый):**
   - 5 юнит-тестов на вычисление AABB взрыва (null, пустой список, один блок, группа блоков, нулевой радиус).
7. **`src/test/java/io/github/gdacoff/killthefrizz/util/SlowdownMonitorTest.java`:**
   - Добавлены тесты Pre/Post замеров, устойчивости к одиночным Post-событиям и сброса состояния.
8. **`src/test/java/io/github/gdacoff/killthefrizz/sleeping/IdleCheckerTest.java`:**
   - Добавлены тесты интервальной математики, устойчивости к переполнению `tickCount = Integer.MAX_VALUE`, отрицательных тиков и проверки сигнатуры перегруженного метода.
9. **`README.md` и `PROGRESS.md`:**
   - Приведены в честное состояние: `GpuCulling`, `GigabyteSqueezer`, `AdaptiveDistance` помечены как прототипы/заглушки (не подключённые к рендеру и аллокациям).
   - Статус `SleepingEntities` помечен как экспериментальный с явным указанием ограничений.

---

## 4. Что намеренно не сделано (и почему)

1. **Не удалены классы `GpuCulling`, `GigabyteSqueezer`, `AdaptiveDistance`:**
   - Согласно регламенту Phase 7 (раздел 0 и 4), удаление и вырезание подсистем требует отдельного явного решения владельца репозитория. В этой фазе они только честно документированы как неактивные заглушки.
2. **Не изменены пороговые значения `SlowdownMonitor`:**
   - Пороги 45 мс (замедление) и 30 мс (восстановление) сохранены, так как после исправления замера на чистый MSPT они полностью соответствуют логике защиты сервера от просадок ниже 20 TPS.
3. **Не добавлен глобальный тикер сущностей:**
   - Состояние сна хранится непосредственно в экземпляре сущности через `@Unique` поле миксина. Это исключило любые утечки памяти (`WeakHashMap`/`UUID`-мапы) и синхронизационные блокировки.

---
## 5. Тесты

- Команда: `./gradlew test --rerun`
- Результат: **BUILD SUCCESSFUL**
- Количество тестов: **81** (все пройдены успешно, 0 ошибок, 0 пропусков)
  - `AdaptiveDistanceTest`: 17 тестов
  - `GigabyteSqueezerTest`: 17 тестов
  - `GpuCullingTest`: 4 теста
  - `IdleCheckerTest`: 14 тестов (+6 новых тестов)
  - `KtfConfigTest`: 18 тестов
  - `SleepingWakeupEventsTest`: 5 тестов (новый тестовый класс)
  - `SlowdownMonitorTest`: 6 тестов (+2 новых теста)

---

## 6. Сборка

- Команда: `./gradlew build`
- Результат: **BUILD SUCCESSFUL in 654ms**
- Сформированный артефакт: `build/libs/killthefrizz-1.0.0.jar`

---

## 7. Benchmark-план для владельца проекта

Для проверки эффективности Phase 7 на целевых сложных сущностях (где ванильный AI действительно потребляет процессорное время) владельцу рекомендуется выполнить следующие тесты:

### Сценарий 1: Контрольная группа (280 коров)
1. Телепортироваться к загону с 280 коровами.
2. Запустить контрольный замер без мода (`/killthefrizz preset off`).
3. Снять профиль Spark: `/spark sampler --timeout 60`.
4. Включить пресет `SAFE` (`/killthefrizz preset safe`), подождать 60 секунд.
5. Снять повторный профиль Spark.
6. **Ожидаемый результат:** В отличие от старой версии, P95/P99 не должны деградировать, так как у спящих коров 19 тиков из 20 выполняются только элементарные проверки флагов в памяти (~2 нс).

### Сценарий 2: Торговый зал жителей (100 Villagers + POI)
1. Создать торговый зал со 100 деревенскими жителями и рабочими местами.
2. Игрок отходит на 48 блоков (за пределы `sleepRadius`).
3. Снять показания `/tick query` и `/spark sampler --timeout 120`.
4. Сравнить долю времени `net.minecraft.world.entity.npc.Villager` и `Mob.serverAiStep` в `OFF` и `SAFE`.
5. **Ожидаемый результат:** Снижение процессорного времени на обработку AI жителей на 15–25%.

### Сценарий 3: Проверка деспавна враждебных мобов
1. Заспавнить 50 зомби на расстоянии 40 блоков от игрока в тёмной зоне.
2. Убедиться через `/killthefrizz stats`, что мобы перешли в спящий режим.
3. Подождать 40–60 секунд.
4. Убедиться, что по мере роста `noActionTime > 600` зомби штатно деспавнятся ванильной механикой.

---

## 8. Риски и ограничения

1. **Пробуждение при дистанционном воздействии:** Если игрок стреляет из лука с расстояния 70 блоков (вне `sleepRadius`), моб проснётся в момент получения урона (`LivingDamageEvent.Post` вызывает `IdleChecker.wakeUp(mob, 40)`), но не увидит натянутую тетиву до попадания.
2. **Интервал 20 тиков:** При приближении игрока на границу радиуса сна (64 блока) моб проснётся в течение максимум 20 тиков (1 секунда). При скорости бега игрока 5.6 блоков/с он успеет приблизиться максимум на 5 блоков до пробуждения моба, что визуально незаметно.
3. **Совместимость с Lithium:** Если на сервере установлен Lithium или ModernFix, они ускоряют внутреннюю математику `serverAiStep`. В связке с KilllTheFrizz конфликтов инъекций нет (KilllTheFrizz использует `@Inject(at=@At("HEAD"), cancellable=true)`), при этом интервальный пропуск тиков экономит время даже поверх алгоритмов Lithium.

---

## 9. Git-статус

- Исходная ветка: `phase-7-core-sleepingentities`
- Рабочая ветка агента: `deepseek/phase-7-implementation-specifi-20261101-181755`
- Состояние рабочей директории: Все изменения проверены тестами, готовы к объединению в коммит.
- Имя коммита: `Phase 7: fix core timing and reduce sleeping entity checks`

---

## 10. Решения, требующие владельца проекта

1. **Судьба неработающих модулей (Retire):** Согласовать полное физическое удаление классов `GpuCulling`, `GigabyteSqueezer` и `AdaptiveDistance` в Phase 8 для очистки кодовой базы от балласта.
2. **Конфигурируемость интервала сна:** Оставить ли интервал перепроверки сна фиксированным (20 тиков = 1 секунда) или вынести параметр `staggeredEvalIntervalTicks` в `killthefrizz-server.toml`.