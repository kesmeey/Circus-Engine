package org.circus.core;

import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import javax.imageio.ImageIO;

/** Prepared actor descriptors, independent native integer pixel oracle and actual
 * DesktopLegacy raster. Non100% samples cover uncached410BF0/411880 only;
 *4034A0's area-filtered handle producer is explicitly outside this audit. */
public final class DcdxActorRasterAudit {
    private static final int WIDTH=1024,HEIGHT=576;
    private static int checks;private static long compared;
    private static void check(boolean value,String why){checks++;if(!value)throw new AssertionError(why);}
    private static DcdxHost host(DesktopLegacy engine){return(DcdxHost)engine.host();}
    private static int[] render(DesktopLegacy engine)throws IOException{BufferedImage b=engine.render();try{return b.getRGB(0,0,WIDTH,HEIGHT,null,0,WIDTH);}finally{b.flush();}}
    private static String hash(int[] pixels){byte[] bytes=new byte[pixels.length*3];for(int n=0;n<pixels.length;n++)for(int ch=0;ch<3;ch++)bytes[n*3+ch]=(byte)(pixels[n]>>>(ch*8));return Binary.sha256(bytes);}
    private static void le16(ByteArrayOutputStream b,int value){b.write(value);b.write(value>>>8);}
    private static byte[] script(){ByteArrayOutputStream b=new ByteArrayOutputStream();for(int n=0;n<4;n++)b.write(0);le16(b,0x7769);b.write(0x65);for(int v:new int[]{1000,800,0,0})le16(b,v);b.write(0);b.write(0);b.write(0);return b.toByteArray();}
    private static GameResources files()throws IOException{
        final Map<String,byte[]> data=new TreeMap<>();byte[] names=new byte[208];names[16]=1;System.arraycopy("START".getBytes(Binary.SJIS),0,names,20,5);data.put("advdata/dat/names.dat",names);data.put("advdata/mes/start.mes",script());
        return new GameResources(new GameResources.Storage(){
            public Set<String> paths(){return data.keySet();}
            private byte[] get(String path)throws IOException{byte[] b=data.get(path.toLowerCase(Locale.ROOT));if(b==null)throw new FileNotFoundException(path);return b;}
            public byte[] read(String path,int limit)throws IOException{byte[] b=get(path);if(b.length>limit)throw new IOException("Synthetic resource limit");return b.clone();}
            public Archive.Source source(String path)throws IOException{final byte[] b=get(path);return new Archive.Source(){public long size(){return b.length;}public byte[] read(long at,int n)throws IOException{Binary.range(at,n,b.length);return Arrays.copyOfRange(b,(int)at,(int)at+n);}};}
        });
    }
    private static final class Resources extends CircusResources {
        final Map<String,Crx.Image> images;
        Resources(Map<String,Crx.Image> images)throws IOException{super(files(),Profiles.get("dcdx-7769"));this.images=images;}
        @Override public Crx.Image image(String name)throws IOException{Crx.Image image=images.get(name);if(image==null)throw new FileNotFoundException(name);return image;}
    }
    private static Crx.Image pattern(int width,int height,int bpp,int variant){
        int stride=(width*(bpp/8)+3)&~3;byte[] pixels=new byte[stride*height];int[] alpha={0,1,2,7,15,16,31,32,64,127,128,129,200,254,255};
        for(int y=0;y<height;y++)for(int x=0;x<width;x++){
            int at=y*stride+x*(bpp/8),r=(x*47+y*19+variant*31)&255,g=(x*13+y*71+variant*7)&255,b=(x*89+y*23+variant*43)&255;
            if(variant==0)r&=127;else if((x+y*3)%5==0){r=255;g=0;b=255;}
            pixels[at]=(byte)b;pixels[at+1]=(byte)g;pixels[at+2]=(byte)r;if(bpp==32)pixels[at+3]=(byte)alpha[(x+y*width)%alpha.length];
        }return new Crx.Image(0,0,width,height,bpp,pixels,null);
    }
    /** Independent top-down byte-surface compositor. Does not call production
     * layout, sampling, ARGB conversion, renderer or blend helpers. */
    private static int[] oracle(Resources resources,Dc2Scene.Frame frame)throws IOException{
        byte[] front=new byte[WIDTH*HEIGHT*3];Crx.Image background=resources.image(frame.slots[1]);
        for(int y=0;y<HEIGHT;y++)for(int x=0;x<WIDTH;x++){int sx=x+frame.x,sy=y+frame.y;if(sx<0||sy<0||sx>=background.width||sy>=background.height)continue;int from=sy*background.stride+sx*3,to=(y*WIDTH+x)*3;for(int c=0;c<3;c++)front[to+c]=background.pixels[from+c];}
        for(int actor=0;actor<4;actor++){
            String name=frame.slots[actor+2];if(name.isEmpty())continue;Crx.Image image=resources.image(name);int[] p=frame.actors[actor];int z=p[2],left=p[0],top=p[1],w=image.width,h=image.height;
            if(z==100){if(w>882)left+=p[3]-((w*100/882)*p[3])/100;if(h>756)top+=p[4]-((h*100/756)*p[4])/100;}
            else{left+=p[3]-p[3]*z/100;top+=p[4]-p[4]*z/100;w=w*z/100;h=h*z/100;}
            if(w<=0||h<=0)continue;int weight=z==100&&p[5]>=1&&p[5]<=16?16-p[5]:16;
            for(int y=Math.max(0,top);y<Math.min((long)HEIGHT,(long)top+h);y++)for(int x=Math.max(0,left);x<Math.min((long)WIDTH,(long)left+w);x++){
                int dx=x-left,dy=y-top,sx=dx==0?0:(int)(((long)image.width/2+(long)dx*image.width)/w),sy=dy==0?0:(int)(((long)image.height/2+(long)dy*image.height)/h);
                int at=sy*image.stride+sx*(image.bpp/8),to=(y*WIDTH+x)*3;
                if(image.bpp==24){
                    if((image.pixels[at]&255)==255&&image.pixels[at+1]==0&&(image.pixels[at+2]&255)==255)continue;
                    for(int c=0;c<3;c++)front[to+c]=(byte)(((front[to+c]&255)*(16-weight)+(image.pixels[at+c]&255)*weight)/16);
                }else{
                    int opacity=image.pixels[at+3]&255;
                    if(z!=100){for(int c=0;c<3;c++)front[to+c]=(byte)(((front[to+c]&255)*(255-opacity)+(image.pixels[at+c]&255)*opacity)/255);}
                    else{int alpha=opacity*weight;for(int c=0;c<3;c++)front[to+c]=(byte)(((front[to+c]&255)*(4080-alpha)+(image.pixels[at+c]&255)*alpha)/4080);}
                }
            }
        }
        int[] out=new int[WIDTH*HEIGHT];for(int n=0;n<out.length;n++)out[n]=0xff000000|(front[n*3]&255)|((front[n*3+1]&255)<<8)|((front[n*3+2]&255)<<16);return out;
    }
    private static DesktopLegacy fixture(Resources data,String[] actors,int[][] poses,int[] alpha)throws Exception{
        DesktopLegacy engine=new DesktopLegacy(data,TextCodec.identity(),true);DcdxHost h=host(engine);h.scene.slots[1]="background";h.write(99,12,0);
        for(int n=0;n<4;n++){h.scene.slots[n+2]=n<actors.length?actors[n]:"";if(n<poses.length){h.scene.character(n,poses[n]);System.arraycopy(poses[n],0,h.liveActors[n],0,3);}engine.vm().variables.write(3,51+n,n<alpha.length?alpha[n]:0);}
        // These are uncached raw-handle consumer fixtures, corresponding to an
        // actor loaded while the nativeCtrl gate skips4034A0; input is released now.
        h.motionSkipPressed=false;h.scene.publish(h.ui,0,0);
        check(engine.vm().run(1)==MesVm.Status.WAIT_HOST&&h.dcdxActorMotion.active,"Synthetic START executes65 into a genuine VM/host wait");return engine;
    }
    private static Map<String,Object> frame(Resources data,DesktopLegacy engine,String label,Path out,boolean png)throws Exception{
        int[] want=oracle(data,host(engine).scene.shown),actual=render(engine);if(!Arrays.equals(want,actual)){int at=0;while(want[at]==actual[at])at++;throw new AssertionError(label+" pixel("+(at%WIDTH)+","+(at/WIDTH)+") expected="+Integer.toHexString(want[at])+" actual="+Integer.toHexString(actual[at]));}
        check(true,"Independent native integer raster "+label);compared+=actual.length;check(Arrays.equals(actual,render(engine)),"Repeated render deterministic "+label);
        if(png){BufferedImage b=new BufferedImage(WIDTH,HEIGHT,BufferedImage.TYPE_INT_ARGB);b.setRGB(0,0,WIDTH,HEIGHT,actual,0,WIDTH);ImageIO.write(b,"png",out.resolve(label+".png").toFile());b.flush();}
        return Json.obj("sample",label,"sha256",hash(actual));
    }
    private static void snapshotFuture(Resources data,DesktopLegacy engine,Path out,List<Object> frames)throws Exception{
        check(engine.vm().status()==MesVm.Status.WAIT_HOST&&host(engine).waiting(),"Snapshot starts from a matching true VM/host wait");
        byte[] snapshot=engine.snapshot();int[] initial=render(engine);DcdxHost previous=host(engine);engine.vm().elapse(0);int[] future=render(engine);check(Arrays.equals(future,oracle(data,host(engine).scene.shown)),"Future native movement redraw has integer oracle pixels");compared+=future.length;
        engine.restore(snapshot);check(host(engine)!=previous&&engine.vm().status()==MesVm.Status.WAIT_HOST,"CVS2 restores into a new correctly bound host");
        check(Arrays.equals(snapshot,engine.snapshot()),"Whole VM wait/projection/frozen-alpha snapshot byte-exact");check(Arrays.equals(initial,render(engine)),"Restore retains frozen frame alpha, not future variables");
        engine.vm().elapse(0);check(Arrays.equals(future,render(engine)),"Restored input schedule produces the identical future frame");frames.add(frame(data,engine,"snapshot-future",out,true));
    }
    public static void main(String[] args)throws Exception{
        if(args.length!=2)throw new IOException("Require readonly_DCDX_GAME owned_OUTPUT");Path game=Paths.get(args[0]).toRealPath(),out=DesktopRouteAudit.outputOutsideGame(game,args[1]);
        System.setProperty("dc.saveRoot",out.resolve("state").toString());System.setProperty("dc.cacheRoot",out.resolve("cache").toString());
        String peHash=Binary.sha256(Files.readAllBytes(game.resolve("DCDX.EXE")));check(peHash.equals("86bcd33e954b81f9b7bb731bcbc2886bc68d8d069074a32ef2af716fede395cb"),"Original PE identity independently bound");
        Map<String,Crx.Image> images=new LinkedHashMap<>();images.put("background",pattern(WIDTH,HEIGHT,24,0));images.put("rgb",pattern(8,7,24,1));images.put("alpha",pattern(9,6,32,2));Resources data=new Resources(images);List<Object> frames=new ArrayList<>();
        for(String name:new String[]{"rgb","alpha"})for(int weight=0;weight<=16;weight++){
            int fade=weight==16?0:16-weight,x=weight%3==0?-3:weight%3==1?WIDTH-4:17,y=weight%2==0?-2:HEIGHT-3;
            DesktopLegacy engine=fixture(data,new String[]{name},new int[][]{{x,y,100,0,0,0}},new int[]{fade});engine.vm().variables.write(3,51,255-fade);
            check(host(engine).scene.shown.actors[0][5]==fade,"Alpha belongs to the frozen frame rather than a future live variable");frames.add(frame(data,engine,name+"-w"+weight,out,weight==7));
        }
        for(int zoom:new int[]{33,50,75,125,200})for(String name:new String[]{"rgb","alpha"}){
            DesktopLegacy engine=fixture(data,new String[]{name},new int[][]{{-2,-1,zoom,0,0,0}},new int[]{16});frames.add(frame(data,engine,name+"-raw-stretch"+zoom,out,false));
        }
        DesktopLegacy stack=fixture(data,new String[]{"rgb","alpha","rgb","alpha"},new int[][]{{-2,-1,100,0,0,0},{0,0,100,0,0,0},{1,1,100,0,0,0},{2,2,100,0,0,0}},new int[]{2,5,9,1});frames.add(frame(data,stack,"actor-order0-to3",out,true));
        stack.vm().variables.write(3,51,13);snapshotFuture(data,stack,out,frames);
        CircusResources original=new CircusResources(new GameResources(new LocalGameStorage(game)),Profiles.get("dcdx-7769"));String asset="bst\\dc\\c06c_01";Crx.Image actor=original.image(asset);images.put("original-body-actor",actor);
        DesktopLegacy real=fixture(data,new String[]{"original-body-actor"},new int[][]{{-80,0,100,441,288,0}},new int[]{7});frames.add(frame(data,real,"actual-body-actor",out,true));
        Map<String,Object> report=Json.obj("result","PASS","checks",checks,"compared_pixels",compared,"exe_sha256",peHash,"frames",frames,"actual_asset",Json.obj("reference",asset,"width",actor.width,"height",actor.height,"bpp",actor.bpp,"decoded_sha256",Binary.sha256(actor.pixels),"source_script","dcdx_dc1a_00.mes","script_sha256",Binary.sha256(Files.readAllBytes(game.resolve("AdvData/MES/dcdx_dc1a_00.mes")))),"scope","Prepared descriptors and synthetic rasters plus one original body actor; shared DesktopLegacy rendering; genuine START65 WAIT_HOST/CVS2 new-host restore/future; no original executable run or original full-frame equivalence.","excluded","4034A0 area-filtered prescale-cache producer; centered fifth actor; character-overlay native merge; rapid-latch input gate; natural story-route coverage.");
        Files.write(out.resolve("report.json"),Json.text(report).getBytes("UTF-8"));System.out.println(Json.text(Json.obj("result","PASS","checks",checks,"compared_pixels",compared,"frames",frames.size())));
    }
}
