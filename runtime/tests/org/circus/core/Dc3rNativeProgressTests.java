package org.circus.core;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Self-authored native protobuf fixtures. No original game resources or saves are embedded. */
public final class Dc3rNativeProgressTests {
    private static int checks;
    private static void check(boolean ok,String why){checks++;if(!ok)throw new AssertionError(why);}
    private interface Action {void run()throws Exception;}
    private static void reject(Action action,String why)throws Exception{try{action.run();throw new AssertionError("Accepted "+why);}catch(IOException expected){checks++;}}
    private static final String CG="0\tev_test_a\n1039\tev_test_b\n",SCENES="0\ttest_a\n589\ttest_b\n";
    static void varint(OutputStream out,long value)throws IOException{while((value&~127L)!=0){out.write((int)value&127|128);value>>>=7;}out.write((int)value);}
    static void integer(OutputStream out,int number,long value)throws IOException{varint(out,number<<3);varint(out,value);}
    static void bytes(OutputStream out,int number,byte[] value)throws IOException{varint(out,(number<<3)|2);varint(out,value.length);out.write(value);}
    static byte[] framed(byte[] body)throws IOException{ByteArrayOutputStream out=new ByteArrayOutputStream();for(int i=0;i<4;i++)out.write(body.length>>>(8*i));out.write(body);return out.toByteArray();}
    private static void flag(OutputStream out,int key,int value)throws IOException{ByteArrayOutputStream entry=new ByteArrayOutputStream();integer(entry,1,key);if(value!=0)integer(entry,2,value);bytes(out,1,entry.toByteArray());}
    private static byte[] flags(int... values)throws IOException{ByteArrayOutputStream body=new ByteArrayOutputStream();flag(body,(101<<16)+999,1);for(int i=0;i<values.length;i+=2)flag(body,values[i],values[i+1]);return framed(body.toByteArray());}
    /** Positive native-format fixture whose gallery IDs also exist in the verified original catalogues. */
    public static byte[] fixture()throws IOException{return flags((100<<16),4,(100<<16)+1,1,(100<<16)+7,1,(97<<16),1,(97<<16)+1039,1,(98<<16),1,(98<<16)+589,1,(99<<16)+7,30,(100<<16)+60,1,(100<<16)+8,2);}
    private static Dc3rNativeProgress parse(byte[] bytes)throws IOException{return new Dc3rNativeProgress(bytes,CG,SCENES);}
    private static byte[] persistent(Dc3rHost host)throws IOException{ByteArrayOutputStream bytes=new ByteArrayOutputStream();host.savePersistent(new DataOutputStream(bytes));return bytes.toByteArray();}
    private static byte[] snapshot(Dc3rHost host)throws IOException{ByteArrayOutputStream bytes=new ByteArrayOutputStream();host.save(new DataOutputStream(bytes));return bytes.toByteArray();}
    public static int run()throws Exception {
        checks=0;byte[] nativeBytes=fixture(),unchanged=nativeBytes.clone();Dc3rNativeProgress progress=parse(nativeBytes);Dc3rHost target=new Dc3rHost();target.configureCatalogues(CG,SCENES);target.writeFlag((99<<16)+7,3);target.writeFlag((100<<16)+60,0);target.writeFlag((100<<16)+8,0);target.writeFlag((100<<16)+200,1);target.viewedScripts.add("already-read");
        byte[] before=persistent(target),oldSlot=snapshot(target);check(progress.source.total==7&&progress.source.completion==3&&progress.source.cg==2&&progress.source.scenes==2,"source includes only verified unlocks");
        check(progress.preview(target).total==7&&Arrays.equals(before,persistent(target)),"preview does not mutate target");
        check(progress.apply(target).total==7&&progress.preview(target).total==0,"positive import is idempotent");
        check(Arrays.equals(nativeBytes,unchanged),"source bytes stay immutable");
        check(target.readFlag((99<<16)+7)==3&&target.readFlag((100<<16)+60)==0&&target.readFlag((100<<16)+8)==0,"preferences and native sprite-state field are not copied");
        check(target.readFlag((100<<16)+200)==1&&target.viewedScripts.contains("already-read"),"existing completion and viewed scripts survive");
        check(target.readFlag((100<<16))==4&&target.readFlag((97<<16)+1039)==1&&target.readFlag((98<<16)+589)==1,"highest tier and catalogue boundary flags imported");
        check(target.viewedCg.contains("ev_test_a")&&target.viewedScripts.contains("test_b"),"gallery names and flags remain consistent");
        Dc3rHost reload=new Dc3rHost();reload.loadPersistent(new DataInputStream(new ByteArrayInputStream(persistent(target))));check(progress.preview(reload).total==0&&reload.readFlag((99<<16)+7)==3,"shipping persistence reload retains import and settings");
        target.load(new DataInputStream(new ByteArrayInputStream(oldSlot)));check(progress.preview(target).total==0&&target.viewedCg.contains("ev_test_b"),"loading older runtime slot cannot roll back imported unlocks");
        Dc3rNativeProgress lower=parse(flags(100<<16,1,(100<<16)+1,0,(97<<16),0));check(lower.apply(target).total==0&&target.readFlag(100<<16)==4,"lower native tier and zero flags cannot erase progress");
        for(int index:new int[]{1,2,3,4,5,6,7,11,20,21,22,23,24,25,28,200,201,202,203}){Dc3rHost one=new Dc3rHost();check(parse(flags((100<<16)+index,1)).apply(one).completion==1&&one.readFlag((100<<16)+index)==1,"completion allowlist "+index);}
        for(int index:new int[]{8,49,56,60,61,62,63,64,65,66,67,68,69,91,99,199,204})check(parse(flags((100<<16)+index,1)).source.total==0,"excluded global field "+index);
        for(int tier=0;tier<=4;tier++){Dc3rHost one=new Dc3rHost();parse(flags(100<<16,tier)).apply(one);check(one.readFlag(100<<16)==tier,"bounded native tier "+tier);}
        for(int value:new int[]{-1,2,Integer.MAX_VALUE})reject(()->parse(flags((100<<16)+1,value)),"invalid completion value "+value);
        reject(()->parse(flags(100<<16,5)),"tier above 4");reject(()->parse(flags((97<<16),2)),"nonboolean CG");reject(()->parse(flags((98<<16),-1)),"negative scene flag");
        reject(()->parse(flags((97<<16)+1,1)),"CG catalogue hole");reject(()->parse(flags((98<<16)+590,1)),"unknown scene ID");
        reject(()->parse(flags(1,1)),"local flag in global save");reject(()->parse(flags((100<<16)+1,1,(100<<16)+1,0)),"duplicate global key");
        reject(()->parse(framed(new byte[0])),"missing native valid-state marker");reject(()->parse(new byte[Dc3rNativeProgress.LIMIT_BYTES+1]),"oversized save");
        for(int n:new int[]{0,3,4,nativeBytes.length-1})reject(()->parse(Arrays.copyOf(nativeBytes,n)),"truncated native file "+n);
        byte[] extra=Arrays.copyOf(nativeBytes,nativeBytes.length+1);reject(()->parse(extra),"trailing bytes outside prefix");
        reject(()->new Dc3rNativeProgress(nativeBytes,"0\tx\n0\ty\n",SCENES),"duplicate catalogue id");reject(()->new Dc3rNativeProgress(nativeBytes,"0\tx\n1\tx\n",SCENES),"duplicate catalogue name");reject(()->new Dc3rNativeProgress(nativeBytes,"0\t../x\n",SCENES),"unsafe catalogue name");
        ByteArrayOutputStream body=new ByteArrayOutputStream();flag(body,(101<<16)+999,1);bytes(body,7,new byte[]{10,1,120});bytes(body,2,"ignored-jump".getBytes(StandardCharsets.UTF_8));Dc3rNativeProgress reads=parse(framed(body.toByteArray()));check(reads.source.total==0,"partial native read-lines and menu jumps never unlock a whole script");
        body.reset();flag(body,(101<<16)+999,1);bytes(body,2,new byte[]{(byte)0xc0});final byte[] invalidUtf=framed(body.toByteArray());reject(()->parse(invalidUtf),"malformed menu UTF8");
        body.reset();flag(body,(101<<16)+999,1);bytes(body,2,new byte[0]);bytes(body,2,new byte[0]);final byte[] duplicate=framed(body.toByteArray());reject(()->parse(duplicate),"duplicate singleton native field");
        protoTests();return checks;
    }
    private static void protoTests()throws Exception {
        ByteArrayOutputStream b=new ByteArrayOutputStream();integer(b,1,-1);integer(b,2,2147483647);bytes(b,3,"中文".getBytes(StandardCharsets.UTF_8));b.write(37);for(int n:new int[]{0,0,128,63})b.write(n);b.write(41);for(int n:new int[]{0,0,0,0,0,0,240,63})b.write(n);
        NativeProto.Message m=NativeProto.framed(framed(b.toByteArray()),1024);check(m.required(1).int32()==-1&&m.required(2).int32()==Integer.MAX_VALUE,"protobuf signed int32 limits");check(m.required(3).string(16).equals("中文")&&m.required(4).float32()==1&&m.required(5).float64()==1,"protobuf UTF8/fixed32/fixed64 decoding");
        reject(()->m.required(6),"missing protobuf field");reject(()->m.required(3).int32(),"wrong wire type");reject(()->m.required(3).string(2),"oversized string");
        reject(()->NativeProto.framed(framed(new byte[]{0}),1024),"zero field tag");reject(()->NativeProto.framed(framed(new byte[]{11}),1024),"group wire type");reject(()->NativeProto.framed(framed(new byte[]{10,5,1}),1024),"truncated length field");
        byte[] overflow=new byte[11];overflow[0]=8;Arrays.fill(overflow,1,10,(byte)0xff);overflow[10]=2;reject(()->NativeProto.framed(framed(overflow),1024),"varint overflow");
        b.reset();integer(b,1,4294967296L);NativeProto.Message huge=NativeProto.framed(framed(b.toByteArray()),1024);reject(()->huge.required(1).int32(),"int32 overflow");
        b.reset();integer(b,1,1);integer(b,1,2);NativeProto.Message repeats=NativeProto.framed(framed(b.toByteArray()),1024);check(repeats.fields(1).size()==2,"repeated collection fields preserved");reject(()->repeats.optional(1),"ambiguous singleton field");
        byte[] nested=new byte[0];for(int i=0;i<34;i++){b.reset();bytes(b,1,nested);nested=b.toByteArray();}final NativeProto.Message deep=NativeProto.framed(framed(nested),4096);reject(()->{NativeProto.Message v=deep;for(int i=0;i<34;i++)v=v.required(1).message();},"excessive message nesting");
    }
    public static void main(String[] args)throws Exception{System.out.println("PASS DC3R native progress "+run()+" checks");}
}
