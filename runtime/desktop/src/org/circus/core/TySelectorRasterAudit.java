package org.circus.core;

import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import javax.imageio.ImageIO;

/** Natural TY story picker and real resource pixels. Native instruction vectors
 * independently validate the model's clips; this separate byte oracle checks
 * the desktop raster/capture path. It is not a running original EXE capture. */
public final class TySelectorRasterAudit {
    private static final int W=800,H=600;
    private static final String[] STORIES={"hyd_0611_a1_avn","set_0214_f1_214","ty_mhr01_d1_mhr","ty_myu01_d1_myu"};
    private static int checks;private static long compared;
    private static void check(boolean value,String why){checks++;if(!value)throw new AssertionError(why);}
    private static TyHost host(DesktopLegacy e){return(TyHost)e.host();}
    private static int[] render(DesktopLegacy e)throws IOException{BufferedImage image=e.render();try{return image.getRGB(0,0,W,H,null,0,W);}finally{image.flush();}}
    private static String hash(int[] pixels){byte[] b=new byte[pixels.length*3];for(int n=0;n<pixels.length;n++)for(int c=0;c<3;c++)b[n*3+c]=(byte)(pixels[n]>>>(c*8));return Binary.sha256(b);}
    private static final class Oracle {
        final CircusResources resources;final Map<String,Crx.Image> images=new LinkedHashMap<String,Crx.Image>();
        Oracle(CircusResources resources){this.resources=resources;}
        Crx.Image image(String name)throws IOException{Crx.Image result=images.get(name);if(result==null){result=resources.image(name);images.put(name,result);check(result.bpp==24||result.bpp==32,"Original selector bitmap depth "+name);}return result;}
        void draw(byte[] b,String name,int dx,int dy,int l,int t,int r,int bottom,boolean keyed)throws IOException{
            Crx.Image raw=image(name);if(!keyed&&raw.bpp!=24)throw new IOException("Native opaque selector background must be BGR24");
            for(int y=Math.max(0,t);y<Math.min(H,bottom);y++)for(int x=Math.max(0,l);x<Math.min(W,r);x++){
                int sx=x-dx,sy=y-dy;if(sx<0||sy<0||sx>=raw.width||sy>=raw.height)continue;int at=sy*raw.stride+sx*(raw.bpp/8),to=(y*W+x)*3;
                if(keyed&&raw.bpp==24&&(raw.pixels[at]&255)==255&&raw.pixels[at+1]==0&&(raw.pixels[at+2]&255)==255)continue;
                int a=keyed&&raw.bpp==32?raw.pixels[at+3]&255:255;
                for(int c=0;c<3;c++)b[to+c]=(byte)(((raw.pixels[at+c]&255)*a+(b[to+c]&255)*(255-a))/255);
            }
        }
        byte[] surface(TySelector.Surface surface)throws IOException{byte[] pixels=new byte[W*H*3];draw(pixels,surface.background,0,0,0,0,W,H,false);for(TySelector.Sprite s:surface.sprites)draw(pixels,s.resource,s.drawX,s.drawY,s.clipLeft,s.clipTop,s.clipRight,s.clipBottom,s.keyed);return pixels;}
        int[] expected(TyHost host)throws IOException{
            TySelector.Frame frame=host.tySelector.frame();byte[] next=surface(frame.target),prior;
            if(frame.weight255==255)prior=next;
            else if(frame.from!=null)prior=surface(frame.from);
            else{check(host.tySelectorUnderlay!=null&&host.tySelectorUnderlay.kind==UiCapture.ARGB,"Native intro retains actual portable old front");UiCapture.Pixels old=host.tySelectorUnderlay.pixels();check(old.width==W&&old.height==H,"Captured selector canvas");prior=new byte[W*H*3];for(int n=0;n<old.argb.length;n++)for(int c=0;c<3;c++)prior[n*3+c]=(byte)(old.argb[n]>>>(c*8));}
            int[] result=new int[W*H];int w=frame.weight255;for(int n=0;n<result.length;n++){int color=0xff000000;for(int c=0;c<3;c++)color|=(((prior[n*3+c]&255)*(255-w)+(next[n*3+c]&255)*w)/255)<<(c*8);result[n]=color;}return result;
        }
    }
    private static Map<String,Object> frame(DesktopLegacy e,Oracle oracle,String name,Path out)throws Exception{
        check(host(e).tySelectorVisible(),"Selector owns the visible native front "+name);int[] want=oracle.expected(host(e)),actual=render(e);
        if(!Arrays.equals(want,actual)){int at=0;while(want[at]==actual[at])at++;throw new AssertionError(name+" pixel("+(at%W)+","+(at/W)+") expected="+Integer.toHexString(want[at])+" actual="+Integer.toHexString(actual[at]));}
        compared+=actual.length;check(true,"Original resources with separate BGR/key/opacity/fade oracle "+name);check(Arrays.equals(actual,render(e)),"Repeated render does not accumulate selector alpha "+name);
        BufferedImage png=new BufferedImage(W,H,BufferedImage.TYPE_INT_ARGB);png.setRGB(0,0,W,H,actual,0,W);ImageIO.write(png,"png",out.resolve(name+".png").toFile());png.flush();TySelector m=host(e).tySelector;
        return Json.obj("sample",name,"selected",m.selected(),"focus",m.focused(),"interactive",m.interactive(),"weight255",m.frame().weight255,"sha256",hash(actual));
    }
    private static DesktopLegacy natural(Path game,List<String> actions,List<String> scripts)throws Exception{
        GameResources files=new GameResources(new LocalGameStorage(game),"dc2ty_chinese(simplified)_patch.exe");Profile profile=GameVersion.detect(files).profile();check(profile.id.equals("dc2ty-5f69"),"Actual START independently identifies TY");
        CircusResources data=new CircusResources(files,profile);DesktopLegacy e=new DesktopLegacy(data,GameText.detect(files,profile),true);
        for(int step=0;step<3000;step++){
            e.tick(1000);String script=e.scriptName();if(!scripts.contains(script))scripts.add(script);
            if(host(e).tySelector.active){check(e.vm().status()==MesVm.Status.WAIT_HOST,"Original sce_select1C12 naturally waits in the VM");return e;}
            if(e.menuWaiting()){List<String> labels=new ArrayList<String>();for(DesktopEngine.Control c:e.controls())labels.add(c.action);String label=labels.contains("03")?"03":labels.contains("window")?"window":labels.contains("start")?"start":null;if(label==null)throw new IOException("Unexpected original pre-selector actions "+labels);e.choose(label);actions.add(label);continue;}
            for(int ch=0;ch<6;ch++)e.audioCompleted(ch,e.audioGeneration(ch));e.vm().signalHost(e.ticket(),MesVm.HostSignal.MEDIA_COMPLETE);e.vm().signalHost(e.ticket(),MesVm.HostSignal.TEXT_COMPLETE);if(e.waiting())e.click(W/2,H/2,false);if(e.vm().status()==MesVm.Status.FAULT)throw new IOException(e.vm().error());
        }throw new IOException("Original story selector not reached");
    }
    private static void settle(DesktopLegacy e)throws Exception{for(int n=0;n<200&&host(e).tySelector.active&&!host(e).tySelector.interactive();n++)e.tick(25);check(host(e).tySelector.interactive(),"Native fade reaches interactive controls");}
    private static Dc2Ui.Button control(DesktopLegacy e,int item)throws IOException{for(Dc2Ui.Button b:host(e).tySelector.controls())if(b.binding==item)return b;throw new IOException("Missing native selector binding "+item);}
    private static void pointer(DesktopLegacy e,int item)throws Exception{Dc2Ui.Button b=control(e,item);int x=(b.left+b.right)/2,y=(b.top+b.bottom)/2;e.hover(x,y);e.pointerDown(x,y);e.pointerUp(x,y);}
    private static void route(DesktopLegacy e,String wanted)throws Exception{for(int n=0;n<600&&!e.scriptName().equalsIgnoreCase(wanted);n++){e.tick(1000);for(int ch=0;ch<6;ch++)e.audioCompleted(ch,e.audioGeneration(ch));e.vm().signalHost(e.ticket(),MesVm.HostSignal.MEDIA_COMPLETE);e.vm().signalHost(e.ticket(),MesVm.HostSignal.TEXT_COMPLETE);if(e.waiting()&&!e.menuWaiting())e.click(W/2,H/2,false);if(e.vm().status()==MesVm.Status.FAULT)throw new IOException(e.vm().error());}check(e.scriptName().equalsIgnoreCase(wanted),"Natural confirmed selector follows original story transfer "+wanted);}
    public static void main(String[] args)throws Exception{
        if(args.length!=2)throw new IOException("Require readonly_TY_GAME owned_OUTPUT");Path game=Paths.get(args[0]).toRealPath(),out=DesktopRouteAudit.outputOutsideGame(game,args[1]);System.setProperty("dc.saveRoot",out.resolve("state").toString());System.setProperty("dc.cacheRoot",out.resolve("cache").toString());
        String exe=Binary.sha256(Files.readAllBytes(game.resolve("dc2ty_chinese(simplified)_patch.exe")));check(exe.equals("615704413dd00960bb47f4d6c1b33dd635931f13085a9a25dd5782fd7dbdf505"),"Reviewed Chinese PE binding");List<String> actions=new ArrayList<String>(),scripts=new ArrayList<String>();DesktopLegacy e=natural(game,actions,scripts);Oracle oracle=new Oracle(e.resources);List<Object> frames=new ArrayList<Object>();
        frames.add(frame(e,oracle,"intro-first",out));e.tick(80);frames.add(frame(e,oracle,"intro-mid",out));byte[] mid=e.snapshot();int[] pixels=render(e);e.tick(33);int[] future=render(e);TyHost old=host(e);e.restore(mid);
        check(host(e)!=old&&e.vm().status()==MesVm.Status.WAIT_HOST&&Arrays.equals(pixels,render(e)),"New-host genuine WAIT snapshot retains intro front");e.tick(33);check(Arrays.equals(future,render(e)),"Restored fade clock produces identical future raster");settle(e);frames.add(frame(e,oracle,"overview",out));byte[] baseline=e.snapshot();
        for(int item=0;item<4;item++){
            e.restore(baseline);Dc2Ui.Button button=control(e,item);check(button.action.equals("ty:"+item),"Original numeric binding is input-only "+item);e.hover(button.left,button.top);frames.add(frame(e,oracle,"overview-hover"+item,out));
            pointer(e,item);check(host(e).tySelector.active&&!host(e).tySelector.interactive()&&host(e).tySelector.selected()==item,"First real pointer click previews without leaving selector "+item);e.tick(97);frames.add(frame(e,oracle,"preview-mid"+item,out));settle(e);frames.add(frame(e,oracle,"preview-settled"+item,out));
            if(item==0){check(e.modalKey(KeyEvent.VK_ESCAPE),"Actual Escape consumes preview cancellation");check(host(e).tySelector.active&&!host(e).tySelector.interactive(),"Preview Escape retains native modal wait");e.tick(80);frames.add(frame(e,oracle,"return-overview-mid",out));settle(e);frames.add(frame(e,oracle,"returned-overview",out));pointer(e,item);settle(e);}
            check(e.modalKey(KeyEvent.VK_ENTER),"Actual portable Enter reaches native confirmation "+item);e.tick(0);check(!host(e).tySelector.active,"Second confirm releases the selected story "+item);actions.add("ty:"+item+"(pointer preview/Enter confirm)");route(e,STORIES[item]);
        }
        e.restore(baseline);check(e.modalKey(KeyEvent.VK_DOWN),"Actual Down navigates native keyboard focus");frames.add(frame(e,oracle,"keyboard-first-card",out));check(host(e).tySelector.focused()==0,"Native Down starts at first card");check(e.modalKey(KeyEvent.VK_ESCAPE),"Overview Escape consumed");e.tick(0);check(!host(e).tySelector.active,"Overview Escape cancels to original main without invented branch");route(e,"main");actions.add("Escape overview→main");
        List<Object> assets=new ArrayList<Object>();for(Map.Entry<String,Crx.Image> a:oracle.images.entrySet()){Crx.Image im=a.getValue();assets.add(Json.obj("reference",a.getKey(),"width",im.width,"height",im.height,"bpp",im.bpp,"decoded_sha256",Binary.sha256(im.pixels)));}
        Map<String,Object> report=Json.obj("result","PASS","checks",checks,"compared_pixels",compared,"frames",frames,"assets",assets,"original_actions",actions,"original_scripts",scripts,"exe_sha256",exe,"codec_fingerprint",e.codec.fingerprint,"original_executable_executed",false,"original_game_files_written",false,"variables_forced",false,"scope","Natural START/main/start/sce_select1C12; independent BGR raw-resource raster/fade oracle consumes model clip metadata separately validated against300 original instruction vectors. Four legitimate two-step story choices, inclusive hover, real Enter/Escape, intro WAIT_HOST/new-host snapshot and future pixels. Port-menu surfaces comparison, not original EXE complete-frame or complete-route fidelity.");Files.write(out.resolve("report.json"),Json.text(report).getBytes("UTF-8"));System.out.println(Json.text(Json.obj("result","PASS","checks",checks,"compared_pixels",compared,"frames",frames.size())));
    }
}
