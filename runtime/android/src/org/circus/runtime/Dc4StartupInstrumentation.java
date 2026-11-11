package org.circus.runtime;

import android.app.Instrumentation;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import org.json.*;
import org.circus.core.*;
import org.circus.graphics.BufferedImage;

/** Explicit PH original-data ART/Canvas audit. Data comes from a private exact
 * RangeStorage pack; no library, shared save, SAF grant or Activity is modified.
 * This first-stage harness does not launch RuntimeActivity or synthesize touch. */
public final class Dc4StartupInstrumentation extends Instrumentation {
    private Bundle arguments;
    private int checks,backgroundFrames,kanaCodepoints;
    private long deadline;
    private Path output;
    private DesktopDd engine;
    private final List<String> failures=new ArrayList<>();
    private final Map<String,Object> report=new LinkedHashMap<>();
    private final List<Map<String,Object>> bodySteps=new ArrayList<>(),sounds=new ArrayList<>(),menuSteps=new ArrayList<>();
    private final Set<String> decodedSounds=new TreeSet<>(),backgrounds=new TreeSet<>();
    private String phase="initialization";
    private void check(boolean ok,String why){checks++;if(!ok)failures.add(why);}
    private void bounded()throws IOException{if(SystemClock.uptimeMillis()>deadline)throw new IOException("PH audit deadline during "+phase);}
    private static Set<String> actions(DesktopDd e){Set<String> result=new LinkedHashSet<>();for(DesktopEngine.Control c:e.controls())if(!c.action.isEmpty())result.add(c.action);return result;}
    private static byte[] macroState(DesktopDd e)throws IOException{ByteArrayOutputStream bytes=new ByteArrayOutputStream();((Dc4Host)e.host).phMacros.save(new DataOutputStream(bytes));return bytes.toByteArray();}
    private static String fileHash(Path path)throws Exception{
        java.security.MessageDigest hash=java.security.MessageDigest.getInstance("SHA-256");byte[] block=new byte[65536];
        try(InputStream input=Files.newInputStream(path)){for(int count;(count=input.read(block))!=-1;)hash.update(block,0,count);}
        return Binary.hex(hash.digest());
    }
    private static int recordedMacros(DesktopDd e){int n=0;for(int group=0;group<10;group++)n+=((Dc4Host)e.host).phMacros.recordedCount(group);return n;}
    private static String frameHash(BufferedImage image)throws IOException{
        int[] pixels=image.getRGB(0,0,image.getWidth(),image.getHeight(),null,0,image.getWidth());byte[] bytes=new byte[pixels.length*4];
        for(int n=0;n<pixels.length;n++){int p=pixels[n],at=n*4;bytes[at]=(byte)(p>>>24);bytes[at+1]=(byte)(p>>>16);bytes[at+2]=(byte)(p>>>8);bytes[at+3]=(byte)p;}return Binary.sha256(bytes);
    }
    private void frame(String name)throws Exception{
        bounded();BufferedImage image=engine.render();
        try{
            check(image.getWidth()==1280&&image.getHeight()==720,"Native PH Canvas remains1280x720 at "+name);
            int nonblack=0;for(int y=0;y<720;y+=12)for(int x=0;x<1280;x+=12)if((image.getRGB(x,y)&0xffffff)!=0)nonblack++;
            if(name.equals("title"))check(nonblack>1000,"Original PH title visibly occupies the Canvas");
            if(name.equals("title")||name.equals("body-01")||name.equals("body-20")){
                try(OutputStream png=Files.newOutputStream(output.resolve(name+".png"))){org.circus.graphics.ImageIO.write(image,"png",png);}
            }
            String background=engine.host.scene.published.layers[1].resource;
            if(!background.isEmpty()&&!background.equals("@ui-capture")){
                Crx.Image raw=engine.resources.image(background);backgrounds.add(background);backgroundFrames++;
                check(raw.width>0&&raw.height>0,"Explicit original PH background decodes at "+name);
            }
            check(engine.missingImages.isEmpty(),"Requested PH images exist at "+name);
        }finally{image.flush();}
    }
    private void audio()throws Exception{
        for(int channel=0;channel<6;channel++){
            String source=engine.audio(channel);
            if(engine.audioActive(channel)&&!source.isEmpty()&&decodedSounds.add(source)){
                Xpcm sound=engine.resources.audio(source);
                check(sound.payload.length>0&&(sound.codec.equals("ogg")||sound.codec.equals("pcm")),"Original PH audio container/PCM transform succeeds");
                sounds.add(Json.obj("resource",source,"channel",channel,"codec",sound.codec,"payload_bytes",sound.payload.length,"sample_rate",sound.sampleRate,"channels",sound.channels,"bits",sound.bits,"payload_sha256",Binary.sha256(sound.payload)));
            }
            // Core flow uses controlled media completion. Actual output has an
            // optional separately labeled MediaPlayer phase below.
            if(engine.audioActive(channel)&&!engine.audioLoop(channel))engine.audioCompleted(channel,engine.audioGeneration(channel));
        }
        if(!engine.movie().isEmpty())engine.mediaCompleted(engine.ticket());
    }
    private void settle(boolean story)throws Exception{
        for(int n=0;n<6000;n++){
            bounded();audio();engine.vm().signalHost(engine.ticket(),MesVm.HostSignal.TEXT_COMPLETE);engine.tick(1000);
            if(engine.textWaiting()&&!engine.body().isEmpty()){
                engine.vm().signalHost(engine.ticket(),MesVm.HostSignal.TEXT_COMPLETE);
                if(story)return;throw new IOException("Unexpected PH story before title/start action");
            }
            if(engine.menuWaiting()){
                // PH78 can publish a waiting menu at the first animation tick,
                // while its buttons still start outside the1280x720 viewport.
                // Advance the real UI owner before enumerating fresh controls.
                engine.tick(400);bounded();
                if(engine.menuWaiting())return;
            }
            if(engine.halted())throw new IOException("PH script halted before expected presentation");
            if(engine.host.remainingMillis()<0)engine.vm().signalHost(engine.ticket(),MesVm.HostSignal.ADVANCE);
        }throw new IOException("PH script did not reach a stable presentation");
    }
    private void chooseVisible(String action)throws Exception{
        DesktopEngine.Control chosen=null;for(DesktopEngine.Control control:engine.controls())if(control.action.equals(action)){chosen=control;break;}
        if(chosen==null)throw new IOException("Original PH control disappeared after menu settle: "+action);
        int x=(chosen.left+chosen.right)/2,y=(chosen.top+chosen.bottom)/2;
        boolean visible=chosen.right>chosen.left&&chosen.bottom>chosen.top&&x>=0&&x<1280&&y>=0&&y<720;
        check(visible,"Original PH control center lies inside the settled Canvas: "+action);
        if(!visible)throw new IOException("Off-screen original PH control "+action+" at "+x+","+y);
        menuSteps.add(Json.obj("script",engine.scriptName(),"pc",engine.scriptOffset(),"ticket",engine.ticket(),"action",action,"bounds",new int[]{chosen.left,chosen.top,chosen.right,chosen.bottom},"pointer",new int[]{x,y},"settled_ms",400));
        engine.pointerDown(x,y);engine.pointerUp(x,y);
    }
    private void titleAndStart()throws Exception{
        phase="title";boolean title=false;
        for(int menus=0;menus<16;menus++){
            settle(false);Set<String> available=actions(engine);
            if(available.contains("start")||available.contains("sce_start")){
                check(!title,"Natural original title reached once");title=true;frame("title");report.put("title_actions",available);
            }
            String chosen=null;for(String candidate:new String[]{"window","03","02","sce_start","start","menu01","sel01","sceselect01"})if(available.contains(candidate)){chosen=candidate;break;}
            if(chosen==null)throw new IOException("Unresolved original PH startup controls "+available);
            chooseVisible(chosen);
            try{settle(true);}catch(Exception failure){throw new IOException("After original action "+chosen+": "+failure,failure);}
            if(engine.textWaiting()){
                check(title,"Natural title/start selection precedes story");return;
            }
        }throw new IOException("Too many original menus before PH story");
    }
    private Map<String,Object> position()throws IOException{
        int macros=recordedMacros(engine),pending=((Dc4Host)engine.host).phMacros.pendingCount();
        return Json.obj("script",engine.scriptName(),"pc",engine.scriptOffset(),"body_sha256",Binary.sha256(engine.body().getBytes("UTF-8")),"macro_sha256",Binary.sha256(macroState(engine)),"recorded_macro_commands",macros,"pending_macro_events",pending);
    }
    private void body(int index,JSONArray expected)throws Exception{
        phase="body-"+index;Map<String,Object> state=position();bodySteps.add(state);
        for(int n=0;n<engine.body().length();n++){char c=engine.body().charAt(n);if(c>=0x3040&&c<=0x30ff)kanaCodepoints++;}
        check(!engine.body().contains("\ufffd"),"Strict CP932 story has no replacement characters at wait "+index);
        if(expected!=null){
            JSONObject actual=expected.getJSONObject(index-1);
            check(actual.getString("script").equals(engine.scriptName())&&actual.getInt("pc")==engine.scriptOffset()&&actual.getString("body_sha256").equals(state.get("body_sha256")),"ART story matches separately recorded JVM script/PC/CP932 body at wait "+index);
        }
        frame(String.format(Locale.ROOT,"body-%02d",index));audio();
    }
    private void saveRestore()throws Exception{
        phase="portable save/restore";String body=engine.body(),script=engine.scriptName();int pc=engine.scriptOffset();byte[] macros=macroState(engine);
        engine.playbackMillis(100000);BufferedImage before=engine.render();String beforeHash;try{beforeHash=frameHash(before);}finally{before.flush();}
        Path save=output.resolve("story.sav");engine.save(save);engine.restore(save);
        check(script.equals(engine.scriptName())&&pc==engine.scriptOffset()&&body.equals(engine.body()),"Portable file save/restore retains PH script/PC/body");
        check(Arrays.equals(macros,macroState(engine)),"Portable file save/restore retains PH recording/replay scheduler state");
        BufferedImage after=engine.render();try{check(beforeHash.equals(frameHash(after)),"Portable file restore retains exact same-platform Canvas pixels");}finally{after.flush();}
        byte[] snapshot=engine.snapshot();engine.tick(35);String future=Binary.sha256(macroState(engine));BufferedImage a=engine.render();String pixels;try{pixels=frameHash(a);}finally{a.flush();}
        engine.restore(snapshot);engine.tick(35);BufferedImage b=engine.render();try{check(future.equals(Binary.sha256(macroState(engine)))&&pixels.equals(frameHash(b)),"PH restored macro clock produces the same35ms future state and Canvas");}finally{b.flush();}
        engine.restore(snapshot);report.put("portable_save",Json.obj("file",save.toString(),"bytes",Files.size(save),"sha256",Binary.sha256(Files.readAllBytes(save)),"macro_commands_at_save",recordedMacros(engine),"pending_macro_events_at_save",((Dc4Host)engine.host).phMacros.pendingCount(),"original_Windows_story_import_tested",false));
    }
    private Map<String,Object> media()throws Exception{
        phase="optional direct MediaPlayer";if(sounds.isEmpty())throw new IOException("No original audio source collected");
        Map<String,Object> chosen=sounds.get(0);for(Map<String,Object> candidate:sounds)if(((Number)candidate.get("channel")).intValue()==0){chosen=candidate;break;}
        String resource=(String)chosen.get("resource");Xpcm sound=engine.resources.audio(resource);Path path=output.resolve(sound.codec.equals("ogg")?"media.ogg":"media.wav");try(OutputStream stream=Files.newOutputStream(path)){sound.writeTo(stream);}
        final MediaPlayer[] player=new MediaPlayer[1];final Throwable[] error=new Throwable[1];CountDownLatch ready=new CountDownLatch(1);Handler main=new Handler(Looper.getMainLooper());
        try{
            main.post(()->{try{MediaPlayer p=new MediaPlayer();player[0]=p;p.setOnPreparedListener(value->{try{value.start();}catch(Throwable failure){error[0]=failure;}finally{ready.countDown();}});p.setOnErrorListener((value,what,extra)->{error[0]=new IOException("MediaPlayer "+what+"/"+extra);ready.countDown();return true;});p.setDataSource(path.toString());p.prepareAsync();}catch(Throwable failure){error[0]=failure;ready.countDown();}});
            if(!ready.await(20,TimeUnit.SECONDS))throw new IOException("MediaPlayer prepare deadline");if(error[0]!=null)throw new IOException("Direct media smoke",error[0]);
            Thread.sleep(500);final int[] values=new int[3];CountDownLatch sampled=new CountDownLatch(1);
            main.post(()->{try{values[0]=player[0].isPlaying()?1:0;values[1]=player[0].getCurrentPosition();values[2]=player[0].getDuration();}catch(Throwable failure){error[0]=failure;}finally{sampled.countDown();}});
            if(!sampled.await(10,TimeUnit.SECONDS)||error[0]!=null)throw new IOException("MediaPlayer sample deadline/failure",error[0]);
            check(values[0]==1&&values[1]>0&&values[2]>0,"Actual Android MediaPlayer prepares and progresses original audio");
            return Json.obj("resource",resource,"playing",values[0]==1,"position_ms",values[1],"duration_ms",values[2],"scope","Direct Android MediaPlayer decoder/output probe; not RuntimeActivity media dispatch","audible_output_heard_verified",false);
        }finally{CountDownLatch released=new CountDownLatch(1);main.post(()->{try{if(player[0]!=null)player[0].release();}finally{released.countDown();}});if(!released.await(10,TimeUnit.SECONDS))throw new IOException("MediaPlayer release deadline");}
    }
    private Path privateFile(String name)throws IOException{
        Path root=getTargetContext().getFilesDir().getCanonicalFile().toPath(),path=root.resolve(Binary.safePath(name)).toRealPath();
        if(!path.startsWith(root))throw new IOException("PH audit input outside application files");return path;
    }
    @Override public void onCreate(Bundle args){super.onCreate(args);arguments=args;start();}
    @Override public void onStart(){
        Bundle result=new Bundle();String oldSaves=System.getProperty("dc.saveRoot"),oldCache=System.getProperty("dc.cacheRoot");
        try{
            deadline=SystemClock.uptimeMillis()+240000;output=Files.createTempDirectory(getTargetContext().getFilesDir().toPath(),"dc4-startup-audit-");
            System.setProperty("dc.saveRoot",output.resolve("saves").toString());System.setProperty("dc.cacheRoot",output.resolve("cache").toString());
            String pack=arguments==null?null:arguments.getString("range_pack"),exe=arguments==null?null:arguments.getString("executable"),expect=arguments==null?null:arguments.getString("expectation");
            if(pack==null)pack="dc4-startup-original.pack";if(exe==null)exe="DC4PHDL.exe";
            GameResources files=new GameResources(RangeStorage.replay(privateFile(pack)),exe);Profile profile=GameVersion.detect(files).profile();
            check(profile.id.equals("dc4ph-abb6"),"Original source independently identifies PH ABB6");if(!profile.id.equals("dc4ph-abb6"))throw new IOException("Wrong PH audit profile");
            CircusResources resources=new CircusResources(files,profile);TextCodec codec=GameText.select(files,profile,"auto","@none");check(codec.charset.equals(Binary.SJIS),"Automatic original PH prose codec is CP932");ExecutableData.validate(resources,codec);
            JSONArray expected=null;if(expect!=null){JSONObject manifest=new JSONObject(new String(Files.readAllBytes(privateFile(expect)),"UTF-8"));expected=manifest.getJSONArray("body_steps");check(manifest.getString("profile").equals(profile.id),"Fixture expectation profile matches");}
            report.put("scope","Production DesktopDd on ART and Android Canvas with exact original range bytes; original controls selected through shared engine, not Android touch/RuntimeActivity; media/time completion controlled; all outputs isolated");
            report.put("original_game_executed",false);report.put("library_or_shared_saves_written",false);report.put("input_kind","app-private exact-original-range-pack");report.put("expectation_kind",expected==null?"not supplied":"separate JVM script/PC/CP932 body hashes");report.put("profile",profile.id);report.put("pack_sha256",fileHash(privateFile(pack)));
            engine=new DesktopDd(resources,codec,true);check(engine.width==1280&&engine.height==720,"Production PH backend selects1280x720");titleAndStart();
            for(int n=1;n<=20;n++){body(n,expected);if(n<20){engine.vm().signalHost(engine.ticket(),MesVm.HostSignal.ADVANCE);settle(true);if(engine.menuWaiting())throw new IOException("Unexpected story choice before wait20: "+actions(engine));}}
            check(kanaCodepoints>0,"Original early story contains correctly admitted Japanese kana");check(backgroundFrames>0&&!backgrounds.isEmpty(),"Early story actually loads original backgrounds");check(!sounds.isEmpty(),"Early flow reads original audio containers/PCM transforms");saveRestore();
            if(arguments!=null&&"true".equals(arguments.getString("play_media")))report.put("direct_platform_media",media());
        }catch(Throwable failure){failures.add(phase+": "+failure);report.put("failure_class",failure.getClass().getName());if(engine!=null)report.put("failure_position",Json.obj("script",engine.scriptName(),"pc",engine.scriptOffset()));}
        finally{
            if(oldSaves==null)System.clearProperty("dc.saveRoot");else System.setProperty("dc.saveRoot",oldSaves);if(oldCache==null)System.clearProperty("dc.cacheRoot");else System.setProperty("dc.cacheRoot",oldCache);
            report.put("checks",checks);report.put("failures",failures);report.put("pass",failures.isEmpty());report.put("menu_steps",menuSteps);report.put("body_steps",bodySteps);report.put("audio",sounds);report.put("backgrounds",backgrounds);report.put("output",output==null?"":output.toString());
            try{if(output!=null)Files.write(output.resolve("report.json"),(Json.text(report)+"\n").getBytes("UTF-8"));}catch(IOException failure){failures.add("Writing audit report: "+failure);report.put("pass",false);}
            result.putString("stream","REPORT "+Json.text(report)+"\n");finish(failures.isEmpty()?-1:0,result);
        }
    }
}
