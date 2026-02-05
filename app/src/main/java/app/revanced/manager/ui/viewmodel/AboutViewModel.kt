package app.revanced.manager.ui.viewmodel

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Article
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Translate
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
                name = "Website",
                url = "https://morphe.software",
                preferred = true
            ),
            SocialLink(
                name = "Changelog",
                url = "https://morphe.software/changelog"
            ),
            SocialLink(
                name = "GitHub",
                url = "https://github.com/MorpheApp"
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
                url = "https://morphe.software/translate"
            )
        )

        private val socialIcons = mapOf(
            "Website" to Icons.Outlined.Public,
            "GitHub" to FontAwesomeIcons.Brands.Github,
            "Changelog" to Icons.AutoMirrored.Outlined.Article,
            "Reddit" to FontAwesomeIcons.Brands.RedditAlien,
            "X" to FontAwesomeIcons.Brands.XTwitter,
            "Crowdin" to Icons.Outlined.Translate,
        )

        fun getSocialIcon(name: String) = socialIcons[name] ?: Icons.Outlined.Language
    }
}
