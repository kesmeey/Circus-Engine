package org.circus.core;

import java.io.*;

/** Actual WindowsA readonly IO for41ABF0. Roots/literals are addressed live
 *owners. Path arguments remain pointers until40E5A0 actually reads them. */
public final class Pc1CatalogFiles implements Pc1CatalogLoader.NativeIo {
    private final Pc1CatalogMemory memory;
    private final Pc1WindowsPaths windows;
    private final Pc1WindowsImageIo io;
    public Pc1CatalogFiles(Pc1CatalogMemory memory,Pc1WindowsPaths windows,Pc1WindowsImageIo io)throws IOException{if(memory==null||windows==null||io==null)throw new IOException("PC1 catalog Windows IO requires canonical globals and actual ANSI/CRT owners");this.memory=memory;this.windows=windows;this.io=io;}
    private final class Scratch implements Pc1NativePaths.Memory {
        static final int ADDRESS=0xe0000000;final byte[] bytes;final boolean[] known;
        Scratch(int size)throws IOException{if(size<1||size>1024*1024)throw new IOException("PC1 catalog path scratch bound");bytes=new byte[size];known=new boolean[size];}
        int offset(int address){long at=(address&0xffffffffL)-(ADDRESS&0xffffffffL);return at>=0&&at<bytes.length?(int)at:-1;}
        public int read(int address)throws IOException{int at=offset(address);if(at<0)return memory.read(address);if(!known[at])throw new IOException("PC1 catalog path reads unknown native stack tail");return bytes[at]&255;}
        public void write(int address,int value)throws IOException{int at=offset(address);if(at<0){memory.write(address,value);return;}bytes[at]=(byte)value;known[at]=true;}
        byte[] cstring(int address)throws IOException{ByteArrayOutputStream out=new ByteArrayOutputStream();for(int n=0;n<1024*1024;n++){int b=read(address+n);if(b==0)return out.toByteArray();out.write(b);}throw new IOException("PC1 catalog path exceeds known traversal bound");}
    }
    @Override public Pc1CatalogLoader.Opened open(int category,int name,int extension,int pathCapacity)throws IOException{
        Scratch scratch=new Scratch(pathCapacity);Pc1NativePaths.buildAsset(scratch,Scratch.ADDRESS,category,name,extension,address->windows.attributes(scratch.cstring(address)));Pc1CrxDecoder.Input file=io.open(scratch.cstring(Scratch.ADDRESS));if(file==null)return null;
        return new Pc1CatalogLoader.Opened(){public int descriptor(){return file.descriptor();}public int read(Pc1CatalogLoader.Destination destination,int requested)throws IOException{long unsigned=requested&0xffffffffL;if(unsigned>64*1024*1024)throw new IOException("PC1 catalog unsigned CRT read requires a directly mapped native destination for0x"+Integer.toHexString(requested));byte[] buffer=new byte[Math.max(1,(int)unsigned)];int got=file.read(buffer,0,(int)unsigned);for(int n=0;n<got;n++)destination.write(n,buffer[n]);return got;}public int close()throws IOException{return io.close(file);}};
    }
}
