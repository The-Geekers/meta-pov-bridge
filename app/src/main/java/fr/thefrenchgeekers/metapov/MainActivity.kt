package fr.thefrenchgeekers.metapov

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
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

class MainActivity:ComponentActivity(){
    private val vm:BridgeViewModel by viewModels()
    private var wearableContinuation:CancellableContinuation<PermissionStatus>?=null
    private val wearablePermission=registerForActivityResult(Wearables.RequestPermissionContract()){r->wearableContinuation?.resume(r.getOrDefault(PermissionStatus.Denied));wearableContinuation=null}
    private val androidPermissions=registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()){}
    private suspend fun requestWearable(p:Permission)=suspendCancellableCoroutine<PermissionStatus>{c->wearableContinuation=c;c.invokeOnCancellation{wearableContinuation=null};wearablePermission.launch(p)}

    override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState)
        androidPermissions.launch(arrayOf(Manifest.permission.BLUETOOTH_CONNECT,Manifest.permission.CAMERA))
        setContent{ MaterialTheme(colorScheme=darkColorScheme()){ Surface(Modifier.fillMaxSize()){BridgeScreen(vm,::requestWearable,{vm.register(this)})} } }
    }
}

@Composable fun BridgeScreen(vm:BridgeViewModel,request:suspend(Permission)->PermissionStatus,register:()->Unit){
    val s by vm.state.collectAsState(); var host by remember{mutableStateOf("")};var port by remember{mutableStateOf("9000")};var sid by remember{mutableStateOf("meta01")};var pass by remember{mutableStateOf("")};var latency by remember{mutableStateOf("500")}
    Column(Modifier.fillMaxSize().padding(22.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
        Text("META POV BRIDGE",style=MaterialTheme.typography.headlineMedium,fontWeight=FontWeight.Black)
        Text("Ray-Ban Meta → DAT HEVC → MPEG-TS → SRT",color=MaterialTheme.colorScheme.primary)
        HorizontalDivider()
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){Status("DAT",s.dat);Status("VIDEO",s.video);Status("SRT",s.srt)}
        OutlinedTextField(host,{host=it},label={Text("SRT host / IP")},modifier=Modifier.fillMaxWidth(),enabled=!s.live)
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){OutlinedTextField(port,{port=it.filter(Char::isDigit)},label={Text("Port")},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number),modifier=Modifier.weight(1f),enabled=!s.live);OutlinedTextField(latency,{latency=it.filter(Char::isDigit)},label={Text("Latency ms")},modifier=Modifier.weight(1f),enabled=!s.live)}
        OutlinedTextField(sid,{sid=it},label={Text("Stream ID")},modifier=Modifier.fillMaxWidth(),enabled=!s.live)
        OutlinedTextField(pass,{pass=it},label={Text("Passphrase (optional)")},modifier=Modifier.fillMaxWidth(),enabled=!s.live)
        Row(horizontalArrangement=Arrangement.spacedBy(10.dp)){Button(onClick=register,enabled=!s.live){Text("REGISTER META")}; if(!s.live) Button(onClick={vm.start(host,port.toIntOrNull()?:9000,sid,pass,latency.toIntOrNull()?:500,request)},enabled=host.isNotBlank()){Text("START SRT")} else Button(onClick=vm::stop){Text("STOP")}}
        Text("Frames: ${s.frames}   •   ${(s.bytes/1024.0/1024.0).let{"%.2f".format(it)}} MiB")
        s.error?.let{Card(colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.errorContainer)){Text(it,Modifier.padding(12.dp))}}
        Spacer(Modifier.weight(1f));Text("V0.1 • HIGH 720×1280 • 30 fps • HEVC passthrough",style=MaterialTheme.typography.labelMedium)
    }
}
@Composable private fun Status(k:String,v:String){AssistChip(onClick={},label={Text("$k: $v")})}
