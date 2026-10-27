package org.circus.core;

import java.io.*;
import java.util.*;

/** DD transition clock and alpha fields, independent of Android drawing APIs. */
public final class Dc3Transition {
    public int mode,variant,generation;
    public long elapsed,duration;
    public String mask="";
    public Dc3Scene.Frame from=new Dc3Scene.Frame();
    private int seed=1;
    public static final class Piece {public int life,age;public float x,y,vx,vy,time;public int bx,by;}
    public final List<Piece> pieces=new ArrayList<>();
    private int random(){seed=seed*214013+2531011;return(seed>>>16)&32767;}
    public boolean active(){return elapsed<duration;}
    public float progress(){return duration==0?1:Math.min(1,elapsed/(float)duration);}
    public void start(int mode,int variant,long duration,String mask,Dc3Scene.Frame from)throws IOException {
        this.mode=mode;this.variant=variant;this.duration=duration;this.mask=mask;this.from=from;elapsed=0;generation++;pieces.clear();
        if(mode==16){
            if(variant!=4&&variant!=5)throw new IOException("Unimplemented break transition variant "+variant);
            // 408060 allocates 33 x 19 alpha stamps, including the clipped edge row/column.
            for(int row=0;row<19;row++)for(int col=0;col<33;col++){
                Piece p=new Piece();int vx=32-(random()&31);random();if((random()&1)==0)vx=(random()&31)-32;
                p.life=(random()&31)+16;p.bx=col*32;p.by=row*32;p.x=p.bx;p.y=p.by;pieces.add(p);
            }
            int ticks=0;
            for(Piece p:pieces){if(variant==4){p.vx=32-(random()&63);p.vy=32-(random()&48);}else{p.age=-(p.by/32)*2-p.bx/32;p.vx=2-(random()&8);p.vy=(random()&8)-2;}ticks=Math.max(ticks,-p.age+Math.min(p.life,24));}
            this.duration=(ticks*1000L+59)/60;
        }
    }
    public void elapse(long millis){long next=Math.min(duration,elapsed+Math.max(0,millis));long oldTick=elapsed*60/1000,newTick=next*60/1000;
        if(mode==16)for(long tick=oldTick;tick<newTick;tick++)for(Piece p:pieces)if(p.life>0){
            if(p.age<0){p.age++;continue;}p.x=(float)(p.bx+p.vx*p.time*4.0);p.y=(float)(p.by+p.vy*p.time*4.0-3.9200000762939453*p.time*p.time);
            p.life--;p.age++;p.time+=.08f;if(p.x>=1024||p.y>=576||p.age>=24)p.life=0;
        }elapsed=next;
    }
    /** 407F90: mask blue + signed bias, truncated and clamped to alpha. */
    public int maskAlpha(int blue){float bias=mode==20?-255+510*progress():255-510*progress();return Math.max(0,Math.min(255,(int)(blue+bias)));}
    public void save(DataOutput out)throws IOException {out.writeInt(mode);out.writeInt(variant);out.writeInt(generation);out.writeInt(seed);out.writeLong(elapsed);out.writeLong(duration);out.writeUTF(mask);from.save(out);out.writeInt(pieces.size());for(Piece p:pieces){out.writeInt(p.life);out.writeInt(p.age);out.writeInt(p.bx);out.writeInt(p.by);out.writeFloat(p.x);out.writeFloat(p.y);out.writeFloat(p.vx);out.writeFloat(p.vy);out.writeFloat(p.time);}}
    public void load(DataInput in)throws IOException {mode=in.readInt();variant=in.readInt();generation=in.readInt();seed=in.readInt();elapsed=in.readLong();duration=in.readLong();mask=in.readUTF();from.load(in);int count=in.readInt();if(count<0||count>627||elapsed<0||elapsed>duration||duration>86400000)throw new IOException("Invalid transition state");pieces.clear();for(int i=0;i<count;i++){Piece p=new Piece();p.life=in.readInt();p.age=in.readInt();p.bx=in.readInt();p.by=in.readInt();p.x=in.readFloat();p.y=in.readFloat();p.vx=in.readFloat();p.vy=in.readFloat();p.time=in.readFloat();if(!Float.isFinite(p.x)||!Float.isFinite(p.y)||!Float.isFinite(p.vx)||!Float.isFinite(p.vy)||!Float.isFinite(p.time)||p.life<0||p.life>47||p.age< -68||p.age>24)throw new IOException("Invalid transition particle");pieces.add(p);}}
}
