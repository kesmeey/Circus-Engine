package org.circus.core;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;

/** Original START choice templates, self-authored captions and a white underlay, on the shipping renderer. */
public final class LegacyChoiceStyleAudit {
    private final DesktopLegacy e;private int checks;private final StringBuilder log=new StringBuilder();
    public LegacyChoiceStyleAudit(DesktopLegacy engine){e=engine;}
    private void check(boolean value,String message)throws IOException{if(!value)throw new IOException(message);checks++;log.append("PASS ").append(message).append('\n');}
    private int[] picture(Path file)throws Exception{e.playbackMillis(600000);BufferedImage image=e.render();try{int[] pixels=image.getRGB(0,0,e.width,e.height,null,0,e.width);if(file!=null)try(OutputStream out=Files.newOutputStream(file)){ImageIO.write(image,"png",out);}return pixels;}finally{image.flush();}}
    private void contrast(Path output,String state)throws Exception{
        int[] shaded=picture(output.resolve(state+".png"));List<Dc2Ui.Quad> text=new ArrayList<>();List<Integer> styles=new ArrayList<>();
        for(Dc2Ui.Button b:e.host().ui.buttons)for(Dc2Ui.Quad q:b.sprites)if(q.type==2){text.add(q);styles.add(q.flags);q.flags&=~0x80;}
        int[] plain;try{plain=picture(state.equals("idle")?output.resolve("without-shadow.png"):null);}finally{for(int n=0;n<text.size();n++)text.get(n).flags=styles.get(n);}
        for(int n=0;n<e.host().ui.buttons.size();n++){
            Dc2Ui.Button b=e.host().ui.buttons.get(n);int dark=0;
            // DM's original opaque pink button covers the white underlay. Compare the
            // same pixels with only style0x80 toggled, rather than assuming a white bar.
            for(int y=Math.max(0,b.top);y<Math.min(e.height,b.bottom);y++)for(int x=Math.max(0,b.left);x<Math.min(e.width,b.right);x++){int p=shaded[y*e.width+x],q=plain[y*e.width+x];int pr=p>>16&255,pg=p>>8&255,pb=p&255,qr=q>>16&255,qg=q>>8&255,qb=q&255;if(pr<100&&pg<100&&pb<100&&qr+qg+qb-pr-pg-pb>80)dark++;}
            check(dark>30,state+" choice "+n+" has visible native shadow over original button ("+dark+" pixels)");
        }
    }
    public String run(Path output)throws Exception{
        Files.createDirectories(output);Dc2Host h=e.host();check(h.ui.buttonTemplates.get(3).size()>=3,"original START supplies native choice templates");byte[] initial=e.snapshot();
        try{
            boolean pc=h instanceof PcHost;h.command(pc?"pc.choice.begin":"dm.choice.begin",new int[]{1,0},new byte[0]);
            h.addChoice("audit-one".getBytes(Binary.SJIS),"Choice One".getBytes(Binary.SJIS));h.addChoice("audit-two".getBytes(Binary.SJIS),"Choice Two".getBytes(Binary.SJIS));
            h.ui.quads.clear();Dc2Ui.Quad white=new Dc2Ui.Quad();white.type=1;white.flags=64|7;white.width=e.width;white.height=e.height;h.ui.quads.add(white);h.body=h.speaker="";h.scene.publish(h.ui,0,0);h.ui.waiting=true;
            check(h.ui.palette[0]==0xff000000&&h.ui.palette[7]==0xffffffff,"native black-shadow/white-foreground palettes initialized");
            for(Dc2Ui.Button b:h.ui.buttons){int normal=0;for(Dc2Ui.Quad q:b.sprites)if(q.type==2&&q.state==99){check((q.flags&0x80)!=0&&!q.text.isEmpty(),"native normal caption requests style0x80 shadow");normal++;}check(normal==1,"native template has exactly one idle caption");}
            e.hover(-1,-1);contrast(output,"idle");int[] idle=picture(null);byte[] baseline=e.snapshot();Dc2Ui.Button b=h.ui.buttons.get(0);int x=(b.left+b.right)/2,y=(b.top+b.bottom)/2;
            e.hover(x,y);contrast(output,"hover");e.pointerDown(x,y);contrast(output,"pressed");e.cancelPointer();e.restore(baseline);e.hover(-1,-1);
            check(Arrays.equals(idle,picture(output.resolve("restored.png"))),"save/restore preserves corrected choice pixels");
            Arrays.fill(e.host().ui.palette,0,13,0xffffffff);byte[] old=e.snapshot();e.restore(old);e.hover(-1,-1);
            check(Arrays.equals(idle,picture(output.resolve("migrated.png"))),"old uninitialized palette snapshot migrates to corrected choice pixels");
            log.append("LEGACY CHOICE STYLE PASS PROFILE ").append(e.resources.profile.id).append(" CHECKS ").append(checks).append("; original START geometry, states, textures and style flags; captions/white underlay self-authored; no story route coverage.\n");
            Files.write(output.resolve("style-result.txt"),log.toString().getBytes("UTF-8"));return log.toString();
        }finally{e.restore(initial);}
    }
}
