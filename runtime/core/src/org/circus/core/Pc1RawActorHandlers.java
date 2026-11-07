package org.circus.core;

import java.io.IOException;

/** Common414578/414670 actor producers over the same bus, caller locals and
 *render graph. Optional46 is borrowed at captured ESI without advancing PC. */
public final class Pc1RawActorHandlers implements Pc1RawDispatch.Leaves {
    public interface Calls {
        default void free401130(int handle)throws IOException{throw required(0x401130);}
        default void release411080(int slot,int mode)throws IOException{throw required(0x411080);}
        default int path4111A0(int slot,Pc1ByteSource source)throws IOException{throw required(0x4111a0);}
        default void messageWindow416C30(int mode)throws IOException{throw required(0x416c30);}
        default void speaker417C50(Pc1ByteSource source,int style,int mode)throws IOException{throw required(0x417c50);}
        default int createLike4020B0(int source)throws IOException{throw required(0x4020b0);}
        default void target409480(int handle)throws IOException{throw required(0x409480);}
        default void redraw41AF50(int mask,int l,int t,int r,int b)throws IOException{throw required(0x41af50);}
        default void invalidate407000()throws IOException{throw required(0x407000);}
        default void bake411D30()throws IOException{throw required(0x411d30);}
        /**Complete native result; an unfinished cooperative call is required.*/
        default int transition41A0B0(int handle,int argument)throws IOException{throw required(0x41a0b0);}
    }
    private static IOException required(int target){return new IOException("PC1 actor producer requires native target0x"+Integer.toHexString(target));}
    /** Reference State uses an explicit known byte/word contract. Production
     *passes NativePaths.memory(bus), retaining its canonical word seam. */
    public static final class Producer {
        private final Pc1NativePaths.Memory memory;private final Calls calls;
        public Producer(Pc1NativePaths.Memory memory,Calls calls)throws IOException{if(memory==null||calls==null)throw new IOException("PC1 actor producer requires native owners");this.memory=memory;this.calls=calls;}
        private int b(int at)throws IOException{return memory.read(at)&255;}private int g(int at)throws IOException{return memory.readInt32(at);}private void g(int at,int value)throws IOException{memory.writeInt32(at,value);}
        private Pc1ByteSource.Pointer global(int at)throws IOException{return Pc1NativePaths.pointer(memory,at);}
        private static int kind(Pc1ByteSource source,int first)throws IOException{return first==0?0:first=='-'&&(source.unsignedByte(1)&255)==0?-1:1;}
        private int second(int cursor)throws IOException{return b(Pc1Fetch.CODE_BASE+cursor)==0x46?Pc1Fetch.CODE_BASE+cursor+1:0x94bc30;}
        /**34 deliberately preserves the original second-normal-input access
         *to OVERLAY1, even though that branch writes the OVERLAY2 name. */
        public Pc1RawDispatch.Flow overlay34(int cursor,Pc1ByteSource first)throws IOException{
            int other=second(cursor),value=first.unsignedByte(0)&255;
            if(value!=0){if(value=='-'&&(first.unsignedByte(1)&255)==0){int handle=g(0x971770);memory.write(0x9d878c,0);calls.free401130(handle);g(0x971770,-1);}else{calls.free401130(g(0x971770));g(0x971770,-1);Pc1NativePaths.copy(global(0x9d878c),first);}}
            Pc1ByteSource source=global(other);value=source.unsignedByte(0)&255;if(value==0)return Pc1RawDispatch.Flow.COMMON_TAIL;
            if(value=='-'&&(source.unsignedByte(1)&255)==0){int handle=g(0x971768);memory.write(0x9d880c,0);calls.free401130(handle);g(0x971768,-1);}else{calls.free401130(g(0x971770));g(0x971770,-1);Pc1NativePaths.copy(global(0x9d880c),source);}return Pc1RawDispatch.Flow.COMMON_TAIL;
        }
        private boolean present(int handle)throws IOException{return handle!=-1&&g(Pc1ImagePool.BASE+(handle<<7))!=0;}
        private long count(Pc1ByteSource source)throws IOException{for(long n=0;n<0xffffffffL;n++)if((source.unsignedByte((int)n)&255)==0)return n+1;return 0xffffffffL;}
        /**416770 expands the existing rectangle using raw shape words. */
        private void bounds(int x,int y,int handle)throws IOException{
            if(g(0x9dbab0)>x)g(0x9dbab0,x);if(g(0x9dbab4)>y)g(0x9dbab4,y);int at=Pc1ImagePool.BASE+(handle<<7),right=x+g(at+12),bottom=y+g(at+16);if(g(0xbb06e0)<right)g(0xbb06e0,right);if(g(0xbb06dc)<bottom)g(0xbb06dc,bottom);
        }
        private int clearSpeakerAndReadVisibility()throws IOException{
            Pc1ByteSource source=global(0x94bc30);Pc1ByteSource.Pointer destination=global(Pc1TextProducer.SPEAKER);long count=count(source),at=0;for(;at+4<=count;at+=4)destination.writeWord((int)at,source.nativeWord((int)at));int visible=g(0xbb844c);for(;at<count;at++)destination.writeByte((int)at,source.unsignedByte((int)at));return visible;
        }
        public Pc1RawDispatch.Flow actor33(int cursor,Pc1ByteSource first,Pc1Fetch.Frame locals)throws IOException{
            int second=second(cursor);locals.dword(Pc1Fetch.Frame.LOCAL_POINTER,second);Pc1ByteSource source2=global(second);int kind1=kind(first,first.unsignedByte(0)&255),first2=source2.unsignedByte(0)&255;locals.dword(20,kind1);int kind2=kind(source2,first2),old1=g(0x9b9be4);locals.dword(24,kind2);boolean was1=present(old1),was2=present(g(0x9b9be8));locals.dword(Pc1Fetch.Frame.PREVIOUS,was2?1:0);
            if(kind1<0&&kind2<0&&!was1&&!was2)return Pc1RawDispatch.Flow.COMMON_TAIL;
            int visible=clearSpeakerAndReadVisibility();
            if(visible!=0&&g(0x9b9f18)==0){if(g(0x9da4ac)==0){calls.messageWindow416C30(0);int style=g(0x43d460);calls.speaker417C50(global(Pc1TextProducer.SPEAKER),style,0);calls.invalidate407000();g(0xbb844c,2);}else{int image=calls.createLike4020B0(0);g(0x43d42c,image);calls.target409480(image);calls.redraw41AF50(15,0,0,800,600);g(0xbb844c,1);calls.target409480(0);}}
            g(0x9dbab0,800);g(0x9dbab4,600);g(0xbb06e0,0);g(0xbb06dc,0);
            if(was1&&locals.dword(20)!=0){int y=g(0xbb8408)+g(0x9d96c8),handle=g(0x9b9be4),xOffset=g(0xbb8404),x=g(0x9d96c4)+xOffset;bounds(x,y,handle);}
            if(locals.dword(Pc1Fetch.Frame.PREVIOUS)==1&&locals.dword(24)!=0){int handle=g(0x9b9be8),yOffset=g(0xbb8408),y=g(0x9d96d0)+yOffset,xOffset=g(0xbb8404),x=g(0x9d96cc)+xOffset;bounds(x,y,handle);}
            int kind=locals.dword(20);if(kind==-1)calls.release411080(1,0);else if(kind==1){calls.path4111A0(1,first);Pc1NativePaths.copy(global(0x9d868c),first);}
            kind=locals.dword(24);if(kind==-1)calls.release411080(2,0);else if(kind==1){int currentSecond=locals.dword(Pc1Fetch.Frame.LOCAL_POINTER);Pc1ByteSource late=global(currentSecond);calls.path4111A0(2,late);Pc1NativePaths.copy(global(0x9d870c),late);}
            if(g(0x9b9f18)!=0){if(g(0x9b9be4)==-1&&g(0x9b9be8)==-1)return Pc1RawDispatch.Flow.COMMON_TAIL;calls.bake411D30();if(calls.transition41A0B0(g(0x43d418),1)!=0)return Pc1RawDispatch.Flow.RETURN_ONE_WITHOUT_TAIL;g(0xbb844c,0);}
            else if(g(0xbb844c)!=0&&g(0x9da4ac)!=0){if(calls.transition41A0B0(g(0x43d42c),1)!=0)return Pc1RawDispatch.Flow.RETURN_ONE_WITHOUT_TAIL;calls.free401130(g(0x43d42c));g(0x43d42c,-1);g(0xbb844c,0);}
            int x2=g(0x9d96dc),x1=g(0x9d96d4),y1=g(0x9d96d8);g(0x9d96cc,x2);int y2=g(0x9d96e0);g(0x9d96c4,x1);g(0x9d96d0,y2);g(0x9d96c8,y1);
            if(locals.dword(20)==1){int handle=g(0x9b9be4);if(present(handle)){int yOffset=g(0xbb8408),xOffset=g(0xbb8404);bounds(x1+xOffset,y1+yOffset,handle);}}
            if(locals.dword(24)==1){int handle=g(0x9b9be8);if(present(handle)){int y=g(0x9d96d0),xOffset=g(0xbb8404),yOffset=g(0xbb8408),x=g(0x9d96cc);bounds(x+xOffset,y+yOffset,handle);}}
            if(g(0x9dbab0)<0)g(0x9dbab0,0);if(g(0xbb06e0)>800)g(0xbb06e0,800);if(g(0x9dbab4)<0)g(0x9dbab4,0);if(g(0xbb06dc)>600)g(0xbb06dc,600);g(Pc1Fetch.WAIT,0x33);return Pc1RawDispatch.Flow.COMMON_TAIL;
        }
    }
    private final Pc1Runtime runtime;private final Pc1GraphicsPipeline graphics;private final Pc1RawDispatch.Leaves other;private final Producer producer;private int depth;
    public Pc1RawActorHandlers(Pc1Runtime runtime,Pc1GraphicsPipeline graphics,Pc1RawDispatch.Leaves other)throws IOException{
        if(runtime==null||graphics==null||graphics.runtime()!=runtime)throw new IOException("PC1 raw actor handlers require canonical graph");this.runtime=runtime;this.graphics=graphics;this.other=other==null?new Pc1RawDispatch.Leaves(){}:other;
        producer=new Producer(Pc1NativePaths.memory(runtime.nativeMemory()),new Calls(){
            public void free401130(int handle)throws IOException{graphics.render().compositor().freeChecked(handle);}
            public void release411080(int slot,int mode)throws IOException{graphics.render().resources().releaseNative(slot,mode);}
            public int path4111A0(int slot,Pc1ByteSource source)throws IOException{return graphics.render().resources().pathSource(slot,source);}
            public void messageWindow416C30(int mode)throws IOException{graphics.render().messageWindow();}
            public void speaker417C50(Pc1ByteSource source,int style,int mode)throws IOException{if(runtime.nativeMemory().readInt32(0x9da4ac)!=0)return;graphics.render().restoreName(0,0,800,600,1);if((source.unsignedByte(0)&255)!=0)throw new IOException("PC1 actor nonempty417C50 requires complete speaker consumer");}
            public int createLike4020B0(int source)throws IOException{return graphics.render().resources().createLike(source);}
            public void target409480(int handle)throws IOException{graphics.render().compositor().target(handle);}
            public void redraw41AF50(int mask,int l,int t,int r,int b)throws IOException{graphics.render().generalRedraw(mask,l,t,r,b);}
            public void invalidate407000()throws IOException{graphics.render().compositor().present();}
            public void bake411D30()throws IOException{graphics.render().bakePending();}
            public int transition41A0B0(int handle,int argument)throws IOException{return Pc1RawActorHandlers.this.other.transition41A0B0(handle,argument);}
        });runtime.bindNativeBoundary(this::requireBoundary);
    }
    public void requireBoundary()throws IOException{if(depth!=0)throw new IOException("PC1 raw actor snapshot during native consumer");graphics.requireBoundary();}
    public void releaseGraphics411080(int slot,int mode)throws IOException{other.releaseGraphics411080(slot,mode);}public void error411050(int raw)throws IOException{other.error411050(raw);}public int transition41A0B0(int handle,int argument)throws IOException{return other.transition41A0B0(handle,argument);}
    public Pc1RawDispatch.Flow handler(int target,Pc1RawDispatch.Registers registers,Pc1Fetch.Frame locals)throws IOException{
        if(target!=0x414578&&target!=0x414670)return other.handler(target,registers,locals);depth++;try{final Pc1RawDispatch.Flow[] flow={null};graphics.render().nativeCall(()->{Pc1ByteSource source=Pc1ByteSource.local(registers.source());flow[0]=target==0x414578?producer.overlay34(registers.codeCursor,source):producer.actor33(registers.codeCursor,source,locals);});return flow[0];}finally{depth--;}
    }
}
