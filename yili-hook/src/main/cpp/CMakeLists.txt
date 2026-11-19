cmake_minimum_required(VERSION 3.28)
project(yilihook C CXX)

set(CMAKE_CXX_STANDARD 23)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# LSPosed's libcxx is built without exceptions and RTTI; everything linking it must match.
string(APPEND CMAKE_CXX_FLAGS " -fno-exceptions -fno-rtti")

find_package(cxx REQUIRED CONFIG)
find_package(dobby REQUIRED CONFIG)

# LSPlant from the git submodule; builds lsplant_static (+ DexBuilder) with C++23 modules.
add_subdirectory(${CMAKE_CURRENT_SOURCE_DIR}/../../../lsplant/lsplant/src/main/jni lsplant)

add_library(yili_xz STATIC
        xz/xz_crc32.c
        xz/xz_crc64.c
        xz/xz_dec_lzma2.c
        xz/xz_dec_stream.c)
target_compile_definitions(yili_xz PUBLIC XZ_USE_CRC64 XZ_DEC_ANY_CHECK)
target_include_directories(yili_xz PUBLIC xz)
set_target_properties(yili_xz PROPERTIES POSITION_INDEPENDENT_CODE ON)

add_library(yilihook SHARED
        art_symbols.cpp
        yili_hook.cpp)
target_compile_options(yilihook PRIVATE -fvisibility=hidden -Wall)
target_link_options(yilihook PRIVATE -Wl,--exclude-libs,ALL -Wl,--gc-sections)
target_link_libraries(yilihook PRIVATE lsplant_static dobby::dobby yili_xz cxx::cxx log)
