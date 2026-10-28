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
    private RuntimeActivity activity;private String tree;private boolean audit=true,sequence,opening,inputRegression,overlayRegression,transitionRegression,petalRegression,petalReplay,galleryRegression;private File output;private int checks;private final StringBuilder log=new StringBuilder();
    public void onCreate(Bundle args){super.onCreate(args);tree=args.getString("tree");audit=!"false".equals(args.getString("audit"));sequence="true".equals(args.getString("sequence"));opening="true".equals(args.getString("opening"));inputRegression="true".equals(args.getString("inputRegression"));overlayRegression="true".equals(args.getString("overlayRegression"));transitionRegression="true".equals(args.getString("transitionRegression"));petalRegression="true".equals(args.getString("petalRegression"));petalReplay="true".equals(args.getString("petalReplay"));galleryRegression="true".equals(args.getString("galleryRegression"));start();}
    interface Job<T>{T run(DesktopEngine e)throws Exception;}
    private <T>T call(Job<T> job)throws Exception {if(activity.failed)throw new IOException(activity.error);return activity.worker.submit(()->{if(activity.engine==null)throw new IOException("Engine not ready");return job.run(activity.engine);}).get(60,TimeUnit.SECONDS);}
    private void check(boolean value,String detail)throws IOException{if(!value)throw new IOException(detail);checks++;log.append("PASS ").append(detail).append('\n');}
    private List<String> actions()throws Exception{return call(e->{List<String> a=new ArrayList<>();for(DesktopEngine.Control c:e.controls())a.add(c.action);return a;});}
    public void onStart(){Bundle result=new Bundle();try{
        if(sequence){switchGames();result.putString("stream",log.toString());finish(-1,result);return;}
        activity=(RuntimeActivity)startActivitySync(new Intent(getTargetContext(),RuntimeActivity.class).putExtra("tree",tree).putExtra("audit",audit).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        long end=SystemClock.uptimeMillis()+120000;while(activity.engine==null&&!activity.failed&&SystemClock.uptimeMillis()<end)Thread.sleep(100);check(activity.engine!=null&&!activity.failed,"SAF directory and shared engine initialize");
        String profile=call(e->e.resources.profile.id);output=new File(getTargetContext().getFilesDir(),"android-011-audit/"+profile+(audit?"":"-upgrade"));output.mkdirs();
        boolean title=false;end=SystemClock.uptimeMillis()+90000;while(SystemClock.uptimeMillis()<end){List<String> a=actions();if(a.contains("start")||a.contains("sce_start")){title=true;break;}if(a.contains("window"))tap("window");else if(a.contains("03"))tap("03");else touch(.5f,.35f);Thread.sleep(300);}
        check(title,"original START reaches title");Thread.sleep(800);capture("01-title");
        if(transitionRegression){transitionRegression();result.putString("stream",log.toString());finish(-1,result);return;}
        if(petalRegression){petalRegression();result.putString("stream",log.toString());finish(-1,result);return;}
        if(galleryRegression){log.append(this.<String>call(e->new DdGalleryAudit((DesktopDd)e).run(output.toPath().resolve("gallery-regression"))));result.putString("stream",log.toString());finish(-1,result);return;}
        if(profile.equals("dcpc-2063")){tap("name");waitMenu();check(call(e->e.editingText()),"original name editor opens");tap("@name:field:4");call(e->{e.typeText("测试");return null;});tap("@name:button:7");capture("00-name-confirm");tap("@name:yes");waitMenu();check(call(e->((PcHost)((DesktopLegacy)e).host()).givenName.equals("测试")),"name confirmation uses shared host");}
        tap(actions().contains("sce_start")?"sce_start":"start");waitText();capture("02-first-dialogue");
        if(overlayRegression){overlayRegression();result.putString("stream",log.toString());finish(-1,result);return;}
        if(inputRegression){inputRegression();result.putString("stream",log.toString());finish(-1,result);return;}
        if(opening){openingMovie();result.putString("stream",log.toString());finish(-1,result);return;}
        for(int n=0;n<12;n++){String before=call(e->e.vm().scriptName()+":"+e.vm().offset());end=SystemClock.uptimeMillis()+20000;boolean changed=false;while(SystemClock.uptimeMillis()<end){touch(.5f,.35f);waitText();if(call(e->!(e.vm().scriptName()+":"+e.vm().offset()).equals(before))){changed=true;break;}}check(changed,"dialogue advance "+(n+1));}
        capture("03-story");
        check(call(e->{Path file=output.toPath().resolve("roundtrip.sav");String body=e.body();String script=e.vm().scriptName();int pc=e.vm().offset();BufferedImage before=e.render();e.save(file);DesktopSave save=DesktopSave.decode(Files.readAllBytes(file));e.restore(file);BufferedImage after=e.render();boolean match=body.equals(e.body())&&script.equals(e.vm().scriptName())&&pc==e.vm().offset()&&save.image()!=null&&Arrays.equals(before.getRGB(0,0,e.width,e.height,null,0,e.width),after.getRGB(0,0,e.width,e.height,null,0,e.width));before.flush();after.flush();return match;}),"save restores sentence, VM position, thumbnail and pixels");
        if(call(e->e instanceof DesktopLegacy))rapidMenus();
        call(e->{if(e instanceof DesktopDd)((DesktopDd)e).action(":backlog");else ((DesktopLegacy)e).action(":backlog");return null;});waitMenu();capture("04-backlog");check(call(e->e.menuWaiting()),"original backlog opens");call(e->{e.scroll(-1);return null;});runOnMainSync(()->activity.onBackPressed());waitText();check(call(e->!e.body().isEmpty()),"Android Back returns from backlog");
        call(e->{e.save(e.saveDirectory.resolve("slot-4.sav"));e.restore(e.saveDirectory.resolve("slot-4.sav"));return null;});runOnMainSync(()->activity.onBackPressed());waitMenu();capture("05-original-menu");
        if(actions().contains("save"))tap("save");else if(actions().contains("s"))tap("s");else if(actions().contains(":save"))tap(":save");else if(call(e->e instanceof DesktopDd))call(e->{((DesktopDd)e).action(":save");return null;});
        waitMenu();capture("06-save-screen");check(call(e->e instanceof DesktopDd?((DesktopDd)e).host.ui.gridWaiting&&((DesktopDd)e).host.ui.saveGrid:((DesktopLegacy)e).host().gridWaiting&&((DesktopLegacy)e).host().saveGrid),"original save grid renders");check(call(e->e.thumbnails.containsKey(4)),"saved thumbnail populated");
        call(e->{e.restore(output.toPath().resolve("roundtrip.sav"));return null;});waitText();capture("07-restored");
        check(call(e->activity.audioStarted>0),"MediaPlayer prepared and started original audio");check(!activity.failed,"no renderer, VM or media failure");log.append("PROFILE ").append(profile).append(" CHECKS ").append(checks).append("\nFull routes and extras are not certified by this Android smoke test.\n");
        Files.write(output.toPath().resolve("result.txt"),log.toString().getBytes("UTF-8"));result.putString("stream",log.toString());finish(-1,result);
    }catch(Throwable e){log.append("FAIL ").append(e).append('\n');try{if(activity!=null&&activity.engine!=null)log.append(this.<String>call(v->v.vm().scriptName()+"@"+Integer.toHexString(v.vm().offset())+" "+v.vm().status()+" actions="+actionsUnsafe(v))).append('\n');}catch(Exception ignored){}try{if(output!=null)Files.write(output.toPath().resolve("result.txt"),log.toString().getBytes("UTF-8"));}catch(Exception ignored){}result.putString("stream",log.toString());finish(0,result);}finally{if(activity!=null)runOnMainSync(()->activity.finish());}}
    private static List<String> actionsUnsafe(DesktopEngine e){List<String>a=new ArrayList<>();for(DesktopEngine.Control c:e.controls())a.add(c.action);return a;}
    private void petalRegression()throws Exception {
        log.append(this.<String>call(e->new DdPetalAudit((DesktopDd)e).run(output.toPath().resolve("petal-regression"),petalReplay)));
        Thread.sleep(1000);List<Long> frames=new ArrayList<>();
        runOnMainSync(()->activity.stage.presentationObserver=frames::add);
        try{Thread.sleep(5000);}finally{runOnMainSync(()->activity.stage.presentationObserver=null);}
        check(frames.size()>5,"continuous Android presentation with petals");
        long[] gaps=new long[frames.size()-1];for(int n=1;n<frames.size();n++)gaps[n-1]=frames.get(n)-frames.get(n-1);Arrays.sort(gaps);
        double fps=(frames.size()-1)*1e9/(frames.get(frames.size()-1)-frames.get(0));
        String pacing="PRESENT_FPS "+fps+" MEDIAN_GAP_MS "+gaps[gaps.length/2]/1e6+" P95_GAP_MS "+gaps[(int)Math.ceil(gaps.length*.95)-1]/1e6;
        log.append(pacing).append('\n');capture("16-petals");check(!activity.failed,"no Android petal scene failure");
        runOnMainSync(()->callActivityOnPause(activity));Thread.sleep(150);
        long pausedTime=call(e->((DesktopDd)e).host.scene.environmentTime);Thread.sleep(350);
        check(call(e->((DesktopDd)e).host.scene.environmentTime)==pausedTime,"backgrounding freezes animation clock");
        List<Long> resumed=new ArrayList<>();runOnMainSync(()->{activity.stage.presentationObserver=resumed::add;callActivityOnResume(activity);});
        try{Thread.sleep(1000);}finally{runOnMainSync(()->activity.stage.presentationObserver=null);}
        check(resumed.size()>5&&call(e->e.body().contains("较真地")),"resume restarts presentation at the same dialogue");
        log.append("ANDROID PETALS PASS CHECKS ").append(checks).append('\n');Files.write(output.toPath().resolve("android-petal-result.txt"),log.toString().getBytes("UTF-8"));
    }
    private void transitionRegression()throws Exception {
        log.append(this.<String>call(e->new DdTransitionAudit((DesktopDd)e).run(output.toPath().resolve("transition-regression"))));
        Thread.sleep(800);capture("15-after-eyelids");
        check(call(e->e.body().contains("晨光透过眼帘")),"Android renders reported narration after eyelids");
        check(!activity.failed,"no Android render or media failure at eyelid scene");
        log.append("ANDROID EYELID PASS CHECKS ").append(checks).append('\n');Files.write(output.toPath().resolve("android-transition-result.txt"),log.toString().getBytes("UTF-8"));
    }
    private void overlayRegression()throws Exception {
        log.append(this.<String>call(e->new DmOverlayAudit((DesktopLegacy)e).run(output.toPath().resolve("overlay-regression"))));
        Thread.sleep(800);capture("14-reported-portrait");
        check(call(e->e.vm().scriptName().equalsIgnoreCase("SIN_0810_F12")&&e.vm().offset()==0x2c59),"Android displays reported portrait scene");
        if(!actions().contains(":option"))tap(":n");tap(":option");waitMenu();
        runOnMainSync(()->activity.onBackPressed());waitText();
        check(call(e->!((DesktopLegacy)e).session.inSystem()&&e.vm().offset()==0x2c59),"Android touch/Back returns to reported portrait");
        check(!activity.failed,"no Android render or media failure at portrait scene");
        log.append("ANDROID OVERLAY PASS CHECKS ").append(checks).append('\n');Files.write(output.toPath().resolve("android-overlay-result.txt"),log.toString().getBytes("UTF-8"));
    }
    private void rapidMenus()throws Exception {
        for(String action:new String[]{":menu",":backlog"}){
            call(e->{e.rapid=true;e.tick(0);((DesktopLegacy)e).action(action);return null;});waitMenu();
            check(call(e->!e.rapid&&!e.auto&&((DesktopLegacy)e).session.inSystem()),"rapid input opens "+action+" safely");
            byte[] suspended=call(e->e.snapshot());runOnMainSync(()->activity.onBackPressed());waitText();
            check(call(e->{Dc2Session saved=new Dc2Session(e.resources,e.codec);saved.restore(suspended);return !((DesktopLegacy)e).session.inSystem()&&e.body().equals(saved.host.body)&&e.vm().scriptName().equals(saved.vm.scriptName())&&e.vm().offset()==saved.vm.offset();}),"Android Back restores suspended rapid story for "+action);
            call(e->{e.restore(output.toPath().resolve("roundtrip.sav"));return null;});waitText();
        }
    }
    private void inputRegression()throws Exception {
        check(call(e->e.resources.profile.id.equals("dc2dm-9d72")),"input regression uses original DM data");
        log.append(this.<String>call(e->new LegacyInputAudit((DesktopLegacy)e).run(output.toPath().resolve("input-regression"))));
        Thread.sleep(800);capture("10-reported-line");
        check(call(e->e.vm().scriptName().equalsIgnoreCase("MAR_0114_A1")&&e.vm().offset()==0x493b),"Android renders reported sentence after regression");
        // Exercise genuine Stage touch and Android Back after the deterministic timing test.
        if(!actions().contains(":option"))tap(":n");tap(":option");waitMenu();capture("11-reported-menu");
        check(call(e->((DesktopLegacy)e).session.inSystem()),"original toolbar touch opens reported scene menu");
        runOnMainSync(()->activity.onBackPressed());waitText();capture("12-reported-return");
        check(call(e->e.vm().offset()==0x493b&&!((DesktopLegacy)e).session.inSystem()),"Android Back returns to reported sentence");
        check(!activity.failed,"no Android renderer or media failure at reported scene");
        log.append("ANDROID INPUT PASS CHECKS ").append(checks).append('\n');Files.write(output.toPath().resolve("android-input-result.txt"),log.toString().getBytes("UTF-8"));
    }
    private void openingMovie()throws Exception{
        check(call(e->e.resources.profile.id.equals("dc2dm-9d72")),"opening audit uses DM");
        long end=SystemClock.uptimeMillis()+180000;int advances=0;
        while(call(e->e.movie().isEmpty())&&SystemClock.uptimeMillis()<end){touch(.5f,.35f);advances++;Thread.sleep(80);}
        check(call(e->e.movie().equalsIgnoreCase("AdvData/MOVIE/DC2DM_OP01.MPG")),"original story requests correct movie path");
        end=SystemClock.uptimeMillis()+45000;while(activity.activeMovie()==null&&!activity.failed&&SystemClock.uptimeMillis()<end)Thread.sleep(100);
        NativeMovie movie=activity.activeMovie();check(movie!=null&&!activity.failed,"SAF movie copied and player opened");
        end=SystemClock.uptimeMillis()+45000;while(movie.frames<100&&!activity.failed&&SystemClock.uptimeMillis()<end)Thread.sleep(100);
        check(movie.frames>=100&&!activity.failed,"real MPEG frames and audio playback advance");capture("08-op-playing");
        end=SystemClock.uptimeMillis()+240000;while(activity.activeMovie()!=null&&!activity.failed&&SystemClock.uptimeMillis()<end)Thread.sleep(200);
        check(activity.activeMovie()==null&&!activity.failed,"OP reaches natural end without skipping");end=SystemClock.uptimeMillis()+60000;while(!call(e->e.textWaiting())&&SystemClock.uptimeMillis()<end){touch(.5f,.35f);Thread.sleep(100);}waitText();capture("09-after-op");
        check(call(e->e.vm().scriptName().equalsIgnoreCase("MAR_0114_A1")&&!e.body().isEmpty()),"OP completion continues next original scene");
        check(call(e->{Path p=output.toPath().resolve("after-op.sav");String body=e.body();int pc=e.vm().offset();e.save(p);e.restore(p);return body.equals(e.body())&&pc==e.vm().offset();}),"save and load after OP preserve dialogue and VM position");
        log.append("DM ORIGINAL OPENING PASS CHECKS ").append(checks).append(" advances=").append(advances).append(" frames=").append(movie.frames).append(" last_frame_ms=").append(movie.playbackMillis).append('\n');Files.write(output.toPath().resolve("opening-result.txt"),log.toString().getBytes("UTF-8"));
    }
    private void switchGames()throws Exception{
        output=new File(getTargetContext().getFilesDir(),"android-012-switch");output.mkdirs();
        String[] directories={"Documents%2FDC2DM","Download%2FDC3DD","Download%2FDC3DD"};
        Path dmSaves=null,ddSaves=null;
        for(int n=0;n<directories.length;n++){
            String uri="content://com.android.externalstorage.documents/tree/primary%3A"+directories[n];
            activity=(RuntimeActivity)startActivitySync(new Intent(getTargetContext(),RuntimeActivity.class).putExtra("tree",uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            long end=SystemClock.uptimeMillis()+120000;while(activity.engine==null&&!activity.failed&&SystemClock.uptimeMillis()<end)Thread.sleep(100);check(activity.engine!=null&&!activity.failed,"switch "+n+" initializes");
            boolean title=false;end=SystemClock.uptimeMillis()+90000;while(SystemClock.uptimeMillis()<end){List<String> a=actions();if(a.contains("start")||a.contains("sce_start")){title=true;break;}if(a.contains("window"))tap("window");else if(a.contains("03"))tap("03");else touch(.5f,.35f);Thread.sleep(200);}
            check(title,"switch "+n+" original title reached");capture("title-"+n);
            if(n==0)dmSaves=call(e->e.saveDirectory);
            if(n==1){ddSaves=call(e->e.saveDirectory);check(!dmSaves.equals(ddSaves),"DM and DD persistence directories differ");call(e->{DesktopDd dd=(DesktopDd)e;dd.host.write(99,7,37);e.persist();return null;});}
            if(n==2){check(ddSaves.equals(call(e->e.saveDirectory)),"DD reuses existing saves");check(call(e->((DesktopDd)e).host.read(99,7)==37),"repeat startup reloads stored volume instead of resetting preferences");tap(actions().contains("sce_start")?"sce_start":"start");waitText();capture("repeat-first-dialogue");check(call(e->!e.body().isEmpty()),"repeat DD starts dialogue");}
            RuntimeActivity closing=activity;runOnMainSync(closing::finish);waitForIdleSync();long stop=SystemClock.uptimeMillis()+10000;while(!closing.closed&&SystemClock.uptimeMillis()<stop)Thread.sleep(50);check(closing.closed&&closing.worker.awaitTermination(20,TimeUnit.SECONDS),"switch "+n+" closes worker and media");activity=null;System.gc();
        }
        log.append("DM -> DD -> DD SAME PROCESS PASS CHECKS ").append(checks).append('\n');Files.write(output.toPath().resolve("result.txt"),log.toString().getBytes("UTF-8"));
    }
    private void waitText()throws Exception{long end=SystemClock.uptimeMillis()+45000;while(SystemClock.uptimeMillis()<end){if(call(e->e.textWaiting())){Thread.sleep(150);return;}List<String>a=actions();if(a.contains("sel01"))tap("sel01");else if(a.contains("sceselect01"))tap("sceselect01");Thread.sleep(100);}throw new IOException("Text timeout; "+actions());}
    private void waitMenu()throws Exception{long end=SystemClock.uptimeMillis()+15000;while(SystemClock.uptimeMillis()<end){if(call(e->e.menuWaiting())){Thread.sleep(300);return;}Thread.sleep(100);}throw new IOException("Menu timeout");}
    private void tap(String action)throws Exception{float[] xy=call(e->{for(DesktopEngine.Control c:e.controls())if(c.action.equals(action))return new float[]{(c.left+c.right)/2f/e.width,(c.top+c.bottom)/2f/e.height};throw new IOException("Missing control "+action);});touch(xy[0],xy[1]);log.append("TOUCH ").append(action).append('\n');}
    private void touch(float x,float y)throws Exception{float[] p=new float[2];runOnMainSync(()->{View v=activity.stage;DesktopEngine e=activity.engine;float s=Math.min(v.getWidth()/(float)e.width,v.getHeight()/(float)e.height);int[] at=new int[2];v.getLocationOnScreen(at);p[0]=at[0]+(v.getWidth()-e.width*s)/2+x*e.width*s;p[1]=at[1]+(v.getHeight()-e.height*s)/2+y*e.height*s;});long now=SystemClock.uptimeMillis();MotionEvent a=MotionEvent.obtain(now,now,0,p[0],p[1],0);sendPointerSync(a);a.recycle();Thread.sleep(70);a=MotionEvent.obtain(now,SystemClock.uptimeMillis(),1,p[0],p[1],0);sendPointerSync(a);a.recycle();Thread.sleep(200);}
    private void capture(String name)throws Exception{Bitmap image=getUiAutomation().takeScreenshot();try(FileOutputStream f=new FileOutputStream(new File(output,name+".png"))){image.compress(Bitmap.CompressFormat.PNG,100,f);}image.recycle();}
}
