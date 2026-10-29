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
        listCaptions(files,two);
        sceneListMode(files,one,two);
        return count;
    }
    private static void sceneListMode(GameResources files,Profile one,Profile two)throws Exception{
        byte[] names=new byte[20+188];names[16]=1;byte[] name="ind_0401_f1_otm".getBytes(Binary.SJIS);System.arraycopy(name,0,names,20,name.length);
        Map<String,byte[]> entries=new HashMap<>();entries.put("AdvData/DAT/NAMES.DAT",names);
        PcHost h=new PcHost(new CircusResources(files(entries),two),TextCodec.identity());h.attach(new Variables(h,Variables.Model.PC_WIDE));
        h.command("pc.grid.item",new int[]{0,0,0,0,68,52,6,0},new byte[0]);h.command("grid.item_action",new int[0],"sce0".getBytes(Binary.SJIS));
        h.command("pc.command",new int[]{7,255},new byte[0]);check(h.list.textStyle==4&&h.list.last().action.equals("sce0"),"Scene renderer selector ignores its second parameter and preserves jump actions");
        check(h.sceneListName(h.list.last()).equals("ind_0401_f1_otm")&&PcHost.sceneListSpecial(h.sceneListName(h.list.last())),"Scene decoration indexes the original scenario catalog by entry id");
        ByteArrayOutputStream out=new ByteArrayOutputStream();h.list.save(new DataOutputStream(out));ScriptList copy=new ScriptList();copy.load(new DataInputStream(new ByteArrayInputStream(out.toByteArray())));check(copy.textStyle==4&&copy.last().action.equals("sce0"),"Scene renderer selector survives existing list snapshots");
        h.list.last().action="";check(h.sceneListName(h.list.last()).isEmpty(),"Locked scenes do not expose native dates or decoration");
        check(PcHost.sceneListDate("ind_0120_f3_yme").equals(" 1月20日")&&PcHost.sceneListDate("ind_0401_f1_otm").equals(" alfine")&&PcHost.sceneListDate("0ind_1216_a1_skr").equals(" dacapo"),"Scene dates preserve native calendar and period labels");
        check(PcHost.sceneListDate("ind_0216_b1_anz").equals(" epilogue")&&PcHost.sceneListDate("ind_0305_b1_akn").equals(" epilogue")&&PcHost.sceneListDate("ind_0125_a1_rno").equals(" epilogue")&&PcHost.sceneListDate("").isEmpty(),"Scene date exceptions and locked labels match native rules");
        h.list.clear();h.command("pc.command",new int[]{6,0},new byte[0]);check(h.list.textStyle==0&&h.saveGrid,"Ordinary save-grid setup remains separate from Scene list styling");
        PcHost first=new PcHost(new CircusResources(files,one),TextCodec.identity());reject(()->first.command("pc.command",new int[]{7,0},new byte[0]),"PC1 command 7 remains unimplemented rather than inheriting PC2 renderer semantics");
    }
    private static void listCaptions(GameResources files,Profile profile)throws Exception{
        check(profile.operation(0x58).equals("pc.grid.caption")&&Profiles.get("dcpc-2063").operation(0x58).equals("variable.update"),"PC2 caption opcode does not replace PC1 variable update");
        PcHost h=new PcHost(new CircusResources(files,profile),TextCodec.identity());
        byte[] code=scriptInts(0x5769,0x57,0,0,0,0,0,0,0,0,0x52,68,0,52,0,6,0,0,0,0x42,'g','o',0,
            (byte)0x58,70,0,34,0,1,0,2,0,(byte)0x45,'T','i','t','l','e',0,(byte)0x5c,2,0,3,0,17,0,0,0,0,0,0);
        MesVm vm=new MesVm(n->new MesScript(n,code,profile),h,"caption");check(vm.run(100)==MesVm.Status.HALTED&&vm.variables.read(2,3)==17,"Caption consumes raw following string and resumes at next opcode");
        ScriptList.Item item=h.list.last();check(item.action.equals("go")&&item.sprites.size()==3,"Caption preserves entry action and thumbnail");
        Dc2Ui.Quad normal=item.sprites.get(1),hover=item.sprites.get(2);
        check(normal.type==2&&normal.x==70&&normal.y==34&&normal.text.equals("Title")&&normal.flags==1&&normal.state==99,"Normal caption keeps relative coordinates and raw caption bytes");
        check(hover.text.equals("Title")&&hover.flags==2&&hover.state==1,"Hover caption selects independent native palette");
        check(ScriptList.visible(item.sprites,99).size()==2&&ScriptList.visible(item.sprites,1).size()==2,"Both normal and hover retain the common thumbnail exactly once");
        h.command("pc.grid.caption",new int[]{-1,-1,3,4},"other".getBytes(Binary.SJIS));check(item.sprites.size()==3&&item.sprites.get(1).x==-1&&item.sprites.get(1).y==-1&&item.sprites.get(1).text.equals("other"),"Repeated caption replaces fields and preserves native center/hide sentinels");
        ByteArrayOutputStream out=new ByteArrayOutputStream();h.list.save(new DataOutputStream(out));ScriptList copy=new ScriptList();copy.load(new DataInputStream(new ByteArrayInputStream(out.toByteArray())));
        check(copy.last().sprites.get(2).type==2&&copy.last().sprites.get(2).text.equals("other")&&copy.last().sprites.get(2).flags==4,"List snapshot roundtrip preserves caption and hover palette");
        PcHost empty=new PcHost(new CircusResources(files,profile),TextCodec.identity());byte[] noEntry=scriptInts(0x5769,0x58,0,0,0,0,1,0,1,0,0x45,'x',0,0,0,0);
        MesVm noEntryVm=new MesVm(n->new MesScript(n,noEntry,profile),empty,"empty");check(noEntryVm.run(10)==MesVm.Status.HALTED&&empty.list.items.isEmpty(),"Native caption without an entry still consumes its string");
        byte[] invalid=scriptInts(0x5769,0x58,0,0,0,0,1,0,1,0,0,0,0);MesVm invalidVm=new MesVm(n->new MesScript(n,invalid,profile),empty,"invalid");check(invalidVm.run(10)==MesVm.Status.FAULT,"Caption missing string payload is an explicit error");
    }
    private static byte[] scriptInts(int marker,int...values)throws IOException{byte[] bytes=new byte[values.length];for(int n=0;n<values.length;n++)bytes[n]=(byte)values[n];return script(marker,bytes);}
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
