package org.circus.core;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

/** Actual all-thread GetMessage/Translate/Dispatch, no original process. */
public final class Pc1WindowsMessageAudit {
    private static int checks;
    private static void check(boolean yes,String why){checks++;if(!yes)throw new AssertionError(why);}
    public static void main(String[] args)throws Exception{
        if(args.length!=1)throw new IOException("Expected message-capable window DLL");List<long[]> delivered=new ArrayList<>();long wp=0x1000000123L,lp=0x2000000456L,result=0x3000000789L;
        Pc1WindowsWindow.NativeDispatcher dispatcher=new Pc1WindowsWindow.NativeDispatcher(){public Pc1WindowsWindow.Dispatch dispatch(long hwnd,int message,long w,long l){if(message==0x8001){delivered.add(new long[]{hwnd,w,l});return Pc1WindowsWindow.Dispatch.handled(result);}return Pc1WindowsWindow.Dispatch.unhandled();}public int quitFlag(){return 0;}};
        try(Pc1WindowsWindow first=new Pc1WindowsWindow(Paths.get(args[0]),dispatcher);Pc1WindowsWindow second=new Pc1WindowsWindow(Paths.get(args[0]),dispatcher)){
            Pc1Startup.Message message=new Pc1Startup.Message();check(second.postMessage(0x8001,wp,lp)!=0,"post actual wide-parameter message to second HWND");int code;
            for(int n=0;;n++){if(n>=32)throw new AssertionError("queued fixture message not retrieved");code=first.getMessage(message);check(code>0,"nonquit GetMessage result remains actual positive BOOL");if(message.get(4)==0x8001)break;first.translateMessage(message);first.dispatchMessage(message);}
            check(delivered.isEmpty(),"retrieval does not substitute DispatchMessage");long[] packet=message.physical();check(packet[0]==second.hwnd()&&packet[2]==wp&&packet[3]==lp,"NULL-filter GetMessage reads other same-thread HWND and preserves both physical64 parameters");check(first.translateMessage(message)==0,"actual non-key TranslateMessage BOOL");check(first.dispatchMessage(message)==result&&delivered.size()==1&&Arrays.equals(delivered.get(0),new long[]{second.hwnd(),wp,lp}),"actual DispatchMessage retains target, parameters, synchronous callback and64-bit result");
            check(first.postThreadMessage(0x8002,17,-19)!=0,"post actual thread message");for(int n=0;;n++){if(n>=32)throw new AssertionError("thread message not retrieved");code=first.getMessage(message);check(code>0,"thread message returns positive GetMessage BOOL");if(message.get(4)==0x8002)break;first.translateMessage(message);first.dispatchMessage(message);}
            check(message.window()==0&&message.get(8)==17&&message.get(12)==-19,"NULL HWND thread message remains distinct from main-window queue filter");check(first.dispatchMessage(message)==0&&delivered.size()==1,"thread message has no invented WndProc recipient");
            first.postQuitMessage(0x12345678);for(int n=0;;n++){if(n>=32)throw new AssertionError("WM_QUIT not retrieved");code=first.getMessage(message);if(code==0)break;check(code>0,"drain actual preceding messages");first.translateMessage(message);first.dispatchMessage(message);}
            check(message.get(4)==0x12&&message.get(8)==0x12345678&&message.window()==0,"actual WM_QUIT supplies native exit DWORD and zero GetMessage result");first.postQuitMessage(-1);check(first.getMessage(message)==0&&message.exitCode()==-1,"negative PostQuitMessage int survives actual physical WPARAM width");System.out.println("negativeQuitPacket="+Arrays.toString(message.physical()));AtomicReference<Throwable> wrong=new AtomicReference<>();Thread thread=new Thread(()->{try{first.getMessage(new Pc1Startup.Message());}catch(Throwable failure){wrong.set(failure);}});thread.start();thread.join();check(wrong.get() instanceof IOException,"message queue leaves retain creating-thread ownership");check(first.windowInfo()[1]==0&&second.windowInfo()[1]==0,"both audit windows remain hidden");System.out.println("wideMessage="+Arrays.toString(packet));
        }
        System.out.println("Pc1WindowsMessageAudit PASS "+checks+" actual all-thread message checks");
    }
}
