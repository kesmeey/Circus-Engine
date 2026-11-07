package org.circus.core;

import java.io.*;
import java.util.Arrays;

/** Current PC1 CN411D30 pending raw32-byte record consumer. Canonical frame
 * bytes and compositor pixels are the state; no Dc2Ui projection or new codec.
 * Resource cache/IO and GDI glyph rasterization are explicit required owners. */
public final class Pc1DrawBatch {
    private Pc1DrawBatch(){}
    public static final int SLOT_BASE=0x9553a4,PORTRAIT=0x9cca90,PORTRAIT_MASK=0x9cca98;
    public static final int PORTRAIT_OTHER=0x9cca94,RESOURCE=0x43d420,SPECIAL_SOURCE=0x43d430;
    public static final int OPACITY_ENABLED=0x454ce8,OPACITY_LEVEL=0x454ce0,PALETTE=0x4304f8;
    private static final byte[] SPECIAL_NAME={101,102,102,32,49,49}; // current image43D8DC
    public interface Resources {
        int load(byte[] rawName,int allocationMode,Pc1Compositor compositor)throws IOException;
        /** Must copy the native resource name as well as explicitly invoking
         * compositor.duplicate(handle); neither side effect may be omitted. */
        int duplicate(int handle,Pc1Compositor compositor)throws IOException;
    }
    public interface TextSource {
        /** Known raw C-string at9A077C+offset, including no guessed tail.
         * Payload may omit its final NUL;original byte encoding is preserved. */
        byte[] rawCString(int nativeCodeOffset)throws IOException;
    }
    public interface TextRenderer {
        /**418930 fixes initial font28;argument2 is newline pitch. The renderer
         * owns its nativeglyph cursor/font effects and actual pixel writes. */
        void draw(Pc1Compositor compositor,int x,int y,int newlinePitch,byte[] expanded,int style)throws IOException;
    }
    private static Resources resources(Resources r,int entry)throws IOException{if(r==null)throw new IOException("Unimplemented PC1 draw-batch resource consumer"+Integer.toHexString(entry));return r;}
    private static int color(Pc1Compositor.State state,int index)throws IOException{
        int packed=state.readInt(PALETTE+index*4);return (packed>>>8&255)<<16|(packed&255)<<8|(packed>>>16&255);
    }
    private static int record(Pc1NativeFrame frame,int index,int field)throws IOException{
        long offset=(long)Pc1FrameLayout.PENDING_OFFSET+(long)index*32+field*4;
        if(offset<0||offset+4>Pc1FrameLayout.FRAME_BYTES)throw new IOException("PC1 pending consumer reads unowned canonical-frame byte "+offset);
        return frame.readI32((int)offset);
    }
    public static void bake(Pc1NativeFrame frame,Pc1Compositor compositor,Pc1Compositor.State state,Resources resources,TextSource textSource,Pc1TextExpander.Context textContext,TextRenderer textRenderer)throws IOException{
        if(frame==null||compositor==null||state==null)throw new IOException("PC1 draw batch requires canonical frame/compositor/global owners");
        if(state.readInt(Pc1Compositor.PENDING_BAKED)!=0&&frame.readU8(Pc1FrameLayout.BACKGROUND)!=0){
            compositor.free(state.readInt(Pc1Compositor.BACKGROUND_HANDLE));state.writeInt(Pc1Compositor.BACKGROUND_HANDLE,-1);
            byte[] name=frame.rawCString(Pc1FrameLayout.BACKGROUND);int handle;
            if(Arrays.equals(name,SPECIAL_NAME))handle=resources(resources,0x402000).duplicate(state.readInt(SPECIAL_SOURCE),compositor);
            else handle=resources(resources,0x401cc0).load(name,0,compositor);
            state.writeInt(Pc1Compositor.BACKGROUND_HANDLE,handle);state.writeInt(Pc1Compositor.PENDING_BAKED,0);
        }
        compositor.fullClip();compositor.target(state.readInt(Pc1Compositor.BACKGROUND_HANDLE));
        state.writeInt(SLOT_BASE+4,state.readInt(PORTRAIT));state.writeInt(SLOT_BASE,state.readInt(Pc1Compositor.BACKGROUND_HANDLE));state.writeInt(SLOT_BASE+16,state.readInt(RESOURCE));state.writeInt(SLOT_BASE+8,state.readInt(PORTRAIT_MASK));state.writeInt(SLOT_BASE+12,state.readInt(PORTRAIT_OTHER));
        // Native compares live count after each callback/record. Producer caps
        // at60, but the unchecked consumer can alias later known frame bytes.
        for(int index=0;index<frame.readI32(Pc1FrameLayout.PENDING_COUNT);index++){
            if(record(frame,index,3)!=0&&frame.readI32(Pc1FrameLayout.CACHED_SCENARIO_READ)==0)continue;
            int type=record(frame,index,2);
            if(type!=-1){
                if(type==10){
                    if(textSource==null)throw new IOException("Unimplemented PC1 type10 loaded-code string owner");byte[] encoded=textSource.rawCString(record(frame,index,4));byte[] expanded=Pc1TextExpander.expandDraw(encoded,textContext);compositor.fullClip();
                    if(textRenderer==null)throw new IOException("Unimplemented PC1 native418930 glyph renderer");
                    textRenderer.draw(compositor,record(frame,index,0)-frame.readI32(Pc1FrameLayout.BACKGROUND_X),record(frame,index,1)-frame.readI32(Pc1FrameLayout.BACKGROUND_Y),record(frame,index,6),expanded,record(frame,index,7));
                }else{
                    int bgX=frame.readI32(Pc1FrameLayout.BACKGROUND_X),bgY=frame.readI32(Pc1FrameLayout.BACKGROUND_Y),left=record(frame,index,0)-bgX,top=record(frame,index,1)-bgY,right=left+record(frame,index,6),bottom=top+record(frame,index,7);
                    if((type&64)!=0){compositor.fullClip();int rgb=color(state,type&63);if((type&128)!=0&&state.readInt(OPACITY_ENABLED)!=0)compositor.fillBlend16(left,top,right,bottom,rgb,state.readInt(OPACITY_LEVEL));else compositor.fill(left,top,right,bottom,rgb);}
                    else{compositor.intersectClip(left,top,right,bottom);int x=record(frame,index,4)-bgX,y=record(frame,index,5)-bgY;
                        if(type==9)compositor.masked(x,y,state.readInt(PORTRAIT),state.readInt(PORTRAIT_MASK));
                        else{int handle=state.readInt(SLOT_BASE+(type&127)*4);if((type&128)!=0&&state.readInt(OPACITY_ENABLED)!=0)compositor.opacity(x,y,handle,state.readInt(OPACITY_LEVEL));else compositor.transparent(x,y,handle);}
                    }
                }
            }
            state.writeInt(Pc1Compositor.PENDING_BAKED,1);
        }
        compositor.target(0);state.writeInt(Pc1Compositor.COMPOSITION_PENDING,0);
    }
}
