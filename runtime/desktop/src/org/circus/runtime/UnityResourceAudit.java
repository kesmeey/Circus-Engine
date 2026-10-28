package org.circus.runtime;

import java.nio.file.*;
import java.security.*;
import java.util.*;
import org.circus.core.*;

/** Read-only audit of original Unity resources; optionally export one decoded image for inspection. */
public final class UnityResourceAudit {
    private static final class CountedStorage implements GameResources.Storage {
        final GameResources.Storage storage;int reads;
        CountedStorage(Path root)throws Exception{storage=new LocalGameStorage(root);}
        public Set<String> paths(){return storage.paths();}
        public byte[] read(String name,int limit)throws java.io.IOException{return storage.read(name,limit);}
        public Archive.Source source(String name)throws java.io.IOException{Archive.Source source=storage.source(name);return new Archive.Source(){public long size()throws java.io.IOException{return source.size();}public byte[] read(long at,int count)throws java.io.IOException{reads++;return source.read(at,count);}};}
    }
    private static String hash(byte[] b)throws Exception{StringBuilder s=new StringBuilder();for(byte v:MessageDigest.getInstance("SHA-256").digest(b))s.append(String.format("%02x",v&255));return s.toString();}
    public static void main(String[] args)throws Exception{
        if(args.length<1)throw new IllegalArgumentException("UnityResourceAudit GAME_ROOT [IMAGE_OUTPUT]");
        CountedStorage storage=new CountedStorage(Paths.get(args[0]));UnityResources r=new UnityResources(new GameResources(storage,"@none"));int scripts=0;MessageDigest digest=MessageDigest.getInstance("SHA-256");
        if(r.contains("voice0","missing-script/rcc_sys5",83))throw new AssertionError("Unknown container context exists");Fsb5Vorbis.toOgg(r.audio("voice0","rcc_sys5").data);if(storage.reads>32)throw new AssertionError("First voice enumerated unrelated clip names: "+storage.reads+" source reads");System.out.println("PASS first voice and missing context use container index: source_reads="+storage.reads);
        for(String name:r.names("script0",49)){byte[] bytes=r.text("script0",name);digest.update(bytes);if(bytes.length>=12&&new String(bytes,0,12,"US-ASCII").equals("CircusScr1.0"))scripts++;}
        if(scripts!=634)throw new AssertionError("Expected 634 Circus scripts, got "+scripts);System.out.println("PASS script0 text_assets="+r.names("script0",49).size()+" CircusScr="+scripts+" start_sha256="+hash(r.text("script0","start")));
        for(String bundle:Arrays.asList("system0","bg0","stand0","face0","anim0")){List<String> names=r.names(bundle,28);UnityResources.Texture t=r.texture(bundle,names.get(0));Crx.Image im=t.image();System.out.println("PASS "+bundle+" textures="+names.size()+" sample="+t.name+" format="+t.format+" geometry="+t.width+"x"+t.height+" bgra_sha256="+hash(im.pixels));}
        for(String bundle:Arrays.asList("bgm0","se0","voice0")){List<String> names=r.names(bundle,83);UnityResources.Audio a=r.audio(bundle,names.get(0));if(a.data.length<4||!new String(a.data,0,4,"US-ASCII").equals("FSB5"))throw new AssertionError("Expected FSB5");System.out.println("PASS "+bundle+" clips="+names.size()+" sample="+a.name+" sample_rate="+a.sampleRate+" channels="+a.channels+" data_sha256="+hash(a.data));}
        byte[] duplicate=r.audio("voice0","dc3_1_1212_c1_rcc_rcc12").data;if(!Arrays.equals(duplicate,r.audio("voice0","dc3_1_1212_c1_hmn/dc3_1_1212_c1_rcc_rcc12").data))throw new AssertionError("Equivalent duplicate mismatch");
        byte[] first=r.audio("voice0","dc3_3_1311_f1_tru/dc3_3_1311_f1_tru_ysy01").data,second=r.audio("voice0","dc3_3_1315_f1_tru/dc3_3_1311_f1_tru_ysy01").data;if(Arrays.equals(first,second))throw new AssertionError("Expected distinct contextual voices");boolean rejected=false;try{r.audio("voice0","dc3_3_1311_f1_tru_ysy01");}catch(java.io.IOException expected){rejected=true;}if(!rejected)throw new AssertionError("Distinct ambiguous voice silently selected");System.out.println("PASS duplicate voices: identical clips collapse; distinct clips require original container directory");
        if(args.length>1){Crx.Image im=r.image("system0","title_back21");java.awt.image.BufferedImage bi=new java.awt.image.BufferedImage(im.width,im.height,java.awt.image.BufferedImage.TYPE_INT_ARGB);bi.setRGB(0,0,im.width,im.height,im.argb(),0,im.width);javax.imageio.ImageIO.write(bi,"PNG",Paths.get(args[1]).toFile());}
        r.clearCaches();
    }
}
