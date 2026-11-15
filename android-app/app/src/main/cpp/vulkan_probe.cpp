#include "probes.h"
#include "log.h"

#include <dlfcn.h>
#include <vector>
#include <cstring>

// Minimal hand-rolled Vulkan declarations: the NDK ships vulkan.h, but
// declaring only what we use keeps the probe independent of header drift and
// makes the dlopen path explicit.
#include <vulkan/vulkan.h>

namespace zovex {

namespace {

template <typename Fn>
Fn load(void* lib, const char* name) {
    return reinterpret_cast<Fn>(dlsym(lib, name));
}

} // namespace

VulkanInfo probeVulkan() {
    VulkanInfo info;

    void* lib = dlopen("libvulkan.so", RTLD_NOW | RTLD_LOCAL);
    if (!lib) {
        info.detail = "No Vulkan loader on this device (libvulkan.so missing).";
        return info;
    }

    auto vkCreateInstance_ = load<PFN_vkCreateInstance>(lib, "vkCreateInstance");
    auto vkEnumeratePhysicalDevices_ = load<PFN_vkEnumeratePhysicalDevices>(lib, "vkEnumeratePhysicalDevices");
    auto vkGetPhysicalDeviceProperties2_ = load<PFN_vkGetPhysicalDeviceProperties2>(lib, "vkGetPhysicalDeviceProperties2");
    auto vkGetPhysicalDeviceMemoryProperties_ = load<PFN_vkGetPhysicalDeviceMemoryProperties>(lib, "vkGetPhysicalDeviceMemoryProperties");
    auto vkGetPhysicalDeviceFeatures2_ = load<PFN_vkGetPhysicalDeviceFeatures2>(lib, "vkGetPhysicalDeviceFeatures2");
    auto vkDestroyInstance_ = load<PFN_vkDestroyInstance>(lib, "vkDestroyInstance");

    if (!vkCreateInstance_ || !vkEnumeratePhysicalDevices_ ||
        !vkGetPhysicalDeviceProperties2_ || !vkGetPhysicalDeviceFeatures2_ ||
        !vkGetPhysicalDeviceMemoryProperties_) {
        info.detail = "Vulkan loader present but core 1.1 entry points are missing.";
        dlclose(lib);
        return info;
    }

    VkApplicationInfo app{};
    app.sType = VK_STRUCTURE_TYPE_APPLICATION_INFO;
    app.pApplicationName = "ZOVEX PC";
    app.apiVersion = VK_API_VERSION_1_1;

    VkInstanceCreateInfo ci{};
    ci.sType = VK_STRUCTURE_TYPE_INSTANCE_CREATE_INFO;
    ci.pApplicationInfo = &app;

    VkInstance instance = VK_NULL_HANDLE;
    if (vkCreateInstance_(&ci, nullptr, &instance) != VK_SUCCESS) {
        info.detail = "Device does not support Vulkan 1.1, which the graphics path requires.";
        dlclose(lib);
        return info;
    }

    uint32_t count = 0;
    vkEnumeratePhysicalDevices_(instance, &count, nullptr);
    if (count == 0) {
        info.detail = "Vulkan 1.1 instance created but no physical device was reported.";
        vkDestroyInstance_(instance, nullptr);
        dlclose(lib);
        return info;
    }

    std::vector<VkPhysicalDevice> devices(count);
    vkEnumeratePhysicalDevices_(instance, &count, devices.data());
    VkPhysicalDevice gpu = devices[0];

    VkPhysicalDeviceDriverProperties driver{};
    driver.sType = VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_DRIVER_PROPERTIES;

    VkPhysicalDeviceProperties2 props{};
    props.sType = VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_PROPERTIES_2;
    props.pNext = &driver;
    vkGetPhysicalDeviceProperties2_(gpu, &props);

    VkPhysicalDeviceDescriptorIndexingFeatures di{};
    di.sType = VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_DESCRIPTOR_INDEXING_FEATURES;

    VkPhysicalDeviceTimelineSemaphoreFeatures ts{};
    ts.sType = VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_TIMELINE_SEMAPHORE_FEATURES;
    ts.pNext = &di;

    VkPhysicalDeviceFeatures2 feats{};
    feats.sType = VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_FEATURES_2;
    feats.pNext = &ts;
    vkGetPhysicalDeviceFeatures2_(gpu, &feats);

    VkPhysicalDeviceMemoryProperties mem{};
    vkGetPhysicalDeviceMemoryProperties_(gpu, &mem);
    for (uint32_t i = 0; i < mem.memoryHeapCount; ++i) {
        if (mem.memoryHeaps[i].flags & VK_MEMORY_HEAP_DEVICE_LOCAL_BIT) {
            info.deviceLocalBytes += mem.memoryHeaps[i].size;
        }
    }

    const uint32_t api = props.properties.apiVersion;
    info.apiMajor = VK_API_VERSION_MAJOR(api);
    info.apiMinor = VK_API_VERSION_MINOR(api);
    info.apiPatch = VK_API_VERSION_PATCH(api);
    info.deviceName = props.properties.deviceName;
    info.driverName = driver.driverName[0] ? driver.driverName : "unknown";
    info.descriptorIndexing = di.descriptorBindingPartiallyBound == VK_TRUE &&
                              di.runtimeDescriptorArray == VK_TRUE;
    info.timelineSemaphore = ts.timelineSemaphore == VK_TRUE;
    info.ok = true;
    info.detail = "Vulkan available.";

    ZLOGI("Vulkan %u.%u.%u on %s (%s); descriptorIndexing=%d timelineSemaphore=%d",
          info.apiMajor, info.apiMinor, info.apiPatch,
          info.deviceName.c_str(), info.driverName.c_str(),
          info.descriptorIndexing, info.timelineSemaphore);

    vkDestroyInstance_(instance, nullptr);
    // The loader stays mapped on purpose: the renderer will reuse it.
    return info;
}

} // namespace zovex
