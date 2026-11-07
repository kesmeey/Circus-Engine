package org.circus.core;

import java.io.*;
import java.lang.reflect.Field;
import java.util.*;

/** Native conformance vectors for PC1 414DC0 and PC2 41EDA4, without game files. */
public final class PcRandomTests {
    private static int count;
    private static void check(boolean value,String why){count++;if(!value)throw new AssertionError(why);}
    private static PcHost host(String profile)throws IOException {
        final byte[] names=new byte[20];
        GameResources files=new GameResources(new GameResources.Storage(){
            public Set<String> paths(){return Collections.singleton("AdvData/DAT/NAMES.DAT");}
            public byte[] read(String path,int limit)throws IOException{if(names.length>limit)throw new IOException("Fixture limit");return names.clone();}
            public Archive.Source source(String path){return new Archive.Source(){
                public long size(){return names.length;}
                public byte[] read(long start,int length)throws IOException{Binary.range(start,length,names.length);return Arrays.copyOfRange(names,(int)start,(int)start+length);}
            };}
        });
        PcHost result=Pc1TestDefaults.bind(new PcHost(new CircusResources(files,Profiles.get(profile)),TextCodec.identity(),0x63c25769));
        result.attach(new Variables(result,result.resources.profile.variableModel));return result;
    }
    // Seed injection is confined to the conformance fixture; no testing API enters the runtime.
    private static void seed(PcHost host,int value)throws Exception {Field f=PcHost.class.getDeclaredField("randomState");f.setAccessible(true);f.setInt(host,value);}
    private static int random(PcHost host,int range)throws IOException {host.command("pc.random",new int[]{range,0},new byte[0]);return host.read(99,2);}
    public static int run()throws Exception {
        count=0;
        for(String profile:new String[]{"dcpc-2063","dc2pc-5769"}){
            PcHost h=host(profile);h.write(99,255,77);int priorReturn=h.read(99,255);check(priorReturn==(h.pc1!=null?0:77),profile+" register255 follows its reviewed native mapping");
            check(random(h,0)==0,profile+" zero modulus returns zero");
            // Fixed vectors from the native CRT recurrence, starting with the explicitly injected seed.
            int[] vectors={31314,28635,19333,3050,30721,30105,6699,27537};
            for(int expected:vectors)check(random(h,32768)==expected,profile+" native 15-bit random sequence");
            check(h.read(99,255)==priorReturn,profile+" random must not overwrite 99:255");
            h=host(profile);check(random(h,7)==3,profile+" IDIV remainder");
            for(int n=0;n<4;n++)check(random(h,0)==0,profile+" repeated zero range");
            check(random(h,7)==5,profile+" zero ranges must not advance CRT state");
            // Direct host calls can pass a negative divisor even though these two opcodes encode u8.
            // The native IDIV remainder keeps the dividend sign, unlike Math.floorMod.
            h=host(profile);check(random(h,-7)==3,profile+" signed remainder agrees with IDIV");
            seed(h,0);check(random(h,32768)==38,profile+" zero seed is valid");
            seed(h,-1);check(random(h,32768)==35,profile+" negative seed wraps as 32-bit native state");
            seed(h,Integer.MIN_VALUE);check(random(h,32768)==38,profile+" high-bit seed yields native 15-bit value");
            seed(h,-1);ByteArrayOutputStream bytes=new ByteArrayOutputStream();h.save(new DataOutputStream(bytes));
            PcHost restored=host(profile);restored.load(new DataInputStream(new ByteArrayInputStream(bytes.toByteArray())));
            check(random(h,32768)==35&&random(restored,32768)==35,profile+" existing snapshot layout retains signed seed");
            check(random(h,32768)==random(restored,32768),profile+" snapshot resumes the same random stream");
            h=host(profile);for(int n=0;n<7;n++)h.command("pc.voice_group.add",new int[0],("VOICE/v"+n).getBytes(Binary.SJIS));
            h.command("pc.voice_group.play",new int[]{0,0},new byte[0]);
            check(h.audio[5].equals("VOICE/v3"),profile+" voice group consumes native random output");
            check(random(h,32768)==28635,profile+" voice groups and opcode use one CRT stream");
        }
        return count;
    }
    public static void main(String[] args)throws Exception{System.out.println("PASS "+run()+" native PC random assertions");}
}
