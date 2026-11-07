package org.circus.core;

import java.io.IOException;
import java.io.ByteArrayOutputStream;

/** Current CHS418930/4176F0 direct byte-string renderer. Unlike417410,
 * this draws immediately; it neither creates glyph records nor dictionary
 * regions. All shared cursor/ruby/font globals remain external raw owners. */
public final class Pc1InlineText {
    public static final int X=0xbb06e8,Y=0xbb06e4,FONT=0x971764,LENGTH=0xbb83f8,
        BODY_X=0x43d438,SHADING=0x454ce4,RUBY_PHASE=0xbb84e8,RUBY_COUNT=0x9cfc6c,
        BASE_COUNT=0x9d2b50,RUBY_CURSOR=0xbb854c,RUBY_ORIGIN=0x9d3ef8,RUBY_GAP=0x9d3af4,
        RUBY_BASE=0x9d8160,STOP_WORD=0x43d9f0,SPLIT_WORD=0x43d9f4;
    /** Required addressed canonical byte bus. Unknown neighboring storage
     * must throw, including beyond the known ruby array; never supply0. */
    public interface Memory {int read(int nativeAddress)throws IOException;void write(int nativeAddress,int value)throws IOException;}
    public interface Services {
        /** Actual4093F0/409140 service, including shared cache/GDI context.
         * knownRaw is the native local two-byte string plus its ONE NUL;
         * both bytes are preserved even when the first is already0. */
        void glyphs(int x,int y,byte[] knownRaw,int palette,int size)throws IOException;
        /**Addressed source for PE literals; production resolves it during
         *the raw glyph wrapper rather than freezing a replacement constant.*/
        default void glyphsSource(int x,int y,Pc1GlyphCache.Source source,int palette,int size)throws IOException{ByteArrayOutputStream known=new ByteArrayOutputStream();for(int n=0;n<1024*1024;n++){int value=source.unsignedByte(n)&255;known.write(value);if(value==0){glyphs(x,y,known.toByteArray(),palette,size);return;}}throw new IOException("PC1 inline glyph literal leaves known source");}
        /**408D00 (weight16) or408DC0. Palette is the native unconverted
         * index; resolve it from the current compositor palette at draw. */
        void decoration(int left,int top,int right,int bottom,int palette,int weight)throws IOException;
    }
    private static int at(byte[] raw,int p)throws IOException{if(raw==null||p<0||p>=raw.length)throw new IOException("PC1 inline text reads beyond known raw backing at"+p);return raw[p]&255;}
    private static int at(Pc1GlyphCache.Source raw,int p)throws IOException{if(raw==null)throw new IOException("PC1 inline text source missing");return raw.unsignedByte(p)&255;}
    private static int word(byte[] raw,int p)throws IOException{return at(raw,p)|at(raw,p+1)<<8;}
    private static int word(Pc1GlyphCache.Source raw,int p)throws IOException{return at(raw,p)|at(raw,p+1)<<8;}
    public static int readI32(Memory m,int address)throws IOException{return(m.read(address)&255)|(m.read(address+1)&255)<<8|(m.read(address+2)&255)<<16|m.read(address+3)<<24;}
    public static void writeI32(Memory m,int address,int value)throws IOException{for(int n=0;n<4;n++)m.write(address+n,value>>>(n*8)&255);}
    private static int readWord(Memory m,int address)throws IOException{return(m.read(address)&255)|(m.read(address+1)&255)<<8;}
    private static int g(Memory m,int address)throws IOException{return readI32(m,address);}
    private static void g(Memory m,int address,int value)throws IOException{writeI32(m,address,value);}
    private static int strlen(byte[] raw,int p)throws IOException{int begin=p;while(at(raw,p)!=0)p++;return p-begin;}
    private static int strlen(Pc1GlyphCache.Source raw,int p)throws IOException{int begin=p;while(at(raw,p)!=0)p++;return p-begin;}
    private static boolean wide(int b){return b>0x80&&b<0xff;}
    private static int divide(int a,int b)throws IOException{if(b==0||a==Integer.MIN_VALUE&&b==-1)throw new IOException("PC1 inline native signed division trap");return a/b;}
    private static void require(Memory m,Services s)throws IOException{if(m==null||s==null)throw new IOException("PC1 inline text requires addressed globals and actual draw services");}
    /** Complete418930. knownRaw is the exact known native backing, not
     * logical text: do not append terminators, truncate suffix or decode it.
     * The initial Y/X/font writes happen BEFORE strlen can fail. */
    public static void draw(Memory m,Services s,int x,int y,int newlinePitch,byte[] knownRaw,int style)throws IOException{
        drawSource(m,s,x,y,newlinePitch,n->at(knownRaw,n),style);
    }
    /**The addressed source remains live across each GDI/decorating callback.*/
    public static void drawSource(Memory m,Services s,int x,int y,int newlinePitch,Pc1GlyphCache.Source knownRaw,int style)throws IOException{
        require(m,s);g(m,Y,y);g(m,X,x);g(m,FONT,28);g(m,LENGTH,strlen(knownRaw,0));int cursor=0;
        while(at(knownRaw,cursor)!=0){if(at(knownRaw,cursor)=='\n'){g(m,X,x);g(m,Y,g(m,Y)+newlinePitch);cursor++;}else cursor=stepSource(m,s,knownRaw,cursor,style);}
    }
    /**416CB0. Count fields are committed only AFTER both scans, while each
     * written ruby byte becomes visible immediately. Failed scans preserve
     * stale suffix and the old count/phase/cursor fields. */
    private static int splitRuby(Memory m,Pc1GlyphCache.Source raw,int p)throws IOException{
        int ruby=0,base=0,out=RUBY_BASE;
        while(at(raw,p)!=0){int w=word(raw,p);if(w==readWord(m,SPLIT_WORD)){p+=2;break;}if(w==readWord(m,STOP_WORD))break;ruby++;int b=at(raw,p);if(wide(b)){m.write(out++,b);p++;}b=at(raw,p);if(b!=0){m.write(out++,b);p++;}}
        m.write(out,0);
        while(at(raw,p)!=0&&word(raw,p)!=readWord(m,STOP_WORD)){base++;if(wide(at(raw,p)))p++;if(at(raw,p)!=0)p++;}
        g(m,RUBY_COUNT,ruby);g(m,BASE_COUNT,base);if(ruby!=0&&base!=0){g(m,RUBY_PHASE,1);g(m,RUBY_CURSOR,0);return ruby*2+2;}return 0;
    }
    private static void rect(Memory m,Services s,boolean solid,int l,int t,int r,int b,int palette)throws IOException{s.decoration(l,t,r,b,palette,solid?16:g(m,SHADING));}
    /** Complete4176F0 invocation. It may consume a size prefix followed by
     * a NUL and still draw/advance. Returned cursor is always native bytes.
     * Calls have no message pump; snapshots belong between whole invocations. */
    public static int step(Memory m,Services s,byte[] raw,int cursor,int style)throws IOException{
        return stepSource(m,s,n->at(raw,n),cursor,style);
    }
    public static int stepSource(Memory m,Services s,Pc1GlyphCache.Source raw,int cursor,int style)throws IOException{
        require(m,s);int p=cursor;
        if(at(raw,p)=='@'){int code=at(raw,p+1);if(code=='s')g(m,FONT,22);else if(code=='n')g(m,FONT,28);else if(code=='m')g(m,FONT,32);else if(code=='l')g(m,FONT,40);p+=2;}
        int first=at(raw,p),second=at(raw,p+1),y=g(m,Y);byte[] local={(byte)first,(byte)second,0};
        if(first==0x81&&second=='o'){
            int next=p+2+splitRuby(m,raw,p+2),base=g(m,BASE_COUNT),ruby=g(m,RUBY_COUNT),gap=base*28-ruby*12;g(m,RUBY_ORIGIN,g(m,X));g(m,RUBY_GAP,gap);
            if(gap<0){g(m,RUBY_GAP,0);g(m,RUBY_ORIGIN,g(m,X)-(ruby*12-base*28)/2);}return next;
        }
        if(first==0x81&&second=='p'){g(m,RUBY_PHASE,2);p+=2;}
        else{
            int x=g(m,X);
            if(x==g(m,BODY_X)&&first==0x81&&second==0x40&&(at(raw,p+2)==0||at(raw,p+2)=='\n'))g(m,X,x+28);
            else{
                if((style&0x40)!=0){
                    int width=strlen(local,0)*g(m,FONT)>>>1,palette=style>>8&63;boolean solid=(style&0x4000)!=0;
                    if(solid||g(m,SHADING)!=0){
                        rect(m,s,solid,x,y-2,x+width,y+g(m,FONT)+2,palette);x=g(m,X);
                        if(x==g(m,BODY_X)){
                            int gy=g(m,Y);rect(m,s,solid,x-1,gy-1,x,gy+g(m,FONT)+1,palette);
                            gy=g(m,Y);int gx=g(m,X);rect(m,s,solid,gx-2,gy+1,gx-1,gy+g(m,FONT)-1,palette);x=g(m,X);
                        }
                        if(p+2>=g(m,LENGTH)||at(raw,p+2)=='\n'||at(raw,p+2)==0){
                            int gy=g(m,Y);rect(m,s,solid,x+width,gy-1,x+width+1,gy+g(m,FONT)+1,palette);
                            gy=g(m,Y);int gx=g(m,X);rect(m,s,solid,gx+width+1,gy+1,gx+width+2,gy+g(m,FONT)-1,palette);x=g(m,X);
                        }
                    }
                }
                if((style&0x80)!=0){s.glyphs(x+1,y+1,local,0,g(m,FONT));x=g(m,X);}
                s.glyphs(x,y,local,style&63,g(m,FONT));g(m,X,g(m,X)+g(m,FONT));y=g(m,Y);
            }
            p+=2;if(g(m,RUBY_PHASE)==0)return p;
        }
        int count=g(m,RUBY_COUNT),index=g(m,RUBY_CURSOR),x=g(m,X);
        while(index<count*2){
            int origin=g(m,RUBY_ORIGIN),gap=g(m,RUBY_GAP),rx=(index*12)/2+origin;
            if(gap>0)rx=origin+divide((index+1)*gap,count)/2+(index*12)/2;
            if(g(m,RUBY_PHASE)==1&&rx>=x)return p;
            first=m.read(RUBY_BASE+index)&255;second=m.read(RUBY_BASE+index+1)&255;local=new byte[]{(byte)first,(byte)second,0};
            if(first==0x81&&second==0x45){
                rx-=(g(m,FONT)-12)/2;Pc1GlyphCache.Source replacement=offset->m.read(0x43d9fc+offset)&255;
                if((style&0x80)!=0){s.glyphsSource(rx+1,y-15,replacement,0,28);y=g(m,Y);}
                s.glyphsSource(rx,y-16,replacement,style&63,28);
            }else{
                if((style&0x80)!=0){s.glyphs(rx+1,y-10,local,0,12);y=g(m,Y);}
                s.glyphs(rx,y-11,local,style&63,12);
            }
            index=g(m,RUBY_CURSOR)+2;g(m,RUBY_CURSOR,index);count=g(m,RUBY_COUNT);if(index<count*2){y=g(m,Y);x=g(m,X);}
        }
        if(g(m,RUBY_PHASE)==2)g(m,RUBY_PHASE,0);return p;
    }
    private Pc1InlineText(){}
}
