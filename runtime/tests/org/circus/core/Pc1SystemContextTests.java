package org.circus.core;

import java.io.*;
import java.util.*;

/** Owner separation and cursor preservation; no original executable or game text. */
public final class Pc1SystemContextTests {
    private static int count;
    private static void check(boolean ok,String why){count++;if(!ok)throw new AssertionError(why);}
    private interface Action {void run()throws Exception;}
    private static void rejects(Action action,String detail)throws Exception{
        boolean failed=false;try{action.run();}catch(IOException e){failed=detail.isEmpty()||String.valueOf(e.getMessage()).contains(detail);}check(failed,"Reject "+detail);
    }
    static PcHost host(String id)throws IOException{
        final byte[] names=new byte[20];GameResources files=new GameResources(new GameResources.Storage(){
            public Set<String> paths(){return new HashSet<>(Arrays.asList("AdvData/DAT/NAMES.DAT","AdvData/GRP/NAMES.DAT"));}
            public byte[] read(String p,int limit)throws IOException{if(limit<names.length)throw new IOException("Fixture limit");return names.clone();}
            public Archive.Source source(String p){return new Archive.Source(){public long size(){return names.length;}public byte[] read(long p,int n)throws IOException{Binary.range(p,n,names.length);return Arrays.copyOfRange(names,(int)p,(int)p+n);}};}
        });PcHost h=Pc1TestDefaults.bind(new PcHost(new CircusResources(files,Profiles.get(id)),TextCodec.identity()));h.attach(new Variables(h,h.resources.profile.variableModel));
        byte[] script=new byte[32];script[4]=(byte)h.resources.profile.version;script[5]=(byte)(h.resources.profile.version>>>8);h.position(new MesScript("caller",script,h.resources.profile),6);return h;
    }
    static byte[] save(Pc1SystemContext context)throws IOException{ByteArrayOutputStream b=new ByteArrayOutputStream();context.save(new DataOutputStream(b));return b.toByteArray();}
    private static byte[] saveFields(Pc1FrameFields fields)throws IOException{ByteArrayOutputStream b=new ByteArrayOutputStream();fields.save(new DataOutputStream(b));return b.toByteArray();}
    private static byte[] vars(Variables v)throws IOException{ByteArrayOutputStream b=new ByteArrayOutputStream();v.save(new DataOutputStream(b));return b.toByteArray();}
    static UiCapture framebuffer()throws IOException{return UiCapture.pixels(2,2,new int[]{0xff000001,0xff000002,0xff000003,0xff000004});}
    public static int run()throws Exception{
        count=0;PcHost h=host("dcpc-2063");byte[] target="advmenu".getBytes(Binary.SJIS);UiCapture pixels=framebuffer();
        for(int n=0;n<28;n++)h.scene.slots[n]="caller/slot"+n;
        h.scene.backgroundX(-7);h.scene.backgroundY(13);for(int n=0;n<2;n++)for(int j=0;j<6;j++)h.scene.character(n,j,n*10+j);
        h.scene.publish(h.ui,0,0);h.speaker="caller speaker";h.body="caller body";h.lineVoice="caller/voice";h.checkpointLabel="caller-checkpoint";h.checkpoint=14;
        h.messageMode(2);h.inputMode=3;h.waitIndicatorStyle=4;h.speakerPending=true;h.textRendered=true;
        h.write(90,5,44);h.logicalMusic(17);
        ScriptList.Item callerItem=new ScriptList.Item();callerItem.id=19;callerItem.action="caller/item";h.list.items.add(callerItem);h.list.columns=2;h.list.hovered=3;
        Dc2Ui.Button callerRect=new Dc2Ui.Button();callerRect.left=23;h.grid.add(callerRect);h.gridBounds[0]=37;h.write(99,0,2);h.listOffset(12);h.pc1.nativeUi().writeInt(0x4305fc,22);h.write(99,254,19);
        for(int n=0;n<4;n++)h.ui.actions[n]="caller/action"+n;
        h.pc1.textLog.onWait07(1,1);h.pc1.textLog.onCheckpoint03(7);h.pc1.textLog.onCheckpoint03(9);int[] callerLog=h.pc1.textLog.rawWords();
        h.pc1.cachedScenarioRead(7);h.pc1.headY(439);h.pc1.waitKind(7);h.pc1.argument0(0x12345678);h.pc1.argument1(Integer.MIN_VALUE);h.pc1.historySelection(1);
        Pc1SystemContext context=Pc1SystemContext.capture(h,"caller",9,0,target,pixels,0x125);target[0]='X';
        check(new String(context.rawTarget(),Binary.SJIS).equals("advmenu"),"Owned target is immutable and independent of caller byte array");
        byte[] returned=context.rawTarget();returned[1]='X';check(context.rawTarget()[1]=='d',"Raw target getter cannot mutate context");
        check(context.returnPoint().script.equals("caller")&&context.returnPoint().pc==9&&context.returnPoint().sha256.equals(h.script.sha256),"Consumed cursor includes exact caller SHA");
        check(context.restartsLoopingSound()&&context.loopingSoundIndex()==37,"Loop SE request is an explicit separate return action");
        context.applyEntry(h);check(h.body.isEmpty()&&!h.textWaiting&&!h.ui.waiting,"Entry releases current display wait without a VM restore");
        check(h.scene.slots[1].isEmpty()&&h.scene.slots[2].isEmpty()&&h.scene.slots[3].isEmpty(),"Mode0 releases only active background and actor1/2");
        check(h.scene.slots[18].equals("caller/slot18"),"Entry does not invent native portrait release");
        check(h.pc1.textLog.count()==2&&h.pc1.historySelection()==1,"Native mode0 entry retains raw log and external selection");
        h.pc1.textLog.clearLog();h.pc1.textLog.onCheckpoint03(12);h.pc1.cachedScenarioRead(1);h.pc1.headY(-1);h.pc1.waitKind(10);h.pc1.argument0(-7);h.pc1.argument1(8);h.pc1.historySelection(999);
        h.history.append("menu","portable history","voice","menu",6);h.history.active=true;h.history.selected=0;
        h.variables.write(0,0,3);h.variables.appendHistory();h.variables.write(0,0,8);h.variables.appendHistory();
        for(int b=0;b<10;b++)for(int n=0;n<100;n++)h.variables.write(b,n,(b*17+n)&255);
        byte[] live=vars(h.variables);Variables identity=h.variables;
        h.write(100,4,81);h.write(99,7,12);h.readCheckpoints.add("menu:progress");h.setNativeName(Pc1NativeSettings.Name.FAMILY,"AB".getBytes(Binary.SJIS));
        h.write(90,5,99);h.ui.actions[2]="menu/confirmation";
        h.audio[0]="menu/music";h.audio[1]="menu/click";h.audio[5]="menu/voice";h.audioActive[0]=h.audioActive[1]=h.audioActive[5]=true;
        h.audioGeneration[0]=30;h.audioGeneration[1]=31;h.audioGeneration[5]=35;
        h.logicalMusic(3);
        for(int n=0;n<28;n++)h.scene.slots[n]="menu/slot"+n;
        h.scene.backgroundX(70);h.scene.backgroundY(80);h.scene.effectClock=12345;h.scene.particleKind=7;
        h.scene.character(0,0,99);h.scene.character(1,5,88);h.scene.character(2,0,77);
        h.ui.palette[5]=0xff010203;h.ui.quadTemplates.get(1).add(new Dc2Ui.Quad());h.ui.buttonTemplates.get(1).add(new Dc2Ui.Button());
        h.ui.actions[0]="menu/action";h.ui.actions[4]="menu/shared-action";h.ui.waiting=true;h.ui.buttons.add(new Dc2Ui.Button());
        h.list.clear();h.grid.clear();h.gridBounds[0]=500;h.write(99,0,6);h.listOffset(40);h.pc1.nativeUi().writeInt(0x4305fc,55);h.write(99,254,90);
        h.ui.quads.add(new Dc2Ui.Quad());long beforeTicket=h.waitTicket();
        byte[] encoded=save(context);Pc1SystemContext restored=Pc1SystemContext.load(new DataInputStream(new ByteArrayInputStream(encoded)));
        check(Arrays.equals(encoded,save(restored)),"Context serializes independently and restores byte for byte");
        check(Arrays.equals(restored.framebuffer().pixels().argb,pixels.pixels().argb),"Complete frozen pixel framebuffer survives context persistence");
        Pc1SystemContext.ReturnPoint point=restored.applyReturn(h);
        check(point.pc==9&&point.sha256.equals(h.script.sha256),"Return cursor and content identity survive independently of host state");
        check(h.variables==identity&&Arrays.equals(live,vars(h.variables)),"Return touches neither current ordinary bytes nor Variables shadow/history log");
        for(int b=0;b<10;b++)for(int n=0;n<100;n++)check(h.variables.read(b,n)==((b*17+n)&255),"Menu ordinary byte survives return");
        check(h.read(100,4)==81&&h.read(99,7)==12&&h.readCheckpoints.contains("menu:progress")&&Arrays.equals(h.nativeFamilyName(),"AB".getBytes(Binary.SJIS)),"Persistence/preferences/read progress/player name retain their live owner");
        check(h.audio[0].equals("menu/music")&&h.audio[1].equals("menu/click")&&h.audio[5].equals("menu/voice")&&h.audioGeneration[0]==30&&h.audioGeneration[1]==31&&h.audioGeneration[5]==35,"Helper does not restore audio arrays or replay saved SE itself");
        check(h.logicalMusic()==17&&restored.musicRequest()==17,"Native logical BGM request returns with the frame while physical menu BGM remains live");
        h.command("dm.audio.music",new int[]{17,1},new byte[0]);
        check(h.audio[0].equals("menu/music")&&h.audioGeneration[0]==30,"Mode1 same-request reuse consults restored logical BGM field, without replaying physical caller music");
        for(int n:new int[]{1,2,3,6,7,10,11,12})check(h.scene.slots[n].equals("caller/slot"+n),"Reviewed graphics path owner returns, including rebuilt overlays");
        for(int n:new int[]{4,18,19,21,22,23})check(h.scene.slots[n].equals("menu/slot"+n),"Unowned graphic surface is not guessed to belong to caller");
        check(h.scene.backgroundX()==-7&&h.scene.backgroundY()==13&&h.scene.character(0,0)==0&&h.scene.character(1,1)==11,"Caller graphics coordinates restored");
        check(h.scene.character(1,5)==88,"Unmapped actor transform attribute is not silently declared caller-owned");
        check(h.scene.character(2,0)==77&&h.scene.effectClock==12345&&h.scene.particleKind==7,"Unowned actors and global effect/scheduler owners stay live");
        check(h.speaker.equals("caller speaker")&&h.body.equals("caller body")&&h.lineVoice.equals("caller/voice")&&h.checkpoint==14,"Portable display adapter is staged; production return must overwrite it by live raw-log replay");
        check(h.pc1.textLog.count()==2&&h.pc1.textLog.currentCheckpoint()==9&&Arrays.equals(h.pc1.textLog.rawWords(),callerLog),"System frame restores the full native raw log independently of live ordinary variables");
        check(h.pc1.cachedScenarioRead()==7&&h.pc1.headY()==439&&h.pc1.waitKind()==7&&h.pc1.argument0()==0x12345678&&h.pc1.argument1()==Integer.MIN_VALUE&&h.pc1.historySelection()==1,"Frame dispatch fields and separately saved external selection return exactly");
        check(h.history.active&&h.history.selected==0&&h.history.entries.size()==1&&h.history.entries.get(0).body.equals("portable history"),"Global portable history remains live, outside native frame owners");
        Pc1TextLog exposedLog=restored.fields().textLog();exposedLog.clearLog();check(restored.fields().textLog().count()==2,"Immutable frame fields cannot expose a mutable captured raw log");
        check(h.ui.quads.isEmpty()&&h.ui.buttons.isEmpty()&&!h.ui.waiting,"Native return clears pending draw registry and system controls");
        check(h.ui.actions[0].equals("caller/action0")&&h.ui.actions[2].equals("menu/confirmation")&&h.ui.actions[4].equals("menu/shared-action"),"Only action0/1 are inside the copied frame");
        check(h.read(90,5)==44,"Caller bank90 is restored separately from the live ordinary1000 bytes");
        check(h.ui.palette[5]==0xff010203&&h.ui.quadTemplates.get(1).size()==1&&h.ui.buttonTemplates.get(1).size()==1,"Global palette and shared template stores are not rolled back");
        check(h.list.items.size()==1&&h.list.items.get(0).id==19&&h.list.items.get(0).action.equals("caller/item")&&h.list.columns==2&&h.list.hovered==3,"Native list entries/page state have their own caller owner");
        check(h.grid.size()==1&&h.grid.get(0).left==23&&h.gridBounds[0]==37&&h.read(99,253)==22&&h.read(99,254)==19,"Native list rectangles/bounds/selection restored independently of active buttons");
        check(h.read(99,0)==6&&h.listOffset()==12&&h.read(99,246)==0,"Native list offset owner restores independently of global0 and script246");
        check(h.waitTicket()>beforeTicket,"Old system input events cannot target resumed caller ticket");
        rejects(()->Pc1SystemContext.capture(h,"caller",9,1,"advmenu".getBytes(Binary.SJIS),pixels,0),"nonzero");
        rejects(()->Pc1SystemContext.capture(h,"caller",9,0,"advmenu".getBytes(Binary.SJIS),null,0),"framebuffer");
        UiCapture logical=SceneCaptures.legacy(h.scene.shown,h.scene,h.ui,h.variables,true,null);
        rejects(()->Pc1SystemContext.capture(h,"caller",9,0,"advmenu".getBytes(Binary.SJIS),logical,0),"pixel framebuffer");
        rejects(()->Pc1SystemContext.capture(h,"other",9,0,"advmenu".getBytes(Binary.SJIS),pixels,0),"does not match");
        rejects(()->Pc1SystemContext.capture(h,"caller",1000,0,"advmenu".getBytes(Binary.SJIS),pixels,0),"outside");
        rejects(()->Pc1SystemContext.capture(h,"caller",9,0,new byte[]{'a',0,'b'},pixels,0),"NUL");
        rejects(()->Pc1SystemContext.capture(h,"caller",9,0,"../unsafe".getBytes(Binary.SJIS),pixels,0),"path segment");
        check(Pc1SystemContext.capture(h,"caller",9,0,"advmenu".getBytes(Binary.SJIS),pixels,-1).packedSoundRequest()==-1,"Frame sound request preserves all32 bits admitted by native byte aliases");
        PcHost other=host("dc2pc-5769");rejects(()->Pc1SystemContext.capture(other,"caller",9,0,"advmenu".getBytes(Binary.SJIS),pixels,0),"requires dcpc-2063");
        PcHost capacity=host("dcpc-2063");for(int n=0;n<220;n++)capacity.list.items.add(new ScriptList.Item());for(int n=0;n<20;n++)capacity.grid.add(new Dc2Ui.Button());
        check(Pc1SystemContext.capture(capacity,"caller",9,0,"advmenu".getBytes(Binary.SJIS),pixels,0)!=null,"Exact native220-item/20-rectangle caller tables are accepted");
        capacity.list.items.add(new ScriptList.Item());rejects(()->Pc1SystemContext.capture(capacity,"caller",9,0,"advmenu".getBytes(Binary.SJIS),pixels,0),"list-page capacity");
        capacity.list.items.remove(220);capacity.grid.add(new Dc2Ui.Button());rejects(()->Pc1SystemContext.capture(capacity,"caller",9,0,"advmenu".getBytes(Binary.SJIS),pixels,0),"list-page capacity");
        PcHost backlog=host("dcpc-2063");backlog.history.append("speaker1","previous","voice1","caller",6);backlog.history.append("speaker2","current","voice2","caller",9);backlog.history.active=true;backlog.history.selected=0;
        backlog.pc1.textLog.onCheckpoint03(4);backlog.pc1.historySelection(0);
        Pc1SystemContext fromHistory=Pc1SystemContext.capture(backlog,"caller",9,0,"advmenu".getBytes(Binary.SJIS),pixels,0);
        fromHistory.applyEntry(backlog);check(backlog.history.active&&backlog.history.selected==0&&backlog.pc1.historySelection()==0&&backlog.pc1.textLog.count()==1,"Entry does not clear global portable history, native raw log or external selection");
        backlog.history.cursor=1;backlog.history.previewEnabled=false;backlog.history.rewindRequest=42;backlog.history.entries.get(1).scene=new byte[]{5};backlog.history.entries.get(1).resume=new byte[]{8};backlog.history.entries.get(0).body="changed";backlog.history.append("menu","new entry","v","menu",7);backlog.history.active=false;backlog.history.selected=2;
        backlog.pc1.historySelection(300);backlog.pc1.textLog.clearLog();
        Pc1SystemContext historyDecoded=Pc1SystemContext.load(new DataInputStream(new ByteArrayInputStream(save(fromHistory))));historyDecoded.applyReturn(backlog);
        check(backlog.pc1.historySelection()==0&&backlog.pc1.textLog.count()==1&&backlog.pc1.textLog.currentCheckpoint()==4,"Return restores raw log and external selection without inspecting portable Entry identities");
        check(!backlog.history.active&&backlog.history.selected==2&&backlog.history.entries.size()==3&&backlog.history.entries.get(0).body.equals("changed")&&backlog.history.cursor==1&&!backlog.history.previewEnabled&&backlog.history.rewindRequest==42&&backlog.history.entries.get(1).scene[0]==5&&backlog.history.entries.get(1).resume[0]==8,"Portable history contents, selection and adapter state remain live without anchor rejection");
        PcHost normal=host("dcpc-2063");normal.pc1.historySelection(9999);Pc1SystemContext fromNormal=Pc1SystemContext.capture(normal,"caller",9,0,"advmenu".getBytes(Binary.SJIS),pixels,0);normal.pc1.historySelection(-1);Pc1SystemContext.load(new DataInputStream(new ByteArrayInputStream(save(fromNormal)))).applyReturn(normal);
        check(normal.pc1.historySelection()==9999&&normal.pc1.textLog.count()==0,"Retained external selection is not prematurely validated against mode0 borrowed log/table");
        byte[] fields=saveFields(restored.fields());Pc1FrameFields decodedFields=Pc1FrameFields.load(new DataInputStream(new ByteArrayInputStream(fields)));check(Arrays.equals(fields,saveFields(decodedFields)),"Dispatch/log fields have their own stable codec");
        byte[] oldFields=fields.clone();oldFields[3]='0';rejects(()->Pc1FrameFields.load(new DataInputStream(new ByteArrayInputStream(oldFields))),"fields format");rejects(()->Pc1FrameFields.load(new DataInputStream(new ByteArrayInputStream(Arrays.copyOf(fields,fields.length-1)))),"");
        PcHost invalidFields=host("dcpc-2063");invalidFields.pc1.cachedScenarioRead(0x12340100);check(Pc1FrameFields.capture(invalidFields).cachedScenarioRead()==0x12340100,"Cached read is a complete nativeDWORD, preserving byte aliases above255");rejects(()->Pc1FrameFields.capture(other),"require dcpc-2063");
        for(int version:new int[]{'1','2','3','4'}){byte[] oldFormat=encoded.clone();oldFormat[3]=(byte)version;rejects(()->Pc1SystemContext.load(new DataInputStream(new ByteArrayInputStream(oldFormat))),"context format");}
        for(int n=0;n<encoded.length;n+=Math.max(1,encoded.length/13)){final byte[] shortData=Arrays.copyOf(encoded,n);rejects(()->Pc1SystemContext.load(new DataInputStream(new ByteArrayInputStream(shortData))),"");}
        return count;
    }
    public static void main(String[] args)throws Exception{System.out.println("PASS Pc1SystemContextTests "+run());}
}
