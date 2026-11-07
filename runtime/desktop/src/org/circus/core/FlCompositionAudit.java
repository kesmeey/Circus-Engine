package org.circus.core;

import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import javax.imageio.ImageIO;

/** Original FL prepared surfaces, actual desktop raster, separate native BGR16 oracle.
 * The fixtures exercise native draw journals; they do not claim original-screen equivalence. */
public final class FlCompositionAudit {
    private static int checks;private static long compared;
    private static final int WIDTH=800,HEIGHT=600;
    private static final String[] IMAGES={"ed/aft","ed/drm","ed/ult"};
    private static void check(boolean ok,String why){checks++;if(!ok)throw new AssertionError(why);}
    private static FlHost host(DesktopLegacy engine){return(FlHost)engine.host();}
    private static int[] pixels(BufferedImage image){return image.getRGB(0,0,WIDTH,HEIGHT,null,0,WIDTH);}
    private static String hash(int[] values){byte[] bytes=new byte[values.length*3];for(int n=0;n<values.length;n++)for(int c=0;c<3;c++)bytes[n*3+c]=(byte)(values[n]>>>(8*c));return Binary.sha256(bytes);}
    /** Decode BGR24 directly; the oracle never calls the production raster/blender. */
    private static int[] original(Crx.Image image){
        check(image.bpp==24&&image.x==0&&image.y==0&&image.width==WIDTH&&image.height==HEIGHT,"prepared source is original opaque800x600 BGR24");
        int[] values=new int[WIDTH*HEIGHT];for(int y=0;y<HEIGHT;y++)for(int x=0;x<WIDTH;x++){int at=y*image.stride+x*3;values[y*WIDTH+x]=0xff000000|((image.pixels[at+2]&255)<<16)|((image.pixels[at+1]&255)<<8)|(image.pixels[at]&255);}return values;
    }
    private static final class Call {
        final int kind,weight,left,top,right,bottom;final boolean prepared;
        Call(int k,int w,boolean p,int l,int t,int r,int b){kind=k;weight=w;prepared=p;left=l;top=t;right=r;bottom=b;}
    }
    private static final class Poll {
        final List<Call> calls=new ArrayList<Call>();boolean active;int phase,progress;
        Poll(boolean a,int p,int n){active=a;phase=p;progress=n;}
    }
    /** Independent native call schedule from421D80/4202D0, including empty release poll. */
    private static List<Poll> schedule(boolean prepare,boolean actors,int speed,boolean ctrl,int[] clip){
        List<Poll> out=new ArrayList<Poll>();
        if(prepare){
            int amount=ctrl||speed==3?8:speed==2?4:speed==1?2:1,weight=ctrl||speed==3?10:speed==2?8:speed==1?6:3;
            for(int n=amount;n<=16;n+=amount){boolean last=n==16;Poll poll=new Poll(!last||actors,last?(actors?1:2):0,last&&actors?0:n);poll.calls.add(new Call(1,weight,true,0,0,WIDTH,HEIGHT));if(last)poll.calls.add(new Call(0,16,true,0,0,WIDTH,HEIGHT));out.add(poll);}
        }
        if(actors){
            if(ctrl||speed==3){Poll poll=new Poll(false,2,5);poll.calls.add(new Call(0,16,false,clip[0],clip[1],clip[2],clip[3]));out.add(poll);}
            else{
                int amount=speed==2?3:speed==1?2:1,weight=speed==2?12:speed==1?10:6;
                for(int progress=0;progress<5;){progress=Math.min(5,progress+amount);Poll poll=new Poll(true,1,progress);poll.calls.add(new Call(progress==5?0:1,progress==5?16:weight,false,clip[0],clip[1],clip[2],clip[3]));out.add(poll);}
                out.add(new Poll(false,2,5));
            }
        }
        return out;
    }
    /** 40C220 is clip-only. Every feedback draw accumulates on the prior front. */
    private static int[] oracle(int[][] scenes,List<Call> calls){
        int[] out=scenes[0].clone();for(Call call:calls){int[] source=scenes[call.prepared?1:2];
            for(int y=call.top;y<call.bottom;y++)for(int x=call.left;x<call.right;x++){
                int at=y*WIDTH+x;if(call.kind==0){out[at]=source[at];continue;}
                int old=out[at],value=0xff000000;for(int c=0;c<3;c++){int shift=c*8;value|=((((old>>>shift)&255)*(16-call.weight)+((source[at]>>>shift)&255)*call.weight)>>4)<<shift;}out[at]=value;
            }
        }return out;
    }
    private static void journal(FlComposition owner,List<Call> calls,String name){
        check(owner.draws().size()==calls.size(),name+" native call count");
        for(int n=0;n<calls.size();n++){Call want=calls.get(n);FlComposition.Draw got=owner.draws().get(n);check(got.kind==want.kind&&got.alpha==want.weight&&got.prepared==want.prepared&&got.left==want.left&&got.top==want.top&&got.right==want.right&&got.bottom==want.bottom,name+" native draw parameters "+n);}
    }
    private static Map<String,Object> frame(DesktopLegacy e,int[][] scenes,List<Call> calls,Path out,String name,boolean png)throws IOException {
        FlComposition owner=host(e).flComposition;journal(owner,calls,name);int[] expected=oracle(scenes,calls);BufferedImage image=e.render();int[] actual=pixels(image);
        check(Arrays.equals(actual,expected),name+" independent cumulative BGR16 pixels");compared+=expected.length;
        if(png)ImageIO.write(image,"png",out.resolve(name+".png").toFile());image.flush();
        BufferedImage again=e.render();check(Arrays.equals(actual,pixels(again)),name+" repeated render in same generation never reapplies feedback");again.flush();
        return Json.obj("sample",name,"generation",owner.generation,"phase",owner.phase,"progress",owner.progress,"active",owner.active,"draw_calls",calls.size(),"sha256",hash(actual));
    }
    private static DesktopLegacy fixture(CircusResources data,TextCodec codec,int[][] scenes,boolean prepared,boolean actors,int speed,boolean ctrl,int gate,int[] clip)throws Exception {
        DesktopLegacy e=new DesktopLegacy(data,codec,true);FlHost host=host(e);host.write(99,12,speed);host.write(99,25,gate);host.motionSkipPressed=ctrl;
        host.flComposition.start(UiCapture.pixels(WIDTH,HEIGHT,scenes[0]),UiCapture.pixels(WIDTH,HEIGHT,scenes[2]),prepared?UiCapture.pixels(WIDTH,HEIGHT,scenes[1]):null,host.scene.transitionSerial,actors,clip[0],clip[1],clip[2],clip[3]);
        check(e.vm().run(1)==MesVm.Status.WAIT_HOST,"prepared fixture records actual VM WAIT_HOST before any snapshot");return e;
    }
    private static void runCase(CircusResources data,TextCodec codec,int[][] scenes,Path out,List<Object> frames,String name,boolean prepare,boolean actors,int speed,boolean ctrl,int gate,int[] clip)throws Exception {
        DesktopLegacy e=fixture(data,codec,scenes,prepare,actors,speed,ctrl,gate,clip);List<Poll> polls=schedule(prepare,actors,speed,ctrl&&gate==0,clip);List<Call> calls=new ArrayList<Call>();
        frames.add(frame(e,scenes,calls,out,name+"-initial",false));long sequence=host(e).flComposition.generation;
        for(int n=0;n<polls.size();n++){
            host(e).elapse(n==0?0:40);Poll poll=polls.get(n);calls.addAll(poll.calls);FlComposition owner=host(e).flComposition;
            check(owner.active==poll.active&&owner.phase==poll.phase&&owner.progress==poll.progress,name+" native poll state "+n);
            check(owner.generation==sequence,name+" multiple feedback draws keep native sequence identity");
            frames.add(frame(e,scenes,calls,out,name+"-poll"+n,n==0||n==polls.size()-1));
            if(n==0&&polls.size()>2){
                byte[] saved=e.snapshot();List<Call> futureCalls=new ArrayList<Call>(calls);host(e).elapse(40);futureCalls.addAll(polls.get(n+1).calls);
                BufferedImage future=e.render();int[] futurePixels=pixels(future);future.flush();check(Arrays.equals(futurePixels,oracle(scenes,futureCalls)),name+" future frame before restore matches oracle");
                e.restore(saved);BufferedImage restored=e.render();check(Arrays.equals(pixels(restored),oracle(scenes,calls)),name+" full VM restore recovers mid-feedback front");restored.flush();
                check(Arrays.equals(saved,e.snapshot()),name+" restored VM/native owner bytes are identical");host(e).elapse(40);future=e.render();check(Arrays.equals(pixels(future),futurePixels),name+" restored future frame identical");future.flush();
                e.restore(saved);
            }
        }
        check(!host(e).flComposition.active&&host(e).flComposition.drawn,name+" completed composition retains native final front");
        ByteArrayOutputStream encoded=new ByteArrayOutputStream();host(e).flComposition.save(new DataOutputStream(encoded));FlComposition decoded=new FlComposition();decoded.load(new DataInputStream(new ByteArrayInputStream(encoded.toByteArray())));
        check(decoded.draws().size()==calls.size()&&!decoded.active,name+" completed retained native journal has a valid codec");
    }
    private static void timerCase(CircusResources data,TextCodec codec,int[][] scenes,Path out,List<Object> frames,int[] clip)throws Exception {
        DesktopLegacy e=fixture(data,codec,scenes,false,true,0,false,0,clip);List<Call> calls=new ArrayList<Call>();List<Poll> polls=schedule(false,true,0,false,clip);
        host(e).elapse(0);calls.addAll(polls.get(0).calls);host(e).elapse(39);journal(host(e).flComposition,calls,"sub40ms timer holds");
        host(e).elapse(1);calls.addAll(polls.get(1).calls);host(e).elapse(4000);calls.addAll(polls.get(2).calls);
        check(host(e).flComposition.progress==3,"late native timer event coalesces to one progress update");
        frames.add(frame(e,scenes,calls,out,"three-events-before-first-render",true));
    }
    private static void word(ByteArrayOutputStream bytes,int value){bytes.write(value);bytes.write(value>>>8);}
    private static void opcodeFlow(CircusResources original,TextCodec codec,int[][] scenes,Path out,List<Object> frames,boolean prepare,boolean actors)throws Exception {
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();bytes.write(new byte[]{0,0,0,0,0x69,(byte)0x9c,0x0d,(byte)255,0,0x5c});
        for(int value:new int[]{8,11,91,0})word(bytes,value);bytes.write(new byte[3]);
        final MesScript script=new MesScript("START",bytes.toByteArray(),original.profile);
        CircusResources fixtureData=new CircusResources(original.files,original.profile){@Override public MesVm.Library scripts(){return name->{if(!name.equalsIgnoreCase("START"))throw new IOException("Composition fixture has only START");return script;};}};
        DesktopLegacy e=new DesktopLegacy(fixtureData,codec,true);FlHost h=host(e);h.write(99,12,0);h.write(99,25,0);
        final UiCapture old=UiCapture.pixels(WIDTH,HEIGHT,scenes[0]),middle=UiCapture.pixels(WIDTH,HEIGHT,scenes[1]),target=UiCapture.pixels(WIDTH,HEIGHT,scenes[2]);
        final List<Map<String,Object>> captures=new ArrayList<Map<String,Object>>();h.nativeFrameCapture=()->old;
        h.compositionCapture=(mask,message,hide)->{captures.add(Json.obj("mask",mask,"message",message,"hideActors",hide));return mask==0x0f?middle:target;};
        if(prepare)h.command("pc.background",new int[0],IMAGES[1].getBytes(Binary.SJIS));
        if(actors)h.command("pc.character",new int[]{0},IMAGES[2].getBytes(Binary.SJIS));
        check(e.vm().run(1)==MesVm.Status.WAIT_HOST&&e.vm().offset()==9,"actual0D255 commits continuation PC and yields real VM host wait");
        check(e.vm().variables.read(8,11)==0,"continuation write cannot run before native composition releases");
        check(captures.size()==(prepare?2:1),"0D255 uses independently bound target/prepared capture count");
        if(prepare)check(captures.get(0).get("mask").equals(15)&&captures.get(0).get("message").equals(false)&&captures.get(0).get("hideActors").equals(actors),"native preparation mask0F excludes message and optionally hides dirty actors");
        Map<String,Object> finalCapture=captures.get(captures.size()-1);
        check(finalCapture.get("mask").equals(255)&&finalCapture.get("message").equals(false)&&finalCapture.get("hideActors").equals(false),"native final target maskFF has explicit message gate and visible actors");
        FlComposition owner=h.flComposition;check((owner.prepared!=null)==prepare,"actual0D255 prepare phase matches preceding native producer");
        h.body="fixture";e.click(20,20,true);h=host(e);check(h.flComposition.active&&!e.session.inSystem()&&e.vm().offset()==9,"right click cannot skip active original2B wait or open system context");h.body="";
        check(!e.vm().signalHost(e.ticket(),MesVm.HostSignal.ADVANCE),"ADVANCE cannot release active original composition wait");
        List<Poll> polls=schedule(prepare,actors,0,false,new int[]{0,0,WIDTH,HEIGHT});List<Call> calls=new ArrayList<Call>();
        String name=prepare?(actors?"opcode-both":"opcode-background"):"opcode-actor";
        for(int n=0;n<polls.size();n++){host(e).elapse(n==0?0:40);calls.addAll(polls.get(n).calls);if(n==0||n==polls.size()-1)frames.add(frame(e,scenes,calls,out,name+"-"+n,true));
            if(n==0){byte[] saved=e.snapshot();e.restore(saved);check(e.vm().status()==MesVm.Status.WAIT_HOST&&host(e).flComposition.active,"actual0D255 mid-poll restore retains real VM host wait");}}
        check(!host(e).flComposition.active,"actual0D255 native journal releases after final full-copy poll");
        check(e.vm().run(10)==MesVm.Status.HALTED&&e.vm().variables.read(8,11)==91,"actual0D255 release resumes following original-format variable instruction");
    }
    public static void main(String[] args)throws Exception {
        if(args.length!=2)throw new IOException("Require readonly_FL_GAME owned_OUTPUT");Path game=Paths.get(args[0]).toRealPath(),out=DesktopRouteAudit.outputOutsideGame(game,args[1]);
        System.setProperty("dc.saveRoot",out.resolve("state").toString());System.setProperty("dc.cacheRoot",out.resolve("cache").toString());
        String exeSha=Binary.sha256(Files.readAllBytes(game.resolve("DC2FL.exe")));check(exeSha.equals("3adf5db19bdc67cb8c90e666572ba53538462d8ef461a29dddaa46671f1867f2"),"native FL fixture bound to reviewed original executable hash");
        GameResources files=new GameResources(new LocalGameStorage(game),"dc2fl.exe");CircusResources data=new CircusResources(files,Profiles.get("dc2fl-9c69"));TextCodec codec=GameText.detect(files,data.profile);
        int[][] scenes=new int[3][];List<Object> assets=new ArrayList<Object>(),frames=new ArrayList<Object>();
        for(int n=0;n<IMAGES.length;n++){Crx.Image image=data.image(IMAGES[n]);scenes[n]=original(image);assets.add(Json.obj("resource",IMAGES[n],"width",image.width,"height",image.height,"bpp",image.bpp,"decoded_BGR_sha256",Binary.sha256(image.pixels),"oracle_RGB_sha256",hash(scenes[n])));}
        int[] full={0,0,WIDTH,HEIGHT},clip={113,67,697,521};
        for(int speed=0;speed<4;speed++){runCase(data,codec,scenes,out,frames,"actor-speed"+speed,false,true,speed,false,0,clip);runCase(data,codec,scenes,out,frames,"prepare-speed"+speed,true,false,speed,false,0,full);}
        runCase(data,codec,scenes,out,frames,"both-speed0",true,true,0,false,0,clip);
        runCase(data,codec,scenes,out,frames,"both-speed3",true,true,3,false,0,clip);
        runCase(data,codec,scenes,out,frames,"actor-ctrl",false,true,0,true,0,clip);
        runCase(data,codec,scenes,out,frames,"actor-ctrl-gated",false,true,0,true,1,clip);
        runCase(data,codec,scenes,out,frames,"prepare-ctrl",true,false,0,true,0,full);
        timerCase(data,codec,scenes,out,frames,clip);opcodeFlow(data,codec,scenes,out,frames,false,true);opcodeFlow(data,codec,scenes,out,frames,true,true);opcodeFlow(data,codec,scenes,out,frames,true,false);
        Map<String,Object> report=Json.obj("schema",1,"result","PASS","checks",checks,"compared_pixels",compared,"exe_sha256",exeSha,"assets",assets,"frames",frames,
            "scope","Captured/prepared original FL scene surfaces; independent native schedule plus cumulative clip-only BGR16 oracle; actual DesktopLegacy raster, real VM0D255 WAIT_HOST continuation and snapshots. Target-mask/capture producer lifecycle is fixture-checked; this is not original EXE full-screen equivalence.",
            "original_executable_run",false,"original_game_files_written",false,"prepared_surface_oracle",true,"original_executable_fullscreen_equivalence_claimed",false);
        Files.write(out.resolve("report.json"),Json.text(report).getBytes("UTF-8"));System.out.println(Json.text(Json.obj("result","PASS","checks",checks,"compared_pixels",compared,"frames",frames.size())));
    }
}
