package org.circus.core;

import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Actual original DCDX system.dat plus an in-memory two-byte clone, shared
 * importer, owned persistence and original CG scripts. No native file is copied
 * to output, no original game file is changed, and no native EXE is run. */
public final class DcdxProgressAudit {
    private static int checks;
    private static final List<Object> menus=new ArrayList<Object>();
    private static void check(boolean yes,String why)throws IOException{checks++;if(!yes)throw new IOException(why);}
    private static final class SystemStorage implements GameResources.Storage {
        private final LocalGameStorage delegate;private final Set<String> paths;
        SystemStorage(Path root)throws IOException{
            delegate=new LocalGameStorage(root);paths=new TreeSet<String>(delegate.paths());
            for(String name:new String[]{"AdvData/SaveData/system.dat","SaveData/system.dat","system.dat"}){
                Path file=root.resolve(name);if(!Files.isRegularFile(file))continue;if(!file.toRealPath().startsWith(root.toRealPath()))throw new IOException("Native source outside selected game");
                boolean found=false;for(String path:paths)if(path.equalsIgnoreCase(name)){found=true;break;}if(!found)paths.add(name);
            }
        }
        public Set<String> paths(){return Collections.unmodifiableSet(paths);}
        public byte[] read(String path,int limit)throws IOException{return delegate.read(path,limit);}
        public Archive.Source source(String path)throws IOException{return delegate.source(path);}
    }
    private static DcdxHost host(DesktopLegacy e){return(DcdxHost)e.host();}
    private static String at(DesktopLegacy e){return e.vm().scriptName()+"@"+Integer.toHexString(e.vm().offset());}
    private static Set<String> actions(DesktopLegacy e){Set<String> result=new LinkedHashSet<String>();for(DesktopEngine.Control c:e.controls())if(!c.action.isEmpty())result.add(c.action);for(ScriptList.Item item:host(e).list.items)if(!item.action.isEmpty())result.add(item.action);return result;}
    private static void settle(DesktopLegacy e,boolean allowStory)throws Exception{
        for(int n=0;n<10000;n++){
            e.tick(1000);DcdxHost h=host(e);if(e.vm().status()==MesVm.Status.FAULT)throw new IOException(e.vm().error());
            if(e.menuWaiting()&&h.dcdxMenu.active&&!h.dcdxMenu.presented)continue;
            if(e.menuWaiting()){menus.add(Json.obj("at",at(e),"actions",actions(e)));return;}
            if(h.presentationWaiting){e.vm().signalHost(e.ticket(),MesVm.HostSignal.TEXT_COMPLETE);continue;}
            if(h.textWaiting&&!h.body.isEmpty()){if(allowStory)return;throw new IOException("Unexpected story in original DCDX gallery "+at(e));}
            for(int c=0;c<6;c++)if(e.audioActive(c)&&!e.audioLoop(c))e.audioCompleted(c,e.audioGeneration(c));
            if(!e.movie().isEmpty())e.vm().signalHost(e.ticket(),MesVm.HostSignal.MEDIA_COMPLETE);
            if(h.remainingMillis()<0)e.vm().signalHost(e.ticket(),MesVm.HostSignal.ADVANCE);
        }
        throw new IOException("Original DCDX UI did not settle "+at(e)+" "+actions(e));
    }
    private static void choose(DesktopLegacy e,String action,boolean story)throws Exception{check(actions(e).contains(action),"Original DCDX action exists "+action+" at "+at(e)+" "+actions(e));e.choose(action);settle(e,story);}
    private static void title(DesktopLegacy e)throws Exception{settle(e,false);choose(e,"window",false);choose(e,"story",false);choose(e,"ystory",false);check(actions(e).contains("sce")&&actions(e).contains("ex_anime"),"Original DCDX title reached through original wheel and confirmation");}
    private static void story(DesktopLegacy e)throws Exception{title(e);choose(e,"sce",false);choose(e,"dc",false);choose(e,"ydc",true);check(e.host().textWaiting&&!e.body().isEmpty(),"Preimport owned slot is made from an actual original story sentence");}
    private static void preferences(DcdxHost h)throws IOException{check(h.familyName.equals("KEEP")&&h.givenName.equals("NAME")&&h.read(99,12)==2&&h.read(100,59)==29,"Current names, speed and system voice choice survive selective import");}
    private static Map<String,Object> summary(NativeProgress.Summary s){return Json.obj("completion",s.completion,"music",s.music,"cg",s.cg,"scenes",s.scenes,"total",s.total);}
    private static Map<String,Object> rendered(DesktopLegacy e)throws IOException{
        BufferedImage image=e.render();try{check(image.getWidth()==1024&&image.getHeight()==576,"Actual DCDX renderer keeps its original canvas");int[] values=image.getRGB(0,0,1024,576,null,0,1024);byte[] bytes=new byte[values.length*3];for(int n=0;n<values.length;n++){bytes[n*3]=(byte)values[n];bytes[n*3+1]=(byte)(values[n]>>>8);bytes[n*3+2]=(byte)(values[n]>>>16);}return Json.obj("at",at(e),"render_BGR_sha256",Binary.sha256(bytes));}finally{image.flush();}
    }
    private static String normal(String text){String s=text.replace('\\','/').toLowerCase(Locale.ROOT);return s.endsWith(".crx")?s.substring(0,s.length()-4):s;}
    private static boolean displayed(DesktopLegacy e,String name){for(String slot:e.host().scene.shown.slots)if(normal(slot).equals(normal(name)))return true;return false;}
    public static void main(String[] args)throws Exception{
        if(args.length!=2)throw new IOException("Require readonly_DCDX_GAME owned_OUTPUT");Path game=Paths.get(args[0]).toRealPath(),out=DesktopRouteAudit.outputOutsideGame(game,args[1]);
        System.setProperty("dc.saveRoot",out.resolve("saves").toString());System.setProperty("dc.cacheRoot",out.resolve("cache").toString());
        Map<String,Object> report=Json.obj("scope","Original DCDX system progress and in-memory two-byte clone; shared importer, owned runtime persistence, restart, old-slot restore and actual original CG scripts. Native narrative file conversion is excluded.","original_executable_run",false,"original_game_files_written",false,"original_system_bytes_exported",false,"media_completion_simulated",true);DesktopLegacy e=null;String stage="admission";
        try{
            GameResources files=new GameResources(new SystemStorage(game),"DCDX.EXE");Profile profile=GameVersion.detect(files).profile();check(profile.id.equals("dcdx-7769")&&NativeProgress.supports(profile.id),"Selected game independently admits the DCDX native progress layout");CircusResources data=new CircusResources(files,profile);TextCodec codec=GameText.detect(files,profile);
            List<String> paths=NativeProgress.sourcePaths(files,profile.id);check(paths.size()==1,"Only one known native system source selected; backups are not guessed");byte[] original=files.read(paths.get(0),NativeProgress.fileSize(profile.id));NativeProgress progress=NativeProgress.parse(profile.id,original);
            check(progress.source.completion==0&&progress.source.music==0&&progress.source.cg>0&&progress.source.scenes>0,"Actual original source imports only its existing CG and seen progress");report.put("original_source",Json.obj("path",paths.get(0),"physical_bytes",original.length,"native_prefix_bytes",2136,"header_u32_0x48",Binary.u32(original,0x48),"sha256",Binary.sha256(original),"summary",summary(progress.source)));
            stage="owned-preimport-story-slot";e=new DesktopLegacy(data,codec,false);story(e);String script=e.vm().scriptName(),body=e.body();int pc=e.vm().offset();Path slot=e.saveDirectory.resolve("slot-4.sav");e.save(slot);String slotSha=Binary.sha256(Files.readAllBytes(slot));
            host(e).setNativeNames("KEEP","NAME");host(e).write(99,12,2);host(e).write(100,59,29);e.persist();preferences(host(e));Path state=e.saveDirectory.resolve("progress.dat");NativeProgress.Target target=new NativeProgress.Target(data,codec);target.load(Files.readAllBytes(state));byte[] before=target.save();
            stage="locked-original-CG-baseline";e=new DesktopLegacy(data,codec,false);title(e);choose(e,"ex_anime",false);byte[] extra=e.snapshot();choose(e,"cg_mode",false);Set<String> locked=actions(e);report.put("CG_before_import",rendered(e));
            stage="shared-original-import";NativeProgress.Summary preview=progress.preview(target);check(preview.cg>0&&preview.scenes>0&&preview.completion==0&&preview.music==0,"Real original source preview finds existing source progress missing on phone");check(Arrays.equals(before,target.save()),"Original source preview is byte-exact read-only");NativeProgress.Summary applied=progress.apply(target);check(applied.total==preview.total,"Real original source apply equals preview");preferences((DcdxHost)target.legacy);report.put("original_preview",summary(preview));report.put("original_applied",summary(applied));
            int cg=-1,scene=-1;for(int n=0;n<Math.min(640,data.names.table("images").size());n++)if(original[0xd8+n]==0&&target.read(97,n)==0){cg=n;break;}for(int n=0;n<1024;n++)if(original[0x358+n]==0&&target.read(98,n)==0){scene=n;break;}check(cg>=0&&scene>=0,"Clone chooses independently locked original CG and seen bytes");
            byte[] clone=original.clone();clone[0xd8+cg]=1;clone[0x358+scene]=1;int differences=0;for(int n=0;n<clone.length;n++)if(clone[n]!=original[n]){differences++;check(n==0xd8+cg||n==0x358+scene,"Clone changes no native settings, globals, voice or checkpoint bytes");}check(differences==2,"In-memory fixture changes precisely two locked progress bytes");
            NativeProgress fixture=NativeProgress.parse(profile.id,clone);NativeProgress.Summary added=fixture.preview(target);check(added.cg==1&&added.scenes==1&&added.total==2,"Two-byte fixture adds exactly one CG and one seen flag after real import");check(fixture.apply(target).total==2,"Shared fixture apply matches preview");report.put("fixture",Json.obj("scope","in-memory clone only; no native file exported","sha256",Binary.sha256(clone),"changed_offsets",Arrays.asList(0xd8+cg,0x358+scene),"CG_bank97_index",cg,"seen_bank98_index",scene,"preview",summary(added)));
            preferences((DcdxHost)target.legacy);byte[] merged=target.save();check(fixture.preview(target).total==0&&fixture.apply(target).total==0&&Arrays.equals(merged,target.save()),"Combined import is byte-for-byte idempotent");DesktopEngine.atomic(state,merged);check(Binary.sha256(Files.readAllBytes(slot)).equals(slotSha),"Neither import rewrites the existing owned story slot");
            stage="restart-old-slot-restore";e=new DesktopLegacy(data,codec,false);preferences(host(e));check(fixture.preview(host(e)).total==0,"Actual DesktopLegacy restart loads merged progress.dat");e.restore(slot);preferences(host(e));check(fixture.preview(host(e)).total==0,"Old story-slot restore retains newer imported progress");check(e.vm().scriptName().equals(script)&&e.vm().offset()==pc&&e.body().equals(body),"Old slot restores the exact original sentence and instruction cursor");
            stage="original-imported-CG-reader";e=new DesktopLegacy(data,codec,false);title(e);choose(e,"ex_anime",false);choose(e,"cg_mode",false);Set<String> unlocked=new LinkedHashSet<String>(actions(e));unlocked.removeAll(locked);check(!unlocked.isEmpty(),"Actual original CG menu exposes imported unlocks");report.put("CG_actions_added",unlocked);report.put("CG_after_import",rendered(e));String action=unlocked.iterator().next();e.choose(action);String expected=data.names.table("images").get(0).name;
            for(int n=0;n<200&&!displayed(e,expected);n++){e.tick(40);for(int c=0;c<6;c++)e.audioCompleted(c,e.audioGeneration(c));}
            check(displayed(e,expected),"Imported original CG action reads its original named CG image");report.put("CG_reader",Json.obj("action",action,"image",expected,"render",rendered(e)));preferences(host(e));check(fixture.preview(host(e)).total==0,"Original gallery navigation preserves all imported bits");
            check(Binary.sha256(files.read(paths.get(0),NativeProgress.fileSize(profile.id))).equals(Binary.sha256(original)),"Actual original system.dat remains byte-identical");check(Binary.sha256(Files.readAllBytes(slot)).equals(slotSha),"Existing owned story slot remains byte-identical after original gallery navigation");
            report.put("owned_existing_slot",Json.obj("relative_name","slot-4.sav","sha256",slotSha,"script",script,"pc",pc,"body_sha256",Binary.sha256(body.getBytes(StandardCharsets.UTF_8))));report.put("result","PASS");
        }catch(Exception failure){report.put("result","FAIL");report.put("stage",stage);report.put("error",failure.toString());if(e!=null){report.put("failure_at",at(e));report.put("actions",actions(e));}throw failure;}
        finally{report.put("checks",checks);report.put("menus",menus);Files.write(out.resolve("report.json"),Json.text(report).getBytes("UTF-8"));}
        System.out.println(Json.text(Json.obj("result","PASS","checks",checks)));
    }
}
