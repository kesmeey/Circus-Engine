package org.circus.core;

import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import javax.imageio.ImageIO;

/** Read-only original FL assets, actual DesktopLegacy output and a separate BGR16 oracle.
 * Service invocations below are explicit test fixtures; this audit does not claim a story clear. */
public final class FlCreditsAudit {
    private static int checks;private static long compared;
    private static void check(boolean ok,String why){checks++;if(!ok)throw new AssertionError(why);}
    private static FlHost host(DesktopLegacy e){return(FlHost)e.host();}
    private static int[] pixels(BufferedImage b){return b.getRGB(0,0,800,600,null,0,800);}
    private static String hash(int[] pixels){byte[] raw=new byte[pixels.length*3];for(int n=0;n<pixels.length;n++)for(int k=0;k<3;k++)raw[n*3+k]=(byte)(pixels[n]>>>(8*k));return Binary.sha256(raw);}
    /** Independent BGR byte-surface replay. The native clip calls never clear.
     * A covering weight16 write makes an earlier prefix irrelevant; this local
     * optimization does not borrow the desktop renderer's owner/cache state. */
    private static final class Oracle {
        final CircusResources data;final int[] initial;final Map<String,int[]> geometry;
        final LinkedHashMap<String,Crx.Image> images=new LinkedHashMap<String,Crx.Image>(4,.75f,true){protected boolean removeEldestEntry(Map.Entry<String,Crx.Image> e){return size()>4;}};
        Oracle(CircusResources data,int[] initial,Map<String,int[]> geometry){this.data=data;this.initial=initial.clone();this.geometry=geometry;}
        Crx.Image image(String name)throws IOException{Crx.Image out=images.get(name);if(out==null){out=data.image(name);images.put(name,out);}return out;}
        int[] expected(List<FlCredits.Frame> journal)throws IOException{
            int first=0,firstLayer=0;boolean overwrite=false;
            for(int n=journal.size()-1;n>=0&&!overwrite;n--)for(int k=journal.get(n).layers.size()-1;k>=0;k--){
                FlCredits.Layer l=journal.get(n).layers.get(k);int[] g=geometry.get(l.image);
                if(g!=null&&g[2]==24&&l.weight16==16&&l.x<=0&&l.y<=0&&(long)l.x+g[0]>=800&&(long)l.y+g[1]>=600){first=n;firstLayer=k;overwrite=true;break;}
            }
            byte[] front=new byte[800*600*3];for(int n=0;n<initial.length;n++)for(int ch=0;ch<3;ch++)front[n*3+ch]=(byte)(initial[n]>>>(8*ch));
            for(int n=first;n<journal.size();n++)for(int k=n==first?firstLayer:0;k<journal.get(n).layers.size();k++){
                FlCredits.Layer l=journal.get(n).layers.get(k);if(l.image.isEmpty()||l.weight16==0)continue;Crx.Image im=image(l.image);if(im.bpp!=24)continue;
                int left=Math.max(0,l.x),top=Math.max(0,l.y),right=(int)Math.min(800L,(long)l.x+im.width),bottom=(int)Math.min(600L,(long)l.y+im.height);
                for(int y=top;y<bottom;y++)for(int x=left;x<right;x++){
                    int source=(y-l.y)*im.stride+(x-l.x)*3,destination=(y*800+x)*3;
                    for(int ch=0;ch<3;ch++)front[destination+ch]=(byte)(((im.pixels[source+ch]&255)*l.weight16+(front[destination+ch]&255)*(16-l.weight16))>>4);
                }
            }
            int[] out=new int[800*600];for(int n=0;n<out.length;n++)out[n]=0xff000000|(front[n*3]&255)|((front[n*3+1]&255)<<8)|((front[n*3+2]&255)<<16);return out;
        }
    }
    private static final class Fixture {
        final DesktopLegacy engine;final Oracle oracle;
        Fixture(DesktopLegacy e,Oracle oracle){engine=e;this.oracle=oracle;}
    }
    private static int[] render(DesktopLegacy e)throws IOException{BufferedImage image=e.render();try{return pixels(image);}finally{image.flush();}}
    private static Map<String,Object> frame(Fixture f,Path out,String name)throws IOException {
        DesktopLegacy e=f.engine;int[] actual=render(e),expected=f.oracle.expected(host(e).flCredits.draws());
        check(Arrays.equals(actual,expected),"desktop agrees with independent persistent BGR16 journal oracle "+name);compared+=actual.length;
        check(Arrays.equals(actual,render(e))&&Arrays.equals(actual,render(e)),"repeated render consumes no extra native operations "+name);
        BufferedImage b=new BufferedImage(800,600,BufferedImage.TYPE_INT_ARGB);b.setRGB(0,0,800,600,actual,0,800);ImageIO.write(b,"png",out.resolve(name+".png").toFile());b.flush();FlCredits c=host(e).flCredits;
        return Json.obj("name",name,"mode",c.variant,"route",c.route,"stage",c.stage,"index",c.index,"alpha",c.alpha,"native_draw_records",c.draws().size(),"sha256",hash(actual));
    }
    private static Fixture service(CircusResources data,TextCodec codec,int mode,int route,Map<String,int[]> geometry)throws Exception {
        DesktopLegacy e=new DesktopLegacy(data,codec,true);FlHost h=host(e);
        // Deliberately prepare a nonblack real-resource front. These direct
        // scene/service assignments are fixtures, not a naturally played ending.
        h.scene.slots[1]="ed/aft";h.scene.publish(h.ui,0,0);int[] initial=render(e);
        boolean nonblack=false;for(int color:initial)nonblack|=(color&0xffffff)!=0;check(nonblack,"prepared original-resource front can detect an invented black clear");
        e.vm().variables.write(3,50,route);h.write(100,52,0);h.write(100,53,0);h.command("pc.command",new int[]{11,mode},new byte[0]);
        check(h.flCreditsUnderlay!=null&&h.flCreditsUnderlay.kind==UiCapture.ARGB&&Arrays.equals(initial,h.flCreditsUnderlay.pixels().argb),"service captures the actual prepared physical front");
        check(Arrays.equals(initial,render(e)),"service start keeps the original front before any native draw");
        check(e.vm().run(1)==MesVm.Status.WAIT_HOST,"fixture records a real VM host wait before snapshot");return new Fixture(e,new Oracle(data,initial,geometry));
    }
    private static void snapshotFuture(Fixture f,long millis,String label)throws Exception{
        DesktopLegacy e=f.engine;byte[] snapshot=e.snapshot();int[] saved=render(e);host(e).elapse(millis);int[] future=render(e);
        check(Arrays.equals(future,f.oracle.expected(host(e).flCredits.draws())),"future frame matches complete BGR journal "+label);compared+=future.length;
        e.restore(snapshot);check(Arrays.equals(snapshot,e.snapshot()),"whole VM FLC2 journal restores byte-exactly "+label);
        check(Arrays.equals(saved,render(e)),"new VM/host renderer owner restores saved native front "+label);
        check(Arrays.equals(saved,render(e)),"second post-restore render does not blend twice "+label);
        host(e).elapse(millis);check(Arrays.equals(future,render(e)),"restored journal and clock produce identical future pixels "+label);
    }
    public static void main(String[] args)throws Exception {
        if(args.length!=2)throw new IOException("Require readonly_FL_GAME owned_OUTPUT");Path game=Paths.get(args[0]).toRealPath(),out=DesktopRouteAudit.outputOutsideGame(game,args[1]);
        System.setProperty("dc.saveRoot",out.resolve("state").toString());System.setProperty("dc.cacheRoot",out.resolve("cache").toString());
        byte[] exe=Files.readAllBytes(game.resolve("DC2FL.exe"));String exeSha=Binary.sha256(exe);check(exeSha.equals("3adf5db19bdc67cb8c90e666572ba53538462d8ef461a29dddaa46671f1867f2"),"audit bound to reviewed DC2FL.exe identity");
        GameResources files=new GameResources(new LocalGameStorage(game),"dc2fl.exe");CircusResources data=new CircusResources(files,Profiles.get("dc2fl-9c69"));TextCodec codec=GameText.detect(files,data.profile);
        LinkedHashSet<String> resources=new LinkedHashSet<String>();FlCredits table=new FlCredits();table.start(0,0);resources.addAll(table.images());for(int route=1;route<=FlCredits.ROUTES;route++){table.start(1,route);resources.addAll(table.images());}
        Map<String,int[]> geometry=new HashMap<String,int[]>();List<Object> assets=new ArrayList<Object>(),frames=new ArrayList<Object>();
        for(String resource:resources){Crx.Image image=data.image(resource);check(image.x==0&&image.y==0&&image.bpp==24,"original FL ending geometry/source "+resource);geometry.put(resource,new int[]{image.width,image.height,image.bpp});assets.add(Json.obj("resource",resource,"width",image.width,"height",image.height,"bpp",image.bpp,"decoded_sha256",Binary.sha256(image.pixels)));if(resource.equals("ed/ged"))check(image.width==800&&image.height==10190&&image.pixels.length==24456000,"GED raw tall allocation is24,456,000 bytes; no full ARGB conversion");}
        Fixture f=service(data,codec,0,0,geometry);DesktopLegacy e=f.engine;FlHost h=host(e);h.elapse(3001);
        check(h.flCredits.frame().capture&&h.flCredits.draws().isEmpty(),"GED leading delay records no draw, even when lead expires");
        check(Arrays.equals(f.oracle.initial,render(e)),"GED lead retains the nonblack physical underlay");h.elapse(1000);frames.add(frame(f,out,"ged-row57"));
        snapshotFuture(f,900,"GED");h=host(e);int[] beforeFinal=render(e);h.elapse(200000);
        check(h.read(100,53)==1&&!h.flCredits.active,"GED natural completion writes100:53");
        check(Arrays.equals(beforeFinal,render(e)),"GED final wholly offscreen blit retains its last visible front");frames.add(frame(f,out,"ged-final-offscreen-retained"));
        for(int route=1;route<=FlCredits.ROUTES;route++){
            f=service(data,codec,1,route,geometry);e=f.engine;h=host(e);h.elapse(3001);for(int n=0;n<8;n++)h.elapse(80);h.elapse(20);frames.add(frame(f,out,"route"+route+"-title-blend"));
            snapshotFuture(f,80,"route"+route+" title");h=host(e);
            int polls=0;while(h.flCredits.active&&polls++<2000){h.elapse(80);if(h.flCredits.stage==2&&h.flCredits.index==3&&h.flCredits.alpha==8)break;}
            check(h.flCredits.stage==2&&h.flCredits.index==3&&h.flCredits.alpha==8,"route"+route+" reaches the second photo alpha sample without a forced stage");frames.add(frame(f,out,"route"+route+"-photo-blend"));
            snapshotFuture(f,80,"route"+route+" photo");h=host(e);
            // Long delayed callbacks intentionally coalesce into one native poll;
            // this raster fixture does not claim every intermediate native frame.
            polls=0;while(h.flCredits.active&&polls++<10000)h.elapse(h.flCredits.fading?80:1000);
            check(!h.flCredits.active&&h.read(100,52)==1&&h.read(100,53)==0,"route"+route+" completes independent slideshow and writes52 only");frames.add(frame(f,out,"route"+route+"-final"));
            if(route<7||route>10){List<FlCredits.Frame> journal=h.flCredits.draws();FlCredits.Frame last=journal.get(journal.size()-1);check(last.layers.size()==1&&last.layers.get(0).image.isEmpty(),"non-DRM ending emits the native unloaded final handle route"+route);check(Arrays.equals(render(e),f.oracle.expected(journal.subList(0,journal.size()-1))),"unloaded final handle preserves previous front route"+route);}
            System.out.println("FL_CREDITS_ROUTE "+route+" PASS journal="+h.flCredits.draws().size());
        }
        Map<String,Object> report=Json.obj("schema",2,"result","PASS","exe_sha256",exeSha,"checks",checks,"compared_pixels",compared,"resources",assets,"frames",frames,"scope","Prepared/service fixtures, original assets and ordered FLC2 journal; persistent physical-front BGR16 oracle plus actual desktop raster/new-host snapshot/future. No native executable run; not original native frame equivalence or a story-clear claim.","coalesced_callback_schedule_ms",Arrays.asList(20,80,900,1000,3001,200000),"supersedes",Json.obj("old_checks",384,"old_compared_pixels",22560000,"old_oracle_status","INVALID: assumed40C220 cleared black before every poll; old384/22.56M conclusion is superseded"));
        Files.write(out.resolve("report.json"),Json.text(report).getBytes("UTF-8"));System.out.println(Json.text(Json.obj("result","PASS","checks",checks,"compared_pixels",compared,"resources",assets.size(),"frames",frames.size())));
    }
}
