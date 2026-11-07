package org.circus.core;
import java.io.*;
import java.util.*;

/** DD and WY staff-roll resources/camera parameters from their own executables.
 * This presentation adapter keeps its elapsed-time model; WY's native input,
 * sprite tween and per-update final fade are not a complete frame scheduler. */
public final class Dc3Credits {
    private static final String[] ROUTES={"RCC","SRR","HMN","SRA","AOI","MKT","SMM"};
    private static final String[] WY_ROUTES={"RCC","SRR","HMN","SRA","AOI","SKI","MKT","SMM","HTN"};
    private final boolean wy;
    public Dc3Credits(){wy=false;}
    public Dc3Credits(String profile){wy="dc3wy-a09f".equals(profile);}
    private String[] routes(){return wy?WY_ROUTES:ROUTES;}
    private int[][] pan(){return (wy?Dc3CreditsRules.WY_PAN:Dc3CreditsRules.PAN)[route];}
    private long routeDuration(int value){return wy?(value<=4?112000:97000):(value<=2?262000:265500);}
    private int fadeMillis(){return wy?3350:834;} // WY .005/update:201 rounded-float steps at compatibility60Hz.
    public int route=-1,pictures,strips;public long elapsed,duration;public boolean skippable;
    public void start(CircusResources data,int route,boolean skippable)throws IOException {
        if(route<0||route>=routes().length)throw new IOException("Unknown credits route "+route);this.route=route;this.skippable=skippable;elapsed=0;duration=routeDuration(route);
        pictures=data.files.archive("AdvData/GRP/"+archive("01")).entries.size();int second=data.files.archive("AdvData/GRP/"+archive("02")).entries.size();strips=data.files.archive("AdvData/GRP/"+archive("")).entries.size();
        if(pictures!=pan().length||second!=pictures||strips<2||strips>1000)throw new IOException("Credits archive layout does not match "+(wy?"WY":"DD"));
    }
    public String archive(String suffix){return wy&&suffix.isEmpty()?"ED/ED.PCK":"ED/ED"+routes()[route]+suffix+".PCK";}
    public String image(String suffix,int index){return "pck:"+archive(suffix)+"#"+index;}
    public int stripCropLeft(){return wy?20:0;}
    public int stripX(int sourceWidth){return wy?740-sourceWidth/2:0;}
    private long rollEnd(){return duration*(strips-1)/Math.max(1,strips);}
    public long end(){return rollEnd()+10000+fadeMillis();}
    public boolean active(){return route>=0&&elapsed<end();}
    public void elapse(long ms){if(active())elapsed=Math.min(end(),elapsed+Math.max(0,ms));}
    public double period(){return wy?(float)((float)(duration-(route<=4?6550:5800))/(float)Math.max(1,pictures)):(duration-13500)/(double)Math.max(1,pictures);}
    public int index(){return Math.min(pictures-1,(int)(elapsed/period()));}
    public float fraction(){return (float)((elapsed-index()*period())/(period()+2000));}
    public float x(){int[] p=pan()[index()];return p[0]+(p[4]==0?(p[2]-p[0])*Math.min(1,fraction()):0);}
    public float y(){int[] p=pan()[index()];return p[1]+(p[4]==0?(p[3]-p[1])*Math.min(1,fraction()):0);}
    public float scale(){int[] p=pan()[index()];return p[4]==0?1:1+(p[2]/100f-1)*Math.min(1,fraction());}
    public float variantAlpha(){return Math.max(0,Math.min(1,(float)((elapsed-index()*period()-period()*.7)/500)));}
    public float whiteAlpha(){double local=elapsed%period();if(elapsed>=pictures*period())return 0;if(local<1300)return (float)Math.min(1,Math.max(0,1-(local-300)/1000));return (float)Math.min(1,Math.max(0,(local-period()+1000)/(1000.0*2/3)));}
    public float blackAlpha(){return Math.max(0,Math.min(1,(elapsed-rollEnd()-10000)/(float)fadeMillis()));}
    public float scroll(){return elapsed>=rollEnd()?(strips-1)*576f:(float)(elapsed*strips*576.0/duration);}
    public int strip(){return Math.min(strips-1,(int)(scroll()/576));}
    public Set<String> resources(){Set<String> r=new LinkedHashSet<>();if(!active())return r;r.add(image("01",index()));r.add(image("02",index()));r.add("ED/EDBACK");r.add(image("",strip()));if(strip()+1<strips)r.add(image("",strip()+1));return r;}
    public void save(DataOutput out)throws IOException{out.writeInt(route);out.writeInt(pictures);out.writeInt(strips);out.writeLong(elapsed);out.writeLong(duration);out.writeBoolean(skippable);}
    public void load(DataInput in)throws IOException{route=in.readInt();pictures=in.readInt();strips=in.readInt();elapsed=in.readLong();duration=in.readLong();skippable=in.readBoolean();if(route< -1||route>=routes().length||elapsed<0||route>=0&&(pictures!=pan().length||strips<2||strips>1000||duration!=routeDuration(route)||elapsed>end()))throw new IOException("Invalid credits state");}
}
