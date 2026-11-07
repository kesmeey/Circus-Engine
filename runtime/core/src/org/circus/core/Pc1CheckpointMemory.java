package org.circus.core;

import java.io.*;
import java.util.Arrays;

/**411670's two external BSS destinations. The4000-byte reservation ends
 *exactly at layout's9D3AF4 ruby-gap DWORD. Native indexed reads and oversized
 *read prefixes may alias another independently known bus owner; neither the
 *loaded count nor the reservation is an invented consumer bounds check. */
public final class Pc1CheckpointMemory {
    public static final int COUNT=0x9cca9c,TABLE=0x9d2b54,TABLE_BYTES=4000;
    private static final int FORMAT=0x50314350,VERSION=1; //P1CP
    private final Pc1NativeMemory bus;
    private final byte[] count=new byte[4],table=new byte[TABLE_BYTES];
    public Pc1CheckpointMemory(Pc1NativeMemory bus)throws IOException{
        if(bus==null)throw new IOException("PC1 checkpoint requires canonical byte bus");this.bus=bus;
        bind(COUNT,count);bind(TABLE,table);
    }
    private void bind(int address,final byte[] bytes)throws IOException{bus.bind(address,bytes.length,new Pc1NativeMemory.Region(){public int read(int p){return bytes[p]&255;}public void write(int p,int value){bytes[p]=(byte)value;}});}
    public int count()throws IOException{return readInt(COUNT);}
    /** Raw DWORD n*4 passed toCRT_read, not a Java allocation size. The CRT
     *read caller owns validation/result/error and supplies actual read bytes. */
    public int requestedTableBytes()throws IOException{return count()*4;}
    /** Actual first_read result prefix only. Short/empty reads preserve stale
     *count bytes; a failed call is represented by no bytes, never zero-fill. */
    public void applyCountRead(byte[] actualBytes)throws IOException{if(actualBytes==null||actualBytes.length>4)throw new IOException("PC1 checkpoint count_read prefix");for(int n=0;n<actualBytes.length;n++)bus.writeAddress(COUNT+n,actualBytes[n]);}
    /** Actual second_read prefix only, in native forward write order. Crosses
     *into adjacent owned layout bytes when present. An unknown neighbor fails
     *after retaining the already written prefix, without atomic rollback. */
    public void applyTableRead(byte[] actualBytes)throws IOException{if(actualBytes==null)throw new IOException("PC1 checkpoint table_read prefix");for(int n=0;n<actualBytes.length;n++)bus.writeAddress(TABLE+n,actualBytes[n]);}
    /** Successful parsed-source convenience, preserving the native inactive
     *suffix. Header bytes are original file bytes, not mutableheaderOffsets. */
    public void loadSource(MesScript source)throws IOException{
        if(source==null||!source.profile.id.equals("dcpc-2063")||source.profile.headerStride!=4)throw new IOException("PC1 checkpoint source requires native four-byte header");
        byte[] raw=source.originalHeaderBytes();if(raw.length<4)throw new IOException("PC1 checkpoint source lacks count");
        applyCountRead(Arrays.copyOf(raw,4));applyTableRead(Arrays.copyOfRange(raw,4,raw.length));
    }
    /**418220 applies7FFF to the raw ushort log word, then indexes directly.
     *No count admission is added. Known neighboring alias reads stay live. */
    public int offsetForLogWord(int rawWord)throws IOException{return readInt(TABLE+(rawWord&0x7fff)*4);}
    private int readInt(int address)throws IOException{return bus.readAddress(address)|bus.readAddress(address+1)<<8|bus.readAddress(address+2)<<16|bus.readAddress(address+3)<<24;}
    public byte[] tableBytes(){return table.clone();}
    public void save(DataOutput out)throws IOException{out.writeInt(FORMAT);out.writeInt(VERSION);out.write(count);out.write(table);}
    public static final class Image {private final byte[] count,table;private Image(byte[] count,byte[] table){this.count=count;this.table=table;}}
    public static Image loadImage(DataInput in)throws IOException{if(in.readInt()!=FORMAT||in.readInt()!=VERSION)throw new IOException("PC1 checkpoint raw image format");byte[] count=new byte[4],table=new byte[TABLE_BYTES];in.readFully(count);in.readFully(table);return new Image(count,table);}
    public void restore(Image image)throws IOException{if(image==null)throw new IOException("PC1 checkpoint image missing");System.arraycopy(image.count,0,count,0,4);System.arraycopy(image.table,0,table,0,TABLE_BYTES);}
}
