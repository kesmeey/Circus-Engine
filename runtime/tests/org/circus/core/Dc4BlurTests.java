package org.circus.core;

import java.io.*;
import java.util.*;

/** Pixel, animation and cold-snapshot checks for native B1/B2. */
public final class Dc4BlurTests {
    private static int checks;
    private static void check(boolean ok,String why){checks++;if(!ok)throw new AssertionError(why);}
    private static void command(Dc4Blur b,String op,int... a)throws IOException{b.command(null,op,a);}
    public static int run()throws Exception{
        checks=0;Dc4Blur b=new Dc4Blur();check(!b.active()&&b.pending()==0,"Fresh render objects use ordinary shader");
        command(b,"ph.scene.blur",30,0,0,0);check(b.active()&&b.textureStep()==1f/300,"Native30 stores reciprocal texture-coordinate step");
        command(b,"ph.scene.blur",101,0,0,0);check(b.textureStep()==1f/300,"Native out-of-range B1 leaves state unchanged");
        command(b,"ph.scene.blur_to",30,100,120,0);check(b.pending()==1&&b.textureStep()==1f/300,"Queueing B2 does not execute its handler early");
        b.elapse(17);check(b.textureStep()==1f/300,"First animation update binds the starting reciprocal");
        b.elapse(1000);float middle=b.textureStep();
        check(Math.abs(middle-(1f/300+1f/1000)/2)<0.000001,"Native tween interpolates reciprocals rather than script strengths");
        ByteArrayOutputStream frozen=new ByteArrayOutputStream();b.save(new DataOutputStream(frozen));Dc4Blur restored=new Dc4Blur();restored.load(new DataInputStream(new ByteArrayInputStream(frozen.toByteArray())));
        check(restored.textureStep()==middle&&restored.pending()==1,"Cold snapshot retains floating sampling step and pending native animation");
        b.elapse(1000);restored.elapse(1000);check(!b.active()&&!restored.active()&&b.pending()==0&&restored.pending()==0,"Target100 restores shader0 and retires the tween after restore");
        command(restored,"ph.scene.blur",50,0,0,0);command(restored,"ph.scene.blur_to",50,70,30,0);command(restored,"ph.scene.blur_to",70,100,30,0);restored.elapse(1200);
        check(!restored.active()&&restored.pending()==0,"Both consecutive native30-tick fade-out jobs run in their original order");
        command(restored,"ph.scene.blur",100,0,0,0);check(!restored.active(),"B1 strength100 invokes native ordinary shader path");
        command(restored,"ph.scene.blur",30,0,0,0);command(restored,"ph.scene.blend",0,0,0,0);check(!restored.active(),"B3 ordinary blend resets Gaussian pass");
        try{command(restored,"ph.scene.blur_to",0,100,30,0);throw new AssertionError("Unreviewed infinity accepted");}catch(IOException expected){check(true,"Unobserved zero reciprocal endpoints remain explicit");}
        int[] impulse=new int[9];impulse[4]=0xffffffff;int[] filtered=Dc4Blur.applyArgb(impulse,3,3,1f/3);
        check(filtered[4]==0x33333333,"Shader center carries weight0.2 in all four channels");
        check(filtered[0]==0x1a1a1a1a&&filtered[1]==0x1a1a1a1a&&filtered[8]==0x1a1a1a1a,"All eight neighbor samples carry weight0.1, including alpha");
        int[] solid=new int[12];Arrays.fill(solid,0x7f1234ab);check(Arrays.equals(solid,Dc4Blur.applyArgb(solid,4,3,0.2f)),"Clamp sampling preserves a constant ARGB texture even at all borders");
        check(Dc4Blur.applyArgb(solid,4,3,0)==solid,"Ordinary shader keeps original source without a copy");
        check(impulse[4]==0xffffffff&&impulse[0]==0,"Blur does not modify archived source textures");
        byte[] invalid=frozen.toByteArray();invalid[5]=0x7f;invalid[6]=(byte)0x80;invalid[7]=invalid[8]=0;
        try{restored.load(new DataInputStream(new ByteArrayInputStream(invalid)));throw new AssertionError("Invalid float accepted");}catch(IOException expected){check(true,"Corrupt non-finite snapshot state is rejected");}
        restored.reset();check(!restored.active()&&restored.pending()==0,"Native scene reset clears pass and queued animation");
        return checks;
    }
    public static void main(String[] args)throws Exception{System.out.println("Dc4BlurTests "+run()+" PASS");}
}
