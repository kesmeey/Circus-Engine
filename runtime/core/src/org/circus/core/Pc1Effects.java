package org.circus.core;

import java.io.*;

/** Current CHS PC1 effect state and integer/raster consumers. Timer5 dispatch,
 *Windows painting and41AF50's other retained layers remain explicit services.
 *Calling draw does not advance the particle simulation. */
public final class Pc1Effects {
    public static final int PARTICLES=0x9692d0,PARTICLE_BYTES=100,PARTICLE_COUNT=50;
    public static final int RAIN_LEVEL=0x96ff84,RECTANGLES=0x96ff88,RAIN=0x970f28;
    public static final int RECT_BANK=0x971428,PREVIOUS_COUNT=0x97142c,RECT_COUNT=0x971430;
    public static final int WIND_MODE=0x971434,WIND=0x971438,TOGGLE=0x97143c;
    public static final int SPRITE_HANDLE=0x43cecc,RAIN_HANDLE=0x43d068;
    public static final int KIND=0x9da4b4,VARIANT=0x9da4b8,STRENGTH=0x9da4bc,ENABLED=0x454cf4,PENDING=0x9b9f18,BUSY=0xbb8434;
    public interface Random {int next()throws IOException;}
    public interface Services {
        int loadImage(int nativeResourceAddress,int allocationMode)throws IOException;
        void freeImage(int handle)throws IOException;
        void ensureTimer5()throws IOException; //407080, SetTimer only if existing94C67C==0
        void redraw(int mask,int left,int top,int right,int bottom)throws IOException; //41AF50
        void beginNativePaint()throws IOException; //GetDC,40F810 only
        void paintRectangle(int left,int top,int right,int bottom)throws IOException; //406E40
        void endNativePaint()throws IOException; //ReleaseDC
    }
    /** Owns only reviewed effect BSS/PE handles. Frame/settings and the shared
     *CRT seed keep their existing owners. The saved bytes include stale tails. */
    public static final class Memory implements Pc1Compositor.State {
        private static final int FORMAT=0x50315631;
        private static final int[][] RANGES={{PARTICLES,5000},{RAIN_LEVEL,4+4000+1280+24},{SPRITE_HANDLE,4},{RAIN_HANDLE,4}};
        private final Pc1NativeMemory memory;
        private final byte[][] raw=new byte[RANGES.length][];
        public Memory(Pc1NativeMemory memory)throws IOException {
            if(memory==null)throw new IOException("PC1 effects canonical memory missing");this.memory=memory;
            for(int n=0;n<RANGES.length;n++){final byte[] bytes=raw[n]=new byte[RANGES[n][1]];if(n>=2)java.util.Arrays.fill(bytes,(byte)255);memory.bind(RANGES[n][0],bytes.length,new Pc1NativeMemory.Region(){public int read(int at){return bytes[at]&255;}public void write(int at,int value){bytes[at]=(byte)value;}});}
        }
        public int readInt(int address)throws IOException {int value=0;for(int n=0;n<4;n++)value|=memory.readAddress(address+n)<<(n*8);return value;}
        public void writeInt(int address,int value)throws IOException {for(int n=0;n<4;n++)memory.readAddress(address+n);for(int n=0;n<4;n++)memory.writeAddress(address+n,value>>>(n*8));}
        public void writeByte(int address,int value)throws IOException {memory.writeAddress(address,value&255);}
        public void save(DataOutput out)throws IOException {out.writeInt(FORMAT);for(byte[] bytes:raw)out.write(bytes);}
        public static final class Image {private final byte[][] raw;private Image(byte[][] raw){this.raw=raw;}}
        public static Image load(DataInput in)throws IOException {if(in.readInt()!=FORMAT)throw new IOException("PC1 effects format");byte[][] bytes=new byte[RANGES.length][];for(int n=0;n<bytes.length;n++){bytes[n]=new byte[RANGES[n][1]];in.readFully(bytes[n]);}return new Image(bytes);}
        public void restore(Image image)throws IOException {if(image==null)throw new IOException("PC1 effects image missing");for(int n=0;n<raw.length;n++)System.arraycopy(image.raw[n],0,raw[n],0,raw[n].length);}
    }
    private final Pc1Compositor.State state;
    private final Pc1Compositor compositor;
    private final Random random;
    private final Services services;
    public Pc1Effects(Pc1Compositor.State state,Pc1Compositor compositor,Random random,Services services)throws IOException {
        if(state==null||compositor==null)throw new IOException("PC1 effects state/compositor missing");this.state=state;this.compositor=compositor;this.random=random;this.services=services;
    }
    private int get(int a)throws IOException {return state.readInt(a);}
    private void set(int a,int v)throws IOException {state.writeInt(a,v);}
    private Services io()throws IOException {if(services==null)throw new IOException("PC1 effects required resource/retained redraw/backend missing");return services;}
    private int rand()throws IOException {if(random==null)throw new IOException("PC1 effects shared CRT RNG missing");return random.next();}
    /**40F560: unknown kinds return without changing kind/strength. Native2B's
     *FF/busy setter is a distinct script producer, not this initializer. */
    public void initialize(int kind,int strength)throws IOException {
        if(kind==0){set(KIND,0);release();return;}if(kind<1||kind>4)return;
        int old=get(KIND);if(old!=0&&old!=kind)release();set(KIND,kind);set(STRENGTH,strength);
        if(get(ENABLED)!=0){if(kind==2)initializeRain(strength);else initializeSpritesForKind(kind,strength,kind==3?4:0);io().ensureTimer5();}
    }
    /**40F540 does not reset bank/wind/toggle or stale rain/rectangle records. */
    public void release()throws IOException {set(RECT_COUNT,0);set(PREVIOUS_COUNT,0);releaseSprites();releaseRain();}
    public void releaseSprites()throws IOException {for(int n=0;n<50;n++){int a=PARTICLES+n*100+8;if(get(a)!=0)set(a,0);}int handle=get(SPRITE_HANDLE);if(handle!=-1)io().freeImage(handle);set(SPRITE_HANDLE,-1);}
    public void releaseRain()throws IOException {int handle=get(RAIN_HANDLE);if(handle!=-1)io().freeImage(handle);set(RAIN_HANDLE,-1);}
    /**40FB40 loads only if the sprite handle is−1, including a failed−1 load. */
    public void initializeSpritesForKind(int kind,int level,int variant)throws IOException {
        set(VARIANT,variant);if(get(SPRITE_HANDLE)==-1)set(SPRITE_HANDLE,io().loadImage(kind==3?0x43d08c:kind==1?0x43d07c:0x43d06c,1));initializeSprites(level,variant);
    }
    private int frame(int row,int position)throws IOException {return get(0x43ced0+(row*16+position)*4);}
    /**40FBA0 clears all50 records, then writes only the admitted prefix. */
    public void initializeSprites(int level,int variant)throws IOException {
        for(int n=0;n<50;n++)for(int k=0;k<25;k++)set(PARTICLES+n*100+k*4,0);
        int count=level<5?get(0x43d050+level*4):50;
        for(int n=0;n<count;n++){
            int p=PARTICLES+n*100;set(p+8,1);set(p+20,rand()%800);int y=-(rand()%300)-32;set(p+24,y);set(p+12,get(p+20));set(p+16,y);
            int row=rand()%6;if(variant==4)row=5;int amplitude;
            if(row==0||row==1||row==2){set(p,row);set(p+72,0);set(p+36,32);set(p+40,24);amplitude=rand()%24+24;}
            else if(row==3){set(p,3);set(p+72,rand()%2);set(p+36,32);set(p+40,24);amplitude=rand()%16+16;}
            else {set(p,row==4?4:5);set(p+72,1);set(p+36,32);set(p+40,24);amplitude=rand()%8+8;}
            set(p+84,amplitude);set(p+56,variant==4?rand()%4+2:rand()%8+4);set(p+88,rand()%8+4);set(p+60,rand()%8+4);
        }
        if(variant==0){set(WIND_MODE,0);wind(0,0);}else if(variant==1||variant==4){set(WIND_MODE,0);wind(1,2);}else if(variant==2){set(WIND_MODE,0);wind(2,2);}
    }
    /**40FE60. Other modes still store mode but retain a zero wind. */
    public void wind(int mode,int amount)throws IOException {set(WIND,0);set(WIND_MODE,mode);if(mode==1)set(WIND,amount);else if(mode==2)set(WIND,-amount);}
    private static int rainCount(int level){return level==1?25:level==2?40:level==3?80:0;}
    public void initializeRain(int level)throws IOException {
        set(RAIN_LEVEL,level);for(int n=0,count=rainCount(level);n<count;n++){int p=RAIN+n*16;set(p,rand()%800);int y=rand()%600;if(level==1){set(p+8,0);set(p+4,y);set(p+12,rand()%4+1);}else{set(p+4,y);set(p+8,rand()%(level==2?2:3));set(p+12,rand()%4+2);}}
        if(get(RAIN_HANDLE)==-1)set(RAIN_HANDLE,io().loadImage(0x43d09c,1));
    }
    /**40F7F0 gates by composition pending, not effect-kind!=0. */
    public void update()throws IOException {if(get(PENDING)==0){if(get(KIND)==2)updateRain();else updateSprites();}}
    public void updateRain()throws IOException {
        int level=get(RAIN_LEVEL);for(int n=0,count=rainCount(level);n<count;n++){
            int p=RAIN+n*16,life=get(p+12);if(life>0){int y=get(p+4);if(y<=600){set(p+12,life-1);if(level==1)set(p+4,y+32);else{int kind=get(p+8);if(kind==0)set(p+4,y+(level==2?48:64));else if(kind==1)set(p+4,y+(level==2?64:80));else if(level==3&&kind==2)set(p+4,y+112);}continue;}}
            set(p,rand()%800);int y=rand()%600-24;if(level==1){set(p+8,0);set(p+4,y);set(p+12,rand()%4+1);}else{set(p+4,y);set(p+8,rand()%(level==2?2:3));set(p+12,rand()%4*2+4);}
        }
    }
    public void updateSprites()throws IOException {
        if(get(TOGGLE)==1){set(TOGGLE,0);return;}set(TOGGLE,1);
        for(int n=0;n<50;n++){int p=PARTICLES+n*100;if(get(p+8)==0)continue;
            set(p+20,get(p+20)+get(WIND));int x=cosine(get(p+96),get(p+84),0)+get(p+20),mode=get(WIND_MODE),y=get(p+56)+get(p+16);set(p+96,get(p+96)+get(p+88));set(p+12,x);set(p+16,y);
            boolean respawn=y>599||(mode==1&&x>=800)||(mode==2&&x+32<0);
            if(respawn){if(mode==1){if(rand()%2==0){set(p+20,rand()%800);set(p+24,-32-rand()%64);}else{set(p+20,-32-rand()%64);set(p+24,rand()%400);}}
                else if(mode==2){if(rand()%2==0){set(p+20,rand()%800);set(p+24,rand()%64-64);}else{set(p+20,rand()%64+800);set(p+24,rand()%400);}}
                else {set(p+20,rand()%800);set(p+24,-(rand()%300));}set(p+12,get(p+20));set(p+16,get(p+24));set(p+96,0);}
            int tick=get(p+64)+1;set(p+64,tick);if(get(p+60)<=tick){set(p+64,0);int f=get(p+76)+1;set(p+76,f);if(frame(get(p),f)==-1)set(p+76,0);}
        }
    }
    /**40FAF0 reads its mutable pointer and the current addressed DWORD. Signed
     *negative phases can reach writable CRX/header/default-data neighbors;
     *read-only PE bytes retain their independent permission/identity owner. */
    public int cosine(int degrees,int amplitude,int base)throws IOException {int index=(degrees%360)*4096/360,entry=get(get(0x42fca4)+index*4),product=entry*amplitude;return (product+(product>>31&4095)>>12)+base;}
    /**40F6A0 intersects clip even after capacity is reached. Raw instructions
     *commit COUNT after all five fields; the decompiler reorders that store. */
    public void recordRectangle(int left,int top,int right,int bottom)throws IOException {
        compositor.intersectClip(left,top,right,bottom);int count=get(RECT_COUNT);if(count<100){int p=RECTANGLES+(count+get(RECT_BANK)*100)*20;set(p,left);set(p+4,top);set(p+8,right);set(p+12,bottom);set(p+16,1);set(RECT_COUNT,count+1);}
    }
    private void restoreParticleRect(int left,int top,int right,int bottom)throws IOException {recordRectangle(left,top,right,bottom);io().redraw(1,left,top,right,bottom);}
    public void restoreSprites()throws IOException {for(int n=0;n<50;n++){int p=PARTICLES+n*100;if(get(p+8)!=0){int x=get(p+12),y=get(p+16);recordRectangle(x,y,x+get(p+36),y+get(p+40));x=get(p+12);y=get(p+16);io().redraw(1,x,y,x+get(p+36),y+get(p+40));}}}
    public void drawSprites(int layer)throws IOException {
        for(int n=0;n<50;n++){int p=PARTICLES+n*100;if(get(p+8)!=0&&get(p+72)==layer){int row=get(p),w=get(p+36),x=get(p+12),f=frame(row,get(p+76)),y=get(p+16);compositor.intersectClip(x,y,x+w,y+get(p+40));int weight=get(KIND)==3?12:8;compositor.opacity(x-f*w,y-row*28,get(SPRITE_HANDLE),weight);}}
    }
    /**4104B0 nonzero mode restores the strips; mode0 blends their atlas. */
    public void drawRain(int mode)throws IOException {
        int level=get(RAIN_LEVEL);for(int n=0,count=rainCount(level);n<count;n++){int p=RAIN+n*16,kind=level==1?0:get(p+8),x=get(p),y=get(p+4);if(kind<0||kind>=(level==1?1:level==2?2:3))continue;int height=kind==0?24:kind==1?56:104;if(mode!=0)restoreParticleRect(x,y,x+2,y+height);else{compositor.intersectClip(x,y,x+2,y+height);int weight=get(p+12),handle=get(RAIN_HANDLE);compositor.opacity(x-kind*8,y,handle,weight);}}
    }
    /**Underlay's40FA80 leaf, with no invented fullClip/present/update. */
    public void drawLayer(int layer)throws IOException {
        if(get(PENDING)!=0)return;int kind=get(KIND);if(layer==1){if(kind==2){if(get(RECT_COUNT)==0)drawRain(1);drawRain(0);}else{if(get(RECT_COUNT)==0)restoreSprites();drawSprites(1);}}else if(kind!=2)drawSprites(0);
    }
    public void redrawRectangles(int mask)throws IOException {for(int n=0;n<get(RECT_COUNT);n++){int p=RECTANGLES+(n+get(RECT_BANK)*100)*20;io().redraw(mask,get(p),get(p+4),get(p+8),get(p+12));}}
    public void rotateRectangles()throws IOException {redrawRectangles(0xf5);set(PREVIOUS_COUNT,get(RECT_COUNT));set(RECT_COUNT,0);set(RECT_BANK,1-get(RECT_BANK));}
    /**40F9E0 is the separate timer composition layer, not40FA80. */
    public void timerDrawLayer(int layer)throws IOException {
        if(get(PENDING)!=0)return;if(layer==1){compositor.fullClip();rotateRectangles();if(get(KIND)==2){compositor.fullClip();drawRain(1);drawRain(0);redrawRectangles(4);redrawRectangles(0xf0);}else{compositor.fullClip();restoreSprites();compositor.fullClip();drawSprites(1);redrawRectangles(4);}}else if(get(KIND)!=2){compositor.fullClip();drawSprites(0);redrawRectangles(0xf0);}
    }
    /**Only the effect prefix of418DF0. Head-icon/audio-callback/wait logic
     *after418E34 remains owned by its enclosing native event dispatcher. */
    public void timerEffects()throws IOException {
        if(get(BUSY)==0&&get(KIND)!=0&&get(ENABLED)!=0){update();compositor.fullClip();timerDrawLayer(1);timerDrawLayer(0);redrawOverlaps();paintRectangles();}
    }
    /**40F8E0 paints every overlapping pair in native order, retaining the
     *last clipped intersection unless another match restores the base. */
    public void redrawOverlaps()throws IOException {
        for(int n=0;n<get(RECT_COUNT);n++){int p=RECTANGLES+get(RECT_BANK)*2000+n*20,l=get(p),t=get(p+4),r=get(p+8),b=get(p+12);compositor.setClip(l,t,r,b);
            for(int j=n+1;j<get(RECT_COUNT);j++){int q=p+(j-n)*20;Pc1Raster.Clip c=compositor.clip();int il=Math.max(c.left,get(q)),it=Math.max(c.top,get(q+4)),ir=Math.min(c.right,get(q+8)),ib=Math.min(c.bottom,get(q+12));if(il<ir&&it<ib){compositor.intersectClip(get(q),get(q+4),get(q+8),get(q+12));io().redraw(255,il,it,ir,ib);compositor.setClip(l,t,r,b);}}
        }
    }
    /**40F810's OS copy service stays distinct from407000 invalidation. */
    public void paintRectangles()throws IOException {
        io().beginNativePaint();int bank=1-get(RECT_BANK);for(int n=0;n<get(PREVIOUS_COUNT);n++){int p=RECTANGLES+(n+bank*100)*20;io().paintRectangle(get(p),get(p+4),get(p+8),get(p+12));}
        int p=RECTANGLES+get(RECT_BANK)*2000;for(int n=0;n<get(RECT_COUNT);n++,p+=20)io().paintRectangle(get(p),get(p+4),get(p+8),get(p+12));io().endNativePaint();compositor.resetDirty();
    }
}
