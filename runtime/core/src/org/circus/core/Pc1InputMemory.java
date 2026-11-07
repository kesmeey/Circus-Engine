package org.circus.core;
import java.io.*;

/** Reviewed currentCHS input/modal globals. Candidate/working/system frames,
 *timer tokens, UI tables, glyphs and settings are borrowed, never duplicated. */
public final class Pc1InputMemory {
    private static final int FORMAT=0x5031494d,VERSION=1;
    private static final int[][] RANGES={{0xbb841c,4},{0xbb8474,4},{0x9b9f14,4},{0xbb84f0,80},
        {0x9d8010,12},{0x9ccba0,4},{0x971440,32},{0x971460,4},{0x9dbabc,32},
        {0x94bc24,8},{0x968a60,24},{0x9d8564,36},{0x43da04,36}};
    private final byte[][] bytes=new byte[RANGES.length][];
    private static final byte[] LITERALS={'@','s',0,0,'j','i','s','h','o',0,0,0,'s','o','u','k','o',0,0,0,'t','a','n','a',0,0,0,0,'t','u','s','h','i','n',0,0};
    public Pc1InputMemory(Pc1NativeMemory memory)throws IOException{if(memory==null)throw new IOException("PC1 input rawmemory bus missing");for(int n=0;n<RANGES.length;n++){final byte[] cell=bytes[n]=new byte[RANGES[n][1]];if(n==RANGES.length-1)System.arraycopy(LITERALS,0,cell,0,cell.length);memory.bind(RANGES[n][0],cell.length,new Pc1NativeMemory.Region(){public int read(int at){return cell[at]&255;}public void write(int at,int value){cell[at]=(byte)value;}});}}
    public void save(DataOutput out)throws IOException{out.writeInt(FORMAT);out.writeInt(VERSION);for(byte[] b:bytes)out.write(b);}
    public static final class Image {private final byte[][] bytes;private Image(byte[][] b){bytes=b;}}
    public static Image load(DataInput in)throws IOException{if(in.readInt()!=FORMAT||in.readInt()!=VERSION)throw new IOException("PC1 input rawmemory format");byte[][] b=new byte[RANGES.length][];for(int n=0;n<b.length;n++){b[n]=new byte[RANGES[n][1]];in.readFully(b[n]);}return new Image(b);}
    public void restore(Image image)throws IOException{if(image==null)throw new IOException("PC1 input rawmemory image missing");for(int n=0;n<bytes.length;n++)System.arraycopy(image.bytes[n],0,bytes[n],0,bytes[n].length);}
}
