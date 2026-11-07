package org.circus.core;

import java.io.*;

/** Three contiguous260-byte path globals, zeroed by the PE loader. Startup
 *configuration/CD discovery writes them separately; no game-root default is
 *guessed here. Their inactive suffixes and cross-field aliases stay intact. */
public final class Pc1PathGlobals implements Pc1NativePaths.Memory {
    private static final int FORMAT=0x50315032;
    public static final int BASE=0x94c310,CELL_BYTES=260,BYTES=CELL_BYTES*3;
    public static final int CONFIGURED_DATA_ROOT=BASE+CELL_BYTES;
    private final byte[] bytes=new byte[BYTES];
    // These literal bytes reside in writable.data, not a read-only section.
    private final byte[] literals={'\\','A','d','v','D','a','t','a','\\',0,0,0,'\\',0,0,0};
    private final Pc1NativeMemory memory;
    public Pc1PathGlobals(Pc1NativeMemory memory)throws IOException{
        if(memory==null)throw new IOException("PC1 native path global memory missing");this.memory=memory;
        memory.bind(BASE,BYTES,new Pc1NativeMemory.Region(){public int read(int at){return bytes[at]&255;}public void write(int at,int value){bytes[at]=(byte)value;}});
        memory.bind(Pc1NativePaths.DATA_PREFIX,literals.length,new Pc1NativeMemory.Region(){public int read(int at){return literals[at]&255;}public void write(int at,int value){literals[at]=(byte)value;}});
    }
    public int read(int address)throws IOException{return memory.readAddress(address);}
    public void write(int address,int value)throws IOException{memory.writeAddress(address,value);}
    public void save(DataOutput out)throws IOException{out.writeInt(FORMAT);out.write(bytes);out.write(literals);}
    public static byte[] loadImage(DataInput in)throws IOException{if(in.readInt()!=FORMAT)throw new IOException("PC1 native path globals format");byte[] raw=new byte[BYTES+16];in.readFully(raw);return raw;}
    public void restore(byte[] image)throws IOException{if(image==null||image.length!=BYTES+literals.length)throw new IOException("PC1 native path globals size");System.arraycopy(image,0,bytes,0,BYTES);System.arraycopy(image,BYTES,literals,0,literals.length);}
}
