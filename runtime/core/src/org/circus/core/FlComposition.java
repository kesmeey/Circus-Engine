package org.circus.core;

import java.io.*;
import java.util.*;

/** FL421D80 prepared-surface feedback and4202D0 wait2B actor feedback.
 * 40C220 changes only clipping. Neither phase clears the framebuffer. */
public final class FlComposition {
    public static final int FULL=0,FEEDBACK=1;
    public static final class Draw {
        public final int kind,alpha,left,top,right,bottom;
        /** Select the prepared background instead of the final actor target. */
        public final boolean prepared;
        private Draw(int kind,int alpha,boolean prepared,int l,int t,int r,int b){this.kind=kind;this.alpha=alpha;this.prepared=prepared;left=l;top=t;right=r;bottom=b;}
    }
    public UiCapture initial,target,prepared;
    public boolean active,drawn;
    public long generation;
    public int phase,progress,sourceSerial;
    private boolean actorPhase,firstPoll=true;
    private int left,top,right,bottom,pollPhase;
    private final List<Draw> journal=new ArrayList<Draw>();
    public List<Draw> draws(){return Collections.unmodifiableList(journal);}
    public void clear(){active=drawn=false;initial=target=prepared=null;journal.clear();phase=progress=pollPhase=0;firstPoll=true;actorPhase=false;}
    public void start(UiCapture old,UiCapture target,int serial)throws IOException{start(old,target,null,serial,true,0,0,800,600);}
    public void start(UiCapture old,UiCapture target,UiCapture prepared,int serial,boolean actors,int l,int t,int r,int b)throws IOException {
        if(old==null||target==null||generation==Long.MAX_VALUE||l<0||t<0||r>800||b>600||l>r||t>b||!actors&&prepared==null)throw new IOException("FL composition sources/clip");
        clear();generation++;initial=old;this.target=target;this.prepared=prepared;sourceSerial=serial;actorPhase=actors;phase=prepared==null?1:0;left=l;top=t;right=r;bottom=b;active=true;
    }
    private void draw(int kind,int alpha,boolean background){journal.add(new Draw(kind,alpha,background,background?0:left,background?0:top,background?800:right,background?600:bottom));drawn=true;}
    /** At most one native poll for each late event, retaining a faster frontend's sub40ms phase. */
    public void advance(long ms,int speed,boolean ctrl){
        if(!active)return;long delta=Math.max(0,ms);boolean due=firstPoll||delta>=40||pollPhase+delta%40>=40;pollPhase=(int)((pollPhase+delta%40)%40);if(!due)return;firstPoll=false;poll(speed,ctrl);
    }
    private void poll(int speed,boolean ctrl){
        if(phase==0){
            int step=speed==3||ctrl?8:speed==2?4:speed==1?2:1,alpha=speed==3||ctrl?10:speed==2?8:speed==1?6:3;
            progress=Math.min(16,progress+step);draw(FEEDBACK,alpha,true);
            if(progress==16){draw(FULL,16,true);if(actorPhase){phase=1;progress=0;firstPoll=true;}else{active=false;phase=2;}}
            return;
        }
        if(progress==5){active=false;phase=2;return;}
        if(speed==3||ctrl){progress=5;draw(FULL,16,false);active=false;phase=2;return;}
        progress=Math.min(5,progress+(speed==2?3:speed==1?2:1));draw(progress==5?FULL:FEEDBACK,progress==5?16:speed==2?12:speed==1?10:6,false);
    }
    public void save(DataOutput out)throws IOException {
        out.writeInt(0x46434d31);out.writeBoolean(active);out.writeBoolean(drawn);out.writeLong(generation);out.writeInt(phase);out.writeInt(progress);out.writeInt(sourceSerial);out.writeBoolean(actorPhase);out.writeBoolean(firstPoll);out.writeInt(left);out.writeInt(top);out.writeInt(right);out.writeInt(bottom);out.writeInt(pollPhase);
        UiCapture.save(out,initial);UiCapture.save(out,target);UiCapture.save(out,prepared);out.writeInt(journal.size());for(Draw d:journal){out.writeInt(d.kind);out.writeInt(d.alpha);out.writeBoolean(d.prepared);out.writeInt(d.left);out.writeInt(d.top);out.writeInt(d.right);out.writeInt(d.bottom);}
    }
    public void load(DataInput in)throws IOException {
        if(in.readInt()!=0x46434d31)throw new IOException("FL composition snapshot version");boolean a=in.readBoolean(),d=in.readBoolean();long g=in.readLong();int p=in.readInt(),n=in.readInt(),serial=in.readInt();boolean actors=in.readBoolean(),first=in.readBoolean();int l=in.readInt(),t=in.readInt(),r=in.readInt(),b=in.readInt(),timer=in.readInt();
        UiCapture old=UiCapture.load(in),next=UiCapture.load(in),bg=UiCapture.load(in);int count=in.readInt();
        if(g<0||p<0||p>2||n<0||n>16||p==1&&n>5||l<0||t<0||r>800||b>600||l>r||t>b||timer<0||timer>=40||count<0||count>64||d!=(count!=0)||a&&(old==null||next==null)||a&&p==0&&bg==null)throw new IOException("FL composition state");
        List<Draw> values=new ArrayList<Draw>();for(int i=0;i<count;i++){int kind=in.readInt(),alpha=in.readInt();boolean background=in.readBoolean();int dl=in.readInt(),dt=in.readInt(),dr=in.readInt(),db=in.readInt();if(kind<0||kind>1||alpha<0||alpha>16||kind==FULL&&alpha!=16||background&&bg==null||dl<0||dt<0||dr>800||db>600||dl>dr||dt>db)throw new IOException("FL composition draw");values.add(new Draw(kind,alpha,background,dl,dt,dr,db));}
        active=a;drawn=d;generation=g;phase=p;progress=n;sourceSerial=serial;actorPhase=actors;firstPoll=first;left=l;top=t;right=r;bottom=b;pollPhase=timer;initial=old;target=next;prepared=bg;journal.clear();journal.addAll(values);
    }
}
