package org.circus.core;

import java.io.*;
import java.util.*;

/** DCDX403D40 bounded system prefix,41BC60 bank offsets and native gallery
 * predicates. Synthetic fixtures only; native narrative conversion excluded. */
public final class DcdxProgressTests {
    private static final String ID="dcdx-7769";
    private static int checks;
    private interface Attempt{void run()throws Exception;}
    private static void check(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
    private static void reject(Attempt action,String message)throws Exception{boolean failed=false;try{action.run();}catch(IOException expected){failed=true;}check(failed,message);}
    private static byte[] source(int length){byte[] out=new byte[length];if(length>=0x4c)out[0x48]=0x77;return out;}
    private static GameResources files()throws IOException{
        return new GameResources(new GameResources.Storage(){
            public Set<String> paths(){return new HashSet<String>(Arrays.asList("AdvData/DAT/NAMES.DAT","AdvData/SaveData/system.dat","SaveData/system.dat","system.dat","backup/system.dat"));}
            public byte[] read(String path,int limit){return new byte[20];}
            public Archive.Source source(String path){return new Archive.Source(){public long size(){return 20;}public byte[] read(long at,int length)throws IOException{Binary.range(at,length,20);return new byte[length];}};}
        });
    }
    private static NativeProgress.Target target(String id)throws Exception{
        Profile profile=Profiles.get(id);NativeProgress.Target out=new NativeProgress.Target(new CircusResources(files(),profile),TextCodec.identity());out.legacy.attach(new Variables(out.legacy,profile.variableModel));return out;
    }
    private static void identity()throws Exception{
        check(NativeProgress.supports(ID)&&NativeProgress.fileSize(ID)==6232,"Read bound includes independently observed4096-byte native checkpoint tail");
        for(int n=2136;n<=6232;n++)check(NativeProgress.parse(ID,source(n)).source.total==0,"Every native-valid complete-prefix/partial-tail extent "+n);
        for(int n:new int[]{0,0x48,2135,6233})reject(()->NativeProgress.parse(ID,source(n)),"Truncated prefix or oversized source rejected "+n);
        reject(()->NativeProgress.parse(ID,null),"Missing source rejected");
        for(int value:new int[]{0,0x76,0x78,0x7700,0x01000077,0x77000077}){
            byte[] bad=source(2136);for(int n=0;n<4;n++)bad[0x48+n]=(byte)(value>>>(8*n));reject(()->NativeProgress.parse(ID,bad),"Header is a full DWORD, not a low-byte test "+Integer.toHexString(value));
        }
        check(NativeProgress.sourcePaths(files(),ID).equals(Arrays.asList("AdvData/SaveData/system.dat","SaveData/system.dat","system.dat")),"Known system locations only; backup is not auto-imported");
        for(String name:new String[]{"DCDX00.DAT","dcdx01.dat","dcdx99.dat","dcdx100.dat"})check(NativeProgress.storySlot(ID,"AdvData/SaveData/system.dat","ADVDATA/SAVEDATA/"+name),"Proven native narrative filename counted, not converted "+name);
        for(String name:new String[]{"AdvData/SaveData/dc2fl01.dat","AdvData/SaveData/sub/dcdx01.dat","other/dcdx01.dat","AdvData/SaveData/dcdx01.dat.bak","AdvData/SaveData/system.dat"})check(!NativeProgress.storySlot(ID,"AdvData/SaveData/system.dat",name),"Unrelated file is not a DCDX native narrative slot "+name);
        for(int offset:new int[]{0xd8,0xd8+639})for(int value:new int[]{2,128,255}){
            byte[] bad=source(2136);bad[offset]=(byte)value;reject(()->NativeProgress.parse(ID,bad),"CG ==1 contract rejects nonbinary values "+offset+"/"+value);
        }
        for(int value:new int[]{1,2,127,128,255}){
            byte[] seen=source(2136);seen[0x358]=(byte)value;seen[0x758-1]=(byte)value;NativeProgress parsed=NativeProgress.parse(ID,seen);
            check(parsed.source.scenes==2&&parsed.source.cg==0,"MOVZX seen predicate accepts all unsigned nonzero source values "+value);
            NativeProgress.Target out=target(ID);parsed.apply(out);check(out.read(98,0)==1&&out.read(98,1023)==1,"Imported nonzero seen values normalize to1 "+value);
        }
        byte[] ignored=source(6232);Arrays.fill(ignored,0,0xd8,(byte)0xfd);for(int n=0;n<4;n++)ignored[0x48+n]=(byte)(0x77>>>(n*8));Arrays.fill(ignored,0x758,ignored.length,(byte)0xfe);
        check(NativeProgress.parse(ID,ignored).source.total==0,"Settings, names, globals, voice preferences and complete checkpoint tail are ignored");
    }
    private static void fullMerge()throws Exception{
        byte[] bytes=source(6232);Arrays.fill(bytes,0,0xd8,(byte)0xfd);for(int n=0;n<4;n++)bytes[0x48+n]=(byte)(0x77>>>(n*8));Arrays.fill(bytes,0xd8,0x358,(byte)1);
        int[] nonzero={1,2,127,128,255};for(int n=0;n<1024;n++)bytes[0x358+n]=(byte)nonzero[n%nonzero.length];Arrays.fill(bytes,0x758,bytes.length,(byte)0xfe);
        NativeProgress progress=NativeProgress.parse(ID,bytes);check(progress.source.cg==640&&progress.source.scenes==1024&&progress.source.completion==0&&progress.source.music==0&&progress.source.total==1664,"Full independent640CG/1024seen source extent, no other banks");Arrays.fill(bytes,(byte)0);
        NativeProgress.Target out=target(ID);check(out.legacy instanceof DcdxHost,"Shared importer constructs the independent DCDX host");PcHost host=(PcHost)out.legacy;
        host.setNativeNames("Own","Name");out.write(99,12,2);out.write(99,7,37);for(int n=0;n<100;n++)out.write(100,n,23+n%17);
        out.write(97,1,2);out.write(97,3,1);out.write(98,0,255);out.write(98,900,2);out.write(95,0,37);out.write(95,255,55);out.write(90,1023,90);
        host.variables.write(3,4,123);host.readCheckpoints.add("owned:42");host.availableSaves.add(6);byte[] before=out.save();
        NativeProgress.Summary preview=progress.preview(out);check(preview.cg==638&&preview.scenes==1022&&preview.total==1660,"Preview counts only new unlocks across all native bitmap boundaries");check(Arrays.equals(before,out.save()),"Source preview does not mutate current phone progress");
        check(progress.apply(out).total==1660,"Apply matches independently counted preview");
        for(int n=0;n<640;n++)check(out.read(97,n)==(n==1?2:1),"Complete byte CG map preserves existing nonzero values "+n);
        for(int n=0;n<1024;n++)check(out.read(98,n)==(n==0?255:n==900?2:1),"Complete byte seen map normalizes only new values "+n);
        for(int n=0;n<100;n++)check(out.read(100,n)==23+n%17,"Every global remains current phone state "+n);
        check(host.familyName.equals("Own")&&host.givenName.equals("Name")&&out.read(99,12)==2&&out.read(99,7)==37,"Current names and settings retained");
        check(out.read(95,0)==37&&out.read(95,255)==55&&out.read(90,1023)==90,"Voice preferences and native scratch are not imported");
        check(host.variables.read(3,4)==123&&host.readCheckpoints.contains("owned:42")&&host.availableSaves.equals(Collections.singleton(6)),"Current story, read journal and preexisting slot availability retained");
        byte[] merged=out.save();check(progress.preview(out).total==0&&progress.apply(out).total==0&&Arrays.equals(merged,out.save()),"Repeated import is byte-for-byte idempotent");
        NativeProgress.Target restarted=target(ID);restarted.load(merged);check(progress.preview(restarted).total==0&&Arrays.equals(merged,restarted.save()),"Exact runtime persistence survives a fresh DCDX target");
        check(NativeProgress.parse(ID,source(2136)).apply(out).total==0&&Arrays.equals(merged,out.save()),"Fresh original source cannot erase existing phone unlocks");
        NativeProgress.Target wrong=target("dc2fl-9c69");byte[] wrongBefore=wrong.save();reject(()->progress.preview(wrong),"Cross-profile preview rejected");reject(()->progress.apply(wrong),"Cross-profile apply rejected");check(Arrays.equals(wrongBefore,wrong.save()),"Profile mismatch is atomic");
        reject(()->progress.apply((NativeProgress.Target)null),"Null target rejected");
    }
    public static int run()throws Exception{checks=0;identity();fullMerge();return checks;}
    public static void main(String[] args)throws Exception{System.out.println("DcdxProgressTests "+run()+" PASS");}
}
