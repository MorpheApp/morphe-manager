/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.shared

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.Recommend
import androidx.compose.material.icons.outlined.Science
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import app.morphe.manager.R
import app.morphe.manager.util.androidVersionName

/**
 * What a version list can say about one version. Declared in the order tags are shown.
 */
sealed interface VersionTag {
    /** The device runs an older Android than this version needs. */
    data class RequiresAndroid(val minSdk: Int) : VersionTag

    /** Unusable here, without a version to name. */
    data object Incompatible : VersionTag

    /** No enabled source carries patches for it. */
    data object Unsupported : VersionTag

    /** What the sources suggest for this app. */
    data object Recommended : VersionTag

    /** Patchable, but the source flags the result as untested. */
    data object Experimental : VersionTag

    /** The stock APK, offered because nothing patched exists yet. */
    data object Unpatched : VersionTag

    /** The version the saved original APK is at. */
    data object Saved : VersionTag
}

val VersionTag.tone: SemanticTone
    get() = when (this) {
        is VersionTag.RequiresAndroid, VersionTag.Incompatible, VersionTag.Unsupported ->
            SemanticTone.Error

        VersionTag.Recommended -> SemanticTone.Primary
        VersionTag.Experimental, VersionTag.Unpatched -> SemanticTone.Warning
        VersionTag.Saved -> SemanticTone.Neutral
    }

val VersionTag.icon: ImageVector
    get() = when (this) {
        is VersionTag.RequiresAndroid -> Icons.Outlined.PhoneAndroid
        VersionTag.Incompatible, VersionTag.Unsupported -> Icons.Outlined.Block
        VersionTag.Recommended -> Icons.Outlined.Recommend
        VersionTag.Experimental -> Icons.Outlined.Science
        VersionTag.Unpatched -> Icons.Outlined.Inventory2
        VersionTag.Saved -> Icons.Outlined.History
    }

@Composable
fun VersionTag.label(): String = when (this) {
    is VersionTag.RequiresAndroid ->
        stringResource(R.string.home_version_requires_android, minSdk.androidVersionName())

    VersionTag.Incompatible -> stringResource(R.string.home_apk_availability_incompatible_label)
    VersionTag.Unsupported ->
        stringResource(R.string.home_dialog_unsupported_version_unsupported_label)

    VersionTag.Recommended -> stringResource(R.string.home_apk_availability_recommended_label)
    VersionTag.Experimental ->
        stringResource(R.string.home_dialog_unsupported_version_experimental_label)

    VersionTag.Unpatched -> stringResource(R.string.home_apk_availability_unpatched_label)
    VersionTag.Saved -> stringResource(R.string.saved)
}

/**
 * Which tags one version carries.
 *
 * A version the device cannot install, or that no patch covers, answers every other question
 * about it, so nothing is listed beside it. The rest are independent, because they answer
 * different questions: the recommended version can be the saved one too.
 */
fun versionTagsOf(
    requiresAndroidSdk: Int? = null,
    isIncompatible: Boolean = false,
    isUnsupported: Boolean = false,
    isExperimental: Boolean = false,
    isUnpatched: Boolean = false,
    isRecommended: Boolean = false,
    isSaved: Boolean = false
): List<VersionTag> = buildList {
    val blocking = when {
        requiresAndroidSdk != null -> VersionTag.RequiresAndroid(requiresAndroidSdk)
        isIncompatible -> VersionTag.Incompatible
        isUnsupported -> VersionTag.Unsupported
        else -> null
    }

    if (blocking != null) {
        add(blocking)
    } else {
        if (isRecommended) add(VersionTag.Recommended)
        if (isExperimental) add(VersionTag.Experimental)
        if (isUnpatched) add(VersionTag.Unpatched)
    }

    // Saved is about the APK on hand rather than the version itself, so it is shown either way
    if (isSaved) add(VersionTag.Saved)
}

/**
 * The color the version string itself takes, so text and badge never disagree about it. Only
 * an experimental version recolors: the rest either dim their whole row or are carried by the
 * badge alone.
 */
@Composable
fun List<VersionTag>.versionTextColor(default: Color): Color =
    if (contains(VersionTag.Experimental)) SemanticTone.Warning.accent else default

/** Tag labels for a row's content description, read out in the order they are shown. */
@Composable
fun List<VersionTag>.labels(): List<String> = map { it.label() }

@Composable
fun VersionTagBadge(tag: VersionTag, modifier: Modifier = Modifier) {
    StatusBadge(
        modifier = modifier,
        text = tag.label(),
        icon = tag.icon,
        tone = tag.tone
    )
}

/**
 * Every tag of a version, stacked at the edge of its row. A long version string shortens
 * itself rather than pushing the tags out of shape, and they line up down the card edge.
 */
@Composable
fun VersionTagBadges(tags: List<VersionTag>, modifier: Modifier = Modifier) {
    if (tags.isEmpty()) return

    StatusBadgeColumn(modifier = modifier) {
        tags.forEach { VersionTagBadge(it) }
    }
}
