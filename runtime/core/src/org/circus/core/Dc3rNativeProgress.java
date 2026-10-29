package org.circus.core;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Read-only conversion of DC3R Unity global.dat into the runtime's persistent progress.
 * Native GlobalMemoryState uses protobuf field 1 for global Flags. Native CG and scene
 * helpers address banks 97/98 using cglist/scenelist IDs; all other settings stay local.
 */
public final class Dc3rNativeProgress {
    public static final int LIMIT_BYTES=8*1024*1024;
    private static final String START_SHA256="4c148e6d6a13e5a339f2c3276eae03a93ac3b3bdce1004e859427a88b65e01ee";
    private static final String CG_SHA256="432cdbe461598fbc6fcd436b920f99c380a29ab82765d7255e94f777d2810d32";
    private static final String SCENE_SHA256="7b5d788bc11fc73e18fe258cc569b11387df7b8fa86a44bd9809a94af9d07729";
    // Original ending/side-story writes are constant 1. Index 0 is the monotonic 0..4
    // chapter-unlock tier computed by ed_flag.inc. Index 8 is sprite state; 49/56 and
    // 60..69 are preferences, and are deliberately absent.
    private static final int[] COMPLETION={0,1,2,3,4,5,6,7,11,20,21,22,23,24,25,28,200,201,202,203};
    private final Map<Integer,Integer> completion=new TreeMap<>();
    private final Map<Integer,String> cg=new TreeMap<>(),scenes=new TreeMap<>();
    public final Summary source;
    public static final class Summary {
        public final int completion,cg,scenes,music,total;
        Summary(int completion,int cg,int scenes){this.completion=completion;this.cg=cg;this.scenes=scenes;music=0;total=completion+cg+scenes;}
        @Override public String toString(){return "completion="+completion+", cg="+cg+", scenes="+scenes+", music=0, total="+total;}
    }
    public static Dc3rNativeProgress parse(byte[] nativeFile,UnityCircusResources data)throws IOException {
        byte[][] verified=verifiedTarget(data);
        return new Dc3rNativeProgress(nativeFile,new String(verified[1],StandardCharsets.UTF_8),new String(verified[2],StandardCharsets.UTF_8));
    }
    /** Slots and global progress share the same verified script/catalogue compatibility gate. */
    public static void validateTarget(UnityCircusResources data)throws IOException {verifiedTarget(data);}
    private static byte[][] verifiedTarget(UnityCircusResources data)throws IOException {
        if(data==null||!UnityCircusResources.ID.equals(data.profileId))throw new IOException("DC3R native progress requires the verified Unity version");
        byte[] start=data.script("start"),cg=data.script("cglist"),scenes=data.script("scenelist");
        if(!Binary.sha256(start).equals(START_SHA256)||!Binary.sha256(cg).equals(CG_SHA256)||!Binary.sha256(scenes).equals(SCENE_SHA256))throw new IOException("此 DC3R 脚本或鉴赏表版本尚未验证，未导入原版进度");
        return new byte[][]{start,cg,scenes};
    }
    // Package-private for self-authored format and merge fixtures; shipping calls verify resources above.
    Dc3rNativeProgress(byte[] nativeFile,String cgList,String sceneList)throws IOException {
        Map<Integer,String> cgCatalogue=catalogue(cgList),sceneCatalogue=catalogue(sceneList);
        NativeProto.Message root=NativeProto.framed(nativeFile,LIMIT_BYTES);Map<Integer,Integer> flags=new HashMap<>();
        for(NativeProto.Field field:root.fields()){
            if(field.number==1){
                NativeProto.Message entry=field.message();for(NativeProto.Field f:entry.fields())if(f.number!=1&&f.number!=2)throw new IOException("Unexpected native flag field");
                int key=entry.required(1).int32();NativeProto.Field value=entry.optional(2);int v=value==null?0:value.int32();
                if(key<655360)throw new IOException("Local flag in native global.dat");
                if(flags.put(key,v)!=null)throw new IOException("Duplicate native global flag "+key);
                if(flags.size()>100000)throw new IOException("Native global flag count limit");
            }else if(field.number==7){
                // ReadLines stores dialogue ordinals +2000, not source line numbers. The
                // existing runtime does not persist that exact model, so never promote
                // these partial reads into whole-script viewed/scene flags.
                if(field.wire!=2)throw new IOException("Invalid native ReadLines wire type");
            }else if(field.number>=2&&field.number<=9){
                root.optional(field.number);field.string(4096); // Validate but do not import menu jumps.
            }else throw new IOException("Unexpected native global.dat field "+field.number);
        }
        if(flags.getOrDefault((101<<16)+999,0)!=1)throw new IOException("Native global.dat is missing its valid-state marker");
        for(int index:COMPLETION){int value=flags.getOrDefault((100<<16)+index,0);if(value<0||value>(index==0?4:1))throw new IOException("Invalid native completion flag "+index);if(value!=0)completion.put(index,value);}
        for(Map.Entry<Integer,Integer> entry:flags.entrySet()){
            int bank=entry.getKey()>>>16,index=entry.getKey()&65535,value=entry.getValue();if(bank!=97&&bank!=98)continue;
            if(value<0||value>1)throw new IOException("Invalid native gallery flag "+bank+":"+index);
            if(value==0)continue;Map<Integer,String> names=bank==97?cgCatalogue:sceneCatalogue;
            String name=names.get(index);if(name==null)throw new IOException("Native gallery ID is not present in this game's catalogue: "+bank+":"+index);
            (bank==97?cg:scenes).put(index,name);
        }
        source=new Summary(completion.size(),cg.size(),scenes.size());
    }
    private static Map<Integer,String> catalogue(String text)throws IOException {
        if(text==null||text.length()>1024*1024)throw new IOException("Native progress catalogue size limit");
        Map<Integer,String> result=new TreeMap<>();Set<String> names=new HashSet<>();
        for(String line:text.replace("\ufeff","").split("\\r?\\n")){
            if(line.trim().isEmpty())continue;String[] values=line.split("\\t",-1);if(values.length!=2)throw new IOException("Invalid native progress catalogue row");
            int id;try{id=Integer.parseInt(values[0]);}catch(NumberFormatException e){throw new IOException("Invalid native progress catalogue ID",e);}
            String name=CircusScrScript.normalizeName(values[1]);if(id<0||id>65535||result.put(id,name)!=null||!names.add(name))throw new IOException("Duplicate or invalid native progress catalogue entry");
        }
        if(result.isEmpty()||result.size()>65536)throw new IOException("Invalid native progress catalogue count");return result;
    }
    public Summary preview(Dc3rHost target)throws IOException{return merge(target,false);}
    public Summary apply(Dc3rHost target)throws IOException{return merge(target,true);}
    private Summary merge(Dc3rHost target,boolean apply)throws IOException {
        if(target==null)throw new IOException("Missing DC3R progress target");int complete=0;
        for(Map.Entry<Integer,Integer> entry:completion.entrySet()){
            int key=(100<<16)+entry.getKey(),old=target.readFlag(key),value=entry.getValue();
            if(entry.getKey()==0?old<value:old==0){complete++;if(apply)target.writeFlag(key,value);}
        }
        int c=gallery(target,97,cg,apply),s=gallery(target,98,scenes,apply);return new Summary(complete,c,s);
    }
    private static int gallery(Dc3rHost target,int bank,Map<Integer,String> values,boolean apply){
        int count=0;Set<String> names=bank==97?target.viewedCg:target.viewedScripts;
        for(Map.Entry<Integer,String> entry:values.entrySet()){
            int key=(bank<<16)+entry.getKey();if(target.readFlag(key)==0||!names.contains(entry.getValue())){count++;if(apply){if(target.readFlag(key)==0)target.writeFlag(key,1);names.add(entry.getValue());}}
        }
        return count;
    }
}
