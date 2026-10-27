package org.circus.graphics;
import android.graphics.Bitmap;
/** Only the image operations used by the shared renderer; not a general AWT implementation. */
public final class BufferedImage {
    public static final int TYPE_INT_RGB=1,TYPE_INT_ARGB=2;
    public final Bitmap bitmap;
    public BufferedImage(int width,int height,int type){bitmap=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888);if(type==TYPE_INT_RGB)bitmap.eraseColor(0xff000000);}
    public BufferedImage(Bitmap bitmap){this.bitmap=bitmap;}
    public int getWidth(){return bitmap.getWidth();}public int getHeight(){return bitmap.getHeight();}
    public Graphics2D createGraphics(){return new Graphics2D(bitmap);}
    public int getRGB(int x,int y){return bitmap.getPixel(x,y);}public void setRGB(int x,int y,int rgb){bitmap.setPixel(x,y,rgb);}
    public int[] getRGB(int x,int y,int w,int h,int[] data,int offset,int stride){if(data==null)data=new int[offset+(h-1)*stride+w];bitmap.getPixels(data,offset,stride,x,y,w,h);return data;}
    public void setRGB(int x,int y,int w,int h,int[] data,int offset,int stride){bitmap.setPixels(data,offset,stride,x,y,w,h);}
    public void flush(){if(!bitmap.isRecycled())bitmap.recycle();}
}
