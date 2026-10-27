package org.circus.runtime;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.media.*;
import android.net.Uri;
import android.os.*;
import android.view.*;
import android.view.inputmethod.*;
import java.io.*;
import java.nio.file.*;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import org.circus.core.*;
import org.circus.graphics.BufferedImage;

/** Android platform boundary. Script execution, menus, rendering commands,
 * captures and save semantics are the same sources as the verified PC engine. */
public final class RuntimeActivity extends Activity {
    final ExecutorService worker=Executors.newSingleThreadExecutor();private final Handler main=new Handler();
    volatile DesktopEngine engine;volatile boolean failed,closed,active;volatile String error="";volatile Stage stage;int audioStarted;
    private GameResources resources;private long lastTick;private boolean busy;private MesVm seenVm;
    private final MediaPlayer[] players=new MediaPlayer[6];private final boolean[] prepared=new boolean[6];
    private final int[] heard={-1,-1,-1,-1,-1,-1},seekGeneration={-1,-1,-1,-1,-1,-1};
    private final long[] requestedResume=new long[6];private final File[] audioFiles=new File[6];
    private int movieGeneration=-1;private volatile int movieToken;private volatile NativeMovie movie;private Dialog movieDialog;private File movieFile;
    private String tree;private boolean resume;private boolean audit;
    NativeMovie activeMovie(){return movie;}
    private final Runnable frame=new Runnable(){public void run(){if(!active||closed||failed)return;if(!busy&&engine!=null){busy=true;worker.execute(()->{try{
        updatePositions();long now=SystemClock.uptimeMillis();engine.tick(Math.min(100,Math.max(0,now-lastTick)));lastTick=now;syncMedia();
        if(engine.exitRequested()){engine.persist();main.post(()->finish());return;}
        if(movie==null){BufferedImage image=engine.render();main.post(()->{if(closed){image.flush();return;}stage.present(image);});}
    }catch(Throwable e){fail(e);}finally{main.post(()->busy=false);}});}main.postDelayed(this,33);}};
    @Override public void onCreate(Bundle saved){super.onCreate(saved);getWindow().addFlags(128|1024);getWindow().getDecorView().setSystemUiVisibility(5894);stage=new Stage();setContentView(stage);
        tree=getIntent().getStringExtra("tree");resume=saved!=null&&saved.getBoolean("resume");audit=getIntent().getBooleanExtra("audit",false)&&(getApplicationInfo().flags&2)!=0;
        if(tree==null){fail(new IOException("请选择包含 AdvData 的游戏目录"));return;}
        worker.execute(()->{try{
            System.setProperty("dc.saveRoot",new File(getFilesDir(),audit?"android-011-audit-saves":"shared-saves").getAbsolutePath());
            resources=new GameResources(new DocumentTree(getContentResolver(),Uri.parse(tree)),getIntent().getStringExtra("executable"));GameVersion version=GameVersion.detect(resources);Profile profile=version.profile();
            if(!profile.id.equals("dc3dd-a5a8")&&!profile.id.equals("dc2dm-9d72")&&!PcHost.supports(profile))throw new IOException("此版本尚未验证："+profile.id);
            CircusResources data=new CircusResources(resources,profile);TextCodec codec=GameText.select(resources,profile,getIntent().getStringExtra("encoding"),getIntent().getStringExtra("configuration"));ExecutableData.validate(data,codec);
            engine=profile.id.equals("dc3dd-a5a8")?new DesktopDd(data,codec,audit):new DesktopLegacy(data,codec,audit);
            if(!audit)migratePreviousAndroid();Path automatic=engine.saveDirectory.resolve("mobile-resume.sav");if(resume&&Files.isRegularFile(automatic))engine.restore(automatic);
            String libraryId=getIntent().getStringExtra("library_id");if(!audit&&libraryId!=null)new GameLibrary(this).opened(libraryId);
            lastTick=SystemClock.uptimeMillis();main.post(()->{stage.setContentDescription("原版游戏画面，点击推进，返回键打开菜单");wake();});
        }catch(Throwable e){fail(e);}});
    }
    private void migratePreviousAndroid()throws IOException {
        String key=engine.resources.profile.id,oldKey=key.equals("dc3dd-a5a8")?"dc3dd":key.equals("dc2dm-9d72")?"dc2dm":key;
        GameSaves old=new GameSaves(this,tree,oldKey);Path marker=engine.saveDirectory.resolve("android-cas-migrated");if(Files.exists(marker))return;
        File prefs=new File(old.directory,"progress.dm");if(engine instanceof DesktopLegacy&&prefs.isFile()&&!Files.exists(engine.saveDirectory.resolve("progress.dat"))){try(DataInputStream in=new DataInputStream(new FileInputStream(prefs))){((DesktopLegacy)engine).host().loadPersistent(in);}engine.persist();}
        if(engine instanceof DesktopDd&&!Files.exists(engine.saveDirectory.resolve("progress.dat"))){SharedPreferences prior=getSharedPreferences(old.directory.getName(),0);Dc3Host host=((DesktopDd)engine).host;for(int flag=100;flag<=110;flag++)host.state.write(100,flag,Math.max(host.state.read(100,flag),prior.getInt("clear_"+flag,0)));engine.persist();}
        for(int slot=0;slot<256;slot++){Path target=engine.saveDirectory.resolve("slot-"+slot+".sav");if(!old.exists(slot)||Files.exists(target))continue;GameSaves.Save prior=old.read(slot);DesktopSave migrated=new DesktopSave();migrated.state=prior.core;migrated.created=prior.when;migrated.caption=prior.script;migrated.textElapsed=prior.revealTime;DesktopEngine.atomic(target,migrated.encode());}
        // Refresh availability without replacing persistent settings or launching a script.
        engine.restore(engine.snapshot());DesktopEngine.atomic(marker,new byte[]{1});
    }
    void submit(Runnable action){if(!closed&&!worker.isShutdown())worker.execute(()->{try{action.run();}catch(Throwable e){fail(e);}});}
    interface Work {void run()throws Exception;}
    void input(Work action){submit(()->{try{if(engine!=null&&!failed){action.run();lastTick=SystemClock.uptimeMillis();}}catch(Exception e){fail(e);}});}
    private void wake(){main.removeCallbacks(frame);if(active&&!closed&&!failed){lastTick=SystemClock.uptimeMillis();main.post(frame);}}
    private void fail(Throwable e){if(closed||failed)return;failed=true;error=e.toString();android.util.Log.e("CIRCUS-011",engine==null?"startup":engine.vm().scriptName()+" @"+Integer.toHexString(engine.vm().offset()),e);
        main.post(()->{main.removeCallbacks(frame);if(!closed)new AlertDialog.Builder(this).setTitle("CIRCUS 兼容诊断").setMessage((engine==null?"":engine.vm().scriptName()+" @0x"+Integer.toHexString(engine.vm().offset())+"\n")+error).setPositiveButton("返回",(d,w)->finish()).setCancelable(false).show();});
    }
    private void updatePositions()throws IOException {if(engine==null)return;for(int ch=0;ch<6;ch++)if(players[ch]!=null&&prepared[ch])engine.audioPosition(ch,players[ch].getCurrentPosition()*1000L,players[ch].getDuration()*1000L);}
    private void syncMedia()throws IOException {
        if(seenVm!=engine.vm()){for(int ch=0;ch<6;ch++)stopAudio(ch);Arrays.fill(heard,-1);Arrays.fill(seekGeneration,-1);seenVm=engine.vm();movieGeneration=-1;}
        for(int ch=0;ch<6;ch++){long position=engine.takeAudioResume(ch);if(position>0)requestedResume[ch]=position;
            if(heard[ch]!=engine.audioGeneration(ch)){heard[ch]=engine.audioGeneration(ch);startAudio(ch,engine.audioActive(ch)?engine.audio(ch):"",heard[ch],engine.audioLoop(ch));}
            if(players[ch]!=null&&prepared[ch]){MediaPlayer p=players[ch];float gain=engine.volume(ch),pan=Math.max(-1,Math.min(1,engine.pan(ch)/100f));p.setVolume(gain*(1-Math.max(0,pan)),gain*(1+Math.min(0,pan)));
                if(requestedResume[ch]>0){p.seekTo(Math.min((int)(requestedResume[ch]/1000),Math.max(0,p.getDuration()-1)));requestedResume[ch]=0;}
                int seek=engine.audioSeekGeneration(ch);if(seekGeneration[ch]!=seek){if(seekGeneration[ch]>=0)p.seekTo((int)(p.getDuration()*(long)engine.audioSeekPercent(ch)/100));seekGeneration[ch]=seek;}
            }
        }
        if(!engine.movie().isEmpty()&&movieGeneration!=engine.movieGeneration()){movieGeneration=engine.movieGeneration();startMovie(engine.movie(),engine.ticket());}
    }
    private void stopAudio(int ch){prepared[ch]=false;if(players[ch]!=null){players[ch].release();players[ch]=null;}if(audioFiles[ch]!=null){audioFiles[ch].delete();audioFiles[ch]=null;}}
    private void startAudio(int ch,String resource,int serial,boolean loop)throws IOException {
        stopAudio(ch);if(resource.isEmpty())return;Xpcm decoded=engine.resources.audio(resource);File file=File.createTempFile("circus-"+ch+"-",decoded.codec.equals("ogg")?".ogg":".wav",getCacheDir());audioFiles[ch]=file;
        try(FileOutputStream out=new FileOutputStream(file)){out.write(decoded.codec.equals("ogg")?decoded.payload:decoded.wav());}
        MediaPlayer player=new MediaPlayer();players[ch]=player;player.setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build());player.setDataSource(file.getAbsolutePath());player.setLooping(loop);
        player.setOnPreparedListener(p->submit(()->{if(players[ch]!=p)return;file.delete();audioFiles[ch]=null;prepared[ch]=true;try{syncMedia();if(active&&players[ch]==p){p.start();audioStarted++;}}catch(Exception e){fail(e);}}));
        player.setOnCompletionListener(p->submit(()->{if(players[ch]==p){engine.audioCompleted(ch,serial);stopAudio(ch);}}));
        player.setOnErrorListener((p,what,extra)->{submit(()->{if(players[ch]==p)fail(new IOException("音频播放失败："+resource+" ("+what+","+extra+")"));});return true;});player.prepareAsync();
    }
    private void startMovie(String reference,long ticket)throws IOException {
        final int token=++movieToken;File file=File.createTempFile("circus-movie-",".mpg",getCacheDir());movieFile=file;try(FileOutputStream out=new FileOutputStream(file)){engine.resources.files.copy(reference,out,1024L*1024*1024);}
        main.post(()->{if(closed||token!=movieToken){file.delete();return;}NativeMovie video=new NativeMovie(this);movie=video;movieDialog=new Dialog(this,android.R.style.Theme_Black_NoTitleBar_Fullscreen);movieDialog.setContentView(video);movieDialog.setCancelable(false);movieDialog.show();movieDialog.getWindow().getDecorView().setSystemUiVisibility(5894);
            video.setOnTouchListener((v,event)->{if(event.getActionMasked()==MotionEvent.ACTION_UP)finishMovie(token,ticket);return true;});movieDialog.setOnKeyListener((d,key,event)->{if(key!=KeyEvent.KEYCODE_BACK)return false;if(event.getAction()==KeyEvent.ACTION_UP)finishMovie(token,ticket);return true;});
            if(!active)video.pauseMovie();video.play(file,new NativeMovie.Listener(){public void finished(){finishMovie(token,ticket);}public void failed(Exception e){fail(e);}});
        });
    }
    private void finishMovie(int token,long ticket){if(token!=movieToken)return;closeMovie();input(()->engine.vm().signalHost(ticket,MesVm.HostSignal.MEDIA_COMPLETE));}
    private void closeMovie(){movieToken++;if(movie!=null){movie.close();movie=null;}if(movieDialog!=null){movieDialog.dismiss();movieDialog=null;}if(movieFile!=null){movieFile.delete();movieFile=null;}}
    @Override public void onBackPressed(){if(engine==null||failed){super.onBackPressed();return;}input(()->engine.click(0,0,true));}
    @Override protected void onResume(){super.onResume();active=true;getWindow().getDecorView().setSystemUiVisibility(5894);submit(()->{for(int ch=0;ch<6;ch++)if(players[ch]!=null&&prepared[ch])players[ch].start();});if(movie!=null)movie.resumeMovie();wake();}
    @Override protected void onPause(){active=false;main.removeCallbacks(frame);if(movie!=null)movie.pauseMovie();submit(()->{try{if(engine!=null&&!failed){engine.cancelPointer();updatePositions();if(!audit){engine.persist();engine.save(engine.saveDirectory.resolve("mobile-resume.sav"));}}}catch(Exception e){fail(e);}finally{for(int ch=0;ch<6;ch++)if(players[ch]!=null&&prepared[ch])players[ch].pause();}});super.onPause();}
    @Override protected void onSaveInstanceState(Bundle out){super.onSaveInstanceState(out);out.putBoolean("resume",true);}
    @Override protected void onDestroy(){closed=true;main.removeCallbacks(frame);closeMovie();worker.execute(()->{for(int ch=0;ch<6;ch++)stopAudio(ch);});worker.shutdown();stage.release();super.onDestroy();}
    @Override public boolean onKeyUp(int code,KeyEvent event){if(code==KeyEvent.KEYCODE_BACK)return super.onKeyUp(code,event);input(()->{if(engine.editingText()){int mapped=code==KeyEvent.KEYCODE_DEL?8:code==KeyEvent.KEYCODE_FORWARD_DEL?127:code==KeyEvent.KEYCODE_DPAD_LEFT?37:code==KeyEvent.KEYCODE_DPAD_RIGHT?39:code==KeyEvent.KEYCODE_ENTER?10:0;if(mapped!=0)engine.editKey(mapped);else if(event.getUnicodeChar()!=0)engine.typeText(new String(Character.toChars(event.getUnicodeChar())));}else if(code==KeyEvent.KEYCODE_ENTER||code==KeyEvent.KEYCODE_SPACE)engine.click(engine.width/2,engine.height/2,false);});return true;}
    final class Stage extends View {
        private BufferedImage image;private float scale=1,ox,oy;private final Paint paint=new Paint(Paint.FILTER_BITMAP_FLAG);private boolean held;private float downY;private boolean scrollGesture;
        Stage(){super(RuntimeActivity.this);setBackgroundColor(Color.BLACK);setFocusableInTouchMode(true);setContentDescription("正在读取游戏资源");}
        void present(BufferedImage next){BufferedImage old=image;image=next;if(old!=null)old.flush();invalidate();}
        void release(){if(image!=null){image.flush();image=null;}}
        @Override protected void onDraw(Canvas canvas){super.onDraw(canvas);canvas.drawColor(Color.BLACK);if(image==null){Paint label=new Paint(Paint.ANTI_ALIAS_FLAG);label.setColor(0xffdddddd);label.setTextSize(18*getResources().getDisplayMetrics().scaledDensity);label.setTextAlign(Paint.Align.CENTER);canvas.drawText("正在读取游戏资源…",getWidth()/2f,getHeight()/2f,label);return;}scale=Math.min(getWidth()/(float)image.getWidth(),getHeight()/(float)image.getHeight());ox=(getWidth()-image.getWidth()*scale)/2;oy=(getHeight()-image.getHeight()*scale)/2;canvas.drawBitmap(image.bitmap,null,new RectF(ox,oy,ox+image.getWidth()*scale,oy+image.getHeight()*scale),paint);}
        @Override public boolean onTouchEvent(MotionEvent event){if(engine==null||failed||image==null)return true;int x=Math.round((event.getX()-ox)/scale),y=Math.round((event.getY()-oy)/scale);int action=event.getActionMasked();
            if(action==MotionEvent.ACTION_POINTER_DOWN){scrollGesture=true;input(()->engine.cancelPointer());return true;}
            if(action==MotionEvent.ACTION_DOWN){held=true;scrollGesture=false;downY=event.getY();input(()->engine.pointerDown(x,y));}
            else if(action==MotionEvent.ACTION_MOVE){if(scrollGesture){float delta=(event.getY()-downY)/scale;if(Math.abs(delta)>35){int units=delta>0?-1:1;downY=event.getY();input(()->engine.scroll(units));}}else if(held)input(()->engine.pointerMove(x,y));}
            else if(action==MotionEvent.ACTION_UP){if(held&&!scrollGesture)input(()->engine.pointerUp(x,y));held=false;performClick();}
            else if(action==MotionEvent.ACTION_CANCEL){held=false;input(()->engine.cancelPointer());}return true;
        }
        @Override public boolean performClick(){super.performClick();return true;}
        @Override public boolean onGenericMotionEvent(MotionEvent event){if(event.getAction()==MotionEvent.ACTION_SCROLL){int units=-(int)Math.signum(event.getAxisValue(MotionEvent.AXIS_VSCROLL));input(()->engine.scroll(units));return true;}if(event.getAction()==MotionEvent.ACTION_HOVER_MOVE){int x=Math.round((event.getX()-ox)/scale),y=Math.round((event.getY()-oy)/scale);input(()->engine.hover(x,y));return true;}return super.onGenericMotionEvent(event);}
        @Override public boolean onCheckIsTextEditor(){return engine!=null&&engine.editingText();}
        @Override public InputConnection onCreateInputConnection(EditorInfo info){info.inputType=android.text.InputType.TYPE_CLASS_TEXT;info.imeOptions=EditorInfo.IME_ACTION_DONE;return new BaseInputConnection(this,false){public boolean commitText(CharSequence text,int cursor){input(()->engine.typeText(text.toString()));return true;}public boolean deleteSurroundingText(int before,int after){input(()->engine.editKey(8));return true;}public boolean performEditorAction(int action){input(()->engine.editKey(10));return true;}};}
    }
}
