package org.circus.core;

import java.io.*;
import java.util.*;

/** Actual PP opcodes reach playback, metadata gain, persistent flags and the saved host. */
public final class PpMusicHostTests {
    private static int count;
    private static void check(boolean value,String why){count++;if(!value)throw new AssertionError(why);}
    private static void near(float value,double expected,String why){check(Math.abs(value-expected)<0.000001,why+": "+value);}
    private static void integer(ByteArrayOutputStream out,int value){for(int n=0;n<4;n++)out.write(value>>>(8*n));}
    private static byte[] names()throws IOException {
        ByteArrayOutputStream out=new ByteArrayOutputStream();integer(out,0);integer(out,3);
        for(int n=0;n<3;n++){byte[] row=new byte[48];byte[] name=("BGM/track"+n).getBytes(Binary.SJIS);System.arraycopy(name,0,row,0,name.length);row[44]=(byte)(n+2);out.write(row);}
        integer(out,1);byte[] sound=new byte[36];byte[] name="SE/test".getBytes(Binary.SJIS);System.arraycopy(name,0,sound,0,name.length);sound[32]=3;out.write(sound);integer(out,0);integer(out,0);return out.toByteArray();
    }
    private static CircusResources resources()throws IOException {
        final byte[] names=names();GameResources files=new GameResources(new GameResources.Storage(){
            public Set<String> paths(){return Collections.singleton("AdvData/GRP/NAMES.DAT");}
            public byte[] read(String path,int limit)throws IOException{if(limit<names.length)throw new IOException("Fixture limit");return names.clone();}
            public Archive.Source source(String path){return new Archive.Source(){public long size(){return names.length;}public byte[] read(long offset,int length)throws IOException{Binary.range(offset,length,names.length);return Arrays.copyOfRange(names,(int)offset,(int)offset+length);}};}
        });return new CircusResources(files,Profiles.get("dc3pp-9872"));
    }
    private static MesScript script()throws IOException {
        return new MesScript("native-music",new byte[]{0,0,0,0,0x72,(byte)0x98,0,5,1,0,6,2,99,0,0,0},Profiles.get("dc3pp-9872"));
    }
    private static byte[] hostState(Dc3Host host)throws IOException{ByteArrayOutputStream bytes=new ByteArrayOutputStream();host.save(new DataOutputStream(bytes));return bytes.toByteArray();}
    public static int run()throws Exception {
        count=0;CircusResources resources=resources();MesScript code=script();
        Dc3Host host=new Dc3Host(resources,TextCodec.identity(),true);MesVm vm=new MesVm(name->code,host,code.name);
        check(vm.run(1)==MesVm.Status.RUNNING&&vm.offset()==13,"05 consumes its optional06 three-byte duck companion");
        check(host.audioIsActive(0)&&host.audio[0].equals("BGM/track1")&&host.audioLoop[0],"05 produces a real BGM channel request immediately");
        check(host.read(96,1)==1&&host.read(99,244)==1,"Music request unlocks its native bit and is returned by244");
        int generation=host.audioGeneration[0];host.command("pp.audio.music",new int[]{1,1,2},new byte[0]);
        check(host.audioGeneration[0]==generation,"Same track/mode1/duck reuses current playback");
        float first=host.ppOutputGain(0);host.command("pp.audio.duck",new int[]{3,0},new byte[0]);
        check(host.audioGeneration[0]==generation&&host.ppOutputGain(0)!=first,"Standalone06 refreshes gain without restarting resource");
        host.command("pp.audio.music",new int[]{1,0,0},new byte[0]);
        check(host.audioGeneration[0]==generation,"Mode0 queues same-track restart behind native buffer fade");
        host.elapse(1000);check(host.audioGeneration[0]==generation+1,"Replacement starts only after asynchronous fade completes");
        near(host.ppOutputGain(0),Math.pow(10,-1960/2000.0),"Metadata extra3=3 contributes -300 hundredth dB");
        host.write(99,7,30);near(host.ppOutputGain(0),Math.pow(10,-300/2000.0),"99:7 refreshes the native channel gain");
        generation=host.audioGeneration[0];host.command("pp.audio.music",new int[]{2,2,0},new byte[0]);
        check(host.audioGeneration[0]==generation&&host.audioLoop[0]&&host.read(99,244)==2,"Mode2 records request while previous loop remains audible");
        host.elapse(1000);check(!host.audioLoop[0]&&host.audio[0].equals("BGM/track2"),"Mode2 queued submission is a single play");
        byte[] before=hostState(host);host.command("pp.audio.music",new int[]{9,10,90},new byte[0]);host.command("pp.audio.music",new int[]{9,11,90},new byte[0]);
        check(Arrays.equals(before,hostState(host)),"05 modes10/11 are true native empty calls with no host state mutation");
        host.enterSystem();host.command("pp.audio.music",new int[]{0,0,0},new byte[0]);host.command("pp.audio.music",new int[]{254,0,0},new byte[0]);
        host.elapse(1000);
        check(host.audio[0].equals("BGM/track2")&&host.audioLoop[0],"FE restores the caller's remembered track and loops");
        byte[] snapshot=vm.snapshot();Dc3Host restored=new Dc3Host(resources,TextCodec.identity(),true);MesVm fresh=new MesVm(name->code,restored,code.name);fresh.restore(snapshot);
        check(Arrays.equals(snapshot,fresh.snapshot())&&restored.ppOutputGain(0)==host.ppOutputGain(0),"CVS2 restores BGM request, metadata and gain phase exactly");
        host.command("pp.audio.music",new int[]{255,10,0},new byte[0]);check(!host.audioIsActive(0)&&host.audio[0].isEmpty()&&host.read(99,244)==-1,"FF stops immediately even when mode byte is10");
        host.write(99,14,0);generation=host.audioGeneration[0];host.command("pp.audio.music",new int[]{1,0,0},new byte[0]);
        check(host.audioGeneration[0]==generation&&!host.audioIsActive(0)&&host.read(96,1)==1&&host.read(99,244)==1,"Disabled BGM remembers/unlocks requests without submitting audio");
        host.write(99,14,1);check(host.audioIsActive(0)&&host.audio[0].equals("BGM/track1")&&host.audioLoop[0],"Enabling BGM replays the remembered request");
        host.write(99,7,20);float beforeDisable=host.ppOutputGain(0);generation=host.audioGeneration[0];host.write(99,14,0);
        near(host.ppOutputGain(0),beforeDisable,"Disabling starts an audible native fade rather than a hard volume gate");
        host.elapse(369);check(host.audioIsActive(0)&&host.audioGeneration[0]==generation,"Ordinary fade retains buffer until its final native event");
        host.elapse(1);check(!host.audioIsActive(0)&&host.read(99,244)==1,"Disable releases buffer after fade but keeps the interpreter request");
        host.write(99,14,1);generation=host.audioGeneration[0];host.audioCompleted(0,generation);
        host.command("pp.audio.music",new int[]{1,0,0},new byte[0]);check(host.audioGeneration[0]==generation,"Naturally completed PCM keeps native allocation and queues a mode0 replay");
        host.elapse(1000);check(host.audioIsActive(0)&&host.audioGeneration[0]==generation+1,"Queued replay releases the completed buffer and starts again");
        host.command("audio.music_stop",new int[]{0,0},new byte[0]);host.command("pp.audio.duck",new int[]{0,0},new byte[0]);
        check(host.read(99,244)==-1,"Opcode12 request cannot be revived by a later standalone06");
        Dc3Host enableOnly=new Dc3Host(resources,TextCodec.identity(),true);
        enableOnly.command("pp.audio.music",new int[]{0,17,0},new byte[0]);enableOnly.write(99,14,1);
        check(enableOnly.audioIsActive(0)&&enableOnly.read(96,0)==0,"Preference enable replays default-mode request without inventing a script music unlock");
        host.audio[1]="SE/test";near(host.ppOutputGain(1),Math.pow(10,-1462/2000.0),"SE metadata extra0=3 is applied once, after native23-level preference");
        Dc3Host query=new Dc3Host(resources,TextCodec.identity(),true);MesVm queryVm=new MesVm(name->code,query,code.name);queryVm.run(1);
        check(query.read(99,250)==0,"PP250 observes actual playing PCM rather than default register zero");int queryGeneration=query.audioGeneration[0];query.audioCompleted(0,queryGeneration-1);check(query.read(99,250)==0,"stale completion cannot end current PP250 playback");query.audioCompleted(0,queryGeneration);check(query.read(99,250)==1&&query.read(99,244)==1,"natural completion changes PP250 but preserves requested track and allocated buffer");
        Dc3Host muted=new Dc3Host(resources,TextCodec.identity(),true);MesVm mutedVm=new MesVm(name->code,muted,code.name);mutedVm.run(1);muted.write(99,14,0);check(muted.audioIsActive(0)&&muted.read(99,250)==1,"PP250 checks disabled preference before still-playing fade buffer");muted.write(99,14,1);check(muted.read(99,250)==0,"nonzero preference queries playing old buffer during queued replacement");
        return count;
    }
    public static void main(String[] args)throws Exception{System.out.println("PP music host assertions: "+run());}
}
