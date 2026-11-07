package org.circus.core;
import java.io.*;

/**418600/4189D0/418A20 over live log/checkpoint/code/global bytes. */
public final class Pc1History {
    public interface Services {
        void stopVoice()throws IOException;
        /**Known initialized local strcpy prefix, including its actual NUL.*/
        void playVoice(byte[] knownLocal)throws IOException; //41CB10
        void redrawText(int selected)throws IOException; //418220
        void storyControls()throws IOException; //416B80
        void activateTextRegions()throws IOException; //412320
    }
    private final Pc1NativeMemory memory;private final Services io;private int depth;
    public Pc1History(Pc1NativeMemory memory,Services services)throws IOException{if(memory==null||services==null)throw new IOException("PC1 history requires canonical bus and every native consumer");this.memory=memory;io=services;}
    public void requireBoundary()throws IOException{if(depth!=0)throw new IOException("PC1 history snapshot during native callback");}
    private int g(int a)throws IOException{return memory.readAddress(a)|memory.readAddress(a+1)<<8|memory.readAddress(a+2)<<16|memory.readAddress(a+3)<<24;}
    private void g(int a,int v)throws IOException{for(int n=0;n<4;n++)memory.writeAddress(a+n,v>>>(n*8));}
    private byte[] localString(int source)throws IOException{ByteArrayOutputStream known=new ByteArrayOutputStream();for(int n=0;n<1024*1024;n++){int value=memory.readAddress(source+n);known.write(value);if(value==0){if(known.size()>1024)throw new IOException("PC1 history418600 strcpy writes beyond known1024-byte native stack local");return known.toByteArray();}}throw new IOException("PC1 history native strlen leaves bounded known source");}
    public void playSelected(int row)throws IOException{depth++;try{
        io.stopVoice();if(row==-1)row=g(0x9d8a1c)-1;int cell=0x9d8a24+row*2,index=(memory.readAddress(cell)|memory.readAddress(cell+1)<<8)&0x7fff;int pointer=Pc1NativeMemory.CODE_BASE+g(Pc1CheckpointMemory.TABLE+index*4);
        int opcode=(byte)memory.readAddress(pointer);byte[] local=localString(pointer+1);pointer+=1+local.length;
        while(opcode!='K'&&opcode!='L'){
            if(opcode=='5'){io.playVoice(local);return;}
            opcode=(byte)memory.readAddress(pointer);local=localString(pointer+1);pointer+=1+local.length;if(opcode=='K')return;
        }
    }finally{depth--;}}
    public void previous()throws IOException{depth++;try{
        int selected=g(0x43d410);if(selected>=0){if(selected>0){g(0x43d410,selected-1);io.redrawText(g(0x43d410));return;}io.storyControls();return;}
        g(0x43d410,g(0x9d8a1c)-2);if(g(0x43d410)>=0){io.redrawText(g(0x43d410));return;}io.storyControls();
    }finally{depth--;}}
    public void next()throws IOException{depth++;try{
        int selected=g(0x43d410);if(selected>=0){if(g(0x9d8a1c)-1<=selected){io.storyControls();return;}g(0x43d410,selected+1);io.redrawText(g(0x43d410));if(g(0x9d8a1c)-1<=g(0x43d410)){g(0x43d410,-1);io.activateTextRegions();}}
    }finally{depth--;}}
}
