# Advanced Transaction Search (Kibana-style query language)

The transactions search bar accepts both plain text (existing behavior, unchanged)
and a Kibana Query Language (KQL)-inspired syntax for scoping searches to
specific fields.

Motivation: searching `UPI` as free text matches merchant names, SMS bodies
("Spent Rs 500 **via UPI**"), references and descriptions at once. A scoped query
like `merchant:"UPI"` matches only transactions whose merchant name is exactly
"UPI".

Core: `app/src/main/java/com/spendly/tracker/utils/TransactionQuery.kt`
(`TransactionQueryParser`), wired into `TransactionsViewModel.getFilteredTransactions`.
Fallback matching uses the legacy `TransactionSearchMatcher.matchesText`.

## UI (wizard-based, no syntax knowledge required)

`presentation/transactions/TransactionsSearchWizard.kt` provides
`TransactionSearchAssistant`, driven by a small **tune icon** at the right of
the search bar (hidden by default; tapping unfolds it vertically):

- **Launcher chips** (`+ Merchant`, `+ Category`, `+ Amount`, `+ Date`,
  `+ Type`, `+ Tag`, `+ Account`) open a bottom sheet with tappable values
  (real merchant names from history, the user's categories/tags/accounts,
  date presets **plus a custom date range picker**, or an amount
  comparison/range builder).
- **Category and Account are multi-select** with an `All` chip; applying
  several values appends an OR-group clause (matches any of them).
- Type-ahead suggestions complete the trailing clause while typing
  (`merchant:ne`, bare `merchant`, `tag:"wor` → value chips).
- Picking a value appends a clause to the query string joined with `and`
  (`appendClauseText`).
- The current query renders as **removable clause chips** derived by parsing
  the query back into its AST (`parseFlatAndChain`); each chip's ✕ removes it
  (`removeFlatClause`) and re-serializes the rest (`SearchNode.toQueryString`).
- The search bar remains fully editable — structured chips and free typing
  coexist, because the query string is the single source of truth.
- Mixed free text + field chips render; OR-groups of field clauses render as a
  single combined chip (e.g. `Category: Food or Travel`) and stay removable.
- A `date:…` clause **supersedes the screen's period filter** while active —
  a preset like `Last 90 days` can widen the window beyond the period the
  screen was opened with. Removing the chip restores the period filter.

## Syntax

| Query | Meaning |
|---|---|
| `upi` | plain text: contains across all fields (legacy behavior, unchanged) |
| `"phone bill"` | quoted phrase: contiguous match across all fields |
| `merchant:upi` | merchant name **contains** "upi" |
| `merchant:"UPI"` | merchant name **equals** "UPI" (case-insensitive) |
| `merchant:upi*` | wildcard against the whole merchant value (`*` = any chars, `?` = one) |
| `name:"UPI"` | `name` is an alias for `merchant` |
| `desc:coffee`, `note:"rent march"` | description (`description`, `desc`, `note`, `notes`) |
| `sms:debited`, `body:debited` | parsed SMS body |
| `tag:"work"`, `tags:reimb*` | tags are comma-separated; quoted = whole-token match |
| `category:"Food"`, `cat:food` | category |
| `type:expense` | `expense`/`income`/`credit`/`transfer`/`investment` (prefix match ok) |
| `bank:hdfc` | bank name |
| `account:1234` | account/card last-4 (also `accountNumber` full match with wildcards) |
| `amount>1000` | also `>=`, `<`, `<=`; `amount:100-200` = inclusive range |
| `date:2026-01-15` | whole ISO day; `date:2026-01` = whole month |
| `date:2026-01-01-2026-01-31` | custom inclusive range (reversed bounds are swapped) |
| `date:"last 90 days"` | presets: `today`, `yesterday`, `this week/month/year`, `last N days` |
| `date>2026-01-01` | bounds: `>` `<` `>=` `<=` |
| `date:today` | also `yesterday`, `"this week"`, `"this month"`, `"this year"`, `"last 30 days"` |
| `ref:"UPI/123"`, `sender:ybl`, `currency:inr` | reference / SMS sender / currency |

Boolean operators:

```
merchant:swiggy AND amount>100
merchant:swiggy OR merchant:zomato
NOT type:transfer
merchant:swiggy NOT category:travel
(cat:food AND amount>100) OR cat:travel
merchant:swiggy amount>100   # implicit AND between neighbors
```

Precedence: `NOT` > `AND` (implicit or explicit) > `OR`. Parentheses group.

Quoting rules: unquoted values are *contains*; quoted values match the *whole*
value; a value containing `*` always matches the whole value. Values with spaces
must be quoted (`note:"office snacks"`).

## Error handling

Invalid syntax (missing quote/paren/value, bad amount or date) never breaks
search: the query falls back to plain-text matching and the error is shown
inline under the search bar (`searchQueryError` in `TransactionsViewModel`).

## Design notes

- Parsing happens once per query (`TransactionQueryParser.parse`), evaluation is
  in-memory over the existing filter pipeline — no schema/DB changes.
- Pure free-text queries intentionally keep the exact legacy matcher behavior
  (including amount substring and type keyword matching), so existing searches
  return identical results.
- `RefundLinker` still uses the legacy broad matcher on purpose.
- Adding a field: add an entry to the `SearchField` enum with its aliases and an
  evaluation arm in `SearchFieldEvaluator.evaluate`.
