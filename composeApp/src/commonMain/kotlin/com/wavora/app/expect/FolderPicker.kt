package com.wavora.app.expect

/**
 * Lets the user pick a folder (desktop only). Returns the absolute path, or null when the
 * dialog was cancelled or the platform has no folder picker.
 */
expect suspend fun pickFolder(initialPath: String?): String?