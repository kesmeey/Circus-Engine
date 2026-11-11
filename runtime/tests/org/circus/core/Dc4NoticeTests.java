package org.circus.core;

import java.io.*;

/** Native5B selects the independent notice template and bypasses93 flags. */
public final class Dc4NoticeTests {
    private static int checks;
    private static void check(boolean ok,String why){checks++;if(!ok)throw new AssertionError(why);}
    private static Dc3Ui.Sprite sprite(String name,int state){Dc3Ui.Sprite s=new Dc3Ui.Sprite();s.state=state;s.quad.resource=name;s.quad.width=100;s.quad.height=40;return s;}
    public static int run()throws Exception{
        checks=0;Dc4Host h=new Dc4Host(null,TextCodec.identity(),true);h.attach(new Variables(h,Variables.Model.BYTE_WRAP));h.ui.exactCheckboxFlags(false);Dc4UiCommands ui=new Dc4UiCommands();
        Dc3Ui.Panel normal=new Dc3Ui.Panel(),notice=new Dc3Ui.Panel();h.ui.templates[12]=normal;h.ui.templates[21]=notice;h.ui.current=notice;
        ui.command(h,"host.command_with_string",new int[]{0,0,100,40},new byte[0]);h.ui.last().sprites.add(sprite("notice-back",99));h.ui.last().sprites.add(sprite("notice-caption",2));
        ui.command(h,"host.command_with_string",new int[]{10,20,110,60},new byte[]{':','y'});h.ui.last().sprites.add(sprite("notice-ok",99));
        Dc3Ui.Panel caller=new Dc3Ui.Panel();h.ui.current=caller;h.ui.confirmKind=0;h.ui.confirmX=20;h.ui.confirmY=30;h.state.write(93,0,1);
        ui.command(h,"ph.ui.notice",new int[0],new byte[0]);
        check(h.ui.waiting&&h.ui.underlay==caller&&h.ui.activeConfirmation==0,"5B opens notice even when the corresponding93 confirmation flag is set");
        check(h.ui.current.quads.size()==2&&h.ui.current.quads.get(0).resource.equals("notice-back")&&h.ui.current.quads.get(1).resource.equals("notice-caption"),"5B uses template21's backdrop and indexed caption");
        check(h.ui.templates[12]==normal&&h.ui.templates[21]==notice,"Temporary notice construction preserves both original templates");
        check(h.ui.current.buttons.size()==1&&h.ui.current.buttons.get(0).left==30&&h.ui.current.buttons.get(0).top==50,"Notice keeps action bytes and applies native requested origin");
        h.ui.closeConfirmation();h.ui.waiting=false;h.ui.confirmKind=5;
        try{ui.command(h,"ph.ui.notice",new int[0],new byte[0]);throw new AssertionError("Missing caption accepted");}catch(IOException expected){check(h.ui.templates[12]==normal,"Failed construction also restores normal59 template in finally");}
        return checks;
    }
    public static void main(String[] args)throws Exception{System.out.println("Dc4NoticeTests "+run()+" PASS");}
}
