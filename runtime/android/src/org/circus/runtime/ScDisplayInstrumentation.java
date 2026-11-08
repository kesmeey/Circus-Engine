package org.circus.runtime;

import android.app.Instrumentation;
import android.os.Bundle;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import org.circus.core.*;
import org.circus.graphics.BufferedImage;

/** Explicit read-only original-data audit. No original game resources enter the APK.
 * Fixture and output are separate from the user's game library and shared saves. */
public final class ScDisplayInstrumentation extends Instrumentation {
    private Bundle args;
    private static final String BODY_SHA="419e807112dc3ffef639024d88b2ae7a58745527b66cadfba97eb665b5582d30";
    private static int checks;
    private static final List<String> failures=new ArrayList<>();
    private static void check(boolean value,String reason){checks++;if(!value)failures.add(reason);}
    private static Set<String> actions(DesktopLegacy e){Set<String> out=new LinkedHashSet<>();for(DesktopEngine.Control c:e.controls())if(!c.action.isEmpty())out.add(c.action);return out;}
    private static void settle(DesktopLegacy e,boolean story)throws Exception{
        for(int n=0;n<10000;n++){e.tick(1000);Dc2Host h=e.host();if(e.menuWaiting())return;
            if(h.presentationWaiting){e.vm().signalHost(e.ticket(),MesVm.HostSignal.TEXT_COMPLETE);continue;}
            if(h.textWaiting&&!h.body.isEmpty()){if(story)return;throw new IOException("Unexpected story before selected action");}
            for(int c=0;c<6;c++)if(e.audioActive(c)&&!e.audioLoop(c))e.audioCompleted(c,e.audioGeneration(c));
            if(!e.movie().isEmpty())e.vm().signalHost(e.ticket(),MesVm.HostSignal.MEDIA_COMPLETE);
            if(h.remainingMillis()<0)e.vm().signalHost(e.ticket(),MesVm.HostSignal.ADVANCE);
        }throw new IOException("SC display audit did not settle");
    }
    private static void choose(DesktopLegacy e,String action,boolean story)throws Exception{if(!actions(e).contains(action))throw new IOException("Original SC action absent: "+action);e.choose(action);settle(e,story);}
    private static void title(DesktopLegacy e)throws Exception{settle(e,false);if(actions(e).contains("03"))choose(e,"03",false);check(actions(e).contains("start")&&actions(e).contains("extra"),"Original SC title reached");}
    private static DesktopLegacy story(CircusResources data,TextCodec codec)throws Exception{
        DesktopLegacy e=new DesktopLegacy(data,codec,true);title(e);choose(e,"start",false);choose(e,"after_story",false);choose(e,"after_otm",false);choose(e,"read_after",true);
        for(int n=1;n<15;n++){e.vm().signalHost(e.ticket(),MesVm.HostSignal.ADVANCE);settle(e,true);}
        check(e.vm().scriptName().equals("aft_0314_a1_otm")&&e.vm().offset()==0x20ed,"Same Otome dialogue location as screenshot");
        check(Binary.sha256(e.body().getBytes("UTF-8")).equals(BODY_SHA),"Exact decoded GBK dialogue identity");return e;
    }
    private static Map<String,Object> frame(DesktopLegacy e,CircusResources data,Path out,String name)throws Exception{
        check(e.host().scene.shown.slots[1].replace('\\','/').equalsIgnoreCase("bg/bg060_d_no"),"Native story requests original BG");
        check(e.host().scene.shown.x==0&&e.host().scene.shown.y==0,"Native background origin is zero");
        Crx.Image bg=data.image("bg/bg060_d_no");BufferedImage image=e.render();int[] expected=bg.argb(true);int count=0,match=0,nonblack=0;
        try{for(int y=10;y<300;y+=10)for(int x=400;x<790;x+=10){int p=image.getRGB(x,y),q=expected[y*bg.width+x];if((q&0x00ffffff)==0)continue;count++;if((p&0xffffff)==(q&0xffffff))match++;if((p&0xffffff)!=0)nonblack++;}
            try(OutputStream png=Files.newOutputStream(out.resolve(name+".png"))){org.circus.graphics.ImageIO.write(image,"png",png);}
        }finally{image.flush();}
        check(count>900&&match*2>count,"Original BG pixels occupy the unobstructed top-right region");
        check(e.missingImages.isEmpty(),"Complete SC fixture has no missing requested images");
        return Json.obj("sample_points",count,"exact_original_BG_pixel_matches",match,"nonblack_samples",nonblack,"missing_images",e.missingImages,"loaded_images",e.loadedImages);
    }
    private static Map<String,Object> gallery(CircusResources data,TextCodec codec,Path out)throws Exception{
        DesktopLegacy e=new DesktopLegacy(data,codec,true);PcHost h=(PcHost)e.host();for(int n=0;n<1024;n++)h.write(98,n,1);title(e);choose(e,"extra",false);choose(e,"scene",false);
        check(e.vm().scriptName().equals("af_otm_00"),"Original SceneReplay first page reached with isolated unlock fixture");
        MesScript original=data.scripts().script(e.vm().scriptName());Set<String> expected=new TreeSet<>();
        for(MesScript.Record record:original.records())if(record.payloadOwner>=0&&data.profile.operation(original.at(record.payloadOwner).opcode).equals("ui.button_text")){
            byte[] raw=record.bytes;if(raw.length>2&&raw[0]=='@'&&raw[1]=='s')raw=Arrays.copyOfRange(raw,2,raw.length);String text=TextCodec.cp932(raw);if(text.matches("[0-9]{2}月[0-9]{2}日"))expected.add(text);
        }
        check(expected.size()==1,"Independent original51 date fields identify the shared date on this page");Set<String> visible=new TreeSet<>();int matched=0;
        for(Dc2Ui.Button b:h.ui.buttons)for(Dc2Ui.Quad q:b.sprites)if(q.type==2&&expected.contains(q.text)){visible.add(q.text);matched++;}
        check(visible.equals(expected)&&matched==24,"All eight SceneReplay rows and three original date text layers use native CP932 captions");
        BufferedImage image=e.render();try(OutputStream png=Files.newOutputStream(out.resolve("scene-replay.png"))){org.circus.graphics.ImageIO.write(image,"png",png);}finally{image.flush();}
        check(e.missingImages.isEmpty(),"Gallery fixture has every requested image");
        return Json.obj("script",e.vm().scriptName(),"pc",e.vm().offset(),"expected_distinct_date_labels",expected.size(),"matching_distinct_date_labels",visible.size(),"matching_native_text_layers",matched,"synthetic_unlocks_only",true);
    }
    private static Map<String,Object> missingBackground(final GameResources.Storage base,String executable,TextCodec codec,Path output)throws Exception{
        final Set<String> paths=new TreeSet<>();for(String p:base.paths())if(!p.replace('\\','/').toLowerCase(Locale.ROOT).endsWith("advdata/grp/bg/bg060_d_no.crx"))paths.add(p);
        GameResources files=new GameResources(new GameResources.Storage(){public Set<String> paths(){return paths;}public byte[] read(String p,int n)throws IOException{return base.read(p,n);}public Archive.Source source(String p)throws IOException{return base.source(p);}},executable);
        CircusResources data=new CircusResources(files,Profiles.get("dc2sc-3b69"));String failure="";Set<String> absent=new TreeSet<>();boolean silentlyRendered=false;
        try{DesktopLegacy e=story(data,codec);BufferedImage image=e.render();try(OutputStream png=Files.newOutputStream(output.resolve("missing-background.png"))){org.circus.graphics.ImageIO.write(image,"png",png);}finally{image.flush();}absent.addAll(e.missingImages);silentlyRendered=true;}
        catch(FileNotFoundException expected){failure=expected.getMessage();}
        check(!silentlyRendered&&failure.toLowerCase(Locale.ROOT).contains("advdata/grp/bg/bg060_d_no.crx"),"Missing SC background reports its concrete resource path instead of silently rendering black");
        return Json.obj("only_hidden_resource","AdvData/GRP/BG/bg060_d_no.CRX","silently_rendered",silentlyRendered,"failure",failure,"missing_images",absent,"original_fixture_untouched",true);
    }
    public static Map<String,Object> run(Path fixture,Path output)throws Exception{
        return runStorage(new LocalGameStorage(fixture),"dc2sc_CHS.exe",output);
    }
    private static Map<String,Object> runStorage(GameResources.Storage source,String executable,Path output)throws Exception{
        checks=0;failures.clear();Files.createDirectories(output);System.setProperty("dc.saveRoot",output.resolve("saves").toString());System.setProperty("dc.cacheRoot",output.resolve("cache").toString());
        GameResources files=new GameResources(source,executable);Profile profile=GameVersion.detect(files).profile();check(profile.id.equals("dc2sc-3b69"),"Actual fixture independently identifies SC");
        check(Binary.sha256(files.read("AdvData/MES/aft_0314_a1_otm.mes",2*1024*1024)).equals("d9448ae50a86cc176701ca115eeb1f1b98704d6dae059fd7015773e7f9b0223e"),"Reviewed original localized story bytes");
        CircusResources data=new CircusResources(files,profile);TextCodec auto=GameText.select(files,profile,"auto","@none"),gbk=GameText.select(files,profile,"GBK","@none");check(auto.fingerprint.equals(gbk.fingerprint)&&auto.charset.name().equalsIgnoreCase("GBK"),"Automatic and explicit GBK codecs agree on this original copy");
        Map<String,Object> report=Json.obj("scope","Original SC selected read-only files, production DesktopLegacy and Android Canvas; original EXE not executed; media completion simulated; isolated output/unlock flags only","profile",profile.id,"body_identity",BODY_SHA);
        report.put("otome_GBK",frame(story(data,gbk),data,output,"otome-gbk"));System.gc();report.put("otome_auto",frame(story(data,auto),data,output,"otome-auto"));System.gc();report.put("scene_replay",gallery(data,gbk,output));System.gc();report.put("missing_background",missingBackground(source,executable,gbk,output));report.put("checks",checks);report.put("failures",new ArrayList<>(failures));report.put("pass",failures.isEmpty());
        Files.write(output.resolve("report.json"),(Json.text(report)+"\n").getBytes("UTF-8"));return report;
    }
    /** The same harness can establish a counterfactual on an older immutable APK via app_process. */
    public static void main(String[] args)throws Exception{Map<String,Object> report=run(Paths.get(args[0]),Paths.get(args[1]));System.out.println(Json.text(report));}
    @Override public void onCreate(Bundle args){super.onCreate(args);this.args=args;start();}
    @Override public void onStart(){Bundle result=new Bundle();try{
        String chosen=args==null?null:args.getString("fixture_root"),tree=args==null?null:args.getString("tree_uri"),executable=args==null?null:args.getString("executable");Path fixture=chosen==null?getTargetContext().getFilesDir().toPath().resolve("sc-display-fixture-20261108/game"):Paths.get(chosen);Path output=Files.createTempDirectory(getTargetContext().getFilesDir().toPath(),"sc-display-audit-");
        Map<String,Object> report=tree==null?run(fixture,output):runStorage(new DocumentTree(getTargetContext().getContentResolver(),android.net.Uri.parse(tree)),executable==null?"dc2sc_CHS.exe":executable,output);report.put("source_kind",tree==null?"owned-original-fixture":"explicit-readonly-SAF-tree");report.put("output",output.toString());result.putString("stream","REPORT "+Json.text(report)+"\n");finish(Boolean.TRUE.equals(report.get("pass"))?-1:0,result);
    }catch(Throwable failure){result.putString("stream","FAIL "+failure+"\n");finish(0,result);}}
}
