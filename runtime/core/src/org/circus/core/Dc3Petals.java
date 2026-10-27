package org.circus.core;
import java.io.*;
import java.util.*;

/** DD effect 1: five layers of 28px animated falling sprites (41E290/41EFFA).
 * Random state is portable and saved; it is not synchronized to the native process RNG.
 */
public final class Dc3Petals {
    private static final int[] FRAMES={7,7,6,8,5,8};
    public static final int[] OBJECTS={6,19,29,39,59};
    public static final class Particle {public int group,row,frame,amplitude;public float baseX,y,speed,phase,alpha=.8f;public boolean rising=true;public long frameTime;}
    public final List<Particle> particles=new ArrayList<>();
    private int random=1;private long remainder,time;public int effect=1;
    private int next(){random=random*214013+2531011;return (random>>>16)&32767;}
    private void reset(Particle p){p.baseX=next()%1024;p.y=-(next()%576);p.speed=effect==3?(float)((next()%3)*2.5+.5):(float)(((next()&7)+1)/1.5);p.amplitude=effect==3?(next()&15)+4:(next()&31)+28;p.row=4-p.group+(next()&1);p.phase=next()%360;p.frame=next()%FRAMES[p.row];p.frameTime=time*1000/60;if(effect==3){p.alpha=(next()%10)/10f;p.rising=next()!=0;}else p.alpha=.8f;}
    public void start(int density){start(1,density);}
    /** 9 and 11 select the same native fallback as 1; effect 3 uses 41E4A0/41F12B. */
    public void start(int mode,int density){effect=mode;particles.clear();remainder=0;time=0;int count=Math.max(0,Math.min(6,density));for(int group=0;group<5;group++)for(int i=0;i<count;i++){Particle p=new Particle();p.group=group;reset(p);particles.add(p);}}
    public void elapse(long millis){if(particles.isEmpty())return;long ticks=(remainder+Math.max(0,millis)*60)/1000;remainder=(remainder+Math.max(0,millis)*60)%1000;
        // A suspended platform must not run an unbounded catch-up loop. The host
        // freezes engine time while paused; this cap also bounds malformed callers.
        ticks=Math.min(ticks,3600);
        for(long i=0;i<ticks;i++){time++;long ms=time*1000/60;for(Particle p:particles){p.phase+=.05f;p.y+=p.speed;if(ms-p.frameTime>500){p.frame=(p.frame+1)%FRAMES[p.row];p.frameTime=ms;}if(effect==3){p.alpha+=p.rising?.002f:-.002f;if(p.alpha>=.8f){p.alpha=.8f;p.rising=false;}else if(p.alpha<=0){p.alpha=0;p.rising=true;}}if(p.y>=576){reset(p);p.frameTime=ms;}}}
    }
    public float x(Particle p){return p.baseX+(float)Math.sin(p.phase)*p.amplitude;}
    public Dc3Petals copy(){Dc3Petals result=new Dc3Petals();try{ByteArrayOutputStream bytes=new ByteArrayOutputStream();save(new DataOutputStream(bytes));result.load(new DataInputStream(new ByteArrayInputStream(bytes.toByteArray())));}catch(IOException impossible){throw new IllegalStateException(impossible);}return result;}
    public void save(DataOutput out)throws IOException{out.writeInt(effect);out.writeInt(random);out.writeLong(remainder);out.writeLong(time);out.writeInt(particles.size());for(Particle p:particles){out.writeInt(p.group);out.writeInt(p.row);out.writeInt(p.frame);out.writeInt(p.amplitude);out.writeFloat(p.baseX);out.writeFloat(p.y);out.writeFloat(p.speed);out.writeFloat(p.phase);out.writeFloat(p.alpha);out.writeBoolean(p.rising);out.writeLong(p.frameTime);}}
    public void load(DataInput in)throws IOException{effect=in.readInt();random=in.readInt();remainder=in.readLong();time=in.readLong();int n=in.readInt();if((effect!=1&&effect!=3&&effect!=9&&effect!=11)||n<0||n>30||remainder<0||remainder>=1000||time<0)throw new IOException("Invalid particle state");particles.clear();for(int i=0;i<n;i++){Particle p=new Particle();p.group=in.readInt();p.row=in.readInt();p.frame=in.readInt();p.amplitude=in.readInt();p.baseX=in.readFloat();p.y=in.readFloat();p.speed=in.readFloat();p.phase=in.readFloat();p.alpha=in.readFloat();p.rising=in.readBoolean();p.frameTime=in.readLong();if(p.group<0||p.group>=5||p.row<0||p.row>=6||p.frame<0||p.frame>=FRAMES[p.row]||!Float.isFinite(p.phase)||!Float.isFinite(p.y)||!Float.isFinite(p.alpha)||p.alpha<0||p.alpha>1||p.speed<=0||p.speed>6||p.baseX<0||p.baseX>=1024||p.amplitude<4||p.amplitude>59)throw new IOException("Invalid particle");particles.add(p);}}
}
