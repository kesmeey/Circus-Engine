package org.circus.core;

import java.io.*;
import java.nio.file.*;
import java.util.*;

/** Exercises only caller-supplied generated fixtures under a runtime-owned
 *directory. Never opens a game installation as a writable source. */
public final class DesktopNativeFilesAudit {
    private static int checks;
    private static void check(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
    private interface Work{void run()throws Exception;}
    private static void reject(Work action)throws Exception{boolean rejected=false;try{action.run();}catch(IOException expected){rejected=true;}check(rejected,"Outside/unsupported operation rejects before mutation");}
    public static void main(String[] args)throws Exception{
        if(args.length!=1)throw new IOException("Usage: DesktopNativeFilesAudit <generated-fixture-directory>");
        Path root=Paths.get(args[0]).toAbsolutePath().normalize();Files.createDirectories(root);
        try(DesktopNativeFiles io=new DesktopNativeFiles(root)){
            check(io.open("absent-native-settings.dat",0x8000,0)==-1,"Missing file returns native open failure");
            int fd=io.open("nested/settings.dat",0x8301,0x180);check(fd>=3,"Native writer acquires descriptor");
            byte[] data={1,2,3,4,5,6};check(io.write(fd,data,4)==4,"Write uses requested prefix");check(io.read(fd,new byte[1],1)==-1,"Read of write-only descriptor fails");check(io.close(fd)==0&&io.close(fd)==-1,"Descriptor closes once");
            check(Arrays.equals(Files.readAllBytes(root.resolve("nested/settings.dat")),new byte[]{1,2,3,4}),"Requested bytes written under owned directory");
            fd=io.open("nested/settings.dat",0x8000,0);byte[] destination=new byte[8];Arrays.fill(destination,(byte)0x7b);
            check(io.read(fd,destination,8)==4&&destination[4]==0x7b,"Short read preserves unread buffer bytes");check(io.read(fd,destination,8)==0,"CRT EOF is0, not Java-1");check(io.write(fd,data,1)==-1,"Write of read-only descriptor fails");check(io.close(fd)==0,"Reader closes");
            fd=io.open("nested/settings.dat",0x8301,0x180);check(io.write(fd,data,1)==1&&io.close(fd)==0&&Files.size(root.resolve("nested/settings.dat"))==1,"Native truncate flag replaces prior length");
            reject(()->io.open("../outside.dat",0x8301,0x180));reject(()->io.open(root.getParent().resolve("outside.dat").toString(),0x8301,0x180));reject(()->io.open("settings.dat",3,0));reject(()->io.write(7,new byte[2],3));
            Pc1NativeSettings value=Pc1NativeSettings.currentChsDefaults();Pc1NativeSettings.WriteResult written=value.nativeWrite(io,"system.dat");
            check(written.nativeResult==0&&written.fullCountAndCloseSucceeded,"Actual native settings writer service uses desktop adapter");
            Pc1NativeSettings.ReadResult read=Pc1NativeSettings.nativeRead(io,"system.dat",Pc1NativeSettings.initialZeroImage());
            check(!read.defaulted&&Arrays.equals(read.settings.nativeBytes(),value.nativeBytes()),"Actual native1748-byte read/write roundtrip");
            Pc1NativeFrame candidate=Pc1NativeFrame.initializedDefault();Pc1StorySlotIo.WriteResult slot=Pc1StorySlotIo.write(io,"dcpc07.dat",candidate,frame->frame.writeOrdinary(0,0,93),()->value.nativeWrite(io,"system.dat").nativeResult);
            check(slot.fullCountAndCloseSucceeded&&Files.size(root.resolve("dcpc07.dat"))==8004,"Actual story writer preserves native complete size and settings prerequisite");
            Pc1NativeFrame work=Pc1NativeFrame.zeroBss(),suspended=Pc1NativeFrame.zeroBss();int[] rebuilt={0};
            Pc1StorySlotIo.ReadResult loaded=Pc1StorySlotIo.read(io,"dcpc07.dat",work,suspended,()->{rebuilt[0]++;return 17;});
            check(loaded.admitted&&loaded.nativeResult==17&&rebuilt[0]==1&&suspended.readOrdinary(0,0)==93,"Admitted original-shape bytes require explicit reconstruction and preserve callback result");
        }
        System.out.println("PASS DesktopNativeFilesAudit "+checks);
    }
}
