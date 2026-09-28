# UI Previews and Screenshots

The app UI is structured so the real application and preview simulations use the same `BridgeScreenContent` composable.

## Preview states

`MainActivity.kt` defines:

- **Idle**
- **Mock ready**
- **Streaming**
- **Error**

These are rendered from the real Compose UI with simulated state data, not from a separate hand-drawn mockup.

## Viewing the previews

In Android Studio, open:

`app/src/main/java/fr/thefrenchgeekers/metapov/MainActivity.kt`

and use the Compose Preview panel.

The app includes:

- `androidx.compose.ui:ui-tooling-preview`
- debug-only `androidx.compose.ui:ui-tooling`

## Automatic PNG screenshots

Android provides official Compose Preview Screenshot Testing that can render preview states into reference PNG files and compare future UI changes with those references.

We intentionally keep automatic PNG generation separate from the APK build because the upstream screenshot-testing feature is still experimental. The transport build must remain the blocking CI path.

Planned reference captures:

1. idle
2. mock-ready
3. streaming
4. error

Once the screenshot workflow is stable, the generated images can be linked directly from the README.
