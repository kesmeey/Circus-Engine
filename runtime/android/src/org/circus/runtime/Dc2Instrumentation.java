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
public final class Dc2Instrumentation extends Instrumentation {
    private Dc2Activity activity;private File dir;private final StringBuilder log=new StringBuilder();
    public void onCreate(Bundle a){super.onCreate(a);start();}
    private Object field(String name)throws Exception{Field f=Dc2Activity.class.getDeclaredField(name);f.setAccessible(true);return f.get(activity);}
    public void onStart(){Bundle result=new Bundle();try{
        Context c=getTargetContext();String tree=c.getSharedPreferences("library",0).getString("game_tree",null);if(tree==null)throw new IOException("Select DM test data");
        GameSaves store=new GameSaves(c,tree,"dc2dm");
        dir=new File(c.getFilesDir(),"dc2-ui-audit");dir.mkdirs();activity=(Dc2Activity)startActivitySync(new Intent(c,Dc2Activity.class).putExtra("tree",tree).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        startup();
        waitReady("MAIN",true);capture("01-title");tap("sce_start");waitReady("MAR_0101_F1",false);capture("02-story");
        tap(":n");tap(":option");waitReady("ADVMENU",true);capture("03-advmenu");tap("save");waitReady("SAVE",true);capture("04-save");tap("@slot:3");waitReady("SAVE",true);capture("05-confirm");tap(":y");waitReady("SAVE",true);capture("06-saved");
        if(!store.exists(4))throw new IOException("Manual slot not written");tap("return");waitReady("ADVMENU",true);tap("return");waitReady("MAR_0101_F1",false);capture("07-resume");
        tap(":n");tap(":option");waitReady("ADVMENU",true);tap("load");waitReady("LOAD",true);capture("08-load");tap("@slot:3");waitReady("LOAD",true);tap(":y");waitReady("MAR_0101_F1",false);capture("09-loaded");
        tap(":n");tap(":option");waitReady("ADVMENU",true);tap("config");waitReady("OPTION",true);capture("10-option");int before=setting(7);tap("*music_vol--");waitReady("OPTION",true);if(setting(7)!=Math.max(0,before-1))throw new IOException("Volume did not change");capture("11-volume");tap("retchk");waitReady("ADVMENU",true);tap("return");waitReady("MAR_0101_F1",false);
        tap(":n");tap(":option");waitReady("ADVMENU",true);tap("title");waitReady("ADVMENU",true);tap(":y");waitReady("MAIN",true);capture("12-title-return");tap("qload");waitReady("MAR_0101_F1",false);capture("13-continue");
        result.putString("stream",log+"PASS cold START/WIN_SIZE/title, body, system menu, save/load, settings, return to title and CONTINUE with actual Android input/media\n");finish(-1,result);
    }catch(Throwable e){result.putString("stream",log+"FAIL "+e+"\n");finish(0,result);}finally{if(activity!=null)runOnMainSync(()->activity.finish());}}
    private void startup()throws Exception{
        long end=SystemClock.uptimeMillis()+180000;boolean[] done={false},click={false};
        while(SystemClock.uptimeMillis()<end){Thread.sleep(500);runOnMainSync(()->{try{MesVm m=(MesVm)field("vm");Dc2Host h=(Dc2Host)field("host");done[0]=m!=null&&m.scriptName().equals("MAIN")&&h.ui.waiting;click[0]=h!=null&&!h.ui.waiting&&h.remainingMillis()<0&&(int)field("loading")==0;}catch(Exception e){throw new RuntimeException(e);}});if(done[0])return;if(click[0]){long t=SystemClock.uptimeMillis();MotionEvent e=MotionEvent.obtain(t,t,0,640,360,0);sendPointerSync(e);e.recycle();e=MotionEvent.obtain(t,t+80,1,640,360,0);sendPointerSync(e);e.recycle();log.append("TOUCH startup continue\n");}}
        throw new IOException("Cold startup timeout");
    }
    private int setting(int n){int[] result={-1};runOnMainSync(()->{try{result[0]=((Dc2Host)field("host")).read(99,n);}catch(Exception e){throw new RuntimeException(e);}});return result[0];}
    private void waitReady(String script,boolean menu)throws Exception{long deadline=SystemClock.uptimeMillis()+120000;long stable=0;boolean[] ok={false};String[] error={null};do{Thread.sleep(100);runOnMainSync(()->{try{MesVm m=(MesVm)field("vm");Dc2Host h=(Dc2Host)field("host");if((boolean)field("failed"))error[0]=m==null?"initialization":m.error();ok[0]=m!=null&&m.scriptName().equalsIgnoreCase(script)&&(int)field("loading")==0&&!(boolean)field("closed")&&((Map<?,?>)field("images")).size()>0&&(menu?h.ui.waiting:h.textWaiting);}catch(Exception e){error[0]=e.toString();}});if(error[0]!=null)throw new IOException(error[0]);if(ok[0]){if(stable==0)stable=SystemClock.uptimeMillis();}else stable=0;}while((stable==0||SystemClock.uptimeMillis()-stable<1200)&&SystemClock.uptimeMillis()<deadline);if(!ok[0]){runOnMainSync(()->{try{Dc2Host h=(Dc2Host)field("host");MesVm m=(MesVm)field("vm");error[0]=m.scriptName()+" @"+Integer.toHexString(m.offset())+" "+m.status()+" loading="+field("loading")+" menu="+h.ui.waiting+" text="+h.textWaiting+" remaining="+h.remainingMillis()+" fade="+h.musicFadeRemaining+" audio="+Arrays.toString(h.audioActive)+" body="+h.body;}catch(Exception e){error[0]=e.toString();}});throw new IOException("Timed out "+script+": "+error[0]);}Thread.sleep(600);}
    @SuppressWarnings("unchecked") private void tap(String action)throws Exception{float[] point=new float[2];runOnMainSync(()->{try{View view=(View)field("stage");Method method=view.getClass().getDeclaredMethod("buttons");method.setAccessible(true);List<Dc2Ui.Button> buttons=(List<Dc2Ui.Button>)method.invoke(view);Dc2Ui.Button chosen=null;for(Dc2Ui.Button b:buttons)if(b.action.equals(action)){chosen=b;break;}if(chosen==null)throw new IOException("Missing "+action);float scale=Math.min(view.getWidth()/1024f,view.getHeight()/576f);int[] at=new int[2];view.getLocationOnScreen(at);point[0]=at[0]+(view.getWidth()-1024*scale)/2+(chosen.left+chosen.right)*scale/2;point[1]=at[1]+(view.getHeight()-576*scale)/2+(chosen.top+chosen.bottom)*scale/2;}catch(Exception e){throw new RuntimeException(e);}});long t=SystemClock.uptimeMillis();MotionEvent down=MotionEvent.obtain(t,t,0,point[0],point[1],0);sendPointerSync(down);down.recycle();Thread.sleep(80);MotionEvent up=MotionEvent.obtain(t,SystemClock.uptimeMillis(),1,point[0],point[1],0);sendPointerSync(up);up.recycle();Thread.sleep(200);log.append("TOUCH ").append(action).append('\n');}
    private void capture(String name)throws Exception{Bitmap b=getUiAutomation().takeScreenshot();try(FileOutputStream out=new FileOutputStream(new File(dir,name+".png"))){b.compress(Bitmap.CompressFormat.PNG,100,out);}b.recycle();log.append("PNG ").append(name).append('\n');}
}
