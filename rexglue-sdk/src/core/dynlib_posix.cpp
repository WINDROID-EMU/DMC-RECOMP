#include <rex/platform.h>
#include <rex/platform/dynlib.h>

static_assert(REX_PLATFORM_LINUX || REX_PLATFORM_MAC, "This file is POSIX-only");

#include <dlfcn.h>

namespace rex::platform {

DynamicLibrary::~DynamicLibrary() {
  Close();
}

DynamicLibrary::DynamicLibrary(DynamicLibrary&& other) noexcept : handle_(other.handle_) {
  other.handle_ = nullptr;
}

DynamicLibrary& DynamicLibrary::operator=(DynamicLibrary&& other) noexcept {
  if (this != &other) {
    Close();
    handle_ = other.handle_;
    other.handle_ = nullptr;
  }
  return *this;
}

bool DynamicLibrary::Load(const std::filesystem::path& path, SymbolResolution mode) {
  Close();
  int flags = (mode == SymbolResolution::kImmediate) ? RTLD_NOW : RTLD_LAZY;
  handle_ = dlopen(path.c_str(), flags);
  if (!handle_ && !path.is_absolute()) {
    std::string base = path.string();
    std::string lib_so = "lib" + base + ".so";
    handle_ = dlopen(lib_so.c_str(), flags);
    if (!handle_) {
      std::string dot_lib_so = "./lib" + base + ".so";
      handle_ = dlopen(dot_lib_so.c_str(), flags);
    }
    if (!handle_) {
      std::string dot_base = "./" + base;
      handle_ = dlopen(dot_base.c_str(), flags);
    }
    if (!handle_) {
      std::string dot_so = "./" + base + ".so";
      handle_ = dlopen(dot_so.c_str(), flags);
    }
    if (!handle_) {
      std::error_code ec;
      auto exe_dir = std::filesystem::read_symlink("/proc/self/exe", ec).parent_path();
      if (!ec && !exe_dir.empty()) {
        auto p1 = exe_dir / lib_so;
        handle_ = dlopen(p1.c_str(), flags);
        if (!handle_) {
          auto p2 = exe_dir / (base + ".so");
          handle_ = dlopen(p2.c_str(), flags);
        }
        if (!handle_) {
          auto p3 = exe_dir / base;
          handle_ = dlopen(p3.c_str(), flags);
        }
      }
    }
  }
  return handle_ != nullptr;
}

void DynamicLibrary::Close() {
  if (handle_) {
    dlclose(handle_);
    handle_ = nullptr;
  }
}

void* DynamicLibrary::GetRawSymbol(const char* name) const {
  if (!handle_)
    return nullptr;
  return dlsym(handle_, name);
}

}  // namespace rex::platform
