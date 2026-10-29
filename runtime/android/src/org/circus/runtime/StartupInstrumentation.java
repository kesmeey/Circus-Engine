package org.circus.runtime;

import android.app.*;
import android.content.*;
import android.net.Uri;
import android.os.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.*;
import org.circus.core.*;
import org.circus.graphics.BufferedImage;

/** Synthetic cache invalidation tests, with optional read-only SAF and Activity startup timings. */
public final class StartupInstrumentation extends Instrumentation {
    private String treeUri,executable;private boolean launch;private RuntimeActivity activity;private final StringBuilder log=new StringBuilder();
    @Override public void onCreate(Bundle args){super.onCreate(args);if(args!=null){treeUri=args.getString("tree_uri");executable=args.getString("executable");launch="true".equalsIgnoreCase(args.getString("launch"));}start();}
    private static double millis(long nanos){return Math.round(nanos/100000.0)/10.0;}
    private static final class Counts implements GameResources.Storage {
        final GameResources.Storage storage;long bytes,opens;
        Counts(GameResources.Storage storage){this.storage=storage;}
        void reset(){bytes=opens=0;}
        public Set<String> paths(){return storage.paths();}
        public byte[] read(String name,int limit)throws IOException{byte[] data=storage.read(name,limit);bytes+=data.length;opens++;return data;}
        public Archive.Source source(String name)throws IOException{Archive.Source source=storage.source(name);return new Archive.Source(){
            public long size()throws IOException{return source.size();}
            public byte[] read(long at,int count)throws IOException{byte[] data=source.read(at,count);bytes+=data.length;return data;}
            public Archive.Reader openReader()throws IOException{Archive.Reader reader=source.openReader();opens++;return new Archive.Reader(){public long size()throws IOException{return reader.size();}public byte[] read(long at,int count)throws IOException{byte[] data=reader.read(at,count);bytes+=data.length;return data;}public void close()throws IOException{reader.close();}};}
        };}
    }
    private void benchmark()throws Exception{
        long start=System.nanoTime();DocumentTree tree=new DocumentTree(getTargetContext().getContentResolver(),Uri.parse(treeUri));Counts counts=new Counts(tree);GameResources files=new GameResources(counts,"@none");log.append("SAF inventory_ms=").append(millis(System.nanoTime()-start)).append(" paths=").append(files.paths().size()).append('\n');
        // Reproduce the old eager I/O cost without executing or retaining the scripts.
        start=System.nanoTime();int scripts=0;for(String path:new TreeSet<>(files.paths()))if(path.startsWith("advdata/mes/")&&path.endsWith(".mes")){files.read(path,64*1024*1024);scripts++;}log.append("EAGER_SCRIPT_READ_BASELINE scripts=").append(scripts).append(" bytes=").append(counts.bytes).append(" opens=").append(counts.opens).append(" elapsed_ms=").append(millis(System.nanoTime()-start)).append('\n');
        counts.reset();start=System.nanoTime();GameVersion detected=GameVersion.detect(files);log.append("BOUNDED_COLLECTION profile=").append(detected.profileId).append(" bytes=").append(counts.bytes).append(" opens=").append(counts.opens).append(" elapsed_ms=").append(millis(System.nanoTime()-start)).append('\n');
        Path temporary=Files.createTempDirectory(getTargetContext().getCacheDir().toPath(),"startup-benchmark-");try{File cache=temporary.toFile();counts.reset();start=System.nanoTime();Profile cold=GameVersionCache.detect(tree,files,cache);log.append("PROFILE_CACHE_MISS profile=").append(cold.id).append(" bytes=").append(counts.bytes).append(" opens=").append(counts.opens).append(" elapsed_ms=").append(millis(System.nanoTime()-start)).append('\n');
            counts.reset();start=System.nanoTime();Profile warm=GameVersionCache.detect(tree,files,cache);log.append("PROFILE_CACHE_REPEAT profile=").append(warm.id).append(" metadata_available=").append(tree.scriptFingerprint(files)!=null).append(" bytes=").append(counts.bytes).append(" opens=").append(counts.opens).append(" elapsed_ms=").append(millis(System.nanoTime()-start)).append('\n');
            if(!cold.id.equals(detected.profileId)||!warm.id.equals(detected.profileId))throw new IOException("Cached and full collection profiles differ");
        }finally{try(java.util.stream.Stream<Path> paths=Files.walk(temporary)){Path[] entries=paths.sorted(Comparator.reverseOrder()).toArray(Path[]::new);for(Path entry:entries){if(!entry.normalize().startsWith(temporary.normalize()))throw new IOException("Benchmark cleanup escaped its temporary directory");Files.deleteIfExists(entry);}}}
        if(launch){launchActivity("first");closeActivity();launchActivity("repeat");closeActivity();}
    }
    private void closeActivity()throws Exception{RuntimeActivity closing=activity;if(closing==null)return;runOnMainSync(()->closing.finish());waitForIdleSync();if(!closing.worker.awaitTermination(20,java.util.concurrent.TimeUnit.SECONDS))throw new IOException("Previous Activity worker did not terminate");activity=null;}
    private void launchActivity(String label)throws Exception{
        boolean cacheExisted=GameVersionCache.cacheFile(getTargetContext().getCacheDir(),treeUri).isFile();
        long start=System.nanoTime();Intent intent=new Intent(getTargetContext(),RuntimeActivity.class).putExtra("tree",treeUri).putExtra("audit",true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);if(executable!=null)intent.putExtra("executable",executable);
        activity=(RuntimeActivity)startActivitySync(intent);AtomicLong nonblack=new AtomicLong();AtomicReference<Throwable> observerError=new AtomicReference<>();
        java.lang.reflect.Field image=RuntimeActivity.Stage.class.getDeclaredField("image");image.setAccessible(true);
        runOnMainSync(()->{java.util.function.LongConsumer observe=nanos->{if(nonblack.get()!=0)return;try{BufferedImage frame=(BufferedImage)image.get(activity.stage);if(frame==null)return;int width=frame.getWidth(),height=frame.getHeight();for(int y=height/36;y<height;y+=Math.max(1,height/18))for(int x=width/64;x<width;x+=Math.max(1,width/32)){int pixel=frame.bitmap.getPixel(x,y);if((pixel>>>16&255)>16||(pixel>>>8&255)>16||(pixel&255)>16){nonblack.compareAndSet(0,nanos);return;}}}catch(Throwable e){observerError.compareAndSet(null,e);}};activity.stage.presentationObserver=observe;observe.accept(System.nanoTime());});
        long end=SystemClock.uptimeMillis()+120000;while(!activity.failed&&(activity.initializedNanos==0||activity.firstPresentationNanos==0||nonblack.get()==0)&&SystemClock.uptimeMillis()<end)Thread.sleep(50);
        if(activity.failed)throw new IOException(activity.error);if(observerError.get()!=null)throw new IOException("Frame observer",observerError.get());
        if(activity.initializedNanos==0||activity.firstPresentationNanos==0||nonblack.get()==0)throw new IOException("Timed out waiting for initialized game and visible frame");
        log.append("ACTIVITY ").append(label).append(" profile_cache_file_preexisted=").append(cacheExisted).append(" initialized_ms=").append(millis(activity.initializedNanos-start)).append(" first_presented_buffer_ms=").append(millis(activity.firstPresentationNanos-start)).append(" first_nonblack_observed_ms=").append(millis(nonblack.get()-start)).append('\n');runOnMainSync(()->activity.stage.presentationObserver=null);
    }
    @Override public void onStart(){Bundle result=new Bundle();try{
        log.append("PASS synthetic cache assertions=").append(GameVersionCacheTests.run(getTargetContext().getCacheDir())).append('\n');if(treeUri!=null)benchmark();
        Files.write(new File(getTargetContext().getFilesDir(),"startup-audit-result.txt").toPath(),log.toString().getBytes("UTF-8"));result.putString("stream",log.toString());finish(-1,result);
    }catch(Throwable error){log.append("FAIL ").append(error).append('\n');try{Files.write(new File(getTargetContext().getFilesDir(),"startup-audit-result.txt").toPath(),log.toString().getBytes("UTF-8"));}catch(IOException ignored){}result.putString("stream",log.toString());finish(0,result);
    }finally{if(activity!=null)runOnMainSync(()->activity.finish());}}
}
