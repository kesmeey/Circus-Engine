package org.circus.core;
import java.io.*;
import java.util.*;

/** Seven DD staff-roll sequences, reconstructed from 4374B0..4384E9.
 * Original PCK indices supply all pictures and credit lettering at runtime. */
public final class Dc3Credits {
    private static final String[] ROUTES={"RCC","SRR","HMN","SRA","AOI","MKT","SMM"};
    public int route=-1,pictures,strips;public long elapsed,duration;public boolean skippable;
    public void start(CircusResources data,int route,boolean skippable)throws IOException {
        if(route<0||route>=7)throw new IOException("Unknown credits route "+route);this.route=route;this.skippable=skippable;elapsed=0;duration=route<=2?262000:265500;
        pictures=data.files.archive("AdvData/GRP/"+archive("01")).entries.size();int second=data.files.archive("AdvData/GRP/"+archive("02")).entries.size();strips=data.files.archive("AdvData/GRP/"+archive("")).entries.size();
        if(pictures!=Dc3CreditsRules.PAN[route].length||second!=pictures||strips<2||strips>1000)throw new IOException("Credits archive layout does not match DD");
    }
    public String archive(String suffix){return "ED/ED"+ROUTES[route]+suffix+".PCK";}
    public String image(String suffix,int index){return "pck:"+archive(suffix)+"#"+index;}
    private long rollEnd(){return duration*(strips-1)/Math.max(1,strips);}
    public long end(){return rollEnd()+10000+834;} // final page hold, then 0.02 alpha/tick fade to black
    public boolean active(){return route>=0&&elapsed<end();}
    public void elapse(long ms){if(active())elapsed=Math.min(end(),elapsed+Math.max(0,ms));}
    public double period(){return (duration-13500)/(double)Math.max(1,pictures);}
    public int index(){return Math.min(pictures-1,(int)(elapsed/period()));}
    public float fraction(){return (float)((elapsed-index()*period())/(period()+2000));}
    public float x(){int[] p=Dc3CreditsRules.PAN[route][index()];return p[0]+(p[4]==0?(p[2]-p[0])*Math.min(1,fraction()):0);}
    public float y(){int[] p=Dc3CreditsRules.PAN[route][index()];return p[1]+(p[4]==0?(p[3]-p[1])*Math.min(1,fraction()):0);}
    public float scale(){int[] p=Dc3CreditsRules.PAN[route][index()];return p[4]==0?1:1+(p[2]/100f-1)*Math.min(1,fraction());}
    public float variantAlpha(){return Math.max(0,Math.min(1,(float)((elapsed-index()*period()-period()*.7)/500)));}
    public float whiteAlpha(){double local=elapsed%period();if(elapsed>=pictures*period())return 0;if(local<1300)return (float)Math.min(1,Math.max(0,1-(local-300)/1000));return (float)Math.min(1,Math.max(0,(local-period()+1000)/(1000.0*2/3)));}
    public float blackAlpha(){return Math.max(0,Math.min(1,(elapsed-rollEnd()-10000)/834f));}
    public float scroll(){return elapsed>=rollEnd()?(strips-1)*576f:(float)(elapsed*strips*576.0/duration);}
    public int strip(){return Math.min(strips-1,(int)(scroll()/576));}
    public Set<String> resources(){Set<String> r=new LinkedHashSet<>();if(!active())return r;r.add(image("01",index()));r.add(image("02",index()));r.add("ED/EDBACK");r.add(image("",strip()));if(strip()+1<strips)r.add(image("",strip()+1));return r;}
    public void save(DataOutput out)throws IOException{out.writeInt(route);out.writeInt(pictures);out.writeInt(strips);out.writeLong(elapsed);out.writeLong(duration);out.writeBoolean(skippable);}
    public void load(DataInput in)throws IOException{route=in.readInt();pictures=in.readInt();strips=in.readInt();elapsed=in.readLong();duration=in.readLong();skippable=in.readBoolean();if(route< -1||route>6||elapsed<0||route>=0&&(pictures!=Dc3CreditsRules.PAN[route].length||strips<2||strips>1000||duration!=(route<=2?262000:265500)||elapsed>end()))throw new IOException("Invalid credits state");}
}
