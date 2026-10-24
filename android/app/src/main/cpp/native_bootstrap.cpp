#include <jni.h>
#include <android/log.h>
#include <android/native_window_jni.h>
#include <vulkan/vulkan.h>
#include <rex/platform.h>

#include <filesystem>
#include <string>
#include <vector>

static_assert(REX_PLATFORM_ANDROID && REX_PLATFORM_LINUX && REX_ARCH_ARM64,
              "The native app must target ReXGlue Android ARM64.");

namespace {
constexpr char kTag[] = "DMC";

void Log(int priority, const char* tag, const std::string& message) {
  __android_log_write(priority, tag, message.c_str());
}

std::string VulkanStatus() {
  uint32_t api_version = VK_API_VERSION_1_0;
  const auto enumerate_instance_version = reinterpret_cast<PFN_vkEnumerateInstanceVersion>(
      vkGetInstanceProcAddr(VK_NULL_HANDLE, "vkEnumerateInstanceVersion"));
  if (enumerate_instance_version != nullptr) {
    const VkResult version_result = enumerate_instance_version(&api_version);
    if (version_result != VK_SUCCESS) {
      Log(ANDROID_LOG_WARN, "DMC-VULKAN", "Vulkan version query failed; using 1.0: " +
          std::to_string(version_result));
      api_version = VK_API_VERSION_1_0;
    }
  }
  Log(ANDROID_LOG_INFO, "DMC-VULKAN", "Loader API version: " + std::to_string(api_version));

  VkApplicationInfo app_info{};
  app_info.sType = VK_STRUCTURE_TYPE_APPLICATION_INFO;
  app_info.pApplicationName = "DmC Devil May Cry";
  app_info.applicationVersion = VK_MAKE_API_VERSION(0, 0, 1, 0);
  app_info.pEngineName = "dmc-android";
  app_info.engineVersion = VK_MAKE_API_VERSION(0, 0, 1, 0);
  app_info.apiVersion = VK_API_VERSION_1_0;

  VkInstanceCreateInfo create_info{};
  create_info.sType = VK_STRUCTURE_TYPE_INSTANCE_CREATE_INFO;
  create_info.pApplicationInfo = &app_info;
  VkInstance instance = VK_NULL_HANDLE;
  const VkResult create_result = vkCreateInstance(&create_info, nullptr, &instance);
  if (create_result != VK_SUCCESS) {
    Log(ANDROID_LOG_ERROR, "DMC-VULKAN", "vkCreateInstance failed: " +
        std::to_string(create_result));
    return "Vulkan instance creation failed (" + std::to_string(create_result) + ")";
  }

  uint32_t count = 0;
  VkResult result = vkEnumeratePhysicalDevices(instance, &count, nullptr);
  if (result != VK_SUCCESS || count == 0) {
    Log(ANDROID_LOG_WARN, "DMC-VULKAN", "No Vulkan physical devices found; result=" +
        std::to_string(result));
    vkDestroyInstance(instance, nullptr);
    return "Vulkan loader ready; no GPU device reported";
  }

  std::vector<VkPhysicalDevice> devices(count);
  result = vkEnumeratePhysicalDevices(instance, &count, devices.data());
  std::string device_name = "unknown";
  if (result == VK_SUCCESS && count != 0) {
    VkPhysicalDeviceProperties properties{};
    vkGetPhysicalDeviceProperties(devices.front(), &properties);
    device_name = properties.deviceName;
    Log(ANDROID_LOG_INFO, "DMC-VULKAN", "Vulkan device: " + device_name);
  }
  vkDestroyInstance(instance, nullptr);
  return "Vulkan ready: " + device_name;
}
}  // namespace

extern "C" JNIEXPORT jstring JNICALL
Java_com_capcom_dmc_MainActivity_nativeInitialize(JNIEnv* env, jclass, jstring files_dir) {
  Log(ANDROID_LOG_INFO, kTag, "Android ARM64 native bootstrap starting");
  const char* path = env->GetStringUTFChars(files_dir, nullptr);
  if (path == nullptr) {
    Log(ANDROID_LOG_ERROR, kTag, "Could not read app-private files directory");
    return env->NewStringUTF("Startup failed: app storage path unavailable");
  }

  std::string storage_path(path);
  env->ReleaseStringUTFChars(files_dir, path);
  const std::filesystem::path root = std::filesystem::path(storage_path) / "dmc";
  std::error_code error;
  std::filesystem::create_directories(root / "game_root", error);
  if (!error) {
    std::filesystem::create_directories(root / "cache", error);
  }
  if (error) {
    Log(ANDROID_LOG_ERROR, "DMC-MEM", "App-private directory setup failed: " + error.message());
    return env->NewStringUTF("Startup failed: private app storage could not be initialized");
  }
  Log(ANDROID_LOG_INFO, "DMC-MEM", "Private game/cache directories ready: " + root.string());
  Log(ANDROID_LOG_INFO, "DMC-REX", "ReXGlue runtime integration ready");
  const std::string status = VulkanStatus();
  Log(ANDROID_LOG_INFO, kTag, "Native bootstrap complete");
  return env->NewStringUTF(status.c_str());
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_capcom_dmc_MainActivity_nativeSurfaceReady(JNIEnv* env, jclass, jobject surface) {
  Log(ANDROID_LOG_INFO, "DMC-VULKAN", "Android surface created; checking Vulkan presentation support");
  const char* extensions[] = {VK_KHR_SURFACE_EXTENSION_NAME, VK_KHR_ANDROID_SURFACE_EXTENSION_NAME};
  VkApplicationInfo app_info{};
  app_info.sType = VK_STRUCTURE_TYPE_APPLICATION_INFO;
  app_info.pApplicationName = "DmC Devil May Cry";
  app_info.apiVersion = VK_API_VERSION_1_0;
  VkInstanceCreateInfo instance_info{};
  instance_info.sType = VK_STRUCTURE_TYPE_INSTANCE_CREATE_INFO;
  instance_info.pApplicationInfo = &app_info;
  instance_info.enabledExtensionCount = 2;
  instance_info.ppEnabledExtensionNames = extensions;

  VkInstance instance = VK_NULL_HANDLE;
  VkResult result = vkCreateInstance(&instance_info, nullptr, &instance);
  if (result != VK_SUCCESS) {
    Log(ANDROID_LOG_ERROR, "DMC-VULKAN", "Android Vulkan instance/extensions failed: " + std::to_string(result));
    return env->NewStringUTF("Vulkan Android surface extensions unavailable");
  }

  ANativeWindow* window = ANativeWindow_fromSurface(env, surface);
  if (window == nullptr) {
    vkDestroyInstance(instance, nullptr);
    Log(ANDROID_LOG_ERROR, "DMC-VULKAN", "ANativeWindow_fromSurface returned null");
    return env->NewStringUTF("Android window unavailable");
  }

  const auto create_surface = reinterpret_cast<PFN_vkCreateAndroidSurfaceKHR>(
      vkGetInstanceProcAddr(instance, "vkCreateAndroidSurfaceKHR"));
  const auto destroy_surface = reinterpret_cast<PFN_vkDestroySurfaceKHR>(
      vkGetInstanceProcAddr(instance, "vkDestroySurfaceKHR"));
  const auto get_present_support = reinterpret_cast<PFN_vkGetPhysicalDeviceSurfaceSupportKHR>(
      vkGetInstanceProcAddr(instance, "vkGetPhysicalDeviceSurfaceSupportKHR"));
  const auto get_surface_formats = reinterpret_cast<PFN_vkGetPhysicalDeviceSurfaceFormatsKHR>(
      vkGetInstanceProcAddr(instance, "vkGetPhysicalDeviceSurfaceFormatsKHR"));
  VkSurfaceKHR vk_surface = VK_NULL_HANDLE;
  VkAndroidSurfaceCreateInfoKHR surface_info{};
  surface_info.sType = VK_STRUCTURE_TYPE_ANDROID_SURFACE_CREATE_INFO_KHR;
  surface_info.window = window;
  if (create_surface == nullptr || destroy_surface == nullptr || get_present_support == nullptr ||
      get_surface_formats == nullptr || create_surface(instance, &surface_info, nullptr, &vk_surface) != VK_SUCCESS) {
    ANativeWindow_release(window);
    vkDestroyInstance(instance, nullptr);
    Log(ANDROID_LOG_ERROR, "DMC-VULKAN", "Could not create VkSurfaceKHR for Android window");
    return env->NewStringUTF("Could not create Vulkan Android surface");
  }

  uint32_t device_count = 0;
  result = vkEnumeratePhysicalDevices(instance, &device_count, nullptr);
  bool present_supported = false;
  std::string device_name = "no Vulkan device";
  if (result == VK_SUCCESS && device_count > 0) {
    std::vector<VkPhysicalDevice> devices(device_count);
    result = vkEnumeratePhysicalDevices(instance, &device_count, devices.data());
    if (result == VK_SUCCESS) {
      for (VkPhysicalDevice device : devices) {
        VkPhysicalDeviceProperties properties{};
        vkGetPhysicalDeviceProperties(device, &properties);
        uint32_t family_count = 0;
        vkGetPhysicalDeviceQueueFamilyProperties(device, &family_count, nullptr);
        std::vector<VkQueueFamilyProperties> families(family_count);
        vkGetPhysicalDeviceQueueFamilyProperties(device, &family_count, families.data());
        for (uint32_t family = 0; family < family_count; ++family) {
          VkBool32 supported = VK_FALSE;
          if ((families[family].queueFlags & VK_QUEUE_GRAPHICS_BIT) &&
              get_present_support(device, family, vk_surface, &supported) == VK_SUCCESS && supported) {
            uint32_t format_count = 0;
            if (get_surface_formats(device, vk_surface, &format_count, nullptr) == VK_SUCCESS && format_count > 0) {
              present_supported = true;
              device_name = properties.deviceName;
              break;
            }
          }
        }
        if (present_supported) break;
      }
    }
  }

  destroy_surface(instance, vk_surface, nullptr);
  ANativeWindow_release(window);
  vkDestroyInstance(instance, nullptr);
  if (!present_supported) {
    Log(ANDROID_LOG_ERROR, "DMC-VULKAN", "No graphics queue supports presentation to this surface");
    return env->NewStringUTF("Vulkan device cannot present to the Android surface");
  }
  Log(ANDROID_LOG_INFO, "DMC-VULKAN", "Android surface presentation supported by " + device_name);
  return env->NewStringUTF(("Vulkan surface ready: " + device_name).c_str());
}
