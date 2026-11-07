package org.circus.core;
import java.io.*;
import java.nio.*;
import java.util.*;

/**One live PE backing for addressed core access, native DSP and snapshot. */
public final class Pc1PeDirectStorageTests {
    private static int checks;
    private static void check(boolean yes,String why){checks++;if(!yes)throw new AssertionError(why);}
    private static byte[] save(PcHost host)throws IOException{ByteArrayOutputStream out=new ByteArrayOutputStream();host.save(new DataOutputStream(out));return out.toByteArray();}
    public static int run()throws Exception{
        checks=0;PcHost host=Pc1SaveContextTests.fixture(0);Pc1EffectPeMemory pe=host.pc1.effectPeMemory();Pc1GraphicsMemory bus=host.pc1.graphicsMemory();ByteBuffer table=pe.borrowedDirectView(0x42ab20,64),rows=pe.borrowedDirectView(0x42ab60,16*64);
        check(table.isDirect()&&!table.isReadOnly()&&table.capacity()==64&&rows.capacity()==1024&&table.order()==ByteOrder.LITTLE_ENDIAN,"native DSP borrows exact contiguous direct little-endian table extents");bus.writeInt(0x42ab20,0x12345678);check(table.getInt(0)==0x12345678,"canonical bus write reaches borrowed native table without upload");table.putInt(60,0x87654321);check(bus.readInt(0x42ab5c)==0x87654321,"borrowed table tail write reaches bus immediately");rows.putInt(1020,0xabcdef01);check(bus.readInt(0x42af5c)==0xabcdef01,"last potential16th coefficient row has the same backing");
        ByteBuffer overlap=pe.borrowedDirectView(0x42ab24,60);overlap.putInt(0,91);check(table.getInt(4)==91,"overlapping direct views alias the one owner rather than snapshots");table.position(11);pe.borrowedDirectView(0x42ab20,64).putInt(0,73);check(table.position()==11&&table.getInt(0)==73,"borrowed view cursors are independent while bytes remain shared");
        byte[] before=save(host);table.putInt(0,7);rows.putInt(1020,8);host.load(new DataInputStream(new ByteArrayInputStream(before)));check(table.getInt(0)==73&&rows.getInt(1020)==0xabcdef01&&Arrays.equals(before,save(host)),"whole Host restore mutates the same direct storage; existing native views remain bound");
        for(int[] range:new int[][]{{0x427ca0,4},{0x42a02c,8},{0x42a054,8},{0x42fca4,8},{0x42ab20,0}}){boolean rejected=false;try{pe.borrowedDirectView(range[0],range[1]);}catch(IOException expected){rejected=true;}check(rejected,"direct exports cannot bridge read-only, independently owned or unknown bytes");}
        return checks;
    }
    public static void main(String[] args)throws Exception{System.out.println("PASS "+run()+" live PE direct-storage checks");}
}
