package org.circus.core;

import java.io.*;
import java.util.*;

/** DC2 2006 41CE10/41CA70 Windows story frame. This is a version-owned
 * parser and load plan, not PC1's same-sized frame or a portable VM snapshot.
 * Native files do not contain a profile/hash; the selected corpus is authority. */
public final class Dc2NativeSlot {
    public static final int FRAME_BYTES=0x1f44,ORDINARY_OFFSET=0xc6c,ORDINARY_BYTES=1000,LOG_OFFSET=0x49c,LOG_CAPACITY=1000;
    public enum StringField {
        SCRIPT(4,128),BACKGROUND(0x84,128),ACTOR1(0x104,128),ACTOR2(0x184,128),
        OVERLAY1(0x204,128),OVERLAY2(0x284,128),RESOURCE3(0x304,128),RESOURCE4(0x384,128),RESOURCE5(0x404,128),
        ACTION0(0x1054,32),ACTION1(0x1074,32);
        public final int offset,cellBytes;
        StringField(int at,int count){offset=at;cellBytes=count;}
    }
    public enum Dword {
        SIGNATURE(0),LOGICAL_MUSIC(0x484),PACKED_SOUND(0x488),RAW_CODE_PC(0x48c),CURRENT_CHECKPOINT(0x490),LOG_COUNT(0x494),WAIT_KIND(0x498),
        BACKGROUND_X(0x1134),BACKGROUND_Y(0x1138),ACTOR1_X(0x113c),ACTOR1_Y(0x1140),ACTOR2_X(0x1144),ACTOR2_Y(0x1148),
        ACTOR1_STAGE_X(0x114c),ACTOR1_STAGE_Y(0x1150),ACTOR2_STAGE_X(0x1154),ACTOR2_STAGE_Y(0x1158),PENDING_COUNT(0x115c),
        CACHED_SCENARIO_READ(0x1f20),PARAGRAPH_MODE(0x1f24),HEAD_Y(0x1f28),EFFECT_KIND(0x1f2c),EFFECT_STRENGTH(0x1f34),ARGUMENT0(0x1f38),ARGUMENT1(0x1f3c);
        public final int offset;Dword(int at){offset=at;}
    }
    public static final class Plan {
        public final String script,sha256;
        public final int savedRawCodePc,savedWaitKind,checkpointIndex,checkpointCodePc,checkpointFilePc,checkpointOpcode,
            resumeCodePc,resumeFilePc,nativeWaitKind,candidateFlag,argument0,argument1;
        private Plan(MesScript caller,Dc2NativeSlot slot,int index,int at,int opcode,int resume,int wait,int flag,int a0,int a1)throws IOException{
            script=caller.name;sha256=caller.sha256;savedRawCodePc=slot.dword(Dword.RAW_CODE_PC);savedWaitKind=slot.dword(Dword.WAIT_KIND);
            checkpointIndex=index;checkpointCodePc=at;checkpointFilePc=caller.versionOffset+at;checkpointOpcode=opcode;
            resumeCodePc=resume;resumeFilePc=caller.versionOffset+resume;nativeWaitKind=wait;candidateFlag=flag;argument0=a0;argument1=a1;
        }
    }
    private final byte[] bytes;
    private Dc2NativeSlot(byte[] source){bytes=source;}
    /**41CE10 reads precisely8004 bytes and admits signature1; trailing data is
     * preserved for archival inspection, never supplied as frame backing. */
    public static Dc2NativeSlot parse(byte[] source)throws IOException{
        if(source==null||source.length<FRAME_BYTES)throw new IOException("DC2 native slot requires8004 bytes");
        if(Binary.u32(source,0)!=1)throw new IOException("DC2 native slot signature must equal1");return new Dc2NativeSlot(source.clone());
    }
    public int originalLength(){return bytes.length;}
    public byte[] originalBytes(){return bytes.clone();}
    public byte[] frameBytes(){return Arrays.copyOf(bytes,FRAME_BYTES);}
    public byte[] trailingBytes(){return Arrays.copyOfRange(bytes,FRAME_BYTES,bytes.length);}
    public int dword(Dword field)throws IOException{if(field==null)throw new IOException("DC2 slot DWORD missing");return(int)Binary.u32(bytes,field.offset);}
    public byte[] rawCString(StringField field)throws IOException{
        if(field==null)throw new IOException("DC2 slot string field missing");int end=field.offset;
        while(end<FRAME_BYTES&&bytes[end]!=0)end++;if(end==FRAME_BYTES)throw new IOException("DC2 slot string exceeds frame");return Arrays.copyOfRange(bytes,field.offset,end);
    }
    /** Portable import requires strings to fit their reviewed native cells;
     * rawCString retains the original unbounded-within-frame read separately. */
    public String text(StringField field)throws IOException{byte[] value=rawCString(field);if(value.length>=field.cellBytes)throw new IOException("DC2 slot string exceeds reviewed cell "+field);return TextCodec.cp932(value);}
    public String scriptName()throws IOException{String name=Binary.nativeResourcePath(text(StringField.SCRIPT));if(name.isEmpty())throw new IOException("DC2 slot caller is empty");return name;}
    public String resource(StringField field)throws IOException{if(field==StringField.SCRIPT||field==StringField.ACTION0||field==StringField.ACTION1)throw new IOException("DC2 slot field is not a resource");String name=text(field);return name.isEmpty()?name:Binary.nativeResourcePath(name);}
    public byte[] ordinaryBytes(){return Arrays.copyOfRange(bytes,ORDINARY_OFFSET,ORDINARY_OFFSET+ORDINARY_BYTES);}
    public int ordinary(int bank,int index)throws IOException{if(bank<0||bank>=10||index<0||index>=100)throw new IOException("DC2 slot ordinary index");return bytes[ORDINARY_OFFSET+bank*100+index]&255;}
    public int[] logWords(){int[] words=new int[LOG_CAPACITY];for(int n=0;n<words.length;n++)words[n]=(bytes[LOG_OFFSET+n*2]&255)|(bytes[LOG_OFFSET+n*2+1]&255)<<8;return words;}
    public int[] activeLogWords()throws IOException{int n=dword(Dword.LOG_COUNT);if(n<0||n>LOG_CAPACITY)throw new IOException("DC2 slot log count outside1000 words");return Arrays.copyOf(logWords(),n);}
    /**41CB2E computes a checkpoint cursor independently of saved RAW_CODE_PC.
     *41CC36 publishes it AFTER the8004-byte frame copy. Known header classes
     * are explicit; the ordinary text importer below is narrower still. */
    public Plan projection(MesScript caller)throws IOException{
        if(caller==null||!caller.profile.id.equals("dc2-3363")||caller.version!=0x3363||!caller.name.replace('\\','/').equalsIgnoreCase(scriptName()))throw new IOException("DC2 slot caller/profile identity mismatch");
        int index=dword(Dword.CURRENT_CHECKPOINT);if(index<0||index>=caller.headerOffsets.length)throw new IOException("DC2 slot checkpoint outside selected caller");
        long offset=caller.headerOffsets[index];if(offset<2||offset>Integer.MAX_VALUE||(long)caller.versionOffset+offset>Integer.MAX_VALUE)throw new IOException("DC2 slot checkpoint code offset");
        int at=(int)offset;if(at>=100000)throw new IOException("DC2 slot checkpoint outside native100000-byte code buffer");MesScript.Record record=caller.fetchRecord(caller.versionOffset+at);if(record.end-caller.versionOffset>100000)throw new IOException("DC2 slot checkpoint record crosses native code buffer");int opcode=record.opcode;
        int resume=at,wait=-1,flag=0,a0=dword(Dword.ARGUMENT0),a1=dword(Dword.ARGUMENT1);
        if(opcode==0x1a){flag=record.args[0];resume+=3;}
        else if(opcode==0x1c){wait=0x1c;resume+=3;}
        else if(opcode==7){wait=7;resume+=3;}
        else if(opcode!=0x34&&opcode!=0x4b)throw new IOException("DC2 slot checkpoint opcode outside reviewed projection: "+opcode);
        return new Plan(caller,this,index,at,opcode,resume,wait,flag,a0,a1);
    }
    /** First import scope: reviewed ordinary text checkpoints. The importer
     * replays34 voice/4B body to its following07; it never resumes saved raw PC.
     * Selection/UI pending batches and unsupported effect2 require more owners. */
    public Plan normalTextPlan(MesScript caller)throws IOException{
        Plan plan=projection(caller);
        if(plan.savedWaitKind!=7||plan.checkpointOpcode!=0x34&&plan.checkpointOpcode!=0x4b)throw new IOException("DC2 slot import supports ordinary wait7 text checkpoints only");
        if(dword(Dword.PENDING_COUNT)!=0)throw new IOException("DC2 slot import pending draw batch is not supported");
        int paragraph=dword(Dword.PARAGRAPH_MODE);if(paragraph<0||paragraph>1)throw new IOException("DC2 slot paragraph mode");
        int effect=dword(Dword.EFFECT_KIND);if(effect<0||effect>6||effect==2)throw new IOException("DC2 slot effect outside supported import scope");
        int[] log=activeLogWords();if(log.length==0||(log[log.length-1]&0x7fff)!=plan.checkpointIndex)throw new IOException("DC2 slot active checkpoint/log mismatch");
        for(int word:log)if((word&0x7fff)>=caller.headerOffsets.length)throw new IOException("DC2 slot log references unavailable caller checkpoint");
        for(StringField field:StringField.values()){if(field==StringField.SCRIPT)scriptName();else if(field==StringField.ACTION0||field==StringField.ACTION1)text(field);else resource(field);}
        return plan;
    }
}
