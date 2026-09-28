# Meta POV Bridge — V0.1

Android proof of concept for a direct, self-hosted POV contribution chain:

**Ray-Ban Meta → Meta Wearables DAT → Android → HEVC → MPEG-TS → SRT → our server / production**

No third-party streaming cloud is required by the target architecture.

## Current status

Reference date: **2026-09-28**

- ✅ Android project builds in GitHub Actions.
- ✅ Debug APK is produced as the `MetaPOVBridge-debug` artifact.
- ✅ Meta Wearables DAT `1.0.0`.
- ✅ Compressed HEVC camera request: HIGH / 720×1280 / 30 fps.
- ✅ Video-only MPEG-TS muxer.
- ✅ SRT caller with LIVE transport mode, latency, Stream ID and optional passphrase.
- ✅ 1316-byte SRT payload chunks for MPEG-TS transport.
- ✅ Android foreground service and partial WakeLock for background / screen-locked operation.
- ✅ Meta Mock Device Kit integration.
- ✅ Phone rear camera can simulate Ray-Ban Meta for development without glasses.
- ✅ Compose UI previews: Idle, Mock Ready, Streaming and Error.
- ⏳ End-to-end Android Mock → SRT receiver validation.
- ⏳ Real Ray-Ban Meta validation.
- ⏳ Audio transport.

Detailed state: [`docs/PROJECT_STATUS.md`](docs/PROJECT_STATUS.md).

## Test modes

### Real glasses

`REGISTER META` starts the official Meta registration flow, then the app creates a DAT session and requests the glasses camera stream.

### Mock / no glasses

`TEST PHONE CAMERA` enables Meta Mock Device Kit, creates simulated Ray-Ban Meta glasses, powers/unfolds/dons them and uses the Android rear camera as the mock glasses feed.

The rest of the transport chain is unchanged:

`phone camera → Mock Ray-Ban → DAT → compressed HEVC → MPEG-TS → SRT`

## UI previews

The real UI and the preview simulations use the same `BridgeScreenContent` composable.

Preview states are defined directly in `MainActivity.kt`:

- Idle
- Mock ready
- Streaming
- Error

This lets us review the interface before using a physical phone. A separate workflow renders PNG references into the `MetaPOVBridge-ui-screenshots` artifact. See [`docs/UI_PREVIEWS.md`](docs/UI_PREVIEWS.md).

## CI / APK

Every push to `main` builds a debug APK.

Workflow: `.github/workflows/build-apk.yml`

Artifact: `MetaPOVBridge-debug`

Pull requests also include a documentation-sync check: application/build changes must update either this README or a file under `docs/`.

## V0.1 limits

- Video only.
- Real-glasses behavior is not yet validated.
- Long-duration stability and network recovery are not yet qualified.
- Audio is intentionally deferred until the video transport path is validated.
- This is not yet production-qualified for an event.

## Acceptance path

First software milestone:

`Phone camera → Mock DAT → HEVC → MPEG-TS → SRT → receiver → ffprobe / Restreamer / vMix`

Then:

`Ray-Ban Meta → DAT → same pipeline`

Initial stability target: **15 minutes continuous video with no app/process crash and a clean receiver stream**.

## Documentation rule

A feature is not considered complete until its documentation/status is updated in the same change.
