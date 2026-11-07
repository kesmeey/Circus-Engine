package org.circus.core;

import java.io.*;
import java.util.*;

/** Self-authored native PC1 MES and NAMES owners. No original game process,
 * glyph drawing, native slot I/O or full native text-pointer consumer. */
public final class Pc1ConditionalTests {
    private static int count;
    private static final byte[] CONDITIONAL={0x2a,0,0};
    private static void check(boolean value,String why){count++;if(!value)throw new AssertionError(why);}
    private interface Action{void run()throws Exception;}
    private static void rejects(Action action,String why)throws Exception{boolean rejected=false;try{action.run();}catch(IOException expected){rejected=true;}check(rejected,why);}
    private static byte[] concat(byte[]...parts)throws IOException{ByteArrayOutputStream bytes=new ByteArrayOutputStream();for(byte[] part:parts)bytes.write(part);return bytes.toByteArray();}
    private static byte[] script(byte[]...records)throws IOException{return concat(new byte[]{0,0,0,0,0x63,0x20},concat(records),new byte[3]);}
    private static byte[] words(int opcode,int...args){byte[] bytes=new byte[1+2*args.length];bytes[0]=(byte)opcode;for(int n=0;n<args.length;n++){bytes[1+n*2]=(byte)args[n];bytes[2+n*2]=(byte)(args[n]>>>8);}return bytes;}
    private static byte[] quad()throws IOException{return concat(words(0x53,1,2,0,0),words(0x52,8,10,0x40,0));}
    private static byte[] text(int marker,byte...raw)throws IOException{return concat(words(0x5c,3,4,28,7),new byte[]{(byte)marker},raw,new byte[1]);}
    private static byte[] encoded(String value){byte[] raw=value.getBytes(Binary.SJIS);for(int n=0;n<raw.length;n++)raw[n]=(byte)((raw[n]&255)-32);return raw;}
    private static byte[] transfer(int mode)throws IOException{
        byte[] name={'N','E','X','T',0};if(mode==0)return concat(new byte[]{0x26,0,0,0},name);
        if(mode==2)return concat(new byte[]{0x37},name);return concat(new byte[]{0x36},name,mode==3?new byte[]{2,1,0}:new byte[0]);
    }
    private static byte[] names()throws IOException{
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();bytes.write(new byte[16]);bytes.write(new byte[]{2,0,0,0});
        for(String name:new String[]{"START","NEXT"}){byte[] row=new byte[188];byte[] raw=name.getBytes(Binary.SJIS);System.arraycopy(raw,0,row,0,raw.length);byte[] caption=("caption-"+name).getBytes(Binary.SJIS);System.arraycopy(caption,0,row,128,caption.length);bytes.write(row);}return bytes.toByteArray();
    }
    private static final class Fixture {
        final Map<String,byte[]> files=new HashMap<>();final CircusResources resources;
        Fixture(byte[] start,byte[] next)throws IOException{
            files.put("AdvData/DAT/NAMES.DAT",names());files.put("AdvData/MES/START.mes",start);files.put("AdvData/MES/NEXT.mes",next);
            GameResources data=new GameResources(new GameResources.Storage(){
                public Set<String> paths(){return files.keySet();}
                public byte[] read(String path,int limit)throws IOException{byte[] raw=files.get(path);if(raw==null)throw new FileNotFoundException(path);if(raw.length>limit)throw new IOException("Fixture read limit");return raw.clone();}
                public Archive.Source source(String path)throws IOException{final byte[] raw=read(path,Integer.MAX_VALUE);return new Archive.Source(){public long size(){return raw.length;}public byte[] read(long offset,int length)throws IOException{Binary.range(offset,length,raw.length);return Arrays.copyOfRange(raw,(int)offset,(int)offset+length);}};}
            });resources=new CircusResources(data,Profiles.get("dcpc-2063"));
        }
        PcHost host()throws IOException{PcHost host=Pc1TestDefaults.bind(new PcHost(resources,TextCodec.identity()));host.nativeFrameCapture=()->UiCapture.pixels(1,1,new int[]{0xff123456});return host;}
        MesVm vm(PcHost host)throws IOException{return new MesVm(resources.scripts(),host,"START");}
    }
    private static byte[] uiWire(Dc2Ui ui)throws IOException{ByteArrayOutputStream bytes=new ByteArrayOutputStream();ui.save(new DataOutputStream(bytes));return bytes.toByteArray();}
    private static byte[] frameWire(Dc2Scene.Frame frame)throws IOException{ByteArrayOutputStream bytes=new ByteArrayOutputStream();frame.save(new DataOutputStream(bytes));return bytes.toByteArray();}
    private static byte[] captureWire(UiCapture capture)throws IOException{ByteArrayOutputStream bytes=new ByteArrayOutputStream();UiCapture.save(new DataOutputStream(bytes),capture);return bytes.toByteArray();}
    private static byte[] listWire(List<Dc2Ui.Quad> quads)throws IOException{ByteArrayOutputStream bytes=new ByteArrayOutputStream();Dc2Ui.saveQuads(new DataOutputStream(bytes),quads);return bytes.toByteArray();}
    private static SceneCaptures.Legacy captured(PcHost host,Dc2Scene.Frame frame,Variables variables)throws IOException{return new SceneCaptures.Legacy(SceneCaptures.legacy(frame,host.scene,host.ui,variables,false,null).input());}
    public static int run()throws Exception{
        count=0;
        for(boolean image:new boolean[]{true,false}){
            Fixture fixture=new Fixture(script(CONDITIONAL,image?quad():text('K',(byte)'R',(byte)'A',(byte)'W')),script());PcHost host=fixture.host();MesVm vm=fixture.vm(host);
            check(vm.run(1)==MesVm.Status.RUNNING&&host.ui.quads.isEmpty(),"native2A sets only pending condition, no variable binding/draw entry");
            byte[] before=vm.snapshot();PcHost resumed=fixture.host();MesVm restored=fixture.vm(resumed);restored.restore(before);
            check(Arrays.equals(before,restored.snapshot()),"pending condition and dispatcher locals survive PCH9/P1R2 whole snapshot");
            check(restored.run(1)==MesVm.Status.RUNNING&&resumed.ui.quads.size()==1,"actual2A->"+(image?"53":"5C")+" creates one draw item");Dc2Ui.Quad q=resumed.ui.quads.get(0);
            check(q.nativeCondition==1&&q.binding==0,"native d3 condition is independent of encoded-variable binding");
            check(resumed.pc1.takeCondition()==0,"successful creation consumes native pending flag exactly once");
            if(!image)check(q.text.isEmpty()&&q.nativeTextOffset==15,"owned5C markerK stores current loaded-code offset, not shifted/frozen Unicode");
            resumed.pc1.cachedScenarioRead(0);check(!resumed.pc1.visible(q),"unread cached scenario hides conditional draw entry");resumed.pc1.cachedScenarioRead(9);check(resumed.pc1.visible(q),"any nonzero cached read byte admits conditional entry");
        }
        Fixture opaqueFixture=new Fixture(script(CONDITIONAL,text('K',(byte)0x81)),script());PcHost opaqueHost=opaqueFixture.host();MesVm opaqueVm=opaqueFixture.vm(opaqueHost);
        check(opaqueVm.run(2)==MesVm.Status.RUNNING&&opaqueHost.ui.quads.size()==1,"native5C producer accepts markerK raw bytes that cannot yet decode as CP932");
        Dc2Ui.Quad opaque=opaqueHost.ui.quads.get(0);check(opaque.nativeTextOffset==15&&opaque.text.isEmpty()&&opaque.nativeCondition==1,"invalid-CP932 source remains a code-relative pointer, not a fabricated Unicode string");
        check(opaqueFixture.resources.scripts().script("START").rawOpcode(opaque.nativeTextOffset+4)==0x81,"pointer addresses the original raw payload after arbitrary marker, before native expansion");
        byte[] opaqueSaved=opaqueVm.snapshot();PcHost opaqueReloaded=opaqueFixture.host();MesVm opaqueRestored=opaqueFixture.vm(opaqueReloaded);opaqueRestored.restore(opaqueSaved);
        check(Arrays.equals(opaqueSaved,opaqueRestored.snapshot())&&opaqueReloaded.ui.quads.get(0).nativeTextOffset==15,"opaque pointer and condition survive portable snapshot without decoding source bytes");
        byte[] opaqueList=listWire(opaqueHost.ui.quads);check(new DataInputStream(new ByteArrayInputStream(opaqueList)).readInt()==-3,"pointer-bearing list uses distinct reviewed codec marker");
        List<Dc2Ui.Quad> opaqueCopy=Dc2Ui.copyQuads(opaqueHost.ui.quads);check(opaqueCopy.get(0).nativeTextOffset==15&&opaqueCopy.get(0).nativeCondition==1,"raw pointer list deep copy retains offset and separate condition");opaqueCopy.get(0).nativeTextOffset=99;
        check(opaque.nativeTextOffset==15,"pointer-bearing copy cannot alias producer state");
        opaqueHost.scene.publish(opaqueHost.ui,0,0);opaqueHost.pc1.cachedScenarioRead(0);
        check(captured(opaqueHost,opaqueHost.scene.shown,opaqueVm.variables).frame.quads.isEmpty(),"unread conditional raw text is filtered without trying to decode invalid bytes");
        opaqueHost.pc1.cachedScenarioRead(1);rejects(()->captured(opaqueHost,opaqueHost.scene.shown,opaqueVm.variables),"accepted pointer-bearing capture requires an explicit current-code consumer");
        for(int mode=0;mode<4;mode++)for(boolean image:new boolean[]{true,false}){
            Fixture fixture=new Fixture(script(CONDITIONAL,transfer(mode)),script(image?quad():text(0,(byte)'x')));PcHost host=fixture.host();MesVm vm=fixture.vm(host);
            check(vm.run(2)==MesVm.Status.RUNNING&&vm.scriptName().equals("NEXT"),"real VM executes native load mode"+mode);
            check(vm.run(1)==MesVm.Status.RUNNING&&host.ui.quads.size()==1,"next script creates draw after mode"+mode);
            check(host.ui.quads.get(0).nativeCondition==(mode==0||mode==2?1:0),"411670 mode0/2 retain BB8430 while1/3 clear it");
        }
        Fixture readFixture=new Fixture(script(quad()),script());PcHost readHost=readFixture.host();readHost.write(98,0,7);MesVm readVm=readFixture.vm(readHost);
        check(readVm.run(1)==MesVm.Status.RUNNING&&readHost.read(99,251)==7,"first native bind caches matching START read byte from NAMES");
        readHost.write(98,0,0);check(readHost.read(98,0)==0&&readHost.read(99,251)==7,"write98 does not refresh cached99:251 in the current caller");
        readHost.write(98,1,19);readHost.pc1.scriptLoaded(2,"NEXT.mes");check(readHost.read(99,251)==7&&readHost.checkpointLabel.equals("caption-NEXT"),"mode2 preserves cache but41AEA0 still updates scenario caption");
        readHost.pc1.scriptLoaded(0,"NEXT.other.extension");check(readHost.read(99,251)==7,"mode0 preserves cache after first-dot scenario lookup");
        readHost.pc1.scriptLoaded(1,"next.any.extra");check(readHost.read(99,251)==19,"mode1 first-dot lookup ignores suffix and compares case insensitively");
        readHost.write(98,1,31);check(readHost.read(99,251)==19,"cached read remains a captured byte after persistent mutation");
        readHost.pc1.scriptLoaded(3,"NEXT");check(readHost.read(99,251)==31,"mode3 refreshes cache from current seen owner");
        readHost.pc1.scriptLoaded(1,"dir/NEXT.mes");check(readHost.read(99,251)==0&&readHost.checkpointLabel.isEmpty(),"native compare never strips a directory prefix to force a basename match");
        readHost.pc1.scriptLoaded(1,"NEXT_suffix");check(readHost.read(99,251)==0,"scenario name requires entire pre-dot equality, not prefix match");
        Fixture cacheFixture=new Fixture(script(quad()),script());PcHost cacheHost=cacheFixture.host();cacheHost.write(98,0,7);MesVm cacheVm=cacheFixture.vm(cacheHost);cacheVm.run(1);cacheHost.write(98,0,29);byte[] cacheSaved=cacheVm.snapshot();PcHost cacheReloaded=cacheFixture.host();MesVm cacheRestored=cacheFixture.vm(cacheReloaded);cacheRestored.restore(cacheSaved);
        check(cacheReloaded.read(99,251)==7&&cacheReloaded.read(98,0)==29&&Arrays.equals(cacheSaved,cacheRestored.snapshot()),"PCH9/P1R2 preserves cached read independently of current persistent seen byte");
        check(cacheRestored.run(1)==MesVm.Status.HALTED&&cacheReloaded.read(99,251)==7,"restore does not rerun initial native loader and recache newer seen state");
        for(boolean intervening:new boolean[]{false,true}){
            Fixture fixture=new Fixture(script(new byte[]{0x4b,46,33,45,37,0},intervening?words(0x58,0,3,11,0):new byte[0],new byte[]{0x4c,34,47,36,57,0}),script());PcHost speakerHost=fixture.host();MesVm speakerVm=fixture.vm(speakerHost);
            check(speakerVm.run(1)==MesVm.Status.RUNNING&&speakerHost.speaker.equals("NAME"),"native4B does not yield merely because the VM caller budget ends");byte[] saved=speakerVm.snapshot();PcHost after=fixture.host();MesVm resumed=fixture.vm(after);resumed.restore(saved);
            check(resumed.run(intervening?2:1)==MesVm.Status.WAIT_HOST&&after.body.equals("BODY"),"next fetch reaches a genuine4C presentation after budget/snapshot boundary");
            check(after.speaker.equals(intervening?"":"NAME"),"dispatcher-local previous opcode covers pure VM operations and survives a nonyield budget boundary");
            check(after.pc1.previousOpcode()==(intervening?0x58:0x4b)&&after.pc1.lastYieldOpcode()==0x4c,"native local previous and global last-yield are distinct owners");
        }
        // The loader resets43D414 even though an earlier presentation yielded4C.
        for(int mode=0;mode<4;mode++){
            Fixture fixture=new Fixture(script(new byte[]{0x4c,33,0},transfer(mode)),script(new byte[]{7,0,0},words(0x58,0,1,42,0)));PcHost host=fixture.host();MesVm vm=fixture.vm(host);
            check(vm.run(1)==MesVm.Status.WAIT_HOST&&host.pc1.lastYieldOpcode()==0x4c,"presentation has a genuine native4C yield");
            check(vm.signalHost(host.waitTicket(),MesVm.HostSignal.TEXT_COMPLETE),"presentation completion releases4C before load");
            check(vm.run(1)==MesVm.Status.RUNNING&&vm.scriptName().equals("NEXT")&&host.pc1.lastYieldOpcode()==-1,"all native loader modes reset43D414 independently of pending condition");
            check(vm.run(2)==MesVm.Status.RUNNING&&vm.variables.read(0,1)==42&&!host.textWaiting,"leading07 after load must not inherit old caller's4C wait");
        }
        // Global type10 pointers alias the current loaded code immediately,
        // including a budget boundary before the target's first instruction.
        for(int mode=0;mode<4;mode++){
            Fixture fixture=new Fixture(script(text('K',encoded("$0,1")),new byte[]{0x22,1,0},transfer(mode)),script(text(0,encoded("NEW"))));PcHost aliasHost=fixture.host();MesVm aliasVm=fixture.vm(aliasHost);aliasVm.variables.write(0,1,9);
            check(aliasVm.run(2)==MesVm.Status.RUNNING&&aliasHost.ui.quadTemplates.get(1).size()==1,"source5C pointer enters the global geometry template");Dc2Ui.Quad retained=aliasHost.ui.quadTemplates.get(1).get(0);
            check(retained.nativeTextOffset==12&&aliasHost.pc1.resolveDrawText(retained).equals("9"),"raw type10 text expands from current code and live variable owner");
            Dc2Scene.Frame textFrame=new Dc2Scene.Frame();textFrame.quads.add(retained);byte[] textBefore=frameWire(textFrame);UiCapture textCapture=SceneCaptures.legacy(textFrame,aliasHost.scene,aliasHost.ui,aliasVm.variables,false,null,aliasHost.pc1::resolveDrawText);SceneCaptures.Legacy frozenText=new SceneCaptures.Legacy(textCapture.input());
            check(frozenText.frame.quads.get(0).nativeTextOffset==-1&&frozenText.frame.quads.get(0).text.equals("9")&&Arrays.equals(textBefore,frameWire(textFrame)),"capture resolves current type10 source without mutating global pointer owner");
            aliasVm.variables.write(0,1,11);check(aliasHost.pc1.resolveDrawText(retained).equals("11")&&new SceneCaptures.Legacy(textCapture.input()).frame.quads.get(0).text.equals("9"),"live pointer re-expands current variables while frozen capture retains submitted text");
            check(aliasVm.run(1)==MesVm.Status.RUNNING&&aliasVm.scriptName().equals("NEXT"),"native mode"+mode+" successfully switches code without fetching target yet");int transferHead=aliasHost.pc;
            check(aliasHost.script.name.equals("NEXT")&&aliasHost.pc1.resolveDrawText(retained).equals("NEW"),"loaded-code owner switches immediately at transfer boundary, no frozen old-script text");
            check(aliasHost.pc==transferHead&&retained.nativeTextOffset==12&&retained.text.isEmpty(),"code-owner rebind preserves last dispatched head and the global raw offset");
            check(new SceneCaptures.Legacy(textCapture.input()).frame.quads.get(0).text.equals("9"),"captured text also cannot follow a subsequent loaded-code replacement");
        }
        for(int marker=0;marker<256;marker++){
            Fixture fixture=new Fixture(script(text(marker,encoded("X$0,1"))),script());PcHost host=fixture.host();MesVm vm=fixture.vm(host);vm.variables.write(0,1,9);
            check(vm.run(1)==MesVm.Status.RUNNING&&host.ui.quads.get(0).text.isEmpty(),"5C marker"+marker+" stores raw pointer without decoding at production");
            check(host.pc1.resolveDrawText(host.ui.quads.get(0)).equals("X9"),"411D30 converts source bytes+20 before expansion independently of marker"+marker);
        }
        Fixture captureFixture=new Fixture(script(CONDITIONAL,quad()),script());PcHost host=captureFixture.host();MesVm vm=captureFixture.vm(host);check(vm.run(2)==MesVm.Status.RUNNING,"capture fixture creates actual conditional53");
        Dc2Ui.Quad conditional=host.ui.quads.get(0);conditional.binding=1;conditional.animation=1;conditional.frames=4;conditional.period=1;conditional.step=3;conditional.sx=5;
        Dc2Ui.Quad unconditional=new Dc2Ui.Quad();unconditional.type=1;unconditional.width=8;unconditional.height=9;unconditional.flags=0x40;host.ui.quads.add(unconditional);host.scene.publish(host.ui,0,0);Dc2Scene.Frame live=host.scene.shown;
        host.pc1.cachedScenarioRead(0);vm.variables.write(0,1,1);byte[] frameBefore=frameWire(live),uiBefore=uiWire(host.ui);SceneCaptures.Legacy unread=captured(host,live,vm.variables);
        check(unread.frame.quads.size()==1&&unread.frame.quads.get(0).nativeCondition==0,"capture filters unread conditional entries at capture time");
        check(Arrays.equals(frameBefore,frameWire(live))&&Arrays.equals(uiBefore,uiWire(host.ui)),"filtering/cloning capture never removes or clears fields on live frame/UI");
        host.pc1.cachedScenarioRead(1);host.scene.effectClock=68;UiCapture frozen=SceneCaptures.legacy(live,host.scene,host.ui,vm.variables,false,null);SceneCaptures.Legacy accepted=new SceneCaptures.Legacy(frozen.input());
        check(accepted.frame.quads.size()==2,"read cache plus enabled variable binding admits frozen item");Dc2Ui.Quad frozenQuad=accepted.frame.quads.get(0);
        check(frozenQuad.nativeCondition==0&&frozenQuad.binding==0&&frozenQuad.animation==0&&frozenQuad.sx==11,"capture freezes native condition, variable binding and current animation frame");
        check(Arrays.equals(frameBefore,frameWire(live))&&Arrays.equals(uiBefore,uiWire(host.ui)),"accepted capture also preserves every original live field");
        byte[] fixed=captureWire(frozen);host.pc1.cachedScenarioRead(0);vm.variables.write(0,1,0);host.scene.effectClock=9999;
        check(Arrays.equals(fixed,captureWire(frozen))&&new SceneCaptures.Legacy(frozen.input()).frame.quads.size()==2,"frozen capture cannot follow later cache, variable or clock changes");
        rejects(()->SceneCaptures.legacy(live,host.scene,host.ui,null,false,null),"conditional capture without native cache owner is explicit");
        byte[] encoded=listWire(live.quads);check(new DataInputStream(new ByteArrayInputStream(encoded)).readInt()==-3,"condition-bearing lists use the reviewed native-fields extension sentinel");
        List<Dc2Ui.Quad> copied=Dc2Ui.copyQuads(live.quads);check(copied.size()==2&&copied.get(0).nativeCondition==1&&copied.get(0).binding==1,"list codec deep copy preserves separate condition/binding owners");
        copied.get(0).nativeCondition=0;copied.get(0).x=999;check(live.quads.get(0).nativeCondition==1&&live.quads.get(0).x!=999,"copied conditional quad mutation cannot alias live owner");
        check(new DataInputStream(new ByteArrayInputStream(listWire(Collections.singletonList(unconditional)))).readInt()==1,"unconditional lists retain legacy nonextended encoding");
        byte[] oldConditional=Arrays.copyOf(listWire(Collections.singletonList(conditional)),listWire(Collections.singletonList(conditional)).length-4);oldConditional[3]=(byte)0xfe;List<Dc2Ui.Quad> oldDecoded=Dc2Ui.loadQuads(new DataInputStream(new ByteArrayInputStream(oldConditional)),1);
        check(oldDecoded.get(0).nativeCondition==1&&oldDecoded.get(0).nativeTextOffset==-1,"prior-2 condition-only list decodes without manufacturing a code pointer");
        Dc2Ui.Button button=new Dc2Ui.Button();button.sprites.add(conditional);host.ui.buttons.add(button);host.ui.quadTemplates.get(1).add(conditional);byte[] savedUi=uiWire(host.ui);Dc2Ui decodedUi=new Dc2Ui();decodedUi.load(new DataInputStream(new ByteArrayInputStream(savedUi)));
        check(Arrays.equals(savedUi,uiWire(decodedUi))&&decodedUi.buttons.get(0).sprites.get(0).nativeCondition==1&&decodedUi.quadTemplates.get(1).get(0).nativeCondition==1,"conditional field survives UI, button-sprite and template nested codecs");
        decodedUi.quadTemplates.get(1).get(0).nativeCondition=0;check(host.ui.quadTemplates.get(1).get(0).nativeCondition==1,"nested template codec also owns separate quads");
        for(int rawCondition:new int[]{2,Integer.MIN_VALUE,Integer.MAX_VALUE}){Dc2Ui.Quad rawQuad=new Dc2Ui.Quad();rawQuad.nativeCondition=rawCondition;List<Dc2Ui.Quad> rawCopy=Dc2Ui.loadQuads(new DataInputStream(new ByteArrayInputStream(listWire(Collections.singletonList(rawQuad)))),1);check(rawCopy.get(0).nativeCondition==rawCondition,"Native condition preserves an arbitrary aliasedDWORD");host.pc1.cachedScenarioRead(0);check(!host.pc1.visible(rawQuad),"Every nonzero native condition obeys unread filtering");host.pc1.cachedScenarioRead(1);check(host.pc1.visible(rawQuad),"Read scenario admits all nonzero native conditions");}
        return count;
    }
    public static void main(String[] args)throws Exception{System.out.println("PASS "+run()+" PC1 conditional/loader assertions");}
}
