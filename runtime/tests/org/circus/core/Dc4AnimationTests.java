package org.circus.core;

import java.io.*;
import java.nio.file.*;

/** Real95 mode4 framing and cold scheduler restore, without source EXE execution. */
public final class Dc4AnimationTests {
    private static int checks;
    private static void check(boolean ok,String why){checks++;if(!ok)throw new AssertionError(why);}
    private static Dc4Animation copy(Dc4Animation a)throws IOException{ByteArrayOutputStream out=new ByteArrayOutputStream();a.save(new DataOutputStream(out));Dc4Animation b=new Dc4Animation();b.load(new DataInputStream(new ByteArrayInputStream(out.toByteArray())));return b;}
    public static int run()throws Exception{
        checks=0;Dc4Animation a=new Dc4Animation();check(!copy(a).active(),"Empty animation state round trips");
        a.start("EFF/KTR.PCK",340,0,0,2);check(a.active()&&a.frame()==0&&a.resource().equals("pck:EFF/KTR.PCK#0"),"95 installs the first PCK frame immediately");
        check(a.remainingMillis()==17034,"340-frame intro uses strictly-greater threshold plus native manager initialization");
        a.elapse(83);check(a.frame()==0,"Initial threshold2 frame remains visible through the first four render updates");
        a.elapse(1);check(a.frame()==1,"Fifth render update selects the second frame");
        Dc4Animation restored=copy(a);check(restored.frame()==1&&restored.remainingMillis()==a.remainingMillis(),"Cold snapshot keeps the current frame and sub-frame clock");
        a.elapse(16949);restored.elapse(16949);check(a.active()&&a.frame()==339&&restored.frame()==339,"Final frame is displayed before its final strict-threshold tick");
        a.elapse(1);restored.elapse(1);check(!a.active()&&!restored.active()&&a.resource().isEmpty()&&a.remainingMillis()==0,"Last advancement unloads the texture instead of holding or looping it");
        a.start("EFF/EF007.PCK",120,50,0,1);check(a.x()==50&&a.y()==0&&a.remainingMillis()==4034,"Position and threshold1 remain independent of the fixed native66 target");
        a.start("EFF/EF001.PCK",60,50,100,0);check(a.y()==100&&a.remainingMillis()==1034,"Zero threshold means one subsequent frame per render update, not zero duration");
        Dc4Animation split=copy(a);a.elapse(900);for(int n=0;n<900;n++)split.elapse(1);check(a.frame()==split.frame()&&a.remainingMillis()==split.remainingMillis(),"Chunked elapsed time and single-millisecond updates choose identical frames");
        a.elapse(Long.MAX_VALUE);check(!a.active(),"Bounded arithmetic can finish a sequence without iterating excess wall time");
        a.reset();check(!a.active()&&a.frameCount()==0&&a.archive().isEmpty(),"Native background clear releases animation ownership");
        try{a.start("bad.PCK",2,0,0,-1);throw new AssertionError("Invalid frame timing accepted");}catch(IOException expected){check(true,"Negative native threshold is rejected");}
        Dc4Host host=new Dc4Host(null,TextCodec.identity(),true);check(a.command(host,"ph.scene.motion",new int[]{0,0,0,4}),"Missing native PCK handle leaves95 mode4 inert");
        try{a.command(host,"ph.scene.motion",new int[]{0,0,0,6});throw new AssertionError("Unused mode accepted");}catch(IOException expected){check(true,"Unused95 path remains explicit");}
        return checks;
    }
    public static void main(String[] args)throws Exception{
        run();
        if(args.length>0){GameResources files=new GameResources(new LocalGameStorage(Paths.get(args[0])),"@none");
            String[] names={"EF001","EF002","EF003","EF004","EF005","EF006","EF007","EF008","EF009","EF013","KTR"};
            int[] counts={60,90,120,120,120,120,120,120,120,30,340};
            for(int i=0;i<names.length;i++){Dc4Animation a=new Dc4Animation();a.start(files,"EFF/"+names[i]+".PCK",0,0,i==10?2:0);check(a.frameCount()==counts[i],"Actual archive page count "+names[i]);}
        }
        System.out.println("Dc4AnimationTests "+checks+" PASS");
    }
}
