package fr.thefrenchgeekers.metapov

import io.github.thibaultbee.srtdroid.core.enums.SockOpt
import io.github.thibaultbee.srtdroid.core.enums.Transtype
import io.github.thibaultbee.srtdroid.core.models.SrtSocket
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class SrtConfig(
    val host: String,
    val port: Int,
    val streamId: String = "",
    val passphrase: String = "",
    val latencyMs: Int = 500,
)

class SrtSender {
    private var socket: SrtSocket? = null

    suspend fun connect(config: SrtConfig) = withContext(Dispatchers.IO) {
        close()

        require(config.host.isNotBlank()) { "SRT host is required" }
        require(config.port in 1..65535) { "Invalid SRT port" }
        require(config.passphrase.isBlank() || config.passphrase.length in 10..79) {
            "SRT passphrase must be 10 to 79 characters"
        }

        val s = SrtSocket()
        s.setSockFlag(SockOpt.TRANSTYPE, Transtype.LIVE)
        s.setSockFlag(SockOpt.SENDER, 1)
        s.setSockFlag(SockOpt.LATENCY, config.latencyMs)
        s.setSockFlag(SockOpt.PAYLOADSIZE, 1316)

        if (config.streamId.isNotBlank()) {
            s.setSockFlag(SockOpt.STREAMID, config.streamId)
        }
        if (config.passphrase.isNotBlank()) {
            s.setSockFlag(SockOpt.PASSPHRASE, config.passphrase)
        }

        s.connect(config.host, config.port)
        socket = s
    }

    suspend fun send(data: ByteArray) = withContext(Dispatchers.IO) {
        val s = socket ?: error("SRT not connected")
        var offset = 0
        while (offset < data.size) {
            val size = minOf(1316, data.size - offset)
            s.send(data, offset, size)
            offset += size
        }
    }

    fun close() {
        runCatching { socket?.close() }
        socket = null
    }
}
