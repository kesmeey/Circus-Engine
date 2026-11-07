package org.circus.core;

import java.io.IOException;

/** A borrowed live byte owner. A typed local has no numeric native address.
 *DWORD reads finish before a consumer starts its forward destination write. */
public interface Pc1ByteSource {
    int unsignedByte(int offset)throws IOException;
    default int nativeWord(int offset)throws IOException{int word=0;for(int n=0;n<4;n++)word|=(unsignedByte(offset+n)&255)<<(8*n);return word;}
    interface Pointer extends Pc1ByteSource {
        void writeByte(int offset,int value)throws IOException;
        default void writeWord(int offset,int value)throws IOException{for(int n=0;n<4;n++)writeByte(offset+n,value>>>(8*n));}
    }
    /** A real addressed owner, never a local-buffer coordinate overlay. */
    final class Addressed implements Pointer {
        private final Pc1NativeMemory bus;private final int address;
        private Addressed(Pc1NativeMemory bus,int address){if(bus==null)throw new IllegalArgumentException("PC1 addressed source requires its real native bus");this.bus=bus;this.address=address;}
        public int address(){return address;}
        public Pc1NativeMemory bus(){return bus;}
        public int unsignedByte(int offset)throws IOException{return bus.readAddress(address+offset);}
        public int nativeWord(int offset)throws IOException{return bus.readInt32(address+offset);}
        public void writeByte(int offset,int value)throws IOException{bus.writeAddress(address+offset,value);}
        public void writeWord(int offset,int value)throws IOException{bus.writeInt32(address+offset,value);}
    }
    static Addressed addressed(Pc1NativeMemory bus,int address){return new Addressed(bus,address);}
    /** Retains the exact Frame.Source; no string snapshot or proxy guest VA. */
    static Pc1ByteSource local(final Pc1GlyphCache.Source source)throws IOException{if(source==null)throw new IOException("PC1 typed local source missing");return offset->source.unsignedByte(offset);}
    /** Pointer arithmetic retains real raw32 addresses only for actual addressed
     *owners; a local slice remains a typed alias and preserves nativeWord. */
    static Pc1ByteSource offset(final Pc1ByteSource source,final int delta)throws IOException{if(source==null)throw new IOException("PC1 source slice missing");if(source instanceof Addressed){Addressed a=(Addressed)source;return addressed(a.bus,a.address+delta);}return new Pc1ByteSource(){private int at(int offset)throws IOException{long at=(long)delta+offset;if(at<Integer.MIN_VALUE||at>Integer.MAX_VALUE)throw new IOException("PC1 local source offset leaves known integer coordinates");return(int)at;}public int unsignedByte(int offset)throws IOException{return source.unsignedByte(at(offset));}public int nativeWord(int offset)throws IOException{return source.nativeWord(at(offset));}};}
}
