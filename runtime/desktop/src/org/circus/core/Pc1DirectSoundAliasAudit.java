package org.circus.core;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Actual muted DS over self-authored PCM, signed/wrapped41CC10 callers.
 *No original EXE, game write, visible window or invented pointer DWORD. */
public final class Pc1DirectSoundAliasAudit {
    private static int checks;
    private interface Work {void run()throws IOException;}
    private static void check(boolean value,String why){checks++;if(!value)throw new AssertionError(why);}
    private static void reject(Work work,String contains)throws IOException{try{work.run();}catch(IOException error){check(error.getMessage().contains(contains),"Exact late fault: "+error.getMessage());return;}throw new AssertionError("Expected late fault "+contains);}
    private static byte[] raw(String value){return value.getBytes(StandardCharsets.US_ASCII);}
    private static void word(byte[] b,int at,int value){b[at]=(byte)value;b[at+1]=(byte)(value>>>8);}
    private static void dword(byte[] b,int at,int value){for(int k=0;k<4;k++)b[at+k]=(byte)(value>>>(k*8));}
    private static Pc1Audio.Catalog empty(){return new Pc1Audio.Catalog(){public int musicCount(){return 0;}public int soundCount(){return 0;}public Pc1Audio.Entry music(int n)throws IOException{throw new IOException("unused");}public Pc1Audio.Entry sound(int n)throws IOException{throw new IOException("unused");}};}
    public static void main(String[] args)throws Exception{
        if(args.length!=2)throw new IOException("Expected immutable DLL and owned stage28 output");Path dll=Paths.get(args[0]),out=Paths.get(args[1]).toAbsolutePath().normalize(),allowed=Paths.get("build/native-rewrite/pc1-audio-alias-stage28").toAbsolutePath().normalize();if(!out.startsWith(allowed))throw new IOException("Alias fixtures require own build directory");Files.createDirectories(out);
        final byte[] payload=new byte[4410];for(int n=0;n<payload.length;n++)payload[n]=(byte)(n*31);byte[] encoded=new byte[28+payload.length];System.arraycopy(raw("XPCM"),0,encoded,0,4);dword(encoded,4,payload.length);word(encoded,12,1);word(encoded,14,1);dword(encoded,16,22050);dword(encoded,20,44100);word(encoded,24,2);word(encoded,26,16);System.arraycopy(payload,0,encoded,28,payload.length);final Path source=out.resolve("self-authored.pcm");Files.write(source,encoded);
        Pc1DirectSound.Resources files=new Pc1DirectSound.Resources(){public Pc1DirectSound.Source open(byte[] name)throws IOException{return new Pc1DirectSound.Source(new Archive.Source(){public long size()throws IOException{return Files.size(source);}public byte[] read(long at,int count)throws IOException{byte[] all=Files.readAllBytes(source);Binary.range(at,count,all.length);return Arrays.copyOfRange(all,(int)at,(int)at+count);}},0,source,"signed/wrapped self-authored source");}public byte[] midiPath(byte[] name)throws IOException{throw new IOException("unused");}};
        byte[] preferences=Pc1NativeSettings.currentChsDefaults().nativeBytes();dword(preferences,0x3c,0);final Pc1NativeSettings settings=Pc1NativeSettings.parse(preferences);final byte[] settingsBytes=settings.nativeBytes();
        Pc1NativeMemory bus=new Pc1NativeMemory(Pc1NativeFrame.zeroBss(),Pc1CodeBuffer.zeroBss(),new Pc1NativeMemory.SettingsBytes(){public int read(int at){return settingsBytes[at]&255;}public void write(int at,int value){settingsBytes[at]=(byte)value;}});new Pc1CatalogMemory(bus);new Pc1LayoutMemory(bus);
        try(Pc1DirectSound device=new Pc1DirectSound(dll,0,files)){
            final Pc1Audio audio=new Pc1Audio(device,empty());audio.bindNativeMemory(bus);device.bindNativeMemory(bus);audio.adoptAlreadyInitialized();Pc1DirectSoundAuditFixtures.synthetic(bus);final List<Integer> messages=new ArrayList<Integer>();device.bindNativeMessages(address->{messages.add(address);});
            reject(()->audio.mixVoice(raw("A"),-4,settings),"NULL dereference SetVolume cell0xbc12cc");
            check(audio.loaded(0)&&audio.byteLimit(0)==payload.length,"Automatic slot0 remains loaded after original requested-1 volume fault");check(Arrays.equals(payload,device.bufferBytes(0)),"Actual selected slot0 received full original loader PCM");check(audio.readNativeInt(0xbc1d04)==0&&audio.readNativeInt(0xbc12cc)==0,"Chosen loader index0 and original null requested pointer cell stay distinct");check(device.status(0)==0,"Late SetVolume fault never fabricates playback");
            audio.startMix(-4);check(device.status(0)==0,"StartMix requested-1 reads decodedLimit0 and skips independently of chosen loaded0");
            audio.writeNativeInt(0xbbaaf8,1);reject(()->audio.startMix(-4),"NULL dereference SetCurrentPosition cell0xbc12cc");audio.writeNativeInt(0xbbaaf8,0);
            int wrapped=-0x40000000,slot=wrapped-3;audio.mixVoice(raw("WRAPPED"),slot,settings);check(audio.readNativeInt(0xbc1d04)==wrapped&&audio.loaded(0),"Signed multiply wraps all mapped channel arrays back to0 without normalizing the scalar index");check(Arrays.equals(payload,device.bufferBytes(0))&&device.query(0,2,0)[1]==-10000,"Wrapped actual slot remains byte exact and muted");audio.startMix(slot);check((device.status(0)&1)!=0,"Wrapped negative index invokes actual slot0 Position/Play");device.stop(0);
            audio.writeNativeInt(0xbbaaf4,0);audio.startMix(-5);audio.writeNativeInt(0xbbaaf4,1);reject(()->audio.startMix(-5),"NULL dereference SetCurrentPosition cell0xbc12c8");audio.writeNativeInt(0xbbaaf4,0);
            for(int n=0;n<10;n++)audio.writeNativeInt(0xbbaafc+n*4,1);audio.mixVoice(raw("FULL"),-4,settings);check(messages.equals(Collections.singletonList(0x43dde8))&&audio.readNativeInt(0xbc1d04)==-1,"Exhausted auto scan reaches required407020 with already-published index-1");for(int n=0;n<10;n++)audio.writeNativeInt(0xbbaafc+n*4,n==0?1:0);
            audio.writeNativeInt(0xbc12cc,1);audio.writeNativeInt(0xbc1d04,2);reject(()->audio.mixVoice(raw("CACHED"),-4,settings),"requires actual COM pointer owner cell0xbc12cc raw0x1");check(audio.loaded(2)&&audio.byteLimit(2)==payload.length&&audio.readNativeInt(0xbc1d04)==2,"Nonzero loaderFlag retains its prior chosen index2, no automatic scan");check(audio.state().rawResourceCell(2)[0]==(byte)255&&audio.readNativeInt(0xbbab54+8)==0,"Cached branch preserves resource string and stored frequency, restores actual driver parameters");check(device.query(2,3,0)[1]==22050,"Native frequency0 means actual DirectSound original sample rate");audio.writeNativeInt(0xbc12cc,0);
            audio.closeNative(device.shutdown());
        }
        System.out.println("Pc1DirectSoundAliasAudit PASS "+checks+"; actual muted DS, self-authored source, signed/wrapped aliases and exact late pointer faults; no original EXE");
    }
}
