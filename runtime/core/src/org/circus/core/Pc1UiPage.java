package org.circus.core;

import java.io.*;

/**40C1C0/40C0A0 list-page suspension. The backup is ordinary mutable native
 * BSS and shares graphics-slot aliases; it is not an immutable UI DTO. */
public final class Pc1UiPage {
    private Pc1UiPage(){}
    /** Backup bytes not already owned by UI/draw/graphics. In particular,
     *9553B8..9553C8 borrow the existing image-slot DWORDs, not another array. */
    public static final class Memory {
        private static final int FORMAT=0x50315531;
        private static final int[][] OWNED={{0x94f8e0,328},{0x94fa30,22880},{0x955390,12},{0x9553a0,4},{0x9553ec,16},{0x955554,20},{0x95af18,16}};
        private final byte[][] raw=new byte[OWNED.length][];
        public Memory(Pc1NativeMemory memory)throws IOException{
            if(memory==null)throw new IOException("PC1 list-page backup requires native byte bus");
            for(int n=0;n<OWNED.length;n++){final byte[] bytes=raw[n]=new byte[OWNED[n][1]];memory.bind(OWNED[n][0],bytes.length,new Pc1NativeMemory.Region(){public int read(int at){return bytes[at]&255;}public void write(int at,int value){bytes[at]=(byte)value;}});}
        }
        public void save(DataOutput out)throws IOException{out.writeInt(FORMAT);for(byte[] bytes:raw)out.write(bytes);}
        public static final class Image {private final byte[][] raw;private Image(byte[][] raw){this.raw=raw;}}
        public static Image load(DataInput in)throws IOException{if(in.readInt()!=FORMAT)throw new IOException("PC1 list-page backup format");byte[][] bytes=new byte[OWNED.length][];for(int n=0;n<bytes.length;n++){bytes[n]=new byte[OWNED[n][1]];in.readFully(bytes[n]);}return new Image(bytes);}
        public void restore(Image image)throws IOException{if(image==null)throw new IOException("PC1 list-page backup missing");for(int n=0;n<raw.length;n++)System.arraycopy(image.raw[n],0,raw[n],0,raw[n].length);}
    }
    private static void dword(Pc1InlineText.Memory m,int destination,int source)throws IOException{int value=Pc1InlineText.readI32(m,source);Pc1InlineText.writeI32(m,destination,value);}
    private static void copy(Pc1InlineText.Memory m,int destination,int source,int dwords)throws IOException{for(int n=0;n<dwords;n++)dword(m,destination+n*4,source+n*4);}
    public static void capture(Pc1InlineText.Memory m)throws IOException{
        if(m==null)throw new IOException("PC1 list-page capture memory missing");
        int a,c,d,source,destination;
        a=Pc1InlineText.readI32(m,0x9553c8);
        c=0x1658;
        source=0x9555b8;
        destination=0x94fa30;
        d=Pc1InlineText.readI32(m,0x9553d0);
        Pc1InlineText.writeI32(m,0x95af18,a);
        a=Pc1InlineText.readI32(m,0x9553d4);
        Pc1InlineText.writeI32(m,0x95af20,d);
        copy(m,destination,source,c);
        d=Pc1InlineText.readI32(m,0x9553dc);
        c=0x50;
        source=0x955408;
        destination=0x94f8e8;
        copy(m,destination,source,c);
        c=Pc1InlineText.readI32(m,0x9553cc);
        Pc1InlineText.writeI32(m,0x95af24,a);
        a=Pc1InlineText.readI32(m,0x9553e0);
        Pc1InlineText.writeI32(m,0x95af1c,c);
        c=Pc1InlineText.readI32(m,0x9553d8);
        Pc1InlineText.writeI32(m,0x95555c,d);
        d=Pc1InlineText.readI32(m,0x95554c);
        Pc1InlineText.writeI32(m,0x955558,c);
        c=Pc1InlineText.readI32(m,0x9553e4);
        Pc1InlineText.writeI32(m,0x955560,a);
        a=Pc1InlineText.readI32(m,0x4305fc);
        Pc1InlineText.writeI32(m,0x955564,c);
        c=Pc1InlineText.readI32(m,0x9553e8);
        Pc1InlineText.writeI32(m,0x955390,d);
        d=Pc1InlineText.readI32(m,0x95539c);
        Pc1InlineText.writeI32(m,0x94f8e0,a);
        a=Pc1InlineText.readI32(m,0x9689f8);
        Pc1InlineText.writeI32(m,0x9553a0,c);
        c=Pc1InlineText.readI32(m,0x430600);
        Pc1InlineText.writeI32(m,0x9689e8,d);
        d=Pc1InlineText.readI32(m,0x9689fc);
        Pc1InlineText.writeI32(m,0x955394,a);
        a=Pc1InlineText.readI32(m,0x430604);
        Pc1InlineText.writeI32(m,0x9553bc,c);
        c=Pc1InlineText.readI32(m,0x430608);
        Pc1InlineText.writeI32(m,0x9553b8,d);
        d=Pc1InlineText.readI32(m,0x968a00);
        Pc1InlineText.writeI32(m,0x94f8e4,a);
        a=Pc1InlineText.readI32(m,0x955550);
        Pc1InlineText.writeI32(m,0x9553c0,c);
        c=Pc1InlineText.readI32(m,0x955548);
        Pc1InlineText.writeI32(m,0x955398,d);
        d=Pc1InlineText.readI32(m,0x955400);
        Pc1InlineText.writeI32(m,0x9553f0,a);
        a=Pc1InlineText.readI32(m,0x9553fc);
        Pc1InlineText.writeI32(m,0x9553ec,c);
        Pc1InlineText.writeI32(m,0x9553f4,d);
        Pc1InlineText.writeI32(m,0x9553f8,a);
    }
    public static void restore(Pc1InlineText.Memory m)throws IOException{
        if(m==null)throw new IOException("PC1 list-page restore memory missing");
        int a,c,d,source,destination;
        a=Pc1InlineText.readI32(m,0x95af18);
        c=0x1658;
        source=0x94fa30;
        destination=0x9555b8;
        d=Pc1InlineText.readI32(m,0x95af20);
        Pc1InlineText.writeI32(m,0x9553c8,a);
        a=Pc1InlineText.readI32(m,0x95af24);
        Pc1InlineText.writeI32(m,0x9553d0,d);
        copy(m,destination,source,c);
        d=Pc1InlineText.readI32(m,0x95555c);
        c=0x50;
        source=0x94f8e8;
        destination=0x955408;
        copy(m,destination,source,c);
        c=Pc1InlineText.readI32(m,0x95af1c);
        Pc1InlineText.writeI32(m,0x9553d4,a);
        a=Pc1InlineText.readI32(m,0x955560);
        Pc1InlineText.writeI32(m,0x9553cc,c);
        c=Pc1InlineText.readI32(m,0x955558);
        Pc1InlineText.writeI32(m,0x9553dc,d);
        d=Pc1InlineText.readI32(m,0x955390);
        Pc1InlineText.writeI32(m,0x9553d8,c);
        c=Pc1InlineText.readI32(m,0x955564);
        Pc1InlineText.writeI32(m,0x9553e0,a);
        a=Pc1InlineText.readI32(m,0x94f8e0);
        Pc1InlineText.writeI32(m,0x9553e4,c);
        c=Pc1InlineText.readI32(m,0x9553a0);
        Pc1InlineText.writeI32(m,0x95554c,d);
        d=Pc1InlineText.readI32(m,0x9689e8);
        Pc1InlineText.writeI32(m,0x4305fc,a);
        a=Pc1InlineText.readI32(m,0x955394);
        Pc1InlineText.writeI32(m,0x9553e8,c);
        c=Pc1InlineText.readI32(m,0x9553bc);
        Pc1InlineText.writeI32(m,0x95539c,d);
        d=Pc1InlineText.readI32(m,0x9553b8);
        Pc1InlineText.writeI32(m,0x9689f8,a);
        a=Pc1InlineText.readI32(m,0x94f8e4);
        Pc1InlineText.writeI32(m,0x430600,c);
        c=Pc1InlineText.readI32(m,0x9553c0);
        Pc1InlineText.writeI32(m,0x9689fc,d);
        d=Pc1InlineText.readI32(m,0x955398);
        Pc1InlineText.writeI32(m,0x430604,a);
        a=Pc1InlineText.readI32(m,0x9553f0);
        Pc1InlineText.writeI32(m,0x430608,c);
        c=Pc1InlineText.readI32(m,0x9553ec);
        Pc1InlineText.writeI32(m,0x968a00,d);
        d=Pc1InlineText.readI32(m,0x9553f4);
        Pc1InlineText.writeI32(m,0x955550,a);
        a=Pc1InlineText.readI32(m,0x9553f8);
        Pc1InlineText.writeI32(m,0x955548,c);
        Pc1InlineText.writeI32(m,0x955400,d);
        Pc1InlineText.writeI32(m,0x9553fc,a);
    }
}
