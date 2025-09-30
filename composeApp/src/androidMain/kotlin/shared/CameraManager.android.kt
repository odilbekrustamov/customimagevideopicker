package shared

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import kmp.image.picker.CameraActivity
import androidx.core.net.toUri

@Composable
actual fun rememberCameraManager(onResult: (SharedMedia?) -> Unit): CameraManager {
    val context = LocalContext.current

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val data = result.data
            val mediaType = data?.getStringExtra(CameraActivity.EXTRA_MEDIA_TYPE)

            when (mediaType) {
                "image" -> {
                    data.getStringExtra(CameraActivity.EXTRA_IMAGE_URI)?.let { uriString ->
                        try {
                            val uri = uriString.toUri()
                            val bitmap = loadBitmapFromUri(context, uri)
                            onResult(SharedMedia.Image(bitmap))
                        } catch (e: Exception) {
                            e.printStackTrace()
                            onResult(null)
                        }
                    }
                }

                "video" -> {
                    data.getStringExtra(CameraActivity.EXTRA_VIDEO_URI)?.let { videoUri ->
                        val duration = data.getLongExtra(CameraActivity.EXTRA_VIDEO_DURATION, 0L)

                        val thumbnail = data.getStringExtra(CameraActivity.EXTRA_VIDEO_THUMBNAIL_URI)?.let { thumbUri ->
                            try {
                                loadBitmapFromUri(context, thumbUri.toUri())
                            } catch (e: Exception) {
                                null
                            }
                        }

                        onResult(SharedMedia.Video(uri = videoUri, thumbnail = thumbnail, duration = duration))
                    }
                }

                else -> onResult(null)
            }
        } else {
            onResult(null)
        }
    }

    return remember {
        CameraManager(
            onLaunch = {
                launcher.launch(Intent(context, CameraActivity::class.java))
            }
        )
    }
}

// Helper function to load bitmap from URI
private fun loadBitmapFromUri(context: android.content.Context, uri: Uri): Bitmap? {
    return try {
        context.contentResolver.openInputStream(uri)?.use { inputStream ->
            BitmapFactory.decodeStream(inputStream)
        }
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}

actual class CameraManager actual constructor(
    private val onLaunch: () -> Unit
) {
    actual fun launch() {
        onLaunch()
    }
}

//@Composable
//actual fun rememberCameraManager(onResult: (SharedImage?) -> Unit): CameraManager {
//    val context = LocalContext.current
//    val contentResolver: ContentResolver = context.contentResolver
//    var tempPhotoUri by remember { mutableStateOf(value = Uri.EMPTY) }
//    val cameraLauncher = rememberLauncherForActivityResult(
//        contract = ActivityResultContracts.CaptureVideo(),
//        onResult = { success ->
//            if (success) {
//                onResult.invoke(SharedImage(BitmapUtils.getBitmapFromUri(tempPhotoUri, contentResolver)))
//            }
//        }
//    )
//    return remember {
//        CameraManager(
//            onLaunch = {
//                tempPhotoUri = ComposeFileProvider.getImageUri(context)
//                cameraLauncher.launch(tempPhotoUri)
//            }
//        )
//    }
//}
//
//actual class CameraManager actual constructor(
//    private val onLaunch: () -> Unit
//) {
//    actual fun launch() {
//        onLaunch()
//    }
//}