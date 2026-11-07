package org.circus.core;

import java.io.*;
import java.nio.file.*;
import java.util.*;

/** Read-only real CRX bytes versus4015D0 and407C90 private machine goldens.
 * Arguments: game root, build/native-rewrite/pc1-alpha-stage7 evidence root.
 * Never starts the original executable and never uses ARGB/AWT conversion. */
public final class Pc1AlphaResourceAudit {
    private static int count;
    private static void check(boolean value,String message){count++;if(!value)throw new AssertionError(message);}
    public static void main(String[] args)throws Exception {
        if(args.length!=2)throw new IllegalArgumentException("gameRoot evidenceRoot");count=0;Path game=Paths.get(args[0]),evidence=Paths.get(args[1]);
        byte[] rgbBytes=Files.readAllBytes(game.resolve("AdvData/GRP/SYSTEM/RainChip.CRX"));check(Binary.sha256(rgbBytes).equals("2dcc912ba4dae8c26ce366d0b14bc00d63379895a3d4be1ad1eee97e3b733472"),"Exact real RGB24 CRX identity");Crx.Image rgb=Crx.plain(rgbBytes,true);check(rgb.width==80&&rgb.height==136&&rgb.bpp==24,"4015D0 RGB24 metadata");byte[] flipped=new byte[rgb.pixels.length];for(int y=0;y<rgb.height;y++)System.arraycopy(rgb.pixels,y*rgb.stride,flipped,(rgb.height-1-y)*rgb.stride,rgb.stride);NativeRgbImage nativeRgb=NativeRgbImage.fromBgr24(rgb.width,rgb.height,rgb.stride,flipped);byte[] expectedRgb=Files.readAllBytes(evidence.resolve("rainchip-native.bgr"));check(Binary.sha256(expectedRgb).equals("f132f1c9610e57f2b957dfb7d1aacd233e2d8f1a826e811d45693799e997cbe1"),"RGB24 native golden identity");check(Arrays.equals(nativeRgb.bgr24(),expectedRgb),"Real raw CRX→decoded BGR→bottom-up native4015D0 full bytes");
        byte[] alphaBytes=Files.readAllBytes(game.resolve("AdvData/GRP/SYSTEM/iday.CRX"));check(Binary.sha256(alphaBytes).equals("64212e085881d89d03074b9d3ac35accddf21f0f0b9d738482aa7423bd21bc67"),"Exact real format1 CRX identity");Crx.Image decoded=Crx.plain(alphaBytes,true);check(decoded.width==640&&decoded.height==100&&decoded.bpp==32,"4015D0 format1 metadata");check(Binary.u16(alphaBytes,12)==2&&Binary.u16(alphaBytes,14)==1&&Binary.u16(alphaBytes,18)==0,"Actual compression2 flags1 mode0 bridge scope");NativeInverseAlphaImage alpha=NativeInverseAlphaImage.fromStraightBgra32(decoded.width,decoded.height,decoded.stride,decoded.pixels);byte[] expectedAlpha=Files.readAllBytes(evidence.resolve("iday-native.abgr"));check(Binary.sha256(expectedAlpha).equals("1e2cfb87faebdb31d88fc04be205403c1df65e10e36e2789d78eae89462edb3d"),"Format1 native golden identity");check(Arrays.equals(alpha.abgr32(),expectedAlpha),"Real raw CRX→normalized straightBGRA→native4015D0 complete ABGR bytes");
        NativeRgbImage destination=NativeRgbImage.fromBgr24(32,16,96,Files.readAllBytes(evidence.resolve("iday-crop-target.bgr")));Pc1Raster.Rect rect=Pc1AlphaRaster.draw(destination,Pc1Raster.Clip.full(destination),alpha,-249,-50);byte[] expectedCrop=Files.readAllBytes(evidence.resolve("iday-crop-expected.bgr"));check(Binary.sha256(expectedCrop).equals("a79ac0099ce245c7df7b8991512dde26dca23c227a9a08eb63695266ab7a1202"),"Native actual-source consumer golden identity");check(rect.left==0&&rect.top==0&&rect.right==32&&rect.bottom==16,"Actual source crop matches native clipped rectangle");check(Arrays.equals(destination.bgr24(),expectedCrop),"Real decoded format1 resource→407C90 cropped consumer exact bytes");
        System.out.println("PC1 actual alpha resource assertions: "+count);
    }
}
