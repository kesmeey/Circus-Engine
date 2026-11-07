package org.circus.core;

import java.io.*;
import java.util.List;

/** CurrentCHS4093F0/409140/4170C0 with addressed font/cache fields. */
public final class Pc1GlyphService {
    private final Pc1GlyphCache cache;private final Pc1GlyphCache.Provider provider;private boolean busy;
    public interface Sink extends Pc1GlyphCache.Drawer {
        Pc1Raster.Clip clip()throws IOException;
        int rawPalette(int index)throws IOException;
        void decoration(int left,int top,int right,int bottom,int rgb,int weight)throws IOException;
    }
    public Pc1GlyphService(Pc1GlyphCache cache,Pc1GlyphCache.Provider provider)throws IOException{if(cache==null||provider==null)throw new IOException("PC1 glyph service requires native cache and actual font provider");this.cache=cache;this.provider=provider;}
    public Pc1GlyphCache cache(){return cache;}
    private void enter(Sink sink)throws IOException{if(busy||sink==null)throw new IOException("PC1 glyph service reentry or missing native compositor");busy=true;cache.fontMemory().begin();}
    private void leave(){cache.fontMemory().end();busy=false;}
    public static final class Cursor {public final int x,sourceOffset;private Cursor(int x,int source){this.x=x;sourceOffset=source;}}
    private Cursor raw(Sink sink,int x,int y,Pc1GlyphCache.Source source,int initial,int palette,int size)throws IOException{
        int offset=initial;
        while(source.unsignedByte(offset)!=0){
            final int at=offset;Pc1GlyphCache.Source current=n->source.unsignedByte(at+n);Pc1GlyphCache.Entry glyph=cache.lookup(current,size,provider);Pc1Raster.Clip clip=sink.clip();
            int l=Math.max(x,clip.left),r=Math.min(x+size,clip.right),t=Math.max(y,clip.top),b=Math.min(y+size,clip.bottom);
            int rgb=r>l&&b>=t?Pc1GlyphRaster.paletteRgb(sink.rawPalette(palette)):0;glyph.draw(sink,x,y,rgb);
            int first=source.unsignedByte(offset);if(first>=0x81&&first<=0xfe){offset+=2;x+=size;}else{offset++;x+=size/2;}
        }
        return new Cursor(x,offset);
    }
    public Cursor drawRaw(Sink sink,int x,int y,byte[] known,int offset,int palette,int size)throws IOException{enter(sink);try{return raw(sink,x,y,Pc1GlyphCache.known(known,0),offset,palette,size);}finally{leave();}}
    public Cursor drawRaw(Sink sink,int x,int y,Pc1GlyphCache.Source source,int palette,int size)throws IOException{enter(sink);try{return raw(sink,x,y,source,0,palette,size);}finally{leave();}}
    /** Actual source pointer, including mutations during font callbacks. */
    public Cursor drawRaw(Sink sink,int x,int y,Pc1NativeMemory bus,int address,int palette,int size)throws IOException{sameBus(bus);return drawRaw(sink,x,y,n->bus.readAddress(address+n),palette,size);}
    private void sameBus(Pc1NativeMemory bus)throws IOException{if(bus==null||cache.fontMemory().nativeMemory()!=bus)throw new IOException("PC1 native glyphs need one bound font/source bus");}
    private interface Records {
        int record(int n)throws IOException;int g(int at)throws IOException;int b(int at)throws IOException;int total()throws IOException;int shade()throws IOException;Pc1GlyphCache.Source source(int at);
    }
    private void records(Sink sink,Records rows,int first)throws IOException{
        for(int pass=0;pass<2;pass++)for(int n=first;n<rows.total();n++){
            int p=rows.record(n);if((rows.g(p+16)==12)!=(pass==1))continue;
            Pc1Raster.Clip clip=sink.clip();int right=rows.g(p+8);if(right<=clip.left)continue;int x=rows.g(p);if(x>=clip.right)continue;int bottom=rows.g(p+12);if(bottom<=clip.top)continue;int y=rows.g(p+4);if(y>=clip.bottom)continue;
            if(pass==0){
                int style=rows.g(p+20);
                if((style&64)!=0){
                    if((style&0x4000)!=0||rows.shade()!=0){
                        boolean solid=(style&0x4000)!=0;int end=right,rgb=Pc1GlyphRaster.paletteRgb(sink.rawPalette(style>>>8&63));
                        if((rows.g(p+24)&1)!=0){sink.decoration(x,y+3,x+1,bottom-3,rgb,solid?16:rows.shade());sink.decoration(x+1,y+1,x+2,rows.g(p+12)-1,rgb,solid?16:rows.shade());x+=2;}
                        if((rows.g(p+24)&2)!=0){end=right-2;sink.decoration(end,y+1,right-1,rows.g(p+12)-1,rgb,solid?16:rows.shade());sink.decoration(right-1,y+3,right,rows.g(p+12)-3,rgb,solid?16:rows.shade());}
                        sink.decoration(x,y,end,rows.g(p+12),rgb,solid?16:rows.shade());
                    }
                    y+=2;
                }
            }else if(rows.b(p+28)==0x81&&rows.b(p+29)==0x45){
                x-=8;y-=5;Pc1GlyphCache.Source replacement=rows.source(Integer.MIN_VALUE);
                if((rows.g(p+20)&128)!=0)raw(sink,x+1,y+1,replacement,0,0,28);raw(sink,x,y,replacement,0,rows.g(p+20)&63,28);continue;
            }
            if((rows.g(p+20)&128)!=0)raw(sink,x+1,y+1,rows.source(p+28),0,0,rows.g(p+16));raw(sink,x,y,rows.source(p+28),0,rows.g(p+20)&63,rows.g(p+16));
        }
    }
    /** Portable/local known array, fixed supplied count/shading. Production
     * native journal uses the live-bus overload below. */
    public void drawRecords(Sink sink,final byte[] known,final int first,final int total,final int shade)throws IOException{
        enter(sink);try{records(sink,new Records(){
            private int u(int at)throws IOException{if(known==null||at<0||at>=known.length)throw new IOException("PC1 glyph record reads unowned neighboring backing");return known[at]&255;}
            public int record(int n)throws IOException{long p=(long)n*32;if(p<Integer.MIN_VALUE||p>Integer.MAX_VALUE)throw new IOException("PC1 glyph record address outside known backing");return(int)p;}
            public int g(int at)throws IOException{return u(at)|u(at+1)<<8|u(at+2)<<16|u(at+3)<<24;}
            public int b(int at)throws IOException{return u(at);}
            public int total(){return total;}public int shade(){return shade;}
            public Pc1GlyphCache.Source source(int at){return at==Integer.MIN_VALUE?n->cache.fontMemory().readByte(Pc1FontMemory.LITERALS+n):n->{long p=(long)at+n;if(known==null||p<0||p>=known.length)throw new IOException("PC1 glyph reads unowned neighboring raw source");return known[(int)p]&255;};}
        },first);}finally{leave();}
    }
    /** Actual BB06F8 journal, live BB8540 count and454CE4 shading, not an
     *immutable text-layout export. All strings and stale suffix stay on bus. */
    public void drawNativeRecords(Sink sink,final Pc1NativeMemory bus,int first)throws IOException{
        sameBus(bus);enter(sink);try{records(sink,new Records(){
            public int record(int n){return 0xbb06f8+n*32;}
            public int b(int at)throws IOException{return bus.readAddress(at);}
            public int g(int at)throws IOException{return b(at)|b(at+1)<<8|b(at+2)<<16|b(at+3)<<24;}
            public int total()throws IOException{return g(0xbb8540);}public int shade()throws IOException{return g(0x454ce4);}
            public Pc1GlyphCache.Source source(int at){final int address=at==Integer.MIN_VALUE?Pc1FontMemory.LITERALS:at;return n->bus.readAddress(address+n);}
        },first);}finally{leave();}
    }
    public void drawJournal(Sink sink,Pc1TextLayout.Snapshot journal,int first,int total,int shading)throws IOException{if(journal==null)throw new IOException("PC1 glyph service missing raw layout journal");List<Pc1TextLayout.Glyph> glyphs=journal.glyphs();byte[] records=new byte[glyphs.size()*32];int at=0;for(Pc1TextLayout.Glyph glyph:glyphs)for(int value:glyph.nativeDwords())for(int b=0;b<4;b++)records[at++]=(byte)(value>>>(b*8));drawRecords(sink,records,first,total,shading);}
    public void save(DataOutput out)throws IOException{if(busy)throw new IOException("PC1 glyph service snapshot during callback");cache.save(out,provider);}
    public static Pc1GlyphService load(DataInput in,Pc1GlyphCache.Provider provider)throws IOException{return new Pc1GlyphService(Pc1GlyphCache.load(in,provider),provider);}
    public static Pc1GlyphService restoreStaged(Pc1GlyphCache.Image image,Pc1GlyphCache.Provider provider)throws IOException{return new Pc1GlyphService(Pc1GlyphCache.restoreStaged(image,provider),provider);}
    public void validateStagedRestore(Pc1GlyphCache.Image image)throws IOException{if(busy)throw new IOException("PC1 glyph service restore during callback");Pc1GlyphCache restored=Pc1GlyphCache.restoreStaged(image,provider);if(restored.fontMemory()!=cache.fontMemory())throw new IOException("PC1 staged glyph service raw owner differs");}
}
