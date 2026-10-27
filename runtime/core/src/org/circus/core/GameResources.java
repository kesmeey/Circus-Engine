package org.circus.core;

import java.io.*;
import java.util.*;

/** Platform-independent, read-only resources. Archive entry names stay byte-exact. */
public final class GameResources {
    public interface Storage {
        Set<String> paths();
        byte[] read(String path,int limit)throws IOException;
        Archive.Source source(String path)throws IOException;
    }
    private final Storage storage;
    private final Map<String,String> paths=new HashMap<>();
    /** Empty: legacy automatic discovery; @none: data-only; otherwise an exact user choice. */
    public final String executable;
    // Cache indices only; large decoded images and sounds do not accumulate here.
    private final LinkedHashMap<String,Archive> archives=new LinkedHashMap<String,Archive>(16,.75f,true){
        @Override protected boolean removeEldestEntry(Map.Entry<String,Archive> entry){return size()>8;}
    };
    public GameResources(Storage storage)throws IOException {this(storage,"");}
    public GameResources(Storage storage,String executable)throws IOException {
        this.storage=storage;
        for(String path:storage.paths()){
            String canonical=Binary.safePath(path),key=canonical.toLowerCase(Locale.ROOT);
            if(paths.put(key,path)!=null)throw new IOException("Case-colliding resource path "+canonical);
        }
        if(!paths.containsKey("advdata/mes/start.mes")){
            Set<String> roots=new HashSet<>();for(String key:paths.keySet())if(key.endsWith("/advdata/mes/start.mes"))roots.add(key.substring(0,key.length()-"advdata/mes/start.mes".length()));
            if(roots.size()>1)throw new IOException("目录包含多部游戏，请选择其中一部的目录");
            if(roots.size()==1){String prefix=roots.iterator().next();Map<String,String> child=new HashMap<>();for(Map.Entry<String,String> e:paths.entrySet())if(e.getKey().startsWith(prefix))child.put(e.getKey().substring(prefix.length()),e.getValue());paths.clear();paths.putAll(child);}
        }
        this.executable=executable==null?"":executable;
        if(explicitExecutable()){if(!this.executable.toLowerCase(Locale.ROOT).endsWith(".exe"))throw new IOException("请选择 EXE 文件");resolve(this.executable);}
    }
    public boolean explicitExecutable(){return !executable.isEmpty()&&!executable.equals("@none");}
    public List<String> executableCandidates(){if(executable.equals("@none"))return Collections.emptyList();if(explicitExecutable())return Collections.singletonList(executable);List<String> found=new ArrayList<>();for(String path:paths.keySet())if(path.endsWith(".exe"))found.add(path);Collections.sort(found);return found;}
    public String displayPath(String path)throws IOException{return resolve(path);}
    private String resolve(String path)throws IOException {
        String actual=paths.get(Binary.safePath(path).toLowerCase(Locale.ROOT));
        if(actual==null)throw new FileNotFoundException(path);return actual;
    }
    public byte[] read(String path,int limit)throws IOException{return storage.read(resolve(path),limit);}
    public Set<String> paths(){return Collections.unmodifiableSet(paths.keySet());}
    /** Small bounded reads for version inspection without loading archives or complete scripts. */
    public byte[] slice(String path,long offset,int length)throws IOException{
        if(length<0||length>8*1024*1024)throw new IOException("Resource inspection size limit");
        Archive.Source source=storage.source(resolve(path));Binary.range(offset,length,source.size());
        byte[] bytes=source.read(offset,length);if(bytes.length!=length)throw new EOFException("Short resource inspection");return bytes;
    }
    public long size(String path)throws IOException{return storage.source(resolve(path)).size();}
    /** Bounded streaming copy for large movies; never buffers the whole file. */
    public void copy(String path,OutputStream out,long limit)throws IOException {
        Archive.Source source=storage.source(resolve(path));long size=source.size();
        if(size<0||size>limit)throw new IOException("Resource exceeds copy limit: "+path);
        for(long offset=0;offset<size;){int count=(int)Math.min(1024*1024,size-offset);byte[] block=source.read(offset,count);if(block.length!=count)throw new EOFException("Short resource copy");out.write(block);offset+=count;}
    }
    public synchronized Archive archive(String path)throws IOException {
        String actual=resolve(path);Archive archive=archives.get(actual);
        if(archive==null){archive=new Archive(storage.source(actual));archives.put(actual,archive);}
        return archive;
    }
    public synchronized Archive legacyVoice(String path)throws IOException{
        String actual=resolve(path),key="voice64:"+actual;Archive a=archives.get(key);if(a==null){Archive.Source second=paths.containsKey("advdata/voice2.dat")?storage.source(resolve("AdvData/VOICE2.DAT")):null;a=new Archive(storage.source(actual),second);archives.put(key,a);}return a;
    }
    public byte[] entry(String path,byte[] name)throws IOException {
        Archive archive=archive(path);return archive.read(archive.find(name));
    }
    public Crx.Image image(String path)throws IOException{return Crx.plain(read(path,128*1024*1024));}
    public Crx.Image image(String path,byte[] name)throws IOException {
        Archive archive=archive(path);
        return imageAtOffset(path,archive.find(name).offset);
    }
    public Crx.Image imageAtOffset(String path,long offset)throws IOException {
        Archive archive=archive(path);
        Crx.Resolver resolver=new Crx.Resolver(){
            public byte[] byOffset(long offset)throws IOException{return archive.read(archive.atOffset(offset));}
            public byte[] byName(byte[] raw)throws IOException{return archive.read(archive.find(raw));}
        };
        return Crx.decode(archive.read(archive.atOffset(offset)),resolver);
    }
    public Xpcm audio(String path)throws IOException{return Xpcm.decode(read(path,128*1024*1024));}
    public Xpcm audio(String path,byte[] name)throws IOException{return Xpcm.decode(entry(path,name));}
    public NamesCatalog names(String path)throws IOException{return new NamesCatalog(read(path,32*1024*1024));}
    /** An explicit verified profile is required; this method grants no host compatibility. */
    public MesVm.Library scripts(String directory,Profile profile)throws IOException {
        final String prefix=Binary.safePath(directory)+"/";
        return name->{
            String relative=Binary.safePath(name);
            String filename=relative.toLowerCase(Locale.ROOT).endsWith(".mes")?relative:relative+".mes";
            return new MesScript(name,read(prefix+filename,64*1024*1024),profile);
        };
    }
}
