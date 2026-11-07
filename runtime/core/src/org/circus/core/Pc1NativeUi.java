package org.circus.core;

import java.io.*;
import java.util.*;

/** Current PC1 button/list BSS and PE globals, published as addressed bytes.
 * Quad templates, code, frame, geometry, settings and list offset keep their
 * existing owners. No portable button DTO is a native-memory substitute. */
public final class Pc1NativeUi implements Pc1Compositor.State {
    private static final int FORMAT=0x50314931;
    public static final int MAIN_BUTTONS=0x95af28,MAIN_CAPACITY=100,BUTTON_BYTES=0x230,COMPONENT_BYTES=0x28,COMPONENTS=12;
    public static final int STORY_BUTTONS=0x9cfc70,STORY_CAPACITY=18;
    public static final int BUTTON_POINTER=0x4305f4,BUTTON_COUNT=0x9689ec,SELECTED_BUTTON=0x94fa28;
    public static final int LIST_RECORDS=0x9555b8,LIST_CAPACITY=220,LIST_RECORD_BYTES=0x68,LIST_VISIBLE=0x95539c,LIST_COUNT=0x95554c;
    public static final int SHARED_TEXT=0x955568,LIST_OFFSET=0x9553e8;
    public interface Counters {
        Pc1DrawMemory.Dword storyPrefixCount(); //BB8450 existing runtime owner
        Pc1DrawMemory.Dword storyFullCount(); //9B8E1C existing runtime owner
    }
    private static final int[][] RANGES={
        {0x95539c,4},{0x9553c8,32},{0x9553fc,12},{0x955408,20*16},{0x955548,12},
        {SHARED_TEXT,80+LIST_CAPACITY*LIST_RECORD_BYTES},{MAIN_BUTTONS,MAIN_CAPACITY*BUTTON_BYTES},
        {0x9b8e20,4*BUTTON_BYTES},{0x9b9bf4,10*80},{0x9b9f20,10*BUTTON_BYTES},
        {0x9cb908,8*BUTTON_BYTES},{0x9ccba8,8*BUTTON_BYTES},{STORY_BUTTONS,STORY_CAPACITY*BUTTON_BYTES}
    };
    private static final int[] SCALARS={BUTTON_POINTER,0x4305f8,0x4305fc,0x430600,0x430604,0x430608,
        SELECTED_BUTTON,0x9689e8,BUTTON_COUNT,0x9689f0,0x9689f4,0x9689f8,0x9689fc,0x968a00,0x968a04,0x968a08,
        0xbb8444,0xbb8454,0xbb845c,0xbb8468,0xbb846c,0xbb8470,0xbb8478};
    private final Pc1NativeMemory memory;
    private final byte[][] raw=new byte[RANGES.length][];
    private final Map<Integer,int[]> scalars=new LinkedHashMap<>();
    public Pc1NativeUi(Pc1NativeMemory memory,Counters counters)throws IOException{
        if(memory==null||counters==null||counters.storyPrefixCount()==null||counters.storyFullCount()==null)throw new IOException("PC1 native UI canonical counters/byte owners missing");this.memory=memory;
        for(int n=0;n<RANGES.length;n++){final byte[] bytes=raw[n]=new byte[RANGES[n][1]];memory.bind(RANGES[n][0],bytes.length,new Pc1NativeMemory.Region(){public int read(int at){return bytes[at]&255;}public void write(int at,int value){bytes[at]=(byte)value;}});}
        for(int address:SCALARS){final int[] value={address==BUTTON_POINTER?MAIN_BUTTONS:address==0x4305f8||address==0x4305fc||address==0x430600?-1:address==0x430604||address==0x430608?255:0};scalars.put(address,value);bind(address,new Pc1DrawMemory.Dword(){public int read(){return value[0];}public void write(int next){value[0]=next;}});}
        bind(0xbb8450,counters.storyPrefixCount());bind(0x9b8e1c,counters.storyFullCount());
    }
    private void bind(int address,final Pc1DrawMemory.Dword value)throws IOException{memory.bind(address,4,new Pc1NativeMemory.Region(){public int read(int at)throws IOException{return value.read()>>>(at*8)&255;}public void write(int at,int next)throws IOException{int shift=at*8;value.write(value.read()&~(255<<shift)|(next&255)<<shift);}public int readInt32(int at)throws IOException{return value.read();}public void writeInt32(int at,int next)throws IOException{value.write(next);}});}
    public int unsignedByte(int address)throws IOException{return memory.readAddress(address);}
    @Override public int readInt(int address)throws IOException{return memory.readInt32(address);}
    @Override public void writeInt(int address,int value)throws IOException{memory.writeInt32(address,value);}
    @Override public void writeByte(int address,int value)throws IOException{memory.writeAddress(address,value&255);}
    public byte[] bytes(int address,int count)throws IOException{if(count<0||count>1024*1024)throw new IOException("PC1 UI raw byte range");byte[] value=new byte[count];for(int n=0;n<count;n++)value[n]=(byte)memory.readAddress(address+n);return value;}
    /** Byte-exact C source; limits bound host allocation, not native indexing. */
    public byte[] cstring(int address)throws IOException{ByteArrayOutputStream out=new ByteArrayOutputStream();for(int n=0;n<1024*1024;n++){int b=memory.readAddress(address+n);if(b==0)return out.toByteArray();out.write(b);}throw new IOException("PC1 UI raw C source exceeds known host bound");}
    public void cstring(int address,byte[] value)throws IOException{if(value==null)throw new IOException("PC1 UI raw C source missing");int n=0;while(n<value.length&&value[n]!=0){memory.writeAddress(address+n,value[n]&255);n++;}memory.writeAddress(address+n,0);}
    /** Current button pointer is genuine state, not necessarily MAIN_BUTTONS. */
    public int button(int index)throws IOException{return readInt(BUTTON_POINTER)+index*BUTTON_BYTES;}
    public void save(DataOutput out)throws IOException{out.writeInt(FORMAT);for(byte[] bytes:raw)out.write(bytes);for(int[] value:scalars.values())out.writeInt(value[0]);}
    public static final class Image {private final byte[][] raw;private final int[] scalars;private Image(byte[][] raw,int[] scalars){this.raw=raw;this.scalars=scalars;}}
    public static Image load(DataInput in)throws IOException{if(in.readInt()!=FORMAT)throw new IOException("PC1 native UI format");byte[][] raw=new byte[RANGES.length][];for(int n=0;n<raw.length;n++){raw[n]=new byte[RANGES[n][1]];in.readFully(raw[n]);}int[] values=new int[SCALARS.length];for(int n=0;n<values.length;n++)values[n]=in.readInt();return new Image(raw,values);}
    /** External canonical story counters are deliberately not rolled back. */
    public void restore(Image image)throws IOException{if(image==null)throw new IOException("PC1 native UI image missing");for(int n=0;n<raw.length;n++)System.arraycopy(image.raw[n],0,raw[n],0,raw[n].length);int n=0;for(int[] value:scalars.values())value[0]=image.scalars[n++];}
}
