package org.circus.core;

import java.io.*;
import java.util.*;
import java.util.zip.*;

/** Host contracts from DC2FL 406D50/406E60/406E70/408E40/4220D0. */
public final class FlHostTests {
    private static int checks;
    private static void check(boolean ok,String why){checks++;if(!ok)throw new AssertionError(why);}
    private static CircusResources data()throws Exception {
        ByteArrayOutputStream image=new ByteArrayOutputStream();byte[] header=new byte[20];header[0]='C';header[1]='R';header[2]='X';header[3]='G';header[8]=(byte)0xec;header[9]=4;header[10]=1;header[12]=2;image.write(header);
        try(DeflaterOutputStream z=new DeflaterOutputStream(image)){z.write(0);z.write(new byte[1260*3]);}final byte[] crx=image.toByteArray();
        GameResources files=new GameResources(new GameResources.Storage(){
            public Set<String> paths(){return new HashSet<String>(Arrays.asList("AdvData/DAT/NAMES.DAT","AdvData/GRP/fifth.CRX","AdvData/GRP/actor.CRX"));}
            public byte[] read(String path,int limit){return path.toLowerCase(Locale.ROOT).endsWith(".crx")?crx:new byte[20];}
            public Archive.Source source(String path){return new Archive.Source(){public long size(){return 20;}public byte[] read(long at,int n)throws IOException{Binary.range(at,n,20);return new byte[n];}};}
        });return new CircusResources(files,Profiles.get("dc2fl-9c69"));
    }
    private static FlHost host()throws Exception{FlHost h=new FlHost(data(),TextCodec.identity());h.attach(new Variables(h,Variables.Model.PC_WIDE));return h;}
    private static byte[] bytes(FlHost h)throws IOException{ByteArrayOutputStream b=new ByteArrayOutputStream();h.save(new DataOutputStream(b));return b.toByteArray();}
    public static int run()throws Exception {
        checks=0;FlHost h=host();check(h.pc1==null&&h.second,"FL uses common PC2 geometry without constructing a PC1 guest");
        check(Dc2Host.create(data(),TextCodec.identity()) instanceof FlHost,"independent profile dispatches to FL owner");
        h.command("pc.command",new int[]{6,0},new byte[0]);check(h.saveGrid&&h.list.textStyle==2,"service6 save mode selects native renderer2");
        h.write(99,0,3);check(h.gridSlot(4)==19,"FL save lists contain5 entries per page");
        h.command("pc.command",new int[]{6,1},new byte[0]);check(!h.saveGrid,"service6 nonzero loads");
        h.command("pc.command",new int[]{7,99},new byte[0]);check(h.list.textStyle==4,"service7 ignores argument and selects scene renderer4");
        h.command("pc.command",new int[]{9,0},new byte[0]);check(h.list.textStyle==3,"service9 selects music renderer3");
        h.endingClock=54321;h.command("pc.command",new int[]{10,0},new byte[0]);check(h.endingClock==54321,"FL selector10 is an actual native default return");
        h.command("pc.command",new int[]{20,0},new byte[0]);check(!h.map.active,"FL does not accidentally inherit PC2 map selector20");
        check(h.command("pc.persist",new int[]{18,0},new byte[0]),"FL ADVMENU selector18 takes its independently reviewed native default return");
        int serial=h.scene.transitionSerial;h.command("pc.transition",new int[]{15,0},new byte[0]);check(h.scene.transitionSerial==serial+1&&h.remainingMillis()==0,"FL ADVMENU0D/15 composes without inventing a transition wait");
        FlHost compose=host();serial=compose.scene.transitionSerial;compose.command("pc.transition",new int[]{255,0},new byte[0]);check(!compose.flComposition.active&&compose.scene.transitionSerial==serial,"native255 with no pending resources really performs no present");
        UiCapture old=UiCapture.pixels(1,1,new int[]{0xff123456}),next=UiCapture.pixels(1,1,new int[]{0xffaabbcc});final List<String> masks=new ArrayList<String>();
        compose.write(99,12,0);compose.nativeFrameCapture=()->old;compose.compositionCapture=(mask,message,hidden)->{masks.add(mask+":"+message+":"+hidden);return next;};
        compose.command("pc.character",new int[]{0},"actor".getBytes(Binary.SJIS));compose.command("pc.transition",new int[]{255,0},new byte[0]);
        check(compose.flComposition.active&&compose.flComposition.phase==1&&compose.flComposition.initial==old&&masks.equals(Collections.singletonList("255:false:false")),"actor dirty255 captures old front and only final FF target");
        check(!compose.signal(compose.waitTicket(),MesVm.HostSignal.ADVANCE),"ADVANCE cannot skip native composition wait");
        compose.elapse(40);FlComposition.Draw event=compose.flComposition.draws().get(0);check(event.alpha==6&&event.left==0&&event.right==800&&event.top==0&&event.bottom==1,"native actor bbox clips old/new union to viewport");
        FlHost composed=host();composed.load(new DataInputStream(new ByteArrayInputStream(bytes(compose))));check(Arrays.equals(bytes(compose),bytes(composed)),"FLH2 roundtrips pending geometry and composition capture journal");
        for(int n=0;n<10&&compose.flComposition.active;n++)compose.elapse(40);check(!compose.flComposition.active&&compose.compositionVisible(),"255 releases but retains final physicalfront until next native present");compose.command("dm.ui.commit",new int[0],new byte[0]);check(!compose.compositionVisible(),"next actual commit replaces retained composition");
        compose=host();final List<String> preparedMasks=new ArrayList<String>();compose.nativeFrameCapture=()->old;compose.compositionCapture=(mask,message,hidden)->{preparedMasks.add(mask+":"+message+":"+hidden);return next;};compose.command("pc.background",new int[0],"actor".getBytes(Binary.SJIS));compose.command("pc.transition",new int[]{255,0},new byte[0]);
        check(compose.flComposition.active&&compose.flComposition.phase==0&&preparedMasks.equals(Arrays.asList("15:false:false","255:false:false")),"pending background255 has separate prepared0F and final FF capture masks");
        for(int transition:new int[]{0,13}){
            FlHost message=host();MesScript fixture=new MesScript("fixture",new byte[]{0,0,0,0,0x69,(byte)0x9c,0,0,0},Profiles.get("dc2fl-9c69"));message.position(fixture,fixture.firstOffset);
            message.command("text.body",new int[0],"retained message bytes".getBytes(Binary.SJIS));message.presentationWaiting=false;message.command("pc.transition",new int[]{transition,0},new byte[0]);message.elapse(100000);
            final List<Boolean> visible=new ArrayList<Boolean>();message.nativeFrameCapture=()->old;message.compositionCapture=(mask,text,hidden)->{visible.add(text);return next;};message.command("pc.character",new int[]{0},"actor".getBytes(Binary.SJIS));message.command("pc.transition",new int[]{255,0},new byte[0]);
            check(!message.body.isEmpty()&&visible.equals(Collections.singletonList(false)),"native0D/"+transition+" clears message-visible flag even though old body bytes remain");
        }
        FlHost mover=host();mover.command("fl.actor_position",new int[]{100,200,0,999},new byte[0]);mover.command("dm.ui.commit",new int[0],new byte[0]);
        check(mover.scene.character(0,0)==100&&mover.scene.shown.actors[0][0]==0,"64 stage does not become live during generic publish");
        mover.command("pc.character",new int[]{0},"actor".getBytes(Binary.SJIS));check(mover.liveActors[0][0]==59&&mover.scene.shown.actors[0][0]==0,"1260px loaded handle applies -41 while retaining previous shown front");
        mover.command("dm.ui.commit",new int[0],new byte[0]);check(mover.scene.shown.actors[0][0]==59,"actual compose hook commits loaded live x instead of stage100");
        mover.command("fl.actor_transform",new int[]{200,220,777,0},new byte[0]);check(mover.flActorMotion.targetX==159&&mover.scene.character(0,0)==100&&mover.liveActors[0][2]==100,"65 adds handle-width target correction, ignores arg2 and leaves stage unchanged");
        mover.write(99,12,0);mover.elapse(1);check(mover.scene.shown.actors[0][0]==67&&mover.scene.shown.actors[0][1]==208,"FL65 actual1ms wait moves8 peraxis");
        for(int n=0;n<20&&mover.flActorMotion.active;n++)mover.elapse(1);mover.command("dm.ui.commit",new int[0],new byte[0]);check(!mover.flActorMotion.active&&mover.scene.shown.actors[0][0]==159&&mover.scene.character(0,0)==100,"future compose preserves completed live coordinates");
        mover.captureUi();check(new SceneCaptures.Legacy(mover.uiCapture.input()).frame.actors[0][0]==159,"captureUi clones projected live frame inside its own publish");
        FlHost prezoom=host();prezoom.command("fl.actor_transform",new int[]{25,0,0,16},new byte[0]);prezoom.command("pc.character",new int[]{0},"actor".getBytes(Binary.SJIS));prezoom.command("fl.actor_transform",new int[]{100,0,-123,0},new byte[0]);
        check(prezoom.liveActors[0][0]==0&&prezoom.flActorMotion.targetX==100,"prezoomed handle315px avoids incorrect raw1260px -41 compensation");
        prezoom=host();prezoom.motionSkipPressed=true;prezoom.command("fl.actor_transform",new int[]{25,0,0,16},new byte[0]);prezoom.command("pc.character",new int[]{0},"actor".getBytes(Binary.SJIS));check(prezoom.liveActors[0][0]==-41,"Ctrl bypasses prezoom and retains raw handle width for -41");
        prezoom=host();prezoom.motionSkipPressed=true;prezoom.write(99,25,1);prezoom.command("fl.actor_transform",new int[]{25,0,0,16},new byte[0]);prezoom.command("pc.character",new int[]{0},"actor".getBytes(Binary.SJIS));check(prezoom.liveActors[0][0]==0,"99:25 nonzero suppresses Ctrl helper and permits native prezoom");
        h.command("pc.character",new int[]{4},"fifth".getBytes(Binary.SJIS));h.fifthCoordinates[0]=10;h.fifthCoordinates[1]=-7;h.fifthCoordinates[2]=75;h.fifthCoordinates[3]=123;
        h.enterSystem();h.fifthCharacter="changed";Arrays.fill(h.fifthCoordinates,9);h.command("pc.character",new int[]{4},"--".getBytes(Binary.SJIS));
        check(h.fifthCharacter.equals("fifth")&&h.fifthCoordinates[0]==10&&h.fifthCoordinates[1]==-7&&h.fifthCoordinates[2]==75&&h.fifthCoordinates[3]==9&&h.fifthDrawXOffset()==1,"fifth -- restores owner and only native first3 coordinate fields");
        h.scene.slots[2]="actor";h.scene.character(0,new int[]{4,5,80,81,82,83});h.scene.publish(h.ui,0,0);h.enterSystem();h.scene.character(0,new int[]{9,9,9,91,92,93});h.command("pc.character",new int[]{0},"--".getBytes(Binary.SJIS));
        check(Arrays.equals(h.scene.character(0),new int[]{4,5,80,91,92,93}),"ordinary -- also preserves un-restored transform fields");
        h.command("pc.character",new int[]{4},"-".getBytes(Binary.SJIS));check(h.fifthCharacter.isEmpty(),"fifth minus clears its independent owner");
        FlHost blank=host();blank.fifthCharacter="live";blank.fifthCoordinates[0]=500;blank.command("pc.character",new int[]{4},"--".getBytes(Binary.SJIS));check(blank.fifthCharacter.isEmpty()&&blank.fifthCoordinates[0]==0&&blank.fifthCoordinates[2]==100,"initial saved owner is a valid empty native BSS record");
        h.variables.write(3,50,1);h.write(100,52,0);h.write(100,53,0);h.command("pc.command",new int[]{11,0},new byte[0]);
        check(h.flCredits.active&&h.flCredits.variant==0&&!h.pcCredits.active&&h.waiting()&&h.flCreditsUnderlay!=null,"service11 mode0 starts independent modal GED instead of stopping PC2 credits");
        check(!h.signal(h.waitTicket(),MesVm.HostSignal.ADVANCE)&&!h.endingInput(2)&&h.flCredits.active,"first-clear ADVANCE and rightclick cannot skip");
        byte[] snapshot=bytes(h);FlHost copy=host();copy.load(new DataInputStream(new ByteArrayInputStream(snapshot)));check(Arrays.equals(snapshot,bytes(copy)),"FL typed tail roundtrips credits, timer, capture and fifth owner");
        h.elapse(3001);h.elapse(200000);check(!h.flCredits.active&&h.read(100,53)==1&&h.read(100,52)==0,"natural GED completion writes exact flag53");
        h.command("pc.command",new int[]{11,0},new byte[0]);h.logicalMusic(5);h.audio[0]="music";h.audioActive[0]=true;check(h.endingInput(2)&&!h.flCredits.active&&h.logicalMusic()==-1&&!h.audioActive[0],"replay rightclick releases owner and stops music");
        h.command("pc.command",new int[]{11,1},new byte[0]);check(h.flCredits.active&&h.flCredits.variant==1&&!h.endingInput(2),"GED replay flag53 does not permit slideshow skip");h.write(100,52,1);check(h.endingInput(2)&&h.read(100,52)==1,"slide replay flag52 permits skip without rewriting flag");
        h.command("dm.ui.commit",new int[0],new byte[0]);check(!h.endingVisible(),"actual dm.ui.commit releases retained ending frame");
        return checks;
    }
    public static void main(String[] args)throws Exception{System.out.println("FlHostTests "+run()+" PASS");}
}
