/* Current d474DC2 CreateFontA/404220/4042C0 API boundary only.
 * Does not load or execute game code. Java performs the native pixel kernels.
 * Build against the local Windows SDK and JDK JNI headers. */
#define WIN32_LEAN_AND_MEAN
#include <windows.h>
#include <jni.h>
#include <stdint.h>
#include <stdlib.h>
#include <string.h>
#include <stdio.h>

#define CONTEXT_MAGIC 0x44324743u
typedef struct Context {
    DWORD magic;
    HDC dc;
    HBITMAP scratchBitmap;
    BYTE *scratch;
    HFONT fonts[5]; /*16,28,12,28,40; duplicate28 object matches startup*/
    HGDIOBJ prior;
    int actual;
} Context;
typedef char Dc2TextMetricSize[(sizeof(TEXTMETRICA)==56)?1:-1];
static void fail(JNIEnv *env,const char *message){jclass cls=(*env)->FindClass(env,"java/io/IOException");if(cls)(*env)->ThrowNew(env,cls,message);}
static void destroy(Context *c){int n;if(!c)return;if(c->dc&&c->prior)SelectObject(c->dc,c->prior);if(c->scratchBitmap)DeleteObject(c->scratchBitmap);if(c->dc)DeleteDC(c->dc);for(n=0;n<5;n++)if(c->fonts[n])DeleteObject(c->fonts[n]);c->magic=0;free(c);}
static Context *context(JNIEnv *env,jlong value){Context *c=(Context *)(intptr_t)value;if(!c||c->magic!=CONTEXT_MAGIC){fail(env,"DC2 GDI invalid native context");return NULL;}return c;}
static void put32(BYTE *b,int offset,int value){uint32_t u=(uint32_t)value;b[offset]=(BYTE)u;b[offset+1]=(BYTE)(u>>8);b[offset+2]=(BYTE)(u>>16);b[offset+3]=(BYTE)(u>>24);}
JNIEXPORT jlong JNICALL Java_org_circus_core_Dc2GdiGlyph_openNative(JNIEnv *env,jclass owner,jlong nativeWindow){
    static const int sizes[5]={16,28,12,28,40};
    static const unsigned char face[]={0xcb,0xce,0xcc,0xe5,0};
    Context *c=(Context *)calloc(1,sizeof(Context));HDC screen;BITMAPINFO bmi;int n;(void)owner;
    if(!c){fail(env,"DC2 GDI context allocation");return 0;}
    for(n=0;n<5;n++){c->fonts[n]=CreateFontA(sizes[n]*2,sizes[n],0,0,0,0,0,0,0x86,4,0x20,0,4,(LPCSTR)face);if(!c->fonts[n]){destroy(c);fail(env,"DC2 GDI CreateFontA failed");return 0;}}
    screen=GetDC((HWND)(intptr_t)nativeWindow);if(!screen){destroy(c);fail(env,"DC2 GDI GetDC failed");return 0;}c->dc=CreateCompatibleDC(screen);ReleaseDC((HWND)(intptr_t)nativeWindow,screen);
    if(!c->dc){destroy(c);fail(env,"DC2 GDI CreateCompatibleDC failed");return 0;}
    memset(&bmi,0,sizeof(bmi));bmi.bmiHeader.biSize=40;bmi.bmiHeader.biWidth=80;bmi.bmiHeader.biHeight=80;bmi.bmiHeader.biPlanes=1;bmi.bmiHeader.biBitCount=24;bmi.bmiHeader.biSizeImage=19200;
    c->scratchBitmap=CreateDIBSection(c->dc,&bmi,DIB_RGB_COLORS,(void **)&c->scratch,NULL,0);
    if(!c->scratchBitmap||!c->scratch){destroy(c);fail(env,"DC2 GDI CreateDIBSection failed");return 0;}
    /* Like current404220 initialization, select the28 font, NOT the newly allocated scratch DIB. */
    c->prior=SelectObject(c->dc,c->fonts[1]);if(!c->prior||c->prior==HGDI_ERROR){c->prior=NULL;destroy(c);fail(env,"DC2 GDI initial SelectObject failed");return 0;}
    c->actual=28;c->magic=CONTEXT_MAGIC;return(jlong)(intptr_t)c;
}
JNIEXPORT jbyteArray JNICALL Java_org_circus_core_Dc2GdiGlyph_outlineNative(JNIEnv *env,jclass owner,jlong handle,jint character,jint requested,jint ascent){
    Context *c=context(env,handle);GLYPHMETRICS gm;MAT2 matrix;DWORD needed,written;BYTE *packet;jbyteArray result;(void)owner;if(!c)return NULL;
    if(character<0||character>65535){fail(env,"DC2 GDI raw character outside native16-bit request");return NULL;}
    memset(&matrix,0,sizeof(matrix));matrix.eM11.value=1;matrix.eM22.value=1;memset(&gm,0,sizeof(gm));
    needed=GetGlyphOutlineA(c->dc,(UINT)character,GGO_BITMAP,&gm,0,NULL,&matrix);
    if(needed==GDI_ERROR){fail(env,"DC2 GDI GetGlyphOutlineA size failed");return NULL;}
    if(needed>19200){fail(env,"DC2 GDI mono glyph reaches unowned19200-byte scratch");return NULL;}
    written=GetGlyphOutlineA(c->dc,(UINT)character,GGO_BITMAP,&gm,needed,c->scratch,&matrix);
    if(written==GDI_ERROR||written!=needed){fail(env,"DC2 GDI GetGlyphOutlineA payload failed");return NULL;}
    packet=(BYTE *)malloc(19248);if(!packet){fail(env,"DC2 GDI packet allocation");return NULL;}
    put32(packet,0,0x314f4750);put32(packet,4,requested);put32(packet,8,c->actual);put32(packet,12,character);put32(packet,16,ascent);put32(packet,20,(int)gm.gmBlackBoxX);put32(packet,24,(int)gm.gmBlackBoxY);put32(packet,28,gm.gmptGlyphOrigin.x);put32(packet,32,gm.gmptGlyphOrigin.y);put32(packet,36,gm.gmCellIncX);put32(packet,40,gm.gmCellIncY);put32(packet,44,(int)needed);memcpy(packet+48,c->scratch,19200);
    result=(*env)->NewByteArray(env,19248);if(result)(*env)->SetByteArrayRegion(env,result,0,19248,(const jbyte *)packet);free(packet);return result;
}
JNIEXPORT jstring JNICALL Java_org_circus_core_Dc2GdiGlyph_environmentNative(JNIEnv *env,jclass owner,jlong handle){
    Context *c=context(env,handle);WCHAR face[LF_FACESIZE],text[256];(void)owner;if(!c)return NULL;memset(face,0,sizeof(face));GetTextFaceW(c->dc,LF_FACESIZE,face);
    _snwprintf_s(text,256,_TRUNCATE,L"ACP=%u;face=%s;actual=%d;GGO=BITMAP;CreateFontAcharset=134",GetACP(),face,c->actual);return(*env)->NewString(env,(const jchar *)text,(jsize)wcslen(text));
}
JNIEXPORT jint JNICALL Java_org_circus_core_Dc2GdiGlyph_selectedNative(JNIEnv *env,jclass owner,jlong handle){Context *c=context(env,handle);(void)owner;return c?c->actual:0;}
JNIEXPORT jint JNICALL Java_org_circus_core_Dc2GdiGlyph_ansiCodePageNative(JNIEnv *env,jclass owner,jlong handle){Context *c=context(env,handle);(void)owner;return c?(jint)GetACP():0;}
JNIEXPORT void JNICALL Java_org_circus_core_Dc2GdiGlyph_clearNative(JNIEnv *env,jclass owner,jlong handle,jint requested){Context *c=context(env,handle);uint32_t square=(uint32_t)requested*(uint32_t)requested,clear=square*8u;(void)owner;if(!c)return;memset(c->scratch,0,clear>19200?19200:clear);if(clear>19200)fail(env,"DC2 scratch clear reaches unowned neighbor after19200bytes");}
JNIEXPORT jbyteArray JNICALL Java_org_circus_core_Dc2GdiGlyph_selectMetricsNative(JNIEnv *env,jclass owner,jlong handle,jint requested){
    Context *c=context(env,handle);int index;TEXTMETRICA tm;jbyteArray result;(void)owner;if(!c)return NULL;
    index=requested==28?1:requested==40?4:requested==12?2:0;
    if(!SelectObject(c->dc,c->fonts[index])||!GetTextMetricsA(c->dc,&tm)){fail(env,"DC2 SelectObject/GetTextMetricsA failed");return NULL;}c->actual=requested==28?28:requested==40?40:requested==12?12:16;
    result=(*env)->NewByteArray(env,56);if(result)(*env)->SetByteArrayRegion(env,result,0,56,(const jbyte *)&tm);return result;
}
JNIEXPORT jbyteArray JNICALL Java_org_circus_core_Dc2GdiGlyph_scratchNative(JNIEnv *env,jclass owner,jlong handle){Context *c=context(env,handle);jbyteArray result;(void)owner;if(!c)return NULL;result=(*env)->NewByteArray(env,19200);if(result)(*env)->SetByteArrayRegion(env,result,0,19200,(const jbyte *)c->scratch);return result;}
JNIEXPORT void JNICALL Java_org_circus_core_Dc2GdiGlyph_restoreScratchNative(JNIEnv *env,jclass owner,jlong handle,jbyteArray bytes){Context *c=context(env,handle);(void)owner;if(!c)return;if(!bytes||(*env)->GetArrayLength(env,bytes)!=19200){fail(env,"DC2 scratch snapshot size");return;}(*env)->GetByteArrayRegion(env,bytes,0,19200,(jbyte *)c->scratch);}
JNIEXPORT jstring JNICALL Java_org_circus_core_Dc2GdiGlyph_faceNative(JNIEnv *env,jclass owner,jlong handle){Context *c=context(env,handle);WCHAR face[LF_FACESIZE];(void)owner;if(!c)return NULL;memset(face,0,sizeof(face));if(!GetTextFaceW(c->dc,LF_FACESIZE,face)){fail(env,"DC2 GDI selected face identity unavailable");return NULL;}return(*env)->NewString(env,(const jchar *)face,(jsize)wcslen(face));}
JNIEXPORT void JNICALL Java_org_circus_core_Dc2GdiGlyph_restoreSelectedNative(JNIEnv *env,jclass owner,jlong handle,jint selected){
    Context *c=context(env,handle);int index;HGDIOBJ previous;(void)owner;if(!c)return;
    if(selected!=12&&selected!=16&&selected!=28&&selected!=40){fail(env,"DC2 invalid saved font selection");return;}index=selected==28?1:selected==12?2:selected==40?4:0;previous=SelectObject(c->dc,c->fonts[index]);if(!previous||previous==HGDI_ERROR){fail(env,"DC2 restored font select failed");return;}c->actual=selected;
}
JNIEXPORT jbyteArray JNICALL Java_org_circus_core_Dc2GdiGlyph_fontDataNative(JNIEnv *env,jclass owner,jlong handle){
    Context *c=context(env,handle);DWORD size,read;BYTE *data;WCHAR face[LF_FACESIZE];jbyteArray result;(void)owner;if(!c)return NULL;
    size=GetFontData(c->dc,0,0,NULL,0);if(size==GDI_ERROR||size>32*1024*1024){fail(env,"DC2 GDI selected TrueType font identity unavailable");return NULL;}
    memset(face,0,sizeof(face));if(!GetTextFaceW(c->dc,LF_FACESIZE,face)){fail(env,"DC2 GDI selected face identity unavailable");return NULL;}
    data=(BYTE *)malloc((size_t)size+80);if(!data){fail(env,"DC2 GDI font identity allocation");return NULL;}put32(data,0,(int)GetACP());put32(data,4,c->actual);memcpy(data+8,face,64);put32(data,72,GetDeviceCaps(c->dc,LOGPIXELSX));put32(data,76,GetDeviceCaps(c->dc,LOGPIXELSY));read=GetFontData(c->dc,0,0,data+80,size);
    if(read!=size){free(data);fail(env,"DC2 GDI font identity read failed");return NULL;}result=(*env)->NewByteArray(env,(jsize)size+80);if(result)(*env)->SetByteArrayRegion(env,result,0,(jsize)size+80,(const jbyte *)data);free(data);return result;
}
JNIEXPORT void JNICALL Java_org_circus_core_Dc2GdiGlyph_closeNative(JNIEnv *env,jclass owner,jlong handle){(void)env;(void)owner;destroy((Context *)(intptr_t)handle);}
