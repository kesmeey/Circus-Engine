package org.circus.runtime;
import java.io.IOException;
/** One worker owns the native decoder and all its buffers. */
final class MpegDecoder {
    static {System.loadLibrary("circus_mpeg");}
    static native long open(String path)throws IOException;
    static native void info(long handle,double[] values)throws IOException;
    static native double video(long handle,int[] pixels)throws IOException;
    /** Decode prediction frames normally, but omit RGB conversion before this time. */
    static native double videoTimed(long handle,int[] pixels,double dropBefore)throws IOException;
    /** Call after seek, before consuming frames; each worker owns a separate handle. */
    static native void streams(long handle,boolean video,boolean audio)throws IOException;
    static native double audio(long handle,short[] pcm)throws IOException;
    static native double seek(long handle,double seconds)throws IOException;
    static native void close(long handle);
}
