package org.circus.core;
import java.io.*;

/**405800 adapter to the staged physical HWND. Engine/font/audio/DD consumers
 *must be supplied; none are inferred from a hidden-window constructor. */
public final class Pc1WindowsStartupServices implements Pc1StartupWindow.Services {
    public interface Consumers {
        void initializeFontContext(long hwnd)throws IOException;
        void initializeAudio()throws IOException;
        void initializeAccelerated(long hwnd)throws IOException;
        /**401000 at its native point before the DDB black-fill tail. */
        void frontCreated(Pc1HeapLease actualDib)throws IOException;
        default void frontCreatedRaw(int rawBits,Pc1HeapLease actualDib)throws IOException{if(actualDib==null)throw new IOException("PC1 raw401000 publication requires canonical pool consumer");frontCreated(actualDib);}
        void loadScript(int rawName,int mode)throws IOException;
    }
    private final PcHost host;private final Pc1Runtime runtime;private final Pc1WindowsWindow window;private final Consumers consumers;private int depth;
    public Pc1WindowsStartupServices(PcHost host,Pc1WindowsWindow deferred,Consumers consumers)throws IOException{
        if(host==null||host.pc1==null||deferred==null||consumers==null)throw new IOException("PC1 Windows startup requires canonical host/staged window/all consumers");this.host=host;runtime=host.pc1;window=deferred;this.consumers=consumers;runtime.bindNativeBoundary(this::requireBoundary);
    }
    private interface Work{void run()throws IOException;}
    private void callback(Work work)throws IOException{depth++;try{work.run();}finally{depth--;}}
    public void requireBoundary()throws IOException{if(depth!=0)throw new IOException("PC1 startup snapshot during required native consumer");window.requireSnapshotBoundary();}
    private byte[] cstring(int address)throws IOException{ByteArrayOutputStream out=new ByteArrayOutputStream();for(int n=0;n<1024*1024;n++){int b=runtime.nativeMemory().readAddress(address+n);if(b==0)return out.toByteArray();out.write(b);}throw new IOException("PC1 startup raw C string exceeds known host bound");}
    public int metric(int index)throws IOException{return window.metric(index);}
    public void adjustWindowRect(Pc1StartupWindow.Rect rect,int style,boolean menu)throws IOException{int[] raw=window.adjustWindowRect(rect.left,rect.top,rect.right,rect.bottom,style,menu);rect.left=raw[1];rect.top=raw[2];rect.right=raw[3];rect.bottom=raw[4];}
    public long instance()throws IOException{return window.currentModuleInstance();}
    public long createWindow(int ex,int rawClass,int rawTitle,int style,int x,int y,int width,int height,long instance)throws IOException{return window.createWindow(ex,cstring(rawClass),cstring(rawTitle),style,x,y,width,height,instance);}
    public long systemMenu(long hwnd,boolean revert)throws IOException{return window.systemMenu(hwnd,revert);}
    public void appendMenu(long menu,int flags,int id,int rawText)throws IOException{window.appendMenu(menu,flags,id,cstring(rawText));}
    public void showWindow(long hwnd,int command)throws IOException{window.showWindow(hwnd,command);}
    public void updateWindow(long hwnd)throws IOException{window.updateWindow(hwnd);}
    public void mainWindow(long hwnd)throws IOException{window.mainWindow(hwnd);}
    public long mainWindow()throws IOException{return window.currentMainWindow();}
    public void enableIme(long hwnd,int enable)throws IOException{window.enableIme(hwnd,enable);}
    public void initializeFontContext(long hwnd)throws IOException{callback(()->consumers.initializeFontContext(hwnd));}
    public void initializeAudio()throws IOException{callback(consumers::initializeAudio);}
    public void initializeAccelerated(long hwnd)throws IOException{callback(()->consumers.initializeAccelerated(hwnd));}
    public void initializeFront(long hwnd)throws IOException{callback(()->window.initializeFrontSharedAt(hwnd,new Pc1WindowsWindow.FrontAllocated(){public void allocated(Pc1HeapLease dib)throws IOException{consumers.frontCreated(dib);}public void allocatedRaw(int rawBits,Pc1HeapLease dib)throws IOException{consumers.frontCreatedRaw(rawBits,dib);}}));}
    public void readSettings()throws IOException{callback(()->{Pc1SettingsIo settings=runtime.nativeSettingsIo();if(settings==null)throw new IOException("PC1 startup requires canonical402890 settings IO");settings.read();});}
    public void loadScript(int rawName,int mode)throws IOException{callback(()->consumers.loadScript(rawName,mode));}
    public int tickCount()throws IOException{return window.tickCount();}
    public void seedRandom(int tick)throws IOException{runtime.graphicsMemory().writeInt(0x43e100,tick);}
    public int setTimer(long hwnd,int id,int interval)throws IOException{return window.setTimerAt(hwnd,id,interval);}
}
