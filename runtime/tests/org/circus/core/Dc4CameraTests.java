package org.circus.core;

import java.io.*;
import java.util.*;

/** Native PH9D range and447FF0 unit-scale transitions, plus old stream parity. */
public final class Dc4CameraTests {
    private static int checks;
    private static void check(boolean ok,String why){checks++;if(!ok)throw new AssertionError(why);}
    private static byte[] save(Dc3Camera camera)throws IOException{ByteArrayOutputStream bytes=new ByteArrayOutputStream();camera.save(new DataOutputStream(bytes));return bytes.toByteArray();}
    private static Dc3Camera load(byte[] bytes)throws IOException{Dc3Camera camera=new Dc3Camera();DataInputStream in=new DataInputStream(new ByteArrayInputStream(bytes));camera.load(in);check(in.available()==0,"Camera stream consumed exactly");return camera;}
    private static byte[] hex(String value){byte[] bytes=new byte[value.length()/2];for(int n=0;n<bytes.length;n++)bytes[n]=(byte)Integer.parseInt(value.substring(n*2,n*2+2),16);return bytes;}
    private static Dc3Camera ph(){Dc3Camera camera=new Dc3Camera();camera.enablePh();return camera;}
    private static void legacy()throws Exception{
        Dc3Camera camera=new Dc3Camera();camera.start(0,150,600,-400);
        // Frozen old56-byte wire: nine big-endian floats, two long clocks,
        // speed5. A PH prefix must never appear in ordinary DD/TY saves.
        byte[] old=hex("4316000044000000c390000042c8000000000000000000004316000044000000c39000000000000000000000000000000000000000000005");
        check(Arrays.equals(old,save(camera)),"Existing camera bytes remain exactly equal to the pre-PH wire");
        Dc3Camera cold=load(old);check(!cold.isPh()&&cold.x==512&&cold.y==-288,"Old stream cold-load retains DD range and family");
        camera=new Dc3Camera();camera.start(1,150,200,100);camera.elapse(500);
        check(camera.zoom==125&&camera.x==100&&camera.y==50,"Old100-to-zoom interpolation stays unchanged");
        Dc3Camera reused=ph();reused.load(new DataInputStream(new ByteArrayInputStream(old)));
        check(!reused.isPh(),"Stream family replaces reused instance family rather than leaking PH mode");
    }
    private static void rangeAndFrames()throws Exception{
        Dc3Camera camera=ph();camera.start(0,120,600,300);
        check(camera.x==600&&camera.y==300,"Native PH script600/300 is not cropped by old DD bounds");
        check(save(camera).length==60,"PH camera adds one explicit family/version word");
        Dc3Camera cold=load(save(camera));check(cold.isPh()&&cold.x==600&&cold.y==300,"Cold camera load admits self-described PH range");
        cold.start(0,120,750,800);check(cold.x==639&&cold.y==359,"PH positive native offsets clamp639/359");
        cold.start(0,120,-750,-800);check(cold.x==-639&&cold.y==-359,"PH negative native offsets clamp639/359");
        Dc3Camera copy=camera.copy();check(copy.isPh()&&Arrays.equals(save(copy),save(camera)),"Camera copy retains family, values and wire state");
        Dc3Scene scene=new Dc3Scene();scene.camera.enablePh();scene.camera.start(0,150,600,300);scene.publish();
        check(scene.published.camera.isPh()&&scene.published.camera.x==600,"Published Frame carries PH mode and expanded offset");
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();scene.published.save(new DataOutputStream(bytes));
        Dc3Scene.Frame frame=new Dc3Scene.Frame();DataInputStream input=new DataInputStream(new ByteArrayInputStream(bytes.toByteArray()));frame.load(input);
        check(input.available()==0&&frame.camera.isPh()&&frame.camera.x==600&&frame.camera.y==300,"Cold Frame.load accepts PH camera without a host/profile side channel");
        bytes.reset();scene.save(new DataOutputStream(bytes));Dc3Scene restored=new Dc3Scene();input=new DataInputStream(new ByteArrayInputStream(bytes.toByteArray()));restored.load(input);
        check(input.available()==0&&restored.camera.isPh()&&restored.published.camera.isPh()&&restored.camera.x==600,"Scene cold-load retains both live and captured PH cameras");
        byte[] invalid=save(camera);invalid[8]=0x44;invalid[9]=0x20;invalid[10]=0;invalid[11]=0; // x=640, beyond PH639.
        boolean rejected=false;try{load(invalid);}catch(IOException expected){rejected=true;}check(rejected,"PH stream rejects offsets beyond its native range");
        byte[] legacy=save(new Dc3Camera());legacy[4]=0x44;legacy[5]=0x16;legacy[6]=0;legacy[7]=0; // x=600 without PH marker.
        rejected=false;try{load(legacy);}catch(IOException expected){rejected=true;}check(rejected,"Unmarked old stream cannot silently acquire PH's wider range");
    }
    private static void timedPivots()throws Exception{
        Dc3Camera camera=ph();camera.start(1,150,600,300);
        check(camera.zoom==100&&camera.x==600&&camera.y==300,"Native100-to-zoom adopts target pivot immediately");
        camera.elapse(500);check(camera.zoom==125&&camera.x==600&&camera.y==300,"Zoom-in uses a fixed target pivot through the first half");
        Dc3Camera cold=load(save(camera));camera.elapse(500);cold.elapse(500);
        check(Arrays.equals(save(camera),save(cold)),"Mid-animation cold restore reaches identical camera state");
        camera.start(1,200,-200,-100);camera.elapse(500);
        check(camera.zoom==175&&camera.x==200&&camera.y==100,"Two nonunit scales interpolate their pivots linearly");
        camera.elapse(500);camera.start(1,100,500,250);
        check(camera.x==-200&&camera.y==-100,"Zoom-out retains the previous pivot rather than new script offsets");
        camera.elapse(500);check(camera.zoom==150&&camera.x==-200&&camera.y==-100,"Zoom-out keeps its old pivot at halfway");
        camera.elapse(500);check(camera.zoom==100&&camera.x==-200&&camera.y==-100&&!camera.active(),"Zoom-out ends at100 with unchanged pivot");
        camera.start(1,100,600,300);camera.elapse(1000);check(camera.x==-200&&camera.y==-100,"Unit-to-unit timed command leaves an unobservable pivot unchanged");
        camera.start(0,100,600,300);check(camera.x==600&&camera.y==300,"Immediate native command still writes its requested pivot at100");
    }
    private static void wordSpeed()throws Exception{
        Dc3Camera camera=ph();camera.speed=500;camera.start(1,110,0,0);camera.elapse(1000);
        Dc3Camera cold=load(save(camera));check(cold.speed==500&&cold.duration==100000,"Actual PH prologue word-speed500 survives a cold restore");
        camera.elapse(99000);cold.elapse(99000);check(Arrays.equals(save(camera),save(cold)),"Restored hundred-second camera reaches the same final state");
        camera.speed=32767;camera.start(1,120,0,0);cold=load(save(camera));check(cold.duration==6553400&&cold.speed==32767,"PH admits the nonnegative native signed-word speed range");
        camera=new Dc3Camera();camera.speed=500;camera.start(1,110,0,0);boolean rejected=false;try{load(save(camera));}catch(IOException expected){rejected=true;}check(rejected,"Old-family stream still rejects PH-only expanded camera timing");
    }
    public static int run()throws Exception{checks=0;legacy();rangeAndFrames();timedPivots();wordSpeed();return checks;}
    public static void main(String[] args)throws Exception{System.out.println("Dc4CameraTests "+run()+" PASS");}
}
