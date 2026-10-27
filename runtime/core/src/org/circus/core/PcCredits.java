package org.circus.core;
import java.io.*;
/** PC2 408230/408340: 31 original 800x600 strips, clock-gated scrolling. */
public final class PcCredits {
    public boolean active;public int variant;public long elapsed,lead;
    public static final float SPEED=Float.intBitsToFloat(0x3d8f826e);
    public void start(int mode,long clock)throws IOException{if(mode<1||mode>3)throw new IOException("PC2 credits variant "+mode);variant=mode;active=true;elapsed=0;lead=Math.max(0,15000-clock);}
    public void preparePc1(){active=false;variant=0;elapsed=lead=0;}
    public int stripHeight(){return variant==0?1200:600;}
    public int lastStrip(){return variant==0?11:30;}
    public float scroll(){return variant==0?Math.min(13200,(elapsed/30)*3):Math.min(18000,Math.max(0,elapsed-lead)*SPEED);}
    public void elapse(long ms){if(active){elapsed+=Math.max(0,ms);if(elapsed>=(variant==0?132000:lead+(long)Math.ceil(18000/SPEED)+3500))active=false;}}
    public String image(int n){return variant==0?String.format(java.util.Locale.ROOT,"STAFF/staff2_%02d",n+1):String.format(java.util.Locale.ROOT,"ED/GEND_%s%02d",variant==2?"SKR_":variant==3?"SNB_":"",n+1);}
    public void save(DataOutput o)throws IOException{o.writeBoolean(active);o.writeInt(variant);o.writeLong(elapsed);o.writeLong(lead);}
    public void load(DataInput i)throws IOException{active=i.readBoolean();variant=i.readInt();elapsed=i.readLong();lead=i.readLong();if(variant<0||variant>3||elapsed<0||lead<0||lead>15000)throw new IOException("Invalid PC credits state");}
}
