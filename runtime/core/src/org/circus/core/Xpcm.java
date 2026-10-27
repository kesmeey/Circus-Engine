package org.circus.core;

// XPCM container reference: GARbro, Copyright (C) 2015-2016 by morkt (MIT).
// Full permission and warranty notice in THIRD_PARTY.md.
import java.io.*;
import java.util.Arrays;
import java.util.zip.*;

public final class Xpcm {
    public final String codec;public final byte[] payload;public final int channels,sampleRate,bits;
    private Xpcm(String c,byte[] p,int ch,int hz,int b){codec=c;payload=p;channels=ch;sampleRate=hz;bits=b;}
    static Xpcm pcm(byte[] p,int channels,int rate,int bits){return new Xpcm("pcm",p,channels,rate,bits);}
    public static Xpcm decode(byte[] data)throws IOException{
        if(Binary.u32(data,0)!=0x4d435058L)throw new IOException("Expected XPCM");
        long size=Binary.u32(data,4);if(size==0||size>128*1024*1024)throw new IOException("Invalid decoded audio size");int mode=Binary.u8(data,8);
        if(mode==5){long packed=Binary.u32(data,12);Binary.range(16,packed,data.length);if(Binary.u32(data,16)!=0x5367674fL)throw new IOException("Invalid Ogg payload");return new Xpcm("ogg",Arrays.copyOfRange(data,16,16+(int)packed),0,0,0);}
        if(mode!=0&&mode!=1&&mode!=3)throw new IOException("Unsupported XPCM transform mode "+mode);
        int tag=Binary.u16(data,12),ch=Binary.u16(data,14),hz=(int)Binary.u32(data,16),align=Binary.u16(data,24),bits=Binary.u16(data,26);
        if(tag!=1||ch<1||ch>2||hz<8000||hz>192000||(bits!=8&&bits!=16)||align!=ch*bits/8||size%align!=0)throw new IOException("Invalid PCM wave format");
        if(mode==1||mode==3){if(bits!=16)throw new IOException("XPCM transform requires PCM16");return new Xpcm("pcm",XpcmTransform.decode(data,(int)size,mode,Binary.u8(data,9)),ch,hz,bits);}
        Binary.range(28,size,data.length);return new Xpcm("pcm",Arrays.copyOfRange(data,28,28+(int)size),ch,hz,bits);
    }
    public byte[] wav()throws IOException{
        if(!codec.equals("pcm"))throw new IOException("Not PCM");ByteArrayOutputStream out=new ByteArrayOutputStream();out.write(new byte[]{82,73,70,70});le(out,payload.length+36,4);out.write(new byte[]{87,65,86,69,102,109,116,32});le(out,16,4);le(out,1,2);le(out,channels,2);le(out,sampleRate,4);le(out,sampleRate*channels*bits/8,4);le(out,channels*bits/8,2);le(out,bits,2);out.write(new byte[]{100,97,116,97});le(out,payload.length,4);out.write(payload);return out.toByteArray();
    }
    private static void le(OutputStream out,int value,int count)throws IOException{for(int i=0;i<count;i++)out.write(value>>>(8*i));}
}
