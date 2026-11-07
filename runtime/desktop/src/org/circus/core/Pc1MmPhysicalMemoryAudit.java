package org.circus.core;
import java.io.*;import java.nio.*;import java.nio.file.*;import java.util.*;
/**Actual JNI DWORDs over a hidden/deferred, device-free owner. Source PE is
 *read only. This proves alias/alignment/admission, not scheduler atomicity. */
public final class Pc1MmPhysicalMemoryAudit {
 private static int checks;private interface Work{void run()throws Exception;}
 private static void check(boolean b,String why){checks++;if(!b)throw new AssertionError(why);}
 private static void reject(Work w,String text)throws Exception{boolean b=false;try{w.run();}catch(IOException e){b=e.getMessage().contains(text);}check(b,text);}
 private static Pc1Audio.Catalog empty(){return new Pc1Audio.Catalog(){public int musicCount(){return 0;}public int soundCount(){return 0;}public Pc1Audio.Entry music(int n)throws IOException{throw new IOException("unused");}public Pc1Audio.Entry sound(int n)throws IOException{throw new IOException("unused");}};}
 private static Pc1NativeMemory bus(){return new Pc1NativeMemory(Pc1NativeFrame.zeroBss(),Pc1CodeBuffer.zeroBss(),new Pc1NativeMemory.SettingsBytes(){byte[] b=new byte[1748];public int read(int n){return b[n]&255;}public void write(int n,int v){b[n]=(byte)v;}});}
 private static final int[] WORDS={0x42a055,0x42a056,0x42a057,0x42fca5,0x42fca6,0x42fca7,0x42fca9,0x42fcaa,0x42fcab,0x43dbd9,0x43dbda,0x43dbdb,0x43dbdd,0x43dbde,0x43dbdf,0x43dbe1,0x43dbe2,0x43dbe3,0x43de75,0x43de76,0x43de77,0x43de79,0x43de7a,0x43de7b,0x455571,0x455572,0x455573,0x48d579,0x48d57a,0x48d57b,0xbbaaf5,0xbbaaf6,0xbbaaf7,0xbbaaf9,0xbbaafa,0xbbaafb,0xbbab21,0xbbab22,0xbbab23,0xbbab25,0xbbab26,0xbbab27,0xbbab4d,0xbbab4e,0xbbab4f,0xbbab51,0xbbab52,0xbbab53,0xbbab79,0xbbab7a,0xbbab7b,0xbbab7d,0xbbab7e,0xbbab7f,0xbc12c5,0xbc12c6,0xbc12c7,0xbc12c9,0xbc12ca,0xbc12cb,0xbc1d61,0xbc1d62,0xbc1d63,0xbc1d65,0xbc1d66,0xbc1d67,0xbc1d75,0xbc1d76,0xbc1d77,0xbc1d79,0xbc1d7a,0xbc1d7b,0xbc1d7d,0xbc1d7e,0xbc1d7f,0xbc1d85,0xbc1d86,0xbc1d87,0xbc1d9d,0xbc1d9e,0xbc1d9f,0xbc1da1,0xbc1da2,0xbc1da3,0xbc5da1,0xbc5da2,0xbc5da3,0xbc5da5,0xbc5da6,0xbc5da7,0xbc5f7d,0xbc5f7e,0xbc5f7f,0xbd9fad,0xbd9fae,0xbd9faf};
 public static void main(String[] args)throws Exception{
  if(args.length!=2)throw new IOException("Expected matching DLL and readonly CHS PE");byte[] pe=Files.readAllBytes(Paths.get(args[1]));Pc1StaticImage.Seed seed=Pc1StaticImage.Seed.readPreferredPc1Chs(pe);check(Binary.sha256(pe).equals(Pc1StaticImage.TARGET_SHA256),"Exact original preferred bytes");
  try(Pc1DirectSound ds=Pc1DirectSound.deferred(Paths.get(args[0]),null)){
   Pc1NativeMemory b=bus();new Pc1CatalogMemory(b);new Pc1ResourceLiterals(b);Pc1EffectPeMemory effect=new Pc1EffectPeMemory(b);Pc1Audio a=new Pc1Audio(ds,empty());a.bindNativeMemory(b);ds.bindNativeMemory(b);ds.bindEffectPeMemory(effect);Pc1MmPhysicalMemory m=b.mmPhysicalMemory();
   reject(()->b.readInt32(0x42fca9),"unknown/readonly");b.finalizeStaticImage(new Pc1StaticImage(seed));int bridge=0,crt=0;
   for(int at:WORDS){if(!Pc1MmPhysicalMemory.contains(at,4)){reject(()->ds.physicalWordForAudit(at,false,0),"single island");crt++;continue;}
    int original=0;for(int n=0;n<4;n++)original|=b.readAddress(at+n)<<(n*8);check(m.hasWord(at),"All four physical bytes admitted");check(ds.physicalWordForAudit(at,false,0)==original,"C single DWORD sees original full logical/raw neighbor value");
    int next=0x79c35127^at;ds.physicalWordForAudit(at,true,next);check(b.readInt32(at)==next,"JNI producer and bus consumer one physical DWORD");for(int n=0;n<4;n++)check(b.readAddress(at+n)==(next>>>(n*8)&255),"Byte alias reaches native word");
    b.writeInt32(at,next^0x5c3917ab);check(ds.physicalWordForAudit(at,false,0)==(next^0x5c3917ab),"Bus word producer and C consumer share storage");b.writeInt32(at,original);bridge++;
   }
   check(bridge==93&&crt==3,"Exactly93 old cross-owner starts bridged,3 CRT starts remain unknown");
   for(int[] r:m.islands()){ByteBuffer raw=m.slice(r[0],r[1]-r[0]).borrow();check(raw.isDirect(),"Shared direct allocation");}
   ByteBuffer cursor=ds.borrowDecoderRegion(0x42fca8);cursor.putInt(0,0x12345678);check(b.readInt32(0x42fca8)==0x12345678,"Public decoder view aliases same shared cursor");b.writeInt32(0x42fca8,0xbd5fa0);check(cursor.getInt(0)==0xbd5fa0,"Cursor reverse alias");
   check(a.streamingWord(4).readInt32(0)==-1,"Native channel seed remains minus1");b.writeInt32(0x43dbdc,-2);check(a.streamingWord(4).readInt32(0)==-2&&ds.streamState()[0]==-2,"Canonical channel and physical stream consumer same word");b.writeInt32(0x43dbdc,-1);
   byte[] before;ByteArrayOutputStream bytes=new ByteArrayOutputStream();m.save(new DataOutputStream(bytes));before=bytes.toByteArray();b.writeAddress(0x42fcac,81);m.restore(Pc1MmPhysicalMemory.loadImage(new DataInputStream(new ByteArrayInputStream(before))));check(b.readAddress(0x42fcac)==0,"PE neighbor restores in place, not stale JNI allocation");check(ds.physicalWordForAudit(0x42fca9,false,0)==b.readInt32(0x42fca9),"Native pointer retained across raw-neighborhood restore");
   check(!ds.pcmInitialized()&&a.pcmReadyRaw()==0,"No DS/device/startup success inferred from physical bridge");
  }
  System.out.println("Pc1MmPhysicalMemoryAudit PASS "+checks+";93 actual single-DWORD cross-owner starts;3 CRT boundaries required; alignment admission checked by JNI; no EXE/device/window");
 }
}
