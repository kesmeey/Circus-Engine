package org.circus.core;

import java.io.IOException;

/** Native script/system consumers connected to one existing scene/audio/
 * transition graph. This never invokes MesVm's parsed-source load shortcut.
 * File/path/error, complete reconstruction and slot services stay explicit. */
public final class Pc1ScriptFlow implements Pc1UiServices.ScriptAndSlots {
    public interface Files extends Pc1ScriptLoader.NativeIo {
        int prepareScenario41AEA0(int rawName)throws IOException;
        void error411050(int rawMessage)throws IOException;
    }
    public interface Persistent {
        void writeSlot41AAD0(int slot)throws IOException;
        void rebuildGraphics41A570(int rawFrame)throws IOException;
    }
    private final Pc1Runtime runtime;
    private final Pc1GraphicsPipeline graphics;
    private final Pc1Audio audio;
    private final Pc1TextServices.Settings settings;
    private final Pc1ScriptLoader loader;
    private final Pc1SystemNative system;
    private final Pc1Capture capture;
    private final Pc1TextRedraw text;
    private final Persistent persistent;
    public Pc1ScriptFlow(Pc1Runtime runtime,Pc1GraphicsPipeline graphics,Pc1Audio audio,Pc1TextServices.Settings settings,Pc1Transition transition,Files files,Persistent persistent)throws IOException{
        if(runtime==null||graphics==null||graphics.runtime()!=runtime||audio==null||settings==null||transition==null||files==null||persistent==null)throw new IOException("PC1 script flow requires canonical graph and every native file/reconstruction/slot leaf");
        audio.requireBus(runtime.nativeMemory());transition.requireGraph(graphics);this.runtime=runtime;this.graphics=graphics;this.audio=audio;this.settings=settings;this.persistent=persistent;
        capture=new Pc1Capture(runtime.nativeMemory(),graphics.render().compositor());text=new Pc1TextRedraw(runtime,graphics);
        loader=new Pc1ScriptLoader(runtime.nativeMemory(),runtime.scriptMemory(),files,new Pc1ScriptLoader.Services(){
            public int prepareScenario41AEA0(int raw)throws IOException{return files.prepareScenario41AEA0(raw);}
            public void error411050(int raw)throws IOException{files.error411050(raw);}
            public void resetEffects40F560(int kind,int strength)throws IOException{graphics.commandEffect(kind,strength);}
            public void releaseGraphics411080(int slot,int mode)throws IOException{graphics.render().release(slot,mode);}
        });
        system=new Pc1SystemNative(runtime.nativeMemory(),runtime.systemMemory(),transition,new Pc1SystemNative.Services(){
            public void resetEffects40F560(int kind,int strength)throws IOException{graphics.commandEffect(kind,strength);}
            public int capture401EE0(int raw)throws IOException{return capture.frontNamed(raw);}
            public void releaseGraphics411080(int slot,int mode)throws IOException{graphics.render().release(slot,mode);}
            public void loadScript411670(int raw,int mode)throws IOException{loader.load(raw,mode);}
            public void freeImage401130(int handle)throws IOException{graphics.render().compositor().freeChecked(handle);}
            public void rebuildGraphics41A570(int raw)throws IOException{persistent.rebuildGraphics41A570(raw);}
            public void target409480(int handle)throws IOException{graphics.render().compositor().target(handle);}
            public void body415D50(int l,int t,int r,int b,int mode)throws IOException{graphics.render().restoreBody(l,t,r,b,mode);}
            public void redrawText418220(int selected)throws IOException{text.text(selected);}
            public void activateText412320()throws IOException{graphics.activateTextRegions();}
            public void sound41C850(int sound,int loop)throws IOException{audio.sound(sound,loop,settings.current());}
        });
        runtime.bindNativeBoundary(this::requireBoundary);
    }
    public Pc1ScriptLoader loader(){return loader;}
    public Pc1SystemNative system(){return system;}
    public void requireBoundary()throws IOException{runtime.scriptMemory().requireBoundary();system.requireBoundary();capture.requireBoundary();}
    public int enterSystem(int rawName,int mode)throws IOException{return system.enter(rawName,mode);}
    public void loadScript(int rawName,int mode)throws IOException{loader.load(rawName,mode);}
    public void externalScript(int rawName)throws IOException{loader.jumpLabel(rawName);}
    public void jumpLabel(int rawName)throws IOException{loader.jumpLabel(rawName);}
    public void writeSlot(int slot)throws IOException{persistent.writeSlot41AAD0(slot);}
}
