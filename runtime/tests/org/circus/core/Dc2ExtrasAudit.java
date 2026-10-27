package org.circus.core;
import java.io.*;
import java.nio.file.*;
import java.util.*;

/** Original-data audits run only in the portable VM; fixture flags never touch the game. */
public final class Dc2ExtrasAudit {
    static Dc2Session s;static int checks;
    static void check(boolean value,String message)throws IOException{if(!value)throw new IOException(message);checks++;}
    static void pump(boolean body)throws Exception{
        for(int n=0;n<60000;n++){
            Dc2Host h=s.host;MesVm.Status status=s.vm.run(1);if(status==MesVm.Status.FAULT)throw new IOException(s.vm.error());
            if(h.ui.waiting||h.returnRequested||body&&(h.textWaiting||h.remainingMillis()<0&&!h.ui.actions[3].isEmpty())){if(h.textWaiting)s.captureHistory();return;}
            if(status==MesVm.Status.HALTED)throw new IOException("Unexpected end");
            for(int c=0;c<6;c++)h.audioCompleted(c,h.audioGeneration[c]);s.vm.elapse(600000);s.vm.signalHost(h.waitTicket(),MesVm.HostSignal.MEDIA_COMPLETE);s.vm.signalHost(h.waitTicket(),MesVm.HostSignal.TEXT_COMPLETE);if(!body||!h.textWaiting)s.vm.signalHost(h.waitTicket(),MesVm.HostSignal.ADVANCE);
        }throw new IOException("Extra UI budget");
    }
    static int index(String action)throws IOException{for(int n=0;n<s.host.ui.buttons.size();n++)if(s.host.ui.buttons.get(n).action.equals(action))return n;throw new IOException("Missing "+action+" in "+s.vm.scriptName());}
    static void tap(String action,boolean body)throws Exception{check(s.vm.activateMenu(s.host.waitTicket(),index(action)),"Action rejected");s.vm.elapse(0);pump(body);}
    static void restore(byte[] data)throws Exception{s.restore(data);}
    static void enter(String script)throws Exception{s.host.enterSystem();s.vm.enterSystem(script);pump(false);}
    public static void main(String[] args)throws Exception{
        CircusResources data=new CircusResources(new GameResources(new LocalGameStorage(Paths.get(args[0]))),Profiles.get("dc2dm-9d72"));s=new Dc2Session(data,TextCodec.identity());pump(false);byte[] title=s.vm.snapshot();
        tap("extra",false);tap("cgmode",false);check(s.host.ui.buttons.stream().noneMatch(b->b.action.matches("cg[0-9]+")),"New install CG must remain locked");tap("return",false);tap("scemode",false);check(s.host.ui.buttons.stream().noneMatch(b->b.action.matches("sce[0-9]+")),"Unseen scene must stay locked");
        restore(title);tap("sce_start",true);check(s.host.state.read(98,0)==1,"Original checkpoint must unlock scene 0");
        while(s.host.history.entries.size()<10||s.host.history.entries.stream().noneMatch(e->!e.voice.isEmpty())){s.vm.signalHost(s.host.waitTicket(),MesVm.HostSignal.ADVANCE);pump(true);if(s.host.ui.waiting)tap(s.host.ui.buttons.get(0).action,true);}
        int size=s.host.history.entries.size(),offset=s.vm.offset();String text=s.host.body,file=s.vm.scriptName();byte[] story=s.vm.snapshot();check(story.length<4*1024*1024,"Save budget");
        s.openSystem("LOG");pump(false);check(s.host.history.active&&s.host.history.entries.size()==size,"LOG retains dialogue");
        int voiced=-1;for(int n=0;n<size;n++)if(!s.host.history.entries.get(n).voice.isEmpty()){voiced=n;break;}
        s.host.history.cursor=Math.max(0,voiced-3);s.host.historyButtons();tap("@log:voice:"+voiced,false);check(s.host.audio[5].equals(s.host.history.entries.get(voiced).voice),"Replay original voice");tap("@log:exit",false);check(s.host.returnRequested,"LOG native return");s.returnToStory();check(s.vm.offset()==offset&&s.vm.scriptName().equals(file)&&s.host.body.equals(text),"LOG must not advance caller");
        s.openSystem("MANUAL");pump(false);tap("ret",false);s.returnToStory();check(s.vm.offset()==offset,"Manual return");
        restore(story);Dc2History.Entry earlier=s.host.history.entries.get(size-3);s.host.state.write(100,99,1);s.rewindHistory(size-3);check(s.host.body.equals(earlier.body)&&s.host.history.entries.size()==size-2,"Rewind restores selected dialogue");check(s.host.state.read(100,99)==1,"Rewind preserves global unlocks");
        s.vm.signalHost(s.host.waitTicket(),MesVm.HostSignal.ADVANCE);pump(true);int again=s.host.history.entries.size()-1;s.rewindHistory(again);check(s.host.textWaiting,"Repeated rewind after continuing");
        restore(story);check(s.host.history.entries.size()==size,"Save restores full history");
        // Fixture-only known unlock to verify image index mapping, before the full gallery sweep.
        s.host.command("dm.scene.preload",new int[]{1},data.named("images",0).getBytes(Binary.SJIS));check(s.host.state.read(97,0)==1,"Image load must unlock its NAMES bit");
        restore(title);tap("extra",false);tap("musmode",false);check(s.host.musicRoom.data.musicTitles.size()==73,"Native music table");tap("@music:track:2",false);check(s.host.audio[0].equals(data.named("music",3)),"Music order differs from NAMES index");
        tap("@music:all",false);int generation=s.host.audioGeneration[0];s.host.audioCompleted(0,generation);check(s.host.musicRoom.selected==3&&s.host.audioGeneration[0]>generation,"Automatic all-loop advance");s.host.audioCompleted(0,generation);check(s.host.musicRoom.selected==3,"Ignore stale completion");tap("@music:play",false);check(!s.host.audioActive[0]&&s.host.musicRoom.stopped,"Music stop");tap("@music:random",false);Set<Integer> order=new HashSet<>();order.add(s.host.musicRoom.selected);for(int k=0;k<72;k++){s.host.musicRoom.next(1);order.add(s.host.musicRoom.selected);}check(order.size()==73,"Shuffle plays each track once");tap("@music:exit",false);check(s.vm.scriptName().equals("MAIN"),"Music return to original EXTRA");
        for(String gallery:new String[]{"CG_MAR","CG_SIN","CG_SUC","CG_HMA","CG_HSI","CG_HSU","SCE_MAR","SCE_SIN","SCE_SUC","SCE_HMA","SCE_HSI","SCE_HSU"}){
            restore(title);for(int n=0;n<579;n++)s.host.state.write(97,n,1);for(int n=0;n<279;n++)s.host.state.write(98,n,1); // Test fixture only.
            s.vm.variables.write(3,5,gallery.endsWith("SIN")||gallery.endsWith("HSI")?1:gallery.endsWith("SUC")||gallery.endsWith("HSU")?2:0);s.vm.variables.write(3,22,gallery.contains("_H")?1:0);if(gallery.startsWith("SCE_"))s.vm.variables.write(9,7,1);enter(gallery);
            Set<String> decoded=new HashSet<>();Set<Integer> pages=new HashSet<>();while(pages.add(s.vm.variables.read(3,6))){
                for(Dc2Ui.Button b:s.host.ui.buttons)for(Dc2Ui.Quad q:b.sprites)if(q.type==0&&q.sx<0x4000&&decoded.add(s.host.scene.slots[q.slot()]))data.image(s.host.scene.slots[q.slot()]);
                check(s.host.ui.buttons.stream().anyMatch(b->b.action.matches("(cg|sce)[0-9]+")),"Unlocked gallery entries "+gallery);tap("page++",false);
            }
            System.out.println("PASS "+gallery+" pages="+pages.size());
            if(gallery.equals("CG_MAR")){tap("cg0",true);check(s.host.scene.slots[1].equals(data.named("images",0)),"CG full image");check(s.vm.activateMenu(s.host.waitTicket(),-1),"CG back");pump(false);check(s.host.ui.waiting,"CG returned to thumbnail page");}
            if(gallery.equals("SCE_MAR")){tap("sce0",false);tap(":y",true);check(s.vm.scriptName().equals("MAR_0101_F1")&&s.host.replayMode,"Scene replay uses original body");while(!s.host.ui.waiting){s.vm.signalHost(s.host.waitTicket(),MesVm.HostSignal.ADVANCE);pump(true);}check(s.vm.scriptName().equals("SCE_MAR"),"Replay returns through SC_END");}
        }
        System.out.println("PASS "+checks+" extras assertions; original data, simulated clocks/media; no PC process, no game writes");
    }
}
