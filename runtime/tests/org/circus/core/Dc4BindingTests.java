package org.circus.core;

import java.io.*;
import java.util.*;

/** Regression cases at public persistence/input/VM boundaries. These reproduce
 * lost PH preferences, rejected clicks consuming macros and callbacks hitting
 * the ordinary dialogue command guard; no original game files are required. */
public final class Dc4BindingTests {
    private static int checks;
    private static final byte[] EMPTY=new byte[0];
    private static void check(boolean ok,String why){checks++;if(!ok)throw new AssertionError(why);}
    private static byte[] raw(String text){return text.getBytes(Binary.SJIS);}
    private static void word(ByteArrayOutputStream out,int n){out.write(n);out.write(n>>>8);}
    private static void command(ByteArrayOutputStream out,int opcode,int... args){out.write(opcode);for(int n:args)word(out,n);}
    private static void small(ByteArrayOutputStream out,int opcode,int a,int b){out.write(opcode);out.write(a);out.write(b);}
    private static void body(ByteArrayOutputStream out,Profile p,String text){
        int opcode=p.opcode("text.body");out.write(opcode);for(byte b:raw(text))out.write(opcode>=p.encodedStart?((b&255)-32)&255:b&255);out.write(0);
    }
    private static byte[] script(String text,boolean delay)throws IOException{
        Profile p=Profiles.get("dc4ph-abb6");ByteArrayOutputStream out=new ByteArrayOutputStream();out.write(new byte[]{0,0,0,0,(byte)0xb6,(byte)0xab,0});
        command(out,0xb0,0,0,0,0);command(out,0xb0,255,0,0,0);
        command(out,0x8b,123,0,0,0);command(out,0x90,5,12,23,34);small(out,0x0f,5,0);
        command(out,0xb0,0,0,0,0);
        if(delay)small(out,p.opcode("wait.duration"),1,0);
        body(out,p,text);small(out,p.opcode("wait.message"),0,0);small(out,0,0,0);return out.toByteArray();
    }
    private static CircusResources resources(Profile p)throws IOException{
        final byte[] names=new byte[20];GameResources files=new GameResources(new GameResources.Storage(){
            public Set<String> paths(){return Collections.singleton("AdvData/GRP/NAMES.DAT");}
            public byte[] read(String path,int limit)throws IOException{if(limit<names.length)throw new IOException("Fixture read limit");return names.clone();}
            public Archive.Source source(String path){return new Archive.Source(){public long size(){return names.length;}public byte[] read(long at,int length)throws IOException{Binary.range(at,length,names.length);return Arrays.copyOfRange(names,(int)at,(int)at+length);}};}
        });return new CircusResources(files,p);
    }
    private static Dc4Host host()throws IOException{Profile p=Profiles.get("dc4ph-abb6");Dc4Host h=new Dc4Host(resources(p),TextCodec.identity(),true);h.attach(new Variables(h,p.variableModel));return h;}
    private static MesVm vm(Dc4Host h,byte[] bytes)throws IOException{Profile p=Profiles.get("dc4ph-abb6");return new MesVm(name->new MesScript(name,bytes,p),h,"START");}
    private static byte[] scene(Dc4Host h)throws IOException{ByteArrayOutputStream bytes=new ByteArrayOutputStream();h.scene.save(new DataOutputStream(bytes));return bytes.toByteArray();}
    private static void persistence()throws Exception{
        Dc3State original=new Dc3State("dc4ph-abb6",true);for(int index:new int[]{180,181,182})original.write(99,index,0);
        byte[] persistent=original.savePersistent();Dc3State cold=new Dc3State("dc4ph-abb6",true);cold.loadPersistent(persistent);
        for(int index:new int[]{180,181,182})check(cold.read(99,index)==0,"Native persistent PH preference99:"+index+" survives process-level persistence");
        check(new Dc3State("dc3dd-a5a8",true).savePersistent().length==4680,"Existing DD portable persistent wire length stays4680 bytes");
        check(persistent.length==4692,"PH adds exactly its three native persistent registers before voice data");
        Dc4Host h=host();h.command("system.command",new int[]{18,0},EMPTY);h.state.write(99,181,0);h.command("system.command",new int[]{18,1},EMPTY);
        check(h.variable(3,0)==1,"Native settings comparison detects a PH-only preference change");
        h.state.resetPhPreferences(-1);h.state.voiceCount=1;h.state.write(94,0,80);h.command("system.command",new int[]{18,0},EMPTY);
        for(int index:new int[]{180,181,182})h.state.write(99,index,0);h.state.write(94,0,30);
        h.command("system.command",new int[]{18,2},EMPTY);for(int index:new int[]{180,181,182})check(h.state.read(99,index)==1,"Native rollback restores PH-only preference99:"+index);
        check(h.state.read(94,0)==30,"Native in-region rollback retains out-of-region current voice preference");
    }
    private static void rejectedClick()throws Exception{
        byte[] bytes=script("AB",true);Dc4Host h=host();MesVm vm=vm(h,bytes);
        check(vm.run(6)==MesVm.Status.RUNNING&&h.phMacros.recordedCount(0)==3,"Recording stores three commands without publishing their effects");
        check(h.scene.slots[4].x==0,"Recorded actor movement has no immediate effect");
        h.phMacros.parseBody(raw("@t10@d1"),3);check(vm.run(1)==MesVm.Status.WAIT_HOST&&h.remainingMillis()==200,"Actual VM enters a200ms non-interruptible native delay");
        byte[] before=scene(h);int color=h.ui.palette[5];long ticket=h.waitTicket();
        check(!vm.signalHost(ticket,MesVm.HostSignal.ADVANCE),"Advance is rejected by non-interruptible delay");
        check(h.phMacros.pendingCount()==1&&Arrays.equals(before,scene(h))&&h.ui.palette[5]==color,"Rejected click preserves pending macro and complete scene/palette");
        check(!vm.signalHost(ticket+1,MesVm.HostSignal.ADVANCE)&&h.phMacros.pendingCount()==1,"Stale ticket also preserves pending macro");
        vm.elapse(200);check(h.phMacros.pendingCount()==1&&h.scene.slots[4].x==0,"Delay expiration does not fast-forward the one-second scheduled macro");
    }
    private static void textCallback()throws Exception{
        byte[] bytes=script("A@t2@d1B",false);Dc4Host h=host();MesVm vm=vm(h,bytes);int originalColor=h.ui.palette[5];
        check(vm.run(100)==MesVm.Status.WAIT_HOST&&h.dialogue.shownText.equals("AB"),"Real PH text wait admits cleaned body and deferred macro");
        long ticket=h.waitTicket();vm.elapse(199);check(h.phMacros.pendingCount()==1&&h.scene.slots[4].x==0&&h.ui.palette[5]==originalColor,"Dialogue wait retains original scene until macro deadline");
        byte[] saved=vm.snapshot();Dc4Host restored=host();MesVm resumed=vm(restored,bytes);resumed.restore(saved);resumed.elapse(1);restored.ui.checkBounds();
        check(resumed.run(1)==MesVm.Status.WAIT_HOST&&resumed.error().isEmpty(),"Asynchronous macro host commands run during actual dialogue wait without fault");
        check(restored.scene.slots[4].x==123&&restored.scene.published.layers[4].x==123&&restored.ui.palette[5]==0xff0c1722,"Timed callback applies and publishes recorded actor/palette exactly at deadline");
        check(restored.phMacros.pendingCount()==0&&restored.waitTicket()==ticket&&restored.dialogue.shownText.equals("AB"),"Macro completion retains the current dialogue event ticket/body");
        resumed.elapse(1);check(resumed.run(1)==MesVm.Status.WAIT_HOST&&restored.scene.slots[4].x==123,"Completed callback does not replay or release dialogue on a later frame");
        check(resumed.signalHost(ticket,MesVm.HostSignal.TEXT_COMPLETE)&&resumed.signalHost(ticket,MesVm.HostSignal.ADVANCE),"Original dialogue can still accept render completion and advance after callback");
        check(resumed.run(10)==MesVm.Status.HALTED,"Accepted original advance continues to the actual terminal record");
        Dc4Host immediate=host();MesVm now=vm(immediate,script("A@d1B",false));
        check(now.run(100)==MesVm.Status.WAIT_HOST&&immediate.scene.published.layers[4].x==123&&immediate.phMacros.pendingCount()==0,"Zero-delay callback safely publishes from inside the initial text-wait publish hook");
    }
    private static void oldDialogueGuard()throws Exception{
        Dc3Host dd=new Dc3Host(null,TextCodec.identity(),true);dd.attach(new Variables(dd));dd.command("text.body",new int[0],raw("AB"));dd.command("wait.message",new int[]{0,0},EMPTY);
        try{dd.command("scene.transition",new int[]{5,0},EMPTY);throw new AssertionError("DD allowed a host command through its live dialogue gate");}
        catch(IOException expected){check(expected.getMessage().contains("Command while dialogue is waiting"),"PH-only callback bypass leaves the previous DD dialogue guard intact");}
    }
    public static int run()throws Exception{checks=0;persistence();rejectedClick();textCallback();oldDialogueGuard();return checks;}
    public static void main(String[] args)throws Exception{System.out.println("Dc4BindingTests "+run()+" PASS");}
}
