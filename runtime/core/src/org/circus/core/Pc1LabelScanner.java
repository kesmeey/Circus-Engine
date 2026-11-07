package org.circus.core;
import java.io.IOException;

/**4114D0 scans the live raw code and known neighboring owners. This does not
 *use an immutable MES label index or normalize signed native opcode bytes. */
public final class Pc1LabelScanner {
    private final Pc1NativeMemory memory;
    public Pc1LabelScanner(Pc1NativeMemory memory)throws IOException{if(memory==null)throw new IOException("PC1 label scanner byte owner missing");this.memory=memory;}
    private int read(int at)throws IOException{return memory.readAddress(at);}
    private boolean equal(int a,int b)throws IOException{for(int n=0;n<1024*1024;n++){int x=read(a+n),y=read(b+n);if(x!=y)return false;if(x==0)return true;}throw new IOException("PC1 label strcmp exceeds known host scan bound");}
    private int length(int at)throws IOException{for(int n=0;n<1024*1024;n++)if(read(at+n)==0)return n;throw new IOException("PC1 label strlen exceeds known host scan bound");}
    public int find(int rawName)throws IOException{
        if(rawName==0||read(rawName)==0)return -1;int cursor=2;
        for(int steps=0;steps<1024*1024;steps++){
            int opcode=(byte)read(Pc1CodeBuffer.BASE+cursor);
            if(opcode<0x2d){if(opcode==0)return -1;cursor+=3;}
            else if(opcode<0x4a){if(opcode==0x32&&equal(Pc1CodeBuffer.BASE+cursor+1,rawName))return cursor;cursor+=length(Pc1CodeBuffer.BASE+cursor+1)+2;}
            else if(opcode<0x4e)cursor+=length(Pc1CodeBuffer.BASE+cursor+1)+2;
            else cursor+=9;
        }
        throw new IOException("PC1 label scanner exceeded native host instruction budget");
    }
}
