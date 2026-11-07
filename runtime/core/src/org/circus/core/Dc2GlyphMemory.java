package org.circus.core;

import java.io.*;

/** Current d474DC2 canonical BSS glyph/cache/TEXTMETRICA bytes. Cache and its
 *56-byte metrics neighbor are one actual contiguous owner. Opaque HDC/font/
 *heap pointers stay at the required provider/backend boundary. */
public final class Dc2GlyphMemory implements Dc2Rain.State,NativeGlyphCache.Memory {
    private static final int FORMAT=0x4432474d; //D2GM
    public static final int CACHE=0x4aca30,FIXED=0x575a30,TEXT_METRICS=0x5a6e30,METRICS_BYTES=56,CACHE_END=TEXT_METRICS;
    public static final int FONT_REQUESTED=0x968d0c,FIXED_CURSOR=0x968d10,VARIABLE_CURSOR=0x968d14;
    public static final int JOURNAL=0xbe0e70,JOURNAL_BYTES=32000,COUNT=0xbe8cbc,SHADE=0x473f84;
    public interface FontState {int requestedSize()throws IOException;void requestedSize(int value)throws IOException;byte[] textMetrics()throws IOException;void textMetrics(byte[] bytes)throws IOException;}
    private static final int[] BASES={CACHE,FONT_REQUESTED,JOURNAL,COUNT},LENGTHS={TEXT_METRICS+METRICS_BYTES-CACHE,12,JOURNAL_BYTES,4};
    private final byte[][] raw=new byte[LENGTHS.length][];
    private final NativeGlyphCache.Memory external;
    private int calls;
    public Dc2GlyphMemory(){this(null);}
    public Dc2GlyphMemory(NativeGlyphCache.Memory external){this.external=external;for(int n=0;n<raw.length;n++)raw[n]=new byte[LENGTHS[n]];}
    private int region(int address){for(int n=0;n<BASES.length;n++)if(address>=BASES[n]&&address<BASES[n]+LENGTHS[n])return n;return-1;}
    public boolean ownsAddress(int address){return region(address)>=0;}
    /**Defensive inspection of a wholly owned contiguous region; borrowed
     *neighbors must keep their real owner and are not zero-padded here. */
    public byte[] ownedBytes(int address,int length)throws IOException{int n=region(address);if(n<0||length<0||(long)address+length>BASES[n]+LENGTHS[n])throw unknown(address);byte[] bytes=new byte[length];System.arraycopy(raw[n],address-BASES[n],bytes,0,length);return bytes;}
    public int readByte(int address)throws IOException{int n=region(address);if(n>=0)return raw[n][address-BASES[n]]&255;if(external!=null)return external.readByte(address)&255;throw unknown(address);}
    public void writeByte(int address,int value)throws IOException{int n=region(address);if(n>=0){raw[n][address-BASES[n]]=(byte)value;return;}if(external!=null){external.writeByte(address,value&255);return;}throw unknown(address);}
    private static IOException unknown(int address){return new IOException("DC2 glyph read/write reaches unowned native neighbor0x"+Integer.toHexString(address));}
    public int read(int address)throws IOException{int value=0;for(int n=0;n<4;n++)value|=readByte(address+n)<<(n*8);return value;}
    public void write(int address,int value)throws IOException{for(int n=0;n<4;n++)readByte(address+n);for(int n=0;n<4;n++)writeByte(address+n,value>>>(n*8));}
    public final FontState fontState=new FontState(){
        public int requestedSize()throws IOException{return read(FONT_REQUESTED);}
        public void requestedSize(int value)throws IOException{write(FONT_REQUESTED,value);}
        public byte[] textMetrics(){byte[] bytes=new byte[METRICS_BYTES];System.arraycopy(raw[0],TEXT_METRICS-CACHE,bytes,0,bytes.length);return bytes;}
        public void textMetrics(byte[] bytes)throws IOException{if(bytes==null||bytes.length!=METRICS_BYTES)throw new IOException("DC2 actual TEXTMETRICA requires56bytes");System.arraycopy(bytes,0,raw[0],TEXT_METRICS-CACHE,bytes.length);}
    };
    void begin(){calls++;}void end(){calls--;}
    public boolean busy(){return calls!=0;}
    static final class View {final byte[] bytes;final int offset;View(byte[] bytes,int offset){this.bytes=bytes;this.offset=offset;}}
    View knownView(int address)throws IOException{int n=region(address);if(n<0)throw new IOException("DC2 glyph coverage requires actual known contiguous view at0x"+Integer.toHexString(address));return new View(raw[n],address-BASES[n]);}
    public void save(DataOutput out)throws IOException{if(busy())throw new IOException("DC2 glyph snapshot inside native cache/font/draw callback");out.writeInt(FORMAT);for(byte[] bytes:raw)out.write(bytes);}
    public static final class Image{private final byte[][] raw;private Image(byte[][] raw){this.raw=raw;}}
    public static Image load(DataInput in)throws IOException{if(in.readInt()!=FORMAT)throw new IOException("DC2 glyph memory format");byte[][] bytes=new byte[LENGTHS.length][];for(int n=0;n<bytes.length;n++){bytes[n]=new byte[LENGTHS[n]];in.readFully(bytes[n]);}return new Image(bytes);}
    public void restore(Image image)throws IOException{if(busy())throw new IOException("DC2 glyph restore inside native callback");if(image==null)throw new IOException("DC2 glyph memory image missing");for(int n=0;n<raw.length;n++)System.arraycopy(image.raw[n],0,raw[n],0,raw[n].length);}
}
