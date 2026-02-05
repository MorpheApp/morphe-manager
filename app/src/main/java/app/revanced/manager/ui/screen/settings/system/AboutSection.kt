package app.revanced.manager.ui.screen.settings.system

import android.annotation.SuppressLint
import android.content.Intent
import androidx.appcompat.content.res.AppCompatResources
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Article
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material3.Icon
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
import app.revanced.manager.ui.screen.shared.MorpheSettingsDivider
import app.revanced.manager.ui.screen.shared.RichSettingsItem
import app.revanced.manager.ui.screen.shared.SettingsItem
import app.revanced.manager.ui.viewmodel.UpdateViewModel
import app.revanced.manager.util.toast
import com.google.accompanist.drawablepainter.rememberDrawablePainter
import org.koin.androidx.compose.koinViewModel

/**
 * About section
 * Contains app info and website sharing
 */
@SuppressLint("LocalContextGetResourceValueCall")
@Composable
fun AboutSection(
    onAboutClick: () -> Unit,
    onChangelogClick: () -> Unit,
    updateViewModel: UpdateViewModel = koinViewModel()
) {
    val context = LocalContext.current

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
                Icon(
                    imageVector = Icons.Outlined.ChevronRight,
                    contentDescription = null,
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
                    context.toast(context.getString(R.string.no_network_toast))
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
                            context.getString(R.string.settings_system_share_website)
                        )
                    )
                }.onFailure {
                    context.toast("Failed to share website: ${it.message}")
                }
            }
        )
    }
}
