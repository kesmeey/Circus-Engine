package org.circus.core;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;

/** Reported Charle route scene; separates particle simulation and drawing cost. */
public final class DdPetalAudit {
    private final DesktopDd e;private int checks;private final StringBuilder log=new StringBuilder();
    public DdPetalAudit(DesktopDd engine){e=engine;}
    private void check(boolean ok,String name)throws IOException{if(!ok)throw new IOException(name+" at "+e.vm().scriptName()+"@"+Integer.toHexString(e.vm().offset()));checks++;log.append("PASS ").append(name).append('\n');}
    private void seek()throws Exception{
        for(int n=0;n<100000;n++){
            for(int c=0;c<6;c++)if(!e.audioLoop(c))e.audioCompleted(c,e.audioGeneration(c));
            e.vm().signalHost(e.ticket(),MesVm.HostSignal.MEDIA_COMPLETE);e.tick(1000);
            if(e.vm().scriptName().equalsIgnoreCase("DD_2_1224_F2")&&e.vm().offset()==0x2954&&e.textWaiting())return;
            if(e.menuWaiting()){
                List<String> actions=new ArrayList<>();for(DesktopEngine.Control c:e.controls())actions.add(c.action);
                String chosen=null;for(String a:new String[]{"window","03","sce_start","start","sel02","sceselect01"})if(actions.contains(a)){chosen=a;break;}
                if(chosen==null&&!actions.isEmpty())chosen=actions.get(0);
                if(chosen==null)throw new IOException("No original route choice");e.choose(chosen);
            }else{e.vm().signalHost(e.ticket(),MesVm.HostSignal.TEXT_COMPLETE);e.vm().signalHost(e.ticket(),MesVm.HostSignal.ADVANCE);}
        }throw new IOException("Reported petal scene not reached");
    }
    private static String hash(BufferedImage image){int[] pixels=image.getRGB(0,0,image.getWidth(),image.getHeight(),null,0,image.getWidth());java.nio.ByteBuffer bytes=java.nio.ByteBuffer.allocate(pixels.length*4);for(int p:pixels)bytes.putInt(p);return Binary.sha256(bytes.array());}
    private static double percentile(long[] samples,double fraction){long[] sorted=samples.clone();Arrays.sort(sorted);return sorted[(int)Math.ceil(sorted.length*fraction)-1]/1e6;}
    public String run(Path output,boolean replay)throws Exception{
        Files.createDirectories(output);Path file=output.resolve("scene.state");
        if(replay)e.restore(Files.readAllBytes(file));else seek();
        e.auto=e.rapid=false;e.vm().signalHost(e.ticket(),MesVm.HostSignal.TEXT_COMPLETE);e.tick(0);e.playbackMillis(600000);
        check(e.vm().scriptName().equalsIgnoreCase("DD_2_1224_F2")&&e.body().contains("较真地"),"reported narration");
        check(e.host.scene.petals.particles.size()==10&&e.host.scene.petals.effect==1,"original ten falling petals");
        check(e.host.scene.slots[27].resource.equalsIgnoreCase("SYSTEM\\EFFSAKURA02")&&!e.host.scene.fade.active(),"original sakura atlas, no transition");
        byte[] state=e.snapshot();if(!replay)Files.write(file,state);
        List<Object> variants=new ArrayList<>();
        for(boolean enabled:new boolean[]{true,false}){
            e.restore(state);e.playbackMillis(600000);if(!enabled)e.host.scene.petals.particles.clear();
            BufferedImage warm=e.render();warm.flush();
            long[] draw=new long[120],tick=new long[120];List<String> hashes=new ArrayList<>();
            for(int frame=0;frame<draw.length;frame++){
                long begin=System.nanoTime();e.tick(frame%3==0?16:17);tick[frame]=System.nanoTime()-begin;
                begin=System.nanoTime();BufferedImage image=e.render();draw[frame]=System.nanoTime()-begin;
                if(frame%10==0)hashes.add(hash(image));
                if(frame==60)try(OutputStream out=Files.newOutputStream(output.resolve(enabled?"petals.png":"no-petals.png"))){ImageIO.write(image,"png",out);}image.flush();
            }
            variants.add(Json.obj("petals",enabled,"render_median_ms",percentile(draw,.5),"render_p95_ms",percentile(draw,.95),"tick_median_ms",percentile(tick,.5),"tick_p95_ms",percentile(tick,.95),"render_ns",draw,"tick_ns",tick,"frame_sha256",hashes));
            log.append("PETALS ").append(enabled).append(" DRAW_MEDIAN_MS ").append(percentile(draw,.5)).append(" DRAW_P95_MS ").append(percentile(draw,.95)).append(" TICK_MEDIAN_MS ").append(percentile(tick,.5)).append('\n');
        }
        e.restore(state);e.playbackMillis(600000);BufferedImage before=e.render();String pixels=hash(before);before.flush();e.restore(state);e.playbackMillis(600000);BufferedImage after=e.render();check(pixels.equals(hash(after)),"save/load preserves particle positions and pixels");after.flush();
        Files.write(output.resolve("petal-result.json"),(Json.text(Json.obj("checks",checks,"variants",variants,"windows_game_executed",false,"media_completion_simulated",true))+"\n").getBytes("UTF-8"));
        log.append("DD PETALS PASS CHECKS ").append(checks).append('\n');Files.write(output.resolve("petal-result.txt"),log.toString().getBytes("UTF-8"));return log.toString();
    }
}
