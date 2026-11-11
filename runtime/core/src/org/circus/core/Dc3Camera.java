package org.circus.core;
import java.io.*;

/** Native 97/93 camera: scale about separate background and actor pivots.
 * 424F49 clamps offsets; actors use 80 percent parallax. Mode 1 lasts speed*12 ticks. */
public final class Dc3Camera {
    // Above every admitted zoom value, so it cannot be an old stream's first
    // float. Frames/captures can cold-load PH without knowing the host profile.
    private static final int PH_FORMAT=0x50484331;
    private boolean ph;
    public float zoom=100,x,y;
    private float z0=100,x0,y0,z1=100,x1,y1;public long elapsed,duration;
    public int speed=5;
    /** Opt in only for the PH ABB6 host; existing camera streams stay unchanged. */
    public void enablePh(){ph=true;}
    public boolean isPh(){return ph;}
    public void start(int mode,int percent,int dx,int dy)throws IOException {
        if(mode<0||mode>1||percent<1||percent>10000)throw new IOException("Unsupported camera mode/range");
        int limitX=ph?639:512,limitY=ph?359:288;
        z0=zoom;x0=x;y0=y;z1=percent;x1=Math.max(-limitX,Math.min(limitX,dx));y1=Math.max(-limitY,Math.min(limitY,dy));elapsed=0;duration=mode==0?0:speed*200L;
        if(ph&&mode==1){
            // Native447FF0: an unscaled image has no observable pivot. Adopt
            // the target pivot before zooming in, and retain the old pivot
            // while zooming out to100; only two nonunit scales tween pivots.
            if(percent==100){x1=x0;y1=y0;}
            else if(zoom==100){x=x0=x1;y=y0=y1;}
        }
        if(duration==0){zoom=z1;x=x1;y=y1;}
    }
    public void elapse(long ms){if(!active())return;elapsed=Math.min(duration,elapsed+Math.max(0,ms));float t=elapsed/(float)duration;zoom=z0+(z1-z0)*t;x=x0+(x1-x0)*t;y=y0+(y1-y0)*t;}
    public boolean active(){return elapsed<duration;}
    public Dc3Camera copy(){Dc3Camera c=new Dc3Camera();c.ph=ph;c.zoom=zoom;c.x=x;c.y=y;c.z0=z0;c.x0=x0;c.y0=y0;c.z1=z1;c.x1=x1;c.y1=y1;c.elapsed=elapsed;c.duration=duration;c.speed=speed;return c;}
    public void save(DataOutput out)throws IOException{if(ph)out.writeInt(PH_FORMAT);for(float f:new float[]{zoom,x,y,z0,x0,y0,z1,x1,y1})out.writeFloat(f);out.writeLong(elapsed);out.writeLong(duration);out.writeInt(speed);}
    public void load(DataInput in)throws IOException{int first=in.readInt();ph=first==PH_FORMAT;zoom=ph?in.readFloat():Float.intBitsToFloat(first);x=in.readFloat();y=in.readFloat();z0=in.readFloat();x0=in.readFloat();y0=in.readFloat();z1=in.readFloat();x1=in.readFloat();y1=in.readFloat();elapsed=in.readLong();duration=in.readLong();speed=in.readInt();for(float f:new float[]{zoom,x,y,z0,x0,y0,z1,x1,y1})if(!Float.isFinite(f))throw new IOException("Invalid camera number");int limitX=ph?639:512,limitY=ph?359:288,maxSpeed=ph?32767:255;if(zoom<=0||zoom>10000||z0<=0||z0>10000||z1<=0||z1>10000||Math.abs(x)>limitX||Math.abs(y)>limitY||Math.abs(x0)>limitX||Math.abs(y0)>limitY||Math.abs(x1)>limitX||Math.abs(y1)>limitY||elapsed<0||elapsed>duration||duration>maxSpeed*200L||speed<0||speed>maxSpeed)throw new IOException("Invalid camera state");}
}
