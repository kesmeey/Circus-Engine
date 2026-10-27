package org.circus.core;

import java.io.*;

/** DC2DM's verified packed persistent memory. Live platform queries stay explicit. */
public final class Dc2State implements Variables.Special {
    // Native 78F4B4..78F631: 100 bytes, 400 music bits, 1200 CG bits, 650 scene bits.
    private final byte[] memory=new byte[382];
    private int result;public boolean persistenceDirty;
    public void indirectResult(int value){result=value;}
    private static int bitBase(int bank){return bank==96?100:bank==97?150:bank==98?300:-1;}
    private static int bitCount(int bank){return bank==96?400:bank==97?1200:650;}
    private static void index(int value,int length)throws IOException{if(value<0||value>=length)throw new IOException("DC2DM variable index outside verified memory: "+value);}
    public int read(int bank,int index)throws IOException{
        if(bank==100){index(index,memory.length);return memory[index]&255;}
        int base=bitBase(bank);if(base>=0){index(index,bitCount(bank));return (memory[base+index/8]>>>(index&7))&1;}
        if(bank==99&&index==255)return result;
        throw new IOException("Unimplemented DC2DM state query "+bank+":"+index);
    }
    public void write(int bank,int index,int value)throws IOException{
        if(index<0)throw new IOException("Negative DC2DM variable index");
        if(bank==100){if(index>=100)return;memory[index]=(byte)value;persistenceDirty=true;return;}
        int base=bitBase(bank);if(base>=0){if(index>=bitCount(bank))return;int p=base+index/8,mask=1<<(index&7);memory[p]=(byte)(value==0?memory[p]&~mask:memory[p]|mask);return;}
        throw new IOException("Unimplemented DC2DM state effect "+bank+":"+index);
    }
    public void save(DataOutput out)throws IOException{out.writeInt(0x444d5331);out.write(memory);out.writeInt(result);out.writeBoolean(persistenceDirty);}
    public void load(DataInput in)throws IOException{if(in.readInt()!=0x444d5331)throw new IOException("DC2DM state version mismatch");in.readFully(memory);result=in.readInt();persistenceDirty=in.readBoolean();}
}
