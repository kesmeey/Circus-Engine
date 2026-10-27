package org.circus.core;

import java.io.*;
import java.util.*;

/** Ordinary bytes plus explicit host delegation; unknown banks never become fake arrays. */
public final class Variables {
    public enum Model { BYTE_WRAP, DC2_PACKED, PC_BYTE, PC_WIDE }
    public interface Special { int read(int bank,int index)throws IOException; void write(int bank,int index,int value)throws IOException; }
    private final byte[] live=new byte[1100],shadow=new byte[1100];
    private final int[] logIndex=new int[1000];private final byte[] logValue=new byte[1000];
    private int logCount;private final Special special;private final Model model;
    public Variables(Special special){this(special,Model.BYTE_WRAP);}
    public Variables(Special special,Model model){this.special=special;this.model=model;}
    private int ordinary(int bank,int index)throws IOException {
        if(bank<0||index<0)throw new IOException("Negative variable reference "+bank+":"+index);
        if(bank<=10){if(index>=100)throw new IOException("Ordinary variable index out of range");return bank*100+index;}
        return -1;
    }
    public int read(int b,int i)throws IOException{
        int p=ordinary(b,i);
        if(model==Model.PC_BYTE||model==Model.PC_WIDE){
            if(model==Model.PC_WIDE&&b>=200){int bank=b-200;if(bank>=10||i>=97)return 0;int value=0;for(int n=0;n<4;n++)value=(value<<8)|(live[bank*100+i+n]&255);return value;}
            return b<10?live[p]&255:special.read(b,i);
        }
        if(model==Model.DC2_PACKED){
            if(b==400){if(i>=100)throw new IOException("Packed persistent index out of range");return (short)((special.read(100,i)<<8)|special.read(100,i+1));}
            if(b>=300){int bank=b-300;if(bank>10||i>=99)return 0;return (short)(((live[bank*100+i]&255)<<8)|(live[bank*100+i+1]&255));}
            if(b>=200){int bank=b-200;if(bank>10||i>=97)return 0;int value=0;for(int n=0;n<4;n++)value=(value<<8)|(live[bank*100+i+n]&255);return value;}
        }
        return p>=0?live[p]&255:special.read(b,i);
    }
    public void write(int b,int i,int v)throws IOException{
        int p=ordinary(b,i);
        if(model==Model.PC_BYTE||model==Model.PC_WIDE){
            if(model==Model.PC_WIDE&&b>=200){int bank=b-200;if(bank>=10||i>=97)return;for(int n=0;n<4;n++)live[bank*100+i+n]=(byte)(v>>>(24-8*n));return;}
            if(b<10)live[p]=(byte)Math.max(0,Math.min(255,v));else special.write(b,i,v);return;
        }
        if(model==Model.DC2_PACKED){
            if(b==400){if(i>=100)return;int value=Math.max(-32768,Math.min(32767,v));write(100,i,(value>>>8)&255);write(100,i+1,value&255);return;}
            if(b>=300){int bank=b-300;if(bank>10||i>=99)return;int value=Math.max(-32768,Math.min(32767,v));write(bank,i,(value>>>8)&255);write(bank,i+1,value&255);return;}
            if(b>=200){int bank=b-200;if(bank>10||i>=97)return;for(int n=0;n<4;n++)write(bank,i+n,(v>>>(24-8*n))&255);return;}
            if(b==100){if(i>=100)return;special.write(b,i,v&255);if(i<=20)live[1000+i]=(byte)v;return;}
            if(p>=0){if(b==10&&i>20)return;v=Math.max(0,Math.min(255,v));if(b==10)special.write(100,i,v);live[p]=(byte)v;return;}
        }
        if(p>=0)live[p]=(byte)v;else special.write(b,i,v);
    }
    public void clearOrdinary(){Arrays.fill(live,(byte)0);Arrays.fill(shadow,(byte)0);}
    public void clearHistory(){logCount=0;Arrays.fill(logIndex,0);Arrays.fill(logValue,(byte)0);}
    public void appendHistory(){for(int i=0;i<live.length;i++)if(live[i]!=shadow[i]){if(logCount<1000){logIndex[logCount]=i;logValue[logCount++]=live[i];}shadow[i]=live[i];}}
    public void replayHistory(int argument)throws IOException {
        int n=argument&0x7fff;if(n>logCount)throw new IOException("History prefix not available");
        clearOrdinary();for(int j=0;j<n;j++)live[logIndex[j]]=shadow[logIndex[j]]=logValue[j];logCount=n;
    }
    public int historyCount(){return logCount;}
    public void save(DataOutput out)throws IOException{out.write(live);out.write(shadow);out.writeInt(logCount);for(int i=0;i<logCount;i++){out.writeShort(logIndex[i]);out.writeByte(logValue[i]);}}
    public void load(DataInput in)throws IOException {
        byte[] l=new byte[1100],s=new byte[1100];in.readFully(l);in.readFully(s);int n=in.readInt();if(n<0||n>1000)throw new IOException("Invalid history size");
        int[] indices=new int[n];byte[] values=new byte[n];for(int i=0;i<n;i++){indices[i]=in.readUnsignedShort();if(indices[i]>=1100)throw new IOException("Invalid history index");values[i]=in.readByte();}
        System.arraycopy(l,0,live,0,1100);System.arraycopy(s,0,shadow,0,1100);logCount=n;
        System.arraycopy(indices,0,logIndex,0,n);System.arraycopy(values,0,logValue,0,n);
    }
}
