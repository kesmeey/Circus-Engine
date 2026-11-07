package org.circus.core;

import java.io.IOException;

/** Current CHS418F20/402130 plusopcode44 and1A producers. Modal records,
 * saved button pointer/count/rectangle and imagelease live in their existing
 * canonical owners. No generic confirmation DTO or independent bitmap pool. */
public final class Pc1Modal {
    public interface Services {
        void buttons40AA50(int mode)throws IOException;
        void inline418930(int x,int y,int pitch,int rawAddress,int style)throws IOException;
        void glyphs4093F0(int x,int y,Pc1GlyphCache.Source liveSource,int palette,int size)throws IOException;
        /**GetCursorPos thenScreenToClient onthe real mainHWND. Unknown
         *uninitializedPOINT after failure must throw, never synthesize0,0. */
        Pc1UiRaster.Point cursorClient()throws IOException;
        void hover419BD0(int x,int y)throws IOException;
    }
    private final Pc1NativeMemory bus;private final Pc1Compositor compositor;private final Pc1Capture capture;private final Services services;private int depth;
    public Pc1Modal(Pc1NativeMemory bus,Pc1Compositor compositor,Services services)throws IOException{if(bus==null||compositor==null||services==null)throw new IOException("PC1 modal requires canonical memory/imagepool and native consumers");this.bus=bus;this.compositor=compositor;this.services=services;capture=new Pc1Capture(bus,compositor);}
    public void requireBoundary()throws IOException{if(depth!=0)throw new IOException("PC1 modal snapshot during native locals/callback");}
    private int b(int at)throws IOException{return bus.readAddress(at);}
    private int d(int at)throws IOException{return b(at)|b(at+1)<<8|b(at+2)<<16|b(at+3)<<24;}
    private void d(int at,int value)throws IOException{for(int n=0;n<4;n++)bus.writeAddress(at+n,value>>>(n*8));}
    private int pointer()throws IOException{return d(Pc1NativeUi.BUTTON_POINTER);}
    private Pc1GlyphCache.Source source(int at){return offset->b(at+offset);}
    private static int length(Pc1GlyphCache.Source source)throws IOException{for(int n=0;n<1024*1024;n++)if(source.unsignedByte(n)==0)return n;throw new IOException("PC1 modal strlen exceeds known host scan bound");}
    private void copy(int destination,Pc1GlyphCache.Source source,int count)throws IOException{int n=0;for(;n+4<=count;n+=4){int word=0;for(int k=0;k<4;k++)word|=(source.unsignedByte(n+k)&255)<<(k*8);d(destination+n,word);}for(;n<count;n++)bus.writeAddress(destination+n,source.unsignedByte(n));}
    /**402130 always copies FRONT0; the previous active target is not restored.
     *Allocation precedes poolsearch. Failure leaves its existingtarget/clip. */
    public int captureRect(int left,int top,int right,int bottom)throws IOException{depth++;try{return capture(left,top,right,bottom);}finally{depth--;}}
    private int capture(int left,int top,int right,int bottom)throws IOException{return capture.rect(left,top,right,bottom);}
    /**1A->414EF5. Values use native32-bit wrapping, noextra screenclamp. */
    public void position1a(int first,int second)throws IOException{depth++;try{d(0x9d8010,first*8);d(0x9d8014,second*8);d(0x9d8a20,0x1a);}finally{depth--;}}
    /**44->414ED9. The caller supplies the already-copied native fetchlocal,
     *not a mutable code alias orUnicode text. Fetch owns its own PC stores. */
    public void opcode44(Pc1GlyphCache.Source fetchedLocal)throws IOException{depth++;try{d(0x9d8a20,0x44);openBody(fetchedLocal);}finally{depth--;}}
    public void open(int rawMessage)throws IOException{open(source(rawMessage));}
    public void open(Pc1GlyphCache.Source rawMessage)throws IOException{depth++;try{openBody(rawMessage);}finally{depth--;}}
    private void shift(int index,int field,int delta)throws IOException{int at=pointer()+index+field;d(at,d(at)+delta);}
    private void openBody(Pc1GlyphCache.Source rawMessage)throws IOException{
        if(rawMessage==null)throw new IOException("PC1 modal native message source missing");
        int oldCount=d(Pc1NativeUi.BUTTON_COUNT),oldPointer=pointer(),x=d(0x9d8010);d(0x9d8018,oldCount);int count=d(0xbb8478),originalTop=d(0x9b8e30),originalLeft=d(0x9b8e2c),y=d(0x9d8014);int dy=y-originalTop;d(0x971460,oldPointer);int dx=x-originalLeft;d(Pc1NativeUi.BUTTON_POINTER,0x9b8e20);d(Pc1NativeUi.BUTTON_COUNT,count);d(0x9b8e20,0);
        for(int index=0,offset=0;index<d(Pc1NativeUi.BUTTON_COUNT);index++,offset+=560){shift(offset,12,dx);shift(offset,16,dy);shift(offset,20,dx);shift(offset,24,dy);for(int component=0;component<12;component++){int at=offset+component*40;shift(at,0x20,dx);shift(at,0x24,dy);shift(at,0x30,dx);shift(at,0x34,dy);}d(pointer()+offset+8,0);}
        int p=pointer(),bottom=d(p+24),right=d(p+20),top=d(p+16),left=d(p+12);d(0x9ccba0,capture(left,top,right,bottom));services.buttons40AA50(0);
        p=pointer();bottom=d(p+24);right=d(p+20);top=d(p+16);left=d(p+12);compositor.setClip(left,top,right,bottom);
        p=pointer();int type=d(p+0x28)&127,handle=d(Pc1DrawBatch.SLOT_BASE+type*4),sourceY=d(p+0x34),sourceX=d(p+0x30);compositor.transparent(sourceX,sourceY,handle);
        int size=length(rawMessage);
        if(size<=20){Pc1GlyphCache.Source prefix=source(0x43da04);copy(0x9d8564,prefix,length(prefix)+1);int bytes=length(rawMessage)+1,end=0x9d8564+length(source(0x9d8564));copy(end,rawMessage,bytes);p=pointer();int style=d(p+0x50),textY=d(p+0x4c),textX=d(p+0x48);services.inline418930(textX,textY,0,0x9d8564,style);}
        else{
            //Native local buffer starts40B before its returnaddress. It copies
            //the entire string before truncatingbyte20. Unknown callerABI is
            //a precise boundary, not an invented safe truncation of thatcopy.
            final byte[] local=new byte[40];final boolean[] known=new boolean[40];int countBytes=length(rawMessage)+1,n=0;
            for(;n+4<=countBytes;n+=4){int word=0;for(int k=0;k<4;k++)word|=(rawMessage.unsignedByte(n+k)&255)<<(k*8);for(int k=0;k<4;k++){if(n+k>=local.length)throw new IOException("PC1 modal long message overwrites unmodeled caller ABI");local[n+k]=(byte)(word>>>(k*8));known[n+k]=true;}}
            for(;n<countBytes;n++){if(n>=local.length)throw new IOException("PC1 modal long message overwrites unmodeled caller ABI");local[n]=(byte)rawMessage.unsignedByte(n);known[n]=true;}local[20]=0;known[20]=true;
            Pc1GlyphCache.Source first=at->{if(at<0||at>=local.length||!known[at])throw new IOException("PC1 modal reads unknown native local suffix");return local[at]&255;};p=pointer();int textY=d(p+0x4c),textX=d(p+0x48);services.glyphs4093F0(textX,textY,first,7,22);p=pointer();textY=d(p+0x4c)+24;textX=d(p+0x48);services.glyphs4093F0(textX,textY,at->rawMessage.unsignedByte(at+20),7,22);
        }
        Pc1UiRaster.Point point=services.cursorClient();if(point==null)throw new IOException("PC1 modal cursor POINT is unknown");services.hover419BD0(point.x,point.y);compositor.present();
    }
}
