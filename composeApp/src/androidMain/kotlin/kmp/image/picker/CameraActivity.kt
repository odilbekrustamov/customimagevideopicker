package kmp.image.picker

import android.content.Intent
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.content.FileProvider
import shared.CameraConfig
import shared.CameraResult
import shared.CustomCameraScreen
import java.io.File
import java.io.FileOutputStream

class CameraActivity : ComponentActivity() {
    companion object {
        const val EXTRA_IMAGE_URI = "image_uri"
        const val EXTRA_VIDEO_URI = "video_uri"
        const val EXTRA_VIDEO_DURATION = "video_duration"
        const val EXTRA_VIDEO_THUMBNAIL_URI = "video_thumbnail_uri"
        const val EXTRA_MEDIA_TYPE = "media_type"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            CustomCameraScreen(
                config = CameraConfig(),
                onResult = { result ->
                    when (result) {
                        is CameraResult.Photo -> {
                            try {
                                val imageFile = saveBitmapToFile(result.bitmap)
                                val imageUri = FileProvider.getUriForFile(
                                    this,
                                    "${packageName}.fileprovider",
                                    imageFile
                                )

                                setResult(RESULT_OK, Intent().apply {
                                    putExtra(EXTRA_IMAGE_URI, imageUri.toString())
                                    putExtra(EXTRA_MEDIA_TYPE, "image")
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                })
                            } catch (e: Exception) {
                                e.printStackTrace()
                                setResult(RESULT_CANCELED)
                            }
                        }

                        is CameraResult.Video -> {
                            try {
                                val duration = getVideoDuration(result.uri)
                                val thumbnail = getVideoThumbnail(result.uri)

                                val thumbnailUri = thumbnail?.let { bitmap ->
                                    val thumbnailFile = saveBitmapToFile(bitmap, "thumbnail_")
                                    FileProvider.getUriForFile(
                                        this,
                                        "${packageName}.fileprovider",
                                        thumbnailFile
                                    )
                                }

                                setResult(RESULT_OK, Intent().apply {
                                    putExtra(EXTRA_VIDEO_URI, result.uri.toString())
                                    putExtra(EXTRA_VIDEO_DURATION, duration)
                                    thumbnailUri?.let {
                                        putExtra(EXTRA_VIDEO_THUMBNAIL_URI, it.toString())
                                    }
                                    putExtra(EXTRA_MEDIA_TYPE, "video")
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                })
                            } catch (e: Exception) {
                                e.printStackTrace()
                                setResult(RESULT_CANCELED)
                            }
                        }

                        CameraResult.Cancelled -> {
                            setResult(RESULT_CANCELED)
                        }
                    }
                    finish()
                }
            )
        }
    }

    private fun saveBitmapToFile(bitmap: Bitmap, prefix: String = "image_"): File {
        val fileName = "${prefix}${System.currentTimeMillis()}.jpg"
        val file = File(cacheDir, fileName)

        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
        }

        return file
    }

    private fun getVideoDuration(uri: Uri): Long {
        return try {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(this, uri)
            val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            retriever.release()
            duration?.toLongOrNull() ?: 0L
        } catch (e: Exception) {
            0L
        }
    }

    private fun getVideoThumbnail(uri: Uri): Bitmap? {
        return try {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(this, uri)
            val bitmap = retriever.frameAtTime
            retriever.release()
            bitmap
        } catch (e: Exception) {
            null
        }
    }
}