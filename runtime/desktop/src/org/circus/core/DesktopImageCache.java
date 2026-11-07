package org.circus.core;

import java.util.*;
import java.util.function.ToLongFunction;

/** Portable decoded-image acceleration only. Pixel/state owners remain with
 *the image and engine. Limit bytes as well as entries, leaving decode and
 *ARGB-construction headroom instead of retaining forty-eight large atlases. */
public final class DesktopImageCache<V> {
    private static final long MIB=1024L*1024;
    private static final class Entry<V>{final V value;final long bytes;Entry(V value,long bytes){this.value=value;this.bytes=bytes;}}
    private final LinkedHashMap<String,Entry<V>> entries=new LinkedHashMap<>(32,.75f,true);
    private final long limit;private final int maximum;private final ToLongFunction<V> weight;
    private long retained;
    public DesktopImageCache(ToLongFunction<V> weight){this(Math.min(32*MIB,Runtime.getRuntime().maxMemory()/8),48,weight);}
    DesktopImageCache(long limit,int maximum,ToLongFunction<V> weight){if(limit<0||maximum<1||weight==null)throw new IllegalArgumentException("image cache budget/weight");this.limit=limit;this.maximum=maximum;this.weight=weight;}
    public V get(String key){Entry<V> value=entries.get(key);return value==null?null:value.value;}
    /** Raw decoding occurs before its exact size is known. */
    public void beforeLoad(){trim(limit/2);}
    /** Include temporary ARGB conversion buffers in the caller's estimate. */
    public void reserve(long upcoming){if(upcoming<0)throw new IllegalArgumentException("negative image construction bytes");trim(upcoming>=limit?0:limit-upcoming);}
    public void put(String key,V value){if(key==null||value==null)throw new IllegalArgumentException("image cache entry");long bytes=weight.applyAsLong(value);if(bytes<0)throw new IllegalArgumentException("negative image resident bytes");Entry<V> old=entries.remove(key);if(old!=null)retained-=old.bytes;if(bytes>limit)return;trim(limit-bytes);while(entries.size()>=maximum)evict();entries.put(key,new Entry<>(value,bytes));retained+=bytes;}
    private void trim(long target){while(retained>target&&!entries.isEmpty())evict();}
    private void evict(){Iterator<Map.Entry<String,Entry<V>>> at=entries.entrySet().iterator();Map.Entry<String,Entry<V>> value=at.next();retained-=value.getValue().bytes;at.remove();}
    long retainedBytes(){return retained;}long limitBytes(){return limit;}int size(){return entries.size();}
}
