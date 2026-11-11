package org.circus.core;

import java.io.*;
import java.util.*;

/** PH ED completion query follows real audio callbacks, including stale guards. */
public final class Dc4AudioStatusTests {
    private static int checks;
    private static void check(boolean ok,String why){checks++;if(!ok)throw new AssertionError(why);}
    private static Dc4Host host()throws IOException{
        byte[] names=new byte[20];GameResources files=new GameResources(new GameResources.Storage(){
            public Set<String> paths(){return Collections.singleton("AdvData/GRP/NAMES.DAT");}public byte[] read(String path,int limit){return names.clone();}
            public Archive.Source source(String path){return new Archive.Source(){public long size(){return names.length;}public byte[] read(long at,int size)throws IOException{Binary.range(at,size,names.length);return Arrays.copyOfRange(names,(int)at,(int)at+size);}};}
        });return new Dc4Host(new CircusResources(files,Profiles.get("dc4ph-abb6")),TextCodec.identity(),true);
    }
    private static void small(ByteArrayOutputStream out,int op,int a,int b){out.write(op);out.write(a);out.write(b);}
    private static void words(ByteArrayOutputStream out,int op,int... a){out.write(op);for(int n:a){out.write(n);out.write(n>>>8);}}
    private static void string(ByteArrayOutputStream out,int op,String text)throws IOException{out.write(op);out.write(text.getBytes(Binary.SJIS));out.write(0);}
    private static MesScript script()throws IOException{
        ByteArrayOutputStream out=new ByteArrayOutputStream();out.write(new byte[]{0,0,0,0,(byte)0xb6,(byte)0xab,0});
        string(out,0x4b,"next");words(out,0x86,99,250,0,0);small(out,0x02,1,0);small(out,0x15,3,0);string(out,0x54,"next");small(out,0x20,1,1);small(out,0,0,0);
        return new MesScript("ending-status",out.toByteArray(),Profiles.get("dc4ph-abb6"));
    }
    public static int run()throws Exception{
        checks=0;Dc4Host h=host();MesScript script=script();MesVm vm=new MesVm(n->script,h,"ending-status");check(h.read(99,250)==1,"Idle music is complete without fabricating a timer");
        h.replaceAudioNow(0,"owned/ending",false,true);int generation=h.audioGeneration[0];check(h.read(99,250)==0,"Active music keeps the PH ED completion query at0");
        check(vm.run(100)==MesVm.Status.WAIT_HOST&&h.remainingMillis()==600,"Actual ED loop structure waits while music remains active");
        h.audioCompleted(0,generation-1);vm.elapse(600);check(vm.run(100)==MesVm.Status.WAIT_HOST&&h.read(99,250)==0,"Stale playback callback cannot release the ending loop");
        byte[] saved=vm.snapshot();Dc4Host copy=host();MesVm resumed=new MesVm(n->script,copy,"ending-status");resumed.restore(saved);check(copy.read(99,250)==0,"Cold snapshot retains actual active audio state for the compatibility query");
        copy.audioCompleted(0,generation);resumed.elapse(600);check(resumed.run(100)==MesVm.Status.HALTED&&copy.read(99,250)==1,"Current playback completion releases the ED loop naturally");
        Dc3Host dd=new Dc3Host(null,TextCodec.identity(),true);check(dd.read(99,250)==0,"The PH compatibility query does not change the existing DD getter");
        check(h.read(99,249)==0&&h.read(99,244)==0,"Uncalled and unverified249/244 remain unchanged");return checks;
    }
    public static void main(String[] args)throws Exception{System.out.println("Dc4AudioStatusTests "+run()+" PASS");}
}
