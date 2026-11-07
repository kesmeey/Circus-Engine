package org.circus.core;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;

/** Independent Java framing audit against a Python corpus inventory. Never executes a VM or EXE. */
public final class NativeMesCorpusAudit {
    private NativeMesCorpusAudit() {}

    @SuppressWarnings("unchecked")
    private static Map<String,Object> object(Object value) { return (Map<String,Object>)value; }
    @SuppressWarnings("unchecked")
    private static List<Object> array(Object value) { return (List<Object>)value; }
    private static int number(Object value) { return ((Number)value).intValue(); }
    private static boolean sameCounts(Map<String,Object> expected,Map<String,Integer> actual){
        if(expected.size()!=actual.size())return false;for(Map.Entry<String,Integer> entry:actual.entrySet())if(!expected.containsKey(entry.getKey())||number(expected.get(entry.getKey()))!=entry.getValue())return false;return true;
    }

    /** Known format data may be framed for research without inventing executable semantics. */
    private static Profile framingOnly(Map<String,Object> format,byte[] raw)throws IOException {
        Map<String,Integer> starts=new HashMap<>();
        for(Object value:array(format.get("ranges"))){
            List<Object> range=array(value);starts.put((String)range.get(0),number(range.get(1)));
        }
        int version=number(format.get("version"));
        int stride=format.get("layout").equals("offset2")?6:4;
        int bytes=stride==6||Binary.u32(raw,4)==3?3:version<256?1:2;
        List<Object> alternatives=format.containsKey("alt_versions")?array(format.get("alt_versions")):Collections.emptyList();
        int[] accepted=new int[alternatives.size()];for(int i=0;i<accepted.length;i++)accepted[i]=number(alternatives.get(i));
        return new Profile((String)format.get("id"),version,stride,bytes,
                starts.containsKey("u8str")?starts.get("u8str"):starts.get("string"),
                starts.get("string"),starts.get("encoded_string"),starts.get("u16x4"),
                -1,-1,-1,-1,false,false,false,false,Variables.Model.BYTE_WRAP,
                Collections.emptyMap(),accepted,false,false,false);
    }

    private static String layoutHash(MesScript script)throws Exception {
        MessageDigest digest=MessageDigest.getInstance("SHA-256");byte[] recordBytes=new byte[13];
        for(MesScript.Record record:script.records()){
            for(int n=0;n<4;n++){recordBytes[n]=(byte)(record.offset>>>(8*n));recordBytes[n+4]=(byte)(record.end>>>(8*n));}
            for(int n=0;n<4;n++)recordBytes[n+9]=(byte)(record.payloadOwner>>>(8*n));
            recordBytes[8]=(byte)record.opcode;digest.update(recordBytes);
        }
        return Binary.hex(digest.digest());
    }

    private static void problem(List<Object> mismatches,String root,String script,String property,Object python,Object java) {
        Map<String,Object> item=new LinkedHashMap<>();item.put("root",root);item.put("path",script);
        item.put("property",property);item.put("python",python);item.put("java",java);mismatches.add(item);
    }

    public static void main(String[] args)throws Exception {
        if(args.length<1||args.length>2)throw new IllegalArgumentException("Usage: NativeMesCorpusAudit CORPUS_JSON [FORMAT_CATALOG_JSON]");
        Map<String,Object> corpus=object(DataJson.parse(new String(Files.readAllBytes(Paths.get(args[0])),StandardCharsets.UTF_8)));
        Path catalog=args.length==2?Paths.get(args[1]):Paths.get("profiles/dc123.json");
        Map<String,Map<String,Object>> formats=new HashMap<>();
        for(Object value:array(object(DataJson.parse(new String(Files.readAllBytes(catalog),StandardCharsets.UTF_8))).get("profiles"))){
            Map<String,Object> format=object(value);formats.put((String)format.get("id"),format);
        }
        List<Object> games=new ArrayList<>(),mismatches=new ArrayList<>();long totalRecords=0;
        int totalScripts=0,totalValid=0,totalInvalid=0,totalFragments=0;
        for(Object gameValue:array(corpus.get("games"))){
            Map<String,Object> expected=object(gameValue);String root=(String)expected.get("root"),pid=(String)expected.get("collection_profile");
            GameResources resources=new GameResources(new LocalGameStorage(Paths.get(root)),"@none");
            for(String path:resources.paths())if(path.contains("_data/managed/"))throw new IOException("Unity installation excluded: "+root);
            GameVersion version=GameVersion.detect(resources);
            if(!version.profileId.equals(pid))problem(mismatches,root,"","collection_profile",pid,version.profileId);
            Profile bound=null;try{bound=Profiles.get(pid);}catch(IllegalArgumentException unsupported){/* Format-only research sample. */}
            Map<String,Object> summary=new LinkedHashMap<>();List<Object> scripts=new ArrayList<>();
            int valid=0,invalid=0,fragments=0;long records=0;Map<String,Integer> owners=new TreeMap<>();List<Object> markerRejected=new ArrayList<>();
            for(Object scriptValue:array(expected.get("scripts"))){
                Map<String,Object> reference=object(scriptValue);String path=(String)reference.get("path");
                Map<String,Object> result=new LinkedHashMap<>();result.put("path",path);List<Object> terminal=new ArrayList<>();
                try{
                    byte[] raw=resources.read(path,64*1024*1024);result.put("sha256",Binary.sha256(raw));
                    if(!Binary.sha256(raw).equals(reference.get("sha256")))problem(mismatches,root,path,"sha256",reference.get("sha256"),result.get("sha256"));
                    Profile profile=bound!=null?bound:framingOnly(formats.get(pid),raw);
                    MesScript script=new MesScript(path,raw,profile);String layout=layoutHash(script);int count=script.records().size();
                    int actualVersion=profile.versionBytes==1?Binary.u8(raw,script.versionOffset):Binary.u16(raw,script.versionOffset);
                    result.put("framing_status","parsed");result.put("instruction_count",count);result.put("record_layout_sha256",layout);
                    result.put("version",String.format(Locale.ROOT,"0x%04X",actualVersion));
                    if(bound!=null){boolean accepted=bound.canExecuteVersion(actualVersion);result.put("runtime_marker_accepted",accepted);if(!accepted)markerRejected.add(path);if(reference.get("runtime_marker_accepted")!=null&&!Boolean.valueOf(accepted).equals(reference.get("runtime_marker_accepted")))problem(mismatches,root,path,"runtime_marker_accepted",reference.get("runtime_marker_accepted"),accepted);}
                    Map<String,Integer> scriptOwners=new TreeMap<>();for(MesScript.Record record:script.records())if(record.payloadOwner>=0){String owner=String.format(Locale.ROOT,"0x%02X",Binary.u8(raw,record.payloadOwner));scriptOwners.put(owner,scriptOwners.getOrDefault(owner,0)+1);owners.put(owner,owners.getOrDefault(owner,0)+1);}result.put("payload_owner_opcode_counts",scriptOwners);
                    if(reference.containsKey("payload_owner_opcode_counts")&&!sameCounts(object(reference.get("payload_owner_opcode_counts")),scriptOwners))problem(mismatches,root,path,"payload_owner_opcode_counts",reference.get("payload_owner_opcode_counts"),scriptOwners);
                    for(MesScript.Record record:script.records())if(record.terminalFragment){
                        Map<String,Object> fragment=new LinkedHashMap<>();fragment.put("offset",record.offset);
                        fragment.put("raw_hex",Binary.hex(Arrays.copyOfRange(raw,record.offset,record.end)));
                        fragment.put("kind","stop_operands_absent");terminal.add(fragment);
                    }
                    result.put("terminal_fragments",terminal);valid++;records+=count;fragments+=terminal.size();
                    if(!"parsed".equals(reference.get("framing_status")))problem(mismatches,root,path,"framing_status",reference.get("framing_status"),"parsed");
                    else{
                        if(number(reference.get("instruction_count"))!=count)problem(mismatches,root,path,"instruction_count",reference.get("instruction_count"),count);
                        if(reference.containsKey("record_layout_sha256")&&!layout.equals(reference.get("record_layout_sha256")))problem(mismatches,root,path,"record_layout_sha256",reference.get("record_layout_sha256"),layout);
                        if(!result.get("version").equals(reference.get("version")))problem(mismatches,root,path,"version",reference.get("version"),result.get("version"));
                        List<Object> expectedFragments=reference.containsKey("terminal_fragments")?array(reference.get("terminal_fragments")):Collections.emptyList();
                        // DataJson uses Double for integers, so compare stable serialized primitive fields.
                        if(expectedFragments.size()!=terminal.size())problem(mismatches,root,path,"terminal_fragments",expectedFragments.size(),terminal.size());
                        else for(int i=0;i<terminal.size();i++){
                            Map<String,Object> a=object(expectedFragments.get(i)),b=object(terminal.get(i));
                            if(number(a.get("offset"))!=number(b.get("offset"))||!a.get("raw_hex").equals(b.get("raw_hex")))problem(mismatches,root,path,"terminal_fragment",a,b);
                        }
                    }
                }catch(IOException failure){
                    result.put("framing_status","failed");result.put("error",failure.getMessage());invalid++;
                    if("parsed".equals(reference.get("framing_status")))problem(mismatches,root,path,"framing_status","parsed",failure.getMessage());
                }
                scripts.add(result);
            }
            summary.put("root",root);summary.put("profile",pid);summary.put("operation_binding_present",bound!=null);
            summary.put("scripts",scripts.size());summary.put("valid",valid);summary.put("invalid",invalid);
            summary.put("records",records);summary.put("terminal_fragments",fragments);summary.put("script_results",scripts);games.add(summary);
            summary.put("payload_owner_opcode_counts",owners);summary.put("runtime_marker_rejected_scripts",markerRejected);
            if(number(expected.get("physical_record_count"))!=records)problem(mismatches,root,"","physical_record_count",expected.get("physical_record_count"),records);
            if(number(expected.get("framing_parsed_scripts"))!=valid)problem(mismatches,root,"","framing_parsed_scripts",expected.get("framing_parsed_scripts"),valid);
            totalScripts+=scripts.size();totalValid+=valid;totalInvalid+=invalid;totalRecords+=records;totalFragments+=fragments;
        }
        Map<String,Object> report=new LinkedHashMap<>();report.put("mode","read_only_python_jvm_framing_comparison_no_game_execution");
        report.put("source_inventory",Paths.get(args[0]).toAbsolutePath().normalize().toString());report.put("games",games);
        report.put("scripts",totalScripts);report.put("valid",totalValid);report.put("invalid",totalInvalid);
        report.put("records",totalRecords);report.put("terminal_fragments",totalFragments);report.put("mismatches",mismatches);
        report.put("parsers_agree",mismatches.isEmpty());report.put("complete_engine_fidelity_verified",false);
        // Windows console encodings can differ from file.encoding. Emit UTF-8
        // bytes directly so redirected evidence is portable and lossless.
        System.out.write((json(report)+"\n").getBytes(StandardCharsets.UTF_8));
        if(!mismatches.isEmpty())System.exit(1);
    }

    private static String json(Object value){
        if(value==null)return "null";if(value instanceof Number||value instanceof Boolean)return value.toString();
        if(value instanceof Map){StringBuilder b=new StringBuilder("{");boolean first=true;for(Map.Entry<?,?> e:((Map<?,?>)value).entrySet()){if(!first)b.append(',');first=false;b.append(json(e.getKey().toString())).append(':').append(json(e.getValue()));}return b.append('}').toString();}
        if(value instanceof Collection){StringBuilder b=new StringBuilder("[");boolean first=true;for(Object item:(Collection<?>)value){if(!first)b.append(',');first=false;b.append(json(item));}return b.append(']').toString();}
        StringBuilder b=new StringBuilder("\"");for(char c:value.toString().toCharArray())switch(c){
            case '\\':b.append("\\\\");break;case '"':b.append("\\\"");break;case '\n':b.append("\\n");break;case '\r':b.append("\\r");break;case '\t':b.append("\\t");break;
            default:if(c<32)b.append(String.format(Locale.ROOT,"\\u%04x",(int)c));else b.append(c);
        }return b.append('"').toString();
    }
}
