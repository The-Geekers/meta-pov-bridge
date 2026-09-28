# Mock → SRT → VPS smoke test

This is the first functional transport milestone for Meta POV Bridge.

For the first pass, leave the SRT passphrase empty. We validate basic transport before adding encryption.

## VPS

Use UDP port `9000` unless it is already occupied.

Check the port:

```bash
sudo ss -lunp | grep -E ':9000\\b' || true
```

From a checkout of this repository:

```bash
chmod +x tools/srt-listener.sh
./tools/srt-listener.sh 9000
```

The helper starts a pinned FFmpeg Docker image as an SRT listener in LIVE mode and discards decoded output after validating the incoming container/codec.

## Android

1. Install the latest green `MetaPOVBridge-debug` APK.
2. Grant Camera and Bluetooth permissions.
3. Tap `TEST PHONE CAMERA`.
4. Wait for `DAT: MOCK READY`.
5. Set `SRT host / IP` to the VPS public IP.
6. Keep port `9000`.
7. Keep latency `500 ms`.
8. Keep Stream ID `meta01`.
9. Leave Passphrase empty.
10. Tap `START SRT`.

## Expected result

- SRT changes to connected in the app.
- Frame and byte counters increase.
- FFmpeg accepts the SRT connection.
- FFmpeg identifies an MPEG-TS input containing an HEVC video stream.

## Diagnosis by symptom

- No connection: check public IP, UDP reachability and firewalling.
- SRT connects but MPEG-TS/HEVC is unreadable: investigate muxing or DAT compressed-frame packaging.
- Stream stops only after screen lock/background: investigate Android foreground-service/power behavior.
- Stream breaks on network changes: add/rework SRT reconnection logic.

## Acceptance sequence after first picture

1. 15 minutes continuous streaming.
2. Screen locked.
3. App backgrounded.
4. Short network interruption.
5. Wi-Fi to mobile-data transition.
6. Then integrate the receiver into Restreamer/MediaMTX/vMix.
