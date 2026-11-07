package org.circus.core;

import java.io.*;
import java.util.*;

/** Cooperative, bounded interpreter. Unknown instructions stop with a precise diagnostic. */
public final class MesVm {
    /** Optional session observer, called after a successful instruction attempt
     * has closed all host-local dispatch scopes and before another fetch. */
    public interface BoundaryListener {void reached()throws IOException;}
    private BoundaryListener boundaryListener;
    public void boundaryListener(BoundaryListener listener){boundaryListener=listener;}
    public interface Library { MesScript script(String name)throws IOException; }
    public interface Host extends Variables.Special {
        boolean command(String semantic,int[] args,byte[] bytes)throws IOException;
        boolean allowNamedJump()throws IOException;
        boolean beforeTransfer(int mode,String name)throws IOException;
        void save(DataOutput out)throws IOException;
        void load(DataInput in)throws IOException;
    }
    /** Presentation is acknowledged separately from user advancement. */
    public interface FlowHost extends Host {
        boolean beforeInstruction(Profile profile,int opcode)throws IOException;
        boolean waiting();
        long waitTicket();
        boolean signal(long ticket,HostSignal signal)throws IOException;
    }
    public interface ContextHost extends FlowHost {
        void attach(Variables variables);
        /** First successful native load happens before callers can mutate live
         *owners, not lazily at the first instruction's position callback. */
        default void loadedInitial(MesScript script,int nextOffset)throws IOException{}
        void position(MesScript script,int pc);
        /** The fetch has actually happened; a preceding wait never calls this. */
        default void fetched(MesScript.Instruction instruction)throws IOException{}
        /** Commit dispatcher-local bookkeeping after the command's real effects. */
        default void dispatched(MesScript.Instruction instruction)throws IOException{}
        /** Attempt scope closes on STOP and on a precise handler failure too. */
        default void instructionFinished(){}
        /** Rebind nonserialized code references without replacing saved command/history positions. */
        default void restored(MesScript script,int nextOffset)throws IOException{}
        void elapse(long milliseconds);
    }
    /** PC1 live9D8A14 shares canonical bytes with variable/frame aliases.
     *Values are raw32-bit code-block-relative PCs, not file offsets. */
    public interface NativePcHost extends Host {
        int readNativeCodePc()throws IOException;
        void writeNativeCodePc(int codePc)throws IOException;
        default Pc1CodeBuffer nativeCodeBuffer(){return null;}
        void nativeHeaderLoaded(MesScript original)throws IOException;
        default void beforeNativeRecordFetch(int opcode,int codeOffset)throws IOException{}
        default void writeNativeArgument0(int value)throws IOException{}
        default void beforeNativeScriptLoad(int opcode,String target)throws IOException{}
        /** The actual handler consumes its own companion bytes and may alter
         *the canonical PC through a pending-record alias while doing so. */
        default boolean ownsNativeProducerPc(int opcode){return false;}
    }
    /** Typed native call/return owners. The running Variables object is never replaced. */
    public interface ScriptSystemHost extends Host {
        void enterSystemContext(MesScript caller,int returnPc,int mode,byte[] rawTarget)throws IOException;
        default void enterSystemEvent(MesScript caller,int returnPc,byte[] rawTarget,int eventKind)throws IOException{throw new IOException("Native system input event is not mapped");}
        SystemReturn systemReturnPoint()throws IOException;
        void leaveSystemContext()throws IOException;
        default void leaveSystemContext(MesScript verifiedCaller)throws IOException{leaveSystemContext();}
    }
    public static final class SystemReturn {
        public final String script,sha256;public final int offset;
        public SystemReturn(String script,String sha256,int offset){this.script=script;this.sha256=sha256;this.offset=offset;}
    }
    public interface ChoiceHost extends Host {
        void addChoice(byte[] label,byte[] caption)throws IOException;
        void selectChoice(byte[] label,int index)throws IOException;
    }
    public interface MenuHost extends FlowHost {
        byte[] menuAction(long ticket,int button)throws IOException;
        void acceptMenuAction(int button)throws IOException;
        default boolean internalMenuAction(long ticket,int button)throws IOException{return false;}
        default byte[] pollMenuAction()throws IOException{return null;}
        default byte[] automaticMenuAction(){return null;}
        default boolean ignoreMissingMenuLabel(){return false;}
    }
    /** A platform may replace a desktop menu script with its own title screen. */
    public interface TitleHost extends Host { boolean returnToTitle(int mode,String script,byte[] entryLabel)throws IOException; }
    public enum HostSignal { TEXT_COMPLETE, ADVANCE, MEDIA_COMPLETE }
    public enum Status { RUNNING, WAIT_CLICK, WAIT_CHOICE, WAIT_TIME, WAIT_HOST, HALTED, FAULT }
    private final Library library;private final Host host;
    public final Variables variables;
    private MesScript script;private int pc;private byte[] pendingLabel=new byte[0];
    private Status status=Status.RUNNING;private String error="";private long remainingMs;
    public MesVm(Library lib,Host host,String entry)throws IOException{
        this.library=lib;this.host=host;script=loadScript(entry);this.variables=new Variables(host,script.profile.variableModel);pc=script.firstOffset;
        if(host instanceof ContextHost)((ContextHost)host).attach(variables);
        script=activateScript(script);
        if(host instanceof ContextHost)((ContextHost)host).loadedInitial(script,pc);
        commitCursor(pc);
    }
    public Status status(){return status;} public String error(){return error;}
    public String scriptName(){return script.name;} public int offset(){if(nativePc(script))try{return script.versionOffset+((NativePcHost)host).readNativeCodePc();}catch(IOException failure){throw new IllegalStateException(failure);}return pc;}
    public String profileId(){return script.profile.id;}
    private boolean nativePc(MesScript owner){return owner.profile.id.equals("dcpc-2063")&&host instanceof NativePcHost;}
    private Pc1CodeBuffer codeOwner(MesScript owner){return nativePc(owner)?((NativePcHost)host).nativeCodeBuffer():null;}
    private MesScript activateScript(MesScript original)throws IOException{Pc1CodeBuffer buffer=codeOwner(original);if(buffer==null)return original;((NativePcHost)host).nativeHeaderLoaded(original);buffer.load(original);return original.nativeView(buffer);}
    private MesScript bindExistingCode(MesScript original)throws IOException{Pc1CodeBuffer buffer=codeOwner(original);return buffer==null?original:original.nativeView(buffer);}
    private boolean knownNativePc(MesScript owner,int filePc){long limit=codeOwner(owner)==null?Math.min((long)owner.length(),(long)owner.versionOffset+100000):(long)owner.versionOffset+Pc1CodeBuffer.CAPACITY;return filePc>=owner.versionOffset&&(long)filePc<limit;}
    private int readNativeCursor(MesScript owner)throws IOException{
        int raw=((NativePcHost)host).readNativeCodePc();long file=(long)owner.versionOffset+raw;
        if(file<Integer.MIN_VALUE||file>Integer.MAX_VALUE||!knownNativePc(owner,(int)file))throw new IOException("PC1 native code PC "+raw+" is outside the currently known loaded code owner");return(int)file;
    }
    private void commitCursor(int filePc)throws IOException{pc=filePc;if(nativePc(script))((NativePcHost)host).writeNativeCodePc(filePc-script.versionOffset);}
    private void advanceNativeCursor(int bytes)throws IOException{NativePcHost nativeHost=(NativePcHost)host;nativeHost.writeNativeCodePc(nativeHost.readNativeCodePc()+bytes);}
    private void nativeArgument0(int value)throws IOException{if(nativePc(script))((NativePcHost)host).writeNativeArgument0(value);}
    private void knownNativeRead(int offset,int length)throws IOException{long end=(long)offset+length,limit=Math.min((long)script.length(),(long)script.versionOffset+100000);if(offset<script.versionOffset||length<0||end>limit)throw new IOException("PC1 fetch reads beyond currently known loaded code owner");}
    private void resumeReleasedHost(){if(status==Status.WAIT_HOST&&host instanceof FlowHost&&!((FlowHost)host).waiting())status=Status.RUNNING;}
    private MesScript loadScript(String name)throws IOException {
        MesScript loaded=library.script(name);
        if(!loaded.profile.canExecuteVersion(loaded.version))throw new IOException(String.format(
            "Native loader rejects MES marker 0x%04X for %s (%s)",loaded.version,loaded.profile.id,name));
        return loaded;
    }
    /** Host-owned system call; the caller snapshot is retained by the session. */
    public void enterSystem(String name)throws IOException {
        MesScript target=loadScript(Binary.safePath(name));
        if(!target.profile.id.equals(script.profile.id))throw new IOException("Cross-profile system call");
        script=activateScript(target);commitCursor(target.firstOffset);pendingLabel=new byte[0];remainingMs=0;status=Status.RUNNING;
        if(host instanceof ContextHost)((ContextHost)host).restored(script,pc);
    }
    /** Native input has its own candidate/voice order; its cursor is this VM's
     * already-consumed PC, not ContextHost's last dispatched command address. */
    public void enterNativeSystem(String name,int eventKind)throws IOException {
        if(!(host instanceof ScriptSystemHost))throw new IOException("Native system event requires typed owners");
        if(pendingLabel.length!=0)throw new IOException("PC1 system event with pending entry label is not yet verified");
        String safe=Binary.safePath(name);MesScript target=loadScript(safe);
        if(!target.profile.id.equals(script.profile.id))throw new IOException("Cross-profile native system event");
        if(nativePc(script))pc=readNativeCursor(script);
        ((ScriptSystemHost)host).enterSystemEvent(script,pc,safe.getBytes(Binary.SJIS),eventKind);
        script=activateScript(target);commitCursor(target.firstOffset);pendingLabel=new byte[0];remainingMs=0;status=Status.RUNNING;
        if(host instanceof ContextHost)((ContextHost)host).restored(script,pc);
    }
    public long remainingMillis(){return remainingMs;}
    /** A native return uses the current live Variables owner, never a caller VM snapshot. */
    public boolean returnSystemContext()throws IOException {
        SystemReturn caller=host instanceof ScriptSystemHost?((ScriptSystemHost)host).systemReturnPoint():null;
        if(caller==null)return false;
        MesScript target=loadScript(Binary.safePath(caller.script));
        if(!target.profile.id.equals(script.profile.id)||!target.sha256.equals(caller.sha256))throw new IOException("Native system caller script changed");
        if(!(nativePc(target)?knownNativePc(target,caller.offset):target.executableOffset(caller.offset)))throw new IOException("Native system return PC is outside caller bytes");
        ((ScriptSystemHost)host).leaveSystemContext(target);
        script=bindExistingCode(target);commitCursor(caller.offset);pendingLabel=new byte[0];remainingMs=0;status=Status.RUNNING;
        if(host instanceof ContextHost)((ContextHost)host).restored(script,pc);
        if(host instanceof FlowHost&&((FlowHost)host).waiting())status=Status.WAIT_HOST;
        return true;
    }
    public void advance(){if(status==Status.WAIT_CLICK)status=Status.RUNNING;}
    public boolean signalHost(long ticket,HostSignal signal)throws IOException {
        if(status!=Status.WAIT_HOST||!(host instanceof FlowHost))return false;
        FlowHost flow=(FlowHost)host;boolean accepted=flow.signal(ticket,signal);
        if(accepted)resumeReleasedHost();return accepted;
    }
    public void elapse(long milliseconds){if(status==Status.WAIT_TIME){remainingMs=Math.max(0,remainingMs-Math.max(0,milliseconds));if(remainingMs==0)status=Status.RUNNING;}if(host instanceof ContextHost){((ContextHost)host).elapse(Math.max(0,milliseconds));resumeReleasedHost();}}
    public void choose(byte[] label,int index)throws IOException {
        if(status!=Status.WAIT_CHOICE)throw new IOException("VM is not waiting for a choice");
        Integer dest=script.label(label);if(dest==null)throw new IOException("Choice target missing");
        if(host instanceof ChoiceHost)((ChoiceHost)host).selectChoice(label,index);
        else host.write(99,245,index);
        commitCursor(dest);status=Status.RUNNING;
    }
    /** Resolve the original script label before accepting an input event. */
    public boolean activateMenu(long ticket,int button)throws IOException {
        if(status!=Status.WAIT_HOST||!(host instanceof MenuHost))return false;
        MenuHost menu=(MenuHost)host;if(menu.internalMenuAction(ticket,button)){resumeReleasedHost();return true;}byte[] action=menu.menuAction(ticket,button);if(action==null)return false;
        Integer target=script.label(action);if(target==null){if(menu.ignoreMissingMenuLabel())return false;throw new IOException("Unsupported UI action: "+new String(action,Binary.SJIS));}
        menu.acceptMenuAction(button);commitCursor(target);status=Status.RUNNING;return true;
    }
    public boolean activateGrid(long ticket,int index)throws IOException {
        if(!(host instanceof FlowHost)||status!=Status.WAIT_HOST||ticket!=((FlowHost)host).waitTicket())return false;
        byte[] action=host instanceof Dc2Host?((Dc2Host)host).menuActionForGrid(index):host instanceof Dc3Host?((Dc3Host)host).menuActionForGrid(index):null;if(action==null)return false;Integer target=script.label(action);if(target==null)throw new IOException("Grid action label missing");commitCursor(target);status=Status.RUNNING;return true;
    }
    private void emit(String op,int[] args,byte[] bytes)throws IOException{if(!host.command(op,args,bytes))throw new IOException("Unimplemented host effect: "+op);}
    /** Instruction trace used by the reverse-engineering probes; off unless -Dcircus.trace is set. */
    static final boolean TRACE=System.getProperty("circus.trace")!=null;
    private void trace(MesScript s,int at,int opcode,String op,int[] args){if(!TRACE)return;if(!s.name.toLowerCase(Locale.ROOT).contains(System.getProperty("circus.trace").toLowerCase(Locale.ROOT)))return;
        StringBuilder b=new StringBuilder(String.format("  %05X %02X %-26s",at,opcode,op));for(int v:args)b.append(' ').append(v);System.out.println(b);}
    public Status run(int budget){
        if(budget<=0||budget>100000)throw new IllegalArgumentException("Instruction budget must be 1..100000");
        if((status==Status.WAIT_HOST||status==Status.RUNNING)&&host instanceof MenuHost){try{byte[] action=((MenuHost)host).pollMenuAction();if(action!=null){Integer target=script.label(action);if(target==null)throw new IOException("Timed menu target missing");commitCursor(target);status=Status.RUNNING;}}catch(IOException e){status=Status.FAULT;error=script.name+": "+e.getMessage();}}
        // A native frame can release its wait without producing a named jump.
        resumeReleasedHost();
        for(int i=0;i<budget && status==Status.RUNNING;i++){
            try{step();if(boundaryListener!=null)boundaryListener.reached();}catch(IOException|RuntimeException e){status=Status.FAULT;error=script.name+" @0x"+Integer.toHexString(pc)+": "+e.getMessage();}
        }
        return status;
    }
    private void step()throws IOException{try{stepInstruction();}finally{if(host instanceof ContextHost)((ContextHost)host).instructionFinished();}}
    private void stepInstruction()throws IOException {
        if(nativePc(script))pc=readNativeCursor(script);
        MesScript executingScript=script;Profile p=script.profile;int opcode=script.rawOpcode(pc);
        if(host instanceof ContextHost)((ContextHost)host).position(script,pc);
        if(host instanceof FlowHost&&((FlowHost)host).beforeInstruction(p,opcode)){status=Status.WAIT_HOST;return;}
        boolean bound=nativePc(script);
        MesScript.Record nativeHead=null;
        if(bound){((NativePcHost)host).beforeNativeRecordFetch(opcode,pc-script.versionOffset);nativeHead=script.fetchRecord(pc);knownNativeRead(nativeHead.offset,nativeHead.terminalFragment?3:nativeHead.end-nativeHead.offset);if(nativeHead.opcode!=0)((NativePcHost)host).writeNativeCodePc(nativeHead.end-script.versionOffset);}
        boolean ownedProducer=bound&&((NativePcHost)host).ownsNativeProducerPc(opcode);
        MesScript.Instruction instruction=ownedProducer?new MesScript.Instruction(nativeHead,nativeHead.args,nativeHead.end,null):script.instruction(pc);MesScript.Record r=instruction.record;
        if(bound)knownNativeRead(r.offset,instruction.next-r.offset);
        if(host instanceof ContextHost)((ContextHost)host).fetched(instruction);
        String op=p.operation(opcode);int next=instruction.next;
        boolean nativeControl=false,nativeExtension=bound&&!ownedProducer&&p.ownsExtension(r.opcode);
        if(nativeExtension)advanceNativeCursor(1);
        int[] args=instruction.args;
        trace(script,pc,r.opcode,op,args);
        //412647's unsigned bound sends every raw opcode>62h to real default,
        //including MOVSX-negative short fetches. Defined but unmodelled cases
        //inside00..62h retain their precise unsupported behavior.
        if(bound&&r.opcode>0x62)op="native.default_pc1";
        if(op.equals("unknown"))throw new IOException(String.format("Unsupported opcode 0x%02X (%s)",r.opcode,p.id));
        switch(op){
        case "stream.stop": status=Status.HALTED;return;
        case "system.exit":emit(op,args,r.bytes);status=Status.HALTED;return;
        case "control.marker": case "label.named": case "arguments.extend": case "choice.caption":case "native.default_pc1": break;
        case "checkpoint":{
            int id=args[0]|args[1]<<8;if(id>=script.headerMetadata.length)throw new IOException("Checkpoint outside metadata table");
            emit(op,new int[]{id,script.headerMetadata[id]},new byte[0]);break;
        }
        case "variable.update":case "variable.update_indirect":{
            int mode=args[3];if(mode>=0 && mode<=2){int v=args[2];if(mode!=0){int old=variables.read(args[0],args[1]);v=mode==1?old+v:old-v;}variables.write(args[0],args[1],v);}
            else if(mode==3&&op.equals("variable.update_indirect"))emit("variable.indirect_result",new int[]{variables.read(args[2],variables.read(args[0],args[1]))},new byte[0]);
            break;
        }
        case "variable.copy": variables.write(args[0],args[1],variables.read(args[2],args[3]));break;
        case "variable.clear_ordinary_bytes":variables.clearOrdinaryBytes();break;
        case "variable.add_variable": variables.write(args[0],args[1],variables.read(args[0],args[1])+variables.read(args[2],args[3]));break;
        case "variable.sum_list":{
            int sum=0,steps=0;
            while(next<script.length() && script.rawOpcode(next)!=p.companion){
                MesScript.Record term=script.wordPayload(next,pc);
                if(bound){knownNativeRead(next,9);advanceNativeCursor(9);}sum+=variables.read(term.args[0],term.args[1]);next=term.end;if(++steps>100000)throw new IOException("Sum list limit");
            }
            if(next>=script.length())throw new IOException("Sum terminator missing");variables.write(args[0],args[1],sum);break;
        }
        case "branch.compare":{
            int left=variables.read(args[0],args[1]),right=args[2],mode=args[3];
            if(bound)nativeArgument0(left);
            if((mode&128)!=0){right=variables.read(right>>8,right&255);mode&=127;}
            boolean pass;switch(mode){case 0:pass=left==right;break;case 1:pass=left!=right;break;case 2:pass=left>right;break;case 3:pass=left<right;break;case 4:pass=left>=right;break;case 5:pass=left<=right;break;default:if(!bound)throw new IOException("Unknown comparison mode");pass=left!=0;}
            if(bound&&mode>=0&&mode<=5)nativeArgument0(pass?1:0);
            MesScript.Record c;if(bound){knownNativeRead(next,3);c=new MesScript.Record(next,next+3,script.rawOpcode(next),new int[]{(byte)script.rawOpcode(next+1),script.rawOpcode(next+2)},new byte[0]);advanceNativeCursor(3);}else{c=script.fetchRecord(next);if(c.opcode!=p.companion||c.args.length!=2)throw new IOException("Missing conditional companion");}
            next=c.end;if(!pass){next=script.scanBlock(next,(byte)c.args[0]);nativeControl=bound;}break;
        }
        case "block.marker":if(args[0]==0){next=script.scanBlock(next,(byte)args[1]);nativeControl=bound;}break;
        case "block.marker_unsigned":if(args[0]==0)next=script.scanBlock(next,args[1]);break;
        case "jump.named":if(host.allowNamedJump()){Integer target=script.label(r.bytes);if(target!=null){next=target;nativeControl=bound;}}break;
        case "jump.named_required":{
            Integer target=script.requiredLabel(r.bytes);if(target==null)throw new IOException("Required named label missing");next=target;nativeControl=bound;break;
        }
        case "pc.character_pair":case "pc.portrait_pair":{emit(op,new int[]{0},r.bytes);if(next<script.length()&&script.fetchRecord(next).opcode==0x46){MesScript.Record pair=script.fetchRecord(next);emit(op,new int[]{1},pair.bytes);if(!bound)next=pair.end;}break;}
        case "script.entry_label":pendingLabel=r.bytes.clone();break;
        case "pc.system.call":{
            if(!(host instanceof ScriptSystemHost)||instruction.stringPayload==null)throw new IOException("Native PC1 system call requires its typed context and raw string owner");
            if(pendingLabel.length!=0)throw new IOException("PC1 system call with pending entry label is not yet verified");
            byte[] raw=instruction.stringPayload.bytes;
            if(bound)advanceNativeCursor(instruction.stringPayload.end-r.end);
            MesScript target=loadScript(Binary.safePath(new String(raw,Binary.SJIS)));
            if(!target.profile.id.equals(p.id))throw new IOException("Cross-profile system call rejected");
            ((ScriptSystemHost)host).enterSystemContext(script,next,args[0],raw);
            script=activateScript(target);next=target.firstOffset;remainingMs=0;nativeControl=bound;break;
        }
        case "pc.system.return":{
            if(!returnSystemContext())throw new IOException("Native PC1 return has no suspended typed caller");next=pc;nativeControl=bound;
            break;
        }
        case "pc.script.transfer":case "dm.script.transfer":case "dm.script.transfer_alt":{
            // D.C.II encodes the mode byte only when the profile has a string8 class; 2C..48 carry none.
            int mode=op.equals("pc.script.transfer")?(args.length==0?1:args[0]==4?2:args[0]==3||args[0]==5?3:1):op.equals("dm.script.transfer_alt")?2:1;
            if(mode==1&&next<script.length()){MesScript.Record following=script.fetchRecord(next);if(following.opcode==p.companion&&following.args[0]==1)mode=3;}
            String name=Binary.safePath(new String(r.bytes,Binary.SJIS));if(bound&&r.opcode==0x36)((NativePcHost)host).beforeNativeScriptLoad(r.opcode,name);MesScript target=loadScript(name);
            if(!target.profile.id.equals(p.id))throw new IOException("Cross-profile transfer rejected");
            Integer priorDestination=null;if(!bound){priorDestination=pendingLabel.length==0?Integer.valueOf(target.firstOffset):target.requiredLabel(pendingLabel);if(priorDestination==null)throw new IOException("Required entry label missing");}
            if(!host.beforeTransfer(mode,name))throw new IOException("Unverified DM transition effects");
            script=activateScript(target);Integer dest=bound?(pendingLabel.length==0?Integer.valueOf(script.firstOffset):script.requiredLabel(pendingLabel)):priorDestination;if(dest==null)throw new IOException("Required entry label missing");next=dest;pendingLabel=new byte[0];nativeControl=bound;break;
        }
        case "script.transfer":case "script.transfer_alt":{
            int mode=op.equals("script.transfer")?0:1;String name=new String(r.bytes,Binary.SJIS);Binary.safePath(name);
            MesScript target=loadScript(name);if(!target.profile.id.equals(p.id))throw new IOException("Cross-profile transfer rejected");
            if(!host.beforeTransfer(mode,name))throw new IOException("Unverified script transition effects");
            if(host instanceof TitleHost&&((TitleHost)host).returnToTitle(mode,name,pendingLabel)){pendingLabel=new byte[0];status=Status.HALTED;return;}
            script=target;next=script.firstOffset;Integer label=script.label(pendingLabel);if(label!=null)next=label;pendingLabel=new byte[0];break;
        }
        case "dm.save.checkpoint":{
            MesScript.Record payload=script.fetchRecord(next);if(payload.opcode<p.stringStart||payload.opcode>=p.wordsStart)throw new IOException("Invalid DM checkpoint label");
            emit(op,args,payload.bytes);next=payload.end;break;
        }
        case "dd.grid.text":{if(args[3]==99)emit(op,args,new byte[0]);else{MesScript.Record payload=script.fetchRecord(next);if(payload.opcode<p.stringStart||payload.opcode>=p.wordsStart)throw new IOException("List caption missing");emit(op,args,payload.bytes);next=payload.end;}break;}
        case "ui.text":if(args[2]==0){emit(op,args,new byte[0]);break;}
        case "pc.button_rect_payload":case "pc.grid.caption_payload":case "pc.grid.caption":case "pc.save_context":case "pc.resource_payload":case "dm.system.resource_handle":case "dm.ui.text":case "ui.button_text":case "host.command_with_string":case "scene.checkpoint":{
            if(ownedProducer){emit(op,args,new byte[0]);break;}
            MesScript.Record payload=instruction.stringPayload!=null?instruction.stringPayload:script.fetchRecord(next);if(instruction.stringPayload==null&&(payload.opcode<p.stringStart||payload.opcode>=p.wordsStart))throw new IOException("Invalid UI string payload");
            int consumed=payload.end-r.end;boolean preResource=bound&&r.opcode==0x25,skipMarker=bound&&(r.opcode==0x4f||r.opcode==0x51);
            if(bound){knownNativeRead(payload.offset,payload.end-payload.offset);if(preResource)advanceNativeCursor(consumed);else if(skipMarker)advanceNativeCursor(1);}
            emit(op,args,instruction.stringPayload==null&&(op.equals("ui.text")||op.equals("dm.ui.text"))?script.nativeText(payload):payload.bytes);if(bound&&!preResource)advanceNativeCursor(consumed-(skipMarker?1:0));next=payload.end;break;
        }
        case "ui.confirm":case "dm.ui.confirm":{
            emit(op,args,r.bytes);if(host instanceof MenuHost){byte[] action=((MenuHost)host).automaticMenuAction();if(action!=null){Integer target=script.label(action);if(target==null)throw new IOException("Automatic UI label missing");next=target;nativeControl=bound;}}break;
        }
        case "text.speaker":case "text.body":emit(op,args,script.nativeText(r));break;
        case "choice.item":{
            if(!(host instanceof ChoiceHost))throw new IOException("Choice-aware host required");
            MesScript.Record caption=script.fetchRecord(next);
            if(!p.operation(caption.opcode).equals("choice.caption"))throw new IOException("Choice caption does not match "+p.id);
            if(bound){knownNativeRead(caption.offset,caption.end-caption.offset);nativeArgument0(caption.bytes.length*14);}((ChoiceHost)host).addChoice(r.bytes,script.nativeText(caption));if(bound)advanceNativeCursor(caption.end-r.end);next=caption.end;break;
        }
        case "choice.show":emit(op,args,new byte[0]);if(args[0]==0)status=Status.WAIT_CHOICE;break;
        case "wait.audio":{
            int mask=args[0];while(next<script.length()&&script.profile.operation(script.fetchRecord(next).opcode).equals(op)){MesScript.Record term=script.fetchRecord(next);mask|=(byte)term.args[0];next=term.end;}
            emit(op,new int[]{mask},new byte[0]);break;
        }
        case "pp.audio.music":{
            int duck=0;
            if(next<script.length()&&script.rawOpcode(next)==6){
                MesScript.Record companion=script.fetchRecord(next);duck=companion.args[0];next=companion.end;
            }
            emit(op,new int[]{args[0],args[1],duck},new byte[0]);break;
        }
        case "wait.message":
            if(!(host instanceof FlowHost))throw new IOException("Native wait requires a flow-aware host");
            emit(op,args,new byte[0]);break;
        case "demo.wait_click":requireDemo();status=Status.WAIT_CLICK;break;
        case "demo.wait_choice":requireDemo();emit(op,args,new byte[0]);status=Status.WAIT_CHOICE;break;
        case "demo.delay":requireDemo();if(args[0]<0)throw new IOException("Negative delay");remainingMs=args[0];status=remainingMs==0?Status.RUNNING:Status.WAIT_TIME;break;
        case "demo.text":case "demo.scene":case "demo.audio":requireDemo();emit(op,args,r.bytes);break;
        default:emit(op,args,r.bytes);break;
        }
        if(bound){if(nativeExtension)advanceNativeCursor(8);if(nativeControl||script!=executingScript)commitCursor(next);else pc=readNativeCursor(script);}
        else{if(status!=Status.HALTED&&!script.executableOffset(next))throw new IOException("Next PC outside code");pc=next;}
        if(script!=executingScript&&!op.equals("pc.system.return")&&host instanceof ContextHost)((ContextHost)host).restored(script,pc);
        if(host instanceof FlowHost&&((FlowHost)host).waiting())status=Status.WAIT_HOST;
        if(host instanceof ContextHost)((ContextHost)host).dispatched(instruction);
    }
    private void requireDemo()throws IOException{if(!script.profile.demo)throw new IOException("Demo operation in native profile");}
    /** Portable engine save format, deliberately not the original game's save format. */
    public byte[] snapshot()throws IOException{
        if(status==Status.FAULT)throw new IOException("Cannot save faulted VM");
        if(nativePc(script))pc=readNativeCursor(script);
        ByteArrayOutputStream raw=new ByteArrayOutputStream();DataOutputStream out=new DataOutputStream(raw);
        out.writeInt(0x43565332);out.writeUTF(script.profile.id);out.writeUTF(script.name);out.writeUTF(script.sha256);
        out.writeUTF(script.profile.executionIdentity());
        out.writeInt(pc);out.writeUTF(status.name());out.writeLong(remainingMs);out.writeInt(pendingLabel.length);out.write(pendingLabel);
        variables.save(out);host.save(out);out.flush();byte[] body=raw.toByteArray();out.writeUTF(Binary.sha256(body));out.flush();return raw.toByteArray();
    }
    /** Restore only into a NEW VM/host pair; publish that pair after this succeeds. */
    public void restore(byte[] saved)throws IOException{
        if(saved.length>32*1024*1024 || saved.length<66)throw new IOException("Invalid save size");
        int bodyLength=saved.length-66;DataInputStream digest=new DataInputStream(new ByteArrayInputStream(saved,bodyLength,66));
        if(!digest.readUTF().equals(Binary.sha256(Arrays.copyOf(saved,bodyLength))))throw new IOException("Save checksum mismatch");
        DataInputStream in=new DataInputStream(new ByteArrayInputStream(saved,0,bodyLength));
        int format=in.readInt();if(format!=0x43565331&&format!=0x43565332)throw new IOException("Unsupported save format");
        if(format==0x43565331&&!script.profile.demo)throw new IOException(
            "Legacy story save has no native execution contract; preserve the original file and import system progress separately");
        String profile=in.readUTF(),name=in.readUTF(),hash=in.readUTF();if(!profile.equals(script.profile.id))throw new IOException("Save profile mismatch");
        if(format==0x43565332&&!in.readUTF().equals(script.profile.executionIdentity()))throw new IOException("Save execution contract differs from this engine; preserve the original save");
        MesScript target=loadScript(Binary.safePath(name));if(!target.profile.id.equals(profile))throw new IOException("Restored library returned a different profile");if(!target.sha256.equals(hash))throw new IOException("Script changed since save");
        int pos=in.readInt();if(!(nativePc(target)?knownNativePc(target,pos):target.executableOffset(pos)))throw new IOException("Invalid saved PC");
        Status state;try{state=Status.valueOf(in.readUTF());}catch(IllegalArgumentException e){throw new IOException("Invalid saved status",e);}
        if(state==Status.FAULT)throw new IOException("Fault status cannot be restored");long time=in.readLong();if(time<0||time>32767)throw new IOException("Invalid saved delay");
        int length=in.readInt();if(length<0||length>65536)throw new IOException("Invalid pending label length");byte[] pending=new byte[length];in.readFully(pending);
        variables.load(in);host.load(in);if(in.available()!=0)throw new IOException("Trailing save payload");target=bindExistingCode(target);
        if(nativePc(target)&&readNativeCursor(target)!=pos)throw new IOException("Saved VM PC differs from canonical native frame PC");
        boolean hostWaiting=host instanceof FlowHost&&((FlowHost)host).waiting();
        if((state==Status.WAIT_HOST)!=hostWaiting)throw new IOException("Saved VM/host wait mismatch");
        script=target;pc=pos;status=state;remainingMs=time;pendingLabel=pending;error="";
        if(host instanceof ContextHost)((ContextHost)host).restored(script,pc);
    }
}
