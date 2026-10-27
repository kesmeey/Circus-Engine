package org.circus.core;
import java.io.*;
import java.util.*;

/** DM GED archives; timing derives from the 208 music clock and 43F010. */
public final class Dc2Credits {
    public int strips,pictures;public long elapsed,duration;public boolean enabled;
    public void start(CircusResources data,long musicElapsed)throws IOException{
        strips=data.files.archive("AdvData/GRP/ED/GED.PCK").entries.size();pictures=data.files.archive("AdvData/GRP/ED/GEDANIME.PCK").entries.size();
        if(strips<1||pictures<1)throw new IOException("Empty DM ending archive");duration=Math.max(0,470000-musicElapsed);elapsed=0;enabled=musicElapsed<500000;
    }
    public boolean active(){return enabled&&elapsed<duration+5000;}
    public void elapse(long ms){if(active())elapsed=Math.min(duration+5000,elapsed+ms);}
    public int picture(){return Math.min(pictures-1,(int)(elapsed*pictures/Math.max(1,duration+5000)));}
    public float scroll(){return (float)Math.min((strips-1)*600.0,elapsed*strips*600.0/Math.max(1,duration));}
    public int strip(){return Math.min(strips-1,(int)(scroll()/600));}
    public String image(boolean picture,int index){return "pck:ED/"+(picture?"GEDANIME":"GED")+".PCK#"+index;}
    public Set<String> resources(){Set<String> r=new HashSet<>();if(active()){r.add("ED/photo");r.add(image(true,picture()));r.add(image(false,strip()));if(strip()+1<strips)r.add(image(false,strip()+1));}return r;}
    public void save(DataOutput o)throws IOException{o.writeInt(strips);o.writeInt(pictures);o.writeLong(elapsed);o.writeLong(duration);o.writeBoolean(enabled);}
    public void load(DataInput i)throws IOException{strips=i.readInt();pictures=i.readInt();elapsed=i.readLong();duration=i.readLong();enabled=i.readBoolean();if(strips<0||pictures<0||elapsed<0||duration<0||duration>470000)throw new IOException("Invalid DM ending state");}
}
