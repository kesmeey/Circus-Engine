package org.circus.core;

import java.io.*;
import java.nio.file.*;
import java.util.Arrays;

/** Actual d474DC2 Win32 GDI API leaf. Song face/charset134/font16 fallback
 *and FFdouble-byte rules belong to this image, not clean Japanese or PC1CHS.
 *Core raw requested/metrics remain the sole canonical field authority. */
public final class Dc2GdiGlyph implements AutoCloseable,Dc2GlyphCache.Provider {
    private static Path loadedLibrary;
    private final long nativeWindow;
    private final Dc2GlyphMemory.FontState font;
    private long handle;private int lastRequired;
    public Dc2GdiGlyph(Path library,Dc2GlyphMemory.FontState font)throws IOException{this(library,0,font);}
    /**Window0 explicitly selects the screen DC for audits; production supplies
     *its HWND. The native window and HDC never become invented bus pointers. */
    public Dc2GdiGlyph(Path library,long nativeWindow,Dc2GlyphMemory.FontState font)throws IOException{
        if(library==null||font==null)throw new IOException("DC2 GDI library/canonical font fields missing");Path path=library.toAbsolutePath().normalize();
        synchronized(Dc2GdiGlyph.class){if(loadedLibrary==null){if(!Files.isRegularFile(path))throw new IOException("Missing DC2 GDI library "+path);try{System.load(path.toString());}catch(UnsatisfiedLinkError failure){throw new IOException("Cannot load DC2 GDI library",failure);}loadedLibrary=path;}else if(!loadedLibrary.equals(path))throw new IOException("DC2 GDI library already loaded from different path");}
        this.nativeWindow=nativeWindow;this.font=font;handle=openNative(nativeWindow);if(handle==0)throw new IOException("DC2 GDI context not created");
    }
    private void open()throws IOException{if(handle==0)throw new IOException("DC2 GDI owner closed");}
    public synchronized Pc1GlyphRaster.Outline outline(Dc2GlyphCache.Source source,int requested)throws IOException{
        open();clearNative(handle,requested);
        if(font.requestedSize()!=requested){byte[] metrics=selectMetricsNative(handle,requested);font.textMetrics(metrics);font.requestedSize(requested);}
        int character=Dc2GlyphCache.characterCode(source),ascent=le(font.textMetrics(),4);byte[] packet=outlineNative(handle,character,requested,ascent);
        if(packet==null||packet.length!=19248||le(packet,0)!=0x314f4750||le(packet,4)!=requested||le(packet,12)!=character||le(packet,44)<0||le(packet,44)>19200)throw new IOException("DC2 actual GDI outline packet");lastRequired=le(packet,44);
        return new Pc1GlyphRaster.Outline(le(packet,16),le(packet,20),le(packet,24),le(packet,28),le(packet,32),le(packet,36),le(packet,40),Arrays.copyOfRange(packet,48,packet.length));
    }
    public synchronized String environment()throws IOException{open();return environmentNative(handle);}
    public synchronized byte[] scratch()throws IOException{open();return scratchNative(handle);}
    /** Diagnostic last API return, separate from the knownscratch read by
     *native4042C0 even when GDI returnedzero for a blank glyph. */
    public synchronized int lastRequiredByteCount()throws IOException{open();return lastRequired;}
    public static final class State {
        private static final int FORMAT=0x44324732; //D2G2,physicalfont/scratch only
        public final int selectedSize,ansiCodePage;
        public final String faceName,fontIdentity;
        private final byte[] scratch;
        private State(int selected,int acp,String face,String identity,byte[] scratch)throws IOException{if(selected!=12&&selected!=16&&selected!=28&&selected!=40||acp<=0||face==null||face.isEmpty()||face.length()>32||identity==null||!identity.matches("[0-9a-f]{64}")||scratch==null||scratch.length!=19200)throw new IOException("DC2 GDI physical context state");selectedSize=selected;ansiCodePage=acp;faceName=face;fontIdentity=identity;this.scratch=scratch.clone();}
        public byte[] scratchBytes(){return scratch.clone();}
        public void save(DataOutput out)throws IOException{out.writeInt(FORMAT);out.writeInt(selectedSize);out.writeInt(ansiCodePage);out.writeUTF(faceName);out.writeUTF(fontIdentity);out.write(scratch);}
        public static State load(DataInput in)throws IOException{if(in.readInt()!=FORMAT)throw new IOException("DC2 GDI state format");int selected=in.readInt(),acp=in.readInt();String face=in.readUTF(),identity=in.readUTF();byte[] scratch=new byte[19200];in.readFully(scratch);return new State(selected,acp,face,identity,scratch);}
    }
    private static String identity(long handle)throws IOException{return Binary.sha256(fontDataNative(handle));}
    public synchronized State snapshot()throws IOException{open();return new State(selectedNative(handle),ansiCodePageNative(handle),faceNative(handle),identity(handle),scratchNative(handle));}
    public synchronized void restore(State state)throws IOException{
        open();if(state==null)throw new IOException("DC2 GDI saved state missing");long replacement=openNative(nativeWindow);boolean accepted=false;
        try{restoreSelectedNative(replacement,state.selectedSize);if(ansiCodePageNative(replacement)!=state.ansiCodePage||!faceNative(replacement).equals(state.faceName)||!identity(replacement).equals(state.fontIdentity))throw new IOException("DC2 actual GDI saved font environment differs");restoreScratchNative(replacement,state.scratch);long old=handle;handle=replacement;accepted=true;closeNative(old);}finally{if(!accepted&&replacement!=0)closeNative(replacement);}
    }
    public synchronized byte[] saveFontContext()throws IOException{ByteArrayOutputStream out=new ByteArrayOutputStream();snapshot().save(new DataOutputStream(out));return out.toByteArray();}
    public synchronized void restoreFontContext(byte[] bytes)throws IOException{if(bytes==null)throw new IOException("DC2 font context missing");ByteArrayInputStream raw=new ByteArrayInputStream(bytes);State state=State.load(new DataInputStream(raw));if(raw.available()!=0)throw new IOException("DC2 GDI context trailing data");restore(state);}
    public synchronized void close(){if(handle!=0){closeNative(handle);handle=0;}}
    private static int le(byte[] b,int at){return(b[at]&255)|(b[at+1]&255)<<8|(b[at+2]&255)<<16|b[at+3]<<24;}
    private static native long openNative(long window)throws IOException;
    private static native void clearNative(long context,int requested)throws IOException;
    private static native byte[] selectMetricsNative(long context,int requested)throws IOException;
    private static native byte[] outlineNative(long context,int character,int requested,int ascent)throws IOException;
    private static native int selectedNative(long context)throws IOException;
    private static native void restoreSelectedNative(long context,int selected)throws IOException;
    private static native byte[] scratchNative(long context)throws IOException;
    private static native void restoreScratchNative(long context,byte[] bytes)throws IOException;
    private static native String environmentNative(long context)throws IOException;
    private static native int ansiCodePageNative(long context)throws IOException;
    private static native byte[] fontDataNative(long context)throws IOException;
    private static native String faceNative(long context)throws IOException;
    private static native void closeNative(long context);
}
