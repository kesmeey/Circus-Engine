package org.circus.core;
import java.io.*;
import java.nio.file.*;
import java.util.*;

public final class Dc2SystemAudit {
    static Dc2Session s;
    static void pump(boolean body)throws Exception{
        for(int n=0;n<50000;n++){
            Dc2Host h=s.host;MesVm.Status status=s.vm.run(1);if(status==MesVm.Status.FAULT)throw new IOException(s.vm.error());
            if(h.returnRequested||h.saveRequest>=0||h.loadRequest>=0||h.ui.waiting||body&&h.textWaiting){System.out.println(s.vm.scriptName()+" @"+Integer.toHexString(s.vm.offset())+" buttons="+actions()+" grid="+h.gridWaiting+" save="+h.saveRequest+" load="+h.loadRequest);return;}
            if(status==MesVm.Status.HALTED)throw new IOException("Unexpected stop");
            for(int ch=0;ch<6;ch++)h.audioCompleted(ch,h.audioGeneration[ch]);s.vm.elapse(600000);s.vm.signalHost(h.waitTicket(),MesVm.HostSignal.MEDIA_COMPLETE);s.vm.signalHost(h.waitTicket(),MesVm.HostSignal.TEXT_COMPLETE);if(!body||!h.textWaiting)s.vm.signalHost(h.waitTicket(),MesVm.HostSignal.ADVANCE);
        }throw new IOException("System budget");
    }
    static String actions(){List<String> r=new ArrayList<>();for(Dc2Ui.Button b:s.host.ui.buttons)r.add(b.action);return r.toString();}
    static void tap(String a)throws Exception{int index=-1;for(int n=0;n<s.host.ui.buttons.size();n++)if(s.host.ui.buttons.get(n).action.equals(a))index=n;if(index<0||!s.vm.activateMenu(s.host.waitTicket(),index))throw new IOException("Missing action "+a);pump(false);}
    public static void main(String[] args)throws Exception{
        CircusResources data=new CircusResources(new GameResources(new LocalGameStorage(Paths.get(args[0]))),Profiles.get("dc2dm-9d72"));s=new Dc2Session(data,TextCodec.identity());pump(false);
        if(args.length>1){tap(args[1]);return;}
        int start=0;for(int n=0;n<s.host.ui.buttons.size();n++)if(s.host.ui.buttons.get(n).action.equals("sce_start"))start=n;s.vm.activateMenu(s.host.waitTicket(),start);pump(true);
        String name=s.vm.scriptName(),body=s.host.body;int pc=s.vm.offset();s.openSystem("ADVMENU");pump(false);tap("save");if(!s.host.gridWaiting)throw new IOException("No save grid");s.vm.activateGrid(s.host.waitTicket(),3);pump(false);tap(":y");if(s.host.saveRequest!=4)throw new IOException("Native slot mapping");byte[] saved=s.storySnapshot();s.host.availableSaves.add(4);s.host.completeSave();s.vm.elapse(0);pump(false);if(s.host.saveRequest==3){s.host.completeSave();s.vm.elapse(0);pump(false);}tap("return");tap("return");if(!s.host.returnRequested)throw new IOException("No system return");s.returnToStory();if(!name.equals(s.vm.scriptName())||pc!=s.vm.offset()||!body.equals(s.host.body))throw new IOException("Caller changed");
        s.openSystem("ADVMENU");pump(false);tap("load");s.vm.activateGrid(s.host.waitTicket(),3);pump(false);tap(":y");if(s.host.loadRequest!=4)throw new IOException("Load target changed");s.restore(saved);if(!body.equals(s.host.body)||pc!=s.vm.offset())throw new IOException("Loaded wrong position");
        s.openSystem("ADVMENU");pump(false);tap("config");
        System.out.println("PASS original title/story/menu/manual save/load and system return; media completion simulated");
    }
}
