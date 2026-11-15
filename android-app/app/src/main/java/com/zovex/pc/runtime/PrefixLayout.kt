package com.zovex.pc.runtime

import java.io.File

/**
 * The on-disk shape of a ZOVEX environment: a Wine prefix plus the directories
 * the desktop shell expects to find. These are our own directories inside app
 * storage — no Windows files are involved.
 *
 * Independent of whether the runtime execs directly or through proot, so this
 * layout is settled before that decision.
 */
class PrefixLayout(val root: File) {

    /** The Wine prefix itself (WINEPREFIX). */
    val prefix: File get() = File(root, "prefix")

    /** What the user sees as `C:\`. */
    val driveC: File get() = File(prefix, "drive_c")

    val programFiles: File get() = File(driveC, "Program Files")
    val programFilesX86: File get() = File(driveC, "Program Files (x86)")
    val windows: File get() = File(driveC, "Windows")
    val temp: File get() = File(driveC, "Temp")
    val games: File get() = File(driveC, "Games")

    val userHome: File get() = File(driveC, "Users/$USER_NAME")
    val desktop: File get() = File(userHome, "Desktop")
    val downloads: File get() = File(userHome, "Downloads")
    val documents: File get() = File(userHome, "Documents")
    val pictures: File get() = File(userHome, "Pictures")

    /** Our own data, deliberately outside drive_c so a C:\ wipe keeps it. */
    val config: File get() = File(root, "config")
    val shaderCache: File get() = File(root, "shader_cache")
    val logs: File get() = File(root, "logs")

    val isInitialized: Boolean get() = File(config, STAMP).isFile

    /**
     * Creates every directory the shell relies on. Safe to call repeatedly;
     * existing content is left untouched.
     *
     * Note that Wine itself creates drive_c and the registry on first
     * wineboot. We pre-create the user-visible tree so the Explorer and the
     * Downloads target exist even before the runtime has ever started.
     */
    fun create(): Result<Unit> = runCatching {
        migrateLowercaseUsers()

        val required = listOf(
            driveC, programFiles, programFilesX86, windows, temp, games,
            userHome, desktop, downloads, documents, pictures,
            config, shaderCache, logs,
        )
        for (dir in required) {
            if (!dir.isDirectory && !dir.mkdirs()) {
                error("Cannot create ${dir.absolutePath}")
            }
        }
    }

    /**
     * Earlier builds created `drive_c/users`. Android's filesystem is
     * case-sensitive, so after the rename to `Users` anything already
     * imported would simply stop being visible. Move it rather than
     * stranding the user's files.
     */
    private fun migrateLowercaseUsers() {
        val old = File(driveC, "users")
        val new = File(driveC, "Users")
        if (!old.isDirectory || new.exists()) return
        if (!old.renameTo(new)) {
            android.util.Log.w("ZovexPrefix", "Could not migrate ${old.path} to ${new.path}")
        }
    }

    fun markInitialized(runtimeVersion: String) {
        File(config, STAMP).writeText(runtimeVersion)
    }

    companion object {
        /** Also the Windows user name shown in C:\users\. */
        const val USER_NAME = "ZOVEX"

        private const val STAMP = "initialized"

        /** The default environment, under the app's private files dir. */
        fun default(filesDir: File) = PrefixLayout(File(filesDir, "environments/default"))
    }
}
