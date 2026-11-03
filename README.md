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
  <img src="https://img.shields.io/badge/Status-Completed-success?style=for-the-badge" />
  <img src="https://img.shields.io/badge/Source_code-Classified-red?style=for-the-badge" />
</div>

---

## 📝 About the project

**KilllTheFrizz** — is a serious engineering project focused on reducing unnecessary server-side entity processing. Instead of random performance tweaks, it relies on targeted optimizations driven by precise measurements.

Development philosophy: *Profile → Identify → Hypothesize → Implement → Benchmark → Regression Test → Audit*. Every change is strictly validated through controlled tests.

## ✨ Key Features

*   💤 **SleepingEntities:** Reduces unnecessary AI processing for inactive mobs. Uses a staggered `IdleChecker` to skip expensive operations without breaking behavior.
*   🧠 **VillagerBrainThrottler:** Targets expensive villager Brain processing (sensors, memories). Includes temporal compensation to keep workstations and restocking functional.
*   💥 **CollisionThrottler:** Reduces entity collision processing (`pushEntities()`) from 20 Hz to 10 Hz for eligible idle entities, while maintaining fast-paths for players, damage, and fire.

## 📊 Performance & Benchmarks

*Note: Reductions in target-method calls do not guarantee identical whole-server MSPT improvements in all scenarios. Actual results depend on the workload.*

| Module / Workload | Metric | Measured Result |
| :--- | :--- | :--- |
| **SleepingEntities** (Mixed 200 entities) | Median MSPT | **-11.88%** |
| **VillagerBrainThrottler** (Stationary) | Brain processing time | **-24.9%** |
| **CollisionThrottler** (40 villagers + 160 animals) | `pushEntities()` execution time | **-58.6%** |

## 📸 Gallery (Benchmarks & Evidence)

<div align="center">

<!-- ВСТАВЬ СЮДА СКРИНЫ С БЕНЧМАРКАМИ, ГРАФИКАМИ ИЛИ ЛОГАМИ ПРОФАЙЛЕРА -->
<img width="1920" height="1080" alt="BENCHMARK_RESULTS_1" src="https://github.com/user-attachments/assets/placeholder-1" />
<img width="1920" height="1080" alt="BENCHMARK_RESULTS_2" src="https://github.com/user-attachments/assets/placeholder-2" />

</div>

## 🔒 Source Code and Copyright

This repository serves as a **portfolio showcase**.

The original source code of the project (Java architecture, Mixin integrations, and optimization algorithms) is permanently **hidden** in a private repository to protect intellectual property and prevent unauthorized distribution of proprietary server solutions.

For orders, please contact:

<!-- ССЫЛКИ НА СВЯЗЬ (скопированы из твоего профиля) -->
<a href="https://discord.com/users/846024512611942401">
  <img src="https://img.shields.io/badge/Discord-8A2BE2?style=flat-square&logo=discord&logoColor=white" alt="Discord" />
</a>

---
<div align="center">
  <i>Designed with ❤️ by <b>KillerZero</b></i>
</div>
