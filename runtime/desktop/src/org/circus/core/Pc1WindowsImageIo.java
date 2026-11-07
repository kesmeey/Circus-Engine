package org.circus.core;

import java.io.*;
import java.nio.file.*;
import java.util.*;

/** Self-authored Windows ANSI readonly CRT and real uninitialized allocation
 * leaf. No original executable mapping/execution. Allocation bytes are those
 * of this process, not a prediction of another process's heap history. */
public final class Pc1WindowsImageIo implements Pc1CrxDecoder.Allocator,Closeable {
    private static Path loadedLibrary;
    private final Set<Integer> files=new HashSet<>();
    private final Set<Block> blocks=new HashSet<>();
    private boolean closed;
    public Pc1WindowsImageIo(Path library)throws IOException{
        if(library==null)throw new IOException("PC1 image IO JNI library missing");Path selected=library.toAbsolutePath().normalize();
        synchronized(Pc1WindowsImageIo.class){if(loadedLibrary==null){if(!Files.isRegularFile(selected))throw new FileNotFoundException(selected.toString());try{System.load(selected.toString());}catch(UnsatisfiedLinkError failure){throw new IOException("PC1 image IO JNI load failed",failure);}loadedLibrary=selected;}else if(!loadedLibrary.equals(selected))throw new IOException("PC1 image IO JNI already loaded from another file");}
    }
    private void ready()throws IOException{if(closed)throw new IOException("PC1 image IO owner closed");}
    /** Exactly readonly binary _open. Raw invalid ACP bytes reach theA API. */
    public Pc1CrxDecoder.Input open(byte[] rawPath)throws IOException{
        ready();if(rawPath==null)throw new IOException("PC1 image ANSI path missing");int descriptor=openNative(rawPath);if(descriptor==-1)return null;files.add(descriptor);
        return new Pc1CrxDecoder.Input(){public int descriptor(){return descriptor;}public int read(byte[] raw,int offset,int requested)throws IOException{ready();if(!files.contains(descriptor))throw new IOException("PC1 image descriptor no longer owned");if(raw==null||offset<0||requested<0||(long)offset+requested>raw.length)throw new IOException("PC1 image CRT read buffer range");return readNative(descriptor,raw,offset,requested);}};
    }
    public int close(Pc1CrxDecoder.Input input)throws IOException{ready();if(input==null||!files.remove(input.descriptor()))throw new IOException("PC1 image close descriptor not owned");return closeNative(input.descriptor());}
    private final class Block implements Pc1CrxDecoder.Allocation {
        final long handle;final boolean image;final byte[] bytes;boolean released;
        Block(long handle,boolean image,int length)throws IOException{this.handle=handle;this.image=image;bytes=allocationBytesNative(handle,length);}
        public byte[] bytes(){return bytes;}
        public void close()throws IOException{if(released)throw new IOException("PC1 image allocation already released");releaseNative(handle,image,bytes);released=true;blocks.remove(this);}
    }
    private Pc1CrxDecoder.Allocation allocate(int count,boolean image)throws IOException{ready();if(count<1||count>64*1024*1024)throw new IOException("PC1 image allocation host known bound");long pointer=allocateNative(count,image);if(pointer==0)return null;Block block;try{block=new Block(pointer,image,count);}catch(IOException|RuntimeException failure){releaseNative(pointer,image,null);throw failure;}blocks.add(block);return block;}
    public Pc1CrxDecoder.Allocation image(int bytes)throws IOException{return allocate(bytes,true);}
    public Pc1CrxDecoder.Allocation temporary(int bytes)throws IOException{return allocate(bytes,false);}
    public int openCount(){return files.size();}
    public int allocationCount(){return blocks.size();}
    public void close()throws IOException{if(closed)return;IOException failure=null;for(Block block:new ArrayList<>(blocks))try{block.close();}catch(IOException error){if(failure==null)failure=error;else failure.addSuppressed(error);}for(int descriptor:files)closeNative(descriptor);files.clear();closed=true;if(failure!=null)throw failure;}
    private static native int openNative(byte[] path)throws IOException;
    private static native int readNative(int descriptor,byte[] data,int offset,int length)throws IOException;
    private static native int closeNative(int descriptor);
    private static native long allocateNative(int length,boolean image);
    private static native byte[] allocationBytesNative(long pointer,int length)throws IOException;
    private static native void releaseNative(long pointer,boolean image,byte[] bytes)throws IOException;
}
