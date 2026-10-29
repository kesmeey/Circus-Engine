package org.circus.core;

import java.awt.image.BufferedImage;
import java.io.*;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
import javax.imageio.ImageIO;

/** Repeatable original-script menu/save audit; writes only to its explicitly supplied output root. */
public final class UnityMenuPerformanceAudit {
    public static final class Meter implements GameResources.Storage {
        private final GameResources.Storage base;public long opens,reads,bytes;
        public Meter(GameResources.Storage base){this.base=base;}
        public Set<String> paths(){return base.paths();}
        public byte[] read(String path,int limit)throws IOException{opens++;byte[] b=base.read(path,limit);reads++;bytes+=b.length;return b;}
        public Archive.Source source(String path)throws IOException{final Archive.Source src=base.source(path);return new Archive.Source(){
            public long size()throws IOException{return src.size();}
            public byte[] read(long at,int count)throws IOException{opens++;byte[] b=src.read(at,count);reads++;bytes+=b.length;return b;}
            public Archive.Reader openReader()throws IOException{opens++;final Archive.Reader in=src.openReader();return new Archive.Reader(){public long size()throws IOException{return in.size();}public byte[] read(long at,int count)throws IOException{byte[] b=in.read(at,count);reads++;bytes+=b.length;return b;}public void close()throws IOException{in.close();}};}
        };}
    }
    private final UnityCircusResources data;private final Meter meter;private final Path out;
    private final StringBuilder log=new StringBuilder();private DesktopUnity e;private int checks;
    public UnityMenuPerformanceAudit(UnityCircusResources data,Meter meter,Path out){this.data=data;this.meter=meter;this.out=out;}
    private DesktopUnity engine(Path root)throws Exception{Constructor<DesktopUnity> c=DesktopUnity.class.getDeclaredConstructor(UnityCircusResources.class,boolean.class,Path.class,boolean.class);c.setAccessible(true);return c.newInstance(data,true,root,true);}
    private void check(boolean ok,String message)throws IOException{if(!ok)throw new IOException(message+" at "+e.scriptName()+":"+e.scriptOffset());checks++;log.append("PASS ").append(message).append('\n');}
    private List<String> actions(){List<String> a=new ArrayList<>();for(DesktopEngine.Control c:e.controls())a.add(c.action);return a;}
    private void choose(String action)throws Exception{for(DesktopEngine.Control c:e.controls())if(c.action.equals(action)){e.click((c.left+c.right)/2,(c.top+c.bottom)/2,false);return;}throw new IOException("No action "+action+" at "+e.scriptName()+":"+e.scriptOffset()+" "+actions());}
    private void tick()throws Exception{for(int ch=0;ch<6;ch++)if(e.audioActive(ch)&&!e.audioLoop(ch))e.audioCompleted(ch,e.audioGeneration(ch));if(!e.movie().isEmpty())e.mediaCompleted(e.ticket());e.tick(1000);BufferedImage b=e.render();b.flush();if(e.halted())throw new IOException("Unexpected halt");}
    private void menu(String name)throws Exception{for(int n=0;n<1000;n++){tick();if(e.menuWaiting()){if(e.scriptName().equals(name))return;throw new IOException("Wrong menu "+e.scriptName()+" "+actions());}if(e.script.status()==CircusScrVm.Status.WAIT_CLICK&&!e.textWaiting())e.click(1,1,false);}throw new IOException("Timeout "+name);}
    private void seek()throws Exception{for(int n=0;n<2000;n++){tick();if(e.textWaiting())return;if(e.menuWaiting()){boolean found=false;for(String a:new String[]{"window","sce_start","sce01","01"})if(actions().contains(a)){choose(a);found=true;break;}if(!found)throw new IOException("Unexpected startup menu "+actions());}else if(e.script.status()==CircusScrVm.Status.WAIT_CLICK)e.click(1,1,false);}throw new IOException("Story timeout");}
    private void story()throws Exception{for(int n=0;n<1000;n++){tick();if(e.textWaiting())return;if(e.menuWaiting()){if(actions().contains("return"))choose("return");else throw new IOException("Cannot return from "+e.scriptName()+" "+actions());}else if(e.script.status()==CircusScrVm.Status.WAIT_CLICK)e.click(1,1,false);}throw new IOException("Return timeout");}
    private void slot(int number)throws Exception{for(Dc3rHost.Button b:e.host.buttons())if(b.action.equals("selsave")&&b.flagValue==number){e.click((b.left+b.right)/2,(b.top+b.bottom)/2,false);return;}throw new IOException("Missing slot "+number);}
    interface Job {void run()throws Exception;}
    private void measure(String phase,int slots,int round,Job job)throws Exception{Set<String> before=new HashSet<>(e.loadedImages);long o=meter.opens,r=meter.reads,b=meter.bytes,start=System.nanoTime();job.run();long elapsed=System.nanoTime()-start;Set<String> loaded=new TreeSet<>(e.loadedImages);loaded.removeAll(before);log.append("METRIC ").append(Json.text(Json.obj("phase",phase,"slots",slots,"round",round,"ms",elapsed/1000000.0,"resource_opens",meter.opens-o,"resource_reads",meter.reads-r,"resource_bytes",meter.bytes-b,"new_textures",loaded))).append('\n');}
    private void screenshot(String name)throws Exception{BufferedImage image=e.render();try(OutputStream stream=Files.newOutputStream(out.resolve(name+".png"))){if(!ImageIO.write(image,"png",stream))throw new IOException("PNG");}finally{image.flush();}}
    private void release(DesktopUnity engine)throws Exception{Method method=DesktopUnity.class.getDeclaredMethod("releaseImages");method.setAccessible(true);method.invoke(engine);}
    public void run()throws Exception{run(1);}
    public void run(int dialogueCount)throws Exception{run(dialogueCount,null);}
    public void run(int dialogueCount,Path seedInput)throws Exception{run(dialogueCount,seedInput,false);}
    public void run(int dialogueCount,Path seedInput,boolean snapshotEveryRound)throws Exception{
        Files.createDirectories(out);Throwable failure=null;try{
            e=engine(out.resolve("seed-saves"));if(seedInput==null){seek();for(int n=1;n<dialogueCount;n++){e.click(1,1,false);seek();}}else{e.restore(seedInput);e.tick(0);check(e.textWaiting(),"provided seed restores a stable dialogue");}String script=e.scriptName(),body=e.body();int pc=e.scriptOffset();byte[] stable=e.snapshot();Path seed=out.resolve("seed.sav");e.save(seed);byte[] seedBytes=Files.readAllBytes(seed);log.append("SEED ").append(Json.text(Json.obj("dialogues",dialogueCount,"history",e.host.history.size(),"state_bytes",stable.length,"save_bytes",seedBytes.length,"script",script,"pc",pc))).append('\n');screenshot("story-before");release(e);
            for(int slots:new int[]{0,10,102}){
                Path root=out.resolve("count-"+slots);e=engine(root);Files.createDirectories(e.saveDirectory);for(int n=0;n<slots;n++)Files.write(e.saveDirectory.resolve("slot-"+n+".sav"),seedBytes);
                measure("restore_story",slots,-1,()->{e.restore(stable);e.tick(0);BufferedImage b=e.render();b.flush();});
                for(int round=0;round<3;round++){
                    final int sample=round;
                    measure("right_click",slots,round,()->e.click(1,1,true));
                    measure("open_advmenu",slots,round,()->menu("advmenu"));
                    measure("open_save",slots,round,()->{choose("save");menu("save");});
                    final byte[][] state={null};if(round==0||snapshotEveryRound){measure("snapshot_menu",slots,round,()->state[0]=e.snapshot());log.append("STATE ").append(Json.text(Json.obj("slots",slots,"round",round,"snapshot_bytes",state[0].length))).append('\n');}
                    if(slots==0&&round==0){screenshot("save-menu");BufferedImage before=e.render();int[] pixels=before.getRGB(0,0,e.width,e.height,null,0,e.width);before.flush();DesktopUnity resumed=engine(root);resumed.restore(state[0]);release(e);e=resumed;BufferedImage after=e.render();check(Arrays.equals(pixels,after.getRGB(0,0,e.width,e.height,null,0,e.width)),"menu snapshot restores exact pixels");after.flush();check(e.menuWaiting()&&e.scriptName().equals("save"),"menu snapshot keeps original SAVE controls");}
                    measure("write_slot",slots,round,()->{slot(0);for(int n=0;n<100;n++){tick();if(e.menuWaiting()){if(actions().contains(":y")){choose(":y");continue;}if(actions().contains("return"))break;}}});
                    Path saved=e.saveDirectory.resolve("slot-0.sav");check(Files.isRegularFile(saved),"original SAVE writes slot 0 at count "+slots+" round "+sample);DesktopSave savedInfo=DesktopSave.decode(Files.readAllBytes(saved));
                    log.append("SAVE ").append(Json.text(Json.obj("slots",slots,"round",round,"file_bytes",Files.size(saved),"state_bytes",savedInfo.state.length,"thumbnail_bytes",savedInfo.preview.length))).append('\n');
                    measure("return_story",slots,round,()->{if(e.menuWaiting())choose("return");story();});check(e.scriptName().equals(script)&&e.scriptOffset()==pc&&e.body().equals(body),"menu return preserves original dialogue");
                }
                byte[] saved=DesktopSave.decode(Files.readAllBytes(e.saveDirectory.resolve("slot-0.sav"))).state;e.restore(saved);check(e.scriptName().equals(script)&&e.scriptOffset()==pc&&e.body().equals(body),"saved slot restores story at count "+slots);screenshot("story-after-"+slots);release(e);
            }
        }catch(Throwable t){failure=t;log.append("FAIL ").append(t).append('\n');}finally{log.append("CHECKS ").append(checks).append("; media completion and script time simulated; original rendering/save/input paths used\n");Files.write(out.resolve("result.txt"),log.toString().getBytes("UTF-8"));System.out.print(log);}
        if(failure!=null){if(failure instanceof Exception)throw(Exception)failure;throw new RuntimeException(failure);}
    }
    public static void main(String[] args)throws Exception{if(args.length<2||args.length>5)throw new IllegalArgumentException("Usage: UnityMenuPerformanceAudit GAME OUTPUT [RECORD_PACK_OR_-] [DIALOGUES] [SEED_SAVE]");GameResources.Storage source=new LocalGameStorage(Paths.get(args[0]));RangeStorage ranges=null;if(args.length>=3&&!args[2].equals("-")){ranges=RangeStorage.recording(source);source=ranges;}Meter meter=new Meter(source);try{new UnityMenuPerformanceAudit(new UnityCircusResources(new GameResources(meter)),meter,Paths.get(args[1])).run(args.length>=4?Integer.parseInt(args[3]):1,args.length==5?Paths.get(args[4]):null);}finally{if(ranges!=null)ranges.write(Paths.get(args[2]));}}
}
