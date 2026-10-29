package org.circus.core;

import java.io.*;
import java.util.*;

/** Unity serialized-file v17 object index, with bounded metadata and lazy object access. */
public final class UnitySerializedFile {
    public static final class ObjectInfo {
        public final long pathId,offset;public final int size,classId;
        ObjectInfo(long id,long off,int size,int type){pathId=id;offset=off;this.size=size;classId=type;}
    }
    public final String unityVersion;public final int version,platform;public final boolean littleEndian;
    public final List<ObjectInfo> objects;private final Archive.Source source;
    public UnitySerializedFile(Archive.Source source)throws IOException{
        this.source=source;long total=source.size();UnityBinary h=new UnityBinary(UnityBinary.read(source,0,20),false);
        long metadata=h.u32(),declared=h.u32();version=h.i32();long dataOffset=h.u32();int endian=h.u8();
        if(version!=17)throw new IOException("Unsupported Unity serialized version "+version);
        if(endian>1||declared!=total||dataOffset<20||dataOffset>total||metadata>32*1024*1024||dataOffset-20>32*1024*1024)throw new IOException("Invalid Unity serialized header");
        littleEndian=endian==0;UnityBinary r=new UnityBinary(UnityBinary.read(source,20,(int)dataOffset-20),littleEndian);unityVersion=r.zero();platform=r.i32();boolean typeTree=r.u8()!=0;
        int[] types=new int[r.count(16384,"type")];
        for(int i=0;i<types.length;i++){int classId=r.i32();types[i]=classId;r.u8();r.u16();if(classId==114)r.skip(16);r.skip(16);
            if(typeTree){int nodes=r.count(1000000,"type-tree node"),strings=r.count(16*1024*1024,"type-tree string");r.skip(Math.multiplyExact(nodes,24));r.skip(strings);}}
        int n=r.count(1000000,"object");List<ObjectInfo> list=new ArrayList<>();Set<Long> ids=new HashSet<>();
        for(int i=0;i<n;i++){r.align();long id=r.i64(),offset=dataOffset+r.u32(),size=r.u32();int type=r.i32();if(type<0||type>=types.length||size>Integer.MAX_VALUE||!ids.add(id))throw new IOException("Invalid Unity object index");Binary.range(offset,size,total);list.add(new ObjectInfo(id,offset,(int)size,types[type]));}
        objects=Collections.unmodifiableList(list);
    }
    public byte[] read(ObjectInfo object)throws IOException{if(object.size>128*1024*1024)throw new IOException("Unity object read limit");return UnityBinary.read(source,object.offset,object.size);}
    public byte[] slice(ObjectInfo object,long offset,int length)throws IOException{Binary.range(offset,length,object.size);return UnityBinary.read(source,object.offset+offset,length);}
    public String name(ObjectInfo object)throws IOException{if(object.classId!=28&&object.classId!=49&&object.classId!=83)throw new IOException("Object is not a supported Unity named asset");UnityBinary r=new UnityBinary(slice(object,0,Math.min(object.size,4096)),littleEndian);return r.string();}
    /** Batch adjacent object headers when a name-index fallback is needed. In large
     * raw bundles, opening a document-provider handle for each tiny object is slow. */
    Map<ObjectInfo,String> names(int type)throws IOException{
        if(type!=28&&type!=49&&type!=83)throw new IOException("Object is not a supported Unity named asset");
        List<ObjectInfo> selected=new ArrayList<>();for(ObjectInfo object:objects)if(object.classId==type)selected.add(object);
        selected.sort(Comparator.comparingLong(object->object.offset));Map<ObjectInfo,String> result=new HashMap<>();
        for(int first=0;first<selected.size();){
            ObjectInfo object=selected.get(first);long begin=object.offset,end=begin+Math.min(object.size,4096);int last=first+1;
            while(last<selected.size()){
                ObjectInfo next=selected.get(last);long nextEnd=next.offset+Math.min(next.size,4096);
                if(next.offset-end>4096||Math.max(end,nextEnd)-begin>256*1024)break;
                end=Math.max(end,nextEnd);last++;
            }
            byte[] bytes=UnityBinary.read(source,begin,(int)(end-begin));
            for(int n=first;n<last;n++){
                ObjectInfo current=selected.get(n);int offset=(int)(current.offset-begin),length=Math.min(current.size,4096);
                result.put(current,new UnityBinary(Arrays.copyOfRange(bytes,offset,offset+length),littleEndian).string());
            }
            first=last;
        }
        return result;
    }
}
