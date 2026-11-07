package org.circus.core;

import java.io.*;
import java.util.*;

/** Version admission counterexamples: small native STOP files and collection-wide checks. */
public final class NativeGameVersionTests {
    private static int count;
    private static void check(boolean value,String message){count++;if(!value)throw new AssertionError(message);}
    private static byte[] tiny(int version){return new byte[]{0,0,0,0,(byte)version,(byte)(version>>8),0};}
    private static final class Memory implements GameResources.Storage {
        final Map<String,byte[]> files=new TreeMap<>();
        final Map<String,Integer> opens=new HashMap<>(),closes=new HashMap<>();
        int largestRead;
        public Set<String> paths(){return files.keySet();}
        public byte[] read(String path,int limit)throws IOException{throw new IOException("Admission must not read complete scripts");}
        public Archive.Source source(final String path)throws IOException{
            final byte[] data=files.get(path);if(data==null)throw new FileNotFoundException(path);
            return new Archive.Source(){
                public long size(){return data.length;}
                public byte[] read(long offset,int length)throws IOException{
                    largestRead=Math.max(largestRead,length);Binary.range(offset,length,data.length);
                    return Arrays.copyOfRange(data,(int)offset,(int)offset+length);
                }
                public Archive.Reader openReader(){
                    opens.put(path,opens.getOrDefault(path,0)+1);final Archive.Source source=this;
                    return new Archive.Reader(){
                        public long size()throws IOException{return source.size();}
                        public byte[] read(long offset,int length)throws IOException{return source.read(offset,length);}
                        public void close(){closes.put(path,closes.getOrDefault(path,0)+1);}
                    };
                }
            };
        }
    }
    private static boolean rejected(Memory memory)throws IOException{
        try{GameVersion.detect(new GameResources(memory,"@none"));return false;}catch(IOException expected){return true;}
    }
    public static int run()throws Exception{
        count=0;
        for(String id:new String[]{"dcpc-2063","dc2pc-5769","dc2-3363"}){
            Profile profile=Profiles.get(id);Memory memory=new Memory();memory.files.put("AdvData/MES/START.mes",tiny(profile.version));
            GameVersion found=GameVersion.detect(new GameResources(memory,"@none"));
            check(found.profileId.equals(id)&&found.scriptCount==1,id+" admits a six-byte header and single STOP");
            check(memory.opens.equals(memory.closes)&&memory.opens.get("AdvData/MES/START.mes")==1,"One bounded provider handle is closed for each script");
            check(memory.largestRead<=4,"Admission reads only count and marker ranges");
        }
        for(int size=0;size<=6;size++){
            Memory memory=new Memory();memory.files.put("AdvData/MES/START.mes",Arrays.copyOf(tiny(0x3363),size));
            check(rejected(memory),"Incomplete count/header or header without code must be rejected: "+size);
            check(memory.opens.equals(memory.closes),"Even truncated header closes its provider handle");
        }
        Memory labels=new Memory();labels.files.put("AdvData/MES/START.mes",new byte[]{1,0,0,0,0,0,0,0,0x63});
        check(rejected(labels),"A count cannot move its version marker past the available table");
        Memory excessive=new Memory();excessive.files.put("AdvData/MES/START.mes",new byte[]{0x41,0x42,0x0f,0,0x63,0x33,0});
        check(rejected(excessive),"Label counts above the resource limit must fail before allocation");
        Memory collection=new Memory();
        collection.files.put("AdvData/MES/START.mes",tiny(0x3363));collection.files.put("AdvData/MES/last.mes",tiny(0x3363));
        GameResources resources=new GameResources(collection,"@none");
        check(GameVersion.detect(resources).scriptCount==2,"The entire collection is admitted");
        check(collection.opens.size()==2,"Every MES is inspected, not just START");
        collection.files.put("AdvData/MES/last.mes",tiny(0x5769));
        boolean mixed=false;try{GameVersion.detect(resources);}catch(IOException expected){mixed=true;}
        check(mixed,"Changed mixed-version data must not inherit an earlier successful admission");
        check(collection.opens.get("AdvData/MES/last.mes")==2,"Later admission reopens changed files");
        Memory alternatives=new Memory();alternatives.files.put("AdvData/MES/START.mes",tiny(0x3363));
        alternatives.files.put("AdvData/MES/kaiso_menu.mes",tiny(0x1a63));alternatives.files.put("AdvData/MES/title.mes",tiny(0x3163));
        check(GameVersion.detect(new GameResources(alternatives,"@none")).scriptCount==3,
              "Structural stamp recognition remains distinct from stricter native runtime loader admission");
        return count;
    }
    public static void main(String[] args)throws Exception{System.out.println("PASS NativeGameVersionTests assertions="+run());}
}
