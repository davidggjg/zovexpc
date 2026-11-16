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
        version = "runtime-8",
        url = "https://github.com/davidggjg/zovexpc/releases/download/runtime-8/zovex-runtime.zip",
        sha256 = "95a7f96389a4225c9da6e19ed7362e56cb4f34fb9e0a719e51b8103a10bb70fa",
        sizeBytes = 126_728_690,
        licence = "Wine (LGPL-2.1), Box64 (MIT), PRoot (GPL-2.0), Ubuntu base (various)",
        executableDirs = listOf("box64/bin", "wine/bin", "proot/bin"),
        // Unpacked on the device: a Linux rootfs carries modes and special
        // files that ZIP cannot represent.
        //
        // Two trees, not one. Wine is x86_64, so Box64 has to resolve its
        // libraries against an x86_64 glibc; the arm64 rootfs cannot serve
        // that, which is why ntdll.so would not load.
        innerTarballs = mapOf(
            "rootfs.tar.gz" to "rootfs",
            "rootfs-x86_64.tar.gz" to "rootfs-x86_64",
        ),
    )

    val all = listOf(WINDOWS_RUNTIME)
}
