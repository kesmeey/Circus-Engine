package org.circus.core;

import java.io.*;
import java.lang.reflect.Proxy;
import java.util.*;

/** Fetch-to-pool integration with explicit synthetic PE table ownership. */
public final class Pc1RawGraphicsHandlersTests {
    private static int checks;
    private static void check(boolean value,String why){checks++;if(!value)throw new AssertionError(why);}
    private static void own(Pc1NativeMemory bus,int address,int size)throws IOException{byte[] raw=new byte[size];try{bus.bind(address,size,new Pc1NativeMemory.Region(){public int read(int at){return raw[at]&255;}public void write(int at,int value){raw[at]=(byte)value;}});}catch(IOException failure){throw new IOException("explicit fixture owner0x"+Integer.toHexString(address),failure);}}
    private static byte[] words(int op,int a,int b,int c,int d){byte[] bytes=new byte[9];bytes[0]=(byte)op;int[] args={a,b,c,d};for(int n=0;n<4;n++){bytes[n*2+1]=(byte)args[n];bytes[n*2+2]=(byte)(args[n]>>>8);}return bytes;}
    private static byte[] cstring(Pc1ByteSource source)throws IOException{ByteArrayOutputStream out=new ByteArrayOutputStream();for(int n=0;;n++){int b=source.unsignedByte(n)&255;if(b==0)return out.toByteArray();out.write(b);}}
    public static int run()throws Exception{
        checks=0;Pc1RenderContextTests.Font font=new Pc1RenderContextTests.Font();Pc1RenderContextTests.Fixture fixture=new Pc1RenderContextTests.Fixture(font,null);Pc1Runtime runtime=fixture.host.pc1;Pc1NativeMemory bus=runtime.nativeMemory();Pc1Fetch.Frame frame=Pc1Fetch.Frame.unknown();final List<String> opened=new ArrayList<>();final boolean[] fenced={false};
        Pc1GraphicsResources.Backend images=new Pc1GraphicsResources.Backend(){
            public void visitCg(byte[] name){}
            public Pc1GraphicsResources.Source readImage(byte[] category,byte[] name,byte[] extension)throws IOException{throw new IOException("typed image boundary required in this fixture");}
            public Pc1GraphicsResources.Source readImage(Pc1GraphicsResources.AddressedImage request)throws IOException{
                String name=new String(cstring(request.name),"US-ASCII");opened.add(name);try{fixture.runtimeBytes();}catch(IOException expected){fenced[0]=true;}
                if(name.equals("BG\\OLD")){byte[] replacement="BG\\NEW".getBytes("US-ASCII");for(int n=0;n<replacement.length;n++)frame.writeByte(Pc1Fetch.Frame.BUFFER+n,replacement[n]);}
                return Pc1GraphicsResources.Source.rgb(Pc1RenderContextTests.solid(8,8,0x203040));
            }
        };
        Pc1GraphicsPipeline.Services drawing=(Pc1GraphicsPipeline.Services)Proxy.newProxyInstance(Pc1GraphicsPipeline.Services.class.getClassLoader(),new Class<?>[]{Pc1GraphicsPipeline.Services.class},(p,m,a)->{throw new IOException("controlled required graphics leaf "+m.getName());});
        Pc1GraphicsPipeline graphics=new Pc1GraphicsPipeline(runtime,new Pc1Compositor(Pc1RenderContextTests.solid(64,48,0x112233)),Pc1GlyphCache.zeroBss(),font,images,drawing);fixture.context=graphics.render();fixture.initializeKnownScene();
        own(bus,0x43d478,48);own(bus,0x43d8dc,4);own(bus,0x43d0c0,16);own(bus,0xe1000020,4);bus.writeInt32(0x43d470,0);bus.writeInt32(Pc1CgVisit.CRT_LOCALE,0);
        int[] paths={0x9d860c,0x9d868c,0x9d870c,0x9d888c,0x9d890c,0x9d898c},handles={0x43d418,0x9b9be4,0x9b9be8,0x9cca90,0x9cca98,0x9cca94};for(int n=0;n<6;n++){bus.writeInt32(0x43d478+n*8,paths[n]);bus.writeInt32(0x43d47c+n*8,handles[n]);}
        bus.writeInt32(0x43d0c0,0xe1000020);bus.writeInt32(0x43d0c4,77);for(int n=0;n<3;n++)bus.writeAddress(0xe1000020+n,"NEW".charAt(n));
        Pc1RawGraphicsHandlers handlers=new Pc1RawGraphicsHandlers(runtime,graphics,null);Pc1Variables variables=new Pc1Variables(bus,null);runtime.bindNativeVariables(variables);Pc1RawDispatch dispatcher=new Pc1RawDispatch(bus,variables,handlers);runtime.bindNativeDispatch(dispatcher,frame);
        final byte[] table=new byte[99*4];for(int n=0;n<99;n++){int target=n==0x60?0x414a81:n==0x2b?0x4139d4:n==0x2f?0x41426f:n==0x2e?0x413c7d:n==0x0e?0x412817:Pc1RawDispatch.TAIL;for(int k=0;k<4;k++)table[n*4+k]=(byte)(target>>>(k*8));}bus.bind(Pc1RawDispatch.TABLE,table.length,new Pc1NativeMemory.Region(){public int read(int at){return table[at]&255;}public void write(int at,int value){table[at]=(byte)value;}});
        ByteArrayOutputStream code=new ByteArrayOutputStream();code.write(words(0x60,1,2,3,4));code.write(new byte[]{0x2b,(byte)255,7,0x2f,'T','O','P',0,0x2f,0,0x2e,'B','G','\\','O','L','D',0,0x0e,0,0});runtime.codeBuffer().writeBytes(0,code.toByteArray());bus.writeInt32(Pc1Fetch.PC,0);check(dispatcher.begin(frame),"raw graphics invocation enters canonical graph");
        Pc1Fetch.Dispatch fetched=dispatcher.next(frame);bus.writeInt32(Pc1Fetch.ARGUMENT0,101);frame.setArgument2(103);frame.setArgument3(104);Pc1RawDispatch.Result result=dispatcher.execute(frame,fetched);
        check(result.loop&&bus.readInt32(0x9d96d4)==1&&bus.readInt32(0x9d96d8)==2&&bus.readInt32(0x9d96dc)==3&&bus.readInt32(0x9d96e0)==4,"60 preserves all four captured native registers");
        result=dispatcher.step(frame);check(result.loop&&bus.readInt32(Pc1Effects.BUSY)==7,"2B FF publishes busy value without inventing effect initialization");
        result=dispatcher.step(frame);int top=bus.readInt32(handles[3]);check(result.loop&&top==20&&graphics.render().compositor().loaded(top)&&bus.readAddress(paths[3])=='T',"2F borrows Frame Source through native pointer table into same image pool");
        result=dispatcher.step(frame);check(result.loop&&bus.readInt32(handles[3])==-1&&bus.readAddress(paths[3])==0&&!graphics.render().compositor().loaded(top),"empty2F releases actual slot and path via411080");
        result=dispatcher.step(frame);int background=bus.readInt32(handles[0]);check(result.loop&&background==20&&graphics.render().compositor().loaded(background),"2E loads actual background into canonical resource slot");
        check(Arrays.equals(cstring(Pc1ByteSource.addressed(bus,paths[0])),"BG\\OLD".getBytes("US-ASCII"))&&Arrays.equals(graphics.render().resources().name(background),"BG\\OLD".getBytes("US-ASCII")),"native path local retains pre-open name despite changed caller Frame");
        check(bus.readInt32(0x43d470)==77&&bus.readInt32(0xbb8548)==60,"410FF0 lookup reads late original Frame Source and live native table");
        check(bus.readInt32(0x9b9f18)==1&&bus.readInt32(0xbb844c)==0&&bus.readInt32(0x9d96bc)==0&&bus.readInt32(0x9d96c0)==0,"background publishes native pending/visibility/origin prefix");
        check(opened.equals(Arrays.asList("TOP","BG\\OLD"))&&fenced[0],"typed resource callbacks run once per admitted image within snapshot fence");
        result=dispatcher.step(frame);check(!result.loop&&result.nativeResult()==0&&bus.readInt32(Pc1Fetch.WAIT)==14,"raw graphics returns only through real common tail");handlers.requireBoundary();
        // Explicit C-locale fixture, not a production CRT startup fallback.
        own(bus,0x4405cc,4);own(bus,0x4403c0,4);own(bus,0xe1000100,512);bus.writeInt32(0x4405cc,1);bus.writeInt32(0x4403c0,0xe1000100);for(int digit='0';digit<='9';digit++)bus.writeAddress(0xe1000100+digit*2,4);
        byte[] path="BG\\OLD\0".getBytes("US-ASCII");for(int n=0;n<path.length;n++){bus.writeAddress(0x9bb508+n,path[n]);bus.writeAddress(0x9da2a4+128+n,path[n]);}
        for(int prefix:new int[]{'*','?'}){
            int index=prefix=='*'?0:1;runtime.codeBuffer().writeBytes(0,new byte[]{0x2e,(byte)prefix,(byte)('0'+index),0,0x0e,0,0});bus.writeInt32(Pc1Fetch.PC,0);bus.writeInt32(Pc1Fetch.WAIT,-1);frame.setArgument2(111);frame.setArgument3(222);dispatcher.begin(frame);result=dispatcher.step(frame);
            check(result.loop&&frame.argument2()==index&&frame.argument3()==222,"indexed2E writes actual ARG2 after atoi pending argument; ARG3 survives prefix"+(char)prefix);
            check(bus.readInt32(handles[0])==background&&opened.size()==2,"indexed2E borrows actual CG/resource table row and hits current native path");dispatcher.step(frame);
        }
        return checks;
    }
    public static void main(String[] args)throws Exception{System.out.println("PASS "+run()+" raw graphics graph checks");}
}
