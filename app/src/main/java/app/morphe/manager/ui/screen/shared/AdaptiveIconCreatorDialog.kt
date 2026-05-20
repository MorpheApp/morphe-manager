/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.shared

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.*
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter.Companion.colorMatrix
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.core.graphics.createBitmap
import androidx.core.graphics.get
import androidx.documentfile.provider.DocumentFile
import app.morphe.manager.R
import app.morphe.manager.util.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs

/**
 * Configuration constants for adaptive icon creation.
 */
private object AdaptiveIconConfig {
    // Folder structure
    const val BRANDING_FOLDER_NAME = "morphe_branding"
    const val YOUTUBE_ICONS_FOLDER_NAME = "morphe_icons_youtube"
    const val YTM_ICONS_FOLDER_NAME = "morphe_icons_music"

    fun iconFolderName(packageName: String) = when (packageName) {
        KnownApps.YOUTUBE_MUSIC -> YTM_ICONS_FOLDER_NAME
        else -> YOUTUBE_ICONS_FOLDER_NAME
    }

    // File names
    const val BACKGROUND_FILE_NAME = "morphe_adaptive_background_custom.png"
    const val FOREGROUND_FILE_NAME = "morphe_adaptive_foreground_custom.png"
    const val NOTIFICATION_FILE_NAME = "morphe_notification_icon_custom.png"
    const val NOTIFICATION_XML_FILE_NAME = "morphe_notification_icon_custom.xml"
    const val MONOCHROME_ADAPTIVE_FILE_NAME = "morphe_adaptive_monochrome_custom.xml"
    const val DRAWABLE_FOLDER_NAME = "drawable"

    // Density folders and sizes
    val DENSITY_CONFIGS = listOf(
        DensityConfig("mipmap-mdpi", 108),
        DensityConfig("mipmap-hdpi", 162),
        DensityConfig("mipmap-xhdpi", 216),
        DensityConfig("mipmap-xxhdpi", 324),
        DensityConfig("mipmap-xxxhdpi", 432)
    )

    // Notification icon density folders and sizes
    val NOTIFICATION_DENSITY_CONFIGS = listOf(
        DensityConfig("drawable-mdpi", 24),
        DensityConfig("drawable-hdpi", 36),
        DensityConfig("drawable-xhdpi", 48),
        DensityConfig("drawable-xxhdpi", 72),
        DensityConfig("drawable-xxxhdpi", 96)
    )

    data class DensityConfig(val folderName: String, val size: Int)

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
    const val SAFE_ZONE_STROKE_WIDTH = 3f
    const val SAFE_ZONE_INNER_ALPHA = 0.5f
    const val SAFE_ZONE_OUTER_ALPHA = 0.5f
    const val SNAP_GUIDE_STROKE_WIDTH = 1.5f
    const val SNAP_GUIDE_ALPHA = 0.6f

    // Default background color and alpha
    const val DEFAULT_BACKGROUND_COLOR = "#B3E5FC"
    const val DEFAULT_BACKGROUND_ALPHA = 1f

    // Preview sizes
    val PREVIEW_SIZE = 130.dp
    val NOTIFICATION_PREVIEW_SIZE = 52.dp

    // Adaptive icon preview shape, squircle approximation matching Pixel launcher mask
    val PREVIEW_CORNER_RADIUS = 28.dp
    // Proportional corner radius for small (52dp) preview tiles (≈21% of size, same ratio as adaptive)
    val SMALL_PREVIEW_CORNER_RADIUS = 12.dp

    // Viewport sizes for XML VectorDrawable output
    const val MONOCHROME_ADAPTIVE_VIEWPORT = 108
    const val NOTIFICATION_XML_VIEWPORT = 24
}

/**
 * Dialog for creating adaptive icons with foreground and background customization.
 * Generates icons in proper sizes for all screen densities, plus XML VectorDrawable
 * files for the monochrome adaptive layer and notification icon.
 */
@Composable
fun AdaptiveIconCreatorDialog(
    packageName: String,
    onDismiss: () -> Unit,
    onIconCreated: (String) -> Unit
) {
    val scope = rememberCoroutineScope()

    var foregroundUri by remember { mutableStateOf<Uri?>(null) }
    var foregroundBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var backgroundColor by remember { mutableStateOf(AdaptiveIconConfig.DEFAULT_BACKGROUND_COLOR) }
    var backgroundAlpha by remember { mutableFloatStateOf(AdaptiveIconConfig.DEFAULT_BACKGROUND_ALPHA) }
    val showColorPicker = remember { mutableStateOf(false) }
    val showInfoDialog = remember { mutableStateOf(false) }

    // Adaptive icon transform state
    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }

    // Notification/monochrome icon transform state
    var notificationScale by remember { mutableFloatStateOf(1f) }

    val context = LocalContext.current

    // Foreground image picker, resets all transforms when a new image is loaded
    val openForegroundPicker = rememberAdaptiveFilePicker(mimeTypes = arrayOf("image/*")) { uri ->
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
                        scale = 1f; offsetX = 0f; offsetY = 0f
                        notificationScale = 1f
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) { context.toast("Failed to load image: ${e.message}") }
                }
            }
        }
    }

    val successMessage = stringResource(R.string.adaptive_icon_created_success)
    val failureMessage = stringResource(R.string.adaptive_icon_creation_failed)

    // Folder picker for saving
    val openFolderPicker = rememberFolderPicker { uri ->
        scope.launch(Dispatchers.IO) {
            try {
                val success = createAdaptiveIcons(
                    context = context,
                    baseUri = uri,
                    packageName = packageName,
                    foregroundBitmap = foregroundBitmap!!,
                    backgroundColor = backgroundColor,
                    backgroundAlpha = backgroundAlpha,
                    scale = scale,
                    offsetX = offsetX,
                    offsetY = offsetY,
                    notificationScale = notificationScale
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
                withContext(Dispatchers.Main) { context.toast("Failed to create icon: ${e.message}") }
            }
        }
    }

    MorpheDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.adaptive_icon_create),
        titleTrailingContent = {
            IconButton(onClick = { showInfoDialog.value = true }) {
                Icon(
                    imageVector = Icons.Outlined.Info,
                    contentDescription = stringResource(R.string.adaptive_icon_guide),
                    modifier = Modifier.size(24.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        compactPadding = true,
        footer = {
            MorpheDialogButton(
                text = stringResource(R.string.adaptive_icon_create),
                onClick = { openFolderPicker() },
                enabled = foregroundBitmap != null,
                icon = Icons.Outlined.Save,
                modifier = Modifier.fillMaxWidth()
            )
        }
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Foreground image picker
            MorpheDialogOutlinedButton(
                text = if (foregroundUri == null)
                    stringResource(R.string.adaptive_icon_select_image)
                else
                    stringResource(R.string.adaptive_icon_change_image),
                onClick = { openForegroundPicker() },
                icon = Icons.Outlined.Image,
                modifier = Modifier.fillMaxWidth()
            )

            // 2. Preview row: large adaptive circle on the left, small notification +
            //    monochrome circles stacked vertically on the right (only when a source exists).
            //    IntrinsicSize.Min lets the right column stretch to match the adaptive circle's height.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(40.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Large adaptive preview
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = stringResource(R.string.adaptive_icon_label),
                        style = MaterialTheme.typography.labelSmall,
                        color = LocalDialogSecondaryTextColor.current
                    )
                    AdaptiveIconPreview(
                        foregroundBitmap = foregroundBitmap,
                        backgroundColor = backgroundColor,
                        backgroundAlpha = backgroundAlpha,
                        scale = scale,
                        offsetX = offsetX,
                        offsetY = offsetY,
                        onScaleChange = { scale = it.coerceIn(AdaptiveIconConfig.MIN_SCALE, AdaptiveIconConfig.MAX_SCALE) },
                        onOffsetChange = { x, y ->
                            offsetX = x.coerceIn(-AdaptiveIconConfig.MAX_OFFSET, AdaptiveIconConfig.MAX_OFFSET)
                            offsetY = y.coerceIn(-AdaptiveIconConfig.MAX_OFFSET, AdaptiveIconConfig.MAX_OFFSET)
                        }
                    )
                }

                // Small previews, fill the same height as the adaptive column via SpaceBetween
                foregroundBitmap?.let { bitmap ->
                    Column(
                        modifier = Modifier.fillMaxHeight(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        // Notification preview, white-on-dark, scale only
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.notification_icon_preview),
                                style = MaterialTheme.typography.labelSmall,
                                color = LocalDialogSecondaryTextColor.current
                            )
                            NotificationIconCanvas(
                                bitmap = bitmap,
                                scale = notificationScale
                            )
                        }
                        // Monochrome adaptive preview, non-interactive, mirrors adaptive transforms
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.adaptive_icon_monochrome_label),
                                style = MaterialTheme.typography.labelSmall,
                                color = LocalDialogSecondaryTextColor.current
                            )
                            MonochromeAdaptiveCanvas(
                                bitmap = bitmap,
                                scale = scale,
                                offsetX = offsetX,
                                offsetY = offsetY
                            )
                        }
                    }
                }
            }

            // Safe zone legend
            val legendColor = MaterialTheme.colorScheme.onSurface
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 36.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                SafeZoneLegendItem(
                    baseColor = legendColor,
                    alpha = AdaptiveIconConfig.SAFE_ZONE_INNER_ALPHA,
                    isDashed = false,
                    text = stringResource(R.string.adaptive_icon_safe_zone_inner)
                )
                SafeZoneLegendItem(
                    baseColor = legendColor,
                    alpha = AdaptiveIconConfig.SAFE_ZONE_OUTER_ALPHA,
                    isDashed = true,
                    text = stringResource(R.string.adaptive_icon_safe_zone_outer)
                )
            }

            // 3. Scale/offset sliders, grouped together, shown only when relevant
            if (foregroundBitmap != null) {
                HorizontalDivider()

                // Adaptive slider
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = stringResource(R.string.adaptive_icon_label),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        color = LocalDialogSecondaryTextColor.current,
                        modifier = Modifier.width(72.dp)
                    )
                    Icon(
                        imageVector = Icons.Outlined.Image,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = LocalDialogSecondaryTextColor.current
                    )
                    Slider(
                        value = scale,
                        onValueChange = { scale = it.coerceIn(AdaptiveIconConfig.MIN_SCALE, AdaptiveIconConfig.MAX_SCALE) },
                        valueRange = AdaptiveIconConfig.MIN_SCALE..AdaptiveIconConfig.MAX_SCALE,
                        modifier = Modifier.weight(1f)
                    )
                    Icon(
                        imageVector = Icons.Outlined.Image,
                        contentDescription = null,
                        modifier = Modifier.size(22.dp),
                        tint = LocalDialogSecondaryTextColor.current
                    )
                    AnimatedVisibility(
                        visible = scale != 1f || offsetX != 0f || offsetY != 0f,
                        enter = MorpheAnimations.expandFadeEnter,
                        exit = MorpheAnimations.shrinkFadeExit
                    ) {
                        IconButton(
                            onClick = { scale = 1f; offsetX = 0f; offsetY = 0f },
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.RestartAlt,
                                contentDescription = stringResource(R.string.adaptive_icon_reset_transform),
                                modifier = Modifier.size(24.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // Notification slider
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = stringResource(R.string.notification_icon_preview),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        color = LocalDialogSecondaryTextColor.current,
                        modifier = Modifier.width(72.dp)
                    )
                    Icon(
                        imageVector = Icons.Outlined.Image,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = LocalDialogSecondaryTextColor.current
                    )
                    Slider(
                        value = notificationScale,
                        onValueChange = { notificationScale = it.coerceIn(AdaptiveIconConfig.MIN_SCALE, AdaptiveIconConfig.MAX_SCALE) },
                        valueRange = AdaptiveIconConfig.MIN_SCALE..AdaptiveIconConfig.MAX_SCALE,
                        modifier = Modifier.weight(1f)
                    )
                    Icon(
                        imageVector = Icons.Outlined.Image,
                        contentDescription = null,
                        modifier = Modifier.size(22.dp),
                        tint = LocalDialogSecondaryTextColor.current
                    )
                    AnimatedVisibility(
                        visible = notificationScale != 1f,
                        enter = MorpheAnimations.expandFadeEnter,
                        exit = MorpheAnimations.shrinkFadeExit
                    ) {
                        IconButton(
                            onClick = { notificationScale = 1f },
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.RestartAlt,
                                contentDescription = stringResource(R.string.adaptive_icon_reset_transform),
                                modifier = Modifier.size(24.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            HorizontalDivider()

            // 4. Background color picker and alpha slider on separate rows
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        onClick = { showColorPicker.value = true },
                        modifier = Modifier.size(36.dp),
                        shape = CircleShape,
                        color = parseColorToRgb(backgroundColor).let { (r, g, b) ->
                            Color(r, g, b, backgroundAlpha)
                        },
                        border = BorderStroke(2.dp, MaterialTheme.colorScheme.outline)
                    ) {}
                    Text(
                        text = stringResource(R.string.adaptive_icon_background_color),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = LocalDialogTextColor.current
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Opacity,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = LocalDialogSecondaryTextColor.current
                    )
                    Slider(
                        value = backgroundAlpha,
                        onValueChange = { backgroundAlpha = it },
                        valueRange = 0f..1f,
                        modifier = Modifier.weight(1f)
                    )
                    Icon(
                        imageVector = Icons.Outlined.Opacity,
                        contentDescription = null,
                        modifier = Modifier.size(22.dp),
                        tint = LocalDialogSecondaryTextColor.current
                    )
                    Text(
                        text = "${(backgroundAlpha * 100).toInt()}%",
                        style = MaterialTheme.typography.bodySmall,
                        color = LocalDialogSecondaryTextColor.current,
                        modifier = Modifier.width(32.dp),
                        textAlign = TextAlign.End
                    )
                }
            }
        }
    }

    // Color picker dialog
    if (showColorPicker.value) {
        ColorPickerDialog(
            title = stringResource(R.string.adaptive_icon_background_color),
            currentColor = backgroundColor,
            onColorSelected = { color ->
                backgroundColor = color
                showColorPicker.value = false
            },
            onDismiss = { showColorPicker.value = false }
        )
    }

    // Icon creation guide dialog
    if (showInfoDialog.value) {
        MorpheDialog(
            onDismissRequest = { showInfoDialog.value = false },
            title = stringResource(R.string.adaptive_icon_guide),
            footer = {
                MorpheDialogButton(
                    text = stringResource(android.R.string.ok),
                    onClick = { showInfoDialog.value = false },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                AdaptiveIconGuideSection(
                    title = stringResource(R.string.adaptive_icon_guide_png_title),
                    body = stringResource(R.string.adaptive_icon_guide_png_body)
                )
                AdaptiveIconGuideSection(
                    title = stringResource(R.string.adaptive_icon_guide_safe_zones_title),
                    body = stringResource(R.string.adaptive_icon_guide_safe_zones_body)
                )
                AdaptiveIconGuideSection(
                    title = stringResource(R.string.adaptive_icon_guide_notification_title),
                    body = stringResource(R.string.adaptive_icon_guide_notification_body)
                )
                AdaptiveIconGuideSection(
                    title = stringResource(R.string.adaptive_icon_guide_monochrome_title),
                    body = stringResource(R.string.adaptive_icon_guide_monochrome_body)
                )
            }
        }
    }
}

/**
 * Adaptive icon preview circle with safe-zone guides and pinch/pan gesture support.
 * Scale slider and reset button are rendered by the caller below the preview row.
 */
@Composable
private fun AdaptiveIconPreview(
    foregroundBitmap: Bitmap?,
    backgroundColor: String,
    backgroundAlpha: Float,
    scale: Float,
    offsetX: Float,
    offsetY: Float,
    onScaleChange: (Float) -> Unit,
    onOffsetChange: (Float, Float) -> Unit
) {
    // Guide color adapts to background brightness to keep circles visible
    val previewGuideColor = remember(backgroundColor) {
        val bgColor = backgroundColor.toColorOrNull()
            ?: AdaptiveIconConfig.DEFAULT_BACKGROUND_COLOR.toColorOrNull()
            ?: Color.Black
        if (bgColor.isDarkBackground()) Color.White else Color.Black
    }
    // Dashed effect for snap guides and outer safe zone
    val dashEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 6f), 0f)

    Box(
        modifier = Modifier
            .size(AdaptiveIconConfig.PREVIEW_SIZE)
            .clip(RoundedCornerShape(AdaptiveIconConfig.PREVIEW_CORNER_RADIUS))
            .background(
                parseColorToRgb(backgroundColor).let { (r, g, b) ->
                    Color(r, g, b, backgroundAlpha)
                }
            )
            .border(2.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(AdaptiveIconConfig.PREVIEW_CORNER_RADIUS)),
        contentAlignment = Alignment.Center
    ) {
        if (foregroundBitmap != null) {
            var currentScale by remember { mutableFloatStateOf(scale) }
            var currentOffsetX by remember { mutableFloatStateOf(offsetX) }
            var currentOffsetY by remember { mutableFloatStateOf(offsetY) }

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
                            currentScale *= zoom
                            var newOffsetX = currentOffsetX + pan.x
                            var newOffsetY = currentOffsetY + pan.y
                            if (abs(newOffsetX) < AdaptiveIconConfig.SNAP_THRESHOLD) newOffsetX = 0f
                            if (abs(newOffsetY) < AdaptiveIconConfig.SNAP_THRESHOLD) newOffsetY = 0f
                            currentOffsetX = newOffsetX
                            currentOffsetY = newOffsetY
                            onScaleChange(currentScale)
                            onOffsetChange(currentOffsetX, currentOffsetY)
                        }
                    }
            ) {
                val centerX = size.width / 2
                val centerY = size.height / 2

                // Draw foreground image
                val imageBitmap = foregroundBitmap.asImageBitmap()

                // Calculate base size by fitting image to canvas while maintaining aspect ratio
                val imageAspect = imageBitmap.width.toFloat() / imageBitmap.height.toFloat()
                val canvasAspect = size.width / size.height  // For square canvas this is 1.0

                val (baseWidth, baseHeight) = if (imageAspect > canvasAspect) {
                    // Image is wider - fit to width
                    size.width to (size.width / imageAspect)
                } else {
                    // Image is taller - fit to height
                    (size.height * imageAspect) to size.height
                }

                // Apply user scale to the fitted size
                val scaledWidth = baseWidth * currentScale
                val scaledHeight = baseHeight * currentScale

                // Calculate position with offset
                val left = centerX - (scaledWidth / 2) + currentOffsetX
                val top = centerY - (scaledHeight / 2) + currentOffsetY

                drawImage(
                    image = imageBitmap,
                    dstOffset = IntOffset(left.toInt(), top.toInt()),
                    dstSize = IntSize(scaledWidth.toInt(), scaledHeight.toInt())
                )

                // Draw dashed snap guides when close to center
                if (abs(currentOffsetX) < AdaptiveIconConfig.SNAP_GUIDE_THRESHOLD) {
                    // Vertical center line
                    drawLine(
                        color = previewGuideColor.copy(alpha = AdaptiveIconConfig.SNAP_GUIDE_ALPHA),
                        start = Offset(centerX, 0f),
                        end = Offset(centerX, size.height),
                        strokeWidth = AdaptiveIconConfig.SNAP_GUIDE_STROKE_WIDTH,
                        pathEffect = dashEffect
                    )
                }
                if (abs(currentOffsetY) < AdaptiveIconConfig.SNAP_GUIDE_THRESHOLD) {
                    // Horizontal center line
                    drawLine(
                        color = previewGuideColor.copy(alpha = AdaptiveIconConfig.SNAP_GUIDE_ALPHA),
                        start = Offset(0f, centerY),
                        end = Offset(size.width, centerY),
                        strokeWidth = AdaptiveIconConfig.SNAP_GUIDE_STROKE_WIDTH,
                        pathEffect = dashEffect
                    )
                }

                // Outer safe zone (66%, mask area)
                val outerSize = size.width * AdaptiveIconConfig.SAFE_ZONE_OUTER
                val outerCorner = outerSize * (AdaptiveIconConfig.PREVIEW_CORNER_RADIUS.value / AdaptiveIconConfig.PREVIEW_SIZE.value)
                drawRoundRect(
                    color = previewGuideColor.copy(alpha = AdaptiveIconConfig.SAFE_ZONE_OUTER_ALPHA),
                    topLeft = Offset(centerX - outerSize / 2, centerY - outerSize / 2),
                    size = androidx.compose.ui.geometry.Size(outerSize, outerSize),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(outerCorner),
                    style = Stroke(width = AdaptiveIconConfig.SAFE_ZONE_STROKE_WIDTH, pathEffect = dashEffect)
                )
                // Inner safe zone (42%, always visible)
                val innerSize = size.width * AdaptiveIconConfig.SAFE_ZONE_INNER
                val innerCorner = innerSize * (AdaptiveIconConfig.PREVIEW_CORNER_RADIUS.value / AdaptiveIconConfig.PREVIEW_SIZE.value)
                drawRoundRect(
                    color = previewGuideColor.copy(alpha = AdaptiveIconConfig.SAFE_ZONE_INNER_ALPHA),
                    topLeft = Offset(centerX - innerSize / 2, centerY - innerSize / 2),
                    size = androidx.compose.ui.geometry.Size(innerSize, innerSize),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(innerCorner),
                    style = Stroke(width = AdaptiveIconConfig.SAFE_ZONE_STROKE_WIDTH)
                )
            }
        } else {
            // Empty state, show only safe zones
            Canvas(modifier = Modifier.fillMaxSize()) {
                val centerX = size.width / 2
                val centerY = size.height / 2
                // Outer safe zone, dashed
                val outerSize = size.width * AdaptiveIconConfig.SAFE_ZONE_OUTER
                val outerCorner = outerSize * (AdaptiveIconConfig.PREVIEW_CORNER_RADIUS.value / AdaptiveIconConfig.PREVIEW_SIZE.value)
                drawRoundRect(
                    color = previewGuideColor.copy(alpha = AdaptiveIconConfig.SAFE_ZONE_OUTER_ALPHA),
                    topLeft = Offset(centerX - outerSize / 2, centerY - outerSize / 2),
                    size = androidx.compose.ui.geometry.Size(outerSize, outerSize),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(outerCorner),
                    style = Stroke(width = AdaptiveIconConfig.SAFE_ZONE_STROKE_WIDTH, pathEffect = dashEffect)
                )
                // Inner safe zone, solid
                val innerSize = size.width * AdaptiveIconConfig.SAFE_ZONE_INNER
                val innerCorner = innerSize * (AdaptiveIconConfig.PREVIEW_CORNER_RADIUS.value / AdaptiveIconConfig.PREVIEW_SIZE.value)
                drawRoundRect(
                    color = previewGuideColor.copy(alpha = AdaptiveIconConfig.SAFE_ZONE_INNER_ALPHA),
                    topLeft = Offset(centerX - innerSize / 2, centerY - innerSize / 2),
                    size = androidx.compose.ui.geometry.Size(innerSize, innerSize),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(innerCorner),
                    style = Stroke(width = AdaptiveIconConfig.SAFE_ZONE_STROKE_WIDTH)
                )
            }
        }
    }
}

/**
 * Notification icon preview (dark background).
 * Recolors all pixels white (alpha-preserving) to simulate Android notification rendering.
 */
@Composable
private fun NotificationIconCanvas(
    bitmap: Bitmap,
    scale: Float
) {
    val shape = RoundedCornerShape(AdaptiveIconConfig.SMALL_PREVIEW_CORNER_RADIUS)
    Box(
        modifier = Modifier
            .size(AdaptiveIconConfig.NOTIFICATION_PREVIEW_SIZE)
            .clip(shape)
            .background(Color(0xFF1A1A1A))
            .border(1.dp, Color.White.copy(alpha = 0.15f), shape),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val centerX = size.width / 2
            val centerY = size.height / 2
            val imageBitmap = bitmap.asImageBitmap()
            val imageAspect = imageBitmap.width.toFloat() / imageBitmap.height.toFloat()
            val (baseWidth, baseHeight) = if (imageAspect > 1f)
                size.width to (size.width / imageAspect)
            else
                (size.height * imageAspect) to size.height
            val scaledWidth = baseWidth * scale
            val scaledHeight = baseHeight * scale
            val left = centerX - scaledWidth / 2
            val top = centerY - scaledHeight / 2
            // Recolor all pixels white (alpha-preserving) to simulate how Android renders
            // notification small icons in the status bar and notification drawer
            drawImage(
                image = imageBitmap,
                dstOffset = IntOffset(left.toInt(), top.toInt()),
                dstSize = IntSize(scaledWidth.toInt(), scaledHeight.toInt()),
                colorFilter = colorMatrix(
                    androidx.compose.ui.graphics.ColorMatrix(floatArrayOf(
                        0f, 0f, 0f, 0f, 255f,
                        0f, 0f, 0f, 0f, 255f,
                        0f, 0f, 0f, 0f, 255f,
                        0f, 0f, 0f, 1f, 0f
                    ))
                )
            )
        }
    }
}

/**
 * Non-interactive monochrome adaptive icon preview (40dp, surfaceVariant background).
 * Mirrors the adaptive icon transforms so the result matches the generated XML layer.
 * Renders black (alpha-preserving); the system tints the layer at runtime.
 */
@Composable
private fun MonochromeAdaptiveCanvas(
    bitmap: Bitmap,
    scale: Float,
    offsetX: Float,
    offsetY: Float
) {
    val density = LocalDensity.current.density
    val shape = RoundedCornerShape(AdaptiveIconConfig.SMALL_PREVIEW_CORNER_RADIUS)
    Box(
        modifier = Modifier
            .size(AdaptiveIconConfig.NOTIFICATION_PREVIEW_SIZE)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), shape),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            // Map adaptive transforms from the PREVIEW_SIZE canvas to this small circle.
            // canvasRatio converts preview-canvas pixels to this canvas's pixels.
            val previewCanvasSize = AdaptiveIconConfig.PREVIEW_SIZE.value * density
            val imageBitmap = bitmap.asImageBitmap()
            val imageAspect = imageBitmap.width.toFloat() / imageBitmap.height.toFloat()
            val (baseWidth, baseHeight) = if (imageAspect > 1f)
                previewCanvasSize to (previewCanvasSize / imageAspect)
            else
                (previewCanvasSize * imageAspect) to previewCanvasSize
            val scaledWidth = baseWidth * scale
            val scaledHeight = baseHeight * scale
            val canvasRatio = size.width / previewCanvasSize
            val dstWidth = scaledWidth * canvasRatio
            val dstHeight = scaledHeight * canvasRatio
            val dstOffsetX = offsetX * canvasRatio
            val dstOffsetY = offsetY * canvasRatio
            val left = (size.width - dstWidth) / 2 + dstOffsetX
            val top = (size.height - dstHeight) / 2 + dstOffsetY
            // Force all channels to black (alpha-preserving); system applies accent tint at runtime
            drawImage(
                image = imageBitmap,
                dstOffset = IntOffset(left.toInt(), top.toInt()),
                dstSize = IntSize(dstWidth.toInt(), dstHeight.toInt()),
                colorFilter = colorMatrix(
                    androidx.compose.ui.graphics.ColorMatrix(floatArrayOf(
                        0f, 0f, 0f, 0f, 0f,
                        0f, 0f, 0f, 0f, 0f,
                        0f, 0f, 0f, 0f, 0f,
                        0f, 0f, 0f, 1f, 0f
                    ))
                )
            )
        }
    }
}

/**
 * One section of the icon creation guide: bold title followed by a description.
 */
@Composable
private fun AdaptiveIconGuideSection(title: String, body: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = LocalDialogTextColor.current
        )
        Text(
            text = body,
            style = MaterialTheme.typography.bodySmall,
            color = LocalDialogSecondaryTextColor.current
        )
    }
}

/**
 * Legend item for safe zones, shows a small circle with solid or dashed stroke.
 */
@Composable
private fun SafeZoneLegendItem(
    baseColor: Color,
    alpha: Float,
    isDashed: Boolean,
    text: String
) {
    val itemColor = baseColor.copy(alpha = alpha)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Canvas(modifier = Modifier.size(16.dp)) {
            val dashEffect = if (isDashed) PathEffect.dashPathEffect(floatArrayOf(8f, 6f), 0f) else null
            val corner = size.minDimension * 0.25f
            drawRoundRect(
                color = itemColor,
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(corner),
                style = Stroke(width = 2.5f, pathEffect = dashEffect)
            )
        }
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = LocalDialogSecondaryTextColor.current
        )
    }
}

private fun DocumentFile.getOrCreateDir(name: String): DocumentFile? =
    findFile(name) ?: createDirectory(name)

private fun DocumentFile.getOrCreateFile(mimeType: String, name: String): DocumentFile? =
    findFile(name) ?: createFile(mimeType, name)

/**
 * Create adaptive icon files for all densities in proper structure.
 * Uses the SAF DocumentFile API so any folder the user picks is writable without MANAGE_EXTERNAL_STORAGE.
 * Returns the real file-system path to the morphe_icons folder (for use as a patch option value),
 * or null if creation failed.
 */
@SuppressLint("UseKtx")
private suspend fun createAdaptiveIcons(
    context: Context,
    baseUri: Uri,
    packageName: String,
    foregroundBitmap: Bitmap,
    backgroundColor: String,
    backgroundAlpha: Float,
    scale: Float,
    offsetX: Float,
    offsetY: Float,
    notificationScale: Float
): String? = withContext(Dispatchers.IO) {
    try {
        val baseDocDir = DocumentFile.fromTreeUri(context, baseUri) ?: return@withContext null

        // Create directory structure: morphe_branding/morphe_icons_youtube or morphe_icons_music
        val brandingDocDir = baseDocDir.getOrCreateDir(AdaptiveIconConfig.BRANDING_FOLDER_NAME)
            ?: return@withContext null

        // Create .nomedia file to prevent icons from appearing in gallery
        if (brandingDocDir.findFile(".nomedia") == null) {
            brandingDocDir.createFile("application/octet-stream", ".nomedia")
        }

        val iconsDocDir = brandingDocDir.getOrCreateDir(AdaptiveIconConfig.iconFolderName(packageName))
            ?: return@withContext null

        // Get preview density for offset calculations
        val previewDensity = context.resources.displayMetrics.density

        // Generate adaptive icon PNGs (foreground + background) for all densities
        AdaptiveIconConfig.DENSITY_CONFIGS.forEach { densityConfig ->
            createIconsForDensity(
                context = context,
                iconsDocDir = iconsDocDir,
                densityConfig = densityConfig,
                foregroundBitmap = foregroundBitmap,
                backgroundColor = backgroundColor,
                backgroundAlpha = backgroundAlpha,
                scale = scale,
                offsetX = offsetX,
                offsetY = offsetY,
                previewDensity = previewDensity
            )
        }

        // Monochrome and notification outputs are always derived from the foreground bitmap
        val monochromeSrc = foregroundBitmap

        // Generate notification icon PNGs for all densities
        AdaptiveIconConfig.NOTIFICATION_DENSITY_CONFIGS.forEach { densityConfig ->
            createNotificationIconForDensity(
                context = context,
                iconsDocDir = iconsDocDir,
                densityConfig = densityConfig,
                sourceBitmap = monochromeSrc,
                scale = notificationScale,
                previewDensity = previewDensity
            )
        }

        // Generate XML VectorDrawable files in a 'drawable' folder
        val drawableDocDir = iconsDocDir.getOrCreateDir(AdaptiveIconConfig.DRAWABLE_FOLDER_NAME)
        if (drawableDocDir != null) {
            val plainPaint = Paint().apply { isAntiAlias = true; isFilterBitmap = true; isDither = true }

            // Monochrome adaptive layer: render at 4x oversample to reduce scanline aliasing,
            // then scale path coordinates back to the 108x108 viewport.
            val monoOversample = 4
            val adaptiveMonoBmp = renderBitmapWithAdaptiveTransforms(
                sourceBitmap = monochromeSrc,
                targetSize = AdaptiveIconConfig.MONOCHROME_ADAPTIVE_VIEWPORT * monoOversample,
                scale = scale,
                offsetX = offsetX,
                offsetY = offsetY,
                previewDensity = previewDensity,
                paint = plainPaint
            )
            val adaptiveMonoXml = createMonochromeVectorXml(
                bitmap = adaptiveMonoBmp,
                viewportSize = AdaptiveIconConfig.MONOCHROME_ADAPTIVE_VIEWPORT,
                coordScale = 1f / monoOversample,
                // System tints the monochrome layer; fill color is overridden at runtime
                fillColor = "#FF000000"
            )
            adaptiveMonoBmp.recycle()
            saveXmlToDocFile(context, drawableDocDir, AdaptiveIconConfig.MONOCHROME_ADAPTIVE_FILE_NAME, adaptiveMonoXml)

            // Notification icon XML: 24x24 viewport, using notification transforms
            val notifMonoBmp = renderBitmapWithNotificationTransforms(
                sourceBitmap = monochromeSrc,
                targetSize = AdaptiveIconConfig.NOTIFICATION_XML_VIEWPORT,
                scale = notificationScale,
                previewDensity = previewDensity,
                paint = plainPaint
            )
            val notifMonoXml = createMonochromeVectorXml(
                bitmap = notifMonoBmp,
                viewportSize = AdaptiveIconConfig.NOTIFICATION_XML_VIEWPORT,
                fillColor = "#FFFFFFFF"
            )
            notifMonoBmp.recycle()
            saveXmlToDocFile(context, drawableDocDir, AdaptiveIconConfig.NOTIFICATION_XML_FILE_NAME, notifMonoXml)
        }

        // Convert back to a real path so the patcher can reference it as a patch option value
        iconsDocDir.uri.toFilePath()
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}

/**
 * Create foreground and background icon files for a specific density.
 */
private fun createIconsForDensity(
    context: Context,
    iconsDocDir: DocumentFile,
    densityConfig: AdaptiveIconConfig.DensityConfig,
    foregroundBitmap: Bitmap,
    backgroundColor: String,
    backgroundAlpha: Float,
    scale: Float,
    offsetX: Float,
    offsetY: Float,
    previewDensity: Float
) {
    val targetSize = densityConfig.size
    val mipmapDocDir = iconsDocDir.getOrCreateDir(densityConfig.folderName) ?: return

    // Background bitmap (solid color with alpha support)
    val backgroundBitmap = createBitmap(targetSize, targetSize)
    val canvas = Canvas(backgroundBitmap)
    val rgb = parseColorToRgb(backgroundColor)
    val paint = Paint().apply {
        color = android.graphics.Color.argb(
            (backgroundAlpha * 255).toInt(),
            (rgb.first * 255).toInt(),
            (rgb.second * 255).toInt(),
            (rgb.third * 255).toInt()
        )
    }
    canvas.drawRect(0f, 0f, targetSize.toFloat(), targetSize.toFloat(), paint)

    // Create foreground bitmap with scaling and offset
    // Paint with antialiasing and bicubic filtering for high-quality scaling
    val bitmapPaint = Paint().apply { isAntiAlias = true; isFilterBitmap = true; isDither = true }
    val foregroundScaled = renderBitmapWithAdaptiveTransforms(
        sourceBitmap = foregroundBitmap,
        targetSize = targetSize,
        scale = scale,
        offsetX = offsetX,
        offsetY = offsetY,
        previewDensity = previewDensity,
        paint = bitmapPaint
    )

    mipmapDocDir.getOrCreateFile("image/png", AdaptiveIconConfig.BACKGROUND_FILE_NAME)
        ?.let { context.contentResolver.openOutputStream(it.uri)?.use { out ->
            backgroundBitmap.compress(Bitmap.CompressFormat.PNG, 100, out) } }

    mipmapDocDir.getOrCreateFile("image/png", AdaptiveIconConfig.FOREGROUND_FILE_NAME)
        ?.let { context.contentResolver.openOutputStream(it.uri)?.use { out ->
            foregroundScaled.compress(Bitmap.CompressFormat.PNG, 100, out) } }

    backgroundBitmap.recycle()
    foregroundScaled.recycle()
}

/**
 * Create a notification icon PNG for a specific density.
 * The source bitmap is recolored white (alpha-preserving) per Material Design guidelines.
 */
private fun createNotificationIconForDensity(
    context: Context,
    iconsDocDir: DocumentFile,
    densityConfig: AdaptiveIconConfig.DensityConfig,
    sourceBitmap: Bitmap,
    scale: Float,
    previewDensity: Float
) {
    val targetSize = densityConfig.size

    // Create drawable-<dpi> directory inside the icons folder
    val drawableDocDir = iconsDocDir.getOrCreateDir(densityConfig.folderName) ?: return

    // ColorMatrix that turns every pixel white while keeping its alpha channel intact:
    //   R = 1, G = 1, B = 1, A = original alpha
    val whitePaint = Paint().apply {
        isAntiAlias = true
        isFilterBitmap = true
        isDither = true
        colorFilter = ColorMatrixColorFilter(ColorMatrix(floatArrayOf(
            0f, 0f, 0f, 0f, 255f,  // R channel → always 255
            0f, 0f, 0f, 0f, 255f,  // G channel → always 255
            0f, 0f, 0f, 0f, 255f,  // B channel → always 255
            0f, 0f, 0f, 1f, 0f     // A channel → keep original
        )))
    }

    val notificationBitmap = renderBitmapWithNotificationTransforms(
        sourceBitmap = sourceBitmap,
        targetSize = targetSize,
        scale = scale,
        previewDensity = previewDensity,
        paint = whitePaint
    )

    drawableDocDir.getOrCreateFile("image/png", AdaptiveIconConfig.NOTIFICATION_FILE_NAME)
        ?.let { context.contentResolver.openOutputStream(it.uri)?.use { out ->
            notificationBitmap.compress(Bitmap.CompressFormat.PNG, 100, out) } }

    notificationBitmap.recycle()
}

/**
 * Render [sourceBitmap] into a [targetSize]×[targetSize] canvas using the same
 * coordinate mapping as the adaptive icon preview (fitted to full preview canvas).
 */
private fun renderBitmapWithAdaptiveTransforms(
    sourceBitmap: Bitmap,
    targetSize: Int,
    scale: Float,
    offsetX: Float,
    offsetY: Float,
    previewDensity: Float,
    paint: Paint = Paint().apply { isAntiAlias = true; isFilterBitmap = true }
): Bitmap {
    val result = createBitmap(targetSize, targetSize)
    val canvas = Canvas(result)
    // Preview canvas size in pixels, the coordinate origin for scale/offset values
    val previewCanvasSize = AdaptiveIconConfig.PREVIEW_SIZE.value * previewDensity
    // Calculate base size by fitting image to canvas (same logic as preview composable)
    val imageAspect = sourceBitmap.width.toFloat() / sourceBitmap.height.toFloat()
    val (baseWidth, baseHeight) = if (imageAspect > 1f) {
        // Image is wider - fit to width
        previewCanvasSize to (previewCanvasSize / imageAspect)
    } else {
        // Image is taller - fit to height
        (previewCanvasSize * imageAspect) to previewCanvasSize
    }
    // Apply user scale to the fitted size
    val scaledWidth = baseWidth * scale
    val scaledHeight = baseHeight * scale
    // Map from preview-canvas coordinates to target-bitmap coordinates
    val ratio = targetSize / previewCanvasSize
    val targetScaledWidth = scaledWidth * ratio
    val targetScaledHeight = scaledHeight * ratio
    // Convert offsets from preview-canvas pixels to target-bitmap pixels
    val targetOffsetX = offsetX * ratio
    val targetOffsetY = offsetY * ratio
    val left = (targetSize - targetScaledWidth) / 2 + targetOffsetX
    val top = (targetSize - targetScaledHeight) / 2 + targetOffsetY
    canvas.drawBitmap(sourceBitmap, null, RectF(left, top, left + targetScaledWidth, top + targetScaledHeight), paint)
    return result
}

/**
 * Render [sourceBitmap] into a [targetSize]×[targetSize] canvas using the notification
 * icon coordinate mapping (fitted to the outer safe zone region).
 */
private fun renderBitmapWithNotificationTransforms(
    sourceBitmap: Bitmap,
    targetSize: Int,
    scale: Float,
    previewDensity: Float,
    paint: Paint = Paint().apply { isAntiAlias = true; isFilterBitmap = true }
): Bitmap {
    val result = createBitmap(targetSize, targetSize)
    val canvas = Canvas(result)
    // The notification icon should fill its small canvas the same way the foreground
    // fills the adaptive icon safe zone. We therefore express the user's transform
    // relative to the safe zone size and then map it onto the full notification canvas.
    val previewCanvasSize = AdaptiveIconConfig.PREVIEW_SIZE.value * previewDensity
    // Size of the outer safe zone in preview canvas pixels
    val safeZoneSize = previewCanvasSize * AdaptiveIconConfig.SAFE_ZONE_OUTER
    // Base fitted size, fitted to safe zone, not the full canvas
    val imageAspect = sourceBitmap.width.toFloat() / sourceBitmap.height.toFloat()
    val (baseWidth, baseHeight) = if (imageAspect > 1f) {
        safeZoneSize to (safeZoneSize / imageAspect)
    } else {
        (safeZoneSize * imageAspect) to safeZoneSize
    }
    // Apply user scale, then map to target notification canvas size
    val scaledWidth = baseWidth * scale
    val scaledHeight = baseHeight * scale
    val ratio = targetSize / safeZoneSize
    val targetScaledWidth = scaledWidth * ratio
    val targetScaledHeight = scaledHeight * ratio
    val left = (targetSize - targetScaledWidth) / 2
    val top = (targetSize - targetScaledHeight) / 2
    canvas.drawBitmap(sourceBitmap, null, RectF(left, top, left + targetScaledWidth, top + targetScaledHeight), paint)
    return result
}

/**
 * Convert a bitmap's alpha channel to SVG/VectorDrawable path data using scanline spans.
 * Each row of opaque pixels (alpha > 127) is encoded as one or more horizontal rect commands.
 * [coordScale] maps bitmap pixel coordinates to viewport units (use 1f/oversampleFactor for oversampled bitmaps).
 */
private fun bitmapToVectorPathData(bitmap: Bitmap, coordScale: Float = 1f): String {
    val width = bitmap.width
    val height = bitmap.height
    val sb = StringBuilder()
    // Each row is scanned left-to-right; adjacent opaque pixels are merged into spans,
    // so the number of path commands equals the number of horizontal spans, not pixels.
    for (y in 0 until height) {
        var spanStart = -1
        for (x in 0 until width) {
            val opaque = android.graphics.Color.alpha(bitmap[x, y]) > 127
            if (opaque && spanStart == -1) {
                spanStart = x
            } else if (!opaque && spanStart != -1) {
                val sx = spanStart * coordScale
                val sy = y * coordScale
                val sw = (x - spanStart) * coordScale
                sb.append("M$sx,${sy}h${sw}v${coordScale}h-${sw}z")
                spanStart = -1
            }
        }
        if (spanStart != -1) {
            val sx = spanStart * coordScale
            val sy = y * coordScale
            val sw = (width - spanStart) * coordScale
            sb.append("M$sx,${sy}h${sw}v${coordScale}h-${sw}z")
        }
    }
    return sb.toString()
}

/**
 * Create an Android VectorDrawable XML string from a pre-rendered monochrome bitmap.
 * The bitmap's alpha channel defines the icon shape; [fillColor] sets the path fill.
 * [coordScale] is forwarded to [bitmapToVectorPathData] for oversampled bitmaps.
 */
private fun createMonochromeVectorXml(bitmap: Bitmap, viewportSize: Int, coordScale: Float = 1f, fillColor: String): String {
    val pathData = bitmapToVectorPathData(bitmap, coordScale)
    return """<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="${viewportSize}dp"
    android:height="${viewportSize}dp"
    android:viewportWidth="$viewportSize"
    android:viewportHeight="$viewportSize">
    <path
        android:fillColor="$fillColor"
        android:pathData="$pathData" />
</vector>"""
}

/**
 * Write an XML string to a DocumentFile, truncating any previous content.
 */
private fun saveXmlToDocFile(context: Context, dir: DocumentFile, fileName: String, content: String) {
    val file = dir.getOrCreateFile("text/xml", fileName) ?: return
    context.contentResolver.openOutputStream(file.uri, "wt")?.use { out ->
        out.write(content.toByteArray(Charsets.UTF_8))
    }
}
