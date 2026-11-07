package org.circus.core;

import java.io.*;
import java.util.List;

/** Current originalDC2 graphics operations with independently verified DC2
 *addresses/wire. Twenty selected graphics instruction spans match the PC1
 *math owner after explicit symbol relocation. Its PC1 higher-level services
 *are never used. This is backing storage, not synchronous Windows display. */
public final class Dc2Compositor {
    public Pc1GlyphRaster.Draw glyph(int x,int y,int size,byte[] coverage,int offset,int rgb)throws IOException{return common.glyph(x,y,size,coverage,offset,rgb);}
    public Pc1GlyphRaster.Draw decoration(int l,int t,int r,int b,int rgb,int weight)throws IOException{return common.decoration(l,t,r,b,rgb,weight);}
    private static final int FORMAT=0x44324f31; //D2O1, nested proven common image/clip storage
    public static final int IMAGE_BASE=0x96c380,IMAGE_STRIDE=128,IMAGES=100,ACTIVE=0x96f580;
    public static final int DIRTY=0x451710,PALETTE=0x451720;
    public static final int CLIP_LEFT=0xbf80e4,CLIP_TOP=0xbf80ec,CLIP_RIGHT=0xbf80e0,CLIP_BOTTOM=0xbf80e8;
    public static final int BASE_LEFT=0xbf80fc,BASE_TOP=0xbf80f4,BASE_RIGHT=0xbf80f8,BASE_BOTTOM=0xbf80f0;
    private final Pc1Compositor common;
    /** Explicitly known pixels; never invent native401050 heap contents. */
    public Dc2Compositor(NativeRgbImage knownFront)throws IOException{common=new Pc1Compositor(knownFront);}
    private Dc2Compositor(Pc1Compositor common){this.common=common;}
    /** External reviewed resource producer imports its actual chosen handle.
     *These are not claims about native allocation/cache/name policy. */
    public void installRgb(int handle,NativeRgbImage pixels)throws IOException{common.installRgb(handle,pixels);}
    public void installAlpha(int handle,NativeInverseAlphaImage pixels)throws IOException{common.installAlpha(handle,pixels);}
    /**401130's proved pixel/pool release. Raw resident-name cache bytes and
     *actual heap/GDI handles belong to the required resource/backend owner. */
    public void releasePixels(int handle){common.free(handle);}
    public boolean loaded(int handle)throws IOException{return common.loaded(handle);}
    public int width(int handle)throws IOException{return common.width(handle);}
    public int height(int handle)throws IOException{return common.height(handle);}
    public int stride(int handle)throws IOException{return common.stride(handle);}
    public int format(int handle)throws IOException{return common.format(handle);}
    public NativeRgbImage rgb(int handle)throws IOException{return common.rgb(handle);}
    public NativeInverseAlphaImage alpha(int handle)throws IOException{return common.alpha(handle);}
    public NativeRgbImage front()throws IOException{return common.front();}
    public int activeTarget(){return common.activeTarget();}
    /**409C40:−1 keeps target,unloaded reviewed slot selects image0. */
    public void target(int handle)throws IOException{common.target(handle);}
    public Pc1Raster.Clip clip(){return common.clip();}
    public Pc1Raster.Clip baseClip(){return common.baseClip();}
    public void fullClip()throws IOException{common.fullClip();} //407E70
    public void setClip(int l,int t,int r,int b)throws IOException{common.setClip(l,t,r,b);} //407EC0
    public void intersectClip(int l,int t,int r,int b){common.intersectClip(l,t,r,b);} //407F40
    /**407FA0 changes current clip only on a nonempty base intersection. */
    public boolean intersectNonempty(int l,int t,int r,int b){Pc1Raster.Clip base=baseClip();int left=Math.max(base.left,l),top=Math.max(base.top,t),right=Math.min(base.right,r),bottom=Math.min(base.bottom,b);if(left>=right||top>=bottom)return false;common.intersectClip(l,t,r,b);return true;}
    public void resetDirty()throws IOException{common.resetDirty();} //407E40
    public Pc1Raster.Rect rawDirty(){return common.rawDirty();}
    public void touch(int l,int t,int r,int b){common.touch(l,t,r,b);} //407D40
    public Pc1Raster.Rect dirtyForPresent()throws IOException{return common.dirtyForPresent();} //407DA0
    public Pc1Compositor.Invalidation present()throws IOException{return common.present();} //4077C0
    public List<Pc1Compositor.Invalidation> invalidations(){return common.invalidations();}
    public List<Pc1Compositor.Invalidation> drainInvalidations(){return common.drainInvalidations();}
    public long invalidationSerial(){return common.invalidationSerial();}
    public void bindInvalidation(Pc1Compositor.InvalidationSink sink)throws IOException{common.bindInvalidation(sink);}
    public Pc1Raster.Rect copy(int x,int y,int handle)throws IOException{return common.copy(x,y,handle);} //408040
    public Pc1Raster.Rect feedback(int x,int y,int handle,int ratio)throws IOException{return common.feedback(x,y,handle,ratio);} //408160
    public Pc1Raster.Rect transparent(int x,int y,int handle)throws IOException{return common.transparent(x,y,handle);} //4086A0/408450
    public Pc1Raster.Rect opacity(int x,int y,int handle,int ratio)throws IOException{return common.opacity(x,y,handle,ratio);} //408B80/4089A0
    public Pc1Raster.Rect masked(int x,int y,int handle,int mask)throws IOException{return common.masked(x,y,handle,mask);} //408D60
    public Pc1Raster.Rect scale(int handle,int ratio)throws IOException{return common.scale(handle,ratio);} //409040
    /**rgb is explicitly resolved from DC2's native G/R/B palette owner. */
    public Pc1Raster.Rect fill(int l,int t,int r,int b,int rgb)throws IOException{return common.fill(l,t,r,b,rgb);} //4094C0
    public Pc1Raster.Rect fillBlend16(int l,int t,int r,int b,int rgb,int ratio)throws IOException{return common.fillBlend16(l,t,r,b,rgb,ratio);} //409580
    private int aligned(int address)throws IOException{
        if(address==ACTIVE)return activeTarget();Pc1Raster.Rect d=rawDirty();if(address>=DIRTY&&address<DIRTY+16)return address==DIRTY?d.left:address==DIRTY+4?d.top:address==DIRTY+8?d.right:d.bottom;
        Pc1Raster.Clip c=clip(),base=baseClip();switch(address){case CLIP_LEFT:return c.left;case CLIP_TOP:return c.top;case CLIP_RIGHT:return c.right;case CLIP_BOTTOM:return c.bottom;case BASE_LEFT:return base.left;case BASE_TOP:return base.top;case BASE_RIGHT:return base.right;case BASE_BOTTOM:return base.bottom;default:break;}
        long offset=(address&0xffffffffL)-IMAGE_BASE;if(offset>=0&&offset<IMAGES*IMAGE_STRIDE){int handle=(int)offset/128,field=(int)offset%128;if(field==8)return stride(handle);if(field==12)return width(handle);if(field==16)return height(handle);if(field==20)return format(handle);}
        throw new IOException("DC2 compositor read reaches unowned native heap/name/neighbor at0x"+Integer.toHexString(address));
    }
    /**Byte-exact metadata/clip/dirty views;real heap-pointer DWORDs are never
     *fabricated as bools or Java object IDs. Adjacent unaligned reads preserve
     *the same bytes. Direct raw writes need an independently ported producer. */
    public int readNativeByte(int address)throws IOException{return aligned(address&~3)>>>(address%4*8)&255;}
    public int readNativeInt(int address)throws IOException{int value=0;for(int n=0;n<4;n++)value|=readNativeByte(address+n)<<(n*8);return value;}
    public void writeNativeByte(int address,int value)throws IOException{throw new IOException("Unimplemented DC2 direct compositor metadata/clip alias write at0x"+Integer.toHexString(address));}
    public void save(DataOutput out)throws IOException{out.writeInt(FORMAT);common.save(out);}
    public static Dc2Compositor load(DataInput in)throws IOException{if(in.readInt()!=FORMAT)throw new IOException("DC2 compositor format");return new Dc2Compositor(Pc1Compositor.load(in));}
}
