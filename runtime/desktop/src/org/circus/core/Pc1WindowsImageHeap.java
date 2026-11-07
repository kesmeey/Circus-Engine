package org.circus.core;

import java.io.*;
import java.nio.*;
import java.nio.file.*;
import java.util.*;

/** Actual HeapAlloc(flags0)/HeapFree provider over direct physical bytes.
 * This64-bit JNI backend never supplies fake native32-bit pointers. */
public final class Pc1WindowsImageHeap implements Pc1HeapLease.Heap,Closeable {
    public interface ProcessHeap {long current()throws IOException;}
    private static Path loaded;
    private static final int MAX_BYTES=64*1024*1024;
    private final ProcessHeap processHeap;
    private final Map<Pc1HeapLease,Long> handles=new IdentityHashMap<>();
    private boolean closed;
    private static synchronized void load(Path library)throws IOException{
        if(library==null)throw new IOException("PC1 actual image heap library required");Path absolute=library.toAbsolutePath().normalize();if(loaded!=null){if(!loaded.equals(absolute))throw new IOException("PC1 image heap JNI already loaded from another path");return;}
        try{System.load(absolute.toString());}catch(UnsatisfiedLinkError failure){throw new IOException("PC1 image heap JNI could not load",failure);}loaded=absolute;
    }
    /** Explicit real current-process bootstrap value. It is not the original
     *process's94C668 DWORD or a guess at that32-bit handle. */
    public static long currentProcessHeap(Path library)throws IOException{load(library);long value=processHeapNative();if(value==0)throw new IOException("GetProcessHeap returned null");return value;}
    public Pc1WindowsImageHeap(Path library,ProcessHeap processHeap)throws IOException{load(library);if(processHeap==null)throw new IOException("PC1 process heap must be an explicit borrowed leaf");this.processHeap=processHeap;}
    private void ready()throws IOException{if(closed)throw new IOException("PC1 actual image heap owner closed");}
    @Override public Pc1HeapLease allocate(int sizeBits)throws IOException{
        ready();long size=sizeBits&0xffffffffL;if(size<1||size>MAX_BYTES)throw new IOException("PC1 actual image heap request exceeds known64MiB provider bound");long heap=processHeap.current();if(heap==0)throw new IOException("PC1 image allocation has unknown/null process heap");long pointer=allocateNative(heap,sizeBits);if(pointer==0)return null;
        boolean published=false;try{
            final ByteBuffer buffer=directNative(pointer,sizeBits);if(buffer==null||!buffer.isDirect()||buffer.capacity()!=sizeBits)throw new IOException("PC1 physical allocation lacks its exact direct byte view");
            Pc1HeapLease.Storage storage=new Pc1HeapLease.Storage(){public int length(){return buffer.capacity();}public int read(int offset){return buffer.get(offset)&255;}public void write(int offset,int value){buffer.put(offset,(byte)value);}};
            Pc1HeapLease lease=Pc1HeapLease.observed64(pointer,storage,"Windows HeapAlloc flags0 direct JNI");handles.put(lease,heap);published=true;return lease;
        }finally{if(!published)freeNative(heap,pointer);}
    }
    @Override public boolean release(Pc1HeapLease lease)throws IOException{
        ready();if(lease==null||!lease.live()||!handles.containsKey(lease))throw new IOException("PC1 native HeapFree requires a currently owned physical allocation");
        // Native401130 rereads its current process-heap global for each call.
        long heap=processHeap.current();if(heap==0)throw new IOException("PC1 image free has unknown/null process heap");boolean success=freeNative(heap,lease.observedAddress());if(success){handles.remove(lease);lease.retired();}return success;
    }
    public int ownedAllocations(){return handles.size();}
    /** Independent physical audit leaf: observes the same native address,
     *not a copy cached in this Java provider. No arbitrary address is exposed. */
    public int observedByte(Pc1HeapLease lease,int offset)throws IOException{ready();if(!handles.containsKey(lease)||!lease.live()||offset<0||offset>=lease.length())throw new IOException("PC1 physical read leaves a live known allocation");return readNative(lease.observedAddress(),offset);}
    public void observedByte(Pc1HeapLease lease,int offset,int value)throws IOException{ready();if(!handles.containsKey(lease)||!lease.live()||offset<0||offset>=lease.length())throw new IOException("PC1 physical write leaves a live known allocation");writeNative(lease.observedAddress(),offset,value);}
    @Override public void close()throws IOException{
        if(closed)return;IOException failed=null;for(Map.Entry<Pc1HeapLease,Long> entry:new ArrayList<>(handles.entrySet())){
            Pc1HeapLease lease=entry.getKey();if(freeNative(entry.getValue(),lease.observedAddress())){handles.remove(lease);if(lease.live())lease.retired();}else{IOException error=new IOException("PC1 physical image heap cleanup failed");if(failed==null)failed=error;else failed.addSuppressed(error);}
        }if(failed!=null)throw failed;closed=true;
    }
    private static native long processHeapNative();
    private static native long allocateNative(long heap,int sizeBits);
    private static native ByteBuffer directNative(long address,int length);
    private static native boolean freeNative(long heap,long address);
    private static native int readNative(long address,int offset);
    private static native void writeNative(long address,int offset,int value);
}
