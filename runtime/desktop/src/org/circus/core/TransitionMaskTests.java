package org.circus.core;

import java.awt.image.BufferedImage;
import java.util.Arrays;

/** Pixel oracle for both Java2D and Android's premultiplied Bitmap backend. */
public final class TransitionMaskTests {
    public static int run(){
        DesktopTransitionMask batch=new DesktopTransitionMask();int checks=0;
        // Same size, upsample, downsample, and equal pixel count with different dimensions.
        for(int[] size:new int[][]{{16,16,16,16},{17,11,5,3},{5,3,17,11},{11,17,5,3}}){
            int w=size[0],h=size[1],mw=size[2],mh=size[3];
            BufferedImage mask=new BufferedImage(mw,mh,BufferedImage.TYPE_INT_ARGB);
            BufferedImage other=new BufferedImage(mw,mh,BufferedImage.TYPE_INT_ARGB);
            for(int y=0;y<mh;y++)for(int x=0;x<mw;x++){
                int b=(y*mw+x)&255;
                mask.setRGB(x,y,0xff000000|((255-b)<<16)|(b*17&255)<<8|b);
                other.setRGB(x,y,0xff000000|(255-b));
            }
            for(int mode:new int[]{17,20,21})for(long elapsed:new long[]{0,1,249,499,500,501,999,1000}){
                Dc3Transition transition=new Dc3Transition();transition.mode=mode;transition.duration=1000;transition.elapsed=elapsed;
                // Change mask identity and back while exercising the same reusable buffers.
                for(BufferedImage m:new BufferedImage[]{mask,mask,other,other,mask}){
                    BufferedImage expected=new BufferedImage(w,h,BufferedImage.TYPE_INT_ARGB),actual=new BufferedImage(w,h,BufferedImage.TYPE_INT_ARGB);
                    for(int y=0;y<h;y++)for(int x=0;x<w;x++){
                        int pixel=((x+y)%3==0?0:(x+y)%3==1?0x7f000000:0xff000000)|((x*31&255)<<16)|((y*57&255)<<8)|(x*13+y*19&255);
                        expected.setRGB(x,y,pixel);actual.setRGB(x,y,pixel);
                    }
                    for(int y=0;y<h;y++)for(int x=0;x<w;x++){
                        int blue=m.getRGB(x*mw/w,y*mh/h)&255;
                        expected.setRGB(x,y,(expected.getRGB(x,y)&0xffffff)|(transition.maskAlpha(blue)<<24));
                    }
                    batch.apply(actual,m,transition);
                    if(!Arrays.equals(expected.getRGB(0,0,w,h,null,0,w),actual.getRGB(0,0,w,h,null,0,w)))throw new AssertionError("Transition pixels: mode="+mode+" elapsed="+elapsed+" size="+Arrays.toString(size));
                    checks++;expected.flush();actual.flush();
                }
                if(elapsed==500)batch.clear();
            }
            // Keep the same mask object while swapping target dimensions (same area).
            BufferedImage resized=new BufferedImage(h,w,BufferedImage.TYPE_INT_ARGB);
            Dc3Transition halfway=new Dc3Transition();halfway.mode=21;halfway.duration=1000;halfway.elapsed=500;
            batch.apply(resized,mask,halfway);
            for(int y=0;y<w;y++)for(int x=0;x<h;x++){
                int expected=halfway.maskAlpha(mask.getRGB(x*mw/h,y*mh/w)&255);
                if((resized.getRGB(x,y)>>>24)!=expected)throw new AssertionError("Resized transition mask");
            }
            checks++;resized.flush();
            mask.flush();other.flush();
        }
        batch.clear();return checks;
    }
    public static void main(String[] args){System.out.println("Transition mask "+run()+" pixel comparisons PASS");}
}
