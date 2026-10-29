package org.circus.core;

import java.io.*;
import java.util.*;

/** PCK/CRM indices retain raw names, duplicate aliases and archive-relative offsets. */
public final class Archive {
    public interface Source {
        long size()throws IOException; byte[] read(long offset,int length)throws IOException;
        /** Keep one read handle for a group of small random-access inspections. */
        default Reader openReader()throws IOException{
            final Source source=this;return new Reader(){public long size()throws IOException{return source.size();}public byte[] read(long at,int count)throws IOException{return source.read(at,count);}public void close(){}};
        }
        /** Platforms with persistent file handles may override this bounded
         * copy; the default never allocates a complete entry. */
        default void copyTo(long offset,long count,OutputStream out)throws IOException{
            Binary.range(offset,count,size());for(long copied=0;copied<count;){int n=(int)Math.min(64*1024,count-copied);byte[] block=read(offset+copied,n);if(block.length!=n)throw new EOFException("Short archive copy");out.write(block);copied+=n;}
        }
    }
    public interface Reader extends Source,AutoCloseable {void close()throws IOException;}
    public static final class Entry {
        public final int index;public final byte[] nameBytes;public final String name;public final long offset;public final int length;
        Entry(int i,byte[] n,long o,int l){index=i;nameBytes=n;name=new String(n,Binary.SJIS);offset=o;length=l;}
    }
    public final List<Entry> entries;public final String kind;private final Source source;
    /** Older VOICE.DAT: count, then 60-byte names and u32 offsets; payloads are aligned. */
    public Archive(Source source,boolean oldVoice)throws IOException{this(source,null);if(!oldVoice)throw new IllegalArgumentException("Use ordinary archive constructor");}
    public Archive(final Source primary,final Source secondary)throws IOException{
        final long split=primary.size();this.source=secondary==null?primary:new Source(){public long size()throws IOException{return split+secondary.size();}public byte[] read(long at,int n)throws IOException{if(at>=split)return secondary.read(at-split,n);Binary.range(at,n,split);return primary.read(at,n);}public void copyTo(long at,long n,OutputStream out)throws IOException{
            Binary.range(at,n,size());if(at<split){long first=Math.min(n,split-at);primary.copyTo(at,first,out);at+=first;n-=first;}if(n>0)secondary.copyTo(at-split,n,out);
        }};
        long total=this.source.size();long count=Binary.u32(primary.read(0,4),0);
        if(count==0||count>1000000)throw new IOException("Legacy voice count limit");long end=4+count*64;Binary.range(0,end,split);byte[] table=primary.read(4,(int)count*64);List<Entry> list=new ArrayList<>();TreeSet<Long> offsets=new TreeSet<>();
        for(int n=0;n<count;n++){int p=n*64,z=p;while(z<p+60&&table[z]!=0)z++;long packed=Binary.u32(table,p+60),at=packed;
            if((packed&1)!=0){if(secondary==null)throw new IOException("VOICE2.DAT required by voice index");at=split+(packed&~1L);}else if(at>=split)throw new IOException("Primary voice offset outside VOICE.DAT");
            if(z==p||z==p+60||at<end||at>=total)throw new IOException("Legacy voice index");list.add(new Entry(n,Arrays.copyOfRange(table,p,z),at,0));offsets.add(at);}
        offsets.add(split);List<Entry> result=new ArrayList<>();for(Entry e:list){Long next=offsets.higher(e.offset);long length=(next==null?total:next)-e.offset;if(length>128*1024*1024)throw new IOException("Legacy voice span limit");result.add(new Entry(e.index,e.nameBytes,e.offset,(int)length));}entries=Collections.unmodifiableList(result);kind="VOICE64";
    }
    public Archive(Source source)throws IOException {
        this.source=source;long total=source.size();byte[] head=source.read(0,(int)Math.min(16,total));boolean crm=head.length>=4 && Binary.u32(head,0)==0x42585243L;
        long count=Binary.u32(head,crm?8:0);if(count==0||count>1000000)throw new IOException("Archive count limit");
        long tableEnd=(crm?16:4)+count*(crm?32:72);Binary.range(0,tableEnd,total);byte[] table=source.read(crm?16:4,(int)(tableEnd-(crm?16:4)));
        List<Entry> result=new ArrayList<>();TreeSet<Long> offsets=new TreeSet<>();
        for(int i=0;i<count;i++){
            int p=crm?i*32:(int)count*8+i*64;boolean empty=true;
            if(crm){for(int j=0;j<32;j++)if(table[p+j]!=0){empty=false;break;}if(empty)continue;}
            long offset=Binary.u32(table,p+(crm?0:56)),size=crm?0:Binary.u32(table,p+60);int start=p+(crm?8:0),max=crm?24:56,end=start;
            while(end<start+max && table[end]!=0)end++;if(end==start)throw new IOException("Empty archive name");
            if(offset<tableEnd||size>Integer.MAX_VALUE)throw new IOException("Invalid archive payload span");Binary.range(offset,crm?1:size,total);
            if(!crm && (offset!=Binary.u32(table,i*8)||size!=Binary.u32(table,i*8+4)))throw new IOException("PCK duplicate index mismatch");
            result.add(new Entry(i,Arrays.copyOfRange(table,start,end),offset,(int)size));offsets.add(offset);
        }
        if(result.isEmpty())throw new IOException("Empty archive");
        if(crm){List<Entry> resolved=new ArrayList<>();for(Entry e:result){Long end=offsets.higher(e.offset);long size=(end==null?total:end)-e.offset;if(size>Integer.MAX_VALUE)throw new IOException("CRM payload too large");resolved.add(new Entry(e.index,e.nameBytes,e.offset,(int)size));}result=resolved;}
        else {List<Entry> sorted=new ArrayList<>(result);sorted.sort(Comparator.comparingLong(e->e.offset));Entry prev=null;for(Entry e:sorted){if(prev!=null && prev.offset+prev.length>e.offset && !(prev.offset==e.offset&&prev.length==e.length))throw new IOException("Overlapping PCK spans");prev=e;}}
        entries=Collections.unmodifiableList(result);kind=crm?"CRM":"PCK";
    }
    public Entry find(byte[] raw)throws IOException {Entry found=null;for(Entry e:entries)if(Arrays.equals(e.nameBytes,raw)){if(found!=null && (found.offset!=e.offset||found.length!=e.length))throw new IOException("Ambiguous archive name");found=e;}if(found==null)throw new FileNotFoundException("Archive entry "+new String(raw,Binary.SJIS));return found;}
    public Entry atOffset(long offset)throws IOException{for(Entry e:entries)if(e.offset==offset)return e;throw new FileNotFoundException("Archive offset "+offset);}
    /** A read-only view bounded to one entry, including split legacy voice banks. */
    public Source source(final Entry entry)throws IOException{
        if(entry.length<=0||entry.length>128*1024*1024)throw new IOException("Invalid archive entry size: "+entry.name);
        Binary.range(entry.offset,entry.length,source.size());
        return new Source(){public long size(){return entry.length;}public byte[] read(long at,int count)throws IOException{
            Binary.range(at,count,entry.length);return source.read(entry.offset+at,count);
        }public void copyTo(long at,long count,OutputStream out)throws IOException{
            Binary.range(at,count,entry.length);source.copyTo(entry.offset+at,count,out);
        }};
    }
    public byte[] read(Entry entry)throws IOException{if(entry.length==0)throw new IOException("Archive entry has invalid payload offset: "+entry.name);if(entry.length>128*1024*1024)throw new IOException("Resource exceeds 128 MiB limit");return source.read(entry.offset,entry.length);}
}
