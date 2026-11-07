package org.circus.core;

import java.io.IOException;
import java.util.Arrays;

/** The shared physical-record reader for execution and the native label/block scanners.
 * Scanner signedness is independent of the execution decoder's signedness. */
final class MesRecordDecoder {
    private MesRecordDecoder() {}
    interface Bytes {
        int length();
        int u8(int offset)throws IOException;
        byte[] slice(int start,int end)throws IOException;
    }
    private static Bytes bytes(final byte[] data){return new Bytes(){public int length(){return data.length;}public int u8(int offset)throws IOException{return Binary.u8(data,offset);}public byte[] slice(int start,int end)throws IOException{Binary.range(start,(long)end-start,data.length);return Arrays.copyOfRange(data,start,end);}};}
    private static void range(Bytes data,int offset,int length)throws IOException{Binary.range(offset,length,data.length());}
    private static int endZ(Bytes data,int start)throws IOException{for(int n=start;n<data.length();n++)if(data.u8(n)==0)return n;throw new IOException("Unterminated string");}
    private static int i16(Bytes data,int at)throws IOException{return(short)(data.u8(at)|data.u8(at+1)<<8);}

    static int end(byte[] data,int offset,Profile profile,boolean signedOpcode)throws IOException {
        return end(bytes(data),offset,profile,signedOpcode);
    }
    static int end(Bytes data,int offset,Profile profile,boolean signedOpcode)throws IOException {
        int raw=data.u8(offset);
        int opcode=signedOpcode?(byte)raw:raw;
        if(opcode<profile.string8Start){range(data,offset,3);return offset+3;}
        if(opcode<profile.wordsStart){
            return endZ(data,offset+(opcode<profile.stringStart?2:1))+1;
        }
        range(data,offset,9);return offset+9;
    }

    static MesScript.Record read(byte[] data,int offset,Profile profile)throws IOException {
        return read(bytes(data),offset,profile);
    }
    static MesScript.Record read(Bytes data,int offset,Profile profile)throws IOException {
        int raw=data.u8(offset);
        if(profile.allowTerminalFragment&&raw==0&&data.length()-offset<3){
            // Preserve only operands actually present. STOP does not advance PC
            // or use them; a missing byte is not a fabricated zero operand.
            int[] args=new int[data.length()-offset-1];
            for(int n=0;n<args.length;n++)args[n]=data.u8(offset+1+n);
            return new MesScript.Record(offset,data.length(),raw,args,new byte[0]);
        }
        int opcode=profile.signedFetchOpcode?(byte)raw:raw;
        int end=end(data,offset,profile,profile.signedFetchOpcode);
        int[] args;byte[] bytes=new byte[0];
        if(opcode<profile.string8Start){args=new int[]{data.u8(offset+1),data.u8(offset+2)};}
        else if(opcode<profile.wordsStart){
            int start=offset+(opcode<profile.stringStart?2:1);
            args=start==offset+2?new int[]{data.u8(offset+1)}:new int[0];
            bytes=data.slice(start,end-1);
        }else{
            args=new int[4];for(int n=0;n<4;n++)args[n]=i16(data,offset+1+2*n);
        }
        return new MesScript.Record(offset,end,raw,args,bytes);
    }
    /** Old handlers skip the next byte without checking its opcode, then read four i16. */
    static MesScript.Record payload(byte[] data,int offset,int owner)throws IOException {
        return payload(bytes(data),offset,owner);
    }
    static MesScript.Record payload(Bytes data,int offset,int owner)throws IOException {
        range(data,offset,9);int[] args=new int[4];
        for(int n=0;n<4;n++)args[n]=i16(data,offset+1+2*n);
        return new MesScript.Record(offset,offset+9,data.u8(offset),args,new byte[0],owner);
    }
    /** An owning handler skips one byte, then consumes raw bytes through NUL.
     * The skipped byte is not interpreted as an instruction or a text codec tag. */
    static MesScript.Record stringPayload(byte[] data,int offset,int owner)throws IOException {
        return stringPayload(bytes(data),offset,owner);
    }
    static MesScript.Record stringPayload(Bytes data,int offset,int owner)throws IOException {
        int opcode=data.u8(offset),end=endZ(data,offset+1)+1;
        return new MesScript.Record(offset,end,opcode,new int[0],data.slice(offset+1,end-1),owner);
    }
}
