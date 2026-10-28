package org.circus.core;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;

/** Real-script system screens, entered from a stable dialogue and tested through their original controls. */
public final class UnitySystemAudit {
    private DesktopUnity e;private final Path out;private final StringBuilder log=new StringBuilder();private int checks;private final Set<String> audio=new HashSet<>();
    UnitySystemAudit(DesktopUnity e,Path out){this.e=e;this.out=out;}
    private void check(boolean ok,String what)throws IOException{if(!ok)throw new IOException(what+" at "+e.scriptName()+":"+e.scriptOffset());checks++;log.append("PASS ").append(what).append('\n');}
    private List<String> actions(){List<String> a=new ArrayList<>();for(DesktopEngine.Control c:e.controls())a.add(c.action);return a;}
    private void choose(String action)throws Exception{log.append("CHOOSE ").append(e.scriptName()).append(' ').append(action).append('\n');for(DesktopEngine.Control c:e.controls())if(c.action.equals(action)){e.click(Math.min(e.width-1,Math.max(0,(c.left+c.right)/2)),Math.min(e.height-1,Math.max(0,(c.top+c.bottom)/2)),false);return;}throw new IOException("No "+action+" in "+e.scriptName()+" "+actions());}
    private void tick()throws Exception{
        for(int c=0;c<6;c++)if(e.audioActive(c)){String ref=e.audio(c);if(audio.add(ref)){Xpcm a;try{a=e.resources.audio(ref);}catch(IOException problem){throw new IOException("System audit audio '"+ref+"' at "+e.scriptName()+":"+e.scriptOffset(),problem);}check(a.codec.equals("ogg")&&a.payload.length>32,"system audio conversion "+ref);}if(!e.audioLoop(c))e.audioCompleted(c,e.audioGeneration(c));}
        if(!e.movie().isEmpty())e.mediaCompleted(e.ticket());e.tick(1000);BufferedImage image=e.render();image.flush();if(e.halted())throw new IOException("Unexpected system halt");
    }
    private void menu(String expected)throws Exception{for(int i=0;i<1000;i++){tick();if(e.menuWaiting()){check(e.scriptName().equals(expected),"original "+expected+" menu reached");return;}if(e.textWaiting())throw new IOException("Returned to text before "+expected);if(e.script.status()==CircusScrVm.Status.WAIT_CLICK)e.click(1,1,false);}throw new IOException("Menu timeout "+expected);}
    private void story()throws Exception{for(int i=0;i<1000;i++){tick();if(e.textWaiting())return;if(e.menuWaiting()){if(actions().contains("return"))choose("return");else throw new IOException("Cannot return from "+e.scriptName()+" "+actions());}else if(e.script.status()==CircusScrVm.Status.WAIT_CLICK)e.click(1,1,false);}throw new IOException("Story return timeout");}
    private void shot(String name)throws Exception{BufferedImage image=e.render();try(OutputStream stream=Files.newOutputStream(out.resolve(name+".png"))){check(ImageIO.write(image,"png",stream),"render "+name);}finally{image.flush();}}
    private void open(String action,String script)throws Exception{e.click(1,1,true);menu("advmenu");shot("advmenu");choose(action);menu(script);shot(script);}
    private void slot(int number)throws Exception{for(Dc3rHost.Button b:e.host.buttons())if(b.action.equals("selsave")&&b.flagValue==number){e.click((b.left+b.right)/2,(b.top+b.bottom)/2,false);return;}throw new IOException("No original slot "+number);}
    private void seek()throws Exception{for(int n=0;n<2000;n++){tick();if(e.textWaiting())return;if(e.menuWaiting()){List<String> a=actions();String chosen=null;for(String p:new String[]{"window","sce_start","sce01"})if(a.contains(p)){chosen=p;break;}if(chosen==null)throw new IOException("Unrecognized startup menu "+a);choose(chosen);}else if(e.script.status()==CircusScrVm.Status.WAIT_CLICK)e.click(1,1,false);}throw new IOException("Startup timed out");}
    private void confirm()throws Exception{for(int n=0;n<30;n++){tick();if(e.menuWaiting()){if(actions().contains(":y")){choose(":y");return;}if(actions().contains("return"))return;}if(e.textWaiting())return;}throw new IOException("Save/load confirmation timed out");}
    private void extras()throws Exception{
        e=new DesktopUnity(e.data,true);
        for(int n=0;n<2000;n++){tick();if(e.menuWaiting()){if(e.scriptName().equals("main")&&actions().contains("sce_start"))break;if(actions().contains("window"))choose("window");else throw new IOException("Unrecognized title menu "+actions());}else if(e.script.status()==CircusScrVm.Status.WAIT_CLICK)e.click(1,1,false);}
        check(e.scriptName().equals("main")&&actions().contains("extra"),"fresh original title has EXTRA entry");choose("extra");menu("main");shot("extra");
        choose("cgmode");menu("cg_rcc");shot("cg");choose("return");menu("main");check(actions().contains("scemode"),"CG returns to original EXTRA menu");
        choose("scemode");menu("sce_rcc");shot("scene");choose("return");menu("main");check(actions().contains("musmode"),"Scene Jump returns to original EXTRA menu");
        choose("musmode");menu("music");shot("music");choose("play");menu("music");check(e.audioActive(0)&&e.audio(0).startsWith("bgm/"),"original MUSIC Play selects a BGM resource");choose("stop");menu("music");check(!e.audioActive(0),"original MUSIC Stop stops playback");choose("return");menu("main");check(actions().contains("cgmode"),"Music returns to original EXTRA menu");
        choose("rmain");menu("main");check(actions().contains("sce_start"),"EXTRA returns to original title");
    }
    public void run()throws Exception{
        Files.createDirectories(out);Exception failure=null;try{
            seek();String name=e.scriptName(),body=e.body();int pc=e.scriptOffset();check(e.script.callDepth()==0,"story context has no quick-script caller");
            Path save=e.saveDirectory.resolve("slot-0.sav");Files.deleteIfExists(save);
            open("save","save");byte[] suspendedMenu=e.snapshot();DesktopUnity resumed=new DesktopUnity(e.data,true);resumed.restore(suspendedMenu);e=resumed;check(e.scriptName().equals("save")&&e.menuWaiting(),"SAVE menu survives engine recreation");
            slot(0);confirm();for(int n=0;n<100&&!Files.isRegularFile(save);n++)tick();check(Files.isRegularFile(save),"original SAVE writes selected slot after menu recreation");
            DesktopUnity restored=new DesktopUnity(e.data,true);restored.restore(save);check(restored.scriptName().equals(name)&&restored.scriptOffset()==pc&&restored.textWaiting()&&restored.script.callDepth()==0&&restored.body().equals(body),"original SAVE contains story VM, not SAVE menu VM");
            story();check(e.scriptName().equals(name)&&e.scriptOffset()==pc&&e.body().equals(body),"SAVE return preserves current dialogue");
            open("load","load");slot(0);confirm();story();check(e.scriptName().equals(name)&&e.scriptOffset()==pc&&e.body().equals(body),"original LOAD restores selected story dialogue");
            open("config","option");check(e.host.layers[0].x==0&&e.host.layers[0].y==0&&e.host.layers[0].moveTime==0,"original OPTION background does not inherit story camera motion");int volume=e.script.readFlag((99<<16)+7);choose("xmusic_vol_down");menu("option");check(e.script.readFlag((99<<16)+7)==Math.max(0,volume-1),"original OPTION changes music volume");choose("retchk");story();check(e.scriptName().equals(name)&&e.scriptOffset()==pc&&e.body().equals(body),"OPTION returns to same dialogue");
            if(!actions().contains(":backlog"))choose(":n");choose(":backlog");menu("log");shot("log");check(!e.host.history.isEmpty(),"LOG contains actual dialogue history");e.click(1,1,true);story();check(e.scriptName().equals(name)&&e.scriptOffset()==pc&&e.body().equals(body),"LOG returns to same dialogue");
            extras();
        }catch(Exception error){failure=error;log.append("FAIL ").append(error).append('\n');}
        Files.write(out.resolve("system-result.txt"),log.toString().getBytes("UTF-8"));Files.write(out.resolve("system-result.json"),(Json.text(Json.obj("pass",failure==null,"checks",checks,"media_completion_simulated",true,"audio_conversion_validated",failure==null,"error",failure==null?"":failure.toString()))+"\n").getBytes("UTF-8"));if(failure!=null)throw failure;System.out.println(log);
    }
    public static void main(String[] args)throws Exception{if(args.length!=2)throw new IllegalArgumentException("Usage: UnitySystemAudit GAME_DIRECTORY OUTPUT_DIRECTORY");Path out=Paths.get(args[1]);System.setProperty("dc.saveRoot",out.resolve("saves").toAbsolutePath().toString());UnityCircusResources resources=new UnityCircusResources(new GameResources(new LocalGameStorage(Paths.get(args[0]))));new UnitySystemAudit(new DesktopUnity(resources,true),out).run();}
}
