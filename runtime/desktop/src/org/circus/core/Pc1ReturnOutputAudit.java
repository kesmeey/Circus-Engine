package org.circus.core;

import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import javax.imageio.ImageIO;

/** Self-authored2063 MES and opaque desktop scenes for native wait-1 return.
 * Uses the actual VM26/1C and DesktopLegacy800x600 renderer, without original
 * game files, EXE instructions, font substitution or relaxed runtime gates. */
public final class Pc1ReturnOutputAudit {
    private static int checks;
    private static void check(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
    private static byte[] mes(byte[] code)throws IOException{ByteArrayOutputStream out=new ByteArrayOutputStream();out.write(new byte[]{0,0,0,0,0x63,0x20});out.write(code);return out.toByteArray();}
    private static CircusResources resources()throws Exception{
        final Map<String,byte[]> files=new HashMap<>();files.put("AdvData/DAT/NAMES.DAT",new byte[20]);
        files.put("system.dat",Pc1NativeSettings.currentChsDefaults().nativeBytes()); // explicit synthetic source
        files.put("AdvData/MES/START.mes",mes(new byte[]{0x26,0,0,0,'m','e','n','u',0,0,0,0}));files.put("AdvData/MES/menu.mes",mes(new byte[]{0x1c,0,0,0,0,0}));
        GameResources data=new GameResources(new GameResources.Storage(){
            public Set<String> paths(){return files.keySet();}
            private byte[] get(String path)throws IOException{byte[] bytes=files.get(path);if(bytes==null)throw new FileNotFoundException(path);return bytes;}
            public byte[] read(String path,int limit)throws IOException{byte[] bytes=get(path);if(bytes.length>limit)throw new IOException("Fixture read limit");return bytes.clone();}
            public Archive.Source source(String path)throws IOException{final byte[] bytes=get(path);return new Archive.Source(){public long size(){return bytes.length;}public byte[] read(long offset,int length)throws IOException{Binary.range(offset,length,bytes.length);return Arrays.copyOfRange(bytes,(int)offset,(int)offset+length);}};}
        },"@none");return new CircusResources(data,Profiles.get("dcpc-2063"));
    }
    private static Dc2Ui.Quad rectangle(int x,int y,int width,int height,int palette){Dc2Ui.Quad q=new Dc2Ui.Quad();q.type=1;q.x=x;q.y=y;q.width=width;q.height=height;q.flags=palette;return q;}
    private static void scene(DesktopLegacy engine,int outer,int inner)throws IOException{
        engine.host().ui.quads.clear();engine.host().ui.quads.add(rectangle(0,0,800,600,outer));engine.host().ui.quads.add(rectangle(93,71,217,143,inner));engine.host().scene.publish(engine.host().ui,0,0);
    }
    private static int[] pixels(DesktopLegacy engine,Path file)throws IOException{
        BufferedImage image=engine.render();try{check(image.getWidth()==800&&image.getHeight()==600,"desktop native return keeps full800x600 output");if(file!=null)ImageIO.write(image,"png",file.toFile());return image.getRGB(0,0,800,600,null,0,800);}finally{image.flush();}
    }
    private static String hash(int[] pixels)throws IOException{ByteArrayOutputStream bytes=new ByteArrayOutputStream();DataOutputStream out=new DataOutputStream(bytes);for(int pixel:pixels)out.writeInt(pixel);return Binary.sha256(bytes.toByteArray());}
    private static PcHost host(DesktopLegacy engine){return (PcHost)engine.host();}
    private static DesktopLegacy restored(DesktopLegacy source,byte[] snapshot)throws Exception{
        DesktopLegacy next=new DesktopLegacy(source.resources,source.codec,true);ByteArrayOutputStream persistent=new ByteArrayOutputStream();source.host().savePersistent(new DataOutputStream(persistent));next.host().loadPersistent(new DataInputStream(new ByteArrayInputStream(persistent.toByteArray())));next.restore(snapshot);return next;
    }
    private static Object run(CircusResources data,int speed,Path output)throws Exception{
        Files.createDirectories(output);DesktopLegacy engine=new DesktopLegacy(data,TextCodec.identity(),true);PcHost h=host(engine);h.write(99,12,speed);
        h.ui.palette[5]=0xffb02539;h.ui.palette[6]=0xff2163ba;h.ui.palette[10]=0xff19514e;h.ui.palette[11]=0xff823c91;scene(engine,5,6);
        int[] caller=pixels(engine,output.resolve("caller.png"));check(caller[0]==0xffb02539&&caller[71*800+93]==0xff2163ba,"caller bitmap is independently recognizable opaque scene geometry");
        check(engine.vm().run(1)==MesVm.Status.RUNNING&&engine.vm().scriptName().equals("menu")&&engine.session.inSystem()&&h.pc1.system().fields().waitKind==-1,"valid native26 captures a wait-1 caller through the desktop framebuffer adapter");
        int returnPc=h.pc1.system().returnPoint().pc;check(returnPc==15,"native26 records consumed owned-string cursor, not a projected slot PC");
        // Palette is outside1F44. A logical repaint after1C would now be green,
        // while wait-1 must retain the captured red caller until a full paint.
        h.ui.palette[5]=0xff37a951;scene(engine,10,11);int[] menu=pixels(engine,output.resolve("menu.png"));
        check(!Arrays.equals(caller,menu),"callee and caller front buffers differ");
        check(engine.vm().run(1)==MesVm.Status.WAIT_HOST&&h.pc1.returning()&&engine.vm().offset()==returnPc&&engine.vm().scriptName().equals("START"),"valid1C owns the native wait-1 return phase before caller execution");
        int first=h.pc1.returnProgress();int[] firstPixels=pixels(engine,output.resolve("first-feedback.png"));
        check(!Arrays.equals(firstPixels,caller)&&!Arrays.equals(firstPixels,menu),"desktop draws the immediate feedback frame between its opaque captures");
        check(!engine.vm().signalHost(engine.ticket(),MesVm.HostSignal.ADVANCE),"wait-1 transfer rejects ADVANCE");engine.click(0,0,false);engine.click(0,0,true);check(engine.modalKey(java.awt.event.KeyEvent.VK_ENTER)&&engine.vm().offset()==returnPc,"platform input cannot run the resumed wait-1 caller early");
        engine.vm().elapse(39);check(h.pc1.returning()&&h.pc1.returnProgress()==first&&Arrays.equals(firstPixels,pixels(engine,null)),"wait-1 first feedback does not finish before40ms");
        byte[] pending=engine.vm().snapshot();Files.write(output.resolve("pending.cvs2"),pending);DesktopLegacy copy=restored(engine,pending);
        check(Arrays.equals(pending,copy.vm().snapshot())&&Arrays.equals(firstPixels,pixels(copy,null))&&host(copy).pc1.returning(),"new desktop/VM restores wait-1 pending phase and exact pixels");
        // Native41A0B0 redraws before taking a fresh clock. A late platform tick
        // cannot spend its excess time on the next iteration's40ms interval.
        engine.vm().elapse(1000);copy.vm().elapse(1000);int increment=new int[]{1,2,4,8}[speed];
        check(h.pc1.returning()&&h.pc1.returnProgress()==Math.min(16,first+increment)&&host(copy).pc1.returnProgress()==h.pc1.returnProgress(),"late platform tick advances only one fresh native feedback iteration");
        int feedbackFrames=2;List<Object> phases=new ArrayList<>();phases.add(Json.obj("progress",first,"pixels_sha256",hash(firstPixels)));
        while(h.pc1.returning()&&feedbackFrames<=16){
            int progress=h.pc1.returnProgress();int[] current=pixels(engine,null);check(Arrays.equals(current,pixels(copy,null))&&Arrays.equals(engine.vm().snapshot(),copy.vm().snapshot()),"full wait-1 phase output and snapshots match restored continuation");
            phases.add(Json.obj("progress",progress,"pixels_sha256",hash(current)));check(!engine.vm().signalHost(engine.ticket(),MesVm.HostSignal.ADVANCE),"ADVANCE stays blocked through later feedback");
            if(progress==16){engine.vm().elapse(39);copy.vm().elapse(39);check(h.pc1.returning()&&!Arrays.equals(current,caller)&&Arrays.equals(current,pixels(engine,null)),"progress16 remains feedback rather than the exact caller for39ms");engine.vm().elapse(1);copy.vm().elapse(1);}
            else{engine.vm().elapse(40);copy.vm().elapse(40);feedbackFrames++;}
        }
        check(!h.pc1.returning()&&!engine.session.inSystem()&&!host(copy).pc1.returning()&&engine.vm().status()==MesVm.Status.RUNNING&&engine.vm().offset()==returnPc,"wait-1 returns to runnable caller only when transfer commits");
        check(Arrays.equals(caller,pixels(engine,output.resolve("committed.png")))&&Arrays.equals(caller,pixels(copy,null)),"final407880 copy is byte-exact captured caller, independent of live palette");
        check(h.ui.palette[5]==0xff37a951&&h.pc1.returnFront()!=null,"committed wait-1 keeps captured output without rolling back the live global palette");
        byte[] committed=engine.vm().snapshot();Files.write(output.resolve("committed.cvs2"),committed);DesktopLegacy committedCopy=restored(engine,committed);
        check(Arrays.equals(committed,committedCopy.vm().snapshot())&&Arrays.equals(caller,pixels(committedCopy,null))&&!committedCopy.session.inSystem(),"committed retained framebuffer survives a new desktop/VM snapshot");
        for(int n=0;n<3;n++)check(Arrays.equals(caller,pixels(engine,null)),"repeated platform repaint cannot retire the wait-1 bitmap");
        h.ui.quads.addAll(Dc2Ui.copyQuads(h.scene.shown.quads));h.scene.publish(h.ui,0,0);int[] repainted=pixels(engine,output.resolve("after-full-paint.png"));
        check(h.pc1.returnFront()==null&&repainted[0]==0xff37a951&&repainted[71*800+93]==0xff2163ba&&!Arrays.equals(caller,repainted),"reviewed full-frame publication retires retained bitmap and uses the current global palette");
        check(engine.missingImages.isEmpty()&&engine.loadedImages.isEmpty(),"synthetic geometry path requires no guessed or missing image resources");
        return Json.obj("speed",speed,"feedback_frames",feedbackFrames,"first_progress",first,"late_tick_requested_ms",1000,"pending_snapshot_bytes",pending.length,"committed_snapshot_bytes",committed.length,"caller_sha256",hash(caller),"menu_sha256",hash(menu),"after_full_paint_sha256",hash(repainted),"phases",phases);
    }
    public static void main(String[] args)throws Exception{
        if(args.length!=1)throw new IOException("OUTPUT");Path output=Paths.get(args[0]);Files.createDirectories(output);System.setProperty("dc.saveRoot",output.resolve("saves").toString());checks=0;CircusResources data=resources();List<Object> rows=new ArrayList<>();
        for(int speed=0;speed<4;speed++)rows.add(run(data,speed,output.resolve("speed"+speed)));
        Files.write(output.resolve("result.json"),Json.text(Json.obj("checks",checks,"fixtures",rows,"scope","Self-authored valid2063 MES26/1C and opaque800x600 desktop scenes; wait-1 feedback, late-tick gate, blocked input, pending/committed snapshot, global-palette lifetime and full-frame retirement. Not original Windows pixel comparison or complete native loader.","original_game_files_read",false,"complete_engine_fidelity_verified",false)).getBytes("UTF-8"));
        System.out.println("PASS "+checks+" PC1 wait-1 desktop return output assertions");
    }
}
