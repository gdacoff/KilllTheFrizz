 <div align="center">

  <!-- PROJECT LOGO / BANNER -->
  <!-- <img src="IMAGE_URL" width="800" alt="KilllTheFrizz Banner"> -->

  <h1>KilllTheFrizz</h1>
  <p><b>Advanced Entity Processing & Performance Optimization for Minecraft</b></p>

  <img src="https://img.shields.io/badge/Java-21-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white" />
  <img src="https://img.shields.io/badge/NeoForge-21.1.265-191919?style=for-the-badge&logo=curseforge&logoColor=white" />
  <img src="https://img.shields.io/badge/Minecraft-1.21.1-5E8A5E?style=for-the-badge&logo=minecraft&logoColor=white" />

  <br><br>

  <img src="https://img.shields.io/badge/Status-In_Development-orange?style=for-the-badge" />
  <img src="https://img.shields.io/badge/Focus-Performance-blue?style=for-the-badge" />
  <img src="https://img.shields.io/badge/Side-Server--Side-purple?style=for-the-badge" />

</div>

---

## 📝 About the project

**KilllTheFrizz** is a performance optimization project for Minecraft 1.21.1, built around a specialized approach to entity processing.

Instead of relying exclusively on broad, generic optimizations, the project investigates specific sources of server-side overhead and introduces targeted optimizations to reduce unnecessary entity processing.

Its development follows a measurement-driven engineering approach:

**Profiling → Bottleneck Analysis → Implementation → Benchmarking → Regression Testing → Integrity Audit.**

Every optimization is evaluated against its actual impact, with particular attention to gameplay behavior, compatibility, and the correctness of the underlying implementation.

The goal is not simply to make performance numbers look better, but to reduce unnecessary server workload while preserving essential game mechanics.

## ✨ Key Features

* 💤 **SleepingEntities:** Prevents eligible inactive mobs from executing unnecessary AI processing. Uses staggered eligibility checks and wake-up conditions to avoid constantly evaluating every entity.

* 🧠 **VillagerBrainThrottler:** Reduces unnecessary villager Brain processing under suitable conditions. Includes temporal compensation for sensor and memory timing to minimize gameplay side effects.

* ⚡ **CollisionThrottler:** Reduces the frequency of collision processing for eligible entities. Uses conditional fast paths to preserve more frequent processing when player proximity, movement, damage, or other gameplay conditions require it.

* 📊 **Performance-Driven Development:** Every major optimization is accompanied by controlled benchmarks, repeated measurements, and analysis of both target-method costs and whole-server performance.

* 🧪 **Gameplay Regression Testing:** Optimization is not considered successful solely because it reduces CPU work. Entity interactions, collision behavior, villager functionality, and world persistence are also tested.

* 🔍 **Implementation Auditing:** Dedicated integrity audits verify implementation details, configuration behavior, benchmark consistency, and known limitations.

## 📈 Performance & Benchmarks

KilllTheFrizz uses controlled OFF/SAFE benchmark comparisons to evaluate the impact of individual optimization systems.

### CollisionThrottler — Phase 7.10

| Metric | 250 Cows | Mixed Workload |
|---|---:|---:|
| `pushEntities()` calls | −54.8% | −57.4% |
| `pushEntities()` execution time | −49.6% | −58.6% |
| Median MSPT | +1.2% | −16.0% |
| P95 MSPT | −5.7% | −13.7% |

<sub>Five repetitions per mode and scenario. MSPT differences were not statistically significant in this sample. Target-method reductions were statistically significant.</sub>

### VillagerBrainThrottler — Phase 7.9

* **Stationary villagers:** Brain calls reduced by approximately 74.7%.
* **Brain execution time:** Reduced by approximately 24.9% in the stationary-villager workload.
* **Mixed workload:** Brain processing time reduced by approximately 31%.

<sub>Results are workload-specific and should not be interpreted as guaranteed whole-server performance improvements.</sub>

### SleepingEntities — Phase 7.8

| Workload | Median MSPT | P95 MSPT |
|---|---:|---:|
| 250 Cows | −4.38% | −3.13% |
| Mixed 200 Entities | −11.88% | −10.48% |

<sub>Results from controlled benchmark scenarios. Performance gains depend on entity composition and workload.</sub>

For methodology, raw measurements, statistical analysis, and known limitations, see the benchmark documentation.

## 📸 Gallery

<div align="center">

<!-- Add screenshots, profiler captures, benchmark visualizations, and in-game footage here. -->

</div>

## 🏗️ Development Philosophy

KilllTheFrizz is developed with an emphasis on measurable engineering rather than speculative optimization.

The project follows several principles:

* **Evidence over assumptions:** Identify actual bottlenecks before changing code.
* **Targeted optimization:** Optimize specific expensive operations instead of introducing unnecessary global changes.
* **Gameplay correctness:** Reduced processing must not silently break important game mechanics.
* **Reproducible benchmarks:** Compare controlled workloads and preserve raw results.
* **Transparent limitations:** Document behavioral differences, measurement uncertainty, and compatibility concerns.
* **Continuous auditing:** Review implementation correctness and benchmark integrity after major development phases.

## ⚠️ Known Limitations

Some optimizations intentionally change the frequency of internal processing.

For example, CollisionThrottler reduces collision processing frequency for eligible entities. This can change the timing of collision interactions and entity cramming behavior compared with unmodified vanilla Minecraft.

Benchmark improvements in individual methods do not automatically translate into equivalent whole-server MSPT improvements.

See `docs/KNOWN_LIMITATIONS.md` for detailed information.

## 📂 Documentation

| Document | Description |
|---|---|
| `docs/ARCHITECTURE.md` | Internal architecture and optimization systems |
| `docs/BENCHMARKS.md` | Benchmark methodology and measured results |
| `docs/KNOWN_LIMITATIONS.md` | Known behavioral differences and limitations |
| `docs/PERFORMANCE_ROADMAP.md` | Planned optimization work |
| `CHANGELOG.md` | Development history and notable changes |

## 🔒 Source Code and License

KilllTheFrizz is an independent Minecraft performance optimization project.

The repository contains the project's development history, implementation, technical documentation, and benchmark evidence.

**License:** Not yet specified.

Until a license is selected and added, no additional permissions to redistribute, modify, or reuse the source code should be assumed.

## 👨‍💻 Author

<div align="center">

  **Designed and developed by KillerZero**

  <a href="https://github.com/gdacoff">
    <img src="https://img.shields.io/badge/GitHub-gdacoff-181717?style=for-the-badge&logo=github&logoColor=white" />
  </a>

  <br><br>

  <i>Measure first. Optimize second. Verify everything.</i>

</div>
