# Analytics metric tiles — layout contract

Tiles under `app/src/main/java/com/spendly/tracker/ui/screens/analytics/` should follow one visual grammar so analytics feels like one surface.

## Grammar

1. **One primary metric** — Large value reads first (`primaryValue` in [`AnalyticsMetricTileContent`](../app/src/main/java/com/spendly/tracker/ui/screens/analytics/AnalyticsMetricTile.kt)).
2. **One secondary row** — Footer with left summary + optional right pill (`bottomLeft*` / `bottomRightPill`).
3. **Transaction count** — Shown as a compact badge (icon + count) in the header row, not duplicated in the footer unless the metric is count-specific.
4. **Tap target** — Whole card is tappable when `onClick != null`; drill-down must open **`TransactionsScreen`** with filters visible or recoverable (chips / period label) so users understand why the list changed.

## Drill-down

- Category / merchant / period / payment mode parameters should match what the tile describes.
- Prefer navigating through [`MainScreen`](../app/src/main/java/com/spendly/tracker/ui/MainScreen.kt) inner `transactions?...` routes so the user stays inside the tab shell when possible.

## New tiles

When adding a tile composable, reuse [`AnalyticsMetricTile`](../app/src/main/java/com/spendly/tracker/ui/screens/analytics/AnalyticsMetricTile.kt) or mirror its spacing (`Dimensions.Padding.content`, `Spacing` from theme) and typography roles (`labelMedium` for labels, `headlineSmall` / `titleLarge` for values).
