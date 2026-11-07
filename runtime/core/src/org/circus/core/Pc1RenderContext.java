package org.circus.core;

import java.io.*;
import java.util.Arrays;

/** Actual PC1 rendering service graph over ONE native state/buffer/cache.
 * The caller imports a genuinely known Compositor front; this class never
 * manufactures initial heap/DIB pixels or calls a portable DTO/AWT painter. */
public final class Pc1RenderContext {
    private static final int FORMAT=0x50314758,VERSION=1,MAX_KNOWN=1024*1024; //P1GX render graph, distinct from Runtime P1R*
    /** These independent native consumers remain required. In particular,
     * effects is40FA80, controls is416B80; neither means a generic repaint.
     * The larger41AF50 partial dispatcher is not substituted by Underlay. */
    public interface Leaves {
        void effects(int layer)throws IOException;
        void controls()throws IOException;
        Pc1UiRaster.Point cursorClient()throws IOException;
        int variable(int bank,int index)throws IOException;
        void sound(int id,int mode)throws IOException;
        void voice(int rawCStringPointer)throws IOException;
        void specialListItem(int visibleIndex,int dataIndex,boolean selected)throws IOException;
    }
    private final Pc1Runtime runtime;
    private final Pc1GraphicsMemory memory;
    private final Pc1Compositor compositor;
    private final Pc1GraphicsResources resources;
    private final Pc1GlyphService glyphs;
    private final Pc1TextExpander.Context expansion;
    private final Leaves leaves;
    private final Pc1Underlay underlay;
    private final Pc1UiRaster ui;
    private final Pc1GeneralRedraw general;
    private int depth;
    private final Pc1GlyphService.Sink sink=new Pc1GlyphService.Sink(){
        public Pc1Raster.Clip clip(){return compositor.clip();}
        public int rawPalette(int index)throws IOException{return memory.readInt(Pc1DrawBatch.PALETTE+index*4);}
        public Pc1GlyphRaster.Draw draw(int x,int y,int size,byte[] known,int offset,int rgb)throws IOException{return compositor.glyph(x,y,size,known,offset,rgb);}
        public void decoration(int l,int t,int r,int b,int rgb,int weight)throws IOException{compositor.decoration(l,t,r,b,rgb,weight);}
    };
    private final Pc1InlineText.Services inline=new Pc1InlineText.Services(){
        public void glyphs(int x,int y,byte[] known,int palette,int size)throws IOException{glyphs.drawRaw(sink,x,y,known,0,palette,size);}
        public void glyphsSource(int x,int y,Pc1GlyphCache.Source source,int palette,int size)throws IOException{glyphs.drawRaw(sink,x,y,source,palette,size);}
        public void decoration(int l,int t,int r,int b,int palette,int weight)throws IOException{compositor.decoration(l,t,r,b,Pc1GlyphRaster.paletteRgb(memory.readInt(Pc1DrawBatch.PALETTE+palette*4)),weight);}
    };
    private final Pc1Compositor.Layers layers=new Pc1Compositor.Layers(){
        public void rebuildBackground(Pc1Compositor c,Pc1Compositor.State s)throws IOException{same(c,s);bakePending();}
        public void grid(Pc1Compositor c,Pc1Compositor.State s,int mode)throws IOException{same(c,s);ui.grid(mode);}
        public void buttons(Pc1Compositor c,Pc1Compositor.State s,int mode)throws IOException{same(c,s);ui.buttons(mode);}
    };
    /** cache/provider are the caller's single canonical native font owners.
     * expansion must read the same current Runtime names/variables/catalogs.
     * Raw graphics globals are already bound by Runtime/its caller. */
    public Pc1RenderContext(Pc1Runtime runtime,Pc1GraphicsMemory memory,Pc1Compositor knownCompositor,Pc1GlyphCache cache,Pc1GlyphCache.Provider provider,Pc1GraphicsResources.Backend backend,Pc1TextExpander.Context expansion,Leaves leaves)throws IOException{
        this(runtime,memory,knownCompositor,newResources(knownCompositor,memory,backend),new Pc1GlyphService(cache,provider),expansion,leaves);
    }
    private static Pc1GraphicsResources newResources(Pc1Compositor c,Pc1GraphicsMemory m,Pc1GraphicsResources.Backend b)throws IOException{if(b==null)throw new IOException("PC1 render context requires actual native image/CG backend");return new Pc1GraphicsResources(c,m,b);}
    private Pc1RenderContext(Pc1Runtime runtime,Pc1GraphicsMemory memory,Pc1Compositor c,Pc1GraphicsResources resources,Pc1GlyphService glyphs,Pc1TextExpander.Context expansion,Leaves leaves)throws IOException{
        if(runtime==null||memory==null||memory.nativeMemory()!=runtime.nativeMemory()||c==null||resources==null||glyphs==null||expansion==null||leaves==null)throw new IOException("PC1 render context requires canonical Runtime, known front and every native leaf");
        this.runtime=runtime;this.memory=memory;compositor=c;this.resources=resources;this.glyphs=glyphs;this.expansion=expansion;this.leaves=leaves;
        // No second names/cache/image pool: raw bus views point at these owners.
        memory.bindViews(compositor,resources,glyphs.cache());
        underlay=new Pc1Underlay(c,memory,new Pc1Underlay.Services(){public void effects(int layer)throws IOException{Pc1RenderContext.this.leaves.effects(layer);}public void controls()throws IOException{Pc1RenderContext.this.leaves.controls();}});
        ui=new Pc1UiRaster(runtime.nativeUi(),c,new Pc1UiRaster.Services(){
            public void restore(int l,int t,int r,int b,int mode)throws IOException{underlay.body(l,t,r,b,mode);}
            public Pc1UiRaster.Point cursorClient()throws IOException{return Pc1RenderContext.this.leaves.cursorClient();}
            public void glyphs(int x,int y,int pitch,byte[] known,int style)throws IOException{Pc1InlineText.draw(Pc1RenderContext.this.memory,inline,x,y,pitch,known,style);}
            public int variable(int bank,int index)throws IOException{return Pc1RenderContext.this.leaves.variable(bank,index);}
            public void sound(int id,int mode)throws IOException{Pc1RenderContext.this.leaves.sound(id,mode);}
            public void voice(int pointer)throws IOException{Pc1RenderContext.this.leaves.voice(pointer);}
            public void specialListItem(int visible,int data,boolean selected)throws IOException{Pc1RenderContext.this.leaves.specialListItem(visible,data,selected);}
            public void expand(int source,int destination)throws IOException{runtime.expandNativeUi(source,destination);}
        });
        general=new Pc1GeneralRedraw(c,memory,memory,new Pc1GeneralRedraw.Services(){
            public void effects(int layer)throws IOException{Pc1RenderContext.this.leaves.effects(layer);}
            public void glyphJournal(int first)throws IOException{drawGlyphJournal(first);}
            public void rangeButton(int button,int state)throws IOException{ui.rangeButton(button,state);}
            public void expand(int source,int destination)throws IOException{runtime.expandNativeUi(source,destination);}
            public void inline(int x,int y,int pitch,byte[] known,int style)throws IOException{Pc1InlineText.draw(Pc1RenderContext.this.memory,inline,x,y,pitch,known,style);}
            public void rawGlyph28(int x,int y,byte[] known,int palette)throws IOException{glyphs.drawRaw(sink,x,y,known,0,palette,28);}
            public void restoreBody(int l,int t,int r,int b,int mode)throws IOException{underlay.body(l,t,r,b,mode);}
        });
        runtime.bindNativeRenderContext(this);
    }
    private void same(Pc1Compositor c,Pc1Compositor.State state)throws IOException{if(c!=compositor||state!=memory)throw new IOException("PC1 render consumer changed canonical compositor/state");}
    public Pc1Compositor compositor(){return compositor;}
    public Pc1GraphicsMemory memory(){return memory;}
    public Pc1GraphicsResources resources(){return resources;}
    public Pc1GlyphCache glyphCache(){return glyphs.cache();}
    public void requireBoundary()throws IOException{if(depth!=0)throw new IOException("PC1 render snapshot during native consumer callback");}
    public interface NativeCall {void run()throws IOException;}
    /** An enclosing effect/control producer can span its complete native
     *entry with this same snapshot fence while still allowing native reentry. */
    public void nativeCall(NativeCall call)throws IOException{if(call==null)throw new IOException("PC1 native render entry callback missing");depth++;try{call.run();}finally{depth--;}}
    /** Actual known bytes until the first unowned neighbor. It never skips a
     *gap or appends0. The downstream native parser faults at its real read. */
    private byte[] knownPrefix(int address)throws IOException{ByteArrayOutputStream out=new ByteArrayOutputStream();for(int n=0;n<MAX_KNOWN;n++){int b;try{b=memory.read(address+n);}catch(IOException end){break;}out.write(b);}return out.toByteArray();}
    private byte[] cstring(int address)throws IOException{ByteArrayOutputStream out=new ByteArrayOutputStream();for(int n=0;n<MAX_KNOWN;n++){int b=memory.read(address+n);if(b==0)return out.toByteArray();out.write(b);}throw new IOException("PC1 render C-string exceeds known host allocation bound");}
    public void drawInline(int x,int y,int newlinePitch,byte[] knownRaw,int style)throws IOException{depth++;try{Pc1InlineText.draw(memory,inline,x,y,newlinePitch,knownRaw,style);}finally{depth--;}}
    public void drawInlineAt(int x,int y,int newlinePitch,int rawAddress,int style)throws IOException{depth++;try{Pc1InlineText.drawSource(memory,inline,x,y,newlinePitch,n->runtime.nativeMemory().readAddress(rawAddress+n),style);}finally{depth--;}}
    public void drawRawGlyphsSource(int x,int y,Pc1GlyphCache.Source source,int palette,int size)throws IOException{depth++;try{glyphs.drawRaw(sink,x,y,source,palette,size);}finally{depth--;}}
    /**409140/4093F0 raw direct wrapper; caller supplies actual known C bytes. */
    public void drawRawGlyphs(int x,int y,byte[] knownRaw,int palette,int size)throws IOException{depth++;try{glyphs.drawRaw(sink,x,y,knownRaw,0,palette,size);}finally{depth--;}}
    public void rawGlyph28(int x,int y,byte[] knownRaw,int palette)throws IOException{drawRawGlyphs(x,y,knownRaw,palette,28);}
    public void rawGlyph28At(int x,int y,int rawAddress,int palette)throws IOException{depth++;try{glyphs.drawRaw(sink,x,y,runtime.nativeMemory(),rawAddress,palette,28);}finally{depth--;}}
    public void expand(int source,int destination)throws IOException{depth++;try{runtime.expandUi(source,destination);}finally{depth--;}}
    /**4170C0 uses full known raw journal/stale suffix, not active Glyph DTOs. */
    public void drawGlyphJournal(int first)throws IOException{depth++;try{glyphs.drawNativeRecords(sink,runtime.nativeMemory(),first);}finally{depth--;}}
    /**Input and drawing borrow this same UI consumer and canonical pool.*/
    public Pc1UiRaster uiRaster(){return ui;}
    public void restoreBody(int l,int t,int r,int b,int mode)throws IOException{depth++;try{underlay.body(l,t,r,b,mode);}finally{depth--;}}
    public void restoreName(int l,int t,int r,int b,int mode)throws IOException{depth++;try{underlay.name(l,t,r,b,mode);}finally{depth--;}}
    public void messageWindow()throws IOException{depth++;try{underlay.messageWindow();}finally{depth--;}}
    /**41AF50 retained partial renderer; this never pretends a full publish. */
    public void generalRedraw(int mask,int l,int t,int r,int b)throws IOException{depth++;try{general.redraw(mask,l,t,r,b);}finally{depth--;}}
    public void waitCursor(int mode)throws IOException{depth++;try{general.waitCursor(mode);}finally{depth--;}}
    public void redrawControls()throws IOException{depth++;try{general.controls();}finally{depth--;}}
    public void redrawBackground()throws IOException{depth++;try{compositor.redrawBackground(memory,layers);}finally{depth--;}}
    public void prepareUi(int withGrid,int intoBackground)throws IOException{depth++;try{compositor.prepareUi(withGrid,intoBackground,memory,layers);}finally{depth--;}}
    public void buttons(int mode)throws IOException{depth++;try{ui.buttons(mode);}finally{depth--;}}
    public void redrawButtons(int state)throws IOException{depth++;try{ui.redrawButtons(state);}finally{depth--;}}
    public void hoverButtons(int x,int y)throws IOException{depth++;try{ui.hoverButtons(x,y);}finally{depth--;}}
    public void rangeButton(int button,int state)throws IOException{depth++;try{ui.rangeButton(button,state);}finally{depth--;}}
    public void grid(int mode)throws IOException{depth++;try{ui.grid(mode);}finally{depth--;}}
    public void hoverGrid(int x,int y)throws IOException{depth++;try{ui.hoverGrid(x,y);}finally{depth--;}}
    public void sprite(int component,int mode)throws IOException{depth++;try{ui.sprite(component,mode);}finally{depth--;}}
    public void uiText(int component,int rawSource)throws IOException{depth++;try{ui.text(component,rawSource);}finally{depth--;}}
    public int path(int slot,byte[] rawName)throws IOException{depth++;try{return resources.path(slot,rawName);}finally{depth--;}}
    public void release(int slot,int mode)throws IOException{depth++;try{resources.release(slot,mode);}finally{depth--;}}
    public void rebuild(Pc1NativeFrame source)throws IOException{depth++;try{resources.rebuild(source);}finally{depth--;}}
    /** Per411D30 invocation, the2048-byte expanded local starts UNKNOWN.
     *4123A0 writes only prefix+2NUL; later records retain known stale suffix.
     *No locals/committed pixels are rolled back on a leaf failure. */
    public void bakePending()throws IOException{
        depth++;try{final byte[] local=new byte[2048];final int[] known={0};
            Pc1DrawBatch.bake(runtime.nativeFrame(),compositor,memory,resources,new Pc1DrawBatch.TextSource(){public byte[] rawCString(int offset)throws IOException{return cstring(Pc1NativeMemory.CODE_BASE+offset);}},expansion,new Pc1DrawBatch.TextRenderer(){public void draw(Pc1Compositor c,int x,int y,int pitch,byte[] expanded,int style)throws IOException{
                if(c!=compositor)throw new IOException("PC1 draw batch changed compositor");byte[] written=Pc1TextExpander.nativeBuffer(expanded);if(written.length>local.length)throw new IOException("PC1 draw-batch expanded local2048 overflow");System.arraycopy(written,0,local,0,written.length);known[0]=Math.max(known[0],written.length);Pc1InlineText.draw(memory,inline,x,y,pitch,Arrays.copyOf(local,known[0]),style);
            }});
        }finally{depth--;}
    }
    /** Only this render graph's independent owners. Runtime already saves
     *P1GM globals, frame/code/layout/UI/settings; do not save them twice. OS
     *window/input/effects/timer/audio owners remain the caller's responsibility. */
    public void save(DataOutput out)throws IOException{requireBoundary();out.writeInt(FORMAT);out.writeInt(VERSION);compositor.save(out);resources.save(out);glyphs.save(out);}
    /** Activate only on a fresh/restored Runtime whose graphics borrowed views
     *have not yet been bound. Font provider validates its real environment.
     *The same runtime/globals bytes must have been restored independently. */
    public static Pc1RenderContext load(DataInput in,Pc1Runtime runtime,Pc1GraphicsMemory memory,Pc1GlyphCache.Provider provider,Pc1GraphicsResources.Backend backend,Pc1TextExpander.Context expansion,Leaves leaves)throws IOException{
        return restore(in,runtime,memory,provider,backend,expansion,leaves,null,null);
    }
    public static final class Image {
        private final Pc1Compositor.Snapshot compositor;private final Pc1GraphicsResources.Image resources;private final Pc1GlyphCache.Image glyphs;
        private Image(Pc1Compositor.Snapshot compositor,Pc1GraphicsResources.Image resources,Pc1GlyphCache.Image glyphs){this.compositor=compositor;this.resources=resources;this.glyphs=glyphs;}
        public Pc1Compositor.Snapshot compositorImage(){return compositor;}
        public Pc1GlyphCache.Image glyphImage(){return glyphs;}
    }
    /** Preflight the complete wire before allocating any physical owner. */
    public static Image loadImage(DataInput in)throws IOException{
        if(in.readInt()!=FORMAT||in.readInt()!=VERSION)throw new IOException("PC1 render context format");Pc1Compositor.Snapshot pixels=Pc1Compositor.loadImage(in);Pc1GraphicsResources.Image resources=Pc1GraphicsResources.loadImage(in);Pc1GlyphCache.Image glyphs=Pc1GlyphCache.loadImage(in);return new Image(pixels,resources,glyphs);
    }
    /** On a newly predeclared graph, install its sole pool/layout into the
     *same compositor already borrowed by every render/control consumer. */
    public void restorePixels(Image image,Pc1ImagePool.Rebinder rebinder)throws IOException{requireBoundary();if(image==null||rebinder==null)throw new IOException("PC1 staged pixel restore requires its decoded state and rebinder");compositor.restoreInto(image.compositor,rebinder);}
    /** FontMemory must already be restored once, and the independent physical
     *provider must be activated after registry/Guest layout installation. */
    public void finishStagedRestore(Image image)throws IOException{requireBoundary();if(image==null)throw new IOException("PC1 staged render state missing");glyphs.validateStagedRestore(image.glyphs);}
    /** Physical restore stages its actual heap/DIB allocations explicitly.
     *The file backend does not choose or manufacture a new platform owner. */
    public static Pc1RenderContext load(DataInput in,Pc1Runtime runtime,Pc1GraphicsMemory memory,Pc1GlyphCache.Provider provider,Pc1GraphicsResources.Backend backend,Pc1TextExpander.Context expansion,Leaves leaves,Pc1HeapLease.Heap heap,Pc1ImagePool.Rebinder rebinder)throws IOException{
        if(heap==null||rebinder==null)throw new IOException("PC1 native render restore requires explicit heap/lease rebinding");return restore(in,runtime,memory,provider,backend,expansion,leaves,heap,rebinder);
    }
    private static Pc1RenderContext restore(DataInput in,Pc1Runtime runtime,Pc1GraphicsMemory memory,Pc1GlyphCache.Provider provider,Pc1GraphicsResources.Backend backend,Pc1TextExpander.Context expansion,Leaves leaves,Pc1HeapLease.Heap heap,Pc1ImagePool.Rebinder rebinder)throws IOException{
        if(in.readInt()!=FORMAT||in.readInt()!=VERSION)throw new IOException("PC1 render context format");if(runtime==null||memory==null||provider==null||backend==null||expansion==null||leaves==null)throw new IOException("PC1 render restore requires actual native owners/leaves");memory.requireUnboundViews();Pc1Compositor c=heap==null?Pc1Compositor.load(in):Pc1Compositor.load(in,heap,rebinder);Pc1GraphicsResources resources=Pc1GraphicsResources.load(in,c,memory,backend);Pc1GlyphService glyphs=Pc1GlyphService.load(in,provider);return new Pc1RenderContext(runtime,memory,c,resources,glyphs,expansion,leaves);
    }
}
