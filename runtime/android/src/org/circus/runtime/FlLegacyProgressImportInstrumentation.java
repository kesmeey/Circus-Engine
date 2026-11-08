package org.circus.runtime;

import android.app.Instrumentation;
import android.os.Bundle;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import org.circus.core.*;

/** Production Android importer over an explicitly copied, read-only 2028-byte
 * FL source. No original save bytes are packaged in the APK or repository. */
public final class FlLegacyProgressImportInstrumentation extends Instrumentation {
    private static final String HASH="0c21781ff89c0d4080482f9ea172eb3a9a2c51748892453e2afa311d22852725";
    private static final int[] COMPLETION={0,1,2,3,4,5,6,7,8,9,10,11,12,13,52,53};
    private final StringBuilder log=new StringBuilder();
    private Path output;private int checks;private String fixtureName;
    private interface Attempt {void run()throws Exception;}
    private void check(boolean value,String reason)throws IOException{if(!value)throw new IOException(reason);checks++;log.append("PASS ").append(reason).append('\n');}
    private void reject(Attempt action,String reason)throws Exception{boolean failed=false;try{action.run();}catch(IOException expected){failed=true;}check(failed,reason);}
    private static boolean completion(int n){for(int v:COMPLETION)if(v==n)return true;return false;}
    private static final class Source implements SelectedSaveFile.Source {
        final Path path;Source(Path path){this.path=path;}
        public String name(){return "user-2028-progress.renamed";}
        public byte[] read(int limit)throws IOException{byte[] b=Files.readAllBytes(path);if(b.length>limit)throw new IOException("Input exceeds limit");return b;}
    }
    private static final class Bytes implements SelectedSaveFile.Source {
        byte[] bytes;Bytes(byte[] b){bytes=b.clone();}
        public String name(){return "reviewed-layout.renamed";}
        public byte[] read(int limit)throws IOException{if(bytes.length>limit)throw new IOException("Input exceeds limit");return bytes.clone();}
    }
    private static final class Fixture implements GameResources.Storage {
        final Map<String,byte[]> data=new TreeMap<>();
        Fixture(byte[] original){byte[] names=new byte[208];names[16]=1;System.arraycopy("START".getBytes(Binary.SJIS),0,names,20,5);data.put("advdata/dat/names.dat",names);data.put("advdata/mes/start.mes",new byte[]{0,0,0,0,0x69,(byte)0x9c,0,0,0});if(original!=null)data.put("dc2fl/system.dat",original.clone());}
        public Set<String> paths(){return data.keySet();}
        private byte[] get(String path)throws IOException{byte[] b=data.get(path.toLowerCase(Locale.ROOT));if(b==null)throw new FileNotFoundException(path);return b;}
        public byte[] read(String path,int limit)throws IOException{byte[] b=get(path);if(b.length>limit)throw new IOException("Fixture read limit");return b.clone();}
        public Archive.Source source(String path)throws IOException{return new Archive.Source(){public long size()throws IOException{return get(path).length;}public byte[] read(long at,int count)throws IOException{byte[] b=get(path);Binary.range(at,count,b.length);return Arrays.copyOfRange(b,(int)at,(int)at+count);}};}
        CircusResources resources()throws IOException{GameResources files=new GameResources(this);Profile profile=GameVersion.detect(files).profile();if(!profile.id.equals("dc2fl-9c69"))throw new IOException("Independent FL START identity");return new CircusResources(files,profile);}
    }
    private static int backups(Path directory)throws IOException{int count=0;try(DirectoryStream<Path> stream=Files.newDirectoryStream(directory,"progress.before-native-*.dat")){for(Path ignored:stream)count++;}return count;}
    private void compareFlags(NativeProgress.Target target,byte[] raw,boolean seeded)throws Exception{
        for(int n=0;n<640;n++){int expected=seeded&&n==7?2:raw[0xd4+n]&255;if(target.read(97,n)!=expected)throw new IOException("Legacy CG offset/index mismatch "+n);}
        check(true,"All640 CG bytes match independent old D4 source offsets");
        for(int n=0;n<1024;n++){int expected=seeded&&n==8?3:raw[0x354+n]&255;if(target.read(98,n)!=expected)throw new IOException("Legacy seen offset/index mismatch "+n);}
        check(true,"All1024 scene bytes match independent old354 source offsets");
    }
    private void runAudit(Path original)throws Exception{
        byte[] raw=Files.readAllBytes(original);check(raw.length==2028&&Binary.sha256(raw).equals(HASH),"Explicitly copied actual2028-byte FL source identity");
        int cg=0,seen=0;for(int n=0;n<640;n++)cg+=raw[0xd4+n];for(int n=0;n<1024;n++)seen+=raw[0x354+n];
        check(cg==597&&seen==360&&raw[0x70+56]==10,"Independent original old-layout counts and ignored counter56");
        Fixture fixture=new Fixture(null);CircusResources data=fixture.resources();TextCodec codec=TextCodec.identity();Source source=new Source(original);
        NativeSaveImport.Preview fresh=NativeSaveImport.prepareFile(data,codec,output.resolve("fresh"),source);
        check(fresh.progress.source.cg==597&&fresh.progress.source.scenes==360&&fresh.progress.source.completion==16&&fresh.storySlots==0,"Selected-file preview recognizes legacy offsets without sibling scan");
        check(!Files.exists(fresh.directory)&&fresh.sourceHash.equals(HASH),"Actual-source preview creates no phone files");
        NativeSaveImport.Result added=NativeSaveImport.apply(fresh);check(added.added.cg==597&&added.added.scenes==360&&added.added.completion==16&&added.backup==null,"Actual source imports597 CG/360 scene/16 reviewed completion flags");
        NativeProgress.Target first=NativeSaveImport.readTarget(fresh);compareFlags(first,raw,false);
        for(int n:COMPLETION)if(first.read(100,n)!=1)throw new IOException("Legacy completion offset/index mismatch "+n);
        check(first.read(100,56)==0,"Original counter56 is excluded from selective progress import");

        Path phoneRoot=output.resolve("phone");NativeSaveImport.Preview seed=NativeSaveImport.prepareFile(data,codec,phoneRoot,source);NativeProgress.Target phone=NativeSaveImport.readTarget(seed);PcHost host=(PcHost)phone.legacy;
        host.setNativeNames("Own","Name");phone.write(99,7,37);phone.write(99,13,3);phone.write(95,0,41);phone.write(95,151,77);host.readCheckpoints.add("phone-text-history");
        for(int n=0;n<100;n++)if(!completion(n))phone.write(100,n,47);phone.write(100,1,2);phone.write(97,7,2);phone.write(98,8,3);
        byte[] before=phone.save();Path progress=seed.directory.resolve("progress.dat");DesktopEngine.atomic(progress,before);
        DesktopSave saved=new DesktopSave();saved.state=new Dc2Session(data,codec).vm.snapshot();saved.caption="Existing independent phone narrative slot";byte[] slot=saved.encode();Path story=seed.directory.resolve("slot-2.sav");DesktopEngine.atomic(story,slot);
        NativeSaveImport.Preview preview=NativeSaveImport.prepareFile(data,codec,phoneRoot,source);check(Arrays.equals(before,Files.readAllBytes(progress))&&Arrays.equals(slot,Files.readAllBytes(story)),"Merge preview is read-only for existing phone state");
        NativeSaveImport.Result merged=NativeSaveImport.apply(preview);check(merged.backup!=null&&Arrays.equals(before,Files.readAllBytes(merged.backup)),"Atomic production merge backs up exact old phone progress");
        NativeProgress.Target loaded=NativeSaveImport.readTarget(preview);PcHost after=(PcHost)loaded.legacy;compareFlags(loaded,raw,true);
        check(after.familyName.equals("Own")&&after.givenName.equals("Name")&&loaded.read(99,7)==37&&loaded.read(99,13)==3&&loaded.read(95,0)==41&&loaded.read(95,151)==77&&after.readCheckpoints.contains("phone-text-history"),"Phone settings/names/voice and text history preserved");
        for(int n=0;n<100;n++)if(!completion(n)&&loaded.read(100,n)!=47)throw new IOException("Unreviewed phone global changed "+n);
        check(loaded.read(100,1)==2,"All unreviewed globals and higher existing completion value preserved");
        check(Arrays.equals(slot,Files.readAllBytes(story)),"Existing real CVS2/DCS2 narrative slot remains byte-identical");
        byte[] afterBytes=Files.readAllBytes(progress);NativeSaveImport.Result repeat=NativeSaveImport.apply(NativeSaveImport.prepareFile(data,codec,phoneRoot,source));
        check(repeat.added.total==0&&repeat.backup==null&&backups(preview.directory)==1&&Arrays.equals(afterBytes,Files.readAllBytes(progress)),"Repeat actual-source import is idempotent without new backup");
        NativeSaveImport.apply(NativeSaveImport.prepareFile(data,codec,phoneRoot,new Bytes(new byte[2032])));
        check(Arrays.equals(afterBytes,Files.readAllBytes(progress)),"Empty modern2032 source cannot erase imported old-layout unlocks");

        byte[] aligned=new byte[2032];System.arraycopy(raw,0,aligned,0,0x48);System.arraycopy(raw,0x48,aligned,0x4c,raw.length-0x48);
        NativeSaveImport.Preview modern=NativeSaveImport.prepareFile(data,codec,output.resolve("aligned-modern-fixture"),new Bytes(aligned));
        check(modern.progress.source.cg==597&&modern.progress.source.scenes==360&&modern.progress.source.completion==16,"Explicit synthetic modern-offset equivalent has matching summary");
        for(int extent:new int[]{2027,2029,2031})reject(()->NativeSaveImport.prepareFile(data,codec,output.resolve("bad"+extent),new Bytes(Arrays.copyOf(raw,extent))),"Unknown intermediate/truncated FL extent rejected "+extent);
        byte[] invalid=raw.clone();invalid[0xd4+639]=2;reject(()->NativeSaveImport.prepareFile(data,codec,output.resolve("invalid-cg"),new Bytes(invalid)),"Invalid old-layout CG2 rejected");
        Bytes changing=new Bytes(raw);NativeSaveImport.Preview changed=NativeSaveImport.prepareFile(data,codec,output.resolve("changed"),changing);changing.bytes[0]^=1;
        reject(()->NativeSaveImport.apply(changed),"Actual-source bytes changed after preview rejected before write");check(!Files.exists(changed.directory.resolve("progress.dat")),"Changed-source rejection writes no phone progress");

        Fixture folder=new Fixture(raw);CircusResources folderData=folder.resources();check(NativeProgress.sourcePaths(folderData.files,folderData.profileId).equals(Collections.singletonList("dc2fl/system.dat")),"Known dc2fl subdirectory is enumerated without recursive guessing");
        NativeSaveImport.Preview folderPreview=NativeSaveImport.prepare(folderData,codec,output.resolve("folder"));NativeSaveImport.apply(folderPreview);
        compareFlags(NativeSaveImport.readTarget(folderPreview),raw,false);
        String wrapped="old_source_circus_dc2fl/dc2fl/system.dat";Fixture backupFolder=new Fixture(null);backupFolder.data.put(wrapped,raw.clone());backupFolder.data.put("advdata/savedata/system.dat",new byte[2032]);CircusResources backupData=backupFolder.resources();
        List<String> candidates=NativeProgress.sourcePaths(backupData.files,backupData.profileId);
        check(candidates.size()==2&&candidates.contains(wrapped)&&candidates.contains("AdvData/SaveData/system.dat"),"Actual known backup wrapper and modern empty file are both explicit candidates");
        reject(()->NativeSaveImport.prepare(backupData,codec,output.resolve("ambiguous-wrapper")),"Modern empty and old unlocked copies cannot be selected automatically");
        check(!Files.exists(output.resolve("ambiguous-wrapper")),"Ambiguous source selection makes no phone files");
        NativeSaveImport.Preview wrappedPreview=NativeSaveImport.prepare(backupData,codec,output.resolve("wrapper"),wrapped);
        check(wrappedPreview.additions.cg==597&&wrappedPreview.additions.scenes==360&&wrappedPreview.additions.completion==16,"Explicit actual wrapper source previews597 CG/360 scenes/16 completion flags");
        NativeSaveImport.apply(wrappedPreview);compareFlags(NativeSaveImport.readTarget(wrappedPreview),raw,false);
        NativeSaveImport.Preview modernEmpty=NativeSaveImport.prepare(backupData,codec,output.resolve("modern-empty"),"AdvData/SaveData/system.dat");
        check(modernEmpty.progress.source.total==0,"Modern empty source is correctly reported empty rather than overriding old source");
        check(Binary.sha256(Files.readAllBytes(original)).equals(HASH),"Explicitly copied source file is byte-identical after all service calls");
    }
    @Override public void onCreate(Bundle args){super.onCreate(args);fixtureName=args==null?null:args.getString("fixture");if(fixtureName==null)fixtureName="fl-legacy-original-2028.dat";start();}
    @Override public void onStart(){Bundle result=new Bundle();try{
        if(!fixtureName.matches("[A-Za-z0-9_.-]+"))throw new IOException("App-private fixture filename required");
        Path files=getTargetContext().getFilesDir().toPath();output=Files.createTempDirectory(files,"fl-legacy-progress-import-audit-");runAudit(files.resolve(fixtureName));
        log.append("CHECKS ").append(checks).append("\nSOURCE_SHA256 ").append(HASH).append("\nSCOPE actual explicitly copied2028-byte user system.dat with synthetic independently recognized FL START/NAMES; production Android selected-file/folder service, atomic backup/merge and direct independent flag-offset comparison; no SAF provider, original EXE execution, native story SAV conversion or whole original-frame claim\nOUTPUT ").append(output).append('\n');
        Files.write(output.resolve("result.txt"),log.toString().getBytes("UTF-8"));result.putString("stream",log.toString());finish(-1,result);
    }catch(Throwable failure){log.append("FAIL ").append(failure).append('\n');try{if(output!=null)Files.write(output.resolve("result.txt"),log.toString().getBytes("UTF-8"));}catch(Exception ignored){}result.putString("stream",log.toString());finish(0,result);}}
}
