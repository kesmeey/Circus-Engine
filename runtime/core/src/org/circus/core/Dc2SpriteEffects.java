package org.circus.core;

import java.io.*;

/** Current originalDC2 raw sprite effects. This borrows the same native frame,
 *PE data, CRT RNG, rectangle banks and persistent compositor as rain. No DCP1
 *DTO, ARGB canvas, generated trig table or independent timer is substituted. */
public final class Dc2SpriteEffects {
    public static final int RECORDS=0x989688,RECORD_BYTES=100,RECORD_COUNT=50,HANDLE=0x45e5d8;
    public static final int WIND_MODE=0x9917ec,WIND=0x9917f0,TOGGLE=0x9917f4;
    public static final int KIND=0xa0ac2c,VARIANT=0xa0ac30,STRENGTH=0xa0ac34,ENABLED=0x473f94;
    public static final int FRAME_TABLE=0x45e5dc,COUNT_TABLE=0x45e75c,COSINE_POINTER=0x432ea4;
    public interface Services {
        int loadImage(int rawResourceAddress,int allocationMode)throws IOException; //401CC0
        void freeImage(int handle)throws IOException; //401130
        void ensureTimer5()throws IOException; //407840
        void releaseEffects()throws IOException; //411650,same rain/rectangle owner
        void recordRectangle(int left,int top,int right,int bottom)throws IOException; //411840
        void redraw(int mask,int left,int top,int right,int bottom)throws IOException; //41D3F0
    }
    /** Only the50 raw records, sprite handle and wind/toggle fields. Native
     *table reads (including signed aliases) borrow the actual same-bus owner. */
    public static final class Memory implements Dc2Rain.State {
        private static final int FORMAT=0x44325331; //D2S1
        private static final int[] BASES={RECORDS,HANDLE,WIND_MODE},LENGTHS={5000,4,12};
        private final byte[][] raw={new byte[5000],{(byte)255,(byte)255,(byte)255,(byte)255},new byte[12]};
        private final Dc2Rain.State external;
        public Memory(Dc2Rain.State external)throws IOException{if(external==null)throw new IOException("DC2 sprite external native owner missing");this.external=external;}
        private boolean own(int address){for(int n=0;n<BASES.length;n++)if(address>=BASES[n]&&address<BASES[n]+LENGTHS[n])return true;return false;}
        public int readByte(int address)throws IOException{for(int n=0;n<BASES.length;n++)if(address>=BASES[n]&&address<BASES[n]+LENGTHS[n])return raw[n][address-BASES[n]]&255;return external.read(address&~3)>>>(address%4*8)&255;}
        public void writeByte(int address,int value)throws IOException{for(int n=0;n<BASES.length;n++)if(address>=BASES[n]&&address<BASES[n]+LENGTHS[n]){raw[n][address-BASES[n]]=(byte)value;return;}int a=address&~3,shift=address%4*8;external.write(a,external.read(a)&~(255<<shift)|(value&255)<<shift);}
        public int read(int address)throws IOException{if(!own(address)&&!own(address+3)&&(address&3)==0)return external.read(address);int value=0;for(int n=0;n<4;n++)value|=readByte(address+n)<<(n*8);return value;}
        public void write(int address,int value)throws IOException{if(!own(address)&&!own(address+3)&&(address&3)==0){external.write(address,value);return;}for(int n=0;n<4;n++)readByte(address+n);for(int n=0;n<4;n++)writeByte(address+n,value>>>(n*8));}
        public void save(DataOutput out)throws IOException{out.writeInt(FORMAT);for(byte[] bytes:raw)out.write(bytes);}
        public static final class Image{private final byte[][] raw;private Image(byte[][] raw){this.raw=raw;}}
        public static Image load(DataInput in)throws IOException{if(in.readInt()!=FORMAT)throw new IOException("DC2 sprite native format");byte[][] raw=new byte[LENGTHS.length][];for(int n=0;n<raw.length;n++){raw[n]=new byte[LENGTHS[n]];in.readFully(raw[n]);}return new Image(raw);}
        public void restore(Image image)throws IOException{if(image==null)throw new IOException("DC2 sprite image missing");for(int n=0;n<raw.length;n++)System.arraycopy(image.raw[n],0,raw[n],0,raw[n].length);}
    }
    private final Dc2Rain.State state;
    private final Dc2Compositor compositor;
    private final Dc2Rain.Random random;
    private final Services services;
    public Dc2SpriteEffects(Dc2Rain.State state,Dc2Compositor compositor,Dc2Rain.Random random,Services services)throws IOException{if(state==null||compositor==null)throw new IOException("DC2 sprite canonical state/compositor missing");this.state=state;this.compositor=compositor;this.random=random;this.services=services;}
    private int g(int address)throws IOException{return state.read(address);}
    private void g(int address,int value)throws IOException{state.write(address,value);}
    private int rand()throws IOException{if(random==null)throw new IOException("DC2 sprite shared CRT RNG missing");return random.next();}
    private Services io(int entry)throws IOException{if(services==null)throw new IOException("DC2 sprite requires native consumer"+Integer.toHexString(entry));return services;}
    public static boolean spriteKind(int kind){return kind==1||kind==3||kind==4||kind==5||kind==6;}
    /** Selected1/3/4/5/6 branches of411670. Kind0/2 and true defaults stay in
     *the enclosing dispatcher; this method cannot silently swallow rain. */
    public void initializeSpriteKind(int kind,int level)throws IOException{
        if(!spriteKind(kind))throw new IOException("DC2 sprite initializer requires kind1/3/4/5/6");
        int old=g(KIND);if(old!=0&&old!=kind)io(0x411650).releaseEffects();g(KIND,kind);g(STRENGTH,level);
        if(g(ENABLED)!=0){initializeForKind(kind,level,kind==3?4:0);io(0x407840).ensureTimer5();}
    }
    /**411CE0 writes VARIANT before loading; even a failed−1 result proceeds
     *to initialize all50 raw records. Existing handle suppresses reload. */
    public void initializeForKind(int kind,int level,int variant)throws IOException{
        g(VARIANT,variant);if(g(HANDLE)==-1){int resource=kind==6?0x45e7bc:kind==5?0x45e7a8:kind==3?0x45e798:kind==1?0x45e788:0x45e778;g(HANDLE,io(0x401cc0).loadImage(resource,1));}initialize(level,variant);
    }
    private static final int[] CLEAR={8,0,4,12,16,20,24,28,32,36,40,44,48,52,56,68,72,76,60,64,80,84,88,92,96};
    /**411D60 count/table and all raw stores preserve signed native addresses.
     *No invented strength bound hides aliases before45E75C. */
    public void initialize(int level,int variant)throws IOException{
        for(int n=0;n<50;n++)for(int offset:CLEAR)g(RECORDS+n*100+offset,0);
        int count=level<5?g(COUNT_TABLE+level*4):50;
        for(int n=0;n<count;n++){
            int p=RECORDS+n*100;g(p+8,1);g(p+20,rand()%800);int y=-32-rand()%300;g(p+24,y);g(p+12,g(p+20));g(p+16,y);
            int row=rand()%6;if(variant==4)row=5;int amplitude;
            if(row>=0&&row<3){g(p,row);g(p+72,0);g(p+36,32);g(p+40,24);amplitude=rand()%24+24;}
            else if(row==3){g(p,3);g(p+72,rand()%2);g(p+36,32);g(p+40,24);amplitude=rand()%16+16;}
            else{g(p,row==4?4:5);g(p+72,1);g(p+36,32);g(p+40,24);amplitude=rand()%8+8;}
            g(p+84,amplitude);g(p+56,variant==4?rand()%4+2:rand()%8+4);g(p+88,rand()%8+4);g(p+60,rand()%8+4);
        }
        if(variant==0){g(WIND_MODE,0);wind(0,0);}else if(variant==1||variant==4){g(WIND_MODE,0);wind(1,2);}else if(variant==2){g(WIND_MODE,0);wind(2,2);}
    }
    public void wind(int mode,int amount)throws IOException{g(WIND,0);g(WIND_MODE,mode);if(mode==1)g(WIND,amount);else if(mode==2)g(WIND,-amount);}
    /**411C90 reads the live table pointer and real signed-index aliases.
     *Preceding.data may contain the live CRX header, not immutable cosine ROM. */
    public int cosine(int degrees,int amplitude,int base)throws IOException{int index=(degrees%360)*4096/360,product=g(g(COSINE_POINTER)+index*4)*amplitude;return (product+(product>>31&4095)>>12)+base;}
    private int frame(int row,int position)throws IOException{return g(FRAME_TABLE+(row*16+position)*4);}
    /**412050 toggle is exactly equality1; arbitrary other raw values update. */
    public void update()throws IOException{
        if(g(TOGGLE)==1){g(TOGGLE,0);return;}g(TOGGLE,1);
        for(int n=0;n<50;n++){
            int p=RECORDS+n*100;if(g(p+8)==0)continue;g(p+20,g(p+20)+g(WIND));int x=cosine(g(p+96),g(p+84),0)+g(p+20),mode=g(WIND_MODE),y=g(p+56)+g(p+16);g(p+96,g(p+96)+g(p+88));g(p+12,x);g(p+16,y);
            if(y>599||mode==1&&x>=800||mode==2&&x+32<0){
                if(mode==1){if(rand()%2==0){g(p+20,rand()%800);g(p+24,-32-rand()%64);}else{g(p+20,-32-rand()%64);g(p+24,rand()%400);}}
                else if(mode==2){if(rand()%2==0){g(p+20,rand()%800);g(p+24,rand()%64-64);}else{g(p+20,rand()%64+800);g(p+24,rand()%400);}}
                else{g(p+20,rand()%800);g(p+24,-(rand()%300));}g(p+12,g(p+20));g(p+16,g(p+24));g(p+96,0);
            }
            int tick=g(p+64)+1;g(p+64,tick);if(g(p+60)<=tick){g(p+64,0);int f=g(p+76)+1;g(p+76,f);if(frame(g(p),f)==-1)g(p+76,0);}
        }
    }
    public void release()throws IOException{for(int n=0;n<50;n++){int p=RECORDS+n*100+8;if(g(p)!=0)g(p,0);}int handle=g(HANDLE);if(handle!=-1)io(0x401130).freeImage(handle);g(HANDLE,-1);}
    public void restoreRectangles()throws IOException{for(int n=0;n<50;n++){int p=RECORDS+n*100;if(g(p+8)!=0){int x=g(p+12),y=g(p+16);io(0x411840).recordRectangle(x,y,x+g(p+36),y+g(p+40));x=g(p+12);y=g(p+16);io(0x41d3f0).redraw(1,x,y,x+g(p+36),y+g(p+40));}}}
    public void draw(int layer)throws IOException{for(int n=0;n<50;n++){int p=RECORDS+n*100;if(g(p+8)!=0&&g(p+72)==layer){int row=g(p),width=g(p+36),x=g(p+12),f=frame(row,g(p+76)),y=g(p+16);compositor.intersectClip(x,y,x+width,y+g(p+40));compositor.opacity(x-f*width,y-row*28,g(HANDLE),g(KIND)==3?12:8);}}}
}
