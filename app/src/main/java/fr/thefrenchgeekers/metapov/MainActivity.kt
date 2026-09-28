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
import androidx.compose.foundation.layout.weight
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
import androidx.compose.ui.unit.dp
import com.meta.wearable.dat.core.Wearables
import com.meta.wearable.dat.core.types.Permission
import com.meta.wearable.dat.core.types.PermissionStatus
import kotlin.coroutines.resume
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class MainActivity : ComponentActivity() {
    private val vm: BridgeViewModel by viewModels()

    private var wearableContinuation: CancellableContinuation<PermissionStatus>? = null
    private val permissionMutex = Mutex()

    private val wearablePermission =
        registerForActivityResult(Wearables.RequestPermissionContract()) { result ->
            wearableContinuation?.resume(
                result.getOrDefault(PermissionStatus.Denied),
            )
            wearableContinuation = null
        }

    private val androidPermissions =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {}

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
            MaterialTheme(colorScheme = darkColorScheme()) {
                Surface(Modifier.fillMaxSize()) {
                    BridgeScreen(
                        vm = vm,
                        request = ::requestWearable,
                        register = { vm.register(this) },
                    )
                }
            }
        }
    }
}

@Composable
fun BridgeScreen(
    vm: BridgeViewModel,
    request: suspend (Permission) -> PermissionStatus,
    register: () -> Unit,
) {
    val state by vm.state.collectAsState()

    var host by remember { mutableStateOf("") }
    var port by remember { mutableStateOf("9000") }
    var streamId by remember { mutableStateOf("meta01") }
    var passphrase by remember { mutableStateOf("") }
    var latency by remember { mutableStateOf("500") }

    Column(
        Modifier
            .fillMaxSize()
            .padding(22.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            "META POV BRIDGE",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Black,
        )
        Text(
            "Ray-Ban Meta → DAT HEVC → MPEG-TS → SRT",
            color = MaterialTheme.colorScheme.primary,
        )

        HorizontalDivider()

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Status("DAT", state.dat)
            Status("VIDEO", state.video)
            Status("SRT", state.srt)
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (!state.mockEnabled) {
                OutlinedButton(
                    onClick = vm::enableMockPhoneCamera,
                    enabled = !state.live,
                ) {
                    Text("TEST PHONE CAMERA")
                }
            } else {
                OutlinedButton(
                    onClick = vm::disableMock,
                    enabled = !state.live,
                ) {
                    Text("DISABLE MOCK")
                }
            }

            Button(
                onClick = register,
                enabled = !state.live && !state.mockEnabled,
            ) {
                Text("REGISTER META")
            }
        }

        if (state.mockEnabled) {
            Text(
                "Mock actif : la caméra arrière du téléphone simule les Ray-Ban Meta.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }

        OutlinedTextField(
            value = host,
            onValueChange = { host = it },
            label = { Text("SRT host / IP") },
            modifier = Modifier.fillMaxWidth(),
            enabled = !state.live,
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = port,
                onValueChange = { port = it.filter(Char::isDigit) },
                label = { Text("Port") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f),
                enabled = !state.live,
            )
            OutlinedTextField(
                value = latency,
                onValueChange = { latency = it.filter(Char::isDigit) },
                label = { Text("Latency ms") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f),
                enabled = !state.live,
            )
        }

        OutlinedTextField(
            value = streamId,
            onValueChange = { streamId = it },
            label = { Text("Stream ID") },
            modifier = Modifier.fillMaxWidth(),
            enabled = !state.live,
        )

        OutlinedTextField(
            value = passphrase,
            onValueChange = { passphrase = it },
            label = { Text("Passphrase (optional)") },
            modifier = Modifier.fillMaxWidth(),
            enabled = !state.live,
        )

        if (!state.live) {
            Button(
                onClick = {
                    vm.start(
                        host = host,
                        port = port.toIntOrNull() ?: 9000,
                        streamId = streamId,
                        pass = passphrase,
                        latency = latency.toIntOrNull() ?: 500,
                        requestPermission = request,
                    )
                },
                enabled = host.isNotBlank(),
            ) {
                Text("START SRT")
            }
        } else {
            Button(onClick = vm::stop) {
                Text("STOP")
            }
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
private fun Status(key: String, value: String) {
    AssistChip(
        onClick = {},
        label = { Text("$key: $value") },
    )
}
