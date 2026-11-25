/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.shared

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp
import app.morphe.manager.util.CodeHost
import com.composables.icons.fontawesome.FontAwesome
import com.composables.icons.fontawesome.brands.Github
import com.composables.icons.fontawesome.brands.Gitlab

/** Logo of the code host, for the links and buttons leading to it. */
val CodeHost.icon: ImageVector
    get() = when (this) {
        CodeHost.GITHUB -> FontAwesome.Brands.Github
        CodeHost.GITLAB -> FontAwesome.Brands.Gitlab
        CodeHost.CODEBERG -> Codeberg
    }

// Font Awesome has no Codeberg logo, this one is the Simple Icons mark (CC0)
private val Codeberg: ImageVector by lazy {
    ImageVector.Builder(
        name = "Codeberg",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).addPath(
        pathData = addPathNodes(
            "M11.999.747A11.974 11.974 0 0 0 0 12.75c0 2.254.635 4.465 1.833 6.376L11.837 6.19c.072-.092.251-.092.323 0" +
                "l4.178 5.402h-2.992l.065.239h3.113l.882 1.138h-3.674l.103.374h3.86l.777 1.003h-4.358l.135.483h4.593" +
                "l.695.894h-5.038l.165.589h5.326l.609.785h-5.717l.182.65h6.038l.562.727h-6.397l.183.65h6.717" +
                "A12.003 12.003 0 0 0 24 12.75 11.977 11.977 0 0 0 11.999.747zm3.654 19.104.182.65h5.326" +
                "c.173-.204.353-.433.513-.65zm.385 1.377.18.65h3.563c.233-.198.485-.428.712-.65zm.383 1.377.182.648" +
                "h1.203c.356-.204.685-.412 1.042-.648z"
        ),
        fill = SolidColor(Color.Black)
    ).build()
}
