package app.revanced.manager.ui.viewmodel

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Language
import androidx.lifecycle.ViewModel
import compose.icons.FontAwesomeIcons
import compose.icons.fontawesomeicons.Brands
import compose.icons.fontawesomeicons.brands.Discord
import compose.icons.fontawesomeicons.brands.Github
import compose.icons.fontawesomeicons.brands.Reddit
import compose.icons.fontawesomeicons.brands.Telegram
import compose.icons.fontawesomeicons.brands.XTwitter
import compose.icons.fontawesomeicons.brands.Youtube

data class SocialLink(
    val name: String,
    val url: String,
    val preferred: Boolean = false,
)

class AboutViewModel() : ViewModel() {
    companion object {
        val socials: List<SocialLink> = listOf(
            SocialLink(
                name = "GitHub",
                url = "https://github.com/MorpheApp",
                preferred = true
            ),
            SocialLink(
                name = "X",
                url = "https://x.com/MorpheApp"
            ),
            SocialLink(
                name = "Reddit",
                url = "https://reddit.com/r/MorpheApp"
            )
        )

        private val socialIcons = mapOf(
            "Discord" to FontAwesomeIcons.Brands.Discord,
            "GitHub" to FontAwesomeIcons.Brands.Github,
            "Reddit" to FontAwesomeIcons.Brands.Reddit,
            "Telegram" to FontAwesomeIcons.Brands.Telegram,
            "Twitter" to FontAwesomeIcons.Brands.XTwitter,
            "X" to FontAwesomeIcons.Brands.XTwitter,
            "YouTube" to FontAwesomeIcons.Brands.Youtube,
        )

        fun getSocialIcon(name: String) = socialIcons[name] ?: Icons.Outlined.Language
    }
}
