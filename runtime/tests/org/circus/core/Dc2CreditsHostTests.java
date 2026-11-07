package org.circus.core;

import java.io.*;
import java.util.*;

/** Original system12 owner, frozen front, whole-session restore and release. */
public final class Dc2CreditsHostTests {
    private static int checks;
    private static void check(boolean yes,String why){checks++;if(!yes)throw new AssertionError(why);}
    private static CircusResources data()throws IOException {
        final byte[] mes={0,0,0,0,0x63,0x33,28,12,0,0,0,0},names=new byte[20];
        GameResources files=new GameResources(new GameResources.Storage(){
            public Set<String> paths(){return new HashSet<>(Arrays.asList("AdvData/MES/START.MES","AdvData/DAT/NAMES.DAT"));}
            private byte[] bytes(String p){return p.toLowerCase(Locale.ROOT).endsWith("names.dat")?names:mes;}
            public byte[] read(String p,int limit){return bytes(p).clone();}
            public Archive.Source source(String p){final byte[] b=bytes(p);return new Archive.Source(){public long size(){return b.length;}public byte[] read(long at,int n)throws IOException{Binary.range(at,n,b.length);return Arrays.copyOfRange(b,(int)at,(int)at+n);}};}
        });return new CircusResources(files,Profiles.get("dc2-3363"));
    }
    private static PcHost h(Dc2Session s){return(PcHost)s.host;}
    private static byte[] hostBytes(PcHost host)throws IOException{ByteArrayOutputStream b=new ByteArrayOutputStream();host.save(new DataOutputStream(b));return b.toByteArray();}
    public static int run()throws Exception {
        checks=0;CircusResources data=data();Dc2Session s=new Dc2Session(data,TextCodec.identity());
        final UiCapture initial=UiCapture.pixels(1,1,new int[]{0xff123456}),finished=UiCapture.pixels(1,1,new int[]{0xffabcdef});final int[] captures={0};
        h(s).nativeFrameCapture=()->{captures[0]++;return h(s).dc2Credits.drawn?finished:initial;};
        s.host.scene.slots[1]="old-story";s.host.scene.slots[2]="old-actor";s.host.scene.publish(s.host.ui,0,0);
        check(s.vm.run(1)==MesVm.Status.WAIT_HOST&&s.vm.offset()==9&&h(s).dc2Credits.active,"system12 commits its operand and blocks on its own owner");
        check(captures[0]==1&&h(s).dc2CreditsUnderlay==initial,"credits capture the actual front before first draw");
        s.vm.signalHost(s.host.waitTicket(),MesVm.HostSignal.ADVANCE);check(h(s).dc2Credits.active&&h(s).dc2Credits.skipRequested()==0,"generic advance cannot become native mouse skip");
        s.vm.elapse(600);check(h(s).dc2Credits.drawn&&h(s).dc2Credits.page==0&&h(s).dc2Credits.row==24,"host polls credits once per elapsed call");
        byte[] active=s.snapshot();Dc2Session restored=new Dc2Session(data,TextCodec.identity());restored.restore(active);check(Arrays.equals(active,restored.snapshot())&&h(restored).dc2CreditsUnderlay.pixels().argb[0]==0xff123456,"whole session retains credits clock, old draw and frozen front");
        byte[] invalid=active.clone();invalid[20]^=1;MesVm prior=restored.vm;boolean refused=false;try{restored.restore(invalid);}catch(IOException expected){refused=true;}check(refused&&restored.vm==prior,"invalid credits session never publishes replacement VM");
        h(s).dc2Credits.input(1,2);check(h(s).dc2Credits.active,"native mouse input queues a deferred release");s.vm.elapse(50);
        check(!h(s).dc2Credits.active&&h(s).dc2Credits.drawn&&captures[0]==2,"completion captures its final draw before releasing the owner");
        check(h(s).dc2CreditsUnderlay==null&&s.host.uiCapture==finished&&s.host.scene.slots[1].equals("@ui-capture")&&s.host.scene.slots[2].isEmpty(),"completed front becomes current scene without retaining old story layers");
        check(s.vm.run(1)==MesVm.Status.HALTED,"released system12 continues to the following instruction");
        byte[] complete=s.snapshot();Dc2Session done=new Dc2Session(data,TextCodec.identity());done.restore(complete);check(Arrays.equals(complete,done.snapshot())&&h(done).dc2CreditsUnderlay==null,"released front serializes without reactivating the credits presenter");
        Dc2Session logical=new Dc2Session(data,TextCodec.identity());logical.vm.run(1);check(h(logical).dc2CreditsUnderlay!=null&&h(logical).dc2CreditsUnderlay.kind==UiCapture.LEGACY,"headless core retains a logical frozen front");
        for(int n=0;n<40&&h(logical).dc2Credits.active;n++)logical.vm.elapse(600000);
        check(!h(logical).dc2Credits.active&&logical.host.scene.slots[1].equals("ed/Gend_19")&&h(logical).dc2CreditsUnderlay==null,"headless natural completion uses the admitted last-page fallback");
        Dc2Session transfer=new Dc2Session(data,TextCodec.identity());transfer.vm.run(1);h(transfer).beforeTransfer(1,"START");check(!h(transfer).dc2Credits.active&&!h(transfer).dc2Credits.drawn&&h(transfer).dc2CreditsUnderlay==null,"ordinary transfer clears completed or abandoned credits lifetime");
        PcHost old=new PcHost(data,TextCodec.identity());byte[] current=hostBytes(old);ByteArrayOutputStream tail=new ByteArrayOutputStream();old.dc2Credits.save(new DataOutputStream(tail));UiCapture.save(new DataOutputStream(tail),old.dc2CreditsUnderlay);old.dc2CharacterEnding.save(new DataOutputStream(tail));UiCapture.save(new DataOutputStream(tail),old.dc2EndingUnderlay);old.dc2ActorMotion.save(new DataOutputStream(tail));old.dc2ActorChange.save(new DataOutputStream(tail));byte[] pch13=Arrays.copyOf(current,current.length-tail.size());int marker=-1;for(int n=0;n<pch13.length-3;n++)if(pch13[n]==0x50&&pch13[n+1]==0x43&&pch13[n+2]==0x48&&pch13[n+3]==0x41){marker=n;break;}check(marker>=0,"original owner declares new PCH17 tail");pch13[marker+3]=0x3d;old.load(new DataInputStream(new ByteArrayInputStream(pch13)));check(!old.dc2Credits.active&&old.dc2CreditsUnderlay==null,"PCH13 migration does not invent an active ending");
        return checks;
    }
    public static void main(String[] args)throws Exception{System.out.println("Dc2CreditsHostTests "+run()+" PASS");}
}
