package org.circus.core;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;

/** Actual title -> route -> original galleries, plus durable upgrade recovery.
 * No unlock flags are injected into the progression being verified. */
public final class DdGalleryAudit {
    private final DesktopDd e;private int checks,lines;private final StringBuilder log=new StringBuilder();
    public DdGalleryAudit(DesktopDd engine){e=engine;}
    private void check(boolean value,String why)throws IOException{if(!value)throw new IOException(why+" at "+e.vm().scriptName()+"@"+Integer.toHexString(e.vm().offset()));checks++;log.append("PASS ").append(why).append('\n');}
    private static List<String> actions(DesktopDd e){List<String> a=new ArrayList<>();for(DesktopEngine.Control c:e.controls())a.add(c.action);return a;}
    private static void step(DesktopDd e)throws Exception{for(int ch=0;ch<6;ch++)e.audioCompleted(ch,e.audioGeneration(ch));e.vm().signalHost(e.ticket(),MesVm.HostSignal.MEDIA_COMPLETE);e.tick(1000);}
    private static void advance(DesktopDd e)throws IOException{e.vm().signalHost(e.ticket(),MesVm.HostSignal.TEXT_COMPLETE);e.vm().signalHost(e.ticket(),MesVm.HostSignal.ADVANCE);}
    private static void menu(DesktopDd e)throws Exception{
        for(int n=0;n<3000;n++){step(e);if(e.menuWaiting())return;if(e.textWaiting())throw new IOException("Unexpected story while waiting for original gallery menu");advance(e);}throw new IOException("Menu budget");
    }
    private static void choose(DesktopDd e,String action)throws Exception{e.choose(action);menu(e);}
    private static int count(Dc3State s,int bank,int size)throws IOException{int count=0;for(int n=0;n<size;n++)if(s.read(bank,n)!=0)count++;return count;}
    private static void shot(DesktopDd e,Path out,String name)throws Exception{BufferedImage image=e.render();try(OutputStream stream=Files.newOutputStream(out.resolve(name+".png"))){ImageIO.write(image,"png",stream);}image.flush();}
    private static int named(CircusResources r,String category,String name)throws IOException{for(NamesCatalog.Entry entry:r.names.table(category))if(entry.name.equalsIgnoreCase(name))return entry.index;return -1;}
    public String run(Path out)throws Exception{
        Files.createDirectories(out);menu(e);byte[] title=e.snapshot();
        check(count(e.host.state,97,1200)==0&&count(e.host.state,98,650)==0,"fresh install has no earned gallery flags");
        choose(e,"extra");choose(e,"cgmode");check(e.host.list.items.stream().noneMatch(i->i.action.matches("cg[0-9]+")),"fresh original CG list stays locked");shot(e,out,"cg-before");choose(e,"return");choose(e,"scemode");check(e.host.list.items.stream().noneMatch(i->i.action.matches("sce[0-9]+")),"fresh original scenario list stays locked");shot(e,out,"scene-before");e.restore(title);
        byte[] olderStory=null,recoveryStory=null;Set<String> scripts=new LinkedHashSet<>();String lastLine="",lastWhere="";int stuck=0;boolean started=false,ended=false;
        for(int n=0;n<100000;n++){
            step(e);scripts.add(e.vm().scriptName());
            String location=e.vm().scriptName()+":"+e.vm().offset();if(location.equals(lastWhere)){if(++stuck>1000)throw new IOException("Stalled "+location+" status="+e.vm().status()+" text="+e.textWaiting()+" wait="+e.host.remainingMillis()+" host="+e.host.waiting()+" save="+e.host.saveRequest+" load="+e.host.loadRequest+" movie="+e.movie());}else{lastWhere=location;stuck=0;}
            if(e.menuWaiting()){
                List<String> a=actions(e);String chosen=null;
                if(started&&lines>0&&(a.contains("start")||a.contains("sce_start"))){ended=true;break;}
                for(String preferred:new String[]{"window","03","sce_start","start","sel01","sceselect01"})if(a.contains(preferred)){chosen=preferred;break;}
                if(chosen==null&&!a.isEmpty())chosen=a.get(0);if(chosen==null)throw new IOException("Unresolved route menu");
                if(chosen.equals("start")||chosen.equals("sce_start"))started=true;e.choose(chosen);continue;
            }
            if(e.textWaiting()){
                String where=e.vm().scriptName()+":"+e.vm().offset();if(!where.equals(lastLine)){lines++;lastLine=where;}
                if(olderStory==null)olderStory=e.snapshot();
                if(recoveryStory==null&&e.host.history.entries.stream().map(h->h.script).distinct().count()>1&&count(e.host.state,97,1200)>0)recoveryStory=e.snapshot();
            }
            advance(e);
        }
        check(ended&&lines>1000,"original Rikka route completes and returns to title");
        int cgs=count(e.host.state,97,1200),scenes=count(e.host.state,98,650);
        check(cgs>10&&cgs<e.resources.names.table("images").size(),"only visited CG variations are earned");
        int expectedScenes=0;for(String script:scripts){int id=named(e.resources,"scenario_scripts",script);if(id>=0){check(e.host.state.read(98,id)==1,"completed scenario recorded: "+script);expectedScenes++;}}
        check(scenes==expectedScenes&&scenes<e.resources.names.table("scenario_scripts").size(),"unplayed scenarios remain locked");
        check(e.host.state.read(97,0)==1&&e.host.state.read(98,0)==1,"first route CG and scene unlocked by actual execution");
        byte[] completed=e.snapshot();e.restore(olderStory);check(count(e.host.state,97,1200)==cgs&&count(e.host.state,98,650)==scenes,"loading an early story cannot erase later gallery progress");e.restore(completed);
        check(recoveryStory!=null,"old-save recovery fixture contains actual history from successive scenes");
        // Isolated files: never use the player's save namespace for durability tests.
        String previousRoot=System.getProperty("dc.saveRoot");Path root=out.resolve("durable-"+System.nanoTime());System.setProperty("dc.saveRoot",root.toString());
        try{
            DesktopDd durable=new DesktopDd(e.resources,e.codec,false);durable.restore(completed);durable.host.state.write(99,7,37);durable.persist();
            DesktopDd restarted=new DesktopDd(e.resources,e.codec,false);menu(restarted);
            check(count(restarted.host.state,97,1200)==cgs&&count(restarted.host.state,98,650)==scenes,"earned flags survive disk persistence and a new START");
            restarted.restore(olderStory);check(restarted.host.state.read(99,7)==37&&count(restarted.host.state,97,1200)==cgs,"old save preserves current preferences and global gallery");
            restarted.restore(completed);choose(restarted,"extra");choose(restarted,"cgmode");
            check(restarted.host.list.items.get(0).action.equals("cg0"),"original CG script exposes earned thumbnail action");shot(restarted,out,"cg-after");
            restarted.choose("@slot:0");for(int n=0;n<2000;n++){step(restarted);if(named(e.resources,"images",restarted.host.scene.slots[1].resource)>=0)break;advance(restarted);}
            check(named(e.resources,"images",restarted.host.scene.slots[1].resource)>=0,"earned CG opens its original full image");shot(restarted,out,"cg-viewer");
            restarted.restore(completed);choose(restarted,"extra");choose(restarted,"scemode");
            check(restarted.host.list.items.get(0).action.equals("sce0"),"original Scene Jump exposes completed scene action");shot(restarted,out,"scene-after");
            restarted.choose("@slot:0");for(int n=0;n<3000;n++){step(restarted);if(restarted.textWaiting())break;if(restarted.menuWaiting()){List<String> a=actions(restarted);if(a.contains(":y"))restarted.choose(":y");else throw new IOException("Unexpected scene confirmation "+a);}else advance(restarted);}
            check(restarted.textWaiting()&&restarted.vm().scriptName().equalsIgnoreCase(e.resources.named("scenario_scripts",0)),"Scene Jump starts original selected scenario");
            recovery(out,recoveryStory);
        }finally{if(previousRoot==null)System.clearProperty("dc.saveRoot");else System.setProperty("dc.saveRoot",previousRoot);}
        log.append("DD GALLERY PASS CHECKS ").append(checks).append(" LINES ").append(lines).append(" CG ").append(cgs).append(" SCENES ").append(scenes).append('\n');
        Files.write(out.resolve("gallery-result.json"),(Json.text(Json.obj("pass",true,"checks",checks,"dialogues",lines,"cg_flags",cgs,"scene_flags",scenes,"scripts",scripts,"windows_game_executed",false,"media_completion_simulated",true))+"\n").getBytes("UTF-8"));Files.write(out.resolve("gallery-result.txt"),log.toString().getBytes("UTF-8"));return log.toString();
    }
    private void recovery(Path out,byte[] story)throws Exception{
        System.setProperty("dc.saveRoot",out.resolve("upgrade-"+System.nanoTime()).toString());
        DesktopDd layout=new DesktopDd(e.resources,e.codec,true);Files.createDirectories(layout.saveDirectory);
        Dc3Host old=new Dc3Host(e.resources,e.codec,true);MesVm vm=new MesVm(e.resources.scripts(),old,"START");vm.restore(story);
        int oldCg=-1,completedScene=-1;String previous="";
        for(Dc2History.Entry entry:old.history.entries){
            if(!previous.isEmpty()&&!previous.equalsIgnoreCase(entry.script))completedScene=named(e.resources,"scenario_scripts",previous);previous=entry.script;
            if(entry.scene.length>0){Dc3Scene.Frame frame=new Dc3Scene.Frame();frame.load(new DataInputStream(new ByteArrayInputStream(entry.scene)));int id=named(e.resources,"images",frame.layers[1].resource);if(id>=0)oldCg=id;}
        }
        check(oldCg>=0&&completedScene>=0,"legacy fixture retains a CG frame and a completed scenario transition");
        // Reproduce <=0.12.4: actual route snapshot with missing gallery bits, not an all-unlock fixture.
        for(int n=0;n<1200;n++)old.state.write(97,n,0);for(int n=0;n<650;n++)old.state.write(98,n,0);
        DesktopSave saved=new DesktopSave();saved.state=vm.snapshot();byte[] bytes=saved.encode();Path slot=layout.saveDirectory.resolve("slot-4.sav");DesktopEngine.atomic(slot,bytes);
        DesktopDd upgraded=new DesktopDd(e.resources,e.codec,false);menu(upgraded);
        check(upgraded.host.state.read(97,oldCg)==1&&upgraded.host.state.read(98,completedScene)==1,"upgrade recovers retained CG and completed scene before opening extras");
        check(Arrays.equals(bytes,Files.readAllBytes(slot)),"upgrade leaves the old save byte-for-byte intact");
        check(count(upgraded.host.state,97,1200)<count(e.host.state,97,1200)&&count(upgraded.host.state,98,650)<count(e.host.state,98,650),"recovery cannot invent the unrecorded remainder of a route");
        DesktopDd again=new DesktopDd(e.resources,e.codec,false);menu(again);check(again.host.state.read(97,oldCg)==1&&again.host.state.read(98,completedScene)==1,"recovered flags survive restart and migration is idempotent");
    }
}
