package org.circus.core;

import java.io.IOException;
import java.util.Arrays;

/** Connects the reviewed input/history/modal entries to the existing scene.
 * The script and slot leaves must execute their complete native chains;
 * they also own admission of source identity after ordered script reads. */
public final class Pc1UiServices implements Pc1Input.Services,Pc1UiRaster.InputServices,Pc1Modal.Services,Pc1History.Services {
    public interface Platform {
        void setCapture()throws IOException;
        void releaseCapture()throws IOException;
        int setTimer(int id,int milliseconds)throws IOException;
        void killTimer(int token)throws IOException;
        Pc1UiRaster.Point cursorClient()throws IOException;
    }
    public interface ScriptAndSlots {
        int enterSystem(int rawName,int mode)throws IOException;
        void loadScript(int rawName,int mode)throws IOException;
        void jumpLabel(int rawName)throws IOException;
        void writeSlot(int slot)throws IOException;
    }
    private final Pc1Runtime runtime;
    private final Pc1GraphicsPipeline graphics;
    private final Pc1Audio audio;
    private final Pc1TextServices.Settings settings;
    private final Platform platform;
    private final ScriptAndSlots scripts;
    private final Pc1TextRedraw text;
    private final Pc1History history;
    private final Pc1Modal modal;
    private final Pc1Capture capture;
    private final Pc1Input input;
    public Pc1UiServices(Pc1Runtime runtime,Pc1GraphicsPipeline graphics,Pc1Audio audio,Pc1TextServices.Settings settings,Platform platform,ScriptAndSlots scripts)throws IOException{
        if(runtime==null||graphics==null||graphics.runtime()!=runtime||audio==null||settings==null||platform==null||scripts==null)throw new IOException("PC1 UI services require one runtime/graphics graph and all physical/script/slot leaves");
        audio.requireBus(runtime.nativeMemory());
        this.runtime=runtime;this.graphics=graphics;this.audio=audio;this.settings=settings;this.platform=platform;this.scripts=scripts;
        text=new Pc1TextRedraw(runtime,graphics);
        history=new Pc1History(runtime.nativeMemory(),this);
        modal=new Pc1Modal(runtime.nativeMemory(),graphics.render().compositor(),this);
        capture=new Pc1Capture(runtime.nativeMemory(),graphics.render().compositor());
        input=new Pc1Input(runtime.nativeMemory(),graphics.render().uiRaster(),graphics.render().compositor(),this);
        graphics.render().uiRaster().bindInput(this);
        runtime.bindNativeBoundary(this::requireBoundary);
    }
    public Pc1Input input(){return input;}
    public Pc1History history(){return history;}
    public Pc1Modal modal(){return modal;}
    public Pc1Capture capture(){return capture;}
    public void requireBoundary()throws IOException{input.requireBoundary();history.requireBoundary();modal.requireBoundary();capture.requireBoundary();}
    public void redraw(int mask,int l,int t,int r,int b)throws IOException{graphics.render().generalRedraw(mask,l,t,r,b);}
    public void storyControls()throws IOException{graphics.drawStoryControls();}
    public void restoreExtraControls()throws IOException{graphics.restoreExtraControls();}
    public void redrawText(int selected)throws IOException{text.text(selected);}
    public void dictionaryControls()throws IOException{graphics.activateTextRegions();}
    public void activateTextRegions()throws IOException{graphics.activateTextRegions();}
    public void stopVoice()throws IOException{audio.stopVoices();}
    public void historyUp()throws IOException{history.previous();}
    public void historyDown()throws IOException{history.next();}
    public void historySelect(int row)throws IOException{history.playSelected(row);}
    public void playVoice(byte[] knownLocal)throws IOException{
        if(knownLocal==null||knownLocal.length==0||knownLocal[knownLocal.length-1]!=0)throw new IOException("PC1 history voice requires its copied native NUL");
        audio.voice(Arrays.copyOf(knownLocal,knownLocal.length-1),settings.current());
    }
    public int enterSystem(int rawName,int mode)throws IOException{return scripts.enterSystem(rawName,mode);}
    public void loadScript(int rawName,int mode)throws IOException{scripts.loadScript(rawName,mode);}
    public void externalScript(int rawName)throws IOException{scripts.jumpLabel(rawName);}
    public void writeSlot(int slot)throws IOException{scripts.writeSlot(slot);}
    public int variable(int bank,int index)throws IOException{return runtime.nativeVariable(bank,index);}
    public void writeVariable(int bank,int index,int value)throws IOException{runtime.nativeVariable(bank,index,value);}
    public void setCapture()throws IOException{platform.setCapture();}
    public void releaseCapture()throws IOException{platform.releaseCapture();}
    public int setTimer(int id,int milliseconds)throws IOException{return platform.setTimer(id,milliseconds);}
    public void killTimer(int token)throws IOException{platform.killTimer(token);}
    public Pc1UiRaster.Point cursorClient()throws IOException{return platform.cursorClient();}
    public void buttons40AA50(int mode)throws IOException{graphics.render().buttons(mode);}
    public void inline418930(int x,int y,int pitch,int raw,int style)throws IOException{graphics.render().drawInlineAt(x,y,pitch,raw,style);}
    public void glyphs4093F0(int x,int y,Pc1GlyphCache.Source live,int palette,int size)throws IOException{graphics.render().drawRawGlyphsSource(x,y,live,palette,size);}
    public void hover419BD0(int x,int y)throws IOException{input.hover(x,y);}
}
