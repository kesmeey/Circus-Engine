package org.circus.core;
import java.io.*;

/** CurrentCHS4027E0/402890. These functions copy raw bytes, not an immutable
 *settings object, and do not validate a signature or preference values. */
public final class Pc1SettingsIo {
 public static final int SAVE_ROOT=0x94bdc4,SUFFIX=0x42a74c,DEFAULTS=0x42a078,SETTINGS=0x454cd8,BYTES=1748;
 public interface State {int read(int address)throws IOException;void write(int address,int value)throws IOException;
  default int readInt32(int address)throws IOException{int value=0;for(int n=0;n<4;n++)value|=(read(address+n)&255)<<(8*n);return value;}
  default void writeInt32(int address,int value)throws IOException{for(int n=0;n<4;n++)write(address+n,value>>>(8*n));}
 }
 public interface Source {int unsignedByte(int offset)throws IOException;}
 public interface NativeIo {
  /** read:flags8000 and hasModefalse; write:8301/hasModetrue/mode180.
   *Null is the native descriptor−1. An object reporting−1 also takes that
   *same failed-open branch; every other raw descriptor is preserved. */
  Opened open(Source path,int flags,boolean hasMode,int mode)throws IOException;
 }
 public interface Opened {
  int descriptor();
  int read(Destination destination,int requested)throws IOException;
  int write(Source liveSource,int requested)throws IOException;
  int close()throws IOException;
 }
 public static final class Locals {
  public static final int PATH_BYTES=260,READ_BYTES=2008,WRITE_BYTES=260;
  private final boolean reading;private final byte[] raw,known;private boolean busy;
  private Locals(boolean reading){this.reading=reading;raw=new byte[reading?READ_BYTES:WRITE_BYTES];known=new byte[raw.length];}
  public static Locals readFrame(){return new Locals(true);}public static Locals writeFrame(){return new Locals(false);}
  public static Locals fromKnown(boolean reading,byte[] raw,byte[] mask)throws IOException{Locals frame=new Locals(reading);if(raw==null||mask==null||raw.length!=frame.raw.length||mask.length!=raw.length)throw new IOException("PC1 settings locals shape");System.arraycopy(raw,0,frame.raw,0,raw.length);for(int n=0;n<mask.length;n++){if(mask[n]!=0&&mask[n]!=1)throw new IOException("PC1 settings locals known mask");frame.known[n]=mask[n];}return frame;}
  private int at(int offset)throws IOException{if(offset<0||offset>=raw.length)throw new IOException("PC1 settings reaches unknown caller ABI beyond actual locals+0x"+Integer.toHexString(offset));return offset;}
  public int read(int offset)throws IOException{int p=at(offset);if(known[p]==0)throw new IOException("PC1 settings reads unknown native local at+0x"+Integer.toHexString(offset));return raw[p]&255;}
  public void write(int offset,int value)throws IOException{int p=at(offset);raw[p]=(byte)value;known[p]=1;}
  public Source source(final int start){return offset->{long p=(long)start+offset;if(p<Integer.MIN_VALUE||p>Integer.MAX_VALUE)throw new IOException("PC1 settings local source offset overflow");return read((int)p);};}
  public byte[] rawBytes(){return raw.clone();}public byte[] knownMask(){return known.clone();}
 }
 /**Same local1748 body, including known path-overflow/stale bytes. This is
  *NOT the live settings destination; close may still update this exact owner. */
 public static final class Destination {
  private final Locals frame;private Destination(Locals frame){this.frame=frame;}
  private static int at(int offset)throws IOException{if(offset<0||offset>=BYTES)throw new IOException("PC1 native read requested1748-byte destination offset");return Locals.PATH_BYTES+offset;}
  public void write(int offset,int value)throws IOException{frame.write(at(offset),value);}
  public int read(int offset)throws IOException{return frame.read(at(offset));}
  public Source source(){return offset->read(offset);}
 }
 public static final class Result {
  /**Explicit native function result: read0 only for exactcount1748, otherwise
   *−1; write0 after any acquired descriptor irrespective write/close errors. */
  public final int nativeResult,descriptor,readCount,writeCount,closeResult;
  public final boolean opened,readCalled,writeCalled,closeCalled;
  private Result(int value,int fd,boolean rc,int r,boolean wc,int w,boolean cc,int c){nativeResult=value;descriptor=fd;opened=fd!=-1;readCalled=rc;readCount=r;writeCalled=wc;writeCount=w;closeCalled=cc;closeResult=c;}
 }
 private final State state;private final NativeIo io;private int depth;private Pc1NativeMemory nativeBus;
 public Pc1SettingsIo(State state,NativeIo io)throws IOException{if(state==null)throw new IOException("PC1 settings IO requires same canonical memory");this.state=state;this.io=io==null?(path,flags,modePresent,mode)->{throw new IOException("PC1 settings required native_open backend");}:io;}
 public Pc1SettingsIo(final Pc1NativeMemory memory,NativeIo io)throws IOException{this(memory==null?null:new State(){public int read(int at)throws IOException{return memory.readAddress(at);}public void write(int at,int value)throws IOException{memory.writeAddress(at,value);}public int readInt32(int at)throws IOException{return memory.readInt32(at);}public void writeInt32(int at,int value)throws IOException{memory.writeInt32(at,value);}},io);nativeBus=memory;}
 public void requireBus(Pc1NativeMemory memory)throws IOException{if(memory==null||memory!=nativeBus)throw new IOException("PC1 settings IO requires same canonical native bus");}
 public void requireBoundary()throws IOException{if(depth!=0)throw new IOException("PC1 settings snapshot during native local/IO callback");}
 private int b(int at)throws IOException{return state.read(at)&255;}
 private int d(int at)throws IOException{return state.readInt32(at);}
 private void d(int at,int value)throws IOException{state.writeInt32(at,value);}
 private int length(int at)throws IOException{for(long n=0;n<0xffffffffL;n++)if(b(at+(int)n)==0)return(int)n;return -2;}
 private static int length(Locals f,int at)throws IOException{int n=0;while(f.read(at+n)!=0)n++;return n;}
 private static int d(Locals f,int at)throws IOException{int value=0;for(int n=0;n<4;n++)value|=f.read(at+n)<<(n*8);return value;}
 private static void d(Locals f,int at,int value)throws IOException{for(int n=0;n<4;n++)f.write(at+n,value>>>(n*8));}
 private void copyIntoLocal(Locals f,int dest,int source,int count)throws IOException{int n=0;for(;n+4<=count;n+=4)d(f,dest+n,d(source+n));for(;n<count;n++)f.write(dest+n,b(source+n));}
 private void buildPath(Locals frame)throws IOException{
  copyIntoLocal(frame,0,SAVE_ROOT,length(SAVE_ROOT)+1);
  //Native strcat scans the source BEFORE finding destination's currentNUL.
  int count=length(SUFFIX)+1,dest=length(frame,0);copyIntoLocal(frame,dest,SUFFIX,count);
 }
 public static byte[] cstring(Source source)throws IOException{if(source==null)throw new IOException("PC1 settings C source missing");ByteArrayOutputStream out=new ByteArrayOutputStream();for(int n=0;;n++){int value=source.unsignedByte(n)&255;if(value==0)return out.toByteArray();out.write(value);}}
 public Result read()throws IOException{return read(Locals.readFrame());}
 public Result read(Locals frame)throws IOException{
  if(frame==null||!frame.reading||frame.busy)throw new IOException("PC1 settings read requires unused read locals");frame.busy=true;depth++;
  try{
   buildPath(frame);Opened file=io.open(frame.source(0),0x8000,false,0);int fd=file==null?-1:file.descriptor();
   if(fd==-1){copyDefaults();return new Result(-1,-1,false,0,false,0,false,0);}
   int count=file.read(new Destination(frame),BYTES);int closed=file.close();
   if(count!=BYTES){copyDefaults();return new Result(-1,fd,true,count,false,0,true,closed);}
   for(int n=0;n<BYTES;n+=4)d(SETTINGS+n,d(frame,Locals.PATH_BYTES+n));return new Result(0,fd,true,count,false,0,true,closed);
  }finally{depth--;frame.busy=false;}
 }
 private void copyDefaults()throws IOException{for(int n=0;n<BYTES;n+=4)d(SETTINGS+n,d(DEFAULTS+n));}
 public Result write()throws IOException{return write(Locals.writeFrame());}
 public Result write(Locals frame)throws IOException{
  if(frame==null||frame.reading||frame.busy)throw new IOException("PC1 settings write requires unused write locals");frame.busy=true;depth++;
  try{
   buildPath(frame);Opened file=io.open(frame.source(0),0x8301,true,0x180);int fd=file==null?-1:file.descriptor();if(fd==-1)return new Result(-1,-1,false,0,false,0,false,0);
   int written=file.write(offset->b(SETTINGS+offset),BYTES);int closed=file.close();return new Result(0,fd,false,0,true,written,true,closed);
  }finally{depth--;frame.busy=false;}
 }
}
