package org.circus.runtime;

import android.content.Context;
import android.net.Uri;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import org.circus.core.*;

/** Explicit, read-only original-save source; only merged progress is written privately. */
final class NativeSaveImport {
    static final String SOURCE="AdvData/SaveData/system.dat";
    private static final Object LOCK=new Object();
    static final class Preview {
        final CircusResources data;final TextCodec codec;final Path directory;
        final PcNativeProgress progress;final PcNativeProgress.Summary additions;
        final String sourceHash,identity;final int storySlots;
        Path legacyProgress;
        Preview(CircusResources data,TextCodec codec,Path directory,PcNativeProgress progress,String hash,int slots)throws IOException{
            this.data=data;this.codec=codec;this.directory=directory;this.progress=progress;sourceHash=hash;storySlots=slots;
            identity=DesktopEngine.mesIdentity(data);additions=progress.preview(readHost(this));
        }
        String description(){String action=progress.source.total==0?"原版系统存档中没有可导入的解锁记录。":additions.total==0?"手机已包含这些解锁记录，无需重复导入，不会修改文件。":"将合并已解锁进度，并在导入前备份手机现有系统进度。手机设置和已有剧情存档保留。";return "来源："+SOURCE+"\n\n"+counts(additions)+"\n\n"+action+"\n\n检测到 "+storySlots+" 个原版剧情存档槽位；此版本仅导入通关、CG 和场景解锁，不转换剧情槽位。";}
    }
    static final class Result {
        final PcNativeProgress.Summary added;final Path backup;
        Result(PcNativeProgress.Summary added,Path backup){this.added=added;this.backup=backup;}
        String description(){return (added.total==0?"手机已经包含这些进度，无需重复导入。":counts(added)+"\n\n重新启动游戏后可查看鉴赏和通关进度。")+(backup==null?"":"\n\n原手机系统进度已备份："+backup.getFileName());}
    }
    static String counts(PcNativeProgress.Summary s){return "新增通关标记："+s.completion+"\n新增 CG 标记："+s.cg+"\n新增场景标记："+s.scenes;}
    static Preview prepare(Context context,GameLibrary.Entry entry)throws IOException{
        if(!entry.configured)throw new IOException("请先保存此游戏的配置，再导入原版进度");
        if(!PcNativeProgress.supports(entry.profile))throw new IOException("当前原版进度导入仅支持 D.C.II P.C（5769），其他版本尚未适配");
        GameResources files=new GameResources(new DocumentTree(context.getContentResolver(),Uri.parse(entry.tree),true),entry.executable);
        Profile profile=GameVersion.detect(files).profile();
        if(!profile.id.equals(entry.profile))throw new IOException("游戏目录版本已改变，请重新配置游戏");
        TextCodec codec=GameText.select(files,profile,entry.encoding,entry.configuration);
        Preview preview=prepare(new CircusResources(files,profile),codec,new File(context.getFilesDir(),"shared-saves").toPath());
        preview.legacyProgress=new File(new GameSaves(context,entry.tree,profile.id).directory,"progress.dm").toPath();
        requireMigrated(preview.directory.resolve("progress.dat"),preview.legacyProgress);return preview;
    }
    /** Separate root allows instrumentation to exercise the shipping service without user saves. */
    static Preview prepare(CircusResources data,TextCodec codec,Path root)throws IOException{
        if(!PcNativeProgress.supports(data.profileId))throw new IOException("此版本不支持原版进度导入");
        if(!data.files.paths().contains(SOURCE.toLowerCase(Locale.ROOT)))throw new IOException("未找到 "+SOURCE+"。请将电脑上的整个 SaveData 文件夹复制到手机游戏目录的 AdvData 中，再重试。");
        byte[] raw=data.files.read(SOURCE,3312);PcNativeProgress parsed=PcNativeProgress.parse(data.profileId,raw);
        int slots=0;for(String path:data.files.paths())if(path.matches("advdata/savedata/dc2pc[0-9]+\\.dat"))slots++;
        synchronized(LOCK){return new Preview(data,codec,DesktopEngine.mesSaveDirectory(root,data),parsed,Binary.sha256(raw),slots);}
    }
    static PcHost readHost(Preview preview)throws IOException{
        PcHost host=new PcHost(preview.data,preview.codec);Path file=preview.directory.resolve("progress.dat");
        if(Files.exists(file))load(host,read(file));return host;
    }
    private static byte[] read(Path file)throws IOException{try(InputStream in=Files.newInputStream(file)){return Binary.read(in,16*1024*1024);}}
    static void requireMigrated(Path target,Path legacy)throws IOException{if(legacy!=null&&!Files.exists(target)&&Files.exists(legacy))throw new IOException("检测到尚未迁移的旧版手机进度。请先启动此游戏，再返回游戏列表，让旧进度迁移完成后重新导入。");}
    private static void load(PcHost host,byte[] bytes)throws IOException{try(DataInputStream in=new DataInputStream(new ByteArrayInputStream(bytes))){host.loadPersistent(in);if(in.available()!=0)throw new IOException("手机系统进度存在无法识别的数据，已保留原文件");}}
    static Result apply(Preview preview)throws IOException{
        synchronized(LOCK){
            requireMigrated(preview.directory.resolve("progress.dat"),preview.legacyProgress);
            if(!preview.identity.equals(DesktopEngine.mesIdentity(preview.data))||!preview.sourceHash.equals(Binary.sha256(preview.data.files.read(SOURCE,3312))))throw new IOException("预览后原文件发生变化，请重新打开导入页面");
            Path target=preview.directory.resolve("progress.dat");byte[] previous=Files.exists(target)?read(target):null;
            PcHost host=new PcHost(preview.data,preview.codec);if(previous!=null)load(host,previous);
            PcNativeProgress.Summary added=preview.progress.apply(host);if(added.total==0)return new Result(added,null);
            ByteArrayOutputStream bytes=new ByteArrayOutputStream();host.savePersistent(new DataOutputStream(bytes));byte[] merged=bytes.toByteArray();
            PcHost verified=new PcHost(preview.data,preview.codec);load(verified,merged);if(preview.progress.preview(verified).total!=0)throw new IOException("导入进度校验失败");
            Path backup=null;if(previous!=null){backup=preview.directory.resolve("progress.before-native-"+System.currentTimeMillis()+"-"+UUID.randomUUID()+".dat");DesktopEngine.atomic(backup,previous);}
            DesktopEngine.atomic(target,merged);return new Result(added,backup);
        }
    }
}
