package fr.thefrenchgeekers.metapov

import android.media.MediaCodec
import com.pedro.common.AudioCodec
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
    private var nextSilentAudioPtsUs: Long? = null

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

                override fun onAuthError() {
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
                setAudioCodec(AudioCodec.AAC)
                setOnlyVideo(false)
                setAudioInfo(AUDIO_SAMPLE_RATE, false)
                setVideoResolution(config.width, config.height)
                setFps(config.fps)
                setReTries(0)
            }

        client = created
        videoInfoReady = false
        nextSilentAudioPtsUs = null
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

        sendSilentAudioUntil(active, presentationTimeUs)

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

    private fun sendSilentAudioUntil(
        active: RtmpClient,
        videoPtsUs: Long,
    ) {
        var next = nextSilentAudioPtsUs

        if (next == null || videoPtsUs + AUDIO_RESYNC_THRESHOLD_US < next) {
            next = videoPtsUs
        }

        while (next <= videoPtsUs) {
            val info =
                MediaCodec.BufferInfo().apply {
                    set(
                        0,
                        SILENT_AAC_LC_FRAME.size,
                        next,
                        0,
                    )
                }

            active.sendAudio(ByteBuffer.wrap(SILENT_AAC_LC_FRAME), info)
            next += AAC_FRAME_DURATION_US
        }

        nextSilentAudioPtsUs = next
    }

    fun close() {
        val old = client
        client = null
        sessionToken = null
        videoInfoReady = false
        nextSilentAudioPtsUs = null
        runCatching { old?.disconnect() }
    }

    companion object {
        private const val CONNECT_TIMEOUT_MS = 15_000L

        private const val AUDIO_SAMPLE_RATE = 44_100
        private const val AAC_SAMPLES_PER_FRAME = 1024L
        private const val AAC_FRAME_DURATION_US =
            AAC_SAMPLES_PER_FRAME * 1_000_000L / AUDIO_SAMPLE_RATE
        private const val AUDIO_RESYNC_THRESHOLD_US = 1_000_000L

        // One valid AAC-LC mono silence access unit at 44.1 kHz.
        // RootEncoder emits the AAC sequence header from setAudioInfo().
        private val SILENT_AAC_LC_FRAME =
            byteArrayOf(
                0x01,
                0x18,
                0x20,
                0x07,
            )
    }
}
