package org.circus.core;

import java.io.*;

/** Current PC1 CN native RGB24 kernels. Resource handles, palette lookup,
 * active-target selection and dirty-rectangle union belong to the caller.
 * Each returned Rect is exactly the clipped407580 argument rectangle, even
 * when empty/inverted. Native407580 unions it only for active image0. */
public final class Pc1Raster {
    private Pc1Raster(){}
    public static final int TRANSPARENT=0xff00ff;
    public static final class Rect {
        public final int left,top,right,bottom;
        public Rect(int left,int top,int right,int bottom){this.left=left;this.top=top;this.right=right;this.bottom=bottom;}
        public boolean empty(){return right<=left||bottom<=top;}
    }
    /** Native BC5F60/68/5C/64, with right/bottom exclusive. */
    public static final class Clip {
        public final int left,top,right,bottom;
        public Clip(int left,int top,int right,int bottom){this.left=left;this.top=top;this.right=right;this.bottom=bottom;}
        public static Clip full(NativeRgbImage image){return new Clip(0,0,image.width,image.height);}
    }
    private static void validate(NativeRgbImage target,Clip clip)throws IOException {
        if(target==null||clip==null)throw new IOException("PC1 raster clip outside known target");
        //407700/407780 preserve empty and reversed rectangles. Every draw
        // intersection stays empty, but407580 still receives its raw bounds.
        if(clip.right<=clip.left||clip.bottom<=clip.top)return;
        if(clip.left<0||clip.top<0||clip.right>target.width||clip.bottom>target.height)throw new IOException("PC1 raster clip outside known target");
    }
    private static Rect rectangle(NativeRgbImage target,Clip clip,int left,int top,int right,int bottom)throws IOException {validate(target,clip);return new Rect(Math.max(left,clip.left),Math.max(top,clip.top),Math.min(right,clip.right),Math.min(bottom,clip.bottom));}
    private static Rect source(NativeRgbImage target,Clip clip,NativeRgbImage source,int x,int y)throws IOException {
        if(source==null)throw new IOException("PC1 raster missing source");return clipImage(target,clip,source.width,source.height,x,y);
    }
    /** Shared native image rectangle for RGB24 and inverse-alpha sources. */
    public static Rect clipImage(NativeRgbImage target,Clip clip,int sourceWidth,int sourceHeight,int x,int y)throws IOException{
        if(sourceWidth<=0||sourceHeight<=0)throw new IOException("PC1 raster invalid source dimensions");long right=(long)x+sourceWidth,bottom=(long)y+sourceHeight;if(right>Integer.MAX_VALUE||right<Integer.MIN_VALUE||bottom>Integer.MAX_VALUE||bottom<Integer.MIN_VALUE)throw new IOException("PC1 raster unmodelled coordinate overflow");return rectangle(target,clip,x,y,(int)right,(int)bottom);
    }
    private static void weight(int ratio)throws IOException{if(ratio<0||ratio>16)throw new IOException("PC1 raster ratio outside verified0..16");}
    private static int channel(int destination,int source,int ratio){return (destination*(16-ratio)+source*ratio)>>>4;}
    /** Shared16-denominator primitive for4079A0/4083C0/408DC0 and return surfaces.
     * Inputs are RGB; alpha has no native role and is not returned. */
    public static int blendRgb16(int destination,int source,int ratio){
        if(ratio<0||ratio>16)throw new IllegalArgumentException("PC1 raster ratio outside verified0..16");
        return channel(destination&255,source&255,ratio)|channel(destination>>>8&255,source>>>8&255,ratio)<<8|channel(destination>>>16&255,source>>>16&255,ratio)<<16;
    }
    private static int word(NativeRgbImage data,int at)throws IOException{return data.byteAt(at)|data.byteAt(at+1)<<8|data.byteAt(at+2)<<16|data.byteAt(at+3)<<24;}
    private static void word(NativeRgbImage data,int at,int value)throws IOException{for(int n=0;n<4;n++)data.byteAt(at+n,value>>>(n*8));}
    private static int blendWord(int destination,int source,int ratio,boolean scale){int out=0;for(int n=0;n<4;n++)out|=(scale?((source>>>(n*8)&255)*ratio)>>>4:channel(destination>>>(n*8)&255,source>>>(n*8)&255,ratio))<<(n*8);return out;}
    /**407880: row order top-to-bottom, forward dword copies then byte tail.
     * This deliberately preserves same-storage overlap; it is not memmove. */
    public static Rect copy(NativeRgbImage target,Clip clip,NativeRgbImage source,int x,int y)throws IOException {
        Rect r=source(target,clip,source,x,y);if(r.empty())return r;int length=(r.right-r.left)*3;
        for(int row=r.top;row<r.bottom;row++){int a=source.offset(r.left-x,row-y),b=target.offset(r.left,row),n=0;for(;n+4<=length;n+=4)word(target,b+n,word(source,a+n));for(;n<length;n++)target.byteAt(b+n,source.byteAt(a+n));}return r;
    }
    /**4079A0 feedback. Each packed group consists of three sequential DWORD
     * reads/writes; scalar tails preserve native alias ordering. */
    public static Rect feedback(NativeRgbImage target,Clip clip,NativeRgbImage source,int x,int y,int ratio)throws IOException{return weighted(target,clip,source,x,y,ratio,false);}
    /**408880 scales the source absolutely at origin0,0. It does not feed back
     * destination and is a separate operation from4079A0. */
    public static Rect scale(NativeRgbImage target,Clip clip,NativeRgbImage source,int ratio)throws IOException{return weighted(target,clip,source,0,0,ratio,true);}
    private static Rect weighted(NativeRgbImage target,Clip clip,NativeRgbImage source,int x,int y,int ratio,boolean scale)throws IOException {
        weight(ratio);Rect r=source(target,clip,source,x,y);if(r.empty())return r;int width=r.right-r.left,packed=(width/4)*12;
        for(int row=r.top;row<r.bottom;row++){int a=source.offset(r.left-x,row-y),b=target.offset(r.left,row),n=0;for(;n<packed;n+=4){int s=word(source,a+n),d=scale?0:word(target,b+n);word(target,b+n,blendWord(d,s,ratio,scale));}for(;n<width*3;n++){int s=source.byteAt(a+n)&255,d=target.byteAt(b+n)&255;target.byteAt(b+n,(scale?(s*ratio)>>>4:channel(d,s,ratio)));}}return r;
    }
    /**407EE0 ignores exact RGB magenta. Native format1 delegates407C90;
     * that separate alpha layout is deliberately not accepted by this type. */
    public static Rect transparent(NativeRgbImage target,Clip clip,NativeRgbImage source,int x,int y)throws IOException{return transparentWeighted(target,clip,source,x,y,16);}
    /**4083C0 RGB24 path:16-denominator opacity, skipping exact magenta. */
    public static Rect opacity(NativeRgbImage target,Clip clip,NativeRgbImage source,int x,int y,int ratio)throws IOException{return transparentWeighted(target,clip,source,x,y,ratio);}
    private static Rect transparentWeighted(NativeRgbImage target,Clip clip,NativeRgbImage source,int x,int y,int ratio)throws IOException {
        weight(ratio);Rect r=source(target,clip,source,x,y);if(r.empty())return r;
        for(int row=r.top;row<r.bottom;row++)for(int col=r.left;col<r.right;col++){int a=source.offset(col-x,row-y),b=target.offset(col,row);int rgb=(source.byteAt(a)&255)|(source.byteAt(a+1)&255)<<8|(source.byteAt(a+2)&255)<<16;if(rgb==TRANSPARENT)continue;for(int n=0;n<3;n++)target.byteAt(b+n,channel(target.byteAt(b+n)&255,source.byteAt(a+n)&255,ratio));}return r;
    }
    /**4085A0 explicit RGB24 mask path. Mask uses its first B byte at each RGB
     * pixel:255 preserves destination,0 copies, other values divide by255.
     * Source magenta always preserves destination. */
    public static Rect masked(NativeRgbImage target,Clip clip,NativeRgbImage source,int x,int y,NativeRgbImage mask)throws IOException {
        if(mask==null)return transparent(target,clip,source,x,y);Rect r=source(target,clip,source,x,y);if(r.empty())return r;
        if(r.right-x>mask.width||r.bottom-y>mask.height)throw new IOException("PC1 mask outside known image");
        for(int row=r.top;row<r.bottom;row++)for(int col=r.left;col<r.right;col++){int a=source.offset(col-x,row-y),b=target.offset(col,row),m=mask.byteAt(mask.offset(col-x,row-y))&255;int rgb=(source.byteAt(a)&255)|(source.byteAt(a+1)&255)<<8|(source.byteAt(a+2)&255)<<16;if(m==255||rgb==TRANSPARENT)continue;for(int n=0;n<3;n++)target.byteAt(b+n,(((target.byteAt(b+n)&255)*m+(source.byteAt(a+n)&255)*(255-m))/255));}return r;
    }
    /**408D00: caller resolves native palette G,R,B,0 to a0xRRGGBB value. */
    public static Rect fill(NativeRgbImage target,Clip clip,int left,int top,int right,int bottom,int rgb)throws IOException{return fillWeighted(target,clip,left,top,right,bottom,rgb,16);}
    /**408DC0: blend solid palette color using denominator16, no alpha. */
    public static Rect fillBlend16(NativeRgbImage target,Clip clip,int left,int top,int right,int bottom,int rgb,int ratio)throws IOException{return fillWeighted(target,clip,left,top,right,bottom,rgb,ratio);}
    private static Rect fillWeighted(NativeRgbImage target,Clip clip,int left,int top,int right,int bottom,int rgb,int ratio)throws IOException {
        weight(ratio);Rect r=rectangle(target,clip,left,top,right,bottom);if(r.empty())return r;
        for(int row=r.top;row<r.bottom;row++)for(int col=r.left;col<r.right;col++){int b=target.offset(col,row);for(int n=0;n<3;n++)target.byteAt(b+n,channel(target.byteAt(b+n)&255,rgb>>>(n*8)&255,ratio));}return r;
    }
}
