package org.circus.core;

import java.io.IOException;

/** Current CHS415681/415695/412898/413413. Sources are the already-decoded
 *live caller locals, not a logical CString or a second K/L decoder. */
public final class Pc1TextProducer {
    public static final int SPEAKER=0x9ccaa0,BODY=0x9d3af8;
    public enum Flow { COMMON_TAIL,RETURN_ONE_WITHOUT_TAIL }
    public interface IntValue {int get()throws IOException;}
    public interface State {
        int read(int address)throws IOException;void write(int address,int value)throws IOException;
        default int readInt32(int address)throws IOException{int v=0;for(int n=0;n<4;n++)v|=(read(address+n)&255)<<(n*8);return v;}
        default void writeInt32(int address,int value)throws IOException{for(int n=0;n<4;n++)write(address+n,value>>>(n*8));}
        default void writeInt16(int address,int value)throws IOException{write(address,value&255);write(address+1,value>>>8&255);}
    }
    public interface Services {
        default void expand4123A0(Pc1GlyphCache.Source source,int destination)throws IOException{throw required(0x4123a0);}
        default void release411080(int slot,int mode)throws IOException{throw required(0x411080);}
        default void bake411D30()throws IOException{throw required(0x411d30);}
        /**The complete native call: 0 complete, nonzero direct return1.
         *A pending cooperative transition is not a made-up completed result.*/
        default int transition41A0B0(int target,int argument)throws IOException{throw required(0x41a0b0);}
        default int skip4118B0()throws IOException{throw required(0x4118b0);}
        default void stopVoice41CBE0()throws IOException{throw required(0x41cbe0);}
        default void restoreControls412260(int mode)throws IOException{throw required(0x412260);}
        default void activate412320()throws IOException{throw required(0x412320);}
        default void redraw418220(int selection)throws IOException{throw required(0x418220);}
    }
    private static IOException required(int target){return new IOException("PC1 text producer required native leaf0x"+Integer.toHexString(target));}
    private final State memory;private final Services services;private Pc1NativeMemory bus;private int depth;
    public Pc1TextProducer(State memory,Services services)throws IOException{if(memory==null)throw new IOException("PC1 text producer requires canonical memory");this.memory=memory;this.services=services==null?new Services(){}:services;}
    public Pc1TextProducer(final Pc1NativeMemory memory,Services services)throws IOException{this(memory==null?null:new State(){public int read(int at)throws IOException{return memory.readAddress(at);}public void write(int at,int value)throws IOException{memory.writeAddress(at,value);}public int readInt32(int at)throws IOException{return memory.readInt32(at);}public void writeInt32(int at,int value)throws IOException{memory.writeInt32(at,value);}},services);bus=memory;}
    public void requireBus(Pc1NativeMemory memory)throws IOException{if(memory==null||memory!=bus)throw new IOException("PC1 text producer requires its canonical bus");}
    public void requireBoundary()throws IOException{if(depth!=0)throw new IOException("PC1 text producer snapshot during native caller/leaf");}
    private int g(int at)throws IOException{return memory.readInt32(at);}private void g(int at,int v)throws IOException{memory.writeInt32(at,v);}
    private static void source(Pc1GlyphCache.Source source)throws IOException{if(source==null)throw new IOException("PC1 text producer requires live local source");}
    public Flow speaker(Pc1GlyphCache.Source source)throws IOException{source(source);depth++;try{services.expand4123A0(source,SPEAKER);return Flow.COMMON_TAIL;}finally{depth--;}}
    public Flow body(IntValue previousOpcode,Pc1GlyphCache.Source source)throws IOException{
        if(previousOpcode==null)throw new IOException("PC1 body requires caller previous opcode");source(source);depth++;
        try{
            if(previousOpcode.get()!=0x4b)memory.write(SPEAKER,0);
            services.expand4123A0(source,BODY);int pending=g(0x9b9f18);
            g(0x43d410,-1);g(0x43d434,0);g(0xbb84e8,0);
            if(pending!=0){services.release411080(1,0);services.release411080(2,0);services.bake411D30();int target=g(0x43d418);if(services.transition41A0B0(target,1)!=0)return Flow.RETURN_ONE_WITHOUT_TAIL;g(0xbb844c,0);}
            g(0x9d8a20,0x4c);return Flow.COMMON_TAIL;
        }finally{depth--;}
    }
    private int bodyLength()throws IOException{for(long n=0;n<0xffffffffL;n++)if((memory.read(BODY+(int)n)&255)==0)return(int)n;return -2;}
    /**07 uses the fetched EDX value, unrestricted when a mutable jump table
     *redirects a WORD/string record to this target. It does not append log.*/
    public Flow wait07(int cachedArgument0)throws IOException{depth++;try{
        g(0xbb84e4,0);int word=0;if(cachedArgument0==1&&g(0x9da4ac)!=0){g(0xbb84e4,1);word=0x8000;}
        int count=g(0x9d8a1c);memory.writeInt16(0x9d8a24+count*2,word);g(0x9cca8c,0);
        if(g(0x43d414)!=0x4c)return Flow.COMMON_TAIL;
        g(0x43d410,-1);g(0x94fa28,-1);
        if(services.skip4118B0()!=0){services.stopVoice41CBE0();return Flow.COMMON_TAIL;}
        g(0x9d8a20,7);services.restoreControls412260(1);services.activate412320();int mode=g(0x454cdc),remaining=0;
        if(mode==2)remaining=bodyLength();else if(mode==3){int twice=bodyLength()<<1;remaining=Integer.divideUnsigned(twice,3);}
        g(0x9da4a4,remaining);return Flow.COMMON_TAIL;
    }finally{depth--;}}
    /**09 consumes ECX captured by fetch, not a fresh BUTTON_COUNT read.*/
    public Flow wait09(int cachedDevice)throws IOException{depth++;try{g(0x94c6a4,0);g(0x9d8a20,9);g(0x9b8e1c,cachedDevice);services.redraw418220(-1);return Flow.COMMON_TAIL;}finally{depth--;}}
}
