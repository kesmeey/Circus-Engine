package org.circus.core;

import java.io.*;
import java.util.*;

/** PC1 frame-owned checkpoint log and read-only418220/418090 replay.
 *43D410's selected cursor is external to1F44 and is supplied by the caller.
 *Script identity belongs to the surrounding frame: mode0 changes the loaded
 *script while retaining this log. No expanded text, Variables or VM is stored. */
public final class Pc1TextLog {
    private static final int FORMAT=0x50314c32,LEGACY_FORMAT=0x50314c31; // P1L2 narrow authority
    /** [9D8A24,9D91F4): the next word aliases ordinary bytes, not more log. */
    public static final int CAPACITY=1000;
    public static final int CODE_CAPACITY=100000,RAW_STRING_CAPACITY=1023;
    private final Pc1NativeFrame frame;
    private final int[] words;
    private int detachedCount,detachedCheckpoint;
    /** Detached reservation only. Adjacent frame bytes are unknown, not zero. */
    public Pc1TextLog(){frame=null;words=new int[CAPACITY];}
    private Pc1TextLog(Pc1NativeFrame frame){this.frame=frame;words=null;}
    /** Direct view: this object never caches or mirrors live frame fields. */
    public static Pc1TextLog backed(Pc1NativeFrame frame){if(frame==null)throw new IllegalArgumentException("PC1 text log requires canonical frame");return new Pc1TextLog(frame);}
    public boolean frameBacked(){return frame!=null;}

    public enum Role { EARLIER, SELECTED, LATER }
    /** Native raw marker+NUL string; payload omits marker/NUL and stays encoded
     *for K/L. Pc1TextExpander owns byte+20h and live dollar expansion. */
    public static final class RawRecord {
        public final int sourcePc,endPc,opcode;
        private final byte[] bytes;
        private RawRecord(int pc,int end,int opcode,byte[] bytes){sourcePc=pc;endPc=end;this.opcode=opcode;this.bytes=bytes;}
        public byte[] payload(){return bytes.clone();}
    }
    public static final class RawMessage {
        public final int logIndex,rawCheckpoint,checkpointId,checkpointPc;
        public final Role role;
        /** Last K encountered, including earlier replay rows; null means empty
         *initial speaker. Voice is first35 before the first K/L in THIS row. */
        public final RawRecord speaker,body,voice;
        private RawMessage(int index,int raw,int pc,Role role,RawRecord speaker,RawRecord body,RawRecord voice){
            logIndex=index;rawCheckpoint=raw;checkpointId=raw&0x7fff;checkpointPc=pc;this.role=role;this.speaker=speaker;this.body=body;this.voice=voice;
        }
    }
    public static final class Replay {
        public final int requestedSelection,selectedIndex,bodySelectedIndex;
        public final boolean currentPalette,clearSpeakerFirst,speakerOnly,drawSpeaker;
        public final List<RawMessage> messages;
        private Replay(int request,int selected,int bodySelected,boolean current,boolean clear,boolean only,boolean draw,List<RawMessage> messages){
            requestedSelection=request;selectedIndex=selected;bodySelectedIndex=bodySelected;currentPalette=current;clearSpeakerFirst=clear;speakerOnly=only;drawSpeaker=draw;
            this.messages=Collections.unmodifiableList(new ArrayList<>(messages));
        }
        /** In single-message mode417C50 uses this speaker, including empty/null.
         *Paragraph replay does not invoke417C50 for each row. */
        public RawRecord drawnSpeaker(){return drawSpeaker&&!messages.isEmpty()?messages.get(messages.size()-1).speaker:null;}
    }

    public int count(){return frame==null?detachedCount:header(Pc1FrameLayout.LOG_COUNT);}
    public int currentCheckpoint(){return frame==null?detachedCheckpoint:header(Pc1FrameLayout.CURRENT_CHECKPOINT);}
    public int rawEntry(int index)throws IOException{if(index<0||index>=count())throw new IOException("PC1 text log index "+index);return word(index);}
    public int pendingRawWord()throws IOException{return word(count());}
    /** Includes inactive words: native loader clears only slot0, not the array. */
    public int[] rawWords(){if(frame==null)return words.clone();int[] raw=new int[CAPACITY];try{for(int n=0;n<CAPACITY;n++)raw[n]=word(n);}catch(IOException impossible){throw new AssertionError(impossible);}return raw;}
    /** Copies only this projection's named scalars and1000 reserved cells.
     *Neighboring frame authority is deliberately not implied by a log copy. */
    public Pc1TextLog copy(){Pc1TextLog result=new Pc1TextLog();result.detachedCount=count();result.detachedCheckpoint=currentCheckpoint();System.arraycopy(rawWords(),0,result.words,0,CAPACITY);return result;}
    public void copyFrom(Pc1TextLog source)throws IOException{if(source==null)throw new IOException("PC1 missing text log owner");int nextCount=source.count(),nextCheckpoint=source.currentCheckpoint();int[] raw=source.rawWords();setCurrent(nextCheckpoint);setCount(nextCount);for(int n=0;n<CAPACITY;n++)word(n,raw[n]);}
    /** Pure projection into a new independently owned log. Import all1000
     *ushort cells directly, including inactive/pending stale words and page
     *bits; replay, producer append and script-table validation do not run.
     *Raw32-bit current/count survive. A later access beyond these1000 provided
     *cells fails for missing authority. No input array is retained. */
    public static Pc1TextLog fromNative(int currentCheckpoint,int count,int[] rawWords)throws IOException{
        if(rawWords==null||rawWords.length!=CAPACITY)throw new IOException("PC1 native text log requires all1000 raw words");
        for(int word:rawWords)ushort(word,"native raw word");
        Pc1TextLog result=new Pc1TextLog();result.detachedCheckpoint=currentCheckpoint;result.detachedCount=count;System.arraycopy(rawWords,0,result.words,0,CAPACITY);return result;
    }

    /**415341: preserve the next slot's flag ONLY if its entire word is8000h. */
    public void onCheckpoint03(int id)throws IOException{
        ushort(id,"checkpoint");int at=count();wordOffset(at);
        if(frame!=null)frame.writeI32(Pc1FrameLayout.ARGUMENT0,id);
        int old=word(at);
        word(at,id|(old==0x8000?0x8000:0));
        //415379 re-reads count and C0 AFTER the aliased word store.
        int next=count()+1,current=frame==null?id:frame.readI32(Pc1FrameLayout.ARGUMENT0);setCurrent(current);setCount(next);
    }
    /**415395:04 changes the current checkpoint without appending a log entry. */
    public void onCheckpoint04(int id)throws IOException{ushort(id,"checkpoint");if(frame!=null)frame.writeI32(Pc1FrameLayout.ARGUMENT0,id);setCurrent(id);}
    /**412898:07 writes the NEXT slot; it does not append or clear old entries. */
    public void onWait07(int argument,int paragraphMode)throws IOException{
        if(argument<0||argument>255)throw new IOException("PC1 text wait argument");word(count(),argument==1&&paragraphMode!=0?0x8000:0);
    }
    /** persist15 mode6/11 clear count/first slot, retaining currentCheckpoint. */
    public void clearLog(){setCount(0);try{word(0,0);}catch(IOException impossible){throw new AssertionError(impossible);}}
    /**419A43: successful ordinary label action (native button index>0) removes
     *one log entry, leaving current checkpoint and the discarded raw word live. */
    public void discardLastForLabelAction()throws IOException{setCount(count()-1);}
    /**411670:mode0 preserves the entire log;1/2/3 clear current/count/slot0.
     *Neither this branch nor412160 clears the external43D410 selection. */
    public void scriptLoaded(int mode)throws IOException{
        if(mode<0||mode>3)throw new IOException("PC1 text log loader mode");if(mode!=0){clearLog();setCurrent(0);}
    }

    /** Strictly checked form of418220. Negative selections use the current entry;
     *-2 omits only its selected body in paragraph mode. Physical instruction
     *boundaries and03 head.end are deliberately not used for table lookup. */
    public Replay replay(MesScript script,int selection,int paragraphMode,int choiceMode)throws IOException{
        require(script);int selected=selectionIndex(selection);boolean current=selected==count()-1;
        int bodySelected=selected,pc=checkpointPc(script,word(selected));boolean clear=script.rawOpcode(pc)==4;
        List<RawMessage> messages=new ArrayList<>();
        if(clear){
            if(selected==0||choiceMode==2)return new Replay(selection,selected,selected,current,true,true,false,messages);
            bodySelected--;pc=checkpointPc(script,word(bodySelected));
        }
        RawRecord speaker=null;
        if(paragraphMode==0){
            RawMessage message=scanMessage(script,bodySelected,pc,Role.SELECTED,speaker);messages.add(message);
            return new Replay(selection,selected,bodySelected,current,clear,false,true,messages);
        }
        int start=bodySelected;while(start>0&&(word(start)&0x8000)==0)start--;
        for(int n=start;n<bodySelected;n++){
            RawMessage message=scanMessage(script,n,checkpointPc(script,word(n)),Role.EARLIER,speaker);messages.add(message);speaker=message.speaker;
        }
        if(selection!=-2){
            RawMessage message=scanMessage(script,bodySelected,pc,Role.SELECTED,speaker);messages.add(message);speaker=message.speaker;
        }
        if(selection>=0)for(int n=bodySelected+1;n<count();n++){
            if((word(n)&0x8000)!=0)break;int next=checkpointPc(script,word(n));if(script.rawOpcode(next)==4)break;
            RawMessage message=scanMessage(script,n,next,Role.LATER,speaker);messages.add(message);speaker=message.speaker;
        }
        return new Replay(selection,selected,bodySelected,current,clear,false,false,messages);
    }

    /**418600 is a separate user voice-replay action, not a redraw side effect.
     *It stops at first K/L or35, including when selection points to04. */
    public RawRecord voiceForSelection(MesScript script,int selection)throws IOException{
        require(script);int index=selectionIndex(selection),pc=checkpointPc(script,word(index));
        while(pc<codeLimit(script)){
            RawRecord record=rawString(script,pc);if(record.opcode==0x35)return record;if(record.opcode==0x4b||record.opcode==0x4c)return null;pc=record.endPc;
        }
        throw new IOException("PC1 voice replay exhausted code at checkpoint "+(word(index)&0x7fff));
    }
    /**418090 returns0 for expanded @K prefix, otherwise1; layout uses this to
     *decide whether a paragraph body first moves to the next visual line. */
    public static boolean beginsNewLine(byte[] expandedBody)throws IOException{
        if(expandedBody==null)throw new IOException("PC1 missing expanded body");return expandedBody.length<2||expandedBody[0]!='@'||expandedBody[1]!='K';
    }

    private RawMessage scanMessage(MesScript script,int index,int pc,Role role,RawRecord speaker)throws IOException{
        int start=pc;RawRecord voice=null;boolean voiceSearch=true;
        while(pc<codeLimit(script)){
            RawRecord record=rawString(script,pc);
            if(voiceSearch&&record.opcode==0x35){voice=record;voiceSearch=false;}
            if(record.opcode==0x4b){speaker=record;voiceSearch=false;}
            else if(record.opcode==0x4c)return new RawMessage(index,word(index),start,role,speaker,record,voice);
            pc=record.endPc;
        }
        throw new IOException("PC1 text replay has no L body at checkpoint "+(word(index)&0x7fff));
    }
    private static RawRecord rawString(MesScript script,int pc)throws IOException{
        int limit=codeLimit(script);if(pc<script.versionOffset||pc>=limit)throw new IOException("PC1 text source outside loaded code: "+pc);
        int opcode=script.rawOpcode(pc),end=pc+1;ByteArrayOutputStream bytes=new ByteArrayOutputStream();
        while(end<limit){int value=script.rawOpcode(end++);if(value==0)return new RawRecord(pc,end,opcode,bytes.toByteArray());if(bytes.size()==RAW_STRING_CAPACITY)throw new IOException("PC1 raw text exceeds native1024-byte temporary at "+pc);bytes.write(value);}
        throw new IOException("PC1 unterminated raw replay string at "+pc);
    }
    private static int codeLimit(MesScript script){return (int)Math.min((long)script.length(),(long)script.versionOffset+CODE_CAPACITY);}
    private static int checkpointPc(MesScript script,int rawWord)throws IOException{
        int id=rawWord&0x7fff;if(id>=script.headerOffsets.length)throw new IOException("PC1 checkpoint ID outside table: "+id);
        long pc=(long)script.versionOffset+script.headerOffsets[id];if(pc<script.versionOffset||pc>=codeLimit(script))throw new IOException("PC1 checkpoint offset outside loaded code: "+id);
        return (int)pc;
    }
    private int selectionIndex(int selection)throws IOException{int count=count(),index=selection<0?count-1:selection;if(index<0||index>=count)throw new IOException("PC1 text selection outside active log: "+selection);return index;}
    private int header(int offset){try{return frame.readI32(offset);}catch(IOException impossible){throw new AssertionError(impossible);}}
    private void header(int offset,int value){try{frame.writeI32(offset,value);}catch(IOException impossible){throw new AssertionError(impossible);}}
    private void setCount(int value){if(frame==null)detachedCount=value;else header(Pc1FrameLayout.LOG_COUNT,value);}
    private void setCurrent(int value){if(frame==null)detachedCheckpoint=value;else header(Pc1FrameLayout.CURRENT_CHECKPOINT,value);}
    private int wordOffset(int index)throws IOException{
        if(frame==null){if(index<0||index>=CAPACITY)throw new IOException("PC1 detached text log has no authority for neighboring frame word "+index+"; next native word aliases ordinary variables");return index;}
        int address=Pc1FrameLayout.LIVE_BASE+Pc1FrameLayout.LOG_OFFSET+index*2;long offset=(address&0xffffffffL)-Pc1FrameLayout.LIVE_BASE;
        if(offset<0||offset+2>Pc1FrameLayout.FRAME_BYTES)throw new IOException("PC1 text log word "+index+" reaches unowned neighboring native memory");return(int)offset;
    }
    private int word(int index)throws IOException{int offset=wordOffset(index);return frame==null?words[offset]:frame.readU16(offset);}
    private void word(int index,int value)throws IOException{int offset=wordOffset(index);if(frame==null)words[offset]=value&65535;else frame.writeU16(offset,value);}
    private static void ushort(int value,String name)throws IOException{if(value<0||value>0xffff)throw new IOException("PC1 text log "+name+" is not ushort");}
    private static void require(MesScript script)throws IOException{if(script==null||!script.profile.id.equals("dcpc-2063")||script.version!=0x2063)throw new IOException("PC1 text log requires native2063 script");}

    /** Narrow value codec only: full runtime snapshots serialize nativeFrame,
     *never this reservation plus a second mirrored runtime log. */
    public void save(DataOutput out)throws IOException{out.writeInt(FORMAT);out.writeInt(currentCheckpoint());out.writeInt(count());for(int word:rawWords())out.writeShort(word);}
    public static Pc1TextLog load(DataInput in)throws IOException{
        int format=in.readInt();if(format!=FORMAT&&format!=LEGACY_FORMAT)throw new IOException("PC1 text log format");Pc1TextLog result=new Pc1TextLog();result.detachedCheckpoint=in.readInt();result.detachedCount=in.readInt();if(format==LEGACY_FORMAT){ushort(result.detachedCheckpoint,"saved checkpoint");if(result.detachedCount<0||result.detachedCount>CAPACITY)throw new IOException("PC1 saved text log capacity");}for(int n=0;n<CAPACITY;n++)result.words[n]=in.readUnsignedShort();return result;
    }
}
