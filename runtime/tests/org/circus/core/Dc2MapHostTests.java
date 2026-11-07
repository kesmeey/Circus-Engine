package org.circus.core;
import java.io.*;
import java.util.*;
/** Map opcode20 must reach its own wait/input/result owner and survive whole-VM restore. */
public final class Dc2MapHostTests {
 private static int count;
 private static void check(boolean yes,String why){count++;if(!yes)throw new AssertionError(why);}
 private static void integer(ByteArrayOutputStream b,int n){for(int k=0;k<4;k++)b.write(n>>>(8*k));}
 private static CircusResources data()throws IOException{
  ByteArrayOutputStream b=new ByteArrayOutputStream();integer(b,0);integer(b,0);integer(b,222);for(int n=0;n<222;n++){byte[] row=new byte[132],name=("SE/test"+n).getBytes(Binary.SJIS);System.arraycopy(name,0,row,0,name.length);b.write(row);}integer(b,0);integer(b,1);byte[] scenario=new byte[188];System.arraycopy("START".getBytes(Binary.SJIS),0,scenario,0,5);b.write(scenario);final byte[] bytes=b.toByteArray();
  GameResources files=new GameResources(new GameResources.Storage(){public Set<String> paths(){return Collections.singleton("AdvData/DAT/NAMES.DAT");}public byte[] read(String p,int limit)throws IOException{if(bytes.length>limit)throw new IOException("Fixture limit");return bytes.clone();}public Archive.Source source(String p){return new Archive.Source(){public long size(){return bytes.length;}public byte[] read(long at,int n)throws IOException{Binary.range(at,n,bytes.length);return Arrays.copyOfRange(bytes,(int)at,(int)at+n);}};}});return new CircusResources(files,Profiles.get("dc2-3363"));
 }
 private static MesScript code(CircusResources data)throws IOException{return new MesScript("START",new byte[]{0,0,0,0,0x63,0x33,28,20,0,0,0,0},data.profile);}
 private static MesVm vm(CircusResources data,PcHost host)throws IOException{MesScript script=code(data);return new MesVm(n->script,host,script.name);}
 private static byte[] hostBytes(PcHost host)throws IOException{ByteArrayOutputStream b=new ByteArrayOutputStream();host.save(new DataOutputStream(b));return b.toByteArray();}
 public static int run()throws Exception{
  count=0;CircusResources data=data();PcHost h=new PcHost(data,TextCodec.identity(),1);MesVm vm=vm(data,h);vm.variables.write(5,0,1);vm.variables.write(5,10,2);vm.variables.write(5,40,71);
  check(vm.run(1)==MesVm.Status.WAIT_HOST&&vm.offset()==9&&h.dc2Map.active,"actual20 opcode commits next PC and blocks VM");check(h.ui.buttons.size()==6&&!h.dc2Map.backdrop,"school map uses six actual hit rectangles");
  check(!vm.activateMenu(h.waitTicket()+1,2)&&h.dc2Map.selected==-1,"stale map click cannot select a location");check(vm.activateMenu(h.waitTicket(),2)&&h.dc2Map.selected==2&&h.ui.buttons.size()==2,"left click opens native yes/no confirmation");
  check(h.audio[1].equals("SE/test220"),"map click uses original sound220");h.hoverMenu(0);check(h.audio[1].equals("SE/test221")&&h.dc2Map.confirmHover==0,"map hover uses original sound221");
  vm.elapse(501);byte[] saved=vm.snapshot();PcHost restored=new PcHost(data,TextCodec.identity(),1);MesVm copy=vm(data,restored);copy.restore(saved);check(Arrays.equals(saved,copy.snapshot())&&restored.dc2Map.active&&restored.dc2Map.selected==2&&restored.dc2Map.frame(0)==1,"full PCH12 restores map selection, atlas clock and UI wait");
  check(copy.activateMenu(restored.waitTicket(),1)&&restored.dc2Map.selected==-1&&restored.dc2Map.active,"No returns to location choice");copy.activateMenu(restored.waitTicket(),2);copy.activateMenu(restored.waitTicket(),0);check(copy.variables.read(5,20)==2&&copy.variables.read(5,40)==71&&!restored.dc2Map.active&&copy.run(1)==MesVm.Status.HALTED,"Yes writes DC2 result20 without touching PC2 result40 and resumes VM");
  PcHost right=new PcHost(data,TextCodec.identity(),1);MesVm r=vm(data,right);r.variables.write(5,20,7);r.run(1);r.activateMenu(right.waitTicket(),-2);check(right.dc2Map.active&&r.variables.read(5,20)==7,"generic left/Enter action never becomes right map cancellation");r.activateMenu(right.waitTicket(),-1);check(right.dc2Map.active&&r.variables.read(5,20)==7,"bare right remains gated before500ms");r.elapse(500);r.activateMenu(right.waitTicket(),-1);check(r.variables.read(5,20)==99&&!right.dc2Map.active&&right.dc2Map.backdrop&&r.run(1)==MesVm.Status.HALTED,"bare right returns99 for actual MES inline rMenu, with a frozen map backdrop");
  long time=right.dc2Map.elapsed;right.elapse(9000);check(right.dc2Map.elapsed==time,"inline map backdrop no longer advances atlas time");
  PcHost old=new PcHost(data,TextCodec.identity(),1);byte[] modern=hostBytes(old);ByteArrayOutputStream tail=new ByteArrayOutputStream();old.dc2Map.save(new DataOutputStream(tail));byte[] legacy=Arrays.copyOf(modern,modern.length-tail.size());int marker=-1;for(int n=0;n<legacy.length-4;n++)if(legacy[n]==0x50&&legacy[n+1]==0x43&&legacy[n+2]==0x48&&legacy[n+3]==0x3c){marker=n;break;}check(marker>=0,"original host writes explicit PCH12");legacy[marker+3]=0x37;restored.load(new DataInputStream(new ByteArrayInputStream(legacy)));check(!restored.dc2Map.active&&!restored.dc2Map.backdrop,"pre-map PCH7 can migrate without inventing an active map");
  return count;
 }
 public static void main(String[] args)throws Exception{System.out.println("Dc2MapHostTests "+run()+" PASS");}
}
