package com.zovex.pc.core

object NativeBridge {

    @Volatile
    private var loadError: String? = null

    val isLoaded: Boolean get() = loadError == null

    /** Non-null when [isLoaded] is false; safe to show to the user. */
    val failureReason: String? get() = loadError

    init {
        loadError = try {
            System.loadLibrary("zovex_native")
            null
        } catch (t: UnsatisfiedLinkError) {
            "Native engine failed to load: ${t.message}"
        }
    }

    fun version(): String =
        if (isLoaded) nativeVersion() else "unavailable"

    /** Returns null when the native engine is not loaded. */
    fun probeVulkan(): VulkanReport? =
        if (isLoaded) nativeProbeVulkan() else null

    /**
     * Measures whether this device permits executable file mappings inside
     * [writableDir]. Returns null when the native engine is not loaded.
     */
    fun probeExec(writableDir: String): ExecReport? =
        if (isLoaded) nativeProbeExec(writableDir) else null

    private external fun nativeVersion(): String
    private external fun nativeProbeVulkan(): VulkanReport?
    private external fun nativeProbeExec(dir: String): ExecReport?
}
