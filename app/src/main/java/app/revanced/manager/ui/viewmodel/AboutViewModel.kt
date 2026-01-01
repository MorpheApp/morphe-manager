package app.revanced.manager.ui.viewmodel

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Language
import androidx.lifecycle.ViewModel
import compose.icons.FontAwesomeIcons
import compose.icons.fontawesomeicons.Brands
import compose.icons.fontawesomeicons.brands.Github
import compose.icons.fontawesomeicons.brands.RedditAlien
import compose.icons.fontawesomeicons.brands.XTwitter

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
            ),
            SocialLink(
                name = "Crowdin",
                url = "https://translate.morphe.software"
            )
        )

        private val socialIcons = mapOf(
            "GitHub" to FontAwesomeIcons.Brands.Github,
            "Reddit" to FontAwesomeIcons.Brands.RedditAlien,
            "X" to FontAwesomeIcons.Brands.XTwitter,
            "Crowdin" to Icons.Outlined.Language,
        )

        fun getSocialIcon(name: String) = socialIcons[name] ?: Icons.Outlined.Language
    }
}
