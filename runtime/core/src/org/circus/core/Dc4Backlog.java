package org.circus.core;

import java.io.IOException;

/** PH406140/405340 backlog atlas and four-row layout.
 * Uses the original panel and voice/selection controls. Native scene-preview
 * framing and portrait-name lookup are not part of this portable binding yet.
 */
public final class Dc4Backlog {
    public static final String BACK="SYSTEM/BKLOGBACK",CHIP="SYSTEM/BKLOGCHIP";
    public static final int ROWS=4,ROW_STEP=150,BODY_X=480,BODY_Y=104,BODY_WIDTH=720,BODY_HEIGHT=123;
    public static final int SCROLL_LEFT=1232,SCROLL_TOP=82,SCROLL_RIGHT=1265,SCROLL_BOTTOM=665;
    private Dc4Backlog(){}
    private static void quad(Dc3Ui ui,int x,int y,int sx,int sy,int w,int h){Dc3Scene.Quad q=new Dc3Scene.Quad();q.resource=CHIP;q.x=x;q.y=y;q.sx=sx;q.sy=sy;q.width=w;q.height=h;ui.current.quads.add(q);}
    private static void sprite(Dc3Ui.Button b,int state,int x,int y,int sx,int sy,int right,int bottom){Dc3Ui.Sprite s=new Dc3Ui.Sprite();s.state=state;s.quad.resource=CHIP;s.quad.x=x;s.quad.y=y;s.quad.sx=sx;s.quad.sy=sy;s.quad.width=right-sx;s.quad.height=bottom-sy;b.sprites.add(s);}
    private static Dc3Ui.Button button(Dc3Ui ui,String action,int l,int t,int r,int b)throws IOException{ui.button(new int[]{l,t,r,b},action);Dc3Ui.Button result=ui.last();result.hoverSound=201;result.clickSound=200;return result;}
    public static void build(Dc3Ui ui,Dc2History history)throws IOException{
        ui.clear();ui.waiting=true;history.previewEnabled=false;
        Dc3Scene.Quad bg=new Dc3Scene.Quad();bg.resource=BACK;bg.width=1280;bg.height=720;ui.current.quads.add(bg);
        if(history.entries.size()>ROWS){int y=82+550*history.cursor/Math.max(1,history.entries.size()-ROWS);quad(ui,1232,y,1200,38,24,30);}
        Dc3Ui.Button exit=button(ui,"@log:exit",1100,672,1216,712);sprite(exit,99,1100,672,640,8,744,47);sprite(exit,1,1100,672,640,47,744,89);sprite(exit,2,1100,672,640,47,744,89);
        for(int row=0;row<ROWS&&row+history.cursor<history.entries.size();row++){
            int at=row+history.cursor,y=82+ROW_STEP*row;Dc2History.Entry entry=history.entries.get(at);
            quad(ui,20,y+14,0,220,1203,123);
            Dc3Ui.Button select=button(ui,"@log:select:"+at,328,y+28,488,y+62);sprite(select,99,328,y+28,362,52,488,87);sprite(select,1,328,y+28,492,52,618,87);sprite(select,2,328,y+28,492,52,618,87);
            if(!entry.voice.isEmpty()){
                Dc3Ui.Button voice=button(ui,"@log:voice:"+at,328,y+68,488,y+101);voice.hoverSound=255;sprite(voice,99,328,y+68,362,12,488,46);sprite(voice,1,328,y+68,492,12,618,46);sprite(voice,2,328,y+68,492,12,618,46);
            }
        }
    }
}
