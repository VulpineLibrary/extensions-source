package eu.kanade.tachiyomi.extension.all.nhentainet

import eu.kanade.tachiyomi.multisrc.galleryadults.GalleryAdults
import eu.kanade.tachiyomi.multisrc.galleryadults.Genre
import eu.kanade.tachiyomi.multisrc.galleryadults.SortOrderFilter
import eu.kanade.tachiyomi.multisrc.galleryadults.imgAttr
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.UpdateStrategy
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.annotation.Source
import keiyoushi.utils.parseAs
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.Locale

@Source
abstract class NHentaiNet : GalleryAdults() {

    override val mangaLang = when (lang) {
        "en" -> LANGUAGE_ENGLISH
        "ja" -> LANGUAGE_JAPANESE
        "zh" -> LANGUAGE_CHINESE
        "all" -> LANGUAGE_MULTI
        else -> throw IllegalArgumentException("Invalid lang: $lang")
    }

    override val supportsLatest = true

    // nhentai.net includes speechless entries under English
    override val supportSpeechless = mangaLang == LANGUAGE_ENGLISH

    override val idPrefixUri = "g"

    override val basicSearchKey = "q"

    override val favoritePath = "favorites"

    // Gallery listing items use .gallery wrapper with a.cover inside
    override fun popularMangaSelector() = ".gallery"

    override fun Element.mangaUrl() = selectFirst("a.cover")?.attr("abs:href")

    override fun Element.mangaThumbnail() = selectFirst("a.cover img")?.imgAttr()

    // Language is already scoped by the listing URL path; no per-item re-filtering needed
    override fun Element.mangaLang() = mangaLang

    // For "all" there is no /language/X/ path prefix — fall back to a popularity-sorted search
    override fun popularMangaRequest(page: Int): Request = if (mangaLang.isBlank()) {
        val popularFilter = SortOrderFilter(getSortOrderURIs()).apply { state = 0 }
        basicSearchRequest(page, "", FilterList(popularFilter))
    } else {
        super.popularMangaRequest(page)
    }

    override fun loginRequired(document: Document, url: String): Boolean =
        url.contains("/$favoritePath/") &&
            document.select("a[href='/login/']:contains(Sign in)").isNotEmpty()

    // nhentai.net tags page uses a.tag with nested .name span (not a.tag_btn)
    override fun tagsParser(document: Document): List<Genre> =
        document.select("a.tag").mapNotNull {
            Genre(
                it.selectFirst(".name")?.text() ?: return@mapNotNull null,
                it.attr("href").removeSuffix("/").substringAfterLast('/'),
            )
        }

    // nhentai.net detail page: tags are grouped in .tag-container divs labelled "Tags:", "Artists:", etc.
    override fun Element.getInfo(tag: String): String =
        select(".tag-container:contains($tag:) a.tag").joinToString {
            val name = it.selectFirst(".name")?.text() ?: ""
            if (tag.contains(regexTag)) {
                genres[name] = it.attr("href").removeSuffix("/").substringAfterLast('/')
            }
            name
        }

    // nhentai.net detail layout: #cover holds the thumbnail, #info/#tags hold metadata
    override fun mangaDetailsParse(document: Document): SManga {
        val tags = document.selectFirst("#tags")
        return SManga.create().apply {
            update_strategy = UpdateStrategy.ONLY_FETCH_ONCE
            status = SManga.COMPLETED
            title = document.selectFirst("#info h1")?.text() ?: ""
            thumbnail_url = document.selectFirst("#cover img")?.imgAttr()
            genre = tags?.getInfo("Tags")
            author = tags?.getInfo("Artists")
            description = buildString {
                listOf("Parodies", "Characters", "Languages", "Categories", "Groups").forEach { tag ->
                    val info = tags?.getInfo(tag)?.takeIf { it.isNotBlank() } ?: return@forEach
                    if (isNotEmpty()) append("\n\n")
                    append("$tag: $info")
                }
            }
        }
    }

    // nhentai.net uses <time class="published" datetime="ISO-8601"> instead of .uploaded text
    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        return listOf(
            SChapter.create().apply {
                name = "Chapter"
                scanlator = document.selectFirst("#tags")?.getInfo("Groups")
                date_upload = document.selectFirst("time.published")
                    ?.attr("datetime")
                    ?.let { datetime ->
                        runCatching {
                            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
                                .parse(datetime.substringBefore("."))?.time
                        }.getOrNull()
                    } ?: 0L
                setUrlWithoutDomain(response.request.url.encodedPath)
            },
        )
    }

    // Use the JSON API to avoid parsing nhentai.net's embedded JS image data
    override fun pageListRequest(chapter: SChapter): Request {
        val id = chapter.url.removePrefix("/$idPrefixUri/").removeSuffix("/")
        return GET("$baseUrl/api/gallery/$id", headers)
    }

    override fun pageListParse(response: Response): List<Page> {
        val gallery = response.parseAs<GalleryDto>()
        return gallery.images.pages.mapIndexed { idx, image ->
            Page(
                index = idx,
                imageUrl = "https://i.nhentai.net/galleries/${gallery.mediaId}/${idx + 1}.${image.t.toExt()}",
            )
        }
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    private fun String.toExt() = when (this) {
        "p" -> "png"
        "g" -> "gif"
        "w" -> "webp"
        else -> "jpg"
    }
}
