package org.circus.core;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;

/** Actual PP SACRA02 decoding, live output and full VM snapshot continuation.
 * Reads archives and PE identity only; never launches the original executable.
 */
public final class PpParticlesAudit {
    private static int checks;
    private static void check(boolean value,String why){checks++;if(!value)throw new AssertionError(why);}
    private static int[] pixels(BufferedImage image){return image.getRGB(0,0,image.getWidth(),image.getHeight(),null,0,image.getWidth());}
    private static String hash(int[] pixels){byte[] bytes=new byte[pixels.length*4];for(int n=0;n<pixels.length;n++)for(int k=0;k<4;k++)bytes[n*4+k]=(byte)(pixels[n]>>>(k*8));return Binary.sha256(bytes);}
    public static void main(String[] args)throws Exception{
        if(args.length!=2)throw new IOException("Require readonly_PP_GAME_ROOT owned_OUTPUT");Path game=Paths.get(args[0]).toRealPath(),out=DesktopRouteAudit.outputOutsideGame(game,args[1]);System.setProperty("dc.saveRoot",out.resolve("portable-state").toString());
        GameResources files=new GameResources(new LocalGameStorage(game),"DC3PP_CN_ruby.exe");CircusResources data=new CircusResources(files,Profiles.get("dc3pp-9872"));TextCodec codec=GameText.detect(files,data.profile);DesktopDd engine=new DesktopDd(data,codec,true);
        String atlas="SYSTEM\\SACRA02";Crx.Image image=data.image(atlas);check(image.width>=256&&image.height>=164,"actual six-row 32x24 sprite atlas geometry");
        engine.host.scene.slots[engine.host.scene.layout.petals].resource=atlas;engine.host.scene.petals.startPp4(0x42,true);engine.host.scene.elapse(2000);
        Dc3PpParticles effect=engine.host.scene.petals.pp4();check(effect.activeCount()==10&&effect.windKind()==2&&effect.windX()==-12,"actual script strength 0x42 selects ten petals and strong left wind");int inView=0,back=0,front=0;for(Dc3PpParticles.Sprite sprite:effect.sprites()){if(sprite.x<1024&&sprite.x+sprite.width>0&&sprite.y<576&&sprite.y+sprite.height>0)inView++;if(sprite.object==1)back++;else if(sprite.object==7)front++;check(sprite.sx>=0&&sprite.sx+sprite.width<=image.width&&sprite.sy>=0&&sprite.sy+sprite.height<=image.height,"native crop stays within actual atlas");}check(inView>0&&back>0&&front>0,"real atlas has visible particles in both native passes");
        BufferedImage shown=engine.render();int[] before=pixels(shown);long colored=0;for(int pixel:before)if((pixel&0xffffff)!=0)colored++;check(colored>50,"actual PP renderer paints particle pixels");ImageIO.write(shown,"png",out.resolve("pp4-2000ms.png").toFile());shown.flush();
        byte[] saved=engine.snapshot();engine.host.scene.elapse(617);BufferedImage continued=engine.render();int[] expected=pixels(continued);ImageIO.write(continued,"png",out.resolve("pp4-2617ms.png").toFile());continued.flush();check(!Arrays.equals(before,expected),"actual particle picture advances with elapsed time");
        engine.restore(saved);BufferedImage restored=engine.render();check(Arrays.equals(before,pixels(restored)),"full VM snapshot restores actual particle frame");restored.flush();for(int n=0;n<617;n++)engine.host.scene.elapse(1);restored=engine.render();check(Arrays.equals(expected,pixels(restored)),"full VM snapshot restores RNG, frame phase and future crop output");restored.flush();
        engine.host.scene.petals.startPp4(0,true);shown=engine.render();long stopped=0;for(int pixel:pixels(shown))if((pixel&0xffffff)!=0)stopped++;shown.flush();check(stopped==0,"zero strength removes particle pixels");
        Files.write(out.resolve("result.json"),(Json.text(Json.obj("checks",checks,"profile",data.profileId,"target_sha256",Binary.sha256(files.read("DC3PP_CN_ruby.exe",64*1024*1024)),"atlas",atlas,"atlas_width",image.width,"atlas_height",image.height,"strength",66,"in_view",inView,"native_object_1",back,"native_object_7",front,"colored_pixels",colored,"frame_2000ms_sha256",hash(before),"frame_2617ms_sha256",hash(expected),"scope","native integer init/update/draw arguments; 60Hz portable frame clock and outer two-pass compositor placement; effect-owned RNG","original_exe_executed",false))+"\n").getBytes("UTF-8"));System.out.println("PpParticlesAudit PASS "+checks+"; visible="+inView+" colored="+colored);
    }
}
