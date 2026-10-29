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
    private static final class Le {
        final ByteArrayOutputStream out=new ByteArrayOutputStream();
        void byteValue(int n){out.write(n);}void i32(int n){for(int i=0;i<4;i++)out.write(n>>>(8*i));}void i64(long n){for(int i=0;i<8;i++)out.write((int)(n>>>(8*i)));}
        void bytes(byte[] bytes){out.write(bytes,0,bytes.length);}void align(){while((out.size()&3)!=0)out.write(0);}
        void string(String value){byte[] bytes=value.getBytes(StandardCharsets.UTF_8);i32(bytes.length);bytes(bytes);align();}
    }
    private static byte[] serialized(String[] names,int[] classes,int gap,boolean container)throws Exception{
        List<byte[]> objects=new ArrayList<>();for(String name:names){Le object=new Le();object.string(name);object.i32(1);object.byteValue(42);objects.add(object.out.toByteArray());}
        if(container){Le object=new Le();object.string("bundle");object.i32(0);object.i32(1);object.string("assets/container_alias.txt");object.i32(0);object.i32(0);object.i32(0);object.i64(1);objects.add(object.out.toByteArray());}
        Le data=new Le();int[] offsets=new int[objects.size()];for(int i=0;i<objects.size();i++){data.align();offsets[i]=data.out.size();data.bytes(objects.get(i));if(i+1<objects.size())data.bytes(new byte[gap]);}
        Le metadata=new Le();metadata.bytes("5.5.0f3\0".getBytes(StandardCharsets.US_ASCII));metadata.i32(5);metadata.byteValue(0);metadata.i32(4);
        for(int type:new int[]{28,49,83,142}){metadata.i32(type);metadata.byteValue(0);metadata.byteValue(0);metadata.byteValue(0);metadata.bytes(new byte[16]);}
        metadata.i32(objects.size());for(int i=0;i<objects.size();i++){metadata.align();metadata.i64(i+1);metadata.i32(offsets[i]);metadata.i32(objects.get(i).length);int type=i<names.length?classes[i]:142;metadata.i32(type==28?0:type==49?1:type==83?2:3);}
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();DataOutputStream header=new DataOutputStream(bytes);header.writeInt(metadata.out.size());header.writeInt(20+metadata.out.size()+data.out.size());header.writeInt(17);header.writeInt(20+metadata.out.size());header.writeInt(0);header.write(metadata.out.toByteArray());header.write(data.out.toByteArray());return bytes.toByteArray();
    }
    private static byte[] wrap(byte[] payload)throws Exception{
        ByteArrayOutputStream index=new ByteArrayOutputStream();DataOutputStream d=new DataOutputStream(index);d.write(new byte[16]);d.writeInt(1);d.writeInt(payload.length);d.writeInt(payload.length);d.writeShort(0);d.writeInt(1);d.writeLong(0);d.writeLong(payload.length);d.writeInt(0);d.writeBytes("CAB-test\0");
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();DataOutputStream h=new DataOutputStream(bytes);h.writeBytes("UnityFS\0");h.writeInt(6);h.writeBytes("5.x.x\0");h.writeBytes("5.5.0f3\0");h.writeLong(46+index.size()+payload.length);h.writeInt(index.size());h.writeInt(index.size());h.writeInt(64);h.write(index.toByteArray());h.write(payload);return bytes.toByteArray();
    }
    private static void nameIndexTests()throws Exception{
        String[] names=new String[1200];int[] types=new int[names.length];Arrays.fill(types,28);for(int n=0;n<names.length;n++)names[n]="texture_"+n;
        byte[] bytes=serialized(names,types,1100,false);final long[] counts=new long[3];Archive.Source counted=new Archive.Source(){public long size(){return bytes.length;}public byte[] read(long at,int length)throws IOException{Binary.range(at,length,bytes.length);counts[0]++;counts[1]+=length;counts[2]=Math.max(counts[2],length);return Arrays.copyOfRange(bytes,(int)at,(int)at+length);}};
        UnitySerializedFile file=new UnitySerializedFile(counted);Arrays.fill(counts,0);Map<UnitySerializedFile.ObjectInfo,String> indexed=file.names(28);
        check(indexed.size()==1200&&indexed.get(file.objects.get(0)).equals("texture_0")&&indexed.get(file.objects.get(1199)).equals("texture_1199"),"Batched fallback retains first and last object names");
        check(counts[0]<=6&&counts[2]<=256*1024,"1200 adjacent names use bounded batches, not 1200 document reads");
        byte[] separated=serialized(new String[]{"left","right"},new int[]{49,49},1024*1024,false);final long[] readBytes={0};Archive.Source sparse=new Archive.Source(){public long size(){return separated.length;}public byte[] read(long at,int n)throws IOException{Binary.range(at,n,separated.length);readBytes[0]+=n;return Arrays.copyOfRange(separated,(int)at,(int)at+n);}};
        UnitySerializedFile spaced=new UnitySerializedFile(sparse);readBytes[0]=0;check(spaced.names(49).size()==2&&readBytes[0]<100,"Fallback does not read large gaps or intervening payloads");
        byte[] invalid=serialized(new String[]{"valid"},new int[]{49},0,false);int at=java.nio.ByteBuffer.wrap(invalid).getInt(12);invalid[at]=(byte)0xff;invalid[at+1]=(byte)0xff;invalid[at+2]=(byte)0xff;invalid[at+3]=0x7f;
        reject(()->new UnitySerializedFile(source(invalid)).names(49),"Malformed object name still fails during batched fallback");
        final byte[] data=wrap(serialized(new String[]{"custom_object_name","unlisted_asset","duplicate","duplicate","texture"},new int[]{49,49,49,49,28},0,true));
        GameResources.Storage storage=new GameResources.Storage(){public Set<String> paths(){return Collections.singleton("script0");}public byte[] read(String name,int limit){return data;}public Archive.Source source(String name){return UnityResourceTests.source(data);}};
        UnityResources resources=new UnityResources(new GameResources(storage,"@none"));
        check(resources.contains("script0","container_alias",49)&&resources.text("script0","container_alias")[0]==42,"Exact container aliases keep priority");
        check(resources.contains("script0","custom_object_name",49)&&resources.contains("script0","unlisted_asset",49),"Fallback preserves custom object names and incomplete containers");
        check(!resources.contains("script0","container_alias",28)&&!resources.contains("script0","missing/context",49)&&!resources.contains("script0","missing",49),"Wrong types and missing names or contexts remain absent");
        reject(()->resources.text("script0","duplicate"),"Ambiguous names remain rejected after batched indexing");
    }
    private static void readerScopeTests()throws Exception{
        Archive.Source memory=source(largeBundle(2*1024*1024,1));int[] counts=new int[4];
        Archive.Source tracked=new Archive.Source(){public long size()throws IOException{return memory.size();}public byte[] read(long at,int n)throws IOException{counts[2]++;return memory.read(at,n);}
            public Archive.Reader openReader(){counts[0]++;return new Archive.Reader(){boolean closed;public long size()throws IOException{if(closed)throw new IOException("Closed reader");return memory.size();}public byte[] read(long at,int n)throws IOException{if(closed)throw new IOException("Closed reader");counts[3]++;return memory.read(at,n);}public void close(){closed=true;counts[1]++;}};}};
        UnityBundle bundle=new UnityBundle(tracked);int direct=counts[2];
        bundle.withReader(()->{bundle.read(13,5);return bundle.withReader(()->bundle.read(1900000,5));});
        check(counts[0]==1&&counts[1]==1&&counts[2]==direct&&counts[3]==2,"Nested name reads share one scoped source handle and close it");
        reject(()->bundle.withReader(()->{bundle.read(25,3);throw new IOException("Expected index failure");}),"Index read failure propagates");
        check(counts[0]==2&&counts[1]==2,"Failed index closes its source handle");bundle.read(30,4);
        check(counts[2]==direct+1,"Reads after failed batch use normal source rather than a closed handle");
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
        nameIndexTests();
        readerScopeTests();
        return assertions;
    }
    public static void main(String[] args)throws Exception{System.out.println("PASS Unity resource tests assertions="+run());}
}
