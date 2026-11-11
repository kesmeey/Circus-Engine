package org.circus.core;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import java.util.List;
import javax.imageio.ImageIO;

/** Real PH START/menu/story input through DesktopRuntime; all writes are audit-owned. */
public final class Dc4DesktopAudit {
    private static int checks;
    private static void check(boolean value,String name)throws IOException{checks++;if(!value)throw new IOException(name);}
    private static String hash(String s){return Binary.sha256(s.getBytes(StandardCharsets.UTF_8));}
    private static String pixels(BufferedImage image){int[] p=image.getRGB(0,0,image.getWidth(),image.getHeight(),null,0,image.getWidth());ByteBuffer b=ByteBuffer.allocate(p.length*4);for(int x:p)b.putInt(x);return Binary.sha256(b.array());}
    private static List<String> actions(DesktopEngine e){List<String> a=new ArrayList<>();for(DesktopEngine.Control c:e.controls())if(!c.action.isEmpty()&&!a.contains(c.action))a.add(c.action);return a;}
    /** Independent Unicode grammar oracle, separate from Dc4Ruby's byte parser. */
    private static String rubyBase(String source,List<Object> spans)throws IOException{
        StringBuilder plain=new StringBuilder();int at=0;
        while(at<source.length()){
            int open=source.indexOf('\uff5b',at);if(open<0){plain.append(source.substring(at));break;}
            plain.append(source.substring(at,open));int slash=source.indexOf('\uff0f',open+1),close=source.indexOf('\uff5d',open+1);
            int nested=source.indexOf('\uff5b',open+1);check(slash>open+1&&close>slash+1&&(nested<0||nested>close),"Independent PH ruby delimiters");
            String note=source.substring(open+1,slash),body=source.substring(slash+1,close);byte[] original=source.substring(open,close+1).getBytes(Binary.SJIS);
            spans.add(Json.obj("native_byte_offset",source.substring(0,open).getBytes(Binary.SJIS).length,"native_bytes_hex",Binary.hex(original),"native_span_sha256",Binary.sha256(original),"annotation_bytes",note.getBytes(Binary.SJIS).length,"base_bytes",body.getBytes(Binary.SJIS).length,"base_sha256",hash(body)));
            plain.append(body);at=close+1;
        }return plain.toString();
    }
    private static Object frame(DesktopDd e,Path out,String name)throws Exception{BufferedImage b=e.render();try{check(b.getWidth()==1280&&b.getHeight()==720,"PH1280x720 production frame");ImageIO.write(b,"png",out.resolve(name+".png").toFile());List<Object> panels=new ArrayList<>();for(int i:new int[]{0,1,3,4,9,10}){Dc3Ui.Panel p=e.host.ui.templates[i];if(p==null)continue;List<Object> quads=new ArrayList<>();for(Dc3Scene.Quad q:p.quads)quads.add(Json.obj("resource",q.resource,"dest",new int[]{q.x,q.y,q.width,q.height},"source",new int[]{q.sx,q.sy}));List<Object> texts=new ArrayList<>();for(Dc3Ui.Text t:p.texts)texts.add(t.args);panels.add(Json.obj("index",i,"hidden",p.hidden,"origin",new int[]{p.x,p.y},"quads",quads,"text_args",texts));}return Json.obj("file",name+".png","pixel_sha256",pixels(b),"missing_images",new ArrayList<>(e.missingImages),"text_waiting",e.textWaiting(),"wait_reason",e.host.dialogue.waitReason(),"body_length",e.body().length(),"visible_body_length",e.visibleBody().length(),"hidden",e.hidden,"novel_mode",e.host.scene.novelMode,"templates",panels);}finally{b.flush();}}
    private static Object bodyOracle(DesktopDd e)throws Exception{
        MesScript source=e.resources.scripts().script(e.scriptName());MesScript.Record last=null;
        for(MesScript.Record r:source.records())if(r.offset<e.vm().offset()&&source.profile.operation(r.opcode).equals("text.body"))last=r;
        check(last!=null,"Stable text wait has an original native body source");byte[] raw=source.nativeText(last);String original=TextCodec.cp932(raw);
        List<Object> spans=new ArrayList<>();String flattened=rubyBase(original,spans);boolean plain=flattened.indexOf('@')<0;
        if(plain){String expected=flattened.replace("&f",e.host.dialogue.familyName).replace("&n",e.host.dialogue.givenName);check(e.body().equals(expected),"Independent CP932/ruby base body matches production text");}
        check(e.body().indexOf('\ufffd')<0,"PH displayed body has no Unicode replacement errors");
        return Json.obj("script",source.name,"source_sha256",source.sha256,"source_file_pc",last.offset,"native_bytes",raw.length,"plain_CP932_oracle_checked",plain,"ruby_spans",spans,"displayed_body_sha256",hash(e.body()));
    }
    private static Object fitOracle(DesktopDd e)throws Exception{
        Dc3Scene.Layer live=e.host.scene.slots[1];if(!live.centered||live.resource.isEmpty())return null;
        Crx.Image raw=e.resources.image(live.resource);BufferedImage source=new BufferedImage(raw.width,raw.height,BufferedImage.TYPE_INT_ARGB);source.setRGB(0,0,raw.width,raw.height,raw.argb(true),0,raw.width);
        BufferedImage actual=new BufferedImage(1280,720,BufferedImage.TYPE_INT_ARGB),expected=new BufferedImage(1280,720,BufferedImage.TYPE_INT_ARGB);Graphics2D a=actual.createGraphics(),b=expected.createGraphics();
        try{
            a.setColor(Color.BLACK);a.fillRect(0,0,1280,720);b.setColor(Color.BLACK);b.fillRect(0,0,1280,720);
            Dc3Scene.Layer[] layers=new Dc3Scene.Layer[43];for(int n=0;n<43;n++)layers[n]=new Dc3Scene.Layer();layers[1]=live.copy();
            Method draw=DesktopDd.class.getDeclaredMethod("scene",Graphics2D.class,Dc3Scene.Layer[].class,List.class,Dc3Camera.class,Dc3Petals.class,boolean.class,boolean.class,long.class,boolean.class);draw.setAccessible(true);draw.invoke(e,a,layers,Collections.emptyList(),new Dc3Camera(),new Dc3Petals(),false,false,0L,false);
            // Independent constants/formula from4289AC and4432E0. The native
            // GPU is not run: this checks the portable renderer's geometry.
            int xScale=12800000/raw.width,yScale=7200000/raw.height;float unit=xScale>=1000&&yScale>=1000?10000f:100f;double sx=xScale/unit,sy=yScale/unit,px=live.pivotX==0?raw.width/2.0:live.pivotX,py=live.pivotY==0?raw.height/2.0:live.pivotY;
            double x=live.x+px-px*sx-(1280-raw.width*sx)/2,y=live.y+py-py*sy-(720-raw.height*sy)/2;
            b.translate(x,y);b.scale(sx,sy);b.setComposite(AlphaComposite.SrcOver.derive(live.alpha/255f));b.drawImage(source,raw.x,raw.y,null);
            String observed=pixels(actual),wanted=pixels(expected);check(observed.equals(wanted),"PH B4 compositor matches independent native-derived geometry");return Json.obj("resource",live.resource,"source_size",new int[]{raw.width,raw.height},"basis_points",new int[]{xScale,yScale},"position",new int[]{live.x,live.y},"compared_pixels",1280*720,"portable_pixel_sha256",observed,"original_GPU_compared",false);
        }finally{a.dispose();b.dispose();source.flush();actual.flush();expected.flush();}
    }
    private static Object actorOracle(DesktopDd e)throws Exception{
        Dc3Scene.Layer live=null;int slot=-1;for(int s=4;s<=8;s++)if(!e.host.scene.slots[s].resource.isEmpty()&&e.host.scene.slots[s].alpha>0){live=e.host.scene.slots[s];slot=s;break;}if(live==null)return null;
        Crx.Image raw=e.resources.image(live.resource);BufferedImage source=new BufferedImage(raw.width,raw.height,BufferedImage.TYPE_INT_ARGB);source.setRGB(0,0,raw.width,raw.height,raw.argb(true),0,raw.width);
        BufferedImage actual=new BufferedImage(1280,720,BufferedImage.TYPE_INT_ARGB),expected=new BufferedImage(1280,720,BufferedImage.TYPE_INT_ARGB);Graphics2D a=actual.createGraphics(),b=expected.createGraphics();
        try{
            a.setColor(Color.BLACK);a.fillRect(0,0,1280,720);b.setColor(Color.BLACK);b.fillRect(0,0,1280,720);
            Dc3Scene.Layer[] layers=new Dc3Scene.Layer[43];for(int i=0;i<43;i++)layers[i]=new Dc3Scene.Layer();layers[slot]=live.copy();
            Method draw=DesktopDd.class.getDeclaredMethod("scene",Graphics2D.class,Dc3Scene.Layer[].class,List.class,Dc3Camera.class,Dc3Petals.class,boolean.class,boolean.class,long.class,boolean.class);draw.setAccessible(true);draw.invoke(e,a,layers,Collections.emptyList(),new Dc3Camera(),new Dc3Petals(),false,false,0L,false);
            b.translate(live.x+live.pivotX,live.y+live.pivotY);b.scale(live.scaleX/100f,live.scaleY/100f);b.translate(-live.pivotX,-live.pivotY);b.setComposite(AlphaComposite.SrcOver.derive(live.alpha/255f));b.drawImage(source,raw.x,raw.y,null);
            String observed=pixels(actual),wanted=pixels(expected);check(observed.equals(wanted),"PH actor retains original CRX origin, pixel position and explicit scale/pivot");int visible=0;for(int pixel:actual.getRGB(0,0,1280,720,null,0,1280))if(pixel!=0xff000000)visible++;
            if(visible==0)return null;return Json.obj("slot",slot,"resource",live.resource,"source_origin",new int[]{raw.x,raw.y},"source_size",new int[]{raw.width,raw.height},"position",new int[]{live.x,live.y},"scale_percent",new int[]{live.scaleX,live.scaleY},"explicit_pivot",new int[]{live.pivotX,live.pivotY},"compared_pixels",1280*720,"visible_nonblack_pixels",visible,"portable_pixel_sha256",observed,"neutral_camera_for_geometry_oracle",true,"original_GPU_compared",false);
        }finally{a.dispose();b.dispose();source.flush();actual.flush();expected.flush();}
    }
    private static Object coldRestore(Path game,String executable,DesktopDd e,Path out)throws Exception{
        return coldRestore(game,executable,e,out,"portable-story.sav");
    }
    private static Object coldRestore(Path game,String executable,DesktopDd e,Path out,String filename)throws Exception{
        e.playbackMillis(1000000);BufferedImage before=e.render();String expected;try{expected=pixels(before);}finally{before.flush();}
        String script=e.scriptName(),body=hash(e.body());int pc=e.scriptOffset();Path saved=out.resolve(filename);e.save(saved);
        try(DesktopRuntime reopened=new DesktopRuntime(game,true,executable)){
            check(reopened.engine instanceof DesktopDd,"PH cold factory selects the DD presentation owner");DesktopDd cold=(DesktopDd)reopened.engine;cold.restore(saved);BufferedImage actual=cold.render();String observed;try{observed=pixels(actual);}finally{actual.flush();}
            check(cold.host instanceof Dc4Host,"Cold restore keeps the independent PH host");check(script.equals(cold.scriptName())&&pc==cold.scriptOffset()&&body.equals(hash(cold.body())),"PH portable file restores exact script, PC and text");check(expected.equals(observed),"PH cold file restore retains every production frame pixel");
            return Json.obj("file",saved.getFileName().toString(),"sha256",Binary.sha256(Files.readAllBytes(saved)),"script",script,"pc",pc,"pixel_sha256",observed,"native_Windows_slot_import",false);
        }
    }
    private static void audio(DesktopDd e,Set<String> checked,List<Object> records)throws Exception{
        for(int channel=0;channel<6;channel++){String name=e.audio(channel);if(name.isEmpty()||!checked.add(name))continue;Xpcm x=e.resources.audio(name);MessageDigest digest=MessageDigest.getInstance("SHA-256");final long[] count={0};OutputStream sink=new OutputStream(){public void write(int b){count[0]++;}public void write(byte[] b,int off,int n){count[0]+=n;}};
            e.resources.writeAudio(name,new DigestOutputStream(sink,digest));check(count[0]>0,"Selected PH audio exports nonempty decoded/container output");records.add(Json.obj("channel",channel,"resource",name,"codec",x.codec,"channels",x.channels,"sample_rate",x.sampleRate,"bits",x.bits,"output_bytes",count[0],"sha256",Binary.hex(digest.digest())));
        }
    }
    private static List<Object> savedFiles(Path directory)throws IOException{
        List<Object> result=new ArrayList<>();if(!Files.isDirectory(directory))return result;
        try(DirectoryStream<Path> entries=Files.newDirectoryStream(directory,"slot-*.sav")){for(Path file:entries)if(Files.isRegularFile(file))result.add(Json.obj("file",file.getFileName().toString(),"bytes",Files.size(file),"sha256",Binary.sha256(Files.readAllBytes(file))));}return result;
    }
    public static void main(String[] args)throws Exception{
        if(args.length<2)throw new IOException("Dc4DesktopAudit GAME OUTPUT [--discover] [--action ORIGINAL_ACTION]... [--lines N] [--steps N]");
        Path game=Paths.get(args[0]).toRealPath(),out=DesktopRouteAudit.outputOutsideGame(game,args[1]);List<String> requested=new ArrayList<>(),storyActions=new ArrayList<>();boolean discover=false,musicGateFixture=false,requireActor=false;int target=20,budget=30000;
        for(int n=2;n<args.length;n++){if(args[n].equals("--discover"))discover=true;else if(args[n].equals("--music-gate-fixture"))musicGateFixture=true;else if(args[n].equals("--require-actor"))requireActor=true;else if(args[n].equals("--action"))requested.add(args[++n]);else if(args[n].equals("--story-action"))storyActions.add(args[++n]);else if(args[n].equals("--lines"))target=Integer.parseInt(args[++n]);else if(args[n].equals("--steps"))budget=Integer.parseInt(args[++n]);else throw new IOException("Unknown PH audit option "+args[n]);}
        System.setProperty("dc.saveRoot",out.resolve("saves").toString());System.setProperty("dc.cacheRoot",out.resolve("cache").toString());String executable="DC4PHDL.exe";
        Map<String,Object> report=Json.obj("scope","Production DesktopRuntime factory and original START/menu input; portable save file, CP932 text, native-derived B4 geometry and source audio export. Media completion/time simulated. No original EXE, native GPU or audible-output equivalence.","original_exe_executed",false,"original_game_directory_written",false,"audible_output_verified",false,"media_completion_simulated",true,"requested_actions",requested);List<Object> menus=new ArrayList<>(),bodies=new ArrayList<>(),audio=new ArrayList<>(),frames=new ArrayList<>();report.put("menus",menus);report.put("bodies",bodies);report.put("audio",audio);report.put("frames",frames);
        report.put("synthetic_music_gate_fixture",musicGateFixture);if(musicGateFixture)report.put("synthetic_state_change",Json.obj("bank",100,"index",109,"value",1,"when","first original title wait","persistent_progress_written",false,"CG_or_route_flags_changed",false));
        try(DesktopRuntime runtime=new DesktopRuntime(game,true,executable)){
            check(runtime.engine instanceof DesktopDd,"DesktopRuntime selects the PH presentation backend");DesktopDd e=(DesktopDd)runtime.engine;check(e.host instanceof Dc4Host&&e.resources.profileId.equals("dc4ph-abb6"),"Independent PH semantic profile and host");check(e.codec.charset.name().equalsIgnoreCase("windows-31j"),"PH original data selects CP932");
            report.put("profile",e.resources.profileId);report.put("class_source",DesktopDd.class.getProtectionDomain().getCodeSource().getLocation().toString());report.put("audit_class_source",Dc4DesktopAudit.class.getProtectionDomain().getCodeSource().getLocation().toString());report.put("START_sha256",Binary.sha256(e.resources.files.read("AdvData/MES/START.MES",1024*1024)));report.put("NAMES_sha256",Binary.sha256(e.resources.files.read("AdvData/GRP/NAMES.DAT",1024*1024)));report.put("EXE_sha256",Binary.sha256(e.resources.files.read(executable,8*1024*1024)));
            int selected=0,lines=0;long lastTicket=-1;String lastWhere="",lastMenu="";boolean restored=false,actor=false,fitted=false,done=false,gateApplied=false,storyActionApplied=false;Set<String> checkedAudio=new HashSet<>();
            try(BufferedWriter trace=Files.newBufferedWriter(out.resolve("trace.jsonl"),StandardCharsets.UTF_8)){
                for(int step=0;step<budget;step++){
                    e.tick(1000);if(e.vm().status()==MesVm.Status.FAULT)throw new IOException(e.vm().error());String where=e.scriptName()+"@"+Integer.toHexString(e.scriptOffset());audio(e,checkedAudio,audio);
                    trace.write(Json.text(Json.obj("step",step,"site",where,"status",e.vm().status().toString(),"text",e.textWaiting(),"menu",e.menuWaiting())));trace.newLine();
                    if(e.menuWaiting()){
                        // PH78 starts the panel at its original off-screen
                        // origin and moves it over twenty updates. A screenshot
                        // or synthetic click at the first tick is premature.
                        e.tick(400);if(e.vm().status()==MesVm.Status.FAULT)throw new IOException(e.vm().error());
                        where=e.scriptName()+"@"+Integer.toHexString(e.scriptOffset());
                        if(musicGateFixture&&!gateApplied){check(e.scriptName().equalsIgnoreCase("MAIN"),"Synthetic music gate applies at the original title only");((Dc4Host)e.host).variable(100,109,1);gateApplied=true;}
                        List<String> available=actions(e);List<Object> bounds=new ArrayList<>();
                        for(DesktopEngine.Control c:e.controls())if(!c.action.isEmpty()){
                            int cx=(c.left+c.right)/2,cy=(c.top+c.bottom)/2;
                            check(cx>=0&&cx<1280&&cy>=0&&cy<720,"Settled original PH control center is on screen: "+c.action+" "+cx+","+cy);
                            bounds.add(Json.obj("action",c.action,"rect",new int[]{c.left,c.top,c.right,c.bottom}));
                        }
                        String menuKey=where+"#"+e.ticket()+"#"+e.host.read(99,246);
                        if(!lastMenu.equals(menuKey)){menus.add(Json.obj("site",where,"wait_ticket",e.ticket(),"actions",available,"settle_ms",400,"page99_0",e.host.read(99,0),"list_offset99_246",e.host.read(99,246),"grid_count",e.host.ui.grid.size(),"list_items",e.host.list.items.size(),"music_resource",e.audio(0),"music_active",e.audioActive(0),"music_generation",e.audioGeneration(0),"controls",bounds));lastMenu=menuKey;frames.add(frame(e,out,"menu-"+menus.size()));System.out.println(Json.text(Json.obj("menu",where,"actions",available)));}
                        if(selected<requested.size()){
                            String action=requested.get(selected++);
                            if(action.startsWith("@scroll:")){check(e.host.ui.gridWaiting&&e.host.list.active(),"Scroll input has an active original list");int units=Integer.parseInt(action.substring(8));check(units>=-1000000&&units<=1000000,"Bounded scroll input");e.scroll(units);}
                            else{check(available.contains(action),"Requested original PH action is present: "+action);e.choose(action);}
                            continue;
                        }
                        if(discover){report.put("result","MENU_DISCOVERED");done=true;break;}
                        throw new IOException("Original PH menu needs --action at "+where+": "+available);
                    }
                    if(e.textWaiting()&&!e.body().isEmpty()&&(!where.equals(lastWhere)||lastTicket!=e.ticket())){
                        // Complete the presentation clock without consuming a
                        // reason8 auto wait before its screenshot is captured.
                        e.playbackMillis(1000000);
                        lastWhere=where;lastTicket=e.ticket();lines++;bodies.add(bodyOracle(e));if(lines==1){frames.add(frame(e,out,"first-body"));report.put("cold_restore",coldRestore(game,executable,e,out));restored=true;}
                        if(!actor&&e.actorVisible()){Object geometry=actorOracle(e);if(geometry!=null){check(!e.host.scene.novelMode&&e.host.ui.templates[0]!=null&&!e.host.ui.templates[0].texts.isEmpty()&&!e.visibleBody().isEmpty(),"PH actor dialogue retains its regular visible body template");report.put("actor_geometry",geometry);frames.add(frame(e,out,"first-actor"));report.put("actor_cold_restore",coldRestore(game,executable,e,out,"portable-actor.sav"));actor=true;}}
                        if(!fitted){Object fit=fitOracle(e);if(fit!=null){report.put("B4_geometry",fit);fitted=true;}}
                        if(lines>=target&&(!requireActor||actor)){
                            frames.add(frame(e,out,"last-body"));
                            if(!storyActions.isEmpty()&&!storyActionApplied){
                                List<Object> evidence=new ArrayList<>();for(String action:storyActions){
                                    check(actions(e).contains(action),"Requested story action is an original on-screen control: "+action);
                                    String beforeBody=hash(e.body()),beforeVoice=e.audio(5);int beforeVoiceGeneration=e.audioGeneration(5),beforeFlag=e.host.read(99,227),beforeControls=actions(e).size();
                                    String expectedVoice=e.host.history.entries.isEmpty()?"":e.host.history.entries.get(e.host.history.entries.size()-1).voice;int historyCount=e.host.history.entries.size();
                                    e.choose(action);
                                    if(action.equals(":n")){check(e.host.read(99,227)==(beforeFlag==0?1:0),"Native control bar preference really toggled");List<String> after=actions(e);check(beforeFlag==0?after.size()>1&&after.contains(":backlog")&&after.contains(":n"):after.equals(Collections.singletonList(":n")),"Visible controls follow the native collapsed/expanded templates");check(beforeBody.equals(hash(e.body())),"Control-bar input retains the current text");}
                                    if(action.equals(":voice")){check(e.audioGeneration(5)==beforeVoiceGeneration+1&&e.audio(5).equals(expectedVoice)&&e.audioActive(5)==!expectedVoice.isEmpty(),"Native voice replay restarts exactly the current history voice");check(beforeBody.equals(hash(e.body()))&&e.host.history.entries.size()==historyCount,"Voice replay does not append or change dialogue history");}
                                    evidence.add(Json.obj("action",action,"body_before_sha256",beforeBody,"body_after_sha256",hash(e.body()),"voice_before",beforeVoice,"voice_after",e.audio(5),"voice_generation_before",beforeVoiceGeneration,"voice_generation_after",e.audioGeneration(5),"voice_active_after",e.audioActive(5),"controls_before",beforeControls,"controls_after",actions(e).size(),"flag99_227_before",beforeFlag,"flag99_227_after",e.host.read(99,227)));
                                    frames.add(frame(e,out,"story-action-"+evidence.size()));
                                }storyActionApplied=true;report.put("original_story_actions",evidence);continue;
                            }
                            report.put("result","PASS");done=true;break;
                        }
                    }
                    for(int c=0;c<6;c++)if(e.audioActive(c)&&!e.audioLoop(c))e.audioCompleted(c,e.audioGeneration(c));
                    e.vm().signalHost(e.ticket(),MesVm.HostSignal.MEDIA_COMPLETE);e.vm().signalHost(e.ticket(),MesVm.HostSignal.TEXT_COMPLETE);
                    if(e.textWaiting()||e.host.remainingMillis()<0)e.vm().signalHost(e.ticket(),MesVm.HostSignal.ADVANCE);
                    if(e.halted())throw new IOException("Unexpected PH STOP at "+where);
                }
            }
            check(done,"PH audit step budget exhausted");check(e.missingImages.isEmpty(),"PH production render did not silently omit requested images: "+e.missingImages);
            check(selected==requested.size(),"All requested original actions were consumed");
            if(!discover){check(restored&&lines>=target,"Required PH story waits and cold portable restore");}
            if(requireActor)check(actor,"PH audit required an actual visible actor resource and geometry comparison");
            report.put("dialogue_waits",lines);report.put("actor_resources_visible",actor);report.put("B4_compared",fitted);report.put("loaded_images",new ArrayList<>(e.loadedImages));report.put("missing_images",new ArrayList<>(e.missingImages));
            report.put("owned_native_menu_save_files",savedFiles(e.saveDirectory));
        }catch(Exception failure){report.put("result","FAIL");report.put("error",failure.toString());throw failure;}
        finally{report.put("checks",checks);Files.write(out.resolve("report.json"),(Json.text(report)+"\n").getBytes(StandardCharsets.UTF_8));}
        System.out.println(Json.text(Json.obj("result",report.get("result"),"checks",checks,"dialogue_waits",report.get("dialogue_waits"))));
    }
}
