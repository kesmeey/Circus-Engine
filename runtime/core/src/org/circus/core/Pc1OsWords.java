package org.circus.core;

import java.io.*;
import java.util.*;

/** Exactly the currentCHS unclaimed OS DWORD cells. Physical objects remain
 *external providers; aliases mutate these bytes, never a typed-font mirror.
 *FILE42AB1C belongs to existing EffectPe/Audio and is deliberately not bound. */
public final class Pc1OsWords {
    private static final int FORMAT=0x50314f57,VERSION=1;
    public static final int INSTANCE=0x94c30c,HWND=0x94c63c,FONT28=0x94c640,FONT12=0x94c644,FONT22=0x94c648,FONT32=0x94c64c,FONT40=0x94c650;
    public static final int SOURCE_DC=0x94bdbc,EFFECT_DC=0x94bdc0,DESTINATION_DC=0x94c654,COMPATIBLE_BITMAP=0x94c658,DIB_BITMAP=0x94c65c,PREVIOUS_BITMAP=0x94c660,MUTEX=0x94c664,PROCESS_HEAP=0x94c668;
    public static final int GLYPH_DC=0x48d580,GLYPH_PREVIOUS_FONT=0x48d584,GLYPH_BITS=0x48d588,GLYPH_BITMAP=0x949868;
    private static final int[][] RANGES={{SOURCE_DC,8},{INSTANCE,4},{HWND,48},{GLYPH_DC,12},{GLYPH_BITMAP,4}};
    private final Pc1NativeMemory memory;private final Pc1OsRegistry registry;private final byte[][] raw=new byte[RANGES.length][],known=new byte[RANGES.length][];private final String[][] reason=new String[RANGES.length][];
    public Pc1OsWords(Pc1NativeMemory memory,Pc1OsRegistry registry)throws IOException{
        if(memory==null||registry==null)throw new IOException("PC1 OS cells require canonical bus/shared scalar registry");if(memory.guestAddressSpace()!=null&&memory.guestAddressSpace()!=registry.guestAddressSpace())throw new IOException("PC1 OS cells/registry have different guest domains");this.memory=memory;this.registry=registry;
        // All of these exact cells are in the target's loaded-zero BSS. This
        //constructor does not publish success for any later physical API call.
        for(int n=0;n<RANGES.length;n++){final int range=n;raw[n]=new byte[RANGES[n][1]];known[n]=new byte[RANGES[n][1]/4];Arrays.fill(known[n],(byte)15);reason[n]=new String[known[n].length];memory.bind(RANGES[n][0],raw[n].length,new Pc1NativeMemory.Region(){public int read(int offset)throws IOException{return readByte(range,offset);}public void write(int offset,int value){writeByte(range,offset,value);}});}
    }
    public Pc1NativeMemory nativeMemory(){return memory;}
    public Pc1OsRegistry registry(){return registry;}
    private int readByte(int region,int offset)throws IOException{if((known[region][offset/4]&(1<<(offset&3)))==0)throw new IOException("PC1 OS raw DWORD byte is unknown after physical publication: "+reason[region][offset/4]);return raw[region][offset]&255;}
    private void writeByte(int region,int offset,int value){raw[region][offset]=(byte)value;known[region][offset/4]|=1<<(offset&3);if(known[region][offset/4]==15)reason[region][offset/4]=null;}
    private int region(int address)throws IOException{long at=address&0xffffffffL;for(int n=0;n<RANGES.length;n++)if(at>=RANGES[n][0]&&at+4<=((long)RANGES[n][0])+RANGES[n][1]&&(address&3)==0)return n;throw new IOException("PC1 publication address is not an owned OS DWORD0x"+Integer.toHexString(address));}
    /** Uses the live canonical bus so externalFILE/current alias cells can be
     *read too. No typed state is cached alongside the raw DWORD. */
    public int rawWord(int address)throws IOException{int value=0;for(int n=0;n<4;n++)value|=memory.readAddress(address+n)<<(8*n);return value;}
    public void rawWord(int address,int value)throws IOException{int region=region(address),offset=address-RANGES[region][0];for(int n=0;n<4;n++)writeByte(region,offset+n,value>>>(8*n));}
    public int knownByteMask(int address)throws IOException{int region=region(address);return known[region][(address-RANGES[region][0])/4]&15;}
    public void unknown(int address,String why)throws IOException{if(why==null||why.length()==0)throw new IOException("PC1 unknown OS DWORD needs precise reason");int region=region(address),offset=(address-RANGES[region][0])/4;known[region][offset]=0;reason[region][offset]=why.length()>4096?why.substring(0,4096):why;}
    /** Call exactly at the original native MOV of an API result. A physical
     *NULL/sentinel result is an explicit rawWord write by the API adapter. */
    public Pc1OsRegistry.Scalar publish(int address,Pc1OsRegistry.Kind kind,Pc1GuestAddressSpace.ExternalOwner owner,Pc1OsRegistry.Binding binding)throws IOException{
        region(address);try{Pc1OsRegistry.Scalar scalar=registry.register(kind,owner,binding);rawWord(address,scalar.rawWord);return scalar;}catch(IOException error){unknown(address,error.getMessage()==null?"required scalar association failed":error.getMessage());throw error;}
    }
    /** DIB bits are memory, not an opaque HBITMAP scalar. Its one physical
     *byte lease is saved by the actual graphics/font owner; sole Guest layout
     *references the external owner key. */
    public void publishMemory(int address,Pc1HeapLease lease,Pc1GuestAddressSpace.ExternalOwner owner)throws IOException{
        region(address);try{Pc1GuestAddressSpace space=registry.guestAddressSpace();int pointer=space.containsLease(lease)?space.addressOf(lease):space.mapBorrowed(lease,owner);rawWord(address,pointer);}catch(IOException error){unknown(address,error.getMessage()==null?"required memory association failed":error.getMessage());throw error;}
    }
    public Pc1OsRegistry.Resolution resolve(int address,Pc1OsRegistry.Domain domain)throws IOException{return registry.lookup(rawWord(address),domain);}
    public Pc1OsRegistry.Resolution resolveFile(int address)throws IOException{return registry.lookupFile(rawWord(address));}
    public void save(DataOutput out)throws IOException{out.writeInt(FORMAT);out.writeInt(VERSION);for(int n=0;n<RANGES.length;n++){out.write(raw[n]);for(int word=0;word<known[n].length;word++){out.writeByte(known[n][word]);if(known[n][word]!=15)out.writeUTF(reason[n][word]);}}}
    public static final class Image {private final byte[][] raw,known;private final String[][] reason;private Image(byte[][] raw,byte[][] known,String[][] reason){this.raw=raw;this.known=known;this.reason=reason;}}
    public static Image loadImage(DataInput in)throws IOException{if(in.readInt()!=FORMAT||in.readInt()!=VERSION)throw new IOException("PC1 OS words codec format");byte[][] raw=new byte[RANGES.length][],known=new byte[RANGES.length][];String[][] reason=new String[RANGES.length][];for(int n=0;n<RANGES.length;n++){raw[n]=new byte[RANGES[n][1]];in.readFully(raw[n]);known[n]=new byte[raw[n].length/4];reason[n]=new String[known[n].length];for(int word=0;word<known[n].length;word++){int mask=in.readUnsignedByte();if(mask>15)throw new IOException("PC1 OS word byte-knowledge mask");known[n][word]=(byte)mask;if(mask!=15){String why=in.readUTF();if(why.length()==0||why.length()>4096)throw new IOException("PC1 OS unknown DWORD reason");reason[n][word]=why;}}}return new Image(raw,known,reason);}
    public void restore(Image image)throws IOException{if(image==null)throw new IOException("PC1 OS words image required");for(int n=0;n<RANGES.length;n++){System.arraycopy(image.raw[n],0,raw[n],0,raw[n].length);System.arraycopy(image.known[n],0,known[n],0,known[n].length);System.arraycopy(image.reason[n],0,reason[n],0,reason[n].length);}}
}
