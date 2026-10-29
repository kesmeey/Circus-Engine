package org.circus.runtime;

import android.app.Instrumentation;
import android.net.Uri;
import android.os.Bundle;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import org.circus.core.*;
import org.circus.graphics.BufferedImage;

/** Shipping import service against original read-only save sources, in a fresh private save root. */
public final class UnityImportInstrumentation extends Instrumentation {
    private String tree,pack,script;private int checks;private Path output;private final StringBuilder log=new StringBuilder();
    @Override public void onCreate(Bundle args){super.onCreate(args);tree=args.getString("tree");pack=args.getString("pack","dc3r-audit.pack");script=args.getString("script0","dc3r-import-script0");start();}
    private void check(boolean ok,String text)throws IOException{if(!ok)throw new IOException(text);checks++;log.append("PASS ").append(text).append('\n');}
    private void settle(DesktopUnity engine)throws Exception{for(int n=0;n<300;n++){engine.tick(1000);if(!engine.movie().isEmpty())engine.mediaCompleted(engine.ticket());for(int ch=0;ch<6;ch++)if(engine.audioActive(ch)&&!engine.audioLoop(ch))engine.audioCompleted(ch,engine.audioGeneration(ch));if(engine.menuWaiting()||engine.textWaiting())return;}throw new IOException("Import audit did not settle");}
    private void title(DesktopUnity engine)throws Exception{for(int n=0;n<20;n++){settle(engine);for(DesktopEngine.Control c:engine.controls())if(c.action.equals("sce_start"))return;boolean selected=false;for(String action:new String[]{"window","01"})for(DesktopEngine.Control c:engine.controls())if(c.action.equals(action)){engine.choose(action);selected=true;break;}if(!selected)throw new IOException("Unexpected startup menu");}throw new IOException("Title not reached");}
    private static byte[] read(Path p)throws IOException{return Files.readAllBytes(p);}
    private static byte[] persistent(Dc3rHost host)throws IOException{ByteArrayOutputStream bytes=new ByteArrayOutputStream();host.savePersistent(new DataOutputStream(bytes));return bytes.toByteArray();}
    private static Archive.Source memory(byte[] bytes){return new Archive.Source(){public long size(){return bytes.length;}public byte[] read(long at,int count)throws IOException{Binary.range(at,count,bytes.length);return Arrays.copyOfRange(bytes,(int)at,(int)at+count);}};}
    @Override public void onStart(){Bundle result=new Bundle();String oldRoot=System.getProperty("dc.saveRoot");try{
        Path base=getTargetContext().getFilesDir().toPath();output=Files.createTempDirectory(base,"dc3r-import-audit-");
        GameResources.Storage ranges=RangeStorage.replay(base.resolve(pack)),supplement=RangeStorage.replay(base.resolve("dc3r-import-supplement.pack"));Path scriptFile=base.resolve(script);byte[] scriptBytes=read(scriptFile);
        GameResources.Storage storage=new GameResources.Storage(){public Set<String> paths(){return ranges.paths();}public byte[] read(String p,int limit)throws IOException{if(p.toLowerCase(Locale.ROOT).endsWith("/script0"))return scriptBytes.clone();try{return ranges.read(p,limit);}catch(IOException absent){return supplement.read(p,limit);}}public Archive.Source source(String p)throws IOException{if(p.toLowerCase(Locale.ROOT).endsWith("/script0"))return memory(scriptBytes);Archive.Source first=ranges.source(p),second=supplement.source(p);return new Archive.Source(){public long size()throws IOException{try{return first.size();}catch(IOException absent){return second.size();}}public byte[] read(long at,int count)throws IOException{try{return first.read(at,count);}catch(IOException absent){return second.read(at,count);}}};}};
        UnityCircusResources data=new UnityCircusResources(new GameResources(storage));GameResources sources=new GameResources(new DocumentTree(getTargetContext().getContentResolver(),Uri.parse(tree),true),"@none");
        UnitySaveImport.Request request=new UnitySaveImport.Request(data,sources,output.resolve("fresh"),output.resolve("cache"));String folder=null;for(String name:request.folders)if(sources.paths().contains((name.isEmpty()?"":name+"/")+"save101.dat")){folder=name;break;}if(folder==null)throw new IOException("Copy original save101.dat into an audit source folder");
        Map<String,String> hashes=new TreeMap<>();for(String path:sources.paths())if(path.startsWith(folder+"/")&&(path.endsWith("global.dat")||path.endsWith("save101.dat")))hashes.put(path,Binary.sha256(sources.read(path,8*1024*1024)));
        Path imported;
        try(UnitySaveImport.Preview preview=request.prepare(folder)){
            log.append("PREVIEW ").append(preview.description()).append('\n');check(preview.slots.size()==1&&preview.slots.get(0).number==101&&preview.rejected.isEmpty(),"real native automatic save converts successfully");
            check(!Files.exists(preview.directory),"preview creates no phone progress or story files");
            UnitySaveImport.Result added=UnitySaveImport.apply(preview,false);imported=preview.directory;
            check(added.slots==1&&added.backup==null&&Files.exists(imported.resolve("slot-101.sav")),"native automatic save imported into runtime slot 101");
        }
        byte[] first=read(imported.resolve("slot-101.sav"));
        try(UnitySaveImport.Preview preview=request.prepare(folder)){check(preview.conflicts==1,"existing same-number phone slot is listed in preview");UnitySaveImport.Result repeated=UnitySaveImport.apply(preview,false);check(repeated.slots==0&&repeated.skipped==1&&repeated.backup==null&&Arrays.equals(first,read(imported.resolve("slot-101.sav"))),"default re-import preserves existing slot byte-for-byte");}
        DesktopSave custom=DesktopSave.decode(first);custom.caption="Phone existing save";byte[] phoneSlot=custom.encode();DesktopEngine.atomic(imported.resolve("slot-101.sav"),phoneSlot);
        try(UnitySaveImport.Preview preview=request.prepare(folder)){UnitySaveImport.Result replaced=UnitySaveImport.apply(preview,true);check(replaced.slots==1&&replaced.backup!=null&&Arrays.equals(phoneSlot,read(replaced.backup.resolve("slot-101.sav"))),"explicit replacement retains exact phone slot backup");}
        System.setProperty("dc.saveRoot",request.root.toString());DesktopUnity engine=new DesktopUnity(data,false);title(engine);engine.choose("load");settle(engine);Dc3rHost.Button auto=null;for(Dc3rHost.Button button:engine.host.buttons())if(button.action.equals("selsave")&&button.flagValue==-2){auto=button;break;}
        check(auto!=null,"normal restart exposes imported automatic slot in original LOAD menu");engine.click((auto.left+auto.right)/2,(auto.top+auto.bottom)/2,false);settle(engine);if(engine.menuWaiting()){engine.choose(":y");settle(engine);}
        check(engine.scriptName().equals("dc3_1_0222_e1_pro")&&engine.textWaiting(),"original LOAD resumes imported script position and reaches dialogue");
        BufferedImage image=engine.render();try(OutputStream out=Files.newOutputStream(output.resolve("imported-first-dialogue.png"))){org.circus.graphics.ImageIO.write(image,"png",out);}finally{image.flush();}
        int before=engine.scriptOffset();engine.click(32,32,false);settle(engine);check(engine.scriptOffset()!=before&&engine.textWaiting(),"imported story continues after tapping");
        Path savedAgain=imported.resolve("audit-roundtrip.sav");engine.save(savedAgain);engine.click(32,32,false);settle(engine);engine.restore(savedAgain);check(engine.textWaiting(),"converted story can be saved and reloaded in runtime format");
        final String originalSlot=(folder.isEmpty()?"":folder+"/")+"save101.dat";final byte[][] changed={sources.read(originalSlot,8*1024*1024)};
        GameResources.Storage mutable=new GameResources.Storage(){public Set<String> paths(){return sources.paths();}public byte[] read(String p,int limit)throws IOException{return p.equals(originalSlot)?changed[0].clone():sources.read(p,limit);}public Archive.Source source(String p)throws IOException{return p.equals(originalSlot)?memory(changed[0]):sources.source(p);}};
        UnitySaveImport.Request changedRequest=new UnitySaveImport.Request(data,new GameResources(mutable),output.resolve("changed"),output.resolve("cache"));
        try(UnitySaveImport.Preview preview=changedRequest.prepare(folder)){changed[0][0]^=1;boolean rejected=false;try{UnitySaveImport.apply(preview,false);}catch(IOException expected){rejected=true;}check(rejected&&!Files.exists(preview.directory),"changed source after preview is rejected without destination writes");}
        changed[0]=new byte[]{0,1,2};try(UnitySaveImport.Preview preview=changedRequest.prepare(folder)){check(preview.slots.isEmpty()&&preview.rejected.size()==1&&!Files.exists(preview.directory),"malformed story file is listed and does not create a fake slot");}
        // Self-authored positive progress fixture exercises the same service and real catalogues.
        final byte[][] global={Dc3rNativeProgressTests.fixture()};final byte[] originalGlobal=global[0].clone();
        GameResources.Storage progressStorage=new GameResources.Storage(){public Set<String> paths(){return Collections.singleton("global.dat");}public byte[] read(String p,int limit)throws IOException{if(!p.equals("global.dat"))throw new FileNotFoundException(p);return global[0].clone();}public Archive.Source source(String p)throws IOException{return memory(read(p,8*1024*1024));}};
        UnitySaveImport.Request progressRequest=new UnitySaveImport.Request(data,new GameResources(progressStorage,"@none"),output.resolve("progress"),output.resolve("cache"));
        Path progressFile=progressRequest.root.resolve(UnityCircusResources.ID+"-"+data.identity()).resolve("progress.dat");
        Dc3rHost preferences=new Dc3rHost();preferences.writeFlag((99<<16)+7,3);preferences.writeFlag((100<<16)+60,0);preferences.writeFlag((100<<16)+200,1);DesktopEngine.atomic(progressFile,persistent(preferences));byte[] phoneProgress=read(progressFile);
        try(UnitySaveImport.Preview preview=progressRequest.prepare("")){check(preview.additions.total==7&&preview.slots.isEmpty(),"real catalogues validate seven synthetic native progress additions");check(Arrays.equals(phoneProgress,read(progressFile)),"progress preview leaves phone file unchanged");UnitySaveImport.Result merged=UnitySaveImport.apply(preview,false);Dc3rHost loaded=UnitySaveImport.target(preview);check(merged.progress.total==7&&merged.backup!=null&&Arrays.equals(phoneProgress,read(merged.backup.resolve("progress.dat"))),"progress merge backs up exact previous phone file");check(loaded.readFlag((99<<16)+7)==3&&loaded.readFlag((100<<16)+60)==0&&loaded.readFlag((100<<16)+200)==1,"native merge preserves phone preferences and existing completion");check(loaded.readFlag(100<<16)==4&&loaded.readFlag(97<<16)==1&&loaded.readFlag((97<<16)+1039)==1&&loaded.readFlag(98<<16)==1&&loaded.readFlag((98<<16)+589)==1,"native completion and both gallery banks survive disk reload");}
        byte[] mergedProgress=read(progressFile);try(UnitySaveImport.Preview preview=progressRequest.prepare("")){check(!preview.hasChanges()&&preview.additions.total==0,"repeated native progress import has no new additions");UnitySaveImport.Result repeated=UnitySaveImport.apply(preview,false);check(repeated.backup==null&&Arrays.equals(mergedProgress,read(progressFile)),"repeated progress import performs no writes");global[0][0]^=1;boolean rejected=false;try{UnitySaveImport.apply(preview,false);}catch(IOException expected){rejected=true;}check(rejected&&Arrays.equals(mergedProgress,read(progressFile)),"changed native global source cannot overwrite phone progress");}global[0]=originalGlobal;
        check(Arrays.equals(global[0],Dc3rNativeProgressTests.fixture()),"self-authored global source remains unchanged by conversion");
        for(Map.Entry<String,String> entry:hashes.entrySet())check(entry.getValue().equals(Binary.sha256(sources.read(entry.getKey(),8*1024*1024))),"original source unchanged: "+entry.getKey());
        log.append("CHECKS ").append(checks).append("\nOUTPUT ").append(output).append('\n');Files.write(output.resolve("result.txt"),log.toString().getBytes("UTF-8"));result.putString("stream",log.toString());finish(-1,result);
    }catch(Throwable ex){log.append("FAIL ").append(ex).append('\n');try{if(output!=null)Files.write(output.resolve("result.txt"),log.toString().getBytes("UTF-8"));}catch(Exception ignored){}result.putString("stream",log.toString());finish(0,result);}finally{if(oldRoot==null)System.clearProperty("dc.saveRoot");else System.setProperty("dc.saveRoot",oldRoot);}}
}
