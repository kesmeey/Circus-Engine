package org.circus.core;

import java.io.IOException;

/** Current DC2 412380/4124C0/412670/412870. Eighty16-byte records are
 *independent from the sprite-particle50x100 store. All state remains addressed;
 *graphics-pool, clip, partial restoration and UI/dirty consumers are required. */
public final class Dc2Rain {
    private Dc2Rain(){}
    public static final int LEVEL=0x99033c,RECORDS=0x9912e0,CAPACITY=80,STRIDE=16,HANDLE=0x45e774,RESOURCE=0x45e7d0;
    public interface State {int read(int address)throws IOException;void write(int address,int value)throws IOException;}
    public interface Random {int next()throws IOException;}
    public interface Services {
        int loadImage(int rawResourceAddress,int allocationMode)throws IOException;
        void freeImage(int handle)throws IOException;
        void clip(int left,int top,int right,int bottom)throws IOException; //407F40
        void restore(int left,int top,int right,int bottom)throws IOException; //411840
        void controls(int mode,int left,int top,int right,int bottom)throws IOException; //41D3F0
        void blend(int x,int y,int handle,int weight)throws IOException; //408B80
    }
    private static int count(int level){return level==1?25:level==2?40:level==3?80:0;}
    private static int random(Random random)throws IOException{if(random==null)throw new IOException("DC2 rain requires shared native CRT random owner");return random.next();}
    private static State state(State state)throws IOException{if(state==null)throw new IOException("DC2 rain addressed state missing");return state;}
    private static Services services(Services services)throws IOException{if(services==null)throw new IOException("DC2 rain required native graphics service missing");return services;}
    /**412380 writes level before any RNG/resource call and preserves every
     *inactive suffix record. Levels outside1..3 still attempt the atlas load. */
    public static void initialize(State input,Random rng,Services io,int level)throws IOException{
        State s=state(input);s.write(LEVEL,level);int count=count(level);
        for(int n=0;n<count;n++){
            int p=RECORDS+n*STRIDE;s.write(p,random(rng)%800);int y=random(rng)%600;
            if(level==1){s.write(p+8,0);s.write(p+4,y);s.write(p+12,random(rng)%4+1);}
            else{s.write(p+4,y);s.write(p+8,random(rng)%(level==2?2:3));s.write(p+12,random(rng)%4+2);}
        }
        if(s.read(HANDLE)==-1)s.write(HANDLE,services(io).loadImage(RESOURCE,1));
    }
    /**4124C0 updates every admitted rain record on EACH call (no sprite
     *alternate-tick toggle). y==600 still advances; y>600 respawns next. */
    public static void update(State input,Random rng)throws IOException{
        State s=state(input);int level=s.read(LEVEL),count=count(level);
        for(int n=0;n<count;n++){
            int p=RECORDS+n*STRIDE,life=s.read(p+12);
            if(life>0){int y=s.read(p+4);if(y<=600){s.write(p+12,life-1);if(level==1)s.write(p+4,y+32);else{int kind=s.read(p+8);if(kind==0)s.write(p+4,y+(level==2?48:64));else if(kind==1)s.write(p+4,y+(level==2?64:80));else if(level==3&&kind==2)s.write(p+4,y+112);}continue;}}
            s.write(p,random(rng)%800);int y=random(rng)%600-24;
            if(level==1){s.write(p+8,0);s.write(p+4,y);s.write(p+12,random(rng)%4+1);}
            else{s.write(p+4,y);s.write(p+8,random(rng)%(level==2?2:3));s.write(p+12,(random(rng)%4)*2+4);}
        }
    }
    /**412670 mode0 draws clipped2-pixel strips. Any nonzero mode restores
     *the same rectangles, then invokes the separate controls/dirty consumer. */
    public static void draw(State input,Services io,int mode)throws IOException{
        State s=state(input);int level=s.read(LEVEL),count=count(level);
        for(int n=0;n<count;n++){
            int p=RECORDS+n*STRIDE,kind=level==1?0:s.read(p+8),x=s.read(p),y=s.read(p+4);
            if(kind<0||kind>=(level==1?1:level==2?2:3))continue;int height=kind==0?24:kind==1?56:104;
            if(mode==0){services(io).clip(x,y,x+2,y+height);int weight=s.read(p+12),handle=s.read(HANDLE);services(io).blend(x-kind*8,y,handle,weight);}
            else{services(io).restore(x,y,x+2,y+height);services(io).controls(1,x,y,x+2,y+height);}
        }
    }
    /**412870 releases only the rain image; raw rain records remain stale. */
    public static void release(State input,Services io)throws IOException{
        State s=state(input);int handle=s.read(HANDLE);if(handle!=-1)services(io).freeImage(handle);s.write(HANDLE,-1);
    }
}
