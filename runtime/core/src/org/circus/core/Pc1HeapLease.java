package org.circus.core;

import java.io.*;
import java.util.*;

/** One observed allocation, with its original mutable bytes and lifetime.
 * A64-bit host pointer is never truncated into a native32-bit image pointer.
 * Native/fixture32 factories require the caller to establish address authority;
 * this class cannot turn a Java object ID into an address. */
public final class Pc1HeapLease {
    public enum AddressKind { OBSERVED32, OBSERVED64, FIXTURE32, MANAGED, GUEST32 }
    public interface Heap {
        /** Actual401050 unsigned SIZE_T bits, including its extra guard byte.
         * Null means the original HeapAlloc returned null. */
        Pc1HeapLease allocate(int requestedSizeBits)throws IOException;
        /** Actual HeapFree result. False still makes401130 clear the slot.
         * Throw only for a genuinely unavailable/unknown native service. */
        boolean release(Pc1HeapLease allocation)throws IOException;
        default Pc1GuestAddressSpace guestAddressSpace(){return null;}
    }
    private final AddressKind kind;
    private final long address;
    private final String authority;
    /** Physical providers can expose direct native storage; no initial-copy/
     *copy-back shadow is necessary. Every byte remains in its one owner. */
    public interface Storage {int length();int read(int offset)throws IOException;void write(int offset,int value)throws IOException;}
    private static final class ArrayStorage implements Storage {
        final byte[] bytes;ArrayStorage(byte[] bytes){this.bytes=bytes;}public int length(){return bytes.length;}public int read(int offset){return bytes[offset]&255;}public void write(int offset,int value){bytes[offset]=(byte)value;}
    }
    private Storage storage;private final int length;
    private final boolean borrowed;
    private boolean live=true;
    private final Set<Pc1GuestAddressSpace> guestSpaces=Collections.newSetFromMap(new IdentityHashMap<Pc1GuestAddressSpace,Boolean>());
    private Pc1HeapLease(AddressKind kind,long address,Storage storage,String authority)throws IOException{this(kind,address,storage,authority,false);}
    private Pc1HeapLease(AddressKind kind,long address,Storage storage,String authority,boolean borrowed)throws IOException{
        if(kind==null||address==0&&kind!=AddressKind.MANAGED||storage==null||storage.length()<=0||authority==null||authority.length()==0)throw new IOException("PC1 heap lease requires observed nonnull address/backing/authority");
        if((kind==AddressKind.OBSERVED32||kind==AddressKind.FIXTURE32)&&(address<0||address>0xffffffffL||address+storage.length()>0x100000000L))throw new IOException("PC1 heap lease leaves known32-bit address space");
        if(kind==AddressKind.OBSERVED64&&Long.compareUnsigned(address+storage.length(),address)<=0)throw new IOException("PC1 physical allocation leaves64-bit address space");
        this.kind=kind;this.address=address;this.storage=storage;this.length=storage.length();this.authority=authority;this.borrowed=borrowed;
    }
    /** Caller has an actual readable32-bit allocation address, not a handle. */
    public static Pc1HeapLease observed32(int address,byte[] originalBacking,String provider)throws IOException{return observed32(address,array(originalBacking),provider);}
    public static Pc1HeapLease observed32(int address,Storage originalBacking,String provider)throws IOException{return new Pc1HeapLease(AddressKind.OBSERVED32,address&0xffffffffL,originalBacking,provider);}
    /** Current64-bit allocation leaf: useful for pixels/nonnull branches, but
     *insufficient authority for either native pointer DWORD. */
    public static Pc1HeapLease observed64(long address,byte[] originalBacking,String provider)throws IOException{return observed64(address,array(originalBacking),provider);}
    public static Pc1HeapLease observed64(long address,Storage originalBacking,String provider)throws IOException{return new Pc1HeapLease(AddressKind.OBSERVED64,address,originalBacking,provider);}
    /** Borrowed actual DIB bytes: retiring this view never deletes a bitmap
     *or frees its memory; its Window owner must enforce its own epoch too. */
    public static Pc1HeapLease borrowedExternal64(long address,Storage originalBacking,String provider)throws IOException{return new Pc1HeapLease(AddressKind.OBSERVED64,address,originalBacking,provider,true);}
    public void retireBorrowedView()throws IOException{if(!borrowed)throw new IOException("PC1 lease is not a borrowed external view");retired();}
    /** Explicit portable import. It has NO physical address or native pointer
     *claim; it is not an uninitialized native allocation. */
    public static Pc1HeapLease managed(byte[] knownBytes,String purpose)throws IOException{return managed(array(knownBytes),purpose);}
    /** Explicit external guest ABI/opaque-leaf view. The supplied owner must
     * reject unknown bytes; this overload never synthesizes code or memory. */
    public static Pc1HeapLease managed(Storage storage,String purpose)throws IOException{return new Pc1HeapLease(AddressKind.MANAGED,0,storage,purpose);}
    /** Only deterministic tests/private-CPU references may supply fixture addresses. */
    public static Pc1HeapLease fixture32(int address,byte[] originalBacking,String evidence)throws IOException{return new Pc1HeapLease(AddressKind.FIXTURE32,address&0xffffffffL,array(originalBacking),evidence);}
    private static Storage array(byte[] raw){return raw==null?null:new ArrayStorage(raw);}
    public AddressKind addressKind(){return guestSpaces.isEmpty()?kind:AddressKind.GUEST32;}
    public AddressKind physicalAddressKind(){return kind;}
    public long observedAddress(){return address;}
    public String authority(){return authority;}
    public boolean hasObserved32Address(){return kind==AddressKind.OBSERVED32||kind==AddressKind.FIXTURE32;}
    public boolean hasNative32Address(){return hasObserved32Address()||!guestSpaces.isEmpty();}
    public int observedAddress32()throws IOException{ready();if(!hasObserved32Address())throw new IOException("PC1 physical pointer is not an observed32-bit address");return(int)address;}
    public int nativeAddress32()throws IOException{ready();if(!guestSpaces.isEmpty())throw new IOException("Guest32 pointer requires an explicit address-space context");if(!hasObserved32Address())throw new IOException("PC1 native pointer DWORD requires a proven32-bit allocation address;64-bit physical pointer is not truncatable");return(int)address;}
    public int nativeAddress32(Pc1GuestAddressSpace space)throws IOException{ready();return space==null?nativeAddress32():space.addressOf(this);}
    void attachGuest(Pc1GuestAddressSpace space)throws IOException{ready();if(space==null)throw new IOException("Guest32 mapping context missing");guestSpaces.add(space);}
    void detachGuest(Pc1GuestAddressSpace space){guestSpaces.remove(space);}
    /** Same managed backing for narrow array providers only. A physical
     *direct allocation cannot be silently materialized into a mutable array. */
    public byte[] bytes()throws IOException{ready();if(!(storage instanceof ArrayStorage))throw new IOException("PC1 allocation is direct native storage, not an array backing");return((ArrayStorage)storage).bytes;}
    public int length(){return length;}
    private void range(int offset)throws IOException{ready();if(offset<0||offset>=length())throw new IOException("PC1 lease byte access leaves its known allocation");}
    public int readU8(int offset)throws IOException{range(offset);return storage.read(offset)&255;}
    public void writeU8(int offset,int value)throws IOException{range(offset);storage.write(offset,value&255);}
    /** Detached snapshot/verification data, never a second live pixel owner. */
    public byte[] copyBytes()throws IOException{ready();byte[] bytes=new byte[length()];for(int n=0;n<bytes.length;n++)bytes[n]=(byte)storage.read(n);return bytes;}
    public boolean live(){return live;}
    private void ready()throws IOException{if(!live)throw new IOException("PC1 allocation lease was released");}
    void retired(){if(!live)return;live=false;for(Pc1GuestAddressSpace space:new ArrayList<>(guestSpaces))space.retired(this);storage=null;}
}
