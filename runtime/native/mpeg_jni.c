#include <jni.h>
#include <stdlib.h>
#include <stdint.h>
#include <math.h>
#define PL_MPEG_IMPLEMENTATION
#include "third_party/pl_mpeg.h"

static void fail(JNIEnv *e,const char *s){jclass c=(*e)->FindClass(e,"java/io/IOException");if(c)(*e)->ThrowNew(e,c,s);}
JNIEXPORT jlong JNICALL Java_org_circus_runtime_MpegDecoder_open(JNIEnv *e,jclass c,jstring path){
    (void)c;const char *p=(*e)->GetStringUTFChars(e,path,0);if(!p)return 0;plm_t *d=plm_create_with_filename(p);(*e)->ReleaseStringUTFChars(e,path,p);
    if(!d){fail(e,"Cannot open MPEG file");return 0;}int w=plm_get_width(d),h=plm_get_height(d),rate=plm_get_samplerate(d);double fps=plm_get_framerate(d);
    if(!plm_has_headers(d)||w<=0||h<=0||w>4096||h>2160||fps<1||fps>120||(plm_get_num_audio_streams(d)>0&&(rate<8000||rate>48000))){plm_destroy(d);fail(e,"Unsupported MPEG-1/MP2 headers");return 0;}
    return (jlong)(intptr_t)d;
}
JNIEXPORT void JNICALL Java_org_circus_runtime_MpegDecoder_info(JNIEnv *e,jclass c,jlong handle,jdoubleArray values){
    (void)c;plm_t *d=(plm_t*)(intptr_t)handle;if(!d||(*e)->GetArrayLength(e,values)!=6){fail(e,"Invalid MPEG info buffer");return;}
    jdouble v[]={plm_get_width(d),plm_get_height(d),plm_get_framerate(d),plm_get_samplerate(d),plm_get_num_audio_streams(d),plm_get_duration(d)};(*e)->SetDoubleArrayRegion(e,values,0,6,v);
}
JNIEXPORT jdouble JNICALL Java_org_circus_runtime_MpegDecoder_video(JNIEnv *e,jclass c,jlong handle,jintArray pixels){
    (void)c;plm_t *d=(plm_t*)(intptr_t)handle;if(!d){fail(e,"Closed MPEG decoder");return -1;}int w=plm_get_width(d),h=plm_get_height(d);
    if((*e)->GetArrayLength(e,pixels)!=w*h){fail(e,"Invalid video buffer");return -1;}plm_frame_t *f=plm_decode_video(d);if(!f)return -1;
    if(f->width!=(unsigned)w||f->height!=(unsigned)h){fail(e,"MPEG dimensions changed");return -1;}jint *out=(*e)->GetIntArrayElements(e,pixels,0);if(!out)return -1;
    for(int i=0;i<w*h;i++)out[i]=(jint)0xff000000;plm_frame_to_bgra(f,(uint8_t*)out,w*4);(*e)->ReleaseIntArrayElements(e,pixels,out,0);return f->time;
}
JNIEXPORT jdouble JNICALL Java_org_circus_runtime_MpegDecoder_audio(JNIEnv *e,jclass c,jlong handle,jshortArray samples){
    (void)c;plm_t *d=(plm_t*)(intptr_t)handle;if(!d||(*e)->GetArrayLength(e,samples)!=PLM_AUDIO_SAMPLES_PER_FRAME*2){fail(e,"Invalid audio buffer");return -1;}
    plm_samples_t *a=plm_decode_audio(d);if(!a)return -1;jshort out[PLM_AUDIO_SAMPLES_PER_FRAME*2];for(int i=0;i<PLM_AUDIO_SAMPLES_PER_FRAME*2;i++){float v=a->interleaved[i]*32767;out[i]=(jshort)fmaxf(-32768,fminf(32767,v));}(*e)->SetShortArrayRegion(e,samples,0,PLM_AUDIO_SAMPLES_PER_FRAME*2,out);return a->time;
}
JNIEXPORT void JNICALL Java_org_circus_runtime_MpegDecoder_close(JNIEnv *e,jclass c,jlong handle){(void)e;(void)c;if(handle)plm_destroy((plm_t*)(intptr_t)handle);}
JNIEXPORT jdouble JNICALL Java_org_circus_runtime_MpegDecoder_seek(JNIEnv *e,jclass c,jlong handle,jdouble seconds){
    (void)c;plm_t *d=(plm_t*)(intptr_t)handle;if(!d||!isfinite(seconds)||seconds<0||!plm_seek(d,seconds,TRUE)){fail(e,"Cannot seek MPEG movie");return -1;}return plm_get_time(d);
}
