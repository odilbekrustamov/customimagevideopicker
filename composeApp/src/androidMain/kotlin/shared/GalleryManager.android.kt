package shared

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.media.ThumbnailUtils
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Size
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import java.io.File

@RequiresApi(Build.VERSION_CODES.Q)
@Composable
actual fun rememberGalleryManager(onResult: (SharedMedia?) -> Unit): GalleryManager {
    val context = LocalContext.current
    val contentResolver: ContentResolver = context.contentResolver

    val galleryLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            uri?.let {
                val mimeType = contentResolver.getType(uri) ?: ""
                when {
                    mimeType.startsWith("image/") -> {
                        val bitmap = BitmapUtils.getBitmapFromUri(uri, contentResolver)
                        onResult.invoke(
                            SharedMedia.Image(bitmap)
                        )
                    }

                    mimeType.startsWith("video/") -> {
                        val cursor = contentResolver.query(
                            uri,
                            arrayOf(
                                MediaStore.Video.Media.DURATION,
                                MediaStore.Video.Media.SIZE
                            ),
                            null,
                            null,
                            null
                        )
                        var duration = 0L
                        var size = 0L
                        cursor?.use {
                            if (it.moveToFirst()) {
                                duration = it.getLong(0)
                                size = it.getLong(1)
                            }
                        }

                        val thumbnail = createVideoThumbnail(context, uri)

                        onResult.invoke(
                            SharedMedia.Video(
                                uri = uri.toString(),
                                thumbnail = thumbnail,
                                duration = duration,
                                size = size
                            )
                        )
                    }

                    else -> onResult.invoke(null)
                }
            } ?: onResult.invoke(null)
        }

    return remember {
        GalleryManager(onLaunch = {
            galleryLauncher.launch(
                PickVisualMediaRequest(
                    mediaType = ActivityResultContracts.PickVisualMedia.ImageAndVideo
                )
            )
        })
    }
}

private fun createVideoThumbnail(context: Context, uri: Uri): Bitmap? {
    return try {
        val retriever = MediaMetadataRetriever()
        retriever.setDataSource(context, uri)
        val bitmap = retriever.getFrameAtTime(0)
        retriever.release()
        bitmap
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}

actual class GalleryManager actual constructor(private val onLaunch: () -> Unit) {
    actual fun launch() {
        onLaunch()
    }
}