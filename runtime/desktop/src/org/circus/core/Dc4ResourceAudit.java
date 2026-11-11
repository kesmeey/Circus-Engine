package org.circus.core;
import java.nio.file.*;
import java.util.*;
import java.io.*;
import java.nio.*;
public final class Dc4ResourceAudit {
 static List<Object> images=new ArrayList<>(),audios=new ArrayList<>(),errors=new ArrayList<>(); static long pixels; static int archives,indexed; static int[] sizes=new int[2];
 static String imgHash(Crx.Image image){ByteBuffer b=ByteBuffer.allocate(image.width*image.height*4);for(int pixel:image.argb())b.putInt(pixel);return Binary.sha256(b.array());}
 static void image(GameResources data,String path,Archive.Entry entry){
  try{Crx.Image i=entry==null?data.image(path):data.imageAtOffset(path,entry.offset);pixels+=(long)i.width*i.height;sizes[0]=Math.max(sizes[0],i.width);sizes[1]=Math.max(sizes[1],i.height); images.add(Json.obj("path",path,"entry",entry==null?null:entry.name,"x",i.x,"y",i.y,"width",i.width,"height",i.height,"bpp",i.bpp,"pixel_sha256",imgHash(i)));}
  catch(Exception e){errors.add(Json.obj("kind","image","path",path,"entry",entry==null?null:entry.name,"error",e.toString()));}
 }
 static void audio(GameResources data,String path,Archive archive,Archive.Entry entry){
  try{Archive.Source source=entry==null?data.source(path):archive.source(entry);Xpcm x=Xpcm.decode(source.read(0,(int)source.size()));ByteArrayOutputStream out=new ByteArrayOutputStream();Xpcm.write(source,out);byte[] written=out.toByteArray();if(x.codec.equals("ogg")&&!Arrays.equals(x.payload,written))throw new IOException("Streaming differs from full Ogg decode");if(x.codec.equals("pcm")&&!Arrays.equals(x.wav(),written))throw new IOException("Streaming differs from full PCM decode"); audios.add(Json.obj("path",path,"entry",entry==null?null:entry.name,"codec",x.codec,"channels",x.channels,"sampleRate",x.sampleRate,"bits",x.bits,"output_bytes",written.length,"sha256",Binary.sha256(written)));}
  catch(Exception e){errors.add(Json.obj("kind","audio","path",path,"entry",entry==null?null:entry.name,"error",e.toString()));}
 }
 public static void main(String[] args)throws Exception{
  Path root=Paths.get(args[0]),out=Paths.get(args[1]);Files.createDirectories(out);GameResources data=new GameResources(new LocalGameStorage(root));NamesCatalog names=data.names("AdvData/GRP/NAMES.DAT");Map<String,Integer> tables=new LinkedHashMap<>();for(String t:NamesCatalog.CATEGORIES)tables.put(t,names.table(t).size());
  List<String> paths=new ArrayList<>(data.paths());Collections.sort(paths);Map<String,Integer> crxKinds=new TreeMap<>();
  for(String p:paths)if(p.endsWith(".crx")){
   byte[] h=data.slice(p,0,20);String kind="c"+Binary.u16(h,12)+"f"+Binary.u16(h,14)+"d"+Binary.i16(h,16)+"m"+Binary.u16(h,18);crxKinds.put(kind,crxKinds.getOrDefault(kind,0)+1);image(data,p,null);
   if(images.size()%100==0)System.out.println("loose images "+images.size());
  }
  for(String p:paths)if(p.endsWith(".crm")||p.endsWith(".pck")){
   try{Archive a=data.archive(p);archives++;indexed+=a.entries.size();Set<Integer> selected=new TreeSet<>();selected.add(0);selected.add(a.entries.size()/2);selected.add(a.entries.size()-1);if(p.endsWith(".pck"))for(int n=0;n<a.entries.size();n+=Math.max(1,a.entries.size()/5))selected.add(n);
    for(int n=0;n<a.entries.size();n+=20)selected.add(n);
    for(int n:selected)image(data,p,a.entries.get(n));
   }catch(Exception e){errors.add(Json.obj("kind","archive","path",p,"error",e.toString()));}
   if(archives%25==0)System.out.println("archive "+archives+" decoded images "+images.size());
  }
  for(String p:paths)if(p.endsWith(".pcm"))audio(data,p,null,null);
  Archive voice=data.archive("AdvData/VOICE.DAT");Set<Integer> selected=new TreeSet<>();selected.add(0);selected.add(voice.entries.size()-1);for(int n=0;n<voice.entries.size();n+=500)selected.add(n);for(int n:selected)audio(data,"AdvData/VOICE.DAT",voice,voice.entries.get(n));
  Map<String,Object> report=Json.obj("scope","Frozen production resource classes against read-only DC4PH bytes: all loose CRX; all container index validation; first/middle/last and every20th CRM entry plus at least5 PCK samples; all loose audio plus every500th voice; no original process execution or native pixel equivalence claim.","original_exe_executed",false,"original_game_written",false,"game",root.toString(),"class_source",Crx.class.getProtectionDomain().getCodeSource().getLocation().toString(),"names_tables",tables,"loose_crx_kinds",crxKinds,"archives",archives,"archive_entries_indexed",indexed,"voice_entries_indexed",voice.entries.size(),"decoded_images",images.size(),"decoded_pixels",pixels,"max_sample_size",sizes,"checked_audio",audios.size(),"errors",errors,"images",images,"audio",audios);
  Files.write(out.resolve("decode-report.json"),(Json.text(report)+"\n").getBytes(java.nio.charset.StandardCharsets.UTF_8));System.out.println("COMPLETE images="+images.size()+" archives="+archives+" audio="+audios.size()+" errors="+errors.size());for(Object error:errors)System.out.println(Json.text(error));
 }
}
