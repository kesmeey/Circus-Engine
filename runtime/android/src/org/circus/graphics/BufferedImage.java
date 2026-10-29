package org.circus.graphics;
import android.graphics.Bitmap;
import java.util.ArrayDeque;
/** Only the image operations used by the shared renderer; not a general AWT implementation. */
public final class BufferedImage {
    public static final int TYPE_INT_RGB=1,TYPE_INT_ARGB=2;
    // The Unity viewport otherwise allocates 2.25 MiB on every animation frame.
    // A wrapper still owns its pixels until flush(): never reuse a live frame.
    // Keep the most recently retired bitmap for one additional retirement before
    // reuse, so the UI does not immediately overwrite its previous presentation.
    private static final int FRAME_WIDTH=1024,FRAME_HEIGHT=576,FREE_FRAMES=2;
    private static final ArrayDeque<Bitmap> frames=new ArrayDeque<>();
    private static boolean pooling=true;private static long created,reused,retired;
    public final Bitmap bitmap;
    private final boolean frame;private boolean released;
    public BufferedImage(int width,int height,int type){frame=width==FRAME_WIDTH&&height==FRAME_HEIGHT;bitmap=allocate(width,height,frame);if(type==TYPE_INT_RGB)bitmap.eraseColor(0xff000000);}
    public BufferedImage(Bitmap bitmap){this.bitmap=bitmap;frame=false;}
    private static synchronized Bitmap allocate(int width,int height,boolean frame){
        Bitmap result=null;if(frame&&pooling&&frames.size()==FREE_FRAMES){result=frames.removeFirst();reused++;}
        if(result==null){result=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888);if(frame)created++;}
        else result.eraseColor(0); // preserve the fresh ARGB constructor's transparent pixels
        return result;
    }
    private static synchronized void retire(Bitmap bitmap,boolean frame){
        if(bitmap.isRecycled())return;
        if(frame&&pooling){retired++;if(frames.size()==FREE_FRAMES)frames.removeFirst().recycle();frames.addLast(bitmap);}
        else bitmap.recycle();
    }
    /** Diagnostic A/B control; changing it only releases already retired buffers. */
    public static synchronized void setFramePoolingForAudit(boolean enabled){pooling=enabled;while(!frames.isEmpty())frames.removeFirst().recycle();}
    /** Created / reused / retired viewport buffers and current free-buffer count. */
    public static synchronized long[] frameAllocationStats(){return new long[]{created,reused,retired,frames.size()};}
    public int getWidth(){return bitmap.getWidth();}public int getHeight(){return bitmap.getHeight();}
    public Graphics2D createGraphics(){return new Graphics2D(bitmap);}
    public int getRGB(int x,int y){return bitmap.getPixel(x,y);}public void setRGB(int x,int y,int rgb){bitmap.setPixel(x,y,rgb);}
    public int[] getRGB(int x,int y,int w,int h,int[] data,int offset,int stride){if(data==null)data=new int[offset+(h-1)*stride+w];bitmap.getPixels(data,offset,stride,x,y,w,h);return data;}
    public void setRGB(int x,int y,int w,int h,int[] data,int offset,int stride){bitmap.setPixels(data,offset,stride,x,y,w,h);}
    public synchronized void flush(){if(released)return;released=true;retire(bitmap,frame);}
}
