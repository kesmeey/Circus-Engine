package org.circus.core;

import java.io.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Actual Win32CreateFont/GDI lifecycle of this rewrite, no target execution.
 *Opaque HFONT/HDC are typed64. Static instruction evidence supplies ordering. */
public final class Pc1FontStartupAudit {
    private static int checks;
    private static void check(boolean v,String why){checks++;if(!v)throw new AssertionError(why);}
    private interface Action{void run()throws Exception;}
    private static void reject(Action action,String why)throws Exception{boolean failed=false;try{action.run();}catch(IOException expected){failed=true;}check(failed,why);}
    private static byte[] raw(Pc1FontMemory m)throws IOException{ByteArrayOutputStream out=new ByteArrayOutputStream();m.save(new DataOutputStream(out));return out.toByteArray();}
    private static byte[] wire(Pc1GlyphCache c,Pc1GdiGlyph p)throws IOException{ByteArrayOutputStream out=new ByteArrayOutputStream();c.save(new DataOutputStream(out),p);return out.toByteArray();}
    public static void main(String[] args)throws Exception{
        if(args.length!=2)throw new IOException("Expected builtDLL and outputdirectory");Path output=Paths.get(args[1]);Files.createDirectories(output);List<String> environment=new ArrayList<>();
        try(Pc1GdiGlyph.FontSet fonts=Pc1GdiGlyph.FontSet.empty(Paths.get(args[0]))){
            check(fonts.createdCount()==0&&fonts.contextCount()==0,"Allocating typedFontSet does not callCreateFont/GetDC/CreateDIB");
            Pc1FontMemory memory=new Pc1FontMemory();memory.requestedSize(99);memory.variableReplacement(7);memory.fixedReplacement(-2);byte[] metrics=new byte[56];for(int n=0;n<56;n++)metrics[n]=(byte)(n*7);memory.textMetrics(metrics);
            reject(()->fonts.initialize404230(0,memory),"404230 cannot invent absent startupfonts");check(memory.requestedSize()==99&&fonts.contextCount()==0,"Failed stage leaves raw requested and contextownership unchanged");
            int[] sizes={22,28,12,32,40},addresses={0x94c648,0x94c640,0x94c644,0x94c64c,0x94c650};long[] handles=new long[5];byte[] face={(byte)0xba,(byte)0xda,(byte)0xcc,(byte)0xe5,0};
            for(int n=0;n<5;n++){handles[n]=fonts.createFont(sizes[n]*2,sizes[n],face);fonts.font(addresses[n],handles[n]);check(handles[n]!=0&&fonts.font(addresses[n])==handles[n]&&fonts.contextCount()==0,"Real WinMainHFONT before404230 stage"+n);for(int j=0;j<n;j++)check(handles[n]!=handles[j],"Each CreateFont produces independently owned HFONT");}
            reject(()->fonts.createFont(44,22,face),"Only five nativeWinMain fontslots");
            byte[] saved;String environmentSaved;
            try(Pc1GdiGlyph gdi=fonts.initialize404230(0,memory)){
                check(fonts.createdCount()==5&&fonts.contextCount()==1&&gdi.snapshot().selectedSize==28,"404230 borrows fiveexistingfonts and selects28");check(memory.requestedSize()==0&&memory.fixedReplacement()==-2&&memory.variableReplacement()==7&&Arrays.equals(memory.textMetrics(),metrics),"404230 writes only requested0, notmetrics/cursors");
                reject(fonts::close,"Borrowed fontowner cannot beclosed before itsHDC");
                memory.requestedSize(0);memory.writeInt(Pc1FontMemory.METRICS+4,0);Pc1GlyphRaster.Outline initial=gdi.outline(new byte[]{65,0},0,0);check(initial.ascent==0&&gdi.snapshot().selectedSize==28,"Initialrequest0 bypasses metrics and retainsphysical28");
                final boolean[] afterMetrics={false};gdi.outline(n->{afterMetrics[0]=memory.requestedSize()==12&&memory.ascent()>0;return n==0?65:0;},12);check(afterMetrics[0],"Source bytes are read after font/metric/cachedrequest writes");
                Pc1GlyphCache cache=Pc1GlyphCache.fromMemory(memory);cache.lookup(new byte[]{65,0},0,12,gdi);gdi.outline(new byte[]{66,0},0,28);byte[] physical=gdi.saveFontContext();Pc1GlyphCache.Entry hit=cache.lookup(new byte[]{65,0},0,12,gdi);check(hit.hit&&Arrays.equals(physical,gdi.saveFontContext())&&memory.requestedSize()==28,"Cachehit leaves raw andphysicalfont unchanged");
                gdi.outline(new byte[]{65,0},0,17);Pc1GdiGlyph.State state=gdi.snapshot();byte[] physicalWire=gdi.saveFontContext();memory.requestedSize(40);memory.writeInt(Pc1FontMemory.METRICS+4,321);gdi.restore(state);check(memory.requestedSize()==40&&memory.ascent()==321&&gdi.snapshot().selectedSize==22&&Arrays.equals(physicalWire,gdi.saveFontContext()),"Physicalrestore never shadows or rolls back canonical rawfields");
                memory.requestedSize(17);gdi.outline(new byte[]{65,0},0,12);saved=wire(cache,gdi);environmentSaved=gdi.environment();environment.add(environmentSaved);Files.write(output.resolve("cache.p1k2"),saved);
                byte[] rawBefore=raw(memory),wrong=gdi.saveFontContext();DataInputStream data=new DataInputStream(new ByteArrayInputStream(wrong));data.readInt();data.readInt();data.readInt();data.readUTF();int identityAt=wrong.length-data.available()+2;wrong[identityAt]=wrong[identityAt]=='0'?(byte)'1':(byte)'0';final byte[] corrupted=wrong;reject(()->gdi.restoreFontContext(corrupted),"Environmentidentity rejection beforephysicalswap");check(Arrays.equals(rawBefore,raw(memory))&&environmentSaved.equals(gdi.environment()),"Rejected physicalrestore leavescanonicalraw andoldHDC intact");
                gdi.outline(new byte[]{65,0},0,40);reject(()->gdi.outline(new byte[]{65,0},0,49),"Scratchclear rejects only after known19200B prefix");check(Arrays.equals(gdi.scratch(),new byte[19200])&&memory.requestedSize()==40,"Oversizedclear preserves alreadywritten prefix anddoes not queryfont");
            }
            check(fonts.contextCount()==0&&fonts.createdCount()==5,"4042A0 releasesHDC/DIB butnotfivefonts");
            Pc1FontMemory restoredMemory=new Pc1FontMemory();restoredMemory.requestedSize(12345);
            try(Pc1GdiGlyph restored=fonts.contextForRestore(0,restoredMemory)){
                check(restoredMemory.requestedSize()==12345&&fonts.createdCount()==5,"Restoreallocation neither resetsraw nor repeatsCreateFont");Pc1GlyphCache cache=Pc1GlyphCache.load(new DataInputStream(new ByteArrayInputStream(saved)),restored);check(cache.fontMemory()==restoredMemory&&Arrays.equals(wire(cache,restored),saved)&&restored.environment().equals(environmentSaved),"P1K2 restores samerawowner plusindependentphysicalcontext exactly");
            }
            for(long handle:handles)check(fonts.deleteObject(handle),"DeleteObject afterfontcontexts close");for(int n=0;n<5;n++)check(fonts.font(addresses[n])==handles[n],"NativeHFONTglobal retains stalevalue afterDeleteObject");
        }
        try(Pc1GdiGlyph convenience=new Pc1GdiGlyph(Paths.get(args[0]))){check(convenience.snapshot().selectedSize==28&&convenience.fontMemory().requestedSize()==0,"Auditconvenience constructor retains explicit offscreenbehavior");environment.add(convenience.environment());}
        Files.write(output.resolve("environment.txt"),environment,StandardCharsets.UTF_8);String result="{\"pass\":true,\"checks\":"+checks+",\"actual_win32_gdi\":true,\"original_exe_executed\":false,\"hwnd\":0,\"offscreen_audit\":true}";Files.write(output.resolve("result.json"),result.getBytes(StandardCharsets.UTF_8));System.out.println("PASS Pc1FontStartupAudit "+checks);
    }
}
