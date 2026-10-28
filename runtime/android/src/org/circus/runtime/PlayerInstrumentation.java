package org.circus.runtime;
import android.app.Instrumentation;
import android.content.Intent;
import android.os.*;
/** Real-time player regression, optionally stressing video without delaying audio. */
public final class PlayerInstrumentation extends Instrumentation {
    private String name;private long startMs,limitMs;private int stallMs,maxUnderruns;
    public void onCreate(Bundle args){super.onCreate(args);name=args.getString("movie_file");startMs=Long.parseLong(args.getString("start_ms","0"));limitMs=Long.parseLong(args.getString("limit_ms","0"));stallMs=Integer.parseInt(args.getString("video_stall_ms","0"));maxUnderruns=Integer.parseInt(args.getString("max_underruns","-1"));start();}
    public void onStart(){Bundle result=new Bundle();MovieTestActivity activity=null;try{
        Intent i=new Intent(getTargetContext(),MovieTestActivity.class).putExtra("movie_file",name).putExtra("start_ms",startMs).putExtra("video_stall_ms",stallMs).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        activity=(MovieTestActivity)startActivitySync(i);final MovieTestActivity a=activity;long begin=SystemClock.uptimeMillis();boolean paused=false;
        while(!a.done&&SystemClock.uptimeMillis()-begin<180000){
            Thread.sleep(50);
            if(!paused&&a.movie.playbackMillis>startMs+1000){
                runOnMainSync(()->a.movie.pauseMovie());Thread.sleep(100);long position=a.movie.playbackMillis;
                Thread.sleep(800);if(a.movie.playbackMillis-position>50)throw new Exception("Playback moved while paused");
                runOnMainSync(()->a.movie.resumeMovie());paused=true;
            }
            if(limitMs>0&&SystemClock.uptimeMillis()-begin>=limitMs)break;
        }
        if(a.error!=null)throw a.error;if(limitMs==0&&!a.done)throw new Exception("Movie completion timed out");
        if(a.movie.frames<100||!paused)throw new Exception("Movie did not present enough frames");
        if(a.done&&a.movie.audioPlayedFrames<a.movie.audioWrittenFrames)throw new Exception("Audio tail was truncated");
        int underruns=a.movie.audioUnderruns;
        if(maxUnderruns>=0&&underruns>maxUnderruns)throw new Exception("Audio underruns="+underruns+" allowed="+maxUnderruns);
        long elapsed=SystemClock.uptimeMillis()-begin,position=a.movie.playbackMillis;
        runOnMainSync(()->a.movie.close());long closeAt=SystemClock.uptimeMillis();
        while(!a.movie.workersStopped&&SystemClock.uptimeMillis()-closeAt<3000)Thread.sleep(10);
        if(!a.movie.workersStopped)throw new Exception("Movie workers did not stop");
        if(limitMs>0&&a.done)throw new Exception("Unexpected callback after skip");
        result.putString("stream","PASS realtime MPEG "+name+" start_ms="+startMs+" frames="+a.movie.frames+" decoded="+a.movie.decodedFrames+" dropped="+a.movie.droppedFrames+" last_frame_ms="+position+" elapsed_ms="+elapsed+" pause_resume=true completion="+a.done+" workers_stopped=true video_stall_ms="+stallMs+" audio_underruns="+underruns+" audio_written_frames="+a.movie.audioWrittenFrames+" audio_played_frames="+a.movie.audioPlayedFrames+"\n");finish(-1,result);
    }catch(Throwable e){result.putString("stream","FAIL "+e+"\n");finish(0,result);}finally{if(activity!=null){final MovieTestActivity a=activity;runOnMainSync(a::finish);}}}
}
