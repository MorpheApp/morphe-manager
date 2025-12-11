package app.revanced.manager.ui.component.haptics

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.FloatingActionButtonElevation
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import app.revanced.manager.util.withHapticFeedback

@Composable
fun HapticExtendedFloatingActionButton (
    text: @Composable () -> Unit,
    icon: @Composable () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    expanded: Boolean = true,
    enabled: Boolean = true,
    shape: Shape = FloatingActionButtonDefaults.extendedFabShape,
    containerColor: Color = FloatingActionButtonDefaults.containerColor,
    contentColor: Color = contentColorFor(containerColor),
    elevation: FloatingActionButtonElevation = FloatingActionButtonDefaults.elevation(),
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
) {
    val displayModifier = if (enabled) modifier else modifier.alpha(0.45f)
    val clickHandler: () -> Unit = if (enabled) {
        onClick.withHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
    } else {
        {}
    }

    ExtendedFloatingActionButton(
        text = text,
        icon = icon,
        onClick = clickHandler,
        modifier = displayModifier,
        expanded = expanded,
        shape = shape,
        containerColor = containerColor,
        contentColor = contentColor,
        elevation = elevation,
        interactionSource = interactionSource
    )
}
