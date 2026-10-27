package org.circus.runtime;
import android.app.*;
import android.content.*;
import android.graphics.Bitmap;
import android.os.*;
import android.view.*;
import java.io.*;
import java.lang.reflect.*;
import java.util.*;
import org.circus.core.*;

/** Original START/title/story with actual Android View input and media playback. */
public final class PcInstrumentation extends Instrumentation {
    private Dc2Activity activity;private String tree;private File dir;private final StringBuilder log=new StringBuilder();
    public void onCreate(Bundle a){super.onCreate(a);tree=a.getString("tree");start();}
    private Object field(String name)throws Exception{Field f=Dc2Activity.class.getDeclaredField(name);f.setAccessible(true);return f.get(activity);}
    private PcHost host()throws Exception{return (PcHost)field("host");}
    public void onStart(){Bundle result=new Bundle();try{
        Context c=getTargetContext();if(tree==null)tree=c.getSharedPreferences("library",0).getString("game_tree",null);
        ActivityMonitor monitor=addMonitor(Dc2Activity.class.getName(),null,false);
        startActivitySync(new Intent(c,GameActivity.class).putExtra("tree",tree).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        activity=(Dc2Activity)waitForMonitorWithTimeout(monitor,120000);removeMonitor(monitor);if(activity==null)throw new IOException("Automatic PC routing failed");
        long end=SystemClock.uptimeMillis()+90000;boolean[] title={false};
        while(SystemClock.uptimeMillis()<end){Thread.sleep(200);String[] action={null};boolean[] click={false};
            runOnMainSync(()->{try{if((boolean)field("failed"))throw new IOException("Activity failed");PcHost h=host();MesVm m=(MesVm)field("vm");if(h==null||(int)field("loading")>0)return;
                if(h.ui.waiting){for(Dc2Ui.Button b:h.ui.buttons)if(b.action.equals("start"))title[0]=true;if(!title[0])action[0]=h.second?"03":"window";}
                else click[0]=h.hasTimedInput()||!h.movie.isEmpty();
            }catch(Exception e){throw new RuntimeException(e);}});
            if(title[0])break;if(action[0]!=null)tap(action[0]);else if(click[0])touch(400,200);
        }
        if(!title[0])throw new IOException("Original title timeout");
        String[] profile={null};runOnMainSync(()->{try{profile[0]=host().resources.profile.id;}catch(Exception e){throw new RuntimeException(e);}});
        dir=new File(c.getFilesDir(),"pc-ui-audit/"+profile[0]);dir.mkdirs();Thread.sleep(1500);capture("01-title");tap("start");waitText();capture("02-story");
        for(int n=0;n<12;n++){touch(400,200);waitText();}capture("03-dialogue");
        String[] before={null};runOnMainSync(()->{try{before[0]=host().body;Method save=Dc2Activity.class.getDeclaredMethod("writeSave",int.class);save.setAccessible(true);save.invoke(activity,90);}catch(Exception e){throw new RuntimeException(e);}});
        touch(400,200);waitText();runOnMainSync(()->{try{Method load=Dc2Activity.class.getDeclaredMethod("loadSave",int.class);load.setAccessible(true);load.invoke(activity,90);if(!before[0].equals(host().body))throw new IOException("Save restore changed sentence");}catch(Exception e){throw new RuntimeException(e);}});
        waitText();capture("04-restored");
        result.putString("stream",log+"PASS "+profile[0]+" automatic profile routing, cold START, original title/new game, 12 dialogue advances and Android save wrapper restore. Full routes/extras not certified.\n");finish(-1,result);
    }catch(Throwable e){result.putString("stream",log+"FAIL "+e+"\n");finish(0,result);}finally{if(activity!=null)runOnMainSync(()->activity.finish());}}
    private void waitText()throws Exception{long end=SystemClock.uptimeMillis()+30000;boolean[] ok={false};while(SystemClock.uptimeMillis()<end){Thread.sleep(100);runOnMainSync(()->{try{if((boolean)field("failed"))throw new IOException("Activity failed: "+((MesVm)field("vm")).error());PcHost h=host();ok[0]=h!=null&&h.textWaiting&&(int)field("loading")==0;}catch(Exception e){throw new RuntimeException(e);}});if(ok[0]){Thread.sleep(300);return;}}throw new IOException("Story wait timeout");}
    private void tap(String action)throws Exception{float[] point=new float[2];runOnMainSync(()->{try{Dc2Ui.Button chosen=null;for(Dc2Ui.Button b:host().ui.buttons)if(b.action.equals(action)){chosen=b;break;}if(chosen==null)throw new IOException("Missing button "+action);point[0]=(chosen.left+chosen.right)/2f;point[1]=(chosen.top+chosen.bottom)/2f;}catch(Exception e){throw new RuntimeException(e);}});touch(point[0],point[1]);log.append("TOUCH ").append(action).append('\n');}
    private void touch(float x,float y)throws Exception{float[] p=new float[2];runOnMainSync(()->{try{View v=(View)field("stage");float scale=Math.min(v.getWidth()/800f,v.getHeight()/600f);int[] at=new int[2];v.getLocationOnScreen(at);p[0]=at[0]+(v.getWidth()-800*scale)/2+x*scale;p[1]=at[1]+(v.getHeight()-600*scale)/2+y*scale;}catch(Exception e){throw new RuntimeException(e);}});long t=SystemClock.uptimeMillis();MotionEvent e=MotionEvent.obtain(t,t,0,p[0],p[1],0);sendPointerSync(e);e.recycle();Thread.sleep(80);e=MotionEvent.obtain(t,SystemClock.uptimeMillis(),1,p[0],p[1],0);sendPointerSync(e);e.recycle();Thread.sleep(200);}
    private void capture(String name)throws Exception{Bitmap b=getUiAutomation().takeScreenshot();try(FileOutputStream out=new FileOutputStream(new File(dir,name+".png"))){b.compress(Bitmap.CompressFormat.PNG,100,out);}b.recycle();log.append("PNG ").append(name).append('\n');}
}
