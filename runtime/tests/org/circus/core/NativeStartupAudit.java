package org.circus.core;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Original-data startup smoke: portable VM only, original files read-only, no prose export. */
public final class NativeStartupAudit {
    private static final class Engine {
        final CircusResources data;final TextCodec codec;MesVm vm;Dc3Host modern;Dc2Session legacy;
        Engine(CircusResources data,TextCodec codec)throws IOException{
            this.data=data;this.codec=codec;
            if(Dc3Host.supports(data.profile)){modern=new Dc3Host(data,codec,true);vm=new MesVm(data.scripts(),modern,"START");}
            else{legacy=new Dc2Session(data,codec);vm=legacy.vm;}
        }
        long ticket(){return modern!=null?modern.waitTicket():legacy.host.waitTicket();}
        boolean menu(){return modern!=null?modern.ui.waiting:legacy.host.ui.waiting;}
        boolean text(){return modern!=null?modern.dialogue.waiting()&&!modern.dialogue.shownText.isEmpty():legacy.host.textWaiting&&!legacy.host.body.isEmpty();}
        List<String> actions(){List<String> result=new ArrayList<>();if(modern!=null){for(Dc3Ui.Button b:modern.ui.current.buttons)result.add(b.action);}else for(Dc2Ui.Button b:legacy.host.ui.buttons)result.add(b.action);return result;}
        void choose(String action)throws IOException{
            int index=actions().indexOf(action);if(index<0)throw new IOException("Requested native action is absent: "+action);
            if(!vm.activateMenu(ticket(),index))throw new IOException("Native action refused: "+action);
        }
        void media(){if(modern!=null){for(int ch=0;ch<6;ch++)modern.audioCompleted(ch,modern.audioGeneration[ch]);}
            else for(int ch=0;ch<6;ch++)legacy.host.audioCompleted(ch,legacy.host.audioGeneration[ch]);}
        void advance()throws IOException{
            long remaining=modern!=null?modern.remainingMillis():legacy.host.remainingMillis();
            vm.signalHost(ticket(),MesVm.HostSignal.MEDIA_COMPLETE);vm.elapse(Math.max(1,remaining));
            vm.signalHost(ticket(),MesVm.HostSignal.TEXT_COMPLETE);vm.signalHost(ticket(),MesVm.HostSignal.ADVANCE);
        }
        boolean timedAdvance()throws IOException{
            if(legacy!=null&&legacy.host instanceof PcHost&&((PcHost)legacy.host).hasTimedInput()&&!legacy.host.ui.actions[1].isEmpty())return vm.activateMenu(ticket(),-2);
            return false;
        }
    }
    private final CircusResources data;private final TextCodec codec;private Engine engine;
    private final List<String> requested;private final List<Integer> choices;
    private final Map<String,Object> report=new LinkedHashMap<>();
    private final List<Object> menuTrace=new ArrayList<>(),imageTrace=new ArrayList<>(),audioTrace=new ArrayList<>();
    private final Set<String> images=new HashSet<>(),audio=new HashSet<>(),scripts=new LinkedHashSet<>();
    private final Set<String> channelAudio=new HashSet<>(),channel0Resources=new TreeSet<>();private final int[] audioChannels=new int[6];
    private final Set<UiCapture> captures=Collections.newSetFromMap(new IdentityHashMap<UiCapture,Boolean>());
    private final Set<String> particleResources=new TreeSet<>(),particleStates=new HashSet<>();
    private final List<Object> particleTrace=new ArrayList<>(),particleSnapshotTrace=new ArrayList<>();
    private int liveParticleChecks,captureParticleChecks,particleSnapshots,activeParticleSnapshots;
    private int actionCursor,choiceCursor,lines,snapshots,steps;private String lastLine="",stage="startup",resource="",lastAction="";
    private NativeStartupAudit(CircusResources data,TextCodec codec,List<String> actions,List<Integer> choices)throws IOException{
        this.data=data;this.codec=codec;requested=actions;this.choices=choices;engine=new Engine(data,codec);
        report.put("profile",data.profileId);report.put("encoding",codec.charset.name());
        report.put("execution_contract",data.profile.executionIdentity());
    }
    private void snapshot()throws IOException{
        stage="CVS2 roundtrip";byte[] priorParticles=particleState(engine);byte[] saved=engine.vm.snapshot();
        if(new DataInputStream(new ByteArrayInputStream(saved)).readInt()!=0x43565332)throw new IOException("Expected CVS2 snapshot");
        Engine restored=new Engine(data,codec);restored.vm.restore(saved);
        if(!Arrays.equals(saved,restored.vm.snapshot()))throw new IOException("New VM/host CVS2 restore differs byte for byte");
        if(!Arrays.equals(priorParticles,particleState(restored)))throw new IOException("DC2 native particle state changed on new-owner CVS2 restore");
        if(priorParticles.length!=0){Dc2Particles value=engine.legacy.host.scene.nativeParticles;int active=0;for(int n=0;n<50;n++)if(value.slot(n)[2]!=0)active++;particleSnapshots++;if(active!=0)activeParticleSnapshots++;Map<String,Object> item=new LinkedHashMap<>();item.put("script",engine.vm.scriptName());item.put("pc",engine.vm.offset());item.put("kind",value.kind());item.put("active_slots",active);item.put("native_particle_state_sha256",Binary.sha256(priorParticles));particleSnapshotTrace.add(item);}
        engine=restored;snapshots++;
    }
    private static byte[] particleBytes(Dc2Particles value)throws IOException{ByteArrayOutputStream bytes=new ByteArrayOutputStream();value.save(new DataOutputStream(bytes));return bytes.toByteArray();}
    private static byte[] particleState(Engine value)throws IOException{
        if(!value.data.profileId.equals("dc2-3363"))return new byte[0];
        if(value.legacy==null||value.legacy.host.scene.nativeParticles==null)throw new IOException("DC2 original host has no native particle owner");
        return particleBytes(value.legacy.host.scene.nativeParticles);
    }
    private void particles(Dc2Particles value,boolean live)throws IOException{
        if(value==null){if(live&&data.profileId.equals("dc2-3363"))throw new IOException("DC2 original host has no native particle owner");return;}
        stage="native particle state";byte[] saved=particleBytes(value);DataInputStream input=new DataInputStream(new ByteArrayInputStream(saved));Dc2Particles loaded=Dc2Particles.load(input,null);
        if(input.available()!=0||!Arrays.equals(saved,particleBytes(loaded)))throw new IOException("Native particle state has no lossless validated frozen projection");
        if(live)liveParticleChecks++;else captureParticleChecks++;
        if(value.loaded()){image(value.atlas());particleResources.add(value.atlas());}
        String sha=Binary.sha256(saved);if(!live||!particleStates.add(sha))return;
        int active=0;for(int n=0;n<50;n++)if(value.slot(n)[2]!=0)active++;
        Map<String,Object> item=new LinkedHashMap<>();item.put("script",engine.vm.scriptName());item.put("pc",engine.vm.offset());item.put("kind",value.kind());item.put("strength",value.strength());item.put("resource",value.atlas());item.put("active_slots",active);item.put("timer_active",value.timerActive());item.put("timer_remainder_ms",value.remainder());item.put("busy",value.busy());item.put("alternate_toggle",value.toggle());item.put("state_sha256",sha);particleTrace.add(item);
    }
    private void image(String ref)throws IOException{
        if(ref==null||ref.isEmpty()||ref.equals("-"))return;
        if(engine.modern!=null)ref=engine.modern.uiSurface(ref);
        if(ref==null||ref.isEmpty())return;
        if(ref.equals("@ui-capture")){capture(engine.modern!=null?engine.modern.uiCapture:engine.legacy.host.uiCapture);return;}
        if(!images.add(ref))return;stage="image";resource=ref;
        Crx.Image decoded=data.image(ref);decoded.argb();
        Map<String,Object> item=new LinkedHashMap<>();item.put("resource",ref);item.put("width",decoded.width);item.put("height",decoded.height);item.put("bpp",decoded.bpp);imageTrace.add(item);
    }
    private void modernFrame(Dc3Scene.Frame frame)throws IOException{
        for(Dc3Scene.Layer layer:frame.layers)image(layer.resource);for(Dc3Scene.Quad quad:frame.quads)image(quad.resource);
    }
    private void modernPanel(Dc3Ui.Panel panel)throws IOException{
        if(panel==null||panel.hidden)return;for(Dc3Scene.Quad q:panel.quads)image(q.resource);
        for(Dc3Ui.Button b:panel.buttons)for(Dc3Ui.Sprite sprite:b.sprites)if(sprite.state!=5)image(sprite.quad.resource);
    }
    private void legacyQuad(Dc2Ui.Quad quad,String[] slots)throws IOException{if(quad.type==0&&quad.slot()<slots.length)image(slots[quad.slot()]);}
    private void legacyFrame(Dc2Scene.Frame frame)throws IOException{
        image(frame.slots[1]);for(int slot=2;slot<=9;slot++)image(frame.slots[slot]);
        for(Dc2Ui.Quad q:frame.quads)legacyQuad(q,frame.slots);
    }
    private void capture(UiCapture capture)throws IOException{
        if(capture==null)throw new IOException("Visible capture has no texture value");if(!captures.add(capture))return;
        if(capture.previous!=null)capture(capture.previous);
        if(capture.kind==UiCapture.ARGB){capture.pixels();return;}
        try(DataInputStream input=capture.input()){
            if(capture.kind==UiCapture.LEGACY){SceneCaptures.Legacy value=new SceneCaptures.Legacy(input);legacyFrame(value.frame);particles(value.particles,false);}
            else{SceneCaptures.Dd value=new SceneCaptures.Dd(input);modernFrame(value.frame);modernPanel(value.panel);}
            if(input.available()!=0)throw new IOException("Extra visible logical capture payload");
        }
    }
    private void motion(Dc2Motion value)throws IOException{
        if(value==null)return;if(value.previous!=null)motion(value.previous);if(value.initial!=null)capture(value.initial);if(value.prepared!=null)capture(value.prepared);legacyFrame(value.source);
    }
    private void resources()throws IOException{
        if(engine.modern!=null){
            Dc3Host host=engine.modern;modernFrame(host.scene.published);modernPanel(host.ui.current);modernPanel(host.ui.underlay);
            if(host.scene.fade.active()&&!host.scene.fade.mask.isEmpty())image(host.scene.fade.mask);
            for(String ref:host.ppScrollCredits.resources())image(ref);
        }else{
            Dc2Host host=engine.legacy.host;legacyFrame(host.scene.shown);particles(host.scene.nativeParticles,true);motion(host.scene.nativeMotion);
            if(host instanceof PcHost&&((PcHost)host).frozenOriginalBatch()!=null)capture(((PcHost)host).frozenOriginalBatch());
            if(host.ui.waiting)for(Dc2Ui.Button b:host.ui.buttons)for(Dc2Ui.Quad q:b.sprites)legacyQuad(q,host.scene.slots);
            if(!host.body.isEmpty())for(int t=1;t<=3;t++)for(Dc2Ui.Quad q:host.ui.quadTemplates.get(t))legacyQuad(q,host.scene.slots);
        }
        for(int ch=0;ch<6;ch++){
            String raw=engine.modern!=null?engine.modern.audio[ch]:engine.legacy.host.audio[ch];
            String ref=engine.modern!=null?engine.modern.audioResource(ch):raw;
            if(!ref.isEmpty()&&channelAudio.add(ch+":"+ref))audioChannels[ch]++;
            if(ch==0&&!ref.isEmpty())channel0Resources.add(ref);
            if(ref.isEmpty()||!audio.add(ref))continue;stage="audio";resource=ref;
            Xpcm decoded=data.audio(ref);Map<String,Object> item=new LinkedHashMap<>();
            item.put("channel",ch);item.put("raw_reference",raw);item.put("runtime_reference",ref);item.put("codec",decoded.codec);item.put("payload_bytes",decoded.payload.length);audioTrace.add(item);
        }
        stage="VM";resource="";
    }
    private Map<String,Object> finish(String status,String error){
        report.put("status",status);report.put("error",error);report.put("stage",stage);report.put("resource",resource);
        report.put("script",engine.vm.scriptName());report.put("pc",engine.vm.offset());report.put("vm_status",engine.vm.status().name());
        report.put("last_action",lastAction);report.put("requested_actions",requested);report.put("actions_consumed",actionCursor);report.put("menu_actions",engine.menu()?engine.actions():Collections.emptyList());
        report.put("lines",lines);report.put("steps",steps);report.put("snapshot_checks",snapshots);report.put("scripts",scripts);
        report.put("image_count",imageTrace.size());report.put("audio_count",audioTrace.size());report.put("images",imageTrace);report.put("audio",audioTrace);report.put("menus",menuTrace);
        Map<String,Object> channelCounts=new LinkedHashMap<>();for(int ch=0;ch<6;ch++)channelCounts.put(Integer.toString(ch),audioChannels[ch]);
        report.put("audio_resources_by_channel",channelCounts);report.put("channel0_audio_reference_observed",audioChannels[0]>0);
        report.put("channel0_resource_references",channel0Resources);report.put("bgm_playback_verified",false);
        report.put("native_particle_resources_checked",particleResources);report.put("live_particle_state_checks",liveParticleChecks);report.put("capture_particle_state_checks",captureParticleChecks);report.put("native_particle_snapshot_checks",particleSnapshots);report.put("active_particle_snapshot_checks",activeParticleSnapshots);report.put("native_particle_states",particleTrace);report.put("native_particle_snapshots",particleSnapshotTrace);
        report.put("persistent_state","fresh runtime defaults; original and portable progress files were not loaded");
        report.put("scope","START/native-action/at-most-20-dialogue-waits; original files read-only; no native EXE executed; no original prose exported; media completion simulated; CRX pixels and XPCM/container decoding checked; DC2 native particle/capture state validated and snapshot-compared; no original visual/audio fidelity comparison");
        report.put("complete_engine_fidelity_verified",false);return report;
    }
    private Map<String,Object> run(int target,int budget){
        try{
            for(steps=0;steps<budget;steps++){
                scripts.add(engine.vm.scriptName());stage="VM";MesVm.Status status=engine.vm.run(1);
                if(status==MesVm.Status.FAULT)throw new IOException(engine.vm.error());
                if(status==MesVm.Status.HALTED)throw new IOException("Unexpected terminal stop before requested dialogue waits");
                if(status!=MesVm.Status.WAIT_HOST&&status!=MesVm.Status.WAIT_CHOICE)continue;
                resources();
                if(engine.menu()){
                    Map<String,Object> item=new LinkedHashMap<>();item.put("script",engine.vm.scriptName());item.put("pc",engine.vm.offset());item.put("actions",engine.actions());menuTrace.add(item);
                    snapshot();
                    if(actionCursor>=requested.size())return finish("awaiting_native_menu_action","");
                    String action=requested.get(actionCursor++);lastAction=action;stage="native menu action";engine.choose(action);continue;
                }
                if(status==MesVm.Status.WAIT_CHOICE){
                    if(engine.modern==null)throw new IOException("Legacy choice did not expose its native menu");
                    if(choiceCursor>=choices.size())return finish("awaiting_choice_index","");
                    int choice=choices.get(choiceCursor++);if(choice<0||choice>=engine.modern.choices().size())throw new IOException("Choice index out of range");
                    engine.vm.choose(engine.modern.choices().get(choice).label(),choice);continue;
                }
                if(engine.text()){
                    String key=engine.vm.scriptName()+":"+engine.vm.offset()+":"+engine.ticket();
                    if(!key.equals(lastLine)){lastLine=key;lines++;if(lines==1||lines==target)snapshot();}
                    if(lines>=target)return finish("passed","");
                }
                engine.media();if(engine.timedAdvance())continue;engine.advance();
            }
            return finish("failed","Instruction budget exhausted");
        }catch(Exception failure){return finish("failed",failure.getClass().getSimpleName()+": "+failure.getMessage());}
    }
    public static void main(String[] args)throws Exception{
        if(args.length==0)throw new IllegalArgumentException("NativeStartupAudit GAME [--exe FILE] [--lines 1..20] [--steps N] [--action LABEL]... [--choice INDEX]...");
        String executable="@none";int lines=20,budget=200000;List<String> actions=new ArrayList<>();List<Integer> choices=new ArrayList<>();
        for(int i=1;i<args.length;i++){
            if(i+1>=args.length)throw new IllegalArgumentException("Missing option value: "+args[i]);String value=args[++i];
            switch(args[i-1]){case "--exe":executable=value;break;case "--lines":lines=Integer.parseInt(value);break;case "--steps":budget=Integer.parseInt(value);break;case "--action":actions.add(value);break;case "--choice":choices.add(Integer.parseInt(value));break;default:throw new IllegalArgumentException("Unknown option: "+args[i-1]);}
        }
        if(lines<1||lines>20||budget<1||budget>1000000)throw new IllegalArgumentException("Audit bounds exceed scope");
        Map<String,Object> report;
        try{
            GameResources files=new GameResources(new LocalGameStorage(Paths.get(args[0])),executable);Profile profile=GameVersion.detect(files).profile();
            CircusResources data=new CircusResources(files,profile);report=new NativeStartupAudit(data,GameText.detect(files,profile),actions,choices).run(lines,budget);
        }catch(Exception failure){report=new LinkedHashMap<>();report.put("status","failed");report.put("stage","initialization");report.put("error",failure.getClass().getSimpleName()+": "+failure.getMessage());}
        report.put("game",Paths.get(args[0]).toAbsolutePath().normalize().toString());report.put("executable_selection_for_static_tables_only",executable);
        System.out.write((json(report)+"\n").getBytes(StandardCharsets.UTF_8));if("failed".equals(report.get("status")))System.exit(1);
    }
    private static String json(Object value){
        if(value==null)return "null";if(value instanceof Number||value instanceof Boolean)return value.toString();
        if(value instanceof Map){StringBuilder b=new StringBuilder("{");boolean first=true;for(Map.Entry<?,?> entry:((Map<?,?>)value).entrySet()){if(!first)b.append(',');first=false;b.append(json(entry.getKey().toString())).append(':').append(json(entry.getValue()));}return b.append('}').toString();}
        if(value instanceof Collection){StringBuilder b=new StringBuilder("[");boolean first=true;for(Object item:(Collection<?>)value){if(!first)b.append(',');first=false;b.append(json(item));}return b.append(']').toString();}
        StringBuilder b=new StringBuilder("\"");for(char c:value.toString().toCharArray())switch(c){case '\\':b.append("\\\\");break;case '"':b.append("\\\"");break;case '\n':b.append("\\n");break;case '\r':b.append("\\r");break;case '\t':b.append("\\t");break;default:if(c<32)b.append(String.format(Locale.ROOT,"\\u%04x",(int)c));else b.append(c);}return b.append('"').toString();
    }
}
