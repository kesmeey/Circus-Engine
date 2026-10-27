package org.circus.runtime;
import android.app.Instrumentation;
import android.os.Bundle;
import android.graphics.Bitmap;
import java.io.*;

/** Opt-in local codec audit. No movie data is distributed in the APK. */
public final class MovieInstrumentation extends Instrumentation {
    private String name;
    public void onCreate(Bundle args){super.onCreate(args);name=args.getString("movie_file");start();}
    public void onStart(){Bundle result=new Bundle();long h=0;try{
        if(name==null||!name.matches("[A-Za-z0-9_-]+\\.mpg"))throw new IOException("Expected local movie_file basename");File movie=new File(getTargetContext().getFilesDir(),name);h=MpegDecoder.open(movie.getAbsolutePath());double[] info=new double[6];MpegDecoder.info(h,info);int[] pixels=new int[(int)info[0]*(int)info[1]];short[] pcm=new short[2304];int frames=0,audio=0;double videoTime=0,audioTime=0;long hash=1,begin=android.os.SystemClock.uptimeMillis();
        while((videoTime=MpegDecoder.video(h,pixels))>=0){frames++;if(frames==31){Bitmap b=Bitmap.createBitmap(pixels,(int)info[0],(int)info[1],Bitmap.Config.ARGB_8888);try(FileOutputStream out=new FileOutputStream(new File(getTargetContext().getFilesDir(),name+".png"))){b.compress(Bitmap.CompressFormat.PNG,100,out);}b.recycle();}while(audioTime<videoTime+.2){double t=MpegDecoder.audio(h,pcm);if(t<0)break;audioTime=t+1152.0/info[3];audio++;for(short sample:pcm)hash=hash*31+sample;}if(frames>30000)throw new IOException("Movie frame budget exceeded");}
        if(frames<10||audio<10||hash==1)throw new IOException("Movie produced no usable video/audio");result.putString("stream","PASS MPEG "+name+" "+(int)info[0]+"x"+(int)info[1]+" fps="+info[2]+" rate="+info[3]+" frames="+frames+" audio_blocks="+audio+" pcm_hash="+hash+" decode_ms="+(android.os.SystemClock.uptimeMillis()-begin)+"\n");finish(-1,result);
    }catch(Throwable e){result.putString("stream","FAIL "+e+"\n");finish(0,result);}finally{if(h!=0)MpegDecoder.close(h);}}
}
