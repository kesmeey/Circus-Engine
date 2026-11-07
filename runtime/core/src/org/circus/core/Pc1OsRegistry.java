package org.circus.core;

import java.io.*;
import java.util.*;

/** Typed scalar OS handles. These are not readable COM interfaces/vtables.
 * A64-bit physical HANDLE is never truncated. Current physical/liveness state
 * is borrowed from its actual provider on every lookup, including MM closes. */
public final class Pc1OsRegistry {
    private static final int FORMAT=0x50314f52,VERSION=1,MAX_OBJECTS=100000,MAX_PROOF=65536;
    public enum Domain { USER,GDI,KERNEL,HEAP,MODULE }
    public enum Kind {
        WINDOW(Domain.USER),MENU(Domain.USER),ICON(Domain.USER),CURSOR(Domain.USER),
        FONT(Domain.GDI),DC(Domain.GDI),BITMAP(Domain.GDI),BRUSH(Domain.GDI),PEN(Domain.GDI),
        FILE(Domain.KERNEL),MUTEX(Domain.KERNEL),EVENT(Domain.KERNEL),PROCESS_HEAP(Domain.HEAP),MODULE(Domain.MODULE);
        public final Domain domain;Kind(Domain domain){this.domain=domain;}
    }
    /** Authority describes the original logical-word publication, not an
     *assertion that a rebound physical handle keeps that numeric value. */
    public enum Authority { OBSERVED32_BIRTH,GUEST32_OPAQUE }
    public enum Status { LIVE,NULL,SENTINEL,CLOSED,UNKNOWN,WRONG_DOMAIN }
    public interface Binding {
        long physicalHandle()throws IOException;
        boolean live()throws IOException;
        String contractId();
        /** Called only at save/restore validation, never eagerly before a FILE
         *header read. The provider owns quiescence and proof/cursor semantics. */
        byte[] restorationProof()throws IOException;
        /** Association only. Do not write engine raw memory here. Native MM
         *providers use this to register the same word without Java callbacks. */
        default void published(int rawWord)throws IOException{}
    }
    /** The registered physical owner performs this API and maintains its own
     *lifetime/byte leases. FALSE never retires an object or clears raw cells. */
    public interface GdiObjectBinding extends Binding {boolean deleteObject()throws IOException;}
    public interface GdiDcBinding extends GdiObjectBinding {boolean deleteDc()throws IOException;}
    public static final class Scalar {
        public final int rawWord;public final Kind kind;public final Authority authority;public final Pc1GuestAddressSpace.ExternalOwner owner;
        private final Binding provider;private final String contract;private final Pc1HeapLease opaque;
        private Scalar(int raw,Kind kind,Authority authority,Pc1GuestAddressSpace.ExternalOwner owner,Binding provider,String contract,Pc1HeapLease opaque){rawWord=raw;this.kind=kind;this.authority=authority;this.owner=owner;this.provider=provider;this.contract=contract;this.opaque=opaque;}
        public boolean live()throws IOException{return provider.live();}
        public long physicalHandle()throws IOException{if(!provider.live())throw new IOException("PC1 OS object has closed: "+owner.type+"/"+owner.id);long value=provider.physicalHandle();if(value==0||value==-1L)throw new IOException("PC1 live OS provider returned a NULL/invalid physical handle");return value;}
        public String contractId(){return contract;}
        public Binding binding(){return provider;}
    }
    public static final class Resolution {
        public final Status status;public final int rawWord,normalizedWord;public final Scalar object;
        private Resolution(Status status,int raw,int normalized,Scalar object){this.status=status;rawWord=raw;normalizedWord=normalized;this.object=object;}
        /** NULL/sentinel/invalid behavior belongs to the actual API leaf. */
        public long physicalHandle()throws IOException{if(status!=Status.LIVE||object==null)throw new IOException("PC1 OS scalar is not a known live object: "+status+" raw0x"+Integer.toHexString(rawWord));return object.physicalHandle();}
    }
    private static final class Key {
        final Domain domain;final int word;Key(Domain domain,int word){this.domain=domain;this.word=word;}
        @Override public int hashCode(){return domain.ordinal()*31+word;}
        @Override public boolean equals(Object value){return value instanceof Key&&domain==((Key)value).domain&&word==((Key)value).word;}
    }
    private final Pc1GuestAddressSpace space;private final LinkedHashMap<Key,Scalar> objects=new LinkedHashMap<>();private int calls;private boolean failedPublication;
    public Pc1OsRegistry(Pc1GuestAddressSpace space)throws IOException{if(space==null)throw new IOException("PC1 scalar OS registry requires the shared Guest32 address domain");this.space=space;}
    public Pc1GuestAddressSpace guestAddressSpace(){return space;}
    private static int normalized(Kind kind,int word){return kind==Kind.FILE?word&~3:word;}
    private static Key key(Kind kind,int word){return new Key(kind.domain,normalized(kind,word));}
    private static String contract(Binding provider)throws IOException{if(provider==null)throw new IOException("PC1 OS physical binding required");String id=provider.contractId();if(id==null||id.length()==0||id.length()>1024)throw new IOException("PC1 OS provider contract required");return id;}
    private static Pc1HeapLease opaque(Pc1GuestAddressSpace.ExternalOwner owner)throws IOException{return Pc1HeapLease.managed(new Pc1HeapLease.Storage(){public int length(){return 1;}public int read(int offset)throws IOException{throw new IOException("PC1 scalar OS token has no readable ABI/code bytes: "+owner.type+"/"+owner.id);}public void write(int offset,int value)throws IOException{throw new IOException("PC1 scalar OS token has no writable ABI/code bytes: "+owner.type+"/"+owner.id);}},"typed scalar OS token: "+owner.type+"/"+owner.id);}
    private void enter(){calls++;space.beginNativeCall();}
    private void leave(){space.endNativeCall();calls--;}
    public Scalar findOwner(Pc1GuestAddressSpace.ExternalOwner owner){for(Scalar scalar:objects.values())if(scalar.owner.equals(owner))return scalar;return null;}
    public Scalar findPhysical(Domain domain,long physical)throws IOException{for(Scalar scalar:objects.values())if(scalar.kind.domain==domain&&scalar.provider.live()&&scalar.provider.physicalHandle()==physical)return scalar;return null;}
    /** Physical create has already happened. This associates one typed object;
     *the caller's subsequent native MOV publishes its word into one raw cell. */
    public Scalar register(Kind kind,Pc1GuestAddressSpace.ExternalOwner owner,Binding binding)throws IOException{
        enter();try{if(failedPublication)throw new IOException("PC1 OS registry has an incomplete physical publication");if(kind==null||owner==null)throw new IOException("PC1 OS kind/typed owner required");String contract=contract(binding);if(!binding.live())throw new IOException("PC1 OS registration requires a live physical object");long physical=binding.physicalHandle();if(physical==0||physical==-1L)throw new IOException("PC1 NULL/invalid API result must be published as the original raw sentinel, not a live object");Scalar known=findOwner(owner);if(known!=null){if(known.kind==kind&&known.provider==binding)return known;throw new IOException("PC1 OS owner key already belongs to another binding/lifetime");}refreshClosed();for(Scalar existing:objects.values())if(existing.kind.domain==kind.domain&&existing.provider.live()&&existing.provider.physicalHandle()==physical)throw new IOException("PC1 physical OS object already has a canonical registry binding");
            int word=(int)physical;Authority authority=Authority.OBSERVED32_BIRTH;Pc1HeapLease token=null;Scalar collision=objects.get(key(kind,word));if(physical<0||physical>0xffffffffL||word==0||word==-1||normalized(kind,word)==0||collision!=null&&collision.provider.live()){token=opaque(owner);final Set<Integer> blocked=new HashSet<>();for(Scalar existing:objects.values())if(existing.kind.domain==kind.domain&&existing.provider.live())blocked.add(normalized(existing.kind,existing.rawWord));word=space.mapBorrowed(token,owner,candidate->!blocked.contains(normalized(kind,candidate)));authority=Authority.GUEST32_OPAQUE;}
            Scalar scalar=new Scalar(word,kind,authority,owner,binding,contract,token);try{binding.published(word);}catch(IOException|RuntimeException failure){failedPublication=true;throw failure;}objects.put(key(kind,word),scalar);if(objects.size()>MAX_OBJECTS){failedPublication=true;throw new IOException("PC1 OS scalar registry snapshot bound exceeded after publication");}return scalar;
        }finally{leave();}
    }
    /** Query actual providers; never maintain a second Java alive flag. */
    public void refreshClosed()throws IOException{for(Scalar scalar:objects.values())if(scalar.opaque!=null&&scalar.opaque.live()&&!scalar.provider.live())scalar.opaque.retired();}
    private Resolution result(int raw,int normalized,Scalar scalar)throws IOException{if(scalar==null)return new Resolution(Status.UNKNOWN,raw,normalized,null);boolean live=scalar.provider.live();if(live&&scalar.opaque!=null&&!scalar.opaque.live())throw new IOException("PC1 OS provider revived a retired token lifetime");return new Resolution(live?Status.LIVE:Status.CLOSED,raw,normalized,scalar);}
    public Resolution lookup(int raw,Domain domain)throws IOException{
        if(domain==null)throw new IOException("PC1 OS lookup domain required");if(raw==0)return new Resolution(Status.NULL,raw,raw,null);if(raw==-1)return new Resolution(Status.SENTINEL,raw,raw,null);Scalar scalar=objects.get(new Key(domain,raw));if(scalar!=null)return result(raw,raw,scalar);for(Scalar other:objects.values())if(other.rawWord==raw&&other.kind.domain!=domain)return new Resolution(Status.WRONG_DOMAIN,raw,raw,other);return new Resolution(Status.UNKNOWN,raw,raw,null);
    }
    /** Only FILE has an independently verified ignored-low2 tag contract. */
    public Resolution lookupFile(int raw)throws IOException{
        if(raw==0||raw==-1)return lookup(raw,Domain.KERNEL);int normalized=raw&~3;Scalar candidate=objects.get(new Key(Domain.KERNEL,normalized));if(candidate!=null&&candidate.kind==Kind.FILE)return result(raw,normalized,candidate);return lookup(raw,Domain.KERNEL);
    }
    public static final class SavedObject {
        public final int rawWord;public final Kind kind;public final Authority authority;public final Pc1GuestAddressSpace.ExternalOwner owner;public final boolean live;public final String contractId;private final byte[] proof;
        private SavedObject(int raw,Kind kind,Authority authority,Pc1GuestAddressSpace.ExternalOwner owner,boolean live,String contract,byte[] proof){rawWord=raw;this.kind=kind;this.authority=authority;this.owner=owner;this.live=live;contractId=contract;this.proof=proof;}
        public byte[] restorationProof(){return proof.clone();}
    }
    public static final class Image {private final List<SavedObject> objects;private Image(List<SavedObject> objects){this.objects=objects;}public List<SavedObject> objects(){return Collections.unmodifiableList(objects);}}
    public void save(DataOutput out)throws IOException{
        if(calls!=0||failedPublication)throw new IOException("PC1 OS registry snapshot during/incomplete native publication");refreshClosed();out.writeInt(FORMAT);out.writeInt(VERSION);out.writeInt(objects.size());for(Scalar scalar:objects.values()){String contract=contract(scalar.provider);if(!contract.equals(scalar.contract))throw new IOException("PC1 OS provider contract changed during lifetime");boolean live=scalar.provider.live();byte[] proof=scalar.provider.restorationProof();if(proof==null||proof.length>MAX_PROOF)throw new IOException("PC1 OS binding restoration proof required");out.writeInt(scalar.rawWord);out.writeByte(scalar.kind.ordinal());out.writeByte(scalar.authority.ordinal());out.writeUTF(scalar.owner.type);out.writeUTF(scalar.owner.id);out.writeBoolean(live);out.writeUTF(contract);out.writeInt(proof.length);out.write(proof);}
    }
    public static Image loadImage(DataInput in)throws IOException{
        if(in.readInt()!=FORMAT||in.readInt()!=VERSION)throw new IOException("PC1 scalar OS registry format");int count=in.readInt();if(count<0||count>MAX_OBJECTS)throw new IOException("PC1 scalar OS object count");List<SavedObject> objects=new ArrayList<>();Set<Key> words=new HashSet<>();Set<Pc1GuestAddressSpace.ExternalOwner> owners=new HashSet<>();long bytes=0;for(int n=0;n<count;n++){int raw=in.readInt(),kind=in.readUnsignedByte(),authority=in.readUnsignedByte();Pc1GuestAddressSpace.ExternalOwner owner=new Pc1GuestAddressSpace.ExternalOwner(in.readUTF(),in.readUTF());boolean live=in.readBoolean();String contract=in.readUTF();int length=in.readInt();bytes+=length;if(raw==0||raw==-1||kind>=Kind.values().length||authority>=Authority.values().length||contract.length()==0||contract.length()>1024||length<0||length>MAX_PROOF||bytes>64L*1024*1024||!owners.add(owner))throw new IOException("PC1 scalar OS object descriptor");Kind type=Kind.values()[kind];if(normalized(type,raw)==0||!words.add(key(type,raw)))throw new IOException("PC1 scalar OS word aliases duplicate registry objects");byte[] proof=new byte[length];in.readFully(proof);objects.add(new SavedObject(raw,type,Authority.values()[authority],owner,live,contract,proof));}return new Image(objects);
    }
    public interface Rebinder {Binding bind(SavedObject object)throws IOException;}
    /** Restore typed providers first; Guest32 external descriptors are staged
     *before Pool restores the sole complete layout. Raw engine cells are owned
     *elsewhere and are never rewritten by this association operation. */
    public static Pc1OsRegistry restore(Image image,Pc1GuestAddressSpace space,Pc1GuestAddressSpace.Image layout,Rebinder rebinder)throws IOException{
        if(space==null)throw new IOException("PC1 OS registry restore requires shared domain");Pc1OsRegistry result=new Pc1OsRegistry(space);result.restoreInto(image,layout,rebinder);return result;
    }
    /** Empty staged world only; retains the registry object already borrowed
     *by physical providers/OsWords, without changing engine raw cells. */
    public void restoreInto(Image image,Pc1GuestAddressSpace.Image layout,Rebinder rebinder)throws IOException{
        if(image==null||rebinder==null||!objects.isEmpty()||calls!=0||failedPublication)throw new IOException("PC1 OS in-place restore requires an empty staged registry/typed rebinder");calls++;try{for(SavedObject saved:image.objects){Binding provider=rebinder.bind(saved);if(!contract(provider).equals(saved.contractId)||provider.live()!=saved.live||!Arrays.equals(provider.restorationProof(),saved.proof)||provider.live()!=saved.live)throw new IOException("PC1 OS physical rebind contract/lifetime/proof mismatch");if(saved.live){long physical=provider.physicalHandle();if(physical==0||physical==-1L)throw new IOException("PC1 OS live rebound provider has no physical handle");if(findPhysical(saved.kind.domain,physical)!=null)throw new IOException("PC1 OS distinct saved lifetimes cannot collapse to one physical object");}Pc1HeapLease opaque=null;if(saved.authority==Authority.GUEST32_OPAQUE&&saved.live){if(layout==null)throw new IOException("PC1 OS Guest token restore needs the sole saved layout");opaque=opaque(saved.owner);int word=space.stageExternal(layout,saved.owner,opaque);if(word!=saved.rawWord)throw new IOException("PC1 OS token layout word mismatch");}Scalar scalar=new Scalar(saved.rawWord,saved.kind,saved.authority,saved.owner,provider,saved.contractId,opaque);provider.published(saved.rawWord);objects.put(key(saved.kind,saved.rawWord),scalar);}}catch(IOException|RuntimeException failure){failedPublication=true;throw failure;}finally{calls--;}
    }
}
