package org.circus.core;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import javax.imageio.ImageIO;

/** Scripted use of the same desktop controller and original hit rectangles. */
public final class DesktopUiAudit {
    public static void main(String[] args)throws Exception{
        Path out=Paths.get(args[1]);Files.createDirectories(out);System.setProperty("dc.saveRoot",out.resolve("saves").toString());
        try(DesktopRuntime app=new DesktopRuntime(Paths.get(args[0]),true)){
            DesktopEngine e=app.engine;int action=2,frame=0;String last="",savedBody="",savedScript="";int savedPc=-1,advance=0;boolean checked=false;
            for(int n=0;n<2000;n++){
                app.tick(1000);String where=e.vm().scriptName()+"@"+Integer.toHexString(e.vm().offset());
                if(e.menuWaiting()){
                    List<String> controls=new ArrayList<>();for(DesktopEngine.Control b:e.controls())controls.add(b.action);
                    if(!where.equals(last)){System.out.println(Json.text(Json.obj("at",where,"controls",controls)));ImageIO.write(e.render(),"png",out.resolve(String.format("%02d-%s.png",frame++,e.vm().scriptName())).toFile());last=where;}
                    if(action>=args.length)return;
                    String next=args[action++];if(next.equals("@right"))e.click(0,0,true);else e.choose(next);
                }else if(e.textWaiting()){
                    if(action<args.length&&args[action].startsWith("@advance:")){advance=Integer.parseInt(args[action++].substring(9));}
                    if(advance>0){e.click(e.width/2,e.height/2,false);advance--;continue;}
                    if(action<args.length&&args[action].equals("@story")){action++;e.vm().signalHost(e.ticket(),MesVm.HostSignal.TEXT_COMPLETE);e.vm().run(1000);ImageIO.write(e.render(),"png",out.resolve("story.png").toFile());e.save(out.resolve("story.sav"));savedBody=e.body();savedScript=e.vm().scriptName();savedPc=e.vm().offset();}
                    if(action<args.length&&args[action].equals("@assert-story")){action++;if(!savedBody.equals(e.body())||!savedScript.equals(e.vm().scriptName())||savedPc!=e.vm().offset())throw new IOException("Original menu did not restore the saved sentence/PC");checked=true;ImageIO.write(e.render(),"png",out.resolve("restored.png").toFile());System.out.println("PASS original system restored sentence and PC");}
                    if(action>=args.length){Files.write(out.resolve("result.json"),Json.text(Json.obj("profile",e.resources.profile.id,"original_system_return_checked",checked,"pass",true)).getBytes(java.nio.charset.StandardCharsets.UTF_8));return;}
                    if(args[action].startsWith(":")){String a=args[action++];if(e instanceof DesktopLegacy)((DesktopLegacy)e).action(a);else ((DesktopDd)e).action(a);}
                    else e.click(e.width/2,e.height/2,false);
                }else if(e.vm().status()==MesVm.Status.WAIT_HOST&&((savedPc<0&&(e.vm().scriptName().equalsIgnoreCase("MAIN")||e.vm().scriptName().toUpperCase(Locale.ROOT).startsWith("TITLE")))||(action<args.length&&(args[action].equals("start")||args[action].equals("sce_start")))||(e instanceof DesktopLegacy?((DesktopLegacy)e).host().remainingMillis()<0:((DesktopDd)e).host.remainingMillis()<0)))e.click(e.width/2,e.height/2,false);
                if(e.exitRequested())return;
            }String detail="";if(e instanceof DesktopLegacy){Dc2Host h=((DesktopLegacy)e).host();detail=" remaining="+h.remainingMillis()+" ui="+h.ui.waiting+" text="+h.textWaiting+" presentation="+h.presentationWaiting+" audio="+h.audioMask+" actions="+Arrays.toString(h.ui.actions);}
            throw new IOException("UI audit timed out at "+e.vm().scriptName()+"@"+Integer.toHexString(e.vm().offset())+" status="+e.vm().status()+" next="+(action<args.length?args[action]:"none")+detail);
        }
    }
}
