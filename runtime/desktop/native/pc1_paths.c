/* Windows ANSI boundary for reviewed PC1 path producers. Read-only queries;
 * no original executable code is mapped or executed. */
#define WIN32_LEAN_AND_MEAN
#include <windows.h>
#include <jni.h>
#include <stdlib.h>
#include <stdint.h>
static void fail(JNIEnv *env,const char *message){jclass cls=(*env)->FindClass(env,"java/io/IOException");if(cls)(*env)->ThrowNew(env,cls,message);}
static char *bytes(JNIEnv *env,jbyteArray input,jsize *length){char *value;if(!input){fail(env,"PC1 ANSI byte source missing");return NULL;}*length=(*env)->GetArrayLength(env,input);if(*length>1024*1024){fail(env,"PC1 ANSI byte source host bound");return NULL;}value=(char *)calloc((size_t)*length+1,1);if(!value){fail(env,"PC1 ANSI byte allocation");return NULL;}(*env)->GetByteArrayRegion(env,input,0,*length,(jbyte *)value);if((*env)->ExceptionCheck(env)){free(value);return NULL;}return value;}
JNIEXPORT jint JNICALL Java_org_circus_core_Pc1WindowsPaths_codePageNative(JNIEnv *env,jclass type){(void)env;(void)type;return (jint)GetACP();}
JNIEXPORT jint JNICALL Java_org_circus_core_Pc1WindowsPaths_attributesNative(JNIEnv *env,jclass type,jbyteArray input){jsize length;char *path=bytes(env,input,&length);DWORD result;(void)type;if(!path)return -1;result=GetFileAttributesA(path);free(path);return (jint)result;}
JNIEXPORT jbyteArray JNICALL Java_org_circus_core_Pc1WindowsPaths_encodeNative(JNIEnv *env,jclass type,jstring input){
    const jchar *wide;jsize length;BOOL used=FALSE;int size,written;char *data;jbyteArray result;(void)type;if(!input){fail(env,"PC1 ANSI string missing");return NULL;}length=(*env)->GetStringLength(env,input);wide=(*env)->GetStringChars(env,input,NULL);if(!wide)return NULL;
    size=length?WideCharToMultiByte(CP_ACP,WC_NO_BEST_FIT_CHARS,(LPCWCH)wide,length,NULL,0,NULL,&used):0;if(length&&(!size||used)){(*env)->ReleaseStringChars(env,input,wide);fail(env,"PC1 path not exactly encodable in WindowsACP");return NULL;}
    data=(char *)malloc((size_t)size+1);if(!data){(*env)->ReleaseStringChars(env,input,wide);fail(env,"PC1 ANSI encode allocation");return NULL;}used=FALSE;written=length?WideCharToMultiByte(CP_ACP,WC_NO_BEST_FIT_CHARS,(LPCWCH)wide,length,data,size,NULL,&used):0;(*env)->ReleaseStringChars(env,input,wide);
    if(written!=size||used){free(data);fail(env,"PC1 ANSI encoding failed");return NULL;}result=(*env)->NewByteArray(env,size);if(result&&size)(*env)->SetByteArrayRegion(env,result,0,size,(const jbyte *)data);free(data);return result;
}
JNIEXPORT jstring JNICALL Java_org_circus_core_Pc1WindowsPaths_decodeNative(JNIEnv *env,jclass type,jbyteArray input){
    jsize length;char *data=bytes(env,input,&length);WCHAR *wide;int size,written;jstring result;(void)type;if(!data)return NULL;size=length?MultiByteToWideChar(CP_ACP,MB_ERR_INVALID_CHARS,data,length,NULL,0):0;if(length&&!size){free(data);fail(env,"PC1 raw path is invalid in WindowsACP");return NULL;}
    wide=(WCHAR *)malloc(((size_t)size+1)*sizeof(WCHAR));if(!wide){free(data);fail(env,"PC1 ANSI decode allocation");return NULL;}written=length?MultiByteToWideChar(CP_ACP,MB_ERR_INVALID_CHARS,data,length,wide,size):0;free(data);if(written!=size){free(wide);fail(env,"PC1 ANSI decoding failed");return NULL;}result=(*env)->NewString(env,(const jchar *)wide,size);free(wide);return result;
}
JNIEXPORT jbyteArray JNICALL Java_org_circus_core_Pc1WindowsPaths_directoryNative(JNIEnv *env,jclass type){
    DWORD needed=GetCurrentDirectoryA(0,NULL),size;char *data;jbyteArray result;(void)type;if(!needed||needed>1024*1024){fail(env,"PC1 GetCurrentDirectoryA length failed");return NULL;}data=(char *)malloc(needed);if(!data){fail(env,"PC1 ANSI directory allocation");return NULL;}size=GetCurrentDirectoryA(needed,data);if(!size||size>=needed){free(data);fail(env,"PC1 GetCurrentDirectoryA changed or failed");return NULL;}result=(*env)->NewByteArray(env,(jsize)size);if(result)(*env)->SetByteArrayRegion(env,result,0,(jsize)size,(const jbyte *)data);free(data);return result;
}
