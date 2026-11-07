package org.circus.core;

import java.io.*;

/** Reviewed current CHS CRX globals. Heap pointers/allocations are separate
 * required owners; in particular4449B4 is not a fabricated32-bit pointer. */
public final class Pc1CrxMemory implements Pc1CrxDecoder.NativeState {
    private static final int FORMAT=0x50315831;
    public static final int HEADER=0x42a030,MAGIC=0x42a044;
    public static final int BUFFER_COUNT=0x4409b0,BUFFER=0x4409b4,BUFFER_BYTES=0x4000;
    public static final int ROW_POINTER=0x4449b4,DESCRIPTOR=0x4449b8,WINDOW=0x4449bc,WINDOW_BYTES=65536;
    public static final int STRIDE=0x4549bc,ROWS_LEFT=0x4549c0,ROW_LEFT=0x4549c4;
    public static final int PALETTE=0x4549c8,PALETTE_BYTES=768,ROW_BYTES=0x454cc8,WINDOW_CURSOR=0x454ccc,BUFFER_POINTER=0x454cd0;
    private static final int[] BASES={HEADER,BUFFER_COUNT,DESCRIPTOR};
    private static final int[] SIZES={24,BUFFER_BYTES+4,0x454cd4-DESCRIPTOR};
    private final byte[][] bytes={new byte[SIZES[0]],new byte[SIZES[1]],new byte[SIZES[2]]};
    private final Pc1NativeMemory memory;
    private boolean active;
    public Pc1CrxMemory(Pc1NativeMemory memory)throws IOException{
        if(memory==null)throw new IOException("PC1 CRX canonical byte memory missing");this.memory=memory;
        byte[] initial={0x43,0x52,0x58,0x47,0,0,0,0,(byte)0x80,2,(byte)0xe0,1,0,0,1,0,0,1,0,0,0x43,0x52,0x58,0x47};
        System.arraycopy(initial,0,bytes[0],0,initial.length);
        for(int n=0;n<BASES.length;n++){final byte[] region=bytes[n];memory.bind(BASES[n],region.length,new Pc1NativeMemory.Region(){public int read(int at){return region[at]&255;}public void write(int at,int value){region[at]=(byte)value;}});}
    }
    public int read(int address)throws IOException{return memory.readAddress(address);}
    public int fieldAddress(int pc1FieldAddress){return pc1FieldAddress;}
    public void write(int address,int value)throws IOException{memory.writeAddress(address,value);}
    public int dword(int address)throws IOException{int value=0;for(int n=0;n<4;n++)value|=read(address+n)<<(n*8);return value;}
    public void dword(int address,int value)throws IOException{for(int n=0;n<4;n++)write(address+n,value>>>(n*8));}
    public int word(int address)throws IOException{return read(address)|read(address+1)<<8;}
    public void begin()throws IOException{if(active)throw new IOException("PC1 CRX loader reentry has no separate native globals");active=true;}
    public void end(){active=false;}
    public boolean active(){return active;}
    public void save(DataOutput out)throws IOException{if(active)throw new IOException("PC1 CRX snapshot inside native IO/allocation call");out.writeInt(FORMAT);for(byte[] raw:bytes)out.write(raw);}
    public static byte[] loadImage(DataInput in)throws IOException{if(in.readInt()!=FORMAT)throw new IOException("PC1 CRX globals format");int length=0;for(int size:SIZES)length+=size;byte[] image=new byte[length];in.readFully(image);return image;}
    public void restore(byte[] image)throws IOException{if(active)throw new IOException("PC1 CRX restore inside native IO/allocation call");int length=0;for(int size:SIZES)length+=size;if(image==null||image.length!=length)throw new IOException("PC1 CRX globals size");int at=0;for(byte[] raw:bytes){System.arraycopy(image,at,raw,0,raw.length);at+=raw.length;}}
}
