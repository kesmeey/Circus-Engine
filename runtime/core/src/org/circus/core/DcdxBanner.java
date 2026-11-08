package org.circus.core;

import java.io.*;
import java.util.*;

/** DCDX service12: native40A1D0/40A300 and the two five-row tables at572268.
 * Each item first restores its old rectangle from the background and then draws
 * the new keyed sprite. The front outside these rectangles is retained. */
public final class DcdxBanner {
    public static final int RESTORE_BG=0,SPRITE=1;
    private static final int WIDTH=472,HEIGHT=64,COUNT=5,MAX_POLLS=25;
    private static final int[] TARGET_X={0,13,27,56,91};
    private static final int[] Y={232,296,360,424,488};
    private static final int[] SECOND_SOURCE_Y={576,320,384,448,512};
    public static final class Layer {
        public final int kind,sx,sy,sw,sh,x,y,dw,dh;
        public final String resource;
        public final boolean keyed;
        private Layer(int kind,String resource,int sx,int sy,int x,int y){
            this.kind=kind;this.resource=resource;this.sx=sx;this.sy=sy;this.x=x;this.y=y;
            sw=dw=WIDTH;sh=dh=HEIGHT;keyed=kind==SPRITE;
        }
    }
    public UiCapture initial,bg;
    public String atlas="";
    public boolean active,visible,drawn;
    public long generation;
    public int mode,sourceSerial,polls;
    private int clockMs,clockTick;
    private boolean clockInitialized;
    private final int[] x=new int[COUNT];
    private final List<Layer> journal=new ArrayList<Layer>();
    public List<Layer> rawLayers(){return Collections.unmodifiableList(journal);}
    public int itemX(int item){if(item<0||item>=COUNT)throw new IllegalArgumentException("DCDX banner item");return x[item];}
    public int clockMillis(){return clockMs;}
    public int clockFrame(){return clockTick;}
    private static int frameAt(int ms){return ((ms+1)*30-1)/1000;}
    /** The original406C00 keeps an existing1000/30 clock. A host sharing that
     * timer across menu owners can supply its current phase before start. */
    public void syncClock(int ms,int frame)throws IOException{
        if(ms<0||ms>=1000||frame<0||frame>=30||frame!=frameAt(ms))throw new IOException("DCDX banner clock phase");
        clockMs=ms;clockTick=frame;clockInitialized=true;
    }
    public void start(int mode,UiCapture initial,UiCapture bg,String atlas,int serial)throws IOException{
        if(mode<0||mode>3)throw new IOException("DCDX banner stage not implemented "+mode);
        if(initial==null||bg==null||atlas==null||atlas.isEmpty()||serial<0||generation==Long.MAX_VALUE)throw new IOException("DCDX banner native sources");
        this.mode=mode;this.initial=initial;this.bg=bg;this.atlas=atlas;sourceSerial=serial;
        active=visible=true;drawn=false;polls=0;journal.clear();generation++;
        for(int n=0;n<COUNT;n++)x[n]=entering()?-472:TARGET_X[n];
        if(!clockInitialized){clockMs=clockTick=0;clockInitialized=true;}
    }
    public void clear(){active=visible=drawn=false;initial=bg=null;atlas="";mode=sourceSerial=polls=0;Arrays.fill(x,0);journal.clear();}
    private boolean entering(){return (mode&1)==0;}
    /**406C50 coalesces every expired rational30Hz deadline into one update. */
    public boolean advance(long ms){
        if(!active)return false;long delta=Math.max(0,ms),elapsed=clockMs+delta%1000;
        boolean due=delta>=1000||elapsed>=(clockTick+1)*1000/30;
        clockMs=(int)(elapsed%1000);if(!due)return false;clockTick=frameAt(clockMs);poll();return true;
    }
    private void poll(){
        int finished=0;
        for(int n=0;n<COUNT;n++){
            int old=x[n],target=entering()?TARGET_X[n]:-472;
            journal.add(new Layer(RESTORE_BG,"",old,Y[n],old,Y[n]));
            if(old==target)finished++;
            else if(old<target)x[n]=Math.min(target,old+24);
            else x[n]=Math.max(target,old-24);
            int sourceY=mode<2?n*64:SECOND_SOURCE_Y[n];
            journal.add(new Layer(SPRITE,atlas,0,sourceY,x[n],Y[n]));
        }
        polls++;drawn=true;if(finished==COUNT)active=false;
    }
    public void save(DataOutput out)throws IOException{
        out.writeInt(0x44584231);out.writeBoolean(active);out.writeBoolean(visible);out.writeBoolean(drawn);out.writeLong(generation);
        out.writeInt(mode);out.writeInt(sourceSerial);out.writeInt(polls);out.writeUTF(atlas);out.writeBoolean(clockInitialized);out.writeInt(clockMs);out.writeInt(clockTick);
        for(int value:x)out.writeInt(value);UiCapture.save(out,initial);UiCapture.save(out,bg);
        out.writeInt(journal.size());for(Layer layer:journal){out.writeInt(layer.kind);out.writeInt(layer.sx);out.writeInt(layer.sy);out.writeInt(layer.x);out.writeInt(layer.y);}
    }
    /** Validate against the actual native table and complete journal before
     * publishing anything, including captures and future timer phase. */
    public void load(DataInput in)throws IOException{
        if(in.readInt()!=0x44584231)throw new IOException("DCDX banner snapshot version");
        boolean a=in.readBoolean(),v=in.readBoolean(),d=in.readBoolean();long g=in.readLong();
        int m=in.readInt(),serial=in.readInt(),p=in.readInt();String resource=in.readUTF();boolean clock=in.readBoolean();int ms=in.readInt(),frame=in.readInt();
        int[] positions=new int[COUNT];for(int n=0;n<COUNT;n++)positions[n]=in.readInt();UiCapture old=UiCapture.load(in),background=UiCapture.load(in);int count=in.readInt();
        if(g<0||m<0||m>3||serial<0||p<0||p>MAX_POLLS||ms<0||ms>=1000||frame!=frameAt(ms)||!clock&&(ms!=0||frame!=0)||count<0||count>MAX_POLLS*COUNT*2)throw new IOException("DCDX banner snapshot bounds");
        if(v){if(!clock||g==0||resource.isEmpty()||old==null||background==null||a!=(p<MAX_POLLS)||d!=(p!=0)||count!=p*COUNT*2)throw new IOException("DCDX banner visible state");}
        else if(a||d||p!=0||count!=0||m!=0||serial!=0||!resource.isEmpty()||old!=null||background!=null)throw new IOException("DCDX banner cleared state");
        DcdxBanner expected=new DcdxBanner();
        if(v){expected.start(m,old,background,resource,serial);for(int n=0;n<p;n++)expected.poll();}
        if(!Arrays.equals(positions,expected.x))throw new IOException("DCDX banner native positions");
        for(int n=0;n<count;n++){
            int kind=in.readInt(),sx=in.readInt(),sy=in.readInt(),dx=in.readInt(),dy=in.readInt();Layer want=expected.journal.get(n);
            if(kind!=want.kind||sx!=want.sx||sy!=want.sy||dx!=want.x||dy!=want.y)throw new IOException("DCDX banner native draw journal");
        }
        active=a;visible=v;drawn=d;generation=g;mode=m;sourceSerial=serial;polls=p;atlas=resource;clockInitialized=clock;clockMs=ms;clockTick=frame;
        System.arraycopy(positions,0,x,0,COUNT);initial=old;bg=background;journal.clear();journal.addAll(expected.journal);
    }
}
