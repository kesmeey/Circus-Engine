package org.circus.core;

import java.io.*;

/** OriginalDC2 rain with persistent pixels and native rectangle banks. This
 *does not synthesize the wider sprite/UI/resource/OS consumers: active calls
 *must reach their real required owners. No scene metadata rebuild or timer
 *catchup is substituted for the native event function. */
public final class Dc2RainPipeline implements Dc2Rain.Services {
    public static final int KIND=0xa0ac2c,STRENGTH=0xa0ac34,PENDING=0x9da2d0,BUSY=0xbe8bb0,ENABLED=0x473f94;
    public static final int RECTANGLES=0x990340,BANK=0x9917e0,PREVIOUS=0x9917e4,COUNT=0x9917e8;
    public interface Services {
        int loadImage(int rawResourceAddress,int allocationMode)throws IOException; //401CC0
        void freeImage(int handle)throws IOException; //401130,actual resource/cache owner
        void ensureTimer5()throws IOException; //407840,real existing timer owner
        void redraw(int mask,int left,int top,int right,int bottom)throws IOException; //41D3F0
        void releaseSprites()throws IOException; //412340,not an assumed empty operation
        void updateSprites()throws IOException; //412050
        void restoreSpriteRectangles()throws IOException; //4122F0
        void drawSprites(int layer)throws IOException; //412250
        void beginNativePaint()throws IOException; //GetDC,4119B0
        void paintRectangle(int left,int top,int right,int bottom)throws IOException; //407600
        void endNativePaint()throws IOException; //ReleaseDC
    }
    /** Dedicated rain/rectangle bytes. Sprite records/wind/toggle, frame,
     *settings and CRT state retain their canonical external owners. */
    public static final class Memory implements Dc2Rain.State {
        private static final int FORMAT=0x44325231;
        private final Dc2Rain.State external;
        private final byte[] raw=new byte[5296],handle={(byte)255,(byte)255,(byte)255,(byte)255};
        public Memory(Dc2Rain.State external)throws IOException{if(external==null)throw new IOException("DC2 rain external raw DWORD owner missing");this.external=external;}
        private boolean own(int address){return address>=Dc2Rain.LEVEL&&address<Dc2Rain.LEVEL+raw.length||address>=Dc2Rain.HANDLE&&address<Dc2Rain.HANDLE+4;}
        public int readByte(int address)throws IOException{if(address>=Dc2Rain.LEVEL&&address<Dc2Rain.LEVEL+raw.length)return raw[address-Dc2Rain.LEVEL]&255;if(address>=Dc2Rain.HANDLE&&address<Dc2Rain.HANDLE+4)return handle[address-Dc2Rain.HANDLE]&255;return external.read(address&~3)>>>(address%4*8)&255;}
        public void writeByte(int address,int value)throws IOException{if(address>=Dc2Rain.LEVEL&&address<Dc2Rain.LEVEL+raw.length)raw[address-Dc2Rain.LEVEL]=(byte)value;else if(address>=Dc2Rain.HANDLE&&address<Dc2Rain.HANDLE+4)handle[address-Dc2Rain.HANDLE]=(byte)value;else{int aligned=address&~3,shift=address%4*8;external.write(aligned,external.read(aligned)&~(255<<shift)|(value&255)<<shift);}}
        public int read(int address)throws IOException{if(!own(address)&&!own(address+3)&&(address&3)==0)return external.read(address);int value=0;for(int n=0;n<4;n++)value|=readByte(address+n)<<(n*8);return value;}
        public void write(int address,int value)throws IOException{if(!own(address)&&!own(address+3)&&(address&3)==0){external.write(address,value);return;}for(int n=0;n<4;n++)readByte(address+n);for(int n=0;n<4;n++)writeByte(address+n,value>>>(n*8));}
        public void save(DataOutput out)throws IOException{out.writeInt(FORMAT);out.write(raw);out.write(handle);}
        public static final class Image{private final byte[] raw,handle;private Image(byte[] raw,byte[] handle){this.raw=raw;this.handle=handle;}}
        public static Image load(DataInput in)throws IOException{if(in.readInt()!=FORMAT)throw new IOException("DC2 rain rectangle format");byte[] raw=new byte[5296],handle=new byte[4];in.readFully(raw);in.readFully(handle);return new Image(raw,handle);}
        public void restore(Image image)throws IOException{if(image==null)throw new IOException("DC2 rain rectangle image missing");System.arraycopy(image.raw,0,raw,0,raw.length);System.arraycopy(image.handle,0,handle,0,4);}
    }
    private final Dc2Rain.State state;
    private final Dc2Compositor c;
    private final Dc2Rain.Random random;
    private final Services services;
    public Dc2RainPipeline(Dc2Rain.State state,Dc2Compositor compositor,Dc2Rain.Random random,Services services)throws IOException{if(state==null||compositor==null)throw new IOException("DC2 rain canonical state/compositor missing");this.state=state;c=compositor;this.random=random;this.services=services;}
    private int g(int a)throws IOException{return state.read(a);}
    private void g(int a,int v)throws IOException{state.write(a,v);}
    private Services io(int entry)throws IOException{if(services==null)throw new IOException("DC2 rain requires native consumer"+Integer.toHexString(entry));return services;}
    /**The kind2 branch of411670. This does not claim other kind handlers. */
    public void initializeRain(int level)throws IOException{int old=g(KIND);if(old!=0&&old!=2)releaseEffects();g(KIND,2);g(STRENGTH,level);if(g(ENABLED)!=0){Dc2Rain.initialize(state,random,this,level);io(0x407840).ensureTimer5();}}
    /**Kind0 branch. Native does not repaint old rain pixels on stop. */
    public void stop()throws IOException{g(KIND,0);releaseEffects();}
    public void releaseEffects()throws IOException{g(COUNT,0);g(PREVIOUS,0);io(0x412340).releaseSprites();Dc2Rain.release(state,this);}
    public int loadImage(int address,int mode)throws IOException{return io(0x401cc0).loadImage(address,mode);}
    public void freeImage(int handle)throws IOException{io(0x401130).freeImage(handle);}
    public void clip(int l,int t,int r,int b){c.intersectClip(l,t,r,b);}
    /**411840 stores fivefields before committing COUNT,following actualbytes. */
    public void restore(int l,int t,int r,int b)throws IOException{c.intersectClip(l,t,r,b);int count=g(COUNT);if(count<100){int p=RECTANGLES+(count+g(BANK)*100)*20;g(p,l);g(p+4,t);g(p+8,r);g(p+12,b);g(p+16,1);g(COUNT,count+1);}}
    public void controls(int mask,int l,int t,int r,int b)throws IOException{io(0x41d3f0).redraw(mask,l,t,r,b);}
    public void blend(int x,int y,int handle,int weight)throws IOException{c.opacity(x,y,handle,weight);}
    /**411990 may call the independently owned sprite updater fornonrain. */
    public void update()throws IOException{if(g(PENDING)==0){if(g(KIND)==2)Dc2Rain.update(state,random);else io(0x412050).updateSprites();}}
    /**411C20. It does not invent an enabled/kind0 gate or fullClip. */
    public void drawLayer(int layer)throws IOException{if(g(PENDING)!=0)return;int kind=g(KIND);if(layer==1){if(kind==2){if(g(COUNT)==0)Dc2Rain.draw(state,this,1);Dc2Rain.draw(state,this,0);}else{if(g(COUNT)==0)io(0x4122f0).restoreSpriteRectangles();io(0x412250).drawSprites(1);}}else if(kind!=2)io(0x412250).drawSprites(0);}
    public void redrawRectangles(int mask)throws IOException{for(int n=0;n<g(COUNT);n++){int p=RECTANGLES+(n+g(BANK)*100)*20;io(0x41d3f0).redraw(mask,g(p),g(p+4),g(p+8),g(p+12));}}
    public void rotateRectangles()throws IOException{redrawRectangles(245);g(PREVIOUS,g(COUNT));g(COUNT,0);g(BANK,1-g(BANK));}
    public void timerDrawLayer(int layer)throws IOException{
        if(g(PENDING)!=0)return;if(layer==1){c.fullClip();rotateRectangles();if(g(KIND)==2){c.fullClip();Dc2Rain.draw(state,this,1);Dc2Rain.draw(state,this,0);redrawRectangles(4);redrawRectangles(240);}else{c.fullClip();io(0x4122f0).restoreSpriteRectangles();c.fullClip();io(0x412250).drawSprites(1);redrawRectangles(4);}}else if(g(KIND)!=2){c.fullClip();io(0x412250).drawSprites(0);redrawRectangles(240);}
    }
    public void redrawOverlaps()throws IOException{for(int n=0;n<g(COUNT);n++){int p=RECTANGLES+g(BANK)*2000+n*20,l=g(p),t=g(p+4),r=g(p+8),b=g(p+12);c.setClip(l,t,r,b);for(int j=n+1;j<g(COUNT);j++){int q=p+(j-n)*20;if(c.intersectNonempty(g(q),g(q+4),g(q+8),g(q+12))){Pc1Raster.Clip v=c.clip();io(0x41d3f0).redraw(255,v.left,v.top,v.right,v.bottom);c.setClip(l,t,r,b);}}}}
    public void paintRectangles()throws IOException{io(0x4119b0).beginNativePaint();int p=RECTANGLES+(1-g(BANK))*2000;for(int n=0;n<g(PREVIOUS);n++,p+=20)io(0x407600).paintRectangle(g(p),g(p+4),g(p+8),g(p+12));p=RECTANGLES+g(BANK)*2000;for(int n=0;n<g(COUNT);n++,p+=20)io(0x407600).paintRectangle(g(p),g(p+4),g(p+8),g(p+12));io(0x4119b0).endNativePaint();c.resetDirty();}
    /**Only41B180's effectprefix;audio callbacks/waits remain external. */
    public void timerEffects()throws IOException{if(g(BUSY)==0&&g(KIND)!=0&&g(ENABLED)!=0){update();c.fullClip();timerDrawLayer(1);timerDrawLayer(0);redrawOverlaps();paintRectangles();}}
}
