package org.circus.core;

import java.io.*;
import java.util.*;

/** Self-authored opcode22 fixtures and fixed original417025/422C72 goldens.
 * No original MES, EXE bytes, save data or dialogue is embedded. */
public final class Dc2RandomTests {
    private static int checks;
    private static void check(boolean yes,String why){checks++;if(!yes)throw new AssertionError(why);}
    // seed, range, native result99:2, native next shared CRT state.
    private static final int[][] GOLDEN={
        {0,0,0,0},
        {0,1,0,2531011},
        {0,2,0,2531011},
        {0,6,2,2531011},
        {0,255,38,2531011},
        {1,0,0,1},
        {1,1,0,2745024},
        {1,2,1,2745024},
        {1,6,5,2745024},
        {1,255,41,2745024},
        {7,0,0,7},
        {7,1,0,4029102},
        {7,2,1,4029102},
        {7,6,1,4029102},
        {7,255,61,4029102},
        {-2147483648,0,0,-2147483648},
        {-2147483648,1,0,-2144952637},
        {-2147483648,2,0,-2144952637},
        {-2147483648,6,2,-2144952637},
        {-2147483648,255,38,-2144952637},
        {-1,0,0,-1},
        {-1,1,0,2316998},
        {-1,2,1,2316998},
        {-1,6,5,2316998},
        {-1,255,35,2316998}
    };
    private static CircusResources data(final int first,final int second)throws IOException{
        final byte[] mes={0,0,0,0,0x63,0x33,0x22,(byte)first,(byte)second,0x22,6,0,0,0,0},names=new byte[20];
        GameResources files=new GameResources(new GameResources.Storage(){
            public Set<String> paths(){return new HashSet<>(Arrays.asList("AdvData/MES/START.MES","AdvData/DAT/NAMES.DAT"));}
            private byte[] raw(String path){return path.toLowerCase(Locale.ROOT).endsWith("names.dat")?names:mes;}
            public byte[] read(String path,int max){return raw(path).clone();}
            public Archive.Source source(String path){final byte[] b=raw(path);return new Archive.Source(){public long size(){return b.length;}public byte[] read(long at,int count)throws IOException{Binary.range(at,count,b.length);return Arrays.copyOfRange(b,(int)at,(int)at+count);}};}
        });return new CircusResources(files,Profiles.get("dc2-3363"));
    }
    public static int run()throws Exception{
        checks=0;
        for(int[] row:GOLDEN){CircusResources data=data(row[1],173);PcHost host=new PcHost(data,TextCodec.identity(),row[0]);host.registers.put(2,99);host.registers.put(255,73);MesVm vm=new MesVm(data.scripts(),host,"START");
            check(vm.run(1)==MesVm.Status.RUNNING&&vm.offset()==9&&!host.waiting(),"native22 returns after its three-byte record without waiting");
            check(vm.variables.read(99,2)==row[2]&&vm.variables.read(99,255)==73,"native22 writes99:2 and preserves independent255");
            check(host.nativeCrtState()==row[3],"native22 matches fixed original shared CRT state; zero range consumes no draw");
            check(row[1]==0?row[2]==0:row[2]>=0&&row[2]<row[1],"native remainder is within the requested byte range");
            check(Arrays.stream(host.audio).allMatch(String::isEmpty)&&Arrays.stream(host.scene.slots).allMatch(String::isEmpty),"native22 requests no resource or media changes");
        }
        CircusResources data=data(6,0);Dc2Session live=new Dc2Session(data,TextCodec.identity());((PcHost)live.host).nativeCrtState(1);check(live.vm.run(1)==MesVm.Status.RUNNING&&live.vm.variables.read(99,2)==5,"range6 fixed native seed1 result");byte[] saved=live.snapshot();Dc2Session restored=new Dc2Session(data,TextCodec.identity());restored.restore(saved);check(Arrays.equals(saved,restored.snapshot()),"session codec preserves random result and shared CRT state on fresh host");
        check(live.vm.run(1)==MesVm.Status.RUNNING&&restored.vm.run(1)==MesVm.Status.RUNNING,"both live and restored execute following real22 record");check(live.vm.variables.read(99,2)==restored.vm.variables.read(99,2)&&((PcHost)live.host).nativeCrtState()==((PcHost)restored.host).nativeCrtState(),"restored random stream produces the same next draw");
        return checks;
    }
    public static void main(String[] args)throws Exception{System.out.println("Dc2RandomTests "+run()+" PASS");}
}
