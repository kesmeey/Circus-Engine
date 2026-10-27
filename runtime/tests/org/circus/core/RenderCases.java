package org.circus.core;
import java.io.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Private renderer checkpoints reached by original scripts, never distributed. */
public final class RenderCases {
    static Dc3Resources data;static TextCodec codec;static Path out;static Set<String> saved=new TreeSet<>();static List<String> report=new ArrayList<>();
    static void capture(String name,MesVm vm,long advance)throws Exception {
        if(!saved.add(name))return;Dc3Host h=new Dc3Host(data,codec);MesVm copy=new MesVm(data.scripts(),h,vm.scriptName());copy.restore(vm.snapshot());if(advance>0)copy.elapse(advance);
        Files.write(out.resolve(name+".cvs"),copy.snapshot());report.add(name+"\t"+vm.scriptName()+"\t0x"+Integer.toHexString(vm.offset())+"\t"+advance);
    }
    public static void main(String[] args)throws Exception {
        data=new Dc3Resources(new GameResources(new LocalGameStorage(Paths.get(args[0]))));out=Paths.get(args[2]);Files.createDirectories(out);
        try(DataInputStream in=new DataInputStream(Files.newInputStream(Paths.get(args[1])))){int n=in.readInt();byte[] b=new byte[n];in.readFully(b);codec=new TextCodec(new String(b,StandardCharsets.UTF_8),new String(Binary.read(in,1024*1024),StandardCharsets.UTF_8));}
        String[] entries={"DD_1_0611_A1","DD_2_1128_E1","DD_3_0211_A1","DD_4_0908_E1","DD_5_0924_B1","DD_6_1211_C1","DD_7_0121_A1"};
        for(int route=0;route<7;route++){Dc3Host h=new Dc3Host(data,codec);MesVm vm=new MesVm(data.scripts(),h,entries[route]);for(int step=0;step<100000;step++){
            MesVm.Status status=vm.run(1);if(status==MesVm.Status.FAULT)throw new IOException(vm.error());if(status==MesVm.Status.HALTED)break;
            if(status==MesVm.Status.WAIT_CHOICE){capture("choice",vm,0);vm.choose(h.choices().get(0).label(),0);}
            if(status==MesVm.Status.WAIT_HOST){
                if(h.dialogue.waiting()&&!h.scene.slots[1].resource.toUpperCase(Locale.ROOT).endsWith("BLACK"))capture("route"+(route+1),vm,0);
                if(h.scene.fade.active()&&h.scene.fade.mode>=3)capture("transition"+h.scene.fade.mode,vm,h.scene.fade.duration/2);
                if(h.scene.camera.zoom>100)capture("camera",vm,0);
                if(h.scene.novelMode&&h.dialogue.waiting())capture("novel",vm,0);
                if(h.scene.clouds)capture("clouds",vm,1000);if(h.scene.fog)capture("fog",vm,0);
                if(!h.scene.petals.particles.isEmpty())capture("particles"+h.scene.petals.effect,vm,1000);
                for(Dc3Scene.Layer l:h.scene.slots){if(l.resource.contains("@,"))capture("tone-night",vm,0);if(l.resource.contains("*,"))capture("tone-warm",vm,0);}
                if(!h.scene.slots[30].resource.isEmpty())capture("face",vm,0);
                if(h.credits.active()){capture("credits"+(route+1),vm,13000);capture("credits-end",vm,h.credits.end()-500);}
                if(!h.movie.isEmpty())capture("movie",vm,0);
                for(int ch=0;ch<6;ch++)h.audioCompleted(ch,h.audioGeneration[ch]);vm.signalHost(h.waitTicket(),MesVm.HostSignal.MEDIA_COMPLETE);vm.elapse(Math.max(1,h.remainingMillis()));if(vm.status()==MesVm.Status.WAIT_HOST){vm.signalHost(h.waitTicket(),MesVm.HostSignal.TEXT_COMPLETE);vm.signalHost(h.waitTicket(),MesVm.HostSignal.ADVANCE);}
            }
        }if(vm.status()!=MesVm.Status.HALTED)throw new IOException("Route did not finish "+route);}
        Files.write(out.resolve("cases.tsv"),report,StandardCharsets.UTF_8);System.out.println("PASS "+saved.size()+" reached checkpoints: "+saved);
    }
}
