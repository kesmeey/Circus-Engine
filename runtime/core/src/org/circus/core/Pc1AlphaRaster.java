package org.circus.core;

import java.io.*;

/** Current PC1 CN407C90/4081E0, format1 source to format0 RGB24 target.
 * Neither operation tests source magenta. Native format0 delegation is
 * selected by the caller's resource type, not guessed from source pixels. */
public final class Pc1AlphaRaster {
    private Pc1AlphaRaster(){}
    private static Pc1Raster.Rect rectangle(NativeRgbImage target,Pc1Raster.Clip clip,NativeInverseAlphaImage source,int x,int y)throws IOException {
        if(source==null)throw new IOException("PC1 alpha raster missing source");return Pc1Raster.clipImage(target,clip,source.width,source.height,x,y);
    }
    /**407C90 normal draw. Inverse-alpha255 preserves destination,0 copies,
     * and other values use floor((dst*A+src*(255-A))/255). */
    public static Pc1Raster.Rect draw(NativeRgbImage target,Pc1Raster.Clip clip,NativeInverseAlphaImage source,int x,int y)throws IOException {
        Pc1Raster.Rect r=rectangle(target,clip,source,x,y);if(r.empty())return r;
        for(int row=r.top;row<r.bottom;row++)for(int col=r.left;col<r.right;col++){int a=source.inverseAlpha(col-x,row-y);if(a==255)continue;int s=source.rgb(col-x,row-y);if(a==0){target.setRgb(col,row,s);continue;}int d=target.rgb(col,row),out=0;for(int n=0;n<3;n++)out|=(((d>>>(n*8)&255)*a+(s>>>(n*8)&255)*(255-a))/255)<<(n*8);target.setRgb(col,row,out);}return r;
    }
    /**4081E0 is deliberately different: A255 skips; every other alpha value
     * is ignored, and the external16-denominator opacity alone blends RGB. */
    public static Pc1Raster.Rect opacity(NativeRgbImage target,Pc1Raster.Clip clip,NativeInverseAlphaImage source,int x,int y,int ratio)throws IOException {
        if(ratio<0||ratio>16)throw new IOException("PC1 alpha raster ratio outside verified0..16");Pc1Raster.Rect r=rectangle(target,clip,source,x,y);if(r.empty())return r;
        for(int row=r.top;row<r.bottom;row++)for(int col=r.left;col<r.right;col++)if(source.inverseAlpha(col-x,row-y)!=255)target.setRgb(col,row,Pc1Raster.blendRgb16(target.rgb(col,row),source.rgb(col-x,row-y),ratio));return r;
    }
}
