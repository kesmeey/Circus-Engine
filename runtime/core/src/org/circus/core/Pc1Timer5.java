package org.circus.core;
import java.io.*;

/** Complete current418DF0: effects, head timeout and SE callback/10/11 wait.
 *All bytes stay in their existing frame/settings/effect owners. */
public final class Pc1Timer5 {
    public static final int CALLBACK=0xbb849c;
    /**32-byte callback cell plus the verified4-byte BSS gap before CG_COUNT.
     *The gap survives native clear32 and is visible to overflowing C sources. */
    public static final class Memory {
        private static final int FORMAT=0x50315435;private final byte[] callback=new byte[36];
        public Memory(Pc1NativeMemory memory)throws IOException{if(memory==null)throw new IOException("PC1 timer5 callback memory missing");memory.bind(CALLBACK,36,new Pc1NativeMemory.Region(){public int read(int at){return callback[at]&255;}public void write(int at,int value){callback[at]=(byte)value;}});}
        public void save(DataOutput out)throws IOException{out.writeInt(FORMAT);out.write(callback);}
        public static byte[] loadImage(DataInput in)throws IOException{if(in.readInt()!=FORMAT)throw new IOException("PC1 timer5 memory format");byte[] raw=new byte[36];in.readFully(raw);return raw;}
        public void restore(byte[] raw)throws IOException{if(raw==null||raw.length!=36)throw new IOException("PC1 timer5 memory size");System.arraycopy(raw,0,callback,0,callback.length);}
    }
    public interface Services {
        int read(int address)throws IOException;void write(int address,int value)throws IOException;
        int readByte(int address)throws IOException;
        void updateEffects()throws IOException;void fullClip()throws IOException;
        void timerDrawLayer(int layer)throws IOException;void redrawOverlaps()throws IOException;void paintRectangles()throws IOException;
        void redrawHead()throws IOException;void present()throws IOException;
        int soundIdle()throws IOException;int findLabel(int rawName)throws IOException;
    }
    private final Services io;private final Pc1RenderContext render;private int depth;
    public Pc1Timer5(Services io)throws IOException{this(io,null);}
    private Pc1Timer5(Services io,Pc1RenderContext render)throws IOException{if(io==null)throw new IOException("PC1 timer5 requires all native consumers");this.io=io;this.render=render;}
    public Pc1Timer5(Pc1Runtime runtime,Pc1GraphicsPipeline graph,Pc1Audio audio,Pc1TextServices.Settings settings)throws IOException{
        this(production(runtime,graph,audio,settings),graph.render());runtime.bindNativeBoundary(this::requireBoundary);
    }
    private static Services production(Pc1Runtime runtime,Pc1GraphicsPipeline graph,Pc1Audio audio,Pc1TextServices.Settings settings)throws IOException{
        if(runtime==null||graph==null||graph.runtime()!=runtime||audio==null||settings==null)throw new IOException("PC1 timer5 requires canonical graph/audio/settings");Pc1LabelScanner labels=new Pc1LabelScanner(runtime.nativeMemory());
        return new Services(){
            public int read(int at)throws IOException{return runtime.graphicsMemory().readInt(at);}public void write(int at,int value)throws IOException{runtime.graphicsMemory().writeInt(at,value);}public int readByte(int at)throws IOException{return runtime.nativeMemory().readAddress(at);}
            public void updateEffects()throws IOException{graph.updateEffects();}public void fullClip()throws IOException{graph.render().compositor().fullClip();}public void timerDrawLayer(int layer)throws IOException{graph.timerDrawEffects(layer);}public void redrawOverlaps()throws IOException{graph.redrawEffectOverlaps();}public void paintRectangles()throws IOException{graph.paintEffectRectangles();}
            public void redrawHead()throws IOException{graph.render().generalRedraw(255,584,8,776,40);}public void present()throws IOException{graph.render().compositor().present();}
            public int soundIdle()throws IOException{return audio.soundIdle(settings.current());}public int findLabel(int rawName)throws IOException{return labels.find(rawName);}
        };
    }
    public void requireBoundary()throws IOException{if(depth!=0)throw new IOException("PC1 timer5 snapshot during native consumer callback");}
    public int tick()throws IOException{depth++;try{if(render==null)return consume();int[] result={0};render.nativeCall(()->result[0]=consume());return result[0];}finally{depth--;}}
    private void clear32(int at)throws IOException{for(int n=0;n<8;n++)io.write(at+n*4,0);}
    private int consume()throws IOException{
        if(io.read(0xbb8434)==0){
            if(io.read(0x9da4b4)!=0&&io.read(0x454cf4)!=0){io.updateEffects();io.fullClip();io.timerDrawLayer(1);io.timerDrawLayer(0);io.redrawOverlaps();io.paintRectangles();}
            if(io.read(0x43d470)>=0){if(io.read(0xbb8548)>0)io.write(0xbb8548,io.read(0xbb8548)-1);io.redrawHead();io.present();if(io.read(0xbb8548)==0)io.write(0x43d470,-1);}
        }
        int wait=io.read(0x9d8a20);if(wait==10||wait==11){
            int count=io.read(0x9da4c0);if(count!=0){count--;io.write(0x9da4c0,count);if(count==0){io.write(0x9d8a20,-1);return 1;}}
            if(io.readByte(CALLBACK)!=0&&io.soundIdle()!=0){int label=io.findLabel(CALLBACK);if(label!=-1){clear32(CALLBACK);clear32(0x9d95dc);clear32(0x9d95fc);io.write(0x9d8a20,-1);io.write(0x9d8a14,label);return 1;}}
        }
        return 0;
    }
}
