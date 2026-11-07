package org.circus.core;
import java.io.*;
import java.nio.file.*;
import java.util.*;
/** All real DC2 map scripts: original variables, input, inline menu and CVS2 continuation. */
public final class Dc2MapAudit {
 private static int checks;
 private static void check(boolean yes,String why){checks++;if(!yes)throw new AssertionError(why);}
 private static final class Session {
  final PcHost host;final MesVm vm;
  Session(CircusResources data,TextCodec codec,String script)throws IOException{host=new PcHost(data,codec,1);vm=new MesVm(data.scripts(),host,script);}
 }
 private static void step(Session s)throws IOException {MesVm.Status status=s.vm.run(1);if(status==MesVm.Status.FAULT)throw new IOException(s.vm.error());if(status==MesVm.Status.WAIT_HOST&&s.host.remainingMillis()>0)s.vm.elapse(600000);}
 private static void open(Session s)throws IOException {for(int n=0;n<1000&&!s.host.dc2Map.active;n++)step(s);check(s.host.dc2Map.active&&s.vm.status()==MesVm.Status.WAIT_HOST,"original map script reaches20 and blocks");}
 private static int button(PcHost h,String action)throws IOException{for(int n=0;n<h.ui.buttons.size();n++)if(h.ui.buttons.get(n).action.equals(action))return n;throw new IOException("Original map action missing "+action);}
 public static void main(String[] args)throws Exception{
  if(args.length!=2)throw new IOException("Require readonly_DC2_GAME owned_OUTPUT");Path game=Paths.get(args[0]).toRealPath(),out=DesktopRouteAudit.outputOutsideGame(game,args[1]);
  GameResources files=new GameResources(new LocalGameStorage(game),"dc2.exe");CircusResources data=new CircusResources(files,Profiles.get("dc2-3363"));TextCodec codec=GameText.detect(files,data.profile);List<String> scripts=new ArrayList<>();
  for(String path:files.paths())if(path.startsWith("advdata/mes/")&&path.endsWith(".mes")){String name=path.substring(12,path.length()-4);MesScript code=data.scripts().script(name);for(MesScript.Record record:code.records())if(code.profile.operation(record.opcode).equals("dm.system.command")&&record.args[0]==20){scripts.add(name);break;}}
  Collections.sort(scripts);check(scripts.size()==24,"actual corpus has24 map20 callers");List<Object> results=new ArrayList<>();
  for(String script:scripts){
   Session live=new Session(data,codec,script);open(live);long ticket=live.host.waitTicket();int first=-1;for(int n=0;n<6;n++)if(live.vm.variables.read(5,n)!=0){first=live.vm.variables.read(5,10+n);break;}check(first>=0&&first<=6,"actual map seeds at least one visible actor with a real location");
   live.vm.activateMenu(ticket,button(live.host,"@dc2map:"+first));byte[] saved=live.vm.snapshot();Session fresh=new Session(data,codec,script);fresh.vm.restore(saved);check(Arrays.equals(saved,fresh.vm.snapshot()),"actual map midway CVS2 restores byte-identical VM and native owners");
   fresh.vm.activateMenu(fresh.host.waitTicket(),button(fresh.host,"@dc2map:no"));check(fresh.host.dc2Map.active&&fresh.host.dc2Map.selected==-1,"actual No returns to map location list");fresh.vm.activateMenu(fresh.host.waitTicket(),button(fresh.host,"@dc2map:"+first));fresh.vm.activateMenu(fresh.host.waitTicket(),button(fresh.host,"@dc2map:yes"));
   check(!fresh.host.dc2Map.active&&fresh.vm.variables.read(5,20)==first,"actual Yes writes original bank5:20");for(int n=0;n<1000&&fresh.vm.scriptName().equalsIgnoreCase(script);n++)step(fresh);check(!fresh.vm.scriptName().equalsIgnoreCase(script)&&fresh.vm.status()!=MesVm.Status.FAULT,"actual map result dispatches to a story MES");String target=fresh.vm.scriptName();
   Session right=new Session(data,codec,script);open(right);right.vm.activateMenu(right.host.waitTicket(),-1);check(right.host.dc2Map.active,"actual bare right before500ms is gated");right.vm.elapse(500);right.vm.activateMenu(right.host.waitTicket(),-1);check(right.vm.variables.read(5,20)==99&&right.host.dc2Map.backdrop&&!right.host.dc2Map.active,"actual right writes99 and retains map backdrop");
   for(int n=0;n<1000&&!right.host.ui.waiting;n++)step(right);check(right.vm.scriptName().equalsIgnoreCase(script)&&right.host.ui.waiting&&right.host.ui.buttons.stream().anyMatch(b->b.action.equals("return")),"original branch99 builds its own inline rMenu without external ADVMENU");
   right.vm.activateMenu(right.host.waitTicket(),button(right.host,"return"));for(int n=0;n<1000&&!right.host.dc2Map.active;n++)step(right);check(right.host.dc2Map.active&&right.vm.scriptName().equalsIgnoreCase(script),"original inline Return reopens20 map");
   List<Object> actors=new ArrayList<>();for(int n=0;n<6;n++)actors.add(Json.obj("visible",live.vm.variables.read(5,n),"location",live.vm.variables.read(5,10+n)));
   results.add(Json.obj("script",script,"school",live.host.dc2Map.school,"actors",actors,"selected_location",first,"story_target",target,"snapshot_bytes",saved.length,"right_inline_menu",true));
  }
  Files.write(out.resolve("result.json"),(Json.text(Json.obj("checks",checks,"pass",true,"profile",data.profileId,"map_script_count",scripts.size(),"map_scripts",results,"target_sha256",Binary.sha256(files.read("dc2.exe",64*1024*1024)),"scope","All24 actual map scripts to result-driven next story transfer and right99 inline menu/return; not full game routes or Windows renderer/COM equivalence","original_exe_executed",false,"original_game_directory_written",false))+"\n").getBytes("UTF-8"));System.out.println("Dc2MapAudit PASS "+checks+";24 original map scripts selected/story transfer/99-inline-menu/CVS2");
 }
}
