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

data class BridgeState(
    val dat: String = "IDLE",
    val video: String = "STOPPED",
    val srt: String = "DISCONNECTED",
    val frames: Long = 0,
    val bytes: Long = 0,
    val error: String? = null,
    val live: Boolean = false,
)

class BridgeViewModel(app: Application) : AndroidViewModel(app) {
    private val _state = MutableStateFlow(BridgeState())
    val state = _state.asStateFlow()

    private var session: DeviceSession? = null
    private var camera: Camera? = null
    private var frameJob: Job? = null

    private val sender = SrtSender()
    private val mux = MpegTsMuxer()
    private var cfg = SrtConfig("", 9000)

    init {
        Wearables.initialize(app)
    }

    fun register(activity: android.app.Activity) = Wearables.startRegistration(activity)

    fun start(
        host: String,
        port: Int,
        streamId: String,
        pass: String,
        latency: Int,
        requestPermission: suspend (Permission) -> PermissionStatus,
    ) {
        if (_state.value.live) return
        cfg = SrtConfig(host, port, streamId, pass, latency)

        viewModelScope.launch {
            try {
                var permissionStatus = PermissionStatus.Denied
                var permissionError: String? = null

                Wearables.checkPermissionStatus(Permission.CAMERA).fold(
                    onSuccess = { status -> permissionStatus = status },
                    onFailure = { datError, _ -> permissionError = datError.description },
                )

                permissionError?.let { message -> error(message) }

                if (permissionStatus != PermissionStatus.Granted) {
                    val requested = requestPermission(Permission.CAMERA)
                    if (requested != PermissionStatus.Granted) {
                        error("Camera permission denied")
                    }
                }

                sender.connect(cfg)
                _state.update { it.copy(srt = "CONNECTED", live = true, error = null) }
                startDat()
            } catch (t: Throwable) {
                sender.close()
                _state.update {
                    it.copy(
                        error = t.message ?: t.toString(),
                        srt = "ERROR",
                        live = false,
                    )
                }
            }
        }
    }

    private fun startDat() {
        Wearables.createSession(AutoDeviceSelector()).fold(
            onSuccess = { created ->
                session = created
                _state.update { it.copy(dat = "CONNECTING") }

                viewModelScope.launch {
                    created.errors.collect { error ->
                        _state.update { it.copy(error = error.description) }
                    }
                }

                viewModelScope.launch {
                    created.state.collect { state ->
                        _state.update { it.copy(dat = state.toString()) }
                        if (state == DeviceSessionState.STARTED && camera == null) {
                            attachCamera(created)
                        }
                    }
                }

                created.start()
            },
            onFailure = { error, _ ->
                sender.close()
                _state.update {
                    it.copy(
                        error = error.description,
                        srt = "ERROR",
                        live = false,
                    )
                }
            },
        )
    }

    private fun attachCamera(activeSession: DeviceSession) {
        StreamingService.start(getApplication())

        activeSession.addCamera(
            StreamConfiguration(
                videoQuality = VideoQuality.HIGH,
                frameRate = 30,
                compressVideo = true,
            ),
        ).fold(
            onSuccess = { addedCamera ->
                camera = addedCamera

                viewModelScope.launch {
                    addedCamera.stream.state.collect { streamState ->
                        _state.update { it.copy(video = streamState.toString()) }
                    }
                }

                viewModelScope.launch {
                    addedCamera.stream.errorStream.collect { error ->
                        _state.update { it.copy(error = error.description) }
                    }
                }

                frameJob = viewModelScope.launch(Dispatchers.Default) {
                    addedCamera.stream.videoStream.collect { frame ->
                        if (!frame.isCompressed) return@collect

                        val buffer = frame.buffer.duplicate()
                        val raw = ByteArray(buffer.remaining())
                        buffer.get(raw)

                        val ts = mux.muxHevc(raw, frame.presentationTimeUs)
                        try {
                            sender.send(ts)
                            _state.update {
                                it.copy(
                                    frames = it.frames + 1,
                                    bytes = it.bytes + ts.size,
                                )
                            }
                        } catch (t: Throwable) {
                            _state.update {
                                it.copy(
                                    error = t.message ?: t.toString(),
                                    srt = "ERROR",
                                )
                            }
                        }
                    }
                }

                viewModelScope.launch {
                    addedCamera.stream.start().onFailure { error, _ ->
                        _state.update { it.copy(error = error.description) }
                        StreamingService.stop(getApplication())
                    }
                }
            },
            onFailure = { error, _ ->
                StreamingService.stop(getApplication())
                sender.close()
                _state.update {
                    it.copy(
                        error = error.description,
                        srt = "ERROR",
                        live = false,
                    )
                }
            },
        )
    }

    fun stop() {
        frameJob?.cancel()
        frameJob = null

        camera?.stop()
        camera = null

        session?.stop()
        session = null

        StreamingService.stop(getApplication())
        sender.close()
        _state.value = BridgeState()
    }

    override fun onCleared() {
        stop()
        super.onCleared()
    }
}
