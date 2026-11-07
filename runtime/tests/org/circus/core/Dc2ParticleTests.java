package org.circus.core;

import java.io.*;
import java.util.*;
import java.util.function.IntSupplier;

/** Goldens are raw native x86 slot-memory projections, not a second Java particle implementation. */
public final class Dc2ParticleTests {
    private static int count;
    private static void check(boolean value,String why){count++;if(!value)throw new AssertionError(why);}
    private static final class Rng implements IntSupplier {int state;Rng(int seed){state=seed;}public int getAsInt(){state=state*214013+2531011;return(state>>>16)&32767;}}
    // seed, strength, native kind, timer ticks, final RNG state, toggle, native 5000-byte slot SHA.
    private static final String[][] VECTORS={
        {"1","2","6","0","3946230366","0","6c6d231092b0a753817028ccf103b1ebe1c4691b80061857e75e87a56cbe3c7d"},
        {"1","2","6","1","3946230366","1","c86c39f9c5314b45a0cfd724da786eff8adec6f9b5eec28b0c16da29c7d7e275"},
        {"1","2","6","2","3946230366","0","c86c39f9c5314b45a0cfd724da786eff8adec6f9b5eec28b0c16da29c7d7e275"},
        {"1","2","6","3","3946230366","1","3e451b506b29f47065eeafdcbdf5e87ac28aba4bcf865000de39c8d600a0faab"},
        {"1","2","6","80","3946230366","0","1921156027bee6136e1fe1c4756497d0481e069588117a3336ab65e8cf0cb097"},
        {"1","2","6","400","1502690714","0","f9a5d81dba1a64328df8c558de9a95b74c570c7498ea3a799440d4e205dd43bd"},
        {"305419896","5","5","0","3777690253","0","574d27b341e8150130c42e2359964f8075853746a725293e6463f9f04b2ec596"},
        {"305419896","5","5","1","3777690253","1","cca407fb210835ebfea0e2e283b3997a17164bce40b8de475bd207515566e5c7"},
        {"305419896","5","5","2","3777690253","0","cca407fb210835ebfea0e2e283b3997a17164bce40b8de475bd207515566e5c7"},
        {"305419896","5","5","3","3777690253","1","0e85fcea98909128af3c1349603ab7ff0fb721702e9ff0649c4bcdb8f917fd47"},
        {"305419896","5","5","80","3777690253","0","8a1b80850938499920567d1c06c253017e139861dbb215502fe3fb718cd8d521"},
        {"305419896","5","5","400","2408449371","0","063d2d1b135ceb7ce6a009fda26722147fba5bc4d81d5fc6bade0ef90cb6e357"},
        {"4294967295","2","3","0","3910159813","0","99f833d54f22b203c83de4db4a9e77aaef796646f153223f58a5f7e1dacaae7a"},
        {"4294967295","2","3","1","3910159813","1","0a6e3266735506e7497a9f01a029979b35a7dfda0510b2aabe7441d34d9c42e7"},
        {"4294967295","2","3","2","3910159813","0","0a6e3266735506e7497a9f01a029979b35a7dfda0510b2aabe7441d34d9c42e7"},
        {"4294967295","2","3","3","3910159813","1","de575dd7c69d0c5ede55288ee5ae48a2fde9b9d2720535789199a9b49cfdfab4"},
        {"4294967295","2","3","80","3867411342","0","b5b7203f8607281a30a80da38ea9098bc635093d11be521c999dbfef858b5426"},
        {"4294967295","2","3","400","3728410376","0","3fc7c8fcd0bae1856983d84267061e5090912dd685ff56fdd4cabad0ebc6ae38"},
        {"0","0","6","0","0","0","7ca5bd879f393d9dd05b14f38add9c0fc6b67928f7f2d261b2e47a32ee8219e3"},
        {"0","0","6","1","0","1","7ca5bd879f393d9dd05b14f38add9c0fc6b67928f7f2d261b2e47a32ee8219e3"},
        {"0","0","6","2","0","0","7ca5bd879f393d9dd05b14f38add9c0fc6b67928f7f2d261b2e47a32ee8219e3"},
        {"0","0","6","3","0","1","7ca5bd879f393d9dd05b14f38add9c0fc6b67928f7f2d261b2e47a32ee8219e3"},
        {"0","0","6","80","0","0","7ca5bd879f393d9dd05b14f38add9c0fc6b67928f7f2d261b2e47a32ee8219e3"},
        {"0","0","6","400","0","0","7ca5bd879f393d9dd05b14f38add9c0fc6b67928f7f2d261b2e47a32ee8219e3"},
        {"2147483648","1","6","0","1108030709","0","687bd219df1d1c2ba032c17040fc0a2a55407b032c3f802c41cea97b26120224"},
        {"2147483648","1","6","1","1108030709","1","e3db0f4afc4b5743e0d6f1f8f3c784806773652358ebd49cc5e3ffb6e7062042"},
        {"2147483648","1","6","2","1108030709","0","e3db0f4afc4b5743e0d6f1f8f3c784806773652358ebd49cc5e3ffb6e7062042"},
        {"2147483648","1","6","3","1108030709","1","1f514386c96d42831e52f44f648512bd66a0b72a7eb4c5d870c295f02c4974bc"},
        {"2147483648","1","6","80","1108030709","0","48d06fab618091c8c7aa9579dc25d950e54aa4aea87012e9f8bd0b8050a1eec9"},
        {"2147483648","1","6","400","496686003","0","3adc0b53f6a0cd47bb6172d2e99ea93bf9cf6da92e393645b8aec113c993e626"}
    };
    private static final String COS_SHA="5fc89e4e961c8449584602bffd5e92369af5dc804d8535cc2a43b9ab9194c5e7";
    private static byte[] rawSlots(Dc2Particles value)throws IOException{ByteArrayOutputStream bytes=new ByteArrayOutputStream();for(int n=0;n<50;n++)for(int word:value.slot(n)){bytes.write(word);bytes.write(word>>>8);bytes.write(word>>>16);bytes.write(word>>>24);}return bytes.toByteArray();}
    private static byte[] saved(Dc2Particles value)throws IOException{ByteArrayOutputStream bytes=new ByteArrayOutputStream();value.save(new DataOutputStream(bytes));return bytes.toByteArray();}
    private static int active(Dc2Particles value){int result=0;for(int n=0;n<50;n++)if(value.slot(n)[2]!=0)result++;return result;}
    public static int run()throws Exception{
        count=0;
        for(String[] vector:VECTORS){Rng rng=new Rng((int)Long.parseLong(vector[0]));Dc2Particles particles=new Dc2Particles(rng);particles.command(Integer.parseInt(vector[2]),Integer.parseInt(vector[1]),true);particles.elapse(Long.parseLong(vector[3])*40,true,false,false);
            check(Binary.sha256(rawSlots(particles)).equals(vector[6]),"all50x25 native slot words match current PE x86 execution at ticks="+vector[3]+",seed="+vector[0]);
            check(Integer.toUnsignedLong(rng.state)==Long.parseLong(vector[4])&&particles.toggle()==Integer.parseInt(vector[5]),"native RNG consumption and alternate update flag match isolated native execution");
        }
        ByteArrayOutputStream cos=new ByteArrayOutputStream();for(int n=0;n<4096;n++){int word=Dc2Particles.cosineEntry(n);cos.write(word);cos.write(word>>>8);cos.write(word>>>16);cos.write(word>>>24);}
        check(Binary.sha256(cos.toByteArray()).equals(COS_SHA),"all4096 generated fixed-point cosine words match current42EEA0, rather than approximating with per-frame floating-point sine");
        for(int level:new int[]{0,1,2,3,4,5,255}){Dc2Particles p=new Dc2Particles(new Rng(1));p.command(6,level,true);int expected=level<5?new int[]{0,5,10,15,30}[level]:50;check(active(p)==expected,"native strength count table and >=5 branch "+level);}
        Rng rng=new Rng(99);Dc2Particles particles=new Dc2Particles(rng);particles.command(6,2,true);particles.elapse(45,true,false,false);int toggle=particles.toggle();int seed=rng.state;
        particles.command(255,7,true);byte[] frozen=rawSlots(particles);particles.elapse(92,true,false,false);
        check(Arrays.equals(frozen,rawSlots(particles))&&rng.state==seed&&particles.toggle()==toggle&&particles.remainder()==17,"rawFF busy suspends updates while40ms timer phase continues");
        particles.command(6,1,true);check(particles.busy()==0&&particles.remainder()==17&&particles.toggle()==toggle&&active(particles)==5,"same-kind command rebuilds slots and clears busy without restarting timer/toggle");
        particles.elapse(23,true,false,false);check(particles.toggle()==0,"the next preserved timer boundary consumes the native alternate skip frame");
        particles.command(0,99,true);int stoppedStrength=particles.strength();toggle=particles.toggle();seed=rng.state;particles.elapse(400,true,false,false);
        check(active(particles)==0&&particles.atlas().isEmpty()&&particles.timerActive()&&particles.strength()==stoppedStrength&&particles.toggle()==toggle&&rng.state==seed,"stop releases the texture/active flags but retains declared strength, timer and alternate flag");
        particles.command(6,2,false);check(!particles.loaded()&&active(particles)==0&&particles.kind()==6&&particles.strength()==2,"disabled start declares kind and strength without allocating texture or consuming RNG");
        particles.command(6,2,true);byte[] beforeDisabled=rawSlots(particles);particles.command(6,4,false);check(Arrays.equals(beforeDisabled,rawSlots(particles))&&particles.loaded()&&particles.strength()==4,"same-kind disabled command preserves previous particles and texture");
        particles.elapse(400,false,false,false);check(Arrays.equals(beforeDisabled,rawSlots(particles)),"effect gate20 pauses timer work without clearing frozen particles");
        byte[] beforeBusy=rawSlots(particles);particles.elapse(400,true,true,false);particles.elapse(400,true,false,true);check(Arrays.equals(beforeBusy,rawSlots(particles)),"native busy and batch dirty each suppress particle update");
        particles.command(5,2,true);check(particles.atlas().equals("system\\SAKU_YUKI01"),"kind5 has the same native sprite kernel and its own atlas");particles.command(6,2,true);check(particles.atlas().equals("system\\SAKU_YUKI02"),"kind change releases prior atlas and binds kind6 source");
        Dc2Particles split=new Dc2Particles(new Rng(123)),large=new Dc2Particles(new Rng(123));split.command(6,5,true);large.command(6,5,true);for(int n=0;n<12345;n++)split.elapse(1,true,false,false);large.elapse(12345,true,false,false);
        check(Arrays.equals(saved(split),saved(large)),"large elapse preserves native timer residues, every update and respawn ordering");
        Rng restoreRng=new Rng(777);Dc2Particles original=new Dc2Particles(restoreRng);original.command(6,5,true);original.elapse(1234,true,false,false);byte[] state=saved(original);Rng loadedRng=new Rng(restoreRng.state);Dc2Particles loaded=Dc2Particles.load(new DataInputStream(new ByteArrayInputStream(state)),loadedRng);original.elapse(17003,true,false,false);loaded.elapse(17003,true,false,false);
        check(Arrays.equals(saved(original),saved(loaded))&&restoreRng.state==loadedRng.state,"full state plus the owning RNG seed resumes the same long-running wrap and animation trajectory");
        Dc2Scene scene=new Dc2Scene();Dc2Ui ui=new Dc2Ui();scene.nativeParticles=loaded;byte[] captured=rawSlots(loaded);UiCapture capture=SceneCaptures.legacy(scene.shown,scene,ui,null,true,null);loaded.elapse(800,true,false,false);SceneCaptures.Legacy decoded=new SceneCaptures.Legacy(capture.input());
        check(decoded.particles!=null&&Arrays.equals(captured,rawSlots(decoded.particles))&&!Arrays.equals(captured,rawSlots(loaded)),"logical capture freezes the full particle state rather than following later live clocks or RNG");
        scene.nativeParticles=null;UiCapture legacy=SceneCaptures.legacy(scene.shown,scene,ui,null,true,null);DataInputStream old=legacy.input();SceneCaptures.Legacy oldDecoded=new SceneCaptures.Legacy(old);check(oldDecoded.particles==null&&old.available()==0,"non-DC2 legacy capture retains its original unextended payload");
        return count;
    }
    public static void main(String[] args)throws Exception{System.out.println("PASS "+run()+" DC2 native particle assertions");}
}
