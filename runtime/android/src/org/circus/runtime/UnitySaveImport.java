package org.circus.runtime;

import android.content.Context;
import android.net.Uri;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import org.circus.core.*;

/** Read-only native sources, reviewed conversion, and private atomic destination writes. */
final class UnitySaveImport {
    private static final Object LOCK=new Object();
    private static final int INPUT_LIMIT=8*1024*1024,OUTPUT_LIMIT=16*1024*1024;
    private static final long TOTAL_LIMIT=256L*1024*1024;
    static final class Request {
        final UnityCircusResources data;final GameResources sources;final Path root,cache;final List<String> folders;
        final SelectedSaveFile.Source selectedSource;
        Request(UnityCircusResources data,GameResources sources,Path root,Path cache)throws IOException{
            this.data=data;this.sources=sources;this.root=root;this.cache=cache;selectedSource=null;SortedSet<String> found=new TreeSet<>();
            for(String path:sources.paths())if(nativeFile(path))found.add(parent(path));folders=new ArrayList<>(found);
            if(folders.isEmpty())throw new IOException("未找到 DC3R 原版存档。请把电脑 %APPDATA%\\Mangagamer\\DaCapo3R 中的 global.dat 和 saveNNN.dat 复制到手机，再选择存档所在文件夹。");
        }
        Request(UnityCircusResources data,SelectedSaveFile.Source source,Path root,Path cache){this.data=data;sources=null;selectedSource=source;this.root=root;this.cache=cache;folders=Collections.singletonList("");}
        Preview prepare(String folder)throws Exception{if(!folders.contains(folder))throw new IOException("无效存档目录");return selectedSource==null?UnitySaveImport.prepare(this,folder):prepareSelected(this);}
    }
    static final class Slot {
        final int number;final String source,sourceHash,convertedHash,caption;final Path converted;final boolean conflict;
        Slot(int number,String source,String hash,String caption,Path converted,boolean conflict)throws IOException{this.number=number;this.source=source;sourceHash=hash;this.caption=caption;this.converted=converted;this.conflict=conflict;convertedHash=Binary.sha256(read(converted,OUTPUT_LIMIT));}
        String label(){return number==101?"自动存档":"槽位 "+String.format(Locale.ROOT,"%03d",number);}
        String target(){return "slot-"+number+".sav";}
    }
    static final class Preview implements AutoCloseable {
        final Request request;final String folder,identity;final Path directory,temporary;
        final List<Slot> slots=new ArrayList<>();final List<String> rejected=new ArrayList<>();
        Dc3rNativeProgress progress;Dc3rNativeProgress.Summary additions;String globalSource,globalHash;int conflicts;boolean closed;
        Preview(Request r,String folder)throws IOException{request=r;this.folder=folder;identity=r.data.identity();directory=r.root.resolve(UnityCircusResources.ID+"-"+identity);Files.createDirectories(r.cache);temporary=Files.createTempDirectory(r.cache,"dc3r-import-");}
        boolean hasChanges(){return !slots.isEmpty()||additions!=null&&additions.total>0;}
        String description(){StringBuilder out=new StringBuilder("来源：").append(folder.isEmpty()?"所选文件夹":folder).append("\n\n");
            if(additions!=null)out.append(counts(additions)).append('\n');else out.append("未包含 global.dat；只导入剧情存档。\n");
            out.append("可转换剧情存档：").append(slots.size()).append(" 个；同号手机存档：").append(conflicts).append(" 个。\n");
            for(Slot slot:slots)out.append('\n').append(slot.label()).append(slot.conflict?"（已有手机存档）":"").append("：").append(slot.caption);
            if(!rejected.isEmpty()){out.append("\n\n以下文件未通过转换，将跳过：");for(String reason:rejected)out.append('\n').append(reason);}
            out.append("\n\n通关和鉴赏进度只合并增加，保留手机设置。原版文件不修改。已读文本记录和原版快速存档不导入；原版快速存档本身仅保存在 PC 运行内存中。");return out.toString();
        }
        public void close()throws IOException{if(closed)return;closed=true;try(DirectoryStream<Path> files=Files.newDirectoryStream(temporary)){for(Path file:files){if(!file.toAbsolutePath().normalize().getParent().equals(temporary.toAbsolutePath().normalize())||Files.isDirectory(file))throw new IOException("Invalid import staging path");Files.deleteIfExists(file);}}Files.deleteIfExists(temporary);}
    }
    static final class Result {
        int slots,skipped;Dc3rNativeProgress.Summary progress;Path backup;boolean progressOnly;
        String description(){String message=progressOnly?((progress==null||progress.total==0)?"所选文件没有可新增的通关或鉴赏进度。":counts(progress)+"\n\n重新启动游戏后可查看鉴赏和通关进度。")+"\n手机设置和剧情存档保持不变。":"已导入剧情存档："+slots+" 个\n保留同号手机存档："+skipped+" 个\n"+(progress==null?"":counts(progress)+"\n")+"\n启动游戏后，在原版 LOAD 界面读取对应槽位；save101.dat 对应自动存档。";return message+(backup==null?"":"\n\n替换前的手机文件已备份到："+backup.getFileName());}
    }
    static String counts(Dc3rNativeProgress.Summary s){return "新增或提高通关标记："+s.completion+"\n新增 CG 标记："+s.cg+"\n新增场景标记："+s.scenes+(s.music==0?"":"\n新增音乐标记："+s.music);}
    private static String parent(String path){int slash=path.lastIndexOf('/');return slash<0?"":path.substring(0,slash);}
    private static String basename(String path){return path.substring(path.lastIndexOf('/')+1);}
    private static boolean nativeFile(String path){String base=basename(path);return base.equals("global.dat")||base.matches("save(?:0[0-9]{2}|10[01])\\.dat");}
    static Request inspect(Context context,GameLibrary.Entry entry,Uri sourceTree)throws IOException{
        if(!entry.configured||!UnityCircusResources.ID.equals(entry.profile))throw new IOException("请先配置 DC3R Unity 游戏目录");
        GameResources files=new GameResources(new DocumentTree(context.getContentResolver(),Uri.parse(entry.tree)),"@none");
        GameResources sources=new GameResources(new DocumentTree(context.getContentResolver(),sourceTree==null?Uri.parse(entry.tree):sourceTree,true),"@none");
        return new Request(new UnityCircusResources(files),sources,new File(context.getFilesDir(),"shared-saves").toPath(),new File(context.getCacheDir(),"native-save-import").toPath());
    }
    /** Single-document progress import; the native file may have been renamed by its owner. */
    static Result importFile(Context context,GameLibrary.Entry entry,Uri uri)throws Exception{
        if(!entry.configured||!UnityCircusResources.ID.equals(entry.profile))throw new IOException("请先配置 DC3R Unity 游戏目录");
        SelectedSaveFile.Source source=SelectedSaveFile.open(context.getContentResolver(),uri);
        GameResources files=new GameResources(new DocumentTree(context.getContentResolver(),Uri.parse(entry.tree)),"@none");
        Preview preview=prepareFile(new UnityCircusResources(files),new File(context.getFilesDir(),"shared-saves").toPath(),new File(context.getCacheDir(),"native-save-import").toPath(),source);
        try{return apply(preview,false);}finally{try{preview.close();}catch(IOException cleanup){android.util.Log.w("CIRCUS","Cannot clear native import staging",cleanup);}}
    }
    /** Explicit roots allow the exact selected-file path to be tested away from user saves. */
    static Preview prepareFile(UnityCircusResources data,Path root,Path cache,SelectedSaveFile.Source source)throws IOException{
        return prepareSelected(new Request(data,source,root,cache));
    }
    private static Preview prepareSelected(Request request)throws IOException{
        // Reject wrong or oversized content before even creating a staging directory.
        byte[] raw=SelectedSaveFile.read(request.selectedSource,INPUT_LIMIT);Dc3rNativeProgress progress;
        try{progress=Dc3rNativeProgress.parse(raw,request.data);}catch(IOException failure){throw new IOException("所选文件不是此版本 DC3R 的有效原版系统进度。请选择原版 global.dat（允许改名）；剧情槽位请使用文件夹批量导入。\n"+failure.getMessage(),failure);}
        Preview preview=new Preview(request,"");try{preview.progress=progress;preview.globalSource=SelectedSaveFile.label(request.selectedSource.name());preview.globalHash=Binary.sha256(raw);preview.additions=progress.preview(target(preview));return preview;}
        catch(IOException|RuntimeException failure){try{preview.close();}catch(IOException cleanup){failure.addSuppressed(cleanup);}throw failure;}
    }
    static Dc3rHost target(Preview preview)throws IOException{
        Dc3rHost host=new Dc3rHost();host.configureCatalogues(new String(preview.request.data.script("cglist"),"UTF-8"),new String(preview.request.data.script("scenelist"),"UTF-8"));Path path=preview.directory.resolve("progress.dat");
        if(Files.exists(path)){try(DataInputStream in=new DataInputStream(new ByteArrayInputStream(read(path,OUTPUT_LIMIT)))){host.loadPersistent(in);if(in.available()!=0)throw new IOException("手机系统进度文件含有未识别数据");}}return host;
    }
    private static byte[] persistent(Dc3rHost host)throws IOException{ByteArrayOutputStream bytes=new ByteArrayOutputStream();host.savePersistent(new DataOutputStream(bytes));return bytes.toByteArray();}
    private static byte[] read(Path path,int limit)throws IOException{try(InputStream in=Files.newInputStream(path)){return Binary.read(in,limit);}}
    static Preview prepare(Request request,String folder)throws Exception{
        Preview p=new Preview(request,folder);try{
            Dc3rHost phone=target(p);String global=folder.isEmpty()?"global.dat":folder+"/global.dat";
            if(request.sources.paths().contains(global)){byte[] raw=request.sources.read(global,INPUT_LIMIT);p.progress=Dc3rNativeProgress.parse(raw,request.data);p.globalSource=global;p.globalHash=Binary.sha256(raw);p.additions=p.progress.preview(phone);p.progress.apply(phone);}
            byte[] progress=persistent(phone);long total=0;
            for(String path:new TreeSet<>(request.sources.paths()))if(parent(path).equals(folder)&&basename(path).matches("save(?:0[0-9]{2}|10[01])\\.dat")){
                int number=Integer.parseInt(basename(path).substring(4,7));try{
                    byte[] raw=request.sources.read(path,INPUT_LIMIT);DesktopSave converted=DesktopUnity.convertNative(request.data,raw,progress);byte[] bytes=converted.encode();
                    if(bytes.length>OUTPUT_LIMIT||total+bytes.length>TOTAL_LIMIT)throw new IOException("转换结果超过导入大小限制");total+=bytes.length;Path staged=p.temporary.resolve("slot-"+number+".sav");Files.write(staged,bytes);
                    boolean conflict=Files.exists(p.directory.resolve(staged.getFileName()));if(conflict)p.conflicts++;p.slots.add(new Slot(number,path,Binary.sha256(raw),converted.caption,staged,conflict));
                }catch(IOException ex){p.rejected.add(basename(path)+"："+ex.getMessage());}
            }
            return p;
        }catch(Exception failure){try{p.close();}catch(IOException cleanup){failure.addSuppressed(cleanup);}throw failure;}
    }
    static Result apply(Preview p,boolean replace)throws Exception{
        synchronized(LOCK){if(p.closed)throw new IOException("导入预览已关闭，请重新选择存档");if(!p.identity.equals(p.request.data.identity()))throw new IOException("游戏脚本已变化，请重新预览");
            if(p.progress!=null){byte[] raw=p.request.selectedSource==null?p.request.sources.read(p.globalSource,INPUT_LIMIT):SelectedSaveFile.read(p.request.selectedSource,INPUT_LIMIT);if(!p.globalHash.equals(Binary.sha256(raw)))throw new IOException("原版系统存档在导入前发生变化，请重新选择文件");}
            Result result=new Result();result.progressOnly=p.request.selectedSource!=null;Dc3rHost phone=target(p);Map<String,Path> writes=new LinkedHashMap<>();
            if(p.progress!=null){result.progress=p.progress.apply(phone);if(result.progress.total>0){Path staged=p.temporary.resolve("progress.dat");Files.write(staged,persistent(phone));writes.put("progress.dat",staged);}}
            for(Slot slot:p.slots){if(!slot.sourceHash.equals(Binary.sha256(p.request.sources.read(slot.source,INPUT_LIMIT))))throw new IOException("原版存档在预览后发生变化："+slot.source);
                if(Files.exists(p.directory.resolve(slot.target()))&&!replace){result.skipped++;continue;}
                byte[] bytes=read(slot.converted,OUTPUT_LIMIT);if(!slot.convertedHash.equals(Binary.sha256(bytes)))throw new IOException("待导入存档校验失败："+slot.source);DesktopSave.decode(bytes);writes.put(slot.target(),slot.converted);result.slots++;
            }
            if(writes.isEmpty())return result;
            Files.createDirectories(p.directory);Path backup=p.directory.resolve("before-native-import-"+System.currentTimeMillis()+"-"+UUID.randomUUID());Map<String,Boolean> existed=new LinkedHashMap<>();
            for(String name:writes.keySet()){Path file=p.directory.resolve(name);boolean exists=Files.exists(file);existed.put(name,exists);if(exists){Files.createDirectories(backup);Files.copy(file,backup.resolve(name));result.backup=backup;}}
            List<String> applied=new ArrayList<>();try{for(Map.Entry<String,Path> entry:writes.entrySet()){copyAtomic(entry.getValue(),p.directory.resolve(entry.getKey()));applied.add(entry.getKey());}}
            catch(Exception failure){for(String name:applied)try{if(existed.get(name))copyAtomic(backup.resolve(name),p.directory.resolve(name));else Files.deleteIfExists(p.directory.resolve(name));}catch(Exception rollback){failure.addSuppressed(rollback);}throw failure;}
            return result;
        }
    }
    private static void copyAtomic(Path source,Path target)throws IOException{
        Path temporary=target.resolveSibling(target.getFileName()+"."+UUID.randomUUID()+".tmp");try{Files.copy(source,temporary);try{Files.move(temporary,target,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}catch(AtomicMoveNotSupportedException ex){Files.move(temporary,target,StandardCopyOption.REPLACE_EXISTING);}}finally{Files.deleteIfExists(temporary);}
    }
}
