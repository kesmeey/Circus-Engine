package org.circus.core;
import java.io.*;
import java.nio.file.*;
import java.util.*;

/** Original-data integration audit; it executes our interpreter, never a Windows EXE. */
public final class PcStartupAudit {
    public static void main(String[] args)throws Exception{
        GameResources files=new GameResources(new LocalGameStorage(Paths.get(args[0])));GameVersion v=GameVersion.detect(files);System.out.println("PROFILE "+v.profileId);
        CircusResources data=new CircusResources(files,v.profile());Dc2Session s=new Dc2Session(data,GameText.detect(files,v.profile()));
        Set<String> decoded=new TreeSet<>(),audio=new TreeSet<>(),scripts=new TreeSet<>();int action=1,lines=0;String previous="";
        for(int n=0;n<100000;n++){
            Dc2Host h=s.host;MesVm m=s.vm;if(scripts.add(m.scriptName()))System.out.println("SCRIPT "+m.scriptName());
            if(args.length>1&&args[1].equals("trace"))System.out.println(m.scriptName()+" @"+Integer.toHexString(m.offset()));
            MesVm.Status status=m.run(1);if(status==MesVm.Status.FAULT)throw new IOException(m.error());
            if(status==MesVm.Status.HALTED)throw new IOException("Unexpected stop "+m.scriptName());
            Set<String> needed=new HashSet<>();needed.add(h.scene.shown.slots[1]);for(int slot=2;slot<=9;slot++)needed.add(h.scene.shown.slots[slot]);
            for(Dc2Ui.Quad q:h.scene.shown.quads)if(q.type==0)needed.add(h.scene.shown.slots[q.slot()]);
            for(Dc2Ui.Button b:h.ui.buttons)for(Dc2Ui.Quad q:b.sprites)if(q.type==0)needed.add(h.scene.slots[q.slot()]);
            if(!h.body.isEmpty())for(int t=1;t<=3;t++)for(Dc2Ui.Quad q:h.ui.quadTemplates.get(t))if(q.type==0)needed.add(h.scene.slots[q.slot()]);
            for(String name:needed)if(!name.isEmpty()&&decoded.add(name)){Crx.Image image=data.image(name);image.argb();System.out.println("IMAGE "+name+" "+image.width+"x"+image.height);}
            if(h.ui.waiting){List<String> choices=new ArrayList<>();for(Dc2Ui.Button b:h.ui.buttons)choices.add(b.action);System.out.println("MENU "+m.scriptName()+" @"+Integer.toHexString(m.offset())+" "+choices);
                String target=m.scriptName().equalsIgnoreCase("START")?(((PcHost)h).second?"03":"window"):action<args.length?args[action++]:null;
                if(target==null&&lines>0&&!choices.isEmpty())target=choices.get(0);
                if(target==null){System.out.println("PASS original title/menu; decoded="+decoded.size());return;}
                int index=choices.indexOf(target);if(index<0)throw new IOException("Missing requested menu action "+target);m.activateMenu(h.waitTicket(),index);continue;
            }
            if(h.textWaiting&&!(m.scriptName()+"#"+m.offset()).equals(previous)){lines++;previous=m.scriptName()+"#"+m.offset();if(lines>=Integer.getInteger("pc.lines",20)){byte[] save=m.snapshot();s.restore(save);System.out.println("PASS "+lines+" dialogue waits and snapshot restore; decoded="+decoded.size()+" scripts="+scripts.size());return;}}
            if(((PcHost)h).hasTimedInput()&&!h.ui.actions[0].isEmpty()){m.activateMenu(h.waitTicket(),-2);continue;}
            for(int ch=0;ch<6;ch++){if(!h.audio[ch].isEmpty()&&audio.add(h.audio[ch])){Xpcm x=data.audio(h.audio[ch]);System.out.println("AUDIO "+h.audio[ch]+" "+x.codec);}h.audioCompleted(ch,h.audioGeneration[ch]);}
            m.elapse(600000);m.signalHost(h.waitTicket(),MesVm.HostSignal.MEDIA_COMPLETE);m.signalHost(h.waitTicket(),MesVm.HostSignal.TEXT_COMPLETE);m.signalHost(h.waitTicket(),MesVm.HostSignal.ADVANCE);
        }throw new IOException("PC startup audit budget exhausted "+s.vm.scriptName()+" @"+Integer.toHexString(s.vm.offset())+" "+s.vm.status()+" remaining="+s.host.remainingMillis());
    }
}

