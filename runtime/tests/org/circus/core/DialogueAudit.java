package org.circus.core;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Reads original MES text without constructing a VM, displaying prose, or launching EXEs. */
public final class DialogueAudit {
    private static String quote(String s){return "\""+s.replace("\\","\\\\").replace("\"","\\\"").replace("\r","\\r").replace("\n","\\n")+"\"";}
    public static void main(String[] args)throws Exception {
        TextCodec codec=TextCodec.identity();
        if(args.length>1){try(DataInputStream in=new DataInputStream(Files.newInputStream(Paths.get(args[1])))){
            int n=in.readInt();if(n<0||n>1024*1024)throw new IOException("Character map size");byte[] from=new byte[n];in.readFully(from);
            byte[] to=Binary.read(in,1024*1024);codec=new TextCodec(new String(from,StandardCharsets.UTF_8),new String(to,StandardCharsets.UTF_8));
        }}
        List<Path> files=new ArrayList<>();try(java.util.stream.Stream<Path> paths=Files.list(Paths.get(args[0]))){paths.filter(p->p.toString().toLowerCase(Locale.ROOT).endsWith(".mes")).forEach(files::add);}Collections.sort(files);
        int scripts=0,speakers=0,bodies=0,plain=0,changed=0,malformed=0;Map<String,Integer> blocked=new TreeMap<>();Map<String,String> first=new TreeMap<>();
        for(Path path:files){MesScript script=new MesScript(path.getFileName().toString(),Files.readAllBytes(path),Profiles.get("dc3dd-a5a8"));scripts++;
            for(MesScript.Record record:script.records())if(record.opcode==0x64||record.opcode==0x65){
                if(record.opcode==0x64)speakers++;else bodies++;
                try{byte[] decoded=script.nativeText(record);String raw=TextCodec.cp932(decoded);if(!codec.apply(raw).equals(raw))changed++;
                    NativeText.plain(decoded,codec,null,null,record.opcode==0x64);plain++;
                }catch(IOException e){String kind=e instanceof NativeText.UnsupportedControl?((NativeText.UnsupportedControl)e).control:e.getMessage();
                    if(kind.equals("Invalid CP932 text"))malformed++;
                    blocked.put(kind,blocked.getOrDefault(kind,0)+1);first.putIfAbsent(kind,script.name+" @PC 0x"+Integer.toHexString(record.offset-script.versionOffset));
                }
            }
        }
        System.out.println("{\"scripts\":"+scripts+",\"speaker_records\":"+speakers+",\"body_records\":"+bodies+",\"plain_subset_records\":"+plain+",\"font_mapping_changed_records\":"+changed+",\"invalid_cp932\":"+malformed+",\"original_game_executed\":false,\"blocked\":[");
        int i=0;for(Map.Entry<String,Integer> e:blocked.entrySet())System.out.println((i++==0?"":",")+"{\"reason\":"+quote(e.getKey())+",\"count\":"+e.getValue()+",\"first\":"+quote(first.get(e.getKey()))+"}");
        System.out.println("]}");
    }
}
