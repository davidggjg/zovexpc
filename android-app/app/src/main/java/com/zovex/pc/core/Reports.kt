package com.zovex.pc.core

/**
 * Filled in from C++ by field, so every field must be a non-private JVM field
 * and the class must keep its no-arg constructor. Do not convert to a data
 * class or add constructor parameters without updating jni_bridge.cpp.
 */
class VulkanReport {
    @JvmField var ok: Boolean = false
    @JvmField var detail: String = ""
    @JvmField var deviceName: String = ""
    @JvmField var driverName: String = ""
    @JvmField var apiMajor: Int = 0
    @JvmField var apiMinor: Int = 0
    @JvmField var apiPatch: Int = 0

    /** Gate for VKD3D-Proton, i.e. any Direct3D 12 support. */
    @JvmField var descriptorIndexing: Boolean = false

    /** Gate for DXVK 2.x; without it only the older DXVK line is usable. */
    @JvmField var timelineSemaphore: Boolean = false

    @JvmField var deviceLocalBytes: Long = 0

    val apiVersion: String get() = "$apiMajor.$apiMinor.$apiPatch"
}

/** See the note on [VulkanReport] — same JNI field-filling contract. */
class ExecReport {
    /** True only when a real execve() from app storage succeeded. */
    @JvmField var ok: Boolean = false

    /** An executable mapping was permitted. Not sufficient on its own. */
    @JvmField var mmapAllowed: Boolean = false

    /** execve() of a file in app storage was permitted. This is what counts. */
    @JvmField var execveAllowed: Boolean = false

    @JvmField var detail: String = ""
}
