package org.circus.core;

import java.io.*;

/** PP_CN PCM music: interpreter428B00, loader432E80, gain432870, fade433140.
 * tick() models one native timer/frame event. elapse() models a fresh10ms timer;
 * Windows activation timers and the frame fallback belong to the platform scheduler.
 */
public final class PpMusic {
    public static final int DEFAULT_VOLUME=20;
    // PRESENT0/1 retain calls to411450, which is an empty RET in this PP build.
    public enum Action { NONE, PLAY, GAIN, STOP, PRESENT0, PRESENT1 }
    public static final class Result {
        public final Action action;
        public final int track,unlockTrack;
        public final boolean loop;
        private Result(Action action,int track,boolean loop,int unlockTrack){this.action=action;this.track=track;this.loop=loop;this.unlockTrack=unlockTrack;}
    }
    private static final int FORMAT=0x50504d32;
    private final int[] attenuation;
    private int requested=-1,buffer=-1,restore=-1,selected=-1;
    private int requestedDuck,runtimeDuck,masterDb,currentDb=-9999,fadeDb;
    private int queued=-1,queuedDuck,volume=DEFAULT_VOLUME;
    private boolean once,bufferOnce,pcmPlaying,refreshed,enabled=true,fading,replacement,queuedOnce;
    private long timerRemainder;

    public PpMusic(int[] musicExtra3){
        if(musicExtra3==null||musicExtra3.length>210)throw new IllegalArgumentException("PP music table exceeds native capacity");
        attenuation=musicExtra3.clone();
    }
    /** InterpreterB921C8, returned by native99:244. */
    public int track(){return requested;}
    public int playingTrack(){return buffer;}
    public int selectedTrack(){return selected;}
    public int duck(){return requestedDuck;}
    public int playingDuck(){return runtimeDuck;}
    public boolean once(){return once;}
    /** A completed DirectSound buffer remains allocated until an explicit release. */
    public boolean active(){return buffer>=0;}
    public boolean playing(){return pcmPlaying;}
    public boolean fading(){return fading;}
    public boolean replacing(){return replacement;}
    public int pendingTrack(){return queued;}
    public int pendingDuck(){return queuedDuck;}
    public boolean pendingOnce(){return queuedOnce;}
    public int hundredthDb(){return currentDb;}
    public int fadeHundredthDb(){return fadeDb;}
    public long timerRemainder(){return timerRemainder;}
    /** B55380 is the caller's restored scenario context, not its last music request. */
    public void setRestoreTrack(int track){if(track< -1||track>253)throw new IllegalArgumentException("PP restore music index");restore=track;}
    private Result result(Action action,int unlock){return new Result(action,buffer,!bufferOnce,unlock);}
    private static void u8(int value,String name)throws IOException{if(value<0||value>255)throw new IOException("PP music "+name+" is not a byte");}
    private static int db(int master,int raw){if(raw<1)return -9999;int value=master+(Math.min(raw,60)-60)*83;return value< -9998?-9999:value;}
    private int refreshDb(int level){return db(masterDb,level*2-runtimeDuck);}

    /** duck is operand1 of at most one immediately following06/u8/u8 record. */
    public Result command(int code,int mode,int duck)throws IOException{return command(code,mode,duck,true,volume);}
    public Result command(int code,int mode,int duck,boolean enabled)throws IOException{return command(code,mode,duck,enabled,volume);}
    public Result command(int code,int mode,int duck,boolean enabled,int level)throws IOException {
        u8(code,"selector");u8(mode,"mode");u8(duck,"duck");
        if(code<254&&(mode==10||mode==11))return result(mode==10?Action.PRESENT0:Action.PRESENT1,-1);
        this.enabled=enabled;volume=level;
        if(code==255){requested=selected=-1;cancelQueue();fading=replacement=false;timerRemainder=0;fadeDb=0;release();return result(Action.STOP,-1);}
        if(code==254)return request(restore,false,duck,false,level);
        if(mode==1&&requested==code){if(requestedDuck==duck)return result(Action.NONE,-1);return duck(duck,level);}
        if(mode==0||mode==1||mode==2)return request(code,mode==2,duck,true,level);
        // Native default42C161 still remembers the request, without changing playback.
        requested=code;return result(Action.NONE,-1);
    }
    private Result request(int track,boolean single,int duck,boolean unlock,int level){
        requested=track;requestedDuck=duck;once=single;
        return submit(track,single,duck,level,unlock&&track>=0&&track<210?track:-1);
    }
    private Result submit(int track,boolean single,int duck,int level,int unlock){
        // The disabled branch precedes the NAMES check and preserves the old buffer.
        if(!enabled){selected=track;runtimeDuck=duck;return result(Action.NONE,unlock);}
        if(track<0||track>=attenuation.length)return result(Action.NONE,unlock);
        if(buffer>=0){
            queued=track;queuedOnce=single;queuedDuck=duck;replacement=true;
            if(!fading){fading=true;timerRemainder=0;if(fadeDb==0)fadeDb=refreshDb(level);}
            return result(Action.NONE,unlock);
        }
        load(track,single,duck,level);return result(Action.PLAY,unlock);
    }
    private void load(int track,boolean single,int duck,int level){
        buffer=selected=track;bufferOnce=single;pcmPlaying=true;runtimeDuck=duck;
        masterDb=(attenuation[track]+duck)*-100;currentDb=db(masterDb,(level-duck)*2);refreshed=false;replacement=false;
    }
    private void release(){buffer=-1;bufferOnce=false;pcmPlaying=false;masterDb=0;currentDb=-9999;refreshed=false;}
    private void cancelQueue(){queued=-1;queuedDuck=0;queuedOnce=false;}

    /** Standalone06 always refreshes gain, including an unchanged operand. */
    public Result duck(int value)throws IOException{return duck(value,volume);}
    public Result duck(int value,int level)throws IOException{u8(value,"duck");requestedDuck=runtimeDuck=value;volumeChanged(level);return result(Action.GAIN,-1);}
    /** 99:7 sets channel volume without overwriting the fade accumulator. */
    public void volumeChanged(){volumeChanged(volume);}
    public void volumeChanged(int level){volume=level;refreshed=true;if(buffer>=0)currentDb=refreshDb(level);}
    /** 99:14=0 clears queued replacement and fades an allocated buffer. */
    public Result disable(){return disable(volume);}
    public Result disable(int level){enabled=false;return fade(level);}
    /** 99:14!=0 replays looping with duck0, without writing music unlocks. */
    public Result enable(){return enable(volume);}
    public Result enable(int level){enabled=true;volume=level;if(requested<0)return result(Action.NONE,-1);return request(requested,false,0,false,level);}
    /** Opcode12 forgets the request while retaining its buffer during the fade. */
    public Result stopFading(){return stopFading(volume);}
    public Result stopFading(int level){requested=-1;return fade(level);}
    private Result fade(int level){
        volume=level;selected=-1;cancelQueue();
        if(buffer>=0&&!fading){fading=true;timerRemainder=0;fadeDb=refreshDb(level);}
        // 431B10 retains an already active replacement fade's faster rate.
        return result(Action.NONE,-1);
    }
    /** Natural PCM completion neither forgets the request nor releases the buffer. */
    public void completed(){pcmPlaying=false;}
    public float gain(){return buffer<0?0:(float)Math.pow(10,currentDb/2000.0);}
    /** Legacy pure volume projection; new callers set volumeChanged(level) and use gain(). */
    public float gain(int level){return buffer<0?0:(float)Math.pow(10,(fading?currentDb:refreshed?refreshDb(level):db(masterDb,(level-runtimeDuck)*2))/2000.0);}
    /** One native433140 event; the shared Windows scheduler can call this directly. */
    public Result tick(int level){volume=level;timerRemainder=0;return advanceTick(level);}
    public Result tick(){return tick(volume);}
    private Result advanceTick(int level){
        if(!fading)return result(Action.NONE,-1);
        if(buffer<0){fading=false;return result(Action.NONE,-1);}
        int amount=replacement?800:150,next=fadeDb-amount;
        if(next< -5000)next=fadeDb-amount*2;
        fadeDb=next;
        if(next>=-9999){currentDb=next;return result(Action.NONE,-1);}
        // Native writes zero just before release, without an audible frame between.
        fading=replacement=false;fadeDb=0;timerRemainder=0;release();
        int nextTrack=queued,nextDuck=queuedDuck;boolean nextOnce=queuedOnce;cancelQueue();
        if(nextTrack>=0){Result result=submit(nextTrack,nextOnce,nextDuck,level,-1);if(result.action==Action.PLAY)return result;}
        return result(Action.STOP,-1);
    }
    /** A fresh BGM/SE timer requests10ms. WM_TIMER coalescing and frame fallback are external. */
    public Result elapse(long milliseconds){return elapse(milliseconds,volume);}
    public Result elapse(long milliseconds,int level){
        volume=level;if(milliseconds<0)throw new IllegalArgumentException("Negative PP music clock");
        if(!fading)return result(Action.NONE,-1);
        long remainder=timerRemainder+milliseconds%10,ticks=milliseconds/10+remainder/10;timerRemainder=remainder%10;
        while(ticks-->0){Result step=advanceTick(level);if(step.action==Action.PLAY||step.action==Action.STOP)return step;if(!fading){timerRemainder=0;break;}}
        return result(Action.NONE,-1);
    }

    public void save(DataOutput out)throws IOException {
        out.writeInt(FORMAT);out.writeInt(attenuation.length);for(int value:attenuation)out.writeInt(value);
        for(int value:new int[]{requested,buffer,restore,selected,requestedDuck,runtimeDuck,masterDb,currentDb,fadeDb,queued,queuedDuck,volume})out.writeInt(value);
        for(boolean value:new boolean[]{once,bufferOnce,pcmPlaying,refreshed,enabled,fading,replacement,queuedOnce})out.writeBoolean(value);
        out.writeLong(timerRemainder);
    }
    public void load(DataInput in)throws IOException {
        if(in.readInt()!=FORMAT)throw new IOException("PP music snapshot format");
        if(in.readInt()!=attenuation.length)throw new IOException("PP music table changed");
        for(int expected:attenuation)if(in.readInt()!=expected)throw new IOException("PP music metadata changed");
        int[] v=new int[12];for(int i=0;i<v.length;i++)v[i]=in.readInt();
        boolean[] f=new boolean[8];for(int i=0;i<f.length;i++)f[i]=in.readBoolean();long rem=in.readLong();
        if(v[0]<-1||v[0]>253||v[1]<-1||v[1]>=attenuation.length||v[2]<-1||v[2]>253||v[3]<-1||v[3]>253||v[4]<0||v[4]>255||v[5]<0||v[5]>255||v[9]<-1||v[9]>=attenuation.length||v[10]<0||v[10]>255||rem<0||rem>=10||v[1]<0&&(f[2]||f[5])||!f[5]&&(rem!=0||v[8]!=0||f[6])||v[9]>=0&&(!f[5]||!f[6])||v[7]<-9999||v[7]>0||v[8]<-9999||v[8]>0||v[1]<0&&(v[6]!=0||v[7]!=-9999))throw new IOException("PP music snapshot state");
        if(v[1]>=0&&(v[6]>-(long)attenuation[v[1]]*100||v[6]<-(attenuation[v[1]]+255L)*100))throw new IOException("PP music snapshot attenuation");
        requested=v[0];buffer=v[1];restore=v[2];selected=v[3];requestedDuck=v[4];runtimeDuck=v[5];masterDb=v[6];currentDb=v[7];fadeDb=v[8];queued=v[9];queuedDuck=v[10];volume=v[11];once=f[0];bufferOnce=f[1];pcmPlaying=f[2];refreshed=f[3];enabled=f[4];fading=f[5];replacement=f[6];queuedOnce=f[7];timerRemainder=rem;
    }
}
