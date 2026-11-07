package org.circus.core;

import java.io.*;

/** Current d474DC2 419330/409900/409BB0 over live addressed glyph bytes and
 *the actual same RGB24 compositor. Font/cache/provider are explicit owners;
 *no Unicode layout, scene metadata or CHS PC1 shadow policy is substituted. */
public final class Dc2GlyphService {
    private static final int FONT_FORMAT=0x44324746; //D2GF,provider-only opaque context
    private final Dc2GlyphMemory memory;
    private final Dc2GlyphCache cache;
    private final Dc2GlyphCache.Provider provider;
    private final Dc2Compositor compositor;
    private boolean busy;
    public Dc2GlyphService(Dc2GlyphMemory memory,Dc2GlyphCache cache,Dc2GlyphCache.Provider provider,Dc2Compositor compositor)throws IOException{if(memory==null||cache==null||!cache.belongsTo(memory)||compositor==null)throw new IOException("DC2 glyph requires one canonical memory/cache and compositor");this.memory=memory;this.cache=cache;this.provider=provider;this.compositor=compositor;}
    private int g(int address)throws IOException{return memory.read(address);}
    private int color(int index)throws IOException{int address=Dc2Compositor.PALETTE+index*4;int b=memory.readByte(address+2),green=memory.readByte(address),r=memory.readByte(address+1);return r<<16|green<<8|b;}
    private void enter()throws IOException{if(busy)throw new IOException("DC2 glyph service reentry");busy=true;memory.begin();}
    private void leave(){memory.end();busy=false;}
    private Dc2GlyphCache.Source source(final int nativeAddress){return offset->memory.readByte(nativeAddress+offset);}
    public static Dc2GlyphCache.Source known(final byte[] bytes,final int offset){return n->{long at=(long)offset+n;if(bytes==null||at<0||at>=bytes.length)throw new IOException("DC2 glyph string crosses unowned backing");return bytes[(int)at]&255;};}
    public static final class Cursor {public final int x,sourceOffset;private Cursor(int x,int sourceOffset){this.x=x;this.sourceOffset=sourceOffset;}}
    private void glyph(int x,int y,int requested,int pointer,int palette)throws IOException{
        Pc1Raster.Clip clip=compositor.clip();int l=Math.max(x,clip.left),t=Math.max(y,clip.top),r=Math.min(x+requested,clip.right),b=Math.min(y+requested,clip.bottom);
        if(b<t||r<l)return;
        //Native407D40 precedes metadata/palette/source accesses, even for an
        //equal-edge rectangle. Preserve the dirty write if a later read fails.
        if(compositor.activeTarget()==0)compositor.touch(l,t,r,b);
        if(compositor.format(compositor.activeTarget())!=0||!compositor.loaded(compositor.activeTarget()))throw new IOException("DC2 glyph destination requires reviewed loaded RGB24 backing");int rgb=r>l?color(palette):0;
        boolean pixels=r>l&&b>t;Dc2GlyphMemory.View view=pixels?memory.knownView(pointer):new Dc2GlyphMemory.View(new byte[0],0);
        compositor.glyph(x,y,requested,view.bytes,view.offset,rgb);
    }
    private Cursor raw(int x,int y,Dc2GlyphCache.Source original,int palette,int size)throws IOException{
        int offset=0;
        while(original.unsignedByte(offset)!=0){
            final int at=offset;Dc2GlyphCache.Source current=n->original.unsignedByte(at+n);NativeGlyphCache.Lookup entry=cache.lookup(current,size,provider);glyph(x,y,size,entry.address,palette);
            //The patched currentPE rereads first after glyph draw, and every
            //first>80 (includingFF) takes2 bytes/fullwidth.
            if(original.unsignedByte(offset)>0x80){offset+=2;x+=size;}else{offset++;x+=size/2;}
        }
        return new Cursor(x,offset);
    }
    public Cursor drawRaw(int x,int y,int nativeSource,int palette,int size)throws IOException{enter();try{return raw(x,y,source(nativeSource),palette,size);}finally{leave();}}
    public Cursor drawRaw(int x,int y,Dc2GlyphCache.Source knownSource,int palette,int size)throws IOException{enter();try{return raw(x,y,knownSource,palette,size);}finally{leave();}}
    private void decoration(int l,int t,int r,int b,int palette,int weight,boolean solid)throws IOException{
        Pc1Raster.Clip clip=compositor.clip();int left=Math.max(l,clip.left),top=Math.max(t,clip.top),right=Math.min(r,clip.right),bottom=Math.min(b,clip.bottom);
        if(compositor.activeTarget()==0)compositor.touch(left,top,right,bottom);
        if(compositor.format(compositor.activeTarget())!=0||!compositor.loaded(compositor.activeTarget()))throw new IOException("DC2 glyph decoration requires reviewed loaded RGB24 backing");int rgb=right>left&&(!solid||bottom>top)?color(palette):0;compositor.decoration(l,t,r,b,rgb,weight);
    }
    /** Native first index/count and all strings borrow the real raw journal.
     *Count is reread at loop boundaries. Both passes can observe cache/GDI
     *writes through aliases; source reads are never frozen as layout DTOs. */
    public void drawJournal(int first)throws IOException{
        enter();try{
            for(int pass=0;pass<2;pass++)for(int n=first,p=Dc2GlyphMemory.JOURNAL+(first<<5);n<g(Dc2GlyphMemory.COUNT);n++,p+=32){
                if((g(p+16)==12)!=(pass==1))continue;
                Pc1Raster.Clip clip=compositor.clip();int right=g(p+8);if(right<=clip.left)continue;int x=g(p);if(x>=clip.right)continue;int bottom=g(p+12);if(bottom<=clip.top)continue;int y=g(p+4);if(y>=clip.bottom)continue;
                if(pass==0){
                    int style=g(p+20);
                    if((style&64)!=0){
                        int palette=style>>>8&63;
                        if((style&0x4000)!=0||g(Dc2GlyphMemory.SHADE)!=0){
                            boolean solid=(style&0x4000)!=0;int end=right;
                            if((g(p+24)&1)!=0){decoration(x,y+3,x+1,bottom-3,palette,solid?16:g(Dc2GlyphMemory.SHADE),solid);decoration(x+1,y+1,x+2,g(p+12)-1,palette,solid?16:g(Dc2GlyphMemory.SHADE),solid);x+=2;}
                            if((g(p+24)&2)!=0){end=right-2;decoration(end,y+1,right-1,g(p+12)-1,palette,solid?16:g(Dc2GlyphMemory.SHADE),solid);decoration(right-1,y+3,right,g(p+12)-3,palette,solid?16:g(Dc2GlyphMemory.SHADE),solid);}
                            decoration(x,y,end,g(p+12),palette,solid?16:g(Dc2GlyphMemory.SHADE),solid);
                        }
                        y+=2;
                    }
                }else if(memory.readByte(p+28)==0x81&&memory.readByte(p+29)==0x45){
                    x-=8;y-=5;
                    int style=g(p+20);if((style&128)!=0)raw(x+1,y+1,source(0x45e9a4),(style&63)==0?9:0,28);raw(x,y,source(0x45e9a4),g(p+20)&63,28);continue;
                }
                int style=g(p+20);if((style&128)!=0)raw(x+1,y+1,source(p+28),(style&63)==0?9:0,g(p+16));raw(x,y,source(p+28),g(p+20)&63,g(p+16));
            }
        }finally{leave();}
    }
    /**Raw memory is serialized ONCE by Dc2GlyphMemory; this is only the
     *independent real GDI context, not another requested/ascent authority. */
    public void saveFontContext(DataOutput out)throws IOException{if(busy||cache.busy()||memory.busy())throw new IOException("DC2 font snapshot inside native callback");if(provider==null)throw new IOException("DC2 font context requires actual provider");byte[] bytes=provider.saveFontContext();if(bytes==null||bytes.length==0||bytes.length>1024*1024)throw new IOException("DC2 font context size");out.writeInt(FONT_FORMAT);out.writeInt(bytes.length);out.write(bytes);}
    public void restoreFontContext(DataInput in)throws IOException{if(busy||cache.busy()||memory.busy())throw new IOException("DC2 font restore inside native callback");if(provider==null||in.readInt()!=FONT_FORMAT)throw new IOException("DC2 font provider/context format");int length=in.readInt();if(length<=0||length>1024*1024)throw new IOException("DC2 font context size");byte[] bytes=new byte[length];in.readFully(bytes);provider.restoreFontContext(bytes);}
}
