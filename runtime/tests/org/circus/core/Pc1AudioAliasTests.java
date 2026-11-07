package org.circus.core;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.InflaterInputStream;

/** Original full41BE20 plus41CC10/90 machine programs. The decoder/COM leaves
 *are explicit FIXTURE32 owners here; they do not authorize production IDs or
 *truncate a64-bit COM pointer. Actual output is independently audited. */
public final class Pc1AudioAliasTests {
    private static int checks;
    private static void check(boolean value,String why){checks++;if(!value)throw new AssertionError(why);}
    private static int n(Object value){return(int)((Number)value).longValue();}
    private static int field(Map<?,?> row,String key,int fallback){return row.containsKey(key)?n(row.get(key)):fallback;}
    private static boolean yes(Map<?,?> row,String key){return Boolean.TRUE.equals(row.get(key));}
    private static byte[] bytes(String text){return text.getBytes(StandardCharsets.US_ASCII);}
    private static byte[] readAll(InputStream in)throws IOException{ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] b=new byte[4096];for(int c;(c=in.read(b))!=-1;)out.write(b,0,c);return out.toByteArray();}
    private static String hex(byte[] bytes){StringBuilder out=new StringBuilder();for(byte value:bytes)out.append(String.format(Locale.ROOT,"%02x",value&255));return out.toString();}
    private static Pc1Audio.Catalog empty(){return new Pc1Audio.Catalog(){public int musicCount(){return 0;}public int soundCount(){return 0;}public Pc1Audio.Entry music(int i)throws IOException{throw new IOException("unused fixture");}public Pc1Audio.Entry sound(int i)throws IOException{throw new IOException("unused fixture");}};}
    private static final class Fault extends IOException {final int pc;Fault(int pc){super("Fixture32 raw COM dereference at0x"+Integer.toHexString(pc));this.pc=pc;}}
    private static final class Output implements Pc1Audio.Backend {
        final Map<?,?> c;final List<String> trace=new ArrayList<String>();final Set<Integer> objects=new HashSet<Integer>();int next;Pc1Audio audio;
        Output(Map<?,?> c){this.c=c;}
        int object(){int pointer=0x700000+(++next)*256;objects.add(pointer);return pointer;}
        void pointer(int raw,int pc)throws IOException{int pointer=audio.readNativeInt(0xbc12d0+raw*4);if(!objects.contains(pointer))throw new Fault(pc);}
        public boolean pcmInitialized(){return true;}
        public void nativeMessage(int address){trace.add("message:"+address);}
        public Pc1Audio.Loaded loadPcm(int raw,byte[] name,boolean streaming)throws IOException{
            trace.add("load:"+raw+":"+hex(name)+":"+(streaming?1:0));if(yes(c,"decoderFail"))return null;int size=field(c,"bytes",37),local=audio.readNativeInt(0xbc1d04);audio.writeNativeInt(0xbc1d38+local*4,size);
            if(yes(c,"createFail")){nativeMessage(0x43ddc4);return new Pc1Audio.Loaded(size,0,0,0,0,false,0,false,Integer.MIN_VALUE,null).alreadyPublished();}
            audio.writeNativeInt(0xbc12d0+local*4,object());int current=audio.readNativeInt(0xbc1d04);pointer(current,0x41bff0);
            boolean cached=audio.readNativeInt(0xbc12cc)!=0;
            if(!cached){if(!yes(c,"frequencyFail"))audio.writeNativeInt(0xbbab28+current*4,22050);current=audio.readNativeInt(0xbc1d04);int rate=audio.readNativeInt(0xbbab28+current*4);audio.writeNativeInt(0xbb8550+current*4,0);audio.writeNativeInt(0xbbab54+current*4,rate);audio.writeNativeInt(0xbbab80+current*4,0);}
            audio.writeNativeInt(0xbbaafc+current*4,1);if(!cached){if(name.length>255){nativeMessage(0x43dd9c);return new Pc1Audio.Loaded(size,0,0,0,0,false,1,false,Integer.MIN_VALUE,null).alreadyPublished();}for(int k=0;k<=name.length;k++)write(0xbc1304+(current<<8)+k,k==name.length?0:name[k]);}
            return new Pc1Audio.Loaded(size,0,0,0,0,cached).alreadyPublished();
        }
        Pc1NativeMemory memory;void write(int address,int value)throws IOException{memory.writeAddress(address,value);}
        public int stop(int raw)throws IOException{pointer(raw,0x41c280);trace.add("stop:"+raw);return 0;}
        public int release(int raw)throws IOException{int pointer=audio.readNativeInt(0xbc12d0+raw*4);if(!objects.remove(pointer))throw new Fault(0x41c20e);trace.add("release:"+raw);audio.writeNativeInt(0xbc12d0+raw*4,0);return 0;}
        public void closeStream(){trace.add("closeStream");}
        public int setVolume(int raw,int volume)throws IOException{pointer(raw,0x41cc7f);trace.add("volume:"+raw+":"+volume);return 0;}
        public int setPosition(int raw,int value)throws IOException{pointer(raw,0x41ccb4);trace.add("position:"+raw+":"+value);return 0;}
        public int play(int raw,int flags)throws IOException{pointer(raw,0x41ccc3);trace.add("play:"+raw+":"+flags);return 0;}
        public int playCursor(int raw)throws IOException{throw new IOException("unused fixture");}
        public int status(int raw)throws IOException{throw new IOException("unused fixture");}
        public byte[] midiPath(byte[] raw)throws IOException{throw new IOException("unused fixture");}
        public int mci(byte[] raw,boolean notify)throws IOException{throw new IOException("unused fixture");}
        public int setTimer(int id,int ms)throws IOException{throw new IOException("unused fixture");}
        public int killTimer(int id)throws IOException{throw new IOException("unused fixture");}
        public boolean adoptRestoredState(Pc1Audio.State state){return false;}
    }
    private static void bind(Pc1NativeMemory memory,int address,int length)throws IOException{final byte[] raw=new byte[length];memory.bind(address,length,new Pc1NativeMemory.Region(){public int read(int at){return raw[at]&255;}public void write(int at,int value){raw[at]=(byte)value;}});}
    private static final String VECTORS="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";
    public static int run()throws Exception{
        checks=0;String text;try(InputStream in=new InflaterInputStream(new ByteArrayInputStream(Base64.getDecoder().decode(VECTORS)))){text=new String(readAll(in),StandardCharsets.UTF_8);}Map<?,?> report=(Map<?,?>)DataJson.parse(text);List<?> rows=(List<?>)report.get("cases");check(rows.size()==22,"Full native loader/negative/wrapped fixture set");
        for(Object value:rows){Map<?,?> row=(Map<?,?>)value,c=(Map<?,?>)row.get("case");String id=c.get("id").toString();byte[] prefs=Pc1NativeSettings.currentChsDefaults().nativeBytes();for(int k=0;k<4;k++)prefs[0x3c+k]=(byte)(field(c,"level",30)>>>(k*8));final byte[] rawPrefs=prefs;Pc1NativeMemory memory=new Pc1NativeMemory(Pc1NativeFrame.zeroBss(),Pc1CodeBuffer.zeroBss(),new Pc1NativeMemory.SettingsBytes(){public int read(int at){return rawPrefs[at]&255;}public void write(int at,int value){rawPrefs[at]=(byte)value;}});new Pc1CatalogMemory(memory);new Pc1LayoutMemory(memory);bind(memory,0xbb8548,4);bind(memory,0xbc12c8,4);bind(memory,0xbc12d0,40);
            Output output=new Output(c);Pc1Audio audio=new Pc1Audio(output,empty());output.audio=audio;output.memory=memory;audio.bindNativeMemory(memory);audio.adoptAlreadyInitialized();audio.writeNativeInt(0xbb854c,field(c,"ruby",0));audio.writeNativeInt(0xbb8548,field(c,"weight",0));if(c.containsKey("patches"))for(Object patch:(List<?>)c.get("patches")){List<?> pair=(List<?>)patch;audio.writeNativeInt(n(pair.get(0)),n(pair.get(1)));}if(yes(c,"full"))for(int k=0;k<10;k++)audio.writeNativeInt(0xbbaafc+k*4,1);if(yes(c,"aliasObject")){audio.writeNativeInt(0xbc12cc,output.object());audio.writeNativeInt(0xbbaaf8,1);}
            Integer fault=null;try{if(yes(c,"start"))audio.startMix(field(c,"slot",-4));else audio.mixVoice(bytes(c.containsKey("name")?c.get("name").toString():"A"),field(c,"slot",-4),Pc1NativeSettings.parse(prefs));}catch(Fault problem){fault=problem.pc;}
            Map<?,?> expectedFault=(Map<?,?>)row.get("fault");check(expectedFault==null?fault==null:fault!=null&&fault==Integer.parseUnsignedInt(expectedFault.get("pc").toString().substring(2),16),"Native fault PC "+id+" actual="+fault);check(output.trace.equals(row.get("trace")),"Full native call order "+id+" actual="+output.trace);
            ByteArrayOutputStream state=new ByteArrayOutputStream();audio.state().save(new DataOutputStream(state));check(Binary.sha256(state.toByteArray()).equals(row.get("state_sha256")),"All audio/control/PE/stale bytes after native alias program "+id);
            for(Map.Entry<?,?> field:((Map<?,?>)row.get("external")).entrySet()){int address=Integer.parseUnsignedInt(field.getKey().toString().substring(2),16);check(audio.readNativeInt(address)==n(field.getValue()),"External canonical alias "+id+" "+field.getKey());}
        }
        return checks;
    }
    public static void main(String[] args)throws Exception{System.out.println("Pc1AudioAliasTests PASS "+run());}
}
