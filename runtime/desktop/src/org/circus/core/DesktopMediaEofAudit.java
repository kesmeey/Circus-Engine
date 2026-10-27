package org.circus.core;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.*;
import java.util.*;

/** Real decoder/output EOF and cancellation test against an authored two-second movie. */
public final class DesktopMediaEofAudit {
    private static Map<String,Object> run(Path fixture,boolean skip)throws Exception{
        GameResources files=new GameResources(new LocalGameStorage(fixture));
        DesktopLegacy e=new DesktopLegacy(new CircusResources(files,Profiles.get("dcpc-2063")),TextCodec.identity(),false);
        try(DesktopMedia media=new DesktopMedia(e)){
            e.host().movie="clip.mpg";e.host().movieGeneration++;e.vm().run(1);
            if(e.vm().status()!=MesVm.Status.WAIT_HOST)throw new IOException("Fixture VM did not suspend for the movie");
            media.sync();long limit=System.nanoTime()+15000000000L,audio=0;Set<Integer> frames=new HashSet<>();
            while(media.playingMovie()&&System.nanoTime()<limit){Thread.sleep(20);media.sync();audio=Math.max(audio,media.movieAudioFrames());BufferedImage frame=media.movieFrame();if(frame!=null)frames.add(Arrays.hashCode(frame.getRGB(0,0,frame.getWidth(),frame.getHeight(),null,0,frame.getWidth())));if(skip&&audio>0&&frames.size()>1)media.finishMovie();}
            if(media.playingMovie()||!e.movie().isEmpty()||e.vm().status()!=MesVm.Status.RUNNING||audio==0||frames.size()<2)throw new IOException("Movie EOF/skip did not restore runnable VM: skip="+skip);
            if(e.vm().run(4)!=MesVm.Status.HALTED)throw new IOException("VM did not execute following instruction");
            return Json.obj("skip",skip,"audio_frames",audio,"distinct_frames",frames.size(),"following_instruction_executed",true);
        }
    }
    public static void main(String[] args)throws Exception{
        Path out=Paths.get(args[1]);Files.createDirectories(out);System.setProperty("dc.saveRoot",out.resolve("saves").toString());System.setProperty("dc.cacheRoot",out.resolve("cache").toString());
        Map<String,Object> result=Json.obj("natural_eof",run(Paths.get(args[0]),false),"skip",run(Paths.get(args[0]),true),"self_authored_fixture",true,"pass",true);
        Files.write(out.resolve("result.json"),Json.text(result).getBytes(java.nio.charset.StandardCharsets.UTF_8));System.out.println(Json.text(result));
    }
}
