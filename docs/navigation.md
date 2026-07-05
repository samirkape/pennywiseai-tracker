# Navigation architecture

## Two NavHosts

1. **Root graph** — [`PennyWiseNavHost.kt`](../app/src/main/java/com/spendly/tracker/navigation/PennyWiseNavHost.kt)  
   Typed routes (`@Serializable` objects in [`PennyWiseDestinations.kt`](../app/src/main/java/com/spendly/tracker/navigation/PennyWiseDestinations.kt)): onboarding, app lock, transaction detail, add transaction, rules, loans, budget edit from settings shortcuts, filtered transaction lists, etc.

2. **Main tab shell** — [`MainScreen.kt`](../app/src/main/java/com/spendly/tracker/ui/MainScreen.kt) inner `NavHost`  
   String routes in [`Constants.Routes`](../app/src/main/java/com/spendly/tracker/core/Constants.kt): home, transactions (with query args), subscriptions, analytics, optional chat (see below), pay period explorer, budgets, settings, and nested settings flows.

The **Home** route at the root hosts `MainScreen`, which owns the bottom navigation.

## Back behavior (main shell)

- **Bottom tabs** (home, budgets, analytics, settings): system back from a non-home tab switches to **Home** instead of popping the inner graph. This avoids overlapping destinations when switching tabs quickly.
- **Pushed routes** (transactions, optional chat, behavioral stats, nested settings, etc.): system back performs a normal **pop** to the previous inner destination. When chat is enabled, chat and analytics behave like a typical stack.

## Duplicate entry points (by design today)

- **Settings** exists as `composable<Settings>` on the **root** host (e.g. from filtered `TransactionsScreen` opened from root) and as `settings` on the **inner** host (tab). Both render the same [`SettingsScreen`](../app/src/main/java/com/spendly/tracker/ui/screens/settings/SettingsScreen.kt); back stack differs by entry path.
- **Budget groups** similarly: root `BudgetGroups` (e.g. from root settings) vs inner `budgets` (tab). Prefer inner tab URLs for in-app navigation when `MainScreen` is already shown.

Future consolidation could deep-link the inner graph from the root with a single route type; that requires passing state into `MainScreen` or merging graphs.

## AI Chat route (optional)

Inner-graph route: `Constants.Routes.CHAT` (`"chat"`). It is **registered only when** [`Constants.Features.AI_CHAT_ENABLED`](../app/src/main/java/com/spendly/tracker/core/Constants.kt) is `true`. When `false` (current product default), there is no chat destination in the inner `NavHost` and no in-app navigation to it. There is no separate typed `Chat` destination on the root graph.
