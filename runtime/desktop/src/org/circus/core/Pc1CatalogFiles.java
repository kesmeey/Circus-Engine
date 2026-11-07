package org.circus.core;

import java.io.*;

/** Actual WindowsA readonly IO for41ABF0. Caller locals are typed storage;
 *all roots, CRT fields and addressed arguments remain on the real native bus. */
public final class Pc1CatalogFiles implements Pc1CatalogLoader.NativeIo {
    private final Pc1CatalogMemory memory;private final Pc1WindowsImageIo io;private final Pc1NativePaths.Memory globals;private final Pc1ResourcePaths paths;
    public Pc1CatalogFiles(Pc1CatalogMemory memory,Pc1WindowsPaths windows,Pc1WindowsImageIo io)throws IOException{if(memory==null||windows==null||io==null)throw new IOException("PC1 catalog Windows IO requires canonical globals and actual ANSI/CRT owners");this.memory=memory;this.io=io;globals=Pc1NativePaths.memory(memory.nativeMemory());paths=new Pc1ResourcePaths(globals,windows);}
    @Override public Pc1CatalogLoader.Opened open(int category,int name,int extension,int pathCapacity)throws IOException{
        Pc1ResourcePaths.Resolved path=paths.assetTyped(Pc1NativePaths.pointer(globals,category),Pc1NativePaths.pointer(globals,name),Pc1NativePaths.pointer(globals,extension),pathCapacity);Pc1CrxDecoder.Input file=io.open(path.rawPath());if(file==null)return null;
        return new Pc1CatalogLoader.Opened(){public int descriptor(){return file.descriptor();}public int read(Pc1CatalogLoader.Destination destination,int requested)throws IOException{long unsigned=requested&0xffffffffL;if(unsigned>64*1024*1024)throw new IOException("PC1 catalog unsigned CRT read requires a directly mapped native destination for0x"+Integer.toHexString(requested));byte[] buffer=new byte[Math.max(1,(int)unsigned)];int got=file.read(buffer,0,(int)unsigned);for(int n=0;n<got;n++)destination.write(n,buffer[n]);return got;}public int close()throws IOException{return io.close(file);}};
    }
}
