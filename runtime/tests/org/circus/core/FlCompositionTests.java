package org.circus.core;

import java.io.*;
import java.util.*;

/** Independent FL421D80/4202D0 vectors; clipping never means a black clear. */
public final class FlCompositionTests {
    private static int checks;
    private static void check(boolean yes,String why){checks++;if(!yes)throw new AssertionError(why);}
    private static byte[] bytes(FlComposition c)throws IOException{ByteArrayOutputStream b=new ByteArrayOutputStream();c.save(new DataOutputStream(b));return b.toByteArray();}
    private static FlComposition copy(FlComposition c)throws IOException{FlComposition d=new FlComposition();d.load(new DataInputStream(new ByteArrayInputStream(bytes(c))));return d;}
    public static int run()throws Exception {
        checks=0;UiCapture old=UiCapture.pixels(1,1,new int[]{0xff123456}),target=UiCapture.pixels(1,1,new int[]{0xffaabbcc});FlComposition c=new FlComposition();
        c.start(old,target,null,7,true,10,20,30,40);check(c.active&&!c.drawn&&c.draws().isEmpty()&&c.generation==1,"init owns captures and makes no invented clear");
        c.advance(0,0,false);check(c.progress==1&&c.draws().size()==1&&c.draws().get(0).kind==FlComposition.FEEDBACK&&c.draws().get(0).alpha==6,"first actor poll increments1 and feedback6");
        FlComposition.Draw draw=c.draws().get(0);check(!draw.prepared&&draw.left==10&&draw.top==20&&draw.right==30&&draw.bottom==40,"native feedback stays inside dirty clip");
        c.advance(0,0,false);c.advance(39,0,false);check(c.progress==1,"zero refresh and sub40ms phase do not repoll");FlComposition saved=copy(c);c.advance(1,0,false);saved.advance(1,0,false);check(Arrays.equals(bytes(c),bytes(saved))&&c.progress==2,"39ms snapshot retains timer phase");
        c.advance(100000,0,false);check(c.progress==3,"late timer coalesces to one feedback");c.advance(40,0,false);c.advance(40,0,false);check(c.progress==5&&c.active&&c.draws().get(4).kind==FlComposition.FULL,"phase5 fullcopy still holds native wait");c.advance(40,0,false);check(!c.active&&c.phase==2&&c.draws().size()==5,"following poll releases without another draw");check(Arrays.equals(bytes(c),bytes(copy(c))),"completed actor journal roundtrips");
        for(int speed=0;speed<=3;speed++){
            c.start(old,target,null,8,true,0,0,800,600);int polls=0;while(c.active&&polls++<10)c.advance(40,speed,false);
            int expected=speed==0?5:speed==1?3:speed==2?2:1;check(!c.active&&c.draws().size()==expected,"actor native step table "+speed);check(c.draws().get(expected-1).kind==FlComposition.FULL,"actor completes with fullcopy "+speed);
            if(speed<3)check(c.draws().get(0).alpha==(speed==0?6:speed==1?10:12),"actor feedback weight "+speed);
        }
        c.start(old,target,9);c.advance(0,0,true);check(!c.active&&c.draws().size()==1&&c.draws().get(0).kind==FlComposition.FULL,"Ctrl avoids actor temporary and presents directly");
        for(int speed=0;speed<=3;speed++){
            c.start(old,target,target,10,false,0,0,800,600);int polls=0;while(c.active&&polls++<20)c.advance(40,speed,false);
            int steps=speed==0?16:speed==1?8:speed==2?4:2;check(!c.active&&c.progress==16&&c.draws().size()==steps+1,"prepare progress/terminal fullcopy "+speed);
            check(c.draws().get(0).prepared&&c.draws().get(0).alpha==(speed==0?3:speed==1?6:speed==2?8:10),"prepare weight is independent of actor weight "+speed);
            check(Arrays.equals(bytes(c),bytes(copy(c))),"retained prepare phase2/progress16 snapshot "+speed);
        }
        c.start(old,target,target,11,true,5,6,70,80);for(int n=0;n<16;n++)c.advance(40,0,false);check(c.active&&c.phase==1&&c.progress==0,"prepare completion enters actor wait with original clip");c.advance(0,0,false);check(c.progress==1&&!c.draws().get(c.draws().size()-1).prepared,"actor phase first poll uses final target");
        byte[] before=bytes(c);boolean no=false;try{c.load(new DataInputStream(new ByteArrayInputStream(Arrays.copyOf(before,before.length-1))));}catch(IOException expected){no=true;}check(no&&Arrays.equals(before,bytes(c)),"truncated composition snapshot does not publish partialstate");
        return checks;
    }
    public static void main(String[] args)throws Exception{System.out.println("FlCompositionTests "+run()+" PASS");}
}
