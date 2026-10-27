package org.circus.core;
import java.io.*;

/** Native 97/93 camera: scale about separate background and actor pivots.
 * 424F49 clamps offsets; actors use 80 percent parallax. Mode 1 lasts speed*12 ticks. */
public final class Dc3Camera {
    public float zoom=100,x,y;
    private float z0=100,x0,y0,z1=100,x1,y1;public long elapsed,duration;
    public int speed=5;
    public void start(int mode,int percent,int dx,int dy)throws IOException {
        if(mode<0||mode>1||percent<1||percent>10000)throw new IOException("Unsupported camera mode/range");
        z0=zoom;x0=x;y0=y;z1=percent;x1=Math.max(-512,Math.min(512,dx));y1=Math.max(-288,Math.min(288,dy));elapsed=0;duration=mode==0?0:speed*200L;
        if(duration==0){zoom=z1;x=x1;y=y1;}
    }
    public void elapse(long ms){if(!active())return;elapsed=Math.min(duration,elapsed+Math.max(0,ms));float t=elapsed/(float)duration;zoom=z0+(z1-z0)*t;x=x0+(x1-x0)*t;y=y0+(y1-y0)*t;}
    public boolean active(){return elapsed<duration;}
    public Dc3Camera copy(){Dc3Camera c=new Dc3Camera();c.zoom=zoom;c.x=x;c.y=y;c.z0=z0;c.x0=x0;c.y0=y0;c.z1=z1;c.x1=x1;c.y1=y1;c.elapsed=elapsed;c.duration=duration;c.speed=speed;return c;}
    public void save(DataOutput out)throws IOException{for(float f:new float[]{zoom,x,y,z0,x0,y0,z1,x1,y1})out.writeFloat(f);out.writeLong(elapsed);out.writeLong(duration);out.writeInt(speed);}
    public void load(DataInput in)throws IOException{zoom=in.readFloat();x=in.readFloat();y=in.readFloat();z0=in.readFloat();x0=in.readFloat();y0=in.readFloat();z1=in.readFloat();x1=in.readFloat();y1=in.readFloat();elapsed=in.readLong();duration=in.readLong();speed=in.readInt();for(float f:new float[]{zoom,x,y,z0,x0,y0,z1,x1,y1})if(!Float.isFinite(f))throw new IOException("Invalid camera number");if(zoom<=0||zoom>10000||z0<=0||z0>10000||z1<=0||z1>10000||Math.abs(x)>512||Math.abs(y)>288||Math.abs(x0)>512||Math.abs(y0)>288||Math.abs(x1)>512||Math.abs(y1)>288||elapsed<0||elapsed>duration||duration>51000||speed<0||speed>255)throw new IOException("Invalid camera state");}
}
