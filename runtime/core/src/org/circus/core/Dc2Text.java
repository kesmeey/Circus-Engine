package org.circus.core;
import java.io.*;

/** DM text copy 4254A0. Performance controls are handled separately from prose. */
public final class Dc2Text {
    private Dc2Text(){}
    public static String plain(byte[] raw,TextCodec codec)throws IOException {
        if(raw.length>8191)throw new IOException("DM text buffer limit");
        ByteArrayOutputStream out=new ByteArrayOutputStream();
        for(int n=0;n<raw.length;n++){
            int b=raw[n]&255;
            if(b==0)throw new IOException("NUL in DM text");
            if(b==36){if(++n>=raw.length)throw new IOException("Incomplete DM text marker");continue;}
            out.write(b);
        }
        String value=codec.decode(out.toByteArray());
        if(value.contains("@w")||value.contains("@r")||value.contains("&f")||value.contains("&n"))throw new IOException("Unimplemented DM inline text command");
        return value;
    }
}
