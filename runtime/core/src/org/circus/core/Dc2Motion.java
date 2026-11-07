package org.circus.core;

import java.io.*;
import java.util.*;

/** D.C.II 416499/418890: background offsets and the native feedback draw journal.
 * The journal retains integer blend order across large elapse calls and snapshots. */
public final class Dc2Motion {
    public static final int FULL=0,BACKGROUND=1,PREPARE=2;
    public static final class Draw {
        public final int kind,x,y,alpha;
        Draw(int kind,int x,int y,int alpha){this.kind=kind;this.x=x;this.y=y;this.alpha=alpha;}
    }
    public final Dc2Scene.Frame source;
    public final UiCapture initial;
    public final Dc2Motion previous;
    public final String background;
    private final List<Draw> draws=new ArrayList<>();
    public int x,y,targetX,targetY,alpha,settled,prepareProgress;
    public boolean active=true,preparing;
    public boolean prepareOnly;
    /** Frozen mask0F composition; a later live particle tick must not change an earlier blend. */
    public UiCapture prepared;
    public long clock;
    public Dc2Motion(Dc2Scene.Frame source,UiCapture initial,Dc2Motion previous,int targetX,int targetY,int alpha,boolean prepare,int speed,boolean skip){
        this.source=source;this.initial=initial;this.previous=previous;this.background=source.slots[1];
        x=source.x;y=source.y;this.targetX=targetX;this.targetY=targetY;this.alpha=alpha;preparing=prepare;
        tick(speed,skip); // 407106 dispatch falls straight through to 40717E's wait consumer.
    }
    public static Dc2Motion preparation(Dc2Scene.Frame source,UiCapture initial,UiCapture prepared,int speed,boolean skip){Dc2Motion result=new Dc2Motion(source,initial,null,source.x,source.y,0,true,speed,skip);result.prepareOnly=true;result.prepared=prepared;return result;}
    public long untilFrame(){return active?(preparing?40:10)-clock:Long.MAX_VALUE;}
    private Dc2Motion(Dc2Scene.Frame source,UiCapture initial,Dc2Motion previous,String background){this.source=source;this.initial=initial;this.previous=previous;this.background=background;}
    public List<Draw> draws(){return Collections.unmodifiableList(draws);}
    public static int step(int speed,boolean skip){return skip||speed==3?16:speed==2?8:speed==1?4:2;}
    public void elapse(long milliseconds,int speed,boolean skip){
        long left=Math.max(0,milliseconds);
        while(active){int period=preparing?40:10;long due=period-clock;if(left<due){clock+=left;break;}left-=due;clock=0;tick(speed,skip);}
    }
    private void tick(int speed,boolean skip){
        if(preparing){
            if(prepareProgress>=16){
                draws.add(new Draw(FULL,x,y,0));preparing=false;alpha=16;
                if(prepareOnly){active=false;return;}
            }else{
                int increment=skip||speed==3?8:speed==2?4:speed==1?2:1;
                int weight=skip||speed==3?10:speed==2?8:speed==1?6:3;
                prepareProgress=Math.min(16,prepareProgress+increment);draws.add(new Draw(PREPARE,0,0,weight));return;
            }
        }
        if(x==targetX&&y==targetY){
            if(alpha==0){active=false;return;}
            settled++;
            if(settled+alpha>16)alpha=0;
        }
        int distance=step(speed,skip);x=approach(x,targetX,distance);y=approach(y,targetY,distance);
        draws.add(new Draw(alpha==0?FULL:BACKGROUND,x,y,alpha));
    }
    private static int approach(int from,int to,int step){return from<to?(int)Math.min((long)to,(long)from+step):(int)Math.max((long)to,(long)from-step);}
    /** 40828E: 24-bit components, multiplication and >>4 before byte truncation. */
    public static int blend(int old,int fresh,int alpha){
        int inverse=16-alpha;
        int rb=(((old&0xff00ff)*inverse+(fresh&0xff00ff)*alpha)>>>4)&0xff00ff;
        int g=((((old>>>4)&0xff00ff0)*inverse+((fresh>>>4)&0xff00ff0)*alpha)&0xff00ff00);
        return 0xff000000|((rb|g)&0xffffff);
    }
    /** A source bitmap is placed at the signed native destination offset, with clipping. */
    public static void blit(int[] canvas,int width,int height,int[] image,int imageWidth,int imageHeight,int x,int y,int alpha){
        int left=(int)Math.max(0L,Math.min((long)width,x)),top=(int)Math.max(0L,Math.min((long)height,y));
        int right=(int)Math.max(0L,Math.min((long)width,(long)x+imageWidth)),bottom=(int)Math.max(0L,Math.min((long)height,(long)y+imageHeight));
        for(int row=top;row<bottom;row++){int out=row*width+left,in=(row-y)*imageWidth+left-x;for(int col=left;col<right;col++,out++,in++)canvas[out]=blend(canvas[out],image[in],alpha);}
    }
    public void save(DataOutput out)throws IOException{save(out,0);}
    private void save(DataOutput out,int depth)throws IOException{
        if(depth>256)throw new IOException("DC2 motion journal depth");out.writeInt(0x44434d32);out.writeBoolean(prepareOnly);UiCapture.save(out,prepared);out.writeBoolean(previous!=null);if(previous!=null)previous.save(out,depth+1);
        UiCapture.save(out,initial);source.save(out);out.writeUTF(background);
        for(int value:new int[]{x,y,targetX,targetY,alpha,settled,prepareProgress})out.writeInt(value);
        out.writeBoolean(active);out.writeBoolean(preparing);out.writeLong(clock);out.writeInt(draws.size());
        for(Draw draw:draws){out.writeByte(draw.kind);out.writeInt(draw.x);out.writeInt(draw.y);out.writeInt(draw.alpha);}
    }
    public static Dc2Motion load(DataInput in)throws IOException{return load(in,0);}
    private static Dc2Motion load(DataInput in,int depth)throws IOException{
        if(depth>256)throw new IOException("DC2 motion journal depth");if(in.readInt()!=0x44434d32)throw new IOException("DC2 motion journal version");boolean prepareOnly=in.readBoolean();UiCapture prepared=UiCapture.load(in);Dc2Motion previous=in.readBoolean()?load(in,depth+1):null;UiCapture initial=UiCapture.load(in);
        Dc2Scene.Frame source=new Dc2Scene.Frame();source.load(in);Dc2Motion result=new Dc2Motion(source,initial,previous,in.readUTF());
        result.x=in.readInt();result.y=in.readInt();result.targetX=in.readInt();result.targetY=in.readInt();result.alpha=in.readInt();result.settled=in.readInt();result.prepareProgress=in.readInt();
        result.prepareOnly=prepareOnly;result.prepared=prepared;
        result.active=in.readBoolean();result.preparing=in.readBoolean();result.clock=in.readLong();int count=in.readInt();
        if(count<0||count>131072||result.clock<0||result.clock>=(result.preparing?40:10)||result.prepareProgress<0||result.prepareProgress>16||previous==null&&initial==null||previous!=null&&initial!=null)throw new IOException("Invalid DC2 motion journal");
        while(count-->0){int kind=in.readUnsignedByte(),x=in.readInt(),y=in.readInt(),alpha=in.readInt();if(kind>PREPARE)throw new IOException("Invalid DC2 motion draw");result.draws.add(new Draw(kind,x,y,alpha));}
        return result;
    }
}
