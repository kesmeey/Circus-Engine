package org.circus.runtime;
import android.app.*;
import android.content.*;
import android.graphics.Bitmap;
import android.os.*;
import android.view.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import org.circus.core.*;
import org.circus.graphics.BufferedImage;

/** Exercise the shipping Android activity through SAF and real touch events. */
public final class SharedRuntimeInstrumentation extends Instrumentation {
    private RuntimeActivity activity;private String tree;private boolean audit=true;private File output;private int checks;private final StringBuilder log=new StringBuilder();
    public void onCreate(Bundle args){super.onCreate(args);tree=args.getString("tree");audit=!"false".equals(args.getString("audit"));start();}
    interface Job<T>{T run(DesktopEngine e)throws Exception;}
    private <T>T call(Job<T> job)throws Exception {if(activity.failed)throw new IOException(activity.error);return activity.worker.submit(()->{if(activity.engine==null)throw new IOException("Engine not ready");return job.run(activity.engine);}).get(60,TimeUnit.SECONDS);}
    private void check(boolean value,String detail)throws IOException{if(!value)throw new IOException(detail);checks++;log.append("PASS ").append(detail).append('\n');}
    private List<String> actions()throws Exception{return call(e->{List<String> a=new ArrayList<>();for(DesktopEngine.Control c:e.controls())a.add(c.action);return a;});}
    public void onStart(){Bundle result=new Bundle();try{
        activity=(RuntimeActivity)startActivitySync(new Intent(getTargetContext(),RuntimeActivity.class).putExtra("tree",tree).putExtra("audit",audit).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        long end=SystemClock.uptimeMillis()+120000;while(activity.engine==null&&!activity.failed&&SystemClock.uptimeMillis()<end)Thread.sleep(100);check(activity.engine!=null&&!activity.failed,"SAF directory and shared engine initialize");
        String profile=call(e->e.resources.profile.id);output=new File(getTargetContext().getFilesDir(),"android-011-audit/"+profile+(audit?"":"-upgrade"));output.mkdirs();
        boolean title=false;end=SystemClock.uptimeMillis()+90000;while(SystemClock.uptimeMillis()<end){List<String> a=actions();if(a.contains("start")||a.contains("sce_start")){title=true;break;}if(a.contains("window"))tap("window");else if(a.contains("03"))tap("03");else touch(.5f,.35f);Thread.sleep(300);}
        check(title,"original START reaches title");Thread.sleep(800);capture("01-title");
        if(profile.equals("dcpc-2063")){tap("name");waitMenu();check(call(e->e.editingText()),"original name editor opens");tap("@name:field:4");call(e->{e.typeText("测试");return null;});tap("@name:button:7");capture("00-name-confirm");tap("@name:yes");waitMenu();check(call(e->((PcHost)((DesktopLegacy)e).host()).givenName.equals("测试")),"name confirmation uses shared host");}
        tap(actions().contains("sce_start")?"sce_start":"start");waitText();capture("02-first-dialogue");
        for(int n=0;n<12;n++){String before=call(e->e.vm().scriptName()+":"+e.vm().offset());end=SystemClock.uptimeMillis()+20000;boolean changed=false;while(SystemClock.uptimeMillis()<end){touch(.5f,.35f);waitText();if(call(e->!(e.vm().scriptName()+":"+e.vm().offset()).equals(before))){changed=true;break;}}check(changed,"dialogue advance "+(n+1));}
        capture("03-story");
        check(call(e->{Path file=output.toPath().resolve("roundtrip.sav");String body=e.body();String script=e.vm().scriptName();int pc=e.vm().offset();BufferedImage before=e.render();e.save(file);DesktopSave save=DesktopSave.decode(Files.readAllBytes(file));e.restore(file);BufferedImage after=e.render();boolean match=body.equals(e.body())&&script.equals(e.vm().scriptName())&&pc==e.vm().offset()&&save.image()!=null&&Arrays.equals(before.getRGB(0,0,e.width,e.height,null,0,e.width),after.getRGB(0,0,e.width,e.height,null,0,e.width));before.flush();after.flush();return match;}),"save restores sentence, VM position, thumbnail and pixels");
        call(e->{if(e instanceof DesktopDd)((DesktopDd)e).action(":backlog");else ((DesktopLegacy)e).action(":backlog");return null;});waitMenu();capture("04-backlog");check(call(e->e.menuWaiting()),"original backlog opens");call(e->{e.scroll(-1);return null;});runOnMainSync(()->activity.onBackPressed());waitText();check(call(e->!e.body().isEmpty()),"Android Back returns from backlog");
        call(e->{e.save(e.saveDirectory.resolve("slot-4.sav"));e.restore(e.saveDirectory.resolve("slot-4.sav"));return null;});runOnMainSync(()->activity.onBackPressed());waitMenu();capture("05-original-menu");
        if(actions().contains("save"))tap("save");else if(actions().contains("s"))tap("s");else if(actions().contains(":save"))tap(":save");else if(call(e->e instanceof DesktopDd))call(e->{((DesktopDd)e).action(":save");return null;});
        waitMenu();capture("06-save-screen");check(call(e->e instanceof DesktopDd?((DesktopDd)e).host.ui.gridWaiting&&((DesktopDd)e).host.ui.saveGrid:((DesktopLegacy)e).host().gridWaiting&&((DesktopLegacy)e).host().saveGrid),"original save grid renders");check(call(e->e.thumbnails.containsKey(4)),"saved thumbnail populated");
        call(e->{e.restore(output.toPath().resolve("roundtrip.sav"));return null;});waitText();capture("07-restored");
        check(call(e->activity.audioStarted>0),"MediaPlayer prepared and started original audio");check(!activity.failed,"no renderer, VM or media failure");log.append("PROFILE ").append(profile).append(" CHECKS ").append(checks).append("\nFull routes and extras are not certified by this Android smoke test.\n");
        Files.write(output.toPath().resolve("result.txt"),log.toString().getBytes("UTF-8"));result.putString("stream",log.toString());finish(-1,result);
    }catch(Throwable e){log.append("FAIL ").append(e).append('\n');try{if(activity!=null&&activity.engine!=null)log.append(this.<String>call(v->v.vm().scriptName()+"@"+Integer.toHexString(v.vm().offset())+" "+v.vm().status()+" actions="+actionsUnsafe(v))).append('\n');}catch(Exception ignored){}try{if(output!=null)Files.write(output.toPath().resolve("result.txt"),log.toString().getBytes("UTF-8"));}catch(Exception ignored){}result.putString("stream",log.toString());finish(0,result);}finally{if(activity!=null)runOnMainSync(()->activity.finish());}}
    private static List<String> actionsUnsafe(DesktopEngine e){List<String>a=new ArrayList<>();for(DesktopEngine.Control c:e.controls())a.add(c.action);return a;}
    private void waitText()throws Exception{long end=SystemClock.uptimeMillis()+45000;while(SystemClock.uptimeMillis()<end){if(call(e->e.textWaiting())){Thread.sleep(150);return;}List<String>a=actions();if(a.contains("sel01"))tap("sel01");else if(a.contains("sceselect01"))tap("sceselect01");Thread.sleep(100);}throw new IOException("Text timeout; "+actions());}
    private void waitMenu()throws Exception{long end=SystemClock.uptimeMillis()+15000;while(SystemClock.uptimeMillis()<end){if(call(e->e.menuWaiting())){Thread.sleep(300);return;}Thread.sleep(100);}throw new IOException("Menu timeout");}
    private void tap(String action)throws Exception{float[] xy=call(e->{for(DesktopEngine.Control c:e.controls())if(c.action.equals(action))return new float[]{(c.left+c.right)/2f/e.width,(c.top+c.bottom)/2f/e.height};throw new IOException("Missing control "+action);});touch(xy[0],xy[1]);log.append("TOUCH ").append(action).append('\n');}
    private void touch(float x,float y)throws Exception{float[] p=new float[2];runOnMainSync(()->{View v=activity.stage;DesktopEngine e=activity.engine;float s=Math.min(v.getWidth()/(float)e.width,v.getHeight()/(float)e.height);int[] at=new int[2];v.getLocationOnScreen(at);p[0]=at[0]+(v.getWidth()-e.width*s)/2+x*e.width*s;p[1]=at[1]+(v.getHeight()-e.height*s)/2+y*e.height*s;});long now=SystemClock.uptimeMillis();MotionEvent a=MotionEvent.obtain(now,now,0,p[0],p[1],0);sendPointerSync(a);a.recycle();Thread.sleep(70);a=MotionEvent.obtain(now,SystemClock.uptimeMillis(),1,p[0],p[1],0);sendPointerSync(a);a.recycle();Thread.sleep(200);}
    private void capture(String name)throws Exception{Bitmap image=getUiAutomation().takeScreenshot();try(FileOutputStream f=new FileOutputStream(new File(output,name+".png"))){image.compress(Bitmap.CompressFormat.PNG,100,f);}image.recycle();}
}
