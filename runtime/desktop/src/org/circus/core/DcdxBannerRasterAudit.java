package org.circus.core;

import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import javax.imageio.ImageIO;

/** Actual DesktopLegacy banner raster and original title images against a
 * separate native40A300 pixel oracle. The tiny instruction fixture supplies
 * real1C12 VM waits; it is not a claim of original-EXE screen equivalence. */
public final class DcdxBannerRasterAudit {
    private static final int W=1024,H=576;
    private static final int[] FINAL_X={0,13,27,56,91},TOP={232,296,360,424,488},ALT_Y={576,320,384,448,512};
    private static int checks;private static long compared;
    private static void check(boolean ok,String why)throws IOException{checks++;if(!ok)throw new IOException(why);}
    private static DcdxHost host(DesktopLegacy e){return(DcdxHost)e.host();}
    private static int[] pixels(BufferedImage image)throws IOException{check(image.getWidth()==W&&image.getHeight()==H,"Original DCDX canvas remains1024x576");return image.getRGB(0,0,W,H,null,0,W);}
    private static String hash(int[] values){byte[] bytes=new byte[values.length*3];for(int n=0;n<values.length;n++){bytes[n*3]=(byte)values[n];bytes[n*3+1]=(byte)(values[n]>>>8);bytes[n*3+2]=(byte)(values[n]>>>16);}return Binary.sha256(bytes);}
    private static int[] background(Crx.Image raw)throws IOException{
        check(raw.bpp==24&&raw.width==W&&raw.height==H&&raw.x==0&&raw.y==0,"Native background is unscaled original1024x576 BGR24");int[] out=new int[W*H];
        for(int y=0;y<H;y++)for(int x=0;x<W;x++){int p=y*raw.stride+x*3;out[y*W+x]=0xff000000|(raw.pixels[p+2]&255)<<16|(raw.pixels[p+1]&255)<<8|(raw.pixels[p]&255);}return out;
    }
    /** Independent iteration: no production layer journal or blitter is used.
     * 40EA30 repairs the old rectangle;40F0A0 keys BGR24 or40EE80 applies
     * normalized source opacity with integer /255 to the new rectangle. */
    private static boolean nativePoll(int mode,int[] x,int[] front,int[] bg,Crx.Image atlas){
        int finished=0,step=atlas.bpp/8;
        for(int item=0;item<5;item++){
            int old=x[item],y=TOP[item];
            for(int py=y;py<y+64;py++)for(int px=Math.max(0,old);px<Math.min(W,old+472);px++)front[py*W+px]=bg[py*W+px];
            int target=(mode&1)==0?FINAL_X[item]:-472;if(old==target)finished++;
            else x[item]=old<target?Math.min(target,old+24):Math.max(target,old-24);
            int sourceY=mode<2?item*64:ALT_Y[item],left=x[item];
            for(int py=y;py<y+64;py++)for(int px=Math.max(0,left);px<Math.min(W,left+472);px++){
                int sx=px-left,sy=sourceY+py-y;if(sx<0||sy<0||sx>=atlas.width||sy>=atlas.height)continue;
                int at=sy*atlas.stride+sx*step,b=atlas.pixels[at]&255,g=atlas.pixels[at+1]&255,r=atlas.pixels[at+2]&255;
                if(step==3&&b==255&&g==0&&r==255)continue;
                int alpha=step==4?atlas.pixels[at+3]&255:255;if(alpha==0)continue;
                int index=py*W+px,prior=front[index];
                front[index]=0xff000000|((r*alpha+(prior>>>16&255)*(255-alpha))/255)<<16|((g*alpha+(prior>>>8&255)*(255-alpha))/255)<<8|(b*alpha+(prior&255)*(255-alpha))/255;
            }
        }
        return finished==5;
    }
    private static Map<String,Object> frame(DesktopLegacy e,int[] expected,Path out,String name,boolean png)throws Exception{
        BufferedImage image=e.render();int[] got=pixels(image);compared+=expected.length;
        if(!Arrays.equals(got,expected)){ImageIO.write(image,"png",out.resolve(name+"-actual-mismatch.png").toFile());for(int n=0;n<got.length;n++)if(got[n]!=expected[n])throw new IOException(name+" pixel"+(n%W)+","+(n/W)+" actual="+Integer.toHexString(got[n])+" expected="+Integer.toHexString(expected[n]));}
        check(true,name+" actual desktop pixels equal independent native iteration");if(png)ImageIO.write(image,"png",out.resolve(name+".png").toFile());image.flush();
        image=e.render();check(Arrays.equals(got,pixels(image)),name+" repeated rendering cannot replay the journal twice");image.flush();
        DcdxBanner b=host(e).dcdxBanner;return Json.obj("sample",name,"generation",b.generation,"mode",b.mode,"polls",b.polls,"active",b.active,"sourceSerial",b.sourceSerial,"draw_calls",b.rawLayers().size(),"BGR_sha256",hash(got));
    }
    private static byte[] ownerBytes(DcdxBanner b)throws IOException{ByteArrayOutputStream out=new ByteArrayOutputStream();b.save(new DataOutputStream(out));return out.toByteArray();}
    private static void word(ByteArrayOutputStream out,int n){out.write(n);out.write(n>>>8);}
    private static CircusResources fixture(final CircusResources data)throws Exception{
        ByteArrayOutputStream code=new ByteArrayOutputStream();code.write(new byte[]{0,0,0,0,0x69,0x77});
        for(int mode=0;mode<4;mode++){code.write(0x1c);code.write(12);code.write(mode);code.write(0x5c);for(int value:new int[]{8,11,mode+1,0})word(code,value);}
        code.write(new byte[3]);final MesScript script=new MesScript("START",code.toByteArray(),data.profile);
        return new CircusResources(data.files,data.profile){@Override public MesVm.Library scripts(){return name->{if(!name.equalsIgnoreCase("START"))throw new IOException("Banner fixture exposes only START");return script;};}};
    }
    public static void main(String[] args)throws Exception{
        if(args.length!=2)throw new IOException("Require readonly_DCDX_GAME owned_OUTPUT");Path game=Paths.get(args[0]).toRealPath(),out=DesktopRouteAudit.outputOutsideGame(game,args[1]);
        System.setProperty("dc.saveRoot",out.resolve("saves").toString());System.setProperty("dc.cacheRoot",out.resolve("cache").toString());
        Map<String,Object> report=Json.obj("schema",1,"scope","Original title prepared surfaces and native40A300 schedule; actual DesktopLegacy pixels plus true VM1C12 WAIT_HOST continuation/snapshots. This is not original-EXE full-screen equivalence.","original_executable_run",false,"original_game_files_written",false);List<Object> frames=new ArrayList<Object>(),assets=new ArrayList<Object>();
        try{
            String exeSha=Binary.sha256(Files.readAllBytes(game.resolve("DCDX.EXE")));check(exeSha.equals("86bcd33e954b81f9b7bb731bcbc2886bc68d8d069074a32ef2af716fede395cb"),"Banner fixtures bind to independently reviewed original DCDX executable bytes");report.put("exe_sha256",exeSha);
            GameResources files=new GameResources(new LocalGameStorage(game),"DCDX.EXE");Profile profile=GameVersion.detect(files).profile();check(profile.id.equals("dcdx-7769"),"Independent DCDX MES version admission");CircusResources original=new CircusResources(files,profile);TextCodec codec=GameText.detect(files,profile);
            String[] names={"system/main_back","system/title_back","system/title_chip"};Crx.Image[] images=new Crx.Image[3];
            for(int n=0;n<names.length;n++){images[n]=original.image(names[n]);Crx.Image raw=images[n];assets.add(Json.obj("resource",names[n],"width",raw.width,"height",raw.height,"bpp",raw.bpp,"decoded_payload_sha256",Binary.sha256(raw.pixels)));}
            int[] old=background(images[0]),bg=background(images[1]);Crx.Image atlas=images[2];check((atlas.bpp==24||atlas.bpp==32)&&atlas.width>=472&&atlas.height>=640,"Original title atlas covers both native source tables");
            DesktopLegacy e=new DesktopLegacy(fixture(original),codec,true);DcdxHost h=host(e);
            h.command("pc.background",new int[0],names[0].getBytes(Binary.SJIS));h.scene.publish(h.ui,0,0);BufferedImage before=e.render();check(Arrays.equals(old,pixels(before)),"Old front is captured through actual desktop background rendering");before.flush();
            h.command("pc.background",new int[0],names[1].getBytes(Binary.SJIS));h.command("pc.image6",new int[0],names[2].getBytes(Binary.SJIS));
            check(e.vm().run(1)==MesVm.Status.WAIT_HOST&&e.vm().offset()==9,"Actual1C12 commits its continuation and enters true VM WAIT_HOST");
            check(Arrays.equals(host(e).dcdxBanner.initial.pixels().argb,old)&&Arrays.equals(host(e).dcdxBanner.bg.pixels().argb,bg),"Native producer captures the old front separately from its pure original BG restore surface");
            int[] front=old.clone();int tick=0,elapsed=0;
            for(int mode=0;mode<4;mode++){
                h=host(e);DcdxBanner b=h.dcdxBanner;check(b.active&&h.bannerVisible()&&b.mode==mode&&b.sourceSerial==h.scene.transitionSerial,"Native1C12 owns current visible frame serial for stage"+mode);
                check(e.vm().variables.read(8,11)==mode,"Following marker cannot run before stage"+mode+" releases");
                int[] x=new int[5];for(int n=0;n<5;n++)x[n]=(mode&1)==0?-472:FINAL_X[n];frames.add(frame(e,front,out,"stage"+mode+"-initial",mode==0));
                byte[] beforeInput=ownerBytes(b);int pc=e.vm().offset(),serial=h.scene.transitionSerial;
                e.click(850,50,true);e.click(850,50,false);check(e.modalKey(10)&&e.modalKey(27),"Banner consumes Enter and Escape without opening a system context");e.tick(0);
                check(!e.session.inSystem()&&e.vm().status()==MesVm.Status.WAIT_HOST&&e.vm().offset()==pc&&host(e).scene.transitionSerial==serial&&Arrays.equals(beforeInput,ownerBytes(host(e).dcdxBanner)),"Right/left click, Enter and Escape preserve the original timer-only wait, pixels and source serial");
                if(mode==0){
                    byte[] initial=e.snapshot();h.elapse(10000);int[] late=front.clone(),positions=x.clone();nativePoll(mode,positions,late,bg,atlas);check(host(e).dcdxBanner.polls==1,"Late native timer coalesces10000ms into one banner poll");frames.add(frame(e,late,out,"late-event-before-first-render",false));
                    e.restore(initial);int[] queued=front.clone(),queuedX=x.clone();
                    for(int delta:new int[]{33,33,34}){host(e).elapse(delta);nativePoll(mode,queuedX,queued,bg,atlas);}
                    check(host(e).dcdxBanner.polls==3,"Three original timer polls can append before a new owner is rendered");frames.add(frame(e,queued,out,"three-polls-before-first-render",false));
                    e.restore(initial);frames.add(frame(e,front,out,"initial-after-late-event-restore",false));
                }
                for(int poll=1;poll<=25;poll++){
                    int deadline=(tick+1)*1000/30;host(e).elapse(deadline-elapsed);tick++;elapsed=deadline;
                    boolean ended=nativePoll(mode,x,front,bg,atlas);b=host(e).dcdxBanner;
                    check(b.polls==poll&&b.active==!ended&&b.rawLayers().size()==poll*10,"Native five-row paired journal and next-poll release, stage"+mode+" poll"+poll);
                    for(int n=0;n<5;n++)check(b.itemX(n)==x[n],"Independent original target coordinates stage"+mode+" item"+n);
                    frames.add(frame(e,front,out,"stage"+mode+"-poll"+poll,poll==1||poll==24||poll==25));
                    if(poll==9){
                        check(e.vm().status()==MesVm.Status.WAIT_HOST,"Snapshots are made only at real VM host waits");byte[] snapshot=e.snapshot();int[] future=front.clone(),nextX=x.clone();int delta=(tick+1)*1000/30-elapsed;
                        host(e).elapse(delta);nativePoll(mode,nextX,future,bg,atlas);frames.add(frame(e,future,out,"stage"+mode+"-future-before-restore",false));
                        e.restore(snapshot);frames.add(frame(e,front,out,"stage"+mode+"-restored-poll9",false));check(Arrays.equals(snapshot,e.snapshot()),"Full VM snapshot restores native phase, captures and journal byte-for-byte");
                        host(e).elapse(delta);frames.add(frame(e,future,out,"stage"+mode+"-future-after-restore",false));e.restore(snapshot);
                    }
                    if(poll==13){
                        byte[] owner=ownerBytes(host(e).dcdxBanner);int[] future=front.clone(),nextX=x.clone();int delta=(tick+1)*1000/30-elapsed;
                        host(e).elapse(delta);nativePoll(mode,nextX,future,bg,atlas);frames.add(frame(e,future,out,"stage"+mode+"-same-owner-future",false));
                        host(e).dcdxBanner.load(new DataInputStream(new ByteArrayInputStream(owner)));frames.add(frame(e,front,out,"stage"+mode+"-same-owner-rewind",false));
                    }
                }
                check(!host(e).dcdxBanner.active&&e.vm().variables.read(8,11)==mode,"Completed native pixels precede continuation marker");
                e.vm().run(1);check(e.vm().variables.read(8,11)==mode+1,"Actual VM resumes the exact following instruction after banner release");
                if(mode<3)check(e.vm().run(1)==MesVm.Status.WAIT_HOST,"Following original-format1C12 enters the next mode");
            }
            check(e.vm().run(1)==MesVm.Status.HALTED,"All four native waits release and the synthetic continuation halts normally");
            check(front[40*W+900]==old[40*W+900],"Original old-front pixels outside all native rectangles remain intact across four modes");
            report.put("result","PASS");
        }catch(Exception failure){report.put("result","FAIL");report.put("error",failure.toString());throw failure;}
        finally{report.put("checks",checks);report.put("compared_pixels",compared);report.put("assets",assets);report.put("frames",frames);Files.write(out.resolve("report.json"),Json.text(report).getBytes("UTF-8"));}
        System.out.println(Json.text(Json.obj("result","PASS","checks",checks,"compared_pixels",compared,"frames",frames.size())));
    }
}
