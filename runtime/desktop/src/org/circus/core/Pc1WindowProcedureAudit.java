package org.circus.core;
import java.io.*;
import java.nio.file.*;
import java.util.*;

/** Actual hidden Windows dispatch, timer, queued400 and nestedpump. The
 *input/script/effect/audio consumers are explicit fixture journals, not a
 *claim that the original game or all remaining engine leaves are running. */
public final class Pc1WindowProcedureAudit {
    private static int checks;
    private static void check(boolean ok,String why){checks++;if(!ok)throw new AssertionError(why);}
    private static final class Actual extends Pc1WindowProcedureTests.Fake {
        Pc1WindowsWindow window;boolean snapshotBlocked;
        Actual()throws IOException{super();}
        public int setTimer(long h,int id,int interval)throws IOException{event("set:"+id+","+interval);return window.setTimer(id,interval);}
        public void killTimer(long h,int token)throws IOException{event("kill:"+token);window.killTimer(token);}
        public void updateWindow(long h)throws IOException{event("update");window.updateWindow();}
        public void postMessage(long h,int message,int wp,int lp)throws IOException{event("post:"+message+","+wp+","+lp);if(window.postMessage(message,wp,lp)==0)throw new IOException("Audit actual PostMessage failed");}
        public int pumpMessages(long h)throws IOException{event("pump");return window.pumpMessages(h);}
        public int tickCount()throws IOException{event("tick");return window.tickCount();}
        public void ensureEffectTimer()throws IOException{event("ensure5");if(g(0x94c67c)==0)g(0x94c67c,window.setTimer(5,40));}
        public int input(int x,int y,int key)throws IOException{int result=super.input(x,y,key);try{window.requireSnapshotBoundary();}catch(IOException active){snapshotBlocked=true;}return result;}
        public boolean paint(long h)throws IOException{if(g(0x94c68c)!=0)return false;throw new IOException("Actual audit has no canonical paintgraph");}
        public Pc1UiRaster.Point cursorClient(long h)throws IOException{int[] point=window.cursorClient();return new Pc1UiRaster.Point(point[4],point[5]);}
    }
    public static void main(String[] args)throws Exception{
        if(args.length!=1)throw new IOException("Require explicit immutablewindowDLL");Actual f=new Actual();final List<Integer> dispatched=new ArrayList<>();Pc1WindowsWindow.NativeDispatcher dispatcher=new Pc1WindowsWindow.NativeDispatcher(){public Pc1WindowsWindow.Dispatch dispatch(long hwnd,int message,long wp,long lp)throws IOException{dispatched.add(message);Pc1WindowProcedure.Frame frame=f.procedure.dispatch(hwnd,message,(int)wp,(int)lp);return frame.defaulted()?Pc1WindowsWindow.Dispatch.unhandled():Pc1WindowsWindow.Dispatch.handled(frame.result());}public int quitFlag()throws IOException{return f.g(0x94c688);}};
        try(Pc1WindowsWindow window=new Pc1WindowsWindow(Paths.get(args[0]),dispatcher)){
            f.window=window;f.g(0x94c68c,1);check(f.hwnd==window.hwnd()&&f.g(0x94c688)==0,"Actual synchronous WM_CREATE stores externalHWND withcanonicalquit0 beforeconstructorreturn");f.trace.clear();f.boundaryState.clear();dispatched.clear();f.inputResult=1;f.scriptResult=1;
            check(window.postMessage(0x101,13,1234)!=0,"Queue actualkeyrelease");check(f.trace.isEmpty(),"PostMessage isqueued, never synchronousVM input");window.pumpMessages();check(f.trace.equals(Arrays.asList("input:-1,-1,13","update","post:1024,0,0","script")),"Each actualDispatch finishes before nextPeek; queued400 sees clearedkeybusy");System.out.println("keyupActualDispatch="+dispatched+";busy="+f.g(0x94c6a8)+";snapshotBlocked="+f.snapshotBlocked);check(Collections.frequency(dispatched,0x101)==1&&Collections.frequency(dispatched,0x400)==1&&dispatched.indexOf(0x101)<dispatched.indexOf(0x400)&&f.g(0x94c6a8)==0&&f.snapshotBlocked,"Rawmessages processed once inOS order andsnapshotrejectsactivecallback");check(f.boundaryState.get(2).contains("@0,0,16,")&&f.boundaryState.get(3).contains("@0,0,1,"),"keyup postswithbit10stillset, nextqueuedscript runswithonlybit1");
            f.trace.clear();f.boundaryState.clear();dispatched.clear();f.timer5Result=1;f.g(0x94c67c,window.setTimer(5,40));window.postMessage(0x113,f.g(0x94c67c),0);window.pumpMessages();window.killTimer(f.g(0x94c67c));f.g(0x94c67c,0);
            check(f.trace.equals(Arrays.asList("kill:5","set:5,40","timer5","post:1024,0,0","update","pump","script")),"Timer5 actualnestedpump dispatchesqueued400 inline beforeoutercallbackreturns");check(f.g(0x94c6bc)==0&&Collections.frequency(dispatched,0x113)==1&&Collections.frequency(dispatched,0x400)==1&&dispatched.indexOf(0x113)<dispatched.indexOf(0x400),"Nested actualWndProc allowsdifferentframe andclearsreentryguardafterdrain");String scriptBoundary=f.boundaryState.get(f.boundaryState.size()-1);String[] state=scriptBoundary.substring(scriptBoundary.indexOf('@')+1).split(",");check(Integer.parseInt(state[21])==1,"Nestedscript observes6BC1 untilnativeouterpumpreturns");
            f.trace.clear();dispatched.clear();f.g(0x94c66c,1);f.waitResult=2;f.g(0x94c670,window.setTimer(1,40));window.postMessage(0x113,f.g(0x94c670),0);window.pumpMessages();if(f.g(0x94c67c)!=0){window.killTimer(f.g(0x94c67c));f.g(0x94c67c,0);}
            check(f.trace.equals(Arrays.asList("random","kill:1","ensure5","tick","wait"))&&f.g(0x94c670)==0,"Actualtimer1 usesrawtoken priority, GetTickCount andcompletewaitfooter");window.requireSnapshotBoundary();check(window.windowInfo()[1]==0,"Auditnever showsitswindow");System.out.println("actualWndProcMessages="+dispatched);System.out.println("Pc1WindowProcedureAudit PASS "+checks+"; actual hidden Windows queued/synchronous/nested dispatch/timers/clock; fixture engine consumers, no originalEXE or completegameplay claim");
        }
    }
}
