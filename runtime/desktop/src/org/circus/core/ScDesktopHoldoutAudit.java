package org.circus.core;

import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import javax.imageio.ImageIO;

/** Physical SC holdout audit. Original game files are read-only; no original EXE runs. */
public final class ScDesktopHoldoutAudit {
    private static int checks,lines;
    private static final List<Object> menus=new ArrayList<>();
    private static void check(boolean yes,String message)throws IOException{checks++;if(!yes)throw new IOException(message);}
    private static String at(DesktopEngine e){return e.vm().scriptName()+"@"+Integer.toHexString(e.vm().offset());}
    private static List<String> actions(DesktopEngine e){return DesktopSettingsAudit.actions(e);}
    private static void settle(DesktopEngine e)throws Exception {
        for(int n=0;n<10000;n++){
            e.tick(1000);Dc2Host h=((DesktopLegacy)e).host();
            if(e.vm().status()==MesVm.Status.FAULT)throw new IOException(e.vm().error());
            if(e.menuWaiting()){menus.add(Json.obj("at",at(e),"actions",actions(e)));return;}
            if(h.presentationWaiting){e.vm().signalHost(e.ticket(),MesVm.HostSignal.TEXT_COMPLETE);continue;}
            if(h.textWaiting&&!h.body.isEmpty())return;
            for(int channel=0;channel<6;channel++)if(e.audioActive(channel)&&!e.audioLoop(channel))e.audioCompleted(channel,e.audioGeneration(channel));
            if(!e.movie().isEmpty())e.vm().signalHost(e.ticket(),MesVm.HostSignal.MEDIA_COMPLETE);
            if(h.remainingMillis()<0)e.vm().signalHost(e.ticket(),MesVm.HostSignal.ADVANCE);
        }
        throw new IOException("SC audit failed to settle at "+at(e)+" "+actions(e));
    }
    private static void choose(DesktopEngine e,String action)throws Exception{check(actions(e).contains(action),"Missing original SC action "+action+" at "+at(e)+" "+actions(e));e.choose(action);settle(e);}
    private static void shot(DesktopEngine e,Path out,String name)throws Exception{ImageIO.write(e.render(),"png",out.resolve(name+".png").toFile());}
    public static void main(String[] args)throws Exception {
        Path game=Paths.get(args[0]).toRealPath(),out=DesktopRouteAudit.outputOutsideGame(game,args[1]);System.setProperty("dc.saveRoot",out.resolve("saves").toString());
        Map<String,Object> report=Json.obj("original_exe_run",false,"media_completion_simulated",true,"original_game_files_read_only",true);String where="initialization";
        try(DesktopRuntime app=new DesktopRuntime(game,true,"dc2sc_CHS.exe")){
            DesktopEngine e=app.engine;
            try{
                check(((DesktopLegacy)e).host() instanceof ScHost,"Factory selects real ScHost");
                report.put("profile",e.resources.profileId);report.put("encoding",e.codec.charset.name());
                settle(e);shot(e,out,"00-window");choose(e,"03");shot(e,out,"01-title");
                choose(e,"start");shot(e,out,"02-category");choose(e,"after_story");shot(e,out,"03-route");choose(e,"after_otm");choose(e,"read_after");
                String prior="";while(lines<20){
                    settle(e);where=at(e);Dc2Host h=((DesktopLegacy)e).host();
                    if(e.menuWaiting())throw new IOException("Unexpected pre20 menu "+where+" "+actions(e));
                    String key=where+":"+e.ticket();if(!key.equals(prior)){prior=key;lines++;if(lines==1)shot(e,out,"04-first-text");}
                    if(lines<20)e.vm().signalHost(e.ticket(),MesVm.HostSignal.ADVANCE);
                }
                check(!e.body().isEmpty(),"20 original dialogue waits reached");shot(e,out,"05-text20");
                String script=e.vm().scriptName(),body=e.body();int pc=e.vm().offset();byte[] raw=e.snapshot();
                BufferedImage before=e.render();Path save=out.resolve("story.sav");e.save(save);DesktopSave metadata=DesktopSave.decode(Files.readAllBytes(save));
                check(metadata.image()!=null&&metadata.image().getWidth()==256,"Owned SC save thumbnail decodes");
                e.restore(save);BufferedImage after=e.render();
                check(script.equals(e.vm().scriptName())&&pc==e.vm().offset()&&body.equals(e.body()),"SC file save restores exact sentence and PC");
                check(Arrays.equals(before.getRGB(0,0,e.width,e.height,null,0,e.width),after.getRGB(0,0,e.width,e.height,null,0,e.width)),"SC file save restores exact rendered pixels");
                check(Arrays.equals(raw,e.snapshot()),"SC file save restores exact VM and native host state");shot(e,out,"06-restored");
                report.put("story_location",script+"@"+Integer.toHexString(pc));report.put("story_body_sha256",Binary.sha256(body.getBytes(StandardCharsets.UTF_8)));
                // Probe original save/load UI using its actual script and hit rows.
                ((DesktopLegacy)e).action(":menu");settle(e);where=at(e);shot(e,out,"07-advmenu");
                String saveAction=actions(e).contains("save")?"save":actions(e).contains("s")?"s":"";
                check(!saveAction.isEmpty(),"Original SC menu exposes Save: "+actions(e));choose(e,saveAction);where=at(e);shot(e,out,"08-native-save");
                ScHost host=(ScHost)((DesktopLegacy)e).host();check(host.gridWaiting&&host.saveGrid,"Original SAVE invokes SC service6 Save");
                check(host.gridSlot(0)==0&&host.gridSlot(1)==1&&host.gridSlot(2)==2,"Native Save binds actual reserved and normal slot IDs");
                check(host.menuActionForGrid(0)==null&&host.menuActionForGrid(1)==null,"Native Save rejects reserved0/1 without advancing");
                report.put("native_save_ui_checked",true);report.put("native_save_controls",actions(e));
                choose(e,"@slot:2");shot(e,out,"09-save-selected");
                Path nativeSlot=e.saveDirectory.resolve("slot-2.sav");
                for(int attempt=0;attempt<5&&!Files.isRegularFile(nativeSlot);attempt++){
                    if(actions(e).contains("iconset00"))choose(e,"iconset00");
                    else if(actions(e).contains("dosave"))choose(e,"dosave");
                    else throw new IOException("Unresolved SC original save confirmation at "+at(e)+" "+actions(e));
                }
                check(Files.isRegularFile(nativeSlot),"Original native SAVE writes selected runtime-owned slot2");
                DesktopSave.decode(Files.readAllBytes(nativeSlot));
                for(int back=0;back<3&&e.menuWaiting()&&actions(e).contains("return");back++)choose(e,"return");
                check(script.equals(e.vm().scriptName())&&pc==e.vm().offset()&&body.equals(e.body()),"Native Save return restores caller sentence and PC");shot(e,out,"10-save-return");
                e.vm().signalHost(e.ticket(),MesVm.HostSignal.ADVANCE);settle(e);check(pc!=e.vm().offset(),"Story advances after native Save return");
                ((DesktopLegacy)e).action(":menu");settle(e);choose(e,"load");shot(e,out,"11-native-load");
                host=(ScHost)((DesktopLegacy)e).host();check(host.gridWaiting&&!host.saveGrid&&host.gridSlot(2)==2,"Original LOAD invokes service6 Load with selected2");
                choose(e,"@slot:2");
                for(int attempt=0;attempt<5&&!script.equals(e.vm().scriptName());attempt++){
                    if(actions(e).contains("doload"))choose(e,"doload");
                    else throw new IOException("Unresolved SC original load confirmation at "+at(e)+" "+actions(e));
                }
                check(script.equals(e.vm().scriptName())&&pc==e.vm().offset()&&body.equals(e.body()),"Native LOAD restores the saved caller sentence and PC");shot(e,out,"12-native-loaded");
                report.put("native_save_load_checked",true);
                report.put("pass",true);
            }catch(Exception failure){report.put("pass",false);report.put("error",failure.toString());report.put("failure_at",at(e));throw failure;}
            finally{report.put("checks",checks);report.put("dialogue_waits",lines);report.put("menus",menus);Files.write(out.resolve("result.json"),(Json.text(report)+"\n").getBytes(StandardCharsets.UTF_8));}
        }
        System.out.println("SC physical desktop PASS "+checks+" checks, "+lines+" dialogue waits");
    }
}
