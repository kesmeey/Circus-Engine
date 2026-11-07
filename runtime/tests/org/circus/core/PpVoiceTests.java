package org.circus.core;

import java.io.*;
import java.util.*;

/** PP_CN native role/mute/commit/gain rules, independent of DD's character banks. */
public final class PpVoiceTests {
    private static int count;
    private static void check(boolean value,String why){count++;if(!value)throw new AssertionError(why);}
    private static CircusResources resources()throws IOException {
        final byte[] names=new byte[20];GameResources files=new GameResources(new GameResources.Storage(){
            public Set<String> paths(){return Collections.singleton("AdvData/GRP/NAMES.DAT");}
            public byte[] read(String path,int limit)throws IOException{if(limit<names.length)throw new IOException("Fixture limit");return names.clone();}
            public Archive.Source source(String path){return new Archive.Source(){public long size(){return names.length;}public byte[] read(long start,int length)throws IOException{Binary.range(start,length,names.length);return Arrays.copyOfRange(names,(int)start,(int)start+length);}};}
        });return new CircusResources(files,Profiles.get("dc3pp-9872"));
    }
    private static Dc3Host host()throws IOException{Dc3Host h=new Dc3Host(resources(),TextCodec.identity(),true);h.attach(new Variables(h,Variables.Model.DC2_PACKED));return h;}
    private static void system(Dc3Host h,String source)throws IOException{h.command("audio.system_voice",new int[0],source.getBytes(Binary.SJIS));}
    private static MesScript script(String source)throws IOException{
        ByteArrayOutputStream out=new ByteArrayOutputStream();out.write(new byte[]{0,0,0,0,0x72,(byte)0x98,0,55});out.write(source.getBytes(Binary.SJIS));out.write(new byte[]{0,53,'L',0,0,0,0});return new MesScript("native-voice",out.toByteArray(),Profiles.get("dc3pp-9872"));
    }
    public static int run()throws Exception{
        count=0;String[] roles={"rcc","srr","hmn","sra","aoi","tmj","smm","yuk"};
        for(int n=0;n<roles.length;n++)check(PpVoice.role("VOICE\\dir_part\\000_"+roles[n].toUpperCase(Locale.ROOT)+"_other_"+roles[n])==n,"fixed native role table "+n);
        check(PpVoice.role("VOICE\\file") ==8&&PpVoice.role("VOICE\\SYSTEM\\a_rcc")==8,"other role and SYS path classification");
        check(PpVoice.role("VOICE/SYSTEM/a_rcc")==0&&!PpVoice.forced("VOICE/SYSTEM/a_rcc"),"forward slash is not a native role/force delimiter before resource resolution");
        check(PpVoice.forced("#file")&&PpVoice.forced("SYS_start")&&PpVoice.forced("VOICE\\SysOther\\x")&&!PpVoice.forced("VOICE\\notSYS\\x"),"native force markers and segment prefix tests");
        PpVoice.Request left=PpVoice.request("<b;line_rcc");check(left.pan==-1000&&left.role==1&&left.resource.equals("line_rcc"),"pan removal precedes semicolon role override");
        PpVoice.Request indexed=PpVoice.request("VOICE\\b;line_rcc");check(indexed.role==0&&indexed.resource.equals("VOICE\\b;line_rcc"),"VOICE index path does not consume semicolon");
        check(PpVoice.request("<#file").pan==-1000&&PpVoice.request("<#file").resource.equals("#file")&&PpVoice.request("<#file").forced,"force marker remains part of the native resource");
        check(PpVoice.request("#<file").pan==0&&PpVoice.request("#<file").resource.equals("#<file"),"only the first raw byte is a pan marker");
        check(PpVoice.hundredthDb(0,27)==-498&&Math.abs(PpVoice.gain(0,27)-0.56363797f)<0.000001f,"native default27 gain is not volume/100");
        check(PpVoice.hundredthDb(0,0)==-9999&&PpVoice.gain(0,0)>0&&PpVoice.gain(0,0)<0.000011f,"zero volume preserves native near-silence rather than mathematical zero");
        check(PpVoice.hundredthDb(0,30)==0&&PpVoice.hundredthDb(0,100)==0&&PpVoice.hundredthDb(-9900,27)==-9999,"native upper clamp and master-db lower clamp");
        check(AudioLevels.directSoundBalance(0)==0&&Math.abs(AudioLevels.directSoundBalance(1000)-0.6837722f)<0.000001f&&AudioLevels.directSoundBalance(-1000)==-AudioLevels.directSoundBalance(1000),"shared output adapters convert native 1000 pan into ten-decibel attenuation");
        Dc3Host h=host();check(h.read(99,5)==1&&h.read(99,9)==27,"PP fresh global enable and volume defaults");
        for(int bank:new int[]{93,94,95}){h.write(bank,0,99);check(h.read(bank,0)==0&&h.read(bank,50)==0,"nonexistent PP bank "+bank+" reads zero and ignores writes");}
        h.write(99,203,1);check(h.read(99,203)==0,"DD master-enable register does not exist in PP");h.write(99,217,3);check(h.read(99,217)==0,"DD confirmation-indirection register does not exist in PP");
        system(h,"<VOICE\\000_rcc");check(h.audioIsActive(5)&&!h.audioIsActive(2)&&h.audioPan(5)==-1000&&h.audioResource(5).equals("VOICE\\000_rcc"),"system voice uses the shared voice channel with native pan and actual resource");
        float gain=h.voiceGain();int generation=h.audioGeneration[5];h.write(99,9,0);h.write(99,5,0);
        check(h.audioIsActive(5)&&h.voiceGain()==gain&&h.read(99,249)==1,"preference writes do not retroactively stop/re-gain current voice, but disabled completion query is finished");
        system(h,"VOICE\\new_rcc");check(h.audioGeneration[5]==generation&&h.audio[5].equals("<VOICE\\000_rcc"),"rejected global-off request does not replace or stop previous voice");
        h.write(99,5,7);check(h.read(99,5)==7,"PP global enable setter preserves the native integer");h.write(100,60,1);system(h,"VOICE\\muted_rcc");check(h.audioGeneration[5]==generation,"nonzero per-character byte means mute");
        system(h,"#VOICE\\forced_rcc");check(h.audioGeneration[5]==generation+1&&h.audioResource(5).startsWith("#"),"forced voice bypasses role mute without stripping its resource marker");
        check(h.voiceGain()>0&&h.voiceGain()<0.000011f,"new submission captures current native gain");
        generation=h.audioGeneration[5];system(h,"");check(h.audioGeneration[5]==generation,"empty submission leaves the current channel intact");
        h.audioCompleted(5,generation-1);check(h.audioIsActive(5),"stale completion cannot stop a newer voice");h.audioCompleted(5,generation);check(!h.audioIsActive(5)&&h.read(99,249)==1,"matching completion updates PP finished query");
        MesScript script=script(">VOICE\\prepared_srr");Dc3Host prepared=host();MesVm vm=new MesVm(name->script,prepared,script.name);
        check(vm.run(1)==MesVm.Status.RUNNING&&!prepared.audioIsActive(5),"ordinary37 prepares without prematurely starting voice");byte[] snapshot=vm.snapshot();
        Dc3Host restored=host();MesVm loaded=new MesVm(name->script,restored,script.name);loaded.restore(snapshot);
        check(loaded.run(1)==MesVm.Status.RUNNING&&restored.audioIsActive(5)&&restored.audioPan(5)==1000,"ordinary voice commits before the next fetched instruction after snapshot restore");
        check(restored.audioResource(5).equals("VOICE\\prepared_srr"),"commit is independent of text.body or later publish");
        float captured=restored.voiceGain();restored.write(99,9,0);byte[] playing=loaded.snapshot();Dc3Host replayed=host();MesVm playback=new MesVm(name->script,replayed,script.name);playback.restore(playing);check(replayed.read(99,9)==0&&replayed.voiceGain()==captured&&replayed.audioIsActive(5),"snapshot restores playback gain captured before a later preference change");
        Dc3Host skipped=host();MesVm fast=new MesVm(name->script,skipped,script.name);fast.run(1);skipped.ppSkipVoice=true;fast.run(1);check(!skipped.audioIsActive(5),"fast-forward gate at the commit barrier suppresses prepared ordinary voice");
        return count;
    }
    public static void main(String[] args)throws Exception{System.out.println("PASS "+run()+" PP native voice assertions");}
}
