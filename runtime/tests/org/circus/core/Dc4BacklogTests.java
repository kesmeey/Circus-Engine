package org.circus.core;

/** Independently authored history entries exercise PH's native atlas contract. */
public final class Dc4BacklogTests {
    private static int n;
    private static void check(boolean ok,String why){n++;if(!ok)throw new AssertionError(why);}
    public static int run()throws Exception{
        n=0;Dc2History history=new Dc2History();for(int i=0;i<7;i++)history.append("owned speaker","owned body "+i,i%2==0?"owned voice":"","owned",i);history.open();Dc3Ui ui=new Dc3Ui();Dc4Backlog.build(ui,history);
        check(ui.waiting&&ui.current.quads.get(0).resource.equals("SYSTEM/BKLOGBACK"),"PH selects its actual BKLOG background");
        check(ui.current.quads.get(0).width==1280&&ui.current.quads.get(0).height==720,"PH native screen geometry");
        int rows=0,voices=0;for(Dc3Ui.Button b:ui.current.buttons){
            check((b.left+b.right)/2>=0&&(b.left+b.right)/2<1280&&(b.top+b.bottom)/2<720,"Original controls have on-screen centers");
            if(b.action.startsWith("@log:select:"))rows++;if(b.action.startsWith("@log:voice:"))voices++;
            for(Dc3Ui.Sprite s:b.sprites)check(s.quad.resource.equals("SYSTEM/BKLOGCHIP")&&s.quad.sx+s.quad.width<=1273&&s.quad.sy+s.quad.height<=854,"PH controls crop the actual BKLOG atlas");
        }
        check(rows==4&&voices==2,"Four visible native rows retain their matching voice entries");
        check(ui.current.buttons.stream().anyMatch(b->b.action.equals("@log:select:3"))&&ui.current.buttons.stream().anyMatch(b->b.action.equals("@log:select:6")),"History offset is preserved in selection actions");
        check(!history.previewEnabled&&ui.current.buttons.stream().noneMatch(b->b.action.equals("@log:preview")),"Unverified native scene preview is explicitly disabled");
        check(ui.current.quads.stream().filter(q->q.resource.equals("SYSTEM/BKLOGCHIP")&&q.width==1203&&q.height==123).count()==4,"Native full-width row backplates replace DD's narrow crops");
        history.clear();history.open();Dc4Backlog.build(ui,history);check(ui.current.buttons.size()==1&&ui.current.buttons.get(0).action.equals("@log:exit"),"Empty history remains dismissible without stale controls");
        return n;
    }
}
