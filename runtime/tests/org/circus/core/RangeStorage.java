package org.circus.core;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Test-only, exact-byte record/replay of resource reads. Missing spans always fail; never zero-fill. */
public final class RangeStorage implements GameResources.Storage {
    private static final byte[] MAGIC={'C','R','R','G','0','0','0','1'};
    private static final long PACK_LIMIT=256L*1024*1024;
    private static final class Span {
        final long offset,packOffset;final int length;final byte[] data;
        Span(long at,byte[] b){offset=at;length=b.length;data=b;packOffset=-1;}
        Span(long at,int size,long packed){offset=at;length=size;packOffset=packed;data=null;}
        long end(){return offset+length;}
    }
    private static final class Item {
        long size=-1;final TreeMap<Long,Span> spans=new TreeMap<>();
    }
    private final GameResources.Storage delegate;private final Path pack;
    private final SortedMap<String,Item> items=new TreeMap<>();private final Set<String> paths;private long recordedBytes;
    private RangeStorage(GameResources.Storage source)throws IOException{
        delegate=source;pack=null;for(String path:source.paths()){Binary.safePath(path);if(items.put(path,new Item())!=null)throw new IOException("Duplicate recording path");}paths=Collections.unmodifiableSet(new LinkedHashSet<>(items.keySet()));
    }
    private RangeStorage(Path path)throws IOException{
        delegate=null;pack=path;try(RandomAccessFile in=new RandomAccessFile(path.toFile(),"r")){
            if(in.length()>PACK_LIMIT)throw new IOException("Range pack size limit");byte[] magic=new byte[8];in.readFully(magic);if(!Arrays.equals(magic,MAGIC))throw new IOException("Range pack signature");int count=in.readInt();if(count<0||count>100000)throw new IOException("Range pack path count");
            for(int i=0;i<count;i++){int bytes=in.readInt();if(bytes<1||bytes>65536)throw new IOException("Range pack path length");byte[] raw=new byte[bytes];in.readFully(raw);String name=new String(raw,StandardCharsets.UTF_8);Binary.safePath(name);Item item=new Item();item.size=in.readLong();if(item.size< -1)throw new IOException("Range pack resource size");int ranges=in.readInt();if(ranges<0||ranges>1000000||item.size<0&&ranges!=0)throw new IOException("Range pack span count");long previous=0;
                for(int j=0;j<ranges;j++){long offset=in.readLong();int length=in.readInt();if(length<=0||offset<previous)throw new IOException("Range pack overlapping spans");Binary.range(offset,length,item.size);Binary.range(in.getFilePointer(),length,in.length());item.spans.put(offset,new Span(offset,length,in.getFilePointer()));previous=offset+length;in.seek(in.getFilePointer()+length);recordedBytes+=length;}
                if(items.put(name,item)!=null)throw new IOException("Range pack duplicate path");
            }
            if(in.getFilePointer()!=in.length())throw new IOException("Trailing range pack bytes");
        }
        paths=Collections.unmodifiableSet(new LinkedHashSet<>(items.keySet()));
    }
    public static RangeStorage recording(GameResources.Storage source)throws IOException{return new RangeStorage(source);}
    public static RangeStorage replay(Path pack)throws IOException{return new RangeStorage(pack);}
    @Override public Set<String> paths(){return paths;}
    public synchronized long recordedBytes(){return recordedBytes;}
    private Item item(String name)throws IOException{Item i=items.get(name);if(i==null)throw new FileNotFoundException("Range resource path "+name);return i;}
    private synchronized long size(String name)throws IOException{Item i=item(name);if(i.size<0){if(delegate==null)throw new IOException("Range size was not recorded: "+name);i.size=delegate.source(name).size();if(i.size<0)throw new IOException("Negative resource size");}return i.size;}
    private synchronized void remember(String name,long at,byte[] data)throws IOException{
        Item item=item(name);Binary.range(at,data.length,item.size);long cursor=at,end=at+data.length;
        while(cursor<end){Map.Entry<Long,Span> before=item.spans.floorEntry(cursor);if(before!=null&&before.getValue().end()>cursor){Span span=before.getValue();long n=Math.min(end,span.end())-cursor;for(int j=0;j<n;j++)if(data[(int)(cursor-at)+j]!=span.data[(int)(cursor-span.offset)+j])throw new IOException("Resource changed while recording: "+name);cursor+=n;continue;}
            Map.Entry<Long,Span> next=item.spans.ceilingEntry(cursor);long until=next==null?end:Math.min(end,next.getKey());int length=(int)(until-cursor);if(recordedBytes+length>PACK_LIMIT-1024*1024)throw new IOException("Range recording exceeds 255 MiB limit");item.spans.put(cursor,new Span(cursor,Arrays.copyOfRange(data,(int)(cursor-at),(int)(until-at))));recordedBytes+=length;cursor=until;
        }
    }
    @Override public byte[] read(String path,int limit)throws IOException{
        long length=size(path);if(limit<0||length>limit||length>Integer.MAX_VALUE)throw new IOException("Range read limit: "+path);return source(path).read(0,(int)length);
    }
    @Override public Archive.Source source(final String path)throws IOException{
        item(path);return new Archive.Source(){public long size()throws IOException{return RangeStorage.this.size(path);}public byte[] read(long offset,int length)throws IOException{
            Binary.range(offset,length,size());if(length>128*1024*1024)throw new IOException("Range resource read limit");
            if(delegate!=null){byte[] bytes=delegate.source(path).read(offset,length);if(bytes.length!=length)throw new EOFException("Short recorded read: "+path);remember(path,offset,bytes);return bytes;}
            byte[] bytes=new byte[length];Item item=item(path);long cursor=offset,end=offset+length;
            try(RandomAccessFile in=new RandomAccessFile(pack.toFile(),"r")){while(cursor<end){Map.Entry<Long,Span> entry=item.spans.floorEntry(cursor);if(entry==null||entry.getValue().end()<=cursor)throw new EOFException("Unrecorded range: "+path+" @"+cursor+" requested "+offset+"+"+length);Span span=entry.getValue();int count=(int)Math.min(end-cursor,span.end()-cursor);in.seek(span.packOffset+cursor-span.offset);in.readFully(bytes,(int)(cursor-offset),count);cursor+=count;}}
            return bytes;
        }};
    }
    /** Writes only bytes that were actually read, plus original path/size metadata. */
    public synchronized void write(Path target)throws IOException{
        if(delegate==null)throw new IOException("Replay storage cannot record");Path parent=target.toAbsolutePath().getParent();if(parent!=null)Files.createDirectories(parent);
        try(DataOutputStream out=new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(target)))){
            out.write(MAGIC);out.writeInt(items.size());for(Map.Entry<String,Item> entry:items.entrySet()){byte[] name=entry.getKey().getBytes(StandardCharsets.UTF_8);Item item=entry.getValue();out.writeInt(name.length);out.write(name);out.writeLong(item.size);out.writeInt(item.spans.size());for(Span span:item.spans.values()){out.writeLong(span.offset);out.writeInt(span.length);out.write(span.data);}}
        }
        if(Files.size(target)>PACK_LIMIT)throw new IOException("Range pack size limit");
    }
}
