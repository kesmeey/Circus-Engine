package org.circus.core;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;

/** Actual Charle route eyelid transitions, with deterministic timing and media completion. */
public final class DdTransitionAudit {
    private final DesktopDd e;private int checks;private final StringBuilder log=new StringBuilder();
    public DdTransitionAudit(DesktopDd engine){e=engine;}
    private void check(boolean value,String message)throws IOException{if(!value)throw new IOException(message+" at "+e.vm().scriptName()+"@"+Integer.toHexString(e.vm().offset()));checks++;log.append("PASS ").append(message).append('\n');}
    private void tick(long ms)throws Exception{for(int c=0;c<6;c++)if(!e.audioLoop(c))e.audioCompleted(c,e.audioGeneration(c));e.vm().signalHost(e.ticket(),MesVm.HostSignal.MEDIA_COMPLETE);e.tick(ms);}
    private void seek()throws Exception{
        for(int n=0;n<100000;n++){
            tick(600000);
            if(e.vm().scriptName().equalsIgnoreCase("DD_2_1212_A1")&&e.vm().offset()==0xe36&&e.host.scene.fade.active())return;
            if(e.menuWaiting()){
                List<String> actions=new ArrayList<>();for(DesktopEngine.Control c:e.controls())actions.add(c.action);
                String selected=null;for(String a:new String[]{"window","03","sce_start","start","sel02","sceselect01"})if(actions.contains(a)){selected=a;break;}
                if(selected==null&&!actions.isEmpty())selected=actions.get(0);
                if(selected==null)throw new IOException("No route choice");log.append("CHOICE ").append(e.vm().scriptName()).append(' ').append(selected).append(' ').append(actions).append('\n');e.choose(selected);
            }else{e.vm().signalHost(e.ticket(),MesVm.HostSignal.TEXT_COMPLETE);e.vm().signalHost(e.ticket(),MesVm.HostSignal.ADVANCE);}
        }throw new IOException("Reported eyelid transition not reached at "+e.vm().scriptName()+"@"+Integer.toHexString(e.vm().offset())+" "+e.vm().status()+"\n"+log);
    }
    private static String hash(BufferedImage image){int[] pixels=image.getRGB(0,0,image.getWidth(),image.getHeight(),null,0,image.getWidth());java.nio.ByteBuffer bytes=java.nio.ByteBuffer.allocate(pixels.length*4);for(int p:pixels)bytes.putInt(p);return Binary.sha256(bytes.array());}
    private void checkBufferReuse(byte[] first)throws Exception{
        e.restore(first);
        int[] modes={0,1,2,3,17,17,20,21};
        String[] masks={"","","","","MSK\\MASK04A","MSK\\MASK11A","MSK\\MASK05A","MSK\\MASK05B"};
        for(int variant=0;variant<modes.length;variant++){
            Dc3Transition f=e.host.scene.fade;f.mode=modes[variant];f.mask=masks[variant];f.duration=f.mode==3?167:1000;f.elapsed=0;
            byte[] start=e.snapshot();String[] reused=new String[3];
            for(int frame=0;frame<3;frame++){f.elapsed=f.duration*frame/3;BufferedImage image=e.render();reused[frame]=hash(image);image.flush();}
            boolean identical=true;
            for(int frame=0;frame<3;frame++){
                // Restore clears the scratch image; compare against a freshly painted buffer.
                e.restore(start);Dc3Transition fresh=e.host.scene.fade;fresh.elapsed=fresh.duration*frame/3;
                BufferedImage image=e.render();identical&=hash(image).equals(reused[frame]);image.flush();
            }
            check(identical,"fresh/reused buffers agree for mode "+f.mode+" mask "+f.mask);
        }
    }
    public String run(Path output)throws Exception{
        Files.createDirectories(output);check(e.resources.profile.id.equals("dc3dd-a5a8"),"original DD profile");seek();
        byte[] first=e.snapshot();
        List<Object> phases=new ArrayList<>();
        for(int phase=0;phase<3;phase++){
            Dc3Transition f=e.host.scene.fade;int expectedPc=new int[]{0xe36,0xe62,0xe98}[phase];long expectedDuration=phase==0?600:1000;
            check(e.vm().offset()==expectedPc&&f.mode==21&&f.duration==expectedDuration,"phase "+phase+" original opcode and duration");
            check(f.mask.equalsIgnoreCase(phase==1?"MSK\\MASK05B":"MSK\\MASK05A"),"phase "+phase+" original eyelid mask");
            long[] times=new long[9];List<String> hashes=new ArrayList<>();
            byte[] start=e.snapshot();
            for(int frame=0;frame<times.length;frame++){
                f.elapsed=f.duration*frame/9;long begin=System.nanoTime();BufferedImage image=e.render();times[frame]=System.nanoTime()-begin;
                hashes.add(hash(image));if(frame==0||frame==4||frame==8)try(OutputStream out=Files.newOutputStream(output.resolve("phase-"+phase+"-frame-"+frame+".png"))){ImageIO.write(image,"png",out);}image.flush();
            }
            f.elapsed=f.duration*4/9;byte[] middle=e.snapshot();e.restore(middle);BufferedImage restored=e.render();check(hash(restored).equals(hashes.get(4)),"phase "+phase+" save/load preserves transition pixels");restored.flush();
            long[] warm=Arrays.copyOfRange(times,1,times.length);Arrays.sort(warm);double median=(warm[3]+warm[4])/2e6;
            log.append("PHASE ").append(phase).append(" COLD_MS ").append(times[0]/1e6).append(" WARM_MEDIAN_MS ").append(median).append('\n');
            phases.add(Json.obj("phase",phase,"pc",Integer.toHexString(expectedPc),"duration_ms",expectedDuration,"mask",f.mask,"render_ns",times,"warm_median_ms",median,"frame_sha256",hashes));
            e.restore(start);tick(expectedDuration);
        }
        check(e.vm().scriptName().equalsIgnoreCase("DD_2_1212_A1")&&e.body().contains("晨光透过眼帘"),"three transitions continue to reported narration");
        byte[] endingState=e.snapshot();checkBufferReuse(first);e.restore(endingState);
        BufferedImage ending=e.render();try(OutputStream out=Files.newOutputStream(output.resolve("after-eyelids.png"))){ImageIO.write(ending,"png",out);}ending.flush();
        e.save(output.resolve("after-eyelids.sav"));
        log.append("DD EYELID PASS CHECKS ").append(checks).append("; render timings exclude media and file I/O; media completion simulated.\n");
        Files.write(output.resolve("transition-result.json"),(Json.text(Json.obj("checks",checks,"phases",phases,"windows_game_executed",false,"media_completion_simulated",true))+"\n").getBytes("UTF-8"));
        Files.write(output.resolve("transition-result.txt"),log.toString().getBytes("UTF-8"));return log.toString();
    }
}
