package org.circus.core;
import java.io.*;
/** Encoding applies to prose only. Verified localized MES fingerprints avoid guessing by folder/EXE name. */
public final class GameText {
    private GameText(){}
    public static TextCodec detect(GameResources files,Profile profile)throws IOException{
        String name,hash;
        if(profile.id.equals("dc2pc-5769")){name="fst_1216_a1_cmn";hash="7fbc02bc2b9634d376c7771c43c2d7c8e12ce46311347316353b5db7f87feabd";}
        else if(profile.id.equals("dcpc-2063")){name="0223A_F01";hash="69b250bc06c5150cba7d925920523adb14124d95d41f5dc2407d500d45036df1";}
        else return configuration(files);
        try{if(Binary.sha256(files.read("AdvData/MES/"+name+".mes",2*1024*1024)).equals(hash))return new TextCodec("","","GBK");}catch(FileNotFoundException absent){}
        return TextCodec.identity();
    }
    public static TextCodec configuration(GameResources files)throws IOException{
        byte[] raw;try{raw=files.read("uif_config.json",2*1024*1024);}catch(FileNotFoundException e){return TextCodec.identity();}
        Object parsed=DataJson.parse(new String(raw,java.nio.charset.StandardCharsets.UTF_8));
        if(!(parsed instanceof java.util.Map))throw new IOException("Invalid uif_config.json root");
        java.util.Map<?,?> root=(java.util.Map<?,?>)parsed;
        Object tunnel=root.get("tunnel_decoder"),sub=root.get("character_substitution");
        if(tunnel instanceof java.util.Map&&Boolean.TRUE.equals(((java.util.Map<?,?>)tunnel).get("enable")))throw new IOException("Enabled tunnel decoder is not implemented");
        if(!(sub instanceof java.util.Map)||!Boolean.TRUE.equals(((java.util.Map<?,?>)sub).get("enable")))return TextCodec.identity();
        java.util.Map<?,?> map=(java.util.Map<?,?>)sub;Object source=map.get("source_characters"),target=map.get("target_characters");
        if(!(source instanceof String)||!(target instanceof String))throw new IOException("Invalid character substitution table");
        return new TextCodec((String)source,(String)target);
    }
}
