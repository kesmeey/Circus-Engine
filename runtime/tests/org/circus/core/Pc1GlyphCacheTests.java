package org.circus.core;

import java.io.*;
import java.util.*;

/** Full current404510/404660 instruction-derived cache images and pointer
 * sequences. Projection is an explicit synthetic GDI leaf, tested separately. */
public final class Pc1GlyphCacheTests {
    private static int count;
    private static void check(boolean v,String why){count++;if(!v)throw new AssertionError(why);}
    private interface Action{void run()throws Exception;}
    private static void reject(Action action,String why)throws Exception{boolean failed=false;try{action.run();}catch(IOException expected){failed=why==null||String.valueOf(expected.getMessage()).contains(why);}check(failed,"Reject "+why);}
    private static byte[] raw(int n){return new byte[]{(byte)(0x81+n/189),(byte)(0x40+n%189),0};}
    private static final String[] GOLDEN={
        "mixed|5be579e1387647397b31418a84d9983bb269eb8f3332c5185aa49a0b8c6f11fd|0258f50251951bc783aafe446b8896fda63104fd22db7e9b1da8ebb9a65021f9|6|17|0|0|9",
        "all_first_bytes|73f451faa13aa6dbb2d8e1e37facca70480eba2259a5adb17931124ad8a21529|b11ef8d115aaacd9d49311d35ea7fa293fb84c323f7b233f4919746146a73da6|511|28|0|0|512",
        "variable_wrap|40e5e545f67de894876d1fcf78e8f6b996e547abebe855f1760010edf13a1846|f0c003199a2998790f5402f844c7ca43c5c484f6477f4d90b78acb42b93d99eb|515|12|0|2|515",
        "fixed_wrap|07b24cb52a170606d342ca3ab9fe4c05c9d6823b1a61656afe881c951cfb0608|394ac6d272cd636b88111e403cab07d4ad0c770172c5d9d8ffaf4405124c9e1b|259|28|2|0|259",
        "variable_alias_fixed|cdd57e367bcbe16ed6f7e90fe5d38d3e1a8eca5158acf44144a5c34a811e2376|42e29228fed16923b3edfed4396b2b16aad86f1bfb1493e8198ef42d2244040f|513|41|0|0|514",
        "fixed_alias_variable|1c96de7d22e1ebc348208aeb31340bae2778ae7e619a04d79f42b8a2cd3b168e|b123c966fd4485900208352dd6492a1a696501b4a4e47525e551e6dd9cbf0067|257|28|0|0|257",
        "variable_stale|df6ec880e9dec87c7c04d5c874d4b431ec06b75a451a57997f73f77770c68bd5|4ffddad0d4f570b6657cf2b5ac19cb68b307c769275952aa9c6a87082067a15d|513|12|0|1|513",
        "zero_key|d55ce73f2aab01e9404649bf84f270dae60801c6665cff54bb95b5a0e6a2f242|6f67ae5c9393f824e516f313301b0b1e335f96fd99fc98d173418d3e53542a36|2|12|0|0|6",
    };
    private static final class Font implements Pc1GlyphCache.Provider {
        int queries,fontSize,saves,restores;boolean fail;String identity="fixture-font";Pc1GlyphCache observed;boolean snapshotRejected;
        public Pc1GlyphRaster.Outline outline(byte[] raw,int offset,int size)throws IOException{
            queries++;fontSize=size;if(fail)throw new IOException("fixture GDI failure");if(observed!=null){try{observed.copy();}catch(IOException expected){snapshotRejected=true;}}
            if(size<=0||size>40)return new Pc1GlyphRaster.Outline(0,0,0,0,0,0,0,new byte[0]);int width=size*2,height=size*2,stride=(width+31)/32*4,character=Pc1GlyphRaster.characterCode(raw,offset);byte[] mono=new byte[stride*height];
            for(int y=0;y<size;y++)for(int x=0;x<size;x++){int cell=(character+(y*size+x)*7+size)&15;for(int bit=0;bit<4;bit++)if((cell&(1<<bit))!=0){int px=x*2+(bit&1),py=y*2+(bit>>>1);mono[py*stride+(px>>>3)]|=(byte)(0x80>>>(px&7));}}
            return new Pc1GlyphRaster.Outline(0,width,height,0,0,0,0,mono);
        }
        public byte[] saveFontContext()throws IOException{saves++;ByteArrayOutputStream out=new ByteArrayOutputStream();DataOutputStream data=new DataOutputStream(out);data.writeUTF(identity);data.writeInt(queries);data.writeInt(fontSize);return out.toByteArray();}
        public void restoreFontContext(byte[] state)throws IOException{DataInputStream in=new DataInputStream(new ByteArrayInputStream(state));String name=in.readUTF();int q=in.readInt(),size=in.readInt();if(!name.equals(identity)||in.available()!=0)throw new IOException("font environment mismatch");queries=q;fontSize=size;restores++;}
    }
    private static final class Fixture {
        final Pc1GlyphCache cache=Pc1GlyphCache.zeroBss();final Font font=new Font();final ByteArrayOutputStream pointers=new ByteArrayOutputStream();int lookups;
        void lookup(byte[] raw,int size)throws IOException{Pc1GlyphCache.Entry entry=cache.lookup(raw,0,size,font);int p=entry.nativeAddress;for(int n=0;n<4;n++)pointers.write(p>>>(n*8));lookups++;}
    }
    private static void scenario(Fixture f,String name)throws IOException{
        if(name.equals("mixed")){byte[][] raw={{65,0},{65,0},{65,0},{66,0},{65,0},{(byte)255,65,0},{(byte)255,66,0},{(byte)255,65,0},{65,0}};int[] sizes={12,12,28,12,12,12,12,12,17};for(int n=0;n<sizes.length;n++)f.lookup(raw[n],sizes[n]);}
        else if(name.equals("all_first_bytes")){for(int first=0;first<256;first++)f.lookup(new byte[]{(byte)first,0x40,0},12);for(int first=0;first<256;first++)f.lookup(new byte[]{(byte)first,0x40,0},28);}
        else if(name.startsWith("variable_")){for(int n=0;n<512;n++)f.lookup(raw(n),name.equals("variable_stale")?40:12);if(name.equals("variable_wrap")){f.cache.variableReplacement(511);f.lookup(raw(700),12);f.lookup(raw(701),12);f.lookup(raw(702),12);}else if(name.equals("variable_alias_fixed")){f.cache.variableReplacement(512);f.lookup(raw(700),41);f.lookup(raw(700),28);}else f.lookup(raw(700),12);}
        else if(name.startsWith("fixed_")){for(int n=0;n<256;n++)f.lookup(raw(n),28);f.cache.fixedReplacement(name.equals("fixed_wrap")?255:-1);f.lookup(raw(700),28);if(name.equals("fixed_wrap")){f.lookup(raw(701),28);f.lookup(raw(702),28);}}
        else if(name.equals("zero_key")){f.lookup(new byte[]{0,0},28);f.lookup(new byte[]{0,0},0);f.lookup(new byte[]{0,0},12);f.lookup(new byte[]{0,0},12);f.lookup(new byte[]{65,0},12);f.lookup(new byte[]{0,0},28);}
        else throw new IOException("Unknown fixture "+name);
    }
    private static byte[] wire(Pc1GlyphCache c,Font p)throws IOException{ByteArrayOutputStream out=new ByteArrayOutputStream();c.save(new DataOutputStream(out),p);return out.toByteArray();}
    private static Pc1GlyphCache unwire(byte[] state,Font p)throws IOException{return Pc1GlyphCache.load(new DataInputStream(new ByteArrayInputStream(state)),p);}
    public static int run()throws Exception{
        count=0;check(GOLDEN.length==8,"All eight native search/eviction/alias scenarios");
        for(String row:GOLDEN){String[] s=row.split("\\|");Fixture f=new Fixture();scenario(f,s[0]);check(Binary.sha256(f.cache.bytes()).equals(s[1]),"whole1025024 native bytes including stale/inactive entries "+s[0]);check(Binary.sha256(f.pointers.toByteArray()).equals(s[2]),"actual native cache pointer sequence "+s[0]);check(f.font.queries==Integer.parseInt(s[3])&&f.font.fontSize==Integer.parseInt(s[4]),"only native misses call font service "+s[0]);check(f.cache.fixedReplacement()==Integer.parseInt(s[5])&&f.cache.variableReplacement()==Integer.parseInt(s[6])&&f.lookups==Integer.parseInt(s[7]),"raw replacement counters and lookup count "+s[0]);}
        Pc1GlyphCache cache=Pc1GlyphCache.zeroBss();Font font=new Font();font.observed=cache;Pc1GlyphCache.Entry first=cache.lookup(new byte[]{65,0},0,12,font);check(!first.hit&&font.snapshotRejected,"miss invokes provider with cache callback snapshot locked");cache.lookup(new byte[]{66,0},0,28,font);int q=font.queries,size=font.fontSize;Pc1GlyphCache.Entry hit=cache.lookup(new byte[]{65,0},0,12,null);check(hit.hit&&font.queries==q&&font.fontSize==size&&size==28,"cache hit does not select font12 or even require/query provider");cache.writeU8(first.nativeAddress,257);check(first.bytes(1)[0]==1&&hit.bytes(1)[0]==1,"entry is a live native pointer view over canonical bytes");byte[] exported=cache.bytes();exported[first.nativeAddress-Pc1GlyphCache.BASE]=0;check(first.bytes(1)[0]==1,"cache export is detached");byte[] view=first.bytes(4);view[0]=0;check(first.bytes(1)[0]==1,"entry byte export is detached");
        byte[] saved=wire(cache,font);Font restoredFont=new Font();Pc1GlyphCache restored=unwire(saved,restoredFont);check(Arrays.equals(cache.bytes(),restored.bytes())&&restoredFont.queries==font.queries&&restoredFont.fontSize==28&&restoredFont.restores==1,"bundle restores rawcache and independent actual-font context");check(Arrays.equals(saved,wire(restored,restoredFont)),"P1K1 complete snapshot byte roundtrip");restored.lookup(new byte[]{65,0},0,12,restoredFont);check(restoredFont.fontSize==28&&restoredFont.queries==font.queries,"restored cache hit still leaves font28 selected");
        Font different=new Font();different.identity="other-font";different.fontSize=99;reject(()->unwire(saved,different),"font environment mismatch");check(different.fontSize==99&&different.restores==0,"font environment rejection preserves existing provider state");Font untouched=new Font();reject(()->unwire(Arrays.copyOf(saved,50),untouched),null);check(untouched.restores==0,"truncated raw cache cannot mutate font context");
        byte[] malformed=saved.clone();int at=4+8+(Pc1FontMemory.END-Pc1FontMemory.BASE)+12+8;Arrays.fill(malformed,at,at+4,(byte)255);reject(()->unwire(malformed,untouched),"state length");
        for(int b=0;b<256;b++){int expected=b>128?b*189+64-0x5ec0:b;check(Pc1GlyphCache.key(new byte[]{(byte)b,64},0)==expected,"exact patched CHS key "+b);}
        check(Pc1GlyphCache.key(new byte[]{(byte)255,65},0)!=Pc1GlyphCache.key(new byte[]{(byte)255,66},0)&&Pc1GlyphRaster.characterCode(new byte[]{(byte)255,65},0)==Pc1GlyphRaster.characterCode(new byte[]{(byte)255,66},0),"FF key/actual GDI uChar discrepancy remains explicit");reject(()->Pc1GlyphCache.key(new byte[]{(byte)255},0),"following raw byte");
        Font failed=new Font();failed.fail=true;byte[] prior=cache.bytes();reject(()->cache.lookup(new byte[]{67,0},0,12,failed),"fixture GDI failure");check(Arrays.equals(prior,cache.bytes()),"a provider failure before output clearing does not publish key or size");reject(()->cache.lookup(new byte[]{67,0},0,12,null),"actual GDI glyph provider");
        Pc1GlyphCache clone=cache.copy();clone.writeI32(Pc1GlyphCache.BASE,0x12345678);check(cache.readI32(Pc1GlyphCache.BASE)!=0x12345678,"copy is a separate complete byte owner");check(cache.readU8(Pc1GlyphCache.END)==0,"adjacent TEXTMETRICA is canonical knownBSS");reject(()->cache.readU8(Pc1FontMemory.END),"unowned native neighbor");
        Fixture full=new Fixture();for(int n=0;n<512;n++)full.lookup(raw(n),12);full.cache.variableReplacement(Integer.MAX_VALUE);int beforeQueries=full.font.queries;reject(()->full.lookup(raw(900),12),"unowned native neighbor");check(full.cache.variableReplacement()==Integer.MIN_VALUE&&full.font.queries==beforeQueries+1,"replacement increment wraps signedDWORD and native GDI happens before unknown output clear");
        return count;
    }
    public static void main(String[] args)throws Exception{System.out.println("PASS Pc1GlyphCacheTests "+run());}
}
