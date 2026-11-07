package org.circus.core;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Exercise context ownership at every actual26 call site; no original EXE or prose. */
public final class NativePc1ContextAudit {
    public static void main(String[] args)throws Exception{
        if(args.length!=1)throw new IOException("Expected exact PC1 AdvData/MES directory");
        Path directory=Paths.get(args[0]);List<Path> paths=new ArrayList<>();
        try(DirectoryStream<Path> input=Files.newDirectoryStream(directory)){for(Path p:input)if(Files.isRegularFile(p)&&p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".mes"))paths.add(p);}
        Collections.sort(paths);List<String> rows=new ArrayList<>(),candidates=new ArrayList<>();int checked=0,bytesChecked=0,prepared=0;
        for(Path path:paths){
            String filename=path.getFileName().toString(),name=filename.substring(0,filename.length()-4);MesScript caller=new MesScript(name,Files.readAllBytes(path),Profiles.get("dcpc-2063"));
            for(MesScript.Record record:caller.records()){
                if(record.payloadOwner<0&&record.opcode==0x1b){
                    PcHost h=Pc1SystemContextTests.host("dcpc-2063");h.position(caller,record.offset);h.variables.write(0,0,73);
                    Pc1SaveContext context=new Pc1SaveContext();context.prepare(h,record.offset,0);Pc1Frame frame=context.candidate();MesScript.Record payload=caller.stringPayload(record.end,record.offset);
                    if(frame.returnPoint().pc!=record.end||frame.returnPoint().pc==payload.end||!frame.returnPoint().sha256.equals(caller.sha256)||frame.rawCompanionMarker()!=payload.opcode||!Arrays.equals(frame.rawOwnedString(),payload.bytes))throw new IOException("1B native pre-companion cursor/raw owner mismatch");
                    byte[] bytes=Pc1SaveContextTests.save(context);Pc1SaveContext restored=new Pc1SaveContext();restored.load(new DataInputStream(new ByteArrayInputStream(bytes)));if(!Arrays.equals(bytes,Pc1SaveContextTests.save(restored)))throw new IOException("1B candidate roundtrip mismatch");
                    h.variables.write(0,0,99);restored.candidate().apply(h);if(h.variables.read(0,0)!=73)throw new IOException("1B did not preserve candidate ordinary snapshot");
                    candidates.add("{\"script\":\""+name+"\",\"sha256\":\""+caller.sha256+"\",\"offset\":"+record.offset+",\"candidate_pc\":"+frame.returnPoint().pc+",\"companion_end\":"+payload.end+",\"flag\":"+context.flag()+",\"raw_marker\":"+frame.rawCompanionMarker()+",\"raw_string_hex\":\""+Binary.hex(frame.rawOwnedString())+"\"}");prepared++;
                }
                if(record.payloadOwner>=0||record.opcode!=0x26)continue;
                MesScript.Instruction instruction=caller.instruction(record.offset);if(instruction.stringPayload==null)throw new IOException("26 payload is not owned");
                PcHost h=Pc1SystemContextTests.host("dcpc-2063");h.position(caller,record.offset);
                Pc1SystemContext context=Pc1SystemContext.capture(h,name,instruction.next,instruction.args[0],instruction.stringPayload.bytes,Pc1SystemContextTests.framebuffer(),0);
                context.applyEntry(h);for(int b=0;b<10;b++)for(int n=0;n<100;n++)h.variables.write(b,n,(checked+b+n)&255);
                byte[] saved=Pc1SystemContextTests.save(context);Pc1SystemContext restored=Pc1SystemContext.load(new DataInputStream(new ByteArrayInputStream(saved)));
                if(!Arrays.equals(saved,Pc1SystemContextTests.save(restored)))throw new IOException("Context roundtrip mismatch");
                Pc1SystemContext.ReturnPoint point=restored.applyReturn(h);
                if(!point.script.equals(name)||!point.sha256.equals(caller.sha256)||point.pc!=instruction.next)throw new IOException("Caller identity/cursor mismatch");
                if(!Arrays.equals(restored.rawTarget(),instruction.stringPayload.bytes))throw new IOException("Raw target changed");
                for(int b=0;b<10;b++)for(int n=0;n<100;n++){if(h.variables.read(b,n)!=((checked+b+n)&255))throw new IOException("Menu ordinary byte rolled back");bytesChecked++;}
                rows.add("{\"script\":\""+name+"\",\"sha256\":\""+caller.sha256+"\",\"offset\":"+record.offset+",\"return_pc\":"+instruction.next+",\"raw_target_hex\":\""+Binary.hex(restored.rawTarget())+"\"}");checked++;
            }
        }
        if(checked!=29||prepared!=661)throw new IOException("Expected29 native calls and661 preparations, found "+checked+"/"+prepared);
        String json="{\"scope\":\"Actual PC1 MES bytes, all29 opcode26 sites and661 opcode1B sites; typed owner/cursor/SHA/serialization/ordinary preservation exercised with synthetic host state and2x2 pixel framebuffer. No route execution, native EXE, game prose, or full framebuffer fidelity claim.\",\"call_sites_checked\":"+checked+",\"save_candidates_checked\":"+prepared+",\"ordinary_bytes_checked\":"+bytesChecked+",\"sites\":["+String.join(",",rows)+"],\"save_candidates\":["+String.join(",",candidates)+"]}";
        System.out.write(json.getBytes(StandardCharsets.UTF_8));System.out.write('\n');
    }
}
