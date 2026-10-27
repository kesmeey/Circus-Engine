package org.circus.runtime;
import android.app.*;
import android.content.*;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.*;
import android.view.*;
import java.io.*;
import java.lang.reflect.*;
import java.util.*;
import org.circus.core.*;

/** Tests original DM UI through real View input in the test emulator; never runs a PC executable. */
public final class Dc2ExtrasInstrumentation extends Instrumentation {
    private byte[] originalProgress;private Dc2Activity activity;private File dir;private final StringBuilder log=new StringBuilder();
    public void onCreate(Bundle a){super.onCreate(a);start();}
    private Object field(String name)throws Exception{Field f=Dc2Activity.class.getDeclaredField(name);f.setAccessible(true);return f.get(activity);}
    public void onStart(){Bundle result=new Bundle();try{
        Context c=getTargetContext();String tree=c.getSharedPreferences("library",0).getString("game_tree",null);if(tree==null)throw new IOException("Select DM test data");
        GameSaves store=new GameSaves(c,tree,"dc2dm");
        dir=new File(c.getFilesDir(),"dc2-extras-audit");dir.mkdirs();activity=(Dc2Activity)startActivitySync(new Intent(c,Dc2Activity.class).putExtra("tree",tree).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        startup();waitReady("MAIN",true);
        runOnMainSync(()->{try{ByteArrayOutputStream out=new ByteArrayOutputStream();((Dc2Host)field("host")).savePersistent(new DataOutputStream(out));originalProgress=out.toByteArray();}catch(Exception e){throw new RuntimeException(e);}});
        // Load the pre-upgrade slot through the actual Android save wrapper.
        runOnMainSync(()->{try{Method m=Dc2Activity.class.getDeclaredMethod("loadSave",int.class);m.setAccessible(true);m.invoke(activity,4);}catch(Exception e){throw new RuntimeException(e);}});
        waitReady("MAR_0101_F1",false);capture("01-legacy-loaded");
        for(int n=0;n<6;n++){touch(500,220);waitReady("MAR_0101_F1",false);}
        String originalBody=body();tap(":n");tap(":backlog");waitReady("LOG",true);capture("02-backlog");
        swipe(700,180,700,480);waitReady("LOG",true);capture("03-backlog-scroll");tap("@log:exit");waitReady("MAR_0101_F1",false);if(!originalBody.equals(body()))throw new IOException("LOG advanced body");
        tap(":n");tap(":torisetu");waitReady("MANUAL",true);capture("04-manual");tap("ret");waitReady("MAR_0101_F1",false);if(!originalBody.equals(body()))throw new IOException("Manual advanced body");
        tap(":n");tap(":backlog");waitReady("LOG",true);tap("@log:voice:2");waitReady("LOG",true);tap("@log:rewind");waitReady("MAR_0101_F1",false);if(originalBody.equals(body()))throw new IOException("Rewind did not change position");capture("05-rewind");
        tap(":n");tap(":option");waitReady("ADVMENU",true);tap("title");waitReady("ADVMENU",true);tap(":y");waitReady("MAIN",true);tap("extra");waitReady("MAIN",true);capture("06-extra");
        tap("cgmode");waitReady("CG_MAR",true);capture("07-cg-locked");tap("return");waitReady("MAIN",true);
        tap("musmode");waitReady("MUSIC",true);capture("08-music");tap("@music:track:2");waitReady("MUSIC",true);capture("09-music-playing");tap("@music:all");waitReady("MUSIC",true);tap("@music:next");waitReady("MUSIC",true);tap("@music:play");waitReady("MUSIC",true);capture("10-music-stopped");swipe(700,460,700,130);waitReady("MUSIC",true);capture("11-music-scroll");tap("@music:exit");waitReady("MAIN",true);
        // Controlled unlock fixture, restored before exit; no shipping unlock-all setting.
        runOnMainSync(()->{try{Dc2Host h=(Dc2Host)field("host");h.state.write(97,0,1);h.state.write(98,0,1);}catch(Exception e){throw new RuntimeException(e);}});
        tap("cgmode");waitReady("CG_MAR",true);capture("12-cg-unlocked");tap("cg0");waitImage();capture("13-cg-full");sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);waitReady("CG_MAR",true);tap("page++");waitReady("CG_MAR",true);capture("14-cg-page");tap("sin");waitReady("CG_SIN",true);capture("15-cg-character");tap("return");waitReady("MAIN",true);
        tap("scemode");waitReady("SCE_MAR",true);capture("16-scene");tap("sce0");waitReady("SCE_MAR",true);tap(":y");waitReady("MAR_0101_F1",false);capture("17-scene-replay");
        // A real voiced scenario fixture exercises the Android voice decoder/player, not just the audio name.
        runOnMainSync(()->{try{Dc2Host h=(Dc2Host)field("host");MesVm m=(MesVm)field("vm");h.enterSystem();h.replayMode=true;m.variables.write(9,7,1);m.enterSystem("MAR_0114_A1");}catch(Exception e){throw new RuntimeException(e);}});
        waitReady("MAR_0114_A1",false);int voiced=-1;
        for(int n=0;n<40;n++){int[] entry={-1};runOnMainSync(()->{try{Dc2Host h=(Dc2Host)field("host");int last=h.history.entries.size()-1;if(last>=0&&!h.history.entries.get(last).voice.isEmpty()&&h.history.entries.get(last).body.length()>20)entry[0]=last;}catch(Exception e){throw new RuntimeException(e);}});if(entry[0]>=0){voiced=entry[0];break;}touch(500,220);waitReady("MAR_0114_A1",false);}
        if(voiced<0)throw new IOException("No voiced dialogue fixture");tap(":n");tap(":backlog");waitReady("LOG",true);tap("@log:voice:"+voiced);waitReady("LOG",true);
        final int voiceIndex=voiced;runOnMainSync(()->{try{Dc2Host h=(Dc2Host)field("host");if(!h.audio[5].equals(h.history.entries.get(voiceIndex).voice)||((int[])field("heard"))[5]!=h.audioGeneration[5])throw new IOException("Voice replay not presented");}catch(Exception e){throw new RuntimeException(e);}});capture("18-voiced-backlog");tap("@log:exit");waitReady("MAR_0114_A1",false);
        result.putString("stream",log+"PASS legacy save, original LOG/rewind/manual/CG/music/scene pages and returns through Android touch input; unlock fixture restored\n");finish(-1,result);
    }catch(Throwable e){result.putString("stream",log+"FAIL "+e+"\n");finish(0,result);}finally{if(activity!=null)runOnMainSync(()->{try{if(originalProgress!=null){Dc2Host h=(Dc2Host)field("host");h.loadPersistent(new DataInputStream(new ByteArrayInputStream(originalProgress)));h.replayMode=true;}}catch(Exception e){android.util.Log.e("CIRCUS-DM","Restore test progress",e);}activity.finish();});}}
    private String body(){String[] text={""};runOnMainSync(()->{try{text[0]=((Dc2Host)field("host")).body;}catch(Exception e){throw new RuntimeException(e);}});return text[0];}
    private void waitImage()throws Exception{for(int n=0;n<600;n++){Thread.sleep(100);boolean[] ready={false};runOnMainSync(()->{try{Dc2Host h=(Dc2Host)field("host");ready[0]=(int)field("loading")==0&&h.remainingMillis()<0;}catch(Exception e){throw new RuntimeException(e);}});if(ready[0]){Thread.sleep(1500);return;}}throw new IOException("CG image wait");}
    private float[] screen(float x,float y){float[] p=new float[2];runOnMainSync(()->{try{View v=(View)field("stage");float scale=Math.min(v.getWidth()/1024f,v.getHeight()/576f);int[] at=new int[2];v.getLocationOnScreen(at);p[0]=at[0]+(v.getWidth()-1024*scale)/2+x*scale;p[1]=at[1]+(v.getHeight()-576*scale)/2+y*scale;}catch(Exception e){throw new RuntimeException(e);}});return p;}
    private void touch(float x,float y)throws Exception{swipe(x,y,x,y);}
    private void swipe(float x,float y,float x2,float y2)throws Exception{float[] a=screen(x,y),b=screen(x2,y2);long t=SystemClock.uptimeMillis();MotionEvent e=MotionEvent.obtain(t,t,0,a[0],a[1],0);sendPointerSync(e);e.recycle();Thread.sleep(100);e=MotionEvent.obtain(t,SystemClock.uptimeMillis(),1,b[0],b[1],0);sendPointerSync(e);e.recycle();Thread.sleep(200);}
    private void startup()throws Exception{
        long end=SystemClock.uptimeMillis()+180000;boolean[] done={false},click={false};
        while(SystemClock.uptimeMillis()<end){Thread.sleep(500);runOnMainSync(()->{try{MesVm m=(MesVm)field("vm");Dc2Host h=(Dc2Host)field("host");done[0]=m!=null&&m.scriptName().equals("MAIN")&&h.ui.waiting;click[0]=h!=null&&!h.ui.waiting&&h.remainingMillis()<0&&(int)field("loading")==0;}catch(Exception e){throw new RuntimeException(e);}});if(done[0])return;if(click[0]){long t=SystemClock.uptimeMillis();MotionEvent e=MotionEvent.obtain(t,t,0,640,360,0);sendPointerSync(e);e.recycle();e=MotionEvent.obtain(t,t+80,1,640,360,0);sendPointerSync(e);e.recycle();log.append("TOUCH startup continue\n");}}
        throw new IOException("Cold startup timeout");
    }
    private int setting(int n){int[] result={-1};runOnMainSync(()->{try{result[0]=((Dc2Host)field("host")).read(99,n);}catch(Exception e){throw new RuntimeException(e);}});return result[0];}
    private void waitReady(String script,boolean menu)throws Exception{long deadline=SystemClock.uptimeMillis()+120000;long stable=0;boolean[] ok={false},calendar={false};String[] error={null};do{Thread.sleep(100);runOnMainSync(()->{try{MesVm m=(MesVm)field("vm");Dc2Host h=(Dc2Host)field("host");if((boolean)field("failed"))error[0]=m==null?"initialization":m.error();calendar[0]=!menu&&h!=null&&!h.ui.waiting&&h.remainingMillis()<0&&(int)field("loading")==0;ok[0]=m!=null&&m.scriptName().equalsIgnoreCase(script)&&(int)field("loading")==0&&!(boolean)field("closed")&&((Map<?,?>)field("images")).size()>0&&(menu?h.ui.waiting:h.textWaiting);}catch(Exception e){error[0]=e.toString();}});if(error[0]!=null)throw new IOException(error[0]);if(calendar[0])touch(500,220);if(ok[0]){if(stable==0)stable=SystemClock.uptimeMillis();}else stable=0;}while((stable==0||SystemClock.uptimeMillis()-stable<1200)&&SystemClock.uptimeMillis()<deadline);if(!ok[0]){runOnMainSync(()->{try{Dc2Host h=(Dc2Host)field("host");MesVm m=(MesVm)field("vm");error[0]=m.scriptName()+" @"+Integer.toHexString(m.offset())+" "+m.status()+" loading="+field("loading")+" menu="+h.ui.waiting+" text="+h.textWaiting+" remaining="+h.remainingMillis()+" fade="+h.musicFadeRemaining+" audio="+Arrays.toString(h.audioActive)+" body="+h.body;}catch(Exception e){error[0]=e.toString();}});throw new IOException("Timed out "+script+": "+error[0]);}Thread.sleep(600);}
    @SuppressWarnings("unchecked") private void tap(String action)throws Exception{float[] point=new float[2];runOnMainSync(()->{try{View view=(View)field("stage");Method method=view.getClass().getDeclaredMethod("buttons");method.setAccessible(true);List<Dc2Ui.Button> buttons=(List<Dc2Ui.Button>)method.invoke(view);Dc2Ui.Button chosen=null;for(Dc2Ui.Button b:buttons)if(b.action.equals(action)){chosen=b;break;}if(chosen==null)throw new IOException("Missing "+action);float scale=Math.min(view.getWidth()/1024f,view.getHeight()/576f);int[] at=new int[2];view.getLocationOnScreen(at);point[0]=at[0]+(view.getWidth()-1024*scale)/2+(chosen.left+chosen.right)*scale/2;point[1]=at[1]+(view.getHeight()-576*scale)/2+(chosen.top+chosen.bottom)*scale/2;}catch(Exception e){throw new RuntimeException(e);}});long t=SystemClock.uptimeMillis();MotionEvent down=MotionEvent.obtain(t,t,0,point[0],point[1],0);sendPointerSync(down);down.recycle();Thread.sleep(80);MotionEvent up=MotionEvent.obtain(t,SystemClock.uptimeMillis(),1,point[0],point[1],0);sendPointerSync(up);up.recycle();Thread.sleep(200);log.append("TOUCH ").append(action).append('\n');}
    private void capture(String name)throws Exception{Bitmap b=getUiAutomation().takeScreenshot();try(FileOutputStream out=new FileOutputStream(new File(dir,name+".png"))){b.compress(Bitmap.CompressFormat.PNG,100,out);}b.recycle();log.append("PNG ").append(name).append('\n');}
}
