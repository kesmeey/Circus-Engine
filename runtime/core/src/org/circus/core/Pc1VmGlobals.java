package org.circus.core;

import java.io.*;

/** Reviewed external411910/411B10 DWORDs and the2B busy word. These are
 *neither portable register entries nor part ofthe8004-byte native frame. */
public final class Pc1VmGlobals {
    private static final int FORMAT=0x50314d32;
    public static final int RESULT0=0xbb8420,RANDOM_RESULT=0xbb8428,EFFECT_BUSY=0xbb8434,SET19=0x9b9f1c;
    public static final int SELECTION243=0x43d470,VALUE22=0x94c628,VALUE252=0x43d41c,DEVICE247=0x94c624;
    public static final int SYSTEM_LIST_OFFSET=0xbb843c;
    private static final int[] ADDRESS={RESULT0,RANDOM_RESULT,EFFECT_BUSY,SET19,SELECTION243,VALUE22,VALUE252,DEVICE247,SYSTEM_LIST_OFFSET};
    private final byte[][] raw=new byte[ADDRESS.length][4];
    public Pc1VmGlobals(Pc1NativeMemory memory)throws IOException{
        if(memory==null)throw new IOException("PC1 VM globals byte owner missing");
        for(int n=0;n<ADDRESS.length;n++){final byte[] bytes=raw[n];if(ADDRESS[n]==SELECTION243||ADDRESS[n]==VALUE252)java.util.Arrays.fill(bytes,(byte)255);memory.bind(ADDRESS[n],4,new Pc1NativeMemory.Region(){public int read(int at){return bytes[at]&255;}public void write(int at,int value){bytes[at]=(byte)value;}});}
    }
    public void save(DataOutput out)throws IOException{out.writeInt(FORMAT);for(byte[] bytes:raw)out.write(bytes);}
    public static byte[] loadImage(DataInput in)throws IOException{if(in.readInt()!=FORMAT)throw new IOException("PC1 VM globals format");byte[] bytes=new byte[ADDRESS.length*4];in.readFully(bytes);return bytes;}
    public void restore(byte[] image)throws IOException{if(image==null||image.length!=ADDRESS.length*4)throw new IOException("PC1 VM globals image size");for(int n=0;n<raw.length;n++)System.arraycopy(image,n*4,raw[n],0,4);}
}
