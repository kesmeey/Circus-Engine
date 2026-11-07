package org.circus.core;

import java.io.*;
import java.util.*;

/** PP presentation commands must block the real VM and survive a CVS2 restore. */
public final class PpPresentationHostTests {
    private static int checks;
    private static void check(boolean yes,String why){checks++;if(!yes)throw new AssertionError(why);}
    private static void integer(ByteArrayOutputStream b,int n){for(int i=0;i<4;i++)b.write(n>>>(i*8));}
    private static byte[] names()throws IOException{
        ByteArrayOutputStream b=new ByteArrayOutputStream();integer(b,0);integer(b,1);byte[] row=new byte[48];byte[] name="BGM/test".getBytes(Binary.SJIS);System.arraycopy(name,0,row,0,name.length);b.write(row);for(int i=0;i<3;i++)integer(b,0);return b.toByteArray();
    }
    private static byte[] archive()throws IOException{
        ByteArrayOutputStream b=new ByteArrayOutputStream();integer(b,3);for(int i=0;i<3;i++){integer(b,220+i);integer(b,1);}for(int i=0;i<3;i++){byte[] row=new byte[56];row[0]=(byte)('A'+i);b.write(row);integer(b,220+i);integer(b,1);}b.write(new byte[3]);return b.toByteArray();
    }
    private static CircusResources resources()throws IOException{
        final Map<String,byte[]> map=new HashMap<>();map.put("AdvData/GRP/NAMES.DAT",names());map.put("AdvData/GRP/ED/GED.PCK",archive());
        GameResources files=new GameResources(new GameResources.Storage(){
            public Set<String> paths(){return map.keySet();}
            public byte[] read(String p,int limit)throws IOException{byte[] b=map.get(p);if(b.length>limit)throw new IOException("Fixture size");return b.clone();}
            public Archive.Source source(String p){final byte[] b=map.get(p);return new Archive.Source(){public long size(){return b.length;}public byte[] read(long at,int length)throws IOException{Binary.range(at,length,b.length);return Arrays.copyOfRange(b,(int)at,(int)at+length);}};}
        });return new CircusResources(files,Profiles.get("dc3pp-9872"));
    }
    private static MesScript script()throws IOException{return new MesScript("pp-roll",new byte[]{0,0,0,0,0x72,(byte)0x98,0,28,(byte)210,0,0,0,0},Profiles.get("dc3pp-9872"));}
    private static MesVm start(CircusResources resources,Dc3Host host)throws IOException{
        MesScript script=script();MesVm vm=new MesVm(name->script,host,script.name);host.scene.slots[20].resource="ED/obsolete.PCK";host.command("image.load",new int[]{20},"ED/GED.PCK".getBytes(Binary.SJIS));
        check(vm.run(1)==MesVm.Status.WAIT_HOST&&vm.offset()==10,"210 commits its PC and blocks the real VM");
        check(host.ppScrollCredits.archive().equals("ED/GED.PCK")&&host.ppScrollCredits.strips()==3,"210 uses the just-loaded slot20, rather than stale published data");
        check(host.read(100,29)==0&&host.remainingMillis()>0,"starting a roll does not mark it completed");return vm;
    }
    private static byte[] hostState(Dc3Host host)throws IOException{ByteArrayOutputStream b=new ByteArrayOutputStream();host.save(new DataOutputStream(b));return b.toByteArray();}
    public static int run()throws Exception{
        checks=0;CircusResources resources=resources();Dc3Host host=new Dc3Host(resources,TextCodec.identity(),true);MesVm vm=start(resources,host);
        check(!vm.signalHost(host.waitTicket()+1,MesVm.HostSignal.ADVANCE)&&!vm.signalHost(host.waitTicket(),MesVm.HostSignal.MEDIA_COMPLETE),"stale input and media completion cannot finish a roll");
        vm.elapse(150000);byte[] saved=vm.snapshot();Dc3Host restored=new Dc3Host(resources,TextCodec.identity(),true);MesScript code=script();MesVm copy=new MesVm(name->code,restored,code.name);copy.restore(saved);
        check(Arrays.equals(saved,copy.snapshot())&&restored.ppScrollCredits.offset()==300&&restored.waiting(),"CVS2 preserves a partially visible pair of roll strips");
        vm.elapse(150000);copy.elapse(150000);check(host.ppScrollCredits.finishing()&&host.read(100,29)==0&&vm.status()==MesVm.Status.WAIT_HOST,"EOF discovery still draws and leaves completion flag untouched");
        check(Arrays.equals(vm.snapshot(),copy.snapshot()),"restored roll has the same future EOF state");
        vm.elapse(1);copy.elapse(1);check(host.read(100,29)==1&&!host.waiting()&&vm.run(1)==MesVm.Status.HALTED,"the next update sets100:29 and releases script execution");
        check(restored.read(100,29)==1&&copy.run(1)==MesVm.Status.HALTED,"restored roll also returns to its next instruction");
        Dc3Host skip=new Dc3Host(resources,TextCodec.identity(),true);MesVm skipVm=start(resources,skip);skip.command("pp.audio.music",new int[]{0,1,0},new byte[0]);int generation=skip.audioGeneration[0];
        check(skipVm.signalHost(skip.waitTicket(),MesVm.HostSignal.ADVANCE)&&skip.read(100,29)==1&&skip.read(99,244)==-1,"ADVANCE starts native BGM fade and marks the roll completed");
        check(skip.audioIsActive(0)&&skip.audioGeneration[0]==generation,"roll skip retains audible buffer during fade");skip.elapse(1000);check(!skip.audioIsActive(0)&&skipVm.run(1)==MesVm.Status.HALTED,"fade eventually releases the skipped roll's music");
        // Explicit pre-scroll PP40 wire conversion checks compatibility of the old tail.
        Dc3Host empty=new Dc3Host(resources,TextCodec.identity(),true);byte[] current=hostState(empty);ByteArrayOutputStream tail=new ByteArrayOutputStream();empty.ppScrollCredits.save(new DataOutputStream(tail));byte[] old=Arrays.copyOf(current,current.length-tail.size());old[3]=0x40;
        Dc3Host reused=new Dc3Host(resources,TextCodec.identity(),true);reused.ppScrollCredits.start("ED/GED.PCK",3,0);reused.load(new DataInputStream(new ByteArrayInputStream(old)));check(!reused.ppScrollCredits.active()&&reused.ppScrollCredits.archive().isEmpty(),"loading PP40 clears any prior scroll owner");
        host.command("scene.effect",new int[]{4,0x42},new byte[0]);check(host.scene.petals.pp4()!=null&&host.scene.petals.pp4().sprites().size()==10,"PP effect4 reaches its independent particle owner");
        int[] random=host.scene.petals.pp4().randomWords();host.write(99,10,0);host.command("scene.effect",new int[]{4,0x42},new byte[0]);check(Arrays.equals(random,host.scene.petals.pp4().randomWords()),"PP preference gate skips reinitialization and consumes no RNG");host.command("scene.effect",new int[]{4,0},new byte[0]);check(host.scene.petals.pp4().sprites().isEmpty(),"zero strength releases existing PP particles even when preference is disabled");
        boolean rejected=false;try{new Dc3Host(null,TextCodec.identity(),true).command("scene.effect",new int[]{4,0x42},new byte[0]);}catch(IOException expected){rejected=true;}check(rejected,"DD never inherits PP effect4");
        return checks;
    }
    public static void main(String[] args)throws Exception{System.out.println("PpPresentationHostTests "+run()+" PASS");}
}
