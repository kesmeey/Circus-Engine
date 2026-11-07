package org.circus.core;

import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import javax.imageio.ImageIO;

/** Read-only DC2 original GEND/resources plus actual portable desktop output.
 * Controlled flag writes below are isolated skip-test fixtures, not route policy. */
public final class Dc2OriginalCreditsAudit {
    private static int checks;private static long compared;
    private static void check(boolean ok,String why){checks++;if(!ok)throw new AssertionError(why);}
    private static PcHost host(DesktopLegacy e){return(PcHost)e.host();}
    private static Dc2OriginalCredits credits(DesktopLegacy e){return host(e).dc2Credits;}
    private static int[] pixels(BufferedImage b){return b.getRGB(0,0,800,600,null,0,800);}
    private static String hash(int[] pixels){byte[] raw=new byte[pixels.length*3];for(int n=0;n<pixels.length;n++)for(int k=0;k<3;k++)raw[n*3+k]=(byte)(pixels[n]>>>(8*k));return Binary.sha256(raw);}
    private static int[] expected(CircusResources data,List<Dc2OriginalCredits.Strip> strips)throws IOException {
        int[] out=new int[800*600];Arrays.fill(out,0xff000000);
        for(Dc2OriginalCredits.Strip s:strips){Crx.Image image=data.image(s.resource);check(image.bpp==24,"actual DC2 native opaque blitter source is24bpp");for(int y=0;y<s.height;y++)for(int x=0;x<s.width;x++){int src=(s.srcY+y)*image.stride+(s.srcX+x)*3;out[(s.y+y)*800+s.x+x]=0xff000000|(image.pixels[src+2]&255)<<16|(image.pixels[src+1]&255)<<8|(image.pixels[src]&255);}}
        return out;
    }
    private static Map<String,Object> frame(DesktopLegacy e,CircusResources data,Path out,String name)throws IOException {
        BufferedImage b=e.render();int[] actual=pixels(b),manual=expected(data,credits(e).frame());check(Arrays.equals(actual,manual),"actual desktop opaque crop agrees exactly with independent BGR oracle: "+name);compared+=actual.length;ImageIO.write(b,"png",out.resolve(name+".png").toFile());b.flush();return Json.obj("name",name,"draw_page",credits(e).drawPage,"draw_row",credits(e).drawRow,"sha256",hash(actual));
    }
    private static DesktopLegacy gend(CircusResources data,TextCodec codec)throws Exception {
        DesktopLegacy e=new DesktopLegacy(data,codec,true);e.session.vm=new MesVm(data.scripts(),e.host(),"gend");
        check(e.vm().run(1000)==MesVm.Status.WAIT_HOST&&credits(e).active,"actual original GEND reaches system12 modal wait");
        check(e.vm().variables.read(100,59)==0&&!e.host().skipAllowed,"actual GEND leaves first-clear flag0 and disables story skip before credits");return e;
    }
    public static void main(String[] args)throws Exception {
        if(args.length!=2)throw new IOException("Require readonly_DC2_GAME owned_OUTPUT");Path game=Paths.get(args[0]).toRealPath(),out=DesktopRouteAudit.outputOutsideGame(game,args[1]);System.setProperty("dc.saveRoot",out.resolve("state").toString());System.setProperty("dc.cacheRoot",out.resolve("cache").toString());
        GameResources files=new GameResources(new LocalGameStorage(game),"dc2.exe");CircusResources data=new CircusResources(files,Profiles.get("dc2-3363"));TextCodec codec=GameText.detect(files,data.profile);List<Object> assets=new ArrayList<>(),frames=new ArrayList<>();
        for(int variant=0;variant<=1;variant++){Dc2OriginalCredits c=new Dc2OriginalCredits();c.start(variant);for(int page=0;page<19;page++){String name=c.image(page);Crx.Image image=data.image(name);check(image.x==0&&image.y==0&&image.width==800&&image.height==600&&image.bpp==24,"original native page geometry "+name);assets.add(Json.obj("resource",name,"x",image.x,"y",image.y,"width",image.width,"height",image.height,"bpp",image.bpp,"decoded_sha256",Binary.sha256(image.pixels)));}}
        DesktopLegacy e=gend(data,codec);Dc2OriginalCredits c=credits(e);String music=e.host().audio[0];int musicGeneration=e.host().audioGeneration[0];
        check(e.modalKey(KeyEvent.VK_ENTER)&&e.modalKey(KeyEvent.VK_SPACE)&&e.modalKey(KeyEvent.VK_ESCAPE),"credits modal consumes keys without synthesizing mouse clicks");e.click(400,300,false);e.click(400,300,true);check(c.active&&c.skipRequested()==0,"first-clear mouse input cannot skip native credits");e.vm().signalHost(e.ticket(),MesVm.HostSignal.ADVANCE);check(c.active&&c.skipRequested()==0,"generic ADVANCE does not impersonate a mouse skip");
        e.vm().elapse(7422);frames.add(frame(e,data,out,"first-poll-old-row0"));e.vm().elapse(0);check(c.drawRow==0,"zero-time refresh cannot poll credits again");e.vm().elapse(50);check(c.drawRow==300,"actual owner retains sampled row300 for next native draw");frames.add(frame(e,data,out,"two-pages-row300"));
        byte[] saved=e.snapshot();BufferedImage before=e.render();int[] savedPixels=pixels(before);before.flush();e.vm().elapse(1000);before=e.render();int[] future=pixels(before);before.flush();e.restore(saved);c=credits(e);before=e.render();check(Arrays.equals(savedPixels,pixels(before)),"full snapshot restores exact credits front");before.flush();e.vm().elapse(1000);before=e.render();check(Arrays.equals(future,pixels(before)),"full snapshot reproduces next clock-gated credits front");before.flush();
        for(int n=0;n<100&&c.page<18;n++)e.vm().elapse(600000);check(c.active&&c.page==18,"large clock polling still walks each of19 actual pages");e.vm().elapse(50);frames.add(frame(e,data,out,"last-page-fixed"));e.vm().elapse(9920-c.elapsed());check(c.active&&c.row==401,"last page waits through400-row strict boundary");e.vm().elapse(50);check(!c.active&&e.vm().variables.read(100,59)==0,"native owner completes without preempting the following MES flag write");frames.add(frame(e,data,out,"natural-complete-front-retained"));
        check(e.host().audio[0].equals(music)&&e.host().audioGeneration[0]==musicGeneration,"credits do not invent a music stop or restart");
        e.vm().run(1);check(e.vm().variables.read(100,59)==1,"following original GEND variable opcode performs first-clear write");frames.add(frame(e,data,out,"post-flag-front-retained"));e.vm().run(1);BufferedImage fade=e.render();int[] last=expected(data,Collections.singletonList(c.frame().get(0)));check(Arrays.equals(last,pixels(fade)),"GEND fade begins from final credits page without old-story flash");ImageIO.write(fade,"png",out.resolve("fade-begins-final-page.png").toFile());fade.flush();
        e.vm().elapse(1000);e.vm().run(2);BufferedImage black=e.render();int[] afterBlack=pixels(black);for(int pixel:afterBlack)if((pixel&0xffffff)!=0)throw new AssertionError("credits journal must release presentation for original GEND black/wait");checks++;ImageIO.write(black,"png",out.resolve("gend-black-wait.png").toFile());black.flush();e.vm().elapse(1001);e.vm().run(1000);String title=e.vm().scriptName();check(title.toLowerCase(Locale.ROOT).startsWith("title_")&&e.host().skipAllowed&&e.vm().status()!=MesVm.Status.FAULT,"ordinary GEND resumes through black wait and restores skip before title transfer: "+title+"@"+e.vm().offset()+" skip="+e.host().skipAllowed+" error="+e.vm().error());
        DesktopLegacy skipped=gend(data,codec);skipped.vm().elapse(7422);skipped.vm().elapse(50);skipped.vm().variables.write(100,59,1);skipped.click(400,300,false);check(credits(skipped).active&&credits(skipped).skipRequested()==1,"real left mouse enqueues eligible skip without ending in event handler");skipped.vm().elapse(0);check(credits(skipped).active,"queued mouse skip respects next native timer");skipped.vm().elapse(50);check(!credits(skipped).active,"eligible mouse skip completes on next native update");frames.add(frame(skipped,data,out,"eligible-skip-current-front"));
        DesktopLegacy sakura=gend(data,codec);host(sakura).openOriginalCredits(1);for(int page=0;page<3;page++){sakura.vm().elapse(15000);sakura.vm().elapse(50);}sakura.vm().elapse(7422);sakura.vm().elapse(50);check(credits(sakura).page==3&&credits(sakura).drawRow==300,"isolated native variant1 fixture reaches distinct Sakura pages4/5");frames.add(frame(sakura,data,out,"sakura-pages4-5-row300"));sakura.vm().variables.write(100,59,1);sakura.click(400,300,true);sakura.vm().elapse(50);check(credits(sakura).active,"real right mouse flag1 cannot skip Sakura variant");sakura.vm().variables.write(100,59,2);sakura.click(400,300,true);sakura.vm().elapse(50);check(!credits(sakura).active,"real right mouse flag2 completes Sakura on poll");frames.add(frame(sakura,data,out,"sakura-eligible-skip-front"));
        Files.write(out.resolve("result.json"),(Json.text(Json.obj("pass",true,"checks",checks,"target_sha256",Binary.sha256(files.read("dc2.exe",32*1024*1024)),"assets",assets,"frames",frames,"pixels_compared",compared,"heap_max_bytes",Runtime.getRuntime().maxMemory(),"actual_gend_entry",true,"first_clear_mouse_cannot_skip",true,"snapshot_future_pixels",true,"natural_final_front_retained",true,"gend_black_wait_title_released",true,"title_script",title,"original_exe_executed",false,"original_game_directory_written",false,"scope","Original GEND script,38 actual19-page image tables, exact opaque BGR crop oracle, native update/skip plus independent portable session front. This is not a screenshot comparison against a running original. Controlled bank100 flag1/2 writes and direct variant1 start are isolated skip/renderer fixtures, not any story route policy."))+"\n").getBytes("UTF-8"));System.out.println("Dc2OriginalCreditsAudit PASS "+checks+"; pixels="+compared);
    }
}
