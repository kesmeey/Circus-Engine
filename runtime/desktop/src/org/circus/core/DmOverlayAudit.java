package org.circus.core;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;

/** Original title/choices to the reported Nanaka scene; audio/movie completion simulated. */
public final class DmOverlayAudit {
    private final DesktopLegacy e;private int checks,lines;private final StringBuilder log=new StringBuilder();
    public DmOverlayAudit(DesktopLegacy engine){e=engine;}
    private void check(boolean yes,String what)throws IOException{if(!yes)throw new IOException(what+" at "+e.vm().scriptName()+"@"+Integer.toHexString(e.vm().offset()));checks++;log.append("PASS ").append(what).append('\n');}
    private void tick()throws Exception{for(int c=0;c<6;c++)if(!e.audioLoop(c))e.audioCompleted(c,e.audioGeneration(c));e.vm().signalHost(e.ticket(),MesVm.HostSignal.MEDIA_COMPLETE);e.tick(600000);}
    private void seek()throws Exception{
        String previous="";
        for(int n=0;n<200000;n++){
            tick();String script=e.vm().scriptName(),where=script+"@"+e.vm().offset();
            if(script.equalsIgnoreCase("SIN_0810_F12")&&e.host().textWaiting&&e.vm().offset()==0x2c59)return;
            if(e.menuWaiting()){
                List<String> actions=new ArrayList<>();for(DesktopEngine.Control c:e.controls())actions.add(c.action);
                String selected=null;for(String a:new String[]{"window","03","sce_start","start","sel01","sceselect01"})if(actions.contains(a)){selected=a;break;}
                if(selected==null&&!actions.isEmpty())selected=script.equalsIgnoreCase("SIN_0810_F1")&&actions.contains("sel1b")?"sel1b":actions.get(0);
                if(selected==null)throw new IOException("No route action "+where);
                log.append("CHOICE ").append(script).append(' ').append(selected).append('\n');e.choose(selected);continue;
            }
            if(e.host().textWaiting&&!where.equals(previous)){lines++;previous=where;}
            e.vm().signalHost(e.ticket(),MesVm.HostSignal.TEXT_COMPLETE);
            if(e.host().textWaiting||e.host().presentationWaiting||e.host().remainingMillis()<0||script.equalsIgnoreCase("MAIN"))e.vm().signalHost(e.ticket(),MesVm.HostSignal.ADVANCE);
        }throw new IOException("Reported overlay scene not reached");
    }
    public String run(Path output)throws Exception{
        Files.createDirectories(output);check(e.resources.profile.id.equals("dc2dm-9d72"),"original DM profile");seek();
        check(e.body().contains("要幸福"),"original title and choices reach reported sentence");
        Dc2Scene.Frame f=e.host().scene.shown;String actor=f.slots[2],accessory=f.slots[6];
        check(actor.replace('\\','/').equalsIgnoreCase("BST/NNK_0C_202_B@,BST/NNK_C")&&accessory.replace('\\','/').equalsIgnoreCase("BST/E_NNK_HAT2,BST/E_NNK"),"MES retains original actor and accessory references: "+actor+"; "+accessory);
        String resolved=e.resources.characterOverlay(actor,accessory);
        check(resolved.replace('\\','/').equalsIgnoreCase("BST/E_NNK_HAT2_B@,BST/E_NNK"),"native 428990 resolves close-up and night filter");
        Crx.Image cap=e.resources.image(resolved);
        check(cap.x==356&&cap.y==84&&cap.width==328&&cap.height==263,"close-up accessory uses native CRX geometry");
        check(Arrays.equals(f.actors[0],new int[]{380,-40,100,512,300,0}),"original actor transform is retained");
        BufferedImage image=e.render();int[] pixels=image.getRGB(0,0,e.width,e.height,null,0,e.width),cp=cap.argb(true);int tested=0;
        for(int y=0;y<cap.height;y++)for(int x=0;x<cap.width;x++){int dx=cap.x+380+x,dy=cap.y-40+y,c=cp[y*cap.width+x];if((c>>>24)==255&&dx>=0&&dx<e.width&&dy>=0&&dy<400){if(pixels[dy*e.width+dx]!=c)throw new IOException("Accessory pixel mismatch at "+dx+","+dy);tested++;}}
        check(tested>10000,"opaque hat pixels match native geometry and tint ("+tested+")");
        try(OutputStream png=Files.newOutputStream(output.resolve("reported-line.png"))){ImageIO.write(image,"png",png);}image.flush();
        Path save=output.resolve("reported-line.sav");e.save(save);byte[] snapshot=e.snapshot();e.restore(save);BufferedImage restored=e.render();
        check(Arrays.equals(pixels,restored.getRGB(0,0,e.width,e.height,null,0,e.width)),"save/load preserves corrected portrait pixels");restored.flush();
        // Existing saves store the unadjusted slot reference. Resolve from each frame,
        // including history and menu captures, rather than mutating or upgrading saves.
        check(e.host().scene.shown.slots[6].equals(accessory),"save format retains original accessory reference");
        e.action(":menu");for(int n=0;n<1000&&!e.menuWaiting();n++)tick();check(e.session.inSystem()&&e.menuWaiting(),"original menu opens at reported scene");
        e.click(0,0,true);for(int n=0;n<1000&&e.session.inSystem();n++)tick();
        check(!e.session.inSystem()&&e.body().contains("要幸福"),"menu returns to reported sentence");
        restored=e.render();check(Arrays.equals(pixels,restored.getRGB(0,0,e.width,e.height,null,0,e.width)),"menu round trip preserves hat alignment");restored.flush();
        e.restore(snapshot);log.append("DM OVERLAY PASS CHECKS ").append(checks).append("; dialogues before target ").append(lines).append("; media completion simulated; original EXE not executed.\n");
        Files.write(output.resolve("overlay-result.txt"),log.toString().getBytes("UTF-8"));return log.toString();
    }
}
