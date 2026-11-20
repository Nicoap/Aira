package com.wavora.appdata.io

import com.wavora.logger.Logger
import java.io.File
import java.nio.file.Files

private const val TAG = "HomeFolder"

/** Root data folder Aira keeps in the user's home directory. */
const val APP_FOLDER = ".aira"

/** Folder name used before the rebrand; its contents are moved once. */
private const val LEGACY_APP_FOLDER = ".wavora"

/** Folder Aira keeps under `%LOCALAPPDATA%` (logs, bundled helper apps). */
const val LOCAL_APP_FOLDER = "Aira"

/** Folder name used under `%LOCALAPPDATA%` before the rebrand. */
private const val LEGACY_LOCAL_APP_FOLDER = "Wavora"

fun getHomeFolderPath(
    additionalPath: List<String>
): String = System.getProperty("user.home").let {
    if (additionalPath.isEmpty()) {
        it
    } else {
        buildString {
            append(it)
            additionalPath.forEach { path ->
                append(File.separator).append(path)
            }
        }
    }
}

/**
 * Data root: `~/.aira` (settings, database, CEF cache, offline cache).
 *
 * On the first launch after the rebrand the contents of `~/.wavora` are moved
 * here, so an existing install keeps its library, history and preferences.
 * The move is per-child and idempotent: it is safe to run in any order
 * relative to the settings/database files being created.
 */
fun appHomeFolder(): File =
    migrateFolder(
        base = File(System.getProperty("user.home")),
        name = APP_FOLDER,
        legacyName = LEGACY_APP_FOLDER,
    )

/**
 * Per-user folder for logs and bundled helper apps: `%LOCALAPPDATA%\Aira` on
 * Windows, `~/.aira` elsewhere. Migrates `%LOCALAPPDATA%\Wavora` the same way.
 */
fun localAppFolder(): File {
    val base = System.getenv("LOCALAPPDATA")?.takeIf { it.isNotBlank() }
        ?: return appHomeFolder()
    return migrateFolder(
        base = File(base),
        name = LOCAL_APP_FOLDER,
        legacyName = LEGACY_LOCAL_APP_FOLDER,
    )
}

private fun migrateFolder(base: File, name: String, legacyName: String): File {
    val current = File(base, name)
    val legacy = File(base, legacyName)

    if (legacy.isDirectory) {
        if (!current.exists()) current.mkdirs()
        legacy.listFiles()?.forEach { child ->
            val target = File(current, child.name)
            if (!target.exists()) {
                runCatching { Files.move(child.toPath(), target.toPath()) }
                    .onSuccess { Logger.d(TAG, "moved ${child.name} from $legacyName to $name") }
                    .onFailure {
                        Logger.w(TAG, "could not move ${child.name} out of $legacyName: ${it.message}")
                    }
            }
        }
        if (legacy.listFiles()?.isEmpty() == true) {
            runCatching { legacy.delete() }
                .onSuccess { Logger.d(TAG, "removed empty legacy folder $legacy") }
        }
    }

    current.mkdirs()
    return current
}
