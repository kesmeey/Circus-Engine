package org.circus.core;

import java.io.*;
import java.util.*;

/** DC2 405340 prepare /405530 poll, native system10 then11 wait1C/type1.
 * Timing, integer alpha and mouse-clear eligibility are separate from GEND12. */
public final class Dc2CharacterEnding {
    private static final String[] ACTORS={"otm","yme","nnk","mnt","anz","kko"};
    private static final int[] COUNTS={24,24,19,18,18,19};
    private static final int[] CLEARS={1,2,3,6,5,4};
    private static final long U32=0xffffffffL;
    public boolean prepared,active,drawn;
    public int variant,index,alpha,whiteAlpha=16,drawIndex,drawAlpha,drawWhiteAlpha;
    private boolean whiteDone,skip;
    private long clock,baseClock;
    private int pollPhase;
    private boolean firstPoll=true;
    private int[] durations=new int[24];
    public static final class Layer {
        public final String resource;
        public final int alpha;
        public final boolean opaque;
        public final int x=0,y=0,srcX=0,srcY=0,width=800,height=600;
        private Layer(String resource,int alpha,boolean opaque){this.resource=resource;this.alpha=alpha;this.opaque=opaque;}
    }
    public int count(){return COUNTS[variant];}
    public int clearIndex(){return CLEARS[variant];}
    public boolean skipRequested(){return skip;}
    public long elapsed(){return(clock-baseClock)&U32;}
    /** Snapshot all24 original byte durations now; later ordinary writes do
     * not retime a prepared ending. System10 does not enter a modal wait. */
    public void prepare(int mode,int[] seconds)throws IOException {
        if(mode<0||mode>=ACTORS.length||seconds==null||seconds.length!=24)throw new IOException("DC2 character ending preparation arguments");
        int[] times=new int[24];for(int n=0;n<24;n++){if(seconds[n]<0||seconds[n]>255)throw new IOException("DC2 character ending duration byte");times[n]=seconds[n]*1000;}
        clear();variant=mode;durations=times;prepared=true;
    }
    public void begin()throws IOException {if(!prepared||active)throw new IOException("DC2 character ending11 requires a prepared10 owner");active=true;pollPhase=0;firstPoll=true;}
    public void clear(){prepared=active=drawn=whiteDone=skip=false;variant=index=alpha=drawIndex=drawAlpha=drawWhiteAlpha=0;whiteAlpha=16;clock=baseClock=0;pollPhase=0;firstPoll=true;durations=new int[24];}
    public String image(int n){if(n<0||n>count())throw new IllegalArgumentException("DC2 character ending page "+n);return n==count()?"BG/black":String.format(Locale.ROOT,"ED/ed_%s_%02d",ACTORS[variant],n+1);}
    /** Shared405A20 input has exact equality1 for the current route's clear
     * flag. The host reads bank100 at clearIndex(); no persistent write here. */
    public void input(int button,int routeClearFlag){if(active&&(button==1||button==2)&&routeClearFlag==1)skip=true;}
    private int duration(int n){return n>=0&&n<durations.length?durations[n]:0;}
    public void elapse(long millis){
        if(!prepared)return;clock=(clock+(Math.max(0,millis)&U32))&U32;if(!active)return;
        firstPoll=false;pollPhase=0;poll();
    }
    /** Real native modal timer cadence; preparation time advances the clock
     * without consuming the white/fade phase before system11 begins. */
    public void advance(long millis){
        if(!prepared)return;long delta=Math.max(0,millis);clock=(clock+(delta&U32))&U32;if(!active)return;
        boolean due=firstPoll||delta>=50||pollPhase+delta%50>=50;pollPhase=(int)((pollPhase+delta%50)%50);
        if(due){firstPoll=false;poll();}
    }
    private void poll(){
        if(!whiteDone&&--whiteAlpha==0)whiteDone=true;
        drawn=true;drawIndex=index;drawAlpha=alpha;drawWhiteAlpha=whiteDone?0:whiteAlpha;
        long elapsed=elapsed(),duration=duration(index);
        if(elapsed>((duration-1000)&U32))alpha=Math.min(16,alpha+1);
        if(elapsed>duration){
            index++;long accumulated=duration(index);
            // Literal40569E loop: accumulate next duration even when the
            // index remains unchanged; this is not a conventional timeline.
            for(int n=0;n<count();n++){accumulated=(accumulated+duration(index+1))&U32;if(((accumulated+200)&U32)<elapsed&&duration(index)>=4000)index++;}
            alpha=0;baseClock=clock;
        }
        if(index>=count()||skip){active=prepared=false;}
    }
    public List<Layer> frame(){
        if(!drawn)return Collections.emptyList();ArrayList<Layer> out=new ArrayList<>(3);
        out.add(new Layer(image(drawIndex),16,true));out.add(new Layer(image(drawIndex+1),drawAlpha,false));
        if(drawWhiteAlpha>0)out.add(new Layer("BG/white",drawWhiteAlpha,false));return Collections.unmodifiableList(out);
    }
    /** 408B80 BGR key and integer floor, not Java2D's rounded source-over. */
    public static int blendRgb(int destination,int source,int weight){
        if(weight<0||weight>16)throw new IllegalArgumentException("DC2 ending alpha outside0..16");if((source&0xffffff)==0xff00ff)return destination;
        int inverse=16-weight,r=((destination>>>16&255)*inverse+(source>>>16&255)*weight)>>4,g=((destination>>>8&255)*inverse+(source>>>8&255)*weight)>>4,b=((destination&255)*inverse+(source&255)*weight)>>4;
        return 0xff000000|r<<16|g<<8|b;
    }
    public void save(DataOutput out)throws IOException {out.writeInt(0x44324532);out.writeBoolean(prepared);out.writeBoolean(active);out.writeBoolean(drawn);out.writeInt(variant);out.writeInt(index);out.writeInt(alpha);out.writeInt(whiteAlpha);out.writeInt(drawIndex);out.writeInt(drawAlpha);out.writeInt(drawWhiteAlpha);out.writeBoolean(whiteDone);out.writeBoolean(skip);out.writeLong(clock);out.writeLong(baseClock);for(int duration:durations)out.writeInt(duration);out.writeInt(pollPhase);out.writeBoolean(firstPoll);}
    public void load(DataInput in)throws IOException {
        int tag=in.readInt();if(tag!=0x44324531&&tag!=0x44324532)throw new IOException("DC2 character ending state tag");boolean p=in.readBoolean(),a=in.readBoolean(),d=in.readBoolean();int v=in.readInt(),i=in.readInt(),al=in.readInt(),w=in.readInt(),di=in.readInt(),da=in.readInt(),dw=in.readInt();boolean wd=in.readBoolean(),s=in.readBoolean();long c=in.readLong(),b=in.readLong();int[] times=new int[24];for(int n=0;n<24;n++){times[n]=in.readInt();if(times[n]<0||times[n]>255000||times[n]%1000!=0)throw new IOException("DC2 ending duration state");}
        int phase=tag==0x44324532?in.readInt():0;boolean first=tag==0x44324532?in.readBoolean():!d;
        if(v<0||v>=6||i<0||i>24||a&&(!p||i>=COUNTS[v])||al<0||al>16||w<0||w>16||di<0||di>=COUNTS[v]||da<0||da>16||dw<0||dw>15||c<0||c>U32||b<0||b>U32||phase<0||phase>=50||first&&d||!d&&(i!=0||al!=0||w!=16||wd||di!=0||da!=0||dw!=0))throw new IOException("DC2 character ending state");
        prepared=p;active=a;drawn=d;variant=v;index=i;alpha=al;whiteAlpha=w;drawIndex=di;drawAlpha=da;drawWhiteAlpha=dw;whiteDone=wd;skip=s;clock=c;baseClock=b;durations=times;pollPhase=phase;firstPoll=first;
    }
}
