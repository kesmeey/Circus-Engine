package org.circus.runtime;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import org.circus.core.*;

/** Original-data chapter player. Data remains in the user's read-only document tree. */
public final class GameActivity extends Activity {
    private final Handler main=new Handler();private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private CircusResources data;private TextCodec codec;private Dc3Host host;private MesVm vm;
    private GameSaves saves;private SharedPreferences progress;private int seenAutosave;private long lastAutosave;private boolean endingShown,saveWarning;
    private final int[] resumeAudio=new int[6];
    private long resumeMovie;private boolean renderAudit,originalSystem;private int modalDepth;private long pausedAt;
    private Stage stage;private TextView status;private LinearLayout layout;private AlertDialog choiceDialog;
    private boolean active,closed,failed,auto;private int generation,loading,seenMovie=-1;
    private long lastTick,presentedTicket=-1,presentedAt;private int page;private boolean textRevealed;
    private final MediaPlayer[] players=new MediaPlayer[6];private final int[] heard={-1,-1,-1,-1,-1,-1};
    private final boolean[] prepared=new boolean[6];private Dialog movieDialog;private NativeMovie nativeMovie;private File movieCache;
    private final Map<String,Picture> pictures=new HashMap<>();private final Set<String> requested=new HashSet<>();
    private static final class Picture {
        final Bitmap bitmap,opaque;final int x,y;
        Picture(Crx.Image i){int[] pixels=i.argb();opaque=Bitmap.createBitmap(pixels,i.width,i.height,Bitmap.Config.ARGB_8888);boolean keyed=false;
            if(i.bpp==24)for(int n=0;n<pixels.length;n++)if((pixels[n]&0xffffff)==0xff00ff){pixels[n]=0;keyed=true;}
            bitmap=keyed?Bitmap.createBitmap(pixels,i.width,i.height,Bitmap.Config.ARGB_8888):opaque;x=i.x;y=i.y;
        }
        void recycle(){bitmap.recycle();if(opaque!=bitmap)opaque.recycle();}
    }
    private final Runnable frame=new Runnable(){public void run(){if(!active||closed||failed)return;long now=SystemClock.uptimeMillis();try{
        if(modalDepth>0){lastTick=now;main.postDelayed(this,32);return;}
        if(vm!=null&&loading==0){if(!renderAudit){vm.elapse(Math.max(0,now-lastTick));vm.run(1000);}refresh();
            if(vm.status()==MesVm.Status.FAULT)throw new IOException(vm.error());
            if(host.exitRequested){persistProgress();finish();return;}
            if(!renderAudit&&host.dialogue.waiting()&&loading==0){
                if(presentedTicket!=host.waitTicket()){presentedTicket=host.waitTicket();presentedAt=now;page=0;textRevealed=false;}
                int chars=stage.pageText().length();long reveal=chars*24L;if(textRevealed||now-presentedAt>=reveal){if(!stage.morePages())vm.signalHost(presentedTicket,MesVm.HostSignal.TEXT_COMPLETE);if(auto&&now-presentedAt>reveal+1800&&!host.audioIsActive(5))advancePage();}
            }
        }
        if(!renderAudit&&vm!=null&&loading==0&&!endingShown){if(host.titleRequested){persistProgress();endingShown=true;new AlertDialog.Builder(GameActivity.this).setTitle("本路线已结束").setMessage("通关记录已保存。").setPositiveButton("返回标题",(d,w)->finish()).setCancelable(false).show();}else if(host.autosaveGeneration!=seenAutosave||now-lastAutosave>15000){writeSave(0,false);}}
        lastTick=now;stage.invalidate();if(vm==null||vm.status()!=MesVm.Status.HALTED)main.postDelayed(this,16);
    }catch(Exception e){failure(e);}}};
    private void wake(){if(active&&!closed&&!failed){main.removeCallbacks(frame);lastTick=SystemClock.uptimeMillis();main.post(frame);}}
    @Override public void onCreate(Bundle saved){super.onCreate(saved);getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,WindowManager.LayoutParams.FLAG_FULLSCREEN);
        renderAudit=getIntent().getBooleanExtra("render_audit",false)&&(getApplicationInfo().flags&android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE)!=0;
        originalSystem=getIntent().getBooleanExtra("original_system",!renderAudit&&!getIntent().hasExtra("entry"));
        layout=new LinearLayout(this);layout.setOrientation(LinearLayout.VERTICAL);layout.setBackgroundColor(Color.BLACK);
        layout.setOnApplyWindowInsetsListener((v,i)->{v.setPadding(i.getSystemWindowInsetLeft(),i.getSystemWindowInsetTop(),i.getSystemWindowInsetRight(),i.getSystemWindowInsetBottom());return i;});
        status=new TextView(this);status.setTextColor(0xffbcc9d8);status.setTextSize(12);status.setPadding(12,4,12,4);status.setText("正在识别游戏版本……");layout.addView(status);if(originalSystem)status.setVisibility(View.GONE);
        stage=new Stage();layout.addView(stage,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout bar=new LinearLayout(this);for(String caption:new String[]{"保存","读取","自动","回看","路线","返回"}){Button b=new Button(this);b.setText(caption);bar.addView(b,new LinearLayout.LayoutParams(0,-2,1));b.setOnClickListener(v->{switch(caption){case "保存":save();break;case "读取":load();break;case "自动":auto=!auto;b.setText(auto?"停止自动":"自动");wake();break;case "回看":backlog();break;case "路线":chapters();break;default:finish();}});}if(!originalSystem)layout.addView(bar);setContentView(layout);layout.requestApplyInsets();
        String tree=getIntent().getStringExtra("tree");if(tree==null){failure(new IOException("未选择游戏数据目录"));return;}try{saves=new GameSaves(this,tree);progress=getSharedPreferences(saves.directory.getName(),0);}catch(Exception e){failure(e);return;}
        worker.execute(()->{try{GameResources files=new GameResources(new DocumentTree(getContentResolver(),Uri.parse(tree)));GameVersion version=GameVersion.detect(files);
            if(version.profileId.equals("dc2dm-9d72")||PcHost.supports(version.profile())){main.post(()->{if(closed)return;startActivity(new Intent(this,Dc2Activity.class).putExtra("tree",tree));finish();});return;}
            if(!version.profileId.equals("dc3dd-a5a8"))throw new IOException("已识别脚本版本 "+version.profileId+"，此版本的原版系统宿主尚未接入。");
            CircusResources ready=new CircusResources(files,version.profile());TextCodec map=TextConfiguration.load(ready.files);
            MesScript entry=ready.scripts().script(originalSystem?"START":"DD_1_0611_A1"); // Verify framing before presenting execution controls.
            main.post(()->{if(closed)return;data=ready;codec=map;int slot=getIntent().getIntExtra("slot",-1);if(slot>=0)loadSlot(slot);else if(getIntent().getBooleanExtra("resume",false)&&saves.exists(0))loadSlot(0);else {String name=getIntent().getStringExtra("entry");start(name==null?(originalSystem?"START":"DD_1_0611_A1"):name);}});
        }catch(Exception e){main.post(()->failure(e));}});
    }
    private void start(String entry){if(data==null)return;failed=false;status.setText("正在读取 "+entry);final int token=++generation;stopAudio();stopMovie();loading=1;
        worker.execute(()->{try{Dc3Host h=new Dc3Host(data,codec,originalSystem);MesVm m=new MesVm(data.scripts(),h,entry);mergeProgress(h);main.post(()->{if(closed||token!=generation)return;host=h;vm=m;loading=0;endingShown=false;lastAutosave=SystemClock.uptimeMillis();seenAutosave=h.autosaveGeneration;Arrays.fill(resumeAudio,0);resumeMovie=0;resetPresentation();wake();});}catch(Exception e){main.post(()->{if(token==generation)failure(e);});}});
    }
    private void resetPresentation(){if(choiceDialog!=null){choiceDialog.dismiss();choiceDialog=null;}Arrays.fill(heard,-1);seenMovie=-1;presentedTicket=-1;page=0;textRevealed=false;requested.clear();stage.clearFade();}
    private Set<String> needed(){Set<String> set=new HashSet<>();if(host==null)return set;
        for(int s:new int[]{1,4,5,6,7,8,9,10,11,12,13,24,25,26,30,31})if(!host.scene.slots[s].resource.isEmpty())set.add(host.scene.slots[s].resource);
        for(Dc3Scene.Quad q:host.scene.quads)set.add(q.resource);if(!host.scene.petals.particles.isEmpty()&&!host.scene.slots[27].resource.isEmpty())set.add(host.scene.slots[27].resource);
        if(host.scene.fade.active()){for(int s:new int[]{1,4,5,6,7,8,9,10,11,12,13,24,25,26,30,31})if(!host.scene.fade.from.layers[s].resource.isEmpty())set.add(host.scene.fade.from.layers[s].resource);for(Dc3Scene.Quad q:host.scene.fade.from.quads)set.add(q.resource);if(!host.scene.fade.from.petals.particles.isEmpty())set.add(host.scene.fade.from.layers[27].resource);if(!host.scene.fade.mask.isEmpty())set.add(host.scene.fade.mask);}
        set.addAll(host.ui.resources());set.addAll(host.credits.resources());set.remove("");return set;
    }
    private void refresh(){
        if(vm.status()==MesVm.Status.WAIT_CHOICE&&choiceDialog==null){final MesVm expected=vm;String[] captions=new String[host.choices().size()];for(int i=0;i<captions.length;i++)captions[i]=host.choices().get(i).caption;
            choiceDialog=new AlertDialog.Builder(this).setTitle("请选择").setItems(captions,(d,index)->{choiceDialog=null;if(vm!=expected)return;try{vm.choose(host.choices().get(index).label(),index);wake();}catch(Exception e){failure(e);}}).setCancelable(false).create();choiceDialog.show();}
        status.setText(vm.scriptName()+"  ·  "+(loading>0?"读取资源":host.dialogue.waiting()?"轻触继续":vm.status().toString()));
        Set<String> needed=needed();
        for(String name:needed)if(!pictures.containsKey(name)&&requested.add(name)){final int token=generation;loading++;
            worker.execute(()->{try{Picture p=new Picture(data.image(name));main.post(()->{if(closed||token!=generation){p.recycle();return;}pictures.put(name,p);loading--;stage.invalidate();wake();});}catch(Exception e){main.post(()->{if(token==generation)failure(e);});}});
        }
        // Retain only the visible frame's decoded pixels; archives themselves cache indices.
        Iterator<Map.Entry<String,Picture>> it=pictures.entrySet().iterator();while(it.hasNext()){Map.Entry<String,Picture> e=it.next();if(!needed.contains(e.getKey())){e.getValue().recycle();requested.remove(e.getKey());it.remove();}}
        for(int ch=0;ch<6;ch++)if(heard[ch]!=host.audioGeneration[ch]){heard[ch]=host.audioGeneration[ch];play(ch,host.audioIsActive(ch)?host.audio[ch]:"",heard[ch],generation,host.audioLoop[ch]);}
        if(players[0]!=null&&prepared[0])players[0].setVolume(host.musicGain,host.musicGain);
        if(!renderAudit&&!host.movie.isEmpty()&&seenMovie!=host.movieGeneration){seenMovie=host.movieGeneration;movie(host.movie,host.waitTicket(),generation);}
    }
    private void play(int channel,String ref,int serial,int token,boolean loop){stopAudio(channel);if(ref.isEmpty())return;loading++;
        worker.execute(()->{try{
            Xpcm pcm=data.audio(ref);String extension=pcm.codec.equals("ogg")?"ogg":"wav";File cache=new File(getCacheDir(),"audio-"+channel+"-"+token+"-"+serial+"."+extension);
            try(FileOutputStream out=new FileOutputStream(cache)){out.write(pcm.codec.equals("ogg")?pcm.payload:pcm.wav());}
            main.post(()->{if(closed||token!=generation){cache.delete();return;}if(serial!=heard[channel]){loading--;cache.delete();return;}
                try{MediaPlayer p=new MediaPlayer();players[channel]=p;p.setDataSource(cache.getAbsolutePath());p.setLooping(loop);
                    p.setOnPreparedListener(m->{cache.delete();if(closed||token!=generation||players[channel]!=m)return;loading--;prepared[channel]=true;if(resumeAudio[channel]>0){m.seekTo(Math.min(resumeAudio[channel],Math.max(0,m.getDuration()-1)));resumeAudio[channel]=0;}if(active)m.start();wake();});
                    p.setOnCompletionListener(m->{if(!closed&&token==generation&&players[channel]==m){host.audioCompleted(channel,serial);stopAudio(channel);wake();}});
                    p.setOnErrorListener((m,w,e)->{cache.delete();if(token==generation)failure(new IOException("音频播放失败 "+ref+" ("+w+", "+e+")"));return true;});p.prepareAsync();
                }catch(Exception e){cache.delete();failure(e);}
            });
        }catch(Exception e){main.post(()->{if(token==generation)failure(e);});}});
    }
    private void movie(String reference,long ticket,int token){
        loading++;worker.execute(()->{try{
            File cache=new File(getCacheDir(),"movie-"+token+"-"+ticket+".mpg");
            try(FileOutputStream out=new FileOutputStream(cache)){data.files.copy(reference,out,1024L*1024*1024);}catch(IOException e){cache.delete();throw e;}
            main.post(()->{if(closed||token!=generation){cache.delete();return;}loading--;NativeMovie video=new NativeMovie(this);nativeMovie=video;movieCache=cache;movieDialog=new Dialog(this,android.R.style.Theme_Black_NoTitleBar_Fullscreen);movieDialog.setContentView(video);movieDialog.setCancelable(false);movieDialog.show();movieDialog.getWindow().getDecorView().setSystemUiVisibility(5894);if(!active)video.pauseMovie();video.play(cache,resumeMovie,new NativeMovie.Listener(){public void finished(){stopMovie();if(token==generation&&!closed)try{vm.signalHost(ticket,MesVm.HostSignal.MEDIA_COMPLETE);wake();}catch(Exception e){failure(e);}}public void failed(Exception e){stopMovie();failure(e);}});});
        }catch(Exception e){main.post(()->{if(token==generation)failure(e);});}});
    }
    private void advancePage()throws IOException{if(!host.dialogue.waiting()){vm.signalHost(host.waitTicket(),MesVm.HostSignal.ADVANCE);return;}if(!textRevealed&&SystemClock.uptimeMillis()-presentedAt<stage.pageText().length()*24L){textRevealed=true;return;}if(stage.morePages()){page++;presentedAt=SystemClock.uptimeMillis();textRevealed=false;return;}vm.signalHost(host.waitTicket(),MesVm.HostSignal.TEXT_COMPLETE);vm.signalHost(host.waitTicket(),MesVm.HostSignal.ADVANCE);}
    private void modal(AlertDialog.Builder builder){long opened=SystemClock.uptimeMillis();int openedGeneration=generation;modalDepth++;AlertDialog dialog=builder.create();dialog.setOnDismissListener(d->{modalDepth--;if(openedGeneration==generation)presentedAt+=SystemClock.uptimeMillis()-opened;wake();});dialog.show();}
    private void save(){
        if(vm==null||loading!=0)return;String[] labels=new String[6];for(int i=0;i<6;i++)labels[i]=saves.label(i+1);
        modal(new AlertDialog.Builder(this).setTitle("保存进度").setItems(labels,(d,i)->{int slot=i+1;if(saves.exists(slot))modal(new AlertDialog.Builder(this).setTitle("覆盖此存档？").setMessage(saves.label(slot)).setPositiveButton("覆盖",(a,b)->writeSave(slot,true)).setNegativeButton("取消",null));else writeSave(slot,true);}).setNegativeButton("取消",null));
    }
    private void writeSave(int slot,boolean notify){if(originalSystem||renderAudit||vm==null||failed||host.titleRequested)return;try{GameSaves.Save s=new GameSaves.Save();s.core=vm.snapshot();s.script=vm.scriptName();s.when=System.currentTimeMillis();s.ticket=presentedTicket;s.page=page;s.revealed=textRevealed;s.revealTime=Math.max(0,SystemClock.uptimeMillis()-presentedAt);s.movieMillis=host.movie.isEmpty()?0:nativeMovie!=null?nativeMovie.playbackMillis:resumeMovie;for(int i=0;i<6;i++)s.audio[i]=players[i]!=null&&prepared[i]?Math.max(0,players[i].getCurrentPosition()):resumeAudio[i];saves.write(slot,s);persistProgress();lastAutosave=SystemClock.uptimeMillis();seenAutosave=host.autosaveGeneration;if(notify)toast("已保存到存档 "+slot);}catch(Exception e){if(notify||!saveWarning){saveWarning=true;toast("保存失败："+e.getMessage());}}}
    private void persistProgress()throws IOException{SharedPreferences.Editor edit=progress.edit();for(int i=100;i<=110;i++)edit.putInt("clear_"+i,Math.max(progress.getInt("clear_"+i,0),host.state.read(100,i)));edit.apply();}
    private void mergeProgress(Dc3Host h)throws IOException{for(int i=100;i<=110;i++)h.state.write(100,i,Math.max(h.state.read(100,i),progress.getInt("clear_"+i,0)));}
    private void load(){if(data==null)return;String[] labels=new String[7];for(int i=0;i<7;i++)labels[i]=saves.label(i);modal(new AlertDialog.Builder(this).setTitle("读取进度").setItems(labels,(d,i)->loadSlot(i)).setNegativeButton("取消",null));}
    private void loadSlot(int slot){if(data==null)return;try{GameSaves.Save s=saves.read(slot);Dc3Host h=new Dc3Host(data,codec,originalSystem);MesVm m=new MesVm(data.scripts(),h,"DD_1_0611_A1");m.restore(s.core);mergeProgress(h);generation++;stopAudio();stopMovie();host=h;vm=m;loading=0;failed=false;endingShown=false;resetPresentation();resumeMovie=s.movieMillis;presentedTicket=s.ticket;page=s.page;textRevealed=s.revealed;presentedAt=SystemClock.uptimeMillis()-s.revealTime;System.arraycopy(s.audio,0,resumeAudio,0,6);lastAutosave=SystemClock.uptimeMillis();seenAutosave=h.autosaveGeneration;refresh();wake();if(!renderAudit&&!originalSystem)toast("已读取");}catch(Exception e){new AlertDialog.Builder(this).setTitle("无法读取存档").setMessage(e.getMessage()).setPositiveButton("关闭",(d,w)->{if(vm==null)finish();}).show();}}
    private void chapters(){if(data==null)return;modal(new AlertDialog.Builder(this).setTitle("从头开始一条路线").setItems(LauncherActivity.ROUTES,(d,i)->{writeSave(0,false);start(LauncherActivity.ENTRIES[i]);}).setNegativeButton("取消",null));}
    private void backlog(){if(host==null)return;TextView t=new TextView(this);t.setText(String.join("\n\n",host.dialogue.backlog));t.setTextSize(18);t.setPadding(20,20,20,20);ScrollView scroll=new ScrollView(this);scroll.addView(t);modal(new AlertDialog.Builder(this).setTitle("回看").setView(scroll).setPositiveButton("返回",null));}
    private void failure(Exception e){if(closed)return;failed=true;main.removeCallbacks(frame);stopAudio();stopMovie();status.setText("执行停止："+e.getMessage());new AlertDialog.Builder(this).setTitle("当前运行限制").setMessage(e.getMessage()).setPositiveButton("关闭",null).show();}
    private void toast(String s){Toast.makeText(this,s,Toast.LENGTH_SHORT).show();}
    private void stopAudio(int i){prepared[i]=false;if(players[i]!=null){players[i].release();players[i]=null;}}
    private void stopAudio(){for(int i=0;i<6;i++)stopAudio(i);}
    private void stopMovie(){resumeMovie=0;if(nativeMovie!=null){nativeMovie.close();nativeMovie=null;}if(movieDialog!=null){movieDialog.dismiss();movieDialog=null;}if(movieCache!=null){movieCache.delete();movieCache=null;}}
    @Override public void onBackPressed(){if(originalSystem&&host!=null&&host.ui.waiting)try{if(vm.activateMenu(host.waitTicket(),-1)){wake();return;}}catch(Exception e){failure(e);return;}super.onBackPressed();}
    @Override protected void onResume(){super.onResume();getWindow().getDecorView().setSystemUiVisibility(5894);if(pausedAt!=0){presentedAt+=SystemClock.uptimeMillis()-pausedAt;pausedAt=0;}active=true;for(int i=0;i<6;i++)if(players[i]!=null&&prepared[i])players[i].start();if(nativeMovie!=null)nativeMovie.resumeMovie();wake();}
    @Override protected void onPause(){active=false;main.removeCallbacks(frame);if(vm!=null&&loading==0)writeSave(0,false);pausedAt=SystemClock.uptimeMillis();for(int i=0;i<6;i++)if(players[i]!=null&&prepared[i]&&players[i].isPlaying())players[i].pause();if(nativeMovie!=null)nativeMovie.pauseMovie();super.onPause();}
    @Override protected void onDestroy(){closed=true;generation++;main.removeCallbacksAndMessages(null);stopAudio();stopMovie();if(choiceDialog!=null)choiceDialog.dismiss();worker.shutdownNow();stage.clearFade();for(Picture p:pictures.values())p.recycle();pictures.clear();super.onDestroy();}
    private final class Stage extends View {
        private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
        private Bitmap fadeBitmap;private int fadeSerial=-1;private int[] fadeBase,fadePixels,maskPixels;private int maskWidth,maskHeight;
        void clearFade(){if(fadeBitmap!=null)fadeBitmap.recycle();fadeBitmap=null;fadeBase=null;fadePixels=null;maskPixels=null;fadeSerial=-1;}
        Stage(){super(GameActivity.this);setContentDescription("DC3DD 原作场景，轻触继续");}
        @Override protected void onDraw(Canvas c){super.onDraw(c);c.drawColor(Color.BLACK);if(host==null)return;float scale=Math.min(getWidth()/1024f,getHeight()/576f);c.save();c.translate((getWidth()-1024*scale)/2,(getHeight()-576*scale)/2);c.scale(scale,scale);c.clipRect(0,0,1024,576);
            if(host.credits.active()){drawCredits(c);c.restore();return;}drawScene(c,host.scene.slots,host.scene.quads,host.scene.camera,host.scene.petals,host.scene.fog,host.scene.clouds,host.scene.environmentTime,true);drawFade(c);
            if(originalSystem)drawOriginalUi(c);
            if(host.dialogue.waiting()){
                boolean novel=host.scene.novelMode;paint.setColor(novel?0xa5142032:0xd9142032);c.drawRoundRect(28,novel?24:398,996,562,12,12,paint);paint.setColor(Color.WHITE);paint.setTextSize(23);paint.setShadowLayer(2,1,1,Color.BLACK);
                boolean portrait=!novel&&pictures.containsKey(host.scene.slots[30].resource);float textX=portrait?258:54;
                if(portrait){Picture face=pictures.get(host.scene.slots[30].resource);float fit=Math.min(190f/face.bitmap.getWidth(),190f/face.bitmap.getHeight());float x=40+(190-face.bitmap.getWidth()*fit)/2,y=552-face.bitmap.getHeight()*fit;paint.clearShadowLayer();for(int slot:new int[]{30,31}){Picture p=pictures.get(host.scene.slots[slot].resource);if(p!=null){c.save();c.translate(x+(p.x-face.x)*fit,y+(p.y-face.y)*fit);c.scale(fit,fit);c.drawBitmap(p.bitmap,0,0,paint);c.restore();}}paint.setShadowLayer(2,1,1,Color.BLACK);}
                String speaker=host.dialogue.shownSpeaker;if(!speaker.isEmpty()){paint.setColor(0xffbfeaff);c.drawText(speaker,textX,novel?56:430,paint);paint.setColor(Color.WHITE);}
                String body=pageText();int visible=textRevealed?body.length():(int)Math.min(body.length(),Math.max(0,SystemClock.uptimeMillis()-presentedAt)/24);if(visible<body.length()&&visible>0&&Character.isHighSurrogate(body.charAt(visible-1)))visible--;
                float y=novel?(speaker.isEmpty()?66:87):(speaker.isEmpty()?435:461);for(String line:body.substring(0,visible).split("\n",-1)){c.drawText(line,textX,y,paint);y+=30;}paint.clearShadowLayer();
                if(morePages()){paint.setTextSize(15);paint.setColor(0xffbfeaff);c.drawText("轻触翻页",891,553,paint);}
            }
            c.restore();
        }
        private void drawOriginalUi(Canvas c){
            paint.setAlpha(255);for(Dc3Scene.Quad q:host.ui.visible()){Picture p=pictures.get(q.resource);if(p!=null)c.drawBitmap(q.opaque?p.opaque:p.bitmap,new Rect(q.sx,q.sy,q.sx+q.width,q.sy+q.height),new RectF(q.x,q.y,q.x+q.width,q.y+q.height),paint);}
        }
        private void drawScene(Canvas c,Dc3Scene.Layer[] layers,List<Dc3Scene.Quad> quads,Dc3Camera camera,Dc3Petals petals,boolean fog,boolean clouds,long time,boolean live){
            Integer[] order={1,-1,4,9,-2,5,10,-3,6,11,-4,7,12,8,13,-5,24,25};Arrays.sort(order,(a,b)->Integer.compare(a<0?50:layers[a].priority,b<0?50:layers[b].priority));
            for(int s:order){if(s<0){drawPetals(c,-s-1,petals,layers[27].resource);continue;}if(s==25&&!fog)continue;Dc3Scene.Layer l=layers[s];Picture p=pictures.get(l.resource);if(p==null)continue;c.save();
                if(s==1||s>=4&&s<=13){boolean actor=s!=1;float px=512+camera.x*(actor?.8f:1),py=288+camera.y*(actor?.8f:1);c.translate(px,py);c.scale(camera.zoom/100f,camera.zoom/100f);c.translate(-px,-py);}
                Dc3Scene.Layer transform=s>=9&&s<=13?layers[s-5]:l;int motion=s>=9&&s<=13?s-5:s;
                c.translate(transform.x+transform.pivotX+(live?host.scene.displacement(motion,0):0),transform.y+transform.pivotY+(live?host.scene.displacement(motion,1):0));c.scale(transform.scaleX/100f,transform.scaleY/100f);c.translate(-transform.pivotX,-transform.pivotY);paint.setAlpha(transform.alpha);c.drawBitmap(p.bitmap,l.centered?(1024-p.bitmap.getWidth())/2f:p.x,l.centered?(576-p.bitmap.getHeight())/2f:p.y,paint);c.restore();}
            paint.setAlpha(255);if(clouds){Picture p=pictures.get(layers[26].resource);if(p!=null&&p.bitmap.getWidth()>=3072&&p.bitmap.getHeight()>=1176)for(int row=0;row<2;row++){float x=-2048*(time%(row==0?120000:60000))/(float)(row==0?120000:60000);c.drawBitmap(p.bitmap,new Rect(0,row*600,3072,row*600+576),new RectF(x,0,x+3072,576),paint);}}
            for(Dc3Scene.Quad q:quads){Picture p=pictures.get(q.resource);if(p!=null)c.drawBitmap(q.opaque?p.opaque:p.bitmap,new Rect(q.sx,q.sy,q.sx+q.width,q.sy+q.height),new RectF(q.x,q.y,q.x+q.width,q.y+q.height),paint);}
        }
        private void picture(Canvas c,String name,float x,float y,float scale,int alpha){Picture p=pictures.get(name);if(p==null)return;paint.setAlpha(Math.max(0,Math.min(255,alpha)));c.save();c.translate(x,y);c.scale(scale,scale);c.drawBitmap(p.bitmap,p.x,p.y,paint);c.restore();paint.setAlpha(255);}
        private void drawCredits(Canvas c){Dc3Credits r=host.credits;picture(c,r.image("01",r.index()),r.x(),r.y(),r.scale(),255);picture(c,r.image("02",r.index()),r.x(),r.y(),r.scale(),(int)(r.variantAlpha()*255));paint.setColor(Color.WHITE);paint.setAlpha((int)(r.whiteAlpha()*255));c.drawRect(0,0,1024,576,paint);paint.setAlpha(255);picture(c,"ED/EDBACK",0,0,1,255);float y=-(r.scroll()%576);picture(c,r.image("",r.strip()),0,y,1,255);if(r.strip()+1<r.strips)picture(c,r.image("",r.strip()+1),0,y+576,1,255);paint.setColor(Color.BLACK);paint.setAlpha((int)(r.blackAlpha()*255));c.drawRect(0,0,1024,576,paint);paint.setAlpha(255);}
        private void drawFade(Canvas c){Dc3Transition f=host.scene.fade;if(!f.active()){if(fadeBitmap!=null)clearFade();return;}
            if(f.mode==3){paint.setColor(Color.WHITE);paint.setAlpha((int)(255*(1-Math.abs(f.progress()*2-1))));c.drawRect(0,0,1024,576,paint);paint.setAlpha(255);return;}
            if(loading>0)return;
            if(fadeSerial!=f.generation){clearFade();fadeSerial=f.generation;fadeBitmap=Bitmap.createBitmap(1024,576,Bitmap.Config.ARGB_8888);Canvas previous=new Canvas(fadeBitmap);previous.drawColor(Color.BLACK);drawScene(previous,f.from.layers,f.from.quads,f.from.camera,f.from.petals,f.from.fog,f.from.clouds,f.from.environmentTime,false);
                fadeBase=new int[1024*576];fadeBitmap.getPixels(fadeBase,0,1024,0,0,1024,576);fadePixels=new int[fadeBase.length];
                if(!f.mask.isEmpty()){Picture mask=pictures.get(f.mask);if(mask!=null){maskWidth=mask.bitmap.getWidth();maskHeight=mask.bitmap.getHeight();maskPixels=new int[maskWidth*maskHeight];mask.bitmap.getPixels(maskPixels,0,maskWidth,0,0,maskWidth,maskHeight);}}
            }
            if(f.mode==16&&maskPixels!=null){
                for(int i=0;i<fadePixels.length;i++)fadePixels[i]=fadeBase[i]&0xffffff;
                for(Dc3Transition.Piece p:f.pieces)if(p.life>0&&p.age<24){int dx=(int)p.x,dy=(int)p.y,sx=p.age*32;if(sx+32>maskWidth||maskHeight<32)continue;
                    for(int y=Math.max(0,-dy);y<32&&dy+y<576;y++)for(int x=Math.max(0,-dx);x<32&&dx+x<1024;x++){int dst=(dy+y)*1024+dx+x;if((fadePixels[dst]>>>24)!=255)fadePixels[dst]=(fadePixels[dst]&0xffffff)|(p.age<0?0xff000000:maskPixels[y*maskWidth+sx+x]&0xff000000);}
                }
                fadeBitmap.setPixels(fadePixels,0,1024,0,0,1024,576);paint.setAlpha(255);
            }else if(maskPixels!=null){for(int y=0;y<576;y++)for(int x=0;x<1024;x++){int i=y*1024+x;int blue=maskPixels[(y*maskHeight/576)*maskWidth+x*maskWidth/1024]&255;fadePixels[i]=(fadeBase[i]&0xffffff)|(f.maskAlpha(blue)<<24);}fadeBitmap.setPixels(fadePixels,0,1024,0,0,1024,576);paint.setAlpha(255);}
            else paint.setAlpha((int)(255*(1-f.progress())));
            c.drawBitmap(fadeBitmap,0,0,paint);paint.setAlpha(255);
        }
        private void drawPetals(Canvas c,int group,Dc3Petals petals,String resource){Picture p=pictures.get(resource);if(p==null)return;for(Dc3Petals.Particle particle:petals.particles)if(particle.group==group){paint.setAlpha((int)(particle.alpha*255));int sx=particle.frame*32,sy=particle.row*28;float x=petals.x(particle),y=particle.y;int h=Math.max(0,Math.min(Math.min(28,p.bitmap.getHeight()-sy),(int)(576-y)));if(sx+28<=p.bitmap.getWidth()&&sy+h<=p.bitmap.getHeight()&&h>0)c.drawBitmap(p.bitmap,new Rect(sx,sy,sx+28,sy+h),new RectF(x,y,x+28,y+h),paint);}paint.setAlpha(255);}
        private List<String> lines(){paint.setTextSize(23);return wrap(host.dialogue.shownText,!host.scene.novelMode&&!host.scene.slots[30].resource.isEmpty()?706:910);}
        private int pageSize(){return host.scene.novelMode?15:4;}
        private boolean morePages(){return (page+1)*pageSize()<lines().size();}
        private String pageText(){List<String> lines=lines();int start=Math.min(lines.size(),page*pageSize());return String.join("\n",lines.subList(start,Math.min(lines.size(),start+pageSize())));}
        private List<String> wrap(String text,int width){List<String> result=new ArrayList<>();for(String line:text.split("\n",-1)){if(line.isEmpty()){result.add("");continue;}for(int at=0;at<line.length();){int n=Math.max(1,paint.breakText(line,at,line.length(),true,width,null));result.add(line.substring(at,at+n));at+=n;}}return result;}
        private long touchTicket=-1;private int downButton=-1;
        @Override public boolean onTouchEvent(MotionEvent e){
            if(vm==null||failed||loading!=0)return true;
            try{if(originalSystem&&host.ui.waiting){float scale=Math.min(getWidth()/1024f,getHeight()/576f);float x=(e.getX()-(getWidth()-1024*scale)/2)/scale,y=(e.getY()-(getHeight()-576*scale)/2)/scale;int hit=host.ui.hit(x,y);
                switch(e.getActionMasked()){
                case MotionEvent.ACTION_DOWN:touchTicket=host.waitTicket();downButton=hit;host.hoverMenu(hit);host.ui.pressed=hit;break;
                case MotionEvent.ACTION_MOVE:host.hoverMenu(hit);host.ui.pressed=hit==downButton?hit:-1;break;
                case MotionEvent.ACTION_UP:host.ui.pressed=-1;if(hit>=0&&hit==downButton)vm.activateMenu(touchTicket,hit);downButton=-1;performClick();wake();break;
                case MotionEvent.ACTION_CANCEL:host.ui.pressed=host.ui.hovered=-1;downButton=-1;break;
                }invalidate();return true;
            }if(e.getAction()==MotionEvent.ACTION_UP){advancePage();wake();performClick();}}catch(Exception error){failure(error);}return true;
        }
        @Override public boolean performClick(){super.performClick();return true;}
    }
}
