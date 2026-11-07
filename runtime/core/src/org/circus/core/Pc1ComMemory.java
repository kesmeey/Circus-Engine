package org.circus.core;

import java.io.*;
import java.nio.*;
import java.util.*;

/**Public32-bit COM ABI views over the world's existing Guest32 domain.
 *Only the interface's vtable DWORD and documented vtable DWORDs are known.
 *No private COM bytes, native machine code, physical pointer truncation, or
 *surrogate reference count is invented. Physical leaves remain required. */
public final class Pc1ComMemory {
    private static final int FORMAT=0x5031434d,VERSION=1,MAX_OBJECTS=10000,MAX_PROOF=65536;
    public enum Kind { DIRECT_SOUND,DIRECT_SOUND_BUFFER }
    public static final String INTERFACE_OWNER="PC1_COM_INTERFACE",VTABLE_OWNER="PC1_COM_VTABLE",CALL_OWNER="PC1_COM_CALL";
    public static final class Method {
        public final Kind kind;public final int slot,argumentWords;public final String name;
        private Method(Kind kind,int slot,int words,String name){this.kind=kind;this.slot=slot;argumentWords=words;this.name=name;}
        public int byteOffset(){return slot*4;}
    }
    private static final String[][] NAMES={{"QueryInterface","AddRef","Release","CreateSoundBuffer","GetCaps","DuplicateSoundBuffer","SetCooperativeLevel","Compact","GetSpeakerConfig","SetSpeakerConfig","Initialize"},{"QueryInterface","AddRef","Release","GetCaps","GetCurrentPosition","GetFormat","GetVolume","GetPan","GetFrequency","GetStatus","Initialize","Lock","Play","SetCurrentPosition","SetFormat","SetVolume","SetPan","SetFrequency","Stop","Unlock","Restore"}};
    //Includes the THIS pointer. This is stdcall32 stack shape, not x64 ABI.
    private static final int[][] WORDS={{3,1,1,4,2,3,3,1,2,2,2},{3,1,1,2,3,4,2,2,2,2,3,8,4,2,2,2,2,2,1,5,1}};
    public static Method method(Kind kind,int slot)throws IOException{if(kind==null||slot<0||slot>=NAMES[kind.ordinal()].length)throw new IOException("PC1 COM vtable slot outside the known public interface");return new Method(kind,slot,WORDS[kind.ordinal()][slot],NAMES[kind.ordinal()][slot]);}
    public static int methodCount(Kind kind){return NAMES[kind.ordinal()].length;}
    /**Reads actual known caller stack words, including THIS at index0.
     *Missing operands are not manufactured as zero. */
    public interface Stack {int word(int index)throws IOException;}
    public interface Leaf {
        /**Stable code/ABI identity, not a physical64 pointer rendered as ID. */
        Pc1GuestAddressSpace.ExternalOwner owner();
        Method method();
        String contractId();
        byte[] restorationProof()throws IOException;
        /**Receives the leaf selected by current raw memory and the actual
         *known THIS association. Wrong-class/unknown ABI stays a required leaf.
         *Must call the physical API, including actual AddRef/Release effects. */
        int invoke(Binding actualThis,Stack callerStack)throws IOException;
    }
    public interface Binding {
        Kind kind();
        /**Actual current64-bit interface identity, used only for deduplication;
         *never serialized as the portable interface DWORD. May be0 for a dead
         *provider. Binding belongs to one object lifetime, never a reused slot. */
        long physicalIdentity()throws IOException;
        boolean live()throws IOException;
        /**Observed/verified public-vtable class, e.g. primary vs secondary.
         *Do not guess that all IDirectSoundBuffer objects share one table. */
        Pc1GuestAddressSpace.ExternalOwner vtableOwner();
        Leaf leaf(int slot)throws IOException;
        String contractId();
        /**Provider owns physical graph/adoption proof and ref lifetimes. */
        byte[] restorationProof()throws IOException;
    }
    private static final class Storage implements Pc1HeapLease.Storage {
        final ByteBuffer bytes;boolean known;Storage(int size){bytes=ByteBuffer.allocateDirect(size).order(ByteOrder.LITTLE_ENDIAN);}Storage(byte[] image){this(image.length);bytes.put(image);known=true;}
        private void requireKnown()throws IOException{if(!known)throw new IOException("PC1 COM incomplete ABI publication has no known readable bytes");}
        public int length(){return bytes.capacity();}public int read(int n)throws IOException{requireKnown();return bytes.get(n)&255;}public void write(int n,int value)throws IOException{requireKnown();bytes.put(n,(byte)value);}
        byte[] image()throws IOException{requireKnown();byte[] out=new byte[length()];for(int n=0;n<out.length;n++)out[n]=bytes.get(n);return out;}
        ByteBuffer view()throws IOException{requireKnown();ByteBuffer out=bytes.duplicate().order(ByteOrder.LITTLE_ENDIAN);out.clear();return out;}
    }
    private static final class Table {
        final Kind kind;final Pc1GuestAddressSpace.ExternalOwner owner;final Storage storage;final Pc1HeapLease lease;final int address;final Pc1GuestAddressSpace.ExternalOwner[] originalLeaves;
        Table(Kind kind,Pc1GuestAddressSpace.ExternalOwner owner,Storage storage,Pc1HeapLease lease,int address,Pc1GuestAddressSpace.ExternalOwner[] leaves){this.kind=kind;this.owner=owner;this.storage=storage;this.lease=lease;this.address=address;originalLeaves=leaves;}
    }
    private static final class Code {
        final Leaf physical;final String contract;final Pc1HeapLease lease;final int address;final Method method;final Pc1GuestAddressSpace.ExternalOwner owner;
        Code(Leaf physical,String contract,Pc1HeapLease lease,int address){this.physical=physical;this.contract=contract;this.lease=lease;this.address=address;method=physical.method();owner=physical.owner();}
    }
    public static final class ObjectRef {
        public final Pc1GuestAddressSpace.ExternalOwner owner;public final Kind kind;public final int address;
        private final Binding physical;private final String contract;private final Storage header;private final Pc1HeapLease lease;private final long physicalAtBirth;private final Pc1GuestAddressSpace.ExternalOwner originalTable;
        private ObjectRef(Pc1GuestAddressSpace.ExternalOwner owner,Kind kind,int address,Binding binding,String contract,Storage header,Pc1HeapLease lease)throws IOException{this.owner=owner;this.kind=kind;this.address=address;physical=binding;this.contract=contract;this.header=header;this.lease=lease;physicalAtBirth=binding.physicalIdentity();originalTable=binding.vtableOwner();}
        public boolean live()throws IOException{boolean live=physical.live();if(live&&physical.physicalIdentity()!=physicalAtBirth)throw new IOException("PC1 COM binding changed physical object during one interface lifetime");return live;}
        public ByteBuffer borrowedHeader()throws IOException{return header.view();}
        public Binding physical(){return physical;}
    }
    public static final class Call {
        public final int pointerCell,interfaceAddress,vtableAddress,leafAddress,byteOffset;
        public final Method method;public final ObjectRef object;
        private final Code code;private final Pc1ComMemory world;
        private Call(Pc1ComMemory world,int cell,int objectAddress,int vtable,int leaf,int offset,ObjectRef object,Code code){this.world=world;pointerCell=cell;interfaceAddress=objectAddress;vtableAddress=vtable;leafAddress=leaf;byteOffset=offset;this.object=object;this.code=code;method=code.method;}
    }
    private final Pc1GuestAddressSpace space;
    private final LinkedHashMap<Pc1GuestAddressSpace.ExternalOwner,ObjectRef> objects=new LinkedHashMap<>();
    private final LinkedHashMap<Pc1GuestAddressSpace.ExternalOwner,Table> tables=new LinkedHashMap<>();
    private final LinkedHashMap<Pc1GuestAddressSpace.ExternalOwner,Code> code=new LinkedHashMap<>();
    private final Map<Integer,ObjectRef> objectsAt=new HashMap<>();private final Map<Integer,Code> codeAt=new HashMap<>();
    private int activeCalls;private boolean detached;
    public Pc1ComMemory(Pc1GuestAddressSpace space)throws IOException{if(space==null)throw new IOException("PC1 COM requires existing Guest32 address domain");this.space=space;}
    public Pc1GuestAddressSpace guestAddressSpace(){return space;}
    private void active()throws IOException{if(detached)throw new IOException("PC1 COM guest views are detached from this world");}
    private static String contract(String value)throws IOException{if(value==null||value.length()==0||value.length()>1024)throw new IOException("PC1 COM explicit physical ABI contract required");return value;}
    private static Pc1GuestAddressSpace.ExternalOwner owner(Pc1GuestAddressSpace.ExternalOwner owner,String role)throws IOException{if(owner==null||!role.equals(owner.type))throw new IOException("PC1 COM typed external owner role must be "+role);return owner;}
    private static byte[] proof(byte[] value)throws IOException{if(value==null||value.length>MAX_PROOF)throw new IOException("PC1 COM physical adoption proof required");return value;}
    private static boolean same(Method a,Method b){return a!=null&&b!=null&&a.kind==b.kind&&a.slot==b.slot&&a.argumentWords==b.argumentWords;}
    private static Pc1HeapLease opaque(final Pc1GuestAddressSpace.ExternalOwner owner)throws IOException{return Pc1HeapLease.managed(new Pc1HeapLease.Storage(){public int length(){return 1;}public int read(int n)throws IOException{throw new IOException("PC1 COM typed OS call leaf has unknown code bytes: "+owner.type+"/"+owner.id);}public void write(int n,int value)throws IOException{throw new IOException("PC1 COM typed OS call leaf has no writable code bytes: "+owner.type+"/"+owner.id);}},"PC1 COM typed call leaf");}
    /**Physical creation/QI has already happened. No AddRef, Release, Stop, or
     *pointer-cell write happens here. Caller publishes address at native MOV. */
    public ObjectRef register(Pc1GuestAddressSpace.ExternalOwner owner,Binding binding)throws IOException{
        active();activeCalls++;try{return registerInner(owner,binding);}finally{activeCalls--;}
    }
    private ObjectRef registerInner(Pc1GuestAddressSpace.ExternalOwner owner,Binding binding)throws IOException{
        owner(owner,INTERFACE_OWNER);if(binding==null||binding.kind()==null||!binding.live())throw new IOException("PC1 COM requires a live typed physical interface");String contract=contract(binding.contractId());long identity=binding.physicalIdentity();if(identity==0||identity==-1L)throw new IOException("PC1 COM physical interface is NULL/invalid");refreshReleased();ObjectRef old=objects.get(owner);if(old!=null)throw new IOException("PC1 COM canonical owner already registered");for(ObjectRef known:objects.values())if(known.live()&&known.physical.physicalIdentity()==identity){if(known.kind!=binding.kind()||!known.originalTable.equals(binding.vtableOwner())||!known.contract.equals(contract))throw new IOException("PC1 COM identical physical interface claimed conflicting ABI/class contracts");return known;}
        Pc1GuestAddressSpace.ExternalOwner tableOwner=owner(binding.vtableOwner(),VTABLE_OWNER);Leaf[] leaves=new Leaf[methodCount(binding.kind())];Pc1GuestAddressSpace.ExternalOwner[] originalLeaves=new Pc1GuestAddressSpace.ExternalOwner[leaves.length];Map<Pc1GuestAddressSpace.ExternalOwner,Leaf> described=new HashMap<>();for(int slot=0;slot<leaves.length;slot++){Leaf leaf=binding.leaf(slot);if(leaf==null||!same(leaf.method(),method(binding.kind(),slot)))throw new IOException("PC1 COM physical leaf ABI shape missing at slot"+slot);originalLeaves[slot]=owner(leaf.owner(),CALL_OWNER);String leafContract=contract(leaf.contractId());Code prior=code.get(leaf.owner());Leaf sibling=described.get(leaf.owner());if(prior!=null&&(!prior.contract.equals(leafContract)||prior.method.argumentWords!=leaf.method().argumentWords)||sibling!=null&&(!sibling.contractId().equals(leafContract)||sibling.method().argumentWords!=leaf.method().argumentWords))throw new IOException("PC1 COM shared leaf has contradictory stdcall cleanup contract");described.put(leaf.owner(),leaf);leaves[slot]=leaf;}
        Table table=tables.get(tableOwner);if(table!=null){if(!table.storage.known||table.kind!=binding.kind()||!Arrays.equals(table.originalLeaves,originalLeaves))throw new IOException("PC1 COM vtable class changed or has incomplete ABI publication");for(Leaf leaf:leaves)if(!code.get(leaf.owner()).contract.equals(leaf.contractId()))throw new IOException("PC1 COM vtable class changed physical leaf contract");}
        if(objects.size()>=MAX_OBJECTS)throw new IOException("PC1 COM object descriptor bound");Storage header=new Storage(4);Pc1HeapLease lease=Pc1HeapLease.managed(header,"known COM public interface header");int address=space.mapBorrowed(lease,owner);ObjectRef object=new ObjectRef(owner,binding.kind(),address,binding,contract,header,lease);objects.put(owner,object);objectsAt.put(address,object);
        //A failed/unknown table provider retains the created header allocation
        //prefix; no fabricated successful interface cell is published.
        if(table==null){Storage storage=new Storage(leaves.length*4);Pc1HeapLease tableLease=Pc1HeapLease.managed(storage,"known COM public vtable DWORDs");int tableAddress=space.mapBorrowed(tableLease,tableOwner);table=new Table(binding.kind(),tableOwner,storage,tableLease,tableAddress,originalLeaves);tables.put(tableOwner,table);
            for(int slot=0;slot<leaves.length;slot++){Leaf leaf=leaves[slot];String leafContract=contract(leaf.contractId());Code entry=code.get(leaf.owner());if(entry==null){Pc1HeapLease executable=opaque(leaf.owner());int leafAddress=space.mapBorrowed(executable,leaf.owner());entry=new Code(leaf,leafContract,executable,leafAddress);code.put(leaf.owner(),entry);codeAt.put(leafAddress,entry);}else if(!entry.contract.equals(leafContract)||entry.physical.method().argumentWords!=leaf.method().argumentWords)throw new IOException("PC1 COM shared leaf has contradictory stdcall cleanup contract");table.storage.bytes.putInt(slot*4,entry.address);}
            table.storage.known=true;
        }header.bytes.putInt(0,table.address);header.known=true;return object;
    }
    public ObjectRef findOwner(Pc1GuestAddressSpace.ExternalOwner owner){return objects.get(owner);}
    public List<ObjectRef> interfaces()throws IOException{active();return Collections.unmodifiableList(new ArrayList<>(objects.values()));}
    public int vtableAddress(Pc1GuestAddressSpace.ExternalOwner owner)throws IOException{active();Table table=tables.get(owner);if(table==null)throw new IOException("PC1 COM vtable owner absent");return table.address;}
    public int callAddress(Pc1GuestAddressSpace.ExternalOwner owner)throws IOException{active();Code leaf=code.get(owner);if(leaf==null)throw new IOException("PC1 COM typed call leaf owner absent");return leaf.address;}
    public Leaf physicalLeaf(Pc1GuestAddressSpace.ExternalOwner owner)throws IOException{active();Code leaf=code.get(owner);if(leaf==null)throw new IOException("PC1 COM typed call leaf owner absent");return leaf.physical;}
    public ByteBuffer borrowedVtable(Pc1GuestAddressSpace.ExternalOwner owner)throws IOException{active();Table table=tables.get(owner);if(table==null)throw new IOException("PC1 COM vtable owner absent");return table.storage.view();}
    /**Only actual provider liveness retires headers. Raw cells are untouched.
     *No helper-owned approximate AddRef/Release count exists. */
    public void refreshReleased()throws IOException{active();for(ObjectRef object:objects.values())if(object.lease.live()&&!object.live()){object.lease.retired();if(objectsAt.get(object.address)==object)objectsAt.remove(object.address);}}
    /**Staging abort/world teardown only. Actual COM references belong to the
     *backend; this does not call Release or modify any engine pointer cell. */
    public void detachViews()throws IOException{if(detached)return;if(activeCalls!=0)throw new IOException("PC1 COM cannot detach during a native registration/call");for(ObjectRef object:objects.values())space.unmap(object.lease);for(Table table:tables.values())space.unmap(table.lease);for(Code leaf:code.values())space.unmap(leaf.lease);detached=true;}
    /**Always executes the original pointer-chain reads, including mutable
     *vtable/header aliases. No numeric slot cache can bypass these reads. */
    public Call resolveCell(int pointerCell,int byteOffset)throws IOException{active();int object=space.readI32(pointerCell);return resolve(pointerCell,object,byteOffset);}
    public Call resolve(int interfaceAddress,int byteOffset)throws IOException{return resolve(0,interfaceAddress,byteOffset);}
    private Call resolve(int cell,int instance,int byteOffset)throws IOException{active();int vtable=space.readI32(instance),leaf=space.readI32(vtable+byteOffset);Code target=codeAt.get(leaf);if(target==null)throw new IOException("PC1 COM current raw leaf has no verified OS-call owner at0x"+Integer.toHexString(leaf));return new Call(this,cell,instance,vtable,leaf,byteOffset,objectsAt.get(instance),target);}
    public int invoke(final Call call,Stack stack,int callerArgumentWords)throws IOException{
        active();if(call==null||call.world!=this||stack==null||callerArgumentWords<1)throw new IOException("PC1 COM caller frame/current world required");ObjectRef actualThis=objectsAt.get(stack.word(0));if(actualThis==null||!actualThis.live())throw new IOException("PC1 COM current THIS needs live physical ABI owner after raw vtable reads");if(!call.code.owner.equals(call.code.physical.owner())||!same(call.code.method,call.code.physical.method())||!call.code.contract.equals(contract(call.code.physical.contractId())))throw new IOException("PC1 COM verified leaf provider changed ABI identity");
        //The selected leaf reads its operands when its proved native order
        //requires them. Do not pre-read every possible callee operand before
        //a physical failure/early-return prefix. THIS can differ from the
        //interface used to fetch the function, just as an actual x86 stack can.
        activeCalls++;int result;try{result=call.code.physical.invoke(actualThis.physical,stack);}finally{activeCalls--;refreshReleased();}
        if(callerArgumentWords!=call.method.argumentWords)throw new IOException("PC1 COM selected leaf changed stdcall stack cleanup after physical prefix: expected"+callerArgumentWords+" actual"+call.method.argumentWords);return result;
    }
    private static void writeOwner(DataOutput out,Pc1GuestAddressSpace.ExternalOwner owner)throws IOException{out.writeUTF(owner.type);out.writeUTF(owner.id);}
    private static Pc1GuestAddressSpace.ExternalOwner readOwner(DataInput in,String role)throws IOException{return owner(new Pc1GuestAddressSpace.ExternalOwner(in.readUTF(),in.readUTF()),role);}
    private static void writeBytes(DataOutput out,byte[] bytes)throws IOException{out.writeInt(bytes.length);out.write(bytes);}
    private static byte[] readBytes(DataInput in,int limit)throws IOException{int count=in.readInt();if(count<0||count>limit)throw new IOException("PC1 COM image byte bound");byte[] out=new byte[count];in.readFully(out);return out;}
    private static int count(DataInput in)throws IOException{int count=in.readInt();if(count<0||count>MAX_OBJECTS)throw new IOException("PC1 COM image record bound");return count;}
    private static int address(DataInput in)throws IOException{int value=in.readInt();if(value==0||value==-1)throw new IOException("PC1 COM allocation descriptor is NULL/sentinel");return value;}
    private static Kind kind(DataInput in)throws IOException{int kind=in.readUnsignedByte();if(kind>=Kind.values().length)throw new IOException("PC1 COM interface kind");return Kind.values()[kind];}
    public static final class SavedCode {
        public final Pc1GuestAddressSpace.ExternalOwner owner;public final int address;public final Method method;public final String contractId;private final byte[] proof;
        private SavedCode(Pc1GuestAddressSpace.ExternalOwner owner,int address,Method method,String contract,byte[] proof){this.owner=owner;this.address=address;this.method=method;contractId=contract;this.proof=proof;}
        public byte[] restorationProof(){return proof.clone();}
    }
    private static final class SavedTable {
        final Pc1GuestAddressSpace.ExternalOwner owner;final int address;final Kind kind;final byte[] bytes;final Pc1GuestAddressSpace.ExternalOwner[] leaves;
        SavedTable(Pc1GuestAddressSpace.ExternalOwner owner,int address,Kind kind,byte[] bytes,Pc1GuestAddressSpace.ExternalOwner[] leaves){this.owner=owner;this.address=address;this.kind=kind;this.bytes=bytes;this.leaves=leaves;}
    }
    public static final class SavedObject {
        public final Pc1GuestAddressSpace.ExternalOwner owner,vtableOwner;public final int address;public final Kind kind;public final boolean live;public final String contractId;private final byte[] bytes,proof;
        private SavedObject(Pc1GuestAddressSpace.ExternalOwner owner,Pc1GuestAddressSpace.ExternalOwner table,int address,Kind kind,boolean live,String contract,byte[] bytes,byte[] proof){this.owner=owner;vtableOwner=table;this.address=address;this.kind=kind;this.live=live;contractId=contract;this.bytes=bytes;this.proof=proof;}
        public byte[] restorationProof(){return proof.clone();}public byte[] knownHeaderBytes(){return bytes.clone();}
    }
    public static final class Image {
        private final List<SavedCode> code;private final List<SavedTable> tables;private final List<SavedObject> objects;
        private Image(List<SavedCode> code,List<SavedTable> tables,List<SavedObject> objects){this.code=code;this.tables=tables;this.objects=objects;}
        public List<SavedCode> code(){return Collections.unmodifiableList(code);}public List<SavedObject> objects(){return Collections.unmodifiableList(objects);}
    }
    /**Caller holds the actual backend's snapshot boundary. Refresh retirement
     *BEFORE saving the Pool's sole Guest layout; this codec stores no layout,
     *physical pointers, native code, or second copy of another owner's bytes. */
    public void save(DataOutput out)throws IOException{
        active();if(activeCalls!=0)throw new IOException("PC1 COM snapshot during native registration/call");refreshReleased();out.writeInt(FORMAT);out.writeInt(VERSION);out.writeInt(code.size());
        for(Code entry:code.values()){Leaf leaf=entry.physical;if(!entry.owner.equals(leaf.owner())||!same(entry.method,leaf.method())||!entry.contract.equals(contract(leaf.contractId())))throw new IOException("PC1 COM leaf provider contract changed");writeOwner(out,entry.owner);out.writeInt(entry.address);out.writeByte(entry.method.kind.ordinal());out.writeInt(entry.method.slot);out.writeUTF(entry.contract);writeBytes(out,proof(leaf.restorationProof()));}
        out.writeInt(tables.size());for(Table table:tables.values()){writeOwner(out,table.owner);out.writeInt(table.address);out.writeByte(table.kind.ordinal());writeBytes(out,table.storage.image());for(Pc1GuestAddressSpace.ExternalOwner leaf:table.originalLeaves)writeOwner(out,leaf);}
        out.writeInt(objects.size());for(ObjectRef object:objects.values()){if(object.kind!=object.physical.kind()||!object.originalTable.equals(object.physical.vtableOwner())||!object.contract.equals(contract(object.physical.contractId())))throw new IOException("PC1 COM interface provider contract changed");boolean live=object.live();if(live&&!object.lease.live())throw new IOException("PC1 COM physical provider revived a retired interface lifetime");writeOwner(out,object.owner);writeOwner(out,object.originalTable);out.writeInt(object.address);out.writeByte(object.kind.ordinal());out.writeBoolean(live);out.writeUTF(object.contract);writeBytes(out,object.header.image());writeBytes(out,proof(object.physical.restorationProof()));}
    }
    public static Image loadImage(DataInput in)throws IOException{
        if(in.readInt()!=FORMAT||in.readInt()!=VERSION)throw new IOException("PC1 COM image format");List<SavedCode> code=new ArrayList<>();List<SavedTable> tables=new ArrayList<>();List<SavedObject> objects=new ArrayList<>();Set<Pc1GuestAddressSpace.ExternalOwner> keys=new HashSet<>();Set<Integer> liveAddresses=new HashSet<>();Map<Pc1GuestAddressSpace.ExternalOwner,SavedCode> codeByOwner=new HashMap<>();Map<Pc1GuestAddressSpace.ExternalOwner,SavedTable> tableByOwner=new HashMap<>();long total=0;
        int count=count(in);for(int n=0;n<count;n++){Pc1GuestAddressSpace.ExternalOwner owner=readOwner(in,CALL_OWNER);int address=address(in);Kind kind=kind(in);Method method=method(kind,in.readInt());String contract=contract(in.readUTF());byte[] proof=readBytes(in,MAX_PROOF);total+=proof.length;if(!keys.add(owner)||!liveAddresses.add(address)||total>64L*1024*1024)throw new IOException("PC1 COM duplicate code descriptor/bound");SavedCode entry=new SavedCode(owner,address,method,contract,proof);code.add(entry);codeByOwner.put(owner,entry);}
        count=count(in);for(int n=0;n<count;n++){Pc1GuestAddressSpace.ExternalOwner owner=readOwner(in,VTABLE_OWNER);int address=address(in);Kind kind=kind(in);byte[] bytes=readBytes(in,84);if(bytes.length!=methodCount(kind)*4||!keys.add(owner)||!liveAddresses.add(address))throw new IOException("PC1 COM vtable descriptor shape");Pc1GuestAddressSpace.ExternalOwner[] leaves=new Pc1GuestAddressSpace.ExternalOwner[methodCount(kind)];for(int slot=0;slot<leaves.length;slot++){leaves[slot]=readOwner(in,CALL_OWNER);SavedCode entry=codeByOwner.get(leaves[slot]);if(entry==null||entry.method.argumentWords!=method(kind,slot).argumentWords)throw new IOException("PC1 COM vtable initial leaf metadata lacks a compatible ABI owner");}SavedTable table=new SavedTable(owner,address,kind,bytes,leaves);tables.add(table);tableByOwner.put(owner,table);}
        count=count(in);for(int n=0;n<count;n++){Pc1GuestAddressSpace.ExternalOwner owner=readOwner(in,INTERFACE_OWNER),tableOwner=readOwner(in,VTABLE_OWNER);int address=address(in);Kind kind=kind(in);boolean live=in.readBoolean();String contract=contract(in.readUTF());byte[] bytes=readBytes(in,4),proof=readBytes(in,MAX_PROOF);SavedTable table=tableByOwner.get(tableOwner);total+=proof.length;if(bytes.length!=4||table==null||table.kind!=kind||!keys.add(owner)||live&&!liveAddresses.add(address)||total>64L*1024*1024)throw new IOException("PC1 COM interface descriptor shape/bound");objects.add(new SavedObject(owner,tableOwner,address,kind,live,contract,bytes,proof));}
        return new Image(code,tables,objects);
    }
    public interface Rebinder {Leaf code(SavedCode code)throws IOException;Binding object(SavedObject object)throws IOException;}
    /**Physical objects/retained references must already have been reconstructed
     *stopped by their backend. No API call, AddRef/Release, raw-cell rewrite, or
     *default-vtable reset occurs here. Pool restores the sole layout afterward. */
    public static Pc1ComMemory restore(Image image,Pc1GuestAddressSpace space,Pc1GuestAddressSpace.Image layout,Rebinder rebinder)throws IOException{
        if(image==null||space==null||layout==null||rebinder==null)throw new IOException("PC1 COM restore requires shared sole layout and physical rebinder");Pc1ComMemory result=new Pc1ComMemory(space);List<Leaf> leaves=new ArrayList<>();List<Binding> bindings=new ArrayList<>();Set<Long> physicalLive=new HashSet<>();
        //Validate all physical adoption before staging any guest owner.
        for(SavedCode saved:image.code){Leaf leaf=rebinder.code(saved);if(leaf==null||!saved.owner.equals(leaf.owner())||!same(saved.method,leaf.method())||!saved.contractId.equals(contract(leaf.contractId()))||!Arrays.equals(saved.proof,proof(leaf.restorationProof())))throw new IOException("PC1 COM physical call leaf adoption mismatch");leaves.add(leaf);}
        for(SavedObject saved:image.objects){Binding binding=rebinder.object(saved);if(binding==null||binding.kind()!=saved.kind||!saved.vtableOwner.equals(binding.vtableOwner())||binding.live()!=saved.live||!saved.contractId.equals(contract(binding.contractId()))||!Arrays.equals(saved.proof,proof(binding.restorationProof())))throw new IOException("PC1 COM physical interface/retained graph adoption mismatch");if(saved.live){long pointer=binding.physicalIdentity();if(pointer==0||pointer==-1L||!physicalLive.add(pointer))throw new IOException("PC1 COM distinct saved live interfaces collapsed onto one physical object");SavedTable original=null;for(SavedTable table:image.tables)if(table.owner.equals(saved.vtableOwner))original=table;for(int slot=0;slot<methodCount(saved.kind);slot++){Leaf actual=binding.leaf(slot);if(actual==null||!original.leaves[slot].equals(actual.owner())||!same(method(saved.kind,slot),actual.method()))throw new IOException("PC1 COM rebound physical vtable class changed leaf identity");}}bindings.add(binding);}
        for(int n=0;n<image.code.size();n++){SavedCode saved=image.code.get(n);Pc1HeapLease lease=opaque(saved.owner);int address=space.stageExternal(layout,saved.owner,lease);if(address!=saved.address)throw new IOException("PC1 COM saved call leaf layout differs");Code entry=new Code(leaves.get(n),saved.contractId,lease,address);result.code.put(saved.owner,entry);result.codeAt.put(address,entry);}
        for(SavedTable saved:image.tables){Storage bytes=new Storage(saved.bytes);Pc1HeapLease lease=Pc1HeapLease.managed(bytes,"restored known COM public vtable DWORDs");int address=space.stageExternal(layout,saved.owner,lease);if(address!=saved.address)throw new IOException("PC1 COM saved vtable layout differs");result.tables.put(saved.owner,new Table(saved.kind,saved.owner,bytes,lease,address,saved.leaves.clone()));}
        for(int n=0;n<image.objects.size();n++){SavedObject saved=image.objects.get(n);Binding binding=bindings.get(n);Storage header=new Storage(saved.bytes);Pc1HeapLease lease=Pc1HeapLease.managed(header,"restored known COM interface header");if(saved.live){int address=space.stageExternal(layout,saved.owner,lease);if(address!=saved.address)throw new IOException("PC1 COM saved interface layout differs");}else lease.retired();ObjectRef object=new ObjectRef(saved.owner,saved.kind,saved.address,binding,saved.contractId,header,lease);result.objects.put(saved.owner,object);if(saved.live)result.objectsAt.put(saved.address,object);}
        return result;
    }
}
