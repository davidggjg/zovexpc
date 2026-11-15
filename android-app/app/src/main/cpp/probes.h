#pragma once
#include <string>

namespace zovex {

// Result of a dynamic Vulkan 1.1+ probe. Never throws; on failure `ok` is
// false and `detail` explains why in a user-presentable sentence.
struct VulkanInfo {
    bool ok = false;
    std::string detail;
    std::string deviceName;
    std::string driverName;
    uint32_t apiMajor = 0;
    uint32_t apiMinor = 0;
    uint32_t apiPatch = 0;
    bool descriptorIndexing = false;   // gate for VKD3D-Proton / DX12
    bool timelineSemaphore = false;    // gate for DXVK 2.x
    uint64_t deviceLocalBytes = 0;
};

VulkanInfo probeVulkan();

// Result of the Android execution probes.
struct ExecInfo {
    // True only when a real execve() of a file in app storage succeeded.
    // An executable *mapping* can be permitted while execve() is refused;
    // measuring the mapping alone gives a false positive.
    bool ok = false;
    bool mmapAllowed = false;
    bool execveAllowed = false;
    std::string detail;
    std::string path;
};

// Writes a tiny file into `dir`, maps it PROT_EXEC and reports whether the
// kernel/SELinux policy allows it. This decides whether the runtime can exec
// Wine binaries directly or must go through the proot path.
ExecInfo probeExecMapping(const std::string& dir);

} // namespace zovex
