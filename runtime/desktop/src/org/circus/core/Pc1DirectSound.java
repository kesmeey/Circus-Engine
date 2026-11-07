package org.circus.core;

import java.io.*;
import java.nio.file.*;
import java.nio.charset.*;
import java.nio.*;
import java.security.*;
import java.util.*;

/** Actual Windows DirectSound/MCI services for the PC1 control owner. This
 *desktop owner never loads target executable code and owns ten native buffers. */
public final class Pc1DirectSound implements Pc1Audio.Backend,AutoCloseable {
    public interface Resources {
        Source open(byte[] rawResource)throws IOException;
        /**41BE20 live pointer. No byte[] adapter may move source reads. */
        default Source openRaw(Pc1Audio.NativeName raw)throws IOException{throw new IOException("PC1 live41BE20 raw resource resolver required");}
        default Source openSource(final Pc1ByteSource source)throws IOException{
            if(source instanceof Pc1Audio.NativeName)return openRaw((Pc1Audio.NativeName)source);
            if(source instanceof Pc1ByteSource.Addressed){final Pc1ByteSource.Addressed a=(Pc1ByteSource.Addressed)source;return openRaw(new Pc1Audio.NativeName(){public int address(){return a.address();}public int unsignedByte(int n)throws IOException{return a.unsignedByte(n);}public int nativeWord(int n)throws IOException{return a.nativeWord(n);}});}
            throw new IOException("PC1 complete41BE20 typed local resource resolver required");
        }
        /**Restore a recorded actual path, not a mutable current raw filename. */
        default Source reopenNative(byte[] path,long offset,String identity)throws IOException{throw new IOException("PC1 live-source snapshot requires exact native-path rebinder");}
        byte[] midiPath(byte[] rawMusicName)throws IOException;
    }
    /** A native loader opens the whole container, then seeks this absolute
     *offset. VOICE.DAT entries do not invent a per-entry read admission bound. */
    public static final class Source {
        public final Archive.Source bytes;
        public final long offset;
        public final Path nativeFile;
        public final String identity;
        private final byte[] nativePath;
        private final Source openFallback;private final OpenFallback lazyFallback;
        public interface OpenFallback {Source source()throws IOException;}
        public Source(Archive.Source bytes,long offset,Path nativeFile,String identity)throws IOException{if(offset>Integer.MAX_VALUE)throw new IOException("PC1 signed SetFilePointer initial offset boundary is not reconstructed above 2GiB");if(bytes==null||offset<0||identity==null)throw new IOException("PC1 raw audio source owner");this.bytes=bytes;this.offset=offset;this.nativeFile=nativeFile;this.identity=identity;nativePath=null;openFallback=null;lazyFallback=null;}
        public Source(Archive.Source bytes,long offset,byte[] rawNativePath,String identity)throws IOException{if(offset>Integer.MAX_VALUE)throw new IOException("PC1 signed SetFilePointer initial offset boundary is not reconstructed above 2GiB");if(bytes==null||offset<0||rawNativePath==null||identity==null)throw new IOException("PC1 raw audio source owner");for(byte b:rawNativePath)if(b==0)throw new IOException("PC1 native audio path prefix contains NUL");this.bytes=bytes;this.offset=offset;nativeFile=null;this.identity=identity;nativePath=rawNativePath.clone();openFallback=null;lazyFallback=null;}
        private Source(Source first,Source fallback,OpenFallback lazy){bytes=first.bytes;offset=first.offset;nativeFile=first.nativeFile;identity=first.identity;nativePath=first.nativePath==null?null:first.nativePath.clone();openFallback=fallback;lazyFallback=lazy;}
        /**41D0C0 has exactly one fallback, only for failed offset0 opens.
         *Descriptions must stay lazy: do not stat/probe to select the winner. */
        public Source withOpenFallback(Source fallback)throws IOException{if(offset!=0||fallback==null||fallback.offset!=0||openFallback!=null||lazyFallback!=null||fallback.openFallback!=null||fallback.lazyFallback!=null||nativeFile==null&&nativePath==null||fallback.nativeFile==null&&fallback.nativePath==null)throw new IOException("PC1 audio open fallback requires two native offset0 candidates with no prior chain");return new Source(this,fallback,null);}
        public Source withLazyOpenFallback(OpenFallback fallback)throws IOException{if(offset!=0||fallback==null||openFallback!=null||lazyFallback!=null||nativeFile==null&&nativePath==null)throw new IOException("PC1 lazy open fallback requires one native offset0 source");return new Source(this,null,fallback);}
        private Source fallback()throws IOException{Source result=lazyFallback==null?openFallback:lazyFallback.source();if(result!=null&&(result.offset!=0||result.openFallback!=null||result.lazyFallback!=null))throw new IOException("PC1 actual open fallback must be one offset0 source");return result;}
        public byte[] rawNativePath(){return nativePath==null?null:nativePath.clone();}
    }
    public static final class Result {
        public final String operation;
        public final int channel,result,a,b;
        Result(String operation,int channel,int result,int a,int b){this.operation=operation;this.channel=channel;this.result=result;this.a=a;this.b=b;}
        @Override public String toString(){return operation+"("+channel+")=0x"+Integer.toHexString(result)+"/"+a+"/"+b;}
    }
    public static final class Event {
        public final int kind,token;
        public final long a,b,tick;
        Event(long[] raw,int at){kind=(int)raw[at];token=(int)raw[at+1];a=raw[at+2];b=raw[at+3];tick=raw[at+4];}
    }
    private static Path loadedLibrary;
    private static final int[][] DECODER_RANGES={{0x455574,0x38008},{0xbc5f80,0x14030},{0x42fca8,4},{0x43de78,4},{0xbc1d78,4},{0xbc1d7c,4},{0xbc1d88,24},{0xbc1da4,0x4000},{0xbc5da4,4}};
    /**403B40 actual native decoder table leaf; not a device/bootstrap success. */
    /**Caller supplies the same world's once-owned PE memory. JNI keeps the
     *exact direct view strongly reachable; it creates no coefficient shadow. */
    public synchronized void bindEffectPeMemory(Pc1EffectPeMemory memory)throws IOException{requireOpen();if(memory==null||nativeMemory==null)throw new IOException("PC1 DSP PE binding requires canonical native world");bindDspPeNative(handle,memory.borrowedDirectView(0x42ab1c,0x42fca8-0x42ab1c));}
    public synchronized void selectDspCoefficientsForAudit(int extra)throws IOException{requireReady();dspCoefficientsNative(handle,extra);}
    public synchronized void transformDspBlockForAudit(int outputOffset,int extra)throws IOException{requireReady();dspTransformNative(handle,outputOffset,extra);}
    /**Explicit byte-array OS fixture for original403DF0/403E40/403E70 proof.
     *This does not install a production FILE/HANDLE or initialize a device. */
    public synchronized int[] lzForAudit(int operation,int packed,byte[] input,int position,int readLimit)throws IOException{requireReady();return dspLzAuditNative(handle,operation,packed,input,position,readLimit);}
    public synchronized long[] fileHandleTagsForAudit(Path callerOwnedFile)throws IOException{requireOpen();return fileTagsAuditNative(handle,ansi(callerOwnedFile.toAbsolutePath().normalize().toString()));}
    public synchronized void initializeDecoderTables()throws IOException{requireReady();initializeDecoderNative(handle);record("403B40",-1,0,0,0);}
    public synchronized ByteBuffer borrowDecoderRegion(int address)throws IOException{requireOpen();ByteBuffer raw=dspRegionNative(handle,address);if(raw==null)throw new IOException("PC1 decoder native direct storage absent");return raw.order(ByteOrder.LITTLE_ENDIAN);}

    public synchronized int physicalWordForAudit(int address,boolean write,int value)throws IOException{requireOpen();return physicalWordForAuditNative(handle,address,write,value);}
    public synchronized byte[] decoderBytesForAudit(int address)throws IOException{requireOpen();return decoderSeedNative(handle,address);}
    private static String loadedLibrarySha;
    private long handle;
    private final Thread ownerThread=Thread.currentThread();
    private Resources resources;
    public interface NativeMessages {void show(int rawTextAddress)throws IOException;}
    private NativeMessages messages;
    public synchronized void bindNativeMessages(NativeMessages required)throws IOException{requireOpen();if(required==null)throw new IOException("PC1 required407020 consumer missing");messages=required;}
    @Override public synchronized void nativeMessage(int rawTextAddress)throws IOException{requireReady();record("407020",-1,0,rawTextAddress,0);if(messages==null)throw new IOException("PC1 required407020 audio message at0x"+Integer.toHexString(rawTextAddress));messages.show(rawTextAddress);}
    private int[] initialization;
    private Pc1NativeMemory nativeMemory,standaloneHeaderTags;
    private Pc1OsRegistry fileRegistry;
    /**The same world's OS scalar domain; no private pointer allocator. */
    public synchronized void bindFileRegistry(Pc1OsRegistry registry)throws IOException{requireOpen();if(registry==null||nativeMemory==null||registry.guestAddressSpace()!=nativeMemory.guestAddressSpace())throw new IOException("PC1 FILE registry must borrow this exact native world");if(fileRegistry!=null&&fileRegistry!=registry)throw new IOException("PC1 FILE registry already bound");fileRegistry=registry;}
    public static Pc1GuestAddressSpace.ExternalOwner nativeFileOwner(long sourceId)throws IOException{if(sourceId<=0)throw new IOException("PC1 FILE source identity");return new Pc1GuestAddressSpace.ExternalOwner("PC1_AUDIO_FILE","source-"+sourceId);}
    /**Restore rebinder borrows the actual stopped/reopened Cdescriptor. This
     *never loads a header, seeks the live stream, or overwrites raw42AB1C. */
    public synchronized Pc1OsRegistry.Binding nativeFileBinding(final long sourceId)throws IOException{requireOpen();sourceInfoNative(handle,sourceId);return new Pc1OsRegistry.Binding(){
        public long physicalHandle()throws IOException{synchronized(Pc1DirectSound.this){if(handle==0)return -1;return sourceWordNative(handle,sourceId,false,0)[0];}}
        public boolean live()throws IOException{synchronized(Pc1DirectSound.this){return handle!=0&&sourceInfoNative(handle,sourceId)[6]!=0;}}
        public String contractId(){return "PC1_CREATEFILEA_READONLY_AUDIO_V1";}
        public byte[] restorationProof()throws IOException{synchronized(Pc1DirectSound.this){requireOpen();if(stagedControl!=null)return fileProof(sourceId);return snapshotBoundary(()->fileProof(sourceId));}}
        public void published(int rawWord)throws IOException{synchronized(Pc1DirectSound.this){requireOpen();sourceWordNative(handle,sourceId,true,rawWord);}}
    };}
    private byte[] fileProof(long sourceId)throws IOException{long[] info=sourceInfoNative(handle,sourceId);byte[] path=sourcePathNative(handle,sourceId);ByteArrayOutputStream bytes=new ByteArrayOutputStream();DataOutputStream out=new DataOutputStream(bytes);out.writeInt(0x50414631);out.writeLong(sourceId);out.writeLong(info[1]);out.writeBoolean(info[6]!=0);out.writeInt(path.length);out.write(path);/*Open future reads require whole actual-file identity; a closed descriptor
         *cannot read future bytes, so it is not reopened merely to hash it.*/if(info[6]!=0)out.writeUTF(actualSourceChecksum(sourceId,0,info[1]));out.flush();return bytes.toByteArray();}
    private Pc1Audio controlOwner;
    private boolean controlBound;
    private int snapshotLevel,rawDepth;
    private final boolean deferred;
    private final List<Result> journal=new ArrayList<>();
    private final boolean[] exists=new boolean[10];
    private final int[] baseVolume=new int[10],baseFrequency=new int[10],frequency=new int[10],pan=new int[10];
    private final ChannelData[] channels=new ChannelData[10];
    private final List<Input> inputs=new ArrayList<>();
    private ChannelData streamData;
    private byte[] stagedControl,stagedStreamWords;
    private final Map<String,byte[]> mciAliases=new LinkedHashMap<>();
    private boolean unknownMciOwner;
    private static final class ChannelData {
        Source source;byte[] raw,format,nativePath;long dataOffset,encodedLength,available,sourceSize;
        int decodedBytes,mode,extra;boolean streaming,rawPointer;long sourceId;String checksum,strategy;
    }
    private final class Input implements AutoCloseable {
        final Source source;final long id;Archive.Reader reader;long position;boolean closed;
        Input(Source source)throws IOException{
            this.source=source;position=source.offset;
            if(nativePath(source)!=null){long[] result=sourceOpenNative(handle,nativePath(source));record("CreateFileA",-1,(int)result[1],0,0);id=result[0];}
            else{id=0;reader=source.bytes.openReader();}
            inputs.add(this);
            if(id!=0&&fileRegistry!=null)fileRegistry.register(Pc1OsRegistry.Kind.FILE,nativeFileOwner(id),nativeFileBinding(id));
        }
        boolean opened(){return source.nativeFile==null&&source.nativePath==null||id!=0;}
        byte[] read(int requested)throws IOException{
            if(requested<0)throw new IOException("PC1 audio request exceeds representable Java backing");byte[] result;
            if(id!=0){result=sourceReadNative(handle,id,position,requested);long[] info=sourceInfoNative(handle,id);record("ReadFile",-1,(int)info[5],requested,result.length);}
            else{int count=(int)Math.min(requested,Math.max(0,reader.size()-position));result=reader.read(position,count);record("fixtureRead",-1,0,requested,result.length);}
            position+=result.length;return result;
        }
        @Override public void close()throws IOException{if(closed)return;if(id!=0){sourceCloseNative(handle,id);long[] info=sourceInfoNative(handle,id);record("CloseHandle",-1,(int)info[7],(int)info[8],0);}if(reader!=null)reader.close();closed=true;}
    }
    /** Explicit convenience audit path; production startup uses deferred(). */
    public Pc1DirectSound(Path library,long cooperativeWindow,Resources resources)throws IOException{this(library,cooperativeWindow,resources,false);}
    public static Pc1DirectSound deferred(Path library,Resources resources)throws IOException{return new Pc1DirectSound(library,0,resources,true);}
    private Pc1DirectSound(Path library,long cooperativeWindow,Resources resources,boolean deferred)throws IOException{
        if(library==null)throw new IOException("PC1 DirectSound requires its locally built JNI library");Path path=library.toAbsolutePath().normalize();
        synchronized(Pc1DirectSound.class){if(loadedLibrary==null){if(!Files.isRegularFile(path))throw new IOException("PC1 DirectSound library missing");loadedLibrarySha=Binary.sha256(Files.readAllBytes(path));try{System.load(path.toString());}catch(UnsatisfiedLinkError error){throw new IOException("PC1 DirectSound library cannot be loaded",error);}loadedLibrary=path;}else if(!loadedLibrary.equals(path))throw new IOException("PC1 DirectSound already loaded from another path");}
        this.resources=resources;this.deferred=deferred;handle=deferred?openDeferredNative():openNative(cooperativeWindow);if(handle==0)throw new IOException("PC1 DirectSound context not created");boolean accepted=false;
        try{refreshInitialization();if(!deferred&&initialization[0]==0&&initialization[4]==0)throw new IOException("PC1 native primary-buffer creation failed before its unchecked SetFormat dereference:0x"+Integer.toHexString(initialization[2]));accepted=true;}finally{if(!accepted){closeNative(handle);handle=0;}}
    }
    private void refreshInitialization()throws IOException{initialization=deviceNative(handle);if(initialization==null||initialization.length!=15)throw new IOException("PC1 DirectSound native identity packet");}
    public synchronized void borrowMainWindow(long hwnd)throws IOException{requireReady();if(!deferred)throw new IOException("PC1 legacy private audio endpoint is not a main-window provider");borrowWindowNative(handle,hwnd);}
    @Override public synchronized void bindControl(Pc1Audio control)throws IOException{
        requireOpen();if(control==null)throw new IOException("PC1 canonical audio control required");if(controlBound&&controlOwner!=control)throw new IOException("PC1 backend already has a different canonical control owner");if(controlBound&&controlOwner==control)return;controlOwner=control;
        if(stagedControl==null&&!control.nativePhysicalBound()){bindStreamBytesNative(handle,control.borrowStreamingBytes());bindDspExtraNative(handle,control.borrowStreamingExtra());controlBound=true;}
    }
    private final Pc1MmPhysicalMemory.Platform physicalPlatform=new Pc1MmPhysicalMemory.Platform(){
        public long address(ByteBuffer raw)throws IOException{return directAddressNative(raw);}
        public int readInt32(ByteBuffer raw,int at)throws IOException{return directWordNative(raw,at,false,0);}
        public void writeInt32(ByteBuffer raw,int at,int value)throws IOException{directWordNative(raw,at,true,value);}
    };
    public synchronized void bindNativeMemory(Pc1NativeMemory memory)throws IOException{
        requireOpen();if(memory==null||nativeMemory!=null||standaloneHeaderTags!=null||controlOwner==null||!controlOwner.nativePhysicalBound())throw new IOException("PC1 backend native views require unique canonical bus and addressed Audio owner");
        Pc1MmPhysicalMemory manager=memory.mmPhysicalMemory();manager.declareBackendIslands();
        final List<Pc1MmPhysicalMemory.Slice> slices=new ArrayList<>();
        for(int[] range:DECODER_RANGES){byte[] seed=decoderSeedNative(handle,range[0]);if(seed.length!=range[1])throw new IOException("PC1 decoder seed extent");slices.add(manager.claim(range[0],seed,true));}
        for(int address:new int[]{0xbc12c8,0xbc1d64,0x43dbe0}){int word=backendWordNative(handle,address,false,0);byte[] seed=new byte[4];for(int n=0;n<4;n++)seed[n]=(byte)(word>>>(8*n));slices.add(manager.claim(address,seed,true));}
        manager.prepareNative(physicalPlatform);int[][] ranges=manager.islands();ByteBuffer[] raw=new ByteBuffer[ranges.length],masks=new ByteBuffer[ranges.length];
        for(int n=0;n<ranges.length;n++){raw[n]=manager.slice(ranges[n][0],ranges[n][1]-ranges[n][0]).borrow();masks[n]=manager.accessMask(ranges[n][0]);}
        if(stagedControl!=null)stagedStreamWords=sharedBytesNative(handle);
        bindPhysicalNative(handle,raw,masks);
        for(final Pc1MmPhysicalMemory.Slice view:slices)memory.bind(view.address(),view.length(),view);
        nativeMemory=memory;if(stagedControl==null)controlBound=true;
    }
    /**Explicit component fixture only. It cannot be mixed with a canonical
     *world and is not saved by the physical backend's PDB/PDS packet. */
    public synchronized void bindStandaloneHeaderTagsForAudit(Pc1NativeMemory tags)throws IOException{requireOpen();if(nativeMemory!=null||tags==null||tags.staticImage()==null||!tags.staticImage().finalized()||standaloneHeaderTags!=null&&standaloneHeaderTags!=tags)throw new IOException("PC1 standalone header fixture requires its admitted StaticImage and no canonical world");standaloneHeaderTags=tags;}
    private int headerTag(int address)throws IOException{Pc1NativeMemory globals=nativeMemory!=null?nativeMemory:standaloneHeaderTags;if(globals==null)throw new IOException("PC1 header tag requires admitted canonical PE (or explicit standalone audit image)");return globals.readInt32(address);}
    /**Diagnostic counts only; retained COM references stay physically live.
     *Their portable reconstruction is a required snapshot boundary. */
    public synchronized int[] retiredReferences()throws IOException{requireOpen();return retiredNative(handle);}
    public synchronized void bindResources(Resources resources)throws IOException{requireReady();if(resources==null)throw new IOException("PC1 raw audio resources required");this.resources=resources;}
    public interface StartupLeaves {
        void initializeDecoderTables()throws IOException;
        void loadVoiceIndex()throws IOException;
        void failureMessage(int rawText)throws IOException;
    }
    private synchronized int initialize(int phase,int value,byte[] format)throws IOException{requireReady();int result=initializeNative(handle,phase,value,format);refreshInitialization();if(phase==4){Arrays.fill(exists,false);Arrays.fill(baseVolume,0);Arrays.fill(baseFrequency,0);Arrays.fill(frequency,0);Arrays.fill(pan,0);Arrays.fill(channels,null);}record("initialize"+phase,-1,result,value,format==null?0:format.length);return result;}
    public Pc1Audio.Initialization initialization(final StartupLeaves leaves)throws IOException{
        if(leaves==null)throw new IOException("PC1 audio startup decoder/VOICE/message consumers required");bindNativeMessages(new NativeMessages(){public void show(int rawTextAddress)throws IOException{leaves.failureMessage(rawTextAddress);}});return new Pc1Audio.Initialization(){
            public int directSoundCreate()throws IOException{return initialize(0,0,null);}
            public int setCooperativeLevel(int level)throws IOException{return initialize(1,level,null);}
            public int createPrimary(int bytes,int flags)throws IOException{if(bytes!=20)throw new IOException("PC1 native x86 primary descriptor extent");return initialize(2,flags,null);}
            public int setPrimaryFormat(byte[] format)throws IOException{
                if(format==null||format.length<18)throw new IOException("PC1 primary WAVEFORMATEX known extent");int extra=Binary.u16(format,16);byte[] known=format;
                if(extra>format.length-18){if(nativeMemory==null)throw new IOException("PC1 primary extension requires canonical neighbor owner");known=new byte[18+extra];for(int n=0;n<known.length;n++)known[n]=(byte)nativeMemory.readAddress(0x43dbb0+n);}return initialize(3,0,known);
            }
            public void clearChannelPointers()throws IOException{initialize(4,0,null);}
            public void initializeDecoderTables()throws IOException{leaves.initializeDecoderTables();}
            public void loadVoiceIndex()throws IOException{leaves.loadVoiceIndex();}
            public void failureMessage(int rawText)throws IOException{leaves.failureMessage(rawText);}
        };
    }
    public Pc1Audio.Shutdown shutdown(){return new Pc1Audio.Shutdown(){public void releasePrimaryIfPresent()throws IOException{initialize(5,0,null);}public void releaseDeviceIfPresent()throws IOException{initialize(6,0,null);}};}
    public synchronized int[] initialization(){return initialization.clone();}
    public synchronized String deviceIdentity()throws IOException{requireOpen();return Binary.sha256(identityNative(handle));}
    public static synchronized String loadedLibrarySha256(){return loadedLibrarySha;}
    public synchronized List<Result> journal(){return Collections.unmodifiableList(new ArrayList<>(journal));}
    private void requireOpen()throws IOException{if(handle==0)throw new IOException("PC1 DirectSound owner is closed");if(Thread.currentThread()!=ownerThread)throw new IOException("PC1 DirectSound service requires its creating owner thread");}
    private void requireReady()throws IOException{requireOpen();if(stagedControl!=null)throw new IOException("PC1 staged backend cannot issue output/control effects before adoption");}
    private byte[] nativePath(Source source)throws IOException{return source.nativePath!=null?source.nativePath.clone():source.nativeFile==null?null:ansi(source.nativeFile.toAbsolutePath().normalize().toString());}
    private static void channel(int channel)throws IOException{if(channel<0||channel>=10)throw new IOException("PC1 DirectSound channel outside ten native slots");}
    private static int wrappedSlot(int raw){int byteOffset=raw*4;return byteOffset>=0&&byteOffset<40?byteOffset/4:-1;}
    private int physicalSlot(int raw,String operation)throws IOException{
        int slot=wrappedSlot(raw);if(slot>=0)return slot;int address=0xbc12d0+raw*4;int pointer;
        if(nativeMemory!=null){pointer=0;for(int n=0;n<4;n++)pointer|=nativeMemory.readAddress(address+n)<<(n*8);}else if(controlOwner!=null)pointer=controlOwner.readNativeInt(address);else throw new IOException("PC1 "+operation+" requires canonical COM pointer cell0x"+Integer.toHexString(address));
        if(pointer==0)throw new IOException("PC1 native COM NULL dereference "+operation+" cell0x"+Integer.toHexString(address)+" requested="+raw);
        throw new IOException("PC1 "+operation+" requires actual COM pointer owner cell0x"+Integer.toHexString(address)+" raw0x"+Integer.toHexString(pointer));
    }
    private int record(String op,int channel,int result,int a,int b){journal.add(new Result(op,channel,result,a,b));return result;}
    public synchronized boolean pcmInitialized()throws IOException{requireOpen();return initialization[4]!=0;}
    /** Explicit native API entry, useful for self-authored output audits. Loader
     *control fields still belong to Pc1Audio and its Loaded projection. */
    public synchronized int createBuffer(int channel,int bytes,int flags,byte[] format)throws IOException{requireReady();int slot=wrappedSlot(channel);if(slot<0)throw new IOException("PC1 CreateSoundBuffer requires typed output pointer cell0x"+Integer.toHexString(0xbc12d0+channel*4)+" after decoder/byteLimit prefix");int hr=createNative(handle,slot,flags,bytes,format);record("CreateSoundBuffer",channel,hr,bytes,flags);if(hr==0)exists[slot]=true;return hr;}
    public synchronized int[] writeBuffer(int channel,int offset,byte[] data,int start,int count)throws IOException{requireReady();int slot=physicalSlot(channel,"Lock/Unlock");int[] result=writeNative(handle,slot,offset,data,start,count);record("Lock",channel,result[0],offset,count);record("Unlock",channel,result[1],result[2],result[3]);return result;}
    public synchronized byte[] bufferBytes(int channel)throws IOException{requireOpen();return bytesNative(handle,physicalSlot(channel,"buffer bytes"));}
    private int command(String op,int operation,int channel,int argument)throws IOException{requireReady();int slot=physicalSlot(channel,op);int hr=commandNative(handle,slot,operation,argument);return record(op,channel,hr,argument,0);}
    public synchronized int stop(int channel)throws IOException{return command("Stop",1,channel,0);}
    public synchronized int release(int channel)throws IOException{int slot=physicalSlot(channel,"Release");int result=command("Release",2,channel,0);exists[slot]=false;baseVolume[slot]=baseFrequency[slot]=frequency[slot]=pan[slot]=0;return result;}
    public synchronized int play(int channel,int flags)throws IOException{return command("Play",0,channel,flags);}
    public synchronized int setPosition(int channel,int position)throws IOException{return command("SetCurrentPosition",3,channel,position);}
    public synchronized int setVolume(int channel,int volume)throws IOException{return command("SetVolume",4,channel,volume);}
    public synchronized int setFrequency(int channel,int value)throws IOException{return command("SetFrequency",5,channel,value);}
    public synchronized int setPan(int channel,int value)throws IOException{return command("SetPan",6,channel,value);}
    public synchronized int[] query(int channel,int kind,int initial)throws IOException{requireOpen();int slot=physicalSlot(channel,"query"+kind);int[] result=queryNative(handle,slot,kind,initial);record("query"+kind,channel,result[0],result[1],result[2]);return result;}
    /**41C410 supplies its channel argument as the play-cursor output backing.
     *A failed COM call leaves that known initial value; the HRESULT is retained. */
    public synchronized int playCursor(int channel)throws IOException{return query(channel,0,channel)[1];}
    public synchronized int status(int channel)throws IOException{int[] result=query(channel,1,0xcccccccc);if(result[0]!=0)throw new IOException("PC1 GetStatus failed; original stack output owner is unknown:0x"+Integer.toHexString(result[0]));return result[1];}
    public synchronized byte[] midiPath(byte[] name)throws IOException{requireOpen();if(resources==null)throw new IOException("PC1 MIDI path resolver is not bound");return resources.midiPath(name.clone());}
    public synchronized int mci(byte[] command,boolean notify)throws IOException{requireReady();if(command==null)throw new IOException("PC1 raw MCI command missing");int end=0;while(end<command.length&&command[end]!=0)end++;byte[] prefix=Arrays.copyOf(command,end);int result=mciNative(handle,prefix,notify);if(result==0)trackMci(prefix);return record("mciSendStringA",-1,result,prefix.length,notify?1:0);}
    private void trackMci(byte[] command){
        String text=new String(command,java.nio.charset.StandardCharsets.ISO_8859_1);int quote=text.indexOf('"'),end=quote<0?-1:text.indexOf('"',quote+1);String verb=text.substring(0,text.indexOf(' ')<0?text.length():text.indexOf(' ')).toLowerCase(Locale.ROOT);
        if((verb.equals("open")||verb.equals("close"))&&quote>=0&&end>quote){byte[] alias=Arrays.copyOfRange(command,quote+1,end);String key=Base64.getEncoder().encodeToString(alias);if(verb.equals("open"))mciAliases.put(key,alias);else mciAliases.remove(key);}
        else if(verb.equals("close")&&text.equalsIgnoreCase("close all")){mciAliases.clear();unknownMciOwner=false;}
        else if(!verb.equals("play")&&!verb.equals("stop")&&!verb.equals("status"))unknownMciOwner=true;
    }
    public synchronized int setTimer(int id,int milliseconds)throws IOException{requireReady();if(id!=2)throw new IOException("PC1 timer2 endpoint required");int[] result=timerNative(handle,0,id,milliseconds);record("SetTimer",-1,result[0],milliseconds,result[1]);return result[0];}
    public synchronized int killTimer(int token)throws IOException{requireReady();int[] result=timerNative(handle,1,token,0);record("KillTimer",-1,result[0],token,result[1]);return result[0];}
    public synchronized List<Event> drainServiceEvents()throws IOException{requireReady();long[] raw=drainNative(handle);if(raw.length%5!=0)throw new IOException("PC1 audio native event packet");List<Event> events=new ArrayList<>();for(int at=0;at<raw.length;at+=5)events.add(new Event(raw,at));return events;}
    public static long nativeTick(){return tickNative();}
    public synchronized Pc1Audio.Loaded loadPcm(int channel,byte[] raw,boolean streaming)throws IOException{
        requireReady();int slot=wrappedSlot(channel);if(!pcmInitialized()||controlOwner==null&&slot>=0&&exists[slot])return null;if(resources==null)throw new IOException("PC1 native PCM resource resolver required");
        try{return loadSource(channel,raw,streaming,resources.open(raw.clone()));}catch(FileNotFoundException ordinaryMissing){record("resolveMissing",channel,2,0,0);return null;}
    }
    /**Full41BE20 raw entry. The argument pointer is captured once while its
     *bytes remain live through path selection, metadata and final strcpy. */
    @Override public synchronized Pc1Audio.Loaded loadPcmRaw(int requested,Pc1Audio.NativeName name,boolean streaming)throws IOException{return loadPcmSource(requested,name,streaming);}
    @Override public synchronized Pc1Audio.Loaded loadPcmSource(int requested,Pc1ByteSource name,boolean streaming)throws IOException{
        requireReady();rawDepth++;try{if(controlOwner==null||nativeMemory==null)throw new IOException("PC1 raw loader requires canonical Audio/native bus");
        if(controlOwner.readNativeInt(0xbc1d10)==0)return null;
        if(requested==-1){if(controlOwner.readNativeInt(0xbc12cc)==0){controlOwner.writeNativeInt(0xbc1d04,-1);int free=-1;for(int n=0;n<10;n++)if(controlOwner.readNativeInt(0xbbaafc+n*4)==0){free=n;controlOwner.writeNativeInt(0xbc1d04,n);break;}if(free==-1){nativeMessage(0x43dde8);return null;}}}
        else{if(controlOwner.readNativeInt(0xbbaafc+requested*4)!=0)return null;controlOwner.writeNativeInt(0xbc1d04,requested);}
        if(name==null)throw new IOException("PC1 raw native source owner missing after loader-channel prefix");if(name instanceof Pc1ByteSource.Addressed&&((Pc1ByteSource.Addressed)name).bus()!=nativeMemory)throw new IOException("PC1 loader source belongs to another canonical bus");final Pc1ByteSource fixed;if(name instanceof Pc1Audio.NativeName){final Pc1Audio.NativeName raw=(Pc1Audio.NativeName)name;final int address=raw.address();fixed=new Pc1Audio.NativeName(){public int address(){return address;}public int unsignedByte(int n)throws IOException{return raw.unsignedByte(n);}public int nativeWord(int n)throws IOException{return raw.nativeWord(n);}};}else fixed=name;
        if(resources==null)throw new IOException("PC1 native PCM raw resource resolver required");try{return loadSource(0,null,fixed,streaming,resources.openSource(fixed));}catch(FileNotFoundException ordinaryMissing){record("resolveMissing",requested,2,0,0);return null;}
        }finally{rawDepth--;}
    }
    //These logically adjacent words still have independent physical MM
    //owners. A split MOVSD cannot be replaced by4byte reads or a new lock.
    //The future continuous backing bridge must remove this precise gate.
    private static final int[][] SPLIT_MM_WORDS={{0x42a058,0x42fca8-0x42a058},{0xbbaaf8,4},{0xbbab24,4},{0xbbab50,4},{0xbbab7c,4},{0x43dbdc,4},{0xbc12c8,4},{0xbc1d64,4},{0x43dbe0,4}};
    private void requireRawWordOwner(int address)throws IOException{if(nativeMemory!=null&&nativeMemory.mmPhysicalMemory().hasWord(address))return;long at=address&0xffffffffL,end=at+4;for(int pass=0;pass<2;pass++)for(int[] range:pass==0?DECODER_RANGES:SPLIT_MM_WORDS){long low=range[0]&0xffffffffL,high=low+range[1];if(at<high&&low<end&&(at<low||end>high))throw new IOException("PC1 finalMOVSD spans independently backed MM owners at0x"+Integer.toHexString(address)+"; continuous native DWORD bridge required");}}
    private static int rawLength(Pc1ByteSource source)throws IOException{for(int n=0;n<1024*1024;n++)if(source.unsignedByte(n)==0)return n;throw new IOException("PC1 native PCM raw string exceeds known scan backing");}
    private Pc1Audio.Loaded publishRaw(ChannelData data,int channel,Pc1ByteSource name,boolean streaming)throws IOException{
        //41C13B samples the flag again after all metadata/loaded writes. EDX
        //and the destination index remain local even if the copy aliases it.
        boolean cached=controlOwner.readNativeInt(0xbc12cc)!=0;boolean success=channel!=-1;
        if(!cached){int length=rawLength(name);if(length>255){nativeMessage(0x43dd9c);success=false;}else{int count=rawLength(name)+1,destination=0xbc1304+(channel<<8),at=0;for(;at+4<=count;at+=4){if(name instanceof Pc1Audio.NativeName)requireRawWordOwner(((Pc1Audio.NativeName)name).address()+at);else if(name instanceof Pc1ByteSource.Addressed)requireRawWordOwner(((Pc1ByteSource.Addressed)name).address()+at);int word=name.nativeWord(at);controlOwner.writeNativeInt(destination+at,word);}for(;at<count;at++)nativeMemory.writeAddress(destination+at,name.unsignedByte(at));}}
        return new Pc1Audio.Loaded(data.decodedBytes,controlOwner.readNativeInt(0xbb8550+channel*4),controlOwner.readNativeInt(0xbbab28+channel*4),controlOwner.readNativeInt(0xbbab54+channel*4),controlOwner.readNativeInt(0xbbab80+channel*4),cached,controlOwner.readNativeInt(0xbbaafc+channel*4),success,streaming?channel:Integer.MIN_VALUE,null).alreadyPublished();
    }
    private Pc1Audio.Loaded loadSource(int channel,byte[] raw,boolean streaming,Source source)throws IOException{return loadSource(channel,raw,null,streaming,source);}
    private Pc1Audio.Loaded loadSource(int channel,byte[] raw,Pc1ByteSource live,boolean streaming,Source source)throws IOException{
        if(live==null&&raw==null)throw new IOException("PC1 raw PCM filename missing");if(raw!=null)for(byte b:raw)if(b==0)throw new IOException("PC1 raw PCM prefix contains NUL");
        Input input=new Input(source);if(!input.opened()){Source fallback=source.fallback();return fallback==null?null:loadSource(channel,raw,live,streaming,fallback);}ChannelData data=new ChannelData();data.source=source;data.raw=raw==null?new byte[0]:raw.clone();data.rawPointer=live!=null;data.nativePath=nativePath(source);data.sourceSize=input.id==0?source.bytes.size():sourceInfoNative(handle,input.id)[1];data.streaming=streaming;data.sourceId=input.id;
        try{
            byte[] header=input.read(12);if(header.length!=12)return rejectSource(input,streaming);byte[] format=null,pcm=null;long decoded=0,packedSize=0;int mode=0,extra=0;ByteArrayOutputStream encoded=new ByteArrayOutputStream();encoded.write(header);
            if(Binary.u32(header,0)==Integer.toUnsignedLong(headerTag(0x43dea4))){
                decoded=Binary.u32(header,4);mode=(byte)header[8];extra=header[9]&255;byte[] rawFormat=input.read(16);encoded.write(rawFormat);if(rawFormat.length!=16)return rejectSource(input,streaming);format=Arrays.copyOf(rawFormat,18);
                if(mode==1||mode==3){
                    byte[] packedBytes=input.read(4);encoded.write(packedBytes);if(packedBytes.length!=4)return rejectSource(input,streaming);long packed=Binary.u32(packedBytes,0);packedSize=packed;data.dataOffset=input.position;
                    if(streaming){if(fileRegistry==null)throw new IOException("PC1 compressed streaming requires same-world typed FILE registry");data.strategy="NATIVE_LZ_FFT_FILE_16K_MODE"+mode;}
                    else{if(packed>Integer.MAX_VALUE)throw new IOException("PC1 packed audio exceeds Java input backing");byte[] payload=input.read((int)packed);encoded.write(payload);if(payload.length!=packed)return rejectSource(input,streaming);pcm=Xpcm.decode(encoded.toByteArray()).payload;data.strategy="JAVA_XPCM_STATIC_"+mode;}
                }else if(mode==2){
                    data.dataOffset=input.position;
                    if(!streaming)throw new IOException("PC1 nonstream mode2 native41D2E3..41D30B reads decodedSize into a half-sized allocation; heap owner not reconstructed");
                    data.strategy="NATIVE_DELTA_FILE_8K";
                }else{
                    data.dataOffset=input.position;data.strategy="NATIVE_RAW_FILE_16K";
                    if(!streaming){if(decoded>Integer.MAX_VALUE)throw new IOException("PC1 decoded audio exceeds Java input backing");pcm=input.read((int)decoded);if(pcm.length!=decoded)return rejectSource(input,streaming);}
                }
            }else if(Binary.u32(header,0)==Integer.toUnsignedLong(headerTag(0x43de94))||Binary.u32(header,8)==Integer.toUnsignedLong(headerTag(0x43de8c))){
                int remaining=(int)Binary.u32(header,4)-4;boolean found=false;
                for(int records=0;records<100000;records++){
                    byte[] chunk=input.read(8);if(chunk.length!=8)return rejectSource(input,streaming);encoded.write(chunk);long tag=Binary.u32(chunk,0),size=Binary.u32(chunk,4);int next=remaining-8;
                    if(tag==Integer.toUnsignedLong(headerTag(0x43de84))){byte[] fmt=input.read(16);encoded.write(fmt);if(fmt.length!=16)return rejectSource(input,streaming);format=Arrays.copyOf(fmt,18);next=remaining-24;if((int)size>16){if(size-16>Integer.MAX_VALUE)throw new IOException("PC1 WAV fmt scratch write exceeds known portable backing");byte[] rest=input.read((int)size-16);encoded.write(rest);if(rest.length!=size-16)return rejectSource(input,streaming);}}
                    else if(tag==Integer.toUnsignedLong(headerTag(0x43de7c))){decoded=size;data.dataOffset=input.position;found=true;break;}
                    else{if(size>Integer.MAX_VALUE)throw new IOException("PC1 WAV scratch write exceeds known portable backing");byte[] skip=input.read((int)size);encoded.write(skip);}
                    if(next<1)return rejectSource(input,streaming);remaining=next;
                }
                if(!found)return rejectSource(input,streaming);if(format==null)throw new IOException("PC1 native WAV data has no known fmt allocation contents");data.strategy="NATIVE_RIFF_RAW_FILE_16K";
                if(!streaming){if(decoded>Integer.MAX_VALUE)throw new IOException("PC1 WAV decoded audio exceeds Java input backing");pcm=input.read((int)decoded);if(pcm.length!=decoded)return rejectSource(input,streaming);}
            }else return rejectSource(input,streaming);
            data.format=format;data.decodedBytes=(int)decoded;data.mode=mode;data.extra=extra;data.available=Math.max(0,data.sourceSize-data.dataOffset);
            data.encodedLength=(mode==1||mode==3)?streaming?data.dataOffset-source.offset+Math.min(packedSize,data.available):input.position-source.offset:data.dataOffset-source.offset+Math.min(mode==2?decoded/2:decoded,data.available);data.checksum=input.id==0?checksum(source.bytes,source.offset,data.encodedLength):actualSourceChecksum(input.id,source.offset,data.encodedLength);
            if(streaming){if(input.id==0)throw new IOException("PC1 Windows file streaming requires an owned native file source; no eager-memory fallback");prepareDecoderNative(handle,input.id,(int)decoded,mode==2?2:mode==1||mode==3?1:0,Binary.u16(format,2),(int)packedSize,(byte)extra);}
            else input.close();
            if(live!=null)channel=controlOwner.readNativeInt(0xbc1d04);int slot=wrappedSlot(channel);if(slot>=0)channels[slot]=data;
            //41BF88 keeps ECX locally across this store; it may itself alias
            //BC1D04, so do not reread the chosen index before this API call.
            if(controlOwner!=null)controlOwner.writeNativeInt(0xbc1d38+channel*4,(int)decoded);
            int hr=createBuffer(channel,(int)decoded,0x40ea,format);
            if(hr!=0){nativeMessage(0x43ddc4);return new Pc1Audio.Loaded((int)decoded,slot<0?0:baseVolume[slot],slot<0?0:baseFrequency[slot],slot<0?0:frequency[slot],slot<0?0:pan[slot],false,0,false,Integer.MIN_VALUE,null).alreadyPublished();}
            if(live!=null)channel=controlOwner.readNativeInt(0xbc1d04);
            if(streaming){if(controlOwner!=null)controlOwner.writeNativeInt(0xbbaaf8,(int)decoded);int[] result=bindStreamNative(handle,channel,input.id,(int)decoded,null,16384,true);streamData=data;record("41BBF0",channel,result[0],result[2],result[4]);if(result[3]!=0)throw new IOException("PC1 native stream reached an unmodelled read/Lock boundary:"+Arrays.toString(result));}
            else{int[] result=writeBuffer(channel,0,pcm,0,pcm.length);if(result[0]!=0)throw new IOException("PC1 native Lock failed before unchecked memcpy:0x"+Integer.toHexString(result[0]));}
            boolean cached=controlOwner!=null&&controlOwner.readNativeInt(0xbc12cc)!=0;
            if(live!=null){
                //Actual41C05D..41C102: output address and each later index are
                //sampled at their own instruction point, not one array DTO.
                channel=controlOwner.readNativeInt(0xbc1d04);
                if(cached){setVolume(channel,0);channel=controlOwner.readNativeInt(0xbc1d04);setVolume(channel,controlOwner.readNativeInt(0xbb8550+channel*4));channel=controlOwner.readNativeInt(0xbc1d04);setFrequency(channel,controlOwner.readNativeInt(0xbbab28+channel*4));channel=controlOwner.readNativeInt(0xbc1d04);setPan(channel,controlOwner.readNativeInt(0xbbab80+channel*4));channel=controlOwner.readNativeInt(0xbc1d04);}
                else{int output=0xbbab28+channel*4,before=controlOwner.readNativeInt(output);int[] queried=query(channel,3,before);controlOwner.writeNativeInt(output,queried[1]);channel=controlOwner.readNativeInt(0xbc1d04);int queriedValue=controlOwner.readNativeInt(0xbbab28+channel*4);controlOwner.writeNativeInt(0xbb8550+channel*4,0);controlOwner.writeNativeInt(0xbbab54+channel*4,queriedValue);controlOwner.writeNativeInt(0xbbab80+channel*4,0);}
                controlOwner.writeNativeInt(0xbbaafc+channel*4,1);return publishRaw(data,channel,live,streaming);
            }
            if(cached){baseVolume[slot]=controlOwner.readNativeInt(0xbb8550+channel*4);baseFrequency[slot]=controlOwner.readNativeInt(0xbbab28+channel*4);frequency[slot]=controlOwner.readNativeInt(0xbbab54+channel*4);pan[slot]=controlOwner.readNativeInt(0xbbab80+channel*4);setVolume(channel,0);setVolume(channel,baseVolume[slot]);setFrequency(channel,baseFrequency[slot]);setPan(channel,pan[slot]);}
            else{int before=controlOwner==null?baseFrequency[slot]:controlOwner.readNativeInt(0xbbab28+channel*4);int[] queried=query(channel,3,before);baseFrequency[slot]=queried[1];baseVolume[slot]=0;frequency[slot]=baseFrequency[slot];pan[slot]=0;}
            Pc1Audio.Loaded projected=new Pc1Audio.Loaded((int)decoded,baseVolume[slot],baseFrequency[slot],frequency[slot],pan[slot],cached,1,cached||raw.length<=255,streaming?channel:Integer.MIN_VALUE,null);
            if(controlOwner!=null){controlOwner.publishLoaded(channel,raw,streaming,projected);projected=projected.alreadyPublished();}
            if(!cached&&raw.length>255)nativeMessage(0x43dd9c);
            return projected;
        }finally{/*Required/unknown boundaries retain the already-open handle. Known static paths close explicitly above.*/}
    }
    private static Pc1Audio.Loaded rejectSource(Input input,boolean streaming)throws IOException{if(!streaming)input.close();return null;}
    private static String checksum(Archive.Source source,long offset,long count)throws IOException{
        try{MessageDigest hash=MessageDigest.getInstance("SHA-256");try(Archive.Reader reader=source.openReader()){for(long at=0;at<count;){int n=(int)Math.min(65536,count-at);byte[] bytes=reader.read(offset+at,n);if(bytes.length!=n)throw new EOFException("PC1 immutable audio backing identity short read");hash.update(bytes);at+=n;}}StringBuilder text=new StringBuilder();for(byte b:hash.digest())text.append(String.format(Locale.ROOT,"%02x",b&255));return text.toString();}catch(NoSuchAlgorithmException impossible){throw new AssertionError(impossible);}
    }
    private byte[] ansi(String text)throws IOException{
        Charset charset=Charset.forName(initialization[12]==936?"GBK":initialization[12]==65001?"UTF-8":"windows-"+initialization[12]);try{ByteBuffer result=charset.newEncoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).encode(CharBuffer.wrap(text));byte[] bytes=new byte[result.remaining()];result.get(bytes);return bytes;}catch(CharacterCodingException unrepresentable){throw new IOException("PC1 native ANSI audio path is not representable in ACP"+initialization[12],unrepresentable);}
    }
    public synchronized void closeStream()throws IOException{requireReady();int[] result=timerNative(handle,3,0,0);record("timeKillEvent",-1,result[0],0,0);}
    @Override public synchronized boolean prepareRestoredState(Pc1Audio control,Pc1Audio.State state)throws IOException{
        requireOpen();if(stagedControl==null||state==null||controlOwner!=control||!Arrays.equals(stagedControl,controlBytes(state)))return false;
        if(!Arrays.equals(streamControlBytes(state),stagedStreamWords!=null?stagedStreamWords:sharedBytesNative(handle)))throw new IOException("PC1 staged stream/control bytes differ");if(nativeMemory==null){bindStreamBytesNative(handle,control.borrowStreamingBytes());bindDspExtraNative(handle,control.borrowStreamingExtra());}controlBound=true;return true;
    }
    private static byte[] streamControlBytes(Pc1Audio.State state){byte[] raw=Arrays.copyOf(state.rawStreamingBytes(),20);int channel=state.words()[13];for(int n=0;n<4;n++)raw[16+n]=(byte)(channel>>>(8*n));return raw;}
    public synchronized boolean adoptRestoredState(Pc1Audio.State control)throws IOException{
        requireOpen();if(stagedControl==null||control==null||!controlBound||controlOwner==null||!Arrays.equals(stagedControl,controlBytes(control))||!Arrays.equals(stagedControl,controlBytes(controlOwner.state())))return false;
        int[] result;try{result=adoptNative(handle);}catch(IOException activation){try{close();}catch(IOException closing){activation.addSuppressed(closing);}throw activation;}for(int n=0;n<10;n++)record("restorePlay",n,result[n],0,0);record("restoreMMTimer",-1,result[10],0,0);record("restoreTimer2",-1,result[11],0,0);stagedControl=null;return true;
    }
    private static byte[] controlBytes(Pc1Audio.State control)throws IOException{ByteArrayOutputStream bytes=new ByteArrayOutputStream();control.save(new DataOutputStream(bytes));return bytes.toByteArray();}
    /** Detached codec. Native packet contains bytes, logical source IDs and
     *queried parameters; opaque OS handles and the hardware write cursor are
     *never adopted as portable handles or a settable sample clock. */
    public static final class Snapshot {
        private final String library,device;
        private final byte[] control,packet,identity;
        private final int[] init;private final boolean mainWindow;
        private final SavedChannel[] channels;
        private Snapshot(String library,byte[] identity,int[] init,boolean mainWindow,byte[] control,byte[] packet,SavedChannel[] channels)throws IOException{this.library=library;this.identity=identity.clone();this.device=Binary.sha256(identity);this.init=init.clone();this.mainWindow=mainWindow;this.control=control.clone();this.packet=packet.clone();this.channels=channels;validate();}
        public String deviceIdentity(){return device;}
        public String librarySha256(){return library;}
        public Pc1Audio.State control()throws IOException{DataInputStream in=new DataInputStream(new ByteArrayInputStream(control));Pc1Audio.State out=Pc1Audio.State.load(in);if(in.available()!=0)throw new IOException("PC1 backend control trailing bytes");return out;}
        public void save(DataOutput out)throws IOException{out.writeInt(0x50444236);out.writeUTF(library);out.writeBoolean(mainWindow);for(int v:init)out.writeInt(v);writeBytes(out,identity);writeBytes(out,control);writeBytes(out,packet);for(SavedChannel c:channels){out.writeBoolean(c!=null);if(c!=null)c.save(out);}}
        public static Snapshot load(DataInput in)throws IOException{if(in.readInt()!=0x50444236)throw new IOException("PC1 backend snapshot format");String library=in.readUTF();boolean main=in.readBoolean();int[] init=new int[15];for(int n=0;n<15;n++)init[n]=in.readInt();byte[] identity=readBytes(in,65552),control=readBytes(in,65536),packet=readBytes(in,0x20000000);if(identity==null||identity.length<16||control==null||packet==null)throw new IOException("PC1 backend snapshot required byte fields");SavedChannel[] channels=new SavedChannel[10];for(int n=0;n<10;n++)if(in.readBoolean())channels[n]=new SavedChannel(in);return new Snapshot(library,identity,init,main,control,packet,channels);}
        private Set<Long> validate()throws IOException{
            if(!library.matches("[0-9a-f]{64}")||!device.matches("[0-9a-f]{64}"))throw new IOException("PC1 backend snapshot binary/device identity");if(init.length!=15||identity.length<16||init[13]<0||init[13]>1||init[14]<0||init[14]>1||init[14]!=0&&init[13]==0)throw new IOException("PC1 backend physical initialization graph");Pc1Audio.State state=control();if(state.pcmReady&&init[4]==0)throw new IOException("PC1 saved PCM initialization mismatch");int[] words=state.words();Set<Long> open=new HashSet<>(),ids=new HashSet<>();
            try{ByteBuffer p=ByteBuffer.wrap(packet).order(ByteOrder.LITTLE_ENDIAN);if(p.getInt()!=0x36534450)throw new IOException("PC1 native backend packet format");int user=p.getInt(),interval=p.getInt();p.getInt();int mm=p.getInt();p.getInt();if(user<0||user>1||mm<0||mm>2)throw new IOException("PC1 backend timer presence flags");p.position(327872);skip(p,p.getInt());int count=p.getInt();if(count<0||count>100000)throw new IOException("PC1 native source count");
                for(int n=0;n<count;n++){long id=p.getLong(),size=p.getLong();p.getLong();skip(p,20);int opened=p.getInt(),hasWord=p.getInt(),rawWord=p.getInt(),length=p.getInt();if(id<=0||!ids.add(id)||size<0||opened<0||opened>1||hasWord<0||hasWord>1||hasWord!=0&&(rawWord==0||rawWord==-1)||length<0||length>32767)throw new IOException("PC1 source descriptor invariant");byte[] path=new byte[length];p.get(path);if(opened!=0){SavedChannel found=null;for(SavedChannel c:channels)if(c!=null&&c.sourceId==id)found=c;if(found==null||found.sourceSize!=size||!Arrays.equals(found.nativePath,path))throw new IOException("PC1 open/partial source has no verified immutable resource binding");open.add(id);}}
                for(int n=0;n<10;n++){int exists=p.getInt(),requested=p.getInt();skip(p,26);if(exists<0||exists>1)throw new IOException("PC1 buffer presence flag");int[] logical=state.channel(n);if(logical[0]!=0&&(exists==0||logical[5]!=requested))throw new IOException("PC1 buffer/control loaded or byteLimit binding:"+n);if(exists!=0){int bytes=p.getInt();skip(p,24);skip(p,bytes);}}
                int events=p.getInt();if(events<0||events>1000000)throw new IOException("PC1 pending service count");skip(p,Math.multiplyExact(events,40));if(p.hasRemaining())throw new IOException("PC1 native backend packet trailing bytes");return open;
            }catch(BufferUnderflowException|IllegalArgumentException|ArithmeticException e){throw new IOException("PC1 native backend packet boundary",e);}
        }
    }
    private static void skip(ByteBuffer b,int n)throws IOException{if(n<0||n>b.remaining())throw new IOException("PC1 backend packet range");b.position(b.position()+n);}
    private static void writeBytes(DataOutput out,byte[] value)throws IOException{out.writeInt(value==null?-1:value.length);if(value!=null)out.write(value);}
    private static byte[] readBytes(DataInput in,int max)throws IOException{int n=in.readInt();if(n< -1||n>max)throw new IOException("PC1 backend byte field bound");if(n==-1)return null;byte[] bytes=new byte[n];in.readFully(bytes);return bytes;}
    private static final class SavedChannel {
        final byte[] raw,format,nativePath;final long sourceOffset,sourceSize,dataOffset,encodedLength,available,sourceId;final int decodedBytes,mode,extra;final boolean streaming,rawPointer;final String identity,checksum,strategy;
        SavedChannel(ChannelData d)throws IOException{raw=d.raw.clone();format=d.format.clone();nativePath=d.nativePath==null?null:d.nativePath.clone();sourceOffset=d.source.offset;sourceSize=d.sourceSize;dataOffset=d.dataOffset;encodedLength=d.encodedLength;available=d.available;sourceId=d.sourceId;decodedBytes=d.decodedBytes;mode=d.mode;extra=d.extra;streaming=d.streaming;rawPointer=d.rawPointer;identity=d.source.identity;checksum=d.checksum;strategy=d.strategy;}
        SavedChannel(DataInput in)throws IOException{raw=readBytes(in,1048576);format=readBytes(in,18);nativePath=readBytes(in,32767);sourceOffset=in.readLong();sourceSize=in.readLong();dataOffset=in.readLong();encodedLength=in.readLong();available=in.readLong();sourceId=in.readLong();decodedBytes=in.readInt();mode=in.readInt();extra=in.readInt();streaming=in.readBoolean();rawPointer=in.readBoolean();identity=in.readUTF();checksum=in.readUTF();strategy=in.readUTF();if(raw==null||format==null||format.length!=18||sourceOffset<0||sourceSize<sourceOffset||dataOffset<sourceOffset||dataOffset>sourceSize||available!=sourceSize-dataOffset||sourceId<0||mode< -128||mode>127||extra<0||extra>255||encodedLength<0||encodedLength>sourceSize-sourceOffset||!checksum.matches("[0-9a-f]{64}"))throw new IOException("PC1 saved audio source descriptor");for(byte b:raw)if(b==0)throw new IOException("PC1 saved audio raw filename NUL");if(nativePath!=null)for(byte b:nativePath)if(b==0)throw new IOException("PC1 saved audio raw path NUL");}
        void save(DataOutput out)throws IOException{writeBytes(out,raw);writeBytes(out,format);writeBytes(out,nativePath);out.writeLong(sourceOffset);out.writeLong(sourceSize);out.writeLong(dataOffset);out.writeLong(encodedLength);out.writeLong(available);out.writeLong(sourceId);out.writeInt(decodedBytes);out.writeInt(mode);out.writeInt(extra);out.writeBoolean(streaming);out.writeBoolean(rawPointer);out.writeUTF(identity);out.writeUTF(checksum);out.writeUTF(strategy);}
        ChannelData bind(Pc1DirectSound backend,boolean open)throws IOException{
            Source source;if(open){if(backend.resources==null)throw new IOException("PC1 staging requires its raw resource resolver");source=rawPointer?backend.resources.reopenNative(nativePath.clone(),sourceOffset,identity):backend.resources.open(raw.clone());while(source!=null&&!Arrays.equals(backend.nativePath(source),nativePath))source=source.fallback();if(source==null)throw new IOException("PC1 staged audio source changed identity/path/candidates");if(source.offset!=sourceOffset||!source.identity.equals(identity)||!Arrays.equals(backend.nativePath(source),nativePath)||!rawPointer&&(source.bytes.size()!=sourceSize||!checksum(source.bytes,sourceOffset,encodedLength).equals(checksum)))throw new IOException("PC1 staged audio source changed identity/path/offset/bytes");/*Raw native paths are validated by actual CreateFileA/GetFileSize/peek-hash after restoreNative; no ACP→JavaPath substitution.*/}
            else{Archive.Source metadata=new Archive.Source(){public long size(){return sourceSize;}public byte[] read(long at,int size)throws IOException{throw new IOException("PC1 closed source has metadata only; restored PCM is independently owned");}};source=nativePath==null?new Source(metadata,sourceOffset,(Path)null,identity):new Source(metadata,sourceOffset,nativePath,identity);}
            ChannelData d=new ChannelData();d.source=source;d.sourceSize=sourceSize;d.raw=raw.clone();d.format=format.clone();d.nativePath=nativePath==null?null:nativePath.clone();d.dataOffset=dataOffset;d.encodedLength=encodedLength;d.available=available;d.sourceId=sourceId;d.decodedBytes=decodedBytes;d.mode=mode;d.extra=extra;d.streaming=streaming;d.rawPointer=rawPointer;d.checksum=checksum;d.strategy=strategy;return d;
        }
    }
    /**Read-only serialization boundary for the complete VM/platform bundle.
     *The worker cannot change shared native stream bytes until this returns.
     *Work must only serialize/query owners, never pump input or mutate OS/VM.
     *Nested capture(audio) uses the same lock; no second native acquisition. */
    public interface SnapshotWork<T> {T capture()throws IOException;}
    public synchronized <T>T snapshotBoundary(SnapshotWork<T> work)throws IOException{
        requireReady();if(rawDepth!=0)throw new IOException("PC1 snapshot during live41BE20 raw source/load frame");if(work==null)throw new IOException("PC1 snapshot serializer required");boolean outer=snapshotLevel==0;if(outer){pumpLegacyNative(handle);snapshotLockNative(handle,true);}snapshotLevel++;
        try{return work.capture();}finally{snapshotLevel--;if(outer)snapshotLockNative(handle,false);}
    }
    public synchronized Snapshot capture(final Pc1Audio control)throws IOException{
        requireReady();if(control==null||controlOwner!=control)throw new IOException("PC1 backend capture requires its canonical control owner");return snapshotBoundary(new SnapshotWork<Snapshot>(){public Snapshot capture()throws IOException{return captureLocked(control.state());}});
    }
    /** Convenience detached capture rejects a record that raced the live MM
     *stream. Production uses capture(Pc1Audio) for one lock across both owners. */
    public synchronized Snapshot capture(final Pc1Audio.State control)throws IOException{
        return snapshotBoundary(new SnapshotWork<Snapshot>(){public Snapshot capture()throws IOException{return captureLocked(control);}});
    }
    private Snapshot captureLocked(Pc1Audio.State control)throws IOException{
        if(control==null||control.pcmReady&&initialization[4]==0)throw new IOException("PC1 backend/control device initialization mismatch");if(unknownMciOwner||!mciAliases.isEmpty())throw new IOException("PC1 active MCI device phase has no implemented portable reconstruction owner");
        if(!Arrays.equals(streamControlBytes(control),sharedBytesNative(handle)))throw new IOException("PC1 detached control stream changed before atomic capture; use capture(Pc1Audio)");
        SavedChannel[] saved=new SavedChannel[10];for(int n=0;n<10;n++)if(channels[n]!=null)saved[n]=new SavedChannel(channels[n]);byte[] identity=identityNative(handle);refreshInitialization();return new Snapshot(loadedLibrarySha,identity,initialization,deferred,controlBytes(control),captureNative(handle),saved);
    }
    /** All validation and stopped reconstruction precede publication. This
     *method never plays a buffer, arms a timer, or mutates the VM/control owner.
     *Close the previous backend at the caller's commit point, then restore the
     *Pc1Audio control with this staged backend to activate the saved services. */
    public static Pc1DirectSound stage(Path dll,long hwnd,Resources resources,Snapshot snapshot)throws IOException{
        if(snapshot==null)throw new IOException("PC1 backend snapshot missing");Set<Long> open=snapshot.validate();Pc1DirectSound backend=snapshot.mainWindow?Pc1DirectSound.deferred(dll,resources):new Pc1DirectSound(dll,hwnd,resources);boolean accepted=false;
        try{
            if(snapshot.mainWindow){backend.borrowMainWindow(hwnd);if(snapshot.init[13]!=0){if(backend.initialize(0,0,null)!=0)throw new IOException("PC1 staged DirectSound device unavailable");if(snapshot.init[1]!=Integer.MAX_VALUE&&backend.initialize(1,2,null)!=snapshot.init[1])throw new IOException("PC1 staged cooperative result differs");if(snapshot.init[14]!=0&&backend.initialize(2,0x4009,null)!=0)throw new IOException("PC1 staged primary unavailable");}}
            if(snapshot.init[14]!=0){if(snapshot.identity.length<34)throw new IOException("PC1 saved primary format owner missing");backend.initialize(3,0,Arrays.copyOfRange(snapshot.identity,16,snapshot.identity.length));}
            if(!snapshot.library.equals(loadedLibrarySha)||!snapshot.device.equals(backend.deviceIdentity()))throw new IOException("PC1 staged backend binary/device identity differs");restoreInitializationNative(backend.handle,snapshot.init);backend.refreshInitialization();
            for(int n=0;n<10;n++)if(snapshot.channels[n]!=null)backend.channels[n]=snapshot.channels[n].bind(backend,open.contains(snapshot.channels[n].sourceId));restoreNative(backend.handle,snapshot.packet);for(SavedChannel c:snapshot.channels)if(c!=null&&open.contains(c.sourceId)&&!backend.actualSourceChecksum(c.sourceId,c.sourceOffset,c.encodedLength).equals(c.checksum))throw new IOException("PC1 staged actual A-handle source bytes differ");Pc1Audio.State control=snapshot.control();for(int n=0;n<10;n++){backend.exists[n]=backend.query(n,5,0)[1]!=0;int[] fields=control.channel(n);backend.baseVolume[n]=fields[1];backend.baseFrequency[n]=fields[2];backend.frequency[n]=fields[3];backend.pan[n]=fields[4];}backend.stagedControl=snapshot.control.clone();accepted=true;return backend;
        }finally{if(!accepted)backend.close();}
    }
    private String actualSourceChecksum(long id,long offset,long count)throws IOException{
        try{MessageDigest hash=MessageDigest.getInstance("SHA-256");for(long at=0;at<count;){int n=(int)Math.min(65536,count-at);hash.update(sourcePeekNative(handle,id,offset+at,n));at+=n;}StringBuilder text=new StringBuilder();for(byte b:hash.digest())text.append(String.format(Locale.ROOT,"%02x",b&255));return text.toString();}catch(NoSuchAlgorithmException impossible){throw new AssertionError(impossible);}
    }
    public synchronized long[] streamState()throws IOException{requireOpen();return streamInfoNative(handle);}
    public synchronized Map<String,Object> describeChannel(int channel)throws IOException{
        requireOpen();channel(channel);ChannelData data=channels[channel];Map<String,Object> result=new LinkedHashMap<>();result.put("bufferExists",exists[channel]);if(data!=null){result.put("source",data.source.identity);result.put("containerOffset",data.source.offset);result.put("dataOffset",data.dataOffset);result.put("decodedBytes",Integer.toUnsignedLong(data.decodedBytes));result.put("encodedBytes",data.encodedLength);result.put("availableDataBytes",data.available);result.put("sourceSliceSha256",data.checksum);result.put("mode",data.mode);result.put("extra",data.extra);result.put("strategy",data.strategy);result.put("streaming",data.streaming);result.put("sourceId",data.sourceId);}return Collections.unmodifiableMap(result);
    }
    /** Audit endpoint of the same native private fill kernel. Production uses
     *the actual MM callback and never overrides a hardware cursor. */
    public synchronized int[] serviceStreamForAudit(boolean periodic,int cursor)throws IOException{requireReady();return serviceStreamNative(handle,periodic,true,cursor);}
    @Override public synchronized void close()throws IOException{if(handle!=0){requireOpen();IOException failure=null;for(byte[] alias:mciAliases.values()){ByteArrayOutputStream command=new ByteArrayOutputStream();command.write("close \"".getBytes(java.nio.charset.StandardCharsets.US_ASCII));command.write(alias);command.write('"');int error=mciNative(handle,command.toByteArray(),false);record("mciCloseOwned",-1,error,0,0);if(error!=0){IOException failed=new IOException("PC1 owned MCI close failed:"+error);if(failure==null)failure=failed;else failure.addSuppressed(failed);}}mciAliases.clear();for(Input input:inputs)if(input.reader!=null&&!input.closed)try{input.reader.close();}catch(IOException closeError){record("fixtureCloseError",-1,-1,0,0);if(failure==null)failure=closeError;else failure.addSuppressed(closeError);}closeNative(handle);handle=0;if(failure!=null)throw failure;}}
    private static native int decoderWordNative(long handle,int region,int offset,boolean write,int value)throws IOException;
    private static native int backendWordNative(long handle,int address,boolean write,int value)throws IOException;
    private static native int[] retiredNative(long handle)throws IOException;
    private static native void bindDspExtraNative(long handle,ByteBuffer raw)throws IOException;
    private static native void bindDspPeNative(long handle,ByteBuffer raw)throws IOException;
    private static native void dspCoefficientsNative(long handle,int extra)throws IOException;
    private static native void dspTransformNative(long handle,int outputOffset,int extra)throws IOException;
    private static native int physicalWordForAuditNative(long handle,int address,boolean write,int value)throws IOException;
    private static native long directAddressNative(ByteBuffer raw)throws IOException;
    private static native int directWordNative(ByteBuffer raw,int offset,boolean write,int value)throws IOException;
    private static native byte[] decoderSeedNative(long handle,int address)throws IOException;
    private static native void bindPhysicalNative(long handle,ByteBuffer[] buffers,ByteBuffer[] masks)throws IOException;
    private static native int[] dspLzAuditNative(long handle,int operation,int packed,byte[] input,int position,int readLimit)throws IOException;
    private static native long[] fileTagsAuditNative(long handle,byte[] rawPath)throws IOException;
    private static native ByteBuffer dspRegionNative(long handle,int address)throws IOException;
    private static native void initializeDecoderNative(long handle)throws IOException;
    private static native long openNative(long hwnd)throws IOException;
    private static native void restoreInitializationNative(long handle,int[] info)throws IOException;
    private static native void bindStreamBytesNative(long handle,ByteBuffer bytes)throws IOException;
    private static native byte[] sharedBytesNative(long handle)throws IOException;
    private static native int backendByteNative(long handle,int address,int offset,int writeValue)throws IOException;
    private static native void snapshotLockNative(long handle,boolean enter)throws IOException;
    private static native void pumpLegacyNative(long handle)throws IOException;
    private static native long openDeferredNative()throws IOException;
    private static native void borrowWindowNative(long handle,long hwnd)throws IOException;
    private static native int initializeNative(long handle,int phase,int value,byte[] format)throws IOException;
    private static native int[] deviceNative(long handle)throws IOException;
    private static native byte[] identityNative(long handle)throws IOException;
    private static native int createNative(long handle,int channel,int flags,int bytes,byte[] format)throws IOException;
    private static native int[] writeNative(long handle,int channel,int offset,byte[] data,int start,int count)throws IOException;
    private static native byte[] bytesNative(long handle,int channel)throws IOException;
    private static native int[] queryNative(long handle,int channel,int kind,int initial)throws IOException;
    private static native int commandNative(long handle,int channel,int operation,int argument)throws IOException;
    private static native int mciNative(long handle,byte[] command,boolean notify)throws IOException;
    private static native int[] timerNative(long handle,int operation,int token,int milliseconds)throws IOException;
    private static native long[] drainNative(long handle)throws IOException;
    private static native long tickNative();
    private static native long[] sourceOpenNative(long handle,byte[] path)throws IOException;
    private static native byte[] sourceReadNative(long handle,long source,long offset,int requested)throws IOException;
    private static native byte[] sourcePathNative(long handle,long id)throws IOException;
    private static native long[] sourceWordNative(long handle,long id,boolean publish,int word)throws IOException;
    private static native long[] sourceInfoNative(long handle,long source)throws IOException;
    private static native void sourceCloseNative(long handle,long source)throws IOException;
    private static native void prepareDecoderNative(long handle,long source,int bytes,int mode,int channels,int packed,int extra)throws IOException;
    private static native int[] bindStreamNative(long handle,int channel,long source,int bytes,byte[] memory,int unit,boolean automatic)throws IOException;
    private static native int[] serviceStreamNative(long handle,boolean periodic,boolean overrideCursor,int cursor)throws IOException;
    private static native long[] streamInfoNative(long handle)throws IOException;
    private static native byte[] sourcePeekNative(long handle,long id,long offset,int count)throws IOException;
    private static native byte[] captureNative(long handle)throws IOException;
    private static native void restoreNative(long handle,byte[] packet)throws IOException;
    private static native int[] adoptNative(long handle)throws IOException;
    static native byte[] deltaForAudit(byte[] encoded,int[] state,int channels)throws IOException;
    private static native void closeNative(long handle)throws IOException;
}
