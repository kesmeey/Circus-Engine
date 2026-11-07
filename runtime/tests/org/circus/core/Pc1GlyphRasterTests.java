package org.circus.core;

import java.io.*;
import java.util.*;

/** Literal hashes from actual private4042D0/408F00/4091A0 instructions;
 * synthetic GDI metrics/mono input, not platform-font screenshots. */
public final class Pc1GlyphRasterTests {
    private static int count;
    private static void check(boolean yes,String why){count++;if(!yes)throw new AssertionError(why);}
    private interface Action{void run()throws Exception;}
    private static void reject(Action action,String text)throws Exception{boolean failed=false;try{action.run();}catch(IOException error){failed=text==null||String.valueOf(error.getMessage()).contains(text);}check(failed,"Reject "+text);}
    private static final String[] PROJECTION={
        "ruby12|12|5|7|1|6|8|0|fdeb8f4decfb4f6a66de92ef8337f346dc89c1f89a11beeb9b29d542d6a5850f",
        "negative_odd_x|12|7|5|-1|7|8|0|6eafefb56ad32998c372bcbcf9c1808689c781e29c988902b79e4bb772b8ceee",
        "negative_y_trunc|12|7|5|0|9|8|0|b2a2764b67cb5209db92905b1a0b00fea40b96cee625d8ef933b620eb663bef5",
        "square28|28|56|56|0|48|48|0|a94bfd9964261fc41eb32ee3036b427ba9aa606d8352727640e302c3e0bfefc9",
        "font40|40|71|77|1|70|71|0|233f0a0b9ed55ff349e8c53e549eecad33da1ee9cf6f33229e8193ab38bc2601",
        "fallback17|17|32|32|0|30|30|0|b041768780e0b3326ab75d5217826ee43a91a39a9230373844e7fac16b781a58",
        "one|1|2|2|0|2|2|0|d959eb952ff1a32ab4da8a36891beb48571a85f154cc5b8a6b35422c84df5959",
        "spill_known_suffix|12|30|2|0|2|2|0|755dc9c66c9fecd4274dd40f8d3beb995f54720a55c71ca678aa51519facf5df",
        "known_prefix|12|4|2|-5|2|2|16|63d59d835121aa4814b13a495bfa354b829bf23ce168fca6d8b96658c39add26",
        "empty|28|0|0|0|0|48|0|8505a25d64842f4c88805d6d7fbf1d47628ee776a1473772d791ad95f00e6357",
        "negative_stride_known|-1|7|7|1|6|8|32|cfb789396c15d01d8f616747d257c9856654a634d8c7d027d65ad09db6b996b3",
        "square_wrap_zero|65536|0|0|0|0|48|0|17924d75c2440c5c48196708f4efe62bd5fb4e5e6cf6084c03effd9fd18ea1b2",
    };
    private static final String[] PIXELS={
        "variable_full|12|0|0|0,0,13,11|04d1680fd8153dce87c4b9553b0142ffd2cc516b1264881acd916a4da1cd843d|0,0,12,11",
        "fixed28_full|28|0|0|0,0,13,11|42f616377f658291d1ecc0bc7859f9b8b72c17f1acd0cf5e3b4109f23582bad4|0,0,13,11",
        "left_top|12|-3|-2|1,1,12,10|7fa9864fb36237b0e514ad7939627eff3f1079d2e0b958c49c5949143106f98a|1,1,9,10",
        "bottom_right|12|10|8|0,0,13,11|3f3bde7234d5b3c52a3dabafe047b1f567566fd292b29f7cff073da6bae6781d|10,8,13,11",
        "empty_edge|12|13|0|0,0,13,11|b9db572c746cc9a1a10f75cb39f7680a052c9edf0cacfc2bbe33d6486ece170d|13,0,13,11",
        "inverted|12|0|0|9,8,2,3|b9db572c746cc9a1a10f75cb39f7680a052c9edf0cacfc2bbe33d6486ece170d|",
        "outside|12|30|0|0,0,13,11|b9db572c746cc9a1a10f75cb39f7680a052c9edf0cacfc2bbe33d6486ece170d|",
        "coordinate_wrap|12|2147483647|0|0,0,13,11|b9db572c746cc9a1a10f75cb39f7680a052c9edf0cacfc2bbe33d6486ece170d|",
    };
    private static byte[] mono(int w,int h,int seed){byte[] b=new byte[((w+31)/32)*4*h];for(int n=0;n<b.length;n++)b[n]=(byte)(n*29+seed);return b;}
    public static int run()throws Exception{
        count=0;check(PROJECTION.length==12&&PIXELS.length==8,"All20 private native kernel vectors");
        for(String row:PROJECTION){String[] s=row.split("\\|");int size=Integer.parseInt(s[1]),w=Integer.parseInt(s[2]),h=Integer.parseInt(s[3]),ox=Integer.parseInt(s[4]),oy=Integer.parseInt(s[5]),ascent=Integer.parseInt(s[6]),offset=Integer.parseInt(s[7]);byte[] backing=new byte[1600];for(int n=0;n<backing.length;n++)backing[n]=(byte)(n*37+51);Pc1GlyphRaster.Outline outline=new Pc1GlyphRaster.Outline(ascent,w,h,ox,oy,13,-2,mono(w,h,43));Pc1GlyphRaster.rasterizeInto(size,outline,backing,offset);check(Binary.sha256(backing).equals(s[8]),"whole known native mask backing including stale/prefix aliases "+s[0]);check(outline.cellIncX==13&&outline.cellIncY==-2,"raw GDI increments retained independently from native fixed advance");}
        byte[] coverage=new byte[1600];int[] values={0,1,2,3,4,5,255};for(int n=0;n<coverage.length;n++)coverage[n]=(byte)values[n%values.length];
        for(String row:PIXELS){String[] s=row.split("\\|",-1);int size=Integer.parseInt(s[1]),x=Integer.parseInt(s[2]),y=Integer.parseInt(s[3]);String[] c=s[4].split(",");Pc1Raster.Clip clip=new Pc1Raster.Clip(Integer.parseInt(c[0]),Integer.parseInt(c[1]),Integer.parseInt(c[2]),Integer.parseInt(c[3]));byte[] image=new byte[40*11];for(int n=0;n<image.length;n++)image[n]=(byte)(n*13+17);NativeRgbImage target=NativeRgbImage.fromBgr24(13,11,40,image);Pc1GlyphRaster.Draw draw=Pc1GlyphRaster.draw(target,clip,x,y,size,coverage,0,Pc1GlyphRaster.paletteRgb(0x00d30f8f));check(Binary.sha256(target.bgr24()).equals(s[5]),"native RGB24 full buffer/padding hash "+s[0]);String actual=draw.touched?draw.rectangle.left+","+draw.rectangle.top+","+draw.rectangle.right+","+draw.rectangle.bottom:"";check(actual.equals(s[6]),"actual407580 touch including equal-edge/inverted/wrapped bounds "+s[0]);}
        for(int first=0;first<256;first++){int actual=Pc1GlyphRaster.characterCode(new byte[]{(byte)first,0},0);check(actual==(first>=0x81&&first<=0xfe?first*256:first),"native GDI packing byte "+first);if(first>=0x81&&first<=0xfe){final byte[] one={(byte)first};reject(()->Pc1GlyphRaster.characterCode(one,0),"actual second");}}
        check(Pc1GlyphRaster.characterCode(new byte[]{(byte)255,0x41},0)==255,"FF remains single GDI code although its distinct native cache key reads next");
        for(int size=0;size<=40;size++)check(Pc1GlyphRaster.selectedFontSize(size)==(size==12||size==28||size==32||size==40?size:22),"exact selection22 fallback "+size);
        byte[] input=mono(4,2,255);Pc1GlyphRaster.Outline own=new Pc1GlyphRaster.Outline(2,4,2,0,2,0,0,input);input[0]=0;byte[] exposed=own.monoBytes();exposed[0]=0;check(own.monoBytes()[0]==(byte)255,"mono input/export do not retain aliases");byte[] mask=Pc1GlyphRaster.rasterize(12,own);check(mask[0]==3&&mask[1]==4,"actual two-row MSB-first bit projection with DWORD row stride");
        Pc1GlyphRaster.Outline outside=new Pc1GlyphRaster.Outline(0,2,1,-5,0,0,0,new byte[]{(byte)255,0,0,0});reject(()->Pc1GlyphRaster.rasterize(12,outside),"unowned neighboring");reject(()->new Pc1GlyphRaster.Outline(2,33,2,0,2,0,0,new byte[8]),"known mono");reject(()->Pc1GlyphRaster.rasterize(49,own),"19200-byte");reject(()->Pc1GlyphRaster.characterCode(new byte[0],0),"known raw");
        check(Pc1GlyphRaster.squareByteCount(-1)==1&&Pc1GlyphRaster.squareByteCount(65536)==0&&Pc1GlyphRaster.squareByteCount(48)==2304,"native32-bit square/clear has no fabricated0..40 parameter bound");
        NativeRgbImage odd=NativeRgbImage.zeroed(1,1);odd.setRgb(0,0,0x070b0f);byte[] one={1};Pc1GlyphRaster.draw(odd,Pc1Raster.Clip.full(odd),0,0,1,one,0,0x111315);check(odd.rgb(0,0)==0x080b0f,"native independent shifts differ from a single rounded alpha expression");
        return count;
    }
    public static void main(String[] args)throws Exception{System.out.println("PASS Pc1GlyphRasterTests "+run());}
}
