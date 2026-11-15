package com.zovex.pc.core

import android.app.ActivityManager
import android.content.Context
import android.os.Build

enum class Support { YES, PARTIAL, NO }

data class Capability(
    val titleHe: String,
    val support: Support,
    val noteHe: String,
)

data class DeviceProfile(
    val socModel: String,
    val abi: String,
    val totalRamBytes: Long,
    val vulkan: VulkanReport?,
    val exec: ExecReport?,
    val capabilities: List<Capability>,
)

/**
 * Turns the raw probes into the claims we are willing to make to the user.
 * Deliberately conservative: anything we cannot demonstrate from a probe is
 * reported as PARTIAL or NO rather than assumed to work.
 */
object DeviceCapabilityScanner {

    private const val GIB = 1024L * 1024L * 1024L

    fun scan(context: Context): DeviceProfile {
        val vulkan = NativeBridge.probeVulkan()
        val exec = NativeBridge.probeExec(context.filesDir.absolutePath)
        val ram = totalRam(context)
        val abi = Build.SUPPORTED_ABIS.firstOrNull() ?: "unknown"

        return DeviceProfile(
            socModel = "${Build.MANUFACTURER} ${Build.MODEL} (${Build.HARDWARE})",
            abi = abi,
            totalRamBytes = ram,
            vulkan = vulkan,
            exec = exec,
            capabilities = buildList {
                add(arm64(abi))
                add(execMode(exec))
                add(vulkanBase(vulkan))
                add(dxvk(vulkan))
                add(directX12(vulkan))
                add(memory(ram))
            },
        )
    }

    private fun arm64(abi: String) = Capability(
        titleHe = "מעבד 64-bit (ARM64)",
        support = if (abi == "arm64-v8a") Support.YES else Support.NO,
        noteHe = if (abi == "arm64-v8a") {
            "מתאים. Box64 דורש תהליך 64-bit."
        } else {
            "המכשיר מדווח $abi. ZOVEX PC דורש arm64-v8a."
        },
    )

    private fun execMode(exec: ExecReport?) = when {
        exec == null -> Capability(
            titleHe = "הרצת קוד מהאחסון",
            support = Support.NO,
            noteHe = "לא נבדק — מנוע ה-native לא נטען.",
        )
        exec.ok -> Capability(
            titleHe = "הרצת קוד מהאחסון",
            support = Support.YES,
            noteHe = "המכשיר מתיר מיפוי הרצה. Wine ירוץ ישירות, בלי proot.",
        )
        else -> Capability(
            titleHe = "הרצת קוד מהאחסון",
            support = Support.PARTIAL,
            noteHe = "המכשיר חוסם מיפוי הרצה (W^X). נידרש למסלול proot, עם עלות ביצועים.",
        )
    }

    private fun vulkanBase(v: VulkanReport?) = when {
        v == null || !v.ok -> Capability(
            titleHe = "Vulkan",
            support = Support.NO,
            noteHe = v?.detail ?: "לא נבדק — מנוע ה-native לא נטען.",
        )
        else -> Capability(
            titleHe = "Vulkan ${v.apiVersion}",
            support = Support.YES,
            noteHe = "${v.deviceName} · דרייבר ${v.driverName}",
        )
    }

    private fun dxvk(v: VulkanReport?) = when {
        v == null || !v.ok -> Capability(
            titleHe = "משחקי DirectX 9 / 11 (DXVK)",
            support = Support.NO,
            noteHe = "דורש Vulkan תקין.",
        )
        v.timelineSemaphore -> Capability(
            titleHe = "משחקי DirectX 9 / 11 (DXVK)",
            support = Support.YES,
            noteHe = "timeline semaphores קיימים — DXVK 2.x נתמך.",
        )
        else -> Capability(
            titleHe = "משחקי DirectX 9 / 11 (DXVK)",
            support = Support.PARTIAL,
            noteHe = "אין timeline semaphores — רק קו ה-DXVK הישן, עם פחות משחקים.",
        )
    }

    private fun directX12(v: VulkanReport?) = when {
        v == null || !v.ok -> Capability(
            titleHe = "משחקי DirectX 12 (VKD3D-Proton)",
            support = Support.NO,
            noteHe = "דורש Vulkan תקין.",
        )
        v.descriptorIndexing -> Capability(
            titleHe = "משחקי DirectX 12 (VKD3D-Proton)",
            support = Support.PARTIAL,
            noteHe = "descriptor indexing קיים, אבל DX12 על Adreno נותר ניסיוני. " +
                "לא מובטח לכל משחק.",
        )
        else -> Capability(
            titleHe = "משחקי DirectX 12 (VKD3D-Proton)",
            support = Support.NO,
            noteHe = "חסר descriptor indexing. VKD3D-Proton לא יעבוד במכשיר הזה.",
        )
    }

    private fun memory(bytes: Long): Capability {
        val gib = bytes.toDouble() / GIB
        val rounded = String.format("%.1f", gib)
        return when {
            bytes >= 11 * GIB -> Capability(
                titleHe = "זיכרון ($rounded GB)",
                support = Support.YES,
                noteHe = "מספיק גם למשחקים כבדים יותר ולמספר חלונות במקביל.",
            )
            bytes >= 7 * GIB -> Capability(
                titleHe = "זיכרון ($rounded GB)",
                support = Support.PARTIAL,
                noteHe = "מתאים לתוכנות ולמשחקים בינוניים. משחקים כבדים עלולים להיחסם.",
            )
            else -> Capability(
                titleHe = "זיכרון ($rounded GB)",
                support = Support.NO,
                noteHe = "מתחת למינימום המעשי. צפויות קריסות בזיכרון.",
            )
        }
    }

    private fun totalRam(context: Context): Long {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val info = ActivityManager.MemoryInfo()
        am.getMemoryInfo(info)
        return info.totalMem
    }
}
