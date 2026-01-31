package app.revanced.manager.ui.screen.shared

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.*
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.core.graphics.createBitmap
import app.morphe.manager.R
import app.revanced.manager.util.parseColorToRgb
import app.revanced.manager.util.toFilePath
import app.revanced.manager.util.toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.math.abs

/**
 * Configuration constants for adaptive icon creation
 */
private object AdaptiveIconConfig {
    // Folder structure
    const val BRANDING_FOLDER_NAME = "morphe_branding"
    const val ICONS_FOLDER_NAME = "morphe_icons"

    // File names
    const val BACKGROUND_FILE_NAME = "morphe_adaptive_background_custom.png"
    const val FOREGROUND_FILE_NAME = "morphe_adaptive_foreground_custom.png"

    // Density folders and sizes
    val DENSITY_CONFIGS = mapOf(
        160 to ("mipmap-mdpi" to 108),
        240 to ("mipmap-hdpi" to 162),
        320 to ("mipmap-xhdpi" to 216),
        480 to ("mipmap-xxhdpi" to 324),
        Int.MAX_VALUE to ("mipmap-xxxhdpi" to 432)
    )

    // Transform constraints
    const val MIN_SCALE = 0.5f
    const val MAX_SCALE = 3.0f
    const val MAX_OFFSET = 200f

    // Snap to center thresholds (in pixels)
    const val SNAP_THRESHOLD = 10f
    const val SNAP_GUIDE_THRESHOLD = 15f

    // Safe zones (as percentage of total size)
    const val SAFE_ZONE_OUTER = 0.66f // 66% - mask zone
    const val SAFE_ZONE_INNER = 0.42f // 42% - always visible

    // Visual appearance
    const val SAFE_ZONE_OUTER_ALPHA = 0.3f
    const val SAFE_ZONE_INNER_ALPHA = 0.5f
    const val SAFE_ZONE_STROKE_WIDTH = 2.5f
    const val SNAP_GUIDE_ALPHA = 0.6f
    const val SNAP_GUIDE_STROKE_WIDTH = 1.5f

    // Preview size
    val PREVIEW_SIZE = 200.dp

    // Default background color
    const val DEFAULT_BACKGROUND_COLOR = "#1E5AA8"
}

/**
 * Dialog for creating adaptive icons with foreground and background customization
 * Generates icons in proper sizes for all screen densities
 */
@Composable
fun AdaptiveIconCreatorDialog(
    onDismiss: () -> Unit,
    onIconCreated: (String) -> Unit
) {
    val scope = rememberCoroutineScope()

    var foregroundUri by remember { mutableStateOf<Uri?>(null) }
    var foregroundBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var backgroundColor by remember { mutableStateOf(AdaptiveIconConfig.DEFAULT_BACKGROUND_COLOR) }
    var showColorPicker by remember { mutableStateOf(false) }

    // Scaling and positioning state
    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }

    val context = LocalContext.current

    // Foreground image picker
    val foregroundPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            foregroundUri = it
            scope.launch(Dispatchers.IO) {
                try {
                    val inputStream = context.contentResolver.openInputStream(it)
                    val bitmap = BitmapFactory.decodeStream(inputStream)
                    inputStream?.close()
                    foregroundBitmap = bitmap
                    // Reset transform when new image is loaded
                    withContext(Dispatchers.Main) {
                        scale = 1f
                        offsetX = 0f
                        offsetY = 0f
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        context.toast("Failed to load image: ${e.message}")
                    }
                }
            }
        }
    }

    // Folder picker for saving
    val successMessage = stringResource(R.string.adaptive_icon_created_success)
    val failureMessage = stringResource(R.string.adaptive_icon_creation_failed)

    val folderPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        uri?.let {
            scope.launch(Dispatchers.IO) {
                try {
                    val success = createAdaptiveIcon(
                        context = context,
                        baseUri = it,
                        foregroundBitmap = foregroundBitmap!!,
                        backgroundColor = backgroundColor,
                        scale = scale,
                        offsetX = offsetX,
                        offsetY = offsetY
                    )

                    withContext(Dispatchers.Main) {
                        if (success != null) {
                            context.toast(successMessage)
                            onIconCreated(success)
                            onDismiss()
                        } else {
                            context.toast(failureMessage)
                        }
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        context.toast("Failed to create icon: ${e.message}")
                    }
                }
            }
        }
    }

    MorpheDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.adaptive_icon_creator_title),
        compactPadding = true,
        footer = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Explanation text
                AnimatedVisibility(
                    visible = foregroundBitmap != null,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    InfoBadge(
                        text = stringResource(R.string.adaptive_icon_folder_explanation),
                        style = InfoBadgeStyle.Primary,
                        icon = Icons.Outlined.Info
                    )
                }

                // Create button
                MorpheDialogButton(
                    text = stringResource(R.string.adaptive_icon_create),
                    onClick = { folderPicker.launch(null) },
                    enabled = foregroundBitmap != null,
                    icon = Icons.Outlined.Save,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Instructions
            InfoBadge(
                text = stringResource(R.string.adaptive_icon_instructions),
                style = InfoBadgeStyle.Primary,
                icon = Icons.Outlined.Info
            )

            // Preview with safe zones
            AdaptiveIconPreview(
                foregroundBitmap = foregroundBitmap,
                backgroundColor = backgroundColor,
                scale = scale,
                offsetX = offsetX,
                offsetY = offsetY,
                onScaleChange = { newScale ->
                    scale = newScale.coerceIn(AdaptiveIconConfig.MIN_SCALE, AdaptiveIconConfig.MAX_SCALE)
                },
                onOffsetChange = { newOffsetX, newOffsetY ->
                    offsetX = newOffsetX.coerceIn(-AdaptiveIconConfig.MAX_OFFSET, AdaptiveIconConfig.MAX_OFFSET)
                    offsetY = newOffsetY.coerceIn(-AdaptiveIconConfig.MAX_OFFSET, AdaptiveIconConfig.MAX_OFFSET)
                }
            )

            // Reset transform button
            if (foregroundBitmap != null && (scale != 1f || offsetX != 0f || offsetY != 0f)) {
                TextButton(
                    onClick = {
                        scale = 1f
                        offsetX = 0f
                        offsetY = 0f
                    },
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.RestartAlt,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.adaptive_icon_reset_transform))
                }
            }

            // Foreground selection
            MorpheDialogButton(
                text = if (foregroundUri == null)
                    stringResource(R.string.adaptive_icon_select_foreground)
                else
                    stringResource(R.string.adaptive_icon_change_foreground),
                onClick = { foregroundPicker.launch("image/*") },
                icon = Icons.Outlined.Image,
                modifier = Modifier.fillMaxWidth()
            )

            // Background color selection
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.adaptive_icon_background_color),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = LocalDialogTextColor.current,
                    modifier = Modifier.weight(1f)
                )

                Surface(
                    onClick = { showColorPicker = true },
                    modifier = Modifier.size(40.dp),
                    shape = CircleShape,
                    color = parseColorToRgb(backgroundColor).let { (r, g, b) ->
                        Color(r, g, b)
                    },
                    border = BorderStroke(
                        2.dp,
                        MaterialTheme.colorScheme.outline
                    )
                ) {}
            }
        }
    }

    // Color picker dialog
    if (showColorPicker) {
        ColorPickerDialog(
            title = stringResource(R.string.adaptive_icon_background_color),
            currentColor = backgroundColor,
            onColorSelected = { color ->
                backgroundColor = color
                showColorPicker = false
            },
            onDismiss = { showColorPicker = false }
        )
    }
}

/**
 * Preview component showing adaptive icon with safe zones and transform gestures
 */
@Composable
private fun AdaptiveIconPreview(
    foregroundBitmap: Bitmap?,
    backgroundColor: String,
    scale: Float,
    offsetX: Float,
    offsetY: Float,
    onScaleChange: (Float) -> Unit,
    onOffsetChange: (Float, Float) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = stringResource(R.string.adaptive_icon_preview),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = LocalDialogTextColor.current
        )

        Box(
            modifier = Modifier
                .size(AdaptiveIconConfig.PREVIEW_SIZE)
                .clip(CircleShape)
                .background(
                    parseColorToRgb(backgroundColor).let { (r, g, b) ->
                        Color(r, g, b)
                    }
                )
                .border(2.dp, MaterialTheme.colorScheme.outline, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            if (foregroundBitmap != null) {
                var currentScale by remember { mutableFloatStateOf(scale) }
                var currentOffsetX by remember { mutableFloatStateOf(offsetX) }
                var currentOffsetY by remember { mutableFloatStateOf(offsetY) }

                // Sync with parent state
                LaunchedEffect(scale, offsetX, offsetY) {
                    currentScale = scale
                    currentOffsetX = offsetX
                    currentOffsetY = offsetY
                }

                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                // Apply zoom
                                currentScale *= zoom

                                // Apply pan (convert to normalized coordinates)
                                var newOffsetX = currentOffsetX + pan.x
                                var newOffsetY = currentOffsetY + pan.y

                                // Snap to center when close
                                if (abs(newOffsetX) < AdaptiveIconConfig.SNAP_THRESHOLD) newOffsetX = 0f
                                if (abs(newOffsetY) < AdaptiveIconConfig.SNAP_THRESHOLD) newOffsetY = 0f

                                currentOffsetX = newOffsetX
                                currentOffsetY = newOffsetY

                                // Update parent state
                                onScaleChange(currentScale)
                                onOffsetChange(currentOffsetX, currentOffsetY)
                            }
                        }
                ) {
                    val centerX = size.width / 2
                    val centerY = size.height / 2

                    // Draw foreground first
                    val imageBitmap = foregroundBitmap.asImageBitmap()

                    // Calculate scaled size
                    val scaledWidth = size.width * currentScale
                    val scaledHeight = size.height * currentScale

                    // Calculate position with offset
                    val left = centerX - (scaledWidth / 2) + currentOffsetX
                    val top = centerY - (scaledHeight / 2) + currentOffsetY

                    drawImage(
                        image = imageBitmap,
                        dstOffset = IntOffset(left.toInt(), top.toInt()),
                        dstSize = IntSize(scaledWidth.toInt(), scaledHeight.toInt())
                    )

                    // Draw center snap guides when near center
                    if (abs(currentOffsetX) < AdaptiveIconConfig.SNAP_GUIDE_THRESHOLD ||
                        abs(currentOffsetY) < AdaptiveIconConfig.SNAP_GUIDE_THRESHOLD) {
                        // Vertical center line
                        if (abs(currentOffsetX) < AdaptiveIconConfig.SNAP_GUIDE_THRESHOLD) {
                            drawLine(
                                color = Color.White.copy(alpha = AdaptiveIconConfig.SNAP_GUIDE_ALPHA),
                                start = Offset(centerX, 0f),
                                end = Offset(centerX, size.height),
                                strokeWidth = AdaptiveIconConfig.SNAP_GUIDE_STROKE_WIDTH
                            )
                        }

                        // Horizontal center line
                        if (abs(currentOffsetY) < AdaptiveIconConfig.SNAP_GUIDE_THRESHOLD) {
                            drawLine(
                                color = Color.White.copy(alpha = AdaptiveIconConfig.SNAP_GUIDE_ALPHA),
                                start = Offset(0f, centerY),
                                end = Offset(size.width, centerY),
                                strokeWidth = AdaptiveIconConfig.SNAP_GUIDE_STROKE_WIDTH
                            )
                        }
                    }

                    // Draw safe zone circles ON TOP of foreground
                    // Outer safe zone (66dp mask)
                    drawCircle(
                        color = Color.White.copy(alpha = AdaptiveIconConfig.SAFE_ZONE_OUTER_ALPHA),
                        radius = size.width * AdaptiveIconConfig.SAFE_ZONE_OUTER / 2,
                        center = Offset(centerX, centerY),
                        style = Stroke(width = AdaptiveIconConfig.SAFE_ZONE_STROKE_WIDTH)
                    )

                    // Inner safe zone (42dp safe area)
                    drawCircle(
                        color = Color.White.copy(alpha = AdaptiveIconConfig.SAFE_ZONE_INNER_ALPHA),
                        radius = size.width * AdaptiveIconConfig.SAFE_ZONE_INNER / 2,
                        center = Offset(centerX, centerY),
                        style = Stroke(width = AdaptiveIconConfig.SAFE_ZONE_STROKE_WIDTH)
                    )
                }
            } else {
                // Empty state - just show safe zones
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val centerX = size.width / 2
                    val centerY = size.height / 2

                    drawCircle(
                        color = Color.White.copy(alpha = AdaptiveIconConfig.SAFE_ZONE_OUTER_ALPHA),
                        radius = size.width * AdaptiveIconConfig.SAFE_ZONE_OUTER / 2,
                        center = Offset(centerX, centerY),
                        style = Stroke(width = AdaptiveIconConfig.SAFE_ZONE_STROKE_WIDTH)
                    )

                    drawCircle(
                        color = Color.White.copy(alpha = AdaptiveIconConfig.SAFE_ZONE_INNER_ALPHA),
                        radius = size.width * AdaptiveIconConfig.SAFE_ZONE_INNER / 2,
                        center = Offset(centerX, centerY),
                        style = Stroke(width = AdaptiveIconConfig.SAFE_ZONE_STROKE_WIDTH)
                    )
                }
            }
        }

        // Safe zone legend
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            SafeZoneLegendItem(
                color = Color.White.copy(alpha = 0.3f),
                text = stringResource(R.string.adaptive_icon_safe_zone_inner)
            )
            SafeZoneLegendItem(
                color = Color.White.copy(alpha = 0.2f),
                text = stringResource(R.string.adaptive_icon_safe_zone_outer)
            )
        }
    }
}

@Composable
private fun SafeZoneLegendItem(
    color: Color,
    text: String
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(12.dp)
                .border(2.dp, color, CircleShape)
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = LocalDialogSecondaryTextColor.current
        )
    }
}

/**
 * Create adaptive icon files in proper structure
 * Returns the path to morphe_icons folder or null if failed
 */
@SuppressLint("UseKtx")
private suspend fun createAdaptiveIcon(
    context: Context,
    baseUri: Uri,
    foregroundBitmap: Bitmap,
    backgroundColor: String,
    scale: Float,
    offsetX: Float,
    offsetY: Float
): String? = withContext(Dispatchers.IO) {
    try {
        // Get current device density
        val displayMetrics = context.resources.displayMetrics
        val density = displayMetrics.densityDpi

        // Determine which folder to create based on density
        val (folderName, targetSize) = AdaptiveIconConfig.DENSITY_CONFIGS
            .entries
            .firstOrNull { density <= it.key }
            ?.value
            ?: AdaptiveIconConfig.DENSITY_CONFIGS[Int.MAX_VALUE]!!

        // Convert URI to File path using existing utility
        val basePath = baseUri.toFilePath()
        val baseDir = File(basePath)

        // Create directory structure: morphe_branding/morphe_icons/mipmap-xxx
        val brandingDir = File(baseDir, AdaptiveIconConfig.BRANDING_FOLDER_NAME)
        if (!brandingDir.exists()) brandingDir.mkdirs()

        val iconsDir = File(brandingDir, AdaptiveIconConfig.ICONS_FOLDER_NAME)
        if (!iconsDir.exists()) iconsDir.mkdirs()

        val mipmapDir = File(iconsDir, folderName)
        if (!mipmapDir.exists()) mipmapDir.mkdirs()

        // Create background bitmap (solid color)
        val backgroundBitmap = createBitmap(targetSize, targetSize)
        val canvas = Canvas(backgroundBitmap)
        val rgb = parseColorToRgb(backgroundColor)
        val paint = Paint().apply {
            color = android.graphics.Color.rgb(
                (rgb.first * 255).toInt(),
                (rgb.second * 255).toInt(),
                (rgb.third * 255).toInt()
            )
        }
        canvas.drawRect(0f, 0f, targetSize.toFloat(), targetSize.toFloat(), paint)

        // Create foreground bitmap with scaling and offset
        val foregroundScaled = createBitmap(targetSize, targetSize)
        val foregroundCanvas = Canvas(foregroundScaled)

        // Apply transformations
        val scaledWidth = targetSize * scale
        val scaledHeight = targetSize * scale
        val left = (targetSize - scaledWidth) / 2 + (offsetX * targetSize / AdaptiveIconConfig.MAX_OFFSET)
        val top = (targetSize - scaledHeight) / 2 + (offsetY * targetSize / AdaptiveIconConfig.MAX_OFFSET)

        val destRect = RectF(left, top, left + scaledWidth, top + scaledHeight)
        foregroundCanvas.drawBitmap(foregroundBitmap, null, destRect, null)

        // Save background
        val backgroundFile = File(mipmapDir, AdaptiveIconConfig.BACKGROUND_FILE_NAME)
        FileOutputStream(backgroundFile).use { out ->
            backgroundBitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }

        // Save foreground
        val foregroundFile = File(mipmapDir, AdaptiveIconConfig.FOREGROUND_FILE_NAME)
        FileOutputStream(foregroundFile).use { out ->
            foregroundScaled.compress(Bitmap.CompressFormat.PNG, 100, out)
        }

        // Clean up
        backgroundBitmap.recycle()
        foregroundScaled.recycle()

        // Return path to 'morphe_icons' folder
        iconsDir.absolutePath
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}
