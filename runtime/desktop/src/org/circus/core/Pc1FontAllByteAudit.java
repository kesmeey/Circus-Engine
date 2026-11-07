package org.circus.core;
import java.io.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Every256 raw firstbyte through our actualCHS GDI/cache, full-byte export
 *for the private current798897 cache/projection machine reference. */
public final class Pc1FontAllByteAudit {
    private static String hex(byte[] b){StringBuilder s=new StringBuilder();for(byte v:b)s.append(String.format(Locale.ROOT,"%02x",v&255));return s.toString();}
    private static String font(Pc1FontMemory m)throws IOException{List<String> regions=new ArrayList<>();for(int[] r:new int[][]{{Pc1FontMemory.BASE,Pc1FontMemory.END-Pc1FontMemory.BASE},{Pc1FontMemory.REQUESTED,12},{Pc1FontMemory.LITERALS,8}}){byte[] b=new byte[r[1]];for(int n=0;n<b.length;n++)b[n]=(byte)m.readByte(r[0]+n);regions.add("{\"address\":"+r[0]+",\"bytes\":\""+hex(b)+"\"}");}return"["+String.join(",",regions)+"]";}
    public static void main(String[] args)throws Exception{
        if(args.length!=2)throw new IOException("ExpectedDLL/outputDirectory");Path out=Paths.get(args[1]);Files.createDirectories(out);List<String> records=new ArrayList<>(),pointers=new ArrayList<>();int checks=0;
        try(final Pc1GdiGlyph gdi=new Pc1GdiGlyph(Paths.get(args[0]))){
            Pc1GlyphCache cache=Pc1GlyphCache.fromMemory(gdi.fontMemory());String initial=font(gdi.fontMemory()),scratchBefore=hex(gdi.scratch());
            Pc1GlyphCache.Provider recorder=new Pc1GlyphCache.Provider(){public Pc1FontMemory fontMemory(){return gdi.fontMemory();}public Pc1GlyphRaster.Outline outline(byte[] b,int at,int size)throws IOException{return outline(Pc1GlyphCache.known(b,at),size);}public Pc1GlyphRaster.Outline outline(Pc1GlyphCache.Source source,int size)throws IOException{Pc1GlyphRaster.Outline o=gdi.outline(source,size);int first=source.unsignedByte(0),character=first>=129&&first<=254?(first<<8)|source.unsignedByte(1):first;records.add("{\"required\":"+gdi.lastRequiredByteCount()+",\"size\":"+size+",\"character\":"+character+",\"metrics\":\""+hex(gdi.fontMemory().textMetrics())+"\",\"width\":"+o.width+",\"height\":"+o.height+",\"ox\":"+o.originX+",\"oy\":"+o.originY+",\"cell_x\":"+o.cellIncX+",\"cell_y\":"+o.cellIncY+",\"mono\":\""+hex(o.monoBytes())+"\"}");return o;}public byte[] saveFontContext()throws IOException{return gdi.saveFontContext();}public void restoreFontContext(byte[] b)throws IOException{gdi.restoreFontContext(b);}};
            for(int first=0;first<256;first++)for(int size:new int[]{12,28}){Pc1GlyphCache.Entry e;try{e=cache.lookup(new byte[]{(byte)first,64,0},0,size,recorder);}catch(IOException failure){throw new IOException("Actual first="+first+" size="+size,failure);}if(e.nativeAddress<Pc1FontMemory.BASE||e.nativeAddress>=Pc1FontMemory.END)throw new AssertionError("Native cachepointer");checks++;pointers.add(Integer.toString(e.nativeAddress));}
            String doc="{\"pass\":true,\"checks\":"+checks+",\"original_exe_executed\":false,\"actual_win32_gdi\":true,\"initial\":"+initial+",\"initial_scratch\":\""+scratchBefore+"\",\"expected\":"+font(gdi.fontMemory())+",\"scratch\":\""+hex(gdi.scratch())+"\",\"selected\":"+gdi.snapshot().selectedSize+",\"pointers\":["+String.join(",",pointers)+"],\"outlines\":["+String.join(",\n",records)+"]}";
            Files.write(out.resolve("all-byte-gdi.json"),doc.getBytes(StandardCharsets.UTF_8));System.out.println("PASS Pc1FontAllByteAudit "+checks+" checks "+records.size()+" actual mono payloads");
        }
    }
}
