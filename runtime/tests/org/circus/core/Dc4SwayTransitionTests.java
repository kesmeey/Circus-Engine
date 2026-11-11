package org.circus.core;

import java.io.*;

/** Actual8E horizontal sine range and0F8/40 fallback commit behavior. */
public final class Dc4SwayTransitionTests {
    private static int checks;
    private static void check(boolean ok,String why){checks++;if(!ok)throw new AssertionError(why);}
    private static Dc4Host host(){Dc4Host h=new Dc4Host(null,TextCodec.identity(),true);h.attach(new Variables(h,Variables.Model.BYTE_WRAP));return h;}
    public static int run()throws Exception{
        checks=0;Dc4Host h=host();Dc4Sway sway=new Dc4Sway();h.scene.slots[4].x=100;h.scene.slots[4].y=80;
        check(sway.command(h,new int[]{60,0,0,32})&&sway.command(h,new int[]{40,0,360,16})&&sway.remainingMillis()==1017,"Actual8E config and radius schedule a native initialization plus60 updates");
        sway.elapse(h,17);check(h.scene.slots[4].x==100,"First native sway update initializes without angular movement");
        sway.elapse(h,250);check(h.scene.slots[4].x==140&&h.scene.slots[4].y==80,"Quarter-period sine reaches positive amplitude on the horizontal axis only");
        ByteArrayOutputStream out=new ByteArrayOutputStream();sway.save(new DataOutputStream(out));Dc4Sway restored=new Dc4Sway();restored.load(new DataInputStream(new ByteArrayInputStream(out.toByteArray())));
        restored.elapse(h,500);check(h.scene.slots[4].x==60,"Cold restored sine reaches its negative amplitude");
        restored.elapse(h,250);check(!restored.active(0)&&h.scene.slots[4].x==100&&restored.remainingMillis()==0,"Completed angular range restores the exact original position");
        restored.command(h,new int[]{40,0,360,16});restored.elapse(h,267);restored.reset(h);check(h.scene.slots[4].x==100&&!restored.active(0),"Scene clear retires sine displacement without retaining drift");
        h.scene.shake(4,0);h.scene.shake(20,1);h.scene.pending[1]="BG/new";
        check(Dc4Transition.command(h,"scene.transition",new int[]{40,2},new byte[0]),"Actual0F40 is handled before the shared DD range check");
        check(h.scene.motions.size()==1&&h.scene.motions.get(0).slot==20,"Native fallback stops only cast logical4..13");
        check(h.scene.slots[1].resource.equals("BG/new")&&h.scene.pending[1]==null&&h.scene.published.layers[1].resource.equals("BG/new"),"Portable fallback commits the new scene rather than dropping pending resources");
        check(h.scene.transition==40&&h.scene.transitionParameter==2&&!h.scene.fade.active(),"Fallback preserves actual mode metadata without an invented fade clock");
        check(Dc4Transition.command(h,"scene.transition",new int[]{8,0},new byte[0])&&!Dc4Transition.command(h,"scene.transition",new int[]{21,5},new byte[0]),"Other actual fallback8 is handled and known native fade21 delegates unchanged");
        return checks;
    }
    public static void main(String[] args)throws Exception{System.out.println("Dc4SwayTransitionTests "+run()+" PASS");}
}
