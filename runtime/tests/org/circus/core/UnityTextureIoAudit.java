package org.circus.core;

import java.io.*;
import java.nio.file.*;
import java.util.*;

/** Read-only real-data check of texture bytes, decoded pixels and physical handle counts. */
public final class UnityTextureIoAudit {
    private static final class Meter implements GameResources.Storage {
        final GameResources.Storage base;long opens,closes,reads,bytes;
        Meter(Path root)throws IOException{base=new LocalGameStorage(root);}
        public Set<String> paths(){return base.paths();}
        public byte[] read(String name,int limit)throws IOException{return base.read(name,limit);}
        public Archive.Source source(String name)throws IOException{final Archive.Source original=base.source(name);return new Archive.Source(){
            public long size()throws IOException{return original.size();}
            public byte[] read(long at,int count)throws IOException{opens++;try{byte[] b=original.read(at,count);reads++;bytes+=b.length;return b;}finally{closes++;}}
            public Archive.Reader openReader()throws IOException{opens++;final Archive.Reader in=original.openReader();return new Archive.Reader(){
                public long size()throws IOException{return in.size();}
                public byte[] read(long at,int count)throws IOException{byte[] b=in.read(at,count);reads++;bytes+=b.length;return b;}
                public void close()throws IOException{try{in.close();}finally{closes++;}}
            };}
        };}
        void reset(){opens=closes=reads=bytes=0;}
    }
    public static void main(String[] args)throws Exception{
        if(args.length!=1)throw new IllegalArgumentException("UnityTextureIoAudit GAME_ROOT");
        Meter meter=new Meter(Paths.get(args[0]));UnityCircusResources resources=new UnityCircusResources(new GameResources(meter,"@none"));
        List<String> references=new ArrayList<>(Arrays.asList("system/advmenu_chip","system/save_back","system/sl_chip","system/meswin","bg/bg004_d_no"));
        for(String bundle:new String[]{"stand0","face0","anim0"}){List<String> names=resources.unity.names(bundle,28);references.add(bundle.substring(0,bundle.length()-1)+"/"+names.get(0));}
        for(String reference:references){
            resources.texture(reference);resources.unity.clearCaches();meter.reset();long start=System.nanoTime();UnityResources.Texture texture=resources.texture(reference);long elapsed=System.nanoTime()-start;
            System.out.println(reference+" geometry="+texture.width+"x"+texture.height+" format="+texture.format+" opens="+meter.opens+" closes="+meter.closes+" reads="+meter.reads+" bytes="+meter.bytes+" ms="+elapsed/1000000.0+" data_sha256="+Binary.sha256(texture.data)+" bgra_sha256="+Binary.sha256(texture.image().pixels));
            if(meter.opens!=meter.closes)throw new AssertionError("Texture handle leak: "+reference);
        }
    }
}
