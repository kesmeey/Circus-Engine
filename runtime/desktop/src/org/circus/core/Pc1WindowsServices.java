package org.circus.core;

import java.io.IOException;

/** Actual HWND leaves for the shared graphics/text graph. This is not the
 *complete405E50 dispatcher or405800 startup; their ordering remains explicit. */
public final class Pc1WindowsServices implements Pc1GraphicsPipeline.Services,Pc1TextServices.Platform,Pc1WindowsWindow.PaintHost {
    public interface Consumers {
        void sound(int id,int mode)throws IOException;
        void voice(int rawCStringPointer)throws IOException;
        void specialListItem(int visible,int data,boolean selected)throws IOException;
        void accelerated(long nativeDib,int[] osUpdateRect)throws IOException;
    }
    private final Pc1Runtime runtime;
    private final Pc1WindowsWindow window;
    private final Consumers consumers;
    private Pc1GraphicsPipeline graphics;
    public Pc1WindowsServices(Pc1Runtime runtime,Pc1WindowsWindow window,Consumers consumers)throws IOException{
        if(runtime==null||window==null||consumers==null)throw new IOException("PC1 Windows services require canonical runtime, actual window and complete consumers");this.runtime=runtime;this.window=window;this.consumers=consumers;
    }
    public void bind(Pc1GraphicsPipeline value)throws IOException{if(value==null||value.runtime()!=runtime||graphics!=null)throw new IOException("PC1 Windows graphics binding requires unique canonical pipeline");graphics=value;}
    private Pc1GraphicsPipeline graphics()throws IOException{if(graphics==null)throw new IOException("PC1 Windows graphics pipeline not bound");return graphics;}
    public int global(int address)throws IOException{return runtime.graphicsMemory().readInt(address);}
    public byte[] frontBgr24()throws IOException{
        NativeRgbImage image=graphics().render().compositor().front();if(image==null||image.width!=800||image.height!=600||image.stride!=2400)throw new IOException("PC1 Windows front requires actual800x600x24/stride2400 owner");return image.bgr24();
    }
    public int[] dirtyForPresent()throws IOException{Pc1Raster.Rect r=graphics().render().compositor().dirtyForPresent();return new int[]{r.left,r.top,r.right,r.bottom};}
    public void resetDirty()throws IOException{graphics().render().compositor().resetDirty();}
    public void accelerated(long nativeDib,int[] rectangle)throws IOException{consumers.accelerated(nativeDib,rectangle);}
    public boolean sharesDib(long physicalAddress)throws IOException{Pc1HeapLease lease=graphics().render().compositor().imageLease(0);return window.isDibLease(lease)&&lease.observedAddress()==physicalAddress;}
    public Pc1WindowsWindow.Dispatch paint()throws IOException{return window.paintCurrent(this);}
    public void invalidate(Pc1Raster.Rect r)throws IOException{window.invalidate(r.left,r.top,r.right,r.bottom,false);}
    public Pc1UiRaster.Point cursorClient()throws IOException{int[] p=window.cursorClient();if(p==null||p.length!=6||p[0]==0)throw new IOException("PC1 actual cursor/client conversion unavailable");return new Pc1UiRaster.Point(p[4],p[5]);}
    public void sound(int id,int mode)throws IOException{consumers.sound(id,mode);}
    public void voice(int pointer)throws IOException{consumers.voice(pointer);}
    public void specialListItem(int visible,int data,boolean selected)throws IOException{consumers.specialListItem(visible,data,selected);}
    /**407080, raw returned timer token shares canonical94C67C. */
    public void ensureEffectTimer()throws IOException{if(global(0x94c67c)==0)runtime.graphicsMemory().writeInt(0x94c67c,window.setTimer(5,40));}
    public void ensureTimer5()throws IOException{ensureEffectTimer();}
    public void beginNativePaint()throws IOException{window.beginNativePaint();}
    public void paintRectangle(int l,int t,int r,int b)throws IOException{Pc1HeapLease lease=graphics().render().compositor().imageLease(0);if(window.isDibLease(lease))window.paintRectangleShared(l,t,r,b,lease);else window.paintRectangle(l,t,r,b,frontBgr24());}
    public void endNativePaint()throws IOException{window.endNativePaint();}
    public int asyncKeyState(int virtualKey)throws IOException{return window.asyncKeyState(virtualKey);}
    public int setTimer(int id,int interval)throws IOException{return window.setTimer(id,interval);}
    public void killTimer(int token)throws IOException{window.killTimer(token);}
    public int tickCount()throws IOException{return window.tickCount();}
    public int localHour()throws IOException{return window.localHour();}
    public void updateWindow()throws IOException{window.updateWindow();}
    public int pumpMessages()throws IOException{return window.pumpMessages();}
    public void postMessage(int message,int wParam,int lParam)throws IOException{window.postMessage(message,wParam&0xffffffffL,lParam&0xffffffffL);}
    public void requireSnapshotBoundary()throws IOException{graphics().requireBoundary();window.requireSnapshotBoundary();}
}
