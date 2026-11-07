package org.circus.core;

import java.io.IOException;

/** Current415D50/416220 rectangle restoration and416C30 window wrapper.
 * Pixels, raw template records and geometry retain their canonical owners.
 * Effects40FA80 and controls416B80 are required independent consumers. */
public final class Pc1Underlay {
    public interface Services {
        void effects(int layer)throws IOException;
        void controls()throws IOException;
    }
    private final Pc1Compositor compositor;
    private final Pc1Compositor.State state;
    private final Services services;
    private static final int PARAGRAPH=0x9da4ac,CACHED=0x9da4a8,HEAD=0x9da4b0;
    private static final int BODY_BASE=0x9d7d90,NAME_BASE=0x9d8020,BODY_COUNT=0xbb84c8,NAME_COUNT=0xbb84d0;
    public Pc1Underlay(Pc1Compositor compositor,Pc1Compositor.State state,Services services)throws IOException{
        if(compositor==null||state==null)throw new IOException("PC1 underlay canonical compositor/state missing");this.compositor=compositor;this.state=state;this.services=services;
    }
    private int read(int address)throws IOException{return state.readInt(address);}
    private void write(int address,int value)throws IOException{state.writeInt(address,value);}
    private boolean admitted(int pointer)throws IOException{return read(pointer+12)==0||read(CACHED)!=0;}
    private void geometry(int pointer,boolean name,int shade)throws IOException{
        int pitch=read(pointer+24);
        if(pitch==0){
            if(name){write(0x43d464,read(pointer));write(0x43d468,read(pointer+4));}
            else{write(0x43d44c,read(pointer));write(0x43d448,read(pointer+4));write(0x43d450,read(pointer+28));}
        }else{
            int base=name?0x43d454:0x43d438;write(base,read(pointer));write(base+4,read(pointer+4));write(base+8,read(pointer+24));write(base+12,read(pointer+28));
            if(!name&&shade==0)write(base+12,read(base+12)&0xffbf);
        }
    }
    private void head()throws IOException{if(read(HEAD)==-1)write(HEAD,read(0x43d43c));}
    private void effect(int layer)throws IOException{if(services==null)throw new IOException("PC1 underlay requires40FA80 effect consumer");services.effects(layer);}
    private void clip(int left,int top,int right,int bottom)throws IOException{compositor.setClip(left,top,right,bottom);}
    private int color(int index)throws IOException{return Pc1GlyphRaster.paletteRgb(read(Pc1DrawBatch.PALETTE+index*4));}
    private void actors()throws IOException{
        int mask=read(Pc1GraphicsResources.ACTOR1_MASK),handle=read(0x9b9be4),y=read(0x9d96c8),offsetY=read(0xbb8408),x=read(0x9d96c4),offsetX=read(0xbb8404);
        compositor.masked(x+offsetX,y+offsetY,handle,mask);
        handle=read(Pc1GraphicsResources.ACTOR1_OVERLAY);y=read(0x9d96c8);offsetY=read(0xbb8408);offsetX=read(0xbb8404);x=read(0x9d96c4);compositor.transparent(x+offsetX,y+offsetY,handle);
        mask=read(Pc1GraphicsResources.ACTOR2_MASK);handle=read(0x9b9be8);offsetY=read(0xbb8408);y=read(0x9d96d0);offsetX=read(0xbb8404);x=read(0x9d96cc);compositor.masked(x+offsetX,y+offsetY,handle,mask);
        handle=read(Pc1GraphicsResources.ACTOR2_OVERLAY);offsetY=read(0xbb8408);y=read(0x9d96d0);offsetX=read(0xbb8404);x=read(0x9d96cc);compositor.transparent(x+offsetX,y+offsetY,handle);
    }
    private void drawRecord(int pointer,boolean name)throws IOException{
        int type=read(pointer+8);
        if(type==10){geometry(pointer,name,name?1:read(0x454cec));return;}
        if((type&64)!=0){
            boolean blend=(type&128)!=0&&(read(Pc1DrawBatch.OPACITY_ENABLED)!=0||read(PARAGRAPH)!=0);
            int weight=blend?read(Pc1DrawBatch.OPACITY_LEVEL):16,y=read(pointer+4),x=read(pointer),bottom=read(pointer+28)+y,right=read(pointer+24)+x;
            if(blend)compositor.fillBlend16(x,y,right,bottom,color(type&63),weight);else compositor.fill(x,y,right,bottom,color(type&63));
        }else{
            int y=read(pointer+4),bottom=read(pointer+28)+y,x=read(pointer),right=read(pointer+24)+x;compositor.intersectClip(x,y,right,bottom);type=read(pointer+8);
            if(type==9){int mask=read(Pc1DrawBatch.PORTRAIT_MASK),handle=read(Pc1DrawBatch.PORTRAIT),sy=read(pointer+20),sx=read(pointer+16);compositor.masked(sx,sy,handle,mask);}
            else if((type&128)==0||read(Pc1DrawBatch.OPACITY_ENABLED)==0){int handle=read(Pc1DrawBatch.SLOT_BASE+(type&127)*4),sy=read(pointer+20),sx=read(pointer+16);compositor.transparent(sx,sy,handle);}
            else{int weight=read(Pc1DrawBatch.OPACITY_LEVEL),handle=read(Pc1DrawBatch.SLOT_BASE+(type&127)*4),sy=read(pointer+20),sx=read(pointer+16);compositor.opacity(sx,sy,handle,weight);}
        }
    }
    public void body(int left,int top,int right,int bottom,int mode)throws IOException{restore(left,top,right,bottom,mode,false);}
    public void name(int left,int top,int right,int bottom,int mode)throws IOException{restore(left,top,right,bottom,mode,true);}
    private void restore(int left,int top,int right,int bottom,int mode,boolean name)throws IOException{
        int base=name?NAME_BASE:BODY_BASE,countBase=name?NAME_COUNT:BODY_COUNT,stride=name?160:320;
        if(mode!=0){
            int paragraph=read(PARAGRAPH),count=read(countBase+paragraph*4),pointer=base+paragraph*stride;left=800;top=600;right=bottom=0;
            int shade=!name&&count>0?read(0x454cec):1;
            for(int n=count;n>0;n--,pointer+=32)if(admitted(pointer)){
                if(read(pointer+8)==10)geometry(pointer,name,shade);
                else{int x=read(pointer),y=read(pointer+4);if(x<left)left=x;if(y<top)top=y;int end=x+read(pointer+24);if(end>right)right=end;end=y+read(pointer+28);if(end>bottom)bottom=end;}
            }
            if(!name)head();if(mode==2)return;
        }
        clip(left,top,right,bottom);int background=read(Pc1Compositor.BACKGROUND_HANDLE),y=read(Pc1Compositor.BACKGROUND_Y),x=read(Pc1Compositor.BACKGROUND_X);compositor.copy(x,y,background);
        if(!name){effect(1);clip(left,top,right,bottom);}
        actors();if(!name){effect(0);clip(left,top,right,bottom);}
        if(read(Pc1Compositor.VISIBILITY)!=0){
            int paragraph=read(PARAGRAPH),pointer=base+paragraph*stride;
            for(int n=0;n<read(countBase+read(PARAGRAPH)*4);n++,pointer+=32)if(admitted(pointer)){clip(left,top,right,bottom);drawRecord(pointer,name);}
            if(read(Pc1DrawBatch.RESOURCE)==-1){
                clip(left,top,right,bottom);
                if(name){int x0=read(0x43d454),y0=read(0x43d458);compositor.fillBlend16(x0,y0,x0+read(0x43d468)*28,y0+read(0x43d46c)*read(0x43d45c),color(0),read(Pc1DrawBatch.OPACITY_LEVEL));}
                else{int x0=read(0x43d438),y0=read(0x43d43c);compositor.fillBlend16(x0,y0,x0+476,y0+read(0x43d440)*4,color(0),read(Pc1DrawBatch.OPACITY_LEVEL));}
            }
            if(!name)head();
        }
        if(!name&&(read(0xbb8470)!=0||read(0x9d8a20)==0x44)){
            clip(left,top,right,bottom);int button=read(Pc1NativeUi.BUTTON_POINTER);compositor.intersectClip(read(button+12),read(button+16),read(button+20),read(button+24));
            int type=read(button+40);compositor.transparent(read(button+48),read(button+52),read(Pc1DrawBatch.SLOT_BASE+(type&127)*4));
        }
    }
    /**416C30 has no optional controls callback or implicit invalidation. */
    public void messageWindow()throws IOException{
        write(Pc1Compositor.VISIBILITY,1);body(0,0,800,600,1);
        if(services==null)throw new IOException("PC1 message window requires416B80 controls consumer");services.controls();
    }
}
