package com.wavora.app.expect

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.swing.JFileChooser
import javax.swing.SwingUtilities

actual suspend fun pickFolder(initialPath: String?): String? =
    withContext(Dispatchers.IO) {
        var result: String? = null
        // Swing dialogs must be created and shown on the event dispatch thread.
        SwingUtilities.invokeAndWait {
            val chooser =
                JFileChooser().apply {
                    fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
                    isAcceptAllFileFilterUsed = false
                    initialPath?.takeIf { it.isNotBlank() }?.let { path ->
                        File(path).takeIf { it.exists() }?.let { currentDirectory = it }
                    }
                }
            if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
                result = chooser.selectedFile?.absolutePath
            }
        }
        result
    }