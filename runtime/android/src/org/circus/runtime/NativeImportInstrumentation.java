package org.circus.runtime;

import android.app.Instrumentation;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import org.circus.core.*;
import org.circus.graphics.BufferedImage;

/** Real SAF source and shipping import service, isolated from all user save roots. */
public final class NativeImportInstrumentation extends Instrumentation {
    private String tree;private Path output;private int checks;
    private final StringBuilder log=new StringBuilder();
    @Override public void onCreate(Bundle args){super.onCreate(args);tree=args.getString("tree");start();}
    private void check(boolean ok,String text)throws IOException{if(!ok)throw new IOException(text);checks++;log.append("PASS ").append(text).append('\n');}
    @Override public void onStart(){Bundle result=new Bundle();String oldRoot=System.getProperty("dc.saveRoot");
        try{
            output=getTargetContext().getFilesDir().toPath().resolve("native-import-audit").resolve("run-"+System.currentTimeMillis());Files.createDirectories(output);
            Uri uri=Uri.parse(tree);
            DocumentTree ordinary=new DocumentTree(getTargetContext().getContentResolver(),uri);
            check(ordinary.paths().stream().noneMatch(p->p.toLowerCase(Locale.ROOT).startsWith("advdata/savedata/")),"ordinary game indexing excludes original SaveData");
            GameResources files=new GameResources(new DocumentTree(getTargetContext().getContentResolver(),uri,true));
            Profile profile=GameVersion.detect(files).profile();check(profile.id.equals("dc2pc-5769"),"independently identified DC2PC source profile");
            CircusResources data=new CircusResources(files,profile);TextCodec codec=GameText.detect(files,profile);
            byte[] original=files.read(NativeSaveImport.SOURCE,3312);String sourceHash=Binary.sha256(original);
            Map<String,String> sourceSlots=new TreeMap<>();for(String p:files.paths())if(p.matches("advdata/savedata/dc2pc[0-9]+\\.dat"))sourceSlots.put(p,Binary.sha256(files.read(p,1024*1024)));
            check(sourceSlots.size()>0,"explicit SAF import indexes original system.dat and story slots");

            Path freshRoot=output.resolve("fresh");NativeSaveImport.Preview preview=NativeSaveImport.prepare(data,codec,freshRoot);
            check(preview.storySlots==sourceSlots.size(),"preview counts native story slots without claiming conversion");
            check(preview.additions.total>0&&preview.additions.total==preview.progress.source.total,"fresh preview reports all original unlocks");
            check(!Files.exists(preview.directory.resolve("progress.dat")),"preview makes no private progress write");
            NativeSaveImport.Result imported=NativeSaveImport.apply(preview);
            check(imported.added.total==preview.additions.total&&imported.backup==null,"first import writes all progress without a fictitious backup");
            check(preview.progress.preview(NativeSaveImport.readHost(preview)).total==0,"reopened private progress contains every imported flag");
            byte[] fresh=Files.readAllBytes(preview.directory.resolve("progress.dat"));
            NativeSaveImport.Result duplicate=NativeSaveImport.apply(NativeSaveImport.prepare(data,codec,freshRoot));
            check(duplicate.added.total==0&&duplicate.backup==null&&Arrays.equals(fresh,Files.readAllBytes(preview.directory.resolve("progress.dat"))),"repeat import is byte-identical and creates no backup");

            Path mergeRoot=output.resolve("merge");NativeSaveImport.Preview merge=NativeSaveImport.prepare(data,codec,mergeRoot);
            PcHost phone=NativeSaveImport.readHost(merge);phone.write(97,1599,1);phone.write(98,1599,1);phone.write(100,1,2);phone.write(100,56,7);phone.write(100,60,1);phone.write(99,5,3);phone.write(95,2,0);phone.givenName="手机";phone.readCheckpoints.add("native-import-existing-checkpoint");
            ByteArrayOutputStream bytes=new ByteArrayOutputStream();phone.savePersistent(new DataOutputStream(bytes));byte[] previous=bytes.toByteArray();DesktopEngine.atomic(merge.directory.resolve("progress.dat"),previous);
            DesktopSave slot=new DesktopSave();slot.state=new Dc2Session(data,codec).vm.snapshot();slot.caption="native-import-slot-preservation";byte[] slotBytes=slot.encode();Path mobileSlot=merge.directory.resolve("slot-250.sav");DesktopEngine.atomic(mobileSlot,slotBytes);
            merge=NativeSaveImport.prepare(data,codec,mergeRoot);NativeSaveImport.Result merged=NativeSaveImport.apply(merge);
            check(merged.added.total>0&&merged.backup!=null&&Arrays.equals(previous,Files.readAllBytes(merged.backup)),"backup exactly preserves pre-import private progress bytes");
            PcHost after=NativeSaveImport.readHost(merge);
            check(after.read(97,1599)==1&&after.read(98,1599)==1&&after.read(100,1)==2,"merge preserves existing phone unlocks and nonzero completion value");
            check(after.read(99,5)==3&&after.read(100,56)==7&&after.read(100,60)==1&&after.read(95,2)==0&&after.givenName.equals("手机")&&after.readCheckpoints.contains("native-import-existing-checkpoint"),"merge preserves phone settings, player name, voice preferences and read checkpoints");
            check(Arrays.equals(slotBytes,Files.readAllBytes(mobileSlot)),"import leaves an existing mobile story slot byte-identical");
            check(merge.progress.preview(after).total==0,"merged private progress reloads every original unlock");

            Path damagedRoot=output.resolve("damaged");NativeSaveImport.Preview damaged=NativeSaveImport.prepare(data,codec,damagedRoot);byte[] bad={0x55,0x66,0x77};Path badFile=damaged.directory.resolve("progress.dat");DesktopEngine.atomic(badFile,bad);
            boolean rejected=false;try{NativeSaveImport.apply(damaged);}catch(IOException expected){rejected=true;}
            check(rejected&&Arrays.equals(bad,Files.readAllBytes(badFile)),"invalid private progress is rejected without replacing its bytes");

            // An in-memory source changes after preview; the actual user's files stay read-only.
            final byte[][] mutable={original.clone()};GameResources.Storage mock=new GameResources.Storage(){public Set<String> paths(){return files.paths();}public byte[] read(String p,int limit)throws IOException{return p.equalsIgnoreCase(NativeSaveImport.SOURCE)?mutable[0].clone():files.read(p,limit);}public Archive.Source source(String p)throws IOException{return files.source(p);}};
            CircusResources changing=new CircusResources(new GameResources(mock),profile);NativeSaveImport.Preview changed=NativeSaveImport.prepare(changing,codec,output.resolve("changed"));mutable[0][0]^=1;
            rejected=false;try{NativeSaveImport.apply(changed);}catch(IOException expected){rejected=true;}
            check(rejected&&!Files.exists(changed.directory.resolve("progress.dat")),"source change after preview is rejected before writing private progress");

            NativeSaveImport.Preview legacy=NativeSaveImport.prepare(data,codec,output.resolve("legacy-migration"));
            legacy.legacyProgress=output.resolve("old-progress.dm");DesktopEngine.atomic(legacy.legacyProgress,previous);
            rejected=false;try{NativeSaveImport.apply(legacy);}catch(IOException expected){rejected=true;}
            check(rejected&&!Files.exists(legacy.directory.resolve("progress.dat"))&&Arrays.equals(previous,Files.readAllBytes(legacy.legacyProgress)),"unmigrated old phone progress blocks import without changing either file");
            DesktopEngine.atomic(legacy.directory.resolve("progress.dat"),previous);
            check(NativeSaveImport.apply(legacy).added.total>0,"import is allowed once old phone progress has been migrated");

            System.setProperty("dc.saveRoot",freshRoot.toString());DesktopLegacy engine=new DesktopLegacy(data,codec,false);
            check(engine.saveDirectory.equals(preview.directory)&&preview.progress.preview((PcHost)engine.host()).total==0,"normal runtime restart reads the same imported private progress");
            engine.restore(new Dc2Session(data,codec).vm.snapshot());
            check(preview.progress.preview((PcHost)engine.host()).total==0,"restoring a phone story snapshot from before import keeps imported unlocks");
            title(engine);capture(engine,"01-imported-title");engine.choose("extra");menu(engine);byte[] title=engine.snapshot();
            check(actions(engine).contains("cg")&&actions(engine).contains("scene"),"original title EXTRA exposes CG and scene menus");
            engine.choose("cg");menu(engine);capture(engine,"02-imported-cg");
            String cg=null;for(String a:actions(engine))if(a.matches("cg[0-9]+")){cg=a;break;}
            check(cg!=null,"original CG script exposes an unlocked thumbnail action");String before=engine.host().scene.slots[1];engine.choose(cg);gallery(engine);capture(engine,"03-imported-cg-full");
            check(!before.equals(engine.host().scene.slots[1])&&!engine.host().scene.slots[1].isEmpty(),"unlocked original CG action displays its full-size image");
            engine.restore(title);engine.choose("scene");menu(engine);capture(engine,"04-imported-scene-list");
            PcHost scene=(PcHost)engine.host();String selected=null;for(int n=0;n<scene.grid.size();n++){ScriptList.Item item=scene.list.at(scene.read(99,246),n,scene.grid.size());if(item!=null&&!item.action.isEmpty()){selected="@slot:"+n;break;}}
            if(selected==null)for(String a:actions(engine))if(a.matches("sce[0-9]+")){selected=a;break;}
            check(selected!=null,"original scene list contains a selectable unlocked scene");String listScript=engine.scriptName();engine.choose(selected);story(engine);capture(engine,"05-imported-scene-story");
            check(!listScript.equals(engine.scriptName())&&!engine.body().isEmpty(),"unlocked original scene starts its original dialogue");
            check(Binary.sha256(files.read(NativeSaveImport.SOURCE,3312)).equals(sourceHash),"original system.dat remains byte-identical");
            for(Map.Entry<String,String> entry:sourceSlots.entrySet())check(entry.getValue().equals(Binary.sha256(files.read(entry.getKey(),1024*1024))),"original story slot unchanged: "+entry.getKey());
            log.append("SOURCE ").append(sourceHash).append("\nCOUNTS ").append(preview.progress.source).append("\nANDROID NATIVE IMPORT PASS CHECKS ").append(checks).append('\n');result.putString("stream",log.toString());finish(-1,result);
        }catch(Throwable e){log.append("FAIL ").append(e).append('\n');result.putString("stream",log.toString());finish(0,result);
        }finally{try{if(output!=null)Files.write(output.resolve("result.txt"),log.toString().getBytes("UTF-8"));}catch(Exception ignored){}if(oldRoot==null)System.clearProperty("dc.saveRoot");else System.setProperty("dc.saveRoot",oldRoot);}
    }
    private static List<String> actions(DesktopLegacy e){List<String> result=new ArrayList<>();for(DesktopEngine.Control c:e.controls())result.add(c.action);return result;}
    private static void step(DesktopLegacy e)throws Exception{for(int ch=0;ch<6;ch++)e.audioCompleted(ch,e.audioGeneration(ch));e.mediaCompleted(e.ticket());e.tick(1000);}
    private static void menu(DesktopLegacy e)throws Exception{for(int n=0;n<1000;n++){step(e);if(e.menuWaiting())return;if(((PcHost)e.host()).hasTimedInput())e.click(400,200,false);}throw new IOException("Original menu did not settle: "+e.scriptName()+" "+actions(e));}
    private static void gallery(DesktopLegacy e)throws Exception{for(int n=0;n<1000;n++){step(e);if(e.menuWaiting()||((PcHost)e.host()).hasTimedInput())return;}throw new IOException("CG did not reach its display wait");}
    private static void title(DesktopLegacy e)throws Exception{for(int n=0;n<20;n++){menu(e);List<String>a=actions(e);if(a.contains("start")&&a.contains("extra"))return;if(a.contains("03"))e.choose("03");else if(a.contains("window"))e.choose("window");else e.click(400,200,false);}throw new IOException("Original title did not appear");}
    private static void story(DesktopLegacy e)throws Exception{for(int n=0;n<2000;n++){step(e);if(e.host().presentationWaiting){e.vm().signalHost(e.ticket(),MesVm.HostSignal.TEXT_COMPLETE);e.tick(1000);}if(e.textWaiting()&&!e.body().isEmpty())return;if(e.menuWaiting())throw new IOException("Unexpected scene menu "+e.scriptName()+" "+actions(e));}throw new IOException("Scene did not reach dialogue");}
    private void capture(DesktopLegacy e,String name)throws IOException{BufferedImage image=e.render();try(OutputStream out=Files.newOutputStream(output.resolve(name+".png"))){if(!image.bitmap.compress(Bitmap.CompressFormat.PNG,100,out))throw new IOException("PNG write failed");}finally{image.flush();}log.append("FRAME ").append(name).append(' ').append(e.scriptName()).append('@').append(Integer.toHexString(e.scriptOffset())).append(" ACTIONS ").append(actions(e)).append('\n');}
}
