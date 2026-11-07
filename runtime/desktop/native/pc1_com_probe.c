/*Self-authored, hidden-window public COM ABI probe. No game PE is loaded.
 *Not the production Audio backend or a replacement for its physical graph.*/
#define WIN32_LEAN_AND_MEAN
#define COBJMACROS
#define DIRECTSOUND_VERSION 0x0800
#include <windows.h>
#include <mmsystem.h>
#include <dsound.h>
#include <jni.h>
#include <stdint.h>
#include <stdlib.h>
#include <string.h>
#define PROBE_MAGIC 0x50434d50u
typedef struct Object {IUnknown *pointer;void *vtable,*functions[21];DWORD ownedRefs;int kind,count;} Object;
typedef struct Probe {DWORD magic,thread;HRESULT results[6];Object objects[4];} Probe;
typedef DWORD (STDMETHODCALLTYPE *Fn1)(void *);
typedef DWORD (STDMETHODCALLTYPE *Fn2)(void *,DWORD);
static void fail(JNIEnv *env,const char *message){jclass type=(*env)->FindClass(env,"java/io/IOException");if(type)(*env)->ThrowNew(env,type,message);}
static Probe *probe(JNIEnv *env,jlong value){Probe *p=(Probe *)(intptr_t)value;if(!p||p->magic!=PROBE_MAGIC||p->thread!=GetCurrentThreadId()){fail(env,"PC1 COM probe requires its creating-thread live owner");return NULL;}return p;}
static Object *object(JNIEnv *env,Probe *p,jint index){if(index<0||index>=4){fail(env,"PC1 COM probe object index");return NULL;}return &p->objects[index];}
static void remember(Object *out,IUnknown *pointer,int kind){void **table;out->pointer=pointer;out->kind=kind;out->count=kind?21:11;out->ownedRefs=pointer?1u:0u;if(pointer){memcpy(&table,pointer,sizeof(table));out->vtable=table;memcpy(out->functions,table,(size_t)out->count*sizeof(void *));}}
static void dispose(Probe *p){int index;for(index=3;index>=0;index--){Object *o=&p->objects[index];while(o->ownedRefs&&o->pointer){ULONG left=IUnknown_Release(o->pointer);o->ownedRefs--;if(left==0)o->pointer=NULL;}}p->magic=0;free(p);}
JNIEXPORT jlong JNICALL Java_org_circus_core_Pc1ComAbiAudit_openNative(JNIEnv *env,jclass type,jlong hwnd){Probe *p=(Probe *)calloc(1,sizeof(Probe));LPDIRECTSOUND device=NULL;LPDIRECTSOUNDBUFFER primary=NULL,one=NULL,two=NULL;DSBUFFERDESC descriptor;WAVEFORMATEX format;int n;(void)type;if(!p){fail(env,"PC1 COM probe allocation");return 0;}p->magic=PROBE_MAGIC;p->thread=GetCurrentThreadId();for(n=0;n<6;n++)p->results[n]=(HRESULT)0x7fffffff;
    p->results[0]=DirectSoundCreate(NULL,&device,NULL);remember(&p->objects[0],(IUnknown *)device,0);if(p->results[0]!=DS_OK)return(jlong)(intptr_t)p;
    p->results[1]=IDirectSound_SetCooperativeLevel(device,(HWND)(intptr_t)hwnd,2);memset(&descriptor,0,sizeof(descriptor));descriptor.dwSize=sizeof(descriptor);descriptor.dwFlags=0x4009;p->results[2]=IDirectSound_CreateSoundBuffer(device,&descriptor,&primary,NULL);remember(&p->objects[1],(IUnknown *)primary,1);
    memset(&format,0,sizeof(format));format.wFormatTag=1;format.nChannels=1;format.nSamplesPerSec=44100;format.nAvgBytesPerSec=88200;format.nBlockAlign=2;format.wBitsPerSample=16;if(primary)p->results[3]=IDirectSoundBuffer_SetFormat(primary,&format);
    descriptor.dwFlags=0x40ea;descriptor.dwBufferBytes=1024;descriptor.lpwfxFormat=&format;p->results[4]=IDirectSound_CreateSoundBuffer(device,&descriptor,&one,NULL);remember(&p->objects[2],(IUnknown *)one,1);p->results[5]=IDirectSound_CreateSoundBuffer(device,&descriptor,&two,NULL);remember(&p->objects[3],(IUnknown *)two,1);return(jlong)(intptr_t)p;
}
JNIEXPORT jintArray JNICALL Java_org_circus_core_Pc1ComAbiAudit_initializationNative(JNIEnv *env,jclass type,jlong handle){Probe *p=probe(env,handle);jintArray out;(void)type;if(!p)return NULL;out=(*env)->NewIntArray(env,6);if(out)(*env)->SetIntArrayRegion(env,out,0,6,(const jint *)p->results);return out;}
JNIEXPORT jlongArray JNICALL Java_org_circus_core_Pc1ComAbiAudit_infoNative(JNIEnv *env,jclass type,jlong handle,jint index){Probe *p=probe(env,handle);Object *o;HMODULE module=NULL;DWORD error=0;jlong values[5];jlongArray out;(void)type;if(!p)return NULL;o=object(env,p,index);if(!o)return NULL;if(o->vtable&&!GetModuleHandleExW(GET_MODULE_HANDLE_EX_FLAG_FROM_ADDRESS|GET_MODULE_HANDLE_EX_FLAG_UNCHANGED_REFCOUNT,(LPCWSTR)o->vtable,&module))error=GetLastError();values[0]=(jlong)(intptr_t)o->pointer;values[1]=(jlong)(intptr_t)o->vtable;values[2]=(jlong)(intptr_t)module;values[3]=o->count;values[4]=error;out=(*env)->NewLongArray(env,5);if(out)(*env)->SetLongArrayRegion(env,out,0,5,values);return out;}
JNIEXPORT jlongArray JNICALL Java_org_circus_core_Pc1ComAbiAudit_leafNative(JNIEnv *env,jclass type,jlong handle,jint index,jint slot){Probe *p=probe(env,handle);Object *o;HMODULE module=NULL;DWORD error=0;jlong values[3];jlongArray out;(void)type;if(!p)return NULL;o=object(env,p,index);if(!o)return NULL;if(slot<0||slot>=o->count||!o->functions[slot]){fail(env,"PC1 COM probe public leaf absent");return NULL;}if(!GetModuleHandleExW(GET_MODULE_HANDLE_EX_FLAG_FROM_ADDRESS|GET_MODULE_HANDLE_EX_FLAG_UNCHANGED_REFCOUNT,(LPCWSTR)o->functions[slot],&module))error=GetLastError();values[0]=(jlong)(intptr_t)o->functions[slot];values[1]=(jlong)(intptr_t)module;values[2]=error;out=(*env)->NewLongArray(env,3);if(out)(*env)->SetLongArrayRegion(env,out,0,3,values);return out;}
JNIEXPORT jstring JNICALL Java_org_circus_core_Pc1ComAbiAudit_modulePathNative(JNIEnv *env,jclass type,jlong handle,jint index,jint slot){Probe *p=probe(env,handle);Object *o;void *address;HMODULE module=NULL;WCHAR path[32768];DWORD count;(void)type;if(!p)return NULL;o=object(env,p,index);if(!o)return NULL;if(slot< -1||slot>=o->count){fail(env,"PC1 COM probe module slot");return NULL;}address=slot<0?o->vtable:o->functions[slot];if(!address||!GetModuleHandleExW(GET_MODULE_HANDLE_EX_FLAG_FROM_ADDRESS|GET_MODULE_HANDLE_EX_FLAG_UNCHANGED_REFCOUNT,(LPCWSTR)address,&module)){fail(env,"PC1 COM probe public ABI address has no known loaded module");return NULL;}count=GetModuleFileNameW(module,path,32768);if(count==0||count>=32768){fail(env,"PC1 COM probe loaded module filename unavailable");return NULL;}return(*env)->NewString(env,(const jchar *)path,(jsize)count);}
JNIEXPORT jint JNICALL Java_org_circus_core_Pc1ComAbiAudit_invokeNative(JNIEnv *env,jclass type,jlong handle,jint source,jint slot,jint target,jint argument){Probe *p=probe(env,handle);Object *from,*to;DWORD result;Fn1 one;Fn2 two;(void)type;if(!p)return 0;from=object(env,p,source);to=object(env,p,target);if(!from||!to)return 0;if(slot<0||slot>=from->count||!from->functions[slot]||!to->pointer){fail(env,"PC1 COM probe selected leaf/actual THIS lifetime absent");return 0;}
    if(from->vtable!=to->vtable&&(slot>=to->count||from->functions[slot]!=to->functions[slot])){fail(env,"PC1 COM probe selected function requires unverified cross-class private THIS layout");return 0;}
    if(slot==1||slot==2||from->kind&&(slot==18||slot==20)||!from->kind&&slot==7){memcpy(&one,&from->functions[slot],sizeof(one));result=one(to->pointer);if(slot==1)to->ownedRefs++;else if(slot==2){if(to->ownedRefs)to->ownedRefs--;if(result==0)to->pointer=NULL;}return(jint)result;}
    if(from->kind&&(slot==13||slot==15||slot==16||slot==17)||!from->kind&&slot==9){memcpy(&two,&from->functions[slot],sizeof(two));return(jint)two(to->pointer,(DWORD)argument);}
    fail(env,"PC1 COM probe requires an actual pointer/stack marshaller for this leaf; no fallback");return 0;
}
JNIEXPORT jintArray JNICALL Java_org_circus_core_Pc1ComAbiAudit_volumeNative(JNIEnv *env,jclass type,jlong handle,jint index){Probe *p=probe(env,handle);Object *o;LONG volume=0x7fffffff;jint values[2];jintArray out;(void)type;if(!p)return NULL;o=object(env,p,index);if(!o)return NULL;if(!o->kind||!o->pointer){fail(env,"PC1 COM probe volume requires a live buffer");return NULL;}values[0]=(jint)IDirectSoundBuffer_GetVolume((LPDIRECTSOUNDBUFFER)o->pointer,&volume);values[1]=(jint)volume;out=(*env)->NewIntArray(env,2);if(out)(*env)->SetIntArrayRegion(env,out,0,2,values);return out;}
JNIEXPORT void JNICALL Java_org_circus_core_Pc1ComAbiAudit_closeNative(JNIEnv *env,jclass type,jlong handle){Probe *p=probe(env,handle);(void)type;if(p)dispose(p);}
