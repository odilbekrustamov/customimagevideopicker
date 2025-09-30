package shared

import android.graphics.Bitmap
import android.net.Uri
import androidx.camera.video.Quality

sealed class CameraResult {
    data class Photo(val bitmap: Bitmap) : CameraResult()
    data class Video(val uri: Uri) : CameraResult()
    object Cancelled : CameraResult()
}

enum class CameraMode {
    PHOTO, VIDEO
}

data class CameraConfig(
    val initialMode: CameraMode = CameraMode.PHOTO,
    val allowModeSwitch: Boolean = true,
    val showFlipCamera: Boolean = true,
    val showFlash: Boolean = true,
    val showGrid: Boolean = false,
    val videoQuality: Quality = Quality.HD,
    val imageQuality: Int = 95
)