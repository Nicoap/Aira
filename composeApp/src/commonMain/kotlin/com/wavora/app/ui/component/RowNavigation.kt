package com.wavora.app.ui.component

/**
 * Lets list rows (which have no NavController) open an artist directly: the artist name in a
 * track row is a link, so there is no need to go through the context menu any more.
 * Set from App.kt once the NavController exists.
 */
object RowNavigation {
    @Volatile
    var openArtist: ((channelId: String) -> Unit)? = null
}