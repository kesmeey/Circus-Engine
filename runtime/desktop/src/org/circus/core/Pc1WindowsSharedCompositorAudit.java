package org.circus.core;
import java.io.*;
import java.nio.file.*;
import java.util.*;

/**Actual allocation-backed pool/raster/DIB/GDI chain with self-authored pixels.
 *The same lease is image0 throughout; no original program or visible UI runs. */
public final class Pc1WindowsSharedCompositorAudit {
    private static int checks;
    private static void check(boolean yes,String why){checks++;if(!yes)throw new AssertionError(why);}
    private static final class Host implements Pc1WindowsWindow.PaintHost {
        Pc1Compositor compositor;Pc1WindowsWindow window;int resets,uploads,shared;
        public int global(int at)throws IOException{if(at==0x94c694)return 1;if(at==0x94c68c||at==0x94c61c)return 0;throw new IOException("unexpected paint global");}
        public boolean sharesDib(long address)throws IOException{Pc1HeapLease lease=compositor.imageLease(0);shared++;return window.isDibLease(lease)&&lease.observedAddress()==address;}
        public byte[] frontBgr24()throws IOException{uploads++;throw new IOException("actual shared compositor must not upload another pixel owner");}
        public int[] dirtyForPresent()throws IOException{Pc1Raster.Rect r=compositor.dirtyForPresent();return new int[]{r.left,r.top,r.right,r.bottom};}public void resetDirty()throws IOException{resets++;compositor.resetDirty();}public void accelerated(long dib,int[] rect)throws IOException{throw new IOException("unexpected accelerated leaf");}
    }
    public static void main(String[] args)throws Exception{
        if(args.length!=2)throw new IOException("Expected exact window and image-heap DLLs");Path heapDll=Paths.get(args[1]);long processHeap=Pc1WindowsImageHeap.currentProcessHeap(heapDll);Pc1WindowsWindow[] current={null};Host host=new Host();
        Pc1WindowsWindow.NativeDispatcher dispatcher=new Pc1WindowsWindow.NativeDispatcher(){public Pc1WindowsWindow.Dispatch dispatch(long hwnd,int msg,long wp,long lp)throws IOException{return msg==0xf&&current[0]!=null&&host.compositor!=null?current[0].paintCurrent(host):Pc1WindowsWindow.Dispatch.unhandled();}public int quitFlag(){return 0;}};
        try(Pc1WindowsImageHeap heap=new Pc1WindowsImageHeap(heapDll,()->processHeap);Pc1WindowsWindow window=new Pc1WindowsWindow(Paths.get(args[0]),dispatcher)){
            current[0]=host.window=window;Pc1HeapLease dib=window.borrowDib();Pc1ImagePool pool=new Pc1ImagePool(heap);pool.importFront(800,600,dib);Pc1Compositor c=host.compositor=new Pc1Compositor(pool);check(c.imageLease(0)==dib&&c.pool()==pool,"one image pool borrows exact actual DIB without copied image0");
            c.fill(0,0,800,600,0x123456);NativeRgbImage expected=NativeRgbImage.zeroed(800,600);for(int y=0;y<600;y++)for(int x=0;x<800;x++)expected.setRgb(x,y,0x123456);check(window.readDibByte(0)==0x56,"real core raster store immediately reaches native DIB");
            int rgb=pool.allocate(2,2,0,0);NativeRgbImage source=NativeRgbImage.fromOpaqueArgb(2,2,new int[]{0xffff0000,0xff00ff00,0xff0000ff,0xffffffff});c.installRgb(rgb,source);check(rgb==20&&heap.ownedAllocations()==1,"source image uses actual401050 HeapAlloc-backed pool slot");c.copy(10,11,rgb);for(int y=0;y<2;y++)for(int x=0;x<2;x++)expected.setRgb(10+x,11+y,source.rgb(x,y));
            c.feedback(20,21,rgb,8);for(int y=0;y<2;y++)for(int x=0;x<2;x++)expected.setRgb(20+x,21+y,Pc1Raster.blendRgb16(0x123456,source.rgb(x,y),8));
            byte[] raw={ (byte)128,(byte)255,0,0, 0,9,11,13, 0,7,13,19, (byte)255,99,12,18 };NativeInverseAlphaImage alpha=NativeInverseAlphaImage.fromAbgr32(2,2,8,raw);int ah=pool.allocate(2,2,1,0);c.installAlpha(ah,alpha);c.transparent(30,31,ah);expected.setRgb(30,31,0x130d07);expected.setRgb(31,32,0x0d0b09);expected.setRgb(30,32,0x091aaa);
            check(heap.ownedAllocations()==2&&Arrays.equals(expected.bgr24(),dib.copyBytes()),"copy/feedback/inverse-alpha all mutate same complete physical DIB");check(Arrays.equals(expected.bgr24(),c.front().bgr24()),"compositor front projection reads same current lease bytes");
            window.postMessage(0xf,0,0);window.pumpMessages();check(host.uploads==0&&host.shared==1&&host.resets==1&&window.lastPaintReport()[17]==0,"actual core dirty/present path consumes shared DIB with no upload");check(Arrays.equals(expected.bgr24(),window.compatibleRgb().bgr24()),"all actual selectedDDB bytes equal shared image0 after GDI");
            c.fill(40,41,41,42,0x071b2f);expected.setRgb(40,41,0x071b2f);window.beginNativePaint();window.paintRectangleShared(40,41,41,42,c.imageLease(0));window.endNativePaint();c.resetDirty();check(Arrays.equals(expected.bgr24(),window.compatibleRgb().bgr24()),"effect direct rectangle consumes subsequent core raster writes from same allocation");
            Pc1HeapLease rgbLease=c.imageLease(rgb),alphaLease=c.imageLease(ah);c.free(rgb);c.free(ah);check(!rgbLease.live()&&!alphaLease.live()&&heap.ownedAllocations()==0&&dib.live(),"actual HeapFree retires source allocations while preserving external DIB");check(window.windowInfo()[1]==0,"actual shared compositor audit remains hidden");System.out.println("sharedCompositorSha256="+Binary.sha256(expected.bgr24()));
        }
        System.out.println("Pc1WindowsSharedCompositorAudit PASS "+checks+" actual pool/raster/shared-DIB/GDI checks");
    }
}
