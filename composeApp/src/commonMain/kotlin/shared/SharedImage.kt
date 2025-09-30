package shared

import androidx.compose.ui.graphics.ImageBitmap

enum class MediaType {
    IMAGE,
    VIDEO
}

sealed class SharedMedia {
    abstract val mediaType: MediaType

    data class Image(
        val bitmap: Any?,
        val byteArray: ByteArray? = null
    ) : SharedMedia() {
        override val mediaType = MediaType.IMAGE

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Image) return false
            if (bitmap != other.bitmap) return false
            if (byteArray != null) {
                if (other.byteArray == null) return false
                if (!byteArray.contentEquals(other.byteArray)) return false
            } else if (other.byteArray != null) return false
            return true
        }

        override fun hashCode(): Int {
            var result = bitmap?.hashCode() ?: 0
            result = 31 * result + (byteArray?.contentHashCode() ?: 0)
            return result
        }
    }

    data class Video(
        val uri: String,
        val thumbnail: Any? = null,
        val duration: Long = 0L,
        val size: Long = 0L
    ) : SharedMedia() {
        override val mediaType = MediaType.VIDEO
    }
}

expect fun SharedMedia.Image.toByteArray(): ByteArray?
expect fun SharedMedia.Image.toImageBitmap(): ImageBitmap?
expect fun SharedMedia.Video.getThumbnailBitmap(): ImageBitmap?