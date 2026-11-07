package org.circus.core;
import java.io.*;
import java.util.*;
/** Actual current401000/401050/402000/4020B0/401130 byte-for-byte outputs.
 * Heap API returns explicit guest addresses; physical fixture pointers differ.
 * No claim that the guest placement policy is native Windows heap placement. */
public final class Pc1GuestMachineTests {
 private static int checks;private static void check(boolean value,String why){checks++;if(!value)throw new AssertionError(why);}
 private static final class Heap implements Pc1HeapLease.Heap {
  int sequence;public Pc1HeapLease allocate(int size)throws IOException{byte[] bytes=new byte[size];for(int n=0;n<size;n++)bytes[n]=(byte)(0xa5+n);return Pc1HeapLease.observed64(0x12345000000L+(sequence++)*0x10000L,bytes,"private physical leaf, raw32 supplied only by GuestAddressSpace");}
  public boolean release(Pc1HeapLease lease){return true;}
 }
 private static byte[] unhex(String raw){byte[] bytes=new byte[raw.length()/2];for(int n=0;n<bytes.length;n++)bytes[n]=(byte)Integer.parseInt(raw.substring(n*2,n*2+2),16);return bytes;}
 private static void compare(Pc1ImagePool pool,String name,String expected,int[] addresses,String[] data)throws IOException{
  ByteArrayOutputStream b=new ByteArrayOutputStream();pool.save(new DataOutputStream(b));byte[] metadata=Pc1ImagePool.loadImage(new DataInputStream(new ByteArrayInputStream(b.toByteArray()))).rawMetadataBacking();check(Binary.sha256(metadata).equals(expected),name+": all12800 native metadata/name bytes");Pc1GuestAddressSpace space=pool.guestAddressSpace();check(space.mappingCount()==addresses.length,name+": exact live mappings, no retired payload retained");for(int n=0;n<addresses.length;n++){Pc1HeapLease lease=space.allocationAtBase(addresses[n]);check(Arrays.equals(lease.copyBytes(),unhex(data[n])),name+": full allocation including guard at "+Integer.toHexString(addresses[n]));check(lease.nativeAddress32(space)==addresses[n],name+": rawDWORD address backed by contextual mapping");}
 }
 public static int run()throws Exception{
  checks=0;Pc1GuestAddressSpace space=new Pc1GuestAddressSpace(Pc1GuestAddressSpace.Layout.imageHeapV1());Pc1ImagePool pool=new Pc1ImagePool(new Pc1GuestHeap(space,new Heap()));for(int n=0;n<100;n++){int at=n*128+24;pool.writeU8(at,'O');pool.writeU8(at+1,'L');pool.writeU8(at+2,'D');pool.writeU8(at+3,0);for(int b=4;b<104;b++)pool.writeU8(at+b,n);}
  byte[] front=new byte[24];for(int n=0;n<front.length;n++)front[n]=(byte)(31+n*17);pool.importFront(3,2,Pc1HeapLease.observed64(0x888888888L,front,"explicit known privateCPU front fixture"));Pc1Compositor compositor=new Pc1Compositor(pool);Pc1NativeMemory bus=new Pc1NativeMemory(Pc1NativeFrame.zeroBss(),Pc1CodeBuffer.zeroBss(),new Pc1NativeMemory.SettingsBytes(){final byte[] raw=new byte[Pc1NativeSettings.SIZE];public int read(int at){return raw[at]&255;}public void write(int at,int value){raw[at]=(byte)value;}});new Pc1EffectPeMemory(bus);Pc1GraphicsMemory globals=new Pc1GraphicsMemory(bus);globals.bindImageMetadata(compositor);Pc1GraphicsResources resources=new Pc1GraphicsResources(compositor,globals,new Pc1GraphicsResources.Backend(){public void visitCg(byte[] name)throws IOException{throw new IOException("No file lookup in native allocation program");}public Pc1GraphicsResources.Source readImage(byte[] group,byte[] name,byte[] extension)throws IOException{throw new IOException("No file lookup in native allocation program");}public boolean usesCanonicalPool(){return true;}});
  
  compare(pool,"front","5ea129d8f0f6e693bce8ce91f0714ab072aa8f21b108a0717ec89e9497ab87d0",new int[]{0x20000000},new String[]{"1f30415263748596a7b8c9daebfc0d1e2f405162738495a6"});
  check(pool.allocate(3,2,0,0)==20,"actual401050 general slot");
  compare(pool,"allocate","38782bda7ad8b164f251fee727e2ba29a2903d773537c419f676402b07387671",new int[]{0x20000000,0x20000018},new String[]{"1f30415263748596a7b8c9daebfc0d1e2f405162738495a6","a5a6a7a8a9aaabacadaeafb0b1b2b3b4b5b6b7b8b9babbbcbd"});
  check(resources.duplicate(20,compositor)==21,"actual402000 duplicates pixel bytes and resident name");
  compare(pool,"duplicate","12071e706377989899948ecdeb583a4f94edc5db026794ab58f9d7e7e16cf27c",new int[]{0x20000000,0x20000018,0x20000038},new String[]{"1f30415263748596a7b8c9daebfc0d1e2f405162738495a6","a5a6a7a8a9aaabacadaeafb0b1b2b3b4b5b6b7b8b9babbbcbd","a5a6a7a8a9aaabacadaeafb0b1b2b3b4b5b6b7b8b9babbbcbd"});
  check(resources.createLike(20)==22,"actual4020B0 allocates without pixel copy and uses live WORK GBUF literal");
  compare(pool,"createLike","b4c44375e36ceffcf2742b093a86ecca0f5f6d2c5d3689aa61f580c3c59b7b87",new int[]{0x20000000,0x20000018,0x20000038,0x20000058},new String[]{"1f30415263748596a7b8c9daebfc0d1e2f405162738495a6","a5a6a7a8a9aaabacadaeafb0b1b2b3b4b5b6b7b8b9babbbcbd","a5a6a7a8a9aaabacadaeafb0b1b2b3b4b5b6b7b8b9babbbcbd","a5a6a7a8a9aaabacadaeafb0b1b2b3b4b5b6b7b8b9babbbcbd"});
  pool.free(20);
  compare(pool,"free","abe9d55a554d267c871bf27b0121029c8d6999ad29ef0ad599abf63ed4808aac",new int[]{0x20000000,0x20000038,0x20000058},new String[]{"1f30415263748596a7b8c9daebfc0d1e2f405162738495a6","a5a6a7a8a9aaabacadaeafb0b1b2b3b4b5b6b7b8b9babbbcbd","a5a6a7a8a9aaabacadaeafb0b1b2b3b4b5b6b7b8b9babbbcbd"});
  check(pool.allocate(3,2,0,0)==20,"actual401050 reuses free record while Guest policy reuses freed VA");
  compare(pool,"reuse","b4c44375e36ceffcf2742b093a86ecca0f5f6d2c5d3689aa61f580c3c59b7b87",new int[]{0x20000000,0x20000018,0x20000038,0x20000058},new String[]{"1f30415263748596a7b8c9daebfc0d1e2f405162738495a6","a5a6a7a8a9aaabacadaeafb0b1b2b3b4b5b6b7b8b9babbbcbd","a5a6a7a8a9aaabacadaeafb0b1b2b3b4b5b6b7b8b9babbbcbd","a5a6a7a8a9aaabacadaeafb0b1b2b3b4b5b6b7b8b9babbbcbd"});
  return checks;
 }
 public static void main(String[] args)throws Exception{System.out.println("PASS Pc1GuestMachineTests "+run());}
}
