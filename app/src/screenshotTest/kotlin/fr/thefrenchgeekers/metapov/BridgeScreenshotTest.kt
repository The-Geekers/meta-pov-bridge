package fr.thefrenchgeekers.metapov

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest

private val noTextChange: (String) -> Unit = {}

@Composable
private fun ScreenshotState(
    state: BridgeState,
    mode: TransportMode = TransportMode.SRT,
) {
    MetaPovTheme {
        BridgeScreenContent(
            state = state,
            transportMode = mode,
            host = "stream.example.net",
            port = "9000",
            streamId = "meta01",
            passphrase = "",
            latency = "500",
            rtmpUrl = "rtmp://stream.example.net/live/meta01",
            onTransportModeChange = {},
            onHostChange = noTextChange,
            onPortChange = noTextChange,
            onStreamIdChange = noTextChange,
            onPassphraseChange = noTextChange,
            onLatencyChange = noTextChange,
            onRtmpUrlChange = noTextChange,
            onEnableMock = {},
            onEnableMockVideo = {},
            onDisableMock = {},
            onRegister = {},
            onStart = {},
            onStop = {},
        )
    }
}

@PreviewTest
@Preview(name = "Idle", showBackground = true, widthDp = 412, heightDp = 915)
@Composable
fun IdleScreenshot() = ScreenshotState(BridgeState())

@PreviewTest
@Preview(name = "Mock ready", showBackground = true, widthDp = 412, heightDp = 915)
@Composable
fun MockReadyScreenshot() = ScreenshotState(BridgeState(dat = "MOCK READY", mockEnabled = true))

@PreviewTest
@Preview(name = "Streaming", showBackground = true, widthDp = 412, heightDp = 915)
@Composable
fun StreamingScreenshot() = ScreenshotState(
    BridgeState(dat = "STARTED", video = "STREAMING", srt = "CONNECTED", frames = 18_420, bytes = 148_897_792, live = true, mockEnabled = true),
)

@PreviewTest
@Preview(name = "RTMP ready", showBackground = true, widthDp = 412, heightDp = 915)
@Composable
fun RtmpReadyScreenshot() =
    ScreenshotState(
        state = BridgeState(
            dat = "MOCK READY",
            protocol = "RTMP",
            mockEnabled = true,
            mockSource = "VIDEO FILE",
        ),
        mode = TransportMode.RTMP,
    )

@PreviewTest
@Preview(name = "Error", showBackground = true, widthDp = 412, heightDp = 915)
@Composable
fun ErrorScreenshot() = ScreenshotState(
    BridgeState(dat = "STARTED", video = "STREAMING", srt = "ERROR", frames = 926, bytes = 7_243_776, error = "SRT connection lost", mockEnabled = true),
)
