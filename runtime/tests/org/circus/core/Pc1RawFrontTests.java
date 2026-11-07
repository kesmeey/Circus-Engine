package org.circus.core;

import java.io.*;
import java.util.Arrays;

/** Raw401000 publication, including retained HWND/NULL after failed DIB.
 * Store order and DWORD formula follow the original21-instruction body. */
public final class Pc1RawFrontTests {
    private static int checks;
    private static void check(boolean yes,String why){checks++;if(!yes)throw new AssertionError(why);}
    private static byte[] wire(Pc1ImagePool pool)throws IOException{ByteArrayOutputStream raw=new ByteArrayOutputStream();pool.save(new DataOutputStream(raw));return raw.toByteArray();}
    public static int run()throws Exception{
        checks=0;Pc1HeapLease.Heap heap=new Pc1HeapLease.Heap(){public Pc1HeapLease allocate(int bytes)throws IOException{throw new IOException("raw401000 must not allocate");}public boolean release(Pc1HeapLease lease)throws IOException{throw new IOException("raw401000 must not free");}};
        Pc1ImagePool pool=new Pc1ImagePool(heap);for(int n=24;n<128;n++)pool.writeU8(n,0xa5);pool.field(1,12,713);
        for(int[] vector:new int[][]{{800,600,0x12345678},{800,600,0},{-4,-7,0x7ffffff8},{Integer.MAX_VALUE,Integer.MIN_VALUE,0xfffffffc},{0,0,1},{3,2,0x30000000}}){int w=vector[0],h=vector[1],bits=vector[2],stride=((w*3+3)/4)*4;pool.importFrontRaw(w,h,bits);check(pool.field(0,0)==bits&&pool.field(0,4)==bits+(h-1)*stride&&pool.field(0,8)==stride,"raw address and signed/wrapped401000 stride arithmetic");check(pool.field(0,12)==w&&pool.field(0,16)==h&&pool.field(0,20)==0&&pool.readU8(24)==0&&pool.readU8(25)==0xa5&&pool.readU8(127)==0xa5&&pool.field(1,12)==713,"only original fields and first name byte are published");}
        boolean gated=false;try{pool.pixelStorage(0).read(0);}catch(IOException expected){gated=true;}check(gated&&pool.field(0,0)==0x30000000,"unmapped retained HWND is rejected only at pixel dereference, without undoing publication");
        byte[] first=wire(pool);check(Arrays.equals(first,wire(pool)),"unresolved raw DWORDs remain stable snapshot bytes without fabricated allocation");
        Pc1HeapLease lease=Pc1HeapLease.fixture32(0x30000000,new byte[24],"self-authored raw401000 alias");pool.importFront(3,2,lease);lease.writeU8(4,91);pool.importFrontRaw(1,1,0x30000004);check(pool.pixelStorage(0).read(0)==91,"raw pixel read may resolve an interior of an existing physical allocation");
        pool.importFrontRaw(800,600,0);check(!pool.occupied(0)&&pool.field(0,4)==599*2400,"NULL base does not erase separately computed nonzero bottom pointer");return checks;
    }
    public static void main(String[] args)throws Exception{System.out.println("PASS "+run()+" raw front publication checks");}
}
