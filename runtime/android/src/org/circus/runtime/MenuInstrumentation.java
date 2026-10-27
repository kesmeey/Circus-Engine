package org.circus.runtime;

import android.app.*;
import android.content.*;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.*;
import android.view.*;
import java.io.*;
import java.lang.reflect.Field;
import java.util.*;
import org.circus.core.*;

/** Original scripts in ART, then real View input and screenshots. Native EXE is never used. */
public final class MenuInstrumentation extends Instrumentation {
    private GameActivity activity;private File directory;private final StringBuilder log=new StringBuilder();
    public void onCreate(Bundle args){super.onCreate(args);start();}
    private Object field(String key)throws Exception{Field f=GameActivity.class.getDeclaredField(key);f.setAccessible(true);return f.get(activity);}
    public void onStart(){Bundle result=new Bundle();try{
        Context c=getTargetContext();String tree=c.getSharedPreferences("library",0).getString("game_tree",null);if(tree==null)throw new IOException("Select original data first");
        Dc3Resources data=new Dc3Resources(new GameResources(new DocumentTree(c.getContentResolver(),Uri.parse(tree))));TextCodec codec=TextConfiguration.load(data.files);
        Dc3Host h=new Dc3Host(data,codec,true);MesVm vm=new MesVm(data.scripts(),h,"START");Set<String> scripts=new LinkedHashSet<>();
        for(int n=0;n<50000&&!h.ui.waiting;n++){scripts.add(vm.scriptName());MesVm.Status s=vm.run(1);if(s==MesVm.Status.FAULT)throw new IOException(vm.error());if(s==MesVm.Status.WAIT_HOST){for(int ch=0;ch<6;ch++)h.audioCompleted(ch,h.audioGeneration[ch]);vm.signalHost(h.waitTicket(),MesVm.HostSignal.MEDIA_COMPLETE);vm.elapse(Math.max(1,h.remainingMillis()));vm.signalHost(h.waitTicket(),MesVm.HostSignal.TEXT_COMPLETE);vm.signalHost(h.waitTicket(),MesVm.HostSignal.ADVANCE);}}
        if(!h.ui.waiting||!vm.scriptName().equalsIgnoreCase("MAIN"))throw new IOException("Original startup failed");log.append("PASS ART startup ").append(scripts).append(" (media completion simulated)\n");
        GameSaves.Save save=new GameSaves.Save();save.core=vm.snapshot();save.script=vm.scriptName();save.when=System.currentTimeMillis();new GameSaves(c,tree).write(98,save);
        directory=new File(c.getFilesDir(),"original-ui");directory.mkdirs();
        activity=(GameActivity)startActivitySync(new Intent(c,GameActivity.class).putExtra("tree",tree).putExtra("slot",98).putExtra("original_system",true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        waitMenu("MAIN");capture("01-title");tap("sce_start");waitMenu("SCESELECT");capture("02-select");tap("sel01");waitMenu("SCESELECT");capture("03-selected");tap("ret");waitMenu("MAIN");capture("04-return");tap("extra");waitMenu("MAIN");capture("05-extra");tap("rmain");waitMenu("MAIN");capture("06-return");
        tap("end");waitMenu("MAIN");capture("12-confirm-exit");tap(":c");waitMenu("MAIN");capture("14-confirm-checked");tap(":c");waitMenu("MAIN");tap(":n");waitMenu("MAIN");capture("13-confirm-cancel");tap("end");waitMenu("MAIN");tap(":y");
        long exitDeadline=SystemClock.uptimeMillis()+30000;while(!activity.isFinishing()&&SystemClock.uptimeMillis()<exitDeadline)Thread.sleep(100);if(!activity.isFinishing())throw new IOException("Game exit did not finish Activity");log.append("PASS original exit script finishes Activity\n");
        result.putString("stream",log.toString());finish(-1,result);
    }catch(Throwable e){result.putString("stream",log+"FAIL "+e+"\n");finish(0,result);}finally{if(activity!=null)runOnMainSync(()->activity.finish());}}
    private void waitMenu(String script)throws Exception{long deadline=SystemClock.uptimeMillis()+30000;boolean[] ready={false};String[] error={null};do{
        Thread.sleep(100);runOnMainSync(()->{try{MesVm vm=(MesVm)field("vm");Dc3Host h=(Dc3Host)field("host");if((boolean)field("failed"))error[0]=vm==null?"initialization":vm.error();ready[0]=vm!=null&&h.ui.waiting&&vm.scriptName().equalsIgnoreCase(script)&&(int)field("loading")==0;}catch(Exception e){error[0]=e.toString();}});
        if(error[0]!=null)throw new IOException(error[0]);
    }while(!ready[0]&&SystemClock.uptimeMillis()<deadline);if(!ready[0])throw new IOException("Timed out waiting for "+script);Thread.sleep(150);}
    private void tap(String action)throws Exception{float[] point=new float[2];runOnMainSync(()->{try{Dc3Host h=(Dc3Host)field("host");View stage=(View)field("stage");Dc3Ui.Button b=null;for(Dc3Ui.Button candidate:h.ui.current.buttons)if(candidate.action.equals(action))b=candidate;if(b==null)throw new IOException("Missing action "+action);int[] location=new int[2];stage.getLocationOnScreen(location);float scale=Math.min(stage.getWidth()/1024f,stage.getHeight()/576f);point[0]=location[0]+(stage.getWidth()-1024*scale)/2+(b.left+b.right)*scale/2;point[1]=location[1]+(stage.getHeight()-576*scale)/2+(b.top+b.bottom)*scale/2;}catch(Exception e){throw new RuntimeException(e);}});
        long t=SystemClock.uptimeMillis();MotionEvent down=MotionEvent.obtain(t,t,MotionEvent.ACTION_DOWN,point[0],point[1],0);sendPointerSync(down);down.recycle();Thread.sleep(120);capture("press-"+action);MotionEvent up=MotionEvent.obtain(t,SystemClock.uptimeMillis(),MotionEvent.ACTION_UP,point[0],point[1],0);sendPointerSync(up);up.recycle();Thread.sleep(300);log.append("PASS touch ").append(action).append('\n');}
    private void capture(String name)throws Exception{Bitmap b=getUiAutomation().takeScreenshot();if(b==null)throw new IOException("Screenshot failed");try(FileOutputStream out=new FileOutputStream(new File(directory,name+".png"))){b.compress(Bitmap.CompressFormat.PNG,100,out);}b.recycle();log.append("PNG ").append(name).append('\n');}
}
