package org.circus.core;

import java.io.*;

/** Current CHS405E50 orchestration. Canonical DWORDs are borrowed, physical
 *HWND/GDI/audio pointers stay in explicit services. A default decision means
 *actual DefWindowProcA, never success for a missing known engine consumer. */
public final class Pc1WindowProcedure {
    public interface Services {
        /** WM_CREATE stores the physical mainHWND AFTER canonical quit0. */
        void mainWindowCreated(long hwnd)throws IOException;
        /** Whole405EB7/406CB0 mapped leaf, including its outerpaint gate. */
        boolean paint(long hwnd)throws IOException; //true handled, false actualDefWindowProc
        boolean graphicsContextPresent()throws IOException; //external94C654
        void destroyFontAndGraphics()throws IOException; //4042A0 +405F6B..FAD
        void releaseAccelerated()throws IOException; //402E30
        void changeDisplayMode(int mode)throws IOException; //407160
        void postQuitMessage(int code)throws IOException;
        void setActiveWindow(long hwnd)throws IOException;
        boolean isIconic(long hwnd)throws IOException;
        /**405AF0 dialog remains a required complete consumer. Parent0 for
         *WM_COMMAND9C41; mainWindowParent=true forSYSCOMMAND384. */
        void settingsDialog(boolean mainWindowParent)throws IOException;
        void changeScreen(int mode)throws IOException; //407370
        /** Actual GetCursorPos/ScreenToClient on givenHWND; message lParam
         *coordinates are NOT substituted. Failed unknownbacking must throw. */
        Pc1UiRaster.Point cursorClient(long hwnd)throws IOException;
        int input(int x,int y,int key)throws IOException; //419280
        void hover(int x,int y)throws IOException; //419BD0
        void leftDown(int x,int y)throws IOException; //419C90
        void timer3(int x,int y,int mode)throws IOException; //40B110(0,0,2)
        int timer5()throws IOException; //complete418DF0, not onlyeffectprefix
        void armAudioTimer()throws IOException; //4070E0, same674/6ACaudioowner
        void audioTimer2(int token)throws IOException; //4066EA..6731, includingraw0
        void separateMediaNotice()throws IOException; //40E000, notMM_MCINOTIFY
        int scriptDispatch()throws IOException; //4125B0 native returnvalue
        int waitDispatch()throws IOException; //418A70 native returnvalue
        int setTimer(long hwnd,int id,int interval)throws IOException;
        void killTimer(long hwnd,int token)throws IOException;
        void updateWindow(long hwnd)throws IOException;
        void postMessage(long hwnd,int message,int wParam,int lParam)throws IOException;
        int pumpMessages(long hwnd)throws IOException; //complete405710, returns94C688
    }
    private static final int QUIT=0x94c688,LOOP=0x94c698,BUSY=0x94c6a8,TIMER1=0x94c670,
        TIMER2=0x94c674,TIMER3=0x94c678,TIMER5=0x94c67c,PAINT_GATE=0x94c68c;
    private final Pc1NativeMemory memory;
    private final Pc1TextPresentation.Host text;
    private final Services io;
    private int depth;
    public Pc1WindowProcedure(Pc1NativeMemory memory,Pc1TextPresentation.Host text,Services io)throws IOException{if(memory==null||text==null||io==null)throw new IOException("PC1 window procedure requires canonical bytebus/textHost and every native service");this.memory=memory;this.text=text;this.io=io;}
    public void requireBoundary()throws IOException{if(depth!=0)throw new IOException("PC1 window procedure snapshot during active native callback");}
    private int g(int address)throws IOException{return memory.readAddress(address)|memory.readAddress(address+1)<<8|memory.readAddress(address+2)<<16|memory.readAddress(address+3)<<24;}
    private void g(int address,int value)throws IOException{for(int n=0;n<4;n++)memory.writeAddress(address+n,value>>>(n*8));}
    private void kill1(long hwnd)throws IOException{int token=g(TIMER1);if(token!=0){io.killTimer(hwnd,token);g(TIMER1,0);}}
    private void postContinue(long hwnd)throws IOException{io.postMessage(hwnd,0x400,0,0);}
    private void bit(int value)throws IOException{g(BUSY,g(BUSY)|value);}
    private void clear(int value)throws IOException{g(BUSY,g(BUSY)&~value);}
    /** One message may return a saved LOCAL footer before its next nested
     *pump. The adapter drains activeframes synchronously for actualWndProc;
     *the bundle may serialize a known localphase between steps. */
    public Frame begin(long hwnd,int message,int wParam,int lParam)throws IOException{
        Frame frame=new Frame(this,hwnd);frame.busy=true;depth++;
        try{dispatch(frame,message,wParam,lParam);return frame;}catch(IOException|RuntimeException failure){frame.phase=Phase.FAILED;throw failure;}finally{depth--;frame.busy=false;}
    }
    public Frame dispatch(long hwnd,int message,int wParam,int lParam)throws IOException{Frame frame=begin(hwnd,message,wParam,lParam);while(frame.active())frame.step();return frame;}
    private void waitFooter(Frame frame)throws IOException{frame.footer=Pc1TextPresentation.TimerFrame.beginWait(text,io::waitDispatch);frame.phase=frame.footer.active()?Phase.WAIT_FOOTER:Phase.COMPLETE;if(!frame.active())frame.footer=null;}
    private void dispatch(Frame f,int message,int wp,int lp)throws IOException{
        long hwnd=f.hwnd;
        switch(message){
        case 1:g(QUIT,0);io.mainWindowCreated(hwnd);return;
        case 2:
            if(io.graphicsContextPresent())io.destroyFontAndGraphics();
            if(g(0x94c61c)!=0){if(g(0x94c620)!=0)io.releaseAccelerated();else io.changeDisplayMode(0);}
            if(g(LOOP)!=2)io.postQuitMessage(0);return;
        case 6:
            if(g(PAINT_GATE)!=2){f.phase=Phase.DEFAULT;return;}io.setActiveWindow(hwnd);g(PAINT_GATE,0);f.result=1;return;
        case 0xf:if(!io.paint(hwnd))f.phase=Phase.DEFAULT;return;
        case 0x10:
            int token=g(TIMER1);g(QUIT,1);if(token!=0){io.killTimer(hwnd,token);g(TIMER1,0);}token=g(TIMER5);if(token!=0){io.killTimer(hwnd,token);g(TIMER5,0);}if(g(PAINT_GATE)!=1)f.phase=Phase.DEFAULT;return;
        case 0x1c:
            if(g(QUIT)!=0)return;g(0x94c66c,wp);if(wp!=0&&g(PAINT_GATE)==0&&g(TIMER2)==0){g(0x94c6ac,5);io.armAudioTimer();}return;
        case 0x100:keyDown(f,wp);return;
        case 0x101:keyUp(f,wp);return;
        case 0x105:
            if(wp!=13){f.phase=Phase.DEFAULT;return;}if(g(LOOP)==0&&g(0x94c6a0)!=0&&g(PAINT_GATE)==0)io.changeScreen(g(0x94c61c)==0?1:0);return;
        case 0x111:
            if(wp==0x9c42){io.postMessage(hwnd,0x10,0,0);return;}if(wp==0x9c41){if(!io.isIconic(hwnd))io.settingsDialog(false);return;}f.phase=Phase.DEFAULT;return;
        case 0x112:
            if(wp==0x384){if(!io.isIconic(hwnd))io.settingsDialog(true);return;}f.phase=Phase.DEFAULT;return;
        case 0x113:timer(f,wp);return;
        case 0x200:
            if(g(LOOP)!=0){f.phase=Phase.DEFAULT;return;}Pc1UiRaster.Point p=point(hwnd);io.hover(p.x,p.y);return;
        case 0x201:leftDown(f);return;
        case 0x202:leftUp(f);return;
        case 0x205:
            int loop=g(LOOP);g(0x94c690,1);if(loop!=0){f.phase=Phase.DEFAULT;return;}bit(8);if(io.input(-1,-1,2)!=0){kill1(hwnd);io.updateWindow(hwnd);clear(8);postContinue(hwnd);}else clear(8);return;
        case 0x20a:
            int delta=(short)(wp>>>16);if(delta==0)return;bit(0x20);if(io.input(-1,-1,delta<0?0x22:0x21)!=0){kill1(hwnd);io.updateWindow(hwnd);postContinue(hwnd);}else io.updateWindow(hwnd);clear(0x20);return;
        case 0x215:g(0x94c634,0);return;
        case 0x231:g(0x94c634,1);return;
        case 0x232:return; //native EXITSIZEMOVE doesnotclear634
        case 0x400:
            if(wp==0){if(g(LOOP)!=0)return;bit(1);if(io.scriptDispatch()!=0){clear(1);return;}io.updateWindow(hwnd);clear(1);}waitFooter(f);return;
        case 0x40d:io.separateMediaNotice();return;
        default:f.phase=Phase.DEFAULT;
        }
    }
    private Pc1UiRaster.Point point(long hwnd)throws IOException{Pc1UiRaster.Point p=io.cursorClient(hwnd);if(p==null)throw new IOException("PC1 native WndProc cursor backing unknown");return p;}
    private void keyDown(Frame f,int key)throws IOException{
        if(key==0x1b)g(0x94c690,1);if(g(LOOP)!=0){f.phase=Phase.DEFAULT;return;}if(key==13){g(0x94c684,1);return;}
        if(key!=0x21&&key!=0x22&&(key<0x25||key>0x28))return;
        g(0x94c6b0,2);if(g(0x94c69c)!=0)return;int flags=g(BUSY);g(0x94c69c,1);g(BUSY,flags|0x10);
        if(io.input(-1,-1,key)!=0){kill1(f.hwnd);io.updateWindow(f.hwnd);clear(0x10);g(0x94c69c,0);postContinue(f.hwnd);}
        else{io.updateWindow(f.hwnd);flags=g(BUSY);g(0x94c69c,0);g(BUSY,flags&~0x10);}
    }
    private void keyUp(Frame f,int key)throws IOException{
        if(key==0x1b)g(0x94c690,1);else if(key==13)g(0x94c684,0);if(g(LOOP)!=0){f.phase=Phase.DEFAULT;return;}if(key!=13&&key!=0x1b&&key!=0x20)return;
        bit(0x10);if(io.input(-1,-1,key)!=0){kill1(f.hwnd);io.updateWindow(f.hwnd);postContinue(f.hwnd);}clear(0x10);
    }
    private void leftDown(Frame f)throws IOException{
        int loop=g(LOOP);g(0x94c680,1);if(loop!=0){f.phase=Phase.DEFAULT;return;}Pc1UiRaster.Point p=point(f.hwnd);
        if(g(0x94c6a4)==1){bit(4);if(io.input(p.x,p.y,1)!=0){kill1(f.hwnd);io.updateWindow(f.hwnd);clear(4);postContinue(f.hwnd);}else clear(4);}
        else{bit(2);io.leftDown(p.x,p.y);clear(2);}
    }
    private void leftUp(Frame f)throws IOException{
        int loop=g(LOOP);g(0x94c680,0);if(loop!=0){f.phase=Phase.DEFAULT;return;}Pc1UiRaster.Point p=point(f.hwnd);if(g(0x94c6a4)==1){g(0x94c6a4,0);return;}
        bit(4);if(io.input(p.x,p.y,1)!=0){kill1(f.hwnd);io.updateWindow(f.hwnd);clear(4);postContinue(f.hwnd);}else{io.updateWindow(f.hwnd);clear(4);}
    }
    private void timer(Frame f,int token)throws IOException{
        f.footer=Pc1TextPresentation.TimerFrame.onTimer1(text,token,io::waitDispatch);
        if(f.footer.phase()!=Pc1TextPresentation.TimerFrame.Phase.OTHER_TIMER){f.phase=f.footer.active()?Phase.WAIT_FOOTER:Phase.COMPLETE;if(!f.active())f.footer=null;return;}f.footer=null;
        int handle=g(TIMER2);if(token==handle){io.audioTimer2(token);return;}
        handle=g(TIMER3);if(token==handle){io.killTimer(f.hwnd,handle);g(TIMER3,0);io.timer3(0,0,2);return;}
        handle=g(TIMER5);if(token!=handle)return;
        io.killTimer(f.hwnd,handle);g(TIMER5,io.setTimer(f.hwnd,5,40));
        if(g(BUSY)!=0||g(LOOP)!=0||g(0x94c634)!=0||g(0x94c638)!=0||g(0x94c6bc)!=0)return;
        if(io.timer5()!=0)postContinue(f.hwnd);io.updateWindow(f.hwnd);g(0x94c6bc,1);f.phase=Phase.EFFECT_PUMP;
    }
    public enum Phase { COMPLETE,DEFAULT,WAIT_FOOTER,EFFECT_PUMP,FAILED }
    public static final class Frame {
        private static final int FORMAT=0x50315750,VERSION=1;
        private final Pc1WindowProcedure owner;private final long hwnd;
        private Phase phase=Phase.COMPLETE;private Pc1TextPresentation.TimerFrame footer;private long result;private boolean busy;
        private Frame(Pc1WindowProcedure owner,long hwnd){this.owner=owner;this.hwnd=hwnd;}
        public Phase phase(){return phase;}public boolean active(){return phase==Phase.WAIT_FOOTER||phase==Phase.EFFECT_PUMP;}public boolean defaulted(){return phase==Phase.DEFAULT;}
        public long result()throws IOException{if(phase!=Phase.COMPLETE)throw new IOException("PC1 WndProc result before completion orDefWindowProc");return result;}
        private void boundary()throws IOException{if(busy)throw new IOException("PC1 same WndProc frame snapshot/reentry duringcallback");owner.requireBoundary();if(phase==Phase.FAILED)throw new IOException("PC1 failed WndProc frame cannotresume");}
        public Phase step()throws IOException{
            if(busy||phase==Phase.FAILED)throw new IOException("PC1 same WndProc frame reentry orfailedresume");if(!active())return phase;busy=true;owner.depth++;
            try{if(phase==Phase.WAIT_FOOTER){footer.step(owner.text);if(!footer.active()){footer=null;phase=Phase.COMPLETE;}}
                else{if(owner.io.pumpMessages(hwnd)==0)owner.g(0x94c6bc,0);phase=Phase.COMPLETE;}return phase;
            }catch(IOException|RuntimeException failure){phase=Phase.FAILED;throw failure;}finally{owner.depth--;busy=false;}
        }
        /** ActualHWND is not a portable native pointer. Restore supplies an
         *explicit rebound mainHWND after adopting its independentOS owners. */
        public void save(DataOutput out)throws IOException{boundary();if(phase==Phase.DEFAULT)throw new IOException("PC1 DefWindowProc decision belongs to current OS callback, not a suspended nativeframe");out.writeInt(FORMAT);out.writeInt(VERSION);out.writeByte(phase.ordinal());out.writeLong(result);if(phase==Phase.WAIT_FOOTER)footer.save(out);}
        public static Frame load(DataInput in,Pc1WindowProcedure owner,long reboundHwnd)throws IOException{
            if(owner==null||in.readInt()!=FORMAT||in.readInt()!=VERSION)throw new IOException("PC1 WndProc localframe format");int p=in.readUnsignedByte();long result=in.readLong();if(p>=Phase.FAILED.ordinal()||p==Phase.DEFAULT.ordinal()||result<0||result>1)throw new IOException("PC1 WndProc localframe state");Frame f=new Frame(owner,reboundHwnd);f.phase=Phase.values()[p];f.result=result;
            if(f.phase==Phase.WAIT_FOOTER){f.footer=Pc1TextPresentation.TimerFrame.load(in);if(!f.footer.active())throw new IOException("PC1 WndProc waitfooter phase mismatch");}if(f.active()&&result!=0)throw new IOException("PC1 active WndProc localframe result");return f;
        }
    }
}
