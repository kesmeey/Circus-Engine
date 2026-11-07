package org.circus.core;
import java.io.*;
import java.lang.reflect.Proxy;
import java.util.*;

/** Cross-owner aliases and fallback order; no original file or OS is written. */
public final class Pc1OwnerIntegrationTests {
    private static int checks;
    private static void check(boolean yes,String why){checks++;if(!yes)throw new AssertionError(why);}
    private static byte[] save(PcHost host)throws IOException{ByteArrayOutputStream out=new ByteArrayOutputStream();host.save(new DataOutputStream(out));return out.toByteArray();}
    private static byte[] save(Pc1Audio.State state)throws IOException{ByteArrayOutputStream out=new ByteArrayOutputStream();state.save(new DataOutputStream(out));return out.toByteArray();}
    private static void settings()throws Exception{
        Pc1RenderContextTests.Fixture f=new Pc1RenderContextTests.Fixture(new Pc1RenderContextTests.Font(),null);PcHost host=f.host;Pc1GraphicsMemory bus=host.pc1.graphicsMemory();int at=0x42a078+Pc1NativeSettings.offset99(12);
        host.bindNativeSettingsDefaults(Pc1NativeSettings.currentChsDefaults(),Pc1NativeSettings.CURRENT_CHS_PE_SHA256);
        host.bindNativeSettingsIo(new Pc1NativeSettings.NativeIo(){public int open(String p,int flags,int mode)throws IOException{bus.writeInt(at,77);return 7;}public int read(int fd,byte[] bytes,int requested)throws IOException{bus.writeInt(at,88);return 0;}public int close(int fd)throws IOException{bus.writeInt(at,99);return -1;}public int write(int fd,byte[] bytes,int requested)throws IOException{throw new IOException("unexpected test write");}},"owned/system.dat");
        check(host.reloadNativeSettingsFromBoundIo().defaulted&&host.read(99,12)==99,"fallback observes live default template AFTER read and ignored close result");
        host.bindNativeSettingsIo(new Pc1NativeSettings.NativeIo(){public int open(String p,int flags,int mode)throws IOException{bus.writeInt(at,123);return -1;}public int read(int fd,byte[] b,int n)throws IOException{throw new IOException("unused");}public int close(int fd)throws IOException{throw new IOException("unused");}public int write(int fd,byte[] b,int n)throws IOException{throw new IOException("unused");}},"owned/system.dat");
        check(host.reloadNativeSettingsFromBoundIo().settings.read99(12)==123,"failed-open fallback reads current PE bytes rather than a bound template clone");
        Pc1CatalogMemory catalog=host.pc1.catalogMemory();catalog.dword(Pc1CatalogMemory.SCENARIO_COUNT,1);int label=catalog.address(Pc1CatalogMemory.Table.SCENARIO,-2,128);catalog.write(label,'N');catalog.write(label+1,0);
        Pc1TextExpander.Context text=host.pc1.nativeCatalogTextContext();check(text.catalogLabel(true,-2)[0]=='N',"negative raw label addresses borrow the actual system table owner");
        catalog.dword(Pc1CatalogMemory.MUSIC_COUNT,0x81234567);bus.writeInt(Pc1CrxMemory.HEADER,0x12345678);byte[] before=save(host);bus.writeInt(at,0);catalog.write(label,0);catalog.dword(Pc1CatalogMemory.MUSIC_COUNT,0);bus.writeInt(Pc1CrxMemory.HEADER,0);
        host.load(new DataInputStream(new ByteArrayInputStream(before)));check(bus.readInt(at)==123&&catalog.dword(Pc1CatalogMemory.MUSIC_COUNT)==0x81234567&&text.catalogLabel(true,-2)[0]=='N',"whole Runtime restore keeps PE/catalog backing and existing borrowed adapters");
        check(bus.readInt(Pc1CrxMemory.HEADER)==0x12345678,"PE state restore leaves its CRX hole to the existing independent owner");check(Arrays.equals(before,save(host)),"current host snapshot roundtrip preserves every raw owner once");
    }
    private static void audio()throws Exception{
        List<String> calls=new ArrayList<>();Pc1Audio.Backend backend=(Pc1Audio.Backend)Proxy.newProxyInstance(Pc1Audio.Backend.class.getClassLoader(),new Class<?>[]{Pc1Audio.Backend.class},(p,m,args)->{switch(m.getName()){case "pcmInitialized":return false;case "bindControl":return null;case "prepareRestoredState":return true;case "adoptRestoredState":return true;case "killTimer":calls.add("kill:"+args[0]);return 0;default:throw new IOException("unexpected fixture audio leaf "+m.getName());}});
        Pc1CatalogMemory catalog=new Pc1RenderContextTests.Fixture(new Pc1RenderContextTests.Font(),null).host.pc1.catalogMemory();Pc1Audio audio=new Pc1Audio(backend,catalog.audioCatalog());
        Pc1RenderContextTests.Fixture f=new Pc1RenderContextTests.Fixture(new Pc1RenderContextTests.Font(),null);Pc1GraphicsMemory bus=f.host.pc1.graphicsMemory();audio.bindNativeMemory(f.host.pc1.nativeMemory());bus.writeInt(0x94c674,0);bus.writeInt(0x94c6ac,0);
        check(!audio.onTimer2(0,f.host.nativeSettings())&&calls.isEmpty(),"provider event filter ignores unregistered token0");audio.handleNativeTimer2(f.host.nativeSettings());check(calls.equals(Arrays.asList("kill:0")),"already classified native timer2 branch must still call KillTimer(0)");
        bus.writeInt(0x94c674,0x81234567);bus.writeInt(0x94c6ac,Integer.MIN_VALUE);check(audio.timerToken()==0x81234567&&audio.activationRefreshes()==Integer.MIN_VALUE&&audio.timerInterval()==0,"raw byte views do not invent OS registration or clamp refresh count");
        byte[] raw=save(audio.state());Pc1Audio.State decoded=Pc1Audio.State.load(new DataInputStream(new ByteArrayInputStream(raw)));check(Arrays.equals(raw,save(decoded)),"logical audio codec retains arbitrary native timer/refresh DWORDs");
        Pc1Audio rebound=Pc1Audio.restore(backend,catalog.audioCatalog(),decoded);check(rebound.timerToken()==0x81234567&&rebound.activationRefreshes()==Integer.MIN_VALUE,"explicit backend adoption preserves canonical raw control fields");
    }
    public static int run()throws Exception{checks=0;settings();audio();return checks;}
    public static void main(String[] args)throws Exception{System.out.println("PASS "+run()+" shared owner integration checks");}
}
