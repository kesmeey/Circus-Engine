package org.circus.core;

import java.io.IOException;

/** Current CHS40E5A0 and41CDE0/41CE20, operating on addressed raw ANSI
 * bytes. Windows attribute queries and primary/fallback roots keep their
 * actual owners. No case, slash, Unicode or basename normalization occurs. */
public final class Pc1NativePaths {
    private Pc1NativePaths(){}
    public static final int PRIMARY_ROOT=0x94c310,FALLBACK_ROOT=0x94c518;
    public static final int DATA_PREFIX=0x43cd88,SEPARATOR=0x43cd94;
    private static final int HOST_SCAN_LIMIT=1024*1024;
    public interface Memory {
        int read(int address)throws IOException;
        void write(int address,int value)throws IOException;
    }
    public interface Attributes {
        /** Raw GetFileAttributesA result. OnlyFFFFFFFF selects fallback.
         * The callback may mutate shared native memory before returning. */
        int query(int rawCStringAddress)throws IOException;
    }
    private static int byteAt(Memory memory,int address)throws IOException{return memory.read(address)&255;}
    private static int length(Memory memory,int address)throws IOException{
        for(int n=0;n<HOST_SCAN_LIMIT;n++)if(byteAt(memory,address+n)==0)return n;
        throw new IOException("PC1 path C source exceeds known host scan bound");
    }
    /** REP MOVSD followed byREP MOVSB, including the terminator. Source
     * lengths are captured first; overlapping writes retain native behavior. */
    private static void copy(Memory memory,int destination,int source)throws IOException{
        int count=length(memory,source)+1,n=0;
        for(;n+4<=count;n+=4){int word=0;for(int b=0;b<4;b++)word|=byteAt(memory,source+n+b)<<(b*8);for(int b=0;b<4;b++)memory.write(destination+n+b,word>>>(b*8)&255);}
        for(;n<count;n++)memory.write(destination+n,byteAt(memory,source+n));
    }
    private static void append(Memory memory,int destination,int source)throws IOException{
        // The native code scans the source before the destination.
        int sourceLength=length(memory,source)+1,destinationEnd=destination+length(memory,destination),n=0;
        for(;n+4<=sourceLength;n+=4){int word=0;for(int b=0;b<4;b++)word|=byteAt(memory,source+n+b)<<(b*8);for(int b=0;b<4;b++)memory.write(destinationEnd+n+b,word>>>(b*8)&255);}
        for(;n<sourceLength;n++)memory.write(destinationEnd+n,byteAt(memory,source+n));
    }
    private static boolean drive(Memory memory,int source)throws IOException{return byteAt(memory,source)!=0&&byteAt(memory,source+1)==':';}
    /**41CDE0 returns the last dot only when it follows every slash/colon.
     * A leading dot is an extension; no separator leaves the terminalNUL. */
    public static int extensionPointer(Memory memory,int path)throws IOException{
        if(memory==null)throw new IOException("PC1 path addressed owner missing");int last=path,at=path;
        for(int n=0;n<HOST_SCAN_LIMIT;n++,at++){
            int b=byteAt(memory,at);if(b==0)return byteAt(memory,last)=='.'?last:at;
            if(b=='\\'||b=='.'||b==':'||b=='/')last=at;
        }
        throw new IOException("PC1 extension scan exceeds known host bound");
    }
    /** Linked CRT initialMB_CUR_MAX1 and signed-char Ctype table4403CA.
     * Negative byte indices really read preceding CRT data in this image. */
    private static boolean space(int value){
        if(value>=9&&value<=13||value==32)return true;
        switch(value){case 0x82:case 0x97:case 0x9a:case 0xb2:case 0xc9:case 0xcb:case 0xcd:case 0xce:case 0xe2:case 0xe4:case 0xe5:case 0xf0:case 0xfb:case 0xfd:return true;default:return false;}
    }
    /**41CE20 always creates a dot, even when the extension is empty or starts
     *with whitespace. It replaces the suffix and retains inactive stale bytes. */
    public static void forceExtension(Memory memory,int path,int extension)throws IOException{
        int output=extensionPointer(memory,path);if(byteAt(memory,output)!='.')memory.write(output,'.');output++;
        int input=extension;if(byteAt(memory,input)=='.')input++;
        for(int n=0;n<HOST_SCAN_LIMIT;n++,input++,output++){
            int b=byteAt(memory,input);if(b==0||space(b)){memory.write(output,0);return;}
            //41CE20 reloadsAL after the linked CRT classification call.
            memory.write(output,byteAt(memory,input));
        }
        throw new IOException("PC1 extension copy exceeds known host bound");
    }
    private static void relative(Memory memory,int destination,int root,int category,int name,int extension,int prefix)throws IOException{
        copy(memory,destination,root);append(memory,destination,prefix);
        if(byteAt(memory,category)!=0){append(memory,destination,category);append(memory,destination,SEPARATOR);}
        append(memory,destination,name);if(byteAt(memory,extension)!=0)forceExtension(memory,destination,extension);
    }
    /**40E5A0. Drive-qualified name bypasses category/extension/query. A
     *drive-qualified category appends onlyseparator/name/forced extension.
     *Relative primary is queried once, and fallback is never queried here. */
    public static void buildAsset(Memory memory,int destination,int category,int name,int extension,Attributes attributes)throws IOException{
        build(memory,destination,category,name,extension,attributes,DATA_PREFIX);
    }
    /**40E870 uses a single root separator instead oftheAdvData prefix. */
    public static void buildRoot(Memory memory,int destination,int category,int name,int extension,Attributes attributes)throws IOException{
        build(memory,destination,category,name,extension,attributes,SEPARATOR);
    }
    private static void build(Memory memory,int destination,int category,int name,int extension,Attributes attributes,int prefix)throws IOException{
        if(memory==null)throw new IOException("PC1 path addressed owner missing");
        if(drive(memory,name)){copy(memory,destination,name);return;}
        if(drive(memory,category)){
            copy(memory,destination,category);append(memory,destination,SEPARATOR);append(memory,destination,name);
            if(byteAt(memory,extension)!=0)forceExtension(memory,destination,extension);return;
        }
        relative(memory,destination,PRIMARY_ROOT,category,name,extension,prefix);
        if(attributes==null)throw new IOException("PC1 path requires native GetFileAttributesA owner");
        if(attributes.query(destination)==-1)relative(memory,destination,FALLBACK_ROOT,category,name,extension,prefix);
    }
}
