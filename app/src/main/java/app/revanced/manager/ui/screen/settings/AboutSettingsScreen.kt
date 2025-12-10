package app.revanced.manager.ui.screen.settings

import androidx.appcompat.content.res.AppCompatResources
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.revanced.manager.ui.component.AnnotatedLinkText // From PR #37: https://github.com/Jman-Github/Universal-ReVanced-Manager/pull/37
import androidx.compose.ui.graphics.vector.ImageVector
import app.revanced.manager.ui.component.AppTopBar
import app.revanced.manager.ui.model.navigation.Settings
import app.revanced.manager.ui.viewmodel.AboutViewModel.Companion.getSocialIcon
import app.revanced.manager.util.openUrl
import app.universal.revanced.manager.BuildConfig
import app.universal.revanced.manager.R
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
                context.getString(R.string.github),
                { context.openUrl("https://github.com/Jman-Github/universal-revanced-manager") }
            ),
            Triple(
                getSocialIcon("GitHub"),
                context.getString(R.string.original_revanced_manager_github),
                { context.openUrl("https://github.com/ReVanced/revanced-manager") }
            ),
            Triple(
                getSocialIcon("GitHub"),
                context.getString(R.string.patch_bundle_urls),
                { context.openUrl("https://github.com/Jman-Github/ReVanced-Patch-Bundles#-patch-bundles-urls") }
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
                    // From PR #37: https://github.com/Jman-Github/Universal-ReVanced-Manager/pull/37
                    AnnotatedLinkText(
                        text = stringResource(R.string.revanced_manager_description),
                        linkLabel = stringResource(R.string.here),
                        url = "https://github.com/Jman-Github/Universal-ReVanced-Manager#-unique-features"
                    )
                }
            }
        }
    }
}
