package org.circus.runtime;

import android.app.*;
import android.content.*;
import android.os.*;
import android.view.*;
import android.graphics.Bitmap;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import org.circus.core.*;
import org.circus.graphics.BufferedImage;

/** Real Activity/input/Canvas/audio checks against ranges recorded from user-owned assets. */
public final class UnityInstrumentation extends Instrumentation {
    private RuntimeActivity activity;private String pack,movieDirectory,tree;private boolean realtime;private int checks;private File output;private final StringBuilder log=new StringBuilder();
    public void onCreate(Bundle args){super.onCreate(args);pack=args.getString("pack","dc3r-audit.pack");movieDirectory=args.getString("movies","dc3r-audit-movies");tree=args.getString("tree");realtime="true".equalsIgnoreCase(args.getString("realtime"));start();}
    interface Job<T>{T run(DesktopUnity engine)throws Exception;}
    private <T>T call(Job<T> job)throws Exception{if(activity.failed)throw new IOException(activity.error);return activity.worker.submit(()->job.run((DesktopUnity)activity.engine)).get(60,TimeUnit.SECONDS);}
    private void check(boolean ok,String message)throws IOException{if(!ok)throw new IOException(message);checks++;log.append("PASS ").append(message).append('\n');}
    private List<String> actions()throws Exception{return call(e->{List<String> a=new ArrayList<>();for(DesktopEngine.Control c:e.controls())a.add(c.action);return a;});}
    private void touch(int x,int y)throws Exception{runOnMainSync(()->{RuntimeActivity.Stage v=activity.stage;float s=Math.min(v.getWidth()/1024f,v.getHeight()/576f),px=(v.getWidth()-1024*s)/2+x*s,py=(v.getHeight()-576*s)/2+y*s;long t=SystemClock.uptimeMillis();MotionEvent a=MotionEvent.obtain(t,t,MotionEvent.ACTION_DOWN,px,py,0),b=MotionEvent.obtain(t,t+30,MotionEvent.ACTION_UP,px,py,0);v.dispatchTouchEvent(a);v.dispatchTouchEvent(b);a.recycle();b.recycle();});call(e->null);}
    private void select(String action)throws Exception{DesktopEngine.Control c=call(e->{for(DesktopEngine.Control b:e.controls())if(b.action.equals(action))return b;throw new IOException("Missing original action "+action);});touch((c.left+c.right)/2,(c.top+c.bottom)/2);}
    private void screenshot(String name)throws Exception{call(e->{BufferedImage b=e.render();try(FileOutputStream out=new FileOutputStream(new File(output,name+".png"))){if(!b.bitmap.compress(Bitmap.CompressFormat.PNG,100,out))throw new IOException("PNG encode failed");}finally{b.flush();}return null;});}
    private <T>T ui(Callable<T> job)throws Exception{FutureTask<T> task=new FutureTask<>(job);runOnMainSync(task);return task.get(10,TimeUnit.SECONDS);}
    private java.lang.reflect.Field field(String name)throws Exception{java.lang.reflect.Field f=RuntimeActivity.class.getDeclaredField(name);f.setAccessible(true);return f;}
    private android.widget.VideoView platformMovie()throws Exception{return ui(()->(android.widget.VideoView)field("platformMovie").get(activity));}
    private android.widget.VideoView beginMovie(String name)throws Exception{
        File directory=new File(getTargetContext().getFilesDir(),movieDirectory).getCanonicalFile();if(!directory.toPath().startsWith(getTargetContext().getFilesDir().getCanonicalFile().toPath()))throw new IOException("Movie audit directory escapes app files");
        File input=new File(directory,name);if(!input.isFile())throw new FileNotFoundException("Copy original movie for audit: "+input);
        File copy=File.createTempFile("dc3r-movie-audit-",".mp4",getTargetContext().getCacheDir());Files.copy(input.toPath(),copy.toPath(),StandardCopyOption.REPLACE_EXISTING);
        // Enter the actual VideoView implementation. The artificial ticket must not
        // resume the story VM when this independent decoder check completes.
        ui(()->{java.lang.reflect.Field token=field("movieToken");int next=token.getInt(activity)+1;token.setInt(activity,next);field("movieFile").set(activity,copy);java.lang.reflect.Method start=RuntimeActivity.class.getDeclaredMethod("startPlatformMovie",File.class,int.class,long.class);start.setAccessible(true);start.invoke(activity,copy,next,Long.MIN_VALUE);return null;});
        long end=SystemClock.uptimeMillis()+15000;android.widget.VideoView video;
        do{if(activity.failed)throw new IOException(activity.error);video=platformMovie();if(video!=null){final android.widget.VideoView candidate=video;if(ui(()->candidate.isPlaying()&&candidate.getDuration()>0))return video;}Thread.sleep(50);}while(SystemClock.uptimeMillis()<end);
        throw new IOException("VideoView did not begin playback: "+name);
    }
    private void movieAudit()throws Exception{
        android.widget.VideoView logo=beginMovie("mgheart.mp4");int logoDuration=ui(()->logo.getDuration()),maxPosition=0;long end=SystemClock.uptimeMillis()+15000;
        while(platformMovie()!=null&&SystemClock.uptimeMillis()<end){if(activity.failed)throw new IOException(activity.error);maxPosition=Math.max(maxPosition,ui(()->logo.getCurrentPosition()));Thread.sleep(100);}
        check(platformMovie()==null&&maxPosition>=Math.min(3500,logoDuration-500),"mgheart MP4 advances and naturally completes through VideoView callback");log.append("MOVIE mgheart duration_ms=").append(logoDuration).append(" observed_position_ms=").append(maxPosition).append('\n');
        android.widget.VideoView opening=beginMovie("dc3_v1.mp4");end=SystemClock.uptimeMillis()+15000;
        while(ui(()->opening.getCurrentPosition())<1500&&SystemClock.uptimeMillis()<end){if(activity.failed)throw new IOException(activity.error);Thread.sleep(100);}
        int before=ui(()->opening.getCurrentPosition());check(before>=1500,"dc3_v1 H.264/AAC MP4 advances on the real VideoView");
        runOnMainSync(()->callActivityOnPause(activity));Thread.sleep(200);int paused=ui(()->opening.getCurrentPosition());Thread.sleep(900);int still=ui(()->opening.getCurrentPosition());check(Math.abs(still-paused)<=150&&!ui(()->opening.isPlaying()),"Activity pause stops VideoView playback clock");
        runOnMainSync(()->callActivityOnResume(activity));end=SystemClock.uptimeMillis()+10000;
        while(ui(()->opening.getCurrentPosition())<still+700&&SystemClock.uptimeMillis()<end){if(activity.failed)throw new IOException(activity.error);Thread.sleep(100);}
        int resumed=ui(()->opening.getCurrentPosition());check(resumed>=still+700,"Activity resume restarts VideoView playback clock");log.append("MOVIE dc3_v1 before_pause_ms=").append(before).append(" paused_ms=").append(paused).append(" still_ms=").append(still).append(" resumed_ms=").append(resumed).append('\n');
        runOnMainSync(()->{long t=SystemClock.uptimeMillis();MotionEvent down=MotionEvent.obtain(t,t,MotionEvent.ACTION_DOWN,opening.getWidth()/2f,opening.getHeight()/2f,0),up=MotionEvent.obtain(t,t+30,MotionEvent.ACTION_UP,opening.getWidth()/2f,opening.getHeight()/2f,0);opening.dispatchTouchEvent(down);opening.dispatchTouchEvent(up);down.recycle();up.recycle();});
        check(platformMovie()==null&&ui(()->field("movieFile").get(activity))==null,"touch skips MP4 and closes the real movie dialog and file");check(!activity.failed,"MP4 decoder reports no media error");
    }
    /** Shipping frame clock and MediaPlayer callbacks only; never advance the VM in the test. */
    private void realtimeAudit()throws Exception{
        long start=SystemClock.uptimeMillis(),selected=0,end=start+120000;boolean title=false,prologue=false;String prior="";int lines=0;
        while(SystemClock.uptimeMillis()<end){
            if(activity.failed)throw new IOException(activity.error);
            String state=call(e->e.scriptName()+":"+e.scriptOffset()+":"+e.script.status());
            if(!state.equals(prior)){prior=state;log.append("STATE ").append(SystemClock.uptimeMillis()-start).append("ms ").append(state).append('\n');android.util.Log.i("CIRCUS-UNITY-AUDIT",state);Files.write(new File(output,"realtime-progress.txt").toPath(),log.toString().getBytes("UTF-8"));}
            List<String> actions=actions();
            if(!title&&actions.contains("sce_start")){title=true;screenshot("realtime-title");select("sce_start");}
            else if(!prologue&&actions.contains("sce01")){prologue=true;selected=SystemClock.uptimeMillis();select("sce01");}
            else if(!title&&actions.contains("window"))select("window");
            else if(!title&&actions.contains("01"))select("01");
            if(prologue&&call(e->e.textWaiting())){
                if(lines==0){log.append("PROLOGUE_FIRST_DIALOGUE_MS ").append(SystemClock.uptimeMillis()-selected).append('\n');screenshot("realtime-first-dialogue");}
                if(++lines==5)break;touch(32,32);
            }
            Thread.sleep(80);
        }
        check(title&&prologue,"real-time title and prologue controls activated by touch");
        check(lines==5,"real-time prologue reaches five dialogue waits without synthetic ticks or audio completion");
        check(activity.audioStarted>0,"real-time MediaPlayer playback started");
        check(!activity.failed,"real-time startup has no resource, VM or media failure");
    }
    public void onStart(){Bundle result=new Bundle();try{
        output=new File(getTargetContext().getFilesDir(),"dc3r-android-audit");output.mkdirs();
        Intent launch=new Intent(getTargetContext(),RuntimeActivity.class).putExtra("audit",true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);if(tree==null)launch.putExtra("range_pack",pack);else launch.putExtra("tree",tree);
        activity=(RuntimeActivity)startActivitySync(launch);
        long end=SystemClock.uptimeMillis()+90000;while(activity.engine==null&&!activity.failed&&SystemClock.uptimeMillis()<end)Thread.sleep(100);check(activity.engine instanceof DesktopUnity&&!activity.failed,"Activity detects Unity CircusScr backend");
        if(realtime){realtimeAudit();Files.write(new File(output,"realtime-result.txt").toPath(),log.toString().getBytes("UTF-8"));result.putString("stream",log.toString());finish(-1,result);return;}
        boolean title=false;for(int i=0;i<200;i++){call(e->{e.tick(1000);return null;});Thread.sleep(60);List<String> a=actions();if(a.contains("sce_start")){title=true;break;}if(a.contains("window"))select("window");else if(a.contains("01"))select("01");}
        check(title,"original START reaches title");Thread.sleep(300);screenshot("title");select("sce_start");
        int lines=0;String prior="";boolean actor=false;for(int i=0;i<600&&lines<50;i++){call(e->{e.tick(1000);return null;});Thread.sleep(60);List<String> a=actions();if(a.contains("sce01")){select("sce01");continue;}if(call(e->e.menuWaiting()&&!e.host.choiceWaiting))throw new IOException("Unexpected menu "+a);
            if(call(e->e.textWaiting())){String now=call(e->e.scriptName()+":"+e.scriptOffset()+":"+e.ticket());if(!now.equals(prior)){prior=now;lines++;if(lines==1)screenshot("first-dialogue");if(!actor&&call(e->e.actorVisible())){actor=true;screenshot("first-actor");}}if(lines<50)touch(32,32);}
        }
        check(lines>=50,"50 dialogue waits advanced using touch");check(actor,"original character texture rendered");screenshot("dialogue-50");
        check(call(e->{byte[] state=e.snapshot();String body=e.body(),script=e.scriptName();int pc=e.scriptOffset();BufferedImage before=e.render();e.click(32,32,false);e.tick(0);e.restore(state);BufferedImage after=e.render();boolean ok=body.equals(e.body())&&script.equals(e.scriptName())&&pc==e.scriptOffset()&&Arrays.equals(before.getRGB(0,0,1024,576,null,0,1024),after.getRGB(0,0,1024,576,null,0,1024));before.flush();after.flush();return ok;}),"snapshot restores exact VM position and Canvas pixels");
        Thread.sleep(1000);check(activity.audioStarted>0,"MediaPlayer starts remuxed original FSB5/Vorbis");check(!activity.failed,"no VM, resource, rendering or media exception");
        movieAudit();log.append("CHECKS ").append(checks).append("; story movie waits simulated; native MP4 playback/pause/resume/completion/skip checked separately; full routes and extras unverified\n");Files.write(new File(output,"result.txt").toPath(),log.toString().getBytes("UTF-8"));result.putString("stream",log.toString());finish(-1,result);
    }catch(Throwable e){log.append("FAIL ").append(e).append('\n');try{if(activity!=null&&activity.engine!=null)log.append(activity.engine.scriptName()).append(':').append(activity.engine.scriptOffset()).append('\n');if(output!=null)Files.write(new File(output,"result.txt").toPath(),log.toString().getBytes("UTF-8"));}catch(Exception ignored){}result.putString("stream",log.toString());finish(0,result);}finally{if(activity!=null)runOnMainSync(()->activity.finish());}}
}
