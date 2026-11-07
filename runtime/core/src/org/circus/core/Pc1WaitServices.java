package org.circus.core;

import java.io.IOException;

/**418A70 and its wait consumers share the already constructed scene, text,
 *script and audio graph. No second progress counter or timer clock is owned. */
public final class Pc1WaitServices implements Pc1WaitDispatcher.Services,Pc1WaitEffects.Services {
    public interface Native extends Pc1InstallKey.Locale {
        void markScenarioRead41AE20(int rawName)throws IOException;
        void closeAudio41CDA0()throws IOException;
        void initializeAudio41B7F0()throws IOException;
        byte[] resolveMoviePath40E870(int rawName)throws IOException;
        void playMovie40E090(byte[] knownLocalPath)throws IOException;
        int pumpMessages405710(long filterHwnd)throws IOException;
        void specialStart404770()throws IOException;
        int specialStep4051E0()throws IOException;
    }
    private final Pc1Runtime runtime;
    private final Pc1GraphicsPipeline graphics;
    private final Pc1Audio audio;
    private final Pc1TextServices.Settings settings;
    private final Pc1TextServices text;
    private final Pc1TextPresentation painter;
    private final Pc1ScriptFlow scripts;
    private final Native nativeLeaves;
    private final Pc1WaitDispatcher dispatcher;
    private final Pc1WaitEffects effects;
    private final Pc1Timer1 timer;
    private final Pc1Capture capture;
    private final Pc1TextRedraw redraw;
    private final Pc1InstallKey installKey;
    private int depth;
    public Pc1WaitServices(Pc1Runtime runtime,Pc1GraphicsPipeline graphics,Pc1Audio audio,Pc1TextServices.Settings settings,Pc1TextServices text,Pc1TextPresentation painter,Pc1ScriptFlow scripts,Native nativeLeaves)throws IOException{
        if(runtime==null||graphics==null||graphics.runtime()!=runtime||audio==null||settings==null||text==null||painter==null||scripts==null||nativeLeaves==null)throw new IOException("PC1 wait services require the complete canonical graph and native leaves");
        audio.requireBus(runtime.nativeMemory());text.requireGraph(runtime,graphics,audio);scripts.requireGraph(runtime,graphics,audio);
        this.runtime=runtime;this.graphics=graphics;this.audio=audio;this.settings=settings;this.text=text;this.painter=painter;this.scripts=scripts;this.nativeLeaves=nativeLeaves;
        dispatcher=new Pc1WaitDispatcher(runtime.nativeMemory(),this);effects=new Pc1WaitEffects(runtime.nativeMemory(),this);timer=new Pc1Timer1(runtime.nativeMemory(),text::setTimer);capture=new Pc1Capture(runtime.nativeMemory(),pixels());redraw=new Pc1TextRedraw(runtime,graphics);installKey=new Pc1InstallKey(runtime.nativeMemory(),nativeLeaves);
        runtime.bindNativeBoundary(this::requireBoundary);
    }
    private Pc1Compositor pixels(){return graphics.render().compositor();}
    private interface Result {int run()throws IOException;}
    private int call(Result body)throws IOException{depth++;try{final int[] result={0};graphics.render().nativeCall(()->result[0]=body.run());return result[0];}finally{depth--;}}
    public void requireBoundary()throws IOException{if(depth!=0)throw new IOException("PC1 wait services snapshot during native consumer");dispatcher.requireBoundary();effects.requireBoundary();timer.requireBoundary();capture.requireBoundary();installKey.requireBoundary();painter.requireSnapshotBoundary();}
    /** One complete418A70 invocation, preserving its raw return value. */
    public int poll()throws IOException{return call(dispatcher::poll);}
    public void waitCursor(int mode)throws IOException{graphics.render().waitCursor(mode);}
    public boolean skipRequested()throws IOException{return text.skipRequested();}
    public int skip()throws IOException{return skipRequested()?1:0;}
    public void stopVoice()throws IOException{audio.stopVoices();}
    public int voiceIdle(int argument)throws IOException{return audio.voiceIdle(settings.current());}
    public void gridWait40B8A0()throws IOException{graphics.render().uiRaster().animationTick();}
    public int returnTransition4158E0()throws IOException{return call(effects::screen);}
    public int selection4167D0()throws IOException{return call(effects::actorFade);}
    public int speaker416620()throws IOException{return call(effects::backgroundPan);}
    public int move416A30()throws IOException{return call(effects::actorMove);}
    public int specialComplete4047A0()throws IOException{return call(effects::special);}
    public int tickCount()throws IOException{return text.tickCount();}
    public int validateInstallKey41B6C0(int raw,int flags)throws IOException{return installKey.validate(raw,flags);}
    public void markScenarioRead(int raw)throws IOException{nativeLeaves.markScenarioRead41AE20(raw);}
    public void loadScript(int raw,int mode)throws IOException{scripts.loadScript(raw,mode);}
    public void closeAudio()throws IOException{nativeLeaves.closeAudio41CDA0();}
    public void initializeAudio()throws IOException{nativeLeaves.initializeAudio41B7F0();}
    public byte[] resolveMoviePath(int raw)throws IOException{return nativeLeaves.resolveMoviePath40E870(raw);}
    public void playMovie(byte[] knownPath)throws IOException{nativeLeaves.playMovie40E090(knownPath);}
    public int pumpMessages(long filterHwnd)throws IOException{return nativeLeaves.pumpMessages405710(filterHwnd);}
    public int textPaint417D20()throws IOException{return text.paint(painter);}
    public int createLike(int source)throws IOException{return graphics.render().resources().createLike(source,pixels());}
    public int capture(int rawLabel)throws IOException{return capture.frontNamed(rawLabel);}
    public void freeImage(int handle)throws IOException{pixels().freeChecked(handle);}
    public void target(int handle)throws IOException{pixels().target(handle);}
    public void redraw(int mask,int l,int t,int r,int b)throws IOException{graphics.render().generalRedraw(mask,l,t,r,b);}
    public void releaseResource(int slot,int mode)throws IOException{graphics.render().release(slot,mode);}
    public void redrawText(int selected)throws IOException{redraw.text(selected);}
    public void restoreBody(int l,int t,int r,int b,int mode)throws IOException{graphics.render().restoreBody(l,t,r,b,mode);}
    public void rawGlyph28(int x,int y,int source,int palette)throws IOException{graphics.render().rawGlyph28At(x,y,source,palette);}
    public void ensureTimer1()throws IOException{timer.arm();}
    public void fullClip()throws IOException{pixels().fullClip();}
    public void setClip(int l,int t,int r,int b)throws IOException{pixels().setClip(l,t,r,b);}
    public void scale(int handle,int ratio)throws IOException{pixels().scale(handle,ratio);}
    public void feedback(int x,int y,int handle,int ratio)throws IOException{pixels().feedback(x,y,handle,ratio);}
    public void copy(int x,int y,int handle)throws IOException{pixels().copy(x,y,handle);}
    public void fillPalette(int l,int t,int r,int b,int palette)throws IOException{pixels().fill(l,t,r,b,runtime.nativeMemory().readInt32(Pc1DrawBatch.PALETTE+palette*4));}
    public void present()throws IOException{pixels().present();}
    public void specialStart()throws IOException{nativeLeaves.specialStart404770();}
    public int specialStep()throws IOException{return nativeLeaves.specialStep4051E0();}
}
