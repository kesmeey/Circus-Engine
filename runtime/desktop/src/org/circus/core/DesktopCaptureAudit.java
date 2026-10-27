package org.circus.core;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import javax.imageio.ImageIO;

/** Pixel regressions against read-only original resources, with synthetic
 * overlays so loss of a prior capture cannot hide behind an opaque next menu. */
public final class DesktopCaptureAudit {
    private static int checks;
    private static void check(boolean value,String label){checks++;if(!value)throw new AssertionError(label);}
    private static boolean same(BufferedImage a,BufferedImage b){return Arrays.equals(a.getRGB(0,0,a.getWidth(),a.getHeight(),null,0,a.getWidth()),b.getRGB(0,0,b.getWidth(),b.getHeight(),null,0,b.getWidth()));}
    private static UiCapture capture(DesktopEngine e){return e instanceof DesktopDd?((DesktopDd)e).host.uiCapture:((DesktopLegacy)e).host().uiCapture;}
    private static void command(DesktopEngine e,boolean raster)throws IOException {
        if(e instanceof DesktopDd){Dc3Host h=((DesktopDd)e).host;UiCapture.Renderer renderer=h.captureRenderer;if(!raster)h.captureRenderer=null;try{h.command("scene.transition",new int[]{9,0},new byte[0]);}finally{h.captureRenderer=renderer;}}
        else{Dc2Host h=((DesktopLegacy)e).host();UiCapture.Renderer renderer=h.captureRenderer;if(!raster)h.captureRenderer=null;try{h.captureUi();}finally{h.captureRenderer=renderer;}}
    }
    private static void display(DesktopEngine e)throws IOException {
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();
        if(e instanceof DesktopDd){Dc3Host h=((DesktopDd)e).host;new Dc3Scene().save(new DataOutputStream(bytes));h.scene.load(new DataInputStream(new ByteArrayInputStream(bytes.toByteArray())));h.scene.slots[1].resource="@ui-capture";h.ui.clear();h.ui.waiting=true;}
        else{Dc2Host h=((DesktopLegacy)e).host();new Dc2Scene().save(new DataOutputStream(bytes));h.scene.load(new DataInputStream(new ByteArrayInputStream(bytes.toByteArray())));h.scene.slots[1]="@ui-capture";h.ui.clearCurrent();h.ui.waiting=true;h.scene.publish(h.ui,0,0);}
    }
    private static void overlay(DesktopEngine e,String resource)throws IOException {
        if(e instanceof DesktopDd){Dc3Scene.Quad q=new Dc3Scene.Quad();q.resource=resource;q.x=77;q.y=88;q.width=80;q.height=60;((DesktopDd)e).host.ui.current.quads.add(q);((DesktopDd)e).host.ui.waiting=true;Dc3Ui.Text text=new Dc3Ui.Text();text.args=new int[]{240,100,7,7};text.text="Frozen capture";((DesktopDd)e).host.ui.palette[7]=0xffff6600;((DesktopDd)e).host.ui.current.texts.add(text);}
        else{Dc2Host h=((DesktopLegacy)e).host();h.scene.slots[21]=resource;Dc2Ui.Quad q=new Dc2Ui.Quad();q.flags=21;q.x=77;q.y=88;q.width=80;q.height=60;h.ui.quads.add(q);Dc2Ui.Quad color=new Dc2Ui.Quad();color.type=1;color.flags=7;color.x=240;color.y=100;color.width=100;color.height=32;h.ui.palette[7]=0xffff6600;h.ui.quads.add(color);h.scene.publish(h.ui,0,0);}
    }
    private static void nested(DesktopEngine e,Path out,byte[] title,String resource,boolean raster)throws Exception {
        e.restore(title);e.vm().restore(title);BufferedImage original=e.render();command(e,raster);display(e);BufferedImage first=e.render();
        String prefix=raster?"raster":"logical";ImageIO.write(original,"png",out.resolve(prefix+"-original.png").toFile());ImageIO.write(first,"png",out.resolve(prefix+"-first.png").toFile());
        check(same(original,first),"First capture matches original title pixels ("+prefix+")");
        overlay(e,resource);BufferedImage before=e.render();check(!same(first,before),"Overlay fixture changes the captured scene");command(e,raster);display(e);BufferedImage after=e.render();
        ImageIO.write(after,"png",out.resolve(prefix+"-nested.png").toFile());check(same(before,after),"Second capture preserves background and overlay ("+prefix+")");
        check(capture(e).depth==(raster?1:2),"Platform flattens; headless VM retains dependency");
        Path path=out.resolve(prefix+"-nested.sav");e.save(path);e.restore(path);check(same(after,e.render()),"Capture pixels survive VM/desktop save and new host");
        if(e instanceof DesktopDd){((DesktopDd)e).host.scene.elapse(7000);Arrays.fill(((DesktopDd)e).host.ui.palette,0xff00ff00);}
        else{((DesktopLegacy)e).host().scene.elapse(7000);Arrays.fill(((DesktopLegacy)e).host().ui.palette,0xff00ff00);}
        check(same(after,e.render()),"Captured colors and animation do not follow the live scene");
        if(raster){for(int n=0;n<40;n++){command(e,true);display(e);}check(capture(e).depth==1&&same(after,e.render()),"Forty repeated captures remain bounded and pixel-identical");}
    }
    private static void settings(DesktopEngine e,Path out,byte[] title,boolean raster)throws Exception {
        e.restore(title);e.vm().restore(title);e.choose("config");DesktopSettingsAudit.settle(e);e.hover(-1,-1);
        BufferedImage before=e.render();command(e,raster);display(e);BufferedImage after=e.render();String name=raster?"settings-raster":"settings-logical";
        ImageIO.write(before,"png",out.resolve(name+"-before.png").toFile());ImageIO.write(after,"png",out.resolve(name+"-after.png").toFile());
        check(same(before,after),"Original settings sprites, labels and sliders survive capture ("+name+")");
    }
    public static void main(String[] args)throws Exception {
        Path out=Paths.get(args[1]);Files.createDirectories(out);System.setProperty("dc.saveRoot",out.resolve("saves").toString());
        try(DesktopRuntime app=new DesktopRuntime(Paths.get(args[0]),true)){DesktopEngine e=app.engine;DesktopSettingsAudit.settle(e);if(e.resources.profile.id.equals("dcpc-2063")){e.choose("window");DesktopSettingsAudit.settle(e);}else if(e.resources.profile.id.equals("dc2pc-5769")){e.choose("03");DesktopSettingsAudit.settle(e);}
            e.hover(-1,-1);byte[] title=e.vm().snapshot();e.render();String resource=e.loadedImages.iterator().next();
            nested(e,out,title,resource,false);nested(e,out,title,resource,true);settings(e,out,title,false);settings(e,out,title,true);
            if(args.length>2){e.restore(Paths.get(args[2]));e.render();check(!e.body().isEmpty(),"Released 0.10 story save migrates");Path migrated=out.resolve("migrated-010.sav");BufferedImage before=e.render();e.save(migrated);e.restore(migrated);check(same(before,e.render()),"Migrated save resaves without pixel changes");}
            Files.write(out.resolve("result.json"),Json.text(Json.obj("profile",e.resources.profile.id,"checks",checks,"pass",true,"original_exe_run",false)).getBytes(java.nio.charset.StandardCharsets.UTF_8));System.out.println("PASS "+checks+" capture checks "+e.resources.profile.id);
        }
    }
}
