package org.circus.core;

import java.io.*;

/** A native system script temporarily borrows the story VM; its return restores that caller. */
public final class Dc2Session {
    public MesVm vm;public Dc2Host host;
    private final CircusResources data;private final TextCodec codec;
    public interface HostInitializer {void initialize(Dc2Host host)throws IOException;}
    private final HostInitializer initializer;
    private byte[] caller;private String callerScript;
    private static final int PC1_SESSION=0x50314353;
    private Pc1PortableCandidate pc1Candidate;
    private static final int DC2_SESSION=0x44324353;private Dc2BorrowedLeaf dc2Borrowed;
    public interface Dc2BorrowedCapture {byte[] capture()throws IOException;}
    public Dc2BorrowedCapture dc2BorrowedCapture;
    /** Platform metadata is sampled synchronously at the actual producer. It
     * is opaque to core and remains paired with that independent continuation. */
    public interface Pc1CandidateCapture {byte[] capture()throws IOException;}
    public Pc1CandidateCapture pc1CandidateCapture;
    public Dc2Session(CircusResources data,TextCodec codec)throws IOException{this(data,codec,null);}
    /** Platform-owned settings are installed before the initial native loader
     *captures its read byte. The same initializer applies to replacement hosts. */
    public Dc2Session(CircusResources data,TextCodec codec,HostInitializer initializer)throws IOException{this.data=data;this.codec=codec;this.initializer=initializer;host=Dc2Host.create(data,codec);if(initializer!=null)initializer.initialize(host);vm=new MesVm(data.scripts(),host,"START");bindPortableCandidate();}
    private PcHost pc1(){return host instanceof PcHost&&((PcHost)host).pc1!=null?(PcHost)host:null;}
    private PcHost dc2(){return host instanceof PcHost&&host.resources.profileId.equals("dc2-3363")?(PcHost)host:null;}
    private void bindPortableCandidate(){PcHost h=pc1();if(h!=null){h.pc1.portableCapture(this::capturePc1Candidate);vm.boundaryListener(h.pc1::capturePortableBoundary);}else if(dc2()!=null)vm.boundaryListener(this::captureDc2Boundary);}
    private void captureDc2Boundary()throws IOException{
        PcHost h=dc2();if(h==null||h.dc2Borrowed.pending()==0)return;
        int kind=h.dc2Borrowed.pending();byte[] leaf=vm.snapshot(),metadata=dc2BorrowedCapture==null?new byte[0]:dc2BorrowedCapture.capture();
        if(kind==2){Pair normal=decodeLeaf(leaf);normal.host.completeSave();normal.vm.elapse(0);leaf=normal.vm.snapshot();}
        Dc2BorrowedLeaf next=new Dc2BorrowedLeaf(leaf,metadata,h.dc2Borrowed);Pair check=decodeLeaf(leaf);next.validate(check.vm,(PcHost)check.host);dc2Borrowed=next;h.dc2Borrowed.captured();
    }
    private static byte[] dc2Envelope(byte[] active,Dc2BorrowedLeaf borrowed,byte[] caller,String callerName)throws IOException{
        if(active==null||active.length<66||active.length>Pc1PortableCandidate.MAX_LEAF)throw new IOException("DC2 active session payload length");
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();DataOutputStream out=new DataOutputStream(bytes);out.writeInt(DC2_SESSION);out.writeInt(1);Pc1PortableCandidate.bytes(out,active);out.writeBoolean(borrowed!=null);if(borrowed!=null)borrowed.save(out);out.writeBoolean(caller!=null);if(caller!=null){Pc1PortableCandidate.bytes(out,caller);out.writeUTF(callerName);}out.flush();out.writeUTF(Binary.sha256(bytes.toByteArray()));out.flush();return bytes.toByteArray();
    }
    /**41BD47 native q action unconditionally captures current frame and saves0.
     * It retains current voice, unlike external save/option menu entry. */
    public void requestDc2QuickSave()throws IOException{
        PcHost h=dc2();if(h==null||inSystem()||vm.status()!=MesVm.Status.WAIT_HOST||!h.textWaiting&&!h.ui.waiting||h.saveRequest>=0)throw new IOException("DC2 quick save requires a stable story wait");
        h.dc2Borrowed.produce(h.script,-1,vm.offset(),4);try{captureDc2Boundary();}catch(IOException failed){h.dc2Borrowed.captured();throw failed;}h.saveRequest=0;h.nextTicket();
    }
    public void requireDc2SaveCaller()throws IOException{PcHost h=dc2();if(h==null||h.saveRequest<0||dc2Borrowed==null||!dc2Borrowed.matches(h))throw new IOException("DC2 independent borrowed save caller is unavailable");}
    public byte[] dc2BorrowedSnapshot()throws IOException{requireDc2SaveCaller();return dc2Envelope(dc2Borrowed.leaf,dc2Borrowed,null,null);}
    /**404E50 computes live weekday, copies9:2/20, sets date/time and the borrowed
     * scenario catalog digits. It does not refresh the borrowed continuation. */
    public byte[] dc2BorrowedSnapshot(Dc2BorrowedState.SaveMetadata local)throws IOException{
        requireDc2SaveCaller();if(local==null)throw new IOException("DC2 local save time missing");PcHost live=dc2();Dc2SystemCommands.command(live,new int[]{1,1});
        int weekday=vm.variables.read(9,2),icon=vm.variables.read(9,20);Pair pair=decodeLeaf(dc2Borrowed.leaf);
        int[] at={2,20,21,22,23,24,25},value={weekday,icon,local.month,local.day,local.hour,local.minute,(local.year+0x30)&255};
        for(int n=0;n<at.length;n++)pair.vm.variables.write(9,at[n],value[n]);
        int id=live.catalogIndex("scenario_scripts",dc2Borrowed.provenance.script);
        if(id>=0){pair.vm.variables.write(9,26,(id/10)&255);pair.vm.variables.write(9,27,(id%10)&255);}
        Dc2BorrowedLeaf patched=new Dc2BorrowedLeaf(pair.vm.snapshot(),dc2Borrowed.metadata,((PcHost)pair.host).dc2Borrowed);patched.validate(pair.vm,(PcHost)pair.host);return dc2Envelope(patched.leaf,patched,null,null);
    }
    public byte[] dc2BorrowedMetadata()throws IOException{requireDc2SaveCaller();return dc2Borrowed.metadata.clone();}
    public String dc2BorrowedScript()throws IOException{requireDc2SaveCaller();return dc2Borrowed.provenance.script;}

    private static final class Pair {final Dc2Host host;final MesVm vm;Pair(Dc2Host h,MesVm m){host=h;vm=m;}}
    private Pair decodeLeaf(byte[] bytes)throws IOException{Dc2Host h=Dc2Host.create(data,codec);if(initializer!=null)initializer.initialize(h);MesVm m=new MesVm(data.scripts(),h,"START");m.restore(bytes);return new Pair(h,m);}
    private void capturePc1Candidate(int kind)throws IOException{
        PcHost current=pc1();if(current==null||current.pc1.inSystem())throw new IOException("PC1 portable candidate cannot capture a system script");
        byte[] leaf=vm.snapshot(),metadata=pc1CandidateCapture==null?new byte[0]:pc1CandidateCapture.capture();
        if(kind==2){Pair normalized=decodeLeaf(leaf);normalized.host.completeSave();normalized.vm.elapse(0);leaf=normalized.vm.snapshot();}
        Pc1PortableCandidate next=new Pc1PortableCandidate(leaf,metadata,current.pc1.saves().candidate(),kind,vm.offset());
        // Validate the leaf before publishing it, including source identity and
        // native candidate provenance; platform callback failure preserves the old leaf.
        Pair checked=decodeLeaf(next.leaf);next.validate(checked.vm,(PcHost)checked.host);pc1Candidate=next;
    }
    private static byte[] envelope(byte[] active,Pc1PortableCandidate candidate)throws IOException{
        if(active==null||active.length<66||active.length>Pc1PortableCandidate.MAX_LEAF)throw new IOException("PC1 active session payload length");
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();DataOutputStream out=new DataOutputStream(bytes);out.writeInt(PC1_SESSION);out.writeInt(1);Pc1PortableCandidate.bytes(out,active);out.writeBoolean(candidate!=null);if(candidate!=null)candidate.save(out);out.flush();out.writeUTF(Binary.sha256(bytes.toByteArray()));out.flush();return bytes.toByteArray();
    }
    /** Session sidecar is outside CVS2 so each candidate contains one leaf,
     * independent of how many earlier checkpoints have been captured. */
    public byte[] snapshot()throws IOException{if(dc2()!=null&&dc2Borrowed!=null&&!dc2Borrowed.matches(dc2()))throw new IOException("DC2 active borrowed producer has no matching portable continuation");return pc1()!=null?envelope(vm.snapshot(),pc1Candidate):dc2()!=null?dc2Envelope(vm.snapshot(),dc2Borrowed,caller,callerScript):storySnapshot();}
    public void requirePc1SaveCandidate()throws IOException{
        PcHost h=pc1();if(h==null||h.saveRequest<0||h.pc1.requestedSave()==null||h.pc1.requestedSlot()!=h.saveRequest)throw new IOException("PC1 slot write requires a real pending persist4 selection");
        if(pc1Candidate==null||!pc1Candidate.matches(h.pc1.saves().candidate())||!pc1Candidate.matches(h.pc1.requestedSave()))throw new IOException("PC1 independent portable candidate is unavailable or differs from the selected native frame");
    }
    public byte[] pc1CandidateSnapshot()throws IOException{requirePc1SaveCandidate();return envelope(pc1Candidate.leaf,pc1Candidate);}
    /**404E70's pre-file-open metadata update is not a checkpoint producer.
     * Patch only its seven ordinary bytes in a detached continuation; all other
     * ordinary/scene/audio state remains the independent producer's state. */
    public byte[] pc1CandidateSnapshot(Pc1Frame.SaveMetadata local)throws IOException{
        requirePc1SaveCandidate();PcHost live=pc1();int value2=vm.variables.read(9,2),value20=vm.variables.read(9,20);
        Pc1SaveContext.Selection selected=live.pc1.saves().selectForPersist4(0,live.saveRequest,null);live.pc1.saves().prepareWrite(selected,value2,value20,local);
        Pair pair=decodeLeaf(pc1Candidate.leaf);PcHost captured=(PcHost)pair.host;
        int[] indices={2,20,21,22,23,24,25},values={value2,value20,local.month,local.day,local.hour,local.minute,(local.year+0x30)&255};
        for(int i=0;i<indices.length;i++)pair.vm.variables.write(9,indices[i],values[i]);
        Pc1SaveContext.Selection source=captured.pc1.saves().selectForPersist4(0,live.saveRequest,null);captured.pc1.saves().prepareWrite(source,value2,value20,local);
        Pc1PortableCandidate patched=new Pc1PortableCandidate(pair.vm.snapshot(),pc1Candidate.metadata,captured.pc1.saves().candidate(),pc1Candidate.kind,pc1Candidate.portablePc);
        patched.validate(pair.vm,captured);return envelope(patched.leaf,patched);
    }
    public byte[] pc1CandidateMetadata()throws IOException{requirePc1SaveCandidate();return pc1Candidate.metadata.clone();}
    public String pc1CandidateScript()throws IOException{requirePc1SaveCandidate();return pc1Candidate.script;}
    private boolean typedSystem(){return host instanceof PcHost&&((PcHost)host).pc1!=null&&((PcHost)host).pc1.inSystem();}
    public boolean inSystem(){return caller!=null||typedSystem();}
    public void openNativeSystem()throws IOException{
        if(inSystem()||!host.textWaiting)throw new IOException("No stable story context for native menu");
        if(host instanceof PcHost&&((PcHost)host).pc1!=null)throw new IOException("PC1 backlog uses the live native history cursor; its redraw lifecycle is not yet mapped");
        caller=vm.snapshot();callerScript=vm.scriptName();host.enterSystem();
    }
    public void openSystem(String script)throws IOException{
        openSystem(script,0);
    }
    public void openSystem(String script,int eventKind)throws IOException{
        if(inSystem()||(!host.textWaiting&&!host.ui.waiting))throw new IOException("No stable story context for system menu");
        if(host instanceof PcHost&&((PcHost)host).pc1!=null){vm.enterNativeSystem(script,eventKind);return;}
        if(dc2()!=null&&dc2().dc2Borrowed.flag==0){host.play(5,"",false);dc2().dc2Borrowed.produce(host.script,-1,vm.offset(),3);try{captureDc2Boundary();}catch(IOException failed){dc2().dc2Borrowed.captured();throw failed;}}
        byte[] saved=vm.snapshot();String name=vm.scriptName();host.enterSystem();vm.enterSystem(script);caller=saved;callerScript=name;
    }
    public void returnToStory()throws IOException{
        if(typedSystem()){vm.returnSystemContext();return;}
        if(caller==null)throw new IOException("System return without caller");byte[] saved=dc2()!=null?dc2Envelope(caller,dc2Borrowed,null,null):caller;restore(saved);caller=null;callerScript=null;
    }
    public byte[] storySnapshot()throws IOException{return caller==null?vm.snapshot():caller.clone();}
    public String storyScript(){return caller!=null?callerScript:typedSystem()?((PcHost)host).pc1.system().returnPoint().script:vm.scriptName();}
    public void restore(byte[] saved)throws IOException{
        if(saved==null||saved.length<4)throw new IOException("Missing session save");
        byte[] active=saved,nextCaller=null;String nextCallerScript=null;Pc1PortableCandidate candidate=null;Dc2BorrowedLeaf borrowed=null;
        if(new DataInputStream(new ByteArrayInputStream(saved)).readInt()==PC1_SESSION){
            if(pc1()==null||saved.length<79||saved.length>70*1024*1024)throw new IOException("Invalid PC1 session envelope");int body=saved.length-66;
            DataInputStream tail=new DataInputStream(new ByteArrayInputStream(saved,body,66));if(!tail.readUTF().equals(Binary.sha256(java.util.Arrays.copyOf(saved,body))))throw new IOException("PC1 session checksum mismatch");
            DataInputStream in=new DataInputStream(new ByteArrayInputStream(saved,0,body));in.readInt();if(in.readInt()!=1)throw new IOException("PC1 session version");active=Pc1PortableCandidate.bytes(in,Pc1PortableCandidate.MAX_LEAF);if(in.readBoolean())candidate=Pc1PortableCandidate.load(in);if(in.available()!=0)throw new IOException("Trailing PC1 session payload");
        }
        if(new DataInputStream(new ByteArrayInputStream(saved)).readInt()==DC2_SESSION){
            if(dc2()==null||saved.length<80||saved.length>100*1024*1024)throw new IOException("Invalid DC2 session envelope");int body=saved.length-66;
            DataInputStream tail=new DataInputStream(new ByteArrayInputStream(saved,body,66));if(!tail.readUTF().equals(Binary.sha256(java.util.Arrays.copyOf(saved,body))))throw new IOException("DC2 session checksum mismatch");
            DataInputStream in=new DataInputStream(new ByteArrayInputStream(saved,0,body));in.readInt();if(in.readInt()!=1)throw new IOException("DC2 session version");active=Pc1PortableCandidate.bytes(in,Pc1PortableCandidate.MAX_LEAF);if(in.readBoolean())borrowed=Dc2BorrowedLeaf.load(in);if(in.readBoolean()){nextCaller=Pc1PortableCandidate.bytes(in,Pc1PortableCandidate.MAX_LEAF);nextCallerScript=in.readUTF();Binary.safePath(nextCallerScript);}if(in.available()!=0)throw new IOException("Trailing DC2 session payload");
        }
        Pair pair=decodeLeaf(active);Dc2Host h=pair.host;MesVm m=pair.vm;
        if(borrowed!=null){Pair leaf=decodeLeaf(borrowed.leaf);if(!(leaf.host instanceof PcHost))throw new IOException("DC2 borrowed profile mismatch");borrowed.validate(leaf.vm,(PcHost)leaf.host);if(!(h instanceof PcHost)||!borrowed.matches((PcHost)h))throw new IOException("DC2 active/borrowed provenance mismatch");}
        if(nextCaller!=null){Pair context=decodeLeaf(nextCaller);if(!context.vm.scriptName().equals(nextCallerScript)||!(context.host instanceof PcHost)||!context.host.resources.profileId.equals("dc2-3363"))throw new IOException("DC2 external caller identity mismatch");}

        if(candidate!=null){Pair leaf=decodeLeaf(candidate.leaf);if(!(leaf.host instanceof PcHost)||((PcHost)leaf.host).pc1==null)throw new IOException("PC1 candidate profile mismatch");candidate.validate(leaf.vm,(PcHost)leaf.host);if(!(h instanceof PcHost)||((PcHost)h).pc1==null||!candidate.matches(((PcHost)h).pc1.saves().candidate()))throw new IOException("PC1 session candidate provenance mismatch");}
        h.copyPersistentFrom(host);h.captureRenderer=host.captureRenderer;if(h instanceof PcHost&&host instanceof PcHost)((PcHost)h).nativeFrameCapture=((PcHost)host).nativeFrameCapture;host=h;vm=m;pc1Candidate=candidate;dc2Borrowed=borrowed;caller=nextCaller;callerScript=nextCallerScript;bindPortableCandidate();
    }
    /** Import the admitted Windows wait7 text checkpoint into detached portable
     * owners, bootstrap original global templates, then publish atomically.
     * Prior Windows backlog rows and physical media cursors are outside scope. */
    public void importDc2NativeSlot(Dc2NativeSlot slot)throws IOException{
        if(dc2()==null||slot==null)throw new IOException("DC2 native import profile/input required");MesScript code=data.scripts().script(slot.scriptName());Dc2NativeSlot.Plan plan=slot.normalTextPlan(code);if(slot.dword(Dc2NativeSlot.Dword.PARAGRAPH_MODE)!=0)throw new IOException("DC2 native import currently supports ADV paragraphs only; prior NVL text requires native log reconstruction");
        PcHost h=(PcHost)Dc2Host.create(data,codec);if(initializer!=null)initializer.initialize(h);MesVm bootstrap=new MesVm(data.scripts(),h,"START");
        for(int n=0;n<200000;n++){
            if(!h.ui.quadTemplates.get(1).isEmpty()&&!h.ui.quadTemplates.get(2).isEmpty()&&!h.ui.buttonTemplates.get(1).isEmpty()&&!h.scene.slots[21].isEmpty())break;
            MesVm.Status status=bootstrap.run(1);if(status==MesVm.Status.FAULT||status==MesVm.Status.HALTED)throw new IOException("DC2 import START bootstrap: "+bootstrap.error());
            if(status==MesVm.Status.WAIT_HOST){bootstrap.elapse(40);for(int ch=0;ch<6;ch++)h.audioCompleted(ch,h.audioGeneration[ch]);bootstrap.signalHost(h.waitTicket(),MesVm.HostSignal.MEDIA_COMPLETE);if(h.ui.waiting)throw new IOException("DC2 import START reached input before required global templates");bootstrap.signalHost(h.waitTicket(),MesVm.HostSignal.TEXT_COMPLETE);bootstrap.signalHost(h.waitTicket(),MesVm.HostSignal.ADVANCE);}
        }
        if(h.ui.quadTemplates.get(1).isEmpty()||h.ui.quadTemplates.get(2).isEmpty()||h.ui.buttonTemplates.get(1).isEmpty()||h.scene.slots[21].isEmpty())throw new IOException("DC2 import START template budget");
        h.copyPersistentFrom(host);h.enterSystem();h.systemScene=null;MesVm m=new MesVm(data.scripts(),h,plan.script);h.applyDc2NativeSlot(slot,plan);m.beginDc2NativeImport(plan);
        if(plan.checkpointOpcode==0x4b){MesScript.Record previous=null;for(MesScript.Record record:code.records()){if(record.offset==plan.resumeFilePc)break;previous=record;}if(previous!=null&&previous.opcode==0x4a&&previous.end==plan.resumeFilePc){h.position(code,previous.offset);h.command("text.speaker",previous.args,previous.bytes);h.position(code,plan.resumeFilePc);}}
        boolean reached=false;
        for(int n=0;n<4096;n++){
            MesVm.Status status=m.run(1);if(status==MesVm.Status.FAULT||status==MesVm.Status.HALTED)throw new IOException("DC2 imported checkpoint replay: "+m.error());
            if(!m.scriptName().equals(plan.script)||h.saveRequest>=0||h.loadRequest>=0||h.ui.waiting)throw new IOException("DC2 imported checkpoint left ordinary text scope");
            if(h.textWaiting&&!h.body.isEmpty()&&h.pc>=plan.resumeFilePc&&code.fetchRecord(h.pc).opcode==7){reached=true;break;}
            if(status==MesVm.Status.WAIT_HOST){m.elapse(40);m.signalHost(h.waitTicket(),MesVm.HostSignal.TEXT_COMPLETE);}
        }
        if(!reached)throw new IOException("DC2 imported checkpoint did not reach its following07");
        h.captureRenderer=host.captureRenderer;h.nativeFrameCapture=dc2().nativeFrameCapture;host=h;vm=m;caller=null;callerScript=null;pc1Candidate=null;dc2Borrowed=null;bindPortableCandidate();
    }
    /** Returning to MAIN abandons the suspended story. */
    public void captureHistory()throws IOException{
        if(host instanceof PcHost&&((PcHost)host).pc1!=null)return;
        if(inSystem()||(!host.textWaiting&&!host.presentationWaiting)||host.history.entries.isEmpty())return;
        Dc2History.Entry e=host.history.entries.get(host.history.entries.size()-1);if(e.resume.length!=0)return;
        host.history.omitFromSnapshot=true;try{e.resume=vm.snapshot();}finally{host.history.omitFromSnapshot=false;}
        int total=0;for(int n=host.history.entries.size()-1;n>=0;n--){Dc2History.Entry old=host.history.entries.get(n);total+=old.resume.length;if(total>2*1024*1024)old.resume=new byte[0];}
    }
    public void rewindHistory(int index)throws IOException{
        if(index<0||index>=host.history.entries.size())throw new IOException("History entry missing");
        java.util.List<Dc2History.Entry> prefix=new java.util.ArrayList<>(host.history.entries.subList(0,index+1));byte[] snapshot=prefix.get(index).resume;
        if(snapshot.length==0)throw new IOException("History checkpoint expired");restore(snapshot);host.history.entries.addAll(prefix);host.history.active=false;host.history.selected=index;host.history.cursor=Math.max(0,index-3);
    }
    public void observeTransfer(){if(caller!=null&&vm.scriptName().equalsIgnoreCase("MAIN")){caller=null;callerScript=null;host.systemScene=null;}}
}
