/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.shared

import android.annotation.SuppressLint
import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.net.Uri
import android.os.Build
import androidx.appcompat.content.res.AppCompatResources
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import androidx.core.graphics.createBitmap
import androidx.core.graphics.get
import androidx.documentfile.provider.DocumentFile
import app.morphe.manager.R
import app.morphe.manager.ui.screen.shared.colorpicker.ColorPresetGrid
import app.morphe.manager.ui.screen.shared.colorpicker.THEME_PRESET_COLORS
import app.morphe.manager.util.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Configuration constants for adaptive icon creation.
 */
private object AdaptiveIconConfig {
    // Folder structure
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
    const val MAX_SCALE = 2.5f
    // How far past its starting size a picture can still be zoomed in
    const val ZOOM_HEADROOM = 1.5f

    // A launcher shows the middle 72 dp of the 108 dp layer, and every icon shape keeps a
    // 66 dp circle, so the picture starts fitted into that circle
    const val VISIBLE_FRACTION = 72f / 108f
    const val SAFE_ZONE_FRACTION = 66f / 108f

    // A notification icon keeps its glyph to the middle 20 dp of its 24 dp canvas, and the status
    // bar draws that canvas about 15 dp across
    const val NOTIFICATION_LIVE_FRACTION = 20f / 24f
    // Notification icon must not exceed the status bar slot boundary
    const val MAX_NOTIFICATION_SCALE = 1f / NOTIFICATION_LIVE_FRACTION
    val STATUS_BAR_ICON_SIZE = 15.dp

    // Where the patch is given no themed or notification icon it keeps its Morphe logo, which fills
    // the notification canvas and spans this share of the themed layer
    const val FALLBACK_THEMED_LOGO_FRACTION = 0.44f

    // Leaves room around the editor to scroll the dialog without dragging the picture
    val EDITOR_MAX_WIDTH = 260.dp
    val LAUNCHER_ICON_SIZE = 56.dp
    val SCRIM = Color.Black.copy(alpha = 0.55f)

    // Viewport sizes for XML VectorDrawable output
    const val MONOCHROME_ADAPTIVE_VIEWPORT = 108
}

/**
 * Background colors offered for the icon: the theme palette, led by the white most launcher icons
 * sit on, which a theme color has no use for. The one extra swatch also fills the grid's last row.
 */
private val BackgroundPresetColors = listOf(Color.White) + THEME_PRESET_COLORS

/** A square in the middle of a square [side] across, spanning [fraction] of it. */
private fun centeredSquare(side: Float, fraction: Float): Rect {
    val inset = side * (1f - fraction) / 2
    return Rect(inset, inset, side - inset, side - inset)
}

/** Bounds of a [picture] on an icon layer [side] across, the whole of it fitted into the safe zone. */
private fun ImageTransform.placeOnLayer(side: Float, picture: IntSize): Rect = place(
    frame = Size(side, side),
    box = centeredSquare(side, AdaptiveIconConfig.SAFE_ZONE_FRACTION),
    size = picture,
    fitted = Rect(Offset.Zero, picture.toSize())
)

/** Bounds of a [picture] on a notification icon [side] across, its [content] kept to the live area. */
private fun notificationBounds(side: Float, picture: IntSize, content: Rect, scale: Float): Rect =
    ImageTransform(scale = scale).place(
        frame = Size(side, side),
        box = centeredSquare(side, AdaptiveIconConfig.NOTIFICATION_LIVE_FRACTION),
        size = picture,
        fitted = content
    )

/** An opaque picture starts covering the whole layer, so the background never shows around it. */
private fun PickedImage.startingTransform(): ImageTransform = if (isOpaque) {
    val aspect = maxOf(size.width, size.height).toFloat() / minOf(size.width, size.height)
    ImageTransform(scale = aspect / AdaptiveIconConfig.SAFE_ZONE_FRACTION)
} else {
    ImageTransform()
}

/** How far the picture zooms, with room past where it starts. */
private fun PickedImage.maxIconScale(): Float =
    maxOf(AdaptiveIconConfig.MAX_SCALE, startingTransform().scale * AdaptiveIconConfig.ZOOM_HEADROOM)

/**
 * Dialog for creating adaptive icons with foreground and background customization. Generates both
 * layers for every density, plus the monochrome layer and notification icon cut from the picture's
 * transparency where it has any.
 */
@Composable
fun AdaptiveIconCreatorDialog(
    packageName: String,
    onDismiss: () -> Unit,
    onIconCreated: (String) -> Unit
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    val foreground = remember { mutableStateOf<PickedImage?>(null) }
    val initialBackground = MaterialTheme.colorScheme.primaryContainer
    val background = remember { mutableStateOf(initialBackground) }
    val showColorPicker = remember { mutableStateOf(false) }
    val transform = remember { mutableStateOf(ImageTransform()) }
    val notificationScale = remember { mutableFloatStateOf(1f) }

    // Foreground image picker, resets all transforms when a new image is loaded
    val openForegroundPicker = rememberPickedImagePicker { picked ->
        foreground.value = picked
        transform.value = picked.startingTransform()
        notificationScale.floatValue = 1f
    }

    val successMessage = stringResource(R.string.adaptive_icon_created_success)
    val failureMessage = stringResource(R.string.adaptive_icon_creation_failed)
    val resetDescription = stringResource(R.string.adaptive_icon_reset_transform)

    val isCreating = remember { mutableStateOf(false) }

    // The patch keeps its Morphe logo for the icons a picture without transparency cannot be cut into
    val morpheLogo = rememberDrawablePainter(
        drawable = remember(context) { AppCompatResources.getDrawable(context, R.drawable.ic_mpp) }
    )
    val logoFallback = morpheLogo.takeIf { foreground.value?.isOpaque == true }

    // Folder picker for saving
    val openFolderPicker = rememberFolderPicker { uri ->
        val picked = foreground.value ?: return@rememberFolderPicker
        scope.launch {
            isCreating.value = true
            val result = createAdaptiveIcons(
                context = context,
                baseUri = uri,
                packageName = packageName,
                picture = picked,
                backgroundColor = background.value.toArgb(),
                transform = transform.value,
                notificationScale = notificationScale.floatValue
            )
            isCreating.value = false
            if (result != null) {
                context.toast(successMessage)
                onIconCreated(result)
                onDismiss()
            } else {
                context.toast(failureMessage)
            }
        }
    }

    CreatorDialogFrame(
        packageName = packageName,
        title = stringResource(R.string.adaptive_icon_create),
        guideTitle = stringResource(R.string.adaptive_icon_guide),
        guide = listOf(
            stringResource(R.string.adaptive_icon_guide_png_title) to stringResource(R.string.adaptive_icon_guide_png_body),
            stringResource(R.string.adaptive_icon_guide_safe_zones_title) to stringResource(R.string.adaptive_icon_guide_safe_zones_body),
            stringResource(R.string.adaptive_icon_guide_notification_title) to stringResource(R.string.adaptive_icon_guide_notification_body),
            stringResource(R.string.adaptive_icon_guide_monochrome_title) to stringResource(R.string.adaptive_icon_guide_monochrome_body)
        ),
        createEnabled = foreground.value != null,
        isCreating = isCreating.value,
        onCreate = { openFolderPicker() },
        onDismiss = onDismiss
    ) {
        // The picture placed on the icon, with the image picked right on the editor
        CreatorCard(title = stringResource(R.string.adaptive_icon_launcher)) {
            IconEditor(
                foreground = foreground.value?.image,
                maxScale = foreground.value?.maxIconScale() ?: AdaptiveIconConfig.MAX_SCALE,
                background = background.value,
                transform = transform.value,
                onTransformChange = { transform.value = it },
                onSelectImage = { openForegroundPicker() },
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )

            foreground.value?.let { picked ->
                Text(
                    text = stringResource(R.string.adaptive_icon_gesture_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = dialogSecondaryTextColor(),
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )

                // Adaptive scale slider
                ScaleSliderRow(
                    value = transform.value.scale,
                    onValueChange = { transform.value = transform.value.copy(scale = it) },
                    valueRange = AdaptiveIconConfig.MIN_SCALE..picked.maxIconScale()
                ) {
                    SliderResetAction(
                        visible = transform.value != picked.startingTransform(),
                        contentDescription = resetDescription,
                        onReset = { transform.value = picked.startingTransform() }
                    )
                }

                AppDialogOutlinedButton(
                    text = stringResource(R.string.adaptive_icon_change_image),
                    onClick = { openForegroundPicker() },
                    icon = Icons.Outlined.Image,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Transparency warning shown when the selected image has no transparent pixels
            AnimatedVisibility(
                visible = foreground.value?.isOpaque == true,
                enter = Animations.expandFadeEnter,
                exit = Animations.shrinkFadeExit
            ) {
                Notice(
                    text = stringResource(R.string.adaptive_icon_no_transparency_warning),
                    tone = SemanticTone.Warning,
                    density = NoticeDensity.Compact
                )
            }
        }

        // Background color, picked the way every color in the app is
        CreatorCard(title = stringResource(R.string.adaptive_icon_background_color)) {
            ColorPresetGrid(
                colors = BackgroundPresetColors,
                selected = background.value,
                onSelect = { background.value = it },
                onCustomClick = { showColorPicker.value = true }
            )
        }

        // Every icon the patch installs, as the device shows it
        CreatorCard(title = stringResource(R.string.adaptive_icon_preview)) {
            HomeScreenPreview(
                appName = KnownApps.getAppName(packageName),
                foreground = foreground.value?.image,
                background = background.value,
                transform = transform.value,
                themedFallback = logoFallback
            )

            // Status bar notification preview
            Text(
                text = stringResource(R.string.notification_icon_preview),
                style = MaterialTheme.typography.labelLarge,
                color = dialogSecondaryTextColor()
            )
            StatusBarPreview(
                picture = foreground.value,
                scale = notificationScale.floatValue,
                fallback = logoFallback
            )

            // Notification scale slider
            if (foreground.value?.isOpaque == false) {
                ScaleSliderRow(
                    value = notificationScale.floatValue,
                    onValueChange = { notificationScale.floatValue = it },
                    valueRange = AdaptiveIconConfig.MIN_SCALE..AdaptiveIconConfig.MAX_NOTIFICATION_SCALE
                ) {
                    SliderResetAction(
                        visible = notificationScale.floatValue != 1f,
                        contentDescription = resetDescription,
                        onReset = { notificationScale.floatValue = 1f }
                    )
                }
            }
        }
    }

    // Color picker dialog
    if (showColorPicker.value) {
        // The presets are already on the card behind the dialog, so repeating them inside it
        // would only push the panel down
        ColorPickerDialog(
            title = stringResource(R.string.adaptive_icon_background_color),
            currentColor = background.value.toHexString(),
            presets = emptyList(),
            onColorSelected = { color ->
                color.toColorOrNull()?.let { background.value = it }
                showColorPicker.value = false
            },
            onDismiss = { showColorPicker.value = false }
        )
    }
}

/** [logo] recolored to [tint], centered on a square [side] across and spanning [fraction] of it. */
private fun DrawScope.drawCenteredLogo(logo: Painter, side: Float, fraction: Float, tint: Color) {
    val box = side * fraction
    val inset = (side - box) / 2
    translate(inset, inset) {
        with(logo) { draw(Size(box, box), colorFilter = ColorFilter.tint(tint)) }
    }
}

/** Both layers of the icon on a square [side] across, from the draw origin. */
private fun DrawScope.drawIconLayers(
    side: Float,
    background: Color,
    foreground: ImageBitmap?,
    transform: ImageTransform,
    tint: Color? = null
) {
    drawRect(background, size = Size(side, side))
    if (foreground != null) {
        drawPicture(foreground, transform.placeOnLayer(side, IntSize(foreground.width, foreground.height)), tint)
    }
}

/**
 * The whole icon layer, dimmed where the launcher cuts it away and outlined with this device's
 * icon shape. Dragging moves the picture and pinching resizes it, with the safe zone and center
 * guides shown only meanwhile. Without a picture, a tap picks one.
 */
@Composable
private fun IconEditor(
    foreground: ImageBitmap?,
    maxScale: Float,
    background: Color,
    transform: ImageTransform,
    onTransformChange: (ImageTransform) -> Unit,
    onSelectImage: () -> Unit,
    modifier: Modifier = Modifier
) {
    val guideColor = remember(background) { background.contrastingContent() }
    // Read through state in the draw below, so a gesture redraws without rebuilding its cache
    val currentForeground by rememberUpdatedState(foreground)
    val currentBackground by rememberUpdatedState(background)
    val currentTransform by rememberUpdatedState(transform)
    val isGesturing = remember { mutableStateOf(false) }
    val guideAlpha by animateFloatAsState(
        targetValue = if (isGesturing.value || foreground == null) 1f else 0f,
        label = "editor_guides"
    )
    val selectImage = stringResource(R.string.adaptive_icon_select_image)

    val input = if (foreground != null) {
        Modifier.imageTransformGestures(
            transform = transform,
            scaleRange = AdaptiveIconConfig.MIN_SCALE..maxScale,
            onTransformChange = onTransformChange,
            onGestureChange = { isGesturing.value = it }
        )
    } else {
        Modifier.clickable(onClickLabel = selectImage, onClick = onSelectImage)
    }

    Box(
        modifier = modifier
            .widthIn(max = AdaptiveIconConfig.EDITOR_MAX_WIDTH)
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(RoundedCornerShape(Defaults.SectionCornerRadius))
            .then(input)
            .drawWithCache {
                val side = size.width
                val visibleShape = appIconOutline(centeredSquare(side, AdaptiveIconConfig.VISIBLE_FRACTION))
                val outline = Stroke(width = 2.dp.toPx())
                val dashed = dashedGuideStroke()
                onDrawBehind {
                    val shown = currentTransform
                    drawIconLayers(side, currentBackground, currentForeground, shown)
                    clipPath(visibleShape, ClipOp.Difference) { drawRect(AdaptiveIconConfig.SCRIM) }
                    drawPath(visibleShape, guideColor, style = outline)

                    if (guideAlpha > 0f) {
                        val guide = guideColor.copy(alpha = 0.7f * guideAlpha)
                        drawCircle(guide, radius = side * AdaptiveIconConfig.SAFE_ZONE_FRACTION / 2, style = dashed)
                        if (currentForeground != null) drawSnapGuides(shown, center, guide, dashed)
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        if (foreground == null) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Image,
                    contentDescription = null,
                    tint = guideColor,
                    modifier = Modifier.size(36.dp)
                )
                Text(
                    text = selectImage,
                    style = MaterialTheme.typography.titleSmall,
                    color = guideColor
                )
            }
        }
    }
}

/** Two colors of the home screen backdrop, taken from the wallpaper where the system shares them. */
private data class Backdrop(val base: Color, val accent: Color)

@Composable
private fun rememberBackdrop(): Backdrop {
    val context = LocalContext.current
    val fallback = Backdrop(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.tertiaryContainer)
    val wallpaper by produceState<Backdrop?>(null) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O_MR1) return@produceState
        value = withContext(Dispatchers.Default) {
            WallpaperManager.getInstance(context).getWallpaperColors(WallpaperManager.FLAG_SYSTEM)
        }?.let { colors ->
            val base = Color(colors.primaryColor.toArgb())
            Backdrop(base, colors.secondaryColor?.let { Color(it.toArgb()) } ?: base.darken(0.2f))
        }
    }
    return wallpaper ?: fallback
}

/** Colors the launcher draws themed icons in, the background first. */
@Composable
private fun themedIconColors(): Pair<Color, Color> {
    val dark = isSystemInDarkTheme()
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        return if (dark) {
            colorResource(android.R.color.system_neutral1_800) to colorResource(android.R.color.system_accent1_100)
        } else {
            colorResource(android.R.color.system_accent1_100) to colorResource(android.R.color.system_neutral2_700)
        }
    }
    return MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
}

/** The standard and the themed launcher icon side by side, over the wallpaper's colors. */
@Composable
private fun HomeScreenPreview(
    appName: String,
    foreground: ImageBitmap?,
    background: Color,
    transform: ImageTransform,
    themedFallback: Painter?
) {
    val backdrop = rememberBackdrop()
    val labelColor = remember(backdrop) { backdrop.base.contrastingContent() }
    val (themedBackground, themedForeground) = themedIconColors()

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(168.dp)
            .clip(RoundedCornerShape(Defaults.CardCornerRadius))
            .background(backdrop.base)
            .drawBehind {
                // Kept to the corner, since labels are colored for the base and lose contrast on it
                drawCircle(backdrop.accent, radius = size.height * 0.5f, center = Offset(size.width, 0f))
            },
        contentAlignment = Alignment.Center
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(32.dp)) {
            LauncherIcon(
                label = appName,
                caption = stringResource(R.string.adaptive_icon_standard),
                labelColor = labelColor
            ) { side -> drawIconLayers(side, background, foreground, transform) }
            LauncherIcon(
                label = appName,
                caption = stringResource(R.string.adaptive_icon_themed),
                labelColor = labelColor
            ) { side ->
                if (themedFallback != null) {
                    drawRect(themedBackground, size = Size(side, side))
                    drawCenteredLogo(themedFallback, side, AdaptiveIconConfig.FALLBACK_THEMED_LOGO_FRACTION, themedForeground)
                } else {
                    drawIconLayers(side, themedBackground, foreground, transform, tint = themedForeground)
                }
            }
        }
    }
}

/** An icon as the launcher lays it out, cut to its shape over the middle of [layers]. */
@Composable
private fun LauncherIcon(
    label: String,
    caption: String,
    labelColor: Color,
    layers: DrawScope.(side: Float) -> Unit
) {
    Column(
        modifier = Modifier.width(88.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(AdaptiveIconConfig.LAUNCHER_ICON_SIZE)
                .clip(AppIconShape)
                .drawBehind {
                    val side = size.width / AdaptiveIconConfig.VISIBLE_FRACTION
                    val inset = (size.width - side) / 2
                    translate(inset, inset) { layers(side) }
                }
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = labelColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = caption,
            style = MaterialTheme.typography.labelSmall,
            color = labelColor.copy(alpha = 0.7f)
        )
    }
}

/**
 * Status bar simulation showing the notification icon at actual size. Content outside its slot is
 * clipped, as Android does in the real status bar.
 */
@Composable
private fun StatusBarPreview(
    picture: PickedImage?,
    scale: Float,
    fallback: Painter?
) {
    val contentColor = MaterialTheme.colorScheme.onSurface
    val shape = RoundedCornerShape(Defaults.CompactCornerRadius)

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
            // Notification icon at actual status bar size, tinted as Android renders
            // notification small icons
            Box(
                modifier = Modifier
                    .size(AdaptiveIconConfig.STATUS_BAR_ICON_SIZE)
                    .clipToBounds()
                    .drawBehind {
                        if (fallback != null) {
                            drawCenteredLogo(fallback, size.width, fraction = 1f, tint = contentColor)
                        } else if (picture != null) {
                            val bounds = notificationBounds(size.width, picture.size, picture.content, scale)
                            drawPicture(picture.image, bounds, tint = contentColor)
                        }
                    }
            )
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
                modifier = Modifier.size(Defaults.IconSizeSmall),
                tint = contentColor
            )
            Icon(
                imageVector = Icons.Outlined.Wifi,
                contentDescription = null,
                modifier = Modifier.size(Defaults.IconSizeSmall),
                tint = contentColor
            )
            Icon(
                imageVector = Icons.Outlined.BatteryFull,
                contentDescription = null,
                modifier = Modifier.size(Defaults.IconSizeSmall),
                tint = contentColor
            )
        }
    }
}

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
    picture: PickedImage,
    backgroundColor: Int,
    transform: ImageTransform,
    notificationScale: Float
): String? = withContext(Dispatchers.IO) {
    try {
        val baseDocDir = DocumentFile.fromTreeUri(context, baseUri) ?: return@withContext null

        // Create directory structure: morphe_branding/YOUTUBE_ICONS_FOLDER_NAME or YTM_ICONS_FOLDER_NAME
        val brandingDocDir = baseDocDir.brandingFolder() ?: return@withContext null
        val iconsDocDir = brandingDocDir.getOrCreateDir(AdaptiveIconConfig.iconFolderName(packageName))
            ?: return@withContext null

        val bitmapPaint = smoothBitmapPaint()

        // Generate adaptive icon PNGs (foreground + background) for all densities
        AdaptiveIconConfig.DENSITY_CONFIGS.forEach { densityConfig ->
            val mipmapDocDir = iconsDocDir.getOrCreateDir(densityConfig.folderName) ?: return@forEach
            val size = densityConfig.size

            val backgroundBitmap = createBitmap(size, size).apply { eraseColor(backgroundColor) }
            val foregroundScaled = renderPicture(
                source = picture.bitmap,
                width = size,
                height = size,
                bounds = transform.placeOnLayer(size.toFloat(), picture.size),
                paint = bitmapPaint
            )
            mipmapDocDir.writePng(context, AdaptiveIconConfig.BACKGROUND_FILE_NAME, backgroundBitmap)
            mipmapDocDir.writePng(context, AdaptiveIconConfig.FOREGROUND_FILE_NAME, foregroundScaled)
        }

        if (!picture.isOpaque) {
            // Notification icons are white wherever the foreground is not transparent, per Material Design
            val whitePaint = Paint(bitmapPaint).apply {
                colorFilter = PorterDuffColorFilter(android.graphics.Color.WHITE, PorterDuff.Mode.SRC_IN)
            }
            AdaptiveIconConfig.NOTIFICATION_DENSITY_CONFIGS.forEach { densityConfig ->
                val drawableDocDir = iconsDocDir.getOrCreateDir(densityConfig.folderName) ?: return@forEach
                val size = densityConfig.size
                val bounds = notificationBounds(size.toFloat(), picture.size, picture.content, notificationScale)
                val notificationBitmap = renderPicture(picture.bitmap, size, size, bounds, whitePaint)
                drawableDocDir.writePng(context, AdaptiveIconConfig.NOTIFICATION_FILE_NAME, notificationBitmap)
            }

            // Generate XML VectorDrawable files in a 'drawable' folder
            val drawableDocDir = iconsDocDir.getOrCreateDir(AdaptiveIconConfig.DRAWABLE_FOLDER_NAME)
            if (drawableDocDir != null) {
                // Monochrome adaptive layer: render at 16x oversample (1728x1728) so each scanline
                // is 0.0625 viewport units tall, making stair-stepping sub-pixel on all densities.
                val monoOversample = 16
                val monoSize = AdaptiveIconConfig.MONOCHROME_ADAPTIVE_VIEWPORT * monoOversample
                val adaptiveMonoBmp = renderPicture(
                    source = picture.bitmap,
                    width = monoSize,
                    height = monoSize,
                    bounds = transform.placeOnLayer(monoSize.toFloat(), picture.size),
                    paint = bitmapPaint
                )
                val adaptiveMonoXml = createMonochromeVectorXml(
                    bitmap = adaptiveMonoBmp,
                    coordinateScale = 1f / monoOversample
                )
                adaptiveMonoBmp.recycle()
                saveXmlToDocFile(context, drawableDocDir, adaptiveMonoXml)
            }
        } else {
            // The patch keeps its own logo where these are missing, so ones left from an earlier
            // picture must not stay behind
            removeCutOutIcons(iconsDocDir)
        }

        // Convert back to a real path so the patcher can reference it as a patch option value
        iconsDocDir.uri.toFilePath()
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}

/** Deletes the notification and monochrome icons from [iconsDocDir], wherever they were written. */
private fun removeCutOutIcons(iconsDocDir: DocumentFile) {
    AdaptiveIconConfig.NOTIFICATION_DENSITY_CONFIGS.forEach { densityConfig ->
        iconsDocDir.findFile(densityConfig.folderName)
            ?.findFile(AdaptiveIconConfig.NOTIFICATION_FILE_NAME)
            ?.delete()
    }
    iconsDocDir.findFile(AdaptiveIconConfig.DRAWABLE_FOLDER_NAME)
        ?.findFile(AdaptiveIconConfig.MONOCHROME_ADAPTIVE_FILE_NAME)
        ?.delete()
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
