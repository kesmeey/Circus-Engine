package org.circus.core;

import java.io.*;
import java.util.*;

/** Current CHS layout's raw global owners. Production instances bind these
 * bytes once and borrow NativeUi encoded words, TextBuffers live strings,
 * Geometry and settings through the same addressed bus. No sync/repair pass. */
public final class Pc1LayoutMemory implements Pc1InlineText.Memory {
    public static final int GLYPHS=0xbb06f8,GLYPH_BYTES=32000,RUBY=0x9d8160,RUBY_BYTES=1024,
        RECORDS=0x9da4d0,RECORD_BYTES=5600,WORDS=0x9b9bf4,WORD_BYTES=800,
        GLYPH_COUNT=0xbb8540,DICT_COUNT=0x43d434,BODY_CURSOR=0x9d801c;
    private static final int FORMAT=0x50314c4d,VERSION=1;
    private static final int[][] OWNED={
        {GLYPHS,GLYPH_BYTES},{RUBY,RUBY_BYTES},{RECORDS,RECORD_BYTES},
        {Pc1InlineText.X,4},{Pc1InlineText.Y,4},{Pc1InlineText.FONT,4},{Pc1InlineText.LENGTH,4},
        {GLYPH_COUNT,4},{0xbb8544,4},{Pc1InlineText.RUBY_PHASE,4},{Pc1InlineText.RUBY_COUNT,4},
        {Pc1InlineText.BASE_COUNT,4},{Pc1InlineText.RUBY_CURSOR,4},{Pc1InlineText.RUBY_ORIGIN,4},
        {Pc1InlineText.RUBY_GAP,4},{DICT_COUNT,4},{BODY_CURSOR,4},{Pc1InlineText.STOP_WORD,2},{Pc1InlineText.SPLIT_WORD,2}
    };
    private final Pc1NativeMemory bus;
    private final byte[][] owned=new byte[OWNED.length][];
    private final TreeMap<Integer,byte[]> detached=new TreeMap<>();
    /** Loaded PE image defaults: BSS0, dictionary-count DATA=-1, two CHS
     * delimiter WORDs. No implicit font28/position or dictionary activation. */
    public Pc1LayoutMemory(Pc1NativeMemory bus)throws IOException{
        if(bus==null)throw new IOException("PC1 layout requires canonical native byte bus");this.bus=bus;initialize();
    }
    private Pc1LayoutMemory()throws IOException{bus=null;initialize();}
    private void initialize()throws IOException{
        for(int n=0;n<OWNED.length;n++){final byte[] bytes=owned[n]=new byte[OWNED[n][1]];
            if(bus==null)detached.put(OWNED[n][0],bytes);else bus.bind(OWNED[n][0],bytes.length,new Pc1NativeMemory.Region(){public int read(int p){return bytes[p]&255;}public void write(int p,int value){bytes[p]=(byte)value;}});
        }
        writeI32(DICT_COUNT,-1);write(Pc1InlineText.STOP_WORD,0xa3);write(Pc1InlineText.STOP_WORD+1,0xfd);write(Pc1InlineText.SPLIT_WORD,0xa3);write(Pc1InlineText.SPLIT_WORD+1,0xaf);
    }
    /** Explicit isolated compatibility/test owner. Its words/geometry are
     * local only because no production bus exists; never bind it afterwards. */
    public static Pc1LayoutMemory detached(Pc1TextLayout.Config config)throws IOException{
        if(config==null)throw new IOException("PC1 detached layout geometry missing");Pc1LayoutMemory memory=new Pc1LayoutMemory();memory.detached.put(WORDS,new byte[WORD_BYTES]);memory.detached.put(Pc1MessageGeometry.BODY_X,new byte[13*4]);memory.detached.put(Pc1InlineText.SHADING,new byte[4]);memory.configureDetached(config);memory.writeI32(Pc1InlineText.FONT,28);memory.writeI32(DICT_COUNT,0);return memory;
    }
    public boolean bound(){return bus!=null;}
    void configureDetached(Pc1TextLayout.Config c)throws IOException{
        if(bound())return;writeI32(Pc1MessageGeometry.BODY_X,c.bodyX);writeI32(Pc1MessageGeometry.BODY_PITCH,c.bodyPitch);writeI32(Pc1MessageGeometry.NAME_X,c.speakerX);writeI32(Pc1MessageGeometry.NAME_Y,c.speakerY);writeI32(Pc1MessageGeometry.NAME_HISTORY_AUX,c.speakerColumns);writeI32(Pc1MessageGeometry.BODY_STYLE,c.textStyle);
        write(Pc1InlineText.STOP_WORD,c.rubyStopWord);write(Pc1InlineText.STOP_WORD+1,c.rubyStopWord>>>8);write(Pc1InlineText.SPLIT_WORD,c.rubySplitWord);write(Pc1InlineText.SPLIT_WORD+1,c.rubySplitWord>>>8);
    }
    private byte[] local(int address)throws IOException{Map.Entry<Integer,byte[]> entry=detached.floorEntry(address);if(entry==null||(long)address-entry.getKey()<0||(long)address-entry.getKey()>=entry.getValue().length)throw new IOException("PC1 layout reaches unowned native neighbor at0x"+Integer.toHexString(address));return entry.getValue();}
    @Override public int read(int address)throws IOException{if(bound())return bus.readAddress(address);byte[] bytes=local(address);return bytes[address-detached.floorKey(address)]&255;}
    @Override public void write(int address,int value)throws IOException{if(bound()){bus.writeAddress(address,value);return;}byte[] bytes=local(address);bytes[address-detached.floorKey(address)]=(byte)value;}
    public int readI32(int address)throws IOException{return Pc1InlineText.readI32(this,address);}
    public void writeI32(int address,int value)throws IOException{for(int n=0;n<4;n++)read(address+n);Pc1InlineText.writeI32(this,address,value);}
    public int readU16(int address)throws IOException{return read(address)|read(address+1)<<8;}
    public byte[] bytes(int address,int length)throws IOException{if(length<0||length>1024*1024)throw new IOException("PC1 layout raw byte length");byte[] bytes=new byte[length];for(int n=0;n<length;n++)bytes[n]=(byte)read(address+n);return bytes;}
    public void writeBytes(int address,byte[] bytes)throws IOException{if(bytes==null)throw new IOException("PC1 layout raw source missing");for(int n=0;n<bytes.length;n++)write(address+n,bytes[n]);}
    /** Raw-image codec owns only OWNED above. Borrowed words, source bytes,
     * frame, geometry, settings and UI are deliberately absent. */
    public void save(DataOutput out)throws IOException{out.writeInt(FORMAT);out.writeInt(VERSION);for(byte[] bytes:owned)out.write(bytes);}
    public static final class Image {private final byte[][] bytes;private Image(byte[][] bytes){this.bytes=bytes;}}
    public static Image load(DataInput in)throws IOException{if(in.readInt()!=FORMAT||in.readInt()!=VERSION)throw new IOException("PC1 layout raw image format");byte[][] bytes=new byte[OWNED.length][];for(int n=0;n<bytes.length;n++){bytes[n]=new byte[OWNED[n][1]];in.readFully(bytes[n]);}return new Image(bytes);}
    public void restore(Image image)throws IOException{if(image==null)throw new IOException("PC1 layout raw image missing");for(int n=0;n<owned.length;n++)System.arraycopy(image.bytes[n],0,owned[n],0,owned[n].length);}
}
