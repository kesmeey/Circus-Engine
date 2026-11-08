package org.circus.core;

import java.io.*;
import java.util.*;

/** TY421E10 mode0: 17ms coalesced integer movement and retained front feedback.
 * 40DD30 only resets clipping; 40E020 mixes source with the existing BGR24 front. */
public final class TyScroll {
    public static final class Frame {
        public final String background;
        public final int x,y,weight;
        public final UiCapture compose;
        private Frame(String bg,int x,int y,int weight,UiCapture compose){this.background=bg;this.x=x;this.y=y;this.weight=weight;this.compose=compose;}
    }
    public interface Composer {UiCapture compose(int x,int y)throws IOException;}
    public UiCapture initial;
    public boolean active,visible;
    public long generation;
    public int sourceSerial,x,y,targetX,targetY,weight,holdPolls,pollPhase;
    private boolean firstPoll;
    private String background="";
    private final List<Frame> journal=new ArrayList<>();
    public List<Frame> draws(){return Collections.unmodifiableList(journal);}
    public void start(UiCapture old,String bg,int x,int y,int tx,int ty,int weight,int serial)throws IOException{
        if(old==null||bg==null||bg.isEmpty())throw new IOException("TY scroll requires physical initial front and background");
        if(generation==Long.MAX_VALUE)throw new IOException("TY scroll generation exhausted");
        clear();generation++;initial=old;background=bg;this.x=x;this.y=y;targetX=tx;targetY=ty;this.weight=Math.max(0,Math.min(16,weight));sourceSerial=serial;active=visible=true;firstPoll=true;
    }
    private static int approach(int value,int target,int step){return value<target?(int)Math.min((long)target,(long)value+step):value>target?(int)Math.max((long)target,(long)value-step):value;}
    public boolean advance(long ms,int speed,boolean ctrl,Composer composer)throws IOException{
        if(!active)return false;long delta=Math.max(0,ms);boolean due=firstPoll||delta>=17||pollPhase+delta%17>=17;pollPhase=(int)((pollPhase+delta%17)%17);if(!due)return false;firstPoll=false;
        if(x==targetX&&y==targetY){if(weight==0){active=false;return false;}holdPolls++;if(holdPolls+weight>16)weight=0;}
        int step=ctrl||speed==3?16:speed==2?8:speed==1?4:2;x=approach(x,targetX,step);y=approach(y,targetY,step);
        if(journal.size()>=100000)throw new IOException("TY scroll journal limit");UiCapture full=weight==0?composer.compose(x,y):null;if(weight==0&&full==null)throw new IOException("TY opaque scroll has no prepared composition");journal.add(new Frame(background,x,y,weight,full));return true;
    }
    public void clear(){initial=null;active=visible=firstPoll=false;background="";x=y=targetX=targetY=weight=holdPolls=pollPhase=sourceSerial=0;journal.clear();}
    public void save(DataOutput out)throws IOException{
        out.writeInt(0x54595331);out.writeLong(generation);out.writeBoolean(active);out.writeBoolean(visible);out.writeBoolean(firstPoll);out.writeInt(sourceSerial);out.writeUTF(background);for(int n:new int[]{x,y,targetX,targetY,weight,holdPolls,pollPhase})out.writeInt(n);UiCapture.save(out,initial);out.writeInt(journal.size());for(Frame f:journal){out.writeUTF(f.background);out.writeInt(f.x);out.writeInt(f.y);out.writeInt(f.weight);UiCapture.save(out,f.compose);}
    }
    public void load(DataInput in)throws IOException{
        if(in.readInt()!=0x54595331)throw new IOException("TY scroll state version");long g=in.readLong();boolean a=in.readBoolean(),v=in.readBoolean(),first=in.readBoolean();int serial=in.readInt();String bg=in.readUTF();int sx=in.readInt(),sy=in.readInt(),tx=in.readInt(),ty=in.readInt(),w=in.readInt(),hold=in.readInt(),phase=in.readInt();UiCapture old=UiCapture.load(in);int count=in.readInt();if(g<0||w<0||w>16||hold<0||hold>16||phase<0||phase>=17||count<0||count>100000||a&&!v||v&&(old==null||bg.isEmpty()))throw new IOException("TY saved scroll state");
        ArrayList<Frame> frames=new ArrayList<>();for(int n=0;n<count;n++){String name=in.readUTF();int fx=in.readInt(),fy=in.readInt(),fw=in.readInt();UiCapture c=UiCapture.load(in);if(name.isEmpty()||fw<0||fw>16||(fw==0)!=(c!=null))throw new IOException("TY saved scroll draw");frames.add(new Frame(name,fx,fy,fw,c));}
        clear();generation=g;active=a;visible=v;firstPoll=first;sourceSerial=serial;background=bg;x=sx;y=sy;targetX=tx;targetY=ty;weight=w;holdPolls=hold;pollPhase=phase;initial=old;journal.addAll(frames);
    }
}
