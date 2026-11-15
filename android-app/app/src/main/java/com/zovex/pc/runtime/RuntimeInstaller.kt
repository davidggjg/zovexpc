package com.zovex.pc.runtime

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.zip.ZipInputStream
import kotlin.coroutines.coroutineContext

/**
 * Downloads, verifies and unpacks the runtime payloads into [root].
 *
 * Shared by every runtime strategy under consideration (glibc rootfs, bionic
 * build, or direct-loader execution), so it is settled before that choice is.
 *
 * Nothing is installed unless its SHA-256 matches the pinned digest: these are
 * executables fetched over the network, and a wrong or tampered payload must
 * fail loudly rather than run.
 */
class RuntimeInstaller(private val root: File) {

    fun installDir(asset: RuntimeAsset): File = File(root, asset.id)

    fun isInstalled(asset: RuntimeAsset): Boolean =
        File(installDir(asset), STAMP).takeIf { it.isFile }?.readText() == asset.sha256

    /**
     * Installs [asset], emitting progress as it goes. A failure is emitted as
     * [InstallState.FAILED] rather than thrown, so the UI can show it; the
     * flow then ends.
     */
    fun install(asset: RuntimeAsset): Flow<InstallProgress> = flow {
        if (isInstalled(asset)) {
            emit(InstallProgress(asset, InstallState.READY, 1f, "מותקן"))
            return@flow
        }

        val work = File(root, "${asset.id}.part").apply { parentFile?.mkdirs() }
        val archive = File(root, "${asset.id}.download")

        try {
            emit(InstallProgress(asset, InstallState.DOWNLOADING, 0f, "מוריד ${asset.displayNameHe}…"))
            val digest = download(asset, archive) { done ->
                val fraction = if (asset.sizeBytes > 0) {
                    (done.toFloat() / asset.sizeBytes).coerceIn(0f, 1f)
                } else null
                emit(InstallProgress(asset, InstallState.DOWNLOADING, fraction, "מוריד ${asset.displayNameHe}…"))
            }

            emit(InstallProgress(asset, InstallState.VERIFYING, null, "מאמת…"))
            if (!digest.equals(asset.sha256, ignoreCase = true)) {
                archive.delete()
                emit(
                    InstallProgress(
                        asset, InstallState.FAILED, null,
                        "האימות נכשל: הקובץ שהתקבל אינו תואם. ההתקנה בוטלה.",
                    )
                )
                return@flow
            }

            emit(InstallProgress(asset, InstallState.EXTRACTING, null, "מחלץ…"))
            work.deleteRecursively()
            extract(archive, work)
            applyExecutableBits(asset, work)

            // Swap into place only once the payload is complete and verified,
            // so an interrupted install never leaves a half-usable runtime.
            val target = installDir(asset)
            target.deleteRecursively()
            if (!work.renameTo(target)) throw IOException("Cannot move ${work.name} into place")
            File(target, STAMP).writeText(asset.sha256)
            archive.delete()

            emit(InstallProgress(asset, InstallState.READY, 1f, "מוכן"))
        } catch (io: IOException) {
            Log.e(TAG, "Install of ${asset.id} failed", io)
            work.deleteRecursively()
            archive.delete()
            emit(
                InstallProgress(
                    asset, InstallState.FAILED, null,
                    "ההתקנה נכשלה: ${io.message ?: "שגיאת רשת או אחסון"}",
                )
            )
        }
    }.flowOn(Dispatchers.IO)

    private suspend inline fun download(
        asset: RuntimeAsset,
        target: File,
        onProgress: (Long) -> Unit,
    ): String {
        val connection = (URL(asset.url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 30_000
            readTimeout = 60_000
            instanceFollowRedirects = true
        }
        try {
            val code = connection.responseCode
            if (code != HttpURLConnection.HTTP_OK) {
                throw IOException("השרת החזיר $code")
            }
            val sha = MessageDigest.getInstance("SHA-256")
            var done = 0L
            var lastReported = 0L

            connection.inputStream.use { input ->
                target.outputStream().use { output ->
                    val buffer = ByteArray(1 shl 16)
                    while (true) {
                        coroutineContext.ensureActive()
                        val read = input.read(buffer)
                        if (read <= 0) break
                        output.write(buffer, 0, read)
                        sha.update(buffer, 0, read)
                        done += read
                        // Reporting every chunk would flood the UI.
                        if (done - lastReported >= PROGRESS_STEP) {
                            lastReported = done
                            onProgress(done)
                        }
                    }
                }
            }
            onProgress(done)
            return sha.digest().joinToString("") { "%02x".format(it) }
        } finally {
            connection.disconnect()
        }
    }

    private suspend fun extract(archive: File, target: File) {
        target.mkdirs()
        val canonicalTarget = target.canonicalPath
        ZipInputStream(archive.inputStream().buffered()).use { zip ->
            while (true) {
                coroutineContext.ensureActive()
                val entry = zip.nextEntry ?: break
                val out = File(target, entry.name)

                // Reject paths that escape the target directory: a crafted
                // archive must not be able to write outside the runtime dir.
                if (!out.canonicalPath.startsWith(canonicalTarget + File.separator)) {
                    throw IOException("ארכיון פגום: נתיב חורג (${entry.name})")
                }

                if (entry.isDirectory) {
                    out.mkdirs()
                } else {
                    out.parentFile?.mkdirs()
                    out.outputStream().use { zip.copyTo(it) }
                }
                zip.closeEntry()
            }
        }
    }

    /**
     * ZIP carries no Unix permission bits, so executables come out unreadable
     * to exec(). The asset declares which directories hold binaries.
     */
    private fun applyExecutableBits(asset: RuntimeAsset, target: File) {
        for (dir in asset.executableDirs) {
            val base = File(target, dir)
            if (!base.isDirectory) continue
            base.walkTopDown().forEach { file ->
                if (file.isFile && !file.setExecutable(true, true)) {
                    Log.w(TAG, "Could not mark ${file.path} executable")
                }
            }
        }
    }

    private companion object {
        const val TAG = "ZovexInstaller"
        const val STAMP = ".installed"
        const val PROGRESS_STEP = 1L shl 20
    }
}
