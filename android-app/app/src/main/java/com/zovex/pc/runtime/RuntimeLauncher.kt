package com.zovex.pc.runtime

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.TimeUnit

data class ExecResult(
    val exitCode: Int,
    val stdout: String,
    val stderr: String,
    val durationMs: Long,
) {
    val ok: Boolean get() = exitCode == 0

    /** The signal that killed the process, if one did. */
    val killedBy: String?
        get() = if (exitCode > 128 && exitCode < 160) {
            val n = exitCode - 128
            "signal $n" + when (n) {
                4 -> " (SIGILL)"
                6 -> " (SIGABRT)"
                7 -> " (SIGBUS)"
                11 -> " (SIGSEGV)"
                31 -> " (SIGSYS — a syscall the Android seccomp filter forbids)"
                else -> ""
            }
        } else null
    val combined: String get() = buildString {
        if (stdout.isNotBlank()) append(stdout)
        if (stderr.isNotBlank()) {
            if (isNotEmpty()) append('\n')
            append(stderr)
        }
    }
}

/**
 * Runs programs that live in the Linux rootfs.
 *
 * Android has no chroot for unprivileged apps, so a glibc binary cannot
 * simply be executed: its interpreter path (/lib/ld-linux-aarch64.so.1) does
 * not exist on the device. Instead the rootfs's own dynamic loader is invoked
 * directly and pointed at the rootfs libraries. This works only because the
 * device permits executable mappings in app storage, which the stage 1 probe
 * measured rather than assumed.
 *
 * If this approach fails on a device, the fallback is PRoot; the error is
 * reported rather than hidden so that is a decision based on evidence.
 */
class RuntimeLauncher(private val installDir: File) {

    val rootfs: File get() = File(installDir, "rootfs")
    val box64: File get() = File(installDir, "box64/bin/box64")
    val wine: File get() = File(installDir, "wine/bin/wine64")

    val isInstalled: Boolean get() = rootfs.isDirectory && box64.isFile

    /** The rootfs's dynamic loader, or null when it cannot be found. */
    fun loader(): File? = LOADER_CANDIDATES
        .map { File(rootfs, it) }
        .firstOrNull { it.isFile }

    private fun libraryPath(): String = LIBRARY_DIRS
        .map { File(rootfs, it) }
        .filter { it.isDirectory }
        .joinToString(":") { it.absolutePath }

    /**
     * Runs a binary that lives inside the rootfs, given its path relative to
     * the rootfs root (for example "bin/ls").
     */
    suspend fun runInRootfs(
        relativeBinary: String,
        args: List<String> = emptyList(),
        timeoutSeconds: Long = 30,
        extraEnv: Map<String, String> = emptyMap(),
    ): ExecResult = withContext(Dispatchers.IO) {
        val loader = loader()
            ?: return@withContext ExecResult(
                -1, "",
                "לא נמצא loader ב-rootfs. חיפשתי: ${LOADER_CANDIDATES.joinToString(", ")}",
                0,
            )

        val binary = File(rootfs, relativeBinary)
        if (!binary.isFile) {
            return@withContext ExecResult(-1, "", "לא נמצא ${binary.absolutePath}", 0)
        }

        // The loader and the target both need the executable bit; ZIP and tar
        // extraction can leave it off.
        loader.setExecutable(true, false)
        binary.setExecutable(true, false)

        val command = buildList {
            add(loader.absolutePath)
            add("--library-path")
            add(libraryPath())
            add(binary.absolutePath)
            addAll(args)
        }

        execute(command, timeoutSeconds, extraEnv)
    }

    /**
     * Runs Box64, which is itself an aarch64 glibc binary: its ELF
     * interpreter does not exist on Android, so executing it directly fails
     * with ENOENT. It goes through the rootfs loader like everything else.
     */
    suspend fun runBox64(
        args: List<String>,
        timeoutSeconds: Long = 60,
        extraEnv: Map<String, String> = emptyMap(),
    ): ExecResult = withContext(Dispatchers.IO) {
        val loader = loader()
            ?: return@withContext ExecResult(-1, "", "לא נמצא loader ב-rootfs.", 0)
        if (!box64.isFile) {
            return@withContext ExecResult(-1, "", "לא נמצא ${box64.absolutePath}", 0)
        }
        loader.setExecutable(true, false)
        box64.setExecutable(true, false)

        execute(
            listOf(loader.absolutePath, "--library-path", libraryPath(), box64.absolutePath) + args,
            timeoutSeconds,
            extraEnv,
        )
    }

    /** Runs an arbitrary command with no rootfs wrapping. */
    suspend fun runRaw(
        command: List<String>,
        timeoutSeconds: Long = 30,
        extraEnv: Map<String, String> = emptyMap(),
    ): ExecResult = withContext(Dispatchers.IO) { execute(command, timeoutSeconds, extraEnv) }

    private fun execute(
        command: List<String>,
        timeoutSeconds: Long,
        extraEnv: Map<String, String> = emptyMap(),
    ): ExecResult {
        val started = System.currentTimeMillis()
        return try {
            val process = ProcessBuilder(command)
                .directory(installDir)
                .apply {
                    environment()["HOME"] = File(rootfs, "root").absolutePath
                    environment()["TMPDIR"] = File(installDir, "tmp").apply { mkdirs() }.absolutePath
                    environment()["PATH"] = "/usr/bin:/bin"
                    environment().putAll(extraEnv)
                }
                .start()

            val out = process.inputStream.bufferedReader().use { it.readText() }
            val err = process.errorStream.bufferedReader().use { it.readText() }

            val finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS)
            if (!finished) {
                process.destroyForcibly()
                return ExecResult(
                    -1, out, "${err}\nהתהליך לא הסתיים תוך $timeoutSeconds שניות ונעצר.",
                    System.currentTimeMillis() - started,
                )
            }

            ExecResult(process.exitValue(), out, err, System.currentTimeMillis() - started)
        } catch (t: Throwable) {
            // An exec denied by the kernel or SELinux surfaces here, and that
            // is the signal that this device needs the PRoot path.
            Log.w(TAG, "exec failed: ${command.firstOrNull()}", t)
            ExecResult(
                -1, "", t.message ?: t::class.java.simpleName,
                System.currentTimeMillis() - started,
            )
        }
    }

    private companion object {
        const val TAG = "ZovexLauncher"

        val LOADER_CANDIDATES = listOf(
            "lib/ld-linux-aarch64.so.1",
            "lib64/ld-linux-aarch64.so.1",
            "usr/lib/ld-linux-aarch64.so.1",
            "lib/aarch64-linux-gnu/ld-linux-aarch64.so.1",
            "usr/lib/aarch64-linux-gnu/ld-linux-aarch64.so.1",
        )

        val LIBRARY_DIRS = listOf(
            "lib/aarch64-linux-gnu",
            "usr/lib/aarch64-linux-gnu",
            "lib",
            "usr/lib",
            "lib64",
            "usr/local/lib",
        )
    }
}
