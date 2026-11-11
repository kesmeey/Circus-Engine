package org.circus.core;

import java.io.*;

/** 8E:20 configures time/axis;8E:10 queues447400 horizontal sine motion.
 * The original448CE0 restores the actor's base X when its angular range ends.
 * Portable timing uses60Hz and one native initialization update. */
public final class Dc4Sway {
    private int nextTicks=60;
    private final boolean[] active=new boolean[5];
    private final int[] baseX=new int[5],radius=new int[5],from=new int[5],to=new int[5],ticks=new int[5];
    private final long[] elapsed=new long[5];
    public boolean command(Dc4Host h,int[] a)throws IOException{
        if((a[3]&0x20)!=0){require(a[0]>=0&&a[0]<=32767&&a[1]==0,"sway configuration outside audited horizontal axis");nextTicks=a[0]==0?60:a[0];return true;}
        if((a[3]&0x10)==0)return false;
        int actor=a[3]&15;require(actor<5,"sway actor");
        stop(h,actor);baseX[actor]=h.scene.slots[actor+4].x;radius[actor]=a[0];from[actor]=a[1];to[actor]=a[2];ticks[actor]=nextTicks;elapsed[actor]=0;active[actor]=a[1]!=a[2];return true;
    }
    public boolean active(int actor){return active[actor];}
    public void stop(Dc4Host h,int actor){if(active[actor])h.scene.slots[actor+4].x=baseX[actor];active[actor]=false;elapsed[actor]=0;}
    public void reset(Dc4Host h){for(int a=0;a<5;a++)stop(h,a);nextTicks=60;}
    private long duration(int actor){return((ticks[actor]+1L)*1000+59)/60;}
    public long remainingMillis(){long remaining=0;for(int a=0;a<5;a++)if(active[a])remaining=Math.max(remaining,duration(a)-elapsed[a]);return remaining;}
    public void elapse(Dc4Host h,long milliseconds){
        if(milliseconds<0)throw new IllegalArgumentException("Negative PH sway elapsed time");
        for(int a=0;a<5;a++)if(active[a]){
            elapsed[a]+=Math.min(milliseconds,duration(a)-elapsed[a]);
            if(elapsed[a]>=duration(a)){stop(h,a);h.scene.revision++;continue;}
            long update=Math.max(0,elapsed[a]*60/1000-1);
            double angle=from[a]+(to[a]-from[a])*(double)Math.min(update,ticks[a])/ticks[a];
            h.scene.slots[a+4].x=(int)(baseX[a]+Math.sin(angle*Math.PI/180.0)*radius[a]);h.scene.revision++;
        }
    }
    public void save(DataOutput out)throws IOException{out.writeInt(0x50485731);out.writeInt(nextTicks);for(int a=0;a<5;a++){out.writeBoolean(active[a]);out.writeInt(baseX[a]);out.writeInt(radius[a]);out.writeInt(from[a]);out.writeInt(to[a]);out.writeInt(ticks[a]);out.writeLong(elapsed[a]);}}
    public void load(DataInput in)throws IOException{
        require(in.readInt()==0x50485731,"sway snapshot version");nextTicks=in.readInt();require(nextTicks>0&&nextTicks<=32767,"sway ticks");
        for(int a=0;a<5;a++){active[a]=in.readBoolean();baseX[a]=in.readInt();radius[a]=in.readInt();from[a]=in.readInt();to[a]=in.readInt();ticks[a]=in.readInt();elapsed[a]=in.readLong();require(ticks[a]>=0&&ticks[a]<=32767&&elapsed[a]>=0&&(!active[a]||ticks[a]>0&&elapsed[a]<duration(a)),"sway snapshot state");}
    }
    private static void require(boolean ok,String what)throws IOException{if(!ok)throw new IOException("Invalid PH "+what);}
}
