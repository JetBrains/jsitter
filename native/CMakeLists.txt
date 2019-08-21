cmake_minimum_required(VERSION 3.8 FATAL_ERROR)
project(JSitter)

set(CMAKE_BUILD_TYPE Release)

find_package(JNI REQUIRED)

SET(CMAKE_C_COMPILER "/usr/bin/clang")
SET(CMAKE_C_FLAGS "-Wall -Ofast -g")

include_directories(src/)
include_directories(tree-sitter/lib/src tree-sitter/lib/include tree-sitter/lib/utf8proc)
include_directories(${JNI_INCLUDE_DIRS})

add_library(jsitter SHARED src/java_api.c tree-sitter/lib/src/lib.c)

add_library(tsgo SHARED grammars/tree-sitter-go/src/parser.c)

add_executable(jsitter-tests src/tests.c)
target_link_libraries(jsitter-tests jsitter tsgo)
