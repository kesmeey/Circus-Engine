package org.circus.core;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.awt.image.BufferedImage;

/** Deterministic shared-controller regression; media completion is simulated. */
public final class LegacyInputAudit {
    private final DesktopLegacy e;private int checks;private final StringBuilder log=new StringBuilder();
    public LegacyInputAudit(DesktopLegacy engine){e=engine;}
    private void check(boolean value,String message)throws IOException{if(!value)throw new IOException(message+" at "+where());checks++;log.append("PASS ").append(message).append('\n');}
    private String where(){return e.vm().scriptName()+"@"+Integer.toHexString(e.vm().offset());}
    private void tick(long ms)throws Exception{for(int ch=0;ch<6;ch++)if(!e.audioLoop(ch))e.audioCompleted(ch,e.audioGeneration(ch));e.vm().signalHost(e.ticket(),MesVm.HostSignal.MEDIA_COMPLETE);e.tick(ms);}
    private boolean has(String action){for(DesktopEngine.Control c:e.controls())if(c.action.equals(action))return true;return false;}
    public void seekDmTarget()throws Exception {
        for(int n=0;n<20000;n++){
            tick(100);
            if(e.vm().scriptName().equalsIgnoreCase("MAR_0114_A1")&&e.vm().offset()==0x4938){check(e.rapid&&!e.host().presentationWaiting&&!e.host().textWaiting&&e.vm().status()==MesVm.Status.RUNNING,"reproduce released presentation wait at MAR_0114_A1@4938");return;}
            if(e.menuWaiting()){boolean chosen=false;for(String a:new String[]{"window","03","sce_start","start","sel01","sceselect01"})if(has(a)){e.choose(a);chosen=true;break;}if(!chosen)throw new IOException("Unexpected menu at "+where());}
            else if(!e.body().isEmpty()||e.vm().scriptName().startsWith("MAR_"))e.rapid=true;
            else e.vm().signalHost(e.ticket(),MesVm.HostSignal.ADVANCE);
        }throw new IOException("DM regression target not reached");
    }
    private void menu()throws Exception {for(int n=0;n<500;n++){if(e.session.inSystem()&&e.menuWaiting())return;tick(100);}throw new IOException("Menu timeout at "+where());}
    private void back()throws Exception {e.click(0,0,true);for(int n=0;n<500;n++){tick(100);if(!e.session.inSystem())return;}throw new IOException("Menu return timeout at "+where());}
    public String run(Path output)throws Exception {
        Files.createDirectories(output);seekDmTarget();String body=e.body();
        e.click(0,0,true);check(!e.rapid&&!e.auto,"menu input immediately stops rapid/auto");menu();
        byte[] caller=e.snapshot();e.action(":backlog");check(Arrays.equals(caller,e.snapshot()),"repeated menu input preserves suspended story");
        back();check(e.vm().offset()==0x493b&&e.body().equals(body)&&e.host().textWaiting,"original menu returns to reported sentence and message wait");
        Path save=output.resolve("reported-line.sav");e.save(save);byte[] baseline=e.snapshot();int[] pixels=pixels();
        e.rapid=true;e.tick(0);e.action(":menu");e.restore(save);check(!e.rapid&&!e.auto,"loading cancels pending input and fast-forward");tick(0);check(!e.session.inSystem()&&e.vm().offset()==0x493b&&e.body().equals(body)&&Arrays.equals(pixels,pixels()),"load retains sentence, VM position and rendered pixels");
        for(String action:new String[]{":backlog",":torisetu",":option"}){e.action(action);menu();back();check(body.equals(e.body())&&e.vm().offset()==0x493b,action+" returns to the same sentence");}
        for(int n=0;n<20;n++){
            e.restore(baseline);e.rapid=true;tick(0);e.action(n%2==0?":menu":":backlog");menu();byte[] suspended=e.snapshot();back();check(Arrays.equals(suspended,e.snapshot()),"rapid/menu round trip "+(n+1)+" preserves VM and host state");
        }
        // Request a menu during a real scene/audio wait, rather than making it a no-op.
        e.restore(baseline);boolean deferred=false;
        for(int n=0;n<10000;n++){
            tick(1);
            if(!e.session.inSystem()&&!e.body().isEmpty()&&!e.textWaiting()&&!e.menuWaiting()&&e.host().remainingMillis()>1){
                String at=where();e.rapid=true;e.action(":menu");check(!e.session.inSystem()&&!e.rapid,"scene wait defers menu and stops rapid at "+at);menu();check(e.session.inSystem(),"deferred menu opens at next stable story wait");back();deferred=true;break;
            }
            if(e.menuWaiting())break;
            if(e.textWaiting())e.vm().signalHost(e.ticket(),MesVm.HostSignal.ADVANCE);
        }
        check(deferred,"exercise a real timed scene wait");
        // Continue through the rest of the reported chapter without manual menu injection.
        e.restore(baseline);e.rapid=true;int steps=0;
        while(steps++<20000&&e.vm().scriptName().equalsIgnoreCase("MAR_0114_A1")){tick(100);if(e.menuWaiting())throw new IOException("Unexpected choice before chapter end");}
        check(!e.vm().scriptName().equalsIgnoreCase("MAR_0114_A1"),"fast-forward passes reported chapter into "+e.vm().scriptName());
        e.restore(save);log.append("LEGACY INPUT PASS CHECKS ").append(checks).append("; media completion simulated; not a full-route test.\n");
        Files.write(output.resolve("input-result.txt"),log.toString().getBytes("UTF-8"));return log.toString();
    }
    private int[] pixels()throws IOException {BufferedImage image=e.render();try{return image.getRGB(0,0,e.width,e.height,null,0,e.width);}finally{image.flush();}}
}
