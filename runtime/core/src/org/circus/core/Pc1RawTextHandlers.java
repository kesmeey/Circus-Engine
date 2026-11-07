package org.circus.core;

import java.io.IOException;

/** Exact text target entrypoints consume fetch's already-decoded live locals.
 *The expander and each rendering/audio callback share the canonical graph. */
public final class Pc1RawTextHandlers implements Pc1RawDispatch.Leaves {
    private final Pc1GraphicsPipeline graphics;
    private final Pc1TextExpander.Live expansion;
    private final Pc1TextProducer producer;
    private final Pc1RawDispatch.Leaves other;
    private int depth;
    public Pc1RawTextHandlers(Pc1Runtime runtime,Pc1GraphicsPipeline graphics,Pc1Audio audio,Pc1TextServices text,Pc1TextExpander.Live expansion,Pc1RawDispatch.Leaves other)throws IOException{
        if(runtime==null||graphics==null||graphics.runtime()!=runtime||audio==null||text==null||expansion==null)throw new IOException("PC1 raw text handlers require the canonical native graph");
        text.requireGraph(runtime,graphics,audio);audio.requireBus(runtime.nativeMemory());expansion.requireBus(runtime.nativeMemory());
        if(runtime.nativeTextExpansion()==null)runtime.bindNativeTextExpansion(expansion);else if(runtime.nativeTextExpansion()!=expansion)throw new IOException("PC1 raw text handler requires the Runtime's canonical expansion");
        this.graphics=graphics;this.expansion=expansion;this.other=other==null?new Pc1RawDispatch.Leaves(){}:other;
        producer=new Pc1TextProducer(runtime.nativeMemory(),new Pc1TextProducer.Services(){
            public void expand4123A0(Pc1GlyphCache.Source source,int destination)throws IOException{expansion.expand(source,destination);}
            public void release411080(int slot,int mode)throws IOException{graphics.render().resources().releaseNative(slot,mode);}
            public void bake411D30()throws IOException{text.bakePending();}
            public int transition41A0B0(int target,int argument)throws IOException{
                Pc1TextPresentation.Transition result=text.beginTransition(target,argument);
                if(result==Pc1TextPresentation.Transition.COMPLETE)return 0;
                if(result==Pc1TextPresentation.Transition.INTERRUPTED)return 1;
                throw new IOException("PC1 raw text requires the completed native41A0B0 call");
            }
            public int skip4118B0()throws IOException{return text.skipRequested()?1:0;}
            public void stopVoice41CBE0()throws IOException{text.stopVoices();}
            public void restoreControls412260(int mode)throws IOException{text.restoreControls(mode);}
            public void activate412320()throws IOException{text.activateTextRegions();}
            public void redraw418220(int selection)throws IOException{text.redrawText(selection);}
        });
        runtime.bindNativeBoundary(this::requireBoundary);
    }
    public void requireBoundary()throws IOException{if(depth!=0)throw new IOException("PC1 raw text snapshot during native consumer");producer.requireBoundary();expansion.requireBoundary();}
    public void releaseGraphics411080(int selector,int mode)throws IOException{other.releaseGraphics411080(selector,mode);}
    public void error411050(int raw)throws IOException{other.error411050(raw);}
    public int transition41A0B0(int target,int argument)throws IOException{return other.transition41A0B0(target,argument);}
    public Pc1RawDispatch.Flow handler(int target,Pc1RawDispatch.Registers registers,Pc1Fetch.Frame frame)throws IOException{
        final Pc1RawDispatch.Flow[] result={Pc1RawDispatch.Flow.COMMON_TAIL};depth++;
        try{graphics.render().nativeCall(()->{
            Pc1TextProducer.Flow flow;
            switch(target){
            case 0x415681:flow=producer.speaker(registers.source());break;
            case 0x415695:flow=producer.body(frame::previousOpcode,registers.source());break;
            case 0x412898:flow=producer.wait07(registers.arg0);break;
            case 0x413413:flow=producer.wait09(registers.device);break;
            default:result[0]=other.handler(target,registers,frame);return;
            }
            result[0]=flow==Pc1TextProducer.Flow.COMMON_TAIL?Pc1RawDispatch.Flow.COMMON_TAIL:Pc1RawDispatch.Flow.RETURN_ONE_WITHOUT_TAIL;
        });return result[0];}finally{depth--;}
    }
}
