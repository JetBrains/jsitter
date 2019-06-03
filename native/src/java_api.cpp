#ifndef trie_java_api_h
#define trie_java_api_h
#include <jni.h>
#include <cstdint>



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

