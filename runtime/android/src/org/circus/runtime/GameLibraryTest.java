package org.circus.runtime;
import android.content.Context;
import android.util.AtomicFile;
import java.io.*;
import java.nio.file.*;

final class GameLibraryTest {
    private static void check(boolean b,String reason){if(!b)throw new AssertionError(reason);}
    static void run(Context context)throws Exception{
        File file=File.createTempFile("library-test-",".json",context.getCacheDir());file.delete();
        try{GameLibrary a=new GameLibrary(file);GameLibrary.Entry dd=a.add("content://example/tree/primary%3ADD"),dm=a.add("content://example/tree/primary%3ADM");dd.executable="Custom_CHS.exe";dd.encoding="GBK";dd.configuration="custom.json";dd.profile="dc3dd-a5a8";a.configure(dd);a.opened(dm.id);
            GameLibrary b=new GameLibrary(file);check(b.entries().size()==2&&b.entries().get(0).id.equals(dm.id),"persistent recent order");GameLibrary.Entry saved=b.get(dd.id);check(saved.configured&&saved.executable.equals("Custom_CHS.exe")&&saved.encoding.equals("GBK")&&saved.configuration.equals("custom.json"),"persistent explicit choices");
            check(b.get(dm.id).executable.isEmpty()&&b.get(dm.id).encoding.equals("auto"),"per-game settings isolation");check(b.add(dd.tree).id.equals(dd.id)&&b.entries().size()==2,"reselecting a directory deduplicates");
            a.opened(dm.id);saved.encoding="windows-31j";b.configure(saved);check(b.get(dm.id).opened>0,"independent library instances do not overwrite recent updates");
            AtomicFile atomic=new AtomicFile(file);FileOutputStream pending=atomic.startWrite();pending.write(new byte[]{0});atomic.failWrite(pending);check(new GameLibrary(file).entries().size()==2,"failed write rolls back");
            b.relink(dd.id,"content://example/tree/primary%3ARenamed");check(!b.get(dd.id).configured&&b.get(dd.id).executable.equals("Custom_CHS.exe"),"relink retains choices and requires revalidation");b.remove(dm.id);check(new GameLibrary(file).entries().size()==1,"remove one entry only");
            Files.write(file.toPath(),new byte[]{0});boolean rejected=false;try{b.entries();}catch(IOException expected){rejected=true;}check(rejected&&Files.size(file.toPath())==1,"corrupt list not silently replaced");
        }finally{new AtomicFile(file).delete();}
    }
}
