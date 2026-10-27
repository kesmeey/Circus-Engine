package org.circus.core;
import java.io.*;
import java.nio.file.*;
import java.util.*;

/** Read-only validation against a local user's EXE. Never loads executable code. */
public final class NativeSelectionAudit {
    public static void main(String[] args)throws Exception{
        GameResources.Storage original=new LocalGameStorage(Paths.get(args[0]));GameResources base=new GameResources(original);Profile profile=GameVersion.detect(base).profile();
        String source=base.displayPath(args[1]),alias="custom_patch_name.exe";
        GameResources.Storage renamed=new GameResources.Storage(){
            public Set<String> paths(){Set<String> p=new HashSet<>(original.paths());p.add(alias);p.add("invalid.exe");return p;}
            public byte[] read(String p,int limit)throws IOException{return p.equals("invalid.exe")?new byte[64]:original.read(p.equals(alias)?source:p,limit);}
            public Archive.Source source(String p)throws IOException{if(p.equals("invalid.exe"))return new Archive.Source(){public long size(){return 64;}public byte[] read(long o,int n)throws IOException{Binary.range(o,n,64);return new byte[n];}};return original.source(p.equals(alias)?source:p);}
        };
        GameResources selected=new GameResources(renamed,alias);TextCodec codec=GameText.select(selected,profile,"auto","");ExecutableData.validate(new CircusResources(selected,profile),codec);
        boolean rejected=false;try{GameResources wrong=new GameResources(renamed,"invalid.exe");ExecutableData.validate(new CircusResources(wrong,profile),codec);}catch(IOException expected){rejected=true;}
        if(!rejected)throw new AssertionError("Invalid explicit EXE fell back to an available original");
        System.out.println("PASS "+profile.id+" renamed compatible EXE accepted; invalid explicit EXE rejected without fallback; original not executed");
    }
}
