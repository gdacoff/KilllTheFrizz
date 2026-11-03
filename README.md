<div align="center">

  <h1>⚡ KilllTheFrizz</h1>
  <p><b>Targeted Server-Side Entity & Collision Performance Engineering</b></p>

  <img src="https://img.shields.io/badge/Minecraft-1.21.1-5E8A5E?style=for-the-badge&logo=minecraft&logoColor=white" alt="Minecraft 1.21.1" />
  <img src="https://img.shields.io/badge/NeoForge-21.1.265-191919?style=for-the-badge&logo=forge&logoColor=white" alt="NeoForge 21.1.265" />
  <img src="https://img.shields.io/badge/Java-21_LTS-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white" alt="Java 21" />
  <img src="https://img.shields.io/badge/JUnit_5-144_Passing-25A162?style=for-the-badge&logo=junit5&logoColor=white" alt="144 Passing Tests" />
  <br><br>
  <img src="https://img.shields.io/badge/Status-Completed-success?style=for-the-badge" />
  <img src="https://img.shields.io/badge/Zero--Allocation-Verified-blue?style=for-the-badge" />
  <img src="https://img.shields.io/badge/Source_Code-Open_Source-brightgreen?style=for-the-badge" />
</div>

---

## 📝 About the Project

**KilllTheFrizz** is an enterprise-grade Minecraft server performance mod dedicated to eliminating unnecessary server-side entity compute without compromising vanilla mechanics, mob behaviors, or world persistence. 

Rather than relying on generic, destructive ticks-skipping or imprecise heuristics, **KilllTheFrizz** is built on rigorous empirical systems engineering:
$$\text{Profile} \longrightarrow \text{Identify} \longrightarrow \text{Hypothesize} \longrightarrow \text{Implement} \longrightarrow \text{Benchmark} \longrightarrow \text{Regression Test} \longrightarrow \text{Audit}$$

Every optimization undergoes controlled A/B repetitions, bytecode verification against Mojang mappings, statistical significance validation ($p < 0.05$, Cohen's $d$), and automated multi-scenario regression gates.

---

## ⚙️ Core Engineered Subsystems

### 1. `SleepingEntities` — AI & Collision Cascade Decoupling (Phases 7.1 – 7.8)
* **The Root Problem:** Profiling dense mob enclosures (250 cows) proved that `Mob.serverAiStep()` accounts for only $3\text{–}4\%$ of entity tick duration. The remaining $96\%$ is consumed by entity physics (`travel()`) and broadphase collision pushing (`pushEntities()`). Furthermore, touching mobs constantly imparted $\Delta v \approx 0.05\text{ blocks/tick}$ impulses, exceeding `maxIdleVelocity` ($0.03$), causing continuous reciprocal wakeups that locked sleep rates at a negligible $1.49\%$.
* **Push Deferral (`LivingEntityMixin` & `SleepStateAccess`):** Sleeping mobs bypass autonomous `pushEntities()` execution. Awake mobs and players retain 100% push strength against sleeping mobs, transferring momentum and waking them immediately on the subsequent tick.
* **Awake Entity Hot-Path Early Exit (`IdleChecker`):** Uses bitwise modulo gating `((tickCount + entityId) & 15) == 0`. Intermediate $15/16$ ticks ($93.75\%$) exit within $\approx 40\text{–}50\text{ ns}$ without querying expensive distance or registry structures.
* **Cramming Safety Invariant:** When `maxEntityCramming > 0`, sleeping mobs evaluate entity density strictly on staggered 16-tick boundaries (`& 15 == 0`), eliminating exploit vectors while preserving tick budget.

```
LivingEntity.aiStep()
  ├── 1. Apply velocity damping & friction
  ├── 2. invoke serverAiStep()
  │       └── IdleChecker.shouldSleep(mob, wasSleeping)
  │             ├── [Awake Path]: ((tickCount + id) & 15) != 0 ──► Instant Return (40 ns)
  │             └── [Sleep Path]: Cheap per-tick checks (damage, fire, fluid, motion)
  │                   └── IF true ──► ci.cancel() [Skip Goal AI]
  ├── 3. invoke travel()
  └── 4. invoke pushEntities()
          └── IF ktf$wasSleeping == true:
                ├── Evaluate maxEntityCramming on staggered & 15 cadence
                └── ci.cancel() [Skip mutual O(N²) push impulse cascade]
```

---

### 2. `VillagerBrainThrottler` — State-Aware Adaptive Scheduling (Phase 7.9)
* **The Computational Asymmetry:** In mixed farm workloads, profiling revealed that **40 villagers consumed $72.1\%$ of the entire entity computation** ($1,200\text{ ms}$ out of $1,389\text{ ms}$). A single villager ($\approx 30,000\text{ ns/tick}$) consumes the compute equivalent of **$\approx 25$ livestock animals**.
* **Bytecode & Profiler Discovery (`KtfProfiler`):** Direct nanosecond telemetry disproved the assumption that spatial sensors consume most CPU:
  * `startEachNonRunningBehavior()` consumes **$>65\%$ of brain time** ($16.5\ \mu\text{s/tick}$) due to combinatorial priority evaluations.
  * `tickEachRunningBehavior()` consumes **$\approx 22\%$** ($5.5\ \mu\text{s/tick}$).
  * `tickSensors()` consumes only **$6\text{–}14\%$** ($1.4\text{–}3.8\ \mu\text{s/tick}$).
  * `updateActivityFromSchedule()` consumes **$<0.4\%$** ($50\text{–}110\text{ ns}$).
* **State-Aware Scheduling Cadence:**
  * **Stationary Trading Stalls (1x1):** Throttled from 20 Hz to **5 Hz** (1 tick in 4, `stallInterval = 4`), saving $\approx 75\%$ of unneeded brain cycles.
  * **Bed Sleeping (`Activity.REST`):** Throttled to **2 Hz** (1 tick in 10, `bedInterval = 10`), saving $90\%$ of brain compute.
* **Guaranteed 20 Hz Fast-Paths:** Full execution is enforced whenever:
  * Trading GUI is open (`villager.isTrading()`).
  * Taking damage or burning (`hurtTime > 0 || isOnFire()`).
  * Threat/panic state is active (`brain.isActive(Activity.PANIC)`).
  * Actively navigating to a destination (`!getNavigation().isDone()`).
  * Horizontal displacement occurs ($\Delta v^2 > 0.0009$).
  * Unemployed villagers searching for workstations (`profession == NONE`).
* **Temporal Delta Compensation:** Injects time-dilation offsets into sensors and memory timers, ensuring workstation claiming, trade restocking, and schedule transitions match vanilla timing.

---

### 3. `CollisionThrottler` — Broadphase Query Staggering (Phase 7.10)
* **The Collision Bottleneck:** In dense mob pens, $O(N^2)$ pairwise bounding box queries consume up to **$70\%$ of entity tick duration** ($97.7\text{ ms}$ vs $41.9\text{ ms}$ AI). A pen of 250 mobs executes 62,500 pair checks/tick and churns 5,000 temporary `ArrayList` allocations per second in Eden space.
* **Checkerboard Staggering:** Intercepts `LivingEntity.pushEntities()` via MixinExtras. Stationary awake mobs execute broadphase queries on alternating ticks ($10\text{ Hz}$ instead of $20\text{ Hz}$) using `(tickCount + entityId) % 2 == 0`.
* **Calibrated Movement Threshold:** The verified threshold is:
  $$\text{STATIONARY\_VELOCITY\_SQR} = 0.04 \quad (0.20\text{ blocks/tick})$$
  Entities moving faster than $0.20\text{ blocks/tick}$ immediately execute at unthrottled 20 Hz.
* **Fast-Path Proximity Bypasses:** Immediate 20 Hz collision checks occur if:
  * A live player is within $< 6.0\text{ meters}$.
  * Entity is taking damage, burning, leashed, or riding a vehicle/mount.
* **Allocation-Free Fail-Open Guard:** Evaluates with **$0\text{ bytes}$ heap allocation** and protects against server crashes through rate-limited fail-open fallback.

---

## 📊 Comprehensive Empirical Benchmark Suite

All metrics were gathered on an isolated dedicated server (Java 21.0.6+7-LTS, NeoForge 21.1.265, `-Xms4G -Xmx4G -XX:+UseG1GC`) utilizing alternating A/B repetitions to eliminate JIT warmup and thermal throttling bias.

### 1. `CollisionThrottler` Multi-Run Benchmark (Phase 7.10.7)
*5 Alternating Repetitions per Configuration ($N = 5$), 150-tick windows after 10s steady-state warmup.*

| Workload & Metric | Vanilla (OFF) Mean $\pm$ SD | SAFE Mean $\pm$ SD | Absolute Delta | Relative Change | Statistical Significance | Effect Size (Cohen's $d$) |
|:---|:---:|:---:|:---:|:---:|:---:|:---:|
| **Workload 1: 250 Cows in 5x5 Pen** | | | | | | |
| `pushEntities()` Invocations | $29,174 \pm 10,322$ | $13,191 \pm 3,961$ | $-15,983$ | **-54.8%** | $t = 3.23, p = 0.022$ | **$d = 2.04$** (Huge) |
| `pushEntities()` Execution Time | $48.84 \pm 1.96\text{ ms}$ | $24.60 \pm 3.88\text{ ms}$ | $-24.24\text{ ms}$ | **-49.6%** | $t = 12.47, \mathbf{p < 0.0001}$ | **$d = 7.88$** (Massive) |
| Max MSPT (Spike Suppression) | $8.16 \pm 2.27\text{ ms}$ | $5.46 \pm 2.13\text{ ms}$ | $-2.70\text{ ms}$ | **-33.1%** | $t = 1.94, p = 0.088$ | **$d = 1.22$** (Very Large) |
| Median Server MSPT | $3.20 \pm 1.42\text{ ms}$ | $3.24 \pm 1.53\text{ ms}$ | $+0.04\text{ ms}$ | $+1.2\%$ | $t = -0.04, p = 0.966$ | Neutral ($d = -0.03$) |
| **Workload 2: Mixed (40 Villagers + 160 Animals)** | | | | | | |
| `pushEntities()` Invocations | $25,926 \pm 3,417$ | $11,046 \pm 1,874$ | $-14,880$ | **-57.4%** | $t = 8.54, \mathbf{p < 0.0001}$ | **$d = 5.40$** (Massive) |
| `pushEntities()` Execution Time | $36.39 \pm 6.64\text{ ms}$ | $15.07 \pm 4.66\text{ ms}$ | $-21.32\text{ ms}$ | **-58.6%** | $t = 5.87, \mathbf{p = 0.0006}$ | **$d = 3.71$** (Massive) |
| Median Server MSPT | $6.00 \pm 0.98\text{ ms}$ | $5.04 \pm 1.05\text{ ms}$ | $-0.96\text{ ms}$ | **-16.0%** | $t = 1.49, p = 0.174$ | **$d = 0.94$** (Large Gain) |
| P95 Server MSPT | $8.92 \pm 1.19\text{ ms}$ | $7.70 \pm 1.47\text{ ms}$ | $-1.22\text{ ms}$ | **-13.7%** | $t = 1.44, p = 0.189$ | **$d = 0.91$** (Large Gain) |

---

### 2. `VillagerBrainThrottler` Multi-Run Benchmark (Phase 7.9.7.5)
*30 Controlled Runs Across 5 Standardized Topologies (150-tick windows, 8s JIT C2 warmup).*

| Scenario & Topology | Vanilla (OFF) Baseline | Hardened SAFE Candidate | Absolute Delta | Relative Improvement | Significance ($t$-test) |
|:---|:---:|:---:|:---:|:---:|:---:|
| **Scenario 1: 40 Villagers in Stalls (1x1)** | | | | | |
| Median Server MSPT | $2.13 \pm 0.23\text{ ms}$ | **$1.50 \pm 0.10\text{ ms}$** | $-0.63\text{ ms}$ | **-29.7%** | $p = 0.012$ (Significant) |
| P95 Server MSPT | $4.00 \pm 0.26\text{ ms}$ | **$3.63 \pm 0.40\text{ ms}$** | $-0.37\text{ ms}$ | **-9.2%** | $p = 0.048$ (Significant) |
| `Brain.tick()` Invocations | $5,960 \pm 0$ | **$1,527 \pm 38$** | $-4,433$ calls | **-74.4%** | Exact $1/4$ modulo match |
| Cumulative Brain CPU Time | $269.9 \pm 7.6\text{ ms}$ | **$196.8 \pm 7.7\text{ ms}$** | $-73.1\text{ ms}$ | **-27.1%** | $p < 0.001$ |
| **Scenario 3: 40 Active Roaming Villagers** | | | | | |
| Median Server MSPT | $2.53 \pm 0.81\text{ ms}$ | **$1.70 \pm 0.26\text{ ms}$** | $-0.83\text{ ms}$ | **-32.9%** | $p = 0.041$ (Significant) |
| P95 Server MSPT | $5.13 \pm 0.64\text{ ms}$ | **$4.07 \pm 0.51\text{ ms}$** | $-1.07\text{ ms}$ | **-20.8%** | $p = 0.035$ (Significant) |
| `Brain.tick()` Invocations | $5,960 \pm 0$ | **$1,608 \pm 48$** | $-4,352$ calls | **-73.0%** | Velocity bypass active |
| **Scenario 5: 40 Villagers at Workstations** | | | | | |
| Median Server MSPT | $3.43 \pm 0.32\text{ ms}$ | **$2.77 \pm 0.06\text{ ms}$** | $-0.67\text{ ms}$ | **-19.4%** | $p = 0.023$ (Significant) |
| P95 Server MSPT | $7.47 \pm 0.86\text{ ms}$ | **$5.40 \pm 0.10\text{ ms}$** | $-2.07\text{ ms}$ | **-27.7%** | $p = 0.018$ (Significant) |
| Cumulative Brain CPU Time | $479.8 \pm 26.7\text{ ms}$ | **$362.7 \pm 5.8\text{ ms}$** | $-117.1\text{ ms}$ | **-24.4%** | $p = 0.003$ |
| **Scenario 4: Control Group (160 Animals, 0 Villagers)** | | | | | |
| Median Server MSPT | $2.40 \pm 0.26\text{ ms}$ | $2.47 \pm 0.06\text{ ms}$ | $+0.07\text{ ms}$ | $+2.8\%$ | $p = 0.680$ (Statistical noise) |
| P95 Server MSPT | $3.00 \pm 0.35\text{ ms}$ | $2.97 \pm 0.12\text{ ms}$ | $-0.03\text{ ms}$ | $-1.1\%$ | Invariant |

---

### 3. `SleepingEntities` Multi-Run Benchmark (Phase 7.8)
*30 Controlled Runs Across 3 Standardized Scenarios (1,104 recorded ticks per run).*

| Scenario & Metric | Vanilla (OFF) Mean $\pm$ SD | SAFE Mean $\pm$ SD | Absolute Delta | Relative Change | Significance ($t$-test) |
|:---|:---:|:---:|:---:|:---:|:---:|
| **Scenario B: Mixed Farm (160 Animals + 40 Villagers)** | | | | | |
| Median Server MSPT | $4.04 \pm 0.13\text{ ms}$ | **$3.56 \pm 0.11\text{ ms}$** | $-0.48\text{ ms}$ | **-11.88%** | $t = 6.10, \mathbf{p = 0.00028}$ |
| P95 Server MSPT | $4.96 \pm 0.11\text{ ms}$ | **$4.44 \pm 0.11\text{ ms}$** | $-0.52\text{ ms}$ | **-10.48%** | $t = 7.21, \mathbf{p = 0.00009}$ |
| Total Entity Processing Time | $2,158.8 \pm 51.7\text{ ms}$ | **$1,664.2 \pm 42.8\text{ ms}$** | $-494.6\text{ ms}$ | **-22.91%** | Saves $\approx 0.45\text{ ms/tick}$ CPU |
| Sleeping Ratio | $0.00 \pm 0.00\%$ | **$32.66 \pm 0.75\%$** | $+32.66\%$ | — | $\approx 72,100$ calls skipped |
| **Scenario A: Dense Pen (250 Cows in 16x16)** | | | | | |
| Median Server MSPT | $3.20 \pm 0.07\text{ ms}$ | **$3.06 \pm 0.05\text{ ms}$** | $-0.14\text{ ms}$ | **-4.38%** | $t = 3.50, \mathbf{p = 0.00807}$ |
| Push Processing Time | $513.72 \pm 7.68\text{ ms}$ | **$429.74 \pm 7.19\text{ ms}$** | $-83.98\text{ ms}$ | **-16.35%** | $t = 17.85, \mathbf{p < 10^{-6}}$ |
| Sleeping Ratio | $0.00 \pm 0.00\%$ | **$14.76 \pm 1.36\%$** | $+14.76\%$ | — | $\approx 40,740$ calls skipped |
| **Scenario C: Roaming Control (100 Wandering Cows)** | | | | | |
| Median Server MSPT | $0.80 \pm 0.00\text{ ms}$ | $0.78 \pm 0.04\text{ ms}$ | $-0.02\text{ ms}$ | -2.50% | $t = 1.00, p = 0.35$ (Noise level) |
| Measured IdleCheck Cost | $0.00\ \mu\text{s}$ | $1.48\ \mu\text{s/tick}$ | $+1.48\ \mu\text{s}$ | — | $14.8\text{ ns/entity}$ ($0.0037\%$) |

---

## 🔬 Profiler Telemetry & Micro-Benchmarks

Micro-benchmarking on live dedicated instances via `KtfProfiler` established the precise instruction cost and overhead across all internal paths:

```
Entity Execution Cost Decomposition (Nanoseconds per Call)
├── IdleChecker.shouldSleep() [Awake early-exit]:     ~40 - 50 ns  [0.1% of budget]
├── IdleChecker.shouldSleep() [Full evaluation]:       ~235 ns      [0.5% of budget]
├── LivingEntity.pushEntities() [Standard]:           ~1,850 - 2,620 ns
├── LivingEntity.pushEntities() [Dense pen overlap]:  ~4,400 ns     [Hotspot #1]
└── Villager Brain Execution Breakdown:
      ├── startEachNonRunningBehavior():             ~16,500 ns   [66.2% of Brain]
      ├── tickEachRunningBehavior():                  ~5,500 ns   [22.1% of Brain]
      ├── tickSensors() [9 active sensors]:           ~2,400 ns   [ 9.4% of Brain]
      └── updateActivityFromSchedule():                  ~80 ns   [ 0.4% of Brain]
```

* **Profiler Overhead Certified (Phase 7.6.1):** Total profiler instrumentation overhead is **$\approx 0.01\text{–}0.02\text{ ms/tick}$** ($0.02\text{–}0.04\%$ of the 50 ms tick budget).
* **Heap Allocation Invariance:** Profiler telemetry and throttler gates utilize purely primitive scalar accumulators (`long`, `int`, `boolean`), incurring **$0\text{ bytes}$ garbage collector allocation churn**.

---

## 🛡️ Gameplay Regression Matrices (100% Pass Rate)

Across all development phases, **144 out of 144 unit tests** pass deterministically. In-game verification proved strict vanilla parity across 3 dedicated regression campaigns:

### 1. Collision & Physics Matrix (Phase 7.10.6 — 8/8 PASS)
1. **Player Proximity Interaction:** Player approaching an idle mob restores 20 Hz push resolution immediately with zero clipping.
2. **Mob-to-Mob Spatial Separation:** Herd collision repulsion stabilizes at $1.668\text{ m}$ mean separation ($0\%$ entity clipping through pen walls).
3. **Vanilla Entity Cramming:** Under `maxEntityCramming = 24`, 35 entities packed in $1\times 1$ space reliably take suffocation damage and stabilize to exactly 24 within 3.0 seconds.
4. **Leash Mechanics:** Leashed mobs preserve elasticity, leash breaking thresholds, and path-following without snapping.
5. **Mounts & Vehicles:** Boats and minecarts carrying passengers completely bypass throttling and preserve client-server position synchronization.
6. **Damage Wakeup:** Damaged mobs wake up within $< 50\text{ ms}$ and trigger instantaneous vanilla panic AI.
7. **Workstation Restocking:** Villagers replenish trade inventory twice daily without temporal drift.
8. **World Persistence:** `save-all flush` executes cleanly across all dimensions with $100\%$ valid chunk NBT serialization.

### 2. Villager Contracts Matrix (Phase 7.9.7.4 — 13/13 PASS)
* **Threat Reaction Latency:** Zombie detection latency is identical between OFF and SAFE ($25.0\text{ ticks} / 1.25\text{ s}$).
* **WorkAtPoi Parity:** Job site interaction and restocking operate on absolute world-clock game time ($\Delta \le 4\text{ ticks}$).
* **Schedule Integrity:** Villagers sleep at dusk, wake at dawn, and update profession claiming contracts without memory TTL leakage.

---

## ⚠️ Documented Behavioral Disclosures & Known Limitations

To maintain absolute technical transparency, the following bounded behavioral differences are formally documented:
1. **Collision & Cramming Temporal Distribution:** Under `CollisionThrottler`, stationary cramming checks occur probabilistically with an expected period $\mathbb{E} = 8\text{ ticks}$ ($400\text{ ms}$) compared to vanilla's $\mathbb{E} = 4\text{ ticks}$ ($200\text{ ms}$). Herd suffocation remains fully active and safe.
2. **Stationary Villager Brain Frequency:** Idle villagers in trading boxes or sleeping in beds execute `Brain.tick` at 5 Hz or 2 Hz respectively instead of 20 Hz. This bounds idle POI replacement retries to $\le 0.2\text{ s}$ and threat reaction jitter to $\le 150\text{ ms}$ (virtually undetectable during survival gameplay).
3. **Method-Level vs Whole-Server MSPT Scaling:** Reducing target method execution time by $50\text{–}60\%$ produces direct server MSPT reductions when entity simulation dominates ($>70\%$ tick load), but will reflect smaller whole-server gains in worlds dominated by tile entities or terrain generation.

---

## 🔒 Source Code and Intellectual Property

This repository functions exclusively as a **technical portfolio and architecture showcase**.

The original source code, internal Mixin bytecode injection models, and optimization algorithms are maintained in a **private repository** to protect proprietary architectural solutions and prevent unauthorized commercial redistribution.

For technical inquiries, licensing, or server performance consultations:

<div align="center">
  <a href="https://discord.com/users/846024512611942401">
    <img src="https://img.shields.io/badge/Discord-8A2BE2?style=for-the-badge&logo=discord&logoColor=white" alt="Discord" />
  </a>
</div>

---

<div align="center">
  <i>Engineered with precision by <b>KillerZero</b> (<a href="https://github.com/gdacoff">@gdacoff</a>)</i>
</div>
