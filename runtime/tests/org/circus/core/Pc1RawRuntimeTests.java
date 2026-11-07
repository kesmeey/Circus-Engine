package org.circus.core;

import java.io.*;
import java.util.Arrays;

/** Native caller locals survive portable Runtime restoration in place. */
public final class Pc1RawRuntimeTests {
    private static int checks;
    private static void check(boolean value,String why){checks++;if(!value)throw new AssertionError(why);}
    private static byte[] save(Pc1Runtime runtime)throws IOException{ByteArrayOutputStream out=new ByteArrayOutputStream();runtime.save(new DataOutputStream(out));return out.toByteArray();}
    private static Pc1Runtime runtime()throws IOException{Pc1RenderContextTests.Fixture files=new Pc1RenderContextTests.Fixture(new Pc1RenderContextTests.Font(),null);return PcHost.pc1BeforeWinMain(files.data,TextCodec.identity()).pc1;}
    private static void table(Pc1NativeMemory bus)throws IOException{
        final byte[] raw=new byte[99*4];for(int n=0;n<99;n++){int target=n==0x58?0x4139f6:n==3?0x412817:Pc1RawDispatch.TAIL;for(int k=0;k<4;k++)raw[n*4+k]=(byte)(target>>>(k*8));}
        bus.bind(Pc1RawDispatch.TABLE,raw.length,new Pc1NativeMemory.Region(){public int read(int offset){return raw[offset]&255;}public void write(int offset,int value)throws IOException{throw new IOException("explicit read-only synthetic target table");}});
    }
    public static int run()throws Exception{
        checks=0;Pc1Runtime runtime=runtime();Pc1NativeMemory bus=runtime.nativeMemory();table(bus);
        Pc1Variables variables=new Pc1Variables(bus,null);runtime.bindNativeVariables(variables);
        Pc1RawDispatch dispatcher=new Pc1RawDispatch(bus,variables,null);Pc1Fetch.Frame frame=Pc1Fetch.Frame.unknown();frame.writeByte(Pc1Fetch.Frame.BUFFER,'A');Pc1GlyphCache.Source alias=frame.source();runtime.bindNativeDispatch(dispatcher,frame);
        runtime.codeBuffer().writeBytes(0,new byte[]{0x58,0,0,0,0,123,0,0,0,3,0,0});bus.writeInt32(Pc1Fetch.PC,0);
        check(dispatcher.begin(frame)&&frame.atNextBoundary(),"actual raw invocation enters with explicit caller knowledge");
        Pc1Fetch.Dispatch fetched=dispatcher.next(frame);boolean blocked=false;try{save(runtime);}catch(IOException expected){blocked=expected.getMessage().contains("unresolved");}check(blocked,"Runtime cannot drop the pending fetch/handler continuation");
        Pc1RawDispatch.Result result=dispatcher.execute(frame,fetched);check(result.loop&&variables.read(0,0)==123&&bus.readInt32(Pc1Fetch.PC)==9,"native WORD fetch and cached-register handler update same variable/PC owners");
        byte[] snapshot=save(runtime);byte[] local=frame.rawBytes(),known=frame.knownMask();frame.writeByte(Pc1Fetch.Frame.BUFFER,'B');frame.setArgument2(55);variables.write(0,0,231);
        runtime.load(new DataInputStream(new ByteArrayInputStream(snapshot)));
        check(runtime.nativeDispatch()==dispatcher&&runtime.nativeDispatchFrame()==frame&&alias.unsignedByte(0)=='A',"restore preserves dispatcher/local Source identities without another stack address");
        check(Arrays.equals(local,frame.rawBytes())&&Arrays.equals(known,frame.knownMask())&&frame.argument2()==123&&variables.read(0,0)==123,"caller raw/known bytes and shared frame restore together");
        check(Arrays.equals(snapshot,save(runtime)),"whole Runtime with known-mask/register cache roundtrips exactly");
        result=dispatcher.step(frame);check(!result.loop&&result.nativeResult()==0&&runtime.waitKind()==14&&bus.readInt32(Pc1Fetch.LAST_OPCODE)==3,"restored READY phase continues into the native return with correct last-opcode publication");
        byte[] before=save(runtime);check(!dispatcher.begin(frame)&&Arrays.equals(before,save(runtime)),"WAIT14 direct return0 leaves preserved caller bytes and globals untouched");
        Pc1Runtime unbound=runtime();unbound.nativeFrame().writeU8(Pc1FrameLayout.ORDINARY_OFFSET,77);byte[] pristine=save(unbound);boolean required=false;try{unbound.load(new DataInputStream(new ByteArrayInputStream(snapshot)));}catch(IOException expected){required=expected.getMessage().contains("explicitly constructed dispatcher graph");}
        check(required&&Arrays.equals(pristine,save(unbound)),"missing native graph rejects saved locals before publishing any other Runtime owner");
        boolean twice=false;try{runtime.bindNativeDispatch(dispatcher,Pc1Fetch.Frame.unknown());}catch(IOException expected){twice=true;}check(twice,"one explicit native caller-local owner per bound Runtime");return checks;
    }
    public static void main(String[] args)throws Exception{System.out.println("PASS "+run()+" raw Runtime checks");}
}
