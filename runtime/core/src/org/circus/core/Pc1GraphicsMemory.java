package org.circus.core;

import java.io.*;

/** Additional reviewed graphics globals and borrowed pixel/cache/name views.
 * Frame, settings, UI, templates, geometry and layout remain in Runtime's
 * existing native bus. Loaded-PE defaults are not a completed OS bootstrap. */
public final class Pc1GraphicsMemory implements Pc1Compositor.State,Pc1InlineText.Memory {
    private static final int FORMAT=0x5031474d,VERSION=1;
    private static final int[][] OWNED={
        {Pc1DrawBatch.PALETTE,252},{Pc1DrawBatch.SLOT_BASE,36},
        {0x43d418,4},{0x43d420,4},{0x43d424,4},{0x43d428,4},{0x43d430,4},{0x9b9be4,4},{0x9b9be8,4},
        {0x9cca90,4},{0x9cca94,4},{0x9cca98,4},{0x971768,4},{0x97176c,4},{0x971770,4},{0x9a0778,4},
        {0x9b96e0,4},{0x9dbab8,4},{0xbb8404,4},{0xbb8408,4},{0x43028c,4},
        {0x94c61c,4},{0x94c62c,8},{0x94c680,8}
    };
    private static final int[] FIRST_COLORS={0x00000000,0x00ff0000,0x0000ff00,0x00ffff00,0x000000ff,0x00ff00ff,0x0000ffff,0x00ffffff,0x0028854e,0x00ffcccc,0x00007744,0x007fff7f,0x00cccccc};
    private final Pc1NativeMemory memory;private final byte[][] bytes=new byte[OWNED.length][];private boolean namesBound,metadataBound,glyphsBound,compositorBound;
    public Pc1GraphicsMemory(Pc1NativeMemory memory)throws IOException{
        if(memory==null)throw new IOException("PC1 graphics globals require canonical native memory");this.memory=memory;
        for(int n=0;n<OWNED.length;n++){final byte[] raw=bytes[n]=new byte[OWNED[n][1]];memory.bind(OWNED[n][0],raw.length,new Pc1NativeMemory.Region(){public int read(int at){return raw[at]&255;}public void write(int at,int value){raw[at]=(byte)value;}});}
        for(int n=0;n<FIRST_COLORS.length;n++)writeInt(Pc1DrawBatch.PALETTE+n*4,FIRST_COLORS[n]);
        writeInt(0x43d418,-1);writeInt(0x43d420,-1);writeInt(0x43d424,-1);writeInt(0x43d428,-1);writeInt(0x43d430,-1);writeInt(0x43028c,1);
    }
    public Pc1NativeMemory nativeMemory(){return memory;}
    public void requireUnboundViews()throws IOException{if(namesBound||metadataBound||glyphsBound||compositorBound)throw new IOException("PC1 graphics restore requires fresh unbound borrowed views");}
    @Override public int read(int address)throws IOException{return memory.readAddress(address);}
    @Override public void write(int address,int value)throws IOException{memory.writeAddress(address,value);}
    @Override public int readInt(int address)throws IOException{return memory.readInt32(address);}
    @Override public void writeInt(int address,int value)throws IOException{memory.writeInt32(address,value);}
    @Override public void writeByte(int address,int value)throws IOException{write(address,value);}
    /** One canonical12800-byte pool plus its CRX row pointer cell. Proven32
     *pointer bytes are directly readable; opaque64/managed pointer bytes retain
     *their explicit unknown mask. Shape/name stores mutate this same owner. */
    public void bindImageMetadata(final Pc1Compositor compositor)throws IOException{
        if(metadataBound||compositor==null)throw new IOException("PC1 graphics metadata view requires unique image-pool owner");
        final Pc1ImagePool pool=compositor.pool();if(pool.guestAddressSpace()!=null)pool.guestAddressSpace().bindTo(memory);memory.bind(Pc1ImagePool.BASE,Pc1ImagePool.BYTES,new Pc1NativeMemory.Region(){public int read(int offset)throws IOException{return pool.readU8(offset);}public void write(int offset,int value)throws IOException{pool.writeU8(offset,value);}});memory.bind(Pc1CrxMemory.ROW_POINTER,4,new Pc1NativeMemory.Region(){public int read(int offset)throws IOException{return pool.rowPointerByte(offset);}public void write(int offset,int value)throws IOException{pool.rowPointerByte(offset,value);}});metadataBound=true;
    }
    /** Names are already part of the same canonical128-byte records. */
    public void bindResidentNames(final Pc1GraphicsResources resources)throws IOException{if(namesBound||resources==null||!metadataBound)throw new IOException("PC1 graphics name view requires unique image-pool owner");namesBound=true;}
    public void bindGlyphCache(final Pc1GlyphCache cache)throws IOException{
        if(glyphsBound||cache==null)throw new IOException("PC1 graphics glyph view requires unique native glyph-cache owner");cache.bindTo(memory);glyphsBound=true;
    }
    /** Native left/top/right/bottom address order is not struct Rect order.
     *All fields delegate directly; this owner never saves a second copy. */
    public void bindCompositorGlobals(final Pc1Compositor compositor)throws IOException{
        if(compositorBound||compositor==null)throw new IOException("PC1 graphics globals view requires unique compositor owner");
        for(final int address:new int[]{0x94f8d8,0x4304e8,0x4304ec,0x4304f0,0x4304f4,0xbc5f5c,0xbc5f60,0xbc5f64,0xbc5f68,0xbc5f6c,0xbc5f70,0xbc5f74,0xbc5f78})memory.bind(address,4,new Pc1NativeMemory.Region(){public int read(int at)throws IOException{return compositor.nativeGlobal(address)>>>(at*8)&255;}public void write(int at,int value)throws IOException{int shift=at*8,old=compositor.nativeGlobal(address);compositor.nativeGlobal(address,old&~(255<<shift)|(value&255)<<shift);}});compositorBound=true;
    }
    public void bindViews(Pc1Compositor compositor,Pc1GraphicsResources resources,Pc1GlyphCache cache)throws IOException{bindCompositorGlobals(compositor);bindImageMetadata(compositor);bindResidentNames(resources);bindGlyphCache(cache);}
    /** Only the additional owned globals above. Pixels/metadata, resident
     * names, glyph bytes/font and existing Runtime owners save themselves. */
    public void save(DataOutput out)throws IOException{out.writeInt(FORMAT);out.writeInt(VERSION);for(byte[] raw:bytes)out.write(raw);}
    public static final class Image {private final byte[][] bytes;private Image(byte[][] bytes){this.bytes=bytes;}}
    public static Image load(DataInput in)throws IOException{if(in.readInt()!=FORMAT||in.readInt()!=VERSION)throw new IOException("PC1 graphics globals format");byte[][] bytes=new byte[OWNED.length][];for(int n=0;n<bytes.length;n++){bytes[n]=new byte[OWNED[n][1]];in.readFully(bytes[n]);}return new Image(bytes);}
    public static Image loadImage(DataInput in)throws IOException{return load(in);}
    public void restore(Image image)throws IOException{if(image==null)throw new IOException("PC1 graphics globals image missing");for(int n=0;n<bytes.length;n++)System.arraycopy(image.bytes[n],0,bytes[n],0,bytes[n].length);}
}
