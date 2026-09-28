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

The project now uses the official Compose Preview Screenshot Testing Gradle plugin in a separate GitHub Actions workflow.

Workflow: `.github/workflows/ui-screenshots.yml`

Artifact: `MetaPOVBridge-ui-screenshots`

Reference captures: idle, mock-ready, streaming and error.

The screenshot workflow is separate from the APK workflow because the upstream screenshot-testing feature is still experimental. A screenshot-render failure must not prevent the transport APK from being built.

## Visual review

The generated captures are also used as a visual review step before they are reused in documentation. The first generated set exposed an overly narrow status chip for long SRT states, so the status row was changed to three equal-width stacked cards (label + value) before publishing screenshots.
