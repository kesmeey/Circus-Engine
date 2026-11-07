package org.circus.core;

/** Bounded retention, LRU order, replacement and construction headroom. */
public final class DesktopImageCacheAudit {
    private static int count;
    private static void check(boolean value,String why){count++;if(!value)throw new AssertionError(why);}
    public static void main(String[] args){
        DesktopImageCache<Long> cache=new DesktopImageCache<>(100,3,v->v);
        cache.put("a",40L);cache.put("b",40L);check(cache.retainedBytes()==80&&cache.size()==2,"sum actual resident weights");check(cache.get("a")==40L,"access returns original value");cache.put("c",40L);check(cache.get("b")==null&&cache.get("a")!=null&&cache.get("c")!=null,"least recently used bytes evicted");check(cache.retainedBytes()==80,"byte bound after eviction");
        cache.reserve(70);check(cache.size()==0&&cache.retainedBytes()==0,"construction includes temporary arrays before allocation");cache.put("a",20L);cache.put("b",20L);cache.put("c",20L);cache.beforeLoad();check(cache.retainedBytes()==40&&cache.get("a")==null,"unknown raw decode starts with half-budget headroom");cache.put("d",10L);cache.put("e",10L);check(cache.size()==3&&cache.get("b")==null,"entry cap also remains bounded");
        cache.put("e",15L);check(cache.get("e")==15L&&cache.retainedBytes()==45,"replacement debits previous item once");cache.put("e",101L);check(cache.get("e")==null&&cache.retainedBytes()==30,"oversized replacement never retains stale previous item");cache.reserve(101);check(cache.retainedBytes()==0,"oversized active image reserves all available cache headroom");Long oversized=101L;cache.put("large",oversized);check(cache.get("large")==null&&oversized==101L,"oversized image may remain with current caller but not cache");
        DesktopImageCache<Long> none=new DesktopImageCache<>(0,48,v->v);none.put("one",1L);check(none.size()==0,"zero budget has no retained allocation");DesktopImageCache<Long> ordinary=new DesktopImageCache<>(v->v);check(ordinary.limitBytes()==Math.min(32L*1024*1024,Runtime.getRuntime().maxMemory()/8),"budget derives from actual heap with32MiB ceiling");
        Crx.Image raw=new Crx.Image(0,0,2,2,24,new byte[16],null);DesktopLegacy.Picture picture=new DesktopLegacy.Picture(raw);check(picture.residentBytes()==1056,"legacy resident includes raw bytes and actual ARGB plane");check(DesktopLegacy.Picture.constructionBytes(raw)==1072,"legacy construction includes temporary conversion plane");
        System.out.println("DesktopImageCacheAudit PASS "+count+"; heap="+Runtime.getRuntime().maxMemory()+"; budget="+ordinary.limitBytes());
    }
}
