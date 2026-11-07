package org.circus.core;

import java.io.*;
import java.util.*;

/** Typed PC1 save-candidate frame (native9CDD28), not the system return frame.
 * Native1B copies before consuming its owned string, so the stored PC points to
 *that raw marker. Full native slot loading instead reconstructs its final PC
 *from checkpoint metadata and command type; this is not that loader.
 * Current ordinary1000 bytes ARE saved here. */
public final class Pc1Frame {
    private static final int FORMAT=0x50314635;
    private static final int BOUND_FORMAT=0x50314642;
    private static final int PREPARED=1,CURRENT_PERSIST=2,NATIVE_EVENT=3;
    private static final int[] SLOTS={1,2,3,6,7,10,11,12};
    private final Pc1SystemContext.ReturnPoint point;
    private final Pc1SystemContext.TextState text;
    private final Pc1NativeFrame frame;
    private final byte[] rawString;
    private final int sourceKind,sourceOffset,marker;
    private final String[] resources,actions;
    private final List<Dc2Ui.Quad> pendingQuads;
    public static final class SaveMetadata {
        public final int year,month,day,hour,minute;
        /** Local platform time supplied by the caller, matching GetLocalTime. */
        public SaveMetadata(int year,int month,int day,int hour,int minute)throws IOException{
            if(year<0||year>65535||month<1||month>12||day<1||day>31||hour<0||hour>23||minute<0||minute>59)throw new IOException("PC1 native local save time");
            this.year=year;this.month=month;this.day=day;this.hour=hour;this.minute=minute;
        }
    }

    public static Pc1Frame capturePrepared(PcHost host,int opcodeOffset,int packedSound)throws IOException{
        MesScript.Record head=head(host,opcodeOffset,0x1b);if(head.args.length!=2)throw new IOException("PC1 1B head shape");
        MesScript.Record payload=host.script.stringPayload(head.end,head.offset);
        return new Pc1Frame(host,head.end,PREPARED,head.offset,payload.opcode,payload.bytes,packedSound);
    }
    public static Pc1Frame capturePersistCurrent(PcHost host,int opcodeOffset,int packedSound)throws IOException{
        MesScript.Record head=head(host,opcodeOffset,0x15);if(head.args.length!=2||head.args[0]!=4)throw new IOException("PC1 persist4 head shape");
        return new Pc1Frame(host,head.end,CURRENT_PERSIST,head.offset,0,new byte[0],packedSound);
    }
    /** A native input handler captures the current interpreter PC while a wait
     *is active. host.pc still names its last07/09 head and must not be substituted. */
    public static Pc1Frame captureEvent(PcHost host,MesScript caller,int eventVmPc,int packedSound)throws IOException{
        require(host);if(caller==null||host.script==null||!caller.profile.id.equals("dcpc-2063")||!caller.name.equals(host.script.name)||!caller.sha256.equals(host.script.sha256))throw new IOException("PC1 native event caller mismatch");
        return new Pc1Frame(host,eventVmPc,NATIVE_EVENT,-1,0,new byte[0],packedSound);
    }
    private Pc1Frame(PcHost host,int pc,int kind,int sourceOffset,int marker,byte[] rawString,int packedSound)throws IOException{
        if(!host.script.executableOffset(pc))throw new IOException("PC1 candidate cursor outside caller");if(rawString.length>1024)throw new IOException("PC1 candidate raw string length");
        point=new Pc1SystemContext.ReturnPoint(host.script.name,host.script.sha256,pc);text=new Pc1SystemContext.TextState(host);frame=Pc1FrameCopies.captureCandidate(host.pc1.nativeFrame());frame.dword(Pc1NativeSlot.Dword.RAW_CODE_PC,pc-host.script.versionOffset);frame.dword(Pc1NativeSlot.Dword.PACKED_SOUND,packedSound);
        sourceKind=kind;this.sourceOffset=sourceOffset;this.marker=marker;this.rawString=rawString.clone();
        resources=new String[SLOTS.length];for(int n=0;n<SLOTS.length;n++)resources[n]=host.scene.slots[SLOTS[n]];
        actions=Arrays.copyOf(host.ui.actions,2);
        if(host.ui.quads.size()>60)throw new IOException("PC1 candidate pending draw capacity");pendingQuads=Dc2Ui.copyQuads(host.ui.quads);
    }
    private Pc1Frame(DataInput in)throws IOException{
        this(in,null);
    }
    private Pc1Frame(DataInput in,Pc1NativeFrame external)throws IOException{
        if(in.readInt()!=(external==null?FORMAT:BOUND_FORMAT))throw new IOException("PC1 candidate frame format");String name=in.readUTF();Binary.safePath(name);String sha=in.readUTF();if(!sha.matches("[0-9a-f]{64}"))throw new IOException("PC1 candidate SHA");int pc=in.readInt();if(pc<0)throw new IOException("PC1 candidate cursor");point=new Pc1SystemContext.ReturnPoint(name,sha,pc);
        sourceKind=in.readUnsignedByte();if(sourceKind!=PREPARED&&sourceKind!=CURRENT_PERSIST&&sourceKind!=NATIVE_EVENT)throw new IOException("PC1 candidate source kind");sourceOffset=in.readInt();if(sourceKind==NATIVE_EVENT?sourceOffset!=-1:sourceOffset<0||sourceOffset+3L!=pc)throw new IOException("PC1 candidate source cursor");marker=in.readUnsignedByte();int size=in.readInt();if(size<0||size>1024||sourceKind!=PREPARED&&size!=0)throw new IOException("PC1 candidate raw string length");rawString=new byte[size];in.readFully(rawString);for(byte b:rawString)if(b==0)throw new IOException("PC1 candidate raw string NUL");
        frame=external==null?Pc1NativeFrame.load(in):external;text=new Pc1SystemContext.TextState(in);
        resources=new String[SLOTS.length];for(int n=0;n<SLOTS.length;n++)resources[n]=in.readUTF();actions=new String[2];for(int n=0;n<2;n++)actions[n]=in.readUTF();pendingQuads=Dc2Ui.loadQuads(in,60);
    }
    private Pc1Frame(Pc1Frame source,Pc1NativeFrame nextFrame)throws IOException{
        point=source.point;text=source.text;frame=nextFrame;rawString=source.rawString;sourceKind=source.sourceKind;sourceOffset=source.sourceOffset;marker=source.marker;
        resources=source.resources;actions=source.actions;pendingQuads=source.pendingQuads;
    }
    /** Raw PC recorded in the candidate, not41A620's final reconstructed PC. */
    public Pc1SystemContext.ReturnPoint returnPoint(){return point;}
    public boolean preparedBy1B(){return sourceKind==PREPARED;}
    public boolean capturedFromEvent(){return sourceKind==NATIVE_EVENT;}
    public boolean capturedForPersistCurrent(){return sourceKind==CURRENT_PERSIST;}
    public int sourceOpcodeOffset(){return sourceOffset;}
    public int rawCompanionMarker(){return marker;}
    public byte[] rawOwnedString(){return rawString.clone();}
    public byte[] ordinaryBytes(){try{return frame.rawSlice(Pc1FrameLayout.ORDINARY_OFFSET,Pc1FrameLayout.ORDINARY_BYTES);}catch(IOException impossible){throw new AssertionError(impossible);}}
    public Pc1NativeFrame nativeFrame(){return frame.copy();}
    public int packedSoundRequest(){return frame.dword(Pc1NativeSlot.Dword.PACKED_SOUND); }
    public int musicRequest(){return frame.dword(Pc1NativeSlot.Dword.LOGICAL_MUSIC);}
    public int checkpointIndex(){return fields().currentCheckpoint();}
    public Pc1FrameFields fields(){try{return Pc1FrameFields.capture(frame);}catch(IOException impossible){throw new AssertionError(impossible);}}
    /** Same raw candidate owner; metadata is only legacy capture provenance. */
    Pc1Frame bindNativeFrame(Pc1NativeFrame owner)throws IOException{if(owner==null)throw new IOException("PC1 candidate backing missing");return new Pc1Frame(this,owner);}
    boolean hasNativeFrame(Pc1NativeFrame owner){return frame==owner;}
    /** Pure native41A620 checkpoint projection, after matching caller identity.
     *The saved raw PC is reported separately; this performs no load or I/O. */
    public Pc1SlotProjection projection(MesScript caller,int currentMusic)throws IOException{
        if(caller==null||!caller.profile.id.equals("dcpc-2063")||!caller.profile.canExecuteVersion(caller.version)||!caller.name.equals(point.script)||!caller.sha256.equals(point.sha256))throw new IOException("PC1 candidate projection caller identity changed");
        if(!caller.executableOffset(point.pc))throw new IOException("PC1 candidate projection raw PC outside caller");
        Pc1FrameFields current=fields();return Pc1SlotProjection.project(caller,current.currentCheckpoint(),new Pc1SlotProjection.SavedFields(frame.dword(Pc1NativeSlot.Dword.RAW_CODE_PC),current.argument0,current.argument1,musicRequest()),currentMusic);
    }
    /** 404E70 patches candidate9:2/9:20 from current ordinary state, followed
     *by local month/day/hour/minute/year fields. This occurs before file open. */
    public Pc1Frame withNativeSaveMetadata(int live9_2,int live9_20,SaveMetadata local)throws IOException{
        if(local==null||live9_2<0||live9_2>255||live9_20<0||live9_20>255)throw new IOException("PC1 native save metadata");
        byte[] next=ordinaryBytes();next[902]=(byte)live9_2;next[920]=(byte)live9_20;next[921]=(byte)local.month;next[922]=(byte)local.day;next[923]=(byte)local.hour;next[924]=(byte)local.minute;next[925]=(byte)(local.year+0x30);
        Pc1NativeFrame patched=frame.copy();patched.writeBytes(Pc1FrameLayout.ORDINARY_OFFSET,next);return new Pc1Frame(this,patched);
    }
    /** Partial typed frame restoration only, not the complete native slot loader.
     *Validate the caller's profile/SHA/cursor before this method. A save load,
     * unlike system-return1C, replaces ordinary bytes without touching the Java
     * history shadow/log or the separate persistent/audio/UI-list owners. */
    public void apply(PcHost host)throws IOException{
        require(host);Pc1FrameCopies.restoreCandidate(host.pc1.nativeFrame(),frame);
        text.restore(host);for(int n=0;n<SLOTS.length;n++)host.scene.slots[SLOTS[n]]=resources[n];
        for(int n=0;n<2;n++)host.ui.actions[n]=actions[n];
        host.ui.quads.clear();host.ui.quads.addAll(Dc2Ui.copyQuads(pendingQuads));
    }
    public void save(DataOutput out)throws IOException{
        save(out,false);
    }
    void saveBound(DataOutput out)throws IOException{save(out,true);}
    private void save(DataOutput out,boolean bound)throws IOException{
        out.writeInt(bound?BOUND_FORMAT:FORMAT);out.writeUTF(point.script);out.writeUTF(point.sha256);out.writeInt(point.pc);out.writeByte(sourceKind);out.writeInt(sourceOffset);out.writeByte(marker);out.writeInt(rawString.length);out.write(rawString);if(!bound)frame.save(out);text.save(out);
        for(String resource:resources)out.writeUTF(resource);for(String action:actions)out.writeUTF(action);Dc2Ui.saveQuads(out,pendingQuads);
    }
    public static Pc1Frame load(DataInput in)throws IOException{return new Pc1Frame(in);}
    static Pc1Frame loadBound(DataInput in,Pc1NativeFrame owner)throws IOException{if(owner==null)throw new IOException("PC1 bound candidate owner missing");return new Pc1Frame(in,owner);}
    private static MesScript.Record head(PcHost host,int offset,int opcode)throws IOException{
        require(host);if(host.script==null||!host.script.profile.id.equals("dcpc-2063"))throw new IOException("PC1 candidate has no caller script");MesScript.Record record=host.script.fetchRecord(offset);if(record.opcode!=opcode)throw new IOException("PC1 candidate opcode mismatch");return record;
    }
    private static void require(PcHost host)throws IOException{if(host==null||!host.resources.profile.id.equals("dcpc-2063")||host.variables==null)throw new IOException("PC1 candidate requires dcpc-2063");}
}
