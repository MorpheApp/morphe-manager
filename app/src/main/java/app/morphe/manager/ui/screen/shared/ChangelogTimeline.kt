/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.shared

import android.text.format.DateUtils
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.automirrored.outlined.Notes
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import app.morphe.manager.R
import app.morphe.manager.util.*
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.abs

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

/** Marks inline code in formatted text, so its chip can be drawn behind it. */
private const val CODE_TAG = "code"
private const val CODE_CHIP_PADDING = "\u202F"
private val CodeChipRadius = 6.dp
private val CodeChipInset = 1.dp

/** Monospace glyphs run wider and heavier than the body font, so code steps down to sit level with it. */
private val CodeFontScale = 0.9.em

/** Marker a release carries on the timeline, listed from the strongest when several apply. */
enum class ChangelogBadge(@param:StringRes val label: Int, val tone: SemanticTone) {
    INSTALLED(R.string.installed, SemanticTone.Success),
    DOWNLOADED(R.string.changelog_badge_downloaded, SemanticTone.Success),
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

/** Stands in for a section: a card of changes headed by its icon and title. */
@Composable
private fun SectionGroupPlaceholder(modifier: Modifier = Modifier) {
    SectionCard(modifier = modifier.fillMaxWidth()) {
        Column {
            CardHeader(
                title = null,
                accentColor = null,
                leading = {
                    ShimmerBox(modifier = Modifier.size(SectionIconSize), shape = SectionIconShape)
                    ShimmerText(widthFraction = 0.3f, height = 16.dp)
                }
            )
            repeat(SECTION_ITEM_PLACEHOLDERS) { index ->
                if (index > 0) SettingsDivider(fullWidth = true)
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
    val counts = remember(sections) { changeCounts(sections) }

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

            if (counts.isNotEmpty()) {
                // Pills take the width their text needs, and a row too wide for one line wraps
                // instead of shortening any of them
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(
                        Defaults.ContentPaddingSmall,
                        Alignment.CenterHorizontally
                    ),
                    verticalArrangement = Arrangement.spacedBy(Defaults.ContentPaddingSmall)
                ) {
                    counts.forEach { count ->
                        SummaryPill(
                            text = count.label(),
                            style = sectionStyleOf(count.kind, title = null)
                        )
                    }
                }
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
        Icon(
            imageVector = style.icon,
            contentDescription = null,
            tint = style.tone.content,
            modifier = Modifier.size(16.dp)
        )
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = style.tone.content,
            // A long translation shortens rather than dropping under the icon
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
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
            ExpandChevron(expanded = expanded)
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

/** A card of changes headed by the section and their number, shortened while folded. */
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

    SectionCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.animateContentSize()) {
            // The section's color is on its icon, so the band stays neutral like the card's edge
            CardHeader(
                title = style.title,
                accentColor = null,
                leading = {
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
                },
                // A section of notes alone has no changes to count
                trailing = if (changeCount > 0) {
                    { StatusBadge(text = changeCount.toString()) }
                } else null
            )
            shownItems.forEachIndexed { index, item ->
                if (index > 0) SettingsDivider(fullWidth = true)
                ChangelogItemRow(item = item, dotColor = style.tone.accent)
            }
            if (isCollapsible) {
                SettingsDivider(fullWidth = true)
                SectionExpander(
                    expanded = expanded,
                    total = changeCount,
                    onToggle = onToggle
                )
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
            FormattedText(
                text = item.text,
                color = if (item.isBullet) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SectionExpander(
    expanded: Boolean,
    total: Int,
    onToggle: () -> Unit
) {
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
        ExpandChevron(
            expanded = expanded,
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.primary
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

/** When the release came out, then how many changes of each counted kind it brought. */
@Composable
private fun releaseSummary(date: String?, sections: List<ChangelogSection>): String? {
    val locale = LocalConfiguration.current.locales[0]
    val released = remember(date, locale) { date?.let { formatReleaseDate(it, locale) } }

    val counts = remember(sections) { changeCounts(sections) }
    val total = sections.sumOf { it.changeCount }

    val parts = buildList {
        released?.let(::add)
        counts.forEach { add(it.label()) }
        // Releases with none of the counted kinds still say how much they hold
        if (counts.isEmpty() && total > 0) {
            add(pluralStringResource(R.plurals.changelog_change_count, total, total.toString()))
        }
    }
    return parts.joinToString(" · ").ifEmpty { null }
}

/** Number of changes of a kind the summaries count, with the plural that names it. */
private class ChangeCount(val kind: ChangelogSection.Kind, @param:PluralsRes val plural: Int, val count: Int)

@Composable
private fun ChangeCount.label(): String = pluralStringResource(plural, count, count.toString())

/** The kinds worth a count of their own, in the order the summaries list them. */
private val CountedKinds = listOf(
    ChangelogSection.Kind.FEATURES to R.plurals.changelog_feature_count,
    ChangelogSection.Kind.FIXES to R.plurals.changelog_fix_count,
    ChangelogSection.Kind.PERFORMANCE to R.plurals.changelog_performance_count
)

private fun changeCounts(sections: List<ChangelogSection>): List<ChangeCount> =
    CountedKinds.mapNotNull { (kind, plural) ->
        sections.countOf(kind).takeIf { it > 0 }?.let { ChangeCount(kind, plural, it) }
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

/**
 * A change's text with its inline formatting. Code sits on rounded chips drawn behind the text,
 * since a span background only fills a hard-edged box.
 */
@Composable
private fun FormattedText(text: String, color: Color) {
    val formatted = rememberInlineFormatting(text)
    val codeRanges = remember(formatted) { formatted.getStringAnnotations(CODE_TAG, 0, formatted.length) }
    val chipColor = MaterialTheme.colorScheme.surfaceContainerHighest
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }

    Text(
        text = formatted,
        style = MaterialTheme.typography.bodyMedium,
        color = color,
        onTextLayout = { layout = it },
        modifier = Modifier.drawBehind {
            val result = layout ?: return@drawBehind
            val radius = CornerRadius(CodeChipRadius.toPx())
            val inset = CodeChipInset.toPx()
            for (range in codeRanges) {
                val firstLine = result.getLineForOffset(range.start)
                val lastLine = result.getLineForOffset(range.end - 1)
                // Code broken across lines gets a chip on each line it covers
                for (line in firstLine..lastLine) {
                    val start = maxOf(range.start, result.getLineStart(line))
                    val end = minOf(range.end, result.getLineEnd(line, visibleEnd = true))
                    if (start >= end) continue
                    val from = result.getHorizontalPosition(start, usePrimaryDirection = true)
                    val to = result.getHorizontalPosition(end, usePrimaryDirection = true)
                    drawRoundRect(
                        color = chipColor,
                        topLeft = Offset(minOf(from, to), result.getLineTop(line) + inset),
                        size = Size(
                            width = abs(to - from),
                            height = result.getLineBottom(line) - result.getLineTop(line) - inset * 2
                        ),
                        cornerRadius = radius
                    )
                }
            }
        }
    )
}

@Composable
private fun rememberInlineFormatting(text: String): AnnotatedString {
    val linkColor = MaterialTheme.colorScheme.primary

    return remember(text, linkColor) {
        buildAnnotatedString {
            var position = 0
            for (match in INLINE_FORMATTING.findAll(text)) {
                append(text, position, match.range.first)
                val (code, bold, label, url) = match.destructured
                when {
                    // Narrow spaces pad the chip and keep it on the same line as the code
                    code.isNotEmpty() -> withAnnotation(CODE_TAG, code) {
                        append(CODE_CHIP_PADDING)
                        withStyle(
                            SpanStyle(fontFamily = FontFamily.Monospace, fontSize = CodeFontScale)
                        ) { append(code) }
                        append(CODE_CHIP_PADDING)
                    }

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

        ChangelogBadge.INSTALLED, ChangelogBadge.DOWNLOADED -> {
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
