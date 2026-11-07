package org.circus.core;

import java.io.*;
import java.util.*;

/** Independent FL 408210/409210 vectors: signed gates, ordered draws and native table sizes. */
public final class FlCreditsTests {
    private static int checks;
    private static void check(boolean ok,String why){checks++;if(!ok)throw new AssertionError(why);}
    private static byte[] bytes(FlCredits c)throws IOException{ByteArrayOutputStream b=new ByteArrayOutputStream();c.save(new DataOutputStream(b));return b.toByteArray();}
    private static FlCredits copy(FlCredits c)throws IOException{FlCredits d=new FlCredits();d.load(new DataInputStream(new ByteArrayInputStream(bytes(c))));return d;}
    private static void layer(FlCredits c,int n,String image,int y,int weight,boolean raw){FlCredits.Layer l=c.frame().layers.get(n);check(l.image.equals(image)&&l.x==0&&l.y==y&&l.weight16==weight&&l.rawClip==raw,"native draw "+image+" "+y+" weight"+weight);}
    private static int feedbackFixture(FlCredits c,int initial){int pixel=initial;for(FlCredits.Frame f:c.draws())for(FlCredits.Layer l:f.layers){int source=l.image.equals("bg/white")?0xffffff:0x0000ff,next=0;for(int k=0;k<3;k++)next|=((((pixel>>>(8*k))&255)*(16-l.weight16)+((source>>>(8*k))&255)*l.weight16)>>4)<<(8*k);pixel=next;}return pixel;}
    public static int run()throws Exception {
        checks=0;FlCredits c=new FlCredits();c.start(0,0);
        check(c.active&&c.visible&&c.frame().capture,"init has no draw and retains screen");
        check(Float.floatToRawIntBits(FlCredits.SPEED)==0x3d6a7040,"FL independent GED float32 rate");
        c.elapse(3000);check(!c.leadDone&&c.frame().capture,"exact3000ms lead remains");
        c.elapse(1);check(c.leadDone&&c.frame().capture,"crossing lead resets base without drawing");
        c.elapse(1000);layer(c,0,"ed/ged",-57,16,true);
        c.elapse(200000);check(!c.active&&c.visible&&c.takeCompletion()==53,"natural GED completion flag100:53");layer(c,0,"ed/ged",-10788,16,true);check(c.takeCompletion()==0,"completion flag consumed once");
        check(c.draws().size()==2&&c.draws().get(0).layers.get(0).y==-57&&!c.frame().black,"GED final offscreen call retains the preceding front journal, with no implicit black clear");
        byte[] completed=bytes(c);c.advance(30000);check(Arrays.equals(completed,bytes(c)),"released ending does not advance its clock");
        c.start(1,1);check(c.images().size()==20&&c.image(2).equals("ed/aft/ed_af_akn01")&&c.image(18).equals("ed/aft/ed_af_akn17")&&c.image(19).equals("ed/logo")&&c.image(20).isEmpty(),"AFT original17 slides and unloaded sentinel");
        c.elapse(3000);layer(c,0,"bg/white",0,16,false);check(!c.fading,"white strict3000ms gate");
        c.elapse(1);check(c.fading&&c.alpha==0,"fade arms after3000");c.elapse(70);check(c.alpha==0,"alpha strict70ms gate");
        c.elapse(1);check(c.alpha==1,"eligible alpha advances once");layer(c,0,"bg/white",0,16,false);layer(c,1,"ed/aft",0,0,false);
        c.elapse(100000);check(c.alpha==2&&c.stage==0,"one delayed poll never catches up16 alpha steps");
        c.start(1,1);long generation=c.generation;c.elapse(3001);c.elapse(80);c.elapse(80);c.elapse(20);
        check(c.draws().size()==4&&feedbackFixture(c,0x123456)==0xddddff,"native ordered feedback accumulates across polls:221 rather than a per-frame black rebuild");
        FlCredits feedback=copy(c);check(Arrays.equals(bytes(c),bytes(feedback))&&feedbackFixture(feedback,0x123456)==0xddddff,"snapshot preserves the complete physical-front draw journal");
        c.elapse(0);feedback.elapse(0);check(Arrays.equals(bytes(c),bytes(feedback))&&feedbackFixture(c,0x123456)==0xdadaff,"another same-alpha native poll feeds back onto the preceding front");
        c.start(1,1);check(c.generation==generation+1&&c.draws().isEmpty()&&c.frame().capture,"new owner generation resets only its journal and uses a new captured front");
        c.start(1,7);check(c.images().size()==12&&c.image(2).equals("ed/drm/ed_dm_akn01")&&c.image(10).equals("ed/drm/ed_dm_akn09")&&c.image(11).equals("bg/white"),"DRM independent9 slides and finalwhite");
        c.start(1,11);check(c.images().size()==23&&c.image(2).equals("ed/etc/ed_au01")&&c.image(21).equals("ed/etc/ed_au20"),"ULT table20 slides");
        c.start(1,12);check(c.image(2).equals("ed/koi/ed_ku01"),"KOI independentroute");
        c.start(1,13);check(c.image(2).equals("ed/ask/ed_su01")&&c.image(21).equals("ed/ask/ed_su20"),"ASK native table13 at471B58 has independent20 slides");
        for(int route:new int[]{14,15}){c.start(1,route);check(c.image(2).equals("ed/etc/ed_au01")&&c.slideCount()==20,"native bonus route"+route+" selects its own ETC table entry");}
        for(int mode=0;mode<=2;mode++)for(int flag=0;flag<=2;flag++){
            c.start(mode,1);check(!c.input(1,flag,flag)&&c.active,"leftmouse cannot skip");check(!c.input(13,flag,flag)&&c.active,"keyboard cannot skip");
            boolean skip=c.input(2,flag,flag);check(skip==(mode<=1&&flag==1)&&c.active==!skip,"rightskip requires mode0/1 and exactpersisted1");check(c.takeCompletion()==0,"skip never creates persistent flag");
        }
        for(int route=1;route<=FlCredits.ROUTES;route++){
            c.start(1,route);long total=0;int restoredAt=0;
            while(c.active&&total<200000){c.advance(20);total+=20;if(total==5040){FlCredits saved=copy(c);for(int ms:new int[]{0,19,1,80,9000}){saved.advance(ms);FlCredits same=copy(c);same.advance(ms);check(Arrays.equals(bytes(saved),bytes(same)),"roundtrip reproduces draw/clock phase route"+route);saved=copy(c);}restoredAt++;}}
            check(restoredAt==1&&!c.active&&c.takeCompletion()==52,"route finishes from native timers "+route);
            layer(c,0,route>=7&&route<=10?"bg/white":"",0,16,false);
        }
        c.start(1,1);c.advance(0);c.advance(19);check(c.elapsed==19&&!c.fading,"sub20ms phase held");FlCredits phase=copy(c);c.advance(1);phase.advance(1);check(Arrays.equals(bytes(c),bytes(phase)),"19ms savedphase fires same nextpoll");
        byte[] before=bytes(c);boolean bad=false;try{c.load(new DataInputStream(new ByteArrayInputStream(Arrays.copyOf(before,before.length-1))));}catch(IOException expected){bad=true;}check(bad&&Arrays.equals(before,bytes(c)),"truncatedstate does not publish partialowner");
        for(int route:new int[]{0,16,-1}){bad=false;try{c.start(1,route);}catch(IOException expected){bad=true;}check(bad,"unreviewed route refuses "+route);}
        c.start(0,0);c.elapse(0x80000000L);check(!c.leadDone,"native signed time comparison does not become unsigned at2^31");
        return checks;
    }
    public static void main(String[] args)throws Exception{System.out.println("FlCreditsTests "+run()+" PASS");}
}
