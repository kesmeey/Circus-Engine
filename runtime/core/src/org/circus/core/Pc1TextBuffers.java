package org.circus.core;

import java.io.*;
import java.util.Arrays;

/** Current PC1 CHS text destinations, outside the native1F44 story frame.
 * Only a complete portable snapshot owns this memory. System26/1C must not
 * copy/restore it. Unknown redraw-stack suffixes are never layout sources. */
public final class Pc1TextBuffers {
    public static final String LIVE_BODY="pc1.live.body.9d3af8";
    public static final String LIVE_SPEAKER="pc1.live.speaker.9ccaa0";
    public static final String REDRAW_BODY="pc1.redraw.418220.local400";
    public static final String REDRAW_SPEAKER="pc1.redraw.418220.local500";
    public static final int BODY_CAPACITY=1024,SPEAKER_CAPACITY=256;
    private static final String[] IDS={LIVE_BODY,LIVE_SPEAKER,REDRAW_BODY,REDRAW_SPEAKER};
    private static final int[] CAPACITIES={BODY_CAPACITY,SPEAKER_CAPACITY,BODY_CAPACITY,SPEAKER_CAPACITY};
    private static final int FORMAT=0x50314231;
    private final byte[][] backing=new byte[4][];
    private final int[] known=new int[4];

    /** Live destinations occupy zero-filled PE virtual data. Redraw locals
     * have no known bytes before beginRedraw(), regardless of Java's zeros. */
    public Pc1TextBuffers(){for(int n=0;n<4;n++)backing[n]=new byte[CAPACITIES[n]];known[0]=BODY_CAPACITY;known[1]=SPEAKER_CAPACITY;}
    private static int index(String owner){for(int n=0;n<IDS.length;n++)if(IDS[n].equals(owner))return n;throw new IllegalArgumentException("Unknown PC1 text buffer owner: "+owner);}
    public static String[] ownerIds(){return IDS.clone();}
    public static int capacity(String owner){return CAPACITIES[index(owner)];}
    public int knownPrefixLength(String owner){return known[index(owner)];}
    /** Full raw backing is for snapshots/diagnostics, not a claim that every
     * byte is known native memory. Layout.Source must receive knownBytes(). */
    public byte[] fullBytes(String owner){return backing[index(owner)].clone();}
    public byte[] knownBytes(String owner){int n=index(owner);return Arrays.copyOf(backing[n],known[n]);}
    public int unsignedByte(String owner,int offset)throws IOException{int n=index(owner);if(offset<0||offset>=known[n])throw new IOException("PC1 text read outside known buffer "+owner+"@"+offset);return backing[n][offset]&255;}
    /** Addressed live byte writes share the actual destination, preserving
     *the stale suffix. A sparse stack write cannot invent unknown gap bytes. */
    public void writeKnownByte(String owner,int offset,int value)throws IOException{
        int n=index(owner);if(offset<0||offset>=backing[n].length||offset>known[n])throw new IOException("PC1 text write outside contiguous known buffer "+owner+"@"+offset);
        backing[n][offset]=(byte)value;if(offset==known[n])known[n]++;
    }
    /** Logical C string, without either terminator. No invented zero at the
     * end of a known prefix: an unterminated known source is an explicit fault. */
    public byte[] read(String owner)throws IOException{int n=index(owner),end=0;while(end<known[n]&&backing[n][end]!=0)end++;if(end==known[n])throw new IOException("PC1 text terminator outside known buffer "+owner);return Arrays.copyOf(backing[n],end);}

    /** 418220 allocates fresh locals, then writes only local_500[0]=0.
     * A previous invocation's bytes remain in portable backing but lose their
     * known status; stack address reuse is not evidence of native contents. */
    public void beginRedraw(){known[2]=0;known[3]=1;backing[3][0]=0;}
    /** Current4C without a preceding4B clears just the live speaker's head. */
    public void clearLiveSpeakerHead(){clearHead(1);}
    /** A head-only clear within the same redraw invocation retains its known
     * stale suffix. It does not manufacture a second terminating zero. */
    public void clearRedrawSpeakerHead(){clearHead(3);}
    private void clearHead(int n){backing[n][0]=0;known[n]=Math.max(known[n],1);}

    /** 4123A0 writes the expanded prefix and exactly two zeros. It does not
     * clear the rest. The capacity check bounds owned memory; native4123A0
     * itself performs no destination-capacity validation. */
    public void writeExpanded(String owner,byte[] expanded)throws IOException{
        int n=index(owner);if(expanded==null)throw new IOException("PC1 expanded text is missing");
        if(expanded.length>CAPACITIES[n]-2)throw new IOException("PC1 expanded text exceeds owned buffer "+owner);
        for(byte value:expanded)if(value==0)throw new IOException("PC1 expanded text contains NUL");
        System.arraycopy(expanded,0,backing[n],0,expanded.length);backing[n][expanded.length]=0;backing[n][expanded.length+1]=0;
        known[n]=Math.max(known[n],expanded.length+2);
    }
    /** Accepts Pc1TextExpander.nativeBuffer(): exactly prefix+twoNUL bytes,
     * rather than an arbitrary capacity-sized source with a stale suffix. */
    public void writeNativeBuffer(String owner,byte[] nativeBuffer)throws IOException{
        int n=index(owner);if(nativeBuffer==null||nativeBuffer.length<2||nativeBuffer.length>CAPACITIES[n])throw new IOException("PC1 native expanded buffer length "+owner);
        int end=nativeBuffer.length-2;if(nativeBuffer[end]!=0||nativeBuffer[end+1]!=0)throw new IOException("PC1 expanded buffer requires two terminating NULs");
        for(int i=0;i<end;i++)if(nativeBuffer[i]==0)throw new IOException("PC1 native expanded buffer contains an earlier NUL");
        System.arraycopy(nativeBuffer,0,backing[n],0,nativeBuffer.length);known[n]=Math.max(known[n],nativeBuffer.length);
    }
    public Pc1TextBuffers copy(){Pc1TextBuffers copy=new Pc1TextBuffers();for(int n=0;n<4;n++){copy.backing[n]=backing[n].clone();copy.known[n]=known[n];}return copy;}
    /** Fixed owner order above; each record is knownPrefixLength:i32 then its
     * full fixed-capacity backing. Unknown bytes are retained, not promoted.
     * A backing need not have doubleNUL after a native head-only clear. */
    public void save(DataOutput out)throws IOException{out.writeInt(FORMAT);for(int n=0;n<4;n++){out.writeInt(known[n]);out.write(backing[n]);}}
    public static Pc1TextBuffers load(DataInput in)throws IOException{
        if(in.readInt()!=FORMAT)throw new IOException("PC1 text buffer format");Pc1TextBuffers result=new Pc1TextBuffers();
        for(int n=0;n<4;n++){int size=in.readInt();if(size<0||size>CAPACITIES[n]||n<2&&size!=CAPACITIES[n])throw new IOException("PC1 text buffer known-prefix length "+IDS[n]);in.readFully(result.backing[n]);result.known[n]=size;}
        return result;
    }
}
