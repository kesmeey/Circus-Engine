package org.circus.runtime;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import org.circus.core.*;

/** Reuses a completed collection check only while the provider's script inventory is unchanged. */
public final class GameVersionCache {
    private static final int MAGIC=0x47564331,MAX_RECORD=2048;
    // Bump this directory when collection-admission rules change, not for UI releases.
    private static final String START="advdata/mes/start.mes",DIRECTORY="mes-version-v1";
    private GameVersionCache(){}

    public static Profile detect(DocumentTree tree,GameResources files,File cacheDir)throws IOException{
        return detect(tree.treeIdentity(),tree.scriptFingerprint(files),files,cacheDir);
    }

    // Same path used by the device's synthetic-storage tests, without granting access
    // to application saves or requiring a particular document-provider implementation.
    static Profile detect(String identity,String fingerprint,GameResources files,File cacheDir)throws IOException{
        int count=0;for(String path:files.paths())if(path.startsWith("advdata/mes/")&&path.endsWith(".mes"))count++;
        boolean usable=identity!=null&&!identity.isEmpty()&&fingerprint!=null&&fingerprint.matches("[0-9a-f]{64}")&&count>0&&count<=100000&&files.paths().contains(START)&&cacheDir!=null;
        File cache=usable?cacheFile(cacheDir,identity):null;
        Profile cached=usable?read(cache,fingerprint,count):null;
        if(cached!=null){
            // Provider timestamps are freshness hints, not content authentication.
            // Reuse the authoritative parser to check START with bounded reads; an
            // ambiguous or changed START falls back to full collection validation.
            try{GameResources first=new GameResources(new GameResources.Storage(){
                public Set<String> paths(){return Collections.singleton(START);}
                public byte[] read(String name,int limit)throws IOException{return files.read(START,limit);}
                public Archive.Source source(String name)throws IOException{return files.source(START);}
            },"@none");if(GameVersion.detect(first).profileId.equals(cached.id))return cached;}catch(IOException ignored){}
        }
        GameVersion version=GameVersion.detect(files);Profile profile=version.profile();
        if(usable&&version.scriptCount==count)write(cache,fingerprint,count,profile);
        return profile;
    }

    static File cacheFile(File cacheDir,String identity){return new File(new File(cacheDir,DIRECTORY),Binary.sha256(identity.getBytes(StandardCharsets.UTF_8))+".bin");}
    private static Profile read(File file,String fingerprint,int count){
        try{if(!file.isFile()||file.length()<32||file.length()>MAX_RECORD)return null;try(DataInputStream in=new DataInputStream(new BufferedInputStream(new FileInputStream(file)))){
            if(in.readInt()!=MAGIC||!in.readUTF().equals(fingerprint)||in.readInt()!=count)return null;
            String id=in.readUTF();if(!id.matches("[a-z0-9][a-z0-9-]{0,63}"))return null;Profile profile=Profiles.get(id);
            if(profile.demo||in.readInt()!=profile.version||in.readInt()!=profile.headerStride||in.readInt()!=profile.versionBytes||in.read()!=-1)return null;return profile;
        }}catch(IOException|IllegalArgumentException|SecurityException ignored){return null;}
    }
    private static void write(File file,String fingerprint,int count,Profile profile){
        Path temporary=null;try{
            File parent=file.getParentFile();if(!parent.isDirectory()&&!parent.mkdirs())return;
            temporary=Files.createTempFile(parent.toPath(),"profile-",".tmp");
            try(FileOutputStream stream=new FileOutputStream(temporary.toFile());DataOutputStream out=new DataOutputStream(stream)){
                out.writeInt(MAGIC);out.writeUTF(fingerprint);out.writeInt(count);out.writeUTF(profile.id);out.writeInt(profile.version);out.writeInt(profile.headerStride);out.writeInt(profile.versionBytes);out.flush();stream.getFD().sync();
            }
            Files.move(temporary,file.toPath(),StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);temporary=null;
        }catch(IOException|SecurityException ignored){
            // Caching is optional. A read-only/full cache directory must never prevent play.
        }finally{if(temporary!=null)try{Files.deleteIfExists(temporary);}catch(IOException|SecurityException ignored){}}
    }
}
