package org.circus.core;

import java.io.*;
import java.util.*;

/** Current PC1 CHS415D50/416220 param5=2. Global geometry preparation only;
 * frame-owned9DA4B0 headY is an explicit argument/result and is never stored here. */
public final class Pc1MessageGeometry {
    public static final int BODY_X=0x43d438,BODY_Y=0x43d43c,BODY_PITCH=0x43d440,BODY_STYLE=0x43d444;
    public static final int BODY_HISTORY_STYLE=0x43d448,BODY_HISTORY_CONFIG=0x43d44c,BODY_HISTORY_AUX=0x43d450;
    public static final int NAME_X=0x43d454,NAME_Y=0x43d458,NAME_PITCH=0x43d45c,NAME_STYLE=0x43d460,NAME_HISTORY_STYLE=0x43d464,NAME_HISTORY_AUX=0x43d468;
    private static final int FORMAT=0x50314731;
    /** Current CHS PE's13 consecutive DWORDs; constructor input can preserve another validated live owner. */
    private static final int[] NATIVE_CHS_DEFAULTS={352,423,34,7,140,137,135,376,364,28,7,7,6};
    private final int[] fields;
    public static final class Entry {
        private final int[] words;
        public Entry(int... raw){if(raw==null||raw.length!=8)throw new IllegalArgumentException("PC1 message geometry entry requires8 native DWORDs");words=raw.clone();}
        public int word(int index){return words[index];}
        public int[] snapshot(){return words.clone();}
    }
    public Pc1MessageGeometry(){this(NATIVE_CHS_DEFAULTS);}
    /** Snapshot order is native address order438,43C,...,468, not a drawing order. */
    public Pc1MessageGeometry(int[] initial){if(initial==null||initial.length!=13)throw new IllegalArgumentException("PC1 message geometry requires13 global DWORDs");fields=initial.clone();}
    public int field(int nativeAddress){int delta=nativeAddress-BODY_X;if(delta<0||(delta&3)!=0||delta/4>=fields.length)throw new IllegalArgumentException("Unknown PC1 message geometry address");return fields[delta/4];}
    public void field(int nativeAddress,int value){int delta=nativeAddress-BODY_X;if(delta<0||(delta&3)!=0||delta/4>=fields.length)throw new IllegalArgumentException("Unknown PC1 message geometry address");fields[delta/4]=value;}
    public int[] snapshot(){return fields.clone();}
    public Pc1MessageGeometry copy(){return new Pc1MessageGeometry(fields);}
    private static void validate(List<Entry> entries)throws IOException{if(entries==null)throw new IOException("PC1 message geometry has no selected native entry list");for(Entry entry:entries)if(entry==null)throw new IOException("PC1 message geometry has a null native entry");}
    /** 415D50 mode2. Only the final headY result belongs back in the story frame. */
    public int prepareBody(List<Entry> entries,int cachedRead,int shading,int headY)throws IOException{
        validate(entries);
        for(Entry entry:entries){int[] d=entry.words;if(d[2]!=10||d[3]!=0&&cachedRead==0)continue;
            if(d[6]==0){fields[5]=d[0];fields[4]=d[1];fields[6]=d[7];}
            else{fields[0]=d[0];fields[1]=d[1];fields[2]=d[6];fields[3]=shading==0?d[7]&0xffbf:d[7];}
        }
        return headY==-1?fields[1]:headY;
    }
    /** 416220 mode2. Name styles are not passed through the body's shading mask. */
    public void prepareName(List<Entry> entries,int cachedRead)throws IOException{
        validate(entries);
        for(Entry entry:entries){int[] d=entry.words;if(d[2]!=10||d[3]!=0&&cachedRead==0)continue;
            if(d[6]==0){fields[11]=d[0];fields[12]=d[1];}
            else{fields[7]=d[0];fields[8]=d[1];fields[9]=d[6];fields[10]=d[7];}
        }
    }
    public void save(DataOutput out)throws IOException{out.writeInt(FORMAT);for(int field:fields)out.writeInt(field);}
    public static Pc1MessageGeometry load(DataInput in)throws IOException{if(in.readInt()!=FORMAT)throw new IOException("PC1 message geometry format");int[] fields=new int[13];for(int n=0;n<fields.length;n++)fields[n]=in.readInt();return new Pc1MessageGeometry(fields);}
}
