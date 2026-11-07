package org.circus.core;

import java.io.IOException;
import java.util.Arrays;

/** Lossless PC1 CHS Windows story-slot image, not41A620's state/resource loader.
 *41A970 reads1F44 bytes and checks the first little-endian DWORD==1. It does
 *not check total file length, C strings, counts, paths or checkpoint validity.
 *All bytes, including unknown fields, inactive records and trailing data, stay
 *owned by this immutable object. Native slots contain no MES hash/profile ID;
 *the selected game and resolved caller require independent identification. */
public final class Pc1NativeSlot {
    public static final int FRAME_BYTES=0x1f44;
    public static final int LOG_OFFSET=0x49c,LOG_CAPACITY=1000;
    public static final int ORDINARY_OFFSET=0xc6c,ORDINARY_BYTES=1000;
    public static final int PENDING_OFFSET=0x1160,PENDING_CAPACITY=60,PENDING_DWORDS=8;
    /** Relative to working9D3F00 / candidate9CDD28 / live9D8588 alike. These
     *cell sizes describe neighboring native fields, not strcpy scan limits. */
    public enum StringField {
        SCRIPT(0x004,0x80),BACKGROUND(0x084,0x80),ACTOR1(0x104,0x80),ACTOR2(0x184,0x80),
        OVERLAY1(0x204,0x80),OVERLAY2(0x284,0x80),RESOURCE3(0x304,0x80),RESOURCE4(0x384,0x80),RESOURCE5(0x404,0x80),
        ACTION0(0x1054,0x20),ACTION1(0x1074,0x20);
        public final int offset,cellBytes;
        StringField(int offset,int size){this.offset=offset;cellBytes=size;}
    }
    /** Raw signed Java representations of native DWORDs. No valid-range or
     *enum interpretation is imposed on an admitted native image. */
    public enum Dword {
        SIGNATURE(0),LOGICAL_MUSIC(0x484),PACKED_SOUND(0x488),RAW_CODE_PC(0x48c),
        CURRENT_CHECKPOINT(0x490),LOG_COUNT(0x494),WAIT_KIND(0x498),
        BACKGROUND_X(0x1134),BACKGROUND_Y(0x1138),ACTOR1_X(0x113c),ACTOR1_Y(0x1140),ACTOR2_X(0x1144),ACTOR2_Y(0x1148),
        PENDING_COUNT(0x115c),CACHED_SCENARIO_READ(0x1f20),PARAGRAPH_MODE(0x1f24),HEAD_Y(0x1f28),
        EFFECT_KIND(0x1f2c),EFFECT_STRENGTH(0x1f34),ARGUMENT0(0x1f38),ARGUMENT1(0x1f3c);
        public final int offset;
        Dword(int offset){this.offset=offset;}
    }
    private final byte[] original;
    private Pc1NativeSlot(byte[] bytes){original=bytes;}

    /** Native read admission only; projections may separately reject an unsafe
     *consumer. A file with trailing bytes is accepted and retained unchanged. */
    public static Pc1NativeSlot parse(byte[] bytes)throws IOException{
        if(bytes==null||bytes.length<FRAME_BYTES)throw new IOException("PC1 native slot requires at least8004 bytes (41A970 read1F44)");
        if(intAt(bytes,0)!=1)throw new IOException("PC1 native slot signature DWORD must equal1");
        return new Pc1NativeSlot(bytes.clone());
    }
    public int originalLength(){return original.length;}
    /** Complete input for lossless archival roundtrip; NOT native41AAD0 output. */
    public byte[] originalBytes(){return original.clone();}
    /** Exactly8004 bytes, the frame length consumed/read and emitted/written by
     *the native routines. Returning it performs no slot I/O or loading. */
    public byte[] frameBytes(){return Arrays.copyOf(original,FRAME_BYTES);}
    public byte[] trailingBytes(){return Arrays.copyOfRange(original,FRAME_BYTES,original.length);}
    public byte[] rawBytes(int offset,int length)throws IOException{range(offset,length);return Arrays.copyOfRange(original,offset,offset+length);}
    public int rawDword(int offset)throws IOException{range(offset,4);return intAt(original,offset);}
    public int dword(Dword field)throws IOException{if(field==null)throw new IOException("PC1 native slot missing DWORD field");return rawDword(field.offset);}
    public byte[] rawCell(StringField field)throws IOException{require(field);return rawBytes(field.offset,field.cellBytes);}
    /** Native REPNE SCASB has no per-cell bound and may cross into the next
     *field. Scan all known frame backing; the native reader did NOT load file
     *trailing bytes into that backing. Missing NUL rejects only this view.
     *Bytes remain encoded/raw; no Unicode, path or destination-size assumption. */
    public byte[] rawCString(StringField field)throws IOException{
        require(field);int end=field.offset;while(end<FRAME_BYTES&&original[end]!=0)end++;
        if(end==FRAME_BYTES)throw new IOException("PC1 native slot "+field+" C string reads beyond owned8004-byte frame");
        return Arrays.copyOfRange(original,field.offset,end);
    }
    public byte[] ordinaryBytes(){return Arrays.copyOfRange(original,ORDINARY_OFFSET,ORDINARY_OFFSET+ORDINARY_BYTES);}
    public int ordinary(int bank,int index)throws IOException{
        if(bank<0||bank>9||index<0||index>99)throw new IOException("PC1 native slot ordinary bank/index outside0..9/0..99");
        return original[ORDINARY_OFFSET+bank*100+index]&255;
    }
    /** Includes inactive/pending history words. The high8000 bit is retained. */
    public int[] rawLogWords(){int[] result=new int[LOG_CAPACITY];for(int n=0;n<result.length;n++){int at=LOG_OFFSET+n*2;result[n]=(original[at]&255)|((original[at+1]&255)<<8);}return result;}
    /** Optional active-count view; malformed raw counts remain admitted and
     *losslessly available via dword(LOG_COUNT), rawLogWords and originalBytes. */
    public int[] activeLogWords()throws IOException{
        int count=dword(Dword.LOG_COUNT);if(count<0||count>LOG_CAPACITY)throw new IOException("PC1 native slot log count exceeds owned1000-word buffer");
        return Arrays.copyOf(rawLogWords(),count);
    }
    /** Eight raw DWORDs per native32-byte pending record, including inactive
     *records. Text offsets/conditions/selectors are not resolved or executed. */
    public int[] pendingRecordDwords(int index)throws IOException{
        if(index<0||index>=PENDING_CAPACITY)throw new IOException("PC1 native slot pending record outside owned60-record buffer");
        int[] result=new int[PENDING_DWORDS];for(int n=0;n<result.length;n++)result[n]=intAt(original,PENDING_OFFSET+index*32+n*4);return result;
    }
    public int activePendingCount()throws IOException{
        int count=dword(Dword.PENDING_COUNT);if(count<0||count>PENDING_CAPACITY)throw new IOException("PC1 native slot pending count exceeds owned60-record buffer");return count;
    }
    /** Pure404E70+41AAD0 metadata patch. Caller supplies the live weekday after
     *404EE0(1,1), live ordinary9:20, and local GetLocalTime fields. Does not run
     *that service, persist system.dat, truncate trailing bytes or perform I/O.
     *Only signature+seven candidate bytes change; all other backing survives. */
    public Pc1NativeSlot withNativeSaveMetadata(int liveWeekday,int live9_20,int year,int month,int day,int hour,int minute)throws IOException{
        if(liveWeekday<0||liveWeekday>255||live9_20<0||live9_20>255||year<0||year>65535||month<1||month>12||day<1||day>31||hour<0||hour>23||minute<0||minute>59)throw new IOException("PC1 native slot save metadata fields");
        byte[] next=original.clone();putInt(next,0,1);int base=ORDINARY_OFFSET+900;
        next[base+2]=(byte)liveWeekday;next[base+20]=(byte)live9_20;next[base+21]=(byte)month;next[base+22]=(byte)day;
        next[base+23]=(byte)hour;next[base+24]=(byte)minute;next[base+25]=(byte)(year+0x30);
        return new Pc1NativeSlot(next);
    }
    /** Caller must independently resolve rawCString(SCRIPT) in the selected
     *PC1 corpus. This original format contains no SHA to authenticate a caller.
     *Delegates only checkpoint projection; does not apply any loaded owner. */
    public Pc1SlotProjection projection(MesScript caller,int currentMusic)throws IOException{
        if(caller==null||!caller.profile.id.equals("dcpc-2063")||!caller.profile.canExecuteVersion(caller.version))throw new IOException("PC1 native slot projection requires independently resolved native PC1 caller");
        return Pc1SlotProjection.project(caller,dword(Dword.CURRENT_CHECKPOINT),
            new Pc1SlotProjection.SavedFields(dword(Dword.RAW_CODE_PC),dword(Dword.ARGUMENT0),dword(Dword.ARGUMENT1),dword(Dword.LOGICAL_MUSIC)),currentMusic);
    }
    private static void require(StringField field)throws IOException{if(field==null)throw new IOException("PC1 native slot missing string field");}
    private static void range(int offset,int length)throws IOException{if(offset<0||length<0||(long)offset+length>FRAME_BYTES)throw new IOException("PC1 native slot view outside owned8004-byte frame");}
    private static int intAt(byte[] bytes,int offset){return (bytes[offset]&255)|((bytes[offset+1]&255)<<8)|((bytes[offset+2]&255)<<16)|((bytes[offset+3]&255)<<24);}
    private static void putInt(byte[] bytes,int offset,int value){for(int n=0;n<4;n++)bytes[offset+n]=(byte)(value>>>(n*8));}
}
