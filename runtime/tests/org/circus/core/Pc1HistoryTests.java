package org.circus.core;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Actual VM03/K/L/07/09 producers and typed1C over self-authored raw MES.
 * History never rewinds Variables or PC. Raster capture is a synthetic fixture. */
public final class Pc1HistoryTests {
    private static int count;
    private static void check(boolean value,String why){count++;if(!value)throw new AssertionError(why);}
    private static byte[] join(byte[]...parts)throws IOException{ByteArrayOutputStream bytes=new ByteArrayOutputStream();for(byte[] part:parts)bytes.write(part);return bytes.toByteArray();}
    private static void little(DataOutput out,int value)throws IOException{for(int n=0;n<4;n++)out.writeByte(value>>>(8*n));}
    private static byte[] text(int opcode,String text)throws IOException{byte[] bytes=text.getBytes(StandardCharsets.US_ASCII);if(opcode==0x4b||opcode==0x4c||opcode==0x4d)for(int n=0;n<bytes.length;n++)bytes[n]=(byte)((bytes[n]&255)-32);return join(new byte[]{(byte)opcode},bytes,new byte[1]);}
    private static byte[] words(int opcode,int...args){byte[] result=new byte[1+args.length*2];result[0]=(byte)opcode;for(int n=0;n<args.length;n++){result[n*2+1]=(byte)args[n];result[n*2+2]=(byte)(args[n]>>>8);}return result;}
    private static byte[] mes(int[] offsets,byte[] code)throws IOException{ByteArrayOutputStream bytes=new ByteArrayOutputStream();DataOutputStream out=new DataOutputStream(bytes);little(out,offsets.length);for(int offset:offsets)little(out,offset);out.write(new byte[]{0x63,0x20});out.write(code);out.write(new byte[3]);return bytes.toByteArray();}
    private static byte[] button(int x,int y,String action)throws IOException{return join(words(0x4f,x,y,x+20,y+20),text(0x32,action));}
    private static byte[] templates(String toolbar0)throws IOException{return join(button(0,0,toolbar0),button(22,0,":d"),new byte[]{0x17,1,0},button(0,30,""),button(0,60,"prefab1"),button(0,90,"prefab2"),new byte[]{0x17,3,0});}
    private static byte[] story(boolean choices,String toolbar0,String firstChoice,int nativeWait)throws IOException{
        ByteArrayOutputStream code=new ByteArrayOutputStream();code.write(templates(toolbar0));int[] offsets=new int[3];String[] captions={"OLD$0,0","MID$0,0","CUR$0,0"};
        for(int n=0;n<3;n++){code.write(new byte[]{3,(byte)n,0});offsets[n]=code.size()+2;code.write(text(0x35,"voice"+n));code.write(text(0x4b,"$n"));code.write(text(0x4c,captions[n]));code.write(new byte[]{7,0,0});}
        if(choices)code.write(join(new byte[]{8,1,0},text(0x45,firstChoice),text(0x4d,"ChoiceA"),text(0x45,"pickB"),text(0x4d,"ChoiceB"),new byte[]{(byte)nativeWait,0,0}));code.write(words(0x58,0,1,99,0));code.write(text(0x32,"pickA"));code.write(words(0x58,0,2,11,0));code.write(text(0x3b,"done"));code.write(text(0x32,"pickB"));code.write(words(0x58,0,2,22,0));code.write(text(0x32,"done"));return mes(offsets,code.toByteArray());
    }
    static final class Fixture {
        final Map<String,byte[]> files=new HashMap<>();final CircusResources resources;final String toolbar0,firstChoice;
        Fixture(boolean choices)throws IOException{this(choices,false);}
        Fixture(boolean choices,boolean overwriteStory)throws IOException{this(choices,overwriteStory,":u","pickA");}
        Fixture(boolean choices,boolean overwriteStory,String toolbar0,String firstChoice)throws IOException{this(choices,overwriteStory,toolbar0,firstChoice,9);}
        Fixture(boolean choices,boolean overwriteStory,String toolbar0,String firstChoice,int nativeWait)throws IOException{
            this.toolbar0=toolbar0;this.firstChoice=firstChoice;files.put("AdvData/DAT/NAMES.DAT",new byte[20]);files.put("AdvData/MES/START.mes",story(choices,toolbar0,firstChoice,nativeWait));files.put("AdvData/MES/menu.mes",mes(new int[0],join(new byte[]{0x17,0,0},button(0,0,"menuOnly"),overwriteStory?new byte[]{0x17,1,0}:new byte[0],words(0x58,0,0,42,0),text(0x4c,"MENUBODY"),new byte[]{0x1c,0,0})));
            GameResources input=new GameResources(new GameResources.Storage(){
                public Set<String> paths(){return files.keySet();}
                public byte[] read(String name,int limit)throws IOException{byte[] data=files.get(name);if(data==null)throw new FileNotFoundException(name);if(data.length>limit)throw new IOException("Fixture limit");return data.clone();}
                public Archive.Source source(String name)throws IOException{final byte[] data=read(name,Integer.MAX_VALUE);return new Archive.Source(){public long size(){return data.length;}public byte[] read(long offset,int length)throws IOException{Binary.range(offset,length,data.length);return Arrays.copyOfRange(data,(int)offset,(int)offset+length);}};}
            });resources=new CircusResources(input,Profiles.get("dcpc-2063"));
        }
        PcHost host()throws IOException{PcHost host=Pc1TestDefaults.bind(new PcHost(resources,TextCodec.identity()));host.setNativeNames("F","N");host.write(99,5,1);host.nativeFrameCapture=()->UiCapture.pixels(2,1,new int[]{0xff010203,0xff040506});return host;}
        MesVm vm(PcHost host)throws IOException{return new MesVm(resources.scripts(),host,"START");}
    }
    private static byte[] variables(Variables variables)throws IOException{ByteArrayOutputStream bytes=new ByteArrayOutputStream();variables.save(new DataOutputStream(bytes));return bytes.toByteArray();}
    private static byte[] journal(Pc1Runtime runtime)throws IOException{ByteArrayOutputStream bytes=new ByteArrayOutputStream();if(runtime.textLayout()!=null)runtime.textLayout().save(new DataOutputStream(bytes));return bytes.toByteArray();}
    private static byte[] buffers(Pc1Runtime runtime)throws IOException{ByteArrayOutputStream bytes=new ByteArrayOutputStream();runtime.textBuffers().save(new DataOutputStream(bytes));return bytes.toByteArray();}
    private static void completeReturn(MesVm vm,PcHost host)throws Exception{
        for(int n=0;host.pc1.returning();n++){if(n>=16)throw new AssertionError("Native1C return exceeded16 feedback frames");vm.elapse(40);}
        check(!host.pc1.inSystem(),"Native1C suspended owner survives until final40ms commit only");
    }
    private static void drive(Fixture fixture,PcHost host,MesVm vm,boolean choices)throws Exception{
        vm.variables.write(0,0,7);String[] prefixes={"OLD","MID","CUR"};
        for(int n=0;n<3;n++){
            check(vm.run(100)==MesVm.Status.WAIT_HOST&&host.body.equals(prefixes[n]+"7")&&host.pc1.waitKind()==0x4c,"real03/K/L creates current live presentation "+n);
            check(host.pc1.textLog().count()==n+1&&host.pc1.textLog().rawEntry(n)==n,"actual03 appends the native header ID, never an expanded prose entry");
            check(vm.signalHost(host.waitTicket(),MesVm.HostSignal.TEXT_COMPLETE),"presentation completion is separate from native07 advance");
            check(vm.run(1)==MesVm.Status.WAIT_HOST&&host.pc1.waitKind()==7&&host.textWaiting,"real07 has a preceding4C yield and enters nativewait7");
            if(n<2)check(vm.signalHost(host.waitTicket(),MesVm.HostSignal.ADVANCE),"advance releases only the current message wait");
        }
        if(choices){check(vm.signalHost(host.waitTicket(),MesVm.HostSignal.ADVANCE),"current07 releases before real09");check(vm.run(10)==MesVm.Status.WAIT_HOST&&host.pc1.waitKind()==9&&host.ui.waiting,"real08/45/09 reconstructs current raw log and enters nativewait9");}
        check(host.pc1.storyPrefix().size()==2&&host.pc1.storyPrefix().get(0).action.equals(fixture.toolbar0)&&host.pc1.storyPrefix().get(1).action.equals(":d")&&(!choices||host.ui.buttons.size()==5),"native story control owner retains template1 toolbar prefix and mode1 reserved+choice entries");
        if(choices)check(host.ui.buttons.get(3).action.equals(fixture.firstChoice)&&host.ui.buttons.get(4).action.equals("pickB"),"actual45 writes distinct choices after the reserved template3[0] slot");
        check(host.history.entries.isEmpty(),"portable expanded history is not populated as a fake native raw log");
        check(host.pc1.textLayout()!=null&&!host.pc1.textLayout().glyphs().isEmpty(),"history fixture has an actual typed glyph journal");
    }
    public static int run()throws Exception{
        count=0;
        for(boolean choices:new boolean[]{false,true}){
            Fixture fixture=new Fixture(choices);PcHost host=fixture.host();MesVm vm=fixture.vm(host);drive(fixture,host,vm,choices);Variables live=vm.variables;int pc=vm.offset(),head=host.pc;String sha=host.script.sha256;
            vm.variables.write(0,0,19);host.setNativeNames("F","A");byte[] unchanged=variables(live);
            check(host.pc1.historyPrevious()&&host.pc1.historySelection()==1&&host.body.equals("MID19")&&host.speaker.equals("A"),"previous uses raw checkpoint with CURRENT ordinary/name substitutions");
            check(host.pc1.storyPrefix().size()==2&&host.pc1.storyPrefix().get(0).action.equals(":u"),"old-history control view supplies only native toolbar prefix while full choice backing remains available");
            check(vm.offset()==pc&&host.pc==head&&host.script.sha256.equals(sha)&&Arrays.equals(unchanged,variables(live)),"history redraw changes neither VM PC/caller nor ordinary/undo owner");
            check(host.pc1.layoutIsHistory()&&host.pc1.dictionaryRegions().isEmpty(),"selected old history disables active inline dictionary controls");
            check(host.pc1.historyPrevious()&&host.pc1.historySelection()==0&&host.body.equals("OLD19"),"previous reaches oldest raw entry without a wholeVM restore");
            byte[] retained=journal(host.pc1),retainedBuffers=buffers(host.pc1);live.write(0,0,23);host.setNativeNames("F","B");
            check(!host.pc1.historyPrevious()&&host.pc1.historySelection()==0&&host.body.equals("OLD19")&&host.speaker.equals("A"),"oldest-boundary PgUp only refreshes controls, preserving its existing bitmap/journal");
            check(Arrays.equals(retained,journal(host.pc1))&&Arrays.equals(retainedBuffers,buffers(host.pc1)),"boundary does not rerun expansion or replace any known text backing");
            host.pc1.replayHistoryVoice();check(host.audio[5].equals("voice0")&&host.audioActive[5],"history click scans original selected35 and submits the raw voice resource");
            byte[] saved=vm.snapshot();PcHost restored=fixture.host();MesVm next=fixture.vm(restored);next.restore(saved);
            check(Arrays.equals(saved,next.snapshot())&&restored.pc1.historySelection()==0&&restored.pc1.layoutIsHistory(),"selected cursor and entire pending/glyph/known-memory owners roundtrip through portable snapshot");
            check(restored.body.equals("OLD19")&&next.variables.read(0,0)==23&&restored.givenName.equals("B"),"snapshot keeps stale displayed expansion separate from newer live values");
            int restoredPc=next.offset();byte[] beforeNext=variables(next.variables);check(restored.pc1.historyNext()&&restored.pc1.historySelection()==1&&restored.body.equals("MID23")&&restored.speaker.equals("B"),"next after restore re-reads raw MES with current values");
            check(next.offset()==restoredPc&&Arrays.equals(beforeNext,variables(next.variables)),"next history selection does not execute instructions or rewind variables");
            check(restored.pc1.historyNext()&&restored.pc1.historySelection()==-1&&restored.body.equals("CUR23")&&!restored.pc1.layoutIsHistory(),"reaching current row returns selection to normal and rebuilds current raw body");
            check(restored.pc1.storyPrefix().size()==2&&(!choices||restored.ui.buttons.size()==5&&restored.ui.buttons.get(3).action.equals("pickA")),"normal selection restores full choice suffix from separate global story backing");
            restored.audio[5]="currently playing";restored.audioActive[5]=true;check(!restored.pc1.historyNext()&&!restored.audioActive[5],"normal PgDn stops voice but never advances the VM or redraws another entry");
            check(restored.pc1.historyPrevious()&&restored.pc1.closeHistory()&&restored.pc1.historySelection()==-1&&restored.body.equals("CUR23"),"right/Esc close reconstructs current entry without opening a system context");
            check(!restored.pc1.inSystem()&&next.offset()==restoredPc&&next.status()==MesVm.Status.WAIT_HOST,"all history operations retain the current native wait/VM identity");
            check(!restored.pc1.closeHistory(),"normal view close is an explicit no-selection result");
            List<int[]> batches=restored.pc1.glyphBatches();check(!batches.isEmpty(),"redraw publishes typed glyph batch ranges");int original=batches.get(0)[0];batches.get(0)[0]=999;check(restored.pc1.glyphBatches().get(0)[0]==original,"glyph batch getter cannot mutate live journal ownership");
        }
        // A real menu1C must use the verified caller, restore raw log/frame
        // fields first, then expand current menu-modified values for wait7/9.
        for(boolean choices:new boolean[]{false,true})for(boolean selected:new boolean[]{false,true}){
            Fixture fixture=new Fixture(choices);PcHost host=fixture.host();MesVm vm=fixture.vm(host);drive(fixture,host,vm,choices);if(selected)host.pc1.historyPrevious();int returnedPc=vm.offset(),selection=host.pc1.historySelection();int[] rawWords=host.pc1.textLog().rawWords();Variables live=vm.variables;
            vm.enterNativeSystem("menu",0);check(host.pc1.inSystem()&&vm.scriptName().equals("menu")&&vm.variables==live,"native input enters menu with live Variables owner");
            check(vm.run(10)==MesVm.Status.WAIT_HOST&&host.body.equals("MENUBODY")&&live.read(0,0)==42&&host.ui.buttons.size()==1&&host.ui.buttons.get(0).action.equals("menuOnly"),"menu executes generic UI/4C and changes current ordinary bytes independently of story controls");host.setNativeNames("F","M");
            check(new String(host.pc1.textBuffers().read(Pc1TextBuffers.LIVE_BODY),StandardCharsets.US_ASCII).equals("MENUBODY"),"menu really overwrites global live text memory");
            byte[] pending=vm.snapshot();PcHost restored=fixture.host();MesVm resumed=fixture.vm(restored);resumed.restore(pending);check(Arrays.equals(pending,resumed.snapshot()),"pending menu and caller raw history survive a portable checkpoint");
            check(resumed.signalHost(restored.waitTicket(),MesVm.HostSignal.TEXT_COMPLETE),"menu presentation finishes before executing1C");
            MesVm.Status returned=resumed.run(1);check(returned==MesVm.Status.WAIT_HOST&&restored.pc1.returning()&&restored.pc1.inSystem(),"actual1C starts feedback before releasing native caller");completeReturn(resumed,restored);
            check(resumed.status()==MesVm.Status.WAIT_HOST&&resumed.scriptName().equals("START")&&resumed.offset()==returnedPc&&!restored.pc1.inSystem(),"actual1C returns to verified caller/consumed PC and retains original07/09 wait: choice="+choices+", selected="+selected+", status="+resumed.status()+", script="+resumed.scriptName()+", pc="+resumed.offset()+" expected="+returnedPc+", error="+resumed.error());
            check(restored.pc1.waitKind()==(choices?9:7)&&restored.pc1.historySelection()==selection&&Arrays.equals(rawWords,restored.pc1.textLog().rawWords()),"return restores frame raw log and external selection before replay");
            check(resumed.variables.read(0,0)==42&&restored.body.equals(selected?"MID42":"CUR42")&&restored.speaker.equals("M"),"1C redraw reads current menu-modified variables/name, not caller's cached Unicode text");
            check(new String(restored.pc1.textBuffers().read(Pc1TextBuffers.LIVE_BODY),StandardCharsets.US_ASCII).equals("MENUBODY"),"1F44 return never rolls back global live text buffer");
            check(new String(restored.pc1.textBuffers().read(Pc1TextBuffers.REDRAW_BODY),StandardCharsets.US_ASCII).equals(selected?"MID42":"CUR42"),"caller reconstruction uses independent418220 local buffer owner");
            check(restored.pc1.textLayout()!=null&&restored.pc1.layoutIsHistory()==selected,"typed journal now belongs to the returned caller selection");
            check(restored.pc1.storyPrefix().size()==2&&restored.pc1.storyPrefix().get(0).action.equals(":u")&&(!choices||restored.ui.buttons.size()==(selected?2:5)),"1C retains native story toolbar/full-choice backing independently of generic menu controls: choices="+choices+", selected="+selected+", buttons="+restored.ui.buttons.size());
            if(choices){if(selected)restored.pc1.closeHistory();check(restored.ui.buttons.size()==5&&restored.ui.buttons.get(4).action.equals("pickB"),"returned normal view retains both actual choice labels");check(resumed.activateMenu(restored.waitTicket(),3),"returned choice is genuinely actionable at its native-prefixed index");check(restored.pc1.textLog().count()==2&&restored.pc1.textLog().currentCheckpoint()==2&&restored.pc1.textLog().pendingRawWord()==2&&restored.pc1.waitKind()==-1,"successful native0based index>0 label pops count only after resolution, preserving current/stale words");check(resumed.run(10)==MesVm.Status.HALTED&&resumed.variables.read(0,2)==11&&resumed.variables.read(0,1)==0,"choice branch executes after explicit input, never falls through09 sentinel");}
        }
        // 17(1) in the menu is a REAL producer of the global story table.
        // 1C must retain that overwrite and untouched stale suffix, rather than
        // restoring an opaque copy of the caller's former button list.
        for(boolean selected:new boolean[]{false,true}){
            Fixture fixture=new Fixture(true,true);PcHost host=fixture.host();MesVm vm=fixture.vm(host);drive(fixture,host,vm,true);if(selected)host.pc1.historyPrevious();int returnPc=vm.offset();vm.enterNativeSystem("menu",0);
            check(vm.run(10)==MesVm.Status.WAIT_HOST&&host.pc1.storyPrefix().size()==1&&host.pc1.storyPrefix().get(0).action.equals("menuOnly"),"menu17(1) truly overwrites global story prefix while caller is suspended");
            byte[] saved=vm.snapshot();PcHost restored=fixture.host();MesVm resumed=fixture.vm(restored);resumed.restore(saved);check(Arrays.equals(saved,resumed.snapshot())&&restored.pc1.storyPrefix().size()==1,"P1R3 stores overwritten prefix and hidden stale choice suffix outside suspended1F44");
            resumed.signalHost(restored.waitTicket(),MesVm.HostSignal.TEXT_COMPLETE);check(resumed.run(1)==MesVm.Status.WAIT_HOST&&restored.pc1.returning()&&restored.pc1.inSystem(),"overwritten table caller remains suspended while feedback runs");completeReturn(resumed,restored);check(resumed.status()==MesVm.Status.WAIT_HOST&&resumed.offset()==returnPc&&restored.pc1.waitKind()==9,"overwritten global table does not release the returned native09 wait");
            check(restored.pc1.storyPrefix().size()==1&&restored.pc1.storyPrefix().get(0).action.equals("menuOnly")&&restored.ui.buttons.size()==(selected?1:5),"1C uses current global prefix/full count instead of rolling back caller's old toolbar2");
            if(selected)restored.pc1.closeHistory();
            check(restored.ui.buttons.size()==5&&restored.ui.buttons.get(0).action.equals("menuOnly")&&restored.ui.buttons.get(1).action.equals(":d")&&restored.ui.buttons.get(3).action.equals("pickA")&&restored.ui.buttons.get(4).action.equals("pickB"),"shorter native prefix write preserves untouched stale suffix and original full count");
            check(resumed.activateMenu(restored.waitTicket(),4)&&resumed.run(10)==MesVm.Status.HALTED&&resumed.variables.read(0,2)==22,"retained global choice suffix remains actionable after caller return");
            check(restored.pc1.textLog().count()==2&&restored.pc1.textLog().currentCheckpoint()==2&&restored.pc1.textLog().pendingRawWord()==2,"later actual choice uses the same native count-only pop, never clears the discarded word");
        }
        for(boolean indexZero:new boolean[]{false,true}){
            Fixture fixture=new Fixture(true,false,indexZero?"pickA":":u",indexZero?"pickA":"missing");PcHost host=fixture.host();MesVm vm=fixture.vm(host);drive(fixture,host,vm,true);int at=vm.offset();byte[] before=variables(vm.variables);long ticket=host.waitTicket();
            check(!vm.activateMenu(ticket,indexZero?0:3),indexZero?"native hit0 is zero-based and cannot take ordinary named-label branch":"missing native label is a known no-accept result");
            check(vm.status()==MesVm.Status.WAIT_HOST&&vm.offset()==at&&host.ui.waiting&&host.pc1.waitKind()==9&&host.pc1.textLog().count()==3,"unaccepted index0/missing action preserves PC, wait, selection and log count");
            check(Arrays.equals(before,variables(vm.variables))&&host.pc1.textLog().currentCheckpoint()==2&&host.pc1.textLog().rawEntry(2)==2,"unaccepted action does not partly pop or replace any current raw owner");
        }
        for(int nativeWait:new int[]{10,11}){
            Fixture fixture=new Fixture(true,false,":u","pickA",nativeWait);PcHost host=fixture.host();MesVm vm=fixture.vm(host);drive(fixture,host,vm,false);vm.signalHost(host.waitTicket(),MesVm.HostSignal.ADVANCE);
            check(vm.run(10)==MesVm.Status.WAIT_HOST&&host.pc1.waitKind()==nativeWait&&host.ui.buttons.size()==5,"real native0A/0B wait has its separate named-label event consumer");
            check(vm.activateMenu(host.waitTicket(),3)&&host.pc1.textLog().count()==3&&host.pc1.textLog().currentCheckpoint()==2,"wait10/11 successful label never applies ordinary07/09 history pop");
            check(vm.run(10)==MesVm.Status.HALTED&&vm.variables.read(0,2)==11&&host.pc1.textLog().count()==3,"separate wait10/11 branch executes once while preserving the raw history owner");
        }
        return count;
    }
    public static void main(String[] args)throws Exception{System.out.println("PASS "+run()+" PC1 raw-history/return assertions");}
}
