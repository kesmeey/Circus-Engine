package org.circus.core;
import java.io.*;
import java.util.*;

/** 407450 +407220. Failed EnumDisplaySettings still has its retained local
 *DEVMODE inspected. Unknown stack bytes stay unknown rather than zero-filled. */
public final class Pc1StartupScreens {
    public interface Services {
        int metric(int index)throws IOException;
        long desktopDc()throws IOException;
        int deviceCaps(long dc,int index)throws IOException;
        void releaseDesktopDc(long dc)throws IOException;
        int insufficientDisplayMessage(int rawText,int rawTitle,int flags)throws IOException;
        int enumDisplaySettings(int index,Buffer output)throws IOException;
    }
    public static final class Buffer {
        public static final int BYTES=148;
        private final byte[] raw=new byte[BYTES],known=new byte[BYTES];private Pc1NativeMemory target;private int base;
        private static Buffer view(Pc1NativeMemory memory,int address)throws IOException{Buffer b=new Buffer();b.target=memory;b.base=address;for(int n=0;n<BYTES;n++){b.raw[n]=(byte)memory.readAddress(address+n);b.known[n]=1;}return b;}
        public void write(int offset,byte[] bytes,int from,int count)throws IOException{if(bytes==null||offset<0||count<0||offset>BYTES-count||from<0||from>bytes.length-count)throw new IOException("PC1 DEVMODE output bounds");for(int n=0;n<count;n++){raw[offset+n]=bytes[from+n];known[offset+n]=1;if(target!=null)target.writeAddress(base+offset+n,bytes[from+n]);}}
        public void put(int offset,int value)throws IOException{for(int n=0;n<4;n++)write(offset+n,new byte[]{(byte)(value>>>(8*n))},0,1);}
        public int get(int offset)throws IOException{if(offset<0||offset>BYTES-4)throw new IOException("PC1 DEVMODE field bounds");int value=0;for(int n=0;n<4;n++){if(known[offset+n]==0)throw new IOException("PC1 EnumDisplaySettings failed with unknown DEVMODE byte "+(offset+n));value|=(raw[offset+n]&255)<<(8*n);}return value;}
        /** Input bytes/mask permit the actual native adapter to seed only known
         *backing. An OS API must explicitly report what output it produced. */
        public byte[] bytes(){return raw.clone();}public byte[] known(){return known.clone();}
        public void save(DataOutput out)throws IOException{out.write(raw);out.write(known);}
        public static Buffer load(DataInput in)throws IOException{Buffer b=new Buffer();in.readFully(b.raw);in.readFully(b.known);for(byte v:b.known)if(v!=0&&v!=1)throw new IOException("PC1 DEVMODE known mask");return b;}
    }
    private final Pc1NativeMemory memory;private final Services io;
    public Pc1StartupScreens(Pc1NativeMemory memory,Services io)throws IOException{if(memory==null||io==null)throw new IOException("PC1 screen startup services missing");this.memory=memory;this.io=io;}
    public Frame begin(){return new Frame();}
    public int check()throws IOException{Frame f=begin();while(f.active())f.step();return f.result();}
    public final class Frame {
        private int phase,width,height,bpp,index,result;private long dc;private boolean busy;private Buffer local=new Buffer();
        private Frame(){}
        public boolean active(){return phase>=0&&phase<7;}public int phase(){return phase;}
        public int result()throws IOException{if(phase!=7)throw new IOException("PC1 display check incomplete");return result;}
        public void step()throws IOException {
            if(!active()||busy)throw new IOException("PC1 display frame not active/reentrant");busy=true;
            try{switch(phase){
            case 0:Pc1Startup.put(memory,0x43028c,1);width=io.metric(0);height=io.metric(1);phase=1;break;
            case 1:dc=io.desktopDc();bpp=io.deviceCaps(dc,12);if(bpp==15||bpp==16)Pc1Startup.put(memory,0x94c62c,1);io.releaseDesktopDc(dc);dc=0;phase=2;break;
            case 2:
                if(width>=800&&height>=600&&bpp>8){if(width==800||height==600){Pc1Startup.put(memory,0x43028c,0);Pc1Startup.put(memory,0x94c61c,1);}phase=3;}
                else{int answer=io.insufficientDisplayMessage(0x430368,0x4302f4,4);if(answer==6){Pc1Startup.put(memory,0x43028c,0);Pc1Startup.put(memory,0x94c61c,1);result=0;}else result=-1;phase=7;}break;
            case 3:
                // Native zeroes the GLOBAL current-mode buffer, but never sets
                //dmSize. It does not initialize the enumeration local at all.
                for(int n=0;n<148;n++)memory.writeAddress(Pc1StartupMemory.CURRENT_DISPLAY+n,0);
                io.enumDisplaySettings(-1,Buffer.view(memory,Pc1StartupMemory.CURRENT_DISPLAY));index=0;phase=4;break;
            case 4:
                int status=io.enumDisplaySettings(index,local);
                if(local.get(108)==800&&local.get(112)==600){int bits=local.get(104);int address=bits==24?0x94c6c0:bits==32?0x94c6c4:bits==16?0x94c6c8:bits==15?0x94c6cc:0;if(address!=0)Pc1Startup.put(memory,address,1);}
                index++;if(status!=1)phase=5;break;
            case 5:
                Pc1Startup.put(memory,Pc1StartupMemory.PREFERRED_BPP,Pc1Startup.get(memory,0x94c6c4)!=0?32:Pc1Startup.get(memory,0x94c6c0)!=0?24:Pc1Startup.get(memory,0x94c6c8)!=0?16:Pc1Startup.get(memory,0x94c6cc)!=0?15:16);index=0;phase=6;break;
            case 6:
                int found=io.enumDisplaySettings(index,local);
                if(local.get(108)==800&&local.get(112)==600&&local.get(104)==Pc1Startup.get(memory,Pc1StartupMemory.PREFERRED_BPP)){
                    int count=Pc1Startup.get(memory,Pc1StartupMemory.REFRESH_COUNT);if(count>=30){phase=7;result=0;break;}
                    // A signed negative count is not silently clamped: the raw
                    //address either reaches a known owner or fails precisely.
                    Pc1Startup.put(memory,Pc1StartupMemory.REFRESH+count*4,local.get(120));Pc1Startup.put(memory,Pc1StartupMemory.REFRESH_COUNT,count+1);
                }
                index++;if(found!=1){phase=7;result=0;}break;
            default:throw new IOException("PC1 display phase");
            }}catch(IOException|RuntimeException failure){phase=-1;throw failure;}finally{busy=false;}
        }
        public void save(DataOutput out)throws IOException{if(busy||phase<0)throw new IOException("PC1 display frame outside snapshot boundary");out.writeInt(0x50315343);out.writeInt(phase);out.writeInt(width);out.writeInt(height);out.writeInt(bpp);out.writeInt(index);out.writeInt(result);out.writeLong(dc);local.save(out);}
    }
    public Frame load(DataInput in,Pc1Startup.Adoption adoption)throws IOException{if(in.readInt()!=0x50315343)throw new IOException("PC1 screen frame format");Frame f=new Frame();f.phase=in.readInt();f.width=in.readInt();f.height=in.readInt();f.bpp=in.readInt();f.index=in.readInt();f.result=in.readInt();long dc=in.readLong();f.local=Buffer.load(in);if(f.phase<0||f.phase>7)throw new IOException("PC1 screen frame phase");f.dc=dc==0?0:Pc1Startup.adopt(adoption,"desktop-dc",dc);return f;}
}
