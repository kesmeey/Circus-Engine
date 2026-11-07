package org.circus.core;

import java.io.*;
import java.lang.reflect.Proxy;
import java.util.*;

/** Connected production UI owners, with explicitly controlled OS/file/audio
 * leaves. No fixture callback is presented as a physical implementation. */
public final class Pc1UiServicesTests {
    private static int checks;
    private static void check(boolean value,String why){checks++;if(!value)throw new AssertionError(why);}
    private static void put(Pc1NativeMemory bus,int at,int value)throws IOException{for(int n=0;n<4;n++)bus.writeAddress(at+n,value>>>(8*n));}
    public static int run()throws Exception{
        checks=0;Pc1RenderContextTests.Font font=new Pc1RenderContextTests.Font();Pc1RenderContextTests.Fixture f=new Pc1RenderContextTests.Fixture(font,null);
        Pc1GraphicsPipeline.Services graphicsLeaves=(Pc1GraphicsPipeline.Services)Proxy.newProxyInstance(Pc1GraphicsPipeline.Services.class.getClassLoader(),new Class<?>[]{Pc1GraphicsPipeline.Services.class},(p,m,a)->{if(m.getName().equals("invalidate"))return null;throw new IOException("unexpected controlled graphics leaf "+m.getName());});
        Pc1HeapLease.Heap fixtureHeap=new Pc1HeapLease.Heap(){public Pc1HeapLease allocate(int length)throws IOException{return Pc1HeapLease.managed(new byte[length],"controlled UI capture fixture");}public boolean release(Pc1HeapLease lease){return true;}};
        Pc1GraphicsPipeline graph=new Pc1GraphicsPipeline(f.host.pc1,new Pc1Compositor(Pc1RenderContextTests.solid(64,48,0x102030),fixtureHeap),Pc1GlyphCache.zeroBss(),font,f.backend,graphicsLeaves);f.context=graph.render();f.initializeKnownScene();
        List<String> calls=new ArrayList<>();boolean[] blocked={false,false};
        Pc1Audio.Backend output=(Pc1Audio.Backend)Proxy.newProxyInstance(Pc1Audio.Backend.class.getClassLoader(),new Class<?>[]{Pc1Audio.Backend.class},(p,m,a)->{
            if(m.getName().equals("bindControl"))return null;if(m.getName().equals("pcmInitialized"))return true;
            if(m.getName().equals("loadPcm")){calls.add("voice:"+a[0]+":"+new String((byte[])a[1],"US-ASCII"));try{f.runtimeBytes();}catch(IOException expected){blocked[0]=true;}return null;}
            if(m.getName().equals("stop")||m.getName().equals("release")){calls.add(m.getName()+":"+a[0]);return 0;}
            throw new IOException("unexpected controlled audio leaf "+m.getName());
        });
        Pc1Audio audio=new Pc1Audio(output,Pc1Audio.catalog(f.data.names));audio.adoptAlreadyInitialized();audio.bindNativeMemory(f.host.pc1.nativeMemory());
        Pc1UiServices.Platform platform=new Pc1UiServices.Platform(){public void setCapture(){calls.add("capture");}public void releaseCapture(){calls.add("releaseCapture");}public int setTimer(int id,int ms){calls.add("timer:"+id+":"+ms);return 73;}public void killTimer(int token){calls.add("kill:"+token);}public Pc1UiRaster.Point cursorClient(){return new Pc1UiRaster.Point(12,13);}};
        Pc1UiServices.ScriptAndSlots scripts=new Pc1UiServices.ScriptAndSlots(){public int enterSystem(int raw,int mode)throws IOException{throw new IOException("unexpected system fixture leaf");}public void loadScript(int raw,int mode)throws IOException{calls.add("script:"+Integer.toHexString(raw)+":"+mode);try{f.runtimeBytes();}catch(IOException expected){blocked[1]=true;}f.host.pc1.codeBuffer().writeBytes(60,new byte[]{'X',0});}public void jumpLabel(int raw)throws IOException{throw new IOException("unexpected label fixture leaf");}public void writeSlot(int slot)throws IOException{throw new IOException("unexpected slot fixture leaf");}};
        Pc1UiServices ui=new Pc1UiServices(f.host.pc1,graph,audio,f.host::nativeSettings,platform,scripts);Pc1NativeMemory bus=f.host.pc1.nativeMemory();
        f.host.pc1.codeBuffer().writeBytes(0,new byte[]{'5','V','O','I','C','E','\\','z','z','z','_','X',0});put(bus,Pc1CheckpointMemory.TABLE,0);put(bus,0x9d8a24,0);ui.historySelect(0);
        check(calls.equals(Arrays.asList("voice:2:VOICE\\zzz_X"))&&blocked[0],"raw log/checkpoint/code reaches same audio with copied NUL removed; whole Host save stays fenced during audio load");check(!audio.loaded(2),"explicit failed audio load remains failed in the canonical controller");
        calls.clear();put(bus,0x9d8a20,1);check(ui.input().input(0,0,13)==1&&calls.equals(Arrays.asList("script:9714e4:1")),"WAIT1 input reaches required native file chain with original raw name and mode");check(blocked[1]&&f.host.pc1.waitKind()==-1&&f.host.pc1.codeBuffer().readU8(60)=='X',"script leaf mutates existing code owner inside the full input fence and original final WAIT store");
        ui.writeVariable(9,17,0x123);check(ui.variable(9,17)==255&&bus.readAddress(Pc1FrameLayout.LIVE_BASE+Pc1FrameLayout.ORDINARY_OFFSET+917)==255,"UI variable setter/read and raw frame observe one saturated ordinary-byte owner");
        ui.modal().position1a(0x40000001,-2);check(f.memory.readInt(0x9d8010)==8&&f.memory.readInt(0x9d8014)==-16&&f.host.pc1.waitKind()==0x1a,"connected position opcode preserves wrapped coordinates and native wait");
        f.host.pc1.codeBuffer().writeBytes(100,new byte[]{'A','B',0});f.memory.writeInt(Pc1InlineText.RUBY_PHASE,0);f.memory.writeInt(Pc1DrawBatch.PALETTE,0xffffff);ui.inline418930(1,2,0,Pc1NativeMemory.CODE_BASE+100,0);check(graph.render().compositor().front().rgb(1,2)==0xffffff&&f.memory.readInt(Pc1InlineText.X)==29,"modal inline consumer draws into existing front using canonical live source and cursor");
        int handle=ui.capture().rect(1,2,9,8);check(handle>=20&&graph.render().compositor().rgb(handle).rgb(0,0)==0xffffff&&graph.render().compositor().activeTarget()==0,"capture shares the populated scene pool and finishes at native front target");
        byte[] saved=f.runtimeBytes();f.host.pc1.load(new DataInputStream(new ByteArrayInputStream(saved)));check(Arrays.equals(saved,f.runtimeBytes())&&ui.variable(9,17)==255,"existing UI consumer still sees canonical restored bytes without rebinding duplicate state");
        boolean rejected=false;try{new Pc1UiServices(f.host.pc1,graph,audio,f.host::nativeSettings,platform,scripts);}catch(IOException expected){rejected=true;}check(rejected,"same raster rejects a second input service graph");ui.requireBoundary();return checks;
    }
    public static void main(String[] args)throws Exception{System.out.println("PASS "+run()+" connected UI services checks");}
}
