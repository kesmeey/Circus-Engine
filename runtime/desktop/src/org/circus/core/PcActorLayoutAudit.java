package org.circus.core;

import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import javax.imageio.ImageIO;

/** Read-only DC2PC Dacapo scene reproduction and renderer regression; originals never execute. */
public final class PcActorLayoutAudit {
    private static int checks;
    private static final String SCRIPT="1ind_0119_e1_skr";
    private static void check(boolean value,String why)throws IOException{if(!value)throw new IOException(why);checks++;System.out.println("PASS "+why);}
    private static void step(DesktopLegacy e)throws Exception{for(int ch=0;ch<6;ch++)e.audioCompleted(ch,e.audioGeneration(ch));e.mediaCompleted(e.ticket());e.tick(1000);}
    private static void advance(DesktopLegacy e)throws IOException{e.vm().signalHost(e.ticket(),MesVm.HostSignal.TEXT_COMPLETE);e.vm().signalHost(e.ticket(),MesVm.HostSignal.ADVANCE);}
    private static void shot(DesktopLegacy e,Path file)throws Exception{e.vm().signalHost(e.ticket(),MesVm.HostSignal.TEXT_COMPLETE);e.playbackMillis(600000);BufferedImage b=e.render();try{ImageIO.write(b,"png",file.toFile());}finally{b.flush();}}
    private static void title(DesktopLegacy e)throws Exception{
        for(int n=0;n<3000;n++){step(e);if(e.menuWaiting()){boolean next=false;for(DesktopEngine.Control c:e.controls()){if(c.action.equals("start"))return;if(c.action.equals("03"))next=true;}if(next){e.choose("03");continue;}}advance(e);}
        throw new IOException("Original title did not settle");
    }
    private static void dialogue(DesktopLegacy e,int pc)throws Exception{
        for(int n=0;n<4000;n++){step(e);if(!e.scriptName().equalsIgnoreCase(SCRIPT))throw new IOException("Unexpected transfer: "+e.scriptName());if(e.textWaiting()&&e.scriptOffset()==pc)return;if(e.menuWaiting())throw new IOException("Unexpected menu");advance(e);}
        throw new IOException("Reported scene did not reach "+Integer.toHexString(pc));
    }
    private static Crx.Image solid(int color){byte[] b=new byte[20*20*4];for(int n=0;n<b.length;n+=4){b[n]=(byte)color;b[n+1]=(byte)(color>>8);b[n+2]=(byte)(color>>16);b[n+3]=(byte)255;}return new Crx.Image(0,0,20,20,32,b,null);}
    public static void main(String[] args)throws Exception{
        if(args.length<2)throw new IllegalArgumentException("game-root output-directory [old-reported.state]");
        Path output=Paths.get(args[1]);Files.createDirectories(output);System.setProperty("dc.saveRoot",output.resolve("saves").toString());
        CircusResources resources=new CircusResources(new GameResources(new LocalGameStorage(Paths.get(args[0])),"@none"),Profiles.get("dc2pc-5769")){
            @Override public Crx.Image image(String name)throws IOException{if(name.equals("@red"))return solid(0xff0000);if(name.equals("@green"))return solid(0x00ff00);if(name.equals("@blue"))return solid(0x0000ff);return super.image(name);}
        };
        DesktopLegacy e=new DesktopLegacy(resources,new TextCodec("","","GBK"),true);title(e);check(e.menuWaiting(),"Original START reaches title");
        e.host().beforeTransfer(1,SCRIPT);e.vm().enterSystem(SCRIPT);dialogue(e,0x85ec);
        check(e.body().contains("我有问题想问樱小姐"),"Reported dialogue at 1ind_0119_e1_skr:85ec");
        Dc2Scene.Frame f=e.host().scene.shown;
        check(f.slots[2].equalsIgnoreCase("bst\\skr_0c_008*")&&f.slots[3].equalsIgnoreCase("bst\\otm_0b_200_b*"),"Original script intentionally displays both actors");
        check(Arrays.equals(f.actors[0],new int[]{87,0,75,441,322,0})&&Arrays.equals(f.actors[1],new int[]{-125,0,100,441,296,0}),"Native scale, position and anchors remain intact");
        Crx.Image otome=resources.image(f.slots[3]);PcActorLayout placement=PcActorLayout.dc2pc(f.actors[1],otome.width,otome.height);
        check(otome.width==1260&&otome.height==1080&&placement.x==-310&&placement.y==-124,"Native back-view canvas moves to -310,-124");
        shot(e,output.resolve("reported.png"));byte[] snapshot=e.snapshot();Files.write(output.resolve("reported.state"),snapshot);e.restore(snapshot);
        check(e.scriptOffset()==0x85ec&&Arrays.equals(e.host().scene.shown.actors[1],f.actors[1]),"Save round trip preserves native actor state");
        shot(e,output.resolve("restored.png"));
        if(args.length>2){e.restore(Files.readAllBytes(Paths.get(args[2])));check(e.scriptOffset()==0x85ec&&e.host().scene.shown.slots[3].equalsIgnoreCase("bst\\otm_0b_200_b*"),"Pre-fix phone-format state restores without migration");shot(e,output.resolve("old-save-restored.png"));}
        // Continue the original script to the reverse camera angle: the actor slots swap roles.
        e.restore(snapshot);dialogue(e,0x9abf);
        check(e.host().scene.shown.slots[3].equalsIgnoreCase("bst\\skr_0c_200_b*"),"Reverse angle uses Sakura's large back-view in later slot");shot(e,output.resolve("reverse-angle.png"));
        // Independent solid pixels make the native slot order observable, without image-specific guesses.
        Dc2Host h=e.host();h.scene.reset();h.body=h.speaker="";h.ui.waiting=false;h.write(99,10,0);
        for(int[] a:h.scene.characters){Arrays.fill(a,0);a[0]=a[1]=10;a[2]=100;}
        h.scene.slots[2]="@red";h.scene.slots[6]="@blue";h.scene.slots[3]="@green";h.scene.publish(h.ui,0,0);
        BufferedImage b=e.render();try{check((b.getRGB(15,15)&0xffffff)==0x00ff00,"Actor slot 1 covers slot 0 and its accessory");}finally{b.flush();}
        h.scene.slots[3]="";h.scene.publish(h.ui,0,0);b=e.render();try{check((b.getRGB(15,15)&0xffffff)==0x0000ff,"Accessory stays above its own actor");}finally{b.flush();}
        System.out.println("PC ACTOR LAYOUT AUDIT PASS "+checks+"; direct transfer after original START; media completion simulated");
    }
}
