package org.circus.core;

import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import javax.imageio.ImageIO;

/** Original fst_1216_e1_cmn ADV4B -> pending1 ->937/5E output.
 * Native initial/0F-preparation/FULL-template layers are tested independently.
 * Original EXE is static data only. No route variable or source file is changed. */
public final class Dc2AdvMotionOutputAudit {
    private static final String SCRIPT="fst_1216_e1_cmn";
    private static int checks;private static long compared;private static int maximumRounding;
    private static void check(boolean yes,String why){checks++;if(!yes)throw new AssertionError(why);}
    private static PcHost host(DesktopLegacy e){return(PcHost)e.host();}
    private static int[] pixels(DesktopLegacy e)throws IOException{BufferedImage b=e.render();try{return b.getRGB(0,0,800,600,null,0,800);}finally{b.flush();}}
    private static String hash(int[] p){byte[] raw=new byte[p.length*3];for(int n=0;n<p.length;n++)for(int k=0;k<3;k++)raw[n*3+k]=(byte)(p[n]>>>(k*8));return Binary.sha256(raw);}
    private static int differences(int[] a,int[] b){int n=0;for(int k=0;k<a.length;k++)if(a[k]!=b[k])n++;return n;}
    private static void png(Path out,String name,int[] pixels)throws IOException{BufferedImage b=new BufferedImage(800,600,BufferedImage.TYPE_INT_ARGB);try{b.setRGB(0,0,800,600,pixels,0,800);ImageIO.write(b,"png",out.resolve(name+".png").toFile());}finally{b.flush();}}
    private static void pump(DesktopLegacy e)throws Exception {
        PcHost h=host(e);for(int ch=0;ch<6;ch++)h.audioCompleted(ch,h.audioGeneration[ch]);e.vm().signalHost(h.waitTicket(),MesVm.HostSignal.MEDIA_COMPLETE);
        if(h.presentationWaiting)e.vm().signalHost(h.waitTicket(),MesVm.HostSignal.TEXT_COMPLETE);
        else if(h.textWaiting)e.vm().signalHost(h.waitTicket(),MesVm.HostSignal.ADVANCE);
        else if(h.scene.nativeMotion!=null&&h.scene.nativeMotion.active)e.vm().elapse(h.scene.nativeMotion.untilFrame());
        else if(h.remainingMillis()>0)e.vm().elapse(h.remainingMillis());
        else if(h.remainingMillis()<0)e.vm().signalHost(h.waitTicket(),MesVm.HostSignal.ADVANCE);
        else e.vm().elapse(40);
    }
    private static DesktopLegacy before(CircusResources data,TextCodec codec,Path out,List<Object> bodyFrames)throws Exception {
        DesktopLegacy e=new DesktopLegacy(data,codec,true);PcHost h=host(e);boolean templates=false;
        for(int n=0;n<200000;n++){
            if(!h.ui.quadTemplates.get(1).isEmpty()&&!h.ui.quadTemplates.get(2).isEmpty()&&!h.ui.buttonTemplates.get(1).isEmpty()&&!h.scene.slots[21].isEmpty()){templates=true;break;}
            MesVm.Status s=e.vm().run(1);if(s==MesVm.Status.FAULT||s==MesVm.Status.HALTED)throw new IOException("Actual START bootstrap "+e.vm().error());if(s==MesVm.Status.WAIT_HOST)pump(e);
        }
        check(templates,"original START loads actual body/name/button templates before the selected script");h.enterSystem();h.systemScene=null;e.session.vm=new MesVm(data.scripts(),h,SCRIPT);
        Set<Integer> seen=new HashSet<>();boolean reached=false;
        for(int n=0;n<200000;n++){
            if(e.vm().offset()==0x937&&!h.waiting()){reached=true;break;}
            MesVm.Status s=e.vm().run(1);if(s==MesVm.Status.FAULT||s==MesVm.Status.HALTED)throw new IOException("Actual prefix "+e.vm().error());
            if(h.textWaiting&&(h.pc==0x8fe||h.pc==0x931)&&seen.add(h.pc)){int[] frame=pixels(e);String name=h.pc==0x8fe?"body-8f0":"body-904";png(out,name,frame);bodyFrames.add(Json.obj("name",name,"body_sha256",Binary.sha256(h.body.getBytes("UTF-8")),"frame_sha256",hash(frame),"wait_pc",h.pc));}
            if(s==MesVm.Status.WAIT_HOST)pump(e);
        }
        check(reached&&seen.size()==2,"actual original prefix executes both8F0/904 bodies and releases931 before937");return e;
    }
    private static int rgb(Crx.Image image,int x,int y){int at=y*image.stride+x*(image.bpp/8);return 0xff000000|(image.pixels[at+2]&255)<<16|(image.pixels[at+1]&255)<<8|(image.pixels[at]&255);}
    private static int blend(int old,int fresh,int weight){int r=((old>>>16&255)*(16-weight)+(fresh>>>16&255)*weight)/16,g=((old>>>8&255)*(16-weight)+(fresh>>>8&255)*weight)/16,b=((old&255)*(16-weight)+(fresh&255)*weight)/16;return 0xff000000|r<<16|g<<8|b;}
    private static void background(int[] canvas,Crx.Image image,int x,int y,int weight){for(int dy=Math.max(0,y);dy<Math.min(600,y+image.height);dy++)for(int dx=Math.max(0,x);dx<Math.min(800,x+image.width);dx++){int p=dy*800+dx;canvas[p]=blend(canvas[p],rgb(image,dx-x,dy-y),weight);}}
    private static final class Oracle {
        final CircusResources data;final Crx.Image background;final List<Dc2Ui.Quad> window;final String[] slots;final int shade;final boolean shadeEnabled;
        int[] expected;int applied;
        Oracle(CircusResources data,PcHost h,Dc2Motion m,int[] initial)throws IOException {
            this.data=data;this.background=data.image(m.background);this.window=Dc2Ui.copyQuads(h.ui.quadTemplates.get(h.messageMode()==0?1:4));slots=m.source.slots.clone();shade=h.read(99,11);shadeEnabled=h.read(99,10)!=0;expected=initial.clone();
            check(background.bpp==24&&background.width==800&&background.height>=1200,"actual scrolling CG admits native24-bit feedback and full600pxpan");
            check(m.source.quads.isEmpty(),"selected actual0F source has no additional baked draw entries");for(int n=2;n<=19;n++)check(m.source.slots[n].isEmpty(),"selected actual0F source slot"+n+" has no actor/effect/portrait layer");
            check(h.messageMode()==0&&window.size()==9&&h.ui.quadTemplates.get(2).size()==3,"actual mode0 has9 primary-window records and separate3-record name template");check(!slots[21].isEmpty()&&shade>=0&&shade<=16,"actual primary window exists and its native alpha is0..16");
        }
        void window(int[] canvas)throws IOException {
            for(Dc2Ui.Quad q:window){if(q.type==2)continue;if(q.binding!=0||q.nativeCondition!=0||q.animation!=0||q.type!=0)throw new IOException("Actual primary window leaves reviewed static image scope");Crx.Image image=data.image(slots[q.slot()]);if(image.bpp!=24&&image.bpp!=32)throw new IOException("Actual primary window is not native truecolor");boolean weighted=q.opaque()&&shadeEnabled;int weight=weighted?shade:16;
                for(int y=0;y<q.height;y++)for(int x=0;x<q.width;x++){int dx=q.x+x,dy=q.y+y,sx=q.sx+x,sy=q.sy+y;if(dx<0||dx>=800||dy<0||dy>=600||sx<0||sx>=image.width||sy<0||sy>=image.height)continue;int source=rgb(image,sx,sy),alpha=image.bpp==32?image.pixels[sy*image.stride+sx*4+3]&255:255;if(image.bpp==24&&(source&0xffffff)==0xff00ff||alpha==0)continue;int at=dy*800+dx;
                    if(weighted||alpha==255)canvas[at]=blend(canvas[at],source,weight);else{int old=canvas[at],r=((old>>>16&255)*(255-alpha)+(source>>>16&255)*alpha)/255,g=((old>>>8&255)*(255-alpha)+(source>>>8&255)*alpha)/255,b=((old&255)*(255-alpha)+(source&255)*alpha)/255;canvas[at]=0xff000000|r<<16|g<<8|b;}
                }
            }
        }
        void update(Dc2Motion motion)throws IOException {
            for(List<Dc2Motion.Draw> journal=motion.draws();applied<journal.size();){Dc2Motion.Draw d=journal.get(applied++);
                if(d.kind==Dc2Motion.PREPARE)background(expected,background,0,0,d.alpha);
                else if(d.kind==Dc2Motion.BACKGROUND)background(expected,background,d.x,d.y,d.alpha);
                else{Arrays.fill(expected,0xff000000);background(expected,background,d.x,d.y,16);window(expected);}
            }
        }
    }
    private static Map<String,Object> frame(DesktopLegacy e,Oracle oracle,Path out,String name)throws IOException {
        Dc2Motion m=host(e).scene.nativeMotion;oracle.update(m);int[] actual=pixels(e);int worst=0;long rounded=0;
        for(int n=0;n<actual.length;n++){int delta=0;for(int shift:new int[]{0,8,16})delta=Math.max(delta,Math.abs((actual[n]>>>shift&255)-(oracle.expected[n]>>>shift&255)));if(delta!=0)throw new AssertionError("Actual937 "+name+" native BGR/window mismatch "+n%800+","+n/800+" delta="+delta+" draws="+m.draws().size()+" last="+m.draws().get(m.draws().size()-1).kind);if(delta!=0)rounded++;worst=Math.max(worst,delta);}
        checks++;compared+=actual.length;maximumRounding=Math.max(maximumRounding,worst);png(out,name,actual);return Json.obj("name",name,"draws",m.draws().size(),"preparing",m.preparing,"active",m.active,"x",m.x,"y",m.y,"maximum_rgb_rounding",worst,"rounded_pixels",rounded,"frame_sha256",hash(actual));
    }
    public static void main(String[] args)throws Exception {
        if(args.length!=2)throw new IOException("Require readonly_DC2_GAME owned_OUTPUT");Path game=Paths.get(args[0]).toRealPath(),out=DesktopRouteAudit.outputOutsideGame(game,args[1]);System.setProperty("dc.saveRoot",out.resolve("state").toString());System.setProperty("dc.cacheRoot",out.resolve("cache").toString());CircusResources data=new CircusResources(new GameResources(new LocalGameStorage(game),"dc2.exe"),Profiles.get("dc2-3363"));TextCodec codec=GameText.detect(data.files,data.profile);List<Object> bodies=new ArrayList<>(),frames=new ArrayList<>();DesktopLegacy e=before(data,codec,out,bodies);PcHost h=host(e);String oldBody=h.body;int[] initial=pixels(e);png(out,"before-937-old-body",initial);check(h.speaker.isEmpty()&&!oldBody.isEmpty()&&h.scene.nativeMotion==null,"937 starts from actual unspoken old ADV body front");
        check(e.vm().run(1)==MesVm.Status.WAIT_HOST&&h.scene.nativeMotion!=null,"actual937 starts its native5E host coroutine");Dc2Motion m=h.scene.nativeMotion;check(m.active&&m.preparing&&!m.prepareOnly&&m.previous==null&&m.initial.kind==UiCapture.ARGB,"pending1 enters real preparation with independent raster initial");check(Arrays.equals(initial,m.initial.pixels().argb),"5E initial capture is exactly prior visible old正文front");check(m.prepared!=null&&m.prepared.kind==UiCapture.LEGACY&&m.fullTemplate!=null,"native mask0F prepared scene and separate frozen primary template both exist");Oracle oracle=new Oracle(data,h,m,initial);int[] clean=new int[800*600];Arrays.fill(clean,0xff000000);background(clean,oracle.background,0,0,16);check(differences(initial,clean)>1000,"old ADV glyph/window pixels are distinguishable from clean0F scene");frames.add(frame(e,oracle,out,"prepare-first-native-blend"));byte[] unchanged=e.snapshot();pixels(e);check(Arrays.equals(unchanged,e.snapshot()),"repeated rendering does not reapply integer feedback or change VM state");
        e.vm().elapse(17);byte[] saved=e.snapshot();DesktopLegacy restored=new DesktopLegacy(data,codec,true);restored.restore(saved);check(Arrays.equals(pixels(e),pixels(restored)),"F5/session restores current preparation pixels and old-front capture");e.vm().elapse(23);for(int n=0;n<23;n++)restored.vm().elapse(1);frames.add(frame(e,oracle,out,"prepare-next-40ms-boundary"));check(Arrays.equals(pixels(e),pixels(restored)),"saved17ms remainder reproduces next native40ms blend under1ms partition");
        for(int n=0;n<1000&&h.scene.nativeMotion.preparing;n++)e.vm().elapse(h.scene.nativeMotion.untilFrame());frames.add(frame(e,oracle,out,"first-pan-after-no-text-preparation"));check(!h.scene.nativeMotion.preparing&&h.scene.nativeMotion.y<0&&h.body.equals(oldBody),"moving stage retains old text state without painting its glyphs again");byte[] pan=e.snapshot();restored.restore(pan);e.vm().elapse(37);for(int n=0;n<37;n++)restored.vm().elapse(1);frames.add(frame(e,oracle,out,"pan-feedback-clock37"));check(Arrays.equals(pixels(e),pixels(restored)),"moving feedback snapshot retains all intermediate draws and fractional clock");
        for(int n=0;n<2000&&h.scene.nativeMotion.active;n++)e.vm().elapse(h.scene.nativeMotion.untilFrame());m=h.scene.nativeMotion;check(!m.active&&m.y== -600&&m.draws().get(m.draws().size()-1).kind==Dc2Motion.FULL,"actual pan completes with original final FULL redraw");frames.add(frame(e,oracle,out,"complete-window-without-old-glyphs"));int[] finished=pixels(e),bare=new int[800*600];Arrays.fill(bare,0xff000000);background(bare,oracle.background,0,-600,16);check(differences(finished,bare)>1000,"final FULL retains primary message window rather than losing it with old glyphs");saved=e.snapshot();restored.restore(saved);check(Arrays.equals(finished,pixels(restored)),"completed motion snapshot retains frozen textless template");check(h.body.equals(oldBody),"pixel-level glyph removal does not falsify native logical body state");
        e.vm().run(3);check(e.vm().offset()==0x95f&&h.presentationWaiting&&h.scene.nativeMotion==null&&!h.body.equals(oldBody),"original940/943/949 opcodes publish the next speaker/body after5E");e.vm().signalHost(h.waitTicket(),MesVm.HostSignal.TEXT_COMPLETE);e.vm().run(1);check(h.textWaiting&&h.pc==0x95f,"next original body reaches its95F text wait");int[] next=pixels(e);png(out,"next-949-body-restored",next);check(differences(finished,next)>30,"next actual body glyphs return after textless motion completes");
        Files.write(out.resolve("result.json"),(Json.text(Json.obj("pass",true,"checks",checks,"script",SCRIPT,"script_sha256",data.scripts().script(SCRIPT).sha256,"target_sha256",Binary.sha256(data.files.read("dc2.exe",32*1024*1024)),"source_cg",m.background,"source_cg_width",oracle.background.width,"source_cg_height",oracle.background.height,"source_cg_decoded_sha256",Binary.sha256(oracle.background.pixels),"window_alpha_weight",oracle.shade,"window_alpha_enabled",oracle.shadeEnabled,"bodies",bodies,"frames",frames,"pixels_compared",compared,"maximum_window_rgb_rounding",maximumRounding,"heap_max_bytes",Runtime.getRuntime().maxMemory(),"original_exe_executed",false,"original_game_directory_written",false,"scope","Actual START templates and unmodified original script prefix through two ADV bodies and937/5E, independent captured old front, raw24bit CG integer feedback oracle and separate native primary-window FULL keyed BGR/16 layers checked exactly, current/future session pixels and next original body. No running-original GDI/font screenshot equivalence claimed."))+"\n").getBytes("UTF-8"));System.out.println("Dc2AdvMotionOutputAudit PASS "+checks+"; pixels="+compared+" max_window_rounding="+maximumRounding);
    }
}
