import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import claude.PlayerPlay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.painterResource
import shared.PermissionCallback
import shared.PermissionStatus
import shared.PermissionType
import shared.SharedMedia
import shared.createPermissionsManager
import shared.getThumbnailBitmap
import shared.rememberCameraManager
import shared.rememberGalleryManager
import shared.toImageBitmap

@OptIn(ExperimentalResourceApi::class)
@Composable
fun App() {
    MaterialTheme {
        val coroutineScope = rememberCoroutineScope()
        var mediaContent by remember { mutableStateOf<SharedMedia?>(null) }
        var imageSourceOptionDialog by remember { mutableStateOf(value = false) }
        var launchCamera by remember { mutableStateOf(value = false) }
        var launchGallery by remember { mutableStateOf(value = false) }
        var launchSetting by remember { mutableStateOf(value = false) }
        var permissionRationalDialog by remember { mutableStateOf(value = false) }
        val permissionsManager = createPermissionsManager(object : PermissionCallback {
            override fun onPermissionStatus(
                permissionType: PermissionType,
                status: PermissionStatus
            ) {
                when (status) {
                    PermissionStatus.GRANTED -> {
                        when (permissionType) {
                            PermissionType.CAMERA -> launchCamera = true
                            PermissionType.GALLERY -> launchGallery = true
                        }
                    }

                    else -> {
                        permissionRationalDialog = true
                    }
                }
            }


        })

        val cameraManager = rememberCameraManager { media ->
            coroutineScope.launch {
                mediaContent = media
            }
        }

        val galleryManager = rememberGalleryManager { media ->
            coroutineScope.launch {
                mediaContent = media
            }
        }

        if (imageSourceOptionDialog) {
            ImageSourceOptionDialog(onDismissRequest = {
                imageSourceOptionDialog = false
            }, onGalleryRequest = {
                imageSourceOptionDialog = false
                launchGallery = true
            }, onCameraRequest = {
                imageSourceOptionDialog = false
                launchCamera = true
            })
        }
        if (launchGallery) {
            if (permissionsManager.isPermissionGranted(PermissionType.GALLERY)) {
                galleryManager.launch()
            } else {
                permissionsManager.askPermission(PermissionType.GALLERY)
            }
            launchGallery = false
        }
        if (launchCamera) {
            if (permissionsManager.isPermissionGranted(PermissionType.CAMERA)) {

                cameraManager.launch()
            } else {
                permissionsManager.askPermission(PermissionType.CAMERA)
            }
            launchCamera = false
        }
        if (launchSetting) {
            permissionsManager.launchSettings()
            launchSetting = false
        }
        if (permissionRationalDialog) {
            AlertMessageDialog(title = "Permission Required",
                message = "To set your profile picture, please grant this permission. You can manage permissions in your device settings.",
                positiveButtonText = "Settings",
                negativeButtonText = "Cancel",
                onPositiveClick = {
                    permissionRationalDialog = false
                    launchSetting = true

                },
                onNegativeClick = {
                    permissionRationalDialog = false
                })

        }
        Box(
            modifier = Modifier.fillMaxSize().background(Color.DarkGray),
            contentAlignment = Alignment.Center
        ) {
            when (val media = mediaContent) {
                is SharedMedia.Image -> {
                    // Display image
                    media.toImageBitmap()?.let { imageBitmap ->
                        Image(
                            bitmap = imageBitmap,
                            contentDescription = "Profile",
                            modifier = Modifier.size(100.dp).clip(CircleShape).clickable {
                                imageSourceOptionDialog = true
                            },
                            contentScale = ContentScale.Crop
                        )
                    }
                }

                is SharedMedia.Video -> {
                    // Display video thumbnail with play icon
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.clickable { imageSourceOptionDialog = true }
                    ) {
                        media.getThumbnailBitmap()?.let { thumbnail ->
                            Box {
                                Image(
                                    bitmap = thumbnail,
                                    contentDescription = "Video thumbnail",
                                    modifier = Modifier.size(100.dp).clip(CircleShape),
                                    contentScale = ContentScale.Crop
                                )
                                // Play icon overlay
                                Icon(
                                    imageVector = PlayerPlay,
                                    contentDescription = "Play",
                                    modifier = Modifier.size(40.dp).align(Alignment.Center),
                                    tint = Color.White
                                )
                            }
                        }

                        // Show duration
                        Text(
                            text = formatDuration(media.duration),
                            color = Color.White,
                            fontSize = 12.sp
                        )
                    }
                }

                null -> {
                    // Show default placeholder
                    Image(
                        modifier = Modifier.size(100.dp).clip(CircleShape).clickable {
                            imageSourceOptionDialog = true
                        },
                        painter = painterResource("ic_person_circle.xml"),
                        contentDescription = "Profile",
                    )
                }
            }
        }
    }
}

fun formatDuration(milliseconds: Long): String {
    val seconds = milliseconds / 1000
    val minutes = seconds / 60
    val remainingSeconds = seconds % 60
    return "${minutes.toString().padStart(2, '0')}:${remainingSeconds.toString().padStart(2, '0')}"
}