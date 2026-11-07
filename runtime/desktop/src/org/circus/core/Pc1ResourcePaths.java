package org.circus.core;

import java.io.*;
import java.nio.file.Path;
import java.util.Arrays;

/** Actual raw40E5A0/40E870 plus WindowsA query. Global root/constant bytes are
 *borrowed from their canonical addressed owner; each caller owns its scratch. */
public final class Pc1ResourcePaths {
    private final Pc1NativePaths.Memory globals;
    private final Pc1WindowsPaths windows;
    public Pc1ResourcePaths(Pc1NativePaths.Memory globals,Pc1WindowsPaths windows)throws IOException{if(globals==null||windows==null)throw new IOException("PC1 resource path roots/WindowsA owner missing");this.globals=globals;this.windows=windows;}
    public static final class Resolved {
        private final byte[] raw;
        private final Pc1WindowsPaths windows;
        private Resolved(byte[] raw,Pc1WindowsPaths windows){this.raw=raw;this.windows=windows;}
        public byte[] rawPath(){return raw.clone();}
        public Path nativePath()throws IOException{return windows.exactPath(raw);}
    }
    private static int cLength(byte[] value)throws IOException{if(value==null)throw new IOException("PC1 path argument missing");int n=0;while(n<value.length&&value[n]!=0)n++;return n;}
    private final class Scratch implements Pc1NativePaths.Memory {
        final int[] addresses=new int[4];final byte[][] bytes=new byte[4][];
        Scratch(byte[] category,byte[] name,byte[] extension,int capacity)throws IOException{
            if(capacity<1||capacity>1024*1024)throw new IOException("PC1 path scratch outside known caller allocation");addresses[0]=0xe0000000;bytes[0]=new byte[capacity];byte[][] arguments={category,name,extension};int next=0xe0200000;
            for(int n=1;n<4;n++){int length=cLength(arguments[n-1]);if(length>1024*1024)throw new IOException("PC1 path argument host bound");addresses[n]=next;bytes[n]=Arrays.copyOf(arguments[n-1],length+1);bytes[n][length]=0;next+=0x200000;}
        }
        public int read(int address)throws IOException{for(int n=0;n<4;n++){long at=(address&0xffffffffL)-(addresses[n]&0xffffffffL);if(at>=0&&at<bytes[n].length)return bytes[n][(int)at]&255;}return globals.read(address);}
        public void write(int address,int value)throws IOException{for(int n=0;n<4;n++){long at=(address&0xffffffffL)-(addresses[n]&0xffffffffL);if(at>=0&&at<bytes[n].length){bytes[n][(int)at]=(byte)value;return;}}globals.write(address,value);}
        byte[] cstring(int address)throws IOException{ByteArrayOutputStream out=new ByteArrayOutputStream();for(int n=0;n<1024*1024;n++){int value=read(address+n);if(value==0)return out.toByteArray();out.write(value);}throw new IOException("PC1 path query host scan bound");}
    }
    public Resolved asset(byte[] category,byte[] name,byte[] extension,int callerCapacity)throws IOException{return resolve(category,name,extension,callerCapacity,true);}
    public Resolved root(byte[] category,byte[] name,byte[] extension,int callerCapacity)throws IOException{return resolve(category,name,extension,callerCapacity,false);}
    /** Native callers keep arguments as addresses. Unlike the legacy logical
     *byte-array facade, this never scans unused arguments or invents initial
     *stack bytes. Sources/roots are borrowed from the caller's same byte bus. */
    public Resolved assetAddressed(Pc1NativePaths.Memory sources,int category,int name,int extension,int callerCapacity)throws IOException{
        if(sources==null||callerCapacity<1||callerCapacity>1024*1024)throw new IOException("PC1 addressed path caller memory/capacity missing");
        final int destination=0xe0000000;final byte[] bytes=new byte[callerCapacity];final boolean[] known=new boolean[callerCapacity];
        Pc1NativePaths.Memory memory=new Pc1NativePaths.Memory(){
            private int offset(int address){long at=(address&0xffffffffL)-(destination&0xffffffffL);return at>=0&&at<bytes.length?(int)at:-1;}
            public int read(int address)throws IOException{int at=offset(address);if(at<0)return sources.read(address);if(!known[at])throw new IOException("PC1 addressed path reads unknown native local stack tail");return bytes[at]&255;}
            public void write(int address,int value)throws IOException{int at=offset(address);if(at<0){sources.write(address,value);return;}bytes[at]=(byte)value;known[at]=true;}
        };
        Pc1NativePaths.Attributes attributes=address->windows.attributes(addressedCString(memory,address));Pc1NativePaths.buildAsset(memory,destination,category,name,extension,attributes);return new Resolved(addressedCString(memory,destination),windows);
    }
    private static byte[] addressedCString(Pc1NativePaths.Memory memory,int address)throws IOException{ByteArrayOutputStream out=new ByteArrayOutputStream();for(int n=0;n<1024*1024;n++){int value=memory.read(address+n)&255;if(value==0)return out.toByteArray();out.write(value);}throw new IOException("PC1 addressed path leaves known bounded memory");}
    private Resolved resolve(byte[] category,byte[] name,byte[] extension,int capacity,boolean data)throws IOException{
        Scratch memory=new Scratch(category,name,extension,capacity);Pc1NativePaths.Attributes query=address->windows.attributes(memory.cstring(address));int[] a=memory.addresses;
        if(data)Pc1NativePaths.buildAsset(memory,a[0],a[1],a[2],a[3],query);else Pc1NativePaths.buildRoot(memory,a[0],a[1],a[2],a[3],query);
        return new Resolved(memory.cstring(a[0]),windows);
    }
}
