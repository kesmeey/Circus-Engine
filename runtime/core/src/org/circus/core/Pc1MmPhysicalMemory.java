package org.circus.core;
import java.io.*;import java.nio.*;import java.util.*;
/**Seven shared physical islands. Logical owners keep their codecs; this
 *owner saves only88 formerly preferred-image neighbour bytes, never padding.
 *An unadmitted byte has storage but no read/write permission. */
public final class Pc1MmPhysicalMemory {
 public static final int READ=1,WRITE=2;private static final int FORMAT=0x50314d4d,VERSION=1;
 private static final int[][] ISLANDS={{0x42a048,0x42fcb0},{0x43dbc0,0x43dbe8},{0x43de74,0x43deac},{0x455570,0x48d580},{0xbb8550,0xbc12d0},{0xbc1d60,0xbc5da8},{0xbc5f7c,0xbd9fb4}};
 private static final int[][] RAW={{0x42fcac,4},{0x43de7c,32},{0x43dea4,8},{0x455570,4},{0x48d57c,4},{0xbc1d68,16},{0xbc1d80,8},{0xbc1da0,4},{0xbc5f7c,4},{0xbd9fb0,4}};
 /**Native provider proves physical address and emits one actualDWORD access.
  *No locks/Interlocked semantics are introduced by this interface. */
 public interface Platform {long address(ByteBuffer allocation)throws IOException;int readInt32(ByteBuffer allocation,int offset)throws IOException;void writeInt32(ByteBuffer allocation,int offset,int value)throws IOException;}
 private final Pc1NativeMemory memory;private final Span[] spans=new Span[ISLANDS.length];private Platform platform;private String preferredIdentity="";private Image pending;
 private final class Span {
  final int base,length;final ByteBuffer allocation,access;int origin;boolean borrowed;
  Span(int index){base=ISLANDS[index][0];length=ISLANDS[index][1]-base;allocation=ByteBuffer.allocateDirect(length+4095).order(ByteOrder.LITTLE_ENDIAN);access=ByteBuffer.allocateDirect(length);}
  void seal()throws IOException{if(platform==null)return;long physical=platform.address(allocation);if(physical==0)throw new IOException("PC1 shared physical allocation has no address");int next=(int)(((base&0xfffL)-(physical&0xfffL))&0xfffL);if(next!=origin){if(borrowed)throw new IOException("PC1 shared island was borrowed before native alignment proof");byte[] values=new byte[length];for(int n=0;n<length;n++)values[n]=allocation.get(origin+n);for(int n=0;n<length;n++)allocation.put(next+n,values[n]);origin=next;}if(((physical+origin)&0xfffL)!=(base&0xfffL))throw new IOException("PC1 shared physical alignment proof failed");}
  void check(int at,int count,int permission)throws IOException{if(at<0||count<0||at>length-count)throw new IOException("PC1 physical word leaves known island");for(int n=0;n<count;n++)if((access.get(at+n)&permission)!=permission)throw new IOException("PC1 shared byte is unknown/readonly at0x"+Integer.toHexString(base+at+n));}
  int word(int at)throws IOException{check(at,4,READ);return platform==null?allocation.getInt(origin+at):platform.readInt32(allocation,origin+at);}
  void word(int at,int value)throws IOException{check(at,4,WRITE);if(platform==null)allocation.putInt(origin+at,value);else platform.writeInt32(allocation,origin+at,value);}
 }
 public final class Slice implements Pc1NativeMemory.Region {
  private final Span span;private final int at,length;private Slice(Span span,int at,int length){this.span=span;this.at=at;this.length=length;}
  public int address(){return span.base+at;}public int length(){return length;}
  private void bounds(int offset,int count)throws IOException{if(offset<0||count<0||offset>length-count)throw new IOException("PC1 physical logical-slice extent");}
  public int read(int offset)throws IOException{bounds(offset,1);span.check(at+offset,1,READ);return span.allocation.get(span.origin+at+offset)&255;}
  public void write(int offset,int value)throws IOException{bounds(offset,1);span.check(at+offset,1,WRITE);span.allocation.put(span.origin+at+offset,(byte)value);}
  public int readInt32(int offset)throws IOException{bounds(offset,4);return span.word(at+offset);}
  public void writeInt32(int offset,int value)throws IOException{bounds(offset,4);span.word(at+offset,value);}
  public byte[] bytes()throws IOException{byte[] out=new byte[length];for(int n=0;n<length;n++)out[n]=(byte)read(n);return out;}
  public void restore(byte[] values)throws IOException{if(values==null||values.length!=length)throw new IOException("PC1 physical slice image shape");span.check(at,length,WRITE);for(int n=0;n<length;n++)span.allocation.put(span.origin+at+n,values[n]);}
  /**Expose onlythis exact slice. Once exposed, relocation is forbidden. */
  public ByteBuffer borrow()throws IOException{span.borrowed=true;ByteBuffer out=span.allocation.duplicate().order(ByteOrder.LITTLE_ENDIAN);out.position(span.origin+at);out.limit(span.origin+at+length);return out.slice().order(ByteOrder.LITTLE_ENDIAN);}
 }
 public Pc1MmPhysicalMemory(Pc1NativeMemory memory)throws IOException{if(memory==null)throw new IOException("PC1 physical islands require canonical bus");this.memory=memory;}
 private static boolean in(int address,int length,int base,int end){long at=address&0xffffffffL;return length>=0&&at>=(base&0xffffffffL)&&at+length<=(end&0xffffffffL);}
 public static boolean contains(int address,int length){for(int[] range:ISLANDS)if(in(address,length,range[0],range[1]))return true;return false;}
 private Span span(int address,int length,boolean create)throws IOException{for(int n=0;n<ISLANDS.length;n++)if(in(address,length,ISLANDS[n][0],ISLANDS[n][1])){if(spans[n]==null&&create){final Span made=new Span(n);spans[n]=made;made.seal();for(int[] range:RAW)if(in(range[0],range[1],made.base,made.base+made.length)){final Slice raw=new Slice(made,range[0]-made.base,range[1]);memory.bind(range[0],range[1],raw);}}return spans[n];}return null;}
 public Slice slice(int address,int length)throws IOException{Span span=span(address,length,true);if(span==null)throw new IOException("PC1 requested physical slice crosses island/CRT boundary");return new Slice(span,address-span.base,length);}
 /**Single initial typed-owner seed, prior to publishing the logical region. */
 public Slice claim(int address,byte[] initial,boolean writable)throws IOException{if(initial==null)throw new IOException("PC1 physical owner seed missing");Slice view=slice(address,initial.length);for(int n=0;n<initial.length;n++)if(view.span.access.get(view.at+n)!=0)throw new IOException("PC1 physical byte has two logical initial owners");for(int n=0;n<initial.length;n++){view.span.allocation.put(view.span.origin+view.at+n,initial[n]);view.span.access.put(view.at+n,(byte)(READ|(writable?WRITE:0)));}return view;}
 public boolean hasWord(int address){try{Span s=span(address,4,false);if(s==null)return false;for(int n=0;n<4;n++)if((s.access.get(address-s.base+n)&READ)==0)return false;return true;}catch(IOException impossible){return false;}}
 /**A word touching any physically admitted byte cannot fall back to folding
  *logical regions. Entirely unclaimed explicit fixture regions retain their
  *declared byte-only contract; they are not a production MM bridge. */
 public boolean managesWord(int address){try{Span s=span(address,4,false);if(s==null)return false;for(int n=0;n<4;n++)if(s.access.get(address-s.base+n)!=0)return true;return false;}catch(IOException impossible){return false;}}
 public int readInt32(int address)throws IOException{Span span=span(address,4,false);if(span==null)throw new IOException("PC1 cross-physical/CRT DWORD required");return span.word(address-span.base);}
 public void writeInt32(int address,int value)throws IOException{Span span=span(address,4,false);if(span==null)throw new IOException("PC1 cross-physical/CRT DWORD required");span.word(address-span.base,value);}
 /**One-time pre-native relocation. No Cview or activeworker mayexist yet. */
 public void prepareNative(Platform required)throws IOException{if(required==null)throw new IOException("PC1 native physical access provider missing");if(platform!=null&&platform!=required)throw new IOException("PC1 physical native provider already bound");platform=required;for(Span span:spans)if(span!=null)span.seal();}
 public int[][] islands(){int[][] copy=new int[ISLANDS.length][];for(int n=0;n<copy.length;n++)copy[n]=ISLANDS[n].clone();return copy;}
 /**Allocate declarations for backend-owned slices without admitting bytes. */
 public void declareBackendIslands()throws IOException{for(int[] range:ISLANDS)span(range[0],range[1]-range[0],true);}
 public ByteBuffer accessMask(int address)throws IOException{Span span=span(address,1,false);if(span==null||address!=span.base)throw new IOException("PC1 full physical island mask missing");return span.access.duplicate();}
 void admitPreferred(Pc1StaticImage image)throws IOException{
  String identity=image.preferredIdentity();if(!preferredIdentity.isEmpty()&&!preferredIdentity.equals(identity))throw new IOException("PC1 physical neighbor PE identity changed");
  if(pending!=null&&!pending.identity.equals(identity))throw new IOException("PC1 restored physical neighbor PE phase mismatch");
  int index=0;for(int[] raw:RAW)for(int n=0;n<raw[1];n++,index++)if(pending!=null&&pending.access[index]!=0&&pending.access[index]!=image.preferredAccess(raw[0]+n))throw new IOException("PC1 restored neighbor permission/provenance mismatch");
  preferredIdentity=identity;index=0;for(int[] raw:RAW)for(int n=0;n<raw[1];n++,index++){Span span=span(raw[0]+n,1,false);if(span==null)continue;int access=pending==null?image.preferredAccess(raw[0]+n):pending.access[index],at=raw[0]+n-span.base;span.access.put(at,(byte)access);if(access!=0){int value=pending==null?image.preferredByte(raw[0]+n):pending.values[index]&255;span.allocation.put(span.origin+at,(byte)value);}}
  pending=null;
 }
 private static int rawBytes(){int count=0;for(int[] range:RAW)count+=range[1];return count;}
 public static final class Image {private final String identity;private final byte[] access,values;private Image(String identity,byte[] access,byte[] values){this.identity=identity;this.access=access;this.values=values;}}
 public void save(DataOutput out)throws IOException{out.writeInt(FORMAT);out.writeInt(VERSION);out.writeUTF(pending!=null?pending.identity:preferredIdentity);int index=0;for(int[] raw:RAW)for(int n=0;n<raw[1];n++,index++){Span span=span(raw[0]+n,1,false);int access=pending!=null?pending.access[index]:span==null?0:span.access.get(raw[0]+n-span.base)&255;out.writeByte(access);if(access!=0)out.writeByte(pending!=null?pending.values[index]:span.allocation.get(span.origin+raw[0]+n-span.base));}}
 public static Image loadImage(DataInput in)throws IOException{if(in.readInt()!=FORMAT||in.readInt()!=VERSION)throw new IOException("PC1 physical neighborhood codec");String identity=in.readUTF();if(identity.length()>1024)throw new IOException("PC1 physical PE provenance length");byte[] flags=new byte[rawBytes()],values=new byte[flags.length];for(int n=0;n<flags.length;n++){int access=in.readUnsignedByte();if(access!=0&&access!=READ&&access!=(READ|WRITE))throw new IOException("PC1 physical permission mask");flags[n]=(byte)access;if(access!=0){if(identity.isEmpty())throw new IOException("PC1 known physical neighbor has no PE source");values[n]=in.readByte();}}return new Image(identity,flags,values);}
 /**Stages raw bytes only. They remain unknown until samePE source admits
  *their permissions; logical-owner snapshots are restored independently. */
 public void requireRestoreImage(Image image)throws IOException{
  if(image==null)throw new IOException("PC1 physical neighborhood image missing");if(!preferredIdentity.isEmpty()&&!preferredIdentity.equals(image.identity))throw new IOException("PC1 physical restore PE provenance/phase mismatch");Pc1StaticImage target=memory.staticImage();if(target!=null){if(!target.preferredIdentity().equals(image.identity))throw new IOException("PC1 physical restore PE phase mismatch");int index=0;for(int[] raw:RAW)for(int n=0;n<raw[1];n++,index++)if(image.access[index]!=0&&image.access[index]!=target.preferredAccess(raw[0]+n))throw new IOException("PC1 physical restore permission/provenance mismatch");}
 }
 public void restore(Image image)throws IOException{requireRestoreImage(image);int index=0;for(int[] raw:RAW){boolean active=false;for(int n=0;n<raw[1];n++)active|=image.access[index+n]!=0;if(active)span(raw[0],raw[1],true);index+=raw[1];}pending=image;if(memory.staticImage()!=null)admitPreferred(memory.staticImage());}
}
