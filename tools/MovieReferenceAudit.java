import org.circus.core.*;
import java.util.*;
import java.nio.file.*;

/** Read-only movie-path audit: feed only movie records to the real host binding. */
public final class MovieReferenceAudit {
    public static void main(String[] args)throws Exception{
        GameResources files=new GameResources(new LocalGameStorage(Paths.get(args[0])));Profile profile=GameVersion.detect(files).profile();CircusResources resources=new CircusResources(files,profile);
        Dc3Host dd=profile.id.equals("dc3dd-a5a8")?new Dc3Host(resources,TextCodec.identity(),true):null;
        Dc2Host legacy=dd==null?Dc2Host.create(resources,TextCodec.identity()):null;
        Set<String> movies=new TreeSet<>();int records=0,scripts=0;
        for(String path:files.paths())if(path.startsWith("advdata/mes/")&&path.endsWith(".mes")){
            MesScript script=new MesScript(path,files.read(path,64*1024*1024),profile);scripts++;
            for(MesScript.Record r:script.records()){
                String op=profile.operation(r.opcode);if(!op.equals("dm.movie")&&!op.equals("pc.movie")&&!op.equals("media.movie"))continue;
                String movie;if(dd!=null){dd.command(op,new int[0],script.nativeText(r));movie=dd.movie;}else{legacy.command(op,new int[0],script.nativeText(r));movie=legacy.movie;}
                files.slice(movie,0,4);movies.add(movie);records++;
            }
        }
        System.out.println("PASS "+profile.id+" scripts="+scripts+" movie_records="+records+" files="+movies+"; static reads only, no Windows execution");
    }
}
