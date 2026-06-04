# Screen shell patterns

Aligned with [design.md](design.md) edge-to-edge and Material 3 usage.

## Pattern A — Hero home

- **When**: Primary dashboard (`HomeScreen`).
- **Structure**: Transparent `Scaffold` container, [`CustomTitleTopAppBar`](../app/src/main/java/com/pennywiseai/tracker/ui/components/CustomTitleTopAppBar.kt) with large/collapsing scroll behavior, optional blur (`HazeState`), hero pager and scrollable feed below.
- **Do not** force this onto dense list screens; it increases implementation cost and scroll coupling.

## Pattern B — Standard list / detail

- **When**: Most secondary screens (settings sub-pages, behavioral stats, simple forms).
- **Structure**: Prefer [`PennyWiseStandardScaffold`](../app/src/main/java/com/pennywiseai/tracker/ui/components/PennyWiseStandardScaffold.kt) or [`PennyWiseScaffold`](../app/src/main/java/com/pennywiseai/tracker/ui/components/PennyWiseScaffold.kt) with a standard `TopAppBar`, solid surface colors, and explicit back navigation.
- Screens that need **collapsing large titles** with blur can keep `CustomTitleTopAppBar` with pinned + exit-until-collapsed behaviors (e.g. `SettingsScreen`, `CategoriesScreen`).

## Pattern C — Tablet / wide layout (planned)

- For width breakpoints, prefer a **NavigationRail** plus content (see project `CLAUDE.md`). This is not wired in the main shell yet; add `androidx.compose.material3.adaptive` / window size class when introducing rail + list-detail splits.
