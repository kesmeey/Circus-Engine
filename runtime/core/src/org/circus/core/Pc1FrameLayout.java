package org.circus.core;

import java.io.IOException;

/** Current readable PC1 CHS native1F44 layout. Offsets describe one byte owner;
 *they are not independent fields to be synchronized after an instruction.
 *Unknown bytes and unchecked native neighboring aliases remain distinct. */
public final class Pc1FrameLayout {
    private Pc1FrameLayout(){}
    public static final int LIVE_BASE=0x9d8588,CANDIDATE_BASE=0x9cdd28,WORKING_BASE=0x9d3f00,SYSTEM_BASE=0x9d5e48;
    public static final int FRAME_BYTES=0x1f44;
    public static final int SIGNATURE=0,SCRIPT=4,STRING_CELL_BYTES=0x80;
    public static final int BACKGROUND=0x84,ACTOR1=0x104,ACTOR2=0x184,OVERLAY1=0x204,OVERLAY2=0x284,RESOURCE3=0x304,RESOURCE4=0x384,RESOURCE5=0x404;
    public static final int LOGICAL_MUSIC=0x484,PACKED_SOUND=0x488,RAW_CODE_PC=0x48c,CURRENT_CHECKPOINT=0x490,LOG_COUNT=0x494,WAIT_KIND=0x498;
    public static final int LOG_OFFSET=0x49c,LOG_CAPACITY=1000,ORDINARY_OFFSET=0xc6c,ORDINARY_BYTES=1000;
    public static final int ACTION0=0x1054,ACTION1=0x1074,ACTION_CELL_BYTES=0x20;
    public static final int BACKGROUND_X=0x1134,BACKGROUND_Y=0x1138,ACTOR1_X=0x113c,ACTOR1_Y=0x1140,ACTOR2_X=0x1144,ACTOR2_Y=0x1148;
    public static final int PENDING_COUNT=0x115c,PENDING_OFFSET=0x1160,PENDING_CAPACITY=60,PENDING_RECORD_BYTES=32,PENDING_DWORDS=8;
    public static final int BANK90_OFFSET=0x18e0,BANK90_MIN_INDEX=-BANK90_OFFSET,BANK90_MAX_INDEX=FRAME_BYTES-BANK90_OFFSET-1;
    public static final int SCENARIO_LABEL=0x1ce0;
    public static final int CACHED_SCENARIO_READ=0x1f20,PARAGRAPH_MODE=0x1f24,HEAD_Y=0x1f28,EFFECT_KIND=0x1f2c,EFFECT_STRENGTH=0x1f34,ARGUMENT0=0x1f38,ARGUMENT1=0x1f3c;

    public static int checkedOffset(int offset,int length)throws IOException{
        if(offset<0||length<0||(long)offset+length>FRAME_BYTES)throw new IOException("PC1 native frame access outside owned8004 bytes: offset="+offset+", length="+length);
        return offset;
    }
    /** Native411910/411B10 impose no bank90 index check. This safe owner may
     *serve every signed index whose computed byte remains in the actual frame. */
    public static int offsetBank90(int index)throws IOException{
        long offset=(long)BANK90_OFFSET+index;
        if(offset<0||offset>=FRAME_BYTES)throw new IOException("PC1 bank90 index "+index+" reaches unowned neighboring native memory (ownedframe indices "+BANK90_MIN_INDEX+".."+BANK90_MAX_INDEX+")");
        return (int)offset;
    }
    /** Normal typed bank/index; this convenience is not native lower-bound validation. */
    public static int offsetOrdinary(int bank,int index)throws IOException{
        if(bank<0||bank>9||index<0||index>99)throw new IOException("PC1 ordinary typed reference outside0..9/0..99");
        return ORDINARY_OFFSET+bank*100+index;
    }
    /** Native signed comparisons only test bank<10 and index<100. Negative
     *operands can alias earlier frame bytes; no host memory is invented when
     *that computed address leaves this frame. Caller handles native no-effect
     *when an upper comparison fails before asking for this offset. */
    public static int offsetNativeOrdinary(int bank,int index)throws IOException{
        if(bank>=10||index>=100)throw new IOException("PC1 ordinary native upper comparison rejected reference");
        // LEA arithmetic is32-bit even for signed admitted operands. A large
        // negative bank may wrap its scaled address back into this frame.
        int address=LIVE_BASE+ORDINARY_OFFSET+bank*100+index;
        long offset=(address&0xffffffffL)-LIVE_BASE;
        if(offset<0||offset>=FRAME_BYTES)throw new IOException("PC1 ordinary native alias reaches unowned neighboring memory: "+bank+":"+index);
        return (int)offset;
    }
    public static int offsetLog(int index)throws IOException{
        if(index<0||index>=LOG_CAPACITY)throw new IOException("PC1 raw log word outside owned1000 entries");
        return LOG_OFFSET+index*2;
    }
    public static int offsetPending(int index,int dword)throws IOException{
        if(index<0||index>=PENDING_CAPACITY||dword<0||dword>=PENDING_DWORDS)throw new IOException("PC1 pending DWORD outside owned60x8 table");
        return PENDING_OFFSET+index*PENDING_RECORD_BYTES+dword*4;
    }
}
