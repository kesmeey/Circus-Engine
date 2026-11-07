package org.circus.core;

import java.io.*;

/** Current CHS417D20, the4B/4C/07/09 producer boundaries and the native
 *406996..406A90 wait-message clock footer. This never invents a Unicode
 *reveal clock. Frame, text buffers, mutable layout, pixels, globals, CRT RNG
 *and platform event queue are independent canonical owners. */
public final class Pc1TextPresentation {
    public static final int WM_CONTINUE=0x400,WM_CLOSE=0x10,AUTO_COUNT_OFFSET=0x1f1c;
    public enum Global {
        PREF0(0x454cd8),TEXT_MODE(0x454cdc),RAPID(0xbb842c),LEFT_HELD(0x94c680),ENTER_HELD(0x94c684),
        LEFT_CONSUMED(0xbb840c),ENTER_CONSUMED(0xbb8410),AUTO_PAUSE(0xbb8414),VISIBILITY(0xbb844c),
        CURSOR(0x9d801c),LENGTH(0xbb83f8),FIRST_DRAW(0xbb8544),BODY_X(0x43d438),BODY_Y(0x43d43c),BODY_PITCH(0x43d440),BODY_STYLE(0x43d444),NAME_STYLE(0x43d460),
        SELECTED_HISTORY(0x43d410),HOVERED_BUTTON(0x94fa28),LAST_YIELD(0x43d414),NEW_PAGE(0xbb84e4),SURFACE_PROGRESS(0x9cca8c),
        COMPOSITION_PENDING(0x9b9f18),DRAW_TARGET(0x43d418),UI_INPUT_MODE(0x94c6a4),BUTTON_COUNT(0x9689ec),STORY_BUTTON_COUNT(0x9b8e1c),
        DELAY(0x430284),TIMER1(0x94c670),LOOP_FLAG(0x94c698),EVENT_BUSY(0x94c6a8),TICK_START(0x94bdb4),APP_ACTIVE(0x94c66c),PAUSE_634(0x94c634),PAUSE_638(0x94c638);
        public final int nativeAddress;Global(int address){nativeAddress=address;}
    }
    public static final class Owners {
        public final Pc1NativeFrame frame;public final Pc1TextBuffers buffers;
        public Owners(Pc1NativeFrame frame,Pc1TextBuffers buffers)throws IOException{if(frame==null||buffers==null)throw new IOException("PC1 text presentation requires canonical frame/text buffers");this.frame=frame;this.buffers=buffers;}
    }
    public enum Transition { RUNNING,COMPLETE,INTERRUPTED }
    public interface Host {
        int global(Global field)throws IOException;
        void global(Global field,int value)throws IOException;
        /** Current mutable canonical layout. A history callback may replace
         *it; never return a fresh detached builder without retaining it. Its
         *debug cursor is not the native9D801C authority (Global.CURSOR is). */
        Pc1TextLayout layout()throws IOException;
        void prepareWindow(int left,int top,int right,int bottom,int mode)throws IOException; //415D50
        void fullClip()throws IOException; //4076B0
        void drawControls()throws IOException; //416B80
        void prepareSpeakerGeometry()throws IOException; //416220(0,0,800,600,1)
        void redrawText(int selection)throws IOException; //418220, must update layout owner
        void drawGlyphs(int first)throws IOException; //4170C0, actual GDI/cache/pixels
        void invalidate()throws IOException; //407000, no synchronous paint
        /**4118B0: actual Ctrl high bit OR native rapid latch; not a cached
         *scenario-read filter. Native calls this only at the shown branches. */
        boolean skipRequested()throws IOException;
        void stopVoices()throws IOException; //41CBE0
        void restoreControls(int draw)throws IOException; //412260
        void activateTextRegions()throws IOException; //412320
        void animateWaitCursor(int mode)throws IOException; //4186F0(0)
        boolean voiceDone(int argument)throws IOException; //41CBF0(0), native channel2
        void releaseGraphic(int slot,int preserveOverlay)throws IOException; //411080
        void bakePending()throws IOException; //411D30
        /** Complete external41A0B0(target,1) owner. Begin submits its first
         *frame; resume preserves native40ms/pump/capture semantics. Returning
         *COMPLETE cannot mean only a delay or a scene-model revision. */
        Transition beginTransition(int target,int argument)throws IOException;
        Transition resumeTransition()throws IOException;
        /** Native HWND services. SetTimer interval is passed as rawDWORD;
         *platform clamp/coalescing is not another character clock. */
        int setTimer(int id,int interval)throws IOException;
        void killTimer(int handle)throws IOException;
        int tickCount()throws IOException; // actual current IAT GetTickCount
        void updateWindow()throws IOException;
        int pumpMessages()throws IOException; //complete405710(hwnd), returns94C688
        void postMessage(int message,int wParam,int lParam)throws IOException;
        void ensureEffectTimer()throws IOException; //407080
        void advanceCrtRandom()throws IOException; //420862, return value discarded
    }
    private boolean painting;
    public void requireSnapshotBoundary()throws IOException{if(painting)throw new IOException("PC1 text presentation snapshot during native callback");}
    private static Pc1TextLayout layout(Host h)throws IOException{Pc1TextLayout l=h.layout();if(l==null)throw new IOException("PC1 text presentation missing canonical layout owner");return l;}
    private static Pc1TextLayout.Snapshot state(Host h)throws IOException{return layout(h).freeze();}
    private static int count(Host h)throws IOException{return state(h).glyphCount;}
    private static int bodyByte(Owners o,int offset)throws IOException{return o.buffers.unsignedByte(Pc1TextBuffers.LIVE_BODY,offset);}
    private static boolean patch(Owners o)throws IOException{return bodyByte(o,0)=='@'&&bodyByte(o,1)=='K';}
    private static int strlen(Owners o)throws IOException{return o.buffers.read(Pc1TextBuffers.LIVE_BODY).length;}
    private static void position(Host h,int x,int y)throws IOException{Pc1TextLayout.Snapshot s=state(h);layout(h).position(x,y,s.fontSize);}
    private static void timer(Host h)throws IOException{if(h.global(Global.TIMER1)==0){int delay=h.global(Global.DELAY);h.global(Global.TIMER1,h.setTimer(1,delay==0?1:delay));}}
    /** One COMPLETE native417D20 invocation. Slow text yields after a layout
     *step; fast/held input can process the whole known body in this call.
     *There is no message pump inside this function. Even its final glyph
     *batch returns1; only the following invocation returns0. */
    public int paint(Owners o,Host h)throws IOException{
        if(o==null||h==null)throw new IOException("PC1 text presentation requires native owners/services");if(painting)throw new IOException("PC1 text presentation reentry before native yield");painting=true;
        try{
            int mode=h.global(Global.TEXT_MODE);h.global(Global.LEFT_CONSUMED,0);h.global(Global.ENTER_CONSUMED,0);
            if(mode==0||mode==2)h.global(Global.DELAY,5);else if(mode==1||mode==3)h.global(Global.DELAY,0);
            if(o.frame.dword(Pc1NativeSlot.Dword.ARGUMENT0)==0){
                int logCount=o.frame.dword(Pc1NativeSlot.Dword.LOG_COUNT);
                boolean fresh=o.frame.dword(Pc1NativeSlot.Dword.PARAGRAPH_MODE)==0||logCount<1||(Pc1TextLog.backed(o.frame).rawEntry(logCount-1)&0x8000)!=0;
                if(fresh){
                    layout(h).clearGlyphs();h.global(Global.VISIBILITY,1);h.prepareWindow(0,0,800,600,1);h.drawControls();
                    if(o.frame.dword(Pc1NativeSlot.Dword.PARAGRAPH_MODE)==0){int style=h.global(Global.NAME_STYLE);h.prepareSpeakerGeometry();Pc1TextLayout.Result named=layout(h).speaker(new Pc1TextLayout.Source(Pc1TextBuffers.LIVE_SPEAKER,o.buffers.knownBytes(Pc1TextBuffers.LIVE_SPEAKER)),style,o.frame.dword(Pc1NativeSlot.Dword.ARGUMENT1),false);if(o.buffers.unsignedByte(Pc1TextBuffers.LIVE_SPEAKER,0)!=0)h.drawGlyphs(named.firstGlyph);}
                    layout(h).position(h.global(Global.BODY_X),h.global(Global.BODY_Y),28);
                }else{
                    if(h.global(Global.VISIBILITY)==0){
                        layout(h).clearGlyphs();int c4=o.frame.dword(Pc1NativeSlot.Dword.ARGUMENT1)+1;o.frame.dword(Pc1NativeSlot.Dword.ARGUMENT1,c4);
                        if(c4<4&&h.global(Global.RAPID)==0&&h.global(Global.ENTER_HELD)==0&&h.global(Global.LEFT_HELD)==0&&h.global(Global.DELAY)!=0&&(h.global(Global.PREF0)!=0||h.skipRequested())){timer(h);return 1;}
                    }
                    h.redrawText(-2);Pc1TextLayout.Snapshot s=state(h);if(s.x!=h.global(Global.BODY_X)&&!patch(o))position(h,h.global(Global.BODY_X),s.y+h.global(Global.BODY_PITCH));
                }
                h.global(Global.CURSOR,0);
                if(patch(o)){Pc1TextLayout.Snapshot s=state(h);h.prepareWindow(s.x,s.y-2,s.x+3,s.y+30,0);h.fullClip();h.global(Global.CURSOR,h.global(Global.CURSOR)+2);}
                h.global(Global.LENGTH,strlen(o));h.global(Global.FIRST_DRAW,count(h));
            }
            if(h.global(Global.CURSOR)>=h.global(Global.LENGTH)){
                if(h.global(Global.LEFT_HELD)!=0)h.global(Global.LEFT_CONSUMED,1);if(h.global(Global.ENTER_HELD)!=0)h.global(Global.ENTER_CONSUMED,1);if(h.global(Global.DELAY)==0)h.global(Global.DELAY,1);return 0;
            }
            timer(h);h.fullClip();
            for(;;){
                int cursor=h.global(Global.CURSOR);
                if(bodyByte(o,cursor)=='\n'){
                    position(h,h.global(Global.BODY_X),state(h).y+h.global(Global.BODY_PITCH));h.global(Global.FIRST_DRAW,count(h));h.global(Global.CURSOR,h.global(Global.CURSOR)+1);
                    if(h.global(Global.RAPID)==0&&h.global(Global.ENTER_HELD)==0&&h.global(Global.LEFT_HELD)==0&&h.global(Global.DELAY)!=0&&(h.global(Global.PREF0)!=0||h.skipRequested()))return yieldBatch(o,h);
                }else{
                    Pc1TextLayout.Snapshot s=state(h);h.prepareWindow(h.global(Global.BODY_X),s.y-11,s.x+31,s.y+30,0);h.fullClip();
                    Pc1TextLayout.Step step=layout(h).step(new Pc1TextLayout.Source(Pc1TextBuffers.LIVE_BODY,o.buffers.knownBytes(Pc1TextBuffers.LIVE_BODY)),h.global(Global.CURSOR),h.global(Global.BODY_STYLE),true,o.frame.dword(Pc1NativeSlot.Dword.ARGUMENT1));
                    h.global(Global.CURSOR,step.nextCursor);h.drawGlyphs(h.global(Global.FIRST_DRAW));
                }
                if(h.global(Global.CURSOR)>=h.global(Global.LENGTH)||h.global(Global.ENTER_HELD)==0&&h.global(Global.LEFT_HELD)==0&&h.global(Global.PREF0)==0&&h.global(Global.DELAY)!=0&&!h.skipRequested())return yieldBatch(o,h);
            }
        }finally{painting=false;}
    }
    private static int yieldBatch(Owners o,Host h)throws IOException{o.frame.dword(Pc1NativeSlot.Dword.ARGUMENT0,o.frame.dword(Pc1NativeSlot.Dword.ARGUMENT0)+1);o.frame.dword(Pc1NativeSlot.Dword.HEAD_Y,state(h).y);h.invalidate();if(h.global(Global.DELAY)==0)h.global(Global.DELAY,1);return 1;}
    /**Logical byte-array convenience; raw fetch uses Pc1TextProducer and its live Source.*/
    public static void produceSpeaker(Owners o,byte[] decoded,Pc1TextExpander.Context context)throws IOException{if(o==null)throw new IOException("PC1 speaker owner missing");o.buffers.writeExpanded(Pc1TextBuffers.LIVE_SPEAKER,Pc1TextExpander.expandSpeaker(decoded,context));}
    public static final class Body {
        private static final int FORMAT=0x50315442; //P1TB
        public enum Phase { TRANSITION,COMPLETE,INTERRUPTED,FAILED }
        private Phase phase;private boolean busy;
        private Body(Phase phase){this.phase=phase;}
        /**Logical byte-array/cooperative convenience. Raw handlers use the
         *live Pc1TextProducer call and exact direct-return flow.*/
        public static Body begin(Owners o,Host h,int previousOpcode,byte[] decoded,Pc1TextExpander.Context context)throws IOException{
            if(o==null||h==null)throw new IOException("PC1 body producer requires native owners/services");Body b=new Body(Phase.TRANSITION);b.busy=true;
            try{
                if(previousOpcode!=0x4b)o.buffers.clearLiveSpeakerHead();o.buffers.writeExpanded(Pc1TextBuffers.LIVE_BODY,Pc1TextExpander.expand(decoded,context));int pending=h.global(Global.COMPOSITION_PENDING);h.global(Global.SELECTED_HISTORY,-1);layout(h).dictionaryCount(0);layout(h).resetRubyPhase();
                if(pending!=0){h.releaseGraphic(1,0);h.releaseGraphic(2,0);h.bakePending();b.finish(o,h,h.beginTransition(h.global(Global.DRAW_TARGET),1));}
                else{o.frame.dword(Pc1NativeSlot.Dword.WAIT_KIND,0x4c);b.phase=Phase.COMPLETE;}return b;
            }catch(IOException|RuntimeException failure){b.phase=Phase.FAILED;throw failure;}finally{b.busy=false;}
        }
        private void finish(Owners o,Host h,Transition status)throws IOException{if(status==null)throw new IOException("PC1 body transition result missing");if(status==Transition.COMPLETE){h.global(Global.VISIBILITY,0);o.frame.dword(Pc1NativeSlot.Dword.WAIT_KIND,0x4c);phase=Phase.COMPLETE;}else if(status==Transition.INTERRUPTED)phase=Phase.INTERRUPTED;}
        public Phase phase(){return phase;}public boolean active(){return phase==Phase.TRANSITION;}public int nativeResult(){if(phase==Phase.COMPLETE)return 0;if(phase==Phase.INTERRUPTED)return 1;throw new IllegalStateException("PC1 body producer has no native result");}
        public Phase step(Owners o,Host h)throws IOException{boundary();if(!active())return phase;busy=true;try{finish(o,h,h.resumeTransition());return phase;}catch(IOException|RuntimeException failure){phase=Phase.FAILED;throw failure;}finally{busy=false;}}
        private void boundary()throws IOException{if(busy)throw new IOException("PC1 body producer snapshot/reentry during callback");if(phase==Phase.FAILED)throw new IOException("PC1 failed body producer cannot resume");}
        public void save(DataOutput out)throws IOException{boundary();out.writeInt(FORMAT);out.writeByte(phase==Phase.TRANSITION?1:phase==Phase.COMPLETE?2:3);}
        public static Body load(DataInput in)throws IOException{if(in.readInt()!=FORMAT)throw new IOException("PC1 body producer format");int p=in.readUnsignedByte();if(p<1||p>3)throw new IOException("PC1 body producer saved phase");return new Body(p==1?Phase.TRANSITION:p==2?Phase.COMPLETE:Phase.INTERRUPTED);}
    }
    /** Native07 case only. Caller already performed the physical fetch;
     *argument is its localu8, LAST_YIELD is43D414, not the local previous opcode. */
    public static void produce07(Owners o,Host h,int argument)throws IOException{
        h.global(Global.NEW_PAGE,0);int paragraph=o.frame.dword(Pc1NativeSlot.Dword.PARAGRAPH_MODE);if(argument==1&&paragraph!=0)h.global(Global.NEW_PAGE,1);Pc1TextLog.backed(o.frame).onWait07(argument,paragraph);h.global(Global.SURFACE_PROGRESS,0);
        if(h.global(Global.LAST_YIELD)==0x4c){
            h.global(Global.SELECTED_HISTORY,-1);h.global(Global.HOVERED_BUTTON,-1);
            if(!h.skipRequested()){o.frame.dword(Pc1NativeSlot.Dword.WAIT_KIND,7);h.restoreControls(1);h.activateTextRegions();int mode=h.global(Global.TEXT_MODE),count=mode==2?strlen(o):mode==3?strlen(o)*2/3:0;o.frame.writeI32(AUTO_COUNT_OFFSET,count);}else h.stopVoices();
        }
    }
    /**Convenience entry samples the device before its first store. The raw
     *dispatcher supplies its actual fetched ECX to the overload/producer.*/
    public static void produce09(Owners o,Host h)throws IOException{produce09(o,h,h.global(Global.BUTTON_COUNT));}
    public static void produce09(Owners o,Host h,int cachedDevice)throws IOException{h.global(Global.UI_INPUT_MODE,0);o.frame.dword(Pc1NativeSlot.Dword.WAIT_KIND,9);h.global(Global.STORY_BUTTON_COUNT,cachedDevice);h.redrawText(-1);}
    /** Native418B10 message07 consumer; auto count is invocation based, not
     *a millisecond timeout. A decrement to0 still returns1 for this call. */
    public static int wait07(Owners o,Host h)throws IOException{
        h.animateWaitCursor(0);if(h.skipRequested()){h.stopVoices();return 0;}int remaining=o.frame.readI32(AUTO_COUNT_OFFSET);if(remaining>0){o.frame.writeI32(AUTO_COUNT_OFFSET,remaining-1);return 1;}int mode=h.global(Global.TEXT_MODE);if((mode==2||mode==3)&&h.global(Global.SELECTED_HISTORY)<0&&h.global(Global.AUTO_PAUSE)==0&&h.voiceDone(0))return 0;return 1;
    }
    public interface WaitConsumer {int consume()throws IOException;}
    /** Continuation for the WM_USER400 wait footer. Snapshot this LOCAL
     *phase separately from all globals, input queue and framebuffer owners.
     *Native94BDB4 holds the start tick; this class never mirrors/repairs it. */
    public static final class TimerFrame {
        private static final int FORMAT=0x50315457; //P1TW
        public enum Phase { AFTER_WAIT,PUMP,COMPLETE,SKIPPED_LOOP,REARMED_PAUSE,OTHER_TIMER,FAILED }
        private Phase phase;private int result;private boolean consumed,quit,busy;
        private TimerFrame(Phase phase){this.phase=phase;}
        public static TimerFrame beginWait(Host h,WaitConsumer consumer)throws IOException{
            if(h==null||consumer==null)throw new IOException("PC1 native wait message requires actual services and consumer");TimerFrame frame=new TimerFrame(Phase.SKIPPED_LOOP);frame.busy=true;
            try{if(h.global(Global.LOOP_FLAG)!=0)return frame;h.global(Global.TICK_START,h.tickCount());h.global(Global.EVENT_BUSY,h.global(Global.EVENT_BUSY)|1);frame.result=consumer.consume();frame.consumed=true;frame.phase=Phase.AFTER_WAIT;return frame;}catch(IOException|RuntimeException failure){frame.phase=Phase.FAILED;throw failure;}finally{frame.busy=false;}
        }
        /** Only the timer matching current94C670 belongs here. Other IDs
         *must route to their real audio/effect timer consumers, not be eaten. */
        public static TimerFrame onTimer1(Host h,int timerId,WaitConsumer consumer)throws IOException{
            if(h==null||consumer==null)throw new IOException("PC1 timer1 requires actual services and wait consumer");if(timerId!=h.global(Global.TIMER1))return new TimerFrame(Phase.OTHER_TIMER);
            h.advanceCrtRandom();if(h.global(Global.LOOP_FLAG)==2)h.global(Global.LOOP_FLAG,0);h.killTimer(h.global(Global.TIMER1));
            if(h.global(Global.APP_ACTIVE)!=0&&h.global(Global.PAUSE_634)==0&&h.global(Global.PAUSE_638)==0){h.global(Global.TIMER1,0);h.ensureEffectTimer();return beginWait(h,consumer);}
            h.global(Global.TIMER1,h.setTimer(1,h.global(Global.DELAY)));return new TimerFrame(Phase.REARMED_PAUSE);
        }
        public Phase phase(){return phase;}public boolean active(){return phase==Phase.AFTER_WAIT||phase==Phase.PUMP;}public boolean waitWasCalled(){return consumed;}public int waitResult(){return result;}public boolean quitObserved(){return quit;}
        public Phase step(Host h)throws IOException{
            boundary();if(!active())return phase;if(h==null)throw new IOException("PC1 native wait footer requires actual services");busy=true;
            try{
                if(phase==Phase.PUMP){quit=h.pumpMessages()!=0;if(!quit)h.postMessage(WM_CONTINUE,1,0);clearBusy(h);phase=Phase.COMPLETE;return phase;}
                if(result==0){h.updateWindow();clearBusy(h);h.postMessage(WM_CONTINUE,0,0);clearBusy(h);phase=Phase.COMPLETE;}
                else if(result==1){
                    if(h.global(Global.DELAY)==0)h.global(Global.DELAY,1);int elapsed=h.tickCount()-h.global(Global.TICK_START),delay=h.global(Global.DELAY);
                    if(Integer.compareUnsigned(elapsed,delay-1)>=0){h.updateWindow();clearBusy(h);int handle=h.global(Global.TIMER1);if(handle!=0){h.killTimer(handle);h.global(Global.TIMER1,0);}phase=Phase.PUMP;}
                    else{if(h.global(Global.TIMER1)==0)h.global(Global.TIMER1,h.setTimer(1,delay-elapsed));h.updateWindow();clearBusy(h);clearBusy(h);phase=Phase.COMPLETE;}
                }else{if(result==3)h.postMessage(WM_CLOSE,0,0);clearBusy(h);phase=Phase.COMPLETE;}
                return phase;
            }catch(IOException|RuntimeException failure){phase=Phase.FAILED;throw failure;}finally{busy=false;}
        }
        private static void clearBusy(Host h)throws IOException{h.global(Global.EVENT_BUSY,h.global(Global.EVENT_BUSY)&~1);}
        private void boundary()throws IOException{if(busy)throw new IOException("PC1 wait footer snapshot/reentry during callback");if(phase==Phase.FAILED)throw new IOException("PC1 failed wait footer cannot resume");}
        public TimerFrame copy()throws IOException{boundary();TimerFrame c=new TimerFrame(phase);c.result=result;c.consumed=consumed;c.quit=quit;return c;}
        public void save(DataOutput out)throws IOException{boundary();out.writeInt(FORMAT);out.writeByte(phase.ordinal());out.writeInt(result);out.writeBoolean(consumed);out.writeBoolean(quit);}
        public static TimerFrame load(DataInput in)throws IOException{
            if(in.readInt()!=FORMAT)throw new IOException("PC1 text timer format");int p=in.readUnsignedByte(),result=in.readInt(),called=in.readUnsignedByte(),quit=in.readUnsignedByte();if(p>=Phase.FAILED.ordinal()||called>1||quit>1)throw new IOException("PC1 text timer saved phase");TimerFrame c=new TimerFrame(Phase.values()[p]);c.result=result;c.consumed=called!=0;c.quit=quit!=0;
            if((c.phase==Phase.AFTER_WAIT||c.phase==Phase.PUMP||c.phase==Phase.COMPLETE)!=c.consumed||!c.consumed&&(result!=0||c.quit)||c.phase==Phase.PUMP&&(result!=1||c.quit)||c.quit&&(c.phase!=Phase.COMPLETE||result!=1))throw new IOException("PC1 text timer phase/result mismatch");return c;
        }
    }
}
