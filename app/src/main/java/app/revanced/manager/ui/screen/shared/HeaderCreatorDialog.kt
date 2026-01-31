package app.revanced.manager.ui.screen.shared

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.RectF
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.core.graphics.createBitmap
import app.morphe.manager.R
import app.revanced.manager.util.toFilePath
import app.revanced.manager.util.toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.math.abs

/**
 * Configuration constants for header creation
 */
private object HeaderConfig {
    // Folder structure
    const val BRANDING_FOLDER_NAME = "morphe_branding"
    const val HEADER_FOLDER_NAME = "morphe_header"

    // File names
    const val LIGHT_HEADER_FILE_NAME = "morphe_header_custom_light.png"
    const val DARK_HEADER_FILE_NAME = "morphe_header_custom_dark.png"

    // Density folders and sizes (width x height)
    val DENSITY_CONFIGS = mapOf(
        240 to ("drawable-hdpi" to (194 to 72)),
        320 to ("drawable-xhdpi" to (258 to 96)),
        480 to ("drawable-xxhdpi" to (387 to 144)),
        Int.MAX_VALUE to ("drawable-xxxhdpi" to (512 to 192))
    )

    // Transform constraints
    const val MIN_SCALE = 0.3f
    const val MAX_SCALE = 5.0f
    const val MAX_OFFSET = 300f

    // Snap to center thresholds (in pixels)
    const val SNAP_THRESHOLD = 10f
    const val SNAP_GUIDE_THRESHOLD = 15f

    // Visual appearance
    const val SNAP_GUIDE_ALPHA = 0.6f
    const val SNAP_GUIDE_STROKE_WIDTH = 1.5f
    const val BORDER_STROKE_WIDTH = 2f

    // Preview size (maintaining aspect ratio ~2.67:1)
    val PREVIEW_WIDTH = 280.dp
    val PREVIEW_HEIGHT = 105.dp
}

/**
 * Dialog for creating custom headers with light and dark theme variants
 * Generates header images in proper sizes for all screen densities
 */
@Composable
fun HeaderCreatorDialog(
    onDismiss: () -> Unit,
    onHeaderCreated: (String) -> Unit
) {
    val scope = rememberCoroutineScope()

    var lightHeaderUri by remember { mutableStateOf<Uri?>(null) }
    var lightHeaderBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var darkHeaderUri by remember { mutableStateOf<Uri?>(null) }
    var darkHeaderBitmap by remember { mutableStateOf<Bitmap?>(null) }

    // Scaling and positioning state for light header
    var lightScale by remember { mutableFloatStateOf(1f) }
    var lightOffsetX by remember { mutableFloatStateOf(0f) }
    var lightOffsetY by remember { mutableFloatStateOf(0f) }

    // Scaling and positioning state for dark header
    var darkScale by remember { mutableFloatStateOf(1f) }
    var darkOffsetX by remember { mutableFloatStateOf(0f) }
    var darkOffsetY by remember { mutableFloatStateOf(0f) }

    val context = LocalContext.current

    // Light header image picker
    val lightHeaderPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            lightHeaderUri = it
            scope.launch(Dispatchers.IO) {
                try {
                    val inputStream = context.contentResolver.openInputStream(it)
                    val bitmap = BitmapFactory.decodeStream(inputStream)
                    inputStream?.close()
                    lightHeaderBitmap = bitmap
                    // Reset transform when new image is loaded
                    withContext(Dispatchers.Main) {
                        lightScale = 1f
                        lightOffsetX = 0f
                        lightOffsetY = 0f
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        context.toast("Failed to load image: ${e.message}")
                    }
                }
            }
        }
    }

    // Dark header image picker
    val darkHeaderPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            darkHeaderUri = it
            scope.launch(Dispatchers.IO) {
                try {
                    val inputStream = context.contentResolver.openInputStream(it)
                    val bitmap = BitmapFactory.decodeStream(inputStream)
                    inputStream?.close()
                    darkHeaderBitmap = bitmap
                    // Reset transform when new image is loaded
                    withContext(Dispatchers.Main) {
                        darkScale = 1f
                        darkOffsetX = 0f
                        darkOffsetY = 0f
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
    val successMessage = stringResource(R.string.header_creator_success)
    val failureMessage = stringResource(R.string.header_creator_failed)

    val folderPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        uri?.let {
            scope.launch(Dispatchers.IO) {
                try {
                    val success = createHeaderFiles(
                        context = context,
                        baseUri = it,
                        lightHeaderBitmap = lightHeaderBitmap!!,
                        darkHeaderBitmap = darkHeaderBitmap!!,
                        lightScale = lightScale,
                        lightOffsetX = lightOffsetX,
                        lightOffsetY = lightOffsetY,
                        darkScale = darkScale,
                        darkOffsetX = darkOffsetX,
                        darkOffsetY = darkOffsetY
                    )

                    withContext(Dispatchers.Main) {
                        if (success != null) {
                            context.toast(successMessage)
                            onHeaderCreated(success)
                            onDismiss()
                        } else {
                            context.toast(failureMessage)
                        }
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        context.toast("Failed to create header: ${e.message}")
                    }
                }
            }
        }
    }

    MorpheDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.header_creator_title),
        compactPadding = true,
        footer = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Explanation text
                AnimatedVisibility(
                    visible = lightHeaderBitmap != null && darkHeaderBitmap != null,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    InfoBadge(
                        text = stringResource(R.string.header_creator_folder_explanation),
                        style = InfoBadgeStyle.Primary,
                        icon = Icons.Outlined.Info
                    )
                }

                // Create button
                MorpheDialogButton(
                    text = stringResource(R.string.header_creator_create),
                    onClick = { folderPicker.launch(null) },
                    enabled = lightHeaderBitmap != null && darkHeaderBitmap != null,
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
                text = stringResource(R.string.header_creator_instructions),
                style = InfoBadgeStyle.Primary,
                icon = Icons.Outlined.Info
            )

            // Light header section
            Text(
                text = stringResource(R.string.header_creator_light_theme),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = LocalDialogTextColor.current
            )

            HeaderPreview(
                headerBitmap = lightHeaderBitmap,
                scale = lightScale,
                offsetX = lightOffsetX,
                offsetY = lightOffsetY,
                isDarkTheme = false,
                onScaleChange = { newScale ->
                    lightScale = newScale.coerceIn(HeaderConfig.MIN_SCALE, HeaderConfig.MAX_SCALE)
                },
                onOffsetChange = { newOffsetX, newOffsetY ->
                    lightOffsetX = newOffsetX.coerceIn(-HeaderConfig.MAX_OFFSET, HeaderConfig.MAX_OFFSET)
                    lightOffsetY = newOffsetY.coerceIn(-HeaderConfig.MAX_OFFSET, HeaderConfig.MAX_OFFSET)
                }
            )

            // Reset transform button for light header
            if (lightHeaderBitmap != null && (lightScale != 1f || lightOffsetX != 0f || lightOffsetY != 0f)) {
                TextButton(
                    onClick = {
                        lightScale = 1f
                        lightOffsetX = 0f
                        lightOffsetY = 0f
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

            MorpheDialogButton(
                text = if (lightHeaderUri == null)
                    stringResource(R.string.header_creator_select_light)
                else
                    stringResource(R.string.header_creator_change_light),
                onClick = { lightHeaderPicker.launch("image/*") },
                icon = Icons.Outlined.LightMode,
                modifier = Modifier.fillMaxWidth()
            )

            HorizontalDivider()

            // Dark header section
            Text(
                text = stringResource(R.string.header_creator_dark_theme),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = LocalDialogTextColor.current
            )

            HeaderPreview(
                headerBitmap = darkHeaderBitmap,
                scale = darkScale,
                offsetX = darkOffsetX,
                offsetY = darkOffsetY,
                isDarkTheme = true,
                onScaleChange = { newScale ->
                    darkScale = newScale.coerceIn(HeaderConfig.MIN_SCALE, HeaderConfig.MAX_SCALE)
                },
                onOffsetChange = { newOffsetX, newOffsetY ->
                    darkOffsetX = newOffsetX.coerceIn(-HeaderConfig.MAX_OFFSET, HeaderConfig.MAX_OFFSET)
                    darkOffsetY = newOffsetY.coerceIn(-HeaderConfig.MAX_OFFSET, HeaderConfig.MAX_OFFSET)
                }
            )

            // Reset transform button for dark header
            if (darkHeaderBitmap != null && (darkScale != 1f || darkOffsetX != 0f || darkOffsetY != 0f)) {
                TextButton(
                    onClick = {
                        darkScale = 1f
                        darkOffsetX = 0f
                        darkOffsetY = 0f
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

            MorpheDialogButton(
                text = if (darkHeaderUri == null)
                    stringResource(R.string.header_creator_select_dark)
                else
                    stringResource(R.string.header_creator_change_dark),
                onClick = { darkHeaderPicker.launch("image/*") },
                icon = Icons.Outlined.DarkMode,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

/**
 * Preview component for header with transform gestures
 */
@Composable
private fun HeaderPreview(
    headerBitmap: Bitmap?,
    scale: Float,
    offsetX: Float,
    offsetY: Float,
    isDarkTheme: Boolean,
    onScaleChange: (Float) -> Unit,
    onOffsetChange: (Float, Float) -> Unit
) {
    val previewWidth = HeaderConfig.PREVIEW_WIDTH
    val previewHeight = HeaderConfig.PREVIEW_HEIGHT

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .width(previewWidth)
                .height(previewHeight)
                .clip(RoundedCornerShape(12.dp))
                .background(
                    if (isDarkTheme)
                        Color(0xFF1C1C1C)
                    else
                        Color(0xFFF5F5F5)
                )
                .border(
                    HeaderConfig.BORDER_STROKE_WIDTH.dp,
                    MaterialTheme.colorScheme.outline,
                    RoundedCornerShape(12.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            if (headerBitmap != null) {
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

                                // Apply pan
                                var newOffsetX = currentOffsetX + pan.x
                                var newOffsetY = currentOffsetY + pan.y

                                // Snap to center when close
                                if (abs(newOffsetX) < HeaderConfig.SNAP_THRESHOLD) newOffsetX = 0f
                                if (abs(newOffsetY) < HeaderConfig.SNAP_THRESHOLD) newOffsetY = 0f

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

                    // Draw header image
                    val imageBitmap = headerBitmap.asImageBitmap()

                    // Size based on scale - keep original size, just scale it
                    val displayWidth = imageBitmap.width * currentScale
                    val displayHeight = imageBitmap.height * currentScale

                    // Calculate position with offset (centered + user offset)
                    val left = centerX - (displayWidth / 2) + currentOffsetX
                    val top = centerY - (displayHeight / 2) + currentOffsetY

                    // Draw the image at actual size (will be cropped by canvas bounds)
                    drawImage(
                        image = imageBitmap,
                        dstOffset = IntOffset(left.toInt(), top.toInt()),
                        dstSize = IntSize(displayWidth.toInt(), displayHeight.toInt())
                    )

                    // Draw center snap guides when near center
                    if (abs(currentOffsetX) < HeaderConfig.SNAP_GUIDE_THRESHOLD ||
                        abs(currentOffsetY) < HeaderConfig.SNAP_GUIDE_THRESHOLD) {
                        val guideColor = if (isDarkTheme)
                            Color.White.copy(alpha = HeaderConfig.SNAP_GUIDE_ALPHA)
                        else
                            Color.Black.copy(alpha = HeaderConfig.SNAP_GUIDE_ALPHA)

                        // Vertical center line
                        if (abs(currentOffsetX) < HeaderConfig.SNAP_GUIDE_THRESHOLD) {
                            drawLine(
                                color = guideColor,
                                start = Offset(centerX, 0f),
                                end = Offset(centerX, size.height),
                                strokeWidth = HeaderConfig.SNAP_GUIDE_STROKE_WIDTH
                            )
                        }

                        // Horizontal center line
                        if (abs(currentOffsetY) < HeaderConfig.SNAP_GUIDE_THRESHOLD) {
                            drawLine(
                                color = guideColor,
                                start = Offset(0f, centerY),
                                end = Offset(size.width, centerY),
                                strokeWidth = HeaderConfig.SNAP_GUIDE_STROKE_WIDTH
                            )
                        }
                    }
                }
            } else {
                // Empty state
                Text(
                    text = stringResource(R.string.header_creator_no_image),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            }
        }
    }
}

/**
 * Create header files in proper structure
 * Returns the path to morphe_header folder or null if failed
 */
private suspend fun createHeaderFiles(
    context: Context,
    baseUri: Uri,
    lightHeaderBitmap: Bitmap,
    darkHeaderBitmap: Bitmap,
    lightScale: Float,
    lightOffsetX: Float,
    lightOffsetY: Float,
    darkScale: Float,
    darkOffsetX: Float,
    darkOffsetY: Float
): String? = withContext(Dispatchers.IO) {
    try {
        // Get current device density
        val displayMetrics = context.resources.displayMetrics
        val density = displayMetrics.densityDpi

        // Determine which folder to create based on density
        val (folderName, targetSize) = HeaderConfig.DENSITY_CONFIGS
            .entries
            .firstOrNull { density <= it.key }
            ?.value
            ?: HeaderConfig.DENSITY_CONFIGS[Int.MAX_VALUE]!!

        val (targetWidth, targetHeight) = targetSize

        // Convert URI to File path using existing utility
        val basePath = baseUri.toFilePath()
        val baseDir = File(basePath)

        // Create directory structure: morphe_branding/morphe_header/drawable-xxx
        val brandingDir = File(baseDir, HeaderConfig.BRANDING_FOLDER_NAME)
        if (!brandingDir.exists()) brandingDir.mkdirs()

        val headerDir = File(brandingDir, HeaderConfig.HEADER_FOLDER_NAME)
        if (!headerDir.exists()) headerDir.mkdirs()

        val drawableDir = File(headerDir, folderName)
        if (!drawableDir.exists()) drawableDir.mkdirs()

        // Create light header
        val lightScaled = createScaledHeader(
            lightHeaderBitmap,
            targetWidth,
            targetHeight,
            lightScale,
            lightOffsetX,
            lightOffsetY
        )

        // Save light header
        val lightFile = File(drawableDir, HeaderConfig.LIGHT_HEADER_FILE_NAME)
        FileOutputStream(lightFile).use { out ->
            lightScaled.compress(Bitmap.CompressFormat.PNG, 100, out)
        }

        // Create dark header
        val darkScaled = createScaledHeader(
            darkHeaderBitmap,
            targetWidth,
            targetHeight,
            darkScale,
            darkOffsetX,
            darkOffsetY
        )

        // Save dark header
        val darkFile = File(drawableDir, HeaderConfig.DARK_HEADER_FILE_NAME)
        FileOutputStream(darkFile).use { out ->
            darkScaled.compress(Bitmap.CompressFormat.PNG, 100, out)
        }

        // Clean up
        lightScaled.recycle()
        darkScaled.recycle()

        // Return path to 'morphe_header' folder
        headerDir.absolutePath
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}

/**
 * Helper function to create scaled and positioned header bitmap
 * Crops the image based on preview transformations to match what user sees
 */
@SuppressLint("UseKtx")
private fun createScaledHeader(
    sourceBitmap: Bitmap,
    targetWidth: Int,
    targetHeight: Int,
    scale: Float,
    offsetX: Float,
    offsetY: Float
): Bitmap {
    // Create output bitmap
    val outputBitmap = createBitmap(targetWidth, targetHeight)
    val canvas = Canvas(outputBitmap)

    // Calculate scaled dimensions of source image
    val scaledSourceWidth = sourceBitmap.width * scale
    val scaledSourceHeight = sourceBitmap.height * scale

    // Calculate position (center + offset)
    // Normalize offset from preview coordinates to output coordinates
    val normalizedOffsetX = offsetX * (targetWidth.toFloat() / HeaderConfig.PREVIEW_WIDTH.value)
    val normalizedOffsetY = offsetY * (targetHeight.toFloat() / HeaderConfig.PREVIEW_HEIGHT.value)

    val left = (targetWidth - scaledSourceWidth) / 2 + normalizedOffsetX
    val top = (targetHeight - scaledSourceHeight) / 2 + normalizedOffsetY

    // Draw the scaled and positioned image (will be cropped to canvas bounds)
    val destRect = RectF(left, top, left + scaledSourceWidth, top + scaledSourceHeight)
    canvas.drawBitmap(sourceBitmap, null, destRect, null)

    return outputBitmap
}
