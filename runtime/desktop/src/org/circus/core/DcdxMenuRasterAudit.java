package org.circus.core;

import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;
import java.io.*;
import java.lang.reflect.Field;
import java.nio.file.*;
import java.util.*;
import javax.imageio.ImageIO;

/** Original MAIN/window/control flow and menu surfaces; independent native table
 * crop/BGR oracle. This is a port-menu comparison, not an original EXE capture. */
public final class DcdxMenuRasterAudit {
    private static final int WIDTH=1024,HEIGHT=576;
    private static final String[] ACTIONS={"story","ard","3do","end"};
    private static final int[] SOURCE_X={0,400,600,200};
    private static int checks;private static long compared;
    private static void check(boolean value,String label){checks++;if(!value)throw new AssertionError(label);}
    private static DcdxHost host(DesktopLegacy e){return(DcdxHost)e.host();}
    private static int[] render(DesktopLegacy e)throws IOException{BufferedImage b=e.render();try{return b.getRGB(0,0,WIDTH,HEIGHT,null,0,WIDTH);}finally{b.flush();}}
    private static String hash(int[] values){byte[] bytes=new byte[values.length*3];for(int n=0;n<values.length;n++)for(int c=0;c<3;c++)bytes[n*3+c]=(byte)(values[n]>>>(8*c));return Binary.sha256(bytes);}
    private static final class Oracle {
        final CircusResources resources;final Map<String,Crx.Image> raw=new LinkedHashMap<>();
        Oracle(CircusResources resources){this.resources=resources;}
        Crx.Image image(String name)throws IOException{Crx.Image image=raw.get(name);if(image==null){image=resources.image(name);raw.put(name,image);}return image;}
        /**40EA30 background is opaque BGR24;40F0A0/40EE80 apply key/opacity.
         * No call to production menu.layers/capture, layout, argb or blending. */
        void draw(byte[] front,String name,int sx,int sy,int w,int h,int x,int y,boolean keyed)throws IOException{
            Crx.Image source=image(name);check(source.bpp==24||source.bpp==32,"Native menu source depth "+name);if(!keyed)check(source.bpp==24,"40EA30 original background is BGR24");
            for(int dy=0;dy<h;dy++)for(int dx=0;dx<w;dx++){
                int px=x+dx,py=y+dy,ix=sx+dx,iy=sy+dy;if(px<0||py<0||px>=WIDTH||py>=HEIGHT||ix<0||iy<0||ix>=source.width||iy>=source.height)continue;
                int at=iy*source.stride+ix*(source.bpp/8),to=(py*WIDTH+px)*3;
                if(keyed&&source.bpp==24&&(source.pixels[at]&255)==255&&source.pixels[at+1]==0&&(source.pixels[at+2]&255)==255)continue;
                int opacity=source.bpp==32?source.pixels[at+3]&255:255;
                for(int c=0;c<3;c++)front[to+c]=(byte)(((source.pixels[at+c]&255)*opacity+(front[to+c]&255)*(255-opacity))/255);
            }
        }
        int[] expected(DcdxHost host)throws IOException{
            DcdxMenu menu=host.dcdxMenu;byte[] front=new byte[WIDTH*HEIGHT*3];draw(front,menu.background,0,0,1024,576,0,0,false);
            if(menu.presented){
                // Native sorted table is the model input. Independently derive
                // atlas rectangles from the original56BC78 rows, not Layer output.
                for(Dc2Ui.Button button:host.ui.buttons){int item=button.binding,x=menu.cardX(item),y=menu.cardY(item);boolean selected=item==menu.front||item==menu.hovered;
                    check(button.action.equals(ACTIONS[item])&&button.left==x&&button.top==y&&button.right==x+200&&button.bottom==y+278,"Native table control geometry/action "+item);
                    draw(front,menu.cardAtlas,SOURCE_X[item],selected?0:278,200,278,x,y,true);
                    if(item==menu.hovered&&item!=menu.front)draw(front,menu.cardAtlas,0,556,248,326,x-24,y-20,true);
                }
                if(menu.hovered==menu.front)draw(front,menu.previewAtlas,(menu.front%2)*1024,(menu.front/2)*576,1024,576,0,0,true);
            }
            int[] result=new int[WIDTH*HEIGHT];for(int n=0;n<result.length;n++)result[n]=0xff000000|(front[n*3]&255)|((front[n*3+1]&255)<<8)|((front[n*3+2]&255)<<16);return result;
        }
    }
    private static Map<String,Object> frame(DesktopLegacy e,Oracle oracle,String name,Path out,boolean png)throws Exception{
        check(host(e).menuVisible(),"Menu owns current physical front "+name);int[] want=oracle.expected(host(e)),actual=render(e);
        if(!Arrays.equals(want,actual)){int at=0;while(want[at]==actual[at])at++;throw new AssertionError(name+" pixel("+(at%WIDTH)+","+(at/WIDTH)+") expected="+Integer.toHexString(want[at])+" actual="+Integer.toHexString(actual[at]));}
        check(true,"Original native table layers/integer BGR oracle "+name);compared+=actual.length;check(Arrays.equals(actual,render(e)),"Repeat menu render is deterministic "+name);
        if(png){BufferedImage b=new BufferedImage(WIDTH,HEIGHT,BufferedImage.TYPE_INT_ARGB);b.setRGB(0,0,WIDTH,HEIGHT,actual,0,WIDTH);ImageIO.write(b,"png",out.resolve(name+".png").toFile());b.flush();}
        DcdxMenu menu=host(e).dcdxMenu;return Json.obj("sample",name,"front",menu.front,"hover",menu.hovered,"turning",menu.turning(),"presented",menu.presented,"sha256",hash(actual));
    }
    private static DesktopLegacy natural(Path game,List<String> actions,List<String> scripts)throws Exception{
        GameResources files=new GameResources(new LocalGameStorage(game),"DCDX.EXE");Profile profile=GameVersion.detect(files).profile();check(profile.id.equals("dcdx-7769"),"START independently identifies reviewed DCDX");
        CircusResources data=new CircusResources(files,profile);DesktopLegacy e=new DesktopLegacy(data,GameText.detect(files,profile),true);
        for(int step=0;step<2000;step++){
            e.tick(1000);String script=e.scriptName();if(!scripts.contains(script))scripts.add(script);if(host(e).dcdxMenu.active){check(e.vm().status()==MesVm.Status.WAIT_HOST,"Original MAIN1C50 naturally owns a true host wait");return e;}
            if(e.menuWaiting()){List<String> labels=new ArrayList<>();for(DesktopEngine.Control b:e.controls())labels.add(b.action);if(!labels.contains("window"))throw new IOException("Unexpected pre-wheel original actions "+labels);e.choose("window");actions.add("window");continue;}
            for(int ch=0;ch<6;ch++)e.audioCompleted(ch,e.audioGeneration(ch));e.vm().signalHost(e.ticket(),MesVm.HostSignal.MEDIA_COMPLETE);e.vm().signalHost(e.ticket(),MesVm.HostSignal.TEXT_COMPLETE);
            if(e.waiting())e.click(WIDTH/2,HEIGHT/2,false);if(e.vm().status()==MesVm.Status.FAULT)throw new IOException(e.vm().error());
        }throw new IOException("Original MAIN menu not reached");
    }
    private static int nativeHit(DcdxHost h,int px,int py){int found=-1;for(Dc2Ui.Button b:h.ui.buttons)if(px>=b.left&&px<=b.right&&py>=b.top&&py<=b.bottom)found=b.binding;return found;}
    private static int[] point(DcdxHost h,int item)throws IOException{
        for(int y=Math.max(0,h.dcdxMenu.cardY(item)+1);y<Math.min(HEIGHT,h.dcdxMenu.cardY(item)+277);y+=3)for(int x=Math.max(0,h.dcdxMenu.cardX(item)+1);x<Math.min(WIDTH,h.dcdxMenu.cardX(item)+199);x+=3)if(nativeHit(h,x,y)==item)return new int[]{x,y};throw new IOException("No exposed native card point "+item);
    }
    private static void settle(DesktopLegacy e)throws Exception{int polls=0;while(host(e).dcdxMenu.turning()&&polls++<60)e.tick(34);check(!host(e).dcdxMenu.turning(),"Native wheel rotation reaches its next card without setting flags");e.tick(34);e.tick(34);}
    private static void hover(DesktopLegacy e,int item)throws Exception{int[] p=point(host(e),item);check(host(e).dcdxMenu.hit(p[0],p[1])==item,"Port/native-table inclusive hit agrees");e.hover(p[0],p[1]);e.tick(34);check(host(e).dcdxMenu.hovered==item,"Real pointer hover reaches requested native card "+item);}
    private static void rawAtlasOnly(DesktopLegacy e)throws Exception{
        Field rawField=DesktopLegacy.class.getDeclaredField("dcdxMenuImages");rawField.setAccessible(true);Map<?,?> cache=(Map<?,?>)rawField.get(e);Object atlas=cache.get(host(e).dcdxMenu.previewAtlas);
        check(atlas instanceof Crx.Image,"Preview atlas retained as a raw CRX owner");Crx.Image raw=(Crx.Image)atlas;check(raw.width==2048&&raw.height==1152,"Original full-screen preview has four1024x576 cells");
        for(Object value:cache.values())check(value instanceof Crx.Image,"Native menu raw cache contains no expanded BufferedImage");Field pictureField=DesktopLegacy.class.getDeclaredField("images");pictureField.setAccessible(true);DesktopImageCache<?> pictures=(DesktopImageCache<?>)pictureField.get(e);
        check(pictures.get(host(e).dcdxMenu.previewAtlas)==null,"Huge preview atlas never enters general ARGB Picture cache");
    }
    public static void main(String[] args)throws Exception{
        if(args.length!=2)throw new IOException("Require readonly_DCDX_GAME owned_OUTPUT");Path game=Paths.get(args[0]).toRealPath(),out=DesktopRouteAudit.outputOutsideGame(game,args[1]);System.setProperty("dc.saveRoot",out.resolve("state").toString());System.setProperty("dc.cacheRoot",out.resolve("cache").toString());
        String exe=Binary.sha256(Files.readAllBytes(game.resolve("DCDX.EXE")));check(exe.equals("86bcd33e954b81f9b7bb731bcbc2886bc68d8d069074a32ef2af716fede395cb"),"Native PE binding");List<String> actions=new ArrayList<>(),scripts=new ArrayList<>();DesktopLegacy e=natural(game,actions,scripts);Oracle oracle=new Oracle(e.resources);List<Object> frames=new ArrayList<>();
        frames.add(frame(e,oracle,"initialized-background-only",out,true));e.tick(33);check(host(e).dcdxMenu.presented,"Original30Hz first presented menu after33ms");frames.add(frame(e,oracle,"first-presented",out,true));byte[] baseline=e.snapshot();
        check(e.modalKey(KeyEvent.VK_ENTER)&&!host(e).dcdxMenu.active,"Portable/desktop Enter10 activates native wheel Enter13");e.tick(0);Set<String> keyActions=new HashSet<>();for(DesktopEngine.Control b:e.controls())keyActions.add(b.action);
        check(e.vm().status()==MesVm.Status.WAIT_HOST&&keyActions.contains("main")&&keyActions.contains("ystory")&&host(e).uiCapture!=null,"Actual native Enter/tick reaches original confirmation with matching VM wait");byte[] keyConfirmation=e.snapshot();e.restore(keyConfirmation);check(e.vm().status()==MesVm.Status.WAIT_HOST&&Arrays.equals(keyConfirmation,e.snapshot()),"Enter confirmation restores as a genuine wait, without fabricating ready state");e.restore(baseline);
        for(int item=0;item<4;item++){
            e.restore(baseline);if(item!=host(e).dcdxMenu.front){int[] p=point(host(e),item);e.pointerDown(p[0],p[1]);e.pointerUp(p[0],p[1]);check(host(e).dcdxMenu.turning()&&host(e).dcdxMenu.active,"Nonfront pointer click rotates rather than activates "+item);frames.add(frame(e,oracle,"turn-start-to"+item,out,false));e.tick(34);frames.add(frame(e,oracle,"turn-first-to"+item,out,false));settle(e);}
            check(host(e).dcdxMenu.front==item,"Legitimate pointer rotation makes desired card front "+item);hover(e,item);frames.add(frame(e,oracle,"front"+item+"-full-preview",out,true));rawAtlasOnly(e);
        }
        e.restore(baseline);hover(e,3);frames.add(frame(e,oracle,"nonfront-hover-border",out,true));byte[] beforeTurn=e.snapshot();check(e.modalKey(KeyEvent.VK_RIGHT),"Native right key consumed");e.tick(34);frames.add(frame(e,oracle,"key-right-midturn",out,false));byte[] turning=e.snapshot();int[] painted=render(e);e.tick(34);int[] future=render(e);e.restore(turning);check(Arrays.equals(painted,render(e)),"New-host snapshot retains painted menu positions");e.tick(34);check(Arrays.equals(future,render(e)),"Restored wheel clock produces the same future raster");settle(e);check(host(e).dcdxMenu.front==3,"Native right key rotates toward end card");
        e.restore(baseline);int edgeX=host(e).dcdxMenu.cardX(3)+200,edgeY=host(e).dcdxMenu.cardY(3)+1;check(nativeHit(host(e),edgeX,edgeY)==3,"Original inclusive edge belongs to left card");e.hover(edgeX,edgeY);check(host(e).dcdxMenu.hovered==3,"Actual hover accepts native inclusive right edge");frames.add(frame(e,oracle,"native-inclusive-hover-edge",out,false));
        e.restore(baseline);hover(e,0);int[] originalFront=oracle.expected(host(e));int[] p=point(host(e),0);e.pointerDown(p[0],p[1]);e.pointerUp(p[0],p[1]);actions.add("story(pointer)");check(!host(e).dcdxMenu.active,"Front pointer follows original story label");e.tick(0);
        check(e.vm().status()==MesVm.Status.WAIT_HOST&&host(e).uiCapture!=null&&host(e).uiCapture.kind==UiCapture.ARGB,"Natural MAIN story executes0D9 and reaches original confirmation wait");
        check(Arrays.equals(originalFront,host(e).uiCapture.pixels().argb),"Original0D9 freezes the complete physical menu front, including preview");compared+=originalFront.length;byte[] confirmation=e.snapshot();int[] confirmPixels=render(e);DcdxHost old=host(e);e.restore(confirmation);check(host(e)!=old&&Arrays.equals(confirmPixels,render(e))&&Arrays.equals(confirmation,e.snapshot()),"CVS2 new-host restore retains captured menu and confirmation raster/state");
        Set<String> confirmActions=new HashSet<>();for(DesktopEngine.Control b:e.controls())confirmActions.add(b.action);check(confirmActions.contains("main")&&confirmActions.contains("ystory"),"Original confirmation exposes original main/ystory labels");e.choose("main");actions.add("main(cancel)");e.tick(0);check(host(e).dcdxMenu.active,"Original confirmation cancel resumes menu owner");e.tick(34);frames.add(frame(e,oracle,"cancel-returned-menu",out,true));
        List<Object> assets=new ArrayList<>();for(Map.Entry<String,Crx.Image> a:oracle.raw.entrySet()){Crx.Image image=a.getValue();assets.add(Json.obj("reference",a.getKey(),"width",image.width,"height",image.height,"bpp",image.bpp,"decoded_sha256",Binary.sha256(image.pixels)));}
        Map<String,Object> report=Json.obj("result","PASS","checks",checks,"compared_pixels",compared,"frames",frames,"assets",assets,"original_actions",actions,"original_scripts",scripts,"exe_sha256",exe,"uif_config_sha256",Binary.sha256(Files.readAllBytes(game.resolve("uif_config.json"))),"codec_fingerprint",e.codec.fingerprint,"original_executable_launched",false,"original_game_files_written",false,"variables_forced",false,"scope","Natural START/window/MAIN1C50 mode0; native sorted menu/control model is input to separate original-table crop/BGR byte oracle; actual desktop rendering, raw-only2048x1152 hover atlas, legal pointer/keyboard rotation, natural story0D9 confirmation capture, new-host CVS2 restored front/future. Port-menu surfaces comparison; not original EXE full-screen fidelity or a completed story route.");
        Files.write(out.resolve("report.json"),Json.text(report).getBytes("UTF-8"));System.out.println(Json.text(Json.obj("result","PASS","checks",checks,"compared_pixels",compared,"frames",frames.size())));
    }
}
