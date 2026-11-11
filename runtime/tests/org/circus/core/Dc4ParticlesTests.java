package org.circus.core;

import java.io.*;
import java.util.*;

/** Native-derived density, atlas and portable capture checks for PH's owner. */
public final class Dc4ParticlesTests {
    private static int n;
    private static void check(boolean ok,String why){n++;if(!ok)throw new AssertionError(why);}
    private static byte[] bytes(Dc3Petals p)throws IOException{ByteArrayOutputStream b=new ByteArrayOutputStream();p.save(new DataOutputStream(b));return b.toByteArray();}
    public static int run()throws Exception{
        n=0;Dc3Petals petals=new Dc3Petals();petals.startPh(13,2,640,360,90,180,120);Dc4Particles ph=petals.ph();
        check(ph.sprites().size()==15,"41DAC0 density2 creates three entries in each of five native planes");
        for(Dc4Particles.Sprite p:ph.sprites()){
            check((p.width==40&&p.sx==140)||(p.width==20&&p.sx==180)||(p.width==8&&p.sx==200),"41CF60 glow uses one of three actual atlas rectangles");
            check(p.height==p.width&&p.sy==0&&p.scale>=300&&p.scale<=699&&p.alpha>=0&&p.alpha<=.09f,"Native glow crop, scale and alpha bounds");
        }
        Dc4Particles.Sprite first=ph.sprites().get(0);check(Math.abs(first.y-360)<.001&&first.x>=648&&first.x<=667,"Native degree-based sine-X/cosine-Y origin and initial radius");
        petals.elapse(1000);check(first.phase>90&&first.phase<180,"PH angular motion advances with native twelve-tick command duration");
        byte[] saved=bytes(petals);Dc3Petals captured=petals.copy();check(Arrays.equals(saved,bytes(captured)),"Published frame copy retains the complete edition-owned particle state");
        petals.elapse(500);captured.elapse(500);check(Arrays.equals(bytes(petals),bytes(captured)),"Cold restore continues the same portable clock and RNG");
        for(int mode:new int[]{1,2,3,9,11}){petals.startPh(mode,6,0,0,0,-1,0);check(petals.ph().sprites().size()==100,"Density6 native maximum is one hundred entries");check(bytes(petals.copy()).length==bytes(petals).length,"Every supported atmosphere mode survives frame capture");}
        byte[] invalid=saved.clone();Arrays.fill(invalid,56,60,(byte)0xff);try{Dc3Petals bad=new Dc3Petals();bad.load(new DataInputStream(new ByteArrayInputStream(invalid)));throw new AssertionError("Invalid particle count accepted");}catch(IOException expected){n++;}
        petals.start(1,2);check(petals.ph()==null&&petals.effect==1,"Legacy owner selection clears edition-only state");
        petals.startPh(13,0,0,0,0,-1,0);check(petals.ph().sprites().isEmpty(),"13,0 clears glow entries");
        petals.start(0);check(petals.ph()==null&&petals.particles.isEmpty(),"Native atmosphere stop clears the PH owner");
        return n;
    }
}
