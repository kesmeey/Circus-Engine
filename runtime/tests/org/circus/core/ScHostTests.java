package org.circus.core;

import java.io.*;
import java.util.*;

/** Independent SC contracts: stage/live separation, native65 polls, and service6.
 * Native references are419E00,41E490,4074C0 in the SC holdout evidence directory. */
public final class ScHostTests {
    private static int checks;
    private static void check(boolean ok,String message){checks++;if(!ok)throw new AssertionError(message);}
    private interface Attempt {void run()throws Exception;}
    private static void fails(Attempt work,String fragment)throws Exception {
        try{work.run();throw new AssertionError("Expected SC failure: "+fragment);}catch(IOException expected){check(expected.getMessage().contains(fragment),"precise failure: "+expected.getMessage());}
    }
    private static CircusResources resources()throws IOException {
        final byte[] names=new byte[20];
        GameResources files=new GameResources(new GameResources.Storage(){
            public Set<String> paths(){return Collections.singleton("AdvData/DAT/NAMES.DAT");}
            public byte[] read(String path,int limit)throws IOException{if(names.length>limit)throw new IOException("Fixture read limit");return names.clone();}
            public Archive.Source source(String path){return new Archive.Source(){public long size(){return names.length;}public byte[] read(long at,int count)throws IOException{Binary.range(at,count,names.length);return Arrays.copyOfRange(names,(int)at,(int)at+count);}};}
        });
        return new CircusResources(files,Profiles.get("dc2sc-3b69"));
    }
    private static ScHost host(CircusResources data){ScHost host=new ScHost(data,TextCodec.identity(),1);host.attach(new Variables(host,data.profile.variableModel));return host;}
    private static byte[] save(ScHost host)throws IOException{ByteArrayOutputStream out=new ByteArrayOutputStream();host.save(new DataOutputStream(out));return out.toByteArray();}
    private static void rectangles(ScHost host){host.grid.clear();for(int row=0;row<5;row++){Dc2Ui.Button button=new Dc2Ui.Button();button.left=444;button.right=732;button.top=97+85*row;button.bottom=169+85*row;host.grid.add(button);}}
    private static void words(ByteArrayOutputStream out,int opcode,int... args){out.write(opcode);for(int value:args){out.write(value);out.write(value>>>8);}}

    public static int run()throws Exception {
        checks=0;CircusResources data=resources();Profile profile=data.profile;
        check(profile.variableModel==Variables.Model.PC_BYTE,"SC uses native byte model rather than PC2 wide banks");
        check(profile.operation(0x1c).equals("sc.command")&&profile.operation(0x64).equals("sc.actor_position")&&profile.operation(0x65).equals("sc.actor_transform"),"real SC opcodes reach SC host operations");
        ScHost h=host(data);check(h.second&&h.pc1==null,"SC uses second-generation common primitives without PC1 state");
        h.scene.shown.actors[2][0]=7;h.scene.shown.actors[2][1]=9;
        h.command("sc.actor_position",new int[]{-50,600,2,99},new byte[0]);
        check(h.scene.character(2,0)==-50&&h.scene.character(2,1)==600&&h.scene.character(2,2)==100,"64 signed XY and reset scale");
        check(h.scene.shown.actors[2][0]==7&&h.scene.shown.actors[2][1]==9&&!h.waiting(),"64 stages coordinates without publishing or waiting");
        h.command("sc.actor_transform",new int[]{75,440,330,18},new byte[0]);
        check(Arrays.equals(Arrays.copyOfRange(h.scene.character(2),2,5),new int[]{75,440,330}),"65 selector18 writes actor2 scale and pivots");
        check(h.scene.shown.actors[2][0]==7&&h.scene.shown.actors[2][1]==9,"nonmodal65 does not overwrite displayed XY");
        h.scene.slots[2]="first";h.scene.slots[3]="second";h.command("sc.actor_position",new int[]{0,0,255,0},new byte[0]);
        check(h.scene.slots[2].equals("second")&&h.scene.slots[3].equals("first"),"64 FF swaps first two resource slots");
        h.scene.character(0,2,87);h.scene.character(1,2,92);h.command("sc.actor_transform",new int[]{-1,80,777,255},new byte[0]);
        check(h.scene.character(0,2)==87&&h.scene.character(1,2)==80,"65 FF keeps -1 scale and assigns the other scale");
        fails(()->h.command("sc.actor_position",new int[]{1,2,4,0},new byte[0]),"descriptor 4");
        fails(()->h.command("sc.actor_transform",new int[]{1,2,3,5},new byte[0]),"descriptor 5");
        fails(()->h.command("sc.actor_position",new int[]{1,2,-1,0},new byte[0]),"descriptor -1");

        h.scene.shown.actors[2][0]=0;h.scene.shown.actors[2][1]=0;h.write(99,12,0);
        h.command("sc.actor_transform",new int[]{20,10,777,2},new byte[0]);long ticket=h.waitTicket();
        check(h.waiting()&&h.actorMotionActive(),"native65 owns a cooperative host wait");
        check(!h.signal(ticket,MesVm.HostSignal.ADVANCE)&&!h.signal(ticket+1,MesVm.HostSignal.ADVANCE),"ordinary or stale advancement does not finish native motion");
        h.elapse(0);check(h.scene.shown.actors[2][0]==8&&h.scene.shown.actors[2][1]==8,"first65 poll moves both displayed axes by native8");
        check(h.scene.character(2,0)==-50&&h.scene.character(2,1)==600&&h.scene.character(2,2)==75,"modal65 preserves staged XY and ignores arg2 scale");
        h.elapse(1000);check(h.scene.shown.actors[2][0]==16&&h.scene.shown.actors[2][1]==10,"delayed timer coalesces to one poll and clamps Y");
        h.elapse(10);check(h.scene.shown.actors[2][0]==20&&h.actorMotionActive(),"target-reaching frame remains waiting");
        h.elapse(9);check(h.actorMotionActive(),"release waits for next10ms poll");
        byte[] state=save(h);ScHost restored=host(data);restored.load(new DataInputStream(new ByteArrayInputStream(state)));
        check(Arrays.equals(state,save(restored))&&restored.actorMotionActive(),"SC snapshot preserves displayed target, next poll, and staged coordinates");
        restored.elapse(1);check(!restored.actorMotionActive()&&!restored.waiting(),"saved timer remainder releases on next poll");

        for(int speed=0;speed<4;speed++){
            ScHost speedHost=host(data);speedHost.write(99,12,speed);speedHost.command("sc.actor_transform",new int[]{200,-200,999,0},new byte[0]);speedHost.elapse(0);
            int step=8<<speed;check(speedHost.scene.shown.actors[0][0]==step&&speedHost.scene.shown.actors[0][1]==-step,"SC speed"+speed+" native step"+step);
        }
        ScHost rapid=host(data);rapid.write(99,12,0);rapid.variables.write(8,18,1);rapid.motionSkipPressed=true;rapid.command("sc.actor_transform",new int[]{200,0,0,1},new byte[0]);rapid.elapse(0);
        check(rapid.scene.shown.actors[1][0]==64,"SC418140 Ctrl/latch does not invent DC2 bank8:18 gate");
        rapid.command("sc.actor_transform",new int[]{64,0,200,1},new byte[0]);rapid.elapse(0);check(!rapid.actorMotionActive(),"already matching displayed coordinates release without redraw or scale assignment");

        ScHost live=host(data);MesScript empty=new MesScript("START",new byte[]{0,0,0,0,0x69,0x3b,0,0,0},profile);live.position(empty,6);
        live.command("sc.actor_position",new int[]{12,24,0,0},new byte[0]);live.command("dm.ui.commit",new int[0],new byte[0]);
        check(live.scene.shown.actors[0][0]==0&&live.scene.shown.actors[0][1]==0&&live.scene.shown.actors[0][2]==0,"publish does not apply64 stage before an actor load; native live starts BSS0");
        live.command("pc.character",new int[]{0},"actor".getBytes(Binary.SJIS));
        check(live.displayedActor(0,0)==12&&live.scene.shown.actors[0][0]==0,"actor load commits native live descriptor without repainting retained frame");
        live.command("dm.ui.commit",new int[0],new byte[0]);
        check(Arrays.equals(Arrays.copyOf(live.scene.shown.actors[0],3),new int[]{12,24,100}),"next real publish projects committed live XYZ");
        live.command("sc.actor_position",new int[]{90,80,0,0},new byte[0]);live.command("sc.actor_transform",new int[]{50,400,300,16},new byte[0]);
        live.command("text.body",new int[0],"body".getBytes(Binary.SJIS));
        check(Arrays.equals(Arrays.copyOf(live.scene.shown.actors[0],3),new int[]{12,24,100}),"text refresh keeps live XYZ despite later64/65 staged values");
        check(live.scene.shown.actors[0][3]==400&&live.scene.shown.actors[0][4]==300,"native renderer reads pivot from current staged descriptor");
        live.write(99,12,3);live.command("sc.actor_transform",new int[]{28,40,777,0},new byte[0]);live.elapse(0);live.elapse(10);
        live.command("text.body",new int[0],"next".getBytes(Binary.SJIS));
        check(Arrays.equals(Arrays.copyOf(live.scene.shown.actors[0],3),new int[]{28,40,100}),"text after65 retains moved live XY and prior live zoom");
        live.command("pc.wait",new int[]{1,0},new byte[0]);
        check(live.scene.shown.actors[0][0]==28&&live.scene.shown.actors[0][1]==40,"wait publish does not reset65 movement to staged XY");
        live.command("pc.transition",new int[]{9,0},new byte[0]);
        try(DataInputStream captured=live.uiCapture.input()){
            SceneCaptures.Legacy image=new SceneCaptures.Legacy(captured);
            check(image.frame.actors[0][0]==28&&image.frame.actors[0][1]==40&&image.frame.actors[0][2]==100,"publish projection applies before captureUi clones the frame inside its own parent call");
        }
        byte[] liveState=save(live);ScHost liveCopy=host(data);liveCopy.load(new DataInputStream(new ByteArrayInputStream(liveState)));liveCopy.command("dm.ui.commit",new int[0],new byte[0]);
        check(liveCopy.scene.shown.actors[0][0]==28&&liveCopy.scene.character(0,0)==90,"snapshot preserves independent live authority across a later publish");
        liveCopy.command("pc.character",new int[]{0},"new_actor".getBytes(Binary.SJIS));liveCopy.command("dm.ui.commit",new int[0],new byte[0]);
        check(Arrays.equals(Arrays.copyOf(liveCopy.scene.shown.actors[0],3),new int[]{90,80,50}),"next actor load deliberately commits staged XYzoom to live");
        liveCopy.command("sc.actor_position",new int[]{-7,-9,0,0},new byte[0]);liveCopy.command("pc.character",new int[]{0},new byte[0]);liveCopy.command("dm.ui.commit",new int[0],new byte[0]);
        check(liveCopy.scene.shown.actors[0][0]==90&&liveCopy.scene.shown.actors[0][1]==80,"empty actor name does not commit staged XY");

        ScHost system=host(data);system.command("sc.actor_position",new int[]{11,22,0,0},new byte[0]);system.command("sc.actor_transform",new int[]{80,300,400,16},new byte[0]);
        system.command("pc.character",new int[]{0},"original_actor".getBytes(Binary.SJIS));system.write(99,12,3);
        system.command("sc.actor_transform",new int[]{75,86,777,0},new byte[0]);system.elapse(0);system.elapse(10);
        check(system.displayedActor(0,0)==75&&system.displayedActor(0,1)==86&&system.scene.character(0,0)==11&&system.scene.character(0,1)==22,"system restore fixture has distinct live and staged XY");
        system.enterSystem();check(system.systemScene.actors[0][0]==75&&system.systemScene.actors[0][1]==86,"system displayed snapshot retains moved live XY independently");
        system.command("sc.actor_position",new int[]{501,502,0,0},new byte[0]);system.command("sc.actor_transform",new int[]{60,700,800,16},new byte[0]);system.scene.character(0,5,19);
        system.command("pc.character",new int[]{0},"temporary_actor".getBytes(Binary.SJIS));
        byte[] systemBytes=save(system);ScHost systemCopy=host(data);systemCopy.load(new DataInputStream(new ByteArrayInputStream(systemBytes)));
        check(Arrays.equals(systemBytes,save(systemCopy)),"SC3 snapshot retains saved stage separately from live and current stage");
        systemCopy.command("pc.character",new int[]{0},"--".getBytes(Binary.SJIS));
        check(systemCopy.scene.slots[2].equals("original_actor")&&systemCopy.scene.character(0,0)==11&&systemCopy.scene.character(0,1)==22&&systemCopy.scene.character(0,2)==80,"419960 -- restores saved stage XYZ rather than saved displayed75/86");
        check(systemCopy.scene.character(0,3)==700&&systemCopy.scene.character(0,4)==800&&systemCopy.scene.character(0,5)==19,"419960 -- preserves current pivot and later fields instead of copying six snapshot fields");
        check(systemCopy.displayedActor(0,0)==11&&systemCopy.displayedActor(0,1)==22&&systemCopy.displayedActor(0,2)==80,"restored saved stage commits XYZ into native live owner");
        systemCopy.command("dm.ui.commit",new int[0],new byte[0]);
        check(Arrays.equals(systemCopy.scene.shown.actors[0],new int[]{11,22,80,700,800,19}),"restored actor render uses saved XYZ and current pivots after publish");
        ScHost absent=host(data);absent.systemScene=new Dc2Scene.Frame();
        fails(()->absent.command("pc.character",new int[]{0},"--".getBytes(Binary.SJIS)),"captured saved stage");
        byte[] invalidSystem=systemBytes.clone();invalidSystem[invalidSystem.length-4]=0;invalidSystem[invalidSystem.length-3]=0;invalidSystem[invalidSystem.length-2]=(byte)0x9c;invalidSystem[invalidSystem.length-1]=0x40;
        fails(()->host(data).load(new DataInputStream(new ByteArrayInputStream(invalidSystem))),"outside native signed word");

        ScHost page=host(data);rectangles(page);page.write(99,0,0);page.command("sc.command",new int[]{6,0},new byte[0]);
        check(page.saveGrid&&page.gridSlot(0)==0&&page.gridSlot(1)==1&&page.gridSlot(4)==4,"SC Save binds five rows and reserved0/1");
        page.gridWaiting=page.ui.waiting=true;check(page.menuActionForGrid(0)==null&&page.menuActionForGrid(1)==null,"native Save reserved slots have empty actions");
        check(new String(page.menuActionForGrid(2),Binary.SJIS).equals("selsave")&&page.read(99,254)==2,"Save dispatches selected2 to local selsave");
        page.availableSaves.add(1);page.command("sc.command",new int[]{6,1},new byte[0]);page.gridWaiting=page.ui.waiting=true;
        check(!page.saveGrid&&page.menuActionForGrid(0)==null,"native Load disables missing0");
        check(new String(page.menuActionForGrid(1),Binary.SJIS).equals("selsave")&&page.read(99,254)==1,"SC Load uses same selsave label, not PC2 selsave2");
        page.write(99,0,2);page.command("sc.command",new int[]{6,0},new byte[0]);page.write(99,0,3);page.gridWaiting=page.ui.waiting=true;
        check(page.gridSlot(0)==16&&page.gridSlot(4)==20,"SC service6 binds page*8 slots once");
        byte[] pageState=save(page);ScHost pageCopy=host(data);pageCopy.load(new DataInputStream(new ByteArrayInputStream(pageState)));
        check(pageCopy.gridSlot(4)==20&&new String(pageCopy.menuActionForGrid(4),Binary.SJIS).equals("selsave")&&pageCopy.read(99,254)==20,"snapshot retains frozen native slot binding despite changed pager");
        fails(()->page.command("sc.command",new int[]{20,0},new byte[0]),"system service");
        fails(()->page.command("pc.command",new int[]{9,0},new byte[0]),"Unimplemented SC");
        fails(()->page.command("native.unmodelled",new int[]{0,0,0,0},new byte[0]),"Unimplemented SC");
        fails(()->page.command("sc.debug67",new int[]{0,0,128,0},new byte[0]),"Unimplemented SC");

        // Check real profile dispatch and whole VM restore, not only direct calls.
        ByteArrayOutputStream code=new ByteArrayOutputStream();code.write(new byte[]{0,0,0,0,0x69,0x3b});words(code,0x64,-80,120,1,0);words(code,0x65,24,16,555,1);code.write(new byte[]{0,0,0});
        MesScript script=new MesScript("START",code.toByteArray(),profile);ScHost vmHost=host(data);MesVm vm=new MesVm(name->script,vmHost,"START");
        check(vm.run(10)==MesVm.Status.WAIT_HOST&&vm.offset()==24&&vmHost.actorMotionActive(),"actual64/65 opcode sequence commits PC then waits");
        byte[] vmState=vm.snapshot();ScHost copyHost=host(data);MesVm copy=new MesVm(name->script,copyHost,"START");copy.restore(vmState);
        check(Arrays.equals(vmState,copy.snapshot()),"whole VM SC state roundtrip");
        copyHost.motionSkipPressed=true;copy.elapse(10);check(copyHost.actorMotionActive(),"actual modal native target frame stays waiting");copy.elapse(10);
        check(copy.run(5)==MesVm.Status.HALTED&&copyHost.scene.character(1,0)==-80&&copyHost.scene.character(1,1)==120,"native65 completion resumes script while64 staged target remains unchanged");
        return checks;
    }
    public static void main(String[] args)throws Exception{System.out.println("ScHostTests "+run()+" PASS");}
}
