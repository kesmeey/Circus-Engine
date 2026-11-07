package org.circus.core;

import java.io.IOException;

/** Current CHS40E5A0/40E870/41CDE0/41CE20. Addressed and typed-local
 *pointers share one forward-copy implementation without coordinate overlays. */
public final class Pc1NativePaths {
    private Pc1NativePaths(){}
    public static final int PRIMARY_ROOT=0x94c310,FALLBACK_ROOT=0x94c518;
    public static final int DATA_PREFIX=0x43cd88,SEPARATOR=0x43cd94;
    public interface Memory {
        int read(int address)throws IOException;void write(int address,int value)throws IOException;
        default int readInt32(int at)throws IOException{int word=0;for(int n=0;n<4;n++)word|=(read(at+n)&255)<<(8*n);return word;}
        default void writeInt32(int at,int word)throws IOException{for(int n=0;n<4;n++)write(at+n,word>>>(8*n));}
        /** Explicit byte-only model contract: current initial linked CRT.
         *Production memory(bus) overrides this with actual mutable CRT reads.*/
        default int classifySpace(int signedChar)throws IOException{return initialSpace(signedChar&255)?8:0;}
    }
    public interface Attributes {
        int query(int rawCStringAddress)throws IOException;
        /** A legacy addressed provider must explicitly implement this leaf to
         *consume a local path. No numeric placeholder is supplied. */
        default int querySource(Pc1ByteSource rawCString)throws IOException{throw new IOException("PC1 GetFileAttributesA typed local-source leaf required");}
    }
    public interface SourceAttributes {int query(Pc1ByteSource rawCString)throws IOException;}
    public static int classifySpaceRaw(Memory memory,int signedChar)throws IOException{if(memory==null)throw new IOException("PC1 CRT memory missing");if(memory.readInt32(0x4405cc)>1)throw new IOException("PC1 multibyte CRT classifier423486 required");int pointer=memory.readInt32(0x4403c0);return memory.read(pointer+signedChar*2)&8;}
    public static Memory memory(final Pc1NativeMemory bus)throws IOException{if(bus==null)throw new IOException("PC1 path native memory missing");return new Memory(){public int read(int at)throws IOException{return bus.readAddress(at);}public void write(int at,int value)throws IOException{bus.writeAddress(at,value);}public int readInt32(int at)throws IOException{return bus.readInt32(at);}public void writeInt32(int at,int value)throws IOException{bus.writeInt32(at,value);}public int classifySpace(int signedChar)throws IOException{return classifySpaceRaw(this,signedChar);}};}
    public static Pc1ByteSource.Pointer pointer(final Memory memory,final int address)throws IOException{if(memory==null)throw new IOException("PC1 path addressed owner missing");return new Pc1ByteSource.Pointer(){public int unsignedByte(int offset)throws IOException{return memory.read(address+offset)&255;}public int nativeWord(int offset)throws IOException{return memory.readInt32(address+offset);}public void writeByte(int offset,int value)throws IOException{memory.write(address+offset,value);}public void writeWord(int offset,int value)throws IOException{memory.writeInt32(address+offset,value);}};}
    private static int b(Pc1ByteSource source,int offset)throws IOException{if(source==null)throw new IOException("PC1 path source is NULL/unprovided");return source.unsignedByte(offset)&255;}
    private static long count(Pc1ByteSource source)throws IOException{for(long n=0;n<0xffffffffL;n++)if(b(source,(int)n)==0)return n+1;return 0xffffffffL;}
    /** Captured source count; each DWORD is read completely before stores. */
    private static void forward(Pc1ByteSource.Pointer destination,int at,Pc1ByteSource source,long count)throws IOException{long n=0;for(;n+4<=count;n+=4)destination.writeWord(at+(int)n,source.nativeWord((int)n));for(;n<count;n++)destination.writeByte(at+(int)n,b(source,(int)n));}
    public static void copy(Pc1ByteSource.Pointer destination,Pc1ByteSource source)throws IOException{if(destination==null)throw new IOException("PC1 path destination missing");forward(destination,0,source,count(source));}
    private static void append(Pc1ByteSource.Pointer destination,Pc1ByteSource source)throws IOException{long length=count(source);int end=(int)count(destination)-1;forward(destination,end,source,length);}
    private static boolean drive(Pc1ByteSource source)throws IOException{return b(source,0)!=0&&b(source,1)==':';}
    /** Offset within the same owner, never a synthesized native pointer. */
    public static int extensionOffset(Pc1ByteSource path)throws IOException{int last=0;for(int at=0;;at++){int value=b(path,at);if(value==0)return b(path,last)=='.'?last:at;if(value=='\\'||value=='.'||value==':'||value=='/')last=at;}}
    public static int extensionPointer(Memory memory,int path)throws IOException{return path+extensionOffset(pointer(memory,path));}
    /** Current linked initialMB_CUR_MAX1/signed-char Ctype data. */
    private static boolean initialSpace(int value){if(value>=9&&value<=13||value==32)return true;switch(value){case 0x82:case 0x97:case 0x9a:case 0xb2:case 0xc9:case 0xcb:case 0xcd:case 0xce:case 0xe2:case 0xe4:case 0xe5:case 0xf0:case 0xfb:case 0xfd:return true;default:return false;}}
    /** Explicit initial-CRT convenience; use the Memory overload for real bus.*/
    public static void forceExtension(Pc1ByteSource.Pointer path,Pc1ByteSource extension)throws IOException{forceExtension(null,path,extension);}
    public static void forceExtension(Memory globals,Pc1ByteSource.Pointer path,Pc1ByteSource extension)throws IOException{int output=extensionOffset(path);if(b(path,output)!='.')path.writeByte(output,'.');output++;int input=0;if(b(extension,input)=='.')input++;for(;;input++,output++){int value=b(extension,input);if(value==0||(globals==null?(initialSpace(value)?8:0):globals.classifySpace((byte)value))!=0){path.writeByte(output,0);return;}path.writeByte(output,b(extension,input));}}
    public static void forceExtension(Memory memory,int path,int extension)throws IOException{forceExtension(memory,pointer(memory,path),pointer(memory,extension));}
    private static void relative(Memory globals,Pc1ByteSource.Pointer destination,int root,Pc1ByteSource category,Pc1ByteSource name,Pc1ByteSource extension,int prefix)throws IOException{copy(destination,pointer(globals,root));append(destination,pointer(globals,prefix));if(b(category,0)!=0){append(destination,category);append(destination,pointer(globals,SEPARATOR));}append(destination,name);if(b(extension,0)!=0)forceExtension(globals,destination,extension);}
    public static void buildAsset(Memory globals,Pc1ByteSource.Pointer destination,Pc1ByteSource category,Pc1ByteSource name,Pc1ByteSource extension,SourceAttributes attributes)throws IOException{build(globals,destination,category,name,extension,attributes,DATA_PREFIX);}
    public static void buildRoot(Memory globals,Pc1ByteSource.Pointer destination,Pc1ByteSource category,Pc1ByteSource name,Pc1ByteSource extension,SourceAttributes attributes)throws IOException{build(globals,destination,category,name,extension,attributes,SEPARATOR);}
    private static void build(Memory globals,Pc1ByteSource.Pointer destination,Pc1ByteSource category,Pc1ByteSource name,Pc1ByteSource extension,SourceAttributes attributes,int prefix)throws IOException{if(globals==null||destination==null)throw new IOException("PC1 typed path owners missing");if(drive(name)){copy(destination,name);return;}if(drive(category)){copy(destination,category);append(destination,pointer(globals,SEPARATOR));append(destination,name);if(b(extension,0)!=0)forceExtension(globals,destination,extension);return;}relative(globals,destination,PRIMARY_ROOT,category,name,extension,prefix);if(attributes==null)throw new IOException("PC1 path requires native GetFileAttributesA owner");if(attributes.query(destination)==-1)relative(globals,destination,FALLBACK_ROOT,category,name,extension,prefix);}
    /** Raw convenience entrypoints preserve the real destination address. */
    public static void buildAsset(Memory memory,int destination,int category,int name,int extension,Attributes attributes)throws IOException{buildAsset(memory,pointer(memory,destination),pointer(memory,category),pointer(memory,name),pointer(memory,extension),attributes==null?null:source->attributes.query(destination));}
    public static void buildRoot(Memory memory,int destination,int category,int name,int extension,Attributes attributes)throws IOException{buildRoot(memory,pointer(memory,destination),pointer(memory,category),pointer(memory,name),pointer(memory,extension),attributes==null?null:source->attributes.query(destination));}
}
