/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.settings.system

import android.os.SystemClock
import androidx.annotation.StringRes
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOutBack
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.morphe.manager.R
import app.morphe.manager.domain.repository.PeopleRepository.People
import app.morphe.manager.domain.repository.PeopleRepository.Person
import app.morphe.manager.ui.screen.shared.*
import app.morphe.manager.ui.viewmodel.AboutViewModel
import app.morphe.manager.util.AvatarCache
import app.morphe.manager.util.GITHUB_HOST
import app.morphe.manager.util.MANAGER_REPO_URL
import app.morphe.manager.util.MORPHE_GITHUB_ORG
import app.morphe.manager.util.loadRemoteAvatar
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.rounded.*
import org.koin.androidx.compose.koinViewModel

/** Which of the people behind Morphe a [PeopleDialog] lists. */
enum class PeopleList {
    /** Everyone who committed to the project, for one of the [ContributorPeriod]s. */
    CONTRIBUTORS,

    /** Everyone who backs it with a donation. */
    SUPPORTERS
}

/** A period the contributors are listed for, picked from a menu in the header. */
private enum class ContributorPeriod(@StringRes val label: Int, val contributors: (People) -> List<Person>) {
    ALL_TIME(R.string.contributors_all_time, People::contributors),
    SIX_MONTHS(R.string.contributors_6_months, People::contributors6Months),
    THREE_MONTHS(R.string.contributors_3_months, People::contributors3Months)
}

/**
 * A project the manager is, or grew out of, credited by a link to the people who wrote it.
 *
 * @param organization GitHub account of the project.
 * @param ownPicture Whether the project goes by the picture of that account. Off for a project
 *   of others, which goes by its initial: their logos are not ours to show.
 */
private data class Project(
    val name: String,
    val organization: String,
    val url: String,
    val ownPicture: Boolean = false,
) {
    /** The project as one of the people the dialog lists, for the round picture they go by. */
    val person = Person(
        name = name,
        url = url,
        avatar = "https://$GITHUB_HOST/$organization.png?size=96".takeIf { ownPicture }
    )
}

private val currentProject = Project(
    name = "Morphe",
    organization = MORPHE_GITHUB_ORG,
    url = "$MANAGER_REPO_URL/graphs/contributors",
    ownPicture = true
)

private val priorProjects = listOf(
    Project(
        name = "URV",
        organization = "Jman-Github",
        url = "https://github.com/Jman-Github/Universal-ReVanced-Manager/graphs/contributors"
    ),
    Project(
        name = "ReVanced",
        organization = "ReVanced",
        url = "https://github.com/ReVanced/revanced-manager/graphs/contributors"
    )
)

// Least width a person takes, their picture and enough of a name to tell them by
private val PersonCellWidth = 84.dp
private const val MIN_PERSON_COLUMNS = 3

private val AvatarSize = 56.dp

// A project's picture, the size of the icons the rows of other lists are led by
private val ProjectAvatarSize = 40.dp

/** The yellow a Mega Supporter goes by, on the ring around their picture. */
private val MegaSupporterTone = SemanticTone.Warning

// The ring a Mega Supporter's picture wears, and the gap keeping it off the picture
private val AvatarRingWidth = 2.dp
private val AvatarRingGap = 2.dp

// The ring sends out waves: rings that keep widening out of it and fading, this far and this
// slowly, one setting off as the one before it is halfway out
private val AvatarRingPulseSpread = 8.dp
private const val AVATAR_RING_PULSE_MILLIS = 2400
private const val AVATAR_RING_PULSE_ALPHA = 0.8f
private const val AVATAR_RING_WAVES = 2

// Each picture comes in a moment after the one before it, so a screen of them arrives as a wave
private const val AVATAR_STAGGER_MILLIS = 30

// Pictures past the first screenful come in at once, as they are scrolled to rather than opened on
private const val STAGGERED_ROWS = 8

// How small a picture starts out as it comes in
private const val AVATAR_ENTRANCE_SCALE = 0.6f

// How long after a change of period someone laid out still moves to their new place, so rows
// scrolled to later do not set off from where their people stood before
private const val PERIOD_MOVE_WINDOW_MILLIS = 600L

// Rows a person moves at most, so someone from far down the list comes from just past the screen
private const val PERIOD_MOVE_MAX_ROWS = 6

/**
 * The people behind Morphe, one [list] of them: its contributors over the projects the manager
 * grew out of, or its supporters. Both come from the Morphe API, the same lists the patched apps
 * show, and both are laid out as a grid of pictures.
 */
@Composable
fun DialogHostScope.PeopleDialog(
    list: PeopleList,
    onDismiss: () -> Unit,
    viewModel: AboutViewModel = koinViewModel()
) {
    val openUrl = rememberOpenUrl()
    val people by viewModel.people.collectAsStateWithLifecycle()
    val unavailable = viewModel.peopleUnavailable

    LaunchedEffect(Unit) { viewModel.loadPeople() }

    val accent = MaterialTheme.colorScheme.primary
    // A period with no one in it, or that the API does not serve yet, is not offered
    val periods = people?.takeIf { list == PeopleList.CONTRIBUTORS }
        ?.let { loaded -> ContributorPeriod.entries.filter { it.contributors(loaded).isNotEmpty() } }
        .orEmpty()
    var pickedPeriod by rememberSaveable { mutableStateOf(ContributorPeriod.ALL_TIME) }
    val period = pickedPeriod.takeIf { it in periods } ?: ContributorPeriod.ALL_TIME
    val periodOptions = periods.map { SortModeOption(it, stringResource(it.label)) }
    var periodMenuOpen by remember { mutableStateOf(false) }
    val persons = when (list) {
        PeopleList.CONTRIBUTORS -> people?.let(period.contributors)
        PeopleList.SUPPORTERS -> people?.supporters
    }
    // A change of period moves everyone from where they stood to their new place, rather than
    // laying the list out anew
    val periodChange = if (list == PeopleList.CONTRIBUTORS) {
        val shownPlaces = remember { ShownPlaces() }
        SideEffect { shownPlaces.places = persons.orEmpty().withIndex().associate { (place, person) -> person.url to place } }
        remember(period) { PeriodChange(shownPlaces.places) }
    } else null
    // Every backer for the supporters, of whom only the named ones are listed
    val count = when (list) {
        PeopleList.CONTRIBUTORS -> persons?.size
        PeopleList.SUPPORTERS -> people?.supportersTotal
    }
    val shownCount = count?.takeIf { it > 0 }
    val subtitle = when (list) {
        // The period names itself here, so switching it says what the list now holds
        PeopleList.CONTRIBUTORS -> shownCount
            ?.let { listOf(pluralStringResource(R.plurals.contributor_count, it, it.toString()), stringResource(period.label)) }
            ?.joinToString(" · ")
            ?: stringResource(R.string.people_unavailable)
        PeopleList.SUPPORTERS -> listOfNotNull(stringResource(R.string.supporters_description), shownCount?.toString())
            .joinToString(" · ")
    }

    ListDialog(
        onDismissRequest = onDismiss,
        header = {
            ListDialogHeader(
                decorativeIcon = true,
                icon = { modifier ->
                    ListDialogHeaderIcon(
                        icon = when (list) {
                            PeopleList.CONTRIBUTORS -> MaterialSymbols.Rounded.Group
                            PeopleList.SUPPORTERS -> MaterialSymbols.Rounded.Volunteer_activism
                        },
                        color = accent,
                        modifier = modifier
                    )
                },
                title = stringResource(
                    when (list) {
                        PeopleList.CONTRIBUTORS -> R.string.contributors
                        PeopleList.SUPPORTERS -> R.string.supporters
                    }
                ),
                subtitle = subtitle,
                subtitleLoading = people == null && !unavailable,
                accentColor = accent,
                actions = if (periodOptions.size > 1) {
                    {
                        // The menu hangs off the button, so it opens under the header's end
                        Box {
                            TitleAction(
                                icon = MaterialSymbols.Rounded.Date_range,
                                contentDescription = stringResource(R.string.filter),
                                onClick = { periodMenuOpen = true },
                                style = TitleActionStyle.Toggle,
                                // Engaged while its menu is open, and marked once the list is off
                                // the all-time one it starts on
                                active = periodMenuOpen,
                                marked = period != ContributorPeriod.ALL_TIME,
                                modifier = Modifier.semantics { stateDescription = periodOptions.first { it.value == period }.title }
                            )
                            SortModeMenu(
                                expanded = periodMenuOpen,
                                onDismissRequest = { periodMenuOpen = false },
                                current = period,
                                options = periodOptions,
                                onSelect = { pickedPeriod = it }
                            )
                        }
                    }
                } else null
            )
        },
        footer = {
            Column(verticalArrangement = Arrangement.spacedBy(Defaults.SpaceSmall)) {
                if (list == PeopleList.SUPPORTERS) {
                    AppDialogButton(
                        text = stringResource(R.string.supporters_become),
                        icon = MaterialSymbols.Rounded.Favorite,
                        onClick = { openUrl(AboutViewModel.donateLink.url) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                AppDialogOutlinedButton(
                    text = stringResource(R.string.close),
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val columns = (maxWidth / PersonCellWidth).toInt().coerceAtLeast(MIN_PERSON_COLUMNS)

            DialogLazyList(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(SegmentGap)
            ) {
                if (persons.isNullOrEmpty() && unavailable) {
                    item(key = "unavailable") {
                        EmptyState(
                            message = stringResource(R.string.people_unavailable),
                            icon = MaterialSymbols.Rounded.Cloud_off,
                            modifier = Modifier
                                .animatedListItem(this)
                                .endsBlock()
                        )
                    }
                }

                people(persons.orEmpty(), columns, openUrl, periodChange)

                when (list) {
                    // Shown with the list or without it, as the manager stands on these either
                    // way, though not ahead of it: a list keeps its place by the row it already
                    // shows, so people arriving above this would open the dialog scrolled past them
                    PeopleList.CONTRIBUTORS -> if (people != null || unavailable) {
                        item(key = "prior") {
                            PriorDevelopment(
                                // The people of the project itself, where the list cannot name them
                                showCurrent = persons.isNullOrEmpty() && unavailable,
                                onOpen = openUrl,
                                // Ends the list as the row closing the supporters does
                                modifier = Modifier
                                    .animatedListItem(this)
                                    .endsBlock()
                            )
                        }
                    }

                    PeopleList.SUPPORTERS -> {
                        val unlisted = (count ?: 0) - persons.orEmpty().size
                        if (!persons.isNullOrEmpty() && unlisted > 0) {
                            item(key = "unlisted") {
                                SettingsItem(
                                    onClick = { openUrl(AboutViewModel.SUPPORTERS_URL) },
                                    showBorder = true,
                                    icon = MaterialSymbols.Rounded.Volunteer_activism,
                                    title = stringResource(R.string.people_more, unlisted.toString()),
                                    // Leaves for the browser rather than going on within the dialog
                                    trailingContent = {
                                        ThemedIcon(icon = MaterialSymbols.Rounded.Open_in_new.autoMirrored)
                                    },
                                    modifier = Modifier
                                        .animatedListItem(this)
                                        .endsBlock()
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * [people] as a grid of pictures, [columns] to a row, each opening that person's page.
 *
 * @param periodChange The last change of the period listed, which moves everyone to their new
 *   place. Null for a list that is not listed by period.
 */
private fun LazyListScope.people(
    people: List<Person>,
    columns: Int,
    onOpen: (String) -> Unit,
    periodChange: PeriodChange?
) {
    val rows = people.chunked(columns)
    // Keyed by place: two supporters may well link to the same website
    itemsIndexed(items = rows, key = { index, _ -> index }) { index, row ->
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .animatedListItem(this)
                .endsBlock(index == rows.lastIndex)
        ) {
            row.forEachIndexed { column, person ->
                PersonCell(
                    person = person,
                    // Across the rows as well as along them, so the wave runs corner to corner
                    entranceOrder = if (index < STAGGERED_ROWS) index + column else column,
                    onClick = { onOpen(person.url) },
                    modifier = Modifier
                        .weight(1f)
                        .then(
                            periodChange
                                ?.let { Modifier.movesOnPeriodChange(it, person, index * columns + column, columns) }
                                ?: Modifier
                        )
                )
            }
            // Keeps the cells of a short last row as wide as the ones above them
            repeat(columns - row.size) { Spacer(modifier = Modifier.weight(1f)) }
        }
    }
}

/** Where everyone stands in the list last shown, by the page they open. */
private class ShownPlaces {
    var places: Map<String, Int> = emptyMap()
}

/**
 * A change of the period the contributors are listed for: where everyone stood in the list shown
 * before it, by the page they open, and when it happened. The first has no places, so nobody
 * moves as the dialog opens.
 */
private class PeriodChange(val places: Map<String, Int>) {
    val atMillis = SystemClock.uptimeMillis()
}

/**
 * Moves a person from where they stood before the last [change] to their [place], [columns] to
 * a row, and fades in one who was not listed before. Rows scrolled to once the move is over are
 * laid out for the period as it is, and stand still.
 */
@Composable
private fun Modifier.movesOnPeriodChange(change: PeriodChange, person: Person, place: Int, columns: Int): Modifier {
    val from = change.places[person.url]
    // How much of the move is left, from 1 where the person stood to 0 at their place
    val remaining = remember(change) {
        val moves = change.places.isNotEmpty() && from != place &&
            SystemClock.uptimeMillis() - change.atMillis < PERIOD_MOVE_WINDOW_MILLIS
        Animatable(if (moves) 1f else 0f)
    }
    LaunchedEffect(change) { remaining.animateTo(0f, Animations.listSpring()) }

    val mirrored = LocalLayoutDirection.current == LayoutDirection.Rtl
    val rowGap = with(LocalDensity.current) { SegmentGap.toPx() }
    return layout { measurable, constraints ->
        val placeable = measurable.measure(constraints)
        layout(placeable.width, placeable.height) {
            placeable.placeWithLayer(0, 0) {
                val left = remaining.value
                if (left == 0f) return@placeWithLayer
                if (from == null) {
                    val shown = (1f - left).coerceIn(0f, 1f)
                    alpha = shown
                    scaleX = AVATAR_ENTRANCE_SCALE + (1f - AVATAR_ENTRANCE_SCALE) * shown
                    scaleY = scaleX
                } else {
                    val columnShift = from % columns - place % columns
                    // Someone from far down the list comes from just past the screen
                    val rowShift = (from / columns - place / columns).coerceIn(-PERIOD_MOVE_MAX_ROWS, PERIOD_MOVE_MAX_ROWS)
                    translationX = columnShift * placeable.width * left * if (mirrored) -1 else 1
                    translationY = rowShift * (placeable.height + rowGap) * left
                }
            }
        }
    }
}

@Composable
private fun PersonCell(
    person: Person,
    entranceOrder: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(Defaults.CornerMedium))
            .clickable(onClick = onClick)
            .padding(vertical = Defaults.SpaceSmall, horizontal = Defaults.SpaceExtraSmall),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Defaults.SpaceExtraSmall)
    ) {
        PersonAvatar(person = person, entranceOrder = entranceOrder)
        Text(
            text = person.name,
            style = MaterialTheme.typography.labelMedium,
            color = dialogTextColor(),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * A person's picture, over the initial that stands in for one until it loads or where there is
 * none, in a pulsing ring for a Mega Supporter. It pops in once, and the picture fades in over the initial
 * as it arrives.
 *
 * @param entranceOrder Place in the wave the pictures on screen come in by, see [AVATAR_STAGGER_MILLIS].
 */
@Composable
private fun PersonAvatar(person: Person, entranceOrder: Int, size: Dp = AvatarSize) {
    val megaColor = MegaSupporterTone.accent
    // Kept by the cache the avatars of the sources are in, so one seen before shows at once
    var picture by remember(person.avatar) { mutableStateOf(person.avatar?.let { AvatarCache[it] }) }
    LaunchedEffect(person.avatar) {
        if (picture == null) picture = person.avatar?.let { loadRemoteAvatar(it) }
    }
    val pictureAlpha by animateFloatAsState(
        targetValue = if (picture != null) 1f else 0f,
        animationSpec = tween(Defaults.ANIMATION_DURATION),
        label = "avatar_picture"
    )
    // Saved with the list, so a picture scrolled away and back does not come in a second time
    var entered by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) { entered = true }
    val entrance by animateFloatAsState(
        targetValue = if (entered) 1f else 0f,
        animationSpec = tween(
            durationMillis = Defaults.ANIMATION_DURATION,
            delayMillis = entranceOrder * AVATAR_STAGGER_MILLIS,
            easing = EaseOutBack
        ),
        label = "avatar_entrance"
    )

    Box(
        modifier = Modifier
            .size(size)
            .graphicsLayer {
                val scale = AVATAR_ENTRANCE_SCALE + (1f - AVATAR_ENTRANCE_SCALE) * entrance
                scaleX = scale
                scaleY = scale
                alpha = entrance.coerceIn(0f, 1f)
            }
            .then(
                if (person.mega) {
                    Modifier
                        .pulsingRing(megaColor)
                        .padding(AvatarRingWidth + AvatarRingGap)
                } else Modifier
            )
            .clip(CircleShape)
            .background(neutralVeil(dialogTextColor())),
        contentAlignment = Alignment.Center
    ) {
        // Gone once the picture is there, or it would show through one with a clear background
        if (pictureAlpha < 1f) {
            Text(
                text = person.name.take(1).uppercase(),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
            )
        }
        picture?.let {
            Image(
                bitmap = it.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .matchParentSize()
                    .graphicsLayer { alpha = pictureAlpha }
            )
        }
    }
}

/**
 * A ring of [color] around a round picture, with softer ones forever widening out of it and
 * fading, so the picture draws the eye without a label saying why.
 */
@Composable
private fun Modifier.pulsingRing(color: Color): Modifier {
    val pulse by rememberInfiniteTransition(label = "avatar_ring").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            // Even, so the waves stay the same distance apart all the way out
            animation = tween(durationMillis = AVATAR_RING_PULSE_MILLIS, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "avatar_ring_pulse"
    )
    return drawBehind {
        val width = AvatarRingWidth.toPx()
        val radius = (size.minDimension - width) / 2
        repeat(AVATAR_RING_WAVES) { wave ->
            val progress = (pulse + wave.toFloat() / AVATAR_RING_WAVES) % 1f
            drawCircle(
                color = color,
                radius = radius + AvatarRingPulseSpread.toPx() * progress,
                alpha = AVATAR_RING_PULSE_ALPHA * (1f - progress),
                style = Stroke(width)
            )
        }
        drawCircle(color = color, radius = radius, style = Stroke(width))
    }
}

/**
 * The projects the manager grew out of, each linking to the people who wrote it, under the people
 * of Morphe itself where [showCurrent] asks for them.
 */
@Composable
private fun PriorDevelopment(
    showCurrent: Boolean,
    onOpen: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(Defaults.SpaceLarge)
    ) {
        if (showCurrent) {
            SettingsGroup {
                ProjectItem(project = currentProject, entranceOrder = 0, onOpen = onOpen)
            }
        }
        SettingsSection(
            title = stringResource(R.string.credits_prior_development),
            icon = MaterialSymbols.Rounded.History
        ) {
            SettingsGroup {
                priorProjects.forEachIndexed { index, project ->
                    ProjectItem(project = project, entranceOrder = index, onOpen = onOpen)
                }
            }
        }
    }
}

/** A project as a row as wide as the dialog, led by the round picture the people above go by. */
@Composable
private fun ProjectItem(project: Project, entranceOrder: Int, onOpen: (String) -> Unit) {
    SettingsItem(
        onClick = { onOpen(project.url) },
        leadingContent = {
            PersonAvatar(person = project.person, entranceOrder = entranceOrder, size = ProjectAvatarSize)
        },
        title = project.name,
        subtitle = "$GITHUB_HOST/${project.organization}",
        // Leaves for the browser rather than going on within the dialog
        trailingContent = { ThemedIcon(icon = MaterialSymbols.Rounded.Open_in_new.autoMirrored) }
    )
}
