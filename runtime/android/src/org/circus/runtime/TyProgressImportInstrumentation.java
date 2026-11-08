package org.circus.runtime;

import android.app.Instrumentation;
import android.os.Bundle;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import org.circus.core.*;

/** Shipping Android service over a read-only native-default fixture and explicit
 * synthetic TY flag maps. No original game resource bytes are packaged. */
public final class TyProgressImportInstrumentation extends Instrumentation {
    private static final String DEFAULT_HASH="461364e5b73c99c7ceb289bf133b905a7f20686f35aa561eda98ebaa4f9faa05";
    private final StringBuilder log=new StringBuilder();private int checks;private Path output;private String fixtureName;
    private interface Attempt {void run()throws Exception;}
    private void check(boolean value,String reason)throws IOException{if(!value)throw new IOException(reason);checks++;log.append("PASS ").append(reason).append('\n');}
    private void reject(Attempt action,String reason)throws Exception{boolean failed=false;try{action.run();}catch(IOException expected){failed=true;}check(failed,reason);}
    private static final class Source implements SelectedSaveFile.Source {
        byte[] bytes;Source(byte[] bytes){this.bytes=bytes.clone();}
        public String name(){return "renamed-TY-native.progress";}
        public byte[] read(int limit)throws IOException{if(bytes.length>limit)throw new IOException("Input exceeds limit");return bytes.clone();}
    }
    private static final class Fixture implements GameResources.Storage {
        final Map<String,byte[]> files=new TreeMap<>();
        Fixture(){byte[] names=new byte[208];names[16]=1;System.arraycopy("START".getBytes(Binary.SJIS),0,names,20,5);files.put("advdata/dat/names.dat",names);files.put("advdata/mes/start.mes",new byte[]{0,0,0,0,0x69,0x5f,0,0,0});}
        public Set<String> paths(){return files.keySet();}
        private byte[] get(String path)throws IOException{byte[] b=files.get(path.toLowerCase(Locale.ROOT));if(b==null)throw new FileNotFoundException(path);return b;}
        public byte[] read(String path,int limit)throws IOException{byte[] b=get(path);if(b.length>limit)throw new IOException("Fixture read limit");return b.clone();}
        public Archive.Source source(String path)throws IOException{return new Archive.Source(){public long size()throws IOException{return get(path).length;}public byte[] read(long at,int n)throws IOException{byte[] b=get(path);Binary.range(at,n,b.length);return Arrays.copyOfRange(b,(int)at,(int)at+n);}};}
        CircusResources resources()throws IOException{GameResources files=new GameResources(this);Profile profile=GameVersion.detect(files).profile();if(!profile.id.equals("dc2ty-5f69"))throw new IOException("Independent TY START identity");return new CircusResources(files,profile);}
    }
    private static int backups(Path directory)throws IOException{int count=0;try(DirectoryStream<Path> stream=Files.newDirectoryStream(directory,"progress.before-native-*.dat")){for(Path ignored:stream)count++;}return count;}
    private static int pcp3(byte[] bytes)throws IOException{for(int n=0;n+4<=bytes.length;n++)if(bytes[n]==0x50&&bytes[n+1]==0x43&&bytes[n+2]==0x50&&bytes[n+3]==0x33)return n;throw new IOException("Missing PCP3 owner");}
    private void flags(NativeProgress.Target target,boolean seeded)throws Exception{
        boolean cg=true,seen=true;for(int n=0;n<512;n++)cg&=target.read(97,n)==(seeded&&n==7?2:1);for(int n=0;n<1024;n++)seen&=target.read(98,n)==(seeded&&n==8?3:1);
        check(cg&&seen,"All512 CG and1024 scene flags independently checked across native boundaries");
    }
    private void runAudit(Path defaultPath)throws Exception{
        byte[] defaults=Files.readAllBytes(defaultPath);check(defaults.length==1848&&Binary.sha256(defaults).equals(DEFAULT_HASH),"Read-only actual native1848-byte default template identity");
        check(NativeProgress.supports("dc2ty-5f69")&&NativeProgress.fileSize("dc2ty-5f69")==1848,"Reviewed TY profile and exact native system-file size admitted");
        Fixture fixture=new Fixture();CircusResources data=fixture.resources();TextCodec codec=TextCodec.identity();Source empty=new Source(defaults);
        NativeSaveImport.Preview zero=NativeSaveImport.prepareFile(data,codec,output.resolve("native-default"),empty);
        check(zero.progress.source.total==0&&zero.additions.total==0&&!Files.exists(zero.directory),"Native default template previews zero unlocks without writing phone state");
        check(NativeSaveImport.apply(zero).added.total==0&&!Files.exists(zero.directory),"Empty native default import is a no-write operation");
        byte[] full=defaults.clone();Arrays.fill(full,0x70,0xd4,(byte)255);Arrays.fill(full,0xd4,0x2d4,(byte)1);Arrays.fill(full,0x2d4,0x6d4,(byte)1);Arrays.fill(full,0x6d4,0x738,(byte)255);Source original=new Source(full);String originalHash=Binary.sha256(full);
        NativeSaveImport.Preview fresh=NativeSaveImport.prepareFile(data,codec,output.resolve("fresh"),original);
        check(fresh.progress.source.cg==512&&fresh.progress.source.scenes==1024&&fresh.progress.source.completion==0&&fresh.progress.source.music==0,"Independent D4/2D4 full-map source excludes all globals and voice bytes");
        check(fresh.description().contains("仅合并 CG 和场景已读")&&fresh.description().contains("不导入通关状态"),"TY preview explicitly states the partial system-progress scope");
        NativeSaveImport.Result first=NativeSaveImport.apply(fresh);check(first.added.total==1536&&first.backup==null,"Fresh production import persists all1536 reviewed flags");flags(NativeSaveImport.readTarget(fresh),false);
        Path phoneRoot=output.resolve("phone");NativeSaveImport.Preview preview=NativeSaveImport.prepareFile(data,codec,phoneRoot,original);NativeProgress.Target phone=NativeSaveImport.readTarget(preview);PcHost host=(PcHost)phone.legacy;
        host.setNativeNames("Own","Name");phone.write(99,7,37);phone.write(99,13,3);host.readCheckpoints.add("TY-phone-text-history");
        for(int n=0;n<100;n++){phone.write(100,n,(n*13+17)&255);phone.write(95,n,(n*11+23)&255);}phone.write(97,7,2);phone.write(98,8,3);
        byte[] before=phone.save();Path progress=preview.directory.resolve("progress.dat");DesktopEngine.atomic(progress,before);DesktopSave story=new DesktopSave();story.state=new Dc2Session(data,codec).vm.snapshot();story.caption="Existing TY phone narrative slot";byte[] slotBytes=story.encode();Path slot=preview.directory.resolve("slot-2.sav");DesktopEngine.atomic(slot,slotBytes);
        preview=NativeSaveImport.prepareFile(data,codec,phoneRoot,original);check(preview.additions.cg==511&&preview.additions.scenes==1023&&preview.additions.completion==0,"Merge preview counts only absent flags");
        check(Arrays.equals(before,Files.readAllBytes(progress))&&Arrays.equals(slotBytes,Files.readAllBytes(slot)),"Merge preview does not write phone progress or narrative slots");
        NativeSaveImport.Result result=NativeSaveImport.apply(preview);check(result.backup!=null&&Arrays.equals(before,Files.readAllBytes(result.backup)),"Atomic production merge backs up exact previous progress");
        NativeProgress.Target loaded=NativeSaveImport.readTarget(preview);PcHost after=(PcHost)loaded.legacy;byte[] merged=loaded.save();
        check(Arrays.equals(Arrays.copyOf(before,pcp3(before)),Arrays.copyOf(merged,pcp3(merged))),"Complete parent phone settings/state prefix remains byte-identical");
        check(after.familyName.equals("Own")&&after.givenName.equals("Name")&&loaded.read(99,7)==37&&loaded.read(99,13)==3&&after.readCheckpoints.equals(Collections.singleton("TY-phone-text-history")),"Phone names/settings and existing text history preserved");
        boolean excluded=true;for(int n=0;n<100;n++)excluded&=loaded.read(100,n)==((n*13+17)&255)&&loaded.read(95,n)==((n*11+23)&255);
        check(excluded,"All100 phone globals and100 voice-setting bytes preserved");flags(loaded,true);
        check(Arrays.equals(slotBytes,Files.readAllBytes(slot)),"Existing real CVS2/DCS2 narrative slot remains byte-identical");
        NativeSaveImport.Result repeated=NativeSaveImport.apply(NativeSaveImport.prepareFile(data,codec,phoneRoot,original));
        check(repeated.added.total==0&&repeated.backup==null&&backups(preview.directory)==1&&Arrays.equals(merged,Files.readAllBytes(progress)),"Repeated TY import is byte-identical without another backup");
        NativeSaveImport.apply(NativeSaveImport.prepareFile(data,codec,phoneRoot,empty));check(Arrays.equals(merged,Files.readAllBytes(progress)),"Empty native source cannot erase imported or phone progress");
        byte[] noStamp=full.clone();Arrays.fill(noStamp,0x48,0x4c,(byte)0x7f);NativeSaveImport.Preview noVersion=NativeSaveImport.prepareFile(data,codec,output.resolve("no-version-gate"),new Source(noStamp));
        check(noVersion.additions.cg==512&&noVersion.additions.scenes==1024,"TY imports do not invent an unrelated native version-word gate");
        for(int size:new int[]{1847,1849,2028,2032,2340})reject(()->NativeSaveImport.prepareFile(data,codec,output.resolve("wrong-size"+size),new Source(Arrays.copyOf(full,size))),"Unknown/truncated/other-game system extent rejected "+size);
        byte[] bad=full.clone();bad[0xd4+511]=2;reject(()->NativeSaveImport.prepareFile(data,codec,output.resolve("invalid-cg"),new Source(bad)),"Nonbinary native CG2 rejected");
        Source changing=new Source(full);NativeSaveImport.Preview changed=NativeSaveImport.prepareFile(data,codec,output.resolve("changed"),changing);changing.bytes[0]^=1;
        reject(()->NativeSaveImport.apply(changed),"Selected source changed after preview rejected before write");check(!Files.exists(changed.directory.resolve("progress.dat")),"Changed-source rejection creates no phone progress");
        Fixture changedStart=new Fixture();NativeSaveImport.Preview identity=NativeSaveImport.prepareFile(changedStart.resources(),codec,output.resolve("changed-start"),original);changedStart.files.get("advdata/mes/start.mes")[6]^=1;
        reject(()->NativeSaveImport.apply(identity),"Changed START identity rejected before write");check(!Files.exists(identity.directory.resolve("progress.dat")),"Changed-identity rejection creates no phone progress");
        NativeSaveImport.Preview corrupt=NativeSaveImport.prepareFile(data,codec,output.resolve("corrupt-phone"),original);byte[] invalid={1,2,3};Path corruptFile=corrupt.directory.resolve("progress.dat");DesktopEngine.atomic(corruptFile,invalid);
        reject(()->NativeSaveImport.apply(corrupt),"Corrupt current phone progress rejected");check(Arrays.equals(invalid,Files.readAllBytes(corruptFile))&&backups(corrupt.directory)==0,"Corrupt phone file remains unchanged without a misleading backup");
        check(originalHash.equals(Binary.sha256(original.bytes))&&Binary.sha256(Files.readAllBytes(defaultPath)).equals(DEFAULT_HASH),"Synthetic selected source and copied native template remain unchanged");
    }
    @Override public void onCreate(Bundle args){super.onCreate(args);fixtureName=args==null?null:args.getString("fixture");if(fixtureName==null)fixtureName="ty-native-default-template-1848.dat";start();}
    @Override public void onStart(){Bundle result=new Bundle();try{
        if(!fixtureName.matches("[A-Za-z0-9_.-]+"))throw new IOException("App-private fixture filename required");Path files=getTargetContext().getFilesDir().toPath();output=Files.createTempDirectory(files,"ty-progress-import-audit-");runAudit(files.resolve(fixtureName));
        log.append("CHECKS ").append(checks).append("\nDEFAULT_SHA256 ").append(DEFAULT_HASH).append("\nSCOPE actual static native default template plus explicit synthetic1848-byte flag maps and independently recognized TY START/NAMES; production Android prepareFile/apply/atomic persistence; no original EXE execution, SAF provider, whole original-game resource or native story SAV/completion import claim\nOUTPUT ").append(output).append('\n');Files.write(output.resolve("result.txt"),log.toString().getBytes("UTF-8"));result.putString("stream",log.toString());finish(-1,result);
    }catch(Throwable failure){log.append("FAIL ").append(failure).append('\n');try{if(output!=null)Files.write(output.resolve("result.txt"),log.toString().getBytes("UTF-8"));}catch(Exception ignored){}result.putString("stream",log.toString());finish(0,result);}}
}
