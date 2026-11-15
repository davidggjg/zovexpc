package com.zovex.pc.runtime

/**
 * A runtime payload the app fetches at first launch rather than bundling.
 *
 * These are large third-party components (Wine, Box64, graphics layers) under
 * their own licences, so they stay out of the APK and out of this repository.
 * Every asset is pinned by URL and SHA-256: a payload that does not match its
 * digest is discarded rather than installed.
 */
data class RuntimeAsset(
    /** Stable id, also the directory name under runtime/. */
    val id: String,
    val displayNameHe: String,
    val version: String,
    val url: String,
    val sha256: String,
    val sizeBytes: Long,
    val licence: String,
    /** Paths inside the archive that must be executable after extraction. */
    val executableDirs: List<String> = emptyList(),
    /** A tarball inside the archive to unpack on the device, if any. */
    val innerTarGz: String? = null,
    /** Where that tarball is unpacked, relative to the install directory. */
    val innerTarGzTarget: String = "rootfs",
) {
    init {
        require(sha256.length == 64) { "sha256 for $id must be 64 hex characters" }
        require(url.startsWith("https://")) { "asset $id must be fetched over HTTPS" }
    }
}

enum class InstallState { MISSING, DOWNLOADING, VERIFYING, EXTRACTING, UNPACKING_ROOTFS, READY, FAILED }

data class InstallProgress(
    val asset: RuntimeAsset,
    val state: InstallState,
    /** 0f..1f within the current state, or null when it cannot be known. */
    val fraction: Float? = null,
    val messageHe: String = "",
)
