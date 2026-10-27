package org.circus.core;
import java.io.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Exhaust reachable choice sequences from fresh route state. Media events are simulated. */
public final class BranchAudit {
    private static final String[] ENTRIES={"DD_1_0611_A1","DD_2_1128_E1","DD_3_0211_A1","DD_4_0908_E1","DD_5_0924_B1","DD_6_1211_C1","DD_7_0121_A1"};
    private static final class Branch {byte[] snapshot;int option;String decisions;Branch(byte[] s,int o,String d){snapshot=s;option=o;decisions=d;}}
    public static String run(Dc3Resources data,TextCodec codec,boolean decodeMedia)throws Exception {
        StringBuilder log=new StringBuilder();Set<String> images=new TreeSet<>(),audio=new TreeSet<>();
        for(String entry:ENTRIES){ArrayDeque<Branch> pending=new ArrayDeque<>();pending.add(new Branch(null,0,""));int segments=0,endings=0,steps=0;Set<String> points=new TreeSet<>(),scripts=new TreeSet<>();
            while(!pending.isEmpty()) {
                if(++segments>10000)throw new IOException("Branch segment limit: "+entry);
                Branch branch=pending.removeLast();Dc3Host h=new Dc3Host(data,codec);MesVm vm=new MesVm(data.scripts(),h,entry);
                if(branch.snapshot!=null){vm.restore(branch.snapshot);if(!Arrays.equals(branch.snapshot,vm.snapshot()))throw new IOException("Branch snapshot round trip differs");vm.choose(h.choices().get(branch.option).label(),branch.option);}
                while(true){if(++steps>2000000)throw new IOException("Branch step limit: "+entry);scripts.add(vm.scriptName());MesVm.Status status=vm.run(1);
                    if(status==MesVm.Status.FAULT)throw new IOException(vm.error()+" choices="+branch.decisions);
                    if(status==MesVm.Status.HALTED){if(!h.titleRequested)throw new IOException("Unexpected stream halt");endings++;log.append("PASS ").append(entry).append(" choices=").append(branch.decisions.isEmpty()?"none":branch.decisions).append(" -> title\n");break;}
                    if(status==MesVm.Status.WAIT_CHOICE){points.add(vm.scriptName()+":"+vm.offset());byte[] snapshot=vm.snapshot();for(int i=h.choices().size()-1;i>=0;i--)pending.add(new Branch(snapshot,i,branch.decisions+(branch.decisions.isEmpty()?"":",")+i));break;}
                    if(status==MesVm.Status.WAIT_HOST){
                        for(Dc3Scene.Layer layer:h.scene.slots)if(!layer.resource.isEmpty()&&images.add(layer.resource)&&decodeMedia)data.image(layer.resource);
                        for(Dc3Scene.Quad q:h.scene.quads)if(images.add(q.resource)&&decodeMedia)data.image(q.resource);
                        if(h.scene.fade.active()&&!h.scene.fade.mask.isEmpty()&&images.add(h.scene.fade.mask)&&decodeMedia)data.image(h.scene.fade.mask);
                        for(String ref:h.credits.resources())if(images.add(ref)&&decodeMedia)data.image(ref);
                        for(String ref:h.audio)if(!ref.isEmpty()&&audio.add(ref)&&decodeMedia)data.audio(ref);
                        for(Dc3Performance.Event event:h.performance.events)if(!event.resource.isEmpty()&&audio.add(event.resource)&&decodeMedia)data.audio(event.resource);
                        for(int ch=0;ch<6;ch++)h.audioCompleted(ch,h.audioGeneration[ch]);vm.signalHost(h.waitTicket(),MesVm.HostSignal.MEDIA_COMPLETE);vm.elapse(Math.max(1,h.remainingMillis()));if(vm.status()==MesVm.Status.WAIT_HOST){vm.signalHost(h.waitTicket(),MesVm.HostSignal.TEXT_COMPLETE);vm.signalHost(h.waitTicket(),MesVm.HostSignal.ADVANCE);}
                    }
                }
            }
            log.append("COVERAGE ").append(entry).append(" choice_points=").append(points.size()).append(" endings=").append(endings).append(" scripts=").append(scripts.size()).append(" segments=").append(segments).append(" steps=").append(steps).append('\n');
        }
        return log+"PASS images="+images.size()+" audio="+audio.size()+" media_decode="+decodeMedia+"; default state and fixed RNG; media completion simulated\n";
    }
    public static void main(String[] args)throws Exception {
        Dc3Resources data=new Dc3Resources(new GameResources(new LocalGameStorage(Paths.get(args[0]))));TextCodec codec;
        try(DataInputStream in=new DataInputStream(Files.newInputStream(Paths.get(args[1])))){int n=in.readInt();if(n<0||n>1024*1024)throw new IOException("Mapping limit");byte[] b=new byte[n];in.readFully(b);codec=new TextCodec(new String(b,StandardCharsets.UTF_8),new String(Binary.read(in,1024*1024),StandardCharsets.UTF_8));}
        System.out.print(run(data,codec,Arrays.asList(args).contains("--media")));
    }
}
