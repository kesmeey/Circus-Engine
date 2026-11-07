package org.circus.core;

import java.io.*;
import java.util.*;

/** PC1 412160 / return1C suspended owners. This is not a VM/whole-host snapshot.
 * Ordinary variables, their history, persistent preferences and live audio remain
 * owned by the running VM/host. The frame's logical BGM request and packed SE
 * request are distinct from physical playback arrays. The SE restart is a
 * separate return action, not permission to restore the complete audio arrays. */
public final class Pc1SystemContext {
    private static final int FORMAT=0x50314336;
    private static final int BOUND_FORMAT=0x50314342;
    private static final int[] RESOURCE_SLOTS={1,2,3,6,7,10,11,12};
    public static final class ReturnPoint {
        public final String script,sha256;
        public final int pc;
        ReturnPoint(String script,String sha256,int pc){this.script=script;this.sha256=sha256;this.pc=pc;}
    }
    /** Portable presentation adapter, not the native global text buffers.
     *Production return must re-expand the restored raw log when count>0. Some
     *mode/layout scalars remain here;40C's list tables have another owner. */
    public static final class TextState {
        public final String speaker,body,lineVoice,checkpointLabel;
        public final int inputMode,waitIndicatorStyle,checkpoint;
        public final boolean textWaiting,textRendered,presentationWaiting,speakerPending;
        TextState(Dc2Host h){
            speaker=h.speaker;body=h.body;lineVoice=h.lineVoice;checkpointLabel=h.checkpointLabel;
            inputMode=h.inputMode;waitIndicatorStyle=h.waitIndicatorStyle;checkpoint=h.checkpoint;
            textWaiting=h.textWaiting;textRendered=h.textRendered;presentationWaiting=h.presentationWaiting;speakerPending=h.speakerPending;
        }
        TextState(DataInput in)throws IOException{
            speaker=in.readUTF();body=in.readUTF();lineVoice=in.readUTF();checkpointLabel=in.readUTF();
            inputMode=in.readInt();waitIndicatorStyle=in.readInt();checkpoint=in.readInt();
            textWaiting=in.readBoolean();textRendered=in.readBoolean();presentationWaiting=in.readBoolean();speakerPending=in.readBoolean();
        }
        void save(DataOutput out)throws IOException{
            out.writeUTF(speaker);out.writeUTF(body);out.writeUTF(lineVoice);out.writeUTF(checkpointLabel);
            out.writeInt(inputMode);out.writeInt(waitIndicatorStyle);out.writeInt(checkpoint);
            out.writeBoolean(textWaiting);out.writeBoolean(textRendered);out.writeBoolean(presentationWaiting);out.writeBoolean(speakerPending);
        }
        void restore(Dc2Host h){
            h.speaker=speaker;h.body=body;h.lineVoice=lineVoice;h.checkpointLabel=checkpointLabel;
            h.inputMode=inputMode;h.waitIndicatorStyle=waitIndicatorStyle;h.checkpoint=checkpoint;
            h.textWaiting=textWaiting;h.textRendered=textRendered;h.presentationWaiting=presentationWaiting;h.speakerPending=speakerPending;
        }
    }
    /** 40C1C0/40C0A0's list-page owner: native220x104-byte entries and
     *20x16-byte rectangles. It is separate from active buttons, draw batches,
     *static message templates and the global64-colour palette. */
    private static final class ListState {
        final ScriptList list=new ScriptList();
        final List<Dc2Ui.Button> rectangles;
        final int[] bounds=new int[8];
        final boolean waiting,saveGrid;
        final int hoverSound,clickSound,hovered;
        ListState(PcHost h)throws IOException{
            if(h.list.items.size()>220||h.grid.size()>20)throw new IOException("PC1 native list-page capacity");
            copyList(h.list,list);hovered=h.list.hovered;rectangles=Dc2Ui.copyButtons(h.grid);System.arraycopy(h.gridBounds,0,bounds,0,8);
            waiting=h.gridWaiting;saveGrid=h.saveGrid;hoverSound=h.gridHoverSound;clickSound=h.gridClickSound;
        }
        ListState(DataInput in)throws IOException{
            list.load(in);hovered=in.readInt();rectangles=Dc2Ui.loadButtons(in);for(int n=0;n<8;n++)bounds[n]=in.readInt();
            if(list.items.size()>220||rectangles.size()>20)throw new IOException("PC1 saved list-page capacity");
            waiting=in.readBoolean();saveGrid=in.readBoolean();hoverSound=in.readInt();clickSound=in.readInt();
        }
        void save(DataOutput out)throws IOException{
            list.save(out);out.writeInt(hovered);Dc2Ui.saveButtons(out,rectangles);for(int n:bounds)out.writeInt(n);
            out.writeBoolean(waiting);out.writeBoolean(saveGrid);out.writeInt(hoverSound);out.writeInt(clickSound);
        }
        void restore(PcHost h)throws IOException{
            copyList(list,h.list);h.list.hovered=hovered;h.grid.clear();h.grid.addAll(Dc2Ui.copyButtons(rectangles));System.arraycopy(bounds,0,h.gridBounds,0,8);
            h.gridWaiting=waiting;h.saveGrid=saveGrid;h.gridHoverSound=hoverSound;h.gridClickSound=clickSound;
        }
        private static void copyList(ScriptList source,ScriptList target)throws IOException{
            ByteArrayOutputStream bytes=new ByteArrayOutputStream();source.save(new DataOutputStream(bytes));target.load(new DataInputStream(new ByteArrayInputStream(bytes.toByteArray())));
        }
    }
    private final ReturnPoint caller;
    private final byte[] rawTarget;
    private final TextState text;
    private final ListState list;
    private final Pc1NativeFrame frame;
    private final int externalSelection;
    private final String[] resources;
    private final Dc2Scene.Frame displayed;
    private final UiCapture framebuffer,resourceCapture;
    private final String[] actions;

    /** The decoder has already consumed the arbitrary companion marker and NUL.
     * framebuffer must be a frozen value of the actual displayed composition;
     * this helper never substitutes a newly published pending draw batch. */
    public static Pc1SystemContext capture(PcHost host,String callerScript,int consumedReturnPc,
            int mode,byte[] rawTarget,UiCapture framebuffer,int packedSound)throws IOException{
        requirePc1(host);
        if(mode!=0)throw new IOException("Unsupported PC1 system-call mode "+mode+"; nonzero no-framebuffer path is not mapped");
        if(framebuffer==null||framebuffer.kind!=UiCapture.ARGB)throw new IOException("PC1 system call requires a complete pixel framebuffer");
        if(callerScript==null||consumedReturnPc<0)throw new IOException("Invalid PC1 suspended caller cursor");
        if(host.script==null||!host.script.name.equals(callerScript)||!host.script.profile.id.equals("dcpc-2063"))throw new IOException("PC1 caller script does not match host position");
        if(!host.script.executableOffset(consumedReturnPc))throw new IOException("PC1 consumed return PC is outside caller bytes");
        Binary.safePath(callerScript);target(rawTarget);
        host.pc1.drawMemory().writeInt(Pc1VmGlobals.SET19,0);
        Pc1UiPage.capture(host.pc1.graphicsMemory());
        return new Pc1SystemContext(host,callerScript,consumedReturnPc,rawTarget,framebuffer,packedSound);
    }
    private Pc1SystemContext(PcHost h,String name,int pc,byte[] target,UiCapture framebuffer,int packedSound)throws IOException{
        caller=new ReturnPoint(name,h.script.sha256,pc);rawTarget=target.clone();text=new TextState(h);list=new ListState(h);frame=Pc1FrameCopies.captureSystem(h.pc1.nativeFrame());frame.dword(Pc1NativeSlot.Dword.RAW_CODE_PC,pc-h.script.versionOffset);frame.dword(Pc1NativeSlot.Dword.PACKED_SOUND,packedSound);externalSelection=h.pc1.historySelection();
        resources=new String[RESOURCE_SLOTS.length];for(int n=0;n<resources.length;n++)resources[n]=h.scene.slots[RESOURCE_SLOTS[n]];
        displayed=copy(h.scene.shown);this.framebuffer=framebuffer;resourceCapture=h.uiCapture;
        actions=Arrays.copyOf(h.ui.actions,2);
    }
    private Pc1SystemContext(DataInput in)throws IOException{this(in,null);}
    private Pc1SystemContext(DataInput in,Pc1NativeFrame external)throws IOException{
        if(in.readInt()!=(external==null?FORMAT:BOUND_FORMAT))throw new IOException("PC1 context format");
        String name=in.readUTF();Binary.safePath(name);String hash=in.readUTF();if(!hash.matches("[0-9a-f]{64}"))throw new IOException("PC1 saved caller SHA");int pc=in.readInt();if(pc<0)throw new IOException("PC1 saved return PC");caller=new ReturnPoint(name,hash,pc);
        int size=in.readInt();if(size<1||size>512)throw new IOException("PC1 saved target length");rawTarget=new byte[size];in.readFully(rawTarget);target(rawTarget);
        text=new TextState(in);list=new ListState(in);frame=external==null?Pc1NativeFrame.load(in):external;externalSelection=in.readInt();resources=new String[RESOURCE_SLOTS.length];for(int n=0;n<resources.length;n++)resources[n]=in.readUTF();
        displayed=new Dc2Scene.Frame();displayed.load(in);framebuffer=UiCapture.load(in);if(framebuffer==null||framebuffer.kind!=UiCapture.ARGB)throw new IOException("PC1 saved pixel framebuffer missing");resourceCapture=UiCapture.load(in);
        actions=new String[2];for(int n=0;n<2;n++)actions[n]=in.readUTF();
    }
    public ReturnPoint returnPoint(){return caller;}
    public byte[] rawTarget(){return rawTarget.clone();}
    public UiCapture framebuffer(){return framebuffer;}
    public TextState text(){return text;}
    public Pc1FrameFields fields(){try{return Pc1FrameFields.capture(frame);}catch(IOException impossible){throw new AssertionError(impossible);}}
    public Pc1NativeFrame nativeFrame(){return frame.copy();}
    private Pc1SystemContext(Pc1SystemContext source,Pc1NativeFrame owner){caller=source.caller;rawTarget=source.rawTarget;text=source.text;list=source.list;frame=owner;externalSelection=source.externalSelection;resources=source.resources;displayed=source.displayed;framebuffer=source.framebuffer;resourceCapture=source.resourceCapture;actions=source.actions;}
    Pc1SystemContext bindNativeFrame(Pc1NativeFrame owner)throws IOException{if(owner==null)throw new IOException("PC1 system frame owner missing");return new Pc1SystemContext(this,owner);}
    boolean hasNativeFrame(Pc1NativeFrame owner){return frame==owner;}
    public int historySelection(){return externalSelection;}
    /** The host calls its own version-specific SE service after restoring owners.
     * Native return1C calls41C850(low8,1) only when saved request bit100h is set. */
    public boolean restartsLoopingSound(){return (packedSoundRequest()&0x100)!=0;}
    public int loopingSoundIndex(){return packedSoundRequest()&255;}
    public int packedSoundRequest(){return frame.dword(Pc1NativeSlot.Dword.PACKED_SOUND); }
    public int musicRequest(){return frame.dword(Pc1NativeSlot.Dword.LOGICAL_MUSIC);}

    /** Mode0 relinquishes active background/actor handles without stopping audio.
     * Target loading and its precise first PC belong to MesVm, not beforeTransfer. */
    public void applyEntry(PcHost h)throws IOException{
        requirePc1(h);h.systemScene=copy(displayed);
        h.scene.slots[1]="";h.scene.slots[2]="";h.scene.slots[3]="";
        h.speaker=h.body="";h.textWaiting=h.textRendered=h.presentationWaiting=h.speakerPending=false;
        h.remaining=0;h.audioMask=0;h.returnRequested=false;h.ui.clearCurrent();
    }
    /** Restore reviewed caller owners plus a temporary portable text adapter.
     *Production Pc1Runtime.leave must immediately replay the restored raw log
     *when count>0; the adapter is not restoration of native global text buffers.
     *Does not write Variables, physical playback, global portable history,
     *persistence, clocks, palette, player name or template storage. */
    public ReturnPoint applyReturn(PcHost h)throws IOException{
        requirePc1(h);
        if(h.ticket==Long.MAX_VALUE)throw new IOException("DM event counter exhausted");
        //41503B patches live ordinary into the suspended caller before the
        //8004-byte copy. Later resource rebuild clears the live draw count.
        Pc1FrameCopies.restoreSystem(h.pc1.nativeFrame(),frame);
        h.pc1.nativeFrame().dword(Pc1NativeSlot.Dword.PENDING_COUNT,0);
        for(int n=0;n<resources.length;n++)h.scene.slots[RESOURCE_SLOTS[n]]=resources[n];
        h.scene.shown=copy(displayed);h.scene.mode=0;h.scene.revision++;
        h.uiCapture=resourceCapture;h.systemScene=null;h.ui.clearCurrent();
        Pc1UiPage.restore(h.pc1.graphicsMemory());
        list.restore(h);
        for(int n=0;n<2;n++)h.ui.actions[n]=actions[n];
        text.restore(h);h.pc1.historySelection(externalSelection);h.returnRequested=false;h.remaining=0;h.audioMask=0;
        h.nextTicket();return caller;
    }
    public void save(DataOutput out)throws IOException{
        save(out,false);
    }
    void saveBound(DataOutput out)throws IOException{save(out,true);}
    private void save(DataOutput out,boolean bound)throws IOException{
        out.writeInt(bound?BOUND_FORMAT:FORMAT);out.writeUTF(caller.script);out.writeUTF(caller.sha256);out.writeInt(caller.pc);out.writeInt(rawTarget.length);out.write(rawTarget);
        text.save(out);list.save(out);if(!bound)frame.save(out);out.writeInt(externalSelection);for(String resource:resources)out.writeUTF(resource);
        displayed.save(out);UiCapture.save(out,framebuffer);UiCapture.save(out,resourceCapture);
        for(String action:actions)out.writeUTF(action);
    }
    public static Pc1SystemContext load(DataInput in)throws IOException{return new Pc1SystemContext(in);}
    static Pc1SystemContext loadBound(DataInput in,Pc1NativeFrame owner)throws IOException{if(owner==null)throw new IOException("PC1 system frame owner missing");return new Pc1SystemContext(in,owner);}
    private static Dc2Scene.Frame copy(Dc2Scene.Frame original)throws IOException{
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();original.save(new DataOutputStream(bytes));Dc2Scene.Frame result=new Dc2Scene.Frame();result.load(new DataInputStream(new ByteArrayInputStream(bytes.toByteArray())));return result;
    }
    private static void target(byte[] raw)throws IOException{
        if(raw==null||raw.length==0||raw.length>512)throw new IOException("Invalid PC1 raw system target");
        for(byte b:raw)if(b==0)throw new IOException("NUL inside PC1 raw system target");
        Binary.safePath(new String(raw,Binary.SJIS));
    }
    private static void requirePc1(PcHost host)throws IOException{if(host==null||!host.resources.profile.id.equals("dcpc-2063"))throw new IOException("PC1 system context requires dcpc-2063");}
}
