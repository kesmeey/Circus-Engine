package org.circus.core;
import java.io.*;
import java.util.*;

/** Synthetic regressions for old framing, byte order, split voice archives and UI coordinates. */
public final class PcCompatibilityTests {
    private static int count;
    private static void check(boolean value,String why){count++;if(!value)throw new AssertionError(why);}
    private interface Work{void run()throws Exception;}
    private static void reject(Work w,String why)throws Exception{boolean rejected=false;try{w.run();}catch(IOException e){rejected=true;}check(rejected,why);}
    private static Archive.Source source(byte[] b){return new Archive.Source(){public long size(){return b.length;}public byte[] read(long p,int n)throws IOException{Binary.range(p,n,b.length);return Arrays.copyOfRange(b,(int)p,(int)p+n);}};}
    private static byte[] script(int marker,byte...code)throws IOException{ByteArrayOutputStream b=new ByteArrayOutputStream();b.write(new byte[]{0,0,0,0,(byte)marker,(byte)(marker>>8)});b.write(code);return b.toByteArray();}
    private static byte[] voiceIndex(){byte[] b=new byte[136];b[0]=2;b[4]='A';b[64]=(byte)132;b[68]='B';b[128]=1;b[132]=10;b[133]=11;b[134]=12;b[135]=13;return b;}
    private static GameResources files(Map<String,byte[]> entries)throws IOException{return new GameResources(new GameResources.Storage(){public Set<String> paths(){return entries.keySet();}public byte[] read(String p,int limit)throws IOException{byte[] b=entries.get(p);if(b.length>limit)throw new IOException("limit");return b.clone();}public Archive.Source source(String p){return PcCompatibilityTests.source(entries.get(p));}});}
    public static int run()throws Exception{
        count=0;Profile one=Profiles.get("dcpc-2063"),two=Profiles.get("dc2pc-5769");
        byte[] pc1=script(0x2063,(byte)0),pc2=script(0x5769,(byte)0,(byte)0,(byte)0,(byte)'a',(byte)'d');
        check(new MesScript("one",pc1,one).firstOffset==6,"PC1 terminal byte and two-byte version");
        check(new MesScript("two",pc2,two).records().size()==1,"PC2 known ad trailer after stop");
        reject(()->new MesScript("wrong",pc1,two),"PC1 data is not PC2");
        reject(()->new MesScript("tail",script(0x2063,(byte)0,(byte)1),one),"Nonzero truncated terminal operands rejected");
        reject(()->new MesScript("tail",script(0x5769,(byte)0,(byte)0,(byte)0,(byte)'a',(byte)'e'),two),"Only reviewed trailer accepted");
        Variables v=new Variables(new DemoHost(),Variables.Model.PC_WIDE);v.write(9,0,300);check(v.read(9,0)==255,"PC clamps positive byte");v.write(9,0,-1);check(v.read(9,0)==0,"PC clamps negative byte");
        v.write(202,30,0x1234fedc);check(v.read(2,30)==0x12&&v.read(2,31)==0x34&&v.read(2,32)==0xfe&&v.read(2,33)==0xdc,"PC2 packed variable is big endian");check(v.read(202,30)==0x1234fedc,"Packed variable roundtrip");check(v.read(202,97)==0,"Packed boundary does not cross bank");
        Archive a=new Archive(source(voiceIndex()),source(new byte[]{20,21,22,23}));check(a.entries.size()==2,"Voice64 entry count");check(Arrays.equals(a.read(a.entries.get(0)),new byte[]{10,11,12,13}),"Primary voice span ends at primary EOF");check(Arrays.equals(a.read(a.entries.get(1)),new byte[]{20,21,22,23}),"Voice2 low-bit file selector and zero offset");reject(()->new Archive(source(voiceIndex()),true),"Missing VOICE2 rejected");
        byte[] malformed=voiceIndex();malformed[64]=(byte)140;reject(()->new Archive(source(malformed),source(new byte[20])),"Unflagged primary offset cannot address second file");
        Map<String,byte[]> map=new HashMap<>();map.put("wrapper/AdvData/MES/START.mes",script(0x2063,(byte)0,(byte)0,(byte)0));map.put("wrapper/AdvData/DAT/NAMES.DAT",new byte[20]);GameResources files=files(map);
        check(GameVersion.detect(files).profileId.equals(one.id),"Unique wrapper root resolved");PcHost host=new PcHost(new CircusResources(files,one),TextCodec.identity());host.attach(new Variables(host,Variables.Model.PC_BYTE));
        check(host.read(99,249)==1,"Old voice query reports completed when idle");host.audioActive[5]=true;check(host.read(99,249)==0,"Old voice query uses voice channel with inverted polarity");host.audioActive[5]=false;
        check(host.gridClickSound==255&&host.gridHoverSound==255,"PC native grid defaults do not inherit DM sound 224");
        MesScript context=new MesScript("context",script(0x2063,(byte)0x1b,(byte)1,(byte)0,(byte)0x3b,(byte)'x',(byte)0,(byte)0),one);
        MesVm contextVm=new MesVm(n->context,host,"context");check(contextVm.run(10)==MesVm.Status.HALTED,"Context capture consumes the following label string instead of executing it as a jump");
        MesScript signedDefault=new MesScript("signed",script(0x2063,(byte)0x81,(byte)0x83,(byte)0,(byte)0),one);
        MesVm signedVm=new MesVm(n->signedDefault,host,"signed");check(signedDefault.at(6).end==9&&signedVm.run(10)==MesVm.Status.HALTED,"PC1 signed default consumes three bytes and reaches the next native instruction");
        check(two.operation(0x81).equals("unknown"),"Signed default is restricted to the verified generation");
        host.command("pc.grid.configure",new int[]{800,0,824,200,64,136,784,464},new byte[0]);
        for(int n=0;n<3;n++)host.command("dm.ui.grid_rect",new int[]{0,n*80,288,n*80+72},new byte[0]);
        host.command("pc.command",new int[]{6,0},new byte[0]);host.command("pc.grid.wait",new int[]{0,0},new byte[0]);
        check(host.read(99,246)==2&&host.menuActionForGrid(0)==null,"PC manual save reserves slots zero and one");
        check(new String(host.menuActionForGrid(2),Binary.SJIS).equals("selsave")&&host.read(99,254)==2,"PC manual grid selects exact slot and label");
        PcHost secondHost=new PcHost(new CircusResources(files,two),TextCodec.identity());Variables secondVars=new Variables(secondHost,Variables.Model.PC_WIDE);secondHost.attach(secondVars);
        secondVars.write(9,0,12);secondVars.write(9,1,18);secondHost.command("pc.command",new int[]{1,1},new byte[0]);check(secondVars.read(9,2)==0,"PC2 native weekday uses day-of-year plus five");
        for(int n=0;n<3;n++)secondHost.command("dm.ui.grid_rect",new int[]{0,n*80,288,n*80+72},new byte[0]);
        secondHost.command("pc.command",new int[]{6,0},new byte[0]);secondHost.command("pc.grid.wait",new int[]{0,0},new byte[0]);check(new String(secondHost.menuActionForGrid(2),Binary.SJIS).equals("selsave"),"PC2 save uses save label");
        secondHost.availableSaves.add(2);secondHost.command("pc.command",new int[]{6,1},new byte[0]);secondHost.command("pc.grid.wait",new int[]{0,0},new byte[0]);check(new String(secondHost.menuActionForGrid(2),Binary.SJIS).equals("selsave2"),"PC2 load uses distinct load label");
        secondHost.ui.waiting=false;
        MesScript homeroom=new MesScript("0228_hr",script(0x2063,(byte)0),one);host.position(homeroom,6);host.command("pc.command",new int[]{1,0},new byte[0]);
        check(host.variables.read(9,0)==2&&host.variables.read(9,1)==28&&host.variables.read(9,3)==0,"Homeroom date resets period even with underscore suffix");
        check(one.operation(0x24).equals("pc.voice_group.play")&&two.operation(0x23).equals("pc.voice_group.play"),"Group playback is not an audio wait");
        host.command("pc.voice_group.add",new int[0],"VOICE/a.wav".getBytes(Binary.SJIS));host.command("pc.voice_group.add",new int[0],"VOICE/b.wav".getBytes(Binary.SJIS));host.command("pc.voice_group.play",new int[]{1,0},new byte[0]);
        check(host.audio[5].startsWith("mix:")&&host.audioActive[5],"Simultaneous voices produce one replayable recipe");
        host.command("pc.voice_group.play",new int[]{2,0},new byte[0]);host.write(100,60,1);host.command("pc.voice_group.add",new int[0],"VOICE/nem/test".getBytes(Binary.SJIS));host.command("pc.voice_group.play",new int[]{0,0},new byte[0]);check(!host.audioActive[5],"Random group playback applies the normal character mute");
        for(PcHost group:new PcHost[]{host,secondHost}){group.command("pc.voice_group.play",new int[]{2,0},new byte[0]);for(int n=0;n<10;n++)group.command("pc.voice_group.add",new int[0],("VOICE/v"+n).getBytes(Binary.SJIS));group.command("pc.voice_group.play",new int[]{1,0},new byte[0]);String recipe=new String(Base64.getDecoder().decode(group.audio[5].substring(4)),"UTF-8");check(recipe.split("\n").length==(group.second?6:7),"Simultaneous group honors the native device-channel limit");}
        Xpcm mixed=MixedVoice.mix(Arrays.asList(Xpcm.pcm(new byte[]{(byte)0x20,0x4e,(byte)0xe0,(byte)0xb1},1,44100,16),Xpcm.pcm(new byte[]{(byte)0x20,0x4e,(byte)0xe0,(byte)0xb1},1,44100,16)));
        check(Arrays.equals(mixed.payload,new byte[]{(byte)0xff,0x7f,0,(byte)0x80}),"Voice mixing clips signed positive and negative saturation");
        Xpcm resampled=MixedVoice.mix(Arrays.asList(Xpcm.pcm(new byte[]{0,0,(byte)0xe8,3},1,22050,16),Xpcm.pcm(new byte[16],2,44100,16)));
        check(resampled.channels==2&&resampled.payload.length==16&&(resampled.payload[4]&255)==244&&resampled.payload[5]==1,"Voice mixing resamples and expands mono consistently");
        secondHost.audioActive[0]=true;secondHost.audioActive[5]=true;secondHost.command("pc.wait.media_mask",new int[]{3,0},new byte[0]);secondHost.audioCompleted(0,secondHost.audioGeneration[0]);check(secondHost.waiting(),"Media mask still waits for voice after music EOF");secondHost.audioCompleted(5,secondHost.audioGeneration[5]);check(!secondHost.waiting(),"Media mask releases after both outputs complete");
        reject(()->secondHost.command("pc.wait.media_mask",new int[]{8,0},new byte[0]),"Unverified timer mask is explicit");
        PcCredits roll=new PcCredits();roll.start(1,5000);roll.elapse(9999);check(roll.scroll()==0,"PC2 credits honor the fifteen-second music clock lead");roll.elapse(1001);check(roll.scroll()>70&&roll.scroll()<71,"PC2 credits preserve native float speed");roll.elapse(400000);check(!roll.active,"PC2 credits finish naturally");
        roll.preparePc1();roll.active=true;roll.elapse(12000);check(roll.scroll()==1200&&roll.stripHeight()==1200,"PC1 credits advance three pixels each thirty milliseconds");
        Crx.Image colored=new Crx.Image(0,0,1,1,32,new byte[]{100,120,(byte)200,(byte)255},null);byte[] tint=CircusResources.tint(colored,'*',1).pixels;
        check((tint[0]&255)==75&&(tint[1]&255)==90&&(tint[2]&255)==166,"PC1 warm tint differs from PC2 and DD");
        host.command("pc.ui.wait",new int[]{2,1},new byte[0]);check(!host.ui.waiting&&host.remainingMillis()==400,"Polling wait does not expose stale buttons");host.elapse(400);
        host.command("pc.origin",new int[]{10,20,0,0},new byte[0]);host.command("pc.quad",new int[]{20,30,5,8,105,58,4,0},new byte[0]);Dc2Ui.Quad q=host.ui.quads.get(0);check(q.x==30&&q.y==50&&q.sx==5&&q.sy==8&&q.width==100&&q.height==50&&q.slot()==21,"Old quad source and texture selector conversion");
        host.write(100,60,1);host.command("dm.audio.voice",new int[0],"voice/nem/test".getBytes(Binary.SJIS));check(!host.audioActive[5],"PC1 character mute uses native flags");host.write(100,60,0);host.command("dm.audio.voice",new int[0],"voice/nem/test".getBytes(Binary.SJIS));check(host.audioActive[5],"PC1 unmute restores playback");
        secondHost.write(100,62,1);secondHost.command("dm.audio.voice",new int[0],"voice/scene_mnt001".getBytes(Binary.SJIS));check(!secondHost.audioActive[5],"PC2 Minatsu mapping differs from DM");secondHost.write(100,62,0);secondHost.command("dm.audio.voice",new int[0],"<voice/scene_mnt001".getBytes(Binary.SJIS));check(secondHost.audioActive[5]&&secondHost.voicePan==-1000&&secondHost.audio[5].equals("voice/scene_mnt001"),"PC2 positional voice strips prefix and retains native pan");host.write(99,13,3);
        host.familyName="Test";host.givenName="Name";host.write(100,90,177);ByteArrayOutputStream state=new ByteArrayOutputStream();host.savePersistent(new DataOutputStream(state));PcHost restored=new PcHost(new CircusResources(files,one),TextCodec.identity());restored.loadPersistent(new DataInputStream(new ByteArrayInputStream(state.toByteArray())));check(restored.read(100,90)==177,"PC progress survives restore independently of DM state");check(restored.familyName.equals("Test")&&restored.givenName.equals("Name"),"Custom player names survive persistent restore");
        check(restored.read(99,13)==3,"PC automatic text mode persists between launches");
        TextCodec gbk=new TextCodec("","","GBK");check(gbk.decode(new byte[]{(byte)0xd6,(byte)0xd0}).equals("中"),"Verified GBK text decode");check(!gbk.fingerprint.equals(TextCodec.identity().fingerprint),"Encoding participates in snapshot identity");reject(()->gbk.decode(new byte[]{(byte)0x81}),"Truncated GBK fails explicitly");
        map.put("other/AdvData/MES/START.mes",script(0x5769,(byte)0,(byte)0,(byte)0));reject(()->files(map),"Mixed wrapper games require an explicit root");
        byte[] aligned=alignedCrx(12,0);reject(()->Crx.plain(aligned),"Strict CRX rejects legacy row alignment tail");
        check(Crx.plain(aligned,true).width==3,"Legacy CRX accepts exact zero row-alignment allocation");
        reject(()->Crx.plain(alignedCrx(13,0),true),"Legacy CRX rejects arbitrary zero excess");
        reject(()->Crx.plain(alignedCrx(12,1),true),"Legacy CRX rejects nonzero alignment tail");
        inputBindings(files,one);inputBindings(files,two);
        return count;
    }
    private static void inputBindings(GameResources files,Profile profile)throws Exception {
        CircusResources data=new CircusResources(files,profile);
        for(boolean timed:new boolean[]{false,true})for(int button:new int[]{-1,-2}){
            PcHost h=new PcHost(data,TextCodec.identity());int label=profile.id.equals("dcpc-2063")?0x32:0x35;
            byte[] code=script(profile.version,(byte)label,(byte)'x',(byte)0,(byte)label,(byte)'r',(byte)0,(byte)0,(byte)0,(byte)0);
            MesVm vm=new MesVm(n->new MesScript(n,code,profile),h,"input");
            h.command(timed?"pc.wait":"pc.ui.wait",new int[]{0,0},new byte[0]);h.ui.actions[0]=button==-1?"r":"missing";h.ui.actions[1]=button==-2?"r":"missing";h.ui.actions[3]="wrong";
            check(vm.run(10)==MesVm.Status.WAIT_HOST&&!vm.activateMenu(h.waitTicket()-1,button),"PC input rejects stale ticket");
            check(vm.activateMenu(h.waitTicket(),button)&&vm.offset()==9&&h.ui.actions[button==-1?0:1].isEmpty()&&h.ui.actions[button==-1?1:0].equals("missing"),"PC original input resolves correct label and consumes only its binding");
        }
        PcHost h=new PcHost(data,TextCodec.identity());h.ui.waiting=h.confirming=true;h.ui.actions[0]="right";h.ui.actions[3]="cancel";
        check(new String(h.menuAction(h.waitTicket(),-1),Binary.SJIS).equals("cancel"),"PC confirmation keeps independent cancel binding");h.acceptMenuAction(-1);check(h.ui.actions[0].equals("right"),"Confirmation does not consume normal right binding");
    }
    private static byte[] alignedCrx(int tail,int value)throws Exception{
        byte[] header=new byte[20];header[0]='C';header[1]='R';header[2]='X';header[3]='G';header[8]=3;header[10]=4;header[12]=2;
        ByteArrayOutputStream out=new ByteArrayOutputStream();out.write(header);
        try(java.util.zip.DeflaterOutputStream z=new java.util.zip.DeflaterOutputStream(out)){z.write(new byte[40]);for(int i=0;i<tail;i++)z.write(value);}
        return out.toByteArray();
    }
}
