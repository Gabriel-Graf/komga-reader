cmake_minimum_required(VERSION 3.18.1)

project("cr3bridge")

# ---------------------------------------------------------------------------
# Committed crengine-ng prefix (Phase 1a+1b output), arm64-v8a only.
# Relative to this CMakeLists: ../../../native/prefix/aarch64-linux-android
# ---------------------------------------------------------------------------
get_filename_component(CRENGINE_PREFIX
        "${CMAKE_CURRENT_SOURCE_DIR}/../../../native/prefix/aarch64-linux-android"
        ABSOLUTE)
if(NOT EXISTS "${CRENGINE_PREFIX}")
    message(FATAL_ERROR "crengine-ng prefix missing: ${CRENGINE_PREFIX}")
endif()

# Let find_package locate the committed config-package inside the NDK sysroot
# find-root sandbox: search the prefix for packages, but the host for nothing.
list(APPEND CMAKE_FIND_ROOT_PATH "${CRENGINE_PREFIX}")
list(APPEND CMAKE_PREFIX_PATH "${CRENGINE_PREFIX}")
set(CMAKE_FIND_ROOT_PATH_MODE_PACKAGE BOTH)
set(CMAKE_FIND_ROOT_PATH_MODE_INCLUDE BOTH)
set(CMAKE_FIND_ROOT_PATH_MODE_LIBRARY BOTH)

# crengine-ng's exported target references the WebP sub-libs by their *bare*
# names (cpufeatures-webp, webpdecoder, webp, webpdemux, webpmux) inside
# $<LINK_ONLY:...> — it was built against a non-namespaced WebP. The committed
# WebPConfig only exports WebP::*, so we declare the bare imported targets here,
# pointing at the same committed static libs, to satisfy those references.
foreach(_webp cpufeatures-webp webpdecoder webp webpdemux webpmux sharpyuv)
    if(NOT TARGET ${_webp})
        add_library(${_webp} STATIC IMPORTED)
        set_target_properties(${_webp} PROPERTIES
                IMPORTED_LOCATION "${CRENGINE_PREFIX}/lib/lib${_webp}.a")
    endif()
endforeach()

find_package(crengine-ng CONFIG REQUIRED)

find_library(log-lib log)
find_library(jnigraphics-lib jnigraphics)
find_library(android-lib android)
find_library(z-lib z)

add_library(cr3bridge SHARED
        cr3_bridge.cpp
        jnigraphicslib.cpp
        lvcolordrawbufex.cpp)

target_include_directories(cr3bridge PRIVATE "${CRENGINE_PREFIX}/include/crengine-ng")

target_link_libraries(cr3bridge
        crengine-ng::crengine-ng_static
        ${log-lib}
        ${jnigraphics-lib}
        ${android-lib}
        ${z-lib})
