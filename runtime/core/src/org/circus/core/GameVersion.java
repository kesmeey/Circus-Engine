package org.circus.core;

import java.io.*;
import java.util.*;

/** Startup-selected framing; full collection inspection remains a separate audit. */
public final class GameVersion {
    public final String profileId;public final int scriptCount;
    private GameVersion(String id,int count){profileId=id;scriptCount=count;}
    public Profile profile()throws IOException{try{return Profiles.get(profileId);}catch(IllegalArgumentException e){throw new IOException("尚未实现此脚本版本的指令语义："+profileId,e);}}
    /** Native engines validate the script being loaded, rather than reject
     * startup because an unrelated, unused legacy MES remains in the folder. */
    public static GameVersion detect(GameResources files)throws IOException{
        String start="advdata/mes/start.mes";if(!files.paths().contains(start))throw new IOException("缺少原版启动脚本 START.mes");
        Set<String> candidates=candidates(files,start);int count=0;for(String path:files.paths())if(path.startsWith("advdata/mes/")&&path.endsWith(".mes"))count++;
        // A structurally ambiguous START still requires more evidence. Do not
        // prefer a supported host over another valid header interpretation.
        if(candidates.size()!=1)return inspectCollection(files);
        return new GameVersion(candidates.iterator().next(),count);
    }
    /** Strict inventory check; identifying START does not admit any other
     * script for execution. MesVm checks each loaded target's exact contract. */
    public static GameVersion inspectCollection(GameResources files)throws IOException{
        Set<String> common=null;int count=0;
        for(String path:new TreeSet<>(files.paths())){
            if(!path.startsWith("advdata/mes/")||!path.endsWith(".mes"))continue;
            Set<String> current=candidates(files,path);if(common==null)common=current;else common.retainAll(current);
            if(common.isEmpty())throw new IOException("游戏目录混有不同版本的 MES："+path);count++;
        }
        if(count==0)throw new IOException("目录中没有 AdvData/MES/*.mes");
        if(common.size()!=1)throw new IOException("MES 版本无法唯一确定："+new TreeSet<>(common));
        if(!files.paths().contains("advdata/mes/start.mes"))throw new IOException("缺少原版启动脚本 START.mes");
        return new GameVersion(common.iterator().next(),count);
    }
    private static Set<String> candidates(GameResources files,String path)throws IOException{
        Set<String> result=new HashSet<>();
        try(Archive.Reader reader=files.openReader(path)){
            long size=reader.size();if(size<4||size>64*1024*1024)throw new IOException("Invalid MES size: "+path);
            long labels=Binary.u32(read(reader,0,4),0);if(labels>1000000)throw new IOException("MES header count limit: "+path);
            boolean extraMarkerByte=size>=8&&Binary.u32(read(reader,4,4),0)==3;
            Map<Integer,byte[]> markers=new HashMap<>();
            for(MesFormats.Format f:MesFormats.ALL){
                long offset=4+labels*f.stride;int length=f.stride==6||extraMarkerByte?3:f.version<256?1:2;
                if(offset+length>=size)continue;
                byte[] marker=markers.get(f.stride);if(marker==null){marker=read(reader,offset,(int)Math.min(3,size-offset));markers.put(f.stride,marker);}
                if((length==1?Binary.u8(marker,0):Binary.u16(marker,0))==f.version)result.add(f.id);
            }
        }
        if(result.isEmpty())throw new IOException("无法识别 MES 版本："+path);return result;
    }
    private static byte[] read(Archive.Reader reader,long offset,int length)throws IOException{
        byte[] bytes=reader.read(offset,length);if(bytes.length!=length)throw new EOFException("Short MES version header");return bytes;
    }
}
