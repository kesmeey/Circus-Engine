package org.circus.core;
import java.io.*;

/** 40E130, read-only registry admission and exact native path writes. Registry
 *APIs own their partial output writes; success does not validate type/length. */
public final class Pc1StartupRegistry {
    public interface Services {
        Open openCurrentUser(byte[] subkey,int options,int access)throws IOException;
        /** Write any actual API output prefix directly into the canonical bus.
         *The returned type/size are native locals unused after this call. */
        int query(long key,int rawName,int destination,int requestedBytes,int initialType)throws IOException;
        void close(long key)throws IOException;
    }
    public static final class Open {public final int status;public final long key;public Open(int status,long key){this.status=status;this.key=key;}}
    private final Pc1NativeMemory memory;private final Services io;
    public Pc1StartupRegistry(Pc1NativeMemory memory,Services io)throws IOException{if(memory==null||io==null)throw new IOException("PC1 registry services missing");this.memory=memory;this.io=io;}
    public Frame begin(){return new Frame();}
    public int read()throws IOException{Frame f=begin();while(f.active())f.step();return f.result();}
    public final class Frame {
        private int phase,result;private long key;private boolean busy;
        private Frame(){}
        public boolean active(){return phase>=0&&phase<9;}
        public int phase(){return phase;}
        public int result()throws IOException{if(phase!=9)throw new IOException("PC1 registry read not complete");return result;}
        public void step()throws IOException {
            if(!active())throw new IOException("PC1 registry frame not active");if(busy)throw new IOException("PC1 registry reentrant frame");busy=true;
            try{switch(phase){
            case 0:
                // Local HKEY=0 and DataDir head clear happen before RegOpen.
                key=0;memory.writeAddress(Pc1StartupMemory.DATA_DIR,0);
                byte[] subkey=Pc1Startup.cString(memory,0x43cd70,260);
                Open opened=io.openCurrentUser(subkey,0,0x20019);if(opened==null)throw new IOException("PC1 registry open result unknown");key=opened.key;phase=opened.status==0?1:7;break;
            case 1:query(0x43cd68,Pc1StartupMemory.DATA_DIR,260,1,false);phase++;break;
            case 2:query(0x43cd60,Pc1StartupMemory.INST_KEY,260,1,false);phase++;break;
            case 3:query(0x43cd54,0x94c620,4,4,true);phase++;break;
            case 4:query(0x43cd4c,0x94c624,4,4,true);phase++;break;
            case 5:query(0x43cd44,0x94c628,4,4,true);phase++;break;
            case 6:io.close(key);key=0;phase=7;break;
            case 7:
                result=memory.readAddress(Pc1StartupMemory.DATA_DIR)==0?0:1;
                if(result!=0){int n=Pc1Startup.cString(memory,Pc1StartupMemory.DATA_DIR).length;if(n>0&&memory.readAddress(Pc1StartupMemory.DATA_DIR+n-1)=='\\')memory.writeAddress(Pc1StartupMemory.DATA_DIR+n-1,0);}
                Pc1Startup.copyCString(memory,Pc1StartupMemory.SAVE_ROOT,result==0?Pc1StartupMemory.MODULE:Pc1StartupMemory.DATA_DIR);
                Pc1Startup.appendCString(memory,Pc1StartupMemory.SAVE_ROOT,0x43cd30);phase=8;break;
            case 8:
                Pc1Startup.copyCString(memory,0x94c310,Pc1StartupMemory.MODULE);
                Pc1Startup.copyCString(memory,0x94c518,0x43cd2c);
                Pc1Startup.copyCString(memory,0x94c414,0x43cd2c);phase=9;break;
            default:throw new IOException("PC1 registry invalid phase");
            }}catch(IOException|RuntimeException e){phase=-1;throw e;}finally{busy=false;}
        }
        private void query(int name,int target,int size,int type,boolean dword)throws IOException{if(io.query(key,name,target,size,type)!=0){if(dword)Pc1Startup.put(memory,target,0);else memory.writeAddress(target,0);}}
        /** Physical registry handles require adoption; no OS handle is invented
         *or reopened by deserializing the engine frame. */
        public void save(DataOutput out)throws IOException{if(busy||phase<0)throw new IOException("PC1 registry frame outside snapshot boundary");out.writeInt(0x50315247);out.writeInt(phase);out.writeInt(result);out.writeLong(key);}
    }
    public Frame load(DataInput in,Pc1Startup.Adoption adoption)throws IOException{if(in.readInt()!=0x50315247)throw new IOException("PC1 registry frame format");Frame f=new Frame();f.phase=in.readInt();f.result=in.readInt();long key=in.readLong();if(f.phase<0||f.phase>9)throw new IOException("PC1 registry frame phase");f.key=f.phase>=1&&f.phase<=6?Pc1Startup.adoptNonzero(adoption,"registry",key):key;return f;}
}
