package org.circus.runtime;
import android.app.*;
import android.content.*;
import android.graphics.Bitmap;
import android.os.*;
import java.io.*;
import java.lang.reflect.Field;
import java.util.*;
import org.circus.core.Binary;

/** Explicit private-data checkpoints, with scene clock frozen for reproducible PNGs. */
public final class RenderInstrumentation extends Instrumentation {
    public void onCreate(Bundle args){super.onCreate(args);start();}
    private Object field(Object o,String key)throws Exception{Field f=o.getClass().getDeclaredField(key);f.setAccessible(true);return f.get(o);}
    public void onStart(){Bundle result=new Bundle();GameActivity activity=null;StringBuilder log=new StringBuilder();try{
        Context c=getTargetContext();String tree=c.getSharedPreferences("library",0).getString("game_tree",null);if(tree==null)tree=c.getSharedPreferences("MainActivity",0).getString("game_tree",null);if(tree==null)throw new IOException("Select private test data first");File dir=new File(c.getFilesDir(),"render-cases");File[] cases=dir.listFiles((d,n)->n.endsWith(".cvs")&&!n.equals("movie.cvs"));if(cases==null||cases.length==0)throw new IOException("No private render cases");Arrays.sort(cases);GameSaves store=new GameSaves(c,tree);
        for(File file:cases){GameSaves.Save save=new GameSaves.Save();save.script=file.getName();save.when=System.currentTimeMillis();save.revealed=true;try(InputStream in=new FileInputStream(file)){save.core=Binary.read(in,4*1024*1024);}store.write(99,save);
            activity=(GameActivity)startActivitySync(new Intent(c,GameActivity.class).putExtra("tree",tree).putExtra("slot",99).putExtra("render_audit",true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));final GameActivity a=activity;long begin=SystemClock.uptimeMillis();boolean[] ready={false},failed={false};
            do{Thread.sleep(100);runOnMainSync(()->{try{ready[0]=field(a,"vm")!=null&&(int)field(a,"loading")==0;failed[0]=(boolean)field(a,"failed");}catch(Exception e){throw new RuntimeException(e);}});}while(!ready[0]&&!failed[0]&&SystemClock.uptimeMillis()-begin<30000);
            if(!ready[0]||failed[0])throw new IOException("Render failed or timed out: "+file.getName());Thread.sleep(600);Bitmap b=getUiAutomation().takeScreenshot();if(b==null)throw new IOException("Screenshot failed");try(FileOutputStream png=new FileOutputStream(new File(dir,file.getName()+".png"))){b.compress(Bitmap.CompressFormat.PNG,100,png);}b.recycle();log.append("PASS ").append(file.getName()).append('\n');runOnMainSync(a::finish);activity=null;
        }result.putString("stream",log.toString());finish(-1,result);
    }catch(Throwable e){result.putString("stream",log+"FAIL "+e+"\n");finish(0,result);}finally{if(activity!=null){final GameActivity a=activity;runOnMainSync(a::finish);}}}
}
