package com.wavora.scraper.models.body

import com.wavora.scraper.models.Context
import kotlinx.serialization.Serializable

@Serializable
data class LikeBody(
    val context: Context,
    val target: Target,
) {
    @Serializable
    data class Target(
        val videoId: String? = null,
        // Для альбомов здесь лежит audioPlaylistId ("OLAK5uy_..."), для плейлистов — их browseId без "VL"
        val playlistId: String? = null,
    )
}