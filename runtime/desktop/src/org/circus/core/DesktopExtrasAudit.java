package org.circus.core;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import javax.imageio.ImageIO;

/** Original-script gallery actions with deterministic, explicit media completion. */
public final class DesktopExtrasAudit {
    private static int checks;
    private static void check(boolean yes,String why)throws IOException{checks++;if(!yes)throw new IOException(why);}
    private static void settle(DesktopEngine e)throws Exception{
        for(int n=0;n<2000;n++){e.tick(1000);if(e.menuWaiting())return;for(int ch=1;ch<6;ch++)if(e.audioActive(ch))e.audioCompleted(ch,e.audioGeneration(ch));if(!e.movie().isEmpty())e.vm().signalHost(e.ticket(),MesVm.HostSignal.MEDIA_COMPLETE);
            if(e.textWaiting())throw new IOException("Unexpected dialogue in extras");
            if(e instanceof DesktopDd&&((DesktopDd)e).host.remainingMillis()<0&&e.vm().scriptName().toUpperCase(Locale.ROOT).startsWith("CG"))return;
            if(e.vm().status()==MesVm.Status.WAIT_HOST&&(e instanceof DesktopDd?((DesktopDd)e).host.remainingMillis()<0:((DesktopLegacy)e).host().remainingMillis()<0)||e.vm().scriptName().toUpperCase(Locale.ROOT).startsWith("TITLE"))e.click(e.width/2,e.height/2,false);
        }throw new IOException("Extras did not settle: "+e.vm().scriptName()+"@"+e.vm().offset());
    }
    private static void choose(DesktopEngine e,String action)throws Exception{e.choose(action);settle(e);}
    private static void shot(DesktopEngine e,Path out,String name)throws Exception{ImageIO.write(e.render(),"png",out.resolve(name+".png").toFile());}
    private static void scrollbar(DesktopEngine e)throws Exception{int[] b;ScriptList list;int cells;if(e instanceof DesktopDd){DesktopDd d=(DesktopDd)e;b=d.host.ui.gridBounds;list=d.host.list;cells=d.host.ui.grid.size();}else{PcHost h=(PcHost)((DesktopLegacy)e).host();b=h.gridBounds;list=h.list;cells=h.grid.size();}int x=(b[0]+b[2])/2;e.pointerDown(x,(b[1]+b[3])/2);e.pointerMove(x,b[3]+20);check(e.vm().variables.read(99,246)==list.clamp(Integer.MAX_VALUE,cells),"Original list scrollbar drags to its last page");e.pointerMove(x,b[1]-20);check(e.vm().variables.read(99,246)==0,"Original list scrollbar drags back to its first page");e.pointerUp(x,b[1]);settle(e);e.render();}
    private static void roundtrip(DesktopEngine e)throws Exception{int[] pixels=e.render().getRGB(0,0,e.width,e.height,null,0,e.width);int pc=e.vm().offset();String script=e.vm().scriptName();byte[] saved=e.snapshot();e.restore(saved);check(pc==e.vm().offset()&&script.equals(e.vm().scriptName()),"Extras snapshot PC");check(Arrays.equals(pixels,e.render().getRGB(0,0,e.width,e.height,null,0,e.width)),"Extras snapshot pixels");}
    public static void main(String[] args)throws Exception{
        Path out=Paths.get(args[1]);Files.createDirectories(out);System.setProperty("dc.saveRoot",out.resolve("saves").toString());
        boolean media=args.length>2&&args[2].equals("--media");
        try(DesktopRuntime app=new DesktopRuntime(Paths.get(args[0]),!media)){
            DesktopEngine e=app.engine;settle(e);
            if(e instanceof DesktopDd){
                choose(e,"extra");choose(e,"musmode");DesktopDd d=(DesktopDd)e;check(d.host.list.items.size()==88,"DD music catalogue count");scrollbar(e);shot(e,out,"music");
                choose(e,"@slot:1");check(e.audio(0).equals(e.resources.named("music",2))&&e.audioActive(0),"DD selected track");
                choose(e,"allloop");int before=e.audioGeneration(0);e.audioCompleted(0,before);settle(e);check(e.audioGeneration(0)>before&&e.audio(0).equals(e.resources.named("music",3)),"DD EOF advances original script");
                choose(e,"alloff");choose(e,"flag01");check(d.host.state.musicFavorites[1]==1,"DD favorite flag");choose(e,"listplay");choose(e,"next");check(e.audio(0).equals(e.resources.named("music",1)),"DD favorite filter");
                choose(e,"alloff");choose(e,"ramloop");check(!Arrays.equals(d.host.musicRoom.order,Dc3Music.TRACKS),"DD random order");Set<Integer> ids=new HashSet<>();for(int id:d.host.musicRoom.order)ids.add(id);check(ids.size()==88,"DD shuffle no duplicates");
                e.scroll(1000);settle(e);check(d.host.read(99,246)==80,"DD list scroll endpoint");choose(e,"@slot:7");check(e.audio(0).equals(e.resources.named("music",283)),"DD final track");shot(e,out,"music-last");roundtrip(e);
                choose(e,"stop");check(!e.audioActive(0),"DD stop");choose(e,"return");choose(e,"cgmode");d=(DesktopDd)e;check(d.host.list.items.size()>12,"DD CG list");shot(e,out,"cg-locked");choose(e,"@slot:0");check(e.vm().scriptName().equalsIgnoreCase("CG_ALL"),"Locked CG stays in original menu");e.scroll(1);check(d.host.read(99,246)==4,"DD four-column scroll");shot(e,out,"cg-scrolled");roundtrip(e);choose(e,"return");
                // Test unlock and viewer with a local audit-only ordinary CG flag.
                d.host.state.write(97,0,1);choose(e,"cgmode");e.scroll(-1000);settle(e);choose(e,"@slot:0");check(!d.host.scene.slots[1].resource.toLowerCase(Locale.ROOT).contains("cgall"),"Unlocked CG enters viewer");e.click(0,0,true);settle(e);check(d.host.list.active(),"DD CG viewer return");choose(e,"return");
            }else{
                DesktopLegacy d=(DesktopLegacy)e;check(d.host() instanceof PcHost&&((PcHost)d.host()).second,"PC2 extras profile");choose(e,"03");choose(e,"extra");choose(e,"bgm");PcHost h=(PcHost)d.host();check(h.list.items.size()>14,"PC2 music list");scrollbar(e);shot(e,out,"music");choose(e,"@slot:0");check(e.audio(0).equals(e.resources.named("music",1)),"PC2 first track");
                choose(e,"all_play_mode");int gen=e.audioGeneration(0);e.audioCompleted(0,gen);settle(e);check(e.audioGeneration(0)>gen,"PC2 EOF script callback");
                choose(e,"sel_statdown");check(h.read(99,246)==2,"PC2 two-column paging");choose(e,"@slot:13");check(e.vm().variables.read(3,5)==15,"PC2 entry ID differs from visible row");e.scroll(1000);check(h.read(99,246)>0,"PC2 scroll endpoint");shot(e,out,"music-last");roundtrip(e);choose(e,"stop");check(!e.audioActive(0),"PC2 stop");choose(e,"return");
            }
            long frames=0;if(media){choose(e,e instanceof DesktopDd?"musmode":"bgm");e.scroll(-1000);settle(e);choose(e,"@slot:0");app.media.sync();long end=System.currentTimeMillis()+3000;while(System.currentTimeMillis()<end){Thread.sleep(50);app.media.sync();}frames=app.media.audioFrames(0);check(frames>44100,"Gallery track reaches real audio device");if(e instanceof DesktopDd){DesktopDd dd=(DesktopDd)e;dd.host.musicRoom.seek=50;dd.host.musicRoom.seekGeneration++;app.media.sync();check(dd.host.musicRoom.duration>0&&dd.host.musicRoom.position*100/dd.host.musicRoom.duration>=49,"Gallery seek changes real audio cursor");}shot(e,out,"media-playing");}
            Files.write(out.resolve("result.json"),Json.text(Json.obj("profile",e.resources.profile.id,"checks",checks,"pass",true,"media_frames",frames,"media_completion","controlled callback; original executables not run")).getBytes(java.nio.charset.StandardCharsets.UTF_8));System.out.println("PASS "+checks+" extras checks");
        }
    }
}
