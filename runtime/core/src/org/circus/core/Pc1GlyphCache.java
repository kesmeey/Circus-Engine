package org.circus.core;

import java.io.*;

/** CurrentCHS404510/404660 on one addressed cache/metrics/fontscalar owner. */
public final class Pc1GlyphCache {
    private static final int FORMAT=0x50314b32; //P1K2,oldpartialP1K1 refused
    public static final int BASE=0x48d590,VARIABLE_COUNT=512,VARIABLE_STRIDE=1608,VARIABLE_MASK=8;
    public static final int FIXED_BASE=0x556590,FIXED_COUNT=256,FIXED_STRIDE=788,FIXED_MASK=4,END=0x587990;
    public static final int BYTES=END-BASE,FIXED_OFFSET=FIXED_BASE-BASE;
    public interface Source {int unsignedByte(int offset)throws IOException;}
    /** Hits call none of these methods; real providers expose their one raw owner. */
    public interface Provider {
        Pc1GlyphRaster.Outline outline(byte[] knownRaw,int offset,int requestedSize)throws IOException;
        default Pc1GlyphRaster.Outline outline(Source source,int size)throws IOException{int first=source.unsignedByte(0);byte[] known=first>128?new byte[]{(byte)first,(byte)source.unsignedByte(1)}:new byte[]{(byte)first};return outline(known,0,size);}
        default Pc1FontMemory fontMemory(){return null;}
        byte[] saveFontContext()throws IOException;
        void restoreFontContext(byte[] state)throws IOException;
        /** Validate an already activated physical world without recreating it.
         * Bound providers must be staged with their independent OS owners first. */
        default void validateRestoredFontContext(byte[] expected)throws IOException{if(!java.util.Arrays.equals(expected,saveFontContext()))throw new IOException("PC1 staged physical font context differs from saved state");}
    }
    private final Pc1FontMemory memory;private final NativeGlyphCache common;private boolean busy;
    private Pc1GlyphCache(Pc1FontMemory memory)throws IOException{if(memory==null)throw new IOException("PC1 glyph rawowner missing");this.memory=memory;common=new NativeGlyphCache(memory,new NativeGlyphCache.Layout(BASE,FIXED_BASE,Pc1FontMemory.VARIABLE_CURSOR,Pc1FontMemory.FIXED_CURSOR));}
    public static Pc1GlyphCache zeroBss(){try{return new Pc1GlyphCache(new Pc1FontMemory());}catch(IOException impossible){throw new AssertionError(impossible);}}
    public static Pc1GlyphCache fromMemory(Pc1FontMemory memory)throws IOException{return new Pc1GlyphCache(memory);}
    public Pc1FontMemory fontMemory(){return memory;}
    public void bindTo(Pc1NativeMemory bus)throws IOException{memory.bindTo(bus);}
    /** Explicit cache-only import. Complete states useP1K2 with all rawfont fields. */
    public static Pc1GlyphCache fromBytes(byte[] raw,int fixed,int variable)throws IOException{Pc1FontMemory memory=new Pc1FontMemory();memory.importCache(raw);memory.fixedReplacement(fixed);memory.variableReplacement(variable);return fromMemory(memory);}
    private void idle()throws IOException{if(busy||common.busy()||memory.busy())throw new IOException("PC1 glyph cache reentry/snapshot during callback");}
    public Pc1GlyphCache copy()throws IOException{idle();return fromMemory(memory.copy());}
    public byte[] bytes()throws IOException{idle();return memory.cacheBytes();}
    public int fixedReplacement(){return memory.fixedReplacement();}public int variableReplacement(){return memory.variableReplacement();}
    public void fixedReplacement(int value){memory.fixedReplacement(value);}public void variableReplacement(int value){memory.variableReplacement(value);}
    public int readU8(int at)throws IOException{return memory.readByte(at);}public void writeU8(int at,int value)throws IOException{memory.writeByte(at,value);}
    public int readI32(int at)throws IOException{return memory.readInt(at);}public void writeI32(int at,int value)throws IOException{memory.writeInt(at,value);}
    public static Source known(final byte[] bytes,final int offset){return n->{long at=(long)offset+n;if(bytes==null||at<0||at>=bytes.length)throw new IOException("PC1 glyph cache key requires actual following raw byte or known source");return bytes[(int)at]&255;};}
    public static int key(byte[] known,int at)throws IOException{return key(known(known,at));}
    public static int key(Source source)throws IOException{int first=source.unsignedByte(0);return first>128?first*189+source.unsignedByte(1)-0x5ec0:first;}
    public final class Entry {
        public final int nativeAddress,requestedSize;public final boolean hit,fixed28;
        private Entry(int at,int size,boolean hit){nativeAddress=at;requestedSize=size;this.hit=hit;fixed28=size==28;}
        public byte[] bytes(int length)throws IOException{return memory.ownedBytes(nativeAddress,length);}
        public Pc1GlyphRaster.Draw draw(NativeRgbImage target,Pc1Raster.Clip clip,int x,int y,int rgb)throws IOException{Pc1FontMemory.View view=memory.view(nativeAddress,0);return Pc1GlyphRaster.draw(target,clip,x,y,requestedSize,view.bytes,view.offset,rgb);}
        public Pc1GlyphRaster.Draw draw(Drawer drawer,int x,int y,int rgb)throws IOException{if(drawer==null)throw new IOException("PC1 glyph cache requires compositor kernel");Pc1FontMemory.View view=memory.view(nativeAddress,0);return drawer.draw(x,y,requestedSize,view.bytes,view.offset,rgb);}
    }
    public interface Drawer {Pc1GlyphRaster.Draw draw(int x,int y,int size,byte[] knownCoverage,int sourceOffset,int rgb)throws IOException;}
    public Entry lookup(byte[] raw,int offset,int size,Provider provider)throws IOException{return lookup(known(raw,offset),size,provider);}
    public Entry lookup(final Source source,int size,final Provider provider)throws IOException{
        if(busy||common.busy())throw new IOException("PC1 glyph cache reentry during callback");int key=key(source);busy=true;memory.begin();try{NativeGlyphCache.Lookup found=common.lookup(key,size,(destination,requested)->{if(provider==null)throw new IOException("Unimplemented PC1 actual GDI glyph provider on native cache miss");Pc1FontMemory provided=provider.fontMemory();if(provided!=null&&provided!=memory)throw new IOException("PC1 glyph provider/cache must share canonical font memory");Pc1GlyphRaster.Outline outline=provider.outline(source,requested);NativeGlyphProjection.project(memory,destination,requested,Pc1FontMemory.METRICS+4,outline);});return new Entry(found.address,size,found.hit);}finally{memory.end();busy=false;}
    }
    public void save(DataOutput out,Provider provider)throws IOException{
        idle();if(provider==null)throw new IOException("PC1 glyph snapshot requires independent font-context owner");busy=true;try{Pc1FontMemory provided=provider.fontMemory();if(provided!=null&&provided!=memory)throw new IOException("PC1 font snapshot provider/cache owner differs");byte[] context=provider.saveFontContext();if(context==null||context.length==0||context.length>1024*1024)throw new IOException("PC1 glyph provider font-context state length");out.writeInt(FORMAT);memory.save(out);out.writeInt(context.length);out.write(context);}finally{busy=false;}
    }
    public static final class Image {
        private final Pc1FontMemory.Image memory;private final byte[] context;
        private Image(Pc1FontMemory.Image memory,byte[] context){this.memory=memory;this.context=context;}
        public Pc1FontMemory.Image fontMemoryImage(){return memory;}
        public byte[] physicalContext(){return context.clone();}
    }
    /** Decode only. No provider call, OS allocation, byte restore or binding. */
    public static Image loadImage(DataInput in)throws IOException{
        if(in.readInt()!=FORMAT)throw new IOException("PC1 glyph cache formatP1K2 required");Pc1FontMemory.Image image=Pc1FontMemory.load(in);int length=in.readInt();if(length<=0||length>1024*1024)throw new IOException("PC1 glyph provider font-context state length");byte[] context=new byte[length];in.readFully(context);return new Image(image,context);
    }
    public static Pc1GlyphCache restore(Image image,Provider provider)throws IOException{
        if(image==null||provider==null)throw new IOException("PC1 glyph snapshot requires independent font-context owner");Pc1FontMemory memory=provider.fontMemory();if(memory==null)memory=new Pc1FontMemory();if(memory.busy())throw new IOException("PC1 font restore during native callback");provider.restoreFontContext(image.context.clone());memory.restore(image.memory);return fromMemory(memory);
    }
    /** Attach after the caller restored raw font memory exactly once and
     *activated all staged font/DC/DIB/registry/layout owners. Never recopy raw
     *cache bytes here or replay physical initialization. */
    public static Pc1GlyphCache restoreStaged(Image image,Provider provider)throws IOException{
        if(image==null||provider==null||provider.fontMemory()==null)throw new IOException("PC1 staged glyph restore requires its actual raw font owner");Pc1FontMemory memory=provider.fontMemory();if(!memory.matchesImage(image.memory))throw new IOException("PC1 staged raw font bytes differ from saved owner");provider.validateRestoredFontContext(image.context.clone());if(provider.fontMemory()!=memory||!memory.matchesImage(image.memory))throw new IOException("PC1 staged font verification changed canonical raw owner");return fromMemory(memory);
    }
    public static Pc1GlyphCache load(DataInput in,Provider provider)throws IOException{if(provider==null)throw new IOException("PC1 glyph snapshot requires independent font-context owner");return restore(loadImage(in),provider);}
}
