package org.circus.core;

import java.io.*;
import java.util.*;

/** Current PC1 native image-cache and frame-path producers. Resident names
 * belong to the image pool; they are not rebuilt from frame paths per draw.
 * Filesystem/CRX decoding and CG table matching remain required typed owners. */
public final class Pc1GraphicsResources implements Pc1DrawBatch.Resources {
    private static final int FORMAT=0x50314732,COUNT=100,NAME_BYTES=104;
    public static final int IMAGE_BASE=0x94c6d8,NAME_BASE=0x94c6f0;
    public static final int ACTOR1_MASK=0x97176c,ACTOR2_MASK=0x9a0778,ACTOR1_OVERLAY=0x971770,ACTOR2_OVERLAY=0x971768;
    private static final int[] PATHS={0x9d860c,0x9d868c,0x9d870c,0x9d888c,0x9d890c,0x9d898c};
    private static final int[] HANDLES={0x43d418,0x9b9be4,0x9b9be8,0x9cca90,0x9cca98,0x9cca94};
    private static final int[] REBUILD_OFFSETS={0x84,0x104,0x184,0x304,0x384,0x404};
    private static final byte[] SPECIAL={'e','f','f',' ','1','1'};
    /** Exactly one proved native pixel format, with its owned bottom-up bytes.
     * CRX header x/y are intentionally absent:4015D0 never applies them. */
    public static final class Source {
        private final NativeRgbImage rgb;
        private final NativeInverseAlphaImage alpha;
        private Source(NativeRgbImage rgb,NativeInverseAlphaImage alpha){this.rgb=rgb;this.alpha=alpha;}
        public static Source rgb(NativeRgbImage value)throws IOException{if(value==null)throw new IOException("PC1 resource RGB source missing");if(value.stride!=NativeRgbImage.nativeStride(value.width))throw new IOException("PC1 decoded resource has nonnative allocation stride");return new Source(value.copy(),null);}
        public static Source alpha(NativeInverseAlphaImage value)throws IOException{if(value==null)throw new IOException("PC1 resource alpha source missing");if(value.stride!=(long)value.width*4)throw new IOException("PC1 decoded resource has nonnative allocation stride");return new Source(null,value.copy());}
    }
    public interface Backend {
        /**41ADA0: compare this first-dot-truncated name to the native CG table
         * case-insensitively and set the matching persistent CG byte. This is
         * called before opening a file, including opens which later fail. */
        void visitCg(byte[] beforeFirstDot)throws IOException;
        /**41ADA0 receives the same live source, before401CC0 copies its name.
         *The legacy leaf only receives a first-dot prefix convenience value.*/
        default void visitCgSource(Pc1ByteSource source)throws IOException{ByteArrayOutputStream out=new ByteArrayOutputStream();for(int n=0;;n++){int b=source.unsignedByte(n)&255;if(n>=260)throw new IOException("PC1 CG-name local260 overflow");if(b==0||b=='.'){visitCg(out.toByteArray());return;}out.write(b);}}
        /**Required40E5A0/open/4015D0/close owner. Null means the native load
         * returned−1. No ARGB flattening, fallback image or implicit success.
         * Category/extension are exact bytes; name has only its terminal
         *@ or* modifier removed. The native path builder owns extension and
         *absolute-path handling. Return known typed pixels including padding. */
        Source readImage(byte[] category,byte[] name,byte[] extension)throws IOException;
        /** Addressed production boundary. The default exists only for older
         *narrow fixtures; eagerly materializing arguments cannot represent
         *40E5A0's conditional native read order. */
        default Source readImage(AddressedImage request)throws IOException{return readImage(request.legacyGlobalCString(request.category),request.legacyCString(request.name),request.legacyGlobalCString(request.extension));}
        /** Production decoders must publish directly into the one pool before
         *temporary allocation/read leaves. False is only a logical fixture. */
        default boolean usesCanonicalPool(){return false;}
        default int readImageInto(AddressedImage request,Pc1ImagePool pool,int mode)throws IOException{throw new IOException("PC1 canonical pool decoder backend missing");}
        /**401050 flags0 allocation initial bytes, NOT duplicate/source copy.
         *Null means actual allocation failure. No implicit zeroed image. */
        default Source allocateImage(int width,int height,int format)throws IOException{throw new IOException("PC1 401050 uninitialized image allocation backend missing");}
        /**Called after the native pool search, before the resident name copy.
         *A full pool leaves401050's allocation orphaned in the original. */
        default void allocationResult(Source source,int handle)throws IOException{}
    }
    /** Typed private local: no numeric coordinate may shadow a real global. */
    private static final class Local implements Pc1ByteSource.Pointer {
        private final byte[] bytes;private final boolean[] known;
        Local(int capacity){bytes=new byte[capacity];known=new boolean[capacity];}
        private int at(int offset)throws IOException{if(offset<0||offset>=bytes.length)throw new IOException("PC1 image filename outside unknown local"+bytes.length+" caller ABI");return offset;}
        public int unsignedByte(int offset)throws IOException{int n=at(offset);if(!known[n])throw new IOException("PC1 image filename reads unknown local"+bytes.length+" stack tail");return bytes[n]&255;}
        public void writeByte(int offset,int value)throws IOException{int n=at(offset);bytes[n]=(byte)value;known[n]=true;}
    }
    /** The image filename is a borrowed local260 Pointer. read/write and word
     *methods refer solely to real global addresses in the canonical State. */
    public static final class AddressedImage implements Pc1NativePaths.Memory {
        public final int category=Pc1ResourceLiterals.GRAPHICS_CATEGORY,extension=Pc1ResourceLiterals.GRAPHICS_EXTENSION,pathCapacity=260;
        public final Pc1ByteSource.Pointer name;
        private final Pc1Compositor.State globals;
        private AddressedImage(Pc1Compositor.State globals,Pc1ByteSource.Pointer name){this.globals=globals;this.name=name;}
        public int read(int address)throws IOException{if(globals instanceof Pc1InlineText.Memory)return ((Pc1InlineText.Memory)globals).read(address);if(globals instanceof Pc1NativePaths.Memory)return ((Pc1NativePaths.Memory)globals).read(address);throw new IOException("PC1 addressed image requires canonical byte memory at0x"+Integer.toHexString(address));}
        public void write(int address,int value)throws IOException{globals.writeByte(address,value);}
        public int readInt32(int address)throws IOException{return globals.readInt(address);}
        public void writeInt32(int address,int value)throws IOException{globals.writeInt(address,value);}
        public int classifySpace(int signedChar)throws IOException{if(globals instanceof Pc1NativePaths.Memory)return ((Pc1NativePaths.Memory)globals).classifySpace(signedChar);return Pc1NativePaths.classifySpaceRaw(this,signedChar);}
        public Pc1ByteSource categorySource()throws IOException{return Pc1NativePaths.pointer(this,category);}
        public Pc1ByteSource extensionSource()throws IOException{return Pc1NativePaths.pointer(this,extension);}
        private int legacyRead(int address)throws IOException{if(globals instanceof Pc1InlineText.Memory||globals instanceof Pc1NativePaths.Memory)return read(address);return globals.readInt(address&~3)>>>((address&3)*8)&255;}
        private byte[] legacyGlobalCString(final int address)throws IOException{return legacyCString(offset->legacyRead(address+offset));}
        private byte[] legacyCString(Pc1ByteSource source)throws IOException{ByteArrayOutputStream out=new ByteArrayOutputStream();for(int n=0;n<1024*1024;n++){int value=source.unsignedByte(n)&255;if(value==0)return out.toByteArray();out.write(value);}throw new IOException("PC1 legacy image fixture literal exceeds bounded memory");}
    }
    private final Pc1Compositor compositor;
    private final Pc1Compositor.State state;
    private final Backend backend;
    public Pc1GraphicsResources(Pc1Compositor compositor,Pc1Compositor.State state,Backend backend)throws IOException{
        if(compositor==null||state==null)throw new IOException("PC1 graphics resource canonical owners missing");this.compositor=compositor;this.state=state;this.backend=backend;
        // Only the original BSS name bytes start zero. Existing pixel imports
        // must bind their real resident names explicitly through name().
    }
    private static void handle(int value)throws IOException{if(value<0||value>=COUNT)throw new IOException("PC1 resident name handle outside100 slots");}
    private void same(Pc1Compositor value)throws IOException{if(value!=compositor)throw new IOException("PC1 resource owner bound to another compositor");}
    private Backend required()throws IOException{if(backend==null)throw new IOException("PC1 native image/CG backend missing");return backend;}
    private static byte[] cstring(byte[] value)throws IOException{if(value==null)throw new IOException("PC1 raw resource name missing");int n=0;while(n<value.length&&value[n]!=0)n++;return Arrays.copyOf(value,n);}
    private static byte[] cstring(byte[] value,String owner)throws IOException{int n=0;while(n<value.length&&value[n]!=0)n++;if(n==value.length)throw new IOException("PC1 "+owner+" string reaches unowned neighboring memory");return Arrays.copyOf(value,n);}
    private int unsignedByte(int address)throws IOException{if(state instanceof Pc1InlineText.Memory)return ((Pc1InlineText.Memory)state).read(address)&255;if(state instanceof Pc1NativePaths.Memory)return ((Pc1NativePaths.Memory)state).read(address)&255;int aligned=address&~3;return state.readInt(aligned)>>>((address&3)*8)&255;}
    private byte[] literal(int address)throws IOException{ByteArrayOutputStream out=new ByteArrayOutputStream();for(int n=0;n<1024*1024;n++){int b=unsignedByte(address+n);if(b==0)return out.toByteArray();out.write(b);}throw new IOException("PC1 graphics literal leaves known bounded memory");}
    private byte[] path(int slot)throws IOException{int at=PATHS[slot];byte[] value=new byte[128];for(int n=0;n<value.length;n++){int b=unsignedByte(at+n);if(b==0)return Arrays.copyOf(value,n);value[n]=(byte)b;}throw new IOException("PC1 frame path exceeds known128-byte cell");}
    private void writePath(int slot,byte[] value)throws IOException{for(int n=0;n<value.length;n++)state.writeByte(PATHS[slot]+n,value[n]&255);state.writeByte(PATHS[slot]+value.length,0);}
    /**Explicit native name producer, not frame-to-cache synchronization.
     *Copies only prefix+NUL and retains the old suffix.104 bytes are the real
     *name tail within each128-byte metadata record; native C-string writes
     *can alias following records in the same canonical pool. */
    public void name(int handle,byte[] rawName)throws IOException{handle(handle);try{compositor.pool().name(handle,cstring(rawName));}catch(IOException error){if(error.getMessage().contains("owned12800"))throw new IOException("PC1 resident name write reaches neighboring image metadata",error);throw error;}}
    public byte[] name(int handle)throws IOException{handle(handle);try{return compositor.pool().name(handle);}catch(IOException error){if(error.getMessage().contains("owned12800"))throw new IOException("PC1 resident name string reaches unowned neighboring memory",error);throw error;}}
    public byte[] rawNameBytes(int handle)throws IOException{handle(handle);return compositor.pool().rawName(handle);}
    public int unsignedNameByte(int handle,int offset)throws IOException{handle(handle);if(offset<0||offset>=NAME_BYTES)throw new IOException("PC1 resident name view reaches neighboring image metadata");return compositor.pool().readU8(handle*128+24+offset);}
    public void writeNameByte(int handle,int offset,int value)throws IOException{handle(handle);if(offset<0||offset>=NAME_BYTES)throw new IOException("PC1 resident name view reaches neighboring image metadata");compositor.pool().writeU8(handle*128+24+offset,value);}
    private static int extensionPoint(byte[] name,boolean slashAware){int at=0;for(int n=0;n<name.length;n++)if(name[n]=='.'||slashAware&&(name[n]=='/'||name[n]=='\\'))at=n;return name.length>0&&name[at]=='.'?at:name.length;}
    /** Explicit byte-array convenience; supplied suffix bytes stay live and
     *one terminal NUL is known. Raw callers use loadSource without a scan. */
    private static Pc1ByteSource value(final byte[] raw)throws IOException{if(raw==null)throw new IOException("PC1 raw resource name missing");return offset->{if(offset<0||offset>raw.length)throw new IOException("PC1 resource value reads unowned source suffix");return offset==raw.length?0:raw[offset]&255;};}
    private static Pc1ByteSource.Pointer slice(final Pc1ByteSource.Pointer pointer,final int delta){return new Pc1ByteSource.Pointer(){public int unsignedByte(int offset)throws IOException{return pointer.unsignedByte(delta+offset);}public int nativeWord(int offset)throws IOException{return pointer.nativeWord(delta+offset);}public void writeByte(int offset,int value)throws IOException{pointer.writeByte(delta+offset,value);}public void writeWord(int offset,int value)throws IOException{pointer.writeWord(delta+offset,value);}};}
    private Pc1ByteSource.Pointer poolName(final int handle){return new Pc1ByteSource.Pointer(){private int at(int offset){return(handle<<7)+24+offset;}public int unsignedByte(int offset)throws IOException{return compositor.pool().readU8(at(offset));}public int nativeWord(int offset)throws IOException{return compositor.pool().readI32(at(offset));}public void writeByte(int offset,int value)throws IOException{compositor.pool().writeU8(at(offset),value);}public void writeWord(int offset,int value)throws IOException{compositor.pool().writeI32(at(offset),value);}};}
    /** Native paired strcmp: left byte is loaded before its corresponding
     *right byte; a differing prefix never scans either remaining CString. */
    private static boolean equal(Pc1ByteSource left,Pc1ByteSource right)throws IOException{for(int at=0;;at+=2){int a=left.unsignedByte(at)&255,b=right.unsignedByte(at)&255;if(a!=b)return false;if(a==0)return true;a=left.unsignedByte(at+1)&255;b=right.unsignedByte(at+1)&255;if(a!=b)return false;if(a==0)return true;}}
    private static int imageExtension(Pc1ByteSource source)throws IOException{int at=0,last=0,current=source.unsignedByte(0)&255;if(current!=0)do{if(current=='.'||current=='\\'||current=='/')last=at;current=source.unsignedByte(at+1)&255;at++;}while(current!=0);return(source.unsignedByte(last)&255)=='.'?last:at;}
    /**401CC0 exact cache/CG/copy/admission order. No name snapshot before its
     *initial byte/cache comparisons/CG call, or after the actual load call. */
    @Override public int load(byte[] rawName,int allocationMode,Pc1Compositor compositor)throws IOException{return loadSource(value(rawName),allocationMode,compositor);}
    public int loadSource(Pc1ByteSource original,int allocationMode,Pc1Compositor compositor)throws IOException{
        same(compositor);if(original==null)throw new IOException("PC1 image live source missing");if((original.unsignedByte(0)&255)==0)return -1;
        for(int h=0;h<COUNT;h++)if(compositor.pool().occupied(h)&&equal(poolName(h),original))return h;
        required().visitCgSource(original);Local file=new Local(260);Pc1NativePaths.copy(file,original);
        int point=imageExtension(file),modifier=0,previous=point==0?0:file.unsignedByte(point-1);if(previous=='@'||previous=='*'){modifier=previous=='@'?1:2;Pc1NativePaths.copy(slice(file,point-1),slice(file,point));}
        AddressedImage request=new AddressedImage(state,file);int h;
        if(required().usesCanonicalPool())h=required().readImageInto(request,compositor.pool(),allocationMode);
        else{Source source=required().readImage(request);if(source==null)return -1;h=source.rgb!=null?compositor.allocateRgb(source.rgb,allocationMode):compositor.allocateAlpha(source.alpha,allocationMode);}if(h==-1)return -1;
        Pc1NativePaths.copy(poolName(h),original);if(modifier!=0)transform(h,modifier);int index=state.readInt(0x94c61c);int address=0x94c62c+index*4;if(state.readInt(address)!=0)transform(h,3);return h;
    }
    @Override public int duplicate(int handle,Pc1Compositor compositor)throws IOException{
        same(compositor);if(handle==-1||!compositor.loaded(handle))return -1;int h=compositor.duplicate(handle);if(h!=-1)name(h,name(handle));return h;
    }
    /**4020B0: allocate matching shape, then copy live WORK GBUF name only.
     *Source pixels are never read/copied. Heap allocation precedes pool scan. */
    public int createLike(int handle,Pc1Compositor compositor)throws IOException{same(compositor);return createLike(handle);}
    public int createLike(int handle)throws IOException{
        if(handle==-1)return -1;int record=IMAGE_BASE+(handle<<7),offset=record-IMAGE_BASE,format,height,width;
        if(offset>=0&&offset<COUNT*128){int source=offset>>>7;if(!compositor.loaded(source))return -1;format=compositor.format(source);height=compositor.height(source);width=compositor.width(source);}
        else{if(state.readInt(record)==0)return -1;format=state.readInt(record+20);height=state.readInt(record+16);width=state.readInt(record+12);}
        if(required().usesCanonicalPool()){int result=compositor.pool().allocate(width,height,format,0);if(result!=-1)name(result,literal(0x42a058));return result;}
        Source allocated=required().allocateImage(width,height,format);if(allocated==null)return -1;
        int actualFormat=allocated.rgb!=null?0:1,actualWidth=allocated.rgb!=null?allocated.rgb.width:allocated.alpha.width,actualHeight=allocated.rgb!=null?allocated.rgb.height:allocated.alpha.height;if(actualFormat!=format||actualWidth!=width||actualHeight!=height)throw new IOException("PC1 native image allocator returned different metadata");
        int result=allocated.rgb!=null?compositor.allocateRgb(allocated.rgb,0):compositor.allocateAlpha(allocated.alpha,0);required().allocationResult(allocated,result);if(result!=-1)name(result,literal(0x42a058));return result;
    }
    private static int pixel(Pc1HeapLease.Storage data,int at)throws IOException{if(data==null)throw new IOException("PC1 image modifier dereferences a null allocation");return data.read(at)&255;}
    private void transform(int handle,int effect)throws IOException{
        boolean alpha=compositor.format(handle)==1;int w=compositor.width(handle),h=compositor.height(handle),ps=alpha?4:3,stride=(w*ps+3)&~3;Pc1HeapLease.Storage data=compositor.pool().pixelStorage(handle);
        // All non1 formats follow the native RGB branch. These helpers
        //recompute stride from width, ignoring the raw record stride field.
        for(int row=0;row<h;row++){int phase=(row&1)*3;for(int x=0;x<w;x++){int p=row*stride+x*ps,base=p+(alpha?1:0);boolean skip;
            if(alpha)skip=pixel(data,p)==255;else{int word=pixel(data,p)|pixel(data,p+1)<<8;skip=word==255&&pixel(data,p+2)==255;}
            if(!skip)for(int c=0;c<3;c++){int value=pixel(data,base+c);if(effect==3)value=Math.max(0,Math.min(255,value+(x&1)+phase));else value=(value*3>>2)+(effect==1&&c==0||effect==2&&c==2?16:0);data.write(base+c,value);}phase=-phase;
        }}
    }
    /**411080; native−1 frees frame slots0..5 then every general image. */
    public void release(int slot,int mode)throws IOException{
        if(slot==-1){for(int n=0;n<6;n++)release(n,0);compositor.free(0);state.writeInt(0x9b96e0,-1);state.writeInt(0x9dbab8,-1);return;}slot(slot);compositor.free(state.readInt(HANDLES[slot]));state.writeByte(PATHS[slot],0);state.writeInt(HANDLES[slot],-1);
        if(slot==1||slot==2){int mask=slot==1?ACTOR1_MASK:ACTOR2_MASK,overlay=slot==1?ACTOR1_OVERLAY:ACTOR2_OVERLAY,overlayName=slot==1?0x9d878c:0x9d880c;compositor.free(state.readInt(mask));state.writeInt(mask,-1);if(mode==0){state.writeByte(overlayName,0);compositor.free(state.readInt(overlay));state.writeInt(overlay,-1);}}
        else if(slot==0){state.writeInt(Pc1Compositor.PENDING_BAKED,0);state.writeInt(Pc1Compositor.PENDING_COUNT,0);}
    }
    private static void slot(int value)throws IOException{if(value<0||value>=6)throw new IOException("PC1 resource frame slot reaches unknown pointer-table entry");}
    private byte[] overlayName(int slot)throws IOException{int address=slot==1?0x9d878c:0x9d880c;byte[] value=new byte[128];for(int n=0;n<value.length;n++){int b=unsignedByte(address+n);if(b==0)return Arrays.copyOf(value,n);value[n]=(byte)b;}throw new IOException("PC1 overlay path exceeds known128-byte cell");}
    /**4111A0 returns0 only for retained identical frame path, otherwise1,
     *including failed native loads. A frame handle is not reference-counted:
     *freeing one shared cache handle can invalidate another frame slot. */
    public int path(int slot,byte[] rawName)throws IOException{
        slot(slot);byte[] incoming=cstring(rawName);int handle=state.readInt(HANDLES[slot]);
        if((slot!=0||state.readInt(Pc1Compositor.PENDING_BAKED)==0)&&handle!=-1&&Arrays.equals(path(slot),incoming)){
            if(slot==1||slot==2){int overlay=slot==1?ACTOR1_OVERLAY:ACTOR2_OVERLAY;byte[] overlayName=overlayName(slot);if(overlayName.length!=0&&state.readInt(overlay)==-1)state.writeInt(overlay,load(overlayName,0,compositor));}return 0;
        }
        if(incoming.length>=128)throw new IOException("PC1 path producer local128 overflow");byte[] local=incoming.clone();release(slot,1);int loaded=Arrays.equals(local,SPECIAL)?duplicate(state.readInt(0x43d430),compositor):load(local,0,compositor);state.writeInt(HANDLES[slot],loaded);writePath(slot,local);
        if(slot==1||slot==2){int mask=slot==1?ACTOR1_MASK:ACTOR2_MASK,overlay=slot==1?ACTOR1_OVERLAY:ACTOR2_OVERLAY;int current=state.readInt(HANDLES[slot]);if(current==-1||!compositor.loaded(current)||compositor.format(current)!=1){int point=extensionPoint(local,false);if(point==0)throw new IOException("PC1 mask suffix reads unknown saved-register byte before local path");if(local[point-1]=='@')point--;if(point==0)throw new IOException("PC1 mask suffix reads unknown saved-register byte before local path");if(local[point-1]=='*')point--;byte[] name=Arrays.copyOf(local,point+1);name[point]='m';state.writeInt(mask,load(name,0,compositor));}byte[] overlayName=overlayName(slot);if(overlayName.length!=0)state.writeInt(overlay,load(overlayName,0,compositor));}
        else if(slot==0)state.writeInt(Pc1Compositor.PENDING_BAKED,0);return 1;
    }
    private Pc1ByteSource.Pointer global(final int address){return new Pc1ByteSource.Pointer(){public int unsignedByte(int offset)throws IOException{return Pc1GraphicsResources.this.unsignedByte(address+offset);}public int nativeWord(int offset)throws IOException{return state.readInt(address+offset);}public void writeByte(int offset,int value)throws IOException{state.writeByte(address+offset,value);}public void writeWord(int offset,int value)throws IOException{state.writeInt(address+offset,value);}};}
    private boolean caseEqual(Pc1ByteSource first,Pc1ByteSource second)throws IOException{int locale=state.readInt(Pc1CgVisit.CRT_LOCALE);for(int n=0;;n++){int a=first.unsignedByte(n)&255,b=second.unsignedByte(n)&255;if(a!=b){if(locale!=0)throw new IOException("PC1 graphics lookup CRT requires42534C owner");int lowerA=a>='A'&&a<='Z'?a+32:a,lowerB=b>='A'&&b<='Z'?b+32:b;if(lowerA!=lowerB)return false;}if(a==0)return true;}}
    /**410FF0 uses the suffix after the FIRST backslash, or the terminal NUL
     *when none exists, then walks43D0C0(namePointer,value) on the live bus.
     *This table is unrelated to401CC0's resident image-name cache. */
    public int lookupSource(Pc1ByteSource source)throws IOException{if(source==null)throw new IOException("PC1 graphics lookup requires live source");int at=0,current=source.unsignedByte(0)&255;if(current!=0)while(current!='\\'){current=source.unsignedByte(at+1)&255;at++;if(current==0)break;}if((source.unsignedByte(at)&255)=='\\')at++;Pc1ByteSource suffix=Pc1ByteSource.offset(source,at);if(state.readInt(0x43d0c0)==0)return -1;for(int row=0;;row++){int name=state.readInt(0x43d0c0+(row<<3));if(caseEqual(global(name),suffix))return state.readInt(0x43d0c4+(row<<3));if(state.readInt(0x43d0c8+(row<<3))==0)return -1;}}
    private int pathAddress(int slot)throws IOException{return state.readInt(0x43d478+(slot<<3));}
    private int handleAddress(int slot)throws IOException{return state.readInt(0x43d47c+(slot<<3));}
    private static long countSource(Pc1ByteSource source)throws IOException{for(long n=0;n<0xffffffffL;n++)if((source.unsignedByte((int)n)&255)==0)return n+1;return 0xffffffffL;}
    private static void forward(Pc1ByteSource.Pointer destination,Pc1ByteSource source,long count)throws IOException{long n=0;for(;n+4<=count;n+=4)destination.writeWord((int)n,source.nativeWord((int)n));for(;n<count;n++)destination.writeByte((int)n,source.unsignedByte((int)n)&255);}
    /**411080 with live pointer-table entries. No frame-table snapshot and no
     *synthetic range check replaces the actual read/failure position. */
    public void releaseNative(int slot,int mode)throws IOException{
        if(slot==-1){for(int n=0;n<6;n++)releaseNative(n,0);compositor.freeChecked(0);state.writeInt(0x9b96e0,-1);state.writeInt(0x9dbab8,-1);return;}
        compositor.freeChecked(state.readInt(handleAddress(slot)));int path=pathAddress(slot);state.writeByte(path,0);state.writeInt(handleAddress(slot),-1);
        if(slot==1||slot==2){int mask=slot==1?ACTOR1_MASK:ACTOR2_MASK,overlay=slot==1?ACTOR1_OVERLAY:ACTOR2_OVERLAY,overlayPath=slot==1?0x9d878c:0x9d880c;compositor.freeChecked(state.readInt(mask));state.writeInt(mask,-1);if(mode==0){int previous=state.readInt(overlay);state.writeByte(overlayPath,0);compositor.freeChecked(previous);state.writeInt(overlay,-1);}}
        else if(slot==0){state.writeInt(Pc1Compositor.PENDING_BAKED,0);state.writeInt(Pc1Compositor.PENDING_COUNT,0);}
    }
    /**4011B0 tests the raw bottom pointer before reading pixel format. This
     *entry requires real addressed metadata; opaque portable arrays use the
     *older byte[] convenience path rather than invented raw pointer values. */
    private int nativeFormat(int handle)throws IOException{if(handle==-1)return -1;int record=IMAGE_BASE+(handle<<7);if(state.readInt(record+4)==0)return -1;return state.readInt(record+20);}
    /**4111A0 production entry. The incoming Source is borrowed through the
     *early paired strcmp. Only the actual miss path copies into private128B.
     *Both table pointers are reloaded at their native consumption points. */
    public int pathSource(int slot,Pc1ByteSource incoming)throws IOException{
        if(incoming==null)throw new IOException("PC1 path requires live source");boolean compare=true;if(slot==0&&state.readInt(Pc1Compositor.PENDING_BAKED)!=0)compare=false;
        if(compare&&state.readInt(handleAddress(slot))!=-1&&equal(global(pathAddress(slot)),incoming)){
            if(slot==1||slot==2){int overlayPath=slot==1?0x9d878c:0x9d880c,overlay=slot==1?ACTOR1_OVERLAY:ACTOR2_OVERLAY;if(unsignedByte(overlayPath)!=0&&state.readInt(overlay)==-1)state.writeInt(overlay,loadSource(global(overlayPath),0,compositor));}return 0;
        }
        Local local=new Local(128);Pc1NativePaths.copy(local,incoming);releaseNative(slot,1);
        int loaded=equal(local,global(0x43d8dc))?duplicate(state.readInt(0x43d430),compositor):loadSource(local,0,compositor);
        state.writeInt(handleAddress(slot),loaded);long bytes=countSource(local);int destination=pathAddress(slot);forward(global(destination),local,bytes);
        int at=0,point=0,current=local.unsignedByte(0);if(current!=0)do{if(current=='.')point=at;current=local.unsignedByte(at+1);at++;}while(current!=0);if(local.unsignedByte(point)!='.')point=at;
        if(point==0)throw new IOException("PC1 mask suffix reads unknown saved-register byte before local path");if(local.unsignedByte(point-1)=='@')point--;if(point==0)throw new IOException("PC1 mask suffix reads unknown saved-register byte before local path");if(local.unsignedByte(point-1)=='*')point--;
        if(slot==1||slot==2){int handlePointer=state.readInt(slot==1?0x43d484:0x43d48c),currentHandle=state.readInt(handlePointer);if(nativeFormat(currentHandle)!=1){Pc1NativePaths.copy(slice(local,point),global(0x43d8d8));state.writeInt(slot==1?ACTOR1_MASK:ACTOR2_MASK,loadSource(local,0,compositor));}int overlayPath=slot==1?0x9d878c:0x9d880c;if(unsignedByte(overlayPath)!=0)state.writeInt(slot==1?ACTOR1_OVERLAY:ACTOR2_OVERLAY,loadSource(global(overlayPath),0,compositor));}
        else if(slot==0)state.writeInt(Pc1Compositor.PENDING_BAKED,0);return 1;
    }
    /**41A570 reads each source path immediately before that call, so aliases
     *with the live frame observe earlier calls' writes. Empty paths do nothing. */
    public void rebuild(Pc1NativeFrame source)throws IOException{if(source==null)throw new IOException("PC1 rebuild source frame missing");for(int n=0;n<6;n++){int at=REBUILD_OFFSETS[n];if(source.readU8(at)==0)continue;byte[] value=new byte[128];int length=0;while(length<128&&source.readU8(at+length)!=0){value[length]=(byte)source.readU8(at+length);length++;}if(length==128)throw new IOException("PC1 rebuild path exceeds known128-byte cell");path(n,Arrays.copyOf(value,length));}}
    public void save(DataOutput out)throws IOException{out.writeInt(FORMAT);}
    public static final class Image {private Image(){}}
    public static Image loadImage(DataInput in)throws IOException{if(in.readInt()!=FORMAT)throw new IOException("PC1 graphics resource format");return new Image();}
    public static Pc1GraphicsResources restore(Image image,Pc1Compositor compositor,Pc1Compositor.State state,Backend backend)throws IOException{if(image==null)throw new IOException("PC1 graphics resource state missing");return new Pc1GraphicsResources(compositor,state,backend);}
    public static Pc1GraphicsResources load(DataInput in,Pc1Compositor compositor,Pc1Compositor.State state,Backend backend)throws IOException{return restore(loadImage(in),compositor,state,backend);}
}
