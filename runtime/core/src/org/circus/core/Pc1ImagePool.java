package org.circus.core;

import java.io.*;
import java.util.*;

/** Canonical401000/401050/401130 image records and live allocation leases.
 * Not bound alongside the old Compositor metadata/name owners: migration
 *must hand those views over to this one backing. No Win32/decoder shortcut. */
public final class Pc1ImagePool {
    private static final int FORMAT=0x50314950,VERSION=4,MAX_LEASES=10000,MAX_BYTES=256*1024*1024;
    public static final int BASE=0x94c6d8,COUNT=100,RECORD_BYTES=128,BYTES=COUNT*RECORD_BYTES;
    public static final int POINTER=0,BOTTOM=4,STRIDE=8,WIDTH=12,HEIGHT=16,PIXEL_FORMAT=20,NAME=24;
    private final byte[] raw=new byte[BYTES];
    private final boolean[] known=new boolean[BYTES];
    private final Pc1HeapLease[] opaqueBase=new Pc1HeapLease[COUNT];
    private final boolean[] opaqueDangling=new boolean[COUNT];
    private final Pc1HeapLease[] opaqueBottom=new Pc1HeapLease[COUNT];
    private final int[] bottomDisplacement=new int[COUNT];
    private final boolean[] bottomDangling=new boolean[COUNT];
    private final byte[] rowRaw=new byte[4];private int rowMask=15,rowDisplacement;
    private Pc1HeapLease rowLease;private boolean rowDangling;
    private final List<Block> blocks=new ArrayList<>();
    private final Pc1HeapLease.Heap heap;
    private final Pc1GuestAddressSpace guest;
    private int nativeDepth;
    private static final class Block {final Pc1HeapLease lease;final boolean heapOwned;Block(Pc1HeapLease lease,boolean heapOwned){this.lease=lease;this.heapOwned=heapOwned;}}
    public Pc1ImagePool(Pc1HeapLease.Heap heap)throws IOException{if(heap==null)throw new IOException("PC1 image pool requires its allocation/free provider");this.heap=heap;this.guest=heap.guestAddressSpace();Arrays.fill(known,true);}
    public Pc1GuestAddressSpace guestAddressSpace(){return guest;}
    private boolean hasAddress(Pc1HeapLease lease){return guest==null?lease.hasObserved32Address():guest.containsLease(lease);}
    private int address(Pc1HeapLease lease)throws IOException{return guest==null?lease.observedAddress32():guest.addressOf(lease);}
    public void beginNativeCall(){nativeDepth++;}
    public void endNativeCall(){if(nativeDepth<=0)throw new IllegalStateException("PC1 image pool native boundary imbalance");nativeDepth--;}
    private static int slot(int handle)throws IOException{if(handle<0||handle>=COUNT)throw new IOException("PC1 image record leaves known100-slot pool");return handle*RECORD_BYTES;}
    private static void range(int offset,int length)throws IOException{if(offset<0||length<0||(long)offset+length>BYTES)throw new IOException("PC1 image byte view leaves owned12800-byte pool");}
    public int readU8(int offset)throws IOException{range(offset,1);if(!known[offset])throw new IOException("PC1 image raw pointer byte has no proven32-bit address");return raw[offset]&255;}
    public void writeU8(int offset,int value)throws IOException{range(offset,1);raw[offset]=(byte)value;known[offset]=true;int field=offset%RECORD_BYTES,handle=offset/RECORD_BYTES;if(field<4){opaqueBase[handle]=null;opaqueDangling[handle]=false;}else if(field<8){opaqueBottom[handle]=null;bottomDangling[handle]=false;}}
    public int readI32(int offset)throws IOException{range(offset,4);int value=0;for(int n=0;n<4;n++)value|=readU8(offset+n)<<(n*8);return value;}
    public void writeI32(int offset,int value)throws IOException{range(offset,4);for(int n=0;n<4;n++)writeU8(offset+n,value>>>(n*8));}
    public int field(int handle,int offset)throws IOException{if(offset<0||offset+4>RECORD_BYTES)throw new IOException("PC1 image field offset");return readI32(slot(handle)+offset);}
    public void field(int handle,int offset,int value)throws IOException{if(offset<0||offset+4>RECORD_BYTES)throw new IOException("PC1 image field offset");writeI32(slot(handle)+offset,value);}
    private boolean wordKnown(int offset){for(int n=0;n<4;n++)if(!known[offset+n])return false;return true;}
    public boolean occupied(int handle)throws IOException{int offset=slot(handle);if(wordKnown(offset))return readI32(offset)!=0;if(opaqueBase[handle]!=null||opaqueDangling[handle])return true;throw new IOException("PC1 image nonnull test reaches an incompletely known pointer alias");}
    /** Actual DWORD arithmetic/division, separate from any typed pixel limits. */
    public static int nativeStride(int width,int format){int value=(format==0?width*3:format==1?width*4:width)+3;return((value+((value>>31)&3))>>2)*4;}
    private void register(Pc1HeapLease lease,boolean heapOwned)throws IOException{register(lease,heapOwned,false);}
    private void register(Pc1HeapLease lease,boolean heapOwned,boolean restoring)throws IOException{
        if(lease==null||!lease.live())throw new IOException("PC1 pool cannot publish an absent/released allocation");if(guest!=null&&!restoring&&!guest.containsLease(lease))throw new IOException("Guest32 pool received an allocation without its contextual mapping");
        // Stale pointer facts live in their cells, not in retained pixel
        //payloads/ever-growing allocation registries after a successful free.
        for(Iterator<Block> iterator=blocks.iterator();iterator.hasNext();)if(!iterator.next().lease.live())iterator.remove();
        for(Block block:blocks)if(block.lease.live()){
            if(block.lease==lease)throw new IOException("PC1 allocation is already registered");
            if(lease.physicalAddressKind()!=Pc1HeapLease.AddressKind.MANAGED&&block.lease.physicalAddressKind()!=Pc1HeapLease.AddressKind.MANAGED&&block.lease.observedAddress()==lease.observedAddress())throw new IOException("PC1 provider reused an allocation address before release");
            if(hasAddress(block.lease)&&hasAddress(lease)){
                long a=address(block.lease)&0xffffffffL,b=address(lease)&0xffffffffL;if(a+block.lease.length()>b&&b+lease.length()>a)throw new IOException("PC1 known allocations overlap");
            }
        }
        blocks.add(new Block(lease,heapOwned));
    }
    private void pointer(int handle,int offset,Pc1HeapLease lease,int displacement)throws IOException{
        int at=slot(handle)+offset;
        if(hasAddress(lease))writeI32(at,address(lease)+displacement);
        else{for(int n=0;n<4;n++){raw[at+n]=0;known[at+n]=false;}if(offset==POINTER){opaqueBase[handle]=lease;opaqueDangling[handle]=false;}else if(offset==BOTTOM){opaqueBottom[handle]=lease;bottomDisplacement[handle]=displacement;bottomDangling[handle]=false;}}
    }
    /**401050: allocate first; then scan0..19 for ANY nonzero mode, otherwise
     *20..99. A full or undecidable pool keeps the new allocation orphaned. */
    public int allocate(int width,int height,int format,int mode)throws IOException{
        beginNativeCall();try{return allocateCore(width,height,format,mode);}finally{endNativeCall();}
    }
    private int allocateCore(int width,int height,int format,int mode)throws IOException{
        int stride=nativeStride(width,format),size=stride*height+1;Pc1HeapLease lease=heap.allocate(size);if(lease==null)return -1;
        if(lease.length()!=(size&0xffffffffL))throw new IOException("PC1 provider returned an incomplete allocation backing");register(lease,true);
        for(int handle=mode==0?20:0,end=mode==0?COUNT:20;handle<end;handle++)if(!occupied(handle)){
            // Exact original write order, with no intermediate shadow/copy.
            pointer(handle,POINTER,lease,0);field(handle,WIDTH,width);field(handle,STRIDE,stride);field(handle,HEIGHT,height);field(handle,PIXEL_FORMAT,format);pointer(handle,BOTTOM,lease,(height-1)*stride);return handle;
        }
        return -1;
    }
    /**401000 imports the real externally owned DIB. Does not allocate/free,
     *and clears only the first name byte rather than the whole stale name. */
    public void importFront(int width,int height,Pc1HeapLease external)throws IOException{
        if(guest!=null&&!guest.containsLease(external))guest.mapBorrowed(external);boolean present=false;for(Block block:blocks)if(block.lease==external&&external.live())present=true;if(!present)register(external,false);int stride=nativeStride(width,0);field(0,WIDTH,width);pointer(0,POINTER,external,0);field(0,PIXEL_FORMAT,0);writeU8(NAME,0);field(0,HEIGHT,height);field(0,STRIDE,stride);pointer(0,BOTTOM,external,(height-1)*stride);
    }
    /**401000 also publishes a known raw ppvBits value when CreateDIBSection
     *has failed. It does not dereference pixels or validate a HANDLE-shaped
     *value. A later pixel access resolves only actual mapped allocations. */
    public void importFrontRaw(int width,int height,int bits)throws IOException{
        field(0,WIDTH,width);field(0,POINTER,bits);field(0,PIXEL_FORMAT,0);writeU8(NAME,0);field(0,HEIGHT,height);int stride=nativeStride(width,0);field(0,STRIDE,stride);field(0,BOTTOM,bits+(height-1)*stride);
    }
    /** Explicit known-pixel portable import, not401050. Never invents an
     *uninitialized guard byte or a physical address for the supplied array. */
    public void importManaged(int handle,int width,int height,int stride,int format,byte[] bytes)throws IOException{
        if(guest!=null)throw new IOException("Guest32 production image import requires allocation before pixel filling");int at=slot(handle);Pc1HeapLease old=null;if(occupied(handle))old=allocation(handle);if(old!=null&&old.physicalAddressKind()!=Pc1HeapLease.AddressKind.MANAGED)throw new IOException("PC1 native allocation replacement requires explicit free/reallocation");
        if(old!=null)old.retired();Pc1HeapLease lease=Pc1HeapLease.managed(bytes,"explicit portable known image import");register(lease,false);pointer(handle,POINTER,lease,0);field(handle,WIDTH,width);field(handle,STRIDE,stride);field(handle,HEIGHT,height);field(handle,PIXEL_FORMAT,format);pointer(handle,BOTTOM,lease,(height-1)*stride);
    }
    private Pc1HeapLease knownAddress(int address)throws IOException{long value=address&0xffffffffL;for(Block block:blocks)if(block.lease.live()&&hasAddress(block.lease)){long base=address(block.lease)&0xffffffffL;if(value>=base&&value<base+block.lease.length())return block.lease;}throw new IOException("PC1 image address reaches unowned allocation memory at0x"+Integer.toHexString(address));}
    private int readAddress(int value)throws IOException{if(guest!=null)return guest.read(value);Pc1HeapLease lease=knownAddress(value);return lease.readU8(value-address(lease));}
    private void writeAddress(int value,int data)throws IOException{if(guest!=null){guest.write(value,data);return;}Pc1HeapLease lease=knownAddress(value);lease.writeU8(value-address(lease),data);}
    /** Checked direct view for typed consumers. Known32 addresses can alias
     *another live allocation; unresolved pointer bytes never turn into zeros. */
    public Pc1HeapLease.Storage pixelStorage(final int handle)throws IOException{
        final int at=slot(handle);if(!occupied(handle))return null;
        if(!wordKnown(at)){final Pc1HeapLease lease=allocation(handle);return new Pc1HeapLease.Storage(){public int length(){return lease.length();}public int read(int offset)throws IOException{return lease.readU8(offset);}public void write(int offset,int value)throws IOException{lease.writeU8(offset,value);}};}
        final int base=readI32(at);return new Pc1HeapLease.Storage(){public int length(){return -1;}public int read(int offset)throws IOException{int address=base+offset;return readAddress(address);}public void write(int offset,int value)throws IOException{int address=base+offset;writeAddress(address,value);}};
    }
    public int pixelBottomOffset(int handle)throws IOException{int at=slot(handle);if(wordKnown(at+4)&&wordKnown(at))return readI32(at+4)-readI32(at);Pc1HeapLease base=allocation(handle);if(opaqueBottom[handle]==base&&base.live())return bottomDisplacement[handle];throw new IOException("PC1 image bottom pointer lacks a readable allocation relation");}
    /**4449B4 is a pointer INTO the image HeapAlloc, never the CRT decode malloc.
     *Its raw value persists after that allocation is freed by palette conversion. */
    public int rowPointerByte(int byteIndex)throws IOException{if(byteIndex<0||byteIndex>=4)throw new IOException("PC1 row pointer byte range");if((rowMask&(1<<byteIndex))==0)throw new IOException("PC1 row pointer byte has no proven32-bit address");return rowRaw[byteIndex]&255;}
    public void rowPointerByte(int byteIndex,int value)throws IOException{if(byteIndex<0||byteIndex>=4)throw new IOException("PC1 row pointer byte range");rowRaw[byteIndex]=(byte)value;rowMask|=1<<byteIndex;rowLease=null;rowDangling=false;}
    private int rowWord()throws IOException{int word=0;for(int n=0;n<4;n++)word|=rowPointerByte(n)<<(n*8);return word;}
    private void rowWord(int value){for(int n=0;n<4;n++)rowRaw[n]=(byte)(value>>>(n*8));rowMask=15;rowLease=null;rowDangling=false;}
    public void pointRow(Pc1HeapLease lease,int displacement)throws IOException{if(lease==null)throw new IOException("PC1 CRX row pointer has no allocation");if(hasAddress(lease))rowWord(address(lease)+displacement);else{Arrays.fill(rowRaw,(byte)0);rowMask=0;rowLease=lease;rowDisplacement=displacement;rowDangling=false;}}
    public void advanceRow(int delta)throws IOException{if(rowMask==15)rowWord(rowWord()+delta);else if(rowLease!=null||rowDangling)rowDisplacement+=delta;else throw new IOException("PC1 row arithmetic reaches a partially known pointer alias");}
    /** Captures a pointer expression for native locals. The storage continues
     *to reference the same live lease; it is not a frozen copy of its bytes. */
    public Pc1HeapLease.Storage rowStorage()throws IOException{
        if(rowMask==15){final int base=rowWord();return new Pc1HeapLease.Storage(){public int length(){return -1;}public int read(int offset)throws IOException{int address=base+offset;return readAddress(address);}public void write(int offset,int value)throws IOException{int address=base+offset;writeAddress(address,value);}};}
        if(rowLease==null||!rowLease.live())throw new IOException("PC1 row pointer has no live readable allocation owner");final Pc1HeapLease lease=rowLease;final int base=rowDisplacement;return new Pc1HeapLease.Storage(){public int length(){return -1;}public int read(int offset)throws IOException{return lease.readU8(base+offset);}public void write(int offset,int value)throws IOException{lease.writeU8(base+offset,value);}};
    }
    /** Resolves only a truly known live allocation. Arbitrary raw aliases are
     *preserved, but cannot become a fabricated safe pointer or pixel image. */
    public Pc1HeapLease allocation(int handle)throws IOException{
        int at=slot(handle);if(!wordKnown(at)){if(opaqueBase[handle]!=null&&opaqueBase[handle].live())return opaqueBase[handle];if(opaqueBase[handle]!=null||opaqueDangling[handle])throw new IOException("PC1 opaque pointer has no live allocation owner");throw new IOException("PC1 allocation pointer alias is partially unknown");}
        int address=readI32(at);if(address==0)return null;if(guest!=null)return guest.allocationAtBase(address);for(Block block:blocks)if(block.lease.live()&&hasAddress(block.lease)&&address(block.lease)==address)return block.lease;
        throw new IOException("PC1 image pointer has no live proven allocation owner at0x"+Integer.toHexString(address));
    }
    private void release(int handle)throws IOException{
        if(!occupied(handle))return;Pc1HeapLease lease=allocation(handle);boolean freed=heap.release(lease);if(freed)lease.retired();field(handle,POINTER,0);field(handle,BOTTOM,0);
    }
    /**401130:0 only frees general20..99; negative/100+ are no effect. HeapFree
     *false still clears both pointers. Null-base slots retain stale bottom. */
    public void free(int handle)throws IOException{beginNativeCall();try{if(handle==0){for(int n=20;n<COUNT;n++)release(n);}else if(handle>0&&handle<COUNT)release(handle);}finally{endNativeCall();}}
    public int liveHeapAllocations(){int count=0;for(Block block:blocks)if(block.heapOwned&&block.lease.live())count++;return count;}
    public long knownAllocationBytes(){long bytes=0;for(Block block:blocks)if(block.lease.live())bytes+=block.lease.length();return bytes;}
    public int orphanHeapAllocations()throws IOException{
        int result=0;for(Block block:blocks)if(block.heapOwned&&block.lease.live()){
            boolean referenced=false;for(int n=0;n<COUNT;n++){int at=n*RECORD_BYTES;if(opaqueBase[n]==block.lease){referenced=true;break;}if(wordKnown(at)&&hasAddress(block.lease)&&readI32(at)==address(block.lease)){referenced=true;break;}}
            if(!referenced)result++;
        }return result;
    }
    /** CString writes can naturally cross the104-byte name tail into adjacent
     *known records. Reaching an unknown/outside byte remains explicit. */
    public void name(int handle,byte[] source)throws IOException{if(source==null)throw new IOException("PC1 image name source missing");int start=slot(handle)+NAME;for(int n=0;;n++){int value=n<source.length?source[n]&255:0;writeU8(start+n,value);if(value==0)return;}}
    public byte[] name(int handle)throws IOException{int start=slot(handle)+NAME;ByteArrayOutputStream out=new ByteArrayOutputStream();for(int n=0;;n++){int value=readU8(start+n);if(value==0)return out.toByteArray();out.write(value);}}
    public byte[] rawName(int handle)throws IOException{byte[] bytes=new byte[104];int start=slot(handle)+NAME;for(int n=0;n<bytes.length;n++)bytes[n]=(byte)readU8(start+n);return bytes;}
    public static final class SavedLease {
        public final Pc1HeapLease.AddressKind kind,physicalKind;public final long address;public final String authority;public final boolean heapOwned;private final byte[] bytes;
        private SavedLease(Pc1HeapLease.AddressKind kind,Pc1HeapLease.AddressKind physicalKind,long address,String authority,boolean heapOwned,byte[] bytes){this.kind=kind;this.physicalKind=physicalKind;this.address=address;this.authority=authority;this.heapOwned=heapOwned;this.bytes=bytes;}
        public byte[] backingBytes(){return bytes.clone();}
    }
    /** Rebinding must establish real current address/backing authority. It is
     *not automatically HeapAlloc: observed32 addresses must remain identical.
     *The caller owns cleanup of unpublished leases if a later bind fails. */
    public interface Rebinder {Pc1HeapLease bind(SavedLease allocation)throws IOException;}
    public static final class Image {
        private final byte[] raw,row;private final int[] masks,opaque,bottom,bottomOffsets;private final List<SavedLease> leases;private final int rowMask,rowIndex,rowOffset;private final Pc1GuestAddressSpace.Image guestLayout;
        private Image(byte[] raw,int[] masks,int[] opaque,int[] bottom,int[] bottomOffsets,List<SavedLease> leases,byte[] row,int rowMask,int rowIndex,int rowOffset,Pc1GuestAddressSpace.Image guestLayout){this.raw=raw;this.masks=masks;this.opaque=opaque;this.bottom=bottom;this.bottomOffsets=bottomOffsets;this.leases=leases;this.row=row;this.rowMask=rowMask;this.rowIndex=rowIndex;this.rowOffset=rowOffset;this.guestLayout=guestLayout;}
        /** A byte-exact metadata image is unavailable when a64-bit provider
         *has no original32-bit pointer. save()/loadImage() preserve that mask. */
        public byte[] rawMetadataBacking()throws IOException{for(int mask:masks)if(mask!=255)throw new IOException("PC1 image snapshot contains unknown native pointer bytes");return raw.clone();}
        public int knownPointerByteMask(int handle)throws IOException{slot(handle);return masks[handle];}
        public int leaseCount(){return leases.size();}
        public SavedLease savedLease(int index)throws IOException{if(index<0||index>=leases.size())throw new IOException("PC1 saved image lease index");return leases.get(index);}
        public Pc1GuestAddressSpace.Image guestLayout(){return guestLayout;}
    }
    /** Reference into the exact live-block sequence serialized by save().
     *The caller must hold the entire world's snapshot boundary across both. */
    public int snapshotLeaseIndex(Pc1HeapLease lease)throws IOException{if(nativeDepth!=0)throw new IOException("PC1 image pool snapshot during native allocation/decoder/free call");int index=0;for(Block block:blocks)if(block.lease.live()){if(block.lease==lease)return index;index++;}return -1;}
    public void save(DataOutput out)throws IOException{
        if(nativeDepth!=0)throw new IOException("PC1 image pool snapshot during native allocation/decoder/free call");
        List<Block> live=new ArrayList<>();for(Block block:blocks)if(block.lease.live())live.add(block);
        if(live.size()>MAX_LEASES)throw new IOException("PC1 image lease snapshot count bound");out.writeInt(FORMAT);out.writeInt(VERSION);out.write(raw);
        for(int n=0;n<COUNT;n++){int mask=0;for(int b=0;b<8;b++)if(known[n*RECORD_BYTES+b])mask|=1<<b;out.writeByte(mask);}
        out.writeInt(live.size());long bytes=0;
        for(Block block:live){Pc1HeapLease lease=block.lease;bytes+=lease.length();if(bytes>MAX_BYTES)throw new IOException("PC1 image snapshot known allocation bound");Pc1HeapLease.AddressKind type=guest!=null&&guest.containsLease(lease)?Pc1HeapLease.AddressKind.GUEST32:lease.physicalAddressKind();out.writeByte(type.ordinal());out.writeByte(lease.physicalAddressKind().ordinal());out.writeLong(lease.observedAddress());out.writeUTF(lease.authority());out.writeBoolean(block.heapOwned);out.writeInt(lease.length());out.write(lease.copyBytes());}
        for(int n=0;n<COUNT;n++){int index=opaqueDangling[n]||opaqueBase[n]!=null&&!opaqueBase[n].live()?-2:-1;if(opaqueBase[n]!=null&&opaqueBase[n].live())for(int b=0;b<live.size();b++)if(live.get(b).lease==opaqueBase[n])index=b;out.writeInt(index);}
        for(int n=0;n<COUNT;n++){int index=bottomDangling[n]||opaqueBottom[n]!=null&&!opaqueBottom[n].live()?-2:-1;if(opaqueBottom[n]!=null&&opaqueBottom[n].live())for(int b=0;b<live.size();b++)if(live.get(b).lease==opaqueBottom[n])index=b;out.writeInt(index);out.writeInt(bottomDisplacement[n]);}
        out.write(rowRaw);out.writeByte(rowMask);int rowIndex=rowDangling||rowLease!=null&&!rowLease.live()?-2:-1;if(rowLease!=null&&rowLease.live())for(int b=0;b<live.size();b++)if(live.get(b).lease==rowLease)rowIndex=b;out.writeInt(rowIndex);out.writeInt(rowDisplacement);out.writeBoolean(guest!=null);if(guest!=null)guest.saveLayout(out,lease->{for(int n=0;n<live.size();n++)if(live.get(n).lease==lease)return n;return -1;});
    }
    public static Image loadImage(DataInput in)throws IOException{
        if(in.readInt()!=FORMAT)throw new IOException("PC1 image pool snapshot format");int version=in.readInt();if(version!=3&&version!=VERSION)throw new IOException("PC1 image pool snapshot version");byte[] raw=new byte[BYTES];in.readFully(raw);int[] masks=new int[COUNT],opaque=new int[COUNT];for(int n=0;n<COUNT;n++)masks[n]=in.readUnsignedByte();
        int count=in.readInt();if(count<0||count>MAX_LEASES)throw new IOException("PC1 image pool lease count");List<SavedLease> leases=new ArrayList<>();long total=0;
        for(int n=0;n<count;n++){int kind=in.readUnsignedByte(),physical=version>=4?in.readUnsignedByte():kind;long address=in.readLong();String authority=in.readUTF();boolean heap=in.readBoolean();int length=in.readInt();total+=length;if(kind>=Pc1HeapLease.AddressKind.values().length||physical>=Pc1HeapLease.AddressKind.GUEST32.ordinal()||authority.length()==0||length<=0||total>MAX_BYTES||version==3&&kind>=Pc1HeapLease.AddressKind.GUEST32.ordinal())throw new IOException("PC1 image snapshot allocation descriptor");Pc1HeapLease.AddressKind type=Pc1HeapLease.AddressKind.values()[kind],physicalType=Pc1HeapLease.AddressKind.values()[physical];if(type!=Pc1HeapLease.AddressKind.GUEST32&&type!=physicalType)throw new IOException("PC1 image snapshot pointer/physical kind mismatch");if(physicalType==Pc1HeapLease.AddressKind.MANAGED?address!=0:address==0)throw new IOException("PC1 image snapshot allocation address authority");if((physicalType==Pc1HeapLease.AddressKind.OBSERVED32||physicalType==Pc1HeapLease.AddressKind.FIXTURE32)&&(address<0||address>0xffffffffL||address+length>0x100000000L))throw new IOException("PC1 image snapshot native32 address range");byte[] bytes=new byte[length];in.readFully(bytes);leases.add(new SavedLease(type,physicalType,address,authority,heap,bytes));}
        for(int n=0;n<COUNT;n++){opaque[n]=in.readInt();if(opaque[n]< -2||opaque[n]>=count||opaque[n]==-2&&(masks[n]&15)!=0||opaque[n]>=0&&((masks[n]&15)!=0||leases.get(opaque[n]).kind==Pc1HeapLease.AddressKind.OBSERVED32||leases.get(opaque[n]).kind==Pc1HeapLease.AddressKind.FIXTURE32||leases.get(opaque[n]).kind==Pc1HeapLease.AddressKind.GUEST32))throw new IOException("PC1 image snapshot opaque pointer association");}
        int[] bottom=new int[COUNT],bottomOffsets=new int[COUNT];for(int n=0;n<COUNT;n++){bottom[n]=in.readInt();bottomOffsets[n]=in.readInt();if(bottom[n]< -2||bottom[n]>=count||bottom[n]!=-1&&(masks[n]&240)!=0)throw new IOException("PC1 image snapshot bottom pointer association");}
        byte[] row=new byte[4];in.readFully(row);int rowMask=in.readUnsignedByte(),rowIndex=in.readInt(),rowOffset=in.readInt();if(rowMask>15||rowIndex< -2||rowIndex>=count||rowIndex!=-1&&rowMask!=0)throw new IOException("PC1 image snapshot row pointer association");Pc1GuestAddressSpace.Image guestLayout=version>=4&&in.readBoolean()?Pc1GuestAddressSpace.loadLayout(in):null;
        for(int n=0;n<count;n++){SavedLease lease=leases.get(n);if((lease.kind==Pc1HeapLease.AddressKind.GUEST32)!=(guestLayout!=null&&guestLayout.hasLeaseIndex(n)))throw new IOException("PC1 Guest32 lease has no matching address-space layout");if(lease.kind==Pc1HeapLease.AddressKind.GUEST32&&lease.heapOwned!=(guestLayout.leaseKind(n)==Pc1GuestAddressSpace.Kind.HEAP))throw new IOException("PC1 Guest32 allocator/borrowed ownership mismatch");}return new Image(raw,masks,opaque,bottom,bottomOffsets,leases,row,rowMask,rowIndex,rowOffset,guestLayout);
    }
    /** Fresh physical owner bindings plus exact saved guest VA layout. The
     *same external DIB object may also belong to a retired/staging world. */
    public static Pc1ImagePool restore(Image image,Pc1HeapLease.Heap heap,Rebinder rebinder)throws IOException{
        Pc1ImagePool result=new Pc1ImagePool(heap);result.restoreInto(image,rebinder);return result;
    }
    /** Fill the predeclared empty pool whose borrowed metadata views may
     *already be bound. Foreign owners must be staged before sole-layout adopt. */
    public void restoreInto(Image image,Rebinder rebinder)throws IOException{
        if(image==null||rebinder==null)throw new IOException("PC1 image restore requires address-authoritative rebinding");if(nativeDepth!=0||!blocks.isEmpty()||rowLease!=null)throw new IOException("PC1 image restore requires fresh empty staged pool");Pc1ImagePool result=this;if((image.guestLayout!=null)!=(result.guest!=null))throw new IOException("PC1 observed/Guest32 snapshot contracts cannot be silently migrated");List<Pc1HeapLease> leases=new ArrayList<>();
        for(SavedLease saved:image.leases){Pc1HeapLease lease=rebinder.bind(saved);if(lease==null||lease.physicalAddressKind()!=saved.physicalKind||saved.kind!=Pc1HeapLease.AddressKind.GUEST32&&lease.hasObserved32Address()&&lease.observedAddress()!=saved.address||!Arrays.equals(lease.copyBytes(),saved.bytes))throw new IOException("PC1 restored image lease lacks the saved address/backing authority");result.register(lease,saved.heapOwned,true);leases.add(lease);}
        if(result.guest!=null)result.guest.restoreLayout(image.guestLayout,leases);
        Arrays.fill(result.opaqueBase,null);Arrays.fill(result.opaqueBottom,null);Arrays.fill(result.opaqueDangling,false);Arrays.fill(result.bottomDangling,false);result.rowLease=null;result.rowDangling=false;System.arraycopy(image.raw,0,result.raw,0,BYTES);for(int n=0;n<COUNT;n++){for(int b=0;b<8;b++)result.known[n*RECORD_BYTES+b]=(image.masks[n]&(1<<b))!=0;if(image.opaque[n]>=0)result.opaqueBase[n]=leases.get(image.opaque[n]);else if(image.opaque[n]==-2)result.opaqueDangling[n]=true;if(image.bottom[n]>=0)result.opaqueBottom[n]=leases.get(image.bottom[n]);else if(image.bottom[n]==-2)result.bottomDangling[n]=true;result.bottomDisplacement[n]=image.bottomOffsets[n];}System.arraycopy(image.row,0,result.rowRaw,0,4);result.rowMask=image.rowMask;result.rowDisplacement=image.rowOffset;if(image.rowIndex>=0)result.rowLease=leases.get(image.rowIndex);else if(image.rowIndex==-2)result.rowDangling=true;
    }
}
