package org.circus.core;

import java.io.*;
import java.nio.file.Path;

/** Real raw path producers with typed caller locals. No local coordinate can
 *shadow global roots, CRT fields, tables or actual addressed arguments. */
public final class Pc1ResourcePaths {
    private final Pc1NativePaths.Memory globals;private final Pc1WindowsPaths windows;private final Pc1NativePaths.SourceAttributes attributes;
    public Pc1ResourcePaths(Pc1NativePaths.Memory globals,Pc1WindowsPaths windows)throws IOException{this(globals,windows,windows==null?null:source->windows.attributes(source));}
    /** Explicit OS-leaf adapter, also allowing controlled native callback
     *fixtures. The ordinary constructor always uses actual WindowsA. */
    public Pc1ResourcePaths(Pc1NativePaths.Memory globals,Pc1WindowsPaths windows,Pc1NativePaths.SourceAttributes attributes)throws IOException{if(globals==null||windows==null||attributes==null)throw new IOException("PC1 resource path roots/WindowsA owner missing");this.globals=globals;this.windows=windows;this.attributes=attributes;}
    public static final class Resolved {
        private final Pc1ByteSource source;private final Pc1WindowsPaths windows;
        private Resolved(Pc1ByteSource source,Pc1WindowsPaths windows){this.source=source;this.windows=windows;}
        public Pc1ByteSource source(){return source;}
        /** Read at the consumer's actual call point, not a resolve-time copy. */
        public byte[] rawPath()throws IOException{return Pc1WindowsPaths.cstring(source);}
        public Path nativePath()throws IOException{return windows.exactPath(rawPath());}
    }
    private static final class Local implements Pc1ByteSource.Pointer {
        private final byte[] bytes;private final boolean[] known;
        Local(int capacity)throws IOException{if(capacity<1||capacity>1024*1024)throw new IOException("PC1 path unowned caller allocation/capacity");bytes=new byte[capacity];known=new boolean[capacity];}
        private int at(int offset)throws IOException{if(offset<0||offset>=bytes.length)throw new IOException("PC1 path unowned caller stack outside actual local");return offset;}
        public int unsignedByte(int offset)throws IOException{int n=at(offset);if(!known[n])throw new IOException("PC1 path unowned/unknown caller stack byte");return bytes[n]&255;}
        public void writeByte(int offset,int value)throws IOException{int n=at(offset);bytes[n]=(byte)value;known[n]=true;}
    }
    /** Explicit byte[] convenience owner: every supplied byte plus terminalNUL
     *is known. Unused inputs are not scanned or dereferenced; no numeric VA. */
    private static Pc1ByteSource value(final byte[] bytes){if(bytes==null)return null;return offset->{if(offset<0||offset>bytes.length)throw new IOException("PC1 path value reads unowned source suffix");return offset==bytes.length?0:bytes[offset]&255;};}
    public Resolved assetTyped(Pc1ByteSource category,Pc1ByteSource name,Pc1ByteSource extension,int capacity)throws IOException{return resolve(category,name,extension,capacity,true);}
    public Resolved rootTyped(Pc1ByteSource category,Pc1ByteSource name,Pc1ByteSource extension,int capacity)throws IOException{return resolve(category,name,extension,capacity,false);}
    public Resolved asset(byte[] category,byte[] name,byte[] extension,int capacity)throws IOException{return assetTyped(value(category),value(name),value(extension),capacity);}
    public Resolved root(byte[] category,byte[] name,byte[] extension,int capacity)throws IOException{return rootTyped(value(category),value(name),value(extension),capacity);}
    public Resolved assetAddressed(Pc1NativePaths.Memory sources,int category,int name,int extension,int capacity)throws IOException{if(sources==null)throw new IOException("PC1 addressed source owner missing");return assetTyped(Pc1NativePaths.pointer(sources,category),Pc1NativePaths.pointer(sources,name),Pc1NativePaths.pointer(sources,extension),capacity);}
    public Resolved rootAddressed(Pc1NativePaths.Memory sources,int category,int name,int extension,int capacity)throws IOException{if(sources==null)throw new IOException("PC1 addressed source owner missing");return rootTyped(Pc1NativePaths.pointer(sources,category),Pc1NativePaths.pointer(sources,name),Pc1NativePaths.pointer(sources,extension),capacity);}
    private Resolved resolve(Pc1ByteSource category,Pc1ByteSource name,Pc1ByteSource extension,int capacity,boolean data)throws IOException{Local destination=new Local(capacity);if(data)Pc1NativePaths.buildAsset(globals,destination,category,name,extension,attributes);else Pc1NativePaths.buildRoot(globals,destination,category,name,extension,attributes);return new Resolved(destination,windows);}
}
