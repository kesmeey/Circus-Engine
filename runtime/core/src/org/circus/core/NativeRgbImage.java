package org.circus.core;

import java.io.*;

/** Known RGB24 storage: B,G,R bytes, aligned native stride, bottom row first.
 * Padding is owned and preserved. This type does not represent format1 alpha.
 * Native401050 allocates uninitialized memory; zeroed() is an explicit caller
 * operation and is not a claim about native allocation contents. */
public final class NativeRgbImage {
    private static final int FORMAT=0x4e524731,MAX_BYTES=64*1024*1024;
    public final int width,height,stride;
    private final Pc1HeapLease.Storage storage;
    private final int base,bottom;
    private NativeRgbImage(int width,int height,int stride,byte[] bytes)throws IOException {
        dimensions(width,height,stride);
        if(bytes==null||bytes.length!=(long)stride*height)throw new IOException("Native RGB24 storage length");
        this.width=width;this.height=height;this.stride=stride;final byte[] owned=bytes.clone();storage=new Pc1HeapLease.Storage(){public int length(){return owned.length;}public int read(int at){return owned[at]&255;}public void write(int at,int value){owned[at]=(byte)value;}};base=0;bottom=(height-1)*stride;
    }
    private NativeRgbImage(int width,int height,int stride,Pc1HeapLease.Storage storage,int base,int bottom)throws IOException{dimensions(width,height,stride);if(storage==null)throw new IOException("Native RGB24 borrowed storage missing");this.width=width;this.height=height;this.stride=stride;this.storage=storage;this.base=base;this.bottom=bottom;}
    /** Ephemeral view over an existing owner. Metadata is supplied by the
     *canonical pool for each consumer; no image bytes are imported/copied. */
    public static NativeRgbImage view(int width,int height,int stride,Pc1HeapLease lease,int base,int bottom)throws IOException{if(lease==null)throw new IOException("Native RGB24 lease missing");return view(width,height,stride,new Pc1HeapLease.Storage(){public int length(){return lease.length();}public int read(int at)throws IOException{return lease.readU8(at);}public void write(int at,int value)throws IOException{lease.writeU8(at,value);}},base,bottom);}
    static NativeRgbImage view(int width,int height,int stride,Pc1HeapLease.Storage storage,int base,int bottom)throws IOException{return new NativeRgbImage(width,height,stride,storage,base,bottom);}
    int byteAt(int at)throws IOException{if(storage.length()>=0&&(at<0||at>=storage.length()))throw new IOException("Native RGB24 byte read leaves known backing");return storage.read(at)&255;}
    void byteAt(int at,int value)throws IOException{if(storage.length()>=0&&(at<0||at>=storage.length()))throw new IOException("Native RGB24 byte write leaves known backing");storage.write(at,value&255);}
    private static void dimensions(int width,int height,int stride)throws IOException {
        if(width<=0||height<=0||stride<=0||(stride&3)!=0||stride<(long)width*3||(long)stride*height>MAX_BYTES)throw new IOException("Native RGB24 dimensions/stride");
    }
    public static int nativeStride(int width)throws IOException {
        if(width<=0||width>(MAX_BYTES-3)/3)throw new IOException("Native RGB24 width");return (width*3+3)&~3;
    }
    public static NativeRgbImage zeroed(int width,int height)throws IOException {
        int stride=nativeStride(width);dimensions(width,height,stride);return new NativeRgbImage(width,height,stride,new byte[stride*height]);
    }
    /** Explicit storage may use a larger aligned stride when its producer owns
     * those bytes. Native401050's normal stride is nativeStride(width). */
    public static NativeRgbImage fromBgr24(int width,int height,int stride,byte[] bytes)throws IOException{return new NativeRgbImage(width,height,stride,bytes);}
    public static NativeRgbImage fromOpaqueArgb(int width,int height,int[] argb)throws IOException {
        NativeRgbImage image=zeroed(width,height);if(argb==null||argb.length!=(long)width*height)throw new IOException("Native RGB24 ARGB length");
        for(int y=0;y<height;y++)for(int x=0;x<width;x++){int value=argb[y*width+x];if((value>>>24)!=255)throw new IOException("Native RGB24 requires opaque pixels");image.setRgb(x,y,value);}
        return image;
    }
    int offset(int x,int y){return bottom-y*stride+x*3;}
    private void position(int x,int y){if(x<0||x>=width||y<0||y>=height)throw new IndexOutOfBoundsException("Native RGB24 pixel");}
    public int rgb(int x,int y){position(x,y);int p=offset(x,y);try{return byteAt(p)|byteAt(p+1)<<8|byteAt(p+2)<<16;}catch(IOException error){throw new UncheckedIOException(error);}}
    public void setRgb(int x,int y,int rgb){position(x,y);int p=offset(x,y);try{byteAt(p,rgb);byteAt(p+1,rgb>>>8);byteAt(p+2,rgb>>>16);}catch(IOException error){throw new UncheckedIOException(error);}}
    public int[] argb(){int[] values=new int[width*height];for(int y=0;y<height;y++)for(int x=0;x<width;x++)values[y*width+x]=0xff000000|rgb(x,y);return values;}
    public byte[] bgr24(){byte[] bytes=new byte[stride*height];try{for(int n=0;n<bytes.length;n++)bytes[n]=(byte)byteAt(base+n);}catch(IOException error){throw new UncheckedIOException(error);}return bytes;}
    public NativeRgbImage copy()throws IOException{return new NativeRgbImage(width,height,stride,bgr24());}
    public void save(DataOutput out)throws IOException{byte[] bytes=bgr24();out.writeInt(FORMAT);out.writeInt(width);out.writeInt(height);out.writeInt(stride);out.writeInt(bytes.length);out.write(bytes);}
    public static NativeRgbImage load(DataInput in)throws IOException {
        if(in.readInt()!=FORMAT)throw new IOException("Native RGB24 format");int width=in.readInt(),height=in.readInt(),stride=in.readInt(),length=in.readInt();dimensions(width,height,stride);if(length!=(long)stride*height)throw new IOException("Native RGB24 storage length");byte[] bytes=new byte[length];in.readFully(bytes);return new NativeRgbImage(width,height,stride,bytes);
    }
}
