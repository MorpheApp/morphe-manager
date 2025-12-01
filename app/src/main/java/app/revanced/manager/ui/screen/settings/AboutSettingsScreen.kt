package app.revanced.manager.ui.screen.settings

import androidx.appcompat.content.res.AppCompatResources
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import app.universal.revanced.manager.BuildConfig
import app.universal.revanced.manager.R
import androidx.compose.ui.graphics.vector.ImageVector
import app.revanced.manager.ui.component.AppTopBar
import app.revanced.manager.ui.model.navigation.Settings
import app.revanced.manager.ui.viewmodel.AboutViewModel.Companion.getSocialIcon
import app.revanced.manager.util.openUrl
import com.google.accompanist.drawablepainter.rememberDrawablePainter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutSettingsScreen(
    onBackClick: () -> Unit,
    navigate: (Settings.Destination) -> Unit,
) {
    val context = LocalContext.current
    // painterResource() is broken on release builds for some reason.
    val icon = rememberDrawablePainter(drawable = remember {
        AppCompatResources.getDrawable(context, R.mipmap.ic_launcher)
    })

    val githubButtons: List<Triple<ImageVector, String, () -> Unit>> = remember(context) {
        listOf(
            Triple(
                getSocialIcon("GitHub"),
                context.getString(R.string.patches_github),
                { context.openUrl("https://github.com/MorpheApp/Patches") }
            ),
            Triple(
                getSocialIcon("GitHub"),
                context.getString(R.string.manager_github),
                { context.openUrl("https://github.com/MorpheApp/Morphe-alpha") } // TODO: Remove "-alpha" when released.
            ),
            Triple(
                getSocialIcon("GitHub"),
                context.getString(R.string.patcher_github),
                { context.openUrl("https://github.com/MorpheApp/Patcher") }
            )
        )
    }

    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior(rememberTopAppBarState())
    Scaffold(
        topBar = {
            AppTopBar(
                title = stringResource(R.string.about),
                scrollBehavior = scrollBehavior,
                onBackClick = onBackClick
            )
        },
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
    ) { paddingValues ->
        val scrollState = rememberScrollState()
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(paddingValues)
                .padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Image(
                modifier = Modifier
                    .padding(top = 16.dp),
                painter = icon,
                contentDescription = stringResource(R.string.app_name)
            )
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    stringResource(R.string.app_name),
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.semantics {
                        hideFromAccessibility()
                    }
                )
                Text(
                    text = stringResource(R.string.version) + " " + BuildConfig.VERSION_NAME + " (" + BuildConfig.VERSION_CODE + ")",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.outline
                )
            }
            Column(
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                githubButtons.forEach { (icon, text, onClick) ->
                    FilledTonalButton(
                        onClick = onClick,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                icon,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text,
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    }
                }
            }
            OutlinedCard(
                modifier = Modifier.padding(horizontal = 16.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = stringResource(R.string.about_revanced_manager),
                        style = MaterialTheme.typography.titleMedium
                    )
                    val description = stringResource(R.string.revanced_manager_description)
                    val uniqueFeaturesLabel = stringResource(R.string.unique_features_label)
                    val uniqueFeaturesUrl = "https://github.com/Jman-Github/Universal-ReVanced-Manager#-unique-features"
                    val annotatedDescription = buildAnnotatedString {
                        append(description)
                        append(" ")
                        pushStringAnnotation(tag = "unique_features", annotation = uniqueFeaturesUrl)
                        withStyle(
                            SpanStyle(
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold,
                                textDecoration = TextDecoration.Underline
                            )
                        ) {
                            append(uniqueFeaturesLabel)
                        }
                        pop()
                    }
                    val launchUrl = { url: String -> context.openUrl(url) }
                    ClickableText(
                        text = annotatedDescription,
                        style = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurfaceVariant),
                        onClick = { offset ->
                            annotatedDescription.getStringAnnotations("unique_features", offset, offset)
                                .firstOrNull()
                                ?.let { launchUrl(it.item) }
                        }
                    )
                }
            }
        }
    }
}
