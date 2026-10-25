# dmc - ReXGlue Recompiled Project
#
# SDK boilerplate lives in generated/rexglue.cmake.

cmake_minimum_required(VERSION 3.25)
project(dmc LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 23)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

include(generated/rexglue.cmake)

# Sources
set(DMC_SOURCES
    src/main.cpp
)

if(ANDROID)
    add_library(dmc SHARED ${DMC_SOURCES})
    set_target_properties(dmc PROPERTIES OUTPUT_NAME main)
elseif(WIN32)
    add_executable(dmc WIN32 ${DMC_SOURCES})
else()
    add_executable(dmc ${DMC_SOURCES})
endif()

rexglue_setup_target(dmc GPU_PLUGINS xenos)
