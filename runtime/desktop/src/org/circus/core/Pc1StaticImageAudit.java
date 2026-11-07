package org.circus.core;
import java.io.*;
import java.nio.file.*;
import java.util.*;

/** Actual CHS bytes are supplied by the user, never bundled or executed.
 * Original getter/writer goldens run only in a private integer CPU reference. */
public final class Pc1StaticImageAudit {
 private static int checks;private static void check(boolean value,String why){checks++;if(!value)throw new AssertionError(why);}
 private interface Work{void run()throws IOException;}
 private static void reject(Work work,String text)throws IOException{boolean failed=false;try{work.run();}catch(IOException expected){failed=expected.getMessage().contains(text);}check(failed,"precise "+text);}
 private static Pc1NativeMemory.Region bytes(byte[] raw){return new Pc1NativeMemory.Region(){public int read(int offset){return raw[offset]&255;}public void write(int offset,int value){raw[offset]=(byte)value;}};}
 private static Pc1NativeMemory bus()throws IOException{Pc1NativeMemory memory=new Pc1NativeMemory(Pc1NativeFrame.zeroBss(),Pc1CodeBuffer.zeroBss(),new Pc1NativeMemory.SettingsBytes(){final byte[] raw=new byte[Pc1NativeSettings.SIZE];public int read(int at){return raw[at]&255;}public void write(int at,int value){raw[at]=(byte)value;}});memory.bind(0x4449b4,4,bytes(new byte[]{17,34,51,68}));memory.bind(0x9b8e1c,2,bytes(new byte[]{61,73}));return memory;}
 private static int read(Pc1NativeMemory memory,int bank,int index)throws IOException{return bank==90?memory.readBank90(index):memory.readNativeOrdinary(bank,index);}
 private static void write(Pc1NativeMemory memory,int bank,int index,int value)throws IOException{if(bank==90)memory.writeBank90(index,value);else memory.writeNativeOrdinary(bank,index,value);}
 private static final int[][] VECTORS={
  {4366383,90,-5963321,291,0,35},
  {4366383,-1460348482,3,300,0,255},
  {4446479,90,-5883225,-1,253,255},
  {4446479,-1503297354,3,-1,253,0},
  {4458928,90,-5870776,291,0,35},
  {4458928,-2018693305,0,300,0,255},
  {4460544,90,-5869160,-1,0,255},
  {4460544,-1846894597,0,-1,0,0},
  {4475315,90,-5854389,291,0,35},
  {4475315,-1116750009,3,300,0,255},
  {4475316,90,-5854388,-1,17,255},
  {4475316,-1073800336,0,-1,17,0},
  {4475319,90,-5854385,291,68,35},
  {4475319,-1073800336,3,300,68,255},
  {4475320,90,-5854384,-1,0,255},
  {4475320,-2104592487,0,-1,0,0},
  {10094459,90,-235245,291,0,35},
  {10094459,-1717989239,3,300,0,255},
  {10094460,90,-235244,-1,0,255},
  {10094460,-1675039566,0,-1,0,0},
  {10194459,90,-135245,291,0,35},
  {10194459,-1717988239,3,300,0,255},
  {10194460,90,-135244,-1,61,255},
  {10194460,-1675038566,0,-1,61,0},
  {10194461,90,-135243,291,73,35},
  {10194461,-1675038566,1,300,73,255},
  {10194462,90,-135242,-1,0,255},
  {10194462,-1675038566,2,-1,0,0},
  {10323335,90,-6369,291,0,35},
  {10323335,-1975684988,3,300,0,255},
  {10331340,90,1636,-1,0,255},
  {10331340,-1889785562,0,-1,0,0},
  {12430080,90,2100376,291,0,35},
  {12430080,-1460267845,0,300,0,255},
 };
 private static final int[][] READONLY={
  {4198400,139},
  {4229248,131},
  {4358144,64},
  {4362748,124},
  {12435712,0},
 };
 private static void runtimeBoundary(Pc1StaticImage.Seed seed)throws IOException{
  Map<String,byte[]> files=new HashMap<>();files.put("AdvData/DAT/NAMES.DAT",new byte[20]);GameResources game=new GameResources(new GameResources.Storage(){public Set<String> paths(){return files.keySet();}public byte[] read(String path,int limit)throws IOException{byte[] raw=files.get(path);if(raw==null)throw new FileNotFoundException(path);if(raw.length>limit)throw new IOException("fixture file limit");return raw.clone();}public Archive.Source source(String path)throws IOException{final byte[] raw=read(path,Integer.MAX_VALUE);return new Archive.Source(){public long size(){return raw.length;}public byte[] read(long at,int length)throws IOException{Binary.range(at,length,raw.length);return Arrays.copyOfRange(raw,(int)at,(int)at+length);}};}});
  PcHost host=PcHost.pc1BeforeWinMain(new CircusResources(game,Profiles.get("dcpc-2063")),TextCodec.identity());host.attach(new Variables(host,Variables.Model.PC_BYTE));Pc1NativeMemory memory=host.pc1.nativeMemory();Pc1FontMemory font=new Pc1FontMemory();font.bindTo(memory);Pc1GuestAddressSpace guest=new Pc1GuestAddressSpace(Pc1GuestAddressSpace.Layout.imageHeapV1());Pc1ImagePool pool=new Pc1ImagePool(new Pc1GuestHeap(guest,new Pc1HeapLease.Heap(){public Pc1HeapLease allocate(int size)throws IOException{throw new IOException("No actual heap leaf invoked by prewindow owner-boundary fixture");}public boolean release(Pc1HeapLease lease)throws IOException{throw new IOException("No actual heap leaf invoked by prewindow owner-boundary fixture");}}));Pc1Compositor compositor=Pc1Compositor.deferred(pool);host.pc1.graphicsMemory().bindImageMetadata(compositor);host.pc1.graphicsMemory().bindCompositorGlobals(compositor);Pc1StaticImage image=new Pc1StaticImage(seed);memory.finalizeStaticImage(image);
  check(memory.fixedRegions().size()>100,"current Runtime fixed owners plus Font/deferredPool are claimed before static finalization");check(!image.owns(Pc1FontMemory.BASE)&&!image.owns(Pc1ImagePool.BASE)&&!image.owns(0x43e100),"native font/pool/sharedCRT state remain existing canonical owners");check(memory.readAddress(0x43e100)==1&&host.nativeCrtState()==1,"known preWinMain CRT seed uses already bound owner instead of excluded fallback");host.variables.write(90,-6369,0xa7);check(host.variables.read(90,-6369)==0xa7&&memory.readAddress(Pc1FrameLayout.LIVE_BASE-1)==0xa7,"real Variables alias reaches preceding canonical template owner without postinstruction repair");check(!image.owns(Pc1FrameLayout.LIVE_BASE-1),"existing native template bytes immediately preceding frame remain borrowed, not copied");int gapIndex=Pc1FontMemory.BASE-1-(Pc1FrameLayout.LIVE_BASE+Pc1FrameLayout.BANK90_OFFSET);host.variables.write(90,gapIndex,0xa7);check(image.owns(Pc1FontMemory.BASE-1)&&host.variables.read(90,gapIndex)==0xa7,"extreme Variables bank90 alias reaches actual static BSS gap before font owner");memory.writeAddress(Pc1FontMemory.BASE-1,0x59);memory.writeAddress(Pc1FontMemory.BASE,0x31);memory.writeAddress(Pc1ImagePool.BASE+24,'R');check(font.readByte(Pc1FontMemory.BASE)==0x31&&pool.readU8(24)=='R'&&image.changedByteCount()==1,"same-page/adjacent font and image views are never mirrored into COW pages; changed="+image.changedByteCount()+" gap="+image.owns(Pc1FontMemory.BASE-1)+" font="+font.readByte(Pc1FontMemory.BASE)+" name="+pool.readU8(24));check(compositor.rawDirty().left==800&&compositor.rawDirty().right==600&&!pool.occupied(0),"static finalize does not fabricate a DIB or advance graphics startup calls");ByteArrayOutputStream output=new ByteArrayOutputStream();image.save(new DataOutputStream(output));memory.writeAddress(Pc1FontMemory.BASE-1,0);font.writeByte(Pc1FontMemory.BASE,0x72);image.restore(Pc1StaticImage.loadImage(new DataInputStream(new ByteArrayInputStream(output.toByteArray())),seed));check(font.readByte(Pc1FontMemory.BASE)==0x72&&memory.readAddress(Pc1FontMemory.BASE-1)==0x59,"COW restore cannot roll back current canonical font cache");reject(()->memory.readAddress(0x43e104),"CRT");
 }
 public static void main(String[] args)throws Exception{
  if(args.length!=1)throw new IOException("Explicit readonly original CHS EXE required");Path source=Paths.get(args[0]);byte[] raw=Files.readAllBytes(source);String sha=Binary.sha256(raw);Pc1StaticImage.Seed seed=Pc1StaticImage.Seed.readPreferredPc1Chs(raw);check(sha.equals(Pc1StaticImage.TARGET_SHA256),"exact user-owned CHS source fingerprint");raw[0x2a000]=(byte)255;
  for(int[] vector:VECTORS){Pc1NativeMemory memory=bus();Pc1StaticImage image=new Pc1StaticImage(seed);memory.finalizeStaticImage(image);check(read(memory,vector[1],vector[2])==vector[4],"actual native411910 before alias store at"+Integer.toHexString(vector[0]));write(memory,vector[1],vector[2],vector[3]);check(read(memory,vector[1],vector[2])==vector[5]&&memory.readAddress(vector[0])==vector[5],"actual411B10/411910 after alias store at"+Integer.toHexString(vector[0]));if(image.owns(vector[0])){check(image.changedByteCount()==(vector[4]==vector[5]?0:1),"static hole stores one changed byte, not a full page of shadow state");}else check(image.changedByteCount()==0,"existing canonical code/row-pointer owner is not copied into StaticImage");}
  Pc1NativeMemory memory=bus();Pc1StaticImage image=new Pc1StaticImage(seed);memory.finalizeStaticImage(image);for(int[] vector:READONLY){check(memory.readBank90(vector[0]-(Pc1FrameLayout.LIVE_BASE+Pc1FrameLayout.BANK90_OFFSET))==vector[1],"actual native411910 raw RO constant/code byte at"+Integer.toHexString(vector[0]));reject(()->memory.writeAddress(vector[0],13),"readonly section");}
  check(memory.readAddress(0x42a000)==0,"private PE seed does not follow mutation of caller's input clone");for(int address:new int[]{0x426000,0x426254})reject(()->memory.readAddress(address),"loader IAT");for(int address:new int[]{0x43e104,0xbc5dbc,0xbdb324})reject(()->memory.readAddress(address),"CRT");reject(()->memory.readAddress(0xbdb800),"unowned neighboring");check(memory.readAddress(0x441000)==0&&memory.readAddress(0x9da4cc)==0,"actual known data virtual suffix may be read without inventing stack/heap");
  memory.writeAddress(0x4449b8,71);memory.writeAddress(0x9d8587,89);memory.writeAddress(0x4449b5,211);byte[] saved;{ByteArrayOutputStream output=new ByteArrayOutputStream();image.save(new DataOutputStream(output));saved=output.toByteArray();}check(image.changedByteCount()==2&&saved.length<600,"actual PE holes snapshot is sparse and excludes native row-pointer bytes");memory.writeAddress(0x4449b8,0);memory.writeAddress(0x9d8587,0);memory.writeAddress(0x4449b5,97);image.restore(Pc1StaticImage.loadImage(new DataInputStream(new ByteArrayInputStream(saved)),seed));check(memory.readAddress(0x4449b8)==71&&memory.readAddress(0x9d8587)==89&&memory.readAddress(0x4449b5)==97,"restore gap bytes while retaining independent raw pointer owner update");check(memory.readAddress(0x4449b7)==68&&memory.readAddress(0x4449b8)==71,"raw adjacent owner-to-gap read order");check(Binary.sha256(Files.readAllBytes(source)).equals(sha),"original CHS executable unchanged and never run");
  runtimeBoundary(seed);
  System.out.println("sourceSha256="+sha+";phase="+image.phase()+";fixedLayout="+image.layoutFingerprint());System.out.println("Pc1StaticImageAudit PASS "+checks+" actual CHS preferred-section/private-native-alias checks; nativeCPU39programs2014steps; no original EXE execution, no full CRT/WinMain/process claim");
 }
}
