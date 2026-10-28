package org.circus.core;

import java.io.*;
import java.util.*;

/** UnityFS block reader. Only the requested LZ4 blocks are expanded, never the whole archive. */
public final class UnityBundle {
    public static final class Entry {
        public final String name; public final long offset,length; public final int flags;
        Entry(String n,long o,long l,int f){name=n;offset=o;length=l;flags=f;}
    }
    private static final class Block {
        final long offset,packedOffset;final int size,packedSize,flags;
        Block(long o,long p,int n,int c,int f){offset=o;packedOffset=p;size=n;packedSize=c;flags=f;}
    }
    public final String unityVersion,unityRevision;public final int version;
    public final List<Entry> entries; public final long unpackedSize;
    private final Archive.Source source;private final List<Block> blocks=new ArrayList<>();
    private final LinkedHashMap<Integer,byte[]> cache=new LinkedHashMap<>(16,.75f,true);
    private int cacheBytes;private static final int CACHE_LIMIT=1024*1024,CHUNK_LIMIT=32*1024*1024;
    public UnityBundle(Archive.Source source)throws IOException{
        this.source=source;long total=source.size();UnityBinary h=new UnityBinary(UnityBinary.read(source,0,(int)Math.min(total,4096)),false);
        if(!h.zero().equals("UnityFS"))throw new IOException("Expected UnityFS bundle");version=h.i32();if(version!=6&&version!=7)throw new IOException("Unsupported UnityFS version "+version);
        unityVersion=h.zero();unityRevision=h.zero();long declared=h.i64();int compressed=h.i32(),expanded=h.i32(),flags=h.i32();
        if(declared!=total||compressed<0||compressed>CHUNK_LIMIT||expanded<0||expanded>CHUNK_LIMIT)throw new IOException("Invalid UnityFS header sizes");
        long afterHeader=version>=7?(h.position+15L)&~15L:h.position;
        long indexOffset=(flags&128)!=0?total-compressed:afterHeader;
        UnityBinary index=new UnityBinary(unpack(UnityBinary.read(source,indexOffset,compressed),expanded,flags&63),false);
        index.skip(16);int count=index.count(1000000,"block");long offset=0,packed=afterHeader+((flags&128)!=0?0:compressed);
        if((flags&512)!=0)packed=(packed+15)&~15L;
        long dataEnd=(flags&128)!=0?indexOffset:total;
        for(int n=0;n<count;n++){
            int size=index.i32(),packedSize=index.i32(),blockFlags=index.u16();
            int compression=blockFlags&63;
            if(size<=0||packedSize<0||(compression==0?size!=packedSize:size>CHUNK_LIMIT||packedSize>CHUNK_LIMIT))throw new IOException("UnityFS block size limit");
            Binary.range(packed,packedSize,dataEnd);if(offset>Long.MAX_VALUE-size)throw new IOException("UnityFS size overflow");
            blocks.add(new Block(offset,packed,size,packedSize,blockFlags));offset+=size;packed+=packedSize;
        }
        unpackedSize=offset;int files=index.count(100000,"node");List<Entry> list=new ArrayList<>();Set<String> names=new HashSet<>();
        for(int n=0;n<files;n++){long at=index.i64(),size=index.i64();int f=index.i32();String name=index.zero();Binary.range(at,size,unpackedSize);if(name.isEmpty()||!names.add(name))throw new IOException("Duplicate/empty UnityFS node");list.add(new Entry(name,at,size,f));}
        entries=Collections.unmodifiableList(list);
    }
    public Entry find(String name)throws IOException{String key=name.replace('\\','/');int slash=key.lastIndexOf('/');String base=key.substring(slash+1);Entry found=null;for(Entry e:entries)if(e.name.equals(key)||e.name.equals(base)){if(found!=null)throw new IOException("Ambiguous UnityFS node "+name);found=e;}if(found==null)throw new FileNotFoundException("UnityFS node "+name);return found;}
    public Archive.Source source(final Entry entry){return new Archive.Source(){public long size(){return entry.length;}public byte[] read(long at,int size)throws IOException{Binary.range(at,size,entry.length);return UnityBundle.this.read(entry.offset+at,size);}};}
    public byte[] read(Entry entry)throws IOException{if(entry.length>128*1024*1024)throw new IOException("UnityFS resource exceeds read limit");return read(entry.offset,(int)entry.length);}
    public synchronized int cachedBytes(){return cacheBytes;}
    public synchronized void clearCache(){cache.clear();cacheBytes=0;}
    private byte[] block(int i)throws IOException{
        byte[] data=cache.get(i);if(data!=null)return data;Block b=blocks.get(i);data=unpack(UnityBinary.read(source,b.packedOffset,b.packedSize),b.size,b.flags&63);
        if(data.length<=CACHE_LIMIT){while(cacheBytes+data.length>CACHE_LIMIT&&!cache.isEmpty()){Iterator<byte[]> it=cache.values().iterator();cacheBytes-=it.next().length;it.remove();}cache.put(i,data);cacheBytes+=data.length;}return data;
    }
    public synchronized byte[] read(long at,int length)throws IOException{
        Binary.range(at,length,unpackedSize);if(length>128*1024*1024)throw new IOException("UnityFS resource exceeds read limit");byte[] out=new byte[length];if(length==0)return out;
        int lo=0,hi=blocks.size();while(lo<hi){int m=(lo+hi)>>>1;Block b=blocks.get(m);if(b.offset+b.size<=at)lo=m+1;else hi=m;}
        int written=0;for(int i=lo;written<length&&i<blocks.size();i++){Block b=blocks.get(i);int begin=(int)Math.max(0,at-b.offset),count=Math.min(b.size-begin,length-written);if((b.flags&63)==0&&b.size>CACHE_LIMIT){byte[] bytes=UnityBinary.read(source,b.packedOffset+begin,count);System.arraycopy(bytes,0,out,written,count);}else System.arraycopy(block(i),begin,out,written,count);written+=count;at+=count;}
        if(written!=length)throw new EOFException("Truncated UnityFS block span");return out;
    }
    static byte[] unpack(byte[] input,int size,int compression)throws IOException{
        if(size<0||size>CHUNK_LIMIT)throw new IOException("UnityFS expansion limit");
        if(compression==0){if(input.length!=size)throw new IOException("UnityFS raw block size mismatch");return input;}
        if(compression!=2&&compression!=3)throw new IOException("Unsupported UnityFS compression "+compression);
        byte[] output=new byte[size];int p=0,q=0;
        while(p<input.length){int token=input[p++]&255,literals=token>>>4;
            if(literals==15){int b;do{if(p==input.length)throw new EOFException("LZ4 literal length");b=input[p++]&255;if(literals>size-b)throw new IOException("LZ4 literal overflow");literals+=b;}while(b==255);}
            if(literals>input.length-p||literals>size-q)throw new IOException("LZ4 literal outside block");System.arraycopy(input,p,output,q,literals);p+=literals;q+=literals;if(p==input.length)break;
            if(input.length-p<2)throw new EOFException("LZ4 distance");int distance=(input[p++]&255)|(input[p++]&255)<<8;if(distance==0||distance>q)throw new IOException("LZ4 invalid distance");
            int match=(token&15)+4;if((token&15)==15){int b;do{if(p==input.length)throw new EOFException("LZ4 match length");b=input[p++]&255;if(match>size-b)throw new IOException("LZ4 match overflow");match+=b;}while(b==255);}
            if(match>size-q)throw new IOException("LZ4 match outside block");for(int i=0;i<match;i++)output[q+i]=output[q+i-distance];q+=match;
        }
        if(q!=size)throw new IOException("LZ4 decoded size mismatch");return output;
    }
}
