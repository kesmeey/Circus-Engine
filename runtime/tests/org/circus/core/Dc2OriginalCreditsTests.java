package org.circus.core;

import java.io.*;
import java.util.*;

/** Vectors from DC2 405890: draw-before-update, strict boundaries and uint32 clock. */
public final class Dc2OriginalCreditsTests {
    private static int checks;
    private static void check(boolean ok,String why){checks++;if(!ok)throw new AssertionError(why);}
    private static byte[] bytes(Dc2OriginalCredits c)throws IOException{ByteArrayOutputStream b=new ByteArrayOutputStream();c.save(new DataOutputStream(b));return b.toByteArray();}
    private static Dc2OriginalCredits copy(Dc2OriginalCredits c)throws IOException{Dc2OriginalCredits d=new Dc2OriginalCredits();d.load(new DataInputStream(new ByteArrayInputStream(bytes(c))));return d;}
    private static void strip(Dc2OriginalCredits.Strip s,String name,int y,int sy,int height){check(s.resource.equals(name)&&s.x==0&&s.srcX==0&&s.y==y&&s.srcY==sy&&s.width==800&&s.height==height&&s.opaque,"native clipped strip "+name+"/"+y+"/"+sy+"/"+height);}
    private static void reject(Dc2OriginalCredits c,byte[] bad)throws IOException{byte[] before=bytes(c);boolean no=false;try{c.load(new DataInputStream(new ByteArrayInputStream(bad)));}catch(IOException expected){no=true;}check(no&&Arrays.equals(before,bytes(c)),"invalid/truncated state rejects without publishing");}
    public static int run()throws Exception {
        checks=0;Dc2OriginalCredits c=new Dc2OriginalCredits();c.start(0);
        check(c.active&&!c.drawn&&c.frame().isEmpty(),"init loads and waits; it does not perform updater draw");
        check(Float.floatToRawIntBits(Dc2OriginalCredits.SPEED)==0x3d25953e,"native rate is initializer's rounded float32");
        c.elapse(14843);check(c.page==0&&c.row==600&&c.drawPage==0&&c.drawRow==0,"14843ms changes current row but draws old zero row");strip(c.frame().get(0),"ed/Gend_01",0,0,600);
        c.elapse(0);check(c.active&&c.page==0&&c.row==600,"row exactly600 does not advance");check(c.frame().size()==1,"edge row600 emits only next full page");strip(c.frame().get(0),"ed/Gend_02",0,0,600);
        c.elapse(24);check(c.page==0&&c.row==601&&c.drawRow==600,"crossing601 remains on old page until next poll");
        c.elapse(0);check(c.page==1&&c.row==0&&c.drawPage==0&&c.drawRow==601,"advance resets per-page clock, retains old draw");strip(c.frame().get(0),"ed/Gend_02",0,1,599);
        c.elapse(600);check(c.row==24&&c.drawPage==1&&c.drawRow==0,"next poll uses new page with no carried millisecond remainder");c.elapse(0);check(c.frame().size()==2,"a regular scroll composes two adjacent pages");strip(c.frame().get(0),"ed/Gend_02",0,24,576);strip(c.frame().get(1),"ed/Gend_03",576,0,24);
        c.start(1);check(c.image(0).equals("ed/Gend_skr_01")&&c.image(18).equals("ed/Gend_skr_19"),"Sakura has its independent19-resource table");
        for(int page=0;page<18;page++){c.elapse(600000);check(c.page==page&&c.row==24255,"large clock jump does not eagerly advance pages");c.elapse(0);check(c.page==page+1&&c.row==0,"one page at most per native poll");}
        c.elapse(9895);check(c.page==18&&c.row==400&&c.drawPage==18,"last page uses separate400-row hold");strip(c.frame().get(0),"ed/Gend_skr_19",0,0,600);
        c.elapse(0);check(c.active&&c.page==18,"last row exactly400 remains active");c.elapse(25);check(c.active&&c.row==401,"crossing401 still requires next poll");c.elapse(0);check(!c.active&&c.page==19&&c.drawPage==18&&c.drawRow==401,"natural completion retains the full final page");strip(c.frame().get(0),"ed/Gend_skr_19",0,0,600);
        byte[] done=bytes(c);c.elapse(100);check(Arrays.equals(done,bytes(c)),"released credits do not update on later host polls");
        for(int mode=0;mode<=1;mode++)for(int saved=0;saved<=3;saved++){
            c.start(mode);c.input(13,2);check(c.skipRequested()==0,"Enter is not a native mouse input");c.input(1,saved);check(c.active,"mouse event sets flag without returning from credits");
            Dc2OriginalCredits restored=copy(c);check(Arrays.equals(bytes(c),bytes(restored)),"pre-first-draw input survives owner save");c.elapse(0);check(c.active==!(saved==2||saved==1&&mode==0),"100:59 gated skip for variant "+mode+" value "+saved);
        }
        c.start(1);c.input(2,1);c.elapse(500);check(c.active&&c.skipRequested()==1,"right mouse first-clear flag cannot skip Sakura");c.input(2,0);check(c.skipRequested()==1,"later unrecognized persisted flag does not clear queued skip");c.input(2,2);c.elapse(0);check(!c.active,"second-clear flag2 skips Sakura on next poll");
        c.start(0);c.elapse(0xffffffffL);check(c.row==173626335,"native unsigned32 clock multiplication retains integer precision");c.elapse(1);check(c.page==1&&c.row==0,"wrapped time resets on the one-page advance");c.elapse(0xffffffffL);check(c.row==173626335,"relative wrapped subtraction is unsigned32");
        c.start(0);c.elapse(4240060065L);check(c.row==171406681,"x87 exact product must not round a value just below integer up as Java double does");
        c.start(0);c.elapse(1234);c.elapse(0);Dc2OriginalCredits d=copy(c);for(int ms:new int[]{0,16,600000,0,14600,300,0}){c.elapse(ms);d.elapse(ms);check(Arrays.equals(bytes(c),bytes(d)),"saved clock and old draw reproduce a future native poll");}
        byte[] valid=bytes(c);for(int n:new int[]{0,4,13,22,31,valid.length-1})reject(c,Arrays.copyOf(valid,n));byte[] bad=valid.clone();bad[9]=3;reject(c,bad);bad=valid.clone();bad[0]=0;reject(c,bad);
        boolean refused=false;try{c.start(2);}catch(IOException expected){refused=true;}check(refused,"unknown variant is not silently aliased to available strips");
        c.start(0);c.advance(0);c.advance(49);check(c.row==0&&c.drawRow==0,"sub50ms timer work does not run the native updater");Dc2OriginalCredits phase=copy(c);c.advance(1);phase.advance(1);check(c.row==2&&Arrays.equals(bytes(c),bytes(phase)),"saved49ms phase fires the next poll at50ms");c.input(1,1);c.advance(0);check(c.active,"zero-time host refresh cannot repeatedly poll or complete queued mouse skip");c.advance(49);check(c.active,"mouse skip waits for the remaining timer interval");c.advance(1);check(!c.active,"mouse skip completes at next50ms poll");
        c.start(0);c.advance(600000);check(c.page==0&&c.row==24255,"late timer coalesces to one old draw");c.advance(0);check(c.page==0,"late-event refresh cannot immediately fire a second updater");c.advance(50);check(c.page==1&&c.row==0,"next timer performs at most one page advance");byte[] legacy=Arrays.copyOf(bytes(c),bytes(c).length-5);legacy[3]=0x31;Dc2OriginalCredits old=new Dc2OriginalCredits();old.load(new DataInputStream(new ByteArrayInputStream(legacy)));old.advance(0);check(old.drawRow==24255,"D2C1 migration waits for next timer rather than replaying an old poll");old.advance(50);check(old.drawPage==1,"D2C1 migration resumes on50ms cadence");
        return checks;
    }
    public static void main(String[] args)throws Exception{System.out.println("Dc2OriginalCreditsTests "+run()+" PASS");}
}
