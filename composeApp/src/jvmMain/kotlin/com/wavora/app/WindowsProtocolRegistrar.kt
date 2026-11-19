package com.wavora.app

import com.sun.jna.platform.win32.Advapi32Util
import com.sun.jna.platform.win32.WinReg
import com.wavora.logger.Logger

/**
 * Registers the "aira://" custom URI protocol handler in Windows Registry
 * under HKEY_CURRENT_USER (no admin rights required).
 *
 * Registry structure:
 * ```
 * HKCU\Software\Classes\aira
 *     (Default) = "URL:Aira Protocol"
 *     URL Protocol = ""
 *     \DefaultIcon
 *         (Default) = "\"<exe_path>\",0"
 *     \shell\open\command
 *         (Default) = "\"<exe_path>\" \"%1\""
 * ```
 *
 * The legacy `wavora` key from pre-1.0.2 builds is removed on registration.
 *
 * Writes go through Advapi32 (JNA) directly instead of spawning
 * `cmd.exe /c reg add`, so no console window flashes on startup.
 */
object WindowsProtocolRegistrar {
    private const val TAG = "WindowsProtocolRegistrar"
    private const val SCHEME = "aira"
    private const val LEGACY_SCHEME = "wavora"
    private const val REG_ROOT = "Software\\Classes\\$SCHEME"
    private const val COMMAND_KEY = "$REG_ROOT\\shell\\open\\command"
    private const val LEGACY_ROOT = "Software\\Classes\\$LEGACY_SCHEME"

    /** Launcher names to look for next to the bundled runtime, newest first. */
    private val EXE_CANDIDATES = listOf("Aira.exe", "Wavora.exe")

    fun register() {
        if (!System.getProperty("os.name", "").contains("Windows", ignoreCase = true)) return

        val exePath = resolveExePath() ?: run {
            Logger.e(TAG, "Could not resolve executable path, skipping protocol registration")
            return
        }

        // Dev runs (`gradlew :desktopApp:run`) resolve to java.exe/javaw.exe:
        // rewriting the handler to a JDK path would fight with the installed
        // launcher and re-register on every start, so skip them entirely.
        if (isJdkLauncher(exePath)) {
            Logger.d(TAG, "Running from a JDK launcher, skipping protocol registration")
            return
        }

        try {
            if (isAlreadyRegistered(exePath)) {
                Logger.d(TAG, "Protocol handler already registered with correct path")
                removeLegacyKey()
                return
            }

            Logger.d(TAG, "$SCHEME:// protocol handler -> $exePath")

            // Main key with protocol description
            setString(REG_ROOT, "", "URL:Aira Protocol")
            setString(REG_ROOT, "URL Protocol", "")

            // DefaultIcon
            setString("$REG_ROOT\\DefaultIcon", "", "\"$exePath\",0")

            // shell\open\command
            setString(COMMAND_KEY, "", "\"$exePath\" \"%1\"")

            removeLegacyKey()

            Logger.d(TAG, "Protocol handler registered successfully")
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to register protocol handler: ${e.message}")
        }
    }

    /** Drops the `wavora://` handler left behind by earlier builds. */
    private fun removeLegacyKey() {
        try {
            if (Advapi32Util.registryKeyExists(WinReg.HKEY_CURRENT_USER, LEGACY_ROOT)) {
                Advapi32Util.registryDeleteKey(WinReg.HKEY_CURRENT_USER, LEGACY_ROOT)
                Logger.d(TAG, "Removed legacy $LEGACY_SCHEME:// protocol handler")
            }
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to remove legacy protocol handler: ${e.message}")
        }
    }

    private fun setString(keyPath: String, valueName: String, data: String) {
        Advapi32Util.registryCreateKey(WinReg.HKEY_CURRENT_USER, keyPath)
        Advapi32Util.registrySetStringValue(WinReg.HKEY_CURRENT_USER, keyPath, valueName, data)
    }

    private fun readDefault(keyPath: String): String? = try {
        Advapi32Util.registryGetStringValue(WinReg.HKEY_CURRENT_USER, keyPath, "")
    } catch (_: Exception) {
        null
    }

    private fun isAlreadyRegistered(currentExePath: String): Boolean {
        // Registry stores path with quotes: "C:\path\to\Aira.exe" "%1"
        // Normalize both for comparison
        val stored = readDefault(COMMAND_KEY) ?: return false
        val storedNormalized = stored.replace("\\", "/").lowercase()
        val exeNormalized = currentExePath.replace("\\", "/").lowercase()
        return exeNormalized.isNotEmpty() && storedNormalized.contains(exeNormalized)
    }

    private fun isJdkLauncher(exePath: String): Boolean {
        val name = exePath.replace("/", "\\").substringAfterLast('\\').lowercase()
        return name == "java.exe" || name == "javaw.exe"
    }

    private fun resolveExePath(): String? {
        // JPackage directory structure:
        //   <app>/runtime/...  (java.home points here)
        //   <app>/Aira.exe
        // So we go: java.home → parent (runtime) → parent (app) → Aira.exe
        val javaHome = System.getProperty("java.home") ?: return null
        val javaHomeDir = java.io.File(javaHome)

        // Try JPackage layout: java.home is <app>/runtime/... or <app>/runtime
        val appDir = if (javaHomeDir.name == "runtime") {
            javaHomeDir.parentFile
        } else {
            // java.home might be deeper, e.g., <app>/runtime/conf/...
            generateSequence(javaHomeDir) { it.parentFile }
                .firstOrNull { it.name == "runtime" }
                ?.parentFile
        }

        if (appDir != null) {
            EXE_CANDIDATES.forEach { name ->
                val exeFile = java.io.File(appDir, name)
                if (exeFile.isFile) return exeFile.absolutePath
            }
            // Launcher may be renamed: take the first executable that is not a JDK stub
            appDir.listFiles()
                ?.firstOrNull {
                    it.isFile &&
                        it.extension.equals("exe", ignoreCase = true) &&
                        !it.nameWithoutExtension.equals("java", ignoreCase = true) &&
                        !it.nameWithoutExtension.equals("javaw", ignoreCase = true)
                }
                ?.let { return it.absolutePath }
        }

        // Fallback: running from IDE/dev environment, use current process
        return ProcessHandle.current().info().command().orElse(null)
    }
}
