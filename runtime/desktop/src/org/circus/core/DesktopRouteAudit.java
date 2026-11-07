package org.circus.core;

import java.io.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Runs original title-to-ending control flow. Media completion is explicitly simulated. */
public final class DesktopRouteAudit {
    private static Path outputOutsideGame(Path game,String requested)throws IOException{
        Path destination=Paths.get(requested).toAbsolutePath().normalize(),existing=destination;
        while(!Files.exists(existing))existing=existing.getParent();
        destination=existing.toRealPath().resolve(existing.relativize(destination)).normalize();
        if(destination.startsWith(game))throw new IOException("Route audit output must be outside the original game directory");
        Files.createDirectories(destination);return destination;
    }
    public static void main(String[] args)throws Exception{
        if(args.length<2)throw new IllegalArgumentException("DesktopRouteAudit GAME OUTPUT [first|last] [ROUTE] [--exe relative.exe] [--action LABEL]... [--steps N]");
        int at=2,route=1,stepBudget=1000000;boolean last=false;String executable="";List<String> startup=new ArrayList<>();
        if(at<args.length&&!args[at].startsWith("--")){if(!args[at].equals("first")&&!args[at].equals("last"))throw new IllegalArgumentException("Expected first or last choice policy");last=args[at++].equals("last");}
        if(at<args.length&&!args[at].startsWith("--"))route=Integer.parseInt(args[at++]);
        for(;at<args.length;at+=2){if(at+1>=args.length)throw new IllegalArgumentException("Missing value for "+args[at]);switch(args[at]){case "--exe":executable=args[at+1];break;case "--action":startup.add(args[at+1]);break;case "--steps":stepBudget=Integer.parseInt(args[at+1]);break;default:throw new IllegalArgumentException("Unknown route audit option "+args[at]);}}
        if(route<1||stepBudget<1)throw new IllegalArgumentException("Route and step budget must be positive");
        Path game=Paths.get(args[0]).toRealPath(),out=outputOutsideGame(game,args[1]);
        Set<String> scripts=new LinkedHashSet<>();List<String> choices=new ArrayList<>();Map<String,Integer> menuVisits=new HashMap<>();List<String> loopEscapes=new ArrayList<>();int lines=0;String previous="",lastWhere="";int stuck=0;boolean started=false,ended=false;
        int startupAt=0;
        try(DesktopRuntime app=new DesktopRuntime(game,true,executable);BufferedWriter log=Files.newBufferedWriter(out.resolve("trace.jsonl"),StandardCharsets.UTF_8)){
            DesktopEngine e=app.engine;if(e instanceof DesktopUnity)throw new IOException("This route audit covers non-Unity engines");
            try{
                for(int step=0;step<stepBudget;step++){
                    e.tick(600000);MesVm m=e.vm();String where=m.scriptName()+"@"+Integer.toHexString(m.offset());
                    if(where.equals(lastWhere)){if(++stuck>2000){String detail="";if(e instanceof DesktopLegacy){Dc2Host h=((DesktopLegacy)e).host();detail=" remaining="+h.remainingMillis()+" text="+h.textWaiting+" presentation="+h.presentationWaiting+" mask="+h.audioMask+" movie="+h.movie+" actions="+Arrays.toString(h.ui.actions);}throw new IOException("No progress "+where+" status="+m.status()+detail);}}else{stuck=0;lastWhere=where;}
                    if(scripts.add(m.scriptName())){System.out.println("SCRIPT "+m.scriptName()+" lines="+lines);e.render();}
                    if(e.menuWaiting()){
                        List<String> actions=new ArrayList<>();for(DesktopEngine.Control b:e.controls())actions.add(b.action);
                        String selected=null;
                        if(lines==0&&startupAt<startup.size()){selected=startup.get(startupAt++);if(!actions.contains(selected))throw new IOException("Requested original startup action absent at "+where+": "+selected+" from "+actions);if(selected.equals("start")||selected.equals("sce_start"))started=true;}
                        else if(!started){for(String a:new String[]{"window","03","sce_start","start"})if(actions.contains(a)){selected=a;break;}if(selected!=null&&(selected.equals("start")||selected.equals("sce_start")))started=true;}
                        else if(lines==0&&actions.contains(String.format("sel%02d",route)))selected=String.format("sel%02d",route);
                        else if(lines==0&&actions.contains("sceselect01"))selected="sceselect01";
                        else if(lines==0&&actions.contains("menu01"))selected="menu01";
                        else if(lines>0&&(actions.contains("start")||actions.contains("sce_start"))){ended=true;break;}
                        else if(lines>0&&!actions.isEmpty()){selected=actions.contains("@map:yes")?"@map:yes":actions.get(last?actions.size()-1:0);choices.add(where+"="+selected);e.render();}
                        if(e instanceof DesktopLegacy&&((DesktopLegacy)e).host() instanceof PcHost){PcHost h=(PcHost)((DesktopLegacy)e).host();int actor=Integer.getInteger("dc.mapCharacter",-1);if(h.map.active&&h.map.selected<0&&actor>=0){int preferred=e.vm().variables.read(5,actor)!=0?e.vm().variables.read(5,20+actor):-1;if(preferred<0)for(int k=0;k<10;k++)if(e.vm().variables.read(5,k)!=0){preferred=e.vm().variables.read(5,20+k);break;}String a="@map:"+preferred;if(actions.contains(a))selected=a;javax.imageio.ImageIO.write(e.render(),"png",out.resolve("map.png").toFile());}String point="pos"+Integer.getInteger("dc.pcPoint",-1);if(actions.contains(point))selected=point;}
                        if(selected==null)throw new IOException("Unresolved original menu "+where+" "+actions);
                        int visits=menuVisits.getOrDefault(where,0)+1;menuVisits.put(where,visits);if(visits>8){if(!Boolean.getBoolean("dc.breakChoiceLoops")||actions.size()<2)throw new IOException("Choice policy loops at "+where+"; alternate original choice required");selected=actions.get((actions.indexOf(selected)+1)%actions.size());loopEscapes.add(where+"="+selected);menuVisits.put(where,0);}
                        log.write(Json.text(Json.obj("menu",where,"selected",selected,"actions",actions)));log.newLine();e.choose(selected);continue;
                    }
                    boolean stable=e instanceof DesktopLegacy?((DesktopLegacy)e).host().textWaiting:e.textWaiting();
                    if(stable&&!where.equals(previous)){previous=where;lines++;if(lines%1000==0){e.render();byte[] saved=e.snapshot();e.restore(saved);}}
                    for(int ch=0;ch<6;ch++)e.audioCompleted(ch,e.audioGeneration(ch));
                    m=e.vm();m.signalHost(e.ticket(),MesVm.HostSignal.MEDIA_COMPLETE);m.signalHost(e.ticket(),MesVm.HostSignal.TEXT_COMPLETE);
                    if(m.status()==MesVm.Status.WAIT_HOST&&(lines==0||m.scriptName().equalsIgnoreCase("MAIN")||e.textWaiting()||(e instanceof DesktopDd&&((DesktopDd)e).host.remainingMillis()<0)||(e instanceof DesktopLegacy&&((DesktopLegacy)e).host().remainingMillis()<0)))e.click(e.width/2,e.height/2,false);
                    if(m.status()==MesVm.Status.FAULT)throw new IOException(m.error());
                    if(m.status()==MesVm.Status.HALTED)throw new IOException("Unexpected halt "+where);
                }
                if(!ended)throw new IOException("Route budget exhausted");
                if(startupAt!=startup.size())throw new IOException("Unused requested startup actions");
                if(e.resources.profile.id.equals("dcpc-2063")&&!scripts.stream().anyMatch(s->s.equalsIgnoreCase("STAFF")||s.equalsIgnoreCase("BADEND")))throw new IOException("Title return without a verified PC1 ending; error fallback is not a completed route");
            }catch(Exception failure){Files.write(out.resolve("failure.txt"),(e.vm().scriptName()+"@"+Integer.toHexString(e.vm().offset())+"\n"+failure+"\n").getBytes(StandardCharsets.UTF_8));throw failure;}
            Map<String,Object> result=Json.obj("profile",e.resources.profile.id,"policy",last?"last":"first","choice_loop_exits",loopEscapes,"dd_route",route,"startup_actions",startup,"static_executable_selection",executable,"step_budget",stepBudget,"dialogues",lines,"scripts",scripts,"choices",choices,"returned_to_title",ended,"media_completion_simulated",true,"original_exe_run",false);
            Files.write(out.resolve("result.json"),(Json.text(result)+"\n").getBytes(StandardCharsets.UTF_8));System.out.println("PASS lines="+lines+" scripts="+scripts.size()+" choices="+choices.size());
        }
    }
}
