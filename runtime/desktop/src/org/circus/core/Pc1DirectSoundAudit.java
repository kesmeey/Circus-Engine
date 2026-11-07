package org.circus.core;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Actual Windows API probe using muted self-authored PCM only. Not a game
 *startup/route/decoder-fidelity claim; original executables are never run. */
public final class Pc1DirectSoundAudit {
    private static int checks;
    private static void check(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
    private static void put16(byte[] bytes,int at,int value){bytes[at]=(byte)value;bytes[at+1]=(byte)(value>>>8);}
    private static void put32(byte[] bytes,int at,int value){for(int n=0;n<4;n++)bytes[at+n]=(byte)(value>>>(8*n));}
    private static byte[] format(){byte[] bytes=new byte[18];put16(bytes,0,1);put16(bytes,2,1);put32(bytes,4,22050);put32(bytes,8,44100);put16(bytes,12,2);put16(bytes,14,16);return bytes;}
    public static void main(String[] args)throws Exception{
        if(args.length!=1)throw new IOException("Expected explicitly built PC1 JNI DLL path");
        try(Pc1DirectSound output=new Pc1DirectSound(Paths.get(args[0]),0,null)){
            int[] info=output.initialization();System.out.println("initialization="+Arrays.toString(info));System.out.println("deviceIdentity="+output.deviceIdentity());System.out.println("librarySha256="+Pc1DirectSound.loadedLibrarySha256());
            if(!output.pcmInitialized()){System.out.println("UNAVAILABLE actual DirectSound device; HRESULT retained; buffer/output tests not run");System.exit(3);}
            byte[] pcm=new byte[44100];for(int n=0;n<pcm.length;n++)pcm[n]=(byte)(n*17+(n>>>3));
            for(int ch=0;ch<10;ch++){
                check(output.createBuffer(ch,pcm.length,0x40ea,format())==0,"Actual ten-channel secondary buffer create:"+ch);
                int[] lock=output.writeBuffer(ch,0,pcm,0,pcm.length);check(lock[0]==0&&lock[1]==0&&lock[2]+lock[3]==pcm.length,"Actual Lock/Unlock regions:"+ch);
                check(Arrays.equals(pcm,output.bufferBytes(ch)),"Actual DirectSound memory roundtrip:"+ch);
                check(output.setVolume(ch,-10000)==0,"Mute actual audit output:"+ch);
                int[] rate=output.query(ch,3,0);check(rate[0]==0&&rate[1]==22050,"Actual initial native frequency:"+ch);
            }
            check(output.setVolume(0,-500)==0&&output.query(0,2,0)[1]==-500,"Native logarithmic volume units survive real COM calls");output.setVolume(0,-10000);
            check(output.setPan(0,1000)==0&&output.query(0,4,0)[1]==1000,"Native pan units survive real COM calls");
            check(output.setFrequency(0,11025)==0&&output.query(0,3,0)[1]==11025,"Native playback frequency survives real COM calls");
            check(output.play(0,1)==0&&output.status(0)!=0,"Actual looping buffer status");Thread.sleep(40);int[] position=output.query(0,0,0);check(position[0]==0,"Actual DirectSound cursor query");System.out.println("playCursor="+Integer.toUnsignedLong(position[1])+";writeCursor="+Integer.toUnsignedLong(position[2]));
            check(output.stop(0)==0&&output.status(0)==0,"Actual stop is observable through GetStatus");
            int token=output.setTimer(2,10);check(token!=0,"Actual Win32 timer allocation");Thread.sleep(30);List<Pc1DirectSound.Event> events=output.drainServiceEvents();check(events.stream().anyMatch(e->e.kind==1&&e.token==token),"Actual WM_TIMER delivered only through explicit service drain");check(output.killTimer(token)!=0,"Actual timer cancellation");
            Path missing=Paths.get(args[0]).toAbsolutePath().getParent().resolve("missing-"+UUID.randomUUID()+".mid");check(!Files.exists(missing),"MCI error probe path absent");int mci=output.mci(("open \""+missing+"\"").getBytes(StandardCharsets.US_ASCII),false);check(mci!=0,"Actual MCI error is not swallowed or replaced by PCM");System.out.println("missingMidiMciError="+mci);
            byte[] overlap=new byte[300];Arrays.fill(overlap,(byte)'x');check(output.mci(overlap,false)!=0,"Actual MCI uses adjacent256B command/response with command overlapping response");boolean beyond=false;try{byte[] longCommand=new byte[512];Arrays.fill(longCommand,(byte)'x');output.mci(longCommand,false);}catch(IOException expected){beyond=expected.getMessage().contains("known adjacent512B");}check(beyond,"Command crossing known stack frame remains a precise required owner boundary");byte[] terminated=new byte[600];Arrays.fill(terminated,(byte)'x');terminated[0]=0;check(output.mci(terminated,false)!=0,"MCI reads only actual C prefix, not array bytes beyond its NUL");
            for(int ch=0;ch<10;ch++)output.release(ch);
            System.out.println("Pc1DirectSoundAudit PASS "+checks+"; actual DirectSound/MCI/timer calls; muted synthetic PCM; loader/stream/restore scope separate");
        }
    }
}
