import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import claude.Crop
import claude.Undo
import shared.SharedMedia
import shared.toImageBitmap
import kotlin.math.abs

enum class EditMode {
    VIEW, CROP, DRAW
}

data class DrawPath(
    val points: List<Offset>,
    val color: Color,
    val strokeWidth: Float
)

enum class CropHandle {
    TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT,
    TOP, BOTTOM, LEFT, RIGHT, CENTER, NONE
}

@Composable
fun ImageEditScreen(
    image: SharedMedia.Image,
    onBack: () -> Unit,
    onDone: (ImageBitmap) -> Unit
) {
    var editMode by remember { mutableStateOf(EditMode.VIEW) }
    var drawColor by remember { mutableStateOf(Color.Red) }
    var brushSize by remember { mutableFloatStateOf(5f) }
    var paths by remember { mutableStateOf<List<DrawPath>>(emptyList()) }
    var currentPath by remember { mutableStateOf<List<Offset>>(emptyList()) }
    var showColorPicker by remember { mutableStateOf(false) }

    // Crop state
    var cropLeft by remember { mutableFloatStateOf(0f) }
    var cropTop by remember { mutableFloatStateOf(0f) }
    var cropRight by remember { mutableFloatStateOf(0f) }
    var cropBottom by remember { mutableFloatStateOf(0f) }

    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    var imageRect by remember { mutableStateOf(Rect.Zero) }
    var activeHandle by remember { mutableStateOf(CropHandle.NONE) }
    var initialCropRect by remember { mutableStateOf(Rect.Zero) }
    var initialTouchPos by remember { mutableStateOf(Offset.Zero) }

    val originalBitmap = remember { image.toImageBitmap() }

    // Track applied bitmap (after crops/edits)
    var workingBitmap by remember { mutableStateOf<ImageBitmap?>(null) }

    // Initialize working bitmap
    LaunchedEffect(originalBitmap) {
        workingBitmap = originalBitmap
    }

    // Calculate display rect for current working bitmap
    fun calculateImageRect(bitmap: ImageBitmap?, size: IntSize): Rect {
        if (bitmap == null || size == IntSize.Zero) return Rect.Zero

        val canvasAspect = size.width.toFloat() / size.height
        val imageAspect = bitmap.width.toFloat() / bitmap.height

        val (width, height) = if (imageAspect > canvasAspect) {
            size.width.toFloat() to size.width / imageAspect
        } else {
            size.height * imageAspect to size.height.toFloat()
        }

        val left = (size.width - width) / 2
        val top = (size.height - height) / 2

        return Rect(Offset(left, top), Size(width, height))
    }

    // Update image rect when canvas size or working bitmap changes
    LaunchedEffect(canvasSize, workingBitmap) {
        if (canvasSize != IntSize.Zero && workingBitmap != null) {
            imageRect = calculateImageRect(workingBitmap, canvasSize)

            // Initialize crop to full image rect
            cropLeft = imageRect.left
            cropTop = imageRect.top
            cropRight = imageRect.right
            cropBottom = imageRect.bottom
        }
    }

    val currentCropRect = Rect(cropLeft, cropTop, cropRight, cropBottom)

    // Function to apply crop
    fun applyCrop() {
        workingBitmap?.let { bitmap ->
            // First, apply any existing drawings to the bitmap
            val bitmapWithDrawings = if (paths.isNotEmpty()) {
                applyDrawings(bitmap, paths, imageRect)
            } else {
                bitmap
            }

            // Calculate crop in bitmap coordinates
            val scaleX = bitmapWithDrawings.width.toFloat() / imageRect.width
            val scaleY = bitmapWithDrawings.height.toFloat() / imageRect.height

            val cropX = ((cropLeft - imageRect.left) * scaleX).toInt().coerceAtLeast(0)
            val cropY = ((cropTop - imageRect.top) * scaleY).toInt().coerceAtLeast(0)
            val cropW = ((cropRight - cropLeft) * scaleX).toInt().coerceAtMost(bitmapWithDrawings.width - cropX)
            val cropH = ((cropBottom - cropTop) * scaleY).toInt().coerceAtMost(bitmapWithDrawings.height - cropY)

            if (cropW > 0 && cropH > 0) {
                // Create cropped bitmap
                val croppedBitmap = ImageBitmap(cropW, cropH)
                val canvas = Canvas(croppedBitmap)

                canvas.drawImageRect(
                    image = bitmapWithDrawings,
                    srcOffset = IntOffset(cropX, cropY),
                    srcSize = IntSize(cropW, cropH),
                    dstOffset = IntOffset.Zero,
                    dstSize = IntSize(cropW, cropH),
                    paint = Paint()
                )

                workingBitmap = croppedBitmap

                // Clear paths since they're now baked into the bitmap
                paths = emptyList()
            }
        }

        editMode = EditMode.VIEW
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Top Bar
            TopAppBar(
                title = {
                    Text(
                        if (editMode == EditMode.CROP) "Crop" else "Edit Photo",
                        color = Color.White
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.Close, "Close", tint = Color.White)
                    }
                },
                actions = {
                    if (editMode == EditMode.CROP) {
                        TextButton(
                            onClick = {
                                cropLeft = imageRect.left
                                cropTop = imageRect.top
                                cropRight = imageRect.right
                                cropBottom = imageRect.bottom
                            }
                        ) {
                            Icon(
                                Icons.Default.Refresh,
                                "Reset",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    TextButton(
                        onClick = {
                            if (editMode == EditMode.CROP) {
                                applyCrop()
                            } else {
                                workingBitmap?.let { bitmap ->
                                    val finalBitmap = applyDrawings(bitmap, paths, imageRect)
                                    onDone(finalBitmap)
                                }
                            }
                        }
                    ) {
                        Text(
                            if (editMode == EditMode.CROP) "Apply" else "Done",
                            color = Color(0xFF2AABEE),
                            style = MaterialTheme.typography.h5
                        )
                    }
                }
            )

            // Canvas Area
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .onGloballyPositioned { coordinates ->
                        canvasSize = coordinates.size
                    }
            ) {
                workingBitmap?.let { bitmap ->
                    Canvas(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(editMode) {
                                detectDragGestures(
                                    onDragStart = { offset ->
                                        when (editMode) {
                                            EditMode.DRAW -> {
                                                currentPath = listOf(offset)
                                            }
                                            EditMode.CROP -> {
                                                activeHandle = detectHandle(offset, currentCropRect)
                                                initialTouchPos = offset
                                                initialCropRect = currentCropRect
                                            }
                                            else -> {}
                                        }
                                    },
                                    onDrag = { change, _ ->
                                        when (editMode) {
                                            EditMode.DRAW -> {
                                                currentPath = currentPath + change.position
                                            }
                                            EditMode.CROP -> {
                                                val dragDelta = change.position - initialTouchPos

                                                when (activeHandle) {
                                                    CropHandle.TOP_LEFT -> {
                                                        cropLeft = (initialCropRect.left + dragDelta.x)
                                                            .coerceIn(imageRect.left, cropRight - 100f)
                                                        cropTop = (initialCropRect.top + dragDelta.y)
                                                            .coerceIn(imageRect.top, cropBottom - 100f)
                                                    }
                                                    CropHandle.TOP_RIGHT -> {
                                                        cropRight = (initialCropRect.right + dragDelta.x)
                                                            .coerceIn(cropLeft + 100f, imageRect.right)
                                                        cropTop = (initialCropRect.top + dragDelta.y)
                                                            .coerceIn(imageRect.top, cropBottom - 100f)
                                                    }
                                                    CropHandle.BOTTOM_LEFT -> {
                                                        cropLeft = (initialCropRect.left + dragDelta.x)
                                                            .coerceIn(imageRect.left, cropRight - 100f)
                                                        cropBottom = (initialCropRect.bottom + dragDelta.y)
                                                            .coerceIn(cropTop + 100f, imageRect.bottom)
                                                    }
                                                    CropHandle.BOTTOM_RIGHT -> {
                                                        cropRight = (initialCropRect.right + dragDelta.x)
                                                            .coerceIn(cropLeft + 100f, imageRect.right)
                                                        cropBottom = (initialCropRect.bottom + dragDelta.y)
                                                            .coerceIn(cropTop + 100f, imageRect.bottom)
                                                    }
                                                    CropHandle.TOP -> {
                                                        cropTop = (initialCropRect.top + dragDelta.y)
                                                            .coerceIn(imageRect.top, cropBottom - 100f)
                                                    }
                                                    CropHandle.BOTTOM -> {
                                                        cropBottom = (initialCropRect.bottom + dragDelta.y)
                                                            .coerceIn(cropTop + 100f, imageRect.bottom)
                                                    }
                                                    CropHandle.LEFT -> {
                                                        cropLeft = (initialCropRect.left + dragDelta.x)
                                                            .coerceIn(imageRect.left, cropRight - 100f)
                                                    }
                                                    CropHandle.RIGHT -> {
                                                        cropRight = (initialCropRect.right + dragDelta.x)
                                                            .coerceIn(cropLeft + 100f, imageRect.right)
                                                    }
                                                    CropHandle.CENTER -> {
                                                        val width = initialCropRect.width
                                                        val height = initialCropRect.height

                                                        var newLeft = initialCropRect.left + dragDelta.x
                                                        var newTop = initialCropRect.top + dragDelta.y

                                                        if (newLeft < imageRect.left) newLeft = imageRect.left
                                                        if (newLeft + width > imageRect.right) newLeft = imageRect.right - width
                                                        if (newTop < imageRect.top) newTop = imageRect.top
                                                        if (newTop + height > imageRect.bottom) newTop = imageRect.bottom - height

                                                        cropLeft = newLeft
                                                        cropTop = newTop
                                                        cropRight = newLeft + width
                                                        cropBottom = newTop + height
                                                    }
                                                    else -> {}
                                                }
                                            }
                                            else -> {}
                                        }
                                    },
                                    onDragEnd = {
                                        when (editMode) {
                                            EditMode.DRAW -> {
                                                if (currentPath.isNotEmpty()) {
                                                    paths = paths + DrawPath(
                                                        currentPath,
                                                        drawColor,
                                                        brushSize
                                                    )
                                                    currentPath = emptyList()
                                                }
                                            }
                                            EditMode.CROP -> {
                                                activeHandle = CropHandle.NONE
                                            }
                                            else -> {}
                                        }
                                    }
                                )
                            }
                    ) {
                        // Draw image
                        drawImage(
                            image = bitmap,
                            dstOffset = IntOffset(imageRect.left.toInt(), imageRect.top.toInt()),
                            dstSize = IntSize(imageRect.width.toInt(), imageRect.height.toInt())
                        )

                        // Draw paths (only if not in crop mode)
                        if (editMode != EditMode.CROP) {
                            paths.forEach { path ->
                                if (path.points.size > 1) {
                                    drawPath(
                                        path = Path().apply {
                                            moveTo(path.points[0].x, path.points[0].y)
                                            path.points.forEach { point ->
                                                lineTo(point.x, point.y)
                                            }
                                        },
                                        color = path.color,
                                        style = Stroke(
                                            width = path.strokeWidth,
                                            cap = StrokeCap.Round,
                                            join = StrokeJoin.Round
                                        )
                                    )
                                }
                            }

                            // Draw current path
                            if (currentPath.size > 1) {
                                drawPath(
                                    path = Path().apply {
                                        moveTo(currentPath[0].x, currentPath[0].y)
                                        currentPath.forEach { point ->
                                            lineTo(point.x, point.y)
                                        }
                                    },
                                    color = drawColor,
                                    style = Stroke(
                                        width = brushSize,
                                        cap = StrokeCap.Round,
                                        join = StrokeJoin.Round
                                    )
                                )
                            }
                        }

                        // Draw crop overlay
                        if (editMode == EditMode.CROP) {
                            val rect = currentCropRect

                            // Dark overlay
                            drawRect(
                                color = Color.Black.copy(alpha = 0.7f),
                                topLeft = Offset.Zero,
                                size = Size(size.width, rect.top)
                            )
                            drawRect(
                                color = Color.Black.copy(alpha = 0.7f),
                                topLeft = Offset(0f, rect.bottom),
                                size = Size(size.width, size.height - rect.bottom)
                            )
                            drawRect(
                                color = Color.Black.copy(alpha = 0.7f),
                                topLeft = Offset(0f, rect.top),
                                size = Size(rect.left, rect.height)
                            )
                            drawRect(
                                color = Color.Black.copy(alpha = 0.7f),
                                topLeft = Offset(rect.right, rect.top),
                                size = Size(size.width - rect.right, rect.height)
                            )

                            // White border
                            drawRect(
                                color = Color.White,
                                topLeft = rect.topLeft,
                                size = rect.size,
                                style = Stroke(width = 2.dp.toPx())
                            )

                            // Grid lines
                            val gridColor = Color.White.copy(alpha = 0.5f)
                            for (i in 1..2) {
                                val x = rect.left + (rect.width / 3) * i
                                drawLine(
                                    color = gridColor,
                                    start = Offset(x, rect.top),
                                    end = Offset(x, rect.bottom),
                                    strokeWidth = 1f
                                )
                                val y = rect.top + (rect.height / 3) * i
                                drawLine(
                                    color = gridColor,
                                    start = Offset(rect.left, y),
                                    end = Offset(rect.right, y),
                                    strokeWidth = 1f
                                )
                            }

                            // Corner and edge handles
                            val handleThickness = 4.dp.toPx()
                            val handleLength = 24.dp.toPx()
                            val handleColor = Color.White

                            // Top-left
                            drawLine(handleColor, Offset(rect.left - handleThickness/2, rect.top),
                                Offset(rect.left + handleLength, rect.top), handleThickness, StrokeCap.Round)
                            drawLine(handleColor, Offset(rect.left, rect.top - handleThickness/2),
                                Offset(rect.left, rect.top + handleLength), handleThickness, StrokeCap.Round)

                            // Top-right
                            drawLine(handleColor, Offset(rect.right + handleThickness/2, rect.top),
                                Offset(rect.right - handleLength, rect.top), handleThickness, StrokeCap.Round)
                            drawLine(handleColor, Offset(rect.right, rect.top - handleThickness/2),
                                Offset(rect.right, rect.top + handleLength), handleThickness, StrokeCap.Round)

                            // Bottom-left
                            drawLine(handleColor, Offset(rect.left - handleThickness/2, rect.bottom),
                                Offset(rect.left + handleLength, rect.bottom), handleThickness, StrokeCap.Round)
                            drawLine(handleColor, Offset(rect.left, rect.bottom + handleThickness/2),
                                Offset(rect.left, rect.bottom - handleLength), handleThickness, StrokeCap.Round)

                            // Bottom-right
                            drawLine(handleColor, Offset(rect.right + handleThickness/2, rect.bottom),
                                Offset(rect.right - handleLength, rect.bottom), handleThickness, StrokeCap.Round)
                            drawLine(handleColor, Offset(rect.right, rect.bottom + handleThickness/2),
                                Offset(rect.right, rect.bottom - handleLength), handleThickness, StrokeCap.Round)

                            // Edge handles
                            val edgeSize = 5.dp.toPx()
                            val edgeLength = 40.dp.toPx()
                            drawLine(handleColor, Offset(rect.left + rect.width/2 - edgeLength/2, rect.top),
                                Offset(rect.left + rect.width/2 + edgeLength/2, rect.top), edgeSize, StrokeCap.Round)
                            drawLine(handleColor, Offset(rect.left + rect.width/2 - edgeLength/2, rect.bottom),
                                Offset(rect.left + rect.width/2 + edgeLength/2, rect.bottom), edgeSize, StrokeCap.Round)
                            drawLine(handleColor, Offset(rect.left, rect.top + rect.height/2 - edgeLength/2),
                                Offset(rect.left, rect.top + rect.height/2 + edgeLength/2), edgeSize, StrokeCap.Round)
                            drawLine(handleColor, Offset(rect.right, rect.top + rect.height/2 - edgeLength/2),
                                Offset(rect.right, rect.top + rect.height/2 + edgeLength/2), edgeSize, StrokeCap.Round)
                        }
                    }
                }
            }

            // Bottom Toolbar
            Surface(
                color = Color(0xFF1C1C1E),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    if (showColorPicker && editMode == EditMode.DRAW) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            listOf(
                                Color.Red, Color(0xFFFF6B00), Color.Yellow, Color.Green,
                                Color.Cyan, Color.Blue, Color(0xFF9C27B0), Color.White
                            ).forEach { color ->
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .background(color)
                                        .border(
                                            width = if (color == drawColor) 3.dp else 1.dp,
                                            color = if (color == drawColor) Color(0xFF2AABEE) else Color.Gray,
                                            shape = CircleShape
                                        )
                                        .clickable {
                                            drawColor = color
                                            showColorPicker = false
                                        }
                                )
                            }
                        }
                    }

                    if (editMode != EditMode.CROP) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            ToolButton(
                                icon = Crop,
                                label = "Crop",
                                isSelected = false,
                                onClick = {
                                    editMode = EditMode.CROP
                                    showColorPicker = false
                                }
                            )

                            ToolButton(
                                icon = Icons.Default.Edit,
                                label = "Draw",
                                isSelected = editMode == EditMode.DRAW,
                                onClick = {
                                    editMode = if (editMode == EditMode.DRAW) EditMode.VIEW else EditMode.DRAW
                                }
                            )

                            if (editMode == EditMode.DRAW) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier.clickable { showColorPicker = !showColorPicker }
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(28.dp)
                                            .clip(CircleShape)
                                            .background(drawColor)
                                            .border(2.dp, Color.White, CircleShape)
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        "Color",
                                        color = if (showColorPicker) Color(0xFF2AABEE) else Color.Gray,
                                        style = MaterialTheme.typography.h5
                                    )
                                }
                            }

                            if (paths.isNotEmpty()) {
                                ToolButton(
                                    icon = Undo,
                                    label = "Undo",
                                    isSelected = false,
                                    onClick = { paths = paths.dropLast(1) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ToolButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (isSelected) Color(0xFF2AABEE) else Color.White,
            modifier = Modifier.size(28.dp)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            color = if (isSelected) Color(0xFF2AABEE) else Color.Gray,
            style = MaterialTheme.typography.h5
        )
    }
}

fun detectHandle(position: Offset, rect: Rect): CropHandle {
    val touchRadius = 50f

    if (abs(position.x - rect.left) < touchRadius && abs(position.y - rect.top) < touchRadius) {
        return CropHandle.TOP_LEFT
    }
    if (abs(position.x - rect.right) < touchRadius && abs(position.y - rect.top) < touchRadius) {
        return CropHandle.TOP_RIGHT
    }
    if (abs(position.x - rect.left) < touchRadius && abs(position.y - rect.bottom) < touchRadius) {
        return CropHandle.BOTTOM_LEFT
    }
    if (abs(position.x - rect.right) < touchRadius && abs(position.y - rect.bottom) < touchRadius) {
        return CropHandle.BOTTOM_RIGHT
    }

    if (abs(position.x - rect.left) < touchRadius && position.y > rect.top + touchRadius && position.y < rect.bottom - touchRadius) {
        return CropHandle.LEFT
    }
    if (abs(position.x - rect.right) < touchRadius && position.y > rect.top + touchRadius && position.y < rect.bottom - touchRadius) {
        return CropHandle.RIGHT
    }
    if (abs(position.y - rect.top) < touchRadius && position.x > rect.left + touchRadius && position.x < rect.right - touchRadius) {
        return CropHandle.TOP
    }
    if (abs(position.y - rect.bottom) < touchRadius && position.x > rect.left + touchRadius && position.x < rect.right - touchRadius) {
        return CropHandle.BOTTOM
    }

    if (rect.contains(position)) {
        return CropHandle.CENTER
    }

    return CropHandle.NONE
}

fun applyDrawings(
    bitmap: ImageBitmap,
    paths: List<DrawPath>,
    imageRect: Rect
): ImageBitmap {
    if (paths.isEmpty()) return bitmap

    val resultBitmap = ImageBitmap(bitmap.width, bitmap.height)
    val canvas = Canvas(resultBitmap)

    // Draw original bitmap
    canvas.drawImage(bitmap, Offset.Zero, Paint())

    // Calculate scale from display to bitmap coordinates
    val scaleX = bitmap.width.toFloat() / imageRect.width
    val scaleY = bitmap.height.toFloat() / imageRect.height

    // Draw paths in bitmap coordinates
    paths.forEach { path ->
        if (path.points.size > 1) {
            val scaledPath = Path().apply {
                val firstPoint = path.points[0]
                val scaledFirst = Offset(
                    (firstPoint.x - imageRect.left) * scaleX,
                    (firstPoint.y - imageRect.top) * scaleY
                )
                moveTo(scaledFirst.x, scaledFirst.y)

                path.points.drop(1).forEach { point ->
                    val scaledPoint = Offset(
                        (point.x - imageRect.left) * scaleX,
                        (point.y - imageRect.top) * scaleY
                    )
                    lineTo(scaledPoint.x, scaledPoint.y)
                }
            }

            canvas.drawPath(
                scaledPath,
                Paint().apply {
                    color = path.color
                    strokeWidth = path.strokeWidth * scaleX
                    style = PaintingStyle.Stroke
                    strokeCap = StrokeCap.Round
                    strokeJoin = StrokeJoin.Round
                }
            )
        }
    }

    return resultBitmap
}