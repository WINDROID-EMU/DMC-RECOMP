cmake_minimum_required(VERSION 3.22.1)
project(dmc_android LANGUAGES C CXX)

# Diagnostics probe library for GPU and Vulkan inspection
add_library(dmc_diagnostics SHARED android_diagnostico.cpp)
target_include_directories(dmc_diagnostics PRIVATE
    "${CMAKE_CURRENT_LIST_DIR}/../../../../../../sdk/thirdparty/vulkan-headers/include"
    "${CMAKE_CURRENT_LIST_DIR}/../../../../../../sdk/include")
target_compile_options(dmc_diagnostics PRIVATE -march=armv8-a)
target_link_libraries(dmc_diagnostics PRIVATE rex_android_gpu_driver dl)
target_link_options(dmc_diagnostics PRIVATE "-Wl,-z,max-page-size=16384")

# ReXGlue SDK configuration
get_filename_component(DMC_REXSDK_DIR "${CMAKE_CURRENT_LIST_DIR}/../../../../../../sdk" ABSOLUTE)
set(REXSDK_DIR "${DMC_REXSDK_DIR}" CACHE PATH "ReXGlue SDK source tree")
set(REXGLUE_USE_VULKAN ON CACHE BOOL "Build ReXGlue's Vulkan backend" FORCE)
set(REXGLUE_USE_D3D12 OFF CACHE BOOL "Android uses Vulkan" FORCE)
set(REXGLUE_ENABLE_TRACY OFF CACHE BOOL "Disable Tracy on Android" FORCE)
set(REXGLUE_VERSION_OVERRIDE "0.10.0-dev.android" CACHE STRING "ReXGlue SDK version for Android")
set(REXGLUE_RECOMP_DEBUG_INFO "none" CACHE STRING "Omit debug tables for the large generated guest code")

# Add the recompiled DMC game project (located at recompilado/)
add_subdirectory("${CMAKE_CURRENT_LIST_DIR}/../../../../.." "dmc-app")
target_sources(dmc PRIVATE
    "${CMAKE_CURRENT_LIST_DIR}/android_rendimiento.cpp"
)

# 16KB max page size for modern Android compatibility (Android 15+)
target_link_options(dmc PRIVATE "-Wl,-z,max-page-size=16384")

# Exclude unnecessary static symbols
foreach(target_name dmc rexruntime)
    if(TARGET ${target_name})
        target_link_options(${target_name} PRIVATE
            "LINKER:--exclude-libs,liblibavcodec.a:liblibavutil.a")
    endif()
endforeach()

# ThinLTO optimization for Release builds
option(DMC_ANDROID_LTO "ThinLTO for DMC on Android Release" ON)
if(DMC_ANDROID_LTO AND CMAKE_BUILD_TYPE STREQUAL "Release")
    foreach(target_name dmc_recomp dmc)
        if(TARGET ${target_name})
            target_compile_options(${target_name} PRIVATE -flto=thin)
        endif()
    endforeach()
    cmake_host_system_information(RESULT DMC_NUCLEOS QUERY NUMBER_OF_LOGICAL_CORES)
    target_link_options(dmc PRIVATE -flto=thin "-Wl,--thinlto-jobs=${DMC_NUCLEOS}"
                        "-Wl,--thinlto-cache-dir=${CMAKE_BINARY_DIR}/thinlto-cache")
    target_link_options(dmc PRIVATE -Wl,--gc-sections -Wl,--icf=safe)
endif()
