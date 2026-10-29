package org.circus.core;

import java.io.*;
import java.util.*;

/** Synthetic original-save tests: no native files or game resources are distributed. */
public final class PcNativeProgressTests {
    private static int count;
    private static void check(boolean value,String why){count++;if(!value)throw new AssertionError(why);}
    private interface Work{void run()throws Exception;}
    private static void reject(Work work,String why)throws Exception{boolean rejected=false;try{work.run();}catch(IOException expected){rejected=true;}check(rejected,why);}
    private static PcHost host(String profile)throws Exception{
        GameResources files=new GameResources(new GameResources.Storage(){
            public Set<String> paths(){return Collections.singleton("AdvData/DAT/NAMES.DAT");}
            public byte[] read(String path,int limit){return new byte[20];}
            public Archive.Source source(String path){return new Archive.Source(){public long size(){return 20;}public byte[] read(long at,int n)throws IOException{Binary.range(at,n,20);return new byte[n];}};}
        });
        PcHost result=new PcHost(new CircusResources(files,Profiles.get(profile)),TextCodec.identity());
        result.attach(new Variables(result,result.second?Variables.Model.PC_WIDE:Variables.Model.PC_BYTE));return result;
    }
    private static byte[] persistent(PcHost host)throws IOException{ByteArrayOutputStream bytes=new ByteArrayOutputStream();host.savePersistent(new DataOutputStream(bytes));return bytes.toByteArray();}
    public static int run()throws Exception{
        count=0;String id="dc2pc-5769";
        check(NativeProgress.supports(id)&&NativeProgress.supports("dcpc-2063")&&!NativeProgress.supports(null),"Native save format is bound to the reviewed PC2 version");
        for(int size:new int[]{0,3311,3313})reject(()->NativeProgress.parse(id,new byte[size]),"Native save exact length required: "+size);
        reject(()->NativeProgress.parse(id,null),"Missing native bytes rejected");
        reject(()->NativeProgress.parse("dc2dm-9d72",new byte[3312]),"Another game cannot be forced into PC2 layout");
        reject(()->NativeProgress.parse("dcpc-2063",new byte[3312]),"PC1 does not inherit PC2 support");
        for(int at:new int[]{0x70,0x70+29,0xd4,0xd4+1499,0x6b0,0x6b0+1499}){
            byte[] bad=new byte[3312];bad[at]=(byte)255;
            reject(()->NativeProgress.parse(id,bad),"Malformed unlock flag rejected at "+at);
        }
        byte[] original=new byte[3312];Arrays.fill(original,0,0x70,(byte)0xfe);
        Arrays.fill(original,0x70,0xd4,(byte)255);
        int[] completion={0,1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,17,18,19,21,28,29};
        for(int n:completion)original[0x70+n]=1;
        original[0xd4]=original[0xd4+45]=original[0xd4+1499]=1;
        original[0x6b0]=original[0x6b0+88]=original[0x6b0+1499]=1;
        Arrays.fill(original,0xc8c,3312,(byte)255);
        NativeProgress progress=NativeProgress.parse(id,original);
        check(progress.source.completion==23&&progress.source.cg==3&&progress.source.scenes==3&&progress.source.total==29,"Source counts only reviewed unlock banks, ignoring native settings/names/voice preferences");
        Arrays.fill(original,(byte)0);
        check(progress.profileId.equals(id)&&progress.sourceSummary.contains("3312"),"Parsed source identifies supported layout");
        PcHost target=host(id);target.familyName="Own";target.givenName="Name";target.write(99,8,4);target.write(99,13,3);
        target.write(100,1,1);target.write(100,20,7);target.write(100,56,9);target.write(100,57,5);target.write(100,60,1);target.write(100,99,17);
        target.write(97,45,1);target.write(97,46,1);target.write(97,1500,1);target.write(98,88,1);target.write(98,89,1);target.write(98,1500,1);
        target.write(95,1,0);target.write(90,7,90);target.variables.write(3,4,123);target.readCheckpoints.add("local:42");target.availableSaves.add(6);
        byte[] before=persistent(target);NativeProgress.Summary preview=progress.preview(target);
        check(preview.completion==22&&preview.cg==2&&preview.scenes==2&&preview.total==26,"Preview reports only additions, not existing unlocks");
        check(Arrays.equals(before,persistent(target)),"Preview leaves persistent data byte-for-byte unchanged");
        NativeProgress.Summary applied=progress.apply(target);
        check(applied.total==preview.total&&progress.preview(target).total==0,"Apply matches preview and records progress despite caller modifying source bytes");
        for(int n:completion)check(target.read(100,n)==1,"Reviewed completion flag imported: "+n);
        check(target.read(97,0)==1&&target.read(97,1499)==1&&target.read(98,0)==1&&target.read(98,1499)==1,"First and last native CG/scene entries use independent exact offsets");
        check(target.read(97,46)==1&&target.read(98,89)==1&&target.read(97,1500)==1&&target.read(98,1500)==1,"Existing phone-only unlocks and wider host tail survive import");
        check(target.read(100,20)==7&&target.read(100,56)==9&&target.read(100,57)==5&&target.read(100,60)==1&&target.read(100,99)==17,"Unreviewed globals, title setting, temporary state and voice mute are preserved");
        check(target.familyName.equals("Own")&&target.givenName.equals("Name")&&target.read(99,8)==4&&target.read(99,13)==3&&target.read(95,1)==0&&target.read(90,7)==90,"Phone names, settings, voice flags and scratch are preserved");
        check(target.variables.read(3,4)==123&&target.readCheckpoints.contains("local:42")&&target.availableSaves.equals(Collections.singleton(6)),"Import does not replace story variables, read text or slot availability");
        byte[] merged=persistent(target);check(progress.apply(target).total==0&&Arrays.equals(merged,persistent(target)),"Repeated import is byte-for-byte idempotent");
        PcHost reloaded=host(id);reloaded.loadPersistent(new DataInputStream(new ByteArrayInputStream(merged)));
        check(progress.preview(reloaded).total==0&&Arrays.equals(merged,persistent(reloaded)),"Imported unlocks round-trip through existing phone persistent format");
        check(NativeProgress.parse(id,new byte[3312]).apply(target).total==0&&Arrays.equals(merged,persistent(target)),"Fresh PC save cannot reset phone progress");
        PcHost wrong=host("dcpc-2063");byte[] wrongBefore=persistent(wrong);
        reject(()->progress.preview(wrong),"Read-only preview rejects mismatched host");reject(()->progress.apply(wrong),"Mutation rejects mismatched host");
        check(Arrays.equals(wrongBefore,persistent(wrong)),"Wrong host remains unchanged");reject(()->progress.apply((Dc2Host)null),"Missing target rejected");
        PcHost noncanonical=host(id);noncanonical.write(100,1,2);noncanonical.write(97,0,2);noncanonical.write(98,0,2);progress.apply(noncanonical);
        check(noncanonical.read(100,1)==2&&noncanonical.read(97,0)==2&&noncanonical.read(98,0)==2,"Existing nonzero target values are never normalized or overwritten");
        return count;
    }
}
