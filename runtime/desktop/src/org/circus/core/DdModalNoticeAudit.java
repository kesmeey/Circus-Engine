package org.circus.core;

import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import javax.imageio.ImageIO;

/** Read-only original DD START assets; explicit modal entry fixtures exercise error-dialog branches. */
public final class DdModalNoticeAudit {
    private static int checks;
    private static void check(boolean value,String why){checks++;if(!value)throw new AssertionError(why);}
    private static boolean same(BufferedImage a,BufferedImage b){return Arrays.equals(a.getRGB(0,0,a.getWidth(),a.getHeight(),null,0,a.getWidth()),b.getRGB(0,0,b.getWidth(),b.getHeight(),null,0,b.getWidth()));}
    private static BufferedImage pixels(UiCapture capture)throws IOException{UiCapture.Pixels p=capture.pixels();BufferedImage result=new BufferedImage(p.width,p.height,BufferedImage.TYPE_INT_ARGB);result.setRGB(0,0,p.width,p.height,p.argb,0,p.width);return result;}
    private static void notice(DesktopDd e,int caption)throws IOException{e.host.command("ui.confirm_position",new int[]{292,160,0,caption},new byte[0]);e.host.command("dd.ui.modal_notice",new int[]{0,0},new byte[0]);}
    private static void restore(DesktopDd e,byte[] snapshot)throws IOException{e.restore(snapshot);e.vm().restore(snapshot);}
    private static void shot(BufferedImage image,Path path)throws IOException{ImageIO.write(image,"png",path.toFile());}
    public static void main(String[] args)throws Exception{
        Path out=Paths.get(args[1]);Files.createDirectories(out);System.setProperty("dc.saveRoot",out.resolve("saves").toString());
        try(DesktopRuntime app=new DesktopRuntime(Paths.get(args[0]),true)){
            check(app.engine instanceof DesktopDd&&app.engine.resources.profile.id.equals("dc3dd-a5a8"),"Audit targets DD only");DesktopDd e=(DesktopDd)app.engine;DesktopSettingsAudit.settle(e);e.hover(-1,-1);byte[] title=e.vm().snapshot();
            check(e.host.ui.templates[20]!=null&&e.host.ui.templates[12]!=null,"Original START constructs distinct templates12/20");
            List<Map<String,Object>> rows=new ArrayList<>();int number=0;
            // These selectors are the five actual LOAD/SAVE error-notice occurrences.
            for(int caption:new int[]{1,3,2,1,3}){
                restore(e,title);Dc3Ui.Panel underlay=e.host.ui.current;BufferedImage before=e.render();notice(e,caption);
                check(e.host.ui.noticeActive()&&e.host.ui.underlay==underlay&&e.host.ui.current.buttons.size()==1,"Original template20 retains title underlay and single named button");
                Dc3Ui.Button button=e.host.ui.current.buttons.get(0);check(button.left==436&&button.top==260&&button.right==584&&button.bottom==324,"Native origin292,160 translates original button rect");
                check(e.controls().size()==1&&e.controls().get(0).action.equals(":y"),"Modal exposes only native named controls");
                BufferedImage open=e.render();check(!same(before,open),"Original caption/backdrop changes rendered pixels");shot(open,out.resolve("notice-"+number+"-caption"+caption+".png"));
                int scroll=e.host.read(99,246);e.scroll(1);check(scroll==e.host.read(99,246)&&!e.drag(button.left+2,button.top+2,true),"Modal blocks background scrolling and slider drag");
                byte[] snapshot=e.vm().snapshot();restore(e,snapshot);check(e.host.ui.noticeActive()&&same(open,e.render()),"Open DD56 snapshot restores exact original-asset pixels");
                UiCapture logical=SceneCaptures.dd(e.host.scene.capture(),SceneCaptures.ddPanel(e.host.ui,e.vm().variables),e.host.ui.palette,null);UiCapture frozen=e.host.captureRenderer.flatten(logical);
                check(same(open,pixels(frozen)),"Frozen modal capture resolves the same original sprites and underlay");
                check(e.modalKey(37)&&e.host.ui.hovered==0,"Single-button modal focus wraps to ordinal0");
                check(e.modalKey(32)&&e.host.ui.noticeActive(),"Native Space10 is consumed without accepting notice");
                if((number&1)==0)check(e.modalKey(10)&&!e.host.ui.noticeActive(),"Enter accepts notice and closes locally");else{e.click(0,0,true);check(!e.host.ui.noticeActive(),"Right/Android BACK cancels notice locally");}
                check(e.host.ui.underlay==null&&e.host.ui.current.buttons.size()==underlay.buttons.size(),"Closing restores original underlying controls");
                rows.add(Json.obj("caption",caption,"snapshot_bytes",snapshot.length,"modal_buttons",1,"capture_pixels_identical",true));number++;
            }
            restore(e,title);Dc3Ui.Panel normal=Dc3Ui.copy(e.host.ui.templates[12]);int flags=0;for(Dc3Ui.Button b:normal.buttons)if(b.checkbox)flags++;
            check(flags>0,"Original START34 marks real shared-builder checkbox sprites");
            // Native56 does not branch on action text: its ordinal1 toggles93:19.
            // Reusing the actual55 assets here supplies an explicit checkbox fixture;
            // the shipped20 has only :y and does not itself expose a checkbox.
            e.host.ui.templates[20]=normal;e.host.write(93,19,0);notice(e,1);e.host.ui.hovered=-1;BufferedImage unchecked=e.render();byte[] uncheckedState=e.vm().snapshot();
            e.host.write(93,19,1);BufferedImage checked=e.render();check(!same(unchecked,checked),"Live93:19 setter changes actual START checkbox sprite pixels");shot(unchecked,out.resolve("checkbox-unchecked.png"));shot(checked,out.resolve("checkbox-checked.png"));
            UiCapture logical=SceneCaptures.dd(e.host.scene.capture(),SceneCaptures.ddPanel(e.host.ui,e.vm().variables),e.host.ui.palette,null);UiCapture frozen=e.host.captureRenderer.flatten(logical);check(same(checked,pixels(frozen)),"Checked sprite is frozen by actual flag, independent of :c name");
            e.host.write(93,19,0);check(same(checked,pixels(frozen))&&!same(e.render(),pixels(frozen)),"Frozen preview does not follow later preference writes");
            restore(e,uncheckedState);check(e.host.ui.noticeActive()&&same(unchecked,e.render()),"Checkbox-bearing notice flags and pixels survive host41 restore");
            int value=e.host.read(93,19);Dc3Ui.Button toggle=e.host.ui.current.buttons.get(1);e.click((toggle.left+toggle.right)/2,(toggle.top+toggle.bottom)/2,false);
            check(e.host.read(93,19)!=value&&e.host.ui.noticeActive(),"Ordinal1 toggles19 without dispatching its :n label");check(e.modalKey(27)&&!e.host.ui.noticeActive(),"Escape closes the checkbox-bearing modal");
            restore(e,title);String target=e.controls().get(0).action;check(e.resources.scripts().script(e.vm().scriptName()).label(target.getBytes(Binary.SJIS))!=null,"Common-tail fixture uses an actual title script label");
            e.host.ui.templates[20]=Dc3Ui.copy(e.host.ui.templates[12]);e.host.command("ui.action",new int[]{2,0},target.getBytes(Binary.SJIS));notice(e,1);
            Dc3Ui.Button second=e.host.ui.current.buttons.get(1);e.hover((second.left+second.right)/2,(second.top+second.bottom)/2);e.click((second.left+second.right)/2,(second.top+second.bottom)/2,false);
            check(!e.host.ui.noticeActive()&&e.host.ui.noticeOverlay()!=null&&!e.modalKey(27),"Common left action releases modal input while retaining native34C0 sprite owner");
            BufferedImage retained=e.render();shot(retained,out.resolve("common-tail-retained.png"));byte[] pending=e.vm().snapshot();restore(e,pending);check(same(retained,e.render()),"Pending common action and detached visible popup roundtrip pixels");
            UiCapture retainedCapture=SceneCaptures.dd(e.host.scene.capture(),SceneCaptures.ddPanel(e.host.ui,e.vm().variables),e.host.ui.palette,null);
            check(same(retained,pixels(e.host.captureRenderer.flatten(retainedCapture))),"Capture freezes retained popup after wait has been released");
            check(e.vm().run(1)!=MesVm.Status.FAULT,"Pending frame action resolves its real script label without generic menu close");
            restore(e,title);Dc3Ui.Panel controlTemplate=Dc3Ui.copy(e.host.ui.templates[12]);Dc3Ui.Button sourceButton=controlTemplate.buttons.get(2);
            Dc3Ui.Sprite config=new Dc3Ui.Sprite();config.state=5;config.quad.resource="";config.quad.x=100;config.quad.y=410;config.quad.sx=31;config.quad.sy=99;config.quad.width=7;sourceButton.sprites.add(config);
            Dc3Ui.Sprite thumb=new Dc3Ui.Sprite();thumb.state=8;thumb.quad=sourceButton.sprites.get(0).quad.copy();thumb.quad.width=thumb.quad.height=8;sourceButton.sprites.add(thumb);
            Dc3Ui.Sprite fill=new Dc3Ui.Sprite();fill.state=105;fill.quad=thumb.quad.copy();fill.quad.width=40;sourceButton.sprites.add(fill);
            e.host.ui.templates[20]=controlTemplate;e.host.command("ui.action",new int[]{2,0},target.getBytes(Binary.SJIS));notice(e,1);Dc3Ui.Button control=e.host.ui.current.buttons.get(1);e.vm().variables.write(0,0,0);e.hover((control.left+control.right)/2,(control.top+control.bottom)/2);
            Dc3Ui.Sprite nativeThumb=null,nativeFill=null;for(Dc3Ui.Sprite sprite:control.sprites){if(sprite.state==101)nativeThumb=sprite;if(sprite.state==105)nativeFill=sprite;}
            check(nativeThumb!=null&&nativeThumb.quad.x==-4&&nativeFill!=null&&nativeFill.quad.width==0,"Original-resource control fixture normalizes8 to101 and uses cloned zero binding");
            check(e.modalKey(39)&&e.vm().variables.read(0,0)==1&&e.host.ui.noticeActive()&&e.host.ui.hovered==1,"Bound right release adjusts0:0 and ordinal1 while consuming global-left/focus navigation");
            check(e.modalKey(37)&&e.vm().variables.read(0,0)==0&&e.host.ui.noticeActive(),"Bound left release preserves native zero endpoint semantics");
            e.host.command("ui.action",new int[]{2,0},new byte[0]);e.pointerDown((control.left+control.right)/2,(control.top+control.bottom)/2);e.pointerUp((control.left+control.right)/2,(control.top+control.bottom)/2);
            check(e.vm().variables.read(0,0)==255&&e.host.ui.noticeActive(),"Bound touch/drag uses global coordinates and native count-1 byte wrap before ordinal1");
            BufferedImage boundImage=e.render();shot(boundImage,out.resolve("bound-clone-zero.png"));byte[] boundSnapshot=e.vm().snapshot();restore(e,boundSnapshot);check(same(boundImage,e.render()),"Owned source crop and separate origin preserve bound-modal snapshot pixels");
            UiCapture boundCapture=SceneCaptures.dd(e.host.scene.capture(),SceneCaptures.ddPanel(e.host.ui,e.vm().variables),e.host.ui.palette,null);check(same(boundImage,pixels(e.host.captureRenderer.flatten(boundCapture))),"Bound modal capture reads frozen native-normalized coordinates and crop");
            Files.write(out.resolve("result.json"),(Json.text(Json.obj("pass",true,"checks",checks,"profile",e.resources.profile.id,"original_exe_run",false,"fixture_entry","host command uses original START templates; core suite separately executes raw5600","actual_corpus_captions",rows,"checkbox_fixture","actual template12 reused as20, explicitly separate from shipped one-button20"))+"\n").getBytes("UTF-8"));
            System.out.println("DD modal original-resource audit PASS "+checks);
        }
    }
}
