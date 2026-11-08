package org.circus.core;

import java.io.*;
import java.util.*;

/** FL native STOP boundary; stale translated-file tails are data, not code. */
public final class FlStoppedTailTests {
    private static int checks;
    private interface Attempt{void run()throws Exception;}
    private static void check(boolean ok,String why){checks++;if(!ok)throw new AssertionError(why);}
    private static void reject(Attempt attempt,String why)throws Exception{boolean failed=false;try{attempt.run();}catch(IOException expected){failed=true;}check(failed,why);}
    private static byte[] file(byte[] code,byte[] tail,int entry)throws IOException{
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();DataOutputStream out=new DataOutputStream(bytes);out.write(new byte[]{1,0,0,0,(byte)entry,(byte)(entry>>>8),0,0,0x69,(byte)0x9c});out.write(code);out.write(tail);return bytes.toByteArray();
    }
    public static int run()throws Exception{
        checks=0;Profile profile=Profiles.get("dc2fl-9c69");byte[] stop={0,0,0};
        for(byte[] tail:new byte[][]{{0x6e,0x75,0,0,0,0},new byte[4],new byte[1147],{0x35,'x',0,0x5c,1,2},new byte[0]}){
            byte[] bytes=file(stop,tail,2);MesScript script=new MesScript("stale-tail",bytes,profile);
            check(script.length()==bytes.length&&script.sha256.equals(Binary.sha256(bytes)),"Physical input and SHA retained");
            check(script.records().size()==1&&script.at(script.firstOffset).opcode==0,"Static index ends at complete native STOP");
            check(script.staticIndexComplete()==(tail.length==0),"Partial physical index is reported honestly");
            check(script.requiredLabel(new byte[]{'x'})==null&&script.label(new byte[]{'x'})==null,"Old tail labels never become jump targets");
            reject(()->script.scanBlock(script.firstOffset,1),"Block scan cannot escape native STOP");
        }
        MesScript exact=new MesScript("exact",file(stop,new byte[0],2),profile);check(exact.staticIndexComplete(),"Ordinary original scripts keep complete indexing");
        reject(()->new MesScript("active-word",file(new byte[]{0x5c,1,2},new byte[0],2),profile),"Truncated reachable word remains invalid");
        reject(()->new MesScript("active-text",file(new byte[]{0x4c,0x41,0x42},new byte[0],2),profile),"Unterminated reachable text remains invalid");
        reject(()->new MesScript("bad-header",file(stop,new byte[]{0x6e,0x75,0,0,0,0},5),profile),"Header target in an opaque tail is not admitted");
        reject(()->new MesScript("bad-after-command",file(new byte[]{1,0,0,0x5c,1,2},new byte[0],2),profile),"An earlier non-STOP does not hide truncation");
        byte[] extension={0x50,0,0,0,0,0,0,0,0};reject(()->new MesScript("active-payload",file(extension,new byte[]{1,2},2),profile),"Required extension remains checked before STOP");
        return checks;
    }
}
