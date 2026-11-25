cmake_minimum_required(VERSION 3.22)

project(GAME)

# --- Dependencies from vcpkg (arm64-android): libpng, libjpeg-turbo, libavif,
#     openal-soft, libmad, libflac, minizip (+ zlib, ogg). ---
set(VCPKG_ARM64 "/Users/joelsaleem/Documents/GitHub/vcpkg/installed/arm64-android")
list(PREPEND CMAKE_PREFIX_PATH "${VCPKG_ARM64}")
# Let find_package/find_library search the vcpkg prefix even under the NDK sysroot.
set(CMAKE_FIND_ROOT_PATH_MODE_PACKAGE BOTH)
set(CMAKE_FIND_ROOT_PATH_MODE_LIBRARY BOTH)
set(CMAKE_FIND_ROOT_PATH_MODE_INCLUDE BOTH)
# pkg-config (built from source) + vcpkg .pc files, for ES's flac/minizip lookups.
set(PKG_CONFIG_EXECUTABLE "/Users/joelsaleem/Documents/GitHub/pkgtools/bin/pkg-config" CACHE FILEPATH "" FORCE)
set(ENV{PKG_CONFIG_PATH} "${VCPKG_ARM64}/lib/pkgconfig")
set(ENV{PKG_CONFIG_LIBDIR} "${VCPKG_ARM64}/lib/pkgconfig")

# --- SDL2 from source (version-matched to the Java glue). Defines SDL2::SDL2 +
#     SDL2::SDL2main, which the Endless Sky CMake reuses (see its NOT TARGET guard). ---
add_subdirectory(SDL)

# Header-only libuuid shim (the Android NDK has no uuid/uuid.h).
include_directories(BEFORE "${CMAKE_CURRENT_SOURCE_DIR}/compat")

# --- Endless Sky engine, built for OpenGL ES. add_subdirectory on the repo root
#     builds EndlessSkyLib and the Android "main" shared library (libmain.so). ---
set(ES_GLES ON CACHE BOOL "" FORCE)
set(ES_USE_SDL3 OFF CACHE BOOL "" FORCE)
set(ES_USE_VCPKG OFF CACHE BOOL "" FORCE)
set(ES_USE_SYSTEM_LIBRARIES OFF CACHE BOOL "" FORCE)
set(BUILD_TESTING OFF CACHE BOOL "" FORCE)
add_subdirectory("${CMAKE_CURRENT_SOURCE_DIR}/../../.." endless_sky_build)
