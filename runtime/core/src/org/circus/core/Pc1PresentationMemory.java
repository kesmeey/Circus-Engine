package org.circus.core;

import java.io.*;

/** Reviewed presentation DWORDs shared by4186F0 and text/timer consumers.
 * PE/BSS seeding is independent of platform timer and window initialization. */
public final class Pc1PresentationMemory {
    private static final int FORMAT=0x50315432;
    private static final int[] ADDRESS={0x430284,0xbb8414,0x43d410,0xbb84e4,0x9cca8c,0xbb8548,0xbb8458};
    private final byte[][] raw=new byte[ADDRESS.length][4];
    public Pc1PresentationMemory(Pc1NativeMemory memory)throws IOException{
        if(memory==null)throw new IOException("PC1 presentation byte owner missing");
        for(int n=0;n<ADDRESS.length;n++){
            final byte[] bytes=raw[n];int value=n==0?40:n==2?-1:0;
            for(int b=0;b<4;b++)bytes[b]=(byte)(value>>>(b*8));
            memory.bind(ADDRESS[n],4,new Pc1NativeMemory.Region(){public int read(int at){return bytes[at]&255;}public void write(int at,int value){bytes[at]=(byte)value;}});
        }
    }
    public void save(DataOutput out)throws IOException{out.writeInt(FORMAT);for(byte[] bytes:raw)out.write(bytes);}
    int selectedHistory(){int value=0;for(int n=0;n<4;n++)value|=(raw[2][n]&255)<<(n*8);return value;}
    void selectedHistory(int value){for(int n=0;n<4;n++)raw[2][n]=(byte)(value>>>(n*8));}
    public static byte[] loadImage(DataInput in)throws IOException{if(in.readInt()!=FORMAT)throw new IOException("PC1 presentation globals format");byte[] bytes=new byte[ADDRESS.length*4];in.readFully(bytes);return bytes;}
    public void restore(byte[] image)throws IOException{if(image==null||image.length!=ADDRESS.length*4)throw new IOException("PC1 presentation globals image size");for(int n=0;n<raw.length;n++)System.arraycopy(image,n*4,raw[n],0,4);}
}
