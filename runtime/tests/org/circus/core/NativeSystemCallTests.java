package org.circus.core;

import java.io.*;
import java.util.*;

/** VM control/owner boundaries: same live memory, consumed return cursor and frame queues. */
public final class NativeSystemCallTests {
    private static int checks;
    private static void check(boolean value,String why){checks++;if(!value)throw new AssertionError(why);}
    private static byte[] set(int index,int value){return new byte[]{0x58,0,0,(byte)index,0,(byte)value,(byte)(value>>>8),0,0};}
    private static byte[] call(String name)throws IOException{ByteArrayOutputStream out=new ByteArrayOutputStream();out.write(new byte[]{0x26,0,99,0});out.write(name.getBytes(Binary.SJIS));out.write(0);return out.toByteArray();}
    private static MesScript script(String name,byte[]...records)throws IOException{ByteArrayOutputStream out=new ByteArrayOutputStream();out.write(new byte[]{0,0,0,0,0x63,0x20});for(byte[] record:records)out.write(record);out.write(new byte[3]);return new MesScript(name,out.toByteArray(),Profiles.get("dcpc-2063"));}
    private static class BaseHost implements MesVm.Host {
        int legacyReturns;
        public int read(int bank,int index){return 0;}
        public void write(int bank,int index,int value){}
        public boolean command(String op,int[] args,byte[] bytes){if(op.equals("dm.system.return")){legacyReturns++;return true;}return false;}
        public boolean allowNamedJump(){return true;}
        public boolean beforeTransfer(int mode,String target){return true;}
        public void save(DataOutput out)throws IOException{out.writeInt(legacyReturns);}
        public void load(DataInput in)throws IOException{legacyReturns=in.readInt();}
    }
    private static final class CallHost extends BaseHost implements MesVm.ScriptSystemHost {
        MesVm.SystemReturn caller;int enters,returns;
        public void enterSystemContext(MesScript script,int pc,int mode,byte[] target)throws IOException{
            if(caller!=null)throw new IOException("Nested PC1 system context is not mapped");
            if(mode!=0)throw new IOException("PC1 nonzero system mode is not mapped");
            caller=new MesVm.SystemReturn(script.name,script.sha256,pc);enters++;
        }
        public MesVm.SystemReturn systemReturnPoint(){return caller;}
        public void leaveSystemContext(){caller=null;returns++;}
        public void save(DataOutput out)throws IOException{super.save(out);out.writeInt(enters);out.writeInt(returns);out.writeBoolean(caller!=null);if(caller!=null){out.writeUTF(caller.script);out.writeUTF(caller.sha256);out.writeInt(caller.offset);}}
        public void load(DataInput in)throws IOException{super.load(in);enters=in.readInt();returns=in.readInt();caller=in.readBoolean()?new MesVm.SystemReturn(in.readUTF(),in.readUTF(),in.readInt()):null;}
    }
    private static MesVm vm(Map<String,MesScript> scripts,MesVm.Host host)throws IOException{return new MesVm(name->{MesScript result=scripts.get(name);if(result==null)throw new FileNotFoundException(name);return result;},host,"main");}
    private static final class FrameHost extends BaseHost implements MesVm.ContextHost,MesVm.MenuHost {
        boolean waiting=true;byte[] pending;
        public void attach(Variables variables){}
        public void position(MesScript script,int pc){}
        public void elapse(long ms){}
        public boolean beforeInstruction(Profile p,int opcode){return waiting;}
        public boolean waiting(){return waiting;}
        public long waitTicket(){return 1;}
        public boolean signal(long ticket,MesVm.HostSignal signal){return false;}
        public byte[] menuAction(long ticket,int button){return null;}
        public void acceptMenuAction(int button){}
        public byte[] pollMenuAction(){byte[] action=pending;pending=null;return action;}
    }
    public static int run()throws Exception {
        checks=0;Map<String,MesScript> scripts=new HashMap<>();
        scripts.put("main",script("main",set(0,11),call("menu"),set(1,42)));
        scripts.put("menu",script("menu",set(0,77),new byte[]{0x1c,0,0}));
        CallHost host=new CallHost();MesVm running=vm(scripts,host);running.run(1);running.variables.appendHistory();
        check(running.variables.historyCount()==1,"initial caller history holds its11 value");
        check(running.run(1)==MesVm.Status.RUNNING&&running.scriptName().equals("menu")&&host.caller.offset==24,"26 consumes STOP-marked raw companion before saving the caller cursor");
        check(host.enters==1&&running.variables.read(0,0)==11,"system entry does not replace the live ordinary-memory owner");
        running.run(1);byte[] saved=running.snapshot();CallHost restoredHost=new CallHost();MesVm restored=vm(scripts,restoredHost);restored.restore(saved);
        check(Arrays.equals(saved,restored.snapshot())&&restored.variables.read(0,0)==77,"snapshot carries active context and menu-written ordinary values");
        check(restored.run(1)==MesVm.Status.RUNNING&&restored.scriptName().equals("main")&&restored.offset()==24&&restoredHost.returns==1,"1C resumes exactly after the consumed companion");
        check(restored.variables.read(0,0)==77&&restored.variables.historyCount()==1,"return retains live bytes and the original shadow/log instead of restoring a whole VM");
        restored.variables.appendHistory();check(restored.variables.historyCount()==2,"menu delta remains observable against the caller's old history shadow");
        check(restored.run(10)==MesVm.Status.HALTED&&restored.variables.read(0,1)==42,"caller continues after26 without replaying its initial instructions");
        for(boolean badHash:new boolean[]{false,true}){
            CallHost h=new CallHost();MesVm invalid=vm(scripts,h);invalid.run(3);MesVm.SystemReturn before=h.caller;
            h.caller=new MesVm.SystemReturn(before.script,badHash?String.join("",Collections.nCopies(64,"0")):before.sha256,badHash?before.offset:999999);
            check(invalid.run(1)==MesVm.Status.FAULT&&h.returns==0&&h.caller!=null&&invalid.variables.read(0,0)==77,"caller code/PC validation happens before any context owner is restored");
        }
        Map<String,MesScript> foreign=new HashMap<>(scripts);foreign.put("menu",new MesScript("menu",new byte[]{0,0,0,0,0x69,0x57,0,0,0},Profiles.get("dc2pc-5769")));
        CallHost unchanged=new CallHost();check(vm(foreign,unchanged).run(10)==MesVm.Status.FAULT&&unchanged.enters==0,"cross-profile target is rejected before suspending the caller");
        check(vm(scripts,new BaseHost()).run(10)==MesVm.Status.FAULT,"a host without a typed owner cannot silently accept26");
        Map<String,MesScript> returns=new HashMap<>();returns.put("main",script("main",new byte[]{0x1c,0,0}));CallHost legacy=new CallHost();
        check(vm(returns,legacy).run(10)==MesVm.Status.FAULT&&legacy.legacyReturns==0,"An orphan PC1 return fails before mutating a legacy DM return owner");
        Map<String,MesScript> flow=new HashMap<>();flow.put("main",script("main",set(0,11),new byte[]{0x32,'x',0},set(0,42)));
        FrameHost queuedHost=new FrameHost();MesVm queued=vm(flow,queuedHost);check(queued.run(1)==MesVm.Status.WAIT_HOST,"frame test waits before the first instruction");
        queuedHost.waiting=false;queuedHost.pending=new byte[]{'x'};queued.elapse(0);
        check(queued.run(1)==MesVm.Status.RUNNING&&queued.variables.read(0,0)==0,"a queued native frame action is consumed before an instruction even after wait was released");
        check(queued.run(10)==MesVm.Status.HALTED&&queued.variables.read(0,0)==42,"queued jump skips the old linear instruction exactly once");
        FrameHost releasedHost=new FrameHost();MesVm released=vm(flow,releasedHost);released.run(1);releasedHost.waiting=false;
        check(released.run(1)==MesVm.Status.RUNNING&&released.variables.read(0,0)==11,"a native missing-label frame that only releases wait resumes linearly without dummy elapse");
        return checks;
    }
    public static void main(String[] args)throws Exception{System.out.println("PASS "+run()+" native system/frame owner assertions");}
}
