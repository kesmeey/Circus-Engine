package org.circus.core;

import java.io.*;
import java.nio.file.*;
import java.util.*;

/** Lossless1748-byte persistent owner and native integer I/O-result ordering.
 * Evidence/current CHS byte ranges: pc1-native-settings-stage5. Test I/O is
 *private memory only; optional actual system.dat arguments are read-only. */
public final class Pc1NativeSettingsTests {
    private static int checks;
    private static void check(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
    private interface Action{void run()throws Exception;}
    private static void rejects(Action action,String part)throws Exception{boolean rejected=false;try{action.run();}catch(IOException expected){rejected=expected.getMessage().contains(part);}check(rejected,"reject "+part);}
    private static int get(byte[] b,int at){return (b[at]&255)|((b[at+1]&255)<<8)|((b[at+2]&255)<<16)|((b[at+3]&255)<<24);}
    private static byte[] image(int trailing){byte[] bytes=new byte[1748+trailing];for(int n=0;n<bytes.length;n++)bytes[n]=(byte)(n*67+149);return bytes;}
    private static void raw()throws Exception{
        rejects(()->Pc1NativeSettings.parse(null),"at least1748");for(int length=0;length<1748;length++){final byte[] shortData=new byte[length];rejects(()->Pc1NativeSettings.parse(shortData),"at least1748");}
        for(int trailing:new int[]{0,1,1748}){byte[] data=image(trailing),before=data.clone();Pc1NativeSettings settings=Pc1NativeSettings.parse(data);check(Arrays.equals(settings.originalBytes(),data)&&settings.nativeBytes().length==1748&&settings.originalLength()==data.length,"headerless input and original/native sizes");
            check(Arrays.equals(settings.trailingBytes(),Arrays.copyOfRange(data,1748,data.length)),"unknown trailing retained separately");Arrays.fill(data,(byte)0);byte[] a=settings.originalBytes(),b=settings.nativeBytes(),c=settings.trailingBytes();Arrays.fill(a,(byte)0);Arrays.fill(b,(byte)0);Arrays.fill(c,(byte)0);check(Arrays.equals(settings.originalBytes(),before),"input/output arrays cannot mutate owner");}
        byte[] data=image(5);Pc1NativeSettings settings=Pc1NativeSettings.parse(data);for(int n=0;n<1745;n++)check(settings.rawDword(n)==get(data,n),"raw signed/unaligned DWORD remains exact");
        check(Arrays.equals(settings.rawBytes(0,1748),settings.nativeBytes())&&settings.rawBytes(1748,0).length==0,"all unknown native bytes view");rejects(()->settings.rawDword(1745),"outside owned");rejects(()->settings.rawBytes(Integer.MAX_VALUE,Integer.MAX_VALUE),"outside owned");
        int[] dwords=settings.preferenceDwords();check(dwords.length==18,"all18 native preference DWORDs");for(int n=0;n<18;n++)check(dwords[n]==get(data,n*4),"preference includes settings without99 mapping");Arrays.fill(dwords,0);check(settings.preferenceDwords()[0]==get(data,0),"preference view copied");
        Pc1NativeSettings defaults=Pc1NativeSettings.currentChsDefaults();check(Binary.sha256(defaults.nativeBytes()).equals("9119ed38a87d97e1b74875e9aff495ed5a5f90ff8d5fe849224357a68849da39"),"default template matches complete independently extracted42A078 PE bytes");
        check(!Arrays.equals(defaults.nativeBytes(),Pc1NativeSettings.initialZeroImage().nativeBytes()),"PE zero-filled live image is not the initialization template");for(byte value:Pc1NativeSettings.initialZeroImage().nativeBytes())check(value==0,"initial live native BSS byte");
        check(Arrays.equals(defaults.rawCString(Pc1NativeSettings.Name.FAMILY),new byte[]{(byte)0xb3,(byte)0xaf,(byte)0xb2,(byte)0xd6})&&Arrays.equals(defaults.rawCString(Pc1NativeSettings.Name.GIVEN),new byte[]{(byte)0xb4,(byte)0xbf,(byte)0xd2,(byte)0xbb}),"native family/given raw CHS bytes survive without Unicode recoding");
        for(int n=112;n<1748;n++)check(defaults.nativeBytes()[n]==0,"full native default template includes zero unlock tables");
    }
    private static void names()throws Exception{
        for(Pc1NativeSettings.Name name:Pc1NativeSettings.Name.values()){
            byte[] data=new byte[1749];Arrays.fill(data,(byte)0xa7);data[name.offset+23]=0;Pc1NativeSettings settings=Pc1NativeSettings.parse(data);check(settings.nameCell(name).length==20&&settings.rawCString(name).length==23,"raw C name may cross its20-byte cell");
            byte[] cell=settings.nameCell(name);Arrays.fill(cell,(byte)0);check(settings.nameCell(name)[0]==(byte)0xa7,"name cell clone");
            Pc1NativeSettings patched=settings.withNameCString(name,new byte[]{0x41,0x42});byte[] expected=data.clone();expected[name.offset]=0x41;expected[name.offset+1]=0x42;expected[name.offset+2]=0;check(Arrays.equals(patched.originalBytes(),expected),"strcpy patches prefix plus oneNUL only, retaining stale/unknown bytes");
            byte[] longPrefix=new byte[27];Arrays.fill(longPrefix,(byte)0x71);check(settings.withNameCString(name,longPrefix).rawCString(name).length==27,"known cross-cell copy aliases are not truncated");
            Arrays.fill(data,name.offset,1748,(byte)0xff);data[1748]=0;Pc1NativeSettings noTerminator=Pc1NativeSettings.parse(data);rejects(()->noTerminator.rawCString(name),"exceeds owned1748");check(Arrays.equals(data,noTerminator.originalBytes()),"unterminated name admitted losslessly, trailingNUL not loaded memory");
            byte[] oversized=new byte[1748];Arrays.fill(oversized,(byte)1);rejects(()->settings.withNameCString(name,new byte[]{1,0,2}),"contains NUL");rejects(()->settings.withNameCString(name,oversized),"outside owned");
        }
    }
    private static void setters()throws Exception{
        byte[] data=image(11);Pc1NativeSettings settings=Pc1NativeSettings.parse(data);int[] indices={5,6,7,8,9,10,11,12,13,14,15,16,17,18,20};
        // Actual411910/411B10 absolute addresses, independent of helper offsets.
        int[] addresses={0x454d18,0x454d1c,0x454d04,0x454d0c,0x454d14,0x454ce8,0x454ce0,0x454cf0,0x454cdc,0x454cfc,0x454d08,0x454ce4,0x454cf8,0x454cec,0x454cf4};
        check(Arrays.equals(indices,Pc1NativeSettings.indices99()),"persistent99 mapping set matches native dispatch");
        for(int n=0;n<indices.length;n++){
            int index=indices[n],offset=addresses[n]-0x454cd8;check(settings.read99(index)==get(data,offset),"native99 absolute read address "+index);
            for(int value:new int[]{Integer.MIN_VALUE,-1,0,1,255,256,Integer.MAX_VALUE}){
                Pc1NativeSettings.ScriptWrite changed=settings.withScriptWrite(99,index,value,new Pc1NativeSettings.WriteContext(17,3));byte[] next=changed.settings.originalBytes();
                check(changed.settings.read99(index)==value&&get(next,offset)==value,"native DWORD store has no clamp");byte[] expected=data.clone();for(int at=0;at<4;at++)expected[offset+at]=(byte)(value>>>(at*8));check(Arrays.equals(next,expected),"DWORD write retains every other raw byte");
                int expectedEffects=index==7?1:index==14?2:0;check(changed.effects.size()==expectedEffects,"only native setter side effects are requested");
            }
        }
        for(int index:new int[]{0,1,2,3,4,19,21,22,243,244,245,246,247,248,249,250,251,252,253,254,255}){check(!Pc1NativeSettings.owns99(index),"external/default99 is not assigned a false persistent slot");rejects(()->settings.read99(index),"not owned");rejects(()->settings.withScriptWrite(99,index,1,null),"not owned");}
        for(Pc1NativeSettings.AudioField field:Pc1NativeSettings.AudioField.values())check(settings.audio(field)==get(data,field.offset),"independent audio raw fields");
        Pc1NativeSettings.ScriptWrite five=settings.withScriptWrite(99,5,0,null);check(five.settings.audio(Pc1NativeSettings.AudioField.VOICE_ENABLED)==settings.audio(Pc1NativeSettings.AudioField.VOICE_ENABLED)&&five.effects.isEmpty(),"99:5 is not native voice-enabled or a per-character volume operation");
        Pc1NativeSettings.ScriptWrite refresh=settings.withScriptWrite(99,7,settings.read99(7),null);check(refresh.effects.get(0).kind==Pc1NativeSettings.EffectKind.REFRESH_VOLUMES_41CD00,"99:7 refreshes native volumes even on same value");
        Pc1NativeSettings.ScriptWrite off=settings.withScriptWrite(99,14,0,null);check(off.effects.get(0).kind==Pc1NativeSettings.EffectKind.STOP_MUSIC_41C650&&Arrays.equals(off.effects.get(0).arguments(),new int[]{0})&&off.effects.get(1).kind==Pc1NativeSettings.EffectKind.ARM_AUDIO_TIMER_4070E0,"99:14 off stores then stopsBGM0 before arming audio timer");
        Pc1NativeSettings.ScriptWrite on=settings.withScriptWrite(99,14,-5,new Pc1NativeSettings.WriteContext(17,3));check(on.effects.get(0).kind==Pc1NativeSettings.EffectKind.REQUEST_MUSIC_41C4B0&&Arrays.equals(on.effects.get(0).arguments(),new int[]{17,3})&&on.effects.get(1).kind==Pc1NativeSettings.EffectKind.ARM_AUDIO_TIMER_4070E0,"nonzero99:14 re-requests live logical track/mode before timer, not saved audio replay");
        check(settings.withScriptWrite(99,14,1,new Pc1NativeSettings.WriteContext(-1,3)).effects.isEmpty(),"enabled with logicalBGM-1 has no native request/timer call");rejects(()->settings.withScriptWrite(99,14,1,null),"live logical BGM context");
        int[] effectArguments=on.effects.get(0).arguments();effectArguments[0]=0;check(on.effects.get(0).arguments()[0]==17,"effect arguments cannot mutate journal");boolean immutable=false;try{on.effects.clear();}catch(UnsupportedOperationException expected){immutable=true;}check(immutable,"effects list is immutable");
        for(int bank:new int[]{100,97,98}){
            int start=bank==100?112:bank==97?212:724;
            for(int offset=0;offset<1748;offset++)check(settings.readByteBank(bank,offset-start)==(data[offset]&255),"native byte-bank pointer aliases every owned byte");
            rejects(()->settings.readByteBank(bank,-start-1),"outside owned");rejects(()->settings.readByteBank(bank,1748-start),"outside owned");
            for(int value:new int[]{Integer.MIN_VALUE,-257,-1,0,255,256,Integer.MAX_VALUE}){
                Pc1NativeSettings.ScriptWrite changed=settings.withScriptWrite(bank,0,value,null);check(changed.settings.readByteBank(bank,0)==(value&255),"persistent byte stores truncate low8, not clamp");check(changed.effects.size()==(bank==100?1:0),"only bank100 setter immediately writes system.dat");
            }
        }
        check(settings.withScriptWrite(100,100,7,null).settings.readByteBank(97,0)==7,"bank100 beyond99 aliases firstCG byte");check(settings.withScriptWrite(97,512,9,null).settings.readByteBank(98,0)==9,"bank97 beyond511 aliases firstscene byte");
        check(settings.withScriptWrite(100,-112,255,null).settings.rawBytes(0,1)[0]==(byte)255,"known negative index alias preserved");rejects(()->settings.readByteBank(90,0),"not owned");rejects(()->settings.withScriptWrite(90,0,1,null),"not owned");check(Arrays.equals(data,settings.originalBytes()),"all setter projections leave source owner untouched");
    }
    private static final class Io implements Pc1NativeSettings.NativeIo {
        byte[] data=image(0),readBuffer,written;int open=5,read=1748,write=1748,close;boolean throwRead,throwWrite,throwClose;final List<String> calls=new ArrayList<>();
        public int open(String path,int flags,int mode){calls.add("open:"+flags+":"+mode);return open;}
        public int read(int fd,byte[] destination,int requested)throws IOException{calls.add("read:"+fd+":"+requested);if(throwRead)throw new IOException("unsupported read adapter");readBuffer=destination;System.arraycopy(data,0,destination,0,Math.max(0,Math.min(Math.min(data.length,requested),read)));return read;}
        public int write(int fd,byte[] source,int requested)throws IOException{calls.add("write:"+fd+":"+requested);if(throwWrite)throw new IOException("unsupported write adapter");written=source.clone();Arrays.fill(source,(byte)0);return write;}
        public int close(int fd)throws IOException{calls.add("close:"+fd);if(throwClose)throw new IOException("unsupported close adapter");return close;}
    }
    private static Pc1NativeSettings.IoKind[] kinds(List<Pc1NativeSettings.IoEvent> events){Pc1NativeSettings.IoKind[] result=new Pc1NativeSettings.IoKind[events.size()];for(int n=0;n<result.length;n++)result[n]=events.get(n).kind;return result;}
    private static void io()throws Exception{
        Pc1NativeSettings defaults=Pc1NativeSettings.currentChsDefaults();Io missing=new Io();missing.open=-1;Pc1NativeSettings.ReadResult absent=Pc1NativeSettings.nativeRead(missing,"system.dat",defaults);
        check(absent.nativeResult==-1&&absent.defaulted&&Arrays.equals(absent.settings.nativeBytes(),defaults.nativeBytes())&&missing.calls.equals(Arrays.asList("open:32768:0")),"open failure copies complete default template without read/close");
        check(Arrays.equals(kinds(absent.events),new Pc1NativeSettings.IoKind[]{Pc1NativeSettings.IoKind.OPEN_READ,Pc1NativeSettings.IoKind.COPY_DEFAULT}),"native missing-file memory mutation order");
        for(int read:new int[]{-1,0,1,1747,1748,1749})for(int close:new int[]{-1,0}){
            Io input=new Io();input.read=read;input.close=close;Pc1NativeSettings.ReadResult result=Pc1NativeSettings.nativeRead(input,"system.dat",defaults);boolean admitted=read==1748;
            check(result.nativeResult==(admitted?0:-1)&&result.defaulted!=admitted&&result.closeResult==close&&result.readCount==read,"read admission only exact count;close status does not change native result");
            check(Arrays.equals(result.settings.nativeBytes(),admitted?input.data:defaults.nativeBytes()),"short read cannot overlay partial data on template/current owner");
            check(input.calls.equals(Arrays.asList("open:32768:0","read:5:1748","close:5")),"native read closes before copying success/default data");
            check(Arrays.equals(kinds(result.events),new Pc1NativeSettings.IoKind[]{Pc1NativeSettings.IoKind.OPEN_READ,Pc1NativeSettings.IoKind.READ,Pc1NativeSettings.IoKind.CLOSE,admitted?Pc1NativeSettings.IoKind.COPY_READ:Pc1NativeSettings.IoKind.COPY_DEFAULT}),"explicit ordered read result journal");
            if(admitted){Arrays.fill(input.readBuffer,(byte)0);check(Arrays.equals(result.settings.nativeBytes(),input.data),"I/O adapter cannot mutate published immutable owner through retained read buffer");}
        }
        Io zeros=new Io();zeros.data=new byte[1748];check(Arrays.equals(Pc1NativeSettings.nativeRead(zeros,"system.dat",defaults).settings.nativeBytes(),zeros.data),"native headerless read admits all-zero data without inventing a signature");
        byte[] original=image(31);Pc1NativeSettings settings=Pc1NativeSettings.parse(original);Io failOpen=new Io();failOpen.open=-1;Pc1NativeSettings.WriteResult unopened=settings.nativeWrite(failOpen,"system.dat");check(unopened.nativeResult==-1&&!unopened.fullCountAndCloseSucceeded&&failOpen.calls.equals(Arrays.asList("open:33537:384")),"native write open failure has no write/close");
        for(int written:new int[]{-1,0,1,1747,1748,1749})for(int closed:new int[]{-1,0}){
            Io output=new Io();output.write=written;output.close=closed;Pc1NativeSettings.WriteResult result=settings.nativeWrite(output,"system.dat");
            check(result.nativeResult==0&&result.writeCount==written&&result.closeResult==closed&&result.fullCountAndCloseSucceeded==(written==1748&&closed==0),"native reported0 is separate from observed full-count/close result");
            check(output.calls.equals(Arrays.asList("open:33537:384","write:5:1748","close:5"))&&Arrays.equals(output.written,Arrays.copyOf(original,1748)),"write emits only1748 bytes before close;unknown trailing not native output");
            check(Arrays.equals(settings.originalBytes(),original),"write callback cannot mutate raw backing");check(Arrays.equals(kinds(result.events),new Pc1NativeSettings.IoKind[]{Pc1NativeSettings.IoKind.OPEN_WRITE,Pc1NativeSettings.IoKind.WRITE,Pc1NativeSettings.IoKind.CLOSE}),"ordered writer event journal");
        }
        rejects(()->Pc1NativeSettings.nativeRead(null,"system.dat",defaults),"requires I/O");rejects(()->settings.nativeWrite(null,"system.dat"),"requires I/O");
        Io exceptionalRead=new Io();exceptionalRead.throwRead=true;rejects(()->Pc1NativeSettings.nativeRead(exceptionalRead,"system.dat",defaults),"unsupported read adapter");check(exceptionalRead.calls.equals(Arrays.asList("open:32768:0","read:5:1748","close:5")),"unexpected adapter exception closes descriptor without fabricating read/default result");
        Io exceptionalWrite=new Io();exceptionalWrite.throwWrite=true;exceptionalWrite.throwClose=true;boolean preserved=false;try{settings.nativeWrite(exceptionalWrite,"system.dat");}catch(IOException expected){preserved=expected.getMessage().equals("unsupported write adapter")&&expected.getSuppressed().length==1;}check(preserved&&exceptionalWrite.calls.equals(Arrays.asList("open:33537:384","write:5:1748","close:5")),"unexpected write/close exceptions preserve primary failure and cleanup order");
    }
    public static int run()throws Exception{checks=0;raw();names();setters();io();return checks;}
    private static int nativeVectors(Path path)throws Exception{
        Map<?,?> reference=(Map<?,?>)DataJson.parse(new String(Files.readAllBytes(path),"UTF-8"));check(reference.get("target_sha256").equals(Pc1NativeSettings.CURRENT_CHS_PE_SHA256),"instruction vectors bind current CHS target");int vectors=0;
        for(Object value:(List<?>)reference.get("vectors")){
            Map<?,?> row=(Map<?,?>)value;int bank=((Number)row.get("bank")).intValue(),index=((Number)row.get("index")).intValue(),stored=((Number)row.get("value")).intValue(),music=((Number)row.get("logical_music")).intValue(),mode=((Number)row.get("bgm_mode")).intValue();
            Pc1NativeSettings.ScriptWrite result=Pc1NativeSettings.parse(image(11)).withScriptWrite(bank,index,stored,new Pc1NativeSettings.WriteContext(music,mode));check(Binary.sha256(result.settings.originalBytes()).equals(row.get("expected_original_sha256")),"actual x86 integer reference raw store "+bank+":"+index);
            List<?> calls=(List<?>)row.get("calls");check(calls.size()==result.effects.size(),"actual x86 call journal effect count");
            for(int n=0;n<calls.size();n++){Map<?,?> call=(Map<?,?>)calls.get(n);Pc1NativeSettings.Effect effect=result.effects.get(n);String name=effect.kind.name();String address=String.format(Locale.ROOT,"%08x",Integer.parseInt(name.substring(name.lastIndexOf('_')+1),16));check(address.equals(call.get("address")),"actual x86 call journal function and order");List<?> args=(List<?>)call.get("arguments");int[] expected=new int[args.size()];for(int a=0;a<expected.length;a++)expected[a]=((Number)args.get(a)).intValue();check(Arrays.equals(expected,effect.arguments()),"actual x86 call journal raw parameters");}
            vectors++;
        }
        check(vectors==((Number)reference.get("vector_count")).intValue(),"all native instruction vectors consumed");return vectors;
    }
    public static void main(String[] args)throws Exception{
        int synthetic=run(),samples=0,vectors=0,machineChecks=0;for(int n=0;n<args.length;n++){String name=args[n];if(name.equals("--native-vectors")){if(++n>=args.length)throw new IOException("Missing native vector path");int before=checks;vectors+=nativeVectors(Paths.get(args[n]));machineChecks+=checks-before;continue;}Path path=Paths.get(name);byte[] data=Files.readAllBytes(path);Pc1NativeSettings settings=Pc1NativeSettings.parse(data);check(Arrays.equals(data,settings.originalBytes()),"actual system.dat byte-exact roundtrip");Io input=new Io();input.data=data;check(Arrays.equals(Pc1NativeSettings.nativeRead(input,path.toString(),Pc1NativeSettings.currentChsDefaults()).settings.nativeBytes(),Arrays.copyOf(data,1748)),"actual sample native read projection is exact first1748");System.out.println("actual read-only system.dat "+path.toAbsolutePath()+" bytes="+data.length+" sha256="+Binary.sha256(data)+" byteExact=true");samples++;}
        System.out.println("PASS Pc1NativeSettingsTests "+synthetic+" synthetic checks; native instruction vectors="+vectors+" vector assertions="+machineChecks+" actual samples="+samples+" actual assertions="+(checks-synthetic-machineChecks));
    }
}
