package org.circus.core;

import java.io.*;
import java.util.*;

/** Native1C/41A0B0 integration over self-authored MES and syntheticRGB24.
 * Pixel constants come from actual4079A0 private-CPU kernel references. */
public final class Pc1ReturnIntegrationTests {
    private static int count;
    private static final int[] MENU={0xff112233,0xff387540,0xff583f94,0xff7f92a1};
    private static final int[] TARGET={0xffe1c080,0xff42d1a5,0xffec0393,0xff4d14b8};
    private static final int[][] FIRST={
        {0xff383f41,0xff398652,0xff733393,0xff757aa5},
        {0xff5f5d4f,0xff3b9765,0xff8f2893,0xff6c62a9},
        {0xff797159,0xff3da372,0xffa22193,0xff6653ac},
        {0xff938463,0xff3eae7f,0xffb41993,0xff5f43af}
    };
    private static final int[][] LAST={
        {0xffd8b87b,0xff3dcc9f,0xffe40393,0xff4d16b3},
        {0xffdbbb7d,0xff40cda1,0xffe70393,0xff4d15b6},
        {0xffd4b67b,0xff41cb9e,0xffe20693,0xff501bb6},
        {0xffc3a975,0xff40c396,0xffd70b93,0xff5325b4}
    };
    private static void check(boolean value,String why){count++;if(!value)throw new AssertionError(why);}
    private static byte[] join(byte[]...parts)throws IOException{ByteArrayOutputStream bytes=new ByteArrayOutputStream();for(byte[] part:parts)bytes.write(part);return bytes.toByteArray();}
    private static byte[] words(int opcode,int...args){byte[] raw=new byte[1+args.length*2];raw[0]=(byte)opcode;for(int n=0;n<args.length;n++){raw[n*2+1]=(byte)args[n];raw[n*2+2]=(byte)(args[n]>>>8);}return raw;}
    private static byte[] script(byte[]...code)throws IOException{return join(new byte[]{0,0,0,0,0x63,0x20},join(code),new byte[3]);}
    private static byte[] names()throws IOException{byte[] bytes=new byte[152];bytes[8]=1;byte[] name={'l','o','o','p','0'};System.arraycopy(name,0,bytes,12,name.length);return bytes;}
    private static int[] pixels(Pc1Runtime runtime)throws IOException{UiCapture front=runtime.returnFront();if(front==null)return null;UiCapture.Pixels pixels=front.pixels();check(pixels.width==2&&pixels.height==2,"native return preserves its frozen canvas dimensions");return pixels.argb;}
    private static final class Probe implements PcHost.NativeFrameCapture {
        final boolean redraw;PcHost host;int calls;
        Probe(boolean redraw){this.redraw=redraw;}
        public UiCapture capture()throws IOException{
            calls++;if(calls==1)return UiCapture.pixels(2,2,TARGET);if(calls==2)return UiCapture.pixels(2,2,MENU);
            if(redraw&&calls==3){if(!host.body.equals("CUR42"))throw new IOException("Caller raw log was not reconstructed before target capture");return UiCapture.pixels(2,2,TARGET);}
            throw new IOException("Unexpected recapture; wait-1 target must not be rebuilt from Unicode body");
        }
    }
    private static final class Fixture {
        final CircusResources resources;
        Fixture()throws IOException{this(new byte[0],true);}
        Fixture(byte[] retainedOperation,boolean nativeCommit)throws IOException{
            final Map<String,byte[]> files=new HashMap<>();files.put("AdvData/DAT/NAMES.DAT",names());
            files.put("AdvData/MES/START.mes",script(words(0x58,0,0,11,0),new byte[]{5,0,1},new byte[]{0x26,0,7,0,'m','e','n','u',0},words(0x58,0,1,42,0),words(0x58,0,2,43,0),retainedOperation,nativeCommit?new byte[]{0x21,0,0}:new byte[0],words(0x58,0,3,44,0)));
            files.put("AdvData/MES/menu.mes",script(words(0x58,0,0,77,0),new byte[]{5,(byte)255,0},new byte[]{0x1c,0,0}));
            GameResources data=new GameResources(new GameResources.Storage(){
                public Set<String> paths(){return files.keySet();}
                public byte[] read(String path,int limit)throws IOException{byte[] raw=files.get(path);if(raw==null)throw new FileNotFoundException(path);if(raw.length>limit)throw new IOException("Fixture limit");return raw.clone();}
                public Archive.Source source(String path)throws IOException{final byte[] raw=read(path,Integer.MAX_VALUE);return new Archive.Source(){public long size(){return raw.length;}public byte[] read(long offset,int length)throws IOException{Binary.range(offset,length,raw.length);return Arrays.copyOfRange(raw,(int)offset,(int)offset+length);}};}
            });resources=new CircusResources(data,Profiles.get("dcpc-2063"));
        }
        PcHost host(int speed,Probe probe)throws IOException{PcHost host=Pc1TestDefaults.bind(new PcHost(resources,TextCodec.identity()));host.write(99,12,speed);host.write(99,15,1);probe.host=host;host.nativeFrameCapture=probe;return host;}
        MesVm vm(PcHost host)throws IOException{return new MesVm(resources.scripts(),host,"START");}
    }
    private static final class Running {
        final Fixture fixture;final PcHost host;final MesVm vm;final Probe probe;final int returnPc,audioGeneration;
        Running(int speed)throws Exception{this(speed,new byte[0]);}
        Running(int speed,byte[] retainedOperation)throws Exception{this(speed,retainedOperation,true);}
        Running(int speed,byte[] retainedOperation,boolean nativeCommit)throws Exception{
            fixture=new Fixture(retainedOperation,nativeCommit);probe=new Probe(false);host=fixture.host(speed,probe);vm=fixture.vm(host);host.body="This Unicode adapter is not the frozen native bitmap";
            check(vm.run(3)==MesVm.Status.RUNNING&&host.pc1.inSystem()&&vm.scriptName().equals("menu"),"real26 captures caller with nativewait-1");returnPc=host.pc1.system().returnPoint().pc;
            check(host.pc1.system().fields().argument1==7,"26 saves native argument1=7 before return helper starts");
            check(vm.run(2)==MesVm.Status.RUNNING&&vm.variables.read(0,0)==77&&!host.audioActive[1],"menu keeps live ordinary changes and stops physicalSE independently of saved loop request");audioGeneration=host.audioGeneration[1];
            check(vm.run(1)==MesVm.Status.WAIT_HOST&&host.pc1.returning()&&host.pc1.inSystem()&&vm.scriptName().equals("START")&&vm.offset()==returnPc,"1C restores caller PC but blocks its execution until feedback commits");
            check(host.pc1.lastYieldOpcode()==-1,"blocking1C has not returned to dispatcher to publish last-yield yet");
        }
    }
    private static void complete(MesVm vm,PcHost host)throws Exception{for(int n=0;host.pc1.returning();n++){if(n>=16)throw new AssertionError("Native return exceeded16feedback intervals");vm.elapse(40);}}
    private static byte[] scene(PcHost host)throws IOException{ByteArrayOutputStream bytes=new ByteArrayOutputStream();host.scene.save(new DataOutputStream(bytes));return bytes.toByteArray();}
    private static void rejectRetainedProducer(byte[] raw,String operation)throws Exception{
        Running state=new Running(3,raw);complete(state.vm,state.host);check(state.vm.run(2)==MesVm.Status.RUNNING,"pure instructions before retained producer remain executable");
        // These known resources must survive preflight. The raw33 clear would
        // otherwise erase both actor slots;12/5E would publish or start timers.
        state.host.scene.slots[2]="known_actor";state.host.scene.slots[6]="known_overlay";byte[] before=scene(state.host),saved=state.vm.snapshot();int at=state.vm.offset();
        check(state.vm.run(1)==MesVm.Status.FAULT&&state.vm.error().contains("PC1 retained return framebuffer: unimplemented partial-paint producer "+operation),"actual native producer reports its exact unimplemented retained framebuffer boundary: "+operation);
        check(state.vm.offset()==at+raw.length&&Arrays.equals(before,scene(state.host))&&Arrays.equals(pixels(state.host.pc1),TARGET),"rejected "+operation+" keeps its already consumed native PC and preserves resource slots, scene/timers and frozen front");
        // Faulted VMs deliberately cannot be saved. Restore the byte-exact
        // pre-instruction snapshot, then independently reproduce the fault.
        Probe probe=new Probe(false);PcHost host=state.fixture.host(3,probe);MesVm vm=state.fixture.vm(host);vm.restore(saved);
        check(Arrays.equals(saved,vm.snapshot())&&Arrays.equals(before,scene(host))&&Arrays.equals(pixels(host.pc1),TARGET)&&probe.calls==0,"retained pre-instruction snapshot preserves exact bytes/resources/pixels without recapture: "+operation);
        check(vm.run(1)==MesVm.Status.FAULT&&vm.error().equals(state.vm.error())&&Arrays.equals(before,scene(host))&&Arrays.equals(pixels(host.pc1),TARGET),"freshVM reproduces the same precise producer fault before resource or pixel changes: "+operation);
    }
    public static int run()throws Exception{
        count=0;
        for(int speed=0;speed<4;speed++){
            Running state=new Running(speed);PcHost host=state.host;MesVm vm=state.vm;int frames=new int[]{16,8,4,2}[speed],increment=new int[]{1,2,4,8}[speed];
            check(Arrays.equals(pixels(host.pc1),FIRST[speed])&&!Arrays.equals(pixels(host.pc1),TARGET),"first immediate feedback is the native4079A0 menu-to-target ratio for speed"+speed);
            check(state.probe.calls==2,"wait-1 uses captured target rather than requesting Unicode reconstruction");
            for(int frame=1;frame<frames;frame++){
                vm.elapse(40);check(host.pc1.returning()&&host.pc1.inSystem()&&host.pc1.returnProgress()==Math.min(16,(frame+1)*increment),"each40ms starts exactly the next native feedback frame");
                check(host.pc1.nativeArgument1()==7&&host.audioGeneration[1]==state.audioGeneration&&!host.audioActive[1],"9CCA8C progress never overwrites C4 or restarts loopingSE before commit");
            }
            check(host.pc1.returnProgress()==16&&Arrays.equals(pixels(host.pc1),LAST[speed]),"last feedback frame still differs from final407880 exact copy");
            vm.elapse(39);check(host.pc1.returning()&&host.pc1.inSystem()&&Arrays.equals(pixels(host.pc1),LAST[speed]),"progress16 owns its complete final40ms interval");
            check(host.audioGeneration[1]==state.audioGeneration&&host.pc1.nativeArgument1()==7,"last39ms also retains native argument and deferred audio");
            vm.elapse(1);check(!host.pc1.returning()&&!host.pc1.inSystem()&&vm.status()==MesVm.Status.RUNNING&&vm.offset()==state.returnPc,"only final40th millisecond commits wait-1 return");
            check(Arrays.equals(pixels(host.pc1),TARGET)&&host.pc1.returnProgress()==16,"final published pixels are exact frozen target, independent of text adapter");
            check(host.audioGeneration[1]==state.audioGeneration+1&&host.audioActive[1]&&host.audioLoop[1]&&host.audio[1].equals("loop0"),"saved loopingSE submits exactly once after final exact target copy");
            vm.elapse(100000);check(host.audioGeneration[1]==state.audioGeneration+1&&Arrays.equals(pixels(host.pc1),TARGET),"committed bitmap survives time without replaying commit side effects");
        }
        Running late=new Running(0);late.vm.elapse(10000);check(late.host.pc1.returnProgress()==2&&late.host.pc1.returning(),"late platform tick cannot carry surplus time through several native iterations");late.vm.elapse(10000);check(late.host.pc1.returnProgress()==3,"each later tick samples a new40ms iteration instead of crediting old lateness");
        Running pending=new Running(3);pending.vm.elapse(17);int[] partial=pixels(pending.host.pc1);byte[] checkpoint=pending.vm.snapshot();Probe probe=new Probe(false);PcHost restored=pending.fixture.host(3,probe);MesVm resumed=pending.fixture.vm(restored);resumed.restore(checkpoint);
        check(Arrays.equals(checkpoint,resumed.snapshot())&&Arrays.equals(partial,pixels(restored.pc1))&&restored.pc1.returning()&&restored.pc1.inSystem(),"newVM pending snapshot is byte-exact and pixel-exact with its suspended caller");
        check(probe.calls==0&&restored.pc1.nativeArgument1()==7,"pending restore never replays26/capture or replaces external C4 with progress");
        int generation=restored.audioGeneration[1];complete(resumed,restored);check(probe.calls==0&&restored.audioGeneration[1]==generation+1&&Arrays.equals(pixels(restored.pc1),TARGET),"pending restore commits once using only its saved pixel owners");
        byte[] committed=resumed.snapshot();Probe afterProbe=new Probe(false);PcHost after=pending.fixture.host(3,afterProbe);MesVm afterVm=pending.fixture.vm(after);afterVm.restore(committed);
        check(Arrays.equals(committed,afterVm.snapshot())&&!after.pc1.inSystem()&&!after.pc1.returning()&&Arrays.equals(pixels(after.pc1),TARGET),"newVM committed snapshot keeps the surviving exact bitmap without a temporary native context");
        int committedGeneration=after.audioGeneration[1];check(afterVm.run(1)==MesVm.Status.RUNNING&&afterVm.variables.read(0,1)==42&&Arrays.equals(pixels(after.pc1),TARGET),"pure variable instruction cannot retire frozen native display");
        check(afterVm.run(1)==MesVm.Status.RUNNING&&afterVm.variables.read(0,2)==43&&Arrays.equals(pixels(after.pc1),TARGET),"another pure operation continues to preserve committed pixels");
        int paintSerial=after.scene.transitionSerial;after.scene.revision++;
        check(after.scene.transitionSerial==paintSerial&&Arrays.equals(pixels(after.pc1),TARGET),"cache invalidation alone cannot retire a native framebuffer without a reviewed full paint");
        byte[] afterActor=afterVm.snapshot();Probe actorProbe=new Probe(false);PcHost actorHost=pending.fixture.host(3,actorProbe);MesVm actorVm=pending.fixture.vm(actorHost);actorVm.restore(afterActor);
        check(Arrays.equals(afterActor,actorVm.snapshot())&&Arrays.equals(pixels(actorHost.pc1),TARGET)&&actorProbe.calls==0,"snapshot after cache invalidation preserves the current bytes and surviving bitmap owner");
        byte[] before21=scene(after);int at21=afterVm.offset();
        check(afterVm.run(1)==MesVm.Status.FAULT&&afterVm.error().contains("PC1 retained return framebuffer: unimplemented partial-paint producer dm.ui.commit")&&Arrays.equals(before21,scene(after))&&Arrays.equals(pixels(after.pc1),TARGET),"real21 draws a native background target, so its unimplemented retained pipeline faults before any pretend front publication");
        check(afterVm.offset()==at21+3&&after.audioGeneration[1]==committedGeneration&&afterVm.variables.read(0,3)==0,"rejected21 retains its real fetch advance without re-submitting loop audio or executing later caller instructions");
        check(actorVm.run(1)==MesVm.Status.FAULT&&actorVm.error().equals(afterVm.error())&&Arrays.equals(pixels(actorHost.pc1),TARGET),"newVM from the retained snapshot reproduces21 fault while preserving frozen pixels");
        // This is a portable renderer API boundary, not native21. No native
        // opcode in this fixture claims that its partial pipeline is complete.
        Running published=new Running(3,new byte[0],false);complete(published.vm,published.host);int publishedAudio=published.host.audioGeneration[1];
        check(Arrays.equals(pixels(published.host.pc1),TARGET),"portable publication fixture starts from a committed exact native target");published.host.scene.publish(published.host.ui,0,0);
        check(published.host.pc1.returnFront()==null&&published.host.pc1.returnPixels()==null,"explicit portable full-frame publish API replaces the retained display; this is not native21");
        check(published.vm.run(10)==MesVm.Status.HALTED&&published.vm.variables.read(0,1)==42&&published.vm.variables.read(0,2)==43&&published.vm.variables.read(0,3)==44&&published.host.pc1.returnProgress()==0,"after explicit portable publication the caller continues once and retires the old owner at dispatch");
        check(published.host.audioGeneration[1]==publishedAudio,"explicit portable publication does not repeat the committed loop sound");
        rejectRetainedProducer(new byte[]{0x33,'-',0},"pc.character_pair");
        rejectRetainedProducer(new byte[]{0x12,1,0},"pc.wait");
        rejectRetainedProducer(words(0x5e,40,50,0,2),"pc.scroll");
        Fixture scrolling=new Fixture();Probe scrollingProbe=new Probe(false);PcHost scrollingHost=scrolling.host(3,scrollingProbe);MesVm scrollingVm=scrolling.vm(scrollingHost);
        check(scrollingVm.run(2)==MesVm.Status.RUNNING,"active scroll preflight fixture stops before actual26");scrollingHost.scene.scrollStep=8;scrollingHost.scene.scrollPeriod=34;scrollingHost.scene.targetX=64;byte[] scrollScene=scene(scrollingHost);int beforeEntry=scrollingVm.offset();
        check(scrollingVm.run(1)==MesVm.Status.FAULT&&scrollingVm.error().contains("PC1 wait-1 return has an unimplemented partial-paint/timer framebuffer owner"),"native26 refuses unsupported active scroll before creating a frozen wait-1 context");
        check(!scrollingHost.pc1.inSystem()&&!scrollingHost.pc1.returning()&&scrollingHost.pc1.returnFront()==null&&scrollingProbe.calls==0&&scrollingVm.offset()==beforeEntry+9&&Arrays.equals(scrollScene,scene(scrollingHost)),"active scroll rejection retains26's consumed owned-string PC but does not capture, enter menu or mutate its renderer owner");
        // Real09 has actionable choices during return; every active input must
        // be blocked rather than accidentally accepting its restored button.
        Pc1HistoryTests.Fixture choice=new Pc1HistoryTests.Fixture(true);PcHost choiceHost=choice.host();Probe choiceProbe=new Probe(true);choiceProbe.host=choiceHost;choiceHost.nativeFrameCapture=choiceProbe;choiceHost.write(99,12,3);MesVm choiceVm=choice.vm(choiceHost);choiceVm.variables.write(0,0,7);
        for(int row=0;row<3;row++){check(choiceVm.run(100)==MesVm.Status.WAIT_HOST,"choice fixture reaches real4C");choiceVm.signalHost(choiceHost.waitTicket(),MesVm.HostSignal.TEXT_COMPLETE);check(choiceVm.run(1)==MesVm.Status.WAIT_HOST&&choiceHost.pc1.waitKind()==7,"choice fixture reaches real07");choiceVm.signalHost(choiceHost.waitTicket(),MesVm.HostSignal.ADVANCE);}
        check(choiceVm.run(10)==MesVm.Status.WAIT_HOST&&choiceHost.pc1.waitKind()==9&&choiceHost.ui.buttons.size()==5,"choice fixture establishes real09 with toolbar and two labels");int callerPc=choiceVm.offset();choiceVm.enterNativeSystem("menu",0);choiceVm.run(10);choiceVm.signalHost(choiceHost.waitTicket(),MesVm.HostSignal.TEXT_COMPLETE);
        check(choiceVm.run(1)==MesVm.Status.WAIT_HOST&&choiceHost.pc1.returning()&&choiceHost.pc1.inSystem()&&choiceHost.pc1.lastYieldOpcode()==-1,"ordinary09 return blocks before dispatcher last-yield and context retirement");
        byte[] blocked=choiceVm.snapshot();long ticket=choiceHost.waitTicket();int[] current=pixels(choiceHost.pc1);int voice=choiceHost.audioGeneration[5];
        check(!choiceVm.signalHost(ticket,MesVm.HostSignal.ADVANCE)&&!choiceVm.signalHost(ticket,MesVm.HostSignal.TEXT_COMPLETE),"advance/text acknowledgement cannot skip active feedback");
        check(!choiceVm.activateMenu(ticket,3)&&!choiceVm.activateGrid(ticket,0)&&choiceHost.pollMenuAction()==null,"menu/grid/timed-label input cannot jump into restored caller while returning");
        check(!choiceHost.pc1.historyPrevious()&&!choiceHost.pc1.historyNext()&&!choiceHost.pc1.closeHistory(),"history navigation cannot replace active native return pixels or journal");choiceHost.pc1.replayHistoryVoice();
        check(choiceHost.audioGeneration[5]==voice&&!choiceVm.returnSystemContext()&&choiceVm.offset()==callerPc,"active history voice/reentrant1C cannot mutate or release the suspended return owner");
        check(choiceVm.run(100)==MesVm.Status.WAIT_HOST&&Arrays.equals(blocked,choiceVm.snapshot())&&Arrays.equals(current,pixels(choiceHost.pc1)),"all blocked operations preserve exact byte/pixel state and VM cursor");
        complete(choiceVm,choiceHost);check(!choiceHost.pc1.inSystem()&&!choiceHost.pc1.returning()&&choiceVm.status()==MesVm.Status.WAIT_HOST&&choiceHost.pc1.lastYieldOpcode()==0x1c,"1C wait9 publishes its global last-yield only after commit");
        check(choiceHost.pc1.returnFront()==null&&choiceHost.ui.buttons.size()==5&&choiceHost.body.equals("CUR42"),"rebuilt07/09 target hands ownership back to normal live controls after final copy");
        check(choiceVm.activateMenu(choiceHost.waitTicket(),3)&&choiceVm.run(10)==MesVm.Status.HALTED&&choiceVm.variables.read(0,2)==11,"restored choice accepts input after commit and executes one native label branch");
        Running interrupted=new Running(3);int[] held=pixels(interrupted.host.pc1);int before=interrupted.host.audioGeneration[1];interrupted.host.exitRequested=true;interrupted.vm.elapse(40);
        check(interrupted.host.pc1.returning()&&interrupted.host.pc1.inSystem()&&Arrays.equals(held,pixels(interrupted.host.pc1))&&interrupted.host.audioGeneration[1]==before,"pump interruption cannot masquerade as successful copy/audio commit");
        return count;
    }
    public static void main(String[] args)throws Exception{System.out.println("PASS "+run()+" PC1 native return integration assertions");}
}
