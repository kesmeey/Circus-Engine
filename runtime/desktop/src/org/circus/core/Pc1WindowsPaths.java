package org.circus.core;

import java.io.*;
import java.nio.file.*;
import java.util.Arrays;

/** Real Windows ANSI path services. Game path construction belongs to the
 *addressed Pc1NativePaths owner; this class does not search or repair paths. */
public final class Pc1WindowsPaths {
    private static Path loadedLibrary;
    public Pc1WindowsPaths(Path library)throws IOException{
        if(library==null)throw new IOException("PC1 ANSI path JNI library missing");Path selected=library.toAbsolutePath().normalize();
        synchronized(Pc1WindowsPaths.class){
            if(loadedLibrary==null){if(!Files.isRegularFile(selected))throw new FileNotFoundException(selected.toString());try{System.load(selected.toString());}catch(UnsatisfiedLinkError failure){throw new IOException("PC1 ANSI path JNI load failed",failure);}loadedLibrary=selected;}
            else if(!loadedLibrary.equals(selected))throw new IOException("PC1 ANSI path JNI already loaded from a different file");
        }
    }
    public int ansiCodePage(){return codePageNative();}
    /** OnlyFFFFFFFF means missing to40E5A0, including ordinary OS errors. */
    public int attributes(byte[] rawCString)throws IOException{if(rawCString==null)throw new IOException("PC1 GetFileAttributesA path missing");return attributesNative(rawCString);}
    /** CString is consumed only at the actual OS-query leaf. There is no
     *invented native address for a caller-owned typed local. */
    public int attributes(Pc1ByteSource liveCString)throws IOException{return attributesNative(cstring(liveCString));}
    public static byte[] cstring(Pc1ByteSource source)throws IOException{if(source==null)throw new IOException("PC1 ANSI live source missing");ByteArrayOutputStream out=new ByteArrayOutputStream();for(int n=0;;n++){int value=source.unsignedByte(n)&255;if(value==0)return out.toByteArray();out.write(value);}}
    public byte[] encode(String value)throws IOException{if(value==null||value.indexOf(0)>=0)throw new IOException("PC1 ANSI path contains embeddedNUL");return encodeNative(value);}
    public String decode(byte[] raw)throws IOException{if(raw==null)throw new IOException("PC1 ANSI path missing");for(byte b:raw)if(b==0)throw new IOException("PC1 logical ANSI path contains embeddedNUL");return decodeNative(raw);}
    /** Existing Java stream APIs are admitted only for an exact ACP round trip.
     *NativeA queries still accept every raw byte, including invalid ACP input. */
    public Path exactPath(byte[] raw)throws IOException{String value=decode(raw);if(!Arrays.equals(raw,encode(value)))throw new IOException("PC1 ANSI path cannot round trip through JavaPath");try{return Paths.get(value);}catch(InvalidPathException failure){throw new IOException("PC1 ANSI path cannot be represented by JavaPath",failure);}}
    public byte[] workingDirectory()throws IOException{return directoryNative();}
    private static native int codePageNative();
    private static native int attributesNative(byte[] raw)throws IOException;
    private static native byte[] encodeNative(String value)throws IOException;
    private static native String decodeNative(byte[] raw)throws IOException;
    private static native byte[] directoryNative()throws IOException;
}
