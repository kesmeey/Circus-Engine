package org.circus.core;
import java.io.*;
import java.nio.file.*;
import java.util.*;

/** Self-owned hidden staged HWND/GDI leaves. No original process, display
 *mode/registry write, visible ShowWindow or complete engine startup claim. */
public final class Pc1WindowsStartupAudit {
    private static int checks;
    private static void check(boolean yes,String why){checks++;if(!yes)throw new AssertionError(why);}
    private static byte[] ascii(String text)throws IOException{return text.getBytes("US-ASCII");}
    private static final class Black implements Pc1WindowsWindow.PaintHost {
        int paints;
        public int global(int address)throws IOException{if(address==0x94c68c||address==0x94c694)return 0;throw new IOException("unexpected pre-front paint global");}
        public byte[] frontBgr24()throws IOException{throw new IOException("pre-front paint must not read DIB");}
        public int[] dirtyForPresent()throws IOException{throw new IOException("pre-front paint must not consume dirty");}
        public void resetDirty()throws IOException{throw new IOException("pre-front paint must not reset dirty");}
        public void accelerated(long dib,int[] rect)throws IOException{throw new IOException("pre-front paint must not call accelerated output");}
    }
    public static void main(String[] args)throws Exception{
        if(args.length!=1)throw new IOException("Expected newly built staged window DLL");Path dll=Paths.get(args[0]);Pc1WindowsWindow[] current={null};Black black=new Black();List<String> trace=new ArrayList<>();long[] created={0};
        Pc1WindowsWindow.NativeDispatcher dispatcher=new Pc1WindowsWindow.NativeDispatcher(){public Pc1WindowsWindow.Dispatch dispatch(long hwnd,int message,long wp,long lp)throws IOException{
            if(message==1){created[0]=hwnd;long[] h=current[0].bootstrapHandles();check(h[0]==hwnd&&h[1]==0&&h[2]==0&&h[3]==0&&h[4]==0,"actual WM_CREATE precedes every physical DC/bitmap");trace.add("create");}
            if(message==0xf){black.paints++;trace.add("paint-before-front");return current[0].paintCurrent(black);}return Pc1WindowsWindow.Dispatch.unhandled();
        }public int quitFlag(){return 0;}};
        try(Pc1WindowsWindow window=Pc1WindowsWindow.deferred(dll,dispatcher)){
            current[0]=window;check(window.hwnd()==0&&created[0]==0,"deferred owner does not register/create/bootstrap implicitly");window.tickCount();check(window.metric(0)>0&&window.metric(1)>0,"actual metrics and tick available before HWND/front");boolean absent=false;try{window.dibRgb();}catch(IOException required){absent=required.getMessage().contains("not initialized");}check(absent,"missing physical DIB is explicit before406ED0");
            long instance=window.moduleInstance(),icon=window.loadIcon(0,32512),cursor=window.loadCursor(0,32512);check(instance!=0&&icon!=0&&cursor!=0,"actual module and explicitly selected audit stock resources");String name="CircusPc1StagedAudit"+Long.toHexString(System.nanoTime());check(window.registerClass(instance,ascii(name),icon,cursor)!=0,"RegisterClassA called separately with real handles");
            int[] adjusted=window.adjustWindowRect(0,0,800,600,0xcf0000,false);check(adjusted.length==5&&adjusted[0]!=0,"actual AdjustWindowRect returns known input/output packet");long hwnd=window.createWindow(0,ascii(name),ascii("Circus owned hidden staged audit"),0xcf0000,0,0,adjusted[3]-adjusted[1],adjusted[4]-adjusted[2],instance);check(hwnd!=0&&created[0]==hwnd,"CreateWindowExA synchronously dispatches supplied HWND with native NULL lpParam");
            long menu=window.systemMenu(hwnd,false);check(menu!=0&&window.appendMenu(menu,0x800,0,new byte[0])!=0&&window.appendMenu(menu,0,0x384,ascii("audit settings"))!=0,"actual system-menu leaves precede Show/Update");window.showWindow(hwnd,0);window.invalidate(0,0,8,8,false);window.postMessage(0xf,0,0);window.pumpMessages();window.updateWindow(hwnd);check(black.paints>0&&window.lastPaintReport()[2]==2,"unready actual WM_PAINT black fill works with no physical front");check(window.bootstrapHandles()[3]==0,"first paint did not bootstrap DIB as a side effect");window.mainWindow(hwnd);Pc1HeapLease[] published={null};window.initializeFrontShared(image->{published[0]=image;check(window.bootstrapHandles()[3]!=0&&window.bootstrapTail()[0]==0,"401000 callback follows real DIB creation and precedes DDB tail");boolean fenced=false;try{window.requireSnapshotBoundary();}catch(IOException required){fenced=true;}check(fenced,"front publication callback is a physical owner snapshot boundary");});trace.add("front");long[] handles=window.bootstrapHandles();check(handles[1]!=0&&handles[2]!=0&&handles[3]!=0&&handles[4]!=0&&handles[3]!=handles[4]&&handles[5]==handles[4],"separate406ED0 creates distinct DIB and selected compatible bitmap");check(window.dibRgb().bgr24().length==1440000,"actual DIB bytes imported only after staged front creation");check(Arrays.equals(published[0].copyBytes(),window.dibRgb().bgr24()),"401000 import preserves actual DIB bytes across subsequent black DDB fill");check(Arrays.equals(window.compatibleRgb().bgr24(),new byte[1440000]),"406ED0 tail fills the selected DDB black");check(window.windowInfo()[1]==0&&trace.indexOf("paint-before-front")<trace.indexOf("front"),"audit remains hidden and real paint precedes front allocation");window.requireSnapshotBoundary();
        }
        try(Pc1WindowsWindow window=Pc1WindowsWindow.deferred(dll,new Pc1WindowsWindow.NativeDispatcher(){public Pc1WindowsWindow.Dispatch dispatch(long h,int m,long w,long l){return Pc1WindowsWindow.Dispatch.unhandled();}public int quitFlag(){return 0;}})){
            long hwnd=window.createWindow(0,ascii("UnregisteredCircusClass"+System.nanoTime()),ascii("never-created"),0,0,0,800,600,window.moduleInstance());int error=window.lastNativeError();check(hwnd==0&&error!=0,"actual CreateWindow failure returns0 without eager exception");long menu=window.systemMenu(hwnd,false);check(menu==0&&window.appendMenu(menu,0x800,0,new byte[0])==0&&window.appendMenu(menu,0,0x384,ascii("failure"))==0,"native failure path can still call GetSystemMenu0/AppendMenu0 in order");
        }
        System.out.println("trace="+trace);System.out.println("Pc1WindowsStartupAudit PASS "+checks+" actual hidden staged Windows checks; font/audio/DD/registry/display/IME and full startup remain separate required consumers");
    }
}
