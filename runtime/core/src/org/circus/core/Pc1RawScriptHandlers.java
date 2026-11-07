package org.circus.core;

import java.io.IOException;

/** Raw script-control targets consume the caller's actual local source.
 *There is no source reparse, temporary Guest VA or early name snapshot. */
public final class Pc1RawScriptHandlers implements Pc1RawDispatch.Leaves {
    private final Pc1Runtime runtime;
    private final Pc1GraphicsPipeline graphics;
    private final Pc1ScriptFlow scripts;
    private final Pc1RawDispatch.Leaves other;
    private int depth;
    public Pc1RawScriptHandlers(Pc1Runtime runtime,Pc1GraphicsPipeline graphics,Pc1Audio audio,Pc1ScriptFlow scripts,Pc1RawDispatch.Leaves other)throws IOException{
        if(runtime==null||graphics==null||graphics.runtime()!=runtime||audio==null||scripts==null)throw new IOException("PC1 raw script handlers require the complete canonical graph");scripts.requireGraph(runtime,graphics,audio);this.runtime=runtime;this.graphics=graphics;this.scripts=scripts;this.other=other==null?new Pc1RawDispatch.Leaves(){}:other;runtime.bindNativeBoundary(this::requireBoundary);
    }
    public void requireBoundary()throws IOException{if(depth!=0)throw new IOException("PC1 raw script handler snapshot during native consumer");scripts.requireBoundary();}
    private interface Work {void run()throws IOException;}
    private void call(Work work)throws IOException{depth++;try{graphics.render().nativeCall(work::run);}finally{depth--;}}
    public void releaseGraphics411080(int selector,int mode)throws IOException{other.releaseGraphics411080(selector,mode);}
    public void error411050(int raw)throws IOException{other.error411050(raw);}
    public int transition41A0B0(int handle,int argument)throws IOException{return other.transition41A0B0(handle,argument);}
    public Pc1RawDispatch.Flow handler(int target,Pc1RawDispatch.Registers registers,Pc1Fetch.Frame frame)throws IOException{
        final Pc1RawDispatch.Flow[] flow={Pc1RawDispatch.Flow.COMMON_TAIL};call(()->{
            Pc1ByteSource source=Pc1ByteSource.local(registers.source());Pc1NativeMemory bus=runtime.nativeMemory();
            switch(target){
            case 0x41529d:Pc1NativePaths.copy(Pc1ByteSource.addressed(bus,Pc1ScriptMemory.PENDING_LABEL),source);break;
            case 0x414f1e:Pc1NativePaths.copy(Pc1ByteSource.addressed(bus,0x9d95dc),source);break;
            case 0x41532d:scripts.loadScript(source,2);break;
            case 0x414f70:scripts.jumpLabel(source);break;
            case 0x4152c6:
                int cursor=Pc1Fetch.CODE_BASE+registers.codeCursor;
                if(bus.readAddress(cursor)==2&&bus.readAddress(cursor+1)==1)bus.writeInt32(Pc1Fetch.ARGUMENT0,3);
                else{other.releaseGraphics411080(-1,0);bus.writeInt32(Pc1Fetch.ARGUMENT0,1);}
                bus.writeInt32(Pc1Fetch.WAIT,0x36);scripts.loader().copyDeferredPath(source);break;
            default:flow[0]=other.handler(target,registers,frame);break;
            }
        });return flow[0];
    }
}
