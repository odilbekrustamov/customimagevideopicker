import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import claude.PlayerPlay
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.painterResource
import shared.SharedMedia
import shared.getThumbnailBitmap
import shared.toImageBitmap

@OptIn(ExperimentalResourceApi::class)
@Composable
fun MainScreen(
    mediaContent: SharedMedia?,
    onImageClick: (SharedMedia.Image) -> Unit,
    onVideoClick: (SharedMedia.Video?) -> Unit,
) {
    Box(
        modifier = Modifier.fillMaxSize().background(Color.DarkGray),
        contentAlignment = Alignment.Center
    ) {
        when (mediaContent) {
            is SharedMedia.Image -> {
                mediaContent.toImageBitmap()?.let { imageBitmap ->
                    Image(
                        bitmap = imageBitmap,
                        contentDescription = "Profile",
                        modifier = Modifier.size(100.dp).clip(CircleShape).clickable {
                            onImageClick(mediaContent) // rasm bo‘lsa edit screen
                        },
                        contentScale = ContentScale.Crop
                    )
                }
            }
            is SharedMedia.Video -> {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.clickable { onVideoClick(mediaContent) } // video shu ekranda
                ) {
                    mediaContent.getThumbnailBitmap()?.let { thumbnail ->
                        Box {
                            Image(
                                bitmap = thumbnail,
                                contentDescription = "Video thumbnail",
                                modifier = Modifier.size(100.dp).clip(CircleShape),
                                contentScale = ContentScale.Crop
                            )
                            Icon(
                                imageVector = PlayerPlay,
                                contentDescription = "Play",
                                modifier = Modifier.size(40.dp).align(Alignment.Center),
                                tint = Color.White
                            )
                        }
                    }
                    Text(
                        text = formatDuration(mediaContent.duration),
                        color = Color.White,
                        fontSize = 12.sp
                    )
                }
            }
            null -> {
                Image(
                    modifier = Modifier
                        .size(100.dp)
                        .clip(CircleShape)
                        .clickable{
                            onVideoClick(null)
                                  },
                    painter = painterResource("ic_person_circle.xml"),
                    contentDescription = "Profile",
                )
            }
        }
    }
}