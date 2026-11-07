package org.circus.core;
import java.io.*;

/** Complete current CHS405800 ordering. OS/engine leaves are required; neither
 *a Java constructor nor a physical DIB implies a successfully started engine. */
public final class Pc1StartupWindow {
    public static final class Rect {public int left,top,right,bottom;public Rect(int l,int t,int r,int b){left=l;top=t;right=r;bottom=b;}}
    public interface Services {
        int metric(int index)throws IOException;
        void adjustWindowRect(Rect rectangle,int style,boolean menu)throws IOException;
        long instance()throws IOException; //physical94C30C
        long createWindow(int extendedStyle,int rawClass,int rawTitle,int style,int x,int y,int width,int height,long instance)throws IOException;
        long systemMenu(long hwnd,boolean revert)throws IOException;
        void appendMenu(long menu,int flags,int id,int rawText)throws IOException;
        void showWindow(long hwnd,int command)throws IOException;
        void updateWindow(long hwnd)throws IOException;
        void mainWindow(long hwnd)throws IOException;long mainWindow()throws IOException;
        void enableIme(long hwnd,int enable)throws IOException; //WINNLSEnableIME(0,0)
        void initializeFontContext(long hwnd)throws IOException; //404230
        void initializeAudio()throws IOException; //41CD90→41B7F0
        void initializeAccelerated(long hwnd)throws IOException; //403050
        void initializeFront(long hwnd)throws IOException; //406ED0, after font/audio/DD
        void readSettings()throws IOException; //402890
        void loadScript(int rawName,int mode)throws IOException; //411670, return ignored
        int tickCount()throws IOException;
        void seedRandom(int rawTick)throws IOException; //420858, native CRT owner
        int setTimer(long hwnd,int id,int milliseconds)throws IOException;
    }
    private final Pc1NativeMemory memory;private final Services io;
    public Pc1StartupWindow(Pc1NativeMemory memory,Services io)throws IOException{if(memory==null||io==null)throw new IOException("PC1 startup window services missing");this.memory=memory;this.io=io;}
    public Frame begin(long instance,int show){return new Frame(instance,show);}
    public int start(long instance,int show)throws IOException{Frame f=begin(instance,show);while(f.active())f.step();return f.result();}
    public final class Frame {
        private int phase,show,style,extended,x,y,width,height,result;private long passedInstance,createInstance,hwnd,menu;private boolean addMenu,busy;
        private Frame(long instance,int show){passedInstance=instance;this.show=show;}
        public boolean active(){return phase>=0&&phase<20;}public int phase(){return phase;}public long window(){return hwnd;}
        public int result()throws IOException{if(phase!=20)throw new IOException("PC1 window startup incomplete");return result;}
        public void step()throws IOException{
            if(!active()||busy)throw new IOException("PC1 window startup frame not active/reentrant");busy=true;
            try{switch(phase){
            case 0:geometry();phase=1;break;
            case 1:hwnd=io.createWindow(extended,0x430314,0x4302f4,style,x,y,width,height,createInstance);phase=addMenu?2:5;break;
            case 2:menu=io.systemMenu(hwnd,false);phase=3;break; //even HWND==NULL
            case 3:io.appendMenu(menu,0x800,0,0x94bc30);phase=4;break;
            case 4:io.appendMenu(menu,0,0x384,0x430320);phase=5;break;
            case 5:if(hwnd==0){result=0;phase=20;}else phase=6;break;
            case 6:io.showWindow(hwnd,show);phase=7;break;
            case 7:io.updateWindow(hwnd);phase=8;break;
            case 8:io.mainWindow(hwnd);io.enableIme(0,0);phase=9;break;
            case 9:io.initializeFontContext(hwnd);phase=10;break;
            case 10:if(Pc1Startup.get(memory,0x94c698)!=2)io.initializeAudio();phase=11;break;
            case 11:if(Pc1Startup.get(memory,0x94c61c)!=0&&Pc1Startup.get(memory,0x94c620)!=0)io.initializeAccelerated(io.mainWindow());phase=12;break;
            case 12:io.initializeFront(io.mainWindow());phase=13;break;
            case 13:phase=Pc1Startup.get(memory,0x94c698)==2?16:14;break;
            case 14:io.readSettings();phase=15;break;
            case 15:io.loadScript(0x971464,1);phase=16;break;
            case 16:int tick=io.tickCount();io.seedRandom(tick);phase=17;break;
            case 17:Pc1Startup.put(memory,0x94c670,io.setTimer(hwnd,1,40));phase=18;break;
            case 18:Pc1Startup.put(memory,0x94c694,1);result=1;phase=20;break;
            default:throw new IOException("PC1 startup window phase");
            }}catch(IOException|RuntimeException failure){phase=-1;throw failure;}finally{busy=false;}
        }
        private void geometry()throws IOException{
            width=800;height=600;x=0;y=0;
            if(Pc1Startup.get(memory,0x94c61c)!=0){extended=8;style=0x92000000;createInstance=io.instance();addMenu=false;return;}
            extended=0;createInstance=passedInstance;addMenu=true;
            if(io.metric(0)==800&&io.metric(1)==600){style=0x82000000;return;}
            style=0x02ca0000;int right=io.metric(5)+799;int bottom=io.metric(6)+599;
            Rect rectangle=new Rect(0,0,right,bottom);io.adjustWindowRect(rectangle,style,false);
            width=rectangle.right-rectangle.left;height=rectangle.bottom-rectangle.top;
            int centerX=io.metric(0)/2-width/2;int centerY=io.metric(1)/2-height/2;
            int aligned=(io.metric(5)+centerX)&~7;aligned+=io.metric(5);
            x=Math.max(0,aligned);y=Math.max(0,centerY);
        }
        public void save(DataOutput out)throws IOException{if(busy||phase<0)throw new IOException("PC1 window startup outside snapshot boundary");out.writeInt(0x50315753);for(int v:new int[]{phase,show,style,extended,x,y,width,height,result})out.writeInt(v);out.writeBoolean(addMenu);out.writeLong(passedInstance);out.writeLong(createInstance);out.writeLong(hwnd);out.writeLong(menu);}
    }
    public Frame load(DataInput in,Pc1Startup.Adoption adoption)throws IOException{
        if(in.readInt()!=0x50315753)throw new IOException("PC1 startup window frame format");Frame f=new Frame(0,0);f.phase=in.readInt();f.show=in.readInt();f.style=in.readInt();f.extended=in.readInt();f.x=in.readInt();f.y=in.readInt();f.width=in.readInt();f.height=in.readInt();f.result=in.readInt();f.addMenu=in.readBoolean();
        f.passedInstance=Pc1Startup.adoptNonzero(adoption,"instance",in.readLong());f.createInstance=Pc1Startup.adoptNonzero(adoption,"instance",in.readLong());f.hwnd=Pc1Startup.adoptNonzero(adoption,"window",in.readLong());f.menu=Pc1Startup.adoptNonzero(adoption,"menu",in.readLong());
        if(f.phase<0||f.phase>20||f.phase==19)throw new IOException("PC1 startup window frame phase");return f;
    }
}
