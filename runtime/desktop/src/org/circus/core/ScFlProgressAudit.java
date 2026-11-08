package org.circus.core;

import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import javax.imageio.ImageIO;

/** Original zero-progress files and isolated edited fixtures, shared importer and actual legacy UI.
 * Fixtures never replace original files and are not evidence of original completed progress. */
public final class ScFlProgressAudit {
    /** The generic audit adapter deliberately excludes SaveData; expose only known native-system
     * locations for this importer audit, preserving read-only access and root containment. */
    private static final class NativeSystemStorage implements GameResources.Storage {
        private final LocalGameStorage files;private final Set<String> paths;
        NativeSystemStorage(Path root)throws IOException{
            files=new LocalGameStorage(root);paths=new TreeSet<String>(files.paths());
            for(String name:new String[]{"AdvData/SaveData/system.dat","SaveData/system.dat","system.dat"}){
                Path path=root.resolve(name);if(!Files.isRegularFile(path))continue;
                if(!path.toRealPath().startsWith(root.toRealPath()))throw new IOException("Native system path outside selected game");
                boolean present=false;for(String current:paths)if(current.equalsIgnoreCase(name)){present=true;break;}
                if(!present)paths.add(name);
            }
        }
        public Set<String> paths(){return Collections.unmodifiableSet(paths);}
        public byte[] read(String name,int limit)throws IOException{return files.read(name,limit);}
        public Archive.Source source(String name)throws IOException{return files.source(name);}
    }
    private static int checks;
    private static final List<Object> menus=new ArrayList<Object>();
    private static void check(boolean ok,String message)throws IOException{checks++;if(!ok)throw new IOException(message);}
    private static PcHost host(DesktopLegacy e){return(PcHost)e.host();}
    private static String at(DesktopLegacy e){return e.vm().scriptName()+"@"+Integer.toHexString(e.vm().offset());}
    private static Set<String> contentActions(DesktopLegacy e){
        Set<String> out=new LinkedHashSet<String>();for(DesktopEngine.Control control:e.controls())if(!control.action.isEmpty())out.add(control.action);
        for(ScriptList.Item item:host(e).list.items)if(!item.action.isEmpty())out.add(item.action);
        return out;
    }
    private static void settle(DesktopLegacy e,boolean story)throws Exception {
        for(int n=0;n<10000;n++){
            e.tick(1000);Dc2Host h=e.host();if(e.vm().status()==MesVm.Status.FAULT)throw new IOException(e.vm().error());
            if(e.menuWaiting()){menus.add(Json.obj("at",at(e),"actions",contentActions(e)));return;}
            if(h.presentationWaiting){e.vm().signalHost(e.ticket(),MesVm.HostSignal.TEXT_COMPLETE);continue;}
            if(h.textWaiting&&!h.body.isEmpty()){if(story)return;throw new IOException("Unexpected story while entering original gallery "+at(e));}
            for(int c=0;c<6;c++)if(e.audioActive(c)&&!e.audioLoop(c))e.audioCompleted(c,e.audioGeneration(c));
            if(!e.movie().isEmpty())e.vm().signalHost(e.ticket(),MesVm.HostSignal.MEDIA_COMPLETE);
            if(h.remainingMillis()<0)e.vm().signalHost(e.ticket(),MesVm.HostSignal.ADVANCE);
        }
        throw new IOException("Original menu did not settle "+at(e)+" "+contentActions(e));
    }
    private static void choose(DesktopLegacy e,String action,boolean story)throws Exception {
        check(contentActions(e).contains(action),"Original action absent "+action+" at "+at(e)+" "+contentActions(e));
        String control=action;boolean direct=false;for(DesktopEngine.Control candidate:e.controls())if(candidate.action.equals(action)){direct=true;break;}
        if(!direct){
            PcHost h=host(e);int row=-1;for(int n=0;n<h.grid.size();n++){ScriptList.Item item=h.list.at(h.listOffset(),n,h.grid.size());if(item!=null&&item.action.equals(action)){row=n;break;}}
            check(row>=0,"Unlocked original list action is visible in a real grid cell");control="@slot:"+row;
        }
        e.choose(control);settle(e,story);
    }
    private static void title(DesktopLegacy e)throws Exception {
        settle(e,false);for(int n=0;n<10;n++){
            Set<String> actions=contentActions(e);if(actions.contains("start")&&actions.contains("extra"))return;
            if(actions.contains("03"))choose(e,"03",false);else if(actions.contains("window"))choose(e,"window",false);else throw new IOException("Original startup action unresolved "+at(e)+" "+actions);
        }
        throw new IOException("Original title not reached");
    }
    private static void story(DesktopLegacy e,boolean sc)throws Exception {
        title(e);choose(e,"start",false);
        if(sc){choose(e,"after_story",false);choose(e,"after_otm",false);choose(e,"read_after",true);}
        else{choose(e,"sce01",false);choose(e,"sce01_set",false);choose(e,"sce01",true);}
        settle(e,true);check(e.host().textWaiting&&!e.body().isEmpty(),"Real original story sentence reached for preexisting owned slot");
    }
    private static void shot(DesktopLegacy e,Path out,String name)throws IOException {BufferedImage image=e.render();try{ImageIO.write(image,"png",out.resolve(name+".png").toFile());}finally{image.flush();}}
    private static void preferences(PcHost h)throws IOException {check(h.familyName.equals("KEEP")&&h.givenName.equals("NAME")&&h.read(99,12)==2,"Owned names and transition speed remain unchanged");}
    private static String normal(String value){String s=value.replace('\\','/').toLowerCase(Locale.ROOT);return s.endsWith(".crx")?s.substring(0,s.length()-4):s;}
    private static boolean shownImage(DesktopLegacy e,String image){for(String slot:e.host().scene.shown.slots)if(normal(slot).equals(normal(image)))return true;return false;}
    private static Map<String,Object> summary(NativeProgress.Summary s){return Json.obj("completion",s.completion,"music",s.music,"cg",s.cg,"scenes",s.scenes,"total",s.total);}
    public static void main(String[] args)throws Exception {
        if(args.length!=2)throw new IOException("Require readonly_SC_or_FL_GAME owned_OUTPUT");Path game=Paths.get(args[0]).toRealPath(),out=DesktopRouteAudit.outputOutsideGame(game,args[1]);
        System.setProperty("dc.saveRoot",out.resolve("saves").toString());System.setProperty("dc.cacheRoot",out.resolve("cache").toString());
        GameResources files=new GameResources(new NativeSystemStorage(game));Profile profile=GameVersion.detect(files).profile();
        boolean sc=profile.id.equals("dc2sc-3b69");check(sc||profile.id.equals("dc2fl-9c69"),"Independently detected SC/FL MES profile required");
        CircusResources data=new CircusResources(files,profile);TextCodec codec=GameText.detect(files,profile);Map<String,Object> report=Json.obj("profile",profile.id,"original_executable_run",false,"original_game_files_written",false,"nonzero_source_scope","owned clone of zero-progress original system.dat; two gallery/scene bytes only","media_completion_simulated",true);
        DesktopLegacy current=null;String stage="original-source";
        try{
            List<String> sources=NativeProgress.sourcePaths(files,profile.id);check(sources.size()==1,"One known original system.dat path selected");
            byte[] original=files.read(sources.get(0),NativeProgress.fileSize(profile.id));NativeProgress zero=NativeProgress.parse(profile.id,original);check(zero.source.total==0,"Original source has zero imported progress");
            report.put("original_source",Json.obj("path",sources.get(0),"bytes",original.length,"sha256",Binary.sha256(original),"summary",summary(zero.source)));
            stage="preexisting-runtime-slot";current=new DesktopLegacy(data,codec,false);story(current,sc);
            String savedScript=current.vm().scriptName(),savedBody=current.body();int savedPc=current.vm().offset();
            Path slot=current.saveDirectory.resolve("slot-4.sav");current.save(slot);String slotSha=Binary.sha256(Files.readAllBytes(slot));
            host(current).setNativeNames("KEEP","NAME");host(current).write(99,12,2);current.persist();preferences(host(current));
            Path progress=current.saveDirectory.resolve("progress.dat");check(Files.isRegularFile(progress),"Existing runtime settings persisted into owned progress.dat");
            NativeProgress.Target before=new NativeProgress.Target(data,codec);before.load(Files.readAllBytes(progress));byte[] unchanged=before.save();
            check(zero.preview(before).total==0&&zero.apply(before).total==0&&Arrays.equals(unchanged,before.save()),"Original zero source preview/apply adds nothing and is byte-exact");
            stage="locked-original-gallery-baseline";current=new DesktopLegacy(data,codec,false);title(current);choose(current,"extra",false);byte[] oldExtra=current.snapshot();
            choose(current,"cg",false);Set<String> lockedCg=contentActions(current);shot(current,out,"01-cg-before");
            current.restore(oldExtra);choose(current,"scene",false);Set<String> lockedScenes=contentActions(current);shot(current,out,"02-scenes-before");
            int cg=0,scene=sc?1:97,cgOffset=sc?0xd4:0xd8,sceneOffset=sc?0x4d4:0x358;
            check(before.read(97,cg)==0&&before.read(98,scene)==0,"Fixture gallery and next-story entry were locked before import");
            byte[] fixture=original.clone();fixture[cgOffset+cg]=1;fixture[sceneOffset+scene]=1;
            for(int n=0;n<fixture.length;n++)if(fixture[n]!=original[n])check(n==cgOffset+cg||n==sceneOffset+scene,"Owned fixture changes only selected native CG/seen bytes");
            Path fixturePath=out.resolve("fixture-system.dat");Files.write(fixturePath,fixture);report.put("fixture",Json.obj("path","fixture-system.dat","bytes",fixture.length,"sha256",Binary.sha256(fixture),"CG_bank97_index",cg,"scene_bank98_index",scene,"changed_offsets",Arrays.asList(cgOffset+cg,sceneOffset+scene)));
            stage="shared-import-apply";NativeProgress importProgress=NativeProgress.parse(profile.id,fixture);NativeProgress.Target target=new NativeProgress.Target(data,codec);target.load(Files.readAllBytes(progress));
            NativeProgress.Summary preview=importProgress.preview(target);check(preview.completion==0&&preview.music==0&&preview.cg==1&&preview.scenes==1,"Shared preview reports precisely the two locked fixture unlocks");
            NativeProgress.Summary applied=importProgress.apply(target);check(applied.total==2,"Shared Target/apply merges both native fixture bytes");preferences((PcHost)target.legacy);
            byte[] imported=target.save();check(importProgress.preview(target).total==0&&importProgress.apply(target).total==0&&Arrays.equals(imported,target.save()),"Shared progress import is idempotent");
            DesktopEngine.atomic(progress,imported);check(Binary.sha256(Files.readAllBytes(slot)).equals(slotSha),"Import never rewrites existing story slot bytes");report.put("preview",summary(preview));report.put("applied",summary(applied));
            stage="restart-and-old-slot-restore";current=new DesktopLegacy(data,codec,false);preferences(host(current));check(importProgress.preview(host(current)).total==0,"Actual DesktopLegacy restart loads imported progress.dat");
            current.restore(slot);preferences(host(current));check(importProgress.preview(host(current)).total==0,"Restoring preimport story slot preserves imported unlocks");
            check(current.vm().scriptName().equals(savedScript)&&current.vm().offset()==savedPc&&current.body().equals(savedBody),"Preexisting story slot still restores its original sentence and PC");
            check(Binary.sha256(Files.readAllBytes(slot)).equals(slotSha),"Restart and restore preserve existing story slot file hash");
            stage="imported-original-gallery";current=new DesktopLegacy(data,codec,false);title(current);shot(current,out,"03-imported-title");choose(current,"extra",false);byte[] extra=current.snapshot();
            choose(current,"cg",false);Set<String> unlockedCg=contentActions(current);Set<String> newCg=new LinkedHashSet<String>(unlockedCg);newCg.removeAll(lockedCg);report.put("CG_actions_added",newCg);
            check(newCg.equals(Collections.singleton(sc?"cg1_a":"cg0")),"Original CG script exposes precisely the fixture-selected newly unlocked action");shot(current,out,"04-cg-imported");String cgAction=newCg.iterator().next();current.choose(cgAction);
            String expectedImage=data.names.table("images").get(cg).name;
            for(int n=0;n<100&&!shownImage(current,expectedImage);n++){current.tick(40);for(int c=0;c<6;c++)current.audioCompleted(c,current.audioGeneration(c));}
            check(shownImage(current,expectedImage),"Imported CG action reaches its original named image");shot(current,out,"05-imported-CG-viewer");
            current.restore(extra);choose(current,"scene",false);Set<String> unlockedScenes=contentActions(current);Set<String> newScenes=new LinkedHashSet<String>(unlockedScenes);newScenes.removeAll(lockedScenes);report.put("scene_actions_added",newScenes);
            check(newScenes.equals(Collections.singleton(sc?"01":"sce1")),"Original scene gallery exposes precisely the fixture-selected newly unlocked action");shot(current,out,"06-scenes-imported");
            stage="imported-original-scene-playback";choose(current,newScenes.iterator().next(),true);
            check(current.host().textWaiting&&!current.body().isEmpty(),"Imported original scene action reaches a real original story sentence");
            report.put("imported_scene_playback",Json.obj("script",current.vm().scriptName(),"pc",current.vm().offset(),"body_sha256",Binary.sha256(current.body().getBytes(StandardCharsets.UTF_8))));shot(current,out,"07-imported-scene-playback");
            check(importProgress.preview(host(current)).total==0,"Original gallery navigation preserves imported native flags");preferences(host(current));
            check(Binary.sha256(files.read(sources.get(0),original.length)).equals(Binary.sha256(original)),"Original system.dat remains byte-identical");
            check(Binary.sha256(Files.readAllBytes(slot)).equals(slotSha),"Original UI navigation preserves preexisting owned story slot");
            report.put("preexisting_story_slot",Json.obj("relative_path","slot-4.sav","sha256",slotSha,"script",savedScript,"pc",savedPc,"body_sha256",Binary.sha256(savedBody.getBytes(StandardCharsets.UTF_8))));
            report.put("completion_import_scope",sc?"No SC completion globals imported; independently unproven completion fields excluded.":"FL allowlist0..13/52/53 supported by shared importer; this fixture changes only CG/seen.");report.put("result","PASS");
        }catch(Exception failure){report.put("result","FAIL");report.put("error",failure.toString());report.put("stage",stage);if(current!=null){report.put("failure_at",at(current));report.put("actions",contentActions(current));}throw failure;}
        finally{report.put("checks",checks);report.put("menus",menus);Files.write(out.resolve("report.json"),(Json.text(report)+"\n").getBytes(StandardCharsets.UTF_8));}
        System.out.println(Json.text(Json.obj("result","PASS","profile",profile.id,"checks",checks)));
    }
}
