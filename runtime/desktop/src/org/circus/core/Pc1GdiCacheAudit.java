package org.circus.core;

import java.io.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Actual local Win32 GDI with canonical cache + complete native consumers.
 * Synthetic strings/records/pixels only; no original game or EXE is run. */
public final class Pc1GdiCacheAudit {
    private static int checks;
    private static void check(boolean v,String why){checks++;if(!v)throw new AssertionError(why);}
    private static final class Sink implements Pc1GlyphService.Sink {
        NativeRgbImage pixels;int touches;
        Sink()throws IOException{pixels=NativeRgbImage.zeroed(96,64);for(int y=0;y<64;y++)for(int x=0;x<96;x++)pixels.setRgb(x,y,0x204060);}
        public Pc1Raster.Clip clip(){return Pc1Raster.Clip.full(pixels);}
        public int rawPalette(int index){return index==0?0x00000000:index==1?0x00e7d9e3:0x004080a0;}
        public Pc1GlyphRaster.Draw draw(int x,int y,int size,byte[] raw,int offset,int rgb)throws IOException{Pc1GlyphRaster.Draw d=Pc1GlyphRaster.draw(pixels,clip(),x,y,size,raw,offset,rgb);if(d.touched)touches++;return d;}
        public void decoration(int l,int t,int r,int b,int rgb,int weight)throws IOException{Pc1GlyphRaster.Draw d=Pc1GlyphRaster.decoration(pixels,clip(),l,t,r,b,rgb,weight);if(d.touched)touches++;}
    }
    private static byte[] save(Pc1GlyphService s)throws IOException{ByteArrayOutputStream out=new ByteArrayOutputStream();s.save(new DataOutputStream(out));return out.toByteArray();}
    private static byte[] records(){int[][] values={{5,8,33,40,28,0x4ac1,3,0x41},{40,8,52,20,12,0x81,0,0x4581}};ByteArrayOutputStream out=new ByteArrayOutputStream();for(int[] row:values)for(int value:row)for(int n=0;n<4;n++)out.write(value>>>(8*n));return out.toByteArray();}
    public static void main(String[] args)throws Exception{
        if(args.length!=2)throw new IOException("Expected DLL and output paths");Path dll=Paths.get(args[0]),output=Paths.get(args[1]);Files.createDirectories(output);byte[] fullSnapshot;String context,cacheHash,pixelHash;int touches;
        try(Pc1GdiGlyph font=new Pc1GdiGlyph(dll)){
            Pc1GlyphCache cache=Pc1GlyphCache.fromMemory(font.fontMemory());Pc1GlyphCache.Entry a12=cache.lookup(new byte[]{65,0},0,12,font);check(!a12.hit&&font.fontMemory().requestedSize()==12,"first miss initializes actual GDI12");cache.lookup(new byte[]{65,0},0,28,font);byte[] before=font.saveFontContext();Pc1GlyphCache.Entry hit=cache.lookup(new byte[]{65,0},0,12,font);check(hit.hit&&Arrays.equals(before,font.saveFontContext())&&font.fontMemory().requestedSize()==28,"actual cache hit for12 preserves selected28/ascent, no hidden requery");
            Pc1GlyphService service=new Pc1GlyphService(cache,font);Sink sink=new Sink();service.drawRecords(sink,records(),0,2,31);check(sink.touches>5&&!Binary.sha256(sink.pixels.bgr24()).equals(Binary.sha256(new Sink().pixels.bgr24())),"actual native style/decor/shadow/ruby consumer changes actual pixels");check(cache.readI32(Pc1GlyphCache.FIXED_BASE)==65,"existing fixed28 glyph survives later service drawing");
            fullSnapshot=save(service);cacheHash=Binary.sha256(cache.bytes());pixelHash=Binary.sha256(sink.pixels.bgr24());context=font.environment();touches=sink.touches;Files.write(output.resolve("service.p1k2"),fullSnapshot);ByteArrayOutputStream image=new ByteArrayOutputStream();sink.pixels.save(new DataOutputStream(image));Files.write(output.resolve("front.nrg1"),image.toByteArray());
            Pc1GdiGlyph.State state=font.snapshot();check(state.ansiCodePage>0&&!state.faceName.isEmpty()&&state.fontIdentity.matches("[0-9a-f]{64}"),"snapshot explicitly identifies ACP/selected face/font data");
        }
        try(Pc1GdiGlyph font=new Pc1GdiGlyph(dll)){
            check(font.fontMemory().requestedSize()==0,"new actual context starts from native initial size0");Pc1GlyphService service=Pc1GlyphService.load(new DataInputStream(new ByteArrayInputStream(fullSnapshot)),font);check(Binary.sha256(service.cache().bytes()).equals(cacheHash)&&font.environment().equals(context),"fresh backend restores both full cache and actual independent font context");check(Arrays.equals(fullSnapshot,save(service)),"whole service snapshot has byte-exact fresh-owner roundtrip");
            byte[] before=font.saveFontContext();Pc1GlyphCache.Entry old=service.cache().lookup(new byte[]{65,0},0,12,font);check(old.hit&&Arrays.equals(before,font.saveFontContext()),"fresh restored hit does not switch active font to its own bitmap size");Sink sink=new Sink();sink.pixels=NativeRgbImage.load(new DataInputStream(new ByteArrayInputStream(Files.readAllBytes(output.resolve("front.nrg1")))));check(Binary.sha256(sink.pixels.bgr24()).equals(pixelHash),"font/cache snapshots do not reconstruct the separate framebuffer from text");
            service.drawRaw(sink,60,40,new byte[]{66,0},0,1,28);check(sink.touches>0&&!Binary.sha256(sink.pixels.bgr24()).equals(pixelHash),"restored native service accepts a new actual-GDI cache miss");
        }
        String report="{\"pass\":true,\"checks\":"+checks+",\"actual_win32_gdi\":true,\"original_exe_executed\":false,\"synthetic_resources_only\":true,\"cached_front_sha256\":\""+pixelHash+"\",\"cache_sha256\":\""+cacheHash+"\",\"original_touch_calls\":"+touches+"}";Files.write(output.resolve("result.json"),report.getBytes(StandardCharsets.UTF_8));System.out.println("PASS Pc1GdiCacheAudit "+checks);
    }
}
