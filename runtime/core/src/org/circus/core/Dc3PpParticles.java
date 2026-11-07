package org.circus.core;

import java.io.*;
import java.util.*;

/** P.P. effect 4: 40A7C0/40BA40/407D40 in the current Chinese executable.
 * Records and RNG are native integers. RNG is owned by this effect rather than
 * shared with the other native subsystems; the platform supplies a 60 Hz clock.
 */
public final class Dc3PpParticles {
    private static final int MAGIC=0x50503431;
    private static final int[] COUNTS={0,5,10,15,30},FRAMES={7,7,6,8,5,8};
    private final int[][] slots=new int[50][26];
    private final int[] random={88675123,362436069,521288629,123456789};
    private int strength,toggle,windKind,windX;
    private boolean running,enabled=true;
    private long remainder,frames;
    public int strength(){return strength;}
    public boolean running(){return running;}
    public int toggle(){return toggle;}
    public int windKind(){return windKind;}
    public int windX(){return windX;}
    public long remainder(){return remainder;}
    public long frames(){return frames;}
    public int[] slot(int index){return slots[index].clone();}
    public int[] randomWords(){return random.clone();}
    public int activeCount(){int n=0;for(int[] p:slots)if(p[2]!=0)n++;return n;}
    private int next(){int old=random[0],t=random[3]^(random[3]<<11);random[3]=random[1];random[1]=random[2];random[0]=((old>>>11)^t)>>>8^old^t;random[2]=old;return random[0]&0x7fffffff;}

    /** 40B830's kind 4 branch, including zero-strength release and the 7410FC gate. */
    public void command(int value,boolean performanceEnabled){
        if(value==0){stop();return;}
        strength=value;running=true;enabled=performanceEnabled;if(!enabled)return;
        for(int[] p:slots)Arrays.fill(p,0);
        int level=value&15,count=level<5?COUNTS[level]:50;
        for(int n=0;n<count;n++){
            int[] p=slots[n];p[2]=1;p[5]=next()%1024;p[6]=-32-next()%288;
            int row=next()%6;p[0]=row;p[9]=32;p[10]=24;
            if(row<3){p[18]=0;p[21]=next()%24+24;}
            else if(row==3){p[18]=next()&1;p[21]=(next()&15)+16;}
            else{p[18]=1;p[21]=(next()&7)+8;}
            // The original consumes these two values before overwriting both
            // below. Their consumption changes all following particles.
            p[22]=(next()&7)+4;p[15]=(next()&7)+4;
            p[14]=(next()&3)+5;p[22]=(next()&7)+4;p[15]=(next()&7)+4;
            p[3]=p[5]+Dc2Particles.cosine(p[24],p[21],0);p[4]=p[6];
        }
        switch(value/16){
            case 0:windKind=0;windX=0;break;
            case 1:windKind=1;windX=4;break;
            case 2:windKind=2;windX=-4;break;
            case 3:windKind=1;windX=12;break;
            case 4:windKind=2;windX=-12;break;
            default:break; // Native switch leaves the previous wind unchanged.
        }
    }
    public void stop(){for(int[] p:slots)p[2]=0;running=false;}
    public void elapse(long milliseconds){
        if(!running||milliseconds<=0)return;
        long whole=milliseconds/1000,part=milliseconds%1000;
        long scaled=remainder+part*60,ticks=Math.min(3600,whole>60?3600:whole*60+scaled/1000);
        remainder=scaled%1000;
        for(long i=0;i<ticks&&running;i++){frames++;tick();}
    }
    /** One 40C510 frame; 40BA40 advances records on alternate calls. */
    public void tick(){
        if(!running)return;if(toggle==1){toggle=0;return;}toggle=1;boolean any=false;
        for(int[] p:slots)if(p[2]!=0){
            any=true;p[5]+=windX;p[3]=p[5]+Dc2Particles.cosine(p[24],p[21],0);p[4]+=p[14];p[24]+=p[22];
            if(p[4]>=576||windKind==1&&p[3]>=1024||windKind==2&&p[3]+32<0){
                if(windKind==1){if((next()&1)==0){p[5]=next()%1024;p[6]=-32-(next()&63);}else{p[5]=-32-(next()&63);p[6]=next()%400;}}
                else if(windKind==2){if((next()&1)==0){p[5]=next()%1024;p[6]=(next()&63)-64;}else{p[5]=(next()&63)+1024;p[6]=next()%400;}}
                else{p[5]=next()%1024;p[6]=-(next()%300);}
                p[24]=0;p[3]=p[5]+Dc2Particles.cosine(0,p[21],0);p[4]=p[6];
            }
            if(++p[16]>=p[15]){p[16]=0;if(++p[19]>=FRAMES[p[0]])p[19]=0;}
        }
        if(!any&&enabled)stop();
    }
    /** Native 402920 submissions, retaining the two object passes and atlas crop. */
    public static final class Sprite {
        public final int object,x,y,width,height,sx,sy,surface=16,mode=0;
        private Sprite(int[] p){object=p[18]!=0?1:7;x=p[3];y=p[4];width=p[9];height=p[10];sx=p[9]*p[19];sy=p[0]*28;}
    }
    public List<Sprite> sprites(){List<Sprite> out=new ArrayList<>();if(running)for(int[] p:slots)if(p[2]!=0)out.add(new Sprite(p));return out;}
    public void save(DataOutput out)throws IOException{
        out.writeInt(MAGIC);out.writeInt(strength);out.writeBoolean(running);out.writeBoolean(enabled);for(int value:random)out.writeInt(value);out.writeLong(remainder);out.writeLong(frames);out.writeInt(toggle);out.writeInt(windKind);out.writeInt(windX);for(int[] p:slots)for(int value:p)out.writeInt(value);
    }
    public static Dc3PpParticles load(DataInput in)throws IOException{
        if(in.readInt()!=MAGIC)throw new IOException("PP4 particle state version");Dc3PpParticles result=new Dc3PpParticles();result.strength=in.readInt();result.running=in.readBoolean();result.enabled=in.readBoolean();for(int n=0;n<4;n++)result.random[n]=in.readInt();result.remainder=in.readLong();result.frames=in.readLong();result.toggle=in.readInt();result.windKind=in.readInt();result.windX=in.readInt();
        if(result.remainder<0||result.remainder>=1000||result.frames<0||result.toggle<0||result.toggle>1||result.windKind<0||result.windKind>2||result.windKind==0&&result.windX!=0||result.windKind==1&&result.windX!=4&&result.windX!=12||result.windKind==2&&result.windX!=-4&&result.windX!=-12)throw new IOException("Invalid PP4 particle header");
        for(int[] p:result.slots){for(int n=0;n<p.length;n++)p[n]=in.readInt();if(p[2]<0||p[2]>1||!result.running&&p[2]!=0)throw new IOException("PP4 particle active flag");if(p[2]!=0&&(p[0]<0||p[0]>=6||p[9]!=32||p[10]!=24||p[14]<5||p[14]>8||p[15]<4||p[15]>11||p[16]<0||p[16]>=p[15]||p[18]<0||p[18]>1||p[19]<0||p[19]>=FRAMES[p[0]]||p[21]<8||p[21]>47||p[22]<4||p[22]>11||p[24]<0))throw new IOException("Invalid PP4 particle record");}
        return result;
    }
}
