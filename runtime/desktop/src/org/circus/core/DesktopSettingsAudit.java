package org.circus.core;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import javax.imageio.ImageIO;

/** Explore original setting buttons from isolated VM snapshots, without game EXEs. */
public final class DesktopSettingsAudit {
    static void settle(DesktopEngine e)throws Exception{
        for(int n=0;n<2000;n++){e.tick(1000);if(e.menuWaiting()||e.textWaiting()||e.exitRequested())return;
            for(int ch=0;ch<6;ch++)if(e.audioActive(ch)&&!e.audioLoop(ch))e.audioCompleted(ch,e.audioGeneration(ch));
            if(!e.movie().isEmpty())e.vm().signalHost(e.ticket(),MesVm.HostSignal.MEDIA_COMPLETE);
            if(e.vm().status()==MesVm.Status.WAIT_HOST){long delay=e instanceof DesktopDd?((DesktopDd)e).host.remainingMillis():((DesktopLegacy)e).host().remaining;if(e.vm().scriptName().equalsIgnoreCase("MAIN"))e.click(e.width/2,e.height/2,false);else if(delay<0)e.vm().signalHost(e.ticket(),MesVm.HostSignal.ADVANCE);}
        }throw new IOException("Setting did not settle: "+e.vm().scriptName()+"@"+Integer.toHexString(e.vm().offset())+" "+e.vm().status()+" delay="+(e instanceof DesktopLegacy?((DesktopLegacy)e).host().remaining:0)+" audio="+(e instanceof DesktopLegacy?((DesktopLegacy)e).host().audioMask:0));
    }
    static List<String> actions(DesktopEngine e){List<String> result=new ArrayList<>();for(DesktopEngine.Control c:e.controls())if(!result.contains(c.action))result.add(c.action);return result;}
    static List<int[]> sliders(DesktopEngine e){List<int[]> out=new ArrayList<>();if(e instanceof DesktopDd){for(Dc3Ui.Button b:((DesktopDd)e).host.ui.current.buttons)for(Dc3Ui.Sprite sp:b.sprites)if(sp.state==5){Dc3Scene.Quad q=sp.quad;out.add(new int[]{(b.left+b.right)/2,(b.top+b.bottom)/2,q.x,q.y,q.sy,q.width,q.sx});}}else for(Dc2Ui.Button b:((DesktopLegacy)e).host().ui.buttons)for(Dc2Ui.Quad q:b.sprites)if(q.state==5)out.add(new int[]{(b.left+b.right)/2,(b.top+b.bottom)/2,q.x,q.y,q.sy,q.width,q.sx});return out;}
    public static void main(String[] args)throws Exception{
        Path out=Paths.get(args[1]);Files.createDirectories(out);System.setProperty("dc.saveRoot",out.resolve("saves").toString());
        try(DesktopRuntime app=new DesktopRuntime(Paths.get(args[0]),true)){
            DesktopEngine e=app.engine;settle(e);for(int n=2;n<args.length;n++){e.choose(args[n]);settle(e);}
            List<Map<String,Object>> reports=new ArrayList<>();Map<String,Set<String>> visited=new HashMap<>();ArrayDeque<byte[]> pending=new ArrayDeque<>();pending.add(e.vm().snapshot());int pages=0,failures=0;
            while(!pending.isEmpty()&&pages<80){byte[] base=pending.remove();e.restore(base);e.vm().restore(base);List<String> keys=actions(e);List<String> normalized=new ArrayList<>();for(String k:keys)normalized.add(k.replace("_on","_toggle").replace("_off","_toggle").replaceAll("^(?:on|off)(?=[0-9])","toggle"));String signature=e.vm().scriptName()+":"+normalized;Set<String> tried=visited.computeIfAbsent(signature,k->new HashSet<>());keys.removeAll(tried);if(keys.isEmpty())continue;tried.addAll(keys);pages++;ImageIO.write(e.render(),"png",out.resolve("page-"+pages+".png").toFile());
                List<int[]> sliders=sliders(e);for(int[] slider:sliders)for(int end=0;end<2;end++){e.restore(base);e.vm().restore(base);String error="";try{if(!e.drag(slider[0],slider[1],true))throw new IOException("Slider hit area rejected");e.drag(slider[2+end],slider[1],false);int want=end==0?0:slider[6]-1;if(e.vm().variables.read(slider[4],slider[5])!=want)throw new IOException("Slider variable mismatch "+slider[4]+":"+slider[5]);e.render();e.endDrag();settle(e);e.render();}catch(Exception ex){error=ex.toString();failures++;}reports.add(Json.obj("page",signature,"action","@slider:"+slider[4]+":"+slider[5]+":"+end,"error",error,"next",e.vm().scriptName()));}
                for(String action:keys){e.restore(base);e.vm().restore(base);String error="";try{e.choose(action);settle(e);e.render();if(e.menuWaiting()&&!e.exitRequested()&&e.vm().scriptName().equalsIgnoreCase(e.resources.profile.id.startsWith("dc3")?"CONFIG":e.resources.profile.id.startsWith("dc2dm")?"OPTION":"OPTION"))pending.add(e.vm().snapshot());}catch(Exception ex){error=ex.toString();failures++;}reports.add(Json.obj("page",signature,"action",action,"error",error,"next",e.vm().scriptName()));}
            }
            Files.write(out.resolve("result.json"),Json.text(Json.obj("profile",e.resources.profile.id,"pages",pages,"actions",reports.size(),"failures",failures,"queue_remaining",pending.size(),"checks",reports)).getBytes(java.nio.charset.StandardCharsets.UTF_8));System.out.println(Json.text(Json.obj("pages",pages,"actions",reports.size(),"failures",failures)));if(failures>0)System.exit(2);
        }
    }
}
