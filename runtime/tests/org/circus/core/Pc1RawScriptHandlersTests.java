package org.circus.core;

import java.io.*;
import java.lang.reflect.Proxy;
import java.util.*;

/** Raw callers keep their local Source across real loader/read/close order. */
public final class Pc1RawScriptHandlersTests {
    private static int checks;
    private static void check(boolean value,String why){checks++;if(!value)throw new AssertionError(why);}
    private static String cstring(Pc1ByteSource source)throws IOException{ByteArrayOutputStream out=new ByteArrayOutputStream();for(int n=0;;n++){int b=source.unsignedByte(n)&255;if(b==0)return out.toString("US-ASCII");out.write(b);}}
    private static void record(ByteArrayOutputStream program,int opcode,String name)throws IOException{program.write(opcode);program.write(name.getBytes("US-ASCII"));program.write(0);}
    public static int run()throws Exception{
        checks=0;Pc1RenderContextTests.Font font=new Pc1RenderContextTests.Font();Pc1RenderContextTests.Fixture f=new Pc1RenderContextTests.Fixture(font,null);Pc1Runtime runtime=f.host.pc1;Pc1NativeMemory bus=runtime.nativeMemory();
        Pc1GraphicsPipeline.Services drawing=(Pc1GraphicsPipeline.Services)Proxy.newProxyInstance(Pc1GraphicsPipeline.Services.class.getClassLoader(),new Class<?>[]{Pc1GraphicsPipeline.Services.class},(p,m,a)->{throw new IOException("controlled required graphics leaf "+m.getName());});
        Pc1GraphicsPipeline graphics=new Pc1GraphicsPipeline(runtime,new Pc1Compositor(Pc1RenderContextTests.solid(64,48,0x204060)),Pc1GlyphCache.zeroBss(),font,f.backend,drawing);f.context=graphics.render();f.initializeKnownScene();
        Pc1Audio.Backend backend=(Pc1Audio.Backend)Proxy.newProxyInstance(Pc1Audio.Backend.class.getClassLoader(),new Class<?>[]{Pc1Audio.Backend.class},(p,m,a)->{if(m.getName().equals("bindControl"))return null;if(m.getName().equals("pcmInitialized"))return false;throw new IOException("controlled required audio leaf "+m.getName());});Pc1Audio audio=new Pc1Audio(backend,Pc1Audio.catalog(f.data.names));audio.bindNativeMemory(bus);
        Pc1TextServices.Platform clock=(Pc1TextServices.Platform)Proxy.newProxyInstance(Pc1TextServices.Platform.class.getClassLoader(),new Class<?>[]{Pc1TextServices.Platform.class},(p,m,a)->{throw new IOException("controlled required clock leaf "+m.getName());});Pc1Transition transition=new Pc1Transition(runtime,graphics,clock);
        Pc1Fetch.Frame caller=Pc1Fetch.Frame.unknown();List<String> calls=new ArrayList<>();
        Pc1ScriptFlow.Files files=new Pc1ScriptFlow.Files(){
            public int prepareScenario41AEA0(int raw)throws IOException{throw new IOException("typed caller must not invent a numeric name");}
            public int prepareScenario41AEA0(Pc1ByteSource source)throws IOException{calls.add("prepare:"+cstring(source));return 19;}
            public void error411050(int raw)throws IOException{throw new IOException("unexpected error for explicit valid fixture");}
            public Pc1ScriptLoader.Opened open(Pc1ScriptLoader.AddressedPath path)throws IOException{throw new IOException("typed caller must not use addressed-path fallback");}
            public Pc1ScriptLoader.Opened open(Pc1ScriptLoader.TypedPath path)throws IOException{
                check(!(path.nameSource() instanceof Pc1ByteSource.Addressed),"native caller source has no fabricated address");calls.add("open:"+cstring(path.nameSource()));
                return new Pc1ScriptLoader.Opened(){int read;public int descriptor(){return 17;}public int read(Pc1ScriptLoader.Destination destination,int count)throws IOException{calls.add("read:"+count);byte[] data=read++==0?new byte[4]:read==2?new byte[0]:new byte[]{0x63,0x20,0x32,'L','A','B','E','L',0,3,0,0};for(int n=0;n<data.length;n++)destination.write(n,data[n]);return data.length;}public int close()throws IOException{calls.add("close");caller.writeByte(Pc1Fetch.Frame.BUFFER,'A');return -1;}};
            }
        };
        Pc1ScriptFlow.Persistent persistent=(Pc1ScriptFlow.Persistent)Proxy.newProxyInstance(Pc1ScriptFlow.Persistent.class.getClassLoader(),new Class<?>[]{Pc1ScriptFlow.Persistent.class},(p,m,a)->{throw new IOException("controlled required persistence leaf "+m.getName());});
        Pc1ScriptFlow scripts=new Pc1ScriptFlow(runtime,graphics,audio,f.host::nativeSettings,transition,files,persistent);
        int[] released={0};Pc1RawDispatch.Leaves other=new Pc1RawDispatch.Leaves(){public void releaseGraphics411080(int selector,int mode)throws IOException{released[0]++;graphics.render().release(selector,mode);}};
        Pc1RawScriptHandlers handlers=new Pc1RawScriptHandlers(runtime,graphics,audio,scripts,other);Pc1Variables variables=new Pc1Variables(bus,null);runtime.bindNativeVariables(variables);Pc1RawDispatch dispatcher=new Pc1RawDispatch(bus,variables,handlers);runtime.bindNativeDispatch(dispatcher,caller);
        final byte[] table=new byte[99*4];for(int n=0;n<99;n++){int target=n==0x38?0x41529d:n==0x3c?0x414f1e:n==0x37?0x41532d:n==0x36?0x4152c6:n==3?0x412817:Pc1RawDispatch.TAIL;for(int k=0;k<4;k++)table[n*4+k]=(byte)(target>>>(k*8));}bus.bind(Pc1RawDispatch.TABLE,table.length,new Pc1NativeMemory.Region(){public int read(int n){return table[n]&255;}public void write(int n,int value){table[n]=(byte)value;}});
        ByteArrayOutputStream code=new ByteArrayOutputStream();record(code,0x38,"LABEL");record(code,0x3c,"HOOK");record(code,0x37,"NEXT");runtime.codeBuffer().writeBytes(0,code.toByteArray());bus.writeInt32(Pc1Fetch.PC,0);check(dispatcher.begin(caller),"raw script invocation uses native code owner");
        check(dispatcher.step(caller).loop&&cstring(Pc1ByteSource.addressed(bus,Pc1ScriptMemory.PENDING_LABEL)).equals("LABEL"),"38 copies live caller local into actual pending-label bytes");
        check(dispatcher.step(caller).loop&&cstring(Pc1ByteSource.addressed(bus,0x9d95dc)).equals("HOOK"),"3C preserves the same typed-source copy semantics in the live frame");
        check(dispatcher.step(caller).loop&&calls.equals(Arrays.asList("prepare:NEXT","open:NEXT","read:4","read:0","read:100000","close")),"37 loads with native mode2 through ordered IO without parsed-script activation");
        check(cstring(Pc1ByteSource.addressed(bus,0x9d858c)).equals("AEXT")&&bus.readInt32(Pc1Fetch.PC)==2&&bus.readAddress(Pc1ScriptMemory.PENDING_LABEL)==0,"late close source mutation publishes real filename while pending label jumps into the actual loaded code");
        check(dispatcher.step(caller).loop,"loaded32 label reaches real native default instead of a parser index");Pc1RawDispatch.Result result=dispatcher.step(caller);check(!result.loop&&runtime.waitKind()==14&&bus.readInt32(Pc1Fetch.LAST_OPCODE)==3,"actual loaded code continues into the native stop boundary");
        code.reset();record(code,0x36,"DEFER");code.write(new byte[]{2,1,0});runtime.codeBuffer().writeBytes(0,code.toByteArray());bus.writeInt32(Pc1Fetch.PC,0);runtime.waitKind(-1);check(dispatcher.begin(caller),"caller explicitly starts deferred-load invocation");result=dispatcher.step(caller);
        check(!result.loop&&runtime.waitKind()==0x36&&bus.readInt32(Pc1Fetch.ARGUMENT0)==3&&released[0]==0&&cstring(Pc1ByteSource.addressed(bus,Pc1ScriptMemory.DEFERRED_PATH)).equals("DEFER"),"36 recognizes cached-ESI companion and publishes mode3/wait/path without releasing graphics");
        int next=bus.readInt32(Pc1Fetch.PC);runtime.codeBuffer().writeBytes(next,new byte[]{1,0,0});bus.writeInt32(Pc1Fetch.PC,0);check(dispatcher.begin(caller),"caller explicitly reenters the other36 branch");result=dispatcher.step(caller);
        check(!result.loop&&bus.readInt32(Pc1Fetch.ARGUMENT0)==1&&released[0]==1&&runtime.waitKind()==0x36,"36 noncompanion branch runs the real release consumer before mode1 publication");handlers.requireBoundary();return checks;
    }
    public static void main(String[] args)throws Exception{System.out.println("PASS "+run()+" raw script graph checks");}
}
