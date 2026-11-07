package org.circus.core;
import java.io.*;

/**40ED00 save-directory write admission. The platform must restrict these raw
 *paths to explicitly runtime-owned storage; parsing startup never grants write
 *access to the user's original game. No exists/stat substitute is equivalent. */
public final class Pc1StartupStorage {
    public interface Services {
        int create(byte[] rawNulPath,int attributes)throws IOException; //_lcreat
        void close(int hfile)throws IOException; //_lclose, result ignored
        void delete(byte[] rawNulPath)throws IOException; //DeleteFileA, ignored
        int messageBox(long parent,int rawText,int rawTitle,int flags)throws IOException;
    }
    private final Pc1NativeMemory memory;private final Services io;
    public Pc1StartupStorage(Pc1NativeMemory memory,Services io)throws IOException{if(memory==null||io==null)throw new IOException("PC1 startup storage services missing");this.memory=memory;this.io=io;}
    public Frame begin(long parent){return new Frame(parent);}
    public int test(long parent)throws IOException{Frame f=begin(parent);while(f.active())f.step();return f.result();}
    public final class Frame {
        private int phase,hfile,result;private long parent;private byte[] path;private boolean busy;
        private Frame(long parent){this.parent=parent;}
        public boolean active(){return phase>=0&&phase<5;}public int phase(){return phase;}
        public int result()throws IOException{if(phase!=5)throw new IOException("PC1 startup storage incomplete");return result;}
        public void step()throws IOException{
            if(!active()||busy)throw new IOException("PC1 startup storage frame not active/reentrant");busy=true;
            try{switch(phase){
            case 0:
                byte[] base=Pc1Startup.cString(memory,Pc1StartupMemory.SAVE_ROOT),tail=Pc1Startup.cString(memory,0x43ce04);
                if(base.length+tail.length>=260)throw new IOException("PC1 save-test path reaches unknown native stack backing");path=new byte[base.length+tail.length+1];System.arraycopy(base,0,path,0,base.length);System.arraycopy(tail,0,path,base.length,tail.length);phase=1;break;
            case 1:hfile=io.create(path.clone(),0);phase=hfile==-1?4:2;break;
            case 2:io.close(hfile);phase=3;break;
            case 3:io.delete(path.clone());result=1;phase=5;break;
            case 4:io.messageBox(parent,0x43cdcc,0x4302f4,0x30);result=0;phase=5;break;
            default:throw new IOException("PC1 startup storage phase");
            }}catch(IOException|RuntimeException failure){phase=-1;throw failure;}finally{busy=false;}
        }
        public void save(DataOutput out)throws IOException{if(busy||phase<0)throw new IOException("PC1 startup storage outside snapshot boundary");out.writeInt(0x50315346);out.writeInt(phase);out.writeInt(hfile);out.writeInt(result);out.writeLong(parent);out.writeInt(path==null?-1:path.length);if(path!=null)out.write(path);}
    }
    /** Open HFILE requires a separately staged CRT owner. Negative success
     *values other than -1 are preserved, not normalized into open-failure. */
    public Frame load(DataInput in,Pc1Startup.Adoption adoption)throws IOException{if(in.readInt()!=0x50315346)throw new IOException("PC1 startup storage frame format");Frame f=new Frame(0);f.phase=in.readInt();f.hfile=in.readInt();f.result=in.readInt();f.parent=Pc1Startup.adoptNonzero(adoption,"window",in.readLong());int size=in.readInt();if(size<-1||size>260)throw new IOException("PC1 startup storage path size");if(size!=-1){f.path=new byte[size];in.readFully(f.path);if(size==0||f.path[size-1]!=0)throw new IOException("PC1 startup storage path terminator");}if(f.phase<0||f.phase>5||(f.phase==0)!=(f.path==null))throw new IOException("PC1 startup storage saved phase");if(f.phase==2){if(adoption==null)throw new IOException("PC1 startup requires physical hfile adoption");long hfile=adoption.adopt("hfile",f.hfile);if(hfile!=(int)hfile)throw new IOException("PC1 startup HFILE adoption range");f.hfile=(int)hfile;}return f;}
}
