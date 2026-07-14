import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "nHentai"
    versionCode = 1
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"
    theme = "galleryadults"

    listOf("all", "en", "ja", "zh").forEach { language ->
        source {
            lang = language
            baseUrl = "https://nhentai.net"
        }
    }
}
