package org.circus.core;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;

/** PH system progress import, cold production startup and original gallery input.
 * --full-fixture changes a private in-memory copy of the source's reviewed
 * progress fields only. It never changes the native game or a user's saves. */
public final class Dc4ProgressGalleryAudit {
    private static final String ID="dc4ph-abb6";
    private final DesktopDd e;private final Path out;private int checks;
    private final List<Object> menus=new ArrayList<>(),frames=new ArrayList<>();
    private Dc4ProgressGalleryAudit(DesktopDd engine,Path output){e=engine;out=output;}
    private void check(boolean ok,String why)throws IOException{if(!ok)throw new IOException(why+" at "+e.scriptName()+"@"+Integer.toHexString(e.scriptOffset()));checks++;}
    private List<String> actions(){List<String> a=new ArrayList<>();for(DesktopEngine.Control c:e.controls())if(!c.action.isEmpty())a.add(c.action);return a;}
    private void menu()throws Exception{
        for(int n=0;n<2000;n++){
            for(int ch=0;ch<6;ch++)if(!e.audioLoop(ch))e.audioCompleted(ch,e.audioGeneration(ch));
            e.mediaCompleted(e.ticket());e.tick(1000);
            if(e.menuWaiting()){
                e.tick(400);check(e.menuWaiting(),"Original menu remains waiting after its native panel animation");
                menus.add(Json.obj("script",e.scriptName(),"pc",e.scriptOffset(),"actions",actions()));return;
            }
            e.vm().signalHost(e.ticket(),MesVm.HostSignal.TEXT_COMPLETE);e.vm().signalHost(e.ticket(),MesVm.HostSignal.ADVANCE);
            if(e.halted())throw new IOException("Original PH flow halted before gallery");
        }throw new IOException("Original PH menu timeout");
    }
    private void press(String action)throws Exception{
        DesktopEngine.Control chosen=null;for(DesktopEngine.Control c:e.controls())if(c.action.equals(action)){chosen=c;break;}
        check(chosen!=null,"Original control is present: "+action);
        int x=(chosen.left+chosen.right)/2,y=(chosen.top+chosen.bottom)/2;
        check(x>=0&&x<1280&&y>=0&&y<720,"Original gallery control is inside the Canvas: "+action);
        e.pointerDown(x,y);e.pointerUp(x,y);
    }
    private void choose(String action)throws Exception{press(action);menu();}
    private void shot(String name)throws Exception{
        BufferedImage image=e.render();try{
            check(e.missingImages.isEmpty(),"Original gallery has no missing images: "+e.missingImages);
            check(image.getWidth()==1280&&image.getHeight()==720,"PH gallery Canvas geometry");
            int[] argb=image.getRGB(0,0,1280,720,null,0,1280);ByteArrayOutputStream bytes=new ByteArrayOutputStream();DataOutputStream words=new DataOutputStream(bytes);int visible=0;for(int pixel:argb){words.writeInt(pixel);if(pixel!=0xff000000)visible++;}
            check(visible>1000,"Original gallery produces visible pixels: "+name);
            ImageIO.write(image,"png",out.resolve(name+".png").toFile());frames.add(Json.obj("name",name,"pixel_sha256",Binary.sha256(bytes.toByteArray()),"nonblack_pixels",visible));
        }finally{image.flush();}
    }
    private static Map<String,Object> summary(NativeProgress.Summary s){return Json.obj("completion",s.completion,"music",s.music,"cg",s.cg,"scenes",s.scenes,"total",s.total);}
    private static byte[] full(byte[] original){byte[] b=original.clone();for(int n=100;n<=112;n++)b[0xd8+n]=1;Arrays.fill(b,0x180,0x310,(byte)1);Arrays.fill(b,0x310,0x7c0,(byte)1);Arrays.fill(b,0x7c0,0x7c0+650,(byte)1);return b;}
    private Map<String,Object> run(byte[] imported,NativeProgress.Summary added)throws Exception{
        NativeProgress progress=NativeProgress.parse(ID,imported);
        check(progress.preview(ID,e.host.state).total==0,"Cold production runtime loads every imported native progress field");
        menu();check(actions().contains("sce_start"),"Natural original title reached after native import");shot("title");choose("extra");
        check(actions().contains("musmode"),"Imported completion109 opens the original music entry");byte[] extra=e.snapshot();
        choose("cgmode");check(e.scriptName().equalsIgnoreCase("CG_ASA")&&actions().contains("cg0"),"Original CG menu exposes the imported first CG");shot("cg-menu");
        press("cg0");
        boolean viewer=false;for(int n=0;n<200;n++){e.tick(1000);if(e.waiting()&&!e.menuWaiting()&&e.host.remainingMillis()<0){viewer=true;break;}if(e.menuWaiting())break;}
        check(viewer,"Original unlocked CG opens its input-driven full-image viewer");
        String image=e.host.scene.slots[1].resource;check(!image.isEmpty(),"CG viewer selects an original background resource");shot("cg-view");
        e.click(640,360,true);menu();check(actions().contains("cg0"),"Original right-click leaves CG viewer for its page");
        choose("page++");check(actions().contains("page--"),"Original CG pagination reaches the next page");shot("cg-page2");
        choose("return");check(actions().contains("scemode"),"CG return goes to original Extra menu");
        choose("scemode");check(e.scriptName().equalsIgnoreCase("SCE_ASA")&&actions().contains("sce0"),"Original Scene Replay exposes imported scenario progress");shot("scene-menu");
        choose("return");choose("musmode");check(e.host.list.items.size()==43&&actions().contains("@slot:0"),"Original43-track music list is available after native import");shot("music-menu");
        choose("@slot:0");check(!e.audio(0).isEmpty()&&e.audioActive(0),"Original music selection requests a real audio resource");String music=e.audio(0);choose("return");
        check(progress.preview(ID,e.host.state).total==0,"Original galleries preserve every imported progress field");
        e.restore(extra);check(progress.preview(ID,e.host.state).total==0,"Portable restore retains imported unlocks and route flags");
        return Json.obj("checks",checks,"pass",true,"profile",ID,"imported_additions",summary(added),"source_summary",summary(progress.source),"menus",menus,"frames",frames,"first_CG_resource",image,"first_music_resource",music,"missing_images",e.missingImages,"original_game_executed",false,"original_game_written",false,"physical_audio_output_verified",false,"time_and_media_completion_controlled",true);
    }
    public static void main(String[] args)throws Exception{
        if(args.length<3||args.length>4)throw new IllegalArgumentException("GAME NATIVE_SYSTEM OUTPUT [--full-fixture]");
        boolean fixture=args.length==4&&args[3].equals("--full-fixture");if(args.length==4&&!fixture)throw new IllegalArgumentException("Unknown option");
        Path game=Paths.get(args[0]).toRealPath(),source=Paths.get(args[1]).toRealPath(),out=DesktopRouteAudit.outputOutsideGame(game,args[2]);
        System.setProperty("dc.saveRoot",out.resolve("saves").toString());System.setProperty("dc.cacheRoot",out.resolve("cache").toString());
        byte[] original=Files.readAllBytes(source),imported=original.clone();String sourceHash=Binary.sha256(original);
        RangeStorage recorder=RangeStorage.recording(new LocalGameStorage(game));Map<String,Object> report=new LinkedHashMap<>();
        try{
            GameResources files=new GameResources(recorder,"DC4PHDL.exe");Profile profile=GameVersion.detect(files).profile();if(!profile.id.equals(ID))throw new IOException("Source game is not independently identified as PH ABB6");
            CircusResources data=new CircusResources(files,profile);TextCodec codec=GameText.select(files,profile,"auto","@none");
            NativeProgress nativeFile=NativeProgress.parse(ID,original);if(fixture)imported=full(original);NativeProgress parsed=NativeProgress.parse(ID,imported);NativeProgress.Target target=new NativeProgress.Target(data,codec);NativeProgress.Summary additions=parsed.apply(target);
            Path progress=DesktopEngine.mesSaveDirectory(out.resolve("saves"),data).resolve("progress.dat");DesktopEngine.atomic(progress,target.save());Files.write(out.resolve("import-system.dat"),imported);
            DesktopDd engine=new DesktopDd(data,codec,false);report=new Dc4ProgressGalleryAudit(engine,out).run(imported,additions);
            report.put("native_file",Json.obj("path",source.toString(),"bytes",original.length,"sha256",sourceHash,"summary",summary(nativeFile.source)));
            report.put("synthetic_full_source",fixture);report.put("imported_source_sha256",Binary.sha256(imported));report.put("game_executable",files.executable);
        }catch(Throwable failure){report.put("pass",false);report.put("error",failure.toString());throw failure;}
        finally{if(!sourceHash.equals(Binary.sha256(Files.readAllBytes(source))))throw new IOException("Original system source changed");recorder.write(out.resolve("gallery-original.pack"));Files.write(out.resolve("report.json"),(Json.text(report)+"\n").getBytes("UTF-8"));}
        System.out.println(Json.text(Json.obj("pass",report.get("pass"),"checks",report.get("checks"),"output",out.toString())));
    }
}
