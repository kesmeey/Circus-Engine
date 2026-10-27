package org.circus.runtime;
import android.app.Instrumentation;
import android.content.Intent;
import android.os.*;
/** Real-time SurfaceView + AudioTrack test on an explicitly selected emulator. */
public final class PlayerInstrumentation extends Instrumentation {
    private String name;private long startMs;public void onCreate(Bundle args){super.onCreate(args);name=args.getString("movie_file");startMs=Long.parseLong(args.getString("start_ms","0"));start();}
    public void onStart(){Bundle result=new Bundle();MovieTestActivity activity=null;try{
        Intent i=new Intent(getTargetContext(),MovieTestActivity.class).putExtra("movie_file",name).putExtra("start_ms",startMs).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);activity=(MovieTestActivity)startActivitySync(i);final MovieTestActivity a=activity;long begin=SystemClock.uptimeMillis();boolean paused=false;
        while(!a.done&&SystemClock.uptimeMillis()-begin<180000){Thread.sleep(100);if(!paused&&a.movie.playbackMillis>startMs+1000){runOnMainSync(()->a.movie.pauseMovie());Thread.sleep(100);long position=a.movie.playbackMillis;Thread.sleep(800);if(a.movie.playbackMillis-position>50)throw new Exception("Playback moved while paused");runOnMainSync(()->a.movie.resumeMovie());paused=true;}}
        if(a.error!=null)throw a.error;if(!a.done)throw new Exception("Movie completion timed out");if(a.movie.frames<100||!paused)throw new Exception("Movie did not present enough frames");result.putString("stream","PASS realtime MPEG "+name+" frames="+a.movie.frames+" last_frame_ms="+a.movie.playbackMillis+" elapsed_ms="+(SystemClock.uptimeMillis()-begin)+" pause_resume=true\n");finish(-1,result);
    }catch(Throwable e){result.putString("stream","FAIL "+e+"\n");finish(0,result);}finally{if(activity!=null){final MovieTestActivity a=activity;runOnMainSync(a::finish);}}}
}
