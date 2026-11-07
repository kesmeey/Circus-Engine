package org.circus.core;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import javax.imageio.ImageIO;
/** Actual DC2 atlas pixels and DesktopLegacy map output; independent manual crop/alpha oracle. */
public final class Dc2MapRendererAudit {
 private static int checks;private static long compared,rounded;private static int worst;
 private static void check(boolean yes,String why){checks++;if(!yes)throw new AssertionError(why);}
 private static int[] pixels(BufferedImage image){return image.getRGB(0,0,800,600,null,0,800);}
 private static String hash(int[] pixels){byte[] bytes=new byte[pixels.length*4];for(int n=0;n<pixels.length;n++)for(int k=0;k<4;k++)bytes[n*4+k]=(byte)(pixels[n]>>>(8*k));return Binary.sha256(bytes);}
 private static int[] expected(CircusResources data,List<Dc2Map.Sprite> sprites)throws IOException{
  int[] frame=new int[800*600];Arrays.fill(frame,0xff000000);
  for(Dc2Map.Sprite sprite:sprites){Crx.Image image=data.image(sprite.resource);check(image.bpp==24||image.bpp==32,"actual map image has reviewed BGR/BGRA layout");
   for(int y=0;y<sprite.height;y++){int dy=sprite.y+y,sy=sprite.sy+y;if(dy<0||dy>=600||sy<0||sy>=image.height)continue;
    for(int x=0;x<sprite.width;x++){int dx=sprite.x+x,sx=sprite.sx+x;if(dx<0||dx>=800||sx<0||sx>=image.width)continue;int at=sy*image.stride+sx*(image.bpp/8),b=image.pixels[at]&255,g=image.pixels[at+1]&255,r=image.pixels[at+2]&255;
     int alpha=sprite.opaque?255:image.bpp==32?image.pixels[at+3]&255:r==255&&g==0&&b==255?0:255;if(alpha==0)continue;int old=frame[dy*800+dx],inverse=255-alpha;
     int rr=(r*alpha+((old>>>16)&255)*inverse+127)/255,gg=(g*alpha+((old>>>8)&255)*inverse+127)/255,bb=(b*alpha+(old&255)*inverse+127)/255;frame[dy*800+dx]=0xff000000|rr<<16|gg<<8|bb;
    }
   }
  }return frame;
 }
 private static Map<String,Object> frame(DesktopLegacy engine,CircusResources data,Path out,String name)throws IOException{
  PcHost host=(PcHost)engine.host();BufferedImage image=engine.render();int[] actual=pixels(image),manual=expected(data,host.dc2Map.sprites(engine.vm().variables));long approximate=0;int max=0;
  for(int n=0;n<actual.length;n++){int delta=0;for(int shift:new int[]{0,8,16})delta=Math.max(delta,Math.abs((actual[n]>>>shift&255)-(manual[n]>>>shift&255)));if(delta>2)throw new AssertionError("DC2 map crop/composition mismatch "+name+" pixel"+(n%800)+","+(n/800)+" delta"+delta);if(delta>0)approximate++;max=Math.max(max,delta);}
  checks++;compared+=actual.length;rounded+=approximate;worst=Math.max(worst,max);ImageIO.write(image,"png",out.resolve(name+".png").toFile());image.flush();return Json.obj("name",name,"pixels",actual.length,"rounded_alpha_pixels",approximate,"maximum_rgb_rounding",max,"sha256",hash(actual));
 }
 private static int button(PcHost h,String action)throws IOException{for(int n=0;n<h.ui.buttons.size();n++)if(h.ui.buttons.get(n).action.equals(action))return n;throw new IOException("Original map action absent "+action);}
 private static DesktopLegacy engine(CircusResources data,TextCodec codec,String script)throws Exception{
  DesktopLegacy engine=new DesktopLegacy(data,codec,true);engine.session.vm=new MesVm(data.scripts(),engine.host(),script);check(engine.vm().run(1000)==MesVm.Status.WAIT_HOST&&((PcHost)engine.host()).dc2Map.active,"actual map MES opens in DesktopLegacy");return engine;
 }
 public static void main(String[] args)throws Exception{
  if(args.length!=2)throw new IOException("Require readonly_DC2_GAME owned_OUTPUT");Path game=Paths.get(args[0]).toRealPath(),out=DesktopRouteAudit.outputOutsideGame(game,args[1]);System.setProperty("dc.saveRoot",out.resolve("state").toString());System.setProperty("dc.cacheRoot",out.resolve("cache").toString());
  GameResources files=new GameResources(new LocalGameStorage(game),"dc2.exe");CircusResources data=new CircusResources(files,Profiles.get("dc2-3363"));TextCodec codec=GameText.detect(files,data.profile);List<Object> images=new ArrayList<>(),frames=new ArrayList<>();
  for(String path:files.paths())if(path.startsWith("advdata/grp/map/")&&path.endsWith(".crx")){Crx.Image image=data.image(path.substring(12));check(image.x==0&&image.y==0,"actual map atlas has origin0");images.add(Json.obj("path",path,"width",image.width,"height",image.height,"bpp",image.bpp,"decoded_sha256",Binary.sha256(image.pixels)));}
  check(images.size()==13,"all thirteen actual map resources decoded");
  for(String script:new String[]{"1218_nin_hiru","1218_nin_after"}){
   DesktopLegacy engine=engine(data,codec,script);PcHost host=(PcHost)engine.host();frames.add(frame(engine,data,out,script+"-initial"));
   for(int location:new int[]{0,1,2,3,5}){host.hoverMenu(button(host,"@dc2map:"+location));frames.add(frame(engine,data,out,script+"-hover"+location));}
   engine.vm().activateMenu(host.waitTicket(),button(host,"@dc2map:2"));host.hoverMenu(0);frames.add(frame(engine,data,out,script+"-confirm"));engine.vm().elapse(501);frames.add(frame(engine,data,out,script+"-tick501"));
   BufferedImage before=engine.render();int[] savedPixels=pixels(before);before.flush();byte[] saved=engine.snapshot();engine.vm().elapse(501);before=engine.render();int[] future=pixels(before);before.flush();engine.restore(saved);before=engine.render();check(Arrays.equals(savedPixels,pixels(before)),"actual DesktopLegacy full snapshot restores map pixels");before.flush();engine.vm().elapse(501);before=engine.render();check(Arrays.equals(future,pixels(before)),"actual DesktopLegacy restored map clock retains future pixels");before.flush();
  }
  Files.write(out.resolve("result.json"),(Json.text(Json.obj("checks",checks,"pass",true,"profile",data.profileId,"images",images,"frames",frames,"pixels_compared",compared,"alpha_rounding_pixels",rounded,"maximum_alpha_rgb_rounding",worst,"heap_max_bytes",Runtime.getRuntime().maxMemory(),"scope","Actual original assets and real DesktopLegacy player; geometry independently checked against native-machine draw journal. Manual BGR/BGRA source crop plus source-over oracle permits2 RGB levels for Java2D alpha rounding. No running-original screenshot/GDI/COM equivalence claimed.","original_exe_executed",false,"original_game_directory_written",false))+"\n").getBytes("UTF-8"));System.out.println("Dc2MapRendererAudit PASS "+checks+"; pixels="+compared+" max alpha rounding="+worst);
 }
}
