package org.circus.core;

import java.io.*;

/** DCDX4227C0 wait65. Relative9:84 and speed modifiers9:82/83 are native
 * ordinary bytes. A late frontend tick polls once, matching a coalesced timer. */
public final class DcdxActorMotion {
    public boolean active;
    public int actor,targetX,targetY;
    private boolean firstPoll,originLatched,clearModifiers;
    private int phase,originX,originY;
    public void start(int actor,int x,int y)throws IOException{
        if(active||actor<0||actor>=5||x<Short.MIN_VALUE||x>Short.MAX_VALUE||y<Short.MIN_VALUE||y>Short.MAX_VALUE)throw new IOException("DCDX actor wait owner/target");
        this.actor=actor;targetX=x;targetY=y;active=true;firstPoll=true;phase=0;clearModifiers=false;
    }
    /** Native transfer can abandon a wait without writing the relative origin globals. */
    public void cancel(){active=false;firstPoll=false;phase=0;clearModifiers=false;}
    public boolean takeClearModifiers(){boolean result=clearModifiers;clearModifiers=false;return result;}
    public boolean relativeOriginLatched(){return originLatched;}
    public int relativeOriginX(){return originX;}
    public int relativeOriginY(){return originY;}
    /** Returns true only for a native full-scene redraw. The completion poll draws nothing. */
    public boolean advance(long ms,int speed,boolean ctrl,int add,int subtract,int relative,int[][] live){
        if(!active)return false;
        if(live==null||actor>=live.length||live[actor]==null||live[actor].length<2||add<0||add>255||subtract<0||subtract>255)throw new IllegalArgumentException("DCDX actor motion inputs");
        long delta=Math.max(0,ms);boolean due=firstPoll||delta>=10||phase+delta%10>=10;
        phase=(int)((phase+delta%10)%10);if(!due)return false;firstPoll=false;
        int[] position=live[actor];int x=targetX,y=targetY,distance;
        if(relative==1){
            if(!originLatched){originX=position[0];originY=position[1];originLatched=true;}
            x+=originX;y+=originY;
            if(position[0]==x&&position[1]==y){originX=originY=0;originLatched=false;active=false;return false;}
            distance=add+1;
            // The native relative branch uses the requested offset's sign.
            position[0]=relativeMove(position[0],x,targetX,distance);
            position[1]=relativeMove(position[1],y,targetY,distance);
        }else{
            if(position[0]==x&&position[1]==y){active=false;clearModifiers=true;return false;}
            int base=speed==3||ctrl?64:speed==2?32:speed==1?16:8;
            distance=Math.max(1,base+add-subtract);
            position[0]=absoluteMove(position[0],x,distance);position[1]=absoluteMove(position[1],y,distance);
        }
        return true;
    }
    private static int absoluteMove(int from,int target,int distance){
        if(from<target){int next=from+distance;return next>target?target:next;}
        if(from>target){int next=from-distance;return next<target?target:next;}
        return from;
    }
    private static int relativeMove(int from,int target,int offset,int distance){
        if(offset>0){int next=from+distance;return next>=target?target:next;}
        if(offset<0){int next=from-distance;return next<=target?target:next;}
        return from;
    }
    public void save(DataOutput out)throws IOException{
        out.writeInt(0x44584d31);out.writeBoolean(active);out.writeInt(actor);out.writeInt(targetX);out.writeInt(targetY);out.writeBoolean(firstPoll);out.writeInt(phase);
        out.writeBoolean(originLatched);out.writeInt(originX);out.writeInt(originY);out.writeBoolean(clearModifiers);
    }
    public void load(DataInput in)throws IOException{
        if(in.readInt()!=0x44584d31)throw new IOException("DCDX actor motion version");
        boolean nextActive=in.readBoolean();int nextActor=in.readInt(),x=in.readInt(),y=in.readInt();boolean nextFirst=in.readBoolean();int nextPhase=in.readInt();
        boolean latched=in.readBoolean();int ox=in.readInt(),oy=in.readInt();boolean clear=in.readBoolean();
        if(nextActor<0||nextActor>=5||x<Short.MIN_VALUE||x>Short.MAX_VALUE||y<Short.MIN_VALUE||y>Short.MAX_VALUE||nextPhase<0||nextPhase>=10)throw new IOException("DCDX actor motion state");
        active=nextActive;actor=nextActor;targetX=x;targetY=y;firstPoll=nextFirst;phase=nextPhase;originLatched=latched;originX=ox;originY=oy;clearModifiers=clear;
    }
}
