package org.circus.core;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.util.*;
import javax.imageio.ImageIO;

/** Exercises actual desktop decoders/output; never launches the Windows game. */
public final class DesktopMediaAudit {
    public static void main(String[] args)throws Exception{
        Path out=Paths.get(args[1]);Files.createDirectories(out);System.setProperty("dc.saveRoot",out.resolve("probe-saves").toString());
        try(DesktopRuntime app=new DesktopRuntime(Paths.get(args[0]),false)){
            DesktopEngine e=app.engine;String music=e.resources.named("music",1);String movie="";for(String p:new TreeSet<>(e.resources.files.paths()))if(p.endsWith(".mpg")||p.endsWith(".mpeg")){movie=p;break;}if(movie.isEmpty())throw new Exception("No original movie");
            if(e instanceof DesktopLegacy){Dc2Host h=((DesktopLegacy)e).host();h.audio[0]=music;h.audioActive[0]=true;h.audioLoop[0]=true;h.audioGeneration[0]++;}else{Dc3Host h=((DesktopDd)e).host;h.command("audio.music",new int[]{1,0},new byte[0]);h.command("audio.restore_volume",new int[]{254,0},new byte[0]);}
            app.media.sync();Thread.sleep(1200);app.media.sync();long samples=app.media.audioFrames(0);if(samples<=0)throw new Exception("Audio output did not consume frames");
            Path snapshot=out.resolve("music-cursor.sav");e.save(snapshot);DesktopSave metadata=DesktopSave.decode(Files.readAllBytes(snapshot));long savedCursor=metadata.position[0];if(savedCursor<500000)throw new Exception("Save did not retain actual output cursor");
            e.restore(snapshot);app.media.reset();app.media.sync();long restoredCursorFrames=app.media.audioFrames(0);if(restoredCursorFrames<samples-1000)throw new Exception("Music resumed from beginning");Thread.sleep(150);app.media.sync();if(app.media.audioFrames(0)<=restoredCursorFrames)throw new Exception("Restored music did not advance");
            if(e instanceof DesktopLegacy){Dc2Host h=((DesktopLegacy)e).host();h.movie=movie;h.movieGeneration++;}else{Dc3Host h=((DesktopDd)e).host;h.movie=movie;h.movieGeneration++;}
            app.media.sync();Set<Integer> frames=new HashSet<>();long audioFrames=0;for(int n=0;n<120;n++){Thread.sleep(50);app.media.sync();BufferedImage frame=app.media.movieFrame();audioFrames=Math.max(audioFrames,app.media.movieAudioFrames());if(frame!=null){frames.add(Arrays.hashCode(frame.getRGB(0,0,e.width,e.height,null,0,e.width)));if(n==100)ImageIO.write(frame,"png",out.resolve("movie.png").toFile());}}
            if(frames.size()<2||audioFrames==0)throw new Exception("Movie video/audio did not advance");app.media.finishMovie();
            Map<String,Object> result=Json.obj("profile",e.resources.profile.id,"music",music,"audio_frames_consumed",samples,"saved_audio_microseconds",savedCursor,"restored_audio_frames",restoredCursorFrames,"audio_resume_verified",true,"movie",movie,"distinct_video_frames",frames.size(),"movie_audio_frames_consumed",audioFrames,"natural_movie_end_tested",false,"original_exe_run",false,"pass",true);
            Files.write(out.resolve("result.json"),Json.text(result).getBytes(java.nio.charset.StandardCharsets.UTF_8));System.out.println(Json.text(result));
        }
    }
}
