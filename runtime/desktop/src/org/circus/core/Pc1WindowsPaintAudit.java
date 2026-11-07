package org.circus.core;

import java.io.*;
import java.nio.file.*;
import java.util.*;

/** Actual hidden WM_PAINT/GDI leaves on self-authored pixels; no original
 *EXE/visible window, accelerated output, or completed engine dispatcher claim. */
public final class Pc1WindowsPaintAudit {
    private static int checks;
    private static void check(boolean ok,String why){checks++;if(!ok)throw new AssertionError(why);}
    private static final class Host implements Pc1WindowsWindow.PaintHost {
        Pc1Compositor compositor;int outer,inner,ready=1,color,accelerated,resets,sync,reads;final List<String> trace=new ArrayList<>();
        public int global(int address)throws IOException{trace.add("g:"+Integer.toHexString(address));switch(address){case 0x94c68c:return ++reads==1?outer:inner;case 0x94c694:return ready;case 0x94c61c:return color;case 0x94c620:return accelerated;default:throw new IOException("unowned paint global:"+Integer.toHexString(address));}}
        public byte[] frontBgr24()throws IOException{sync++;trace.add("front");return compositor.rgb(0).bgr24();}
        public int[] dirtyForPresent()throws IOException{trace.add("dirty");Pc1Raster.Rect r=compositor.dirtyForPresent();return new int[]{r.left,r.top,r.right,r.bottom};}
        public void resetDirty()throws IOException{trace.add("reset");resets++;compositor.resetDirty();}
        public void accelerated(long dib,int[] rect)throws IOException{trace.add("accelerated");throw new IOException("required native403360 accelerated owner unavailable");}
        void start(){reads=0;trace.clear();}
    }
    private static void expectedRect(byte[] expected,byte[] front,int left,int top,int right,int bottom){for(int y=top;y<bottom;y++)System.arraycopy(front,(599-y)*2400+left*3,expected,(599-y)*2400+left*3,(right-left)*3);}
    private static void paint(Pc1WindowsWindow window,Host host)throws IOException{host.start();check(window.postMessage(0xf,0,0)!=0,"Post actual hidden WM_PAINT");window.pumpMessages();}
    public static void main(String[] args)throws Exception{
        if(args.length!=1)throw new IOException("Expected explicitly built windowJNI");Pc1WindowsWindow[] current={null};Host host=new Host();Pc1WindowsWindow.NativeDispatcher dispatcher=new Pc1WindowsWindow.NativeDispatcher(){public Pc1WindowsWindow.Dispatch dispatch(long hwnd,int message,long wp,long lp)throws IOException{return message==0xf&&current[0]!=null?current[0].paintCurrent(host):Pc1WindowsWindow.Dispatch.unhandled();}public int quitFlag(){return 0;}};
        try(Pc1WindowsWindow window=new Pc1WindowsWindow(Paths.get(args[0]),dispatcher)){
            current[0]=window;host.compositor=new Pc1Compositor(window.dibRgb());byte[] front=host.compositor.rgb(0).bgr24(),expected=new byte[front.length];for(int n=0;n<front.length;n++)front[n]=(byte)(n*29+(n>>>8));host.compositor.installRgb(0,NativeRgbImage.fromBgr24(800,600,2400,front));host.compositor.touch(8,12,20,30);paint(window,host);long[] report=window.lastPaintReport();check(report[2]==4&&report[6]!=0&&report[20]==1,"Actual mapped normal GDI path executes source copy and canonical reset");expectedRect(expected,front,8,12,20,30);check(Arrays.equals(expected,window.compatibleRgb().bgr24()),"All1.44MB selectedDDB pixels: dirty region fromDIB, outside retains black");check(host.trace.equals(Arrays.asList("g:94c68c","g:94c68c","g:94c694","g:94c61c","dirty","front","reset")),"Native gate/dirty/front/reset call order without eager flag snapshot");check(host.compositor.rawDirty().left==800&&host.compositor.rawDirty().top==600,"407680 happens after actual paint");System.out.println("firstPaintReport="+Arrays.toString(report));
            front[(599)*2400]=3;front[(599)*2400+1]=7;front[(599)*2400+2]=11;host.compositor.installRgb(0,NativeRgbImage.fromBgr24(800,600,2400,front));paint(window,host);expectedRect(expected,front,0,0,1,1);check(Arrays.equals(expected,window.compatibleRgb().bgr24()),"Empty canonical dirty publishes only native0,0,1,1, preserving previous DDB pixels");
            host.ready=0;host.compositor.touch(40,40,50,50);int resets=host.resets,sync=host.sync;paint(window,host);report=window.lastPaintReport();check(report[2]==2&&report[18]!=-1&&host.resets==resets&&host.sync==sync,"Unready black-paint branch never syncsDIB or consumes canonical dirty");check(Arrays.equals(expected,window.compatibleRgb().bgr24()),"Black paint targets window paintDC, not cached DDB");check(host.compositor.rawDirty().left==40,"Black branch retains pending dirty for a future ready paint");
            host.ready=1;host.outer=1;paint(window,host);check(window.lastPaintReport()[2]==0&&host.trace.equals(Arrays.asList("g:94c68c")),"Outer mode gate delegates actual DefWindowProc without entering406CB0");check(host.resets==resets,"Outer default branch does not resetdirty");host.outer=0;host.inner=1;paint(window,host);check(window.lastPaintReport()[2]==1&&host.trace.equals(Arrays.asList("g:94c68c","g:94c68c")),"Live mode reread after BeginPaint selects EndPaint-only branch");check(host.resets==resets,"Inner mode gate does not consume dirty");host.inner=0;host.color=1;host.accelerated=0;paint(window,host);report=window.lastPaintReport();check(report[2]==4&&host.trace.contains("g:94c620"),"Color index alone still uses GDI unless accelerated flag also nonzero");expectedRect(expected,front,40,40,50,50);check(Arrays.equals(expected,window.compatibleRgb().bgr24()),"Pending dirty is finally published on ready GDI branch");
            check(window.windowInfo()[1]==0,"Pixel/paint audit remains hidden");System.out.println("finalCompatibleSha256="+Binary.sha256(window.compatibleRgb().bgr24()));
            host.accelerated=1;host.compositor.touch(3,4,9,10);resets=host.resets;boolean failed=false;try{paint(window,host);}catch(IOException unsupported){failed=unsupported.getMessage().contains("403360");}check(failed,"Unimplemented accelerated owner never falls back or claims success");report=window.lastPaintReport();check(report[2]==3&&report[5]!=-1&&report[19]==1&&host.resets==resets,"Accelerated strict gate follows EndPaint and preserves dirty/prefix without reset");check(!host.trace.contains("dirty")&&!host.trace.contains("reset"),"Accelerated branch does not consume normal GDI dirty getter or false-reset after fault");System.out.println("acceleratedFaultReport="+Arrays.toString(report));
        }
        System.out.println("Pc1WindowsPaintAudit PASS "+checks+"; actual hidden WM_PAINT/GDI/DDB full-pixel readback; visible display/accelerated403360 not claimed");
    }
}
