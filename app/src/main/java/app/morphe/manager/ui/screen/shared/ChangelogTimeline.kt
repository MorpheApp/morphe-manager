/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.shared

import android.text.format.DateUtils
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.automirrored.outlined.Notes
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import app.morphe.manager.R
import app.morphe.manager.util.*
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.temporal.ChronoUnit
import java.util.Locale

// Timeline geometry: the rail runs through the middle of a slot at the leading edge, and each
// node sits level with the first line of its release header
private val RailSlotWidth = 18.dp
private val RailContentGap = 14.dp
private val RailStrokeWidth = 2.dp
private val NodeRadius = 6.dp
private val NodeHaloRadius = 10.dp
private val NodeCenterY = 16.dp
private val ReleaseSpacing = 20.dp

// Shared by the content and the placeholders that stand in for it, so loading does not shift it
private val SummaryPadding = 20.dp
private val SummaryArrowSize = 18.dp
private val SectionIconSize = 28.dp
private val SectionIconShape = RoundedCornerShape(9.dp)
private val SectionIconGap = 10.dp
private val ItemDotSize = 6.dp

/** Sets the dot level with the middle of the first line: the change's, or its smaller scope label's. */
private val ItemDotOffset = 7.dp
private val ItemDotOffsetScoped = 5.dp
private val ItemPadding = PaddingValues(horizontal = 14.dp, vertical = 11.dp)

/** Sections longer than this show their first [SECTION_PREVIEW_SIZE] changes until opened. */
private const val SECTION_PREVIEW_LIMIT = 6
private const val SECTION_PREVIEW_SIZE = 5

/** Placeholders the loading state lays out, sized to fill a dialog without scrolling. */
private const val FOLDED_RELEASE_PLACEHOLDERS = 2
private const val SECTION_ITEM_PLACEHOLDERS = 3

/** Releases this many days old or newer read as "2 days ago" rather than as a date. */
private const val RELATIVE_DATE_DAYS = 6

/** Inline code, bold text and links, the only formatting a single change carries. */
private val INLINE_FORMATTING = Regex("""`([^`]+)`|\*\*(.+?)\*\*|\[([^]]+)]\(([^)\s]+)\)""")

/** Marker a release carries on the timeline, listed from the strongest when several apply. */
enum class ChangelogBadge(@param:StringRes val label: Int, val tone: SemanticTone) {
    INSTALLED(R.string.installed, SemanticTone.Success),
    IN_USE(R.string.changelog_badge_in_use, SemanticTone.Success),
    PRERELEASE(R.string.changelog_badge_prerelease, SemanticTone.Warning),
    LATEST(R.string.changelog_badge_latest, SemanticTone.Primary)
}

/**
 * Loading state of the changelog timeline, laid out like the list it stands in for: the newest
 * release open on a section of changes, older ones folded below, so nothing moves once it loads.
 *
 * @param withSummary Whether the list opens on the update summary, as in the update dialog.
 */
@Composable
fun ChangelogListLoading(
    modifier: Modifier = Modifier,
    withSummary: Boolean = false
) {
    Column(modifier = modifier.fillMaxWidth()) {
        if (withSummary) {
            UpdateSummaryPlaceholder()
            Spacer(modifier = Modifier.height(Defaults.ContentPaddingMedium))
        }

        TimelineRow(badge = null, isFirst = true, isLast = false) {
            ReleaseHeaderPlaceholder()
            SectionGroupPlaceholder(modifier = Modifier.padding(top = Defaults.ItemSpacing))
        }
        repeat(FOLDED_RELEASE_PLACEHOLDERS) { index ->
            TimelineRow(
                badge = null,
                isFirst = false,
                isLast = index == FOLDED_RELEASE_PLACEHOLDERS - 1
            ) {
                ReleaseHeaderPlaceholder()
            }
        }
    }
}

/** Stands in for [ChangelogUpdateSummary]: the version step, the release count and one tally. */
@Composable
private fun UpdateSummaryPlaceholder() {
    SectionCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(SummaryPadding),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Defaults.ContentPadding)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Defaults.ItemSpacing)
            ) {
                ShimmerBox(
                    modifier = Modifier.size(width = 96.dp, height = statusBadgeHeight),
                    shape = Defaults.PillShape
                )
                ShimmerBox(modifier = Modifier.size(SummaryArrowSize), shape = CircleShape)
                ShimmerBox(
                    modifier = Modifier.size(width = 96.dp, height = statusBadgeHeight),
                    shape = Defaults.PillShape
                )
            }
            ShimmerText(widthFraction = 0.5f, height = 20.dp, cornerRadius = 6.dp)
            ShimmerBox(
                modifier = Modifier.size(width = 110.dp, height = 36.dp),
                shape = Defaults.PillShape
            )
        }
    }
}

/** Stands in for the version line and the summary line under it. */
@Composable
private fun ReleaseHeaderPlaceholder() {
    Column(
        modifier = Modifier.padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        ShimmerText(widthFraction = 0.45f, height = 22.dp, cornerRadius = 6.dp)
        ShimmerText(widthFraction = 0.6f, height = 14.dp)
    }
}

/** Stands in for a section: its icon and title over a card of changes. */
@Composable
private fun SectionGroupPlaceholder(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(Defaults.ContentPaddingSmall)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(SectionIconGap)
        ) {
            ShimmerBox(
                modifier = Modifier.size(SectionIconSize),
                shape = SectionIconShape
            )
            ShimmerText(widthFraction = 0.3f, height = 16.dp)
        }

        SectionCard(modifier = Modifier.fillMaxWidth()) {
            Column {
                repeat(SECTION_ITEM_PLACEHOLDERS) { index ->
                    if (index > 0) ItemDivider()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(ItemPadding),
                        horizontalArrangement = Arrangement.spacedBy(Defaults.ItemSpacing)
                    ) {
                        ShimmerBox(
                            modifier = Modifier
                                .padding(top = ItemDotOffset)
                                .size(ItemDotSize),
                            shape = CircleShape
                        )
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            ShimmerText(widthFraction = if (index % 2 == 0) 0.9f else 0.75f, height = 14.dp)
                            ShimmerText(widthFraction = 0.5f, height = 14.dp)
                        }
                    }
                }
            }
        }
    }
}

/**
 * One release on the changelog timeline: its node on the rail, a header that folds the release
 * open and shut, and its changes grouped by section.
 *
 * @param isFirst Whether the rail starts at this release instead of coming down from the one above.
 * @param isLast Whether the rail ends at this release.
 */
@Composable
fun ChangelogRelease(
    entry: ChangelogEntry,
    badge: ChangelogBadge?,
    isFirst: Boolean,
    isLast: Boolean,
    expanded: Boolean,
    onToggle: () -> Unit,
    isSectionExpanded: (Int) -> Boolean,
    onToggleSection: (Int) -> Unit
) {
    val sections = remember(entry.content) { ChangelogParser.sections(entry.content) }
    val hasBody = sections.isNotEmpty()

    TimelineRow(badge = badge, isFirst = isFirst, isLast = isLast) {
        ReleaseHeader(
            entry = entry,
            badge = badge,
            sections = sections,
            expanded = expanded && hasBody,
            onToggle = onToggle.takeIf { hasBody }
        )
        AnimatedVisibility(
            visible = expanded && hasBody,
            enter = Animations.expandTopFadeIn,
            exit = Animations.shrinkTopFadeOut
        ) {
            ReleaseBody(
                sections = sections,
                isSectionExpanded = isSectionExpanded,
                onToggleSection = onToggleSection,
                modifier = Modifier.padding(top = Defaults.ItemSpacing)
            )
        }
    }
}

/** Stands at the end of the timeline while older releases load. */
@Composable
fun ChangelogOlderLoading() {
    TimelineRow(badge = null, isFirst = false, isLast = true) {
        ReleaseHeaderPlaceholder()
    }
}

/** Ends the timeline where older releases failed to load, with a way to try again. */
@Composable
fun ChangelogOlderFailed(onRetry: () -> Unit) {
    TimelineRow(badge = null, isFirst = false, isLast = true) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = stringResource(R.string.changelog_older_failed),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 5.dp)
            )
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(Defaults.CompactCornerRadius))
                    .clickable(role = Role.Button, onClick = onRetry)
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Refresh,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = stringResource(R.string.retry),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

/** Names the stretch of the timeline below it, with the rail running past without a node. */
@Composable
fun ChangelogTimelineLabel(text: String) {
    TimelineRow(badge = null, isFirst = false, isLast = false, hasNode = false) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}

/**
 * A place on the timeline: the rail and a node at the leading edge, [content] beside them.
 *
 * @param hasNode Whether the place is a release of its own, rather than a note along the rail.
 */
@Composable
private fun TimelineRow(
    badge: ChangelogBadge?,
    isFirst: Boolean,
    isLast: Boolean,
    hasNode: Boolean = true,
    content: @Composable ColumnScope.() -> Unit
) {
    // A node takes the tone of its badge, so the two read as one marker
    val tone = badge?.tone ?: SemanticTone.Primary
    val colors = TimelineColors(
        rail = MaterialTheme.colorScheme.outlineVariant,
        accent = tone.accent,
        halo = tone.container,
        muted = MaterialTheme.colorScheme.outline
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .drawBehind { drawTimeline(badge, isFirst, isLast, hasNode, colors) }
    ) {
        Spacer(modifier = Modifier.width(RailSlotWidth + RailContentGap))
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(bottom = if (isLast) 0.dp else ReleaseSpacing),
            content = content
        )
    }
}

/**
 * Head of the update dialog: the step from the installed version to the new one, and what the
 * releases in between bring together.
 */
@Composable
fun ChangelogUpdateSummary(
    fromVersion: String,
    toVersion: String,
    entries: List<ChangelogEntry>,
    modifier: Modifier = Modifier
) {
    val sections = remember(entries) {
        entries.flatMap { ChangelogParser.sections(it.content) }
    }
    val features = sections.countOf(ChangelogSection.Kind.FEATURES)
    val fixes = sections.countOf(ChangelogSection.Kind.FIXES)

    SectionCard(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(SummaryPadding),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Defaults.ContentPadding)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Defaults.ItemSpacing)
            ) {
                StatusBadge(text = fromVersion.withVersionPrefix())
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.ArrowForward,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(SummaryArrowSize)
                )
                StatusBadge(text = toVersion.withVersionPrefix(), tone = SemanticTone.Primary)
            }

            Text(
                text = pluralStringResource(
                    R.plurals.changelog_new_releases,
                    entries.size,
                    entries.size.toString()
                ),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = LocalDialogTextColor.current,
                textAlign = TextAlign.Center
            )

            when {
                features > 0 && fixes > 0 -> Row(
                    horizontalArrangement = Arrangement.spacedBy(Defaults.ContentPaddingSmall)
                ) {
                    SummaryTile(
                        count = features,
                        style = sectionStyleOf(ChangelogSection.Kind.FEATURES, title = null),
                        modifier = Modifier.weight(1f)
                    )
                    SummaryTile(
                        count = fixes,
                        style = sectionStyleOf(ChangelogSection.Kind.FIXES, title = null),
                        modifier = Modifier.weight(1f)
                    )
                }

                // A single kind fills no pair with tiles, so it reads as one line instead
                features > 0 -> SummaryPill(
                    text = pluralStringResource(R.plurals.changelog_new_count, features, features.toString()),
                    style = sectionStyleOf(ChangelogSection.Kind.FEATURES, title = null)
                )

                fixes > 0 -> SummaryPill(
                    text = pluralStringResource(R.plurals.changelog_fix_count, fixes, fixes.toString()),
                    style = sectionStyleOf(ChangelogSection.Kind.FIXES, title = null)
                )
            }
        }
    }
}

@Composable
private fun SummaryPill(
    text: String,
    style: SectionStyle
) {
    Row(
        modifier = Modifier
            .clip(Defaults.PillShape)
            .background(style.tone.container)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Defaults.ContentPaddingSmall)
    ) {
        SectionLabel(style = style, text = text)
    }
}

@Composable
private fun SummaryTile(
    count: Int,
    style: SectionStyle,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(Defaults.CardCornerRadius))
            .background(style.tone.container)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = style.tone.content
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            SectionLabel(style = style, text = style.title)
        }
    }
}

/** The section's icon and [text], in its tone, for the summary on its tinted ground. */
@Composable
private fun SectionLabel(style: SectionStyle, text: String) {
    Icon(
        imageVector = style.icon,
        contentDescription = null,
        tint = style.tone.content,
        modifier = Modifier.size(16.dp)
    )
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = style.tone.content
    )
}

/** Version, badge and a line on when the release came out and what it brought. */
@Composable
private fun ReleaseHeader(
    entry: ChangelogEntry,
    badge: ChangelogBadge?,
    sections: List<ChangelogSection>,
    expanded: Boolean,
    onToggle: (() -> Unit)?
) {
    val chevronRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = tween(Defaults.ANIMATION_DURATION),
        label = "releaseChevron"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Defaults.CompactCornerRadius))
            .then(
                if (onToggle != null) Modifier.clickable(role = Role.Button, onClick = onToggle)
                else Modifier
            )
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Defaults.ItemSpacing)
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Defaults.ContentPaddingSmall)
            ) {
                Text(
                    text = entry.version.withVersionPrefix(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = LocalDialogTextColor.current
                )
                if (badge != null) {
                    StatusBadge(text = stringResource(badge.label), tone = badge.tone)
                }
            }
            releaseSummary(entry.date, sections)?.let { summary ->
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (onToggle != null) {
            Icon(
                imageVector = Icons.Outlined.ExpandMore,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.rotate(chevronRotation)
            )
        }
    }
}

@Composable
private fun ReleaseBody(
    sections: List<ChangelogSection>,
    isSectionExpanded: (Int) -> Boolean,
    onToggleSection: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    // The original stays up while the translation is worked out, then gives way to it
    AnimatedContent(
        targetState = rememberTranslated(sections),
        transitionSpec = Animations.fadeCrossfade(),
        modifier = modifier.fillMaxWidth(),
        label = "releaseSections"
    ) { shown ->
        Column(verticalArrangement = Arrangement.spacedBy(Defaults.ContentPadding)) {
            shown.forEachIndexed { index, section ->
                ChangelogSectionGroup(
                    section = section,
                    expanded = isSectionExpanded(index),
                    onToggle = { onToggleSection(index) }
                )
            }
        }
    }
}

/** Heading with the number of changes, over a card that lists them, shortened while folded. */
@Composable
private fun ChangelogSectionGroup(
    section: ChangelogSection,
    expanded: Boolean,
    onToggle: () -> Unit
) {
    val style = sectionStyleOf(section.kind, section.title)
    val changeCount = section.changeCount
    val isCollapsible = section.items.size > SECTION_PREVIEW_LIMIT
    val shownItems = if (isCollapsible && !expanded) section.items.take(SECTION_PREVIEW_SIZE) else section.items

    Column(verticalArrangement = Arrangement.spacedBy(Defaults.ContentPaddingSmall)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(SectionIconGap)
        ) {
            Box(
                modifier = Modifier
                    .size(SectionIconSize)
                    .clip(SectionIconShape)
                    .background(style.tone.container),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = style.icon,
                    contentDescription = null,
                    tint = style.tone.content,
                    modifier = Modifier.size(16.dp)
                )
            }
            // The count reads as part of the title, a space after it, and wraps along with it
            val countColor = MaterialTheme.colorScheme.onSurfaceVariant
            Text(
                text = buildAnnotatedString {
                    append(style.title)
                    // A section of notes alone has no changes to count
                    if (changeCount > 0) {
                        withStyle(SpanStyle(color = countColor, fontWeight = FontWeight.Medium)) {
                            append(" ($changeCount)")
                        }
                    }
                },
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = LocalDialogTextColor.current
            )
        }

        SectionCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.animateContentSize()) {
                shownItems.forEachIndexed { index, item ->
                    if (index > 0) ItemDivider()
                    ChangelogItemRow(item = item, dotColor = style.tone.accent)
                }
                if (isCollapsible) {
                    ItemDivider()
                    SectionExpander(
                        expanded = expanded,
                        total = changeCount,
                        onToggle = onToggle
                    )
                }
            }
        }
    }
}

@Composable
private fun ChangelogItemRow(
    item: ChangelogItem,
    dotColor: Color
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(ItemPadding),
        horizontalArrangement = Arrangement.spacedBy(Defaults.ItemSpacing)
    ) {
        // Level with the middle of the first line, which is the scope when there is one.
        // A note between the changes is no change itself, so it goes without one
        if (item.isBullet) {
            Box(
                modifier = Modifier
                    .padding(top = if (item.scope != null) ItemDotOffsetScoped else ItemDotOffset)
                    .size(ItemDotSize)
                    .background(dotColor, CircleShape)
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            if (item.scope != null) {
                Text(
                    text = item.scope,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = rememberInlineFormatting(item.text),
                style = MaterialTheme.typography.bodyMedium,
                color = if (item.isBullet) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** Rule between the changes of a section card. */
@Composable
private fun ItemDivider() {
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
}

@Composable
private fun SectionExpander(
    expanded: Boolean,
    total: Int,
    onToggle: () -> Unit
) {
    val chevronRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = tween(Defaults.ANIMATION_DURATION),
        label = "sectionChevron"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onToggle)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = if (expanded) stringResource(R.string.changelog_show_less)
            else stringResource(R.string.changelog_show_all, total.toString()),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary
        )
        Icon(
            imageVector = Icons.Outlined.ExpandMore,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .size(18.dp)
                .rotate(chevronRotation)
        )
    }
}

/** How a section presents itself: its own heading for the kinds changelogs share, else the one it came with. */
private class SectionStyle(val title: String, val icon: ImageVector, val tone: SemanticTone)

@Composable
private fun sectionStyleOf(kind: ChangelogSection.Kind, title: String?): SectionStyle = when (kind) {
    ChangelogSection.Kind.FEATURES -> SectionStyle(
        title = stringResource(R.string.changelog_section_new),
        icon = Icons.Outlined.AutoAwesome,
        tone = SemanticTone.Primary
    )
    ChangelogSection.Kind.FIXES -> SectionStyle(
        title = stringResource(R.string.changelog_section_fixes),
        icon = Icons.Outlined.BugReport,
        tone = SemanticTone.Warning
    )
    ChangelogSection.Kind.IMPROVEMENTS -> SectionStyle(
        title = stringResource(R.string.changelog_section_improvements),
        icon = Icons.Outlined.Tune,
        tone = SemanticTone.Success
    )
    ChangelogSection.Kind.PERFORMANCE -> SectionStyle(
        title = stringResource(R.string.changelog_section_performance),
        icon = Icons.Outlined.Speed,
        tone = SemanticTone.Success
    )
    ChangelogSection.Kind.APP_SUPPORT -> SectionStyle(
        title = stringResource(R.string.changelog_section_app_support),
        icon = Icons.Outlined.Smartphone,
        tone = SemanticTone.Neutral
    )
    ChangelogSection.Kind.OTHER -> SectionStyle(
        title = title ?: stringResource(R.string.changelog_section_other),
        icon = Icons.AutoMirrored.Outlined.Notes,
        tone = SemanticTone.Neutral
    )
}

/** When the release came out, then how many features and fixes it brought. */
@Composable
private fun releaseSummary(date: String?, sections: List<ChangelogSection>): String? {
    val locale = LocalConfiguration.current.locales[0]
    val released = remember(date, locale) { date?.let { formatReleaseDate(it, locale) } }

    val features = sections.countOf(ChangelogSection.Kind.FEATURES)
    val fixes = sections.countOf(ChangelogSection.Kind.FIXES)
    val total = sections.sumOf { it.changeCount }

    val parts = listOfNotNull(
        released,
        if (features > 0) pluralStringResource(R.plurals.changelog_new_count, features, features.toString()) else null,
        if (fixes > 0) pluralStringResource(R.plurals.changelog_fix_count, fixes, fixes.toString()) else null,
        // Releases with neither still say how much they hold
        if (features == 0 && fixes == 0 && total > 0) {
            pluralStringResource(R.plurals.changelog_change_count, total, total.toString())
        } else null
    )
    return parts.joinToString(" · ").ifEmpty { null }
}

/** A recent release as "2 days ago", an older one as a date in the app language. */
private fun formatReleaseDate(date: String, locale: Locale): String {
    val day = runCatching { LocalDate.parse(date) }.getOrNull() ?: return date
    val age = ChronoUnit.DAYS.between(day, LocalDate.now())
    if (age in 0..RELATIVE_DATE_DAYS) {
        val millis = day.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        return DateUtils.getRelativeTimeSpanString(millis, System.currentTimeMillis(), DateUtils.DAY_IN_MILLIS)
            .toString()
    }
    return DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale).format(day)
}

@Composable
private fun rememberInlineFormatting(text: String): AnnotatedString {
    val codeBackground = MaterialTheme.colorScheme.surfaceContainerHighest
    val linkColor = MaterialTheme.colorScheme.primary

    return remember(text, codeBackground, linkColor) {
        buildAnnotatedString {
            var position = 0
            for (match in INLINE_FORMATTING.findAll(text)) {
                append(text, position, match.range.first)
                val (code, bold, label, url) = match.destructured
                when {
                    code.isNotEmpty() -> withStyle(
                        SpanStyle(fontFamily = FontFamily.Monospace, background = codeBackground)
                    ) { append(code) }

                    bold.isNotEmpty() -> withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(bold) }

                    else -> withLink(
                        LinkAnnotation.Url(url, TextLinkStyles(SpanStyle(color = linkColor)))
                    ) { append(label) }
                }
                position = match.range.last + 1
            }
            append(text, position, text.length)
        }
    }
}

private class TimelineColors(
    val rail: Color,
    val accent: Color,
    val halo: Color,
    val muted: Color
)

/** Draws the rail through the release and its node, which tells the release's badge at a glance. */
private fun DrawScope.drawTimeline(
    badge: ChangelogBadge?,
    isFirst: Boolean,
    isLast: Boolean,
    hasNode: Boolean,
    colors: TimelineColors
) {
    val railX = (RailSlotWidth / 2).toPx().let { if (layoutDirection == LayoutDirection.Rtl) size.width - it else it }
    val stroke = RailStrokeWidth.toPx()

    if (!hasNode) {
        drawLine(colors.rail, Offset(railX, 0f), Offset(railX, size.height), stroke)
        return
    }

    val center = Offset(railX, NodeCenterY.toPx())
    // The rail stops short of the node, so hollow nodes stay hollow over any background
    val clearance = NodeHaloRadius.toPx() + RailStrokeWidth.toPx()

    if (!isFirst) {
        drawLine(colors.rail, Offset(railX, 0f), Offset(railX, center.y - clearance), stroke)
    }
    if (!isLast) {
        drawLine(colors.rail, Offset(railX, center.y + clearance), Offset(railX, size.height), stroke)
    }

    val radius = NodeRadius.toPx()
    when (badge) {
        ChangelogBadge.LATEST -> {
            drawCircle(colors.halo, radius = NodeHaloRadius.toPx(), center = center)
            drawCircle(colors.accent, radius = radius, center = center)
        }

        ChangelogBadge.INSTALLED, ChangelogBadge.IN_USE -> {
            val ring = 3.dp.toPx()
            drawCircle(colors.accent, radius = radius - ring / 2, center = center, style = Stroke(ring))
        }

        ChangelogBadge.PRERELEASE -> {
            val ring = RailStrokeWidth.toPx()
            val dash = 2.5.dp.toPx()
            drawCircle(
                colors.muted,
                radius = radius - ring / 2,
                center = center,
                style = Stroke(ring, pathEffect = PathEffect.dashPathEffect(floatArrayOf(dash, dash)))
            )
        }

        null -> drawCircle(colors.muted, radius = radius * 0.7f, center = center)
    }
}
