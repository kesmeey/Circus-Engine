package org.circus.core;

import java.io.*;

/** Complete current CHS41A0B0 control flow. A suspended frame owns only its
 *native locals; globals, image pool and platform queue remain borrowed owners.
 *Each step performs one405710 call, never substitutes a Java elapsed clock. */
public final class Pc1Transition implements Pc1TextServices.Transition {
    public static final int BUSY=0xbb8434,LOOP=0x94c698,SPEED=0x454cf0,
        RAPID=0xbb842c,DELAY=0x430284,PROGRESS=0x9cca8c,
        BACKGROUND=0x43d418,X=0x9d96bc,Y=0x9d96c0,NAME=0x9d860c;
    private static final int FORMAT=0x50315446;
    public interface Services {
        int read(int address)throws IOException;
        void write(int address,int value)throws IOException;
        void fullClip()throws IOException;
        void fillBlack()throws IOException;
        void backgroundName()throws IOException;
        int allocateLikeFront()throws IOException;
        void target(int handle)throws IOException;
        void redraw()throws IOException;
        void feedback(int x,int y,int handle,int ratio)throws IOException;
        void copy(int x,int y,int handle)throws IOException;
        void present()throws IOException;
        void free(int handle)throws IOException;
        int tickCount()throws IOException;
        int asyncKeyState(int key)throws IOException;
        int pump()throws IOException;
    }
    private final Services services;
    private final Pc1RenderContext render;
    private int depth;
    public Pc1Transition(Services services)throws IOException{this(services,null);}
    private Pc1Transition(Services services,Pc1RenderContext render)throws IOException{if(services==null)throw new IOException("PC1 transition requires complete consumers");this.services=services;this.render=render;}
    public Pc1Transition(Pc1Runtime runtime,Pc1GraphicsPipeline graphics,Pc1TextServices.Platform platform)throws IOException{
        this(production(runtime,graphics,platform),graphics.render());
    }
    private static Services production(Pc1Runtime runtime,Pc1GraphicsPipeline graphics,Pc1TextServices.Platform platform)throws IOException{
        if(runtime==null||graphics==null||graphics.runtime()!=runtime||platform==null)throw new IOException("PC1 transition requires same runtime/graphics/platform");
        return new Services(){
            private Pc1Compositor pixels(){return graphics.render().compositor();}
            public int read(int address)throws IOException{return runtime.graphicsMemory().readInt(address);}
            public void write(int address,int value)throws IOException{runtime.graphicsMemory().writeInt(address,value);}
            public void fullClip()throws IOException{pixels().fullClip();}
            public void fillBlack()throws IOException{pixels().fill(0,0,800,600,0);}
            public void backgroundName()throws IOException{graphics.render().rawGlyph28At(0,0,NAME,7);}
            public int allocateLikeFront()throws IOException{return graphics.render().resources().createLike(0,pixels());}
            public void target(int handle)throws IOException{pixels().target(handle);}
            public void redraw()throws IOException{graphics.render().generalRedraw(0xb,0,0,800,600);}
            public void feedback(int x,int y,int handle,int ratio)throws IOException{pixels().feedback(x,y,handle,ratio);}
            public void copy(int x,int y,int handle)throws IOException{pixels().copy(x,y,handle);}
            public void present()throws IOException{pixels().present();}
            public void free(int handle){pixels().free(handle);}
            public int tickCount()throws IOException{return platform.tickCount();}
            public int asyncKeyState(int key)throws IOException{return platform.asyncKeyState(key);}
            public int pump()throws IOException{return platform.pumpMessages();}
        };
    }
    private interface Work {void run()throws IOException;}
    private void call(Work work)throws IOException{depth++;try{if(render==null)work.run();else render.nativeCall(work::run);}finally{depth--;}}
    public void requireBoundary()throws IOException{if(depth!=0)throw new IOException("PC1 transition snapshot during native callback");}
    public void requireGraph(Pc1GraphicsPipeline graphics)throws IOException{if(graphics==null||render==null||render!=graphics.render())throw new IOException("PC1 transition consumer requires its canonical graphics graph");}
    public Frame beginFrame(int target,int argument)throws IOException{
        Frame frame=new Frame(target,argument);call(()->frame.start());return frame;
    }
    /** The physical Windows adapter follows the native blocking nested pump.
     *Portable callers may retain beginFrame() at its explicit pump boundary. */
    public Pc1TextPresentation.Transition begin(int target,int argument)throws IOException{Frame frame=beginFrame(target,argument);while(frame.active())frame.step();return frame.result()==0?Pc1TextPresentation.Transition.COMPLETE:Pc1TextPresentation.Transition.INTERRUPTED;}
    public Pc1TextPresentation.Transition resume()throws IOException{throw new IOException("PC1 blocking transition has no suspended frame; restore its explicit Frame owner");}
    public final class Frame {
        private int target,argument,startTick,result;
        private boolean temporary,active,inside,failed;
        private Frame(int target,int argument){this.target=target;this.argument=argument;}
        public boolean active(){return active;}
        public int result()throws IOException{if(active||failed)throw new IOException("PC1 transition result before completed native return");return result;}
        private void start()throws IOException{
            services.write(BUSY,1);services.fullClip();services.write(LOOP,1);
            if(target==-1){services.fillBlack();services.backgroundName();services.present();finish();return;}
            temporary=target==services.read(BACKGROUND);
            if(temporary){target=services.allocateLikeFront();services.target(target);services.redraw();services.target(0);argument=0;}
            services.write(PROGRESS,0);active=true;iteration();
        }
        private void iteration()throws IOException{
            startTick=services.tickCount();services.write(DELAY,40);int ratio;
            if(services.read(SPEED)==3||(services.asyncKeyState(0x11)&0x8000)!=0||services.read(RAPID)!=0){ratio=10;services.write(PROGRESS,services.read(PROGRESS)+8);}
            else if(services.read(SPEED)==2){ratio=8;services.write(PROGRESS,services.read(PROGRESS)+4);}
            else if(services.read(SPEED)==1){ratio=6;services.write(PROGRESS,services.read(PROGRESS)+2);}
            else{ratio=3;services.write(PROGRESS,services.read(PROGRESS)+1);}
            services.fullClip();int x=services.read(X),y=services.read(Y);if(argument==0){x=0;y=0;}
            services.feedback(x,y,target,ratio);if(services.read(PROGRESS)>16)services.write(PROGRESS,16);services.present();
        }
        private void finish()throws IOException{services.write(LOOP,0);services.write(BUSY,0);active=false;result=0;}
        public void step()throws IOException{
            if(inside)throw new IOException("PC1 transition same frame reentry");if(failed)throw new IOException("PC1 transition failed native consumer");if(!active)return;
            inside=true;try{call(()->{
                if(services.pump()!=0){active=false;result=1;return;}
                int now=services.tickCount();if(Integer.compareUnsigned(now-startTick,40)<0)return;
                if(services.read(PROGRESS)<16){iteration();return;}
                int x=services.read(X),y=services.read(Y);if(argument==0){x=0;y=0;}
                services.copy(x,y,target);services.present();if(temporary)services.free(target);finish();
            });}catch(IOException|RuntimeException error){failed=true;throw error;}finally{inside=false;}
        }
        public void save(DataOutput out)throws IOException{requireBoundary();if(inside||failed)throw new IOException("PC1 transition local frame is not at a resumable pump boundary");out.writeInt(FORMAT);out.writeInt(target);out.writeInt(argument);out.writeInt(startTick);out.writeInt(result);out.writeBoolean(temporary);out.writeBoolean(active);}
    }
    /** Rebind locals to the SAME restored raw globals/image pool. This does not
     *restore a physical queue, HWND, bitmap or allocate another temporary. */
    public Frame loadFrame(DataInput in)throws IOException{requireBoundary();if(in.readInt()!=FORMAT)throw new IOException("PC1 transition frame format");Frame frame=new Frame(in.readInt(),in.readInt());frame.startTick=in.readInt();frame.result=in.readInt();frame.temporary=in.readBoolean();frame.active=in.readBoolean();if(frame.result!=0&&frame.result!=1||frame.active&&frame.result!=0)throw new IOException("PC1 transition frame return state");return frame;}
}
