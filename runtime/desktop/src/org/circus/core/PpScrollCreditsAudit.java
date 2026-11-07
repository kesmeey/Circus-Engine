package org.circus.core;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import javax.imageio.ImageIO;

/** Read-only actual PP GED archive admission and portable crop preview.
 * This audit does not execute the original PE or establish native GPU pixels. */
public final class PpScrollCreditsAudit {
    private static int checks;
    private static void check(boolean yes,String why){checks++;if(!yes)throw new AssertionError(why);}
    private static BufferedImage image(CircusResources data,PpScrollCredits c,int page)throws IOException {
        if(page<0)return null;Crx.Image decoded=data.image(c.image(page));BufferedImage b=new BufferedImage(decoded.width,decoded.height,BufferedImage.TYPE_INT_ARGB);b.setRGB(0,0,decoded.width,decoded.height,decoded.argb(true),0,decoded.width);return b;
    }
    private static BufferedImage preview(CircusResources data,PpScrollCredits c)throws IOException {
        BufferedImage frame=new BufferedImage(PpScrollCredits.WIDTH,576,BufferedImage.TYPE_INT_ARGB);Graphics2D g=frame.createGraphics();
        try{
            g.setColor(Color.BLACK);g.fillRect(0,0,frame.getWidth(),frame.getHeight());int split=PpScrollCredits.HEIGHT-c.offset();
            BufferedImage old=image(data,c,c.currentPage()),next=image(data,c,c.nextPage());
            if(old!=null){g.drawImage(old,0,0,PpScrollCredits.WIDTH,split,0,c.offset(),PpScrollCredits.WIDTH,PpScrollCredits.HEIGHT,null);old.flush();}
            if(next!=null){g.drawImage(next,0,split,PpScrollCredits.WIDTH,PpScrollCredits.HEIGHT,0,0,PpScrollCredits.WIDTH,c.offset(),null);next.flush();}
        }finally{g.dispose();}return frame;
    }
    public static void main(String[]args)throws Exception{
        if(args.length!=2)throw new IOException("Require readonly_PP_GAME_ROOT owned_OUTPUT");
        Path game=Paths.get(args[0]).toRealPath(),out=DesktopRouteAudit.outputOutsideGame(game,args[1]);
        GameResources files=new GameResources(new LocalGameStorage(game),"DC3PP_CN_ruby.exe");CircusResources data=new CircusResources(files,Profiles.get("dc3pp-9872"));PpScrollCredits c=new PpScrollCredits();c.start(data,"ED\\GED.PCK");
        check(c.strips()==19&&c.speed()==41,"actual GED has nineteen strips and native rate41");Archive archive=files.archive("AdvData/GRP/ED/GED.PCK");java.util.List<Object> pages=new ArrayList<>();
        long pixels=0;
        for(int n=0;n<c.strips();n++){
            Crx.Image decoded=data.image(c.image(n));check(decoded.width==1024&&decoded.height==600,"actual native strip geometry "+n);check(decoded.x==0&&decoded.y==0,"actual native strip origin "+n);pixels+=(long)decoded.width*decoded.height;
            pages.add(Json.obj("index",n,"name",archive.entries.get(n).name,"width",decoded.width,"height",decoded.height,"bpp",decoded.bpp,"decoded_sha256",Binary.sha256(decoded.pixels)));
        }
        int visited=0;
        for(int n=0;n<=c.strips();n++){
            int clock=(n*600000+c.speed()-1)/c.speed();check(c.update(clock)==PpScrollCredits.Step.DRAW,"EOF discovery still draws "+n);check(c.requestedPage()==n,"actual sequential requested page "+n);
            if(n<c.strips()){check(c.nextPage()==n&&!c.finishing(),"each real GED page admitted "+n);visited++;}else check(c.currentPage()==18&&c.nextPage()==-1&&c.offset()==0&&c.finishing(),"EOF displays final current strip at offset0");
        }
        check(visited==19&&c.active(),"all pages read before deferred completion");check(c.elapse(1)==PpScrollCredits.Step.COMPLETE&&!c.active(),"completion happens on next poll");
        java.util.List<Object> frames=new ArrayList<>();
        for(int at:new int[]{1000,21952,146342,278049}){
            c.start(data,"ED/GED.PCK");for(int clock=0;clock<at;clock+=100)c.update(clock);c.update(at);BufferedImage frame=preview(data,c);String name="preview-"+at+".png";ImageIO.write(frame,"png",out.resolve(name).toFile());frame.flush();frames.add(Json.obj("clock",at,"current",c.currentPage(),"next",c.nextPage(),"offset",c.offset(),"file",name));
        }
        Files.write(out.resolve("result.json"),(Json.text(Json.obj("checks",checks,"profile",data.profileId,"game",game.toString(),"target_sha256",Binary.sha256(files.read("DC3PP_CN_ruby.exe",64*1024*1024)),"archive_sha256",Binary.sha256(files.read("AdvData/GRP/ED/GED.PCK",4*1024*1024)),"pages",pages,"decoded_pixels",pixels,"frames",frames,"scope","actual read-only archive decoding and portable two-rectangle crop preview; no native GPU or original process execution","original_exe_executed",false))+"\n").getBytes("UTF-8"));
        System.out.println("PpScrollCreditsAudit PASS "+checks+"; all19 actual GED strips, "+pixels+" decoded pixels");
    }
}
