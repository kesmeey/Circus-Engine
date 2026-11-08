package org.circus.runtime;

import android.app.Instrumentation;
import android.os.Bundle;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import org.circus.core.*;

/** Production selected-file import/atomic service with synthetic SC/FL data.
 * No game assets, external documents or existing shared-save roots are accessed. */
public final class ScFlProgressImportInstrumentation extends Instrumentation {
    private int checks;private Path output;private final StringBuilder log=new StringBuilder();
    private interface Attempt {void run()throws Exception;}
    private void check(boolean value,String reason)throws IOException{if(!value)throw new IOException(reason);checks++;log.append("PASS ").append(reason).append('\n');}
    private void reject(Attempt attempt,String reason)throws Exception{boolean failed=false;try{attempt.run();}catch(IOException expected){failed=true;}check(failed,reason);}
    private static final class Source implements SelectedSaveFile.Source {
        byte[] bytes;Source(byte[] bytes){this.bytes=bytes.clone();}
        public String name(){return "synthetic-original.renamed";}
        public byte[] read(int limit)throws IOException{if(bytes.length>limit)throw new IOException("Input exceeds limit "+limit);return bytes.clone();}
    }
    private static final class Fixture implements GameResources.Storage {
        final String id;final Map<String,byte[]> data=new TreeMap<>();
        Fixture(String id)throws IOException{this.id=id;Profile profile=Profiles.get(id);byte[] names=new byte[208];names[16]=1;System.arraycopy("START".getBytes(Binary.SJIS),0,names,20,5);data.put("advdata/dat/names.dat",names);data.put("advdata/mes/start.mes",new byte[]{0,0,0,0,(byte)profile.version,(byte)(profile.version>>>8),0,0,0});}
        public Set<String> paths(){return data.keySet();}
        private byte[] get(String path)throws IOException{byte[] b=data.get(path.toLowerCase(Locale.ROOT));if(b==null)throw new FileNotFoundException(path);return b;}
        public byte[] read(String path,int limit)throws IOException{byte[] b=get(path);if(b.length>limit)throw new IOException("Synthetic resource limit");return b.clone();}
        public Archive.Source source(String path)throws IOException{return new Archive.Source(){public long size()throws IOException{return get(path).length;}public byte[] read(long at,int n)throws IOException{byte[] b=get(path);Binary.range(at,n,b.length);return Arrays.copyOfRange(b,(int)at,(int)at+n);}};}
        CircusResources resources()throws IOException{GameResources files=new GameResources(this);Profile profile=GameVersion.detect(files).profile();if(!profile.id.equals(id))throw new IOException("Synthetic START identity");return new CircusResources(files,profile);}
    }
    private static byte[] original(boolean fl){
        byte[] b=new byte[fl?2032:2340];int global=fl?0x74:0x70,cg=fl?0xd8:0xd4,seen=fl?0x358:0x4d4,cgCount=fl?640:1024;
        b[global+20]=(byte)255;b[global+56]=(byte)255;b[global+60]=(byte)255;b[global+80]=1;
        if(fl){for(int n=0;n<=13;n++)b[global+n]=1;b[global+52]=b[global+53]=1;}else for(int n=1;n<=6;n++)b[global+n]=1;
        b[cg]=b[cg+7]=b[cg+cgCount-1]=1;b[seen]=b[seen+8]=b[seen+1023]=1;Arrays.fill(b,seen+1024,b.length,(byte)255);return b;
    }
    private static int backups(Path directory)throws IOException{int count=0;try(DirectoryStream<Path> stream=Files.newDirectoryStream(directory,"progress.before-native-*.dat")){for(Path ignored:stream)count++;}return count;}
    private void family(boolean fl)throws Exception{
        String id=fl?"dc2fl-9c69":"dc2sc-3b69";Fixture fixture=new Fixture(id);CircusResources data=fixture.resources();TextCodec codec=TextCodec.identity();Source source=new Source(original(fl));String sourceHash=Binary.sha256(source.bytes);Path family=output.resolve(id),root=family.resolve("phone");
        NativeSaveImport.Preview abandoned=NativeSaveImport.prepareFile(data,codec,family.resolve("abandoned"),source);
        check(abandoned.progress.source.completion==(fl?16:0)&&abandoned.additions.cg==3&&abandoned.additions.scenes==3,"Exact independently selected source offsets/scope "+id);
        check(!Files.exists(family.resolve("abandoned"))&&abandoned.storySlots==0,"Selected-file preview writes nothing or scans narrative siblings "+id);
        NativeSaveImport.Preview fresh=NativeSaveImport.prepareFile(data,codec,family.resolve("fresh"),source);NativeSaveImport.Result first=NativeSaveImport.apply(fresh);
        check(first.added.total==(fl?22:6)&&first.backup==null&&fresh.progress.preview(NativeSaveImport.readTarget(fresh)).total==0,"First Android service import persists all reviewed progress "+id);

        NativeSaveImport.Preview preview=NativeSaveImport.prepareFile(data,codec,root,source);NativeProgress.Target phone=NativeSaveImport.readTarget(preview);PcHost host=(PcHost)phone.legacy;
        host.setNativeNames("Own","Name");phone.write(99,7,37);phone.write(99,13,3);phone.write(95,0,41);phone.write(95,fl?151:79,77);host.readCheckpoints.add("synthetic-phone-read-history");
        for(int n:new int[]{20,56,57,60,80,99})phone.write(100,n,47);phone.write(100,1,2);phone.write(97,7,1);phone.write(98,8,1);phone.write(97,18,1);phone.write(98,18,1);
        byte[] before=phone.save();Path progress=preview.directory.resolve("progress.dat");DesktopEngine.atomic(progress,before);
        DesktopSave slot=new DesktopSave();slot.state=new Dc2Session(data,codec).vm.snapshot();slot.caption="Synthetic existing narrative slot";byte[] slotBytes=slot.encode();Path story=preview.directory.resolve("slot-2.sav");DesktopEngine.atomic(story,slotBytes);
        preview=NativeSaveImport.prepareFile(data,codec,root,source);NativeProgress.Summary expected=preview.additions;check(expected.cg==2&&expected.scenes==2&&expected.completion==(fl?15:0),"Preview merges missing flags only, preserving nonzero completion "+id);
        check(Arrays.equals(before,Files.readAllBytes(progress))&&Arrays.equals(slotBytes,Files.readAllBytes(story)),"Read-only merge preview preserves phone files "+id);
        NativeSaveImport.Result result=NativeSaveImport.apply(preview);check(result.added.total==expected.total&&result.backup!=null&&Arrays.equals(before,Files.readAllBytes(result.backup)),"Atomic service creates exact pre-import backup "+id);
        NativeProgress.Target loaded=NativeSaveImport.readTarget(preview);PcHost after=(PcHost)loaded.legacy;
        check(after.familyName.equals("Own")&&after.givenName.equals("Name")&&loaded.read(99,7)==37&&loaded.read(99,13)==3&&loaded.read(95,0)==41&&loaded.read(95,fl?151:79)==77&&after.readCheckpoints.contains("synthetic-phone-read-history"),"Phone settings/names/voice/read journal preserved "+id);
        for(int n:new int[]{20,56,57,60,80,99})check(loaded.read(100,n)==47,"Unreviewed global preserved "+id+":"+n);
        check(loaded.read(100,1)==2&&loaded.read(97,18)==1&&loaded.read(98,18)==1,"Existing unlocks absent from source retained "+id);
        check(loaded.read(97,fl?639:1023)==1&&loaded.read(98,1023)==1,"Independent native gallery/scene final bytes imported "+id);
        check(Arrays.equals(slotBytes,Files.readAllBytes(story)),"Existing runtime narrative slot byte-identical "+id);
        byte[] merged=Files.readAllBytes(progress);NativeSaveImport.Result repeat=NativeSaveImport.apply(NativeSaveImport.prepareFile(data,codec,root,source));
        check(repeat.added.total==0&&repeat.backup==null&&backups(preview.directory)==1&&Arrays.equals(merged,Files.readAllBytes(progress)),"Repeated Android service import is byte-identical with no new backup "+id);
        check(sourceHash.equals(Binary.sha256(source.bytes)),"Selected source remains read-only "+id);

        Source changing=new Source(original(fl));NativeSaveImport.Preview changed=NativeSaveImport.prepareFile(data,codec,family.resolve("changed"),changing);changing.bytes[0]^=1;
        reject(()->NativeSaveImport.apply(changed),"Source hash change after preview rejected "+id);check(!Files.exists(changed.directory.resolve("progress.dat")),"Changed source makes no phone progress "+id);
        Fixture changedStart=new Fixture(id);CircusResources changingData=changedStart.resources();NativeSaveImport.Preview changedIdentity=NativeSaveImport.prepareFile(changingData,codec,family.resolve("changed-start"),source);changedStart.data.get("advdata/mes/start.mes")[6]^=1;
        reject(()->NativeSaveImport.apply(changedIdentity),"START identity change after preview rejected "+id);check(!Files.exists(changedIdentity.directory.resolve("progress.dat")),"Changed game identity makes no phone progress "+id);
        NativeSaveImport.Preview corrupt=NativeSaveImport.prepareFile(data,codec,family.resolve("corrupt"),source);byte[] bad={1,2,3};Path corruptFile=corrupt.directory.resolve("progress.dat");DesktopEngine.atomic(corruptFile,bad);
        reject(()->NativeSaveImport.apply(corrupt),"Corrupt existing phone progress rejected "+id);check(Arrays.equals(bad,Files.readAllBytes(corruptFile))&&backups(corrupt.directory)==0,"Corrupt phone bytes preserved without misleading backup "+id);
        reject(()->NativeSaveImport.prepareFile(data,codec,family.resolve("wrong-game"),new Source(original(!fl))),"Other game's original extent rejected "+id);
        reject(()->NativeSaveImport.prepareFile(data,codec,family.resolve("truncated"),new Source(Arrays.copyOf(original(fl),fl?2031:2339))),"Truncated native source rejected "+id);
    }
    @Override public void onCreate(Bundle args){super.onCreate(args);start();}
    @Override public void onStart(){Bundle result=new Bundle();try{
        output=Files.createTempDirectory(getTargetContext().getFilesDir().toPath(),"sc-fl-progress-import-audit-");family(false);family(true);
        log.append("CHECKS ").append(checks).append("\nSCOPE synthetic START/NAMES/system.dat; Android production prepareFile/apply, atomic backup/merge/rejection; no SAF provider, original-game resources or existing user save roots\nOUTPUT ").append(output).append('\n');
        Files.write(output.resolve("result.txt"),log.toString().getBytes("UTF-8"));result.putString("stream",log.toString());finish(-1,result);
    }catch(Throwable failure){log.append("FAIL ").append(failure).append('\n');try{if(output!=null)Files.write(output.resolve("result.txt"),log.toString().getBytes("UTF-8"));}catch(Exception ignored){}result.putString("stream",log.toString());finish(0,result);}}
}
