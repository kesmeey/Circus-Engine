package org.circus.core;

import java.io.*;
import java.util.*;

/** Original DC2 wait32/418A40. Frozen scene/window inputs and the literal
 * forty-millisecond feedback/full-copy journal, separate from actor61 motion. */
public final class Dc2ActorChange {
    public static final int FULL=0,FEEDBACK=1;
    public static final class Draw {public final int kind,alpha;Draw(int k,int a){kind=k;alpha=a;}}
    public final int[] staged=new int[4];
    public boolean active,preparing,drawn;
    public int phase,sceneSerial;
    public long generation;
    public UiCapture initial,target;
    public Dc2MessageWindow initialTemplate,targetTemplate;
    private int timerPhase;private boolean first=true;
    private final List<Draw> journal=new ArrayList<>();
    public List<Draw> draws(){return Collections.unmodifiableList(journal);}
    public void start(UiCapture oldScene,UiCapture newScene,Dc2MessageWindow oldTemplate,Dc2MessageWindow newTemplate,int serial,boolean prepare)throws IOException{
        if(oldScene==null||newScene==null||generation==Long.MAX_VALUE)throw new IOException("DC2 actor change frozen inputs");clear();generation++;initial=oldScene;target=newScene;initialTemplate=oldTemplate;targetTemplate=newTemplate;sceneSerial=serial;preparing=prepare;active=true;
    }
    public void afterPreparation(UiCapture preparedScene,UiCapture newScene,int serial)throws IOException{if(!active||!preparing||preparedScene==null||newScene==null)throw new IOException("DC2 actor preparation owner");initial=preparedScene;target=newScene;initialTemplate=targetTemplate=null;sceneSerial=serial;preparing=false;}
    /** stage60 is live engine storage, and survives clearing the render owner. */
    public void clear(){active=preparing=drawn=false;phase=sceneSerial=timerPhase=0;first=true;initial=target=null;initialTemplate=targetTemplate=null;journal.clear();}
    /** Returns true only for a new release. Reaching phase5 by a normal copy
     * still waits until a later poll; the native no-temp fast branch releases now. */
    public boolean advance(long millis,int speed,boolean rapid){
        if(!active||preparing)return false;long delta=Math.max(0,millis);boolean due=first||delta>=40||timerPhase+delta%40>=40;timerPhase=(int)((timerPhase+delta%40)%40);if(!due)return false;first=false;
        if(phase==5){active=false;return true;}
        if(phase==0&&(speed==3||rapid)){phase=5;drawn=true;journal.add(new Draw(FULL,0));active=false;return true;}
        phase=Math.min(5,phase+(speed==1?2:speed==2?3:1));drawn=true;journal.add(new Draw(phase==5?FULL:FEEDBACK,speed==1?10:speed==2?12:6));return false;
    }
    public void save(DataOutput out)throws IOException{out.writeInt(0x44325831);for(int n:staged)out.writeInt(n);out.writeBoolean(active);out.writeBoolean(preparing);out.writeBoolean(drawn);out.writeInt(phase);out.writeInt(sceneSerial);out.writeLong(generation);out.writeInt(timerPhase);out.writeBoolean(first);UiCapture.save(out,initial);UiCapture.save(out,target);Dc2MessageWindow.save(out,initialTemplate);Dc2MessageWindow.save(out,targetTemplate);out.writeInt(journal.size());for(Draw d:journal){out.writeByte(d.kind);out.writeByte(d.alpha);}}
    public void load(DataInput in)throws IOException{
        if(in.readInt()!=0x44325831)throw new IOException("DC2 actor change tag");int[] xy=new int[4];for(int n=0;n<4;n++)xy[n]=in.readInt();boolean a=in.readBoolean(),p=in.readBoolean(),d=in.readBoolean();int ph=in.readInt(),serial=in.readInt();long gen=in.readLong();int clock=in.readInt();boolean f=in.readBoolean();UiCapture old=UiCapture.load(in),next=UiCapture.load(in);Dc2MessageWindow oldWindow=Dc2MessageWindow.load(in),nextWindow=Dc2MessageWindow.load(in);int count=in.readInt();if(ph<0||ph>5||gen<0||clock<0||clock>=40||count<0||count>5||p&&!a||(a||d)&&(old==null||next==null)||d!=(count!=0)||p&&(ph!=0||d))throw new IOException("DC2 actor change state");ArrayList<Draw> draws=new ArrayList<>();for(int n=0;n<count;n++){int kind=in.readUnsignedByte(),alpha=in.readUnsignedByte();if(kind<0||kind>1||alpha!=0&&alpha!=6&&alpha!=10&&alpha!=12)throw new IOException("DC2 actor change draw");draws.add(new Draw(kind,alpha));}
        System.arraycopy(xy,0,staged,0,4);active=a;preparing=p;drawn=d;phase=ph;sceneSerial=serial;generation=gen;timerPhase=clock;first=f;initial=old;target=next;initialTemplate=oldWindow;targetTemplate=nextWindow;journal.clear();journal.addAll(draws);
    }
}
