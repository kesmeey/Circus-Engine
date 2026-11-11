package org.circus.runtime;

import android.app.Instrumentation;
import android.content.Intent;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.os.ParcelFileDescriptor;
import android.provider.DocumentsContract;
import android.provider.DocumentsProvider;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import org.circus.core.*;
import org.circus.graphics.BufferedImage;
import org.circus.graphics.ImageIO;

/** PH selected-document import through the shipping ContentResolver/service.
 * Synthetic native-format maps are explicit fixtures, not a claim that a user
 * save is fully cleared. All writes belong to isolated application directories. */
public final class Dc4ProgressImportInstrumentation extends Instrumentation {
    private static final String ID="dc4ph-abb6",AUTHORITY="org.circus.runtime.dc4progress.audit";
    private static final int SIZE=21368,GLOBALS=0xd8,MUSIC=0x180,CG=0x310,SCENES=0x7c0;
    private Bundle arguments;private int checks;private Path output;
    private final StringBuilder log=new StringBuilder();
    private final Map<String,Object> report=new LinkedHashMap<>();
    private interface Attempt{void run()throws Exception;}
    private void check(boolean ok,String why)throws IOException{if(!ok)throw new IOException(why);checks++;log.append("PASS ").append(why).append('\n');}
    private void reject(Attempt action,String why)throws Exception{boolean rejected=false;try{action.run();}catch(IOException expected){rejected=true;log.append("REJECT ").append(expected.getMessage()).append('\n');}check(rejected,why);}

    /** A real read-only provider, deliberately returning reordered metadata.
     * It accepts no external filenames or writes. No original bytes are bundled. */
    public static final class Documents extends DocumentsProvider {
        private static final Map<String,byte[]> data=new ConcurrentHashMap<>();
        static Uri publish(String id,byte[] bytes){data.put(id,bytes.clone());return DocumentsContract.buildDocumentUri(AUTHORITY,id);}
        private static byte[] bytes(String id)throws FileNotFoundException{byte[] b=data.get(id);if(b==null)throw new FileNotFoundException("Unknown PH synthetic document");return b;}
        @Override public boolean onCreate(){return true;}
        @Override public Cursor queryRoots(String[] projection){return new MatrixCursor(new String[]{DocumentsContract.Root.COLUMN_ROOT_ID,DocumentsContract.Root.COLUMN_DOCUMENT_ID,DocumentsContract.Root.COLUMN_TITLE});}
        @Override public Cursor queryDocument(String id,String[] projection)throws FileNotFoundException{
            bytes(id);MatrixCursor c=new MatrixCursor(new String[]{DocumentsContract.Document.COLUMN_DOCUMENT_ID,DocumentsContract.Document.COLUMN_SIZE,DocumentsContract.Document.COLUMN_DISPLAY_NAME,DocumentsContract.Document.COLUMN_MIME_TYPE});
            c.addRow(new Object[]{id,null,"PH-"+id+".unusual","application/octet-stream"});return c;
        }
        @Override public Cursor queryChildDocuments(String id,String[] projection,String sort){return new MatrixCursor(new String[]{DocumentsContract.Document.COLUMN_DOCUMENT_ID});}
        @Override public ParcelFileDescriptor openDocument(String id,String mode,CancellationSignal signal)throws FileNotFoundException{
            if(!mode.equals("r")||!id.matches("[A-Za-z0-9_-]+"))throw new FileNotFoundException("Read-only PH fixture");byte[] b=bytes(id);
            File directory=new File(getContext().getCacheDir(),"dc4-progress-documents");if(!directory.isDirectory()&&!directory.mkdirs())throw new FileNotFoundException("Cannot create private fixture cache");File file=new File(directory,id+".bin");
            try{try(FileOutputStream out=new FileOutputStream(file)){out.write(b);}return ParcelFileDescriptor.open(file,ParcelFileDescriptor.MODE_READ_ONLY);}catch(IOException ex){throw new FileNotFoundException(ex.toString());}
        }
    }
    private SelectedSaveFile.Source selected(String id,byte[] bytes)throws IOException{return SelectedSaveFile.open(getTargetContext().getContentResolver(),Documents.publish(id,bytes));}
    private static final class Fixture implements GameResources.Storage {
        final Map<String,byte[]> files=new TreeMap<>();
        Fixture(){files.put("advdata/grp/names.dat",new byte[20]);files.put("advdata/mes/start.mes",new byte[]{0,0,0,0,(byte)0xb6,(byte)0xab,0,0,0,0});}
        public Set<String> paths(){return files.keySet();}
        private byte[] get(String p)throws IOException{byte[] b=files.get(p.toLowerCase(Locale.ROOT));if(b==null)throw new FileNotFoundException(p);return b;}
        public byte[] read(String p,int limit)throws IOException{byte[] b=get(p);if(b.length>limit)throw new IOException("Fixture limit");return b.clone();}
        public Archive.Source source(String p){return new Archive.Source(){public long size()throws IOException{return get(p).length;}public byte[] read(long at,int n)throws IOException{byte[] b=get(p);Binary.range(at,n,b.length);return Arrays.copyOfRange(b,(int)at,(int)at+n);}};}
        CircusResources resources()throws IOException{GameResources f=new GameResources(this,"@none");Profile p=GameVersion.detect(f).profile();if(!p.id.equals(ID))throw new IOException("Independent PH START identity");return new CircusResources(f,p);}
    }
    private static byte[] full(){
        byte[] b=new byte[SIZE];Arrays.fill(b,GLOBALS,GLOBALS+150,(byte)255);Arrays.fill(b,GLOBALS+100,GLOBALS+113,(byte)0);
        Arrays.fill(b,MUSIC,MUSIC+400,(byte)1);Arrays.fill(b,CG,CG+1200,(byte)1);Arrays.fill(b,SCENES,SCENES+650,(byte)1);
        b[SCENES+2]=2;b[SCENES+128]=(byte)128;b[SCENES+649]=(byte)255;return b;
    }
    private static int backups(Path p)throws IOException{int n=0;try(DirectoryStream<Path> files=Files.newDirectoryStream(p,"progress.before-native-*.dat")){for(Path ignored:files)n++;}return n;}
    private void flags(NativeProgress.Target target)throws Exception{
        boolean ok=true;for(int n=0;n<400;n++)ok&=target.read(96,n)==1;for(int n=0;n<1200;n++)ok&=target.read(97,n)==(n==7?2:1);for(int n=0;n<650;n++)ok&=target.read(98,n)==(n==8?3:1);
        check(ok,"All reviewed music/CG/scene flags and existing nonzero target values persist at every native boundary");
    }
    private void transactions()throws Exception{
        Fixture fixture=new Fixture();CircusResources data=fixture.resources();TextCodec codec=TextCodec.identity();byte[] bytes=full();SelectedSaveFile.Source source=selected("full",bytes);
        Intent picker=LauncherActivity.progressFilePicker();check(Intent.ACTION_OPEN_DOCUMENT.equals(picker.getAction())&&picker.hasCategory(Intent.CATEGORY_OPENABLE)&&"*/*".equals(picker.getType()),"Shipping picker accepts one openable document independently of filename extension");
        check((picker.getFlags()&Intent.FLAG_GRANT_READ_URI_PERMISSION)!=0&&(picker.getFlags()&Intent.FLAG_GRANT_WRITE_URI_PERMISSION)==0&&!picker.getBooleanExtra(Intent.EXTRA_ALLOW_MULTIPLE,false),"Picker requests read access to one document only");
        check(source.name().equals("PH-full.unusual")&&Arrays.equals(SelectedSaveFile.read(source,SIZE),bytes),"ContentResolver reads a real renamed document despite reordered metadata and unknown size");
        reject(()->SelectedSaveFile.open(getTargetContext().getContentResolver(),Uri.parse("file:///sdcard/untrusted.dat")),"Non-content selected source is rejected");
        reject(()->getTargetContext().getContentResolver().openFileDescriptor(Documents.publish("readonly",bytes),"rw"),"Fixture provider rejects a write request");
        NativeSaveImport.Preview cancelled=NativeSaveImport.prepareFile(data,codec,output.resolve("cancelled"),source);
        check(cancelled.progress.source.music==400&&cancelled.progress.source.cg==1200&&cancelled.progress.source.scenes==650,"Independent21368-byte full-map fixture parses400 music,1200 CG and650 scene flags");
        check(cancelled.progress.source.completion==0,"Unreviewed nonbinary source globals are excluded, while native unsigned scene2/128/255 normalize to seen");
        check(!Files.exists(output.resolve("cancelled"))&&cancelled.storySlots==0,"Read-only preparation creates no destination and scans no sibling story slots");
        check(cancelled.description().contains("D.C.4 PH")&&cancelled.description().contains("剧情"),"PH preview explains version-specific progress and story-slot boundary");
        Path root=output.resolve("phone");NativeSaveImport.Preview preview=NativeSaveImport.prepareFile(data,codec,root,source);NativeProgress.Target phone=NativeSaveImport.readTarget(preview);
        for(int n=0;n<150;n++)phone.write(100,n,(n*17+13)&255);phone.write(99,7,37);phone.write(99,180,0);phone.write(99,181,0);phone.write(99,182,0);phone.dd.togglePhMessageControls();phone.dd.voiceCount=50;
        for(int n=0;n<20;n++)phone.write(93,n,n&1);for(int n=0;n<51;n++){phone.write(94,n,n+30);phone.write(95,n,n&1);}for(int n=0;n<400;n++)phone.dd.musicFavorites[n]=(n*3+1)%7;
        phone.write(97,7,2);phone.write(98,8,3);byte[] before=phone.save();Path progress=preview.directory.resolve("progress.dat"),slot=preview.directory.resolve("slot-250.sav");DesktopEngine.atomic(progress,before);
        Dc4Host h=new Dc4Host(data,codec,true);MesVm vm=new MesVm(data.scripts(),h,"START");DesktopSave story=new DesktopSave();story.state=vm.snapshot();story.caption="Existing PH portable story";byte[] storyBytes=story.encode();DesktopEngine.atomic(slot,storyBytes);
        preview=NativeSaveImport.prepareFile(data,codec,root,source);check(preview.additions.cg==1199&&preview.additions.scenes==649,"Merge preview counts only missing gallery/scene flags");
        check(Arrays.equals(before,Files.readAllBytes(progress))&&Arrays.equals(storyBytes,Files.readAllBytes(slot)),"Preview leaves prior progress and real CVS2 narrative slot unchanged");
        NativeSaveImport.Result applied=NativeSaveImport.apply(preview);check(applied.backup!=null&&Arrays.equals(before,Files.readAllBytes(applied.backup)),"Shipping atomic merge backs up exact prior progress");
        NativeProgress.Target after=NativeSaveImport.readTarget(preview);byte[] merged=after.save();flags(after);
        check(merged.length==before.length&&Arrays.equals(Arrays.copyOfRange(before,0,154),Arrays.copyOfRange(merged,0,154))&&Arrays.equals(Arrays.copyOfRange(before,2404,before.length),Arrays.copyOfRange(merged,2404,merged.length)),"Every unimported global, preference, favorite and voice-setting byte is preserved");
        check(after.read(99,7)==37&&after.read(99,180)==0&&after.read(99,181)==0&&after.read(99,182)==0&&after.read(99,227)==0,"PH-only settings and control-bar state survive selected-file merge");
        check(Arrays.equals(storyBytes,Files.readAllBytes(slot)),"Existing portable narrative slot remains byte-identical");
        NativeSaveImport.Result repeated=NativeSaveImport.apply(NativeSaveImport.prepareFile(data,codec,root,selected("renamed",bytes)));
        check(repeated.added.total==0&&repeated.backup==null&&backups(preview.directory)==1&&Arrays.equals(merged,Files.readAllBytes(progress)),"Identical bytes under another filename are idempotent with no second backup or write");
        NativeSaveImport.apply(NativeSaveImport.prepareFile(data,codec,root,selected("empty",new byte[SIZE])));check(Arrays.equals(merged,Files.readAllBytes(progress)),"An empty native source never erases imported progress");
        for(int length:new int[]{SIZE-1,SIZE+1,21356,19744}){Path rejected=output.resolve("size-"+length);reject(()->NativeSaveImport.prepareFile(data,codec,rejected,selected("size-"+length,Arrays.copyOf(bytes,length))),"Wrong extent rejected through actual document read: "+length);check(!Files.exists(rejected),"Rejected source creates no destination: "+length);}
        for(int at:new int[]{MUSIC+399,CG+1199}){byte[] invalid=bytes.clone();invalid[at]=2;Path rejected=output.resolve("flag-"+at);reject(()->NativeSaveImport.prepareFile(data,codec,rejected,selected("flag-"+at,invalid)),"Nonbinary music/CG flag rejected at independently chosen final boundary "+at);check(!Files.exists(rejected),"Invalid flag causes no destination writes "+at);}
        byte[] clears=new byte[SIZE];Arrays.fill(clears,GLOBALS+100,GLOBALS+113,(byte)1);clears[GLOBALS+120]=(byte)255;clears[GLOBALS+121]=(byte)255;
        NativeSaveImport.Preview completion=NativeSaveImport.prepareFile(data,codec,output.resolve("completion"),selected("completion",clears));check(completion.progress.source.completion==13&&completion.additions.completion==13,"Exactly the thirteen native/MES-proven100..112 completion gates are imported");NativeSaveImport.apply(completion);NativeProgress.Target completed=NativeSaveImport.readTarget(completion);boolean clearFlags=true;for(int n=100;n<=112;n++)clearFlags&=completed.read(100,n)==1;check(clearFlags&&completed.read(100,120)==0&&completed.read(100,121)==0,"Cold persisted completion112 and all thirteen gates exclude unreviewed counter120 and multi-value121");
        SelectedSaveFile.Source brokenProvider=new SelectedSaveFile.Source(){public String name(){return "ignores-limit";}public byte[] read(int limit){return new byte[limit+1];}};reject(()->NativeSaveImport.prepareFile(data,codec,output.resolve("provider-limit"),brokenProvider),"Provider ignoring byte limit is rejected by the shipping source boundary");
        SelectedSaveFile.Source changing=selected("changing",bytes);NativeSaveImport.Preview changed=NativeSaveImport.prepareFile(data,codec,output.resolve("changed"),changing);byte[] mutation=bytes.clone();mutation[0]^=1;Documents.publish("changing",mutation);reject(()->NativeSaveImport.apply(changed),"Actual selected document changed after preview is rejected");check(!Files.exists(changed.directory),"Changed selected source creates no destination");
        Fixture altered=new Fixture();NativeSaveImport.Preview identity=NativeSaveImport.prepareFile(altered.resources(),codec,output.resolve("identity"),source);altered.files.get("advdata/mes/start.mes")[7]^=1;reject(()->NativeSaveImport.apply(identity),"Changed game START identity is rejected before import");
        NativeSaveImport.Preview corrupt=NativeSaveImport.prepareFile(data,codec,output.resolve("corrupt"),source);byte[] bad={1,2,3};Path badFile=corrupt.directory.resolve("progress.dat");DesktopEngine.atomic(badFile,bad);reject(()->NativeSaveImport.apply(corrupt),"Malformed existing phone progress is not overwritten");check(Arrays.equals(bad,Files.readAllBytes(badFile))&&backups(corrupt.directory)==0,"Malformed prior progress remains byte-identical without a misleading backup");
        check(Arrays.equals(bytes,SelectedSaveFile.read(source,SIZE)),"All operations preserve the original selected source bytes");
        report.put("native_layout",Json.obj("bytes",SIZE,"music_offset",MUSIC,"cg_offset",CG,"scene_offset",SCENES));report.put("content_uri_source",true);report.put("synthetic_full_map",true);report.put("synthetic_source_sha256",Binary.sha256(bytes));report.put("preserved_story_sha256",Binary.sha256(storyBytes));report.put("backup_sha256",Binary.sha256(Files.readAllBytes(applied.backup)));report.put("completion_flags_set_in_this_fixture",0);
    }
    private List<String> actions(DesktopDd e){List<String> a=new ArrayList<>();for(DesktopEngine.Control c:e.controls())if(!c.action.isEmpty())a.add(c.action);return a;}
    private void settle(DesktopDd e)throws Exception{
        for(int n=0;n<3000;n++){
            for(int ch=0;ch<6;ch++)if(e.audioActive(ch)&&!e.audioLoop(ch))e.audioCompleted(ch,e.audioGeneration(ch));if(!e.movie().isEmpty())e.mediaCompleted(e.ticket());e.tick(1000);
            if(e.menuWaiting()){e.tick(400);if(e.menuWaiting())return;}
            if(e.textWaiting()||e.host.remainingMillis()<0)return;
            if(e.halted())throw new IOException("Original gallery halted at "+e.scriptName());
        }throw new IOException("Original gallery presentation deadline at "+e.scriptName());
    }
    private void pointer(DesktopDd e,String action,List<Object> steps)throws Exception{
        DesktopEngine.Control found=null;for(DesktopEngine.Control c:e.controls())if(c.action.equals(action)){found=c;break;}if(found==null)throw new IOException("Original gallery action absent "+action+" from "+actions(e));
        int x=(found.left+found.right)/2,y=(found.top+found.bottom)/2;check(x>=0&&x<1280&&y>=0&&y<720,"Original gallery control is visible: "+action);
        steps.add(Json.obj("script",e.scriptName(),"pc",e.scriptOffset(),"action",action,"pointer",new int[]{x,y}));e.pointerDown(x,y);e.pointerUp(x,y);settle(e);
    }
    private void picture(DesktopDd e,String name)throws Exception{BufferedImage b=e.render();try(OutputStream out=Files.newOutputStream(output.resolve(name+".png"))){ImageIO.write(b,"png",out);}finally{b.flush();}check(e.missingImages.isEmpty(),"Original gallery render has no missing requested images at "+name);}
    private void originalGallery(String packName)throws Exception{
        if(!packName.matches("[A-Za-z0-9_.-]+"))throw new IOException("App-private original gallery pack filename required");Path input=getTargetContext().getFilesDir().toPath().resolve(packName);GameResources files=new GameResources(RangeStorage.replay(input),"DC4PHDL.exe");Profile p=GameVersion.detect(files).profile();check(p.id.equals(ID),"Original range bytes independently identify PH before gallery import");
        CircusResources data=new CircusResources(files,p);TextCodec codec=GameText.select(files,p,"auto","@none");byte[] full=full();Arrays.fill(full,GLOBALS+100,GLOBALS+113,(byte)1);SelectedSaveFile.Source source=selected("original-gallery-full",full);Path saveRoot=output.resolve("original-gallery-saves");NativeSaveImport.Preview preview=NativeSaveImport.prepareFile(data,codec,saveRoot,source);
        check(preview.additions.cg>0&&!Files.exists(saveRoot),"Original-game progress preview does not create a destination");NativeSaveImport.apply(preview);String previous=System.getProperty("dc.saveRoot");System.setProperty("dc.saveRoot",saveRoot.toString());List<Object> steps=new ArrayList<>();
        try{
            DesktopDd e=new DesktopDd(data,codec,false);check(preview.progress.preview(ID,e.host.state).total==0,"A cold production runtime loads every imported flag from the shipping persistence file");
            for(int n=0;n<16;n++){settle(e);List<String> available=actions(e);if(available.contains("extra")&&(available.contains("start")||available.contains("sce_start")))break;String chosen=null;for(String a:new String[]{"window","03","02"})if(available.contains(a)){chosen=a;break;}if(chosen==null)throw new IOException("Unresolved original title "+available);pointer(e,chosen,steps);}
            check(actions(e).contains("extra"),"Original title exposes its actual extras menu after a cold import");picture(e,"gallery-title");
            String path=arguments.getString("gallery_actions","extra,cgmode");for(String action:path.split(","))pointer(e,action,steps);picture(e,"gallery-imported");
            boolean cgAvailable=false;for(String action:actions(e))cgAvailable|=action.startsWith("cg")||action.startsWith("@slot:");check(cgAvailable||e.scriptName().toUpperCase(Locale.ROOT).startsWith("CG"),"Original CG script exposes imported entries or its viewer");
            String expected=arguments.getString("gallery_resource");if(expected!=null)check(expected.replace('\\','/').equalsIgnoreCase(e.host.scene.slots[1].resource.replace('\\','/')),"Original CG viewer binds the independently expected resource after import");
            check(preview.progress.preview(ID,e.host.state).total==0,"Original gallery navigation preserves every imported unlock");
            report.put("original_gallery",Json.obj("pack_sha256",Binary.sha256(Files.readAllBytes(input)),"synthetic_full_map_imported",true,"steps",steps,"final_script",e.scriptName(),"final_pc",e.scriptOffset(),"actions",actions(e),"background",e.host.scene.slots[1].resource,"missing_images",new ArrayList<>(e.missingImages),"game_executable_run",false,"android_activity_touch_or_saf_game_tree_tested",false));
        }finally{if(previous==null)System.clearProperty("dc.saveRoot");else System.setProperty("dc.saveRoot",previous);}
    }
    @Override public void onCreate(Bundle args){super.onCreate(args);arguments=args;start();}
    @Override public void onStart(){Bundle result=new Bundle();boolean pass=false;try{output=Files.createTempDirectory(getTargetContext().getFilesDir().toPath(),"dc4-progress-import-");check(NativeProgress.supports(ID)&&NativeProgress.fileSize(ID)==SIZE,"PH-specific native progress format is admitted at its exact read bound");transactions();if(arguments!=null&&arguments.getString("gallery_pack")!=null)originalGallery(arguments.getString("gallery_pack"));pass=true;}catch(Throwable failure){report.put("failure",failure.toString());log.append("FAIL ").append(failure).append('\n');}
        report.put("pass",pass);report.put("checks",checks);report.put("output",output==null?"":output.toString());report.put("scope","Production SelectedSaveFile ContentResolver reads and NativeSaveImport prepare/apply transactions over independently authored PH native-format bytes; no original EXE or user save writes; no Launcher Activity/picker interaction claimed");
        try{if(output!=null){Files.write(output.resolve("report.json"),(Json.text(report)+"\n").getBytes("UTF-8"));Files.write(output.resolve("result.txt"),log.toString().getBytes("UTF-8"));}}catch(IOException failure){pass=false;report.put("write_failure",failure.toString());}
        result.putString("stream","REPORT "+Json.text(report)+"\n"+log+"CHECKS "+checks+"\n");finish(pass?-1:0,result);
    }
}
