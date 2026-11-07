package org.circus.core;

import java.io.*;
import java.util.*;

/** Distinguish1B's candidate and persist4 policy from26's suspended return. */
public final class Pc1SaveContextTests {
    private static int count;
    private static void check(boolean value,String why){count++;if(!value)throw new AssertionError(why);}
    private interface Action {void run()throws Exception;}
    private static void rejects(Action a,String message)throws Exception{boolean bad=false;try{a.run();}catch(IOException e){bad=message.isEmpty()||String.valueOf(e.getMessage()).contains(message);}check(bad,"Reject "+message);}
    static PcHost fixture(int flag)throws Exception{
        PcHost h=Pc1SystemContextTests.host("dcpc-2063");byte[] bytes={0,0,0,0,0x63,0x20,0x1b,(byte)flag,0,0x3b,'x',0,0x15,4,1,0,0,0};h.position(new MesScript("prepared",bytes,h.resources.profile),6);return h;
    }
    static byte[] save(Pc1SaveContext c)throws IOException{ByteArrayOutputStream out=new ByteArrayOutputStream();c.save(new DataOutputStream(out));return out.toByteArray();}
    public static int run()throws Exception{
        count=0;PcHost h=fixture(1);h.variables.write(0,0,41);h.variables.appendHistory();h.variables.write(0,0,42);h.variables.appendHistory();h.speaker="prepared speaker";h.body="prepared body";h.scene.slots[1]="prepared/background";h.ui.actions[0]="prepared/action";
        Dc2Ui.Quad pending=new Dc2Ui.Quad();pending.x=144;h.ui.quads.add(pending);h.write(90,4,61);h.logicalMusic(17);h.scene.slots[6]="prepared/overlay1";h.scene.slots[7]="prepared/overlay2";h.scene.slots[21]="live/resource";
        h.pc1.textLog.onWait07(1,1);h.pc1.textLog.onCheckpoint03(8);h.pc1.textLog.onCheckpoint04(9);int[] preparedLog=h.pc1.textLog.rawWords();h.pc1.cachedScenarioRead(5);h.pc1.headY(77);h.pc1.waitKind(7);h.pc1.argument0(0x12345678);h.pc1.argument1(-99);h.pc1.historySelection(2);h.checkpoint=999;
        Pc1SaveContext context=new Pc1SaveContext();context.prepare(h,6,0x123);Pc1Frame candidate=context.candidate();pending.x=999;
        check(context.flag()==1&&candidate.preparedBy1B(),"1B stores its control flag and independent frame");
        check(candidate.returnPoint().pc==9&&candidate.sourceOpcodeOffset()==6,"1B snapshot cursor is after short head and BEFORE owned string consumption");
        check(candidate.rawCompanionMarker()==0x3b&&Arrays.equals(candidate.rawOwnedString(),new byte[]{'x'}),"Candidate preserves raw companion identity without reinterpreting its label");
        check(candidate.returnPoint().sha256.equals(h.script.sha256)&&candidate.ordinaryBytes()[0]==42&&candidate.packedSoundRequest()==0x123,"Caller SHA/ordinary/packed SE are part of the typed candidate");
        h.variables.write(0,0,99);h.body="menu body";h.scene.slots[1]="menu/background";h.scene.slots[6]="menu/overlay1";h.scene.slots[7]="menu/overlay2";h.scene.slots[21]="menu/resource";h.audio[0]="menu/music";h.logicalMusic(3);h.write(100,1,77);h.write(90,4,88);h.ui.actions[2]="menu/confirmation";h.ui.palette[2]=123;h.scene.effectClock=444;
        h.pc1.textLog.clearLog();h.pc1.textLog.onCheckpoint03(1);h.pc1.cachedScenarioRead(0);h.pc1.headY(-1);h.pc1.waitKind(-1);h.pc1.argument0(3);h.pc1.argument1(4);h.pc1.historySelection(900);h.history.append("menu","entry","v","menu",6);h.history.active=true;h.history.selected=0;
        Pc1Frame current=Pc1Frame.capturePersistCurrent(h,12,0);
        check(current.returnPoint().pc==15&&!current.preparedBy1B()&&current.rawOwnedString().length==0,"Fresh persist4 frame points after persist short head without inventing an owned string");
        Pc1SaveContext.Selection selected=context.selectForPersist4(1,7,current);
        check(selected.slot==0&&selected.frame==candidate&&!context.needsCurrentFrame(1),"Nonzero BB8418 preserves earlier candidate even for slot0");
        check(context.selectForPersist4(0,7,current).slot==7&&context.selectForPersist4(0,7,current).frame==candidate,"Ordinary selected-slot save always uses existing candidate");
        candidate.apply(h);check(h.variables.read(0,0)==42,"Candidate load RESTORES captured ordinary, unlike26 system return");
        check(h.variables.historyCount()==2,"Candidate ordinary restore does not invent Java history replay/reset");
        check(h.body.equals("prepared body")&&h.scene.slots[1].equals("prepared/background")&&h.ui.actions[0].equals("prepared/action"),"Partial typed frame restores portable text adapter and reviewed graphics, not a completed native load");
        check(Arrays.equals(h.pc1.textLog.rawWords(),preparedLog)&&h.pc1.textLog.currentCheckpoint()==9&&candidate.checkpointIndex()==9&&h.checkpoint==999,"Candidate current checkpoint/raw log are native fields, independent of portable checkpoint adapter");
        check(h.pc1.cachedScenarioRead()==5&&h.pc1.headY()==77&&h.pc1.waitKind()==7&&h.pc1.argument0()==0x12345678&&h.pc1.argument1()==-99,"Candidate restores frame dispatch fields");
        check(h.pc1.historySelection()==900&&h.history.active&&h.history.selected==0&&h.history.entries.size()==1,"Candidate does not restore external selection or global portable history");
        check(h.ui.quads.size()==1&&h.ui.quads.get(0).x==144,"1B candidate keeps native frame pending batch, whereas1C explicitly clears it");
        check(h.read(90,4)==61&&h.ui.actions[2].equals("menu/confirmation"),"Candidate owns frame bank90 and action0/1, not external confirmation actions");
        check(h.audio[0].equals("menu/music")&&h.read(100,1)==77&&h.ui.palette[2]==123&&h.scene.effectClock==444,"Candidate does not load unrelated audio/persistent/palette/scheduler owners");
        check(h.logicalMusic()==17&&candidate.musicRequest()==17,"Candidate includes logical9D8A0C request separately from physical playback");
        check(h.scene.slots[6].equals("prepared/overlay1")&&h.scene.slots[7].equals("prepared/overlay2")&&h.scene.slots[21].equals("menu/resource"),"Frame owns overlay paths while named message resource handles stay live");
        byte[] copy=candidate.ordinaryBytes();copy[0]=0;check(candidate.ordinaryBytes()[0]==42,"Captured ordinary cannot be mutated through a getter");
        byte[] encoded=save(context);Pc1SaveContext decoded=new Pc1SaveContext();decoded.load(new DataInputStream(new ByteArrayInputStream(encoded)));check(Arrays.equals(encoded,save(decoded)),"Candidate and flag roundtrip independently of a mobile VM save");
        check(decoded.candidate().returnPoint().pc==9&&decoded.candidate().ordinaryBytes()[0]==42,"Roundtrip preserves pre-companion cursor and candidate ordinary");
        check(decoded.candidate().fields().currentCheckpoint()==9&&decoded.candidate().fields().headY==77,"P1F3 codec retains native fields independent of portable text cache");
        PcHost zero=fixture(0);zero.variables.write(0,0,5);Pc1SaveContext fresh=new Pc1SaveContext();fresh.prepare(zero,6,0);
        zero.variables.write(0,0,90);Pc1Frame atPersist=Pc1Frame.capturePersistCurrent(zero,12,0);
        check(fresh.needsCurrentFrame(1)&&!fresh.needsCurrentFrame(0),"Only flag0 plus persist4 argument1 requires current frame");
        check(fresh.selectForPersist4(0,9,atPersist).frame.ordinaryBytes()[0]==5,"Flag0 does not refresh ordinary selected-slot save");
        check(fresh.selectForPersist4(1,9,atPersist).frame.ordinaryBytes()[0]==90&&fresh.candidate().returnPoint().pc==15,"Flag0 slot0 uses freshly copied current frame");
        check(fresh.flag()==0,"persist4 does not silently reset BB8418");
        Pc1SaveContext empty=new Pc1SaveContext();rejects(()->empty.selectForPersist4(0,3,null),"not been prepared");
        rejects(()->empty.selectForPersist4(1,3,null),"requires current");
        rejects(()->empty.selectForPersist4(1,3,candidate),"requires current");
        check(empty.selectForPersist4(1,-1,atPersist).slot==0,"Slot0 is independent of selected native ID");
        rejects(()->context.selectForPersist4(0,-1,null),"save ID");rejects(()->context.selectForPersist4(256,0,null),"argument");
        rejects(()->Pc1Frame.capturePrepared(h,12,0),"opcode mismatch");rejects(()->Pc1Frame.capturePersistCurrent(h,6,0),"opcode mismatch");
        for(int n=0;n<encoded.length;n+=Math.max(1,encoded.length/11)){final byte[] shortData=Arrays.copyOf(encoded,n);rejects(()->new Pc1SaveContext().load(new DataInputStream(new ByteArrayInputStream(shortData))),"");}
        for(int flag:new int[]{0,1,2,255}){PcHost f=fixture(flag);Pc1SaveContext c=new Pc1SaveContext();c.prepare(f,6,0);check(c.flag()==flag,"Keep exact native u8 BB8418 flag");}
        PcHost lifeHost=fixture(1);Pc1SaveContext life=new Pc1SaveContext();life.prepare(lifeHost,6,0);Pc1Frame buffer=life.candidate();
        life.scriptLoaded(0);check(life.flag()==1&&life.candidate()==buffer,"System target/caller mode0 does not reset save-candidate owner");
        life.scriptLoaded(2);check(life.flag()==1&&life.candidate()==buffer,"Mode2 keeps flag and candidate");
        life.scriptLoaded(1);check(life.flag()==0&&life.candidate()==buffer,"Mode1 clears only flag, preserving candidate bytes");
        life.prepare(lifeHost,6,0);buffer=life.candidate();life.scriptLoaded(3);check(life.flag()==0&&life.candidate()==buffer,"Mode3 clears only flag");
        rejects(()->life.scriptLoaded(4),"loader mode");
        PcHost eventHost=fixture(0);eventHost.pc=6;eventHost.variables.write(0,0,37);Pc1SaveContext eventOwner=new Pc1SaveContext();
        check(eventOwner.captureNativeEvent(eventHost,eventHost.script,15,0x105),"Flag0 input event refreshes the candidate without requiring a persist or1B opcode at its cursor");
        Pc1Frame event=eventOwner.candidate();check(event.capturedFromEvent()&&!event.preparedBy1B()&&!event.capturedForPersistCurrent()&&event.returnPoint().pc==15&&event.sourceOpcodeOffset()==-1&&event.rawOwnedString().length==0,"Event frame records the actual VM cursor, not last dispatched host.pc");
        check(event.ordinaryBytes()[0]==37&&event.packedSoundRequest()==0x105,"Event captures ordinary and packed request at its own call point");
        byte[] eventBytes=save(eventOwner);Pc1SaveContext eventReload=new Pc1SaveContext();eventReload.load(new DataInputStream(new ByteArrayInputStream(eventBytes)));check(Arrays.equals(eventBytes,save(eventReload))&&eventReload.candidate().capturedFromEvent(),"Native event source kind survives candidate serialization");
        rejects(()->new Pc1SaveContext().selectForPersist4(1,0,event),"requires current");
        eventHost.variables.write(0,0,83);eventOwner.prepare(eventHost,6,0);Pc1Frame protectedCandidate=eventOwner.candidate();
        eventOwner.scriptLoaded(0);check(eventOwner.captureNativeEvent(eventHost,eventHost.script,12,0)&&eventOwner.candidate()!=protectedCandidate,"Flag0 refresh is independent of the preceding prepared candidate");
        PcHost heldHost=fixture(1);Pc1SaveContext held=new Pc1SaveContext();held.prepare(heldHost,6,0);Pc1Frame heldCandidate=held.candidate();check(!held.captureNativeEvent(heldHost,heldHost.script,15,0)&&held.candidate()==heldCandidate,"Flag1 input event preserves the prepared candidate");
        rejects(()->Pc1Frame.captureEvent(eventHost,eventHost.script,1000,0),"outside caller");
        rejects(()->Pc1Frame.captureEvent(eventHost,heldHost.script,15,0),"caller mismatch");
        byte[] oldSave=eventBytes.clone();oldSave[3]='1';rejects(()->new Pc1SaveContext().load(new DataInputStream(new ByteArrayInputStream(oldSave))),"save-context format");
        ByteArrayOutputStream rawFrame=new ByteArrayOutputStream();event.save(new DataOutputStream(rawFrame));byte[] oldFrame=rawFrame.toByteArray();oldFrame[3]='1';rejects(()->Pc1Frame.load(new DataInputStream(new ByteArrayInputStream(oldFrame))),"candidate frame format");
        Pc1SaveContext dated=new Pc1SaveContext();dated.prepare(lifeHost,6,0);Pc1SaveContext.Selection beforeDate=dated.selectForPersist4(0,7,null);byte[] original=beforeDate.frame.ordinaryBytes();
        Pc1Frame.SaveMetadata local=new Pc1Frame.SaveMetadata(2026,11,4,3,42);Pc1SaveContext.Selection written=dated.prepareWrite(beforeDate,6,9,local);byte[] stamped=written.frame.ordinaryBytes();
        check(written.slot==7&&written.frame==dated.candidate()&&written.frame.returnPoint().pc==9,"Save metadata updates selected candidate while keeping its native resume cursor/slot");
        check((stamped[902]&255)==6&&(stamped[920]&255)==9&&(stamped[921]&255)==11&&(stamped[922]&255)==4&&(stamped[923]&255)==3&&(stamped[924]&255)==42&&(stamped[925]&255)==26,"Exact404E70 current-state/time byte offsets");
        for(int n=0;n<1000;n++)if(n!=902&&(n<920||n>925))check(stamped[n]==original[n],"Save metadata cannot accidentally replace all ordinary with current state");
        check(beforeDate.frame!=written.frame&&beforeDate.frame.ordinaryBytes()[925]==stamped[925]&&beforeDate.frame.hasNativeFrame(dated.nativeCandidate()),"Earlier candidate metadata keeps the same native buffer alias after its bytes are stamped");
        rejects(()->dated.prepareWrite(beforeDate,1,2,local),"Stale");
        Pc1Frame wrapped=written.frame.withNativeSaveMetadata(6,9,new Pc1Frame.SaveMetadata(2256,1,1,0,0));check((wrapped.ordinaryBytes()[925]&255)==0,"Native year byte wraps exactly after year+30h");
        byte[] afterDate=save(dated);Pc1SaveContext reloaded=new Pc1SaveContext();reloaded.load(new DataInputStream(new ByteArrayInputStream(afterDate)));check(Arrays.equals(afterDate,save(reloaded)),"Updated candidate metadata survives independent context persistence before file I/O");
        rejects(()->new Pc1Frame.SaveMetadata(2026,0,4,3,42),"local save time");
        PcHost projected=Pc1SystemContextTests.host("dcpc-2063");byte[] projectionBytes={1,0,0,0,2,0,0,0,0x63,0x20,7,8,2,0,0,0};MesScript projectionScript=new MesScript("projection",projectionBytes,projected.resources.profile);projected.position(projectionScript,10);projected.pc1.textLog.onCheckpoint04(0);projected.checkpoint=999;projected.pc1.argument0(1234);projected.pc1.argument1(-4321);projected.logicalMusic(17);
        Pc1Frame projectedFrame=Pc1Frame.captureEvent(projected,projectionScript,13,0);Pc1SlotProjection projection=projectedFrame.projection(projectionScript,3);
        check(projection.checkpointIndex==0&&projection.checkpointCodePc==2&&projection.resumeFilePc==13&&projection.wait==Pc1SlotProjection.Wait.MESSAGE,"Projection uses raw-log current checkpoint and native table, not portable checkpoint adapter");
        check(projection.loadedFields.rawCodePc==5&&projection.loadedFields.argument0==1234&&projection.loadedFields.argument1==-4321&&projection.loadedFields.logicalMusic==17&&projection.logicalMusicDuringReconstruction==3,"Projection keeps saved raw code PC/args/music distinct from current reconstruction music and header peek args");
        rejects(()->projectedFrame.projection(new MesScript("other",projectionBytes,projected.resources.profile),3),"identity changed");byte[] changedProjection=projectionBytes.clone();changedProjection[11]=9;rejects(()->projectedFrame.projection(new MesScript("projection",changedProjection,projected.resources.profile),3),"identity changed");
        ByteArrayOutputStream latestFrame=new ByteArrayOutputStream();projectedFrame.save(new DataOutputStream(latestFrame));byte[] p1f2=latestFrame.toByteArray();p1f2[3]='2';rejects(()->Pc1Frame.load(new DataInputStream(new ByteArrayInputStream(p1f2))),"candidate frame format");
        return count;
    }
    public static void main(String[] args)throws Exception{System.out.println("PASS Pc1SaveContextTests "+run());}
}
