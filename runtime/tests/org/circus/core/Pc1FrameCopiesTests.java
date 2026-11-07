package org.circus.core;

import java.io.*;
import java.util.*;

/** Private-memory interpretation of current CHS1C memcpy instructions, not
 *native code execution. Resource-handle/global writes stay external to helper. */
public final class Pc1FrameCopiesTests {
    private static int checks;
    private static void check(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
    private static void sameBytes(byte[] actual,byte[] expected,String message){checks++;if(actual.length!=expected.length)throw new AssertionError(message+" length");for(int n=0;n<actual.length;n++)if(actual[n]!=expected[n])throw new AssertionError(message+" byte0x"+Integer.toHexString(n));}
    private interface Action{void run()throws Exception;}
    private static void rejects(Action action,String part)throws Exception{boolean rejected=false;try{action.run();}catch(IOException expected){rejected=expected.getMessage().contains(part);}check(rejected,"reject "+part);}
    private static byte[] image(int multiplier,int bias){byte[] result=new byte[8004];for(int n=0;n<result.length;n++)result[n]=(byte)(n*multiplier+bias);return result;}
    private static byte[] hex(String value){byte[] bytes=new byte[value.length()/2];for(int n=0;n<bytes.length;n++)bytes[n]=(byte)Integer.parseInt(value.substring(2*n,2*n+2),16);return bytes;}
    private static int word(byte[] bytes,int offset){return(bytes[offset]&255)|((bytes[offset+1]&255)<<8)|((bytes[offset+2]&255)<<16)|(bytes[offset+3]<<24);}
    private static final class NativeReference {
        final byte[] live,suspended;byte[] suspendedAfterOrdinary;int ecx,esi,edi,repCount,dwordWrites,externalHandleWrite;
        NativeReference(byte[] live,byte[] suspended){this.live=live.clone();this.suspended=suspended.clone();}
        private byte[] owner(int address){if(address>=0x9d8588&&address<0x9da4cc)return live;if(address>=0x9d5e48&&address<0x9d7d8c)return suspended;throw new AssertionError("unowned native probe address "+Integer.toHexString(address));}
        private int offset(int address){return address-(owner(address)==live?0x9d8588:0x9d5e48);}
        void run(){run(false);}
        void capture(){run(true);}
        private void run(boolean capture){
            // Actual current PE41503B..415065, including the external handle
            // store between setting full-copy pointers and the final REP.
            byte[] code=hex(capture?"b9d1070000be88859d00bf485e9d0085c0f3a5":"b9fa000000bef4919d00bfb46a9d0083c40cf3a5b9d1070000be485e9d00bf88859d00891d94ca9c00f3a5");
            for(int pc=0;pc<code.length;){int op=code[pc]&255;
                if(op==0xb9){ecx=word(code,pc+1);pc+=5;}
                else if(op==0xbe){esi=word(code,pc+1);pc+=5;}
                else if(op==0xbf){edi=word(code,pc+1);pc+=5;}
                else if(op==0x83&&(code[pc+1]&255)==0xc4){if(code[pc+2]!=12)throw new AssertionError("native stack adjustment");pc+=3;}
                else if(op==0x85&&(code[pc+1]&255)==0xc0){pc+=2;} // capture's TEST changes flags only
                else if(op==0x89&&(code[pc+1]&255)==0x1d){if(word(code,pc+2)!=0x9cca94)throw new AssertionError("native external handle destination");externalHandleWrite=-1;pc+=6;}
                else if(op==0xf3&&(code[pc+1]&255)==0xa5){
                    for(;ecx!=0;ecx--){byte[] source=owner(esi),target=owner(edi);int from=offset(esi),to=offset(edi);for(int n=0;n<4;n++)target[to+n]=source[from+n];esi+=4;edi+=4;dwordWrites++;}
                    repCount++;if(repCount==1)suspendedAfterOrdinary=suspended.clone();pc+=2;
                }else throw new AssertionError("unhandled native probe opcode @"+pc);
            }
        }
    }
    private static void system()throws Exception{
        for(int seed:new int[]{0,1,19,127,255}){
            byte[] caller=image(73,seed),current=image(29,seed^0xa5);Pc1NativeFrame live=Pc1NativeFrame.fromBytes(caller);Pc1NativeFrame saved=Pc1FrameCopies.captureSystem(live);
            check(saved!=live&&Arrays.equals(saved.bytes(),caller),"system capture is a distinct complete raw image");live.writeBytes(0,current);byte[] savedBefore=saved.bytes();
            NativeReference capture=new NativeReference(caller,image(3,177));capture.capture();check(capture.repCount==1&&capture.dwordWrites==2001,"actual412175 capture uses2001 DWORDs");sameBytes(savedBefore,capture.suspended,"capture matches actual412160 REP byte by byte");
            NativeReference nativeResult=new NativeReference(current,savedBefore);nativeResult.run();
            check(nativeResult.repCount==2&&nativeResult.dwordWrites==2251&&nativeResult.externalHandleWrite==-1,"native probe interprets250+2001 DWORD copies and separate external handle write");
            byte[] expected=new byte[8004];for(int n=0;n<8004;n++)expected[n]=n>=0xc6c&&n<0x1054?current[n]:caller[n];sameBytes(nativeResult.suspendedAfterOrdinary,expected,"native first REP patches only current ordinary into suspended");
            Pc1FrameCopies.restoreSystem(live,saved);check(Arrays.equals(live.bytes(),nativeResult.live)&&Arrays.equals(saved.bytes(),nativeResult.suspended),"helper matches actual native live and MUTATED suspended images");
            byte[] restored=live.bytes();sameBytes(restored,expected,"system return retains every caller unknown byte and current ordinary");
            check(Arrays.equals(saved.bytes(),restored),"patched suspended and live agree after full native copy");
            live.writeU8(0x1f43,33);check(saved.readU8(0x1f43)==(restored[0x1f43]&255),"restored images do not alias backing arrays");
        }
        Pc1NativeFrame live=Pc1NativeFrame.fromBytes(image(7,9));byte[] before=live.bytes();rejects(()->Pc1FrameCopies.restoreSystem(live,null),"source owner");check(Arrays.equals(live.bytes(),before),"invalid source cannot modify live before validation");rejects(()->Pc1FrameCopies.restoreSystem(live,live),"distinct owners");check(Arrays.equals(live.bytes(),before),"self owner rejected before ordinary patch");rejects(()->Pc1FrameCopies.captureSystem(null),"live owner");
    }
    private static void candidate()throws Exception{
        byte[] original=image(11,77);Pc1NativeFrame live=Pc1NativeFrame.fromBytes(original),candidate=Pc1FrameCopies.captureCandidate(live);
        check(candidate!=live&&Arrays.equals(candidate.bytes(),original),"candidate capture includes every raw byte without typed parsing");
        live.writeBytes(0,image(37,211));Pc1FrameCopies.restoreCandidate(live,candidate);check(Arrays.equals(live.bytes(),original)&&Arrays.equals(candidate.bytes(),original),"candidate image restores its CAPTURED ordinary, not system-return current ordinary policy");
        sameBytes(live.bytes(),original,"candidate copy preserves all unknown raw fields");
        live.writeI32(Pc1FrameLayout.RAW_CODE_PC,123456);check(candidate.readI32(Pc1FrameLayout.RAW_CODE_PC)==word(original,0x48c),"candidate saved rawPC is not projected or rewritten");
        rejects(()->Pc1FrameCopies.captureCandidate(null),"live owner");rejects(()->Pc1FrameCopies.restoreCandidate(null,candidate),"live owner");rejects(()->Pc1FrameCopies.restoreCandidate(live,null),"source owner");rejects(()->Pc1FrameCopies.restoreCandidate(live,live),"distinct owners");
    }
    public static int run()throws Exception{checks=0;system();candidate();return checks;}
    public static void main(String[] args)throws Exception{if(args.length!=0)throw new IOException("Synthetic/native-instruction reference only; no original EXE argument");System.out.println("PASS Pc1FrameCopiesTests "+run());}
}
