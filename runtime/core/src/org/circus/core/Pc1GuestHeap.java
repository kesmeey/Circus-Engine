package org.circus.core;
import java.io.*;

/** Guest32 HeapAlloc ABI over one actual physical allocation provider. */
public final class Pc1GuestHeap implements Pc1HeapLease.Heap {
    private final Pc1GuestAddressSpace space;private final Pc1HeapLease.Heap physical;
    public Pc1GuestHeap(Pc1GuestAddressSpace space,Pc1HeapLease.Heap physical)throws IOException{if(space==null||physical==null||physical.guestAddressSpace()!=null)throw new IOException("Guest32 heap requires explicit space and unmapped physical provider");this.space=space;this.physical=physical;}
    @Override public Pc1GuestAddressSpace guestAddressSpace(){return space;}
    public Pc1HeapLease.Heap physicalHeap(){return physical;}
    @Override public Pc1HeapLease allocate(int bits)throws IOException{
        space.beginNativeCall();try{Pc1HeapLease lease=physical.allocate(bits);if(lease==null)return null;if(lease.physicalAddressKind()==Pc1HeapLease.AddressKind.MANAGED)throw new IOException("Guest32 native HeapAlloc cannot use a fabricated managed allocation");try{space.mapHeap(lease);return lease;}catch(Pc1GuestAddressSpace.Exhausted full){if(!physical.release(lease))throw new IOException("Guest32 VA exhaustion cleanup could not release physical allocation",full);lease.retired();return null;}}finally{space.endNativeCall();}
    }
    @Override public boolean release(Pc1HeapLease lease)throws IOException{space.beginNativeCall();try{if(space.kindOf(lease)!=Pc1GuestAddressSpace.Kind.HEAP)throw new IOException("Guest32 HeapFree reached borrowed external allocation; allocator contract unavailable");boolean success=physical.release(lease);if(success)lease.retired();return success;}finally{space.endNativeCall();}}
    /** Snapshot restoration must not rerun first-fit before saved mappings
     *are re-established. External DIB rebinding belongs to its Window owner. */
    public Pc1HeapLease rebindPhysical(Pc1ImagePool.SavedLease saved)throws IOException{
        if(saved==null||!saved.heapOwned)throw new IOException("Guest32 external snapshot requires its original owner");byte[] bytes=saved.backingBytes();Pc1HeapLease lease=physical.allocate(bytes.length);if(lease==null)throw new IOException("Guest32 physical snapshot allocation failed");for(int n=0;n<bytes.length;n++)lease.writeU8(n,bytes[n]);return lease;
    }
}
