cmake_minimum_required(VERSION 3.22.1)
project(dmc_android LANGUAGES C CXX)

set(CMAKE_CXX_STANDARD 23)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
add_compile_options("-fexperimental-library")

# ReXGlue SDK configuration
get_filename_component(DMC_REXSDK_DIR "${CMAKE_CURRENT_LIST_DIR}/../../../../../sdk" ABSOLUTE)
set(REXSDK_DIR "${DMC_REXSDK_DIR}" CACHE PATH "ReXGlue SDK source tree")
set(REXGLUE_USE_VULKAN ON CACHE BOOL "Build ReXGlue's Vulkan backend" FORCE)
set(REXGLUE_USE_D3D12 OFF CACHE BOOL "Android uses Vulkan" FORCE)
set(REXGLUE_ENABLE_TRACY OFF CACHE BOOL "Disable Tracy on Android" FORCE)
set(REXGLUE_BUILD_TESTS OFF CACHE BOOL "Disable tests" FORCE)
set(REXGLUE_VERSION_OVERRIDE "0.10.0-dev.android" CACHE STRING "ReXGlue SDK version for Android")
set(REXGLUE_RECOMP_DEBUG_INFO "none" CACHE STRING "Omit debug tables for the large generated guest code")
set(SDL_SHARED ON CACHE BOOL "SDL shared library loaded by SDLActivity" FORCE)
set(SDL_STATIC OFF CACHE BOOL "Use SDL's shared Android library" FORCE)

# Add the recompiled DMC game project (located at recomp_project/)
get_filename_component(DMC_APP_DIR "${CMAKE_CURRENT_LIST_DIR}/../../../../../recomp_project" ABSOLUTE)
add_subdirectory("${DMC_APP_DIR}" "dmc-app")

if(TARGET SDL3-shared)
    set_target_properties(SDL3-shared PROPERTIES
        OUTPUT_NAME "SDL3"
        DEBUG_POSTFIX ""
        RELEASE_POSTFIX ""
        RELWITHDEBINFO_POSTFIX ""
        MINSIZEREL_POSTFIX ""
    )
endif()

if(TARGET dmc)
    target_sources(dmc PRIVATE
        "${CMAKE_CURRENT_LIST_DIR}/android_rendimiento.cpp"
    )
    # 16KB max page size for modern Android compatibility (Android 15+)
    target_link_options(dmc PRIVATE "-Wl,-z,max-page-size=16384")
endif()

# AdrenoTools and Custom Vulkan GPU Driver support
add_subdirectory("${DMC_REXSDK_DIR}/thirdparty/libadrenotools" "libadrenotools")

add_library(rex_android_gpu_driver STATIC
    "${DMC_REXSDK_DIR}/src/ui/vulkan/android_gpu_driver.cpp"
)
target_include_directories(rex_android_gpu_driver PUBLIC
    "${DMC_REXSDK_DIR}/include"
    "${DMC_REXSDK_DIR}/thirdparty/libadrenotools/include"
)
target_link_libraries(rex_android_gpu_driver PUBLIC adrenotools dl log)

if(TARGET rexruntime)
    target_link_libraries(rexruntime PRIVATE rex_android_gpu_driver dl log)
    target_link_options(rexruntime PRIVATE "-Wl,-z,max-page-size=16384")
endif()

# Diagnostics probe library for GPU and Vulkan inspection
add_library(dmc_diagnostics SHARED "${CMAKE_CURRENT_LIST_DIR}/android_diagnostico.cpp")
target_include_directories(dmc_diagnostics PRIVATE
    "${DMC_REXSDK_DIR}/thirdparty/vulkan-headers/include"
    "${DMC_REXSDK_DIR}/include"
    "${DMC_REXSDK_DIR}/thirdparty/libadrenotools/include"
)
target_compile_options(dmc_diagnostics PRIVATE -march=armv8-a)
target_link_libraries(dmc_diagnostics PRIVATE rex_android_gpu_driver dl log)
target_link_options(dmc_diagnostics PRIVATE "-Wl,-z,max-page-size=16384")

