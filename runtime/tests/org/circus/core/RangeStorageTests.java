package org.circus.core;

import java.io.*;
import java.nio.file.*;
import java.util.*;

/** Synthetic disk-backed range-pack checks; callers supply a disposable test directory. */
public final class RangeStorageTests {
    private static int assertions;private interface Action{void run()throws Exception;}
    private static void check(boolean value,String message){assertions++;if(!value)throw new AssertionError(message);}
    private static void reject(Action action,String message)throws Exception{try{action.run();throw new AssertionError(message);}catch(IOException expected){assertions++;}}
    public static int run(Path directory)throws Exception{
        assertions=0;Files.createDirectories(directory);Path pack=directory.resolve("synthetic-ranges.pack"),bad=directory.resolve("synthetic-ranges-truncated.pack");final byte[] data=new byte[64];for(int i=0;i<data.length;i++)data[i]=(byte)i;
        GameResources.Storage source=new GameResources.Storage(){public Set<String> paths(){return new HashSet<>(Arrays.asList("a.bin","unvisited.bin"));}public byte[] read(String name,int limit)throws IOException{if(limit<data.length)throw new IOException("limit");return data.clone();}public Archive.Source source(String name){return new Archive.Source(){public long size(){return data.length;}public byte[] read(long at,int length)throws IOException{Binary.range(at,length,data.length);return Arrays.copyOfRange(data,(int)at,(int)at+length);}};}};
        try{
            RangeStorage recording=RangeStorage.recording(source);recording.source("a.bin").read(2,4);recording.source("a.bin").read(6,4);recording.source("a.bin").read(5,3);check(recording.recordedBytes()==8,"Overlapping ranges deduplicated");recording.write(pack);RangeStorage replay=RangeStorage.replay(pack);check(replay.paths().equals(source.paths()),"Original path list retained");check(replay.source("a.bin").size()==64,"Original full resource size retained");check(Arrays.equals(replay.source("a.bin").read(2,8),Arrays.copyOfRange(data,2,10)),"Cross-span replay exact bytes");check(replay.recordedBytes()==8,"Replay byte accounting");
            reject(()->replay.source("a.bin").read(1,2),"Leading unrecorded byte rejected");reject(()->replay.source("a.bin").read(9,2),"Trailing unrecorded byte rejected");reject(()->replay.source("unvisited.bin").size(),"Unobserved size rejected");reject(()->replay.source("a.bin").read(64,1),"Original file bounds enforced");reject(()->replay.read("a.bin",63),"Full read limit enforced");
            data[6]=99;reject(()->recording.source("a.bin").read(6,1),"Changed bytes rejected during capture");byte[] bytes=Files.readAllBytes(pack);Files.write(bad,Arrays.copyOf(bytes,bytes.length-1));reject(()->RangeStorage.replay(bad),"Truncated range pack rejected");
        }finally{Files.deleteIfExists(pack);Files.deleteIfExists(bad);}
        return assertions;
    }
    public static void main(String[] args)throws Exception{if(args.length!=1)throw new IllegalArgumentException("RangeStorageTests TEMP_DIRECTORY");System.out.println("PASS range storage assertions="+run(Paths.get(args[0])));}
}
