package org.circus.core;

import java.io.*;

/** Current41AF50 mask-selective retained redraw,40A2A0 controls and4186F0
 *wait cursor. Every field is borrowed from the canonical addressed owners.
 *This is not Underlay.body and does not manufacture a full-frame publish. */
public final class Pc1GeneralRedraw {
    public interface Services {
        void effects(int layer)throws IOException; //40FA80,may reenter this dispatcher
        void glyphJournal(int first)throws IOException; //4170C0,actual cache/font/pixels
        void rangeButton(int rawButton,int state)throws IOException; //4097C0
        void expand(int source,int destination)throws IOException; //4123A0
        void inline(int x,int y,int pitch,byte[] knownRaw,int style)throws IOException; //418930
        void rawGlyph28(int x,int y,byte[] knownRaw,int palette)throws IOException; //409140
        void restoreBody(int left,int top,int right,int bottom,int mode)throws IOException; //415D50
    }
    private final Pc1Compositor c;
    private final Pc1Compositor.State state;
    private final Pc1InlineText.Memory memory;
    private final Services services;
    private static final int PARAGRAPH=0x9da4ac,CACHED=0x9da4a8,WAIT=0x9d8a20;
    public Pc1GeneralRedraw(Pc1Compositor compositor,Pc1Compositor.State state,Pc1InlineText.Memory memory,Services services)throws IOException {
        if(compositor==null||state==null||memory==null)throw new IOException("PC1 general redraw canonical compositor/byte state missing");c=compositor;this.state=state;this.memory=memory;this.services=services;
    }
    private int g(int address)throws IOException{return state.readInt(address);}
    private void g(int address,int value)throws IOException{state.writeInt(address,value);}
    private Services io(int address)throws IOException{if(services==null)throw new IOException("PC1 general redraw requires native consumer"+Integer.toHexString(address));return services;}
    private int color(int index)throws IOException{return Pc1GlyphRaster.paletteRgb(g(Pc1DrawBatch.PALETTE+index*4));}
    private byte[] string(int address)throws IOException{ByteArrayOutputStream out=new ByteArrayOutputStream();for(int n=0;n<1024*1024;n++){int v=memory.read(address+n)&255;if(v==0)return out.toByteArray();out.write(v);}throw new IOException("PC1 redraw C source leaves bounded known backing");}
    private byte[] shared()throws IOException{byte[] out=new byte[80+220*104];for(int n=0;n<out.length;n++)out[n]=(byte)memory.read(0x955568+n);return out;}
    private void clip(int l,int t,int r,int b)throws IOException{c.setClip(l,t,r,b);}
    private void actors()throws IOException{
        int mask=g(0x97176c),handle=g(0x9b9be4),y=g(0x9d96c8),oy=g(0xbb8408),x=g(0x9d96c4),ox=g(0xbb8404);c.masked(x+ox,y+oy,handle,mask);
        handle=g(0x971770);y=g(0x9d96c8);oy=g(0xbb8408);ox=g(0xbb8404);x=g(0x9d96c4);c.transparent(x+ox,y+oy,handle);
        mask=g(0x9a0778);handle=g(0x9b9be8);oy=g(0xbb8408);y=g(0x9d96d0);ox=g(0xbb8404);x=g(0x9d96cc);c.masked(x+ox,y+oy,handle,mask);
        handle=g(0x971768);oy=g(0xbb8408);y=g(0x9d96d0);ox=g(0xbb8404);x=g(0x9d96cc);c.transparent(x+ox,y+oy,handle);
    }
    private void record(int p)throws IOException{
        int type=g(p+8);if(type==10)return;
        if((type&64)==0){int x=g(p),y=g(p+4);c.intersectClip(x,y,x+g(p+24),y+g(p+28));type=g(p+8);
            if(type==9){int mask=g(0x9cca98),handle=g(0x9cca90),sy=g(p+20),sx=g(p+16);c.masked(sx,sy,handle,mask);}
            else if((type&128)==0||g(Pc1DrawBatch.OPACITY_ENABLED)==0){int handle=g(Pc1DrawBatch.SLOT_BASE+(type&127)*4),sy=g(p+20),sx=g(p+16);c.transparent(sx,sy,handle);}
            else{int weight=g(Pc1DrawBatch.OPACITY_LEVEL),handle=g(Pc1DrawBatch.SLOT_BASE+(type&127)*4),sy=g(p+20),sx=g(p+16);c.opacity(sx,sy,handle,weight);}
        }else{boolean blend=(type&128)!=0&&(g(Pc1DrawBatch.OPACITY_ENABLED)!=0||g(PARAGRAPH)!=0);int weight=blend?g(Pc1DrawBatch.OPACITY_LEVEL):16,y=g(p+4),x=g(p),bottom=y+g(p+28),right=x+g(p+24);if(blend)c.fillBlend16(x,y,right,bottom,color(type&63),weight);else c.fill(x,y,right,bottom,color(type&63));}
    }
    private void templates(boolean name,int l,int t,int r,int b)throws IOException{
        int countBase=name?0xbb84d0:0xbb84c8,p=(name?0x9d8020:0x9d7d90)+g(PARAGRAPH)*(name?160:320);
        for(int n=0;n<g(countBase+g(PARAGRAPH)*4);n++,p+=32)if(g(p+12)==0||g(CACHED)!=0){clip(l,t,r,b);record(p);}
        if(g(0x43d420)==-1){clip(l,t,r,b);if(name){int x=g(0x43d454),y=g(0x43d458);c.fillBlend16(x,y,x+g(0x43d468)*28,y+g(0x43d46c)*g(0x43d45c),color(0),g(Pc1DrawBatch.OPACITY_LEVEL));}else{int x=g(0x43d438),y=g(0x43d43c);c.fillBlend16(x,y,x+476,y+g(0x43d440)*4,color(0),g(Pc1DrawBatch.OPACITY_LEVEL));}}
    }
    /**41AF50 reads a BYTE mask. Unused bits40/80 do not add consumers. Even
     *mask0 resets the clip when composition pending is zero. */
    public void redraw(int mask,int left,int top,int right,int bottom)throws IOException{
        if(g(Pc1Compositor.COMPOSITION_PENDING)!=0)return;clip(left,top,right,bottom);
        if((mask&1)!=0){int handle=g(Pc1Compositor.BACKGROUND_HANDLE),y=g(Pc1Compositor.BACKGROUND_Y),x=g(Pc1Compositor.BACKGROUND_X);c.copy(x,y,handle);}
        if((mask&2)!=0)io(0x40fa80).effects(1);
        if((mask&4)!=0){clip(left,top,right,bottom);actors();}
        if((mask&8)!=0)io(0x40fa80).effects(0);
        if((mask&16)!=0&&g(Pc1Compositor.VISIBILITY)!=0){templates(false,left,top,right,bottom);templates(true,left,top,right,bottom);io(0x4170c0).glyphJournal(0);}
        if((mask&32)==0)return;
        if(g(0x43d470)>=0&&g(0xbb8548)>0){clip(left,top,right,bottom);c.intersectClip(584,8,776,40);int weight=g(0xbb8548);if(weight<16){if(weight>0){int head=g(0x43d470),handle=g(0x43d428);c.opacity(584-head/16*192,8-head%16*32,handle,weight);}}else{int head=g(0x43d470),handle=g(0x43d428);c.transparent(584-head/16*192,8-head%16*32,handle);}}
        if(g(0xbb8470)!=0||g(WAIT)==0x44){clip(left,top,right,bottom);int p=g(Pc1NativeUi.BUTTON_POINTER);c.intersectClip(g(p+12),g(p+16),g(p+20),g(p+24));int handle=g(Pc1DrawBatch.SLOT_BASE+(g(g(Pc1NativeUi.BUTTON_POINTER)+40)&127)*4),sy=g(g(Pc1NativeUi.BUTTON_POINTER)+52),sx=g(g(Pc1NativeUi.BUTTON_POINTER)+48);c.transparent(sx,sy,handle);}
        int wait=g(WAIT);if(wait==7){if(g(Pc1Compositor.VISIBILITY)!=0){clip(left,top,right,bottom);waitCursor(1);clip(left,top,right,bottom);controls();}}
        else if(wait==10||wait==11||wait==9&&g(Pc1Compositor.VISIBILITY)!=0){clip(left,top,right,bottom);controls();}
    }
    private int buttons()throws IOException{return g(Pc1NativeUi.BUTTON_POINTER);}
    private int count()throws IOException{return g(Pc1NativeUi.BUTTON_COUNT);}
    private boolean eligible(int p)throws IOException{return g(p+4)==0||g(CACHED)!=0;}
    private void buttonClip(int p,boolean replace)throws IOException{if(replace)c.setClip(g(p+12),g(p+16),g(p+20),g(p+24));else c.intersectClip(g(p+12),g(p+16),g(p+20),g(p+24));}
    /**40A1B0 intersects current caller base and never restores Underlay. */
    public void clippedSprite(int p)throws IOException{int x=g(p),y=g(p+4);c.intersectClip(x,y,x+g(p+24),y+g(p+28));int type=g(p+8);if((type&128)!=0&&g(Pc1DrawBatch.OPACITY_ENABLED)!=0){int weight=g(Pc1DrawBatch.OPACITY_LEVEL),handle=g(Pc1DrawBatch.SLOT_BASE+(type&127)*4),sy=g(p+20),sx=g(p+16);c.opacity(sx,sy,handle,weight);}else{int handle=g(Pc1DrawBatch.SLOT_BASE+(type&127)*4),sy=g(p+20),sx=g(p+16);c.transparent(sx,sy,handle);}}
    public void clippedText(int p,int source)throws IOException{io(0x4123a0).expand(source,0x955568);int width=string(0x955568).length*28,x=g(p),y=g(p+4);c.intersectClip(x,y,x+width,y+g(p+28));io(0x418930).inline(g(p)+1,g(p+4)+1,0,shared(),g(p+8));}
    private void component(int p)throws IOException{int source=g(p+32);if(source==0)clippedSprite(p);else clippedText(p,source);}
    private void caption(int x,int y,int source,int style)throws IOException{byte[] encoded=string(source);if(encoded.length>=1024)throw new IOException("PC1 clipped caption native local1024 overflow");byte[] known=new byte[encoded.length+1];for(int n=0;n<encoded.length;n++)known[n]=(byte)((encoded[n]&255)+32);io(0x418930).inline(x,y,0,known,style);}
    /**40A2A0 two native passes. It does not updatebutton visual-state fields
     *or unconditionally invalidate as409D30 does. Range/caption leaves can
     *change the shared state/pointer, and it is reloaded at native boundaries. */
    public void controls()throws IOException{
        int pointer=buttons();
        for(int n=0;n<count();n++){
            int offset=n*560,p=pointer+offset;if(!eligible(p))continue;
            if(g(p)==0){for(int k=0;k<12;k++){int at=pointer+offset+k*40;if(g(at+28)==255&&g(0x43028c)==0&&g(at+64)!=0){clippedSprite(at+32);pointer=buttons();}}}
            else if(g(p)==2){buttonClip(p,false);io(0x4097c0).rangeButton(buttons()+offset,0);pointer=buttons();}
            else{for(int k=0;k<12;k++){int at=pointer+offset+k*40;if(g(at+28)==99&&n!=g(Pc1NativeUi.SELECTED_BUTTON)){component(at+32);pointer=buttons();}}
                if(g(pointer+offset+0x22c)!=0){buttonClip(pointer+offset,false);int fresh=buttons()+offset;caption(g(fresh+32),g(fresh+36),g(fresh+0x22c),g(fresh+40));pointer=buttons();}}
        }
        int selectedState=g(0x94c680)+1;if(g(0x9da4c4)==1)selectedState=2;
        for(int n=0;n<count();n++){
            int offset=n*560,p=pointer+offset;if(!eligible(p)||g(p)==0)continue;
            if(g(p)==2&&n==g(Pc1NativeUi.SELECTED_BUTTON)){buttonClip(p,true);io(0x4097c0).rangeButton(buttons()+offset,-1);pointer=buttons();}
            else if(n==g(Pc1NativeUi.SELECTED_BUTTON)){
                boolean found=false;for(int k=0;k<12;k++){int at=pointer+offset+k*40,flag=g(at+28);if(flag==selectedState||flag==selectedState+2){found=true;component(at+32);pointer=buttons();if(g(pointer+offset+0x22c)!=0){buttonClip(pointer+offset,false);int fresh=buttons();caption(g(fresh+offset+k*40+32),g(fresh+offset+k*40+36),g(fresh+offset+0x22c),g(fresh+(n*14+1+k)*40));pointer=buttons();}}}
                if(!found){int weight=g(Pc1DrawBatch.OPACITY_LEVEL),p0=pointer+offset;c.fillBlend16(g(p0+12),g(p0+16),g(p0+20),g(p0+24),color(0),weight);pointer=buttons();}
            }
        }
    }
    /**4186F0. Mode0 advances phase/restores Underlay; any other mode reuses
     *phase and intersects callerclip. Both actual drawing paths call407000. */
    public void waitCursor(int mode)throws IOException{
        g(0x430284,100);if(g(0xbb8470)!=0||g(0xbb8414)!=0||g(0x43d410)>=0)return;
        int phase=g(0x9cca8c);if(mode==0){phase++;g(0x9cca8c,phase);}int paragraph=g(PARAGRAPH),selector=paragraph+g(0xbb84e4),count=g(0xbb84d8+selector*4);
        if(count==0){int p=0x9d23d0+(phase+selector*20)*32;g(p,800);g(p+4,525);g(p+24,28);g(p+28,28);if(mode==0)io(0x415d50).restoreBody(800,525,828,553,0);else c.intersectClip(800,525,828,553);if((g(0x9cca8c)&2)==0)io(0x409140).rawGlyph28(g(p),g(p+4),new byte[]{(byte)0xa8,(byte)0x8b,0},7);else g(0x9cca8c,0);c.present();return;}
        if(phase>=count){phase=0;g(0x9cca8c,0);}int p=0x9d23d0+(phase+selector*20)*32,x=g(p),y=g(p+4);
        if(paragraph!=0){x+=g(0xbb06e8);y+=g(0xbb06e4);if(mode==0){io(0x415d50).restoreBody(x,y,x+g(p+24),y+g(p+28),0);clip(x,y,x+g(p+24),y+g(p+28));}else c.intersectClip(x,y,x+g(p+24),y+g(p+28));int handle=g(Pc1DrawBatch.SLOT_BASE+(g(p+8)&127)*4),sy=g(p+20)+g(0xbb06e4),sx=g(p+16)+g(0xbb06e8);c.transparent(sx,sy,handle);}
        else{if(mode==0){io(0x415d50).restoreBody(x,y,x+g(p+24),y+g(p+28),0);x=g(p);y=g(p+4);clip(x,y,x+g(p+24),y+g(p+28));}else c.intersectClip(x,y,x+g(p+24),y+g(p+28));int handle=g(Pc1DrawBatch.SLOT_BASE+(g(p+8)&127)*4),sy=g(p+20),sx=g(p+16);c.transparent(sx,sy,handle);}c.present();
    }
}
