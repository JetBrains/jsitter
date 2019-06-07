#ifndef trie_java_api_h
#define trie_java_api_h
#include <jni.h>
#include <cstdint>
#include <tree_sitter/api.h>

struct Zipper {
    TSTreeCursor cursor;
    uint32_t start_byte;
    uint32_t end_byte;
    TSSymbol symbol;
};

#ifdef __cplusplus
extern "C" {
#endif

  JNIEXPORT jlong JNICALL Java_com_jetbrains_jsitter_foo
        (JNIEnv *, jclass);

#ifdef __cplusplus
}

jlong JNICALL Java_com_jetbrains_jsitter_foo(JNIEnv *, jclass) {
  printf("hello from JNI");
  return 12;
}

#endif


#endif

