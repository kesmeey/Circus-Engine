package org.circus.runtime;

import android.app.Instrumentation;
import android.net.Uri;
import android.os.Bundle;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import org.circus.core.*;

/** Four version-native SAF sources, shipping importer, isolated phone save directories. */
public final class NativeImportMatrixInstrumentation extends Instrumentation {
    private String tree,source;private int checks;private final StringBuilder log=new StringBuilder();
    public void onCreate(Bundle args){super.onCreate(args);tree=args.getString("tree");source=args.getString("source");start();}
    private void check(boolean ok,String why)throws IOException{if(!ok)throw new IOException(why);checks++;log.append("PASS ").append(why).append('\n');}
    public void onStart(){Bundle result=new Bundle();String old=System.getProperty("dc.saveRoot");Path output=null;
        try{
            output=getTargetContext().getFilesDir().toPath().resolve("native-import-matrix").resolve("run-"+System.currentTimeMillis());Files.createDirectories(output);
            GameResources files=new GameResources(new DocumentTree(getTargetContext().getContentResolver(),Uri.parse(tree),true));Profile profile=GameVersion.detect(files).profile();
            CircusResources data=new CircusResources(files,profile);TextCodec codec=GameText.detect(files,profile);String id=profile.id;
            check(NativeProgress.supports(id),"MES independently identifies a supported native layout: "+id);
            if(source==null){List<String> sources=NativeProgress.sourcePaths(files,id);if(sources.size()!=1)throw new IOException("Specify source when more than one exists: "+sources);source=sources.get(0);}
            List<String> candidates=NativeProgress.sourcePaths(files,id);check(candidates.contains(source),"selected source is a known game-relative path");if(candidates.size()>1){boolean ambiguous=false;try{NativeSaveImport.prepare(data,codec,output.resolve("ambiguous"));}catch(IOException e){ambiguous=true;}check(ambiguous&&!Files.exists(output.resolve("ambiguous")),"multiple native copies require explicit source selection without writes");}
            byte[] original=files.read(source,NativeProgress.fileSize(id));String hash=Binary.sha256(original);Map<String,String> slots=new TreeMap<>();
            for(String path:files.paths())if(NativeProgress.storySlot(id,source,path))slots.put(path,Binary.sha256(files.read(path,1024*1024)));
            Path fresh=output.resolve("fresh");NativeSaveImport.Preview preview=NativeSaveImport.prepare(data,codec,fresh,source);
            check(preview.storySlots==slots.size(),"counts original story slots beside selected source without converting them");
            check(preview.additions.total>0&&!Files.exists(preview.directory.resolve("progress.dat")),"read-only preview has additions and makes no progress file");
            NativeSaveImport.Result added=NativeSaveImport.apply(preview);check(added.added.total==preview.additions.total&&added.backup==null,"first import matches preview");
            check(preview.progress.preview(NativeSaveImport.readTarget(preview)).total==0,"all native unlocks survive private progress reload");
            byte[] persisted=Files.readAllBytes(preview.directory.resolve("progress.dat"));NativeSaveImport.Result repeat=NativeSaveImport.apply(NativeSaveImport.prepare(data,codec,fresh,source));
            check(repeat.added.total==0&&repeat.backup==null&&Arrays.equals(persisted,Files.readAllBytes(preview.directory.resolve("progress.dat"))),"repeat import makes no write or backup");

            NativeSaveImport.Preview merge=NativeSaveImport.prepare(data,codec,output.resolve("merge"),source);NativeProgress.Target phone=NativeSaveImport.readTarget(merge);
            phone.write(99,7,37);phone.write(100,49,27);if(phone.legacy!=null)phone.legacy.readCheckpoints.add("phone-read-history");if(phone.legacy instanceof PcHost)((PcHost)phone.legacy).givenName="名";if(phone.dd!=null)phone.dd.musicFavorites[6]=1;
            byte[] before=phone.save();DesktopEngine.atomic(merge.directory.resolve("progress.dat"),before);
            NativeProgress.Target expected=new NativeProgress.Target(data,codec);expected.load(before);merge.progress.apply(expected);byte[] expectedBytes=expected.save();
            byte[] slotBytes={10,20,30,40};Path phoneSlot=merge.directory.resolve("slot-250.sav");DesktopEngine.atomic(phoneSlot,slotBytes);
            NativeSaveImport.Result merged=NativeSaveImport.apply(merge);check(merged.backup!=null&&Arrays.equals(before,Files.readAllBytes(merged.backup)),"backup is exact original phone progress");
            check(Arrays.equals(expectedBytes,Files.readAllBytes(merge.directory.resolve("progress.dat"))),"persisted merge equals reviewed flag merge, retaining every other phone field");
            check(Arrays.equals(slotBytes,Files.readAllBytes(phoneSlot)),"existing phone story slot remains byte-identical");
            NativeProgress.Target loaded=NativeSaveImport.readTarget(merge);check(loaded.read(99,7)==37&&loaded.read(100,49)==27,"phone audio and non-progress globals preserved");

            NativeSaveImport.Preview corrupt=NativeSaveImport.prepare(data,codec,output.resolve("corrupt"),source);Path corruptPath=corrupt.directory.resolve("progress.dat");DesktopEngine.atomic(corruptPath,slotBytes);boolean rejected=false;
            try{NativeSaveImport.apply(corrupt);}catch(IOException e){rejected=true;}check(rejected&&Arrays.equals(slotBytes,Files.readAllBytes(corruptPath)),"corrupt phone progress is not overwritten");
            final String selected=source;final byte[][] mutable={original.clone()};GameResources.Storage mock=new GameResources.Storage(){public Set<String> paths(){return files.paths();}public byte[] read(String p,int limit)throws IOException{return p.equalsIgnoreCase(selected)?mutable[0].clone():files.read(p,limit);}public Archive.Source source(String p)throws IOException{return files.source(p);}};
            NativeSaveImport.Preview changed=NativeSaveImport.prepare(new CircusResources(new GameResources(mock),profile),codec,output.resolve("changed"),source);mutable[0][0]^=1;rejected=false;
            try{NativeSaveImport.apply(changed);}catch(IOException e){rejected=true;}check(rejected&&!Files.exists(changed.directory.resolve("progress.dat")),"changed original file after preview is rejected");
            NativeSaveImport.Preview legacy=NativeSaveImport.prepare(data,codec,output.resolve("legacy"),source);legacy.legacyProgress=output.resolve("old-progress.dm");DesktopEngine.atomic(legacy.legacyProgress.resolveSibling("old-progress.dm.bak"),before);rejected=false;
            try{NativeSaveImport.apply(legacy);}catch(IOException e){rejected=true;}check(rejected&&!Files.exists(legacy.directory.resolve("progress.dat")),"unmigrated legacy backup blocks import without writing");

            System.setProperty("dc.saveRoot",fresh.toString());DesktopEngine engine=id.equals("dc3dd-a5a8")?new DesktopDd(data,codec,false):new DesktopLegacy(data,codec,false);
            check(preview.directory.equals(engine.saveDirectory)&&preview.progress.preview(id,variables(engine)).total==0,"normal runtime restart consumes imported progress namespace");
            byte[] early=id.equals("dc3dd-a5a8")?new MesVm(data.scripts(),new Dc3Host(data,codec,true),"START").snapshot():new Dc2Session(data,codec).vm.snapshot();engine.restore(early);
            check(preview.progress.preview(id,variables(engine)).total==0,"loading pre-import phone snapshot retains imported unlocks");
            title(engine);check(preview.progress.preview(id,variables(engine)).total==0,"original START and title scripts retain imported flags");
            log.append("TITLE ").append(engine.scriptName()).append(' ').append(actions(engine)).append('\n');
            if(actions(engine).contains("extra")){engine.choose("extra");menu(engine);}
            log.append("EXTRA ").append(engine.scriptName()).append(' ').append(actions(engine)).append('\n');byte[] extra=engine.snapshot();
            String cg=first(actions(engine),"cgmode","cg","cg_mode");if(cg==null)throw new IOException("Original CG menu action missing: "+actions(engine));engine.choose(cg);menu(engine);
            check(unlocked(engine,"cg"),"original CG script exposes imported unlocked entries");log.append("CG ").append(engine.scriptName()).append(' ').append(actions(engine)).append('\n');
            engine.restore(extra);String scene=first(actions(engine),"scemode","scene","sce_mode","scenario");
            if(scene!=null){engine.choose(scene);menu(engine);check(unlocked(engine,id.equals("dcpc-2063")?"sc":"sce"),"original scene script exposes imported unlocked entries");log.append("SCENE ").append(engine.scriptName()).append(' ').append(actions(engine)).append('\n');}
            else throw new IOException("Original scene menu action missing: "+actions(engine));
            check(Binary.sha256(files.read(source,NativeProgress.fileSize(id))).equals(hash),"original system.dat stays byte-identical");
            for(Map.Entry<String,String> s:slots.entrySet())check(s.getValue().equals(Binary.sha256(files.read(s.getKey(),1024*1024))),"original story slot unchanged: "+s.getKey());
            log.append("COUNTS ").append(preview.progress.source).append("\nSOURCE ").append(source).append(' ').append(hash).append("\nNATIVE IMPORT MATRIX PASS CHECKS ").append(checks).append('\n');
            Files.write(output.resolve("result.txt"),log.toString().getBytes("UTF-8"));result.putString("stream",log.toString());finish(-1,result);
        }catch(Throwable e){log.append("FAIL ").append(e).append('\n');result.putString("stream",log.toString());try{if(output!=null)Files.write(output.resolve("result.txt"),log.toString().getBytes("UTF-8"));}catch(Exception ignored){}finish(0,result);}
        finally{if(old==null)System.clearProperty("dc.saveRoot");else System.setProperty("dc.saveRoot",old);}
    }
    private static Variables.Special variables(DesktopEngine e){return e instanceof DesktopDd?((DesktopDd)e).host.state:((DesktopLegacy)e).host();}
    private static List<String> actions(DesktopEngine e){List<String> r=new ArrayList<>();for(DesktopEngine.Control c:e.controls())r.add(c.action);return r;}
    private static String first(List<String> list,String... choices){for(String c:choices)if(list.contains(c))return c;return null;}
    private static void step(DesktopEngine e)throws Exception{for(int ch=0;ch<6;ch++)e.audioCompleted(ch,e.audioGeneration(ch));e.mediaCompleted(e.ticket());e.tick(1000);}
    private static void menu(DesktopEngine e)throws Exception{for(int n=0;n<2000;n++){step(e);if(e.menuWaiting())return;if(e instanceof DesktopLegacy&&((DesktopLegacy)e).host() instanceof PcHost&&((PcHost)((DesktopLegacy)e).host()).hasTimedInput())e.click(e.width/2,e.height/2,false);else{e.vm().signalHost(e.ticket(),MesVm.HostSignal.TEXT_COMPLETE);e.vm().signalHost(e.ticket(),MesVm.HostSignal.ADVANCE);}}throw new IOException("Menu budget "+e.scriptName()+"@"+Integer.toHexString(e.scriptOffset())+" "+e.vm().status()+" "+actions(e));}
    private static void title(DesktopEngine e)throws Exception{for(int n=0;n<20;n++){menu(e);List<String>a=actions(e);if(a.contains("start")||a.contains("sce_start"))return;String c=first(a,"03","window");if(c!=null)e.choose(c);else e.click(e.width/2,e.height/2,false);}throw new IOException("Title missing "+actions(e));}
    private static boolean unlocked(DesktopEngine e,String prefix){for(String a:actions(e))if(a.matches(prefix+"[0-9]+"))return true;ScriptList list=e instanceof DesktopDd?((DesktopDd)e).host.list:((DesktopLegacy)e).host() instanceof PcHost?((PcHost)((DesktopLegacy)e).host()).list:null;if(list!=null)for(ScriptList.Item i:list.items)if(i.action.matches(prefix+"[0-9]+"))return true;return false;}
}
