package org.circus.core;

import java.io.*;

/** Current PC1 format1 storage: inverse-alpha A,B,G,R bytes, bottom row first.
 * A255 is invisible; A0 is a full copy. This immutable source is distinct from
 * the mutable format0 RGB24 render target and from ordinary straight BGRA. */
public final class NativeInverseAlphaImage {
    private static final int FORMAT=0x4e494131,MAX_BYTES=64*1024*1024;
    public final int width,height,stride;
    private final Pc1HeapLease.Storage storage;
    private final int base,bottom;
    private NativeInverseAlphaImage(int width,int height,int stride,byte[] bytes)throws IOException {
        dimensions(width,height,stride);if(bytes==null||bytes.length!=(long)stride*height)throw new IOException("Native inverse-alpha storage length");this.width=width;this.height=height;this.stride=stride;final byte[] owned=bytes.clone();storage=new Pc1HeapLease.Storage(){public int length(){return owned.length;}public int read(int at){return owned[at]&255;}public void write(int at,int value){owned[at]=(byte)value;}};base=0;bottom=(height-1)*stride;
    }
    private NativeInverseAlphaImage(int width,int height,int stride,Pc1HeapLease.Storage storage,int base,int bottom)throws IOException{dimensions(width,height,stride);if(storage==null)throw new IOException("Native inverse-alpha borrowed storage missing");this.width=width;this.height=height;this.stride=stride;this.storage=storage;this.base=base;this.bottom=bottom;}
    static NativeInverseAlphaImage view(int width,int height,int stride,Pc1HeapLease.Storage storage,int base,int bottom)throws IOException{return new NativeInverseAlphaImage(width,height,stride,storage,base,bottom);}
    int byteAt(int at)throws IOException{if(storage.length()>=0&&(at<0||at>=storage.length()))throw new IOException("Native inverse-alpha read leaves known backing");return storage.read(at)&255;}
    private static void dimensions(int width,int height,int stride)throws IOException {
        if(width<=0||height<=0||stride<=0||(stride&3)!=0||stride<(long)width*4||(long)stride*height>MAX_BYTES)throw new IOException("Native inverse-alpha dimensions/stride");
    }
    public static NativeInverseAlphaImage fromAbgr32(int width,int height,int stride,byte[] bottomUpBytes)throws IOException{return new NativeInverseAlphaImage(width,height,stride,bottomUpBytes);}
    /** Explicit bridge from a proved straight-alpha, top-down BGRA producer.
     * Current mode0 Crx.plain normalizes native raw A,B,G,R by flipping A and
     * rotating bytes; this operation exactly reverses that normalization.
     * It must not be applied directly to raw CRX predictor/native ABGR bytes. */
    public static NativeInverseAlphaImage fromStraightBgra32(int width,int height,int stride,byte[] topDownBytes)throws IOException {
        dimensions(width,height,stride);if(topDownBytes==null||topDownBytes.length!=(long)stride*height)throw new IOException("Native inverse-alpha BGRA length");byte[] data=new byte[topDownBytes.length];
        for(int y=0;y<height;y++){int source=y*stride,target=(height-1-y)*stride;System.arraycopy(topDownBytes,source,data,target,stride);for(int x=0;x<width;x++){int a=source+x*4,b=target+x*4;data[b]=(byte)(255-(topDownBytes[a+3]&255));data[b+1]=topDownBytes[a];data[b+2]=topDownBytes[a+1];data[b+3]=topDownBytes[a+2];}}
        return new NativeInverseAlphaImage(width,height,stride,data);
    }
    private int offset(int x,int y){if(x<0||x>=width||y<0||y>=height)throw new IndexOutOfBoundsException("Native inverse-alpha pixel");return bottom-y*stride+x*4;}
    public int inverseAlpha(int x,int y){try{return byteAt(offset(x,y));}catch(IOException error){throw new UncheckedIOException(error);}}
    public int rgb(int x,int y){int p=offset(x,y);try{return byteAt(p+1)|byteAt(p+2)<<8|byteAt(p+3)<<16;}catch(IOException error){throw new UncheckedIOException(error);}}
    public byte[] abgr32(){byte[] bytes=new byte[stride*height];try{for(int n=0;n<bytes.length;n++)bytes[n]=(byte)byteAt(base+n);}catch(IOException error){throw new UncheckedIOException(error);}return bytes;}
    public NativeInverseAlphaImage copy()throws IOException{return new NativeInverseAlphaImage(width,height,stride,abgr32());}
    public void save(DataOutput out)throws IOException{byte[] bytes=abgr32();out.writeInt(FORMAT);out.writeInt(width);out.writeInt(height);out.writeInt(stride);out.writeInt(bytes.length);out.write(bytes);}
    public static NativeInverseAlphaImage load(DataInput in)throws IOException {
        if(in.readInt()!=FORMAT)throw new IOException("Native inverse-alpha format");int width=in.readInt(),height=in.readInt(),stride=in.readInt(),length=in.readInt();dimensions(width,height,stride);if(length!=(long)stride*height)throw new IOException("Native inverse-alpha storage length");byte[] bytes=new byte[length];in.readFully(bytes);return new NativeInverseAlphaImage(width,height,stride,bytes);
    }
}
