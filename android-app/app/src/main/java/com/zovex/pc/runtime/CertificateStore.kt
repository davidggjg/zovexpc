package com.zovex.pc.runtime

import android.util.Base64
import android.util.Log
import java.io.File
import java.security.KeyStore
import java.security.cert.X509Certificate
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

/**
 * Exports Android's trusted CA certificates into the prefix as a PEM bundle.
 *
 * Without this, Wine has no trust store: every program that talks to an HTTPS
 * API fails with "certificate verify failed". That breaks exactly the programs
 * people care about, so the bundle is written when an environment is created
 * and refreshed when the system trust store changes.
 */
object CertificateStore {

    private const val TAG = "ZovexCerts"

    /** Where programs and our own tooling look for the bundle inside C:\. */
    fun bundlePath(layout: PrefixLayout): File =
        File(layout.driveC, "ZOVEX/ssl/ca-bundle.crt")

    /**
     * Writes the PEM bundle. Returns the number of certificates written, or a
     * failure if the trust store could not be read or the file not written.
     */
    fun export(layout: PrefixLayout): Result<Int> = runCatching {
        val certs = systemCertificates()
        if (certs.isEmpty()) error("Android reported no trusted CA certificates")

        val target = bundlePath(layout)
        target.parentFile?.mkdirs()

        // Write to a temp file and rename, so a crash mid-write cannot leave a
        // truncated bundle that would fail verification in confusing ways.
        val tmp = File(target.parentFile, target.name + ".tmp")
        tmp.bufferedWriter().use { out ->
            for (cert in certs) {
                out.write(toPem(cert))
            }
        }
        if (!tmp.renameTo(target)) {
            tmp.delete()
            error("Cannot move the certificate bundle into place")
        }

        Log.i(TAG, "Exported ${certs.size} CA certificates to ${target.absolutePath}")
        certs.size
    }

    private fun systemCertificates(): List<X509Certificate> {
        val tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
        tmf.init(null as KeyStore?)
        return tmf.trustManagers
            .filterIsInstance<X509TrustManager>()
            .flatMap { it.acceptedIssuers.toList() }
            .distinctBy { it.subjectX500Principal to it.serialNumber }
    }

    private fun toPem(cert: X509Certificate): String {
        val body = Base64.encodeToString(cert.encoded, Base64.NO_WRAP)
        val wrapped = body.chunked(64).joinToString("\n")
        return buildString {
            append("# ").append(cert.subjectX500Principal.name).append('\n')
            append("-----BEGIN CERTIFICATE-----\n")
            append(wrapped).append('\n')
            append("-----END CERTIFICATE-----\n")
        }
    }
}
