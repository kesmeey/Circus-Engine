package org.circus.core;

import java.io.*;
import java.util.*;

/** State oracle for TY421E10. Pixel feedback is audited separately by the platform. */
public final class TyScrollTests {
    private static int checks;
    private static void check(boolean yes,String why){checks++;if(!yes)throw new AssertionError(why);}
    private static byte[] save(TyScroll value)throws IOException{ByteArrayOutputStream b=new ByteArrayOutputStream();value.save(new DataOutputStream(b));return b.toByteArray();}
    public static int run()throws Exception{
        checks=0;UiCapture old=UiCapture.pixels(1,1,new int[]{0xff102030}),full=UiCapture.pixels(1,1,new int[]{0xffa0b0c0});TyScroll.Composer composer=(x,y)->full;
        for(int speed=0;speed<4;speed++){
            TyScroll s=new TyScroll();s.start(old,"bg",0,0,100,-100,2,1);check(s.draws().isEmpty()&&s.initial==old&&s.active,"Start retains old front without drawing");s.advance(0,speed,false,composer);int step=2<<speed;check(s.x==step&&s.y==-step&&s.draws().get(0).weight==2&&s.draws().get(0).compose==null,"Native2/4/8/16 first poll speed"+speed);int prior=s.x;s.advance(100000,speed,false,composer);check(s.x==prior+step,"Late17ms timer coalesces to one step");
        }
        TyScroll s=new TyScroll();s.start(old,"bg",0,0,5,3,2,7);s.advance(0,1,false,composer);check(s.x==4&&s.y==3,"Axes clamp independently");s.advance(16,1,false,composer);check(s.x==4&&s.draws().size()==1,"No extra poll before17ms");byte[] state=save(s);TyScroll restored=new TyScroll();restored.load(new DataInputStream(new ByteArrayInputStream(state)));check(Arrays.equals(state,save(restored)),"Partial clock and journal roundtrip");restored.advance(1,1,false,composer);check(restored.x==5&&restored.active&&restored.holdPolls==0,"Arrival frame retains native wait and does not count a dwell yet");
        for(int n=1;n<=14;n++){restored.advance(17,1,false,composer);check(restored.weight==2&&restored.holdPolls==n&&restored.draws().get(restored.draws().size()-1).compose==null,"Native dwell before cutoff "+n);}
        restored.advance(17,1,false,composer);TyScroll.Frame finalFrame=restored.draws().get(restored.draws().size()-1);check(restored.active&&restored.weight==0&&restored.holdPolls==15&&finalFrame.compose==full,"weight+hold>16 emits one full composition while still waiting");byte[] last=save(restored);TyScroll copy=new TyScroll();copy.load(new DataInputStream(new ByteArrayInputStream(last)));check(Arrays.equals(last,save(copy)),"Last full frame codec retains prepared surface");copy.advance(17,1,false,composer);check(!copy.active&&copy.visible&&copy.draws().size()==17,"Only next poll releases and retains the last front");
        TyScroll aligned=new TyScroll();aligned.start(old,"bg",9,8,9,8,0,2);check(!aligned.advance(0,3,true,composer)&&!aligned.active&&aligned.draws().isEmpty(),"Already-aligned opaque scroll releases without replacing physical front");
        TyScroll ctrl=new TyScroll();ctrl.start(old,"bg",0,0,100,100,0,2);ctrl.advance(0,0,true,composer);check(ctrl.x==16&&ctrl.draws().get(0).compose==full,"Ctrl selects16 and weight0 freezes complete compose inputs");long gen=ctrl.generation;ctrl.start(old,"bg",0,0,10,10,30,3);check(ctrl.generation==gen+1&&ctrl.weight==16&&ctrl.draws().isEmpty(),"New owner generation and native alpha clamp");ctrl.clear();check(ctrl.initial==null&&!ctrl.visible&&ctrl.draws().isEmpty()&&ctrl.generation==gen+1,"Release drops heavy owner but preserves generation");
        return checks;
    }
    public static void main(String[] args)throws Exception{System.out.println("TyScrollTests "+run()+" PASS");}
}
