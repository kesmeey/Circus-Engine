package org.circus.core;
import java.io.*;
import java.nio.file.*;
import java.util.concurrent.atomic.AtomicReference;

/** Actual mouse capture leaves on one hidden, self-authored main HWND. */
public final class Pc1WindowsCaptureAudit {
    private static int checks;
    private static void check(boolean yes,String why){checks++;if(!yes)throw new AssertionError(why);}
    public static void main(String[] args)throws Exception{
        if(args.length!=1)throw new IOException("Expected window DLL");int[] changed={0};
        Pc1WindowsWindow.NativeDispatcher dispatch=new Pc1WindowsWindow.NativeDispatcher(){
            public Pc1WindowsWindow.Dispatch dispatch(long hwnd,int message,long wp,long lp){if(message==0x215)changed[0]++;return Pc1WindowsWindow.Dispatch.unhandled();}
            public int quitFlag(){return 0;}
        };
        Pc1WindowsWindow window=new Pc1WindowsWindow(Paths.get(args[0]),dispatch);
        try{
            check(window.captureWindow()==0,"new test thread initially owns no mouse capture");long hwnd=window.hwnd(),previous=window.setCapture();
            check(previous==0&&window.captureWindow()==hwnd,"SetCapture uses this exact actual main HWND");
            check(window.setCapture()==hwnd&&window.captureWindow()==hwnd,"repeat SetCapture returns previous opaque HWND without narrowing");
            int released=window.releaseCapture();check(released!=0&&window.captureWindow()==0,"ReleaseCapture returns actual BOOL and releases OS capture");
            System.out.println("actualCaptureChanged="+changed[0]);check(changed[0]==2,"repeat SetCapture and ReleaseCapture synchronously notify the same dispatcher");
            AtomicReference<Throwable> wrong=new AtomicReference<>();Thread thread=new Thread(()->{try{window.setCapture();}catch(Throwable failure){wrong.set(failure);}});thread.start();thread.join();check(wrong.get() instanceof IOException,"capture leaves retain creating-thread authority");
            check(window.windowInfo()[1]==0,"capture audit remains hidden");System.out.println("capturePacket="+previous+","+hwnd+","+released+","+changed[0]);
        }finally{window.close();}
        boolean closed=false;try{window.releaseCapture();}catch(IOException expected){closed=true;}check(closed,"capture leaves reject retired native owner");System.out.println("Pc1WindowsCaptureAudit PASS "+checks+" actual capture checks");
    }
}
