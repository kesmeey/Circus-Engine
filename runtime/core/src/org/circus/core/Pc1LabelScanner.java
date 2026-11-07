package org.circus.core;
import java.io.IOException;

/**4114D0 scans the live raw code and known neighboring owners. This does not
 *use an immutable MES label index or normalize signed native opcode bytes. */
public final class Pc1LabelScanner {
    private final Pc1NativeMemory memory;
    public Pc1LabelScanner(Pc1NativeMemory memory)throws IOException{if(memory==null)throw new IOException("PC1 label scanner byte owner missing");this.memory=memory;}
    private int read(int at)throws IOException{return memory.readAddress(at);}
    private boolean equal(int a,Pc1ByteSource b)throws IOException{for(int n=0;;n++){int x=read(a+n),y=b.unsignedByte(n)&255;if(x!=y)return false;if(x==0)return true;}}
    private int length(int at)throws IOException{for(long n=0;n<0xffffffffL;n++)if(read(at+(int)n)==0)return(int)n;return -2;}
    public int find(int rawName)throws IOException{return find(rawName==0?null:Pc1ByteSource.addressed(memory,rawName));}
    public int find(Pc1ByteSource name)throws IOException{
        if(name==null)return -1;if(name instanceof Pc1ByteSource.Addressed){Pc1ByteSource.Addressed addressed=(Pc1ByteSource.Addressed)name;if(addressed.address()==0)return -1;if(addressed.bus()!=memory)throw new IOException("PC1 label source belongs to another native bus");}
        if((name.unsignedByte(0)&255)==0)return -1;int cursor=2;
        for(;;){
            int opcode=(byte)read(Pc1CodeBuffer.BASE+cursor);
            if(opcode<0x2d){if(opcode==0)return -1;cursor+=3;}
            else if(opcode<0x4a){if(opcode==0x32&&equal(Pc1CodeBuffer.BASE+cursor+1,name))return cursor;cursor+=length(Pc1CodeBuffer.BASE+cursor+1)+2;}
            else if(opcode<0x4e)cursor+=length(Pc1CodeBuffer.BASE+cursor+1)+2;
            else cursor+=9;
        }
    }
}
