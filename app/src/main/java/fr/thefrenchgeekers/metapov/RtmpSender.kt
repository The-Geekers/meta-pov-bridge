package fr.thefrenchgeekers.metapov

import android.media.MediaCodec
import com.pedro.common.ConnectChecker
import com.pedro.common.VideoCodec
import com.pedro.rtmp.rtmp.RtmpClient
import java.nio.ByteBuffer
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeout

data class RtmpConfig(
    val url: String,
    val width: Int = 720,
    val height: Int = 1280,
    val fps: Int = 30,
)

class RtmpSender(
    private val onRuntimeError: (String) -> Unit = {},
) {
    private var client: RtmpClient? = null
    private var sessionToken: Any? = null
    private var videoInfoReady = false

    suspend fun connect(config: RtmpConfig) {
        close()

        val url = config.url.trim()
        require(url.startsWith("rtmp://") || url.startsWith("rtmps://")) {
            "RTMP URL must start with rtmp:// or rtmps://"
        }

        val connected = CompletableDeferred<Unit>()
        val token = Any()
        sessionToken = token

        val checker =
            object : ConnectChecker {
                override fun onConnectionStarted(url: String) = Unit

                override fun onConnectionSuccess() {
                    if (sessionToken === token && !connected.isCompleted) {
                        connected.complete(Unit)
                    }
                }

                override fun onConnectionFailed(reason: String) {
                    if (sessionToken !== token) return
                    if (!connected.isCompleted) {
                        connected.completeExceptionally(
                            IllegalStateException("RTMP connection failed: $reason"),
                        )
                    } else {
                        onRuntimeError("RTMP connection failed: $reason")
                    }
                }

                override fun onDisconnect() = Unit

                overrie fun onAuthError() {
                    if (sessionToken !== token) return
                    val message = "RTMP authentication failed"
                    if (!connected.isCompleted) {
                        connected.completeExceptionally(IllegalStateException(message))
                    } else {
                        onRuntimeError(message)
                    }
                }

                override fun onAuthSuccess() = Unit
            }

        val created =
            RtmpClient(checker).apply {
                setVideoCodec(VideoCodec.H265)
                setOnlyVideo(true)
                setVideoResolution(config.width, config.height)
                setFps(config.fps)
                setReTries(0)
            }

        client = created
        videoInfoReady = false
        created.connect(url)

        try {
            withTimeout(CONNECT_TIMEOUT_MS) {
                connected.await()
            }
        } catch (t: Throwable) {
            close()
            throw t
        }
    }

    fun setVideoInfo(config: HevcCodecConfig) {
        val active = client ?: return
        active.setVideoInfo(
            ByteBuffer.wrap(config.sps),
            ByteBuffer.wrap(config.pps),
            ByteBuffer.wrap(config.vps),
        )
        videoInfoReady = true
    }

    fun sendVideo(
        data: ByteArray,
        presentationTimeUs: Long,
        isKeyFrame: Boolean,
    ): Int {
        if (data.isEmpty() || !videoInfoReady) return 0
        val active = client ?: error("RTMP not connected")

        val info =
            MediaCodec.BufferInfo().apply {
                set(
                    0,
                    data.size,
                    presentationTimeUs,
                    if (isKeyFrame) MediaCodec.BUFFER_FLAG_KEY_FRAME else 0,
                )
            }

        active.sendVideo(ByteBuffer.wrap(data), info)
        return data.size
    }

    fun close() {
        val old = client
        client = null
        sessionToken = null
        videoInfoReady = false
        runCatching { old?.disconnect() }
    }

    companion object {
        private const val CONNECT_TIMEOUT_MS = 15_000L
    }
}
