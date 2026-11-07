package org.circus.core;

import java.io.*;

/** External412160/1C globals. Four8004-byte frames and list-page backups
 * retain their existing canonical owners; these cells never copy a DTO. */
public final class Pc1SystemMemory {
    public static final int CAPTURE=0x43d42c,SAVED_BACKGROUND=0x9bb500,SAVED_HISTORY=0x9bb504;
    public static final int SAVED_VISIBILITY=0x9da4cc; //immediately AFTER live8004
    public static final int ENTER_LABEL=0x43d978,REDRAW_LABEL=0x43d98c,NEW_LABEL=0x43d998;
    private static final int FORMAT=0x5031534e,VERSION=1;
    private static final int[][] RANGES={{CAPTURE,4},{SAVED_BACKGROUND,8},{SAVED_VISIBILITY,4},{ENTER_LABEL,40}};
    private final byte[][] bytes=new byte[RANGES.length][];private final Pc1NativeMemory bus;private int depth;
    public Pc1SystemMemory(Pc1NativeMemory bus)throws IOException{
        if(bus==null)throw new IOException("PC1 system globals require canonical bus");this.bus=bus;
        final byte[] literals={'c','a','l','l',' ','n','e','w','b','a','c','k',0,0,0,0,'%','d',0,0,'m','s','g',' ','r','e','d','r','a','w',0,0,'n','e','w',0,'-','-',0,0};
        for(int n=0;n<RANGES.length;n++){final byte[] cell=bytes[n]=new byte[RANGES[n][1]];if(n==0)java.util.Arrays.fill(cell,(byte)255);else if(n==RANGES.length-1)System.arraycopy(literals,0,cell,0,cell.length);bus.bind(RANGES[n][0],cell.length,new Pc1NativeMemory.Region(){public int read(int at){return cell[at]&255;}public void write(int at,int value){cell[at]=(byte)value;}});}
    }
    void requireBus(Pc1NativeMemory value)throws IOException{if(value!=bus)throw new IOException("PC1 system globals belong to another canonical bus");}
    void begin(){depth++;}void end(){depth--;}
    public void requireBoundary()throws IOException{if(depth!=0)throw new IOException("PC1 system snapshot during native local/callback");}
    public void save(DataOutput out)throws IOException{requireBoundary();out.writeInt(FORMAT);out.writeInt(VERSION);for(byte[] cell:bytes)out.write(cell);}
    public static final class Image {private final byte[][] bytes;private Image(byte[][] bytes){this.bytes=bytes;}}
    public static Image loadImage(DataInput in)throws IOException{if(in.readInt()!=FORMAT||in.readInt()!=VERSION)throw new IOException("PC1 system globals format");byte[][] raw=new byte[RANGES.length][];for(int n=0;n<raw.length;n++){raw[n]=new byte[RANGES[n][1]];in.readFully(raw[n]);}return new Image(raw);}
    public void restore(Image image)throws IOException{requireBoundary();if(image==null)throw new IOException("PC1 system globals image missing");for(int n=0;n<bytes.length;n++)System.arraycopy(image.bytes[n],0,bytes[n],0,bytes[n].length);}
}
