package org.circus.core;

import java.awt.image.BufferedImage;

/** Bulk pixel access is essential on Android: each get/setRGB otherwise crosses JNI. */
final class DesktopTransitionMask {
    private BufferedImage cachedMask;
    private int width,height;
    private byte[] blue;
    private int[] pixels;
    private final int[] alpha=new int[256];

    void clear(){cachedMask=null;blue=null;pixels=null;width=height=0;}

    void apply(BufferedImage before,BufferedImage mask,Dc3Transition transition){
        int w=before.getWidth(),h=before.getHeight();
        if(cachedMask!=mask||width!=w||height!=h){
            int mw=mask.getWidth(),mh=mask.getHeight();
            int[] source=mask.getRGB(0,0,mw,mh,null,0,mw);
            blue=new byte[w*h];
            // Preserve the original nearest-neighbour sampling and blue channel.
            for(int y=0;y<h;y++)for(int x=0;x<w;x++)blue[y*w+x]=(byte)source[(y*mh/h)*mw+x*mw/w];
            if(pixels==null||pixels.length!=w*h)pixels=new int[w*h];
            cachedMask=mask;width=w;height=h;
        }
        for(int b=0;b<256;b++)alpha[b]=transition.maskAlpha(b)<<24;
        before.getRGB(0,0,w,h,pixels,0,w);
        for(int n=0;n<pixels.length;n++)pixels[n]=(pixels[n]&0x00ffffff)|alpha[blue[n]&255];
        before.setRGB(0,0,w,h,pixels,0,w);
    }
}
