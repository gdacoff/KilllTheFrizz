# Фаза 7.10.1 — Необработанные данные захвата профилировщика

**Проект:** KilllTheFrizz  
**Minecraft:** 1.21.1 | **NeoForge:** 21.1.265 | **Java:** 21  
**Исходные данные:** `results/phase_7_10_1_baseline.json`  
**Инструментарий:** Spark Profiler + KtfProfiler (высокоточные наносекундные таймеры JVM)  

---

## 1. Необработанные захваты рабочих нагрузок

```json
{
  "WL1_Dense_250_Cows": {
    "spark": {
      "mspt_median": 3.10,
      "mspt_p95": 4.70,
      "mspt_max": 16.60,
      "cpu_process_pct": 0.0
    },
    "ktf": {
      "recorded_ticks": 150,
      "server_ai_calls": 37241,
      "server_ai_total_nanos": 41852100,
      "server_ai_avg_nanos": 1123,
      "push_entities_calls": 37241,
      "push_entities_total_nanos": 97712300,
      "push_entities_avg_nanos": 2623,
      "push_entities_max_nanos": 312500,
      "villager_brain_calls": 0,
      "villager_brain_total_nanos": 0,
      "villager_brain_avg_nanos": 0
    }
  },
  "WL2_Mixed_200": {
    "spark": {
      "mspt_median": 6.50,
      "mspt_p95": 9.70,
      "mspt_max": 13.60,
      "cpu_process_pct": 0.0
    },
    "ktf": {
      "recorded_ticks": 150,
      "server_ai_calls": 27752,
      "server_ai_total_nanos": 460124300,
      "server_ai_avg_nanos": 16578,
      "push_entities_calls": 27752,
      "push_entities_total_nanos": 46912800,
      "push_entities_avg_nanos": 1690,
      "push_entities_max_nanos": 215400,
      "villager_brain_calls": 1704,
      "villager_brain_total_nanos": 424215800,
      "villager_brain_avg_nanos": 248923
    }
  },
  "WL3_Roaming_100_Cows": {
    "spark": {
      "mspt_median": 2.20,
      "mspt_p95": 2.90,
      "mspt_max": 16.70,
      "cpu_process_pct": 0.0
    },
    "ktf": {
      "recorded_ticks": 150,
      "server_ai_calls": 14617,
      "server_ai_total_nanos": 16843200,
      "server_ai_avg_nanos": 1152,
      "push_entities_calls": 14617,
      "push_entities_total_nanos": 17834500,
      "push_entities_avg_nanos": 1220,
      "push_entities_max_nanos": 184500,
      "villager_brain_calls": 0,
      "villager_brain_total_nanos": 0,
      "villager_brain_avg_nanos": 0
    }
  },
  "WL4_Trading_Hall_40": {
    "spark": {
      "mspt_median": 2.70,
      "mspt_p95": 4.60,
      "mspt_max": 9.40,
      "cpu_process_pct": 0.0
    },
    "ktf": {
      "recorded_ticks": 150,
      "server_ai_calls": 5960,
      "server_ai_total_nanos": 196123400,
      "server_ai_avg_nanos": 32908,
      "push_entities_calls": 5960,
      "push_entities_total_nanos": 4683400,
      "push_entities_avg_nanos": 785,
      "push_entities_max_nanos": 65400,
      "villager_brain_calls": 1490,
      "villager_brain_total_nanos": 187461200,
      "villager_brain_avg_nanos": 125813
    }
  },
  "WL5_Village_Workload_40": {
    "spark": {
      "mspt_median": 2.90,
      "mspt_p95": 6.70,
      "mspt_max": 16.10,
      "cpu_process_pct": 0.0
    },
    "ktf": {
      "recorded_ticks": 150,
      "server_ai_calls": 5960,
      "server_ai_total_nanos": 393452100,
      "server_ai_avg_nanos": 66014,
      "push_entities_calls": 5960,
      "push_entities_total_nanos": 2124500,
      "push_entities_avg_nanos": 356,
      "push_entities_max_nanos": 54200,
      "villager_brain_calls": 1515,
      "villager_brain_total_nanos": 383635800,
      "villager_brain_avg_nanos": 253225
    }
  },
  "WL6_Collision_Heavy_100": {
    "spark": {
      "mspt_median": 1.30,
      "mspt_p95": 1.60,
      "mspt_max": 7.90,
      "cpu_process_pct": 0.0
    },
    "ktf": {
      "recorded_ticks": 150,
      "server_ai_calls": 14887,
      "server_ai_total_nanos": 17412300,
      "server_ai_avg_nanos": 1169,
      "push_entities_calls": 14887,
      "push_entities_total_nanos": 17265400,
      "push_entities_avg_nanos": 1159,
      "push_entities_max_nanos": 194300,
      "villager_brain_calls": 0,
      "villager_brain_total_nanos": 0,
      "villager_brain_avg_nanos": 0
    }
  }
}
```