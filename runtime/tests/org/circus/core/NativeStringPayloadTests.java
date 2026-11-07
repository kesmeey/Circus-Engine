package org.circus.core;

import java.io.*;
import java.util.*;

/** PC1 native1B/25/26/4F/51/57/5C own raw NUL strings, regardless of the skipped marker byte. */
public final class NativeStringPayloadTests {
    private static int checks;
    private static void check(boolean value,String why){checks++;if(!value)throw new AssertionError(why);}
    private static MesScript script(int marker,String value)throws IOException{
        return script(0x26,marker,value);
    }
    private static MesScript script(int owner,int marker,String value)throws IOException{
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();bytes.write(new byte[]{0,0,0,0,0x63,0x20,(byte)owner,0,0});if(owner>=0x4f)bytes.write(new byte[6]);
        bytes.write(marker);bytes.write(value.getBytes(Binary.SJIS));bytes.write(0);bytes.write(new byte[3]);
        return new MesScript("owned-string",bytes.toByteArray(),Profiles.get("dcpc-2063"));
    }
    public static int run()throws Exception {
        checks=0;byte[] raw="advmenu".getBytes(Binary.SJIS);
        for(int owner:new int[]{0x1b,0x25,0x26,0x4f,0x51,0x57,0x5c})for(int marker=0;marker<256;marker++){
            int headEnd=owner>=0x4f?15:9,end=headEnd+9;MesScript script=script(owner,marker,"advmenu");MesScript.Instruction instruction=script.instruction(6);MesScript.Record payload=script.at(headEnd);
            check(payload.payloadOwner==6&&payload.opcode==marker&&payload.end==end&&Arrays.equals(payload.bytes,raw),"every marker byte is skipped while the raw bytes and exact ownership are retained");
            check(instruction.stringPayload!=null&&Arrays.equals(instruction.stringPayload.bytes,raw)&&instruction.next==end&&instruction.args.length==(owner>=0x4f?4:2),"each native owner consumes the entire companion without using its marker's instruction framing");
            check(script.records().size()==3&&!payload.terminalFragment&&script.at(end).opcode==0,"owned data is not recursively an instruction owner or STOP");
        }
        MesScript empty=script(0,"");check(empty.at(9).end==11&&!empty.at(9).terminalFragment&&empty.instruction(6).next==11,"empty raw string with STOP marker is two bytes of owned data, not a truncated STOP");
        MesScript label=script(0x32,"advmenu");
        check(label.label(raw)==null&&label.requiredLabel(raw)==9,"structural label index excludes owned data while the independent native raw scanner can still find its bytes");
        MesScript.Record dynamic=script(0x58,"advmenu").fetchRecord(9);
        check(dynamic.payloadOwner<0&&dynamic.bytes.length==0&&dynamic.args.length==4,"jumping to a former raw payload decodes a fresh ordinary word instruction at that actual PC");
        for(byte[] tail:new byte[][]{new byte[0],new byte[]{0x46},new byte[]{0x46,'x'}}){
            ByteArrayOutputStream bytes=new ByteArrayOutputStream();bytes.write(new byte[]{0,0,0,0,0x63,0x20,0x26,0,0});bytes.write(tail);
            MesScript truncated=new MesScript("broken-owned-string",bytes.toByteArray(),Profiles.get("dcpc-2063"));
            check(!truncated.staticIndexComplete(),"Native file admission does not pretend a truncated static index is complete");
            boolean rejected=false;try{truncated.stringPayload(9,6);}catch(IOException expected){rejected=true;}
            check(rejected,"A standalone file view cannot fabricate a missing marker or NUL");
            Pc1CodeBuffer buffer=Pc1CodeBuffer.zeroBss();buffer.load(truncated);
            byte[] expected=tail.length==2?new byte[]{'x'}:new byte[0];
            check(Arrays.equals(truncated.nativeView(buffer).instruction(6).stringPayload.bytes,expected),"The native view uses actual known BSS zeros after its loaded prefix");
        }
        check(!Profiles.get("dc2pc-5769").ownsStringPayload(0x26)&&!Profiles.get("dc2-3363").ownsStringPayload(0x26),"one verified PC1 handler does not create a cross-version ownership rule");
        return checks;
    }
    public static void main(String[] args)throws Exception{System.out.println("PASS "+run()+" raw string-payload assertions");}
}
