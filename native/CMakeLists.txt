cmake_minimum_required(VERSION 3.8 FATAL_ERROR)
project(JSitter)

#set(CMAKE_BUILD_TYPE Release)

find_package(JNI REQUIRED)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_STANDARD 17)

if (MSVC)
    SET(CMAKE_CXX_FLAGS_DEBUG "/Od")
    SET(CMAKE_CXX_FLAGS_RELEASE "/O2")
else ()
    SET(CMAKE_CXX_COMPILER "/usr/bin/clang++")
    SET(CMAKE_CXX_FLAGS "-Wall -std=c++17 -stdlib=libc++")
    SET(CMAKE_CXX_FLAGS_DEBUG "-g")
    SET(CMAKE_CXX_FLAGS_RELEASE "-mavx -mavx2 -Ofast -DNDEBUG")
    SET(CLANG_DEFAULT_CXX_STDLIB "libc++")

    set(CMAKE_EXE_LINKER_FLAGS "${CMAKE_EXE_LINKER_FLAGS} -stdlib=libc++ -lc++abi")
endif ()

include_directories(src/)
include_directories(tree-sitter/lib/src tree-sitter/lib/include tree-sitter/lib/utf8proc)
include_directories(${JNI_INCLUDE_DIRS})

add_library(jsitter SHARED src/java_api.cpp tree-sitter/lib/src/lib.c)


