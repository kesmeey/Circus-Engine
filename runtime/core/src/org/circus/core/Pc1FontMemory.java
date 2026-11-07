package org.circus.core;

import java.io.*;
import java.util.Arrays;

/** Canonical currentCHS cache/TEXTMETRICA/requested/cursor bytes. Physical
 *HFONT/HDC and monoheap remain platform owners, never fabricated DWORDs. */
public final class Pc1FontMemory implements NativeGlyphCache.Memory {
    private static final int FORMAT=0x5031464d,VERSION=1;
    public static final int BASE=0x48d590,FIXED=0x556590,METRICS=0x587990,METRICS_BYTES=56,END=METRICS+56;
    public static final int REQUESTED=0x94986c,FIXED_CURSOR=0x949870,VARIABLE_CURSOR=0x949874;
    public static final int LITERALS=0x43d9fc;
    private final byte[] cache=new byte[END-BASE],scalars=new byte[12],literals={0x3f,0x3f,0,0,(byte)0xa8,(byte)0x8b,0,0};
    private Pc1NativeMemory bus;private int bound,calls;
    public Pc1FontMemory(){}
    public void bindTo(Pc1NativeMemory memory)throws IOException{
        if(memory==null||bus!=null&&bus!=memory)throw new IOException("PC1 font memory must bind one canonical bus");bus=memory;
        if(bound==0){memory.bind(BASE,cache.length,new Pc1NativeMemory.Region(){public int read(int at){return cache[at]&255;}public void write(int at,int value){cache[at]=(byte)value;}});bound=1;}
        if(bound==1){memory.bind(REQUESTED,12,new Pc1NativeMemory.Region(){public int read(int at){return scalars[at]&255;}public void write(int at,int value){scalars[at]=(byte)value;}});bound=2;}
        if(bound==2){memory.bind(LITERALS,8,new Pc1NativeMemory.Region(){public int read(int at){return literals[at]&255;}public void write(int at,int value){literals[at]=(byte)value;}});bound=3;}
    }
    public Pc1NativeMemory nativeMemory(){return bus;}
    public boolean busy(){return calls!=0;}void begin(){calls++;}void end(){calls--;}
    public int readByte(int at)throws IOException{if(at>=BASE&&at<END)return cache[at-BASE]&255;if(at>=REQUESTED&&at<REQUESTED+12)return scalars[at-REQUESTED]&255;if(at>=LITERALS&&at<LITERALS+8)return literals[at-LITERALS]&255;if(bus!=null)return bus.readAddress(at);throw unknown(at);}
    public void writeByte(int at,int value)throws IOException{if(at>=BASE&&at<END)cache[at-BASE]=(byte)value;else if(at>=REQUESTED&&at<REQUESTED+12)scalars[at-REQUESTED]=(byte)value;else if(at>=LITERALS&&at<LITERALS+8)literals[at-LITERALS]=(byte)value;else if(bus!=null)bus.writeAddress(at,value&255);else throw unknown(at);}
    private static IOException unknown(int at){return new IOException("PC1 font/cache access reaches unowned native neighbor0x"+Integer.toHexString(at));}
    public int readInt(int at)throws IOException{int value=0;for(int n=0;n<4;n++)value|=readByte(at+n)<<(n*8);return value;}
    public void writeInt(int at,int value)throws IOException{for(int n=0;n<4;n++)readByte(at+n);for(int n=0;n<4;n++)writeByte(at+n,value>>>(n*8));}
    public int requestedSize(){return scalar(0);}public void requestedSize(int value){scalar(0,value);}
    public int fixedReplacement(){return scalar(4);}public void fixedReplacement(int value){scalar(4,value);}
    public int variableReplacement(){return scalar(8);}public void variableReplacement(int value){scalar(8,value);}
    private int scalar(int at){return(scalars[at]&255)|(scalars[at+1]&255)<<8|(scalars[at+2]&255)<<16|scalars[at+3]<<24;}
    private void scalar(int at,int value){for(int n=0;n<4;n++)scalars[at+n]=(byte)(value>>>(n*8));}
    public byte[] textMetrics(){return Arrays.copyOfRange(cache,METRICS-BASE,cache.length);}
    public void textMetrics(byte[] bytes)throws IOException{if(bytes==null||bytes.length!=56)throw new IOException("PC1 TEXTMETRICA needs56knownbytes");System.arraycopy(bytes,0,cache,METRICS-BASE,56);}
    public int ascent()throws IOException{return readInt(METRICS+4);}
    public byte[] cacheBytes(){return Arrays.copyOf(cache,METRICS-BASE);}
    void importCache(byte[] bytes)throws IOException{if(bytes==null||bytes.length!=METRICS-BASE)throw new IOException("PC1 glyph cache requires complete1025024-byte owner");System.arraycopy(bytes,0,cache,0,bytes.length);}
    static final class View{final byte[] bytes;final int offset;View(byte[] bytes,int offset){this.bytes=bytes;this.offset=offset;}}
    View view(int at,int length)throws IOException{if(at<BASE||length<0||(long)at+length>END)throw unknown(at);return new View(cache,at-BASE);}
    public byte[] ownedBytes(int at,int length)throws IOException{View view=view(at,length);return Arrays.copyOfRange(view.bytes,view.offset,view.offset+length);}
    public void save(DataOutput out)throws IOException{if(busy())throw new IOException("PC1 font snapshot during native cache/font/draw callback");out.writeInt(FORMAT);out.writeInt(VERSION);out.write(cache);out.write(scalars);out.write(literals);}
    public static final class Image{private final byte[] cache,scalars,literals;private Image(byte[] cache,byte[] scalars,byte[] literals){this.cache=cache;this.scalars=scalars;this.literals=literals;}}
    public static Image load(DataInput in)throws IOException{if(in.readInt()!=FORMAT||in.readInt()!=VERSION)throw new IOException("PC1 font memory format");byte[] cache=new byte[END-BASE],scalars=new byte[12],literals=new byte[8];in.readFully(cache);in.readFully(scalars);in.readFully(literals);return new Image(cache,scalars,literals);}
    public void restore(Image image)throws IOException{if(busy()||image==null)throw new IOException("PC1 font restore outside native boundary");System.arraycopy(image.cache,0,cache,0,cache.length);System.arraycopy(image.scalars,0,scalars,0,12);System.arraycopy(image.literals,0,literals,0,8);}
    public boolean matchesImage(Image image)throws IOException{if(busy()||image==null)throw new IOException("PC1 font comparison outside native boundary");return Arrays.equals(cache,image.cache)&&Arrays.equals(scalars,image.scalars)&&Arrays.equals(literals,image.literals);}
    public Pc1FontMemory copy()throws IOException{if(busy())throw new IOException("PC1 font copy during native callback");Pc1FontMemory copy=new Pc1FontMemory();System.arraycopy(cache,0,copy.cache,0,cache.length);System.arraycopy(scalars,0,copy.scalars,0,12);System.arraycopy(literals,0,copy.literals,0,8);return copy;}
}
