package org.circus.core;

import java.io.*;
import java.util.*;

/** A four-byte offset error can leave both FL bitmaps binary; compare every index. */
public final class FlLegacyProgressTests {
    private static int checks;
    private static final String ID="dc2fl-9c69";
    private static final int[] CLEAR={0,1,2,3,4,5,6,7,8,9,10,11,12,13,52,53};
    private static void check(boolean ok,String why){checks++;if(!ok)throw new AssertionError(why);}
    private static NativeProgress.Target target()throws Exception{
        GameResources files=new GameResources(new GameResources.Storage(){
            public Set<String> paths(){return Collections.singleton("AdvData/DAT/NAMES.DAT");}
            public byte[] read(String path,int limit){return new byte[20];}
            public Archive.Source source(String path){return new Archive.Source(){public long size(){return 20;}public byte[] read(long at,int size)throws IOException{Binary.range(at,size,20);return new byte[size];}};}
        });
        NativeProgress.Target result=new NativeProgress.Target(new CircusResources(files,Profiles.get(ID)),TextCodec.identity());result.legacy.attach(new Variables(result.legacy,Variables.Model.PC_WIDE));return result;
    }
    public static int run()throws Exception{
        checks=0;byte[] old=new byte[2028];Arrays.fill(old,0,0xd4,(byte)0xfe);for(int n:CLEAR)old[0x70+n]=1;
        for(int n:new int[]{0,4,639})old[0xd4+n]=1;for(int n:new int[]{0,4,1023})old[0x354+n]=1;Arrays.fill(old,0x754,old.length,(byte)0xfd);
        NativeProgress progress=NativeProgress.parse(ID,old);check(NativeProgress.fileSize(ID)==2032,"Read cap retains both known layouts");check(progress.source.cg==3&&progress.source.scenes==3&&progress.source.completion==16,"Legacy offsets, not four-byte-shifted modern offsets");
        NativeProgress.Target phone=target();PcHost host=(PcHost)phone.legacy;host.setNativeNames("Own","Name");phone.write(99,7,37);phone.write(100,56,23);phone.write(95,151,55);phone.write(97,10,1);phone.write(98,10,1);byte[] before=phone.save();
        check(progress.preview(phone).total==22&&Arrays.equals(before,phone.save()),"Preview observes legacy flags without writing");check(progress.apply(phone).total==22,"Selective legacy merge");
        for(int n=0;n<640;n++)check(phone.read(97,n)==(n==0||n==4||n==10||n==639?1:0),"Legacy CG index "+n);
        for(int n=0;n<1024;n++)check(phone.read(98,n)==(n==0||n==4||n==10||n==1023?1:0),"Legacy seen index "+n);
        check(phone.read(99,7)==37&&phone.read(100,56)==23&&phone.read(95,151)==55&&host.familyName.equals("Own")&&host.givenName.equals("Name"),"Settings, unrelated globals, voice and names stay on phone");
        byte[] merged=phone.save();check(progress.apply(phone).total==0&&Arrays.equals(merged,phone.save()),"Legacy repeat merge is byte-identical");NativeProgress.Target restart=target();restart.load(merged);check(progress.preview(restart).total==0,"Legacy progress persists in current schema");
        byte[] current=new byte[2032];System.arraycopy(old,0,current,0,0x48);System.arraycopy(old,0x48,current,0x4c,old.length-0x48);
        NativeProgress modern=NativeProgress.parse(ID,current);NativeProgress.Target converted=target();converted.load(before);check(modern.apply(converted).total==22&&Arrays.equals(merged,converted.save()),"Independently realigned modern fixture produces exactly the same current progress");
        for(int length:new int[]{2027,2029,2031,2033}){boolean failed=false;try{NativeProgress.parse(ID,new byte[length]);}catch(IOException expected){failed=true;}check(failed,"Unreviewed FL extent rejected "+length);}
        return checks;
    }
}
