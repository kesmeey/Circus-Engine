package org.circus.core;

import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import javax.imageio.ImageIO;

/** Original FL title LOAD, including read-only phone MES overlays with stale
 * physical bytes beyond native STOP. Writes only runtime-owned audit saves. */
public final class FlTitleLoadAudit {
    private static int checks,lines;
    private static final List<Object> menus=new ArrayList<>();
    private static void check(boolean yes,String why)throws IOException{checks++;if(!yes)throw new IOException(why);}
    private static String at(DesktopEngine e){return e.vm().scriptName()+"@"+Integer.toHexString(e.vm().offset());}
    private static List<String> actions(DesktopEngine e){return DesktopSettingsAudit.actions(e);}
    private static void settle(DesktopEngine e)throws Exception{
        for(int n=0;n<10000;n++){
            e.tick(1000);Dc2Host h=((DesktopLegacy)e).host();if(e.vm().status()==MesVm.Status.FAULT)throw new IOException(e.vm().error());
            if(e.menuWaiting()){menus.add(Json.obj("at",at(e),"actions",actions(e)));return;}
            if(h.presentationWaiting){e.vm().signalHost(e.ticket(),MesVm.HostSignal.TEXT_COMPLETE);continue;}
            if(h.textWaiting&&!h.body.isEmpty())return;
            for(int c=0;c<6;c++)if(e.audioActive(c)&&!e.audioLoop(c))e.audioCompleted(c,e.audioGeneration(c));
            if(!e.movie().isEmpty())e.vm().signalHost(e.ticket(),MesVm.HostSignal.MEDIA_COMPLETE);
            if(h.remainingMillis()<0)e.vm().signalHost(e.ticket(),MesVm.HostSignal.ADVANCE);
        }
        throw new IOException("FL title LOAD did not settle at "+at(e));
    }
    private static void choose(DesktopEngine e,String action)throws Exception{check(actions(e).contains(action),"Missing original action "+action+" at "+at(e)+" "+actions(e));e.choose(action);settle(e);}
    private static void shot(DesktopEngine e,Path out,String name)throws IOException{ImageIO.write(e.render(),"png",out.resolve(name+".png").toFile());}
    private static DesktopLegacy open(Path game,Path copies,Map<String,Object> sources)throws Exception{
        final LocalGameStorage local=new LocalGameStorage(game);final Map<String,byte[]> overlay=new HashMap<>();
        if(copies!=null)for(String name:new String[]{"LOAD","SAVE","main","aft_1122_a1_akn"}){
            Path p=copies.resolve("phone-"+name+".mes");if(!Files.isRegularFile(p)&&name.equals("aft_1122_a1_akn"))continue;byte[] bytes=Files.readAllBytes(p);overlay.put(("advdata/mes/"+name+".mes").toLowerCase(Locale.ROOT),bytes);sources.put(name,Json.obj("path",p.toString(),"bytes",bytes.length,"sha256",Binary.sha256(bytes)));
        }
        if(copies!=null&&Files.isRegularFile(copies.resolve("phone-uif_config.json"))){Path p=copies.resolve("phone-uif_config.json");byte[] bytes=Files.readAllBytes(p);overlay.put("uif_config.json",bytes);sources.put("uif_config",Json.obj("path",p.toString(),"bytes",bytes.length,"sha256",Binary.sha256(bytes)));}
        final Set<String> paths=new TreeSet<>(local.paths());for(String key:overlay.keySet()){boolean present=false;for(String path:paths)if(path.replace('\\','/').equalsIgnoreCase(key)){present=true;break;}if(!present)paths.add(key);}
        GameResources.Storage storage=new GameResources.Storage(){
            public Set<String> paths(){return paths;}
            private byte[] override(String path){return overlay.get(path.replace('\\','/').toLowerCase(Locale.ROOT));}
            public byte[] read(String path,int limit)throws IOException{byte[] b=override(path);if(b==null)return local.read(path,limit);if(b.length>limit)throw new IOException("Overlay source exceeds read limit");return b.clone();}
            public Archive.Source source(String path)throws IOException{final byte[] b=override(path);if(b==null)return local.source(path);return new Archive.Source(){public long size(){return b.length;}public byte[] read(long at,int n)throws IOException{Binary.range(at,n,b.length);return Arrays.copyOfRange(b,(int)at,(int)at+n);}};}
        };
        GameResources files=new GameResources(storage,"DC2FL.exe");Profile profile=GameVersion.detect(files).profile();check(profile.id.equals("dc2fl-9c69"),"FL independent profile selected");return new DesktopLegacy(new CircusResources(files,profile),GameText.detect(files,profile),true);
    }
    private static void title(DesktopEngine e)throws Exception{settle(e);if(actions(e).contains("03"))choose(e,"03");check(e.menuWaiting()&&actions(e).contains("start")&&actions(e).contains("load"),"Natural startup reaches original title controls");}
    public static void main(String[] args)throws Exception{
        Path game=Paths.get(args[0]).toRealPath(),out=DesktopRouteAudit.outputOutsideGame(game,args[1]),copies=args.length>2?Paths.get(args[2]).toRealPath():null;System.setProperty("dc.saveRoot",out.resolve("saves").toString());System.setProperty("dc.cacheRoot",out.resolve("cache").toString());
        Map<String,Object> sources=new LinkedHashMap<>(),report=Json.obj("original_executable_run",false,"original_game_files_written",false,"phone_sources_read_only",true,"media_completion_simulated",true,"scope","Original title LOAD and original SAVE UI with runtime-owned portable slot; no original Windows SAV conversion");DesktopLegacy e=null;
        try{
            e=open(game,copies,sources);title(e);shot(e,out,"00-title-empty");choose(e,"load");FlHost h=(FlHost)e.host();check(h.gridWaiting&&!h.saveGrid&&h.grid.size()==5,"Cold title LOAD reaches original five-row service6");check(h.availableSaves.isEmpty(),"Cold audit starts with no runtime-owned slots");
            for(int n=0;n<5;n++)check(h.menuActionForGrid(n)==null,"Empty title LOAD row is disabled "+n);shot(e,out,"01-title-load-empty");choose(e,"return");check(actions(e).contains("start")&&actions(e).contains("load"),"Empty LOAD Return reaches title");
            choose(e,"start");choose(e,"sce01");choose(e,"sce01_set");choose(e,"sce01");String prior="";
            while(lines<20){settle(e);check(!e.menuWaiting(),"Story remains on text before twentieth wait");String key=at(e)+":"+e.ticket();if(!key.equals(prior)){prior=key;lines++;}if(lines<20)e.vm().signalHost(e.ticket(),MesVm.HostSignal.ADVANCE);}
            check(!e.body().isEmpty(),"Natural story reaches20 original text waits");String script=e.vm().scriptName(),body=e.body();int pc=e.vm().offset();BufferedImage before=e.render();shot(e,out,"02-story20");report.put("story_location",script+"@"+Integer.toHexString(pc));report.put("body_sha256",Binary.sha256(body.getBytes(StandardCharsets.UTF_8)));
            e.action(":menu");settle(e);choose(e,"save");h=(FlHost)e.host();check(h.gridWaiting&&h.saveGrid,"Original story SAVE invokes Save service6");check(h.gridSlot(2)==2&&h.menuActionForGrid(0)==null&&h.menuActionForGrid(1)==null,"Save reserves0/1 and maps selected native slot2");choose(e,"@slot:2");Path slot=e.saveDirectory.resolve("slot-2.sav");
            for(int n=0;n<5&&!Files.isRegularFile(slot);n++){if(actions(e).contains("iconset00"))choose(e,"iconset00");else if(actions(e).contains("dosave"))choose(e,"dosave");else throw new IOException("Unexpected save confirmation "+at(e)+" "+actions(e));}
            check(Files.isRegularFile(slot),"Original SAVE writes runtime-owned portable slot2");DesktopSave saved=DesktopSave.decode(Files.readAllBytes(slot));check(saved.image()!=null,"Original slot thumbnail decodes");String slotHash=Binary.sha256(Files.readAllBytes(slot));
            for(int n=0;n<3&&e.menuWaiting()&&actions(e).contains("return");n++)choose(e,"return");check(script.equals(e.vm().scriptName())&&pc==e.vm().offset()&&body.equals(e.body()),"SAVE returns to same caller sentence and PC");e.vm().signalHost(e.ticket(),MesVm.HostSignal.ADVANCE);settle(e);String futureScript=e.vm().scriptName(),futureBody=e.body();int futurePc=e.vm().offset();check(futurePc!=pc,"Caller advances after original SAVE");
            DesktopLegacy cold=open(game,copies,sources);check(cold.host()!=e.host(),"Cold title constructs a distinct VM and FL host");title(cold);choose(cold,"load");FlHost loaded=(FlHost)cold.host();check(loaded.gridWaiting&&!loaded.saveGrid&&loaded.availableSaves.contains(2),"Cold title LOAD discovers the saved portable slot2");choose(cold,"@slot:2");for(int n=0;n<5&&!script.equals(cold.vm().scriptName());n++){if(actions(cold).contains("doload"))choose(cold,"doload");else throw new IOException("Unexpected cold title load confirmation "+at(cold)+" "+actions(cold));}
            check(script.equals(cold.vm().scriptName())&&pc==cold.vm().offset()&&body.equals(cold.body()),"Cold title LOAD restores saved caller sentence and PC");check(Arrays.equals(before.getRGB(0,0,e.width,e.height,null,0,e.width),cold.render().getRGB(0,0,cold.width,cold.height,null,0,cold.width)),"Cold title LOAD restores exact portable framebuffer pixels");check(slotHash.equals(Binary.sha256(Files.readAllBytes(slot))),"LOAD leaves saved slot bytes unchanged");shot(cold,out,"03-cold-title-loaded");cold.vm().signalHost(cold.ticket(),MesVm.HostSignal.ADVANCE);settle(cold);check(futureScript.equals(cold.vm().scriptName())&&futurePc==cold.vm().offset()&&futureBody.equals(cold.body()),"Cold-loaded new host follows identical next sentence");report.put("result","PASS");report.put("native_title_load_empty_and_return",true);report.put("story_save_then_cold_title_load",true);report.put("new_host_future_checked",true);
        }catch(Exception failure){report.put("result","FAIL");report.put("error",failure.toString());if(e!=null)report.put("failure_at",at(e));throw failure;}
        finally{report.put("checks",checks);report.put("dialogue_waits",lines);report.put("read_only_phone_overlays",sources);report.put("menus",menus);Files.write(out.resolve("report.json"),(Json.text(report)+"\n").getBytes(StandardCharsets.UTF_8));}
        System.out.println("FL title LOAD PASS "+checks+" checks, "+lines+" dialogue waits");
    }
}
