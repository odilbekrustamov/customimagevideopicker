package shared

import androidx.compose.runtime.Composable

@Composable
expect fun rememberGalleryManager(onResult: (SharedMedia?) -> Unit): GalleryManager


expect class GalleryManager(
    onLaunch: () -> Unit
) {
    fun launch()
}