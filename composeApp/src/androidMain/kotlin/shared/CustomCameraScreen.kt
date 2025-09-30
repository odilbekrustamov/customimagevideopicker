package shared

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FlashAuto
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.Timer
import java.util.TimerTask
import java.util.concurrent.Executor

@Composable
fun CustomCameraScreen(
    config: CameraConfig = CameraConfig(),
    onResult: (CameraResult) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var cameraMode by remember { mutableStateOf(config.initialMode) }
    var lensFacing by remember { mutableStateOf(CameraSelector.LENS_FACING_BACK) }
    var flashMode by remember { mutableStateOf(ImageCapture.FLASH_MODE_OFF) }

    val cameraState = rememberCameraState(
        context = context,
        lifecycleOwner = lifecycleOwner,
        lensFacing = lensFacing,
        flashMode = flashMode,
        cameraMode = cameraMode,
        videoQuality = config.videoQuality
    )

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            factory = { cameraState.previewView },
            modifier = Modifier.fillMaxSize(),
        )

        CameraTopBar(
            flashMode = flashMode,
            showFlash = config.showFlash && cameraMode == CameraMode.PHOTO,
            onFlashModeChange = { newMode ->
                flashMode = newMode
                cameraState.updateFlashMode(newMode)
            },
            onClose = { onResult(CameraResult.Cancelled) },
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
        )

        CameraBottomBar(
            cameraMode = cameraMode,
            isRecording = cameraState.isRecording,
            allowModeSwitch = config.allowModeSwitch,
            showFlipCamera = config.showFlipCamera,
            onModeChange = { mode ->
                cameraMode = mode
                cameraState.updateCameraMode(mode)
            },
            onCapture = {
                when (cameraMode) {
                    CameraMode.PHOTO -> {
                        cameraState.takePicture { bitmap ->
                            onResult(CameraResult.Photo(bitmap))
                        }
                    }
                    CameraMode.VIDEO -> {
                        if (cameraState.isRecording) {
                            cameraState.stopRecording()
                        } else {
                            cameraState.startRecording { uri ->
                                onResult(CameraResult.Video(uri))
                            }
                        }
                    }
                }
            },
            onFlipCamera = {
                lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK) {
                    CameraSelector.LENS_FACING_FRONT
                } else {
                    CameraSelector.LENS_FACING_BACK
                }
                cameraState.updateLensFacing(lensFacing)
            },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
        )

        if (cameraState.isRecording) {
            RecordingIndicator(
                duration = cameraState.recordingDuration,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 80.dp)
            )
        }
    }
}

@Composable
fun CameraTopBar(
    flashMode: Int,
    showFlash: Boolean,
    onFlashModeChange: (Int) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .background(Color.Black.copy(alpha = 0.5f))
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onClose) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Close",
                tint = Color.White
            )
        }

        if (showFlash) {
            IconButton(
                onClick = {
                    val newMode = when (flashMode) {
                        ImageCapture.FLASH_MODE_OFF -> ImageCapture.FLASH_MODE_ON
                        ImageCapture.FLASH_MODE_ON -> ImageCapture.FLASH_MODE_AUTO
                        else -> ImageCapture.FLASH_MODE_OFF
                    }
                    onFlashModeChange(newMode)
                }
            ) {
                Icon(
                    imageVector = when (flashMode) {
                        ImageCapture.FLASH_MODE_ON -> Icons.Default.FlashOn
                        ImageCapture.FLASH_MODE_AUTO -> Icons.Default.FlashAuto
                        else -> Icons.Default.FlashOff
                    },
                    contentDescription = "Flash",
                    tint = Color.White
                )
            }
        } else {
            Spacer(modifier = Modifier.width(48.dp))
        }
    }
}

@Composable
fun CameraBottomBar(
    cameraMode: CameraMode,
    isRecording: Boolean,
    allowModeSwitch: Boolean,
    showFlipCamera: Boolean,
    onModeChange: (CameraMode) -> Unit,
    onCapture: () -> Unit,
    onFlipCamera: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .background(Color.Black.copy(alpha = 0.5f))
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Mode selector
        if (allowModeSwitch) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                TextButton(onClick = { onModeChange(CameraMode.PHOTO) }) {
                    Text(
                        "PHOTO", color = if (cameraMode == CameraMode.PHOTO) Color.White else Color.Gray
                    )
                }
                Spacer(modifier = Modifier.width(24.dp))
                TextButton(onClick = { onModeChange(CameraMode.VIDEO) }) {
                    Text(
                        text = "VIDEO",
                        color = if (cameraMode == CameraMode.VIDEO) Color.White else Color.Gray
                    )
                }
            }
        }

        // Camera controls
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Gallery button (placeholder)
            IconButton(onClick = { /* Open gallery */ }) {
                Icon(
                    imageVector = Icons.Default.PhotoLibrary,
                    contentDescription = "Gallery",
                    tint = Color.White
                )
            }

            // Capture/Record button
            CaptureButton(
                cameraMode = cameraMode,
                isRecording = isRecording,
                onClick = onCapture
            )

            // Flip camera button
            if (showFlipCamera) {
                IconButton(onClick = onFlipCamera) {
                    Icon(
                        imageVector = Icons.Default.Cameraswitch,
                        contentDescription = "Flip Camera",
                        tint = Color.White
                    )
                }
            } else {
                Spacer(modifier = Modifier.width(48.dp))
            }
        }
    }
}

@Composable
fun CaptureButton(
    cameraMode: CameraMode,
    isRecording: Boolean,
    onClick: () -> Unit
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.size(72.dp)
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .background(
                    color = if (cameraMode == CameraMode.VIDEO && isRecording) {
                        Color.Red
                    } else {
                        Color.White
                    },
                    shape = if (cameraMode == CameraMode.VIDEO && isRecording) {
                        MaterialTheme.shapes.small
                    } else {
                        CircleShape
                    }
                )
                .border(
                    width = 4.dp,
                    color = Color.Gray,
                    shape = CircleShape
                )
        )
    }
}

@Composable
fun RecordingIndicator(
    duration: Long,
    modifier: Modifier = Modifier
) {
    val minutes = duration / 60
    val seconds = duration % 60

    Surface(
        modifier = modifier,
        color = Color.Red,
        shape = MaterialTheme.shapes.small
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(Color.White, CircleShape)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = String.format("%02d:%02d", minutes, seconds),
                color = Color.White,
                style = MaterialTheme.typography.h3
            )
        }
    }
}

@Composable
fun rememberCameraState(
    context: Context,
    lifecycleOwner: LifecycleOwner,
    lensFacing: Int,
    flashMode: Int,
    cameraMode: CameraMode,
    videoQuality: Quality
): CameraState {
    return remember {
        CameraState(
            context = context,
            lifecycleOwner = lifecycleOwner,
            initialLensFacing = lensFacing,
            initialFlashMode = flashMode,
            initialCameraMode = cameraMode,
            videoQuality = videoQuality
        )
    }.apply {
        LaunchedEffect(lensFacing, flashMode, cameraMode) {
            rebindCamera(lensFacing, flashMode, cameraMode)
        }
    }
}

class CameraState(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner,
    initialLensFacing: Int,
    initialFlashMode: Int,
    initialCameraMode: CameraMode,
    private val videoQuality: Quality
) {
    val previewView = PreviewView(context)
    private val executor: Executor = ContextCompat.getMainExecutor(context)

    private var cameraProvider: ProcessCameraProvider? = null
    private var camera: Camera? = null
    private var imageCapture: ImageCapture? = null
    private var videoCapture: VideoCapture<Recorder>? = null
    private var recording: Recording? = null

    var isRecording by mutableStateOf(false)
        private set

    var recordingDuration by mutableStateOf(0L)
        private set

    private var recordingTimer: Timer? = null

    init {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            cameraProvider = cameraProviderFuture.get()
            rebindCamera(initialLensFacing, initialFlashMode, initialCameraMode)
        }, executor)
    }

    fun rebindCamera(lensFacing: Int, flashMode: Int, cameraMode: CameraMode) {
        val provider = cameraProvider ?: return

        provider.unbindAll()

        val cameraSelector = CameraSelector.Builder()
            .requireLensFacing(lensFacing)
            .build()

        val preview = Preview.Builder().build().also {
            it.setSurfaceProvider(previewView.surfaceProvider)
        }

        when (cameraMode) {
            CameraMode.PHOTO -> {
                imageCapture = ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                    .setFlashMode(flashMode)
                    .build()

                camera = provider.bindToLifecycle(
                    lifecycleOwner,
                    cameraSelector,
                    preview,
                    imageCapture
                )
            }
            CameraMode.VIDEO -> {
                val recorder = Recorder.Builder()
                    .setQualitySelector(QualitySelector.from(videoQuality))
                    .build()

                videoCapture = VideoCapture.withOutput(recorder)

                camera = provider.bindToLifecycle(
                    lifecycleOwner,
                    cameraSelector,
                    preview,
                    videoCapture
                )
            }
        }
    }

    fun updateLensFacing(lensFacing: Int) {
        // Handled by rebindCamera through LaunchedEffect
    }

    fun updateFlashMode(flashMode: Int) {
        imageCapture?.flashMode = flashMode
    }

    fun updateCameraMode(mode: CameraMode) {
        // Handled by rebindCamera through LaunchedEffect
    }

    fun takePicture(onImageCaptured: (Bitmap) -> Unit) {
        val capture = imageCapture ?: return

        capture.takePicture(
            executor,
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(imageProxy: ImageProxy) {
                    val bitmap = imageProxy.toBitmap()
                    onImageCaptured(bitmap)
                    imageProxy.close()
                }

                override fun onError(exception: ImageCaptureException) {
                    Log.e("CameraState", "Photo capture failed", exception)
                }
            }
        )
    }

    fun startRecording(onVideoRecorded: (Uri) -> Unit) {
        val capture = videoCapture ?: return

        val videoFile = File(
            context.cacheDir,
            "video_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())}.mp4"
        )

        val outputOptions = FileOutputOptions.Builder(videoFile).build()

        recording = capture.output
            .prepareRecording(context, outputOptions)
            .start(executor) { recordEvent ->
                when (recordEvent) {
                    is VideoRecordEvent.Start -> {
                        isRecording = true
                        startTimer()
                    }
                    is VideoRecordEvent.Finalize -> {
                        isRecording = false
                        stopTimer()
                        if (!recordEvent.hasError()) {
                            onVideoRecorded(Uri.fromFile(videoFile))
                        }
                    }
                }
            }
    }

    fun stopRecording() {
        recording?.stop()
        recording = null
    }

    private fun startTimer() {
        recordingDuration = 0
        recordingTimer = Timer()
        recordingTimer?.scheduleAtFixedRate(object : TimerTask() {
            override fun run() {
                recordingDuration++
            }
        }, 1000, 1000)
    }

    private fun stopTimer() {
        recordingTimer?.cancel()
        recordingTimer = null
        recordingDuration = 0
    }
}