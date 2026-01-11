package app.revanced.manager.ui.component.morphe.settings

import android.content.Intent
import androidx.appcompat.content.res.AppCompatResources
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.morphe.manager.BuildConfig
import app.morphe.manager.R
import app.revanced.manager.ui.component.morphe.shared.IconTextRow
import app.revanced.manager.ui.component.morphe.shared.MorpheSettingsDivider
import app.revanced.manager.ui.component.morphe.shared.SettingsItemCard
import app.revanced.manager.util.toast
import com.google.accompanist.drawablepainter.rememberDrawablePainter

/**
 * About section
 * Contains app info and website sharing
 */
@Composable
fun AboutSection(
    onAboutClick: () -> Unit
) {
    val context = LocalContext.current

    Column(
        modifier = Modifier.padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // About item
        SettingsItemCard(
            onClick = onAboutClick
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val icon = rememberDrawablePainter(
                    drawable = remember {
                        AppCompatResources.getDrawable(context, R.mipmap.ic_launcher)
                    }
                )
                Image(
                    painter = icon,
                    contentDescription = null,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(12.dp))
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.app_name),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = stringResource(R.string.version) + " " + BuildConfig.VERSION_NAME,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Icon(
                    imageVector = Icons.Outlined.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }

        MorpheSettingsDivider()

        // Share Website
        SettingsItemCard(
            onClick = {
                try {
                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, "https://morphe.software")
                    }
                    context.startActivity(
                        Intent.createChooser(
                            shareIntent,
                            context.getString(R.string.morphe_share_website)
                        )
                    )
                } catch (e: Exception) {
                    context.toast("Failed to share website: ${e.message}")
                }
            }
        ) {
            IconTextRow(
                icon = Icons.Outlined.Language,
                title = stringResource(R.string.morphe_share_website),
                description = stringResource(R.string.morphe_share_website_description),
                modifier = Modifier.padding(16.dp),
                trailingContent = {
                    Icon(
                        imageVector = Icons.Outlined.ChevronRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            )
        }
    }
}
