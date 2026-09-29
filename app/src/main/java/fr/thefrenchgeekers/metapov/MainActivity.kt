package fr.thefrenchgeekers.metapov

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.meta.wearable.dat.core.Wearables
import com.meta.wearable.dat.core.types.Permission
import com.meta.wearable.dat.core.types.PermissionStatus
import kotlin.coroutines.resume
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

enum class TransportMode {
    SRT,
    RTMP,
}

class MainActivity : ComponentActivity() {
    private val vm: BridgeViewModel by viewModels()
    private var wearableContinuation: CancellableContinuation<PermissionStatus>? = null
    private val permissionMutex = Mutex()

    private val wearablePermission =
        registerForActivityResult(Wearables.RequestPermissionContract()) { result ->
            wearableContinuation?.resume(result.getOrDefault(PermissionStatus.Denied))
            wearableContinuation = null
        }

    private val androidPermissions =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {}

    private val mockVideoPicker =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            uri?.let(vm::enableMockVideoFile)
        }

    private suspend fun requestWearable(permission: Permission): PermissionStatus {
        return permissionMutex.withLock {
            suspendCancellableCoroutine { continuation ->
                wearableContinuation = continuation
                continuation.invokeOnCancellation { wearableContinuation = null }
                wearablePermission.launch(permission)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        androidPermissions.launch(
            arrayOf(
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.CAMERA,
            ),
        )
        setContent {
            MetaPovTheme {
                BridgeScreen(
                    vm = vm,
                    request = ::requestWearable,
                    register = { vm.register(this) },
                    pickMockVideo = { mockVideoPicker.launch(arrayOf("video/*")) },
                )
            }
        }
    }
}

@Composable
fun MetaPovTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = darkColorScheme()) {
        Surface(Modifier.fillMaxSize()) { content() }
    }
}

@Composable
fun BridgeScreen(
    vm: BridgeViewModel,
    request: suspend (Permission) -> PermissionStatus,
    register: () -> Unit,
    pickMockVideo: () -> Unit,
) {
    val state by vm.state.collectAsState()
    var transportMode by remember { mutableStateOf(TransportMode.SRT) }
    var host by remember { mutableStateOf("") }
    var port by remember { mutableStateOf("9000") }
    var streamId by remember { mutableStateOf("meta01") }
    var passphrase by remember { mutableStateOf("") }
    var latency by remember { mutableStateOf("500") }
    var rtmpUrl by remember { mutableStateOf("") }

    BridgeScreenContent(
        state = state,
        transportMode = transportMode,
        host = host,
        port = port,
        streamId = streamId,
        passphrase = passphrase,
        latency = latency,
        rtmpUrl = rtmpUrl,
        onTransportModeChange = { transportMode = it },
        onHostChange = { host = it },
        onPortChange = { port = it.filter(Char::isDigit) },
        onStreamIdChange = { streamId = it },
        onPassphraseChange = { passphrase = it },
        onLatencyChange = { latency = it.filter(Char::isDigit) },
        onRtmpUrlChange = { rtmpUrl = it },
        onEnableMock = vm::enableMockPhoneCamera,
        onEnableMockVideo = pickMockVideo,
        onDisableMock = vm::disableMock,
        onRegister = register,
        onStart = {
            when (transportMode) {
                TransportMode.SRT ->
                    vm.startSrt(
                        host = host,
                        port = port.toIntOrNull() ?: 9000,
                        streamId = streamId,
                        pass = passphrase,
                        latency = latency.toIntOrNull() ?: 500,
                        requestPermission = request,
                    )
                TransportMode.RTMP ->
                    vm.startRtmp(
                        url = rtmpUrl,
                        requestPermission = request,
                    )
            }
        },
        onStop = vm::stop,
    )
}

@Composable
fun BridgeScreenContent(
    state: BridgeState,
    transportMode: TransportMode,
    host: String,
    port: String,
    streamId: String,
    passphrase: String,
    latency: String,
    rtmpUrl: String,
    onTransportModeChange: (TransportMode) -> Unit,
    onHostChange: (String) -> Unit,
    onPortChange: (String) -> Unit,
    onStreamIdChange: (String) -> Unit,
    onPassphraseChange: (String) -> Unit,
    onLatencyChange: (String) -> Unit,
    onRtmpUrlChange: (String) -> Unit,
    onEnableMock: () -> Unit,
    onEnableMockVideo: () -> Unit,
    onDisableMock: () -> Unit,
    onRegister: () -> Unit,
    onStart: () -> Unit,
    onStop: () -> Unit,
) {
    Column(
        Modifier.fillMaxSize().padding(22.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            "META POV BRIDGE",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Black,
        )
        Text(
            "Ray-Ban Meta → DAT HEVC → SRT / Enhanced RTMP",
            color = MaterialTheme.colorScheme.primary,
        )
        HorizontalDivider()

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Status("DAT", state.dat, Modifier.weight(1f))
            Status("VIDEO", state.video, Modifier.weight(1f))
            Status(state.protocol, state.srt, Modifier.weight(1f))
        }

        if (!state.mockEnabled) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedButton(
                    onClick = onEnableMock,
                    enabled = !state.live,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("PHONE CAMERA")
                }
                OutlinedButton(
                    onClick = onEnableMockVideo,
                    enabled = !state.live,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("VIDEO FILE")
                }
            }
            Button(onClick = onRegister, enabled = !state.live) {
                Text("REGISTER META")
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = onDisableMock, enabled = !state.live) {
                    Text("DISABLE MOCK")
                }
                Button(onClick = onRegister, enabled = false) {
                    Text("REGISTER META")
                }
            }
        }

        if (state.mockEnabled) {
            Text(
                "Mock actif : ${state.mockSource}.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (transportMode == TransportMode.SRT) {
                Button(
                    onClick = { onTransportModeChange(TransportMode.SRT) },
                    enabled = !state.live,
                    modifier = Modifier.weight(1f),
                ) { Text("SRT") }
                OutlinedButton(
                    onClick = { onTransportModeChange(TransportMode.RTMP) },
                    enabled = !state.live,
                    modifier = Modifier.weight(1f),
                ) { Text("RTMP") }
            } else {
                OutlinedButton(
                    onClick = { onTransportModeChange(TransportMode.SRT) },
                    enabled = !state.live,
                    modifier = Modifier.weight(1f),
                ) { Text("SRT") }
                Button(
                    onClick = { onTransportModeChange(TransportMode.RTMP) },
                    enabled = !state.live,
                    modifier = Modifier.weight(1f),
                ) { Text("RTMP") }
            }
        }

        if (transportMode == TransportMode.SRT) {
            OutlinedTextField(
            value = host,
            onValueChange = onHostChange,
            label = { Text("SRT host / IP") },
            modifier = Modifier.fillMaxWidth(),
            enabled = !state.live,
            singleLine = true,
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = port,
                onValueChange = onPortChange,
                label = { Text("Port") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f),
                enabled = !state.live,
                singleLine = true,
            )
            OutlinedTextField(
                value = latency,
                onValueChange = onLatencyChange,
                label = { Text("Latency ms") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f),
                enabled = !state.live,
                singleLine = true,
            )
        }

        OutlinedTextField(
            value = streamId,
            onValueChange = onStreamIdChange,
            label = { Text("Stream ID") },
            modifier = Modifier.fillMaxWidth(),
            enabled = !state.live,
            singleLine = true,
        )

        OutlinedTextField(
                value = passphrase,
                onValueChange = onPassphraseChange,
                label = { Text("Passphrase (optional)") },
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.live,
                singleLine = true,
            )
        } else {
            OutlinedTextField(
                value = rtmpUrl,
                onValueChange = onRtmpUrlChange,
                label = { Text("RTMP / RTMPS publish URL") },
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.live,
                singleLine = true,
            )
            Text(
                "HEVC passthrough via Enhanced RTMP (hvc1) — no video re-encode.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }

        val canStart =
            when (transportMode) {
                TransportMode.SRT -> host.isNotBlank()
                TransportMode.RTMP ->
                    rtmpUrl.trim().startsWith("rtmp://") ||
                        rtmpUrl.trim().startsWith("rtmps://")
            }

        if (!state.live) {
            Button(onClick = onStart, enabled = canStart) {
                Text("START ${transportMode.name}")
            }
        } else {
            Button(onClick = onStop) { Text("STOP") }
        }

        Text(
            "Frames: ${state.frames}   •   " +
                "${"%.2f".format(state.bytes / 1024.0 / 1024.0)} MiB",
        )

        state.error?.let { error ->
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                ),
            ) {
                Text(error, Modifier.padding(12.dp))
            }
        }

        Spacer(Modifier.weight(1f))
        Text(
            "V0.1 • HIGH 720×1280 • 30 fps • HEVC passthrough",
            style = MaterialTheme.typography.labelMedium,
        )
    }
}

@Composable
private fun Status(
    key: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier) {
        Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
            Text(
                text = key,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = value,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private val previewNoOp: (String) -> Unit = {}

@Composable
private fun PreviewScreen(
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
            onHostChange = previewNoOp,
            onPortChange = previewNoOp,
            onStreamIdChange = previewNoOp,
            onPassphraseChange = previewNoOp,
            onLatencyChange = previewNoOp,
            onRtmpUrlChange = previewNoOp,
            onEnableMock = {},
            onEnableMockVideo = {},
            onDisableMock = {},
            onRegister = {},
            onStart = {},
            onStop = {},
        )
    }
}

@Preview(name = "Idle", showBackground = true, widthDp = 412, heightDp = 915)
@Composable
private fun IdlePreview() = PreviewScreen(BridgeState())

@Preview(name = "Mock ready", showBackground = true, widthDp = 412, heightDp = 915)
@Composable
private fun MockReadyPreview() =
    PreviewScreen(
        BridgeState(
            dat = "MOCK READY",
            mockEnabled = true,
        ),
    )

@Preview(name = "Streaming", showBackground = true, widthDp = 412, heightDp = 915)
@Composable
private fun StreamingPreview() =
    PreviewScreen(
        BridgeState(
            dat = "STARTED",
            video = "STREAMING",
            srt = "CONNECTED",
            frames = 18_420,
            bytes = 148_897_792,
            live = true,
            mockEnabled = true,
        ),
    )

@Preview(name = "RTMP ready", showBackground = true, widthDp = 412, heightDp = 915)
@Composable
private fun RtmpReadyPreview() =
    PreviewScreen(
        state = BridgeState(
            dat = "MOCK READY",
            protocol = "RTMP",
            mockEnabled = true,
            mockSource = "VIDEO FILE",
        ),
        mode = TransportMode.RTMP,
    )

@Preview(name = "Error", showBackground = true, widthDp = 412, heightDp = 915)
@Composable
private fun ErrorPreview() =
    PreviewScreen(
        BridgeState(
            dat = "STARTED",
            video = "STREAMING",
            srt = "ERROR",
            frames = 926,
            bytes = 7_243_776,
            error = "SRT connection lost",
            mockEnabled = true,
        ),
    )
