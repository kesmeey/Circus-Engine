package org.circus.core;

import java.io.*;
import java.util.*;

/** Original table coordinates, timer boundaries and native mode0 input contract. */
public final class DcdxMenuTests {
    private static int checks;
    private static void check(boolean yes,String why){checks++;if(!yes)throw new AssertionError(why);}
    private static DcdxMenu menu()throws Exception{DcdxMenu m=new DcdxMenu();m.start(0,"bg","cards","yesno","preview",17);return m;}
    private static byte[] save(DcdxMenu m)throws Exception{ByteArrayOutputStream b=new ByteArrayOutputStream();m.save(new DataOutputStream(b));return b.toByteArray();}
    public static int run()throws Exception{
        checks=0;DcdxMenu m=menu();check(m.front==0&&m.cardX(0)==412&&m.cardY(0)==248,"Native0-degree story is centered and frontmost");check(m.cardX(1)==691&&m.cardY(1)==184&&m.cardX(2)==412&&m.cardY(2)==120&&m.cardX(3)==132&&m.cardY(3)==183,"Original non-PI radians and truncation retain asymmetry");
        check(m.layers().size()==1&&!m.advance(32)&&m.layers().size()==1,"Initializer only blits background before floor1000/30 deadline");check(m.advance(1)&&m.layers().size()==5,"33ms first native poll paints four depth-ordered cards");check(!m.advance(32)&&m.advance(1),"Second rational deadline is66ms");check(!m.advance(33)&&m.advance(1),"Third deadline is100ms");
        List<Dc2Ui.Button> controls=m.controls();check(controls.size()==4&&controls.get(3).action.equals("story")&&new HashSet<String>(Arrays.asList(controls.get(0).action,controls.get(1).action,controls.get(2).action,controls.get(3).action)).equals(new HashSet<String>(Arrays.asList("story","ard","3do","end"))),"Depth controls expose actual native labels without flags");
        check(m.click(1)==null&&m.turning(),"A nonfront ard click rotates first");check(m.key(13)==null&&m.click(0)==null,"Inputs are swallowed during rotation");for(int n=0;n<14;n++)m.advance(1000);check(!m.turning()&&m.front==1&&m.key(13).equals("ard"),"Negative rotation wraps to native270-degree index and activates front");
        m.hover(1);List<DcdxMenu.Layer> layers=m.layers();DcdxMenu.Layer preview=layers.get(layers.size()-1);check(preview.resource.equals("preview")&&preview.sx==1024&&preview.sy==0&&preview.keyed&&preview.sw==1024&&preview.dw==1024,"Front hover selects the correct native full-screen atlas tile");
        m.click(3);m.advance(67);byte[] bytes=save(m);DcdxMenu copy=new DcdxMenu();copy.load(new DataInputStream(new ByteArrayInputStream(bytes)));check(Arrays.equals(bytes,save(copy)),"Menu snapshot retains current painted positions and future rotation state");for(int n=0;n<26;n++){m.advance(34);copy.advance(34);check(Arrays.equals(save(m),save(copy)),"Future poll is identical after new-owner restore "+n);}check(m.front==3&&m.key(13).equals("end"),"Opposite card uses two native90-degree steps");
        m.release();check(!m.active&&m.visible&&!m.layers().isEmpty(),"Activation retains its physical frame until replacement");m.resume(21);check(m.active&&m.sourceSerial==21&&m.front==3,"53 resumes the existing owner without resetting selection");boolean failure=false;try{m.start(1,"bg","cards","yesno","preview",1);}catch(IOException expected){failure=true;}check(failure,"Unimplemented voice wheel remains explicit");return checks;
    }
    public static void main(String[] args)throws Exception{System.out.println("DcdxMenuTests "+run()+" PASS");}
}
