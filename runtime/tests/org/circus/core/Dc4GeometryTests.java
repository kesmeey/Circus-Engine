package org.circus.core;

import java.io.IOException;

/** Native integer-division examples from PH's B4 fit contract. */
public final class Dc4GeometryTests {
    private static int checks;
    private static void check(boolean ok,String name){checks++;if(!ok)throw new AssertionError(name);}
    private static boolean near(double a,double b){return Math.abs(a-b)<0.0001;}
    public static int run()throws Exception{
        checks=0;
        Dc4Geometry.Fit cg=Dc4Geometry.fit(1920,1080);
        check(cg.xBasisPoints==6666&&cg.yBasisPoints==6666,"PH truncates1920x1080 scale to6666 basis points");
        check(near(cg.scaleX,.6666)&&near(cg.scaleY,.6666),"PH interprets the large scale pair in basis points");
        check(near(cg.left,.064)&&near(cg.top,.036),"PH centers the scaled bounds rather than the original rectangle");
        Dc4Geometry.Fit bg=Dc4Geometry.fit(1600,900);
        check(bg.xBasisPoints==8000&&bg.yBasisPoints==8000,"PH1600x900 uses8000 basis points");
        check(near(bg.left,0)&&near(bg.top,0),"PH ordinary background fills1280x720");
        Dc4Geometry.Fit ui=Dc4Geometry.fit(1280,720);
        check(ui.xBasisPoints==10000&&ui.yBasisPoints==10000,"PH screen-sized image retains unit scale");
        check(ui.scaleX==1&&ui.scaleY==1&&ui.left==0&&ui.top==0,"PH unit fit retains exact bounds");
        Dc4Geometry.Fit old=Dc4Geometry.fit(1024,600);
        check(old.xBasisPoints==12500&&old.yBasisPoints==12000,"PH legacy black surface has independent horizontal and vertical scale");
        check(near(old.scaleX,1.25)&&near(old.scaleY,1.2),"PH B4 is explicit stretch rather than aspect fitting");
        Dc4Geometry.Fit tall=Dc4Geometry.fit(1600,1800);
        check(tall.xBasisPoints==8000&&tall.yBasisPoints==4000,"PH tall image keeps independent integer divisions");
        check(near(tall.left,0)&&near(tall.top,0),"PH tall explicit fit centers output bounds");
        Dc4Geometry.Fit threshold=Dc4Geometry.fit(12800,7200);
        check(threshold.xBasisPoints==1000&&threshold.yBasisPoints==1000&&near(threshold.scaleX,.1),"PH includes1000 in the basis-point branch");
        Dc4Geometry.Fit ordinary=Dc4Geometry.fit(12801,7200);
        check(ordinary.xBasisPoints==999&&near(ordinary.scaleX,9.99)&&ordinary.scaleY==10,"PH both-axis threshold matches4432E0");
        for(int[] size:new int[][]{{0,720},{1280,0},{-1,720},{1280,-1}}){
            try{Dc4Geometry.fit(size[0],size[1]);throw new AssertionError("PH accepted invalid image dimensions");}
            catch(IOException expected){checks++;}
        }
        return checks;
    }
}
