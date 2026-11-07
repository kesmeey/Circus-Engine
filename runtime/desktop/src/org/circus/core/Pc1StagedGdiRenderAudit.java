package org.circus.core;
import java.io.*;
import java.nio.file.*;
import java.util.Arrays;

/** Real staged font/DC/DIB/registry/layout adoption through the complete
 *render wire; RGB front memory is an explicitly declared managed fixture. */
public final class Pc1StagedGdiRenderAudit {
    private static int checks;
    private static void check(boolean yes,String why){checks++;if(!yes)throw new AssertionError(why);}
    private interface Saved {void save(DataOutput out)throws IOException;}
    private static byte[] save(Saved work)throws IOException{ByteArrayOutputStream out=new ByteArrayOutputStream();work.save(new DataOutputStream(out));return out.toByteArray();}
    private static DataInput input(byte[] raw){return new DataInputStream(new ByteArrayInputStream(raw));}
    private static byte[] unhex(String raw){byte[] bytes=new byte[raw.length()/2];for(int n=0;n<bytes.length;n++)bytes[n]=(byte)Integer.parseInt(raw.substring(n*2,n*2+2),16);return bytes;}
    private static final class Physical implements Pc1HeapLease.Heap {
        public Pc1HeapLease allocate(int bytes)throws IOException{return Pc1HeapLease.managed(new byte[bytes],"explicit staged GDI front fixture");}
        public boolean release(Pc1HeapLease lease){return true;}
    }
    private static final class World implements AutoCloseable {
        final Pc1RenderContextTests.Fixture fixture;final Pc1GuestAddressSpace space;final Pc1OsRegistry registry;final Pc1OsWords words;final Pc1FontMemory font=new Pc1FontMemory();final Pc1GdiGlyph.FontSet fonts;final Pc1ImagePool pool;Pc1GdiGlyph glyph;
        World(Path dll,Pc1GuestAddressSpace.Image layout)throws Exception{
            fixture=new Pc1RenderContextTests.Fixture(new Pc1RenderContextTests.Font(),null);Pc1NativeMemory bus=fixture.host.pc1.nativeMemory();space=layout==null?new Pc1GuestAddressSpace(Pc1GuestAddressSpace.Layout.imageHeapV1()):Pc1GuestAddressSpace.emptyLike(layout);space.bindTo(bus);registry=new Pc1OsRegistry(space);words=new Pc1OsWords(bus,registry);font.bindTo(bus);byte[] info=unhex("2800000050000000500000000100180000000000004b000000000000000000000000000000000000");bus.bind(0x42fcb0,40,new Pc1NativeMemory.Region(){public int read(int at){return info[at]&255;}public void write(int at,int value){info[at]=(byte)value;}});fonts=Pc1GdiGlyph.FontSet.empty(dll);fonts.bindOsWords(words,"actual-staged-render");pool=new Pc1ImagePool(new Pc1GuestHeap(space,new Physical()));
        }
        void render(Pc1Compositor compositor)throws IOException{fixture.context=new Pc1RenderContext(fixture.host.pc1,fixture.memory,compositor,Pc1GlyphCache.fromMemory(font),glyph,fixture.backend,fixture.expansion,fixture.leaves);}
        public void close()throws IOException{if(glyph!=null)glyph.close();fonts.close();}
    }
    public static void main(String[] args)throws Exception{
        if(args.length!=1)throw new IOException("Expected exact bound ABI2 GDI DLL");Path dll=Paths.get(args[0]);byte[] face={(byte)0xba,(byte)0xda,(byte)0xcc,(byte)0xe5,0};
        try(World source=new World(dll,null)){
            source.fonts.font(Pc1OsWords.FONT28,source.fonts.createFont(56,28,face));source.glyph=source.fonts.initialize404230(0,source.font);source.pool.importFront(64,48,Pc1HeapLease.managed(Pc1RenderContextTests.solid(64,48,0x102030).bgr24(),"explicit staged GDI front fixture"));source.render(new Pc1Compositor(source.pool));source.fixture.initializeKnownScene();source.fixture.memory.writeInt(Pc1DrawBatch.PALETTE+7*4,0xffffff);source.fixture.context.drawRawGlyphs(3,4,new byte[]{65,0},7,28);source.glyph.borrowScratchLease().writeU8(19199,73);
            source.fonts.font(Pc1OsWords.FONT40,source.fonts.createFont(80,40,face));source.words.rawWord(Pc1OsWords.FONT28,source.words.rawWord(Pc1OsWords.FONT40));byte[] runtime=source.fixture.runtimeBytes(),render=source.fixture.renderBytes(),wordBytes=save(source.words::save),registryBytes=save(source.registry::save),fontSetBytes=save(source.fonts.snapshot()::save);Pc1RenderContext.Image image=Pc1RenderContext.loadImage(input(render));Pc1GuestAddressSpace.Image layout=image.compositorImage().poolImage().guestLayout();Pc1GdiGlyph.State physical=Pc1GdiGlyph.State.load(input(image.glyphImage().physicalContext()));Pc1HeapLease oldScratch=source.glyph.borrowScratchLease();
            try(World target=new World(dll,layout)){
                target.fixture.host.pc1.load(input(runtime));target.font.restore(image.glyphImage().fontMemoryImage());target.fonts.stage(Pc1GdiGlyph.FontSet.State.load(input(fontSetBytes)));target.glyph=target.fonts.contextForRestore(0,target.font,physical);Pc1Compositor staged=Pc1Compositor.deferred(target.pool);target.render(staged);target.glyph.completePhysicalRestore(null);target.glyph.stageScratch(layout);target.registry.restoreInto(Pc1OsRegistry.loadImage(input(registryBytes)),layout,saved->target.fonts.bindingForOwner(saved.owner));target.fixture.context.restorePixels(image,saved->Pc1HeapLease.managed(saved.backingBytes(),saved.authority));target.words.restore(Pc1OsWords.loadImage(input(wordBytes)));target.glyph.activateRestored();target.fixture.context.finishStagedRestore(image);
                check(target.fixture.context.compositor()==staged&&staged.pool()==target.pool,"borrowed predeclared pool/compositor identities survive whole staging");check(target.glyph.borrowScratchLease()!=oldScratch&&target.glyph.borrowScratchLease().observedAddress()!=oldScratch.observedAddress(),"new actual DIB storage replaces saved physical address without payload shadow");check(target.space.addressOf(target.glyph.borrowScratchLease())==source.space.addressOf(oldScratch)&&target.glyph.borrowScratchLease().readU8(19199)==73,"sole pool layout preserves exact scratch Guest VA and stale physical suffix");
                check(Arrays.equals(runtime,target.fixture.runtimeBytes())&&Arrays.equals(render,target.fixture.renderBytes()),"complete Runtime/render bytes match after actual physical world adoption");check(Arrays.equals(wordBytes,save(target.words::save))&&Arrays.equals(registryBytes,save(target.registry::save)),"raw words and physical association graph round-trip independently");
                source.fixture.context.drawRawGlyphs(10,5,new byte[]{66,0},7,28);target.fixture.context.drawRawGlyphs(10,5,new byte[]{66,0},7,28);check(Arrays.equals(source.fixture.context.compositor().front().bgr24(),target.fixture.context.compositor().front().bgr24()),"new real cache miss keeps selected28 independently from raw FONT28 pointing to40");check(Arrays.equals(source.fixture.renderBytes(),target.fixture.renderBytes()),"continued actual GDI mask/cache/scratch and pixels serialize identically");
            }
        }
        System.out.println("PASS "+checks+" actual staged GDI/render checks; complete window/audio world restoration remains separate");
    }
}
