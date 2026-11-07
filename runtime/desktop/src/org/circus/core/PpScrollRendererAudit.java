package org.circus.core;

import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import javax.imageio.ImageIO;

/** Real DesktopDd rendering against an independent per-pixel GED row oracle.
 * Actual game files are read only; the original PE is never executed. */
public final class PpScrollRendererAudit {
    private static int checks;
    private static long comparedPixels;
    private static void check(boolean yes,String why){checks++;if(!yes)throw new AssertionError(why);}
    private static final class Session {
        final DesktopDd engine;final MesVm vm;int clock;
        Session(CircusResources data,TextCodec codec,boolean start)throws IOException{
            engine=new DesktopDd(data,codec,true);
            // Engine's original START VM is deliberately not used for snapshots.
            // Bind this three-byte command plus STOP script to the same render host.
            final MesScript code=new MesScript("pp-scroll-render",new byte[]{0,0,0,0,0x72,(byte)0x98,0,28,(byte)210,0,0,0,0},Profiles.get("dc3pp-9872"));
            vm=new MesVm(name->code,engine.host,code.name);
            if(start){
                engine.host.command("image.load",new int[]{1},"BG\\BLACK".getBytes(Binary.SJIS));
                engine.host.scene.slots[20].resource="ED/obsolete.PCK";
                engine.host.command("image.load",new int[]{20},"ED\\GED.PCK".getBytes(Binary.SJIS));
                check(vm.run(1)==MesVm.Status.WAIT_HOST&&vm.offset()==10,"real210 commits its PC and enters WAIT_HOST");
                check(engine.host.ppScrollCredits.strips()==19&&engine.host.ppScrollCredits.speed()==41,"host admits the actual nineteen-page archive");
                check(engine.host.ppScrollCredits.archive().equals("ED/GED.PCK"),"host consumes pending slot20 instead of stale current slot20");
            }
        }
        void advance(int target){
            if(target<clock)throw new IllegalArgumentException("Audit clock must advance");
            // Each poll moves at most41 pixels, retaining the native successive-page cache.
            while(clock<target){int step=Math.min(1000,target-clock);vm.elapse(step);clock+=step;}
        }
    }
    private static int[] pixels(BufferedImage image){return image.getRGB(0,0,image.getWidth(),image.getHeight(),null,0,image.getWidth());}
    private static String hash(int[] pixels){byte[] bytes=new byte[pixels.length*4];for(int n=0;n<pixels.length;n++)for(int k=0;k<4;k++)bytes[n*4+k]=(byte)(pixels[n]>>>(k*8));return Binary.sha256(bytes);}
    /** Explicit BGR access and row selection, independent of Java2D drawImage,
     * the live renderer's rectangle operations and Crx.Image.argb(). */
    private static int[] expected(CircusResources data,int current,int next,int row)throws IOException{
        Crx.Image old=current<0?null:data.image("pck:ED/GED.PCK#"+current),upcoming=next<0?null:data.image("pck:ED/GED.PCK#"+next);
        for(Crx.Image page:new Crx.Image[]{old,upcoming})if(page!=null)check(page.width==1024&&page.height==600&&page.bpp==24&&page.x==0&&page.y==0,"actual GED oracle uses origin-zero1024x600 BGR24");
        int[] result=new int[1024*576];Arrays.fill(result,0xff000000);
        for(int y=0;y<576;y++){
            int sy=y+row;Crx.Image page=sy<600?old:upcoming;if(sy>=600)sy-=600;if(page==null)continue;
            for(int x=0;x<1024;x++){
                int p=sy*page.stride+x*3,b=page.pixels[p]&255,g=page.pixels[p+1]&255,r=page.pixels[p+2]&255;
                if(r!=255||g!=0||b!=255)result[y*1024+x]=0xff000000|r<<16|g<<8|b;
            }
        }
        return result;
    }
    private static Map<String,Object> frame(Session s,CircusResources data,int requested,int row,Path out,String name)throws IOException{
        int current=requested==0?-1:requested-1,next=requested<19?requested:-1;PpScrollCredits c=s.engine.host.ppScrollCredits;
        check(c.active()&&c.requestedPage()==requested&&c.currentPage()==current&&c.nextPage()==next&&c.offset()==row,"independent expected page/row before render "+name);
        check(s.vm.status()==MesVm.Status.WAIT_HOST&&s.engine.host.read(100,29)==0,"drawing keeps actual VM blocked and completion flag unset "+name);
        BufferedImage image=s.engine.render();check(image.getWidth()==1024&&image.getHeight()==576,"real desktop viewport remains1024x576 "+name);
        int[] actual=pixels(image),expected=expected(data,current,next,row);
        for(int n=0;n<actual.length;n++)if(actual[n]!=expected[n])throw new AssertionError("PP roll renderer pixel "+name+" at "+(n%1024)+","+(n/1024)+" expected="+Integer.toHexString(expected[n])+" actual="+Integer.toHexString(actual[n]));
        check(true,"all desktop pixels match manual source rows "+name);comparedPixels+=actual.length;ImageIO.write(image,"png",out.resolve(name+".png").toFile());image.flush();
        long colored=0;for(int pixel:actual)if((pixel&0xffffff)!=0)colored++;
        return Json.obj("name",name,"clock_ms",s.clock,"requested",requested,"current",current,"next",next,"offset",row,"pixels_compared",actual.length,"nonblack_pixels",colored,"frame_sha256",hash(actual));
    }
    public static void main(String[]args)throws Exception{
        if(args.length!=2)throw new IOException("Require readonly_PP_GAME_ROOT owned_OUTPUT");
        Path game=Paths.get(args[0]).toRealPath(),out=DesktopRouteAudit.outputOutsideGame(game,args[1]);System.setProperty("dc.saveRoot",out.resolve("portable-state").toString());
        GameResources files=new GameResources(new LocalGameStorage(game),"DC3PP_CN_ruby.exe");CircusResources data=new CircusResources(files,Profiles.get("dc3pp-9872"));TextCodec codec=GameText.detect(files,data.profile);
        Session live=new Session(data,codec,true);java.util.List<Object> frames=new ArrayList<>();
        // 24 rows have entered the600-high native roll, still wholly below576.
        live.advance(586);frames.add(frame(live,data,0,24,out,"initial-offset24"));
        live.advance(15220);frames.add(frame(live,data,1,24,out,"page1-offset24"));
        live.advance(21952);Map<String,Object> middle=frame(live,data,1,300,out,"page1-offset300");frames.add(middle);
        byte[] saved=live.vm.snapshot();Files.write(out.resolve("midroll.cvs2"),saved);
        Session restored=new Session(data,codec,false);restored.vm.restore(saved);restored.clock=live.clock;
        check(Arrays.equals(saved,restored.vm.snapshot()),"custom CVS2 VM restores byte-identical host and roll state");Map<String,Object> recovered=frame(restored,data,1,300,out,"restored-offset300");frames.add(recovered);
        check(middle.get("frame_sha256").equals(recovered.get("frame_sha256")),"middle actual desktop pixels survive full custom VM restore");
        int[][] future={{29244,1,599},{153659,10,300},{278049,19,0}};
        for(int[] sample:future){
            live.advance(sample[0]);restored.advance(sample[0]);String label=sample[1]==19?"eof":"page"+sample[1]+"-offset"+sample[2];
            Map<String,Object> a=frame(live,data,sample[1],sample[2],out,label),b=frame(restored,data,sample[1],sample[2],out,"restored-"+label);frames.add(a);frames.add(b);
            check(a.get("frame_sha256").equals(b.get("frame_sha256")),"future actual desktop output survives custom VM restore "+label);check(Arrays.equals(live.vm.snapshot(),restored.vm.snapshot()),"future full VM state stays identical "+label);
        }
        check(live.engine.host.ppScrollCredits.finishing()&&restored.engine.host.ppScrollCredits.finishing(),"EOF frame is retained before completion");live.vm.elapse(1);restored.vm.elapse(1);
        check(!live.engine.host.ppScrollCredits.active()&&!restored.engine.host.ppScrollCredits.active()&&live.engine.host.read(100,29)==1&&restored.engine.host.read(100,29)==1,"next real VM poll completes and writes persistent100:29");
        check(live.vm.run(1)==MesVm.Status.HALTED&&restored.vm.run(1)==MesVm.Status.HALTED,"both custom VMs resume their actual following STOP");
        Files.write(out.resolve("result.json"),(Json.text(Json.obj("checks",checks,"pass",true,"profile",data.profileId,"target_sha256",Binary.sha256(files.read("DC3PP_CN_ruby.exe",64*1024*1024)),"archive_sha256",Binary.sha256(files.read("AdvData/GRP/ED/GED.PCK",4*1024*1024)),"producer_classes",DesktopDd.class.getProtectionDomain().getCodeSource().getLocation().toString(),"actual_archive_pages",19,"viewport",new int[]{1024,576},"native_page_height",600,"total_pixels_compared",comparedPixels,"frames",frames,"snapshot","Custom MES VM bound to engine.host; engine.snapshot/original START VM are not used","scope","actual portable DesktopDd/Host210/CVS2 integration against independent per-pixel decoded BGR row copies; no comparison against a running original renderer","original_exe_executed",false,"original_game_directory_written",false))+"\n").getBytes("UTF-8"));
        System.out.println("PpScrollRendererAudit PASS "+checks+"; "+comparedPixels+" actual desktop pixels matched manual GED rows");
    }
}
