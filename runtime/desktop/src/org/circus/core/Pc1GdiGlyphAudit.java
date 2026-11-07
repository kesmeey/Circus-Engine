package org.circus.core;

import java.io.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;

/** Real local Win32 API audit of our JNI backend. No original EXE is run;
 * it is not a comparison against original screenshots or original OS fonts. */
public final class Pc1GdiGlyphAudit {
    private static int count;
    private static void check(boolean v,String why){count++;if(!v)throw new AssertionError(why);}
    private static byte[] wire(Pc1GdiGlyph.State state)throws IOException{ByteArrayOutputStream out=new ByteArrayOutputStream();state.save(new DataOutputStream(out));return out.toByteArray();}
    private static Pc1GdiGlyph.State unwire(byte[] raw)throws IOException{return Pc1GdiGlyph.State.load(new DataInputStream(new ByteArrayInputStream(raw)));}
    private static Pc1FontMemory.Image rawImage(Pc1FontMemory memory)throws IOException{ByteArrayOutputStream out=new ByteArrayOutputStream();memory.save(new DataOutputStream(out));return Pc1FontMemory.load(new DataInputStream(new ByteArrayInputStream(out.toByteArray())));}
    private static String hex(byte[] raw){StringBuilder s=new StringBuilder();for(byte b:raw)s.append(String.format(Locale.ROOT,"%02x",b&255));return s.toString();}
    public static void main(String[] args)throws Exception{
        if(args.length!=2)throw new IOException("Expected built DLL path and audit output directory");Path output=Paths.get(args[1]);Files.createDirectories(output);List<String> lines=new ArrayList<>(),vectors=new ArrayList<>();int ink=0;
        try(Pc1GdiGlyph gdi=new Pc1GdiGlyph(Paths.get(args[0]))){
            lines.add(gdi.environment());Pc1GdiGlyph.State initial=gdi.snapshot();byte[] initialWire=wire(initial);Pc1FontMemory initialRaw=gdi.fontMemory().copy();check(initial.selectedSize==28&&gdi.fontMemory().requestedSize()==0&&gdi.fontMemory().ascent()==0&&Arrays.equals(initialWire,wire(unwire(initialWire))),"initial font context snapshot is exact and independent from glyph caches");Pc1GlyphRaster.Outline zero=gdi.outline(new byte[]{65,0},0,0);check(zero.ascent==0,"initial cached requested-size0 preserves native BSS ascent0 and initially selected28 font");gdi.outline(new byte[]{65,0},0,12);gdi.restore(unwire(initialWire));gdi.fontMemory().restore(rawImage(initialRaw));check(gdi.outline(new byte[]{65,0},0,0).ascent==0&&gdi.environment().contains("actual=28"),"restoring initial0 state does not synthesize a font22 selection or metrics query");
            NativeRgbImage canvas=NativeRgbImage.zeroed(240,160);for(int row=0;row<160;row++)for(int col=0;col<240;col++)canvas.setRgb(col,row,0x204060);
            int x=5,y=5;for(int size:new int[]{12,28,32,40,17}){
                for(byte[] raw:new byte[][]{{65,0},{(byte)0xd6,(byte)0xd0,0}}){byte[] beforeScratch=gdi.scratch();Pc1GlyphRaster.Outline glyph=gdi.outline(raw,0,size),again=gdi.outline(raw,0,size);check(Arrays.equals(glyph.monoBytes(),again.monoBytes())&&glyph.ascent==again.ascent&&glyph.originX==again.originX&&glyph.originY==again.originY,"same GDI owner returns identical actual monochrome payload");byte[] mask=new byte[1600];Pc1GlyphRaster.rasterizeInto(size,glyph,mask,0);int nonzero=0;for(byte b:mask){if(size!=17)check((b&255)<=4,"normal GDI glyph has actual0..4 supersample coverage");if(b!=0)nonzero++;}check(nonzero>0,"actual Win32 glyph has ink");ink+=nonzero;Pc1GlyphRaster.draw(canvas,Pc1Raster.Clip.full(canvas),x,y,size,mask,0,0xe7e3d9);x+=size+5;lines.add(size+":"+Pc1GlyphRaster.characterCode(raw,0)+":"+glyph.width+"x"+glyph.height+":ascent="+glyph.ascent+":mono="+Binary.sha256(glyph.monoBytes())+":mask="+Binary.sha256(mask)+":"+gdi.environment());vectors.add("{\"required\":"+gdi.lastRequiredByteCount()+",\"before_scratch\":\""+hex(beforeScratch)+"\",\"size\":"+size+",\"width\":"+glyph.width+",\"height\":"+glyph.height+",\"ox\":"+glyph.originX+",\"oy\":"+glyph.originY+",\"ascent\":"+glyph.ascent+",\"raw\":\""+hex(raw)+"\",\"mono\":\""+hex(glyph.monoBytes())+"\",\"java_mask_sha256\":\""+Binary.sha256(mask)+"\"}");}
                y+=size+5;x=5;
            }
            Pc1GdiGlyph.State latest=gdi.snapshot();byte[] saved=wire(latest);Pc1FontMemory savedRaw=gdi.fontMemory().copy();Pc1GlyphRaster.Outline prior=gdi.outline(new byte[]{65,0},0,17);byte[] priorPhysical=gdi.saveFontContext();gdi.outline(new byte[]{65,0},0,40);gdi.restore(unwire(saved));gdi.fontMemory().restore(rawImage(savedRaw));Pc1GlyphRaster.Outline resumed=gdi.outline(new byte[]{65,0},0,17);check(Arrays.equals(prior.monoBytes(),resumed.monoBytes())&&prior.ascent==resumed.ascent&&Arrays.equals(priorPhysical,wire(gdi.snapshot())),"restored requested17/font22/ascent context produces exact same current-GDI payload");String before=gdi.environment();byte[] wrong=saved.clone();DataInputStream locate=new DataInputStream(new ByteArrayInputStream(wrong));locate.readInt();locate.readInt();locate.readInt();locate.readUTF();int identityAt=wrong.length-locate.available()+2;wrong[identityAt]=wrong[identityAt]=='0'?(byte)'1':(byte)'0';boolean rejected=false;try{gdi.restore(unwire(wrong));}catch(IOException expected){rejected=expected.getMessage().contains("environment differs");}check(rejected&&before.equals(gdi.environment()),"font environment mismatch rejects before replacing live native context");
            Pc1GlyphRaster.Outline changedZero=gdi.outline(new byte[]{65,0},0,0);check(changedZero.ascent>0&&gdi.environment().contains("actual=22"),"later requested0 selects real22 fallback after a different cached size");lines.add(gdi.environment());
            BufferedImage image=new BufferedImage(canvas.width,canvas.height,BufferedImage.TYPE_INT_ARGB);image.setRGB(0,0,canvas.width,canvas.height,canvas.argb(),0,canvas.width);ImageIO.write(image,"PNG",output.resolve("actual-gdi-kernel.png").toFile());image.flush();
        }
        Files.write(output.resolve("gdi-payloads.txt"),lines,StandardCharsets.UTF_8);Files.write(output.resolve("gdi-vectors.json"),("["+String.join(",\n",vectors)+"]\n").getBytes(StandardCharsets.UTF_8));String result="{\"pass\":true,\"checks\":"+count+",\"actual_win32_api\":true,\"original_exe_executed\":false,\"native_screenshot_comparison\":false,\"glyph_ink_cells\":"+ink+"}";Files.write(output.resolve("result.json"),result.getBytes(StandardCharsets.UTF_8));System.out.println("PASS Pc1GdiGlyphAudit "+count);
    }
}
