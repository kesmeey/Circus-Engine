package org.circus.core;
import java.io.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
/** Isolated entry coverage, not a claim of whole-game reachability or presentation fidelity. */
public final class ChapterAudit {
    public static void main(String[] args)throws Exception {
        Dc3Resources data=new Dc3Resources(new GameResources(new LocalGameStorage(Paths.get(args[0]))));
        TextCodec codec;
        try(DataInputStream in=new DataInputStream(Files.newInputStream(Paths.get(args[1])))){int n=in.readInt();byte[] b=new byte[n];in.readFully(b);codec=new TextCodec(new String(b,StandardCharsets.UTF_8),new String(Binary.read(in,1024*1024),StandardCharsets.UTF_8));}
        int passed=0,failed=0;
        for(NamesCatalog.Entry entry:data.names.table("scenario_scripts"))for(int branch=0;branch<2;branch++){
            if(entry.name.isEmpty()||entry.name.startsWith("/"))continue; // NAMES replay aliases, not filesystem paths
            Dc3Host h=new Dc3Host(data,codec);MesVm m=new MesVm(data.scripts(),h,entry.name);int steps=0;
            while(steps++<30000){
                MesVm.Status s=m.run(1);if(s==MesVm.Status.FAULT||s==MesVm.Status.HALTED||!m.scriptName().equals(entry.name))break;
                if(s==MesVm.Status.WAIT_CHOICE){int i=Math.min(branch,h.choices().size()-1);m.choose(h.choices().get(i).label(),i);}
                if(s==MesVm.Status.WAIT_HOST){for(int c=0;c<6;c++)h.audioCompleted(c,h.audioGeneration[c]);m.signalHost(h.waitTicket(),MesVm.HostSignal.MEDIA_COMPLETE);m.elapse(Math.max(1,h.remainingMillis()));m.signalHost(h.waitTicket(),MesVm.HostSignal.TEXT_COMPLETE);m.signalHost(h.waitTicket(),MesVm.HostSignal.ADVANCE);}
            }
            boolean ok=m.status()!=MesVm.Status.FAULT&&steps<=30000;if(ok)passed++;else failed++;
            System.out.println(entry.name+"\t"+branch+"\t"+(ok?"PASS":"FAIL")+"\t"+steps+"\t"+m.error());
        }
        System.out.println("TOTAL pass="+passed+" fail="+failed+" (entry-isolated, media completion simulated)");
    }
}
