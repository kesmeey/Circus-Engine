package org.circus.core;

import java.io.*;

/** Canonical rendering/effect/story-control graph. Independent OS, audio,
 * special list consumers remain required services; none is a successful stub. */
public final class Pc1GraphicsPipeline {
    public interface Services {
        void invalidate(Pc1Raster.Rect rectangle)throws IOException;
        Pc1UiRaster.Point cursorClient()throws IOException;
        void sound(int id,int mode)throws IOException;
        void voice(int rawCStringPointer)throws IOException;
        void specialListItem(int visible,int data,boolean selected)throws IOException;
        void ensureTimer5()throws IOException;
        void beginNativePaint()throws IOException;
        void paintRectangle(int left,int top,int right,int bottom)throws IOException;
        void endNativePaint()throws IOException;
    }
    private final Pc1Runtime runtime;
    private final Services services;
    private final Pc1RenderContext render;
    private final Pc1Effects effects;
    private final Pc1StoryControls story;
    private final Pc1TextControls textControls;
    private int depth;
    private final Pc1RenderContext.Leaves leaves=new Pc1RenderContext.Leaves(){
        public void effects(int layer)throws IOException{Pc1GraphicsPipeline.this.effects.drawLayer(layer);}
        public void controls()throws IOException{story.draw();}
        public Pc1UiRaster.Point cursorClient()throws IOException{return services.cursorClient();}
        public int variable(int bank,int index)throws IOException{return runtime.nativeVariable(bank,index);}
        public void sound(int id,int mode)throws IOException{services.sound(id,mode);}
        public void voice(int pointer)throws IOException{services.voice(pointer);}
        public void specialListItem(int visible,int data,boolean selected)throws IOException{services.specialListItem(visible,data,selected);}
    };
    private final Pc1Effects.Services effectServices=new Pc1Effects.Services(){
        public int loadImage(int raw,int mode)throws IOException{return render.resources().load(cstring(raw),mode,render.compositor());}
        public void freeImage(int handle)throws IOException{render.compositor().free(handle);}
        public void ensureTimer5()throws IOException{services.ensureTimer5();}
        public void redraw(int mask,int l,int t,int r,int b)throws IOException{render.generalRedraw(mask,l,t,r,b);}
        public void beginNativePaint()throws IOException{services.beginNativePaint();}
        public void paintRectangle(int l,int t,int r,int b)throws IOException{services.paintRectangle(l,t,r,b);}
        public void endNativePaint()throws IOException{services.endNativePaint();}
    };
    private final Pc1StoryControls.Services storyServices=new Pc1StoryControls.Services(){
        public void buttons(int mode)throws IOException{render.buttons(mode);}
        public void restoreBody(int l,int t,int r,int b,int mode)throws IOException{render.restoreBody(l,t,r,b,mode);}
    };
    public Pc1GraphicsPipeline(Pc1Runtime runtime,Pc1Compositor knownFront,Pc1GlyphCache cache,Pc1GlyphCache.Provider font,Pc1GraphicsResources.Backend images,Services services)throws IOException{
        if(runtime==null||services==null)throw new IOException("PC1 graphics pipeline requires canonical runtime and complete platform services");this.runtime=runtime;this.services=services;
        render=new Pc1RenderContext(runtime,runtime.graphicsMemory(),knownFront,cache,font,images,runtime.nativeCatalogTextContext(),leaves);
        effects=new Pc1Effects(runtime.graphicsMemory(),knownFront,runtime::nativeCrtRandom,effectServices);
        story=new Pc1StoryControls(runtime.graphicsMemory(),storyServices);
        textControls=new Pc1TextControls(runtime.graphicsMemory(),mode->render.buttons(mode));
        knownFront.bindInvalidation(services::invalidate);
    }
    private Pc1GraphicsPipeline(DataInput in,Pc1Runtime runtime,Pc1GlyphCache.Provider font,Pc1GraphicsResources.Backend images,Services services)throws IOException{
        this(in,runtime,font,images,services,null,null);
    }
    private Pc1GraphicsPipeline(DataInput in,Pc1Runtime runtime,Pc1GlyphCache.Provider font,Pc1GraphicsResources.Backend images,Services services,Pc1HeapLease.Heap heap,Pc1ImagePool.Rebinder rebinder)throws IOException{
        if(runtime==null||services==null)throw new IOException("PC1 graphics restore requires canonical runtime and complete platform services");this.runtime=runtime;this.services=services;
        render=heap==null?Pc1RenderContext.load(in,runtime,runtime.graphicsMemory(),font,images,runtime.nativeCatalogTextContext(),leaves):Pc1RenderContext.load(in,runtime,runtime.graphicsMemory(),font,images,runtime.nativeCatalogTextContext(),leaves,heap,rebinder);
        effects=new Pc1Effects(runtime.graphicsMemory(),render.compositor(),runtime::nativeCrtRandom,effectServices);
        story=new Pc1StoryControls(runtime.graphicsMemory(),storyServices);
        textControls=new Pc1TextControls(runtime.graphicsMemory(),mode->render.buttons(mode));
        render.compositor().bindInvalidation(services::invalidate);
    }
    private byte[] cstring(int at)throws IOException{ByteArrayOutputStream out=new ByteArrayOutputStream();for(int n=0;n<1024*1024;n++){int b=runtime.nativeMemory().readAddress(at+n);if(b==0)return out.toByteArray();out.write(b);}throw new IOException("PC1 graphics C string leaves known bounded memory");}
    public Pc1RenderContext render(){return render;}
    public Pc1Runtime runtime(){return runtime;}
    public void requireBoundary()throws IOException{if(depth!=0)throw new IOException("PC1 graphics pipeline snapshot during native consumer");render.requireBoundary();}
    private void nativeCall(Pc1RenderContext.NativeCall call)throws IOException{depth++;try{render.nativeCall(call);}finally{depth--;}}
    /** Native2B keeps FF busy writes separate from40F560. */
    public void commandEffect(int kind,int strength)throws IOException{nativeCall(()->{if(kind==255)runtime.graphicsMemory().writeInt(Pc1Effects.BUSY,strength);else effects.initialize(kind,strength);});}
    public void updateEffects()throws IOException{nativeCall(()->effects.update());}
    public void drawEffects(int layer)throws IOException{nativeCall(()->effects.drawLayer(layer));}
    /** Only reviewed effect prefix of418DF0; enclosing window dispatch owns
     *timer-token comparisons and the remaining head/wait/audio consumers. */
    public void timerEffects()throws IOException{nativeCall(()->effects.timerEffects());}
    public void timerDrawEffects(int layer)throws IOException{nativeCall(()->effects.timerDrawLayer(layer));}
    public void redrawEffectOverlaps()throws IOException{nativeCall(()->effects.redrawOverlaps());}
    public void paintEffectRectangles()throws IOException{nativeCall(()->effects.paintRectangles());}
    public void drawStoryControls()throws IOException{nativeCall(()->story.draw());}
    public void restoreExtraControls()throws IOException{nativeCall(()->story.restoreExtra());}
    public void restoreTextControls(int draw)throws IOException{nativeCall(()->textControls.restore(draw));}
    public void activateTextRegions()throws IOException{nativeCall(()->textControls.activate());}
    /** Runtime saves effect/global/control bytes once. This saves the separate
     *pixel pool, resident names, glyph cache and actual font context only. */
    public void save(DataOutput out)throws IOException{requireBoundary();render.save(out);}
    public static Pc1GraphicsPipeline load(DataInput in,Pc1Runtime runtime,Pc1GlyphCache.Provider font,Pc1GraphicsResources.Backend images,Services services)throws IOException{return new Pc1GraphicsPipeline(in,runtime,font,images,services);}
    public static Pc1GraphicsPipeline load(DataInput in,Pc1Runtime runtime,Pc1GlyphCache.Provider font,Pc1GraphicsResources.Backend images,Services services,Pc1HeapLease.Heap heap,Pc1ImagePool.Rebinder rebinder)throws IOException{if(heap==null||rebinder==null)throw new IOException("PC1 pipeline restore requires actual heap/lease rebinding");return new Pc1GraphicsPipeline(in,runtime,font,images,services,heap,rebinder);}
}
