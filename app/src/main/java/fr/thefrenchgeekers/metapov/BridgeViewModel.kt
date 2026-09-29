package fr.thefrenchgeekers.metapov

import android.app.Application
import android.net.Uri
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
import com.meta.wearable.dat.mockdevice.MockDeviceKit
import com.meta.wearable.dat.mockdevice.api.GlassesModel
import com.meta.wearable.dat.mockdevice.api.MockDeviceKitConfig
import com.meta.wearable.dat.mockdevice.api.MockGlasses
import com.meta.wearable.dat.mockdevice.api.camera.CameraFacing
import java.io.File
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
    val mockEnabled: Boolean = false,
    val mockSource: String = "",
)

class BridgeViewModel(app: Application) : AndroidViewModel(app) {
    private val _state = MutableStateFlow(BridgeState())
    val state = _state.asStateFlow()

    private var session: DeviceSession? = null
    private var camera: Camera? = null
    private var frameJob: Job? = null

    private val sender = SrtSender()
    private val mux = MpegTsMuxer()
    private val hevcNormalizer = HevcAccessUnitNormalizer()
    private var cfg = SrtConfig("", 9000)

    private val mockDeviceKit = MockDeviceKit.getInstance(app.applicationContext)
    private var mockGlasses: MockGlasses? = null
    private var mockSetupInProgress = false

    init {
        Wearables.initialize(app)
    }

    fun register(activity: android.app.Activity) = Wearables.startRegistration(activity)

    fun enableMockPhoneCamera() {
        enableMock("PHONE CAMERA") { glasses ->
            glasses.services.camera.setCameraFeed(CameraFacing.BACK)
        }
    }

    fun enableMockVideoFile(sourceUri: Uri) {
        enableMock("VIDEO FILE") { glasses ->
            val app = getApplication<Application>()
            val cached = File(app.cacheDir, "meta_pov_mock_feed.mp4")
            app.contentResolver.openInputStream(sourceUri)?.use { input ->
                cached.outputStream().use { output -> input.copyTo(output) }
            } ?: error("Unable to read selected video file")

            glasses.services.camera.setCameraFeed(Uri.fromFile(cached))
        }
    }

    private fun enableMock(
        sourceLabel: String,
        configureFeed: (MockGlasses) -> Unit,
    ) {
        if (_state.value.live || mockSetupInProgress) return

        mockSetupInProgress = true
        _state.update { it.copy(dat = "MOCK SETUP", error = null) }

        viewModelScope.launch(Dispatchers.Default) {
            try {
                if (!mockDeviceKit.isEnabled) {
                    mockDeviceKit.enable(
                        MockDeviceKitConfig(
                            initiallyRegistered = true,
                            initialPermissionsGranted = true,
                        ),
                    )
                }

                val glasses =
                    mockGlasses
                        ?: mockDeviceKit
                            .pairGlasses(GlassesModel.RAYBAN_META)
                            .getOrThrow()
                            .also { mockGlasses = it }

                glasses.powerOn()
                glasses.unfold()
                glasses.don()
                configureFeed(glasses)

                _state.update {
                    it.copy(
                        dat = "MOCK READY",
                        mockEnabled = true,
                        mockSource = sourceLabel,
                        error = null,
                    )
                }
            } catch (t: Throwable) {
                _state.update {
                    it.copy(
                        dat = "MOCK ERROR",
                        error = t.message ?: t.toString(),
                        mockEnabled = false,
                        mockSource = "",
                    )
                }
            } finally {
                mockSetupInProgress = false
            }
        }
    }

    fun disableMock() {
        if (_state.value.live) return

        viewModelScope.launch(Dispatchers.Default) {
            runCatching {
                mockGlasses?.let { mockDeviceKit.unpairDevice(it) }
                mockGlasses = null
                mockDeviceKit.disable()
            }.onFailure { failure ->
                _state.update { it.copy(error = failure.message ?: failure.toString()) }
                return@launch
            }

            _state.value = BridgeState()
        }
    }

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
                var permissionStatus: PermissionStatus = PermissionStatus.Denied
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

                mux.reset()
                hevcNormalizer.reset()
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
                    created.errors.collect { datError ->
                        _state.update { it.copy(error = datError.description) }
                    }
                }

                viewModelScope.launch {
                    created.state.collect { sessionState ->
                        _state.update { it.copy(dat = sessionState.toString()) }
                        if (sessionState == DeviceSessionState.STARTED && camera == null) {
                            attachCamera(created)
                        }
                    }
                }

                created.start()
            },
            onFailure = { datError, _ ->
                sender.close()
                _state.update {
                    it.copy(
                        error = datError.description,
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
                    addedCamera.stream.errorStream.collect { datError ->
                        _state.update {
                            it.copy(error = "STREAM $datError: ${datError.description}")
                        }
                    }
                }

                frameJob = viewModelScope.launch(Dispatchers.Default) {
                    addedCamera.stream.videoStream.collect { frame ->
                        if (!frame.isCompressed) return@collect

                        val buffer = frame.buffer.duplicate()
                        val raw = ByteArray(buffer.remaining())
                        buffer.get(raw)

                        val normalized =
                            hevcNormalizer.normalize(raw, frame.isCodecConfig)
                                ?: return@collect
                        val ts =
                            mux.muxHevc(
                                normalized.data,
                                frame.presentationTimeUs,
                                normalized.isKeyFrame,
                            )
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
                    addedCamera.stream.start().onFailure { datError, _ ->
                        _state.update {
                            it.copy(error = "STREAM START $datError: ${datError.description}")
                        }
                        StreamingService.stop(getApplication())
                    }
                }
            },
            onFailure = { datError, _ ->
                StreamingService.stop(getApplication())
                sender.close()
                _state.update {
                    it.copy(
                        error = datError.description,
                        srt = "ERROR",
                        live = false,
                    )
                }
            },
        )
    }

    fun stop() {
        val keepMock = _state.value.mockEnabled

        frameJob?.cancel()
        frameJob = null

        camera?.stop()
        camera = null

        session?.stop()
        session = null

        StreamingService.stop(getApplication())
        sender.close()
        mux.reset()
        hevcNormalizer.reset()

        _state.value = BridgeState(
            dat = if (keepMock) "MOCK READY" else "IDLE",
            mockEnabled = keepMock,
            mockSource = if (keepMock) _state.value.mockSource else "",
        )
    }

    override fun onCleared() {
        stop()
        if (mockDeviceKit.isEnabled) {
            mockDeviceKit.disable()
        }
        super.onCleared()
    }
}
