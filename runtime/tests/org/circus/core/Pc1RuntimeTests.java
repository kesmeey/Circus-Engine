package org.circus.core;

import java.io.*;
import java.util.*;

/** Full VM/host boundaries for the reviewed PC1 owners, using self-authored MES. */
public final class Pc1RuntimeTests {
    private static int count;
    private static void check(boolean ok,String why){count++;if(!ok)throw new AssertionError(why);}
    private interface Work {void run()throws Exception;}
    private static void reject(Work work,String message)throws Exception{boolean failed=false;try{work.run();}catch(IOException e){failed=String.valueOf(e.getMessage()).contains(message);}check(failed,"Reject "+message);}
    private static byte[] script(byte[]...records)throws IOException{
        ByteArrayOutputStream out=new ByteArrayOutputStream();out.write(new byte[]{0,0,0,0,0x63,0x20});for(byte[] record:records)out.write(record);out.write(new byte[3]);return out.toByteArray();
    }
    private static byte[] set(int i,int value){return new byte[]{0x58,0,0,(byte)i,0,(byte)value,0,0,0};}
    private static byte[] eventScript()throws IOException{
        ByteArrayOutputStream out=new ByteArrayOutputStream();out.write(new byte[]{1,0,0,0,14,0,0,0,0x63,0x20});out.write(set(0,11));out.write(new byte[]{3,0,0,0x4c,35,33,44,44,37,50,0,7,0,0});out.write(set(1,42));out.write(new byte[3]);return out.toByteArray();
    }
    private static byte[] dictionaryScript()throws IOException{
        ByteArrayOutputStream out=new ByteArrayOutputStream();out.write(new byte[]{1,0,0,0,14,0,0,0,0x63,0x20});out.write(new byte[]{3,0,0,0x4c,0x61,0x4d,0x61,0x21,0x61,0x4e,0,7,0,0});out.write(new byte[3]);return out.toByteArray();
    }
    private static int occurrences(byte[] bytes,byte[] marker){int count=0;for(int n=0;n+marker.length<=bytes.length;n++){boolean same=true;for(int k=0;k<marker.length;k++)same&=bytes[n+k]==marker[k];if(same)count++;}return count;}
    private static void completeReturn(MesVm vm,PcHost host)throws Exception{
        for(int n=0;host.pc1.returning();n++){if(n>=16)throw new AssertionError("Native1C return exceeded16 feedback frames");vm.elapse(40);}
        check(!host.pc1.inSystem(),"Only committed40ms return releases suspended native caller");
    }
    private static final class Fixture {
        final Map<String,byte[]> entries=new HashMap<>();
        final CircusResources data;
        Fixture(byte[] start,byte[] menu)throws IOException{
            entries.put("AdvData/DAT/NAMES.DAT",new byte[20]);entries.put("AdvData/MES/START.mes",start);entries.put("AdvData/MES/menu.mes",menu);
            GameResources files=new GameResources(new GameResources.Storage(){
                public Set<String> paths(){return entries.keySet();}
                private byte[] get(String p)throws IOException{byte[] b=entries.get(p);if(b==null)throw new FileNotFoundException(p);return b;}
                public byte[] read(String p,int limit)throws IOException{byte[] b=get(p);if(b.length>limit)throw new IOException("Fixture limit");return b.clone();}
                public Archive.Source source(String p)throws IOException{final byte[] b=get(p);return new Archive.Source(){public long size(){return b.length;}public byte[] read(long p,int n)throws IOException{Binary.range(p,n,b.length);return Arrays.copyOfRange(b,(int)p,(int)p+n);}};}
            });data=new CircusResources(files,Profiles.get("dcpc-2063"));
        }
        PcHost host()throws IOException{PcHost h=Pc1TestDefaults.bind(new PcHost(data,TextCodec.identity()));h.nativeFrameCapture=Pc1SystemContextTests::framebuffer;return h;}
        MesVm vm(PcHost host)throws IOException{return new MesVm(data.scripts(),host,"START");}
    }
    public static int run()throws Exception{
        count=0;
        byte[] call={0x26,0,0,0,'m','e','n','u',0};
        Fixture f=new Fixture(script(set(0,11),new byte[]{0x1b,1,0,0x3b,'x',0},call,set(1,42)),script(set(0,77),new byte[]{0x1c,0,0}));
        PcHost h=f.host();MesVm vm=f.vm(h);Variables live=vm.variables;
        h.scene.slots[1]="caller/background";h.scene.slots[21]="caller/message";h.scene.publish(h.ui,0,0);h.body="caller body";
        h.ui.actions[0]="caller action";h.write(90,4,61);h.pc1.soundRequest(255,1);
        check(h.pc1.packedSoundRequest()==0,"Opcode05 FF clears its saved request even in loop mode");
        check(vm.run(3)==MesVm.Status.RUNNING&&vm.scriptName().equals("menu")&&h.pc1.inSystem(),"Actual VM26 enters a typed context after its owned raw NUL string");
        check(h.pc1.system().hasNativeFrame(h.pc1.systemFrame()),"actual26 metadata borrows the fixed9D5E48 buffer");
        Pc1Frame candidate=h.pc1.saves().candidate();int candidatePc=candidate.returnPoint().pc;
        check(candidatePc==18&&candidate.preparedBy1B()&&h.pc1.saves().flag()==1,"1B's pre-string candidate remains separate from26 consumed return cursor");
        check(h.pc1.system().returnPoint().pc==30&&h.pc1.system().returnPoint().pc!=candidatePc,"System return and save-load cursors cannot be substituted");
        check(h.scene.slots[1].isEmpty()&&h.ui.actions[0].equals("caller action"),"Mode0 releases reviewed images while preserving native action0/1");
        check(vm.run(1)==MesVm.Status.RUNNING&&live.read(0,0)==77,"Menu writes the same ordinary-memory owner");
        h.scene.slots[21]="menu/message";h.write(100,3,55);h.write(90,4,99);h.ui.actions[2]="menu confirmation";
        h.audio[0]="menu/music";h.audioActive[0]=true;h.audioGeneration[0]=44;
        byte[] saved=vm.snapshot();PcHost restored=f.host();MesVm next=f.vm(restored);next.restore(saved);
        check(Arrays.equals(saved,next.snapshot())&&restored.pc1.inSystem(),"PCH8 persists complete typed hidden owners alongside the current menu VM");
        check(restored.pc1.system().hasNativeFrame(restored.pc1.systemFrame()),"whole VM restore binds metadata to the new runtime's sole suspended frame");
        check(next.run(1)==MesVm.Status.WAIT_HOST&&restored.pc1.returning()&&restored.pc1.inSystem(),"1C begins cooperative native feedback and retains suspended owner");completeReturn(next,restored);
        check(next.status()==MesVm.Status.RUNNING&&next.scriptName().equals("START")&&next.offset()==30&&!restored.pc1.inSystem(),"1C returns to the consumed cursor and releases the suspended owner once");
        check(next.variables.read(0,0)==77&&restored.read(100,3)==55&&restored.read(90,4)==61,"Ordinary and persistent menu changes stay live while frame bank90 returns");
        check(restored.scene.slots[1].equals("caller/background")&&restored.scene.slots[21].equals("menu/message"),"Only verified image owners return; message resource21 remains live");
        check(restored.body.equals("caller body")&&restored.ui.actions[2].equals("menu confirmation"),"Text display returns without rolling back external confirmation actions");
        check(restored.audio[0].equals("menu/music")&&restored.audioGeneration[0]==44,"System return does not rewind or replay unrelated physical BGM");
        check(next.run(10)==MesVm.Status.HALTED&&next.variables.read(0,1)==42,"Caller continues once after system return");
        check(restored.pc1.saves().candidate().returnPoint().pc==candidatePc&&restored.pc1.saves().candidate().ordinaryBytes()[0]==11,"Candidate is not replaced by menu writes or system return");
        Fixture changed=new Fixture(script(set(0,12),new byte[]{0x1b,1,0,0x3b,'x',0},call,set(1,42)),script(set(0,77),new byte[]{0x1c,0,0}));
        PcHost changedHost=changed.host();reject(()->changed.vm(changedHost).restore(saved),"suspended caller code changed");
        PcHost noFrame=f.host();noFrame.nativeFrameCapture=null;MesVm noFrameVm=f.vm(noFrame);check(noFrameVm.run(3)==MesVm.Status.FAULT&&!noFrame.pc1.inSystem()&&noFrameVm.scriptName().equals("START"),"Missing full-frame adapter rejects entry before publishing a context or changing script");
        check(noFrameVm.error().contains("complete framebuffer capture"),"Missing capture diagnostic identifies the actual prerequisite");
        PcHost query=f.host();query.attach(new Variables(query,Variables.Model.PC_BYTE));query.listOffset(12);query.write(99,246,99);query.logicalMusic(4);query.ui.hovered=7;
        check(query.read(99,244)==0&&query.read(99,245)==0&&query.read(99,246)==0&&query.listOffset()==12,"Unmapped PC1 script registers cannot expose or mutate platform list/BGM/hover state");
        query.write(90,100,257);check(query.read(90,100)==1,"Native bank90 extends beyond portable100-byte scratch and stores low8");
        reject(()->query.read(90,Integer.MAX_VALUE),"unowned");query.write(90,-6369,1);check(query.pc1.nativeMemory().readAddress(Pc1FrameLayout.LIVE_BASE-1)==1,"negative bank90 index reaches the newly reviewed adjacent modal-text owner");
        check(query.ui.quadLimit==60,"PC1 pending draw capacity matches native frame60");
        for(int selector:new int[]{5,6}){final int nativeSelector=selector;reject(()->query.command("pc.quad",new int[]{0,0,0,0,8,8,nativeSelector,0},new byte[0]),"native bitmap cache selector");}
        for(int selector:new int[]{1,4,127,255}){
            Fixture resources=new Fixture(script(new byte[]{0x25,(byte)selector,0,0,'x',0},set(1,42)),script());
            PcHost noEffect=resources.host();MesVm resourceVm=resources.vm(noEffect);for(int n=0;n<noEffect.scene.slots.length;n++)noEffect.scene.slots[n]="slot"+n;String[] original=noEffect.scene.slots.clone();
            check(resourceVm.run(10)==MesVm.Status.HALTED&&resourceVm.variables.read(0,1)==42,"Native25 no-effect selector still consumes its STOP-marked raw tail");
            check(Arrays.equals(original,noEffect.scene.slots),"Native25 unmapped selector does not invent a resource handle write");
        }
        Fixture persist=new Fixture(script(set(0,31),new byte[]{0x1b,1,0,0x3b,'x',0},set(0,91),new byte[]{0x15,4,0}),script());
        PcHost saver=persist.host();MesVm saveVm=persist.vm(saver);saver.write(99,254,7);
        check(saveVm.run(10)==MesVm.Status.WAIT_HOST&&saver.saveRequest==7&&saver.pc1.requestedSlot()==7,"Native persist4 requests the selected slot and suspends VM for actual I/O");
        check(saver.pc1.requestedSave().ordinaryBytes()[0]==31&&saveVm.variables.read(0,0)==91,"Native requested frame is the earlier1B candidate, not the current whole VM");
        byte[] pending=saveVm.snapshot();PcHost pendingHost=persist.host();MesVm pendingVm=persist.vm(pendingHost);pendingVm.restore(pending);
        check(Arrays.equals(pending,pendingVm.snapshot())&&pendingHost.pc1.requestedSave().ordinaryBytes()[0]==31,"Pending save ownership survives portable snapshot without writing a native slot");
        pendingHost.completeSave();check(pendingHost.saveRequest==-1&&pendingHost.pc1.requestedSave()==null&&pendingHost.pc1.requestedSlot()==-1,"Only completion releases both platform and native save request owners");
        check(pendingVm.run(10)==MesVm.Status.HALTED,"Completed I/O releases WAIT_HOST without replacing the running VM");
        ByteArrayOutputStream owner=new ByteArrayOutputStream();query.pc1.save(new DataOutputStream(owner));
        PcHost inconsistent=f.host();inconsistent.saveRequest=7;
        reject(()->inconsistent.pc1.load(new DataInputStream(new ByteArrayInputStream(owner.toByteArray()))),"pending save state");
        for(int kind=0;kind<3;kind++){
            Fixture event=new Fixture(eventScript(),script(set(0,77),new byte[]{0x1c,0,0}));
            Dc2Session session=new Dc2Session(event.data,TextCodec.identity(),Pc1TestDefaults::bind);PcHost eventHost=(PcHost)session.host;eventHost.nativeFrameCapture=Pc1SystemContextTests::framebuffer;
            check(session.vm.run(3)==MesVm.Status.WAIT_HOST&&eventHost.body.equals("CALLER"),"Actual4C presentation yields before the native07 message wait");
            session.vm.signalHost(eventHost.waitTicket(),MesVm.HostSignal.TEXT_COMPLETE);
            check(session.vm.run(1)==MesVm.Status.WAIT_HOST&&session.vm.offset()==33&&eventHost.pc==30,"Native event starts at consumed07 PC33 distinct from last-dispatched head30");
            eventHost.audio[5]="live voice";eventHost.audioActive[5]=true;eventHost.audioGeneration[5]=40;
            Variables memory=session.vm.variables;session.openSystem("menu",kind);
            check(session.inSystem()&&session.vm.scriptName().equals("menu")&&session.vm.variables==memory,"Session input uses typed PC1 owners and retains the live Variables object");
            check(eventHost.pc1.saves().candidate().returnPoint().pc==33&&eventHost.pc1.saves().candidate().sourceOpcodeOffset()==-1,"Flag0 input refresh captures actualVM33 rather than fabricating an after-head cursor from30");
            check(!eventHost.audioActive[5]&&eventHost.audioGeneration[5]==(kind==1?42:41),"Native right/toolbar event keeps its proven one/two voice-stop order");
            check(session.vm.run(2)==MesVm.Status.WAIT_HOST&&session.inSystem()&&eventHost.pc1.returning(),"Actual menu1C starts return feedback without advancing caller");completeReturn(session.vm,eventHost);
            check(session.vm.status()==MesVm.Status.WAIT_HOST&&!session.inSystem()&&session.vm.offset()==33&&memory.read(0,0)==77,"Actual menu1C returns with current ordinary bytes and original message wait");
            check(session.host==eventHost&&session.vm.variables==memory&&eventHost.body.equals("CALLER"),"Event return keeps the VM/host pair instead of restoring an opaque caller snapshot");
            session.vm.signalHost(eventHost.waitTicket(),MesVm.HostSignal.ADVANCE);
            check(session.vm.run(10)==MesVm.Status.HALTED&&memory.read(0,1)==42,"Input caller resumes after07 exactly once");
        }
        Fixture aliasFixture=new Fixture(dictionaryScript(),script());PcHost aliasHost=aliasFixture.host();MesVm aliasVm=aliasFixture.vm(aliasHost);check(aliasVm.run(3)==MesVm.Status.WAIT_HOST,"native4C dictionary fixture prepares a canonical glyph journal");aliasVm.signalHost(aliasHost.waitTicket(),MesVm.HostSignal.TEXT_COMPLETE);check(aliasVm.run(1)==MesVm.Status.WAIT_HOST&&aliasHost.pc1.waitKind()==7&&aliasHost.pc1.dictionaryRegions().size()==1,"native07 captures active count before disabling dictionary construction");
        Pc1LayoutMemory raw=aliasHost.pc1.layoutMemory();int oldGlyphs=raw.readI32(Pc1LayoutMemory.GLYPH_COUNT);check(oldGlyphs>0&&raw.readI32(Pc1LayoutMemory.DICT_COUNT)==-1,"raw journal and disabled construction count are distinct from active dictionary count");aliasHost.pc1.nativeUi().writeByte(Pc1LayoutMemory.WORDS+1,0x23);check(aliasHost.pc1.dictionaryRegions().get(0).encodedWord()[1]==0x23,"active dictionary reads current NativeUi word backing after activation");raw.writeI32(Pc1LayoutMemory.RECORDS+556,0x9d3afa);check(Arrays.equals(aliasHost.pc1.dictionaryRegions().get(0).encodedWord(),new byte[]{(byte)0x81,0x41,(byte)0x81,0x6e}),"active dictionary follows aliased raw pointer into current known live body");
        raw.writeI32(Pc1InlineText.X,12345);raw.writeI32(Pc1InlineText.FONT,22);raw.writeI32(Pc1LayoutMemory.GLYPH_COUNT,0);raw.write(Pc1LayoutMemory.GLYPHS+31999,0x5a);check(aliasHost.pc1.textLayout().x==12345&&aliasHost.pc1.textLayout().fontSize==22&&aliasHost.pc1.textLayout().glyphCount==0,"runtime textLayout getter freezes current canonical bytes instead of an old DTO");
        byte[] aliasSaved=aliasVm.snapshot();check(occurrences(aliasSaved,new byte[]{0x50,0x31,0x4c,0x4d})==1&&occurrences(aliasSaved,new byte[]{0x50,0x31,0x59,0x32})==0,"portable runtime stores one P1LM raw owner and no duplicate P1Y2 Snapshot authority");PcHost aliasRestored=aliasFixture.host();MesVm aliasNext=aliasFixture.vm(aliasRestored);aliasNext.restore(aliasSaved);check(Arrays.equals(aliasSaved,aliasNext.snapshot())&&aliasRestored.pc1.textLayout().glyphCount==0&&!aliasRestored.pc1.glyphBatches().isEmpty(),"raw glyph-count shrink does not invalidate previously captured portable batch metadata");check(aliasRestored.pc1.layoutMemory().read(Pc1LayoutMemory.GLYPHS+31999)==0x5a&&aliasRestored.pc1.dictionaryRegions().get(0).encodedWord()[1]==0x41,"whole snapshot restores inactive raw glyph tail and resolves external pointer through restored TextBuffers");
        aliasRestored.pc1.layoutMemory().writeI32(Pc1LayoutMemory.GLYPH_COUNT,1001);byte[] rawCount=aliasNext.snapshot();PcHost rawRestored=aliasFixture.host();MesVm rawNext=aliasFixture.vm(rawRestored);rawNext.restore(rawCount);check(Arrays.equals(rawCount,rawNext.snapshot())&&rawRestored.pc1.layoutMemory().readI32(Pc1LayoutMemory.GLYPH_COUNT)==1001,"raw owner codec cannot reject native DWORD state based on a portable glyph cache");boolean failedAtConsume=false;try{rawRestored.pc1.textLayout();}catch(UncheckedIOException expected){failedAtConsume=true;}check(failedAtConsume,"unknown glyph neighbor fails only at actual DTO/render consumption");
        Pc1TextLayout presenterLayout=aliasRestored.pc1.nativeTextLayout();
        check(presenterLayout==aliasRestored.pc1.nativeTextLayout(),"presentation and dispatcher retain one mutable layout metadata owner");
        presenterLayout.clearGlyphs();presenterLayout.position(12,34,28);presenterLayout.step(new Pc1TextLayout.Source("external-known",new byte[]{'Z',0,0}),0,7,true,0);
        check(aliasRestored.pc1.textLayout().glyphCount==1&&aliasRestored.pc1.layoutMemory().readI32(Pc1LayoutMemory.GLYPH_COUNT)==1,"external presenter changes canonical glyph journal without publishing a DTO");
        byte[] presenterSave=aliasNext.snapshot();PcHost presenterRestored=aliasFixture.host();MesVm presenterNext=aliasFixture.vm(presenterRestored);presenterNext.restore(presenterSave);
        check(Arrays.equals(presenterSave,presenterNext.snapshot())&&presenterRestored.pc1.textLayout().glyphCount==1,"live presenter source-lifetime metadata is saved with its canonical bytes");
        return count;
    }
    public static void main(String[] args)throws Exception{System.out.println("PASS "+run()+" PC1 VM/host owner assertions");}
}
