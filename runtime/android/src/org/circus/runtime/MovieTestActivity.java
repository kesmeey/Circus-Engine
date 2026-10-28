package org.circus.runtime;
import android.app.Activity;
import android.os.Bundle;
import java.io.File;
/** Private instrumentation target; not a launchable application entry. */
public final class MovieTestActivity extends Activity {
    NativeMovie movie;volatile boolean done;volatile Exception error;
    public void onCreate(Bundle state){super.onCreate(state);getWindow().setFlags(1024,1024);movie=new NativeMovie(this);setContentView(movie);String name=getIntent().getStringExtra("movie_file");if(name==null||!name.matches("[A-Za-z0-9_-]+\\.mpg")){error=new IllegalArgumentException("Invalid test basename");done=true;return;}movie.play(new File(getFilesDir(),name),getIntent().getLongExtra("start_ms",0),new NativeMovie.Listener(){public void finished(){done=true;}public void failed(Exception e){error=e;done=true;}});}
    protected void onResume(){super.onResume();getWindow().getDecorView().setSystemUiVisibility(5894);movie.testVideoStallMs=getIntent().getIntExtra("video_stall_ms",0);movie.resumeMovie();}
    protected void onPause(){movie.pauseMovie();super.onPause();}
    protected void onDestroy(){movie.close();super.onDestroy();}
}
