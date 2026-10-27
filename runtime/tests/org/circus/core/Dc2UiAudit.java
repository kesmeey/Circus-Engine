package org.circus.core;
import java.io.*;
import java.nio.file.*;
import java.util.*;

/** Executes original DM MES only in our VM. Media completion is simulated. */
public final class Dc2UiAudit {
    public static void main(String[] args)throws Exception{
        CircusResources data=new CircusResources(new GameResources(new LocalGameStorage(Paths.get(args[0]))),Profiles.get("dc2dm-9d72"));
        Dc2Host host=new Dc2Host(data,TextCodec.identity());MesVm vm=new MesVm(data.scripts(),host,"START");int action=1;
        Set<String> scripts=new LinkedHashSet<>(),images=new HashSet<>();boolean assetAudit=Boolean.getBoolean("dm.assets");
        for(int step=0;step<1000000;step++){
            if(scripts.add(vm.scriptName()))System.out.println("SCRIPT "+vm.scriptName());
            MesVm.Status status=vm.run(1);if(status==MesVm.Status.FAULT)throw new IOException(vm.error());
            if(status==MesVm.Status.HALTED)throw new IOException("Unexpected stop "+vm.scriptName());
            if(status!=MesVm.Status.WAIT_HOST)continue;
            if(assetAudit){Set<String> visible=new HashSet<>();for(int slot:new int[]{1,2,3,4,5,6,7,8,9,18,19})visible.add(host.scene.shown.slots[slot]);for(Dc2Ui.Quad q:host.scene.shown.quads)if(q.type==0)visible.add(host.scene.shown.slots[q.slot()]);visible.addAll(host.credits.resources());for(String image:visible)if(!image.isEmpty()&&images.add(image)){data.image(image);if(images.size()%100==0)System.out.println("IMAGES "+images.size());}}

            if(host.ui.waiting){System.out.println("MENU "+vm.scriptName()+" @"+Integer.toHexString(vm.offset())+" background="+host.scene.slots[1]);
                for(Dc2Ui.Button b:host.ui.buttons)System.out.println("  "+b.action+" "+b.left+","+b.top+","+b.right+","+b.bottom);
                for(Dc2Ui.Quad q:host.ui.quads)check(data,host,q);for(Dc2Ui.Button b:host.ui.buttons)for(Dc2Ui.Quad q:b.sprites)check(data,host,q);
                byte[] saved=vm.snapshot();Dc2Host restored=new Dc2Host(data,TextCodec.identity());MesVm next=new MesVm(data.scripts(),restored,"START");next.restore(saved);if(!Arrays.equals(saved,next.snapshot()))throw new IOException("DM UI snapshot mismatch");vm=next;host=restored;
                if(action==args.length||step>10000&&vm.scriptName().equals("MAIN")){System.out.println("PASS original DM menu and snapshot, "+step+" instructions, "+scripts.size()+" scripts, "+images.size()+" images decoded; simulated media");return;}
                int index=args[action].equals("@first")?0:args[action].equals("@last")?host.ui.buttons.size()-1:-1;for(int n=0;n<host.ui.buttons.size();n++)if(host.ui.buttons.get(n).action.equals(args[action]))index=n;
                if(index<0)throw new IOException("Missing action "+args[action]);if(!args[action].startsWith("@"))action++;if(!vm.activateMenu(host.waitTicket(),index))throw new IOException("Refused action");
            }else{for(int n=0;n<6;n++)host.audioCompleted(n,host.audioGeneration[n]);vm.elapse(600000);vm.signalHost(host.waitTicket(),MesVm.HostSignal.MEDIA_COMPLETE);vm.signalHost(host.waitTicket(),MesVm.HostSignal.TEXT_COMPLETE);vm.signalHost(host.waitTicket(),MesVm.HostSignal.ADVANCE);}
        }throw new IOException("DM audit budget exceeded");
    }
    private static void check(CircusResources data,Dc2Host host,Dc2Ui.Quad q)throws IOException{
        if(q.type!=0||q.sx>=0x4000)return;String name=host.scene.slots[q.slot()];if(name.isEmpty())throw new IOException("Empty DM UI slot "+q.slot());Crx.Image image=data.image(name);
        if(q.sx<0||q.sy<0||q.sx+q.width>image.width||q.sy+q.height>image.height)throw new IOException("DM UI source bounds "+name+" "+q.sx+","+q.sy+","+q.width+","+q.height);
    }
}

