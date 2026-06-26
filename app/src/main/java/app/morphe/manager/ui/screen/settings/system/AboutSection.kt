/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.settings.system

import android.content.Intent
import androidx.appcompat.content.res.AppCompatResources
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Article
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.morphe.manager.BuildConfig
import app.morphe.manager.R
import app.morphe.manager.ui.screen.shared.MorpheIcon
import app.morphe.manager.ui.screen.shared.MorpheSettingsDivider
import app.morphe.manager.ui.screen.shared.RichSettingsItem
import app.morphe.manager.ui.screen.shared.SettingsItem
import app.morphe.manager.ui.viewmodel.UpdateViewModel
import app.morphe.manager.util.toast
import com.google.accompanist.drawablepainter.rememberDrawablePainter
import org.koin.androidx.compose.koinViewModel

/**
 * About section.
 * Contains app info and website sharing.
 */
@Composable
fun AboutSection(
    onAboutClick: () -> Unit,
    onChangelogClick: () -> Unit,
    onStartTour: (() -> Unit)? = null,
    updateViewModel: UpdateViewModel = koinViewModel()
) {
    val context = LocalContext.current
    val noNetworkToast = stringResource(R.string.no_network_toast)
    val shareWebsiteChooserTitle = stringResource(R.string.settings_system_share_website)

    Column {
        // App info item
        val appIconPainter = remember {
            AppCompatResources.getDrawable(context, R.mipmap.ic_launcher)
        }.let { rememberDrawablePainter(it) }

        RichSettingsItem(
            onClick = onAboutClick,
            title = stringResource(R.string.app_name),
            subtitle = stringResource(R.string.version) + " " + BuildConfig.VERSION_NAME,
            leadingContent = {
                Image(
                    painter = appIconPainter,
                    contentDescription = null,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                )
            },
            trailingContent = {
                MorpheIcon(
                    icon = Icons.Outlined.ChevronRight,
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        )

        MorpheSettingsDivider()

        // Changelog item
        SettingsItem(
            icon = Icons.AutoMirrored.Outlined.Article,
            title = stringResource(R.string.changelog),
            description = stringResource(R.string.changelog_description),
            onClick = {
                if (!updateViewModel.isConnected) {
                    context.toast(noNetworkToast)
                    return@SettingsItem
                }
                onChangelogClick()
            }
        )

        MorpheSettingsDivider()

        // Share Website item
        SettingsItem(
            icon = Icons.Outlined.Public,
            title = stringResource(R.string.settings_system_share_website),
            description = stringResource(R.string.settings_system_share_website_description),
            onClick = {
                runCatching {
                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, "https://morphe.software")
                    }
                    context.startActivity(
                        Intent.createChooser(
                            shareIntent,
                            shareWebsiteChooserTitle
                        )
                    )
                }.onFailure {
                    context.toast("Failed to share website: ${it.message}")
                }
            }
        )

        if (onStartTour != null) {
            MorpheSettingsDivider()

            SettingsItem(
                icon = Icons.Outlined.Lightbulb,
                title = stringResource(R.string.onboarding_restart_title),
                description = stringResource(R.string.onboarding_restart_desc),
                onClick = onStartTour
            )
        }
    }
}
