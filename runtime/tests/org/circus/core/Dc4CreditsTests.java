package org.circus.core;

import java.io.*;
import java.nio.file.*;
import java.util.*;

/** PH x87 speed/truncation boundaries and detached controller snapshot checks. */
public final class Dc4CreditsTests {
    private static int checks;
    private static void check(boolean ok,String why){checks++;if(!ok)throw new AssertionError(why);}
    private static byte[] save(Dc4Credits c)throws IOException{ByteArrayOutputStream b=new ByteArrayOutputStream();c.save(new DataOutputStream(b));return b.toByteArray();}
    private interface Call {void run()throws IOException;}
    private static void reject(Call call,String why)throws IOException{try{call.run();throw new AssertionError(why);}catch(IOException expected){checks++;}}
    public static int run()throws Exception {
        checks=0;float speed=Dc4Credits.nativeSpeed(22,125000);
        check(Dc4Credits.nativeDistance(125000,speed)==15839,"PH retains stored float rate and x87 product before integer truncation");
        check((int)(125000*speed)==15840,"Counterfactual Java float multiplication crosses the native boundary");
        check(Dc4Credits.nativeDistance(5681,speed)==719&&Dc4Credits.nativeDistance(5682,speed)==720,"First720-row page boundary follows native float speed");
        check(Dc4Credits.nativeDistance(119318,speed)==15119&&Dc4Credits.nativeDistance(119319,speed)==15120,"Last-page threshold is retained before the hold phase");
        check(Dc4Credits.nativeFade(200)<1&&Dc4Credits.nativeFade(201)==1&&Dc4Credits.nativeFadeUpdates()==201,"Double0.005 plus float store takes201 updates to reach native clamp");
        Dc4Credits c=new Dc4Credits();byte[] idle=save(c);Dc4Credits idleCopy=new Dc4Credits();idleCopy.load(new DataInputStream(new ByteArrayInputStream(idle)));check(Arrays.equals(idle,save(idleCopy)),"Unstarted credits snapshot roundtrips");
        c.start(22,125,0);check(c.active()&&c.currentPage()==0&&c.nextPage()==1&&c.offset()==0,"PH starts with pages0/1 and1280x720 output");
        check(c.completionMillis()==127670&&c.lastPageMillis()==119319&&c.completionFlag()==111,"Native page threshold and declared60Hz tail produce completion time/flag");
        c.elapse(5681);check(c.currentPage()==0&&c.nextPage()==1&&c.offset()==719,"Page ends at row719 without cropping the721st storage row");
        c.elapse(1);check(c.currentPage()==1&&c.nextPage()==2&&c.offset()==0,"Adjacent page begins at720 with next resource ready");
        c.elapse(119318-c.elapsedMillis());check(!c.holding()&&c.currentPage()==20&&c.offset()==719,"Penultimate page before hold threshold");
        c.elapse(1);check(c.holding()&&c.currentPage()==21&&c.nextPage()==-1&&c.offset()==0,"Last resource holds at row0");
        c.elapse(5000);check(c.holding()&&c.fadeAlpha()==0,"Native hold comparison is strict greater-than5000ms");
        c.elapse(1);check(!c.holding()&&c.fadeAlpha()==0,"Entering fade state does not consume an alpha update");
        c.elapse(3334);check(c.active()&&c.fadeAlpha()<1,"Two hundred60Hz updates are still below the native alpha clamp");
        byte[] frozen=save(c);Dc4Credits restored=new Dc4Credits();restored.load(new DataInputStream(new ByteArrayInputStream(frozen)));check(Arrays.equals(frozen,save(restored)),"Credits snapshot preserves future final-alpha boundary");
        check(restored.elapse(15)==Dc4Credits.Step.DRAW&&restored.active(),"Snapshot does not complete one millisecond early");
        check(restored.elapse(1)==Dc4Credits.Step.COMPLETE&&!restored.active()&&restored.fadeAlpha()==1,"Final update yields exactly one completion");
        check(restored.elapse(9999)==Dc4Credits.Step.INACTIVE&&restored.resources().isEmpty(),"Completed owner cannot deliver repeated completion or retain pages");
        Dc4Credits one=new Dc4Credits(),chunks=new Dc4Credits();one.start(22,125,1);chunks.start(22,125,1);one.elapse(125000);for(int i=0;i<125;i++)chunks.elapse(1000);check(Arrays.equals(save(one),save(chunks))&&one.completionFlag()==110,"Chunked time agrees with single jump and mode1 selects its own completion flag");
        byte[] bad=frozen.clone();bad[19]^=1;reject(()->c.load(new DataInputStream(new ByteArrayInputStream(bad))),"Corrupt saved float rate rejects");check(Arrays.equals(frozen,save(c)),"Rejected restore publishes no partial credit state");
        reject(()->c.start(0,125,0),"No fabricated pages for empty archive");reject(()->c.start(22,0,0),"Zero duration division rejects");reject(()->c.image(22),"Out-of-range page cannot become resource reference");
        return checks;
    }
    public static int runGame(Path game)throws Exception {
        int count=run();GameResources files=new GameResources(new LocalGameStorage(game),"@none");Dc4Credits c=new Dc4Credits();c.start(files,125,0);
        check(c.strips()==22&&c.scrollDurationMillis()==125000,"Actual ED.PCK has22 pages admitted through bounded native headers");
        Archive a=files.archive("AdvData/GRP/ED/ED.PCK");for(int i=0;i<a.entries.size();i++){Archive.Entry e=a.entries.get(i);byte[] header=a.source(e).read(0,20);check(e.name.equals(String.format(Locale.ROOT,"%03d.CRX",i+1))&&Binary.u16(header,8)==1280&&Binary.u16(header,10)==721,"Original page index/order and storage geometry "+i);}
        check(c.resources().equals(new LinkedHashSet<>(Arrays.asList("pck:ED/ed.PCK#0","pck:ED/ed.PCK#1"))),"Actual archive uses numeric PCK page references without basename guessing");
        return checks;
    }
    public static void main(String[] args)throws Exception{int n=args.length==0?run():runGame(Paths.get(args[0]));System.out.println("Dc4CreditsTests "+n+" PASS");}
}
