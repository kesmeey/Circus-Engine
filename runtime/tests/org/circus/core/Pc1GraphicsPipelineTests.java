package org.circus.core;

import java.io.*;
import java.util.*;

/** Synthetic inputs exercise the production graph, with explicit platform
 * fixtures. This does not claim physical timer/audio/window adoption. */
public final class Pc1GraphicsPipelineTests {
    private static int count;
    private static void check(boolean value,String why){count++;if(!value)throw new AssertionError(why);}
    private static byte[] wire(Pc1GraphicsPipeline p)throws IOException{ByteArrayOutputStream out=new ByteArrayOutputStream();p.save(new DataOutputStream(out));return out.toByteArray();}
    private static final class Platform implements Pc1GraphicsPipeline.Services {
        Pc1GraphicsPipeline owner;int timers,cursors,invalidations;boolean graphBlocked,contextBlocked;
        public void invalidate(Pc1Raster.Rect rectangle){invalidations++;}
        public Pc1UiRaster.Point cursorClient(){cursors++;return new Pc1UiRaster.Point(0,0);}
        public void sound(int id,int mode)throws IOException{throw new IOException("fixture unexpected physical sound");}
        public void voice(int pointer)throws IOException{throw new IOException("fixture unexpected native voice list");}
        public void specialListItem(int visible,int data,boolean selected)throws IOException{throw new IOException("fixture unimplemented special list item");}
        public void ensureTimer5()throws IOException{
            timers++;try{wire(owner);}catch(IOException expected){graphBlocked=true;}
            try{ByteArrayOutputStream out=new ByteArrayOutputStream();owner.render().save(new DataOutputStream(out));}catch(IOException expected){contextBlocked=true;}
            if(owner.runtime().graphicsMemory().readInt(0x94c67c)==0)owner.runtime().graphicsMemory().writeInt(0x94c67c,37);
        }
        public void beginNativePaint()throws IOException{throw new IOException("fixture unimplemented physical GetDC");}
        public void paintRectangle(int l,int t,int r,int b)throws IOException{throw new IOException("fixture unimplemented physical paint");}
        public void endNativePaint()throws IOException{throw new IOException("fixture unimplemented physical ReleaseDC");}
    }
    private static void particle(Pc1GraphicsMemory memory)throws IOException{
        for(int n=0;n<50;n++)memory.writeInt(Pc1Effects.PARTICLES+n*100+8,0);
        for(int[] entry:new int[][]{{0,0},{8,1},{12,2},{16,3},{36,4},{40,3},{72,0},{76,0}})memory.writeInt(Pc1Effects.PARTICLES+entry[0],entry[1]);
        memory.writeInt(Pc1Effects.RECT_COUNT,0);memory.writeInt(Pc1Effects.PREVIOUS_COUNT,0);
    }
    public static int run()throws Exception{
        count=0;Pc1RenderContextTests.Font font=new Pc1RenderContextTests.Font();Pc1RenderContextTests.Fixture f=new Pc1RenderContextTests.Fixture(font,null);
        Platform platform=new Platform();Pc1GraphicsPipeline p=new Pc1GraphicsPipeline(f.host.pc1,new Pc1Compositor(Pc1RenderContextTests.solid(64,48,0x102030)),Pc1GlyphCache.zeroBss(),font,f.backend,platform);platform.owner=p;f.context=p.render();f.backend.observed=f.context;f.initializeKnownScene();
        f.backend.images.put("bg",Pc1GraphicsResources.Source.rgb(Pc1RenderContextTests.solid(64,48,0x204060)));
        f.backend.images.put("system\\SACRA01",Pc1GraphicsResources.Source.rgb(Pc1RenderContextTests.solid(4,3,0xe0c040)));
        p.render().path(0,new byte[]{'b','g'});f.memory.writeInt(Pc1Effects.ENABLED,1);f.memory.writeInt(0x43e100,123);
        int seed=f.memory.readInt(0x43e100);p.commandEffect(1,1);int atlas=f.memory.readInt(Pc1Effects.SPRITE_HANDLE);
        check(atlas>0&&p.render().compositor().loaded(atlas)&&f.memory.readInt(Pc1Effects.KIND)==1,"native initializer loads actual mutable PE atlas name into same persistent pool");
        check(f.memory.readInt(0x43e100)!=seed&&platform.timers==1&&f.memory.readInt(0x94c67c)==37,"effect initializer shares CRT owner and required timer service");
        check(platform.graphBlocked&&platform.contextBlocked&&f.backend.snapshotBlocked,"whole initializer fences graph and context snapshots across image IO and timer callback");
        int afterRandom=f.memory.readInt(0x43e100);p.commandEffect(255,-7);check(f.memory.readInt(Pc1Effects.BUSY)==-7&&f.memory.readInt(Pc1Effects.KIND)==1&&f.memory.readInt(0x43e100)==afterRandom,"raw2B FF writes busy without initializing effects or consuming RNG");p.commandEffect(255,0);
        particle(f.memory);p.render().restoreBody(0,0,64,48,0);
        NativeRgbImage expected=Pc1RenderContextTests.solid(64,48,0x204060);for(int y=3;y<6;y++)for(int x=2;x<6;x++)expected.setRgb(x,y,0x808050);
        check(Arrays.equals(expected.bgr24(),p.render().compositor().front().bgr24())&&f.memory.readInt(Pc1Effects.RECT_COUNT)==1,"assembled effects, partial repair and Underlay draw exact whole RGB buffer");
        f.memory.writeInt(0xbb8450,1);f.host.pc1.waitKind(-1);f.memory.writeInt(Pc1NativeUi.STORY_BUTTONS+8,0x76543210);p.drawStoryControls();
        check(f.memory.readInt(Pc1NativeUi.BUTTON_POINTER)==Pc1NativeUi.STORY_BUTTONS&&f.memory.readInt(Pc1NativeUi.BUTTON_COUNT)==1&&f.memory.readInt(Pc1NativeUi.STORY_BUTTONS+8)==0&&platform.cursors==0&&platform.invalidations==1&&p.render().compositor().invalidations().isEmpty(),"same416B80 bridge clears actual visual state and invokes required platform invalidation without accumulating a debug journal");
        byte[] runtime=f.runtimeBytes(),graphics=wire(p);Pc1RenderContextTests.Font nextFont=new Pc1RenderContextTests.Font();Pc1RenderContextTests.Fixture next=new Pc1RenderContextTests.Fixture(nextFont,null);next.backend.images.putAll(f.backend.images);
        next.host.pc1.load(new DataInputStream(new ByteArrayInputStream(runtime)));Platform nextPlatform=new Platform();Pc1GraphicsPipeline restored=Pc1GraphicsPipeline.load(new DataInputStream(new ByteArrayInputStream(graphics)),next.host.pc1,nextFont,next.backend,nextPlatform);nextPlatform.owner=restored;next.context=restored.render();next.backend.observed=next.context;
        check(Arrays.equals(runtime,next.runtimeBytes())&&Arrays.equals(graphics,wire(restored))&&nextPlatform.timers==0,"independent globals/pixel/cache owners restore byte-exactly without replaying effect initialization or pretending OS adoption");
        p.render().restoreBody(0,0,64,48,0);restored.render().restoreBody(0,0,64,48,0);
        check(Arrays.equals(f.runtimeBytes(),next.runtimeBytes())&&Arrays.equals(wire(p),wire(restored)),"restored active effects continue with identical raw banks, pixels, pool and cache");
        p.commandEffect(0,0);check(!p.render().compositor().loaded(atlas)&&f.memory.readInt(Pc1Effects.SPRITE_HANDLE)==-1&&f.memory.readInt(Pc1Effects.KIND)==0,"native release frees the same atlas and resets actual handle");
        return count;
    }
    public static void main(String[] args)throws Exception{System.out.println("PASS "+run()+" connected graphics pipeline checks");}
}
