package org.circus.core;

import java.io.*;

/** Current originalDC2 CRX persistent globals at their actual addresses.
 *The shared decoder's named PC1 field tokens translate explicitly;read/write
 *and stored buffer-pointer values always use DC2's native byte namespace.
 *No second PC1 ring/frame or synthetic heap pointer is introduced. */
public final class Dc2CrxMemory implements Pc1CrxDecoder.NativeState {
    private static final int FORMAT=0x44325831; //D2X1
    public static final int HEADER=0x42d030,MAGIC=0x42d044,BUFFER_COUNT=0x45fc50,BUFFER=0x45fc54;
    public static final int ROW_POINTER=0x463c54,DESCRIPTOR=0x463c58,WINDOW=0x463c5c;
    public static final int STRIDE=0x473c5c,ROWS_LEFT=0x473c60,ROW_LEFT=0x473c64,PALETTE=0x473c68;
    public static final int ROW_BYTES=0x473f68,WINDOW_CURSOR=0x473f6c,BUFFER_POINTER=0x473f70;
    private static final int[] BASES={HEADER,BUFFER_COUNT,DESCRIPTOR},SIZES={24,16388,0x473f74-DESCRIPTOR};
    public interface Bytes{int read(int nativeAddress)throws IOException;void write(int nativeAddress,int value)throws IOException;}
    private final byte[][] raw={new byte[SIZES[0]],new byte[SIZES[1]],new byte[SIZES[2]]};
    private final Bytes neighbors;
    private boolean active;
    public Dc2CrxMemory(){this(null);}
    /**Unknown neighboring aliases may use an explicitly reviewed same-bus
     *owner;absence raises an exact boundary instead of manufacturing zeros. */
    public Dc2CrxMemory(Bytes neighbors){this.neighbors=neighbors;byte[] initial={0x43,0x52,0x58,0x47,0,0,0,0,(byte)0x80,2,(byte)0xe0,1,0,0,1,0,0,1,0,0,0x43,0x52,0x58,0x47};System.arraycopy(initial,0,raw[0],0,24);}
    public int fieldAddress(int token)throws IOException{
        if(token>=Pc1CrxMemory.HEADER&&token<Pc1CrxMemory.HEADER+24)return token+0x3000;
        if(token>=Pc1CrxMemory.BUFFER_COUNT&&token<0x454cd4)return token+0x1f2a0;
        throw new IOException("DC2 CRX unknown shared field token at0x"+Integer.toHexString(token));
    }
    public int read(int address)throws IOException{for(int n=0;n<BASES.length;n++)if(address>=BASES[n]&&address<BASES[n]+SIZES[n])return raw[n][address-BASES[n]]&255;if(neighbors!=null)return neighbors.read(address)&255;throw unknown(address);}
    public void write(int address,int value)throws IOException{for(int n=0;n<BASES.length;n++)if(address>=BASES[n]&&address<BASES[n]+SIZES[n]){raw[n][address-BASES[n]]=(byte)value;return;}if(neighbors!=null){neighbors.write(address,value&255);return;}throw unknown(address);}
    private static IOException unknown(int address){return new IOException("DC2 CRX alias reaches unowned native heap/neighbor at0x"+Integer.toHexString(address));}
    public int word(int address)throws IOException{return read(address)|read(address+1)<<8;}
    public int dword(int address)throws IOException{int value=0;for(int n=0;n<4;n++)value|=read(address+n)<<(n*8);return value;}
    public void dword(int address,int value)throws IOException{for(int n=0;n<4;n++)write(address+n,value>>>(n*8));}
    public void begin()throws IOException{if(active)throw new IOException("DC2 CRX loader reentry shares native globals");active=true;}
    public void end(){active=false;}
    public boolean active(){return active;}
    public void save(DataOutput out)throws IOException{if(active)throw new IOException("DC2 CRX snapshot inside native IO/allocation call");out.writeInt(FORMAT);for(byte[] bytes:raw)out.write(bytes);}
    public static byte[] loadImage(DataInput in)throws IOException{if(in.readInt()!=FORMAT)throw new IOException("DC2 CRX format");int length=0;for(int n:SIZES)length+=n;byte[] bytes=new byte[length];in.readFully(bytes);return bytes;}
    public void restore(byte[] image)throws IOException{if(active)throw new IOException("DC2 CRX restore inside native IO/allocation call");int length=0;for(int n:SIZES)length+=n;if(image==null||image.length!=length)throw new IOException("DC2 CRX raw image size");int offset=0;for(byte[] bytes:raw){System.arraycopy(image,offset,bytes,0,bytes.length);offset+=bytes.length;}}
}
