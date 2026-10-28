package org.circus.runtime;

import java.nio.file.*;
import org.circus.core.Fsb5Vorbis;

/** Lossless audio-container conversion diagnostic; input and output must be explicit. */
public final class Fsb5VorbisAudit {
    public static void main(String[] args)throws Exception{
        if(args.length!=2)throw new IllegalArgumentException("Fsb5VorbisAudit INPUT.fsb OUTPUT.ogg");
        byte[] ogg=Fsb5Vorbis.toOgg(Files.readAllBytes(Paths.get(args[0])));Files.write(Paths.get(args[1]),ogg);System.out.println("PASS FSB5 -> Ogg bytes="+ogg.length);
    }
}
