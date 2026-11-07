package org.circus.core;

import java.io.*;
import java.lang.reflect.Proxy;
import java.util.*;

/**Complete417D20 through the actual core service graph. Platform and failed
 *audio initialization are explicit inputs; no physical output is claimed.*/
public final class Pc1TextServicesTests {
    private static int checks;private static void check(boolean value,String why){checks++;if(!value)throw new AssertionError(why);}
    private static final class Graphics implements Pc1GraphicsPipeline.Services {
        int invalidates;public void invalidate(Pc1Raster.Rect r){invalidates++;}
        private IOException absent(){return new IOException("unexpected external graphics fixture leaf");}
        public Pc1UiRaster.Point cursorClient()throws IOException{throw absent();}public void sound(int id,int mode)throws IOException{throw absent();}public void voice(int p)throws IOException{throw absent();}public void specialListItem(int v,int d,boolean s)throws IOException{throw absent();}public void ensureTimer5()throws IOException{throw absent();}public void beginNativePaint()throws IOException{throw absent();}public void paintRectangle(int l,int t,int r,int b)throws IOException{throw absent();}public void endNativePaint()throws IOException{throw absent();}
    }
    private static final class Clock implements Pc1TextServices.Platform {
        final Pc1GraphicsPipeline graph;int keys,timers,key,interval;boolean clearRapid,snapshotBlocked;
        Clock(Pc1GraphicsPipeline graph){this.graph=graph;}
        public int asyncKeyState(int vk)throws IOException{check(vk==0x11,"4118B0 queries actual Ctrl key code");keys++;if(clearRapid)graph.runtime().graphicsMemory().writeInt(0xbb842c,0);return key;}
        public int setTimer(int id,int delay)throws IOException{check(id==1,"417D20 timer1 identity");timers++;interval=delay;try{graph.render().save(new DataOutputStream(new ByteArrayOutputStream()));}catch(IOException required){snapshotBlocked=true;}return 77;}
        private IOException absent(){return new IOException("unexpected text platform fixture leaf");}
        public void killTimer(int token)throws IOException{throw absent();}public int tickCount()throws IOException{throw absent();}public void updateWindow()throws IOException{throw absent();}public int pumpMessages()throws IOException{throw absent();}public void postMessage(int m,int w,int l)throws IOException{throw absent();}public void ensureEffectTimer()throws IOException{throw absent();}
    }
    public static int run()throws Exception{
        checks=0;Pc1RenderContextTests.Font font=new Pc1RenderContextTests.Font();Pc1RenderContextTests.Fixture f=new Pc1RenderContextTests.Fixture(font,null);Graphics platform=new Graphics();Pc1GraphicsPipeline graph=new Pc1GraphicsPipeline(f.host.pc1,new Pc1Compositor(Pc1RenderContextTests.solid(64,48,0x102030)),Pc1GlyphCache.zeroBss(),font,f.backend,platform);f.context=graph.render();f.initializeKnownScene();
        f.backend.images.put("bg",Pc1GraphicsResources.Source.rgb(Pc1RenderContextTests.solid(64,48,0x204060)));graph.render().path(0,new byte[]{'b','g'});
        Pc1Audio.Backend noDevice=(Pc1Audio.Backend)Proxy.newProxyInstance(Pc1Audio.Backend.class.getClassLoader(),new Class<?>[]{Pc1Audio.Backend.class},(proxy,method,args)->{if(method.getName().equals("pcmInitialized"))return false;if(method.getName().equals("bindControl"))return null;throw new IOException("unexpected physical audio call"+method.getName());});
        Pc1Audio audio=new Pc1Audio(noDevice,Pc1Audio.catalog(f.data.names));Clock clock=new Clock(graph);
        Pc1TextServices services=new Pc1TextServices(f.host.pc1,graph,audio,f.host::nativeSettings,clock,selection->{throw new IOException("unexpected418220 redraw");},new Pc1TextServices.Transition(){public Pc1TextPresentation.Transition begin(int target,int argument)throws IOException{throw new IOException("unimplemented test41A0B0");}public Pc1TextPresentation.Transition resume()throws IOException{throw new IOException("unimplemented test41A0B0");}});
        for(Pc1TextPresentation.Global field:Pc1TextPresentation.Global.values())services.global(field);
        services.global(Pc1TextPresentation.Global.RAPID,1);clock.clearRapid=true;check(!services.skipRequested()&&clock.keys==1,"skip queries platform before reading rapid latch changed by callback");clock.clearRapid=false;clock.key=0x8000;check(services.skipRequested()&&clock.keys==2,"Ctrl high bit returns true through same platform service");clock.key=0;
        f.host.pc1.textBuffers().writeExpanded(Pc1TextBuffers.LIVE_BODY,new byte[]{(byte)0x81,0x41});f.host.pc1.textBuffers().clearLiveSpeakerHead();f.host.pc1.argument0(0);f.host.pc1.argument1(2);services.global(Pc1TextPresentation.Global.TEXT_MODE,1);
        Pc1TextPresentation painter=new Pc1TextPresentation();check(services.paint(painter)==1,"complete native417D20 produces first final glyph batch");NativeRgbImage expected=Pc1RenderContextTests.solid(64,48,0x102030);for(int y=0;y<28;y++)for(int x=0;x<28;x++)expected.setRgb(x,y,0xffffff);
        check(Arrays.equals(expected.bgr24(),graph.render().compositor().front().bgr24()),"native text state/layout/cache/GDI-mask kernel changes exact whole shared front");
        check(f.host.pc1.textLayout().glyphCount==1&&f.host.pc1.nativeTextLayout()==services.layout()&&services.global(Pc1TextPresentation.Global.CURSOR)==2,"presenter and Runtime share glyph journal, source metadata and raw cursor");
        check(clock.timers==1&&clock.interval==1&&services.global(Pc1TextPresentation.Global.TIMER1)==77&&clock.snapshotBlocked,"native fast-mode timer clamp and complete-entry snapshot fence reach platform callback");
        check(platform.invalidates==2&&services.paint(painter)==0&&services.global(Pc1TextPresentation.Global.DELAY)==1,"controls and yielded glyph use real invalidation path; next invocation completes instead of another glyph");
        check(services.voiceDone(0),"actual audio controller reports idle after explicit failed device initialization");return checks;
    }
    public static void main(String[] args)throws Exception{System.out.println("PASS "+run()+" connected text services checks");}
}
