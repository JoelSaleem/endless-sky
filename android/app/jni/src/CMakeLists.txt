cmake_minimum_required(VERSION 3.22)

project(main)

# Stub phase: prove Gradle -> NDK -> SDL -> APK -> emulator works before
# wiring the Endless Sky engine. Replaced in the engine task.
add_library(main SHARED stub_main.c)

target_link_libraries(main SDL2 SDL2main)
