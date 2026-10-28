package org.circus.core;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Portable malformed-input and pixel-layout checks, independent of commercial game data. */
public final class UnityResourceTests {
    private static int assertions;
    private interface Action{void run()throws Exception;}
    private static void check(boolean value,String label){assertions++;if(!value)throw new AssertionError(label);}
    private static void reject(Action action,String label)throws Exception{try{action.run();throw new AssertionError(label);}catch(IOException expected){assertions++;}}
    private static Archive.Source source(final byte[] data){return new Archive.Source(){public long size(){return data.length;}public byte[] read(long at,int n)throws IOException{Binary.range(at,n,data.length);return Arrays.copyOfRange(data,(int)at,(int)at+n);}};}
    private static byte[] bundle()throws Exception{
        ByteArrayOutputStream index=new ByteArrayOutputStream();DataOutputStream d=new DataOutputStream(index);d.write(new byte[16]);d.writeInt(2);d.writeInt(3);d.writeInt(3);d.writeShort(0);d.writeInt(3);d.writeInt(3);d.writeShort(0);d.writeInt(1);d.writeLong(1);d.writeLong(4);d.writeInt(0);d.writeBytes("test\0");
        ByteArrayOutputStream header=new ByteArrayOutputStream();DataOutputStream h=new DataOutputStream(header);h.writeBytes("UnityFS\0");h.writeInt(6);h.writeBytes("5.x.x\0");h.writeBytes("5.5.0f3\0");h.writeLong(46+index.size()+6);h.writeInt(index.size());h.writeInt(index.size());h.writeInt(64);h.write(index.toByteArray());h.writeBytes("abcdef");return header.toByteArray();
    }
    private static byte[] largeBundle(int blockSize,int blocks)throws Exception{
        ByteArrayOutputStream index=new ByteArrayOutputStream();DataOutputStream d=new DataOutputStream(index);d.write(new byte[16]);d.writeInt(blocks);for(int i=0;i<blocks;i++){d.writeInt(blockSize);d.writeInt(blockSize);d.writeShort(0);}d.writeInt(1);d.writeLong(0);d.writeLong((long)blockSize*blocks);d.writeInt(0);d.writeBytes("all\0");ByteArrayOutputStream output=new ByteArrayOutputStream();DataOutputStream h=new DataOutputStream(output);h.writeBytes("UnityFS\0");h.writeInt(6);h.writeBytes("5.x.x\0");h.writeBytes("5.5.0f3\0");h.writeLong(46+index.size()+(long)blockSize*blocks);h.writeInt(index.size());h.writeInt(index.size());h.writeInt(64);h.write(index.toByteArray());for(int i=0;i<blocks;i++){byte[] bytes=new byte[blockSize];Arrays.fill(bytes,(byte)i);h.write(bytes);}return output.toByteArray();
    }
    public static int run()throws Exception{
        assertions=0;
        check(Arrays.equals(UnityBundle.unpack(new byte[]{0x30,97,98,99},3,2),new byte[]{97,98,99}),"LZ4 literals");
        check(new String(UnityBundle.unpack(new byte[]{0x12,97,1,0,0},7,3),StandardCharsets.US_ASCII).equals("aaaaaaa"),"LZ4 overlapping match");
        reject(()->UnityBundle.unpack(new byte[]{0,0,0},4,2),"LZ4 zero distance");reject(()->UnityBundle.unpack(new byte[]{0x30,1},3,2),"LZ4 truncated literal");reject(()->UnityBundle.unpack(new byte[]{0x10,1},2,2),"LZ4 short output");reject(()->UnityBundle.unpack(new byte[]{0x1f,1,1,0,(byte)255},20,2),"LZ4 match overflow");
        UnityBundle b=new UnityBundle(source(bundle()));check(Arrays.equals(b.source(b.find("test")).read(0,4),new byte[]{98,99,100,101}),"Cross-block node read");check(b.cachedBytes()==6,"Bounded block cache");b.clearCache();check(b.cachedBytes()==0,"Cache release");reject(()->b.read(5,2),"Bundle bounds");
        UnityBundle many=new UnityBundle(source(largeBundle(128*1024,10)));for(int i=0;i<10;i++)check(many.read((long)i*128*1024,1)[0]==i,"Random block read");check(many.cachedBytes()<=1024*1024,"LRU cache remains within one MiB");check(many.read(0,1)[0]==0,"Evicted block reloaded");UnityBundle raw=new UnityBundle(source(largeBundle(2*1024*1024,1)));raw.read(1048576,16);check(raw.cachedBytes()==0,"Large raw resource block never materialized or cached");
        UnityResources.Texture rgb=new UnityResources.Texture("test",1,2,3,1,new byte[]{(byte)255,0,0,0,0,(byte)255});int[] pixels=rgb.image().argb();check(pixels[0]==0xff0000ff&&pixels[1]==0xffff0000,"RGB channel order and vertical flip");
        UnityResources.Texture rgba=new UnityResources.Texture("test",1,1,4,1,new byte[]{1,2,3,4});check(rgba.image().argb()[0]==0x04010203,"RGBA channel order");
        byte[] dxt=new byte[16];dxt[0]=(byte)255;dxt[8]=0;dxt[9]=(byte)0xf8;check(new UnityResources.Texture("dxt",4,4,12,1,dxt).image().argb()[0]==0xffff0000,"DXT5 red with opaque alpha");
        return assertions;
    }
    public static void main(String[] args)throws Exception{System.out.println("PASS Unity resource tests assertions="+run());}
}
