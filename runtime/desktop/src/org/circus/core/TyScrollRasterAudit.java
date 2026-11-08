package org.circus.core;

import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.*;
import java.util.*;

/** Actual DesktopLegacy scroll renderer with genuine synthetic TY VM waits,
 * original BGR24/BGRA32 resources and an independent persistent byte oracle. */
public final class TyScrollRasterAudit {
    private static final int W=800,H=600;
    private static int checks;private static long compared;
    private static void check(boolean value,String why){checks++;if(!value)throw new AssertionError(why);}
    private static void word(ByteArrayOutputStream b,int n){b.write(n);b.write(n>>>8);}
    private static void words(ByteArrayOutputStream b,int op,int... values){b.write(op);for(int n:values)word(b,n);}
    private static byte[] script(String image,int speed,int x,int y,int weight){ByteArrayOutputStream b=new ByteArrayOutputStream();b.write(new byte[]{0,0,0,0,0x69,0x5f},0,6);words(b,0x5c,99,12,speed,0);b.write(0x30);byte[] name=image.getBytes(Binary.SJIS);b.write(name,0,name.length);b.write(0);b.write(0x0d);b.write(0);b.write(0);words(b,0x62,x,y,0,weight);b.write(0x12);b.write(200);b.write(0);b.write(0);b.write(0);b.write(0);return b.toByteArray();}
    private static final class Fixture implements GameResources.Storage {
        final LocalGameStorage original;final byte[] start;final Set<String> paths;
        Fixture(Path root,byte[] start)throws IOException{original=new LocalGameStorage(root);this.start=start;paths=new TreeSet<String>();for(String path:original.paths())if(!start(path))paths.add(path);paths.add("advdata/mes/start.mes");}
        private boolean start(String name){return name.replace('\\','/').equalsIgnoreCase("advdata/mes/start.mes");}
        public Set<String> paths(){return paths;}
        public byte[] read(String path,int limit)throws IOException{if(!start(path))return original.read(path,limit);if(start.length>limit)throw new IOException("Fixture read bound");return start.clone();}
        public Archive.Source source(String path)throws IOException{if(!start(path))return original.source(path);return new Archive.Source(){public long size(){return start.length;}public byte[] read(long at,int n)throws IOException{Binary.range(at,n,start.length);return Arrays.copyOfRange(start,(int)at,(int)at+n);}};}
    }
    private static TyHost host(DesktopLegacy e){return(TyHost)e.host();}
    private static int[] render(DesktopLegacy e)throws IOException{BufferedImage image=e.render();try{return image.getRGB(0,0,W,H,null,0,W);}finally{image.flush();}}
    private static int[] opaque(Crx.Image raw,int x,int y){
        if(raw.bpp!=24)throw new IllegalArgumentException("This oracle does not claim native32-bit background full composition");
        int[] pixels=new int[W*H];Arrays.fill(pixels,0xff000000);for(int dy=Math.max(0,y);dy<Math.min(H,y+raw.height);dy++)for(int dx=Math.max(0,x);dx<Math.min(W,x+raw.width);dx++){
            int at=(dy-y)*raw.stride+(dx-x)*(raw.bpp/8),old=pixels[dy*W+dx],a=raw.bpp==32?raw.pixels[at+3]&255:255,r=raw.pixels[at+2]&255,g=raw.pixels[at+1]&255,b=raw.pixels[at]&255;
            pixels[dy*W+dx]=0xff000000|((r*a+(old>>>16&255)*(255-a))/255)<<16|((g*a+(old>>>8&255)*(255-a))/255)<<8|(b*a+(old&255)*(255-a))/255;
        }return pixels;
    }
    private static final class Oracle {
        final Crx.Image source;final int[] initial;int[] front;int consumed;
        Oracle(Crx.Image source,int[] initial){this.source=source;this.initial=initial.clone();front=initial.clone();}
        int[] expected(TyScroll owner){if(consumed>owner.draws().size()){consumed=0;front=initial.clone();}for(;consumed<owner.draws().size();consumed++){
            TyScroll.Frame frame=owner.draws().get(consumed);if(frame.weight==0){front=opaque(source,frame.x,frame.y);continue;}if(source.bpp!=24)continue;
            for(int y=Math.max(0,frame.y);y<Math.min(H,frame.y+source.height);y++)for(int x=Math.max(0,frame.x);x<Math.min(W,frame.x+source.width);x++){
                int at=(y-frame.y)*source.stride+(x-frame.x)*3,old=front[y*W+x],color=0xff000000,w=frame.weight;
                for(int c=0;c<3;c++)color|=((((old>>>(c*8))&255)*(16-w)+(source.pixels[at+c]&255)*w)/16)<<(c*8);front[y*W+x]=color;
            }
        }return front.clone();}
    }
    private static void compare(DesktopLegacy e,Oracle oracle,String label)throws Exception{
        int[] expected=oracle.expected(host(e).tyScroll),actual=render(e);if(!Arrays.equals(expected,actual)){int at=0;while(expected[at]==actual[at])at++;throw new AssertionError(label+" pixel("+(at%W)+","+(at/W)+") expected="+Integer.toHexString(expected[at])+" actual="+Integer.toHexString(actual[at]));}compared+=actual.length;check(true,"Persistent native16-level feedback oracle "+label);check(Arrays.equals(actual,render(e)),"Repeated rendering consumes no extra feedback "+label);
    }
    private static Map<String,Object> one(Path game,Path out,String name,String asset,int speed,int tx,int ty,int weight)throws Exception{
        System.setProperty("dc.saveRoot",out.resolve(name+"-state").toString());System.setProperty("dc.cacheRoot",out.resolve(name+"-cache").toString());GameResources files=new GameResources(new Fixture(game,script(asset,speed,tx,ty,weight)),"dc2ty_chinese(simplified)_patch.exe");Profile profile=GameVersion.detect(files).profile();check(profile.id.equals("dc2ty-5f69"),"Fixture START independently identifies TY "+name);CircusResources resources=new CircusResources(files,profile);DesktopLegacy e=new DesktopLegacy(resources,TextCodec.identity(),true);
        for(int n=0;n<100&&!host(e).tyScroll.active;n++)e.tick(25);check(host(e).tyScroll.active&&e.vm().status()==MesVm.Status.WAIT_HOST,"Authored opcode62 creates a genuine VM/native host wait "+name);
        check(host(e).tyScroll.initial.kind==UiCapture.ARGB,"Scroll captures actual portable initial framebuffer "+name);Crx.Image source=resources.image(asset);Oracle oracle=new Oracle(source,host(e).tyScroll.initial.pixels().argb);compare(e,oracle,name+"-initial");
        e.tick(0);compare(e,oracle,name+"-first");byte[] state=e.snapshot();int[] old=render(e);e.tick(17);int[] future=render(e);e.restore(state);check(e.vm().status()==MesVm.Status.WAIT_HOST&&Arrays.equals(old,render(e)),"New-host real-wait restore keeps feedback raster "+name);e.tick(17);check(Arrays.equals(future,render(e)),"Restored native clock produces identical future feedback "+name);compare(e,oracle,name+"-restored-future");
        int polls=0;while(host(e).tyScroll.active&&polls++<100){TyScroll stateNow=host(e).tyScroll;if(source.bpp==32&&stateNow.x==stateNow.targetX&&stateNow.y==stateNow.targetY&&stateNow.holdPolls+1+stateNow.weight>16)break;e.tick(17);compare(e,oracle,name+"-poll"+polls);}
        if(source.bpp==32)check(host(e).tyScroll.active&&host(e).tyScroll.weight>0,"Unreviewed32-bit final composition is excluded after confirmed weighted no-write polls");else check(!host(e).tyScroll.active,"Native move/dwell/full-compose/release completes "+name);compare(e,oracle,name+"-last-reviewed-frame");
        return Json.obj("sample",name,"asset",asset,"bpp",source.bpp,"source_size",Arrays.asList(source.width,source.height),"speed",speed,"target",Arrays.asList(tx,ty),"requested_weight",weight,"draws",host(e).tyScroll.draws().size(),"polls",polls,"full_composition_verified",source.bpp==24,"decoded_sha256",Binary.sha256(source.pixels));
    }
    public static void main(String[] args)throws Exception{
        if(args.length!=2)throw new IOException("Require readonly_TY_GAME owned_OUTPUT");Path game=Paths.get(args[0]).toRealPath(),out=DesktopRouteAudit.outputOutsideGame(game,args[1]);List<Object> cases=new ArrayList<Object>();
        cases.add(one(game,out,"slow-weight1","system/sce_select_back.crx",0,-5,3,1));cases.add(one(game,out,"medium-weight8","system/sce_select_hyd.crx",1,5,-3,8));cases.add(one(game,out,"fast-weight16","system/sce_select_set.crx",3,-17,6,16));cases.add(one(game,out,"opaque-weight0","system/sce_select_mhr.crx",2,-9,-11,0));cases.add(one(game,out,"clamped-weight24","system/sce_select_myu.crx",1,5,-3,24));cases.add(one(game,out,"bgra-weighted-skip","system/sce_select_chip.crx",1,5,-3,8));
        Map<String,Object> report=Json.obj("result","PASS","checks",checks,"compared_pixels",compared,"cases",cases,"original_executable_executed",false,"original_game_files_written",false,"scope","Authored minimal TY START executes native opcode62 against actual original24/32-bit assets and creates genuine WAIT_HOST snapshots. Independent per-byte persistent BGR16 oracle,32-bit weighted no-write,24-bit full prepared background composition, clipping, repeated render and new-host future feedback. The deliberately misused32-bit UI atlas is stopped before the unreviewed full-composition branch. Prepared service fixtures; not original native complete frames or a naturally reached scroll route.");Files.write(out.resolve("report.json"),Json.text(report).getBytes("UTF-8"));System.out.println(Json.text(Json.obj("result","PASS","checks",checks,"compared_pixels",compared,"cases",cases.size())));
    }
}
