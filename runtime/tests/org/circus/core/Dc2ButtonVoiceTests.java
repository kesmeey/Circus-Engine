package org.circus.core;

import java.io.*;
import java.util.*;

/** Native 3D/3E region properties and 40C6F0/40C810 -> 41F130 playback. */
public final class Dc2ButtonVoiceTests {
    private static int count;
    private static void check(boolean value,String why){count++;if(!value)throw new AssertionError(why);}
    private static final class Fixture {
        final PcHost host;final MesVm vm;final MesScript script;
        Fixture(byte[] records)throws IOException{
            final byte[] names=new byte[20];GameResources files=new GameResources(new GameResources.Storage(){
                public Set<String> paths(){return Collections.singleton("AdvData/DAT/NAMES.DAT");}
                public byte[] read(String path,int limit){return names.clone();}
                public Archive.Source source(String path){return new Archive.Source(){public long size(){return names.length;}public byte[] read(long start,int length)throws IOException{Binary.range(start,length,names.length);return Arrays.copyOfRange(names,(int)start,(int)start+length);}};}
            });Profile profile=Profiles.get("dc2-3363");host=Pc1TestDefaults.bind(new PcHost(new CircusResources(files,profile),TextCodec.identity()));
            ByteArrayOutputStream bytes=new ByteArrayOutputStream();bytes.write(new byte[]{0,0,0,0,0x63,0x33});bytes.write(records);bytes.write(new byte[]{0,0,0});script=new MesScript("native-buttons",bytes.toByteArray(),profile);vm=new MesVm(name->script,host,script.name);
        }
    }
    private static void string(ByteArrayOutputStream out,int opcode,String value)throws IOException{out.write(opcode);out.write(value.getBytes(Binary.SJIS));out.write(0);}
    private static byte[] records()throws IOException{ByteArrayOutputStream out=new ByteArrayOutputStream();string(out,0x3d,"VOICE\\SYSTEM\\click");string(out,0x3e,"VOICE\\SYSTEM\\hover");return out.toByteArray();}
    private static byte[] saveHost(PcHost host)throws IOException{ByteArrayOutputStream bytes=new ByteArrayOutputStream();host.save(new DataOutputStream(bytes));return bytes.toByteArray();}
    public static int run()throws Exception {
        count=0;Fixture empty=new Fixture(records());Dc2Ui.Button grid=new Dc2Ui.Button();grid.voice="grid-old";empty.host.grid.add(grid);
        check(empty.vm.run(2)==MesVm.Status.RUNNING&&empty.host.ui.buttons.isEmpty()&&grid.voice.equals("grid-old")&&grid.hoverVoice.isEmpty(),"empty native region list makes both property handlers no effect, without borrowing the separate thumbnail grid");
        Fixture f=new Fixture(records());Dc2Ui.Button first=new Dc2Ui.Button(),last=new Dc2Ui.Button();first.voice="untouched";last.clickSound=7;last.hoverSound=8;last.action="target";last.binding=123;f.host.ui.buttons.add(first);f.host.ui.buttons.add(last);
        check(f.vm.run(2)==MesVm.Status.RUNNING&&last.voice.equals("VOICE\\SYSTEM\\click")&&last.hoverVoice.equals("VOICE\\SYSTEM\\hover"),"raw native3D/3E update the last current region's distinct properties");
        check(last.clickSound==255&&last.hoverSound==255&&first.voice.equals("untouched")&&last.action.equals("target")&&last.binding==123,"voice setters disable only the corresponding SE and preserve the region action, binding and earlier buttons");
        Dc2Ui.Button copied=Dc2Ui.copyButtons(Collections.singletonList(last)).get(0),view=last.controlView("@native-input");
        check(copied!=last&&copied.voice.equals(last.voice)&&copied.hoverVoice.equals(last.hoverVoice)&&view.hoverVoice.equals(last.hoverVoice),"deep template copies and platform control views retain both native voice fields");
        f.host.command("pc.button_template",new int[]{1,0},new byte[0]);check(f.host.ui.buttons.isEmpty()&&f.host.ui.buttonTemplates.get(1).get(1).hoverVoice.equals(last.hoverVoice),"native button-template ownership keeps hover voice after clearing the pending region list");
        f.host.ui.buttons.add(copied);Fixture restored=new Fixture(records());restored.host.load(new DataInputStream(new ByteArrayInputStream(saveHost(f.host))));
        check(restored.host.ui.buttons.get(0).hoverVoice.equals(last.hoverVoice)&&restored.host.ui.buttonTemplates.get(1).get(1).voice.equals(last.voice),"host snapshots preserve active and templated region click/hover voice properties");
        int[] nativeDefaults={1,1,25,25,29,1,8,1,0,1,1,8,1,1};for(int n=0;n<nativeDefaults.length;n++)check(restored.host.read(99,5+n)==nativeDefaults[n],"current DC2 native default table register99:"+(5+n));
        check(restored.host.read(99,20)==1,"current DC2 explicit register99:20 default comes from473F94");restored.host.hoverMenu(0);check(restored.host.audioActive[5]&&restored.host.audio[5].equals(last.hoverVoice)&&!restored.host.audioActive[1]&&!restored.host.audioActive[2],"hover plays native channel2 through logical voice channel5, without conflating it with SE channels");
        check(Math.abs(restored.host.voiceGain()-0.89125094f)<0.000001f,"native DC2 level29 yields minus one decibel instead of a linear 29/30 multiplier");
        int generation=restored.host.audioGeneration[5];restored.host.hoverMenu(0);check(restored.host.audioGeneration[5]==generation,"remaining inside a region does not restart its hover voice");
        restored.host.hoverMenu(-1);restored.host.hoverMenu(0);check(restored.host.audioGeneration[5]==generation+1,"leaving and re-entering a region starts its hover voice again");
        restored.host.write(99,15,0);generation=restored.host.audioGeneration[5];restored.host.toolbarSound(copied);
        check(restored.host.audioGeneration[5]==generation&&restored.host.audioActive[5],"button voice SE gate rejects a new request without stopping the previous native voice");
        restored.host.write(99,15,1);restored.host.write(99,5,0);restored.host.toolbarSound(copied);
        check(restored.host.audioGeneration[5]==generation+1&&restored.host.audio[5].equals(last.voice),"script99:5 is not the independent native voice-enable field and cannot mute button speech");
        restored.host.write(99,9,0);float playing=restored.host.voiceGain();check(playing>0.89f,"native voice-volume assignment alone does not reconfigure the active channel");
        restored.host.write(99,7,25);check(restored.host.voiceGain()==0.00001f,"native music-volume setter refreshes both active BGM and voice gains through41F0C0");
        restored.host.write(99,9,29);restored.host.toolbarSound(copied);playing=restored.host.voiceGain();restored.host.write(99,9,0);Fixture playingCopy=new Fixture(records());playingCopy.host.load(new DataInputStream(new ByteArrayInputStream(saveHost(restored.host))));
        check(playingCopy.host.read(99,9)==0&&playingCopy.host.voiceGain()==playing&&playingCopy.host.audioActive[5],"snapshot retains captured voice gain separately from later preference writes");
        String[] tags={"otm","yme","mnt","kko","nnk","anz","akn","mya","myk","wtr","sgn","skr","yzu","myu","mik","gai","gse","hks","hrm","jun","sin","yto","yki","ysy","syo","rdj"};
        for(int n=0;n<tags.length;n++)check(Dc2Voice.character("VOICE\\"+tags[n].toUpperCase(Locale.ROOT)+"\\line")==Math.min(n,15),"independent native role table row "+n);
        check(Dc2Voice.character("VOICE/otm/line")==17&&Dc2Voice.character("otm\\line")==17&&Dc2Voice.character("VOICE\\otmLong\\line")==0,"native role matcher checks only three bytes after an actual backslash");
        check(Dc2Voice.forced("VOICE\\systemLong\\line")&&Dc2Voice.forced("OMAKE\\line")&&Dc2Voice.forced("VOICE\\Gaya\\line")&&!Dc2Voice.forced("VOICE/SYSTEM/line"),"native forced prefixes use their exact segment lengths and backslash delimiter");
        PcHost muted=new Fixture(records()).host;Dc2Ui.Button ordinary=new Dc2Ui.Button();ordinary.voice="VOICE\\otm\\line";muted.write(100,60,1);muted.toolbarSound(ordinary);
        check(!muted.audioActive[5]&&muted.audioGeneration[5]==0,"native nonzero role mute rejects region speech before replacing any channel");
        ordinary.voice="VOICE\\SYSTEM\\line";muted.toolbarSound(ordinary);check(muted.audioActive[5],"forced system speech bypasses role mute");
        generation=muted.audioGeneration[5];ordinary.voice="";muted.toolbarSound(ordinary);check(muted.audioGeneration[5]==generation,"empty raw button voice retains the previous channel");
        check(Dc2Voice.hundredthDb(0,0)==-10000&&Dc2Voice.hundredthDb(0,-1)==-3000&&Dc2Voice.hundredthDb(0,31)==0&&Dc2Voice.hundredthDb(-500,20)==-1500,"native zero special case and nonzero level/master clamps match41E5D0");
        Dc2Ui.Button legacy=new Dc2Ui.Button();ByteArrayOutputStream prior=new ByteArrayOutputStream();DataOutputStream wire=new DataOutputStream(prior);wire.writeInt(1);legacy.save(wire);ByteArrayOutputStream current=new ByteArrayOutputStream();Dc2Ui.saveButtons(new DataOutputStream(current),Collections.singletonList(legacy));
        check(Arrays.equals(prior.toByteArray(),current.toByteArray()),"lists without hover voice keep the exact legacy serialized layout");
        return count;
    }
    public static void main(String[] args)throws Exception{System.out.println("PASS "+run()+" DC2 region voice assertions");}
}
