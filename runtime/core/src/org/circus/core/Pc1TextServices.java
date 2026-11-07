package org.circus.core;

import java.io.IOException;

/**417D20 adapter over canonical layout/pixels/audio and actual platform
 *leaves.418220 and41A0B0 remain explicitly required complete consumers. */
public final class Pc1TextServices implements Pc1TextPresentation.Host {
    public interface Settings {Pc1NativeSettings current()throws IOException;}
    public interface Platform {
        int asyncKeyState(int virtualKey)throws IOException;
        int setTimer(int id,int interval)throws IOException;
        void killTimer(int token)throws IOException;
        int tickCount()throws IOException;
        void updateWindow()throws IOException;
        int pumpMessages()throws IOException;
        void postMessage(int message,int wParam,int lParam)throws IOException;
        void ensureEffectTimer()throws IOException;
    }
    public interface Redraw {void text(int selection)throws IOException;}
    public interface Transition {
        Pc1TextPresentation.Transition begin(int target,int argument)throws IOException;
        Pc1TextPresentation.Transition resume()throws IOException;
    }
    private final Pc1Runtime runtime;
    private final Pc1GraphicsPipeline graphics;
    private final Pc1Audio audio;
    private final Settings settings;
    private final Platform platform;
    private final Redraw redraw;
    private final Transition transition;
    public Pc1TextServices(Pc1Runtime runtime,Pc1GraphicsPipeline graphics,Pc1Audio audio,Settings settings,Platform platform)throws IOException{
        this(runtime,graphics,audio,settings,platform,new Pc1Transition(runtime,graphics,platform));
    }
    public Pc1TextServices(Pc1Runtime runtime,Pc1GraphicsPipeline graphics,Pc1Audio audio,Settings settings,Platform platform,Transition transition)throws IOException{
        this(runtime,graphics,audio,settings,platform,new Pc1TextRedraw(runtime,graphics)::text,transition);
    }
    public Pc1TextServices(Pc1Runtime runtime,Pc1GraphicsPipeline graphics,Pc1Audio audio,Settings settings,Platform platform,Redraw redraw,Transition transition)throws IOException{
        if(runtime==null||graphics==null||graphics.runtime()!=runtime||audio==null||settings==null||platform==null||redraw==null||transition==null)throw new IOException("PC1 text services require canonical owners and every native consumer");
        this.runtime=runtime;this.graphics=graphics;this.audio=audio;this.settings=settings;this.platform=platform;this.redraw=redraw;this.transition=transition;
    }
    public Pc1TextPresentation.Owners owners()throws IOException{return new Pc1TextPresentation.Owners(runtime.nativeFrame(),runtime.textBuffers());}
    /** Keep the complete native paint entry fenced across its globals,
     *layout, audio and platform callbacks, including gaps between draws. */
    public int paint(Pc1TextPresentation painter)throws IOException{if(painter==null)throw new IOException("PC1 text painter missing");final int[] result={0};graphics.render().nativeCall(()->result[0]=painter.paint(owners(),this));return result[0];}
    public int global(Pc1TextPresentation.Global field)throws IOException{return runtime.graphicsMemory().readInt(field.nativeAddress);}
    public void global(Pc1TextPresentation.Global field,int value)throws IOException{runtime.graphicsMemory().writeInt(field.nativeAddress,value);}
    public Pc1TextLayout layout()throws IOException{return runtime.nativeTextLayout();}
    public void prepareWindow(int l,int t,int r,int b,int mode)throws IOException{graphics.render().restoreBody(l,t,r,b,mode);}
    public void fullClip()throws IOException{graphics.render().compositor().fullClip();}
    public void drawControls()throws IOException{graphics.drawStoryControls();}
    public void prepareSpeakerGeometry()throws IOException{graphics.render().restoreName(0,0,800,600,1);}
    public void redrawText(int selection)throws IOException{redraw.text(selection);}
    public void drawGlyphs(int first)throws IOException{graphics.render().drawGlyphJournal(first);}
    public void invalidate()throws IOException{graphics.render().compositor().present();}
    /**4118B0 queries the OS first even when rapid is already set. The API's
     *low bit is observable, so reordering this OR changes platform behavior. */
    public boolean skipRequested()throws IOException{return (platform.asyncKeyState(0x11)&0x8000)!=0||global(Pc1TextPresentation.Global.RAPID)!=0;}
    public void stopVoices()throws IOException{audio.stopVoices();}
    public void restoreControls(int draw)throws IOException{graphics.restoreTextControls(draw);}
    public void activateTextRegions()throws IOException{graphics.activateTextRegions();}
    public void animateWaitCursor(int mode)throws IOException{graphics.render().waitCursor(mode);}
    public boolean voiceDone(int argument)throws IOException{return audio.voiceIdle(settings.current())!=0;}
    public void releaseGraphic(int slot,int preserveOverlay)throws IOException{graphics.render().release(slot,preserveOverlay);}
    public void bakePending()throws IOException{graphics.render().bakePending();}
    public Pc1TextPresentation.Transition beginTransition(int target,int argument)throws IOException{return transition.begin(target,argument);}
    public Pc1TextPresentation.Transition resumeTransition()throws IOException{return transition.resume();}
    public int setTimer(int id,int interval)throws IOException{return platform.setTimer(id,interval);}
    public void killTimer(int handle)throws IOException{platform.killTimer(handle);}
    public int tickCount()throws IOException{return platform.tickCount();}
    public void updateWindow()throws IOException{platform.updateWindow();}
    public int pumpMessages()throws IOException{return platform.pumpMessages();}
    public void postMessage(int message,int wParam,int lParam)throws IOException{platform.postMessage(message,wParam,lParam);}
    public void ensureEffectTimer()throws IOException{platform.ensureEffectTimer();}
    public void advanceCrtRandom()throws IOException{runtime.nativeCrtRandom();}
}
