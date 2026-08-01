# Implementation Prompt v2: Spendly Home Screen (Kotlin / Jetpack Compose)

> **What changed from v1:**
> 1. Full reconciliation with `design.md` (Spendly Design System) — every conflict is resolved below
> 2. New **Smart Insights** section added as screen section 5 (pushes Subscriptions/Goal and Transactions down)
> 3. Data model extended for insights; all derivations remain on the ViewModel

---

## Part 0 — Design Conflict Analysis & Resolutions

Before building, here is a complete audit of everywhere the original prompt and `design.md` contradict each other, with the resolution that wins for each.

| # | Area | Original Prompt | design.md | Conflict | Resolution |
|---|------|-----------------|-----------|----------|------------|
| 1 | **Color system** | Custom `LocalSpendTrackerColors` CompositionLocal with earthy tokens (Green, Amber, Red, Purple…) — no M3 mapping | Material 3 dynamic color, `primary = 0xFF6750A4` (purple), full `lightColorScheme` / `darkColorScheme` | Completely different palettes; image clearly shows the earthy palette | **Use the earthy palette.** Map it into M3 color roles (see §Color Bridging below) so M3 components pick up correct colors automatically. Keep `LocalSpendTrackerColors` for semantic extensions that have no M3 equivalent |
| 2 | **App name** | Unnamed / "SpendTracker" references | "Spendly" throughout | Theme class name inconsistency | **Use `SpendlyTheme` everywhere.** All composables, previews, and theme files should say "Spendly" |
| 3 | **Typography** | DM Sans (400/500) + Playfair Display SemiBold | `FontFamily.Default` (system font) for all type styles | design.md doesn't declare any custom fonts | **Keep DM Sans + Playfair Display** — they are visible and intentional in the image. Update `Type.kt` to wire them into M3's `Typography` object using the roles below, rather than a parallel system |
| 4 | **Type scale mapping** | Custom ad-hoc sizes (40sp balance, 28sp week, 22sp subscriptions…) | Declares a full M3 type scale (displayLarge=57sp, headlineLarge=32sp, etc.) but leaves sizes generic | Sizes in prompt don't map to any M3 role | **Adopt M3 role names** in composables (`MaterialTheme.typography.displayMedium` for 40sp balance, etc.). Define the sizes once in `Type.kt`. This way font scaling and theming work correctly |
| 5 | **Vertical spacing** | `Arrangement.spacedBy(14.dp)` between LazyColumn items | 8dp grid: xs=4, sm=8, md=16, lg=24, xl=32, xxl=48 | 14dp is off-grid | **Change to 16.dp** (`Spacing.md`). Also change any 18dp paddings to 16dp |
| 6 | **Card corner radius** | 14dp explicit | `Shapes.large = RoundedCornerShape(16.dp)` | 14dp is off-grid and conflicts with the shape system | **Use `MaterialTheme.shapes.large` (16dp)** for main content cards. `Shapes.medium` (12dp) for inner containers |
| 7 | **Transaction icon container** | "9dp corner radius" | `Shapes.small = RoundedCornerShape(8.dp)` | 9dp is off-grid | **Use `MaterialTheme.shapes.small` (8dp)** |
| 8 | **Status pill corner** | "fully rounded corners (20dp+)" | `Shapes.extraLarge = RoundedCornerShape(28.dp)` | Minor only | **Use `CircleShape`** (semantically correct for a pill, design.md's extraLarge) |
| 9 | **Bottom navigation** | Custom `Row` + `Box` with raised FAB center; no tablet support | `NavigationBar` for compact, `NavigationRail` for medium/expanded; separate FAB | design.md requires adaptive nav; custom FAB centre not in design.md | **Keep the custom Row for the raised-centre-FAB** (it's a signature element visible in the image and genuinely better UX). **Add `NavigationRail` path** per design.md's `AdaptiveNavigation` pattern for medium/expanded windows |
| 10 | **Color semantic extensions** | Not using M3 extension pattern | Shows `ColorScheme.success`, `.warning`, `.income`, `.expense` extensions | Missed pattern that design.md explicitly defines | **Adopt the extension pattern**. Add `ColorScheme.spendGreen`, `.spendAmber`, `.spendRed`, `.spendPurple` as `@Composable` getters that return the correct light/dark token |
| 11 | **Dynamic color** | Not mentioned | Supported on Android 12+ via `dynamicDarkColorScheme` / `dynamicLightColorScheme` | Missing feature | **Support dynamic color** as the fallback for users who haven't set a wallpaper or are on Android <12. When dynamic color is active, `LocalSpendTrackerColors` still supplies the semantic greens/reds so chart bars always look intentional |
| 12 | **Card borders** | 0.5dp `CardBorder` stroke | Standard Material card, no explicit border | design.md doesn't mention borders | **Keep the 0.5dp border** — it's clearly visible in the image and is a deliberate texture decision. Represent it via `LocalSpendTrackerColors.cardBorder` |
| 13 | **Empty states** | Mentions goal card empty state, no pattern | Defines `EmptyTransactions()` pattern: centered icon + title + body + action | No shared pattern | **Use design.md's empty state pattern** for both the Goal card empty state and any future empty list |
| 14 | **Progress bar clip** | `clip(RoundedCornerShape(3.dp))` — off-grid | Shape system starts at 4dp (extraSmall) | 3dp is off-grid | **Use `MaterialTheme.shapes.extraSmall` (4dp)** for the progress bar clip |

---

## Part 1 — Reconciled Design Tokens

### Color Bridging Strategy

Map the earthy palette into M3 roles so all Material components inherit correct colors without any hardcoded hex inside composables. Define in `Color.kt` and `Theme.kt`.

```kotlin
// Color.kt — raw tokens (unchanged from v1)
// Light
val BackgroundPrimary    = Color(0xFFF7F6F3)
val CardBackground       = Color(0xFFFFFFFF)
val CardBorder           = Color(0xFFE2DED7)
val HeaderBackground     = Color(0xFFEEEAE3)
val TextMuted            = Color(0xFF8C8B85)
val TextInk              = Color(0xFF1A1A18)
val TextInkSecondary     = Color(0xFF4A4A46)
val Divider              = Color(0xFFEDE9E2)
val Green                = Color(0xFF4A7C6F)
val GreenBg              = Color(0xFFE6F5F0)
val Amber                = Color(0xFFC97E28)
val AmberBg              = Color(0xFFFDF3E7)
val Red                  = Color(0xFFB03030)
val RedBg                = Color(0xFFFCEBEB)
val Purple               = Color(0xFF6059A8)
val PurpleBg             = Color(0xFFEEEDFE)

// Dark equivalents (same names + Dark suffix) ...

// Theme.kt — M3 color scheme using the earthy palette
val LightColorScheme = lightColorScheme(
    primary            = Green,           // main actions, FAB, active nav
    onPrimary          = Color.White,
    primaryContainer   = GreenBg,         // selected states, highlights
    onPrimaryContainer = TextInk,
    secondary          = Amber,           // subscriptions accent
    onSecondary        = Color.White,
    secondaryContainer = AmberBg,
    onSecondaryContainer = TextInk,
    tertiary           = Purple,          // smart insights accent
    onTertiary         = Color.White,
    tertiaryContainer  = PurpleBg,
    onTertiaryContainer = TextInk,
    background         = BackgroundPrimary,
    onBackground       = TextInk,
    surface            = CardBackground,
    onSurface          = TextInk,
    surfaceVariant     = HeaderBackground,
    onSurfaceVariant   = TextInkSecondary,
    outline            = CardBorder,
    outlineVariant     = Divider,
    error              = Red,
    onError            = Color.White,
    errorContainer     = RedBg,
    onErrorContainer   = TextInk,
    scrim              = Color.Black,
)
// DarkColorScheme mirrors with *Dark tokens
```

#### ColorScheme Semantic Extensions (design.md pattern, extended)

```kotlin
// Extensions.kt
val ColorScheme.spendGreen: Color
    @Composable get() = if (isSystemInDarkTheme()) GreenDark else Green

val ColorScheme.spendGreenBg: Color
    @Composable get() = if (isSystemInDarkTheme()) GreenBgDark else GreenBg

val ColorScheme.spendAmber: Color
    @Composable get() = if (isSystemInDarkTheme()) AmberDark else Amber

val ColorScheme.spendAmberBg: Color
    @Composable get() = if (isSystemInDarkTheme()) AmberBgDark else AmberBg

val ColorScheme.spendRed: Color
    @Composable get() = if (isSystemInDarkTheme()) RedDark else Red

val ColorScheme.spendRedBg: Color
    @Composable get() = if (isSystemInDarkTheme()) RedBgDark else RedBg

val ColorScheme.spendPurple: Color
    @Composable get() = if (isSystemInDarkTheme()) PurpleDark else Purple

val ColorScheme.spendPurpleBg: Color
    @Composable get() = if (isSystemInDarkTheme()) PurpleBgDark else PurpleBg

val ColorScheme.textMuted: Color
    @Composable get() = if (isSystemInDarkTheme()) TextMutedDark else TextMuted

val ColorScheme.cardBorder: Color
    @Composable get() = if (isSystemInDarkTheme()) CardBorderDark else CardBorder

// Legacy aliases from design.md
val ColorScheme.income: Color  @Composable get() = spendGreen
val ColorScheme.expense: Color @Composable get() = spendRed
val ColorScheme.success: Color @Composable get() = spendGreen
val ColorScheme.warning: Color @Composable get() = spendAmber
```

#### Spendly Theme (aligns with design.md's SpendlyTheme)

```kotlin
// Theme.kt
@Composable
fun SpendlyTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,   // design.md requirement
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            // Dynamic color from wallpaper — earthy semantic extensions still apply
            if (darkTheme) dynamicDarkColorScheme(context)
            else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }
    MaterialTheme(
        colorScheme = colorScheme,
        typography  = SpendlyTypography,   // see Type.kt below
        shapes      = SpendlyShapes,        // see below
        content     = content
    )
}
```

---

### Shape System (aligned with design.md)

```kotlin
// Theme.kt
val SpendlyShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),   // progress bars, small chips
    small      = RoundedCornerShape(8.dp),   // transaction icon containers (was 9dp)
    medium     = RoundedCornerShape(12.dp),  // inner containers, dialogs
    large      = RoundedCornerShape(16.dp),  // main content cards (was 14dp)
    extraLarge = RoundedCornerShape(28.dp)   // pills / status chips
)
// In composables: always reference MaterialTheme.shapes.X, never hardcode dp values
```

---

### Spacing (aligned with design.md 8dp grid)

```kotlin
// Spacing.kt
object Spacing {
    val xs  = 4.dp
    val sm  = 8.dp
    val md  = 16.dp   // was 14dp in LazyColumn spacing — fixed to 16dp
    val lg  = 24.dp
    val xl  = 32.dp
    val xxl = 48.dp
}
// LazyColumn: verticalArrangement = Arrangement.spacedBy(Spacing.md)
// Card content padding: PaddingValues(horizontal = Spacing.md, vertical = Spacing.md)
// Screen horizontal content padding: Spacing.md
```

---

### Typography (DM Sans + Playfair Display wired into M3 type scale)

```kotlin
// Type.kt
val DmSans = FontFamily(
    Font(R.font.dm_sans, FontWeight.Normal),
    Font(R.font.dm_sans, FontWeight.Medium),
)
val PlayfairDisplay = FontFamily(
    Font(R.font.playfair_display_semibold, FontWeight.SemiBold)
)

val SpendlyTypography = Typography(
    // Used for the Playfair accent on balance figures
    displayMedium = TextStyle(
        fontFamily   = PlayfairDisplay,
        fontWeight   = FontWeight.SemiBold,
        fontSize     = 40.sp,
        lineHeight   = 44.sp,
        letterSpacing = (-0.5).sp
    ),
    // Week/large card amount
    headlineMedium = TextStyle(
        fontFamily = DmSans,
        fontWeight = FontWeight.Medium,
        fontSize   = 28.sp,
        lineHeight = 32.sp,
    ),
    // Subscription/goal card amounts
    titleLarge = TextStyle(
        fontFamily = DmSans,
        fontWeight = FontWeight.Medium,
        fontSize   = 22.sp,
        lineHeight = 28.sp,
    ),
    // Merchant names, row primary labels
    titleSmall = TextStyle(
        fontFamily = DmSans,
        fontWeight = FontWeight.Medium,
        fontSize   = 13.sp,
        lineHeight = 18.sp,
    ),
    // General body copy
    bodyMedium = TextStyle(
        fontFamily = DmSans,
        fontWeight = FontWeight.Normal,
        fontSize   = 13.sp,
        lineHeight = 18.sp,
    ),
    // Section label style: uppercase, spaced, muted
    labelSmall = TextStyle(
        fontFamily    = DmSans,
        fontWeight    = FontWeight.Normal,
        fontSize      = 11.sp,
        lineHeight    = 16.sp,
        letterSpacing = 0.06.em
    ),
    // Navigation labels
    labelMedium = TextStyle(
        fontFamily = DmSans,
        fontWeight = FontWeight.Medium,
        fontSize   = 11.sp,
        lineHeight = 16.sp,
    ),
)
// Rule: Playfair Display (displayMedium) is used ONLY for the ₹ balance figure in the Pay Period card.
// All other text uses DM Sans.
```

---

### Adaptive Navigation (design.md requirement)

```kotlin
// In HomeScreen.kt
@Composable
fun HomeScreen(windowSize: WindowSize, ...) {
    val isCompact = windowSize == WindowSize.COMPACT
    if (isCompact) {
        // Use SpendlyBottomNav (custom Row with raised centre FAB)
        Scaffold(bottomBar = { SpendlyBottomNav(...) }) { ... }
    } else {
        // Use NavigationRail per design.md AdaptiveNavigation
        Row {
            NavigationRail { /* destinations */ }
            LazyColumn { /* screen content */ }
        }
    }
}
```

---

## Part 2 — Updated State Model

Add `SpendInsight` to support the new Smart Insights section. Everything else from v1 is preserved.

```kotlin
// HomeUiState.kt

data class HomeUiState(
    val userName: String,
    val payPeriodLabel: String,
    val spentThisPeriod: Double,
    val totalBudgetThisPeriod: Double,
    val currentDayOfPeriod: Int,
    val totalDaysInPeriod: Int,
    val paceStatus: PaceStatus,
    val thisWeekSpent: Double,
    val lastWeekSpent: Double,
    val last7DaysSpend: List<DailySpend>,
    val subscriptions: List<Subscription>,
    val activeGoal: SavingsGoal?,
    val recentTransactions: List<Transaction>,
    val todaySpend: Double,
    val insights: List<SpendInsight>   // ← NEW: pre-computed on ViewModel
)

// --- NEW types ---

/** A single rule-based or ML-derived spending insight shown in the Smart Insights card. */
data class SpendInsight(
    val type: InsightType,
    val title: String,              // short — max 4 words
    val body: String,               // 1-line explanatory sentence
    val severity: InsightSeverity,  // drives left-accent bar color
    val actionLabel: String? = null // optional CTA, e.g. "Review" or "Adjust budget"
)

enum class InsightType {
    PACE_PREDICTION,         // month-end spend forecast
    CATEGORY_SPIKE,          // one category is unusually high
    SUBSCRIPTION_UPCOMING,   // renewals in next 3 days
    GOAL_MILESTONE,          // crossed a savings percentage threshold
    WEEK_TREND,              // week-over-week change summary
    LOW_REMAINING            // less than 10% of budget left
}

enum class InsightSeverity {
    INFO,     // neutral good news → Green accent
    CAUTION,  // worth watching   → Amber accent
    ALERT     // needs attention  → Red accent
}

// --- Existing types (unchanged) ---
data class DailySpend(val date: LocalDate, val amount: Double)
data class Subscription(val name: String, val amount: Double, val nextRenewal: LocalDate)
data class SavingsGoal(val name: String, val savedAmount: Double, val targetAmount: Double, val targetDate: LocalDate)
data class Transaction(
    val id: String,
    val merchant: String,
    val category: TransactionCategory,
    val amount: Double,
    val isIncome: Boolean,
    val timestamp: Instant,
    val paymentMethod: String,
    val tag: String? = null
)
enum class PaceStatus { ON_TRACK, AHEAD, OVER_BUDGET }
enum class TransactionCategory { GROCERIES, BILLS, SHOPPING, TRANSPORT, HEALTH, INCOME, OTHER }
```

---

## Part 3 — ViewModel Changes

Add insight computation to `HomeScreenViewModel`. All derived values (existing and new) remain on the ViewModel, never inside Composables.

```kotlin
// HomeScreenViewModel.kt

class HomeScreenViewModel(
    private val repository: SpendRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(sampleUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    // --- Insight computation ---

    /**
     * Derives up to [maxInsights] rule-based spending insights from the current UI state.
     * Call this whenever underlying data changes and include the result in HomeUiState.
     * The rules below are ordered by urgency — ALERT before CAUTION before INFO —
     * so the most actionable insights surface first.
     */
    private fun computeInsights(state: HomeUiState, maxInsights: Int = 3): List<SpendInsight> {
        val insights = mutableListOf<SpendInsight>()

        // Rule 1 — Low remaining budget (ALERT)
        val remaining = state.totalBudgetThisPeriod - state.spentThisPeriod
        val remainingPct = remaining / state.totalBudgetThisPeriod
        if (remainingPct < 0.10) {
            insights += SpendInsight(
                type        = InsightType.LOW_REMAINING,
                title       = "Budget almost gone",
                body        = "Only ${formatCurrency(remaining)} left for ${state.totalDaysInPeriod - state.currentDayOfPeriod} more days",
                severity    = InsightSeverity.ALERT,
                actionLabel = "Adjust budget"
            )
        }

        // Rule 2 — Month-end pace prediction (ALERT if over, CAUTION if within 5%, INFO otherwise)
        val dailyRunRate     = state.spentThisPeriod / state.currentDayOfPeriod.coerceAtLeast(1)
        val projectedTotal   = dailyRunRate * state.totalDaysInPeriod
        val projectedDelta   = projectedTotal - state.totalBudgetThisPeriod
        val paceSeverity = when {
            projectedDelta > 0                              -> InsightSeverity.ALERT
            projectedDelta > -(state.totalBudgetThisPeriod * 0.05) -> InsightSeverity.CAUTION
            else                                            -> InsightSeverity.INFO
        }
        val paceBody = if (projectedDelta > 0)
            "Projected ${formatCurrency(projectedTotal)} — ${formatCurrency(projectedDelta)} over budget"
        else
            "Projected ${formatCurrency(projectedTotal)} — ${formatCurrency(-projectedDelta)} under budget"
        insights += SpendInsight(
            type     = InsightType.PACE_PREDICTION,
            title    = "Month-end forecast",
            body     = paceBody,
            severity = paceSeverity
        )

        // Rule 3 — Subscriptions renewing in next 3 days (CAUTION)
        val dueThreshold = LocalDate.now().plusDays(3)
        val dueSoon = state.subscriptions.filter { it.nextRenewal <= dueThreshold }
        if (dueSoon.isNotEmpty()) {
            val dueTotal = dueSoon.sumOf { it.amount }
            insights += SpendInsight(
                type        = InsightType.SUBSCRIPTION_UPCOMING,
                title       = "${dueSoon.size} renewals due soon",
                body        = "${formatCurrency(dueTotal)} debiting in the next 3 days",
                severity    = InsightSeverity.CAUTION,
                actionLabel = "Review"
            )
        }

        // Rule 4 — Goal milestone crossed (INFO)
        state.activeGoal?.let { goal ->
            val pct = goal.savedAmount / goal.targetAmount
            val milestone = listOf(0.25, 0.50, 0.75).lastOrNull { pct >= it && pct < it + 0.05 }
            if (milestone != null) {
                insights += SpendInsight(
                    type     = InsightType.GOAL_MILESTONE,
                    title    = "${(milestone * 100).toInt()}% to ${goal.name}",
                    body     = "${formatCurrency(goal.savedAmount)} saved of ${formatCurrency(goal.targetAmount)}",
                    severity = InsightSeverity.INFO
                )
            }
        }

        // Rule 5 — Week-over-week spike (CAUTION if >15% higher)
        val weekDelta = state.thisWeekSpent - state.lastWeekSpent
        val weekDeltaPct = weekDelta / state.lastWeekSpent.coerceAtLeast(1.0)
        if (weekDeltaPct > 0.15) {
            insights += SpendInsight(
                type     = InsightType.WEEK_TREND,
                title    = "Spending up this week",
                body     = "${formatCurrency(weekDelta)} more than last week (${(weekDeltaPct * 100).toInt()}% increase)",
                severity = InsightSeverity.CAUTION
            )
        }

        // Sort: ALERT first, then CAUTION, then INFO; take top N
        return insights
            .sortedBy { it.severity.ordinal } // INFO=0, CAUTION=1, ALERT=2 → reverse for priority
            .reversed()
            .take(maxInsights)
    }

    // Inject computed insights into state when data loads
    private fun buildState(raw: RawSpendData): HomeUiState {
        val base = raw.toHomeUiState()
        return base.copy(insights = computeInsights(base))
    }
}
```

---

## Part 4 — Updated Screen Structure

The screen now has 8 sections. Add **Smart Insights** as section 5 (after the bar chart, before Subscriptions/Goal).

```
LazyColumn (verticalArrangement = 16.dp, contentPadding horizontal = 16.dp)
  ├─ 1. HeaderRow
  ├─ 2. PayPeriodCard
  ├─ 3. ThisWeekCard
  ├─ 4. Last7DaysCard
  ├─ 5. SmartInsightsCard       ← NEW
  ├─ 6. SubscriptionsAndGoalRow
  ├─ 7. RecentTransactionsSection
  └─ (BottomNavBar is outside LazyColumn, in Scaffold bottomBar slot)
```

---

## Part 5 — Smart Insights Card Spec

### Visual design

```
┌─────────────────────────────────────────────┐
│ ✦ SMART INSIGHTS               [3 insights] │  ← header row
├─────────────────────────────────────────────┤
│ ▌ 📈 Month-end forecast                     │  ← ALERT row (Red left bar)
│   Projected ₹58,200 — ₹3,200 over budget   │
│                                    [Adjust] │
├╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌┤  ← 0.5dp divider
│ ▌ 🔄 2 renewals due soon                   │  ← CAUTION row (Amber left bar)
│   ₹1,049 debiting in the next 3 days        │
│                                   [Review] │
├╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌┤
│ ▌ 🎯 50% to Goa trip                        │  ← INFO row (Green left bar)
│   ₹11,600 saved of ₹20,000                 │
└─────────────────────────────────────────────┘
```

### Composable spec — `SmartInsightsCard.kt`

```kotlin
@Composable
fun SmartInsightsCard(
    insights: List<SpendInsight>,
    onInsightAction: (SpendInsight) -> Unit,
    modifier: Modifier = Modifier
) {
    // Card: same border/corner/padding as other cards
    // Header row:
    //   Left: Icon(Icons.Outlined.AutoAwesome or Lightbulb, tint = MaterialTheme.colorScheme.spendPurple, size 15sp)
    //         + Text "SMART INSIGHTS" in label style, spendPurple tint
    //   Right: Text "${insights.size} insights" in 11sp muted — drives user expectation of scroll depth
    //
    // Body: Column of InsightRow composables separated by 0.5dp HorizontalDivider
    //       No divider after the last row
    //
    // Empty state (insights.isEmpty()): show design.md empty state pattern:
    //   Icon(Icons.Outlined.Lightbulb, 48dp, tint = textMuted)
    //   Text("All clear") titleSmall
    //   Text("No unusual patterns in your spending") labelSmall muted
}

@Composable
private fun InsightRow(
    insight: SpendInsight,
    onAction: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Layout: Row, verticalAlignment = CenterVertically, minHeight = 56.dp (touch target)
    //
    // Left accent bar:
    //   Box(modifier = Modifier.width(3.dp).fillMaxHeight().clip(RoundedCornerShape(2.dp))
    //       .background(insight.severity.color()))
    //   Spacing.xs gap after bar
    //
    // Middle: Column, weight(1f)
    //   Row: insight type icon (16dp, severity color) + Spacer(4dp) + Text(insight.title, titleSmall, TextInk)
    //   Text(insight.body, labelSmall, textMuted)
    //
    // Right (optional): if insight.actionLabel != null:
    //   TextButton(onClick = onAction, contentPadding = PaddingValues(0.dp)) {
    //       Text(insight.actionLabel, 11sp, spendGreen or severity color)
    //   }
    //
    // Severity → color mapping:
    //   ALERT   → spendRed
    //   CAUTION → spendAmber
    //   INFO    → spendGreen
}

// Icon mapping for InsightType (reference only, implement as when() in composable)
// PACE_PREDICTION      → Icons.Outlined.TrendingUp
// CATEGORY_SPIKE       → Icons.Outlined.BarChart
// SUBSCRIPTION_UPCOMING→ Icons.Outlined.Refresh
// GOAL_MILESTONE       → Icons.Outlined.EmojiEvents
// WEEK_TREND           → Icons.Outlined.CompareArrows
// LOW_REMAINING        → Icons.Outlined.Warning
```

### Accessibility

- `contentDescription` on the card: `"Smart insights: ${insights.size} items. ${insights.joinToString(". ") { "${it.title}: ${it.body}" }}"`
- Each `InsightRow` must have a `Modifier.semantics { heading() }` on the title text for TalkBack
- Action buttons must meet 48dp touch target — use `Modifier.defaultMinSize(minHeight = 48.dp)` on the TextButton

---

## Part 6 — Full Updated Screen Section List

The full ordered list with any changed details highlighted:

### 1. Header Row
*(No changes from v1 — spec remains the same)*

### 2. Pay Period Balance Card
**Changed:** Card `cornerRadius` → `MaterialTheme.shapes.large` (was hardcoded 14dp). Card padding → `Spacing.md` (was 18dp). Progress bar clip → `MaterialTheme.shapes.extraSmall` (was 3dp).

### 3. This Week Snapshot Card
**Changed:** Card corner/padding as above.

### 4. Last 7 Days Bar Chart Card
**Changed:** Card corner/padding as above. Bar rounded top corners → `4.dp` (already on grid).

### 5. Smart Insights Card ← NEW
Full spec in Part 5 above.

### 6. Subscriptions + Goal Row
**Changed:** Card corner/padding. Transaction icon container → `MaterialTheme.shapes.small` (was 9dp). Status pill → `CircleShape` (was 20dp).
- Goal card empty state: use design.md `EmptyState` pattern (Icon + title + body + "Set a savings goal" button).

### 7. Recent Transactions Section
**Changed:** Icon container → `MaterialTheme.shapes.small`. Row minimum height 48dp enforced via `Modifier.heightIn(min = 48.dp)` on each transaction row.

### 8. Bottom Nav (SpendlyBottomNav)
**Changed:** On `WindowSize.COMPACT` — keep custom Row with raised centre FAB (v1 spec). On `WindowSize.MEDIUM` / `WindowSize.EXPANDED` — use `NavigationRail` per design.md `AdaptiveNavigation` pattern. The raised FAB becomes a standard `FloatingActionButton` at bottom of the rail on larger screens.

---

## Part 7 — Deliverables

*(Extends v1 deliverables list)*

1. `HomeScreen.kt` — updated with `SmartInsightsCard` in the LazyColumn, `WindowSize`-aware Scaffold
2. `HomeScreenViewModel.kt` — `computeInsights()` method, updated sample data including mock insights
3. `Color.kt` / `Theme.kt` — earthy palette bridged to M3 roles, `SpendlyTheme`, dynamic color support
4. `Extensions.kt` — `ColorScheme.spendGreen/Amber/Red/Purple/textMuted/cardBorder` extensions
5. `Type.kt` — DM Sans + Playfair Display wired into M3 `Typography` with role names
6. `Spacing.kt` — `Spacing` object matching design.md's 8dp grid
7. `SpendBarChart.kt` — unchanged from v1
8. **`SmartInsightsCard.kt`** — new file: `SmartInsightsCard` + `InsightRow` composables
9. **`InsightTypes.kt`** — new file: `SpendInsight`, `InsightType`, `InsightSeverity` data classes
10. `@Preview` composables for each card in both light and dark mode, now wrapped in `SpendlyTheme { ... }`

---

## Part 8 — What NOT to Build (unchanged + additions)

*All original exclusions apply, plus:*
- Smart Insights must **not** make network calls — all insights are derived from data already in `HomeUiState`
- Do not add an "AI-powered" label or branding to the insights card — keep it ambient and matter-of-fact
- Do not show more than 3 insights at a time; if the list is empty, show the empty state (not an empty card)
- Do not add a "See all insights" link to an analytics screen in this iteration