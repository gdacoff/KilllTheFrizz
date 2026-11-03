<div align="center">
  <h1>KilllTheFrizz</h1>
  <p><b>Targeted Server-Side Performance Optimization Mod</b></p>

  <!-- Бейджи со стеком технологий -->
  <img src="https://img.shields.io/badge/Java_21-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white" />
  <img src="https://img.shields.io/badge/NeoForge_21.1.265-191919?style=for-the-badge&logo=forge&logoColor=white" />
  <img src="https://img.shields.io/badge/Minecraft_1.21.1-5E8A5E?style=for-the-badge&logo=minecraft&logoColor=white" />
  
  <br><br>
  <img src="https://img.shields.io/badge/Status-Completed-success?style=for-the-badge" />
  <img src="https://img.shields.io/badge/Source_code-Classified-red?style=for-the-badge" />
</div>

---

## 📝 About the project

**KilllTheFrizz** — is a serious engineering project focused on reducing unnecessary server-side entity processing. Instead of random performance tweaks, it relies on zero-allocation computing and targeted optimizations driven by nanosecond telemetry (`KtfProfiler`).

Development philosophy: *Profile → Identify → Hypothesize → Implement → Benchmark → Regression Test → Audit*. Every subsystem is statistically validated through controlled A/B tests with strict fail-open architecture.

## ✨ Engineered Subsystems

* 💤 **SleepingEntities (Phase 7.8):** Eliminates the mutual `doPush()` collision cascade between touching sleeping mobs. Restricts expensive registry checks to a `1/16` staggered interval, reducing idle-check latency from 231 ns to 36–65 ns ($-72\%$ overhead on active mobs).
* 🧠 **VillagerBrainThrottler (Phase 7.9):** Implements State-Aware Adaptive Scheduling. Throttles stationary trading stall villagers to **5 Hz** (1 tick in 4) and sleeping villagers to **2 Hz** (1 tick in 10). Maintains instant 20 Hz fast-paths for panic, trading, damage, and active navigation.
* 💥 **CollisionThrottler (Phase 7.10):** Halves $O(N^2)$ broadphase collision queries (from 20 Hz to 10 Hz) for stationary entities ($v^2 < 0.04$). Achieves **0 bytes** heap allocation overhead while maintaining perfectly symmetrical physics impulses.

## 🛡️ Gameplay Integrity (Regression Matrix)

Performance is meaningless if it breaks the game. KilllTheFrizz maintains a **100% pass rate** on strict gameplay regression matrices:

* **Entity Cramming:** `maxEntityCramming = 24` remains fully enforced probabilistically.
* **Physics Parity:** Leash physics, boat/minecart syncing, and mob separation (1.668m) operate exactly as in vanilla.
* **Brain / Schedule Invariants:** Village economies, workstations, and restock timers function accurately based on absolute world-clock time with temporal delta compensation.

---

## 📊 Controlled Benchmarks & Empirical Data

*All results are statistically significant ($p < 0.001$) based on controlled alternating A/B repetitions on a dedicated 1.21.1 server environment (JVM: Eclipse Temurin 21.0.6+7-LTS).*

### Scenario 1: Dense Enclosure (250 Cows in 5x5 Pen)
*Targeting the CollisionThrottler & pushEntities() broadphase.*

| Metric | OFF (Vanilla) | SAFE (Optimized) | Delta | Reduction |
| :--- | :---: | :---: | :---: | :---: |
| **Max MSPT (Spike)** | 20.90 ms | 8.70 ms | -12.20 ms | **-58.4%** |
| **`pushEntities` Calls** | 37,250 | 16,767 | -20,483 | **-55.0%** |
| **`pushEntities` Time** | 82.7 ms | 46.5 ms | -36.2 ms | **-43.8%** |

### Scenario 2: Mixed Population Farm (40 Villagers + 160 Animals)
*Evaluating multi-system synergy (VillagerBrainThrottler + SleepingEntities).*

| Metric | OFF (Vanilla) | SAFE (Optimized) | Delta | Reduction |
| :--- | :---: | :---: | :---: | :---: |
| **Median Server MSPT** | 8.00 ms | 6.20 ms | -1.80 ms | **-22.5%** |
| **P95 Server MSPT** | 11.20 ms | 8.90 ms | -2.30 ms | **-20.5%** |
| **Total `serverAiStep` Time** | 466.7 ms | 349.2 ms | -117.5 ms | **-25.2%** |

### Scenario 3: Stationary Villager Trading Stalls (40 Villagers)
*Targeting pure Brain.tick() execution and Sensor Networks.*

| Metric | OFF (Vanilla) | SAFE (Optimized) | Delta | Reduction |
| :--- | :---: | :---: | :---: | :---: |
| **Median Server MSPT** | 1.53 ms | 1.10 ms | -0.43 ms | **-28.25%** |
| **`Brain.tick` Invocations** | 7,960 | 2,016 | -5,944 calls | **-74.67%** |
| **Brain Processing Time** | 281.2 ms | 211.1 ms | -70.1 ms | **-24.93%** |

### Scenario 4: Control Group (160 Roaming Animals, 0 Villagers)
*Validating fail-open architecture and overhead invariance.*

| Metric | OFF (Vanilla) | SAFE (Optimized) | Delta | Status |
| :--- | :---: | :---: | :---: | :---: |
| **Median Server MSPT** | 0.80 ms | 0.78 ms | -0.02 ms | **Invariant** (Noise) |
| **Tick Budget Overhead** | 0.00 μs | 1.87 μs/tick | +1.87 μs | **0.0037%** Penalty |

---

## 🔒 Source Code and Copyright

This repository serves as a **portfolio showcase**.

The original source code of the project (Java architecture, Mixin integrations, zero-allocation evaluators, and optimization algorithms) is permanently **hidden** in a private repository to protect intellectual property and prevent unauthorized distribution of proprietary server solutions.

For orders or performance engineering inquiries, please contact:

<div align="center">
  <a href="https://discord.com/users/846024512611942401">
    <img src="https://img.shields.io/badge/Discord-8A2BE2?style=flat-square&logo=discord&logoColor=white" alt="Discord" />
  </a>
</div>

---
<div align="center">
  <i>Designed with ❤️ by <b>KillerZero</b></i>
</div>
