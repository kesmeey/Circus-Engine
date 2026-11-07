package org.circus.core;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.nio.ByteBuffer;

/** CurrentCHS Win32 API leaf. Font creation belongs to WinMain before the
 *window;404230 only creates itsDC/DIB afterwards. Raw requested/metrics/cache
 *are exclusively Pc1FontMemory, independent from selected physicalHFONT. */
public final class Pc1GdiGlyph implements AutoCloseable,Pc1GlyphCache.Provider {
    private static Path loadedLibrary;
    private final FontSet fonts;private final Pc1FontMemory memory;private final long nativeWindow;private final boolean ownsFonts;
    private long handle;private int lastRequired,nativeDepth;private final String contextKey;private Pc1HeapLease scratchLease;private boolean orphaned;
    private static synchronized void load(Path library)throws IOException{
        if(library==null)throw new IOException("PC1 GDI requires an explicit built JNI library");Path path=library.toAbsolutePath().normalize();
        if(loadedLibrary==null){if(!Files.isRegularFile(path))throw new IOException("Missing PC1 GDI library "+path);try{System.load(path.toString());}catch(UnsatisfiedLinkError e){throw new IOException("Cannot load PC1 GDI library",e);}loadedLibrary=path;}else if(!loadedLibrary.equals(path))throw new IOException("PC1 GDI library already loaded from a different path");
    }
    /** Typed64 HFONT owner, not a fabricatedDWORD region in nativeMemory. */
    public static final class FontSet implements AutoCloseable {
        private long handle;private Pc1OsWords words;private String scope;private int nextContext;
        private final Map<Pc1GuestAddressSpace.ExternalOwner,Pc1OsRegistry.Binding> contextObjects=new HashMap<>();
        private final Map<Integer,byte[]> descriptions=new HashMap<>();private final Map<Integer,Pc1OsRegistry.Binding> bindings=new HashMap<>();private final List<Pc1GdiGlyph> orphans=new ArrayList<>(),contexts=new ArrayList<>();
        public synchronized void bindOsWords(Pc1OsWords words,String scope)throws IOException{open();if(this.words!=null||words==null||scope==null||scope.length()==0||scope.length()>512||createdCount()!=0||contextCount()!=0)throw new IOException("PC1 FontSet OS words must bind before actual WinMain font creation");try{if(osWordsAbiNative()!=2)throw new IOException("PC1 GDI OS words ABI differs");}catch(UnsatisfiedLinkError missing){throw new IOException("PC1 bound GDI requires its OS-word JNI backend",missing);}this.words=words;this.scope=scope;}
        public Pc1OsWords osWords(){return words;}
        private Pc1GuestAddressSpace.ExternalOwner fontKey(int index)throws IOException{return new Pc1GuestAddressSpace.ExternalOwner("PC1_WINMAIN_FONT",scope+"/font-"+index);}
        public synchronized Pc1OsRegistry.Binding bindingForOwner(Pc1GuestAddressSpace.ExternalOwner owner)throws IOException{open();Pc1OsRegistry.Binding context=contextObjects.get(owner);if(context!=null)return context;for(int n=0;n<createdCount();n++)if(fontKey(n).equals(owner))return fontBinding(n);for(Pc1GdiGlyph orphan:contexts){Pc1OsRegistry.Binding binding=orphan.bindingForOwner(owner);if(binding!=null)return binding;}throw new IOException("PC1 GDI physical owner key not available: "+owner.type+"/"+owner.id);}
        private Pc1GuestAddressSpace.ExternalOwner ownerForPhysical(long physical)throws IOException{for(int n=0;n<createdCount();n++){Pc1OsRegistry.Binding binding=fontBinding(n);if(binding.live()&&binding.physicalHandle()==physical)return fontKey(n);}for(Map.Entry<Pc1GuestAddressSpace.ExternalOwner,Pc1OsRegistry.Binding> entry:contextObjects.entrySet())if(entry.getValue().live()&&entry.getValue().physicalHandle()==physical)return entry.getKey();return null;}
        private Pc1OsRegistry.Binding fontBinding(final int index)throws IOException{Pc1OsRegistry.Binding prior=bindings.get(index);if(prior!=null)return prior;final long epoch=handle;final byte[] description=descriptions.get(index);if(description==null)throw new IOException("PC1 actual font creation descriptor missing");Pc1OsRegistry.GdiObjectBinding result=new Pc1OsRegistry.GdiObjectBinding(){public boolean live()throws IOException{return handle==epoch&&handle!=0&&ownedFontNative(handle,index)!=0;}public long physicalHandle()throws IOException{if(!live())throw new IOException("PC1 owned HFONT has closed");return ownedFontNative(handle,index);}public String contractId(){return "PC1 CreateFontA owned physical object V1";}public byte[] restorationProof()throws IOException{ByteArrayOutputStream bytes=new ByteArrayOutputStream();DataOutputStream data=new DataOutputStream(bytes);data.write(description);data.writeBoolean(live());if(live())data.writeUTF(Binary.sha256(fontProgramNative(physicalHandle())));return bytes.toByteArray();}public boolean deleteObject()throws IOException{if(!live())return false;boolean ok=deleteFontNative(handle,physicalHandle());if(words!=null)words.registry().refreshClosed();return ok;}};bindings.put(index,result);return result;}

        private FontSet(Path library)throws IOException{load(library);handle=fontsOpenNative();if(handle==0)throw new IOException("PC1 font owner allocation failed");}
        public static FontSet empty(Path library)throws IOException{return new FontSet(library);}
        private void open()throws IOException{if(handle==0)throw new IOException("PC1 font set closed");}
        public synchronized long createFont(int height,int width,byte[] faceNul)throws IOException{open();if(faceNul==null||faceNul.length==0||faceNul[faceNul.length-1]!=0)throw new IOException("PC1 CreateFontA requires known raw ANSI NUL string");for(int n=0;n<faceNul.length-1;n++)if(faceNul[n]==0)throw new IOException("PC1 CreateFontA face backing contains an earlierNUL");int index=createdCount();long physical=createFontNative(handle,height,width,faceNul);if(physical!=0){ByteArrayOutputStream output=new ByteArrayOutputStream();DataOutputStream data=new DataOutputStream(output);data.writeInt(height);data.writeInt(width);data.writeInt(faceNul.length);data.write(faceNul);descriptions.put(index,output.toByteArray());}return physical;}
        private static int index(int address)throws IOException{switch(address){case 0x94c648:return 0;case 0x94c640:return 1;case 0x94c644:return 2;case 0x94c64c:return 3;case 0x94c650:return 4;default:throw new IOException("PC1 unknown nativeHFONT global0x"+Integer.toHexString(address));}}
        public synchronized void font(int nativeAddress,long font)throws IOException{open();index(nativeAddress);if(words==null){bindFontNative(handle,index(nativeAddress),font);return;}if(font==0||font==-1L){words.rawWord(nativeAddress,(int)font);return;}Pc1OsRegistry.Scalar known=words.registry().findPhysical(Pc1OsRegistry.Domain.GDI,font);if(known!=null){words.rawWord(nativeAddress,known.rawWord);return;}for(int n=0;n<createdCount();n++)if(ownedFontNative(handle,n)==font){words.publish(nativeAddress,Pc1OsRegistry.Kind.FONT,fontKey(n),fontBinding(n));return;}words.unknown(nativeAddress,"unregistered actual font/GDI object publication");throw new IOException("PC1 HFONT publication requires a known actual owner");}
        public synchronized long font(int nativeAddress)throws IOException{open();index(nativeAddress);if(words==null)return fontNative(handle,index(nativeAddress));return resolveGdi(words,nativeAddress);}
        public synchronized boolean deleteObject(long font)throws IOException{open();if(words==null)return deleteFontNative(handle,font);if(font==0||font==-1L)return deleteKnownNative(font);Pc1OsRegistry.Scalar object=words.registry().findPhysical(Pc1OsRegistry.Domain.GDI,font);if(object==null||!(object.binding() instanceof Pc1OsRegistry.GdiObjectBinding))throw new IOException("PC1 DeleteObject requires the registered GDI object's physical lifecycle provider");return((Pc1OsRegistry.GdiObjectBinding)object.binding()).deleteObject();}
        public synchronized int createdCount()throws IOException{open();return fontCountNative(handle);}
        public synchronized int contextCount()throws IOException{open();return contextCountNative(handle);}
        /** Actual404230, including only its single rawrequested=0 write. */
        public synchronized Pc1GdiGlyph initialize404230(long hwnd,Pc1FontMemory raw)throws IOException{open();return new Pc1GdiGlyph(this,hwnd,raw,false,true);}
        /** Portable restore allocation: noCreateFont, no reset of canonicalraw. */
        public synchronized Pc1GdiGlyph contextForRestore(long hwnd,Pc1FontMemory raw)throws IOException{open();if(words!=null)throw new IOException("PC1 bound GDI restore requires its explicit P1G4 physical capsule");return new Pc1GdiGlyph(this,hwnd,raw,false,false);}
        /** Physical staging only: never executes engine MOVs or changes raw globals. */
        public synchronized Pc1GdiGlyph contextForRestore(long hwnd,Pc1FontMemory raw,Pc1GdiGlyph.State saved)throws IOException{open();return new Pc1GdiGlyph(this,hwnd,raw,saved);}
        public static final class State {
            private final String scope;private final List<byte[]> descriptions;private final boolean[] live;
            private State(String scope,List<byte[]> descriptions,boolean[] live){this.scope=scope;this.descriptions=descriptions;this.live=live;}
            public String scope(){return scope;}
            public void save(DataOutput out)throws IOException{out.writeInt(0x50314631);out.writeUTF(scope);out.writeInt(live.length);for(int n=0;n<live.length;n++){out.writeBoolean(live[n]);out.writeInt(descriptions.get(n).length);out.write(descriptions.get(n));}}
            public static State load(DataInput in)throws IOException{if(in.readInt()!=0x50314631)throw new IOException("PC1 FontSet physical state format");String scope=in.readUTF();int count=in.readInt();if(scope.length()==0||scope.length()>512||count<0||count>5)throw new IOException("PC1 FontSet physical state descriptor");List<byte[]> descriptions=new ArrayList<>();boolean[] live=new boolean[count];for(int n=0;n<count;n++){live[n]=in.readBoolean();int length=in.readInt();if(length<13||length>65548)throw new IOException("PC1 FontSet creation descriptor");byte[] raw=new byte[length];in.readFully(raw);DataInputStream fields=new DataInputStream(new ByteArrayInputStream(raw));fields.readInt();fields.readInt();int face=fields.readInt();if(face<1||face!=length-12||raw[length-1]!=0)throw new IOException("PC1 FontSet ANSI face descriptor");for(int p=12;p<length-1;p++)if(raw[p]==0)throw new IOException("PC1 FontSet ANSI face contains early NUL");descriptions.add(raw);}return new State(scope,descriptions,live);}
        }
        public synchronized State snapshot()throws IOException{open();if(words==null)throw new IOException("PC1 FontSet capsule requires bound OS words");List<byte[]> copy=new ArrayList<>();boolean[] live=new boolean[createdCount()];for(int n=0;n<live.length;n++){copy.add(descriptions.get(n).clone());live[n]=fontBinding(n).live();}return new State(scope,copy,live);}
        /** Empty physical owner only. Font creation/deletion is staged without publication. */
        public synchronized void stage(State saved)throws IOException{open();if(saved==null||words==null||!scope.equals(saved.scope)||createdCount()!=0||contextCount()!=0)throw new IOException("PC1 FontSet staging needs matching empty bound owner");for(int n=0;n<saved.live.length;n++){DataInputStream fields=new DataInputStream(new ByteArrayInputStream(saved.descriptions.get(n)));int height=fields.readInt(),width=fields.readInt();byte[] face=new byte[fields.readInt()];fields.readFully(face);long font=createFont(height,width,face);if(font==0)throw new IOException("PC1 rebound CreateFontA failed");if(!saved.live[n]&&!deleteFontNative(handle,font))throw new IOException("PC1 rebound closed HFONT retirement failed");}}
        public synchronized void close()throws IOException{for(Pc1GdiGlyph orphan:new ArrayList<>(orphans))orphan.close();orphans.clear();if(handle!=0){fontsCloseNative(handle);handle=0;if(words!=null)words.registry().refreshClosed();}}
    }
    private static FontSet defaults(Path path)throws IOException{
        FontSet f=FontSet.empty(path);boolean ok=false;try{int[] sizes={22,28,12,32,40},addresses={0x94c648,0x94c640,0x94c644,0x94c64c,0x94c650};byte[] face={(byte)0xba,(byte)0xda,(byte)0xcc,(byte)0xe5,0};for(int n=0;n<5;n++)f.font(addresses[n],f.createFont(sizes[n]*2,sizes[n],face));ok=true;return f;}finally{if(!ok)f.close();}
    }
    /** Explicit offscreen audit convenience; production usesFontSet stages. */
    public Pc1GdiGlyph(Path library)throws IOException{this(library,0);}
    public Pc1GdiGlyph(Path library,long hwnd)throws IOException{this(defaults(library),hwnd,new Pc1FontMemory(),true,true);}
    private Pc1GdiGlyph(FontSet fonts,long hwnd,Pc1FontMemory memory,boolean owns,boolean initialize)throws IOException{
        if(memory==null)throw new IOException("PC1 GDI needs canonical font memory");this.fonts=fonts;this.memory=memory;nativeWindow=hwnd;ownsFonts=owns;contextKey=fonts.words==null?null:fonts.scope+"/context-"+(fonts.nextContext++);boolean ok=false;
        if(fonts.words!=null&&memory.nativeMemory()!=fonts.words.nativeMemory())throw new IOException("PC1 GDI raw font/OS words use different canonical buses");
        try{if(fonts.words==null)handle=contextOpenNative(fonts.handle,hwnd);else handle=contextOpenWordsNative(fonts.handle,hwnd,this);if(handle==0)throw new IOException("PC1 GDI context allocation failed");if(initialize)memory.requestedSize(0);ok=true;}finally{if(!ok&&fonts.words!=null&&handle!=0){orphaned=true;fonts.orphans.add(this);}if(!ok&&owns)fonts.close();}
    }
    public Pc1FontMemory fontMemory(){return memory;}
    /** Actual owned-DC selected FONT/BITMAP/BRUSH/PEN, diagnostic/export only. */
    public synchronized long[] selectedObjects()throws IOException{open();return selectedObjectsNative(handle);}
    public synchronized Pc1HeapLease borrowScratchLease()throws IOException{open();return borrowScratch();}
    public synchronized boolean isScratchLease(Pc1HeapLease lease)throws IOException{open();return lease!=null&&lease==scratchLease&&lease.live();}
    private void open()throws IOException{if(handle==0)throw new IOException("PC1 GDI owner closed");}
    public synchronized Pc1GlyphRaster.Outline outline(byte[] raw,int offset,int size)throws IOException{return outline(Pc1GlyphCache.known(raw,offset),size);}
    public synchronized Pc1GlyphRaster.Outline outline(Pc1GlyphCache.Source source,int requested)throws IOException{
        open();if(stagedState!=null)throw new IOException("PC1 GDI glyph before staged physical world activation");nativeDepth++;if(fonts.words!=null)fonts.words.registry().guestAddressSpace().beginNativeCall();try{return outlinePhysical(source,requested);}finally{if(fonts.words!=null)fonts.words.registry().guestAddressSpace().endNativeCall();nativeDepth--;}
    }
    private Pc1GlyphRaster.Outline outlinePhysical(Pc1GlyphCache.Source source,int requested)throws IOException{
        open();if(fonts.words==null)clearNative(handle,requested);else clearWords(requested);
        if(memory.requestedSize()!=requested){memory.textMetrics(fonts.words==null?selectMetricsNative(handle,requested):selectMetricsWordsNative(handle,requested,this,memory.textMetrics()));memory.requestedSize(requested);}
        // Native reads its source AFTER scratchclear/font selection/metric writes.
        int first=source.unsignedByte(0),character=first>=0x81&&first<=0xfe?(first<<8)|source.unsignedByte(1):first;
        byte[] packet=fonts.words==null?outlineNative(handle,character,requested,memory.ascent()):outlineWordsNative(handle,character,requested,memory.ascent(),this);
        if(packet==null||packet.length!=(fonts.words==null?19248:48)||le(packet,0)!=0x314f4750||le(packet,4)!=requested||le(packet,12)!=character||le(packet,44)<0||le(packet,44)>(fonts.words==null?19200:16*1024*1024))throw new IOException("PC1 actual GDI outline packet");lastRequired=le(packet,44);
        if(fonts.words==null)return new Pc1GlyphRaster.Outline(le(packet,16),le(packet,20),le(packet,24),le(packet,28),le(packet,32),le(packet,36),le(packet,40),Arrays.copyOfRange(packet,48,packet.length));
        final int stride=(int)((((long)le(packet,20)+31)/32)*4);return Pc1GlyphRaster.Outline.liveRows(le(packet,16),le(packet,20),le(packet,24),le(packet,28),le(packet,32),le(packet,36),le(packet,40),row->{final int pointer=fonts.words.rawWord(Pc1OsWords.GLYPH_BITS)+row*stride;return offset->fonts.words.nativeMemory().readAddress(pointer+offset);});
    }
    public synchronized String environment()throws IOException{open();return environmentNative(handle)+";requested="+memory.requestedSize()+";cachedAscent="+memory.ascent();}
    public synchronized byte[] scratch()throws IOException{open();return fonts.words==null?scratchNative(handle):activeScratch();}
    /** Diagnostic API return of the lastlive outline request. Projection uses
     *the known19200-byte scratch even when GDI returnedzero, e.g.ANSIspace. */
    public synchronized int lastRequiredByteCount()throws IOException{open();return lastRequired;}
    /** Physical capsule only. Raw engine words/font-cache bytes and the sole
     *Guest layout have their own owners; this contains no copies of them. */
    public static final class State {
        private static final int LEGACY=0x50314733,BOUND=0x50314734;
        public final int selectedSize,ansiCodePage;public final String faceName,fontIdentity;private final byte[] scratch;
        private final String contextKey,wordsHash;private final byte[] createdInfo,dcProperties;private final boolean dcLive,bitmapLive;private final int priorKind;private final ObjectRef[] selected;private final int[] bindingFields;
        private State(int selected,int acp,String face,String identity,byte[] scratch)throws IOException{this(selected,acp,face,identity,scratch,null,null,null,null,true,true,0,null,null);}
        private State(int size,int acp,String face,String identity,byte[] scratch,String key,String wordsHash,byte[] info,byte[] properties,boolean dcLive,boolean bitmapLive,int priorKind,ObjectRef[] selected,int[] bindings)throws IOException{
            boolean bound=key!=null;if((!bound&&size!=12&&size!=22&&size!=28&&size!=32&&size!=40)||acp<=0||face==null||face.length()>32||identity==null||!identity.matches("[0-9a-f]{64}")||scratch==null||(!bound&&scratch.length!=19200)||(bound&&(key.length()==0||key.length()>768||wordsHash==null||!wordsHash.matches("[0-9a-f]{64}")||info==null||info.length!=40||properties==null||properties.length>1024||selected==null||selected.length!=4||bindings==null||bindings.length>3||scratch.length>16*1024*1024||bitmapLive!=(scratch.length>0)||!dcLive&&properties.length!=0)))throw new IOException("PC1 GDI saved physical context");
            selectedSize=size;ansiCodePage=acp;faceName=face;fontIdentity=identity;this.scratch=scratch.clone();contextKey=key;this.wordsHash=wordsHash;createdInfo=info==null?null:info.clone();dcProperties=properties==null?null:properties.clone();this.dcLive=dcLive;this.bitmapLive=bitmapLive;this.priorKind=priorKind;this.selected=selected==null?null:selected.clone();bindingFields=bindings==null?null:bindings.clone();if(bound){if(priorKind!=0&&priorKind!=6&&priorKind!=7&&priorKind!=2&&priorKind!=1)throw new IOException("PC1 GDI previous object kind");Set<Integer> seen=new HashSet<>();for(int field:bindings)if((field!=Pc1OsWords.GLYPH_DC&&field!=Pc1OsWords.GLYPH_BITMAP&&field!=Pc1OsWords.GLYPH_PREVIOUS_FONT)||!seen.add(field))throw new IOException("PC1 GDI context binding field");for(ObjectRef ref:selected)if(ref==null)throw new IOException("PC1 GDI selected object reference");}
        }
        public boolean bound(){return contextKey!=null;}
        public byte[] scratchBytes(){return scratch.clone();}
        public String contextOwnerId(){return contextKey;}
        public void save(DataOutput out)throws IOException{out.writeInt(bound()?BOUND:LEGACY);out.writeInt(selectedSize);out.writeInt(ansiCodePage);out.writeUTF(faceName);out.writeUTF(fontIdentity);if(!bound()){out.write(scratch);return;}out.writeUTF(contextKey);out.writeUTF(wordsHash);out.write(createdInfo);out.writeBoolean(dcLive);out.writeBoolean(bitmapLive);out.writeInt(priorKind);out.writeInt(scratch.length);out.write(scratch);out.writeInt(dcProperties.length);out.write(dcProperties);for(ObjectRef ref:selected)ref.save(out);out.writeInt(bindingFields.length);for(int field:bindingFields)out.writeInt(field);}
        public static State load(DataInput in)throws IOException{int format=in.readInt();if(format!=LEGACY&&format!=BOUND)throw new IOException("PC1 GDI physical state format");int size=in.readInt(),acp=in.readInt();String face=in.readUTF(),identity=in.readUTF();if(format==LEGACY){byte[] scratch=new byte[19200];in.readFully(scratch);return new State(size,acp,face,identity,scratch);}String key=in.readUTF(),words=in.readUTF();byte[] info=new byte[40];in.readFully(info);boolean dc=in.readBoolean(),bitmap=in.readBoolean();int priorKind=in.readInt();byte[] scratch=readBlob(in,16*1024*1024),properties=readBlob(in,1024);ObjectRef[] refs=new ObjectRef[4];for(int n=0;n<4;n++)refs[n]=ObjectRef.load(in);int count=in.readInt();if(count<0||count>3)throw new IOException("PC1 GDI context binding count");int[] fields=new int[count];for(int n=0;n<count;n++)fields[n]=in.readInt();return new State(size,acp,face,identity,scratch,key,words,info,properties,dc,bitmap,priorKind,refs,fields);}
    }
    private static byte[] readBlob(DataInput in,int max)throws IOException{int count=in.readInt();if(count<0||count>max)throw new IOException("PC1 GDI capsule payload bound");byte[] result=new byte[count];in.readFully(result);return result;}
    /** Defaults are described, not associated with another process's numeric
     *handle. Non-default selected objects borrow the registry's typed owner. */
    private static final class ObjectRef {
        final int mode;final Pc1GuestAddressSpace.ExternalOwner owner;final byte[] proof;
        ObjectRef(int mode,Pc1GuestAddressSpace.ExternalOwner owner,byte[] proof)throws IOException{if(mode<0||mode>2||mode==1!=(owner!=null)||proof==null||proof.length>4096||mode==2&&proof.length!=0)throw new IOException("PC1 GDI selected reference");this.mode=mode;this.owner=owner;this.proof=proof.clone();}
        void save(DataOutput out)throws IOException{out.writeByte(mode);if(mode==1){out.writeUTF(owner.type);out.writeUTF(owner.id);}out.writeInt(proof.length);out.write(proof);}
        static ObjectRef load(DataInput in)throws IOException{int mode=in.readUnsignedByte();Pc1GuestAddressSpace.ExternalOwner owner=mode==1?new Pc1GuestAddressSpace.ExternalOwner(in.readUTF(),in.readUTF()):null;return new ObjectRef(mode,owner,readBlob(in,4096));}
    }
    private State stagedState;
    private Pc1GdiGlyph(FontSet fonts,long hwnd,Pc1FontMemory memory,State saved)throws IOException{
        if(saved==null||!saved.bound()||fonts.words==null||memory==null||memory.nativeMemory()!=fonts.words.nativeMemory()||!saved.contextKey.startsWith(fonts.scope+"/context-"))throw new IOException("PC1 GDI staging requires matching bound physical capsule");this.fonts=fonts;this.memory=memory;nativeWindow=hwnd;ownsFonts=false;contextKey=saved.contextKey;for(Pc1GdiGlyph context:fonts.contexts)if(contextKey.equals(context.contextKey))throw new IOException("PC1 GDI context owner already staged");String ordinal=contextKey.substring((fonts.scope+"/context-").length());int number;try{number=Integer.parseInt(ordinal);}catch(NumberFormatException error){throw new IOException("PC1 GDI context owner ordinal",error);}if(number<0||number==Integer.MAX_VALUE||!Integer.toString(number).equals(ordinal))throw new IOException("PC1 GDI context ordinal bound");fonts.nextContext=Math.max(fonts.nextContext,number+1);
        boolean accepted=false;try{handle=contextStageNative(fonts.handle,hwnd,saved.createdInfo,saved.dcLive,saved.bitmapLive,saved.selectedSize,saved.priorKind);if(handle==0)throw new IOException("PC1 GDI physical staging allocation failed");fonts.contexts.add(this);if(saved.bitmapLive){Pc1HeapLease scratch=borrowScratch();if(scratch.length()!=saved.scratch.length)throw new IOException("PC1 GDI restored DIB extent differs");for(int n=0;n<scratch.length();n++)scratch.writeU8(n,saved.scratch[n]&255);}for(int field:saved.bindingFields)makeContextBinding(field,contextFieldNative(handle,field));stagedState=saved;accepted=true;}finally{if(!accepted&&handle!=0){contextCloseNative(handle);handle=0;fonts.contexts.remove(this);}}
    }
    public interface ObjectRebinder {Pc1OsRegistry.Binding binding(Pc1GuestAddressSpace.ExternalOwner owner)throws IOException;}
    /** Called while the world is unpublished. Reconstructs only selections;
     *any differing non-selection DC attributes precisely require a DC leaf. */
    public synchronized void completePhysicalRestore(ObjectRebinder external)throws IOException{
        open();State saved=stagedState;if(saved==null)throw new IOException("PC1 GDI physical restore has not been staged");long[] defaults=defaultObjectsNative(handle),selected=new long[4];for(int n=0;n<4;n++){ObjectRef ref=saved.selected[n];if(ref.mode==2){selected[n]=0;continue;}if(ref.mode==0){selected[n]=defaults[n];if(selected[n]==0||!Arrays.equals(knownGdiDescriptorNative(selected[n]),ref.proof))throw new IOException("PC1 GDI stock/default object identity differs");}else{Pc1OsRegistry.Binding binding;try{binding=fonts.bindingForOwner(ref.owner);}catch(IOException missing){if(external==null)throw missing;binding=external.binding(ref.owner);}if(binding==null||!binding.live())throw new IOException("PC1 GDI selected object requires a live explicit rebound owner");selected[n]=binding.physicalHandle();}}
        if(saved.dcLive){if(!Arrays.equals(dcPropertiesNative(handle),saved.dcProperties))throw new IOException("PC1 GDI non-selection DC state needs explicit reconstruction");restoreObjectsNative(handle,selected);if(ansiCodePageNative(handle)!=saved.ansiCodePage||!faceNative(handle).equals(saved.faceName)||!identity(handle).equals(saved.fontIdentity))throw new IOException("PC1 GDI saved actual selected font environment differs");}else if(saved.ansiCodePage!=ansiCodePageNative(handle))throw new IOException("PC1 GDI saved ACP differs");
        // Validation remains separate from activation: raw OsWords may not yet
        //be installed, and registry/Guest staging must not write those fields.
    }
    public synchronized void stageScratch(Pc1GuestAddressSpace.Image soleLayout)throws IOException{open();if(stagedState==null)throw new IOException("PC1 GDI scratch staging requires P1G4");if(stagedState.bitmapLive)fonts.words.registry().guestAddressSpace().stageExternal(soleLayout,new Pc1GuestAddressSpace.ExternalOwner("PC1_GDI_SCRATCH_BYTES",contextKey),borrowScratch());}
    /** Call after OsWords/registry/sole-layout restoration, before publishing
     *the platform world. Does not execute a glyph or mutate any raw field. */
    public synchronized void activateRestored()throws IOException{open();if(stagedState==null)throw new IOException("PC1 GDI physical restore not staged");State saved=stagedState;if(!wordsIdentity().equals(saved.wordsHash))throw new IOException("PC1 GDI saved OS-word owner differs");if(saved.bitmapLive&&!fonts.words.registry().guestAddressSpace().containsLease(borrowScratch()))throw new IOException("PC1 GDI scratch not adopted by sole Guest layout");State actual=snapshotBound();if(!samePhysicalCapsule(saved,actual))throw new IOException("PC1 GDI staged physical capsule differs");stagedState=null;}
    private static boolean samePhysicalCapsule(State a,State b)throws IOException{ByteArrayOutputStream aa=new ByteArrayOutputStream(),bb=new ByteArrayOutputStream();a.save(new DataOutputStream(aa));b.save(new DataOutputStream(bb));return Arrays.equals(aa.toByteArray(),bb.toByteArray());}
    private String wordsIdentity()throws IOException{ByteArrayOutputStream out=new ByteArrayOutputStream();fonts.words.save(new DataOutputStream(out));return Binary.sha256(out.toByteArray());}
    private static String identity(long h)throws IOException{return Binary.sha256(fontDataNative(h));}
    public synchronized State snapshot()throws IOException{open();if(nativeDepth!=0||memory.busy())throw new IOException("PC1 GDI snapshot during native glyph/cache operation");if(stagedState!=null)throw new IOException("PC1 GDI snapshot before staged physical restore activation");return fonts.words==null?new State(selectedNative(handle),ansiCodePageNative(handle),faceNative(handle),identity(handle),scratchNative(handle)):snapshotBound();}
    private State snapshotBound()throws IOException{
        boolean dc=contextFieldLiveNative(handle,Pc1OsWords.GLYPH_DC),bitmap=contextFieldLiveNative(handle,Pc1OsWords.GLYPH_BITMAP);byte[] payload=new byte[bitmap?borrowScratch().length():0];for(int n=0;n<payload.length;n++)payload[n]=(byte)scratchLease.readU8(n);ObjectRef[] refs=selectionRefs();int[] fields=new int[contextBindings.size()];int p=0;for(int field:contextBindings.keySet())fields[p++]=field;Arrays.sort(fields);return new State(selectedNative(handle),ansiCodePageNative(handle),dc?faceNative(handle):"",dc?identity(handle):Binary.sha256(new byte[0]),payload,contextKey,wordsIdentity(),createdInfoNative(handle),dc?dcPropertiesNative(handle):new byte[0],dc,bitmap,knownGdiTypeNative(contextFieldNative(handle,Pc1OsWords.GLYPH_PREVIOUS_FONT)),refs,fields);
    }
    public synchronized void restore(State state)throws IOException{
        open();if(state==null)throw new IOException("PC1 GDI saved state missing");if(fonts.words!=null||state.bound())throw new IOException("PC1 bound GDI restore requires staged FontSet/context, registry and sole Guest layout");long replacement; synchronized(fonts){fonts.open();replacement=contextOpenNative(fonts.handle,nativeWindow);}boolean accepted=false;
        try{restoreSelectedNative(replacement,state.selectedSize);if(ansiCodePageNative(replacement)!=state.ansiCodePage||!faceNative(replacement).equals(state.faceName)||!identity(replacement).equals(state.fontIdentity))throw new IOException("PC1 GDI saved font environment differs from the actual selected font");restoreScratchNative(replacement,state.scratch);long previous=handle;handle=replacement;accepted=true;contextCloseNative(previous);}finally{if(!accepted&&replacement!=0)contextCloseNative(replacement);}
    }
    public synchronized byte[] saveFontContext()throws IOException{ByteArrayOutputStream out=new ByteArrayOutputStream();snapshot().save(new DataOutputStream(out));return out.toByteArray();}
    public synchronized void restoreFontContext(byte[] bytes)throws IOException{if(bytes==null)throw new IOException("PC1 GDI physical context missing");ByteArrayInputStream raw=new ByteArrayInputStream(bytes);State state=State.load(new DataInputStream(raw));if(raw.available()!=0)throw new IOException("PC1 GDI physical context trailingbytes");restore(state);}
    /** Actual4042A0: captured previous/DC words, SelectObject, bitmap reread,
     *DeleteObject, then DC reread/DeleteDC. No engine word is cleared. Bundle
     *close() is a separate physical cleanup operation. */
    public synchronized void destroy4042A0()throws IOException{
        open();if(fonts.words==null)throw new IOException("PC1 actual4042A0 requires bound OS words");if(stagedState!=null||nativeDepth!=0)throw new IOException("PC1 GDI destruction during staged/native operation");nativeDepth++;fonts.words.registry().guestAddressSpace().beginNativeCall();try{int previous=fonts.words.rawWord(Pc1OsWords.GLYPH_PREVIOUS_FONT),dc=fonts.words.rawWord(Pc1OsWords.GLYPH_DC);selectObjectsNative(resolveGdiWord(fonts.words,dc),resolveGdiWord(fonts.words,previous));deleteWord(fonts.words.rawWord(Pc1OsWords.GLYPH_BITMAP),false);deleteWord(fonts.words.rawWord(Pc1OsWords.GLYPH_DC),true);}finally{fonts.words.registry().guestAddressSpace().endNativeCall();nativeDepth--;}
    }
    private void deleteWord(int raw,boolean dc)throws IOException{Pc1OsRegistry.Resolution resolution=fonts.words.registry().lookup(raw,Pc1OsRegistry.Domain.GDI);if(resolution.status==Pc1OsRegistry.Status.NULL||resolution.status==Pc1OsRegistry.Status.SENTINEL){deleteServiceNative(raw==0?0:-1L,dc);return;}if(resolution.status!=Pc1OsRegistry.Status.LIVE)throw new IOException("PC1 actual GDI deletion needs known physical owner: "+resolution.status);Pc1OsRegistry.Binding provider=resolution.object.binding();if(dc){if(provider instanceof Pc1OsRegistry.GdiDcBinding)((Pc1OsRegistry.GdiDcBinding)provider).deleteDc();else{boolean deleted=deleteServiceNative(resolution.physicalHandle(),true);if(deleted)throw new IOException("PC1 actual DeleteDC TRUE requires the registered lifetime provider");}}else{if(!(provider instanceof Pc1OsRegistry.GdiObjectBinding))throw new IOException("PC1 DeleteObject requires its actual registered GDI service provider");((Pc1OsRegistry.GdiObjectBinding)provider).deleteObject();}}
    public synchronized void close()throws IOException{if(nativeDepth!=0||memory.busy())throw new IOException("PC1 GDI close during native glyph/cache operation");if(handle!=0){if(scratchLease!=null&&scratchLease.live())scratchLease.retireBorrowedView();contextCloseNative(handle);handle=0;if(fonts.words!=null)fonts.words.registry().refreshClosed();}fonts.contexts.remove(this);if(orphaned){fonts.orphans.remove(this);orphaned=false;}if(ownsFonts)fonts.close();}
    private static long resolveGdi(Pc1OsWords words,int address)throws IOException{return resolveGdiWord(words,words.rawWord(address));}
    private static long resolveGdiWord(Pc1OsWords words,int raw)throws IOException{Pc1OsRegistry.Resolution result=words.registry().lookup(raw,Pc1OsRegistry.Domain.GDI);if(result.status==Pc1OsRegistry.Status.LIVE)return result.physicalHandle();if(result.status==Pc1OsRegistry.Status.NULL)return 0;if(result.status==Pc1OsRegistry.Status.SENTINEL)return -1L;throw new IOException("PC1 actual GDI argument requires a known physical object: "+result.status+" raw0x"+Integer.toHexString(raw));}
    private byte[] bitmapInfoNative()throws IOException{byte[] raw=new byte[40];for(int n=0;n<raw.length;n++)raw[n]=(byte)fonts.words.nativeMemory().readAddress(0x42fcb0+n);int width=le(raw,4),height=le(raw,8),planes=(raw[12]&255)|(raw[13]&255)<<8,bits=(raw[14]&255)|(raw[15]&255)<<8;long stride=(((long)width*(bits/8)+3)&~3L),bytes=stride*Math.abs((long)height);if(le(raw,0)!=40||width<=0||height==0||planes!=1||(bits!=24&&bits!=32)||le(raw,16)!=0||le(raw,32)!=0)throw new IOException("PC1 glyph DIB bitmap-info requires an addressed palette/format provider");if(stride>Integer.MAX_VALUE||bytes<1||bytes>16*1024*1024)throw new IOException("PC1 glyph DIB physical provider bound16MiB");return raw;}
    private void contextAllocatedNative(long context)throws IOException{if(handle!=0)throw new IOException("PC1 native GDI context publication repeated");handle=context;fonts.contexts.add(this);}
    private long resolveGdiNative(int address)throws IOException{return resolveGdi(fonts.words,address);}
    private int readRawGdiWordNative(int address)throws IOException{return fonts.words.rawWord(address);}
    private long resolveRawGdiNative(int word)throws IOException{return resolveGdiWord(fonts.words,word);}
    private Pc1GuestAddressSpace.ExternalOwner contextOwner(int address)throws IOException{return new Pc1GuestAddressSpace.ExternalOwner("PC1_GDI_CONTEXT_OBJECT",contextKey+"/"+Integer.toHexString(address));}
    private final Map<Integer,Pc1OsRegistry.Binding> contextBindings=new HashMap<>();
    private Pc1OsRegistry.Binding bindingForOwner(Pc1GuestAddressSpace.ExternalOwner owner)throws IOException{for(Map.Entry<Integer,Pc1OsRegistry.Binding> entry:contextBindings.entrySet())if(contextOwner(entry.getKey()).equals(owner))return entry.getValue();return null;}
    private static Pc1OsRegistry.Kind kindForGdi(int type)throws IOException{switch(type){case 1:return Pc1OsRegistry.Kind.PEN;case 2:return Pc1OsRegistry.Kind.BRUSH;case 3:case 10:case 4:case 12:return Pc1OsRegistry.Kind.DC;case 6:return Pc1OsRegistry.Kind.FONT;case 7:return Pc1OsRegistry.Kind.BITMAP;default:throw new IOException("PC1 selected GDI object type needs a typed provider: "+type);}}
    private ObjectRef[] selectionRefs()throws IOException{
        ObjectRef[] refs=new ObjectRef[4];boolean dc=contextFieldLiveNative(handle,Pc1OsWords.GLYPH_DC);long[] current=selectedObjectsNative(handle),defaults=defaultObjectsNative(handle);
        for(int n=0;n<4;n++){if(!dc){refs[n]=new ObjectRef(2,null,new byte[0]);continue;}Pc1OsRegistry.Scalar scalar=fonts.words.registry().findPhysical(Pc1OsRegistry.Domain.GDI,current[n]);Pc1GuestAddressSpace.ExternalOwner owner=scalar==null?fonts.ownerForPhysical(current[n]):scalar.owner;if(owner!=null)refs[n]=new ObjectRef(1,owner,new byte[0]);else if(current[n]!=0&&current[n]==defaults[n])refs[n]=new ObjectRef(0,null,knownGdiDescriptorNative(current[n]));else throw new IOException("PC1 GDI selected object lacks a reconstructable typed owner");}return refs;
    }
    private Pc1OsRegistry.Binding makeContextBinding(final int field,final long born)throws IOException{
        Pc1OsRegistry.Binding existing=contextBindings.get(field);if(existing!=null)return existing;final long epoch=handle;final Pc1OsRegistry.Kind kind=field==Pc1OsWords.GLYPH_DC?Pc1OsRegistry.Kind.DC:field==Pc1OsWords.GLYPH_BITMAP?Pc1OsRegistry.Kind.BITMAP:kindForGdi(knownGdiTypeNative(born));
        Pc1OsRegistry.GdiDcBinding provider=new Pc1OsRegistry.GdiDcBinding(){
            public boolean live()throws IOException{return field==Pc1OsWords.GLYPH_PREVIOUS_FONT?fonts.handle!=0&&knownGdiTypeNative(born)!=0:handle==epoch&&handle!=0&&contextFieldLiveNative(handle,field);}
            public long physicalHandle()throws IOException{if(!live())throw new IOException("PC1 GDI context object closed");return field==Pc1OsWords.GLYPH_PREVIOUS_FONT?born:contextFieldNative(handle,field);}
            public String contractId(){return "PC1 404230 physical object "+Integer.toHexString(field)+" V2";}
            public byte[] restorationProof()throws IOException{
                if(field==Pc1OsWords.GLYPH_PREVIOUS_FONT)return knownGdiDescriptorNative(born);
                ByteArrayOutputStream bytes=new ByteArrayOutputStream();DataOutputStream out=new DataOutputStream(bytes);out.writeUTF(kind.name());out.writeInt(field);out.writeBoolean(live());
                if(live()&&field==Pc1OsWords.GLYPH_DC){byte[] properties=dcPropertiesNative(handle);out.writeInt(properties.length);out.write(properties);for(ObjectRef ref:selectionRefs())ref.save(out);}
                if(live()&&field==Pc1OsWords.GLYPH_BITMAP){out.write(createdInfoNative(handle));Pc1HeapLease lease=borrowScratch();byte[] raw=new byte[lease.length()];for(int n=0;n<raw.length;n++)raw[n]=(byte)lease.readU8(n);out.writeUTF(Binary.sha256(raw));}return bytes.toByteArray();
            }
            public boolean deleteObject()throws IOException{if(!live())return false;boolean ok=field==Pc1OsWords.GLYPH_PREVIOUS_FONT?deleteKnownNative(born):contextDeleteObjectNative(handle,field);if(field==Pc1OsWords.GLYPH_BITMAP&&ok&&scratchLease!=null&&scratchLease.live())scratchLease.retireBorrowedView();fonts.words.registry().refreshClosed();return ok;}
            public boolean deleteDc()throws IOException{if(field!=Pc1OsWords.GLYPH_DC){boolean ok=deleteServiceNative(physicalHandle(),true);if(ok)throw new IOException("PC1 actual DeleteDC TRUE requires the referenced object lifetime provider");return false;}boolean ok=contextDeleteDcNative(handle);fonts.words.registry().refreshClosed();return ok;}
        };contextBindings.put(field,provider);fonts.contextObjects.put(contextOwner(field),provider);return provider;
    }
    private void publishContextNative(int address,long physical)throws IOException{
        if(physical==0||physical==-1L){fonts.words.rawWord(address,(int)physical);return;}if(address==Pc1OsWords.GLYPH_BITS){fonts.words.publishMemory(address,borrowScratch(),new Pc1GuestAddressSpace.ExternalOwner("PC1_GDI_SCRATCH_BYTES",contextKey));return;}
        Pc1OsRegistry.Scalar known=fonts.words.registry().findPhysical(Pc1OsRegistry.Domain.GDI,physical);if(known!=null){fonts.words.rawWord(address,known.rawWord);return;}Pc1OsRegistry.Binding provider=makeContextBinding(address,physical);Pc1OsRegistry.Kind kind=address==Pc1OsWords.GLYPH_DC?Pc1OsRegistry.Kind.DC:address==Pc1OsWords.GLYPH_BITMAP?Pc1OsRegistry.Kind.BITMAP:kindForGdi(knownGdiTypeNative(physical));fonts.words.publish(address,kind,contextOwner(address),provider);if(provider.physicalHandle()!=physical)throw new IOException("PC1 GDI physical object changed during publication");
    }
    private Pc1HeapLease borrowScratch()throws IOException{if(scratchLease!=null)return scratchLease;final long epoch=handle;final ByteBuffer bytes=scratchBufferNative(handle);final long physical=contextFieldNative(handle,Pc1OsWords.GLYPH_BITS);if(bytes==null||!bytes.isDirect()||bytes.capacity()<1||bytes.capacity()>16*1024*1024||physical==0)throw new IOException("PC1 GDI scratch requires exact actual DIB storage");scratchLease=Pc1HeapLease.borrowedExternal64(physical,new Pc1HeapLease.Storage(){private void ready()throws IOException{if(handle!=epoch||handle==0||!contextFieldLiveNative(handle,Pc1OsWords.GLYPH_BITMAP))throw new IOException("PC1 GDI scratch physical DIB lifetime ended");}public int length(){return bytes.capacity();}public int read(int offset)throws IOException{ready();return bytes.get(offset)&255;}public void write(int offset,int value)throws IOException{ready();bytes.put(offset,(byte)value);}},"actual GDI glyph DIB owned by "+contextKey);return scratchLease;}
    private void clearWords(int requested)throws IOException{long count=((requested*requested)*8)&0xffffffffL;int pointer=fonts.words.rawWord(Pc1OsWords.GLYPH_BITS);for(long n=0;n<count;n++)fonts.words.nativeMemory().writeAddress(pointer+(int)n,0);}
    /** Required known physical span. Other native allocation families may be
     *added by the enclosing bundle; never reuse an old scratch pointer. */
    private long scratchAddressNative(int needed)throws IOException{int pointer=fonts.words.rawWord(Pc1OsWords.GLYPH_BITS);Pc1GuestAddressSpace space=fonts.words.registry().guestAddressSpace();for(Pc1GdiGlyph context:fonts.contexts){Pc1HeapLease lease=context.scratchLease;if(lease==null||!lease.live()||!space.containsLease(lease))continue;int base=space.addressOf(lease);long offset=(pointer&0xffffffffL)-(base&0xffffffffL);if(offset>=0&&offset+needed<=lease.length()){lease.readU8((int)Math.min(offset,lease.length()-1));return lease.observedAddress()+offset;}}throw new IOException("PC1 GDI raw scratch pointer needs a validated physical writable span at0x"+Integer.toHexString(pointer));}
    private byte[] activeScratch()throws IOException{int pointer=fonts.words.rawWord(Pc1OsWords.GLYPH_BITS);byte[] result=new byte[19200];for(int n=0;n<result.length;n++)result[n]=(byte)fonts.words.nativeMemory().readAddress(pointer+n);return result;}
    private static native long contextStageNative(long fonts,long hwnd,byte[] createdInfo,boolean dcLive,boolean bitmapLive,int selected,int priorKind)throws IOException;
    private static native void selectObjectsNative(long dc,long object)throws IOException;
    private static native boolean deleteServiceNative(long object,boolean dc)throws IOException;
    private static native byte[] createdInfoNative(long context)throws IOException;
    private static native byte[] dcPropertiesNative(long context)throws IOException;
    private static native long[] defaultObjectsNative(long context)throws IOException;
    private static native void restoreObjectsNative(long context,long[] selections)throws IOException;
    private static native long[] selectedObjectsNative(long context)throws IOException;
    private static native int osWordsAbiNative();
    private static native long ownedFontNative(long fonts,int index)throws IOException;
    private static native boolean deleteKnownNative(long physical)throws IOException;
    private static native int knownGdiTypeNative(long physical)throws IOException;
    private static native byte[] knownGdiDescriptorNative(long physical)throws IOException;
    private static native byte[] fontProgramNative(long physical)throws IOException;
    private static native long contextOpenWordsNative(long fonts,long hwnd,Pc1GdiGlyph owner)throws IOException;
    private static native byte[] selectMetricsWordsNative(long context,int size,Pc1GdiGlyph owner,byte[] priorMetrics)throws IOException;
    private static native byte[] outlineWordsNative(long context,int character,int size,int ascent,Pc1GdiGlyph owner)throws IOException;
    private static native long contextFieldNative(long context,int address)throws IOException;
    private static native boolean contextFieldLiveNative(long context,int address)throws IOException;
    private static native boolean contextDeleteDcNative(long context)throws IOException;
    private static native boolean contextDeleteObjectNative(long context,int address)throws IOException;
    private static native ByteBuffer scratchBufferNative(long context)throws IOException;
    private static int le(byte[] b,int at){return(b[at]&255)|(b[at+1]&255)<<8|(b[at+2]&255)<<16|b[at+3]<<24;}
    private static native long fontsOpenNative()throws IOException;
    private static native long createFontNative(long fonts,int height,int width,byte[] face)throws IOException;
    private static native void bindFontNative(long fonts,int index,long font)throws IOException;
    private static native long fontNative(long fonts,int index)throws IOException;
    private static native boolean deleteFontNative(long fonts,long font)throws IOException;
    private static native int fontCountNative(long fonts)throws IOException;
    private static native int contextCountNative(long fonts)throws IOException;
    private static native void fontsCloseNative(long fonts)throws IOException;
    private static native long contextOpenNative(long fonts,long hwnd)throws IOException;
    private static native void contextCloseNative(long context);
    private static native void clearNative(long context,int size)throws IOException;
    private static native byte[] selectMetricsNative(long context,int size)throws IOException;
    private static native byte[] outlineNative(long context,int character,int size,int ascent)throws IOException;
    private static native int selectedNative(long context)throws IOException;
    private static native void restoreSelectedNative(long context,int selected)throws IOException;
    private static native byte[] scratchNative(long context)throws IOException;
    private static native void restoreScratchNative(long context,byte[] bytes)throws IOException;
    private static native int ansiCodePageNative(long context)throws IOException;
    private static native String environmentNative(long context)throws IOException;
    private static native String faceNative(long context)throws IOException;
    private static native byte[] fontDataNative(long context)throws IOException;
}
