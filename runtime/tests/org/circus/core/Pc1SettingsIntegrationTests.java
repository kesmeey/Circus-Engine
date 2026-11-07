package org.circus.core;

import java.io.*;
import java.nio.file.*;
import java.util.*;

/** Lossless PC1 settings owner/host boundaries. No original file is written. */
public final class Pc1SettingsIntegrationTests {
    private static int count;
    private static void check(boolean value,String why){count++;if(!value)throw new AssertionError(why);}
    private interface Work {void run()throws Exception;}
    private static void reject(Work work,String text)throws Exception{boolean failed=false;try{work.run();}catch(IOException error){failed=String.valueOf(error.getMessage()).contains(text);}check(failed,"Reject "+text);}
    private static final class Fixture {
        final Map<String,byte[]> files=new HashMap<>();
        int sourceReads;
        Fixture(){files.put("AdvData/DAT/NAMES.DAT",new byte[20]);}
        PcHost host()throws IOException{return host("dcpc-2063");}
        PcHost host(String profile)throws IOException{
            GameResources resources=new GameResources(new GameResources.Storage(){
                public Set<String> paths(){return files.keySet();}
                private byte[] data(String path)throws IOException{byte[] bytes=files.get(path);if(bytes==null)throw new FileNotFoundException(path);return bytes;}
                public byte[] read(String path,int limit)throws IOException{byte[] bytes=data(path);if(bytes.length>limit)throw new IOException("Fixture limit");return bytes.clone();}
                public Archive.Source source(String path)throws IOException{byte[] bytes=data(path);return new Archive.Source(){
                    public long size(){return bytes.length;}
                    public byte[] read(long at,int length)throws IOException{sourceReads++;Binary.range(at,length,bytes.length);return Arrays.copyOfRange(bytes,(int)at,(int)at+length);}
                };}
            },"@none");
            return new PcHost(new CircusResources(resources,Profiles.get(profile)),TextCodec.identity().withEncoding("GBK"));
        }
    }
    private static PcHost seed(Fixture f,byte[] image)throws IOException{PcHost h=f.host();h.loadNativeSettings(image);return h;}
    private static byte[] image()throws IOException{
        byte[] bytes=new byte[Pc1NativeSettings.SIZE+17];for(int n=0;n<bytes.length;n++)bytes[n]=(byte)(n*37+19);
        return Pc1NativeSettings.parse(bytes).withNameCString(Pc1NativeSettings.Name.FAMILY,new byte[]{'F'}).withNameCString(Pc1NativeSettings.Name.GIVEN,new byte[]{'G'}).originalBytes();
    }
    private static final class Io implements Pc1NativeSettings.NativeIo {
        final List<String> events=new ArrayList<>();byte[] source,written;int openResult=7,readResult=Integer.MAX_VALUE,writeResult=Integer.MAX_VALUE,closeResult;
        public int open(String path,int flags,int mode){events.add("open:"+path+":"+Integer.toHexString(flags)+":"+Integer.toHexString(mode));return openResult;}
        public int read(int descriptor,byte[] target,int requested){events.add("read:"+requested);int n=Math.min(requested,source==null?0:source.length);if(n>0)System.arraycopy(source,0,target,0,n);return readResult==Integer.MAX_VALUE?n:readResult;}
        public int write(int descriptor,byte[] bytes,int requested){events.add("write:"+requested);written=Arrays.copyOf(bytes,requested);return writeResult==Integer.MAX_VALUE?requested:writeResult;}
        public int close(int descriptor){events.add("close");return closeResult;}
    }
    private static byte[] persistent(PcHost h)throws IOException{ByteArrayOutputStream bytes=new ByteArrayOutputStream();h.savePersistent(new DataOutputStream(bytes));return bytes.toByteArray();}
    private static byte[] snapshot(PcHost h)throws IOException{ByteArrayOutputStream bytes=new ByteArrayOutputStream();h.save(new DataOutputStream(bytes));return bytes.toByteArray();}
    private static void routes()throws Exception{
        Fixture f=new Fixture();byte[] initial=image();PcHost h=seed(f,initial);Io io=new Io();h.bindNativeSettingsIo(io,"account/system.dat");
        for(int index:Pc1NativeSettings.indices99())check(h.read(99,index)==Pc1NativeSettings.parse(initial).read99(index),"15 persistent99 reads come from raw memory:"+index);
        h.registers.put(12,777);check(h.read(99,12)!=777,"Old register cannot override persistent DWORD");
        for(int index:new int[]{Integer.MIN_VALUE,-1,1,23,25,26,27,244,245,246,255,256,Integer.MAX_VALUE}){
            int prior=h.read(99,index);h.write(99,index,91);check(h.read(99,index)==prior&&prior==0,"Unmapped99 getter0/setter no effect:"+index);
        }
        h.listOffset(12);h.write(99,246,99);check(h.listOffset()==12,"Private list pager is not a script register");
        check(h.signedNativeIndex(90)&&h.signedNativeIndex(97)&&h.signedNativeIndex(98)&&h.signedNativeIndex(99)&&h.signedNativeIndex(100),"Native signed index dispatch exposes every relevant special bank");
        Variables vars=new Variables(h,Variables.Model.PC_BYTE);check(vars.read(99,-1)==0&&vars.read(97,-Pc1NativeSettings.CG_OFFSET)==(initial[0]&255),"Variables preserves signed special indices through the real host dispatch");
        check(h.read(97,-Pc1NativeSettings.CG_OFFSET)==(initial[0]&255),"CG negative index aliases preference bytes");
        check(h.read(98,-Pc1NativeSettings.SCENE_OFFSET)==(initial[0]&255),"Scene negative index aliases preference bytes");
        h.write(97,-Pc1NativeSettings.CG_OFFSET,0x1ab);check(h.nativeSettings().rawBytes(0,1)[0]==(byte)0xab&&io.events.isEmpty(),"CG alias stores low8 without system write");
        h.write(100,-Pc1NativeSettings.GLOBAL_OFFSET,0x1cd);check(h.nativeSettings().rawBytes(0,1)[0]==(byte)0xcd&&io.events.equals(Arrays.asList("open:account/system.dat:8301:180","write:1748","close")),"Global negative alias preserves store-before-write and native flags");
        io.events.clear();h.write(100,5,0x123);h.write(100,5,0x123);check(io.events.size()==6&&h.read(100,5)==0x23,"Repeated same-value bank100 still calls writer twice");
        check(Arrays.equals(io.written,h.nativeSettings().nativeBytes())&&io.written.length==1748,"Writer emits exact current native region, excluding archival trailing");
        io.events.clear();int address=Pc1NativeMemory.SETTINGS_BASE+Pc1NativeSettings.GLOBAL_OFFSET+5;h.pc1.nativeMemory().writeAddress(address,0x1fe);
        check(h.read(100,5)==254&&io.events.isEmpty(),"Addressed settings alias is raw memory, not the bank100 setter");
        int bank90Alias=address-(Pc1FrameLayout.LIVE_BASE+Pc1FrameLayout.BANK90_OFFSET);h.write(90,bank90Alias,0x1a4);check(h.read(90,bank90Alias)==164&&h.read(100,5)==164&&io.events.isEmpty(),"Bank90 signed alias uses shared settings view, without recursion/file effect");
        h.write(90,0,9);h.pc1.codeBuffer().writeU8(12,8);check(h.nativeByteMemory()==h.pc1.nativeMemory()&&h.nativeCodeBuffer()==h.pc1.codeBuffer(),"VM hooks expose the same addressed owners");
        reject(()->h.read(97,-Pc1NativeSettings.CG_OFFSET-1),"owned1748");check(h.read(94,-1)==0&&h.read(95,0)==0,"Unknown special banks retain native default0");
    }
    private static void effectOrder()throws Exception{
        Fixture f=new Fixture();PcHost h=seed(f,Pc1NativeSettings.currentChsDefaults().nativeBytes());
        reject(()->h.write(99,7,31),"audio effect");check(h.read(99,7)==31,"Strict missing audio adapter does not undo the native preceding DWORD store");
        List<String> effects=new ArrayList<>();h.bindNativeSettingsAudioEffects((effect,settings)->{check(settings.read99(14)==h.read(99,14),"Callback sees published owner");effects.add(effect.kind+Arrays.toString(effect.arguments()));});
        h.logicalMusic(-1);h.write(99,14,1);check(effects.isEmpty(),"Enable with logical music -1 has no audio/timer call");
        h.write(99,14,0);check(effects.equals(Arrays.asList("STOP_MUSIC_41C650[0]","ARM_AUDIO_TIMER_4070E0[]")),"Disable stores then stops and arms native timer in order");
        effects.clear();h.logicalMusic(17);h.write(99,14,2);check(effects.equals(Arrays.asList("REQUEST_MUSIC_41C4B0[17, 0]","ARM_AUDIO_TIMER_4070E0[]")),"Re-enable uses logical request and external mode, not the portable track field");
        effects.clear();h.write(99,7,-7);check(effects.equals(Arrays.asList("REFRESH_VOLUMES_41CD00[]"))&&h.read(99,7)==-7,"Volume rawDWORD is not clamped and refresh is not swallowed");
        effects.clear();for(int index:new int[]{5,6,8,9,10,11,12,13,15,16,17,18,20})h.write(99,index,Integer.MIN_VALUE+index);check(effects.isEmpty(),"Other persistent setters only store their native DWORDs");
        Io io=new Io();io.openResult=-1;h.bindNativeSettingsIo(io,"runtime/system.dat");h.write(100,1,257);check(h.read(100,1)==1&&h.nativeSettingsWriteResult().nativeResult==-1&&io.events.size()==1,"File open failure is visible and preserves the completed byte store");
        io.events.clear();io.openResult=7;io.writeResult=-1;io.closeResult=-1;h.write(100,1,258);check(h.read(100,1)==2&&h.nativeSettingsWriteResult().nativeResult==0&&!h.nativeSettingsWriteResult().fullCountAndCloseSucceeded&&io.events.size()==3,"Native ignores write/close results but diagnostics retain failure");
    }
    private static void initialization()throws Exception{
        Fixture missing=new Fixture();PcHost unbound=missing.host();reject(()->unbound.read(99,12),"default_unbound");
        reject(()->unbound.bindNativeSettingsDefaults(Pc1NativeSettings.currentChsDefaults(),"wrong executable"),"PE identity");
        unbound.bindNativeSettingsDefaults(Pc1NativeSettings.currentChsDefaults(),Pc1NativeSettings.CURRENT_CHS_PE_SHA256);check(unbound.read(99,12)==1&&unbound.nativeSettingsReadResult().defaulted,"Explicit verified CHS binding admits fallback");
        byte[] initial=image();Fixture readable=new Fixture();readable.files.put("system.dat",initial);PcHost h=readable.host();check(h.read(99,12)==Pc1NativeSettings.parse(initial).read99(12)&&h.nativeSettings().originalLength()==1748,"Native read admits first1748 even if original file has trailing");
        check(readable.sourceReads==1&&!h.nativeSettingsReadResult().defaulted&&h.nativeSettingsSource().equals("system.dat"),"Original source is read once through its read-only stream");
        PcHost busFirst=readable.host();int readsBeforeRaw=readable.sourceReads;check(busFirst.pc1.nativeMemory().readAddress(Pc1NativeMemory.SETTINGS_BASE)==0&&readable.sourceReads==readsBeforeRaw,"Raw shared RAM access preserves BSS without implicit file admission");busFirst.read(99,12);check(busFirst.pc1.nativeMemory().readAddress(Pc1NativeMemory.SETTINGS_BASE)==(initial[0]&255)&&readable.sourceReads==readsBeforeRaw+1,"Explicit legacy settings admission publishes to the already bound RAM view");int sourceReads=readable.sourceReads;
        Io io=new Io();io.source=new byte[1747];h.bindNativeSettingsIo(io,"runtime/system.dat");byte[] prior=h.nativeSettings().originalBytes();reject(h::reloadNativeSettingsFromBoundIo,"default_unbound");check(Arrays.equals(prior,h.nativeSettings().originalBytes())&&readable.sourceReads==sourceReads,"Short owned file never falls through to original or publishes unverified defaults");
        check(h.nativeSettingsReadResult().defaulted&&h.nativeSettingsSource().equals("runtime/system.dat"),"Rejected unbound fallback still retains the actual file-read diagnostic");
        h.bindNativeSettingsDefaults(Pc1NativeSettings.currentChsDefaults(),Pc1NativeSettings.CURRENT_CHS_PE_SHA256);io.events.clear();check(h.reloadNativeSettingsFromBoundIo().defaulted&&h.read(99,12)==1&&io.events.equals(Arrays.asList("open:runtime/system.dat:8000:0","read:1748","close")),"Bound fallback uses exact native read-close-admit order");
        io.source=initial;io.closeResult=-1;check(!h.reloadNativeSettingsFromBoundIo().defaulted&&h.nativeSettings().originalLength()==1748,"Full read remains admitted despite native close error");
        Fixture multiple=new Fixture();multiple.files.put("system.dat",new byte[1748]);multiple.files.put("SaveData/system.dat",new byte[1748]);reject(()->multiple.host().nativeSettings(),"Multiple PC1");
    }
    private static void codecsAndNames()throws Exception{
        Fixture f=new Fixture();byte[] initial=image();PcHost h=seed(f,initial);h.readCheckpoints.add("portable/read-adapter");
        byte[] given=h.nativeGivenName();h.givenName="UNRELATED CACHE";check(Arrays.equals(given,h.nativeGivenName()),"Raw native name does not ingest Unicode cache changes");
        h.setNativeName(Pc1NativeSettings.Name.GIVEN,new byte[]{(byte)0x81,0x40});check(Arrays.equals(h.nativeGivenName(),new byte[]{(byte)0x81,0x40}),"Explicit raw name setter preserves encoding bytes");
        h.setNativeNames("FAMILY","GIVEN");check(Arrays.equals(h.nativeFamilyName(),new byte[]{'F','A','M','I','L','Y'}),"Platform String setter writes raw owner explicitly");
        PcHost mapped=new PcHost(h.resources,new TextCodec("A","Z","GBK"));mapped.loadNativeSettings(Pc1NativeSettings.currentChsDefaults().withNameCString(Pc1NativeSettings.Name.FAMILY,new byte[]{'A'}).nativeBytes());mapped.setNativeNames(mapped.familyName,mapped.givenName);check(Arrays.equals(mapped.nativeFamilyName(),new byte[]{'A'}),"Name editor logical strings do not bake font substitution back into raw names");
        reject(()->h.setNativeNames("\ud800","x"),"cannot be represented");reject(()->h.setNativeName(Pc1NativeSettings.Name.GIVEN,new byte[]{1,0,2}),"contains NUL");
        h.setNativeName(Pc1NativeSettings.Name.GIVEN,new byte[]{(byte)0xff});byte[] before=h.nativeSettings().originalBytes(),account=persistent(h),state=snapshot(h);
        PcHost accountClone=seed(f,Pc1NativeSettings.initialZeroImage().nativeBytes());accountClone.pc1.argument0(77);accountClone.loadPersistent(new DataInputStream(new ByteArrayInputStream(account)));check(Arrays.equals(account,persistent(accountClone))&&Arrays.equals(before,accountClone.nativeSettings().originalBytes())&&accountClone.pc1.argument0()==77,"Phone account preserves malformed/raw/trailing bytes and leaves frame live");
        PcHost clone=seed(f,Pc1NativeSettings.initialZeroImage().nativeBytes());clone.load(new DataInputStream(new ByteArrayInputStream(state)));check(Arrays.equals(state,snapshot(clone))&&Arrays.equals(before,clone.nativeSettings().originalBytes()),"PCH10/PCP4 snapshot raw settings and inactive bytes roundtrip exactly");
        byte[] old={0x44,0x4d,0x50,0x32};byte[] unchanged=persistent(clone);reject(()->clone.loadPersistent(new DataInputStream(new ByteArrayInputStream(old))),"old phone progress");check(Arrays.equals(unchanged,persistent(clone)),"Old schema without raw unknowns is rejected before mutation");
        byte[] damaged=account.clone();Arrays.fill(damaged,4,8,(byte)0xff);reject(()->clone.loadPersistent(new DataInputStream(new ByteArrayInputStream(damaged))),"backing length");check(Arrays.equals(unchanged,persistent(clone)),"Invalid envelope length preserves current owner");
        Io io=new Io();h.bindNativeSettingsIo(io,"runtime/system.dat");h.bindNativeSettingsAudioEffects((effect,settings)->{});clone.pc1.argument0(88);clone.copyPersistentFrom(h);check(Arrays.equals(before,clone.nativeSettings().originalBytes())&&clone.pc1.argument0()==88&&clone.nativeSettingsIoBound(),"Live account copy keeps canonical frame and transfers settings bindings");
        clone.write(100,5,31);check(io.written!=null&&h.read(100,5)!=(clone.read(100,5)),"Copied settings are immutable owners, not mutable aliased bytearrays");
        PcHost second=f.host("dc2pc-5769");second.write(100,1,2);byte[] prior=persistent(second);PcHost secondClone=f.host("dc2pc-5769");secondClone.loadPersistent(new DataInputStream(new ByteArrayInputStream(prior)));check(Arrays.equals(prior,persistent(secondClone)),"PC2 persistent schema is unchanged");
    }
    private static void progress()throws Exception{
        Fixture f=new Fixture();PcHost h=seed(f,Pc1NativeSettings.currentChsDefaults().nativeBytes());Io io=new Io();h.bindNativeSettingsIo(io,"runtime/system.dat");byte[] nativeFile=new byte[1748];nativeFile[0x70]=3;nativeFile[0xd4+511]=1;nativeFile[0x2d4+1023]=1;
        NativeProgress.Summary added=NativeProgress.parse("dcpc-2063",nativeFile).apply(h);check(added.total==3&&h.read(100,0)==3&&h.read(97,511)==1&&h.read(98,1023)==1&&io.events.isEmpty(),"Phone progress merge patches owned bytes once without pretending to execute bank100 I/O");
        NativeProgress.Target target=new NativeProgress.Target(h.resources,h.codec);((PcHost)target.legacy).loadNativeSettings(Pc1NativeSettings.currentChsDefaults().nativeBytes());((PcHost)target.legacy).bindNativeSettingsIo(io,"runtime/system.dat");target.write(100,1,9);check(target.read(100,1)==9&&io.events.isEmpty(),"Offline progress target uses direct raw merge");
        target.write(97,512,7);check(target.read(98,0)==7&&io.events.isEmpty(),"Offline target respects native CG512/scene0 alias within raw owner");
        List<String> effects=new ArrayList<>();((PcHost)target.legacy).bindNativeSettingsAudioEffects((effect,settings)->effects.add(effect.kind.name()));target.write(99,7,11);check(target.read(99,7)==11&&effects.equals(Arrays.asList("REFRESH_VOLUMES_41CD00")),"Offline target non-import banks retain real host setter effects");
        byte[] saved=target.save();NativeProgress.Target loaded=new NativeProgress.Target(h.resources,h.codec);loaded.load(saved);check(Arrays.equals(saved,loaded.save()),"Offline target roundtrips new raw phone account schema");
    }
    public static int run()throws Exception{count=0;routes();effectOrder();initialization();codecsAndNames();progress();return count;}
    public static void main(String[] paths)throws Exception{
        int checks=run();for(String path:paths){byte[] original=Files.readAllBytes(Paths.get(path));PcHost host=seed(new Fixture(),original);byte[] account=persistent(host);PcHost clone=seed(new Fixture(),Pc1NativeSettings.initialZeroImage().nativeBytes());clone.loadPersistent(new DataInputStream(new ByteArrayInputStream(account)));check(Arrays.equals(original,clone.nativeSettings().originalBytes()),"Read-only actual settings account roundtrip");System.out.println("actualSettingsSha256="+Binary.sha256(original));}
        System.out.println("Pc1SettingsIntegrationTests PASS "+count+" (synthetic="+checks+", actual="+(count-checks)+")");
    }
}
