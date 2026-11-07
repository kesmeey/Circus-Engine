package org.circus.core;

import java.io.*;

/** Native raw-PC counterexamples: jump into payload bytes, waits and story snapshots. */
public final class NativeCursorTests {
    private static int count;
    private static void check(boolean value,String message){count++;if(!value)throw new AssertionError(message);}

    private static class Host implements MesVm.Host {
        int soundCount,lastSound=-1;
        public int read(int bank,int index){return 0;}
        public void write(int bank,int index,int value){}
        public boolean command(String operation,int[] args,byte[] bytes){
            if(operation.equals("pc.sound8")){soundCount++;lastSound=args[0];return true;}
            return operation.equals("native.no_effect");
        }
        public boolean allowNamedJump(){return true;}
        public boolean beforeTransfer(int mode,String script){return true;}
        public void save(DataOutput out)throws IOException{out.writeInt(soundCount);out.writeInt(lastSound);}
        public void load(DataInput in)throws IOException{soundCount=in.readInt();lastSound=in.readInt();}
    }
    private static final class FlowHost extends Host implements MesVm.ContextHost {
        int pc,gate=21;boolean waiting,released;
        public void attach(Variables variables){}
        public void position(MesScript script,int offset){pc=offset;}
        public void elapse(long ms){}
        public boolean beforeInstruction(Profile profile,int opcode){if(pc==gate&&!released){waiting=true;return true;}return false;}
        public boolean waiting(){return waiting;}
        public long waitTicket(){return 1;}
        public boolean signal(long ticket,MesVm.HostSignal signal){
            if(ticket!=1||signal!=MesVm.HostSignal.ADVANCE||!waiting)return false;
            waiting=false;released=true;return true;
        }
        public void save(DataOutput out)throws IOException{super.save(out);out.writeInt(pc);out.writeBoolean(waiting);out.writeBoolean(released);}
        public void load(DataInput in)throws IOException{super.load(in);pc=in.readInt();waiting=in.readBoolean();released=in.readBoolean();}
    }
    private static MesScript script(byte[] payload,boolean sum)throws IOException{
        // Native PC1 label lookup scans raw record shapes. It can find x at
        // the first payload byte or after the signed BF default's three bytes.
        ByteArrayOutputStream out=new ByteArrayOutputStream();
        out.write(new byte[]{0,0,0,0,0x63,0x20,0x3b,'x',0});
        out.write(sum?0x5a:0x53);out.write(new byte[8]);out.write(payload);
        if(sum)out.write(new byte[]{2,0,0});
        out.write(new byte[3]);
        return new MesScript("cursor",out.toByteArray(),Profiles.get("dcpc-2063"));
    }
    private static MesVm vm(MesScript script,Host host)throws IOException{return new MesVm(name->script,host,"cursor");}

    public static int run()throws Exception{
        count=0;
        MesScript atStart=script(new byte[]{0x32,'x',0,5,7,0,0,0,0},false);
        check(atStart.at(18).payloadOwner==9&&atStart.at(18).end==27,"The structural audit retains the owner's nine-byte payload");
        check(atStart.requiredLabel(new byte[]{'x'})==18,"Native byte scanner finds the label at the payload start");
        MesScript.Record label=atStart.fetchRecord(18);
        check(label.opcode==0x32&&label.end==21&&label.bytes.length==1&&label.bytes[0]=='x',"Execution decodes the short string at its actual PC");
        check(!atStart.boundary(21)&&atStart.executableOffset(21),"A valid native PC need not belong to the structural audit index");
        MesScript.Record sound=atStart.fetchRecord(21);
        check(sound.opcode==5&&sound.end==24&&sound.args.length==2&&sound.args[0]==7,"The formerly hidden sound instruction is decoded normally");
        check(!atStart.executableOffset(atStart.firstOffset-1)&&!atStart.executableOffset(atStart.length()),"Header bytes and EOF are not executable offsets");
        Host uninterrupted=new Host();MesVm whole=vm(atStart,uninterrupted);
        check(whole.run(10)==MesVm.Status.HALTED&&whole.offset()==24,"STOP preserves its raw PC inside the old payload span");
        check(uninterrupted.soundCount==1&&uninterrupted.lastSound==7,"Jumping into payload data cannot silently skip a native side effect");

        Host firstHost=new Host();MesVm first=vm(atStart,firstHost);
        check(first.run(1)==MesVm.Status.RUNNING&&first.offset()==18,"First step performs the required named jump");
        check(first.run(1)==MesVm.Status.RUNNING&&first.offset()==21,"Second step leaves a non-indexed native sound PC");
        Host restoredHost=new Host();MesVm restored=vm(atStart,restoredHost);restored.restore(first.snapshot());
        check(restored.offset()==21&&restored.status()==MesVm.Status.RUNNING,"Snapshot restore accepts the actual PC inside the payload");
        check(restored.run(1)==MesVm.Status.RUNNING&&restored.offset()==24&&restoredHost.soundCount==1,"Restored cursor executes the sound once and stops before STOP");
        Host stopHost=new Host();MesVm stop=vm(atStart,stopHost);stop.restore(restored.snapshot());
        check(stop.offset()==24&&stop.run(1)==MesVm.Status.HALTED&&stop.offset()==24,"A saved pending STOP stays at PC24");
        Host haltedHost=new Host();MesVm halted=vm(atStart,haltedHost);halted.restore(stop.snapshot());
        check(halted.status()==MesVm.Status.HALTED&&halted.offset()==24&&haltedHost.soundCount==1,"A halted snapshot at a non-indexed STOP is preserved without replaying audio");

        for(boolean sum:new boolean[]{false,true}){
            MesScript interior=script(new byte[]{(byte)0xbf,1,2,0x32,'x',0,5,7,0},sum);
            check(interior.requiredLabel(new byte[]{'x'})==21&&!interior.boundary(21),"Native label scanner may locate a label inside a raw word payload");
            Host host=new Host();MesVm execution=vm(interior,host);
            check(execution.run(10)==MesVm.Status.HALTED&&host.soundCount==1&&host.lastSound==7,
                  "Interior labels work in "+(sum?"sum terms":"handler payloads")+" without dispatching their owners");
            check(execution.offset()==(sum?30:27),"Raw cursor resumes the following companion/STOP at the native byte location");
        }
        FlowHost flow=new FlowHost();MesVm waiting=vm(atStart,flow);
        check(waiting.run(10)==MesVm.Status.WAIT_HOST&&waiting.offset()==21&&flow.soundCount==0,"Flow wait happens before executing the dynamic instruction");
        FlowHost waitingHost=new FlowHost();MesVm waitingRestore=vm(atStart,waitingHost);waitingRestore.restore(waiting.snapshot());
        check(waitingRestore.status()==MesVm.Status.WAIT_HOST&&waitingRestore.offset()==21,"Waiting snapshots preserve dynamic raw PCs and host wait state");
        check(waitingRestore.signalHost(1,MesVm.HostSignal.ADVANCE),"The restored wait accepts exactly its host signal");
        check(waitingRestore.run(10)==MesVm.Status.HALTED&&waitingRestore.offset()==24&&waitingHost.soundCount==1,"Resuming the wait neither drops nor duplicates the sound");
        // The structural scan sees a complete opaque payload. Entering its
        // interior later reveals a word instruction truncated by EOF. Input
        // waiting must be delivered before its operands are touched.
        MesScript truncatedCursor=script(new byte[]{0x32,'x',0,1,0,0,0x53,0,0},false);
        FlowHost deferred=new FlowHost();deferred.gate=24;MesVm deferredVm=vm(truncatedCursor,deferred);
        check(deferredVm.run(10)==MesVm.Status.WAIT_HOST&&deferredVm.offset()==24,
              "A dynamic operand error is not raised before the pre-instruction flow wait");
        FlowHost deferredRestoreHost=new FlowHost();deferredRestoreHost.gate=24;
        MesVm deferredRestore=vm(truncatedCursor,deferredRestoreHost);deferredRestore.restore(deferredVm.snapshot());
        check(deferredRestore.status()==MesVm.Status.WAIT_HOST&&deferredRestore.offset()==24,
              "Restoring a valid pre-decode wait must not inspect its future truncated operands");
        check(deferredRestore.signalHost(1,MesVm.HostSignal.ADVANCE)&&deferredRestore.run(1)==MesVm.Status.FAULT,
              "A restored pre-decode wait reports the future bounds error only after resuming");
        check(deferredVm.signalHost(1,MesVm.HostSignal.ADVANCE)&&deferredVm.run(1)==MesVm.Status.FAULT,
              "The truncated dynamic instruction reports its bounds error only after resuming");
        return count;
    }
    public static void main(String[] args)throws Exception{System.out.println("PASS NativeCursorTests assertions="+run());}
}
