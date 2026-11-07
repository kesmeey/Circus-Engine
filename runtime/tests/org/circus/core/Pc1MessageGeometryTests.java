package org.circus.core;

import java.io.*;
import java.util.*;

/** Four complete mode2 paths from current CHS private-CPU interpretation,781 steps.
 * Native field-address order differs from the fixture's44C-before448 display order. */
public final class Pc1MessageGeometryTests {
    private static int count;
    private static void check(boolean value,String why){count++;if(!value)throw new AssertionError(why);}
    private interface Action{void run()throws Exception;}
    private static void rejects(Action action,String why)throws Exception{boolean rejected=false;try{action.run();}catch(IOException|IllegalArgumentException expected){rejected=true;}check(rejected,why);}
    private static final int[] INITIAL={1000,1001,1002,1003,1005,1004,1006,1007,1008,1009,1010,1011,1012};
    private static final int[][] REFERENCE={
        {11,23,31,4743,171,155,962,43,52,26,8643,9,7},
        {99,88,41,23173,171,155,962,71,81,33,18118,9,7},
        {11,23,31,4807,171,155,962,43,52,26,8643,9,7},
        {99,88,41,23237,171,155,962,71,81,33,18118,9,7}
    };
    private static final int[][] OPTIONS={{0,0,-1,23},{1,0,-1,88},{0,1,999,999},{1,1,-1,88}};
    private static Pc1MessageGeometry.Entry entry(int x,int y,int type,int condition,int width,int height){return new Pc1MessageGeometry.Entry(x,y,type,condition,0,0,width,height);}
    private static List<Pc1MessageGeometry.Entry> body(){return Arrays.asList(entry(11,23,10,0,31,0x12c7),entry(155,171,10,0,0,0x03c2),entry(-77,101,1,1,80,70),entry(99,88,10,1,41,0x5ac5));}
    private static List<Pc1MessageGeometry.Entry> name(){return Arrays.asList(entry(43,52,10,0,26,0x21c3),entry(9,7,10,0,0,0x7fff),entry(71,81,10,1,33,0x46c6));}
    private static byte[] wire(Pc1MessageGeometry geometry)throws IOException{ByteArrayOutputStream bytes=new ByteArrayOutputStream();geometry.save(new DataOutputStream(bytes));return bytes.toByteArray();}
    public static int run()throws Exception{
        count=0;
        for(int n=0;n<REFERENCE.length;n++){
            Pc1MessageGeometry geometry=new Pc1MessageGeometry(INITIAL);int[] option=OPTIONS[n];int head=geometry.prepareBody(body(),option[0],option[1],option[2]);geometry.prepareName(name(),option[0]);int[] actual=geometry.snapshot();
            for(int field=0;field<13;field++)check(actual[field]==REFERENCE[n][field],"current native full mode2 path "+n+" field@"+Integer.toHexString(Pc1MessageGeometry.BODY_X+4*field));
            check(head==option[3],"frame headY result matches native14th field in reference "+n);
            check(geometry.field(Pc1MessageGeometry.BODY_HISTORY_STYLE)==171&&geometry.field(Pc1MessageGeometry.BODY_HISTORY_CONFIG)==155,"codec/snapshot field order is native address448 then44C");
        }
        Pc1MessageGeometry geometry=new Pc1MessageGeometry(INITIAL);byte[] initial=wire(geometry);int emptyHead=geometry.prepareBody(Collections.emptyList(),0,0,-1);geometry.prepareName(Collections.emptyList(),0);
        check(emptyHead==1001&&Arrays.equals(initial,wire(geometry)),"empty lists retain prior globals while unknown headY adopts the retained bodyY");
        geometry.prepareBody(Collections.singletonList(entry(Integer.MAX_VALUE,Integer.MIN_VALUE,1,0,Integer.MAX_VALUE,Integer.MIN_VALUE)),0,0,1);
        check(Arrays.equals(initial,wire(geometry)),"nontext entries only affect discarded native clipping locals in mode2");
        List<Pc1MessageGeometry.Entry> conditional=Collections.singletonList(entry(7,8,10,Integer.MIN_VALUE,-9,-1));geometry.prepareBody(conditional,0,0,-3);
        check(Arrays.equals(initial,wire(geometry)),"any nonzero condition is skipped when cachedRead is exactly0");
        check(geometry.prepareBody(conditional,-1,0,-3)==-3&&geometry.field(Pc1MessageGeometry.BODY_X)==7&&geometry.field(Pc1MessageGeometry.BODY_PITCH)==-9,"nonzero cachedRead includes negative values and negative width remains the nonzero branch");
        check(geometry.field(Pc1MessageGeometry.BODY_STYLE)==0xffbf,"native literal0000FFBF mask clears high16bits as well as bit40");
        geometry.prepareBody(conditional,1,-1,-1);check(geometry.field(Pc1MessageGeometry.BODY_STYLE)==-1,"nonzero shading leaves the entire raw32-bit current style intact");
        geometry.prepareBody(Collections.singletonList(entry(-1,-2,10,0,0,-3)),0,0,99);
        check(geometry.field(Pc1MessageGeometry.BODY_HISTORY_CONFIG)==-1&&geometry.field(Pc1MessageGeometry.BODY_HISTORY_STYLE)==-2&&geometry.field(Pc1MessageGeometry.BODY_HISTORY_AUX)==-3,"zero-width body configuration keeps raw fields and is not shaded");
        int[] beforeName=geometry.snapshot();geometry.prepareName(Collections.singletonList(entry(17,19,10,0,31,-1)),0);
        check(geometry.field(Pc1MessageGeometry.NAME_STYLE)==-1,"name current style never uses the body's bitmask");
        for(int n=0;n<7;n++)check(geometry.snapshot()[n]==beforeName[n],"name preparation cannot rewrite any body owner");
        geometry.prepareName(Collections.singletonList(entry(0x7fffffff,Integer.MIN_VALUE,10,0,0,123)),0);
        check(geometry.field(Pc1MessageGeometry.NAME_HISTORY_STYLE)==Integer.MAX_VALUE&&geometry.field(Pc1MessageGeometry.NAME_HISTORY_AUX)==Integer.MIN_VALUE,"zero-width name config preserves two raw native DWORDs and ignores entryheight");
        Pc1MessageGeometry one=new Pc1MessageGeometry(INITIAL),two=new Pc1MessageGeometry(INITIAL);int oneHead=one.prepareBody(body(),1,0,-1),twoHead=two.prepareBody(body(),1,0,-991);
        check(oneHead==88&&twoHead==-991&&Arrays.equals(wire(one),wire(two)),"frame headY belongs only in the return value, never global state or codec");
        Pc1MessageGeometry copy=geometry.copy();copy.prepareBody(body(),1,1,-1);check(!Arrays.equals(copy.snapshot(),geometry.snapshot()),"copy isolates global geometry changes");
        int[] mutable=INITIAL.clone();Pc1MessageGeometry owned=new Pc1MessageGeometry(mutable);mutable[0]=1;int[] snapshot=owned.snapshot();snapshot[1]=2;
        check(owned.field(Pc1MessageGeometry.BODY_X)==1000&&owned.field(Pc1MessageGeometry.BODY_Y)==1001,"constructor and snapshot do not expose writable global aliases");
        int[] rawEntry={3,5,10,0,0,0,7,9};Pc1MessageGeometry.Entry immutable=new Pc1MessageGeometry.Entry(rawEntry);rawEntry[0]=999;immutable.snapshot()[1]=777;owned.prepareBody(Collections.singletonList(immutable),0,1,-1);
        check(owned.field(Pc1MessageGeometry.BODY_X)==3&&owned.field(Pc1MessageGeometry.BODY_Y)==5,"raw template entry is immutable and owned independently");
        byte[] saved=wire(geometry);check(saved.length==56&&new DataInputStream(new ByteArrayInputStream(saved)).readInt()==0x50314731,"P1G1 contains exactly magic plus13 global DWORDs");
        Pc1MessageGeometry restored=Pc1MessageGeometry.load(new DataInputStream(new ByteArrayInputStream(saved)));check(Arrays.equals(saved,wire(restored)),"all raw32-bit geometry fields roundtrip without coordinate/style clipping");
        ByteArrayOutputStream appended=new ByteArrayOutputStream();appended.write(saved);new DataOutputStream(appended).writeInt(777);DataInputStream input=new DataInputStream(new ByteArrayInputStream(appended.toByteArray()));Pc1MessageGeometry.load(input);check(input.readInt()==777,"geometry codec stops before the outer frame owner");
        byte[] old=saved.clone();old[3]='0';rejects(()->Pc1MessageGeometry.load(new DataInputStream(new ByteArrayInputStream(old))),"unknown geometry version is rejected");
        for(int n:new int[]{0,3,4,27,55}){byte[] cut=Arrays.copyOf(saved,n);rejects(()->Pc1MessageGeometry.load(new DataInputStream(new ByteArrayInputStream(cut))),"truncated global codec is rejected");}
        rejects(()->new Pc1MessageGeometry(new int[14]),"frame headY cannot be appended to13-global snapshot");rejects(()->new Pc1MessageGeometry.Entry(new int[7]),"raw geometry entry requires exactly8DWORDs");
        rejects(()->geometry.field(0x9da4b0),"frame headY address is outside global geometry");rejects(()->geometry.field(0x43d439),"unaligned addresses cannot alias a global field");
        byte[] beforeInvalid=wire(geometry);rejects(()->geometry.prepareBody(Arrays.asList(entry(1,2,10,0,3,4),null),0,0,-1),"invalid entry input fails before partial global mutation");check(Arrays.equals(beforeInvalid,wire(geometry)),"invalid body list cannot partly replace the global owner");
        rejects(()->geometry.prepareName(null,0),"missing selected name list is explicit");
        check(Arrays.equals(new Pc1MessageGeometry().snapshot(),new int[]{352,423,34,7,140,137,135,376,364,28,7,7,6}),"empty constructor uses the current locked CHS PE globals, not invented zero geometry");
        return count;
    }
    public static void main(String[] args)throws Exception{System.out.println("PASS "+run()+" PC1 message-geometry assertions");}
}
