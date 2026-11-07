package org.circus.core;

import java.io.*;
import java.util.*;

/** DC2 character10/11 dispatch and loader-cached99:251, not a directory index. */
public final class Dc2EndingHostTests {
    private static int checks;
    private static void check(boolean yes,String why){checks++;if(!yes)throw new AssertionError(why);}
    private static void integer(ByteArrayOutputStream b,int v){for(int n=0;n<4;n++)b.write(v>>>(8*n));}
    private static CircusResources data()throws IOException {
        final byte[] mes={0,0,0,0,0x63,0x33,28,10,4,4,0,0,28,11,0,0,0,0};ByteArrayOutputStream b=new ByteArrayOutputStream();for(int n=0;n<4;n++)integer(b,0);integer(b,2);for(String s:new String[]{"START","NEXT"}){byte[] row=new byte[188],name=s.getBytes("US-ASCII");System.arraycopy(name,0,row,0,name.length);b.write(row);}final byte[] names=b.toByteArray();
        GameResources f=new GameResources(new GameResources.Storage(){public Set<String> paths(){return new HashSet<>(Arrays.asList("AdvData/MES/START.MES","AdvData/MES/NEXT.MES","AdvData/DAT/NAMES.DAT"));}private byte[] bytes(String p){return p.toLowerCase(Locale.ROOT).endsWith("names.dat")?names:mes;}public byte[] read(String p,int limit){return bytes(p).clone();}public Archive.Source source(String p){final byte[] bytes=bytes(p);return new Archive.Source(){public long size(){return bytes.length;}public byte[] read(long at,int n)throws IOException{Binary.range(at,n,bytes.length);return Arrays.copyOfRange(bytes,(int)at,(int)at+n);}};}});return new CircusResources(f,Profiles.get("dc2-3363"));
    }
    private static PcHost h(Dc2Session s){return(PcHost)s.host;}
    private static byte[] bytes(PcHost h)throws IOException{ByteArrayOutputStream b=new ByteArrayOutputStream();h.save(new DataOutputStream(b));return b.toByteArray();}
    public static int run()throws Exception {
        checks=0;CircusResources d=data();Dc2Session s=new Dc2Session(d,TextCodec.identity());for(int n=0;n<24;n++)s.vm.variables.write(6,50+n,16);
        UiCapture initial=UiCapture.pixels(1,1,new int[]{0xff112233}),last=UiCapture.pixels(1,1,new int[]{0xffaabbcc});final int[] captures={0};h(s).nativeFrameCapture=()->{captures[0]++;return h(s).dc2CharacterEnding.drawn?last:initial;};
        check(s.vm.run(1)==MesVm.Status.RUNNING&&s.vm.offset()==9&&h(s).dc2CharacterEnding.prepared&&!h(s).waiting(),"system10 prepares the independent owner without entering a modal wait");
        check(captures[0]==1&&h(s).dc2EndingUnderlay==initial&&h(s).dc2CharacterEnding.clearIndex()==5,"prepare captures front and mode4 selects Anzu clear100:5");
        s.vm.elapse(7000);check(h(s).dc2CharacterEnding.elapsed()==7000&&!h(s).dc2CharacterEnding.drawn,"prepared clock runs before system11 without drawing");
        check(s.vm.run(2)==MesVm.Status.WAIT_HOST&&s.vm.offset()==15&&h(s).dc2CharacterEnding.active&&h(s).dc2CharacterEnding.elapsed()==7000,"checkpoint then11 begins its wait without resetting10 clock");
        byte[] saved=s.snapshot();Dc2Session copy=new Dc2Session(d,TextCodec.identity());copy.restore(saved);check(Arrays.equals(saved,copy.snapshot())&&h(copy).dc2EndingUnderlay.pixels().argb[0]==0xff112233,"PCH15 restores active clock/durations and matching frozen front");
        s.vm.signalHost(s.host.waitTicket(),MesVm.HostSignal.ADVANCE);check(!h(s).dc2CharacterEnding.skipRequested(),"generic ADVANCE does not become a native mouse ending skip");s.vm.elapse(0);check(h(s).dc2CharacterEnding.drawWhiteAlpha==15,"host performs one native alpha poll per elapsed call");
        h(s).dc2CharacterEnding.input(1,2);check(!h(s).dc2CharacterEnding.skipRequested(),"route clear must equal1, not any nonzero flag");h(s).dc2CharacterEnding.input(2,1);check(h(s).dc2CharacterEnding.active,"eligible mouse input defers release to next poll");s.vm.elapse(50);
        check(!h(s).dc2CharacterEnding.active&&captures[0]==2&&h(s).dc2EndingUnderlay==null&&s.host.uiCapture==last&&s.host.scene.slots[1].equals("@ui-capture"),"completion captures old draw then publishes front and releases presenter");check(s.vm.run(1)==MesVm.Status.HALTED,"system11 resumes only after its owner releases");
        h(copy).beforeTransfer(1,"NEXT");check(!h(copy).dc2CharacterEnding.active&&!h(copy).dc2CharacterEnding.drawn&&h(copy).dc2EndingUnderlay==null,"normal transfer clears character ending lifetime");
        PcHost cache=h(copy);check(cache.read(99,251)==0&&cache.read(98,1)==0,"unseen NEXT caches0 despite its nonzero catalog index and is not prematurely marked seen");cache.write(98,1,1);check(cache.read(99,251)==0,"later seen writes do not rewrite the current load's cached byte");cache.beforeTransfer(2,"START");check(cache.read(99,251)==0,"mode2 retains cached byte");cache.beforeTransfer(3,"NEXT");check(cache.read(99,251)==1,"mode3 samples prior read byte independently of its catalog index");cache.write(98,1,0);check(cache.read(99,251)==1,"cached1 remains stable after live seen table changes");cache.beforeTransfer(1,"MISSING");check(cache.read(99,251)==0,"unknown native catalog entry yields0");
        PcHost legacy=new PcHost(d,TextCodec.identity());byte[] modern=bytes(legacy);ByteArrayOutputStream tail=new ByteArrayOutputStream();legacy.dc2CharacterEnding.save(new DataOutputStream(tail));UiCapture.save(new DataOutputStream(tail),legacy.dc2EndingUnderlay);legacy.dc2ActorMotion.save(new DataOutputStream(tail));legacy.dc2ActorChange.save(new DataOutputStream(tail));byte[] old=Arrays.copyOf(modern,modern.length-tail.size());int marker=-1;for(int n=0;n<old.length-3;n++)if(old[n]==0x50&&old[n+1]==0x43&&old[n+2]==0x48&&old[n+3]==0x41){marker=n;break;}check(marker>=0,"new original host declares PCH17");old[marker+3]=0x3e;legacy.load(new DataInputStream(new ByteArrayInputStream(old)));check(!legacy.dc2CharacterEnding.prepared&&legacy.dc2EndingUnderlay==null,"PCH14 retains prior owners without inventing prepared10");
        cache.skipAllowed=true;cache.motionSkipPressed=true;int generation=cache.dc2RapidResetGeneration();Dc2SystemCommands.command(cache,new int[]{40,0});check(cache.dc2RapidResetGeneration()==generation+1&&!cache.motionSkipPressed&&cache.skipAllowed,"system40 clears rapid input without changing skip permission");byte[] reset=bytes(cache);PcHost resetCopy=new PcHost(d,TextCodec.identity());resetCopy.load(new DataInputStream(new ByteArrayInputStream(reset)));check(resetCopy.dc2RapidResetGeneration()==generation+1,"portable rapid-reset generation survives current host codec");
        return checks;
    }
    public static void main(String[] a)throws Exception{System.out.println("Dc2EndingHostTests "+run()+" PASS");}
}
