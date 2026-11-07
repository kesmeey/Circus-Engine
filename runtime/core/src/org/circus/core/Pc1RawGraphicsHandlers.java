package org.circus.core;

import java.io.IOException;

/** Common raw graphics targets over the existing pixel pool and live paths. */
public final class Pc1RawGraphicsHandlers implements Pc1RawDispatch.Leaves {
    private final Pc1Runtime runtime;
    private final Pc1GraphicsPipeline graphics;
    private final Pc1TextExpander.LinkedCrt crt;
    private final Pc1RawDispatch.Leaves other;
    private int depth;
    public Pc1RawGraphicsHandlers(Pc1Runtime runtime,Pc1GraphicsPipeline graphics,Pc1RawDispatch.Leaves other)throws IOException{
        if(runtime==null||graphics==null||graphics.runtime()!=runtime)throw new IOException("PC1 raw graphics handlers require the canonical scene graph");this.runtime=runtime;this.graphics=graphics;crt=new Pc1TextExpander.LinkedCrt(runtime.nativeMemory());this.other=other==null?new Pc1RawDispatch.Leaves(){}:other;runtime.bindNativeBoundary(this::requireBoundary);
    }
    public void requireBoundary()throws IOException{if(depth!=0)throw new IOException("PC1 raw graphics snapshot during native consumer");graphics.requireBoundary();}
    private interface Work{void run()throws IOException;}
    private void call(Work work)throws IOException{depth++;try{graphics.render().nativeCall(work::run);}finally{depth--;}}
    public void releaseGraphics411080(int slot,int mode)throws IOException{call(()->graphics.render().resources().releaseNative(slot,mode));}
    public void error411050(int raw)throws IOException{other.error411050(raw);}
    public int transition41A0B0(int handle,int argument)throws IOException{return other.transition41A0B0(handle,argument);}
    private int g(int at)throws IOException{return runtime.nativeMemory().readInt32(at);}
    private void g(int at,int value)throws IOException{runtime.nativeMemory().writeInt32(at,value);}
    private static boolean equal(Pc1ByteSource left,Pc1ByteSource right)throws IOException{for(int at=0;;at+=2){int a=left.unsignedByte(at)&255,b=right.unsignedByte(at)&255;if(a!=b)return false;if(a==0)return true;a=left.unsignedByte(at+1)&255;b=right.unsignedByte(at+1)&255;if(a!=b)return false;if(a==0)return true;}}
    private Pc1ByteSource.Addressed global(int at)throws IOException{return Pc1ByteSource.addressed(runtime.nativeMemory(),at);}
    private void background(Pc1ByteSource source,Pc1Fetch.Frame frame)throws IOException{
        Pc1GraphicsResources images=graphics.render().resources();int first=source.unsignedByte(0)&255;g(0x9d96bc,0);g(0x9d96c0,0);
        if(first=='*'||first=='?'){
            Pc1ByteSource indexSource=Pc1ByteSource.offset(source,1);int index=crt.atoi420BDA(indexSource::unsignedByte);frame.setArgument2(index);images.pathSource(0,global((first=='*'?0x9bb508:0x9da2a4)+(index<<7)));
        }else if(first=='-'&&(source.unsignedByte(1)&255)=='-'){
            images.pathSource(0,global(0x9d5ecc));int x=g(0x9d6f7c),y=g(0x9d6f80);g(0x9d96bc,x);g(0x9d96c0,y);
        }else{
            if(equal(source,global(0x9d860c)))g(0x43d470,-2);images.pathSource(0,source);
        }
        int hint=g(0x43d470);g(0x9d96e4,0);g(0xbb8540,0);g(0xbb8460,0);g(0xbb8464,0);g(0x9b9f18,1);g(0xbb844c,0);
        if(hint==-2)g(0x43d470,-1);else{g(0x43d470,images.lookupSource(source));g(0xbb8548,60);}
    }
    public Pc1RawDispatch.Flow handler(int target,Pc1RawDispatch.Registers registers,Pc1Fetch.Frame frame)throws IOException{
        final Pc1RawDispatch.Flow[] flow={Pc1RawDispatch.Flow.COMMON_TAIL};call(()->{
            Pc1GraphicsResources images=graphics.render().resources();Pc1ByteSource source=Pc1ByteSource.local(registers.source());
            switch(target){
            case 0x413c7d:background(source,frame);break;
            case 0x41426f:case 0x41429c:case 0x4142c9:
                int slot=target==0x41426f?3:target==0x41429c?4:5;
                if((source.unsignedByte(0)&255)!=0)images.pathSource(slot,source);else images.releaseNative(slot,0);break;
            case 0x4139d4:graphics.commandEffect(registers.arg0,registers.arg1);break;
            case 0x414a81:g(0x9d96d4,registers.arg0);g(0x9d96d8,registers.arg1);g(0x9d96dc,registers.argument2());g(0x9d96e0,registers.argument3());break;
            default:flow[0]=other.handler(target,registers,frame);break;
            }
        });return flow[0];
    }
}
