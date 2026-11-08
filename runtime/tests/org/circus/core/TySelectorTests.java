package org.circus.core;

import java.io.*;
import java.util.*;

/** Native input/phase contracts, immutable render views and future-frame saves. */
public final class TySelectorTests {
    private static int checks;
    private static void check(boolean value,String why){checks++;if(!value)throw new AssertionError(why);}
    private static byte[] save(TySelector selector)throws Exception{ByteArrayOutputStream out=new ByteArrayOutputStream();selector.save(new DataOutputStream(out));return out.toByteArray();}
    private static TySelector copy(TySelector selector)throws Exception{TySelector other=new TySelector();DataInputStream in=new DataInputStream(new ByteArrayInputStream(save(selector)));other.load(in);check(in.available()==0,"Selector consumes exactly its own codec");return other;}
    private static void settle(TySelector selector){for(int n=0;n<100&&!selector.interactive()&&selector.active;n++)selector.advance(25);check(selector.interactive(),"Native selector reaches an interactive frame");}
    private static void click(TySelector selector,int item){Dc2Ui.Button control=selector.controls().get(item);selector.click((control.left+control.right)/2,(control.top+control.bottom)/2);selector.advance(0);}
    public static int run()throws Exception{
        checks=0;TySelector menu=new TySelector();menu.start(10,3,10,71);
        check(menu.active&&menu.visible&&menu.sourceSerial==71&&menu.generation==1,"Service12 arms a separate visible input owner");
        check(!menu.interactive()&&menu.controls().isEmpty()&&menu.frame().from==null&&menu.frame().weight255==0,"Intro hides controls and requires the actual old front");
        menu.click(600,80);menu.key(13);menu.advance(50);check(!menu.interactive()&&menu.selected()==-1&&menu.takeResult()==null,"Intro input cannot silently choose a story");
        check(menu.frame().weight255==127,"Native float255/fade duration truncates an intermediate frame");
        TySelector middle=copy(menu);menu.advance(20);middle.advance(20);check(Arrays.equals(save(menu),save(middle)),"Mid-intro save and future frame are identical");settle(menu);
        check(menu.controls().size()==5&&menu.currentBackground().equals(TySelector.BACK),"Overview exposes exactly five native controls");
        for(int n=0;n<5;n++){Dc2Ui.Button b=menu.controls().get(n);check(b.binding==n&&b.action.equals("ty:"+n),"Control binding routes input without jumping labels");check(menu.hit(b.left,b.top)==n&&menu.hit(b.right,b.bottom)==n,"Native hit edges are inclusive");}
        TySelector.Frame retained=menu.frame();int retainedCount=retained.target.sprites.size();
        boolean immutable=false;try{retained.target.sprites.clear();}catch(UnsupportedOperationException expected){immutable=true;}check(immutable,"Captured native patch sequence is immutable");
        click(menu,0);check(menu.active&&!menu.interactive()&&menu.selected()==0&&menu.takeResult()==null,"First card click starts preview without producing a result");
        check(menu.frame().from!=null&&menu.frame().target.background.equals("system/sce_select_hyd.crx"),"Preview uses its original family background and frozen old surface");
        check(retained.target.sprites.size()==retainedCount,"New preview does not mutate an earlier captured frame");
        check(menu.takeSound()==223&&menu.takeSound()==null,"First preview click queues the original confirmation sound once");settle(menu);
        TySelector previewCopy=copy(menu);menu.key(27);previewCopy.key(27);menu.advance(0);previewCopy.advance(0);
        check(menu.active&&!menu.interactive()&&menu.selected()==-1&&menu.takeResult()==null,"Escape from preview returns to overview without leaving modal");
        menu.advance(65);previewCopy.advance(65);check(Arrays.equals(save(menu),save(previewCopy)),"Return-fade save/input/future view are identical");settle(menu);menu.key(27);menu.advance(0);
        check(!menu.active&&menu.visible&&menu.takeResult()==4&&menu.takeResult()==null,"Escape from overview returns native cancellation4 exactly once");
        long generation=menu.generation;menu.clear();check(!menu.visible&&menu.generation==generation,"Clearing retained menu does not reset generation identity");

        for(int story=0;story<4;story++){
            TySelector pick=new TySelector();pick.start(1,3,255,story);settle(pick);click(pick,story);settle(pick);
            check(pick.selected()==story&&pick.takeResult()==null,"Each first legal card click remains a preview");
            TySelector.Frame frame=pick.frame();TySelector.Sprite last=frame.target.sprites.get(frame.target.sprites.size()-1);
            check(last.resource.equals(TySelector.ATLAS)&&last.drawX==-16&&last.drawY==new int[]{8,-40,-80,-136}[story]&&last.clipTop==new int[]{8,120,248,368}[story],"Selected card uses independent original440238/440300 table coordinates");
            click(pick,story);check(!pick.active&&pick.visible&&pick.takeResult()==story,"Second click of the same preview returns its own story index");
        }
        TySelector keys=new TySelector();keys.start(1,3,255,19);settle(keys);keys.key(38);keys.advance(0);check(keys.focused()==4,"Up from no keyboard focus wraps to return");keys.key(40);keys.advance(0);check(keys.focused()==0,"Down wraps return to the first story");
        keys.key(13);keys.advance(0);check(keys.selected()==0&&keys.active&&!keys.interactive()&&keys.focused()==-1,"Native Enter13 previews and clears keyboard focus");settle(keys);keys.key(13);keys.advance(0);check(keys.active&&keys.takeResult()==null,"Enter without pointer/focus cannot confirm an unfocused preview");keys.key(39);keys.advance(0);keys.key(13);keys.advance(0);check(keys.takeResult()==0&&!keys.active,"Native Enter13 confirms only after refocusing the preview");
        TySelector mixed=new TySelector();mixed.start(1,1,1,1);settle(mixed);mixed.pointer(600,200);mixed.advance(0);mixed.key(39);mixed.advance(0);mixed.key(13);mixed.advance(0);check(mixed.selected()==1&&mixed.focused()==-1,"Native descending mouse/keyboard hit priority precedes preview focus reset");
        TySelector backward=new TySelector();backward.start(1,1,1,1);settle(backward);backward.key(39);backward.advance(0);check(backward.focused()==0,"Right from no focus selects first card");backward.key(37);backward.advance(0);check(backward.focused()==4,"Left wraps first card to return");click(backward,4);check(backward.takeResult()==4&&!backward.active,"Original return control cancels without a fake story transfer");
        TySelector saved=copy(backward);check(saved.takeResult()==null&&saved.visible&&!saved.active,"Consumed completion and retained front roundtrip without re-trigger");
        return checks;
    }
    public static void main(String[] args)throws Exception{System.out.println("TySelectorTests "+run()+" PASS");}
}
