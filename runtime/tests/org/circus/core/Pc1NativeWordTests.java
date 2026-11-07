package org.circus.core;

import java.io.IOException;
import java.nio.ByteBuffer;

/** Physical DWORD consumers must reach a word owner once; split-owner faults
 *retain the native prefix instead of preflighting or rolling it back. */
public final class Pc1NativeWordTests {
    private static int checks;
    private static void check(boolean value,String why){checks++;if(!value)throw new AssertionError(why);}
    private static Pc1NativeMemory bus(){return new Pc1NativeMemory(Pc1NativeFrame.zeroBss(),Pc1CodeBuffer.zeroBss(),new Pc1NativeMemory.SettingsBytes(){final byte[] b=new byte[1748];public int read(int at){return b[at]&255;}public void write(int at,int v){b[at]=(byte)v;}});}
    public static int run()throws Exception{
        checks=0;Pc1NativeMemory memory=bus();final int[] word={0x81234567},calls={0,0};memory.bind(0x50000000,4,new Pc1NativeMemory.Region(){public int read(int offset){throw new AssertionError("DWORD was torn into byte reads");}public void write(int offset,int value){throw new AssertionError("DWORD was torn into byte writes");}public int readInt32(int offset){calls[0]++;return word[0];}public void writeInt32(int offset,int value){calls[1]++;word[0]=value;}});
        check(memory.readInt32(0x50000000)==0x81234567&&calls[0]==1,"one native load reaches one physical DWORD operation");memory.writeInt32(0x50000000,0xfedcba98);check(word[0]==0xfedcba98&&calls[1]==1,"one native store reaches one physical DWORD operation");
        final byte[] prefix={1,2};final int[] byteCalls={0};memory.bind(0x50000010,2,new Pc1NativeMemory.Region(){public int read(int offset){byteCalls[0]++;return prefix[offset]&255;}public void write(int offset,int value){byteCalls[0]++;prefix[offset]=(byte)value;}});boolean fault=false;try{memory.writeInt32(0x50000010,0x88776655);}catch(IOException expected){fault=true;}check(fault&&prefix[0]==0x55&&prefix[1]==0x66&&byteCalls[0]==2,"split-owner write preserves known prefix before unknown third byte");byteCalls[0]=0;fault=false;try{memory.readInt32(0x50000010);}catch(IOException expected){fault=true;}check(fault&&byteCalls[0]==2,"split-owner read performs known prefix before fault");
        final byte[] suffix={0x77,(byte)0x88};memory.bind(0x50000012,2,new Pc1NativeMemory.Region(){public int read(int at){return suffix[at]&255;}public void write(int at,int value){suffix[at]=(byte)value;}});check(memory.readInt32(0x50000010)==0x88776655,"adjacent independent byte owners preserve little-endian DWORD load");memory.writeInt32(0x50000010,0x12345678);check(prefix[0]==0x78&&prefix[1]==0x56&&suffix[0]==0x34&&suffix[1]==0x12,"cross-owner fallback reaches both original owners in address order");
        Pc1EffectPeMemory pe=new Pc1EffectPeMemory(memory);ByteBuffer physical=pe.borrowedDirectView(Pc1EffectPeMemory.COS_DEFAULT,8);physical.putInt(0,0x89abcdef);check(memory.readInt32(Pc1EffectPeMemory.COS_DEFAULT)==0x89abcdef,"physical DSP backing and canonical DWORD load share bytes");memory.writeInt32(Pc1EffectPeMemory.COS_DEFAULT+4,0x76543210);check(physical.getInt(4)==0x76543210,"canonical DWORD store immediately reaches borrowed physical DSP backing");memory.writeAddress(Pc1EffectPeMemory.COS_DEFAULT+1,0x42);check(physical.getInt(0)==0x89ab42ef,"byte-bank alias remains visible to physical DWORD consumer");fault=false;int before=memory.readInt32(Pc1EffectPeMemory.SPAN_BASE);try{memory.writeInt32(Pc1EffectPeMemory.SPAN_BASE,0);}catch(IOException expected){fault=true;}check(fault&&memory.readInt32(Pc1EffectPeMemory.SPAN_BASE)==before,"read-only PE word rejects before changing physical bytes");return checks;
    }
    public static void main(String[] args)throws Exception{System.out.println("PASS "+run()+" native word checks");}
}
