package org.circus.runtime;

import java.io.*;
import java.nio.charset.StandardCharsets;
import org.json.*;
import org.circus.core.*;

/** Reads data-only display mapping from the selected game; never loads its DLLs. */
public final class TextConfiguration {
    private TextConfiguration(){}
    public static TextCodec load(GameResources resources)throws IOException {
        byte[] bytes;try{bytes=resources.read("uif_config.json",2*1024*1024);}catch(FileNotFoundException absent){return TextCodec.identity();}
        return parse(bytes);
    }
    static TextCodec parse(byte[] bytes)throws IOException {
        String value=new String(bytes,StandardCharsets.UTF_8);if(value.startsWith("\uFEFF"))value=value.substring(1);
        try{
            JSONObject root=new JSONObject(value),tunnel=root.optJSONObject("tunnel_decoder"),map=root.optJSONObject("character_substitution");
            if(tunnel!=null&&tunnel.optBoolean("enable",false))throw new IOException("Enabled tunnel decoder is not implemented");
            if(map==null||!map.getBoolean("enable"))return TextCodec.identity();
            return new TextCodec(map.getString("source_characters"),map.getString("target_characters"));
        }catch(JSONException e){throw new IOException("Invalid uif_config.json character mapping",e);}
    }
}
