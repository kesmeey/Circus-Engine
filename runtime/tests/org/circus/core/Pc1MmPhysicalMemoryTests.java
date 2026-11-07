package org.circus.core;
import java.io.*;import java.nio.*;import java.util.*;
/**Synthetic PE permissions and explicit physical-operation recorder. Actual
 *address alignment and concurrent MM are separate desktop audits. */
public final class Pc1MmPhysicalMemoryTests {
 private static int checks;private interface Work{void run()throws Exception;}
 private static void check(boolean b,String why){checks++;if(!b)throw new AssertionError(why);}
 private static void reject(Work w,String text)throws Exception{boolean b=false;try{w.run();}catch(IOException e){b=e.getMessage().contains(text);}check(b,text);}
 private static Pc1NativeMemory bus(){return new Pc1NativeMemory(Pc1NativeFrame.zeroBss(),Pc1CodeBuffer.zeroBss(),new Pc1NativeMemory.SettingsBytes(){byte[] b=new byte[1748];public int read(int p){return b[p]&255;}public void write(int p,int v){b[p]=(byte)v;}});}
 private static void put(byte[] b,int at,int v,int n){for(int k=0;k<n;k++)b[at+k]=(byte)(v>>>(k*8));}
 private static Pc1StaticImage.Seed seed(int salt)throws IOException{byte[] b=new byte[1024];put(b,0,0x5a4d,2);put(b,0x3c,0x80,4);put(b,0x80,0x4550,4);put(b,0x84,0x14c,2);put(b,0x86,1,2);put(b,0x94,224,2);int o=0x98;put(b,o,0x10b,2);put(b,o+28,0x400000,4);put(b,o+56,0x7dd000,4);int h=o+224;put(b,h+8,0x7b133c,4);put(b,h+12,0x2a000,4);put(b,h+16,512,4);put(b,h+20,512,4);put(b,h+36,0xc0000040,4);b[512]=(byte)salt;return Pc1StaticImage.Seed.fixture(b,Binary.sha256(b),"MM synthetic preferred-data permission V1");}
 private static byte[] saved(Pc1MmPhysicalMemory m)throws IOException{ByteArrayOutputStream b=new ByteArrayOutputStream();m.save(new DataOutputStream(b));return b.toByteArray();}
 private static Pc1MmPhysicalMemory.Image image(byte[] b)throws IOException{return Pc1MmPhysicalMemory.loadImage(new DataInputStream(new ByteArrayInputStream(b)));}
 private static void claim(Pc1NativeMemory b,int at,byte[] seed)throws IOException{Pc1MmPhysicalMemory.Slice s=b.mmPhysicalMemory().claim(at,seed,true);b.bind(at,seed.length,s);}
 private static void words()throws Exception{
  Pc1NativeMemory b=bus();Pc1MmPhysicalMemory m=b.mmPhysicalMemory();claim(b,0x43dbd8,new byte[]{1,2,3,4});claim(b,0x43dbdc,new byte[]{5,6,7,8});final int[] operations=new int[2];
  m.prepareNative(new Pc1MmPhysicalMemory.Platform(){public long address(ByteBuffer raw){return 0x100000;}public int readInt32(ByteBuffer raw,int at){operations[0]++;return raw.getInt(at);}public void writeInt32(ByteBuffer raw,int at,int v){operations[1]++;raw.putInt(at,v);}});
  for(int k=0;k<5;k++){int at=0x43dbd8+k;int want=0;for(int n=0;n<4;n++)want|=b.readAddress(at+n)<<(n*8);check(b.readInt32(at)==want,"one unaligned/cross-owner word value");check(operations[0]==k+1,"exactly one physical read");b.writeInt32(at,0x6d513725+k);check(operations[1]==k+1,"exactly one physical write");for(int n=0;n<4;n++)check(b.readAddress(at+n)==((0x6d513725+k)>>>(n*8)&255),"byte alias reaches physical word");}
  reject(()->b.writeInt32(0x43dbd6,99),"unknown/readonly");check(b.readAddress(0x43dbd8)==0x25,"failed mixed-known word makes no partial store");
  reject(()->m.claim(0x43dbdc,new byte[4],true),"two logical");reject(()->m.slice(0xbc5da5,4),"CRT boundary");reject(()->b.readAddress(0x42fcac),"unowned");m.slice(0x42fca8,8);reject(()->b.readAddress(0x42fcac),"unknown/readonly");
  final byte[] fixture={9,8,7,6};b.bind(0xbc12c8,4,new Pc1NativeMemory.Region(){public int read(int n){return fixture[n]&255;}public void write(int n,int v){fixture[n]=(byte)v;}});m.slice(0xbb8550,0xbc12d0-0xbb8550);check(b.readInt32(0xbc12c8)==0x06070809,"unclaimed explicit byte-only fixture is not hidden by allocation");check(!m.hasWord(0xbc12c8),"fixture does not prove actual physical admission");
 }
 private static void provenance()throws Exception{
  Pc1NativeMemory b=bus();Pc1MmPhysicalMemory m=b.mmPhysicalMemory();m.declareBackendIslands();byte[] unknown=saved(m);reject(()->b.readAddress(0x42fcac),"unknown/readonly");Pc1StaticImage.Seed s=seed(1);b.finalizeStaticImage(new Pc1StaticImage(s));b.writeAddress(0x42fcac,39);byte[] known=saved(m);reject(()->m.requireRestoreImage(image(unknown)),"phase");reject(()->m.restore(image(unknown)),"phase");check(b.readAddress(0x42fcac)==39&&Arrays.equals(known,saved(m)),"phase rejection changes neither bytes nor codec");
  DataInputStream in=new DataInputStream(new ByteArrayInputStream(known));int magic=in.readInt(),version=in.readInt();String id=in.readUTF();ByteArrayOutputStream blank=new ByteArrayOutputStream();DataOutputStream out=new DataOutputStream(blank);out.writeInt(magic);out.writeInt(version);out.writeUTF(id);for(int n=0;n<88;n++)out.writeByte(0);byte[] masked=blank.toByteArray();m.requireRestoreImage(image(masked));m.restore(image(masked));reject(()->b.readAddress(0x42fcac),"unknown/readonly");check(Arrays.equals(masked,saved(m)),"same-phase unknown masks stay unknown, never seed-known");m.restore(image(known));check(b.readAddress(0x42fcac)==39&&Arrays.equals(known,saved(m)),"same-target raw values restore once");
  Pc1NativeMemory other=bus();Pc1MmPhysicalMemory om=other.mmPhysicalMemory();om.declareBackendIslands();other.finalizeStaticImage(new Pc1StaticImage(seed(2)));byte[] before=saved(om);reject(()->om.requireRestoreImage(image(known)),"provenance");check(Arrays.equals(before,saved(om)),"cross-PE prevalidation is pure");
  Pc1NativeMemory pending=bus();Pc1MmPhysicalMemory pm=pending.mmPhysicalMemory();pm.restore(image(unknown));reject(()->pending.finalizeStaticImage(new Pc1StaticImage(s)),"phase");check(pending.staticImage()==null&&Arrays.equals(unknown,saved(pm)),"pending unknown phase cannot silently gain PE knowledge");
  Pc1NativeMemory excluded=bus();Pc1MmPhysicalMemory em=excluded.mmPhysicalMemory();em.declareBackendIslands();excluded.finalizeStaticImage(new Pc1StaticImage(s,Arrays.asList(new Pc1StaticImage.Exclusion(0x42fcac,4,"explicit unknown state"))));reject(()->excluded.readAddress(0x42fcac),"unknown/readonly");reject(()->em.requireRestoreImage(image(known)),"permission");
 }
 public static int run()throws Exception{checks=0;words();provenance();return checks;}
 public static void main(String[] args)throws Exception{System.out.println("Pc1MmPhysicalMemoryTests PASS "+run());}
}
