package org.circus.core;

import java.io.*;
import java.util.*;

/** Independent DC2PC descriptors and poll vectors:41E8C5,41E993,41BF2F,
 * 420C10,41A950. Native geometry goldens are in the holdout contract artifact. */
public final class Pc2ActorTests {
    private static int checks;
    private static void check(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
    private interface Attempt {void run()throws Exception;}
    private static void fails(Attempt work,String fragment)throws Exception{
        try{work.run();throw new AssertionError("Expected PC2 failure: "+fragment);}catch(IOException expected){check(expected.getMessage().contains(fragment),expected.getMessage());}
    }
    private static CircusResources resources()throws IOException{
        final byte[] names=new byte[20];
        GameResources files=new GameResources(new GameResources.Storage(){
            public Set<String> paths(){return Collections.singleton("AdvData/DAT/NAMES.DAT");}
            public byte[] read(String path,int limit)throws IOException{if(names.length>limit)throw new IOException("Fixture read limit");return names.clone();}
            public Archive.Source source(String path){return new Archive.Source(){public long size(){return names.length;}public byte[] read(long at,int count)throws IOException{Binary.range(at,count,names.length);return Arrays.copyOfRange(names,(int)at,(int)at+count);}};}
        });
        return new CircusResources(files,Profiles.get("dc2pc-5769"));
    }
    private static Pc2Host host(CircusResources data)throws IOException{
        Pc2Host h=new Pc2Host(data,TextCodec.identity(),1);h.attach(new Variables(h,data.profile.variableModel));
        h.loadedInitial(new MesScript("START",new byte[]{0,0,0,0,0x69,0x57,0,0,0},data.profile),6);return h;
    }
    private static void command(Pc2Host h,String op,int... args)throws IOException{h.command(op,args,new byte[0]);}
    private static void load(Pc2Host h,int actor,String name)throws IOException{h.command("pc.character",new int[]{actor},name.getBytes(Binary.SJIS));}
    private static void publish(Pc2Host h)throws IOException{command(h,"dm.ui.commit");}
    private static byte[] save(Pc2Host h)throws IOException{ByteArrayOutputStream out=new ByteArrayOutputStream();h.save(new DataOutputStream(out));return out.toByteArray();}
    private static void words(ByteArrayOutputStream out,int opcode,int... args){out.write(opcode);for(int value:args){out.write(value);out.write(value>>>8);}}
    public static int run()throws Exception{
        checks=0;CircusResources data=resources();Pc2Host h=host(data);
        check(h.pc1==null&&h.second,"PC2 host keeps independently bound family");
        command(h,"pc.actor_position",10,20,2,999);command(h,"pc.actor_transform",75,441,322,18);load(h,2,"actor");
        check(h.scene.shown.actors[2][0]==0&&h.scene.shown.slots[4].isEmpty(),"actor loading retains prior frame until composition");publish(h);
        check(Arrays.equals(h.scene.shown.actors[2],new int[]{10,20,75,441,322,0}),"normal load commits stage XYZ and preserves pivot");
        command(h,"pc.actor_position",-50,600,2,999);publish(h);
        check(Arrays.equals(Arrays.copyOf(h.scene.shown.actors[2],3),new int[]{10,20,75}),"64 staging survives generic publish without early commit");
        check(Arrays.equals(Arrays.copyOf(h.scene.character(2),3),new int[]{-50,600,100}),"64 writes stage XY and stage zoom100");
        load(h,2,"");publish(h);check(h.scene.shown.actors[2][0]==10,"empty actor payload does not commit stage");
        command(h,"pc.actor_transform",75,441,322,18);load(h,2,"replacement");publish(h);
        command(h,"pc.actor_transform",-30,590,777,2);long ticket=h.waitTicket();
        check(h.actorMotionActive()&&h.waiting()&&!h.signal(ticket,MesVm.HostSignal.ADVANCE),"65 owns native wait and rejects ordinary advancement");
        h.write(99,12,0);h.elapse(0);
        check(Arrays.equals(Arrays.copyOf(h.scene.shown.actors[2],3),new int[]{-42,592,75}),"first native poll moves8 on both axes and ignores arg2");
        check(Arrays.equals(Arrays.copyOf(h.scene.character(2),3),new int[]{-50,600,75}),"native motion does not change staged XYZ");
        h.elapse(1000);check(h.scene.shown.actors[2][0]==-34&&h.scene.shown.actors[2][1]==590,"delayed polling coalesces to one8 step and clamps Y");
        h.elapse(10);check(h.scene.shown.actors[2][0]==-30&&h.actorMotionActive(),"arrival still returns waiting");
        h.elapse(9);byte[] state=save(h);Pc2Host restored=host(data);restored.load(new DataInputStream(new ByteArrayInputStream(state)));
        check(Arrays.equals(state,save(restored)),"snapshot preserves live stage and partial native poll");
        restored.elapse(1);check(!restored.actorMotionActive()&&!restored.waiting(),"following10ms poll releases native wait");
        restored.command("text.body",new int[0],"hello".getBytes(Binary.SJIS));
        check(restored.scene.shown.actors[2][0]==-30&&restored.scene.character(2,0)==-50,"text publish retains completed live motion and old stage");
        publish(restored);check(restored.scene.shown.actors[2][0]==-30,"subsequent redraw never reverts65 motion");
        restored.captureUi();SceneCaptures.Legacy captured=new SceneCaptures.Legacy(restored.uiCapture.input());
        check(captured.frame.actors[2][0]==-30&&captured.frame.actors[2][2]==75,"captureUi freezes native live descriptor before returning from parent command");
        load(restored,2,"next");publish(restored);check(restored.scene.shown.actors[2][0]==-50,"next actor load reuses unchanged staged position");

        for(int speed=0;speed<4;speed++){
            Pc2Host s=host(data);s.write(99,12,speed);command(s,"pc.actor_transform",200,-200,-999,0);s.elapse(0);
            check(s.scene.shown.actors[0][0]==(8<<speed)&&s.scene.shown.actors[0][1]==-(8<<speed),"PC2 original speed"+speed);
        }
        Pc2Host ctrl=host(data);ctrl.write(99,12,0);ctrl.motionSkipPressed=true;ctrl.variables.write(8,18,1);command(ctrl,"pc.actor_transform",100,100,0,1);ctrl.elapse(0);
        check(ctrl.scene.shown.actors[1][0]==64,"PC2 Ctrl/latch step64 has no DC2 bank8:18 gate");
        command(ctrl,"pc.actor_transform",64,64,777,1);ctrl.elapse(0);check(!ctrl.actorMotionActive(),"already equal native poll returns immediately without zoom assignment");
        ctrl.write(99,25,1);command(ctrl,"pc.actor_transform",100,100,0,1);ctrl.elapse(0);
        check(ctrl.scene.shown.actors[1][0]==72,"PC2 native99:25 disables the Ctrl/latch speed override");
        command(h,"pc.actor_transform",80,1,90,255);check(h.scene.character(0,2)==80&&h.scene.character(1,2)==90,"PC2 FF scales use args0 and2 rather than SC args0 and1");
        fails(()->command(h,"pc.actor_position",0,0,-1,0),"descriptor -1");
        fails(()->command(h,"pc.actor_transform",0,0,0,5),"descriptor 5");
        fails(()->command(h,"pc.actor_position",40000,0,0,0),"signed native word");

        Pc2Host menu=host(data);command(menu,"pc.actor_position",5,6,0,0);command(menu,"pc.actor_transform",75,441,296,16);load(menu,0,"saved");publish(menu);
        command(menu,"pc.actor_position",17,18,0,0);menu.enterSystem();
        command(menu,"pc.actor_position",200,300,0,0);command(menu,"pc.actor_transform",25,11,12,16);load(menu,0,"menu");load(menu,0,"--");publish(menu);
        check(menu.scene.slots[2].equals("saved")&&Arrays.equals(menu.scene.shown.actors[0],new int[]{17,18,100,11,12,0}),"-- restores saved stage prefix and image while retaining current pivots");
        PcActorLayout normal=PcActorLayout.dc2pc(new int[]{87,0,75,441,322,0},882,756);
        PcActorLayout close=PcActorLayout.dc2pc(new int[]{-125,0,100,441,296,0},1260,1080);
        check(normal.x==198&&normal.y==81&&normal.width==661&&normal.height==567,"original instruction scaled geometry golden");
        check(close.x==-310&&close.y==-124&&close.width==1260&&close.height==1080,"original instruction closeup geometry golden");

        ByteArrayOutputStream code=new ByteArrayOutputStream();code.write(new byte[]{0,0,0,0,0x69,0x57});words(code,0x64,-80,120,1,0);words(code,0x65,24,16,555,1);code.write(new byte[]{0,0,0});
        MesScript script=new MesScript("START",code.toByteArray(),data.profile);Pc2Host vmHost=host(data);MesVm vm=new MesVm(name->script,vmHost,"START");
        check(vm.run(10)==MesVm.Status.WAIT_HOST&&vm.offset()==24,"real PC2 decoder yields65 after staged64");
        byte[] vmState=vm.snapshot();Pc2Host copyHost=host(data);MesVm copy=new MesVm(name->script,copyHost,"START");copy.restore(vmState);
        check(Arrays.equals(vmState,copy.snapshot()),"whole VM PC2 actor snapshot roundtrip");
        copyHost.motionSkipPressed=true;copy.elapse(10);check(copyHost.actorMotionActive(),"native target frame remains waiting after VM restore");copy.elapse(10);
        check(copy.run(5)==MesVm.Status.HALTED&&copyHost.scene.character(1,0)==-80&&copyHost.scene.shown.actors[1][0]==24,"VM resumes65 with staged and displayed positions distinct");
        return checks;
    }
    public static void main(String[] args)throws Exception{System.out.println("Pc2ActorTests "+run()+" PASS");}
}
