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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.core.graphics.createBitmap
import androidx.core.graphics.get
import androidx.core.graphics.scale
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
    // Notification icon must not exceed the status bar slot boundary
    const val MAX_NOTIFICATION_SCALE = 2.0f
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

    // Used only as a ratio reference for safe zone corner calculations
    val PREVIEW_SIZE = 150.dp

    // Adaptive icon preview shape, squircle approximation matching Pixel launcher mask
    val PREVIEW_CORNER_RADIUS = 28.dp

    // Viewport sizes for XML VectorDrawable output
    const val MONOCHROME_ADAPTIVE_VIEWPORT = 108
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
    val primaryContainer = MaterialTheme.colorScheme.primaryContainer
    var backgroundColor by remember {
        mutableStateOf(rgbToHex(primaryContainer.red, primaryContainer.green, primaryContainer.blue))
    }
    val showColorPicker = remember { mutableStateOf(false) }
    val showInfoDialog = remember { mutableStateOf(false) }

    // Adaptive icon transform state
    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }

    // Notification/monochrome icon transform state
    var notificationScale by remember { mutableFloatStateOf(1f) }

    var showTransparencyWarning by remember { mutableStateOf(false) }

    val context = LocalContext.current

    // Foreground image picker, resets all transforms when a new image is loaded
    val openForegroundPicker = rememberAdaptiveFilePicker(
        mimeTypes = arrayOf("image/*"),
        onResult = { uri ->
            uri?.let {
                foregroundUri = it
                showTransparencyWarning = false
                scope.launch(Dispatchers.IO) {
                    try {
                        val inputStream = context.contentResolver.openInputStream(it)
                        val bitmap = BitmapFactory.decodeStream(inputStream)
                        inputStream?.close()
                        foregroundBitmap = bitmap
                        val hasTransparency = bitmap?.hasTransparentPixels() == true
                        // Reset transform when new image is loaded
                        withContext(Dispatchers.Main) {
                            scale = 1f; offsetX = 0f; offsetY = 0f
                            notificationScale = 1f
                            showTransparencyWarning = !hasTransparency
                        }
                    } catch (e: Exception) {
                        withContext(Dispatchers.Main) { context.toast("Failed to load image: ${e.message}") }
                    }
                }
            }
        }
    )

    val successMessage = stringResource(R.string.adaptive_icon_created_success)
    val failureMessage = stringResource(R.string.adaptive_icon_creation_failed)

    var isCreating by remember { mutableStateOf(false) }

    // Folder picker for saving
    val openFolderPicker = rememberFolderPicker { uri ->
        scope.launch(Dispatchers.IO) {
            withContext(Dispatchers.Main) { isCreating = true }
            try {
                val success = createAdaptiveIcons(
                    context = context,
                    baseUri = uri,
                    packageName = packageName,
                    foregroundBitmap = foregroundBitmap!!,
                    backgroundColor = backgroundColor,
                    scale = scale,
                    offsetX = offsetX,
                    offsetY = offsetY,
                    notificationScale = notificationScale
                )
                withContext(Dispatchers.Main) {
                    isCreating = false
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
                    isCreating = false
                    context.toast("Failed to create icon: ${e.message}")
                }
            }
        }
    }

    MorpheDialog(
        onDismissRequest = { if (!isCreating) onDismiss() },
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
                enabled = foregroundBitmap != null && !isCreating,
                icon = Icons.Outlined.Save,
                modifier = Modifier.fillMaxWidth()
            )
        }
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
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

                // Transparency warning shown when the selected image has no transparent pixels
                AnimatedVisibility(
                    visible = showTransparencyWarning,
                    enter = MorpheAnimations.expandFadeEnter,
                    exit = MorpheAnimations.shrinkFadeExit
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Info,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.error
                        )
                        Text(
                            text = stringResource(R.string.adaptive_icon_no_transparency_warning),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }

                // 2. Preview row: adaptive on the left, monochrome on the right.
                //    Each column takes equal weight so the previews fill available width side by side
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(MorpheDefaults.ContentPadding),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Adaptive icon preview, interactive
                    Column(
                        modifier = Modifier.weight(1f),
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
                            scale = scale,
                            offsetX = offsetX,
                            offsetY = offsetY,
                            onScaleChange = {
                                scale = it.coerceIn(
                                    AdaptiveIconConfig.MIN_SCALE,
                                    AdaptiveIconConfig.MAX_SCALE
                                )
                            },
                            onOffsetChange = { x, y ->
                                offsetX = x.coerceIn(
                                    -AdaptiveIconConfig.MAX_OFFSET,
                                    AdaptiveIconConfig.MAX_OFFSET
                                )
                                offsetY = y.coerceIn(
                                    -AdaptiveIconConfig.MAX_OFFSET,
                                    AdaptiveIconConfig.MAX_OFFSET
                                )
                            }
                        )
                    }

                    // Monochrome preview, mirrors adaptive transforms
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.adaptive_icon_monochrome_label),
                            style = MaterialTheme.typography.labelSmall,
                            color = LocalDialogSecondaryTextColor.current
                        )
                        MonochromeAdaptiveCanvas(
                            bitmap = foregroundBitmap,
                            scale = scale,
                            offsetX = offsetX,
                            offsetY = offsetY
                        )
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

                // Adaptive scale slider
                if (foregroundBitmap != null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Image,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = LocalDialogSecondaryTextColor.current
                        )
                        Spacer(Modifier.width(8.dp))
                        Slider(
                            value = scale,
                            onValueChange = {
                                scale = it.coerceIn(
                                    AdaptiveIconConfig.MIN_SCALE,
                                    AdaptiveIconConfig.MAX_SCALE
                                )
                            },
                            valueRange = AdaptiveIconConfig.MIN_SCALE..AdaptiveIconConfig.MAX_SCALE,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(Modifier.width(8.dp))
                        Icon(
                            imageVector = Icons.Outlined.Image,
                            contentDescription = null,
                            modifier = Modifier.size(22.dp),
                            tint = LocalDialogSecondaryTextColor.current
                        )
                        // Spacer inside AnimatedVisibility so the gap also animates away
                        AnimatedVisibility(
                            visible = scale != 1f || offsetX != 0f || offsetY != 0f
                        ) {
                            Row {
                                Spacer(Modifier.width(8.dp))
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
                    }
                }

                // 3. Status bar notification preview
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = stringResource(R.string.notification_icon_preview),
                        style = MaterialTheme.typography.labelSmall,
                        color = LocalDialogSecondaryTextColor.current,
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    )
                    StatusBarPreview(
                        bitmap = foregroundBitmap,
                        scale = notificationScale
                    )
                }

                // Notification scale slider
                if (foregroundBitmap != null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Image,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = LocalDialogSecondaryTextColor.current
                        )
                        Spacer(Modifier.width(8.dp))
                        Slider(
                            value = notificationScale,
                            onValueChange = {
                                notificationScale = it.coerceIn(
                                    AdaptiveIconConfig.MIN_SCALE,
                                    AdaptiveIconConfig.MAX_NOTIFICATION_SCALE
                                )
                            },
                            valueRange = AdaptiveIconConfig.MIN_SCALE..AdaptiveIconConfig.MAX_NOTIFICATION_SCALE,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(Modifier.width(8.dp))
                        Icon(
                            imageVector = Icons.Outlined.Image,
                            contentDescription = null,
                            modifier = Modifier.size(22.dp),
                            tint = LocalDialogSecondaryTextColor.current
                        )
                        // Spacer inside AnimatedVisibility so the gap also animates away
                        AnimatedVisibility(
                            visible = notificationScale != 1f
                        ) {
                            Row {
                                Spacer(Modifier.width(8.dp))
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
                }

                HorizontalDivider()

                // 4. Background color swatch - tap to open picker
                Text(
                    text = stringResource(R.string.adaptive_icon_background_color),
                    style = MaterialTheme.typography.bodySmall,
                    color = LocalDialogSecondaryTextColor.current,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
                val swatchColor = parseColorToRgb(backgroundColor).let { (r, g, b) -> Color(r, g, b) }
                Surface(
                    onClick = { showColorPicker.value = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = swatchColor,
                    border = BorderStroke(2.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = backgroundColor.uppercase(),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = if (swatchColor.requiresLightContent()) Color.White else Color.Black
                        )
                    }
                }
            }
            MorpheContentOverlay(visible = isCreating) {
                PulsingLogoIndicator()
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
    scale: Float,
    offsetX: Float,
    offsetY: Float,
    onScaleChange: (Float) -> Unit,
    onOffsetChange: (Float, Float) -> Unit
) {
    // Guide color adapts to background brightness to keep circles visible
    val previewGuideColor = remember(backgroundColor) {
        val bgColor = backgroundColor.toColorOrNull()
            ?: Color.Black
        if (bgColor.isDarkBackground()) Color.White else Color.Black
    }
    // Dashed effect for snap guides and outer safe zone
    val dashEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 6f), 0f)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(RoundedCornerShape(AdaptiveIconConfig.PREVIEW_CORNER_RADIUS))
            .background(
                parseColorToRgb(backgroundColor).let { (r, g, b) -> Color(r, g, b) }
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
 * Status bar simulation showing the notification icon at actual size with a dashed slot boundary.
 * Accepts a nullable bitmap so the slot guide is visible before an image is selected.
 */
@Composable
private fun StatusBarPreview(
    bitmap: Bitmap?,
    scale: Float
) {
    val contentColor = MaterialTheme.colorScheme.onSurface
    val guideColor = contentColor.copy(alpha = 0.5f)
    val dashEffect = remember { PathEffect.dashPathEffect(floatArrayOf(3f, 3f), 0f) }
    val shape = RoundedCornerShape(12.dp)
    // Capture RGB components for use inside Canvas DrawScope
    val iconR = contentColor.red * 255f
    val iconG = contentColor.green * 255f
    val iconB = contentColor.blue * 255f

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
            .padding(horizontal = 16.dp),
    ) {
        // Left side: clock + notification icon
        Row(
            modifier = Modifier.align(Alignment.CenterStart),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Simulated clock
            Text(
                text = "9:41",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = contentColor
            )
            // Notification icon at actual status bar size (~20 dp) with slot boundary guide
            Canvas(modifier = Modifier.size(20.dp)) {
                // Dashed border marks the notification icon slot boundary; content outside
                // this area is clipped by Android in the real status bar
                val iconStroke = Stroke(width = 1.dp.toPx(), pathEffect = dashEffect)
                drawRect(
                    color = guideColor,
                    topLeft = Offset(0.5f, 0.5f),
                    size = androidx.compose.ui.geometry.Size(size.width - 1f, size.height - 1f),
                    style = iconStroke
                )
                if (bitmap != null) {
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
                    // Recolor all pixels to match onSurface (alpha-preserving); simulates how
                    // Android renders notification small icons in the status bar
                    drawImage(
                        image = imageBitmap,
                        dstOffset = IntOffset(left.toInt(), top.toInt()),
                        dstSize = IntSize(scaledWidth.toInt(), scaledHeight.toInt()),
                        colorFilter = colorMatrix(
                            androidx.compose.ui.graphics.ColorMatrix(floatArrayOf(
                                0f, 0f, 0f, 0f, iconR,
                                0f, 0f, 0f, 0f, iconG,
                                0f, 0f, 0f, 0f, iconB,
                                0f, 0f, 0f, 1f, 0f
                            ))
                        )
                    )
                }
            }
        }

        // Right side: system status icons
        Row(
            modifier = Modifier.align(Alignment.CenterEnd),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Outlined.SignalCellular4Bar,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = contentColor
            )
            Icon(
                imageVector = Icons.Outlined.Wifi,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = contentColor
            )
            Icon(
                imageVector = Icons.Outlined.BatteryFull,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = contentColor
            )
        }
    }
}

/**
 * Non-interactive monochrome icon preview using theme accent colors to simulate launcher themed icons.
 * Accepts a nullable bitmap so the colored background is visible before an image is selected.
 */
@Composable
private fun MonochromeAdaptiveCanvas(
    bitmap: Bitmap?,
    scale: Float,
    offsetX: Float,
    offsetY: Float
) {
    val shape = RoundedCornerShape(AdaptiveIconConfig.PREVIEW_CORNER_RADIUS)
    // Read accent colors outside Canvas; must be stable across recompositions.
    // Icon uses primaryContainer so it reads as a cutout from the onPrimaryContainer background
    val iconColor = MaterialTheme.colorScheme.primaryContainer
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(shape)
            .background(MaterialTheme.colorScheme.onPrimaryContainer)
            .border(2.dp, MaterialTheme.colorScheme.outline, shape),
        contentAlignment = Alignment.Center
    ) {
        if (bitmap != null) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                // Image fitting mirrors AdaptiveIconPreview: fill the canvas dimension that matches
                // the image's longer edge, then scale/offset using the shared state values
                val imageBitmap = bitmap.asImageBitmap()
                val imageAspect = imageBitmap.width.toFloat() / imageBitmap.height.toFloat()
                val (baseWidth, baseHeight) = if (imageAspect > 1f)
                    size.width to (size.width / imageAspect)
                else
                    (size.width * imageAspect) to size.width
                val scaledWidth = baseWidth * scale
                val scaledHeight = baseHeight * scale
                val left = (size.width - scaledWidth) / 2 + offsetX
                val top = (size.height - scaledHeight) / 2 + offsetY
                // Force all channels to the accent foreground color (alpha-preserving);
                // launchers apply the same tint at runtime
                drawImage(
                    image = imageBitmap,
                    dstOffset = IntOffset(left.toInt(), top.toInt()),
                    dstSize = IntSize(scaledWidth.toInt(), scaledHeight.toInt()),
                    colorFilter = colorMatrix(
                        androidx.compose.ui.graphics.ColorMatrix(floatArrayOf(
                            0f, 0f, 0f, 0f, iconColor.red * 255,
                            0f, 0f, 0f, 0f, iconColor.green * 255,
                            0f, 0f, 0f, 0f, iconColor.blue * 255,
                            0f, 0f, 0f, 1f, 0f
                        ))
                    )
                )
            }
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
    scale: Float,
    offsetX: Float,
    offsetY: Float,
    notificationScale: Float
): String? = withContext(Dispatchers.IO) {
    try {
        val baseDocDir = DocumentFile.fromTreeUri(context, baseUri) ?: return@withContext null

        // Create directory structure: BRANDING_FOLDER_NAME/YOUTUBE_ICONS_FOLDER_NAME or YTM_ICONS_FOLDER_NAME
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

            // Monochrome adaptive layer: render at 16x oversample (1728x1728) so each scanline
            // is 0.0625 viewport units tall, making stair-stepping sub-pixel on all densities.
            val monoOversample = 16
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
                coordinateScale = 1f / monoOversample
            )
            adaptiveMonoBmp.recycle()
            saveXmlToDocFile(context, drawableDocDir, adaptiveMonoXml)
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
    scale: Float,
    offsetX: Float,
    offsetY: Float,
    previewDensity: Float
) {
    val targetSize = densityConfig.size
    val mipmapDocDir = iconsDocDir.getOrCreateDir(densityConfig.folderName) ?: return

    // Background bitmap (solid color)
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
    // relative to the safe zone size and then map it onto the full notification canvas
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
 * [coordinateScale] maps bitmap pixel coordinates to viewport units (use 1f/oversampleFactor for oversampled bitmaps).
 */
private fun bitmapToVectorPathData(bitmap: Bitmap, coordinateScale: Float = 1f): String {
    val width = bitmap.width
    val height = bitmap.height
    val sb = StringBuilder()
    // Each row is scanned left-to-right; adjacent opaque pixels are merged into spans,
    // so the number of path commands equals the number of horizontal spans, not pixels
    for (y in 0 until height) {
        var spanStart = -1
        for (x in 0 until width) {
            val opaque = android.graphics.Color.alpha(bitmap[x, y]) > 127
            if (opaque && spanStart == -1) {
                spanStart = x
            } else if (!opaque && spanStart != -1) {
                val sx = spanStart * coordinateScale
                val sy = y * coordinateScale
                val sw = (x - spanStart) * coordinateScale
                sb.append("M$sx,${sy}h${sw}v${coordinateScale}h-${sw}z")
                spanStart = -1
            }
        }
        if (spanStart != -1) {
            val sx = spanStart * coordinateScale
            val sy = y * coordinateScale
            val sw = (width - spanStart) * coordinateScale
            sb.append("M$sx,${sy}h${sw}v${coordinateScale}h-${sw}z")
        }
    }
    return sb.toString()
}

/**
 * Create an Android VectorDrawable XML string from a pre-rendered monochrome bitmap.
 * The bitmap's alpha channel defines the icon shape.
 * [coordinateScale] is forwarded to [bitmapToVectorPathData] for oversampled bitmaps.
 */
private fun createMonochromeVectorXml(bitmap: Bitmap, coordinateScale: Float = 1f): String {
    val viewportSize = AdaptiveIconConfig.MONOCHROME_ADAPTIVE_VIEWPORT
    val pathData = bitmapToVectorPathData(bitmap, coordinateScale)
    return """<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="${viewportSize}dp"
    android:height="${viewportSize}dp"
    android:viewportWidth="$viewportSize"
    android:viewportHeight="$viewportSize">
    <path
        android:fillColor="#FF000000"
        android:pathData="$pathData" />
</vector>"""
}

/**
 * Write an XML string to a DocumentFile, truncating any previous content.
 */
private fun saveXmlToDocFile(context: Context, dir: DocumentFile, content: String) {
    val file = dir.getOrCreateFile("text/xml", AdaptiveIconConfig.MONOCHROME_ADAPTIVE_FILE_NAME) ?: return
    context.contentResolver.openOutputStream(file.uri, "wt")?.use { out ->
        out.write(content.toByteArray(Charsets.UTF_8))
    }
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
