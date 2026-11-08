package org.circus.runtime;

import android.app.Instrumentation;
import android.content.Intent;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.circus.core.*;
import org.circus.graphics.BufferedImage;
import org.circus.graphics.Graphics2D;
import org.circus.graphics.Font;
import org.circus.graphics.Color;
import org.circus.graphics.RenderingHints;

/** Explicit original-data display/media audit. Original bytes are supplied by a
 * private exact-range pack or an explicit read-only SAF tree, never APK assets. */
public final class TyMusicDisplayInstrumentation extends Instrumentation {
    private static final String EXE="dc2ty_chinese(simplified)_patch.exe",BODY="7b3cea08d6a56f7ad54b63db10ae7e98c5d5c35ccf038e5814998b16512da933";
    private Bundle args;private RuntimeActivity activity;private int checks;private final List<String> failures=new ArrayList<>();
    private void check(boolean value,String reason){checks++;if(!value)failures.add(reason);}
    private static Set<String> actions(DesktopLegacy e){Set<String> a=new LinkedHashSet<>();for(DesktopEngine.Control c:e.controls())if(!c.action.isEmpty())a.add(c.action);return a;}
    private static void settle(DesktopLegacy e,boolean story)throws Exception{
        for(int n=0;n<10000;n++){e.tick(1000);Dc2Host h=e.host();if(e.menuWaiting())return;
            if(h.presentationWaiting){e.vm().signalHost(e.ticket(),MesVm.HostSignal.TEXT_COMPLETE);continue;}
            if(h.textWaiting&&!h.body.isEmpty()){if(story)return;throw new IOException("Unexpected story before selected TY action");}
            for(int c=0;c<6;c++)if(e.audioActive(c)&&!e.audioLoop(c))e.audioCompleted(c,e.audioGeneration(c));
            if(!e.movie().isEmpty())e.vm().signalHost(e.ticket(),MesVm.HostSignal.MEDIA_COMPLETE);if(h.remainingMillis()<0)e.vm().signalHost(e.ticket(),MesVm.HostSignal.ADVANCE);
        }throw new IOException("TY audit failed to settle");
    }
    private static void choose(DesktopLegacy e,String action,boolean story)throws Exception{if(!actions(e).contains(action))throw new IOException("Missing original TY action "+action+actions(e));e.choose(action);settle(e,story);}
    private DesktopLegacy title(CircusResources data,TextCodec codec)throws Exception{
        DesktopLegacy e=new DesktopLegacy(data,codec,true);settle(e,false);if(actions(e).contains("02"))choose(e,"02",false);else if(actions(e).contains("03"))choose(e,"03",false);check(actions(e).contains("start")&&actions(e).contains("extra"),"Original TY title reached");for(int n=0;n<1024;n++)e.host().write(98,n,1);return e;
    }
    private static void png(DesktopLegacy e,Path file)throws Exception{BufferedImage image=e.render();try(OutputStream out=Files.newOutputStream(file)){org.circus.graphics.ImageIO.write(image,"png",out);}finally{image.flush();}}
    private Map<String,Object> replay(CircusResources data,TextCodec codec,Path output)throws Exception{
        DesktopLegacy e=title(data,codec);choose(e,"extra",false);choose(e,"scene",false);PcHost h=(PcHost)e.host();check(e.vm().scriptName().equals("sce_hyd")&&h.list.items.size()==10,"Natural TY SceneReplay first page has ten original entries");
        MesScript original=data.scripts().script(e.vm().scriptName());Set<String> expected=new HashSet<>();for(MesScript.Record r:original.records())if(r.payloadOwner>=0&&data.profile.operation(original.at(r.payloadOwner).opcode).equals("pc.grid.caption"))expected.add(TextCodec.cp932(r.bytes));
        int total=0,matched=0;for(ScriptList.Item item:h.list.items)for(Dc2Ui.Quad q:item.sprites)if(q.type==2){total++;if(expected.contains(q.text))matched++;}
        check(total==20&&matched==20,"All ten normal/hover SceneReplay captions match independently decoded native CP932 fields");png(e,output.resolve("scene-replay.png"));
        byte[] snapshot=e.snapshot();String script=e.vm().scriptName();int pc=e.vm().offset();e.restore(snapshot);check(script.equals(e.vm().scriptName())&&pc==e.vm().offset()&&Arrays.equals(snapshot,e.snapshot()),"Replay list snapshot retains VM position and exact field text");
        for(int n=0;n<3;n++)choose(e,"next",false);choose(e,"@slot:9",true);check(e.vm().scriptName().equals("hyd_b_028_pst")&&e.vm().offset()==0x1f6e&&Binary.sha256(e.body().getBytes("UTF-8")).equals(BODY),"Natural replay entry39 reaches exact reported GBK dialogue and original07 wait");png(e,output.resolve("reported-gbk-body.png"));
        return Json.obj("script",script,"pc",pc,"visible_text_layers",total,"native_CP932_matches",matched,"body_script",e.vm().scriptName(),"body_sha256",Binary.sha256(e.body().getBytes("UTF-8")),"isolated_synthetic_scene_flags",true);
    }
    private Map<String,Object> bodyCodec(CircusResources data,TextCodec codec)throws Exception{
        MesScript script=data.scripts().script("hyd_b_028_pst");MesScript.Record record=script.at(0x1f2c);byte[] raw=script.nativeText(record);String body=codec.decode(raw);boolean rejected=false;try{TextCodec.identity().decode(raw);}catch(IOException expected){rejected=true;}
        check(record.opcode==0x4c&&raw.length==61,"Exact reported original native dialogue record");check(Binary.sha256(body.getBytes("UTF-8")).equals(BODY),"Original record decodes to reviewed GBK body identity");check(rejected,"Forcing Japanese rejects this Chinese dialogue instead of changing its encoding");
        return Json.obj("script_sha256",script.sha256,"record_offset",record.offset,"native_bytes",raw.length,"GBK_body_sha256",Binary.sha256(body.getBytes("UTF-8")),"Japanese_rejected",rejected);
    }
    private Map<String,Object> music(CircusResources data,TextCodec codec,Path output)throws Exception{
        DesktopLegacy e=title(data,codec);choose(e,"extra",false);String error="";try{choose(e,"bgm",false);}catch(IOException missing){error=missing.toString();}
        check(error.isEmpty()&&e.vm().scriptName().equals("music")&&e.menuWaiting(),"Original MusicMode enters without unimplemented service9");Map<String,Object> report=Json.obj("error",error,"script",e.vm().scriptName(),"pc",e.vm().offset(),"actions",actions(e));if(!error.isEmpty())return report;
        report.put("entry_title",bakedTitle(e,data));png(e,output.resolve("music-entry.png"));
        choose(e,"music0",false);check(e.audioActive(0)&&!e.audioLoop(0)&&e.audio(0).equals(data.named("music",1)),"First native MusicMode row starts original single-play track1");report.put("first_track",e.audio(0));report.put("first_title",bakedTitle(e,data));
        choose(e,"music1",false);check(e.audioActive(0)&&!e.audioLoop(0)&&e.audio(0).equals(data.named("music",3)),"Second native row changes single playback to original track3");report.put("second_track",e.audio(0));report.put("second_title",bakedTitle(e,data));png(e,output.resolve("music-second-track.png"));
        byte[] snapshot=e.snapshot();e.restore(snapshot);check(Arrays.equals(snapshot,e.snapshot()),"MusicMode baked title and VM state survive exact snapshot roundtrip");check(e.audioActive(0)&&e.audio(0).equals(data.named("music",3)),"Restored MusicMode preserves selected original playback resource");bakedTitle(e,data);
        choose(e,"rplay",false);check(e.audioActive(0)&&e.audioLoop(0)&&e.audio(0).equals(data.named("music",3)),"Native rplay repeats the current original track");choose(e,"play",false);check(e.audioActive(0)&&!e.audioLoop(0),"Native play restores single-play mode");choose(e,"stop",false);check(!e.audioActive(0),"Original MusicMode stop deactivates channel0");choose(e,"return",false);check(e.vm().scriptName().equals("main")&&actions(e).contains("bgm"),"Original MusicMode returns to actual extra menu");return report;
    }
    /** Independent PE section mapping and raw cell decoding; not TyMusicNames. */
    private String originalTitle(CircusResources data,int index)throws Exception{
        byte[] pe=data.files.read(data.files.executable,4*1024*1024);String hash=Binary.sha256(pe);String encoding;
        if(hash.equals("615704413dd00960bb47f4d6c1b33dd635931f13085a9a25dd5782fd7dbdf505"))encoding="GBK";
        else if(hash.equals("70d118b8e7d79011d0e7443d488897ed03ae184ebc83db8abde3af007aab328e")||hash.equals("21988ca3227d494ad8fd816fd6363670b1a6245f82ac28314c4bb4e8353d19bd"))encoding="windows-31j";else throw new IOException("Unreviewed TY title module");
        int nt=(int)Binary.u32(pe,0x3c),optional=nt+24,sections=Binary.u16(pe,nt+6),table=optional+Binary.u16(pe,nt+20);long rva=0x4feb98L+260L*index-Binary.u32(pe,optional+28);int offset=-1;
        if(index<0||index>=48||Binary.u16(pe,optional)!=0x10b)throw new IOException("Native music title cell outside reviewed PE");
        for(int n=0;n<sections;n++){int at=table+n*40;long va=Binary.u32(pe,at+12),bytes=Binary.u32(pe,at+16);if(rva>=va&&rva+260<=va+bytes){offset=(int)(Binary.u32(pe,at+20)+rva-va);break;}}
        if(offset<0)throw new IOException("Native music title has no file-backed section");int end=Binary.endZ(pe,offset);if(end-offset>=260)throw new IOException("Native music title cell unterminated");return new TextCodec("","",encoding).decode(Arrays.copyOfRange(pe,offset,end));
    }
    private Map<String,Object> bakedTitle(DesktopLegacy e,CircusResources data)throws Exception{
        PcHost h=(PcHost)e.host();int index=e.vm().variables.read(3,5);String expected=originalTitle(data,index);Object catalog=h.getClass().getMethod("musicNames").invoke(h);String actual=(String)catalog.getClass().getMethod("text",int.class).invoke(catalog,index);check(actual.equals(expected),"Music name matches original module bytes decoded independently of prose codec");
        check(h.uiCapture!=null&&h.uiCapture.kind==UiCapture.ARGB&&h.scene.slots[1].equals("@ui-capture"),"Music title is an immutable native background raster");UiCapture.Pixels captured=h.uiCapture.pixels();Crx.Image original=data.image("system/music_back");BufferedImage oracle=new BufferedImage(800,600,BufferedImage.TYPE_INT_ARGB);oracle.setRGB(0,0,800,600,original.argb(true),0,800);Graphics2D g=oracle.createGraphics();int[] pixels;
        try{g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,RenderingHints.VALUE_TEXT_ANTIALIAS_ON);g.setFont(new Font("Microsoft YaHei",Font.PLAIN,16));g.setColor(new Color(h.ui.palette[15],true));g.drawString(expected,81,115);g.setColor(new Color(h.ui.palette[7],true));g.drawString(expected,80,114);pixels=oracle.getRGB(0,0,800,600,null,0,800);}finally{g.dispose();oracle.flush();}
        check(e.vm().variables.read(3,31)==1&&captured.width==800&&captured.height==600&&Arrays.equals(pixels,captured.argb),"Native background title pixels match independent PE string and reviewed16px shadow/main positions");
        return Json.obj("index",index,"text_utf8_sha256",Binary.sha256(expected.getBytes("UTF-8")),"baked_pixels_compared",pixels.length,"source","independent native PE cell; no prose exported");
    }
    private interface EngineJob<T>{T run(DesktopEngine engine)throws Exception;}
    private <T>T call(EngineJob<T> job)throws Exception{if(activity.failed)throw new IOException(activity.error);return activity.worker.submit(()->{if(activity.engine==null)throw new IOException("Runtime engine not ready");return job.run(activity.engine);}).get(60,TimeUnit.SECONDS);}
    private Set<String> activityActions()throws Exception{return call(e->actions((DesktopLegacy)e));}
    private void waitAction(String action)throws Exception{long end=SystemClock.uptimeMillis()+60000;while(SystemClock.uptimeMillis()<end){if(activity.failed)throw new IOException(activity.error);if(activity.engine!=null&&activityActions().contains(action))return;Thread.sleep(100);}throw new IOException("Activity original control did not appear: "+action);}
    private void tap(String action)throws Exception{
        waitAction(action);float[] point=call(e->{for(DesktopEngine.Control c:e.controls())if(c.action.equals(action))return new float[]{(c.left+c.right)/2f/e.width,(c.top+c.bottom)/2f/e.height};throw new IOException("Original control disappeared: "+action);});float[] screen=new float[2];runOnMainSync(()->{View v=activity.stage;DesktopEngine e=activity.engine;float scale=Math.min(v.getWidth()/(float)e.width,v.getHeight()/(float)e.height);int[] at=new int[2];v.getLocationOnScreen(at);screen[0]=at[0]+(v.getWidth()-e.width*scale)/2+point[0]*e.width*scale;screen[1]=at[1]+(v.getHeight()-e.height*scale)/2+point[1]*e.height*scale;});long down=SystemClock.uptimeMillis();MotionEvent event=MotionEvent.obtain(down,down,MotionEvent.ACTION_DOWN,screen[0],screen[1],0);sendPointerSync(event);event.recycle();Thread.sleep(70);event=MotionEvent.obtain(down,SystemClock.uptimeMillis(),MotionEvent.ACTION_UP,screen[0],screen[1],0);sendPointerSync(event);event.recycle();Thread.sleep(200);
    }
    private Map<String,Object> playerState()throws Exception{return call(e->{java.lang.reflect.Field players=RuntimeActivity.class.getDeclaredField("players"),prepared=RuntimeActivity.class.getDeclaredField("prepared"),heard=RuntimeActivity.class.getDeclaredField("heard");players.setAccessible(true);prepared.setAccessible(true);heard.setAccessible(true);MediaPlayer p=((MediaPlayer[])players.get(activity))[0];boolean ready=((boolean[])prepared.get(activity))[0],playing=p!=null&&ready&&p.isPlaying();return Json.obj("resource",e.audio(0),"active",e.audioActive(0),"generation",e.audioGeneration(0),"platform_generation",((int[])heard.get(activity))[0],"player_present",p!=null,"prepared",ready,"is_playing",playing,"position_ms",p!=null&&ready?p.getCurrentPosition():-1,"duration_ms",p!=null&&ready?p.getDuration():-1,"player_identity",p==null?0:System.identityHashCode(p));});}
    private Map<String,Object> waitPlayer(String resource)throws Exception{long end=SystemClock.uptimeMillis()+60000;Map<String,Object> state=null;while(SystemClock.uptimeMillis()<end){state=playerState();if(resource.equals(state.get("resource"))&&state.get("generation").equals(state.get("platform_generation"))&&Boolean.TRUE.equals(state.get("prepared"))&&Boolean.TRUE.equals(state.get("is_playing"))&&((Number)state.get("duration_ms")).intValue()>0)return state;Thread.sleep(100);}throw new IOException("MediaPlayer failed to play selected original track: "+state);}
    private void closeActivity()throws Exception{if(activity==null)return;RuntimeActivity closing=activity;runOnMainSync(()->closing.finish());waitForIdleSync();if(!closing.worker.awaitTermination(20,TimeUnit.SECONDS))throw new IOException("Audit Activity worker did not stop");activity=null;}
    private Map<String,Object> mediaSmoke(String tree,String pack,String executable)throws Exception{
        Intent launch=new Intent(getTargetContext(),RuntimeActivity.class).putExtra("audit",true).putExtra("executable",executable).putExtra("encoding","auto").putExtra("configuration","@none").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);if(tree==null)launch.putExtra("range_pack",pack);else launch.putExtra("tree",tree);activity=(RuntimeActivity)startActivitySync(launch);long end=SystemClock.uptimeMillis()+120000;boolean initialized=false;
        while(SystemClock.uptimeMillis()<end){if(activity.failed)throw new IOException(activity.error);if(activity.engine!=null){Set<String> controls=activityActions();if(controls.contains("02")){tap("02");}else if(controls.contains("start")){initialized=true;break;}}Thread.sleep(100);}if(!initialized)throw new IOException("Activity did not reach original TY title");check(call(e->e.resources.profileId.equals("dc2ty-5f69")),"Shipping RuntimeActivity selects actual TY engine and automatic text codec");tap("extra");tap("bgm");waitAction("music0");tap("music0");String first=call(e->e.resources.named("music",1));Map<String,Object> played1=waitPlayer(first);check(true,"Real platform MediaPlayer prepares and plays original first selected track");tap("music1");String second=call(e->e.resources.named("music",3));Map<String,Object> played2=waitPlayer(second);check(!played1.get("player_identity").equals(played2.get("player_identity"))&&((Number)played2.get("generation")).intValue()>((Number)played1.get("generation")).intValue(),"Real platform replaces player and resource when original menu switches track");tap("stop");end=SystemClock.uptimeMillis()+10000;Map<String,Object> stopped;
        do{stopped=playerState();if(Boolean.FALSE.equals(stopped.get("active"))&&Boolean.FALSE.equals(stopped.get("player_present")))break;Thread.sleep(100);}while(SystemClock.uptimeMillis()<end);check(Boolean.FALSE.equals(stopped.get("active"))&&Boolean.FALSE.equals(stopped.get("player_present")),"Original stop releases actual platform player");tap("return");waitAction("bgm");check(call(e->e.scriptName().equals("main")),"Android touch returns from MusicMode to original extra menu");Map<String,Object> report=Json.obj("first",played1,"second",played2,"stopped",stopped,"input","Real Android touch events on original script controls","audible_output_heard_verified",false,"audit_activity",true);closeActivity();return report;
    }
    private Map<String,Object> run(GameResources.Storage storage,String executable,Path output)throws Exception{
        checks=0;failures.clear();Files.createDirectories(output);System.setProperty("dc.saveRoot",output.resolve("saves").toString());System.setProperty("dc.cacheRoot",output.resolve("cache").toString());GameResources files=new GameResources(storage,executable);Profile profile=GameVersion.detect(files).profile();check(profile.id.equals("dc2ty-5f69"),"Actual original source independently identifies TY");CircusResources data=new CircusResources(files,profile);TextCodec auto=GameText.select(files,profile,"auto","@none"),gbk=GameText.select(files,profile,"GBK","@none");check(auto.fingerprint.equals(gbk.fingerprint),"Automatic encoding equals GBK on reviewed localized TY source");
        Map<String,Object> report=Json.obj("scope","Production engine/Canvas with read-only original TY range bytes or SAF, no original EXE execution; engine audit simulates media completion and uses isolated synthetic scene flags/output");report.put("body_codec",bodyCodec(data,gbk));report.put("music",music(data,gbk,output));System.gc();report.put("replay",replay(data,gbk,output));report.put("checks",checks);report.put("failures",new ArrayList<>(failures));report.put("pass",failures.isEmpty());Files.write(output.resolve("report.json"),(Json.text(report)+"\n").getBytes("UTF-8"));return report;
    }
    @Override public void onCreate(Bundle args){super.onCreate(args);this.args=args;start();}
    @Override public void onStart(){Bundle result=new Bundle();try{
        String tree=args==null?null:args.getString("tree_uri"),executable=args==null?null:args.getString("executable"),pack=args==null?null:args.getString("range_pack");if(executable==null)executable=EXE;if(pack==null)pack="ty-music-display-original-20261109-v2.pack";
        GameResources.Storage storage;if(tree==null){Path file=getTargetContext().getFilesDir().toPath().resolve(Binary.safePath(pack));if(!file.toRealPath().startsWith(getTargetContext().getFilesDir().getCanonicalFile().toPath()))throw new IOException("Audit pack outside app files");storage=RangeStorage.replay(file);}else storage=new DocumentTree(getTargetContext().getContentResolver(),android.net.Uri.parse(tree));
        Path output=Files.createTempDirectory(getTargetContext().getFilesDir().toPath(),"ty-music-display-audit-");Map<String,Object> report=run(storage,executable,output);if(args!=null&&"true".equals(args.getString("launch_media"))){try{report.put("platform_media",mediaSmoke(tree,pack,executable));}catch(Throwable error){check(false,"Platform MediaPlayer smoke: "+error);report.put("platform_media",Json.obj("error",error.toString()));}}report.put("source_kind",tree==null?"private-exact-original-range-pack":"explicit-readonly-SAF-tree");report.put("output",output.toString());report.put("checks",checks);report.put("failures",new ArrayList<>(failures));report.put("pass",failures.isEmpty());Files.write(output.resolve("report.json"),(Json.text(report)+"\n").getBytes("UTF-8"));result.putString("stream","REPORT "+Json.text(report)+"\n");finish(Boolean.TRUE.equals(report.get("pass"))?-1:0,result);
    }catch(Throwable failure){result.putString("stream","FAIL "+failure+"\n");finish(0,result);}finally{if(activity!=null)runOnMainSync(()->activity.finish());}}
}
