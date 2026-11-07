package org.circus.core;
import java.io.*;
import java.util.*;
/** Original PP40EF70/40F050 controller projections. Controlled native
 *render/archive/OS calls do not establish physical renderer equivalence. */
public final class PpScrollCreditsTests {
 private static int checks;
 private static void check(boolean yes,String why){checks++;if(!yes)throw new AssertionError(why);}
 private interface Action{void run()throws IOException;}
 private static void reject(Action action,String why)throws IOException{boolean failed=false;try{action.run();}catch(IOException expected){failed=true;}check(failed,why);}
 private static byte[] save(PpScrollCredits c)throws IOException{ByteArrayOutputStream b=new ByteArrayOutputStream();c.save(new DataOutputStream(b));return b.toByteArray();}
 private static final class Case{final String id;final int count,start;final int[][] samples;Case(String id,int count,int start,int[][] samples){this.id=id;this.count=count;this.start=start;this.samples=samples;}}
 private static final Case[] CASES={
 new Case("initial19",19,0,new int[][]{{0,41,0,-1,0,0,0,0},{1000,41,0,-1,0,41,0,0},{14634,41,0,-1,0,599,0,0},{14635,41,1,0,1,0,0,0},{29269,41,2,1,2,0,0,0}}),
 new Case("wrap",19,-256,new int[][]{{-256,41,0,-1,0,0,0,0},{744,41,0,-1,0,41,0,0},{14379,41,1,0,1,0,0,0}}),
 new Case("overflow_product",19,0,new int[][]{{0,41,0,-1,0,0,0,0},{2147483647,41,3579,0,-1,0,1,0},{-2147483648,41,3579,0,-1,-1,1,1}}),
 new Case("max_tick",19,0,new int[][]{{-1,41,7158,-1,-1,0,1,0}}),
 new Case("wrapped_speed",4000,0,new int[][]{{0,-6915,0,-1,0,0,0,0},{1000,-6915,7146,0,-1,0,1,0}}),
 new Case("one",1,17,new int[][]{{17,2,0,-1,0,0,0,0},{300016,2,0,-1,0,599,0,0},{300017,2,1,0,-1,0,1,0},{300018,2,1,0,-1,-1,1,1}}),
 new Case("jump",19,0,new int[][]{{0,41,0,-1,0,0,0,0},{600000,41,41,0,-1,0,1,0},{600001,41,41,0,-1,-1,1,1}}),
 new Case("sequential",19,0,new int[][]{{0,41,0,-1,0,0,0,0},{14635,41,1,0,1,0,0,0},{29269,41,2,1,2,0,0,0},{43903,41,3,2,3,0,0,0},{58537,41,4,3,4,0,0,0},{73171,41,5,4,5,0,0,0},{87805,41,6,5,6,0,0,0},{102440,41,7,6,7,0,0,0},{117074,41,8,7,8,0,0,0},{131708,41,9,8,9,0,0,0},{146342,41,10,9,10,0,0,0},{160976,41,11,10,11,0,0,0},{175610,41,12,11,12,0,0,0},{190244,41,13,12,13,0,0,0},{204879,41,14,13,14,0,0,0},{219513,41,15,14,15,0,0,0},{234147,41,16,15,16,0,0,0},{248781,41,17,16,17,0,0,0},{263415,41,18,17,18,0,0,0},{278049,41,19,18,-1,0,1,0},{278050,41,19,18,-1,-1,1,1}})
 };
 public static int run()throws Exception{
  checks=0;
  for(Case row:CASES){
   PpScrollCredits c=new PpScrollCredits();c.start("ED/GED.PCK",row.count,row.start);
   check(c.active()&&!c.finishing()&&c.requestedPage()==-1&&c.currentPage()==-1&&c.nextPage()==-1,"native init projection "+row.id);
   for(int n=0;n<row.samples.length;n++){
    int[] s=row.samples[n];PpScrollCredits.Step step=c.update(s[0]);
    check(c.speed()==s[1]&&c.requestedPage()==s[2]&&c.currentPage()==s[3]&&c.nextPage()==s[4]&&c.finishing()==(s[6]!=0),"native wrapped arithmetic/successive cache "+row.id+"/"+n);
    check(step==(s[7]!=0?PpScrollCredits.Step.COMPLETE:PpScrollCredits.Step.DRAW)&&c.active()==(s[7]==0),"native EOF does not finish until next update "+row.id+"/"+n);
    if(s[5]>=0)check(c.offset()==s[5],"native quotient/remainder source rectangle "+row.id+"/"+n);
    Set<String> refs=c.resources();check(refs.size()<=2,"at most two live page references "+row.id);
    if(c.active()&&c.currentPage()>=0)check(refs.contains("pck:ED/GED.PCK#"+c.currentPage()),"actual current resource "+row.id);
    if(c.active()&&c.nextPage()>=0)check(refs.contains("pck:ED/GED.PCK#"+c.nextPage()),"actual next resource "+row.id);
    byte[] saved=save(c);PpScrollCredits restored=new PpScrollCredits();restored.load(new DataInputStream(new ByteArrayInputStream(saved)));check(Arrays.equals(saved,save(restored)),"full portable state roundtrip "+row.id+"/"+n);
   }
  }
  PpScrollCredits tick=new PpScrollCredits();tick.start("ED\\GED.PCK",19,0);tick.elapse(1000);check(tick.offset()==41&&tick.nextPage()==0,"portable delta uses same integer tick arithmetic");
  check(tick.skip()&&!tick.active()&&!tick.skip(),"explicit skip closes only one active caller");check(tick.update(2000)==PpScrollCredits.Step.INACTIVE&&tick.resources().isEmpty(),"inactive caller never issues another native poll");
  reject(()->tick.start("ED/GED.PCK",0,0),"empty archive admission does not invent valid pages");reject(()->tick.start("../GED.PCK",19,0),"resource path remains contained");reject(()->tick.image(19),"invalid page never becomes a resource");
  PpScrollCredits idle=new PpScrollCredits();byte[] zero=save(idle);idle.load(new DataInputStream(new ByteArrayInputStream(zero)));check(Arrays.equals(zero,save(idle)),"unstarted owner roundtrip");tick.reset();check(Arrays.equals(zero,save(tick)),"old host migration resets complete owner");check(!tick.active()&&tick.resources().isEmpty(),"reset releases all portable resources");
  PpScrollCredits valid=new PpScrollCredits();valid.start("ED/GED.PCK",19,0);valid.update(14635);byte[] original=save(valid),damaged=original.clone();int utf=2+"ED/GED.PCK".length();int speedOffset=4+utf+4;damaged[speedOffset+3]^=1;
  reject(()->valid.load(new DataInputStream(new ByteArrayInputStream(damaged))),"invalid saved arithmetic rejects");check(Arrays.equals(original,save(valid)),"failed restore publishes no partial owner state");
  return checks;
 }
 public static void main(String[]args)throws Exception{System.out.println("PpScrollCreditsTests PASS "+run());}
}
