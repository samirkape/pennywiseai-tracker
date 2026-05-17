---
name: Home budget buckets + quick actions (minimal)
overview: Restore Home quick actions and add spend-vs-budget buckets using existing budgetSummary; remove the hero sparkline for a cleaner minimal layout; keep density and visual noise low.
todos:
  - id: hero-no-graph
    content: Remove CompactSparkline block from HeroSpendCard in BalanceCard.kt; drop unused imports if CompactSparkline becomes home-only elsewhere
    status: completed
  - id: quick-actions
    content: Restore slim quick action row (Search, Add, Budgets, Analytics) on Home—icon-forward, minimal labels or single row tonal chips
    status: completed
  - id: budget-buckets
    content: Add minimal HomeBudgetBucketsSection (LazyRow, soft cards/progress only) after StatPillRow; LIMIT categories; empty CTA to Budgets
    status: completed
  - id: polish-minimal
    content: Tune HomeScreen spacing (stagger, LazyColumn padding) so hero + pills + buckets + feed read as one calm column; verify light/dark
    status: completed
isProject: false
---

# Home: budget buckets, quick actions, minimal hero

## Goals

1. **Quick actions** — Restore one-tap **Search**, **Add transaction**, **Budgets**, **Analytics** on Home (removed in fintech pass); keep FAB for sync + long-press menu as today.
2. **Budget buckets** — Show **spend vs budget** for bucket categories using existing **`HomeUiState.budgetSummary`** (no ViewModel/data pipeline changes unless we add sorting prefs later).
3. **Hero tile** — **Remove the graph** (delete the `CompactSparkline` usage inside [`HeroSpendCard`](app/src/main/java/com/pennywiseai/tracker/ui/components/cards/BalanceCard.kt)); hero = period chip + amount + delta pill only.
4. **Minimal screen** — Overall layout stays **calm and sparse**: limited decoration, consistent `surfaceContainer` hierarchy, no redundant charts at top.

## 1. Hero: remove graph

- In [`BalanceCard.kt`](app/src/main/java/com/pennywiseai/tracker/ui/components/cards/BalanceCard.kt) (`HeroSpendCard`), remove the block that renders **`CompactSparkline`** when `spendingHistory.size >= 2`.
- Remove related vertical spacers tied only to the chart.
- If **`CompactSparkline.kt`** is unused after removal, delete the file or keep it only if another screen imports it (grep before delete).

## 2. Quick actions (minimal)

- Add a **single compact row** after the hero (before **StatPillRow**), wired to existing `HomeScreen` lambdas:
  - Search → `onNavigateToTransactionsWithSearch`
  - Add → `onNavigateToAddScreen`
  - Budgets → `onNavigateToBudgets`
  - Analytics → `onNavigateToTransactions`
- **Minimal styling:** prefer **small tonal icon buttons** or a **single `Surface` row** with evenly spaced actions, **short or no text labels** (contentDescription for a11y), no heavy cards—avoid repeating the old 52dp tall strip if it feels busy; target **~40–44dp** touch height.
- Optional: use **one** `PennyWiseCardV2` wrapping the row with `contentPadding` small, or **no** wrapper—whichever reads cleaner against `background`.

## 3. Budget buckets (minimal)

- **Data (UI-only):** From `budgetSummary`, use **`BudgetGroupType.LIMIT`** groups only; flatten **`categorySpending`** with optional **group name** subtitle when disambiguating.
- Sort by **`percentageUsed`** descending (over-budget surfaces first); cap **6–8** items + trailing **“All”** / **“Budgets”** text chip → `onNavigateToBudgets`.
- **UI:** New composable e.g. [`HomeBudgetBucketsSection.kt`](app/src/main/java/com/pennywiseai/tracker/ui/components/cards/HomeBudgetBucketsSection.kt):
  - **`LazyRow`** with narrow cells (**~140–160dp**): category title, **thin `LinearProgressIndicator`**, one line **spent / budget** using `CurrencyFormatter` + `summary.currency` (or `selectedCurrency` if aligned with summary).
  - **Colors:** `MaterialTheme` only; over-cap use `error` / `errorContainer` sparingly (text or bar only, not both heavy).
- **Empty:** One line + tap to open budgets—no large illustration.

## 4. Layout order and polish

Suggested **`LazyColumn`** order:

1. Hero (no graph)
2. Quick actions (minimal row)
3. Stat pills
4. Budget buckets section
5. Date scrubber + feed (unchanged below)

- Revisit **stagger delays** so the new row does not feel like a seventh heavy block—keep **4 steps** max (e.g. 0 / 40 / 80 / 120ms) by merging bucket section with pills or feed entrance if needed.
- **`contentPadding.bottom`:** Confirm FAB + bottom nav still clear after new rows; bump by **~8–16dp** only if scroll clips.

## Files (expected)

| File | Change |
|------|--------|
| [`BalanceCard.kt`](app/src/main/java/com/pennywiseai/tracker/ui/components/cards/BalanceCard.kt) | Remove hero `CompactSparkline` |
| [`HomeScreen.kt`](app/src/main/java/com/pennywiseai/tracker/presentation/home/HomeScreen.kt) | Quick actions item; buckets item; spacing |
| New `HomeBudgetBucketsSection.kt` | LazyRow + minimal cells + empty state |

## Out of scope

- Analytics-level charts on Home (keeps hero minimal).
- ViewModel signature changes / new repository calls.

## Acceptance

- Hero shows **no** line chart; amount + delta remain clear.
- **Four** quick actions visible without opening FAB menu.
- Budget buckets visible when data exists; **empty CTA** when not.
- Screen reads **minimal**: no duplicate charts, no crowded hero.
