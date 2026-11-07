package org.circus.core;
import java.io.*;
import java.util.*;

/** WY-owned numeric/resource rules. The final elapsed fade is an explicitly
 *approximate presentation clock, not a native per-update phase journal. */
public final class WyCreditsTests {
 private static int count;
 private static void check(boolean yes,String why){count++;if(!yes)throw new AssertionError(why);}
 private static byte[] save(Dc3Credits c)throws IOException{ByteArrayOutputStream out=new ByteArrayOutputStream();c.save(new DataOutputStream(out));return out.toByteArray();}
 public static int run()throws Exception{
  count=0;String[] tags={"RCC","SRR","HMN","SRA","AOI","SKI","MKT","SMM","HTN"};int[] counts={9,8,8,6,6,9,8,9,11};int[][] first={{-446,-55},{-200,0},{-200,0},{53,40},{53,80},{-200,40},{-240,0},{53,0},{-300,-80}};
  for(int n=0;n<9;n++){
   Dc3Credits c=new Dc3Credits("dc3wy-a09f");c.route=n;c.pictures=counts[n];c.strips=23;c.duration=n<=4?112000:97000;
   check(c.archive("01").equals("ED/ED"+tags[n]+"01.PCK")&&c.archive("02").equals("ED/ED"+tags[n]+"02.PCK"),"WY route numbering "+n);check(c.archive("").equals("ED/ED.PCK"),"WY shares actual lettering archive "+n);check(c.image("",2).equals("pck:ED/ED.PCK#2"),"shared native resource reference "+n);
   check(Float.floatToIntBits((float)c.period())==Float.floatToIntBits((float)(c.duration-(n<=4?6550:5800))/counts[n]),"WY native float period "+n);check(c.stripCropLeft()==20&&c.stripX(562)==459&&c.stripX(561)==460,"WY crop and signed integer half-width "+n);
   check(c.x()==first[n][0]&&c.y()==first[n][1],"WY own first camera row "+n);
   byte[] saved=save(c);Dc3Credits restored=new Dc3Credits("dc3wy-a09f");restored.load(new DataInputStream(new ByteArrayInputStream(saved)));check(Arrays.equals(saved,save(restored)),"existing wire restored with WY profile authority "+n);boolean rejected=false;try{new Dc3Credits().load(new DataInputStream(new ByteArrayInputStream(saved)));}catch(IOException e){rejected=true;}check(rejected,"active WY data cannot silently load as DD "+n);
   c.elapse(c.end()-3350);check(c.active()&&c.blackAlpha()==0,"WY compatibility fade starts after final page hold "+n);c.elapse(1675);check(c.active()&&c.blackAlpha()==.5f,"WY compatibility clock explicitly spans3350ms "+n);c.elapse(1675);check(!c.active()&&c.blackAlpha()==1,"WY compatibility clock returns after actual content "+n);
  }
  Dc3Credits dd=new Dc3Credits();dd.route=0;dd.pictures=10;dd.strips=23;dd.duration=262000;check(dd.archive("").equals("ED/EDRCC.PCK")&&dd.period()==24850,"DD resource and timing unchanged");check(dd.stripCropLeft()==0&&dd.stripX(562)==0,"DD strips keep old geometry");check(dd.end()==262000L*22/23+10000+834,"DD final elapsed fade unchanged");
  return count;
 }
 public static void main(String[] args)throws Exception{System.out.println("WyCreditsTests "+run()+" PASS");}
}
