package org.circus.core;

import java.io.*;
import java.nio.*;
import java.nio.file.*;
import java.util.*;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;

/** Executes the original Unity scripts. Audio/movie completion is deliberately simulated. */
public final class UnityStartupAudit {
    private final DesktopUnity engine;private final Path output;
    private final StringBuilder log=new StringBuilder();private final List<Object> events=new ArrayList<>();
    private final Set<String> audioChecked=new TreeSet<>(),moviesChecked=new TreeSet<>();
    private final Set<String> buttonResources=new HashSet<>();
    private int checks,dialogues,choiceBranches;private boolean title,started,actor,firstChoice,roundtrip;private String lastDialogue="";
    public UnityStartupAudit(DesktopUnity engine,Path output){this.engine=engine;this.output=output;}
    private void check(boolean condition,String message)throws IOException{if(!condition)throw new IOException(message);checks++;log.append("PASS ").append(message).append('\n');}
    private void event(String name,Object details){events.add(Json.obj("event",name,"script",engine.scriptName(),"instruction",engine.scriptOffset(),"details",details));log.append(name).append(' ').append(engine.scriptName()).append(':').append(engine.scriptOffset()).append(' ').append(details).append('\n');}
    private static String pixels(BufferedImage image){int[] data=image.getRGB(0,0,image.getWidth(),image.getHeight(),null,0,image.getWidth());ByteBuffer bytes=ByteBuffer.allocate(data.length*4);for(int p:data)bytes.putInt(p);return Binary.sha256(bytes.array());}
    private void screenshot(String name)throws IOException{BufferedImage image=engine.render();try(OutputStream out=Files.newOutputStream(output.resolve(name+".png"))){check(ImageIO.write(image,"png",out),"render "+name);}finally{image.flush();}}
    private void preloadTexture(String reference)throws IOException{if(reference!=null&&!reference.isEmpty()&&!reference.startsWith("@capture/")&&buttonResources.add(reference))engine.data.texture(reference);}
    private void preloadQuads(List<Dc3rHost.Quad> quads)throws IOException{
        for(Dc3rHost.Quad original:quads){for(String frame:original.packFrames)preloadTexture("anim/"+frame);for(Dc3rHost.Quad q:Arrays.asList(original,engine.host.animated(original)))preloadTexture(q.resource.isEmpty()?engine.host.works.get(q.work):q.resource);}
    }
    private void preloadButtonStates()throws IOException{
        preloadTexture(engine.host.transitionMask);
        for(Dc3rHost.Panel panel:Arrays.asList(engine.host.back,engine.host.fore,engine.host.fogBack,engine.host.fogFore))preloadQuads(panel.quads);
        for(Dc3rHost.Button b:engine.host.buttons())for(List<Dc3rHost.Quad> quads:Arrays.asList(b.normal,b.hover,b.pressed))preloadQuads(quads);
    }
    private void select(String action)throws Exception{for(DesktopEngine.Control c:engine.controls())if(c.action.equals(action)){int x=Math.max(0,Math.min(engine.width-1,(c.left+c.right)/2)),y=Math.max(0,Math.min(engine.height-1,(c.top+c.bottom)/2));check(c.contains(x,y),"visible original control "+action);event("select",action);engine.click(x,y,false);return;}throw new IOException("No original action "+action);}
    private void inspectMedia()throws Exception{
        for(int c=0;c<6;c++)if(engine.audioActive(c)){
            String reference=engine.audio(c);if(!reference.isEmpty()&&audioChecked.add(reference)){
                Xpcm converted=engine.resources.audio(reference);check(converted.codec.equals("ogg")&&converted.payload.length>32,"FSB5 to Ogg conversion "+reference);event("audio_converted",reference+" "+converted.payload.length+" Ogg bytes");
            }
            if(!engine.audioLoop(c))engine.audioCompleted(c,engine.audioGeneration(c));
        }
        if(!engine.movie().isEmpty()){
            String reference=engine.movie();if(moviesChecked.add(reference)){Archive.Source source=engine.resources.files.source(reference);check(source.size()>32,"movie resource "+reference);event("movie_completion_simulated",reference+" "+source.size()+" bytes");}engine.mediaCompleted(engine.ticket());
        }
    }
    private void saveRoundtrip()throws Exception{
        engine.tick(10000);byte[] state=engine.snapshot();BufferedImage before=engine.render();String hash=pixels(before);before.flush();String name=engine.scriptName(),text=engine.body();int pc=engine.scriptOffset();engine.click(1,1,false);engine.tick(1000);engine.restore(state);BufferedImage after=engine.render();String result=pixels(after);after.flush();
        check(name.equals(engine.scriptName())&&pc==engine.scriptOffset()&&text.equals(engine.body()),"save restores exact story VM position");check(hash.equals(result),"save restores original scene pixels");Files.write(output.resolve("story.state"),state);roundtrip=true;
    }
    private void exerciseChoiceBranches()throws Exception{
        byte[] state=engine.snapshot();Files.write(output.resolve("first-choice.state"),state);List<String> actions=new ArrayList<>();for(DesktopEngine.Control c:engine.controls())actions.add(c.action);
        int count=engine.host.choices.size();for(int i=0;i<count;i++){
            engine.restore(state);String action=":"+(i+1);check(actions.contains(action),"original choice action "+action);select(action);check(engine.script.readFlag((99<<16)+254)==i,"choice stores original selection index "+i);
            boolean reached=false;for(int turn=0;turn<2000;turn++){inspectMedia();engine.tick(1000);inspectMedia();BufferedImage frame=engine.render();frame.flush();if(engine.textWaiting()){reached=true;break;}if(engine.script.status()==CircusScrVm.Status.WAIT_CLICK)engine.click(1,1,false);if(engine.menuWaiting()||engine.halted())break;}
            check(reached,"choice "+i+" reaches following dialogue");screenshot("choice-"+(i+1));event("choice_branch",i+" -> "+engine.scriptName()+":"+engine.scriptOffset());choiceBranches++;
        }engine.restore(state);
    }
    public String run(int minimumDialogues,int maximumDialogues)throws Exception{
        Files.createDirectories(output);Exception failure=null;
        try{
            for(int turn=0;turn<50000;turn++){
                inspectMedia();engine.tick(1000);inspectMedia();BufferedImage rendered=engine.render();rendered.flush();preloadButtonStates();
                if(engine.halted()||engine.exitRequested())throw new IOException("Unexpected script termination "+engine.scriptName());
                if(engine.menuWaiting()){
                    List<String> actions=new ArrayList<>();for(DesktopEngine.Control c:engine.controls())actions.add(c.action);
                    event("original_menu",actions);
                    if("main".equals(engine.scriptName())&&actions.contains("sce_start")){if(!title){screenshot("title");title=true;}select("sce_start");started=true;continue;}
                    if(engine.host.choiceWaiting){if(!firstChoice){screenshot("first-choice");firstChoice=true;check(!engine.host.choices.isEmpty(),"original story choice captions and labels");}if(dialogues>=minimumDialogues)break;String action=actions.get(actions.size()-1);select(action);continue;}
                    String chosen=null;for(String preferred:new String[]{"window","01","start","sceselect01","sce_start"})if(actions.contains(preferred)){chosen=preferred;break;}
                    if(chosen==null)for(String action:actions)if(!action.startsWith(":")){chosen=action;break;}
                    if(chosen==null)throw new IOException("Unresolved original menu "+actions);select(chosen);continue;
                }
                if(engine.textWaiting()){
                    String key=engine.scriptName()+":"+engine.scriptOffset()+":"+engine.ticket();if(!key.equals(lastDialogue)){lastDialogue=key;dialogues++;if(dialogues==1){check(title&&started,"original START to title to New Game to first dialogue");screenshot("first-dialogue");saveRoundtrip();}if(!actor&&engine.actorVisible()){actor=true;screenshot("first-actor");}if(dialogues==minimumDialogues)screenshot("dialogue-"+dialogues);}
                    if(dialogues>=minimumDialogues&&firstChoice||dialogues>=maximumDialogues)break;engine.click(1,1,false);
                }
                else if(engine.script.status()==CircusScrVm.Status.WAIT_CLICK){event("scene_click_without_text",engine.script.offset());engine.click(1,1,false);}
                if(turn%100==0)Files.write(output.resolve("progress.txt"),("turn="+turn+" dialogues="+dialogues+" script="+engine.scriptName()+" instruction="+engine.scriptOffset()+" status="+engine.script.status()+"\n").getBytes("UTF-8"));
            }
            if(firstChoice)exerciseChoiceBranches();check(title,"original title reached");check(started,"original New Game selected");check(dialogues>=minimumDialogues,"at least "+minimumDialogues+" dialogue waits");check(actor,"original character layer reached");check(roundtrip,"save and load roundtrip exercised");
        }catch(Exception e){failure=e;event("failure",e.toString());}
        Map<String,Object> report=Json.obj("pass",failure==null,"checks",checks,"dialogues",dialogues,"original_title",title,"new_game",started,"actor",actor,"story_choice_reached",firstChoice,"choice_branches",choiceBranches,"save_pixel_roundtrip",roundtrip,"audio_resources",audioChecked,"movie_resources",moviesChecked,"windows_game_executed",false,"media_completion_simulated",true,"audio_conversion_validated",failure==null,"audio_playback_validated",false,"events",events);
        Files.write(output.resolve("startup-result.json"),(Json.text(report)+"\n").getBytes("UTF-8"));Files.write(output.resolve("startup-result.txt"),log.toString().getBytes("UTF-8"));if(failure!=null)throw failure;return log.toString();
    }
    public static void main(String[] args)throws Exception{
        if(args.length<2)throw new IllegalArgumentException("Usage: UnityStartupAudit GAME_DIRECTORY OUTPUT_DIRECTORY [MAX_DIALOGUES] [RANGE_PACK]");GameResources.Storage storage=new LocalGameStorage(Paths.get(args[0]));RangeStorage recording=args.length>3?RangeStorage.recording(storage):null;if(recording!=null)storage=recording;UnityCircusResources resources=new UnityCircusResources(new GameResources(storage));
        try{System.out.println(new UnityStartupAudit(new DesktopUnity(resources,true),Paths.get(args[1])).run(50,args.length>2?Integer.parseInt(args[2]):70));}finally{if(recording!=null){recording.write(Paths.get(args[3]));System.out.println("RECORDED_RANGE_BYTES "+recording.recordedBytes());}}
    }
}
