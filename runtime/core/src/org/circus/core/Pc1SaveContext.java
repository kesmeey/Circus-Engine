package org.circus.core;

import java.io.*;

/** Native BB8418 plus9CDD28: independent candidate-selection policy.
 * Never substitutes the26 suspended caller or a mobile whole-VM snapshot. */
public final class Pc1SaveContext {
    private static final int FORMAT=0x50315333;
    public static final int FLAG=0xbb8418,CANDIDATE=Pc1FrameLayout.CANDIDATE_BASE;
    private int flag;
    private final Pc1NativeFrame raw=Pc1NativeFrame.zeroBss();
    private Pc1Frame candidate;
    private boolean bound;
    public Pc1NativeFrame nativeCandidate(){return raw;}
    public void bindNativeMemory(Pc1NativeMemory memory)throws IOException{
        if(memory==null||bound)throw new IOException("PC1 candidate views require unique native memory");
        memory.bind(FLAG,4,new Pc1NativeMemory.Region(){public int read(int at){return flag>>>(at*8)&255;}public void write(int at,int value){int shift=at*8;flag=flag&~(255<<shift)|(value&255)<<shift;}});
        memory.bind(CANDIDATE,Pc1FrameLayout.FRAME_BYTES,new Pc1NativeMemory.Region(){public int read(int at)throws IOException{return raw.readU8(at);}public void write(int at,int value)throws IOException{raw.writeU8(at,value);}});bound=true;
    }
    private void candidate(Pc1Frame next)throws IOException{raw.copyFrom(next.nativeFrame());candidate=next.bindNativeFrame(raw);}
    /** Restore into the original BSS owner so existing bus/consumer views stay
     *bound. Legacy capture metadata carries no second8004-byte authority. */
    public void copyFrom(Pc1SaveContext source)throws IOException{if(source==null)throw new IOException("PC1 candidate source missing");raw.copyFrom(source.raw);flag=source.flag;candidate=source.candidate==null?null:source.candidate.bindNativeFrame(raw);}
    public static final class Selection {
        public final int slot;
        public final Pc1Frame frame;
        private Selection(int slot,Pc1Frame frame){this.slot=slot;this.frame=frame;}
    }
    public int flag(){return flag;}
    public Pc1Frame candidate(){return candidate;}
    /** 411670 clears BB8418 for normal/return-to-story load modes1/3.
     * Mode0 borrowed system loads and mode2 keep it; the candidate buffer itself
     * is not cleared by this loader branch. */
    public void scriptLoaded(int nativeMode)throws IOException{
        if(nativeMode<0||nativeMode>3)throw new IOException("PC1 native loader mode");if(nativeMode==1||nativeMode==3)flag=0;
    }
    public void prepare(PcHost host,int opcodeOffset,int packedSound)throws IOException{
        if(host==null||host.script==null)throw new IOException("PC1 save-context has no caller script");
        MesScript.Record head=host.script.fetchRecord(opcodeOffset);if(head.opcode!=0x1b||head.args.length!=2)throw new IOException("PC1 save-context1B head");
        Pc1Frame next=Pc1Frame.capturePrepared(host,opcodeOffset,packedSound);candidate(next);flag=head.args[0];
    }
    /** Input-event path is distinct from opcode26, which never refreshes this
     *candidate. The event caller chooses capture's order relative to voice stop. */
    public boolean captureNativeEvent(PcHost host,MesScript caller,int eventVmPc,int packedSound)throws IOException{
        if(flag!=0)return false;Pc1Frame next=Pc1Frame.captureEvent(host,caller,eventVmPc,packedSound);candidate(next);return true;
    }
    /**4199E6: toolbar:q copies the current frame unconditionally, including
     *when1B left a nonzeroBB8418. It does not clear that flag or stop voice. */
    public void captureQuickEvent(PcHost host,MesScript caller,int eventVmPc,int packedSound)throws IOException{
        candidate(Pc1Frame.captureEvent(host,caller,eventVmPc,packedSound));
    }
    public boolean needsCurrentFrame(int persistArgument){return persistArgument==1&&flag==0;}
    /** persist4(a1==1) saves slot0, refreshing only when BB8418==0.
     * Other a1 values use selected native ID and never refresh the candidate. */
    public Selection selectForPersist4(int argument,int selectedNativeId,Pc1Frame current)throws IOException{
        if(argument<0||argument>255)throw new IOException("PC1 persist4 argument");
        if(needsCurrentFrame(argument)){
            if(current==null||!current.capturedForPersistCurrent())throw new IOException("PC1 persist4 requires current after-head frame");candidate(current);
        }
        if(candidate==null)throw new IOException("PC1 save candidate has not been prepared");int slot=argument==1?0:selectedNativeId;if(slot<0)throw new IOException("PC1 selected save ID");
        return new Selection(slot,candidate);
    }
    /** Native save updates its candidate before attempting file creation. No
     *filesystem operation or implicit whole-VM projection happens here. */
    public Selection prepareWrite(Selection selected,int live9_2,int live9_20,Pc1Frame.SaveMetadata local)throws IOException{
        if(selected==null||selected.frame!=candidate)throw new IOException("Stale PC1 save selection");
        Pc1Frame next=candidate.withNativeSaveMetadata(live9_2,live9_20,local);candidate(next);return new Selection(selected.slot,candidate);
    }
    public void save(DataOutput out)throws IOException{out.writeInt(FORMAT);out.writeInt(flag);raw.save(out);out.writeBoolean(candidate!=null);if(candidate!=null)candidate.saveBound(out);}
    public void load(DataInput in)throws IOException{
        if(in.readInt()!=FORMAT)throw new IOException("PC1 save-context format");int nextFlag=in.readInt();Pc1NativeFrame image=Pc1NativeFrame.load(in);Pc1Frame next=in.readBoolean()?Pc1Frame.loadBound(in,image):null;raw.copyFrom(image);flag=nextFlag;candidate=next==null?null:next.bindNativeFrame(raw);
    }
}
