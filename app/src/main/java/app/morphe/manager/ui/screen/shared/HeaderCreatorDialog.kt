/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.shared

import android.content.Context
import android.net.Uri
import androidx.annotation.StringRes
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.documentfile.provider.DocumentFile
import app.morphe.manager.R
import app.morphe.manager.util.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val MIN_SCALE = 0.3f
private const val MAX_SCALE = 3f

/** A header starts at the left edge of the app's own logo, centered on it across. */
private val HeaderAnchor = Offset(0f, 0.5f)

/** One theme of a header: the file the patch reads it from, and how the app bar looks in it. */
private enum class HeaderTheme(
    val fileName: String,
    @param:StringRes val titleResId: Int,
    val bar: Color,
    val content: Color,
    val field: Color
) {
    Light(
        fileName = "morphe_header_custom_light.png",
        titleResId = R.string.header_creator_light_theme,
        bar = Color.White,
        content = Color(0xFF0F0F0F),
        field = Color(0xFFF2F2F2)
    ),
    Dark(
        fileName = "morphe_header_custom_dark.png",
        titleResId = R.string.header_creator_dark_theme,
        bar = Color(0xFF0F0F0F),
        content = Color.White,
        field = Color(0xFF272727)
    )
}

/** Folder of one screen density, and the size of the header image in it. */
private class HeaderDensity(val folderName: String, val width: Int, val height: Int)

/**
 * How an app lays out its header logo, measured from its own: the image slot, where the stock logo
 * sits in it, and the gap the app bar leaves before it. The densities are the sizes the patch takes.
 */
private enum class HeaderLayout(
    val folderName: String,
    val slot: DpSize,
    /** The stock logo within [slot], in dp. */
    val logoArea: Rect,
    val startInset: Dp,
    val themes: List<HeaderTheme>,
    val densities: List<HeaderDensity>
) {
    YouTube(
        folderName = "morphe_header_youtube",
        slot = DpSize(129.dp, 48.dp),
        logoArea = Rect(16f, 14f, 117f, 34f),
        startInset = 0.dp,
        themes = listOf(HeaderTheme.Light, HeaderTheme.Dark),
        densities = listOf(
            HeaderDensity("drawable-hdpi", 194, 72),
            HeaderDensity("drawable-xhdpi", 258, 96),
            HeaderDensity("drawable-xxhdpi", 387, 144),
            HeaderDensity("drawable-xxxhdpi", 516, 192)
        )
    ),

    // YouTube Music has no light theme, and its logo fills its slot
    Music(
        folderName = "morphe_header_music",
        slot = DpSize(80.dp, 24.dp),
        logoArea = Rect(0f, 0f, 80f, 24f),
        startInset = 16.dp,
        themes = listOf(HeaderTheme.Dark),
        densities = listOf(
            HeaderDensity("drawable-hdpi", 121, 36),
            HeaderDensity("drawable-xhdpi", 160, 48),
            HeaderDensity("drawable-xxhdpi", 240, 72),
            HeaderDensity("drawable-xxxhdpi", 320, 96)
        )
    );

    val aspectRatio get() = slot.width / slot.height

    /** [logoArea] stretched over a [frame] of the slot's proportions. */
    fun logoBox(frame: Size): Rect {
        val x = frame.width / slot.width.value
        val y = frame.height / slot.height.value
        return Rect(logoArea.left * x, logoArea.top * y, logoArea.right * x, logoArea.bottom * y)
    }

    companion object {
        fun of(packageName: String) = if (packageName == KnownApps.YOUTUBE_MUSIC) Music else YouTube
    }
}

/** Bounds of [picture] in a [frame] of the header slot, its visible part fitted to the stock logo. */
private fun ImageTransform.placeInSlot(layout: HeaderLayout, frame: Size, picture: PickedImage): Rect =
    place(frame, layout.logoBox(frame), picture.size, picture.content, HeaderAnchor)

/** One theme's header: the picture picked for it and where it sits in the slot. */
@Stable
private class HeaderVariant(val theme: HeaderTheme) {
    var picture by mutableStateOf<PickedImage?>(null)
    var transform by mutableStateOf(ImageTransform())
}

/**
 * Dialog for creating custom headers, one per theme the app has. Each is placed in an editor of the
 * header slot and shown in the app bar it lands in, then written for every density the patch takes.
 */
@Composable
fun HeaderCreatorDialog(
    packageName: String,
    onDismiss: () -> Unit,
    onHeaderCreated: (String) -> Unit
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val layout = remember(packageName) { HeaderLayout.of(packageName) }
    val variants = remember(layout) { layout.themes.map(::HeaderVariant) }

    val successMessage = stringResource(R.string.header_creator_success)
    val failureMessage = stringResource(R.string.header_creator_failed)
    val isCreating = remember { mutableStateOf(false) }

    // Folder picker for saving
    val openFolderPicker = rememberFolderPicker { uri ->
        scope.launch {
            isCreating.value = true
            val result = createHeaderFiles(context, uri, layout, variants)
            isCreating.value = false
            if (result != null) {
                context.toast(successMessage)
                onHeaderCreated(result)
                onDismiss()
            } else {
                context.toast(failureMessage)
            }
        }
    }

    CreatorDialogFrame(
        packageName = packageName,
        title = stringResource(R.string.header_creator_create),
        guideTitle = stringResource(R.string.header_creator_guide),
        guide = listOf(
            stringResource(R.string.header_creator_guide_image_title) to stringResource(R.string.header_creator_guide_image_body),
            stringResource(R.string.header_creator_guide_themes_title) to stringResource(R.string.header_creator_guide_themes_body),
            stringResource(R.string.header_creator_guide_positioning_title) to stringResource(R.string.header_creator_guide_positioning_body)
        ),
        createEnabled = variants.all { it.picture != null },
        isCreating = isCreating.value,
        onCreate = { openFolderPicker() },
        onDismiss = onDismiss
    ) {
        variants.forEach { variant -> HeaderVariantCard(layout, variant) }
    }
}

/** Card of one theme's header: its editor, the app bar it lands in, and its size controls. */
@Composable
private fun HeaderVariantCard(layout: HeaderLayout, variant: HeaderVariant) {
    val openPicker = rememberPickedImagePicker { picked ->
        variant.picture = picked
        // Reset transform when new image is loaded
        variant.transform = ImageTransform()
    }

    CreatorCard(title = stringResource(variant.theme.titleResId)) {
        HeaderEditor(
            layout = layout,
            theme = variant.theme,
            picture = variant.picture,
            transform = variant.transform,
            onTransformChange = { variant.transform = it },
            onSelectImage = { openPicker() }
        )

        if (variant.picture != null) {
            Text(
                text = stringResource(R.string.adaptive_icon_gesture_hint),
                style = MaterialTheme.typography.bodySmall,
                color = dialogSecondaryTextColor(),
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
        }

        Text(
            text = stringResource(R.string.adaptive_icon_preview),
            style = MaterialTheme.typography.labelLarge,
            color = dialogSecondaryTextColor()
        )
        AppBarPreview(layout, variant.theme, variant.picture, variant.transform)

        if (variant.picture != null) {
            ScaleSliderRow(
                value = variant.transform.scale,
                onValueChange = { variant.transform = variant.transform.copy(scale = it) },
                valueRange = MIN_SCALE..MAX_SCALE
            ) {
                SliderResetAction(
                    visible = variant.transform != ImageTransform(),
                    contentDescription = stringResource(R.string.adaptive_icon_reset_transform),
                    onReset = { variant.transform = ImageTransform() }
                )
            }

            AppDialogOutlinedButton(
                text = stringResource(R.string.adaptive_icon_change_image),
                onClick = { openPicker() },
                icon = Icons.Outlined.Image,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

/**
 * The header slot enlarged to the card's width, over the app bar's color. The stock logo's place is
 * outlined, and guides show while the picture is moved. Without a picture, a tap picks one.
 */
@Composable
private fun HeaderEditor(
    layout: HeaderLayout,
    theme: HeaderTheme,
    picture: PickedImage?,
    transform: ImageTransform,
    onTransformChange: (ImageTransform) -> Unit,
    onSelectImage: () -> Unit
) {
    // Read through state in the draw below, so a gesture redraws without rebuilding its cache
    val currentPicture by rememberUpdatedState(picture)
    val currentTransform by rememberUpdatedState(transform)
    val isGesturing = remember { mutableStateOf(false) }
    val guideAlpha by animateFloatAsState(
        targetValue = if (isGesturing.value || picture == null) 0.7f else 0.3f,
        label = "header_guides"
    )
    val selectImage = stringResource(R.string.adaptive_icon_select_image)
    val shape = RoundedCornerShape(Defaults.CompactCornerRadius)

    val input = if (picture != null) {
        Modifier.imageTransformGestures(
            transform = transform,
            scaleRange = MIN_SCALE..MAX_SCALE,
            onTransformChange = onTransformChange,
            onGestureChange = { isGesturing.value = it }
        )
    } else {
        Modifier.clickable(onClickLabel = selectImage, onClick = onSelectImage)
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(layout.aspectRatio)
            .clip(shape)
            .background(theme.bar)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
            .then(input)
            .drawWithCache {
                val logoBox = layout.logoBox(size)
                val anchor = logoBox.pointAt(HeaderAnchor)
                val dashed = dashedGuideStroke()
                onDrawBehind {
                    val shown = currentTransform
                    currentPicture?.let { drawPicture(it.image, shown.placeInSlot(layout, size, it)) }

                    val guide = theme.content.copy(alpha = guideAlpha)
                    drawRect(guide, logoBox.topLeft, logoBox.size, style = dashed)
                    if (isGesturing.value) drawSnapGuides(shown, anchor, guide, dashed)
                }
            },
        contentAlignment = Alignment.Center
    ) {
        if (picture == null) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Outlined.Image,
                    contentDescription = null,
                    tint = theme.content,
                    modifier = Modifier.size(24.dp)
                )
                Text(
                    text = selectImage,
                    style = MaterialTheme.typography.titleSmall,
                    color = theme.content
                )
            }
        }
    }
}

/** The app bar the header lands in, at its real size, with the controls the app puts beside it. */
@Composable
private fun AppBarPreview(
    layout: HeaderLayout,
    theme: HeaderTheme,
    picture: PickedImage?,
    transform: ImageTransform
) {
    val shape = RoundedCornerShape(Defaults.CompactCornerRadius)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(shape)
            .background(theme.bar)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Spacer(Modifier.width(layout.startInset))
        Box(
            modifier = Modifier
                .size(layout.slot)
                .clipToBounds()
                .drawBehind {
                    picture?.let { drawPicture(it.image, transform.placeInSlot(layout, size, it)) }
                }
        )

        when (layout) {
            HeaderLayout.YouTube -> {
                // The search field the app bar widens into beside the logo
                Box(
                    modifier = Modifier
                        .padding(start = 2.dp, end = 10.dp)
                        .weight(1f)
                        .height(32.dp)
                        .drawBehind {
                            drawRoundRect(theme.field, cornerRadius = CornerRadius(size.height / 2))
                        },
                    contentAlignment = Alignment.CenterEnd
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Search,
                        contentDescription = null,
                        tint = theme.content,
                        modifier = Modifier
                            .padding(end = 10.dp)
                            .size(Defaults.IconSizeSmall)
                    )
                }
            }

            HeaderLayout.Music -> {
                Spacer(Modifier.weight(1f))
                Icon(
                    imageVector = Icons.Outlined.Notifications,
                    contentDescription = null,
                    tint = theme.content,
                    modifier = Modifier
                        .padding(12.dp)
                        .size(Defaults.IconSize)
                )
                Box(
                    modifier = Modifier
                        .padding(start = 8.dp, end = 12.dp)
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(theme.field)
                )
            }
        }
    }
}

/**
 * Writes each theme's header for every density the patch takes. Returns the real file-system path
 * to the header folder, for use as a patch option value, or null if creation failed.
 */
private suspend fun createHeaderFiles(
    context: Context,
    baseUri: Uri,
    layout: HeaderLayout,
    variants: List<HeaderVariant>
): String? = withContext(Dispatchers.IO) {
    try {
        val headerDocDir = DocumentFile.fromTreeUri(context, baseUri)
            ?.brandingFolder()
            ?.getOrCreateDir(layout.folderName)
            ?: return@withContext null

        layout.densities.forEach { density ->
            val drawableDocDir = headerDocDir.getOrCreateDir(density.folderName) ?: return@forEach
            val frame = Size(density.width.toFloat(), density.height.toFloat())
            variants.forEach { variant ->
                val picture = variant.picture ?: return@forEach
                val bounds = variant.transform.placeInSlot(layout, frame, picture)
                val header = renderPicture(picture.bitmap, density.width, density.height, bounds)
                drawableDocDir.writePng(context, variant.theme.fileName, header)
            }
        }

        // Convert back to a real path so the patcher can reference it as a patch option value
        headerDocDir.uri.toFilePath()
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}
