/* Self-authored readonly ANSI CRT/allocation boundary, not original code. */
#define WIN32_LEAN_AND_MEAN
#define _CRT_SECURE_NO_WARNINGS
#include <windows.h>
#include <jni.h>
#include <stdint.h>
#include <stdlib.h>
#include <io.h>
#include <fcntl.h>

static void fail(JNIEnv *env,const char *message){jclass cls=(*env)->FindClass(env,"java/io/IOException");if(cls)(*env)->ThrowNew(env,cls,message);}
JNIEXPORT jint JNICALL Java_org_circus_core_Pc1WindowsImageIo_openNative(JNIEnv *env,jclass type,jbyteArray input){
    jsize length;char *path;int descriptor;(void)type;if(!input){fail(env,"PC1 readonly image path missing");return -1;}length=(*env)->GetArrayLength(env,input);if(length>1024*1024){fail(env,"PC1 readonly image path host bound");return -1;}
    path=(char *)malloc((size_t)length+1);if(!path){fail(env,"PC1 readonly image path allocation");return -1;}(*env)->GetByteArrayRegion(env,input,0,length,(jbyte *)path);if((*env)->ExceptionCheck(env)){free(path);return -1;}path[length]=0;descriptor=_open(path,_O_BINARY|_O_RDONLY);free(path);return descriptor;
}
JNIEXPORT jint JNICALL Java_org_circus_core_Pc1WindowsImageIo_readNative(JNIEnv *env,jclass type,jint descriptor,jbyteArray output,jint offset,jint length){
    jbyte *bytes;int count;(void)type;if(!output||offset<0||length<0||(int64_t)offset+length>(*env)->GetArrayLength(env,output)){fail(env,"PC1 readonly image read range");return -1;}bytes=(*env)->GetByteArrayElements(env,output,NULL);if(!bytes)return -1;count=_read(descriptor,bytes+offset,(unsigned int)length);(*env)->ReleaseByteArrayElements(env,output,bytes,0);return count;
}
JNIEXPORT jint JNICALL Java_org_circus_core_Pc1WindowsImageIo_closeNative(JNIEnv *env,jclass type,jint descriptor){(void)env;(void)type;return _close(descriptor);}
JNIEXPORT jlong JNICALL Java_org_circus_core_Pc1WindowsImageIo_allocateNative(JNIEnv *env,jclass type,jint length,jboolean image){void *pointer;(void)env;(void)type;if(length<1)return 0;pointer=image?HeapAlloc(GetProcessHeap(),0,(SIZE_T)length):malloc((size_t)length);return(jlong)(intptr_t)pointer;}
JNIEXPORT jbyteArray JNICALL Java_org_circus_core_Pc1WindowsImageIo_allocationBytesNative(JNIEnv *env,jclass type,jlong handle,jint length){jbyteArray output;(void)type;if(!handle||length<1){fail(env,"PC1 native image allocation missing");return NULL;}output=(*env)->NewByteArray(env,length);if(output)(*env)->SetByteArrayRegion(env,output,0,length,(const jbyte *)(intptr_t)handle);return output;}
JNIEXPORT void JNICALL Java_org_circus_core_Pc1WindowsImageIo_releaseNative(JNIEnv *env,jclass type,jlong handle,jboolean image,jbyteArray input){void *pointer=(void *)(intptr_t)handle;(void)type;if(!pointer){fail(env,"PC1 native image release missing");return;}if(input){jsize length=(*env)->GetArrayLength(env,input);(*env)->GetByteArrayRegion(env,input,0,length,(jbyte *)pointer);if((*env)->ExceptionCheck(env))return;}if(image){if(!HeapFree(GetProcessHeap(),0,pointer))fail(env,"PC1 HeapFree failed");}else free(pointer);}
