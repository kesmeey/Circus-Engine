package org.circus.core;
import java.io.*;
import java.util.Arrays;
/** Stable canonical header through the complete Runtime codec. */
public final class Pc1WindowRuntimeTests {
    public static int run()throws Exception{
        PcHost host=Pc1SaveContextTests.fixture(0);Pc1Runtime runtime=host.pc1;Pc1WindowMemory owner=runtime.windowMemory();Pc1NativeMemory bus=runtime.nativeMemory();
        for(int n=0;n<40;n++)bus.writeAddress(Pc1WindowMemory.BITMAP_INFO+n,n*37+11);
        ByteArrayOutputStream out=new ByteArrayOutputStream();runtime.save(new DataOutputStream(out));byte[] saved=out.toByteArray();for(int n=0;n<40;n++)bus.writeAddress(Pc1WindowMemory.BITMAP_INFO+n,0);runtime.load(new DataInputStream(new ByteArrayInputStream(saved)));
        if(owner!=runtime.windowMemory()||owner.nativeMemory()!=bus)throw new AssertionError("Runtime preserves header owner and bus identity");
        for(int n=0;n<40;n++)if(bus.readAddress(Pc1WindowMemory.BITMAP_INFO+n)!=((n*37+11)&255))throw new AssertionError("header bytes restored through canonical bus");out.reset();runtime.save(new DataOutputStream(out));if(!Arrays.equals(saved,out.toByteArray()))throw new AssertionError("P1RX window bytes round-trip without duplicate header state");return 2;
    }
    public static void main(String[] args)throws Exception{System.out.println("PASS "+run()+" Runtime window owner checks");}
}
