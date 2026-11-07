package org.circus.core;

import java.io.*;
import java.util.Arrays;

/** Explicit narrow projection never gains canonical adjacent-byte authority. */
public final class Pc1FrameFieldsTests {
    private static int count;
    private static void check(boolean value,String message){count++;if(!value)throw new AssertionError(message);}
    private interface Work {void run()throws Exception;}
    private static void reject(Work work,String text)throws Exception{boolean failed=false;try{work.run();}catch(IOException error){failed=text==null||String.valueOf(error.getMessage()).contains(text);}check(failed,"Reject "+text);}
    private static byte[] pattern(int salt){byte[] raw=new byte[8004];for(int n=0;n<raw.length;n++)raw[n]=(byte)(n*73+(n>>3)+salt);return raw;}
    private static byte[] save(Pc1FrameFields fields)throws IOException{ByteArrayOutputStream bytes=new ByteArrayOutputStream();fields.save(new DataOutputStream(bytes));return bytes.toByteArray();}
    public static int run()throws Exception{
        count=0;Pc1NativeFrame source=Pc1NativeFrame.fromBytes(pattern(17));source.writeI32(0,1);source.writeI32(0x490,0x89abcdef);source.writeI32(0x494,1001);source.writeI32(0x498,0x87654321);source.writeI32(0x1f20,0xfedcba98);source.writeI32(0x1f28,Integer.MIN_VALUE);source.writeI32(0x1f38,0x12345678);source.writeI32(0x1f3c,0x76543210);
        byte[] nativeBytes=source.bytes();Pc1FrameFields fields=Pc1FrameFields.capture(source);
        check(fields.currentCheckpoint()==0x89abcdef&&fields.textLog().count()==1001&&fields.cachedScenarioRead==0xfedcba98&&fields.headY==Integer.MIN_VALUE&&fields.waitKind==0x87654321&&fields.argument0==0x12345678&&fields.argument1==0x76543210,"subset keeps all32 bits of native scalars");
        check(!fields.textLog().frameBacked(),"immutable subset owns a detached reservation projection");reject(()->fields.textLog().pendingRawWord(),"no authority");
        source.writeBank90(0,99);source.writeI32(0x490,0);source.writeI32(0x1f20,0);Pc1TextLog.backed(source).clearLog();check(fields.currentCheckpoint()==0x89abcdef&&fields.textLog().count()==1001&&fields.cachedScenarioRead==0xfedcba98,"later canonical writes cannot mutate an earlier immutable subset");
        Pc1TextLog exposed=fields.textLog();exposed.clearLog();exposed.onCheckpoint04(1);check(fields.currentCheckpoint()==0x89abcdef&&fields.textLog().count()==1001,"subset getter returns a detached copy rather than a mutable alias");
        byte[] wire=save(fields);Pc1FrameFields decoded=Pc1FrameFields.load(new DataInputStream(new ByteArrayInputStream(wire)));check(wire.length==2036&&wire[0]=='P'&&wire[1]=='1'&&wire[2]=='D'&&wire[3]=='2',"P1D2 is explicit narrow scalar+1000word format, not an8004 image");check(Arrays.equals(wire,save(decoded)),"all raw scalar and inactive word bits survive narrow codec");
        Pc1NativeFrame target=Pc1NativeFrame.fromBytes(pattern(91));byte[] before=target.bytes();decoded.apply(target);byte[] after=target.bytes();
        for(int n=0;n<8004;n++){boolean projected=n>=0x490&&n<0xc6c||n>=0x1f20&&n<0x1f24||n>=0x1f28&&n<0x1f2c||n>=0x1f38&&n<0x1f40;check(after[n]==(projected?nativeBytes[n]:before[n]),"subset apply touches only explicitly owned bytes at "+n);}
        check(Pc1TextLog.backed(target).count()==1001&&Arrays.equals(Arrays.copyOfRange(after,0xc6c,0x1054),Arrays.copyOfRange(before,0xc6c,0x1054)),"log alias count does not authorize copying or rolling back ordinary bytes");
        check(target.readI32(0x1f24)==intAt(before,0x1f24)&&target.readI32(0x1f2c)==intAt(before,0x1f2c)&&target.readI32(0x48c)==intAt(before,0x48c),"paragraph/effect/rawPC and unknown regions remain outside this subset");
        Pc1FrameFields fromSlot=Pc1FrameFields.fromNativeSlot(Pc1NativeSlot.parse(nativeBytes));check(Arrays.equals(wire,save(fromSlot)),"native-slot projection and canonical-frame subset share the same raw facts");
        Pc1NativeFrame arbitrary=Pc1NativeFrame.zeroBss();arbitrary.writeI32(0x490,Integer.MIN_VALUE);arbitrary.writeI32(0x494,Integer.MAX_VALUE);arbitrary.writeI32(0x1f20,Integer.MAX_VALUE);Pc1FrameFields raw=Pc1FrameFields.capture(arbitrary);check(raw.currentCheckpoint()==Integer.MIN_VALUE&&raw.textLog().count()==Integer.MAX_VALUE&&raw.cachedScenarioRead==Integer.MAX_VALUE,"raw DWORDs are not coerced by old ushort/count/cache admission gates");reject(()->raw.textLog().pendingRawWord(),"no authority");
        Pc1NativeFrame identity=Pc1NativeFrame.fromBytes(nativeBytes);Pc1FrameFields.capture(identity).apply(identity);check(Arrays.equals(identity.bytes(),nativeBytes),"subset self-apply does not alter unknown or adjacent bytes");
        reject(()->Pc1FrameFields.capture((Pc1NativeFrame)null),"canonical frame");reject(()->fields.apply((Pc1NativeFrame)null),"canonical frame");byte[] wrong=wire.clone();wrong[3]='0';reject(()->Pc1FrameFields.load(new DataInputStream(new ByteArrayInputStream(wrong))),"format");reject(()->Pc1FrameFields.load(new DataInputStream(new ByteArrayInputStream(Arrays.copyOf(wire,wire.length-1)))),null);
        return count;
    }
    private static int intAt(byte[] raw,int offset){return(raw[offset]&255)|((raw[offset+1]&255)<<8)|((raw[offset+2]&255)<<16)|((raw[offset+3]&255)<<24);}
    public static void main(String[] args)throws Exception{System.out.println("PASS "+run()+" PC1 narrow frame-fields assertions");}
}
