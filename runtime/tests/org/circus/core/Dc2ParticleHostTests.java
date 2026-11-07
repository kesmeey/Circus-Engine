package org.circus.core;

import java.io.*;
import java.util.*;

/** Raw native2A/2D/34/4A/4B, dispatcher-batch boundaries and PCH7 restoration. */
public final class Dc2ParticleHostTests {
    private static int count;
    private static void check(boolean value,String why){count++;if(!value)throw new AssertionError(why);}
    private static void string(ByteArrayOutputStream out,int op,String text)throws IOException{out.write(op);byte[] value=text.getBytes(Binary.SJIS);if(op==0x4a||op==0x4b)for(int n=0;n<value.length;n++)value[n]=(byte)((value[n]==' '?36:value[n]&255)-32);out.write(value);out.write(0);}
    private static byte[] code(boolean voice,boolean adjacent)throws IOException{ByteArrayOutputStream out=new ByteArrayOutputStream();out.write(new byte[]{0,0,0,0,0x63,0x33,0x2a,6,2});string(out,0x2d,"new");if(voice)string(out,0x34,"Y_OTO_000");string(out,0x4a,"speaker");if(!adjacent)out.write(new byte[]{0x2a,(byte)255,0});string(out,0x4b,"new-body");out.write(new byte[]{0,0,0});return out.toByteArray();}
    private static final class Fixture {
        final PcHost host;final MesVm vm;final MesScript script;
        Fixture(byte[] code,int seed)throws IOException{
            final byte[] names=new byte[208];names[16]=1;System.arraycopy("START".getBytes(Binary.SJIS),0,names,20,5);
            GameResources files=new GameResources(new GameResources.Storage(){
                public Set<String> paths(){return Collections.singleton("AdvData/DAT/NAMES.DAT");}
                public byte[] read(String path,int limit){return names.clone();}
                public Archive.Source source(String path){return new Archive.Source(){public long size(){return names.length;}public byte[] read(long start,int length)throws IOException{Binary.range(start,length,names.length);return Arrays.copyOfRange(names,(int)start,(int)start+length);}};}
            });Profile profile=Profiles.get("dc2-3363");host=Pc1TestDefaults.bind(new PcHost(new CircusResources(files,profile),TextCodec.identity(),seed));script=new MesScript("START",code,profile);vm=new MesVm(name->script,host,script.name);host.write(99,12,2);
            host.position(script,6);host.scene.slots[1]="old";host.scene.publish(host.ui,0,0);host.body="old body";host.speaker="old speaker";
        }
    }
    private static byte[] particles(PcHost host)throws IOException{ByteArrayOutputStream out=new ByteArrayOutputStream();host.scene.nativeParticles.save(new DataOutputStream(out));return out.toByteArray();}
    public static int run()throws Exception{
        count=0;byte[] bodyCode=code(false,true);Fixture f=new Fixture(bodyCode,1);
        check(f.vm.run(1)==MesVm.Status.RUNNING&&f.host.scene.nativeParticles.kind()==6&&f.host.scene.nativeParticles.atlas().endsWith("SAKU_YUKI02"),"raw2A06 selects the proven native kernel and resource");
        ByteArrayOutputStream nativeSlots=new ByteArrayOutputStream();for(int n=0;n<50;n++)for(int word:f.host.scene.nativeParticles.slot(n)){nativeSlots.write(word);nativeSlots.write(word>>>8);nativeSlots.write(word>>>16);nativeSlots.write(word>>>24);}check(Binary.sha256(nativeSlots.toByteArray()).equals("6c6d231092b0a753817028ccf103b1ebe1c4691b80061857e75e87a56cbe3c7d"),"injected startup tick1 reaches the independently interpreted native5000-byte slot golden through rawVM dispatch");
        int following=(int)3946230366L*214013+2531011;f.host.command("pc.random",new int[]{100},new byte[0]);check(f.host.read(99,2)==((following>>>16)&32767)%100,"script random consumes the next value from the same native CRT stream used by particle initialization");
        f.vm.elapse(39);check(f.host.scene.nativeParticles.toggle()==0,"40ms timer retains its first partial interval");f.vm.elapse(1);check(f.host.scene.nativeParticles.toggle()==1,"timer updates on its first40ms callback");
        byte[] beforeDirty=particles(f.host);check(f.vm.run(1)==MesVm.Status.RUNNING&&f.host.frozenOriginalBatch()!=null,"raw2D freezes the old displayed frame and begins one native draw batch");
        f.vm.elapse(80);check(f.host.scene.nativeParticles.toggle()==1,"dirty background suppresses both native timer callbacks rather than treating VM budget exit as dispatcher reentry");
        MesVm.Status speakerStatus=f.vm.run(1);check(speakerStatus==MesVm.Status.RUNNING&&f.host.speaker.isEmpty(),"raw4A stores the name without publishing it over the old front buffer: "+speakerStatus+" speaker="+f.host.speaker+" "+f.vm.error());
        check(f.vm.run(1)==MesVm.Status.WAIT_HOST&&f.host.sceneCommandDeferred()&&f.host.scene.nativeMotion.prepareOnly,"raw4B enters real41C440 preparation");
        check(f.host.body.isEmpty()&&!f.host.presentationWaiting&&f.host.history.entries.isEmpty()&&f.host.read(98,0)==0,"new text, history and scenario unlock do not occur before the native prep barrier");
        check(f.host.scene.nativeMotion.draws().size()==1&&f.host.scene.nativeMotion.draws().get(0).alpha==8,"native speed2 applies its first8/16 preparation feedback immediately");
        f.vm.elapse(159);check(f.host.waiting()&&f.host.history.entries.isEmpty()&&f.host.scene.nativeMotion.prepareProgress==16,"final prep image still owns its last40ms interval");
        byte[] saved=f.vm.snapshot();Fixture restored=new Fixture(bodyCode,99);restored.vm.restore(saved);
        check(Arrays.equals(saved,restored.vm.snapshot())&&restored.host.sceneCommandDeferred(),"PCH7 roundtrip preserves deferred command, source PC, captures, particles, timer phase and RNG seed");
        f.vm.elapse(1);restored.vm.elapse(1);check(!f.host.waiting()&&f.host.body.isEmpty()&&f.host.history.entries.isEmpty(),"elapse finishes native drawing without publishing IO or text side effects");
        byte[] complete=f.vm.snapshot();Fixture completed=new Fixture(bodyCode,987);completed.vm.restore(complete);
        for(Fixture target:new Fixture[]{f,restored,completed}){
            MesVm.Status commit=target.vm.run(1);check(commit==MesVm.Status.WAIT_HOST&&target.host.body.equals("new-body")&&target.host.speaker.equals("speaker"),"beforeInstruction commits the already-fetched body after preparation: "+commit+" body="+target.host.body+" speaker="+target.host.speaker+" "+target.vm.error());
            check(target.host.read(98,0)==1&&target.host.history.entries.size()==1&&target.host.history.entries.get(0).pc==bodyCode.length-13,"history/source-PC and unlock are committed exactly once after prep");
            target.vm.run(100);check(target.host.history.entries.size()==1,"repeated polling cannot replay publication side effects");
            target.vm.signalHost(target.host.waitTicket(),MesVm.HostSignal.TEXT_COMPLETE);check(target.vm.run(10)==MesVm.Status.HALTED,"resumption reaches the following raw stop without executing the body again");
        }
        Fixture large=new Fixture(bodyCode,3),small=new Fixture(bodyCode,3);large.vm.run(100);small.vm.run(100);large.vm.elapse(153);for(int n=0;n<153;n++)small.vm.elapse(1);
        check(Arrays.equals(large.vm.snapshot(),small.vm.snapshot()),"large and1ms elapse retain identical native feedback order and particle suspension state");
        Fixture voice=new Fixture(code(true,true),1);check(voice.vm.run(100)==MesVm.Status.WAIT_HOST&&voice.host.audioGeneration[5]==0&&!voice.host.presentationWaiting,"native34 dirty prep does not start its voice or publish following text early");
        voice.vm.elapse(160);voice.vm.run(100);check(voice.host.audioGeneration[5]==1&&voice.host.audio[5].equals("Y_OTO_000")&&voice.host.history.entries.size()==1&&voice.host.history.entries.get(0).voice.equals("Y_OTO_000"),"normal voice starts after its own barrier; following4B consumes the cleared batch without a second prep");
        check(voice.host.scene.nativeMotion==null&&voice.host.presentationWaiting,"body enters native text wait after the completed voice prep");
        Fixture separated=new Fixture(code(false,false),5);separated.vm.run(100);separated.vm.elapse(160);separated.vm.run(10);check(separated.host.speaker.isEmpty(),"4B uses a speaker only when4A is the immediately preceding native instruction");
        Fixture old=new Fixture(bodyCode,1);old.vm.run(100);ByteArrayOutputStream oldWire=new ByteArrayOutputStream();old.host.save(new DataOutputStream(oldWire));byte[] wire=oldWire.toByteArray();boolean found=false;for(int n=0;n<wire.length-4;n++)if(wire[n]==0x50&&wire[n+1]==0x43&&wire[n+2]==0x48&&wire[n+3]==0x41){wire[n+3]=0x36;found=true;break;}check(found,"DC2 host writes explicit PCH17 marker");
        boolean refused=false;try{new Fixture(bodyCode,1).host.load(new DataInputStream(new ByteArrayInputStream(wire)));}catch(IOException expected){refused=expected.getMessage().contains("pre-PCH7");}check(refused,"older PCH6 is explicitly rejected instead of inventing missing particle/RNG/batch state");
        PcHost h=new Fixture(bodyCode,10).host;h.command("pc.effect",new int[]{6,2},new byte[0]);h.elapse(40);byte[] frozen=particles(h);h.write(99,10,0);h.elapse(80);check(!Arrays.equals(frozen,particles(h)),"native particle update does not inherit the unrelated generic99:10 gate");h.write(99,20,0);byte[] stopped=particles(h);h.elapse(80);check(Arrays.equals(stopped,particles(h)),"native99:20 disables timer work while retaining atlas and slots");
        boolean rain=false;try{h.command("pc.effect",new int[]{2,2},new byte[0]);}catch(IOException e){rain=e.getMessage().contains("412380")&&e.getMessage().contains("START@0x");}check(rain,"unreviewed native rain remains a precise failure");
        ByteArrayOutputStream actorCode=new ByteArrayOutputStream();actorCode.write(new byte[]{0,0,0,0,0x63,0x33,0x2a,6,2});string(actorCode,0x2d,"new");string(actorCode,0x32,"actor");string(actorCode,0x4b,"text");actorCode.write(new byte[]{0,0,0});Fixture actor=new Fixture(actorCode.toByteArray(),1);check(actor.vm.run(100)==MesVm.Status.WAIT_HOST&&actor.host.dc2ActorChange.preparing&&actor.host.sceneCommandDeferred()&&actor.host.history.entries.isEmpty(),"native32 dirty branch owns preparation before its independent actor fade");actor.vm.elapse(160);check(!actor.host.dc2ActorChange.preparing&&actor.host.dc2ActorChange.active&&actor.host.history.entries.isEmpty(),"mask0F preparation completes before actor phase5 and body publication");actor.vm.elapse(0);actor.vm.elapse(40);actor.vm.elapse(40);check(!actor.host.dc2ActorChange.active&&actor.vm.run(100)==MesVm.Status.WAIT_HOST&&actor.host.history.entries.size()==1,"actor fade releases before the following actual4B commits history once");
        return count;
    }
    public static void main(String[] args)throws Exception{System.out.println("PASS "+run()+" DC2 particle host assertions");}
}
