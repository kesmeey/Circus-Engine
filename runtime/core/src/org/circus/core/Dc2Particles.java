package org.circus.core;

import java.io.*;
import java.util.*;
import java.util.function.IntSupplier;

/** Current DC2 411670/411D60/412050/412250. Each slot is the native 25-DWORD record. */
public final class Dc2Particles {
    private static final int[] COUNTS={0,5,10,15,30},FRAMES={7,7,6,8,5,8};
    private static final int[] COS=new int[4096];
    static {for(int n=0;n<COS.length;n++)COS[n]=(int)(4096*StrictMath.cos(n*2*StrictMath.PI/4096));}
    private final IntSupplier random;
    private final int[][] slots=new int[50][25];
    private int kind,strength,busy,toggle,windKind,windX;
    private String atlas="";
    private boolean timer;
    private long remainder;
    public Dc2Particles(IntSupplier random){this.random=random;}
    public int kind(){return kind;}
    public int strength(){return strength;}
    public String atlas(){return atlas;}
    public boolean timerActive(){return timer;}
    public long remainder(){return remainder;}
    public long untilTick(){return timer?40-remainder:Long.MAX_VALUE;}
    public int busy(){return busy;}
    public int toggle(){return toggle;}
    public int windKind(){return windKind;}
    public int windX(){return windX;}
    public boolean loaded(){return !atlas.isEmpty();}
    public int[] slot(int index){return slots[index].clone();}
    public static boolean spriteKind(int value){return value==1||value==3||value==4||value==5||value==6;}
    private static String resource(int kind){switch(kind){case 1:return "system\\SACRA01";case 3:return "system\\YUKI01";case 4:return "system\\SACRA02";case 5:return "system\\SAKU_YUKI01";case 6:return "system\\SAKU_YUKI02";default:return "";}}
    private int rand(){if(random==null)throw new IllegalStateException("Frozen DC2 particle state cannot advance RNG");return random.getAsInt();}
    /** Raw 2A includes the FF busy setter outside 411670. */
    public void command(int requested,int value,boolean enabled)throws IOException {
        if(requested==255){busy=value;return;}
        if(requested==2)throw new IOException("DC2 native rain 412380/4124C0 is not the sprite particle kernel");
        if(requested==0){kind=0;release();busy=0;return;}
        if(!spriteKind(requested)){busy=0;return;} // 411670 default; raw2A still clears busy.
        if(value<0)throw new IOException("Negative DC2 particle strength would read before native45E75C");
        if(kind!=0&&kind!=requested)release();kind=requested;strength=value;busy=0;
        if(enabled){if(atlas.isEmpty())atlas=resource(kind);initialize(value,kind==3?4:0);if(!timer){timer=true;remainder=0;}}
    }
    private void release(){for(int[] slot:slots)slot[2]=0;atlas="";}
    private void initialize(int level,int variant){
        for(int[] slot:slots)Arrays.fill(slot,0);int count=level<5?COUNTS[level]:50;
        for(int n=0;n<count;n++){
            int[] p=slots[n];p[2]=1;p[5]=rand()%800;p[6]=-32-rand()%300;p[3]=p[5];p[4]=p[6];
            int row=rand()%6;if(variant==4)row=5;p[0]=row;p[9]=32;p[10]=24;
            if(row<3){p[18]=0;p[21]=rand()%24+24;}
            else if(row==3){p[18]=rand()%2;p[21]=rand()%16+16;}
            else{p[18]=1;p[21]=rand()%8+8;}
            p[14]=variant==4?rand()%4+2:rand()%8+4;p[22]=rand()%8+4;p[15]=rand()%8+4;
        }
        windKind=variant==4?1:0;windX=variant==4?2:0;
    }
    /** Timer phase survives disable, suspend, reconfiguration and stop. No render call advances it. */
    public void elapse(long milliseconds,boolean enabled,boolean externallyBusy,boolean dirty){
        if(!timer)return;long left=Math.max(0,milliseconds);
        while(left>=40-remainder){left-=40-remainder;remainder=0;if(enabled&&busy==0&&!externallyBusy&&!dirty&&kind!=0)tick();}
        remainder+=left;
    }
    public static int cosine(int degrees,int amplitude,int base){int phase=(degrees%360)*4096/360;if(phase<0)throw new IllegalStateException("Native DC2 cosine table underflow");return COS[phase]*amplitude/4096+base;}
    public static int cosineEntry(int index){return COS[index];}
    private void tick(){
        if(toggle==1){toggle=0;return;}toggle=1;
        for(int[] p:slots)if(p[2]!=0){
            p[5]+=windX;p[3]=p[5]+cosine(p[24],p[21],0);p[4]+=p[14];p[24]+=p[22];
            if(p[4]>=600||windKind==1&&p[3]>=800||windKind==2&&p[3]+32<0){
                if(windKind==1){if(rand()%2==0){p[5]=rand()%800;p[6]=-32-rand()%64;}else{p[5]=-32-rand()%64;p[6]=rand()%400;}}
                else if(windKind==2){if(rand()%2==0){p[5]=rand()%800;p[6]=rand()%64-64;}else{p[5]=rand()%64+800;p[6]=rand()%400;}}
                else{p[5]=rand()%800;p[6]=-rand()%300;}
                p[3]=p[5];p[4]=p[6];p[24]=0;
            }
            if(++p[16]>=p[15]){p[16]=0;if(++p[19]>=FRAMES[p[0]])p[19]=0;}
        }
    }
    /** Native keyed fixed-weight blend. RGBA's nonzero alpha is a mask, not a second opacity. */
    public void paint(int[] canvas,int width,int height,int[] image,int imageWidth,int imageHeight,int bits,int layer){
        if(atlas.isEmpty())return;int weight=kind==3?12:8;
        for(int[] p:slots)if(p[2]!=0&&p[18]==layer){
            int sx=p[19]*32,sy=p[0]*28;
            for(int row=0;row<24;row++){long dy=(long)p[4]+row;int ay=sy+row;if(dy<0||dy>=height||ay<0||ay>=imageHeight)continue;
                for(int col=0;col<32;col++){long dx=(long)p[3]+col;int ax=sx+col;if(dx<0||dx>=width||ax<0||ax>=imageWidth)continue;int color=image[ay*imageWidth+ax];
                    if(bits==32?color>>>24==0:(color&0xffffff)==0xff00ff)continue;int index=(int)dy*width+(int)dx;canvas[index]=Dc2Motion.blend(canvas[index],color,weight);
                }
            }
        }
    }
    public void save(DataOutput out)throws IOException{
        out.writeInt(0x44435031);out.writeInt(kind);out.writeInt(strength);out.writeInt(busy);out.writeUTF(atlas);out.writeBoolean(timer);out.writeLong(remainder);out.writeInt(toggle);out.writeInt(windKind);out.writeInt(windX);for(int[] p:slots)for(int value:p)out.writeInt(value);
    }
    public static Dc2Particles load(DataInput in,IntSupplier random)throws IOException{
        if(in.readInt()!=0x44435031)throw new IOException("DC2 particle state version");Dc2Particles result=new Dc2Particles(random);result.kind=in.readInt();result.strength=in.readInt();result.busy=in.readInt();result.atlas=in.readUTF();result.timer=in.readBoolean();result.remainder=in.readLong();result.toggle=in.readInt();result.windKind=in.readInt();result.windX=in.readInt();
        if(result.kind!=0&&!spriteKind(result.kind)||result.strength<0||result.remainder<0||result.remainder>=40||!result.timer&&result.remainder!=0||result.toggle<0||result.toggle>1||result.windKind<0||result.windKind>2||!result.atlas.isEmpty()&&!result.atlas.equals(resource(result.kind)))throw new IOException("Invalid DC2 particle header");
        for(int[] p:result.slots){for(int n=0;n<p.length;n++)p[n]=in.readInt();if(p[2]!=0&&p[2]!=1)throw new IOException("DC2 particle active flag");if(p[2]!=0&&(p[0]<0||p[0]>=6||p[18]<0||p[18]>1||p[9]!=32||p[10]!=24||p[14]<=0||p[15]<=0||p[16]<0||p[16]>=p[15]||p[19]<0||p[19]>=FRAMES[p[0]]||p[24]<0))throw new IOException("Invalid DC2 particle slot");}
        return result;
    }
    public Dc2Particles frozenCopy()throws IOException{ByteArrayOutputStream bytes=new ByteArrayOutputStream();save(new DataOutputStream(bytes));return load(new DataInputStream(new ByteArrayInputStream(bytes.toByteArray())),null);}
}
