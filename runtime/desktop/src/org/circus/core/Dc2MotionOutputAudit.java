package org.circus.core;

import java.awt.image.BufferedImage;
import java.io.*;
import java.util.*;

/** Actual DesktopLegacy output and snapshot replay, without original assets. */
public final class Dc2MotionOutputAudit {
    private static int count;
    private static void check(boolean value,String why){count++;if(!value)throw new AssertionError(why);}
    private static int oldPixel(int x,int y){return 0xff000000|((x*3+y)&255)<<16|((y*5+x)&255)<<8|((x+y*7)&255);}
    private static int newPixel(int x,int y){return 0xff000000|((x*11+y*3)&255)<<16|((255-y+x*2)&255)<<8|((255-x+y*13)&255);}
    private static int scalar(int old,int fresh,int alpha){int pixel=0xff000000;for(int shift=0;shift<24;shift+=8)pixel|=((((old>>>shift)&255)*(16-alpha)+((fresh>>>shift)&255)*alpha)/16)<<shift;return pixel;}
    private static Crx.Image image(boolean old){int w=800,h=old?600:640,stride=(w*3+3)&~3;byte[] pixels=new byte[stride*h];for(int y=0;y<h;y++)for(int x=0;x<w;x++){int pixel=old?oldPixel(x,y):newPixel(x,y),at=y*stride+x*3;pixels[at]=(byte)pixel;pixels[at+1]=(byte)(pixel>>>8);pixels[at+2]=(byte)(pixel>>>16);}return new Crx.Image(0,0,w,h,24,pixels,null);}
    private static CircusResources resources()throws IOException {
        ByteArrayOutputStream script=new ByteArrayOutputStream();script.write(new byte[]{0,0,0,0,0x63,0x33,0x5e});for(int value:new int[]{0,-24,0,2}){script.write(value);script.write(value>>>8);}script.write(new byte[]{0,0,0});
        final Map<String,byte[]> files=new HashMap<>();files.put("AdvData/DAT/NAMES.DAT",new byte[20]);files.put("AdvData/MES/START.mes",script.toByteArray());
        GameResources data=new GameResources(new GameResources.Storage(){
            public Set<String> paths(){return files.keySet();}
            public byte[] read(String path,int limit)throws IOException{byte[] bytes=files.get(path);if(bytes==null||bytes.length>limit)throw new IOException("Fixture "+path);return bytes.clone();}
            public Archive.Source source(String path){final byte[] bytes=files.get(path);return new Archive.Source(){public long size(){return bytes.length;}public byte[] read(long start,int length)throws IOException{Binary.range(start,length,bytes.length);return Arrays.copyOfRange(bytes,(int)start,(int)start+length);}};}
        });
        final Crx.Image old=image(true),fresh=image(false);return new CircusResources(data,Profiles.get("dc2-3363")){@Override public Crx.Image image(String reference)throws IOException{if(reference.equals("old"))return old;if(reference.equals("new"))return fresh;throw new FileNotFoundException(reference);}};
    }
    private static DesktopLegacy start()throws Exception {DesktopLegacy engine=new DesktopLegacy(resources(),TextCodec.identity(),true);engine.host().write(99,12,2);engine.host().scene.slots[1]="new";engine.host().scene.shown.slots[1]="old";check(engine.vm().run(1)==MesVm.Status.WAIT_HOST,"real engine binds native 5E and stops at its host coroutine");return engine;}
    private static int[] pixels(DesktopLegacy engine)throws IOException{return engine.render().getRGB(0,0,800,600,null,0,800);}
    private static void apply(int[] expected,int offset,int alpha){for(int y=0;y<600;y++)for(int x=0;x<800;x++)expected[y*800+x]=scalar(expected[y*800+x],newPixel(x,y-offset),alpha);}
    public static void main(String[] args)throws Exception {
        DesktopLegacy engine=start();int[] expected=new int[800*600];for(int y=0;y<600;y++)for(int x=0;x<800;x++)expected[y*800+x]=oldPixel(x,y);apply(expected,-8,2);
        check(Arrays.equals(expected,pixels(engine)),"first actual rendered frame places background at negative native destination and applies integer feedback over the previous screen");
        byte[] beforeRender=engine.vm().snapshot();pixels(engine);check(Arrays.equals(beforeRender,engine.vm().snapshot()),"rendering an unchanged motion cannot apply a second feedback blend or mutate native state");
        engine.vm().elapse(35);apply(expected,-16,2);apply(expected,-24,2);apply(expected,-24,2);
        check(Arrays.equals(expected,pixels(engine)),"a thirty-five millisecond update replays all three feedback frames in order, including settling at the destination");
        byte[] saved=engine.vm().snapshot();DesktopLegacy restored=new DesktopLegacy(resources(),TextCodec.identity(),true);restored.restore(saved);
        check(Arrays.equals(expected,pixels(restored)),"a new renderer reconstructs every intermediate native feedback value from a restored motion journal");
        DesktopLegacy tiny=start();for(int n=0;n<35;n++)tiny.vm().elapse(1);check(Arrays.equals(pixels(engine),pixels(tiny)),"platform output is identical for large and one-millisecond elapse partitioning");
        engine.vm().elapse(5);restored.vm().elapse(5);apply(expected,-24,2);check(Arrays.equals(expected,pixels(engine))&&Arrays.equals(expected,pixels(restored)),"snapshot retains the five-millisecond remainder and playback resumes at the same next blend");
        engine.vm().elapse(140);check(!engine.host().waiting(),"native destination tail releases after its full redraw and final coroutine boundary");
        int[] finished=pixels(engine);for(int y=0;y<600;y++)for(int x=0;x<800;x++)expected[y*800+x]=newPixel(x,y+24);check(Arrays.equals(expected,finished),"native final full redraw removes the accumulated integer quantization trail");
        engine.host().command("dc2.scene.motion",new int[]{0,-1,2,32767},new byte[0]);engine.vm().elapse(100);check(Arrays.equals(finished,pixels(engine))&&engine.host().scene.backgroundY()==-1,"nonzero mode changes live coordinates without repainting the retained native framebuffer");
        engine.host().scene.publish(engine.host().ui,0,0);BufferedImage published=engine.render();check(published.getRGB(7,9)==newPixel(7,10),"the next explicit scene publication uses the newly assigned signed offset");
        final CircusResources plain=resources();CircusResources formatOne=new CircusResources(plain.files,plain.profile){@Override public Crx.Image image(String reference)throws IOException{Crx.Image source=plain.image(reference);if(!reference.equals("new"))return source;int[] colors=source.argb();byte[] rgba=new byte[colors.length*4];for(int n=0;n<colors.length;n++){rgba[n*4]=(byte)colors[n];rgba[n*4+1]=(byte)(colors[n]>>>8);rgba[n*4+2]=(byte)(colors[n]>>>16);rgba[n*4+3]=(byte)255;}return new Crx.Image(0,0,source.width,source.height,32,rgba,null);}};
        DesktopLegacy skipped=new DesktopLegacy(formatOne,TextCodec.identity(),true);skipped.host().write(99,12,2);skipped.host().scene.slots[1]="new";skipped.host().scene.shown.slots[1]="old";skipped.vm().run(1);int[] unchanged=new int[800*600];for(int y=0;y<600;y++)for(int x=0;x<800;x++)unchanged[y*800+x]=oldPixel(x,y);
        check(Arrays.equals(unchanged,pixels(skipped)),"native 408160 refuses format-one RGBA feedback instead of ignoring the alpha byte and blending RGB");
        skipped.vm().elapse(1000);check(skipped.render().getRGB(7,9)==newPixel(7,33),"the later complete scene redraw still uses the separate RGBA-capable normal blitter");
        System.out.println("PASS "+count+" DC2 motion output assertions");
    }
}
