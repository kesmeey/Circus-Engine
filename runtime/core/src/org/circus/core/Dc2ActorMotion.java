package org.circus.core;

import java.io.*;

/** Original DC2 wait61/418CA0, two independent axes and a10ms timer.
 * Current coordinates remain in the live scene owner; reaching the target
 * still draws and waits, then the next poll releases without drawing. */
public final class Dc2ActorMotion {
    public boolean active,drawn;
    public int actor,targetX,targetY,sceneSerial;
    public Dc2MessageWindow template;
    private int phase;
    private boolean first=true;
    public static final class Step {
        public final boolean drawn,released;public final int x,y;
        Step(boolean d,boolean r,int x,int y){drawn=d;released=r;this.x=x;this.y=y;}
    }
    public void start(int selector,int x,int y){clear();active=true;actor=selector==0?0:1;targetX=x;targetY=y;}
    public void clear(){active=drawn=false;actor=targetX=targetY=phase=sceneSerial=0;first=true;template=null;}
    public static int distance(int speed,boolean rapid){return rapid||speed==3?64:speed==2?32:speed==1?16:8;}
    /** Delayed Windows timer events coalesce; never synthesize multiple polls. */
    public Step advance(long millis,int x,int y,int speed,boolean rapid){
        if(!active)return new Step(false,false,x,y);long delta=Math.max(0,millis);boolean due=first||delta>=10||phase+delta%10>=10;phase=(int)((phase+delta%10)%10);if(!due)return new Step(false,false,x,y);first=false;
        if(x==targetX&&y==targetY){active=false;return new Step(false,true,x,y);}
        int d=distance(speed,rapid);drawn=true;return new Step(true,false,axis(x,targetX,d),axis(y,targetY,d));
    }
    private static int axis(int value,int target,int step){if(value<target){value+=step;if(value>target)value=target;}else if(value>target){value-=step;if(value<target)value=target;}return value;}
    public void save(DataOutput out)throws IOException{out.writeInt(0x44324132);out.writeBoolean(active);out.writeInt(actor);out.writeInt(targetX);out.writeInt(targetY);out.writeInt(phase);out.writeBoolean(first);out.writeBoolean(drawn);out.writeInt(sceneSerial);Dc2MessageWindow.save(out,template);}
    public void load(DataInput in)throws IOException{int tag=in.readInt();if(tag!=0x44324131&&tag!=0x44324132)throw new IOException("DC2 actor motion tag");boolean a=in.readBoolean();int n=in.readInt(),x=in.readInt(),y=in.readInt(),p=in.readInt();boolean f=in.readBoolean();boolean d=tag==0x44324132&&in.readBoolean();int serial=tag==0x44324132?in.readInt():0;Dc2MessageWindow window=tag==0x44324132?Dc2MessageWindow.load(in):null;if(n<0||n>1||p<0||p>=10)throw new IOException("DC2 actor motion state");active=a;actor=n;targetX=x;targetY=y;phase=p;first=f;drawn=d;sceneSerial=serial;template=window;}
}
