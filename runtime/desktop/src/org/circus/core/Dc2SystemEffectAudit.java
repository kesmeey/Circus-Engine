package org.circus.core;

import java.io.*;
import java.nio.file.*;
import java.util.*;

/** Original DC2 system-command corpus and real service40 records through the
 * shipping VM/desktop rapid-reset consumer. Only owned control-state fixtures
 * are positioned; original scripts and game files are never modified. */
public final class Dc2SystemEffectAudit {
    private static int checks;
    private static void check(boolean yes,String why){checks++;if(!yes)throw new AssertionError(why);}
    private static final class Site {
        String script,path,sha; int pc,end,service,mode;
        Map<String,Object> json(){return Json.obj("script",script,"file_pc",pc,"code_pc",pc-versionOffset,"next_file_pc",end,"service",service,"mode",mode,"mes_sha256",sha);}
        int versionOffset;
    }
    private static final class Corpus {
        int scripts,records; final List<Site> resets=new ArrayList<>(),randoms=new ArrayList<>();
        final Map<Integer,Integer> services=new TreeMap<>();
        final Map<String,Integer> unmodelled=new TreeMap<>();
        final Map<String,List<Object>> examples=new TreeMap<>();
    }
    private static Corpus scan(CircusResources data)throws IOException {
        Corpus c=new Corpus();
        for(String path:data.files.paths()){
            String lower=path.toLowerCase(Locale.ROOT);if(!lower.startsWith("advdata/mes/")||!lower.endsWith(".mes"))continue;
            String name=path.substring(path.lastIndexOf('/')+1,path.length()-4);MesScript script=data.scripts().script(name);c.scripts++;
            for(MesScript.Record r:script.records()){
                if(r.payloadOwner>=0)continue;c.records++;String semantic=script.profile.operation(r.opcode);
                if(semantic.equals("dm.system.command")){
                    check(r.args.length==2,"DC2 system call has two native byte arguments");int service=r.args[0];c.services.put(service,c.services.getOrDefault(service,0)+1);
                    if(service==40){Site s=new Site();s.script=name;s.path=path;s.sha=script.sha256;s.pc=r.offset;s.end=script.instruction(r.offset).next;s.service=service;s.mode=r.args[1];s.versionOffset=script.versionOffset;c.resets.add(s);}
                }
                if(r.opcode==0x22){Site s=new Site();s.script=name;s.path=path;s.sha=script.sha256;s.pc=r.offset;s.end=script.instruction(r.offset).next;s.service=r.args[0];s.mode=r.args[1];s.versionOffset=script.versionOffset;c.randoms.add(s);}
                if(semantic.equals("native.unmodelled")){
                    String key=String.format("%02X %s",r.opcode,Arrays.toString(r.args));c.unmodelled.put(key,c.unmodelled.getOrDefault(key,0)+1);List<Object> examples=c.examples.computeIfAbsent(key,k->new ArrayList<>());if(examples.size()<3)examples.add(Json.obj("script",name,"file_pc",r.offset));
                }
            }
        }
        return c;
    }
    /** An owned CVS2 control fixture selects exactly one original instruction.
     * This is not an original Windows-slot importer or a route-playthrough. */
    private static byte[] positioned(byte[] saved,int target)throws IOException {
        DataInputStream in=new DataInputStream(new ByteArrayInputStream(saved));check(in.readInt()==0x43565332,"owned fixture is CVS2");for(int n=0;n<4;n++)in.readUTF();int at=saved.length-in.available(),length=saved.length-66;byte[] body=Arrays.copyOf(saved,length);for(int n=0;n<4;n++)body[at+n]=(byte)(target>>>(24-n*8));ByteArrayOutputStream bytes=new ByteArrayOutputStream();DataOutputStream out=new DataOutputStream(bytes);out.write(body);out.writeUTF(Binary.sha256(body));out.flush();return bytes.toByteArray();
    }
    private static DesktopLegacy record(CircusResources data,TextCodec codec,Site site)throws Exception {
        DesktopLegacy e=new DesktopLegacy(data,codec,true);MesVm source=new MesVm(data.scripts(),e.host(),site.script);e.restore(positioned(source.snapshot(),site.pc));check(e.vm().offset()==site.pc&&e.vm().scriptName().equals(site.script),"owned fixture selects actual original record");return e;
    }
    public static void main(String[] args)throws Exception {
        if(args.length!=2)throw new IOException("Require readonly_DC2_GAME owned_OUTPUT");Path game=Paths.get(args[0]).toRealPath(),out=DesktopRouteAudit.outputOutsideGame(game,args[1]);System.setProperty("dc.saveRoot",out.resolve("owned-saves").toString());System.setProperty("dc.cacheRoot",out.resolve("cache").toString());GameResources files=new GameResources(new LocalGameStorage(game),"dc2.exe");CircusResources data=new CircusResources(files,Profiles.get("dc2-3363"));TextCodec codec=GameText.detect(files,data.profile);Corpus corpus=scan(data);
        check(corpus.scripts>500,"full actual DC2 MES corpus enumerated");for(int absent:new int[]{8,30,31})check(corpus.services.getOrDefault(absent,0)==0,"effectful service absent from actual MES: "+absent);check(corpus.resets.size()==6,"six actual service40 calls");
        check(corpus.randoms.size()==1,"one actual Sakura22 record");List<Object> randomActual=new ArrayList<>();
        for(Site site:corpus.randoms){check(site.script.equals("1ind_0124_f1_skr")&&site.pc==0x4f07&&site.service==6&&site.mode==0,"actual Sakura22 physical byte record and range");int[][] goldens={{0,2,2531011},{1,5,2745024},{7,1,4029102},{Integer.MIN_VALUE,2,-2144952637},{-1,5,2316998}};for(int[] nativeRow:goldens){DesktopLegacy player=record(data,codec,site);PcHost host=(PcHost)player.host();host.nativeCrtState(nativeRow[0]);host.registers.put(2,99);host.registers.put(255,73);String[] scene=host.scene.slots.clone(),audio=host.audio.clone();check(player.vm().run(1)==MesVm.Status.RUNNING&&player.vm().offset()==site.end&&!host.waiting(),"actual Sakura22 returns directly to its next original instruction");check(player.vm().variables.read(99,2)==nativeRow[1]&&host.nativeCrtState()==nativeRow[2]&&player.vm().variables.read(99,255)==73,"actual Sakura22 consumes original CRT golden and writes only99:2");check(Arrays.equals(scene,host.scene.slots)&&Arrays.equals(audio,host.audio),"actual Sakura22 does not request images or audio");randomActual.add(Json.obj("script",site.script,"file_pc",site.pc,"seed",nativeRow[0],"range",6,"result99_2",nativeRow[1],"next_crt_state",nativeRow[2]));}check(site.sha.equals(Binary.sha256(files.read(site.path,64*1024*1024))),"actual Sakura source unchanged");}
        List<Object> actual=new ArrayList<>();
        for(Site site:corpus.resets){
            check(site.mode==0,"actual service40 mode0");DesktopLegacy e=record(data,codec,site);PcHost host=(PcHost)e.host();int generation=host.dc2RapidResetGeneration();e.rapid=true;e.auto=true;host.skipAllowed=true;final boolean[] hold={true};
            // Keep following story commands out of this independent instruction
            // fixture, while retaining the actual40 byte record and VM dispatch.
            e.vm().boundaryListener(()->{if(hold[0]){hold[0]=false;host.body="Owned post-service wait";host.textRendered=true;host.textWaiting=true;}});
            e.tick(0);check(host.dc2RapidResetGeneration()==generation+1,"actual40 publishes one native latch reset");check(!e.rapid&&!host.motionSkipPressed,"same tick consumes40 before platform rapid ADVANCE");check(e.auto&&host.skipAllowed,"40 preserves auto mode and skip permission");check(e.vm().offset()==site.end&&e.vm().status()==MesVm.Status.WAIT_HOST&&host.textWaiting,"40 returns normally and post-service wait is not skipped");e.vm().boundaryListener(null);
            // Once consumed, the event must not cancel a subsequent user toggle.
            e.rapid=true;host.skipAllowed=false;e.tick(0);check(e.rapid&&host.motionSkipPressed&&host.textWaiting,"old reset event is not repeatedly consumed");check(!host.skipAllowed,"platform reset does not enable forbidden skip");
            Dc2SystemCommands.command(host,new int[]{40,255});check(host.dc2RapidResetGeneration()==generation+2,"native40 ignores mode rather than treating255 as a different service");e.tick(0);check(!e.rapid&&!host.motionSkipPressed&&!host.skipAllowed&&e.auto,"pre-elapse pending reset is consumed without changing unrelated modes");
            e.controlKey(true);e.rapid=true;Dc2SystemCommands.command(host,new int[]{40,0});e.tick(0);check(!e.rapid&&host.motionSkipPressed&&!host.skipAllowed&&e.auto,"40 clears the latch while separately held physical Ctrl remains effective");e.vm().variables.write(8,18,1);e.tick(0);check(!host.motionSkipPressed,"native ordinary8:18 disables even physically held Ctrl");e.controlKey(false);e.vm().variables.write(8,18,0);e.tick(0);check(!host.motionSkipPressed&&!e.rapid,"physical key release does not recreate the cancelled latch");
            byte[] saved=e.snapshot();DesktopLegacy fresh=new DesktopLegacy(data,codec,true);fresh.restore(saved);check(((PcHost)fresh.host()).dc2RapidResetGeneration()==host.dc2RapidResetGeneration(),"rapid-reset generation survives real session codec on a fresh host");
            for(int absent:new int[]{8,30,31}){boolean failed=false;try{Dc2SystemCommands.command(host,new int[]{absent,0});}catch(IOException expected){failed=expected.getMessage().contains("service "+absent+"(");}check(failed,"unused effectful service keeps precise explicit boundary "+absent);}
            check(site.sha.equals(Binary.sha256(files.read(site.path,64*1024*1024))),"original MES source unchanged");actual.add(site.json());
        }
        List<Object> unknown=new ArrayList<>();for(Map.Entry<String,Integer> entry:corpus.unmodelled.entrySet())unknown.add(Json.obj("record",entry.getKey(),"count",entry.getValue(),"examples",corpus.examples.get(entry.getKey())));
        Files.write(out.resolve("result.json"),(Json.text(Json.obj("pass",true,"checks",checks,"mes_scripts",corpus.scripts,"physical_records",corpus.records,"system_call_counts",corpus.services,"actual40_sites",actual,"actual_sakura22",randomActual,"native_unmodelled_groups",unknown,"same_tick_after_run_reset",true,"pending_pre_elapse_reset",true,"repeat_event_idempotent",true,"auto_and_skip_permission_preserved",true,"physical_ctrl_and_ordinary_disable_independent",true,"session_generation_roundtrip",true,"original_exe_executed",false,"original_game_directory_written",false,"scope","Six original service40 records and the actual Sakura22 record dispatched from owned positioned CVS2 fixtures; five fixed seeds verify Sakura RNG without claiming bonus-story completion. A controlled post-instruction stable wait isolates each record and proves shipping platform rapid consumption before ADVANCE. Not a title-to-route playthrough or original-Windows snapshot comparison. Services8/30/31 have zero physical calls in this selected complete MES corpus and remain explicit unsupported effects."))+"\n").getBytes("UTF-8"));System.out.println("Dc2SystemEffectAudit PASS "+checks+"; scripts="+corpus.scripts+"; actual40="+corpus.resets.size()+"; Sakura22="+corpus.randoms.size());
    }
}
