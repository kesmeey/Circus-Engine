package org.circus.core;

import java.io.*;

/** Native78 moves visual geometry and its hit region as one panel. */
public final class Dc4UiMoveTests {
    private static int checks;
    private static void check(boolean ok,String why){checks++;if(!ok)throw new AssertionError(why);}
    public static int run()throws Exception{
        checks=0;Dc4Host h=new Dc4Host(null,TextCodec.identity(),true);h.attach(new Variables(h,Variables.Model.BYTE_WRAP));
        Dc4UiCommands ui=new Dc4UiCommands();Dc3Ui.Panel p=h.ui.current;
        ui.command(h,"host.command_with_string",new int[]{10,20,110,60},new byte[]{65});
        Dc3Scene.Quad q=new Dc3Scene.Quad();q.resource="fixture";q.x=10;q.y=20;p.quads.add(q);
        Dc3Ui.Sprite sprite=new Dc3Ui.Sprite();sprite.quad=q;p.buttons.get(0).sprites.add(sprite);
        Dc3Ui.Text text=new Dc3Ui.Text();text.args=new int[]{12,23,32,0};text.text="title";p.texts.add(text);
        ui.command(h,"ph.ui.move",new int[]{1280,0,0,0},new byte[0]);
        check(q.x==1290&&p.buttons.get(0).left==1290&&text.args[0]==1292,"Start origin translates each aliased quad once, input bounds and text together");
        ui.elapse(h,167);int halfway=ui.originX(0);
        check(halfway>630&&halfway<650&&q.x==halfway+10&&p.buttons.get(0).left==halfway+10,"Twenty-tick panel movement reaches its half position on the portable clock");
        check(ui.remainingMillis(0)==167&&ui.remainingMillis(1)==0,"Native15:255 barrier can wait only the primary panel's remaining movement");
        ui.command(h,"ph.ui.move",new int[]{0,0,360,0},new byte[0]);check(ui.originX(0)==halfway,"Native busy guard ignores an overlapping move");
        ByteArrayOutputStream frozen=new ByteArrayOutputStream();ui.save(new DataOutputStream(frozen));Dc4UiCommands restored=new Dc4UiCommands();restored.load(new DataInputStream(new ByteArrayInputStream(frozen.toByteArray())));
        restored.elapse(h,167);check(!restored.moving(0)&&restored.originX(0)==0&&q.x==10&&p.buttons.get(0).left==10&&text.args[0]==12,"Snapshot restores the remaining move, reaching the exact target without input drift");
        restored.command(h,"ph.ui.move",new int[]{0,0,360,0},new byte[0]);restored.elapse(h,334);
        check(q.x==370&&p.buttons.get(0).contains(375,30)&&text.args[0]==372,"MAIN second target360 shifts rendered and clickable geometry");
        restored.command(h,"ui.navigation",new int[]{99,1},new byte[0]);
        restored.command(h,"host.command_with_string",new int[]{1,2,11,12},new byte[]{66});
        restored.command(h,"ph.ui.move",new int[]{0,0,0,40},new byte[0]);restored.elapse(h,334);
        check(h.ui.current.buttons.get(0).top==42&&p.buttons.get(0).top==20,"Each construction builder owns its native origin");
        return checks;
    }
    public static void main(String[] args)throws Exception{System.out.println("Dc4UiMoveTests "+run()+" PASS");}
}
