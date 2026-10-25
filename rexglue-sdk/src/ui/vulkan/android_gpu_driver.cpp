#include <rex/ui/vulkan/android_gpu_driver.h>
#include <adrenotools/driver.h>
#include <dlfcn.h>

#include <string>

extern "C" void* rex_android_open_vulkan(const char* hooks, const char* temp, const char* directory, const char* library) {
  if (directory && *directory && library && *library) {
    std::string dir = directory;
    if (dir.back() != '/') {
      dir += '/';
    }
    return adrenotools_open_libvulkan(
        RTLD_NOW,
        ADRENOTOOLS_DRIVER_CUSTOM,
        temp && *temp ? temp : nullptr,
        hooks && *hooks ? hooks : nullptr,
        dir.c_str(),
        library,
        nullptr,
        nullptr);
  }
  return dlopen("libvulkan.so", RTLD_NOW | RTLD_LOCAL);
}
