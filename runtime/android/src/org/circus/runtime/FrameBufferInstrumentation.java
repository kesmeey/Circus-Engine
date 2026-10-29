package org.circus.runtime;

import android.app.Instrumentation;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.Debug;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.CRC32;
import org.circus.core.*;
import org.circus.graphics.BufferedImage;

/** Android Canvas/allocation audit using original opening assets and deterministic clocks.
 * Audio/movie completion is simulated; this is not a device presentation-rate claim. */
public final class FrameBufferInstrumentation extends Instrumentation {
    private String pack;private Path output;private int checks;private final StringBuilder log=new StringBuilder();
    private final int[] pixels=new int[1024*576];
    @Override public void onCreate(Bundle args){super.onCreate(args);pack=args.getString("pack","dc3r-audit.pack");start();}
    private void check(boolean ok,String text)throws IOException{if(!ok)throw new IOException(text);checks++;log.append("PASS ").append(text).append('\n');}
    private static BufferedImage frame(){return new BufferedImage(1024,576,BufferedImage.TYPE_INT_ARGB);}
    private void contracts()throws Exception{
        BufferedImage.setFramePoolingForAudit(true);
        BufferedImage held=frame(),a=frame(),b=frame();held.setRGB(43,27,0xff123456);a.setRGB(0,0,0xffabcdef);a.setRGB(1023,575,0xffabcdef);
        check(held.bitmap!=a.bitmap&&a.bitmap!=b.bitmap,"simultaneously owned frames have separate bitmaps");
        a.flush();b.flush();BufferedImage reused=frame();
        check(reused!=a&&reused.bitmap==a.bitmap,"retired pixels reused through a new independently owned wrapper");
        reused.getRGB(0,0,1024,576,pixels,0,1024);boolean clear=true;for(int value:pixels)if(value!=0){clear=false;break;}
        check(clear,"reused ARGB viewport starts fully transparent");
        reused.setRGB(0,0,0xff556677);a.flush();
        check(!reused.bitmap.isRecycled()&&reused.getRGB(0,0)==0xff556677,"double flush of an old wrapper cannot release a newly owned frame");
        check(held.getRGB(43,27)==0xff123456,"an unflushed held frame remains unchanged during reuse");
        reused.flush();BufferedImage rgb=new BufferedImage(1024,576,BufferedImage.TYPE_INT_RGB);rgb.getRGB(0,0,1024,576,pixels,0,1024);boolean black=true;for(int value:pixels)if(value!=0xff000000){black=false;break;}
        check(black,"reused RGB viewport starts fully opaque black");
        rgb.flush();held.flush();check(BufferedImage.frameAllocationStats()[3]<=2,"pool retains at most two viewport bitmaps (4.5 MiB)");
        BufferedImage tiny=new BufferedImage(20,10,BufferedImage.TYPE_INT_ARGB);tiny.flush();check(tiny.bitmap.isRecycled(),"other image dimensions keep their previous release behavior");
        BufferedImage external=new BufferedImage(Bitmap.createBitmap(1024,576,Bitmap.Config.ARGB_8888));external.flush();check(external.bitmap.isRecycled(),"externally decoded bitmaps are not pooled");
        BufferedImage live=frame();live.setRGB(3,4,0xff334455);BufferedImage.setFramePoolingForAudit(false);
        check(live.getRGB(3,4)==0xff334455,"clearing the free pool cannot recycle live frame ownership");live.flush();check(live.bitmap.isRecycled(),"pool disabled reproduces allocating/recycling implementation");
        BufferedImage.setFramePoolingForAudit(true);
    }
    private static boolean choose(DesktopUnity e,String action)throws Exception{for(DesktopEngine.Control c:e.controls())if(c.action.equals(action)){e.click((c.left+c.right)/2,(c.top+c.bottom)/2,false);return true;}return false;}
    private void opening(DesktopUnity e)throws Exception{
        boolean title=false,story=false;int lines=0;String previous="";
        for(int n=0;n<500&&lines<5;n++){
            e.tick(1000);for(int ch=0;ch<6;ch++)if(e.audioActive(ch)&&!e.audioLoop(ch))e.audioCompleted(ch,e.audioGeneration(ch));if(!e.movie().isEmpty())e.mediaCompleted(e.ticket());
            if(!title&&choose(e,"sce_start")){title=true;continue;}if(title&&!story&&choose(e,"sce01")){story=true;continue;}
            if(!story){if(!choose(e,"window"))choose(e,"01");}else if(e.textWaiting()){
                String position=e.scriptName()+":"+e.scriptOffset();if(!position.equals(previous)){previous=position;lines++;}
                if(lines<5)e.click(32,32,false);
            }
            BufferedImage image=e.render();image.flush();
        }
        check(title&&story&&lines==5,"original title and prologue reach fifth dialogue");
        check(e.host.layers[0].resource.equals("bg/railback"),"benchmark contains original scrolling railback background");
        boolean rail=false;for(Dc3rHost.Quad q:e.host.back.quads)if("system/rail_chip2".equals(e.host.works.get(q.work))&&q.animation>=0)rail=true;
        check(rail&&e.actorVisible(),"benchmark contains animated train foreground and original Himeno standing texture");
        log.append("SCENE ").append(e.scriptName()).append(':').append(e.scriptOffset()).append(' ').append(e.body()).append('\n');
    }
    private long hash(BufferedImage image){image.getRGB(0,0,1024,576,pixels,0,1024);CRC32 crc=new CRC32();for(int v:pixels){crc.update(v);crc.update(v>>>8);crc.update(v>>>16);crc.update(v>>>24);}return crc.getValue();}
    private static long stat(String key){try{return Long.parseLong(Debug.getRuntimeStat(key));}catch(Exception ignored){return -1;}}
    private static final class Metrics {long[] hashes;long created,reused,gcs,blocking,allocated,nanos,p95,max;}
    private Metrics measure(DesktopUnity e,byte[] state,boolean pool,int round)throws Exception{
        BufferedImage.setFramePoolingForAudit(pool);e.restore(state);e.tick(0);
        // Compile and populate both the drawing hot path and the two-entry retirement pool.
        for(int n=0;n<8;n++){BufferedImage warm=e.render();warm.flush();}
        long[] before=BufferedImage.frameAllocationStats();long gc=stat("art.gc.gc-count"),blocking=stat("art.gc.blocking-gc-count"),allocated=stat("art.gc.bytes-allocated");
        Metrics m=new Metrics();m.hashes=new long[3];long[] times=new long[240];
        for(int n=0;n<times.length;n++){
            long start=System.nanoTime();e.tick(16);BufferedImage image=e.render();times[n]=System.nanoTime()-start;m.nanos+=times[n];
            if(n==0||n==119||n==239){int i=n==0?0:n==119?1:2;m.hashes[i]=hash(image);if(round==0&&i==2)try(OutputStream out=Files.newOutputStream(output.resolve(pool?"pooled.png":"allocating.png"))){image.bitmap.compress(Bitmap.CompressFormat.PNG,100,out);}}
            image.flush();
        }
        long[] after=BufferedImage.frameAllocationStats();m.created=after[0]-before[0];m.reused=after[1]-before[1];m.gcs=stat("art.gc.gc-count")-gc;m.blocking=stat("art.gc.blocking-gc-count")-blocking;m.allocated=stat("art.gc.bytes-allocated")-allocated;
        Arrays.sort(times);m.p95=times[times.length*95/100];m.max=times[times.length-1];
        log.append("METRICS round=").append(round).append(" pool=").append(pool).append(" frames=240 mean_ms=").append(m.nanos/240e6).append(" p95_ms=").append(m.p95/1e6).append(" max_ms=").append(m.max/1e6).append(" viewport_creates=").append(m.created).append(" viewport_reuses=").append(m.reused).append(" gc=").append(m.gcs).append(" blocking_gc=").append(m.blocking).append(" java_bytes_allocated=").append(m.allocated).append(" hashes=").append(Arrays.toString(m.hashes)).append('\n');
        android.util.Log.i("CIRCUS-FRAME-AUDIT",log.substring(log.lastIndexOf("METRICS")));Files.write(output.resolve("result.txt"),log.toString().getBytes("UTF-8"));return m;
    }
    @Override public void onStart(){Bundle result=new Bundle();String oldRoot=System.getProperty("dc.saveRoot");try{
        Path base=getTargetContext().getFilesDir().toPath();output=Files.createTempDirectory(base,"dc3r-frame-audit-");Path input=base.resolve(pack).toFile().getCanonicalFile().toPath();if(!input.startsWith(base.toFile().getCanonicalFile().toPath()))throw new IOException("Audit input escapes app files");
        System.setProperty("dc.saveRoot",output.resolve("saves").toString());contracts();
        DesktopUnity engine=new DesktopUnity(new UnityCircusResources(new GameResources(RangeStorage.replay(input))),true);opening(engine);byte[] state=engine.snapshot();
        for(int round=0;round<2;round++){
            Metrics off=measure(engine,state,false,round),on=measure(engine,state,true,round);
            check(off.created==240&&off.reused==0,"allocating baseline creates exactly one viewport per measured frame, round "+round);
            check(on.created==0&&on.reused==240,"pooled steady render creates no viewport bitmap over 240 frames, round "+round);
            check(Arrays.equals(off.hashes,on.hashes),"three original animated-frame pixel hashes match allocating baseline, round "+round);
        }
        log.append("CHECKS ").append(checks).append("\nOUTPUT ").append(output).append("\nClock is deterministic; audio/movie completion is simulated. Measurements are software Android Canvas frame construction, not screen presentation rate.\n");
        Files.write(output.resolve("result.txt"),log.toString().getBytes("UTF-8"));result.putString("stream",log.toString());finish(-1,result);
    }catch(Throwable ex){log.append("FAIL ").append(ex).append('\n');try{if(output!=null)Files.write(output.resolve("result.txt"),log.toString().getBytes("UTF-8"));}catch(Exception ignored){}result.putString("stream",log.toString());finish(0,result);
    }finally{BufferedImage.setFramePoolingForAudit(true);if(oldRoot==null)System.clearProperty("dc.saveRoot");else System.setProperty("dc.saveRoot",oldRoot);}}
}
