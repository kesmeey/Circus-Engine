package org.circus.core;
import java.io.*;import java.nio.file.*;
/**Explicit self-authored admission for component audits. Never called by
 *production construction and never treated as original CRT initialization. */
final class Pc1DirectSoundAuditFixtures {
 private static void put(byte[] b,int at,int v,int count){for(int n=0;n<count;n++)b[at+n]=(byte)(v>>>(8*n));}
 static Pc1NativeMemory bus(){return new Pc1NativeMemory(Pc1NativeFrame.zeroBss(),Pc1CodeBuffer.zeroBss(),new Pc1NativeMemory.SettingsBytes(){byte[] b=new byte[1748];public int read(int n){return b[n]&255;}public void write(int n,int v){b[n]=(byte)v;}});}
 static void crt(Pc1NativeMemory memory)throws IOException{final byte[] raw=new byte[0x210];put(raw,0,0x4403ca,4);put(raw,0x20c,1,4);for(int c:new int[]{9,10,11,12,13,32})raw[10+c*2]=8;memory.bind(0x4403c0,raw.length,new Pc1NativeMemory.Region(){public int read(int n){return raw[n]&255;}public void write(int n,int v){raw[n]=(byte)v;}});}
 static Pc1StaticImage.Seed syntheticSeed()throws IOException{byte[] raw=new byte[0x14400];put(raw,0,0x5a4d,2);put(raw,0x3c,0x80,4);put(raw,0x80,0x4550,4);put(raw,0x84,0x14c,2);put(raw,0x86,1,2);put(raw,0x94,224,2);int o=0x98;put(raw,o,0x10b,2);put(raw,o+28,0x400000,4);put(raw,o+56,0x7dd000,4);int h=o+224;put(raw,h+8,0x7b133c,4);put(raw,h+12,0x2a000,4);put(raw,h+16,0x14000,4);put(raw,h+20,0x400,4);put(raw,h+36,0xc0000040,4);int[] addresses={0x43de7c,0x43de84,0x43de8c,0x43de94,0x43dea4},values={0x61746164,0x20746d66,0x45564157,0x46464952,0x4d435058};for(int n=0;n<addresses.length;n++)put(raw,0x400+addresses[n]-0x42a000,values[n],4);return Pc1StaticImage.Seed.fixture(raw,Binary.sha256(raw),"self-authored header-tag test image; no production/CRT equivalence");}
 static void synthetic(Pc1NativeMemory memory)throws IOException{if(memory.staticImage()==null)memory.finalizeStaticImage(new Pc1StaticImage(syntheticSeed()));}
 static void actual(Pc1NativeMemory memory,Path originalPe)throws IOException{if(memory.staticImage()==null)memory.finalizeStaticImage(new Pc1StaticImage(Pc1StaticImage.Seed.readPreferredPc1Chs(Files.readAllBytes(originalPe))));}
 static void standalone(Pc1DirectSound backend)throws IOException{Pc1NativeMemory tags=bus();synthetic(tags);backend.bindStandaloneHeaderTagsForAudit(tags);}
}
