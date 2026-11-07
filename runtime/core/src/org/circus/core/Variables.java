package org.circus.core;

import java.io.*;
import java.util.*;

/** Native byte banks and their version-specific views. Special storage belongs to the host. */
public final class Variables {
    public enum Model { BYTE_WRAP, DC2_PACKED, PC_BYTE, PC_WIDE }
    public interface Special {
        int read(int bank,int index)throws IOException;
        void write(int bank,int index,int value)throws IOException;
        default NativeByteMemory nativeByteMemory(){return null;}
        default boolean signedNativeIndex(int bank){return false;}
    }
    /** Native ordinary bytes already owned by a complete host frame. Their
     *live bytes are serialized by that owner, never duplicated in Variables. */
    public interface NativeByteMemory {
        int readNativeOrdinary(int bank,int index)throws IOException;
        void writeNativeOrdinary(int bank,int index,int value)throws IOException;
        byte ordinaryByte(int flatIndex);
        void clearOrdinaryBytes();
    }
    // Keep the existing snapshot representation, including unused PC bank 10.
    private final byte[] live=new byte[1100],shadow=new byte[1100];
    private final int[] logIndex=new int[1000];
    private final byte[] logValue=new byte[1000];
    private int logCount;
    private final Special special;
    private final Model model;
    private final NativeByteMemory nativeMemory;

    public Variables(Special special){this(special,Model.BYTE_WRAP);}
    public Variables(Special special,Model model){this.special=special;this.model=model;nativeMemory=special==null?null:special.nativeByteMemory();if(nativeMemory!=null&&model!=Model.PC_BYTE)throw new IllegalArgumentException("Native frame ordinary memory requires PC_BYTE");}

    private static void reference(int bank,int index)throws IOException {
        if(bank<0||index<0)throw new IOException("Negative variable reference "+bank+":"+index);
    }
    private int ordinaryBanks(){return model==Model.PC_BYTE||model==Model.PC_WIDE?10:11;}
    private static int clamp(int value,int low,int high){return Math.max(low,Math.min(high,value));}
    private int readBytes(int bank,int index,int width){
        int value=0;
        for(int n=0;n<width;n++)value=(value<<8)|(live[bank*100+index+n]&255);
        return width==2?(short)value:value;
    }
    private int readView(int bank,int index,int base,int width,int banks){
        int ordinary=bank-base;
        if(ordinary>=banks||index>100-width)return 0;
        return readBytes(ordinary,index,width);
    }
    private void writeView(int bank,int index,int value,int base,int width,int banks)throws IOException {
        int ordinary=bank-base;
        if(ordinary>=banks||index>100-width)return;
        // DM recursively invokes its byte setter, including the bank 10 alias.
        for(int n=0;n<width;n++)write(ordinary,index+n,(value>>>(8*(width-1-n)))&255);
    }

    public int read(int bank,int index)throws IOException {
        if(nativeMemory!=null&&bank<10)return nativeMemory.readNativeOrdinary(bank,index);
        if(bank>=0&&index<0&&special!=null&&special.signedNativeIndex(bank))return special.read(bank,index);
        reference(bank,index);
        if(model==Model.DC2_PACKED){
            if(bank==400){
                return (short)((special.read(100,index)<<8)|special.read(100,index+1));
            }
            if(bank>=300)return readView(bank,index,300,2,11);
            if(bank>=200)return readView(bank,index,200,4,11);
        }else if(model==Model.PC_WIDE&&bank>=200){
            return readView(bank,index,200,4,10);
        }
        if(bank<ordinaryBanks())return index<100?live[bank*100+index]&255:0;
        return special.read(bank,index);
    }

    public void write(int bank,int index,int value)throws IOException {
        if(nativeMemory!=null&&bank<10){nativeMemory.writeNativeOrdinary(bank,index,value);return;}
        if(bank>=0&&index<0&&special!=null&&special.signedNativeIndex(bank)){special.write(bank,index,value);return;}
        reference(bank,index);
        if(model==Model.DC2_PACKED){
            if(bank==400){
                if(index>=100)return;
                int word=clamp(value,-32768,32767);
                write(100,index,(word>>>8)&255);write(100,index+1,word&255);return;
            }
            if(bank>=300){writeView(bank,index,clamp(value,-32768,32767),300,2,11);return;}
            if(bank>=200){writeView(bank,index,value,200,4,11);return;}
            if(bank==100){
                if(index>=100)return;
                special.write(bank,index,value&255);
                if(index<=20)live[1000+index]=(byte)value;
                return;
            }
            if(bank==10){
                if(index>20)return;
                // The ordinary alias clamps; direct bank 100 writes wrap.
                write(100,index,clamp(value,0,255));return;
            }
        }else if(model==Model.PC_WIDE&&bank>=200){
            writeView(bank,index,value,200,4,10);return;
        }
        if(bank<ordinaryBanks()){
            if(index<100)live[bank*100+index]=(byte)(model==Model.BYTE_WRAP?value:clamp(value,0,255));
            return;
        }
        special.write(bank,index,value);
    }

    public void clearOrdinary(){Arrays.fill(live,(byte)0);if(nativeMemory!=null)nativeMemory.clearOrdinaryBytes();Arrays.fill(shadow,(byte)0);}
    /** A native byte memset does not also erase the change-history shadow or log. */
    public void clearOrdinaryBytes(){if(nativeMemory!=null)nativeMemory.clearOrdinaryBytes();else Arrays.fill(live,0,ordinaryBanks()*100,(byte)0);}
    public void clearHistory(){logCount=0;Arrays.fill(logIndex,0);Arrays.fill(logValue,(byte)0);}
    public void appendHistory(){
        for(int i=0;i<live.length;i++){byte value=nativeMemory!=null&&i<1000?nativeMemory.ordinaryByte(i):live[i];if(value!=shadow[i]){
            if(logCount<1000){logIndex[logCount]=i;logValue[logCount++]=value;}
            shadow[i]=value;
        }
        }
    }
    public void replayHistory(int argument)throws IOException {
        int count=argument&0x7fff;
        if(count>logCount)throw new IOException("History prefix not available");
        clearOrdinary();
        for(int n=0;n<count;n++){int at=logIndex[n];if(nativeMemory!=null&&at<1000)nativeMemory.writeNativeOrdinary(at/100,at%100,logValue[n]&255);else live[at]=logValue[n];shadow[at]=logValue[n];}
        logCount=count;
    }
    public int historyCount(){return logCount;}
    public void save(DataOutput out)throws IOException {
        if(nativeMemory==null)out.write(live);else{out.writeInt(0x564e4d31);out.write(live,1000,100);}out.write(shadow);out.writeInt(logCount);
        for(int n=0;n<logCount;n++){out.writeShort(logIndex[n]);out.writeByte(logValue[n]);}
    }
    public void load(DataInput in)throws IOException {
        byte[] nextLive=new byte[1100],nextShadow=new byte[1100];
        if(nativeMemory==null)in.readFully(nextLive);else{if(in.readInt()!=0x564e4d31)throw new IOException("Native variable memory owner format");in.readFully(nextLive,1000,100);}in.readFully(nextShadow);int count=in.readInt();
        if(count<0||count>1000)throw new IOException("Invalid history size");
        int[] indices=new int[count];byte[] values=new byte[count];
        for(int n=0;n<count;n++){
            indices[n]=in.readUnsignedShort();
            if(indices[n]>=1100)throw new IOException("Invalid history index");
            values[n]=in.readByte();
        }
        System.arraycopy(nextLive,0,live,0,1100);System.arraycopy(nextShadow,0,shadow,0,1100);
        logCount=count;System.arraycopy(indices,0,logIndex,0,count);System.arraycopy(values,0,logValue,0,count);
    }
}
