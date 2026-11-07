#define WIN32_LEAN_AND_MEAN
#include <windows.h>
#include <jni.h>
#include <stdint.h>

/* Self-authored current-process service. Never runs or injects the game EXE.
 * Java permits only sealed live lease addresses/counts to reach these leaves. */
JNIEXPORT jlong JNICALL Java_org_circus_core_Pc1WindowsImageHeap_processHeapNative(JNIEnv *env,jclass type){(void)env;(void)type;return(jlong)(intptr_t)GetProcessHeap();}
JNIEXPORT jlong JNICALL Java_org_circus_core_Pc1WindowsImageHeap_allocateNative(JNIEnv *env,jclass type,jlong heap,jint count){(void)env;(void)type;return(jlong)(intptr_t)HeapAlloc((HANDLE)(intptr_t)heap,0,(SIZE_T)(uint32_t)count);}
JNIEXPORT jobject JNICALL Java_org_circus_core_Pc1WindowsImageHeap_directNative(JNIEnv *env,jclass type,jlong address,jint length){(void)type;return(*env)->NewDirectByteBuffer(env,(void *)(intptr_t)address,(jlong)length);}
JNIEXPORT jboolean JNICALL Java_org_circus_core_Pc1WindowsImageHeap_freeNative(JNIEnv *env,jclass type,jlong heap,jlong address){(void)env;(void)type;return HeapFree((HANDLE)(intptr_t)heap,0,(void *)(intptr_t)address)?JNI_TRUE:JNI_FALSE;}
JNIEXPORT jint JNICALL Java_org_circus_core_Pc1WindowsImageHeap_readNative(JNIEnv *env,jclass type,jlong address,jint offset){const volatile unsigned char *bytes=(const volatile unsigned char *)(intptr_t)address;(void)env;(void)type;return(jint)bytes[offset];}
JNIEXPORT void JNICALL Java_org_circus_core_Pc1WindowsImageHeap_writeNative(JNIEnv *env,jclass type,jlong address,jint offset,jint value){volatile unsigned char *bytes=(volatile unsigned char *)(intptr_t)address;(void)env;(void)type;bytes[offset]=(unsigned char)value;}
