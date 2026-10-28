package org.circus.core;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/** Bounded binary cursor used by the Unity 5 resource readers. */
final class UnityBinary {
    final byte[] data; int position; final boolean little;
    UnityBinary(byte[] data,boolean little){this.data=data;this.little=little;}
    void need(int count)throws IOException{if(count<0||position<0||count>data.length-position)throw new EOFException("Truncated Unity structure");}
    int u8()throws IOException{need(1);return data[position++]&255;}
    int u16()throws IOException{int a=u8(),b=u8();return little?a|b<<8:a<<8|b;}
    int i32()throws IOException{int a=u16(),b=u16();return little?a|b<<16:a<<16|b;}
    long u32()throws IOException{return i32()&0xffffffffL;}
    long i64()throws IOException{long a=u32(),b=u32();return little?a|b<<32:a<<32|b;}
    float f32()throws IOException{return Float.intBitsToFloat(i32());}
    void skip(int n)throws IOException{need(n);position+=n;}
    void align()throws IOException{skip((-position)&3);}
    byte[] bytes(int n)throws IOException{need(n);byte[] out=Arrays.copyOfRange(data,position,position+n);position+=n;return out;}
    String zero()throws IOException{int start=position;while(u8()!=0)if(position-start>4096)throw new IOException("Unity string limit");return new String(data,start,position-start-1,StandardCharsets.UTF_8);}
    String string()throws IOException{int n=i32();if(n<0||n>1048576)throw new IOException("Unity string length");need(n);String s=new String(data,position,n,StandardCharsets.UTF_8);position+=n;align();return s;}
    int count(int limit,String label)throws IOException{int n=i32();if(n<0||n>limit)throw new IOException("Unity "+label+" count limit");return n;}
    static byte[] read(Archive.Source source,long offset,int length)throws IOException{Binary.range(offset,length,source.size());byte[] b=source.read(offset,length);if(b.length!=length)throw new EOFException("Short Unity resource read");return b;}
}
