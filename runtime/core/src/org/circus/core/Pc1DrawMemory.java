package org.circus.core;

import java.io.*;
import java.util.*;

/** Reviewed PC1 raw template BSS plus explicitly bound external DWORD views.
 * Frame, code, settings, UI origin, palette and geometry retain their owners;
 * this class never synchronizes a second scene after each VM instruction. */
public final class Pc1DrawMemory implements Pc1Compositor.State {
    private static final int FORMAT=0x50314532;
    private static final int[] BASE={0x9d7d90,0x9d8020,0x9d23d0,0x9d7ed0,0x9d80c0,0x9d2650,0x9d28d0};
    private static final int[] CAPACITY={10,5,20,10,5,20,20};
    private static final int[] COUNTS={0xbb84c8,0xbb84d0,0xbb84d8,0xbb84cc,0xbb84d4,0xbb84dc,0xbb84e0};
    private static final int[] OWNED={0x9b9f18,0xbb844c,0xbb8424,0xbb8440,0x43d46c};
    public interface Dword {int read()throws IOException;void write(int value)throws IOException;}
    private final Pc1NativeMemory memory;
    private final byte[][] templates=new byte[7][];
    private final Map<Integer,int[]> owned=new LinkedHashMap<>();
    public Pc1DrawMemory(Pc1NativeMemory memory)throws IOException{
        if(memory==null)throw new IOException("PC1 draw memory requires its addressed byte owners");this.memory=memory;
        for(int n=0;n<7;n++){final byte[] raw=templates[n]=new byte[CAPACITY[n]*32];memory.bind(BASE[n],raw.length,new Pc1NativeMemory.Region(){public int read(int at){return raw[at]&255;}public void write(int at,int value){raw[at]=(byte)value;}});}
        for(int address:COUNTS)own(address,0);for(int address:OWNED)own(address,address==0x43d46c?1:0);
    }
    private void own(int address,int initial)throws IOException{final int[] value={initial};owned.put(address,value);bind(address,new Dword(){public int read(){return value[0];}public void write(int next){value[0]=next;}});}
    public void bind(final int address,final Dword field)throws IOException{
        if(field==null)throw new IOException("PC1 draw DWORD binding missing");memory.bind(address,4,new Pc1NativeMemory.Region(){
            public int read(int at)throws IOException{return field.read()>>>(at*8)&255;}
            public void write(int at,int value)throws IOException{int shift=at*8;field.write((field.read()&~(255<<shift))|((value&255)<<shift));}
        });
    }
    @Override public int readInt(int address)throws IOException{int value=0;for(int n=0;n<4;n++)value|=memory.readAddress(address+n)<<(8*n);return value;}
    @Override public void writeInt(int address,int value)throws IOException{
        // Preflight the complete fixed DWORD before committing its bytes.
        for(int n=0;n<4;n++)memory.readAddress(address+n);for(int n=0;n<4;n++)memory.writeAddress(address+n,value>>>(8*n));
    }
    @Override public void writeByte(int address,int value)throws IOException{memory.writeAddress(address,value);}
    public List<Pc1MessageGeometry.Entry> template(int selector)throws IOException{
        if(selector<1||selector>7)throw new IOException("PC1 template selector outside reviewed seven groups");int index=selector-1,count=readInt(COUNTS[index]);List<Pc1MessageGeometry.Entry> rows=new ArrayList<>();
        for(int n=0;n<count;n++){int[] row=new int[8];for(int f=0;f<8;f++)row[f]=readInt(BASE[index]+n*32+f*4);rows.add(new Pc1MessageGeometry.Entry(row));}return rows;
    }
    /** Only this owner's templates/counts/flags, never external bound views. */
    public void save(DataOutput out)throws IOException{out.writeInt(FORMAT);for(byte[] raw:templates)out.write(raw);for(int[] value:owned.values())out.writeInt(value[0]);}
    public static final class Image {private final byte[][] templates;private final int[] values;private Image(byte[][] templates,int[] values){this.templates=templates;this.values=values;}}
    public static Image load(DataInput in)throws IOException{if(in.readInt()!=FORMAT)throw new IOException("PC1 raw draw memory format");byte[][] raw=new byte[7][];for(int n=0;n<7;n++){raw[n]=new byte[CAPACITY[n]*32];in.readFully(raw[n]);}int[] values=new int[COUNTS.length+OWNED.length];for(int n=0;n<values.length;n++)values[n]=in.readInt();return new Image(raw,values);}
    public void restore(Image image)throws IOException{if(image==null)throw new IOException("PC1 raw draw memory image missing");for(int n=0;n<7;n++)System.arraycopy(image.templates[n],0,templates[n],0,templates[n].length);int n=0;for(int[] value:owned.values())value[0]=image.values[n++];}
}
