package org.circus.core;

import java.io.*;
import java.util.*;

/** Original SC/FL system.dat layouts from their own PE loaders/getters and FL ED.
 * Fixtures contain synthetic bytes only. Narrative native SAV conversion is out of scope. */
public final class PcScFlProgressTests {
    private static int checks;
    private static final String[] IDS={"dc2sc-3b69","dc2fl-9c69"};
    private static final int[] SIZE={2340,2032},GLOBAL={0x70,0x74},CG={0xd4,0xd8},CG_COUNT={1024,640},SEEN={0x4d4,0x358},VOICE={80,152};
    private static final int[][] COMPLETION={new int[0],{0,1,2,3,4,5,6,7,8,9,10,11,12,13,52,53}};
    private interface Attempt{void run()throws Exception;}
    private static void check(boolean value,String reason){checks++;if(!value)throw new AssertionError(reason);}
    private static void reject(Attempt attempt,String reason)throws Exception{
        boolean failed=false;try{attempt.run();}catch(IOException expected){failed=true;}check(failed,reason);
    }
    private static GameResources files()throws IOException{
        return new GameResources(new GameResources.Storage(){
            public Set<String> paths(){return new HashSet<>(Arrays.asList("AdvData/DAT/NAMES.DAT","AdvData/SaveData/system.dat","SaveData/system.dat","system.dat","backup/system.dat"));}
            public byte[] read(String path,int limit){return new byte[20];}
            public Archive.Source source(String path){return new Archive.Source(){public long size(){return 20;}public byte[] read(long at,int length)throws IOException{Binary.range(at,length,20);return new byte[length];}};}
        });
    }
    private static NativeProgress.Target target(int family)throws Exception{
        Profile profile=Profiles.get(IDS[family]);NativeProgress.Target target=new NativeProgress.Target(new CircusResources(files(),profile),TextCodec.identity());
        target.legacy.attach(new Variables(target.legacy,profile.variableModel));return target;
    }
    private static boolean allowed(int family,int index){for(int n:COMPLETION[family])if(n==index)return true;return false;}
    private static byte[] fixture(int family){
        byte[] bytes=new byte[SIZE[family]];Arrays.fill(bytes,0,CG[family],(byte)0xfd);
        for(int n:COMPLETION[family])bytes[GLOBAL[family]+n]=1;
        bytes[CG[family]]=bytes[CG[family]+17]=bytes[CG[family]+CG_COUNT[family]-1]=1;
        bytes[SEEN[family]]=bytes[SEEN[family]+29]=bytes[SEEN[family]+1023]=1;
        Arrays.fill(bytes,SEEN[family]+1024,bytes.length,(byte)0xfe);return bytes;
    }
    private static void identity(int family)throws Exception{
        String id=IDS[family];int size=SIZE[family];
        check(NativeProgress.supports(id)&&NativeProgress.fileSize(id)==size,"Independently versioned system layout "+id);
        check(NativeProgress.parse(id,new byte[size]).source.total==0,"Fresh native progress has no additions "+id);
        reject(()->NativeProgress.parse(id,null),"Missing original source "+id);
        for(int length:new int[]{0,size-1,size+1,SIZE[1-family]})reject(()->NativeProgress.parse(id,new byte[length]),"Exact native extent "+id+"/"+length);
        check(NativeProgress.sourcePaths(files(),id).equals(Arrays.asList("AdvData/SaveData/system.dat","SaveData/system.dat","system.dat")),"Known source locations only "+id);
        String prefix=family==0?"dc2sc":"dc2fl",source="AdvData/SaveData/system.dat";
        check(NativeProgress.storySlot(id,source,"ADVDATA/SAVEDATA/"+prefix.toUpperCase(Locale.ROOT)+"01.DAT"),"Own native narrative filename recognized for count only "+id);
        for(String path:new String[]{"AdvData/SaveData/dc2pc01.dat","AdvData/SaveData/sub/"+prefix+"01.dat","other/"+prefix+"01.dat","AdvData/SaveData/"+prefix+"01.dat.bak","AdvData/SaveData/system.dat"})
            check(!NativeProgress.storySlot(id,source,path),"Unrelated file is not a native narrative slot "+path);
        for(int offset:new int[]{CG[family],CG[family]+CG_COUNT[family]-1,SEEN[family],SEEN[family]+1023}){
            byte[] bad=new byte[size];bad[offset]=2;reject(()->NativeProgress.parse(id,bad),"Nonbinary gallery/seen byte rejected "+id+"/"+offset);
        }
        for(int n:COMPLETION[family]){byte[] bad=new byte[size];bad[GLOBAL[family]+n]=2;reject(()->NativeProgress.parse(id,bad),"Nonbinary reviewed completion rejected "+n);}
        byte[] ignored=new byte[size];Arrays.fill(ignored,GLOBAL[family],GLOBAL[family]+100,(byte)0xff);
        for(int n:COMPLETION[family])ignored[GLOBAL[family]+n]=0;
        check(NativeProgress.parse(id,ignored).source.total==0,"Unreviewed globals are neither copied nor misvalidated "+id);
        NativeProgress p=NativeProgress.parse(id,fixture(family));NativeProgress.Target wrong=target(1-family);byte[] before=wrong.save();
        reject(()->p.preview(wrong),"Cross-profile preview rejected "+id);reject(()->p.apply(wrong),"Cross-profile merge rejected "+id);
        check(Arrays.equals(before,wrong.save()),"Rejected profile cannot mutate phone progress "+id);reject(()->p.apply((NativeProgress.Target)null),"Null merge target "+id);
    }
    private static void merge(int family)throws Exception{
        String id=IDS[family];byte[] bytes=fixture(family);NativeProgress p=NativeProgress.parse(id,bytes);
        check(p.source.completion==COMPLETION[family].length&&p.source.cg==3&&p.source.scenes==3&&p.source.music==0,"Source counts independently reviewed fields "+id);
        Arrays.fill(bytes,(byte)0);NativeProgress.Target t=target(family);PcHost host=(PcHost)t.legacy;
        host.setNativeNames("Own","Name");t.write(99,8,4);t.write(99,13,3);
        for(int index=0;index<100;index++)t.write(100,index,allowed(family,index)?0:23+(index%17));
        if(COMPLETION[family].length>0)t.write(100,COMPLETION[family][0],1);
        t.write(97,17,1);t.write(97,18,1);t.write(98,29,1);t.write(98,30,1);t.write(95,0,37);t.write(95,VOICE[family]-1,55);t.write(90,1023,90);
        host.variables.write(3,4,123);host.readCheckpoints.add("local:42");host.availableSaves.add(6);
        byte[] before=t.save();NativeProgress.Summary preview=p.preview(t);
        int completion=COMPLETION[family].length==0?0:COMPLETION[family].length-1;
        check(preview.completion==completion&&preview.cg==2&&preview.scenes==2&&preview.total==completion+4,"Preview counts only new unlocks "+id);
        check(Arrays.equals(before,t.save()),"Preview is read-only "+id);check(p.apply(t).total==preview.total,"Apply matches preview "+id);
        for(int index=0;index<100;index++)check(t.read(100,index)==(allowed(family,index)?1:23+(index%17)),"Only reviewed completion global changed "+id+":"+index);
        for(int index:new int[]{0,17,18,CG_COUNT[family]-1})check(t.read(97,index)==1,"Exact CG offset including independent boundary "+id+":"+index);
        for(int index:new int[]{0,29,30,1023})check(t.read(98,index)==1,"Exact seen offset including independent boundary "+id+":"+index);
        check(t.read(97,CG_COUNT[family]-2)==0&&t.read(98,1022)==0,"Byte arrays do not inherit packed or neighboring flags "+id);
        check(host.familyName.equals("Own")&&host.givenName.equals("Name")&&t.read(99,8)==4&&t.read(99,13)==3,"Names/settings retained "+id);
        check(t.read(95,0)==37&&t.read(95,VOICE[family]-1)==55&&t.read(90,1023)==90,"Voice flags and scratch retained "+id);
        check(host.variables.read(3,4)==123&&host.readCheckpoints.contains("local:42")&&host.availableSaves.equals(Collections.singleton(6)),"Running story, read-text journal and story-slot availability retained "+id);
        byte[] merged=t.save();check(Arrays.equals(Arrays.copyOf(before,pcp3(before)),Arrays.copyOf(merged,pcp3(merged))),"Complete parent settings/state prefix preserved byte-for-byte "+id);
        check(p.apply(t).total==0&&Arrays.equals(merged,t.save()),"Repeated import byte-for-byte idempotent "+id);
        NativeProgress.Target restored=target(family);restored.load(merged);
        check(p.preview(restored).total==0&&Arrays.equals(merged,restored.save()),"New host persistent roundtrip "+id);
        check(NativeProgress.parse(id,new byte[SIZE[family]]).apply(t).total==0&&Arrays.equals(merged,t.save()),"Fresh PC source cannot erase phone unlocks "+id);
        reject(()->target(1-family).load(merged),"Cross-family native runtime layout rejected "+id);
        reject(()->target(family).load(Arrays.copyOf(merged,merged.length-1)),"Truncated phone progress rejected "+id);
        reject(()->target(family).load(Arrays.copyOf(merged,merged.length+1)),"Trailing phone progress rejected "+id);
        NativeProgress.Target noncanonical=target(family);noncanonical.write(97,0,2);noncanonical.write(98,0,255);
        if(COMPLETION[family].length>0)noncanonical.write(100,COMPLETION[family][0],2);p.apply(noncanonical);
        check(noncanonical.read(97,0)==2&&noncanonical.read(98,0)==255,"Existing nonzero flags retained without normalization "+id);
        if(COMPLETION[family].length>0)check(noncanonical.read(100,COMPLETION[family][0])==2,"Existing nonzero completion retained "+id);
    }
    private static int pcp3(byte[] bytes)throws IOException{
        for(int n=0;n+4<=bytes.length;n++)if(bytes[n]==0x50&&bytes[n+1]==0x43&&bytes[n+2]==0x50&&bytes[n+3]==0x33)return n;
        throw new IOException("Synthetic PCP3 prefix missing");
    }
    private static byte[][] tails(byte[] bytes)throws IOException{
        DataInputStream in=new DataInputStream(new ByteArrayInputStream(bytes,pcp3(bytes)+4,bytes.length-pcp3(bytes)-4));
        for(int bank=0;bank<5;bank++){int length=in.readInt();in.skipBytes(length);}byte[][] result=new byte[5][];
        for(int bank=0;bank<5;bank++){result[bank]=new byte[in.readInt()];in.readFully(result[bank]);}return result;
    }
    private static void oldTailMerge(int family,int version)throws Exception{
        NativeProgress.Target old=target(family);byte[] current=old.save();ByteArrayOutputStream bytes=new ByteArrayOutputStream();bytes.write(current,0,pcp3(current));DataOutputStream out=new DataOutputStream(bytes);out.writeInt(version);
        int[] oldSizes={100,1600,1600,128,100},nativeSizes={100,CG_COUNT[family],1024,VOICE[family],1024};
        for(int bank=0;bank<5;bank++)for(int index=0;index<oldSizes[bank];index++)out.writeByte(index>=nativeSizes[bank]?(index*17+bank*13)&255:0);
        out.writeInt(0x13579bdf);out.writeInt(0x2468ace0);if(version==0x50435032){out.writeUTF("Keep");out.writeUTF("Mine");}
        old.load(bytes.toByteArray());byte[][] before=tails(old.save());NativeProgress p=NativeProgress.parse(IDS[family],fixture(family));
        check(p.apply(old).total==p.source.total,"Merge into migrated original portable state "+IDS[family]+"/"+version);byte[] merged=old.save();byte[][] after=tails(merged);
        for(int bank=0;bank<5;bank++)check(Arrays.equals(before[bank],after[bank])&&after[bank].length==Math.max(0,oldSizes[bank]-nativeSizes[bank]),"Opaque legacy tail survives selective merge "+IDS[family]+"/"+bank);
        if(version==0x50435032)check(((PcHost)old.legacy).familyName.equals("Keep")&&((PcHost)old.legacy).givenName.equals("Mine"),"Migrated player names survive import "+IDS[family]);
        NativeProgress.Target restored=target(family);restored.load(merged);check(Arrays.equals(merged,restored.save())&&p.preview(restored).total==0,"Migrated tails and merged unlocks survive another restart "+IDS[family]);
    }
    public static int run()throws Exception{
        checks=0;for(int family=0;family<2;family++){identity(family);merge(family);oldTailMerge(family,0x50435031);oldTailMerge(family,0x50435032);}
        return checks;
    }
    public static void main(String[] args)throws Exception{System.out.println("PcScFlProgressTests: "+run()+" assertions passed");}
}
