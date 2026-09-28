# Meta POV Bridge — V0.1

Prototype Android: **Ray-Ban Meta → Meta DAT 1.0 compressed HEVC → MPEG-TS → SRT caller**.

## Target
- Pixel 8/9 or other Android 12+ device
- Ray-Ban Meta supported by DAT
- Meta AI + Developer Mode / Wearables Developer Center registration
- SRT listener/server (Restreamer, MediaMTX, srt-live-transmit, etc.)

## CI / APK
Every push to `main` runs GitHub Actions and builds a debug APK.

Artifact name: **MetaPOVBridge-debug**

Workflow: `.github/workflows/build-apk.yml`

## V0.1 pipeline
1. DAT session requests `VideoQuality.HIGH`, 30 fps, `compressVideo=true`.
2. Compressed HEVC access units are wrapped in a video-only MPEG-TS stream.
3. MPEG-TS is emitted through SRT in 1316-byte chunks.
4. SRT caller parameters: host, port, stream ID, passphrase, latency.

## Known V0.1 limits
- Video only for the first transport validation.
- Foreground service/watchdog hardening comes after the first green end-to-end test.
- MockDeviceKit integration is the next functional milestone.
- MPEG-TS output must be validated with ffprobe / Restreamer using real or mocked DAT compressed frames.
- Real-glasses validation is mandatory before event use.

## Acceptance test
`Ray-Ban/Mock → app → SRT → server → ffprobe/vMix/Restreamer`, stable image for 15 min with no process crash.
