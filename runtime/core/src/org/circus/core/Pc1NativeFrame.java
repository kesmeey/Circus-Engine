package org.circus.core;

import java.io.*;
import java.util.Arrays;

/** Mutable canonical8004-byte PC1 frame. All typed/variable aliases use these
 *same bytes. This class does not reconstruct resources, replay a native slot,
 *own external globals, or attach itself to a running VM. */
public final class Pc1NativeFrame implements Variables.NativeByteMemory {
    private static final int FORMAT=0x50314e31; // P1N1
    private final byte[] bytes;
    private Pc1NativeFrame(byte[] owned){bytes=owned;}
    /** PE virtual zero initialization only, before native411480 constructor. */
    public static Pc1NativeFrame zeroBss(){return new Pc1NativeFrame(new byte[Pc1FrameLayout.FRAME_BYTES]);}
    /** Exact live image after411480. That routine separately copies this
     *image to the system-return frame; callers decide when to create that owner. */
    public static Pc1NativeFrame initializedDefault()throws IOException{
        Pc1NativeFrame frame=zeroBss();frame.writeI32(Pc1FrameLayout.SIGNATURE,1);frame.writeI32(Pc1FrameLayout.LOGICAL_MUSIC,-1);return frame;
    }
    /** Raw image admission has no story-slot signature/range/string checks. */
    public static Pc1NativeFrame fromBytes(byte[] raw)throws IOException{
        if(raw==null||raw.length!=Pc1FrameLayout.FRAME_BYTES)throw new IOException("PC1 native frame requires exactly8004 bytes");
        return new Pc1NativeFrame(raw.clone());
    }
    public static Pc1NativeFrame fromSlot(Pc1NativeSlot slot)throws IOException{
        if(slot==null)throw new IOException("PC1 native frame requires a slot image");return fromBytes(slot.frameBytes());
    }
    public Pc1NativeFrame copy(){return new Pc1NativeFrame(bytes.clone());}
    public void copyFrom(Pc1NativeFrame source)throws IOException{
        if(source==null)throw new IOException("PC1 native frame copy requires a source");System.arraycopy(source.bytes,0,bytes,0,bytes.length);
    }
    public byte[] bytes(){return bytes.clone();}
    public byte[] rawSlice(int offset,int length)throws IOException{Pc1FrameLayout.checkedOffset(offset,length);return Arrays.copyOfRange(bytes,offset,offset+length);}
    public void writeBytes(int offset,byte[] raw)throws IOException{
        if(raw==null)throw new IOException("PC1 native frame write requires source bytes");Pc1FrameLayout.checkedOffset(offset,raw.length);System.arraycopy(raw,0,bytes,offset,raw.length);
    }
    public int readU8(int offset)throws IOException{return bytes[Pc1FrameLayout.checkedOffset(offset,1)]&255;}
    public int readU16(int offset)throws IOException{Pc1FrameLayout.checkedOffset(offset,2);return(bytes[offset]&255)|((bytes[offset+1]&255)<<8);}
    public int readI32(int offset)throws IOException{Pc1FrameLayout.checkedOffset(offset,4);return(bytes[offset]&255)|((bytes[offset+1]&255)<<8)|((bytes[offset+2]&255)<<16)|((bytes[offset+3]&255)<<24);}
    /** Fixed, reviewed DWORD overlays cannot leave the native frame. */
    public int dword(Pc1NativeSlot.Dword field){if(field==null)throw new IllegalArgumentException("PC1 frame field");int at=field.offset;return(bytes[at]&255)|((bytes[at+1]&255)<<8)|((bytes[at+2]&255)<<16)|(bytes[at+3]<<24);}
    public void dword(Pc1NativeSlot.Dword field,int value){if(field==null)throw new IllegalArgumentException("PC1 frame field");for(int n=0;n<4;n++)bytes[field.offset+n]=(byte)(value>>>(8*n));}
    public void writeU8(int offset,int value)throws IOException{bytes[Pc1FrameLayout.checkedOffset(offset,1)]=(byte)value;}
    public void writeU16(int offset,int value)throws IOException{Pc1FrameLayout.checkedOffset(offset,2);for(int n=0;n<2;n++)bytes[offset+n]=(byte)(value>>>(8*n));}
    public void writeI32(int offset,int value)throws IOException{Pc1FrameLayout.checkedOffset(offset,4);for(int n=0;n<4;n++)bytes[offset+n]=(byte)(value>>>(8*n));}
    public int readBank90(int index)throws IOException{return readU8(Pc1FrameLayout.offsetBank90(index));}
    /**411B10 stores low8 bits; it does not apply ordinary-byte saturation. */
    public void writeBank90(int index,int value)throws IOException{writeU8(Pc1FrameLayout.offsetBank90(index),value);}
    public int readOrdinary(int bank,int index)throws IOException{return readU8(Pc1FrameLayout.offsetOrdinary(bank,index));}
    public void writeOrdinary(int bank,int index,int value)throws IOException{writeU8(Pc1FrameLayout.offsetOrdinary(bank,index),clamp(value));}
    public int readNativeOrdinary(int bank,int index)throws IOException{return bank>=10||index>=100?0:readU8(Pc1FrameLayout.offsetNativeOrdinary(bank,index));}
    public void writeNativeOrdinary(int bank,int index,int value)throws IOException{if(bank<10&&index<100)writeU8(Pc1FrameLayout.offsetNativeOrdinary(bank,index),clamp(value));}
    public void clearOrdinaryBytes(){Arrays.fill(bytes,Pc1FrameLayout.ORDINARY_OFFSET,Pc1FrameLayout.ORDINARY_OFFSET+Pc1FrameLayout.ORDINARY_BYTES,(byte)0);}
    public byte ordinaryByte(int flatIndex){if(flatIndex<0||flatIndex>=Pc1FrameLayout.ORDINARY_BYTES)throw new IndexOutOfBoundsException("PC1 ordinary byte");return bytes[Pc1FrameLayout.ORDINARY_OFFSET+flatIndex];}
    public int rawLogWord(int index)throws IOException{return readU16(Pc1FrameLayout.offsetLog(index));}
    public void rawLogWord(int index,int value)throws IOException{writeU16(Pc1FrameLayout.offsetLog(index),value);}
    public int pendingDword(int index,int dword)throws IOException{return readI32(Pc1FrameLayout.offsetPending(index,dword));}
    public void pendingDword(int index,int dword,int value)throws IOException{writeI32(Pc1FrameLayout.offsetPending(index,dword),value);}
    /** Native strcpy scans across neighboring cells. This view can use only
     *the known8004 bytes, not slot-file trailing bytes or imaginary process RAM. */
    public byte[] rawCString(int offset)throws IOException{
        Pc1FrameLayout.checkedOffset(offset,1);int end=offset;while(end<bytes.length&&bytes[end]!=0)end++;
        if(end==bytes.length)throw new IOException("PC1 native C string reads beyond owned8004-byte frame");return Arrays.copyOfRange(bytes,offset,end);
    }
    /** Raw payload excludes NUL. Copy the prefix plus ONE native strcpy NUL;
     *leave stale suffix intact. Expanded text buffers have another double-NUL owner. */
    public void writeCString(int offset,byte[] payload)throws IOException{
        if(payload==null)throw new IOException("PC1 native C string requires raw payload");
        for(byte value:payload)if(value==0)throw new IOException("PC1 native C string payload contains NUL");
        Pc1FrameLayout.checkedOffset(offset,payload.length+1);System.arraycopy(payload,0,bytes,offset,payload.length);bytes[offset+payload.length]=0;
    }
    public void save(DataOutput out)throws IOException{out.writeInt(FORMAT);out.write(bytes);}
    public static Pc1NativeFrame load(DataInput in)throws IOException{
        if(in.readInt()!=FORMAT)throw new IOException("PC1 canonical native frame format");byte[] raw=new byte[Pc1FrameLayout.FRAME_BYTES];in.readFully(raw);return new Pc1NativeFrame(raw);
    }
    private static int clamp(int value){return value<0?0:value>255?255:value;}
}
