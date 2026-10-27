package org.circus.core;

import java.io.*;
import java.nio.file.*;
import java.util.*;

/** Static corpus audit only: parses/decompresses bytes; NEVER constructs a MesVm. */
public final class CorpusAudit {
    private static int mes,physical,images,diffs,audio,unsupported,failures;
    private static final List<String> errors=new ArrayList<>();
    private static Archive.Source source(RandomAccessFile f)throws IOException{return new Archive.Source(){public long size()throws IOException{return f.length();}public byte[] read(long p,int n)throws IOException{Binary.range(p,n,f.length());byte[] b=new byte[n];f.seek(p);f.readFully(b);return b;}};}
    private static void inspect(byte[] data,Crx.Resolver resolver)throws IOException {
        if(data.length<4)return;long magic=Binary.u32(data,0);
        if(magic==0x47585243L||magic==0x44585243L){Crx.Image image=Crx.decode(data,resolver);if(image.pixels.length!=image.height*image.stride)throw new IOException("Pixel size mismatch");images++;if(magic==0x44585243L)diffs++;}
        else if(magic==0x4d435058L){int mode=Binary.u8(data,8);if(mode!=0&&mode!=5){unsupported++;return;}Xpcm.decode(data);audio++;}
    }
    public static void main(String[] args)throws Exception {
        Path root=Paths.get(args[0]);Profile profile=Profiles.get(args[1]);boolean media=args.length>2&&args[2].equals("--media");
        List<Path> paths=new ArrayList<>();try(java.util.stream.Stream<Path> stream=Files.walk(root)){stream.filter(Files::isRegularFile).forEach(paths::add);}Collections.sort(paths);
        for(Path path:paths){String name=path.toString().toLowerCase(Locale.ROOT);try{
            if(name.endsWith(".mes")){MesScript s=new MesScript(path.getFileName().toString(),Files.readAllBytes(path),profile);mes++;physical+=s.records().size();}
            else if(media&&(name.endsWith(".crx")||name.endsWith(".pcm"))){inspect(Files.readAllBytes(path),null);}
            else if(media&&(name.endsWith(".crm")||name.endsWith(".pck")||name.endsWith("voice.dat"))){
                try(RandomAccessFile file=new RandomAccessFile(path.toFile(),"r")){
                    Archive archive=new Archive(source(file));Crx.Resolver resolver=new Crx.Resolver(){public byte[] byOffset(long o)throws IOException{return archive.read(archive.atOffset(o));}public byte[] byName(byte[] n)throws IOException{return archive.read(archive.find(n));}};
                    for(Archive.Entry entry:archive.entries)try{inspect(archive.read(entry),resolver);}catch(IOException e){failures++;if(errors.size()<50)errors.add(path.getFileName()+"#"+entry.index+": "+e.getMessage());}
                }
                System.out.println("progress images="+images+" audio="+audio+" failures="+failures);
            }
        }catch(IOException e){failures++;if(errors.size()<50)errors.add(path.getFileName()+": "+e.getMessage());}}
        System.out.println("STATIC AUDIT MES="+mes+" records="+physical+" images="+images+" CRXD="+diffs+" audio="+audio+" unsupported_audio="+unsupported+" failures="+failures);
        for(String error:errors)System.out.println("ERROR "+error);
        if(failures>0)System.exit(1);
    }
}
