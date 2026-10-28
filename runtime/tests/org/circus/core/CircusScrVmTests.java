package org.circus.core;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Independent bytecode fixtures exercise the Unity-era VM without shipping original game data. */
public final class CircusScrVmTests {
    private static int checks;
    private static void check(boolean ok,String why){checks++;if(!ok)throw new AssertionError(why);}
    private static final class Builder {
        final ArrayList<int[]> code=new ArrayList<int[]>();final ArrayList<String> strings=new ArrayList<String>();final ArrayList<Integer> jumps=new ArrayList<Integer>();final LinkedHashMap<String,Integer> labels=new LinkedHashMap<String,Integer>();
        Builder op(int op,int v){code.add(new int[]{op,v});return this;}Builder integer(int v){return op(0,v).op(3,0);}Builder text(String v){int i=strings.indexOf(v);if(i<0){i=strings.size();strings.add(v);}return op(0,i).op(3,1);}Builder call(int command,int count){return op(4,(count<<16)|command);}Builder label(String v){labels.put(v,code.size());return this;}
        byte[] bytes()throws IOException{ByteArrayOutputStream out=new ByteArrayOutputStream();out.write(CircusScrScript.SIGNATURE.getBytes(StandardCharsets.US_ASCII));le(out,code.size());for(int[] v:code){le(out,v[0]);le(out,v[1]);}le(out,strings.size());for(String s:strings)string(out,s);le(out,labels.size());for(Map.Entry<String,Integer> e:labels.entrySet()){string(out,e.getKey());le(out,e.getValue());}le(out,jumps.size());for(int j:jumps)le(out,j);le(out,0);out.write(0);return out.toByteArray();}
    }
    private static void le(OutputStream o,int v)throws IOException{for(int n=0;n<4;n++)o.write(v>>>(n*8));}
    private static void string(OutputStream o,String s)throws IOException{byte[] data=s.getBytes(StandardCharsets.UTF_8);int n=data.length;while(n>=128){o.write((n&127)|128);n>>>=7;}o.write(n);o.write(data);}
    private static final class Host implements CircusScrVm.Host {
        final HashMap<Integer,Integer> flags=new HashMap<Integer,Integer>();final ArrayList<String> events=new ArrayList<String>();
        public int readFlag(int f){Integer v=flags.get(f);return v==null?0:v;}public void writeFlag(int f,int v){flags.put(f,v);}
        public boolean command(int command,CircusScrVm.Value[] a,CircusScrVm vm)throws IOException{if(command==121){events.add(a[0].text());vm.waitClick();return true;}if(command==173){vm.waitTime(a[0].integer());return true;}return command==37||command==38||command==39||command==74||command==89||command==134||command==135||command==148||command==149;}
    }
    public static int run()throws Exception{
        checks=0;check(new CircusScrVm.Value("42").integer()==0,"string CsData numeric field retains zero");final HashMap<String,CircusScrScript> scripts=new HashMap<String,CircusScrScript>();CircusScrVm.Library library=name->{CircusScrScript s=scripts.get(name);if(s==null)throw new IOException("Missing fixture "+name);return s;};
        Builder b=new Builder();b.op(0,70000).op(1,0).op(0,3).op(2,0).op(7,0).op(6,5).op(0,5).op(5,0).op(3,0).integer(-400).call(74,2).text("对白 "+new String(new char[160]).replace('\0','界')).call(121,1).integer(25).call(173,1).op(23,0);
        scripts.put("test",new CircusScrScript("test",b.bytes()));Host host=new Host();CircusScrVm vm=new CircusScrVm(library,host,"TEST.txt");
        check(vm.run(1000)==CircusScrVm.Status.WAIT_CLICK,"text yield");check(host.readFlag(5)==70003&&host.readFlag(70003)==-400,"32-bit flags and ordered arguments");check(host.events.get(0).length()==163,"UTF8 and 7-bit string length");
        ByteArrayOutputStream saved=new ByteArrayOutputStream();vm.save(new DataOutputStream(saved));vm.advance();check(vm.run(1000)==CircusScrVm.Status.WAIT_TIME&&vm.remainingMillis()==25,"timed wait");vm.elapse(24);check(vm.status()==CircusScrVm.Status.WAIT_TIME,"bounded timed wait");vm.elapse(1);check(vm.run(1000)==CircusScrVm.Status.HALTED,"end script halts");vm.load(new DataInputStream(new ByteArrayInputStream(saved.toByteArray())));check(vm.status()==CircusScrVm.Status.WAIT_CLICK,"restore waiting position");
        int[][] arithmetic={{7,11,3,14},{8,11,3,8},{9,11,3,33},{10,-11,3,-3},{11,0,4,1},{12,0,4,0},{13,6,3,7},{14,6,3,2},{15,3,3,1},{16,3,3,1},{17,4,3,1},{18,4,3,0},{19,4,4,1},{20,4,4,0}};
        for(int[] a:arithmetic){b=new Builder().op(0,a[1]).op(1,0).op(0,a[2]).op(2,0).op(a[0],0).op(6,12).op(23,0);scripts.put("arith",new CircusScrScript("arith",b.bytes()));vm=new CircusScrVm(library,host,"arith");check(vm.run(100)==CircusScrVm.Status.HALTED&&host.readFlag(12)==a[3],"operator "+a[0]);}
        b=new Builder().op(0,0).op(24,0).op(0,99).op(6,20).op(0,42).op(6,20).op(27,1).op(0,90).op(6,20).op(23,0);b.jumps.add(4);b.jumps.add(9);scripts.put("branch",new CircusScrScript("branch",b.bytes()));vm=new CircusScrVm(library,host,"branch");check(vm.run(100)==CircusScrVm.Status.HALTED&&host.readFlag(20)==42,"branch target is instruction index");
        b=new Builder().text("done").call(89,1).op(0,9).op(6,21).label("done").op(0,10).op(6,21).op(23,0);scripts.put("goto",new CircusScrScript("goto",b.bytes()));vm=new CircusScrVm(library,host,"goto");check(vm.run(100)==CircusScrVm.Status.HALTED&&host.readFlag(21)==10,"named label no skipped instruction");
        b=new Builder().op(0,900).op(1,0).text("menu").call(134,1).op(0,2).op(2,0).op(8,0).op(6,22).op(23,0);scripts.put("story",new CircusScrScript("story",b.bytes()));b=new Builder().text("other").call(134,1).op(23,0);scripts.put("menu",new CircusScrScript("menu",b.bytes()));b=new Builder().text("pause").call(121,1).call(39,0);scripts.put("other",new CircusScrScript("other",b.bytes()));vm=new CircusScrVm(library,host,"story");check(vm.run(100)==CircusScrVm.Status.WAIT_CLICK&&vm.callDepth()==1&&"other".equals(vm.scriptName()),"qscript replacement preserves one original caller");saved.reset();vm.save(new DataOutputStream(saved));CircusScrVm restored=new CircusScrVm(library,host,"test");restored.load(new DataInputStream(new ByteArrayInputStream(saved.toByteArray())));restored.advance();check(restored.run(100)==CircusScrVm.Status.HALTED&&host.readFlag(22)==898&&restored.callDepth()==0,"restored quick return restores register and expression stacks");
        b=new Builder().op(2,0);scripts.put("bad",new CircusScrScript("bad",b.bytes()));vm=new CircusScrVm(library,host,"bad");check(vm.run(10)==CircusScrVm.Status.FAULT&&vm.error().contains("underflow"),"stack errors stop with location");
        b=new Builder().op(0,0).op(24,0);try{new CircusScrScript("bad",b.bytes());throw new AssertionError("accepted invalid jump");}catch(IOException expected){checks++;}
        byte[] bytes=scripts.get("test").strings[0].getBytes(StandardCharsets.UTF_8);check(bytes.length>127,"long UTF8 fixture uses multibyte length");
        b=new Builder().op(23,0);byte[] valid=b.bytes();for(int length:new int[]{0,11,15,valid.length-1}){try{new CircusScrScript("bad",Arrays.copyOf(valid,length));throw new AssertionError("accepted truncation "+length);}catch(IOException expected){checks++;}}
        try{CircusScrScript.normalizeName("../start");throw new AssertionError("accepted traversal");}catch(IOException expected){checks++;}
        return checks;
    }
    public static void main(String[] args)throws Exception{System.out.println("PASS CircusScr VM "+run()+" checks");}
}
