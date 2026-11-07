package org.circus.core;

import java.io.IOException;

/** Mono2x projection over actual addressed bytes. The native ascent DWORD is
 *reread for EACH row, including when an earlier output spill changed it. */
public final class NativeGlyphProjection {
    private NativeGlyphProjection(){}
    private static int g(NativeGlyphCache.Memory memory,int address)throws IOException{int value=0;for(int n=0;n<4;n++)value|=memory.readByte(address+n)<<(n*8);return value;}
    public static void project(NativeGlyphCache.Memory memory,int destination,int requestedSize,int ascentAddress,Pc1GlyphRaster.Outline outline)throws IOException{
        if(memory==null||outline==null)throw new IOException("Native glyph projection known memory/mono missing");
        long square=requestedSize*requestedSize&0xffffffffL;int clearAt=destination;for(long n=0;n<(square>>>2);n++,clearAt+=4){for(int b=0;b<4;b++)memory.readByte(clearAt+b);for(int b=0;b<4;b++)memory.writeByte(clearAt+b,0);}for(int n=0;n<(square&3);n++)memory.writeByte(clearAt+n,0);
        int start=outline.originX/2,parity=outline.originX&1;
        for(int row=0;row<outline.height;row++){
            int y=(g(memory,ascentAddress)-outline.originY+row)/2,base=destination+y*requestedSize+start;Pc1GlyphRaster.Outline.MonoRow source=outline.row(row);
            for(int column=0;column<outline.width;column++)if((source.read(column>>>3)&(0x80>>>(column&7)))!=0){int at=base+((parity+column)>>>1);memory.writeByte(at,memory.readByte(at)+1);}
        }
    }
}
