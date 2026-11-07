package org.circus.core;

import java.io.*;
import java.util.*;

/** Original D.C.II 004057B0/00405890/00405A20, native wait 1C/type 2.
 * Each update draws the OLD page/row before advancing at most one page. The
 * owner retains that journal after completion for the following MES fade.
 * This is independent of DM's GED/PCK and PC2's continuous 31-strip roll. */
public final class Dc2OriginalCredits {
    public static final int PAGES=19,WIDTH=800,HEIGHT=600;
    // 00405872: f32(11400 * f32[00429258]); the latter has bits 366DF986.
    public static final float SPEED=Float.intBitsToFloat(0x3d25953e);
    private static final long U32=0xffffffffL;
    private static final int MAX_ROW=nativeRow(U32);
    public boolean active,drawn;
    public int variant,page,row,drawPage,drawRow;
    private int skip;
    private long clock,baseClock;
    private int pollPhase;
    private boolean firstPoll=true;

    /** An opaque, already viewport-clipped blit. Native 408040 ignores CRX's
     * origin; resource pixels at srcX/srcY are copied to screen x/y. The
     * presenter restores its frozen native background before these blits. */
    public static final class Strip {
        public final String resource;
        public final int x,y,srcX,srcY,width,height;
        public final boolean opaque=true;
        private Strip(String name,int y,int sy,int height){resource=name;x=srcX=0;this.y=y;srcY=sy;width=WIDTH;this.height=height;}
    }
    public void start(int mode)throws IOException {
        if(mode<0||mode>1)throw new IOException("Original D.C.II credits variant "+mode);
        clear();variant=mode;active=true;
    }
    public void clear(){active=drawn=false;variant=page=row=drawPage=drawRow=skip=0;clock=baseClock=0;pollPhase=0;firstPoll=true;}
    public int skipRequested(){return skip;}
    public long elapsed(){return(clock-baseClock)&U32;}
    public String image(int n){
        if(n<0||n>=PAGES)throw new IllegalArgumentException("Original D.C.II credits page "+n);
        return String.format(Locale.ROOT,"ed/Gend_%s%02d",variant==1?"skr_":"",n+1);
    }
    /** Only native mouse button codes 1/2 reach 405A20. Neither auto nor a
     * generic ADVANCE signal may call this. 100:59 is read, never written. */
    public void input(int button,int persisted10059){
        if(active&&(button==1||button==2)&&(persisted10059==1||persisted10059==2))skip=persisted10059;
    }
    public void elapse(long millis){
        if(!active)return;
        clock=(clock+(Math.max(0,millis)&U32))&U32;
        firstPoll=false;pollPhase=0;poll();
    }
    /** WindowProc407162/4071A0 gates wait1C polls with4514AC=50ms.
     * Delayed timer events coalesce, so one call never catches up many draws.
     * Retain sub50ms phase when a faster portable frontend calls this. */
    public void advance(long millis){
        if(!active)return;long delta=Math.max(0,millis);clock=(clock+(delta&U32))&U32;
        boolean due=firstPoll||delta>=50||pollPhase+delta%50>=50;pollPhase=(int)((pollPhase+delta%50)%50);
        if(due){firstPoll=false;poll();}
    }
    private void poll(){
        drawn=true;drawPage=page;drawRow=row;
        if((page>=18&&row>400)||row>600){baseClock=clock;page++;row=0;}
        // x87 FILD unsigned32, FMUL f32 rate, then _ftol. The exact
        // 56-bit product fits x87's64-bit significand, but not Java double.
        row=nativeRow((clock-baseClock)&U32);
        if(page>=PAGES||skip==2||(skip==1&&variant==0))active=false;
    }
    private static int nativeRow(long unsignedMillis){return(int)((unsignedMillis*10851646L)>>>28);}
    /** Last native draw, including the completing update. Empty before its
     * first poll; an empty offscreen journal still restores the underlay. */
    public List<Strip> frame(){
        if(!drawn)return Collections.emptyList();
        if(drawPage>=18)return Collections.singletonList(new Strip(image(drawPage),0,0,HEIGHT));
        ArrayList<Strip> strips=new ArrayList<>(2);
        clipped(strips,drawPage,-(long)drawRow);
        clipped(strips,drawPage+1,HEIGHT-(long)drawRow);
        return Collections.unmodifiableList(strips);
    }
    private void clipped(List<Strip> out,int n,long top){
        long y=Math.max(0,top),bottom=Math.min(HEIGHT,top+HEIGHT);
        if(bottom>y)out.add(new Strip(image(n),(int)y,(int)(y-top),(int)(bottom-y)));
    }
    public void save(DataOutput out)throws IOException {
        out.writeInt(0x44324332);out.writeBoolean(active);out.writeBoolean(drawn);
        out.writeInt(variant);out.writeInt(page);out.writeInt(row);out.writeInt(drawPage);out.writeInt(drawRow);out.writeInt(skip);
        out.writeLong(clock);out.writeLong(baseClock);out.writeInt(pollPhase);out.writeBoolean(firstPoll);
    }
    /** Validate first so a rejected state cannot mutate a live owner. */
    public void load(DataInput in)throws IOException {
        int tag=in.readInt();if(tag!=0x44324331&&tag!=0x44324332)throw new IOException("Invalid original D.C.II credits tag");
        boolean a=in.readBoolean(),d=in.readBoolean();int v=in.readInt(),p=in.readInt(),r=in.readInt(),dp=in.readInt(),dr=in.readInt(),s=in.readInt();long c=in.readLong(),b=in.readLong();
        int phase=tag==0x44324332?in.readInt():0;boolean first=tag==0x44324332?in.readBoolean():!d;
        if(v<0||v>1||p<0||p>PAGES||r<0||r>MAX_ROW||dp<0||dp>=PAGES||dr<0||dr>MAX_ROW||s<0||s>2||c<0||c>U32||b<0||b>U32||a&&p==PAGES||phase<0||phase>=50||first&&d||!d&&(p!=0||r!=0||dp!=0||dr!=0||c!=0||b!=0))throw new IOException("Invalid original D.C.II credits state");
        active=a;drawn=d;variant=v;page=p;row=r;drawPage=dp;drawRow=dr;skip=s;clock=c;baseClock=b;pollPhase=phase;firstPoll=first;
    }
}
