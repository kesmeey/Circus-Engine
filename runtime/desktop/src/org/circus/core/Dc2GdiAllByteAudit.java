package org.circus.core;
import java.io.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Every256 raw firstbyte through our actualCHS GDI/cache, full-byte export
 *for the private currentd474 cache/projection machine reference. */
public final class Dc2GdiAllByteAudit {
    private static String hex(byte[] b){StringBuilder s=new StringBuilder();for(byte v:b)s.append(String.format(Locale.ROOT,"%02x",v&255));return s.toString();}
    private static String font(Dc2GlyphMemory m)throws IOException{List<String> regions=new ArrayList<>();for(int[] r:new int[][]{{Dc2GlyphMemory.CACHE,Dc2GlyphMemory.TEXT_METRICS+56-Dc2GlyphMemory.CACHE},{Dc2GlyphMemory.FONT_REQUESTED,12}}){byte[] b=new byte[r[1]];for(int n=0;n<b.length;n++)b[n]=(byte)m.readByte(r[0]+n);regions.add("{\"address\":"+r[0]+",\"bytes\":\""+hex(b)+"\"}");}return"["+String.join(",",regions)+"]";}
    public static void main(String[] args)throws Exception{
        if(args.length!=2)throw new IOException("ExpectedDLL/outputDirectory");Path out=Paths.get(args[1]);Files.createDirectories(out);List<String> records=new ArrayList<>(),pointers=new ArrayList<>();int checks=0;
        final Dc2GlyphMemory memory=new Dc2GlyphMemory();try(final Dc2GdiGlyph gdi=new Dc2GdiGlyph(Paths.get(args[0]),memory.fontState)){
            Dc2GlyphCache cache=new Dc2GlyphCache(memory);String initial=font(memory),scratchBefore=hex(gdi.scratch());
            Dc2GlyphCache.Provider recorder=new Dc2GlyphCache.Provider(){public Pc1GlyphRaster.Outline outline(byte[] b,int at,int size)throws IOException{return outline(Dc2GlyphService.known(b,at),size);}public Pc1GlyphRaster.Outline outline(Dc2GlyphCache.Source source,int size)throws IOException{Pc1GlyphRaster.Outline o=gdi.outline(source,size);int first=source.unsignedByte(0),character=first>=129?(first<<8)|source.unsignedByte(1):first;records.add("{\"required\":"+gdi.lastRequiredByteCount()+",\"size\":"+size+",\"character\":"+character+",\"metrics\":\""+hex(memory.fontState.textMetrics())+"\",\"width\":"+o.width+",\"height\":"+o.height+",\"ox\":"+o.originX+",\"oy\":"+o.originY+",\"cell_x\":"+o.cellIncX+",\"cell_y\":"+o.cellIncY+",\"mono\":\""+hex(o.monoBytes())+"\"}");return o;}public byte[] saveFontContext()throws IOException{return gdi.saveFontContext();}public void restoreFontContext(byte[] b)throws IOException{gdi.restoreFontContext(b);}};
            for(int first=0;first<256;first++)for(int size:new int[]{12,28}){NativeGlyphCache.Lookup e;try{e=cache.lookup(Dc2GlyphService.known(new byte[]{(byte)first,64,0},0),size,recorder);}catch(IOException failure){throw new IOException("Actual first="+first+" size="+size,failure);}if(e.address<Dc2GlyphMemory.CACHE||e.address>=Dc2GlyphMemory.TEXT_METRICS+56)throw new AssertionError("Native cachepointer");checks++;pointers.add(Integer.toString(e.address));}
            String doc="{\"pass\":true,\"checks\":"+checks+",\"original_exe_executed\":false,\"actual_win32_gdi\":true,\"initial\":"+initial+",\"initial_scratch\":\""+scratchBefore+"\",\"expected\":"+font(memory)+",\"scratch\":\""+hex(gdi.scratch())+"\",\"selected\":"+gdi.snapshot().selectedSize+",\"pointers\":["+String.join(",",pointers)+"],\"outlines\":["+String.join(",\n",records)+"]}";
            Files.write(out.resolve("all-byte-gdi.json"),doc.getBytes(StandardCharsets.UTF_8));System.out.println("PASS Dc2GdiAllByteAudit "+checks+" checks "+records.size()+" actual mono payloads");
        }
    }
}
