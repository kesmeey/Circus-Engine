package org.circus.runtime;
import android.content.Context;
import android.graphics.*;
import android.media.*;
import android.os.*;
import android.view.*;
import java.io.*;

/** Independent MPEG video/audio workers, with the actual audio head as master clock. */
final class NativeMovie extends SurfaceView implements AutoCloseable {
    interface Listener {void finished();void failed(Exception error);}
    private final Handler ui=new Handler(Looper.getMainLooper());
    private volatile boolean closed,stopping,paused,audioPrimed,audioComplete;
    private volatile Exception audioFailure;
    private AudioTrack audio;
    private Thread thread,audioThread;
    private boolean hasAudio;
    private int rate;
    private double start,audioBase,audioFinal;
    private long origin,pauseAt,pausedMillis,audioCompletedAt;
    volatile int frames,decodedFrames,droppedFrames,audioUnderruns;
    volatile long playbackMillis,audioWrittenFrames,audioPlayedFrames;
    volatile boolean workersStopped;
    // Private instrumentation only; production never injects a delay.
    volatile int testVideoStallMs;

    NativeMovie(Context context){super(context);setContentDescription("原作影片");}
    void play(File file,Listener listener){play(file,0,listener);}
    synchronized void play(File file,long position,Listener listener){
        if(thread!=null||closed)throw new IllegalStateException("Movie already started or closed");
        origin=SystemClock.uptimeMillis();if(paused)pauseAt=origin;
        thread=new Thread(()->decode(file,position,listener),"circus-mpeg-video");thread.start();
    }
    synchronized void pauseMovie(){
        if(!paused){paused=true;pauseAt=SystemClock.uptimeMillis();}
        if(audio!=null)audio.pause();
    }
    synchronized void resumeMovie(){
        if(paused){pausedMillis+=SystemClock.uptimeMillis()-pauseAt;paused=false;}
        if(audio!=null&&audioPrimed&&!audioComplete&&!closed&&!stopping)audio.play();
    }
    @Override public synchronized void close(){
        closed=true;
        if(audio!=null){audio.pause();audio.flush();}
        if(thread!=null)thread.interrupt();if(audioThread!=null)audioThread.interrupt();
    }
    private boolean running(){return !closed&&!stopping&&audioFailure==null;}
    private boolean awaitActive()throws InterruptedException{
        while(paused&&running())Thread.sleep(5);return running();
    }
    private synchronized long activeMillis(){
        return (paused?pauseAt:SystemClock.uptimeMillis())-origin-pausedMillis;
    }
    private synchronized double clock(){
        if(!hasAudio)return start+activeMillis()/1000.0;
        if(audioComplete)return audioFinal+(activeMillis()-audioCompletedAt)/1000.0;
        if(!audioPrimed||audio==null)return start;
        return audioBase+(audio.getPlaybackHeadPosition()&0xffffffffL)/(double)rate;
    }
    private synchronized void startAudio(){
        if(closed||stopping||audio==null||audioPrimed)return;
        audioPrimed=true;if(!paused)audio.play();
    }
    private void decodeAudio(File file,double seek){long handle=0;try{
        android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_AUDIO);
        handle=MpegDecoder.open(file.getAbsolutePath());
        // PL_MPEG seek needs both streams to align audio PTS with the video origin.
        if(seek>0)MpegDecoder.seek(handle,seek);
        MpegDecoder.streams(handle,false,true);
        short[] pcm=new short[2304];boolean first=true;long written=0;
        AudioTrack track; synchronized(this){track=audio;}
        int primeFrames=track.getBufferSizeInFrames();
        while(running()){
            if(audioPrimed&&!awaitActive())break;
            double t=MpegDecoder.audio(handle,pcm);if(t<0)break;
            if(first){synchronized(this){audioBase=t;}first=false;}
            for(int offset=0;offset<pcm.length&&running();){
                if(audioPrimed&&!awaitActive())break;
                int n=track.write(pcm,offset,pcm.length-offset,AudioTrack.WRITE_NON_BLOCKING);
                if(n<0)throw new IOException("Movie audio write failed: "+n);
                offset+=n;written+=n/2;audioWrittenFrames=written;
                if(written>=primeFrames||n==0)startAudio();
                audioPlayedFrames=track.getPlaybackHeadPosition()&0xffffffffL;
                audioUnderruns=track.getUnderrunCount();
                if(n==0)Thread.sleep(3);
            }
        }
        if(!running())return;
        if(first)throw new IOException("Movie audio stream contains no samples");
        startAudio();
        // Keep the final buffer alive through pause/resume, including very short clips.
        while(running()){
            if(!awaitActive())return;
            audioPlayedFrames=track.getPlaybackHeadPosition()&0xffffffffL;
            if(audioPlayedFrames>=written)break;
            Thread.sleep(3);
        }
        synchronized(this){
            if(!running())return;
            // Do not count the expected empty buffer after the last sample as starvation.
            track.pause();audioFinal=audioBase+written/(double)rate;
            audioCompletedAt=activeMillis();audioComplete=true;
        }
    }catch(Exception|UnsatisfiedLinkError e){if(!closed&&!stopping)audioFailure=new IOException("Movie audio failed",e);}
    finally{if(handle!=0)MpegDecoder.close(handle);}}

    private void decode(File file,long position,Listener listener){long handle=0;Bitmap bitmap=null;Exception error=null;try{
        handle=MpegDecoder.open(file.getAbsolutePath());double[] info=new double[6];MpegDecoder.info(handle,info);
        int width=(int)info[0],height=(int)info[1];rate=(int)info[3];hasAudio=info[4]>0;
        double seek=position>0?Math.min(position/1000.0,Math.max(0,info[5]-.2)):0;
        start=seek>0?MpegDecoder.seek(handle,seek):0;playbackMillis=(long)(start*1000);
        MpegDecoder.streams(handle,true,false);
        if(hasAudio){
            int minimum=AudioTrack.getMinBufferSize(rate,AudioFormat.CHANNEL_OUT_STEREO,AudioFormat.ENCODING_PCM_16BIT);
            if(minimum<=0)throw new IOException("Unsupported movie audio format: "+rate);
            int buffer=Math.max(rate,minimum*4); // At least 250 ms of stereo PCM16.
            synchronized(this){
                if(closed)return;
                audio=new AudioTrack(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_MOVIE).build(),
                    new AudioFormat.Builder().setSampleRate(rate).setChannelMask(AudioFormat.CHANNEL_OUT_STEREO).setEncoding(AudioFormat.ENCODING_PCM_16BIT).build(),
                    buffer,AudioTrack.MODE_STREAM,AudioManager.AUDIO_SESSION_ID_GENERATE);
                if(audio.getState()!=AudioTrack.STATE_INITIALIZED)throw new IOException("Movie audio initialization failed");
                audioThread=new Thread(()->decodeAudio(file,seek),"circus-mpeg-audio");audioThread.start();
            }
        }else synchronized(this){origin=SystemClock.uptimeMillis();pausedMillis=0;if(paused)pauseAt=origin;}
        int[] pixels=new int[width*height];bitmap=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888);
        Paint paint=new Paint(Paint.FILTER_BITMAP_FLAG);
        while(running()){
            if(!awaitActive())break;
            while(running()&&hasAudio&&!audioPrimed)Thread.sleep(3);
            if(!running())break;
            // Prediction frames are still decoded; only obsolete RGB conversion/drawing is skipped.
            double dropBefore=frames==0?-1:clock()-.1;
            double videoTime=MpegDecoder.videoTimed(handle,pixels,dropBefore);if(videoTime<0)break;
            decodedFrames++;
            if(testVideoStallMs>0&&decodedFrames%30==0)Thread.sleep(testVideoStallMs);
            if(videoTime<dropBefore){droppedFrames++;continue;}
            while(running()){
                if(!awaitActive())break;
                if(clock()>=videoTime)break;Thread.sleep(3);
            }
            if(!running())break;
            if(frames>0&&clock()-videoTime>.1){droppedFrames++;continue;}
            if(!awaitActive())break;
            playbackMillis=(long)(videoTime*1000);frames++;
            if(getHolder().getSurface().isValid()){
                bitmap.setPixels(pixels,0,width,0,0,width,height);
                Canvas canvas=getHolder().lockCanvas();
                if(canvas!=null)try{
                    canvas.drawColor(Color.BLACK);
                    float scale=Math.min(canvas.getWidth()/(float)width,canvas.getHeight()/(float)height);
                    float x=(canvas.getWidth()-width*scale)/2,y=(canvas.getHeight()-height*scale)/2;
                    canvas.drawBitmap(bitmap,null,new RectF(x,y,x+width*scale,y+height*scale),paint);
                }finally{getHolder().unlockCanvasAndPost(canvas);}
            }
        }
        while(running()&&hasAudio&&!audioComplete)Thread.sleep(5);
        if(audioFailure!=null)throw audioFailure;
    }catch(Exception|UnsatisfiedLinkError e){if(!closed)error=new IOException("MPEG playback failed",e);}
    finally{
        stopping=true;
        Thread worker; synchronized(this){worker=audioThread;if(worker!=null)worker.interrupt();}
        // Only the coordinator waits; close() on the UI thread is always non-blocking.
        boolean interrupted=false;
        if(worker!=null)while(worker.isAlive())try{worker.join();}catch(InterruptedException e){interrupted=true;}
        if(handle!=0)MpegDecoder.close(handle);if(bitmap!=null)bitmap.recycle();
        synchronized(this){if(audio!=null){audio.release();audio=null;}}
        workersStopped=true;if(interrupted)Thread.currentThread().interrupt();
    }
        if(!closed){final Exception failure=error;ui.post(()->{if(!closed){if(failure==null)listener.finished();else listener.failed(failure);}});}
    }
}
