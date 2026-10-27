package org.circus.core;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import java.util.stream.Stream;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;

/** Read-only extraction, sharing the runtime's actual archive/image/audio decoders. */
public final class DcUnpack {
    private final Path input,out;
    private final boolean rawOnly;
    private final Map<String,Integer> counts=new TreeMap<>();
    private final List<Map<String,Object>> errors=new ArrayList<>();
    private BufferedWriter manifest,sources;
    private Profile profile;
    private TextCodec codec;
    private int total,rawOk,decoded,sourceCount,textErrors,rawFailures;
    private long written;

    private DcUnpack(Path input,Path out,boolean rawOnly)throws IOException {
        this.input=input.toRealPath();this.rawOnly=rawOnly;this.codec=new TextCodec("","");
        Path protectedRoot=this.input;
        if(Files.isRegularFile(input))for(Path p=this.input.getParent();p!=null;p=p.getParent())if(p.getFileName()!=null&&p.getFileName().toString().equalsIgnoreCase("AdvData")){protectedRoot=p.getParent();break;}
        Path target=out.toAbsolutePath().normalize();
        // Resolve existing parents before creating anything (including junctions).
        Path ancestor=target;while(!Files.exists(ancestor))ancestor=ancestor.getParent();
        target=ancestor.toRealPath().resolve(ancestor.relativize(target)).normalize();
        if(target.startsWith(protectedRoot)||protectedRoot.startsWith(target))throw new IOException("Input/output trees overlap");
        if(Files.exists(target)){try(Stream<Path> s=Files.list(target)){if(s.findAny().isPresent())throw new IOException("Output must be empty; choose a new output directory");}}
        Files.createDirectories(target);this.out=target.toRealPath();
    }
    private static byte[] bytes(Path p)throws IOException {
        if(Files.size(p)>128L*1024*1024)throw new IOException("Individual resource exceeds 128 MiB");return Files.readAllBytes(p);
    }
    private static String hash(Path p)throws IOException {
        try{MessageDigest d=MessageDigest.getInstance("SHA-256");try(InputStream in=Files.newInputStream(p)){byte[] b=new byte[1024*1024];for(int n;(n=in.read(b))!=-1;)d.update(b,0,n);}return Binary.hex(d.digest());}catch(NoSuchAlgorithmException e){throw new AssertionError(e);}
    }
    static String safeName(String name) {
        StringBuilder b=new StringBuilder("f_");byte[] raw=name.getBytes(StandardCharsets.UTF_8);
        for(byte v:raw){int c=v&255;if(c>='a'&&c<='z'||c>='A'&&c<='Z'||c>='0'&&c<='9'||c=='_'||c=='-'||c=='.')b.append((char)c);else b.append(String.format("%%%02X",c));}
        if(b.length()>130)b.setLength(130);while(b.charAt(b.length()-1)=='.')b.setLength(b.length()-1);return b.toString();
    }
    private Path dest(String rel)throws IOException {Path p=out.resolve(rel).normalize();if(!p.startsWith(out)||p.equals(out))throw new IOException("Invalid destination");Files.createDirectories(p.getParent());return p;}
    private void write(String rel,byte[] data)throws IOException {Path p=dest(rel);Files.write(p,data,StandardOpenOption.CREATE_NEW);if(!Binary.sha256(data).equals(hash(p)))throw new IOException("Output verification failed "+rel);written+=data.length;}
    private void line(BufferedWriter w,Object row)throws IOException {w.write(Json.text(row));w.newLine();}
    private void failure(Map<String,Object> row,Exception e){row.put("error",e.toString());errors.add(Json.obj("source",row.get("source"),"entry",row.get("entry"),"error",e.toString()));}
    private void source(Path file,String name)throws IOException {
        if(!file.toRealPath().startsWith(Files.isDirectory(input)?input:input.getParent()))throw new IOException("Source symlink escapes selected root");
        line(sources,Json.obj("path",name,"bytes",Files.size(file),"sha256",hash(file)));sourceCount++;
    }
    private static String category(String name) {
        String p="/"+name.replace('\\','/').toLowerCase(Locale.ROOT);String n=p.substring(p.lastIndexOf('/')+1);
        if(p.contains("/voice")||n.endsWith(".wav"))return "voice";
        if(p.contains("/music/")||p.contains("/bgm/"))return "bgm";
        if(p.contains("/se/"))return "se";
        if(n.endsWith(".mes"))return "script";
        if(p.contains("/bst/")||p.contains("/bsteff/")||p.contains("/character/"))return "character";
        if(p.contains("/face/"))return "face";
        if(p.contains("/bg/"))return n.startsWith("ev")||n.startsWith("cg")?"cg":"bg";
        if(p.contains("/system/"))return "system";
        if(n.endsWith(".mpg")||n.endsWith(".wmv")||n.endsWith(".avi"))return "movie";
        if(n.endsWith(".crx"))return "image";
        return "other";
    }
    private void resource(String source,String entry,byte[] nameBytes,long offset,byte[] data,Crx.Resolver resolver,String stem)throws IOException {
        String cat=category(source+"/"+entry),base=cat+"/"+stem;
        Map<String,Object> row=Json.obj("source",source,"entry",entry,"name_bytes_hex",Binary.hex(nameBytes),"offset",offset,"bytes",data.length,"sha256",Binary.sha256(data),"category",cat,"classification","source folder and filename; CG prefix is heuristic");
        total++;counts.put(cat,counts.getOrDefault(cat,0)+1);
        try{
            String raw="raw/"+base;write(raw,data);row.put("raw",raw);rawOk++;
            if(!rawOnly){long magic=data.length>=4?Binary.u32(data,0):0;
                if(magic==0x47585243L||magic==0x44585243L){Crx.Image img=magic==0x47585243L?Crx.plain(data,profile!=null&&PcHost.supports(profile)):Crx.decode(data,resolver);
                    BufferedImage image=new BufferedImage(img.width,img.height,BufferedImage.TYPE_INT_ARGB);image.setRGB(0,0,img.width,img.height,img.argb(false),0,img.width);
                    ByteArrayOutputStream png=new ByteArrayOutputStream();if(!ImageIO.write(image,"png",png))throw new IOException("PNG writer unavailable");
                    byte[] b=png.toByteArray();String path=base+".png";write(path,b);row.put("decoded",path);row.put("decoded_sha256",Binary.sha256(b));row.put("image",Json.obj("width",img.width,"height",img.height,"x",img.x,"y",img.y,"bpp",img.bpp,"color_key_applied",false));decoded++;
                }else if(magic==0x4d435058L){Xpcm audio=Xpcm.decode(data);byte[] b=audio.codec.equals("ogg")?audio.payload:audio.wav();String path=base+(audio.codec.equals("ogg")?".ogg":".wav");write(path,b);row.put("decoded",path);row.put("decoded_sha256",Binary.sha256(b));row.put("audio",Json.obj("codec",audio.codec,"sample_rate",audio.sampleRate,"channels",audio.channels,"bits",audio.bits));decoded++;
                }else if(entry.toLowerCase(Locale.ROOT).endsWith(".mes")){
                    String rawScript=base;write(rawScript,data);row.put("extracted",rawScript);
                    if(profile==null)throw new IOException("Unknown MES profile; raw script preserved");
                    MesScript script=new MesScript(entry,data,profile);String path=base+".jsonl";
                    try(BufferedWriter w=Files.newBufferedWriter(dest(path),StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW)){
                        line(w,Json.obj("profile",profile.id,"sha256",script.sha256,"code_offset",script.firstOffset,"label_words",script.headerOffsets,"label_metadata",script.headerMetadata));
                        for(MesScript.Record r:script.records()){
                            byte[] nativeBytes=script.nativeText(r);Map<String,Object> record=Json.obj("offset",r.offset,"opcode",String.format("0x%02X",r.opcode),"operation",profile.operation(r.opcode),"args",r.args,"raw_hex",Binary.hex(Arrays.copyOfRange(data,r.offset,r.end)),"native_string_hex",Binary.hex(nativeBytes));
                            try{record.put("text",codec.decode(nativeBytes));record.put("text_encoding",codec.charset.name());}catch(IOException e){record.put("text",null);record.put("text_encoding_error",e.getMessage());textErrors++;}
                            line(w,record);
                        }
                    }row.put("decoded",path);row.put("decoded_sha256",hash(dest(path)));decoded++;
                }else {String path=base;write(path,data);row.put("extracted",path);}
            }
            row.put("status","ok");
        }catch(Exception e){row.put("status","error");failure(row,e);}
        line(manifest,row);if(total%500==0){manifest.flush();System.out.println(Json.text(Json.obj("entries",total,"decoded",decoded,"errors",errors.size())));}
    }
    private static Archive.Source open(Path p) {return new Archive.Source(){public long size()throws IOException{return Files.size(p);}public byte[] read(long at,int n)throws IOException{try(RandomAccessFile f=new RandomAccessFile(p.toFile(),"r")){Binary.range(at,n,f.length());byte[] b=new byte[n];f.seek(at);f.readFully(b);return b;}}};}
    private static Path sibling(Path p,String name)throws IOException{try(Stream<Path> s=Files.list(p.getParent())){return s.filter(x->x.getFileName().toString().equalsIgnoreCase(name)).findFirst().orElse(null);}}
    private void archive(Path path,String name,int ordinal)throws IOException {
        final Archive a;Path second=null;Archive candidate;
        try{candidate=new Archive(open(path));}catch(IOException first){try{second=sibling(path,"VOICE2.DAT");candidate=new Archive(open(path),second==null?null:open(second));}catch(IOException old){first.addSuppressed(old);throw first;}}
        a=candidate;
        if(a.kind.equals("VOICE64")&&second!=null)source(second,name.substring(0,Math.max(0,name.lastIndexOf('/')+1))+second.getFileName());
        final Crx.Resolver resolver=new Crx.Resolver(){public byte[] byOffset(long offset)throws IOException{return a.read(a.atOffset(offset));}public byte[] byName(byte[] raw)throws IOException{return a.read(a.find(raw));}};
        for(Archive.Entry e:a.entries){String physical=name;long at=e.offset;
            if(second!=null&&e.offset>=Files.size(path)){physical=name.substring(0,Math.max(0,name.lastIndexOf('/')+1))+second.getFileName();at-=Files.size(path);}
            String stem=String.format("%05d_%s/%06d_%s",ordinal,safeName(path.getFileName().toString()),e.index,safeName(e.name));
            try{resource(physical,e.name,e.nameBytes,at,a.read(e),resolver,stem);}catch(IOException err){rawFailures++;Map<String,Object> row=Json.obj("source",physical,"entry",e.name,"status","read_error");failure(row,err);line(manifest,row);}
        }
    }
    private boolean run()throws Exception {
        List<Path> files=new ArrayList<>();Path root=input;
        if(Files.isDirectory(input)){
            List<Path> roots=new ArrayList<>();try(Stream<Path> direct=Files.list(input)){direct.filter(Files::isDirectory).filter(p->p.getFileName().toString().equalsIgnoreCase("AdvData")).forEach(roots::add);}
            if(roots.isEmpty())try(Stream<Path> walk=Files.walk(input)){walk.filter(Files::isDirectory).filter(p->!p.toString().toLowerCase(Locale.ROOT).contains("old_source_circus_dcpc")).filter(p->p.getFileName().toString().equalsIgnoreCase("AdvData")).forEach(roots::add);}
            List<Path> games=new ArrayList<>();for(Path r:roots){try(Stream<Path> walk=Files.walk(r,2)){if(walk.anyMatch(p->p.getFileName().toString().equalsIgnoreCase("START.mes")))games.add(r);}}
            roots=games;
            if(roots.size()!=1)throw new IOException("Select one game directory containing one AdvData/MES/START.mes tree");root=roots.get(0).getParent();
            GameResources game=new GameResources(new LocalGameStorage(root));GameVersion v=GameVersion.detect(game);profile=v.profile();codec=GameText.detect(game,profile);
            try(Stream<Path> walk=Files.walk(roots.get(0))){walk.filter(Files::isRegularFile).filter(p->{String n=p.toString().replace('\\','/').toLowerCase(Locale.ROOT);return !n.contains("/savedata/")&&!n.contains("/backup/");}).sorted().forEach(files::add);}
        }else files.add(input);
        for(String c:new String[]{"bg","cg","character","voice","bgm","se","script","system","face","movie","other","raw"})Files.createDirectories(out.resolve(c));
        try(BufferedWriter m=Files.newBufferedWriter(out.resolve("manifest.jsonl"),StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW);BufferedWriter s=Files.newBufferedWriter(out.resolve("sources.jsonl"),StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW)){
            manifest=m;sources=s;int ordinal=0;
            for(Path file:files){String name=Files.isDirectory(input)?root.relativize(file).toString().replace('\\','/'):file.getFileName().toString();String lower=name.toLowerCase(Locale.ROOT);if(lower.endsWith("/voice2.dat"))continue;
                source(file,name);ordinal++;
                try{
                    if(lower.endsWith(".crm")||lower.endsWith(".pck")||file.getFileName().toString().equalsIgnoreCase("voice.dat")||(!Files.isDirectory(input)&&lower.endsWith(".dat"))){archive(file,name,ordinal);continue;}
                    if(Files.size(file)>128L*1024*1024){String target=category(name)+"/"+String.format("%05d_%s",ordinal,safeName(file.getFileName().toString()));Path dest=dest(target);Files.copy(file,dest);if(!hash(file).equals(hash(dest)))throw new IOException("Copy hash mismatch");total++;rawOk++;line(m,Json.obj("source",name,"entry",file.getFileName().toString(),"bytes",Files.size(file),"raw",target,"sha256",hash(dest),"status","ok","category",category(name)));continue;}
                    resource(name,file.getFileName().toString(),file.getFileName().toString().getBytes(Binary.SJIS),0,bytes(file),null,String.format("%05d_%s",ordinal,safeName(file.getFileName().toString())));
                }catch(IOException e){rawFailures++;Map<String,Object> row=Json.obj("source",name,"status","file_error");failure(row,e);line(m,row);}
            }
        }
        Map<String,Object> summary=Json.obj("schema",1,"input",input.toString(),"profile",profile==null?null:profile.id,"raw_only",rawOnly,"source_files",sourceCount,"entries",total,"raw_verified",rawOk,"decoded",decoded,"written_bytes",written,"categories",counts,"errors",errors,"complete",errors.isEmpty(),"raw_complete",rawOk==total&&rawFailures==0,"text_encoding_errors",textErrors,"game_executed",false,"png_note","Original pixels retained; magenta color key not applied. x/y are in manifest.","scope","AdvData resources, excluding saves/backups; original scripts preserved even when disassembly fails.");
        write("summary.json",(Json.text(summary)+"\n").getBytes(StandardCharsets.UTF_8));System.out.println(Json.text(summary));return errors.isEmpty();
    }
    public static void main(String[] args)throws Exception {
        if(args.length<2){System.err.println("dc_unpack <game-directory | archive.dat | archive.crm> <empty-output-directory> [--raw-only]");System.exit(2);}
        if(args.length>3||args.length==3&&!args[2].equals("--raw-only"))throw new IllegalArgumentException("Unknown option");
        if(!new DcUnpack(Paths.get(args[0]),Paths.get(args[1]),args.length==3).run())System.exit(1);
    }
}
