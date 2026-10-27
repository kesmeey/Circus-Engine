package org.circus.runtime;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.*;
import android.util.AtomicFile;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import org.circus.core.*;

/** Native Android host for the shared core. Original-game admission stays explicit. */
public final class MainActivity extends Activity {
    private final Handler handler=new Handler();private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private DemoHost host;private MesVm vm;private Stage stage;private TextView status;
    private Bitmap bitmap;private String imageName="";private MediaPlayer player;private int heard=-1;
    private long lastTick;private boolean active,closed;private int session;
    private final Runnable frame=new Runnable(){public void run(){if(!active||closed)return;long now=SystemClock.uptimeMillis();if(vm!=null){vm.elapse(Math.max(0,now-lastTick));vm.run(1000);refresh();}lastTick=now;if(vm!=null&&(vm.status()==MesVm.Status.RUNNING||vm.status()==MesVm.Status.WAIT_TIME))handler.postDelayed(this,16);}};
    private void wake(){if(active&&!closed){handler.removeCallbacks(frame);lastTick=SystemClock.uptimeMillis();handler.post(frame);}}
    private byte[] asset(String name)throws IOException{try(InputStream in=getAssets().open("demo/"+Binary.safePath(name))){return Binary.read(in,64*1024*1024);}}
    private MesVm.Library library(){return name->new MesScript(name,asset(name+".mes"),Profiles.get("circus-demo-v1"));}
    @Override public void onCreate(Bundle state){super.onCreate(state);getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,WindowManager.LayoutParams.FLAG_FULLSCREEN);
        LinearLayout layout=new LinearLayout(this);layout.setOrientation(LinearLayout.VERTICAL);layout.setBackgroundColor(Color.rgb(13,19,31));
        layout.setOnApplyWindowInsetsListener((view,insets)->{view.setPadding(insets.getSystemWindowInsetLeft(),insets.getSystemWindowInsetTop(),insets.getSystemWindowInsetRight(),insets.getSystemWindowInsetBottom());return insets;});
        status=new TextView(this);status.setTextColor(Color.rgb(172,191,213));status.setTextSize(13);status.setPadding(18,8,18,8);layout.addView(status);
        stage=new Stage();layout.addView(stage,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout bar=new LinearLayout(this);for(String title:new String[]{"保存","读取","重新开始","回看","数据目录"}){Button b=new Button(this);b.setText(title);b.setAllCaps(false);bar.addView(b,new LinearLayout.LayoutParams(0,-2,1));b.setOnClickListener(v->{switch(title){case "保存":save();break;case "读取":load();break;case "重新开始":startDemo();break;case "回看":backlog();break;default:pick();}});}
        Button dialogue=new Button(this);dialogue.setText("正文测试");bar.addView(dialogue,new LinearLayout.LayoutParams(0,-2,1));dialogue.setOnClickListener(v->startActivity(new Intent(this,DialogueActivity.class)));
        Button resume=new Button(this);resume.setText("继续原作");bar.addView(resume,new LinearLayout.LayoutParams(0,-2,1));resume.setOnClickListener(v->{String tree=getPreferences(0).getString("game_tree",null);if(tree==null)pick();else startActivity(new Intent(this,GameActivity.class).putExtra("tree",tree).putExtra("resume",true));});
        layout.addView(bar);setContentView(layout);layout.requestApplyInsets();startDemo();
    }
    private void startDemo(){try{session++;stopAudio();host=new DemoHost();vm=new MesVm(library(),host,"main");imageName="";heard=-1;vm.run(1000);refresh();wake();}catch(Exception e){failure(e);}}
    private void refresh(){
        if(vm==null)return;status.setText("CIRCUS Core  ·  独立测试场景  ·  "+stateLabel(vm.status()));
        if(!host.scene.equals(imageName)){
            String requested=host.scene;imageName=requested;final int generation=session;
            worker.execute(()->{try{Crx.Image image=Crx.plain(asset(requested));Bitmap ready=Bitmap.createBitmap(image.argb(),image.width,image.height,Bitmap.Config.ARGB_8888);
                handler.post(()->{if(closed||generation!=session||!imageName.equals(requested)){ready.recycle();return;}Bitmap old=bitmap;bitmap=ready;if(old!=null)old.recycle();stage.invalidate();});
            }catch(Exception e){handler.post(()->{if(!closed&&generation==session)failure(e);});}});
        }
        if(active&&host.audioGeneration!=heard&&!host.audio.isEmpty()){heard=host.audioGeneration;play(host.audio,session);}
        stage.invalidate();
    }
    private String stateLabel(MesVm.Status state){switch(state){case WAIT_CLICK:return "轻触画面继续";case WAIT_CHOICE:return "请选择";case WAIT_TIME:return "场景切换中";case HALTED:return "测试场景结束";case FAULT:return "执行已停止";default:return "运行中";}}
    private void play(String name,int generation){worker.execute(()->{try{
        Xpcm audio=Xpcm.decode(asset(name));byte[] bytes=audio.codec.equals("ogg")?audio.payload:audio.wav();File file=new File(getCacheDir(),"core-tone-"+generation+"."+(audio.codec.equals("ogg")?"ogg":"wav"));
        try(FileOutputStream out=new FileOutputStream(file)){out.write(bytes);}
        handler.post(()->{if(closed||!active||generation!=session)return;try{stopAudio();player=new MediaPlayer();player.setDataSource(file.getAbsolutePath());player.setOnPreparedListener(p->{if(active)p.start();});player.setOnCompletionListener(p->stopAudio());player.setOnErrorListener((p,w,e)->{stopAudio();return true;});player.prepareAsync();}catch(Exception e){failure(e);}});
    }catch(Exception e){handler.post(()->{if(!closed&&generation==session)failure(e);});}});}
    private void stopAudio(){if(player!=null){player.release();player=null;}}
    private void save(){if(vm==null)return;try{
        byte[] snapshot=vm.snapshot();AtomicFile file=new AtomicFile(new File(getFilesDir(),"slot1.cvs"));FileOutputStream out=null;
        try{out=file.startWrite();out.write(snapshot);file.finishWrite(out);}catch(IOException e){file.failWrite(out);throw e;}toast("已保存当前场景");
    }catch(Exception e){failure(e);}}
    private void load(){try{
        AtomicFile file=new AtomicFile(new File(getFilesDir(),"slot1.cvs"));byte[] bytes;try(InputStream in=file.openRead()){bytes=Binary.read(in,4*1024*1024);}
        DemoHost candidate=new DemoHost();MesVm restored=new MesVm(library(),candidate,"main");restored.restore(bytes);
        session++;stopAudio();host=candidate;vm=restored;heard=host.audioGeneration;imageName="";refresh();wake();toast("已读取保存的场景");
    }catch(Exception e){failure(e);}}
    private void backlog(){if(host==null)return;TextView text=new TextView(this);text.setText(String.join("\n\n",host.backlog));text.setTextSize(17);text.setPadding(24,20,24,20);ScrollView scroll=new ScrollView(this);scroll.addView(text);new AlertDialog.Builder(this).setTitle("回看").setView(scroll).setPositiveButton("返回",null).show();}
    private void pick(){Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);startActivityForResult(i,10);}
    @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(request!=10||result!=RESULT_OK||data==null||data.getData()==null)return;
        Uri uri=data.getData();try{getContentResolver().takePersistableUriPermission(uri,data.getFlags()&Intent.FLAG_GRANT_READ_URI_PERMISSION);}catch(SecurityException ignored){}
        getPreferences(0).edit().putString("game_tree",uri.toString()).apply();
        new AlertDialog.Builder(this).setTitle("已选择游戏目录").setMessage("DC3DD 原作章节执行仍在适配中，遇到未实现的指令会显示位置。请选择操作。")
            .setPositiveButton("运行 DC3DD",(d,w)->startActivity(new Intent(this,GameActivity.class).putExtra("tree",uri.toString())))
            .setNeutralButton("静态检查",(d,w)->worker.execute(()->{try{String report=new DocumentTree(getContentResolver(),uri).inspect();handler.post(()->{if(!closed)new AlertDialog.Builder(this).setTitle("数据兼容性检查").setMessage(report).setPositiveButton("关闭",null).show();});}catch(Exception e){handler.post(()->{if(!closed)failure(e);});}})).setNegativeButton("取消",null).show();
    }
    private void failure(Exception e){if(!closed)new AlertDialog.Builder(this).setTitle("无法完成操作").setMessage(e.getMessage()).setPositiveButton("关闭",null).show();}
    private void toast(String text){Toast.makeText(this,text,Toast.LENGTH_SHORT).show();}
    @Override protected void onResume(){super.onResume();active=true;wake();}
    @Override protected void onPause(){active=false;handler.removeCallbacks(frame);stopAudio();super.onPause();}
    @Override protected void onDestroy(){closed=true;active=false;handler.removeCallbacksAndMessages(null);stopAudio();worker.shutdownNow();super.onDestroy();}
    private final class Stage extends View {
        private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);private float scale=1,ox,oy;
        Stage(){super(MainActivity.this);setContentDescription("测试场景，轻触继续");}
        @Override protected void onDraw(Canvas c){super.onDraw(c);c.drawColor(Color.rgb(12,19,32));scale=Math.min(getWidth()/1024f,getHeight()/576f);ox=(getWidth()-1024*scale)/2;oy=(getHeight()-576*scale)/2;c.save();c.translate(ox,oy);c.scale(scale,scale);
            if(bitmap!=null&&!bitmap.isRecycled())c.drawBitmap(bitmap,null,new RectF(0,0,1024,576),paint);
            paint.setColor(0x66080e1c);c.drawRect(0,0,1024,576,paint);
            paint.setColor(0xffc5d8eb);paint.setTextSize(16);c.drawText("CIRCUS / CORE TEST",38,42,paint);
            if(vm!=null){
                if(vm.status()==MesVm.Status.WAIT_CHOICE){for(int i=0;i<host.choices.size();i++){DemoHost.Choice b=host.choices.get(i);paint.setColor(0xe6223853);c.drawRoundRect(b.rect[0],b.rect[1],b.rect[2],b.rect[3],12,12,paint);paint.setColor(Color.WHITE);paint.setTextSize(25);c.drawText(b.caption(),b.rect[0]+25,b.rect[1]+48,paint);}}
                paint.setColor(0xe6101928);c.drawRoundRect(28,386,996,548,16,16,paint);paint.setColor(0xfff3f5f8);paint.setTextSize(25);
                String text=vm.status()==MesVm.Status.FAULT?vm.error():host.text;float y=424;for(String line:wrap(text,900)){c.drawText(line,56,y,paint);y+=32;if(y>534)break;}
                if(vm.status()==MesVm.Status.HALTED){paint.setColor(0xff9bc6ba);paint.setTextSize(18);c.drawText("已完成 · 可重新开始或读取",700,365,paint);}
            }c.restore();
        }
        private java.util.List<String> wrap(String value,int width){java.util.List<String> out=new ArrayList<>();for(String paragraph:value.split("\n",-1)){if(paragraph.isEmpty()){out.add("");continue;}int start=0;while(start<paragraph.length()){int count=paint.breakText(paragraph,start,paragraph.length(),true,width,null);if(count==0)count=1;out.add(paragraph.substring(start,start+count));start+=count;}}return out;}
        @Override public boolean onTouchEvent(android.view.MotionEvent e){if(e.getAction()!=MotionEvent.ACTION_UP)return true;if(vm==null)return true;float x=(e.getX()-ox)/scale,y=(e.getY()-oy)/scale;
            if(x<0||x>1024||y<0||y>576)return true;
            try{if(vm.status()==MesVm.Status.WAIT_CHOICE){for(int i=0;i<host.choices.size();i++){DemoHost.Choice b=host.choices.get(i);if(x>=b.rect[0]&&x<b.rect[2]&&y>=b.rect[1]&&y<b.rect[3]){vm.choose(b.action,i);break;}}}else vm.advance();vm.run(1000);refresh();wake();performClick();}catch(Exception error){failure(error);}return true;
        }
        @Override public boolean performClick(){super.performClick();return true;}
    }
}
