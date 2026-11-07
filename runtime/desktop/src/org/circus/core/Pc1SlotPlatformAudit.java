package org.circus.core;

import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.*;
import java.util.*;

/** Read-only DCPC admission plus portable producer/slot metadata integration.
 * Controlled synthetic MES/audio names use the actual game's graphics and PE.
 * The original executable is never launched; saves stay in the owned output. */
public final class Pc1SlotPlatformAudit {
    private static int checks;
    private static void check(boolean yes,String why){checks++;if(!yes)throw new AssertionError(why);}
    private static byte[] thumbnail(DesktopLegacy e)throws IOException{BufferedImage image=e.preview();try{return DesktopSave.thumbnail(image,256,192);}finally{image.flush();}}
    private static PcHost host(DesktopLegacy e){return(PcHost)e.host();}
    private static Path slot(DesktopLegacy e,int n){return e.saveDirectory.resolve("slot-"+n+".sav");}
    private static DesktopLegacy engine(CircusResources data,TextCodec codec)throws Exception{return new DesktopLegacy(data,codec,true);}
    private static DesktopSave saved(DesktopLegacy e,int n)throws IOException{return DesktopSave.decode(Files.readAllBytes(slot(e,n)));}
    private static byte[] fixtureScript()throws IOException{
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();bytes.write(new byte[]{0,0,0,0,0x63,0x20});
        for(int n=0;n<4;n++)bytes.write(new byte[]{0x1b,1,0,0x3b,(byte)('A'+n),0,0x15,4,1});
        bytes.write(new byte[]{0,0,0});return bytes.toByteArray();
    }
    private static CircusResources fixture(final LocalGameStorage original)throws IOException{
        final byte[] script=fixtureScript();GameResources.Storage overlay=new GameResources.Storage(){
            private boolean start(String path){return path.replace('\\','/').equalsIgnoreCase("AdvData/MES/START.MES");}
            public Set<String> paths(){return original.paths();}
            public byte[] read(String path,int limit)throws IOException{if(!start(path))return original.read(path,limit);if(script.length>limit)throw new IOException("Fixture read limit");return script.clone();}
            public Archive.Source source(String path)throws IOException{if(!start(path))return original.source(path);return new Archive.Source(){public long size(){return script.length;}public byte[] read(long at,int n)throws IOException{Binary.range(at,n,script.length);return Arrays.copyOfRange(script,(int)at,(int)at+n);}};}
        };
        return new CircusResources(new GameResources(overlay,"DCPCDVD_CHS.exe"),Profiles.get("dcpc-2063"));
    }
    private static void prime(DesktopLegacy e,String tag,String background,int ordinary,int mismatch)throws Exception{
        PcHost h=host(e);h.body="producer "+tag;h.checkpointLabel="producer "+tag;h.scene.slots[1]=background;h.scene.shown.slots[1]=background;e.vm().variables.write(0,0,ordinary);e.vm().variables.write(9,2,2);e.vm().variables.write(9,20,3);e.playbackMillis(321);
        for(int ch=0;ch<6;ch++){String resource="fixture/"+tag+"/channel"+ch;h.play(ch,resource,ch==0);e.audioPosition(ch,1000L*(ch+1),100000);}
        if(mismatch>=0)h.play(mismatch,h.audio[mismatch],false);
    }
    private static void mutate(DesktopLegacy e,String tag,String background,int ordinary)throws Exception{
        PcHost h=host(e);h.body=h.checkpointLabel="live "+tag;h.scene.slots[1]=background;h.scene.shown.slots[1]=background;e.vm().variables.write(0,0,ordinary);e.vm().variables.write(9,2,7);e.vm().variables.write(9,20,9);e.playbackMillis(999);
        for(int ch=0;ch<6;ch++){h.play(ch,"fixture/live-"+tag+"/channel"+ch,false);e.audioPosition(ch,99000+ch,100000);}
    }
    /** Windows java.io sharing retains the old target while atomic replacement is attempted. */
    private static InputStream preventDelete(Path path)throws IOException{return new FileInputStream(path.toFile());}
    private static Map<String,Object> fixtureChecks(LocalGameStorage original,TextCodec codec,Path out)throws Exception{
        System.setProperty("dc.saveRoot",out.resolve("fixture-saves").toString());CircusResources data=fixture(original);DesktopLegacy e=engine(data,codec);
        prime(e,"A","bg\\hai_15",31,3);byte[] previewA=thumbnail(e);
        check(e.vm().run(1)==MesVm.Status.RUNNING,"first1B captures at its completed instruction boundary");
        mutate(e,"B","bg\\i_etc",91);byte[] liveB=thumbnail(e);check(!Arrays.equals(previewA,liveB),"fixture backgrounds visibly differ before selected-candidate write");
        e.tick(0);DesktopSave a=saved(e,0);
        check(a.caption.equals("producer A")&&Arrays.equals(a.preview,previewA),"slot metadata uses independent producer caption and thumbnail");
        check(!Arrays.equals(a.preview,liveB),"later current scene cannot replace selected thumbnail");check(a.icon==9,"save icon comes from native pre-file-open current9:20 patch");
        for(int ch=0;ch<6;ch++){check(a.audio[ch].equals("fixture/A/channel"+ch),"producer audio resource "+ch);check(a.position[ch]==(ch==3?0:1000L*(ch+1)),"producer cursor or generation-mismatch zero "+ch);}
        check(a.textElapsed==321,"text cursor belongs to producer rather than save menu");check(host(e).saveRequest==-1&&host(e).pc1.requestedSave()==null,"successful auto slot clears only after atomic write");
        DesktopSave cached=e.metadata(slot(e,0));check(cached.caption.equals("producer A"),"first slot metadata is cached");
        DesktopLegacy restored=engine(data,codec);restored.restore(slot(e,0));
        check(restored.vm().variables.read(0,0)==31&&!restored.session.inSystem()&&restored.vm().offset()==12,"fresh slot load restores producer continuation, not later persist position");
        check(restored.vm().variables.read(9,2)==7&&restored.vm().variables.read(9,20)==9,"native metadata patch is limited to requested current fields");
        check(Arrays.equals(thumbnail(restored),previewA)&&restored.playbackMillis()==321,"fresh load restores producer visible scene and text cursor");
        for(int ch=0;ch<6;ch++)check(restored.takeAudioResume(ch)==(ch==3?0:1000L*(ch+1)),"fresh slot load submits producer cursor "+ch);
        prime(e,"B","bg\\i_etc",41,2);byte[] previewB=thumbnail(e);check(e.vm().run(1)==MesVm.Status.RUNNING,"second real1B produces a new independent leaf");
        byte[] retained=e.snapshot();mutate(e,"C","bg\\hai_15",99);e.restore(retained);mutate(e,"C","bg\\hai_15",99);e.tick(0);DesktopSave b=saved(e,0);
        check(b.caption.equals("producer B")&&Arrays.equals(b.preview,previewB),"session restore retains earlier candidate platform sidecar");
        check(e.metadata(slot(e,0)).caption.equals("producer B")&&Arrays.equals(e.metadata(slot(e,0)).preview,previewB),"overwriting one slot refreshes cached metadata");
        BufferedImage cachedThumb=e.thumbnails.get(0),decoded=b.image();try{check(cachedThumb!=null&&Arrays.equals(cachedThumb.getRGB(0,0,256,192,null,0,256),decoded.getRGB(0,0,256,192,null,0,256)),"overwriting one slot refreshes thumbnail cache pixels");}finally{decoded.flush();}
        for(int ch=0;ch<6;ch++)check(b.position[ch]==(ch==2?0:1000L*(ch+1)),"restored sidecar retains each producer cursor "+ch);
        prime(e,"C","bg\\hai_15",51,-1);check(e.vm().run(1)==MesVm.Status.RUNNING,"third producer captured for atomic failure test");byte[] before=Files.readAllBytes(slot(e,0));boolean failed=false;
        try(InputStream lock=preventDelete(slot(e,0))){try{e.tick(0);}catch(IOException expected){failed=true;}}
        check(failed&&Arrays.equals(before,Files.readAllBytes(slot(e,0))),"failed atomic replacement preserves the prior complete slot");
        check(host(e).saveRequest==0&&host(e).pc1.requestedSlot()==0&&host(e).pc1.requestedSave()!=null&&e.vm().status()==MesVm.Status.WAIT_HOST,"failed write preserves live pending native request");
        check(e.metadata(slot(e,0)).caption.equals("producer B"),"failed write cannot publish new metadata cache");
        e.tick(0);check(host(e).saveRequest==-1&&saved(e,0).caption.equals("producer C"),"retry commits original pending producer after sharing lock is released");
        check(e.vm().offset()==33&&e.vm().status()==MesVm.Status.RUNNING,"successful retry resumes after persist rather than replaying its request");
        // Resource and native generation survive CVS2, while the physical
        // playback sample belongs to the old execution instance.
        prime(e,"D","bg\\i_etc",61,-1);Object oldExecution=e.executionIdentity();String[] resources=host(e).audio.clone();int[] generations=host(e).audioGeneration.clone();byte[] beforeRestore=e.snapshot();e.restore(beforeRestore);
        check(e.executionIdentity()!=oldExecution&&Arrays.equals(resources,host(e).audio)&&Arrays.equals(generations,host(e).audioGeneration),"restore replaces the VM while keeping identical audio resource/generation values");
        check(e.vm().run(1)==MesVm.Status.RUNNING,"real fourth1B captures before a new platform audioPosition report");e.tick(0);DesktopSave d=saved(e,0);
        for(int ch=0;ch<6;ch++)check(d.audio[ch].equals(resources[ch])&&d.position[ch]==0,"new execution cannot reuse old physical cursor even with same resource/generation "+ch);
        check(e.vm().run(1)==MesVm.Status.HALTED,"cross-VM producer slot resumes its following STOP");
        return Json.obj("checks",checks,"state_scope","self-authored four1B/persist4 programs over actual DCPC graphics/PE","first_slot_caption",a.caption,"second_slot_caption",b.caption,"producer_thumbnail_sha256",Binary.sha256(previewA),"later_scene_thumbnail_sha256",Binary.sha256(liveB),"first_audio_cursors",a.position,"second_audio_cursors",b.position,"same_resource_generation_after_restore_cursors",d.position,"execution_instance_replaced",true,"failed_replace_preserved_old_slot",true,"failed_replace_preserved_request",true);
    }
    private static Map<String,Object> originalAuto(CircusResources data,TextCodec codec,Path out)throws Exception{
        System.setProperty("dc.saveRoot",out.resolve("original-saves").toString());DesktopLegacy e=engine(data,codec);final byte[][] metadata={null};final String[] producerScript={""};final int[] producerPc={-1};Dc2Session.Pc1CandidateCapture platform=e.session.pc1CandidateCapture;
        e.session.pc1CandidateCapture=()->{byte[] value=platform.capture();metadata[0]=value.clone();producerScript[0]=e.vm().scriptName();producerPc[0]=e.vm().offset();return value;};
        int dialogues=0;boolean started=false;String previous="";Set<String> scripts=new LinkedHashSet<>();
        for(int step=0;step<100000&&!Files.exists(slot(e,0));step++){
            e.tick(600000);scripts.add(e.vm().scriptName());String where=e.vm().scriptName()+"@"+e.vm().offset();
            if(e.menuWaiting()){
                List<String> actions=new ArrayList<>();for(DesktopEngine.Control control:e.controls())actions.add(control.action);
                if(host(e).pc1.waitKind()==9&&!host(e).pc1.historyActive())actions.removeIf(a->a.startsWith(":"));
                String selected=null;if(!started){for(String action:new String[]{"window","03","sce_start","start"})if(actions.contains(action)){selected=action;break;}if("start".equals(selected)||"sce_start".equals(selected))started=true;}
                if(selected==null&&!actions.isEmpty())selected=actions.get(actions.size()-1);if(selected==null)throw new IOException("Unresolved original menu "+where+actions);e.choose(selected);continue;
            }
            if(e.host().textWaiting&&!where.equals(previous)){previous=where;dialogues++;}
            for(int ch=0;ch<6;ch++)e.audioCompleted(ch,e.audioGeneration(ch));e.vm().signalHost(e.ticket(),MesVm.HostSignal.MEDIA_COMPLETE);e.vm().signalHost(e.ticket(),MesVm.HostSignal.TEXT_COMPLETE);
            if(e.vm().status()==MesVm.Status.WAIT_HOST&&(dialogues==0||e.vm().scriptName().equalsIgnoreCase("MAIN")||e.textWaiting()||e.host().remainingMillis()<0))e.click(e.width/2,e.height/2,false);
            if(e.vm().status()==MesVm.Status.FAULT)throw new IOException(e.vm().error());if(e.vm().status()==MesVm.Status.HALTED)throw new IOException("Unexpected halt before original autosave");
        }
        check(Files.isRegularFile(slot(e,0)),"actual original forced auto slot is written");check(e.vm().scriptName().equalsIgnoreCase("0224a_f00")&&e.vm().offset()>=0x826,"actual route passes its prior0x826 guard");check(e.host().saveRequest<0,"actual route does not leave an endless pending autosave");
        DesktopSave file=saved(e,0),producer=DesktopSave.decode(metadata[0]);check(Arrays.equals(file.preview,producer.preview)&&file.caption.equals(producer.caption)&&Arrays.equals(file.audio,producer.audio)&&Arrays.equals(file.position,producer.position),"actual forced slot metadata remains paired with its earlier1B producer");
        DesktopLegacy recovered=engine(data,codec);recovered.restore(slot(e,0));check(recovered.vm().scriptName().equalsIgnoreCase(producerScript[0])&&recovered.vm().offset()==producerPc[0]&&!recovered.session.inSystem(),"actual forced slot freshly restores the independent prepared continuation");
        check(Arrays.equals(thumbnail(recovered),file.preview),"actual forced slot thumbnail matches restored visible base scene");
        return Json.obj("dialogues_before_auto",dialogues,"script_count",scripts.size(),"written_script",e.vm().scriptName(),"written_pc",e.vm().offset(),"producer_script",producerScript[0],"producer_pc",producerPc[0],"restored_script",recovered.vm().scriptName(),"restored_pc",recovered.vm().offset(),"slot_bytes",Files.size(slot(e,0)),"returned_to_title",false,"media_completion_simulated",true,"choice_policy","last real story option; native toolbar actions excluded");
    }
    public static void main(String[] args)throws Exception{
        if(args.length!=2)throw new IOException("Require readonly_DCPC_GAME_ROOT owned_OUTPUT");Path game=Paths.get(args[0]).toRealPath(),output=DesktopRouteAudit.outputOutsideGame(game,args[1]),out=output.resolve("run-"+UUID.randomUUID());Files.createDirectories(out);
        LocalGameStorage storage=new LocalGameStorage(game);GameResources files=new GameResources(storage,"DCPCDVD_CHS.exe");CircusResources data=new CircusResources(files,Profiles.get("dcpc-2063"));TextCodec codec=GameText.detect(files,data.profile);
        checks=0;Map<String,Object> fixture=fixtureChecks(storage,codec,out),original=originalAuto(data,codec,out);
        Files.write(output.resolve("result.json"),(Json.text(Json.obj("pass",true,"checks",checks,"target_sha256",Binary.sha256(files.read("DCPCDVD_CHS.exe",64*1024*1024)),"controlled_platform",fixture,"actual_original_auto",original,"owned_run_directory",out.toString(),"original_exe_executed",false,"original_game_directory_written",false,"scope","portable candidate slots and platform sidecars; not Windows8004 interchange or complete native41A620 reconstruction"))+"\n").getBytes("UTF-8"));
        System.out.println("Pc1SlotPlatformAudit PASS "+checks+"; actual auto slot and producer metadata, cursor, overwrite and atomic failure contracts");
    }
}
