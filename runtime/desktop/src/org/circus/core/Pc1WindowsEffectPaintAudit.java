package org.circus.core;

import java.io.*;
import java.nio.file.*;
import java.util.*;

/** Actual40F810/406E40 GDI leaves on a hidden self-authored window. Cached
 *DDB readback is evidence; visible pixels or DirectDraw output are not. */
public final class Pc1WindowsEffectPaintAudit {
    private static int checks;
    private static void check(boolean ok,String why){checks++;if(!ok)throw new AssertionError(why);}
    private static void expectedRect(byte[] expected,byte[] front,int l,int t,int r,int b){l=Math.max(0,l);t=Math.max(0,t);r=Math.min(800,r);b=Math.min(600,b);for(int y=t;y<b;y++)System.arraycopy(front,(599-y)*2400+l*3,expected,(599-y)*2400+l*3,(r-l)*3);}
    public static void main(String[] args)throws Exception{
        if(args.length!=1)throw new IOException("Expected explicitly built windowJNI");final int[] paints={0};Pc1WindowsWindow.NativeDispatcher dispatcher=new Pc1WindowsWindow.NativeDispatcher(){public Pc1WindowsWindow.Dispatch dispatch(long hwnd,int message,long wp,long lp){if(message==0xf)paints[0]++;return Pc1WindowsWindow.Dispatch.unhandled();}public int quitFlag(){return 0;}};
        try(Pc1WindowsWindow window=new Pc1WindowsWindow(Paths.get(args[0]),dispatcher)){
            Pc1Compositor compositor=new Pc1Compositor(window.dibRgb());byte[] front=new byte[1440000],expected=new byte[front.length];for(int n=0;n<front.length;n++)front[n]=(byte)(n*43+(n>>>9));compositor.installRgb(0,NativeRgbImage.fromBgr24(800,600,2400,front));compositor.touch(410,420,430,440);
            check(Arrays.equals(expected,window.compatibleRgb().bgr24()),"Independent cachedDDB begins actual black");boolean refused=false;try{window.paintRectangle(0,0,1,1,front);}catch(IOException e){refused=e.getMessage().contains("GetDC lease");}check(refused,"Direct copy requires actual acquired windowDC, never a made-up zero sink");
            long dc=window.beginNativePaint();check(dc!=0,"Actual40F810 GetDC returns opaque windowHDC");refused=false;try{window.requireSnapshotBoundary();}catch(IOException e){refused=e.getMessage().contains("effect GetDC lease");}check(refused,"Snapshot refuses physical GetDC lease in flight");
            long[] packet=window.paintRectangle(13,17,31,47,compositor.rgb(0).bgr24());check(packet.length==9&&packet[0]!=0&&packet[1]!=0&&packet[3]!=0&&packet[4]==dc,"406E40 actual firstcopy and bothGdiFlush leaves retain borrowedHDC");check(packet[5]==13&&packet[6]==17&&packet[7]==31&&packet[8]==47,"Raw rectangle is passed unchanged");expectedRect(expected,front,13,17,31,47);check(Arrays.equals(expected,window.compatibleRgb().bgr24()),"Full1.44MB cachedDDB equals only direct rectangle, outside unchanged");
            for(int n=0;n<front.length;n++)front[n]=(byte)(n*11+97);compositor.installRgb(0,NativeRgbImage.fromBgr24(800,600,2400,front));packet=window.paintRectangle(24,34,50,55,compositor.rgb(0).bgr24());check(packet[0]!=0&&packet[4]==dc,"Second rectangle reuses acquired DC and current canonical front");expectedRect(expected,front,24,34,50,55);check(Arrays.equals(expected,window.compatibleRgb().bgr24()),"Overlapping rect takes fresh front bytes while old-only region retains prior front");
            packet=window.paintRectangle(-4,-3,7,6,front);check(packet[5]==-4&&packet[6]==-3&&packet[7]==7&&packet[8]==6,"Signed off-edge coordinates reach GDI without Java clamping");expectedRect(expected,front,-4,-3,7,6);check(Arrays.equals(expected,window.compatibleRgb().bgr24()),"Actual GDI clipping leaves all outside cached pixels unchanged");
            packet=window.paintRectangle(100,100,100,100,front);check(packet[5]==100&&packet[7]==100,"Empty rectangle is an actual GDI call, not widened to fullscreen");check(Arrays.equals(expected,window.compatibleRgb().bgr24()),"Actual empty GDI rectangle changes no cached pixels");check(window.endNativePaint()!=0,"Actual40F8C6 ReleaseDC succeeds");window.requireSnapshotBoundary();check(compositor.rawDirty().left==410&&compositor.rawDirty().top==420,"OS triplet never consumes canonical dirty; enclosing407680 remains separate");
            check(window.beginNativePaint()!=0&&window.endNativePaint()!=0,"Zero-rectangle batch still acquires/releases actual DC");check(Arrays.equals(expected,window.compatibleRgb().bgr24()),"Release does not clear the independent cachedDDB");check(window.windowInfo()[1]==0&&paints[0]==0,"Effect output never shows window or synthesizes WM_PAINT/invalidation");System.out.println("effectCachedDdbSha256="+Binary.sha256(window.compatibleRgb().bgr24()));System.out.println("actualEffectLastPacket="+Arrays.toString(packet));
        }
        System.out.println("Pc1WindowsEffectPaintAudit PASS "+checks+"; actual hidden GetDC/2BitBlt/2GdiFlush/ReleaseDC with full cachedDDB readback; visible pixels not claimed");
    }
}
