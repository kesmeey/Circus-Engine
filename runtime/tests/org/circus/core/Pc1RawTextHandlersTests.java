package org.circus.core;

import java.io.*;
import java.lang.reflect.Proxy;

/** Real native fetch, live text expansion and canonical wait consumers. */
public final class Pc1RawTextHandlersTests {
    private static int checks;
    private static void check(boolean value,String why){checks++;if(!value)throw new AssertionError(why);}
    private static byte[] encoded(int opcode,String value)throws IOException{ByteArrayOutputStream out=new ByteArrayOutputStream();out.write(opcode);for(byte b:value.getBytes("US-ASCII"))out.write((b&255)-0x20);out.write(0);return out.toByteArray();}
    public static int run()throws Exception{
        checks=0;Pc1RenderContextTests.Font font=new Pc1RenderContextTests.Font();Pc1RenderContextTests.Fixture fixture=new Pc1RenderContextTests.Fixture(font,null);Pc1Runtime runtime=fixture.host.pc1;Pc1NativeMemory bus=runtime.nativeMemory();
        Pc1GraphicsPipeline.Services drawing=(Pc1GraphicsPipeline.Services)Proxy.newProxyInstance(Pc1GraphicsPipeline.Services.class.getClassLoader(),new Class<?>[]{Pc1GraphicsPipeline.Services.class},(p,m,a)->{throw new IOException("controlled required graphics leaf "+m.getName());});
        Pc1GraphicsPipeline graphics=new Pc1GraphicsPipeline(runtime,new Pc1Compositor(Pc1RenderContextTests.solid(64,48,0x203040)),Pc1GlyphCache.zeroBss(),font,fixture.backend,drawing);fixture.context=graphics.render();fixture.initializeKnownScene();
        Pc1Audio.Backend backend=(Pc1Audio.Backend)Proxy.newProxyInstance(Pc1Audio.Backend.class.getClassLoader(),new Class<?>[]{Pc1Audio.Backend.class},(p,m,a)->{if(m.getName().equals("bindControl"))return null;if(m.getName().equals("pcmInitialized"))return false;throw new IOException("controlled required audio leaf "+m.getName());});Pc1Audio audio=new Pc1Audio(backend,Pc1Audio.catalog(fixture.data.names));audio.bindNativeMemory(bus);
        final int[] keyCalls={0},redrawCalls={0};final boolean[] runtimeBlocked={false},renderBlocked={false};
        Pc1TextServices.Platform platform=(Pc1TextServices.Platform)Proxy.newProxyInstance(Pc1TextServices.Platform.class.getClassLoader(),new Class<?>[]{Pc1TextServices.Platform.class},(p,m,a)->{if(m.getName().equals("asyncKeyState")){check((Integer)a[0]==0x11,"07 asks the actual Ctrl key");keyCalls[0]++;return 0x8000;}throw new IOException("controlled required text platform "+m.getName());});
        Pc1TextServices text=new Pc1TextServices(runtime,graphics,audio,fixture.host::nativeSettings,platform,selection->{
            check(selection==-1&&bus.readInt32(Pc1Fetch.WAIT)==9,"09 redraw observes its native wait publication");redrawCalls[0]++;
            try{fixture.runtimeBytes();}catch(IOException expected){runtimeBlocked[0]=true;}
            try{fixture.renderBytes();}catch(IOException expected){renderBlocked[0]=true;}
        },new Pc1TextServices.Transition(){public Pc1TextPresentation.Transition begin(int target,int argument)throws IOException{throw new IOException("controlled required transition");}public Pc1TextPresentation.Transition resume()throws IOException{throw new IOException("controlled required transition resume");}});
        Pc1TextExpander.Live expansion=new Pc1TextExpander.Live(bus,new Pc1TextExpander.LinkedCrt(bus));
        Pc1RawTextHandlers handlers=new Pc1RawTextHandlers(runtime,graphics,audio,text,expansion,null);
        Pc1Variables variables=new Pc1Variables(bus,null);runtime.bindNativeVariables(variables);Pc1RawDispatch dispatcher=new Pc1RawDispatch(bus,variables,handlers);Pc1Fetch.Frame frame=Pc1Fetch.Frame.unknown();runtime.bindNativeDispatch(dispatcher,frame);
        final byte[] table=new byte[99*4];for(int n=0;n<99;n++){int target=n==0x4b?0x415681:n==0x4c?0x415695:n==7?0x412898:n==9?0x413413:n==3?0x412817:Pc1RawDispatch.TAIL;for(int k=0;k<4;k++)table[n*4+k]=(byte)(target>>>(k*8));}bus.bind(Pc1RawDispatch.TABLE,table.length,new Pc1NativeMemory.Region(){public int read(int at){return table[at]&255;}public void write(int at,int value){table[at]=(byte)value;}});
        ByteArrayOutputStream program=new ByteArrayOutputStream();program.write(encoded(0x4b,"S"));program.write(encoded(0x4c,"Hi$n"));program.write(new byte[]{7,1,0,3,0,0,9,0,0});runtime.codeBuffer().writeBytes(0,program.toByteArray());bus.writeInt32(Pc1Fetch.PC,0);
        check(dispatcher.begin(frame),"native text invocation enters the canonical Runtime");Pc1RawDispatch.Result result=dispatcher.step(frame);
        check(result.loop&&bus.readAddress(Pc1TextProducer.SPEAKER)=='S'&&bus.readAddress(Pc1TextProducer.SPEAKER+1)==0&&bus.readAddress(Pc1TextProducer.SPEAKER+2)==0,"4B consumes exactly once-decoded local bytes and writes native double terminator");
        Pc1Fetch.Dispatch fetched=dispatcher.next(frame);frame.writeByte(Pc1Fetch.Frame.BUFFER,'B');bus.writeAddress(0x454d34,'N');bus.writeAddress(0x454d35,0);result=dispatcher.execute(frame,fetched);
        check(!result.loop&&result.nativeResult()==0&&bus.readInt32(Pc1Fetch.WAIT)==0x4c,"4C reaches the actual wait and common-tail return0");
        check(bus.readAddress(Pc1TextProducer.BODY)=='B'&&bus.readAddress(Pc1TextProducer.BODY+1)=='i'&&bus.readAddress(Pc1TextProducer.BODY+2)=='N'&&bus.readAddress(Pc1TextProducer.BODY+3)==0&&bus.readAddress(Pc1TextProducer.BODY+4)==0,"body reads changed caller locals and current name bytes without early CString copy");
        check(bus.readAddress(Pc1TextProducer.SPEAKER)=='S'&&bus.readInt32(Pc1Fetch.LAST_OPCODE)==0x4c,"body retains immediately preceding speaker through real Frame.previousOpcode");
        check(dispatcher.begin(frame),"waiting body explicitly reenters native caller");fetched=dispatcher.next(frame);bus.writeInt32(Pc1Fetch.ARGUMENT0,0);bus.writeInt32(0x9da4ac,1);result=dispatcher.execute(frame,fetched);
        check(result.loop&&keyCalls[0]==1&&bus.readInt32(0xbb84e4)==1&&bus.readAddress(0x9d8a25)==0x80,"07 consumes cached EDX and real skip query, then continues without invented wait");
        result=dispatcher.step(frame);check(!result.loop&&bus.readInt32(Pc1Fetch.WAIT)==14,"following native stop exits after skipped wait");
        bus.writeInt32(Pc1Fetch.WAIT,-1);bus.writeInt32(Pc1Fetch.DEVICE,42);check(dispatcher.begin(frame),"next native invocation can enter after explicit wait update");fetched=dispatcher.next(frame);bus.writeInt32(Pc1Fetch.DEVICE,99);result=dispatcher.execute(frame,fetched);
        check(!result.loop&&result.nativeResult()==0&&bus.readInt32(0x9b8e1c)==42&&redrawCalls[0]==1,"09 consumes captured ECX rather than rereading changed device global");
        check(runtimeBlocked[0]&&renderBlocked[0],"entire callback is fenced in both Runtime and render snapshots");handlers.requireBoundary();graphics.render().requireBoundary();fixture.runtimeBytes();
        check(runtime.nativeTextExpansion()==expansion,"raw text and addressed UI share the one live expansion consumer");
        byte[] alias={'A','B','C',0,0,0,0,0};int aliasBase=0xe1000010;bus.bind(aliasBase,alias.length,new Pc1NativeMemory.Region(){public int read(int at){return alias[at]&255;}public void write(int at,int value){alias[at]=(byte)value;}});
        boolean failed=false;try{runtime.expandNativeUi(aliasBase,aliasBase+1);}catch(IOException expected){failed=expected.getMessage().contains("unowned");}
        boolean repeated=true;for(byte b:alias)repeated&=b=='A';check(failed&&repeated,"addressed UI expansion observes forward source/output alias and keeps stores preceding unknown neighbor");
        boolean duplicate=false;try{runtime.bindNativeTextExpansion(expansion);}catch(IOException expected){duplicate=true;}check(duplicate,"canonical expansion cannot be rebound after publication");
        return checks;
    }
    public static void main(String[] args)throws Exception{System.out.println("PASS "+run()+" raw text graph checks");}
}
