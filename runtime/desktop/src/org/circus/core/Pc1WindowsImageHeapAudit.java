package org.circus.core;

import java.io.*;
import java.nio.file.*;
import java.util.*;

/** Self-authored actual HeapAlloc/HeapFree/direct-memory exercise. No game
 *file, EXE, window or original process address is used or fabricated. */
public final class Pc1WindowsImageHeapAudit {
    private static int count;
    private static void check(boolean value,String why){count++;if(!value)throw new AssertionError(why);}
    private interface Work{void run()throws IOException;}
    private static void reject(Work work,String text)throws IOException{boolean failed=false;try{work.run();}catch(IOException expected){failed=String.valueOf(expected.getMessage()).contains(text);}check(failed,"precise "+text);}
    private static byte[] save(Pc1ImagePool pool)throws IOException{ByteArrayOutputStream out=new ByteArrayOutputStream();pool.save(new DataOutputStream(out));return out.toByteArray();}
    public static void main(String[] args)throws Exception{
        if(args.length!=1)throw new IOException("Require exact self-authored heap.dll");Path library=Paths.get(args[0]);final long actualHeap=Pc1WindowsImageHeap.currentProcessHeap(library);check(actualHeap!=0,"actual GetProcessHeap provides a nonnull borrowed handle");final int[] reads={0};Pc1WindowsImageHeap.ProcessHeap handle=()->{reads[0]++;return actualHeap;};
        try(Pc1WindowsImageHeap heap=new Pc1WindowsImageHeap(library,handle);Pc1WindowsImageHeap again=new Pc1WindowsImageHeap(library,handle)){
            Pc1ImagePool pool=new Pc1ImagePool(heap);int slot=pool.allocate(3,2,0,0);check(slot==20&&reads[0]==1,"actual HeapAlloc called before general slot publication");Pc1HeapLease lease=pool.allocation(slot);check(lease.addressKind()==Pc1HeapLease.AddressKind.OBSERVED64&&lease.observedAddress()!=0&&lease.length()==25,"actual physical lease includes all24pixel/padding bytes plus guard");byte[] initial=lease.copyBytes();for(int n=0;n<25;n++)check((initial[n]&255)==heap.observedByte(lease,n),"initial bytes observed independently at the actual allocation address");
            for(int n=0;n<25;n++){lease.writeU8(n,n*19+7);check(heap.observedByte(lease,n)==((n*19+7)&255),"direct lease write immediately changes actual heap memory without copy-back");heap.observedByte(lease,n,n*31+5);check(lease.readU8(n)==((n*31+5)&255),"actual native pointer write is immediately visible through same lease");}
            reject(lease::bytes,"direct native storage");reject(lease::nativeAddress32,"not truncatable");reject(()->pool.field(slot,0),"proven32-bit");check(pool.field(slot,8)==12&&pool.field(slot,12)==3&&pool.field(slot,16)==2,"known shape retains native metadata despite unavailable32-bit pointer");
            byte[] frozen=save(pool);Pc1ImagePool.Image image=Pc1ImagePool.loadImage(new DataInputStream(new ByteArrayInputStream(frozen)));Pc1ImagePool restored=Pc1ImagePool.restore(image,again,saved->{byte[] bytes=saved.backingBytes();Pc1HeapLease value=again.allocate(bytes.length);for(int n=0;n<bytes.length;n++)value.writeU8(n,bytes[n]);return value;});check(restored.occupied(slot)&&Arrays.equals(restored.allocation(slot).copyBytes(),lease.copyBytes()),"required physical rebind restores all direct bytes rather than a fabricated pointer");check(restored.allocation(slot).observedAddress()!=lease.observedAddress(),"two live native allocations have distinct actual physical addresses");reject(()->restored.field(slot,4),"proven32-bit");
            pool.free(slot);check(!pool.occupied(slot)&&pool.field(slot,4)==0&&!lease.live()&&heap.ownedAllocations()==0,"actual HeapFree retires lease before metadata pointer clear");reject(()->lease.readU8(0),"released");check(restored.allocation(slot).live()&&again.ownedAllocations()==1,"restored direct allocation remains independent of original release");restored.free(slot);check(again.ownedAllocations()==0,"restored physical owner releases normally");
            Pc1ImagePool full=new Pc1ImagePool(heap);for(int n=20;n<100;n++)check(full.allocate(1,1,0,0)==n,"actual original-order general pool scan");check(heap.ownedAllocations()==80&&full.liveHeapAllocations()==80,"all80slots retain live physical allocations");check(full.allocate(3,2,0,0)==-1&&heap.ownedAllocations()==81&&full.orphanHeapAllocations()==1,"actual full pool keeps HeapAlloc orphan rather than avoiding/freeing allocation");full.free(0);check(heap.ownedAllocations()==1&&full.liveHeapAllocations()==1&&full.orphanHeapAllocations()==1,"free0 releases slot-owned allocations but cannot discover orphan");
            reject(()->heap.allocate(-1),"64MiB");System.out.println("physicalHeapHandle="+Long.toUnsignedString(actualHeap)+";processHeapLeafReads="+reads[0]+";initialBytesSha256="+Binary.sha256(initial));
        }
        System.out.println("Pc1WindowsImageHeapAudit PASS "+count+"; actual current-process Win32 allocations/direct bytes/free; no original EXE/process,32-bit pointer fabrication or original file IO");
    }
}
