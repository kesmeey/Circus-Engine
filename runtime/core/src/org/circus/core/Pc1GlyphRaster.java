package org.circus.core;

import java.io.*;
import java.util.Arrays;

/** Current CHS4042D0 mono2x projection and408F00/4091A0 RGB24 kernel.
 * Font selection/GDI, native glyph caches, cursor advance and dirty union are
 * separate owners. No Unicode conversion or platform font approximation. */
public final class Pc1GlyphRaster {
    private Pc1GlyphRaster(){}
    /** Normal non28 entry has1600 mask bytes; native imposes no size<=40
     * admission. Larger/wrapped requests may reach known neighboring entries. */
    public static final int MAX_CACHE_SIZE=40,MONO_SCRATCH_BYTES=19200;
    /** Native picks22 for EVERY requested size except these four values. */
    public static int selectedFontSize(int requested){return requested==12||requested==28||requested==32||requested==40?requested:22;}
    /**4093F0/4042D0 byte classification, distinct from CHS cache-key logic.
     * FF is a single-byte GDI character although its cache key reads next. */
    public static int characterCode(byte[] known,int offset)throws IOException{
        if(known==null||offset<0||offset>=known.length)throw new IOException("PC1 GDI character reads outside known raw bytes");int first=known[offset]&255;
        if(first>=0x81&&first<=0xfe){if(offset+1>=known.length)throw new IOException("PC1 GDI character requires its actual second raw byte");return first*256+(known[offset+1]&255);}return first;
    }
    /** Unmodified GetGlyphOutlineA GGO_BITMAP metrics and DWORD-aligned mono
     * bytes. Cell increments are retained even though4042D0 does not use them. */
    public static final class Outline {
        public final int ascent,width,height,originX,originY,cellIncX,cellIncY,rowStride;
        public interface MonoRow {int read(int byteOffset)throws IOException;}
        public interface MonoRows {MonoRow row(int row)throws IOException;}
        private final byte[] mono;private final MonoRows rows;
        public Outline(int ascent,int width,int height,int originX,int originY,int cellIncX,int cellIncY,byte[] mono)throws IOException{this(ascent,width,height,originX,originY,cellIncX,cellIncY,mono,null,false);}
        private Outline(int ascent,int width,int height,int originX,int originY,int cellIncX,int cellIncY,byte[] mono,MonoRows rows,boolean consumed)throws IOException{
            long stride=((long)width+31)/32*4,needed=consumed?(height==0||width==0?0:(height-1)*stride+((long)width+7)/8):stride*height;
            if(width<0||height<0||stride>Integer.MAX_VALUE||needed>16*1024*1024L||rows==null&&(mono==null||needed>mono.length))throw new IOException("PC1 GDI outline outside known mono bitmap");
            this.ascent=ascent;this.width=width;this.height=height;this.originX=originX;this.originY=originY;this.cellIncX=cellIncX;this.cellIncY=cellIncY;rowStride=(int)stride;this.mono=mono==null?null:mono.clone();this.rows=rows;
        }
        /** Exact consumed prefix. Do not invent unused end-row padding. */
        public static Outline consumedPrefix(int ascent,int width,int height,int originX,int originY,int cellIncX,int cellIncY,byte[] known)throws IOException{return new Outline(ascent,width,height,originX,originY,cellIncX,cellIncY,known,null,true);}
        /** Bound projection reloads the actual scratch pointer for each row,
         *after native outputclear may have overwritten that pointer itself. */
        public static Outline liveRows(int ascent,int width,int height,int originX,int originY,int cellIncX,int cellIncY,MonoRows rows)throws IOException{if(rows==null)throw new IOException("PC1 live mono row source required");return new Outline(ascent,width,height,originX,originY,cellIncX,cellIncY,null,rows,true);}
        public MonoRow row(final int row)throws IOException{if(row<0||row>=height)throw new IOException("PC1 mono row outside glyph");if(rows!=null)return rows.row(row);return offset->{long at=(long)row*rowStride+offset;if(offset<0||at<0||at>=mono.length)throw new IOException("PC1 mono byte outside known prefix");return mono[(int)at]&255;};}
        /** Explicit export; native projection must use row() rather than
         *freezing this source before its output-clear side effects. */
        public byte[] monoBytes(){try{return monoBytesChecked();}catch(IOException error){throw new UncheckedIOException(error);}}
        public byte[] monoBytesChecked()throws IOException{if(mono!=null)return mono.clone();int size=height==0||width==0?0:(height-1)*rowStride+(width+7)/8;byte[] result=new byte[size];for(int row=0;row<height&&width!=0;row++){MonoRow source=rows.row(row);int count=row==height-1?(width+7)/8:rowStride;for(int offset=0;offset<count;offset++)result[row*rowStride+offset]=(byte)source.read(offset);}return result;}
    }
    public static int squareByteCount(int size)throws IOException{
        int square=size*size;long unsigned=square&0xffffffffL,clear=(square<<3)&0xffffffffL;
        if(clear>MONO_SCRATCH_BYTES)throw new IOException("PC1 glyph scratch clear reaches unowned19200-byte backing");
        if(unsigned>Integer.MAX_VALUE)throw new IOException("PC1 glyph output clear reaches unowned neighboring backing");return (int)unsigned;
    }
    private static void range(int offset,int length,int capacity)throws IOException{if(offset<0||length<0||(long)offset+length>capacity)throw new IOException("PC1 glyph access reaches unowned neighboring backing");}
    /** Narrow independent result. Ink crossing the square is not clipped or
     * invented as zero; use rasterizeInto with an actual larger known cache
     * owner when native neighboring aliases are available. */
    public static byte[] rasterize(int requestedSize,Outline outline)throws IOException{int bytes=squareByteCount(requestedSize);if(bytes>16*1024*1024)throw new IOException("PC1 independent glyph square exceeds owned allocation");byte[] result=new byte[bytes];rasterizeInto(requestedSize,outline,result,0);return result;}
    /** Native memset clears only requestedSize squared bytes, preserving all
     * stale suffix. Each set mono bit increments one byte with8-bit wrap. The
     * x-origin odd parity and signed/2 truncation follow actual x86 separately;
     * this is not a floating-point scaler or a floor-divided image resize. */
    public static void rasterizeInto(int requestedSize,Outline outline,byte[] knownBacking,int destinationOffset)throws IOException{
        int square=squareByteCount(requestedSize);if(outline==null||knownBacking==null)throw new IOException("PC1 glyph projection requires mono and known backing");range(destinationOffset,square,knownBacking.length);Arrays.fill(knownBacking,destinationOffset,destinationOffset+square,(byte)0);
        int xStart=outline.originX/2,parity=outline.originX&1;
        for(int row=0;row<outline.height;row++){
            int y=(outline.ascent-outline.originY+row)/2,base=destinationOffset+y*requestedSize+xStart;Outline.MonoRow source=outline.row(row);
            for(int column=0;column<outline.width;column++)if((source.read(column>>>3)&(0x80>>>(column&7)))!=0){int at=base+((parity+column)>>>1);range(at,1,knownBacking.length);knownBacking[at]++;}
        }
    }
    /** Palette memory is native G,R,B,unused, not a Java RGB DWORD. */
    public static int paletteRgb(int raw){return(raw>>>8&255)<<16|(raw&255)<<8|(raw>>>16&255);}
    private static int blend(int destination,int source,int coverage){
        if(coverage==4)return source;if(coverage==3)return(source>>>2)+(source>>>1)+(destination>>>2);if(coverage==2)return(source>>>1)+(destination>>>1);if(coverage==1)return(source>>>2)+(destination>>>2)+(destination>>>1);return destination;
    }
    public static final class Draw {
        public final boolean touched;
        public final Pc1Raster.Rect rectangle;
        private Draw(boolean touched,int left,int top,int right,int bottom){this.touched=touched;rectangle=new Pc1Raster.Rect(left,top,right,bottom);}
    }
    /** Same pixel kernel for fixed28 and variable-size paths. Return touched
     * says whether native407580 was called, including an equal-edge rectangle;
     * caller unions it only for active target0. All other coverage values do
     * nothing, including malformed values5..255. Coordinates use int32 ADD. */
    public static Draw draw(NativeRgbImage target,Pc1Raster.Clip clip,int x,int y,int requestedSize,byte[] knownCoverage,int sourceOffset,int rgb)throws IOException{
        if(target==null||clip==null||knownCoverage==null)throw new IOException("PC1 glyph draw requires native RGB24, clip and mask backing");
        int right=x+requestedSize,bottom=y+requestedSize,at=sourceOffset,left=x,top=y;
        if(left<clip.left){at+=clip.left-left;left=clip.left;}if(top<clip.top){at+=(clip.top-top)*requestedSize;top=clip.top;}if(right>clip.right)right=clip.right;if(bottom>clip.bottom)bottom=clip.bottom;
        boolean touched=bottom>=top&&right>=left;Draw result=new Draw(touched,left,top,right,bottom);if(!touched||right==left||bottom==top)return result;
        if(left<0||top<0||right>target.width||bottom>target.height)throw new IOException("PC1 glyph clip reaches unowned destination image");
        int width=right-left;
        for(int row=top;row<bottom;row++){
            range(at,width,knownCoverage.length);int pixel=target.offset(left,row);
            for(int col=0;col<width;col++){int coverage=knownCoverage[at+col]&255;for(int channel=0;channel<3;channel++)target.byteAt(pixel+channel,blend(target.byteAt(pixel+channel)&255,rgb>>>(channel*8)&255,coverage));pixel+=3;}
            at+=requestedSize;
        }
        return result;
    }
    /**408D00/408DC0 decoration fill:407580 is called even for inverted
     * rectangles. Weight is a native DWORD, not clamped to0..16; products and
     * addition wrap32 bits before the logical right shift and byte store. */
    public static Draw decoration(NativeRgbImage target,Pc1Raster.Clip clip,int left,int top,int right,int bottom,int rgb,int weight)throws IOException{
        if(target==null||clip==null)throw new IOException("PC1 glyph decoration requires native target/clip");left=Math.max(left,clip.left);top=Math.max(top,clip.top);right=Math.min(right,clip.right);bottom=Math.min(bottom,clip.bottom);Draw result=new Draw(true,left,top,right,bottom);
        if(right<=left||bottom<=top)return result;if(left<0||top<0||right>target.width||bottom>target.height)throw new IOException("PC1 glyph decoration reaches unowned destination");
        for(int row=top;row<bottom;row++)for(int column=left;column<right;column++){int pixel=target.offset(column,row);for(int channel=0;channel<3;channel++){int old=target.byteAt(pixel+channel)&255,source=rgb>>>(channel*8)&255;target.byteAt(pixel+channel,((old*(16-weight)+source*weight)>>>4));}}
        return result;
    }
}
