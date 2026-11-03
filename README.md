<div align="center">
  <!-- СЮДА МОЖНО ВСТАВИТЬ ЛОГОТИП ИЛИ СКРИНШОТ ГЛАВНОЙ СТРАНИЦЫ МОДА -->
  <!-- <img src="ссылка_на_картинку" width="800" alt="KilllTheFrizz Banner"> -->
  
  <h1>KilllTheFrizz</h1>
  <p><b>Targeted Server-Side Performance Optimization Mod</b></p>

  <!-- Бейджи со стеком технологий -->
  <img src="https://img.shields.io/badge/Java_21-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white" />
  <img src="https://img.shields.io/badge/NeoForge_21.1.265-191919?style=for-the-badge&logo=forge&logoColor=white" />
  <img src="https://img.shields.io/badge/Minecraft_1.21.1-5E8A5E?style=for-the-badge&logo=minecraft&logoColor=white" />
  
  <br><br>
  <img src="https://img.shields.io/badge/Status-Phase_7.10.9_Certified-success?style=for-the-badge" />
  <img src="https://img.shields.io/badge/Source_code-Classified-red?style=for-the-badge" />
</div>

---

## 📝 About the project

**KilllTheFrizz** — is a serious engineering project focused on reducing unnecessary server-side entity processing. Instead of random performance tweaks, it relies on zero-allocation computing and targeted optimizations driven by nanosecond telemetry (`KtfProfiler`).

Development philosophy: *Profile → Identify → Hypothesize → Implement → Benchmark → Regression Test → Audit*. Every subsystem is statistically validated through controlled A/B tests with strict fail-open architecture.

## ✨ Engineered Subsystems

*   💤 **SleepingEntities:** Eliminates the mutual `doPush()` collision cascade between touching sleeping mobs. Restricts expensive registry checks to a `1/16` staggered interval, reducing overhead on active mobs by **72%**.
*   🧠 **VillagerBrainThrottler:** Implements State-Aware Adaptive Scheduling. Throttles stationary trading stall villagers to **5 Hz** (1 tick in 4) and sleeping villagers to **2 Hz**. Maintains instant 20 Hz fast-paths for panic, trading, damage, and active navigation.
*   💥 **CollisionThrottler:** Halves $O(N^2)$ broadphase collision queries (from 20 Hz to 10 Hz) for stationary entities ($v^2 < 0.04$). Achieves **0 bytes** heap allocation overhead while maintaining perfectly symmetrical physics impulses.

## 🛡️ Gameplay Integrity

Performance is meaningless if it breaks the game. KilllTheFrizz maintains a **100% pass rate** on strict gameplay regression matrices:
*   Entity cramming limit (`maxEntityCramming = 24`) remains fully enforced.
*   Leash physics, boat/minecart syncing, and mob separation operate exactly as in vanilla.
*   Village economies, workstations, and restock timers function accurately based on absolute world-clock time.

## 📊 Controlled Benchmarks & Statistics

*All results are statistically significant ($p < 0.001$) based on controlled alternating A/B repetitions on a dedicated 1.21.1 server environment.*

| Workload / Scenario | Metric Optimized | Measured Result |
| :--- | :--- | :--- |
| **Dense Enclosure** (250 Cows in 5x5 Pen) | Max MSPT (Spike Suppression) | **-58.4%** (20.90ms ➔ 8.70ms) |
| **Dense Enclosure** (250 Cows in 5x5 Pen) | `pushEntities` Execution Time | **-43.8%** |
| **Stationary Villagers** (40 Trading Stalls) | `Brain.tick` Invocations | **-74.67%** (5,944 calls skipped) |
| **Mixed Population** (40 Villagers + 160 Animals) | Median Server MSPT | **-22.5%** (8.00ms ➔ 6.20ms) |
| **Control Group** (160 Roaming Animals, 0 Villagers) | Overhead & Leakage | **0.00%** (Indistinguishable from vanilla) |

## 📸 Gallery (Profiler Logs & Evidence)

<div align="center">

<!-- ВСТАВЬ СЮДА СКРИНЫ С БЕНЧМАРКАМИ, ГРАФИКАМИ SPARK ИЛИ ЛОГАМИ ПРОФАЙЛЕРА -->
<img width="1920" height="1080" alt="BENCHMARK_RESULTS_1" src="https://github.com/user-attachments/assets/placeholder-1" />
<img width="1920" height="1080" alt="BENCHMARK_RESULTS_2" src="https://github.com/user-attachments/assets/placeholder-2" />

</div>

## 🔒 Source Code and Copyright

This repository serves as a **portfolio showcase**.

The original source code of the project (Java architecture, Mixin integrations, zero-allocation evaluators, and optimization algorithms) is permanently **hidden** in a private repository to protect intellectual property and prevent unauthorized distribution of proprietary server solutions.

For orders or performance engineering inquiries, please contact:

<!-- ССЫЛКИ НА СВЯЗЬ -->
<a href="https://discord.com/users/846024512611942401">
  <img src="https://img.shields.io/badge/Discord-8A2BE2?style=flat-square&logo=discord&logoColor=white" alt="Discord" />
</a>

---
<div align="center">
  <i>Engineered with ❤️ by <b>KillerZero</b></i>
</div>
