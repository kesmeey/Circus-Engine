package org.circus.runtime;

import android.content.Context;
import java.io.*;
import java.util.Arrays;

/** Isolated platform-save tests. No user save slot or game data is touched. */
final class GameSavesTest {
    static void run(Context context)throws Exception {
        GameSaves store=new GameSaves(context,"circus-test://save-envelope-v2");
        try {
            GameSaves.Save original=new GameSaves.Save();original.script="SYNTHETIC";original.when=1234;original.ticket=17;original.page=2;original.revealed=true;original.revealTime=456;original.movieMillis=90000;original.audio=new int[]{3,4,5,6,7,8};original.core=new byte[]{0,1,2,-1};
            store.write(1,original);GameSaves.Save restored=store.read(1);
            check(restored.script.equals(original.script)&&restored.when==1234&&restored.ticket==17&&restored.page==2&&restored.revealed&&restored.revealTime==456&&restored.movieMillis==90000&&Arrays.equals(restored.audio,original.audio)&&Arrays.equals(restored.core,original.core),"CAS2 presentation/media round trip");
            // A failed replacement must keep the last committed save.
            android.util.AtomicFile atomic=new android.util.AtomicFile(store.file(1));FileOutputStream interrupted=atomic.startWrite();interrupted.write(7);atomic.failWrite(interrupted);
            check(Arrays.equals(store.read(1).core,original.core),"Atomic replacement rollback");
            try(DataOutputStream out=new DataOutputStream(new FileOutputStream(store.file(2)))) {
                out.writeInt(0x43415331);out.writeUTF("SYNTHETIC");out.writeLong(1);out.writeLong(2);out.writeInt(0);out.writeBoolean(false);out.writeLong(3);for(int i=0;i<6;i++)out.writeInt(0);out.writeInt(1);out.writeByte(9);
            }
            check(store.read(2).movieMillis==0&&store.read(2).core[0]==9,"CAS1 compatibility");
            try(FileOutputStream out=new FileOutputStream(store.file(2),true)){out.write(1);}
            boolean rejected=false;try{store.read(2);}catch(IOException expected){rejected=true;}check(rejected,"Malformed trailing data rejection");
        } finally {
            for(int slot:new int[]{1,2})new android.util.AtomicFile(store.file(slot)).delete();
            store.directory.delete(); // Empty, dedicated test directory only.
        }
    }
    private static void check(boolean ok,String name){if(!ok)throw new AssertionError(name);}
}
