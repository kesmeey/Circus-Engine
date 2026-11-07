/* Current PC1 DirectSound/MCI API boundary. No target executable is loaded.
 * Only borrowed backend stream bytes may change on the native MM thread.
 * VM/opcode/host/UI execution remains on the creating Java thread. */
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

#define MAGIC 0x50413143u
#define CHANNELS 10
#define NOT_CALLED ((HRESULT)0x7fffffff)
typedef struct Event {jlong kind,token,a,b,tick;struct Event *next;} Event;
typedef struct Buffer {LPDIRECTSOUNDBUFFER object;DWORD bytes,flags,playFlags;WAVEFORMATEX format;} Buffer;
typedef struct MmRegistration {UINT timer;int once;struct MmRegistration *next;} MmRegistration;
typedef struct Retired {IUnknown *object;int kind;struct Retired *next;} Retired;
typedef struct SourceFile {jlong id;HANDLE file;ULONGLONG size,position;DWORD requested,count,error,rawWord;int hasWord,wordAdopted,closeResult;DWORD closeError;char *path;struct SourceFile *next;} SourceFile;
typedef struct Stream {
    int error; volatile DWORD *mode,*channels;
    volatile DWORD *remaining; DWORD decodedCursor,unit,lastCursor,lastCount,lastLock,lastUnlock;
    SourceFile *source;
    BYTE *memory;
    DWORD memoryBytes;
    BYTE *scratch;
    BYTE *encodedScratch; /*NativeBC3DA4 aliases scratch+0x2000.*/
    DWORD *delta;
} Stream;
typedef struct LegacyMemory {
 BYTE globals[0x38008],lz[0x14030],scratch[0x4000]; DWORD delta[6];
 DWORD input,extra,streamExtra,mode,channels,remaining,words[5],packet,busy,mm;
} LegacyMemory;
typedef struct PhysicalIsland {DWORD base,length;BYTE *bytes,*access;jobject buffer,mask;} PhysicalIsland;
typedef struct Context {
    DWORD magic,thread;
    HWND window,cooperative;
    int ownsWindow;
    LPDIRECTSOUND device;
    LPDIRECTSOUNDBUFFER primary;
    HRESULT createHr,cooperativeHr,primaryHr,formatHr,capsHr;
    DSCAPS caps;
    GUID actualDevice;
    HRESULT deviceIdentityHr;
    Buffer buffers[CHANNELS];
    Retired *retired;
    UINT_PTR userTimer;
    UINT mmTimer;
    volatile DWORD *mmStored;
    DWORD userInterval,mmStopEpoch;
    MmRegistration *mmRegistrations;
    ULONGLONG userDue,mmDue;
    int userFirst,mmFirst,staged;
    DWORD restoreUserDelay,restoreMmDelay,restoreUserInterval;
    int restoreMm,restoreUser;
    DWORD restorePlay[CHANNELS],restoreFlags[CHANNELS];
    jlong registryId;
    int ready,failed,closed,queueFailure;
    Event *head,*tail;
    SourceFile *sources;
    jlong nextSource;
    Stream stream;
    LegacyMemory *legacy;int legacyBorrowed;PhysicalIsland islands[7];int physicalBound;
    volatile DWORD *streamWord[5];
    jobject streamBuffer;
    volatile DWORD *callbackBusy,*packetCount;
    /*One native decoder owner. All these spans are verified CHS PE/BSS.
     *Do not initialize LUT at construction:403B40 owns that transition. */
    BYTE *dspGlobals;
    BYTE *dspLz;
    volatile DWORD *dspInputPointer,*dspStaticExtra;
    jobject dspPeBuffer;
    volatile BYTE *dspPe;DWORD dspPeLength,dspFaultAddress;
    jobject dspExtraBuffer;volatile DWORD *dspStreamingExtra;
    DWORD snapshotDepth,restoreUserToken;
    struct Context *next;
} Context;
#define SW(c,n) (*(c)->streamWord[(n)])
static SRWLOCK registryLock=SRWLOCK_INIT;
static Context *registry;
static LONG64 nextId;
static INIT_ONCE windowOnce=INIT_ONCE_STATIC_INIT;
static const WCHAR windowClass[]=L"CircusPc1AudioService";
static int fillStreamLocked(Context *c,int periodic,int overrideCursor,DWORD cursor);
static int registeredStreamPacket(Context *c,DWORD *count);
static void CALLBACK streamTick(UINT timer,UINT message,DWORD_PTR user,DWORD_PTR one,DWORD_PTR two);
/*Actual registrations are opaque OS resources, separate from writable43DBE0.
 *This list never replaces the canonical token and never repairs a raw alias. */
static void forgetMm(Context *c,UINT timer){MmRegistration **at,*old;for(at=&c->mmRegistrations;*at;at=&(*at)->next)if((*at)->timer==timer){old=*at;*at=old->next;free(old);return;}}
static UINT setMmLocked(Context *c,UINT delay,UINT resolution,UINT flags){MmRegistration *node=(MmRegistration *)calloc(1,sizeof(MmRegistration));UINT timer;if(!node){c->queueFailure=1;return 0;}timer=timeSetEvent(delay,resolution,streamTick,(DWORD_PTR)c->registryId,flags);if(timer){node->timer=timer;node->once=(flags&TIME_PERIODIC)==0;node->next=c->mmRegistrations;c->mmRegistrations=node;}else free(node);return timer;}
static MMRESULT killMm(Context *c,DWORD token){MMRESULT result=token==0xffffffffu?TIMERR_NOERROR:timeKillEvent(token);if(token==c->mmTimer){c->mmStopEpoch++;if(result==TIMERR_NOERROR)c->mmTimer=0;}if(result==TIMERR_NOERROR&&token!=0xffffffffu)forgetMm(c,token);return result;}
static void fail(JNIEnv *env,const char *message){jclass cls=(*env)->FindClass(env,"java/io/IOException");if(cls)(*env)->ThrowNew(env,cls,message);}
static void eventLocked(Context *c,jlong kind,jlong token,jlong a,jlong b){
    Event *e;if(c->closed)return;e=(Event *)malloc(sizeof(Event));if(!e){c->queueFailure=1;return;}
    e->kind=kind;e->token=token;e->a=a;e->b=b;e->tick=(jlong)GetTickCount64();e->next=NULL;if(c->tail)c->tail->next=e;else c->head=e;c->tail=e;
}
static LRESULT CALLBACK windowProc(HWND window,UINT message,WPARAM wParam,LPARAM lParam){
    Context *c=(Context *)(intptr_t)GetWindowLongPtrW(window,GWLP_USERDATA);
    if(message==WM_NCCREATE){CREATESTRUCTW *create=(CREATESTRUCTW *)lParam;c=(Context *)create->lpCreateParams;SetWindowLongPtrW(window,GWLP_USERDATA,(LONG_PTR)c);}
    if(c&&(message==WM_TIMER||message==MM_MCINOTIFY)){AcquireSRWLockExclusive(&registryLock);if(message==WM_TIMER){if(c->userFirst){KillTimer(c->window,c->userTimer);c->userTimer=SetTimer(c->window,2,c->userInterval,NULL);c->userFirst=0;}c->userDue=GetTickCount64()+c->userInterval;}eventLocked(c,message==WM_TIMER?1:3,(jlong)wParam,(jlong)lParam,0);ReleaseSRWLockExclusive(&registryLock);return 0;}
    return DefWindowProcW(window,message,wParam,lParam);
}
static BOOL CALLBACK registerWindow(PINIT_ONCE once,PVOID parameter,PVOID *context){
    WNDCLASSW cls;(void)once;(void)parameter;(void)context;memset(&cls,0,sizeof(cls));cls.lpfnWndProc=windowProc;cls.hInstance=GetModuleHandleW(NULL);cls.lpszClassName=windowClass;return RegisterClassW(&cls)!=0||GetLastError()==ERROR_CLASS_ALREADY_EXISTS;
}
static void CALLBACK streamTick(UINT timer,UINT message,DWORD_PTR user,DWORD_PTR one,DWORD_PTR two){
    Context *c;(void)message;(void)one;(void)two;AcquireSRWLockExclusive(&registryLock);
    for(c=registry;c;c=c->next)if(c->registryId==(jlong)user){
        int result=1,first=c->mmFirst&&c->mmTimer==timer;DWORD stop=c->mmStopEpoch;DWORD raw=(*c->mmStored);
        if(first){c->mmFirst=0;forgetMm(c,timer);}
        if((*c->callbackBusy)==0){(*c->callbackBusy)=1;result=fillStreamLocked(c,1,0,0);(*c->callbackBusy)=0;}
        /*Restored first phase is a one-shot adapter for a periodic timer.
         *Rearm even if fill skipped (busy/rawstate2), unless the actual leaf
         *cancelled this registration. Preserve a concurrently aliased token. */
        if(first){c->mmTimer=0;if(stop==c->mmStopEpoch){c->mmTimer=setMmLocked(c,200,200,TIME_PERIODIC);if(raw==timer&&(*c->mmStored)==raw)(*c->mmStored)=c->mmTimer;}}
        c->mmDue=GetTickCount64()+200;eventLocked(c,2,(jlong)timer,result,SW(c,1));break;
    }
    ReleaseSRWLockExclusive(&registryLock);
}
static Context *context(JNIEnv *env,jlong handle){
    Context *c=(Context *)(intptr_t)handle;if(!c||c->magic!=MAGIC){fail(env,"PC1 DirectSound invalid native context");return NULL;}if(c->thread!=GetCurrentThreadId()){fail(env,"PC1 DirectSound service must be drained/used on its owner thread");return NULL;}return c;
}
static Buffer *buffer(JNIEnv *env,Context *c,jint channel){DWORD offset=(DWORD)channel*4u;if(offset>=CHANNELS*4u){fail(env,"PC1 DirectSound physical pointer cell leaves known ten-slot array after native32 arithmetic");return NULL;}return &c->buffers[offset/4u];}
static jintArray integers(JNIEnv *env,const jint *values,jsize count){jintArray out=(*env)->NewIntArray(env,count);if(out)(*env)->SetIntArrayRegion(env,out,0,count,values);return out;}
static SourceFile *sourceFile(Context *c,jlong id){SourceFile *s;for(s=c->sources;s;s=s->next)if(s->id==id)return s;return NULL;}
static void closeSource(SourceFile *s){if(s&&s->file!=INVALID_HANDLE_VALUE){s->closeResult=CloseHandle(s->file);s->closeError=s->closeResult?0:GetLastError();s->file=INVALID_HANDLE_VALUE;}}
/*41CE70. Each call starts at channel0. DWORD arithmetic and the reserved
 *third word of each channel are retained, including stale shift values. */
static void deltaDecode(BYTE *out,DWORD count,const BYTE *encoded,volatile DWORD state[6],int channels){
    DWORD n,channel=0;for(n=0;n<count;n++){volatile DWORD *s=state+channel*3;int v=(int)(signed char)encoded[n];
        s[0]+=(DWORD)v<<(s[1]&31u);if(v==0){if(s[1]!=0)s[1]--;}else if((v==127||v==-128)&&s[1]!=8)s[1]++;
        out[n*2]=(BYTE)s[0];out[n*2+1]=(BYTE)(s[0]>>8);if(channels==2)channel=1-channel;
    }
}
/*41CF00 raw PCM packet. A zero-byte read with remaining input is an explicit
 *unsafe/stalled native boundary, never an invented successful EOF. */
static int streamPacket(Context *c,DWORD *count){
    Stream *s=&c->stream;DWORD wanted,got=0,produced;BYTE *target;BOOL ok=TRUE;
    if((*s->mode)!=0&&(*s->mode)!=2||s->source&&s->source->hasWord)return registeredStreamPacket(c,count);
    if((*s->remaining)==0){memset(s->scratch,0,0x4000u);*count=0;(*c->packetCount)=0;closeSource(s->source);return 0;}
    wanted=(*s->remaining)<s->unit?(*s->remaining):s->unit;target=s->scratch;
    if((*s->mode)==2){wanted=(*s->remaining)/2;if(wanted>0x2000u)wanted=0x2000u;target=s->encodedScratch;}
    if(s->source){SourceFile *file=s->source;file->requested=wanted;file->count=0;file->error=0;
        ok=ReadFile(file->file,target,wanted,&got,NULL);if(!ok)file->error=GetLastError();file->count=got;file->position+=got;
    }else{DWORD available=s->decodedCursor<s->memoryBytes?s->memoryBytes-s->decodedCursor:0;got=wanted<available?wanted:available;if(got)memcpy(target,s->memory+s->decodedCursor,got);}
    produced=got;if((*s->mode)==2){deltaDecode(s->scratch,got,s->encodedScratch,s->delta,(*s->channels));produced=got*2;}
    (*s->remaining)-=produced;s->decodedCursor+=produced;*count=produced;(*c->packetCount)=produced;
    /* The native ignores ReadFile BOOL, publishes its count, then returns1.
     *A0 count would continue into an unsafe zero Lock/stall. Stop only AFTER
     *the proved decoder/output-count prefix, not before publishing it. */
    if(got==0){s->error=1;s->lastCount=0;eventLocked(c,4,1,s->source?s->source->error:ERROR_HANDLE_EOF,s->decodedCursor);return -1;}
    return 1;
}
/*41B9E0: a full-song DS buffer with a0x20000-byte lead, not a small cyclic
 *ring. Only this backend's private stream/PCM bytes change on the MM thread. */
static int fillStreamLocked(Context *c,int periodic,int overrideCursor,DWORD cursor){
    Stream *s=&c->stream;Buffer *b;HRESULT hr;DWORD ignored=0,count,copy,n1,n2;void *one,*two;int packet;
    if(SW(c,4)==0xffffffffu)return 1;
    if(((int)SW(c,4))==-2){
        /*BC12D0[-2] aliases the actual BC12C8 output-count DWORD. Its zero
         *test precedes fillstate, but nonzero is not a made-up COM object. */
        if((*c->packetCount)==0||SW(c,2)!=0)return 1;
        s->error=3;eventLocked(c,4,3,0xbc12c8u,(*c->packetCount));return -1;
    }
    if(SW(c,4)*4u>=CHANNELS*4u){s->error=3;eventLocked(c,4,3,0xbc12d0u+SW(c,4)*4u,SW(c,4));return -1;}
    b=&c->buffers[(SW(c,4)*4u)/4u];if(!b->object)return 1;if(SW(c,2)!=0||s->error)return 1;SW(c,2)=1;
    if(periodic&&SW(c,1)!=0){if(!overrideCursor){hr=IDirectSoundBuffer_GetCurrentPosition(b->object,&cursor,&ignored);if(hr!=DS_OK){SW(c,2)=0;return 0;}}if(cursor+0x20000u<SW(c,1)){SW(c,2)=0;return 0;}}
    else cursor=0;s->lastCursor=cursor;
    for(;;){
        packet=streamPacket(c,&count);if(packet<0)return -1;
        if(packet==0){SW(c,2)=2;killMm(c,(*c->mmStored));(*c->mmStored)=0xffffffffu;return 1;}
        copy=count;if(SW(c,0)-SW(c,1)<copy)copy=SW(c,0)-SW(c,1);one=two=NULL;n1=n2=0;s->lastCount=copy;(*c->packetCount)=copy;
        hr=IDirectSoundBuffer_Lock(b->object,SW(c,1),copy,&one,&n1,&two,&n2,0);s->lastLock=(DWORD)hr;
        if(hr!=DS_OK||(uint64_t)n1+(uint64_t)n2!=(uint64_t)copy){s->error=2;eventLocked(c,4,2,hr,SW(c,1));return -1;}
        if(n1)memcpy(one,s->scratch,n1);if(n2)memcpy(two,s->scratch+n1,n2);hr=IDirectSoundBuffer_Unlock(b->object,one,n1,two,n2);s->lastUnlock=(DWORD)hr;SW(c,1)+=copy;
        if(cursor+0x20000u<=SW(c,1)){SW(c,2)=0;return 0;}
    }
}
static void destroy(JNIEnv *env,Context *c){
    int n;Context **at;Event *e,*next;SourceFile *s,*nextSource;Retired *retired,*nextRetired;MmRegistration *mm,*nextMm;if(!c)return;
    AcquireSRWLockExclusive(&registryLock);c->closed=1;for(at=&registry;*at;at=&(*at)->next)if(*at==c){*at=c->next;break;}ReleaseSRWLockExclusive(&registryLock);
    for(mm=c->mmRegistrations;mm;mm=nextMm){nextMm=mm->next;timeKillEvent(mm->timer);free(mm);}if(c->userTimer)KillTimer(c->window,c->userTimer);
    for(n=0;n<CHANNELS;n++)if(c->buffers[n].object){IDirectSoundBuffer_Stop(c->buffers[n].object);IDirectSoundBuffer_Release(c->buffers[n].object);}
    if(c->primary)IDirectSoundBuffer_Release(c->primary);if(c->device)IDirectSound_Release(c->device);
    /*Native pointer overwrites leak these references until process exit.
     *Keep them alive/playable across init, release only at provider teardown. */
    for(retired=c->retired;retired;retired=nextRetired){nextRetired=retired->next;IUnknown_Release(retired->object);free(retired);}
    if(c->ownsWindow&&c->window)DestroyWindow(c->window);for(e=c->head;e;e=next){next=e->next;free(e);}for(s=c->sources;s;s=nextSource){nextSource=s->next;if(s->file!=INVALID_HANDLE_VALUE)CloseHandle(s->file);free(s->path);free(s);}free(c->stream.memory);if(c->dspExtraBuffer)(*env)->DeleteGlobalRef(env,c->dspExtraBuffer);if(c->dspPeBuffer)(*env)->DeleteGlobalRef(env,c->dspPeBuffer);if(c->streamBuffer)(*env)->DeleteGlobalRef(env,c->streamBuffer);for(n=0;n<7;n++){if(c->islands[n].buffer)(*env)->DeleteGlobalRef(env,c->islands[n].buffer);if(c->islands[n].mask)(*env)->DeleteGlobalRef(env,c->islands[n].mask);}free(c->legacy);c->magic=0;free(c);
}
static Context *allocateContext(JNIEnv *env,HWND hwnd,int privateWindow){
    Context *c=(Context *)calloc(1,sizeof(Context));if(!c){fail(env,"PC1 audio context allocation");return NULL;}
    c->legacy=(LegacyMemory *)calloc(1,sizeof(LegacyMemory));if(!c->legacy){free(c);fail(env,"PC1 legacy audio allocation");return NULL;}
    c->magic=MAGIC;c->thread=GetCurrentThreadId();c->dspGlobals=c->legacy->globals;c->dspLz=c->legacy->lz;
    c->stream.scratch=c->legacy->scratch;c->stream.encodedScratch=c->stream.scratch+0x2000;c->stream.delta=c->legacy->delta;
    c->stream.mode=&c->legacy->mode;c->stream.channels=&c->legacy->channels;c->stream.remaining=&c->legacy->remaining;
    c->dspInputPointer=&c->legacy->input;c->dspStaticExtra=&c->legacy->extra;c->dspStreamingExtra=&c->legacy->streamExtra;
    c->mmStored=&c->legacy->mm;c->callbackBusy=&c->legacy->busy;c->packetCount=&c->legacy->packet;
    {int n;for(n=0;n<5;n++)c->streamWord[n]=&c->legacy->words[n];}
    *c->dspInputPointer=0xbd5fa0u;*c->dspStaticExtra=1;*c->dspStreamingExtra=1;*c->mmStored=0xffffffffu;SW(c,4)=0xffffffffu;
    c->createHr=c->cooperativeHr=c->primaryHr=c->formatHr=c->capsHr=c->deviceIdentityHr=NOT_CALLED;
    if(privateWindow){
        if(!InitOnceExecuteOnce(&windowOnce,registerWindow,NULL,NULL)){free(c);fail(env,"PC1 audio service window class unavailable");return NULL;}
        c->ownsWindow=1;c->window=CreateWindowExW(WS_EX_TOOLWINDOW,windowClass,L"Circus audio service",WS_POPUP,0,0,1,1,NULL,NULL,GetModuleHandleW(NULL),c);
        if(!c->window){destroy(env,c);fail(env,"PC1 audio hidden service window creation failed");return NULL;}
        c->cooperative=hwnd?hwnd:c->window;
    }else c->window=c->cooperative=hwnd;
    c->registryId=(jlong)InterlockedIncrement64(&nextId);AcquireSRWLockExclusive(&registryLock);c->next=registry;registry=c;ReleaseSRWLockExclusive(&registryLock);return c;
}
static Retired *reserveRetired(JNIEnv *env,IUnknown *old,int kind){Retired *node;if(!old)return NULL;node=(Retired *)calloc(1,sizeof(Retired));if(!node){fail(env,"PC1 overwritten COM reference owner allocation");return NULL;}node->object=old;node->kind=kind;return node;}
static void retainOverwritten(Context *c,Retired *node,IUnknown *current,HRESULT result){if(!node)return;if(node->object!=current||result==DS_OK){node->next=c->retired;c->retired=node;}else free(node);}
static HRESULT initCreate(JNIEnv *env,Context *c){Retired *old=reserveRetired(env,(IUnknown *)c->device,0);if((*env)->ExceptionCheck(env))return NOT_CALLED;c->createHr=DirectSoundCreate(NULL,&c->device,NULL);retainOverwritten(c,old,(IUnknown *)c->device,c->createHr);if(c->createHr!=DS_OK)c->failed=1;return c->createHr;}
static HRESULT initCooperative(JNIEnv *env,Context *c,DWORD level){if(!c->device){fail(env,"PC1 native cooperative call dereferences absent DirectSound device");return NOT_CALLED;}c->cooperativeHr=IDirectSound_SetCooperativeLevel(c->device,c->cooperative,level);return c->cooperativeHr;}
static HRESULT initPrimary(JNIEnv *env,Context *c,DWORD flags){DSBUFFERDESC desc;Retired *old;if(!c->device){fail(env,"PC1 native primary create dereferences absent DirectSound device");return NOT_CALLED;}old=reserveRetired(env,(IUnknown *)c->primary,1);if((*env)->ExceptionCheck(env))return NOT_CALLED;memset(&desc,0,sizeof(desc));desc.dwSize=sizeof(desc);desc.dwFlags=flags;c->primaryHr=IDirectSound_CreateSoundBuffer(c->device,&desc,&c->primary,NULL);retainOverwritten(c,old,(IUnknown *)c->primary,c->primaryHr);return c->primaryHr;}
static HRESULT initFormat(JNIEnv *env,Context *c,const BYTE *format,size_t bytes){
    WORD extra;if(!c->primary){fail(env,"PC1 native SetFormat dereferences absent primary after ignored create HRESULT");return NOT_CALLED;}
    if(bytes<18){fail(env,"PC1 primary WAVEFORMATEX missing known bytes");return NOT_CALLED;}memcpy(&extra,format+16,2);if((size_t)extra>bytes-18){fail(env,"PC1 primary WAVEFORMATEX extension reaches unknown backing");return NOT_CALLED;}
    c->formatHr=IDirectSoundBuffer_SetFormat(c->primary,(LPCWAVEFORMATEX)format);c->ready=1;return c->formatHr;
}
JNIEXPORT jlong JNICALL Java_org_circus_core_Pc1DirectSound_openNative(JNIEnv *env,jclass owner,jlong hwnd){
    Context *c;WAVEFORMATEX format;(void)owner;c=allocateContext(env,(HWND)(intptr_t)hwnd,1);if(!c)return 0;
    if(initCreate(env,c)==DS_OK){initCooperative(env,c,2);if(!(*env)->ExceptionCheck(env))initPrimary(env,c,0x4009u);if(!(*env)->ExceptionCheck(env)){
        memset(&format,0,sizeof(format));format.wFormatTag=1;format.nChannels=2;format.nSamplesPerSec=44100;format.nAvgBytesPerSec=176400;format.nBlockAlign=4;format.wBitsPerSample=16;initFormat(env,c,(BYTE *)&format,18);
    }}return(jlong)(intptr_t)c;
}
JNIEXPORT jlong JNICALL Java_org_circus_core_Pc1DirectSound_openDeferredNative(JNIEnv *env,jclass owner){Context *c;(void)owner;c=allocateContext(env,NULL,0);return(jlong)(intptr_t)c;}
JNIEXPORT void JNICALL Java_org_circus_core_Pc1DirectSound_borrowWindowNative(JNIEnv *env,jclass owner,jlong handle,jlong hwnd){
    Context *c=context(env,handle);(void)owner;if(!c)return;if(c->ownsWindow){fail(env,"PC1 private service window cannot be rebound as native main window");return;}
    if(c->userTimer&&c->window!=(HWND)(intptr_t)hwnd){fail(env,"PC1 cannot move live timer2 to a different HWND without staging");return;}c->window=c->cooperative=(HWND)(intptr_t)hwnd;
}
JNIEXPORT jint JNICALL Java_org_circus_core_Pc1DirectSound_initializeNative(JNIEnv *env,jclass owner,jlong handle,jint phase,jint value,jbyteArray raw){
    Context *c=context(env,handle);HRESULT result=NOT_CALLED;BYTE *bytes=NULL;jsize count=0;int n;(void)owner;if(!c)return(jint)result;
    switch(phase){
    case 0:result=initCreate(env,c);break;
    case 1:result=initCooperative(env,c,(DWORD)value);break;
    case 2:result=initPrimary(env,c,(DWORD)value);break;
    case 3:
        if(!raw){fail(env,"PC1 primary raw WAVEFORMATEX required");break;}count=(*env)->GetArrayLength(env,raw);bytes=(BYTE *)malloc(count?(size_t)count:1);if(!bytes){fail(env,"PC1 primary format backing allocation");break;}
        (*env)->GetByteArrayRegion(env,raw,0,count,(jbyte *)bytes);if(!(*env)->ExceptionCheck(env))result=initFormat(env,c,bytes,(size_t)count);free(bytes);break;
    case 4:{Retired *pending[CHANNELS]={0};
        /*Allocate bookkeeping before REP, then preserve every lost COM ref.
         *No Stop/Release occurs at native pointer clear, even when playing. */
        for(n=0;n<CHANNELS;n++){pending[n]=reserveRetired(env,(IUnknown *)c->buffers[n].object,2);if((*env)->ExceptionCheck(env)){int k;for(k=0;k<n;k++)free(pending[k]);return(jint)result;}}
        AcquireSRWLockExclusive(&registryLock);for(n=0;n<CHANNELS;n++)if(pending[n]){pending[n]->next=c->retired;c->retired=pending[n];}memset(c->buffers,0,sizeof(c->buffers));ReleaseSRWLockExclusive(&registryLock);result=0;break;}
    case 5:if(c->primary){result=(HRESULT)IDirectSoundBuffer_Release(c->primary);c->primary=NULL;}else result=0;break;
    case 6:if(c->device){result=(HRESULT)IDirectSound_Release(c->device);c->device=NULL;}else result=0;c->ready=0;break;
    default:fail(env,"PC1 audio native initialization phase");break;
    }return(jint)result;
}
static const DWORD physicalBases[7]={0x42a048u,0x43dbc0u,0x43de74u,0x455570u,0xbb8550u,0xbc1d60u,0xbc5f7cu};
static const DWORD physicalLengths[7]={0x5c68u,40u,56u,0x38010u,0x8d80u,0x4048u,0x14038u};
static BYTE *physicalPointer(Context *c,DWORD address,DWORD count,int permission,DWORD *available){int n;DWORD at,k;
 if(!c->physicalBound)return NULL;for(n=0;n<7;n++){PhysicalIsland *p=&c->islands[n];at=address-p->base;if(at<p->length){
  if(count>p->length-at)break;for(k=0;k<count;k++)if((p->access[at+k]&permission)!=permission)break;if(k!=count)break;
  if(available){k=count;while(k<p->length-at&&(p->access[at+k]&permission)==permission)k++;*available=k;}return p->bytes+at;
 }}c->dspFaultAddress=address;if(available)*available=0;return NULL;
}
JNIEXPORT jlong JNICALL Java_org_circus_core_Pc1DirectSound_directAddressNative(JNIEnv *env,jclass owner,jobject raw){void *p;(void)owner;p=raw?(*env)->GetDirectBufferAddress(env,raw):NULL;if(!p)fail(env,"PC1 native physical address requires direct allocation");return(jlong)(intptr_t)p;}
JNIEXPORT jint JNICALL Java_org_circus_core_Pc1DirectSound_directWordNative(JNIEnv *env,jclass owner,jobject raw,jint offset,jboolean write,jint value){BYTE *p;volatile DWORD *word;jlong length;(void)owner;p=raw?(*env)->GetDirectBufferAddress(env,raw):NULL;length=raw?(*env)->GetDirectBufferCapacity(env,raw):0;if(!p||offset<0||(jlong)offset+4>length){fail(env,"PC1 native physical DWORD leaves exact allocation");return 0;}word=(volatile DWORD *)(p+offset);if(write){*word=(DWORD)value;return value;}return(jint)*word;}
JNIEXPORT jbyteArray JNICALL Java_org_circus_core_Pc1DirectSound_decoderSeedNative(JNIEnv *env,jclass owner,jlong handle,jint address);
JNIEXPORT void JNICALL Java_org_circus_core_Pc1DirectSound_bindPhysicalNative(JNIEnv *env,jclass owner,jlong handle,jobjectArray buffers,jobjectArray masks){
 Context *c=context(env,handle);PhysicalIsland staged[7];int n,k;DWORD length;BYTE *p;(void)owner;if(!c)return;
 if(c->physicalBound||c->mmRegistrations||c->legacyBorrowed||!buffers||!masks||(*env)->GetArrayLength(env,buffers)!=7||(*env)->GetArrayLength(env,masks)!=7){fail(env,"PC1 physical adoption requires stopped, unloaned legacy context and seven exact islands");return;}
 memset(staged,0,sizeof(staged));for(n=0;n<7;n++){jobject b=(*env)->GetObjectArrayElement(env,buffers,n),m=(*env)->GetObjectArrayElement(env,masks,n);staged[n].base=physicalBases[n];staged[n].length=physicalLengths[n];staged[n].bytes=b?(*env)->GetDirectBufferAddress(env,b):NULL;staged[n].access=m?(*env)->GetDirectBufferAddress(env,m):NULL;
  if(!staged[n].bytes||!staged[n].access||(*env)->GetDirectBufferCapacity(env,b)!=staged[n].length||(*env)->GetDirectBufferCapacity(env,m)!=staged[n].length||((uintptr_t)staged[n].bytes&0xfffu)!=(staged[n].base&0xfffu)){fail(env,"PC1 shared island extent/alignment proof");break;}
  staged[n].buffer=(*env)->NewGlobalRef(env,b);staged[n].mask=(*env)->NewGlobalRef(env,m);(*env)->DeleteLocalRef(env,b);(*env)->DeleteLocalRef(env,m);if(!staged[n].buffer||!staged[n].mask)break;
 }
 if(n!=7){for(k=0;k<7;k++){if(staged[k].buffer)(*env)->DeleteGlobalRef(env,staged[k].buffer);if(staged[k].mask)(*env)->DeleteGlobalRef(env,staged[k].mask);}return;}
 {static const DWORD required[][2]={{0x455574u,0x38008u},{0xbc5f80u,0x14030u},{0x42fca8u,4},{0x43de78u,4},{0xbc1d78u,4},{0xbc1d7cu,4},{0xbc1d88u,24},{0xbc1da4u,0x4000},{0xbc5da4u,4},{0xbc12c8u,4},{0xbc1d64u,4},{0x43dbe0u,4},{0x43de74u,4},{0xbbaaf8u,4},{0xbbab24u,4},{0xbbab50u,4},{0xbbab7cu,4},{0x43dbdcu,4}};DWORD r,at,j;int found;
  for(r=0;r<sizeof(required)/sizeof(required[0]);r++){found=0;for(n=0;n<7;n++){at=required[r][0]-staged[n].base;if(at<staged[n].length&&required[r][1]<=staged[n].length-at){found=1;for(j=0;j<required[r][1];j++)if((staged[n].access[at+j]&3)!=3){found=0;break;}break;}}if(!found){for(k=0;k<7;k++){(*env)->DeleteGlobalRef(env,staged[k].buffer);(*env)->DeleteGlobalRef(env,staged[k].mask);}fail(env,"PC1 physical adoption has unknown logical owner bytes");return;}}
 }
 /*Only Java's owned seeds enter the shared allocation; no second live copy.
  *No MM registration exists, and this adoption performs no opcode callback.*/
 memcpy(c->islands,staged,sizeof(staged));c->physicalBound=1;
 #define PWORD(a) ((volatile DWORD *)physicalPointer(c,(a),4,3,NULL))
 c->dspGlobals=physicalPointer(c,0x455574u,0x38008u,3,&length);c->dspLz=physicalPointer(c,0xbc5f80u,0x14030u,3,&length);
 c->dspInputPointer=PWORD(0x42fca8u);c->dspStaticExtra=PWORD(0x43de78u);c->dspStreamingExtra=PWORD(0x43de74u);
 c->stream.mode=PWORD(0xbc1d78u);c->stream.remaining=PWORD(0xbc1d7cu);c->stream.channels=PWORD(0xbc5da4u);
 c->stream.delta=(DWORD *)physicalPointer(c,0xbc1d88u,24,3,&length);c->stream.scratch=physicalPointer(c,0xbc1da4u,0x4000u,3,&length);c->stream.encodedScratch=c->stream.scratch?c->stream.scratch+0x2000:NULL;
 c->packetCount=PWORD(0xbc12c8u);c->callbackBusy=PWORD(0xbc1d64u);c->mmStored=PWORD(0x43dbe0u);
 {static const DWORD words[5]={0xbbaaf8u,0xbbab24u,0xbbab50u,0xbbab7cu,0x43dbdcu};for(n=0;n<5;n++)c->streamWord[n]=PWORD(words[n]);}
 #undef PWORD
 p=c->stream.scratch;if(!p||!c->dspGlobals||!c->dspLz||!c->dspStreamingExtra||!c->dspInputPointer||!c->dspStaticExtra||!c->stream.mode||!c->stream.remaining||!c->stream.channels||!c->stream.delta||!c->packetCount||!c->callbackBusy||!c->mmStored){fail(env,"PC1 physical adoption missing admitted logical owner");return;}
 for(n=0;n<5;n++)if(!c->streamWord[n]){fail(env,"PC1 physical interleave owner missing");return;}
 if(c->streamBuffer){(*env)->DeleteGlobalRef(env,c->streamBuffer);c->streamBuffer=NULL;}if(c->dspExtraBuffer){(*env)->DeleteGlobalRef(env,c->dspExtraBuffer);c->dspExtraBuffer=NULL;}free(c->legacy);c->legacy=NULL;
}
static BYTE *dspRegion(Context *c,DWORD address,DWORD *length){switch(address){case 0x455574u:*length=0x38008u;return c->dspGlobals;case 0xbc5f80u:*length=0x14030u;return c->dspLz;case 0x42fca8u:*length=4;return(BYTE *)c->dspInputPointer;case 0x43de78u:*length=4;return(BYTE *)c->dspStaticExtra;case 0xbc1d78u:*length=4;return(BYTE *)c->stream.mode;case 0xbc1d7cu:*length=4;return(BYTE *)c->stream.remaining;case 0xbc1d88u:*length=24;return(BYTE *)c->stream.delta;case 0xbc1da4u:*length=0x4000u;return c->stream.scratch;case 0xbc3da4u:*length=0x2000;return c->stream.encodedScratch;case 0xbc5da4u:*length=4;return(BYTE *)c->stream.channels;default:*length=0;return NULL;}}
JNIEXPORT jint JNICALL Java_org_circus_core_Pc1DirectSound_physicalWordForAuditNative(JNIEnv *env,jclass owner,jlong handle,jint address,jboolean write,jint value){Context *c=context(env,handle);BYTE *p;volatile DWORD *word;(void)owner;if(!c)return 0;p=physicalPointer(c,(DWORD)address,4,write?2:1,NULL);if(!p){fail(env,"PC1 exact physical DWORD requires known single island");return 0;}word=(volatile DWORD *)p;if(write){*word=(DWORD)value;return value;}return(jint)*word;}
JNIEXPORT jbyteArray JNICALL Java_org_circus_core_Pc1DirectSound_decoderSeedNative(JNIEnv *env,jclass owner,jlong handle,jint address){Context *c=context(env,handle);DWORD length;BYTE *p;jbyteArray out;(void)owner;if(!c)return NULL;if(c->mmRegistrations){fail(env,"PC1 decoder seed requires stopped physical provider");return NULL;}p=dspRegion(c,(DWORD)address,&length);if(!p){fail(env,"PC1 decoder seed range");return NULL;}out=(*env)->NewByteArray(env,(jsize)length);if(out)(*env)->SetByteArrayRegion(env,out,0,(jsize)length,(const jbyte *)p);return out;}
#include "pc1_dsp.h"
/*FILE is an OS scalar, not a readable COM object. Java's sole world registry
 *publishes these associations; the MM thread performs only native IO/DSP. */
static SourceFile *registeredFile(Context *c,DWORD raw){SourceFile *file;DWORD base=raw&~3u;if(raw==0||raw==0xffffffffu)return NULL;for(file=c->sources;file;file=file->next)if(file->hasWord&&(file->rawWord&~3u)==base)return file;return NULL;}
static int registeredReadBytes(Context *c,DWORD raw,BYTE *target,DWORD requested,DWORD *count){SourceFile *file=registeredFile(c,raw);HANDLE physical=INVALID_HANDLE_VALUE;BOOL result;DWORD error;
    if(file&&file->file!=INVALID_HANDLE_VALUE)physical=(HANDLE)((uintptr_t)file->file|(raw&3u));else if(raw==0)physical=NULL;
    *count=0;result=ReadFile(physical,target,requested,count,NULL);error=result?0:GetLastError();if(file){file->requested=requested;file->count=*count;file->error=error;file->position+=*count;}return result!=0;
}
static int registeredRead(Context *c,void *unused,DWORD raw,DWORD target,DWORD requested,DWORD countAddress){DWORD available=requested,count=0,n;volatile BYTE *pointer=c->physicalBound?physicalPointer(c,target,requested,2,NULL):dspPointer(c,target,&available);(void)unused;if(!pointer||available<requested){c->dspFaultAddress=target;return 0;}registeredReadBytes(c,raw,(BYTE *)pointer,requested,&count);for(n=0;n<4;n++)if(!dspByteStore(c,countAddress+n,(BYTE)(count>>(n*8))))return 0;/*ReadFile BOOL is ignored by original403DF0.*/return 1;}
static int registeredClose(Context *c,void *unused,DWORD raw){SourceFile *file=registeredFile(c,raw);HANDLE physical=INVALID_HANDLE_VALUE;BOOL result;DWORD error;(void)unused;if(file&&file->file!=INVALID_HANDLE_VALUE)physical=(HANDLE)((uintptr_t)file->file|(raw&3u));else if(raw==0)physical=NULL;result=CloseHandle(physical);error=result?0:GetLastError();if(file){file->closeResult=result;file->closeError=error;if(result)file->file=INVALID_HANDLE_VALUE;}return 1;/*Original ignores BOOL.*/}
static int registeredStreamPacket(Context *c,DWORD *count){Stream *s=&c->stream;DWORD requested,got=0,produced;int result;c->dspFaultAddress=0;
    if(!c->dspPe){s->error=3;eventLocked(c,4,4,0x42ab1cu,0);return -1;}
    if((*s->mode)!=0&&(*s->mode)!=2){result=dspCompressedPacket(c,registeredRead,registeredClose,NULL,count);if(result<0){s->error=3;eventLocked(c,4,4,c->dspFaultAddress,0);}else{(*c->packetCount)=*count;s->decodedCursor+=*count;}return result;}
    if((*s->remaining)==0){memset(s->scratch,0,0x4000u);*count=0;(*c->packetCount)=0;registeredClose(c,NULL,dspPeWord(c,0x42ab1c));dspPeStore(c,0x42ab1c,0xffffffffu);return 0;}
    requested=(*s->remaining)<s->unit?(*s->remaining):s->unit;if((*s->mode)==2){requested=(*s->remaining)/2u;if(requested>0x2000u)requested=0x2000u;}
    registeredReadBytes(c,dspPeWord(c,0x42ab1c),(*s->mode)==2?s->encodedScratch:s->scratch,requested,&got);produced=got;if((*s->mode)==2){deltaDecode(s->scratch,got,s->encodedScratch,s->delta,(*s->channels));produced=got*2u;}(*s->remaining)-=produced;s->decodedCursor+=produced;*count=produced;(*c->packetCount)=produced;
    if(got==0){s->error=1;s->lastCount=0;eventLocked(c,4,1,ERROR_HANDLE_EOF,s->decodedCursor);return -1;}return 1;
}
typedef struct DspFixture {const BYTE *bytes;DWORD length,position,limit,calls,lastHandle,requested;} DspFixture;
static int dspFixtureRead(Context *c,void *opaque,DWORD file,DWORD target,DWORD requested,DWORD countAddress){DspFixture *fixture=(DspFixture *)opaque;DWORD got,n;fixture->calls++;fixture->lastHandle=file;fixture->requested=requested;got=fixture->position<fixture->length?fixture->length-fixture->position:0;if(got>requested)got=requested;if(got>fixture->limit)got=fixture->limit;for(n=0;n<got;n++)if(!dspByteStore(c,target+n,fixture->bytes[fixture->position+n]))return 0;fixture->position+=got;for(n=0;n<4;n++)if(!dspByteStore(c,countAddress+n,(BYTE)(got>>(n*8))))return 0;return 1;}
static int dspFixtureClose(Context *c,void *opaque,DWORD file){DspFixture *fixture=(DspFixture *)opaque;(void)c;fixture->lastHandle=file;return 1;}
/*Private-array ReadFile fixture only. Production MM never calls Java. The
 *returned fault address is after the actual ordered native-memory prefix. */
JNIEXPORT jintArray JNICALL Java_org_circus_core_Pc1DirectSound_dspLzAuditNative(JNIEnv *env,jclass owner,jlong handle,jint operation,jint packed,jbyteArray input,jint position,jint readLimit){Context *c=context(env,handle);DspFixture fixture;BYTE *bytes;jsize length;DWORD result=0;int ok=1;jint values[7];(void)owner;if(!c)return NULL;if(!c->dspPe||!input){fail(env,"PC1 LZ audit requires canonical PE and explicit ReadFile fixture");return NULL;}length=(*env)->GetArrayLength(env,input);bytes=(BYTE *)malloc(length?length:1);if(!bytes){fail(env,"PC1 LZ audit fixture allocation");return NULL;}if(length)(*env)->GetByteArrayRegion(env,input,0,length,(jbyte *)bytes);memset(&fixture,0,sizeof(fixture));fixture.bytes=bytes;fixture.length=(DWORD)length;fixture.position=(DWORD)position;fixture.limit=(DWORD)readLimit;c->dspFaultAddress=0;if(operation==0)dspInitLz(c,(DWORD)packed);else if(operation==1)ok=dspInputByte(c,dspFixtureRead,&fixture,&result);else if(operation==2)ok=dspLzBlock(c,dspFixtureRead,&fixture,&result);else if(operation==3){ok=dspCompressedPacket(c,dspFixtureRead,dspFixtureClose,&fixture,&result);(*c->packetCount)=result;/*Audit returns native function result separately from outputcount.*/}else{free(bytes);fail(env,"PC1 LZ audit operation");return NULL;}free(bytes);values[0]=(jint)result;values[1]=(jint)fixture.position;values[2]=(jint)fixture.calls;values[3]=(jint)fixture.lastHandle;values[4]=(jint)fixture.requested;values[5]=(jint)c->dspFaultAddress;values[6]=ok;return integers(env,values,7);}
/*Controlled caller-owned file only. Each variant gets a fresh HANDLE, so
 *successful tagged CloseHandle cannot accidentally close a reused resource. */
JNIEXPORT jlongArray JNICALL Java_org_circus_core_Pc1DirectSound_fileTagsAuditNative(JNIEnv *env,jclass owner,jlong handle,jbyteArray raw){Context *c=context(env,handle);jsize length;char *path;HANDLE file,tagged;DWORD tag,count,error,closeError;BOOL readResult,closeResult;BYTE byte;jlong values[40];jlongArray out;(void)owner;if(!c||!raw)return NULL;length=(*env)->GetArrayLength(env,raw);path=(char *)calloc((size_t)length+1,1);if(!path){fail(env,"PC1 file tag audit path allocation");return NULL;}(*env)->GetByteArrayRegion(env,raw,0,length,(jbyte *)path);for(tag=0;tag<4;tag++){file=CreateFileA(path,GENERIC_READ,FILE_SHARE_READ,NULL,OPEN_EXISTING,FILE_ATTRIBUTE_NORMAL,NULL);if(file==INVALID_HANDLE_VALUE){free(path);fail(env,"PC1 controlled file tag audit open failed");return NULL;}tagged=(HANDLE)((uintptr_t)file|tag);count=0;byte=0;SetLastError(0);readResult=ReadFile(tagged,&byte,1,&count,NULL);error=readResult?0:GetLastError();SetLastError(0);closeResult=CloseHandle(tagged);closeError=closeResult?0:GetLastError();if(!closeResult)CloseHandle(file);values[tag*10]=tag;values[tag*10+1]=(jlong)(intptr_t)file;values[tag*10+2]=(jlong)(intptr_t)tagged;values[tag*10+3]=readResult;values[tag*10+4]=error;values[tag*10+5]=count;values[tag*10+6]=byte;values[tag*10+7]=closeResult;values[tag*10+8]=closeError;values[tag*10+9]=GetCurrentProcessId();}free(path);out=(*env)->NewLongArray(env,40);if(out)(*env)->SetLongArrayRegion(env,out,0,40,values);return out;}
JNIEXPORT void JNICALL Java_org_circus_core_Pc1DirectSound_bindDspPeNative(JNIEnv *env,jclass owner,jlong handle,jobject raw){Context *c=context(env,handle);void *pointer;jlong length;jobject strong;(void)owner;if(!c)return;if(!raw){fail(env,"PC1 DSP requires canonical effect PE direct backing");return;}pointer=(*env)->GetDirectBufferAddress(env,raw);length=(*env)->GetDirectBufferCapacity(env,raw);if(!pointer||length!=0x42fca8u-0x42ab1cu||((uintptr_t)pointer&3u)){fail(env,"PC1 DSP PE view must be aligned exact42AB1C..42FCA8 owner");return;}strong=(*env)->NewGlobalRef(env,raw);if(!strong)return;AcquireSRWLockExclusive(&registryLock);if(c->mmRegistrations){ReleaseSRWLockExclusive(&registryLock);(*env)->DeleteGlobalRef(env,strong);fail(env,"PC1 cannot switch shared DSP PE owner while MM registered");return;}if(c->dspPeBuffer)(*env)->DeleteGlobalRef(env,c->dspPeBuffer);c->dspPeBuffer=strong;c->dspPe=(volatile BYTE *)pointer;c->dspPeLength=(DWORD)length;ReleaseSRWLockExclusive(&registryLock);}
JNIEXPORT void JNICALL Java_org_circus_core_Pc1DirectSound_dspCoefficientsNative(JNIEnv *env,jclass owner,jlong handle,jint extra){Context *c=context(env,handle);(void)owner;if(!c)return;if(!c->dspPe){fail(env,"PC1 DSP coefficients require canonical PE owner");return;}dspSelectCoefficients(c,(DWORD)extra);}
JNIEXPORT void JNICALL Java_org_circus_core_Pc1DirectSound_dspTransformNative(JNIEnv *env,jclass owner,jlong handle,jint output,jint extra){Context *c=context(env,handle);(void)owner;if(!c)return;if(!c->dspPe){fail(env,"PC1 native404140 requires canonical PE owner");return;}c->dspFaultAddress=0;if(!dspStreamTransform(c,(DWORD)output,(DWORD)extra)){char message[128];sprintf_s(message,sizeof(message),"PC1 native DSP memory view required at0x%08lx after actual prefix",(unsigned long)c->dspFaultAddress);fail(env,message);}}
JNIEXPORT void JNICALL Java_org_circus_core_Pc1DirectSound_bindDspExtraNative(JNIEnv *env,jclass owner,jlong handle,jobject raw){Context *c=context(env,handle);void *address;jobject strong;(void)owner;if(!c)return;address=raw?(*env)->GetDirectBufferAddress(env,raw):NULL;if(!address||(*env)->GetDirectBufferCapacity(env,raw)!=4||((uintptr_t)address&3u)){fail(env,"PC1 DSP43DE74 requires aligned canonical4B direct owner");return;}strong=(*env)->NewGlobalRef(env,raw);if(!strong)return;AcquireSRWLockExclusive(&registryLock);if(c->mmRegistrations||c->physicalBound){ReleaseSRWLockExclusive(&registryLock);(*env)->DeleteGlobalRef(env,strong);fail(env,"PC1 active/shared MM cannot switch canonical extra owner");return;}if(c->dspExtraBuffer)(*env)->DeleteGlobalRef(env,c->dspExtraBuffer);c->dspExtraBuffer=strong;c->dspStreamingExtra=(volatile DWORD *)address;ReleaseSRWLockExclusive(&registryLock);}
JNIEXPORT jobject JNICALL Java_org_circus_core_Pc1DirectSound_dspRegionNative(JNIEnv *env,jclass owner,jlong handle,jint address){Context *c=context(env,handle);DWORD length;BYTE *pointer;(void)owner;if(!c)return NULL;pointer=dspRegion(c,(DWORD)address,&length);if(!c->physicalBound)c->legacyBorrowed=1;if(!pointer){fail(env,"PC1 decoder direct view requires reviewed native owner range");return NULL;}return(*env)->NewDirectByteBuffer(env,pointer,length);}
JNIEXPORT void JNICALL Java_org_circus_core_Pc1DirectSound_initializeDecoderNative(JNIEnv *env,jclass owner,jlong handle){Context *c=context(env,handle);volatile DWORD *ready;volatile WORD *table;WORD positive=0,negative=0;DWORD n;(void)owner;if(!c)return;ready=(volatile DWORD *)(c->dspGlobals+(0x48d578u-0x455574u));if(*ready!=0)return;*ready=1;table=(volatile WORD *)(c->dspGlobals+(0x465574u-0x455574u));for(n=0;n<32768u;n++){positive++;table[n*2]=negative;table[n*2+1]=positive;negative--;}}
JNIEXPORT jintArray JNICALL Java_org_circus_core_Pc1DirectSound_retiredNative(JNIEnv *env,jclass owner,jlong handle){Context *c=context(env,handle);Retired *node;jint values[3]={0};(void)owner;if(!c)return NULL;for(node=c->retired;node;node=node->next)values[node->kind]++;return integers(env,values,3);}
JNIEXPORT void JNICALL Java_org_circus_core_Pc1DirectSound_bindStreamBytesNative(JNIEnv *env,jclass owner,jlong handle,jobject raw){
    Context *c=context(env,handle);void *address;jlong capacity;jobject hold;(void)owner;if(!c)return;
    if(!raw){fail(env,"PC1 canonical stream direct bytes missing");return;}address=(*env)->GetDirectBufferAddress(env,raw);capacity=(*env)->GetDirectBufferCapacity(env,raw);
    if(!address||capacity!=20||((uintptr_t)address&3u)!=0){fail(env,"PC1 canonical stream bytes require aligned20B direct backing");return;}hold=(*env)->NewGlobalRef(env,raw);if(!hold)return;
    AcquireSRWLockExclusive(&registryLock);if(c->mmRegistrations||c->physicalBound){ReleaseSRWLockExclusive(&registryLock);(*env)->DeleteGlobalRef(env,hold);fail(env,"PC1 cannot rebind active/shared MM stream owner");return;}
    if(c->streamBuffer)(*env)->DeleteGlobalRef(env,c->streamBuffer);c->streamBuffer=hold;{int n;for(n=0;n<5;n++)c->streamWord[n]=(volatile DWORD *)address+n;}ReleaseSRWLockExclusive(&registryLock);
}
JNIEXPORT jbyteArray JNICALL Java_org_circus_core_Pc1DirectSound_sharedBytesNative(JNIEnv *env,jclass owner,jlong handle){Context *c=context(env,handle);jbyteArray out;(void)owner;if(!c)return NULL;out=(*env)->NewByteArray(env,20);if(out){DWORD words[5];int n;for(n=0;n<5;n++)words[n]=SW(c,n);(*env)->SetByteArrayRegion(env,out,0,20,(const jbyte *)words);}return out;}
static volatile DWORD *backendWord(Context *c,jint address){switch((DWORD)address){case 0xbc12c8u:return c->packetCount;case 0xbc1d64u:return c->callbackBusy;case 0x43dbe0u:return c->mmStored;default:return NULL;}}
JNIEXPORT jint JNICALL Java_org_circus_core_Pc1DirectSound_backendByteNative(JNIEnv *env,jclass owner,jlong handle,jint address,jint byteOffset,jint writeValue){
    Context *c=context(env,handle);volatile DWORD *word;volatile BYTE *bytes;(void)owner;if(!c)return 0;word=backendWord(c,address);if(!word||byteOffset<0||byteOffset>3){fail(env,"PC1 backend byte owner address");return 0;}bytes=(volatile BYTE *)word;
    if(writeValue>=0)bytes[byteOffset]=(BYTE)writeValue;return bytes[byteOffset];
}
/*Native DWORD leaves use the sole aligned backend/direct backing. Do not
 *take registryLock here: that would strengthen original MM/UI ordering.
 *Unaligned x86 words still use one native load/store; no Java byte folding.*/
JNIEXPORT jint JNICALL Java_org_circus_core_Pc1DirectSound_backendWordNative(JNIEnv *env,jclass owner,jlong handle,jint address,jboolean write,jint value){Context *c=context(env,handle);volatile DWORD *word;(void)owner;if(!c)return 0;word=backendWord(c,address);if(!word){fail(env,"PC1 backend DWORD owner address");return 0;}if(write){*word=(DWORD)value;return value;}return(jint)*word;}
JNIEXPORT jint JNICALL Java_org_circus_core_Pc1DirectSound_decoderWordNative(JNIEnv *env,jclass owner,jlong handle,jint region,jint offset,jboolean write,jint value){Context *c=context(env,handle);DWORD length;BYTE *pointer;volatile DWORD *word;(void)owner;if(!c)return 0;pointer=dspRegion(c,(DWORD)region,&length);if(!pointer||offset<0||(DWORD)offset>length||length-(DWORD)offset<4){fail(env,"PC1 decoder DWORD leaves its known direct owner");return 0;}word=(volatile DWORD *)(pointer+(DWORD)offset);if(write){*word=(DWORD)value;return value;}return(jint)*word;}
JNIEXPORT void JNICALL Java_org_circus_core_Pc1DirectSound_snapshotLockNative(JNIEnv *env,jclass owner,jlong handle,jboolean enter){Context *c=context(env,handle);(void)owner;if(!c)return;if(enter){if(c->snapshotDepth){fail(env,"PC1 recursive backend snapshot lock");return;}AcquireSRWLockExclusive(&registryLock);c->snapshotDepth=1;}else{if(c->snapshotDepth!=1){fail(env,"PC1 backend snapshot lock not owned");return;}c->snapshotDepth=0;ReleaseSRWLockExclusive(&registryLock);}}
JNIEXPORT jintArray JNICALL Java_org_circus_core_Pc1DirectSound_deviceNative(JNIEnv *env,jclass owner,jlong handle){
    Context *c=context(env,handle);jint values[15];(void)owner;if(!c)return NULL;values[0]=(jint)c->createHr;values[1]=(jint)c->cooperativeHr;values[2]=(jint)c->primaryHr;values[3]=(jint)c->formatHr;values[4]=c->ready;values[5]=c->failed;values[6]=(jint)c->capsHr;values[7]=(jint)c->caps.dwFlags;values[8]=(jint)c->caps.dwMinSecondarySampleRate;values[9]=(jint)c->caps.dwMaxSecondarySampleRate;values[10]=(jint)c->caps.dwPrimaryBuffers;values[11]=(jint)c->deviceIdentityHr;values[12]=(jint)GetACP();values[13]=c->device!=NULL;values[14]=c->primary!=NULL;return integers(env,values,15);
}
JNIEXPORT void JNICALL Java_org_circus_core_Pc1DirectSound_restoreInitializationNative(JNIEnv *env,jclass owner,jlong handle,jintArray info){Context *c=context(env,handle);jint v[15];(void)owner;if(!c)return;if(!info||(*env)->GetArrayLength(env,info)!=15){fail(env,"PC1 initialization restore record");return;}(*env)->GetIntArrayRegion(env,info,0,15,v);if(v[13]!=(c->device!=NULL)||v[14]!=(c->primary!=NULL)){fail(env,"PC1 initialization physical presence differs");return;}c->createHr=v[0];c->cooperativeHr=v[1];c->primaryHr=v[2];c->formatHr=v[3];c->ready=v[4];c->failed=v[5];c->capsHr=v[6];c->caps.dwFlags=v[7];c->caps.dwMinSecondarySampleRate=v[8];c->caps.dwMaxSecondarySampleRate=v[9];c->caps.dwPrimaryBuffers=v[10];c->deviceIdentityHr=v[11];}
JNIEXPORT jbyteArray JNICALL Java_org_circus_core_Pc1DirectSound_identityNative(JNIEnv *env,jclass owner,jlong handle){
    Context *c=context(env,handle);DWORD size=0;HRESULT hr;BYTE *packet;jbyteArray out;(void)owner;if(!c)return NULL;
    if(c->device&&c->deviceIdentityHr==NOT_CALLED)c->deviceIdentityHr=GetDeviceID(&DSDEVID_DefaultPlayback,&c->actualDevice);
    if(c->primary){hr=IDirectSoundBuffer_GetFormat(c->primary,NULL,0,&size);if(hr!=DS_OK||size>65536){fail(env,"PC1 audio primary format identity unavailable");return NULL;}}
    packet=(BYTE *)calloc(1,(size_t)size+16);if(!packet){fail(env,"PC1 audio identity allocation");return NULL;}memcpy(packet,&c->actualDevice,16);
    if(size){hr=IDirectSoundBuffer_GetFormat(c->primary,(LPWAVEFORMATEX)(packet+16),size,NULL);if(hr!=DS_OK){free(packet);fail(env,"PC1 audio primary format identity read failed");return NULL;}}
    out=(*env)->NewByteArray(env,(jsize)size+16);if(out)(*env)->SetByteArrayRegion(env,out,0,(jsize)size+16,(const jbyte *)packet);free(packet);return out;
}
JNIEXPORT jint JNICALL Java_org_circus_core_Pc1DirectSound_createNative(JNIEnv *env,jclass owner,jlong handle,jint channel,jint flags,jint bytes,jbyteArray rawFormat){
    Context *c=context(env,handle);Buffer *b;DSBUFFERDESC desc;HRESULT hr;(void)owner;if(!c)return(jint)E_FAIL;b=buffer(env,c,channel);if(!b)return(jint)E_INVALIDARG;
    if(!rawFormat||(*env)->GetArrayLength(env,rawFormat)!=18){fail(env,"PC1 audio WAVEFORMATEX requires eighteen known bytes");return(jint)E_INVALIDARG;}
    b->bytes=(DWORD)bytes;b->flags=(DWORD)flags;memset(&b->format,0,sizeof(b->format));(*env)->GetByteArrayRegion(env,rawFormat,0,18,(jbyte *)&b->format);if((*env)->ExceptionCheck(env))return(jint)E_FAIL;
    if(!c->device)return(jint)DSERR_UNINITIALIZED;memset(&desc,0,sizeof(desc));desc.dwSize=sizeof(desc);desc.dwFlags=b->flags;desc.dwBufferBytes=b->bytes;desc.lpwfxFormat=&b->format;
    {Retired *old=reserveRetired(env,(IUnknown *)b->object,2);if((*env)->ExceptionCheck(env))return(jint)E_FAIL;hr=IDirectSound_CreateSoundBuffer(c->device,&desc,&b->object,NULL);retainOverwritten(c,old,(IUnknown *)b->object,hr);}return(jint)hr;
}
JNIEXPORT jintArray JNICALL Java_org_circus_core_Pc1DirectSound_writeNative(JNIEnv *env,jclass owner,jlong handle,jint channel,jint offset,jbyteArray input,jint start,jint length){
    Context *c=context(env,handle);Buffer *b;void *one=NULL,*two=NULL;DWORD n1=0,n2=0;HRESULT lock,unlock=NOT_CALLED;jint values[4];(void)owner;if(!c)return NULL;b=buffer(env,c,channel);if(!b)return NULL;
    if(!input||start<0||length<0||start>(*env)->GetArrayLength(env,input)-length){fail(env,"PC1 audio PCM copy source boundary");return NULL;}
    if(!b->object){values[0]=(jint)DSERR_INVALIDCALL;values[1]=(jint)NOT_CALLED;values[2]=values[3]=0;return integers(env,values,4);}
    lock=IDirectSoundBuffer_Lock(b->object,(DWORD)offset,(DWORD)length,&one,&n1,&two,&n2,0);
    if(lock==DS_OK){if((uint64_t)n1+(uint64_t)n2!=(uint64_t)(DWORD)length){IDirectSoundBuffer_Unlock(b->object,one,n1,two,n2);fail(env,"PC1 audio Lock regions do not match requested known PCM count");return NULL;}if(n1)(*env)->GetByteArrayRegion(env,input,start,(jsize)n1,(jbyte *)one);if(n2)(*env)->GetByteArrayRegion(env,input,start+(jint)n1,(jsize)n2,(jbyte *)two);unlock=IDirectSoundBuffer_Unlock(b->object,one,n1,two,n2);}
    values[0]=(jint)lock;values[1]=(jint)unlock;values[2]=(jint)n1;values[3]=(jint)n2;return integers(env,values,4);
}
JNIEXPORT jbyteArray JNICALL Java_org_circus_core_Pc1DirectSound_bytesNative(JNIEnv *env,jclass owner,jlong handle,jint channel){
    Context *c=context(env,handle);Buffer *b;DSBCAPS caps;void *one=NULL,*two=NULL;DWORD n1=0,n2=0;HRESULT hr;jbyteArray out;(void)owner;if(!c)return NULL;b=buffer(env,c,channel);if(!b)return NULL;if(!b->object){fail(env,"PC1 audio buffer bytes owner absent");return NULL;}memset(&caps,0,sizeof(caps));caps.dwSize=sizeof(caps);hr=IDirectSoundBuffer_GetCaps(b->object,&caps);if(hr!=DS_OK||caps.dwBufferBytes>0x7fffffffu){fail(env,"PC1 audio buffer actual caps unavailable/exceed Java backing");return NULL;}
    hr=IDirectSoundBuffer_Lock(b->object,0,caps.dwBufferBytes,&one,&n1,&two,&n2,0);if(hr!=DS_OK){fail(env,"PC1 audio snapshot Lock failed");return NULL;}if((ULONGLONG)n1+n2!=caps.dwBufferBytes){IDirectSoundBuffer_Unlock(b->object,one,n1,two,n2);fail(env,"PC1 audio actual buffer Lock region bound");return NULL;}out=(*env)->NewByteArray(env,(jsize)caps.dwBufferBytes);if(out){if(n1)(*env)->SetByteArrayRegion(env,out,0,(jsize)n1,(const jbyte *)one);if(n2)(*env)->SetByteArrayRegion(env,out,(jsize)n1,(jsize)n2,(const jbyte *)two);}hr=IDirectSoundBuffer_Unlock(b->object,one,n1,two,n2);if(hr!=DS_OK){fail(env,"PC1 audio snapshot Unlock failed");return NULL;}return out;
}
JNIEXPORT jintArray JNICALL Java_org_circus_core_Pc1DirectSound_queryNative(JNIEnv *env,jclass owner,jlong handle,jint channel,jint kind,jint initial){
    Context *c=context(env,handle);Buffer *b;DWORD a=(DWORD)initial,z=0xccccccccu;HRESULT hr=DSERR_INVALIDCALL;jint values[3];(void)owner;if(!c)return NULL;b=buffer(env,c,channel);if(!b)return NULL;
    if(kind==5){a=b->object!=NULL;hr=DS_OK;}
    else if(b->object){switch(kind){case 0:hr=IDirectSoundBuffer_GetCurrentPosition(b->object,&a,&z);break;case 1:hr=IDirectSoundBuffer_GetStatus(b->object,&a);break;case 2:hr=IDirectSoundBuffer_GetVolume(b->object,(LONG *)&a);break;case 3:hr=IDirectSoundBuffer_GetFrequency(b->object,&a);break;case 4:hr=IDirectSoundBuffer_GetPan(b->object,(LONG *)&a);break;default:fail(env,"PC1 audio query selector");return NULL;}}
    else{fail(env,"PC1 native COM NULL dereference in channel query");return NULL;}
    values[0]=(jint)hr;values[1]=(jint)a;values[2]=(jint)z;return integers(env,values,3);
}
JNIEXPORT jint JNICALL Java_org_circus_core_Pc1DirectSound_commandNative(JNIEnv *env,jclass owner,jlong handle,jint channel,jint operation,jint argument){
    Context *c=context(env,handle);Buffer *b;HRESULT hr=DSERR_INVALIDCALL;(void)owner;if(!c)return(jint)E_FAIL;b=buffer(env,c,channel);if(!b)return(jint)E_INVALIDARG;if(!b->object){fail(env,"PC1 native COM NULL dereference in channel command");return(jint)hr;}
    switch(operation){case 0:hr=IDirectSoundBuffer_Play(b->object,0,0,(DWORD)argument);if(hr==DS_OK)b->playFlags=(DWORD)argument;break;case 1:hr=IDirectSoundBuffer_Stop(b->object);break;case 2:{ULONG references;AcquireSRWLockExclusive(&registryLock);references=IDirectSoundBuffer_Release(b->object);b->object=NULL;ReleaseSRWLockExclusive(&registryLock);return(jint)references;}case 3:hr=IDirectSoundBuffer_SetCurrentPosition(b->object,(DWORD)argument);break;case 4:hr=IDirectSoundBuffer_SetVolume(b->object,(LONG)argument);break;case 5:hr=IDirectSoundBuffer_SetFrequency(b->object,(DWORD)argument);break;case 6:hr=IDirectSoundBuffer_SetPan(b->object,(LONG)argument);break;default:fail(env,"PC1 audio operation selector");return(jint)E_INVALIDARG;}return(jint)hr;
}
JNIEXPORT jint JNICALL Java_org_circus_core_Pc1DirectSound_mciNative(JNIEnv *env,jclass owner,jlong handle,jbyteArray raw,jboolean notify){
    Context *c=context(env,handle);jsize length;char *frame;MCIERROR error;(void)owner;if(!c)return(jint)MCIERR_INTERNAL;if(!raw){fail(env,"PC1 raw MCI command missing");return(jint)MCIERR_NULL_PARAMETER_BLOCK;}length=(*env)->GetArrayLength(env,raw);if(length>=512){fail(env,"PC1 MCI command exceeds known adjacent512B native stack owner");return(jint)MCIERR_INTERNAL;}frame=(char *)malloc(512);if(!frame){fail(env,"PC1 MCI command allocation");return(jint)MCIERR_OUT_OF_MEMORY;}(*env)->GetByteArrayRegion(env,raw,0,length,(jbyte *)frame);frame[length]=0;
    /*41C290/41C3B0 locals are adjacent command[256],response[256].
     *A long formatter result overlaps the response rather than gaining a
     *separate allocation. Output beyond this known frame stays a gate. */
    error=mciSendStringA(frame,frame+256,256,notify?c->window:NULL);free(frame);return(jint)error;
}
JNIEXPORT jintArray JNICALL Java_org_circus_core_Pc1DirectSound_timerNative(JNIEnv *env,jclass owner,jlong handle,jint operation,jint token,jint milliseconds){
    Context *c=context(env,handle);jint values[2];(void)owner;if(!c)return NULL;values[1]=0;
    switch(operation){
    case 0:c->userInterval=(DWORD)milliseconds;c->userDue=GetTickCount64()+(DWORD)milliseconds;c->userFirst=0;c->userTimer=SetTimer(c->window,(UINT_PTR)(uint32_t)token,(UINT)milliseconds,NULL);values[0]=(jint)c->userTimer;values[1]=c->userTimer?0:(jint)GetLastError();break;
    case 1:values[0]=KillTimer(c->window,(UINT_PTR)(uint32_t)token);values[1]=values[0]?0:(jint)GetLastError();if(c->userTimer==(UINT_PTR)(uint32_t)token)c->userTimer=0;break;
    case 2:AcquireSRWLockExclusive(&registryLock);c->mmTimer=setMmLocked(c,200,200,TIME_PERIODIC);(*c->mmStored)=c->mmTimer;c->mmDue=GetTickCount64()+200;values[0]=(jint)c->mmTimer;ReleaseSRWLockExclusive(&registryLock);break;
    case 3:{DWORD previous=(*c->mmStored);
        values[0]=(jint)(previous==0xffffffffu?TIMERR_NOERROR:timeKillEvent(previous));
        (*c->mmStored)=0xffffffffu;
        AcquireSRWLockExclusive(&registryLock);if(previous==c->mmTimer){c->mmStopEpoch++;if(values[0]==TIMERR_NOERROR)c->mmTimer=0;}if(values[0]==TIMERR_NOERROR&&previous!=0xffffffffu)forgetMm(c,previous);ReleaseSRWLockExclusive(&registryLock);
        /*41B990(0): no registry lock while waiting, nor channel rewrite.
         *41C1A0 already wrote43DBDC=-1 before this leaf when releasing. */
        while((*c->callbackBusy)!=0)Sleep(10);
        AcquireSRWLockExclusive(&registryLock);if(c->stream.source&&c->stream.source->hasWord&&c->dspPe){DWORD raw=dspPeWord(c,0x42ab1c);if(raw!=0xffffffffu){registeredClose(c,NULL,raw);dspPeStore(c,0x42ab1c,0xffffffffu);}}else closeSource(c->stream.source);ReleaseSRWLockExclusive(&registryLock);break;}
    default:fail(env,"PC1 audio timer operation selector");return NULL;
    }return integers(env,values,2);
}
JNIEXPORT void JNICALL Java_org_circus_core_Pc1DirectSound_pumpLegacyNative(JNIEnv *env,jclass owner,jlong handle){Context *c=context(env,handle);MSG message;(void)owner;if(!c)return;while(c->ownsWindow&&PeekMessageW(&message,c->window,0,0,PM_REMOVE)){TranslateMessage(&message);DispatchMessageW(&message);}}
JNIEXPORT jlongArray JNICALL Java_org_circus_core_Pc1DirectSound_drainNative(JNIEnv *env,jclass owner,jlong handle){
    Context *c=context(env,handle);MSG message;Event *e,*next;size_t count=0,at=0;jlong *values;jlongArray out;(void)owner;if(!c)return NULL;
    while(!c->snapshotDepth&&c->ownsWindow&&PeekMessageW(&message,c->window,0,0,PM_REMOVE)){TranslateMessage(&message);DispatchMessageW(&message);}
    AcquireSRWLockExclusive(&registryLock);if(c->queueFailure){ReleaseSRWLockExclusive(&registryLock);fail(env,"PC1 audio service event allocation failed; no silent event drop");return NULL;}for(e=c->head;e;e=e->next)count++;
    if(count>0x100000u){ReleaseSRWLockExclusive(&registryLock);fail(env,"PC1 audio pending service events exceed portable drain bound");return NULL;}values=(jlong *)malloc((count?count:1)*5*sizeof(jlong));if(!values){ReleaseSRWLockExclusive(&registryLock);fail(env,"PC1 audio drain allocation");return NULL;}
    for(e=c->head;e;e=next){next=e->next;values[at++]=e->kind;values[at++]=e->token;values[at++]=e->a;values[at++]=e->b;values[at++]=e->tick;free(e);}c->head=c->tail=NULL;ReleaseSRWLockExclusive(&registryLock);
    out=(*env)->NewLongArray(env,(jsize)at);if(out&&at)(*env)->SetLongArrayRegion(env,out,0,(jsize)at,values);free(values);return out;
}
JNIEXPORT jlong JNICALL Java_org_circus_core_Pc1DirectSound_tickNative(JNIEnv *env,jclass owner){(void)env;(void)owner;return(jlong)GetTickCount64();}
JNIEXPORT jlongArray JNICALL Java_org_circus_core_Pc1DirectSound_sourceOpenNative(JNIEnv *env,jclass owner,jlong handle,jbyteArray raw){
    Context *c=context(env,handle);SourceFile *s;char *path;jsize length;LARGE_INTEGER size;jlong values[3];jlongArray out;(void)owner;if(!c)return NULL;if(!raw){fail(env,"PC1 native audio file path missing");return NULL;}
    length=(*env)->GetArrayLength(env,raw);path=(char *)malloc((size_t)length+1);if(!path){fail(env,"PC1 native audio source path allocation");return NULL;}(*env)->GetByteArrayRegion(env,raw,0,length,(jbyte *)path);path[length]=0;
    if(memchr(path,0,(size_t)length)){free(path);fail(env,"PC1 native audio path prefix contains NUL");return NULL;}s=(SourceFile *)calloc(1,sizeof(SourceFile));if(!s){free(path);fail(env,"PC1 native audio source owner allocation");return NULL;}
    s->file=CreateFileA(path,GENERIC_READ,FILE_SHARE_READ,NULL,OPEN_EXISTING,0,NULL);s->path=path;values[0]=0;values[1]=0;values[2]=0;
    if(s->file==INVALID_HANDLE_VALUE){values[1]=(jlong)GetLastError();free(s->path);free(s);}
    else if(!GetFileSizeEx(s->file,&size)){values[1]=(jlong)GetLastError();CloseHandle(s->file);free(s->path);free(s);}
    else{s->id=++c->nextSource;s->size=(ULONGLONG)size.QuadPart;s->closeResult=-1;s->next=c->sources;c->sources=s;values[0]=s->id;values[2]=size.QuadPart;}
    out=(*env)->NewLongArray(env,3);if(out)(*env)->SetLongArrayRegion(env,out,0,3,values);return out;
}
JNIEXPORT jbyteArray JNICALL Java_org_circus_core_Pc1DirectSound_sourceReadNative(JNIEnv *env,jclass owner,jlong handle,jlong id,jlong offset,jint requested){
    Context *c=context(env,handle);SourceFile *s;LARGE_INTEGER at;BYTE *bytes;DWORD count=0;BOOL ok;jbyteArray out;(void)owner;if(!c)return NULL;s=sourceFile(c,id);if(!s||s->file==INVALID_HANDLE_VALUE||offset<0||requested<0){fail(env,"PC1 audio file read has no owned descriptor/range");return NULL;}
    if(c->stream.source==s&&((int)SW(c,4))!=-1){fail(env,"PC1 audio parser cannot move an active stream file cursor");return NULL;}s->requested=(DWORD)requested;s->count=s->error=0;at.QuadPart=offset;
    if(!SetFilePointerEx(s->file,at,NULL,FILE_BEGIN)){s->error=GetLastError();return(*env)->NewByteArray(env,0);}s->position=(ULONGLONG)offset;bytes=(BYTE *)malloc(requested?(size_t)requested:1);if(!bytes){fail(env,"PC1 audio file read allocation");return NULL;}
    ok=ReadFile(s->file,bytes,(DWORD)requested,&count,NULL);s->count=count;if(!ok)s->error=GetLastError();s->position+=count;out=(*env)->NewByteArray(env,(jsize)count);if(out&&count)(*env)->SetByteArrayRegion(env,out,0,(jsize)count,(const jbyte *)bytes);free(bytes);return out;
}
JNIEXPORT jlongArray JNICALL Java_org_circus_core_Pc1DirectSound_sourceInfoNative(JNIEnv *env,jclass owner,jlong handle,jlong id){
    Context *c=context(env,handle);SourceFile *s;jlong values[9];jlongArray out;(void)owner;if(!c)return NULL;s=sourceFile(c,id);if(!s){fail(env,"PC1 audio source identity absent");return NULL;}if(!c->snapshotDepth)AcquireSRWLockExclusive(&registryLock);values[0]=s->id;values[1]=(jlong)s->size;values[2]=(jlong)s->position;values[3]=s->requested;values[4]=s->count;values[5]=s->error;values[6]=s->file!=INVALID_HANDLE_VALUE;values[7]=s->closeResult;values[8]=s->closeError;if(!c->snapshotDepth)ReleaseSRWLockExclusive(&registryLock);out=(*env)->NewLongArray(env,9);if(out)(*env)->SetLongArrayRegion(env,out,0,9,values);return out;
}
JNIEXPORT void JNICALL Java_org_circus_core_Pc1DirectSound_sourceCloseNative(JNIEnv *env,jclass owner,jlong handle,jlong id){Context *c=context(env,handle);SourceFile *s;(void)owner;if(!c)return;s=sourceFile(c,id);if(!s){fail(env,"PC1 audio source identity absent");return;}AcquireSRWLockExclusive(&registryLock);if(c->stream.source==s&&((int)SW(c,4))!=-1){ReleaseSRWLockExclusive(&registryLock);fail(env,"PC1 active stream file requires closeStream owner");return;}closeSource(s);ReleaseSRWLockExclusive(&registryLock);}
JNIEXPORT jlongArray JNICALL Java_org_circus_core_Pc1DirectSound_sourceWordNative(JNIEnv *env,jclass owner,jlong handle,jlong id,jboolean publish,jint word){Context *c=context(env,handle);SourceFile *s,*other;jlong values[4];jlongArray out;(void)owner;if(!c)return NULL;s=sourceFile(c,id);if(!s){fail(env,"PC1 FILE registry source identity absent");return NULL;}if(!c->snapshotDepth)AcquireSRWLockExclusive(&registryLock);if(publish){if(word==0||word==-1){if(!c->snapshotDepth)ReleaseSRWLockExclusive(&registryLock);fail(env,"PC1 FILE registry cannot publish NULL/invalid sentinel");return NULL;}for(other=c->sources;other;other=other->next)if(other!=s&&other->hasWord&&(other->rawWord&~3u)==((DWORD)word&~3u)&&other->file!=INVALID_HANDLE_VALUE){if(!c->snapshotDepth)ReleaseSRWLockExclusive(&registryLock);fail(env,"PC1 FILE registry aliases two distinct live descriptors");return NULL;}s->rawWord=(DWORD)word;s->hasWord=s->wordAdopted=1;}values[0]=(jlong)(intptr_t)s->file;values[1]=s->rawWord;values[2]=s->hasWord;values[3]=s->wordAdopted;if(!c->snapshotDepth)ReleaseSRWLockExclusive(&registryLock);out=(*env)->NewLongArray(env,4);if(out)(*env)->SetLongArrayRegion(env,out,0,4,values);return out;}
JNIEXPORT void JNICALL Java_org_circus_core_Pc1DirectSound_prepareDecoderNative(JNIEnv *env,jclass owner,jlong handle,jlong sourceId,jint bytes,jint mode,jint channels,jint packed,jint extra){
    Context *c=context(env,handle);SourceFile *source;(void)owner;if(!c)return;source=sourceFile(c,sourceId);if(!source||source->file==INVALID_HANDLE_VALUE){fail(env,"PC1 decoder preparation requires its open source");return;}
    if(mode!=0&&mode!=1&&mode!=2){fail(env,"PC1 decoder preparation mode contract");return;}if(mode==1&&(!source->hasWord||!c->dspPe||(!c->dspExtraBuffer&&!c->physicalBound))){fail(env,"PC1 compressed stream requires same-world FILE/PE/Audio owners");return;}if(source->hasWord&&!c->dspPe){fail(env,"PC1 registered FILE stream requires canonical PE rawword owner");return;}AcquireSRWLockExclusive(&registryLock);c->stream.source=source;c->stream.decodedCursor=0;
    /*41D1E0/38E/432/708: remaining,FILE,mode in this order. Mode0
     *does not write BC5DA4. Mode2 preserves the reserved delta words. */
    (*c->stream.remaining)=(DWORD)bytes;if(source->hasWord)dspPeStore(c,0x42ab1c,source->rawWord);(*c->stream.mode)=(DWORD)mode;
    if(mode==1){DWORD n;(*c->stream.channels)=0;*c->dspStreamingExtra=(DWORD)extra;dspInitLz(c,(DWORD)packed);for(n=0;n<16;n++)((volatile DWORD *)c->stream.scratch)[n]=0;}
    else if(mode==2){volatile DWORD *delta=c->stream.delta;delta[0]=0;(*c->stream.channels)=(DWORD)channels;delta[1]=0;delta[3]=0;delta[4]=0;}ReleaseSRWLockExclusive(&registryLock);
}
JNIEXPORT jintArray JNICALL Java_org_circus_core_Pc1DirectSound_bindStreamNative(JNIEnv *env,jclass owner,jlong handle,jint channel,jlong sourceId,jint bytes,jbyteArray memory,jint unit,jboolean automatic){
    Context *c=context(env,handle);SourceFile *source=NULL;Stream *s;Buffer *b;BYTE *data=NULL;jsize length=0;int result;jint values[5];(void)owner;if(!c)return NULL;b=buffer(env,c,channel);if(!b)return NULL;
    if(!b->object||bytes<0||unit<1||unit>0x4000){fail(env,"PC1 stream requires an actual buffer and native packet bound");return NULL;}if(sourceId){source=sourceFile(c,sourceId);if(!source||source->file==INVALID_HANDLE_VALUE){fail(env,"PC1 stream source descriptor absent");return NULL;}}
    else if(memory){length=(*env)->GetArrayLength(env,memory);data=(BYTE *)malloc(length?(size_t)length:1);if(!data){fail(env,"PC1 stream immutable PCM backing allocation");return NULL;}(*env)->GetByteArrayRegion(env,memory,0,length,(jbyte *)data);}
    else{fail(env,"PC1 stream file or immutable PCM owner required");return NULL;}
    AcquireSRWLockExclusive(&registryLock);s=&c->stream;
    free(s->memory);s->memory=data;s->memoryBytes=(DWORD)length;s->source=source;SW(c,4)=(DWORD)channel;SW(c,2)=s->error=0;if(!c->streamBuffer&&!c->physicalBound)SW(c,0)=(DWORD)bytes;if(!source)(*s->remaining)=(DWORD)bytes;SW(c,1)=s->decodedCursor=0;s->unit=(DWORD)unit;s->lastCursor=s->lastCount=s->lastLock=s->lastUnlock=0;
    result=fillStreamLocked(c,0,0,0);if(SW(c,2)!=2&&s->error==0&&automatic){c->mmTimer=setMmLocked(c,200,200,TIME_PERIODIC);(*c->mmStored)=c->mmTimer;c->mmDue=GetTickCount64()+200;}
    values[0]=result;values[1]=SW(c,2);values[2]=(jint)SW(c,1);values[3]=s->error;values[4]=(jint)(*c->mmStored);ReleaseSRWLockExclusive(&registryLock);return integers(env,values,5);
}
JNIEXPORT jintArray JNICALL Java_org_circus_core_Pc1DirectSound_serviceStreamNative(JNIEnv *env,jclass owner,jlong handle,jboolean periodic,jboolean overrideCursor,jint cursor){
    Context *c=context(env,handle);jint values[4];(void)owner;if(!c)return NULL;AcquireSRWLockExclusive(&registryLock);values[0]=fillStreamLocked(c,periodic!=0,overrideCursor!=0,(DWORD)cursor);values[1]=SW(c,2);values[2]=(jint)SW(c,1);values[3]=c->stream.error;ReleaseSRWLockExclusive(&registryLock);return integers(env,values,4);
}
JNIEXPORT jlongArray JNICALL Java_org_circus_core_Pc1DirectSound_streamInfoNative(JNIEnv *env,jclass owner,jlong handle){
    Context *c=context(env,handle);jlong values[16];jlongArray out;Stream *s;(void)owner;if(!c)return NULL;AcquireSRWLockExclusive(&registryLock);s=&c->stream;values[0]=((int)SW(c,4));values[1]=SW(c,2);values[2]=s->error;values[3]=SW(c,0);values[4]=(*s->remaining);values[5]=SW(c,1);values[6]=s->decodedCursor;values[7]=s->unit;values[8]=s->source?s->source->id:0;values[9]=s->memoryBytes;values[10]=s->lastCursor;values[11]=s->lastCount;values[12]=s->lastLock;values[13]=s->lastUnlock;values[14]=(*c->mmStored);values[15]=s->source?(jlong)s->source->position:0;ReleaseSRWLockExclusive(&registryLock);out=(*env)->NewLongArray(env,16);if(out)(*env)->SetLongArrayRegion(env,out,0,16,values);return out;
}
JNIEXPORT void JNICALL Java_org_circus_core_Pc1DirectSound_closeNative(JNIEnv *env,jclass owner,jlong handle){Context *c=context(env,handle);(void)owner;if(c)destroy(env,c);}

/* PDS2 is an explicit backend state, never a copied COM/HWND/HANDLE. Private
 *MM state is sampled under the same lock as the real refill callback. Hardware
 *continues advancing during the query; its write cursor is diagnostic only. */
typedef struct Packet {BYTE *data;size_t size,capacity,at;int error;} Packet;
static void putBytes(Packet *p,const void *data,size_t count){
    size_t capacity;BYTE *next;if(p->error)return;if(count>0x20000000u-p->size){p->error=1;return;}
    if(p->size+count>p->capacity){capacity=p->capacity?p->capacity:65536;while(capacity<p->size+count)capacity*=2;next=(BYTE *)realloc(p->data,capacity);if(!next){p->error=1;return;}p->data=next;p->capacity=capacity;}
    if(count)memcpy(p->data+p->size,data,count);p->size+=count;
}
static void put32(Packet *p,DWORD n){putBytes(p,&n,4);}
static void put64(Packet *p,ULONGLONG n){putBytes(p,&n,8);}
static const BYTE *take(Packet *p,size_t n){const BYTE *v;if(p->error||p->at>p->size||n>p->size-p->at){p->error=1;return NULL;}v=p->data+p->at;p->at+=n;return v;}
static DWORD get32(Packet *p){DWORD n=0;const BYTE *b=take(p,4);if(b)memcpy(&n,b,4);return n;}
static ULONGLONG get64(Packet *p){ULONGLONG n=0;const BYTE *b=take(p,8);if(b)memcpy(&n,b,8);return n;}
static DWORD delayUntil(ULONGLONG due,ULONGLONG now){return due>now?(DWORD)(due-now):0;}
JNIEXPORT jbyteArray JNICALL Java_org_circus_core_Pc1DirectSound_captureNative(JNIEnv *env,jclass owner,jlong handle){
    Context *c=context(env,handle);Packet p={0};Stream *s;SourceFile *file;Event *e;DWORD n,count;ULONGLONG now;MSG message;jbyteArray out=NULL;(void)owner;if(!c)return NULL;
    while(!c->snapshotDepth&&c->ownsWindow&&PeekMessageW(&message,c->window,0,0,PM_REMOVE)){TranslateMessage(&message);DispatchMessageW(&message);}
    if(!c->snapshotDepth)AcquireSRWLockExclusive(&registryLock);if(c->retired){if(!c->snapshotDepth)ReleaseSRWLockExclusive(&registryLock);fail(env,"PC1 overwritten still-live COM graph needs retained-reference snapshot reconstruction");return NULL;}
    {MmRegistration *mm=c->mmRegistrations;if(mm&&(mm->next||mm->timer!=c->mmTimer||(*c->mmStored)!=c->mmTimer)||!mm&&(c->mmTimer||(*c->mmStored)!=0xffffffffu&&(*c->mmStored)!=0)){if(!c->snapshotDepth)ReleaseSRWLockExclusive(&registryLock);fail(env,"PC1 raw MM token differs from retained actual timer graph; required snapshot reconstruction");return NULL;}}
    if(c->staged||c->queueFailure){if(!c->snapshotDepth)ReleaseSRWLockExclusive(&registryLock);fail(env,"PC1 backend cannot capture staged or dropped service events");return NULL;}
    now=GetTickCount64();put32(&p,0x36534450u);put32(&p,c->userTimer?1u:0u);put32(&p,c->userInterval);put32(&p,delayUntil(c->userDue,now));put32(&p,(*c->mmStored)==0xffffffffu?0u:(*c->mmStored)?1u:2u);put32(&p,delayUntil(c->mmDue,now));
    s=&c->stream;put32(&p,(DWORD)((int)SW(c,4)));put32(&p,(DWORD)SW(c,2));put32(&p,(DWORD)s->error);put32(&p,(DWORD)(*s->mode));put32(&p,(DWORD)(*s->channels));
    put32(&p,SW(c,0));put32(&p,(*s->remaining));put32(&p,SW(c,1));put32(&p,s->decodedCursor);put32(&p,s->unit);put32(&p,s->lastCursor);put32(&p,s->lastCount);put32(&p,s->lastLock);put32(&p,s->lastUnlock);put64(&p,s->source?(ULONGLONG)s->source->id:0);
    putBytes(&p,s->scratch,0x4000u);putBytes(&p,s->delta,24u);put32(&p,SW(c,3));put32(&p,(*c->packetCount));put32(&p,(*c->callbackBusy));put32(&p,(DWORD)c->userTimer);putBytes(&p,c->dspGlobals,0x38008u);putBytes(&p,c->dspLz,0x14030u);put32(&p,(*c->dspInputPointer));put32(&p,(*c->dspStaticExtra));put32(&p,s->memoryBytes);putBytes(&p,s->memory,s->memoryBytes);
    count=0;for(file=c->sources;file;file=file->next)count++;put32(&p,count);
    for(file=c->sources;file;file=file->next){size_t len=file->path?strlen(file->path):0;put64(&p,(ULONGLONG)file->id);put64(&p,file->size);put64(&p,file->position);put32(&p,file->requested);put32(&p,file->count);put32(&p,file->error);put32(&p,(DWORD)file->closeResult);put32(&p,file->closeError);put32(&p,file->file!=INVALID_HANDLE_VALUE);put32(&p,(DWORD)file->hasWord);put32(&p,file->rawWord);put32(&p,(DWORD)len);putBytes(&p,file->path,len);}
    for(n=0;n<CHANNELS;n++){
        Buffer *b=&c->buffers[n];DSBCAPS caps;DWORD position=0,write=0,status=0,freq=0;LONG volume=0,pan=0;void *one=NULL,*two=NULL;DWORD n1=0,n2=0;HRESULT hr;
        put32(&p,b->object?1u:0u);put32(&p,b->bytes);put32(&p,b->flags);put32(&p,b->playFlags);putBytes(&p,&b->format,18);if(!b->object)continue;
        memset(&caps,0,sizeof(caps));caps.dwSize=sizeof(caps);hr=IDirectSoundBuffer_GetCaps(b->object,&caps);if(hr!=DS_OK||caps.dwBufferBytes>0x10000000u){p.error=1;break;}
        if(IDirectSoundBuffer_GetCurrentPosition(b->object,&position,&write)!=DS_OK||IDirectSoundBuffer_GetStatus(b->object,&status)!=DS_OK||IDirectSoundBuffer_GetVolume(b->object,&volume)!=DS_OK||IDirectSoundBuffer_GetFrequency(b->object,&freq)!=DS_OK||IDirectSoundBuffer_GetPan(b->object,&pan)!=DS_OK||(status&DSBSTATUS_BUFFERLOST)){p.error=1;break;}
        put32(&p,caps.dwBufferBytes);put32(&p,position);put32(&p,write);put32(&p,status);put32(&p,(DWORD)volume);put32(&p,freq);put32(&p,(DWORD)pan);
        hr=IDirectSoundBuffer_Lock(b->object,0,caps.dwBufferBytes,&one,&n1,&two,&n2,0);if(hr!=DS_OK){p.error=1;break;}
        if((ULONGLONG)n1+n2!=caps.dwBufferBytes)p.error=1;else{putBytes(&p,one,n1);putBytes(&p,two,n2);}if(IDirectSoundBuffer_Unlock(b->object,one,n1,two,n2)!=DS_OK)p.error=1;
    }
    count=0;for(e=c->head;e;e=e->next)count++;put32(&p,count);for(e=c->head;e;e=e->next){put64(&p,(ULONGLONG)e->kind);put64(&p,(ULONGLONG)e->token);put64(&p,(ULONGLONG)e->a);put64(&p,(ULONGLONG)e->b);put64(&p,(ULONGLONG)e->tick);}if(!c->snapshotDepth)ReleaseSRWLockExclusive(&registryLock);
    if(p.error)fail(env,"PC1 backend capture failed actual buffer queries/Lock or packet bound; no partial snapshot published");else{out=(*env)->NewByteArray(env,(jsize)p.size);if(out)(*env)->SetByteArrayRegion(env,out,0,(jsize)p.size,(const jbyte *)p.data);}free(p.data);return out;
}
JNIEXPORT void JNICALL Java_org_circus_core_Pc1DirectSound_restoreNative(JNIEnv *env,jclass owner,jlong handle,jbyteArray raw){
    Context *c=context(env,handle);Packet p={0};Stream *s;DWORD n,count,mm,sourceCount;ULONGLONG streamSource;const BYTE *value;size_t len;LARGE_INTEGER size,position;SourceFile **tail;HRESULT failureHr=NOT_CALLED;DWORD winError=0;const char *phase="packet";(void)owner;if(!c)return;
    if(!raw){fail(env,"PC1 backend snapshot packet missing");return;}p.size=(size_t)(*env)->GetArrayLength(env,raw);if(p.size>0x20000000u){fail(env,"PC1 backend snapshot packet bound");return;}p.data=(BYTE *)malloc(p.size?p.size:1);if(!p.data){fail(env,"PC1 backend snapshot packet allocation");return;}(*env)->GetByteArrayRegion(env,raw,0,(jsize)p.size,(jbyte *)p.data);
    if(get32(&p)!=0x36534450u||c->sources||c->staged){p.error=1;goto done;}for(n=0;n<CHANNELS;n++)if(c->buffers[n].object){p.error=1;goto done;}c->staged=1;
    c->restoreUser=(int)get32(&p);c->restoreUserInterval=get32(&p);c->restoreUserDelay=get32(&p);mm=get32(&p);c->restoreMm=mm==1;c->restoreMmDelay=get32(&p);if(c->restoreUser<0||c->restoreUser>1||mm>2||c->restoreUserDelay>0x7fffffffu||c->restoreMmDelay>200){p.error=1;goto done;}(*c->mmStored)=mm==2?0:0xffffffffu;
    s=&c->stream;SW(c,4)=get32(&p);SW(c,2)=(int)get32(&p);s->error=(int)get32(&p);(*s->mode)=(int)get32(&p);(*s->channels)=(int)get32(&p);SW(c,0)=get32(&p);(*s->remaining)=get32(&p);SW(c,1)=get32(&p);s->decodedCursor=get32(&p);s->unit=get32(&p);s->lastCursor=get32(&p);s->lastCount=get32(&p);s->lastLock=get32(&p);s->lastUnlock=get32(&p);streamSource=get64(&p);
    if(s->error<0||s->error>3||s->unit>0x4000u){p.error=1;goto done;}
    value=take(&p,0x4000u);if(value)memcpy(s->scratch,value,0x4000u);value=take(&p,24u);if(value)memcpy(s->delta,value,24u);SW(c,3)=get32(&p);(*c->packetCount)=get32(&p);(*c->callbackBusy)=get32(&p);c->restoreUserToken=get32(&p);value=take(&p,0x38008u);if(value)memcpy(c->dspGlobals,value,0x38008u);value=take(&p,0x14030u);if(value)memcpy(c->dspLz,value,0x14030u);(*c->dspInputPointer)=get32(&p);(*c->dspStaticExtra)=get32(&p);s->memoryBytes=get32(&p);value=take(&p,s->memoryBytes);if(p.error)goto done;if(s->memoryBytes){s->memory=(BYTE *)malloc(s->memoryBytes);if(!s->memory){p.error=1;goto done;}memcpy(s->memory,value,s->memoryBytes);}
    sourceCount=get32(&p);if(sourceCount>100000u){p.error=1;goto done;}tail=&c->sources;
    for(n=0;n<sourceCount;n++){
        SourceFile *file=(SourceFile *)calloc(1,sizeof(SourceFile));DWORD opened;if(!file){p.error=1;goto done;}file->file=INVALID_HANDLE_VALUE;*tail=file;tail=&file->next;file->id=(jlong)get64(&p);file->size=get64(&p);file->position=get64(&p);file->requested=get32(&p);file->count=get32(&p);file->error=get32(&p);file->closeResult=(int)get32(&p);file->closeError=get32(&p);opened=get32(&p);file->hasWord=(int)get32(&p);file->rawWord=get32(&p);len=get32(&p);value=take(&p,len);
        if(p.error||opened>1||file->hasWord<0||file->hasWord>1||file->hasWord&&(file->rawWord==0||file->rawWord==0xffffffffu)||len>32767||!file->id||memchr(value,0,len)){p.error=1;goto done;}file->path=(char *)malloc(len+1);if(!file->path){p.error=1;goto done;}memcpy(file->path,value,len);file->path[len]=0;if(file->id>c->nextSource)c->nextSource=file->id;
        if(opened){phase="CreateFileA";file->file=CreateFileA(file->path,GENERIC_READ,FILE_SHARE_READ,NULL,OPEN_EXISTING,0,NULL);if(file->file==INVALID_HANDLE_VALUE){winError=GetLastError();p.error=1;goto done;}phase="GetFileSizeEx";if(!GetFileSizeEx(file->file,&size)){winError=GetLastError();p.error=1;goto done;}if((ULONGLONG)size.QuadPart!=file->size){phase="source_size_identity";p.error=1;goto done;}position.QuadPart=(LONGLONG)file->position;phase="SetFilePointerEx";if(!SetFilePointerEx(file->file,position,NULL,FILE_BEGIN)){winError=GetLastError();p.error=1;goto done;}}
        if((ULONGLONG)file->id==streamSource)s->source=file;
    }
    if(streamSource&&!s->source){p.error=1;goto done;}
    for(n=0;n<CHANNELS;n++){
        Buffer *b=&c->buffers[n];DWORD exists=get32(&p),bytes,play,write,status,volume,freq,pan;DSBUFFERDESC desc;DSBCAPS caps;void *one=NULL,*two=NULL;DWORD n1=0,n2=0;HRESULT hr;
        b->bytes=get32(&p);b->flags=get32(&p);b->playFlags=get32(&p);value=take(&p,18);if(value)memcpy(&b->format,value,18);if(p.error||exists>1){p.error=1;goto done;}if(!exists)continue;
        bytes=get32(&p);play=get32(&p);write=get32(&p);status=get32(&p);volume=get32(&p);freq=get32(&p);pan=get32(&p);value=take(&p,bytes);(void)write;if(p.error||bytes>0x10000000u||play>=bytes||(status&~(DSBSTATUS_PLAYING|DSBSTATUS_LOOPING))||!c->device){p.error=1;goto done;}
        memset(&desc,0,sizeof(desc));desc.dwSize=sizeof(desc);desc.dwFlags=b->flags;desc.dwBufferBytes=b->bytes;desc.lpwfxFormat=&b->format;phase="CreateSoundBuffer";hr=IDirectSound_CreateSoundBuffer(c->device,&desc,&b->object,NULL);failureHr=hr;if(hr!=DS_OK){p.error=1;goto done;}
        memset(&caps,0,sizeof(caps));caps.dwSize=sizeof(caps);phase="GetCaps/actual_size";failureHr=IDirectSoundBuffer_GetCaps(b->object,&caps);if(failureHr!=DS_OK||caps.dwBufferBytes!=bytes){p.error=1;goto done;}
        phase="Lock";hr=IDirectSoundBuffer_Lock(b->object,0,bytes,&one,&n1,&two,&n2,0);failureHr=hr;if(hr!=DS_OK){p.error=1;goto done;}if((ULONGLONG)n1+n2!=bytes)p.error=1;else{if(n1)memcpy(one,value,n1);if(n2)memcpy(two,value+n1,n2);}phase="Unlock";failureHr=IDirectSoundBuffer_Unlock(b->object,one,n1,two,n2);if(failureHr!=DS_OK)p.error=1;if(p.error)goto done;
        phase="SetVolume";failureHr=IDirectSoundBuffer_SetVolume(b->object,(LONG)volume);if(failureHr!=DS_OK){p.error=1;goto done;}phase="SetFrequency";failureHr=IDirectSoundBuffer_SetFrequency(b->object,freq);if(failureHr!=DS_OK){p.error=1;goto done;}phase="SetPan";failureHr=IDirectSoundBuffer_SetPan(b->object,(LONG)pan);if(failureHr!=DS_OK){p.error=1;goto done;}phase="SetCurrentPosition";failureHr=IDirectSoundBuffer_SetCurrentPosition(b->object,play);if(failureHr!=DS_OK){p.error=1;goto done;}c->restorePlay[n]=status&DSBSTATUS_PLAYING;c->restoreFlags[n]=b->playFlags;
    }
    count=get32(&p);if(count>1000000u){p.error=1;goto done;}for(n=0;n<count;n++){Event *event=(Event *)calloc(1,sizeof(Event));if(!event){p.error=1;goto done;}event->kind=(jlong)get64(&p);event->token=(jlong)get64(&p);event->a=(jlong)get64(&p);event->b=(jlong)get64(&p);event->tick=(jlong)get64(&p);if(c->tail)c->tail->next=event;else c->head=event;c->tail=event;}if(p.at!=p.size)p.error=1;
 done:free(p.data);if(p.error){char message[256];sprintf_s(message,sizeof(message),"PC1 backend staging failed phase=%s HRESULT=0x%08lx Win32=0x%08lx; actual prefix retained until staged owner closes",phase,(unsigned long)failureHr,(unsigned long)winError);fail(env,message);}
}
JNIEXPORT jintArray JNICALL Java_org_circus_core_Pc1DirectSound_adoptNative(JNIEnv *env,jclass owner,jlong handle){
    Context *c=context(env,handle);SourceFile *file;jint result[12];int n;(void)owner;if(!c)return NULL;if(!c->staged){fail(env,"PC1 backend has no staged output owner");return NULL;}
    for(file=c->sources;file;file=file->next)if(file->hasWord&&file->file!=INVALID_HANDLE_VALUE&&!file->wordAdopted){fail(env,"PC1 staged FILE descriptor requires same-domain registry adoption before output/timers");return NULL;}if((*c->stream.mode)!=0&&(*c->stream.mode)!=2&&(!c->dspPe||(!c->dspExtraBuffer&&!c->physicalBound))){fail(env,"PC1 staged compressed stream requires canonical PE/extra before output/timers");return NULL;}
    memset(result,0,sizeof(result));for(n=0;n<CHANNELS;n++)if(c->restorePlay[n]){result[n]=(jint)IDirectSoundBuffer_Play(c->buffers[n].object,0,0,c->restoreFlags[n]);if(result[n]!=DS_OK){char message[160];sprintf_s(message,sizeof(message),"PC1 staged output Play failed channel=%d HRESULT=0x%08lx after actual partial activation",n,(unsigned long)(DWORD)result[n]);fail(env,message);return NULL;}}
    if(c->restoreMm){c->mmFirst=1;AcquireSRWLockExclusive(&registryLock);c->mmTimer=setMmLocked(c,c->restoreMmDelay?c->restoreMmDelay:1,1,TIME_ONESHOT);(*c->mmStored)=c->mmTimer;ReleaseSRWLockExclusive(&registryLock);c->mmDue=GetTickCount64()+c->restoreMmDelay;result[10]=(jint)c->mmTimer;if(!c->mmTimer){fail(env,"PC1 staged stream timer activation failed");return NULL;}}
    if(c->restoreUser){c->userInterval=c->restoreUserInterval;c->userFirst=1;c->userTimer=SetTimer(c->window,c->restoreUserToken,c->restoreUserDelay?c->restoreUserDelay:1,NULL);c->userDue=GetTickCount64()+c->restoreUserDelay;result[11]=(jint)c->userTimer;if(!c->userTimer){fail(env,"PC1 staged timer2 activation failed");return NULL;}}
    c->staged=0;return integers(env,result,12);
}

JNIEXPORT jbyteArray JNICALL Java_org_circus_core_Pc1DirectSound_deltaForAudit(JNIEnv *env,jclass owner,jbyteArray encoded,jintArray state,jint channels){
    jsize count;DWORD words[6];BYTE *input,*output;jbyteArray out;(void)owner;
    if(!encoded||!state||(*env)->GetArrayLength(env,state)!=6){fail(env,"PC1 delta audit input/state bound");return NULL;}count=(*env)->GetArrayLength(env,encoded);if(count>8192){fail(env,"PC1 delta audit packet bound");return NULL;}input=(BYTE *)malloc(count?(size_t)count:1);output=(BYTE *)malloc(count?(size_t)count*2:1);if(!input||!output){free(input);free(output);fail(env,"PC1 delta audit allocation");return NULL;}
    (*env)->GetByteArrayRegion(env,encoded,0,count,(jbyte *)input);(*env)->GetIntArrayRegion(env,state,0,6,(jint *)words);deltaDecode(output,(DWORD)count,input,words,channels);(*env)->SetIntArrayRegion(env,state,0,6,(const jint *)words);out=(*env)->NewByteArray(env,count*2);if(out)(*env)->SetByteArrayRegion(env,out,0,count*2,(const jbyte *)output);free(input);free(output);return out;
}

/* Validation reads the actual staged, read-only A-handle, preserving all
 *native loader/file metadata and its cursor. This closes a hash/open race. */
/*A newly opened pending source may be hashed before decoder binding. The
 *current streaming source still requires the whole snapshot lock. This
 *validation read preserves both real file cursor and native IO metadata.*/
JNIEXPORT jbyteArray JNICALL Java_org_circus_core_Pc1DirectSound_sourcePeekNative(JNIEnv *env,jclass owner,jlong handle,jlong id,jlong offset,jint requested){
    Context *c=context(env,handle);SourceFile *file;LARGE_INTEGER zero,prior,at;BYTE *bytes;DWORD count=0,error=0;BOOL ok;jbyteArray out=NULL;(void)owner;if(!c)return NULL;file=sourceFile(c,id);
    if(!file||file->file==INVALID_HANDLE_VALUE||offset<0||requested<0||requested>65536||(!c->staged&&!c->snapshotDepth&&c->stream.source==file)){fail(env,"PC1 source identity read requires staged/pending file or snapshot lock");return NULL;}bytes=(BYTE *)malloc(requested?(size_t)requested:1);if(!bytes){fail(env,"PC1 staged source identity allocation");return NULL;}
    if(!c->snapshotDepth)AcquireSRWLockExclusive(&registryLock);zero.QuadPart=0;at.QuadPart=offset;ok=SetFilePointerEx(file->file,zero,&prior,FILE_CURRENT);if(ok){ok=SetFilePointerEx(file->file,at,NULL,FILE_BEGIN);if(ok)ok=ReadFile(file->file,bytes,(DWORD)requested,&count,NULL);if(!ok)error=GetLastError();if(!SetFilePointerEx(file->file,prior,NULL,FILE_BEGIN)){ok=FALSE;error=GetLastError();}}else error=GetLastError();if(!c->snapshotDepth)ReleaseSRWLockExclusive(&registryLock);
    if(!ok||count!=(DWORD)requested){char message[160];sprintf_s(message,sizeof(message),"PC1 staged actual source hash read failed Win32=0x%08lx count=%lu requested=%ld",(unsigned long)error,(unsigned long)count,(long)requested);fail(env,message);}else{out=(*env)->NewByteArray(env,requested);if(out&&requested)(*env)->SetByteArrayRegion(env,out,0,requested,(const jbyte *)bytes);}free(bytes);return out;
}

JNIEXPORT jbyteArray JNICALL Java_org_circus_core_Pc1DirectSound_sourcePathNative(JNIEnv *env,jclass owner,jlong handle,jlong id){Context *c=context(env,handle);SourceFile *file;size_t length;jbyteArray out;(void)owner;if(!c)return NULL;file=sourceFile(c,id);if(!file||!file->path){fail(env,"PC1 FILE path identity absent");return NULL;}length=strlen(file->path);out=(*env)->NewByteArray(env,(jsize)length);if(out&&length)(*env)->SetByteArrayRegion(env,out,0,(jsize)length,(const jbyte *)file->path);return out;}
