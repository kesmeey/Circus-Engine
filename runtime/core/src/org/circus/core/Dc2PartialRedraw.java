package org.circus.core;

import java.io.IOException;

/** Current originalDC2 41D3F0 retained partial redraw. This is not an ARGB
 *scene reconstruction and is not PC1's similarly shaped41AF50 dispatcher.
 *All frame/template/UI/palette fields borrow the addressed native owner. */
public final class Dc2PartialRedraw {
    public interface Services {
        void effects(int layer)throws IOException; //411C20,actual same-compositor pipeline
        void glyphJournal(int first)throws IOException; //419330
        void waitCursor(int mode)throws IOException; //41AA70
        void controls()throws IOException; //40C3E0,not whole-scene refresh
    }
    public static final int BACKGROUND=0x45e800,BG_X=0xa09e34,BG_Y=0xa09e38;
    public static final int BODY=0xa08508,NAME=0xa08798,BODY_COUNT=0xbe8c44,NAME_COUNT=0xbe8c4c;
    public static final int PARAGRAPH=0xa0ac24,CACHED=0xa0ac20,VISIBLE=0xbe8bc8,WAIT=0xa09198;
    public static final int SLOTS=0x975654,OPACITY=0x473f80,OPACITY_ENABLED=0x473f88,BUTTON_POINTER=0x451d10;
    private final Dc2Rain.State state;
    private final Dc2Compositor c;
    private final Services services;
    public Dc2PartialRedraw(Dc2Rain.State state,Dc2Compositor compositor,Services services)throws IOException{if(state==null||compositor==null)throw new IOException("DC2 partial redraw canonical state/compositor missing");this.state=state;c=compositor;this.services=services;}
    private int g(int a)throws IOException{return state.read(a);}
    private Services io(int entry)throws IOException{if(services==null)throw new IOException("DC2 partial redraw requires native consumer"+Integer.toHexString(entry));return services;}
    private void clip(int l,int t,int r,int b)throws IOException{c.setClip(l,t,r,b);}
    private int color(int n)throws IOException{return Pc1GlyphRaster.paletteRgb(g(Dc2Compositor.PALETTE+n*4));}
    private void actors()throws IOException{
        int mask=g(0x991b24),handle=g(0x9d9f9c),y=g(0xa09e40),oy=g(0xbe8b80),x=g(0xa09e3c),ox=g(0xbe8b7c);c.masked(x+ox,y+oy,handle,mask);
        handle=g(0x991b28);y=g(0xa09e40);oy=g(0xbe8b80);ox=g(0xbe8b7c);x=g(0xa09e3c);c.transparent(x+ox,y+oy,handle);
        mask=g(0x9c0b30);handle=g(0x9d9fa0);oy=g(0xbe8b80);y=g(0xa09e48);ox=g(0xbe8b7c);x=g(0xa09e44);c.masked(x+ox,y+oy,handle,mask);
        handle=g(0x991b20);oy=g(0xbe8b80);y=g(0xa09e48);ox=g(0xbe8b7c);x=g(0xa09e44);c.transparent(x+ox,y+oy,handle);
    }
    private void record(int p)throws IOException{
        int type=g(p+8);if(type==10)return;
        if((type&64)==0){int x=g(p),y=g(p+4);c.intersectClip(x,y,x+g(p+24),y+g(p+28));type=g(p+8);
            if(type==9){int mask=g(0x9fce50),handle=g(0x9fce48),sy=g(p+20),sx=g(p+16);c.masked(sx,sy,handle,mask);}
            else if((type&128)==0||g(OPACITY_ENABLED)==0){int handle=g(SLOTS+(type&127)*4),sy=g(p+20),sx=g(p+16);c.transparent(sx,sy,handle);}
            else{int weight=g(OPACITY),handle=g(SLOTS+(type&127)*4),sy=g(p+20),sx=g(p+16);c.opacity(sx,sy,handle,weight);}
        }else{
            boolean blend=(type&128)!=0&&(g(OPACITY_ENABLED)!=0||g(PARAGRAPH)!=0);
            //DC2 p+20 overrides opacity. Zero chooses the live preference;
            //PC1's analogous dispatcher has no such per-record override.
            int weight=16;if(blend){weight=g(p+20);if(weight==0)weight=g(OPACITY);}
            int y=g(p+4),x=g(p),bottom=y+g(p+28),right=x+g(p+24);if(blend)c.fillBlend16(x,y,right,bottom,color(type&63),weight);else c.fill(x,y,right,bottom,color(type&63));
        }
    }
    private void templates(boolean name,int l,int t,int r,int b)throws IOException{
        int count=name?NAME_COUNT:BODY_COUNT,p=(name?NAME:BODY)+g(PARAGRAPH)*(name?160:320);
        for(int n=0;n<g(count+g(PARAGRAPH)*4);n++,p+=32)if(g(p+12)==0||g(CACHED)!=0){clip(l,t,r,b);record(p);}
        if(g(0x45e808)==-1){clip(l,t,r,b);if(name){int x=g(0x45e83c),y=g(0x45e840);c.fillBlend16(x,y,x+g(0x45e850)*28,y+g(0x45e854)*g(0x45e844),color(0),g(OPACITY));}else{int x=g(0x45e820),y=g(0x45e824);c.fillBlend16(x,y,x+476,y+g(0x45e828)*4,color(0),g(OPACITY));}}
    }
    /**BYTE-mask41D3F0. It deliberately preserves each native clip reset and
     *leaf's live state changes; there is no implicit present or fullClip. */
    public void redraw(int mask,int l,int t,int r,int b)throws IOException{
        if(g(Dc2RainPipeline.PENDING)!=0)return;clip(l,t,r,b);
        if((mask&1)!=0){int handle=g(BACKGROUND),y=g(BG_Y),x=g(BG_X);c.copy(x,y,handle);}
        if((mask&2)!=0)io(0x411c20).effects(1);
        if((mask&4)!=0){clip(l,t,r,b);actors();}
        if((mask&8)!=0)io(0x411c20).effects(0);
        if((mask&16)!=0&&g(VISIBLE)!=0){templates(false,l,t,r,b);templates(true,l,t,r,b);io(0x419330).glyphJournal(0);}
        if((mask&32)==0)return;
        if(g(0xbe8bec)!=0||g(WAIT)==0x43){clip(l,t,r,b);int pointer=g(BUTTON_POINTER);c.intersectClip(g(pointer+12),g(pointer+16),g(pointer+20),g(pointer+24));int handle=g(SLOTS+(g(g(BUTTON_POINTER)+40)&127)*4),sy=g(g(BUTTON_POINTER)+52),sx=g(g(BUTTON_POINTER)+48);c.transparent(sx,sy,handle);}
        int wait=g(WAIT);if(wait==7){if(g(VISIBLE)!=0){clip(l,t,r,b);io(0x41aa70).waitCursor(1);clip(l,t,r,b);io(0x40c3e0).controls();}}
        else if(wait==10||wait==11||wait==9&&g(VISIBLE)!=0){clip(l,t,r,b);io(0x40c3e0).controls();}
    }
}
