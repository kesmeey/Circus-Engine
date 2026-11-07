package org.circus.core;

import java.io.*;
import java.util.*;

/** PP/WY99:3 is a read-only platform capability, not a DD stored preference. */
public final class NativeCapabilityTests {
    private static int count;
    private static void check(boolean value,String why){count++;if(!value)throw new AssertionError(why);}
    private static Dc3Host host(String profile)throws IOException {
        final byte[] names=new byte[20];GameResources data=new GameResources(new GameResources.Storage(){
            public Set<String> paths(){return Collections.singleton("AdvData/GRP/NAMES.DAT");}
            public byte[] read(String path,int limit){return names.clone();}
            public Archive.Source source(String path){return new Archive.Source(){public long size(){return names.length;}public byte[] read(long at,int length)throws IOException{Binary.range(at,length,names.length);return Arrays.copyOfRange(names,(int)at,(int)at+length);}};}
        });return new Dc3Host(new CircusResources(data,Profiles.get(profile)),TextCodec.identity(),true);
    }
    public static int run()throws Exception {
        count=0;Dc3Host pp=host("dc3pp-9872");
        for(int[] mode:new int[][]{{1024,576,9,1},{1023,576,32,0},{1024,575,32,0},{1024,576,8,0},{1920,1080,32,1}}){
            pp.displayCapabilities(mode[0],mode[1],mode[2]);check(pp.read(99,3)==mode[3],"PP native display-capability thresholds");
            pp.write(99,3,1-mode[3]);check(pp.read(99,3)==mode[3],"Script writes cannot change a read-only platform capability");
        }
        pp.fullscreen=true;check(pp.read(99,4)==1&&pp.read(99,3)==1,"Current window mode is separate from capability");
        Dc3Host wy=host("dc3wy-a09f");wy.displayCapabilities(800,600,8);wy.write(99,3,0);
        check(wy.read(99,3)==1&&wy.read(99,4)==0,"WY native constructor enables mode switching independently of current fullscreen flag");
        return count;
    }
}
