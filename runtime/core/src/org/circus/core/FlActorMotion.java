package org.circus.core;

import java.io.*;

/** FL 4204C0 wait65. Live XY is distinct from opcode64's staged coordinates.
 * FL alone has the bank3:52 clock branch and a 1ms coalescing native timer. */
public final class FlActorMotion {
    public boolean active;
    public int actor,targetX,targetY;
    private long clock;
    private int base;
    private float residue;
    public void start(int actor,int x,int y)throws IOException{if(active||actor<0||actor>4)throw new IOException("FL actor wait owner");this.actor=actor;targetX=x;targetY=y;active=true;}
    public long clock(){return clock;}
    public int clockBase(){return base;}
    public float residue(){return residue;}
    /** Each late event polls once. Returns true only when native redrew the live scene. */
    public boolean advance(long ms,int speed,boolean ctrl,int mode,int[][] live){
        clock=(clock+(Math.max(0,ms)&0xffffffffL))&0xffffffffL;
        if(!active)return false;
        if(ms<=0)return false;
        int[] p=live[actor];if(p[0]==targetX&&p[1]==targetY){base=0;active=false;return false;}
        // Window timer1ms cannot re-enter the updater on a zero-time frontend refresh.
        float step;
        if(mode==1){
            int now=(int)clock,delta=now-base;if(delta<36)return false;
            if(base==0){base=now;return false;}
            // Native's double constant is exactly15339169/2^26. The signed32
            // product fits55 bits and therefore x87's64-bit significand exactly.
            // Convert long directly to f32 before the exact power-of-two scale,
            // avoiding Java double's possible extra rounding before f32.
            long product=(long)delta*15339169L;step=(float)product/67108864.0f;
            long rounded=(long)((double)step*67108864.0);
            float error=(float)(product-rounded)/67108864.0f;residue=(float)(residue+error);
            if(residue>=1.0f){step=(float)(step+1.0f);residue=(float)(residue-1.0f);}
        }else step=speed==3||ctrl?64:speed==2?32:speed==1?16:8;
        p[0]=move(p[0],targetX,step);p[1]=move(p[1],targetY,step);return true;
    }
    private static int move(int from,int to,float step){
        if(from==to)return from;
        long scaled=(long)from*67108864L,delta=(long)((double)step*67108864.0);
        int next=(int)((scaled+(from<to?delta:-delta))/67108864L);
        return from<to?Math.min(next,to):Math.max(next,to);
    }
    public void save(DataOutput o)throws IOException{o.writeInt(0x46414d31);o.writeBoolean(active);o.writeInt(actor);o.writeInt(targetX);o.writeInt(targetY);o.writeLong(clock);o.writeInt(base);o.writeFloat(residue);}
    public void load(DataInput in)throws IOException {
        if(in.readInt()!=0x46414d31)throw new IOException("FL actor motion snapshot version");boolean a=in.readBoolean();int n=in.readInt(),x=in.readInt(),y=in.readInt();long c=in.readLong();int b=in.readInt();float f=in.readFloat();
        if(n<0||n>4||c<0||c>0xffffffffL||Float.isNaN(f)||Float.isInfinite(f))throw new IOException("FL actor motion state");active=a;actor=n;targetX=x;targetY=y;clock=c;base=b;residue=f;
    }
}
