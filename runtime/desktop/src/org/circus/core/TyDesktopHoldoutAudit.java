package org.circus.core;

import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import javax.imageio.ImageIO;

/** To You natural original selector, twenty dialogue waits and original
 * Save/Load workflow. Never executes an original PE or changes game files. */
public final class TyDesktopHoldoutAudit {
    private static int checks,lines;
    private static final List<Object> menus=new ArrayList<Object>();
    private static final String[] ENTRIES={"hyd","set","mhr","myu"};
    private static final String[] FIRST={"hyd_0611_a1_avn","set_0214_f1_214","ty_mhr01_d1_mhr","ty_myu01_d1_myu"};
    private static void check(boolean ok,String why)throws IOException{checks++;if(!ok)throw new IOException(why);}
    private static PcHost host(DesktopEngine e){return(PcHost)((DesktopLegacy)e).host();}
    private static String at(DesktopEngine e){return e.vm().scriptName()+"@"+Integer.toHexString(e.vm().offset());}
    private static List<String> actions(DesktopEngine e){return DesktopSettingsAudit.actions(e);}
    private static void settle(DesktopEngine e)throws Exception{
        for(int n=0;n<10000;n++){
            e.tick(1000);Dc2Host h=((DesktopLegacy)e).host();if(e.vm().status()==MesVm.Status.FAULT)throw new IOException(e.vm().error());
            if(e.menuWaiting()){menus.add(Json.obj("at",at(e),"actions",actions(e)));return;}
            if(h.presentationWaiting){e.vm().signalHost(e.ticket(),MesVm.HostSignal.TEXT_COMPLETE);continue;}
            if(h.textWaiting&&!h.body.isEmpty())return;
            for(int channel=0;channel<6;channel++)if(e.audioActive(channel)&&!e.audioLoop(channel))e.audioCompleted(channel,e.audioGeneration(channel));
            if(!e.movie().isEmpty())e.vm().signalHost(e.ticket(),MesVm.HostSignal.MEDIA_COMPLETE);
            if(h.remainingMillis()<0)e.vm().signalHost(e.ticket(),MesVm.HostSignal.ADVANCE);
        }
        throw new IOException("Original TY wait did not settle "+at(e)+" "+actions(e));
    }
    private static void choose(DesktopEngine e,String action)throws Exception{check(actions(e).contains(action),"Original TY action present "+action+" at "+at(e)+" "+actions(e));e.choose(action);settle(e);}
    private static void picture(DesktopEngine e,Path out,String name)throws Exception{BufferedImage frame=e.render();try{ImageIO.write(frame,"png",out.resolve(name+".png").toFile());}finally{frame.flush();}}
    /** TySelector.controls projects the reviewed native card index into ty:N.
     * Resolve it through the actual engine controls, never a transfer or variable. */
    private static String selectorAction(DesktopEngine e,int nativeIndex)throws IOException{
        check(e.vm().scriptName().equalsIgnoreCase("sce_select"),"Natural original story-selector script reached");String expected="ty:"+nativeIndex;int found=0;
        for(DesktopEngine.Control control:e.controls())if(control.action.equals(expected))found++;
        check(found==1,"Original TY selector exposes exactly one native control for return index"+nativeIndex+": "+actions(e));return expected;
    }
    public static void main(String[] args)throws Exception{
        if(args.length<2||args.length>3)throw new IOException("TyDesktopHoldoutAudit GAME OUTPUT [hyd|set|mhr|myu]");Path game=Paths.get(args[0]).toRealPath(),out=DesktopRouteAudit.outputOutsideGame(game,args[1]);String entry=args.length==3?args[2]:"hyd";int index=Arrays.asList(ENTRIES).indexOf(entry);if(index<0)throw new IOException("Unknown original TY story category");
        System.setProperty("dc.saveRoot",out.resolve("saves").toString());System.setProperty("dc.cacheRoot",out.resolve("cache").toString());
        Map<String,Object> report=Json.obj("entry",entry,"original_executable_run",false,"original_game_files_written",false,"media_completion_simulated",true,"save_format_scope","Actual original SAVE/LOAD UI writes and restores runtime-owned portable slots; no native Windows SAV conversion");String stage="initialization";
        try(DesktopRuntime app=new DesktopRuntime(game,true,"dc2ty_chinese(simplified)_patch.exe")){
            DesktopEngine e=app.engine;
            try{
                check(e instanceof DesktopLegacy&&host(e).getClass().getSimpleName().equals("TyHost"),"Factory independently binds TyHost");check(e.resources.profileId.equals("dc2ty-5f69"),"Actual script collection admits only the TY profile");report.put("encoding",e.codec.charset.name());
                stage="natural-original-selector";settle(e);picture(e,out,"00-window");choose(e,"03");picture(e,out,"01-title");choose(e,"start");picture(e,out,"02-story-selector");String action=selectorAction(e,index);report.put("selector_action",action);choose(e,action);
                check(e.vm().scriptName().equalsIgnoreCase("sce_select"),"First original card click stays in its preview wait");check(action.equals(selectorAction(e,index)),"Preview retains the same original card control");picture(e,out,"02b-story-preview");choose(e,action);
                check(e.vm().scriptName().equalsIgnoreCase(FIRST[index]),"Original selector return index enters its original expected script");
                stage="twenty-original-dialogues";String previous="";
                while(lines<20){settle(e);check(!e.menuWaiting(),"No unrequested menu before twenty original story waits");String key=at(e)+":"+e.ticket();if(!key.equals(previous)){previous=key;lines++;if(lines==1)picture(e,out,"03-first-text");}if(lines<20)e.vm().signalHost(e.ticket(),MesVm.HostSignal.ADVANCE);}
                check(host(e).textWaiting&&!e.body().isEmpty(),"Twenty real original dialogue waits observed");picture(e,out,"04-text20");String script=e.vm().scriptName(),body=e.body();int pc=e.vm().offset();byte[] snapshot=e.snapshot();
                stage="portable-save-new-host";BufferedImage before=e.render();Path portable=out.resolve("story.sav");e.save(portable);DesktopSave info=DesktopSave.decode(Files.readAllBytes(portable));check(info.image()!=null&&info.image().getWidth()==256,"Actual TY desktop slot thumbnail is readable");Dc2Host original=((DesktopLegacy)e).host();e.restore(portable);BufferedImage restored=e.render();
                check(((DesktopLegacy)e).host()!=original&&host(e).getClass().getSimpleName().equals("TyHost"),"File restore constructs a new independently bound TyHost");check(script.equals(e.vm().scriptName())&&pc==e.vm().offset()&&body.equals(e.body()),"File restore recovers the exact original sentence and PC");check(Arrays.equals(before.getRGB(0,0,e.width,e.height,null,0,e.width),restored.getRGB(0,0,e.width,e.height,null,0,e.width)),"File restore recovers exact displayed pixels");before.flush();restored.flush();check(Arrays.equals(snapshot,e.snapshot()),"TY VM/native snapshot roundtrip is byte-identical");
                report.put("story_location",script+"@"+Integer.toHexString(pc));report.put("story_body_sha256",Binary.sha256(body.getBytes(StandardCharsets.UTF_8)));
                stage="original-SAVE";((DesktopLegacy)e).action(":menu");settle(e);picture(e,out,"05-advmenu");String save=actions(e).contains("save")?"save":actions(e).contains("s")?"s":"";check(!save.isEmpty(),"Original advmenu exposes SAVE");choose(e,save);picture(e,out,"06-save-page");PcHost h=host(e);
                check(h.gridWaiting&&h.saveGrid&&h.grid.size()==5,"Original service6 SAVE binds five active native rows");check(h.gridSlot(0)==0&&h.gridSlot(1)==1&&h.gridSlot(2)==2&&h.gridSlot(4)==4,"Native page0 preserves quick0/1 and ordinary2..4 bindings");check(h.menuActionForGrid(0)==null&&h.menuActionForGrid(1)==null,"Native SAVE rejects reserved quick slots without releasing its wait");
                choose(e,"@slot:2");Path slot=e.saveDirectory.resolve("slot-2.sav");
                for(int n=0;n<7&&!Files.isRegularFile(slot);n++){if(actions(e).contains("iconset00"))choose(e,"iconset00");else if(actions(e).contains("asksave"))choose(e,"asksave");else if(actions(e).contains("dosave"))choose(e,"dosave");else throw new IOException("Unresolved original TY Save confirmation "+at(e)+" "+actions(e));}
                check(Files.isRegularFile(slot),"Original SAVE workflow creates runtime-owned slot2");DesktopSave.decode(Files.readAllBytes(slot));for(int n=0;n<5&&e.menuWaiting()&&actions(e).contains("return");n++)choose(e,"return");check(script.equals(e.vm().scriptName())&&pc==e.vm().offset()&&body.equals(e.body()),"Original SAVE return restores the exact suspended caller");picture(e,out,"07-save-return");
                e.vm().signalHost(e.ticket(),MesVm.HostSignal.ADVANCE);settle(e);check(pc!=e.vm().offset(),"Caller advances after native SAVE return");String nextScript=e.vm().scriptName(),nextBody=e.body();int nextPc=e.vm().offset();
                stage="original-LOAD";((DesktopLegacy)e).action(":menu");settle(e);choose(e,"load");picture(e,out,"08-load-page");h=host(e);check(h.gridWaiting&&!h.saveGrid&&h.grid.size()==5&&h.gridSlot(2)==2,"Original service6 LOAD exposes five active rows and saved slot2");choose(e,"@slot:2");
                for(int n=0;n<7&&!script.equals(e.vm().scriptName());n++){if(actions(e).contains("askload"))choose(e,"askload");else if(actions(e).contains("doload"))choose(e,"doload");else throw new IOException("Unresolved original TY Load confirmation "+at(e)+" "+actions(e));}
                check(script.equals(e.vm().scriptName())&&pc==e.vm().offset()&&body.equals(e.body()),"Original LOAD restores its saved caller sentence and PC");picture(e,out,"09-loaded");e.vm().signalHost(e.ticket(),MesVm.HostSignal.ADVANCE);settle(e);check(nextScript.equals(e.vm().scriptName())&&nextPc==e.vm().offset()&&nextBody.equals(e.body()),"New TyHost executes the identical future sentence and PC after LOAD");
                report.put("future_location",at(e));report.put("native_save_load_checked",true);report.put("new_host_future_checked",true);report.put("result","PASS");
            }catch(Exception failure){report.put("result","FAIL");report.put("stage",stage);report.put("error",failure.toString());report.put("failure_at",at(e));report.put("actions",actions(e));throw failure;}
            finally{report.put("checks",checks);report.put("dialogue_waits",lines);report.put("menus",menus);Files.write(out.resolve("report.json"),Json.text(report).getBytes("UTF-8"));}
        }
        System.out.println(Json.text(Json.obj("result","PASS","checks",checks,"dialogue_waits",lines,"entry",entry)));
    }
}
