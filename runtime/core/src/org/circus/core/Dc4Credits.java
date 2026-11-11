package org.circus.core;

import java.io.*;
import java.util.*;

/** PH4595F0/459120 staff roll. Original pages and float rate are retained.
 * The native alpha tail advances once per render update; this owner assigns
 * those updates a deterministic60Hz clock. Companion background slides and
 * native input skipping are outside this initial controller. */
public final class Dc4Credits {
    public static final int WIDTH=1280,HEIGHT=720;
    public static final String ARCHIVE="ED/ed.PCK";
    private static final int MAGIC=0x50484331;
    public enum Step { INACTIVE,DRAW,COMPLETE }
    private int strips,seconds,mode;
    private float speed;
    private long elapsed,lastPageAt,completionAt;
    private boolean enabled;

    /** The original IMUL count*720 precedes x87 division and one float store. */
    public static float nativeSpeed(int count,int milliseconds){return(float)((double)(count*HEIGHT)/(double)milliseconds);}
    /** x87 multiplies the integer tick by the stored float, then ftol truncates.
     * A Java float product would round a second time (125000ms is a boundary).
     * All admitted count/time products fit exactly in double precision. */
    public static int nativeDistance(int milliseconds,float speed){return(int)((double)milliseconds*(double)speed);}
    public static float nativeFade(int updates){float alpha=0;for(int i=0;i<updates&&alpha<1;i++)alpha=(float)((double)alpha+0.005);return Math.min(1,alpha);}
    public static int nativeFadeUpdates(){int n=0;while(nativeFade(n)<1)n++;return n;}
    public void start(CircusResources data,int seconds,int mode)throws IOException {
        if(data==null)throw new IOException("PH credits require actual resources");start(data.files,seconds,mode);
    }
    /** Index and twenty-byte CRX headers only; no full archive or decoded image cache. */
    public void start(GameResources files,int seconds,int mode)throws IOException {
        Archive pages=files.archive("AdvData/GRP/"+ARCHIVE);
        require(pages.kind.equals("PCK"),"credits require a PCK index");
        for(Archive.Entry entry:pages.entries){
            byte[] h=pages.source(entry).read(0,20);
            require(Binary.u32(h,0)==0x47585243L&&Binary.i16(h,4)==0&&Binary.i16(h,6)==0
                &&Binary.u16(h,8)==WIDTH&&Binary.u16(h,10)==HEIGHT+1,"credits strip geometry");
        }
        start(pages.entries.size(),seconds,mode);
    }
    /** Explicit numeric admission permits independently authored arithmetic fixtures. */
    public void start(int count,int seconds,int mode)throws IOException {
        require(count>=1&&count<=1000000&&seconds>=1&&seconds<=255&&mode>=0&&mode<=255,"credits parameters");
        float rate=nativeSpeed(count,seconds*1000);require(rate>0&&!Float.isInfinite(rate),"credits rate");
        long last=lastPage(count,seconds,rate),complete=last+5001+fadeMillis();
        strips=count;this.seconds=seconds;this.mode=mode;speed=rate;elapsed=0;lastPageAt=last;completionAt=complete;enabled=true;
    }
    private static long lastPage(int count,int seconds,float speed){
        int target=Math.max(1,count-1)*HEIGHT,low=0,high=seconds*2000;
        while(low<high){int middle=low+(high-low)/2;if(nativeDistance(middle,speed)>=target)high=middle;else low=middle+1;}
        return low;
    }
    private static long fadeMillis(){return(nativeFadeUpdates()*1000L+59)/60;}
    public boolean active(){return enabled;}
    public int strips(){return strips;}
    public int mode(){return mode;}
    public float speed(){return speed;}
    public long elapsedMillis(){return elapsed;}
    public long scrollDurationMillis(){return seconds*1000L;}
    public long lastPageMillis(){return lastPageAt;}
    public long completionMillis(){return completionAt;}
    public long remainingMillis(){return enabled?completionAt-elapsed:0;}
    public int completionFlag(){return mode==1?110:111;}
    public int currentPage(){return strips==0?-1:Math.min(strips-1,nativeDistance((int)Math.min(elapsed,lastPageAt),speed)/HEIGHT);}
    public int nextPage(){int page=currentPage();return page<0||page+1>=strips?-1:page+1;}
    public int offset(){return strips==0||elapsed>=lastPageAt?0:nativeDistance((int)elapsed,speed)%HEIGHT;}
    public boolean holding(){return strips!=0&&elapsed>=lastPageAt&&elapsed<=lastPageAt+5000;}
    public float fadeAlpha(){long time=elapsed-lastPageAt-5001;return time<=0?0:nativeFade((int)Math.min(1000,time*60/1000));}
    public String image(int page)throws IOException{require(page>=0&&page<strips,"credits page index");return"pck:"+ARCHIVE+"#"+page;}
    public Set<String> resources()throws IOException{
        Set<String> set=new LinkedHashSet<>();if(enabled){set.add(image(currentPage()));if(nextPage()>=0)set.add(image(nextPage()));}return set;
    }
    /** COMPLETE is delivered once; the host writes100:completionFlag()=1. */
    public Step elapse(long milliseconds){
        if(!enabled)return Step.INACTIVE;
        elapsed+=Math.min(Math.max(0,milliseconds),completionAt-elapsed);
        if(elapsed==completionAt){enabled=false;return Step.COMPLETE;}return Step.DRAW;
    }
    public void save(DataOutput out)throws IOException{
        out.writeInt(MAGIC);out.writeInt(strips);out.writeInt(seconds);out.writeInt(mode);out.writeInt(Float.floatToIntBits(speed));out.writeLong(elapsed);out.writeBoolean(enabled);
    }
    public void load(DataInput in)throws IOException{
        require(in.readInt()==MAGIC,"credits snapshot format");int count=in.readInt(),sec=in.readInt(),kind=in.readInt(),bits=in.readInt();long time=in.readLong();boolean active=in.readBoolean();
        if(count==0){require(sec==0&&kind==0&&bits==0&&time==0&&!active,"credits idle snapshot");strips=seconds=mode=0;speed=0;elapsed=lastPageAt=completionAt=0;enabled=false;return;}
        require(count>=1&&count<=1000000&&sec>=1&&sec<=255&&kind>=0&&kind<=255,"credits snapshot parameters");
        float rate=nativeSpeed(count,sec*1000);long last=lastPage(count,sec,rate),complete=last+5001+fadeMillis();
        require(bits==Float.floatToIntBits(rate)&&time>=0&&time<=complete&&active==(time<complete),"credits snapshot clock/rate");
        strips=count;seconds=sec;mode=kind;speed=rate;elapsed=time;lastPageAt=last;completionAt=complete;enabled=active;
    }
    private static void require(boolean ok,String what)throws IOException{if(!ok)throw new IOException("Invalid PH "+what);}
}
