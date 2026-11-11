package org.circus.core;

import java.io.*;
import java.util.*;

/** ABB6 B1/B2, native Gaussian shader pass4, not gamma correction.
 * 444CB0 stores reciprocal texture-coordinate steps; 447C10 interpolates
 * those reciprocals, rather than interpolating the script's strength.
 * The portable clock approximates native render updates at 60 Hz. */
public final class Dc4Blur {
    private static final int MAGIC=0x50484231,MAX_QUEUE=100;
    private static final class Tween {
        int from,to,ticks,step;float start,target,delta;
        Tween(int from,int to,int ticks){this.from=from;this.to=to;this.ticks=ticks;start=1f/(from*10);target=1f/(to*10);delta=(target-start)/ticks;}
    }
    private final ArrayDeque<Tween> queue=new ArrayDeque<>();
    private Tween current;
    private boolean enabled;
    private float reciprocal;
    private int clock;
    private long revision;

    public boolean active(){return enabled&&reciprocal>0;}
    public float textureStep(){return active()?reciprocal:0;}
    public long revision(){return revision;}
    public int pending(){return queue.size()+(current==null?0:1);}
    public void reset(){queue.clear();current=null;enabled=false;reciprocal=0;clock=0;revision++;}

    public boolean command(Dc4Host h,String op,int[] a)throws IOException {
        switch(op){
        case "ph.scene.blur":
            require(a.length==4,"B1 arguments");
            // The unsigned native comparison ignores values outside0..100.
            if(a[0]>=0&&a[0]<=100){enabled=a[0]>0&&a[0]<100;if(enabled)reciprocal=1f/(a[0]*10);revision++;}
            return true;
        case "ph.scene.blur_to":
            require(a.length==4,"B2 arguments");
            if(a[0]<0||a[0]>100||a[1]<0||a[1]>100)return true;
            // Zero endpoints produce native infinities; none occur in the
            // audited536 MES. Do not silently invent a shader interpretation.
            require(a[0]>0&&a[1]>0&&a[2]>0,"B2 zero endpoint/duration");
            if(a[0]==a[1])return true; // Native zero delta does not enqueue.
            require(pending()<MAX_QUEUE,"B2 animation queue");queue.add(new Tween(a[0],a[1],a[2]));return true;
        case "ph.scene.blend":
            if(a[0]!=0)return false;
            // Native B3 mode0 restores the ordinary pass on the same objects.
            enabled=false;revision++;return true;
        default:return false;
        }
    }

    public void elapse(long milliseconds){
        if(milliseconds<0)throw new IllegalArgumentException("Negative PH blur elapsed time");
        long frames=(milliseconds/1000)*60+(clock+(milliseconds%1000)*60)/1000;
        clock=(int)((clock+(milliseconds%1000)*60)%1000);
        // Empty render frames have no effect and need not be iterated.
        while(frames-->0&&(current!=null||!queue.isEmpty())){
            if(current==null){current=queue.remove();reciprocal=current.start;enabled=true;revision++;continue;}
            reciprocal=(float)(reciprocal+current.delta);current.step++;revision++;
            float distance=Math.abs(current.delta);
            if(reciprocal>=current.target-distance&&reciprocal<current.target+distance||current.step>=current.ticks){
                reciprocal=current.target;
                if(current.to==100)enabled=false; // Native444CB0(0,0) at1000.
                current=null;
            }
        }
    }

    /** Nine native UV samples on a whole ARGB texture. The caller must apply
     * this before UI composition. Native executes per render object; applying
     * it to an already composed stage is a documented portable approximation.
     * Point sampling and integer channel rounding approximate GPU filtering.
     * Native's unused local alpha multiplication is intentionally absent. */
    public int[] applyArgb(int[] source,int width,int height){return applyArgb(source,width,height,textureStep());}
    public static int[] applyArgb(int[] source,int width,int height,float step){
        if(width<=0||height<=0||(long)width*height!=source.length||!Float.isFinite(step)||step<0)throw new IllegalArgumentException("Invalid PH blur texture");
        if(step==0)return source;
        int[] out=new int[source.length];
        int[] left=new int[width],right=new int[width];
        double dx=(double)step*width,dy=(double)step*height;
        for(int x=0;x<width;x++){left[x]=clamp((int)Math.round(x-dx),width);right[x]=clamp((int)Math.round(x+dx),width);}
        for(int y=0;y<height;y++){
            int top=clamp((int)Math.round(y-dy),height)*width,bottom=clamp((int)Math.round(y+dy),height)*width,row=y*width;
            for(int x=0;x<width;x++){
                int l=left[x],r=right[x],c=source[row+x];
                int a=source[top+l],b=source[top+x],d=source[top+r],e=source[row+l],f=source[row+r],g=source[bottom+l],j=source[bottom+x],k=source[bottom+r];
                int color=0;
                for(int shift=0;shift<32;shift+=8){int sum=2*(c>>>shift&255)+(a>>>shift&255)+(b>>>shift&255)+(d>>>shift&255)+(e>>>shift&255)+(f>>>shift&255)+(g>>>shift&255)+(j>>>shift&255)+(k>>>shift&255);color|=((sum+5)/10)<<shift;}
                out[row+x]=color;
            }
        }
        return out;
    }
    private static int clamp(int value,int count){return Math.max(0,Math.min(count-1,value));}
    public void save(DataOutput out)throws IOException{
        out.writeInt(MAGIC);out.writeBoolean(enabled);out.writeFloat(reciprocal);out.writeInt(clock);out.writeLong(revision);
        out.writeBoolean(current!=null);if(current!=null)write(out,current);
        out.writeInt(queue.size());for(Tween t:queue)write(out,t);
    }
    private static void write(DataOutput out,Tween t)throws IOException{out.writeInt(t.from);out.writeInt(t.to);out.writeInt(t.ticks);out.writeInt(t.step);}
    private static Tween read(DataInput in)throws IOException{
        int from=in.readInt(),to=in.readInt(),ticks=in.readInt(),step=in.readInt();
        require(from>=1&&from<=100&&to>=1&&to<=100&&from!=to&&ticks>0&&ticks<=32767&&step>=0&&step<ticks,"blur tween snapshot");
        Tween t=new Tween(from,to,ticks);t.step=step;return t;
    }
    public void load(DataInput in)throws IOException{
        require(in.readInt()==MAGIC,"blur snapshot version");boolean active=in.readBoolean();float value=in.readFloat();int remainder=in.readInt();long version=in.readLong();
        require(Float.isFinite(value)&&value>=0&&value<=0.1f&&remainder>=0&&remainder<1000&&version>=0,"blur snapshot state");
        Tween running=in.readBoolean()?read(in):null;int count=in.readInt();require(count>=0&&count+(running==null?0:1)<=MAX_QUEUE,"blur snapshot queue");
        ArrayDeque<Tween> pending=new ArrayDeque<>();for(int n=0;n<count;n++)pending.add(read(in));
        require(!active||value>0,"active blur reciprocal");enabled=active;reciprocal=value;clock=remainder;revision=version;current=running;queue.clear();queue.addAll(pending);
    }
    private static void require(boolean ok,String what)throws IOException{if(!ok)throw new IOException("Invalid PH "+what);}
}
