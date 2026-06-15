# Smart Insights — Design Document

## Overview

Smart Insights surfaces automated, ML/stats-driven observations about a user's spending patterns
beyond what the Analytics tab already shows. Rather than a separate app section, it is layered
**into the existing Analytics screen** so users have one destination for all spending intelligence.

---

## 1. Data Window Setting

### Why it matters
- A brand-new user with 2 weeks of data should not see "your spending is unusually high" —
  there is no baseline to compare against.
- A power user with 2 years of history may want seasonal context that a 3-month window hides.
- The window also gates how expensive the computation is.

### Setting location
**Settings → Analytics** (new sub-section, between "Pay Period" and "Backup"):

```
┌─────────────────────────────────┐
│  Analytics                      │
│  ─────────────────────────────  │
│  Insights data window           │
│  How many months of past data   │
│  are used to generate insights  │
│                                 │
│  ● 1 month                      │
│  ○ 3 months  (default)          │
│  ○ 6 months                     │
│  ○ 12 months                    │
│  ○ All time                     │
└─────────────────────────────────┘
```

### Storage
Add to `UserPreferencesRepository` / DataStore:

```kotlin
// PreferencesKeys
val INSIGHTS_DATA_WINDOW_MONTHS = intPreferencesKey("insights_data_window_months")
// -1 = all time; default = 3
```

### UX guard rails
| Window | Minimum transactions needed to show insights |
|--------|----------------------------------------------|
| 1 month | 5 |
| 3 months | 15 |
| 6 months | 30 |
| 12 months / All time | 50 |

If the user has fewer transactions than the threshold, show an empty state:
> "Not enough data yet — insights will appear as more transactions are detected."

---

## 2. Handling Uncategorized ("Others") Data

### The problem
After a fresh onboarding or import, the majority of transactions carry the generic "Others"
category because the user has not yet assigned categories to merchants. Running category-level
trend analysis on this data produces meaningless results.

### Strategy: tiered degradation + categorization nudge

#### Tier 1 — High uncategorized rate (> 50 % of transactions)

Show a **Categorize First** banner at the top of the Insights section:

```
┌─────────────────────────────────────────────────────┐
│  🏷️  Categorize to unlock insights                  │
│  62 % of your transactions are uncategorized.       │
│  Insights improve significantly once merchants      │
│  are tagged.                                        │
│                              [Quick Categorize →]   │
└─────────────────────────────────────────────────────┘
```

- Still show **total-spend** and **unusual-amount** insights (these work without categories).
- Hide category-level insights (top category, category trend, budget overrun projections).
- "Quick Categorize" navigates to a bulk merchant-categorization flow (top 5 merchants by
  total spend, presented as a swipeable card stack).

#### Tier 2 — Moderate uncategorized rate (20 – 50 %)

Show a compact inline nudge inside category insight cards:

```
┌──────────────────────────────────────────┐
│  🛍️ Food & Dining  ↑ 23 % vs last month │
│  Based on 38 % of your transactions.    │  ← low-confidence label
│  Categorize more for better accuracy.   │
└──────────────────────────────────────────┘
```

#### Tier 3 — Low uncategorized rate (< 20 %)

Show insights normally. "Others" transactions are included in total-spend calculations but
excluded from category-specific trend and comparison cards.

### Categorization coverage metric
Expose a `categorizationCoverage: Float` (0..1) in `InsightsUiState` computed as:

```
coverage = (txns with category != "Others") / total_txns_in_window
```

---

## 3. Where Insights Live (Screen Placement)

### Decision: **Dedicated section inside Analytics tab, expandable to full Insights screen**

Rationale:
- Analytics is already the "intelligence" tab; users already navigate there for data.
- Adding a new bottom nav tab would push the bar to 5 items and dilute each destination's focus.
- A full stand-alone screen accessible via "View all" keeps the Analytics tab uncluttered.

### Layout (Analytics tab)

```
AnalyticsScreen
│
├── Period selector  (existing)
├── Summary tiles   (existing)
│
├── ── Smart Insights ─────────────────────────────────
│   │  [ insight card 1 ]  [ insight card 2 ]  ›       ← horizontal LazyRow, max 3 visible
│   │                              [View all insights] │  ← navigates to InsightsScreen
│   └───────────────────────────────────────────────────
│
├── Category breakdown  (existing)
└── Charts / heatmap    (existing)
```

### Full InsightsScreen (pushed from main shell)

Route: `Constants.Routes.INSIGHTS` (`"insights"`)  
Registered in the **inner nav host** (`MainScreen`), similar to `behavioralStats`.

```
InsightsScreen
│
├── TopAppBar: "Smart Insights"  + data-window chip (e.g. "3 months ▾")
│   (tapping the chip opens a bottom sheet to change the window without going to Settings)
│
├── Categorization coverage banner (Tier 1 / Tier 2 only)
│
├── Insight cards (LazyColumn)
│   ├── Anomaly / unusual spend alert
│   ├── Top growing category this period
│   ├── Largest single-merchant jump
│   ├── Estimated end-of-month spend (based on current pace)
│   ├── Recurring spend vs discretionary ratio
│   └── Savings opportunity (category where spend dropped — positive reinforcement)
│
└── "Last updated: 2 hours ago · Refresh"  footer
```

---

## 4. Computation Frequency

### Two tiers of computation

| Tier | What | When | Where |
|------|------|------|-------|
| **Stats** | Aggregations, ratios, totals, trend %, anomaly z-scores | On-demand when screen opens; cached for the session (in-memory) | `InsightsViewModel` |
| **ML** | Anomaly detection (IQR / z-score on rolling window), pace projection (linear regression on daily spend), merchant clustering | WorkManager job, max once per day; also triggered when ≥ 5 new transactions arrive | `InsightsWorker` |

### Caching contract

Persist computed results in a lightweight Room table:

```kotlin
@Entity(tableName = "insights_cache")
data class InsightsCacheEntity(
    @PrimaryKey val key: String,         // e.g. "anomaly_2026-06"
    val payload: String,                 // JSON blob of the insight result
    val computedAtEpoch: Long,
    val dataWindowMonths: Int,           // invalidate when setting changes
    val transactionCount: Int            // invalidate when new txns arrive
)
```

Invalidation rules:
1. `dataWindowMonths` differs from current setting → recompute.
2. Current transaction count > `transactionCount` at last compute → schedule immediate recompute.
3. `computedAtEpoch` > 24 hours ago → recompute in background, show stale results while waiting.

### WorkManager schedule

```kotlin
// Periodic: once daily, runs only on Wi-Fi / charging (light battery impact)
PeriodicWorkRequestBuilder<InsightsWorker>(1, TimeUnit.DAYS)
    .setConstraints(
        Constraints.Builder()
            .setRequiresBatteryNotLow(true)
            .build()
    )
    .build()

// Reactive: after SMS scan completes and adds ≥ 5 new transactions
// InsightsScanTrigger enqueued as a one-time worker from SmsWorker's success callback
OneTimeWorkRequestBuilder<InsightsWorker>()
    .setInitialDelay(2, TimeUnit.MINUTES)  // debounce rapid SMS bursts
    .build()
```

### On-demand path (fast path)
When the user opens InsightsScreen and the cache is fresh (< 24 h, same txn count, same window):
- Load from Room cache → display immediately (< 50 ms).

When cache is stale:
- Show cached (stale) results with a "Refreshing…" indicator.
- Compute stats tier synchronously in a coroutine on `Dispatchers.Default` (~100–500 ms).
- Enqueue ML tier as a WorkManager one-time job; update UI via `collectAsStateWithLifecycle`.

---

## 5. Insight Card Types

Each card follows the existing `AnalyticsMetricTile` grammar (one primary metric, one secondary row).

| ID | Title | Primary metric | Secondary row | Data needed | Category-safe? |
|----|-------|----------------|---------------|-------------|----------------|
| `ANOMALY` | Unusual spend | Amount (highlighted) | "X% above your average" | Rolling 3-month average per category | No (uses Others bucket) |
| `TOP_GROWER` | Fastest-growing category | Category name | "↑ X% vs last period" | Category totals across 2 periods | Yes |
| `MERCHANT_JUMP` | Merchant spike | Merchant name + amount | "X% more than usual" | Per-merchant rolling average | Yes (merchant-level) |
| `PACE` | Month-end projection | Projected total | "X days remaining" | Daily spend rate (linear) | No |
| `RECURRING_RATIO` | Recurring vs discretionary | Ratio (e.g. 42 % recurring) | Trend vs last month | Subscription-tagged txns | No |
| `SAVINGS_WIN` | Spending dropped | Category name | "↓ X% vs last month — great job!" | Category comparison | Yes |

---

## 6. Architecture Sketch

```
domain/
  usecase/
    ComputeInsightsUseCase.kt       # orchestrates stats + caches results
    DetectSpendAnomalyUseCase.kt    # z-score / IQR on rolling window
    ProjectMonthlySpendUseCase.kt   # linear regression on daily totals
    RankCategoryGrowthUseCase.kt    # delta % across periods

data/
  database/
    InsightsCacheDao.kt
    InsightsCacheEntity.kt
  repository/
    InsightsRepository.kt

ui/screens/insights/
  InsightsScreen.kt
  InsightsViewModel.kt
  InsightCard.kt                    # reuses AnalyticsMetricTile
  CategorizationNudgeBanner.kt

workers/
  InsightsWorker.kt                 # ML-tier WorkManager worker

di/
  InsightsModule.kt
```

---

## 7. Open Questions / Future Work

1. **LLM narrative** — When `AI_CHAT_ENABLED` is turned on, insights can be summarised into
   a natural-language paragraph by the on-device Qwen 2.5 model ("This month you spent 30%
   more on dining, mainly at two merchants…").

2. **Push nudge** — A notification when an anomaly is detected (opt-in, configurable in Settings).

3. **Insight history** — Store past insight snapshots so users can see how their patterns evolved.

4. **Personalised thresholds** — Let the user mark an insight as "not useful" to tune the
   anomaly sensitivity over time (simple feedback loop, no model training needed on-device).

