package org.circus.core;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import javax.imageio.ImageIO;

/** Synthesized raw glyph journals and private current-CN draw-call references.
 * This is neither an original-game screenshot nor a native font/GDI oracle. */
public final class Pc1GlyphAudit {
    private static int count;
    private static void check(boolean value,String why){count++;if(!value)throw new AssertionError(why);}
    private interface Work {void run()throws Exception;}
    private static void reject(Work work,String text)throws Exception{boolean rejected=false;try{work.run();}catch(IOException e){rejected=String.valueOf(e.getMessage()).contains(text);}check(rejected,"Reject "+text);}
    private static byte[] hex(String s){byte[] b=new byte[s.length()/2];for(int i=0;i<b.length;i++)b[i]=(byte)Integer.parseInt(s.substring(i*2,i*2+2),16);return b;}
    private static Pc1TextLayout fresh()throws Exception{Pc1TextLayout l=new Pc1TextLayout(new Pc1TextLayout.Config(20,36,200,16,6,0x1234,0xfda3,0xafa3));l.position(20,40,28);return l;}
    private static Pc1TextLayout.Source body(String s)throws Exception{byte[] b=new byte[1024],raw=hex(s);System.arraycopy(raw,0,b,0,raw.length);return new Pc1TextLayout.Source("body",b);}
    private static BufferedImage canvas(){BufferedImage b=new BufferedImage(320,160,BufferedImage.TYPE_INT_ARGB);int[] pixels=new int[320*160];Arrays.fill(pixels,0xff204060);b.setRGB(0,0,320,160,pixels,0,320);return b;}
    private static int[] palette(){int[] p=new int[64];Arrays.fill(p,0xff112233);p[0]=0xff000000;p[1]=0xffffffff;p[10]=0xffa08040;return p;}
    private static java.util.List<int[]> batch(Pc1TextLayout.Snapshot s){return Collections.singletonList(new int[]{0,s.glyphs().size()});}
    private static final String[][] NATIVE={
        {"81418142","2625","8","r,18,41,19,67,10,8;r,19,39,20,69,10,8;r,20,38,48,70,10,8;g,20,40,1,28,8141;r,76,39,77,69,10,8;r,77,41,78,67,10,8;r,48,38,76,70,10,8;g,48,40,1,28,8142"},
        {"81418142","2753","8","r,18,41,19,67,10,8;r,19,39,20,69,10,8;r,20,38,48,70,10,8;g,21,41,0,28,8141;g,20,40,1,28,8141;r,76,39,77,69,10,8;r,77,41,78,67,10,8;r,48,38,76,70,10,8;g,49,41,0,28,8142;g,48,40,1,28,8142"},
        {"81418142","19009","0","r,18,41,19,67,10,16;r,19,39,20,69,10,16;r,20,38,48,70,10,16;g,20,40,1,28,8141;r,76,39,77,69,10,16;r,77,41,78,67,10,16;r,48,38,76,70,10,16;g,48,40,1,28,8142"},
        {"81418142","2625","0","g,18,40,1,28,8141;g,48,40,1,28,8142"},
        {"81418142","2625","31","r,18,41,19,67,10,31;r,19,39,20,69,10,31;r,20,38,48,70,10,31;g,20,40,1,28,8141;r,76,39,77,69,10,31;r,77,41,78,67,10,31;r,48,38,76,70,10,31;g,48,40,1,28,8142"},
        {"816f819ca3af8141a3fd8170","2753","8","r,18,41,19,67,10,8;r,19,39,20,69,10,8;r,20,38,48,70,10,8;g,21,41,0,28,8141;g,20,40,1,28,8141;r,48,38,76,70,10,8;g,49,41,0,28,a3fd;g,48,40,1,28,a3fd;g,29,30,0,12,819c;g,28,29,1,12,819c"},
        {"816f8145a3af8141a3fd8170","2689","8","g,21,41,0,28,8141;g,20,40,1,28,8141;g,49,41,0,28,a3fd;g,48,40,1,28,a3fd;g,21,25,0,28,3f3f;g,20,24,1,28,3f3f"}
    };
    public static void main(String[] args)throws Exception{
        count=0;Path output=Paths.get(args.length==0?"build/native-rewrite/pc1-glyph-desktop-stage3":args[0]);Files.createDirectories(output);int[] palette=palette();
        for(String[] row:NATIVE){Pc1TextLayout l=fresh();l.body(body(row[0]),Integer.parseInt(row[1]),100);BufferedImage image=canvas();java.util.List<String> trace=new ArrayList<>();try{DesktopLegacy.rasterPc1Text(image,null,l.freeze(),batch(l.freeze()),Collections.<int[]>emptyList(),palette,Integer.parseInt(row[2]),(g,raw,x,y,p,size)->{},trace);check(String.join(";",trace).equals(row[3]),"Current4170C0 integer call trace style="+row[1]+" shade="+row[2]+" actual="+trace);}finally{image.flush();}}
        Pc1TextLayout fill=fresh();fill.body(body("8141"),0x0a41,2);BufferedImage image=canvas();try{DesktopLegacy.rasterPc1Text(image,null,fill.freeze(),batch(fill.freeze()),Collections.<int[]>emptyList(),palette,8,(g,raw,x,y,p,size)->{},null);check(image.getRGB(25,50)==0xff606050,"Exact 8/16 native RGB mix");check(image.getRGB(18,40)==0xff204060&&image.getRGB(18,41)==0xff606050&&image.getRGB(19,39)==0xff606050,"Native stepped decoration cap pixels");ImageIO.write(image,"PNG",output.resolve("style40-native-blend.png").toFile());}finally{image.flush();}
        image=canvas();try{DesktopLegacy.rasterPc1Text(image,null,fill.freeze(),batch(fill.freeze()),Collections.<int[]>emptyList(),palette,31,(g,raw,x,y,p,size)->{},null);check(image.getRGB(25,50)==0xff18bc22,"Native out-of-range weight wraps bytes rather than alpha clamp");}finally{image.flush();}
        Pc1TextLayout journal=fresh();Pc1TextLayout.Result a=journal.body(body("8141"),0x4a41,2),b=journal.body(body("404b8142"),1,100);Pc1TextLayout.Snapshot snap=journal.freeze();java.util.List<int[]> batches=Arrays.asList(new int[]{a.firstGlyph,b.firstGlyph},new int[]{b.firstGlyph,snap.glyphs().size()});Pc1TextLayout.Patch p=b.patches.get(0);java.util.List<int[]> patches=Collections.singletonList(new int[]{b.firstGlyph,p.left,p.top,p.right,p.bottom,0});BufferedImage target=canvas(),underlay=canvas();try{DesktopLegacy.rasterPc1Text(target,underlay,snap,batches,patches,palette,0,(g,raw,x,y,c,size)->{},null);check(target.getRGB(47,50)==0xffa08040&&target.getRGB(49,50)==0xff204060,"@K restores clean underlay between draw batches");reject(()->DesktopLegacy.rasterPc1Text(target,null,snap,batches,patches,palette,0,(g,raw,x,y,c,size)->{},null),"underlay patch owner");ImageIO.write(target,"PNG",output.resolve("patch-underlay.png").toFile());}finally{target.flush();underlay.flush();}
        Pc1TextLayout raw=fresh();raw.body(body("4142"),0x81,100);BufferedImage text=canvas();TextCodec codec=TextCodec.identity();try{DesktopLegacy.rasterPc1Text(text,null,raw.freeze(),batch(raw.freeze()),Collections.<int[]>emptyList(),palette,0,(g,bytes,x,y,c,size)->DesktopLegacy.paintPc1Glyph(g,bytes,x,y,c,size,codec,palette),null);int changed=0;for(int value:text.getRGB(0,0,320,160,null,0,320))if(value!=0xff204060)changed++;check(changed>20,"Actual platform font callback paints glyph at journal coordinates");ImageIO.write(text,"PNG",output.resolve("platform-font-approximation.png").toFile());ByteArrayOutputStream bytes=new ByteArrayOutputStream();raw.freeze().save(new DataOutputStream(bytes));Pc1TextLayout.Snapshot restored=Pc1TextLayout.Snapshot.load(new DataInputStream(new ByteArrayInputStream(bytes.toByteArray())));BufferedImage restoredImage=canvas();try{DesktopLegacy.rasterPc1Text(restoredImage,null,restored,batch(restored),Collections.<int[]>emptyList(),palette,0,(g,r,x,y,c,size)->DesktopLegacy.paintPc1Glyph(g,r,x,y,c,size,codec,palette),null);check(Arrays.equals(text.getRGB(0,0,320,160,null,0,320),restoredImage.getRGB(0,0,320,160,null,0,320)),"Portable journal roundtrip yields identical platform raster");}finally{restoredImage.flush();}}finally{text.flush();}
        String result="{\"pass\":true,\"checks\":"+count+",\"native_draw_trace_vectors\":7,\"original_exe_executed\":false,\"original_screenshots_compared\":false,\"platform_font_is_approximate\":true}";Files.write(output.resolve("result.json"),result.getBytes(java.nio.charset.StandardCharsets.UTF_8));System.out.println("PC1 glyph journal desktop audit PASS "+count);
    }
}
