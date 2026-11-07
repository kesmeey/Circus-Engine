package org.circus.core;
import java.io.*;

/** 407160 display-mode request construction. Actual ChangeDisplaySettingsA is
 *required and may change the user's desktop, so portable tests never call it. */
public final class Pc1StartupDisplay {
    public interface Services {
        int enumDisplaySettings(int index,Pc1StartupScreens.Buffer output)throws IOException;
        int changeDisplaySettings(Pc1StartupScreens.Buffer mode,int flags)throws IOException;
    }
    private final Pc1NativeMemory memory;private final Services io;
    public Pc1StartupDisplay(Pc1NativeMemory memory,Services io)throws IOException{if(memory==null||io==null)throw new IOException("PC1 display-change services missing");this.memory=memory;this.io=io;}
    public Frame begin(int mode){return new Frame(mode);}
    public int change(int mode)throws IOException{Frame f=begin(mode);while(f.active())f.step();return f.result();}
    public final class Frame {
        private int mode,phase,result;private boolean busy;private Pc1StartupScreens.Buffer local=new Pc1StartupScreens.Buffer();
        private Frame(int mode){this.mode=mode;}
        public boolean active(){return phase>=0&&phase<4;}public int phase(){return phase;}
        public int result()throws IOException{if(phase!=4)throw new IOException("PC1 display-change incomplete");return result;}
        public void step()throws IOException{
            if(!active()||busy)throw new IOException("PC1 display-change frame not active/reentrant");busy=true;
            try{switch(phase){
            case 0:local.write(0,new byte[148],0,148);io.enumDisplaySettings(0,local);phase=1;break;
            case 1:
                if(mode==0){byte[] raw=new byte[148];for(int n=0;n<148;n++)raw[n]=(byte)memory.readAddress(Pc1StartupMemory.CURRENT_DISPLAY+n);local.write(0,raw,0,148);local.put(40,0x5c0000);}
                else{int depth=Pc1Startup.get(memory,Pc1StartupMemory.PREFERRED_BPP);local.put(104,depth);local.put(112,600);local.put(108,800);local.put(40,0x1c0000);Pc1Startup.put(memory,0x94c630,depth<24?1:0);}phase=2;break;
            case 2:result=io.changeDisplaySettings(local,2);phase=result==0?3:4;break;
            case 3:result=io.changeDisplaySettings(local,mode==0?0:4);phase=4;break;
            default:throw new IOException("PC1 display-change phase");
            }}catch(IOException|RuntimeException failure){phase=-1;throw failure;}finally{busy=false;}
        }
        public void save(DataOutput out)throws IOException{if(busy||phase<0)throw new IOException("PC1 display-change outside snapshot boundary");out.writeInt(0x50315344);out.writeInt(mode);out.writeInt(phase);out.writeInt(result);local.save(out);}
    }
    public Frame load(DataInput in)throws IOException{if(in.readInt()!=0x50315344)throw new IOException("PC1 display-change frame format");Frame f=new Frame(in.readInt());f.phase=in.readInt();f.result=in.readInt();f.local=Pc1StartupScreens.Buffer.load(in);if(f.phase<0||f.phase>4)throw new IOException("PC1 display-change phase");return f;}
}
