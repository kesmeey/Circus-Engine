package org.circus.runtime;
import android.content.Context;
import android.graphics.*;
import android.media.*;
import android.os.*;
import android.view.*;
import java.io.*;

/** MPEG-1 video + MP2 audio, paced by AudioTrack's actual playback head. */
final class NativeMovie extends SurfaceView implements AutoCloseable {
    interface Listener {void finished();void failed(Exception error);}
    private final Handler ui=new Handler(Looper.getMainLooper());private volatile boolean closed,paused;private AudioTrack audio;private Thread thread;
    volatile int frames;volatile long playbackMillis;
    NativeMovie(Context context){super(context);setContentDescription("原作影片");}
    void play(File file,Listener listener){play(file,0,listener);}
    void play(File file,long position,Listener listener){thread=new Thread(()->decode(file,position,listener),"circus-mpeg");thread.start();}
    synchronized void pauseMovie(){paused=true;if(audio!=null)audio.pause();}
    synchronized void resumeMovie(){paused=false;if(audio!=null)audio.play();}
    @Override public synchronized void close(){closed=true;if(audio!=null){audio.pause();audio.flush();}if(thread!=null)thread.interrupt();}
    private void decode(File file,long position,Listener listener){long handle=0;Bitmap bitmap=null;try{
        handle=MpegDecoder.open(file.getAbsolutePath());double[] info=new double[6];MpegDecoder.info(handle,info);int width=(int)info[0],height=(int)info[1],rate=(int)info[3];boolean hasAudio=info[4]>0;
        double start=position>0?MpegDecoder.seek(handle,Math.min(position/1000.0,Math.max(0,info[5]-.2))):0;
        int[] pixels=new int[width*height];short[] pcm=new short[2304];bitmap=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888);long origin=SystemClock.uptimeMillis(),pauseAt=0;double audioEnd=start,audioBase=start;boolean audioEof=!hasAudio,firstAudio=true;
        if(hasAudio){int buffer=Math.max(rate,AudioTrack.getMinBufferSize(rate,AudioFormat.CHANNEL_OUT_STEREO,AudioFormat.ENCODING_PCM_16BIT)*4);synchronized(this){if(closed)return;audio=new AudioTrack(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_MOVIE).build(),new AudioFormat.Builder().setSampleRate(rate).setChannelMask(AudioFormat.CHANNEL_OUT_STEREO).setEncoding(AudioFormat.ENCODING_PCM_16BIT).build(),buffer,AudioTrack.MODE_STREAM,AudioManager.AUDIO_SESSION_ID_GENERATE);if(audio.getState()!=AudioTrack.STATE_INITIALIZED)throw new IOException("Movie audio initialization failed");if(!paused)audio.play();}}
        // Some devices wait for a full streaming buffer before advancing the
        // playback head. A fixed 150 ms lead deadlocks on larger buffers.
        double audioLead=hasAudio?Math.max(.15,audio.getBufferSizeInFrames()/(double)rate+.05):0;
        Paint paint=new Paint(Paint.FILTER_BITMAP_FLAG);double videoTime;
        while(!closed&&(videoTime=MpegDecoder.video(handle,pixels))>=0){
            while(!closed&&!audioEof&&audioEnd<videoTime+audioLead){while(paused&&!closed)Thread.sleep(10);double t=MpegDecoder.audio(handle,pcm);if(t<0){audioEof=true;break;}if(firstAudio){audioBase=t;firstAudio=false;}audioEnd=t+1152.0/rate;int offset=0;while(offset<pcm.length&&!closed){while(paused&&!closed)Thread.sleep(10);if(closed)break;int n=audio.write(pcm,offset,pcm.length-offset,AudioTrack.WRITE_NON_BLOCKING);if(n<0)throw new IOException("Movie audio write failed: "+n);offset+=n;if(n==0)Thread.sleep(5);}}
            while(!closed){if(paused){if(pauseAt==0)pauseAt=SystemClock.uptimeMillis();Thread.sleep(10);continue;}if(pauseAt!=0){origin+=SystemClock.uptimeMillis()-pauseAt;pauseAt=0;}double clock=hasAudio?audioBase+(audio.getPlaybackHeadPosition()&0xffffffffL)/(double)rate:start+(SystemClock.uptimeMillis()-origin)/1000.0;if(clock>=videoTime||audioEof&&clock>=audioEnd)break;Thread.sleep(5);}
            if(closed)break;frames++;playbackMillis=(long)(videoTime*1000);if(getHolder().getSurface().isValid()){bitmap.setPixels(pixels,0,width,0,0,width,height);Canvas canvas=getHolder().lockCanvas();if(canvas!=null)try{canvas.drawColor(Color.BLACK);float scale=Math.min(canvas.getWidth()/(float)width,canvas.getHeight()/(float)height),x=(canvas.getWidth()-width*scale)/2,y=(canvas.getHeight()-height*scale)/2;canvas.drawBitmap(bitmap,null,new RectF(x,y,x+width*scale,y+height*scale),paint);}finally{getHolder().unlockCanvasAndPost(canvas);}}
        }
        // Let the queued tail play before releasing the AudioTrack.
        while(!closed&&hasAudio&&audioBase+(audio.getPlaybackHeadPosition()&0xffffffffL)/(double)rate<audioEnd-.002)Thread.sleep(5);
        if(!closed)ui.post(()->{if(!closed)listener.finished();});
    }catch(Exception|UnsatisfiedLinkError error){if(!closed)ui.post(()->{if(!closed)listener.failed(new IOException("MPEG playback failed",error));});}
    finally{if(handle!=0)MpegDecoder.close(handle);if(bitmap!=null)bitmap.recycle();synchronized(this){if(audio!=null){audio.release();audio=null;}}}}
}
