package org.circus.core;

import java.io.*;
import java.util.*;

/** PP_CN 4256A0/4216B0 packed storage and backwards-compatible portable projections. */
public final class PpStateTests {
    private static int count;
    private static void check(boolean value,String why){count++;if(!value)throw new AssertionError(why);}
    private interface Work{void run()throws Exception;}
    private static void reject(Work work,String message)throws Exception{boolean rejected=false;try{work.run();}catch(IOException e){rejected=e.getMessage().contains(message);}check(rejected,message);}
    private static Dc3State pp(){return new Dc3State("dc3pp-9872",true);}
    private static byte[] save(Dc3State state)throws IOException{ByteArrayOutputStream bytes=new ByteArrayOutputStream();state.save(new DataOutputStream(bytes));return bytes.toByteArray();}
    private static void load(Dc3State state,byte[] bytes)throws IOException{DataInputStream input=new DataInputStream(new ByteArrayInputStream(bytes));state.load(input);check(input.available()==0,"state format consumes its unchanged portable layout");}
    private static CircusResources resources()throws IOException{
        final byte[] names=new byte[20];GameResources files=new GameResources(new GameResources.Storage(){
            public Set<String> paths(){return Collections.singleton("AdvData/GRP/NAMES.DAT");}
            public byte[] read(String path,int limit)throws IOException{if(limit<names.length)throw new IOException("Fixture limit");return names.clone();}
            public Archive.Source source(String path){return new Archive.Source(){public long size(){return names.length;}public byte[] read(long start,int length)throws IOException{Binary.range(start,length,names.length);return Arrays.copyOfRange(names,(int)start,(int)start+length);}};}
        });return new CircusResources(files,Profiles.get("dc3pp-9872"));
    }
    public static int run()throws Exception{
        count=0;Dc3State state=pp();
        check(state.read(99,7)==20&&state.read(99,8)==23&&state.read(99,9)==27&&state.read(99,11)==11&&state.read(99,26)==8&&state.read(99,29)==17,"PP defaults come from its independently verified native preference table");
        check(state.read(96,1)==0&&state.read(96,255)==0,"fresh PP storage does not inherit DD unlocked music defaults");
        state.write(96,0,2);state.write(96,7,-1);check(state.read(100,100)==129,"PP music flags share the next byte after globals, nonzero writes set one bit");
        state.write(100,100,0);check(state.read(100,100)==129,"PP global writes stop at 99, while reads may cross into music");
        state.write(96,209,1);state.write(96,210,1);check(state.read(100,126)==2&&state.read(96,210)==0,"PP music write bound is 210 and trailing byte padding is retained");
        state.write(97,0,1);check(state.read(100,127)==1&&state.read(96,216)==1,"unbounded native bit read may alias the following verified bitmap");
        state.write(98,0,1);check(state.read(100,277)==1&&state.read(97,1200)==1,"CG read crosses into the scene bitmap without inventing a read-side write bound");
        state.write(98,649,1);state.write(98,650,1);check(state.read(100,358)==2&&state.read(98,649)==1&&state.read(98,650)==0,"scene tail has 650 writable bits in 82 bytes");
        final Dc3State bounds=state;reject(()->bounds.read(100,359),"outside verified range");reject(()->bounds.read(98,656),"outside verified range");reject(()->bounds.read(96,-1),"Negative PP");
        Variables variables=new Variables(state,Variables.Model.DC2_PACKED);
        variables.write(100,20,300);check(variables.read(10,20)==44&&variables.read(100,20)==44,"PP persistent byte write wraps and mirrors ordinary bank 10");
        variables.write(10,20,300);check(variables.read(100,20)==255,"ordinary bank 10 clamps before writing persistent alias");
        variables.write(400,99,0x1234);check(variables.read(400,99)==0x1281,"last persistent word writes high byte but reads music as its low byte");
        int prior=state.read(99,11);state.write(100,49,7);check(state.read(100,49)==7&&state.read(99,11)==prior,"PP global 49 has no DD preference setter side effect");
        byte[] bytes=save(state);Dc3State restored=pp();load(restored,bytes);
        check(Arrays.equals(bytes,save(restored))&&restored.read(100,100)==129&&restored.read(100,358)==2,"PP state preserves packed aliases through the self-described save format");
        byte[] padded=bytes.clone();padded[4+126]|=(byte)252;padded[4+358]|=(byte)252;Dc3State padding=pp();load(padding,padded);
        check(padding.read(96,210)==1&&padding.read(96,215)==1&&padding.read(98,650)==1&&padding.read(98,655)==1,"owned padding bits remain visible to native raw-bit readers");
        padding.write(96,210,0);padding.write(98,650,0);check(padding.read(96,210)==1&&padding.read(98,650)==1,"native write bounds cannot clear preserved owned padding bits");
        check(Arrays.equals(padded,save(padding)),"complete 359-byte packed backing, including padding, survives a story snapshot");
        Dc3State paddingSettings=pp();paddingSettings.loadPersistent(padding.savePersistent());check(paddingSettings.read(100,126)==254&&paddingSettings.read(100,358)==254,"persistent settings preserve the owned padding bytes");
        final byte[] disagree=bytes.clone();disagree[4+100]^=1;reject(()->load(pp(),disagree),"disagree with their logical save projection");
        reject(()->load(new Dc3State(true),bytes),"PP packed save profile mismatch");
        Dc3State settings=pp();settings.loadPersistent(state.savePersistent());check(settings.read(100,127)==1&&settings.read(98,649)==1,"normal persistence restores the same packed owner");
        Dc3State legacy=new Dc3State(true);legacy.write(100,20,8);legacy.write(97,1199,1);byte[] legacyBytes=save(legacy);
        Dc3State migrated=pp();load(migrated,legacyBytes);
        check(Arrays.equals(legacyBytes,Arrays.copyOfRange(save(migrated),363,save(migrated).length)),"valid legacy PP logical projection is retained byte-for-byte inside the versioned save, including opaque unused tails");
        check(migrated.read(96,210)==0&&migrated.read(98,655)==0,"legacy projections cannot recover raw padding and explicitly initialize it to zero");
        check(migrated.read(96,1)==1&&migrated.read(97,1199)==1&&migrated.read(100,276)==128,"legacy logical flags become the correct PP packed bytes");
        Dc3State badFlags=new Dc3State(true);badFlags.write(97,2,255);final byte[] invalidFlags=save(badFlags);reject(()->load(pp(),invalidFlags),"cannot migrate faithfully");
        Dc3State badGlobals=new Dc3State(true);badGlobals.write(100,100,1);final byte[] invalidGlobals=save(badGlobals);reject(()->load(pp(),invalidGlobals),"outside the native writable region");
        final Dc3State retained=pp();retained.write(100,3,9);final byte[] beforeFailure=save(retained);
        reject(()->load(retained,invalidFlags),"cannot migrate faithfully");check(Arrays.equals(beforeFailure,save(retained)),"failed PP flag migration does not publish partial progress");
        reject(()->load(retained,invalidGlobals),"outside the native writable region");check(Arrays.equals(beforeFailure,save(retained)),"failed PP global migration retains the prior state and original input bytes");
        for(String id:new String[]{"dc3dd-a5a8","dc3wy-a09f"}){Dc3State original=new Dc3State(id,true);original.write(96,399,257);check(original.read(96,399)==1&&original.read(96,255)==1,id+" keeps byte-bank and default behavior");original.write(100,49,7);check(original.read(99,11)==7,id+" keeps DD-style preference binding");Dc3State roundtrip=new Dc3State(id,true);load(roundtrip,save(original));check(Arrays.equals(save(original),save(roundtrip)),id+" unchanged serialization round trip");}
        Dc3State more=pp();more.write(97,20,1);more.write(98,649,1);Dc3State merged=pp();merged.mergeGallery(more);check(merged.read(97,20)==1&&merged.read(98,649)==1,"PP gallery merge uses version-bound logical flags");
        reject(()->merged.mergeGallery(new Dc3State(true)),"profile mismatch");
        byte[] nativeProgress=new byte[NativeProgress.fileSize("dc3pp-9872")];nativeProgress[0x88]=1;nativeProgress[0xec+26]=2;nativeProgress[0x107+149]=(byte)128;nativeProgress[0x19d+81]=2;
        NativeProgress.Target target=new NativeProgress.Target(resources(),TextCodec.identity());NativeProgress.parse("dc3pp-9872",nativeProgress).apply(target);
        check(target.dd.profileId.equals("dc3pp-9872")&&target.read(100,126)==2&&target.read(100,276)==128&&target.read(100,358)==2,"native progress import writes the same PP packed backing used in normal execution");
        NativeProgress.Target reloaded=new NativeProgress.Target(resources(),TextCodec.identity());reloaded.load(target.save());check(reloaded.read(100,126)==2&&reloaded.read(97,1199)==1&&reloaded.read(98,649)==1,"native import persistence uses the same version-bound format");
        return count;
    }
    public static void main(String[] args)throws Exception{System.out.println("PASS "+run()+" PP native state assertions");}
}
