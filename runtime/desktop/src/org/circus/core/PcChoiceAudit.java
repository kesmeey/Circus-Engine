package org.circus.core;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;

/** Original PC2 al fine choices through the shared renderer; media completion is simulated. */
public final class PcChoiceAudit {
    private final DesktopLegacy e;private int checks;private final StringBuilder log=new StringBuilder();
    public PcChoiceAudit(DesktopLegacy engine){e=engine;}
    private void check(boolean value,String message)throws IOException{if(!value)throw new IOException(message+" at "+e.scriptName()+"@"+Integer.toHexString(e.scriptOffset()));checks++;log.append("PASS ").append(message).append('\n');}
    private void step()throws Exception{for(int c=0;c<6;c++)if(!e.audioLoop(c))e.audioCompleted(c,e.audioGeneration(c));e.mediaCompleted(e.ticket());e.tick(1000);}
    private List<String> actions(){List<String> out=new ArrayList<>();for(DesktopEngine.Control c:e.controls())out.add(c.action);return out;}
    private void title()throws Exception{
        for(int n=0;n<3000;n++){
            if(e.menuWaiting()){List<String> a=actions();if(a.contains("start"))return;if(a.contains("03")){e.choose("03");continue;}throw new IOException("Unexpected title menu "+a);}
            step();e.vm().signalHost(e.ticket(),MesVm.HostSignal.TEXT_COMPLETE);e.vm().signalHost(e.ticket(),MesVm.HostSignal.ADVANCE);
        }throw new IOException("PC2 title timeout");
    }
    private int[] picture(Path file)throws Exception{e.playbackMillis(600000);BufferedImage image=e.render();try{int[] pixels=image.getRGB(0,0,e.width,e.height,null,0,e.width);if(file!=null)try(OutputStream out=Files.newOutputStream(file)){ImageIO.write(image,"png",out);}return pixels;}finally{image.flush();}}
    private void visible(Path output,String script,String state)throws Exception{
        int[] shown=picture(output.resolve(script+"-"+state+".png"));List<Dc2Ui.Quad> text=new ArrayList<>();List<String> captions=new ArrayList<>();
        for(Dc2Ui.Button b:e.host().ui.buttons)for(Dc2Ui.Quad q:b.sprites)if(q.type==2){text.add(q);captions.add(q.text);q.text="";}
        int[] hidden;try{hidden=picture(null);}finally{for(int n=0;n<text.size();n++)text.get(n).text=captions.get(n);}
        for(int n=0;n<e.host().ui.buttons.size();n++){
            Dc2Ui.Button b=e.host().ui.buttons.get(n);int changed=0;
            for(int y=Math.max(0,b.top);y<Math.min(e.height,b.bottom);y++)for(int x=Math.max(0,b.left);x<Math.min(e.width,b.right);x++)if(shown[y*e.width+x]!=hidden[y*e.width+x])changed++;
            check(changed>80,script+" "+state+" choice "+n+" has visible glyph pixels ("+changed+")");
        }
    }
    private static void oldStates(List<Dc2Ui.Button> buttons){for(Dc2Ui.Button b:buttons)for(Dc2Ui.Quad q:b.sprites)if(q.type==2&&q.state==99)q.state=9;}
    private void shadowContrast(Path output,String script)throws Exception{
        int[] shaded=picture(null);List<Dc2Ui.Quad> text=new ArrayList<>();List<Integer> styles=new ArrayList<>();
        for(Dc2Ui.Button b:e.host().ui.buttons)for(Dc2Ui.Quad q:b.sprites)if(q.type==2){text.add(q);styles.add(q.flags);q.flags&=~0x80;}
        int[] plain;try{plain=picture(output.resolve(script+"-without-shadow.png"));}finally{for(int n=0;n<text.size();n++)text.get(n).flags=styles.get(n);}
        for(int n=0;n<e.host().ui.buttons.size();n++){
            Dc2Ui.Button b=e.host().ui.buttons.get(n);int dark=0;
            for(int y=b.top;y<b.bottom;y++)for(int x=b.left;x<b.right;x++){int p=shaded[y*e.width+x],q=plain[y*e.width+x];if((p>>16&255)<100&&(p>>8&255)<100&&(p&255)<100&&(q>>16&255)>120&&(q>>8&255)>120&&(q&255)>120)dark++;}
            check(dark>30,script+" white-background choice "+n+" has native dark shadow pixels ("+dark+")");
        }
    }
    public String run(Path output)throws Exception{
        Files.createDirectories(output);check(e.resources.profile.id.equals("dc2pc-5769"),"original PC2 profile");title();byte[] atTitle=e.snapshot();
        String[] scripts={"ind_0401_a1_cmn","ind_0401_a1_cmn2"};String[][] expected={{"○看窗户外面","○清醒起来"},{"○是音姐","○是由梦"}};int[] waits={0x5e3,0xefc};
        for(int group=0;group<scripts.length;group++){
            String script=scripts[group];e.restore(atTitle);e.host().beforeTransfer(1,script);e.vm().enterSystem(script);
            for(int n=0;n<1000&&!e.menuWaiting();n++){step();if(e.menuWaiting())break;e.vm().signalHost(e.ticket(),MesVm.HostSignal.TEXT_COMPLETE);e.vm().signalHost(e.ticket(),MesVm.HostSignal.ADVANCE);}
            check(e.menuWaiting()&&e.scriptOffset()==waits[group]&&actions().equals(Arrays.asList("01","02")),script+" reaches its two original choices");
            for(int n=0;n<2;n++){
                Dc2Ui.Button b=e.host().ui.buttons.get(n);int found=0;
                for(Dc2Ui.Quad q:b.sprites)if(q.type==2){check(q.text.equals(expected[group][n]),script+" decodes original caption "+expected[group][n]);if(q.state==99)found++;}
                check(found==1,script+" choice "+n+" has one normal caption");
            }
            e.hover(-1,-1);visible(output,script,"idle");if(group==1)shadowContrast(output,script);byte[] choice=e.snapshot();int[] idle=picture(null);
            Dc2Ui.Button first=e.host().ui.buttons.get(0);int x=(first.left+first.right)/2,y=(first.top+first.bottom)/2;
            e.hover(x,y);visible(output,script,"hover");e.pointerDown(x,y);visible(output,script,"pressed");e.cancelPointer();e.hover(-1,-1);
            oldStates(e.host().ui.buttons);for(List<Dc2Ui.Button> template:e.host().ui.buttonTemplates)oldStates(template);Arrays.fill(e.host().ui.palette,0,13,0xffffffff);byte[] old=e.snapshot();
            picture(output.resolve(script+"-old-state.png"));e.restore(old);e.hover(-1,-1);
            check(Arrays.equals(idle,picture(output.resolve(script+"-migrated.png"))),script+" older phone choice snapshot restores exact corrected pixels");
            for(Dc2Ui.Button b:e.host().ui.buttonTemplates.get(3))for(Dc2Ui.Quad q:b.sprites)if(q.type==2)check(q.state!=9,script+" saved future-choice template migrates");
            for(int n=0;n<2;n++){e.restore(choice);String action=n==0?"01":"02";Integer target=e.resources.scripts().script(script).label(action.getBytes(Binary.SJIS));e.choose(action);check(target!=null&&e.vm().offset()==target,script+" choice "+action+" retains original branch target");}
        }
        e.restore(atTitle);log.append("PC2 CHOICE PASS CHECKS ").append(checks).append("; original START templates and both al fine entry scripts, audit transfer, simulated media completion; original EXE not executed.\n");
        Files.write(output.resolve("choice-result.txt"),log.toString().getBytes("UTF-8"));Files.write(output.resolve("loaded-images.json"),(Json.text(e.loadedImages)+"\n").getBytes("UTF-8"));return log.toString();
    }
}
