package org.circus.core;

import java.io.*;

/** Reviewed411670 globals and dispatch's deferred script/movie C string.
 * The1024-byte reservation is a known BSS owner, NOT a native strcpy limit.
 * Copies crossing it continue through independently bound bus neighbors. */
public final class Pc1ScriptMemory {
    public static final int DEFERRED_PATH=0x9cb508,DEFERRED_PATH_BYTES=1024,CODE_READ_RESULT=0x9b9bec;
    public static final int PENDING_LABEL=0xbb847c;
    public static final int ERROR_LABEL=0x43d8e4,ERROR_VERSION=0x43d8fc,ERROR_MAGIC=0x43d930,ERROR_OPEN=0x43d950,CATEGORY=0x43d96c,EXTENSION=0x43d970;
    private static final int FORMAT=0x50314a31,VERSION=3;
    //BB84BC is already owned byPc1Timer5.Memory's callback/suffix span.
    private static final int[][] RANGES={{DEFERRED_PATH,DEFERRED_PATH_BYTES},{CODE_READ_RESULT,4},{PENDING_LABEL,32},{ERROR_LABEL,0x94}};
    private static final String SEED="a5e9a5d9a5eba4acd28aa4c4a4aba4eaa4dea4bba4f30000a5c7a960a5bfa4acdf60a4a4a4dea4b9a1a3d5fda4b7a4afa5a4a5f3a5b9a5c8a960a5eba4b7a4c6a4afa4c0a4b5a4a4a1a300004d4553a5d5a5a1a5a4a5eba4f2b8fcd0c2a4b7a4c6a4afa4c0a4b5a4a40000004d4553a5d5a5a1a5a4a5eba4aca4dfa4c4a4aba4eaa4dea4bba4f3004d4553002e6d657300000000";
    private final byte[][] bytes=new byte[RANGES.length][];
    private final Pc1NativeMemory memory;
    private int depth;
    public Pc1ScriptMemory(Pc1NativeMemory memory)throws IOException{
        if(memory==null)throw new IOException("PC1 script globals require canonical bus");this.memory=memory;
        for(int n=0;n<RANGES.length;n++){final byte[] cell=bytes[n]=new byte[RANGES[n][1]];if(n==RANGES.length-1)for(int p=0;p<cell.length;p++)cell[p]=(byte)Integer.parseInt(SEED.substring(p*2,p*2+2),16);memory.bind(RANGES[n][0],cell.length,new Pc1NativeMemory.Region(){public int read(int at){return cell[at]&255;}public void write(int at,int value){cell[at]=(byte)value;}});}
    }
    void begin(){depth++;}
    void end(){depth--;}
    void requireBus(Pc1NativeMemory expected)throws IOException{if(memory!=expected)throw new IOException("PC1 script globals belong to another canonical bus");}
    public void requireBoundary()throws IOException{if(depth!=0)throw new IOException("PC1 script snapshot during native stack/I-O callback");}
    public void save(DataOutput out)throws IOException{requireBoundary();out.writeInt(FORMAT);out.writeInt(VERSION);for(byte[] cell:bytes)out.write(cell);}
    public static final class Image {private final byte[][] cells;private Image(byte[][] cells){this.cells=cells;}}
    public static Image loadImage(DataInput in)throws IOException{if(in.readInt()!=FORMAT||in.readInt()!=VERSION)throw new IOException("PC1 script globals format");byte[][] cells=new byte[RANGES.length][];for(int n=0;n<cells.length;n++){cells[n]=new byte[RANGES[n][1]];in.readFully(cells[n]);}return new Image(cells);}
    public void restore(Image image)throws IOException{requireBoundary();if(image==null)throw new IOException("PC1 script globals image missing");for(int n=0;n<bytes.length;n++)System.arraycopy(image.cells[n],0,bytes[n],0,bytes[n].length);}
}
