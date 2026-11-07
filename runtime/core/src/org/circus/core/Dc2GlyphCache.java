package org.circus.core;

import java.io.*;

/** Current patched d474DC2 reachable cache branches4044F0/404640. This does
 *not claim the inaccessible dense Japanese path belongs to another image. */
public final class Dc2GlyphCache {
    public interface Source {int unsignedByte(int offset)throws IOException;}
    public interface Provider {
        Pc1GlyphRaster.Outline outline(Source source,int requestedSize)throws IOException;
        byte[] saveFontContext()throws IOException;
        void restoreFontContext(byte[] bytes)throws IOException;
    }
    private final Dc2GlyphMemory memory;
    private final NativeGlyphCache common;
    public Dc2GlyphCache(Dc2GlyphMemory memory)throws IOException{if(memory==null)throw new IOException("DC2 glyph cache canonical memory missing");this.memory=memory;common=new NativeGlyphCache(memory,new NativeGlyphCache.Layout(Dc2GlyphMemory.CACHE,Dc2GlyphMemory.FIXED,Dc2GlyphMemory.VARIABLE_CURSOR,Dc2GlyphMemory.FIXED_CURSOR));}
    boolean belongsTo(Dc2GlyphMemory owner){return memory==owner;}
    public static int key(Source source)throws IOException{int first=source.unsignedByte(0);return first>0x80?first*189+source.unsignedByte(1)-0x5ec0:first;}
    /** Raw409900/409BB0 and4042C0 all treat FF as a double byte in this PE. */
    public static int characterCode(Source source)throws IOException{int first=source.unsignedByte(0);return first>0x80?first*256+source.unsignedByte(1):first;}
    public static int selectedFontSize(int request){return request==28||request==12||request==40?request:16;}
    public NativeGlyphCache.Lookup lookup(final Source source,int requestedSize,final Provider provider)throws IOException{
        memory.begin();try{return common.lookup(key(source),requestedSize,(destination,size)->{if(provider==null)throw new IOException("DC2 glyph actual GDI provider required on cache miss");Pc1GlyphRaster.Outline outline=provider.outline(source,size);NativeGlyphProjection.project(memory,destination,size,Dc2GlyphMemory.TEXT_METRICS+4,outline);});}finally{memory.end();}
    }
    public boolean busy(){return common.busy();}
}
