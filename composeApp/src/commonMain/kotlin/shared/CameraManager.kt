package shared

import androidx.compose.runtime.Composable

@Composable
expect fun rememberCameraManager(onResult: (SharedMedia?) -> Unit): CameraManager


expect class CameraManager(
    onLaunch: () -> Unit
) {
    fun launch()
}