# Release readiness — Phase 0 & 1 baseline

Captured during Phase 0 (baseline) and Phase 1 (green build) execution. Update this file when the release candidate changes.

## Phase 0 — Baseline

| Item | Value |
|------|--------|
| **Branch** | `main` (tracks `origin/main`) |
| **HEAD at capture** | `e27dc48` — *note: working tree had many local modifications; commit before tagging a release.* |
| **Default release flavor** | `standard` (`app/build.gradle.kts`, `isDefault = true`) |
| **applicationId** | `com.spendly.tracker` |
| **Debug applicationId** | `com.spendly.tracker.debug` (`applicationIdSuffix` on `debug`) |
| **versionCode** | `88` |
| **versionName** | `2.15.53` (debug: `2.15.53-debug`) |
| **minSdk / targetSdk** | `26` / `36` |
| **Release signing** | `signingConfigs.release` applied to **standard** `release` only when `local.properties` defines `RELEASE_STORE_FILE` and keystore exists |
| **Other flavors** | `fdroid` (ABI filters, no local signing block in snippet) |

### Phase 0 follow-ups (process)

1. **Commit or stash** WIP before creating a release branch or tag.
2. **Create a release branch** (e.g. `release/2.15.53`) from the commit you intend to ship, if you use branch-based releases.
3. **Play track:** set in Play Console (internal → closed → production); not stored in repo.
4. **Open PRs:** not listed here (`gh` unavailable in this environment); run `gh pr list` locally if needed.

---

## Phase 1 — Green build

| Step | Result |
|------|--------|
| **Kotlin compile (`standardRelease`)** | ✅ `./gradlew :app:clean :app:compileStandardReleaseKotlin` |
| **Unit tests** | ✅ `./gradlew test` |
| **Full release APK** | ✅ `./gradlew :app:assembleStandardRelease` (includes `lintVitalStandardRelease`) |
| **Full lint (`lintStandardRelease`)** | ✅ Passes (see Phase 2–3 for follow-up fixes bundled after baseline). |

### Code fixes applied (Phase 1)

- **`PayPeriodExplorerViewModel`:** Replaced six-way `Flow.combine` (Kotlin inference issue) with nested `combine` of two triples, then `collect` → `recompute`.
- **Pay period timeline:** `PayPeriodExplorerContent` is shown from Home (bottom sheet); no separate full-screen route.
- **Widget preview layouts:** Replaced `@android:style/Theme.DeviceDefault.DayNight` (API 29+) with `@style/Theme.Spendlycompose` for minSdk 26 compatibility in four layout files.

### Lint (`:app:lintStandardRelease`)

```bash
./gradlew :app:lintStandardRelease
```

**Current status:** ✅ **BUILD SUCCESSFUL** — no lint errors (warnings may remain). Phase 2–3 changes include `LocalResources.getString` instead of `LocalContext.current.getString` in snackbars, `MerchantRenameReviewSheet` `AnimatedContent` + `key()`, and `@Suppress("UnusedTransitionTargetStateParameter")` on `BlurredAnimatedVisibility`.

---

## Phase 2–3 (post-baseline)

- **`Constants.Features.AI_CHAT_ENABLED`:** `false` — inner `NavHost` does not register `Routes.CHAT`; `AnalyticsScreen` no longer accepts `onNavigateToChat`.
- **FAQ:** “AI Features” category omitted when chat is off; privacy/backup copy adjusted.
- **Settings appearance:** Subtitle moved to `R.string.settings_appearance_subtitle` (plain “and”, no raw `&` in Kotlin).
- **`docs/navigation.md`:** Documents optional chat route behind the feature flag.

### Optional next command

```bash
./gradlew :app:bundleStandardRelease
```

for Play upload (AAB).
