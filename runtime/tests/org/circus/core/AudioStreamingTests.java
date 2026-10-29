package org.circus.core;

import java.io.*;
import java.nio.*;
import java.util.*;
import java.util.zip.*;

/** Bounded playback export regressions; all sounds are synthetic. */
public final class AudioStreamingTests {
    private static int checks;
    private static void check(boolean value,String why){checks++;if(!value)throw new AssertionError(why);}
    private interface Work{void run()throws Exception;}
    private static void reject(Work work,String why)throws Exception{boolean rejected=false;try{work.run();}catch(IOException expected){rejected=true;}check(rejected,why);}
    private static final class Bytes implements Archive.Source {
        final byte[] data;int maxRead,reads,copies;boolean shortRead;
        Bytes(byte[] data){this.data=data;}
        public long size(){return data.length;}
        public byte[] read(long at,int n)throws IOException{Binary.range(at,n,data.length);maxRead=Math.max(maxRead,n);reads++;return Arrays.copyOfRange(data,(int)at,(int)at+n-(shortRead&&at>=28?1:0));}
        public void copyTo(long at,long n,OutputStream out)throws IOException{copies++;Archive.Source.super.copyTo(at,n,out);}
    }
    private static byte[] pcm(int size,int channels,int bits){
        ByteBuffer out=ByteBuffer.allocate(28+size).order(ByteOrder.LITTLE_ENDIAN);
        out.putInt(0x4d435058).putInt(size).putInt(0).putShort((short)1).putShort((short)channels).putInt(44100).putInt(44100*channels*bits/8).putShort((short)(channels*bits/8)).putShort((short)bits);
        for(int n=0;n<size;n++)out.put((byte)(n*17+31));return out.array();
    }
    private static byte[] ogg(int size){
        ByteBuffer out=ByteBuffer.allocate(16+size+13).order(ByteOrder.LITTLE_ENDIAN);
        out.putInt(0x4d435058).putInt(size*2).putInt(5).putInt(size).put(new byte[]{'O','g','g','S'});
        for(int n=4;n<size;n++)out.put((byte)(n*13+7));return out.array();
    }
    private static byte[] transformed(int mode)throws IOException{
        byte[] spectrum=new byte[8192],packed;
        if(mode==3){ByteArrayOutputStream bytes=new ByteArrayOutputStream();try(DeflaterOutputStream out=new DeflaterOutputStream(bytes)){out.write(spectrum);}packed=bytes.toByteArray();}
        else {ByteArrayOutputStream bytes=new ByteArrayOutputStream();for(int n=0;n<spectrum.length;n+=8){bytes.write(255);bytes.write(spectrum,n,8);}packed=bytes.toByteArray();}
        ByteBuffer out=ByteBuffer.allocate(32+packed.length).order(ByteOrder.LITTLE_ENDIAN);
        out.putInt(0x4d435058).putInt(882).putInt(mode|3<<8).putShort((short)1).putShort((short)1).putInt(44100).putInt(88200).putShort((short)2).putShort((short)16).putInt(packed.length).put(packed);return out.array();
    }
    private static byte[] export(byte[] nativeBytes)throws IOException{ByteArrayOutputStream out=new ByteArrayOutputStream();Xpcm.write(new Bytes(nativeBytes),out);return out.toByteArray();}
    private static GameResources files(Map<String,byte[]> values)throws IOException{
        return new GameResources(new GameResources.Storage(){
            public Set<String> paths(){return values.keySet();}
            public byte[] read(String name,int limit)throws IOException{byte[] bytes=values.get(name);if(bytes==null)throw new FileNotFoundException(name);if(bytes.length>limit)throw new IOException("Test resource limit");return bytes.clone();}
            public Archive.Source source(String name)throws IOException{byte[] bytes=values.get(name);if(bytes==null)throw new FileNotFoundException(name);return new Bytes(bytes);}
        });
    }
    public static int run(CoreTests.Assets assets)throws Exception{
        checks=0;
        for(int channels:new int[]{1,2})for(int bits:new int[]{8,16}){
            byte[] source=pcm(262148,channels,bits);Bytes chunks=new Bytes(source);ByteArrayOutputStream out=new ByteArrayOutputStream();Xpcm.write(chunks,out);
            Xpcm expected=Xpcm.decode(source);check(Arrays.equals(expected.wav(),out.toByteArray()),"Streamed PCM format and every sample match buffered decoder: "+channels+"/"+bits);
            check(chunks.maxRead<=65536&&chunks.reads>4,"Long PCM cannot issue a whole-resource allocation");
            ByteArrayOutputStream decoded=new ByteArrayOutputStream();expected.writeTo(decoded);check(Arrays.equals(out.toByteArray(),decoded.toByteArray()),"Decoded sound export preserves WAV bytes");
        }
        byte[] source=ogg(196613);Bytes chunks=new Bytes(source);ByteArrayOutputStream out=new ByteArrayOutputStream();Xpcm.write(chunks,out);
        check(Arrays.equals(Xpcm.decode(source).payload,out.toByteArray()),"Ogg stream excludes wrapper and archive padding");
        check(chunks.maxRead<=65536&&chunks.reads>3,"Ogg source reads stay bounded");
        ByteArrayOutputStream decoded=new ByteArrayOutputStream();Xpcm.decode(source).writeTo(decoded);check(Arrays.equals(decoded.toByteArray(),out.toByteArray()),"Already decoded Ogg stays byte-identical");
        for(int mode:new int[]{1,3}){byte[] encoded=transformed(mode);check(Arrays.equals(Xpcm.decode(encoded).wav(),export(encoded)),"Transform mode "+mode+" retains authoritative sample decoder");}
        for(int size:new int[]{0,15,27})reject(()->export(new byte[size]),"Truncated XPCM header rejected: "+size);
        byte[] invalid=pcm(16,1,16);invalid[0]=0;reject(()->export(invalid),"Wrong container signature rejected");
        byte[] badAlign=pcm(16,1,16);badAlign[24]=4;reject(()->export(badAlign),"Malformed PCM block alignment rejected");
        byte[] badSize=pcm(16,1,16);badSize[4]=15;reject(()->export(badSize),"Partial PCM sample rejected");
        byte[] badMode=pcm(16,1,16);badMode[8]=2;reject(()->export(badMode),"Unverified transform mode rejected");
        byte[] good=pcm(32,2,16);reject(()->export(Arrays.copyOf(good,good.length-1)),"Truncated PCM payload rejected before export");
        byte[] badOgg=ogg(32);badOgg[16]=0;reject(()->export(badOgg),"Invalid embedded Ogg signature rejected");
        byte[] shortOgg=Arrays.copyOf(ogg(32),47);reject(()->export(shortOgg),"Truncated embedded Ogg span rejected");
        Bytes shortReads=new Bytes(pcm(128,1,16));shortReads.shortRead=true;reject(()->Xpcm.write(shortReads,new ByteArrayOutputStream()),"Source short-read cannot silently truncate a song");
        Archive sample=new Archive(new Bytes(assets.read("sample.pck")));Archive.Entry item=sample.entries.get(0);Archive.Source view=sample.source(item);
        check(view.size()==item.length&&Arrays.equals(view.read(0,item.length),sample.read(item)),"Archive view exposes exactly its selected entry");
        reject(()->view.read(-1,1),"Archive view rejects negative offset");reject(()->view.read(view.size()-1,2),"Archive view cannot read next entry");
        ByteArrayOutputStream archived=new ByteArrayOutputStream();Xpcm.write(view,archived);check(Arrays.equals(Xpcm.decode(sample.read(item)).wav(),archived.toByteArray()),"Archived sound exports unchanged WAV");
        reject(()->view.copyTo(view.size()-1,2,new ByteArrayOutputStream()),"Archive copy cannot escape the selected entry");
        Map<String,byte[]> values=new LinkedHashMap<>();values.put("AdvData/DAT/NAMES.DAT",new byte[20]);values.put("AdvData/MUSIC/test.PCM",good);
        // Legacy voice entry length includes native padding, which is excluded
        // by the XPCM payload length when producing a playable WAV.
        ByteBuffer voice=ByteBuffer.allocate(68+good.length+16).order(ByteOrder.LITTLE_ENDIAN);voice.putInt(1).put("sample.PCM".getBytes(Binary.SJIS));voice.position(64);voice.putInt(68).put(good);values.put("AdvData/VOICE.DAT",voice.array());
        CircusResources resources=new CircusResources(files(values),Profiles.get("dc2pc-5769"));
        for(String reference:new String[]{"music/test","VOICE/SAMPLE.wav"}){ByteArrayOutputStream stream=new ByteArrayOutputStream();resources.writeAudio(reference,stream);check(Arrays.equals(Xpcm.decode(good).wav(),stream.toByteArray()),"Resource resolution preserves loose and legacy voice semantics: "+reference);}
        ByteBuffer primary=ByteBuffer.allocate(132+good.length+16).order(ByteOrder.LITTLE_ENDIAN);primary.putInt(2).put("first.PCM".getBytes(Binary.SJIS));primary.position(64);primary.putInt(132).put("second.PCM".getBytes(Binary.SJIS));primary.position(128);primary.putInt(1).put(good);
        Bytes bank1=new Bytes(primary.array()),bank2=new Bytes(good);Archive split=new Archive(bank1,bank2);
        for(Archive.Entry entry:split.entries){ByteArrayOutputStream stream=new ByteArrayOutputStream();Xpcm.write(split.source(entry),stream);check(Arrays.equals(Xpcm.decode(good).wav(),stream.toByteArray()),"Split voice bank exports selected sound: "+entry.name);}
        check(bank1.copies==1&&bank2.copies==1,"Entry views preserve platform streaming copy across both voice banks");
        final int[] overrides={0};CircusResources resourceOnly=new CircusResources(files(values),"test-resource-only"){@Override public Xpcm audio(String reference)throws IOException{overrides[0]++;return Xpcm.ogg(new byte[]{'O','g','g','S',7});}};
        ByteArrayOutputStream resourceStream=new ByteArrayOutputStream();resourceOnly.writeAudio("backend:sound",resourceStream);check(overrides[0]==1&&Arrays.equals(resourceStream.toByteArray(),new byte[]{'O','g','g','S',7}),"Resource-only audio override remains authoritative");
        CircusResources mixed=new CircusResources(files(values),Profiles.get("dc2pc-5769")){@Override public Xpcm audio(String reference)throws IOException{overrides[0]++;return Xpcm.decode(good);}};
        ByteArrayOutputStream mixStream=new ByteArrayOutputStream();mixed.writeAudio("mix:sample",mixStream);check(overrides[0]==2&&Arrays.equals(mixStream.toByteArray(),Xpcm.decode(good).wav()),"Mixed voice retains sample-wise backend");
        return checks;
    }
}
