package org.circus.core;
import java.io.*;
import java.util.*;
/** Ten original412A10 programs, exact getter/setter order, then PP host routing. */
public final class PpSystem206Tests {
 private static int count;
 private static void check(boolean yes,String why){count++;if(!yes)throw new AssertionError(why);}
 private static final class Case {final String id,events;final int[] values;final int fault;final int[][] mutations;Case(String id,int[] values,int fault,int[][] mutations,String events){this.id=id;this.values=values;this.fault=fault;this.mutations=mutations;this.events=events;}}
 private static final Case[] CASES={
        new Case("zero_tie",new int[]{0,0,0,0,0},-1,new int[][]{},"get:5:95:0;get:5:96:0;get:5:97:0;get:5:98:0;get:5:99:0;set:3:0:0"),
        new Case("increasing",new int[]{1,2,3,4,5},-1,new int[][]{},"get:5:95:1;get:5:96:2;get:5:97:3;get:5:98:4;get:5:99:5;set:3:0:4"),
        new Case("decreasing",new int[]{5,4,3,2,1},-1,new int[][]{},"get:5:95:5;get:5:96:4;get:5:97:3;get:5:98:2;get:5:99:1;set:3:0:0"),
        new Case("first_max_tie",new int[]{9,2,9,1,9},-1,new int[][]{},"get:5:95:9;get:5:96:2;get:5:97:9;get:5:98:1;get:5:99:9;set:3:0:0"),
        new Case("negative_signed",new int[]{-3,-2,-1,-4,-5},-1,new int[][]{},"get:5:95:-3;get:5:96:-2;get:5:97:-1;get:5:98:-4;get:5:99:-5;set:3:0:2"),
        new Case("signed_extremes",new int[]{-2147483648,2147483647,0,2147483647,-1},-1,new int[][]{},"get:5:95:-2147483648;get:5:96:2147483647;get:5:97:0;get:5:98:2147483647;get:5:99:-1;set:3:0:1"),
        new Case("live_later",new int[]{1,2,3,4,5},-1,new int[][]{new int[]{95,99,30},new int[]{96,95,100}},"get:5:95:1;get:5:96:2;get:5:97:3;get:5:98:4;get:5:99:30;set:3:0:4"),
        new Case("ignore_mode",new int[]{2,8,3,4,5},-1,new int[][]{},"get:5:95:2;get:5:96:8;get:5:97:3;get:5:98:4;get:5:99:5;set:3:0:1"),
        new Case("unknown_mode_unused",new int[]{2,8,3,4,5},-1,new int[][]{},"get:5:95:2;get:5:96:8;get:5:97:3;get:5:98:4;get:5:99:5;set:3:0:1"),
        new Case("failure_prefix",new int[]{1,2,3,4,5},3,new int[][]{},"get:5:95:1;get:5:96:2;get:5:97:3;fault:5:98"),
 };
 private static CircusResources resources()throws IOException{
  final byte[] names=new byte[20];GameResources files=new GameResources(new GameResources.Storage(){public Set<String> paths(){return Collections.singleton("AdvData/GRP/NAMES.DAT");}public byte[] read(String p,int limit){return names.clone();}public Archive.Source source(String p){return new Archive.Source(){public long size(){return names.length;}public byte[] read(long offset,int length)throws IOException{Binary.range(offset,length,names.length);return Arrays.copyOfRange(names,(int)offset,(int)offset+length);}};}});return new CircusResources(files,Profiles.get("dc3pp-9872"));
 }
 public static int run()throws Exception{
  count=0;for(final Case c:CASES){final int[] values=c.values.clone();final StringBuilder events=new StringBuilder();boolean failed=false;
   try{PpSystem206.execute(new PpSystem206.Access(){private void event(String e){if(events.length()!=0)events.append(';');events.append(e);}public int read(int bank,int index)throws IOException{check(bank==5&&index>=95&&index<=99,c.id+" exact getter arguments");if(c.fault==index-95){event("fault:"+bank+":"+index);throw new IOException("fixture getter boundary");}int value=values[index-95];event("get:"+bank+":"+index+":"+value);for(int[] m:c.mutations)if(m[0]==index)values[m[1]-95]=m[2];return value;}public void write(int bank,int index,int value){check(bank==3&&index==0,c.id+" native final setter");event("set:"+bank+":"+index+":"+value);}});}catch(IOException e){failed=e.getMessage().contains("fixture getter boundary");}
   check(failed==(c.fault>=0),c.id+" complete/fault prefix");check(events.toString().equals(c.events),c.id+" exact machine call journal: "+events);
  }
  CircusResources data=resources();Dc3Host host=new Dc3Host(data,TextCodec.identity(),true);MesScript script=new MesScript("pp206",new byte[]{0,0,0,0,0x72,(byte)0x98,0,0,0,0},Profiles.get("dc3pp-9872"));MesVm vm=new MesVm(name->script,host,script.name);
  int[] values={3,9,5,9,1};for(int n=0;n<5;n++)vm.variables.write(5,95+n,values[n]);
  check(host.command("system.command",new int[]{206},new byte[0]),"PP206 does not read unused mode");check(vm.variables.read(3,0)==1,"actual variable owner sees first maximum ordinal");
  for(int mode:new int[]{0,1,-1,Integer.MIN_VALUE,Integer.MAX_VALUE}){vm.variables.write(3,0,4);host.command("system.command",new int[]{206,mode},new byte[0]);check(vm.variables.read(3,0)==1,"mode is native unused "+mode);}
  for(int n=0;n<5;n++)check(vm.variables.read(5,95+n)==values[n],"native local sorting never rewrites source ordinary bytes");
  byte[] saved=vm.snapshot();Dc3Host restored=new Dc3Host(data,TextCodec.identity(),true);MesVm fresh=new MesVm(name->script,restored,script.name);fresh.restore(saved);check(fresh.variables.read(3,0)==1,"computed result uses existing portable variable snapshot, no new wire");
  boolean required=false;try{new Dc3Host(null,TextCodec.identity(),true).command("system.command",new int[]{206,0},new byte[0]);}catch(IOException e){required=e.getMessage().contains("system.command");}check(required,"DD never inherits PP206 semantics");
  return count;
 }
 public static void main(String[] args)throws Exception{System.out.println("PpSystem206Tests "+run()+" PASS");}
}
