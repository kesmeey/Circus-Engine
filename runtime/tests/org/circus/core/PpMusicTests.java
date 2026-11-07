package org.circus.core;

import java.io.*;
import java.util.*;

/** Native PCM request/buffer/queue counterexamples; no original executable is run. */
public final class PpMusicTests {
    private static int count;
    private static void check(boolean value,String message){count++;if(!value)throw new AssertionError(message);}
    private static void near(float value,double expected,String message){check(Math.abs(value-expected)<0.000001,message+": "+value);}
    private static PpMusic music(){return new PpMusic(new int[]{3,8,0});}
    private static byte[] save(PpMusic music)throws IOException{ByteArrayOutputStream out=new ByteArrayOutputStream();music.save(new DataOutputStream(out));return out.toByteArray();}
    private static PpMusic copy(PpMusic source)throws IOException{PpMusic target=music();target.load(new DataInputStream(new ByteArrayInputStream(save(source))));return target;}
    private static void fails(PpMusic music,byte[] data)throws IOException {
        byte[] before=save(music);boolean failed=false;try{music.load(new DataInputStream(new ByteArrayInputStream(data)));}catch(IOException e){failed=true;}
        check(failed,"Invalid or differently bound snapshot is rejected");check(Arrays.equals(before,save(music)),"Rejected snapshot leaves state unchanged");
    }
    private static byte[] integer(byte[] original,int offset,int value){byte[] result=original.clone();for(int n=0;n<4;n++)result[offset+n]=(byte)(value>>>(24-8*n));return result;}
    public static int run()throws Exception {
        count=0;PpMusic m=music();check(PpMusic.DEFAULT_VOLUME==20&&!m.active()&&m.gain()==0,"Fresh native PCM state and PP volume default");
        PpMusic.Result first=m.command(0,0,0,true,20);
        check(first.action==PpMusic.Action.PLAY&&first.track==0&&first.loop&&first.unlockTrack==0,"First mode0 starts looping and unlocks");
        check(m.hundredthDb()==-1960&&m.playing(),"Metadata extra3 adds -300 before startup volume");near(m.gain(),Math.pow(10,-1960/2000.0),"Native DS to linear conversion");
        byte[] unchanged=save(m);check(m.command(0,1,0,true,20).action==PpMusic.Action.NONE&&Arrays.equals(unchanged,save(m)),"Mode1 same request and duck preserves playback state");
        check(m.command(9,10,99,false,3).action==PpMusic.Action.PRESENT0&&Arrays.equals(unchanged,save(m)),"Mode10 native RET cannot change enable, volume or selection");
        check(m.command(9,11,99,false,3).action==PpMusic.Action.PRESENT1&&Arrays.equals(unchanged,save(m)),"Mode11 native RET is also empty");
        float projection=m.gain(30);check(projection>m.gain()&&Arrays.equals(unchanged,save(m)),"Legacy gain projection is a pure query");
        PpMusic.Result queued=m.command(1,2,0,true,20);
        check(queued.action==PpMusic.Action.NONE&&queued.unlockTrack==1&&m.track()==1&&m.once(),"Mode2 records/unlocks immediately while old buffer fades");
        check(m.playingTrack()==0&&m.pendingTrack()==1&&m.pendingOnce()&&m.replacing(),"Replacement keeps old buffer and pending once flag distinct");
        check(m.elapse(9,20).action==PpMusic.Action.NONE&&m.hundredthDb()==-1960,"Fresh timer has no event before10ms");
        m.elapse(1,20);check(m.hundredthDb()==-2760,"Replacement first native event subtracts800");
        m.elapse(20,20);check(m.hundredthDb()==-4360,"Replacement uses800 before crossing -5000");
        m.elapse(10,20);check(m.hundredthDb()==-5960,"Crossing threshold subtracts1600 from previous value");
        check(m.elapse(29,20).action==PpMusic.Action.NONE&&m.playingTrack()==0,"Replacement is not submitted before its final event");
        PpMusic.Result committed=m.elapse(1,20);check(committed.action==PpMusic.Action.PLAY&&committed.track==1&&!committed.loop&&committed.unlockTrack==-1,"At70ms old buffer releases and one queued track starts without another unlock");
        check(m.hundredthDb()==-2460&&!m.fading()&&m.pendingTrack()==-1,"New buffer starts with its own metadata and clears queue");
        m.completed();check(!m.playing()&&m.active()&&m.track()==1,"Natural completion preserves buffer allocation and requested track");
        check(m.command(1,1,0,true,20).action==PpMusic.Action.NONE&&!m.playing(),"Same-track mode1 does not replay a naturally completed buffer");
        check(m.command(1,0,0,true,20).action==PpMusic.Action.NONE&&m.fading(),"Mode0 still queues behind an allocated but naturally completed buffer");
        check(m.elapse(1000,20).action==PpMusic.Action.PLAY&&m.playing(),"Silent old buffer follows the same release/queue path");
        m.command(2,0,9,true,20);PpMusic.Result stopped=m.command(255,10,1,true,20);
        check(stopped.action==PpMusic.Action.STOP&&m.track()==-1&&!m.active()&&!m.fading()&&m.pendingTrack()==-1,"FF stops immediately and cancels pending playback, including mode10");
        check(m.elapse(Long.MAX_VALUE,20).action==PpMusic.Action.NONE,"Stopped queue cannot revive after a large clock advance");
        m.setRestoreTrack(1);PpMusic.Result restored=m.command(254,11,2,true,20);
        check(restored.action==PpMusic.Action.PLAY&&restored.track==1&&restored.loop&&restored.unlockTrack==-1&&!m.once(),"FE precedes mode11 and restores saved scenario track looping without unlock");

        m=music();m.command(0,0,4,true,20);check(m.hundredthDb()==-3024,"Startup duck reduces both master and raw volume");
        m.duck(1,20);check(m.hundredthDb()==-2443,"06 uses odd raw update argument and retained master");
        m.command(255,0,0);m.command(0,0,4,true,20);m.volumeChanged(20);check(m.hundredthDb()==-2692,"99:7 same-value write switches startup to update formula");
        m.volumeChanged(0);check(m.hundredthDb()==-9999,"Raw argument below1 uses native -9999 floor");
        m.command(255,0,0);m.command(0,0,4,true,20);m.command(1,0,4,true,20);
        check(m.fadeHundredthDb()==-2692&&m.hundredthDb()==-3024,"Replacement captures refresh formula but leaves audible startup gain until its first event");
        m.tick(20);check(m.hundredthDb()==-3492,"First replacement event uses independently captured fade value");
        int accumulator=m.fadeHundredthDb();m.volumeChanged(30);check(m.hundredthDb()==-1032&&m.fadeHundredthDb()==accumulator,"Volume setter changes actual DS gain without rewriting fade accumulator");
        m.tick(30);check(m.hundredthDb()==accumulator-800,"Next fade event resumes original accumulator");
        m.duck(1,30);check(m.pendingDuck()==4&&m.duck()==1,"Runtime duck updates do not rewrite already queued track duck");
        check(m.elapse(1000,30).action==PpMusic.Action.PLAY&&m.playingDuck()==4&&m.duck()==1,"Queued startup retains its captured duck while interpreter memory remains separate");

        m=music();m.command(0,0,0,true,20);int audible=m.hundredthDb();m.disable(20);
        check(m.track()==0&&m.hundredthDb()==audible&&m.fading()&&!m.replacing(),"Disable preserves requested track and begins audible ordinary fade");
        m.elapse(200,20);check(m.hundredthDb()==-4960,"Ordinary fade subtracts150 through its threshold");
        m.elapse(10,20);check(m.hundredthDb()==-5260,"Ordinary threshold event subtracts300");
        m.elapse(150,20);check(m.hundredthDb()==-9760&&m.active(),"-9999 threshold is not an early hard mute");
        check(m.elapse(10,20).action==PpMusic.Action.STOP&&!m.active()&&m.track()==0,"Ordinary fade finally releases buffer while disabled request remains");
        check(m.command(2,2,9,false,20).unlockTrack==2&&!m.active()&&m.playingDuck()==9,"Disabled request still records runtime duck and unlocks without allocating");
        PpMusic.Result enable=m.enable(20);check(enable.action==PpMusic.Action.PLAY&&enable.track==2&&enable.loop&&enable.unlockTrack==-1&&m.duck()==0&&!m.once(),"Enable resets duck and once without a script unlock");
        m.command(1,0,0,true,20);m.disable(20);check(m.pendingTrack()==-1&&m.replacing(),"Disable cancels queued replacement but preserves an already fast fade");
        check(m.elapse(1000,20).action==PpMusic.Action.STOP&&!m.active(),"Cancelled pending track is not committed");
        m.enable(20);m.stopFading(20);m.duck(1,20);check(m.track()==-1,"Opcode12 request stays forgotten through a later06 refresh");
        m.elapse(1000,20);check(!m.active(),"Opcode12 also releases through ordinary fade");

        m=music();m.command(0,0,0,true,20);m.command(1,0,2,true,20);m.elapse(23,20);m.command(2,2,3,true,20);
        PpMusic copy=copy(m);check(Arrays.equals(save(copy),save(m)),"Snapshot preserves pending replacement, fade and sub-tick phase");
        for(int duration:new int[]{0,1,6,7,9,10,17,46,47,70,999}){
            PpMusic whole=copy(m),pieces=copy(m);PpMusic.Action action=whole.elapse(duration,20).action;PpMusic.Action split=PpMusic.Action.NONE;
            for(int remaining=duration;remaining>0;){int step=Math.min(remaining,3);PpMusic.Result result=pieces.elapse(step,20);if(result.action!=PpMusic.Action.NONE)split=result.action;remaining-=step;}
            check(Arrays.equals(save(whole),save(pieces))&&action==split,"Large and split clock updates agree at "+duration+"ms");
        }
        check(copy.elapse(1000,30).track==2&&copy.playingDuck()==3&&copy.hundredthDb()==-798,"Latest queued track/duck/once wins and samples volume at actual commit");
        m=music();m.command(1,17,5,true,20);check(m.track()==1&&!m.active(),"Other native modes remember request without starting playback");
        check(m.enable(20).unlockTrack==-1&&m.active(),"Enable after a default-mode request does not invent an unlock");
        m.command(255,0,0);PpMusic.Result absent=m.command(8,0,0,true,20);check(absent.action==PpMusic.Action.NONE&&absent.unlockTrack==8&&m.track()==8,"Missing NAMES entry preserves native request/unlock side effects");
        byte[] saved=save(copy);fails(m,integer(saved,0,0x50504d31));fails(m,integer(saved,20+7*4,Integer.MAX_VALUE));fails(m,integer(saved,20+8*4,Integer.MIN_VALUE));fails(m,Arrays.copyOf(saved,saved.length-1));fails(new PpMusic(new int[]{3,9,0}),saved);
        boolean invalid=false;try{m.command(256,0,0);}catch(IOException expected){invalid=true;}check(invalid,"Only byte operands are accepted");
        return count;
    }
    public static void main(String[] args)throws Exception{System.out.println("PP music assertions: "+run());}
}
