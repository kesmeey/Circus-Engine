package org.circus.core;

import java.io.*;
import java.util.*;

/** Native table vectors and an independent persistent-front pixel oracle.
 * These generated surfaces contain no original game assets. */
public final class DcdxBannerTests {
    private static int checks;
    private static final int W=1024,H=576,ATLAS_W=472,ATLAS_H=640;
    private static final int[] END_X={0,13,27,56,91},TOP={232,296,360,424,488},SECOND={576,320,384,448,512};
    private static void check(boolean yes,String message){checks++;if(!yes)throw new AssertionError(message);}
    private static byte[] bytes(DcdxBanner b)throws IOException{ByteArrayOutputStream out=new ByteArrayOutputStream();b.save(new DataOutputStream(out));return out.toByteArray();}
    private static DcdxBanner copy(DcdxBanner b)throws IOException{DcdxBanner out=new DcdxBanner();out.load(new DataInputStream(new ByteArrayInputStream(bytes(b))));return out;}
    private static int[] pixels(int seed){int[] out=new int[W*H];for(int y=0;y<H;y++)for(int x=0;x<W;x++)out[y*W+x]=0xff000000|((x*7+y*3+seed)&255)<<16|((y*5+seed)&255)<<8|((x+y*11+seed)&255);return out;}
    private static int[] atlas(){int[] out=new int[ATLAS_W*ATLAS_H];for(int y=0;y<ATLAS_H;y++)for(int x=0;x<ATLAS_W;x++)out[y*ATLAS_W+x]=x%11==0?0xffff00ff:0xff000000|((y*13+17)&255)<<16|((x*3+53)&255)<<8|((x+y*7+29)&255);return out;}
    /** Direct40A300 oracle: restore old clip, step24, then key only exact magenta. */
    private static boolean nativePoll(int mode,int[] positions,int[] front,int[] bg,int[] sprites){
        int done=0;
        for(int item=0;item<5;item++){
            int left=positions[item],top=TOP[item],right=left+472;
            for(int y=top;y<top+64;y++)for(int x=Math.max(0,left);x<Math.min(W,right);x++)front[y*W+x]=bg[y*W+x];
            int target=(mode==0||mode==2)?END_X[item]:-472;
            if(left==target)done++;
            if(left<target)positions[item]=Math.min(target,left+24);else if(left>target)positions[item]=Math.max(target,left-24);
            int sourceY=mode<2?item*64:SECOND[item];left=positions[item];
            for(int y=top;y<top+64;y++)for(int x=Math.max(0,left);x<Math.min(W,left+472);x++){
                int pixel=sprites[(sourceY+y-top)*ATLAS_W+x-left];if(pixel!=0xffff00ff)front[y*W+x]=pixel;
            }
        }
        return done==5;
    }
    /** Consume the public journal as a platform renderer would, independently
     * from the direct native control-flow oracle above. */
    private static int replay(DcdxBanner banner,int consumed,int[] front,int[] bg,int[] sprites){
        List<DcdxBanner.Layer> layers=banner.rawLayers();
        for(int n=consumed;n<layers.size();n++){
            DcdxBanner.Layer layer=layers.get(n);check(layer.sw==layer.dw&&layer.sh==layer.dh&&layer.sw==472&&layer.sh==64,"Native banner never scales a sprite");
            for(int dy=0;dy<layer.dh;dy++)for(int dx=0;dx<layer.dw;dx++){
                int x=layer.x+dx,y=layer.y+dy;if(x<0||x>=W||y<0||y>=H)continue;
                int pixel=layer.kind==DcdxBanner.RESTORE_BG?bg[(layer.sy+dy)*W+layer.sx+dx]:sprites[(layer.sy+dy)*ATLAS_W+layer.sx+dx];
                if(!layer.keyed||pixel!=0xffff00ff)front[y*W+x]=pixel;
            }
        }
        return layers.size();
    }
    public static int run()throws Exception{
        checks=0;int[] old=pixels(19),bg=pixels(83),sprites=atlas();UiCapture initial=UiCapture.pixels(W,H,old),background=UiCapture.pixels(W,H,bg);
        DcdxBanner banner=new DcdxBanner();banner.start(0,initial,background,"system/banner",17);
        check(banner.active&&banner.visible&&!banner.drawn&&banner.polls==0&&banner.rawLayers().isEmpty()&&banner.sourceSerial==17,"Start retains the initial front until the first native timer");
        check(!banner.advance(0)&&!banner.advance(32)&&banner.itemX(0)==-472,"First30Hz deadline is33ms, not an immediate poll");
        DcdxBanner saved=copy(banner);check(banner.advance(1)&&saved.advance(1)&&Arrays.equals(bytes(banner),bytes(saved)),"32ms snapshot preserves the exact first rational deadline");
        check(banner.itemX(0)==-448&&banner.itemX(4)==-448&&banner.rawLayers().size()==10,"One native poll moves all five sprites24px and journals paired rectangles");
        DcdxBanner.Layer restore=banner.rawLayers().get(0),draw=banner.rawLayers().get(1);
        check(restore.kind==DcdxBanner.RESTORE_BG&&!restore.keyed&&restore.x==-472&&restore.sx==-472&&restore.y==232,"Background repair uses the old negative-X clip at matching source coordinates");
        check(draw.kind==DcdxBanner.SPRITE&&draw.keyed&&draw.x==-448&&draw.sx==0&&draw.sy==0&&draw.resource.equals("system/banner"),"Sprite source and destination are separate after the24px move");
        banner.advance(10000);check(banner.polls==2&&banner.itemX(0)==-424,"A late event coalesces missed30Hz deadlines into one poll");
        check(!banner.advance(0),"Repeated same-time refresh cannot advance again");
        for(int mode=0;mode<4;mode++){
            banner=new DcdxBanner();banner.start(mode,initial,background,"fixture/atlas",91+mode);long generation=banner.generation;
            int[] want=new int[5];for(int n=0;n<5;n++)want[n]=(mode&1)==0?-472:END_X[n];int[] actual=old.clone(),oracle=old.clone();int consumed=0;
            int elapsed=0;
            for(int poll=1;poll<=25;poll++){
                int deadline=poll*1000/30;check(banner.advance(deadline-elapsed),"Every rational floor(k*1000/30) deadline is due");elapsed=deadline;
                boolean ended=nativePoll(mode,want,oracle,bg,sprites);
                for(int n=0;n<5;n++)check(banner.itemX(n)==want[n],"All original per-row targets are honored in stage"+mode);
                consumed=replay(banner,consumed,actual,bg,sprites);check(Arrays.equals(actual,oracle),"Independent persistent-front oracle matches every pixel, stage"+mode+" poll"+poll);
                check(banner.active==!ended&&banner.generation==generation&&banner.polls==poll,"Activity and journal generation follow native poll order");
                check(banner.rawLayers().size()==poll*10,"Each ready poll repairs and draws every row, including stationary rows");
                if(poll==24)check(banner.active,"The last arriving sprite still holds the native wait until the next poll");
                if(poll==9){
                    saved=copy(banner);check(Arrays.equals(bytes(banner),bytes(saved)),"Mid-animation captures, phase and entire ordered journal roundtrip");
                    DcdxBanner future=copy(saved);banner.advance(0);check(Arrays.equals(bytes(banner),bytes(saved)),"A snapshot is independent from renderer refreshes");
                    saved.advance(33);future.advance(33);check(Arrays.equals(bytes(saved),bytes(future)),"Restored owner generates identical future dirty rectangles");
                }
            }
            check(!banner.active&&banner.visible&&banner.drawn&&banner.rawLayers().size()==250,"Following poll releases while retaining the final journal");
            byte[] finalState=bytes(banner);check(!banner.advance(Long.MAX_VALUE)&&Arrays.equals(finalState,bytes(banner)),"Released owner cannot append new frames or change its retained front");
            check(Arrays.equals(finalState,bytes(copy(banner))),"Completed animation snapshot remains loadable");
            check(actual[50*W+800]==old[50*W+800],"Pixels outside all native dirty rectangles keep the original front");
        }
        banner=new DcdxBanner();banner.syncClock(32,0);banner.start(2,initial,background,"fixture/second",38);check(banner.advance(1),"A shared menu timer phase survives owner handoff");
        check(banner.rawLayers().get(1).sy==576&&banner.rawLayers().get(3).sy==320&&banner.rawLayers().get(9).sy==512,"Second table starts at sourceY576 and then320/384/448/512");
        banner.advance(5);int phase=banner.clockMillis(),frame=banner.clockFrame();banner.start(3,initial,background,"fixture/second",39);
        check(banner.clockMillis()==phase&&banner.clockFrame()==frame&&banner.rawLayers().isEmpty()&&banner.sourceSerial==39,"Restart owns a new sourceSerial and fresh journal without resetting the native timer");
        byte[] stable=bytes(banner);boolean rejected=false;try{banner.start(4,initial,background,"fixture/second",40);}catch(IOException expected){rejected=true;}check(rejected&&Arrays.equals(stable,bytes(banner)),"Unreviewed stages fail without altering the existing owner");
        rejected=false;try{banner.syncClock(33,0);}catch(IOException expected){rejected=true;}check(rejected&&Arrays.equals(stable,bytes(banner)),"Inconsistent rational timer phase is rejected atomically");
        banner.advance(34);stable=bytes(banner);byte[] corrupt=stable.clone();corrupt[corrupt.length-5]^=1;
        rejected=false;try{banner.load(new DataInputStream(new ByteArrayInputStream(corrupt)));}catch(IOException expected){rejected=true;}check(rejected&&Arrays.equals(stable,bytes(banner)),"A bounded but nonnative journal coordinate is rejected without partial publication");
        rejected=false;try{banner.load(new DataInputStream(new ByteArrayInputStream(Arrays.copyOf(stable,stable.length-1))));}catch(IOException expected){rejected=true;}check(rejected&&Arrays.equals(stable,bytes(banner)),"Truncated journal preserves the preexisting owner and captures");
        banner.clear();check(!banner.active&&!banner.visible&&banner.initial==null&&banner.bg==null&&banner.rawLayers().isEmpty(),"Clear releases all frozen framebuffer inputs");
        check(Arrays.equals(bytes(banner),bytes(copy(banner))),"Cleared clock state roundtrips");
        return checks;
    }
    public static void main(String[] args)throws Exception{System.out.println("DcdxBannerTests "+run()+" PASS");}
}
