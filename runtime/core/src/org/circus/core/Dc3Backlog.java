package org.circus.core;
import java.io.*;
/** Native DD backlog geometry from 4060E0, 401110 and 405680. */
public final class Dc3Backlog {
    private Dc3Backlog(){}
    private static void quad(Dc3Ui ui,int x,int y,int sx,int sy,int w,int h){Dc3Scene.Quad q=new Dc3Scene.Quad();q.resource="SYSTEM/LOGCHIP";q.x=x;q.y=y;q.sx=sx;q.sy=sy;q.width=w;q.height=h;ui.current.quads.add(q);}
    private static void button(Dc3Ui ui,String action,int x,int y,int r,int b,int sx,int sy,int w,int h)throws IOException{
        ui.button(new int[]{x,y,r,b},action);Dc3Ui.Button button=ui.last();button.hoverSound=223;button.clickSound=224;
        if(w>0){Dc3Ui.Sprite sprite=new Dc3Ui.Sprite();sprite.state=99;sprite.quad.resource="SYSTEM/LOGCHIP";sprite.quad.x=x;sprite.quad.y=y;sprite.quad.sx=sx;sprite.quad.sy=sy;sprite.quad.width=w;sprite.quad.height=h;button.sprites.add(sprite);}
    }
    private static void state(Dc3Ui ui,int state,int sx,int sy,int w,int h,int dx)throws IOException{Dc3Ui.Button b=ui.last();Dc3Ui.Sprite s=new Dc3Ui.Sprite();s.state=state;s.quad.resource="SYSTEM/LOGCHIP";s.quad.x=b.left+dx;s.quad.y=b.top;s.quad.sx=sx;s.quad.sy=sy;s.quad.width=w;s.quad.height=h;b.sprites.add(s);}
    public static void build(Dc3Ui ui,Dc2History history)throws IOException{
        ui.clear();ui.waiting=true;
        Dc3Scene.Quad bg=new Dc3Scene.Quad();bg.resource="SYSTEM/LOGBACK";bg.width=1024;bg.height=576;ui.current.quads.add(bg);
        if(history.entries.size()>4){
            quad(ui,943,76,820,0,35,336);
            button(ui,"@log:first",940,12,978,54,696,228,40,37);
            state(ui,1,696,266,40,37,0);state(ui,2,696,266,40,37,0);
            button(ui,"@log:up",940,54,978,78,680,0,40,23);
            state(ui,1,680,24,40,23,0);state(ui,2,680,48,40,23,0);
            button(ui,"@log:down",940,416,978,440,720,0,40,23);
            state(ui,1,720,24,40,23,0);state(ui,2,720,48,40,23,0);
            button(ui,"@log:last",940,450,978,492,736,228,40,37);
            state(ui,1,736,266,40,37,0);state(ui,2,736,266,40,37,0);
            quad(ui,944,80+332*history.cursor/Math.max(1,history.entries.size()-4),216,0,32,31);
        }
        button(ui,"@log:exit",908,516,1017,572,696,364,109,55);
        state(ui,1,696,420,109,55,0);state(ui,2,696,476,109,55,1);
        for(int row=0;row<4&&row+history.cursor<history.entries.size();row++){
            int at=row+history.cursor,y=44+130*row;Dc2History.Entry entry=history.entries.get(at);
            quad(ui,206,y,0,0,679,131);
            button(ui,"@log:select:"+at,206,y,885,y+131,0,0,0,0);
            if(!entry.voice.isEmpty()){button(ui,"@log:voice:"+at,98,138+130*row,198,170+130*row,696,132,100,31);state(ui,1,696,164,100,31,0);state(ui,2,696,196,100,31,0);}
        }
        button(ui,"@log:preview",14,80,206,110,696,775,186,30);
        state(ui,1,696,806,186,30,0);state(ui,2,696,806,186,30,0);
        if(history.previewEnabled)for(int s:new int[]{99,1,2}){state(ui,s,884,775,25,24,2);Dc3Ui.Button b=ui.last();b.sprites.get(b.sprites.size()-1).quad.y+=2;}
    }
}
