package fr.thefrenchgeekers.metapov

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.meta.wearable.dat.camera.Camera
import com.meta.wearable.dat.camera.addCamera
import com.meta.wearable.dat.camera.types.StreamConfiguration
import com.meta.wearable.dat.camera.types.VideoQuality
import com.meta.wearable.dat.core.Wearables
import com.meta.wearable.dat.core.selectors.AutoDeviceSelector
import com.meta.wearable.dat.core.session.DeviceSession
import com.meta.wearable.dat.core.session.DeviceSessionState
import com.meta.wearable.dat.core.types.Permission
import com.meta.wearable.dat.core.types.PermissionStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class BridgeState(val dat="IDLE",val video="STOPPED",val srt="DISCONNECTED",val frames:Long=0,val bytes:Long=0,val error:String?=null,val live:Boolean=false)

class BridgeViewModel(app:Application):AndroidViewModel(app){
    private val _state=MutableStateFlow(BridgeState()); val state=_state.asStateFlow()
    private var session:DeviceSession?=null; private var camera:Camera?=null; private var frameJob:Job?=null
    private val sender=SrtSender(); private val mux=MpegTsMuxer(); private var cfg=SrtConfig("",9000)

    init { Wearables.initialize(app) }
    fun register(activity:android.app.Activity)=Wearables.startRegistration(activity)

    fun start(host:String,port:Int,streamId:String,pass:String,latency:Int, requestPermission:suspend(Permission)->PermissionStatus){
        if(_state.value.live)return; cfg=SrtConfig(host,port,streamId,pass,latency)
        viewModelScope.launch {
            try{
                val perm=Wearables.checkPermissionStatus(Permission.CAMERA).getOrElse{throw IllegalStateException(it.message)}
                if(perm!=PermissionStatus.Granted){ val p=requestPermission(Permission.CAMERA); if(p!=PermissionStatus.Granted) error("Camera permission denied") }
                sender.connect(cfg); _state.update{it.copy(srt="CONNECTED",live=true,error=null)}; startDat()
            }catch(t:Throwable){ sender.close(); _state.update{it.copy(error=t.message?:t.toString(),srt="ERROR",live=false)} }
        }
    }

    private fun startDat(){
        Wearables.createSession(AutoDeviceSelector()).fold(onSuccess={s->
            session=s; _state.update{it.copy(dat="CONNECTING")}
            viewModelScope.launch { s.errors.collect{e->_state.update{it.copy(error=e.description)} } }
            viewModelScope.launch { s.state.collect{st->
                _state.update{it.copy(dat=st.toString())}
                if(st==DeviceSessionState.STARTED && camera==null) attachCamera(s)
            }}
            s.start()
        },onFailure={e,_-> _state.update{it.copy(error=e.description,live=false)} })
    }

    private fun attachCamera(s:DeviceSession){
        s.addCamera(StreamConfiguration(videoQuality=VideoQuality.HIGH,frameRate=30,compressVideo=true)).fold(onSuccess={c->
            camera=c
            viewModelScope.launch { c.stream.state.collect{v->_state.update{it.copy(video=v.toString())}} }
            viewModelScope.launch { c.stream.errorStream.collect{e->_state.update{it.copy(error=e.description)}} }
            frameJob=viewModelScope.launch(Dispatchers.Default){ c.stream.videoStream.collect{f->
                if(!f.isCompressed)return@collect
                val bb=f.buffer.duplicate(); val raw=ByteArray(bb.remaining()); bb.get(raw)
                val ts=mux.muxHevc(raw,f.presentationTimeUs)
                try{ sender.send(ts); _state.update{it.copy(frames=it.frames+1,bytes=it.bytes+ts.size)} }catch(t:Throwable){_state.update{it.copy(error=t.message,srt="ERROR")}}
            }}
            viewModelScope.launch { c.stream.start().onFailure{e,_-> _state.update{it.copy(error=e.description)} } }
        },onFailure={e,_-> _state.update{it.copy(error=e.description)} })
    }

    fun stop(){ frameJob?.cancel();frameJob=null;camera?.stop();camera=null;session?.stop();session=null;sender.close();_state.value=BridgeState() }
    override fun onCleared(){stop();super.onCleared()}
}
