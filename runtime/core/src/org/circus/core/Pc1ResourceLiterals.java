package org.circus.core;

import java.io.*;

/** Exact currentCHS resource literals in writable.data, plus the PE zero-fill
 *empty-string global. Consumers borrow these bytes rather than immutable Java
 *extensions/prefixes. Native aliases and inactive alignment bytes are saved. */
public final class Pc1ResourceLiterals {
    private static final int FORMAT=0x50314c33;
    public static final int VOICE_ARCHIVE=0x43dd88,PCM_EXTENSION=0x43dd94,VOICE_PREFIX=0x43de04,WAV_EXTENSION=0x43de9c,MIDI_DIRECTORY=0x43de38,EMPTY=0x94bc30;
    public static final int SACRA02=0x43d06c,SACRA01=0x43d07c,YUKI01=0x43d08c,RAINCHIP=0x43d09c;
    public static final int GRAPHICS_STRINGS=0x42a048,GRAPHICS_CATEGORY=0x42a04c,GRAPHICS_EXTENSION=0x42a050;
    private static final int[] ADDRESS={VOICE_ARCHIVE,PCM_EXTENSION,VOICE_PREFIX,WAV_EXTENSION,MIDI_DIRECTORY,EMPTY,SACRA02,SACRA01,YUKI01,RAINCHIP,GRAPHICS_STRINGS};
    private static final byte[][] SEED={
        {'V','O','I','C','E','.','D','A','T',0,0,0},
        {'.','p','c','m',0,0,0,0},
        {'V','O','I','C','E','\\',0,0},
        {'.','w','a','v',0,0,0,0},
        {'M','I','D','I',0,0,0,0},
        {0,0,0,0},
        {'s','y','s','t','e','m','\\','S','A','C','R','A','0','2',0,0},
        {'s','y','s','t','e','m','\\','S','A','C','R','A','0','1',0,0},
        {'s','y','s','t','e','m','\\','Y','U','K','I','0','1',0,0,0},
        {'s','y','s','t','e','m','\\','R','A','I','N','C','H','I','P',0},
        {0,0,0,0,'G','R','P',0,'.','c','r','x',0,0,0,0}
    };
    private final byte[][] bytes=new byte[SEED.length][];
    public Pc1ResourceLiterals(Pc1NativeMemory memory)throws IOException{
        if(memory==null)throw new IOException("PC1 resource literals byte bus missing");
        for(int n=0;n<bytes.length;n++){final byte[] raw=bytes[n]=SEED[n].clone();memory.bind(ADDRESS[n],raw.length,new Pc1NativeMemory.Region(){public int read(int at){return raw[at]&255;}public void write(int at,int value){raw[at]=(byte)value;}});}
    }
    public void save(DataOutput out)throws IOException{out.writeInt(FORMAT);for(byte[] raw:bytes)out.write(raw);}
    public static byte[][] loadImage(DataInput in)throws IOException{if(in.readInt()!=FORMAT)throw new IOException("PC1 resource literal format");byte[][] raw=new byte[SEED.length][];for(int n=0;n<raw.length;n++){raw[n]=new byte[SEED[n].length];in.readFully(raw[n]);}return raw;}
    public void restore(byte[][] image)throws IOException{if(image==null||image.length!=bytes.length)throw new IOException("PC1 resource literal rows");for(int n=0;n<bytes.length;n++)if(image[n]==null||image[n].length!=bytes[n].length)throw new IOException("PC1 resource literal size");for(int n=0;n<bytes.length;n++)System.arraycopy(image[n],0,bytes[n],0,bytes[n].length);}
}
