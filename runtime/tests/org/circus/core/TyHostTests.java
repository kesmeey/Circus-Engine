package org.circus.core;

import java.io.*;
import java.util.*;

/** TY native41DAB0/41D260/4221A0 and4071D0 contracts. */
public final class TyHostTests {
    private static int checks;
    private static void check(boolean yes,String why){checks++;if(!yes)throw new AssertionError(why);}
    private interface Attempt {void run()throws Exception;}
    private static void fails(Attempt work,String fragment)throws Exception{
        try{work.run();throw new AssertionError("Expected TY failure "+fragment);}catch(IOException expected){check(expected.getMessage().contains(fragment),expected.getMessage());}
    }
    private static byte[] names(){int m=96*192,s=299*132;byte[] b=new byte[20+m+s+188];b[4]=96;for(int n=0;n<96;n++){byte[] name=("music/"+n).getBytes(Binary.SJIS);System.arraycopy(name,0,b,8+n*192,name.length);}b[8+m]=43;b[9+m]=1;for(int n=0;n<299;n++){byte[] name=("sound/"+n).getBytes(Binary.SJIS);System.arraycopy(name,0,b,12+m+n*132,name.length);}b[16+m+s]=1;System.arraycopy("story".getBytes(Binary.SJIS),0,b,20+m+s,5);return b;}
    private static TyHost host()throws Exception{
        GameResources f=new GameResources(new GameResources.Storage(){
            public Set<String> paths(){return Collections.singleton("AdvData/DAT/NAMES.DAT");}
            public byte[] read(String p,int limit)throws IOException{if(names().length>limit)throw new IOException("Fixture bound");return names();}
            public Archive.Source source(String p){return new Archive.Source(){public long size(){return names().length;}public byte[] read(long at,int count)throws IOException{Binary.range(at,count,names().length);return Arrays.copyOfRange(names(),(int)at,(int)at+count);}};}
        });
        CircusResources data=new CircusResources(f,Profiles.get("dc2ty-5f69"));TyHost h=new TyHost(data,TextCodec.identity(),1);h.attach(new Variables(h,Variables.Model.PC_WIDE));return h;
    }
    private static byte[] save(TyHost h)throws IOException{ByteArrayOutputStream b=new ByteArrayOutputStream();h.save(new DataOutputStream(b));return b.toByteArray();}
    private static MesScript scrollScript(Profile p)throws IOException{
        ByteArrayOutputStream b=new ByteArrayOutputStream();b.write(new byte[]{0,0,0,0,0x69,0x5f});b.write(0x62);for(int n:new int[]{0,-5,0,0}){b.write(n);b.write(n>>>8);}b.write(0x5c);for(int n:new int[]{0,0,7,0}){b.write(n);b.write(n>>>8);}b.write(new byte[]{0,0,0});return new MesScript("fixture",b.toByteArray(),p);
    }
    private static void rows(TyHost h){h.grid.clear();for(int n=0;n<5;n++){Dc2Ui.Button b=new Dc2Ui.Button();b.left=430;b.right=750;b.top=80+n*95;b.bottom=b.top+80;h.grid.add(b);}}
    public static int run()throws Exception{
        checks=0;TyHost h=host();Profile p=h.resources.profile;
        check(h.second&&h.pc1==null&&p.variableModel==Variables.Model.PC_WIDE,"TY independently binds PC_WIDE common primitives");
        check(p.operation(0x1c).equals("ty.command")&&p.operation(0x64).equals("ty.actor_position")&&p.operation(0x65).equals("ty.actor_transform"),"TY cases reach its own host");
        for(int[] span:new int[][]{{97,512},{98,1024},{90,1024},{95,100}}){h.write(span[0],span[1]-1,37);check(h.read(span[0],span[1]-1)==37,"Native final byte "+span[0]);final int bank=span[0],end=span[1];fails(()->h.read(bank,end),"byte-bank span");}
        int[] defaults={1,1,25,25,29,1,8,1,0,1,1,8,0,1};for(int n=0;n<defaults.length;n++)check(h.read(99,5+n)==defaults[n],"TY native default99:"+(5+n));
        for(int n=13;n<63;n++)check(h.ui.palette[n]==0xff000000,"TY initial black palette "+n);
        h.variables.write(200,96,0x12345678);check(h.variables.read(0,96)==0x12&&h.variables.read(0,99)==0x78&&h.variables.read(200,96)==0x12345678,"Native big-endian four-byte view");
        check(h.variables.read(200,97)==0,"Wide getter rejects index97");
        h.command("ty.actor_position",new int[]{12,24,0,0},new byte[0]);h.scene.publish(h.ui,0,0);
        check(h.scene.shown.actors[0][0]==0&&h.scene.character(0,0)==12,"64 stages without applying live position");
        h.command("pc.character",new int[]{0},"actor".getBytes(Binary.SJIS));check(h.displayedActor(0,0)==12&&h.scene.shown.actors[0][0]==0,"2B commits live without presenting");
        h.scene.publish(h.ui,0,0);check(Arrays.equals(Arrays.copyOf(h.scene.shown.actors[0],3),new int[]{12,24,100}),"Next native presentation sees committed XYZ");
        h.command("ty.actor_position",new int[]{99,88,0,0},new byte[0]);h.command("pc.character",new int[]{0},new byte[0]);h.scene.publish(h.ui,0,0);
        check(h.scene.shown.actors[0][0]==12&&h.scene.shown.actors[0][1]==24,"Empty2B does not commit pending position");
        h.command("ty.actor_transform",new int[]{75,440,330,16},new byte[0]);h.command("ty.actor_transform",new int[]{-1,80,777,255},new byte[0]);
        check(h.scene.character(0,2)==75&&h.scene.character(1,2)==80,"65FF uses word1 for second actor scale");
        h.write(99,12,0);h.command("ty.actor_transform",new int[]{32,34,999,0},new byte[0]);long ticket=h.waitTicket();h.elapse(0);
        check(h.scene.shown.actors[0][0]==20&&h.scene.shown.actors[0][1]==32&&h.scene.character(0,0)==99,"65 changes liveXY by8 and leaves stage alone");
        check(!h.signal(ticket,MesVm.HostSignal.ADVANCE),"Advance cannot skip native motion");
        h.elapse(1000);check(h.displayedActor(0,0)==28&&h.displayedActor(0,1)==34,"Late timer coalesces and clamps independently");h.elapse(10);check(h.displayedActor(0,0)==32&&h.actorMotionActive(),"Arrival still owns wait");h.elapse(9);
        byte[] snap=save(h);TyHost copy=host();copy.load(new DataInputStream(new ByteArrayInputStream(snap)));check(Arrays.equals(snap,save(copy)),"Snapshot keeps stage/live and timer remainder");copy.elapse(1);check(!copy.actorMotionActive(),"Next poll releases restored arrival");
        for(int speed=0;speed<4;speed++){TyHost s=host();s.write(99,12,speed);s.command("ty.actor_transform",new int[]{200,-200,123,0},new byte[0]);s.elapse(0);check(s.displayedActor(0,0)==(8<<speed)&&s.displayedActor(0,1)==-(8<<speed),"Native motion speed"+speed);}
        TyHost ctrl=host();ctrl.write(99,12,0);ctrl.motionSkipPressed=true;ctrl.write(99,25,1);ctrl.command("ty.actor_transform",new int[]{200,0,0,0},new byte[0]);ctrl.elapse(0);check(ctrl.displayedActor(0,0)==8,"99:25 suppresses physical Ctrl");ctrl.write(99,25,0);ctrl.elapse(10);check(ctrl.displayedActor(0,0)==72,"Enabled physical Ctrl uses64");
        TyHost caller=host();caller.command("ty.actor_position",new int[]{11,22,0,0},new byte[0]);caller.command("ty.actor_transform",new int[]{80,300,400,16},new byte[0]);caller.enterSystem();caller.command("ty.actor_position",new int[]{90,95,0,0},new byte[0]);caller.command("ty.actor_transform",new int[]{50,10,20,16},new byte[0]);caller.command("pc.character",new int[]{0},"--".getBytes(Binary.SJIS));
        check(Arrays.equals(Arrays.copyOf(caller.scene.character(0),5),new int[]{11,22,80,10,20}),"Saved owner restores XYZ and preserves current stage pivot");int[] stage=caller.scene.character(0);caller.command("pc.character_overlay",new int[]{0},"--".getBytes(Binary.SJIS));check(Arrays.equals(stage,caller.scene.character(0)),"Overlay resource restore preserves stage");
        TyHost grid=host();rows(grid);grid.write(99,0,0);grid.command("ty.command",new int[]{6,0},new byte[0]);check(grid.grid.get(0).action.isEmpty()&&grid.grid.get(1).action.isEmpty()&&grid.grid.get(2).action.equals("selsave")&&grid.gridSlot(2)==2,"Native first save page disables quick slots");
        grid.write(99,0,2);grid.command("ty.command",new int[]{6,0},new byte[0]);for(int n=0;n<5;n++)check(grid.gridSlot(n)==16+n,"Five active rows retain native stride8");
        grid.availableSaves.add(18);grid.command("ty.command",new int[]{6,1},new byte[0]);check(grid.grid.get(2).action.equals("selsave2")&&grid.grid.get(0).action.isEmpty(),"Native load enables existing slots with its own label");grid.gridWaiting=grid.ui.waiting=true;check(new String(grid.menuActionForGrid(2),Binary.SJIS).equals("selsave2")&&grid.read(99,254)==18,"Grid selection returns native label and absolute slot");
        TyHost seen=host();seen.loadedInitial(new MesScript("story",new byte[]{0,0,0,0,0x69,0x5f,0,0,0},p),6);seen.command("text.body",new int[0],"first line".getBytes(Binary.SJIS));check(seen.read(98,0)==0&&seen.read(90,0)==0,"4C does not mark seen before scenario transfer");seen.beforeTransfer(2,"story");check(seen.read(99,251)==0&&seen.read(98,0)==0,"Mode2 preserves cached seen and leaves old flags");seen.beforeTransfer(1,"story");check(seen.read(98,0)==1&&seen.read(90,0)==1&&seen.read(99,251)==1,"Wait2E marks previous scenario before loading cached seen");
        fails(()->h.command("ty.command",new int[]{50,0},new byte[0]),"native system service");fails(()->h.command("ty.actor_position",new int[]{0,0,4,0},new byte[0]),"descriptor 4");fails(()->h.command("ty.actor_transform",new int[]{0,0,0,5},new byte[0]),"descriptor 5");fails(()->h.command("ty.scroll",new int[]{0,0,3,0},new byte[0]),"native scroll");
        byte[] trailer={0,0,0,0,0x69,0x5f,0,0,0,0x61,0x64};
        check(new MesScript("SAVE",trailer,p).records().size()==1,"Exact native ad trailer follows final STOP");
        fails(()->new MesScript("SAVE",Arrays.copyOf(trailer,trailer.length+1),p),"range outside input");
        byte[] different=trailer.clone();different[different.length-1]=0x65;fails(()->new MesScript("SAVE",different,p),"range outside input");
        final MesScript program=scrollScript(p);MesVm.Library library=n->program;TyHost nativeScroll=host();nativeScroll.command("pc.background",new int[0],"bg".getBytes(Binary.SJIS));MesVm vm=new MesVm(library,nativeScroll,"fixture");
        check(vm.run(1)==MesVm.Status.WAIT_HOST&&vm.offset()==15&&nativeScroll.tyScroll.active,"Actual62 commits cursor and owns WAIT_HOST");vm.elapse(0);check(nativeScroll.tyScroll.y==-4&&!vm.signalHost(nativeScroll.waitTicket(),MesVm.HostSignal.ADVANCE),"Native scroll ignores ordinary Advance and moves one exact first step");byte[] waiting=vm.snapshot();TyHost newOwner=host();MesVm next=new MesVm(library,newOwner,"fixture");next.restore(waiting);next.elapse(17);check(newOwner.tyScroll.y==-5&&newOwner.tyScroll.active,"Full CVS2 restore continues target-reaching frame");next.elapse(17);check(!newOwner.tyScroll.active&&next.run(20)==MesVm.Status.HALTED&&next.variables.read(0,0)==7,"Later native poll releases and executes the actual following5C");check(newOwner.tyScrollVisible()&&newOwner.tyScroll.draws().size()==2,"Completed scroll retains its own prepared journal");newOwner.command("dm.ui.commit",new int[0],new byte[0]);newOwner.save(new DataOutputStream(new ByteArrayOutputStream()));check(newOwner.tyScroll.initial==null&&newOwner.tyScroll.draws().isEmpty(),"A later real native presentation disposes replaced heavy scroll state");
        final MesScript selectorProgram=new MesScript("fixture",new byte[]{0,0,0,0,0x69,0x5f,0x1c,12,0,0x5c,0,0,0,0,7,0,0,0,0,0,0},p);MesVm.Library selection=n->selectorProgram;TyHost select=host();MesVm selectVm=new MesVm(selection,select,"fixture");selectVm.variables.write(3,33,2);selectVm.variables.write(3,34,3);selectVm.variables.write(3,35,4);
        check(selectVm.run(1)==MesVm.Status.WAIT_HOST&&selectVm.offset()==9&&select.tySelector.fadeMillis()==8&&select.tySelector.unusedSlideMillis()==12,"Actual1C12 consumes ordinary timing bytes and commits PC before native wait");selectVm.elapse(9);check(select.ui.waiting&&select.ui.buttons.size()==5&&select.ui.buttons.get(0).action.equals("ty:0"),"Intro exposes five actual native hit controls");check(selectVm.activateMenu(select.waitTicket(),0)&&select.tySelector.active&&selectVm.variables.read(3,32)==0&&!select.ui.waiting,"First click previews without releasing the interpreter");byte[] preview=selectVm.snapshot();TyHost loaded=host();MesVm loadedVm=new MesVm(selection,loaded,"fixture");loadedVm.restore(preview);loadedVm.elapse(9);check(loaded.ui.waiting&&loaded.tySelector.selected()==0,"New owner resumes the same preview transition");check(!loadedVm.signalHost(loaded.waitTicket(),MesVm.HostSignal.ADVANCE)&&loaded.tySelector.active,"Dialogue Advance cannot auto-confirm a newly ready preview");check(loadedVm.activateMenu(loaded.waitTicket(),0)&&!loaded.tySelector.active&&loadedVm.variables.read(3,32)==0,"Second click confirms exact native result without named-label routing");check(loadedVm.run(20)==MesVm.Status.HALTED&&loadedVm.variables.read(0,0)==7,"Native selector result resumes following5C");loaded.scene.publish(loaded.ui,0,0);loaded.save(new DataOutputStream(new ByteArrayOutputStream()));check(loaded.tySelectorUnderlay==null&&!loaded.tySelector.visible,"Replaced inactive selector releases captured underlay");
        return checks;
    }
    public static void main(String[] args)throws Exception{System.out.println("TyHostTests "+run()+" PASS");}
}
