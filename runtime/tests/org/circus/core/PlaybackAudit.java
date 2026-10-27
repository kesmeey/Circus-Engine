package org.circus.core;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Executes original MES in the portable VM, never launches a native EXE. No prose export. */
public final class PlaybackAudit {
    public static void main(String[] args)throws Exception {
        Dc3Resources resources=new Dc3Resources(new GameResources(new LocalGameStorage(Paths.get(args[0]))));
        TextCodec codec=TextCodec.identity();
        if(!args[1].equals("-"))try(DataInputStream in=new DataInputStream(Files.newInputStream(Paths.get(args[1])))){
            int n=in.readInt();if(n<0||n>1024*1024)throw new IOException("Mapping limit");byte[] b=new byte[n];in.readFully(b);codec=new TextCodec(new String(b,StandardCharsets.UTF_8),new String(Binary.read(in,1024*1024),StandardCharsets.UTF_8));
        }
        String entry=args.length>2?args[2]:"DD_1_0611_A1";
        int maxInstructions=args.length>3?Integer.parseInt(args[3]):100000;
        boolean media=Arrays.asList(args).contains("--media");int choiceIndex=Arrays.asList(args).contains("--last-choice")?49:0;
        Dc3Host host=new Dc3Host(resources,codec);MesVm vm=new MesVm(resources.scripts(),host,entry);
        int steps=0,waits=0,snapshots=0;Set<String> images=new TreeSet<>(),audio=new TreeSet<>(),scripts=new TreeSet<>();
        for(;steps<maxInstructions;steps++){
            if(Arrays.asList(args).contains("--single")&&!vm.scriptName().equals(entry))break;
            scripts.add(vm.scriptName());MesVm.Status status=vm.run(1);
            if(status==MesVm.Status.FAULT||status==MesVm.Status.HALTED)break;
            if(status==MesVm.Status.WAIT_CHOICE){
                byte[] saved=vm.snapshot();Dc3Host candidate=new Dc3Host(resources,codec);MesVm loaded=new MesVm(resources.scripts(),candidate,entry);loaded.restore(saved);
                if(!Arrays.equals(saved,loaded.snapshot()))throw new IOException("Choice save round trip differs");host=candidate;vm=loaded;snapshots++;
                int index=Math.min(choiceIndex,host.choices().size()-1);vm.choose(host.choices().get(index).label(),index);
            }
            if(status==MesVm.Status.WAIT_HOST){
                waits++;
                try{
                    for(Dc3Scene.Layer layer:host.scene.slots)if(!layer.resource.isEmpty()&&images.add(layer.resource)&&media)resources.image(layer.resource);
                    for(Dc3Scene.Quad q:host.scene.quads)if(images.add(q.resource)&&media)resources.image(q.resource);
                    if(host.scene.fade.active()&&!host.scene.fade.mask.isEmpty()&&images.add(host.scene.fade.mask)&&media)resources.image(host.scene.fade.mask);
                    for(String ref:host.credits.resources())if(images.add(ref)&&media)resources.image(ref);
                    for(String ref:host.audio)if(!ref.isEmpty()&&audio.add(ref)&&media)resources.audio(ref);
                    for(Dc3Performance.Event event:host.performance.events)if(!event.resource.isEmpty()&&audio.add(event.resource)&&media)resources.audio(event.resource);
                }catch(IOException e){throw new IOException(vm.scriptName()+" @0x"+Integer.toHexString(vm.offset())+": "+e.getMessage(),e);}
                if(waits==1||waits%20==0){
                    byte[] saved=vm.snapshot();Dc3Host candidate=new Dc3Host(resources,codec);MesVm loaded=new MesVm(resources.scripts(),candidate,entry);loaded.restore(saved);
                    if(!Arrays.equals(saved,loaded.snapshot()))throw new IOException("Save round trip differs");host=candidate;vm=loaded;snapshots++;
                }
                for(int channel=0;channel<6;channel++)host.audioCompleted(channel,host.audioGeneration[channel]);
                vm.signalHost(host.waitTicket(),MesVm.HostSignal.MEDIA_COMPLETE);
                vm.elapse(Math.max(1,host.remainingMillis()));if(vm.status()==MesVm.Status.WAIT_HOST){vm.signalHost(host.waitTicket(),MesVm.HostSignal.TEXT_COMPLETE);vm.signalHost(host.waitTicket(),MesVm.HostSignal.ADVANCE);}
            }
        }
        System.out.println("status="+vm.status()+" scripts="+scripts.size()+" steps="+steps+" waits="+waits+" snapshot_checks="+snapshots+" images="+images.size()+" audio="+audio.size());
        System.out.println("script="+vm.scriptName()+" absolute_pc=0x"+Integer.toHexString(vm.offset())+" error="+vm.error());
        System.out.println("scripts="+scripts);
        if(Arrays.asList(args).contains("--refs")){for(String s:images)System.out.println("IMAGE\t"+s);for(String s:audio)System.out.println("AUDIO\t"+s);}
        if(vm.status()==MesVm.Status.FAULT)System.exit(2);
    }
}
