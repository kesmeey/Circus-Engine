package org.circus.core;

import java.io.*;
import java.lang.reflect.Proxy;

/** Real fetch register caching reaches real UI state without the parsed VM. */
public final class Pc1RawUiHandlersTests {
    private static int checks;
    private static void check(boolean value,String why){checks++;if(!value)throw new AssertionError(why);}
    private static byte[] words(int opcode,int a0,int a1,int a2,int a3){byte[] raw=new byte[9];raw[0]=(byte)opcode;int[] args={a0,a1,a2,a3};for(int n=0;n<4;n++){raw[1+n*2]=(byte)args[n];raw[2+n*2]=(byte)(args[n]>>>8);}return raw;}
    public static int run()throws Exception{
        checks=0;Pc1RenderContextTests.Font font=new Pc1RenderContextTests.Font();Pc1RenderContextTests.Fixture fixture=new Pc1RenderContextTests.Fixture(font,null);Pc1Runtime runtime=fixture.host.pc1;Pc1NativeMemory bus=runtime.nativeMemory();
        Pc1GraphicsPipeline.Services draw=(Pc1GraphicsPipeline.Services)Proxy.newProxyInstance(Pc1GraphicsPipeline.Services.class.getClassLoader(),new Class<?>[]{Pc1GraphicsPipeline.Services.class},(p,m,a)->{throw new IOException("controlled required graphics leaf "+m.getName());});
        Pc1GraphicsPipeline graphics=new Pc1GraphicsPipeline(runtime,new Pc1Compositor(Pc1RenderContextTests.solid(64,48,0x102030)),Pc1GlyphCache.zeroBss(),font,fixture.backend,draw);fixture.context=graphics.render();fixture.initializeKnownScene();
        Pc1Audio.Backend backend=(Pc1Audio.Backend)Proxy.newProxyInstance(Pc1Audio.Backend.class.getClassLoader(),new Class<?>[]{Pc1Audio.Backend.class},(p,m,a)->{if(m.getName().equals("bindControl"))return null;if(m.getName().equals("pcmInitialized"))return false;throw new IOException("controlled required audio leaf "+m.getName());});Pc1Audio audio=new Pc1Audio(backend,Pc1Audio.catalog(fixture.data.names));audio.bindNativeMemory(bus);
        Pc1UiServices.Platform platform=(Pc1UiServices.Platform)Proxy.newProxyInstance(Pc1UiServices.Platform.class.getClassLoader(),new Class<?>[]{Pc1UiServices.Platform.class},(p,m,a)->{throw new IOException("controlled required UI platform leaf "+m.getName());});
        Pc1UiServices.ScriptAndSlots scripts=(Pc1UiServices.ScriptAndSlots)Proxy.newProxyInstance(Pc1UiServices.ScriptAndSlots.class.getClassLoader(),new Class<?>[]{Pc1UiServices.ScriptAndSlots.class},(p,m,a)->{throw new IOException("controlled required script leaf "+m.getName());});
        Pc1UiServices ui=new Pc1UiServices(runtime,graphics,audio,fixture.host::nativeSettings,platform,scripts);
        Pc1TextServices.Platform clock=(Pc1TextServices.Platform)Proxy.newProxyInstance(Pc1TextServices.Platform.class.getClassLoader(),new Class<?>[]{Pc1TextServices.Platform.class},(p,m,a)->{throw new IOException("controlled required transition leaf "+m.getName());});
        Pc1Transition transition=new Pc1Transition(runtime,graphics,clock);
        Pc1RawDispatch.Leaves required=new Pc1RawDispatch.Leaves(){public Pc1RawDispatch.Flow handler(int target,Pc1RawDispatch.Registers regs,Pc1Fetch.Frame locals)throws IOException{bus.writeInt32(0x430604,77);throw new IOException("controlled required target "+Integer.toHexString(target));}};
        Pc1RawUiHandlers handlers=new Pc1RawUiHandlers(runtime,graphics,ui,transition,required);
        Pc1Variables variables=new Pc1Variables(bus,null);runtime.bindNativeVariables(variables);Pc1RawDispatch dispatcher=new Pc1RawDispatch(bus,variables,handlers);Pc1Fetch.Frame frame=Pc1Fetch.Frame.unknown();runtime.bindNativeDispatch(dispatcher,frame);
        final byte[] table=new byte[99*4];for(int n=0;n<99;n++){int target=n==0x5f?0x4136c7:n==0x55?0x41355f:n==0x1a?0x414ef5:n==4?0x12345678:Pc1RawDispatch.TAIL;for(int k=0;k<4;k++)table[n*4+k]=(byte)(target>>>(k*8));}bus.bind(Pc1RawDispatch.TABLE,table.length,new Pc1NativeMemory.Region(){public int read(int at){return table[at]&255;}public void write(int at,int value){table[at]=(byte)value;}});
        ByteArrayOutputStream program=new ByteArrayOutputStream();program.write(words(0x5f,1,2,3,4));program.write(words(0x55,11,12,13,14));program.write(new byte[]{0x1a,7,9});runtime.codeBuffer().writeBytes(0,program.toByteArray());bus.writeInt32(Pc1Fetch.PC,0);
        check(dispatcher.begin(frame),"raw native invocation enters existing runtime/UI graph");
        Pc1Fetch.Dispatch fetched=dispatcher.next(frame);bus.writeInt32(Pc1Fetch.ARGUMENT0,901);bus.writeInt32(Pc1Fetch.ARGUMENT1,902);frame.setArgument2(903);frame.setArgument3(904);Pc1RawDispatch.Result result=dispatcher.execute(frame,fetched);
        check(result.loop&&bus.readInt32(0x955550)==1&&bus.readInt32(0x955548)==2&&bus.readInt32(0x955400)==3&&bus.readInt32(0x9553fc)==4,"5F routes by actual target and uses cached EDX/EBP/EDI/EBX despite changed globals/locals");
        fetched=dispatcher.next(frame);bus.writeInt32(Pc1Fetch.ARGUMENT0,801);bus.writeInt32(Pc1Fetch.ARGUMENT1,802);frame.setArgument2(803);frame.setArgument3(804);result=dispatcher.execute(frame,fetched);
        check(result.loop&&bus.readInt32(0x955408)==11&&bus.readInt32(0x95540c)==12&&bus.readInt32(0x955410)==13&&bus.readInt32(0x955414)==14&&bus.readInt32(Pc1NativeUi.LIST_VISIBLE)==1,"55 writes captured coordinates into the canonical visible rectangle and publishes count once");
        fetched=dispatcher.next(frame);bus.writeInt32(Pc1Fetch.ARGUMENT0,101);bus.writeInt32(Pc1Fetch.ARGUMENT1,102);result=dispatcher.execute(frame,fetched);
        check(!result.loop&&result.nativeResult()==0&&runtime.waitKind()==0x1a&&bus.readInt32(0x9d8010)==56&&bus.readInt32(0x9d8014)==72,"1A modal position uses short-fetch registers and returns through the real common tail");
        check(bus.readInt32(Pc1Fetch.LAST_OPCODE)==0x1a&&runtime.nativeDispatchFrame()==frame,"last opcode and preserved caller locals belong to the same Runtime");
        runtime.codeBuffer().writeBytes(0,new byte[]{4,0,0});bus.writeInt32(Pc1Fetch.PC,0);check(dispatcher.begin(frame),"caller explicitly reenters with its known test locals");boolean failed=false;try{dispatcher.step(frame);}catch(IOException expected){failed=expected.getMessage().contains("required target 12345678");}
        check(failed&&bus.readInt32(0x430604)==77,"unknown target keeps explicit required leaf and its preceding real mutation");boolean blocked=false;try{fixture.runtimeBytes();}catch(IOException expected){blocked=expected.getMessage().contains("unresolved");}check(blocked,"unresolved handler cannot be saved as a completed native operation");return checks;
    }
    public static void main(String[] args)throws Exception{System.out.println("PASS "+run()+" raw UI graph checks");}
}
