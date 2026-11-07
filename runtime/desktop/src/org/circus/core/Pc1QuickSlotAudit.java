package org.circus.core;

import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.*;
import java.util.*;

/** Actual DCPC quick input and portable slot1 integration. Original EXE is
 * read as static data only; controlled input/media/time never write the game. */
public final class Pc1QuickSlotAudit {
    private static int checks;
    private static void check(boolean yes,String why){checks++;if(!yes)throw new AssertionError(why);}
    private static PcHost host(DesktopLegacy e){return(PcHost)e.host();}
    private static byte[] thumbnail(DesktopLegacy e)throws IOException{BufferedImage b=e.preview();try{return DesktopSave.thumbnail(b,256,192);}finally{b.flush();}}
    private static DesktopLegacy engine(CircusResources data,TextCodec codec)throws Exception{return new DesktopLegacy(data,codec,true);}
    private static void waitForStoryWithCandidate(DesktopLegacy e)throws Exception{
        boolean started=false;Set<String> seen=new HashSet<>();
        for(int step=0;step<20000;step++){
            e.tick(600000);PcHost h=host(e);if(seen.add(e.vm().scriptName()))e.render().flush();
            if(h.pc1.waitKind()==7&&h.textWaiting&&!h.presentationWaiting&&!e.session.inSystem()&&h.pc1.saves().candidate()!=null)return;
            if(e.menuWaiting()){
                List<String> actions=new ArrayList<>();for(DesktopEngine.Control b:e.controls())actions.add(b.action);
                if(h.pc1.waitKind()==9&&!h.pc1.historyActive()){Set<String> choices=new HashSet<>();for(int n=h.pc1.storyPrefix().size();n<h.ui.buttons.size();n++)if(!h.ui.buttons.get(n).action.isEmpty())choices.add(h.ui.buttons.get(n).action);actions.retainAll(choices);}
                String selected=null;if(!started){for(String a:new String[]{"window","03","start"})if(actions.contains(a)){selected=a;break;}if("start".equals(selected))started=true;}
                if(selected==null&&!actions.isEmpty())selected=actions.get(0);if(selected==null)throw new IOException("Quick audit unresolved original menu "+e.vm().scriptName()+actions);e.choose(selected);continue;
            }
            for(int ch=0;ch<6;ch++)e.audioCompleted(ch,e.audioGeneration(ch));e.vm().signalHost(e.ticket(),MesVm.HostSignal.MEDIA_COMPLETE);e.vm().signalHost(e.ticket(),MesVm.HostSignal.TEXT_COMPLETE);
            if(e.vm().status()==MesVm.Status.WAIT_HOST&&(!started||e.vm().scriptName().equalsIgnoreCase("MAIN")||e.textWaiting()||h.remainingMillis()<0))e.click(e.width/2,e.height/2,false);
            if(e.vm().status()==MesVm.Status.FAULT)throw new IOException(e.vm().error());
        }
        throw new IOException("No original prepared1B/story07 boundary reached");
    }
    public static void main(String[]args)throws Exception{
        if(args.length!=2)throw new IOException("Require readonly_DCPC_GAME owned_OUTPUT");
        Path game=Paths.get(args[0]).toRealPath(),out=DesktopRouteAudit.outputOutsideGame(game,args[1]);System.setProperty("dc.saveRoot",out.resolve("saves").toString());System.setProperty("dc.cacheRoot",out.resolve("cache").toString());
        GameResources files=new GameResources(new LocalGameStorage(game),"DCPCDVD_CHS.exe");CircusResources data=new CircusResources(files,Profiles.get("dcpc-2063"));TextCodec codec=GameText.detect(files,data.profile);DesktopLegacy e=engine(data,codec);waitForStoryWithCandidate(e);
        PcHost h=host(e);String script=e.vm().scriptName(),body=h.body;String oldScript=h.pc1.saves().candidate().returnPoint().script;int pc=e.vm().offset(),flag=h.pc1.saves().flag(),oldPc=h.pc1.saves().candidate().returnPoint().pc;long ticket=h.waitTicket();
        check(h.pc1.saves().candidate().preparedBy1B()&&(!oldScript.equals(script)||oldPc!=pc),"actual input runs after an older independent1B candidate");
        byte[] preview=thumbnail(e);String[] audio=h.audio.clone();int[] generations=h.audioGeneration.clone();long[] cursors=new long[6];
        for(int ch=0;ch<6;ch++)if(h.audioActive[ch]&&!audio[ch].isEmpty()){cursors[ch]=(ch+1)*1000;e.audioPosition(ch,cursors[ch],60000);}
        e.action(":qs");Path slot=e.saveDirectory.resolve("slot-1.sav");check(Files.exists(slot)&&h.saveRequest<0&&h.pc1.requestedSave()==null,"portable:qs creates slot1 and acknowledges only its successful write");
        DesktopSave saved=DesktopSave.decode(Files.readAllBytes(slot));check(Arrays.equals(preview,saved.preview),"slot preview comes from the current quick producer");
        check(h.waitTicket()==ticket&&h.pc1.waitKind()==7&&h.textWaiting&&e.vm().offset()==pc,"quick save does not advance the original caller wait");
        check(Arrays.equals(generations,h.audioGeneration)&&Arrays.equals(audio,h.audio),"quick input/file write leaves all actual audio requests untouched");
        for(int ch=0;ch<6;ch++){check(saved.audio[ch].equals(audio[ch]),"slot audio producer channel"+ch);check(saved.position[ch]==cursors[ch],"slot physical cursor channel"+ch);}
        check(h.pc1.saves().flag()==flag&&h.pc1.saves().candidate().capturedFromEvent()&&h.pc1.saves().candidate().returnPoint().pc==pc,"quick replaces the earlier1B candidate without changingBB8418");
        DesktopLegacy fresh=engine(data,codec);fresh.restore(slot);check(fresh.vm().scriptName().equals(script)&&fresh.vm().offset()==pc&&fresh.host().body.equals(body),"fresh load returns to the actual quick caller, not1B or a menu");
        check(fresh.host().textWaiting&&!fresh.session.inSystem()&&fresh.host().saveRequest<0,"fresh load has the original wait and no pending file replay");
        check(Arrays.equals(thumbnail(fresh),preview),"actual visible background survives quick save/fresh load");
        for(int ch=0;ch<6;ch++)check(fresh.takeAudioResume(ch)==cursors[ch],"fresh slot load restores matching audio cursor"+ch);
        byte[] session=e.snapshot();e.restore(session);check(e.vm().offset()==pc&&e.host().body.equals(body),"F5/session preserves the current quick caller and candidate sidecar");
        e.action(":option");check(e.session.inSystem(),"actual original OPTION enters a typed system caller");byte[] oldSlot=Files.readAllBytes(slot);boolean refused=false;try{e.action(":qs");}catch(IOException expected){refused=expected.getMessage().contains("stable native story");}check(refused&&Arrays.equals(oldSlot,Files.readAllBytes(slot)),"quick input in OPTION cannot capture or overwrite a system menu");
        e.action(":ql");check(!e.session.inSystem()&&e.vm().scriptName().equals(script)&&e.vm().offset()==pc&&e.host().body.equals(body),"portable:qload may leave OPTION by loading the independent quick slot");
        try(FileInputStream lock=new FileInputStream(slot.toFile())){
            boolean blocked=false;try{e.action(":qs");}catch(IOException expected){blocked=true;}
            check(blocked&&host(e).saveRequest==1&&host(e).pc1.requestedSlot()==1,"Windows locked replacement retains the real quick pending request");
            check(Arrays.equals(oldSlot,Files.readAllBytes(slot)),"failed quick replacement preserves old slot bytes");
        }
        e.tick(0);check(host(e).saveRequest<0&&host(e).pc1.requestedSave()==null&&e.host().textWaiting,"retry after unlock finishes the same request without advancing dialogue");
        check(DesktopSave.decode(Files.readAllBytes(slot)).state.length>0,"successful retry leaves a readable independent slot");
        Files.write(out.resolve("result.json"),(Json.text(Json.obj("pass",true,"checks",checks,"target_sha256",Binary.sha256(files.read("DCPCDVD_CHS.exe",64*1024*1024)),"script",script,"current_quick_pc",pc,"replaced_prepared_script",oldScript,"replaced_prepared_pc",oldPc,"candidate_flag_unchanged",flag,"slot_bytes",Files.size(slot),"original_audio_names",audio,"sampled_cursors",cursors,"real_option_rejected",true,"file_lock_failure_and_retry",true,"original_exe_executed",false,"original_game_directory_written",false,"scope","Original:q force-copy policy plus portable:qs/:ql aliases and independent P1CS slot1; Windows share-delete failure injection; no original8004 slot interchange or native quick-load input claim"))+"\n").getBytes("UTF-8"));
        System.out.println("Pc1QuickSlotAudit PASS "+checks+"; native:q current caller and portable slot1/OPTION/failure retry");
    }
}
