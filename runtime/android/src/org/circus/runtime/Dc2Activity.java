package org.circus.runtime;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.*;
import android.view.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import org.circus.core.*;

/** Android presentation for the DM host. All layouts and hit regions come from MES. */
public final class Dc2Activity extends Activity {
    private final Handler main=new Handler();private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private Dc2NativeData nativeData;private Dc2Session session;private CircusResources data;private TextCodec codec;private Dc2Host host;private MesVm vm;private GameSaves saves;
    private int stageWidth=1024,stageHeight=576;private boolean pcHost;
    private Stage stage;private boolean active,closed,failed,auto,rapid,toolbar,hidden;private int loading,generation,seenMovie=-1,seenSave;
    private long tick,textAt,textTicket=-1,lastSave;private String tree;
    private final Map<String,Picture> images=new HashMap<>();private final Set<String> requested=new HashSet<>();
    private final MediaPlayer[] players=new MediaPlayer[6];private final boolean[] prepared=new boolean[6];private final int[] heard={-1,-1,-1,-1,-1,-1};
    private NativeMovie movie;private Dialog movieDialog;private File movieFile;
    private static final class Picture {Bitmap bitmap;int x,y;Picture(Crx.Image source){int[] p=source.argb();if(source.bpp==24)for(int n=0;n<p.length;n++)if((p[n]&0xffffff)==0xff00ff)p[n]=0;bitmap=Bitmap.createBitmap(p,source.width,source.height,Bitmap.Config.ARGB_8888);x=source.x;y=source.y;}}
    @Override public void onCreate(Bundle b){super.onCreate(b);getWindow().addFlags(128|1024);getWindow().getDecorView().setSystemUiVisibility(5894);stage=new Stage();setContentView(stage);tree=getIntent().getStringExtra("tree");
        worker.execute(()->{try{GameResources files=new GameResources(new DocumentTree(getContentResolver(),Uri.parse(tree)));GameVersion v=GameVersion.detect(files);if(!v.profileId.equals("dc2dm-9d72")&&!PcHost.supports(v.profile()))throw new IOException("DC2DM profile required");CircusResources r=new CircusResources(files,v.profile());TextCodec c=PcHost.supports(v.profile())?GameText.detect(files,v.profile()):TextConfiguration.load(files);Dc2NativeData tables=PcHost.supports(v.profile())?null:new Dc2NativeData(r,c);main.post(()->{data=r;codec=c;nativeData=tables;pcHost=PcHost.supports(r.profile);if(pcHost){stageWidth=800;stageHeight=600;}try{saves=new GameSaves(this,tree,pcHost?r.profile.id:"dc2dm");start();}catch(Exception e){fail(e);}});}catch(Exception e){main.post(()->fail(e));}});
    }
    private void start()throws IOException {session=new Dc2Session(data,codec);host=session.host;vm=session.vm;readProgress();scanSaves();int slot=getIntent().getIntExtra("slot",-1);if(slot>=0&&saves.exists(slot))loadSave(slot);lastSave=SystemClock.uptimeMillis();wake();}
    private void wake(){if(active&&!failed&&!closed){main.removeCallbacks(frame);tick=SystemClock.uptimeMillis();main.post(frame);}}
    private final Runnable frame=new Runnable(){public void run(){if(!active||closed||failed)return;try{long now=SystemClock.uptimeMillis();if(vm!=null&&loading==0){
        vm.elapse(Math.min(100,Math.max(0,now-tick)));if(vm.status()==MesVm.Status.RUNNING)vm.run(1000);if(vm.status()==MesVm.Status.FAULT)throw new IOException(vm.error());
        if(vm.status()==MesVm.Status.HALTED){finish();return;}
        session.observeTransfer();session.captureHistory();if(host.history.rewindRequest>=0){session.rewindHistory(host.history.rewindRequest);adopt();auto=rapid=false;if(!pcHost)host.write(99,13,0);}if(host.returnRequested){session.returnToStory();adopt(true);}if(host.saveRequest>=0){writeSave(host.saveRequest);host.completeSave();vm.elapse(0);}if(host.loadRequest>=0)loadSave(host.loadRequest);
        if(!pcHost)auto=host.read(99,13)!=0;refresh();for(int ch=0;ch<6;ch++)if(players[ch]!=null&&prepared[ch]){float gain=volume(ch);players[ch].setVolume(gain,gain);}
        if(host.presentationWaiting){if(textTicket!=host.waitTicket()){textTicket=host.waitTicket();textAt=now;}if(now-textAt>=Math.max(60,host.body.length()*textSpeed()))vm.signalHost(host.waitTicket(),MesVm.HostSignal.TEXT_COMPLETE);}
        if(rapid&&host.skipAllowed&&(host.textWaiting||host.presentationWaiting))vm.signalHost(host.waitTicket(),MesVm.HostSignal.ADVANCE);
        if(auto&&host.textWaiting&&now-textAt>Math.max(1200,host.body.length()*80L)&&!host.audioActive[5])vm.signalHost(host.waitTicket(),MesVm.HostSignal.ADVANCE);
        if(!session.inSystem()&&!host.replayMode&&host.textWaiting&&(now-lastSave>20000||seenSave!=host.autosaveGeneration)){writeSave(0);writeSave(2);writeProgress();seenSave=host.autosaveGeneration;lastSave=now;}
        }tick=now;stage.invalidate();main.postDelayed(this,16);
    }catch(Exception e){fail(e);}}};
    private Set<String> needed(){Set<String> s=new HashSet<>();if(host==null)return s;s.addAll(host.credits.resources());if(host.gridWaiting||host.history.active||host.musicRoom.active)s.add(host.scene.slots[10]);if(host.musicRoom.active)s.add(host.scene.slots[11]);if(host.scene.particleKind!=0)s.add(host.scene.slots[host.scene.particleKind==2?17:16]);if(host.scene.overlayKind!=0)s.add(host.scene.slots[15]);collect(s,host.scene.shown);if(host.scene.transitionRemaining>0)collect(s,host.scene.previous);for(int n=21;n<=25;n++)s.add(host.scene.slots[n]);s.remove("");return s;}
    private void collect(Set<String> s,Dc2Scene.Frame f){s.add(f.slots[1]);for(int n=2;n<=9;n++)s.add(f.slots[n]);if(!host.body.isEmpty()){s.add(f.slots[18]);s.add(f.slots[19]);}for(Dc2Ui.Quad q:f.quads)if(q.type==0&&q.slot()<f.slots.length)s.add(f.slots[q.slot()]);for(Dc2Ui.Button b:host.ui.buttons)for(Dc2Ui.Quad q:b.sprites)if(q.type==0&&q.slot()<f.slots.length)s.add(f.slots[q.slot()]);}
    private void refresh(){Set<String> needed=needed();for(String name:needed)if(!images.containsKey(name)&&requested.add(name)){loading++;int token=generation;worker.execute(()->{try{Picture p=new Picture(data.image(name));main.post(()->{if(closed||token!=generation){p.bitmap.recycle();return;}images.put(name,p);loading--;wake();});}catch(Exception e){main.post(()->{if(token==generation)fail(new IOException("图像 "+name,e));});}});}
        Iterator<Map.Entry<String,Picture>> it=images.entrySet().iterator();while(it.hasNext()){Map.Entry<String,Picture> e=it.next();if(!needed.contains(e.getKey())){e.getValue().bitmap.recycle();requested.remove(e.getKey());it.remove();}}
        for(int n=0;n<6;n++)if(heard[n]!=host.audioGeneration[n]){heard[n]=host.audioGeneration[n];audio(n,host.audio[n],heard[n],host.audioLoop[n]);}
        if(!host.movie.isEmpty()&&seenMovie!=host.movieGeneration){seenMovie=host.movieGeneration;playMovie(host.movie,host.waitTicket());}
    }
    private void stopAudio(int n){MediaPlayer p=players[n];players[n]=null;prepared[n]=false;if(p!=null)p.release();}
    private void audio(int ch,String name,int serial,boolean loop){stopAudio(ch);if(name.isEmpty())return;loading++;int token=generation;worker.execute(()->{try{Xpcm x=data.audio(name);File f=new File(getCacheDir(),"dm-audio-"+ch+"-"+token+"-"+serial+(x.codec.equals("ogg")?".ogg":".wav"));try(FileOutputStream out=new FileOutputStream(f)){out.write(x.codec.equals("ogg")?x.payload:x.wav());}main.post(()->{if(closed||token!=generation){f.delete();return;}if(serial!=heard[ch]){loading--;f.delete();return;}try{MediaPlayer p=new MediaPlayer();players[ch]=p;p.setDataSource(f.getAbsolutePath());p.setLooping(loop);p.setOnPreparedListener(m->{f.delete();if(players[ch]!=m)return;prepared[ch]=true;loading--;if(active)m.start();wake();});p.setOnCompletionListener(m->{if(players[ch]==m){host.audioCompleted(ch,serial);stopAudio(ch);wake();}});p.setOnErrorListener((m,w,e)->{f.delete();fail(new IOException("音频播放失败 "+name));return true;});p.prepareAsync();}catch(Exception e){f.delete();fail(e);}});}catch(Exception e){main.post(()->{if(token==generation)fail(new IOException("音频 "+name,e));});}});}
    private void playMovie(String name,long ticket){loading++;int token=generation;worker.execute(()->{try{File f=new File(getCacheDir(),"dm-movie.mpg");try(FileOutputStream out=new FileOutputStream(f)){data.files.copy(name,out,1024L*1024*1024);}main.post(()->{if(closed||token!=generation){f.delete();return;}loading--;movieFile=f;movie=new NativeMovie(this);movieDialog=new Dialog(this,android.R.style.Theme_Black_NoTitleBar_Fullscreen);movieDialog.setContentView(movie);movieDialog.setCancelable(false);movieDialog.show();movieDialog.getWindow().getDecorView().setSystemUiVisibility(5894);if(pcHost){movie.setOnTouchListener((v,event)->{if(event.getActionMasked()==MotionEvent.ACTION_UP)finishPcMovie(ticket);return true;});movieDialog.setOnKeyListener((d,key,event)->{if(key!=KeyEvent.KEYCODE_BACK)return false;if(event.getAction()==KeyEvent.ACTION_UP)finishPcMovie(ticket);return true;});}movie.play(f,0,new NativeMovie.Listener(){public void finished(){stopMovie();try{vm.signalHost(ticket,MesVm.HostSignal.MEDIA_COMPLETE);wake();}catch(Exception e){fail(e);}}public void failed(Exception e){stopMovie();fail(e);}});});}catch(Exception e){main.post(()->fail(e));}});}
    private void finishPcMovie(long ticket){stopMovie();try{vm.signalHost(ticket,MesVm.HostSignal.MEDIA_COMPLETE);wake();}catch(Exception e){fail(e);}}
    private void stopMovie(){if(movie!=null){movie.close();movie=null;}if(movieDialog!=null){movieDialog.dismiss();movieDialog=null;}if(movieFile!=null){movieFile.delete();movieFile=null;}}
    private void writeSave(int slot)throws IOException{GameSaves.Save s=new GameSaves.Save();s.core=session.storySnapshot();s.script=session.storyScript();s.when=System.currentTimeMillis();saves.write(slot,s);host.availableSaves.add(slot);host.saveLabels.put(slot,saveDate(s.when));}
    private static String saveDate(long when){return new java.text.SimpleDateFormat("MM/dd  HH:mm",java.util.Locale.ROOT).format(new java.util.Date(when));}
    private void scanSaves(){host.availableSaves.clear();host.saveLabels.clear();for(int n=0;n<104;n++)if(saves.exists(n)){host.availableSaves.add(n);try{host.saveLabels.put(n,saveDate(saves.read(n).when));}catch(IOException ignored){}}}
    private long textSpeed()throws IOException{return Math.max(1,11-host.read(99,26))*10L;}
    private float volume(int ch)throws IOException{int enabled=ch==0?14:ch==5?5:15,level=ch==0?7:ch==5?9:8;if(host.read(99,enabled)==0)return 0;return (ch==0?host.musicFadeGain():1)*Math.max(0,Math.min(1,(host.read(99,level)*2-(ch==0?host.musicAttenuation:0))/60f));}
    private void readProgress()throws IOException{File f=new File(saves.directory,"progress.dm");if(f.isFile()||new File(f+".bak").isFile())try(DataInputStream in=new DataInputStream(new android.util.AtomicFile(f).openRead())){host.loadPersistent(in);}}
    private void writeProgress()throws IOException{android.util.AtomicFile f=new android.util.AtomicFile(new File(saves.directory,"progress.dm"));FileOutputStream out=null;try{out=f.startWrite();host.savePersistent(new DataOutputStream(out));f.finishWrite(out);}catch(IOException e){f.failWrite(out);throw e;}}
    private void adopt(){adopt(false);}
    private void adopt(boolean keepMusic){boolean preserve=keepMusic&&host!=null&&players[0]!=null&&prepared[0]&&host.audio[0].equals(session.host.audio[0])&&session.host.audioActive[0];host=session.host;vm=session.vm;generation++;loading=0;for(Picture p:images.values())p.bitmap.recycle();images.clear();requested.clear();for(int n=0;n<6;n++)if(n!=0||!preserve)stopAudio(n);Arrays.fill(heard,-1);if(preserve){heard[0]=host.audioGeneration[0];int serial=heard[0];players[0].setLooping(host.audioLoop[0]);players[0].setOnCompletionListener(m->{if(players[0]==m){host.audioCompleted(0,serial);stopAudio(0);wake();}});}seenMovie=-1;toolbar=false;hidden=false;textTicket=-1;scanSaves();wake();}
    private void loadSave(int slot)throws IOException{GameSaves.Save s=saves.read(slot);session.restore(s.core);adopt();}
    private void openSystem(String name)throws IOException{if(host.presentationWaiting){vm.signalHost(host.waitTicket(),MesVm.HostSignal.TEXT_COMPLETE);vm.run(1000);}session.captureHistory();session.openSystem(name);toolbar=false;auto=rapid=false;if(!pcHost)host.write(99,13,0);wake();}
    private void action(String a)throws IOException {switch(a){case ":n":toolbar=!toolbar;host.write(99,227,toolbar?1:0);break;case ":auto":auto=!auto;toolbar=false;if(!pcHost)host.write(99,13,auto?1:0);break;case ":rapid":rapid=!rapid;toolbar=false;break;case ":option":openSystem(pcHost?"OPTION":"ADVMENU");break;case ":backlog":openSystem("LOG");break;case ":torisetu":openSystem("MANUAL");break;case ":menu":openSystem("ADVMENU");break;case ":full":break;case ":close":hidden=true;break;case ":qsave":case ":qs":writeSave(1);toolbar=false;break;case ":qload":case ":ql":if(saves.exists(1))loadSave(1);toolbar=false;break;case ":clear":hidden=true;toolbar=false;break;default:throw new IOException("尚未实现的原版系统动作 "+a);}wake();}
    private void fail(Exception e){if(closed||failed)return;failed=true;main.removeCallbacks(frame);for(int n=0;n<6;n++)stopAudio(n);android.util.Log.e("CIRCUS-DM",vm==null?"startup":vm.scriptName()+" @"+Integer.toHexString(vm.offset()),e);new AlertDialog.Builder(this).setTitle("CIRCUS 兼容诊断").setMessage((vm==null?"":vm.scriptName()+" @0x"+Integer.toHexString(vm.offset())+"\n")+e.toString()).setPositiveButton("返回",(d,w)->finish()).setCancelable(false).show();}
    @Override protected void onResume(){super.onResume();active=true;for(int n=0;n<6;n++)if(prepared[n]&&players[n]!=null)players[n].start();if(movie!=null)movie.resumeMovie();wake();}
    @Override protected void onPause(){try{if(host!=null&&saves!=null){if(!session.inSystem()&&!host.replayMode&&host.textWaiting)writeSave(0);writeProgress();}}catch(IOException e){android.util.Log.e("CIRCUS-DM","Save on pause",e);}active=false;main.removeCallbacks(frame);for(int n=0;n<6;n++)if(prepared[n]&&players[n]!=null)players[n].pause();if(movie!=null)movie.pauseMovie();super.onPause();}
    @Override protected void onDestroy(){closed=true;generation++;main.removeCallbacks(frame);for(int n=0;n<6;n++)stopAudio(n);stopMovie();worker.shutdownNow();for(Picture p:images.values())p.bitmap.recycle();images.clear();super.onDestroy();}
    @Override public void onBackPressed(){try{if(host!=null&&(pcHost&&((PcHost)host).hasTimedInput()||host.ui.waiting||host.remainingMillis()<0&&!host.ui.actions[3].isEmpty())){vm.activateMenu(host.waitTicket(),-1);wake();}else if(host!=null&&!host.body.isEmpty()){toolbar=!toolbar;host.write(99,227,toolbar?1:0);stage.invalidate();}else super.onBackPressed();}catch(IOException e){fail(e);}}
    private final class Stage extends View {
        final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);int pressed=-1;float scale,ox,oy,downX,downY;
        Stage(){super(Dc2Activity.this);setBackgroundColor(Color.BLACK);setLayerType(View.LAYER_TYPE_SOFTWARE,null);}
        private List<Dc2Ui.Button> buttons(){if(host.ui.waiting){if(!host.gridWaiting)return host.ui.buttons;List<Dc2Ui.Button> all=new ArrayList<>(host.ui.buttons);for(int n=0;n<host.grid.size();n++){Dc2Ui.Button b=host.grid.get(n);b.action="@slot:"+n;all.add(b);}return all;}if(!host.body.isEmpty()&&!hidden)return host.ui.buttonTemplates.get(pcHost?(host.messageMode==0?1:4):(toolbar?6:1));return Collections.emptyList();}
        @Override protected void onDraw(Canvas c){super.onDraw(c);if(host==null)return;try{scale=Math.min(getWidth()/(float)stageWidth,getHeight()/(float)stageHeight);ox=(getWidth()-stageWidth*scale)/2;oy=(getHeight()-stageHeight*scale)/2;c.save();c.translate(ox,oy);c.scale(scale,scale);c.clipRect(0,0,stageWidth,stageHeight);
            if(host.credits.active()){drawCredits(c);c.restore();return;}Dc2Scene s=host.scene;float progress=s.transitionDuration==0?1:1-s.transitionRemaining/(float)s.transitionDuration;
            if(progress<1){drawScene(c,s.previous);int layer=c.saveLayerAlpha(0,0,stageWidth,stageHeight,(int)(255*progress));drawScene(c,s.shown);c.restoreToCount(layer);}else drawScene(c,s.shown);
            if(!host.body.isEmpty()&&!hidden){for(Dc2Ui.Quad q:host.ui.quadTemplates.get(host.messageMode==0?1:4))quad(c,q,s.slots,false);
                if(!host.speaker.isEmpty())for(Dc2Ui.Quad q:host.ui.quadTemplates.get(host.messageMode==0?2:5))quad(c,q,s.slots,false);
                drawMessage(c);for(int n=18;n<=19;n++){Picture p=images.get(s.shown.slots[n]);if(p!=null)c.drawBitmap(p.bitmap,s.portraitX,s.portraitY,paint);}
                if(host.textWaiting)for(Dc2Ui.Quad q:host.ui.quadTemplates.get(3))quad(c,q,s.slots,false);
            }
            List<Dc2Ui.Button> bs=buttons();for(int n=0;n<bs.size();n++){Dc2Ui.Button b=bs.get(n);int state=n==pressed?2:99;for(Dc2Ui.Quad q:b.sprites)if(q.state==state||q.state==0)quad(c,q,s.slots,true);drawSlider(c,b);}if(host.gridWaiting)drawGrid(c);if(host.history.active)drawHistory(c);if(host.musicRoom.active)drawMusicRoom(c);c.restore();
        }catch(Exception e){main.post(()->fail(e));}}
        private void picture(Canvas c,String name,float x,float y){Picture p=images.get(name);paint.setAlpha(255);if(p!=null)c.drawBitmap(p.bitmap,x,y,paint);}
        private void drawCredits(Canvas c){Dc2Credits r=host.credits;c.drawColor(Color.BLACK);c.save();c.clipRect(80,160,480,460);picture(c,r.image(true,r.picture()),80,160);c.restore();picture(c,"ED/photo",80,160);float y=-(r.scroll()%600);picture(c,r.image(false,r.strip()),480,y);if(r.strip()+1<r.strips)picture(c,r.image(false,r.strip()+1),480,y+600);}
        private Dc2Ui.Quad slider(Dc2Ui.Button b){for(Dc2Ui.Quad q:b.sprites)if(q.state==5)return q;return null;}
        // Native 4203F0/420486 clips state-2 fills to the value, then positions state-6 end caps.
        private void drawSlider(Canvas c,Dc2Ui.Button b)throws IOException{
            Dc2Ui.Quad config=slider(b);if(config==null)return;int value=vm.variables.read(config.sy,config.width);
            int offset=(config.y-config.x)*Math.max(0,Math.min(config.sx-1,value))/Math.max(1,config.sx-1);
            for(Dc2Ui.Quad q:b.sprites){if(q.state==2){int width=Math.min(q.width,config.x+offset-q.x);if(width>0){c.save();c.clipRect(q.x,q.y,q.x+width,q.y+q.height);quad(c,q,host.scene.slots,true);c.restore();}}else if(q.state==6){Picture p=images.get(host.scene.slots[q.slot()]);if(p!=null){paint.setAlpha(255);float x=q.x+offset;c.drawBitmap(p.bitmap,new Rect(q.sx,q.sy,q.sx+q.width,q.sy+q.height),new RectF(x,q.y,x+q.width,q.y+q.height),paint);}}}
        }
        // 40F220: reserved quick/auto/new entries, original 336x84 SL_CHIP cells and 16x24 digits.
        private void drawGrid(Canvas c)throws IOException{
            Picture p=images.get(host.scene.slots[10]);if(p==null)return;int page=host.read(99,0);
            for(int n=0;n<host.grid.size();n++){Dc2Ui.Button b=host.grid.get(n);int slot=1+page*10+n;boolean selected=pressed==host.ui.buttons.size()+n;int sx=slot==1?1008:0,sy=slot==3?84:slot<=2?0:168;
                if(selected){if(slot==1)sy=host.saveGrid?0:336;else {sx=336;sy+=host.saveGrid?0:252;}}
                paint.setAlpha(255);c.drawBitmap(p.bitmap,new Rect(sx,sy,Math.min(sx+336,p.bitmap.getWidth()),sy+84),new Rect(b.left,b.top,b.right,b.bottom),paint);
                if(slot>3){int number=slot-3;for(int k=0;k<2;k++){int digit=k==0?number/10:number%10;int x=b.left+52+k*16;c.drawBitmap(p.bitmap,new Rect(336+digit*16,508,352+digit*16,532),new Rect(x,b.top+28,x+16,b.top+52),paint);}}
                String label=host.saveLabels.get(slot);if(label!=null)text(c,label,b.left+120,b.top+24,22,13,b.right-b.left-136);
            }
        }
        private void drawScene(Canvas c,Dc2Scene.Frame f)throws IOException{c.drawColor(Color.BLACK);Picture bg=images.get(f.slots[1]);paint.setAlpha(255);if(bg!=null)c.drawBitmap(bg.bitmap,bg.x-f.x,bg.y-f.y,paint);
            for(int n=3;n>=0;n--){int[] a=f.actors[n];for(int slot:new int[]{n+2,n+6}){Picture p=images.get(f.slots[slot]);if(p==null)continue;float zoom=a[2]/100f;c.save();c.translate(a[0]+a[3],a[1]+a[4]);c.scale(zoom,zoom);c.drawBitmap(p.bitmap,p.x-a[3],p.y-a[4],paint);c.restore();}}
            drawEffects(c);for(Dc2Ui.Quad q:f.quads)quad(c,q,f.slots,false);
        }
        // Native sprite sizes and density; random phase and motion interpolation are portable approximations.
        private void drawEffects(Canvas c)throws IOException{Dc2Scene s=host.scene;if(host.read(99,10)==0)return;long t=s.effectClock/34;
            if(s.overlayKind==7){Picture p=images.get(s.slots[15]);if(p!=null){paint.setAlpha(96);float x=-(t%Math.max(1,p.bitmap.getWidth()));for(float a=x;a<stageWidth;a+=p.bitmap.getWidth())c.drawBitmap(p.bitmap,a,0,paint);paint.setAlpha(255);}}
            if(s.particleKind==0)return;Picture p=images.get(s.slots[s.particleKind==2?17:16]);if(p==null)return;int level=s.particleStrength&15,count=level>=5?50:new int[]{0,5,10,15,30}[level];paint.setAlpha(255);
            for(int n=0;n<count;n++){int variant=n%6,w=s.particleKind==2?8:32,h=s.particleKind==2?80:24;int sy=s.particleKind==2?0:variant*28;if(sy+h>p.bitmap.getHeight())continue;int sx=s.particleKind==2?0:(int)((t/(4+n%8))%5)*w;float x=(float)((n*193+Math.sin((t*(4+n%8)+n*71)*Math.PI/180)*(24+n%24)+stageWidth)%stageWidth);float y=(n*137+t*(s.particleKind==2?32:2+n%4))%(stageHeight+64)-32;if(s.particleKind==9)y=stageHeight-y;c.drawBitmap(p.bitmap,new Rect(sx,sy,sx+w,sy+h),new RectF(x,y,x+w,y+h),paint);}
        }
        private void quad(Canvas c,Dc2Ui.Quad q,String[] slots,boolean sprite)throws IOException{
            if(q.binding!=0&&vm.variables.read(q.binding>>8,q.binding&255)==0)return;
            paint.setAlpha(255);if(q.type==2){if(!q.text.isEmpty())text(c,codec.apply(q.text),q.x,q.y,sprite?23:Math.max(22,q.width),sprite?q.flags:q.height,stageWidth-q.x);return;}
            if(q.width<=0||q.height<=0)return;
            if(q.type==1){paint.setColor(host.ui.palette[q.flags&63]);paint.setAlpha(q.opaque()?80:255);c.drawRect(q.x,q.y,q.x+q.width,q.y+q.height,paint);paint.setAlpha(255);return;}
            if(q.type!=0)throw new IOException("DM drawing primitive "+q.type);
            if(q.slot()>=slots.length)throw new IOException("DM drawing slot");Picture p=images.get(slots[q.slot()]);if(p==null)return;
            int sx=q.sx,sy=q.sy;if(q.animation!=0&&q.frames>0&&q.period>0)sx+=(int)(SystemClock.uptimeMillis()/(34L*q.period)%q.frames)*q.step;
            int left=Math.max(0,sx),top=Math.max(0,sy),right=Math.min(p.bitmap.getWidth(),sx+q.width),bottom=Math.min(p.bitmap.getHeight(),sy+q.height);if(right<=left||bottom<=top)return;
            if(q.opaque())paint.setAlpha(80);c.drawBitmap(p.bitmap,new Rect(left,top,right,bottom),new Rect(q.x+left-sx,q.y+top-sy,q.x+right-sx,q.y+bottom-sy),paint);paint.setAlpha(255);
        }
        private void chip(Canvas c,int slot,int sx,int sy,int width,int height,float x,float y){Picture p=images.get(host.scene.slots[slot]);if(p==null)return;paint.setAlpha(255);c.drawBitmap(p.bitmap,new Rect(sx,sy,sx+width,sy+height),new RectF(x,y,x+width,y+height),paint);}
        private void roomText(Canvas c,String value,float x,float y,float width,int size,int color){
            paint.setTypeface(Typeface.create("sans-serif",Typeface.NORMAL));paint.setTextSize(size);paint.setColor(color);paint.setAlpha(255);float at=x,baseline=y+size;
            for(int n=0;n<value.length();){int cp=value.codePointAt(n);String ch=new String(Character.toChars(cp));n+=Character.charCount(cp);float w=paint.measureText(ch);if(cp==10||at+w>x+width){at=x;baseline+=size+4;if(cp==10)continue;}c.drawText(ch,at,baseline,paint);at+=w;}
        }
        private void drawHistory(Canvas c){
            Dc2History h=host.history;
            for(int row=0;row<4&&row+h.cursor<h.entries.size();row++){
                int index=row+h.cursor,y=25+row*133;Dc2History.Entry e=h.entries.get(index);
                chip(c,10,228,0,680,124,200,y-4);c.save();c.clipRect(200,y-4,880,y+120);
                Integer face=nativeData.faces.get(e.speaker);
                if(face!=null){int sx=face==24?864:168+(face%6)*116,sy=face==24?480:132+(face/6)*116;chip(c,10,sx,sy,116,116,240,y);}
                else roomText(c,e.speaker,212,y+12,135,20,0xff9a386d);
                roomText(c,e.body,360,y+5,500,22,index==h.selected?0xff99325b:0xff663b63);
                if(!e.voice.isEmpty())chip(c,10,84,120,24,24,332,y+6);
                c.restore();
            }
            // Native log rail and arrows; dragging supplements the PC wheel controls.
            chip(c,10,988,0,32,332,948,112);chip(c,10,908,0,40,24,944,88);chip(c,10,948,0,40,24,944,444);
            int at=112+(int)(300L*h.cursor/Math.max(1,h.entries.size()-4));chip(c,10,908,72,32,32,948,at);
            if(h.entries.isEmpty())roomText(c,"暂无回看记录",360,220,480,22,0xff99325b);
        }
        private void drawMusicRoom(Canvas c){
            Dc2MusicRoom r=host.musicRoom;
            roomText(c,String.format(Locale.ROOT,"%03d  %s",r.selected+1,r.data.musicTitles.get(r.selected)),42,201,320,19,0xffa73563);
            for(int row=0;row<10&&row+r.top<r.data.musicTitles.size();row++){
                int index=row+r.top,y=78+row*41;boolean selected=index==r.selected;
                chip(c,10,491,selected?159:77,59,37,435,y);
                roomText(c,String.format(Locale.ROOT,"%03d",index+1),450,y+7,47,18,selected?0xffcc416e:0xff95536b);
                c.save();c.clipRect(500,y,944,y+37);String title=r.data.musicTitles.get(index);roomText(c,title,504,y+6,440,title.length()>29?16:20,0xffa73563);c.restore();
            }
            chip(c,10,436,88,10,388,960,88);chip(c,10,392,32,27,32,951,88+350*r.top/63);
        }
        private void drawMessage(Canvas c)throws IOException{Dc2Ui.Quad bodyStyle=null,nameStyle=null;for(Dc2Ui.Quad q:host.ui.quadTemplates.get(host.messageMode==0?1:4))if(q.type==2&&q.width>2)bodyStyle=q;for(Dc2Ui.Quad q:host.ui.quadTemplates.get(host.messageMode==0?2:5))if(q.type==2&&q.width>2)nameStyle=q;
            if(nameStyle!=null&&!host.speaker.isEmpty())text(c,host.speaker,nameStyle.x,nameStyle.y,nameStyle.width,nameStyle.height,700);
            if(bodyStyle!=null){String b=host.body;if(host.presentationWaiting){int count=(int)Math.min(b.length(),Math.max(0,(SystemClock.uptimeMillis()-textAt)/textSpeed()));if(count>0&&count<b.length()&&Character.isHighSurrogate(b.charAt(count-1)))count--;b=b.substring(0,count);}text(c,b,bodyStyle.x,bodyStyle.y,bodyStyle.width,bodyStyle.height,(pcHost?stageWidth-40:952)-bodyStyle.x);}
        }
        private void text(Canvas c,String value,float x,float y,int lineHeight,int style,float width){paint.setTextSize(22);paint.setTypeface(Typeface.create("sans-serif",Typeface.NORMAL));paint.setColor(host.ui.palette[style&63]);paint.setAlpha(255);paint.setShadowLayer(1.5f,1,1,0xff50304a);float at=x,baseline=y+22;for(int n=0;n<value.length();){int cp=value.codePointAt(n);String ch=new String(Character.toChars(cp));n+=Character.charCount(cp);float w=paint.measureText(ch);if(cp==10||at+w>x+width){at=x;baseline+=lineHeight;if(cp==10)continue;}c.drawText(ch,at,baseline,paint);at+=w;}paint.clearShadowLayer();}
        @Override public boolean onTouchEvent(MotionEvent e){
            if(host==null||loading>0||failed)return true;float x=(e.getX()-ox)/scale,y=(e.getY()-oy)/scale;
            List<Dc2Ui.Button> bs=buttons();int hit=-1;for(int n=0;n<bs.size();n++){Dc2Ui.Button b=bs.get(n);if(!b.action.isEmpty()&&x>=b.left&&x<b.right&&y>=b.top&&y<b.bottom)hit=n;}
            if(e.getActionMasked()==MotionEvent.ACTION_DOWN){downX=x;downY=y;pressed=hit;invalidate();return true;}
            if(e.getActionMasked()==MotionEvent.ACTION_CANCEL){pressed=-1;invalidate();return true;}
            if(e.getActionMasked()!=MotionEvent.ACTION_UP)return true;
            try{
                if(host.history.active&&Math.abs(y-downY)>40){host.history.scroll(Math.round((downY-y)/100));host.historyButtons();}
                else if(host.musicRoom.active&&x>420&&Math.abs(y-downY)>30){host.musicRoom.scroll(Math.round((downY-y)/41));host.musicRoom.buttons(host.ui);}
                else if(host.history.active&&x>=944&&x<984&&y>=112&&y<444){host.history.cursor=Math.round((y-112)/332*Math.max(0,host.history.entries.size()-4));host.historyButtons();}
                else if(host.musicRoom.active&&x>=950&&y>=88&&y<476){host.musicRoom.top=Math.round((y-88)/388*63);host.musicRoom.buttons(host.ui);}
                else if(hidden)hidden=false;
                else if(hit>=0&&pressed==hit){
                    Dc2Ui.Button button=bs.get(hit);Dc2Ui.Quad range=slider(button);
                    if(range!=null){int value=Math.round(Math.max(0,Math.min(1,(x-range.x)/Math.max(1f,range.y-range.x)))*Math.max(0,range.sx-1));vm.variables.write(range.sy,range.width,value);}
                    if(host.gridWaiting&&button.action.startsWith("@slot:"))vm.activateGrid(host.waitTicket(),Integer.parseInt(button.action.substring(6)));
                    else if(host.ui.waiting)vm.activateMenu(host.waitTicket(),hit);
                    else {host.toolbarSound(button);action(button.action);}
                }else if(!host.ui.waiting){toolbar=false;if(pcHost&&((PcHost)host).hasTimedInput()&&!host.ui.actions[0].isEmpty())vm.activateMenu(host.waitTicket(),-2);else vm.signalHost(host.waitTicket(),MesVm.HostSignal.ADVANCE);}
                pressed=-1;wake();
            }catch(Exception ex){fail(ex);}return true;
        }
    }
}
