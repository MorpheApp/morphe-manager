package app.revanced.manager.ui.component.settings

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource

@Composable
fun TextItem(
    modifier: Modifier = Modifier,
    @StringRes headline: Int,
    @StringRes description: Int
) = SettingsListItem(
    modifier = modifier,
    headlineContent = stringResource(headline),
    supportingContent = stringResource(description),
    trailingContent = {} // no switch, button, etc.
)
