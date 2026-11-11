package com.wavora.app

import kotlinx.coroutines.flow.MutableSharedFlow

/** One-way commands from platform code (e.g. the desktop window key handler) into the common UI. */
object AppCommands {
    /** Open the Search screen (Ctrl+K / Ctrl+F). */
    val openSearch = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
}