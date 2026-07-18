/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.settings.system

import android.content.Intent
import androidx.appcompat.content.res.AppCompatResources
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Article
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.Public
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import app.morphe.manager.BuildConfig
import app.morphe.manager.R
import app.morphe.manager.ui.screen.shared.MorpheDefaults
import app.morphe.manager.ui.screen.shared.MorpheSettingsDivider
import app.morphe.manager.ui.screen.shared.SettingsGroup
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

    SettingsGroup {
        SettingsItem(
            onClick = onAboutClick,
            title = stringResource(R.string.app_name),
            subtitle = stringResource(R.string.version) + " " + BuildConfig.VERSION_NAME,
            leadingContent = {
                Image(
                    painter = rememberDrawablePainter(
                        drawable = AppCompatResources.getDrawable(context, R.mipmap.ic_launcher)
                    ),
                    contentDescription = null,
                    modifier = Modifier.size(MorpheDefaults.IconSize)
                )
            }
        )

        MorpheSettingsDivider()

        SettingsItem(
            icon = Icons.AutoMirrored.Outlined.Article,
            title = stringResource(R.string.changelog),
            subtitle = stringResource(R.string.changelog_description),
            onClick = {
                if (!updateViewModel.isConnected) {
                    context.toast(noNetworkToast)
                    return@SettingsItem
                }
                onChangelogClick()
            }
        )

        MorpheSettingsDivider()

        SettingsItem(
            icon = Icons.Outlined.Public,
            title = stringResource(R.string.settings_system_share_website),
            subtitle = stringResource(R.string.settings_system_share_website_description),
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
                subtitle = stringResource(R.string.onboarding_restart_desc),
                onClick = onStartTour
            )
        }
    }
}
