package com.zovex.pc.runtime

/**
 * The runtime payloads this build knows how to install.
 *
 * The digest is the one printed by the workflow that produced the archive.
 * Bumping the payload means bumping both the URL and the digest here: an
 * archive that does not match is refused rather than installed.
 */
object RuntimeCatalog {

    val WINDOWS_RUNTIME = RuntimeAsset(
        id = "windows-runtime",
        displayNameHe = "מנוע Windows",
        version = "runtime-6",
        url = "https://github.com/davidggjg/zovexpc/releases/download/runtime-6/zovex-runtime.zip",
        sha256 = "3a080021aed63457d42edfde0cfa58835a86144a72691f7021987cde68c55069",
        sizeBytes = 126_719_716,
        licence = "Wine (LGPL-2.1), Box64 (MIT), PRoot (GPL-2.0), Ubuntu base (various)",
        executableDirs = listOf("box64/bin", "wine/bin", "proot/bin"),
        // Unpacked on the device: a Linux rootfs carries modes and special
        // files that ZIP cannot represent.
        innerTarGz = "rootfs.tar.gz",
        innerTarGzTarget = "rootfs",
    )

    val all = listOf(WINDOWS_RUNTIME)
}
