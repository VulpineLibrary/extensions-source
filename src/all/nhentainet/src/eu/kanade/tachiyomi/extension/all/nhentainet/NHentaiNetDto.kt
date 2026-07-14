package eu.kanade.tachiyomi.extension.all.nhentainet

import kotlinx.serialization.Serializable

@Serializable
data class GalleryDto(
    val id: Long,
    val media_id: String,
    val images: GalleryImagesDto,
)

@Serializable
data class GalleryImagesDto(
    val pages: List<ImageDto>,
)

@Serializable
data class ImageDto(
    val t: String,
    val w: Int = 0,
    val h: Int = 0,
)
