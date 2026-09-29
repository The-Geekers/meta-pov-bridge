# Project Status

Last synchronized with code: **2026-09-29**

This file is the operational source of truth for Meta POV Bridge.

## Architecture

```text
Ray-Ban Meta
     ↓
Meta Wearables DAT
     ↓ compressed HEVC
Android Meta POV Bridge
     ├─ SRT: HEVC → MPEG-TS → SRT LIVE
     ├─ RTMP: HEVC → Enhanced RTMP (hvc1)
     └─ foreground service + WakeLock
     ↓
Self-hosted SRT receiver
     ↓
Restreamer / MediaMTX / vMix / production
```

Development without glasses:

```text
Android rear camera
     ↓
Meta Mock Device Kit
     ↓ simulated Ray-Ban Meta
same DAT → HEVC → MPEG-TS → SRT pipeline
```

## Implemented

- Android app, minSdk 31 / targetSdk 36.
- Meta Wearables DAT 1.0.0 core, camera and Mock Device Kit.
- Developer Mode DAT placeholders default to `0`; production credentials can override them through Gradle properties.
- DAT registration and camera permission flows.
- HIGH camera stream, 30 fps, compressed video enabled.
- Video-only MPEG-TS muxer with HEVC stream type 0x24.
- HEVC VPS/SPS/PPS are cached and re-injected on keyframes so late-joining receivers can recover codec dimensions/configuration.
- PAT/PMT are emitted periodically and on keyframes.
- SRT caller with LIVE transport mode.
- Configurable host, port, latency, Stream ID and optional passphrase.
- 1316-byte SRT transport chunks.
- Enhanced RTMP transport via RootEncoder 2.8.1, with direct HEVC passthrough and no Android video re-encode.
- RTMP/RTMPS publish URL input and SRT / RTMP selector in the application UI.
- Android foreground connected-device service.
- Partial WakeLock.
- Mock Ray-Ban Meta creation and lifecycle.
- Phone rear-camera feed for mock glasses.
- Selectable mock video-file feed for emulator/BlueStacks testing.
- Stream errors include their DAT error identity as well as the human-readable description.
- UI status and telemetry.
- Compose UI preview simulations: Idle, Mock Ready, Streaming and Error.
- GitHub Actions debug APK build.
- Pull-request documentation-sync check.
- Automatic Compose screenshot workflow, isolated from APK CI.
- Reproducible FFmpeg SRT listener helper for the VPS smoke test.

## Validated

- BlueStacks installs and launches the APK.
- BlueStacks MockDeviceKit initialization reaches `DAT: MOCK READY`.
- SRT caller successfully handshakes with the existing Restreamer SRT input.
- Corrected SRT path is validated end-to-end in BlueStacks Mock VIDEO FILE → DAT → HEVC → MPEG-TS → SRT → Restreamer: visible picture, 30 fps, and receiver bitrate around 9.8 Mbit/s during the observed test.
- Project compiles in GitHub Actions.
- Debug APK artifact is generated.
- MPEG-TS structure has been sanity-checked with synthetic HEVC and ffprobe.
- Meta Mock Device Kit APIs match the current official sample patterns.
- Foreground-service lifecycle pattern is aligned with Meta's official CameraAccess sample.

## Not yet validated

- Enhanced RTMP publish connects and transmits data from the app, but Restreamer's generated process currently exits with `Error opening output files: Invalid argument`; visible picture is not yet validated.

- BlueStacks handset-camera feed: DAT reaches STARTED but the stream currently returns a critical stream error before the first frame; video-file MockDeviceKit mode is being used to isolate the emulator camera layer.
- Install/start on the intended physical Android phone.
- Mock phone-camera → DAT → SRT end-to-end transport to the VPS.
- Real Ray-Ban Meta camera streaming.
- Long-duration stability.
- Screen-lock/background behavior on the target phone.
- Wi-Fi ↔ 5G transitions.
- SRT reconnection after network loss.
- Thermal and battery behavior.
- Audio capture and synchronization.
- Event-production qualification.

## Next milestone

**RTMP receiver compatibility + physical-device validation**

Procedure: `docs/SRT_END_TO_END_TEST.md`

RTMP procedure: `docs/RTMP_END_TO_END_TEST.md`

1. Prepare an SRT listener on the controlled VPS.
2. Install the latest green debug APK on an Android phone.
3. Enable `TEST PHONE CAMERA`.
4. Confirm `DAT: MOCK READY`.
5. Enter SRT receiver host/port.
6. Start SRT.
7. Confirm incoming MPEG-TS/HEVC with ffprobe and/or Restreamer/vMix.
8. Run a continuous 15-minute stability test.
9. Lock the phone screen and repeat.
10. Simulate a short network interruption and inspect recovery behavior.

## Definition of done

A feature is complete only when:

- it compiles;
- relevant documentation/status is updated in the same change;
- validation state is explicit;
- unvalidated behavior is not described as validated.
