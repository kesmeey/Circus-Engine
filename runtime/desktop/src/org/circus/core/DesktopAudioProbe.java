package org.circus.core;
import javax.sound.sampled.*;

/** Silent self-authored PCM checks the PC platform sink without playing game content. */
public final class DesktopAudioProbe {
    public static void main(String[] args)throws Exception {
        Clip clip=AudioSystem.getClip();
        try{clip.open(new AudioFormat(44100,16,2,true,false),new byte[44100*4],0,44100*4);clip.start();Thread.sleep(250);int frames=clip.getFramePosition();if(frames<=0)throw new IllegalStateException("Audio sink did not advance");System.out.println(Json.text(Json.obj("pcm_sink_opened",true,"frames_advanced",frames,"audible_game_audio_verified",false)));}
        finally{clip.close();}
    }
}
