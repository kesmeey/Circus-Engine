package org.circus.core;

import java.io.*;
import java.lang.reflect.Proxy;
import java.util.*;

/** Wait dispatch draws through the real canonical pixel/Pool graph, with
 *explicitly controlled platform leaves and original partial-failure order. */
public final class Pc1WaitServicesTests {
    private static int checks;
    private static void check(boolean value,String why){checks++;if(!value)throw new AssertionError(why);}
    private static void put(Pc1NativeMemory bus,int at,int value)throws IOException{bus.writeInt32(at,value);}
    public static int run()throws Exception{
        checks=0;
        Pc1RenderContextTests.Font font=new Pc1RenderContextTests.Font();
        Pc1RenderContextTests.Fixture fixture=new Pc1RenderContextTests.Fixture(font,null);
        Pc1Runtime runtime=fixture.host.pc1;Pc1NativeMemory bus=runtime.nativeMemory();
        int[] presents={0},timers={0},keys={0},ticks={1500};boolean[] fences={false,false};
        Pc1GraphicsPipeline.Services leaves=(Pc1GraphicsPipeline.Services)Proxy.newProxyInstance(Pc1GraphicsPipeline.Services.class.getClassLoader(),new Class<?>[]{Pc1GraphicsPipeline.Services.class},(p,m,a)->{
            if(m.getName().equals("invalidate")){presents[0]++;try{fixture.runtimeBytes();}catch(IOException expected){fences[0]=true;}try{fixture.renderBytes();}catch(IOException expected){fences[1]=true;}return null;}
            throw new IOException("controlled required graphics leaf "+m.getName());
        });
        Pc1HeapLease.Heap heap=new Pc1HeapLease.Heap(){public Pc1HeapLease allocate(int bytes)throws IOException{return Pc1HeapLease.managed(new byte[bytes],"controlled wait capture");}public boolean release(Pc1HeapLease lease){return true;}};
        Pc1GraphicsPipeline graphics=new Pc1GraphicsPipeline(runtime,new Pc1Compositor(Pc1RenderContextTests.solid(64,48,0x204060),heap),Pc1GlyphCache.zeroBss(),font,fixture.backend,leaves);
        fixture.context=graphics.render();fixture.initializeKnownScene();
        //The test explicitly owns only this label. No unknown PE bytes are
        //made readable to obtain a capture or to bypass StaticImage admission.
        final byte[] label={'C','A','P',0};bus.bind(0x43d9d4,label.length,new Pc1NativeMemory.Region(){public int read(int at){return label[at]&255;}public void write(int at,int value){label[at]=(byte)value;}});
        Pc1Audio.Backend backend=(Pc1Audio.Backend)Proxy.newProxyInstance(Pc1Audio.Backend.class.getClassLoader(),new Class<?>[]{Pc1Audio.Backend.class},(p,m,a)->{if(m.getName().equals("bindControl"))return null;if(m.getName().equals("pcmInitialized"))return false;throw new IOException("controlled required audio leaf "+m.getName());});
        Pc1Audio audio=new Pc1Audio(backend,Pc1Audio.catalog(fixture.data.names));audio.bindNativeMemory(bus);
        Pc1TextServices.Platform platform=(Pc1TextServices.Platform)Proxy.newProxyInstance(Pc1TextServices.Platform.class.getClassLoader(),new Class<?>[]{Pc1TextServices.Platform.class},(p,m,a)->{
            if(m.getName().equals("setTimer")){timers[0]++;check((int)a[0]==1&&(int)a[1]==40,"407040 receives timer1 and current40ms delay");return 77;}
            if(m.getName().equals("asyncKeyState")){keys[0]++;check((int)a[0]==0x11,"native skip queries actual Ctrl key");return 0;}
            if(m.getName().equals("tickCount"))return ticks[0];
            throw new IOException("controlled required text platform leaf "+m.getName());
        });
        Pc1Transition transition=new Pc1Transition(runtime,graphics,platform);
        Pc1ScriptFlow.Files files=(Pc1ScriptFlow.Files)Proxy.newProxyInstance(Pc1ScriptFlow.Files.class.getClassLoader(),new Class<?>[]{Pc1ScriptFlow.Files.class},(p,m,a)->{throw new IOException("controlled required script leaf "+m.getName());});
        Pc1ScriptFlow.Persistent persistent=(Pc1ScriptFlow.Persistent)Proxy.newProxyInstance(Pc1ScriptFlow.Persistent.class.getClassLoader(),new Class<?>[]{Pc1ScriptFlow.Persistent.class},(p,m,a)->{throw new IOException("controlled required persistent leaf "+m.getName());});
        Pc1ScriptFlow scripts=new Pc1ScriptFlow(runtime,graphics,audio,fixture.host::nativeSettings,transition,files,persistent);
        Pc1TextServices text=new Pc1TextServices(runtime,graphics,audio,fixture.host::nativeSettings,platform,transition);
        Pc1WaitServices.Native nativeLeaves=(Pc1WaitServices.Native)Proxy.newProxyInstance(Pc1WaitServices.Native.class.getClassLoader(),new Class<?>[]{Pc1WaitServices.Native.class},(p,m,a)->{
            if(m.getName().equals("specialStart404770")){put(bus,0x9cca8c,55);throw new IOException("controlled required404770 failure");}
            throw new IOException("controlled required native leaf "+m.getName());
        });
        Pc1WaitServices waits=new Pc1WaitServices(runtime,graphics,audio,fixture.host::nativeSettings,text,new Pc1TextPresentation(),scripts,nativeLeaves);
        byte[] front=graphics.render().compositor().front().bgr24();put(bus,0x9d8a20,13);put(bus,0x9da4c0,3);put(bus,0x9da4c4,1);put(bus,0x9cca8c,0);put(bus,Pc1DrawBatch.PALETTE+7*4,0xee3311);
        check(waits.poll()==1&&graphics.render().compositor().front().rgb(0,0)==0xee3311,"WAIT13 reaches actual palette fill on the same front image");
        int captured=bus.readInt32(0x9b96e0);check(captured>=20&&Arrays.equals(front,graphics.render().compositor().rgb(captured).bgr24())&&bus.readInt32(0xbb8434)==1,"capture owns original front bytes in the existing Pool and publishes busy");
        check(fences[0]&&fences[1]&&timers[0]==1&&bus.readInt32(Pc1Timer1.TOKEN)==77,"whole wait callback fences Runtime and render and publishes actual timer token");
        check(waits.poll()==1&&Arrays.equals(front,graphics.render().compositor().front().bgr24())&&timers[0]==1,"second native iteration restores capture without restarting the existing timer");
        check(waits.poll()==0&&bus.readInt32(0x9b96e0)==-1&&bus.readInt32(0xbb8434)==0&&!graphics.render().compositor().loaded(captured)&&presents[0]==2,"cleanup frees the same capture before resetting handle and busy, without invented present");
        put(bus,0x9d8a20,14);check(waits.poll()==3&&presents[0]==2,"WAIT14 retains raw dispatcher return3 with no drawing");
        put(bus,0x9d8a20,7);put(bus,0xbb842c,1);check(waits.poll()==0&&keys[0]==1&&bus.readInt32(0x9d8a20)==7&&presents[0]==3,"WAIT7 first draws/presents its wait cursor, then queries platform before rapid latch without overwriting WAIT");
        put(bus,0x9d8a20,0x12);put(bus,0x9da4c0,7);put(bus,0x9da4c4,4);put(bus,Pc1WaitDispatcher.TICK,100);check(waits.poll()==0&&bus.readInt32(Pc1WaitDispatcher.TICK)==1500&&timers[0]==1,"timed wait consumes actual tick difference and advances the same tick owner");
        put(bus,0x9d8a20,0x1d);put(bus,0xbb8470,0);put(bus,0x94bc24,0);put(bus,0x430284,13);int beforeFault=presents[0];boolean failed=false;try{waits.poll();}catch(IOException expected){failed=expected.getMessage().contains("required404770");}
        check(failed&&bus.readInt32(0x9cca8c)==55&&bus.readInt32(0x430284)==13&&presents[0]==beforeFault,"required minigame failure keeps its real prefix and does not invent delay/present after the fault");
        waits.requireBoundary();fixture.runtimeBytes();fixture.renderBytes();return checks;
    }
    public static void main(String[] args)throws Exception{System.out.println("PASS "+run()+" connected wait services checks");}
}
