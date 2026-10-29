package org.circus.runtime;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import org.circus.core.*;

/** Self-authored MES framing fixtures; no game scripts or external directory permissions. */
public final class GameVersionCacheTests {
    private static int assertions;
    private static final String TREE="content://synthetic/tree/game",START="advdata/mes/start.mes";
    private interface Action{void run()throws Exception;}
    private static void check(boolean value,String label){assertions++;if(!value)throw new AssertionError(label);}
    private static void reject(Action action,String label)throws Exception{try{action.run();throw new AssertionError(label);}catch(IOException expected){assertions++;}}
    private static byte[] script(int version){byte[] bytes=new byte[32];bytes[0]=1;bytes[10]=(byte)version;bytes[11]=(byte)(version>>>8);return bytes;}
    private static final class Fixture implements GameResources.Storage {
        final Map<String,byte[]> scripts=new TreeMap<>();final Map<String,Long> times=new HashMap<>();final Map<String,String> documents=new HashMap<>();final Set<String> opened=new HashSet<>();int sources;long bytes;
        Fixture(){put(START,script(0xa5a8));for(int i=0;i<7;i++)put("advdata/mes/story"+i+".mes",script(0xa5a8));}
        void put(String path,byte[] data){scripts.put(path,data);times.put(path,1000L);documents.put(path,"content://synthetic/document/"+path);}
        void reset(){opened.clear();sources=0;bytes=0;}
        String fingerprint(){StringBuilder text=new StringBuilder();for(String path:scripts.keySet()){Long time=times.get(path);if(time==null||time<=0)return null;text.append(path).append('\0').append(scripts.get(path).length).append('\0').append(time).append('\0').append(documents.get(path)).append('\n');}return Binary.sha256(text.toString().getBytes(StandardCharsets.UTF_8));}
        GameResources files()throws IOException{return new GameResources(this,"@none");}
        public Set<String> paths(){return new TreeSet<>(scripts.keySet());}
        public byte[] read(String path,int limit)throws IOException{throw new IOException("Version detection must not read whole scripts");}
        public Archive.Source source(String path)throws IOException{byte[] data=scripts.get(path);if(data==null)throw new FileNotFoundException(path);sources++;opened.add(path);return new Archive.Source(){public long size(){return data.length;}public byte[] read(long at,int length)throws IOException{Binary.range(at,length,data.length);if(length>8)throw new IOException("Unbounded version read");bytes+=length;return Arrays.copyOfRange(data,(int)at,(int)at+length);}};}
    }
    private static Profile detect(Fixture fixture,File cache)throws IOException{return GameVersionCache.detect(TREE,fixture.fingerprint(),fixture.files(),cache);}
    private static void full(Fixture fixture,File cache,String label)throws Exception{fixture.reset();check(detect(fixture,cache).id.equals("dc3dd-a5a8")&&fixture.opened.size()==fixture.scripts.size(),label);}
    private static void record(File file,String fingerprint,int count,String id,int version,int stride,int versionBytes)throws Exception{try(DataOutputStream out=new DataOutputStream(new FileOutputStream(file))){out.writeInt(0x47564331);out.writeUTF(fingerprint);out.writeInt(count);out.writeUTF(id);out.writeInt(version);out.writeInt(stride);out.writeInt(versionBytes);}}
    public static int run(File parent)throws Exception{
        assertions=0;Path root=Files.createTempDirectory(parent.toPath(),"mes-cache-test-");try{
            File cache=root.toFile();Fixture f=new Fixture();full(f,cache,"Cold detection validates every MES before writing cache");File record=GameVersionCache.cacheFile(cache,TREE);check(record.isFile(),"Successful collection check creates a versioned cache record");
            f.reset();check(detect(f,cache).id.equals("dc3dd-a5a8")&&f.sources==1&&f.opened.equals(Collections.singleton(START)),"Cache hit checks only START with bounded reads");check(f.bytes<=20,"Cache hit does not read START body");
            f.times.put("advdata/mes/story0.mes",2000L);full(f,cache,"Modified timestamp invalidates profile cache");
            f.documents.put("advdata/mes/story0.mes","content://synthetic/document/replacement");full(f,cache,"Replaced document URI invalidates profile cache");
            f.scripts.put("advdata/mes/story0.mes",Arrays.copyOf(f.scripts.get("advdata/mes/story0.mes"),33));full(f,cache,"Changed script size invalidates profile cache");
            f.put("advdata/mes/added.mes",script(0xa5a8));full(f,cache,"Added MES invalidates profile cache");f.scripts.remove("advdata/mes/added.mes");full(f,cache,"Removed MES invalidates profile cache");
            Files.write(record.toPath(),new byte[]{1,2,3});full(f,cache,"Truncated cache falls back to full detection");
            record(record,f.fingerprint(),f.scripts.size(),"unknown-profile",0xa5a8,6,3);full(f,cache,"Unknown cached profile is rejected");
            record(record,f.fingerprint(),f.scripts.size(),"dc3dd-a5a8",0xa5a8,4,3);full(f,cache,"Cached framing must match the registered profile");
            record(record,f.fingerprint(),f.scripts.size()+1,"dc3dd-a5a8",0xa5a8,6,3);full(f,cache,"Cached collection count must match the current inventory");
            Files.write(record.toPath(),new byte[]{99},StandardOpenOption.APPEND);full(f,cache,"Trailing cache bytes are rejected");
            f.reset();check(GameVersionCache.detect(TREE+"-other",f.fingerprint(),f.files(),cache).id.equals("dc3dd-a5a8")&&f.opened.size()==f.scripts.size(),"Different selected tree never reuses another tree's profile");
            f.times.put(START,0L);full(f,cache,"Unknown provider metadata disables caching");full(f,cache,"Unknown metadata remains uncached on repeated launch");f.times.put(START,3000L);full(f,cache,"Known metadata restores normal full admission");
            String old=f.fingerprint();for(String path:f.scripts.keySet())f.scripts.put(path,script(0x9d72));f.reset();check(GameVersionCache.detect(TREE,old,f.files(),cache).id.equals("dc2dm-9d72")&&f.opened.size()==f.scripts.size(),"START version check rejects a stale hint even when its fingerprint is reused");
            for(String path:f.scripts.keySet())f.scripts.put(path,script(0xa5a8));f.scripts.get(START)[8]=0x63;f.scripts.get(START)[9]=0x20;full(f,cache,"Collection can disambiguate START's two possible formats");full(f,cache,"Ambiguous START on cache hit falls back to collection validation");
            f.scripts.put("advdata/mes/story0.mes",script(0x9d72));reject(()->detect(f,cache),"Changed collection with mixed script versions remains rejected");f.scripts.put("advdata/mes/story0.mes",script(0xa5a8));
            File blocked=root.resolve("not-a-directory").toFile();Files.write(blocked.toPath(),new byte[]{1});full(f,blocked,"Cache write failure does not prevent valid profile detection");
            f.scripts.remove(START);reject(()->detect(f,cache),"Missing START is not hidden by an earlier cache");
            check(new File(cache,"shared-saves").exists()==false,"Cache never creates a save directory");
            return assertions;
        }finally{try(java.util.stream.Stream<Path> paths=Files.walk(root)){Path[] entries=paths.sorted(Comparator.reverseOrder()).toArray(Path[]::new);for(Path entry:entries){if(!entry.normalize().startsWith(root.normalize()))throw new IOException("Test cleanup escaped its temporary directory");Files.deleteIfExists(entry);}}}
    }
    public static void main(String[] args)throws Exception{System.out.println("PASS startup profile cache assertions="+run(Paths.get(args[0]).toFile()));}
}
