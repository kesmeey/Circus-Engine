package org.circus.core;

import java.io.*;
import java.lang.reflect.Proxy;
import java.util.*;

/** Native loader/system/frame components assembled over controlled file/OS
 * leaves. These fixtures do not claim physical file or module admission. */
public final class Pc1ScriptFlowTests {
    private static int checks;
    private static void check(boolean value,String why){checks++;if(!value)throw new AssertionError(why);}
    private static void string(Pc1NativeMemory bus,int address,String text)throws IOException{byte[] raw=text.getBytes("US-ASCII");for(int n=0;n<raw.length;n++)bus.writeAddress(address+n,raw[n]);bus.writeAddress(address+raw.length,0);}
    private static String string(Pc1NativeMemory bus,int address)throws IOException{ByteArrayOutputStream out=new ByteArrayOutputStream();for(int n=0;n<256;n++){int b=bus.readAddress(address+n);if(b==0)return out.toString("US-ASCII");out.write(b);}throw new IOException("fixture name lacks terminator");}
    public static int run()throws Exception{
        checks=0;Pc1RenderContextTests.Font font=new Pc1RenderContextTests.Font();Pc1RenderContextTests.Fixture f=new Pc1RenderContextTests.Fixture(font,null);Pc1Runtime runtime=f.host.pc1;Pc1NativeMemory bus=runtime.nativeMemory();
        Pc1GraphicsPipeline.Services leaves=(Pc1GraphicsPipeline.Services)Proxy.newProxyInstance(Pc1GraphicsPipeline.Services.class.getClassLoader(),new Class<?>[]{Pc1GraphicsPipeline.Services.class},(p,m,a)->{if(m.getName().equals("invalidate"))return null;throw new IOException("unexpected controlled graphics leaf "+m.getName());});
        Pc1GraphicsPipeline graph=new Pc1GraphicsPipeline(runtime,new Pc1Compositor(Pc1RenderContextTests.solid(64,48,0x204060)),Pc1GlyphCache.zeroBss(),font,f.backend,leaves);f.context=graph.render();f.initializeKnownScene();
        Pc1Audio.Backend output=(Pc1Audio.Backend)Proxy.newProxyInstance(Pc1Audio.Backend.class.getClassLoader(),new Class<?>[]{Pc1Audio.Backend.class},(p,m,a)->{if(m.getName().equals("bindControl"))return null;if(m.getName().equals("pcmInitialized"))return false;throw new IOException("unexpected controlled audio leaf "+m.getName());});Pc1Audio audio=new Pc1Audio(output,Pc1Audio.catalog(f.data.names));audio.bindNativeMemory(bus);
        Pc1TextServices.Platform platform=(Pc1TextServices.Platform)Proxy.newProxyInstance(Pc1TextServices.Platform.class.getClassLoader(),new Class<?>[]{Pc1TextServices.Platform.class},(p,m,a)->{throw new IOException("unexpected controlled transition leaf "+m.getName());});Pc1Transition transition=new Pc1Transition(runtime,graph,platform);
        List<String> calls=new ArrayList<>();int[] blocked={0};
        Pc1ScriptFlow.Files files=new Pc1ScriptFlow.Files(){
            private void event(String text)throws IOException{calls.add(text);try{f.runtimeBytes();}catch(IOException expected){blocked[0]++;}}
            public int prepareScenario41AEA0(int raw)throws IOException{event("prepare:"+string(bus,raw));return 1;}
            public void error411050(int raw)throws IOException{throw new IOException("unexpected native error in controlled valid source");}
            public Pc1ScriptLoader.Opened open(Pc1ScriptLoader.AddressedPath path)throws IOException{event("open:"+string(bus,path.name));return new Pc1ScriptLoader.Opened(){int n;public int descriptor(){return 19;}public int read(Pc1ScriptLoader.Destination to,int count)throws IOException{event("read:"+count);byte[] raw=n++==0?new byte[]{1,0,0,0}:n==2?new byte[]{2,0,0,0}:new byte[]{0x63,0x20,0x32,'L',0,0};for(int k=0;k<raw.length;k++)to.write(k,raw[k]);return raw.length;}public int close()throws IOException{event("close");runtime.codeBuffer().writeU8(80,0x91);return -1;}};}
        };
        Pc1ScriptFlow.Persistent persistent=new Pc1ScriptFlow.Persistent(){public void writeSlot41AAD0(int slot)throws IOException{throw new IOException("required full slot leaf");}public void rebuildGraphics41A570(int raw)throws IOException{throw new IOException("required full reconstruction leaf");}};
        Pc1ScriptFlow flow=new Pc1ScriptFlow(runtime,graph,audio,f.host::nativeSettings,transition,files,persistent);string(bus,Pc1ScriptMemory.DEFERRED_PATH,"SYS");runtime.codeBuffer().writeU8(90,0xa3);Pc1ScriptLoader.Result loaded=flow.loader().load(Pc1ScriptMemory.DEFERRED_PATH,1);
        check(loaded.markerAccepted&&loaded.codeReadResult==6&&loaded.closeResult==-1&&calls.equals(Arrays.asList("prepare:SYS","open:SYS","read:4","read:4","read:100000","close")),"ordered native three-read chain executes through the connected flow, ignoring native close failure");
        check(blocked[0]==6&&runtime.waitKind()==-1&&runtime.codeBuffer().readU8(80)==0x91&&runtime.codeBuffer().readU8(90)==0xa3&&runtime.checkpointMemory().count()==1,"complete native consumers retain callback mutation/stale code/header in shared owners under snapshot fence");
        check(string(bus,0x9d858c).equals("SYS")&&runtime.nativeFrame().dword(Pc1NativeSlot.Dword.RAW_CODE_PC)==2,"loader publishes live script and raw PC without parsed reload");
        string(bus,Pc1ScriptMemory.DEFERRED_PATH,"L");flow.jumpLabel(Pc1ScriptMemory.DEFERRED_PATH);check(runtime.nativeFrame().dword(Pc1NativeSlot.Dword.RAW_CODE_PC)==2&&calls.size()==6,"4115A0 scans current raw code without file IO");
        runtime.nativeVariable(9,5,7);runtime.nativeFrame().dword(Pc1NativeSlot.Dword.RAW_CODE_PC,77);runtime.waitKind(0x12);string(bus,Pc1ScriptMemory.DEFERRED_PATH,"MENU");calls.clear();blocked[0]=0;check(flow.enterSystem(Pc1ScriptMemory.DEFERRED_PATH,1)==0&&string(bus,0x9d858c).equals("MENU")&&runtime.nativeFrame().dword(Pc1NativeSlot.Dword.RAW_CODE_PC)==2,"system call invokes same loader in mode0 after saving caller and UI raw state");
        check(runtime.systemFrame().readU8(Pc1FrameLayout.ORDINARY_OFFSET+905)==7&&runtime.graphicsMemory().readInt(0x9b9f14)==77&&blocked[0]==6,"system frame/current PC and loader callback fence share the same bus");
        runtime.nativeVariable(9,5,13);runtime.waitKind(0x12);Pc1SystemNative.ReturnFrame returned=flow.system().beginReturn();check(!returned.active()&&returned.outcome()==Pc1SystemNative.Outcome.CONTINUE_DISPATCH&&string(bus,0x9d858c).equals("SYS")&&runtime.nativeFrame().dword(Pc1NativeSlot.Dword.RAW_CODE_PC)==77&&runtime.nativeVariable(9,5)==13,"script-only native return restores caller source/PC while retaining actual live ordinary changes");flow.requireBoundary();return checks;
    }
    public static void main(String[] args)throws Exception{System.out.println("PASS "+run()+" connected script/system flow checks");}
}
