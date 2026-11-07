package org.circus.core;

import java.io.*;
import java.util.*;

/** Current3B/3C setters, 41B5F0 input dispatch and 4137D0 transfer modes. */
public final class Dc2ActionTests {
    private static int count;
    private static void check(boolean value,String why){count++;if(!value)throw new AssertionError(why);}
    private static void string(ByteArrayOutputStream out,int opcode,String value)throws IOException{out.write(opcode);out.write(value.getBytes(Binary.SJIS));out.write(0);}
    private static byte[] code(byte[] body)throws IOException{ByteArrayOutputStream out=new ByteArrayOutputStream();out.write(new byte[]{0,0,0,0,0x63,0x33});out.write(body);return out.toByteArray();}
    private static byte[] waitCode(int wait,int mode,String right,String left,boolean clear)throws IOException {
        ByteArrayOutputStream out=new ByteArrayOutputStream();string(out,0x3b,right);string(out,0x3c,left);if(clear){string(out,0x3b,"");string(out,0x3c,"");}out.write(new byte[]{(byte)wait,2,(byte)mode});string(out,0x31,"right");out.write(new byte[]{0,0,0});string(out,0x31,"left");out.write(new byte[]{0,0,0});return code(out.toByteArray());
    }
    private static final class Fixture {
        final PcHost host;final MesVm vm;final MesScript main,target;
        Fixture(byte[] mainCode,byte[] targetCode)throws IOException{
            final byte[] names=new byte[20];GameResources files=new GameResources(new GameResources.Storage(){
                public Set<String> paths(){return Collections.singleton("AdvData/DAT/NAMES.DAT");}
                public byte[] read(String path,int limit){return names.clone();}
                public Archive.Source source(String path){return new Archive.Source(){public long size(){return names.length;}public byte[] read(long start,int length)throws IOException{Binary.range(start,length,names.length);return Arrays.copyOfRange(names,(int)start,(int)start+length);}};}
            });Profile profile=Profiles.get("dc2-3363");host=Pc1TestDefaults.bind(new PcHost(new CircusResources(files,profile),TextCodec.identity()));main=new MesScript("main",mainCode,profile);target=targetCode==null?main:new MesScript("target",targetCode,profile);vm=new MesVm(name->name.equals("target")?target:main,host,main.name);
        }
        void waitAt(){check(vm.run(100)==MesVm.Status.WAIT_HOST,"raw setters reach their native input wait: "+vm.error());}
    }
    public static int run()throws Exception {
        count=0;
        for(int button:new int[]{-1,-2}){
            Fixture f=new Fixture(waitCode(0x12,0,"right","left",false),null);f.waitAt();String chosen=button==-1?"right":"left",other=button==-1?"left":"right";
            check(!f.vm.activateMenu(f.host.waitTicket()-1,button),"native input rejects an obsolete host ticket");
            check(f.vm.activateMenu(f.host.waitTicket(),button)&&f.vm.offset()==f.main.label(chosen.getBytes(Binary.SJIS)),"timed wait resolves the correct right/Esc or left/Enter binding");
            check(f.host.ui.actions[button==-1?0:1].isEmpty()&&f.host.ui.actions[button==-1?1:0].equals(other),"successful input consumes only the selected global binding");
        }
        for(int wait:new int[]{0x0a,0x0b}){
            Fixture f=new Fixture(waitCode(wait,0,"right","left",false),null);f.waitAt();
            check(!f.vm.activateMenu(f.host.waitTicket(),-2)&&f.host.ui.actions[1].equals("left"),"native UI wait does not confuse global action1 with Enter on the selected region");
            check(f.vm.activateMenu(f.host.waitTicket(),-1)&&f.vm.offset()==f.main.label("right".getBytes(Binary.SJIS)),"native UI wait consumes global action0 for right/Esc");
        }
        Fixture choice=new Fixture(waitCode(9,0,"right","left",false),null);choice.waitAt();check(!choice.vm.activateMenu(choice.host.waitTicket(),-1)&&!choice.vm.activateMenu(choice.host.waitTicket(),-2),"choice wait9 does not use the UI/timed wait global actions");
        Fixture missing=new Fixture(waitCode(0x12,0,"absent","absent",false),null);missing.waitAt();check(!missing.vm.activateMenu(missing.host.waitTicket(),-1)&&missing.vm.status()==MesVm.Status.WAIT_HOST&&missing.host.ui.actions[0].equals("absent"),"missing optional action label leaves the binding and native wait intact");
        check(!missing.vm.signalHost(missing.host.waitTicket(),MesVm.HostSignal.ADVANCE),"timed wait mode bit0 clear does not accept generic click completion");
        Fixture cleared=new Fixture(waitCode(0x12,1,"right","left",true),null);cleared.waitAt();check(cleared.host.ui.actions[0].isEmpty()&&cleared.host.ui.actions[1].isEmpty()&&!cleared.vm.activateMenu(cleared.host.waitTicket(),-1),"raw3B/3C empty strings clear existing bindings rather than taking a no-op shortcut");
        check(cleared.vm.signalHost(cleared.host.waitTicket(),MesVm.HostSignal.ADVANCE)&&!cleared.host.hasTimedInput(),"timed wait bit0 allows ordinary right/left input to complete when no action label is selected");
        Fixture delay=new Fixture(waitCode(0x0a,1,"right","left",false),null);delay.waitAt();check(!delay.vm.signalHost(delay.host.waitTicket(),MesVm.HostSignal.ADVANCE)&&delay.host.remainingMillis()==400,"native0A mode1 ignores input until its clock expires");
        Fixture saved=new Fixture(waitCode(0x12,0,"right","left",false),null);saved.waitAt();saved.vm.elapse(37);byte[] snapshot=saved.vm.snapshot();Fixture loaded=new Fixture(waitCode(0x12,0,"right","left",false),null);loaded.vm.restore(snapshot);
        check(loaded.host.remainingMillis()==363&&!loaded.vm.signalHost(loaded.host.waitTicket(),MesVm.HostSignal.ADVANCE)&&loaded.vm.activateMenu(loaded.host.waitTicket(),-2),"snapshot preserves the wait-specific input gate and unconsumed bindings");
        for(int mode:new int[]{1,2,3}){
            ByteArrayOutputStream out=new ByteArrayOutputStream();string(out,0x3b,"right");string(out,0x3c,"left");string(out,mode==2?0x36:0x35,"target");if(mode==3)out.write(new byte[]{2,1,0});out.write(new byte[]{0,0,0});
            ByteArrayOutputStream target=new ByteArrayOutputStream();target.write(new byte[]{0x12,2,0});string(target,0x31,"right");target.write(new byte[]{0,0,0});string(target,0x31,"left");target.write(new byte[]{0,0,0});
            Fixture transfer=new Fixture(code(out.toByteArray()),code(target.toByteArray()));transfer.waitAt();check(transfer.vm.scriptName().equals("target"),"actual native script transfer enters the target in mode "+mode);
            check(transfer.host.ui.actions[0].equals(mode==2?"right":"")&&transfer.host.ui.actions[1].equals(mode==2?"left":""),"raw36 mode2 preserves both actions; raw35 modes1/3 clear both according to4137D0");
            if(mode==2)check(transfer.vm.activateMenu(transfer.host.waitTicket(),-1)&&transfer.vm.offset()==transfer.target.label("right".getBytes(Binary.SJIS)),"retained mode2 binding resolves in the newly loaded script");
        }
        return count;
    }
    public static void main(String[] args)throws Exception{System.out.println("PASS "+run()+" DC2 global action assertions");}
}
