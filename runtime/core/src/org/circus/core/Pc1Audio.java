package org.circus.core;

import java.io.*;
import java.util.*;
import java.nio.*;

/** Current CHS PC1 audio PE/BSS,41B7F0 initialization,41C220..41CD00 andtimer2.
 * These canonical globals are outside the8004-byte story/system frame.
 *Construction preserves verified PE/BSS defaults and never initializes output.
 * PCM file/decoder/ring-buffer and actual output belong to the required Backend.
 * A logical-state codec alone does not restore those backend owners. */
public final class Pc1Audio {
    public static final int CHANNELS=10;
    private static final int FORMAT=0x50314133;
    /** Fixed native pointer, re-read at each original41BE20 consumption point.
     * A backend must retain this source through metadata and final strcpy. */
    public interface NativeName extends Pc1ByteSource {int address();}
    public interface Backend {
        boolean pcmInitialized()throws IOException;
        /** Memory registration only: never create a device or initialize PCM. */
        default void bindControl(Pc1Audio control)throws IOException{}
        /** Validate stopped staging and borrow its canonical control owner before
         *installation; actual timer/play activation follows installation. */
        default boolean prepareRestoredState(Pc1Audio control,State state)throws IOException{return true;}
        /**41BE20 boundary. Null is failure before any published control-field
         *change. A later native-1 must return its published partial Loaded
         *projection. Success requires a real playable PCM buffer owner. */
        Loaded loadPcm(int channel,byte[] rawResource,boolean streaming)throws IOException;
        /** Complete41BE20 raw-pointer entry, including its own control writes,
         *source aliases and final forward strcpy. Success must be published.
         *A byte[] adapter would move reads before release/metadata effects. */
        default Loaded loadPcmRaw(int requested,NativeName source,boolean streaming)throws IOException{throw new IOException("PC1 complete41BE20 live native-name backend required");}
        /**Typed local sources have no numeric VA. Addressed compatibility
         *keeps the real bus pointer; a local never falls back to byte[]. */
        default Loaded loadPcmSource(int requested,final Pc1ByteSource source,boolean streaming)throws IOException{
            if(source instanceof NativeName)return loadPcmRaw(requested,(NativeName)source,streaming);
            if(source instanceof Pc1ByteSource.Addressed){final Pc1ByteSource.Addressed a=(Pc1ByteSource.Addressed)source;return loadPcmRaw(requested,new NativeName(){public int address(){return a.address();}public int unsignedByte(int n)throws IOException{return a.unsignedByte(n);}public int nativeWord(int n)throws IOException{return a.nativeWord(n);}},streaming);}
            throw new IOException("PC1 complete41BE20 typed local source backend required");
        }
        /**407020 modal/error consumer used by41BE20 after its partial writes. */
        default void nativeMessage(int rawTextAddress)throws IOException{throw new IOException("PC1 required407020 audio message at0x"+Integer.toHexString(rawTextAddress));}
        int stop(int channel)throws IOException;
        int release(int channel)throws IOException;
        /**41B990(0): cancel/wait/close the streaming decoder owner. */
        void closeStream()throws IOException;
        int play(int channel,int flags)throws IOException;
        int setPosition(int channel,int bytePosition)throws IOException;
        int setVolume(int channel,int hundredthsDb)throws IOException;
        int playCursor(int channel)throws IOException;
        int status(int channel)throws IOException;
        /**40E5A0 path resolution, including MIDI folder and native extensions. */
        byte[] midiPath(byte[] rawMusicName)throws IOException;
        /** mciSendStringA. Errors are returned, not silently converted to PCM. */
        int mci(byte[] rawCommand,boolean notifyWindow)throws IOException;
        int setTimer(int id,int milliseconds)throws IOException;
        int killTimer(int token)throws IOException;
        /** Control bytes do not contain decoder/ring-buffer/sample-clock state.
         *Return false unless all live buffers, output and timer owners have
         *actually been restored by their independent backend state owner. */
        boolean adoptRestoredState(State control)throws IOException;
    }
    public static final class Loaded {
        public final int bytes,baseVolume,baseFrequency,frequency,pan;
        public final int loadedFlag,streamingChannel;
        public final boolean cached,successful,published;
        private final byte[] rawResourceCell;
        public Loaded(int bytes,int baseVolume,int baseFrequency,int frequency,int pan,boolean cached){this.bytes=bytes;this.baseVolume=baseVolume;this.baseFrequency=baseFrequency;this.frequency=frequency;this.pan=pan;this.cached=cached;published=false;loadedFlag=1;successful=true;streamingChannel=Integer.MIN_VALUE;rawResourceCell=null;}
        /** Complete already-published control projection, including ordinary
         *native failure after byteLimit/allocation/stream initialization. */
        public Loaded(int bytes,int baseVolume,int baseFrequency,int frequency,int pan,boolean cached,int loadedFlag,boolean successful,int streamingChannel,byte[] rawResourceCell)throws IOException{
            if(rawResourceCell!=null&&rawResourceCell.length!=256)throw new IOException("PC1 PCM loader control projection");
            this.bytes=bytes;this.baseVolume=baseVolume;this.baseFrequency=baseFrequency;this.frequency=frequency;this.pan=pan;this.cached=cached;this.published=false;this.loadedFlag=loadedFlag;this.successful=successful;this.streamingChannel=streamingChannel;this.rawResourceCell=rawResourceCell==null?null:rawResourceCell.clone();
        }
        private Loaded(Loaded source){bytes=source.bytes;baseVolume=source.baseVolume;baseFrequency=source.baseFrequency;frequency=source.frequency;pan=source.pan;loadedFlag=source.loadedFlag;streamingChannel=source.streamingChannel;cached=source.cached;successful=source.successful;rawResourceCell=source.rawResourceCell==null?null:source.rawResourceCell.clone();published=true;}
        public Loaded alreadyPublished(){return new Loaded(this);}
    }
    public static final class Entry {
        private final byte[] name;
        public final int gainTrim;
        public Entry(byte[] rawName,int gainTrim)throws IOException{cstring(rawName);name=rawName.clone();this.gainTrim=gainTrim;}
        public byte[] rawName(){return name.clone();}
    }
    public interface Catalog {
        int musicCount()throws IOException;
        Entry music(int index)throws IOException;
        int soundCount()throws IOException;
        Entry sound(int index)throws IOException;
        /** Raw addressed catalogs override these separately. Native reads trim
         *only AFTER the PCM loader succeeds, and negative rows may alias the
         *channel fields changed by that very load. */
        default byte[] musicName(int index)throws IOException{return music(index).rawName();}
        default int musicGainTrim(int index)throws IOException{return music(index).gainTrim;}
        default byte[] soundName(int index)throws IOException{return sound(index).rawName();}
        default int soundGainTrim(int index)throws IOException{return sound(index).gainTrim;}
    }
    public static Catalog catalog(final NamesCatalog names){
        if(names==null)throw new IllegalArgumentException("PC1 audio catalog missing");
        return new Catalog(){
            public int musicCount()throws IOException{return names.table("music").size();}
            public Entry music(int n)throws IOException{return row(names.table("music"),n,60);}
            public int soundCount()throws IOException{return names.table("sound").size();}
            public Entry sound(int n)throws IOException{return row(names.table("sound"),n,0);}
        };
    }
    private static Entry row(List<NamesCatalog.Entry> rows,int n,int offset)throws IOException{
        if(n<0||n>=rows.size())throw new IOException("PC1 audio catalog address outside owned rows");NamesCatalog.Entry row=rows.get(n);return new Entry(row.nameBytes,(int)Binary.u32(row.extra,offset));
    }
    /** Native frame9D8A0C stays in its canonical frame; only the raw06 bridge
     *needs access to its exact before/after-call write order. */
    public interface LogicalMusic {int read();void write(int value);}
    private final Backend backend;
    private final Catalog catalog;
    private int pcmReady;
    private boolean nativeMemoryBound;
    private Pc1NativeMemory nativeMemory;
    private ByteBuffer livePe=peStorage();
    private static ByteBuffer peStorage(){byte[] seed=Base64.getDecoder().decode(LIVE_PE);ByteBuffer raw=ByteBuffer.allocateDirect(seed.length).order(ByteOrder.LITTLE_ENDIAN);raw.put(seed);return raw;}
    private int initializationDisabled,loaderFlag,loaderChannel,voiceCount;
    private ByteBuffer channelGaps=ByteBuffer.allocateDirect(20).order(ByteOrder.LITTLE_ENDIAN);
    //Verified writable CHS PE neighbors, not zero-filled virtual padding.
    private byte[] waveNeighbors=Base64.getDecoder().decode("AAAAAAAAAQABACJWAABErAAAAgAQAAAAAAAAAAAA");
    private byte[] primaryFormat={1,0,2,0,0x44,(byte)0xac,0,0,0x10,(byte)0xb1,2,0,4,0,16,0,0,0};
    private int scriptMode,currentTrack=-1,currentMode,pending,fade,fadeVolume,pendingTrack,pendingMode,soundFade;
    private int mciClosed,timerToken,timerInterval,refreshCount;
    private int[] loaded=new int[CHANNELS],baseVolume=new int[CHANNELS],baseFrequency=new int[CHANNELS],frequency=new int[CHANNELS],pan=new int[CHANNELS];
    private final int[] byteLimit=new int[CHANNELS];
    private final List<Pc1MmPhysicalMemory.Slice> physical=new ArrayList<>();
    private Pc1MmPhysicalMemory.Slice physicalAt(int address,int count){for(Pc1MmPhysicalMemory.Slice view:physical)if((address&0xffffffffL)>=(view.address()&0xffffffffL)&&(address&0xffffffffL)+count<=(view.address()&0xffffffffL)+view.length())return view;return null;}
    public boolean nativePhysicalBound(){return nativeMemoryBound;}
    public Pc1MmPhysicalMemory.Slice streamingWord(int index)throws IOException{int address=index==4?0x43dbdc:0xbbaaf8+index*44;if(index<0||index>4||!nativeMemoryBound)throw new IOException("PC1 native streaming word requires physical owner");return physicalAt(address,4);}
    private byte[] rawBytes(int address,int count)throws IOException{byte[] out=new byte[count];for(int n=0;n<count;n++)out[n]=(byte)readNativeByte(address+n);return out;}
    private final byte[][] resources=new byte[CHANNELS][256];
    private final byte[] mciPath=new byte[256];
    public Pc1Audio(Backend backend,Catalog catalog)throws IOException{
        if(backend==null||catalog==null)throw new IOException("PC1 audio requires real backend and catalog owners");this.backend=backend;this.catalog=catalog;mciPath[0]=(byte)255;channelGaps.putInt(16,-1);backend.bindControl(this);
    }
    /**41CD90 tail-jumps41B7F0. These are real required physical leaves, not
     *success stubs around an already eager constructor. HRESULTs after
     *DirectSoundCreate are ignored just as native; a NULL primary dereference
     *must be reported by setPrimaryFormat before logical initialization. */
    public interface Initialization {
        int directSoundCreate()throws IOException;
        int setCooperativeLevel(int level)throws IOException; //same physicalmainHWND
        int createPrimary(int descriptorBytes,int flags)throws IOException;
        int setPrimaryFormat(byte[] rawWaveFormat)throws IOException;
        void clearChannelPointers()throws IOException; //native overwrites10 pointers, noRelease
        void initializeDecoderTables()throws IOException; //403B40
        void loadVoiceIndex()throws IOException; //41BC60, writes its own pointer/count owner
        void failureMessage(int rawText)throws IOException; //407020 mainHWND/title4302F4/flags0
    }
    private boolean initializing;
    /** No ready guard is invented: native checks only BC1D60's failed-create
     *latch, so a second successful call really initializes again. */
    public int initializeNative(Initialization init)throws IOException{
        if(init==null)throw new IOException("PC1 native audio initializer required");if(initializing)throw new IOException("PC1 native audio initialize reentry");
        if(readNativeInt(0xbc1d60)!=0)return 0;initializing=true;
        try{
            if(init.directSoundCreate()!=0){init.failureMessage(0x43dd6c);writeNativeInt(0xbc1d60,1);return 0;}
            init.setCooperativeLevel(2);init.createPrimary(20,0x4009);init.setPrimaryFormat(rawBytes(0x43dbb0,18));
            init.clearChannelPointers();initializeControlArrays();
            writeNativeInt(0xbc12cc,0);pcmReady=1;pendingTrack=-1;pendingMode=0;fadeVolume=0;
            init.initializeDecoderTables();init.loadVoiceIndex();return 1;
        }finally{initializing=false;}
    }
    private void initializeControlArrays()throws IOException{
        //41B89A: physical pointer clear precedes pan,freq,basefreq,vol,loaded.
        for(int base:new int[]{0xbbab80,0xbbab54,0xbbab28,0xbb8550,0xbbaafc})for(int n=0;n<CHANNELS;n++)writeNativeInt(base+n*4,0);
        for(byte[] name:resources)name[0]=(byte)255;
    }
    /** Explicit post-init import for an independently initialized backend or
     *a self-authored fixture. This is NOT the production startup sequence. */
    public void adoptAlreadyInitialized()throws IOException{
        if(initializing||!backend.pcmInitialized())throw new IOException("PC1 post-init import requires initialized physical PCM backend");
        initializeControlArrays();writeNativeInt(0xbc12cc,0);pcmReady=1;pendingTrack=-1;pendingMode=0;fadeVolume=0;
    }
    /** One shared20B owner:16 interleaved stream bytes and43DBDC channel. Native MM consumers may mutate only these backend
     *stream words; snapshots must hold the backend lock. Bank bytealiases remain
     *byte accesses. Native DWORD consumers verify4-byte alignment and LE. */
    public ByteBuffer borrowStreamingExtra()throws IOException{if(nativeMemoryBound)return physicalAt(0x43de74,4).borrow();ByteBuffer view=livePe.duplicate().order(ByteOrder.LITTLE_ENDIAN);view.position(512);view.limit(516);return view.slice().order(ByteOrder.LITTLE_ENDIAN);}
    public ByteBuffer borrowStreamingBytes()throws IOException{if(nativeMemoryBound)throw new IOException("PC1 native interleaves require five addressed streaming views, no packed20 shadow");return channelGaps.duplicate().order(ByteOrder.LITTLE_ENDIAN);}
    public int pcmReadyRaw(){return pcmReady;}
    public int initializationDisabled()throws IOException{return readNativeInt(0xbc1d60);}
    public interface Shutdown {void releasePrimaryIfPresent()throws IOException;void releaseDeviceIfPresent()throws IOException;}
    /**41CDA0→41B940. MIDI/currentrequest/refresh/stale byte limits are not
     *cleared by native. A physical release fault preserves preceding writes. */
    public void closeNative(Shutdown shutdown)throws IOException{
        if(shutdown==null)throw new IOException("PC1 native audio shutdown required");for(int n=0;n<CHANNELS;n++)releasePcm(n);
        shutdown.releasePrimaryIfPresent();shutdown.releaseDeviceIfPresent();int token=timerToken;pcmReady=0;fade=0;
        if(token!=0){backend.killTimer(token);timerToken=0;timerInterval=0;}
    }
    public int scriptMode(){return scriptMode;}
    public int currentTrack(){return currentTrack;}
    public int currentMode(){return currentMode;}
    public int pendingTrack(){return pendingTrack;}
    public boolean fadingMusic(){return fade!=0;}
    public boolean pendingMusic(){return pending!=0;}
    public int fadeVolume(){return fadeVolume;}
    public int timerToken(){return timerToken;}
    public int timerInterval(){return timerInterval;}
    public int activationRefreshes(){return refreshCount;}
    /** Borrowed byte views of the same control words, not another timer or
     *refresh counter. OS registration and decoder/output ownership stay in
     *the backend and require their own adoption at snapshot restore. */
    public void bindNativeMemory(Pc1NativeMemory memory)throws IOException{
        if(memory==null||nativeMemoryBound)throw new IOException("PC1 audio native views require unique canonical memory");
        byte[][] initial=new byte[NATIVE_RANGES.length][];for(int n=0;n<initial.length;n++)initial[n]=rawBytes(NATIVE_RANGES[n][0],NATIVE_RANGES[n][1]);
        Pc1MmPhysicalMemory islands=memory.mmPhysicalMemory();int[][] declarations=islands.islands();
        for(int n=0;n<NATIVE_RANGES.length;n++){final int[] range=NATIVE_RANGES[n];for(int[] island:declarations){int lo=Math.max(range[0],island[0]),hi=Math.min(range[0]+range[1],island[1]);if(lo<hi)physical.add(islands.claim(lo,Arrays.copyOfRange(initial[n],lo-range[0],hi-range[0]),true));}memory.bind(range[0],range[1],new Pc1NativeMemory.Region(){public int read(int offset)throws IOException{return readNativeByte(range[0]+offset);}public void write(int offset,int value)throws IOException{writeNativeByte(range[0]+offset,value);}public int readInt32(int offset)throws IOException{return readRawWord32(range[0]+offset);}public void writeInt32(int offset,int value)throws IOException{writeRawWord32(range[0]+offset,value);}});}
        nativeMemory=memory;nativeMemoryBound=true;
        //Legacy standalone storage is retired; current getters/codecs useonly
        //the canonical slices. The Cprovider must reborrow before MM begins.
        loaded=baseVolume=baseFrequency=frequency=pan=null;channelGaps=null;waveNeighbors=null;primaryFormat=Arrays.copyOf(primaryFormat,16);ByteBuffer prefix=ByteBuffer.allocateDirect(512).order(ByteOrder.LITTLE_ENDIAN);for(int n=0;n<512;n++)prefix.put(n,livePe.get(n));livePe=prefix;
    }
    public void requireBus(Pc1NativeMemory memory)throws IOException{if(memory==null||!nativeMemoryBound||nativeMemory!=memory)throw new IOException("PC1 audio consumer requires its bound canonical memory");}
    private static final int[] WORD_ADDRESS={0xbb8438,0x43daa8,0xbc1d34,0xbc1d18,0xbc1d1c,0xbc12f8,0xbc1300,0xbc12fc,0xbc1d20,0xbc1d30,0x94c674,0x94c6ac,0x43dbdc,0xbc1d10,0xbc1d60,0xbc12cc,0xbc1d04,0xbc1d2c};
    private static final int[][] NATIVE_RANGES={{0xbb8438,4},{0x43daa8,4},{0x43daac,256},{0x43dbb0,18},{0x43dbc2,26},{0x43dbdc,4},{0x43dbe4,4},{0xbb8550,40},{0xbbaaf8,176},{0xbc12cc,4},{0xbc12f8,12},{0xbc1304,2560},{0xbc1d04,4},{0xbc1d10,4},{0xbc1d18,12},{0xbc1d2c,12},{0xbc1d38,40},{0xbc1d60,4},{0x94c674,4},{0x94c6ac,4},{0x43dbe8,416},{0x43de0c,44},{0x43de40,56}};
    private int word(int n){switch(n){case 0:return scriptMode;case 1:return currentTrack;case 2:return currentMode;case 3:return pending;case 4:return fade;case 5:return fadeVolume;case 6:return pendingTrack;case 7:return pendingMode;case 8:return soundFade;case 9:return mciClosed;case 10:return timerToken;case 11:return refreshCount;case 12:return channelGaps.getInt(16);case 13:return pcmReady;case 14:return initializationDisabled;case 15:return loaderFlag;case 16:return loaderChannel;case 17:return voiceCount;default:throw new AssertionError();}}
    private void word(int n,int v){switch(n){case 0:scriptMode=v;break;case 1:currentTrack=v;break;case 2:currentMode=v;break;case 3:pending=v;break;case 4:fade=v;break;case 5:fadeVolume=v;break;case 6:pendingTrack=v;break;case 7:pendingMode=v;break;case 8:soundFade=v;break;case 9:mciClosed=v;break;case 10:timerToken=v;break;case 11:refreshCount=v;break;case 12:channelGaps.putInt(16,v);break;case 13:pcmReady=v;break;case 14:initializationDisabled=v;break;case 15:loaderFlag=v;break;case 16:loaderChannel=v;break;case 17:voiceCount=v;break;default:throw new AssertionError();}}
    /** The176B span includes four intervening streaming DWORDs (16bytes) between channel
     *arrays. They are live canonical storage, not padding reset by41B7F0. */
    private static final int[][] LIVE_PE_RANGES={{0x43dbe8,416},{0x43de0c,44},{0x43de40,56}};
    private static int livePeOffset(int address){int base=0;for(int[] r:LIVE_PE_RANGES){int at=address-r[0];if(at>=0&&at<r[1])return base+at;base+=r[1];}return -1;}
    private static final String LIVE_PE="AAAAAGjdQwBk3UMAAQAAAGDdQwBc3UMAAgAAAFjdQwBU3UMAAwAAAFDdQwBM3UMABAAAAEjdQwBE3UMABQAAAEDdQwDY2EMABgAAADzdQwA43UMABwAAADTdQwAw3UMACAAAACzdQwAo3UMACQAAACTdQwAg3UMACgAAABzdQwAY3UMACwAAABTdQwAQ3UMADAAAAAzdQwAI3UMADQAAAATdQwAA3UMADgAAAPzcQwD43EMADwAAAPTcQwDw3EMAEAAAAOzcQwDo3EMACwAAAOTcQwDg3EMABgAAANzcQwDY3EMA/////wAAAAAAAAAAcAAAAHBpcgBxAAAAbWlzAHUAAAB1dGEAYwAAAGtveQBvAAAAc3VnAGkAAABhc3UAcgAAAGthcwB5AAAAeW9yAGQAAABrdWQAYgAAAGl6dQB0AAAAdGFtAGcAAABuYW4AYQAAAGFsaQBtYWsAZQAAAG1vZQB3AAAAbWloAGsAAABrb3QAcwAAAHNhawBuAAAAbmVtAERpcmVjdFNvdW5kpKzKudPDpMekraTepLuk8wBwbGF5ICIlcyIAAABwbGF5ICIlcyIgbm90aWZ5AAAAAG9wZW4gIiVzIgAAAC0xAABjbG9zZSAiJXMiAABzdG9wICIlcyIAAABHQVlBAAAAAE9NQUtFAAAAU1lTVEVNAAABAAAA";
    public int readNativeByte(int address)throws IOException{
        Pc1MmPhysicalMemory.Slice shared=physicalAt(address,1);if(shared!=null)return shared.read(address-shared.address());
        int pe=livePeOffset(address);if(pe>=0)return livePe.get(pe)&255;
        for(int n=0;n<WORD_ADDRESS.length;n++){int at=address-WORD_ADDRESS[n];if(at>=0&&at<4)return word(n)>>>(at*8)&255;}
        int at=address-0x43daac;if(at>=0&&at<256)return mciPath[at]&255;
        at=address-0x43dbb0;if(at>=0&&at<18)return primaryFormat[at]&255;
        at=address-0x43dbc2;if(at>=0&&at<26)return waveNeighbors[at]&255;
        at=address-0x43dbe4;if(at>=0&&at<4)return waveNeighbors[26+at]&255;
        at=address-0xbb8550;if(at>=0&&at<40)return baseVolume[at/4]>>>((at&3)*8)&255;
        at=address-0xbbaaf8;if(at>=0&&at<176){int block=at/44,p=at%44;if(p<4)return channelGaps.get(block*4+p)&255;int[][] arrays={loaded,baseFrequency,frequency,pan};return arrays[block][(p-4)/4]>>>(((p-4)&3)*8)&255;}
        at=address-0xbc1304;if(at>=0&&at<2560)return resources[at/256][at%256]&255;
        at=address-0xbc1d38;if(at>=0&&at<40)return byteLimit[at/4]>>>((at&3)*8)&255;
        throw new IOException("PC1 audio native byte is not owned:0x"+Integer.toHexString(address));
    }
    public void writeNativeByte(int address,int value)throws IOException{
        Pc1MmPhysicalMemory.Slice shared=physicalAt(address,1);if(shared!=null){shared.write(address-shared.address(),value);return;}
        int pe=livePeOffset(address);if(pe>=0){livePe.put(pe,(byte)value);return;}
        value&=255;for(int n=0;n<WORD_ADDRESS.length;n++){int at=address-WORD_ADDRESS[n];if(at>=0&&at<4){word(n,patch(word(n),at,value));return;}}
        int at=address-0x43daac;if(at>=0&&at<256){mciPath[at]=(byte)value;return;}
        at=address-0x43dbb0;if(at>=0&&at<18){primaryFormat[at]=(byte)value;return;}
        at=address-0x43dbc2;if(at>=0&&at<26){waveNeighbors[at]=(byte)value;return;}
        at=address-0x43dbe4;if(at>=0&&at<4){waveNeighbors[26+at]=(byte)value;return;}
        at=address-0xbb8550;if(at>=0&&at<40){baseVolume[at/4]=patch(baseVolume[at/4],at&3,value);return;}
        at=address-0xbbaaf8;if(at>=0&&at<176){int block=at/44,p=at%44;if(p<4)channelGaps.put(block*4+p,(byte)value);else{int[][] arrays={loaded,baseFrequency,frequency,pan};int n=(p-4)/4;arrays[block][n]=patch(arrays[block][n],(p-4)&3,value);}return;}
        at=address-0xbc1304;if(at>=0&&at<2560){resources[at/256][at%256]=(byte)value;return;}
        at=address-0xbc1d38;if(at>=0&&at<40){byteLimit[at/4]=patch(byteLimit[at/4],at&3,value);return;}
        throw new IOException("PC1 audio native byte is not owned:0x"+Integer.toHexString(address));
    }
    private int readRawWord32(int address)throws IOException{
        Pc1MmPhysicalMemory.Slice shared=physicalAt(address,4);if(shared!=null)return shared.readInt32(address-shared.address());
        int pe=livePeOffset(address);if(pe>=0&&pe+4<=livePe.capacity()&&livePeOffset(address+3)==pe+3)return livePe.getInt(pe);
        for(int n=0;n<WORD_ADDRESS.length;n++)if(address==WORD_ADDRESS[n])return word(n);
        int at=address-0xbb8550;if(at>=0&&at<=36&&(at&3)==0)return baseVolume[at/4];
        at=address-0xbc1d38;if(at>=0&&at<=36&&(at&3)==0)return byteLimit[at/4];
        at=address-0xbbaaf8;if(at>=0&&at<=172&&(at&3)==0){int block=at/44,p=at%44;if(p==0)return channelGaps.getInt(block*4);int[][] arrays={loaded,baseFrequency,frequency,pan};return arrays[block][(p-4)/4];}
        int value=0;for(int n=0;n<4;n++)value|=readNativeByte(address+n)<<(n*8);return value;
    }
    private void writeRawWord32(int address,int value)throws IOException{
        Pc1MmPhysicalMemory.Slice shared=physicalAt(address,4);if(shared!=null){shared.writeInt32(address-shared.address(),value);return;}
        int pe=livePeOffset(address);if(pe>=0&&pe+4<=livePe.capacity()&&livePeOffset(address+3)==pe+3){livePe.putInt(pe,value);return;}
        for(int n=0;n<WORD_ADDRESS.length;n++)if(address==WORD_ADDRESS[n]){word(n,value);return;}
        int at=address-0xbb8550;if(at>=0&&at<=36&&(at&3)==0){baseVolume[at/4]=value;return;}
        at=address-0xbc1d38;if(at>=0&&at<=36&&(at&3)==0){byteLimit[at/4]=value;return;}
        at=address-0xbbaaf8;if(at>=0&&at<=172&&(at&3)==0){int block=at/44,p=at%44;if(p==0){channelGaps.putInt(block*4,value);return;}int[][] arrays={loaded,baseFrequency,frequency,pan};arrays[block][(p-4)/4]=value;return;}
        for(int n=0;n<4;n++)writeNativeByte(address+n,value>>>(n*8));
    }
    private static int patch(int old,int at,int value){int shift=at*8;return old&~(255<<shift)|value<<shift;}
    public boolean loaded(int channel)throws IOException{channel(channel);return readNativeInt(0xbbaafc+(channel)*4)!=0;}
    public int baseVolume(int channel)throws IOException{channel(channel);return readNativeInt(0xbb8550+(channel)*4);}
    public int byteLimit(int channel)throws IOException{channel(channel);return byteLimit[channel];}
    public void applySettingsEffect(Pc1NativeSettings.Effect effect,Pc1NativeSettings settings)throws IOException{
        if(effect==null)throw new IOException("PC1 settings effect missing");int[] a=effect.arguments();
        switch(effect.kind){
        case REFRESH_VOLUMES_41CD00:refreshVolumes(settings);return;
        case STOP_MUSIC_41C650:stopMusic(a[0],settings);return;
        case REQUEST_MUSIC_41C4B0:requestMusic(a[0],a[1],settings);return;
        case ARM_AUDIO_TIMER_4070E0:armTimer();return;
        default:throw new IOException("PC1 audio cannot execute file/settings effect "+effect.kind);
        }
    }
    /**41C220 signed32 arithmetic, including the level0 special case. */
    public static int volume(int base,int level){if(level==0)return -10000;int result=base+level*100-3000;return Math.max(-3000,Math.min(0,result));}
    public static float linearGain(int hundredthsDb){return (float)Math.pow(10,hundredthsDb/2000.0);}
    private int preference(Pc1NativeSettings settings,Pc1NativeSettings.AudioField field)throws IOException{if(nativeMemory!=null)return nativeWord(Pc1NativeMemory.SETTINGS_BASE+field.offset);if(settings==null)throw new IOException("PC1 audio settings owner missing");return settings.audio(field);}
    public void requestMusic(int track,int mode,Pc1NativeSettings settings)throws IOException{
        if(preference(settings,Pc1NativeSettings.AudioField.BGM_ENABLED)==0){currentTrack=track;currentMode=mode;return;}
        if(track==-1||track>=catalog.musicCount())return;
        if(preference(settings,Pc1NativeSettings.AudioField.BGM_BACKEND)==1){currentTrack=track;currentMode=mode;stopMusic(1,settings);resumeMusic(settings);return;}
        if(pcmReady==0)return;
        if(readNativeInt(0xbbaafc+(0)*4)==0){
            pending=0;byte[] name=musicName(track);
            if(load(0,name,true)){
                writeNativeInt(0xbb8550,readNativeInt(0xbb8550)+catalog.musicGainTrim(track)*-100);
                if(readNativeInt(0xbbaafc+(0)*4)!=0){int gain=volume(readNativeInt(0xbb8550+(0)*4),preference(settings,Pc1NativeSettings.AudioField.BGM_VOLUME));backend.setVolume(0,gain);backend.play(0,mode==1?0:1);backend.setVolume(0,gain);}
                currentMode=mode;currentTrack=track;
            }
        }else{
            pending=1;pendingTrack=track;pendingMode=mode;if(fade!=0)return;
            fade=1;int gain=volume(readNativeInt(0xbb8550+(0)*4),preference(settings,Pc1NativeSettings.AudioField.BGM_VOLUME));if(fadeVolume==0)fadeVolume=gain;
        }
    }
    public void stopMusic(int keepRequest,Pc1NativeSettings settings)throws IOException{
        if(preference(settings,Pc1NativeSettings.AudioField.BGM_BACKEND)==1)stopMidi();
        else{
            if(pcmReady==0)return;pendingTrack=-1;
            if(readNativeInt(0xbbaafc+(0)*4)!=0&&fade==0){fade=1;fadeVolume=volume(readNativeInt(0xbb8550+(0)*4),preference(settings,Pc1NativeSettings.AudioField.BGM_VOLUME));}
        }
        if(keepRequest==0){currentTrack=-1;currentMode=0;}
    }
    public void immediateStopMusic(int keepRequest,Pc1NativeSettings settings)throws IOException{
        if(preference(settings,Pc1NativeSettings.AudioField.BGM_BACKEND)==1)stopMidi();
        else{if(pcmReady==0)return;stopPcm(0);releasePcm(0);}
        if(keepRequest==0){currentTrack=-1;currentMode=0;}
    }
    public void resumeMusic(Pc1NativeSettings settings)throws IOException{
        if(pcmReady==0||preference(settings,Pc1NativeSettings.AudioField.BGM_ENABLED)==0)return;
        if(preference(settings,Pc1NativeSettings.AudioField.BGM_BACKEND)==1)playMidi(currentMode==0,settings);
        else requestMusic(currentTrack,currentMode,settings);
    }
    public void commandMusic(int track,int mode,int selected,LogicalMusic frame,Pc1NativeSettings settings)throws IOException{
        if(frame==null)throw new IOException("PC1 raw06 needs its canonical logical-music frame owner");
        if(track==255){frame.write(-1);immediateStopMusic(0,settings);return;}
        if(track==254)track=selected;
        if(mode==0){scriptMode=0;requestMusic(track,scriptMode,settings);}
        else if(mode==1){if(frame.read()!=track){scriptMode=0;requestMusic(track,scriptMode,settings);}}
        else if(mode==2){scriptMode=1;requestMusic(track,scriptMode,settings);}
        armTimer();frame.write(track);
    }
    /**41C6D0: threshold is tested BEFORE this tick's subtraction. */
    public boolean musicFadeTick(Pc1NativeSettings settings)throws IOException{
        if(fade==0)return true;if(readNativeInt(0xbbaafc+(0)*4)==0){fade=0;return true;}
        if(fadeVolume<=-5000){
            fade=fadeVolume=pending=0;stopPcm(0);releasePcm(0);
            if(pendingTrack!=-1){requestMusic(pendingTrack,pendingMode,settings);pendingTrack=-1;pendingMode=0;}return true;
        }
        fadeVolume-=pending!=0?800:150;backend.setVolume(0,fadeVolume);return false;
    }
    public void sound(int track,int loop,Pc1NativeSettings settings)throws IOException{
        soundFade=0;if(track==-1||track>=catalog.soundCount()||preference(settings,Pc1NativeSettings.AudioField.SE_ENABLED)==0||pcmReady==0)return;
        if(readNativeInt(0xbbaafc+(1)*4)!=0){stopPcm(1);releasePcm(1);}byte[] name=catalog.soundName(track);
        if(load(1,name,false)){writeNativeInt(0xbb8554,readNativeInt(0xbb8554)+catalog.soundGainTrim(track)*-100);backend.setVolume(1,volume(readNativeInt(0xbb8550+(1)*4),preference(settings,Pc1NativeSettings.AudioField.SE_VOLUME)));if(readNativeInt(0xbbaafc+(1)*4)!=0)backend.play(1,loop==1?1:0);}
    }
    public void stopSound()throws IOException{soundFade=0;stopPcm(1);}
    public void startSoundFade(Pc1NativeSettings settings)throws IOException{if(pcmReady!=0&&readNativeInt(0xbbaafc+(1)*4)!=0&&soundFade==0){soundFade=1;writeNativeInt(0xbb8554,volume(readNativeInt(0xbb8554),preference(settings,Pc1NativeSettings.AudioField.SE_VOLUME)));}}
    public boolean soundFadeTick()throws IOException{
        if(soundFade!=0){if(readNativeInt(0xbbaafc+(1)*4)!=0){if(readNativeInt(0xbb8550+(1)*4)>-5000){writeNativeInt(0xbb8554,readNativeInt(0xbb8554)-150);if(readNativeInt(0xbb8550+(1)*4)>-5000){backend.setVolume(1,readNativeInt(0xbb8550+(1)*4));return false;}}stopPcm(1);writeNativeInt(0xbb8554,0);releasePcm(1);}soundFade=0;}return true;
    }
    public void voice(byte[] raw,Pc1NativeSettings settings)throws IOException{
        if(preference(settings,Pc1NativeSettings.AudioField.VOICE_ENABLED)==0||pcmReady==0||raw==null||raw.length==0)return;cstring(raw);
        if(!forced(raw)&&muteByte(settings,60+character(raw))!=0)return;
        if(readNativeInt(0xbbaafc+(2)*4)!=0){stopPcm(2);releasePcm(2);}
        if(load(2,raw,false)){backend.setVolume(2,volume(readNativeInt(0xbb8550+(2)*4),preference(settings,Pc1NativeSettings.AudioField.VOICE_VOLUME)));if(readNativeInt(0xbbaafc+(2)*4)!=0){backend.setPosition(2,0);backend.play(2,0);}}
    }
    public void mixVoice(byte[] raw,int slot,Pc1NativeSettings settings)throws IOException{
        if(preference(settings,Pc1NativeSettings.AudioField.VOICE_ENABLED)==0||pcmReady==0||raw==null||raw.length==0)return;cstring(raw);
        int at=slot+3;if(at>9)return;if(readNativeInt(0xbbaafc+at*4)!=0){stopPcm(at);releasePcm(at);}if(load(at,raw,false))backend.setVolume(at,nativeVolume(at,preference(settings,Pc1NativeSettings.AudioField.VOICE_VOLUME)));
    }
    /**41CC10 live source route. Keep the upper-only signed slot check and
     *sample only the first byte before old-channel release, never strlen. */
    public void mixVoiceSource(Pc1ByteSource source,int slot,Pc1NativeSettings settings)throws IOException{
        if(preference(settings,Pc1NativeSettings.AudioField.VOICE_ENABLED)==0||pcmReady==0||source==null)return;
        if(nativeMemory==null)throw new IOException("PC1 typed mix requires canonical native memory");
        if(source instanceof Pc1ByteSource.Addressed){Pc1ByteSource.Addressed a=(Pc1ByteSource.Addressed)source;if(a.address()==0)return;if(a.bus()!=nativeMemory)throw new IOException("PC1 mix source belongs to another canonical bus");}
        if(source.unsignedByte(0)==0)return;int at=slot+3;if(at>9)return;
        if(readNativeInt(0xbbaafc+at*4)!=0){stopPcm(at);releasePcm(at);}Loaded result=backend.loadPcmSource(at,source,false);if(result==null)return;
        if(!result.published)throw new IOException("PC1 typed mix backend must publish actual41BE20 effects");if(result.successful)backend.setVolume(at,nativeVolume(at,preference(settings,Pc1NativeSettings.AudioField.VOICE_VOLUME)));
    }
    /** Tail after Pc1VoiceDispatch's actual CB10 admission. No repeated
     *preference/first-byte/forced/mute checks, and no early name copy. */
    public void voiceAdmitted(final int rawPointer,Pc1NativeSettings settings)throws IOException{if(nativeMemory==null)throw new IOException("PC1 admitted raw voice requires canonical native memory");voiceAdmitted(Pc1ByteSource.addressed(nativeMemory,rawPointer),settings);}
    public void voiceAdmitted(Pc1ByteSource source,Pc1NativeSettings settings)throws IOException{
        if(nativeMemory==null)throw new IOException("PC1 admitted raw voice requires canonical native memory");
        if(source==null)throw new IOException("PC1 admitted typed voice source missing");
        if(source instanceof Pc1ByteSource.Addressed&&((Pc1ByteSource.Addressed)source).bus()!=nativeMemory)throw new IOException("PC1 admitted source belongs to another native bus");
        if(readNativeInt(0xbbaafc+(2)*4)!=0){stopPcm(2);releasePcm(2);}
        Loaded result=backend.loadPcmSource(2,source,false);if(result==null)return;
        if(!result.published)throw new IOException("PC1 live-name backend must publish its actual41BE20 effects before returning");
        if(result.successful){backend.setVolume(2,volume(readNativeInt(0xbb8550+(2)*4),preference(settings,Pc1NativeSettings.AudioField.VOICE_VOLUME)));if(readNativeInt(0xbbaafc+(2)*4)!=0){backend.setPosition(2,0);backend.play(2,0);}}
    }
    public void startMix(int slot)throws IOException{int at=slot+3;if(at>9)return;if(readNativeInt(0xbbaafc+at*4)!=0){backend.setPosition(at,0);backend.play(at,0);}}
    public void stopVoices()throws IOException{stopPcm(2);for(int n=3;n<10;n++)if(readNativeInt(0xbbaafc+(n)*4)!=0){stopPcm(n);releasePcm(n);}}
    public void refreshVolumes(Pc1NativeSettings settings)throws IOException{
        if(readNativeInt(0xbbaafc+(0)*4)!=0){int gain=volume(readNativeInt(0xbb8550+(0)*4),preference(settings,Pc1NativeSettings.AudioField.BGM_VOLUME));backend.setVolume(0,0);backend.setVolume(0,gain);}
        if(readNativeInt(0xbbaafc+(2)*4)!=0){int gain=volume(readNativeInt(0xbb8550+(2)*4),preference(settings,Pc1NativeSettings.AudioField.VOICE_VOLUME));backend.setVolume(2,0);backend.setVolume(2,gain);}
    }
    public int channelIdle(int at)throws IOException{
        channel(at);if(pcmReady==0||readNativeInt(0xbbaafc+(at)*4)==0)return 1;
        int cursor=backend.playCursor(at);if(Integer.compareUnsigned(byteLimit[at],cursor)<0){byteLimit[at]=0;return 1;}return backend.status(at)==0?1:0;
    }
    public int musicIdle(Pc1NativeSettings settings)throws IOException{if(preference(settings,Pc1NativeSettings.AudioField.BGM_ENABLED)==0)return 1;if(pending!=0)return 0;return channelIdle(0);}
    public int soundIdle(Pc1NativeSettings settings)throws IOException{return preference(settings,Pc1NativeSettings.AudioField.SE_ENABLED)==0?1:channelIdle(1);}
    public int voiceIdle(Pc1NativeSettings settings)throws IOException{return preference(settings,Pc1NativeSettings.AudioField.VOICE_ENABLED)==0?1:channelIdle(2);}
    public int query99(int index,int external94c624,Pc1NativeSettings settings)throws IOException{
        switch(index){case 247:return external94c624==0?1:0;case 248:return soundIdle(settings);case 249:return voiceIdle(settings);case 250:return musicIdle(settings);default:throw new IOException("PC1 audio does not own99:"+index);}
    }
    public void armTimer()throws IOException{
        if(fade!=0&&timerToken==0)setTimer(10);if(soundFade!=0&&timerToken==0)setTimer(10);if(refreshCount!=0&&timerToken==0)setTimer(150);
    }
    private void setTimer(int interval)throws IOException{timerToken=backend.setTimer(2,interval);timerInterval=timerToken==0?0:interval;}
    /** Caller supplies the native window gates94C688/94C68C. */
    public void activation(boolean active,int external94c688,int external94c68c)throws IOException{if(external94c688==0&&active&&external94c68c==0&&timerToken==0){refreshCount=5;armTimer();}}
    public boolean onTimer2(int token,Pc1NativeSettings settings)throws IOException{
        if(token==0||token!=timerToken)return false;handleNativeTimer2(settings);return true;
    }
    /** The complete already-classified405E50 timer2 branch. That native
     *comparison may match raw0 after timer1 did not match; callers must not
     *discard it merely because no OS timer was registered with that token. */
    public void handleNativeTimer2(Pc1NativeSettings settings)throws IOException{backend.killTimer(timerToken);timerToken=timerInterval=0;if(!musicFadeTick(settings))armTimer();if(!soundFadeTick())armTimer();if(refreshCount>0){refreshVolumes(settings);refreshCount--;armTimer();}}
    /**Actual x86 DWORD addressing; negative and wrapped indices may reach
     *other reviewed owners. No native lower bound is invented here. */
    public int readNativeInt(int address)throws IOException{return nativeMemory==null?readRawWord32(address):nativeMemory.readInt32(address);}
    public void writeNativeInt(int address,int value)throws IOException{if(nativeMemory==null)writeRawWord32(address,value);else nativeMemory.writeInt32(address,value);}
    private void writeAddress(int address,int value)throws IOException{if(nativeMemory==null)writeNativeByte(address,value);else nativeMemory.writeAddress(address,value);}
    private int nativeVolume(int at,int level)throws IOException{return volume(level==0?0:readNativeInt(0xbb8550+at*4),level);}
    private boolean load(int requested,byte[] name,boolean stream)throws IOException{
        cstring(name);if(pcmReady==0)return false;
        if(requested==-1){
            if(readNativeInt(0xbc12cc)==0){loaderChannel=-1;for(int n=0;n<CHANNELS;n++)if(readNativeInt(0xbbaafc+n*4)==0){loaderChannel=n;break;}if(loaderChannel==-1){backend.nativeMessage(0x43dde8);return false;}}
        }else{if(readNativeInt(0xbbaafc+requested*4)!=0)return false;loaderChannel=requested;}
        int at=loaderChannel;Loaded result=backend.loadPcm(at,name.clone(),stream);if(result==null)return false;
        if(!result.published)publishLoaded(at,name,stream,result);return result.successful;
    }
    /**Physical41BE20 provider publishes before late modal/COM failures. The
     *legacy fixture projection remains available at the call boundary. */
    public void publishLoaded(int at,byte[] name,boolean stream,Loaded result)throws IOException{
        writeNativeInt(0xbbaafc+at*4,result.loadedFlag);writeNativeInt(0xbc1d38+at*4,result.bytes);writeNativeInt(0xbb8550+at*4,result.baseVolume);writeNativeInt(0xbbab28+at*4,result.baseFrequency);writeNativeInt(0xbbab54+at*4,result.frequency);writeNativeInt(0xbbab80+at*4,result.pan);
        if(result.streamingChannel!=Integer.MIN_VALUE)writeNativeInt(0x43dbdc,result.streamingChannel);else if(stream&&result.successful)writeNativeInt(0x43dbdc,at);
        int cell=0xbc1304+(at<<8);
        if(result.rawResourceCell!=null)for(int n=0;n<256;n++)writeAddress(cell+n,result.rawResourceCell[n]);
        else if(result.successful&&!result.cached){if(name.length>255)throw new IOException("PC1 PCM loader did not report its native late256-byte name failure projection");for(int n=0;n<=name.length;n++)writeAddress(cell+n,n==name.length?0:name[n]);}
    }
    private void stopPcm(int at)throws IOException{if(pcmReady!=0&&readNativeInt(0xbbaafc+at*4)!=0)backend.stop(at);}
    private void releasePcm(int at)throws IOException{
        if(readNativeInt(0xbbaafc+at*4)==0)return;writeNativeInt(0xbbaafc+at*4,0);writeAddress(0xbc1304+(at<<8),255);writeNativeInt(0xbb8550+at*4,0);writeNativeInt(0xbbab28+at*4,0);writeNativeInt(0xbbab54+at*4,0);writeNativeInt(0xbbab80+at*4,0);backend.stop(at);
        if(readNativeInt(0x43dbdc)==at){writeNativeInt(0x43dbdc,-1);backend.closeStream();}backend.release(at);
    }
    private void playMidi(boolean notify,Pc1NativeSettings settings)throws IOException{
        if(preference(settings,Pc1NativeSettings.AudioField.BGM_ENABLED)==0)return;
        byte[] target=backend.midiPath(musicName(currentTrack));cstring(target);if(target.length>255)throw new IOException("PC1 MIDI path exceeds owned256-byte buffer");copyCString(mciPath,target);
        backend.mci(command(0x43de2c,prefix(mciPath)),false);backend.mci(command(notify?0x43de18:0x43de0c,prefix(mciPath)),notify);mciClosed=0;
    }
    private void stopMidi()throws IOException{
        if(mciClosed!=0)return;backend.mci(command(0x43de50,prefix(mciPath)),false);backend.mci(command(0x43de44,prefix(mciPath)),false);mciClosed=1;formatMidiWithoutArgument(0x43de40);
    }
    private byte[] musicName(int track)throws IOException{return catalog.musicName(track);}
    private int nativeByte(int address)throws IOException{return nativeMemory==null?readNativeByte(address):nativeMemory.readAddress(address);}
    private int muteByte(Pc1NativeSettings settings,int index)throws IOException{if(nativeMemory!=null)return nativeMemory.readAddress(Pc1NativeMemory.SETTINGS_BASE+Pc1NativeSettings.GLOBAL_OFFSET+index);if(settings==null)throw new IOException("PC1 audio settings owner missing");return settings.readByteBank(100,index);}
    private int nativeWord(int address)throws IOException{int v=0;for(int n=0;n<4;n++)v|=nativeByte(address+n)<<(n*8);return v;}
    private void requireCLocale()throws IOException{if(nativeMemory!=null&&nativeWord(Pc1CatalogMemory.CRT_LOCALE)!=0)throw new IOException("PC1 audio CRT non-C locale requires42534C owner");}
    /** Exact known-format subset of420661. Extra varargs and other CRT format
     *conversions require their actual stack/formatter owner, never formatting
     *the Java pointer/string differently. The native command locals are not
     *published until a complete command reaches mciSendStringA. */
    private byte[] command(int format,byte[] value)throws IOException{
        requireCLocale();ByteArrayOutputStream out=new ByteArrayOutputStream();boolean used=false;
        for(int n=0;n<1_048_576;n++){int c=nativeByte(format+n);if(c==0)return out.toByteArray();if(c!='%'){out.write(c);continue;}
            int conversion=nativeByte(format+(++n));if(conversion=='%')out.write('%');else if(conversion=='s'&&!used){out.write(value);used=true;}else throw new IOException("PC1 audio raw sprintf requires additional CRT/vararg owner at0x"+Integer.toHexString(format+n));
        }
        throw new IOException("PC1 audio command format exceeds known scan bound");
    }
    private void midiByte(int offset,int value)throws IOException{if(offset<mciPath.length)mciPath[offset]=(byte)value;else if(nativeMemory!=null)nativeMemory.writeAddress(0x43daac+offset,value);else throw new IOException("PC1 MIDI format reaches unowned neighboring memory");}
    private void formatMidiWithoutArgument(int format)throws IOException{
        requireCLocale();int at=0;for(int n=0;n<1_048_576;n++){int c=nativeByte(format+n);if(c==0){midiByte(at,0);return;}if(c=='%'){c=nativeByte(format+(++n));if(c!='%')throw new IOException("PC1 MIDI raw sprintf requires missing vararg owner");}midiByte(at++,c);}throw new IOException("PC1 MIDI format exceeds known scan bound");
    }
    public byte[] formatMidiCommand(int rawFormat)throws IOException{return command(rawFormat,prefix(mciPath));}
    private static void cstring(byte[] raw)throws IOException{if(raw==null)throw new IOException("PC1 raw audio string missing");for(byte b:raw)if(b==0)throw new IOException("PC1 raw audio prefix contains NUL");}
    private static void channel(int at)throws IOException{if(at<0||at>=CHANNELS)throw new IOException("PC1 audio channel reaches unowned neighboring memory:"+at);}
    private static byte[] prefix(byte[] raw)throws IOException{int end=0;while(end<raw.length&&raw[end]!=0)end++;if(end==raw.length)throw new IOException("PC1 audio string exceeds known backing");return Arrays.copyOf(raw,end);}
    private static void copyCString(byte[] target,byte[] raw){System.arraycopy(raw,0,target,0,raw.length);target[raw.length]=0;}
    private static int sourceByte(byte[] raw,int at)throws IOException{if(at==raw.length)return 0;if(at<0||at>raw.length)throw new IOException("PC1 audio comparator reaches beyond known source NUL");return raw[at]&255;}
    private boolean match(byte[] raw,int at,int text,int count)throws IOException{
        requireCLocale();for(int n=0;n<count;n++){int a=sourceByte(raw,at+n),b=nativeByte(text+n);if(a==0||b==0)return a==b;if(a>='A'&&a<='Z')a+=32;if(b>='A'&&b<='Z')b+=32;if(a!=b)return false;}return true;
    }
    private boolean forced(byte[] raw)throws IOException{for(int at=0;at<raw.length;at++)if(at==0||raw[at-1]=='\\')if(match(raw,at,0x43de6c,6)||match(raw,at,0x43de64,5)||match(raw,at,0x43de5c,4))return true;return false;}
    /** Raw41CAA0/41CA30 leaves for a known C-string prefix. They borrow the
     *same writable table/literals and canonical pointer targets as voice(). */
    public boolean forcedVoice(byte[] raw)throws IOException{cstring(raw);return forced(raw);}
    public int voiceCharacter(byte[] raw)throws IOException{cstring(raw);return character(raw);}
    private int character(byte[] raw)throws IOException{
        for(int at=0;at<raw.length;at++)if(raw[at]=='\\'){
            int table=0x43dbe8;boolean ended=false;for(int n=0;n<1_048_576;n++,table+=12){int id=nativeWord(table);if(id==-1){ended=true;break;}if(match(raw,at+1,nativeWord(table+4),3))return id;}if(!ended)throw new IOException("PC1 voice table passes known scan bound");
        }
        return 17;
    }
    public State state()throws IOException{if(initializing)throw new IOException("PC1 audio snapshot during physical native initialization");return new State(this);}
    public static Pc1Audio restore(Backend backend,Catalog catalog,State state)throws IOException{
        Pc1Audio audio=new Pc1Audio(backend,catalog);audio.restoreState(state);return audio;
    }
    /** Preserve already-bound canonical byte views. The independently staged
     *backend must validate/adopt first; no constructor/reset/import is called. */
    public void restoreState(State state)throws IOException{
        if(state==null)throw new IOException("PC1 audio control state missing");if(initializing)throw new IOException("PC1 audio restore during native initialization");
        if(state.pcmReadyRaw!=0&&!backend.pcmInitialized())throw new IOException("PC1 restored active PCM control has no initialized physical device");
        if(!backend.prepareRestoredState(this,state))throw new IOException("PC1 audio backend has not restored its PCM/stream/output/timer owners");State prior=new State(this);install(state);if(!backend.adoptRestoredState(state)){install(prior);throw new IOException("PC1 audio backend has not restored its PCM/stream/output/timer owners");}
    }
    private void install(State s)throws IOException{pcmReady=s.pcmReadyRaw;writeNativeInt(0xbc1d60,s.extra[0]);writeNativeInt(0xbc12cc,s.extra[1]);loaderChannel=s.extra[2];voiceCount=s.extra[3];for(int n=0;n<16;n++)writeNativeByte(0xbbaaf8+(n/4)*44+n%4,s.channelGaps[n]);for(int n=0;n<18;n++)writeNativeByte(0x43dbb0+n,s.primaryFormat[n]);for(int n=0;n<30;n++)writeNativeByte(n<26?0x43dbc2+n:0x43dbe4+n-26,s.waveNeighbors[n]);int pe=0;for(int[] range:LIVE_PE_RANGES)for(int n=0;n<range[1];n++)writeNativeByte(range[0]+n,s.livePe[pe++]);scriptMode=s.words[0];currentTrack=s.words[1];currentMode=s.words[2];pending=s.words[3];fade=s.words[4];fadeVolume=s.words[5];pendingTrack=s.words[6];pendingMode=s.words[7];soundFade=s.words[8];mciClosed=s.words[9];timerToken=s.words[10];timerInterval=s.words[11];refreshCount=s.words[12];writeNativeInt(0x43dbdc,s.words[13]);int[] bases={0xbbaafc,0xbb8550,0xbbab28,0xbbab54,0xbbab80,0xbc1d38};for(int n=0;n<bases.length;n++)for(int c=0;c<CHANNELS;c++)writeNativeInt(bases[n]+c*4,s.arrays[n][c]);for(int n=0;n<CHANNELS;n++)System.arraycopy(s.resources[n],0,resources[n],0,256);System.arraycopy(s.mciPath,0,mciPath,0,256);}

    /** Detached logical/PE byte projection. This does not serialize native audio buffers
     *or decoder clocks, and loading it does not issue any output/file calls. */
    public static final class State {
        public final boolean pcmReady;
        public final int pcmReadyRaw;
        private final int[] extra;private final byte[] channelGaps,primaryFormat,waveNeighbors,livePe;
        private final int[] words;
        private final int[][] arrays;
        private final byte[][] resources;
        private final byte[] mciPath;
        private State(Pc1Audio a)throws IOException{pcmReadyRaw=a.pcmReady;pcmReady=pcmReadyRaw!=0;extra=new int[]{a.readNativeInt(0xbc1d60),a.readNativeInt(0xbc12cc),a.loaderChannel,a.voiceCount};channelGaps=new byte[16];for(int n=0;n<16;n++)channelGaps[n]=(byte)a.readNativeByte(0xbbaaf8+(n/4)*44+n%4);primaryFormat=a.rawBytes(0x43dbb0,18);waveNeighbors=new byte[30];for(int n=0;n<30;n++)waveNeighbors[n]=(byte)a.readNativeByte(n<26?0x43dbc2+n:0x43dbe4+n-26);livePe=new byte[516];int offset=0;for(int[] range:LIVE_PE_RANGES)for(int n=0;n<range[1];n++)livePe[offset++]=(byte)a.readNativeByte(range[0]+n);words=new int[]{a.scriptMode,a.currentTrack,a.currentMode,a.pending,a.fade,a.fadeVolume,a.pendingTrack,a.pendingMode,a.soundFade,a.mciClosed,a.timerToken,a.timerInterval,a.refreshCount,a.readNativeInt(0x43dbdc)};int[] bases={0xbbaafc,0xbb8550,0xbbab28,0xbbab54,0xbbab80,0xbc1d38};arrays=new int[bases.length][CHANNELS];for(int n=0;n<bases.length;n++)for(int c=0;c<CHANNELS;c++)arrays[n][c]=a.readNativeInt(bases[n]+c*4);resources=new byte[CHANNELS][];for(int n=0;n<CHANNELS;n++)resources[n]=a.resources[n].clone();mciPath=a.mciPath.clone();}
        private State(DataInput in)throws IOException{if(in.readInt()!=FORMAT)throw new IOException("PC1 audio control state format");pcmReadyRaw=in.readInt();pcmReady=pcmReadyRaw!=0;extra=new int[4];for(int n=0;n<extra.length;n++)extra[n]=in.readInt();channelGaps=new byte[16];in.readFully(channelGaps);primaryFormat=new byte[18];in.readFully(primaryFormat);waveNeighbors=new byte[30];in.readFully(waveNeighbors);livePe=new byte[516];in.readFully(livePe);words=new int[14];for(int n=0;n<words.length;n++)words[n]=in.readInt();arrays=new int[6][CHANNELS];for(int[] array:arrays)for(int n=0;n<CHANNELS;n++)array[n]=in.readInt();resources=new byte[CHANNELS][256];for(byte[] raw:resources)in.readFully(raw);mciPath=new byte[256];in.readFully(mciPath);}
        public byte[] rawStreamingBytes(){return channelGaps.clone();}
        public int[] words(){return words.clone();}
        public int[] channel(int at)throws IOException{Pc1Audio.channel(at);int[] values=new int[arrays.length];for(int n=0;n<values.length;n++)values[n]=arrays[n][at];return values;}
        public byte[] rawResourceCell(int at)throws IOException{Pc1Audio.channel(at);return resources[at].clone();}
        public byte[] rawMidiCell(){return mciPath.clone();}
        public void save(DataOutput out)throws IOException{out.writeInt(FORMAT);out.writeInt(pcmReadyRaw);for(int v:extra)out.writeInt(v);out.write(channelGaps);out.write(primaryFormat);out.write(waveNeighbors);out.write(livePe);for(int word:words)out.writeInt(word);for(int[] array:arrays)for(int word:array)out.writeInt(word);for(byte[] raw:resources)out.write(raw);out.write(mciPath);}
        public static State load(DataInput in)throws IOException{return new State(in);}
    }
}
