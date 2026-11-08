package org.circus.core;

import java.io.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Runs original title-to-ending control flow. Media completion is explicitly simulated. */
public final class DesktopRouteAudit {
    private static String progress(DesktopEngine engine,String where){
        if(engine instanceof DesktopLegacy&&((DesktopLegacy)engine).host() instanceof FlHost){
            FlHost h=(FlHost)((DesktopLegacy)engine).host();FlCredits c=h.flCredits;
            if(c.active){String key=where+":"+c.stage+":"+c.index+":"+c.alpha+":"+c.fading+":"+c.leadDone;for(FlCredits.Layer layer:c.frame().layers)key+=":"+layer.y;return key;}
            if(h.flActorMotion.active){int n=h.flActorMotion.actor;return where+":actor:"+n+":"+h.liveActors[n][0]+":"+h.liveActors[n][1];}
            if(h.flComposition.active)return where+":composition:"+h.flComposition.phase+":"+h.flComposition.progress+":"+h.flComposition.draws().size();
        }
        return where;
    }
    static Path outputOutsideGame(Path game,String requested)throws IOException{
        Path destination=Paths.get(requested).toAbsolutePath().normalize(),existing=destination;
        while(!Files.exists(existing))existing=existing.getParent();
        destination=existing.toRealPath().resolve(existing.relativize(destination)).normalize();
        if(destination.startsWith(game))throw new IOException("Route audit output must be outside the original game directory");
        Files.createDirectories(destination);return destination;
    }
    public static void main(String[] args)throws Exception{
        if(args.length<2)throw new IllegalArgumentException("DesktopRouteAudit GAME OUTPUT [first|last] [ROUTE] [--exe relative.exe] [--action LABEL]... [--choose SCRIPT@HEX=LABEL]... [--steps N]");
        int at=2,route=1,stepBudget=1000000;boolean last=false;String executable="";List<String> startup=new ArrayList<>();Map<String,String> overrides=new LinkedHashMap<>();Set<String> consumedOverrides=new HashSet<>();
        if(at<args.length&&!args[at].startsWith("--")){if(!args[at].equals("first")&&!args[at].equals("last"))throw new IllegalArgumentException("Expected first or last choice policy");last=args[at++].equals("last");}
        if(at<args.length&&!args[at].startsWith("--"))route=Integer.parseInt(args[at++]);
        for(;at<args.length;at+=2){if(at+1>=args.length)throw new IllegalArgumentException("Missing value for "+args[at]);switch(args[at]){case "--exe":executable=args[at+1];break;case "--action":startup.add(args[at+1]);break;case "--choose":{String choice=args[at+1];int split=choice.lastIndexOf('=');if(split<=0||split==choice.length()-1||!choice.substring(0,split).matches("[^@]+@[0-9a-fA-F]+"))throw new IllegalArgumentException("Expected --choose SCRIPT@HEX=LABEL");String site=choice.substring(0,split).toLowerCase(Locale.ROOT);if(overrides.put(site,choice.substring(split+1))!=null)throw new IllegalArgumentException("Duplicate choice override "+site);break;}case "--steps":stepBudget=Integer.parseInt(args[at+1]);break;default:throw new IllegalArgumentException("Unknown route audit option "+args[at]);}}
        if(route<1||stepBudget<1)throw new IllegalArgumentException("Route and step budget must be positive");
        Path game=Paths.get(args[0]).toRealPath(),out=outputOutsideGame(game,args[1]);
        Files.deleteIfExists(out.resolve("result.json"));Files.deleteIfExists(out.resolve("failure.txt"));
        Set<String> scripts=new LinkedHashSet<>();List<String> choices=new ArrayList<>();Map<String,Integer> menuVisits=new HashMap<>();List<String> loopEscapes=new ArrayList<>();int lines=0;String previous="",lastWhere="";int stuck=0;boolean started=false,ended=false;
        int startupAt=0;
        try(DesktopRuntime app=new DesktopRuntime(game,true,executable);BufferedWriter log=Files.newBufferedWriter(out.resolve("trace.jsonl"),StandardCharsets.UTF_8)){
            DesktopEngine e=app.engine;if(e instanceof DesktopUnity)throw new IOException("This route audit covers non-Unity engines");
            try{
                for(int step=0;step<stepBudget;step++){
                    FlHost fl=e instanceof DesktopLegacy&&((DesktopLegacy)e).host() instanceof FlHost?(FlHost)((DesktopLegacy)e).host():null;
                    // Native FL timers coalesce; a ten-minute jump can skip the
                    // slideshow's indices. Use its actual poll interval here.
                    // Other FL waits use bounded time as well: thousands of
                    // ten-minute jumps would manufacture signed uptime wrap.
                    e.tick(fl==null?600000:fl.flCredits.active?FlCredits.POLL_MS:fl.flActorMotion.active?16:fl.flComposition.active?40:1000);MesVm m=e.vm();String where=m.scriptName()+"@"+Integer.toHexString(m.offset()),progress=progress(e,where);
                    if(progress.equals(lastWhere)){if(++stuck>2000){String detail="";if(e instanceof DesktopLegacy){Dc2Host h=((DesktopLegacy)e).host();detail=" remaining="+h.remainingMillis()+" text="+h.textWaiting+" presentation="+h.presentationWaiting+" mask="+h.audioMask+" movie="+h.movie+" actions="+Arrays.toString(h.ui.actions);}throw new IOException("No progress "+where+" status="+m.status()+detail);}}else{stuck=0;lastWhere=progress;}
                    if(scripts.add(m.scriptName())){System.out.println("SCRIPT "+m.scriptName()+" lines="+lines);e.render();}
                    if(e.menuWaiting()){
                        List<String> actions=new ArrayList<>();for(DesktopEngine.Control b:e.controls())actions.add(b.action);
                        if(e instanceof DesktopLegacy&&((DesktopLegacy)e).host() instanceof PcHost){PcHost h=(PcHost)((DesktopLegacy)e).host();if(h.pc1!=null&&h.pc1.waitKind()==9&&!h.pc1.historyActive()){Set<String> labels=new HashSet<>();for(int n=h.pc1.storyPrefix().size();n<h.ui.buttons.size();n++)if(!h.ui.buttons.get(n).action.isEmpty())labels.add(h.ui.buttons.get(n).action);actions.retainAll(labels);}}
                        String selected=null;
                        if(lines==0&&startupAt<startup.size()){selected=startup.get(startupAt++);if(!actions.contains(selected))throw new IOException("Requested original startup action absent at "+where+": "+selected+" from "+actions);if(selected.equals("start")||selected.equals("sce_start"))started=true;}
                        else if(!started){for(String a:new String[]{"window","03","sce_start","start"})if(actions.contains(a)){selected=a;break;}if(selected!=null&&(selected.equals("start")||selected.equals("sce_start")))started=true;}
                        else if(lines==0&&actions.contains(String.format("sel%02d",route)))selected=String.format("sel%02d",route);
                        else if(lines==0&&actions.contains("sceselect01"))selected="sceselect01";
                        else if(lines==0&&actions.contains("menu01"))selected="menu01";
                        else if(lines>0&&(actions.contains("start")||actions.contains("sce_start"))){ended=true;break;}
                        else if(lines>0&&!actions.isEmpty()){selected=actions.contains("@map:yes")?"@map:yes":actions.contains("@dc2map:yes")?"@dc2map:yes":actions.get(last?actions.size()-1:0);e.render();}
                        if(e instanceof DesktopLegacy&&((DesktopLegacy)e).host() instanceof PcHost){PcHost h=(PcHost)((DesktopLegacy)e).host();int actor=Integer.getInteger("dc.mapCharacter",-1);if(h.map.active&&h.map.selected<0&&actor>=0){int preferred=e.vm().variables.read(5,actor)!=0?e.vm().variables.read(5,20+actor):-1;if(preferred<0)for(int k=0;k<10;k++)if(e.vm().variables.read(5,k)!=0){preferred=e.vm().variables.read(5,20+k);break;}String a="@map:"+preferred;if(actions.contains(a))selected=a;javax.imageio.ImageIO.write(e.render(),"png",out.resolve("map.png").toFile());}String point="pos"+Integer.getInteger("dc.pcPoint",-1);if(actions.contains(point))selected=point;}
                        String site=where.toLowerCase(Locale.ROOT);if(overrides.containsKey(site)){selected=overrides.get(site);if(!actions.contains(selected))throw new IOException("Requested original choice absent at "+where+": "+selected+" from "+actions);consumedOverrides.add(site);}
                        if(e instanceof DesktopLegacy&&((DesktopLegacy)e).host() instanceof PcHost){PcHost h=(PcHost)((DesktopLegacy)e).host();int actor=Integer.getInteger("dc.mapCharacter",-1);if(h.resources.profileId.equals("dc2-3363")&&h.dc2Map.active&&h.dc2Map.selected<0&&actor>=0&&!overrides.containsKey(where.toLowerCase(Locale.ROOT))){if(actor>=6)throw new IOException("DC2 map actor must be0..5");String preferred=e.vm().variables.read(5,actor)==0?"":"@dc2map:"+e.vm().variables.read(5,10+actor);if(!actions.contains(preferred))for(int n=0;n<6;n++){String candidate="@dc2map:"+e.vm().variables.read(5,10+n);if(e.vm().variables.read(5,n)!=0&&actions.contains(candidate)){preferred=candidate;break;}}if(actions.contains(preferred))selected=preferred;}}
                        if(selected==null)throw new IOException("Unresolved original menu "+where+" "+actions);
                        int visits=menuVisits.getOrDefault(where,0)+1;menuVisits.put(where,visits);if(visits>8){if(!Boolean.getBoolean("dc.breakChoiceLoops")||actions.size()<2)throw new IOException("Choice policy loops at "+where+"; alternate original choice required");selected=actions.get((actions.indexOf(selected)+1)%actions.size());loopEscapes.add(where+"="+selected);menuVisits.put(where,0);}
                        if(lines>0)choices.add(where+"="+selected);
                        log.write(Json.text(Json.obj("menu",where,"selected",selected,"actions",actions)));log.newLine();e.choose(selected);continue;
                    }
                    boolean stable=e instanceof DesktopLegacy?((DesktopLegacy)e).host().textWaiting:e.textWaiting();
                    if(stable&&!where.equals(previous)){previous=where;lines++;started=true;if(lines%1000==0){e.render();byte[] saved=e.snapshot();e.restore(saved);}}
                    for(int ch=0;ch<6;ch++)e.audioCompleted(ch,e.audioGeneration(ch));
                    m=e.vm();m.signalHost(e.ticket(),MesVm.HostSignal.MEDIA_COMPLETE);m.signalHost(e.ticket(),MesVm.HostSignal.TEXT_COMPLETE);
                    if(m.status()==MesVm.Status.WAIT_HOST&&(lines==0||m.scriptName().equalsIgnoreCase("MAIN")||e.textWaiting()||(e instanceof DesktopDd&&((DesktopDd)e).host.remainingMillis()<0)||(e instanceof DesktopLegacy&&((DesktopLegacy)e).host().remainingMillis()<0)))e.click(e.width/2,e.height/2,false);
                    if(m.status()==MesVm.Status.FAULT)throw new IOException(m.error());
                    if(m.status()==MesVm.Status.HALTED)throw new IOException("Unexpected halt "+where);
                }
                if(!ended)throw new IOException("Route budget exhausted");
                if(startupAt!=startup.size())throw new IOException("Unused requested startup actions");
                if(consumedOverrides.size()!=overrides.size())throw new IOException("Unused requested choice overrides");
                if(e.resources.profile.id.equals("dcpc-2063")&&!scripts.stream().anyMatch(s->s.equalsIgnoreCase("STAFF")||s.equalsIgnoreCase("BADEND")))throw new IOException("Title return without a verified PC1 ending; error fallback is not a completed route");
            }catch(Exception failure){Files.write(out.resolve("failure.txt"),(e.vm().scriptName()+"@"+Integer.toHexString(e.vm().offset())+"\n"+failure+"\n").getBytes(StandardCharsets.UTF_8));throw failure;}
            Map<String,Object> result=Json.obj("profile",e.resources.profile.id,"policy",last?"last":"first","choice_loop_exits",loopEscapes,"choice_overrides",overrides,"map_actor",Integer.getInteger("dc.mapCharacter",-1),"audit_hour",System.getProperty("dc.auditHour"),"dd_route",route,"startup_actions",startup,"static_executable_selection",executable,"step_budget",stepBudget,"dialogues",lines,"scripts",scripts,"choices",choices,"returned_to_title",ended,"media_completion_simulated",true,"original_exe_run",false);
            if(e.resources.profileId.equals("dc2-3363")){Map<String,Integer> clear=new LinkedHashMap<>();for(int index:new int[]{1,2,3,4,5,6,59})clear.put("100:"+index,e.vm().variables.read(100,index));result.put("dc2_clear_flags",clear);List<String> endingScripts=new ArrayList<>();for(String script:scripts)if(script.toLowerCase(Locale.ROOT).matches("ed_(anz|kko|mnt|nnk|otm|yme)|gend(_s)?|.*_ed[0-9]+"))endingScripts.add(script);result.put("dc2_ending_scripts",endingScripts);result.put("dialogue_count_scope","Observed stable text waits; original fast/automatic mode can release additional lines inside a tick.");}
            if(e.resources.profileId.equals("dc2fl-9c69")){Map<String,Integer> clear=new LinkedHashMap<>();for(int index=1;index<=13;index++)clear.put("100:"+index,e.vm().variables.read(100,index));for(int index:new int[]{52,53})clear.put("100:"+index,e.vm().variables.read(100,index));result.put("fl_clear_flags",clear);result.put("ending_poll_millis",FlCredits.POLL_MS);}
            result.put("dialogue_count_scope","Observed stable text waits; original fast/automatic mode can release additional lines inside a tick.");
            Files.write(out.resolve("result.json"),(Json.text(result)+"\n").getBytes(StandardCharsets.UTF_8));System.out.println("PASS lines="+lines+" scripts="+scripts.size()+" choices="+choices.size());
        }
    }
}
