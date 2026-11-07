package org.circus.core;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.util.*;

/**41BE20/41D0C0 path selection. Borrows live roots and writable literals;
 *no guessed installation directory, archive entry span, or decoded text. */
public final class Pc1AudioResources implements Pc1DirectSound.Resources {
    public static final int EMPTY=0x94bc30,VOICE_DAT=0x43dd88,PCM=0x43dd94,WAV=0x43de9c,VOICE_PREFIX=0x43de04,MIDI=0x43de38;
    public interface RawFiles {
        /** Must return a lazy whole-file source without stat/open/probe here.
         *Actual DS loader opens the raw A-path before querying this backing. */
        Archive.Source source(byte[] rawNativePath)throws IOException;
    }
    /** Explicit adapter for the exact-ACP JavaPath subset. Raw DS opens still
     *use A-bytes; this provider is lazy validation/index backing, not path
     *selection. Unsupported raw byte round trips fail instead of substitution. */
    public static RawFiles exactAcpFiles(final Pc1WindowsPaths windows)throws IOException{
        if(windows==null)throw new IOException("PC1 exact ACP file owner missing");
        return raw->{if(raw==null)throw new IOException("PC1 raw file path missing");final byte[] path=raw.clone();return new Archive.Source(){
            public long size()throws IOException{return Files.size(windows.exactPath(path));}
            public byte[] read(long at,int requested)throws IOException{try(Archive.Reader reader=openReader()){return reader.read(at,requested);}}
            public Archive.Reader openReader()throws IOException{
                final FileChannel channel=FileChannel.open(windows.exactPath(path),StandardOpenOption.READ);
                return new Archive.Reader(){public long size()throws IOException{return channel.size();}public byte[] read(long at,int requested)throws IOException{if(at<0||requested<0)throw new IOException("PC1 readonly file range");int count=(int)Math.min(requested,Math.max(0,channel.size()-at));ByteBuffer bytes=ByteBuffer.allocate(count);while(bytes.hasRemaining()){int n=channel.read(bytes,at+bytes.position());if(n<0)break;}return Arrays.copyOf(bytes.array(),bytes.position());}public void close()throws IOException{channel.close();}};
            }
        };};
    }
    public interface Locale {int nativeLocaleFlag()throws IOException;default int lowerCase42534C(int unsignedByte)throws IOException{throw new IOException("PC1 raw resource non-C locale42534C converter required");}}
    private final Pc1NativePaths.Memory globals;
    private final Pc1ResourcePaths paths;
    private final Pc1WindowsPaths windows;
    private final RawFiles files;
    public interface IndexOwner {Pc1VoiceIndex current()throws IOException;}
    private final IndexOwner voice;
    private final Locale locale;
    public Pc1AudioResources(Pc1NativePaths.Memory borrowedGlobals,Pc1WindowsPaths windows,RawFiles files,Pc1VoiceIndex voice,Locale locale)throws IOException{
        if(borrowedGlobals==null||windows==null||files==null||locale==null)throw new IOException("PC1 audio resource path/file/CRT owners required");globals=borrowedGlobals;this.windows=windows;paths=new Pc1ResourcePaths(globals,windows);this.files=files;this.voice=()->voice;this.locale=locale;
    }
    private Pc1AudioResources(Pc1NativePaths.Memory globals,Pc1WindowsPaths windows,RawFiles files,IndexOwner voice,Locale locale,boolean borrowed)throws IOException{
        if(globals==null||windows==null||files==null||voice==null||locale==null)throw new IOException("PC1 audio resource live owners required");this.globals=globals;this.windows=windows;paths=new Pc1ResourcePaths(globals,windows);this.files=files;this.voice=voice;this.locale=locale;
    }
    /** Native41B7F0 initializes the index only after successful device setup
     *and403B40. The caller owns that lifetime; this adapter never preloads it. */
    public static Pc1AudioResources borrowingIndex(Pc1NativePaths.Memory globals,Pc1WindowsPaths windows,RawFiles files,IndexOwner voice,Locale locale)throws IOException{return new Pc1AudioResources(globals,windows,files,voice,locale,true);}
    private byte[] literal(int address)throws IOException{ByteArrayOutputStream out=new ByteArrayOutputStream();for(int n=0;n<1024*1024;n++){int value=globals.read(address+n)&255;if(value==0)return out.toByteArray();out.write(value);}throw new IOException("PC1 audio literal leaves known C source");}
    /** Only explicit provider startup uses this path; constructor does not
     *load the voice table or implicitly turn a missing table into an index. */
    public byte[] voiceIndexPath()throws IOException{return paths.asset(literal(EMPTY),literal(VOICE_DAT),literal(EMPTY),256).rawPath();}
    private Pc1DirectSound.Source source(byte[] path,long offset)throws IOException{return new Pc1DirectSound.Source(files.source(path.clone()),offset,path,"PC1-A:"+Base64.getEncoder().encodeToString(path));}
    private byte[] extension(byte[] original,byte[] extension)throws IOException{
        final byte[] scratch=new byte[260];if(original.length+1>scratch.length)throw new IOException("PC1 41D0C0 source copy exceeds original260byte scratch");System.arraycopy(original,0,scratch,0,original.length);
        Pc1NativePaths.Memory memory=new Pc1NativePaths.Memory(){public int read(int address)throws IOException{if(address>=0&&address<scratch.length)return scratch[address]&255;int at=address-4096;if(at>=0&&at<=extension.length)return at==extension.length?0:extension[at]&255;throw new IOException("PC1 audio extension leaves caller/literal owner");}public void write(int address,int value)throws IOException{if(address<0||address>=scratch.length)throw new IOException("PC1 audio extension writes beyond original260byte scratch");scratch[address]=(byte)value;}};
        Pc1NativePaths.forceExtension(memory,0,4096);int end=0;while(end<scratch.length&&scratch[end]!=0)end++;if(end==scratch.length)throw new IOException("PC1 audio path has no owned terminator");return Arrays.copyOf(scratch,end);
    }
    public Pc1DirectSound.Source open(byte[] rawName)throws IOException{
        if(rawName==null)throw new IOException("PC1 raw audio name missing");int length=0;while(length<rawName.length&&rawName[length]!=0)length++;byte[] raw=Arrays.copyOf(rawName,length),empty=literal(EMPTY);long offset=0;byte[] path;
        if(Pc1VoiceIndex.prefix(raw,literal(VOICE_PREFIX),6,locale.nativeLocaleFlag())){
            Pc1VoiceIndex index=voice.current();if(index!=null)offset=index.lookup(raw,literal(PCM),locale.nativeLocaleFlag());
            path=offset!=0?paths.asset(empty,literal(VOICE_DAT),empty,256).rawPath():paths.asset(empty,raw,empty,256).rawPath();
        }else path=paths.asset(empty,raw,literal(PCM),256).rawPath();
        if(offset!=0)return source(path,offset);
        // Neither source construction nor the readonly backing provider may
        // select by attributes. 41D0C0 falls through only after actual open.
        final byte[] pcmPath=extension(path,literal(PCM));return source(pcmPath,0).withLazyOpenFallback(()->source(extension(pcmPath,literal(WAV)),0));
    }
    /**The actual41BE20 pointer route borrows the complete canonical bus.
     *NativeName offsets may wrap to roots/literals, never a copied source.
     *The stack-local resolved path becomes independent only where native
     *40E5A0/41D0C0 has actually copied it. */
    @Override public Pc1DirectSound.Source openRaw(final Pc1Audio.NativeName name)throws IOException{return openSource(name);}
    private static Pc1ByteSource ownedCString(final byte[] bytes)throws IOException{if(bytes==null)throw new IOException("PC1 copied local Cstring missing");return n->{if(n<0||n>bytes.length)throw new IOException("PC1 copied local source extent");return n==bytes.length?0:bytes[n]&255;};}
    private static byte[] cstring(Pc1ByteSource source)throws IOException{ByteArrayOutputStream b=new ByteArrayOutputStream();for(int n=0;n<1024*1024;n++){int v=source.unsignedByte(n)&255;if(v==0)return b.toByteArray();b.write(v);}throw new IOException("PC1 audio local Cstring scan bound");}
    private byte[] asset(Pc1ByteSource category,Pc1ByteSource name,Pc1ByteSource extension)throws IOException{return paths.assetTyped(category,name,extension,256).rawPath();}
    private byte[] extension(byte[] original,Pc1ByteSource extension)throws IOException{Pc1AudioScratch local=new Pc1AudioScratch(260);Pc1NativePaths.copy(local,ownedCString(original));Pc1NativePaths.forceExtension(globals,local,extension);return local.cstring();}
    /**Source may be a real canonical pointer or the actual Frame.local. It is
     *never remapped to a coordinate overlay or prefetched complete string. */
    @Override public Pc1DirectSound.Source openSource(final Pc1ByteSource name)throws IOException{
        if(name==null)throw new IOException("PC1 typed audio name missing");Pc1ByteSource empty=Pc1NativePaths.pointer(globals,EMPTY),pcm=Pc1NativePaths.pointer(globals,PCM);int nativeLocale=locale.nativeLocaleFlag();long offset=0;byte[] path;
        if(Pc1VoiceIndex.prefix(name,Pc1NativePaths.pointer(globals,VOICE_PREFIX),6,nativeLocale,locale::lowerCase42534C)){
            Pc1VoiceIndex index=voice.current();if(index!=null)offset=index.lookupSource(globals,name,pcm,locale.nativeLocaleFlag());
            path=offset!=0?asset(empty,Pc1NativePaths.pointer(globals,VOICE_DAT),empty):asset(empty,name,empty);
        }else path=asset(empty,name,pcm);
        if(offset!=0)return source(path,offset);
        final byte[] pcmPath=extension(path,pcm);return source(pcmPath,0).withLazyOpenFallback(()->source(extension(pcmPath,Pc1NativePaths.pointer(globals,WAV)),0));
    }
    /**Snapshot staging reopens the recorded native A-path. Re-expanding the
     *current source/string/roots would invent new resource selection. */
    @Override public Pc1DirectSound.Source reopenNative(byte[] path,long offset,String identity)throws IOException{
        Pc1DirectSound.Source reopened=source(path,offset);if(!reopened.identity.equals(identity))throw new IOException("PC1 restored raw path identity differs");return reopened;
    }
    public byte[] midiPath(byte[] rawMusicName)throws IOException{return paths.asset(literal(MIDI),rawMusicName,literal(EMPTY),256).rawPath();}
}
