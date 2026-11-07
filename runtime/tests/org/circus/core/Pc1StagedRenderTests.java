package org.circus.core;
import java.io.*;
import java.util.Arrays;

/** Whole render wire preflight and same-owner staging; physical OS services
 *are explicit test inputs, not reconstructed by the font cache. */
public final class Pc1StagedRenderTests {
    private static int checks;
    private static void check(boolean yes,String why){checks++;if(!yes)throw new AssertionError(why);}
    private static final class Font implements Pc1GlyphCache.Provider {
        final Pc1FontMemory memory=new Pc1FontMemory();final Pc1RenderContextTests.Font masks=new Pc1RenderContextTests.Font();int selected,restores,saves;boolean mutate;
        public Pc1FontMemory fontMemory(){return memory;}
        public Pc1GlyphRaster.Outline outline(byte[] raw,int offset,int size)throws IOException{selected=size;return masks.outline(raw,offset,size);}
        public byte[] saveFontContext(){saves++;if(mutate)memory.requestedSize(99);return new byte[]{(byte)selected};}
        public void restoreFontContext(byte[] state)throws IOException{restores++;throw new IOException("physical world must be staged independently");}
    }
    private static final class Heap implements Pc1HeapLease.Heap {
        int rebinds;
        public Pc1HeapLease allocate(int length)throws IOException{return Pc1HeapLease.managed(new byte[length],"explicit staged-render fixture heap");}
        public boolean release(Pc1HeapLease lease){return true;}
        Pc1HeapLease rebind(Pc1ImagePool.SavedLease saved)throws IOException{rebinds++;if(saved.kind!=Pc1HeapLease.AddressKind.MANAGED)throw new IOException("fixture only supports its declared managed bytes");return Pc1HeapLease.managed(saved.backingBytes(),saved.authority);}
    }
    public static int run()throws Exception{
        checks=0;Font sourceFont=new Font();Pc1RenderContextTests.Fixture source=new Pc1RenderContextTests.Fixture(sourceFont,null);Heap sourceHeap=new Heap();source.context=new Pc1RenderContext(source.host.pc1,source.memory,new Pc1Compositor(Pc1RenderContextTests.solid(64,48,0x102030),sourceHeap),Pc1GlyphCache.fromMemory(sourceFont.memory),sourceFont,source.backend,source.expansion,source.leaves);source.initializeKnownScene();source.memory.writeInt(Pc1DrawBatch.PALETTE,0xffffff);source.host.pc1.codeBuffer().writeBytes(100,new byte[]{'A','B',0});source.context.drawInlineAt(3,4,0,Pc1NativeMemory.CODE_BASE+100,0);byte[] runtime=source.runtimeBytes(),wire=source.renderBytes();int saves=sourceFont.saves;
        Pc1RenderContext.Image image=Pc1RenderContext.loadImage(new DataInputStream(new ByteArrayInputStream(wire)));check(sourceFont.saves==saves&&sourceFont.restores==0&&image.compositorImage().poolImage()!=null,"decode entire render image before any provider or layout installation");
        byte[] truncated=Arrays.copyOf(wire,wire.length-1);boolean rejected=false;try{Pc1RenderContext.loadImage(new DataInputStream(new ByteArrayInputStream(truncated)));}catch(IOException expected){rejected=true;}check(rejected&&sourceHeap.rebinds==0&&sourceFont.restores==0,"truncated trailing physical capsule cannot allocate/restore earlier pixel owners");
        byte[] context=image.glyphImage().physicalContext();context[0]^=1;check(image.glyphImage().physicalContext()[0]==(byte)sourceFont.selected,"physical capsule accessor cannot mutate decoded snapshot");
        Font stagedFont=new Font();Pc1RenderContextTests.Fixture target=new Pc1RenderContextTests.Fixture(stagedFont,null);target.host.pc1.load(new DataInputStream(new ByteArrayInputStream(runtime)));stagedFont.memory.restore(image.glyphImage().fontMemoryImage());stagedFont.selected=sourceFont.selected;Heap stagedHeap=new Heap();Pc1ImagePool pool=new Pc1ImagePool(stagedHeap);Pc1Compositor compositor=Pc1Compositor.deferred(pool);target.context=new Pc1RenderContext(target.host.pc1,target.memory,compositor,Pc1GlyphCache.fromMemory(stagedFont.memory),stagedFont,target.backend,target.expansion,target.leaves);check(stagedHeap.rebinds==0&&stagedFont.restores==0,"declare canonical fixed views while physical image restoration is still pending");
        target.context.restorePixels(image,stagedHeap::rebind);check(target.context.compositor()==compositor&&compositor.pool()==pool&&stagedHeap.rebinds==1,"same predeclared pool/compositor adopt physical image bytes without replacing borrowed owners");target.context.finishStagedRestore(image);check(stagedFont.restores==0&&stagedFont.memory==target.context.glyphCache().fontMemory(),"staged attach validates active provider and preserves its restored raw memory");
        check(Arrays.equals(runtime,target.runtimeBytes())&&Arrays.equals(wire,target.renderBytes()),"whole Runtime/render serialization is byte-exact after staged adoption");
        check(Arrays.equals(source.context.compositor().front().bgr24(),target.context.compositor().front().bgr24())&&target.context.compositor().front().rgb(3,4)==0xffffff,"restored native glyph cache and actual pool front match source pixels");
        int before=stagedHeap.rebinds;rejected=false;try{target.context.restorePixels(image,stagedHeap::rebind);}catch(IOException expected){rejected=true;}check(rejected&&stagedHeap.rebinds==before,"active pool cannot be replaced by replaying staged installation");
        stagedFont.selected++;rejected=false;try{target.context.finishStagedRestore(image);}catch(IOException expected){rejected=true;}check(rejected&&stagedFont.memory.matchesImage(image.glyphImage().fontMemoryImage()),"different physical state rejects without reinitializing canonical cache");
        stagedFont.selected=sourceFont.selected;stagedFont.mutate=true;rejected=false;try{target.context.finishStagedRestore(image);}catch(IOException expected){rejected=true;}check(rejected&&stagedFont.memory.requestedSize()==99,"provider verification mutation is detected and its prefix is not overwritten by another restore");return checks;
    }
    public static void main(String[] args)throws Exception{System.out.println("PASS "+run()+" staged render restoration checks");}
}
