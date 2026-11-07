package org.circus.core;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

/**Actual DIB lease sharing and GDI readback. No copied pixel state is used as
 *a live image0 owner, and no original game process or visible window runs. */
public final class Pc1WindowsDibLeaseAudit {
    private static int checks;
    private static void check(boolean yes,String why){checks++;if(!yes)throw new AssertionError(why);}
    private static final class Host implements Pc1WindowsWindow.PaintHost {
        Pc1HeapLease lease;int resets,shared;
        public int global(int at)throws IOException{if(at==0x94c694)return 1;if(at==0x94c68c||at==0x94c61c)return 0;throw new IOException("unexpected paint global");}
        public boolean sharesDib(long address){shared++;return lease!=null&&lease.live()&&lease.observedAddress()==address;}
        public byte[] frontBgr24()throws IOException{throw new IOException("shared DIB must not request an upload snapshot");}
        public int[] dirtyForPresent(){return new int[]{0,0,800,600};}public void resetDirty(){resets++;}public void accelerated(long dib,int[] rect)throws IOException{throw new IOException("unexpected accelerated output");}
    }
    public static void main(String[] args)throws Exception{
        if(args.length!=1)throw new IOException("Expected staged shared-DIB DLL");Pc1WindowsWindow[] current={null};Host host=new Host();Pc1HeapLease lease;
        Pc1WindowsWindow.NativeDispatcher dispatcher=new Pc1WindowsWindow.NativeDispatcher(){public Pc1WindowsWindow.Dispatch dispatch(long hwnd,int msg,long wp,long lp)throws IOException{return msg==0xf&&current[0]!=null?current[0].paintCurrent(host):Pc1WindowsWindow.Dispatch.unhandled();}public int quitFlag(){return 0;}};
        try(Pc1WindowsWindow window=new Pc1WindowsWindow(Paths.get(args[0]),dispatcher)){
            current[0]=window;lease=host.lease=window.borrowDib();check(lease==window.borrowDib()&&window.isDibLease(lease),"one live DIB allocation and one borrowed view");check(lease.length()==1440000,"DIB has actual byte extent without invented HeapAlloc guard");
            window.writeDibByte(100,0x91);check(lease.readU8(100)==0x91,"independent native write is immediately visible through direct Java lease");lease.writeU8(101,0xa7);check(window.readDibByte(101)==0xa7,"Java lease write immediately reaches independently read native DIB byte");
            byte[] expected=new byte[1440000];for(int n=0;n<expected.length;n++){expected[n]=(byte)(n*29+(n>>>8));lease.writeU8(n,expected[n]);}check(Arrays.equals(expected,window.dibRgb().bgr24()),"all physical DIB bytes equal direct lease writes without upload or copy-back");
            window.postMessage(0xf,0,0);window.pumpMessages();long[] report=window.lastPaintReport();check(report[2]==4&&report[17]==0&&host.shared==1&&host.resets==1,"actual WM_PAINT detects same DIB and skips snapshot upload");check(Arrays.equals(expected,window.compatibleRgb().bgr24()),"same DIB reaches all cached DDB pixels through actual native BitBlt");
            int offset=(599-3)*2400+2*3;for(int n=0;n<3;n++){expected[offset+n]=(byte)(31+n*47);lease.writeU8(offset+n,expected[offset+n]);}window.beginNativePaint();long[] direct=window.paintRectangleShared(2,3,3,4,lease);window.endNativePaint();byte[] observed=window.compatibleRgb().bgr24();int difference=-1;for(int n=0;n<observed.length;n++)if(observed[n]!=expected[n]){difference=n;break;}check(direct[0]!=0&&(direct[2]==0||direct[2]==1),"actual shared direct paint records both native BOOL results "+Arrays.toString(direct));System.out.println("sharedDirectPacket="+Arrays.toString(direct));check(difference==-1,"effect direct paint reads same live DIB without upload; first difference="+difference+(difference<0?"":" observed="+(observed[difference]&255)+" expected="+(expected[difference]&255)));
            boolean guarded=false;try{lease.nativeAddress32();}catch(IOException required){guarded=required.getMessage().contains("32-bit");}check(guarded,"physical64 DIB address is never truncated into native pointer DWORD");
            AtomicReference<Throwable> wrong=new AtomicReference<>();Thread thread=new Thread(()->{try{lease.readU8(0);}catch(Throwable failure){wrong.set(failure);}});thread.start();thread.join();check(wrong.get() instanceof IOException,"borrowed bytes retain creating-thread physical owner checks");check(window.windowInfo()[1]==0,"DIB audit remains hidden");
            System.out.println("sharedDibSha256="+Binary.sha256(expected));
        }
        boolean retired=false;try{lease.readU8(0);}catch(IOException required){retired=true;}check(!lease.live()&&retired,"window close retires the borrowed view before freeing native DIB");System.out.println("Pc1WindowsDibLeaseAudit PASS "+checks+" actual shared-DIB lifetime/pixel checks");
    }
}
