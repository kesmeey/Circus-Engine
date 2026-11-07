package org.circus.core;

import java.io.IOException;

/**Machine target routing into the canonical UI producers. Fetch register
 *values, late native globals and late caller locals remain distinct inputs. */
public final class Pc1RawUiHandlers implements Pc1RawDispatch.Leaves {
    private final Pc1Runtime runtime;
    private final Pc1GraphicsPipeline graphics;
    private final Pc1UiServices ui;
    private final Pc1Transition transition;
    private final Pc1RawDispatch.Leaves other;
    private int depth;
    public Pc1RawUiHandlers(Pc1Runtime runtime,Pc1GraphicsPipeline graphics,Pc1UiServices ui,Pc1Transition transition,Pc1RawDispatch.Leaves other)throws IOException{
        if(runtime==null||graphics==null||graphics.runtime()!=runtime||ui==null||transition==null)throw new IOException("PC1 raw UI handlers require their existing native scene graph");
        ui.requireGraph(runtime,graphics);transition.requireGraph(graphics);this.runtime=runtime;this.graphics=graphics;this.ui=ui;this.transition=transition;this.other=other==null?new Pc1RawDispatch.Leaves(){}:other;runtime.bindNativeBoundary(this::requireBoundary);
    }
    public void requireBoundary()throws IOException{if(depth!=0)throw new IOException("PC1 raw UI handler snapshot during native consumer");ui.requireBoundary();transition.requireBoundary();}
    private interface Work {void run()throws IOException;}
    private void call(Work work)throws IOException{depth++;try{graphics.render().nativeCall(work::run);}finally{depth--;}}
    public void releaseGraphics411080(int selector,int mode)throws IOException{call(()->graphics.render().resources().releaseNative(selector,mode));}
    public void error411050(int rawMessage)throws IOException{call(()->other.error411050(rawMessage));}
    public int transition41A0B0(int handle,int argument)throws IOException{final int[] result={0};call(()->result[0]=transition.begin(handle,argument)==Pc1TextPresentation.Transition.COMPLETE?0:1);return result[0];}
    public Pc1RawDispatch.Flow handler(int target,Pc1RawDispatch.Registers r,Pc1Fetch.Frame locals)throws IOException{
        final Pc1RawDispatch.Flow[] result={Pc1RawDispatch.Flow.COMMON_TAIL};call(()->{
            Pc1UiProducer producer=runtime.uiProducer();
            switch(target){
            case 0x412af5:producer.template17(r.arg0,r.device);break;
            case 0x41363e:producer.sealGrid43();break;
            case 0x412d7d:producer.rectangle4f(r.arg0,r.arg1,r::argument3,locals);break;
            case 0x412e28:producer.sprite50(r.codeCursor,r::argument2,r::argument3);break;
            case 0x412fa1:producer.text51(r.codeCursor,r.arg0,r.arg1,r::argument2,r::argument3);break;
            case 0x413478:producer.grid54(r.codeCursor,r.arg0,r.arg1,r::argument2,r::argument3);break;
            case 0x41355f:producer.gridRect55(r.arg0,r.arg1,r::argument2,r::argument3);break;
            case 0x41358e:producer.gridItem56(r.arg0,r::argument3,locals::argument2);break;
            case 0x413661:producer.gridCaption57(r.codeCursor,r.arg0,r.arg1,r::argument2,(source,destination)->{if(runtime.nativeTextExpansion()==null)throw new IOException("PC1 raw grid caption requires canonical live4123A0 expansion");runtime.expandNativeUi(source,destination);});break;
            case 0x4136c7:producer.gridCaptionBounds5f(r.arg0,r.arg1,r::argument2,r::argument3);break;
            case 0x414ef5:ui.modal().position1a(r.arg0,r.arg1);break;
            case 0x414ed9:ui.modal().opcode44(r.source());break;
            default:result[0]=other.handler(target,r,locals);break;
            }
        });return result[0];
    }
}
