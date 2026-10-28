package org.circus.core;

import java.io.*;
import java.util.*;

/** Platform resource bridge for the verified DC3R Unity 5.5 asset layout. */
public final class UnityCircusResources extends CircusResources {
    public static final String ID="dc3r-unity-550";
    public final UnityResources unity;
    private final List<String> bundles;
    public UnityCircusResources(GameResources files)throws IOException{super(files,ID);unity=new UnityResources(files);bundles=unity.bundleNames();}
    public static boolean candidate(GameResources files){for(String name:files.paths())if(name.equals("streamingassets/script0")||name.endsWith("/streamingassets/script0"))return true;return false;}
    private List<String> series(String prefix){List<String> found=new ArrayList<>();for(int i=0;bundles.contains(prefix+i);i++)found.add(prefix+i);return found;}
    private String find(String prefix,String name,int type)throws IOException{
        for(String b:series("patch"))if(unity.contains(b,name,type))return b;
        for(String b:series(prefix))if(unity.contains(b,name,type))return b;
        return null;
    }
    public byte[] script(String name)throws IOException{String key=clean(name),b=find("script",key,49);if(b==null)throw new FileNotFoundException("Unity script "+name);return unity.text(b,key);}
    public String identity()throws IOException{return Binary.sha256(script("start")).substring(0,16);}
    private static String clean(String name)throws IOException{return Binary.safePath(name.replace('\\','/')).toLowerCase(Locale.ROOT);}
    private String[] reference(String reference)throws IOException{int slash=reference.indexOf('/');if(slash<=0)throw new IOException("Expected Unity category/name: "+reference);return new String[]{reference.substring(0,slash).toLowerCase(Locale.ROOT),clean(reference.substring(slash+1))};}
    @Override public Crx.Image image(String reference)throws IOException{return texture(reference).image();}
    public UnityResources.Texture texture(String reference)throws IOException{
        String[] r=reference(reference);List<String> search=new ArrayList<>();String category=r[0],name=r[1];
        // This is the native provider's precedence, including resource patch bundles.
        if(category.equals("background"))category="bg";if(category.equals("standface"))category="face";
        if(category.equals("system")){search.add("adult");search.add("anim");}
        if((category.equals("bg")||category.equals("stand"))&&name.startsWith("ev")){search.add("event");search.add("adult");}
        search.add(category);for(String prefix:search){String b=find(prefix,name,28);if(b!=null)return unity.texture(b,name);}
        throw new FileNotFoundException("Unity image "+reference);
    }
    @Override public Xpcm audio(String reference)throws IOException{
        String[] r=reference(reference);String b=find(r[0],r[1],83);if(b==null&&r[0].equals("voice")&&r[1].contains("/")){r[1]=r[1].substring(r[1].lastIndexOf('/')+1);b=find(r[0],r[1],83);}if(b==null)throw new FileNotFoundException("Unity audio "+reference);
        UnityResources.Audio a=unity.audio(b,r[1]);
        return Xpcm.ogg(Fsb5Vorbis.toOgg(a.data));
    }
    public String movie(String name)throws IOException{String path=unity.directory+"movies/"+clean(name);if(!path.endsWith(".mp4"))path+=".mp4";if(!files.paths().contains(path))throw new FileNotFoundException(path);return path;}
}
