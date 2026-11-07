package org.circus.core;

import java.io.*;
import java.util.*;

/** PC1 current CHS system.dat block454CD8..4553AB (1748 bytes).
 * Raw preservation, native read/write ordering and reviewed persistent setter
 * effects only; no Host/register/persistent-owner merge or audio execution.
 * Unknown fields, unused array cells and admitted file trailing stay intact. */
public final class Pc1NativeSettings {
    public static final int SIZE=0x6d4,GLOBAL_OFFSET=0x70,CG_OFFSET=0xd4,SCENE_OFFSET=0x2d4;
    public static final String CURRENT_CHS_PE_SHA256="7988971ff38f6b0fb2f21827b1ba38c3c7fe5cc824420b179ea9d9a838872057";
    public static final String CURRENT_CHS_DEFAULT_SHA256="9119ed38a87d97e1b74875e9aff495ed5a5f90ff8d5fe849224357a68849da39";
    private final byte[] original;
    private Pc1NativeSettings(byte[] bytes){original=bytes;}
    public enum Name {
        FAMILY(0x48),GIVEN(0x5c);
        public final int offset;
        Name(int offset){this.offset=offset;}
    }
    /** Named independent audio fields.99:5/+40 and99:6/+44 are NOT aliases
     *for VOICE_ENABLED/+38 or per-character portable voice preference arrays. */
    public enum AudioField {
        BGM_ENABLED(0x24),BGM_BACKEND(0x28),BGM_VOLUME(0x2c),SE_ENABLED(0x30),SE_VOLUME(0x34),VOICE_ENABLED(0x38),VOICE_VOLUME(0x3c);
        public final int offset;
        AudioField(int offset){this.offset=offset;}
    }
    /**402890 checks only read count; there is no signature/value validation.
     *Complete file backing survives even though native reads/writes just SIZE. */
    public static Pc1NativeSettings parse(byte[] bytes)throws IOException{
        if(bytes==null||bytes.length<SIZE)throw new IOException("PC1 native system.dat requires at least1748 bytes");return new Pc1NativeSettings(bytes.clone());
    }
    /** PE live454CD8 is zero-filled BSS before initialization; this is NOT the
     *402890 fallback/default template at42A078. No original JP PE is implied. */
    public static Pc1NativeSettings initialZeroImage(){return new Pc1NativeSettings(new byte[SIZE]);}
    /** Exact current readable CHS42A078 template. Remaining globals/CG/scenes
     *are zero in that template. Raw GBK names are kept as bytes, not Unicode. */
    public static Pc1NativeSettings currentChsDefaults(){
        byte[] bytes=new byte[SIZE];String prefix="0000000000000000040000000800000001000000010000000100000001000000010000000100000000000000190000000100000019000000010000001e0000000100000001000000b3afb2d600000000000000000000000000000000b4bfd2bb00000000000000000000000000000000";
        for(int n=0;n<prefix.length()/2;n++)bytes[n]=(byte)Integer.parseInt(prefix.substring(n*2,n*2+2),16);return new Pc1NativeSettings(bytes);
    }
    public int originalLength(){return original.length;}
    /** Archival bytes including trailing, not native4027E0 output length. */
    public byte[] originalBytes(){return original.clone();}
    /** Exactly native1748-byte output; this method performs no I/O. */
    public byte[] nativeBytes(){return Arrays.copyOf(original,SIZE);}
    /** Current native RAM view. No bank setter, persistence or audio effect. */
    int nativeByte(int offset)throws IOException{if(offset<0||offset>=SIZE)throw new IOException("PC1 native settings byte range");return original[offset]&255;}
    void nativeByte(int offset,int value)throws IOException{if(offset<0||offset>=SIZE)throw new IOException("PC1 native settings byte range");original[offset]=(byte)value;}
    public byte[] trailingBytes(){return Arrays.copyOfRange(original,SIZE,original.length);}
    public byte[] rawBytes(int offset,int length)throws IOException{range(offset,length);return Arrays.copyOfRange(original,offset,offset+length);}
    public int rawDword(int offset)throws IOException{range(offset,4);return intAt(original,offset);}
    public int audio(AudioField field)throws IOException{if(field==null)throw new IOException("PC1 missing native audio setting");return rawDword(field.offset);}
    /** All18 preference DWORDs, including fields without a99 mapping. */
    public int[] preferenceDwords(){int[] result=new int[18];for(int n=0;n<result.length;n++)result[n]=intAt(original,n*4);return result;}
    public byte[] nameCell(Name name)throws IOException{require(name);return rawBytes(name.offset,20);}
    /** Native strcpy/$ expansion has no20-byte cell guard. Scan all known
     *block backing, never unread file trailing; returned bytes omit the NUL. */
    public byte[] rawCString(Name name)throws IOException{
        require(name);int end=name.offset;while(end<SIZE&&original[end]!=0)end++;if(end==SIZE)throw new IOException("PC1 name C string exceeds owned1748-byte block");return Arrays.copyOfRange(original,name.offset,end);
    }
    /** Raw strcpy projection only. Input/UI glyph/encoding limits belong to
     *their input owner. Writes one NUL, retaining the stale suffix; known block
     *aliases across a cell are preserved rather than silently truncated. */
    public Pc1NativeSettings withNameCString(Name name,byte[] prefix)throws IOException{
        require(name);if(prefix==null)throw new IOException("PC1 missing native name prefix");for(byte value:prefix)if(value==0)throw new IOException("PC1 native name prefix contains NUL");range(name.offset,(long)prefix.length+1);byte[] next=original.clone();System.arraycopy(prefix,0,next,name.offset,prefix.length);next[name.offset+prefix.length]=0;return new Pc1NativeSettings(next);
    }
    /** Only the actual persistent99 mappings. Other99 fields are external
     *scalars/queries or native defaults and must be resolved by their owner. */
    public static int offset99(int index){
        switch(index){case 5:return 0x40;case 6:return 0x44;case 7:return 0x2c;case 8:return 0x34;case 9:return 0x3c;case 10:return 0x10;case 11:return 8;case 12:return 0x18;case 13:return 4;case 14:return 0x24;case 15:return 0x30;case 16:return 0xc;case 17:return 0x20;case 18:return 0x14;case 20:return 0x1c;default:return -1;}
    }
    public static int[] indices99(){return new int[]{5,6,7,8,9,10,11,12,13,14,15,16,17,18,20};}
    public static boolean owns99(int index){return offset99(index)>=0;}
    public int read99(int index)throws IOException{int offset=offset99(index);if(offset<0)throw new IOException("PC1 bank99:"+index+" is not owned by native system.dat");return rawDword(offset);}
    /**411910/411B10 do not bound individual byte banks. Permit every alias
     *whose final address stays in this owner, including signed index aliases;
     *never fabricate bytes beyond454CD8..4553AB. */
    public int readByteBank(int bank,int index)throws IOException{return original[byteBankOffset(bank,index)]&255;}
    private static int byteBankOffset(int bank,int index)throws IOException{
        int start=bank==100?GLOBAL_OFFSET:bank==97?CG_OFFSET:bank==98?SCENE_OFFSET:-1;if(start<0)throw new IOException("PC1 byte bank"+bank+" is not owned by native system.dat");long offset=(long)start+index;range(offset,1);return (int)offset;
    }
    public enum EffectKind { WRITE_SYSTEM_4027E0,REFRESH_VOLUMES_41CD00,STOP_MUSIC_41C650,REQUEST_MUSIC_41C4B0,ARM_AUDIO_TIMER_4070E0 }
    public static final class Effect {
        public final EffectKind kind;private final int[] arguments;
        private Effect(EffectKind kind,int... arguments){this.kind=kind;this.arguments=arguments.clone();}
        public int[] arguments(){return arguments.clone();}
    }
    /** Live/frame fields read only by99:14. They are not persistent settings. */
    public static final class WriteContext {
        public final int logicalMusic,bgmMode;
        public WriteContext(int logicalMusic,int bgmMode){this.logicalMusic=logicalMusic;this.bgmMode=bgmMode;}
    }
    public static final class ScriptWrite {
        /** Publish this new raw owner BEFORE submitting its ordered effects.
         *Native411B10's memory store occurs before the tailcalls below. */
        public final Pc1NativeSettings settings;public final List<Effect> effects;
        private ScriptWrite(Pc1NativeSettings settings,List<Effect> effects){this.settings=settings;this.effects=Collections.unmodifiableList(new ArrayList<>(effects));}
    }
    /** Pure411B10 persistent branches: DWORD stores do not clamp; byte stores
     *take low8. No files/audio/timers or unrelated Host fields are touched. */
    public ScriptWrite withScriptWrite(int bank,int index,int value,WriteContext context)throws IOException{
        byte[] next=original.clone();List<Effect> effects=new ArrayList<>();
        if(bank==99){int offset=offset99(index);if(offset<0)throw new IOException("PC1 bank99:"+index+" write is not owned by native system.dat");
            if(index==14&&value!=0&&context==null)throw new IOException("PC1 bank99:14 needs live logical BGM context");putInt(next,offset,value);
            if(index==7)effects.add(new Effect(EffectKind.REFRESH_VOLUMES_41CD00));
            else if(index==14){if(value==0){effects.add(new Effect(EffectKind.STOP_MUSIC_41C650,0));effects.add(new Effect(EffectKind.ARM_AUDIO_TIMER_4070E0));}
                else if(context.logicalMusic!=-1){effects.add(new Effect(EffectKind.REQUEST_MUSIC_41C4B0,context.logicalMusic,context.bgmMode));effects.add(new Effect(EffectKind.ARM_AUDIO_TIMER_4070E0));}}
        }else{next[byteBankOffset(bank,index)]=(byte)value;if(bank==100)effects.add(new Effect(EffectKind.WRITE_SYSTEM_4027E0));}
        return new ScriptWrite(new Pc1NativeSettings(next),effects);
    }
    /** Interface adapters report ordinary file failures as CRT-style integer
     *results. IOException denotes an unsupported/unexpected platform failure,
     *which propagates rather than inventing a native success/fallback result. */
    public interface NativeIo {
        int open(String path,int flags,int mode)throws IOException;
        int read(int descriptor,byte[] destination,int requested)throws IOException;
        int write(int descriptor,byte[] source,int requested)throws IOException;
        int close(int descriptor)throws IOException;
    }
    public enum IoKind { OPEN_READ,READ,CLOSE,COPY_DEFAULT,COPY_READ,OPEN_WRITE,WRITE }
    public static final class IoEvent {
        public final IoKind kind;public final int result,requested;
        private IoEvent(IoKind kind,int result,int requested){this.kind=kind;this.result=result;this.requested=requested;}
    }
    public static final class ReadResult {
        public final Pc1NativeSettings settings;public final int nativeResult,openResult,readCount,closeResult;public final boolean defaulted;public final List<IoEvent> events;
        private ReadResult(Pc1NativeSettings settings,int opened,int read,int closed,boolean fallback,List<IoEvent> events){this.settings=settings;openResult=opened;readCount=read;closeResult=closed;defaulted=fallback;nativeResult=fallback?-1:0;this.events=Collections.unmodifiableList(new ArrayList<>(events));}
    }
    /** Exact402890 admission/default ordering. An unread trailing file cannot
     *become known live memory: this result's raw block is exactly SIZE bytes.
     *Use parse(completeFile) separately for lossless file archival. */
    public static ReadResult nativeRead(NativeIo io,String path,Pc1NativeSettings defaults)throws IOException{
        if(defaults==null)throw new IOException("PC1 native settings read requires bound default template");return nativeReadCurrent(io,path,()->defaults);
    }
    public interface DefaultTemplate {Pc1NativeSettings current()throws IOException;}
    /** Read the mutable PE template only when402890 actually takes fallback,
     *after the failed open or the acquired descriptor's read/close calls. */
    public static ReadResult nativeReadCurrent(NativeIo io,String path,DefaultTemplate defaults)throws IOException{
        if(io==null||path==null||defaults==null)throw new IOException("PC1 native settings read requires I/O, path and bound default template");List<IoEvent> events=new ArrayList<>();int fd=io.open(path,0x8000,0);events.add(new IoEvent(IoKind.OPEN_READ,fd,0));
        if(fd==-1){events.add(new IoEvent(IoKind.COPY_DEFAULT,0,SIZE));return new ReadResult(new Pc1NativeSettings(defaults.current().nativeBytes()),fd,-1,-1,true,events);}
        byte[] bytes=new byte[SIZE];int read;try{read=io.read(fd,bytes,SIZE);}catch(IOException unexpected){closeAfterFailure(io,fd,unexpected);throw unexpected;}events.add(new IoEvent(IoKind.READ,read,SIZE));int closed=io.close(fd);events.add(new IoEvent(IoKind.CLOSE,closed,0));boolean fallback=read!=SIZE;events.add(new IoEvent(fallback?IoKind.COPY_DEFAULT:IoKind.COPY_READ,0,SIZE));
        return new ReadResult(new Pc1NativeSettings(fallback?defaults.current().nativeBytes():bytes.clone()),fd,read,closed,fallback,events);
    }
    public static final class WriteResult {
        public final int nativeResult,openResult,writeCount,closeResult;public final boolean fullCountAndCloseSucceeded;public final List<IoEvent> events;
        private WriteResult(int opened,int written,int closed,List<IoEvent> events){openResult=opened;writeCount=written;closeResult=closed;nativeResult=opened==-1?-1:0;fullCountAndCloseSucceeded=opened!=-1&&written==SIZE&&closed==0;this.events=Collections.unmodifiableList(new ArrayList<>(events));}
    }
    /**4027E0 ignores write/close failure after a successful open. Preserve that
     *reported native result separately from the observed count/close outcome.
     *No story-slot or save-metadata operation is implied by writing this block. */
    public WriteResult nativeWrite(NativeIo io,String path)throws IOException{
        if(io==null||path==null)throw new IOException("PC1 native settings write requires I/O and path");List<IoEvent> events=new ArrayList<>();int fd=io.open(path,0x8301,0x180);events.add(new IoEvent(IoKind.OPEN_WRITE,fd,0));if(fd==-1)return new WriteResult(fd,-1,-1,events);
        int written;try{written=io.write(fd,nativeBytes(),SIZE);}catch(IOException unexpected){closeAfterFailure(io,fd,unexpected);throw unexpected;}events.add(new IoEvent(IoKind.WRITE,written,SIZE));int closed=io.close(fd);events.add(new IoEvent(IoKind.CLOSE,closed,0));return new WriteResult(fd,written,closed,events);
    }
    /** Adapter exceptions are not native CRT return codes: close the acquired
     *descriptor and preserve the exception, without fabricating a native result. */
    private static void closeAfterFailure(NativeIo io,int descriptor,IOException failure){try{io.close(descriptor);}catch(IOException close){failure.addSuppressed(close);}}
    private static void require(Name name)throws IOException{if(name==null)throw new IOException("PC1 missing native name field");}
    private static void range(long offset,long length)throws IOException{if(offset<0||length<0||offset+length<offset||offset+length>SIZE)throw new IOException("PC1 native settings access outside owned1748-byte block");}
    private static int intAt(byte[] bytes,int at){return (bytes[at]&255)|((bytes[at+1]&255)<<8)|((bytes[at+2]&255)<<16)|((bytes[at+3]&255)<<24);}
    private static void putInt(byte[] bytes,int at,int value){for(int n=0;n<4;n++)bytes[at+n]=(byte)(value>>>(n*8));}
}
