package org.circus.core;
import java.io.*;
import java.util.*;

/** DM resource slots (4DA0E8); UI slots 21..25 survive ordinary transfers. */
public final class Dc2Scene {
    public final String[] slots=new String[28];
    private final int[][] characters=new int[4][6];
    private int portableBackgroundX,portableBackgroundY;
    public interface LiveCoordinates {
        int background(int axis);void background(int axis,int value);
        int actor(int actor,int axis);void actor(int actor,int axis,int value);
    }
    private LiveCoordinates coordinates;
    /** Coordinate bytes belong to a native frame. Binding does not copy the
     *portable cache into that frame or turn a coordinate write into a repaint. */
    public void bindCoordinates(LiveCoordinates owner){if(owner==null||coordinates!=null)throw new IllegalArgumentException("Scene coordinate owner missing/already bound");coordinates=owner;}
    public int backgroundX(){return coordinates==null?portableBackgroundX:coordinates.background(0);}
    public int backgroundY(){return coordinates==null?portableBackgroundY:coordinates.background(1);}
    public void backgroundX(int value){if(coordinates==null)portableBackgroundX=value;else coordinates.background(0,value);}
    public void backgroundY(int value){if(coordinates==null)portableBackgroundY=value;else coordinates.background(1,value);}
    public int character(int actor,int field){return coordinates!=null&&actor>=0&&actor<2&&field>=0&&field<2?coordinates.actor(actor,field):characters[actor][field];}
    public void character(int actor,int field,int value){if(coordinates!=null&&actor>=0&&actor<2&&field>=0&&field<2)coordinates.actor(actor,field,value);else characters[actor][field]=value;}
    public int[] character(int actor){int[] value=characters[actor].clone();if(coordinates!=null&&actor<2){value[0]=coordinates.actor(actor,0);value[1]=coordinates.actor(actor,1);}return value;}
    public void character(int actor,int[] value){characterPrefix(actor,value,value.length);}
    public void characterPrefix(int actor,int[] value,int count){if(value==null||count<0||count>6||count>value.length)throw new IllegalArgumentException("Actor coordinate prefix");for(int n=0;n<count;n++)character(actor,n,value[n]);}
    public int mode,targetX,targetY,scrollStep,scrollPeriod;
    public int portraitX,portraitY,transitionKind,transitionSerial;
    public long transitionDuration,transitionRemaining;
    public final int[] actorEffect=new int[4];
    public int particleKind,particleStrength,overlayKind,overlayStrength;public long effectClock;public boolean effectsFrozen;
    public void effect(int kind,int strength)throws IOException {
        if(kind==255){effectsFrozen=strength!=0;return;}
        if(kind==0){particleKind=particleStrength=overlayKind=overlayStrength=0;return;}
        if(kind==1||kind==2||kind==9){particleKind=strength==0?0:kind;particleStrength=strength;effectClock=0;}
        else if(kind==7){overlayKind=strength==0?0:kind;overlayStrength=strength;}
        else throw new IOException("Unimplemented DM procedural effect "+kind+","+strength);
        effectsFrozen=false;
    }
    public Frame shown=new Frame(),previous=new Frame();
    /** Only dc2-3363 uses 418890; DM/PC1/PC2 retain their independently bound scrolls. */
    public Dc2Motion nativeMotion;
    public Dc2Particles nativeParticles;
    public static final class Frame {
        public String[] slots=new String[28];public int[][] actors=new int[4][6];public List<Dc2Ui.Quad> quads=new ArrayList<>();public int x,y;
        Frame(){Arrays.fill(slots,"");}
        void save(DataOutput o)throws IOException{for(String s:slots)o.writeUTF(s);for(int[] a:actors)for(int v:a)o.writeInt(v);Dc2Ui.saveQuads(o,quads);o.writeInt(x);o.writeInt(y);}
        void load(DataInput i)throws IOException{for(int n=0;n<28;n++)slots[n]=i.readUTF();for(int[] a:actors)for(int n=0;n<6;n++)a[n]=i.readInt();quads=Dc2Ui.loadQuads(i,2048);x=i.readInt();y=i.readInt();}
    }
    /** Native PC2-family stage coordinates are not the displayed actor state.
     * Project the host-owned live fields before any capture can observe a frame. */
    public interface ActorProjection {void project(Frame frame);}
    private ActorProjection actorProjection;
    public void bindActorProjection(ActorProjection projection){if(projection==null||actorProjection!=null&&actorProjection!=projection)throw new IllegalArgumentException("Actor projection owner already bound");actorProjection=projection;}
    public void publish(Dc2Ui ui,int kind,long duration)throws IOException {
        nativeMotion=null;
        previous=shown;Frame f=new Frame();f.slots=slots.clone();for(int n=0;n<4;n++)f.actors[n]=character(n);f.quads=Dc2Ui.copyQuads(ui.quads);f.x=backgroundX();f.y=backgroundY();if(actorProjection!=null)actorProjection.project(f);shown=f;mode=0;
        transitionKind=kind;transitionDuration=transitionRemaining=duration;transitionSerial++;revision++;
    }
    public long scrollClock;
    public long revision;
    public Dc2Scene(){Arrays.fill(slots,"");for(int[] c:characters)c[2]=100;}
    public void reset(){Arrays.fill(slots,0,21,"");mode=0;nativeMotion=null;revision++;}
    public void elapse(long ms){if(!effectsFrozen)effectClock+=ms;transitionRemaining=Math.max(0,transitionRemaining-ms);if(scrollStep<=0)return;scrollClock+=ms;long ticks=scrollClock/scrollPeriod;scrollClock%=scrollPeriod;long distance=Math.min(Integer.MAX_VALUE,ticks*scrollStep);backgroundX(approach(backgroundX(),targetX,distance));backgroundY(approach(backgroundY(),targetY,distance));shown.x=backgroundX();shown.y=backgroundY();if(backgroundX()==targetX&&backgroundY()==targetY)scrollStep=0;}
    private static int approach(int from,int to,long distance){return from<to?(int)Math.min(to,from+distance):(int)Math.max(to,from-distance);}
    public void save(DataOutput o)throws IOException{o.writeInt(particleKind);o.writeInt(particleStrength);o.writeInt(overlayKind);o.writeInt(overlayStrength);o.writeLong(effectClock);o.writeBoolean(effectsFrozen);for(String s:slots)o.writeUTF(s);for(int a=0;a<4;a++)for(int n=0;n<6;n++)o.writeInt(coordinates!=null&&a<2&&n<2?0:characters[a][n]);for(int v:new int[]{coordinates==null?portableBackgroundX:0,coordinates==null?portableBackgroundY:0,mode,targetX,targetY,scrollStep,scrollPeriod})o.writeInt(v);o.writeLong(scrollClock);o.writeLong(revision);o.writeInt(portraitX);o.writeInt(portraitY);o.writeInt(transitionKind);o.writeInt(transitionSerial);o.writeLong(transitionDuration);o.writeLong(transitionRemaining);for(int v:actorEffect)o.writeInt(v);shown.save(o);previous.save(o);}
    public void load(DataInput i)throws IOException{particleKind=i.readInt();particleStrength=i.readInt();overlayKind=i.readInt();overlayStrength=i.readInt();effectClock=i.readLong();effectsFrozen=i.readBoolean();for(int n=0;n<slots.length;n++)slots[n]=i.readUTF();for(int a=0;a<4;a++)for(int n=0;n<6;n++){int value=i.readInt();if(coordinates==null||a>=2||n>=2)characters[a][n]=value;}int x=i.readInt(),y=i.readInt();if(coordinates==null){portableBackgroundX=x;portableBackgroundY=y;}mode=i.readInt();targetX=i.readInt();targetY=i.readInt();scrollStep=i.readInt();scrollPeriod=i.readInt();scrollClock=i.readLong();revision=i.readLong();portraitX=i.readInt();portraitY=i.readInt();transitionKind=i.readInt();transitionSerial=i.readInt();transitionDuration=i.readLong();transitionRemaining=i.readLong();for(int n=0;n<4;n++)actorEffect[n]=i.readInt();shown.load(i);previous.load(i);if(scrollStep<0||scrollStep>0&&scrollPeriod<=0)throw new IOException("Invalid DM scroll");}
}
