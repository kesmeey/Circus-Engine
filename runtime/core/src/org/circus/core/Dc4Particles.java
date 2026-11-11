package org.circus.core;

import java.io.*;
import java.util.*;

/** PH's five particle planes (41F300/41DAC0/41D1F0).
 * Atlas rectangles, density, bounds and parameters follow the native owner.
 * Portable RNG/60Hz elapsed time are deterministic, not native frame identity.
 */
public final class Dc4Particles {
    public static final int WIRE=0x50483431;
    private static final int[] COUNTS={0,1,3,6,9,12,20},FRAMES={7,7,6,8,5,8};
    public static final class Sprite {
        public int group,index,sx,sy,width,height,scale=100,row,frame,radiusX,radiusY;
        public float x,y,baseX,baseY,speed,phase,angular,alpha,amplitude;
        public boolean rising;
        public long frameTime;
    }
    private int random=1,mode,density,drift,originX,originY,angle,target=-1,angleTicks;
    private long remainder,time;
    private final List<Sprite> sprites=new ArrayList<>();
    public List<Sprite> sprites(){return Collections.unmodifiableList(sprites);}
    public int mode(){return mode;}
    private int next(){random=random*214013+2531011;return (random>>>16)&32767;}
    public void configure(int x,int y,int a,int end,int ticks){originX=x;originY=y;angle=a;target=end;angleTicks=ticks;}
    public void start(int type,int parameter){
        mode=type;density=Math.max(0,Math.min(6,parameter%10));drift=parameter<100?parameter/10:-(parameter%100)/10;
        sprites.clear();time=remainder=0;
        for(int group=0;group<5;group++)for(int i=0;i<COUNTS[density];i++){Sprite p=new Sprite();p.group=group;p.index=i;reset(p);sprites.add(p);}
    }
    private void reset(Sprite p){
        p.frameTime=time*1000/60;p.scale=100;p.angular=0;
        if(mode==13){
            p.phase=angle;p.angular=target!=-1&&angleTicks!=0?(target-angle)/(float)angleTicks:0;
            p.baseX=originX;p.baseY=originY;p.radiusX=next()%20+8+p.index*64;p.radiusY=next()%20+8+p.index*64;
            p.scale=next()%400+300;int variant=next()%3;p.sx=variant==0?140:variant==1?180:200;p.sy=0;p.width=p.height=variant==0?40:variant==1?20:8;
            p.alpha=(next()%10)/100f;locateGlow(p);return;
        }
        p.baseX=next()%1280;p.y=-(next()%720);p.phase=next()%360;
        p.row=4-p.group+(next()&1);p.frame=next()%FRAMES[p.row];p.width=p.height=28;p.sx=p.frame*28;p.sy=p.row*28;
        if(mode==1){p.speed=(next()%3)*2.5f+.5f;p.amplitude=(next()&15)+4;p.alpha=(next()%10)/10f;p.rising=next()!=0;}
        else if(mode==11){p.y=next()%720;p.speed=((next()&3)*.2f+.2f)*(next()%2==0?-1:1);p.amplitude=(next()&31)+28;p.alpha=0;p.rising=true;}
        else{int tile=next()%3;p.baseX+=tile==1?-1280:tile==2?1280:0;p.speed=((density==6?(next()&3):next()%10)+2)/1.5f;p.amplitude=(next()&31)+28;p.scale=p.group==4?100:(6+p.group)*10;p.alpha=(next()%3)/10f+.5f-(4-p.group)*.02f;}
        locate(p);
    }
    private static void locateGlow(Sprite p){double phase=p.phase*.017453277777777776;p.x=p.baseX+(float)Math.sin(phase)*p.radiusX;p.y=p.baseY+(float)Math.cos(phase)*p.radiusY;}
    private static void locate(Sprite p){p.x=p.baseX+(float)Math.sin(p.phase)*p.amplitude;}
    public void elapse(long millis){
        long value=remainder+Math.max(0,millis)*60,ticks=Math.min(3600,value/1000);remainder=value%1000;
        for(long i=0;i<ticks;i++){time++;long ms=time*1000/60;for(Sprite p:sprites){
            if(mode==13){if(p.angular!=0){p.phase+=p.angular;if(Math.abs(p.phase-target)<Math.abs(p.angular))p.angular=0;locateGlow(p);}continue;}
            p.phase+=mode==11?.01f:.05f;p.y+=mode==11?-p.speed:p.speed;
            if(mode!=1&&mode!=11)p.baseX+=drift;
            if(ms-p.frameTime>(mode==1||mode==11?500:300)){p.frame=(p.frame+1)%FRAMES[p.row];p.sx=p.frame*28;p.frameTime=ms;}
            if(mode==1||mode==11){float delta=mode==1?.002f:.005f;p.alpha+=p.rising?delta:-delta;if(p.alpha>=.8f){p.alpha=.8f;p.rising=false;}else if(p.alpha<=0){p.alpha=0;p.rising=true;}}
            locate(p);if(p.y>720||p.y < -748||mode==11&&(p.x < -28||p.x>1280))reset(p);
        }}
    }
    public void save(DataOutput o)throws IOException{
        o.writeInt(mode);o.writeInt(density);o.writeInt(drift);o.writeInt(random);o.writeInt(originX);o.writeInt(originY);o.writeInt(angle);o.writeInt(target);o.writeInt(angleTicks);o.writeLong(remainder);o.writeLong(time);o.writeInt(sprites.size());
        for(Sprite p:sprites){o.writeInt(p.group);o.writeInt(p.index);o.writeInt(p.sx);o.writeInt(p.sy);o.writeInt(p.width);o.writeInt(p.height);o.writeInt(p.scale);o.writeInt(p.row);o.writeInt(p.frame);o.writeInt(p.radiusX);o.writeInt(p.radiusY);o.writeFloat(p.x);o.writeFloat(p.y);o.writeFloat(p.baseX);o.writeFloat(p.baseY);o.writeFloat(p.speed);o.writeFloat(p.phase);o.writeFloat(p.angular);o.writeFloat(p.alpha);o.writeFloat(p.amplitude);o.writeBoolean(p.rising);o.writeLong(p.frameTime);}
    }
    public static Dc4Particles load(DataInput in)throws IOException{
        Dc4Particles r=new Dc4Particles();r.mode=in.readInt();r.density=in.readInt();r.drift=in.readInt();r.random=in.readInt();r.originX=in.readInt();r.originY=in.readInt();r.angle=in.readInt();r.target=in.readInt();r.angleTicks=in.readInt();r.remainder=in.readLong();r.time=in.readLong();int count=in.readInt();
        if((r.mode!=1&&r.mode!=2&&r.mode!=3&&r.mode!=9&&r.mode!=11&&r.mode!=13)||r.density<0||r.density>6||r.drift < -1000||r.drift>1000||r.remainder<0||r.remainder>=1000||r.time<0||r.angleTicks<0||count!=COUNTS[r.density]*5)throw new IOException("Invalid PH particle state");
        for(int i=0;i<count;i++){Sprite p=new Sprite();p.group=in.readInt();p.index=in.readInt();p.sx=in.readInt();p.sy=in.readInt();p.width=in.readInt();p.height=in.readInt();p.scale=in.readInt();p.row=in.readInt();p.frame=in.readInt();p.radiusX=in.readInt();p.radiusY=in.readInt();p.x=in.readFloat();p.y=in.readFloat();p.baseX=in.readFloat();p.baseY=in.readFloat();p.speed=in.readFloat();p.phase=in.readFloat();p.angular=in.readFloat();p.alpha=in.readFloat();p.amplitude=in.readFloat();p.rising=in.readBoolean();p.frameTime=in.readLong();
            if(p.group<0||p.group>=5||p.index<0||p.index>=20||p.sx<0||p.sx>224||p.sy<0||p.sy>140||p.width<=0||p.width>40||p.height<=0||p.height>40||p.scale<1||p.scale>699||p.row<0||p.row>5||p.frame<0||p.frame>=FRAMES[p.row]||p.radiusX<0||p.radiusX>1250||p.radiusY<0||p.radiusY>1250||!Float.isFinite(p.x)||!Float.isFinite(p.y)||!Float.isFinite(p.baseX)||!Float.isFinite(p.baseY)||!Float.isFinite(p.speed)||!Float.isFinite(p.phase)||!Float.isFinite(p.angular)||!Float.isFinite(p.alpha)||!Float.isFinite(p.amplitude)||p.alpha<0||p.alpha>1||p.frameTime<0)throw new IOException("Invalid PH particle");r.sprites.add(p);
        }return r;
    }
}
