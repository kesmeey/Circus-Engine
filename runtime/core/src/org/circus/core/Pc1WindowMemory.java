package org.circus.core;
import java.io.*;
/** The40 loaded-zero BITMAPINFOHEADER bytes written by406ED0. Scalar OS
 * cells belong to Pc1OsWords; physical DIB/DC lifetime belongs to Windows. */
public final class Pc1WindowMemory {
 public static final int BITMAP_INFO=0x94c2e0,BYTES=40;
 private static final int FORMAT=0x5031574d,VERSION=1;
 private final byte[] raw=new byte[BYTES];private final Pc1NativeMemory bus;
 public Pc1WindowMemory(Pc1NativeMemory bus)throws IOException{if(bus==null)throw new IOException("PC1 window memory requires canonical bus");this.bus=bus;bus.bind(BITMAP_INFO,BYTES,new Pc1NativeMemory.Region(){public int read(int at){return raw[at]&255;}public void write(int at,int value){raw[at]=(byte)value;}});}
 public Pc1NativeMemory nativeMemory(){return bus;}
 public void save(DataOutput out)throws IOException{out.writeInt(FORMAT);out.writeInt(VERSION);out.write(raw);}
 public static final class Image {private final byte[] raw;private Image(byte[] raw){this.raw=raw;}}
 public static Image loadImage(DataInput in)throws IOException{if(in.readInt()!=FORMAT||in.readInt()!=VERSION)throw new IOException("PC1 window memory format");byte[] raw=new byte[BYTES];in.readFully(raw);return new Image(raw);}
 public void restore(Image image)throws IOException{if(image==null)throw new IOException("PC1 window memory image missing");System.arraycopy(image.raw,0,raw,0,BYTES);}
}
