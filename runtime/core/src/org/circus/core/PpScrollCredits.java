package org.circus.core;

import java.io.*;
import java.util.*;

/** PP40EF70/40F050 plain PCK roll. This portable owner retains the native
 *32-bit clock/product arithmetic, successive-page cache and EOF-later-update
 *completion. Renderer/COM objects and their failure prefixes are external. */
public final class PpScrollCredits {
    private static final int FORMAT=0x50505231;
    public static final int WIDTH=1024,HEIGHT=600;
    public enum Step { INACTIVE,DRAW,COMPLETE }
    private String archive="";
    private int strips,speed,startTick,tick,lastPage=-1,current=-1,next=-1,offset;
    private boolean enabled,finishing;
    /** Old host snapshots did not have this owner. Restore the unstarted state. */
    public void reset(){archive="";strips=speed=startTick=tick=offset=0;lastPage=current=next=-1;enabled=finishing=false;}
    /** The original count*600000 IMUL wraps before signed /274000. */
    public static int nativeSpeed(int count){return(count*600000)/274000;}
    /** IMUL low32, then the original unsigned /1000. */
    public static int nativeDistance(int elapsed,int speed){long product=((elapsed&0xffffffffL)*(speed&0xffffffffL))&0xffffffffL;return(int)(product/1000);}
    public void start(CircusResources data,String source)throws IOException{
        if(data==null)throw new IOException("PP scroll credits require actual resources");
        String path=Binary.nativeResourcePath(source);Archive pages=data.files.archive("AdvData/GRP/"+path);
        if(!pages.kind.equals("PCK"))throw new IOException("PP portable roll currently requires the admitted PCK layout");
        start(path,pages.entries.size(),0);
    }
    /** Explicit numeric entry also supports independent original-program
     *fixtures. Caller archive strings are real relative resource paths. */
    public void start(String source,int count,int clock)throws IOException{
        String path=Binary.nativeResourcePath(source);
        if(count<1||count>1000000)throw new IOException("PP scroll credits require a nonempty admitted archive");
        archive=path;strips=count;speed=nativeSpeed(count);startTick=tick=clock;
        current=next=lastPage=-1;offset=0;finishing=false;enabled=true;
    }
    public boolean active(){return enabled;}
    public boolean finishing(){return finishing;}
    public String archive(){return archive;}
    public int strips(){return strips;}
    public int speed(){return speed;}
    public int currentPage(){return current;}
    public int nextPage(){return next;}
    public int requestedPage(){return lastPage;}
    public int offset(){return offset;}
    /** Native caller emits persist100:29=1 and clears wait after COMPLETE.
     *EOF discovery still draws that update; completion occurs on the next. */
    public Step update(int clock){
        if(!enabled)return Step.INACTIVE;
        tick=clock;
        if(finishing){enabled=false;return Step.COMPLETE;}
        int pixels=nativeDistance(clock-startTick,speed),page=pixels/HEIGHT;offset=pixels-page*HEIGHT;
        if(page!=lastPage){lastPage=page;current=next;next=-1;if(page<strips)next=page;else{finishing=true;offset=0;}}
        return Step.DRAW;
    }
    public Step elapse(long milliseconds){return update(tick+(int)Math.max(0,milliseconds));}
    /** 40F050(-1): caller starts the native BGM fade before the shared finish. */
    public boolean skip(){if(!enabled)return false;enabled=false;return true;}
    public String image(int page)throws IOException{if(page<0||page>=strips)throw new IOException("PP scroll image outside admitted archive");return "pck:"+archive+"#"+page;}
    public Set<String> resources()throws IOException{Set<String> resources=new LinkedHashSet<>();if(enabled){if(current>=0)resources.add(image(current));if(next>=0)resources.add(image(next));}return resources;}
    public void save(DataOutput out)throws IOException{out.writeInt(FORMAT);out.writeUTF(archive);out.writeInt(strips);out.writeInt(speed);out.writeInt(startTick);out.writeInt(tick);out.writeInt(lastPage);out.writeInt(current);out.writeInt(next);out.writeInt(offset);out.writeBoolean(enabled);out.writeBoolean(finishing);}
    public void load(DataInput in)throws IOException{
        if(in.readInt()!=FORMAT)throw new IOException("PP scroll credits format");
        String name=in.readUTF();int count=in.readInt(),rate=in.readInt(),start=in.readInt(),now=in.readInt(),last=in.readInt(),old=in.readInt(),upcoming=in.readInt(),row=in.readInt();boolean active=in.readBoolean(),done=in.readBoolean();
        boolean idle=name.isEmpty();
        if(idle){if(count!=0||rate!=0||start!=0||now!=0||last!=-1||old!=-1||upcoming!=-1||row!=0||active||done)throw new IOException("PP scroll credits idle state");}
        else{
            Binary.nativeResourcePath(name);
            if(count<1||count>1000000||rate!=nativeSpeed(count)||last< -1||last>7158||old< -1||old>=count||upcoming< -1||upcoming>=count||row<0||row>=HEIGHT||done&&last<count)throw new IOException("PP scroll credits state");
            if(last==-1&&(old!=-1||upcoming!=-1||row!=0||done||start!=now))throw new IOException("PP scroll credits unpolled state");
        }
        archive=name;strips=count;speed=rate;startTick=start;tick=now;lastPage=last;current=old;next=upcoming;offset=row;enabled=active;finishing=done;
    }
}
