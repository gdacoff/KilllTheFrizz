<div align="center">
  <!-- LOGO / BANNER PLACEHOLDER -->
  <!-- <img src="path_to_banner_or_logo" width="800" alt="KilllTheFrizz Banner"> -->

  <h1>KilllTheFrizz</h1>
  <p><b>Targeted Server-Side Entity Performance Engineering Mod for Minecraft 1.21.1</b></p>

  <img src="https://img.shields.io/badge/Minecraft-1.21.1-5E8A5E?style=for-the-badge&logo=minecraft&logoColor=white" alt="Minecraft 1.21.1" />
  <img src="https://img.shields.io/badge/NeoForge-21.1.265-191919?style=for-the-badge&logo=forge&logoColor=white" alt="NeoForge 21.1.265" />
  <img src="https://img.shields.io/badge/Java-21_LTS-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white" alt="Java 21" />
  <img src="https://img.shields.io/badge/JUnit_5-144_Passing-25A162?style=for-the-badge&logo=junit5&logoColor=white" alt="144 Passing Tests" />
  <br><br>
  <img src="https://img.shields.io/badge/Status-Completed-success?style=for-the-badge" alt="Status Completed" />
  <img src="https://img.shields.io/badge/Evaluator_Allocations-0_Heap_Objects-blue?style=for-the-badge" alt="Zero Evaluator Heap Allocations" />
  <img src="https://img.shields.io/badge/Source_Code-Open_Source-brightgreen?style=for-the-badge" alt="Open Source" />
</div>

---

## 📝 About the Project

**KilllTheFrizz** is a specialized server-side Minecraft performance optimization mod focused on reducing redundant entity simulation overhead while preserving core survival gameplay contracts within bounded, audited temporal tolerances.

Rather than applying arbitrary tick-skipping heuristics, **KilllTheFrizz** is built around an empirical performance engineering lifecycle:

$$
\text{Profile} \longrightarrow \text{Identify} \longrightarrow \text{Hypothesize} \longrightarrow \text{Implement} \longrightarrow \text{Benchmark} \longrightarrow \text{Regression Test} \longrightarrow \text{Audit}
$$

Every optimization subsystem targets a verified bottleneck isolated via nanosecond-level telemetry and Mojang bytecode inspection, and is validated through alternating A/B benchmarks, statistical hypothesis testing ($t$-tests, Cohen's $d$), and in-game regression matrices.

---

## ⚙️ Core Engineered Subsystems

### 1. `SleepingEntities` — Inactive AI & Mutual Push Deferral (Phases 7.1 – 7.8)

* **Root-Cause Investigation:** Profiling dense enclosures (250 cows) demonstrated that `Mob.serverAiStep()` accounts for only $3\text{–}4\%$ of passive mob tick cost, whereas physics movement (`travel()`) and collision broadphase (`pushEntities()`) consume up to $96\%$. Furthermore, touching mobs continuously imparted $\Delta v \approx 0.05\text{ blocks/tick}$ reciprocal push impulses—exceeding `maxIdleVelocity` ($0.03$)—which caused endless mutual wake-ups and restricted dense-pen sleep rates to $1.49\%$.
* **Staggered Eligibility & Fast Wake-Up (`IdleChecker`):**
  * **Awake Hot-Path Early Exit:** Uses bitwise modulo gating `((tickCount + entityId) & 15) == 0`. On $15/16$ intermediate ticks ($93.75\%$), awake mobs exit immediately in $\approx 40\text{–}50\text{ ns}$ without executing distance or registry queries.
  * **Per-Tick Wake-Up Invariants:** Sleeping mobs evaluate cheap wake-up checks (damage, fire, active target, fluid contact, velocity, passengers/leashes, active navigation, and heartbeat) on every tick.
  * **Overflow-Safe Staggering:** Re-evaluations use `Math.floorMod((long) tickCount + id, interval)` to prevent 32-bit integer overflow across long server uptimes.
* **Mutual Push Deferral (`LivingEntityMixin` & `SleepStateAccess`):**
  * Sleeping entities skip autonomous `pushEntities()` execution, breaking the reciprocal collision wake-up cascade and raising dense-pen sleep ratios from $1.49\%$ to $14.76\%$.
  * Awake entities and players continue to push sleeping entities normally, transferring velocity and waking them on the subsequent tick.
  * **Periodic Cramming Enforcement:** When `maxEntityCramming > 0`, sleeping entities evaluate cramming density on staggered 16-tick intervals (`((tickCount + id) & 15) == 0`).
* **Event & Tick Monitoring (`SleepingWakeupEvents` & `SlowdownMonitor`):**
  * `SlowdownMonitor` tracks server tick durations via `System.nanoTime()` with a $10\text{ s}$ sanity bound to filter out GC pause or clock-jump anomalies.
  * `SleepingWakeupEvents` wakes affected entities immediately upon explosions (with bounding-box radius clamped to $[0, 32]$).

```text
LivingEntity.aiStep()
  ├── 1. Apply velocity damping & friction
  ├── 2. invoke serverAiStep()
  │       └── IdleChecker.shouldSleep(mob, wasSleeping)
  │             ├── [Awake Path]: ((tickCount + id) & 15) != 0 ──► Early Exit (~40-50 ns)
  │             └── [Sleep Path]: Per-tick wake checks (hurt, fire, fluid, motion, nav)
  │                   └── IF eligible ──► ci.cancel() [Skip Goal & Navigation AI]
  ├── 3. invoke travel()
  └── 4. invoke pushEntities()
          └── IF ktf$wasSleeping == true:
                ├── Evaluate maxEntityCramming on staggered & 15 cadence
                └── ci.cancel() [Skip mutual O(N²) push impulse cascade]
```

### 2. `VillagerBrainThrottler` — State-Aware Adaptive Brain Scheduling (Phases 7.9 – 7.9.7)

* **The Computational Asymmetry:** Whole-server profiling revealed that 1 Villager ($\approx 30,000\text{ ns/tick}$) consumes the CPU equivalent of $\approx 25$ cows ($1,160\text{ ns/tick}$). In mixed farm workloads (160 animals + 40 villagers), **40 villagers accounted for $72.1\%$ of total entity AI compute**.
* **Internal `Brain.tick()` Telemetry (`KtfProfiler`):** Direct nanosecond instrumentation inside `BrainMixin` disproved the common assumption that sensors dominate villager overhead:
  * `startEachNonRunningBehavior()` consumes **$63.2\%\text{–}69.4\%$ of brain time** ($15.5\text{–}16.7\ \mu\text{s/tick}$) iterating multi-tiered priority behavior trees.
  * `tickEachRunningBehavior()` consumes **$20.0\%\text{–}22.3\%$** ($4.9\text{–}5.5\ \mu\text{s/tick}$).
  * `tickSensors()` consumes only **$6.2\%\text{–}14.7\%$** ($1.4\text{–}3.8\ \mu\text{s/tick}$).
  * `updateActivityFromSchedule()` consumes **$< 0.4\%$** ($50\text{–}110\text{ ns}$, guarded by a 20-tick vanilla cooldown).
* **Adaptive Frequency Scaling (`VillagerMixin` `@WrapOperation`):**
  * **Stationary Trading Stalls:** Confined idle villagers (`velocity^2 <= 0.0009`, navigation done) are throttled from 20 Hz to **5 Hz** (`stallInterval = 4`, executing 1 tick in 4).
  * **Bed Sleeping (`LivingEntity.isSleeping()`):** Villagers sleeping in beds are throttled from 20 Hz to **2 Hz** (`bedInterval = 10`, executing 1 tick in 10).
* **Mandatory 20 Hz Fast-Path Bypasses:** Villagers immediately execute at unthrottled 20 Hz when:
  * A player has their trading GUI open (`isTrading()`).
  * Taking damage or burning (`hurtTime > 0 || isOnFire()`).
  * Panic state is active (`brain.isActive(Activity.PANIC)`).
  * Actively pathfinding (`!getNavigation().isDone()`) or moving ($\Delta v^2 > 0.0009$).
  * Unemployed and searching for a workstation (`profession == NONE`).
* **Temporal Delta Compensation & Hardening (`BrainAccessor`, `SensorAccessor`, `ExpirableValueAccessor`):** Compensates sensor countdowns and memory TTL expiration deltas on skipped ticks so workstation restocking (`WorkAtPoi` world-clock check), schedule transitions, and hostile threat reaction ($25.0\text{ ticks} / 1.25\text{ s}$) remain accurate.

### 3. `CollisionThrottler` — Checkerboard Broadphase Staggering (Phases 7.10 – 7.10.9)

* **The Broadphase Bottleneck:** When a player is near a farm (keeping mobs awake), `LivingEntity.pushEntities()` consumes **$70.0\%$ of the entity tick loop** ($97.7\text{ ms}$ vs $41.9\text{ ms}$ AI across 150 ticks in a 250-cow pen). Bytecode and profiler dissection isolated three root causes:
  1. **Spatial Broadphase (`Level.getEntities` + `EntitySection` traversal):** Accounts for **$\approx 82\%$** of `pushEntities()` time ($250 \times 250 = 62,500$ pairwise checks/tick).
  2. **Transient Collection Churn:** `Level.getEntities()` instantiates `Lists.newArrayList()` on every call ($\approx 5,000\text{ ArrayList allocations/sec}$ in a 250-mob pen), accounting for **$\approx 11\%$** of overhead.
  3. **Symmetric Double-Push Redundancy:** `Entity.push(Entity)` applies impulse vectors to *both* entities simultaneously ($\approx 7\%$ of time), meaning every pair $(i, j)$ is queried and pushed twice per tick.
* **Why Navigation Was Spared (Phase 7.10.3 Audit):** Profiling proved that new pathfinding (`DefaultRandomPos`) accounts for only **$3.1\%$** of entity loop time ($\approx 2.08\text{ mobs/tick}$ via `RandomStrollGoal`'s 120-tick interval). Navigation was left unthrottled to guarantee zero risk to panic fleeing, food temptation, and breeding.
* **Checkerboard Cadence Implementation:**
  * Hooked into `LivingEntityMixin.pushEntities()` via `CollisionThrottler.shouldPushEntities()`.
  * Eligible stationary awake entities reduce broadphase queries from **20 Hz to 10 Hz** using `Math.floorMod((long) tickCount + entityId, 2) == 0`. Because `Entity.push()` transfers momentum symmetrically, 10 Hz per-entity querying preserves 20 Hz effective pairwise repulsion.
  * Cuts temporary `ArrayList` instantiations inside `Level.getEntities()` by **$50\%$** (saving $\approx 2,500\text{ allocations/sec}$ in 250-mob pens).
* **Audited Velocity Threshold & Fast-Paths (Phase 7.10.9.1):**
  * Bytecode audit confirmed the active stationary cutoff constant:

$$
\text{STATIONARY\_VELOCITY\_SQR} = 0.04 \quad (|\vec{v}| = 0.20\text{ blocks/tick})
$$

  * **20 Hz Fast-Path Bypasses:** Full every-tick collision processing is preserved whenever a live player is within $< 6.0\text{ blocks}$, velocity exceeds $0.20\text{ blocks/tick}$, `hurtTime > 0`, `isOnFire()`, `isLeashed()`, or `isPassenger() / isVehicle()`.

---

## 📊 Empirical Benchmarks & Statistical Validation

> **Important Interpretation Note:** All benchmarks were executed on an isolated dedicated server (Java 21.0.6+7-LTS, NeoForge 21.1.265, `-Xms4G -Xmx4G -XX:+UseG1GC`) using alternating OFF/SAFE repetitions. **Method-level reductions** measure direct savings in the targeted subsystem, whereas **whole-server MSPT** reflects total server tick duration across all world systems and varies by workload composition and sample size.

### 1. `SleepingEntities` Controlled Benchmark (Phase 7.8)

*Protocol: 5 alternating OFF/SAFE repetitions per scenario across 3 scenarios (30 total runs), 1,104 recorded ticks (50s) per run after 60s JIT C2 warmup.*

| Workload Scenario | Metric | Vanilla (OFF) Mean $\pm$ SD | SAFE Mean $\pm$ SD | Relative Change | Statistical Significance |
| :--- | :--- | :---: | :---: | :---: | :--- |
| **Scenario A: Dense Pen**<br>*(250 Cows in 16x16)* | **Median MSPT**<br>**P95 MSPT**<br>`pushEntities()` Total Time<br>Entity Sleeping Ratio | $3.20 \pm 0.07\text{ ms}$<br>$3.84 \pm 0.09\text{ ms}$<br>$513.72 \pm 7.68\text{ ms}$<br>$0.00\%$ | $3.06 \pm 0.05\text{ ms}$<br>$3.72 \pm 0.23\text{ ms}$<br>$429.74 \pm 7.19\text{ ms}$<br>$14.76 \pm 1.36\%$ | **-4.38%**<br>**-3.13%**<br>**-16.35%**<br>**+14.76%** | $t = 3.50, p < 0.01$<br>$t = 1.10, p > 0.05$ ($1.3\sigma$)<br>$t = 17.85, p < 10^{-5}$<br>$40,740\text{ calls skipped/run}$ |
| **Scenario B: Mixed Farm**<br>*(160 Animals + 40 Villagers)* | **Median MSPT**<br>**P95 MSPT**<br>Total Entity Compute Time<br>Entity Sleeping Ratio | $4.04 \pm 0.13\text{ ms}$<br>$4.96 \pm 0.11\text{ ms}$<br>$2,158.8 \pm 51.7\text{ ms}$<br>$0.00\%$ | $3.56 \pm 0.11\text{ ms}$<br>$4.44 \pm 0.11\text{ ms}$<br>$1,664.2 \pm 42.8\text{ ms}$<br>$32.66 \pm 0.75\%$ | **-11.88%**<br>**-10.48%**<br>**-22.91%**<br>**+32.66%** | $t = 6.10, p = 0.00028$<br>$t = 7.21, p = 0.00009$ ($4.7\sigma$)<br>$t = 16.48, p < 10^{-5}$<br>$72,100\text{ calls skipped/run}$ |
| **Scenario C: Roaming Control**<br>*(100 Wandering Cows)* | **Median MSPT**<br>**P95 MSPT**<br>IdleCheck Hook Overhead | $0.80 \pm 0.00\text{ ms}$<br>$1.06 \pm 0.09\text{ ms}$<br>$0.00\ \mu\text{s/tick}$ | $0.78 \pm 0.04\text{ ms}$<br>$1.02 \pm 0.04\text{ ms}$<br>$+1.87\ \mu\text{s/tick}$ | $-2.50\%$<br>$-3.77\%$<br>$+0.0037\%$ budget | $t = 1.00, p > 0.35$ (Noise)<br>$t = 0.89, p > 0.40$ (Noise)<br>$18.7\text{ ns/mob/tick}$ (Zero penalty) |

### 2. `VillagerBrainThrottler` Benchmarks (Phases 7.9.6 & 7.9.7.5)

*Below are both the Phase 7.9.6 method-level campaign results (200-tick windows) and the Phase 7.9.7.5 hardened multi-scenario verification (30 runs across 5 topologies, 150-tick windows).*

| Campaign & Workload | `Brain.tick()` Calls | Brain CPU Time | Median MSPT ($\Delta\%$) | P95 MSPT ($\Delta\%$) | Notes / Statistical Verification |
| :--- | :---: | :---: | :---: | :---: | :--- |
| **Phase 7.9.6: 40 Stationary Stalls** | **-74.67%**<br>*(7,960 $\to$ 2,016)* | **-24.93%**<br>*(281.2 $\to$ 211.1 ms)* | **-28.25%**<br>*(1.53 $\to$ 1.10 ms)* | **-19.62%**<br>*(3.57 $\to$ 2.87 ms)* | $t = 4.91, p = 0.039$. Eliminates $3/4$ of stationary brain ticks. |
| **Phase 7.9.6: Mixed 200 Entities**<br>*(40 Villagers + 160 Animals)* | **-71.54%**<br>*(7,960 $\to$ 2,265)* | **-31.11%**<br>*(306.6 $\to$ 211.2 ms)* | $-6.07\%$<br>*(4.40 $\to$ 4.13 ms)* | $-6.92\%$<br>*(6.27 $\to$ 5.83 ms)* | Total AI time dropped **-23.56%** ($t = 9.44, p = 0.0014$). |
| **Phase 7.9.7.5: 40 Stalls (Hardened)** | **-74.4%**<br>*(5,960 $\to$ 1,527)* | **-27.1%**<br>*(269.9 $\to$ 196.8 ms)* | **-29.7%**<br>*(2.13 $\to$ 1.50 ms)* | **-9.2%**<br>*(4.00 $\to$ 3.63 ms)* | Temporal compensation active ($p = 0.012$). |
| **Phase 7.9.7.5: 40 Active Roaming** | **-73.0%**<br>*(5,960 $\to$ 1,608)* | **-27.5%**<br>*(316.1 $\to$ 229.1 ms)* | **-32.9%**<br>*(2.53 $\to$ 1.70 ms)* | **-20.8%**<br>*(5.13 $\to$ 4.07 ms)* | Dynamic velocity fast-path active ($p = 0.041$). |
| **Phase 7.9.7.5: 40 at Workstations** | **-74.7%**<br>*(5,960 $\to$ 1,509)* | **-24.4%**<br>*(479.8 $\to$ 362.7 ms)* | **-19.4%**<br>*(3.43 $\to$ 2.77 ms)* | **-27.7%**<br>*(7.47 $\to$ 5.40 ms)* | 40 Librarians + 40 Lecterns ($p = 0.023$). |
| **Phase 7.9.7.5: Control (160 Animals)** | $0 \to 0$ | $0.0\text{ ms}$ | $+2.8\%$ (Noise)<br>*(2.40 $\to$ 2.47 ms)* | $-1.1\%$ (Noise)<br>*(3.00 $\to$ 2.97 ms)* | $p = 0.68 > 0.05$. Zero overhead when no villagers exist. |

### 3. `CollisionThrottler` Controlled Benchmark & Audit Reconciliation (Phases 7.10.7 & 7.10.9)

*Protocol: 5 alternating OFF/SAFE repetitions per scenario (20 runs total), 150-tick windows after 10s warmup.*

| Workload & Metric | Vanilla (OFF) Mean $\pm$ SD | SAFE Mean $\pm$ SD | Relative Change | Welch's $t$ & $p$-value | Effect Size & Audit Notes |
| :--- | :---: | :---: | :---: | :---: | :--- |
| **Workload 1: 250 Cows (5x5 Pen)** | | | | | |
| `pushEntities()` Calls | $29,174 \pm 10,322$ | $13,191 \pm 3,961$ | **-54.8%** | $t = 3.23, \mathbf{p = 0.022}$ | **Statistically Significant** ($d = 2.04$) |
| `pushEntities()` Execution Time | $48.84 \pm 1.96\text{ ms}$ | $24.60 \pm 3.88\text{ ms}$ | **-49.6%** | $t = 12.47, \mathbf{p < 0.0001}$ | **Statistically Significant** ($d = 7.88$) |
| Median Server MSPT | $3.20 \pm 1.42\text{ ms}$ | $3.24 \pm 1.53\text{ ms}$ | **+1.2%** | $t = -0.04, p = 0.966$ | Not statistically significant ($d = -0.03$) |
| P95 Server MSPT | $3.84 \pm 1.55\text{ ms}$ | $3.62 \pm 1.61\text{ ms}$ | **-5.7%** | $t = 0.22, p = 0.832$ | Not statistically significant ($d = 0.14$) |
| Maximum Server MSPT | $8.16 \pm 2.27\text{ ms}$ | $5.46 \pm 2.13\text{ ms}$ | **-33.1%** | $t = 1.94, p = 0.088$ | Large spike reduction ($d = 1.22$) |
| **Workload 2: 40 Villagers + 160 Animals** | | | | | |
| `pushEntities()` Calls | $25,926 \pm 3,417$ | $11,046 \pm 1,874$ | **-57.4%** | $t = 8.54, \mathbf{p < 0.0001}$ | **Statistically Significant** ($d = 5.40$) |
| `pushEntities()` Execution Time | $36.39 \pm 6.64\text{ ms}$ | $15.07 \pm 4.66\text{ ms}$ | **-58.6%** | $t = 5.87, \mathbf{p = 0.0006}$ | **Statistically Significant** ($d = 3.71$) |
| Median Server MSPT | $6.00 \pm 0.98\text{ ms}$ | $5.04 \pm 1.05\text{ ms}$ | **-16.0%** | $t = 1.49, p = 0.174$ | Large effect ($d = 0.94$), $p > 0.05$ at $N=5$ |
| P95 Server MSPT | $8.92 \pm 1.19\text{ ms}$ | $7.70 \pm 1.47\text{ ms}$ | **-13.7%** | $t = 1.44, p = 0.189$ | Large effect ($d = 0.91$), $p > 0.05$ at $N=5$ |
| Maximum Server MSPT | $18.04 \pm 6.32\text{ ms}$ | $25.84 \pm 30.16\text{ ms}$ | **+43.2%** | $t = -0.57, p = 0.598$ | Confounded by disk flush (see below) |

* **Forensic Audit Reconciliations (Phase 7.10.9.3):**
  1. **Target-Method vs Whole-Server MSPT:** Target-method call and execution-time reductions ($-49.6\%$ to $-58.6\%$) are statistically significant ($p < 0.001$). Whole-server MSPT differences in this 5-repetition sample did not reach $p < 0.05$ and are therefore not marketed as guaranteed whole-server speedups.
  2. **Workload 2 Max MSPT Outlier ($+43.2\%$):** Server `debug.log` auditing confirmed that during SAFE Repetition 4 (timestamp `18:24:35`), an internal world autosave flushed chunks to disk on the main server thread, causing a single $78.60\text{ ms}$ I/O spike while `pushEntities()` max duration was only $1.4\ \mu\text{s}$.
  3. **Workload 1 Call Variance:** In Repetitions 1 & 2, vanilla `maxEntityCramming = 24` suffocated ~130 cows during the 10s warmup, leaving 119 cows where the SAFE/OFF call ratio was an exact $50.00\%$ ($8,939 / 17,880$).

---

## 🔬 Profiler Telemetry & Memory Overhead Verification

* **Diagnostic Profiler (`KtfProfiler` — Phase 7.6.1):**
  * Tracks nanosecond durations and invocation counts across `IdleChecker.shouldSleep`, `Mob.serverAiStep`, `LivingEntity.pushEntities`, and `Villager` brain sub-phases via `/killthefrizz profile [start|stop|report|reset]`.
  * **Bytecode-Verified Exclusivity:** Disassembly of `MobMixin` and `LivingEntityMixin` confirmed that cancelled ticks (`ci.cancel()`) never increment executed AI timers.
  * **Measured Overhead:** Active profiling adds only $\approx 90\text{ ns/entity/tick}$ ($\approx 0.01\text{–}0.02\text{ ms/tick}$ for 250 cows, or **$0.02\text{–}0.04\%$** of the $50.0\text{ ms}$ tick budget). When inactive (`recording = false`), overhead is $< 1\text{ ns}$.
* **Evaluator Allocation & GC Audit (Phases 7.6.1 & 7.10.9.4):**
  * While the underlying JVM and vanilla Minecraft continue normal heap activity, custom evaluators (`IdleChecker`, `VillagerBrainThrottler`, `CollisionThrottler`, and `KtfProfiler`) use strictly primitive scalar fields and perform **zero object instantiations (`0 new` allocations)** in their hot paths.
  * Furthermore, skipping 50% of stationary `pushEntities()` calls avoids $\approx 2,500$ transient `new ArrayList()` allocations per second in 250-mob enclosures. Spark G1GC Young Generation telemetry verified no increase in GC pause frequency or duration.

---

## 🛡️ Automated Testing & Gameplay Regression Matrices

The project maintains **144 passing JUnit 5 unit tests** (covering modulo stagger math, 64-bit overflow boundaries, velocity thresholds, profiler state isolation, and fail-open behavior) alongside **31 passed in-game regression checks** across three dedicated validation suites:

1. **Sleeping Entities Regression Suite (Phase 7.7.3 — 10/10 PASS):** Verified player damage wake-up (`hurtTime = 10`), awake-to-sleeping collision impulse wake-up ($+0.05$ velocity transfer), 250-cow enclosure stability, wheat feeding & breeding (`InLove: 600`), leash physics, boat/minecart mounting, `maxEntityCramming = 24` suffocation, `save-all flush` NBT persistence, Nether portal cooldowns, and TNT explosion wake-ups.
2. **Villager Gameplay Contracts (Phase 7.9.7.4 — 13/13 PASS):** Verified identical zombie flee reaction latency ($25.0\text{ ticks} / 1.25\text{ s}$ in both OFF and SAFE), workstation claiming, world-clock restocking parity ($\Delta \le 4\text{ ticks}$), dusk bed sleeping, dawn wake-up, and memory TTL accuracy.
3. **Collision Throttler Regression Matrix (Phase 7.10.6 — 8/8 PASS):** Verified `< 6.0m` player proximity 20 Hz bypass, overlapping cow separation ($1.668\text{ m}$ within 30 ticks), cramming stabilization (35 cows reduced to 24 in $3.0\text{ s}$), leash tension, vehicle sync, damage wake-up, villager trade restocking, and clean chunk serialization.

---

## ⚠️ Documented Behavioral Disclosures & Known Limitations

In accordance with strict engineering transparency, the following bounded behavioral differences and audit findings are disclosed:

1. **Collision & Cramming Temporal Semantics (Phase 7.10.9.2):** Vanilla Minecraft evaluates `maxEntityCramming` probabilistically inside `pushEntities()` with $P = 0.25$ ($\mathbb{E} = 4\text{ ticks} / 200\text{ ms}$). For stationary entities throttled to 10 Hz under `CollisionThrottler`, cramming evaluation shifts to $\mathbb{E} = 8\text{ ticks}$ ($400\text{ ms}$). Regression testing confirms herd suffocation still stabilizes to the exact vanilla cap (24 entities), but tick-for-tick damage timing is not identical to vanilla.
2. **Stationary Villager Brain Cadence (Phase 7.9.7):** Stationary villagers in trading stalls and beds tick their `Brain` at 5 Hz (every 4 ticks) and 2 Hz (every 10 ticks) respectively. If a workstation is broken while a villager is stationary, POI replacement evaluation occurs within $\le 4\text{ ticks}$ ($0.2\text{ s}$), and threat reaction jitter is bounded within $\le 3\text{ ticks}$ ($150\text{ ms}$).
3. **Fail-Open `Throwable` Catch Scope (Phase 7.10.9 Audit):** `CollisionThrottler` and `VillagerBrainThrottler` wrap evaluation logic in a `catch (Throwable t)` fail-open block that logs a rate-limited warning and falls back to 20 Hz vanilla execution. While `IdleChecker` was narrowed to `catch (Exception)` in Phase 7.1, the phase 7.10.9 integrity audit noted that catching `Throwable` in the remaining throttlers could theoretically intercept fatal JVM `Error` subclasses (`OutOfMemoryError`, `VirtualMachineError`); narrowing those catch clauses is tracked for future refinement.
4. **Workload Dependency:** Reductions in `Brain.tick()` or `pushEntities()` execution time do not guarantee proportional reductions in total server MSPT across all worlds, particularly when server tick time is dominated by chunk generation, block entities, or network packet serialization.

---

## 🔓 Source Code & Licensing

* **Repository Status:** This repository is **Open Source**, with Phases 7.1 through 7.10.9 (`SleepingEntities`, `VillagerBrainThrottler`, and `CollisionThrottler`) completed, audited, and verified. Future development (Phase 7.11+) is planned to investigate chunk and block-entity processing.
* **License:** Formal open-source licensing terms have not yet been finalized. Until an explicit `LICENSE` file is added to the root directory, standard copyright protections apply.

For technical inquiries, performance audits, or collaboration:

<a href="https://discord.com/users/846024512611942401">
  <img src="https://img.shields.io/badge/Discord-8A2BE2?style=flat-square&logo=discord&logoColor=white" alt="Discord" />
</a>

---

<div align="center">
  <i>Engineered with precision by <b><a href="https://github.com/gdacoff">KillerZero</a></b> — Measure First, Optimize Second.</i>
</div>
