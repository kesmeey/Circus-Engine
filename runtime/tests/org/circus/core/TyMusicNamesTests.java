package org.circus.core;
import java.io.*;import java.util.*;
/** Self-authored table cells; no game names or PE bytes are embedded. */
public final class TyMusicNamesTests {
    private static int n;private static void check(boolean ok,String why){n++;if(!ok)throw new AssertionError(why);}private interface Task{void run()throws Exception;}private static void bad(Task t)throws Exception{try{t.run();throw new AssertionError("Expected bounded music-name rejection");}catch(IOException expected){n++;}}
    public static int run()throws Exception{
        n=0;byte[] table=new byte[TyMusicNames.COUNT*TyMusicNames.STRIDE];String fixture="試験曲";byte[] raw=fixture.getBytes(Binary.SJIS);System.arraycopy(raw,0,table,3*260,raw.length);TyMusicNames jp=TyMusicNames.table(table,"windows-31j");check(jp.text(3).equals(fixture),"Native CP932 cell uses its own encoding");check(jp.raw(3).length==raw.length,"Cell retains byte count for native font threshold");jp.raw(3)[0]=0;check(jp.raw(3)[0]==raw[0],"Returned title bytes cannot mutate the catalog");
        byte[] translated=table.clone();Arrays.fill(translated,3*260,4*260,(byte)0);byte[] cn="测试曲".getBytes("GBK");System.arraycopy(cn,0,translated,3*260,cn.length);TyMusicNames gbk=TyMusicNames.table(translated,"GBK");check(gbk.text(3).equals("测试曲"),"Translated PE cell uses GBK independently of the story codec");check(jp.text(47).isEmpty(),"Bounded empty cell is not read through next field");bad(()->jp.text(-1));bad(()->jp.text(48));bad(()->TyMusicNames.table(Arrays.copyOf(table,table.length-1),"windows-31j"));byte[] noNul=table.clone();Arrays.fill(noNul,0,260,(byte)'A');bad(()->TyMusicNames.table(noNul,"GBK"));byte[] invalid=table.clone();invalid[0]=(byte)0x81;bad(()->TyMusicNames.table(invalid,"windows-31j"));
        check(TyMusicNames.fontSize(18,1,0)==16&&TyMusicNames.fontSize(19,1,0)==14,"3:30 native18-byte threshold");check(TyMusicNames.fontSize(30,0,1)==16&&TyMusicNames.fontSize(30,0,0)==28,"3:31 selects16 else28");check(TyMusicNames.fontSize(19,1,1)==14,"3:30 takes precedence over3:31");return n;
    }
}
