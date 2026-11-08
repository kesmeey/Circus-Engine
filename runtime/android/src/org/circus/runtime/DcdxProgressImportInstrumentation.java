package org.circus.runtime;

import android.app.Instrumentation;
import android.os.Bundle;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import org.circus.core.*;

/** Synthetic DCDX system.dat and real Android prepareFile/apply transactions.
 * Only reviewed CG/scene flags are progress; no original game/user save is used. */
public final class DcdxProgressImportInstrumentation extends Instrumentation {
    private static final String ID="dcdx-7769";
    private static final int PREFIX=2136,COMPLETE=6232,GLOBALS=0x74,CG=0xd8,CG_COUNT=640,SEEN=0x358,SEEN_COUNT=1024;
    private int checks;private Path output;private final StringBuilder log=new StringBuilder();
    private interface Attempt{void run()throws Exception;}
    private void check(boolean value,String label)throws IOException{if(!value)throw new IOException(label);checks++;log.append("PASS ").append(label).append('\n');}
    private void reject(Attempt action,String label)throws Exception{boolean failed=false;try{action.run();}catch(IOException expected){failed=true;}check(failed,label);}
    private static final class Source implements SelectedSaveFile.Source {
        byte[] bytes;Source(byte[] bytes){this.bytes=bytes.clone();}
        public String name(){return "renamed-DCDX-system.progress";}
        public byte[] read(int limit)throws IOException{if(bytes.length>limit)throw new IOException("Input exceeds limit "+limit);return bytes.clone();}
    }
    private static final class Fixture implements GameResources.Storage {
        final Map<String,byte[]> files=new TreeMap<>();
        Fixture(){byte[] names=new byte[208];names[16]=1;System.arraycopy("START".getBytes(Binary.SJIS),0,names,20,5);files.put("advdata/dat/names.dat",names);files.put("advdata/mes/start.mes",new byte[]{0,0,0,0,0x69,0x77,0,0,0});}
        public Set<String> paths(){return files.keySet();}
        private byte[] get(String path)throws IOException{byte[] b=files.get(path.toLowerCase(Locale.ROOT));if(b==null)throw new FileNotFoundException(path);return b;}
        public byte[] read(String path,int limit)throws IOException{byte[] b=get(path);if(b.length>limit)throw new IOException("Synthetic resource limit");return b.clone();}
        public Archive.Source source(String path)throws IOException{return new Archive.Source(){public long size()throws IOException{return get(path).length;}public byte[] read(long at,int n)throws IOException{byte[] b=get(path);Binary.range(at,n,b.length);return Arrays.copyOfRange(b,(int)at,(int)at+n);}};}
        CircusResources resources()throws IOException{GameResources data=new GameResources(this);Profile profile=GameVersion.detect(data).profile();if(!profile.id.equals(ID))throw new IOException("Synthetic START profile mismatch");return new CircusResources(data,profile);}
    }
    private static byte[] source(int length,boolean full){
        byte[] b=new byte[length];b[0x48]=0x77;Arrays.fill(b,GLOBALS,GLOBALS+100,(byte)255);Arrays.fill(b,0x758,PREFIX,(byte)255);
        if(full){Arrays.fill(b,CG,CG+CG_COUNT,(byte)1);Arrays.fill(b,SEEN,SEEN+SEEN_COUNT,(byte)1);b[SEEN+2]=2;b[SEEN+255]=(byte)255;}
        else{b[CG]=b[CG+CG_COUNT-1]=1;b[SEEN]=b[SEEN+SEEN_COUNT-1]=1;}
        // Opaque native read-position tail deliberately contains nonbinary,
        // nonmonotonic values. Import must not turn it into phone text history.
        if(length==COMPLETE)for(int n=0;n<119;n++){int value=(n*37%788)+15;for(int c=0;c<4;c++)b[PREFIX+n*4+c]=(byte)(value>>>(c*8));}
        return b;
    }
    private static int pcp3(byte[] b)throws IOException{for(int n=0;n+4<=b.length;n++)if(b[n]==0x50&&b[n+1]==0x43&&b[n+2]==0x50&&b[n+3]==0x33)return n;throw new IOException("Missing synthetic PCP3 owner");}
    private static int backups(Path directory)throws IOException{int n=0;try(DirectoryStream<Path> files=Files.newDirectoryStream(directory,"progress.before-native-*.dat")){for(Path ignored:files)n++;}return n;}
    private void format(int length)throws Exception{
        String label="DCDX"+length;Fixture fixture=new Fixture();CircusResources data=fixture.resources();TextCodec codec=TextCodec.identity();Source original=new Source(source(length,true));String originalHash=Binary.sha256(original.bytes);Path base=output.resolve(label);
        NativeSaveImport.Preview abandoned=NativeSaveImport.prepareFile(data,codec,base.resolve("abandoned"),original);
        check(abandoned.progress.source.completion==0&&abandoned.progress.source.music==0&&abandoned.progress.source.cg==CG_COUNT&&abandoned.progress.source.scenes==SEEN_COUNT,"All reviewed CG/scene bytes parsed, unreviewed globals/tail excluded "+label);
        check(!Files.exists(base.resolve("abandoned"))&&abandoned.storySlots==0,"Preview is read-only and does not enumerate original narrative slots "+label);
        check(abandoned.description().contains("DCDX 当前仅合并 CG 和场景已读标记")&&abandoned.description().contains("不导入通关状态"),"Preview explicitly states partial DCDX system-progress scope "+label);
        NativeSaveImport.Preview fresh=NativeSaveImport.prepareFile(data,codec,base.resolve("fresh"),original);NativeSaveImport.Result first=NativeSaveImport.apply(fresh);
        check(first.added.total==CG_COUNT+SEEN_COUNT&&first.backup==null&&fresh.progress.preview(NativeSaveImport.readTarget(fresh)).total==0,"Fresh production import writes all CG/seen flags "+label);

        Path phoneRoot=base.resolve("phone");NativeSaveImport.Preview preview=NativeSaveImport.prepareFile(data,codec,phoneRoot,original);NativeProgress.Target phone=NativeSaveImport.readTarget(preview);PcHost h=(PcHost)phone.legacy;h.setNativeNames("Own","Name");
        phone.write(99,7,37);phone.write(99,13,3);for(int n=0;n<100;n++)phone.write(100,n,(n*13+17)&255);for(int n=0;n<256;n++)phone.write(95,n,(n*11+23)&255);phone.write(97,7,2);phone.write(98,8,3);h.readCheckpoints.add("synthetic-DCDX-phone-text");
        byte[] before=phone.save();Path progress=preview.directory.resolve("progress.dat");DesktopEngine.atomic(progress,before);DesktopSave narrative=new DesktopSave();narrative.state=new Dc2Session(data,codec).vm.snapshot();narrative.caption="Existing DCDX CVS2 slot";byte[] narrativeBytes=narrative.encode();Path slot=preview.directory.resolve("slot-2.sav");DesktopEngine.atomic(slot,narrativeBytes);
        preview=NativeSaveImport.prepareFile(data,codec,phoneRoot,original);check(preview.additions.cg==CG_COUNT-1&&preview.additions.scenes==SEEN_COUNT-1&&preview.additions.completion==0,"Merge preview counts only missing flags "+label);
        check(Arrays.equals(before,Files.readAllBytes(progress))&&Arrays.equals(narrativeBytes,Files.readAllBytes(slot)),"Merge preview preserves existing phone files "+label);
        NativeSaveImport.Result applied=NativeSaveImport.apply(preview);check(applied.added.total==CG_COUNT+SEEN_COUNT-2&&applied.backup!=null&&Arrays.equals(before,Files.readAllBytes(applied.backup)),"Atomic merge creates exact previous-progress backup "+label);
        NativeProgress.Target after=NativeSaveImport.readTarget(preview);PcHost loaded=(PcHost)after.legacy;byte[] merged=after.save();
        check(Arrays.equals(Arrays.copyOf(before,pcp3(before)),Arrays.copyOf(merged,pcp3(merged))),"Complete parent settings/state prefix remains byte-identical "+label);
        check(loaded.familyName.equals("Own")&&loaded.givenName.equals("Name")&&after.read(99,7)==37&&after.read(99,13)==3&&loaded.readCheckpoints.equals(Collections.singleton("synthetic-DCDX-phone-text")),"Names/settings/existing text history retained without native tail import "+label);
        boolean globals=true,voice=true;for(int n=0;n<100;n++)globals&=after.read(100,n)==((n*13+17)&255);for(int n=0;n<256;n++)voice&=after.read(95,n)==((n*11+23)&255);
        check(globals&&voice,"All100 globals and256 voice-setting bytes preserved "+label);
        for(int n:new int[]{0,7,CG_COUNT-1})check(after.read(97,n)==(n==7?2:1),"CG native boundaries/nonzero value preserved "+label+":"+n);
        for(int n:new int[]{0,2,8,255,SEEN_COUNT-1})check(after.read(98,n)==(n==8?3:1),"Seen native boundaries/nonzero-source normalization/nonzero-target preservation "+label+":"+n);
        check(Arrays.equals(narrativeBytes,Files.readAllBytes(slot)),"Existing real CVS2/DCS2 narrative slot bytes unchanged "+label);
        NativeSaveImport.Result repeat=NativeSaveImport.apply(NativeSaveImport.prepareFile(data,codec,phoneRoot,original));check(repeat.added.total==0&&repeat.backup==null&&backups(preview.directory)==1&&Arrays.equals(merged,Files.readAllBytes(progress)),"Repeated full import is byte-identical and creates no backup "+label);
        NativeSaveImport.Result sparse=NativeSaveImport.apply(NativeSaveImport.prepareFile(data,codec,phoneRoot,new Source(source(length,false))));check(sparse.added.total==0&&Arrays.equals(merged,Files.readAllBytes(progress))&&Arrays.equals(narrativeBytes,Files.readAllBytes(slot)),"Sparse source cannot erase imported or phone narrative progress "+label);
        check(Binary.sha256(original.bytes).equals(originalHash),"Original selected source stays byte-identical "+label);

        Source changing=new Source(source(length,true));NativeSaveImport.Preview changed=NativeSaveImport.prepareFile(data,codec,base.resolve("changed-source"),changing);changing.bytes[0]^=1;reject(()->NativeSaveImport.apply(changed),"Source changed after preview rejected "+label);check(!Files.exists(changed.directory.resolve("progress.dat")),"Changed source makes no progress file "+label);
        Fixture changedGame=new Fixture();NativeSaveImport.Preview game=NativeSaveImport.prepareFile(changedGame.resources(),codec,base.resolve("changed-game"),original);changedGame.files.get("advdata/mes/start.mes")[6]^=1;reject(()->NativeSaveImport.apply(game),"Changed START identity rejected "+label);
        NativeSaveImport.Preview corrupt=NativeSaveImport.prepareFile(data,codec,base.resolve("corrupt-phone"),original);byte[] invalid={1,2,3};Path damaged=corrupt.directory.resolve("progress.dat");DesktopEngine.atomic(damaged,invalid);reject(()->NativeSaveImport.apply(corrupt),"Corrupt phone progress is not overwritten "+label);check(Arrays.equals(invalid,Files.readAllBytes(damaged))&&backups(corrupt.directory)==0,"Corrupt progress bytes retained without fake backup "+label);
        reject(()->NativeSaveImport.prepareFile(data,codec,base.resolve("wrong-game"),new Source(new byte[2032])),"FL source extent cannot be imported as DCDX "+label);
        reject(()->NativeSaveImport.prepareFile(data,codec,base.resolve("truncated"),new Source(Arrays.copyOf(original.bytes,PREFIX-1))),"Truncated native system prefix rejected "+label);
        reject(()->NativeSaveImport.prepareFile(data,codec,base.resolve("trailing"),new Source(Arrays.copyOf(original.bytes,COMPLETE+1))),"Bytes beyond native prefix/read-position maximum rejected "+label);
        byte[] wrongTag=original.bytes.clone();wrongTag[0x48]=0x78;reject(()->NativeSaveImport.prepareFile(data,codec,base.resolve("wrong-tag"),new Source(wrongTag)),"Different native system version tag rejected "+label);
        byte[] badCg=original.bytes.clone();badCg[CG+17]=2;reject(()->NativeSaveImport.prepareFile(data,codec,base.resolve("nonbinary-cg"),new Source(badCg)),"Native CG==1 gate does not admit nonbinary CG values "+label);
    }
    @Override public void onCreate(Bundle args){super.onCreate(args);start();}
    @Override public void onStart(){Bundle result=new Bundle();try{
        output=Files.createTempDirectory(getTargetContext().getFilesDir().toPath(),"dcdx-progress-import-audit-");check(NativeProgress.supports(ID),"Reviewed DCDX native progress profile admitted");
        check(NativeProgress.fileSize(ID)==COMPLETE,"Selected-file read cap includes complete4096-byte native read-position tail");format(PREFIX);format(COMPLETE);
        CircusResources data=new Fixture().resources();for(int length:new int[]{2137,4095,6231}){NativeSaveImport.Preview shortTail=NativeSaveImport.prepareFile(data,TextCodec.identity(),output.resolve("valid-short-tail"+length),new Source(source(length,true)));check(shortTail.additions.cg==CG_COUNT&&shortTail.additions.scenes==SEEN_COUNT&&!Files.exists(shortTail.directory),"Valid native short read-position tail does not discard CG/seen prefix "+length);}
        Fixture folder=new Fixture();folder.files.put("advdata/savedata/system.dat",source(COMPLETE,true));byte[] nativeSlot={3,1,4,1,5};folder.files.put("advdata/savedata/dcdx01.dat",nativeSlot.clone());folder.files.put("advdata/savedata/dc2fl01.dat",new byte[]{9});CircusResources folderData=folder.resources();NativeSaveImport.Preview directory=NativeSaveImport.prepare(folderData,TextCodec.identity(),output.resolve("folder"));
        check(directory.storySlots==1&&directory.description().contains("同目录检测到 1 个原版剧情存档槽位"),"Verified native DCDX prefix counts its own original slot without conversion");NativeSaveImport.apply(directory);check(Arrays.equals(nativeSlot,folder.files.get("advdata/savedata/dcdx01.dat")),"Folder service never rewrites original narrative slot bytes");
        log.append("CHECKS ").append(checks).append("\nSCOPE synthetic DCDX START/NAMES/system.dat; production Android prepareFile/apply and atomic persistence; no SAF provider/original-game assets/user shared-save root; CG and scene-seen only, no completion/native SAV/read-position import\nOUTPUT ").append(output).append('\n');Files.write(output.resolve("result.txt"),log.toString().getBytes("UTF-8"));result.putString("stream",log.toString());finish(-1,result);
    }catch(Throwable error){log.append("FAIL ").append(error).append('\n');try{if(output!=null)Files.write(output.resolve("result.txt"),log.toString().getBytes("UTF-8"));}catch(Exception ignored){}result.putString("stream",log.toString());finish(0,result);}}
}
