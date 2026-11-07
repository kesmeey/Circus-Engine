package org.circus.core;

import java.io.*;
import java.util.*;

/** Explicit32-bit guest virtual addresses over existing allocation owners.
 * Values are neither host-pointer truncations nor Java object IDs. This
 *image-space contract does not claim to implement CRT/stack/OS-handle space. */
public final class Pc1GuestAddressSpace implements Pc1InlineText.Memory {
    private static final int FORMAT=0x47333249,VERSION=2,MAX_ENTRIES=10000;
    public static final String POLICY="PC1_GUEST_IMAGE_FIRST_FIT_REUSE_V1";
    public enum Kind { HEAP, BORROWED }
    public static final class Layout {
        public final long start,end;public final int alignment;
        public Layout(int start,long end,int alignment)throws IOException{
            this.start=start&0xffffffffL;this.end=end;this.alignment=alignment;
            if(this.start==0||end<=this.start||end>0x100000000L||alignment<1||alignment>4096||(alignment&(alignment-1))!=0||(this.start&(alignment-1))!=0)throw new IOException("Guest32 arena/alignment contract");
        }
        /** Explicit guest policy, not an observed original Windows heap base. */
        public static Layout imageHeapV1()throws IOException{return new Layout(0x20000000,0x70000000L,8);}
        public String identity(){return POLICY+":"+Long.toHexString(start)+":"+Long.toHexString(end)+":"+alignment;}
    }
    private static final class Range {final long base,length;final String name;Range(long base,long length,String name){this.base=base;this.length=length;this.name=name;}long end(){return base+length;}}
    /** Stable typed reference to bytes saved by a different canonical owner.
     * This identity is not an address and is never exposed to native code. */
    public static final class ExternalOwner {
        public final String type,id;
        public ExternalOwner(String type,String id)throws IOException{if(type==null||id==null||type.length()==0||id.length()==0||type.length()>256||id.length()>1024)throw new IOException("Guest32 external owner type/id required");this.type=type;this.id=id;}
        @Override public boolean equals(Object other){return other instanceof ExternalOwner&&type.equals(((ExternalOwner)other).type)&&id.equals(((ExternalOwner)other).id);}
        @Override public int hashCode(){return type.hashCode()*31+id.hashCode();}
    }
    private static final class Mapping {final long base,span;final Pc1HeapLease lease;final Kind kind;final boolean automatic;final ExternalOwner owner;Mapping(long base,long span,Pc1HeapLease lease,Kind kind,boolean automatic,ExternalOwner owner){this.base=base;this.span=span;this.lease=lease;this.kind=kind;this.automatic=automatic;this.owner=owner;}long end(){return base+span;}}
    private final Layout layout;
    private final List<Range> reservations=new ArrayList<>(),statics=new ArrayList<>();
    private final TreeMap<Long,Mapping> mappings=new TreeMap<>();
    private final Map<Pc1HeapLease,Mapping> byLease=new IdentityHashMap<>();
    private Pc1InlineText.Memory staticMemory;private Pc1NativeMemory boundBus;
    private int nativeDepth;
    public Pc1GuestAddressSpace(Layout layout)throws IOException{this(layout,true);}
    private Pc1GuestAddressSpace(Layout layout,boolean seed)throws IOException{if(layout==null)throw new IOException("Guest32 explicit layout required");this.layout=layout;if(seed){if(overlap(layout.start,layout.end-layout.start,0x00400000,0x00800000L))throw new IOException("Guest32 arena overlaps original PE image");reserve(0x00400000,0x00800000L,"original PC1 PE virtual-image reservation");}}
    public Layout layout(){return layout;}
    public String contractIdentity(){return layout.identity();}
    private static boolean overlap(long a,long n,long b,long m){return a<b+m&&b<a+n;}
    private static void addressRange(long address,long length)throws IOException{if(address<=0||length<=0||address+length>0x100000000L)throw new IOException("Guest32 range leaves nonnull32-bit address space");}
    private long rounded(int length){return((long)length+layout.alignment-1)&~((long)layout.alignment-1);}
    public void reserve(int address,long length,String purpose)throws IOException{
        long base=address&0xffffffffL;addressRange(base,length);if(purpose==null||purpose.length()==0)throw new IOException("Guest32 reservation authority required");
        for(Range range:reservations)if(overlap(base,length,range.base,range.length))throw new IOException("Guest32 overlapping reservations");for(Mapping map:mappings.values())if(overlap(base,length,map.base,map.span))throw new IOException("Guest32 reservation overlaps a mapped allocation");reservations.add(new Range(base,length,purpose));
    }
    /** NativeMemory uses this before publishing a fixed owner. No silent
     *static priority is allowed to conceal a guest arena/mapping collision. */
    public void requireStaticDisjoint(int address,int length)throws IOException{long base=address&0xffffffffL;if(length<=0||base+length>0x100000000L)throw new IOException("Guest32 invalid static range");if(overlap(base,length,layout.start,layout.end-layout.start))throw new IOException("Native fixed region overlaps Guest32 arena");for(Mapping map:mappings.values())if(overlap(base,length,map.base,map.span))throw new IOException("Native fixed region overlaps Guest32 mapping");}
    void rememberStatic(int address,int length){statics.add(new Range(address&0xffffffffL,length,"borrowed canonical native owner"));}
    void attachBus(Pc1NativeMemory memory)throws IOException{if(boundBus!=null&&boundBus!=memory)throw new IOException("Guest32 space already belongs to another native bus");boundBus=memory;staticMemory=new Pc1InlineText.Memory(){public int read(int address)throws IOException{return memory.readAddress(address);}public void write(int address,int value)throws IOException{memory.writeAddress(address,value);}};}
    public void bindTo(Pc1NativeMemory memory)throws IOException{if(memory==null)throw new IOException("Guest32 native bus required");memory.bindGuestAddressSpace(this);}
    public void beginNativeCall(){nativeDepth++;}
    public void endNativeCall(){if(nativeDepth<=0)throw new IllegalStateException("Guest32 native boundary imbalance");nativeDepth--;}
    private void mappingDisjoint(long base,long span)throws IOException{
        addressRange(base,span);for(Range range:reservations)if(overlap(base,span,range.base,range.length))throw new IOException("Guest32 mapping overlaps reserved native/static region");for(Range range:statics)if(overlap(base,span,range.base,range.length))throw new IOException("Guest32 mapping overlaps fixed native owner");for(Mapping map:mappings.values())if(overlap(base,span,map.base,map.span))throw new IOException("Guest32 mappings overlap");
    }
    private static void physicalDisjoint(Pc1HeapLease first,Pc1HeapLease second)throws IOException{if(first==second||first.physicalAddressKind()==Pc1HeapLease.AddressKind.MANAGED||second.physicalAddressKind()==Pc1HeapLease.AddressKind.MANAGED)return;long a=first.observedAddress(),b=second.observedAddress();if(Long.compareUnsigned(a,b+second.length())<0&&Long.compareUnsigned(b,a+first.length())<0)throw new IOException("Guest32 physical allocation overlap requires the same canonical lease");}
    private int publish(long base,long span,Pc1HeapLease lease,Kind kind,boolean automatic,ExternalOwner owner)throws IOException{
        if(lease==null||!lease.live()||kind==null)throw new IOException("Guest32 mapping requires live allocation and typed ownership");if(kind==Kind.HEAP&&lease.physicalAddressKind()==Pc1HeapLease.AddressKind.MANAGED)throw new IOException("Guest32 native heap requires actual physical allocation authority");for(Mapping map:mappings.values())physicalDisjoint(lease,map.lease);Mapping old=byLease.get(lease);if(old!=null){if(old.base==base&&old.span==span&&old.kind==kind&&Objects.equals(old.owner,owner))return(int)base;throw new IOException("Guest32 lease already has a different mapping in this space");}
        if(owner!=null)for(Mapping existing:mappings.values())if(owner.equals(existing.owner))throw new IOException("Guest32 external owner already maps another allocation");mappingDisjoint(base,span);Mapping map=new Mapping(base,span,lease,kind,automatic,owner);lease.attachGuest(this);mappings.put(base,map);byLease.put(lease,map);return(int)base;
    }
    /** Fixed mapping is explicit, useful for native CPU reference layouts or
     *reviewed borrowed regions. It does not run the heap placement policy. */
    public int mapAt(int address,Pc1HeapLease lease,Kind kind)throws IOException{return mapAt(address,lease,kind,null);}
    public int mapAt(int address,Pc1HeapLease lease,Kind kind,ExternalOwner owner)throws IOException{return publish(address&0xffffffffL,lease==null?0:lease.length(),lease,kind,false,owner);}
    private int mapAutomatic(Pc1HeapLease lease,Kind kind,ExternalOwner owner)throws IOException{return mapAutomatic(lease,kind,owner,null);}
    private int mapAutomatic(Pc1HeapLease lease,Kind kind,ExternalOwner owner,java.util.function.IntPredicate acceptable)throws IOException{
        if(lease==null||!lease.live())throw new IOException("Guest32 mapping requires a live allocation");Mapping old=byLease.get(lease);if(old!=null){if(old.kind!=kind||!Objects.equals(old.owner,owner))throw new IOException("Guest32 allocation ownership differs");return(int)old.base;}
        long span=rounded(lease.length()),candidate=layout.start;List<Range> occupied=new ArrayList<>();for(Mapping map:mappings.values())occupied.add(new Range(map.base,map.span,"allocation"));for(Range range:reservations)occupied.add(range);for(Range range:statics)occupied.add(range);occupied.sort(Comparator.comparingLong(value->value.base));
        while(true){for(Range range:occupied){if(range.end()<=candidate||range.base>=layout.end)continue;if(candidate+span<=range.base)break;candidate=(range.end()+layout.alignment-1)&~((long)layout.alignment-1);}if(candidate+span>layout.end)throw new Exhausted("Guest32 image arena exhausted");if(acceptable==null||acceptable.test((int)candidate))return publish(candidate,span,lease,kind,true,owner);candidate+=layout.alignment;}
    }
    public int mapHeap(Pc1HeapLease lease)throws IOException{return mapAutomatic(lease,Kind.HEAP,null);}
    public int mapBorrowed(Pc1HeapLease lease)throws IOException{return mapAutomatic(lease,Kind.BORROWED,null);}
    public int mapBorrowed(Pc1HeapLease lease,ExternalOwner owner)throws IOException{if(owner==null)throw new IOException("Guest32 external owner required");return mapAutomatic(lease,Kind.BORROWED,owner);}
    /** Same allocator/policy, with a scalar namespace veto of a candidate
     *word. A veto does not reserve or make that numeric memory address unreadable. */
    public int mapBorrowed(Pc1HeapLease lease,ExternalOwner owner,java.util.function.IntPredicate acceptable)throws IOException{if(owner==null||acceptable==null)throw new IOException("Guest32 typed owner/candidate predicate required");return mapAutomatic(lease,Kind.BORROWED,owner,acceptable);}
    public static final class Exhausted extends IOException{private Exhausted(String message){super(message);}}
    public boolean containsLease(Pc1HeapLease lease){return byLease.containsKey(lease)&&lease.live();}
    public int addressOf(Pc1HeapLease lease)throws IOException{Mapping map=byLease.get(lease);if(map==null||!lease.live())throw new IOException("Guest32 allocation has no live mapping in this address space");return(int)map.base;}
    public Kind kindOf(Pc1HeapLease lease)throws IOException{Mapping map=byLease.get(lease);if(map==null||!lease.live())throw new IOException("Guest32 allocation mapping missing");return map.kind;}
    private Mapping find(int address){long value=address&0xffffffffL;Map.Entry<Long,Mapping> entry=mappings.floorEntry(value);if(entry==null)return null;Mapping map=entry.getValue();return map.lease.live()&&value<map.base+map.lease.length()?map:null;}
    public boolean contains(int address){return find(address)!=null;}
    public Pc1HeapLease allocationAtBase(int address)throws IOException{Mapping map=mappings.get(address&0xffffffffL);if(map==null||!map.lease.live())throw new IOException("Guest32 pointer is not the base of a live mapped allocation");return map.lease;}
    public int readMapped(int address)throws IOException{Mapping map=find(address);if(map==null)throw unmapped(address);return map.lease.readU8((int)((address&0xffffffffL)-map.base));}
    public void writeMapped(int address,int value)throws IOException{Mapping map=find(address);if(map==null)throw unmapped(address);map.lease.writeU8((int)((address&0xffffffffL)-map.base),value);}
    private static IOException unmapped(int address){return new IOException("Guest32 byte access reaches unmapped/reserved-gap memory at0x"+Integer.toHexString(address));}
    @Override public int read(int address)throws IOException{if(contains(address))return readMapped(address);if(staticMemory!=null)return staticMemory.read(address);throw unmapped(address);}
    @Override public void write(int address,int value)throws IOException{if(contains(address)){writeMapped(address,value);return;}if(staticMemory!=null){staticMemory.write(address,value);return;}throw unmapped(address);}
    /** Ordered little-endian byte operations, not a claim about arbitrary
     *x86 faulting MOV-store atomicity across inaccessible pages. */
    public int readI32(int address)throws IOException{int value=0;for(int n=0;n<4;n++)value|=read(address+n)<<(n*8);return value;}
    public void writeI32(int address,int value)throws IOException{for(int n=0;n<4;n++)write(address+n,value>>>(n*8));}
    /** Detach a guest view without freeing the physical owner (staging abort).
     *Ordinary successful physical retirement calls this automatically. */
    public void unmap(Pc1HeapLease lease){Mapping map=byLease.remove(lease);if(map!=null)mappings.remove(map.base);lease.detachGuest(this);}
    /** Ends this guest view only. Physical Heap/Window owners still dispose
     *their resources; another staging world may borrow the same DIB lease. */
    public void detachAllViews(){for(Pc1HeapLease lease:new ArrayList<>(byLease.keySet()))unmap(lease);}
    void retired(Pc1HeapLease lease){unmap(lease);}
    public int mappingCount(){return mappings.size();}
    public interface LeaseIndex {int indexOf(Pc1HeapLease lease)throws IOException;}
    private static final class Entry {final long base,span;final int length,index;final Kind kind;final boolean automatic;final ExternalOwner owner;final Pc1HeapLease.AddressKind physicalKind;Entry(long base,long span,int length,int index,Kind kind,boolean automatic,ExternalOwner owner,Pc1HeapLease.AddressKind physicalKind){this.base=base;this.span=span;this.length=length;this.index=index;this.kind=kind;this.automatic=automatic;this.owner=owner;this.physicalKind=physicalKind;}}
    public static final class ExternalMapping {
        public final int address,length;public final long reservedLength;public final Kind kind;public final ExternalOwner owner;public final Pc1HeapLease.AddressKind physicalKind;
        private ExternalMapping(Entry entry){address=(int)entry.base;length=entry.length;reservedLength=entry.span;kind=entry.kind;owner=entry.owner;physicalKind=entry.physicalKind;}
    }
    public static final class Image {
        private final Layout layout;private final List<Range> reservations;private final List<Entry> entries;
        private Image(Layout layout,List<Range> reservations,List<Entry> entries){this.layout=layout;this.reservations=reservations;this.entries=entries;}
        public String contractIdentity(){return layout.identity();}public int mappingCount(){return entries.size();}
        public boolean hasLeaseIndex(int index){if(index<0)return false;for(Entry entry:entries)if(entry.index==index)return true;return false;}
        public Kind leaseKind(int index)throws IOException{for(Entry entry:entries)if(entry.index==index&&index>=0)return entry.kind;throw new IOException("Guest32 saved allocation has no mapping");}
        /** Descriptors only: the external owner's codec retains its bytes once. */
        public List<ExternalMapping> externalMappings(){List<ExternalMapping> result=new ArrayList<>();for(Entry entry:entries)if(entry.index<0)result.add(new ExternalMapping(entry));return Collections.unmodifiableList(result);}
    }
    /** Sole complete layout. The allocation registry writes image bytes once;
     * external typed owners write their own payloads, never another layout. */
    public void saveLayout(DataOutput out,LeaseIndex indices)throws IOException{
        if(nativeDepth!=0)throw new IOException("Guest32 snapshot during active allocation/free call");if(indices==null)throw new IOException("Guest32 snapshot requires canonical allocation indices");
        out.writeInt(FORMAT);out.writeInt(VERSION);out.writeInt((int)layout.start);out.writeLong(layout.end);out.writeInt(layout.alignment);out.writeUTF(POLICY);out.writeInt(reservations.size());for(Range range:reservations){out.writeInt((int)range.base);out.writeLong(range.length);out.writeUTF(range.name);}out.writeInt(mappings.size());
        for(Mapping map:mappings.values()){if(!map.lease.live())throw new IOException("Guest32 snapshot contains retired mapping");int index=indices.indexOf(map.lease);if(index< -1||index>=MAX_ENTRIES||index<0&&map.owner==null)throw new IOException("Guest32 mapping needs canonical payload index or typed external owner");out.writeInt((int)map.base);out.writeLong(map.span);out.writeInt(map.lease.length());out.writeInt(index);out.writeByte(map.kind.ordinal());out.writeBoolean(map.automatic);out.writeByte(map.lease.physicalAddressKind().ordinal());if(index<0){out.writeUTF(map.owner.type);out.writeUTF(map.owner.id);}}
    }
    public static Image loadLayout(DataInput in)throws IOException{
        if(in.readInt()!=FORMAT)throw new IOException("Guest32 layout format");int version=in.readInt();if(version<1||version>VERSION)throw new IOException("Guest32 layout format version");Layout layout=new Layout(in.readInt(),in.readLong(),in.readInt());if(!POLICY.equals(in.readUTF()))throw new IOException("Guest32 placement policy differs");Pc1GuestAddressSpace check=new Pc1GuestAddressSpace(layout,false);int count=in.readInt();if(count<0||count>MAX_ENTRIES)throw new IOException("Guest32 reservation count");for(int n=0;n<count;n++)check.reserve(in.readInt(),in.readLong(),in.readUTF());int size=in.readInt();if(size<0||size>MAX_ENTRIES)throw new IOException("Guest32 mapping count");List<Entry> entries=new ArrayList<>();List<Range> spans=new ArrayList<>();Set<Integer> ids=new HashSet<>();Set<ExternalOwner> owners=new HashSet<>();
        for(int n=0;n<size;n++){long base=in.readInt()&0xffffffffL,span=in.readLong();int length=in.readInt(),index=in.readInt(),kind=in.readUnsignedByte();boolean automatic=in.readBoolean();int physical=version>=2?in.readUnsignedByte():-1;ExternalOwner owner=index<0&&version>=2?new ExternalOwner(in.readUTF(),in.readUTF()):null;addressRange(base,span);if(length<=0||span<length||index< -1||index>=MAX_ENTRIES||kind>=Kind.values().length||index>=0&&!ids.add(index)||index<0&&(owner==null||!owners.add(owner))||physical>=Pc1HeapLease.AddressKind.GUEST32.ordinal())throw new IOException("Guest32 mapping descriptor");if(automatic&&(base<layout.start||base+span>layout.end||(base&(layout.alignment-1))!=0||span!=check.rounded(length))||!automatic&&span!=length)throw new IOException("Guest32 saved allocation placement");for(Range range:check.reservations)if(overlap(base,span,range.base,range.length))throw new IOException("Guest32 saved mapping overlaps reservation");for(Range range:spans)if(overlap(base,span,range.base,range.length))throw new IOException("Guest32 saved mappings overlap");spans.add(new Range(base,span,"mapping"));entries.add(new Entry(base,span,length,index,Kind.values()[kind],automatic,owner,physical<0?null:Pc1HeapLease.AddressKind.values()[physical]));}
        boolean pe=false;for(Range range:check.reservations)if(range.base==0x00400000&&range.length==0x00800000L&&range.name.equals("original PC1 PE virtual-image reservation"))pe=true;if(!pe)throw new IOException("Guest32 saved layout lacks the original PE reservation");
        return new Image(layout,new ArrayList<>(check.reservations),entries);
    }
    public static Pc1GuestAddressSpace emptyLike(Image image)throws IOException{if(image==null)throw new IOException("Guest32 saved layout required");Pc1GuestAddressSpace result=new Pc1GuestAddressSpace(image.layout,false);result.reservations.addAll(image.reservations);return result;}
    private boolean sameReservations(Image image){if(reservations.size()!=image.reservations.size())return false;for(int n=0;n<reservations.size();n++){Range a=reservations.get(n),b=image.reservations.get(n);if(a.base!=b.base||a.length!=b.length||!a.name.equals(b.name))return false;}return true;}
    private void requireImage(Image image)throws IOException{if(image==null||!layout.identity().equals(image.layout.identity())||!sameReservations(image))throw new IOException("Guest32 restore layout/policy mismatch");if(nativeDepth!=0)throw new IOException("Guest32 restore during native call");}
    /** Stage a separately restored typed owner before the image-pool restore.
     * The caller must obtain this lease from that owner's validated codec/API;
     * an arbitrary string is never sufficient to infer bytes or COM lifetime. */
    public int stageExternal(Image image,ExternalOwner owner,Pc1HeapLease lease)throws IOException{
        requireImage(image);if(owner==null)throw new IOException("Guest32 typed external owner required");for(Entry entry:image.entries)if(entry.index<0&&owner.equals(entry.owner)){if(lease==null||!lease.live()||lease.length()!=entry.length||lease.physicalAddressKind()!=entry.physicalKind)throw new IOException("Guest32 external owner backing/type/length mismatch");return publish(entry.base,entry.span,lease,entry.kind,entry.automatic,owner);}throw new IOException("Guest32 external owner absent from saved layout");
    }
    public void restoreLayout(Image image,List<Pc1HeapLease> leases)throws IOException{
        requireImage(image);if(leases==null)throw new IOException("Guest32 canonical allocation registry required");TreeMap<Long,Mapping> staged=new TreeMap<>();Map<Pc1HeapLease,Mapping> stagedLeases=new IdentityHashMap<>();
        for(Entry entry:image.entries){Pc1HeapLease lease;if(entry.index<0){Mapping external=null;for(Mapping candidate:mappings.values())if(entry.owner.equals(candidate.owner)){external=candidate;break;}if(external==null)throw new IOException("Guest32 external owner must be staged before image restoration");lease=external.lease;}else{if(entry.index>=leases.size())throw new IOException("Guest32 saved allocation index");lease=leases.get(entry.index);}if(lease==null||!lease.live()||lease.length()!=entry.length||entry.physicalKind!=null&&lease.physicalAddressKind()!=entry.physicalKind||stagedLeases.containsKey(lease))throw new IOException("Guest32 restore requires unique live backing of exact size/type");for(Range range:statics)if(overlap(entry.base,entry.span,range.base,range.length))throw new IOException("Guest32 restored mapping overlaps fixed owner");Mapping existing=byLease.get(lease);if(existing!=null&&(existing.base!=entry.base||existing.span!=entry.span||existing.kind!=entry.kind||!Objects.equals(existing.owner,entry.owner)))throw new IOException("Guest32 restore must stage into an empty/compatible address space");for(Mapping previous:staged.values())physicalDisjoint(lease,previous.lease);Mapping map=new Mapping(entry.base,entry.span,lease,entry.kind,entry.automatic,entry.owner);staged.put(entry.base,map);stagedLeases.put(lease,map);}
        for(Mapping old:mappings.values())if(!stagedLeases.containsKey(old.lease))throw new IOException("Guest32 restore would discard an unrelated live mapping");
        for(Mapping map:staged.values())map.lease.attachGuest(this);mappings.clear();mappings.putAll(staged);byLease.clear();byLease.putAll(stagedLeases);
    }
}
