package org.circus.core;
import java.nio.file.*;
import java.util.*;
import java.io.*;

/** Runs the unmodified startup scripts in our VM; never launches the original EXE. */
public final class OriginalUiAudit {
    public static void main(String[] args)throws Exception{
        Dc3Resources data=new Dc3Resources(new GameResources(new LocalGameStorage(Paths.get(args[0]))));
        Dc3Host host=new Dc3Host(data,TextCodec.identity(),true);MesVm vm=new MesVm(data.scripts(),host,"START");
        Set<String> scripts=new LinkedHashSet<>(),images=new HashSet<>(),audio=new HashSet<>();int action=1,waits=0;
        for(int step=0;step<50000;step++){
            scripts.add(vm.scriptName());MesVm.Status status=vm.run(1);
            if(status==MesVm.Status.FAULT)throw new IOException(vm.error());
            if(status==MesVm.Status.HALTED){if(host.exitRequested&&action==args.length){System.out.println("PASS original game exit "+vm.scriptName()+" @"+Integer.toHexString(vm.offset()));return;}throw new IOException("Unexpected script stop "+vm.scriptName());}
            if(status!=MesVm.Status.WAIT_HOST)continue;
            for(String image:host.ui.resources())if(images.add(image))data.image(image);
            for(Dc3Scene.Layer layer:host.scene.slots)if(!layer.resource.isEmpty()&&images.add(layer.resource))data.image(layer.resource);
            for(String sound:host.audio)if(!sound.isEmpty()&&audio.add(sound))data.audio(sound);
            if(host.dialogue.waiting()&&action<args.length&&args[action].equals("@story")){System.out.println("PASS original menu -> story "+vm.scriptName()+" @"+Integer.toHexString(vm.offset())+" decoded_images="+images.size()+" decoded_audio="+audio.size());return;}
            if(host.ui.waiting){
                System.out.println("BG "+host.scene.slots[1].resource+" x="+host.scene.slots[1].x+" y="+host.scene.slots[1].y);
                System.out.println("MENU "+vm.scriptName()+" @"+Integer.toHexString(vm.offset())+" buttons="+host.ui.current.buttons.size()+" scripts="+scripts);
                for(Dc3Ui.Button b:host.ui.current.buttons)System.out.println("  "+b.action+" rect="+b.left+","+b.top+","+b.right+","+b.bottom);
                byte[] saved=vm.snapshot();Dc3Host h=new Dc3Host(data,TextCodec.identity(),true);MesVm v=new MesVm(data.scripts(),h,"START");v.restore(saved);if(!Arrays.equals(saved,v.snapshot()))throw new IOException("UI snapshot mismatch");vm=v;host=h;
                if(action>=args.length)return;
                String expected=args[action++];int index=-1;for(int n=0;n<host.ui.current.buttons.size();n++)if(host.ui.current.buttons.get(n).action.equals(expected))index=n;
                if(index<0)throw new IOException("Missing menu action "+expected);
                if(vm.activateMenu(host.waitTicket()-1,index))throw new IOException("Stale UI event accepted");
                if(!vm.activateMenu(host.waitTicket(),index))throw new IOException("UI event refused");continue;
            }
            waits++;for(int ch=0;ch<6;ch++)host.audioCompleted(ch,host.audioGeneration[ch]);
            vm.signalHost(host.waitTicket(),MesVm.HostSignal.MEDIA_COMPLETE);vm.elapse(Math.max(1,host.remainingMillis()));
            vm.signalHost(host.waitTicket(),MesVm.HostSignal.TEXT_COMPLETE);vm.signalHost(host.waitTicket(),MesVm.HostSignal.ADVANCE);
            if(waits>10000)throw new IOException("UI audit wait limit");
        }
        throw new IOException("UI audit instruction limit");
    }
}
