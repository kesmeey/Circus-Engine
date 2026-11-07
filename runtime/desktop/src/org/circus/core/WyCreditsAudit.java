package org.circus.core;
import java.io.*;
import java.lang.reflect.*;
import java.nio.file.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.*;
import javax.imageio.ImageIO;

/** Actual WY archives and portable crop pixels; no original process/audio. */
public final class WyCreditsAudit {
 private static int count;
 private static void check(boolean yes,String why){count++;if(!yes)throw new AssertionError(why);}
 public static void main(String[] args)throws Exception{
  if(args.length!=2)throw new IOException("Require readonly_WY_GAME_ROOT owned_OUTPUT");Path out=Paths.get(args[1]).toAbsolutePath().normalize();Files.createDirectories(out);System.setProperty("dc.saveRoot",out.resolve("portable-state").toString());GameResources files=new GameResources(new LocalGameStorage(Paths.get(args[0])),"DC3WY_CN.exe");CircusResources data=new CircusResources(files,Profiles.get("dc3wy-a09f"));DesktopDd engine=new DesktopDd(data,GameText.detect(files,data.profile),true);Dc3Credits c=engine.host.credits;
  String[] tags={"RCC","SRR","HMN","SRA","AOI","SKI","MKT","SMM","HTN"};int[] expected={9,8,8,6,6,9,8,9,11};Method draw=DesktopDd.class.getDeclaredMethod("creditStrip",Graphics2D.class,Dc3Credits.class,int.class,float.class);draw.setAccessible(true);long opaque=0,transparent=0;java.util.List<Object> rows=new ArrayList<>();
  for(int route=0;route<9;route++){
   c.start(data,route,false);check(c.pictures==expected[route]&&c.strips==23,"actual WY archive pages "+route);check(c.archive("01").contains(tags[route]),"actual route resource "+route);check(c.duration==(route<=4?112000:97000),"native duration choice "+route);
   for(String suffix:new String[]{"01","02"})for(int index:new int[]{0,c.pictures-1}){Crx.Image image=data.image(c.image(suffix,index));check(image.width>0&&image.height>0,"actual first/last photo pixels "+route+suffix+index);}
   Crx.Image strip=data.image(c.image("",0));int[] original=strip.argb(true);check(strip.width==562&&strip.height==576,"actual common lettering geometry");BufferedImage frame=new BufferedImage(engine.width,engine.height,BufferedImage.TYPE_INT_ARGB);Graphics2D g=frame.createGraphics();int seed=0xff1964a0;try{g.setColor(new Color(seed,true));g.fillRect(0,0,frame.getWidth(),frame.getHeight());draw.invoke(engine,g,c,0,0f);}catch(InvocationTargetException e){throw new IOException("actual crop consumer",e.getCause());}finally{g.dispose();}
   int left=c.stripCropLeft(),x=c.stripX(strip.width);for(int y=0;y<strip.height;y++)for(int sx=left;sx<strip.width;sx++){int raw=original[y*strip.width+sx],alpha=raw>>>24,actual=frame.getRGB(x+sx-left,y);if(alpha==255){if(actual!=raw)throw new AssertionError("WY opaque crop pixel "+route+":"+sx+","+y);opaque++;}else if(alpha==0){if(actual!=seed)throw new AssertionError("WY transparent crop pixel "+route+":"+sx+","+y);transparent++;}}
   check(frame.getRGB(x-1,0)==seed&&frame.getRGB(x+strip.width-left,0)==seed,"no paint outside native crop destination "+route);rows.add(Json.obj("route",route,"tag",tags[route],"pictures",c.pictures,"strips",c.strips,"duration",c.duration,"period",c.period(),"crop_left",left,"destination_x",x));frame.flush();
   if(route==0||route==8){for(long at:new long[]{3000,c.duration/2,c.end()-1000}){c.elapsed=at;BufferedImage shown=engine.render();ImageIO.write(shown,"png",out.resolve("route"+route+"-"+at+".png").toFile());shown.flush();}}
   c.elapsed=0;c.elapse(c.end());check(!c.active(),"compatibility clock ends actual WY content "+route);
  }
  check(opaque+transparent>100000,"whole crop compares actual endpoint-alpha pixels");Files.write(out.resolve("result.json"),(Json.text(Json.obj("checks",count,"routes",rows,"opaque_pixels_compared",opaque,"transparent_pixels_compared",transparent,"phase_clock","elapsed compatibility, not native update/input/tween equivalence","original_exe_executed",false))+"\n").getBytes("UTF-8"));System.out.println("WyCreditsAudit PASS "+count+"; opaque="+opaque+" transparent="+transparent+"; all9 actual archive routes");
 }
}
