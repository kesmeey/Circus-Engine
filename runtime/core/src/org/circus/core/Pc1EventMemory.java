package org.circus.core;

import java.io.*;

/** Reviewed text/WndProc globals, all zero in the verified current PE.
 * Timer tokens and gates remain raw DWORDs; binding bytes does not create an
 * OS timer, initialize a window, or adopt an external audio timer owner. */
public final class Pc1EventMemory {
    private static final int FORMAT=0x50315733;
    private static final int[] ADDRESS={0xbb842c,0xbb840c,0xbb8410,0x94c6a4,0x94c670,0x94c698,0x94c6a8,0x94bdb4,0x94c66c,0x94c634,0x94c638,0x94c688,0x94c68c,0x94c694,0x94c678,0x94c67c,0x94c620,0x94c690,0x94c69c,0x94c6a0,0x94c6b0,0x94c6bc};
    private final byte[][] raw=new byte[ADDRESS.length][4];
    public Pc1EventMemory(Pc1NativeMemory memory)throws IOException{
        if(memory==null)throw new IOException("PC1 event globals byte owner missing");
        for(int n=0;n<ADDRESS.length;n++){final byte[] bytes=raw[n];memory.bind(ADDRESS[n],4,new Pc1NativeMemory.Region(){public int read(int at){return bytes[at]&255;}public void write(int at,int value){bytes[at]=(byte)value;}});}
    }
    public void save(DataOutput out)throws IOException{out.writeInt(FORMAT);for(byte[] bytes:raw)out.write(bytes);}
    public static byte[] loadImage(DataInput in)throws IOException{if(in.readInt()!=FORMAT)throw new IOException("PC1 event globals format");byte[] bytes=new byte[ADDRESS.length*4];in.readFully(bytes);return bytes;}
    public void restore(byte[] image)throws IOException{if(image==null||image.length!=ADDRESS.length*4)throw new IOException("PC1 event globals image size");for(int n=0;n<raw.length;n++)System.arraycopy(image,n*4,raw[n],0,4);}
}
