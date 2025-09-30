package shared

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import java.io.ByteArrayOutputStream


actual fun SharedMedia.Image.toByteArray(): ByteArray? {
    byteArray?.let { return it }

    return if (bitmap is Bitmap) {
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 90, stream)
        stream.toByteArray()
    } else {
        null
    }
}

actual fun SharedMedia.Image.toImageBitmap(): ImageBitmap? {
    return when {
        bitmap is Bitmap -> bitmap.asImageBitmap()

        byteArray != null -> {
            BitmapFactory.decodeByteArray(byteArray, 0, byteArray.size)?.asImageBitmap()
        }

        else -> null
    }
}

actual fun SharedMedia.Video.getThumbnailBitmap(): ImageBitmap? {
    return when {
        thumbnail is Bitmap -> thumbnail.asImageBitmap()
        else -> {
            try {
                val retriever = MediaMetadataRetriever()
                retriever.setDataSource(uri)
                val bitmap = retriever.frameAtTime
                retriever.release()
                bitmap?.asImageBitmap()
            } catch (e: Exception) {
                null
            }
        }
    }
}