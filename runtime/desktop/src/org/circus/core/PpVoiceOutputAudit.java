package org.circus.core;

import java.io.*;
import java.util.*;
import java.awt.image.BufferedImage;

/** Shared presentation adapter regression; no audio device or original game files required. */
public final class PpVoiceOutputAudit {
    private static int checks;
    private static void check(boolean value,String why){checks++;if(!value)throw new AssertionError(why);}
    private static CircusResources fixture()throws IOException{
        byte[] names=new byte[68];names[4]=1;byte[] track="music/fixture".getBytes(Binary.SJIS);System.arraycopy(track,0,names,8,track.length);
        final Map<String,byte[]> bytes=new HashMap<>();bytes.put("AdvData/GRP/NAMES.DAT",names);bytes.put("AdvData/MES/START.MES",new byte[]{0,0,0,0,0x72,(byte)0x98,0,0,0,0});
        GameResources files=new GameResources(new GameResources.Storage(){
            public Set<String> paths(){return bytes.keySet();}public byte[] read(String path,int limit)throws IOException{byte[] value=bytes.get(path);if(value==null||value.length>limit)throw new IOException("Fixture input "+path);return value.clone();}
            public Archive.Source source(String path){final byte[] value=bytes.get(path);return new Archive.Source(){public long size(){return value.length;}public byte[] read(long start,int length)throws IOException{Binary.range(start,length,value.length);return Arrays.copyOfRange(value,(int)start,(int)start+length);}};}
        });return new CircusResources(files,Profiles.get("dc3pp-9872"));
    }
    public static void main(String[] args)throws Exception{
        DesktopDd engine=new DesktopDd(fixture(),TextCodec.identity(),true);
        engine.host.command("audio.system_voice",new int[0],"<VOICE\\example_RCC".getBytes(Binary.SJIS));
        float gain=engine.volume(5);check(engine.audioActive(5)&&engine.audio(5).equals("VOICE\\example_RCC")&&engine.pan(5)==-1000,"output uses shared voice channel, resolved resource and native pan");
        check(engine.host.read(99,203)==0&&Math.abs(gain-0.56363797f)<0.000001f,"nonexistent DD master-enable does not silence PP voice or rescale it twice");
        check(engine.volume(0)==0&&!engine.audioActive(0),"an absent native BGM buffer has no output gain");
        engine.host.command("pp.audio.music",new int[]{0,0,0},new byte[0]);
        check(engine.audioActive(0)&&Math.abs(engine.volume(0)-PpVoice.gain(0,20))<0.000001f&&Math.abs(engine.volume(1)-PpVoice.gain(0,23))<0.000001f,"submitted PP BGM and SE use their native curve without the DD master multiplier");
        engine.host.write(99,5,0);engine.host.write(99,9,0);check(engine.volume(5)==gain&&engine.audioActive(5),"output retains submission gain after preference writes");
        byte[] saved=engine.snapshot();BufferedImage image=engine.render();check(image.getWidth()==1024&&image.getHeight()==576,"real renderer accepts versioned PP state");image.flush();
        engine.restore(saved);check(engine.volume(5)==gain&&engine.audioActive(5)&&engine.pan(5)==-1000,"presentation restore retains captured voice gain and pan independently of current preferences");
        System.out.println("PASS "+checks+" PP voice output assertions");
    }
}
