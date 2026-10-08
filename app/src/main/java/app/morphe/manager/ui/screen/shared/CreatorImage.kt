/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.shared

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.net.Uri
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toAndroidRectF
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import androidx.core.graphics.createBitmap
import androidx.core.graphics.scale
import androidx.documentfile.provider.DocumentFile
import app.morphe.manager.util.rememberImagePicker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.abs
import kotlin.math.roundToInt

/** Folder the creators write into, inside the one the user picks. */
private const val BRANDING_FOLDER_NAME = "morphe_branding"

// Offsets are shares of the frame, so a picture can at most center on its edge
private const val MAX_OFFSET = 0.5f
private const val SNAP_THRESHOLD = 0.02f

// Alpha above which a pixel counts as part of the picture rather than its faint margin
private const val CONTENT_ALPHA_THRESHOLD = 32

/** The point in the middle of a box, where pictures meet the box they are fitted into by default. */
val CenterAnchor = Offset(0.5f, 0.5f)

/** A picture picked for a creator, with what the creators need to know about it, worked out once. */
class PickedImage(val bitmap: Bitmap) {
    val image: ImageBitmap = bitmap.asImageBitmap()
    val size = IntSize(bitmap.width, bitmap.height)
    val bounds = Rect(Offset.Zero, size.toSize())
    val isOpaque = !bitmap.hasTransparentPixels()

    /** The visible part, so margins baked into the file do not shrink a picture fitted by it. */
    val content: Rect = if (isOpaque) bounds else bitmap.contentBounds() ?: bounds
}

/** [rememberImagePicker] whose picture is analyzed off the main thread before [onPicked] gets it. */
@Composable
fun rememberPickedImagePicker(onPicked: (PickedImage) -> Unit): () -> Unit {
    val scope = rememberCoroutineScope()
    val currentOnPicked by rememberUpdatedState(onPicked)
    return rememberImagePicker { bitmap ->
        scope.launch { currentOnPicked(withContext(Dispatchers.Default) { PickedImage(bitmap) }) }
    }
}

/** Where a creator places a picture: its scale, and its offsets as shares of the frame it is drawn in. */
@Immutable
data class ImageTransform(
    val scale: Float = 1f,
    val offsetX: Float = 0f,
    val offsetY: Float = 0f
) {
    /**
     * Bounds of a [size] picture in a [frame]. At scale 1 its [fitted] part fills [box] as far as its
     * proportions allow, the two meeting at [anchor], a point given as shares of each.
     */
    fun place(frame: Size, box: Rect, size: IntSize, fitted: Rect, anchor: Offset = CenterAnchor): Rect {
        val factor = scale * minOf(box.width / fitted.width, box.height / fitted.height)
        val target = box.pointAt(anchor) + Offset(offsetX * frame.width, offsetY * frame.height)
        return Rect(target - fitted.pointAt(anchor) * factor, size.toSize() * factor)
    }

    /** This transform zoomed by [zoom] within [scaleRange] and moved to [offset], snapping near its anchor. */
    fun movedTo(offset: Offset, zoom: Float, scaleRange: ClosedFloatingPointRange<Float>) = ImageTransform(
        scale = (scale * zoom).coerceIn(scaleRange),
        offsetX = offset.x.snapped(),
        offsetY = offset.y.snapped()
    )
}

/** The point of this rectangle at [anchor], given as shares of its size. */
fun Rect.pointAt(anchor: Offset) = Offset(left + width * anchor.x, top + height * anchor.y)

private fun Float.snapped() = if (abs(this) < SNAP_THRESHOLD) 0f else coerceInOffsetRange()

private fun Float.coerceInOffsetRange() = coerceIn(-MAX_OFFSET, MAX_OFFSET)

/**
 * Drag moves and pinch zooms [transform] within [scaleRange]. The gesture follows the finger and only
 * what is shown snaps, so a picture snapped onto its anchor can still be pulled off it.
 * [onGestureChange] hears when a gesture starts and ends.
 */
@Composable
fun Modifier.imageTransformGestures(
    transform: ImageTransform,
    scaleRange: ClosedFloatingPointRange<Float>,
    onTransformChange: (ImageTransform) -> Unit,
    onGestureChange: (Boolean) -> Unit
): Modifier {
    val currentTransform by rememberUpdatedState(transform)
    val currentScaleRange by rememberUpdatedState(scaleRange)
    val currentOnTransformChange by rememberUpdatedState(onTransformChange)
    val currentOnGestureChange by rememberUpdatedState(onGestureChange)

    return pointerInput(Unit) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false)
            currentOnGestureChange(true)
            var offset = Offset(currentTransform.offsetX, currentTransform.offsetY)
            do {
                val event = awaitPointerEvent()
                val pan = event.calculatePan()
                val zoom = event.calculateZoom()
                if (pan != Offset.Zero || zoom != 1f) {
                    offset = Offset(
                        (offset.x + pan.x / size.width).coerceInOffsetRange(),
                        (offset.y + pan.y / size.height).coerceInOffsetRange()
                    )
                    currentOnTransformChange(currentTransform.movedTo(offset, zoom, currentScaleRange))
                    event.changes.forEach { if (it.positionChanged()) it.consume() }
                }
            } while (event.changes.any { it.pressed })
            currentOnGestureChange(false)
        }
    }
}

/** Stroke of the dashed guides drawn over a creator's editor. */
fun Density.dashedGuideStroke() = Stroke(
    width = 1.5.dp.toPx(),
    pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 5.dp.toPx()))
)

/** Lines through [anchor] along each axis [transform] sits snapped on. */
fun DrawScope.drawSnapGuides(transform: ImageTransform, anchor: Offset, color: Color, stroke: Stroke) {
    if (transform.offsetX == 0f) {
        drawLine(color, Offset(anchor.x, 0f), Offset(anchor.x, size.height), stroke.width, pathEffect = stroke.pathEffect)
    }
    if (transform.offsetY == 0f) {
        drawLine(color, Offset(0f, anchor.y), Offset(size.width, anchor.y), stroke.width, pathEffect = stroke.pathEffect)
    }
}

/** Draws [image] into [bounds], recolored to [tint] where it is not transparent. */
fun DrawScope.drawPicture(image: ImageBitmap, bounds: Rect, tint: Color? = null) {
    drawImage(
        image = image,
        dstOffset = IntOffset(bounds.left.roundToInt(), bounds.top.roundToInt()),
        dstSize = IntSize(bounds.width.roundToInt(), bounds.height.roundToInt()),
        colorFilter = tint?.let { ColorFilter.tint(it, BlendMode.SrcIn) }
    )
}

/**
 * The folder at [uri], whether the system picker granted it or Morphe's own picker named it by
 * path, which needs no grant once storage access is given.
 */
fun Context.pickedFolder(uri: Uri): DocumentFile? =
    if (uri.scheme == ContentResolver.SCHEME_FILE) {
        uri.path?.let { DocumentFile.fromFile(File(it)) }
    } else {
        DocumentFile.fromTreeUri(this, uri)
    }

fun DocumentFile.getOrCreateDir(name: String): DocumentFile? =
    findFile(name) ?: createDirectory(name)

fun DocumentFile.getOrCreateFile(mimeType: String, name: String): DocumentFile? =
    findFile(name) ?: if (uri.scheme == ContentResolver.SCHEME_FILE) {
        // A plain file keeps its name as given, where createFile would add an extension for the type
        uri.path?.let { File(it, name) }?.takeIf { it.createNewFile() }?.let(DocumentFile::fromFile)
    } else {
        createFile(mimeType, name)
    }

/**
 * The branding folder, kept out of the gallery. A picked folder already named so is used as it is,
 * since storage roots such as Download cannot be picked to hold one.
 */
fun DocumentFile.brandingFolder(): DocumentFile? {
    val folder = if (name == BRANDING_FOLDER_NAME) this else getOrCreateDir(BRANDING_FOLDER_NAME)
    folder?.getOrCreateFile("application/octet-stream", ".nomedia")
    return folder
}

/** Writes [bitmap] into this folder as the PNG [name], replacing an earlier one, then frees it. */
fun DocumentFile.writePng(context: Context, name: String, bitmap: Bitmap) {
    getOrCreateFile("image/png", name)?.let { file ->
        context.contentResolver.openOutputStream(file.uri, "wt")?.use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
    }
    bitmap.recycle()
}

/** Antialiased paint with bicubic filtering, for scaling pictures without jagged edges. */
fun smoothBitmapPaint() = Paint().apply {
    isAntiAlias = true
    isFilterBitmap = true
    isDither = true
}

/** A transparent [width] by [height] bitmap with [source] drawn into [bounds]. */
fun renderPicture(source: Bitmap, width: Int, height: Int, bounds: Rect, paint: Paint = smoothBitmapPaint()): Bitmap =
    createBitmap(width, height).also { Canvas(it).drawBitmap(source, null, bounds.toAndroidRectF(), paint) }

/** Bounds of the pixels that are not close to transparent, or null when there are none. */
private fun Bitmap.contentBounds(): Rect? {
    val row = IntArray(width)
    var left = width
    var top = -1
    var right = -1
    var bottom = -1
    for (y in 0 until height) {
        getPixels(row, 0, width, 0, y, width, 1)
        for (x in 0 until width) {
            if (android.graphics.Color.alpha(row[x]) > CONTENT_ALPHA_THRESHOLD) {
                left = minOf(left, x)
                right = maxOf(right, x)
                if (top < 0) top = y
                bottom = y
            }
        }
    }
    return if (top < 0) null else Rect(left.toFloat(), top.toFloat(), right + 1f, bottom + 1f)
}

// Checks a scaled-down sample of the bitmap to determine if any pixel has transparency
private fun Bitmap.hasTransparentPixels(): Boolean {
    if (!hasAlpha()) return false
    val sampleWidth = minOf(width, 64)
    val sampleHeight = minOf(height, 64)
    val scaled = this.scale(sampleWidth, sampleHeight, false)
    val pixels = IntArray(sampleWidth * sampleHeight)
    scaled.getPixels(pixels, 0, sampleWidth, 0, 0, sampleWidth, sampleHeight)
    if (scaled !== this) scaled.recycle()
    return pixels.any { android.graphics.Color.alpha(it) < 255 }
}
