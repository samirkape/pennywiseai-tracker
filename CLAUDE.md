# Spendly Project Context

## Project Overview
Spendly is a minimalist, AI-powered expense tracker for Android that automatically extracts transaction data from SMS messages using on-device processing.

## Important Documents
Please reference these documents when working on this project:
- **Architecture**: `/docs/architecture.md` - MVVM + Clean Architecture patterns, layer responsibilities
- **Design System**: `/docs/design.md` - Material 3 theming, colors, typography, components
- **Navigation**: `/docs/navigation.md` - root vs main shell, back behavior, duplicate entry points
- **Screen shells**: `/docs/scaffold-patterns.md` - hero vs standard scaffold patterns
- **PRD**: `/prd.md` - Product requirements, features, timeline

## Key Technical Decisions
- **AI chat (optional)**: `Constants.Features.AI_CHAT_ENABLED` gates the inner-shell chat route (`Routes.CHAT`). When `false`, the chat composable is not registered and there is no in-app navigation to it (LLM / chat UI code may remain for future enablement).

## Design Principles
- **Material You**: Dynamic color from wallpaper (Android 12+)
- **Light/Dark Theme**: Full support with semantic color roles
- **Spacing**: 8dp grid system
- **Typography**: Material 3 type scale
- **Navigation**: NavigationBar for phones, NavigationRail for tablets
- **Edge-to-Edge**: All screens use SpendlyScaffold with default TopAppBar for consistent system bar handling
- **Consistent UI**: SpendlyScaffold provides default TopAppBar with options for title, navigation, actions, and transparency

## Current Phase
Working on Phase 1: Core Foundation (Project setup, Material 3 theming, Room database, Navigation)

## Device Deployment (After UI/Code Changes)
After making changes, always build and install the release APK on the connected device:
```
./gradlew :app:assembleStandardRelease --quiet && \
adb install -r app/build/outputs/apk/standard/release/app-standard-arm64-v8a-release.apk
```
- The connected device ID is `R5CW61GQRTB` (use `-s R5CW61GQRTB` if multiple devices are connected)
- The release app package is `com.spendly.tracker` (separate from the debug build)
- After install, relaunch with: `adb -s R5CW61GQRTB shell am force-stop com.spendly.tracker && adb -s R5CW61GQRTB shell am start -n com.spendly.tracker/com.spendly.tracker.MainActivity`
- You can take a screenshot to verify UI changes: `adb -s R5CW61GQRTB exec-out screencap -p > /tmp/screen.png`

# Important
Never use pii in comments, code anywhere
