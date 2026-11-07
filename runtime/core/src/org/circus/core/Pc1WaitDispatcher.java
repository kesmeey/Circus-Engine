package org.circus.core;
import java.io.*;

/**Complete418A70 control flow. Unmapped external leaves stay required. */
public final class Pc1WaitDispatcher {
    public interface Services {
        void waitCursor(int mode)throws IOException; //4186F0
        boolean skipRequested()throws IOException; //4118B0
        void stopVoice()throws IOException; //41CBE0
        int voiceIdle(int argument)throws IOException; //41CBF0
        void gridWait40B8A0()throws IOException;
        int returnTransition4158E0()throws IOException;
        int tickCount()throws IOException;
        int specialComplete4047A0()throws IOException;
        int validateInstallKey41B6C0(int rawText,int flags)throws IOException;
        int selection4167D0()throws IOException;
        void markScenarioRead(int rawName)throws IOException; //41AE20
        void loadScript(int rawName,int mode)throws IOException; //411670
        void closeAudio()throws IOException; //41CDA0
        byte[] resolveMoviePath(int rawName)throws IOException; //40E870, actual local260
        void playMovie(byte[] knownPath)throws IOException; //40E090, same actualHWND
        void initializeAudio()throws IOException; //41B7F0
        int pumpMessages(long filterHwnd)throws IOException; //405710(0)
        int textPaint417D20()throws IOException;
        int speaker416620()throws IOException;
        int move416A30()throws IOException;
    }
    public static final int TICK=0xbb84ec;
    public static final class Memory {
        private final byte[] raw=new byte[4];
        public Memory(Pc1NativeMemory bus)throws IOException{if(bus==null)throw new IOException("PC1 wait tick requires canonical bus");bus.bind(TICK,4,new Pc1NativeMemory.Region(){public int read(int at){return raw[at]&255;}public void write(int at,int value){raw[at]=(byte)value;}});}
        public void save(DataOutput out)throws IOException{out.writeInt(0x50315754);out.write(raw);}
        public static byte[] loadImage(DataInput in)throws IOException{if(in.readInt()!=0x50315754)throw new IOException("PC1 wait tick format");byte[] result=new byte[4];in.readFully(result);return result;}
        public void restore(byte[] bytes)throws IOException{if(bytes==null||bytes.length!=4)throw new IOException("PC1 wait tick image");System.arraycopy(bytes,0,raw,0,4);}
    }
    private final Pc1NativeMemory memory;private final Services io;private int depth;
    public Pc1WaitDispatcher(Pc1NativeMemory memory,Services services)throws IOException{if(memory==null||services==null)throw new IOException("PC1 wait dispatcher requires canonical bus and every native consumer");this.memory=memory;io=services;}
    public void requireBoundary()throws IOException{if(depth!=0)throw new IOException("PC1 wait snapshot during native callback");}
    private int g(int a)throws IOException{return memory.readAddress(a)|memory.readAddress(a+1)<<8|memory.readAddress(a+2)<<16|memory.readAddress(a+3)<<24;}
    private void g(int a,int v)throws IOException{for(int n=0;n<4;n++)memory.writeAddress(a+n,v>>>(n*8));}
    public int poll()throws IOException{depth++;try{switch(g(0x9d8a20)){
        case 0:return 1;
        case 1:case 10:case 0x16:return 2;
        case 7:
            io.waitCursor(0);if(io.skipRequested()){io.stopVoice();return 0;}
            if(g(0x9da4a4)>0){g(0x9da4a4,g(0x9da4a4)-1);return 1;}
            int mode=g(0x454cdc);if(mode==2||mode==3){if(g(0x43d410)>=0||g(0xbb8414)!=0)return 1;if(io.voiceIdle(0)!=0)return 0;}return 1;
        case 9:g(0x430284,100);return 1;
        case 11:g(0x9da4c0,-1);io.gridWait40B8A0();g(0x430284,100);return 1;
        case 13:return io.returnTransition4158E0();
        case 14:return 3;
        case 0x12:
            if(g(0x9da4c4)==4){int now=io.tickCount(),duration=g(0x9da4c0)*200;if(Integer.compareUnsigned(duration,now-g(TICK))<=0){g(TICK,g(TICK)+duration);return 0;}return 1;}
            if((g(0x9da4c4)&2)!=0)io.waitCursor(0);if(g(0x9da4c0)!=-1){g(0x9da4c0,g(0x9da4c0)-1);return g(0x9da4c0)>0?1:0;}return 1;
        case 0x1d:if(g(0xbb8470)!=0||io.specialComplete4047A0()==0)return 1;g(0x9d8a20,-1);return 0;
        case 0x29:io.validateInstallKey41B6C0(0x9691c4,0x20);break;
        case 0x33:return io.selection4167D0();
        case 0x36:io.markScenarioRead(0x9d858c);io.loadScript(0x9cb508,g(0x9da4c0));if(g(0x9d8a20)==1)return 2;break;
        case 0x39:
            if(g(0x94c624)==0){io.closeAudio();g(0x94c68c,1);byte[] path=io.resolveMoviePath(0x9cb508);io.playMovie(path);if(g(0x94c688)==0){io.initializeAudio();io.pumpMessages(0);g(0x94c68c,0);return 0;}return -1;}break;
        case 0x44:g(0x9da4c0,-1);return 2;
        case 0x4c:return io.textPaint417D20();
        case 0x5e:return io.speaker416620();
        case 0x61:return io.move416A30();
        default:break;
    }return 0;}finally{depth--;}}
}
