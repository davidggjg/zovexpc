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
        version = "runtime-5",
        url = "https://github.com/davidggjg/zovexpc/releases/download/runtime-5/zovex-runtime.zip",
        sha256 = "cc17f36a5088006ae2860ee3597e06bdb12456eb2241743080ce73d40e0d6325",
        sizeBytes = 126_590_417,
        licence = "Wine (LGPL-2.1), Box64 (MIT), Ubuntu base (various)",
        executableDirs = listOf("box64/bin", "wine/bin"),
        // Unpacked on the device: a Linux rootfs carries modes and special
        // files that ZIP cannot represent.
        innerTarGz = "rootfs.tar.gz",
        innerTarGzTarget = "rootfs",
    )

    val all = listOf(WINDOWS_RUNTIME)
}
