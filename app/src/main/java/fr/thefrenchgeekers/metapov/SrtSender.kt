package fr.thefrenchgeekers.metapov

import io.github.thibaultbee.srtdroid.core.enums.SockOpt
import io.github.thibaultbee.srtdroid.core.models.SrtSocket
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class SrtConfig(val host:String,val port:Int,val streamId:String="",val passphrase:String="",val latencyMs:Int=500)

class SrtSender {
    private var socket:SrtSocket?=null
    suspend fun connect(c:SrtConfig)=withContext(Dispatchers.IO){
        close(); val s=SrtSocket(); s.setSockFlag(SockOpt.SENDER,1); s.setSockFlag(SockOpt.LATENCY,c.latencyMs)
        s.setSockFlag(SockOpt.PAYLOADSIZE,1316)
        if(c.streamId.isNotBlank()) s.setSockFlag(SockOpt.STREAMID,c.streamId)
        if(c.passphrase.isNotBlank()) s.setSockFlag(SockOpt.PASSPHRASE,c.passphrase)
        s.connect(c.host,c.port); socket=s
    }
    suspend fun send(data:ByteArray)=withContext(Dispatchers.IO){
        val s=socket ?: error("SRT not connected")
        var off=0; while(off<data.size){ val n=minOf(1316,data.size-off); s.send(data,off,n); off+=n }
    }
    fun close(){ runCatching{socket?.close()};socket=null }
}
