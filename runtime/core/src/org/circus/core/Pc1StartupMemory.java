package org.circus.core;
import java.io.*;
import java.util.*;

/** Current verified CHS startup bytes. Physical handles are deliberately not
 *bound as invented DWORDs. Construction seeds BSS/PE bytes only; it performs
 *no registry read, screen change, window creation or successful startup. */
public final class Pc1StartupMemory {
    public static final int MODULE=0x94bc38,SAVE_ROOT=0x94bdc4,DATA_DIR=0x968bb8,INST_KEY=0x9691c4,
        CURRENT_DISPLAY=0x94bec8,REFRESH=0x94bd3c,REFRESH_COUNT=0x94c6b4,PREFERRED_BPP=0x94c6d0;
    private static final int FORMAT=0x5031534d,VERSION=1;
    private static final int[][] RANGES={{MODULE,260},{REFRESH,120},{SAVE_ROOT,260},{CURRENT_DISPLAY,148},
        {DATA_DIR,260},{INST_KEY,260},{REFRESH_COUNT,4},{0x94c6c0,20},{0x430290,0x118},{0x43cd2c,0x5c},{0x43cd98,0x78}};
    private final byte[][] raw=new byte[RANGES.length][];
    public Pc1StartupMemory(Pc1NativeMemory memory)throws IOException {
        if(memory==null)throw new IOException("PC1 startup requires canonical memory");
        byte[] seed=Base64.getDecoder().decode(SEED);int seedAt=0;
        for(int n=0;n<RANGES.length;n++){
            final byte[] cell=raw[n]=new byte[RANGES[n][1]];
            if(n>=8){System.arraycopy(seed,seedAt,cell,0,cell.length);seedAt+=cell.length;}
            memory.bind(RANGES[n][0],cell.length,new Pc1NativeMemory.Region(){public int read(int at){return cell[at]&255;}public void write(int at,int value){cell[at]=(byte)value;}});
        }
    }
    public void save(DataOutput out)throws IOException{out.writeInt(FORMAT);out.writeInt(VERSION);for(byte[] cell:raw)out.write(cell);}
    public static final class Image{private final byte[][] cells;private Image(byte[][] c){cells=c;}}
    public static Image loadImage(DataInput in)throws IOException{if(in.readInt()!=FORMAT||in.readInt()!=VERSION)throw new IOException("PC1 startup memory format");byte[][] c=new byte[RANGES.length][];for(int n=0;n<c.length;n++){c[n]=new byte[RANGES[n][1]];in.readFully(c[n]);}return new Image(c);}
    public void restore(Image image)throws IOException{if(image==null)throw new IOException("PC1 startup memory image missing");for(int n=0;n<raw.length;n++)System.arraycopy(image.cells[n],0,raw[n],0,raw[n].length);}
    private static final String SEED="utrM5QAAAAAAAAAAAAAAAGRhdFxuYW1lcy5kYXSkrNVppOGkyqSkpKvtl8S/yv2krN9gpKSk3qS5AAAApaKl86WkpfOluaXIqWCl68fpiPOkrNKKpMSkq6TqpN6ku6TzAAAAAKPEo66jw6Ouo9CjrqPDo64AAAAALVUAAERDUENfRVgARENQQ19DTEFTUwAApdCpYKW4peel88fpiPMAACVzIFZlci4gJXMNCqGhRGF0YVZlciAlZC4lMDJkAAAAMS4wMAAAAABtb2RlIGNoZyB0ZW1wAAAAODAweDYwMHgxNkJpdNLUyc+kx8bwhNOkt6TGz8KktaSkoaMKRnVsbFNjcmVlbqTHxvCE06S3pN6kuaSro78AAC4AAABcQWR2RGF0YVxTYXZlRGF0YQAAAFVzZVdNVgAATm9Nb3ZpZQBVc2VEaXJlY3RYAABJbnN0S2V5AERhdGFEaXIAU29mdHdhcmVcQ0lSQ1VTXERDUENEVkQARC5DLlAuQy6kzkRWRKTypbulw6XIpLekxqSvpMCktaSkAAAAUToAAERDUENfRFZEAAAAAKW7qWCl1u5J0/KkrKSipOqk3qS7pPMK1f2kt6SvpaSl86W5pcipYKXrpLekxqSvpMCktaSkAAAAXHNhdmV0ZXN0AAAA";
}
