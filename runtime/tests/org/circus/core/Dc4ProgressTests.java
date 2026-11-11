package org.circus.core;

import java.io.*;
import java.util.*;

/** PH native offsets are independent of the portable DDP2 projection. */
public final class Dc4ProgressTests {
    private static final String ID="dc4ph-abb6";
    private static final int SIZE=21368,GLOBALS=0xd8,MUSIC=0x180,CG=0x310,SCENES=0x7c0;
    private static final int[] COMPLETION={100,101,102,103,104,105,106,107,108,109,110,111,112};
    private static int checks;
    private interface Attempt{void run()throws Exception;}
    private static void check(boolean ok,String why){checks++;if(!ok)throw new AssertionError(why);}
    private static void reject(Attempt action,String why)throws Exception{try{action.run();throw new AssertionError(why);}catch(IOException expected){checks++;}}
    private static CircusResources resources()throws IOException{
        final Map<String,byte[]> entries=new TreeMap<>();
        for(String path:new String[]{"AdvData/GRP/NAMES.DAT","AdvData/SaveData/system.dat","SaveData/system.dat","system.dat","dc4ph/system.dat","backup/system.dat"})entries.put(path.toLowerCase(Locale.ROOT),new byte[path.endsWith("DAT")?20:SIZE]);
        entries.put("advdata/mes/start.mes",new byte[]{0,0,0,0,(byte)0xb6,(byte)0xab,0,0,0,0});
        GameResources files=new GameResources(new GameResources.Storage(){
            public Set<String> paths(){return entries.keySet();}
            public byte[] read(String path,int limit)throws IOException{byte[] b=entries.get(path.toLowerCase(Locale.ROOT));if(b==null)throw new FileNotFoundException(path);if(b.length>limit)throw new IOException("Input exceeds limit");return b.clone();}
            public Archive.Source source(String path)throws IOException{byte[] bytes=read(path,Integer.MAX_VALUE);return new Archive.Source(){public long size(){return bytes.length;}public byte[] read(long at,int size)throws IOException{Binary.range(at,size,bytes.length);return Arrays.copyOfRange(bytes,(int)at,(int)at+size);}};}
        });
        return new CircusResources(files,GameVersion.detect(files).profile());
    }
    private static NativeProgress.Target target()throws Exception{return new NativeProgress.Target(resources(),TextCodec.identity());}
    private static boolean completion(int index){for(int n:COMPLETION)if(n==index)return true;return false;}
    private static byte[] full(){
        byte[] bytes=new byte[SIZE];Arrays.fill(bytes,(byte)0xa5);
        for(int n:COMPLETION)bytes[GLOBALS+n]=1;
        Arrays.fill(bytes,MUSIC,MUSIC+400,(byte)1);Arrays.fill(bytes,CG,CG+1200,(byte)1);Arrays.fill(bytes,SCENES,SCENES+650,(byte)1);return bytes;
    }
    public static int run()throws Exception{
        checks=0;
        check(resources().profileId.equals(ID)&&NativeProgress.supports(ID)&&NativeProgress.fileSize(ID)==SIZE,"PH identity comes from ABB6 START and admits its own21368-byte system format");
        Dc3State phState=new Dc3State(ID,true);long phRevision=phState.progressRevision;phState.write(100,112,1);check(phState.progressRevision==phRevision+1,"PH's reviewed completion112 requests persistent progress just like its other route flags");
        Dc3State ddState=new Dc3State("dc3dd-a5a8",true);long ddRevision=ddState.progressRevision;ddState.write(100,112,1);check(ddState.progressRevision==ddRevision,"PH completion expansion does not change the legacy DD dirty policy");
        for(int size:new int[]{0,SIZE-1,SIZE+1,21356,19744,4696})reject(()->NativeProgress.parse(ID,new byte[size]),"Wrong native version length cannot use PH offsets: "+size);
        reject(()->NativeProgress.parse(ID,null),"Null native source rejected");
        check(NativeProgress.sourcePaths(resources().files,ID).equals(Arrays.asList("AdvData/SaveData/system.dat","SaveData/system.dat","system.dat")),"Directory discovery excludes guessed PH subfolders and unrelated backups");
        String parent="AdvData/SaveData/system.dat";
        for(String name:new String[]{"DC4PHDL00.dat","DC4PHDL99.dat","PREDC4PHDL01.dat"})check(NativeProgress.storySlot(ID,parent,"AdvData/SaveData/"+name),"Known PH original story filename counted: "+name);
        for(String name:new String[]{"dc4ph00.dat","dc3dd00.dat","system.dat","DC4PHDL00.dat.bak","backup/DC4PHDL00.dat"})check(!NativeProgress.storySlot(ID,parent,"AdvData/SaveData/"+name),"Other files are not reported as PH story slots: "+name);
        NativeProgress.Target empty=target();byte[] initial=empty.save();
        check(NativeProgress.parse(ID,new byte[SIZE]).apply(empty).total==0&&Arrays.equals(initial,empty.save()),"Empty native progress cannot clear target defaults or write settings");
        byte[] source=full(),sourceCopy=source.clone();NativeProgress imported=NativeProgress.parse(ID,source);
        check(imported.source.cg==1200&&imported.source.scenes==650&&imported.source.music==400&&imported.source.completion==COMPLETION.length,"Full source independently covers each byte bank and reviewed completion flags");
        NativeProgress.Target own=target();own.dd.voiceCount=2;
        for(int n=0;n<150;n++)own.write(100,n,completion(n)?0:(n*13+7)&255);
        own.write(99,7,37);own.write(99,180,0);own.dd.togglePhMessageControls();own.write(93,3,1);own.write(94,50,41);own.write(95,50,0);own.dd.musicFavorites[399]=1;
        own.write(96,7,4);own.write(97,7,2);own.write(98,7,3);
        byte[] before=own.save(),expected=before.clone();NativeProgress.Summary preview=imported.preview(own);
        check(Arrays.equals(before,own.save())&&Arrays.equals(sourceCopy,source),"Preview owns no write effects on either source or target");
        int expectedAdded=0;
        for(int n:COMPLETION)if(expected[4+n]==0){expected[4+n]=1;expectedAdded++;}
        int at=4+150;
        for(int length:new int[]{400,1200,650}){for(int n=0;n<length;n++)if(expected[at+n]==0){expected[at+n]=1;expectedAdded++;}at+=length;}
        Arrays.fill(source,(byte)0);NativeProgress.Summary applied=imported.apply(own);
        check(applied.total==preview.total&&applied.total==expectedAdded,"Apply adds exactly source flags absent from the current portable banks");
        check(Arrays.equals(expected,own.save()),"All settings, favorites, confirmations, voice controls and unreviewed globals are byte-identical; only approved progress fields changed");
        check(own.read(96,7)==4&&own.read(97,7)==2&&own.read(98,7)==3,"Import preserves existing nonzero progress without normalization or regression");
        check(own.read(97,1199)==1&&own.read(98,649)==1&&own.read(96,399)==1,"End-of-bank bytes are imported at native offsets without packed-bit decoding");
        check(Arrays.equals(sourceCopy,full()),"Parser retained owned progress values after caller changed source bytes");
        byte[] merged=own.save();check(imported.apply(own).total==0&&Arrays.equals(merged,own.save()),"Repeated full import is byte-stable");
        NativeProgress.Target cold=target();cold.load(merged);check(Arrays.equals(merged,cold.save())&&imported.preview(cold).total==0,"Imported progress survives exact phone persistence cold-load");
        NativeProgress.Target wrong=new NativeProgress.Target(new CircusResources(resources().files,Profiles.get("dc3dd-a5a8")),TextCodec.identity());byte[] wrongBefore=wrong.save();
        reject(()->imported.apply(wrong),"PH progress cannot mutate another independently selected game");check(Arrays.equals(wrongBefore,wrong.save()),"Rejected cross-profile apply leaves target bytes intact");
        for(int offset:new int[]{MUSIC,MUSIC+399,CG,CG+1199}){byte[] bad=full();bad[offset]=2;reject(()->NativeProgress.parse(ID,bad),"Nonbinary source music/CG flag rejected at "+offset);}
        for(int value:new int[]{2,127,128,255}){byte[] seen=new byte[SIZE];seen[SCENES]=(byte)value;seen[SCENES+649]=(byte)value;NativeProgress decoded=NativeProgress.parse(ID,seen);NativeProgress.Target seenTarget=target();decoded.apply(seenTarget);check(decoded.source.scenes==2&&seenTarget.read(98,0)==1&&seenTarget.read(98,649)==1,"Unsigned positive PH scene bytes normalize to seen, matching the original menu: "+value);}
        for(int index:COMPLETION){byte[] bad=full();bad[GLOBALS+index]=2;reject(()->NativeProgress.parse(ID,bad),"Completion flag requires independently reviewed boolean polarity: "+index);}
        return checks;
    }
    public static void main(String[] args)throws Exception{System.out.println("Dc4ProgressTests "+run()+" PASS");}
}
